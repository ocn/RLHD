package rs117.hd.spikes.vulkan.control;

final class VulkanCrashJournalException extends IllegalStateException
{
	VulkanCrashJournalException(String message, Throwable cause)
	{
		super(message, cause);
	}
}
