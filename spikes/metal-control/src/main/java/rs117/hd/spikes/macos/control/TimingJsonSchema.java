package rs117.hd.spikes.macos.control;

import java.util.Arrays;

public final class TimingJsonSchema
{
	public static final String SCHEMA = "rlhd.renderer.timing/v1";
	public static final String BACKEND = "metal-control";

	private static final String[] COMMON_FIELDS = {"\"schema\"", "\"type\"", "\"backend\""};
	private static final String[] FRAME_FIELDS = {
		"\"frame_id\"", "\"resolution\"", "\"requested_present_mode\"", "\"effective_present_mode\"",
		"\"outcome\"", "\"cpu_ns\"", "\"gpu_ns\"", "\"present\"", "\"counters\"", "\"error\""
	};

	private TimingJsonSchema()
	{
	}

	public static void validateLine(String line)
	{
		if (line == null || !line.startsWith("{") || !line.endsWith("}"))
		{
			throw new IllegalArgumentException("Timing record must be one JSON object line.");
		}
		for (String field : COMMON_FIELDS)
		{
			requireField(line, field);
		}
		if (!line.contains("\"schema\":\"" + SCHEMA + "\"") || !line.contains("\"backend\":\"" + BACKEND + "\""))
		{
			throw new IllegalArgumentException("Unexpected timing schema or backend.");
		}
		if (line.contains("\"type\":\"frame\""))
		{
			for (String field : FRAME_FIELDS)
			{
				requireField(line, field);
			}
		}
		else if (!line.contains("\"type\":\"run_start\"") && !line.contains("\"type\":\"run_end\""))
		{
			throw new IllegalArgumentException("Unknown timing record type.");
		}
	}

	public static String frameFixture()
	{
		return "{\"schema\":\"" + SCHEMA + "\",\"type\":\"frame\",\"backend\":\"" + BACKEND +
			"\",\"frame_id\":7,\"resolution\":{\"width\":320,\"height\":180}," +
			"\"requested_present_mode\":\"fifo-like\",\"effective_present_mode\":\"fifo-like\"," +
			"\"outcome\":\"submitted\",\"cpu_ns\":{\"ui_generate\":1,\"ui_upload\":1,\"encode\":2,\"submit\":3,\"total\":7}," +
			"\"gpu_ns\":{\"start\":10,\"end\":20,\"duration\":10}," +
			"\"present\":{\"requested\":true,\"drawable_available\":true},\"counters\":{},\"error\":null}";
	}

	private static void requireField(String line, String field)
	{
		if (!line.contains(field + ":"))
		{
			throw new IllegalArgumentException("Missing timing field " + field + " in " + Arrays.toString(FRAME_FIELDS));
		}
	}
}
