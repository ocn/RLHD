package rs117.hd.spikes.vulkan.control;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.Configuration;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.EXTDebugUtils;
import org.lwjgl.vulkan.EXTMetalSurface;
import org.lwjgl.vulkan.KHRPortabilityEnumeration;
import org.lwjgl.vulkan.KHRSurface;
import org.lwjgl.vulkan.KHRSwapchain;
import org.lwjgl.vulkan.VK;
import org.lwjgl.vulkan.VkApplicationInfo;
import org.lwjgl.vulkan.VkAttachmentDescription;
import org.lwjgl.vulkan.VkAttachmentReference;
import org.lwjgl.vulkan.VkBufferCopy;
import org.lwjgl.vulkan.VkBufferCreateInfo;
import org.lwjgl.vulkan.VkBufferImageCopy;
import org.lwjgl.vulkan.VkClearValue;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkCommandBufferAllocateInfo;
import org.lwjgl.vulkan.VkCommandBufferBeginInfo;
import org.lwjgl.vulkan.VkCommandPoolCreateInfo;
import org.lwjgl.vulkan.VkDebugUtilsMessengerCallbackDataEXT;
import org.lwjgl.vulkan.VkDebugUtilsMessengerCallbackEXT;
import org.lwjgl.vulkan.VkDebugUtilsMessengerCreateInfoEXT;
import org.lwjgl.vulkan.VkDependencyInfo;
import org.lwjgl.vulkan.VkDescriptorImageInfo;
import org.lwjgl.vulkan.VkDescriptorPoolCreateInfo;
import org.lwjgl.vulkan.VkDescriptorPoolSize;
import org.lwjgl.vulkan.VkDescriptorSetAllocateInfo;
import org.lwjgl.vulkan.VkDescriptorSetLayoutBinding;
import org.lwjgl.vulkan.VkDescriptorSetLayoutCreateInfo;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkDeviceCreateInfo;
import org.lwjgl.vulkan.VkDeviceQueueCreateInfo;
import org.lwjgl.vulkan.VkExtensionProperties;
import org.lwjgl.vulkan.VkExtent2D;
import org.lwjgl.vulkan.VkFenceCreateInfo;
import org.lwjgl.vulkan.VkFramebufferCreateInfo;
import org.lwjgl.vulkan.VkGraphicsPipelineCreateInfo;
import org.lwjgl.vulkan.VkImageCreateInfo;
import org.lwjgl.vulkan.VkImageMemoryBarrier;
import org.lwjgl.vulkan.VkImageSubresourceLayers;
import org.lwjgl.vulkan.VkImageViewCreateInfo;
import org.lwjgl.vulkan.VkInstance;
import org.lwjgl.vulkan.VkInstanceCreateInfo;
import org.lwjgl.vulkan.VkLayerProperties;
import org.lwjgl.vulkan.VkMemoryAllocateInfo;
import org.lwjgl.vulkan.VkMemoryRequirements;
import org.lwjgl.vulkan.VkMetalSurfaceCreateInfoEXT;
import org.lwjgl.vulkan.VkPhysicalDevice;
import org.lwjgl.vulkan.VkPhysicalDeviceMemoryProperties;
import org.lwjgl.vulkan.VkPhysicalDeviceProperties;
import org.lwjgl.vulkan.VkPipelineColorBlendAttachmentState;
import org.lwjgl.vulkan.VkPipelineColorBlendStateCreateInfo;
import org.lwjgl.vulkan.VkPipelineDynamicStateCreateInfo;
import org.lwjgl.vulkan.VkPipelineInputAssemblyStateCreateInfo;
import org.lwjgl.vulkan.VkPipelineLayoutCreateInfo;
import org.lwjgl.vulkan.VkPipelineMultisampleStateCreateInfo;
import org.lwjgl.vulkan.VkPipelineRasterizationStateCreateInfo;
import org.lwjgl.vulkan.VkPipelineShaderStageCreateInfo;
import org.lwjgl.vulkan.VkPipelineVertexInputStateCreateInfo;
import org.lwjgl.vulkan.VkPipelineViewportStateCreateInfo;
import org.lwjgl.vulkan.VkPresentInfoKHR;
import org.lwjgl.vulkan.VkPushConstantRange;
import org.lwjgl.vulkan.VkQueryPoolCreateInfo;
import org.lwjgl.vulkan.VkQueue;
import org.lwjgl.vulkan.VkQueueFamilyProperties;
import org.lwjgl.vulkan.VkRenderPassBeginInfo;
import org.lwjgl.vulkan.VkRenderPassCreateInfo;
import org.lwjgl.vulkan.VkSamplerCreateInfo;
import org.lwjgl.vulkan.VkSemaphoreCreateInfo;
import org.lwjgl.vulkan.VkShaderModuleCreateInfo;
import org.lwjgl.vulkan.VkSubmitInfo;
import org.lwjgl.vulkan.VkSubpassDependency;
import org.lwjgl.vulkan.VkSubpassDescription;
import org.lwjgl.vulkan.VkSurfaceCapabilitiesKHR;
import org.lwjgl.vulkan.VkSurfaceFormatKHR;
import org.lwjgl.vulkan.VkSwapchainCreateInfoKHR;
import org.lwjgl.vulkan.VkViewport;
import org.lwjgl.vulkan.VkWriteDescriptorSet;
import rs117.hd.spikes.macos.control.SyntheticUi;

import static org.lwjgl.system.MemoryUtil.NULL;
import static org.lwjgl.system.MemoryUtil.memByteBuffer;
import static org.lwjgl.system.MemoryUtil.memPutAddress;
import static org.lwjgl.vulkan.EXTDebugUtils.VK_DEBUG_UTILS_MESSAGE_SEVERITY_ERROR_BIT_EXT;
import static org.lwjgl.vulkan.EXTDebugUtils.VK_DEBUG_UTILS_MESSAGE_SEVERITY_WARNING_BIT_EXT;
import static org.lwjgl.vulkan.EXTDebugUtils.VK_DEBUG_UTILS_MESSAGE_TYPE_GENERAL_BIT_EXT;
import static org.lwjgl.vulkan.EXTDebugUtils.VK_DEBUG_UTILS_MESSAGE_TYPE_PERFORMANCE_BIT_EXT;
import static org.lwjgl.vulkan.EXTDebugUtils.VK_DEBUG_UTILS_MESSAGE_TYPE_VALIDATION_BIT_EXT;
import static org.lwjgl.vulkan.KHRSurface.VK_COLOR_SPACE_SRGB_NONLINEAR_KHR;
import static org.lwjgl.vulkan.KHRSurface.VK_PRESENT_MODE_FIFO_KHR;
import static org.lwjgl.vulkan.KHRSurface.VK_PRESENT_MODE_IMMEDIATE_KHR;
import static org.lwjgl.vulkan.KHRSurface.VK_PRESENT_MODE_MAILBOX_KHR;
import static org.lwjgl.vulkan.KHRSwapchain.VK_ERROR_OUT_OF_DATE_KHR;
import static org.lwjgl.vulkan.KHRSwapchain.VK_IMAGE_LAYOUT_PRESENT_SRC_KHR;
import static org.lwjgl.vulkan.KHRSwapchain.VK_SUBOPTIMAL_KHR;
import static org.lwjgl.vulkan.VK10.*;

final class LwjglVulkanBackend implements VulkanBackendAccess
{
	private static final int FRAME_COUNT = 2;
	private static final int COUNTER_INIT_ERRORS = 0;
	private static final int COUNTER_SHADER_ERRORS = 1;
	private static final int COUNTER_PIPELINE_ERRORS = 2;
	private static final int COUNTER_SUBMITTED = 3;
	private static final int COUNTER_COMPLETED = 4;
	private static final int COUNTER_COMMAND_ERRORS = 5;
	private static final int COUNTER_PRESENT_REQUESTED = 6;
	private static final int COUNTER_NIL_DRAWABLE = 7;
	private static final int COUNTER_SKIPPED_SUSPENDED = 8;
	private static final int COUNTER_SKIPPED_IN_FLIGHT = 9;
	private static final int COUNTER_UI_UPLOAD_BYTES = 10;
	private static final int COUNTER_RESIZE_REBUILDS = 11;
	private static final int COUNTER_DEVICE_REBUILDS = 12;
	private static final int COUNTER_LIVE_NATIVE_OBJECTS = 13;
	private static final int COUNTER_HIGH_WATER_NATIVE_OBJECTS = 14;
	private static final int COUNTER_MAX_IN_FLIGHT = 15;
	private static final int COUNTER_PRESENT_MODE_DIVERGENCES = 19;
	private static final int COUNTER_ACQUISITION_REQUESTS = 20;
	private static final int COUNTER_ACQUISITION_COMPLETIONS = 21;

	private final long[] counterValues = new long[VulkanControlCounters.FIELD_COUNT];
	private final VulkanTimingLog timingLog;
	private final boolean validationRequested;
	private final long layerHandle;
	private VulkanPresentMode requestedMode;
	private VulkanPresentMode effectiveMode = VulkanPresentMode.FIFO;
	private boolean ready;
	private boolean accepting = true;
	private boolean consumed;
	private boolean loaderCreated;
	private boolean validationEnabled;
	private boolean debugUtilsEnabled;
	private boolean portabilitySubset;
	private String initializationError;
	private List<String> instanceExtensions = new ArrayList<>();
	private List<String> deviceExtensions = new ArrayList<>();
	private VkInstance instance;
	private long debugMessenger;
	private VkDebugUtilsMessengerCallbackEXT debugCallback;
	private long surface;
	private VkPhysicalDevice physicalDevice;
	private String physicalDeviceName = "unknown";
	private VkDevice device;
	private int graphicsQueueFamily = -1;
	private int presentQueueFamily = -1;
	private VkQueue graphicsQueue;
	private VkQueue presentQueue;
	private long commandPool;
	private final FrameResources[] frames = new FrameResources[FRAME_COUNT];
	private int frameCursor;
	private SwapchainData swapchain;
	private boolean recreatePending;
	private int timestampValidBits;
	private double timestampPeriodNs;
	private boolean timestampsSupported;
	private long generation;

	LwjglVulkanBackend(long layerHandle, int initialWidth, int initialHeight, Path timingLog,
		VulkanPresentMode requestedMode, boolean validationRequested)
	{
		if (layerHandle == 0) throw new IllegalArgumentException("A borrowed CAMetalLayer handle is required.");
		if (requestedMode == VulkanPresentMode.MAILBOX) throw new IllegalArgumentException("MAILBOX is not a requested mode.");
		this.layerHandle = layerHandle;
		this.requestedMode = requestedMode;
		this.validationRequested = validationRequested;
		this.timingLog = new VulkanTimingLog(timingLog);
		try
		{
			createLoader();
			createInstance();
			createSurface();
			selectPhysicalDevice();
			createDevice();
			createCommandResources();
			if (initialWidth <= 0 || initialHeight <= 0) throw new IllegalArgumentException("Initial Vulkan extent must be positive.");
			createSwapchain(initialWidth, initialHeight);
			ready = true;
		}
		catch (RuntimeException | Error ex)
		{
			counterValues[COUNTER_INIT_ERRORS]++;
			initializationError = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
		}
	}

