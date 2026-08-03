package rs117.hd.spikes.vulkan.control;

import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.lwjgl.vulkan.KHRSurface.VK_COMPOSITE_ALPHA_OPAQUE_BIT_KHR;
import static org.lwjgl.vulkan.KHRSurface.VK_COMPOSITE_ALPHA_PRE_MULTIPLIED_BIT_KHR;
import static org.lwjgl.vulkan.KHRSurface.VK_PRESENT_MODE_FIFO_KHR;
import static org.lwjgl.vulkan.KHRSurface.VK_PRESENT_MODE_IMMEDIATE_KHR;
import static org.lwjgl.vulkan.KHRSurface.VK_PRESENT_MODE_MAILBOX_KHR;

public class SwapchainSelectionTest
{
	@Test
	public void clampsThreeImagesAndVariableExtentToAdvertisedLimits()
	{
		SwapchainSelection selection = SwapchainSelection.choose(4, 5, -1, -1,
			320, 200, 1600, 900, 2400, 100,
			VK_COMPOSITE_ALPHA_PRE_MULTIPLIED_BIT_KHR, 8,
			Arrays.asList(VK_PRESENT_MODE_FIFO_KHR, VK_PRESENT_MODE_MAILBOX_KHR), VulkanPresentMode.UNLOCKED);

		assertEquals(4, selection.requestedImageCount());
		assertEquals(1600, selection.width());
		assertEquals(200, selection.height());
		assertEquals(VK_COMPOSITE_ALPHA_PRE_MULTIPLIED_BIT_KHR, selection.compositeAlpha());
		assertEquals(8, selection.preTransform());
		assertEquals(VulkanPresentMode.MAILBOX, selection.effectivePresentMode());
	}

	@Test
	public void fixedExtentAndImmediateRemainTruthful()
	{
		SwapchainSelection selection = SwapchainSelection.choose(2, 0, 1024, 768,
			1, 1, 4096, 4096, 800, 600,
			VK_COMPOSITE_ALPHA_OPAQUE_BIT_KHR, 1,
			Arrays.asList(VK_PRESENT_MODE_FIFO_KHR, VK_PRESENT_MODE_IMMEDIATE_KHR), VulkanPresentMode.UNLOCKED);
		assertEquals(3, selection.requestedImageCount());
		assertEquals(1024, selection.width());
		assertEquals(768, selection.height());
		assertEquals(VulkanPresentMode.UNLOCKED, selection.effectivePresentMode());
		selection.requireActualImageCount(4);
		assertThrows(IllegalArgumentException.class, () -> selection.requireActualImageCount(2));
	}

	@Test
	public void zeroCallerExtentSuspendsWithoutSelectingADeviceExtent()
	{
		SwapchainSelection selection = SwapchainSelection.choose(1, 0, -1, -1,
			1, 1, 4096, 4096, 0, 600,
			VK_COMPOSITE_ALPHA_OPAQUE_BIT_KHR, 1,
			Collections.singletonList(VK_PRESENT_MODE_FIFO_KHR), VulkanPresentMode.FIFO);
		assertTrue(selection.suspended());
	}

	@Test
	public void rejectsASurfaceThatCannotProvideThreeImages()
	{
		assertThrows(IllegalArgumentException.class, () -> SwapchainSelection.choose(1, 2, -1, -1,
			1, 1, 4096, 4096, 640, 480, VK_COMPOSITE_ALPHA_OPAQUE_BIT_KHR, 1,
			Collections.singletonList(VK_PRESENT_MODE_FIFO_KHR), VulkanPresentMode.FIFO));
	}
}
