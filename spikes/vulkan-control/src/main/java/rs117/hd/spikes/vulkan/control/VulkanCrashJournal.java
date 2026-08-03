package rs117.hd.spikes.vulkan.control;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

final class VulkanCrashJournal implements AutoCloseable
{
	static final String PATH_PROPERTY = "rlhd.spike.vulkan.crashJournal";
	private static final String SCHEMA = "rlhd.renderer.crash-investigation/v1";
	private static final VulkanCrashJournal DISABLED = new VulkanCrashJournal();
	private static final Object FILE_MUTEX = new Object();

	private final Gson gson;
	private final FileChannel channel;
	private final String runId;
	private final String scenario;
	private long sequence;
	private String previousHash;
	private boolean complete;
	private boolean closed;

	private VulkanCrashJournal()
	{
		gson = null;
		channel = null;
		runId = "disabled";
		scenario = "disabled";
		complete = true;
		closed = true;
	}

	private VulkanCrashJournal(Path path, String scenario) throws IOException
	{
		Path absolute = path.toAbsolutePath();
		Path parent = absolute.getParent();
		if (parent != null) Files.createDirectories(parent);
		ensureRecordBoundary(absolute);
		this.gson = new GsonBuilder().serializeNulls().disableHtmlEscaping().create();
		this.channel = FileChannel.open(absolute, StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND);
		this.runId = Instant.now().toString().replace(':', '-') + "-" + ProcessHandle.current().pid() + "-" + UUID.randomUUID();
		this.scenario = requireToken(scenario, "scenario");
		write("run.start", "completed", null, fields(
			"os_name", System.getProperty("os.name"),
			"os_version", System.getProperty("os.version"),
			"os_build", System.getProperty("rlhd.spike.osBuild", "unknown"),
			"os_arch", System.getProperty("os.arch"),
			"java_version", System.getProperty("java.version"),
			"java_command", System.getProperty("sun.java.command", "unknown"),
			"gradle_task", System.getProperty("rlhd.spike.gradleTask", "unknown"),
			"git_commit", System.getProperty("rlhd.spike.gitCommit", "unknown"),
			"git_dirty", Boolean.parseBoolean(System.getProperty("rlhd.spike.gitDirty", "false")),
			"git_status_porcelain", System.getProperty("rlhd.spike.gitStatus", "unknown"),
			"risk_level", System.getProperty("rlhd.spike.vulkan.riskLevel", "unknown"),
			"validation_requested", Boolean.parseBoolean(System.getProperty("rlhd.spike.vulkan.validation", "false")),
			"vulkan_loader", System.getProperty("rlhd.spike.vulkan.loader", System.getenv("RLHD_VULKAN_LOADER")),
			"vulkan_loader_sha256", System.getProperty("rlhd.spike.vulkan.loaderSha256", "unknown"),
			"moltenvk_library", System.getProperty("rlhd.spike.moltenvk.library", "unknown"),
			"moltenvk_sha256", System.getProperty("rlhd.spike.moltenvk.sha256", "unknown"),
			"vulkan_driver_files", System.getenv("VK_DRIVER_FILES"),
			"vulkan_layer_path", System.getenv("VK_LAYER_PATH")));
	}

	static VulkanCrashJournal openRequired(String scenario)
	{
		String configured = System.getProperty(PATH_PROPERTY);
		if (configured == null || configured.trim().isEmpty())
			throw new IllegalStateException("A durable Vulkan crash journal path is required via -D" + PATH_PROPERTY + ".");
		try
		{
			return new VulkanCrashJournal(Paths.get(configured), scenario);
		}
		catch (IOException ex)
		{
			throw new VulkanCrashJournalException("Unable to open the durable Vulkan crash journal.", ex);
		}
	}

	static VulkanCrashJournal open(Path path, String scenario)
	{
		try
		{
			return new VulkanCrashJournal(Objects.requireNonNull(path, "path"), scenario);
		}
		catch (IOException ex)
		{
			throw new VulkanCrashJournalException("Unable to open the durable Vulkan crash journal.", ex);
		}
	}

	static VulkanCrashJournal disabled()
	{
		return DISABLED;
	}

	synchronized String intent(String stage, Map<String, ?> details)
	{
		if (channel == null) return "disabled";
		String transitionId = UUID.randomUUID().toString();
		write(stage, "intent", transitionId, details);
		return transitionId;
	}

	synchronized void completed(String stage, String transitionId, Map<String, ?> details)
	{
		if (channel == null) return;
		write(stage, "completed", transitionId, details);
	}

	synchronized void failed(String stage, String transitionId, Throwable failure)
	{
		if (channel == null) return;
		Map<String, Object> details = fields("error_type", failure.getClass().getName(), "error_message", failure.getMessage());
		write(stage, "failed", transitionId, details);
	}