	@Override
	public synchronized boolean ready()
	{
		return ready;
	}

	@Override
	public synchronized VulkanFrameOutcome render(int width, int height, long frameId, long uiGenerateNs, byte[] uiBytes)
	{
		ensureAccepting();
		if (!ready) return failedFrame(frameId, width, height, uiGenerateNs, VulkanFrameOutcome.REJECTED, "renderer-not-ready");
		if (width <= 0 || height <= 0) return skipSuspended(frameId);
		long frameStart = System.nanoTime();
		FrameResources frame = frames[frameCursor];
		finishFrame(frame, true);
		if (swapchain == null || width != swapchain.requestedWidth || height != swapchain.requestedHeight || recreatePending)
		{
			recreateSwapchain(width, height);
		}
		frame = frames[frameCursor];
		byte[] uploadBytes = uiBytes;
		if (uploadBytes.length != frame.staging.size)
		{
			long regenerateStart = System.nanoTime();
			uploadBytes = new byte[SyntheticUi.byteCount(swapchain.width, swapchain.height)];
			SyntheticUi.fillPremultipliedBgra(uploadBytes, swapchain.width, swapchain.height, frameId);
			uiGenerateNs += System.nanoTime() - regenerateStart;
		}
		int imageIndex;
		try (MemoryStack stack = MemoryStack.stackPush())
		{
			IntBuffer image = stack.mallocInt(1);
			counterValues[COUNTER_ACQUISITION_REQUESTS]++;
			int acquire = KHRSwapchain.vkAcquireNextImageKHR(device, swapchain.handle, -1L, frame.acquireSemaphore, NULL, image);
			counterValues[COUNTER_ACQUISITION_COMPLETIONS]++;
			if (acquire == VK_ERROR_OUT_OF_DATE_KHR)
			{
				recreatePending = true;
				counterValues[COUNTER_NIL_DRAWABLE]++;
				return failedFrame(frameId, width, height, uiGenerateNs, VulkanFrameOutcome.NIL_DRAWABLE, null);
			}
			if (acquire != VK_SUCCESS && acquire != VK_SUBOPTIMAL_KHR) throw failure("vkAcquireNextImageKHR", acquire);
			if (acquire == VK_SUBOPTIMAL_KHR) recreatePending = true;
			imageIndex = image.get(0);
		}
		long imageFence = swapchain.imageFences[imageIndex];
		if (imageFence != NULL && imageFence != frame.fence) check(vkWaitForFences(device, imageFence, true, -1L), "vkWaitForFences(image)");
		long uploadStart = System.nanoTime();
		upload(frame.staging, uploadBytes);
		long uploadNs = System.nanoTime() - uploadStart;
		counterValues[COUNTER_UI_UPLOAD_BYTES] += uploadBytes.length;
		long encodeStart = System.nanoTime();
		recordFrame(frame, imageIndex, frameId);
		long encodeNs = System.nanoTime() - encodeStart;
		check(vkResetFences(device, frame.fence), "vkResetFences");
		long submitStart = System.nanoTime();
		try (MemoryStack stack = MemoryStack.stackPush())
		{
			VkSubmitInfo submit = VkSubmitInfo.calloc(stack).sType$Default()
				.waitSemaphoreCount(1).pWaitSemaphores(stack.longs(frame.acquireSemaphore))
				.pWaitDstStageMask(stack.ints(VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT))
				.pCommandBuffers(stack.pointers(frame.commandBuffer.address()))
				.pSignalSemaphores(stack.longs(swapchain.renderFinishedSemaphores[imageIndex]));
			check(vkQueueSubmit(graphicsQueue, submit, frame.fence), "vkQueueSubmit");
		}
		long submitNs = System.nanoTime() - submitStart;
		frame.pending = submittedRecord(frameId, swapchain.width, swapchain.height, uiGenerateNs, uploadNs, encodeNs, submitNs,
			System.nanoTime() - frameStart + uiGenerateNs);
		frame.submitted = true;
		frame.imageIndex = imageIndex;
		swapchain.imageFences[imageIndex] = frame.fence;
		counterValues[COUNTER_SUBMITTED]++;
		counterValues[COUNTER_PRESENT_REQUESTED]++;
		long inFlight = counterValues[COUNTER_SUBMITTED] - counterValues[COUNTER_COMPLETED];
		counterValues[COUNTER_MAX_IN_FLIGHT] = Math.max(counterValues[COUNTER_MAX_IN_FLIGHT], inFlight);
		int present;
		try (MemoryStack stack = MemoryStack.stackPush())
		{
			VkPresentInfoKHR info = VkPresentInfoKHR.calloc(stack).sType$Default()
				.pWaitSemaphores(stack.longs(swapchain.renderFinishedSemaphores[imageIndex]))
				.swapchainCount(1).pSwapchains(stack.longs(swapchain.handle)).pImageIndices(stack.ints(imageIndex));
			present = KHRSwapchain.vkQueuePresentKHR(presentQueue, info);
		}
		if (present == VK_ERROR_OUT_OF_DATE_KHR || present == VK_SUBOPTIMAL_KHR) recreatePending = true;
		else if (present != VK_SUCCESS) { counterValues[COUNTER_COMMAND_ERRORS]++; frame.pending.error = "vkQueuePresentKHR:" + present; }
		frameCursor = (frameCursor + 1) % FRAME_COUNT;
		return VulkanFrameOutcome.SUBMITTED;
	}

	@Override
	public synchronized VulkanFrameOutcome skipSuspended(long frameId)
	{
		ensureAccepting();
		counterValues[COUNTER_SKIPPED_SUSPENDED]++;
		if (device != null && swapchain != null)
		{
			check(vkDeviceWaitIdle(device), "vkDeviceWaitIdle(suspend)");
			finishAllFrames();
			destroySwapchain();
		}
		recreatePending = true;
		return failedFrame(frameId, 0, 0, 0, VulkanFrameOutcome.SKIPPED_SUSPENDED, null);
	}

	@Override
	public synchronized void setPresentMode(VulkanPresentMode mode)
	{
		ensureAccepting();
		if (mode == VulkanPresentMode.MAILBOX) throw new IllegalArgumentException("MAILBOX is not a requested mode.");
		requestedMode = mode;
		recreatePending = true;
	}

	@Override
	public synchronized long[] counters()
	{
		return counterValues.clone();
	}

	@Override
	public synchronized boolean runReadbackCheck(byte[] firstUiBytes, byte[] secondUiBytes)
	{
		ensureAccepting();
		if (!ready || swapchain == null || firstUiBytes.length != 8 * 8 * 4 || secondUiBytes.length != firstUiBytes.length) return false;
		check(vkDeviceWaitIdle(device), "vkDeviceWaitIdle(readback)");
		finishAllFrames();
		return runReadback(firstUiBytes, secondUiBytes);
	}

	@Override
	public synchronized void close(long[] finalCounters)
	{
		if (finalCounters == null || finalCounters.length != VulkanControlCounters.FIELD_COUNT)
			throw new IllegalArgumentException("Final Vulkan counter storage has the wrong length.");
		if (consumed || !accepting) throw new IllegalStateException("Vulkan control renderer is already closed.");
		if (device != null) check(vkDeviceWaitIdle(device), "vkDeviceWaitIdle(close)");
		accepting = false;
		consumed = true;
		finishAllFrames();
		destroySwapchain();
		destroyCommandResources();
		if (device != null) { vkDestroyDevice(device, null); device = null; trackRelease(); }
		if (surface != NULL && instance != null) { KHRSurface.vkDestroySurfaceKHR(instance, surface, null); surface = NULL; trackRelease(); }
		if (debugMessenger != NULL && instance != null) { EXTDebugUtils.vkDestroyDebugUtilsMessengerEXT(instance, debugMessenger, null); debugMessenger = NULL; trackRelease(); }
		if (debugCallback != null) { debugCallback.free(); debugCallback = null; }
		if (instance != null) { vkDestroyInstance(instance, null); instance = null; trackRelease(); }
		if (loaderCreated) { VK.destroy(); loaderCreated = false; }
		ready = false;
		String error = initializationError;
		if (error == null && (counterValues[COUNTER_INIT_ERRORS] != 0 || counterValues[COUNTER_SHADER_ERRORS] != 0 ||
			counterValues[COUNTER_PIPELINE_ERRORS] != 0 || counterValues[COUNTER_COMMAND_ERRORS] != 0)) error = "renderer-errors";
		timingLog.runEnd(requestedMode, effectiveMode, counterValues, error);
		timingLog.close();
		System.arraycopy(counterValues, 0, finalCounters, 0, counterValues.length);
	}

	private void createLoader()
	{
		Configuration.VULKAN_EXPLICIT_INIT.set(true);
		String configured = System.getProperty("rlhd.spike.vulkan.loader");
		if (configured == null || configured.trim().isEmpty()) configured = System.getenv("RLHD_VULKAN_LOADER");
		if (configured == null || configured.trim().isEmpty()) VK.create();
		else VK.create(configured);
		loaderCreated = true;
		if (VK.getInstanceVersionSupported() < VK_MAKE_API_VERSION(0, 1, 2, 0))
			throw new IllegalStateException("The Vulkan loader does not support Vulkan 1.2.");
	}

	private void createInstance()
	{
		instanceExtensions = enumerateInstanceExtensions();
		require(instanceExtensions, KHRSurface.VK_KHR_SURFACE_EXTENSION_NAME);
		require(instanceExtensions, EXTMetalSurface.VK_EXT_METAL_SURFACE_EXTENSION_NAME);
		require(instanceExtensions, KHRPortabilityEnumeration.VK_KHR_PORTABILITY_ENUMERATION_EXTENSION_NAME);
		List<String> layers = enumerateInstanceLayers();
		validationEnabled = validationRequested && layers.contains("VK_LAYER_KHRONOS_validation");
		debugUtilsEnabled = validationRequested && instanceExtensions.contains(EXTDebugUtils.VK_EXT_DEBUG_UTILS_EXTENSION_NAME);
		List<String> enabledExtensions = new ArrayList<>();
		enabledExtensions.add(KHRSurface.VK_KHR_SURFACE_EXTENSION_NAME);
		enabledExtensions.add(EXTMetalSurface.VK_EXT_METAL_SURFACE_EXTENSION_NAME);
		enabledExtensions.add(KHRPortabilityEnumeration.VK_KHR_PORTABILITY_ENUMERATION_EXTENSION_NAME);
		if (debugUtilsEnabled) enabledExtensions.add(EXTDebugUtils.VK_EXT_DEBUG_UTILS_EXTENSION_NAME);
		try (MemoryStack stack = MemoryStack.stackPush())
		{
			VkApplicationInfo application = VkApplicationInfo.calloc(stack).sType$Default()
				.pApplicationName(stack.UTF8("RLHD Vulkan control")).applicationVersion(1)
				.pEngineName(stack.UTF8("RLHD spike")).engineVersion(1)
				.apiVersion(VK_MAKE_API_VERSION(0, 1, 2, 0));
			VkInstanceCreateInfo info = VkInstanceCreateInfo.calloc(stack).sType$Default()
				.flags(KHRPortabilityEnumeration.VK_INSTANCE_CREATE_ENUMERATE_PORTABILITY_BIT_KHR)
				.pApplicationInfo(application).ppEnabledExtensionNames(strings(stack, enabledExtensions));
			if (validationEnabled) info.ppEnabledLayerNames(strings(stack, java.util.Collections.singletonList("VK_LAYER_KHRONOS_validation")));
			PointerBuffer pointer = stack.mallocPointer(1);
			check(vkCreateInstance(info, null, pointer), "vkCreateInstance");
			instance = new VkInstance(pointer.get(0), info);
			trackCreate();
		}
		if (debugUtilsEnabled) createDebugMessenger();
	}

