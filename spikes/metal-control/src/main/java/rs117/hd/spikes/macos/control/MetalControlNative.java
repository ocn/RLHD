package rs117.hd.spikes.macos.control;

import java.io.File;

final class MetalControlNative implements NativeRendererAccess
{
	private static final String LIBRARY_PROPERTY = "rlhd.spike.metal.library";

	static
	{
		String libraryPath = System.getProperty(LIBRARY_PROPERTY);
		if (libraryPath == null || libraryPath.trim().isEmpty())
		{
			System.loadLibrary("rlhd_metal_control");
		}
		else
		{
			System.load(new File(libraryPath).getAbsolutePath());
		}
	}

	static final MetalControlNative INSTANCE = new MetalControlNative();

	private MetalControlNative()
	{
	}

	@Override
	public long create(long layerHandle, String logPath, PresentMode requestedMode, String failureStage)
	{
		return nativeCreate(layerHandle, logPath, requestedMode.wireName(), failureStage);
	}

	@Override public boolean ready(long stateHandle) { return nativeReady(stateHandle); }
	@Override public int render(long stateHandle, int width, int height, long frameId, long uiGenerateNs, byte[] uiBytes) { return nativeRender(stateHandle, width, height, frameId, uiGenerateNs, uiBytes); }
	@Override public int skipSuspended(long stateHandle, long frameId) { return nativeSkipSuspended(stateHandle, frameId); }
	@Override public void setPresentMode(long stateHandle, PresentMode requestedMode) { nativeSetPresentMode(stateHandle, requestedMode.wireName()); }
	@Override public long[] counters(long stateHandle) { return nativeCounters(stateHandle); }
	@Override public boolean runReadbackCheck(long stateHandle, byte[] firstUiBytes, byte[] secondUiBytes) { return nativeRunReadbackCheck(stateHandle, firstUiBytes, secondUiBytes); }
	@Override public void close(long stateHandle, long[] finalCounters) { nativeClose(stateHandle, finalCounters); }

	private static native long nativeCreate(long layerHandle, String logPath, String requestedMode, String failureStage);
	private static native boolean nativeReady(long stateHandle);
	private static native int nativeRender(long stateHandle, int width, int height, long frameId, long uiGenerateNs, byte[] uiBytes);
	private static native int nativeSkipSuspended(long stateHandle, long frameId);
	private static native void nativeSetPresentMode(long stateHandle, String requestedMode);
	private static native long[] nativeCounters(long stateHandle);
	private static native boolean nativeRunReadbackCheck(long stateHandle, byte[] firstUiBytes, byte[] secondUiBytes);
	private static native void nativeClose(long stateHandle, long[] finalCounters);
}
