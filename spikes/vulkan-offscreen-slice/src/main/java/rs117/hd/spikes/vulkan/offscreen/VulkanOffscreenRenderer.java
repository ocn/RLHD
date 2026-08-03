package rs117.hd.spikes.vulkan.offscreen;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.LongBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkAttachmentDescription;
import org.lwjgl.vulkan.VkAttachmentReference;
import org.lwjgl.vulkan.VkBufferImageCopy;
import org.lwjgl.vulkan.VkBufferMemoryBarrier;
import org.lwjgl.vulkan.VkClearValue;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkDescriptorBufferInfo;
import org.lwjgl.vulkan.VkDescriptorImageInfo;
import org.lwjgl.vulkan.VkDescriptorPoolCreateInfo;
import org.lwjgl.vulkan.VkDescriptorPoolSize;
import org.lwjgl.vulkan.VkDescriptorSetAllocateInfo;
import org.lwjgl.vulkan.VkDescriptorSetLayoutBinding;
import org.lwjgl.vulkan.VkDescriptorSetLayoutCreateInfo;
import org.lwjgl.vulkan.VkFramebufferCreateInfo;
import org.lwjgl.vulkan.VkGraphicsPipelineCreateInfo;
import org.lwjgl.vulkan.VkImageMemoryBarrier;
import org.lwjgl.vulkan.VkPipelineColorBlendAttachmentState;
import org.lwjgl.vulkan.VkPipelineColorBlendStateCreateInfo;
import org.lwjgl.vulkan.VkPipelineDepthStencilStateCreateInfo;
import org.lwjgl.vulkan.VkPipelineDynamicStateCreateInfo;
import org.lwjgl.vulkan.VkPipelineInputAssemblyStateCreateInfo;
import org.lwjgl.vulkan.VkPipelineLayoutCreateInfo;
import org.lwjgl.vulkan.VkPipelineMultisampleStateCreateInfo;
import org.lwjgl.vulkan.VkPipelineRasterizationStateCreateInfo;
import org.lwjgl.vulkan.VkPipelineShaderStageCreateInfo;
import org.lwjgl.vulkan.VkPipelineVertexInputStateCreateInfo;
import org.lwjgl.vulkan.VkPipelineViewportStateCreateInfo;
import org.lwjgl.vulkan.VkPushConstantRange;
import org.lwjgl.vulkan.VkRect2D;
import org.lwjgl.vulkan.VkRenderPassBeginInfo;
import org.lwjgl.vulkan.VkRenderPassCreateInfo;
import org.lwjgl.vulkan.VkSamplerCreateInfo;
import org.lwjgl.vulkan.VkShaderModuleCreateInfo;
import org.lwjgl.vulkan.VkSubpassDependency;
import org.lwjgl.vulkan.VkSubpassDescription;
import org.lwjgl.vulkan.VkVertexInputAttributeDescription;
import org.lwjgl.vulkan.VkVertexInputBindingDescription;
import org.lwjgl.vulkan.VkViewport;
import org.lwjgl.vulkan.VkWriteDescriptorSet;
import rs117.hd.renderer.PreparedFrame;
import rs117.hd.renderer.PreparedUiTexture;
import rs117.hd.spikes.vulkan.opaque.VulkanOpaqueZoneContract;

import static org.lwjgl.vulkan.VK10.*;

public final class VulkanOffscreenRenderer implements AutoCloseable
{
	private static final int COLOR_FORMAT = VK_FORMAT_B8G8R8A8_UNORM;
	private static final int DEPTH_FORMAT = VK_FORMAT_D32_SFLOAT;

	private VulkanOffscreenDevice context;
	private VulkanOffscreenResources resources;
	private long renderPass;
	private long opaqueDescriptorLayout;
	private long uiDescriptorLayout;
	private long opaquePipelineLayout;
	private long uiPipelineLayout;
	private long opaquePipeline;
	private long uiPipeline;
	private long sampler;
	private int liveHandles;
	private boolean closed;
	private boolean finalValidationEnabled;
	private int finalValidationWarnings;
	private int finalValidationErrors;

	private VulkanOffscreenRenderer() {}