	private void createDebugMessenger()
	{
		debugCallback = VkDebugUtilsMessengerCallbackEXT.create((severity, types, callbackData, userData) ->
		{
			String message = VkDebugUtilsMessengerCallbackDataEXT.create(callbackData).pMessageString();
			System.err.println("[vulkan-validation] " + message);
			if ((severity & VK_DEBUG_UTILS_MESSAGE_SEVERITY_ERROR_BIT_EXT) != 0) counterValues[COUNTER_COMMAND_ERRORS]++;
			return VK_FALSE;
		});
		try (MemoryStack stack = MemoryStack.stackPush())
		{
			VkDebugUtilsMessengerCreateInfoEXT info = VkDebugUtilsMessengerCreateInfoEXT.calloc(stack).sType$Default()
				.messageSeverity(VK_DEBUG_UTILS_MESSAGE_SEVERITY_WARNING_BIT_EXT | VK_DEBUG_UTILS_MESSAGE_SEVERITY_ERROR_BIT_EXT)
				.messageType(VK_DEBUG_UTILS_MESSAGE_TYPE_GENERAL_BIT_EXT | VK_DEBUG_UTILS_MESSAGE_TYPE_VALIDATION_BIT_EXT |
					VK_DEBUG_UTILS_MESSAGE_TYPE_PERFORMANCE_BIT_EXT).pfnUserCallback(debugCallback);
			LongBuffer handle = stack.mallocLong(1);
			check(EXTDebugUtils.vkCreateDebugUtilsMessengerEXT(instance, info, null, handle), "vkCreateDebugUtilsMessengerEXT");
			debugMessenger = handle.get(0);
			trackCreate();
		}
	}

	private void createSurface()
	{
		try (MemoryStack stack = MemoryStack.stackPush())
		{
			VkMetalSurfaceCreateInfoEXT info = VkMetalSurfaceCreateInfoEXT.calloc(stack).sType$Default();
			memPutAddress(info.address() + VkMetalSurfaceCreateInfoEXT.PLAYER, layerHandle);
			LongBuffer handle = stack.mallocLong(1);
			check(EXTMetalSurface.vkCreateMetalSurfaceEXT(instance, info, null, handle), "vkCreateMetalSurfaceEXT");
			surface = handle.get(0);
			trackCreate();
		}
	}

	private void selectPhysicalDevice()
	{
		try (MemoryStack stack = MemoryStack.stackPush())
		{
			IntBuffer count = stack.mallocInt(1);
			check(vkEnumeratePhysicalDevices(instance, count, null), "vkEnumeratePhysicalDevices(count)");
			PointerBuffer devices = stack.mallocPointer(count.get(0));
			check(vkEnumeratePhysicalDevices(instance, count, devices), "vkEnumeratePhysicalDevices");
			for (int index = 0; index < devices.remaining(); index++)
			{
				VkPhysicalDevice candidate = new VkPhysicalDevice(devices.get(index), instance);
				Candidate support = inspectCandidate(candidate);
				if (support != null)
				{
					physicalDevice = candidate;
					graphicsQueueFamily = support.graphicsFamily;
					presentQueueFamily = support.presentFamily;
					deviceExtensions = support.extensions;
					portabilitySubset = deviceExtensions.contains("VK_KHR_portability_subset");
					physicalDeviceName = support.name;
					timestampValidBits = support.timestampValidBits;
					timestampPeriodNs = support.timestampPeriodNs;
					timestampsSupported = timestampValidBits > 0 && Double.isFinite(timestampPeriodNs) && timestampPeriodNs > 0;
					return;
				}
			}
		}
		throw new IllegalStateException("No Vulkan 1.2 device supports graphics and presentation on the borrowed CAMetalLayer.");
	}

	private Candidate inspectCandidate(VkPhysicalDevice candidate)
	{
		List<String> extensions = enumerateDeviceExtensions(candidate);
		if (!extensions.contains(KHRSwapchain.VK_KHR_SWAPCHAIN_EXTENSION_NAME)) return null;
		try (MemoryStack stack = MemoryStack.stackPush())
		{
			VkPhysicalDeviceProperties properties = VkPhysicalDeviceProperties.malloc(stack);
			vkGetPhysicalDeviceProperties(candidate, properties);
			if (properties.apiVersion() < VK_MAKE_API_VERSION(0, 1, 2, 0)) return null;
			IntBuffer queueCount = stack.mallocInt(1);
			vkGetPhysicalDeviceQueueFamilyProperties(candidate, queueCount, null);
			VkQueueFamilyProperties.Buffer queues = VkQueueFamilyProperties.malloc(queueCount.get(0), stack);
			vkGetPhysicalDeviceQueueFamilyProperties(candidate, queueCount, queues);
			int graphics = -1;
			int present = -1;
			int validBits = 0;
			IntBuffer supported = stack.mallocInt(1);
			for (int index = 0; index < queues.capacity(); index++)
			{
				VkQueueFamilyProperties queue = queues.get(index);
				if (graphics < 0 && queue.queueCount() > 0 && (queue.queueFlags() & VK_QUEUE_GRAPHICS_BIT) != 0)
				{
					graphics = index;
					validBits = queue.timestampValidBits();
				}
				check(KHRSurface.vkGetPhysicalDeviceSurfaceSupportKHR(candidate, index, surface, supported), "vkGetPhysicalDeviceSurfaceSupportKHR");
				if (present < 0 && queue.queueCount() > 0 && supported.get(0) == VK_TRUE) present = index;
			}
			if (graphics < 0 || present < 0 || !hasRequiredSurfaceSupport(candidate)) return null;
			return new Candidate(graphics, present, extensions, properties.deviceNameString(), validBits, properties.limits().timestampPeriod());
		}
	}

	private boolean hasRequiredSurfaceSupport(VkPhysicalDevice candidate)
	{
		SurfaceSupport support = querySurfaceSupport(candidate);
		boolean bgra = false;
		boolean fifo = false;
		for (SurfaceFormat format : support.formats) if (format.format == VK_FORMAT_B8G8R8A8_UNORM) bgra = true;
		for (int mode : support.presentModes) if (mode == VK_PRESENT_MODE_FIFO_KHR) fifo = true;
		return bgra && fifo;
	}

	private void createDevice()
	{
		Set<Integer> families = new LinkedHashSet<>();
		families.add(graphicsQueueFamily);
		families.add(presentQueueFamily);
		List<String> enabledExtensions = new ArrayList<>();
		enabledExtensions.add(KHRSwapchain.VK_KHR_SWAPCHAIN_EXTENSION_NAME);
		if (portabilitySubset) enabledExtensions.add("VK_KHR_portability_subset");
		try (MemoryStack stack = MemoryStack.stackPush())
		{
			VkDeviceQueueCreateInfo.Buffer queues = VkDeviceQueueCreateInfo.calloc(families.size(), stack);
			int index = 0;
			for (int family : families)
			{
				FloatBuffer priority = stack.floats(1.0f);
				queues.get(index++).sType$Default().queueFamilyIndex(family).pQueuePriorities(priority);
			}
			VkDeviceCreateInfo info = VkDeviceCreateInfo.calloc(stack).sType$Default()
				.pQueueCreateInfos(queues).ppEnabledExtensionNames(strings(stack, enabledExtensions));
			PointerBuffer pointer = stack.mallocPointer(1);
			check(vkCreateDevice(physicalDevice, info, null, pointer), "vkCreateDevice");
			device = new VkDevice(pointer.get(0), physicalDevice, info);
			trackCreate();
			PointerBuffer queue = stack.mallocPointer(1);
			vkGetDeviceQueue(device, graphicsQueueFamily, 0, queue);
			graphicsQueue = new VkQueue(queue.get(0), device);
			vkGetDeviceQueue(device, presentQueueFamily, 0, queue);
			presentQueue = new VkQueue(queue.get(0), device);
		}
	}

	private void createCommandResources()
	{
		try (MemoryStack stack = MemoryStack.stackPush())
		{
			LongBuffer handle = stack.mallocLong(1);
			VkCommandPoolCreateInfo pool = VkCommandPoolCreateInfo.calloc(stack).sType$Default()
				.flags(VK_COMMAND_POOL_CREATE_RESET_COMMAND_BUFFER_BIT).queueFamilyIndex(graphicsQueueFamily);
			check(vkCreateCommandPool(device, pool, null, handle), "vkCreateCommandPool");
			commandPool = handle.get(0);
			trackCreate();
			VkCommandBufferAllocateInfo allocate = VkCommandBufferAllocateInfo.calloc(stack).sType$Default()
				.commandPool(commandPool).level(VK_COMMAND_BUFFER_LEVEL_PRIMARY).commandBufferCount(FRAME_COUNT);
			PointerBuffer buffers = stack.mallocPointer(FRAME_COUNT);
			check(vkAllocateCommandBuffers(device, allocate, buffers), "vkAllocateCommandBuffers");
			VkSemaphoreCreateInfo semaphore = VkSemaphoreCreateInfo.calloc(stack).sType$Default();
			VkFenceCreateInfo fence = VkFenceCreateInfo.calloc(stack).sType$Default().flags(VK_FENCE_CREATE_SIGNALED_BIT);
			for (int index = 0; index < FRAME_COUNT; index++)
			{
				FrameResources frame = new FrameResources();
				frame.commandBuffer = new VkCommandBuffer(buffers.get(index), device);
				trackCreate();
				check(vkCreateSemaphore(device, semaphore, null, handle), "vkCreateSemaphore(acquire)");
				frame.acquireSemaphore = handle.get(0); trackCreate();
				check(vkCreateFence(device, fence, null, handle), "vkCreateFence");
				frame.fence = handle.get(0); trackCreate();
				if (timestampsSupported)
				{
					VkQueryPoolCreateInfo query = VkQueryPoolCreateInfo.calloc(stack).sType$Default()
						.queryType(VK_QUERY_TYPE_TIMESTAMP).queryCount(2);
					check(vkCreateQueryPool(device, query, null, handle), "vkCreateQueryPool");
					frame.queryPool = handle.get(0); trackCreate();
				}
				frames[index] = frame;
			}
		}
	}

