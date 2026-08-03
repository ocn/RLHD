package rs117.hd.spikes.macos;

import java.util.Objects;

public final class SurfaceExtent
{
	private final int logicalWidth;
	private final int logicalHeight;
	private final double pixelWidth;
	private final double pixelHeight;
	private final double backingScale;
	private final boolean suspended;

	private SurfaceExtent(int logicalWidth, int logicalHeight, double backingScale)
	{
		this.logicalWidth = logicalWidth;
		this.logicalHeight = logicalHeight;
		this.pixelWidth = logicalWidth * backingScale;
		this.pixelHeight = logicalHeight * backingScale;
		this.backingScale = backingScale;
		this.suspended = logicalWidth == 0 || logicalHeight == 0;
	}

	public static SurfaceExtent of(int logicalWidth, int logicalHeight, double backingScale)
	{
		if (logicalWidth < 0 || logicalHeight < 0)
		{
			throw new IllegalArgumentException("Logical surface dimensions must be non-negative.");
		}
		if (!Double.isFinite(backingScale) || backingScale <= 0)
		{
			throw new IllegalArgumentException("Backing scale must be finite and positive.");
		}
		if (!Double.isFinite(logicalWidth * backingScale) || !Double.isFinite(logicalHeight * backingScale))
		{
			throw new IllegalArgumentException("Pixel surface dimensions must be finite.");
		}
		return new SurfaceExtent(logicalWidth, logicalHeight, backingScale);
	}

	public int logicalWidth()
	{
		return logicalWidth;
	}

	public int logicalHeight()
	{
		return logicalHeight;
	}

	public double pixelWidth()
	{
		return pixelWidth;
	}

	public double pixelHeight()
	{
		return pixelHeight;
	}

	public double backingScale()
	{
		return backingScale;
	}

	public boolean suspended()
	{
		return suspended;
	}

	@Override
	public boolean equals(Object object)
	{
		if (this == object)
		{
			return true;
		}
		if (!(object instanceof SurfaceExtent))
		{
			return false;
		}
		SurfaceExtent other = (SurfaceExtent) object;
		return logicalWidth == other.logicalWidth &&
			logicalHeight == other.logicalHeight &&
			Double.compare(pixelWidth, other.pixelWidth) == 0 &&
			Double.compare(pixelHeight, other.pixelHeight) == 0 &&
			Double.compare(backingScale, other.backingScale) == 0 &&
			suspended == other.suspended;
	}

	@Override
	public int hashCode()
	{
		return Objects.hash(logicalWidth, logicalHeight, pixelWidth, pixelHeight, backingScale, suspended);
	}

	@Override
	public String toString()
	{
		return "SurfaceExtent{" +
			"logical=" + logicalWidth + 'x' + logicalHeight +
			", pixel=" + pixelWidth + 'x' + pixelHeight +
			", scale=" + backingScale +
			", suspended=" + suspended +
			'}';
	}
}
