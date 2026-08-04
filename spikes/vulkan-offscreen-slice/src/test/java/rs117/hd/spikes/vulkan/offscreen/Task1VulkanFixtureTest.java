package rs117.hd.spikes.vulkan.offscreen;

import org.junit.Test;
import rs117.hd.renderer.PreparedFrame;
import rs117.hd.spikes.vulkan.opaque.VulkanOpaqueZoneContract;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

public class Task1VulkanFixtureTest
{
	@Test
	public void retainsMappedUploaderHashesAndTopDownCamera() throws Exception
	{
		VulkanOpaqueZoneContract.PreparedUpload upload = Task1VulkanFixture.upload();
		assertEquals(18, upload.opaqueVertexCount());
		assertEquals(Task1VulkanFixture.OPAQUE_SHA256, Task1VulkanFixture.sha256Words(upload.vertexBytes()));
		assertEquals(Task1VulkanFixture.FACE_SHA256, Task1VulkanFixture.sha256Words(upload.faceMetadataBytes()));

		PreparedFrame frame = Task1VulkanFixture.frame(new byte[16]);
		assertArrayEquals(new float[] {
			.0125f, 0, 0, 0,
			0, 0, 0, 0,
			0, .0125f, 0, 0,
			-.8f, -.8f, 0, 1
		}, frame.camera().clipFromWorld(), 0);
		assertEquals(64, frame.viewport().width());
		assertEquals(64, frame.viewport().height());
	}

	@Test
	public void exactUiFixtureIsStableAndPremultiplied()
	{
		assertArrayEquals(new byte[] {
			0, 0, 0, 0,
			(byte) 255, 0, 0, (byte) 255,
			0, 0, (byte) 128, (byte) 128,
			0, (byte) 255, 0, (byte) 255
		}, Task1VulkanFixture.exactUi());
	}

	@Test
	public void boundedOracleDescriptorIsStable() throws Exception
	{
		assertEquals(Task1VulkanFixture.ORACLE_SHA256, Task1VulkanFixture.oracleSha256());
	}
}
