package rs117.hd.spikes.vulkan.offscreen;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkBufferCreateInfo;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkCommandBufferAllocateInfo;
import org.lwjgl.vulkan.VkCommandBufferBeginInfo;
import org.lwjgl.vulkan.VkCommandPoolCreateInfo;
import org.lwjgl.vulkan.VkImageCreateInfo;
import org.lwjgl.vulkan.VkImageViewCreateInfo;
import org.lwjgl.vulkan.VkMemoryAllocateInfo;
import org.lwjgl.vulkan.VkMemoryRequirements;
import org.lwjgl.vulkan.VkPhysicalDeviceMemoryProperties;
import org.lwjgl.vulkan.VkSubmitInfo;

import static org.lwjgl.system.MemoryUtil.memByteBuffer;
import static org.lwjgl.vulkan.VK10.*;

public final class VulkanOffscreenResources implements AutoCloseable
{
	private final VulkanOffscreenDevice context;
	private final List<Resource> resources = new ArrayList<>();
	private long commandPool;
	private int liveHandles;
	private boolean closed;

	private VulkanOffscreenResources(VulkanOffscreenDevice context)
	{
		this.context = context;
	}

	public static VulkanOffscreenResources open(VulkanOffscreenDevice context)
	{
		if (context == null) throw new NullPointerException("context");
		VulkanOffscreenResources result = new VulkanOffscreenResources(context);
		try
		{
			result.createCommandPool();
			return result;
		}
		catch (RuntimeException | Error failure)
		{
			try { result.close(); }
			catch (RuntimeException | Error cleanup) { failure.addSuppressed(cleanup); }
			throw failure;
		}
	}

	public int liveHandleCount()
	{
		return liveHandles;
	}

	Buffer createBuffer(long size, int usage, int memoryProperties)
	{
		ensureOpen();
		if (size <= 0) throw new IllegalArgumentException("Buffer size must be positive.");
		Buffer resource = new Buffer(size);
		boolean complete = false;
		try (MemoryStack stack = MemoryStack.stackPush())
		{
			VkBufferCreateInfo info = VkBufferCreateInfo.calloc(stack).sType$Default()
				.size(size).usage(usage).sharingMode(VK_SHARING_MODE_EXCLUSIVE);
			LongBuffer handle = stack.mallocLong(1);
			check(vkCreateBuffer(context.device(), info, null, handle), "vkCreateBuffer");
			resource.buffer = handle.get(0);
			liveHandles++;
			VkMemoryRequirements requirements = VkMemoryRequirements.malloc(stack);
			vkGetBufferMemoryRequirements(context.device(), resource.buffer, requirements);
			VkMemoryAllocateInfo allocate = VkMemoryAllocateInfo.calloc(stack).sType$Default()
				.allocationSize(requirements.size())
				.memoryTypeIndex(findMemoryType(requirements.memoryTypeBits(), memoryProperties));
			check(vkAllocateMemory(context.device(), allocate, null, handle), "vkAllocateMemory(buffer)");
			resource.memory = handle.get(0);
			liveHandles++;
			check(vkBindBufferMemory(context.device(), resource.buffer, resource.memory, 0), "vkBindBufferMemory");
			resources.add(resource);
			complete = true;
			return resource;
		}
		finally
		{
			if (!complete) resource.close();
		}
	}

