package rs117.hd.spikes.vulkan.offscreen;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.Test;
import rs117.hd.renderer.PreparedUiTexture;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;

public class VulkanOffscreenRendererTest
{
	@Test
	public void sourcePinsTheReviewedOpaqueAndUiPipelineState() throws Exception
	{
		Path source = Paths.get("spikes/vulkan-offscreen-slice/src/main/java/rs117/hd/spikes/vulkan/offscreen/VulkanOffscreenRenderer.java");
		String text = new String(Files.readAllBytes(source), StandardCharsets.UTF_8);
		for (String required : new String[] {
			"VK_FORMAT_B8G8R8A8_UNORM", "VK_FORMAT_D32_SFLOAT", "VK_COMPARE_OP_GREATER_OR_EQUAL",
			".height(-call.height)", "VK_FRONT_FACE_CLOCKWISE", "VK_CULL_MODE_BACK_BIT",
			"VK_BLEND_FACTOR_ONE_MINUS_SRC_ALPHA", "VK_FORMAT_R16G16B16A16_SFLOAT",
			"vkCmdCopyImageToBuffer", "VK_ACCESS_HOST_READ_BIT"
		}) assertTrue(required, text.contains(required));
	}

	@Test
	public void readbackResultOwnsAndDefensivelyCopiesPixels()
	{
		byte[] input = { 1, 2, 3, 4 };
		VulkanOffscreenRenderer.Result result = new VulkanOffscreenRenderer.Result(1, 1, input, 3);
		input[0] = 9;
		byte[] first = result.bgra();
		byte[] second = result.bgra();
		assertArrayEquals(new byte[] { 1, 2, 3, 4 }, first);
		assertNotSame(first, second);
	}

	@Test
	public void uiRowsAreRepackedWithoutAssumingVulkanCompatiblePadding()
	{
		ByteBuffer source = ByteBuffer.wrap(new byte[] { 1, 2, 3, 4, 99, 5, 6, 7, 8, 98 });
		PreparedUiTexture ui = new PreparedUiTexture(1, 2, 5,
			PreparedUiTexture.PixelFormat.BGRA8_SRGB_PREMULTIPLIED, source);
		ByteBuffer packed = VulkanOffscreenRenderer.tightlyPackedUi(ui);
		byte[] actual = new byte[packed.remaining()];
		packed.get(actual);
		assertArrayEquals(new byte[] { 1, 2, 3, 4, 5, 6, 7, 8 }, actual);
	}
}
