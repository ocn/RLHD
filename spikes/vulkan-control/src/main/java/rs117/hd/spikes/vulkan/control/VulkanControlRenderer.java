package rs117.hd.spikes.vulkan.control;

import java.nio.file.Path;
import java.util.Objects;
import rs117.hd.spikes.macos.MacMetalSurface;
import rs117.hd.spikes.macos.SurfaceExtent;
import rs117.hd.spikes.macos.control.SyntheticUi;

public final class VulkanControlRenderer implements AutoCloseable
{
	private enum Phase { RUNNING, CLOSING, CLOSED }

	private final VulkanBackendAccess backend;
	private Phase phase = Phase.RUNNING;
	private VulkanControlCounters finalCounters;
	private byte[] uiBytes = new byte[0];
	private int uiWidth;
	private int uiHeight;

	public VulkanControlRenderer(MacMetalSurface surface, Path timingLog, VulkanPresentMode presentMode, boolean validationRequested)
	{
		this(createBackend(surface, timingLog, presentMode, validationRequested), timingLog);
	}

	public VulkanControlRenderer(MacMetalSurface surface, Path timingLog, VulkanPresentMode presentMode)
	{
		this(surface, timingLog, presentMode, Boolean.parseBoolean(System.getProperty("rlhd.spike.vulkan.validation", "false")));
	}

	VulkanControlRenderer(VulkanBackendAccess backend, Path timingLog)
	{
		this.backend = Objects.requireNonNull(backend, "backend");
		Objects.requireNonNull(timingLog, "timingLog");
		if (!backend.ready())
		{
			long[] counters = new long[VulkanControlCounters.FIELD_COUNT];
			backend.close(counters);
			phase = Phase.CLOSED;
			throw new IllegalStateException("Vulkan control initialization failed; inspect the timing log.");
		}
	}

	public synchronized VulkanFrameOutcome render(SurfaceExtent extent, long frameId)
	{
		ensureRunning();
		Objects.requireNonNull(extent, "extent");
		if (extent.suspended()) return backend.skipSuspended(frameId);
		int width = Math.max(1, Math.toIntExact(Math.round(extent.pixelWidth())));
		int height = Math.max(1, Math.toIntExact(Math.round(extent.pixelHeight())));
		long generationStart = System.nanoTime();
		ensureUiBuffer(width, height);
		SyntheticUi.fillPremultipliedBgra(uiBytes, width, height, frameId);
		long generationNs = System.nanoTime() - generationStart;
		return backend.render(width, height, frameId, generationNs, uiBytes);
	}

	public synchronized void setPresentMode(VulkanPresentMode mode)
	{
		ensureRunning();
		if (mode == VulkanPresentMode.MAILBOX) throw new IllegalArgumentException("MAILBOX is an observed fallback, not a requested unlocked mode.");
		backend.setPresentMode(Objects.requireNonNull(mode, "mode"));
	}

	public synchronized VulkanControlCounters counters()
	{
		if (phase == Phase.CLOSED) return finalCounters;
		ensureRunning();
		return new VulkanControlCounters(backend.counters());
	}

	public synchronized boolean runReadbackCheck()
	{
		ensureRunning();
		byte[] first = new byte[SyntheticUi.byteCount(8, 8)];
		byte[] second = new byte[first.length];
		SyntheticUi.fillPremultipliedBgra(first, 8, 8, 7);
		SyntheticUi.fillPremultipliedBgra(second, 8, 8, 8);
		return backend.runReadbackCheck(first, second);
	}

	@Override
	public synchronized void close()
	{
		ensureRunning();
		phase = Phase.CLOSING;
		long[] raw = new long[VulkanControlCounters.FIELD_COUNT];
		try
		{
			backend.close(raw);
		}
		catch (RuntimeException | Error ex)
		{
			if (ex instanceof VulkanBackendCloseException && ((VulkanBackendCloseException) ex).consumed())
			{
				phase = Phase.CLOSED;
				finalCounters = new VulkanControlCounters(raw);
				uiBytes = new byte[0];
				uiWidth = 0;
				uiHeight = 0;
			}
			else phase = Phase.RUNNING;
			throw ex;
		}
		phase = Phase.CLOSED;
		finalCounters = new VulkanControlCounters(raw);
		uiBytes = new byte[0];
		uiWidth = 0;
		uiHeight = 0;
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
		if (phase != Phase.RUNNING) throw new IllegalStateException("Vulkan control renderer is not running.");
	}

	private static VulkanBackendAccess createBackend(MacMetalSurface surface, Path timingLog,
		VulkanPresentMode presentMode, boolean validationRequested)
	{
		MacMetalSurface checkedSurface = Objects.requireNonNull(surface, "surface");
		SurfaceExtent extent = checkedSurface.extent();
		if (extent.suspended()) throw new IllegalStateException("Attach and size the CAMetalLayer before creating Vulkan.");
		int width = Math.toIntExact(Math.round(extent.pixelWidth()));
		int height = Math.toIntExact(Math.round(extent.pixelHeight()));
		return new LwjglVulkanBackend(checkedSurface.metalLayerHandle(), width, height,
			Objects.requireNonNull(timingLog, "timingLog"), Objects.requireNonNull(presentMode, "presentMode"), validationRequested);
	}
}
