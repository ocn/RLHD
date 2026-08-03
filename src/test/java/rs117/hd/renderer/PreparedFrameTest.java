package rs117.hd.renderer;

import java.nio.ByteBuffer;
import java.nio.ReadOnlyBufferException;
import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class PreparedFrameTest {
	@Test
	public void frameOwnsImmutableCameraAndUiSnapshots() {
		float[] matrix = {
			1, 0, 0, 0,
			0, 1, 0, 0,
			0, 0, 1, 0,
			0, 0, 0, 1
		};
		ByteBuffer pixels = ByteBuffer.allocate(16);
		pixels.put(new byte[] { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16 }).flip();
		ZoneKey key = new ZoneKey(0, 5, 7, 11);
		PreparedFrame frame = new PreparedFrame(
			key,
			new CameraUniforms(matrix),
			new SurfaceExtent(2, 2),
			new PreparedUiTexture(2, 2, 8, PreparedUiTexture.PixelFormat.BGRA8_SRGB_PREMULTIPLIED, pixels)
		);

		matrix[0] = 99;
		pixels.put(0, (byte) 99);
		assertArrayEquals(new float[] {
			1, 0, 0, 0,
			0, 1, 0, 0,
			0, 0, 1, 0,
			0, 0, 0, 1
		}, frame.camera().clipFromWorld(), 0);
		ByteBuffer snapshot = frame.ui().pixels();
		assertEquals(1, snapshot.get(0));
		assertEquals(0, snapshot.position());
		assertEquals(16, snapshot.remaining());
		assertTrue(snapshot.isReadOnly());
		assertThrows(ReadOnlyBufferException.class, () -> snapshot.put(0, (byte) 4));
		assertNotSame(snapshot, frame.ui().pixels());
		assertEquals(key, frame.zone());
	}

	@Test
	public void uiSnapshotRequiresCompleteBgraRows() {
		assertThrows(IllegalArgumentException.class, () -> new PreparedUiTexture(
			2, 1, 7, PreparedUiTexture.PixelFormat.BGRA8_SRGB_PREMULTIPLIED, ByteBuffer.allocate(8)));
		assertThrows(IllegalArgumentException.class, () -> new PreparedUiTexture(
			2, 2, 8, PreparedUiTexture.PixelFormat.BGRA8_SRGB_PREMULTIPLIED, ByteBuffer.allocate(15)));
	}

	@Test
	public void zeroExtentIsAValidSuspendedFrame() {
		SurfaceExtent extent = new SurfaceExtent(0, 720);
		assertTrue(extent.isZero());
		assertEquals(0, extent.width());
		assertEquals(720, extent.height());
	}
}
