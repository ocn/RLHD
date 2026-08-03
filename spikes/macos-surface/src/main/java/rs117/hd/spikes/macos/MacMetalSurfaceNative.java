package rs117.hd.spikes.macos;

import java.awt.Canvas;
import java.io.File;

final class MacMetalSurfaceNative implements NativeSurfaceAccess
{
	private static final String LIBRARY_PROPERTY = "rlhd.spike.macos.library";

	static
	{
		if (!MacMetalSurface.isMacOs())
		{
			throw new UnsupportedOperationException("The CAMetalLayer bridge is only available on macOS.");
		}
		System.loadLibrary("jawt");
		String libraryPath = System.getProperty(LIBRARY_PROPERTY);
		if (libraryPath == null || libraryPath.trim().isEmpty())
		{
			System.loadLibrary("rlhd_mac_surface");
		}
		else
		{
			System.load(new File(libraryPath).getAbsolutePath());
		}
	}

	static final MacMetalSurfaceNative INSTANCE = new MacMetalSurfaceNative();

	private MacMetalSurfaceNative()
	{
	}

	@Override
	public long create()
	{
		return nativeCreate();
	}

	@Override
	public void attach(long stateHandle, Canvas canvas)
	{
		nativeAttach(stateHandle, canvas);
	}

	@Override
	public void resize(long stateHandle, SurfaceExtent extent)
	{
		nativeResize(stateHandle, extent.logicalWidth(), extent.logicalHeight(), extent.backingScale());
	}

	@Override
	public long layerHandle(long stateHandle)
	{
		return nativeLayerHandle(stateHandle);
	}

	@Override
	public void assertLayerState(long stateHandle, SurfaceExtent extent)
	{
		nativeAssertLayerState(stateHandle, extent.logicalWidth(), extent.logicalHeight(), extent.backingScale());
	}

	@Override
	public void detach(long stateHandle)
	{
		nativeDetach(stateHandle);
	}

	@Override
	public void close(long stateHandle)
	{
		nativeClose(stateHandle);
	}

	private static native long nativeCreate();

	private static native void nativeAttach(long stateHandle, Canvas canvas);

	private static native void nativeResize(long stateHandle, int logicalWidth, int logicalHeight, double backingScale);

	private static native long nativeLayerHandle(long stateHandle);

	private static native void nativeAssertLayerState(long stateHandle, int logicalWidth, int logicalHeight, double backingScale);

	private static native void nativeDetach(long stateHandle);

	private static native void nativeClose(long stateHandle);
}
