package rs117.hd.spikes.vulkan.control;

import java.util.List;

import static org.lwjgl.vulkan.KHRSurface.VK_COMPOSITE_ALPHA_INHERIT_BIT_KHR;
import static org.lwjgl.vulkan.KHRSurface.VK_COMPOSITE_ALPHA_OPAQUE_BIT_KHR;
import static org.lwjgl.vulkan.KHRSurface.VK_COMPOSITE_ALPHA_POST_MULTIPLIED_BIT_KHR;
import static org.lwjgl.vulkan.KHRSurface.VK_COMPOSITE_ALPHA_PRE_MULTIPLIED_BIT_KHR;
import static org.lwjgl.vulkan.KHRSurface.VK_PRESENT_MODE_IMMEDIATE_KHR;
import static org.lwjgl.vulkan.KHRSurface.VK_PRESENT_MODE_MAILBOX_KHR;

public final class SwapchainSelection
{
	private final int requestedImageCount;
	private final int width;
	private final int height;
	private final int compositeAlpha;
	private final int preTransform;
	private final VulkanPresentMode effectivePresentMode;
	private final boolean suspended;

	private SwapchainSelection(int requestedImageCount, int width, int height, int compositeAlpha,
		int preTransform, VulkanPresentMode effectivePresentMode, boolean suspended)
	{
		this.requestedImageCount = requestedImageCount;
		this.width = width;
		this.height = height;
		this.compositeAlpha = compositeAlpha;
		this.preTransform = preTransform;
		this.effectivePresentMode = effectivePresentMode;
		this.suspended = suspended;
	}

	public static SwapchainSelection choose(int minImages, int maxImages, int currentWidth, int currentHeight,
		int minWidth, int minHeight, int maxWidth, int maxHeight, int requestedWidth, int requestedHeight,
		int supportedCompositeAlpha, int currentTransform, List<Integer> presentModes, VulkanPresentMode requestedMode)
	{
		if (minImages <= 0 || maxImages < 0 || (maxImages != 0 && maxImages < minImages))
		{
			throw new IllegalArgumentException("Invalid swapchain image limits.");
		}
		if (maxImages != 0 && maxImages < 3)
		{
			throw new IllegalArgumentException("The control spike requires at least three swapchain images.");
		}
		int requestedImages = Math.max(3, minImages);
		if (maxImages != 0) requestedImages = Math.min(requestedImages, maxImages);
		int compositeAlpha = firstSupported(supportedCompositeAlpha,
			VK_COMPOSITE_ALPHA_OPAQUE_BIT_KHR, VK_COMPOSITE_ALPHA_PRE_MULTIPLIED_BIT_KHR,
			VK_COMPOSITE_ALPHA_POST_MULTIPLIED_BIT_KHR, VK_COMPOSITE_ALPHA_INHERIT_BIT_KHR);
		VulkanPresentMode effective = requestedMode == VulkanPresentMode.FIFO ? VulkanPresentMode.FIFO :
			presentModes.contains(VK_PRESENT_MODE_IMMEDIATE_KHR) ? VulkanPresentMode.UNLOCKED :
			presentModes.contains(VK_PRESENT_MODE_MAILBOX_KHR) ? VulkanPresentMode.MAILBOX : VulkanPresentMode.FIFO;
		if (requestedWidth <= 0 || requestedHeight <= 0)
		{
			return new SwapchainSelection(requestedImages, 0, 0, compositeAlpha, currentTransform, effective, true);
		}
		int width = currentWidth >= 0 ? currentWidth : clamp(requestedWidth, minWidth, maxWidth);
		int height = currentHeight >= 0 ? currentHeight : clamp(requestedHeight, minHeight, maxHeight);
		return new SwapchainSelection(requestedImages, width, height, compositeAlpha, currentTransform, effective, false);
	}

	public void requireActualImageCount(int actualImageCount)
	{
		if (actualImageCount < requestedImageCount)
		{
			throw new IllegalArgumentException("Vulkan returned fewer swapchain images than requested.");
		}
	}

	public int requestedImageCount() { return requestedImageCount; }
	public int width() { return width; }
	public int height() { return height; }
	public int compositeAlpha() { return compositeAlpha; }
	public int preTransform() { return preTransform; }
	public VulkanPresentMode effectivePresentMode() { return effectivePresentMode; }
	public boolean suspended() { return suspended; }

	private static int clamp(int value, int minimum, int maximum)
	{
		return Math.max(minimum, Math.min(maximum, value));
	}

	private static int firstSupported(int supported, int... candidates)
	{
		for (int candidate : candidates) if ((supported & candidate) != 0) return candidate;
		throw new IllegalArgumentException("Surface advertises no usable composite alpha mode.");
	}
}
