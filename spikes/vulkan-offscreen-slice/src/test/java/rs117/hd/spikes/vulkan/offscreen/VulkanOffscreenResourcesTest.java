package rs117.hd.spikes.vulkan.offscreen;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class VulkanOffscreenResourcesTest
{
	@Test
	public void selectsACompatibleAdvertisedMemoryType()
	{
		assertEquals(2, VulkanOffscreenResources.selectMemoryType(
			0b1101, new int[] { 0b001, 0b111, 0b101, 0b011 }, 0b100));
		assertEquals(-1, VulkanOffscreenResources.selectMemoryType(
			0b0011, new int[] { 0b001, 0b010 }, 0b100));
	}
}
