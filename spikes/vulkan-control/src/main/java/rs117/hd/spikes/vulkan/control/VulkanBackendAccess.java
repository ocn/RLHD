package rs117.hd.spikes.vulkan.control;

interface VulkanBackendAccess
{
	boolean ready();
	VulkanFrameOutcome render(int width, int height, long frameId, long uiGenerateNs, byte[] uiBytes);
	VulkanFrameOutcome skipSuspended(long frameId);
	void setPresentMode(VulkanPresentMode mode);
	long[] counters();
	boolean runReadbackCheck(byte[] firstUiBytes, byte[] secondUiBytes);
	void close(long[] finalCounters);
}
