package rs117.hd.spikes.vulkan.offscreen;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.util.ArrayList;
import java.util.List;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.Configuration;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.EXTDebugUtils;
import org.lwjgl.vulkan.KHRPortabilityEnumeration;
import org.lwjgl.vulkan.VK;
import org.lwjgl.vulkan.VkApplicationInfo;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkDeviceCreateInfo;
import org.lwjgl.vulkan.VkDeviceQueueCreateInfo;
import org.lwjgl.vulkan.VkDebugUtilsMessengerCallbackDataEXT;
import org.lwjgl.vulkan.VkDebugUtilsMessengerCallbackEXT;
import org.lwjgl.vulkan.VkDebugUtilsMessengerCreateInfoEXT;
import org.lwjgl.vulkan.VkExtensionProperties;
import org.lwjgl.vulkan.VkFormatProperties;
import org.lwjgl.vulkan.VkInstance;
import org.lwjgl.vulkan.VkInstanceCreateInfo;
import org.lwjgl.vulkan.VkLayerProperties;
import org.lwjgl.vulkan.VkPhysicalDevice;
import org.lwjgl.vulkan.VkPhysicalDeviceProperties;
import org.lwjgl.vulkan.VkQueue;
import org.lwjgl.vulkan.VkQueueFamilyProperties;

import static org.lwjgl.vulkan.VK10.*;
import static org.lwjgl.vulkan.VK11.VK_FORMAT_FEATURE_TRANSFER_DST_BIT;
import static org.lwjgl.vulkan.VK11.VK_FORMAT_FEATURE_TRANSFER_SRC_BIT;

public final class VulkanOffscreenDevice implements AutoCloseable
{
	private static final String PORTABILITY_SUBSET = "VK_KHR_portability_subset";

	private static String loaderIdentity;
	private VkInstance instance;
	private VkDebugUtilsMessengerCallbackEXT debugCallback;
	private long debugMessenger;
	private VkPhysicalDevice physicalDevice;
	private VkDevice device;
	private VkQueue graphicsQueue;
	private int graphicsQueueFamily = -1;
	private String physicalDeviceName = "unknown";
	private int liveHandles;
	private boolean closed;
	private boolean validationEnabled;
	private int validationWarnings;
	private int validationErrors;

	private VulkanOffscreenDevice() {}

	public static VulkanOffscreenDevice open()
	{
		VulkanOffscreenDevice result = new VulkanOffscreenDevice();
		try
		{
			result.createLoader();
			result.createInstance();
			result.selectPhysicalDevice();
			result.createDevice();
			return result;
		}
		catch (RuntimeException | Error failure)
		{
			try { result.close(); }
			catch (RuntimeException | Error cleanup) { failure.addSuppressed(cleanup); }
			throw failure;
		}
	}

	public String physicalDeviceName()
	{
		ensureOpen();
		return physicalDeviceName;
	}

	public int graphicsQueueFamily()
	{
		ensureOpen();
		return graphicsQueueFamily;
	}

	public int liveHandleCount()
	{
		return liveHandles;
	}

	public boolean validationEnabled()
	{
		ensureOpen();
		return validationEnabled;
	}

	public int validationWarningCount()
	{
		return validationWarnings;
	}

	public int validationErrorCount()
	{
		return validationErrors;
	}

	VkDevice device()
	{
		ensureOpen();
		return device;
	}

	VkPhysicalDevice physicalDevice()
	{
		ensureOpen();
		return physicalDevice;
	}

	VkQueue graphicsQueue()
	{
		ensureOpen();
		return graphicsQueue;
	}

	@Override
	public void close()
	{
		if (closed) return;
		closed = true;
		if (device != null)
		{
			vkDeviceWaitIdle(device);
			vkDestroyDevice(device, null);
			device = null;
			graphicsQueue = null;
			liveHandles--;
		}
		physicalDevice = null;
		if (instance != null)
		{
			if (debugMessenger != VK_NULL_HANDLE)
			{
				EXTDebugUtils.vkDestroyDebugUtilsMessengerEXT(instance, debugMessenger, null);
				debugMessenger = VK_NULL_HANDLE;
				liveHandles--;
			}
			vkDestroyInstance(instance, null);
			instance = null;
			liveHandles--;
		}
		if (debugCallback != null) { debugCallback.free(); debugCallback = null; }
		if (liveHandles != 0) throw new IllegalStateException("Offscreen Vulkan handle accounting is unbalanced: " + liveHandles);
	}

	static int selectGraphicsQueueFamily(int[] queueFlags, int[] queueCounts)
	{
		if (queueFlags == null || queueCounts == null || queueFlags.length != queueCounts.length)
			throw new IllegalArgumentException("Queue flags and counts must be parallel arrays.");
		for (int index = 0; index < queueFlags.length; index++)
			if (queueCounts[index] > 0 && (queueFlags[index] & VK_QUEUE_GRAPHICS_BIT) != 0) return index;
		return -1;
	}

