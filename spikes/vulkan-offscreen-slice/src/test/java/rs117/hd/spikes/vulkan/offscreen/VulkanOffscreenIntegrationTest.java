package rs117.hd.spikes.vulkan.offscreen;

import org.junit.Assume;
import org.junit.Test;
import rs117.hd.spikes.vulkan.opaque.VulkanOpaqueZoneContract;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class VulkanOffscreenIntegrationTest
{
	@Test
	public void rendersTask1BaseZoneAndExactUiToReadback() throws Exception
	{
		Assume.assumeTrue(Boolean.getBoolean("rlhd.spike.vulkan.offscreen.integration"));
		VulkanOffscreenRenderer renderer = VulkanOffscreenRenderer.open();
		try
		{
			assertTrue("Validation layer must be enabled for live offscreen acceptance", renderer.validationEnabled());
			int baselineHandles = renderer.liveHandleCount();
			VulkanOpaqueZoneContract.PreparedUpload upload = Task1VulkanFixture.upload();
			assertEquals(Task1VulkanFixture.OPAQUE_SHA256, Task1VulkanFixture.sha256Words(upload.vertexBytes()));
			assertEquals(Task1VulkanFixture.FACE_SHA256, Task1VulkanFixture.sha256Words(upload.faceMetadataBytes()));
			VulkanOffscreenRenderer.Result result = renderer.render(
				upload, Task1VulkanFixture.frame(new byte[16]));
			assertEquals(64, result.width());
			assertEquals(64, result.height());
			assertEquals(18, result.opaqueVertexCount());
			assertEquals(64 * 64 * 4, result.bgra().length);
			int coloredPixels = 0;
			int opaqueAlphaPixels = 0;
			byte[] pixels = result.bgra();
			for (int offset = 0; offset < pixels.length; offset += 4)
			{
				if ((pixels[offset] & 0xff) != 0 || (pixels[offset + 1] & 0xff) != 0 || (pixels[offset + 2] & 0xff) != 0)
					coloredPixels++;
				if ((pixels[offset + 3] & 0xff) == 255) opaqueAlphaPixels++;
			}
			assertEquals("Every output pixel must retain opaque alpha", 64 * 64, opaqueAlphaPixels);
			assertEquals("Task 1 top-down footprint must cover the analytic 52x52 pixel-center region", 52 * 52, coloredPixels);
			assertSceneMask(pixels);
			assertPixel(pixels, 2, 2, 0, 0, 0, 255, 0);
			assertPixel(pixels, 48, 16, 59, 59, 67, 255, 2);
			assertPixel(pixels, 16, 48, 40, 41, 46, 255, 2);

			VulkanOffscreenRenderer.Result composedResult = renderer.render(
				upload, Task1VulkanFixture.frame(Task1VulkanFixture.exactUi()));
			assertComposedFrame(pixels, composedResult.bgra());
			assertEquals(baselineHandles, renderer.liveHandleCount());
		}
		finally
		{
			renderer.close();
		}
		assertEquals(0, renderer.liveHandleCount());
		assertTrue(renderer.validationEnabled());
		assertEquals(0, renderer.validationWarningCount());
		assertEquals(0, renderer.validationErrorCount());
	}

	private static void assertSceneMask(byte[] pixels)
	{
		for (int y = 0; y < 64; y++)
		{
			for (int x = 0; x < 64; x++)
			{
				int offset = (y * 64 + x) * 4;
				boolean nonBlack = (pixels[offset] & 0xff) != 0 || (pixels[offset + 1] & 0xff) != 0 ||
					(pixels[offset + 2] & 0xff) != 0;
				assertEquals("scene mask at " + x + ',' + y, x >= 6 && x <= 57 && y >= 6 && y <= 57, nonBlack);
				assertEquals("scene alpha at " + x + ',' + y, 255, pixels[offset + 3] & 0xff);
			}
		}
	}

	private static void assertComposedFrame(byte[] scene, byte[] composed)
	{
		for (int y = 0; y < 64; y++)
		{
			for (int x = 0; x < 64; x++)
			{
				int offset = (y * 64 + x) * 4;
				if (y < 32 && x < 32)
				{
					assertChannel(composed, offset, roundedHalf(scene[offset] & 0xff), 1, "half-red blue", x, y);
					assertChannel(composed, offset + 1, roundedHalf(scene[offset + 1] & 0xff), 1, "half-red green", x, y);
					assertChannel(composed, offset + 2, 128 + roundedHalf(scene[offset + 2] & 0xff), 1, "half-red red", x, y);
				}
				else if (y < 32)
				{
					assertPixel(composed, x, y, 0, 255, 0, 255, 0);
				}
				else if (x < 32)
				{
					assertPixel(composed, x, y, scene[offset] & 0xff, scene[offset + 1] & 0xff,
						scene[offset + 2] & 0xff, 255, 0);
				}
				else
				{
					assertPixel(composed, x, y, 255, 0, 0, 255, 0);
				}
				assertEquals("composed alpha at " + x + ',' + y, 255, composed[offset + 3] & 0xff);
			}
		}
	}

	private static int roundedHalf(int value)
	{
		return Math.round(value * (127f / 255f));
	}

	private static void assertChannel(byte[] pixels, int offset, int expected, int tolerance, String label, int x, int y)
	{
		assertTrue(label + " at " + x + ',' + y,
			Math.abs((pixels[offset] & 0xff) - expected) <= tolerance);
	}

	private static void assertPixel(byte[] pixels, int x, int y, int blue, int green, int red, int alpha, int rgbTolerance)
	{
		int offset = (y * 64 + x) * 4;
		assertTrue("blue at " + x + ',' + y, Math.abs((pixels[offset] & 0xff) - blue) <= rgbTolerance);
		assertTrue("green at " + x + ',' + y, Math.abs((pixels[offset + 1] & 0xff) - green) <= rgbTolerance);
		assertTrue("red at " + x + ',' + y, Math.abs((pixels[offset + 2] & 0xff) - red) <= rgbTolerance);
		assertEquals("alpha at " + x + ',' + y, alpha, pixels[offset + 3] & 0xff);
	}

}
