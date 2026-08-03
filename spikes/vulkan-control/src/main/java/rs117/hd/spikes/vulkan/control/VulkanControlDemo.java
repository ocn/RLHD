package rs117.hd.spikes.vulkan.control;

import java.awt.Canvas;
import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.GraphicsDevice;
import java.awt.geom.AffineTransform;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;
import rs117.hd.spikes.macos.MacMetalSurface;

public final class VulkanControlDemo
{
	private VulkanControlDemo() {}

	public static void main(String[] args) throws Exception
	{
		VulkanLiveRiskLevel riskLevel = VulkanLiveRiskLevel.selectRequired(
			System.getProperty(VulkanLiveRiskLevel.SYSTEM_PROPERTY), argument(args, "--risk-level", null));
		boolean validation = Boolean.parseBoolean(argument(args, "--validation", "true"));
		System.setProperty(VulkanLiveRiskLevel.SYSTEM_PROPERTY, riskLevel.optionName());
		System.setProperty("rlhd.spike.vulkan.validation", Boolean.toString(validation));
		try (VulkanCrashJournal journal = VulkanCrashJournal.openRequired("vulkan-control-demo"))
		{
			run(args, riskLevel, validation, journal);
		}
	}

	private static void run(String[] args, VulkanLiveRiskLevel riskLevel, boolean validation,
		VulkanCrashJournal journal) throws Exception
	{
		int seconds = Integer.parseInt(argument(args, "--seconds", "30"));
		Path log = Paths.get(argument(args, "--log", "build/spikes/vulkan-control/vulkan-control.jsonl"));
		String logTransition = journal.intent("timing_log.selected", VulkanCrashJournal.fields(
			"path", log.toAbsolutePath().normalize().toString(), "validation_requested", validation));
		journal.completed("timing_log.selected", logTransition, VulkanCrashJournal.fields(
			"path", log.toAbsolutePath().normalize().toString(), "validation_requested", validation));
		String riskTransition = journal.intent("risk_level.selected", VulkanCrashJournal.fields(
			"risk_level", riskLevel.optionName(), "rung", riskLevel.rung()));
		journal.completed("risk_level.selected", riskTransition, VulkanCrashJournal.fields(
			"risk_level", riskLevel.optionName(), "rung", riskLevel.rung()));
		Frame[] frameHolder = new Frame[1];
		Canvas[] canvasHolder = new Canvas[1];
		String frameTransition = journal.intent("awt_frame.visible", VulkanCrashJournal.fields("width", 960, "height", 540));
		EventQueue.invokeAndWait(() ->
		{
			Frame frame = new Frame("RLHD Vulkan/MoltenVK control spike");
			Canvas canvas = new Canvas();
			frame.add(canvas);
			frame.setSize(960, 540);
			frame.setLocationByPlatform(true);
			frame.setVisible(true);
			frameHolder[0] = frame;
			canvasHolder[0] = canvas;
		});
		Frame frame = frameHolder[0];
		Canvas canvas = canvasHolder[0];
		GraphicsDevice display = frame.getGraphicsConfiguration().getDevice();
		journal.completed("awt_frame.visible", frameTransition, displayDetails(frame, canvas));
		MacMetalSurface surface = new MacMetalSurface();
		VulkanControlRenderer renderer = null;
		Throwable primaryFailure = null;
		try
		{
			String attachTransition = journal.intent("surface.attach", displayDetails(frame, canvas));
			surface.attach(canvas);
			journal.completed("surface.attach", attachTransition,
				VulkanCrashJournal.fields("metal_layer_handle_nonzero", surface.metalLayerHandle() != 0));
			String resizeTransition = journal.intent("surface.initial_resize", displayDetails(frame, canvas));
			resize(surface, frame, canvas);
			journal.completed("surface.initial_resize", resizeTransition, extentDetails(surface));
			String backendTransition = journal.intent("backend.init", extentDetails(surface));
			renderer = new VulkanControlRenderer(surface, log, VulkanPresentMode.FIFO, validation, journal);
			journal.completed("backend.init", backendTransition, VulkanCrashJournal.fields("validation", validation));
			String readbackTransition = journal.intent("gpu_readback", VulkanCrashJournal.fields("width", 8, "height", 8));
			if (!renderer.runReadbackCheck()) throw new IllegalStateException("Vulkan triangle/UI GPU readback failed.");
			journal.completed("gpu_readback", readbackTransition, VulkanCrashJournal.fields("matched", true));
			if (riskLevel.exercisesFullscreen() && !display.isFullScreenSupported())
				throw new IllegalStateException("The selected fullscreen risk rung is unsupported by this display.");
			long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(Math.max(1, seconds));
			long frameId = 0;
			boolean resized = false;
			boolean suspended = false;
			boolean restored = false;
			boolean unlocked = false;
			boolean fifo = false;
			boolean fullscreen = false;
			boolean fullscreenExited = false;
			while (riskLevel.presentsFrames() && System.nanoTime() < deadline && frame.isDisplayable())
			{
				long elapsed = seconds - TimeUnit.NANOSECONDS.toSeconds(Math.max(0, deadline - System.nanoTime()));
				if (riskLevel.exercisesResizeSuspendRestore() && !resized && elapsed >= Math.max(1, seconds / 6))
				{
					String transition = journal.intent("window.resize", VulkanCrashJournal.fields("width", 720, "height", 420));
					EventQueue.invokeAndWait(() -> frame.setSize(720, 420));
					journal.completed("window.resize", transition, displayDetails(frame, canvas));
					resized = true;
				}
				if (riskLevel.exercisesResizeSuspendRestore() && !suspended && elapsed >= Math.max(2, seconds / 4))
				{
					String transition = journal.intent("surface.suspend", extentDetails(surface));
					surface.resize(0, 0, scale(canvas));
					journal.completed("surface.suspend", transition, extentDetails(surface));
					suspended = true;
				}
				if (riskLevel.exercisesResizeSuspendRestore() && suspended && !restored && elapsed >= Math.max(3, seconds / 4 + 2))
				{
					String transition = journal.intent("surface.restore", displayDetails(frame, canvas));
					resize(surface, frame, canvas);
					journal.completed("surface.restore", transition, extentDetails(surface));
					restored = true;
				}
				if (riskLevel.exercisesUnlockedPresent() && !unlocked && elapsed >= Math.max(1, seconds / 3))
				{
					String transition = journal.intent("present_mode.unlocked", VulkanCrashJournal.fields());
					renderer.setPresentMode(VulkanPresentMode.UNLOCKED);
					journal.completed("present_mode.unlocked", transition, VulkanCrashJournal.fields());
					unlocked = true;
				}
				if (riskLevel.exercisesUnlockedPresent() && !fifo && elapsed >= Math.max(2, seconds * 2 / 3))
				{
					String transition = journal.intent("present_mode.fifo", VulkanCrashJournal.fields());
					renderer.setPresentMode(VulkanPresentMode.FIFO);
					journal.completed("present_mode.fifo", transition, VulkanCrashJournal.fields());
					fifo = true;
				}
				if (riskLevel.exercisesFullscreen() && !fullscreen && !fullscreenExited && elapsed >= Math.max(2, seconds / 2))
				{
					String transition = journal.intent("fullscreen.enter", displayDetails(frame, canvas));
					EventQueue.invokeAndWait(() -> display.setFullScreenWindow(frame));
					journal.completed("fullscreen.enter", transition, displayDetails(frame, canvas));
					fullscreen = true;
				}
				if (riskLevel.exercisesFullscreen() && fullscreen && elapsed >= Math.max(3, seconds * 3 / 4))
				{
					String transition = journal.intent("fullscreen.exit", displayDetails(frame, canvas));
					EventQueue.invokeAndWait(() -> display.setFullScreenWindow(null));
					journal.completed("fullscreen.exit", transition, displayDetails(frame, canvas));
					fullscreen = false;
					fullscreenExited = true;
				}
				if (!suspended || restored) resize(surface, frame, canvas);
				renderer.render(surface.extent(), frameId++);
				if (riskLevel == VulkanLiveRiskLevel.FIRST_FIFO_PRESENT) break;
				Thread.sleep(1);
			}
			if (riskLevel.exercisesResizeSuspendRestore() && !(resized && suspended && restored))
				throw new IllegalStateException("The selected resize/suspend/restore rung did not complete; increase --seconds.");
			if (riskLevel.exercisesUnlockedPresent() && !(unlocked && fifo))
				throw new IllegalStateException("The selected unlocked/FIFO rung did not complete; increase --seconds.");
			if (riskLevel.exercisesFullscreen() && !fullscreenExited)
				throw new IllegalStateException("The selected fullscreen enter/exit rung did not complete; increase --seconds.");
			System.out.println(renderer.counters());
		}
		catch (Exception | Error ex)
		{
			primaryFailure = ex;
			journal.heartbeat("run.failure", VulkanCrashJournal.fields("error_type", ex.getClass().getName(), "error_message", ex.getMessage()));
			throw ex;
		}
		finally
		{
			Throwable cleanup = null;
			if (renderer != null) cleanup = attempt(cleanup, renderer::close);
			cleanup = attempt(cleanup, surface::detach);
			cleanup = attempt(cleanup, surface::close);
			cleanup = attempt(cleanup, () -> EventQueue.invokeAndWait(frame::dispose));
			if (cleanup != null && primaryFailure != null) primaryFailure.addSuppressed(cleanup);
			journal.complete(primaryFailure == null && cleanup == null ? "passed" : "failed",
				primaryFailure == null ? cleanup : primaryFailure);
			if (primaryFailure == null && cleanup instanceof Exception) throw (Exception) cleanup;
			if (primaryFailure == null && cleanup instanceof Error) throw (Error) cleanup;
		}
	}