	synchronized void heartbeat(String stage, Map<String, ?> details)
	{
		if (channel == null) return;
		write(stage, "heartbeat", null, details);
	}

	private void write(String stage, String state, String transitionId, Map<String, ?> details)
	{
		if (closed) throw new IllegalStateException("Vulkan crash journal is closed.");
		JsonObject record = new JsonObject();
		record.addProperty("schema", SCHEMA);
		record.addProperty("type", "checkpoint");
		record.addProperty("run_id", runId);
		record.addProperty("scenario", scenario);
		record.addProperty("seq", sequence++);
		record.addProperty("stage", requireToken(stage, "stage"));
		record.addProperty("state", requireToken(state, "state"));
		if (transitionId == null) record.add("transition_id", com.google.gson.JsonNull.INSTANCE);
		else record.addProperty("transition_id", transitionId);
		record.addProperty("timestamp_utc", Instant.now().toString());
		record.addProperty("monotonic_ns", System.nanoTime());
		record.addProperty("pid", ProcessHandle.current().pid());
		record.add("details", gson.toJsonTree(details == null ? java.util.Collections.emptyMap() : details));
		if (previousHash == null) record.add("previous_sha256", com.google.gson.JsonNull.INSTANCE);
		else record.addProperty("previous_sha256", previousHash);
		String hash = sha256(gson.toJson(record).getBytes(StandardCharsets.UTF_8));
		record.addProperty("sha256", hash);
		writeDurably(record);
		previousHash = hash;
	}

	synchronized void complete(String outcome, Throwable failure)
	{
		if (channel == null || complete) return;
		Map<String, Object> details = fields("outcome", requireToken(outcome, "outcome"));
		if (failure != null)
		{
			details.put("error_type", failure.getClass().getName());
			details.put("error_message", failure.getMessage());
		}
		write("run.end", "terminal", null, details);
		complete = true;
	}

	@Override
	public synchronized void close()
	{
		if (channel == null || closed) return;
		try
		{
			if (!complete) complete("closed-without-outcome", null);
			channel.force(true);
			channel.close();
			closed = true;
		}
		catch (IOException ex)
		{
			throw new VulkanCrashJournalException("Unable to close the Vulkan crash journal.", ex);
		}
	}

	static Map<String, Object> fields(Object... pairs)
	{
		if ((pairs.length & 1) != 0) throw new IllegalArgumentException("Crash journal fields require key/value pairs.");
		Map<String, Object> fields = new LinkedHashMap<>();
		for (int index = 0; index < pairs.length; index += 2)
			fields.put(Objects.toString(pairs[index]), pairs[index + 1]);
		return fields;
	}

	private void writeDurably(JsonObject record)
	{
		byte[] bytes = (gson.toJson(record) + "\n").getBytes(StandardCharsets.UTF_8);
		synchronized (FILE_MUTEX)
		{
			try (FileLock ignored = channel.lock())
			{
				ByteBuffer buffer = ByteBuffer.wrap(bytes);
				while (buffer.hasRemaining()) channel.write(buffer);
				channel.force(true);
			}
			catch (IOException ex)
			{
				throw new VulkanCrashJournalException("Unable to persist a Vulkan crash checkpoint.", ex);
			}
		}
	}

	private static String requireToken(String value, String name)
	{
		if (value == null || !value.matches("[a-z0-9][a-z0-9._-]*"))
			throw new IllegalArgumentException(name + " must be a lowercase diagnostic token.");
		return value;
	}

	private static void ensureRecordBoundary(Path path) throws IOException
	{
		if (!Files.exists(path) || Files.size(path) == 0) return;
		byte last;
		try (FileChannel reader = FileChannel.open(path, StandardOpenOption.READ))
		{
			reader.position(reader.size() - 1);
			ByteBuffer byteBuffer = ByteBuffer.allocate(1);
			if (reader.read(byteBuffer) != 1) throw new IOException("Unable to read the final crash-journal byte.");
			last = byteBuffer.array()[0];
		}
		if (last != '\n')
		{
			try (FileChannel writer = FileChannel.open(path, StandardOpenOption.WRITE, StandardOpenOption.APPEND))
			{
				writer.write(ByteBuffer.wrap(new byte[] {'\n'}));
				writer.force(true);
			}
		}
	}

	private static String sha256(byte[] bytes)
	{
		try
		{
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
			StringBuilder result = new StringBuilder(digest.length * 2);
			for (byte value : digest) result.append(String.format("%02x", value & 0xff));
			return result.toString();
		}
		catch (NoSuchAlgorithmException ex)
		{
			throw new IllegalStateException("SHA-256 is unavailable.", ex);
		}
	}
}
