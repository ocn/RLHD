package rs117.hd.spikes.vulkan.control;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.assertThrows;

public class VulkanTimingJsonSchemaTest
{
	@Rule
	public final TemporaryFolder temporaryFolder = new TemporaryFolder();

	@Test
	public void loggerPreservesRequiredNullFields() throws Exception
	{
		Path path = temporaryFolder.newFile("vulkan.jsonl").toPath();
		VulkanTimingLog.CapabilityRecord capabilities = new VulkanTimingLog.CapabilityRecord();
		capabilities.portabilitySubset = true;
		capabilities.colorSpace = "VK_COLOR_SPACE_SRGB_NONLINEAR_KHR";
		capabilities.requestedImages = 3;
		capabilities.actualImages = 3;
		capabilities.validationRequested = true;
		capabilities.validationEnabled = true;
		VulkanTimingLog.FrameRecord frame = new VulkanTimingLog.FrameRecord();
		frame.frameId = 1;
		frame.width = 8;
		frame.height = 8;
		frame.requested = VulkanPresentMode.FIFO;
		frame.effective = VulkanPresentMode.FIFO;
		frame.outcome = VulkanFrameOutcome.SUBMITTED;
		long[] counters = new long[VulkanControlCounters.FIELD_COUNT];
		counters[3] = 1;
		counters[4] = 1;
		try (VulkanTimingLog log = new VulkanTimingLog(path))
		{
			log.runStart(VulkanPresentMode.FIFO, VulkanPresentMode.FIFO, capabilities);
			log.frame(frame, counters);
			log.runEnd(VulkanPresentMode.FIFO, VulkanPresentMode.FIFO, counters, null);
		}
		VulkanTimingJsonSchema.validateLog(Files.readAllLines(path));
	}

	@Test
	public void acceptsStrictCompatibleRunWithCapabilities()
	{
		VulkanTimingJsonSchema.validateLog(Arrays.asList(
			VulkanTimingJsonSchema.runStartFixture(),
			VulkanTimingJsonSchema.frameFixture(),
			VulkanTimingJsonSchema.runEndFixture()));
	}

	@Test(expected = IllegalArgumentException.class)
	public void rejectsCustomPresentCapability()
	{
		VulkanTimingJsonSchema.validateLine(VulkanTimingJsonSchema.runStartFixture().replace("\"standard_queue_present\":true", "\"standard_queue_present\":false"));
	}

	@Test(expected = IllegalArgumentException.class)
	public void rejectsDuplicateJsonKeys()
	{
		VulkanTimingJsonSchema.validateLine(VulkanTimingJsonSchema.frameFixture().replace("\"frame_id\":7", "\"frame_id\":7,\"frame_id\":8"));
	}

	@Test
	public void acceptsMailboxOnlyAsAnEffectiveVblankSynchronizedMode()
	{
		VulkanTimingJsonSchema.validateLine(VulkanTimingJsonSchema.runStartFixture()
			.replace("\"effective_present_mode\":\"fifo-like\"", "\"effective_present_mode\":\"mailbox\""));
		assertThrows(IllegalArgumentException.class, () -> VulkanTimingJsonSchema.validateLine(VulkanTimingJsonSchema.runStartFixture()
			.replace("\"requested_present_mode\":\"fifo-like\"", "\"requested_present_mode\":\"mailbox\"")));
	}

	@Test
	public void acceptsOptionalPortabilitySubsetAndClampedImageCount()
	{
		VulkanTimingJsonSchema.validateLine(VulkanTimingJsonSchema.runStartFixture()
			.replace("\"portability_subset\":true", "\"portability_subset\":false")
			.replace("\"requested_images\":3,\"actual_images\":3", "\"requested_images\":4,\"actual_images\":5"));
		assertThrows(IllegalArgumentException.class, () -> VulkanTimingJsonSchema.validateLine(VulkanTimingJsonSchema.runStartFixture()
			.replace("\"requested_images\":3,\"actual_images\":3", "\"requested_images\":4,\"actual_images\":3")));
	}

	@Test
	public void rejectsEffectiveValidationThatWasNotRequested()
	{
		assertThrows(IllegalArgumentException.class, () -> VulkanTimingJsonSchema.validateLine(VulkanTimingJsonSchema.runStartFixture()
			.replace("\"validation_requested\":false,\"validation_enabled\":false", "\"validation_requested\":false,\"validation_enabled\":true")));
	}
}
