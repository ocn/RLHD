package rs117.hd.spikes.vulkan.control;

import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class VulkanCapabilitiesTest
{
	@Test
	public void acceptsRequiredPortableMetalSurfaceConfiguration()
	{
		VulkanCapabilities capabilities = VulkanCapabilities.require(
			Arrays.asList("VK_KHR_surface", "VK_KHR_portability_enumeration", "VK_EXT_metal_surface"),
			Arrays.asList("VK_KHR_swapchain", "VK_KHR_portability_subset"),
			Arrays.asList("VK_FORMAT_B8G8R8A8_UNORM"),
			Arrays.asList("VK_PRESENT_MODE_FIFO_KHR", "VK_PRESENT_MODE_MAILBOX_KHR"), 3);
		assertEquals(VulkanPresentMode.MAILBOX, capabilities.select(VulkanPresentMode.UNLOCKED));
		assertEquals(3, capabilities.imageCount());
	}

	@Test
	public void immediateIsTheOnlyUnlockedEquivalent()
	{
		VulkanCapabilities capabilities = VulkanCapabilities.require(
			Arrays.asList("VK_KHR_surface", "VK_KHR_portability_enumeration", "VK_EXT_metal_surface"),
			Collections.singletonList("VK_KHR_swapchain"),
			Collections.singletonList("VK_FORMAT_B8G8R8A8_UNORM"),
			Arrays.asList("VK_PRESENT_MODE_FIFO_KHR", "VK_PRESENT_MODE_MAILBOX_KHR", "VK_PRESENT_MODE_IMMEDIATE_KHR"), 4);
		assertEquals(VulkanPresentMode.UNLOCKED, capabilities.select(VulkanPresentMode.UNLOCKED));
		assertEquals(Collections.singletonList("VK_KHR_swapchain"), capabilities.requiredDeviceExtensions());
	}

	@Test
	public void fallsBackFromUnlockedToFifoWhenNoUnlockedModeExists()
	{
		VulkanCapabilities capabilities = VulkanCapabilities.require(
			Arrays.asList("VK_KHR_surface", "VK_KHR_portability_enumeration", "VK_EXT_metal_surface"),
			Arrays.asList("VK_KHR_swapchain", "VK_KHR_portability_subset"),
			Arrays.asList("VK_FORMAT_B8G8R8A8_UNORM"),
			Collections.singletonList("VK_PRESENT_MODE_FIFO_KHR"), 3);
		assertEquals(VulkanPresentMode.FIFO, capabilities.select(VulkanPresentMode.UNLOCKED));
	}

	@Test(expected = IllegalArgumentException.class)
	public void rejectsMissingBgraUnorm()
	{
		VulkanCapabilities.require(Arrays.asList("VK_KHR_surface", "VK_KHR_portability_enumeration", "VK_EXT_metal_surface"),
			Arrays.asList("VK_KHR_swapchain", "VK_KHR_portability_subset"),
			Collections.singletonList("VK_FORMAT_R8G8B8A8_UNORM"),
			Collections.singletonList("VK_PRESENT_MODE_FIFO_KHR"), 3);
	}
}
