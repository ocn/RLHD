package rs117.hd.spikes.macos;

import java.awt.Canvas;
import java.util.Locale;
import java.util.Objects;

public final class MacMetalSurface implements AutoCloseable
{
	private static final SurfaceExtent DETACHED_EXTENT = SurfaceExtent.of(0, 0, 1.0);

	private NativeSurfaceAccess nativeAccess;
	private final boolean requireMacOs;
	private final boolean requireDisplayableCanvas;
	private long stateHandle;
	private boolean attached;
	private boolean closed;
	private Canvas canvas;
	private SurfaceExtent extent = DETACHED_EXTENT;

	public MacMetalSurface()
	{
		this(null, true, true);
	}

	MacMetalSurface(NativeSurfaceAccess nativeAccess, boolean requireMacOs, boolean requireDisplayableCanvas)
	{
		this.nativeAccess = nativeAccess;
		this.requireMacOs = requireMacOs;
		this.requireDisplayableCanvas = requireDisplayableCanvas;
	}

	public static boolean isMacOs()
	{
		return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("mac");
	}

	public synchronized void attach(Canvas canvas)
	{
		ensureOpen();
		if (attached)
		{
			throw new IllegalStateException("Surface is already attached.");
		}
		Objects.requireNonNull(canvas, "canvas");
		if (requireDisplayableCanvas && !canvas.isDisplayable())
		{
			throw new IllegalArgumentException("Canvas must have a displayable native peer before attachment.");
		}
		ensureTreeUnlocked(canvas);

		NativeSurfaceAccess access = nativeAccess();
		if (stateHandle == 0)
		{
			stateHandle = access.create();
			if (stateHandle == 0)
			{
				throw new IllegalStateException("Native surface state allocation failed.");
			}
		}
		access.attach(stateHandle, canvas);
		attached = true;
		this.canvas = canvas;
		extent = DETACHED_EXTENT;
	}

	public synchronized void resize(int logicalWidth, int logicalHeight, double backingScale)
	{
		ensureOpen();
		ensureAttached();
		ensureTreeUnlocked(canvas);
		SurfaceExtent updatedExtent = SurfaceExtent.of(logicalWidth, logicalHeight, backingScale);
		nativeAccess().resize(stateHandle, updatedExtent);
		extent = updatedExtent;
	}

	public synchronized SurfaceExtent extent()
	{
		ensureOpen();
		return extent;
	}

	public synchronized long metalLayerHandle()
	{
		ensureOpen();
		ensureAttached();
		long layerHandle = nativeAccess().layerHandle(stateHandle);
		if (layerHandle == 0)
		{
			throw new IllegalStateException("Attached surface has no CAMetalLayer handle.");
		}
		return layerHandle;
	}

	public synchronized void detach()
	{
		ensureOpen();
		ensureAttached();
		ensureTreeUnlocked(canvas);
		nativeAccess().detach(stateHandle);
		attached = false;
		canvas = null;
		extent = DETACHED_EXTENT;
	}

	@Override
	public synchronized void close()
	{
		ensureOpen();
		if (attached)
		{
			ensureTreeUnlocked(canvas);
		}
		try
		{
			if (stateHandle != 0)
			{
				nativeAccess().close(stateHandle);
			}
		}
		finally
		{
			stateHandle = 0;
			attached = false;
			closed = true;
			canvas = null;
			extent = DETACHED_EXTENT;
		}
	}

	private NativeSurfaceAccess nativeAccess()
	{
		if (nativeAccess == null)
		{
			if (requireMacOs && !isMacOs())
			{
				throw new UnsupportedOperationException("The CAMetalLayer bridge is only available on macOS.");
			}
			nativeAccess = MacMetalSurfaceNative.INSTANCE;
		}
		return nativeAccess;
	}

	private void ensureOpen()
	{
		if (closed)
		{
			throw new IllegalStateException("Surface is closed.");
		}
	}

	private void ensureAttached()
	{
		if (!attached)
		{
			throw new IllegalStateException("Surface is not attached.");
		}
	}

	private static void ensureTreeUnlocked(Canvas canvas)
	{
		if (Thread.holdsLock(canvas.getTreeLock()))
		{
			throw new IllegalStateException("Do not call surface operations while holding the AWT tree lock.");
		}
	}
}
