package rs117.hd.renderer;

import java.util.Objects;

public final class SurfaceExtent {
	private final int width;
	private final int height;

	public SurfaceExtent(int width, int height) {
		if (width < 0 || height < 0)
			throw new IllegalArgumentException("Surface extent must be non-negative");
		this.width = width;
		this.height = height;
	}

	public int width() { return width; }
	public int height() { return height; }
	public boolean isZero() { return width == 0 || height == 0; }

	@Override
	public boolean equals(Object other) {
		return this == other || other instanceof SurfaceExtent && width == ((SurfaceExtent) other).width &&
			height == ((SurfaceExtent) other).height;
	}

	@Override
	public int hashCode() { return Objects.hash(width, height); }
}
