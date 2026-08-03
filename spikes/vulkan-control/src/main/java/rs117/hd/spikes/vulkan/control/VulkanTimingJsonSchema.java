package rs117.hd.spikes.vulkan.control;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.Strictness;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import java.io.IOException;
import java.io.StringReader;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class VulkanTimingJsonSchema
{
	public static final String SCHEMA = "rlhd.renderer.timing/v1";
	public static final String BACKEND = "vulkan-control";
	private static final Set<String> TYPES = set("run_start", "frame", "run_end");
	private static final Set<String> REQUESTED_MODES = set("fifo-like", "unlocked");
	private static final Set<String> EFFECTIVE_MODES = set("fifo-like", "unlocked", "mailbox");
	private static final Set<String> OUTCOMES = set("submitted", "skipped-suspended", "skipped-in-flight", "nil-drawable", "rejected", "error");
	private static final String[] COUNTERS = {
		"init_errors", "shader_errors", "pipeline_errors", "submitted", "completed", "command_errors",
		"present_requested", "nil_drawable", "skipped_suspended", "skipped_in_flight", "ui_upload_bytes",
		"resize_rebuilds", "device_rebuilds", "live_native_objects", "high_water_native_objects", "max_in_flight",
		"presentation_callbacks", "presentation_dropped", "presentation_timeouts", "present_mode_divergences",
		"drawable_acquisition_requests", "drawable_acquisition_completions", "validation_warnings", "validation_errors",
		"timestamp_query_errors"
	};

	private VulkanTimingJsonSchema() {}

	public static void validateLine(String line)
	{
		try
		{
			assertStrictSyntaxAndUniqueKeys(line);
			JsonElement parsed = JsonParser.parseString(line);
			if (!parsed.isJsonObject()) throw invalid("record must be object");
			JsonObject record = parsed.getAsJsonObject();
			requireString(record, "schema", set(SCHEMA));
			requireString(record, "backend", set(BACKEND));
			String type = requireString(record, "type", TYPES);
			if ("run_start".equals(type)) validateRunStart(record);
			else if ("frame".equals(type)) validateFrame(record);
			else validateRunEnd(record);
		}
		catch (IOException | RuntimeException ex)
		{
			if (ex instanceof IllegalArgumentException) throw (IllegalArgumentException) ex;
			throw invalid(ex.getMessage());
		}
	}

	public static void validateLog(List<String> lines)
	{
		if (lines == null || lines.size() < 2) throw invalid("log must contain start and end records");
		for (String line : lines) validateLine(line);
		if (!"run_start".equals(typeOf(lines.get(0))) || !"run_end".equals(typeOf(lines.get(lines.size() - 1)))) throw invalid("invalid run ordering");
		for (int index = 1; index < lines.size() - 1; index++) if (!"frame".equals(typeOf(lines.get(index)))) throw invalid("only frames may appear inside a run");
	}

	public static String runStartFixture()
	{
		return "{\"schema\":\"" + SCHEMA + "\",\"type\":\"run_start\",\"backend\":\"" + BACKEND +
			"\",\"timestamp_ns\":1,\"requested_present_mode\":\"fifo-like\",\"effective_present_mode\":\"fifo-like\"," +
			"\"capabilities\":{\"api_version\":\"1.2\",\"portability_enumeration\":true,\"metal_surface\":true," +
			"\"swapchain\":true,\"swapchain_maintenance1\":true,\"presentation_fences\":true,\"portability_subset\":true," +
			"\"format\":\"VK_FORMAT_B8G8R8A8_UNORM\",\"color_space\":\"VK_COLOR_SPACE_SRGB_NONLINEAR_KHR\"," +
			"\"requested_images\":3,\"actual_images\":3,\"frames_in_flight\":2,\"standard_queue_present\":true," +
			"\"validation_requested\":false,\"validation_enabled\":false}}";
	}

	public static String frameFixture()
	{
		return "{\"schema\":\"" + SCHEMA + "\",\"type\":\"frame\",\"backend\":\"" + BACKEND +
			"\",\"frame_id\":7,\"resolution\":{\"width\":320,\"height\":180},\"requested_present_mode\":\"fifo-like\"," +
			"\"effective_present_mode\":\"fifo-like\",\"outcome\":\"submitted\"," +
			"\"cpu_ns\":{\"ui_generate\":1,\"ui_upload\":1,\"encode\":2,\"submit\":3,\"total\":7}," +
			"\"gpu_ns\":null,\"present\":{\"requested\":true,\"drawable_available\":true,\"callback_status\":\"unsupported\"," +
			"\"presented_time_ns\":null,\"latency_ns\":null},\"counters\":" + countersFixture(1, 1, 1, 0) + ",\"error\":null}";
	}

	public static String runEndFixture()
	{
		return "{\"schema\":\"" + SCHEMA + "\",\"type\":\"run_end\",\"backend\":\"" + BACKEND +
			"\",\"timestamp_ns\":30,\"requested_present_mode\":\"fifo-like\",\"effective_present_mode\":\"fifo-like\"," +
			"\"counters\":" + countersFixture(1, 1, 1, 0) + ",\"error\":null}";
	}

	private static void validateRunStart(JsonObject record)
	{
		requireLong(record, "timestamp_ns");
			requireString(record, "requested_present_mode", REQUESTED_MODES);
			requireString(record, "effective_present_mode", EFFECTIVE_MODES);
		JsonObject caps = requireObject(record, "capabilities");
		if (!"1.2".equals(requireString(caps, "api_version", set("1.2")))) throw invalid("Vulkan 1.2 is required");
		for (String field : Arrays.asList("portability_enumeration", "metal_surface", "swapchain", "swapchain_maintenance1",
			"presentation_fences", "standard_queue_present"))
			if (!requireBoolean(caps, field)) throw invalid("required capability disabled: " + field);
		requireBoolean(caps, "portability_subset");
		if (!"VK_FORMAT_B8G8R8A8_UNORM".equals(requireString(caps, "format", set("VK_FORMAT_B8G8R8A8_UNORM")))) throw invalid("invalid format");
		requireString(caps, "color_space", set("VK_COLOR_SPACE_SRGB_NONLINEAR_KHR"));
		long requestedImages = requireLong(caps, "requested_images");
		if (requestedImages < 3 || requireLong(caps, "actual_images") < requestedImages || requireLong(caps, "frames_in_flight") != 2) throw invalid("invalid buffering configuration");
		boolean validationRequested = requireBoolean(caps, "validation_requested");
		boolean validationEnabled = requireBoolean(caps, "validation_enabled");
		if (validationEnabled && !validationRequested) throw invalid("validation cannot be enabled unless requested");
	}

	private static void validateFrame(JsonObject record)
	{
		requireLong(record, "frame_id");
		JsonObject resolution = requireObject(record, "resolution");
		requireLong(resolution, "width"); requireLong(resolution, "height");
		requireString(record, "requested_present_mode", REQUESTED_MODES); requireString(record, "effective_present_mode", EFFECTIVE_MODES);
		String outcome = requireString(record, "outcome", OUTCOMES);
		JsonObject cpu = requireObject(record, "cpu_ns");
		long total = requireLong(cpu, "total");
		for (String field : Arrays.asList("ui_generate", "ui_upload", "encode", "submit")) if (requireLong(cpu, field) > total) throw invalid("CPU phase exceeds total");
		JsonElement gpu = require(record, "gpu_ns");
		if (!gpu.isJsonNull()) { JsonObject timing = gpu.getAsJsonObject(); long start = requireLong(timing, "start"); long end = requireLong(timing, "end"); if (requireLong(timing, "duration") != end - start) throw invalid("invalid GPU duration"); }
		JsonObject present = requireObject(record, "present");
		boolean requested = requireBoolean(present, "requested"); boolean drawable = requireBoolean(present, "drawable_available");
		requireString(present, "callback_status", set("presented", "dropped", "unsupported", "callback-timeout", "not-requested"));
		requireNullableLong(present, "presented_time_ns"); requireNullableLong(present, "latency_ns");
		if ("submitted".equals(outcome) != requested || requested != drawable) throw invalid("present flags disagree with outcome");
		validateCounters(requireObject(record, "counters")); validateError(record);
	}

	private static void validateRunEnd(JsonObject record)
	{
		requireLong(record, "timestamp_ns"); requireString(record, "requested_present_mode", REQUESTED_MODES); requireString(record, "effective_present_mode", EFFECTIVE_MODES);
		JsonObject counters = requireObject(record, "counters"); validateCounters(counters);
		if (requireLong(counters, "live_native_objects") != 0) throw invalid("run_end must have no live objects");
		if (requireLong(counters, "completed") != requireLong(counters, "submitted")) throw invalid("run_end has unfinished submissions");
		if (requireLong(counters, "present_requested") != requireLong(counters, "submitted")) throw invalid("run_end has unpresented submissions");
		if (requireLong(counters, "drawable_acquisition_completions") != requireLong(counters, "drawable_acquisition_requests"))
			throw invalid("run_end has unfinished acquisitions");
		validateError(record);
	}

	private static void validateCounters(JsonObject counters)
	{
		for (String name : COUNTERS) requireLong(counters, name);
		if (requireLong(counters, "completed") > requireLong(counters, "submitted")) throw invalid("completed exceeds submitted");
		if (requireLong(counters, "max_in_flight") > 2) throw invalid("max_in_flight exceeds Vulkan frame slots");
		if (requireLong(counters, "present_requested") > requireLong(counters, "submitted")) throw invalid("present requests exceed submissions");
		if (requireLong(counters, "drawable_acquisition_completions") > requireLong(counters, "drawable_acquisition_requests"))
			throw invalid("acquisition completions exceed requests");
	}

	private static void validateError(JsonObject object)
	{
		JsonElement error = require(object, "error");
		if (!error.isJsonNull() && (!error.isJsonPrimitive() || !error.getAsJsonPrimitive().isString())) throw invalid("error must be string or null");
	}

	private static void assertStrictSyntaxAndUniqueKeys(String line) throws IOException
	{
		if (line == null) throw invalid("record is null");
		JsonReader reader = new JsonReader(new StringReader(line)); reader.setStrictness(Strictness.STRICT); consume(reader);
		if (reader.peek() != JsonToken.END_DOCUMENT) throw invalid("trailing JSON content");
	}

	private static void consume(JsonReader reader) throws IOException
	{
		JsonToken token = reader.peek();
		if (token == JsonToken.BEGIN_OBJECT) { reader.beginObject(); Set<String> names = new HashSet<>(); while (reader.hasNext()) { String name = reader.nextName(); if (!names.add(name)) throw invalid("duplicate field: " + name); consume(reader); } reader.endObject(); }
		else if (token == JsonToken.BEGIN_ARRAY) { reader.beginArray(); while (reader.hasNext()) consume(reader); reader.endArray(); }
		else if (token == JsonToken.STRING || token == JsonToken.NUMBER) reader.nextString();
		else if (token == JsonToken.BOOLEAN) reader.nextBoolean(); else if (token == JsonToken.NULL) reader.nextNull(); else throw invalid("unexpected token");
	}

	private static JsonElement require(JsonObject object, String name) { if (!object.has(name)) throw invalid("missing field: " + name); return object.get(name); }
	private static JsonObject requireObject(JsonObject object, String name) { JsonElement value = require(object, name); if (!value.isJsonObject()) throw invalid(name + " must be object"); return value.getAsJsonObject(); }
	private static String requireString(JsonObject object, String name, Set<String> allowed) { JsonElement value = require(object, name); if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString() || !allowed.contains(value.getAsString())) throw invalid("invalid " + name); return value.getAsString(); }
	private static boolean requireBoolean(JsonObject object, String name) { JsonElement value = require(object, name); if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean()) throw invalid(name + " must be boolean"); return value.getAsBoolean(); }
	private static long requireLong(JsonObject object, String name) { Long value = requireNullableLong(object, name); if (value == null) throw invalid(name + " must be integer"); return value; }
	private static Long requireNullableLong(JsonObject object, String name) { JsonElement value = require(object, name); if (value.isJsonNull()) return null; try { long number = new BigDecimal(value.getAsString()).longValueExact(); if (number < 0) throw invalid(name + " must be nonnegative"); return number; } catch (RuntimeException ex) { throw invalid(name + " must be integer"); } }
	private static String typeOf(String line) { return JsonParser.parseString(line).getAsJsonObject().get("type").getAsString(); }
	private static Set<String> set(String... values) { return new HashSet<>(Arrays.asList(values)); }
	private static IllegalArgumentException invalid(String message) { return new IllegalArgumentException("Invalid Vulkan timing JSON: " + message); }

	private static String countersFixture(long submitted, long completed, long presentRequested, long live)
	{
		StringBuilder value = new StringBuilder("{");
		for (int index = 0; index < COUNTERS.length; index++) { if (index > 0) value.append(','); String name = COUNTERS[index]; long count = "submitted".equals(name) ? submitted : "completed".equals(name) ? completed : "present_requested".equals(name) ? presentRequested : "live_native_objects".equals(name) ? live : "high_water_native_objects".equals(name) || "max_in_flight".equals(name) || "drawable_acquisition_requests".equals(name) || "drawable_acquisition_completions".equals(name) ? 1 : 0; value.append('"').append(name).append("\":").append(count); }
		return value.append('}').toString();
	}
}