	private static synchronized void createLoader()
	{
		String configured = System.getProperty("rlhd.spike.vulkan.loader");
		if (configured == null || configured.trim().isEmpty()) configured = System.getenv("RLHD_VULKAN_LOADER");
		String requestedIdentity = configured == null || configured.trim().isEmpty() ? "<default>" : configured.trim();
		if (loaderIdentity != null)
		{
			if (!loaderIdentity.equals(requestedIdentity))
				throw new IllegalStateException("Vulkan loader is already initialized as " + loaderIdentity +
					" and cannot be replaced with " + requestedIdentity + '.');
			return;
		}
		Configuration.VULKAN_EXPLICIT_INIT.set(true);
		if ("<default>".equals(requestedIdentity)) VK.create();
		else VK.create(requestedIdentity);
		loaderIdentity = requestedIdentity;
		if (VK.getInstanceVersionSupported() < VK_MAKE_API_VERSION(0, 1, 2, 0))
			throw new IllegalStateException("The Vulkan loader does not support Vulkan 1.2.");
	}

	private void createInstance()
	{
		List<String> advertised = enumerateInstanceExtensions();
		boolean portability = advertised.contains(KHRPortabilityEnumeration.VK_KHR_PORTABILITY_ENUMERATION_EXTENSION_NAME);
		boolean validationRequested = Boolean.getBoolean("rlhd.spike.vulkan.validation");
		List<String> layers = enumerateInstanceLayers();
		validationEnabled = validationRequested && layers.contains("VK_LAYER_KHRONOS_validation");
		boolean debugUtilsEnabled = validationRequested && advertised.contains(EXTDebugUtils.VK_EXT_DEBUG_UTILS_EXTENSION_NAME);
		if (validationRequested && !validationEnabled)
			throw new IllegalStateException("VK_LAYER_KHRONOS_validation was requested but is unavailable.");
		if (validationRequested && !debugUtilsEnabled)
			throw new IllegalStateException("VK_EXT_debug_utils was requested but is unavailable.");
		List<String> enabled = new ArrayList<>();
		if (portability) enabled.add(KHRPortabilityEnumeration.VK_KHR_PORTABILITY_ENUMERATION_EXTENSION_NAME);
		if (debugUtilsEnabled) enabled.add(EXTDebugUtils.VK_EXT_DEBUG_UTILS_EXTENSION_NAME);
		try (MemoryStack stack = MemoryStack.stackPush())
		{
			VkDebugUtilsMessengerCreateInfoEXT debugInfo = null;
			if (debugUtilsEnabled)
			{
				initializeDebugCallback();
				debugInfo = debugInfo(stack);
			}
			VkApplicationInfo application = VkApplicationInfo.calloc(stack).sType$Default()
				.pApplicationName(stack.UTF8("RLHD Vulkan offscreen slice")).applicationVersion(1)
				.pEngineName(stack.UTF8("RLHD renderer slice")).engineVersion(1)
				.apiVersion(VK_MAKE_API_VERSION(0, 1, 2, 0));
			VkInstanceCreateInfo info = VkInstanceCreateInfo.calloc(stack).sType$Default()
				.flags(portability ? KHRPortabilityEnumeration.VK_INSTANCE_CREATE_ENUMERATE_PORTABILITY_BIT_KHR : 0)
				.pApplicationInfo(application);
			if (!enabled.isEmpty()) info.ppEnabledExtensionNames(strings(stack, enabled));
			if (validationEnabled) info.ppEnabledLayerNames(strings(stack,
				java.util.Collections.singletonList("VK_LAYER_KHRONOS_validation")));
			if (debugInfo != null) info.pNext(debugInfo);
			PointerBuffer pointer = stack.mallocPointer(1);
			check(vkCreateInstance(info, null, pointer), "vkCreateInstance");
			instance = new VkInstance(pointer.get(0), info);
			liveHandles++;
		}
		if (debugUtilsEnabled) createDebugMessenger();
	}

	private void createDebugMessenger()
	{
		initializeDebugCallback();
		try (MemoryStack stack = MemoryStack.stackPush())
		{
			LongBuffer handle = stack.mallocLong(1);
			check(EXTDebugUtils.vkCreateDebugUtilsMessengerEXT(instance, debugInfo(stack), null, handle),
				"vkCreateDebugUtilsMessengerEXT");
			debugMessenger = handle.get(0);
			liveHandles++;
		}
	}

