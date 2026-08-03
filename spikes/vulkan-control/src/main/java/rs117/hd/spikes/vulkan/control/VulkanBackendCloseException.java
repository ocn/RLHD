package rs117.hd.spikes.vulkan.control;

final class VulkanBackendCloseException extends IllegalStateException
{
	private final boolean consumed;

	VulkanBackendCloseException(String message, boolean consumed)
	{
		super(message);
		this.consumed = consumed;
	}

	VulkanBackendCloseException(String message, Throwable cause, boolean consumed)
	{
		super(message, cause);
		this.consumed = consumed;
	}

	boolean consumed()
	{
		return consumed;
	}
}
