package rs117.hd.spikes.macos.control;

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

public final class TimingJsonSchema
{
	public static final String SCHEMA = "rlhd.renderer.timing/v1";
	public static final String BACKEND = "metal-control";

	private static final Set<String> TYPES = set("run_start", "frame", "run_end");
	private static final Set<String> MODES = set("fifo-like", "unlocked");
	private static final Set<String> OUTCOMES = set("submitted", "skipped-suspended", "skipped-in-flight", "nil-drawable", "rejected", "error");
	private static final Set<String> PRESENT_STATUSES = set("presented", "dropped", "unsupported", "callback-timeout", "not-requested");
	private static final String[] COUNTERS = {
		"init_errors", "shader_errors", "pipeline_errors", "submitted", "completed", "command_errors",
		"present_requested", "nil_drawable", "skipped_suspended", "skipped_in_flight", "ui_upload_bytes",
		"resize_rebuilds", "device_rebuilds", "live_native_objects", "high_water_native_objects", "max_in_flight",
		"presentation_callbacks", "presentation_dropped", "presentation_timeouts", "present_mode_divergences",
		"drawable_acquisition_requests", "drawable_acquisition_completions"
	};

	private TimingJsonSchema()
	{
	}

	public static void validateLine(String line)
	{
		try
		{
			assertStrictSyntaxAndUniqueKeys(line);
			JsonElement parsed = JsonParser.parseString(line);
			if (!parsed.isJsonObject()) throw invalid("record must be an object");
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
		if (!"run_start".equals(typeOf(lines.get(0))) || !"run_end".equals(typeOf(lines.get(lines.size() - 1))))
		{
			throw invalid("log must start with run_start and end with run_end");
		}
		for (int index = 1; index < lines.size() - 1; index++)
		{
			if (!"frame".equals(typeOf(lines.get(index)))) throw invalid("only frame records may appear inside a run");
		}
	}

	public static String frameFixture()
	{
		return "{\"schema\":\"" + SCHEMA + "\",\"type\":\"frame\",\"backend\":\"" + BACKEND +
			"\",\"frame_id\":7,\"resolution\":{\"width\":320,\"height\":180}," +
			"\"requested_present_mode\":\"fifo-like\",\"effective_present_mode\":\"fifo-like\"," +
			"\"outcome\":\"submitted\",\"cpu_ns\":{\"ui_generate\":1,\"ui_upload\":1,\"encode\":2,\"submit\":3,\"total\":7}," +
			"\"gpu_ns\":{\"start\":10,\"end\":20,\"duration\":10}," +
			"\"present\":{\"requested\":true,\"drawable_available\":true,\"callback_status\":\"presented\"," +
			"\"presented_time_ns\":25,\"latency_ns\":5},\"counters\":" + countersFixture(1, 1, 1, 0) + ",\"error\":null}";
	}

	public static String runStartFixture()
	{
		return "{\"schema\":\"" + SCHEMA + "\",\"type\":\"run_start\",\"backend\":\"" + BACKEND +
			"\",\"timestamp_ns\":1,\"requested_present_mode\":\"fifo-like\",\"effective_present_mode\":\"fifo-like\"}";
	}

	public static String runEndFixture()
	{
		return "{\"schema\":\"" + SCHEMA + "\",\"type\":\"run_end\",\"backend\":\"" + BACKEND +
			"\",\"timestamp_ns\":30,\"requested_present_mode\":\"fifo-like\",\"effective_present_mode\":\"fifo-like\"," +
			"\"counters\":" + countersFixture(1, 1, 1, 0) + ",\"error\":null}";
	}

	private static void validateRunStart(JsonObject record)
	{
		requireNonnegativeLong(record, "timestamp_ns", false);
		requireString(record, "requested_present_mode", MODES);
		requireString(record, "effective_present_mode", MODES);
	}

	private static void validateRunEnd(JsonObject record)
	{
		requireNonnegativeLong(record, "timestamp_ns", false);
		requireString(record, "requested_present_mode", MODES);
		requireString(record, "effective_present_mode", MODES);
		JsonObject counters = requireObject(record, "counters");
		validateCounters(counters);
		if (requireNonnegativeLong(counters, "live_native_objects", false) != 0) throw invalid("run_end must have no live native objects");
		validateError(record);
	}

	private static void validateFrame(JsonObject record)
	{
		requireNonnegativeLong(record, "frame_id", false);
		JsonObject resolution = requireObject(record, "resolution");
		requireNonnegativeLong(resolution, "width", false);
		requireNonnegativeLong(resolution, "height", false);
		requireString(record, "requested_present_mode", MODES);
		requireString(record, "effective_present_mode", MODES);
		String outcome = requireString(record, "outcome", OUTCOMES);
		JsonObject cpu = requireObject(record, "cpu_ns");
		long total = requireNonnegativeLong(cpu, "total", false);
		for (String field : Arrays.asList("ui_generate", "ui_upload", "encode", "submit"))
		{
			if (requireNonnegativeLong(cpu, field, false) > total) throw invalid("CPU phase exceeds total");
		}
		JsonElement gpu = require(record, "gpu_ns");
		if (!gpu.isJsonNull())
		{
			if (!gpu.isJsonObject()) throw invalid("gpu_ns must be object or null");
			JsonObject object = gpu.getAsJsonObject();
			long start = requireNonnegativeLong(object, "start", false);
			long end = requireNonnegativeLong(object, "end", false);
			long duration = requireNonnegativeLong(object, "duration", false);
			if (end < start || duration != end - start) throw invalid("invalid GPU duration");
		}
		JsonObject present = requireObject(record, "present");
		boolean requested = requireBoolean(present, "requested");
		boolean drawable = requireBoolean(present, "drawable_available");
		String status = requireString(present, "callback_status", PRESENT_STATUSES);
		Long presented = nullableNonnegativeLong(present, "presented_time_ns");
		Long latency = nullableNonnegativeLong(present, "latency_ns");
		if ("submitted".equals(outcome) != requested || requested != drawable) throw invalid("present flags disagree with outcome");
		if ("presented".equals(status) != (presented != null && latency != null)) throw invalid("presentation timing disagrees with status");
		if (!requested && !"not-requested".equals(status)) throw invalid("skipped frame must not have presentation callback");
		validateCounters(requireObject(record, "counters"));
		validateError(record);
	}

	private static void validateCounters(JsonObject counters)
	{
		for (String counter : COUNTERS) requireNonnegativeLong(counters, counter, false);
		long submitted = requireNonnegativeLong(counters, "submitted", false);
		if (requireNonnegativeLong(counters, "completed", false) > submitted) throw invalid("completed exceeds submitted");
		if (requireNonnegativeLong(counters, "present_requested", false) > submitted) throw invalid("present requests exceed submissions");
		if (requireNonnegativeLong(counters, "max_in_flight", false) > 3) throw invalid("max_in_flight exceeds slot count");
		if (requireNonnegativeLong(counters, "drawable_acquisition_completions", false) >
			requireNonnegativeLong(counters, "drawable_acquisition_requests", false)) throw invalid("acquisition completions exceed requests");
	}

	private static void validateError(JsonObject object)
	{
		JsonElement error = require(object, "error");
		if (!error.isJsonNull() && (!error.isJsonPrimitive() || !error.getAsJsonPrimitive().isString()))
		{
			throw invalid("error must be string or null");
		}
	}

	private static void assertStrictSyntaxAndUniqueKeys(String line) throws IOException
	{
		if (line == null) throw invalid("record is null");
		JsonReader reader = new JsonReader(new StringReader(line));
		reader.setStrictness(Strictness.STRICT);
		consume(reader);
		if (reader.peek() != JsonToken.END_DOCUMENT) throw invalid("trailing JSON content");
	}

	private static void consume(JsonReader reader) throws IOException
	{
		JsonToken token = reader.peek();
		if (token == JsonToken.BEGIN_OBJECT)
		{
			reader.beginObject();
			Set<String> names = new HashSet<>();
			while (reader.hasNext())
			{
				String name = reader.nextName();
				if (!names.add(name)) throw invalid("duplicate field: " + name);
				consume(reader);
			}
			reader.endObject();
		}
		else if (token == JsonToken.BEGIN_ARRAY)
		{
			reader.beginArray();
			while (reader.hasNext()) consume(reader);
			reader.endArray();
		}
		else if (token == JsonToken.STRING || token == JsonToken.NUMBER) reader.nextString();
		else if (token == JsonToken.BOOLEAN) reader.nextBoolean();
		else if (token == JsonToken.NULL) reader.nextNull();
		else throw invalid("unexpected JSON token: " + token);
	}

	private static JsonElement require(JsonObject object, String name)
	{
		if (!object.has(name)) throw invalid("missing field: " + name);
		return object.get(name);
	}

	private static JsonObject requireObject(JsonObject object, String name)
	{
		JsonElement value = require(object, name);
		if (!value.isJsonObject()) throw invalid(name + " must be an object");
		return value.getAsJsonObject();
	}

	private static String requireString(JsonObject object, String name, Set<String> allowed)
	{
		JsonElement value = require(object, name);
		if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) throw invalid(name + " must be a string");
		String string = value.getAsString();
		if (!allowed.contains(string)) throw invalid("invalid " + name + ": " + string);
		return string;
	}