	public static VulkanOffscreenRenderer open()
	{
		VulkanOffscreenRenderer result = new VulkanOffscreenRenderer();
		try
		{
			result.context = VulkanOffscreenDevice.open();
			result.resources = VulkanOffscreenResources.open(result.context);
			result.createRenderPass();
			result.createDescriptorLayouts();
			result.createPipelineLayouts();
			result.createSampler();
			result.opaquePipeline = result.createPipeline("opaque.vert.spv", "opaque.frag.spv", false);
			result.uiPipeline = result.createPipeline("ui.vert.spv", "ui.frag.spv", true);
			return result;
		}
		catch (RuntimeException | Error failure)
		{
			try { result.close(); }
			catch (RuntimeException | Error cleanup) { failure.addSuppressed(cleanup); }
			throw failure;
		}
	}

	public Result render(VulkanOpaqueZoneContract.PreparedUpload upload, PreparedFrame frame)
	{
		ensureOpen();
		if (upload == null || frame == null) throw new NullPointerException("upload and frame are required");
		int width = frame.viewport().width();
		int height = frame.viewport().height();
		if (width <= 0 || height <= 0) throw new IllegalArgumentException("Offscreen extent must be positive.");
		if (frame.ui().width() <= 0 || frame.ui().height() <= 0)
			throw new IllegalArgumentException("UI texture extent must be positive.");

		try (Call call = new Call(width, height, upload, frame.ui()))
		{
			resources.submitAndWait(command -> record(command, call, upload, frame));
			return new Result(width, height, call.readback.readBytes(), upload.opaqueVertexCount());
		}
	}

	public String physicalDeviceName()
	{
		ensureOpen();
		return context.physicalDeviceName();
	}

	public int liveHandleCount()
	{
		return liveHandles + (resources == null ? 0 : resources.liveHandleCount()) +
			(context == null ? 0 : context.liveHandleCount());
	}

	public boolean validationEnabled()
	{
		return context == null ? finalValidationEnabled : context.validationEnabled();
	}

	public int validationWarningCount() { return context == null ? finalValidationWarnings : context.validationWarningCount(); }
	public int validationErrorCount() { return context == null ? finalValidationErrors : context.validationErrorCount(); }

	static ByteBuffer tightlyPackedUi(PreparedUiTexture ui)
	{
		if (ui == null) throw new NullPointerException("ui");
		int tightStride = Math.multiplyExact(ui.width(), 4);
		ByteBuffer source = ui.pixels();
		ByteBuffer packed = ByteBuffer.allocate(Math.multiplyExact(tightStride, ui.height()));
		for (int row = 0; row < ui.height(); row++)
		{
			ByteBuffer rowBytes = source.duplicate();
			rowBytes.position(Math.multiplyExact(row, ui.rowStride()));
			rowBytes.limit(rowBytes.position() + tightStride);
			packed.put(rowBytes);
		}
		return packed.flip().asReadOnlyBuffer();
	}

	@Override
	public void close()
	{
		if (closed) return;
		closed = true;
		int idleResult = context == null ? VK_SUCCESS : vkDeviceWaitIdle(context.device());
		if (context != null)
		{
			if (uiPipeline != VK_NULL_HANDLE) { vkDestroyPipeline(context.device(), uiPipeline, null); uiPipeline = VK_NULL_HANDLE; liveHandles--; }
			if (opaquePipeline != VK_NULL_HANDLE) { vkDestroyPipeline(context.device(), opaquePipeline, null); opaquePipeline = VK_NULL_HANDLE; liveHandles--; }
			if (sampler != VK_NULL_HANDLE) { vkDestroySampler(context.device(), sampler, null); sampler = VK_NULL_HANDLE; liveHandles--; }
			if (uiPipelineLayout != VK_NULL_HANDLE) { vkDestroyPipelineLayout(context.device(), uiPipelineLayout, null); uiPipelineLayout = VK_NULL_HANDLE; liveHandles--; }
			if (opaquePipelineLayout != VK_NULL_HANDLE) { vkDestroyPipelineLayout(context.device(), opaquePipelineLayout, null); opaquePipelineLayout = VK_NULL_HANDLE; liveHandles--; }
			if (uiDescriptorLayout != VK_NULL_HANDLE) { vkDestroyDescriptorSetLayout(context.device(), uiDescriptorLayout, null); uiDescriptorLayout = VK_NULL_HANDLE; liveHandles--; }
			if (opaqueDescriptorLayout != VK_NULL_HANDLE) { vkDestroyDescriptorSetLayout(context.device(), opaqueDescriptorLayout, null); opaqueDescriptorLayout = VK_NULL_HANDLE; liveHandles--; }
			if (renderPass != VK_NULL_HANDLE) { vkDestroyRenderPass(context.device(), renderPass, null); renderPass = VK_NULL_HANDLE; liveHandles--; }
		}
		if (resources != null) { resources.close(); resources = null; }
		if (context != null)
		{
			finalValidationEnabled = context.validationEnabled();
			context.close();
			finalValidationWarnings = context.validationWarningCount();
			finalValidationErrors = context.validationErrorCount();
			context = null;
		}
		if (liveHandles != 0) throw new IllegalStateException("Offscreen renderer handle accounting is unbalanced: " + liveHandles);
		check(idleResult, "vkDeviceWaitIdle");
	}

