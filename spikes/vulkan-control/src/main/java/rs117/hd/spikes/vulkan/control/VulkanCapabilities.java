package rs117.hd.spikes.vulkan.control;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class VulkanCapabilities
{
	private final List<String> instanceExtensions;
	private final List<String> deviceExtensions;
	private final List<String> formats;
	private final List<String> presentModes;
	private final int imageCount;

	private VulkanCapabilities(List<String> instanceExtensions, List<String> deviceExtensions, List<String> formats,
		List<String> presentModes, int imageCount)
	{
		this.instanceExtensions = immutable(instanceExtensions);
		this.deviceExtensions = immutable(deviceExtensions);
		this.formats = immutable(formats);
		this.presentModes = immutable(presentModes);
		this.imageCount = imageCount;
	}

	public static VulkanCapabilities require(List<String> instanceExtensions, List<String> deviceExtensions,
		List<String> formats, List<String> presentModes, int imageCount)
	{
		requireContains(instanceExtensions, "VK_KHR_surface");
		requireContains(instanceExtensions, "VK_KHR_portability_enumeration");
		requireContains(instanceExtensions, "VK_EXT_metal_surface");
		requireContains(deviceExtensions, "VK_KHR_swapchain");
		requireContains(formats, "VK_FORMAT_B8G8R8A8_UNORM");
		requireContains(presentModes, "VK_PRESENT_MODE_FIFO_KHR");
		if (imageCount < 3) throw new IllegalArgumentException("Three swapchain images are required.");
		return new VulkanCapabilities(instanceExtensions, deviceExtensions, formats, presentModes, imageCount);
	}

	public VulkanPresentMode select(VulkanPresentMode requested)
	{
		if (requested == VulkanPresentMode.FIFO) return VulkanPresentMode.FIFO;
		if (presentModes.contains("VK_PRESENT_MODE_IMMEDIATE_KHR")) return VulkanPresentMode.UNLOCKED;
		if (presentModes.contains("VK_PRESENT_MODE_MAILBOX_KHR")) return VulkanPresentMode.MAILBOX;
		return VulkanPresentMode.FIFO;
	}

	public List<String> requiredDeviceExtensions()
	{
		List<String> required = new ArrayList<>();
		required.add("VK_KHR_swapchain");
		if (deviceExtensions.contains("VK_KHR_portability_subset")) required.add("VK_KHR_portability_subset");
		return Collections.unmodifiableList(required);
	}

	public List<String> instanceExtensions() { return instanceExtensions; }
	public List<String> deviceExtensions() { return deviceExtensions; }
	public List<String> formats() { return formats; }
	public List<String> presentModes() { return presentModes; }
	public int imageCount() { return imageCount; }

	private static void requireContains(List<String> values, String required)
	{
		if (values == null || !values.contains(required)) throw new IllegalArgumentException("Missing required Vulkan capability: " + required);
	}

	private static List<String> immutable(List<String> values)
	{
		if (values == null) throw new NullPointerException("values");
		return Collections.unmodifiableList(new ArrayList<>(values));
	}
}
