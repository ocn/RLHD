package rs117.hd.renderer;

import java.nio.ByteBuffer;
import java.util.Objects;

public final class PreparedUiTexture {
	public enum PixelFormat { BGRA8_SRGB_PREMULTIPLIED }

	private final int width;
	private final int height;
	private final int rowStride;
	private final PixelFormat pixelFormat;
	private final ByteBuffer pixels;

	public PreparedUiTexture(int width, int height, int rowStride, PixelFormat pixelFormat, ByteBuffer pixels) {
		long minimumRowStride = (long) width * 4;
		if (width < 0 || height < 0 || rowStride < 0 || rowStride < minimumRowStride)
			throw new IllegalArgumentException("UI extent and BGRA row stride are inconsistent");
		Objects.requireNonNull(pixelFormat, "pixelFormat");
		Objects.requireNonNull(pixels, "pixels");
		long required = (long) rowStride * height;
		if (required > Integer.MAX_VALUE || pixels.remaining() != (int) required)
			throw new IllegalArgumentException("UI pixels must contain every complete row exactly once");
		ByteBuffer copy = ByteBuffer.allocate((int) required);
		copy.put(pixels.duplicate()).flip();
		this.width = width;
		this.height = height;
		this.rowStride = rowStride;
		this.pixelFormat = pixelFormat;
		this.pixels = copy.asReadOnlyBuffer();
	}

	public int width() { return width; }
	public int height() { return height; }
	public int rowStride() { return rowStride; }
	public PixelFormat pixelFormat() { return pixelFormat; }
	public ByteBuffer pixels() { return pixels.asReadOnlyBuffer(); }
}
