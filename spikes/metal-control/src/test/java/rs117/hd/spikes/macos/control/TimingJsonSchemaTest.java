package rs117.hd.spikes.macos.control;

import org.junit.Test;

import static org.junit.Assert.assertThrows;

public class TimingJsonSchemaTest
{
	@Test
	public void acceptsVersionedFrameSchema()
	{
		TimingJsonSchema.validateLine(TimingJsonSchema.frameFixture());
		TimingJsonSchema.validateLine("{\"schema\":\"rlhd.renderer.timing/v1\",\"type\":\"run_start\",\"backend\":\"metal-control\"}");
		TimingJsonSchema.validateLine("{\"schema\":\"rlhd.renderer.timing/v1\",\"type\":\"run_end\",\"backend\":\"metal-control\"}");
	}

	@Test
	public void rejectsWrongSchemaUnknownTypeAndMissingFrameFields()
	{
		assertThrows(IllegalArgumentException.class, () -> TimingJsonSchema.validateLine(
			TimingJsonSchema.frameFixture().replace(TimingJsonSchema.SCHEMA, "rlhd.renderer.timing/v2")));
		assertThrows(IllegalArgumentException.class, () -> TimingJsonSchema.validateLine(
			"{\"schema\":\"rlhd.renderer.timing/v1\",\"type\":\"sample\",\"backend\":\"metal-control\"}"));
		assertThrows(IllegalArgumentException.class, () -> TimingJsonSchema.validateLine(
			TimingJsonSchema.frameFixture().replace("\"gpu_ns\":", "\"missing_gpu_ns\":")));
	}
}