	private static boolean requireBoolean(JsonObject object, String name)
	{
		JsonElement value = require(object, name);
		if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean()) throw invalid(name + " must be boolean");
		return value.getAsBoolean();
	}

	private static long requireNonnegativeLong(JsonObject object, String name, boolean nullable)
	{
		Long value = nullableNonnegativeLong(object, name);
		if (value == null && !nullable) throw invalid(name + " must be an integer");
		return value == null ? 0 : value;
	}

	private static Long nullableNonnegativeLong(JsonObject object, String name)
	{
		JsonElement value = require(object, name);
		if (value.isJsonNull()) return null;
		if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) throw invalid(name + " must be numeric or null");
		try
		{
			long number = new BigDecimal(value.getAsString()).longValueExact();
			if (number < 0) throw invalid(name + " must be nonnegative");
			return number;
		}
		catch (ArithmeticException ex)
		{
			throw invalid(name + " must be an exact integer");
		}
	}

	private static String typeOf(String line)
	{
		return JsonParser.parseString(line).getAsJsonObject().get("type").getAsString();
	}

	private static String countersFixture(long submitted, long completed, long presentRequested, long live)
	{
		StringBuilder value = new StringBuilder("{");
		for (int index = 0; index < COUNTERS.length; index++)
		{
			if (index != 0) value.append(',');
			String name = COUNTERS[index];
			long count = "submitted".equals(name) ? submitted : "completed".equals(name) ? completed :
				"present_requested".equals(name) ? presentRequested : "live_native_objects".equals(name) ? live :
				"high_water_native_objects".equals(name) || "max_in_flight".equals(name) ||
				"drawable_acquisition_requests".equals(name) || "drawable_acquisition_completions".equals(name) ? 1 : 0;
			value.append('"').append(name).append("\":").append(count);
		}
		return value.append('}').toString();
	}

	private static Set<String> set(String... values)
	{
		return new HashSet<>(Arrays.asList(values));
	}

	private static IllegalArgumentException invalid(String message)
	{
		return new IllegalArgumentException("Invalid timing JSON: " + message);
	}
}
