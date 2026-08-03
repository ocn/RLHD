package rs117.hd.spikes.vulkan.control;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

final class VulkanTimingLog implements AutoCloseable
{
	private static final String[] COUNTER_NAMES = {
		"init_errors", "shader_errors", "pipeline_errors", "submitted", "completed", "command_errors",
		"present_requested", "nil_drawable", "skipped_suspended", "skipped_in_flight", "ui_upload_bytes",
		"resize_rebuilds", "device_rebuilds", "live_native_objects", "high_water_native_objects", "max_in_flight",
		"presentation_callbacks", "presentation_dropped", "presentation_timeouts", "present_mode_divergences",
		"drawable_acquisition_requests", "drawable_acquisition_completions", "validation_warnings", "validation_errors",
		"timestamp_query_errors"
	};

	private final Gson gson = new GsonBuilder().serializeNulls().create();
	private final BufferedWriter writer;
	private boolean started;
	private boolean ended;

	VulkanTimingLog(Path path)
	{
		try
		{
			Path absolute = path.toAbsolutePath();
			Path parent = absolute.getParent();
			if (parent != null) Files.createDirectories(parent);
			writer = Files.newBufferedWriter(absolute, StandardCharsets.UTF_8);
		}
		catch (IOException ex)
		{
			throw new IllegalStateException("Unable to open Vulkan timing log.", ex);
		}
	}

	synchronized void runStart(VulkanPresentMode requested, VulkanPresentMode effective, CapabilityRecord capabilities)
	{
		if (started) return;
		JsonObject record = base("run_start");
		record.addProperty("timestamp_ns", System.nanoTime());
		record.addProperty("requested_present_mode", requested.logName());
		record.addProperty("effective_present_mode", effective.logName());
		JsonObject caps = new JsonObject();
		caps.addProperty("api_version", "1.2");
		caps.addProperty("portability_enumeration", true);
		caps.addProperty("metal_surface", true);
		caps.addProperty("swapchain", true);
		caps.addProperty("swapchain_maintenance1", capabilities.swapchainMaintenance1);
		caps.addProperty("presentation_fences", capabilities.presentationFences);
		caps.addProperty("portability_subset", capabilities.portabilitySubset);
		caps.addProperty("format", "VK_FORMAT_B8G8R8A8_UNORM");
		caps.addProperty("color_space", capabilities.colorSpace);
		caps.addProperty("requested_images", capabilities.requestedImages);
		caps.addProperty("actual_images", capabilities.actualImages);
		caps.addProperty("frames_in_flight", 2);
		caps.addProperty("standard_queue_present", true);
		caps.addProperty("graphics_queue_family", capabilities.graphicsQueueFamily);
		caps.addProperty("present_queue_family", capabilities.presentQueueFamily);
		caps.addProperty("split_queue_families", capabilities.graphicsQueueFamily != capabilities.presentQueueFamily);
		caps.addProperty("validation_requested", capabilities.validationRequested);
		caps.addProperty("validation_enabled", capabilities.validationEnabled);
		caps.addProperty("debug_utils_enabled", capabilities.debugUtilsEnabled);
		caps.addProperty("timestamps_supported", capabilities.timestampsSupported);
		caps.addProperty("timestamp_valid_bits", capabilities.timestampValidBits);
		caps.addProperty("timestamp_period_ns", capabilities.timestampPeriodNs);
		caps.addProperty("mailbox_available", capabilities.mailboxAvailable);
		caps.addProperty("immediate_available", capabilities.immediateAvailable);
		caps.addProperty("physical_device", capabilities.physicalDevice);
		record.add("capabilities", caps);
		write(record);
		started = true;
	}

