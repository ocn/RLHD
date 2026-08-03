package rs117.hd.spikes.macos.control;

import java.nio.file.Path;
import java.util.Objects;
import rs117.hd.spikes.macos.MacMetalSurface;
import rs117.hd.spikes.macos.SurfaceExtent;

public final class MetalControlRenderer implements AutoCloseable
{
	private enum Phase { RUNNING, CLOSING, CLOSED }

	private final NativeRendererAccess nativeAccess;
	private long stateHandle;
	private Phase phase = Phase.RUNNING;
	private MetalControlCounters finalCounters;
	private byte[] uiBytes = new byte[0];
	private int uiWidth;
	private int uiHeight;

	public MetalControlRenderer(MacMetalSurface surface, Path timingLog, PresentMode presentMode)
	{
		this(MetalControlNative.INSTANCE, Objects.requireNonNull(surface, "surface").metalLayerHandle(), timingLog, presentMode,
			System.getProperty("rlhd.spike.metal.failure", ""));
	}

	MetalControlRenderer(NativeRendererAccess nativeAccess, long layerHandle, Path timingLog, PresentMode presentMode, String failureStage)
	{
		this.nativeAccess = Objects.requireNonNull(nativeAccess, "nativeAccess");
		Objects.requireNonNull(timingLog, "timingLog");
		Objects.requireNonNull(presentMode, "presentMode");
		stateHandle = nativeAccess.create(layerHandle, timingLog.toAbsolutePath().toString(), presentMode, failureStage == null ? "" : failureStage);
		if (stateHandle == 0)
		{
			phase = Phase.CLOSED;
			throw new IllegalStateException("Native Metal control state allocation failed.");
		}
		if (!nativeAccess.ready(stateHandle))
		{
			long failedHandle = stateHandle;
			stateHandle = 0;
			phase = Phase.CLOSED;
			nativeAccess.close(failedHandle);
			throw new IllegalStateException("Native Metal control initialization failed; inspect the timing log counters.");
		}
	}

	public synchronized FrameOutcome render(SurfaceExtent extent, long frameId)
	{
		ensureRunning();
		Objects.requireNonNull(extent, "extent");
		if (extent.suspended())
		{
			return FrameOutcome.fromNativeCode(nativeAccess.skipSuspended(stateHandle, frameId));
		}
		int width = Math.max(1, Math.toIntExact(Math.round(extent.pixelWidth())));
		int height = Math.max(1, Math.toIntExact(Math.round(extent.pixelHeight())));
		long generationStart = System.nanoTime();
		ensureUiBuffer(width, height);
		SyntheticUi.fillPremultipliedBgra(uiBytes, width, height, frameId);
		long generationNs = System.nanoTime() - generationStart;
		return FrameOutcome.fromNativeCode(nativeAccess.render(stateHandle, width, height, frameId, generationNs, uiBytes));
	}

	public synchronized void setPresentMode(PresentMode mode)
	{
		ensureRunning();
		nativeAccess.setPresentMode(stateHandle, Objects.requireNonNull(mode, "mode"));
	}

	public synchronized MetalControlCounters counters()
	{
		if (phase == Phase.CLOSED && finalCounters != null)
		{
			return finalCounters;
		}
		ensureRunning();
		return new MetalControlCounters(nativeAccess.counters(stateHandle));
	}

	public synchronized boolean runReadbackCheck()
	{
		ensureRunning();
		return nativeAccess.runReadbackCheck(stateHandle);
	}

	@Override
	public synchronized void close()
	{
		ensureRunning();
		phase = Phase.CLOSING;
		try
		{
			finalCounters = new MetalControlCounters(nativeAccess.close(stateHandle));
		}
		catch (RuntimeException | Error ex)
		{
			phase = Phase.RUNNING;
			throw ex;
		}
		stateHandle = 0;
		uiBytes = new byte[0];
		uiWidth = 0;
		uiHeight = 0;
		phase = Phase.CLOSED;
	}

	private void ensureUiBuffer(int width, int height)
	{
		if (width != uiWidth || height != uiHeight)
		{
			uiBytes = new byte[SyntheticUi.byteCount(width, height)];
			uiWidth = width;
			uiHeight = height;
		}
	}

	private void ensureRunning()
	{
		if (phase != Phase.RUNNING)
		{
			throw new IllegalStateException("Metal control renderer is not running.");
		}
	}
}