	private void destroyCommandResources()
	{
		if (device == null) return;
		for (FrameResources frame : frames)
		{
			if (frame == null) continue;
			if (frame.queryPool != NULL) { vkDestroyQueryPool(device, frame.queryPool, null); frame.queryPool = NULL; trackRelease(); }
			if (frame.acquireSemaphore != NULL) { vkDestroySemaphore(device, frame.acquireSemaphore, null); frame.acquireSemaphore = NULL; trackRelease(); }
			if (frame.fence != NULL) { vkDestroyFence(device, frame.fence, null); frame.fence = NULL; trackRelease(); }
			if (frame.commandBuffer != null && commandPool != NULL) { vkFreeCommandBuffers(device, commandPool, frame.commandBuffer); frame.commandBuffer = null; trackRelease(); }
		}
		if (commandPool != NULL) { vkDestroyCommandPool(device, commandPool, null); commandPool = NULL; trackRelease(); }
	}

	private void createSwapchain(int requestedWidth, int requestedHeight)
	{
		SurfaceSupport support = querySurfaceSupport(physicalDevice);
		SurfaceFormat selectedFormat = null;
		for (SurfaceFormat format : support.formats) if (format.format == VK_FORMAT_B8G8R8A8_UNORM) { selectedFormat = format; break; }
		if (selectedFormat == null) throw new IllegalStateException("VK_FORMAT_B8G8R8A8_UNORM is not advertised for the Metal surface.");
		List<Integer> modes = new ArrayList<>();
		for (int mode : support.presentModes) modes.add(mode);
		SwapchainSelection selection = SwapchainSelection.choose(support.minImages, support.maxImages,
			support.currentWidth, support.currentHeight, support.minWidth, support.minHeight, support.maxWidth, support.maxHeight,
			requestedWidth, requestedHeight, support.compositeAlpha, support.currentTransform, modes, requestedMode);
		if (selection.suspended()) throw new IllegalStateException("Cannot create a swapchain for a zero extent.");
		SwapchainData data = new SwapchainData();
		data.requestedWidth = requestedWidth;
		data.requestedHeight = requestedHeight;
		data.width = selection.width();
		data.height = selection.height();
		data.format = selectedFormat.format;
		data.colorSpace = selectedFormat.colorSpace;
		data.requestedImages = selection.requestedImageCount();
		data.presentMode = selection.effectivePresentMode();
		try (MemoryStack stack = MemoryStack.stackPush())
		{
			VkSwapchainCreateInfoKHR info = VkSwapchainCreateInfoKHR.calloc(stack).sType$Default()
				.surface(surface).minImageCount(data.requestedImages).imageFormat(data.format).imageColorSpace(data.colorSpace)
				.imageExtent(VkExtent2D.calloc(stack).set(data.width, data.height)).imageArrayLayers(1)
				.imageUsage(VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT).preTransform(selection.preTransform())
				.compositeAlpha(selection.compositeAlpha()).presentMode(vulkanMode(data.presentMode)).clipped(true).oldSwapchain(NULL);
			if (graphicsQueueFamily != presentQueueFamily)
			{
				info.imageSharingMode(VK_SHARING_MODE_CONCURRENT)
					.pQueueFamilyIndices(stack.ints(graphicsQueueFamily, presentQueueFamily));
			}
			else info.imageSharingMode(VK_SHARING_MODE_EXCLUSIVE);
			LongBuffer handle = stack.mallocLong(1);
			check(KHRSwapchain.vkCreateSwapchainKHR(device, info, null, handle), "vkCreateSwapchainKHR");
			data.handle = handle.get(0); trackCreate();
			IntBuffer count = stack.mallocInt(1);
			check(KHRSwapchain.vkGetSwapchainImagesKHR(device, data.handle, count, null), "vkGetSwapchainImagesKHR(count)");
			LongBuffer images = stack.mallocLong(count.get(0));
			check(KHRSwapchain.vkGetSwapchainImagesKHR(device, data.handle, count, images), "vkGetSwapchainImagesKHR");
			selection.requireActualImageCount(count.get(0));
			data.images = new long[count.get(0)];
			for (int index = 0; index < data.images.length; index++) data.images[index] = images.get(index);
		}
		createSwapchainObjects(data);
		swapchain = data;
		effectiveMode = data.presentMode;
		if (effectiveMode != requestedMode) counterValues[COUNTER_PRESENT_MODE_DIVERGENCES]++;
		generation++;
		VulkanTimingLog.CapabilityRecord capability = new VulkanTimingLog.CapabilityRecord();
		capability.portabilitySubset = portabilitySubset;
		capability.colorSpace = colorSpaceName(data.colorSpace);
		capability.requestedImages = data.requestedImages;
		capability.actualImages = data.images.length;
		capability.graphicsQueueFamily = graphicsQueueFamily;
		capability.presentQueueFamily = presentQueueFamily;
		capability.validationRequested = validationRequested;
		capability.validationEnabled = validationEnabled;
		capability.debugUtilsEnabled = debugUtilsEnabled;
		capability.timestampsSupported = timestampsSupported;
		capability.timestampValidBits = timestampValidBits;
		capability.timestampPeriodNs = timestampPeriodNs;
		capability.mailboxAvailable = modes.contains(VK_PRESENT_MODE_MAILBOX_KHR);
		capability.immediateAvailable = modes.contains(VK_PRESENT_MODE_IMMEDIATE_KHR);
		capability.physicalDevice = physicalDeviceName;
		timingLog.runStart(requestedMode, effectiveMode, capability);
	}

	private void recreateSwapchain(int width, int height)
	{
		check(vkDeviceWaitIdle(device), "vkDeviceWaitIdle(recreate)");
		finishAllFrames();
		destroySwapchain();
		createSwapchain(width, height);
		counterValues[COUNTER_RESIZE_REBUILDS]++;
		recreatePending = false;
	}

	private void createSwapchainObjects(SwapchainData data)
	{
		createImageViews(data);
		createRenderPass(data);
		createDescriptorLayout(data);
		createPipelineLayout(data);
		createPipelines(data);
		createFramebuffers(data);
		createSampler(data);
		createFrameUiResources(data);
		createRenderFinishedSemaphores(data);
		data.imageFences = new long[data.images.length];
	}

	private void destroySwapchain()
	{
		SwapchainData data = swapchain;
		if (data == null || device == null) return;
		for (FrameResources frame : frames) destroyUiResources(frame);
		if (data.descriptorPool != NULL) { vkDestroyDescriptorPool(device, data.descriptorPool, null); trackRelease(data.descriptorSetCount + 1); }
		if (data.sampler != NULL) { vkDestroySampler(device, data.sampler, null); trackRelease(); }
		if (data.trianglePipeline != NULL) { vkDestroyPipeline(device, data.trianglePipeline, null); trackRelease(); }
		if (data.uiPipeline != NULL) { vkDestroyPipeline(device, data.uiPipeline, null); trackRelease(); }
		if (data.pipelineLayout != NULL) { vkDestroyPipelineLayout(device, data.pipelineLayout, null); trackRelease(); }
		if (data.descriptorLayout != NULL) { vkDestroyDescriptorSetLayout(device, data.descriptorLayout, null); trackRelease(); }
		for (long framebuffer : data.framebuffers) { vkDestroyFramebuffer(device, framebuffer, null); trackRelease(); }
		if (data.renderPass != NULL) { vkDestroyRenderPass(device, data.renderPass, null); trackRelease(); }
		for (long view : data.imageViews) { vkDestroyImageView(device, view, null); trackRelease(); }
		for (long semaphore : data.renderFinishedSemaphores) { vkDestroySemaphore(device, semaphore, null); trackRelease(); }
		if (data.handle != NULL) { KHRSwapchain.vkDestroySwapchainKHR(device, data.handle, null); trackRelease(); }
		swapchain = null;
	}

	private SurfaceSupport querySurfaceSupport(VkPhysicalDevice candidate)
	{
		try (MemoryStack stack = MemoryStack.stackPush())
		{
			VkSurfaceCapabilitiesKHR capabilities = VkSurfaceCapabilitiesKHR.malloc(stack);
			check(KHRSurface.vkGetPhysicalDeviceSurfaceCapabilitiesKHR(candidate, surface, capabilities), "vkGetPhysicalDeviceSurfaceCapabilitiesKHR");
			SurfaceSupport support = new SurfaceSupport();
			support.minImages = capabilities.minImageCount();
			support.maxImages = capabilities.maxImageCount();
			support.currentWidth = capabilities.currentExtent().width() == -1 ? -1 : capabilities.currentExtent().width();
			support.currentHeight = capabilities.currentExtent().height() == -1 ? -1 : capabilities.currentExtent().height();
			support.minWidth = capabilities.minImageExtent().width();
			support.minHeight = capabilities.minImageExtent().height();
			support.maxWidth = capabilities.maxImageExtent().width();
			support.maxHeight = capabilities.maxImageExtent().height();
			support.compositeAlpha = capabilities.supportedCompositeAlpha();
			support.currentTransform = capabilities.currentTransform();
			IntBuffer count = stack.mallocInt(1);
			check(KHRSurface.vkGetPhysicalDeviceSurfaceFormatsKHR(candidate, surface, count, null), "vkGetPhysicalDeviceSurfaceFormatsKHR(count)");
			VkSurfaceFormatKHR.Buffer formats = VkSurfaceFormatKHR.malloc(count.get(0), stack);
			check(KHRSurface.vkGetPhysicalDeviceSurfaceFormatsKHR(candidate, surface, count, formats), "vkGetPhysicalDeviceSurfaceFormatsKHR");
			for (int index = 0; index < formats.capacity(); index++) support.formats.add(new SurfaceFormat(formats.get(index).format(), formats.get(index).colorSpace()));
			check(KHRSurface.vkGetPhysicalDeviceSurfacePresentModesKHR(candidate, surface, count, null), "vkGetPhysicalDeviceSurfacePresentModesKHR(count)");
			IntBuffer modes = stack.mallocInt(count.get(0));
			check(KHRSurface.vkGetPhysicalDeviceSurfacePresentModesKHR(candidate, surface, count, modes), "vkGetPhysicalDeviceSurfacePresentModesKHR");
			for (int index = 0; index < modes.capacity(); index++) support.presentModes.add(modes.get(index));
			return support;
		}
	}

	private void createImageViews(SwapchainData data)
	{
		data.imageViews = new long[data.images.length];
		try (MemoryStack stack = MemoryStack.stackPush())
		{
			LongBuffer handle = stack.mallocLong(1);
			for (int index = 0; index < data.images.length; index++)
			{
				VkImageViewCreateInfo info = VkImageViewCreateInfo.calloc(stack).sType$Default().image(data.images[index])
					.viewType(VK_IMAGE_VIEW_TYPE_2D).format(data.format);
				info.components().r(VK_COMPONENT_SWIZZLE_IDENTITY).g(VK_COMPONENT_SWIZZLE_IDENTITY)
					.b(VK_COMPONENT_SWIZZLE_IDENTITY).a(VK_COMPONENT_SWIZZLE_IDENTITY);
				info.subresourceRange().aspectMask(VK_IMAGE_ASPECT_COLOR_BIT).baseMipLevel(0).levelCount(1)
					.baseArrayLayer(0).layerCount(1);
				check(vkCreateImageView(device, info, null, handle), "vkCreateImageView(swapchain)");
				data.imageViews[index] = handle.get(0); trackCreate();
			}
		}
	}