	Image createImage(int width, int height, int format, int usage, int aspectMask)
	{
		ensureOpen();
		if (width <= 0 || height <= 0) throw new IllegalArgumentException("Image extent must be positive.");
		Image resource = new Image(width, height, format);
		boolean complete = false;
		try (MemoryStack stack = MemoryStack.stackPush())
		{
			VkImageCreateInfo info = VkImageCreateInfo.calloc(stack).sType$Default()
				.imageType(VK_IMAGE_TYPE_2D).format(format);
			info.extent().set(width, height, 1);
			info.mipLevels(1).arrayLayers(1).samples(VK_SAMPLE_COUNT_1_BIT)
				.tiling(VK_IMAGE_TILING_OPTIMAL).usage(usage)
				.sharingMode(VK_SHARING_MODE_EXCLUSIVE).initialLayout(VK_IMAGE_LAYOUT_UNDEFINED);
			LongBuffer handle = stack.mallocLong(1);
			check(vkCreateImage(context.device(), info, null, handle), "vkCreateImage");
			resource.image = handle.get(0);
			liveHandles++;
			VkMemoryRequirements requirements = VkMemoryRequirements.malloc(stack);
			vkGetImageMemoryRequirements(context.device(), resource.image, requirements);
			VkMemoryAllocateInfo allocate = VkMemoryAllocateInfo.calloc(stack).sType$Default()
				.allocationSize(requirements.size())
				.memoryTypeIndex(findMemoryType(requirements.memoryTypeBits(), VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT));
			check(vkAllocateMemory(context.device(), allocate, null, handle), "vkAllocateMemory(image)");
			resource.memory = handle.get(0);
			liveHandles++;
			check(vkBindImageMemory(context.device(), resource.image, resource.memory, 0), "vkBindImageMemory");
			VkImageViewCreateInfo view = VkImageViewCreateInfo.calloc(stack).sType$Default()
				.image(resource.image).viewType(VK_IMAGE_VIEW_TYPE_2D).format(format);
			view.subresourceRange().aspectMask(aspectMask).baseMipLevel(0).levelCount(1)
				.baseArrayLayer(0).layerCount(1);
			check(vkCreateImageView(context.device(), view, null, handle), "vkCreateImageView");
			resource.view = handle.get(0);
			liveHandles++;
			resources.add(resource);
			complete = true;
			return resource;
		}
		finally
		{
			if (!complete) resource.close();
		}
	}

	void submitAndWait(Consumer<VkCommandBuffer> recorder)
	{
		ensureOpen();
		if (recorder == null) throw new NullPointerException("recorder");
		VkCommandBuffer commandBuffer = null;
		try (MemoryStack stack = MemoryStack.stackPush())
		{
			VkCommandBufferAllocateInfo allocate = VkCommandBufferAllocateInfo.calloc(stack).sType$Default()
				.commandPool(commandPool).level(VK_COMMAND_BUFFER_LEVEL_PRIMARY).commandBufferCount(1);
			PointerBuffer pointer = stack.mallocPointer(1);
			check(vkAllocateCommandBuffers(context.device(), allocate, pointer), "vkAllocateCommandBuffers");
			commandBuffer = new VkCommandBuffer(pointer.get(0), context.device());
			VkCommandBufferBeginInfo begin = VkCommandBufferBeginInfo.calloc(stack).sType$Default()
				.flags(VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT);
			check(vkBeginCommandBuffer(commandBuffer, begin), "vkBeginCommandBuffer");
			recorder.accept(commandBuffer);
			check(vkEndCommandBuffer(commandBuffer), "vkEndCommandBuffer");
			VkSubmitInfo.Buffer submit = VkSubmitInfo.calloc(1, stack);
			submit.get(0).sType$Default().pCommandBuffers(stack.pointers(commandBuffer.address()));
			check(vkQueueSubmit(context.graphicsQueue(), submit, VK_NULL_HANDLE), "vkQueueSubmit");
			check(vkQueueWaitIdle(context.graphicsQueue()), "vkQueueWaitIdle");
		}
		finally
		{
			if (commandBuffer != null && commandPool != VK_NULL_HANDLE)
				vkFreeCommandBuffers(context.device(), commandPool, commandBuffer);
		}
	}

	@Override
	public void close()
	{
		if (closed) return;
		closed = true;
		for (Resource resource : new ArrayList<>(resources)) resource.close();
		if (commandPool != VK_NULL_HANDLE)
		{
			vkDestroyCommandPool(context.device(), commandPool, null);
			commandPool = VK_NULL_HANDLE;
			liveHandles--;
		}
		if (liveHandles != 0)
			throw new IllegalStateException("Offscreen Vulkan resource accounting is unbalanced: " + liveHandles);
	}

	static int selectMemoryType(int typeBits, int[] propertyFlags, int requiredProperties)
	{
		if (propertyFlags == null) throw new NullPointerException("propertyFlags");
		for (int index = 0; index < propertyFlags.length; index++)
			if ((typeBits & (1 << index)) != 0 &&
				(propertyFlags[index] & requiredProperties) == requiredProperties) return index;
		return -1;
	}

	private void createCommandPool()
	{
		try (MemoryStack stack = MemoryStack.stackPush())
		{
			VkCommandPoolCreateInfo info = VkCommandPoolCreateInfo.calloc(stack).sType$Default()
				.flags(VK_COMMAND_POOL_CREATE_TRANSIENT_BIT).queueFamilyIndex(context.graphicsQueueFamily());
			LongBuffer handle = stack.mallocLong(1);
			check(vkCreateCommandPool(context.device(), info, null, handle), "vkCreateCommandPool");
			commandPool = handle.get(0);
			liveHandles++;
		}
	}