	private void initializeDebugCallback()
	{
		if (debugCallback != null) return;
		debugCallback = VkDebugUtilsMessengerCallbackEXT.create((severity, types, callbackData, userData) ->
		{
			String message = VkDebugUtilsMessengerCallbackDataEXT.create(callbackData).pMessageString();
			System.err.println("[vulkan-offscreen-validation] " + message);
			if ((severity & EXTDebugUtils.VK_DEBUG_UTILS_MESSAGE_SEVERITY_ERROR_BIT_EXT) != 0) validationErrors++;
			else if ((severity & EXTDebugUtils.VK_DEBUG_UTILS_MESSAGE_SEVERITY_WARNING_BIT_EXT) != 0) validationWarnings++;
			return VK_FALSE;
		});
	}

	private VkDebugUtilsMessengerCreateInfoEXT debugInfo(MemoryStack stack)
	{
		return VkDebugUtilsMessengerCreateInfoEXT.calloc(stack).sType$Default()
			.messageSeverity(EXTDebugUtils.VK_DEBUG_UTILS_MESSAGE_SEVERITY_WARNING_BIT_EXT |
				EXTDebugUtils.VK_DEBUG_UTILS_MESSAGE_SEVERITY_ERROR_BIT_EXT)
			.messageType(EXTDebugUtils.VK_DEBUG_UTILS_MESSAGE_TYPE_GENERAL_BIT_EXT |
				EXTDebugUtils.VK_DEBUG_UTILS_MESSAGE_TYPE_VALIDATION_BIT_EXT |
				EXTDebugUtils.VK_DEBUG_UTILS_MESSAGE_TYPE_PERFORMANCE_BIT_EXT)
			.pfnUserCallback(debugCallback);
	}

	private void selectPhysicalDevice()
	{
		try (MemoryStack stack = MemoryStack.stackPush())
		{
			IntBuffer count = stack.mallocInt(1);
			check(vkEnumeratePhysicalDevices(instance, count, null), "vkEnumeratePhysicalDevices(count)");
			if (count.get(0) == 0) throw new IllegalStateException("No Vulkan physical devices were enumerated.");
			PointerBuffer devices = stack.mallocPointer(count.get(0));
			check(vkEnumeratePhysicalDevices(instance, count, devices), "vkEnumeratePhysicalDevices");
			for (int index = 0; index < count.get(0); index++)
			{
				VkPhysicalDevice candidate = new VkPhysicalDevice(devices.get(index), instance);
				VkPhysicalDeviceProperties properties = VkPhysicalDeviceProperties.malloc(stack);
				vkGetPhysicalDeviceProperties(candidate, properties);
				if (properties.apiVersion() < VK_MAKE_API_VERSION(0, 1, 2, 0)) continue;
				int family = graphicsQueueFamily(candidate, stack);
				if (family < 0) continue;
				if (!supportsRequiredFormats(candidate, stack)) continue;
				physicalDevice = candidate;
				graphicsQueueFamily = family;
				physicalDeviceName = properties.deviceNameString();
				return;
			}
		}
		throw new IllegalStateException("No Vulkan 1.2 physical device exposes a graphics queue.");
	}

	private boolean supportsRequiredFormats(VkPhysicalDevice candidate, MemoryStack stack)
	{
		VkFormatProperties properties = VkFormatProperties.malloc(stack);
		vkGetPhysicalDeviceFormatProperties(candidate, VK_FORMAT_B8G8R8A8_UNORM, properties);
		int bgraRequired = VK_FORMAT_FEATURE_COLOR_ATTACHMENT_BIT | VK_FORMAT_FEATURE_SAMPLED_IMAGE_BIT |
			VK_FORMAT_FEATURE_TRANSFER_SRC_BIT | VK_FORMAT_FEATURE_TRANSFER_DST_BIT;
		if (!hasAllFeatures(properties.optimalTilingFeatures(), bgraRequired)) return false;
		vkGetPhysicalDeviceFormatProperties(candidate, VK_FORMAT_D32_SFLOAT, properties);
		if (!hasAllFeatures(properties.optimalTilingFeatures(), VK_FORMAT_FEATURE_DEPTH_STENCIL_ATTACHMENT_BIT)) return false;
		for (int format : new int[] { VK_FORMAT_R16G16B16A16_SINT, VK_FORMAT_R16G16B16A16_SFLOAT,
			VK_FORMAT_R32_SINT })
		{
			vkGetPhysicalDeviceFormatProperties(candidate, format, properties);
			if (!hasAllFeatures(properties.bufferFeatures(), VK_FORMAT_FEATURE_VERTEX_BUFFER_BIT)) return false;
		}
		return true;
	}

	static boolean hasAllFeatures(int advertised, int required)
	{
		return (advertised & required) == required;
	}