	private void createRenderPass(SwapchainData data)
	{
		try (MemoryStack stack = MemoryStack.stackPush())
		{
			VkAttachmentDescription.Buffer attachment = VkAttachmentDescription.calloc(1, stack);
			attachment.get(0).format(data.format).samples(VK_SAMPLE_COUNT_1_BIT).loadOp(VK_ATTACHMENT_LOAD_OP_CLEAR)
				.storeOp(VK_ATTACHMENT_STORE_OP_STORE).stencilLoadOp(VK_ATTACHMENT_LOAD_OP_DONT_CARE)
				.stencilStoreOp(VK_ATTACHMENT_STORE_OP_DONT_CARE).initialLayout(VK_IMAGE_LAYOUT_UNDEFINED)
				.finalLayout(VK_IMAGE_LAYOUT_PRESENT_SRC_KHR);
			VkAttachmentReference.Buffer color = VkAttachmentReference.calloc(1, stack);
			color.get(0).attachment(0).layout(VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL);
			VkSubpassDescription.Buffer subpass = VkSubpassDescription.calloc(1, stack);
			subpass.get(0).pipelineBindPoint(VK_PIPELINE_BIND_POINT_GRAPHICS).colorAttachmentCount(1).pColorAttachments(color);
			VkSubpassDependency.Buffer dependency = VkSubpassDependency.calloc(1, stack);
			dependency.get(0).srcSubpass(VK_SUBPASS_EXTERNAL).dstSubpass(0)
				.srcStageMask(VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT).dstStageMask(VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT)
				.dstAccessMask(VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT);
			VkRenderPassCreateInfo info = VkRenderPassCreateInfo.calloc(stack).sType$Default()
				.pAttachments(attachment).pSubpasses(subpass).pDependencies(dependency);
			LongBuffer handle = stack.mallocLong(1);
			check(vkCreateRenderPass(device, info, null, handle), "vkCreateRenderPass");
			data.renderPass = handle.get(0); trackCreate();
		}
	}

	private void createDescriptorLayout(SwapchainData data)
	{
		try (MemoryStack stack = MemoryStack.stackPush())
		{
			VkDescriptorSetLayoutBinding.Buffer binding = VkDescriptorSetLayoutBinding.calloc(1, stack);
			binding.get(0).binding(0).descriptorType(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER).descriptorCount(1)
				.stageFlags(VK_SHADER_STAGE_FRAGMENT_BIT);
			VkDescriptorSetLayoutCreateInfo info = VkDescriptorSetLayoutCreateInfo.calloc(stack).sType$Default().pBindings(binding);
			LongBuffer handle = stack.mallocLong(1);
			check(vkCreateDescriptorSetLayout(device, info, null, handle), "vkCreateDescriptorSetLayout");
			data.descriptorLayout = handle.get(0); trackCreate();
		}
	}

	private void createPipelineLayout(SwapchainData data)
	{
		try (MemoryStack stack = MemoryStack.stackPush())
		{
			VkPushConstantRange.Buffer push = VkPushConstantRange.calloc(1, stack);
			push.get(0).stageFlags(VK_SHADER_STAGE_VERTEX_BIT).offset(0).size(4);
			VkPipelineLayoutCreateInfo info = VkPipelineLayoutCreateInfo.calloc(stack).sType$Default()
				.pSetLayouts(stack.longs(data.descriptorLayout)).pPushConstantRanges(push);
			LongBuffer handle = stack.mallocLong(1);
			check(vkCreatePipelineLayout(device, info, null, handle), "vkCreatePipelineLayout");
			data.pipelineLayout = handle.get(0); trackCreate();
		}
	}

	private void createPipelines(SwapchainData data)
	{
		data.trianglePipeline = createPipeline(data, "triangle.vert.spv", "triangle.frag.spv", false);
		data.uiPipeline = createPipeline(data, "ui.vert.spv", "ui.frag.spv", true);
	}

	private long createPipeline(SwapchainData data, String vertexResource, String fragmentResource, boolean blend)
	{
		long vertex = createShaderModule(vertexResource);
		long fragment = createShaderModule(fragmentResource);
		try (MemoryStack stack = MemoryStack.stackPush())
		{
			ByteBuffer main = stack.UTF8("main");
			VkPipelineShaderStageCreateInfo.Buffer stages = VkPipelineShaderStageCreateInfo.calloc(2, stack);
			stages.get(0).sType$Default().stage(VK_SHADER_STAGE_VERTEX_BIT).module(vertex).pName(main);
			stages.get(1).sType$Default().stage(VK_SHADER_STAGE_FRAGMENT_BIT).module(fragment).pName(main);
			VkPipelineVertexInputStateCreateInfo vertexInput = VkPipelineVertexInputStateCreateInfo.calloc(stack).sType$Default();
			VkPipelineInputAssemblyStateCreateInfo assembly = VkPipelineInputAssemblyStateCreateInfo.calloc(stack).sType$Default()
				.topology(VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST);
			VkPipelineViewportStateCreateInfo viewport = VkPipelineViewportStateCreateInfo.calloc(stack).sType$Default()
				.viewportCount(1).scissorCount(1);
			VkPipelineRasterizationStateCreateInfo raster = VkPipelineRasterizationStateCreateInfo.calloc(stack).sType$Default()
				.depthClampEnable(false).rasterizerDiscardEnable(false).polygonMode(VK_POLYGON_MODE_FILL).cullMode(VK_CULL_MODE_NONE)
				.frontFace(VK_FRONT_FACE_COUNTER_CLOCKWISE).lineWidth(1.0f);
			VkPipelineMultisampleStateCreateInfo multisample = VkPipelineMultisampleStateCreateInfo.calloc(stack).sType$Default()
				.rasterizationSamples(VK_SAMPLE_COUNT_1_BIT);
			VkPipelineColorBlendAttachmentState.Buffer attachment = VkPipelineColorBlendAttachmentState.calloc(1, stack);
			attachment.get(0).colorWriteMask(VK_COLOR_COMPONENT_R_BIT | VK_COLOR_COMPONENT_G_BIT | VK_COLOR_COMPONENT_B_BIT |
				VK_COLOR_COMPONENT_A_BIT).blendEnable(blend);
			if (blend) attachment.get(0).srcColorBlendFactor(VK_BLEND_FACTOR_ONE).dstColorBlendFactor(VK_BLEND_FACTOR_ONE_MINUS_SRC_ALPHA)
				.colorBlendOp(VK_BLEND_OP_ADD).srcAlphaBlendFactor(VK_BLEND_FACTOR_ONE)
				.dstAlphaBlendFactor(VK_BLEND_FACTOR_ONE_MINUS_SRC_ALPHA).alphaBlendOp(VK_BLEND_OP_ADD);
			VkPipelineColorBlendStateCreateInfo colorBlend = VkPipelineColorBlendStateCreateInfo.calloc(stack).sType$Default()
				.pAttachments(attachment);
			VkPipelineDynamicStateCreateInfo dynamic = VkPipelineDynamicStateCreateInfo.calloc(stack).sType$Default()
				.pDynamicStates(stack.ints(VK_DYNAMIC_STATE_VIEWPORT, VK_DYNAMIC_STATE_SCISSOR));
			VkGraphicsPipelineCreateInfo.Buffer pipeline = VkGraphicsPipelineCreateInfo.calloc(1, stack);
			pipeline.get(0).sType$Default().pStages(stages).pVertexInputState(vertexInput).pInputAssemblyState(assembly)
				.pViewportState(viewport).pRasterizationState(raster).pMultisampleState(multisample)
				.pColorBlendState(colorBlend).pDynamicState(dynamic).layout(data.pipelineLayout).renderPass(data.renderPass).subpass(0);
			LongBuffer handle = stack.mallocLong(1);
			int result = vkCreateGraphicsPipelines(device, NULL, pipeline, null, handle);
			if (result != VK_SUCCESS) { counterValues[COUNTER_PIPELINE_ERRORS]++; throw failure("vkCreateGraphicsPipelines", result); }
			trackCreate();
			return handle.get(0);
		}
		finally
		{
			vkDestroyShaderModule(device, fragment, null); trackRelease();
			vkDestroyShaderModule(device, vertex, null); trackRelease();
		}
	}

	private long createShaderModule(String name)
	{
		byte[] bytes;
		try (InputStream input = LwjglVulkanBackend.class.getResourceAsStream("/shaders/vulkan-control/" + name))
		{
			if (input == null) throw new IllegalStateException("Missing SPIR-V resource: " + name);
			bytes = input.readAllBytes();
		}
		catch (IOException ex)
		{
			counterValues[COUNTER_SHADER_ERRORS]++;
			throw new IllegalStateException("Unable to read SPIR-V resource: " + name, ex);
		}
		try (MemoryStack stack = MemoryStack.stackPush())
		{
			ByteBuffer code = stack.malloc(bytes.length).put(bytes).flip();
			VkShaderModuleCreateInfo info = VkShaderModuleCreateInfo.calloc(stack).sType$Default().pCode(code);
			LongBuffer handle = stack.mallocLong(1);
			int result = vkCreateShaderModule(device, info, null, handle);
			if (result != VK_SUCCESS) { counterValues[COUNTER_SHADER_ERRORS]++; throw failure("vkCreateShaderModule", result); }
			trackCreate();
			return handle.get(0);
		}
	}

	private void createFramebuffers(SwapchainData data)
	{
		data.framebuffers = new long[data.imageViews.length];
		try (MemoryStack stack = MemoryStack.stackPush())
		{
			LongBuffer handle = stack.mallocLong(1);
			for (int index = 0; index < data.imageViews.length; index++)
			{
				VkFramebufferCreateInfo info = VkFramebufferCreateInfo.calloc(stack).sType$Default()
					.renderPass(data.renderPass).pAttachments(stack.longs(data.imageViews[index]))
					.width(data.width).height(data.height).layers(1);
				check(vkCreateFramebuffer(device, info, null, handle), "vkCreateFramebuffer");
				data.framebuffers[index] = handle.get(0); trackCreate();
			}
		}
	}

	private void createSampler(SwapchainData data)
	{
		try (MemoryStack stack = MemoryStack.stackPush())
		{
			VkSamplerCreateInfo info = VkSamplerCreateInfo.calloc(stack).sType$Default()
				.magFilter(VK_FILTER_NEAREST).minFilter(VK_FILTER_NEAREST).mipmapMode(VK_SAMPLER_MIPMAP_MODE_NEAREST)
				.addressModeU(VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE).addressModeV(VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE)
				.addressModeW(VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE).maxLod(0.0f);
			LongBuffer handle = stack.mallocLong(1);
			check(vkCreateSampler(device, info, null, handle), "vkCreateSampler");
			data.sampler = handle.get(0); trackCreate();
		}
	}