	private static java.util.Map<String, Object> displayDetails(Frame frame, Canvas canvas)
	{
		java.awt.DisplayMode mode = frame.getGraphicsConfiguration().getDevice().getDisplayMode();
		return VulkanCrashJournal.fields("display_id", frame.getGraphicsConfiguration().getDevice().getIDstring(),
			"window_width", frame.getWidth(), "window_height", frame.getHeight(),
			"canvas_width", canvas.getWidth(), "canvas_height", canvas.getHeight(), "scale", scale(canvas),
			"display_width", mode.getWidth(), "display_height", mode.getHeight(), "refresh_rate", mode.getRefreshRate());
	}

	private static java.util.Map<String, Object> extentDetails(MacMetalSurface surface)
	{
		rs117.hd.spikes.macos.SurfaceExtent extent = surface.extent();
		return VulkanCrashJournal.fields("logical_width", extent.logicalWidth(), "logical_height", extent.logicalHeight(),
			"scale", extent.backingScale(), "pixel_width", extent.pixelWidth(), "pixel_height", extent.pixelHeight(),
			"suspended", extent.suspended());
	}

	private static Throwable attempt(Throwable prior, Action action)
	{
		try { action.run(); }
		catch (Throwable ex) { if (prior == null) return ex; prior.addSuppressed(ex); }
		return prior;
	}

	private static void resize(MacMetalSurface surface, Frame frame, Canvas canvas)
	{
		if ((frame.getExtendedState() & Frame.ICONIFIED) != 0) surface.resize(0, 0, scale(canvas));
		else surface.resize(Math.max(0, canvas.getWidth()), Math.max(0, canvas.getHeight()), scale(canvas));
	}

	private static double scale(Canvas canvas)
	{
		AffineTransform transform = canvas.getGraphicsConfiguration().getDefaultTransform();
		return transform.getScaleX();
	}

	private static String argument(String[] args, String name, String fallback)
	{
		for (int index = 0; index + 1 < args.length; index++) if (name.equals(args[index])) return args[index + 1];
		return fallback;
	}

	private interface Action { void run() throws Exception; }
}