	synchronized void frame(FrameRecord frame, long[] counters)
	{
		JsonObject record = base("frame");
		record.addProperty("frame_id", frame.frameId);
		JsonObject resolution = new JsonObject();
		resolution.addProperty("width", frame.width);
		resolution.addProperty("height", frame.height);
		record.add("resolution", resolution);
		record.addProperty("requested_present_mode", frame.requested.logName());
		record.addProperty("effective_present_mode", frame.effective.logName());
		record.addProperty("outcome", frame.outcome.wireName());
		JsonObject cpu = new JsonObject();
		cpu.addProperty("ui_generate", frame.uiGenerateNs);
		cpu.addProperty("ui_upload", frame.uiUploadNs);
		cpu.addProperty("encode", frame.encodeNs);
		cpu.addProperty("submit", frame.submitNs);
		cpu.addProperty("total", frame.totalNs);
		record.add("cpu_ns", cpu);
		if (frame.gpuStartNs == null || frame.gpuEndNs == null)
		{
			record.add("gpu_ns", JsonNull.INSTANCE);
		}
		else
		{
			JsonObject gpu = new JsonObject();
			gpu.addProperty("start", frame.gpuStartNs);
			gpu.addProperty("end", frame.gpuEndNs);
			gpu.addProperty("duration", frame.gpuEndNs - frame.gpuStartNs);
			record.add("gpu_ns", gpu);
		}
		JsonObject present = new JsonObject();
		boolean submitted = frame.outcome == VulkanFrameOutcome.SUBMITTED;
		present.addProperty("requested", submitted);
		present.addProperty("drawable_available", submitted);
		present.addProperty("callback_status", submitted ? "unsupported" : "not-requested");
		present.add("presented_time_ns", JsonNull.INSTANCE);
		present.add("latency_ns", JsonNull.INSTANCE);
		record.add("present", present);
		record.add("counters", counters(counters));
		if (frame.error == null) record.add("error", JsonNull.INSTANCE);
		else record.addProperty("error", frame.error);
		write(record);
	}

	synchronized void runEnd(VulkanPresentMode requested, VulkanPresentMode effective, long[] counters, String error)
	{
		if (ended) return;
		JsonObject record = base("run_end");
		record.addProperty("timestamp_ns", System.nanoTime());
		record.addProperty("requested_present_mode", requested.logName());
		record.addProperty("effective_present_mode", effective.logName());
		record.add("counters", counters(counters));
		if (error == null) record.add("error", JsonNull.INSTANCE);
		else record.addProperty("error", error);
		write(record);
		ended = true;
	}

	@Override
	public synchronized void close()
	{
		try
		{
			writer.close();
		}
		catch (IOException ex)
		{
			throw new IllegalStateException("Unable to close Vulkan timing log.", ex);
		}
	}

	private JsonObject base(String type)
	{
		JsonObject object = new JsonObject();
		object.addProperty("schema", VulkanTimingJsonSchema.SCHEMA);
		object.addProperty("type", type);
		object.addProperty("backend", VulkanTimingJsonSchema.BACKEND);
		return object;
	}

	private JsonObject counters(long[] values)
	{
		JsonObject object = new JsonObject();
		for (int index = 0; index < COUNTER_NAMES.length; index++) object.addProperty(COUNTER_NAMES[index], values[index]);
		return object;
	}

	private void write(JsonObject record)
	{
		try
		{
			writer.write(gson.toJson(record));
			writer.newLine();
			writer.flush();
		}
		catch (IOException ex)
		{
			throw new IllegalStateException("Unable to write Vulkan timing log.", ex);
		}
	}

	static final class CapabilityRecord
	{
		boolean portabilitySubset;
		boolean swapchainMaintenance1;
		boolean presentationFences;
		String colorSpace;
		int requestedImages;
		int actualImages;
		int graphicsQueueFamily;
		int presentQueueFamily;
		boolean validationRequested;
		boolean validationEnabled;
		boolean debugUtilsEnabled;
		boolean timestampsSupported;
		int timestampValidBits;
		double timestampPeriodNs;
		boolean mailboxAvailable;
		boolean immediateAvailable;
		String physicalDevice;
	}

	static final class FrameRecord
	{
		long frameId;
		int width;
		int height;
		VulkanPresentMode requested;
		VulkanPresentMode effective;
		VulkanFrameOutcome outcome;
		long uiGenerateNs;
		long uiUploadNs;
		long encodeNs;
		long submitNs;
		long totalNs;
		Long gpuStartNs;
		Long gpuEndNs;
		String error;
	}
}
