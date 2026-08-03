package rs117.hd.spikes.vulkan.offscreen;

import java.lang.reflect.Constructor;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import org.junit.Assume;
import org.junit.Test;
import rs117.hd.renderer.CameraUniforms;
import rs117.hd.renderer.PreparedFrame;
import rs117.hd.renderer.PreparedUiTexture;
import rs117.hd.renderer.SurfaceExtent;
import rs117.hd.renderer.ZoneKey;
import rs117.hd.renderer.zone.PreparedZoneGeometry;
import rs117.hd.spikes.vulkan.opaque.VulkanOpaqueZoneContract;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class VulkanOffscreenIntegrationTest
{
	@Test
	public void rendersPreparedOpaqueTriangleAndTransparentUiToReadback() throws Exception
	{
		Assume.assumeTrue(Boolean.getBoolean("rlhd.spike.vulkan.offscreen.integration"));
		VulkanOffscreenRenderer renderer = VulkanOffscreenRenderer.open();
		try
		{
			assertTrue("Validation layer must be enabled for live offscreen acceptance", renderer.validationEnabled());
			int baselineHandles = renderer.liveHandleCount();
			VulkanOffscreenRenderer.Result result = renderer.render(triangle(), frame(new byte[16]));
			assertEquals(64, result.width());
			assertEquals(64, result.height());
			assertEquals(3, result.opaqueVertexCount());
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
			byte[] solidUi = new byte[16];
			for (int offset = 0; offset < solidUi.length; offset += 4) { solidUi[offset + 2] = (byte) 128; solidUi[offset + 3] = (byte) 128; }
			VulkanOffscreenRenderer.Result uiResult = renderer.render(empty(), frame(solidUi));
			int uiColoredPixels = 0;
			byte[] uiPixels = uiResult.bgra();
			for (int offset = 0; offset < uiPixels.length; offset += 4)
			{
				if ((uiPixels[offset] & 0xff) != 0 || (uiPixels[offset + 1] & 0xff) != 0 || (uiPixels[offset + 2] & 0xff) != 0)
					uiColoredPixels++;
			}
			assertTrue("Expected render-pass clear/readback alpha; alpha255=" + opaqueAlphaPixels, opaqueAlphaPixels > 4000);
			assertTrue("Expected UI composition in GPU readback; uiColored=" + uiColoredPixels, uiColoredPixels > 4000);
			assertTrue("Expected an opaque clockwise triangle; colored=" + coloredPixels, coloredPixels > 256);
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

	private static VulkanOpaqueZoneContract.PreparedUpload triangle() throws Exception
	{
		int[] vertices = {
			pack(-1, -1), pack(0, 0), 0, 0, 0, 0, 0,
			pack(0, 1), pack(0, 0), 0, 0, 0, 0, 0,
			pack(1, -1), pack(0, 0), 0, 0, 0, 0, 0
		};
		int[] metadata = { 30, 40, 20, 0, 0, 0, 0, 0, 0 };
		Constructor<PreparedZoneGeometry> constructor = PreparedZoneGeometry.class.getDeclaredConstructor(
			IntBuffer.class, IntBuffer.class, IntBuffer.class, int[].class);
		constructor.setAccessible(true);
		PreparedZoneGeometry geometry = constructor.newInstance(written(vertices), written(new int[0]),
			written(metadata), new int[] { vertices.length });
		return VulkanOpaqueZoneContract.prepare(geometry);
	}

	private static VulkanOpaqueZoneContract.PreparedUpload empty() throws Exception
	{
		Constructor<PreparedZoneGeometry> constructor = PreparedZoneGeometry.class.getDeclaredConstructor(
			IntBuffer.class, IntBuffer.class, IntBuffer.class, int[].class);
		constructor.setAccessible(true);
		return VulkanOpaqueZoneContract.prepare(constructor.newInstance(
			written(new int[0]), written(new int[0]), written(new int[0]), new int[0]));
	}

	private static PreparedFrame frame(byte[] uiBytes)
	{
		PreparedUiTexture ui = new PreparedUiTexture(2, 2, 8,
			PreparedUiTexture.PixelFormat.BGRA8_SRGB_PREMULTIPLIED, ByteBuffer.wrap(uiBytes));
		return new PreparedFrame(new ZoneKey(0, 0, 0, 1), new CameraUniforms(new float[] {
			1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1
		}), 0, 0, new SurfaceExtent(64, 64), ui);
	}

	private static IntBuffer written(int[] values)
	{
		IntBuffer result = IntBuffer.allocate(values.length);
		return result.put(values);
	}

	private static int pack(int low, int high)
	{
		return (low & 0xffff) | (high << 16);
	}
}
