package rs117.hd.spikes.vulkan.offscreen;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;

public class VulkanOffscreenDeviceTest
{
	@Test
	public void selectsTheFirstPopulatedGraphicsQueue()
	{
		assertEquals(2, VulkanOffscreenDevice.selectGraphicsQueueFamily(
			new int[] { 0, 1, 1 }, new int[] { 2, 0, 1 }));
		assertEquals(-1, VulkanOffscreenDevice.selectGraphicsQueueFamily(
			new int[] { 0, 0 }, new int[] { 1, 1 }));
		assertThrows(IllegalArgumentException.class,
			() -> VulkanOffscreenDevice.selectGraphicsQueueFamily(new int[] { 1 }, new int[0]));
		assertEquals(true, VulkanOffscreenDevice.hasAllFeatures(0b1110, 0b0110));
		assertEquals(false, VulkanOffscreenDevice.hasAllFeatures(0b0010, 0b0110));
	}

	@Test
	public void sourceCannotReferenceWindowSystemIntegration() throws Exception
	{
		Path source = Paths.get("spikes/vulkan-offscreen-slice/src/main/java/rs117/hd/spikes/vulkan/offscreen");
		StringBuilder allSources = new StringBuilder();
		try (java.util.stream.Stream<Path> files = Files.walk(source))
		{
			files.filter(path -> path.toString().endsWith(".java")).forEach(path -> {
				try { allSources.append(new String(Files.readAllBytes(path), StandardCharsets.UTF_8)); }
				catch (java.io.IOException failure) { throw new java.io.UncheckedIOException(failure); }
			});
		}
		String text = allSources.toString();
		for (String forbidden : Arrays.asList("java.awt", "CAMetalLayer", "Surface", "Swapchain", "Present", "Acquire", "Display",
			"KHRSurface", "KHRSwapchain",
			"VkSurfaceKHR", "VkSwapchain", "AcquireNextImage", "QueuePresent", "EXTMetalSurface",
			"KHRWin32Surface", "KHRWaylandSurface", "KHRXlibSurface", "KHRXcbSurface", "KHRDisplay",
			"EXTHeadlessSurface", "MVKMacosSurface", "MVKIosSurface"))
			assertFalse(forbidden, text.contains(forbidden));
	}
}