	private void createFrameUiResources(SwapchainData data)
	{
		long byteCount = Math.multiplyExact(Math.multiplyExact((long) data.width, data.height), 4L);
		try (MemoryStack stack = MemoryStack.stackPush())
		{
			VkDescriptorPoolSize.Buffer size = VkDescriptorPoolSize.calloc(1, stack);
			size.get(0).type(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER).descriptorCount(FRAME_COUNT);
			VkDescriptorPoolCreateInfo pool = VkDescriptorPoolCreateInfo.calloc(stack).sType$Default()
				.maxSets(FRAME_COUNT).pPoolSizes(size);
			LongBuffer handle = stack.mallocLong(1);
			check(vkCreateDescriptorPool(device, pool, null, handle), "vkCreateDescriptorPool");
			data.descriptorPool = handle.get(0); trackCreate();
			LongBuffer layouts = stack.mallocLong(FRAME_COUNT);
			for (int index = 0; index < FRAME_COUNT; index++) layouts.put(index, data.descriptorLayout);
			VkDescriptorSetAllocateInfo allocate = VkDescriptorSetAllocateInfo.calloc(stack).sType$Default()
				.descriptorPool(data.descriptorPool).pSetLayouts(layouts);
			LongBuffer sets = stack.mallocLong(FRAME_COUNT);
			check(vkAllocateDescriptorSets(device, allocate, sets), "vkAllocateDescriptorSets");
			data.descriptorSetCount = FRAME_COUNT;
			trackCreate(FRAME_COUNT);
			for (int index = 0; index < FRAME_COUNT; index++)
			{
				FrameResources frame = frames[index];
				frame.staging = createBuffer(byteCount, VK_BUFFER_USAGE_TRANSFER_SRC_BIT,
					VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT);
				frame.ui = createImage(data.width, data.height, data.format,
					VK_IMAGE_USAGE_TRANSFER_DST_BIT | VK_IMAGE_USAGE_SAMPLED_BIT, VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT, true);
				frame.descriptorSet = sets.get(index);
				VkDescriptorImageInfo.Buffer image = VkDescriptorImageInfo.calloc(1, stack);
				image.get(0).sampler(data.sampler).imageView(frame.ui.view).imageLayout(VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL);
				VkWriteDescriptorSet.Buffer write = VkWriteDescriptorSet.calloc(1, stack);
				write.get(0).sType$Default().dstSet(frame.descriptorSet).dstBinding(0)
					.descriptorCount(1).descriptorType(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER).pImageInfo(image);
				vkUpdateDescriptorSets(device, write, null);
			}
		}
	}

	private void createRenderFinishedSemaphores(SwapchainData data)
	{
		data.renderFinishedSemaphores = new long[data.images.length];
		try (MemoryStack stack = MemoryStack.stackPush())
		{
			VkSemaphoreCreateInfo info = VkSemaphoreCreateInfo.calloc(stack).sType$Default();
			LongBuffer handle = stack.mallocLong(1);
			for (int index = 0; index < data.images.length; index++)
			{
				check(vkCreateSemaphore(device, info, null, handle), "vkCreateSemaphore(present)");
				data.renderFinishedSemaphores[index] = handle.get(0); trackCreate();
			}
		}
	}

	private void destroyUiResources(FrameResources frame)
	{
		if (frame == null) return;
		destroyImage(frame.ui);
		frame.ui = null;
		destroyBuffer(frame.staging);
		frame.staging = null;
		frame.descriptorSet = NULL;
	}

	private BufferResource createBuffer(long size, int usage, int properties)
	{
		BufferResource resource = new BufferResource();
		resource.size = size;
		try (MemoryStack stack = MemoryStack.stackPush())
		{
			VkBufferCreateInfo info = VkBufferCreateInfo.calloc(stack).sType$Default().size(size)
				.usage(usage).sharingMode(VK_SHARING_MODE_EXCLUSIVE);
			LongBuffer handle = stack.mallocLong(1);
			check(vkCreateBuffer(device, info, null, handle), "vkCreateBuffer");
			resource.buffer = handle.get(0); trackCreate();
			VkMemoryRequirements requirements = VkMemoryRequirements.malloc(stack);
			vkGetBufferMemoryRequirements(device, resource.buffer, requirements);
			VkMemoryAllocateInfo allocate = VkMemoryAllocateInfo.calloc(stack).sType$Default().allocationSize(requirements.size())
				.memoryTypeIndex(findMemoryType(requirements.memoryTypeBits(), properties));
			check(vkAllocateMemory(device, allocate, null, handle), "vkAllocateMemory(buffer)");
			resource.memory = handle.get(0); trackCreate();
			check(vkBindBufferMemory(device, resource.buffer, resource.memory, 0), "vkBindBufferMemory");
		}
		return resource;
	}

	private ImageResource createImage(int width, int height, int format, int usage, int properties, boolean view)
	{
		ImageResource resource = new ImageResource();
		resource.width = width;
		resource.height = height;
		resource.format = format;
		try (MemoryStack stack = MemoryStack.stackPush())
		{
			VkImageCreateInfo info = VkImageCreateInfo.calloc(stack).sType$Default().imageType(VK_IMAGE_TYPE_2D)
				.format(format);
			info.extent().set(width, height, 1);
			info.mipLevels(1).arrayLayers(1).samples(VK_SAMPLE_COUNT_1_BIT).tiling(VK_IMAGE_TILING_OPTIMAL)
				.usage(usage).sharingMode(VK_SHARING_MODE_EXCLUSIVE).initialLayout(VK_IMAGE_LAYOUT_UNDEFINED);
			LongBuffer handle = stack.mallocLong(1);
			check(vkCreateImage(device, info, null, handle), "vkCreateImage");
			resource.image = handle.get(0); trackCreate();
			VkMemoryRequirements requirements = VkMemoryRequirements.malloc(stack);
			vkGetImageMemoryRequirements(device, resource.image, requirements);
			VkMemoryAllocateInfo allocate = VkMemoryAllocateInfo.calloc(stack).sType$Default().allocationSize(requirements.size())
				.memoryTypeIndex(findMemoryType(requirements.memoryTypeBits(), properties));
			check(vkAllocateMemory(device, allocate, null, handle), "vkAllocateMemory(image)");
			resource.memory = handle.get(0); trackCreate();
			check(vkBindImageMemory(device, resource.image, resource.memory, 0), "vkBindImageMemory");
			if (view)
			{
				VkImageViewCreateInfo imageView = VkImageViewCreateInfo.calloc(stack).sType$Default().image(resource.image)
					.viewType(VK_IMAGE_VIEW_TYPE_2D).format(format);
				imageView.subresourceRange().aspectMask(VK_IMAGE_ASPECT_COLOR_BIT).baseMipLevel(0).levelCount(1)
					.baseArrayLayer(0).layerCount(1);
				check(vkCreateImageView(device, imageView, null, handle), "vkCreateImageView");
				resource.view = handle.get(0); trackCreate();
			}
		}
		return resource;
	}

	private void destroyBuffer(BufferResource resource)
	{
		if (resource == null || device == null) return;
		if (resource.buffer != NULL) { vkDestroyBuffer(device, resource.buffer, null); trackRelease(); }
		if (resource.memory != NULL) { vkFreeMemory(device, resource.memory, null); trackRelease(); }
	}

	private void destroyImage(ImageResource resource)
	{
		if (resource == null || device == null) return;
		if (resource.view != NULL) { vkDestroyImageView(device, resource.view, null); trackRelease(); }
		if (resource.image != NULL) { vkDestroyImage(device, resource.image, null); trackRelease(); }
		if (resource.memory != NULL) { vkFreeMemory(device, resource.memory, null); trackRelease(); }
	}

	private int findMemoryType(int mask, int properties)
	{
		try (MemoryStack stack = MemoryStack.stackPush())
		{
			VkPhysicalDeviceMemoryProperties memory = VkPhysicalDeviceMemoryProperties.malloc(stack);
			vkGetPhysicalDeviceMemoryProperties(physicalDevice, memory);
			for (int index = 0; index < memory.memoryTypeCount(); index++)
			{
				if ((mask & (1 << index)) != 0 && (memory.memoryTypes(index).propertyFlags() & properties) == properties) return index;
			}
		}
		throw new IllegalStateException("No compatible Vulkan memory type.");
	}

	private void upload(BufferResource target, byte[] bytes)
	{
		if (bytes.length != target.size) throw new IllegalArgumentException(
			"UI upload byte count " + bytes.length + " does not match swapchain storage " + target.size + '.');
		try (MemoryStack stack = MemoryStack.stackPush())
		{
			PointerBuffer pointer = stack.mallocPointer(1);
			check(vkMapMemory(device, target.memory, 0, target.size, 0, pointer), "vkMapMemory");
			memByteBuffer(pointer.get(0), bytes.length).put(bytes);
			vkUnmapMemory(device, target.memory);
		}
	}

	private void recordFrame(FrameResources frame, int imageIndex, long frameId)
	{
		check(vkResetCommandBuffer(frame.commandBuffer, 0), "vkResetCommandBuffer");
		try (MemoryStack stack = MemoryStack.stackPush())
		{
			VkCommandBufferBeginInfo begin = VkCommandBufferBeginInfo.calloc(stack).sType$Default();
			check(vkBeginCommandBuffer(frame.commandBuffer, begin), "vkBeginCommandBuffer");
			if (timestampsSupported)
			{
				vkCmdResetQueryPool(frame.commandBuffer, frame.queryPool, 0, 2);
				vkCmdWriteTimestamp(frame.commandBuffer, VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT, frame.queryPool, 0);
			}
			transitionUiForUpload(frame.commandBuffer, frame.ui);
			VkBufferImageCopy.Buffer copy = VkBufferImageCopy.calloc(1, stack);
			copy.get(0).bufferOffset(0).bufferRowLength(0).bufferImageHeight(0);
			copy.get(0).imageSubresource().aspectMask(VK_IMAGE_ASPECT_COLOR_BIT).mipLevel(0).baseArrayLayer(0).layerCount(1);
			copy.get(0).imageOffset().set(0, 0, 0);
			copy.get(0).imageExtent().set(frame.ui.width, frame.ui.height, 1);
			vkCmdCopyBufferToImage(frame.commandBuffer, frame.staging.buffer, frame.ui.image,
				VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, copy);
			transitionUiForSampling(frame.commandBuffer, frame.ui);
			recordScene(frame.commandBuffer, swapchain.renderPass, swapchain.framebuffers[imageIndex],
				swapchain.width, swapchain.height, frame.descriptorSet, frameId, false);
			if (timestampsSupported) vkCmdWriteTimestamp(frame.commandBuffer, VK_PIPELINE_STAGE_BOTTOM_OF_PIPE_BIT, frame.queryPool, 1);
			check(vkEndCommandBuffer(frame.commandBuffer), "vkEndCommandBuffer");
			frame.ui.initialized = true;
		}
	}

	private void transitionUiForUpload(VkCommandBuffer command, ImageResource image)
	{
		int oldLayout = image.initialized ? VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL : VK_IMAGE_LAYOUT_UNDEFINED;
		int sourceAccess = image.initialized ? VK_ACCESS_SHADER_READ_BIT : 0;
		int sourceStage = image.initialized ? VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT : VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT;
		imageBarrier(command, image.image, oldLayout, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
			sourceAccess, VK_ACCESS_TRANSFER_WRITE_BIT, sourceStage, VK_PIPELINE_STAGE_TRANSFER_BIT);
	}