	private int graphicsQueueFamily(VkPhysicalDevice candidate, MemoryStack stack)
	{
		IntBuffer count = stack.mallocInt(1);
		vkGetPhysicalDeviceQueueFamilyProperties(candidate, count, null);
		VkQueueFamilyProperties.Buffer properties = VkQueueFamilyProperties.malloc(count.get(0), stack);
		vkGetPhysicalDeviceQueueFamilyProperties(candidate, count, properties);
		int[] flags = new int[properties.capacity()];
		int[] counts = new int[properties.capacity()];
		for (int index = 0; index < properties.capacity(); index++)
		{
			flags[index] = properties.get(index).queueFlags();
			counts[index] = properties.get(index).queueCount();
		}
		return selectGraphicsQueueFamily(flags, counts);
	}

	private void createDevice()
	{
		List<String> enabled = new ArrayList<>();
		if (enumerateDeviceExtensions().contains(PORTABILITY_SUBSET)) enabled.add(PORTABILITY_SUBSET);
		try (MemoryStack stack = MemoryStack.stackPush())
		{
			FloatBuffer priority = stack.floats(1.0f);
			VkDeviceQueueCreateInfo.Buffer queueInfo = VkDeviceQueueCreateInfo.calloc(1, stack);
			queueInfo.get(0).sType$Default().queueFamilyIndex(graphicsQueueFamily).pQueuePriorities(priority);
			VkDeviceCreateInfo info = VkDeviceCreateInfo.calloc(stack).sType$Default().pQueueCreateInfos(queueInfo);
			if (!enabled.isEmpty()) info.ppEnabledExtensionNames(strings(stack, enabled));
			PointerBuffer pointer = stack.mallocPointer(1);
			check(vkCreateDevice(physicalDevice, info, null, pointer), "vkCreateDevice");
			device = new VkDevice(pointer.get(0), physicalDevice, info);
			liveHandles++;
			PointerBuffer queue = stack.mallocPointer(1);
			vkGetDeviceQueue(device, graphicsQueueFamily, 0, queue);
			graphicsQueue = new VkQueue(queue.get(0), device);
		}
	}

	private List<String> enumerateInstanceExtensions()
	{
		try (MemoryStack stack = MemoryStack.stackPush())
		{
			IntBuffer count = stack.mallocInt(1);
			check(vkEnumerateInstanceExtensionProperties((String) null, count, null), "vkEnumerateInstanceExtensionProperties(count)");
			VkExtensionProperties.Buffer values = VkExtensionProperties.malloc(count.get(0), stack);
			check(vkEnumerateInstanceExtensionProperties((String) null, count, values), "vkEnumerateInstanceExtensionProperties");
			List<String> result = new ArrayList<>();
			for (int index = 0; index < values.capacity(); index++) result.add(values.get(index).extensionNameString());
			return result;
		}
	}

	private List<String> enumerateDeviceExtensions()
	{
		try (MemoryStack stack = MemoryStack.stackPush())
		{
			IntBuffer count = stack.mallocInt(1);
			check(vkEnumerateDeviceExtensionProperties(physicalDevice, (String) null, count, null), "vkEnumerateDeviceExtensionProperties(count)");
			VkExtensionProperties.Buffer values = VkExtensionProperties.malloc(count.get(0), stack);
			check(vkEnumerateDeviceExtensionProperties(physicalDevice, (String) null, count, values), "vkEnumerateDeviceExtensionProperties");
			List<String> result = new ArrayList<>();
			for (int index = 0; index < values.capacity(); index++) result.add(values.get(index).extensionNameString());
			return result;
		}
	}

	private List<String> enumerateInstanceLayers()
	{
		try (MemoryStack stack = MemoryStack.stackPush())
		{
			IntBuffer count = stack.mallocInt(1);
			check(vkEnumerateInstanceLayerProperties(count, null), "vkEnumerateInstanceLayerProperties(count)");
			VkLayerProperties.Buffer values = VkLayerProperties.malloc(count.get(0), stack);
			check(vkEnumerateInstanceLayerProperties(count, values), "vkEnumerateInstanceLayerProperties");
			List<String> result = new ArrayList<>();
			for (int index = 0; index < values.capacity(); index++) result.add(values.get(index).layerNameString());
			return result;
		}
	}

	private static PointerBuffer strings(MemoryStack stack, List<String> values)
	{
		PointerBuffer result = stack.mallocPointer(values.size());
		for (String value : values) result.put(stack.UTF8(value));
		return result.flip();
	}

	private static void check(int result, String operation)
	{
		if (result != VK_SUCCESS) throw new IllegalStateException(operation + " failed with VkResult " + result);
	}

	private void ensureOpen()
	{
		if (closed || device == null) throw new IllegalStateException("Offscreen Vulkan device is closed.");
	}
}
