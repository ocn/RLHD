package rs117.hd.spikes.macos.control;

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class SyntheticUiTest
{
	@Test
	public void writesDeterministicPremultipliedBgraSentinels()
	{
		byte[] bytes = new byte[SyntheticUi.byteCount(4, 4)];
		SyntheticUi.fillPremultipliedBgra(bytes, 4, 4, 9);

		assertPixel(bytes, 4, 0, 0, 0, 0, 0, 0);
		assertPixel(bytes, 4, 3, 0, 0, 0, 128, 128);
		assertPixel(bytes, 4, 0, 3, 255, 0, 0, 255);
		for (int index = 0; index < bytes.length; index += 4)
		{
			int alpha = unsigned(bytes[index + 3]);
			assertTrue(unsigned(bytes[index]) <= alpha);
			assertTrue(unsigned(bytes[index + 1]) <= alpha);
			assertTrue(unsigned(bytes[index + 2]) <= alpha);
		}
	}

	@Test
	public void animationChangesOnlyAnimatedSentinelRegion()
	{
		byte[] first = new byte[SyntheticUi.byteCount(4, 4)];
		byte[] second = new byte[first.length];
		SyntheticUi.fillPremultipliedBgra(first, 4, 4, 1);
		SyntheticUi.fillPremultipliedBgra(second, 4, 4, 2);

		assertArrayEquals(slice(first, 0, 3 * 4), slice(second, 0, 3 * 4));
		assertNotEquals(unsigned(first[(3 * 4 + 3) * 4]), unsigned(second[(3 * 4 + 3) * 4]));
	}

	@Test
	public void validatesDimensionsAndExactStorage()
	{
		assertThrows(IllegalArgumentException.class, () -> SyntheticUi.byteCount(0, 1));
		assertThrows(ArithmeticException.class, () -> SyntheticUi.byteCount(Integer.MAX_VALUE, 2));
		assertThrows(IllegalArgumentException.class, () -> SyntheticUi.fillPremultipliedBgra(new byte[3], 1, 1, 0));
	}

	private static void assertPixel(byte[] bytes, int width, int x, int y, int blue, int green, int red, int alpha)
	{
		int index = (y * width + x) * 4;
		assertArrayEquals(new byte[]{(byte) blue, (byte) green, (byte) red, (byte) alpha}, slice(bytes, index, index + 4));
	}

	private static byte[] slice(byte[] bytes, int from, int to)
	{
		byte[] copy = new byte[to - from];
		System.arraycopy(bytes, from, copy, 0, copy.length);
		return copy;
	}

	private static int unsigned(byte value)
	{
		return value & 0xff;
	}
}
