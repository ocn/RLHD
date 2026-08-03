package rs117.hd.spikes.vulkan.control;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class VulkanLiveRiskLevelTest
{
	@Test
	public void parsesOnlyExplicitSupportedRiskLevels()
	{
		for (VulkanLiveRiskLevel riskLevel : VulkanLiveRiskLevel.values())
		{
			assertEquals(riskLevel, VulkanLiveRiskLevel.parseRequired(riskLevel.optionName()));
		}

		assertThrows(IllegalArgumentException.class, () -> VulkanLiveRiskLevel.parseRequired(null));
		assertThrows(IllegalArgumentException.class, () -> VulkanLiveRiskLevel.parseRequired(""));
		assertThrows(IllegalArgumentException.class, () -> VulkanLiveRiskLevel.parseRequired("fullscreen "));
		assertThrows(IllegalArgumentException.class, () -> VulkanLiveRiskLevel.parseRequired("FULLSCREEN"));
		assertThrows(IllegalArgumentException.class, () -> VulkanLiveRiskLevel.parseRequired("all"));
	}

	@Test
	public void optionalRiskTransitionsAreMutuallyExclusive()
	{
		assertTrue(VulkanLiveRiskLevel.RESIZE_SUSPEND_RESTORE.exercisesResizeSuspendRestore());
		assertFalse(VulkanLiveRiskLevel.RESIZE_SUSPEND_RESTORE.exercisesUnlockedPresent());
		assertFalse(VulkanLiveRiskLevel.RESIZE_SUSPEND_RESTORE.exercisesFullscreen());

		assertFalse(VulkanLiveRiskLevel.UNLOCKED_PRESENT.exercisesResizeSuspendRestore());
		assertTrue(VulkanLiveRiskLevel.UNLOCKED_PRESENT.exercisesUnlockedPresent());
		assertFalse(VulkanLiveRiskLevel.UNLOCKED_PRESENT.exercisesFullscreen());

		assertFalse(VulkanLiveRiskLevel.FULLSCREEN.exercisesResizeSuspendRestore());
		assertFalse(VulkanLiveRiskLevel.FULLSCREEN.exercisesUnlockedPresent());
		assertTrue(VulkanLiveRiskLevel.FULLSCREEN.exercisesFullscreen());
	}

	@Test
	public void presentationGatesMatchTheSelectedRung()
	{
		assertFalse(VulkanLiveRiskLevel.SURFACE_SWAPCHAIN.presentsFrames());
		assertEquals(0, VulkanLiveRiskLevel.SURFACE_SWAPCHAIN.baselineFrameCount());
		assertTrue(VulkanLiveRiskLevel.FIRST_FIFO_PRESENT.presentsFrames());
		assertEquals(1, VulkanLiveRiskLevel.FIRST_FIFO_PRESENT.baselineFrameCount());
		assertTrue(VulkanLiveRiskLevel.SUSTAINED_FIFO.presentsFrames());
		assertTrue(VulkanLiveRiskLevel.SUSTAINED_FIFO.baselineFrameCount() > 1);
	}

	@Test
	public void recordsTheDocumentedRiskRung()
	{
		assertEquals(5, VulkanLiveRiskLevel.SURFACE_SWAPCHAIN.rung());
		assertEquals(6, VulkanLiveRiskLevel.FIRST_FIFO_PRESENT.rung());
		assertEquals(7, VulkanLiveRiskLevel.SUSTAINED_FIFO.rung());
		assertEquals(8, VulkanLiveRiskLevel.RESIZE_SUSPEND_RESTORE.rung());
		assertEquals(9, VulkanLiveRiskLevel.UNLOCKED_PRESENT.rung());
		assertEquals(10, VulkanLiveRiskLevel.FULLSCREEN.rung());
	}

	@Test
	public void systemPropertyGateFailsClosed()
	{
		String previous = System.getProperty(VulkanLiveRiskLevel.SYSTEM_PROPERTY);
		try
		{
			System.clearProperty(VulkanLiveRiskLevel.SYSTEM_PROPERTY);
			assertThrows(IllegalArgumentException.class, VulkanLiveRiskLevel::fromSystemProperty);
			System.setProperty(VulkanLiveRiskLevel.SYSTEM_PROPERTY, "all");
			assertThrows(IllegalArgumentException.class, VulkanLiveRiskLevel::fromSystemProperty);
			System.setProperty(VulkanLiveRiskLevel.SYSTEM_PROPERTY, "unlocked-present");
			assertEquals(VulkanLiveRiskLevel.UNLOCKED_PRESENT, VulkanLiveRiskLevel.fromSystemProperty());
		}
		finally
		{
			if (previous == null) System.clearProperty(VulkanLiveRiskLevel.SYSTEM_PROPERTY);
			else System.setProperty(VulkanLiveRiskLevel.SYSTEM_PROPERTY, previous);
		}
	}

	@Test
	public void commandLineCannotOverrideTheGradleSelectedRung()
	{
		assertEquals(VulkanLiveRiskLevel.SURFACE_SWAPCHAIN,
			VulkanLiveRiskLevel.selectRequired("surface-swapchain", null));
		assertEquals(VulkanLiveRiskLevel.FULLSCREEN,
			VulkanLiveRiskLevel.selectRequired(null, "fullscreen"));
		assertEquals(VulkanLiveRiskLevel.UNLOCKED_PRESENT,
			VulkanLiveRiskLevel.selectRequired("unlocked-present", "unlocked-present"));
		assertThrows(IllegalArgumentException.class,
			() -> VulkanLiveRiskLevel.selectRequired("surface-swapchain", "fullscreen"));
	}
}
