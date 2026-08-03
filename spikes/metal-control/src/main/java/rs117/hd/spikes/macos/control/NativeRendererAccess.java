package rs117.hd.spikes.macos.control;

interface NativeRendererAccess
{
	long create(long layerHandle, String logPath, PresentMode requestedMode, String failureStage);

	boolean ready(long stateHandle);

	int render(long stateHandle, int width, int height, long frameId, long uiGenerateNs, byte[] uiBytes);

	int skipSuspended(long stateHandle, long frameId);

	void setPresentMode(long stateHandle, PresentMode requestedMode);

	long[] counters(long stateHandle);

	boolean runReadbackCheck(long stateHandle, byte[] firstUiBytes, byte[] secondUiBytes);

	void close(long stateHandle, long[] finalCounters);
}
