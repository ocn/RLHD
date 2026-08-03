package rs117.hd.spikes.vulkan.control;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;

public class VulkanCrashJournalTest
{
	@Test
	public void writesOrderedHashChainedTransitionsAndTerminalOutcome() throws Exception
	{
		Path path = Files.createTempFile("rlhd-vulkan-crash-journal-", ".jsonl");
		try (VulkanCrashJournal journal = VulkanCrashJournal.open(path, "journal-test"))
		{
			String transition = journal.intent("fullscreen.enter", VulkanCrashJournal.fields("frame_id", 12));
			journal.completed("fullscreen.enter", transition, VulkanCrashJournal.fields("frame_id", 12));
			journal.heartbeat("steady_present", VulkanCrashJournal.fields("frame_id", 13));
			journal.complete("passed", null);
		}

		List<String> lines = Files.readAllLines(path);
		assertEquals(5, lines.size());
		String runId = null;
		String previousHash = null;
		for (int index = 0; index < lines.size(); index++)
		{
			JsonObject record = JsonParser.parseString(lines.get(index)).getAsJsonObject();
			assertEquals(index, record.get("seq").getAsLong());
			if (runId == null) runId = record.get("run_id").getAsString();
			else assertEquals(runId, record.get("run_id").getAsString());
			if (previousHash == null) assertEquals(true, record.get("previous_sha256").isJsonNull());
			else assertEquals(previousHash, record.get("previous_sha256").getAsString());
			previousHash = record.get("sha256").getAsString();
			assertNotNull(previousHash);
			assertNotEquals("", previousHash);
			JsonObject unhashed = record.deepCopy();
			unhashed.remove("sha256");
			assertEquals(previousHash, sha256(unhashed.toString().getBytes(StandardCharsets.UTF_8)));
		}
		assertEquals("intent", JsonParser.parseString(lines.get(1)).getAsJsonObject().get("state").getAsString());
		assertEquals("completed", JsonParser.parseString(lines.get(2)).getAsJsonObject().get("state").getAsString());
		assertEquals("terminal", JsonParser.parseString(lines.get(4)).getAsJsonObject().get("state").getAsString());
	}

	@Test
	public void requiredJournalFailsBeforeRiskWhenNoPathIsConfigured()
	{
		String previous = System.getProperty(VulkanCrashJournal.PATH_PROPERTY);
		try
		{
			System.clearProperty(VulkanCrashJournal.PATH_PROPERTY);
			assertThrows(IllegalStateException.class, () -> VulkanCrashJournal.openRequired("missing-path-test"));
		}
		finally
		{
			if (previous == null) System.clearProperty(VulkanCrashJournal.PATH_PROPERTY);
			else System.setProperty(VulkanCrashJournal.PATH_PROPERTY, previous);
		}
	}

	@Test
	public void closesAnUnfinishedRunWithExplicitOutcome() throws Exception
	{
		Path path = Files.createTempFile("rlhd-vulkan-crash-journal-close-", ".jsonl");
		try (VulkanCrashJournal ignored = VulkanCrashJournal.open(path, "journal-close-test")) {}
		JsonObject terminal = JsonParser.parseString(Files.readAllLines(path).get(1)).getAsJsonObject();
		assertEquals("terminal", terminal.get("state").getAsString());
		assertEquals("closed-without-outcome", terminal.getAsJsonObject("details").get("outcome").getAsString());
	}

	@Test
	public void separatesAnUnterminatedCrashPrefixBeforeAppending() throws Exception
	{
		Path path = Files.createTempFile("rlhd-vulkan-crash-journal-prefix-", ".jsonl");
		Files.write(path, "{\"partial\":".getBytes(StandardCharsets.UTF_8));
		try (VulkanCrashJournal journal = VulkanCrashJournal.open(path, "prefix-recovery-test"))
		{
			journal.complete("passed", null);
		}
		List<String> lines = Files.readAllLines(path);
		assertEquals(3, lines.size());
		assertEquals("{\"partial\":", lines.get(0));
		assertEquals("run.start", JsonParser.parseString(lines.get(1)).getAsJsonObject().get("stage").getAsString());
	}

	private static String sha256(byte[] bytes) throws Exception
	{
		byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
		StringBuilder result = new StringBuilder(digest.length * 2);
		for (byte value : digest) result.append(String.format("%02x", value & 0xff));
		return result.toString();
	}
}