	private void record(VkCommandBuffer command, Call call, VulkanOpaqueZoneContract.PreparedUpload upload, PreparedFrame frame)
	{
		transitionImage(command, call.ui.handle(), VK_IMAGE_LAYOUT_UNDEFINED, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
			0, VK_ACCESS_TRANSFER_WRITE_BIT, VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT, VK_PIPELINE_STAGE_TRANSFER_BIT);
		try (MemoryStack stack = MemoryStack.stackPush())
		{
			VkBufferImageCopy.Buffer uiCopy = VkBufferImageCopy.calloc(1, stack);
			uiCopy.get(0).bufferRowLength(0).bufferImageHeight(0);
			uiCopy.get(0).imageSubresource().aspectMask(VK_IMAGE_ASPECT_COLOR_BIT).layerCount(1);
			uiCopy.get(0).imageExtent().set(frame.ui().width(), frame.ui().height(), 1);
			vkCmdCopyBufferToImage(command, call.uiStaging.handle(), call.ui.handle(),
				VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, uiCopy);
		}
		transitionImage(command, call.ui.handle(), VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL,
			VK_ACCESS_TRANSFER_WRITE_BIT, VK_ACCESS_SHADER_READ_BIT, VK_PIPELINE_STAGE_TRANSFER_BIT,
			VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT);

		try (MemoryStack stack = MemoryStack.stackPush())
		{
			VkClearValue.Buffer clear = VkClearValue.calloc(2, stack);
			clear.get(0).color().float32(0, 0f).float32(1, 0f).float32(2, 0f).float32(3, 1f);
			clear.get(1).depthStencil().set(0f, 0);
			VkRenderPassBeginInfo begin = VkRenderPassBeginInfo.calloc(stack).sType$Default()
				.renderPass(renderPass).framebuffer(call.framebuffer).pClearValues(clear);
			begin.renderArea().offset().set(0, 0);
			begin.renderArea().extent().set(call.width, call.height);
			vkCmdBeginRenderPass(command, begin, VK_SUBPASS_CONTENTS_INLINE);

			VkViewport.Buffer viewport = VkViewport.calloc(1, stack);
			viewport.get(0).x(0).y(call.height).width(call.width).height(-call.height).minDepth(0).maxDepth(1);
			VkRect2D.Buffer scissor = VkRect2D.calloc(1, stack);
			scissor.get(0).offset().set(0, 0);
			scissor.get(0).extent().set(call.width, call.height);
			vkCmdSetViewport(command, 0, viewport);
			vkCmdSetScissor(command, 0, scissor);

			vkCmdBindPipeline(command, VK_PIPELINE_BIND_POINT_GRAPHICS, opaquePipeline);
			vkCmdBindDescriptorSets(command, VK_PIPELINE_BIND_POINT_GRAPHICS, opaquePipelineLayout, 0,
				stack.longs(call.opaqueDescriptorSet), null);
			vkCmdBindVertexBuffers(command, 0, stack.longs(call.vertices.handle()), stack.longs(0));
			ByteBuffer push = stack.malloc(VulkanOpaqueZoneContract.manifest().pushConstantSize()).order(ByteOrder.nativeOrder());
			for (float value : frame.camera().clipFromWorld()) push.putFloat(value);
			push.putInt(frame.sceneBaseX()).putInt(frame.sceneBaseZ()).flip();
			vkCmdPushConstants(command, opaquePipelineLayout, VK_SHADER_STAGE_VERTEX_BIT, 0, push);
			vkCmdDraw(command, upload.opaqueVertexCount(), 1, 0, 0);

			vkCmdBindPipeline(command, VK_PIPELINE_BIND_POINT_GRAPHICS, uiPipeline);
			vkCmdBindDescriptorSets(command, VK_PIPELINE_BIND_POINT_GRAPHICS, uiPipelineLayout, 0,
				stack.longs(call.uiDescriptorSet), null);
			vkCmdDraw(command, 6, 1, 0, 0);
			vkCmdEndRenderPass(command);

			VkBufferImageCopy.Buffer copy = VkBufferImageCopy.calloc(1, stack);
			copy.get(0).imageSubresource().aspectMask(VK_IMAGE_ASPECT_COLOR_BIT).layerCount(1);
			copy.get(0).imageExtent().set(call.width, call.height, 1);
			vkCmdCopyImageToBuffer(command, call.color.handle(), VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
				call.readback.handle(), copy);
			VkBufferMemoryBarrier.Buffer barrier = VkBufferMemoryBarrier.calloc(1, stack);
			barrier.get(0).sType$Default().srcAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT).dstAccessMask(VK_ACCESS_HOST_READ_BIT)
				.srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED).dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
				.buffer(call.readback.handle()).offset(0).size(call.readback.size());
			vkCmdPipelineBarrier(command, VK_PIPELINE_STAGE_TRANSFER_BIT, VK_PIPELINE_STAGE_HOST_BIT, 0,
				null, barrier, null);
		}
	}

	private void createRenderPass()
	{
		try (MemoryStack stack = MemoryStack.stackPush())
		{
			VkAttachmentDescription.Buffer attachments = VkAttachmentDescription.calloc(2, stack);
			attachments.get(0).format(COLOR_FORMAT).samples(VK_SAMPLE_COUNT_1_BIT)
				.loadOp(VK_ATTACHMENT_LOAD_OP_CLEAR).storeOp(VK_ATTACHMENT_STORE_OP_STORE)
				.stencilLoadOp(VK_ATTACHMENT_LOAD_OP_DONT_CARE).stencilStoreOp(VK_ATTACHMENT_STORE_OP_DONT_CARE)
				.initialLayout(VK_IMAGE_LAYOUT_UNDEFINED).finalLayout(VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL);
			attachments.get(1).format(DEPTH_FORMAT).samples(VK_SAMPLE_COUNT_1_BIT)
				.loadOp(VK_ATTACHMENT_LOAD_OP_CLEAR).storeOp(VK_ATTACHMENT_STORE_OP_DONT_CARE)
				.stencilLoadOp(VK_ATTACHMENT_LOAD_OP_DONT_CARE).stencilStoreOp(VK_ATTACHMENT_STORE_OP_DONT_CARE)
				.initialLayout(VK_IMAGE_LAYOUT_UNDEFINED).finalLayout(VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL);
			VkAttachmentReference.Buffer color = VkAttachmentReference.calloc(1, stack);
			color.get(0).attachment(0).layout(VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL);
			VkAttachmentReference depth = VkAttachmentReference.calloc(stack).attachment(1)
				.layout(VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL);
			VkSubpassDescription.Buffer subpass = VkSubpassDescription.calloc(1, stack);
			subpass.get(0).pipelineBindPoint(VK_PIPELINE_BIND_POINT_GRAPHICS).colorAttachmentCount(1)
				.pColorAttachments(color).pDepthStencilAttachment(depth);
			VkSubpassDependency.Buffer dependencies = VkSubpassDependency.calloc(2, stack);
			dependencies.get(0).srcSubpass(VK_SUBPASS_EXTERNAL).dstSubpass(0)
				.srcStageMask(VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT)
				.dstStageMask(VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT | VK_PIPELINE_STAGE_EARLY_FRAGMENT_TESTS_BIT)
				.dstAccessMask(VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT | VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT);
			dependencies.get(1).srcSubpass(0).dstSubpass(VK_SUBPASS_EXTERNAL)
				.srcStageMask(VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT).dstStageMask(VK_PIPELINE_STAGE_TRANSFER_BIT)
				.srcAccessMask(VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT).dstAccessMask(VK_ACCESS_TRANSFER_READ_BIT);
			VkRenderPassCreateInfo info = VkRenderPassCreateInfo.calloc(stack).sType$Default()
				.pAttachments(attachments).pSubpasses(subpass).pDependencies(dependencies);
			LongBuffer handle = stack.mallocLong(1);
			check(vkCreateRenderPass(context.device(), info, null, handle), "vkCreateRenderPass");
			renderPass = handle.get(0); liveHandles++;
		}
	}

	private void createDescriptorLayouts()
	{
		try (MemoryStack stack = MemoryStack.stackPush())
		{
			LongBuffer handle = stack.mallocLong(1);
			VkDescriptorSetLayoutBinding.Buffer opaque = VkDescriptorSetLayoutBinding.calloc(1, stack);
			opaque.get(0).binding(0).descriptorType(VK_DESCRIPTOR_TYPE_STORAGE_BUFFER).descriptorCount(1)
				.stageFlags(VK_SHADER_STAGE_VERTEX_BIT);
			VkDescriptorSetLayoutCreateInfo info = VkDescriptorSetLayoutCreateInfo.calloc(stack).sType$Default().pBindings(opaque);
			check(vkCreateDescriptorSetLayout(context.device(), info, null, handle), "vkCreateDescriptorSetLayout(opaque)");
			opaqueDescriptorLayout = handle.get(0); liveHandles++;
			VkDescriptorSetLayoutBinding.Buffer ui = VkDescriptorSetLayoutBinding.calloc(1, stack);
			ui.get(0).binding(0).descriptorType(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER).descriptorCount(1)
				.stageFlags(VK_SHADER_STAGE_FRAGMENT_BIT);
			info.pBindings(ui);
			check(vkCreateDescriptorSetLayout(context.device(), info, null, handle), "vkCreateDescriptorSetLayout(ui)");
			uiDescriptorLayout = handle.get(0); liveHandles++;
		}
	}

	private void createPipelineLayouts()
	{
		try (MemoryStack stack = MemoryStack.stackPush())
		{
			LongBuffer handle = stack.mallocLong(1);
			VkPushConstantRange.Buffer push = VkPushConstantRange.calloc(1, stack);
			push.get(0).stageFlags(VK_SHADER_STAGE_VERTEX_BIT).offset(0)
				.size(VulkanOpaqueZoneContract.manifest().pushConstantSize());
			VkPipelineLayoutCreateInfo info = VkPipelineLayoutCreateInfo.calloc(stack).sType$Default()
				.pSetLayouts(stack.longs(opaqueDescriptorLayout)).pPushConstantRanges(push);
			check(vkCreatePipelineLayout(context.device(), info, null, handle), "vkCreatePipelineLayout(opaque)");
			opaquePipelineLayout = handle.get(0); liveHandles++;
			info.pSetLayouts(stack.longs(uiDescriptorLayout)).pPushConstantRanges(null);
			check(vkCreatePipelineLayout(context.device(), info, null, handle), "vkCreatePipelineLayout(ui)");
			uiPipelineLayout = handle.get(0); liveHandles++;
		}
	}

	private void createSampler()
	{
		try (MemoryStack stack = MemoryStack.stackPush())
		{
			VkSamplerCreateInfo info = VkSamplerCreateInfo.calloc(stack).sType$Default()
				.magFilter(VK_FILTER_NEAREST).minFilter(VK_FILTER_NEAREST).mipmapMode(VK_SAMPLER_MIPMAP_MODE_NEAREST)
				.addressModeU(VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE).addressModeV(VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE)
				.addressModeW(VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE).maxLod(0);
			LongBuffer handle = stack.mallocLong(1);
			check(vkCreateSampler(context.device(), info, null, handle), "vkCreateSampler");
			sampler = handle.get(0); liveHandles++;
		}
	}

	private long createPipeline(String vertexName, String fragmentName, boolean ui)
	{
		long vertex = VK_NULL_HANDLE;
		long fragment = VK_NULL_HANDLE;
		try (MemoryStack stack = MemoryStack.stackPush())
		{
			vertex = createShaderModule(vertexName);
			fragment = createShaderModule(fragmentName);
			ByteBuffer main = stack.UTF8("main");
			VkPipelineShaderStageCreateInfo.Buffer stages = VkPipelineShaderStageCreateInfo.calloc(2, stack);
			stages.get(0).sType$Default().stage(VK_SHADER_STAGE_VERTEX_BIT).module(vertex).pName(main);
			stages.get(1).sType$Default().stage(VK_SHADER_STAGE_FRAGMENT_BIT).module(fragment).pName(main);
			VkPipelineVertexInputStateCreateInfo vertexInput = VkPipelineVertexInputStateCreateInfo.calloc(stack).sType$Default();
			if (!ui)
			{
				VkVertexInputBindingDescription.Buffer binding = VkVertexInputBindingDescription.calloc(1, stack);
				binding.get(0).binding(0).stride(28).inputRate(VK_VERTEX_INPUT_RATE_VERTEX);
				VkVertexInputAttributeDescription.Buffer attributes = VkVertexInputAttributeDescription.calloc(4, stack);
				attributes.get(0).location(0).binding(0).format(VK_FORMAT_R16G16B16A16_SINT).offset(0);
				attributes.get(1).location(1).binding(0).format(VK_FORMAT_R16G16B16A16_SFLOAT).offset(8);
				attributes.get(2).location(2).binding(0).format(VK_FORMAT_R16G16B16A16_SINT).offset(16);
				attributes.get(3).location(3).binding(0).format(VK_FORMAT_R32_SINT).offset(24);
				vertexInput.pVertexBindingDescriptions(binding).pVertexAttributeDescriptions(attributes);
			}
			VkPipelineInputAssemblyStateCreateInfo assembly = VkPipelineInputAssemblyStateCreateInfo.calloc(stack).sType$Default()
				.topology(VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST);
			VkPipelineViewportStateCreateInfo viewport = VkPipelineViewportStateCreateInfo.calloc(stack).sType$Default()
				.viewportCount(1).scissorCount(1);
			VkPipelineRasterizationStateCreateInfo raster = VkPipelineRasterizationStateCreateInfo.calloc(stack).sType$Default()
				.polygonMode(VK_POLYGON_MODE_FILL).cullMode(ui ? VK_CULL_MODE_NONE : VK_CULL_MODE_BACK_BIT)
				.frontFace(ui ? VK_FRONT_FACE_COUNTER_CLOCKWISE : VK_FRONT_FACE_CLOCKWISE).lineWidth(1);
			VkPipelineMultisampleStateCreateInfo multisample = VkPipelineMultisampleStateCreateInfo.calloc(stack).sType$Default()
				.rasterizationSamples(VK_SAMPLE_COUNT_1_BIT);
			VkPipelineDepthStencilStateCreateInfo depth = VkPipelineDepthStencilStateCreateInfo.calloc(stack).sType$Default()
				.depthTestEnable(!ui).depthWriteEnable(!ui).depthCompareOp(VK_COMPARE_OP_GREATER_OR_EQUAL);
			VkPipelineColorBlendAttachmentState.Buffer blendAttachment = VkPipelineColorBlendAttachmentState.calloc(1, stack);
			blendAttachment.get(0).colorWriteMask(VK_COLOR_COMPONENT_R_BIT | VK_COLOR_COMPONENT_G_BIT |
				VK_COLOR_COMPONENT_B_BIT | VK_COLOR_COMPONENT_A_BIT).blendEnable(ui);
			if (ui) blendAttachment.get(0).srcColorBlendFactor(VK_BLEND_FACTOR_ONE)
				.dstColorBlendFactor(VK_BLEND_FACTOR_ONE_MINUS_SRC_ALPHA).colorBlendOp(VK_BLEND_OP_ADD)
				.srcAlphaBlendFactor(VK_BLEND_FACTOR_ONE).dstAlphaBlendFactor(VK_BLEND_FACTOR_ONE_MINUS_SRC_ALPHA)
				.alphaBlendOp(VK_BLEND_OP_ADD);
			VkPipelineColorBlendStateCreateInfo blend = VkPipelineColorBlendStateCreateInfo.calloc(stack).sType$Default()
				.pAttachments(blendAttachment);
			VkPipelineDynamicStateCreateInfo dynamic = VkPipelineDynamicStateCreateInfo.calloc(stack).sType$Default()
				.pDynamicStates(stack.ints(VK_DYNAMIC_STATE_VIEWPORT, VK_DYNAMIC_STATE_SCISSOR));
			VkGraphicsPipelineCreateInfo.Buffer info = VkGraphicsPipelineCreateInfo.calloc(1, stack);
			info.get(0).sType$Default().pStages(stages).pVertexInputState(vertexInput).pInputAssemblyState(assembly)
				.pViewportState(viewport).pRasterizationState(raster).pMultisampleState(multisample)
				.pDepthStencilState(depth).pColorBlendState(blend).pDynamicState(dynamic)
				.layout(ui ? uiPipelineLayout : opaquePipelineLayout).renderPass(renderPass).subpass(0);
			LongBuffer handle = stack.mallocLong(1);
			check(vkCreateGraphicsPipelines(context.device(), VK_NULL_HANDLE, info, null, handle), "vkCreateGraphicsPipelines");
			liveHandles++;
			return handle.get(0);
		}
		finally
		{
			if (fragment != VK_NULL_HANDLE) { vkDestroyShaderModule(context.device(), fragment, null); liveHandles--; }
			if (vertex != VK_NULL_HANDLE) { vkDestroyShaderModule(context.device(), vertex, null); liveHandles--; }
		}
	}

	private long createShaderModule(String name)
	{
		byte[] bytes;
		try (InputStream input = VulkanOffscreenRenderer.class.getResourceAsStream("/shaders/vulkan-opaque-slice/" + name))
		{
			if (input == null) throw new IllegalStateException("Missing SPIR-V resource: " + name);
			bytes = input.readAllBytes();
		}
		catch (IOException failure)
		{
			throw new IllegalStateException("Unable to read SPIR-V resource: " + name, failure);
		}
		try (MemoryStack stack = MemoryStack.stackPush())
		{
			ByteBuffer code = stack.malloc(bytes.length).put(bytes).flip();
			VkShaderModuleCreateInfo info = VkShaderModuleCreateInfo.calloc(stack).sType$Default().pCode(code);
			LongBuffer handle = stack.mallocLong(1);
			check(vkCreateShaderModule(context.device(), info, null, handle), "vkCreateShaderModule");
			liveHandles++;
			return handle.get(0);
		}
	}

	private void transitionImage(VkCommandBuffer command, long image, int oldLayout, int newLayout,
		int sourceAccess, int destinationAccess, int sourceStage, int destinationStage)
	{
		try (MemoryStack stack = MemoryStack.stackPush())
		{
			VkImageMemoryBarrier.Buffer barrier = VkImageMemoryBarrier.calloc(1, stack);
			barrier.get(0).sType$Default().oldLayout(oldLayout).newLayout(newLayout)
				.srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED).dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
				.image(image).srcAccessMask(sourceAccess).dstAccessMask(destinationAccess);
			barrier.get(0).subresourceRange().aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
				.baseMipLevel(0).levelCount(1).baseArrayLayer(0).layerCount(1);
			vkCmdPipelineBarrier(command, sourceStage, destinationStage, 0, null, null, barrier);
		}
	}

	private void ensureOpen()
	{
		if (closed || context == null) throw new IllegalStateException("Offscreen renderer is closed.");
	}

	private static void check(int result, String operation)
	{
		if (result != VK_SUCCESS) throw new IllegalStateException(operation + " failed with VkResult " + result);
	}

	private final class Call implements AutoCloseable
	{
		final int width;
		final int height;
		VulkanOffscreenResources.Buffer vertices;
		VulkanOffscreenResources.Buffer metadata;
		VulkanOffscreenResources.Buffer uiStaging;
		VulkanOffscreenResources.Buffer readback;
		VulkanOffscreenResources.Image ui;
		VulkanOffscreenResources.Image color;
		VulkanOffscreenResources.Image depth;
		long framebuffer;
		long descriptorPool;
		long opaqueDescriptorSet;
		long uiDescriptorSet;

		Call(int width, int height, VulkanOpaqueZoneContract.PreparedUpload upload, PreparedUiTexture preparedUi)
		{
			this.width = width;
			this.height = height;
			try
			{
				ByteBuffer packedUi = tightlyPackedUi(preparedUi);
				vertices = resources.createBuffer(Math.max(4, upload.vertexBytes().remaining()), VK_BUFFER_USAGE_VERTEX_BUFFER_BIT,
					VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT);
				metadata = resources.createBuffer(Math.max(4, upload.faceMetadataBytes().remaining()), VK_BUFFER_USAGE_STORAGE_BUFFER_BIT,
					VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT);
				uiStaging = resources.createBuffer(packedUi.remaining(), VK_BUFFER_USAGE_TRANSFER_SRC_BIT,
					VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT);
				readback = resources.createBuffer((long) width * height * 4, VK_BUFFER_USAGE_TRANSFER_DST_BIT,
					VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT);
				ui = resources.createImage(preparedUi.width(), preparedUi.height(), COLOR_FORMAT,
					VK_IMAGE_USAGE_TRANSFER_DST_BIT | VK_IMAGE_USAGE_SAMPLED_BIT, VK_IMAGE_ASPECT_COLOR_BIT);
				color = resources.createImage(width, height, COLOR_FORMAT,
					VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT | VK_IMAGE_USAGE_TRANSFER_SRC_BIT, VK_IMAGE_ASPECT_COLOR_BIT);
				depth = resources.createImage(width, height, DEPTH_FORMAT,
					VK_IMAGE_USAGE_DEPTH_STENCIL_ATTACHMENT_BIT, VK_IMAGE_ASPECT_DEPTH_BIT);
				if (upload.vertexBytes().hasRemaining()) vertices.upload(upload.vertexBytes());
				if (upload.faceMetadataBytes().hasRemaining()) metadata.upload(upload.faceMetadataBytes());
				uiStaging.upload(packedUi);
				createCallObjects();
			}
			catch (RuntimeException | Error failure)
			{
				try { close(); }
				catch (RuntimeException | Error cleanup) { failure.addSuppressed(cleanup); }
				throw failure;
			}
		}

		private void createCallObjects()
		{
			try (MemoryStack stack = MemoryStack.stackPush())
			{
				LongBuffer handle = stack.mallocLong(2);
				VkFramebufferCreateInfo framebufferInfo = VkFramebufferCreateInfo.calloc(stack).sType$Default()
					.renderPass(renderPass).pAttachments(stack.longs(color.view(), depth.view()))
					.width(width).height(height).layers(1);
				check(vkCreateFramebuffer(context.device(), framebufferInfo, null, handle), "vkCreateFramebuffer");
				framebuffer = handle.get(0); liveHandles++;
				VkDescriptorPoolSize.Buffer sizes = VkDescriptorPoolSize.calloc(2, stack);
				sizes.get(0).type(VK_DESCRIPTOR_TYPE_STORAGE_BUFFER).descriptorCount(1);
				sizes.get(1).type(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER).descriptorCount(1);
				VkDescriptorPoolCreateInfo pool = VkDescriptorPoolCreateInfo.calloc(stack).sType$Default()
					.maxSets(2).pPoolSizes(sizes);
				check(vkCreateDescriptorPool(context.device(), pool, null, handle), "vkCreateDescriptorPool");
				descriptorPool = handle.get(0); liveHandles++;
				VkDescriptorSetAllocateInfo allocate = VkDescriptorSetAllocateInfo.calloc(stack).sType$Default()
					.descriptorPool(descriptorPool).pSetLayouts(stack.longs(opaqueDescriptorLayout, uiDescriptorLayout));
				check(vkAllocateDescriptorSets(context.device(), allocate, handle), "vkAllocateDescriptorSets");
				opaqueDescriptorSet = handle.get(0);
				uiDescriptorSet = handle.get(1);
				VkDescriptorBufferInfo.Buffer bufferInfo = VkDescriptorBufferInfo.calloc(1, stack);
				bufferInfo.get(0).buffer(metadata.handle()).offset(0).range(metadata.size());
				VkDescriptorImageInfo.Buffer imageInfo = VkDescriptorImageInfo.calloc(1, stack);
				imageInfo.get(0).sampler(sampler).imageView(ui.view()).imageLayout(VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL);
				VkWriteDescriptorSet.Buffer writes = VkWriteDescriptorSet.calloc(2, stack);
				writes.get(0).sType$Default().dstSet(opaqueDescriptorSet).dstBinding(0).descriptorCount(1)
					.descriptorType(VK_DESCRIPTOR_TYPE_STORAGE_BUFFER).pBufferInfo(bufferInfo);
				writes.get(1).sType$Default().dstSet(uiDescriptorSet).dstBinding(0).descriptorCount(1)
					.descriptorType(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER).pImageInfo(imageInfo);
				vkUpdateDescriptorSets(context.device(), writes, null);
			}
		}

		@Override
		public void close()
		{
			if (descriptorPool != VK_NULL_HANDLE) { vkDestroyDescriptorPool(context.device(), descriptorPool, null); descriptorPool = VK_NULL_HANDLE; liveHandles--; }
			if (framebuffer != VK_NULL_HANDLE) { vkDestroyFramebuffer(context.device(), framebuffer, null); framebuffer = VK_NULL_HANDLE; liveHandles--; }
			if (depth != null) depth.close();
			if (color != null) color.close();
			if (ui != null) ui.close();
			if (readback != null) readback.close();
			if (uiStaging != null) uiStaging.close();
			if (metadata != null) metadata.close();
			if (vertices != null) vertices.close();
		}
	}

	public static final class Result
	{
		private final int width;
		private final int height;
		private final byte[] bgra;
		private final int opaqueVertexCount;

		Result(int width, int height, byte[] bgra, int opaqueVertexCount)
		{
			this.width = width;
			this.height = height;
			this.bgra = bgra.clone();
			this.opaqueVertexCount = opaqueVertexCount;
		}
		public int width() { return width; }
		public int height() { return height; }
		public byte[] bgra() { return bgra.clone(); }
		public int opaqueVertexCount() { return opaqueVertexCount; }
	}
}
