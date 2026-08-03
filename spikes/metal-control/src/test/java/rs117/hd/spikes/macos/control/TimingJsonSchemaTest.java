package rs117.hd.spikes.macos.control;

import java.util.Arrays;
import org.junit.Test;

import static org.junit.Assert.assertThrows;

public class TimingJsonSchemaTest
{
	@Test
	public void acceptsStrictTypedRunAndEscapedErrorRecords()
	{
		TimingJsonSchema.validateLine(TimingJsonSchema.runStartFixture());
		TimingJsonSchema.validateLine(TimingJsonSchema.frameFixture());
		TimingJsonSchema.validateLine(TimingJsonSchema.frameFixture().replace("\"error\":null", "\"error\":\"quoted \\\"value\\\"\""));
		TimingJsonSchema.validateLine(withNullPresentation("unsupported"));
		TimingJsonSchema.validateLine(withNullPresentation("dropped"));
		TimingJsonSchema.validateLine(withNullPresentation("callback-timeout"));
		TimingJsonSchema.validateLine(TimingJsonSchema.runEndFixture());
		TimingJsonSchema.validateLog(Arrays.asList(
			TimingJsonSchema.runStartFixture(), TimingJsonSchema.frameFixture(), TimingJsonSchema.runEndFixture()));
	}

	@Test
	public void rejectsMalformedTrailingDuplicateAndIncompleteJson()
	{
		reject(TimingJsonSchema.frameFixture().substring(0, TimingJsonSchema.frameFixture().length() - 1));
		reject(TimingJsonSchema.frameFixture() + " true");
		reject(TimingJsonSchema.frameFixture().replaceFirst("\\{", "{\"schema\":\"duplicate\","));
		reject(TimingJsonSchema.frameFixture().replace("\"gpu_ns\":", "\"missing_gpu_ns\":"));
	}

	@Test
	public void rejectsWrongTypesEnumsAndNumericInvariants()
	{
		reject(TimingJsonSchema.frameFixture().replace("\"frame_id\":7", "\"frame_id\":\"7\""));
		reject(TimingJsonSchema.frameFixture().replace("\"outcome\":\"submitted\"", "\"outcome\":\"unknown\""));
		reject(TimingJsonSchema.frameFixture().replace("\"duration\":10", "\"duration\":9"));
		reject(TimingJsonSchema.frameFixture().replace("\"requested\":true", "\"requested\":false"));
		reject(TimingJsonSchema.frameFixture().replace("\"max_in_flight\":1", "\"max_in_flight\":4"));
		reject(TimingJsonSchema.runEndFixture().replace("\"live_native_objects\":0", "\"live_native_objects\":1"));
	}

	@Test
	public void rejectsInvalidRunOrdering()
	{
		assertThrows(IllegalArgumentException.class, () -> TimingJsonSchema.validateLog(Arrays.asList(
			TimingJsonSchema.frameFixture(), TimingJsonSchema.runEndFixture())));
		assertThrows(IllegalArgumentException.class, () -> TimingJsonSchema.validateLog(Arrays.asList(
			TimingJsonSchema.runStartFixture(), TimingJsonSchema.runStartFixture(), TimingJsonSchema.runEndFixture())));
	}

	private static void reject(String line)
	{
		assertThrows(IllegalArgumentException.class, () -> TimingJsonSchema.validateLine(line));
	}

	private static String withNullPresentation(String status)
	{
		return TimingJsonSchema.frameFixture()
			.replace("\"callback_status\":\"presented\"", "\"callback_status\":\"" + status + "\"")
			.replace("\"presented_time_ns\":25", "\"presented_time_ns\":null")
			.replace("\"latency_ns\":5", "\"latency_ns\":null");
	}
}