	private int findMemoryType(int typeBits, int requiredProperties)
	{
		try (MemoryStack stack = MemoryStack.stackPush())
		{
			VkPhysicalDeviceMemoryProperties properties = VkPhysicalDeviceMemoryProperties.malloc(stack);
			vkGetPhysicalDeviceMemoryProperties(context.physicalDevice(), properties);
			int[] flags = new int[properties.memoryTypeCount()];
			for (int index = 0; index < flags.length; index++) flags[index] = properties.memoryTypes(index).propertyFlags();
			int selected = selectMemoryType(typeBits, flags, requiredProperties);
			if (selected >= 0) return selected;
		}
		throw new IllegalStateException("No compatible Vulkan memory type.");
	}

	private void ensureOpen()
	{
		if (closed || commandPool == VK_NULL_HANDLE) throw new IllegalStateException("Offscreen Vulkan resources are closed.");
	}

	private static void check(int result, String operation)
	{
		if (result != VK_SUCCESS) throw new IllegalStateException(operation + " failed with VkResult " + result);
	}

	abstract class Resource implements AutoCloseable
	{
		boolean released;
		@Override public abstract void close();

		final void released()
		{
			if (!released)
			{
				released = true;
				resources.remove(this);
			}
		}
	}

	final class Buffer extends Resource
	{
		private final long size;
		private long buffer;
		private long memory;

		Buffer(long size) { this.size = size; }
		long handle() { return buffer; }
		long size() { return size; }

		void upload(ByteBuffer source)
		{
			if (released) throw new IllegalStateException("Buffer is closed.");
			ByteBuffer bytes = source.duplicate();
			if (bytes.remaining() > size) throw new IllegalArgumentException("Upload exceeds the Vulkan buffer.");
			try (MemoryStack stack = MemoryStack.stackPush())
			{
				PointerBuffer pointer = stack.mallocPointer(1);
				check(vkMapMemory(context.device(), memory, 0, bytes.remaining(), 0, pointer), "vkMapMemory");
				try { memByteBuffer(pointer.get(0), bytes.remaining()).put(bytes); }
				finally { vkUnmapMemory(context.device(), memory); }
			}
		}

		byte[] readBytes()
		{
			if (released) throw new IllegalStateException("Buffer is closed.");
			if (size > Integer.MAX_VALUE) throw new IllegalStateException("Buffer is too large for a Java byte array.");
			try (MemoryStack stack = MemoryStack.stackPush())
			{
				PointerBuffer pointer = stack.mallocPointer(1);
				check(vkMapMemory(context.device(), memory, 0, size, 0, pointer), "vkMapMemory(readback)");
				try
				{
					byte[] result = new byte[(int) size];
					memByteBuffer(pointer.get(0), result.length).get(result);
					return result;
				}
				finally { vkUnmapMemory(context.device(), memory); }
			}
		}

		@Override
		public void close()
		{
			if (released) return;
			if (buffer != VK_NULL_HANDLE) { vkDestroyBuffer(context.device(), buffer, null); buffer = VK_NULL_HANDLE; liveHandles--; }
			if (memory != VK_NULL_HANDLE) { vkFreeMemory(context.device(), memory, null); memory = VK_NULL_HANDLE; liveHandles--; }
			released();
		}
	}

	final class Image extends Resource
	{
		private final int width;
		private final int height;
		private final int format;
		private long image;
		private long memory;
		private long view;

		Image(int width, int height, int format)
		{
			this.width = width;
			this.height = height;
			this.format = format;
		}
		long handle() { return image; }
		long view() { return view; }
		int width() { return width; }
		int height() { return height; }
		int format() { return format; }

		@Override
		public void close()
		{
			if (released) return;
			if (view != VK_NULL_HANDLE) { vkDestroyImageView(context.device(), view, null); view = VK_NULL_HANDLE; liveHandles--; }
			if (image != VK_NULL_HANDLE) { vkDestroyImage(context.device(), image, null); image = VK_NULL_HANDLE; liveHandles--; }
			if (memory != VK_NULL_HANDLE) { vkFreeMemory(context.device(), memory, null); memory = VK_NULL_HANDLE; liveHandles--; }
			released();
		}
	}
}