	private void transitionUiForSampling(VkCommandBuffer command, ImageResource image)
	{
		imageBarrier(command, image.image, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL,
			VK_ACCESS_TRANSFER_WRITE_BIT, VK_ACCESS_SHADER_READ_BIT, VK_PIPELINE_STAGE_TRANSFER_BIT, VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT);
	}

	private void imageBarrier(VkCommandBuffer command, long image, int oldLayout, int newLayout,
		int sourceAccess, int destinationAccess, int sourceStage, int destinationStage)
	{
		try (MemoryStack stack = MemoryStack.stackPush())
		{
			VkImageMemoryBarrier.Buffer barrier = VkImageMemoryBarrier.calloc(1, stack);
			barrier.get(0).sType$Default().oldLayout(oldLayout).newLayout(newLayout)
				.srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED).dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
				.image(image).srcAccessMask(sourceAccess).dstAccessMask(destinationAccess);
			barrier.get(0).subresourceRange().aspectMask(VK_IMAGE_ASPECT_COLOR_BIT).baseMipLevel(0).levelCount(1)
				.baseArrayLayer(0).layerCount(1);
			vkCmdPipelineBarrier(command, sourceStage, destinationStage, 0, null, null, barrier);
		}
	}

	private void recordScene(VkCommandBuffer command, long renderPass, long framebuffer, int width, int height,
		long descriptorSet, long frameId, boolean fixedClear)
	{
		try (MemoryStack stack = MemoryStack.stackPush())
		{
			VkClearValue.Buffer clear = VkClearValue.calloc(1, stack);
			float pulse = fixedClear ? 0.75f : (float) (0.5 + 0.5 * Math.sin(frameId * 0.025));
			if (fixedClear) clear.get(0).color().float32(0, 0.10f).float32(1, 0.15f).float32(2, 0.20f).float32(3, 1.0f);
			else clear.get(0).color().float32(0, 0.04f + pulse * 0.08f).float32(1, 0.08f)
				.float32(2, 0.14f + pulse * 0.08f).float32(3, 1.0f);
			VkRenderPassBeginInfo begin = VkRenderPassBeginInfo.calloc(stack).sType$Default().renderPass(renderPass)
				.framebuffer(framebuffer).pClearValues(clear);
			begin.renderArea().offset().set(0, 0);
			begin.renderArea().extent().set(width, height);
			vkCmdBeginRenderPass(command, begin, VK_SUBPASS_CONTENTS_INLINE);
			VkViewport.Buffer viewport = VkViewport.calloc(1, stack);
			viewport.get(0).x(0).y(0).width(width).height(height).minDepth(0).maxDepth(1);
			vkCmdSetViewport(command, 0, viewport);
			org.lwjgl.vulkan.VkRect2D.Buffer scissor = org.lwjgl.vulkan.VkRect2D.calloc(1, stack);
			scissor.get(0).offset().set(0, 0); scissor.get(0).extent().set(width, height);
			vkCmdSetScissor(command, 0, scissor);
			vkCmdBindPipeline(command, VK_PIPELINE_BIND_POINT_GRAPHICS, swapchain.trianglePipeline);
			ByteBuffer phase = stack.malloc(4).putFloat(0, fixedClear ? 0.0f : (float) (frameId * 0.0125));
			vkCmdPushConstants(command, swapchain.pipelineLayout, VK_SHADER_STAGE_VERTEX_BIT, 0, phase);
			vkCmdDraw(command, 3, 1, 0, 0);
			vkCmdBindPipeline(command, VK_PIPELINE_BIND_POINT_GRAPHICS, swapchain.uiPipeline);
			vkCmdBindDescriptorSets(command, VK_PIPELINE_BIND_POINT_GRAPHICS, swapchain.pipelineLayout, 0,
				stack.longs(descriptorSet), null);
			vkCmdDraw(command, 6, 1, 0, 0);
			vkCmdEndRenderPass(command);
		}
	}

	private void finishFrame(FrameResources frame, boolean wait)
	{
		if (frame == null || !frame.submitted) return;
		int result = vkWaitForFences(device, frame.fence, true, wait ? -1L : 0L);
		if (!wait && result == VK_TIMEOUT) return;
		check(result, "vkWaitForFences(frame)");
		if (timestampsSupported)
		{
			try (MemoryStack stack = MemoryStack.stackPush())
			{
				LongBuffer values = stack.mallocLong(2);
				int query = vkGetQueryPoolResults(device, frame.queryPool, 0, 2, values, Long.BYTES,
					VK_QUERY_RESULT_64_BIT | VK_QUERY_RESULT_WAIT_BIT);
				if (query == VK_SUCCESS)
				{
					long first = maskTimestamp(values.get(0));
					long second = maskTimestamp(values.get(1));
					long delta = timestampDelta(first, second);
					frame.pending.gpuStartNs = Math.round(first * timestampPeriodNs);
					frame.pending.gpuEndNs = frame.pending.gpuStartNs + Math.round(delta * timestampPeriodNs);
				}
			}
		}
		counterValues[COUNTER_COMPLETED]++;
		timingLog.frame(frame.pending, counterValues);
		frame.pending = null;
		frame.submitted = false;
	}

	private void finishAllFrames()
	{
		if (device == null) return;
		for (FrameResources frame : frames) finishFrame(frame, true);
	}

	private long maskTimestamp(long value)
	{
		if (timestampValidBits >= 64) return value;
		return value & ((1L << timestampValidBits) - 1L);
	}

	private long timestampDelta(long start, long end)
	{
		if (end >= start) return end - start;
		if (timestampValidBits >= 64) return 0;
		return (1L << timestampValidBits) - start + end;
	}

	private VulkanTimingLog.FrameRecord submittedRecord(long frameId, int width, int height, long uiGenerateNs,
		long uiUploadNs, long encodeNs, long submitNs, long totalNs)
	{
		VulkanTimingLog.FrameRecord record = baseRecord(frameId, width, height, VulkanFrameOutcome.SUBMITTED);
		record.uiGenerateNs = uiGenerateNs;
		record.uiUploadNs = uiUploadNs;
		record.encodeNs = encodeNs;
		record.submitNs = submitNs;
		record.totalNs = Math.max(totalNs, Math.max(Math.max(uiGenerateNs, uiUploadNs), Math.max(encodeNs, submitNs)));
		return record;
	}

	private VulkanFrameOutcome failedFrame(long frameId, int width, int height, long uiGenerateNs,
		VulkanFrameOutcome outcome, String error)
	{
		VulkanTimingLog.FrameRecord record = baseRecord(frameId, width, height, outcome);
		record.uiGenerateNs = uiGenerateNs;
		record.totalNs = uiGenerateNs;
		record.error = error;
		timingLog.frame(record, counterValues);
		return outcome;
	}

	private VulkanTimingLog.FrameRecord baseRecord(long frameId, int width, int height, VulkanFrameOutcome outcome)
	{
		VulkanTimingLog.FrameRecord record = new VulkanTimingLog.FrameRecord();
		record.frameId = frameId;
		record.width = width;
		record.height = height;
		record.requested = requestedMode;
		record.effective = effectiveMode;
		record.outcome = outcome;
		return record;
	}

	private boolean runReadback(byte[] firstUiBytes, byte[] secondUiBytes)
	{
		ReadbackResources resources = null;
		try
		{
			resources = createReadbackResources();
			byte[][] uploads = {firstUiBytes, secondUiBytes};
			byte[][] results = new byte[2][];
			for (int index = 0; index < uploads.length; index++) results[index] = runReadbackPass(resources, uploads[index], 7 + index);
			int topRight = (1 * 8 + 6) * 4;
			int bottomLeft = (6 * 8 + 1) * 4;
			int center = (3 * 8 + 3) * 4;
			int animated = (6 * 8 + 6) * 4;
			return near(results[0][topRight], 26) && near(results[0][topRight + 1], 19) && near(results[0][topRight + 2], 140) &&
				near(results[0][topRight + 3], 255) && near(results[0][bottomLeft], 255) && near(results[0][bottomLeft + 1], 0) &&
				near(results[0][bottomLeft + 2], 0) && near(results[0][bottomLeft + 3], 255) &&
				unsigned(results[0][center + 1]) > 150 && unsigned(results[0][center + 2]) < 100 &&
				!java.util.Arrays.equals(java.util.Arrays.copyOfRange(results[0], animated, animated + 4),
					java.util.Arrays.copyOfRange(results[1], animated, animated + 4));
		}
		finally
		{
			destroyReadbackResources(resources);
		}
	}

	private ReadbackResources createReadbackResources()
	{
		ReadbackResources resources = new ReadbackResources();
		resources.staging = createBuffer(8 * 8 * 4, VK_BUFFER_USAGE_TRANSFER_SRC_BIT,
			VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT);
		resources.outputBuffer = createBuffer(8 * 8 * 4, VK_BUFFER_USAGE_TRANSFER_DST_BIT,
			VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT);
		resources.ui = createImage(8, 8, VK_FORMAT_B8G8R8A8_UNORM, VK_IMAGE_USAGE_TRANSFER_DST_BIT | VK_IMAGE_USAGE_SAMPLED_BIT,
			VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT, true);
		resources.output = createImage(8, 8, VK_FORMAT_B8G8R8A8_UNORM, VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT | VK_IMAGE_USAGE_TRANSFER_SRC_BIT,
			VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT, true);
		try (MemoryStack stack = MemoryStack.stackPush())
		{
			LongBuffer handle = stack.mallocLong(1);
			VkFramebufferCreateInfo framebuffer = VkFramebufferCreateInfo.calloc(stack).sType$Default()
				.renderPass(swapchain.renderPass).pAttachments(stack.longs(resources.output.view)).width(8).height(8).layers(1);
			check(vkCreateFramebuffer(device, framebuffer, null, handle), "vkCreateFramebuffer(readback)");
			resources.framebuffer = handle.get(0); trackCreate();
			VkDescriptorPoolSize.Buffer size = VkDescriptorPoolSize.calloc(1, stack);
			size.get(0).type(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER).descriptorCount(1);
			VkDescriptorPoolCreateInfo pool = VkDescriptorPoolCreateInfo.calloc(stack).sType$Default().maxSets(1).pPoolSizes(size);
			check(vkCreateDescriptorPool(device, pool, null, handle), "vkCreateDescriptorPool(readback)");
			resources.descriptorPool = handle.get(0); trackCreate();
			VkDescriptorSetAllocateInfo allocate = VkDescriptorSetAllocateInfo.calloc(stack).sType$Default()
				.descriptorPool(resources.descriptorPool).pSetLayouts(stack.longs(swapchain.descriptorLayout));
			check(vkAllocateDescriptorSets(device, allocate, handle), "vkAllocateDescriptorSets(readback)");
			resources.descriptorSet = handle.get(0); trackCreate();
			VkDescriptorImageInfo.Buffer image = VkDescriptorImageInfo.calloc(1, stack);
			image.get(0).sampler(swapchain.sampler).imageView(resources.ui.view).imageLayout(VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL);
			VkWriteDescriptorSet.Buffer write = VkWriteDescriptorSet.calloc(1, stack);
			write.get(0).sType$Default().dstSet(resources.descriptorSet).dstBinding(0)
				.descriptorCount(1).descriptorType(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER).pImageInfo(image);
			vkUpdateDescriptorSets(device, write, null);
			VkCommandBufferAllocateInfo command = VkCommandBufferAllocateInfo.calloc(stack).sType$Default()
				.commandPool(commandPool).level(VK_COMMAND_BUFFER_LEVEL_PRIMARY).commandBufferCount(1);
			PointerBuffer pointer = stack.mallocPointer(1);
			check(vkAllocateCommandBuffers(device, command, pointer), "vkAllocateCommandBuffers(readback)");
			resources.commandBuffer = new VkCommandBuffer(pointer.get(0), device); trackCreate();
		}
		return resources;
	}

	private byte[] runReadbackPass(ReadbackResources resources, byte[] uiBytes, long frameId)
	{
		upload(resources.staging, uiBytes);
		check(vkResetCommandBuffer(resources.commandBuffer, 0), "vkResetCommandBuffer(readback)");
		try (MemoryStack stack = MemoryStack.stackPush())
		{
			VkCommandBufferBeginInfo begin = VkCommandBufferBeginInfo.calloc(stack).sType$Default();
			check(vkBeginCommandBuffer(resources.commandBuffer, begin), "vkBeginCommandBuffer(readback)");
			transitionUiForUpload(resources.commandBuffer, resources.ui);
			VkBufferImageCopy.Buffer uiCopy = VkBufferImageCopy.calloc(1, stack);
			uiCopy.get(0).imageSubresource().aspectMask(VK_IMAGE_ASPECT_COLOR_BIT).layerCount(1);
			uiCopy.get(0).imageExtent().set(8, 8, 1);
			vkCmdCopyBufferToImage(resources.commandBuffer, resources.staging.buffer, resources.ui.image,
				VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, uiCopy);
			transitionUiForSampling(resources.commandBuffer, resources.ui);
			recordScene(resources.commandBuffer, swapchain.renderPass, resources.framebuffer, 8, 8, resources.descriptorSet, frameId, true);
			imageBarrier(resources.commandBuffer, resources.output.image, VK_IMAGE_LAYOUT_PRESENT_SRC_KHR,
				VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL, VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT, VK_ACCESS_TRANSFER_READ_BIT,
				VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT, VK_PIPELINE_STAGE_TRANSFER_BIT);
			VkBufferImageCopy.Buffer outputCopy = VkBufferImageCopy.calloc(1, stack);
			outputCopy.get(0).imageSubresource().aspectMask(VK_IMAGE_ASPECT_COLOR_BIT).layerCount(1);
			outputCopy.get(0).imageExtent().set(8, 8, 1);
			vkCmdCopyImageToBuffer(resources.commandBuffer, resources.output.image, VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
				resources.outputBuffer.buffer, outputCopy);
			check(vkEndCommandBuffer(resources.commandBuffer), "vkEndCommandBuffer(readback)");
			VkSubmitInfo submit = VkSubmitInfo.calloc(stack).sType$Default()
				.pCommandBuffers(stack.pointers(resources.commandBuffer.address()));
			check(vkQueueSubmit(graphicsQueue, submit, NULL), "vkQueueSubmit(readback)");
			check(vkQueueWaitIdle(graphicsQueue), "vkQueueWaitIdle(readback)");
			resources.ui.initialized = true;
			PointerBuffer pointer = stack.mallocPointer(1);
			check(vkMapMemory(device, resources.outputBuffer.memory, 0, resources.outputBuffer.size, 0, pointer), "vkMapMemory(readback)");
			byte[] result = new byte[(int) resources.outputBuffer.size];
			memByteBuffer(pointer.get(0), result.length).get(result);
			vkUnmapMemory(device, resources.outputBuffer.memory);
			return result;
		}
	}

	private void destroyReadbackResources(ReadbackResources resources)
	{
		if (resources == null || device == null) return;
		if (resources.commandBuffer != null) { vkFreeCommandBuffers(device, commandPool, resources.commandBuffer); trackRelease(); }
		if (resources.descriptorPool != NULL) { vkDestroyDescriptorPool(device, resources.descriptorPool, null); trackRelease(2); }
		if (resources.framebuffer != NULL) { vkDestroyFramebuffer(device, resources.framebuffer, null); trackRelease(); }
		destroyImage(resources.output);
		destroyImage(resources.ui);
		destroyBuffer(resources.outputBuffer);
		destroyBuffer(resources.staging);
	}

	private static boolean near(byte actual, int expected)
	{
		return Math.abs(unsigned(actual) - expected) <= 4;
	}

	private static int unsigned(byte value)
	{
		return value & 0xff;
	}

	private long maskTimestampResult(long value)
	{
		return maskTimestamp(value);
	}

	private List<String> enumerateInstanceExtensions()
	{
		try (MemoryStack stack = MemoryStack.stackPush())
		{
			IntBuffer count = stack.mallocInt(1);
			check(vkEnumerateInstanceExtensionProperties((String) null, count, null), "vkEnumerateInstanceExtensionProperties(count)");
			VkExtensionProperties.Buffer values = VkExtensionProperties.malloc(count.get(0), stack);
			check(vkEnumerateInstanceExtensionProperties((String) null, count, values), "vkEnumerateInstanceExtensionProperties");
			List<String> names = new ArrayList<>();
			for (int index = 0; index < values.capacity(); index++) names.add(values.get(index).extensionNameString());
			return names;
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
			List<String> names = new ArrayList<>();
			for (int index = 0; index < values.capacity(); index++) names.add(values.get(index).layerNameString());
			return names;
		}
	}

	private List<String> enumerateDeviceExtensions(VkPhysicalDevice candidate)
	{
		try (MemoryStack stack = MemoryStack.stackPush())
		{
			IntBuffer count = stack.mallocInt(1);
			check(vkEnumerateDeviceExtensionProperties(candidate, (String) null, count, null), "vkEnumerateDeviceExtensionProperties(count)");
			VkExtensionProperties.Buffer values = VkExtensionProperties.malloc(count.get(0), stack);
			check(vkEnumerateDeviceExtensionProperties(candidate, (String) null, count, values), "vkEnumerateDeviceExtensionProperties");
			List<String> names = new ArrayList<>();
			for (int index = 0; index < values.capacity(); index++) names.add(values.get(index).extensionNameString());
			return names;
		}
	}

	private static PointerBuffer strings(MemoryStack stack, List<String> values)
	{
		PointerBuffer pointers = stack.mallocPointer(values.size());
		for (String value : values) pointers.put(stack.UTF8(value));
		return pointers.flip();
	}

	private static void require(List<String> values, String required)
	{
		if (!values.contains(required)) throw new IllegalStateException("Missing Vulkan instance extension: " + required);
	}

	private static int vulkanMode(VulkanPresentMode mode)
	{
		if (mode == VulkanPresentMode.UNLOCKED) return VK_PRESENT_MODE_IMMEDIATE_KHR;
		if (mode == VulkanPresentMode.MAILBOX) return VK_PRESENT_MODE_MAILBOX_KHR;
		return VK_PRESENT_MODE_FIFO_KHR;
	}

	private static String colorSpaceName(int colorSpace)
	{
		return colorSpace == VK_COLOR_SPACE_SRGB_NONLINEAR_KHR ? "VK_COLOR_SPACE_SRGB_NONLINEAR_KHR" : Integer.toString(colorSpace);
	}

	private void ensureAccepting()
	{
		if (!accepting || consumed) throw new IllegalStateException("Vulkan control renderer is not running.");
	}

	private void trackCreate()
	{
		trackCreate(1);
	}

	private void trackCreate(long count)
	{
		counterValues[COUNTER_LIVE_NATIVE_OBJECTS] += count;
		counterValues[COUNTER_HIGH_WATER_NATIVE_OBJECTS] = Math.max(counterValues[COUNTER_HIGH_WATER_NATIVE_OBJECTS],
			counterValues[COUNTER_LIVE_NATIVE_OBJECTS]);
	}

	private void trackRelease()
	{
		trackRelease(1);
	}

	private void trackRelease(long count)
	{
		counterValues[COUNTER_LIVE_NATIVE_OBJECTS] = Math.max(0, counterValues[COUNTER_LIVE_NATIVE_OBJECTS] - count);
	}

	private static void check(int result, String operation)
	{
		if (result != VK_SUCCESS) throw failure(operation, result);
	}

	private static IllegalStateException failure(String operation, int result)
	{
		return new IllegalStateException(operation + " failed with Vulkan result " + result + '.');
	}

	private static final class Candidate
	{
		final int graphicsFamily;
		final int presentFamily;
		final List<String> extensions;
		final String name;
		final int timestampValidBits;
		final double timestampPeriodNs;

		Candidate(int graphicsFamily, int presentFamily, List<String> extensions, String name,
			int timestampValidBits, double timestampPeriodNs)
		{
			this.graphicsFamily = graphicsFamily;
			this.presentFamily = presentFamily;
			this.extensions = extensions;
			this.name = name;
			this.timestampValidBits = timestampValidBits;
			this.timestampPeriodNs = timestampPeriodNs;
		}
	}

	private static final class SurfaceSupport
	{
		int minImages;
		int maxImages;
		int currentWidth;
		int currentHeight;
		int minWidth;
		int minHeight;
		int maxWidth;
		int maxHeight;
		int compositeAlpha;
		int currentTransform;
		final List<SurfaceFormat> formats = new ArrayList<>();
		final List<Integer> presentModes = new ArrayList<>();
	}

	private static final class SurfaceFormat
	{
		final int format;
		final int colorSpace;
		SurfaceFormat(int format, int colorSpace) { this.format = format; this.colorSpace = colorSpace; }
	}

	private static final class FrameResources
	{
		VkCommandBuffer commandBuffer;
		long acquireSemaphore;
		long fence;
		long queryPool;
		BufferResource staging;
		ImageResource ui;
		long descriptorSet;
		boolean submitted;
		int imageIndex;
		VulkanTimingLog.FrameRecord pending;
	}

	private static final class SwapchainData
	{
		long handle;
		int requestedWidth;
		int requestedHeight;
		int width;
		int height;
		int format;
		int colorSpace;
		int requestedImages;
		VulkanPresentMode presentMode;
		long[] images = new long[0];
		long[] imageViews = new long[0];
		long[] framebuffers = new long[0];
		long[] renderFinishedSemaphores = new long[0];
		long[] imageFences = new long[0];
		long renderPass;
		long descriptorLayout;
		long pipelineLayout;
		long trianglePipeline;
		long uiPipeline;
		long sampler;
		long descriptorPool;
		int descriptorSetCount;
	}

	private static final class BufferResource
	{
		long buffer;
		long memory;
		long size;
	}

	private static final class ImageResource
	{
		long image;
		long memory;
		long view;
		int width;
		int height;
		int format;
		boolean initialized;
	}

	private static final class ReadbackResources
	{
		BufferResource staging;
		BufferResource outputBuffer;
		ImageResource ui;
		ImageResource output;
		long framebuffer;
		long descriptorPool;
		long descriptorSet;
		VkCommandBuffer commandBuffer;
	}
}
