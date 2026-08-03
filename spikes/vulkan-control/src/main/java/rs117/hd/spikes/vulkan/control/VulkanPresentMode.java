package rs117.hd.spikes.vulkan.control;

public enum VulkanPresentMode
{
	FIFO("fifo-like"), UNLOCKED("unlocked"), MAILBOX("mailbox");

	private final String logName;

	VulkanPresentMode(String logName)
	{
		this.logName = logName;
	}

	public String logName()
	{
		return logName;
	}
}
