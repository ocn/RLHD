package rs117.hd.spikes.macos.control;

import java.util.Objects;

public final class SyntheticUi
{
	private SyntheticUi()
	{
	}

	public static int byteCount(int width, int height)
	{
		if (width <= 0 || height <= 0)
		{
			throw new IllegalArgumentException("UI dimensions must be positive.");
		}
		return Math.multiplyExact(Math.multiplyExact(width, height), 4);
	}

	public static void fillPremultipliedBgra(byte[] destination, int width, int height, long frameId)
	{
		Objects.requireNonNull(destination, "destination");
		int expected = byteCount(width, height);
		if (destination.length != expected)
		{
			throw new IllegalArgumentException("Expected " + expected + " BGRA bytes.");
		}
		for (int y = 0; y < height; y++)
		{
			for (int x = 0; x < width; x++)
			{
				int index = (y * width + x) * 4;
				if (x < width / 2 && y < height / 2)
				{
					write(destination, index, 0, 0, 0, 0);
				}
				else if (x >= width / 2 && y < height / 2)
				{
					write(destination, index, 0, 0, 128, 128);
				}
				else if (x < width / 2)
				{
					write(destination, index, 255, 0, 0, 255);
				}
				else
				{
					int alpha = 192;
					int sourceRed = (int) ((frameId * 5 + x * 3 + y) & 0xff);
					int sourceGreen = (int) ((frameId * 3 + x + y * 5) & 0xff);
					int sourceBlue = (int) ((frameId * 7 + x * 2 + y * 3) & 0xff);
					write(destination, index, premultiply(sourceBlue, alpha), premultiply(sourceGreen, alpha), premultiply(sourceRed, alpha), alpha);
				}
			}
		}
	}

	static int premultiply(int channel, int alpha)
	{
		return (channel * alpha + 127) / 255;
	}

	private static void write(byte[] bytes, int index, int blue, int green, int red, int alpha)
	{
		bytes[index] = (byte) blue;
		bytes[index + 1] = (byte) green;
		bytes[index + 2] = (byte) red;
		bytes[index + 3] = (byte) alpha;
	}
}
