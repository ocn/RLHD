package rs117.hd.spikes.vulkan.control;

public enum VulkanFrameOutcome
{
	SUBMITTED("submitted"),
	SKIPPED_SUSPENDED("skipped-suspended"),
	SKIPPED_IN_FLIGHT("skipped-in-flight"),
	NIL_DRAWABLE("nil-drawable"),
	REJECTED("rejected"),
	ERROR("error");

	private final String wireName;

	VulkanFrameOutcome(String wireName)
	{
		this.wireName = wireName;
	}

	public String wireName()
	{
		return wireName;
	}
}
