package rs117.hd.spikes.vulkan.offscreen;

import java.lang.reflect.Constructor;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import rs117.hd.renderer.CameraUniforms;
import rs117.hd.renderer.PreparedFrame;
import rs117.hd.renderer.PreparedUiTexture;
import rs117.hd.renderer.SurfaceExtent;
import rs117.hd.renderer.ZoneKey;
import rs117.hd.renderer.zone.PreparedZoneGeometry;
import rs117.hd.spikes.vulkan.opaque.VulkanOpaqueZoneContract;

final class Task1VulkanFixture
{
	static final String OPAQUE_SHA256 = "eb34eee67ce381cfeccf1a3917ca193fc47b4cd2f8badd64ea19b174a946db8f";
	static final String FACE_SHA256 = "0d44981e3cf76b1413b172a9db5a08b1e6de170a65e2c35e6f098df8d3c02d0c";
	static final String ORACLE_DESCRIPTOR = "rlhd-task1-base-oracle-v1|extent=64x64|mask=x6..57,y6..57|" +
		"samples=48,16:59,59,67;16,48:40,41,46|rgbTolerance=2|" +
		"ui=TL:0,0,128,128;TR:0,255,0,255;BL:0,0,0,0;BR:255,0,0,255|" +
		"blend=ONE,ONE_MINUS_SRC_ALPHA|blendTolerance=1|alpha=255";
	static final String ORACLE_SHA256 = "4577074de8e78763bc9a4c63aedcf179d68746ba6958d82dde2cc2539e51f2df";
	private static final int[] OPAQUE_VERTICES = {
		-2097024, 128, 0, 0, -65536, 0, 0, -3145728, 128, 15360, 0, -65536, 0, 0,
		-1048448, 0, 1006632960, 0, -65536, 0, 0, 0, 0, 1006648320, 0, -65536, 0, 3,
		-1048448, 0, 1006632960, 0, -65536, 0, 3, -3145728, 128, 15360, 0, -65536, 0, 3,
		-2097024, 128, 0, 0, -65536, 0, 6, -3145728, 128, 15360, 0, -65536, 0, 6,
		-1048448, 0, 1006632960, 0, -65536, 0, 6, -1048448, 0, 1006632960, 0, 65536, 0, -2147483642,
		-3145728, 128, 15360, 0, 65536, 0, -2147483642, -2097024, 128, 0, 0, 65536, 0, -2147483642,
		0, 0, 1006648320, 0, -65536, 0, 9, -1048448, 0, 1006632960, 0, -65536, 0, 9,
		-3145728, 128, 15360, 0, -65536, 0, 9, -3145728, 128, 15360, 0, 65536, 0, -2147483639,
		-1048448, 0, 1006632960, 0, 65536, 0, -2147483639, 0, 0, 1006648320, 0, 65536, 0, -2147483639
	};
	private static final int[] FACE_METADATA = {
		30, 40, 20, 8400896, 6303744, 4206592, 1, 1, 1,
		10, 20, 40, 2109440, 4206592, 6303744, 1, 1, 1,
		31, 41, 21, 8400896, 6303744, 4206592, 3, 3, 3,
		11, 21, 41, 2109440, 4206592, 6303744, 3, 3, 3
	};
	private static final int[] LEVEL_OFFSETS = { 42, 126, 126, 126, 126, 126 };
	private static final float[] CLIP_FROM_WORLD = {
		.0125f, 0, 0, 0,
		0, 0, 0, 0,
		0, .0125f, 0, 0,
		-.8f, -.8f, 0, 1
	};

	private Task1VulkanFixture() {}

	static VulkanOpaqueZoneContract.PreparedUpload upload() throws Exception
	{
		Constructor<PreparedZoneGeometry> constructor = PreparedZoneGeometry.class.getDeclaredConstructor(
			IntBuffer.class, IntBuffer.class, IntBuffer.class, int[].class);
		constructor.setAccessible(true);
		PreparedZoneGeometry geometry = constructor.newInstance(written(OPAQUE_VERTICES), written(new int[0]),
			written(FACE_METADATA), LEVEL_OFFSETS.clone());
		return VulkanOpaqueZoneContract.prepare(geometry);
	}

	static PreparedFrame frame(byte[] uiBytes)
	{
		PreparedUiTexture ui = new PreparedUiTexture(2, 2, 8,
			PreparedUiTexture.PixelFormat.BGRA8_SRGB_PREMULTIPLIED, ByteBuffer.wrap(uiBytes));
		return new PreparedFrame(new ZoneKey(0, 5, 5, 1), new CameraUniforms(CLIP_FROM_WORLD),
			0, 0, new SurfaceExtent(64, 64), ui);
	}

	static byte[] exactUi()
	{
		return new byte[] {
			0, 0, 0, 0,
			(byte) 255, 0, 0, (byte) 255,
			0, 0, (byte) 128, (byte) 128,
			0, (byte) 255, 0, (byte) 255
		};
	}

	static String sha256Words(ByteBuffer source) throws Exception
	{
		MessageDigest digest = MessageDigest.getInstance("SHA-256");
		ByteBuffer words = source.duplicate().order(ByteOrder.LITTLE_ENDIAN);
		while (words.hasRemaining())
		{
			int value = words.getInt();
			digest.update((byte) (value >>> 24));
			digest.update((byte) (value >>> 16));
			digest.update((byte) (value >>> 8));
			digest.update((byte) value);
		}
		StringBuilder hex = new StringBuilder();
		for (byte value : digest.digest()) hex.append(String.format("%02x", value));
		return hex.toString();
	}

	static String oracleSha256() throws Exception
	{
		MessageDigest digest = MessageDigest.getInstance("SHA-256");
		StringBuilder hex = new StringBuilder();
		for (byte value : digest.digest(ORACLE_DESCRIPTOR.getBytes(StandardCharsets.UTF_8)))
			hex.append(String.format("%02x", value));
		return hex.toString();
	}

	private static IntBuffer written(int[] values)
	{
		IntBuffer result = IntBuffer.allocate(values.length);
		return result.put(values);
	}
}
