package rs117.hd.spikes.vulkan.control;

import java.awt.Canvas;
import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.GraphicsDevice;
import java.awt.geom.AffineTransform;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.Assume;
import org.junit.Test;
import rs117.hd.spikes.macos.MacMetalSurface;
import rs117.hd.spikes.macos.SurfaceExtent;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertThrows;

public class VulkanControlIntegrationTest
{
	@Test
	public void rendersReadsBackRecreatesAndTearsDownOnMoltenVk() throws Exception
	{
		Assume.assumeTrue(Boolean.getBoolean("rlhd.spike.vulkan.integration"));
		VulkanLiveRiskLevel riskLevel = VulkanLiveRiskLevel.fromSystemProperty();
		try (VulkanCrashJournal journal = VulkanCrashJournal.openRequired("vulkan-control-integration"))
		{
			recordRiskSelection(journal, riskLevel);
			Throwable runFailure = null;
			try
			{
		Path log = selectedTimingLog(journal, "rlhd-vulkan-control-");
		Frame[] frameHolder = new Frame[1];
		Canvas[] canvasHolder = new Canvas[1];
		String frameTransition = journal.intent("awt_frame.visible", VulkanCrashJournal.fields("width", 640, "height", 360));
		EventQueue.invokeAndWait(() ->
		{
			Frame frame = new Frame("RLHD Vulkan integration");
			Canvas canvas = new Canvas();
			frame.add(canvas);
			frame.setSize(640, 360);
			frame.setVisible(true);
			frameHolder[0] = frame;
			canvasHolder[0] = canvas;
		});
		Frame frame = frameHolder[0];
		Canvas canvas = canvasHolder[0];
		journal.completed("awt_frame.visible", frameTransition, displayDetails(frame, canvas));
		MacMetalSurface surface = new MacMetalSurface();
		VulkanControlRenderer renderer = null;
		try
		{
			String attachTransition = journal.intent("surface.attach", displayDetails(frame, canvas));
			surface.attach(canvas);
			journal.completed("surface.attach", attachTransition,
				VulkanCrashJournal.fields("metal_layer_handle_nonzero", surface.metalLayerHandle() != 0));
			resize(surface, canvas);
			renderer = new VulkanControlRenderer(surface, log, VulkanPresentMode.FIFO, true, journal);
			String readbackTransition = journal.intent("gpu_readback", VulkanCrashJournal.fields("width", 8, "height", 8));
			assertTrue("Actual Vulkan triangle/UI GPU readback failed", renderer.runReadbackCheck());
			journal.completed("gpu_readback", readbackTransition, VulkanCrashJournal.fields("matched", true));
			long frameId = 0;
			for (; frameId < riskLevel.baselineFrameCount(); frameId++)
				assertEquals(VulkanFrameOutcome.SUBMITTED, renderer.render(surface.extent(), frameId));

			if (riskLevel.exercisesResizeSuspendRestore())
			{
				String resizeTransition = journal.intent("window.resize", VulkanCrashJournal.fields("width", 777, "height", 431));
				EventQueue.invokeAndWait(() -> frame.setSize(777, 431));
				journal.completed("window.resize", resizeTransition, displayDetails(frame, canvas));
				resize(surface, canvas);
				for (int count = 0; count < 20; count++, frameId++)
					assertEquals(VulkanFrameOutcome.SUBMITTED, renderer.render(surface.extent(), frameId));

				String suspendTransition = journal.intent("surface.suspend", VulkanCrashJournal.fields());
				surface.resize(0, 0, scale(canvas));
				journal.completed("surface.suspend", suspendTransition, VulkanCrashJournal.fields());
				assertEquals(VulkanFrameOutcome.SKIPPED_SUSPENDED, renderer.render(surface.extent(), frameId++));
				String restoreTransition = journal.intent("surface.restore", displayDetails(frame, canvas));
				resize(surface, canvas);
				journal.completed("surface.restore", restoreTransition, VulkanCrashJournal.fields());
				for (int count = 0; count < 20; count++, frameId++)
					assertEquals(VulkanFrameOutcome.SUBMITTED, renderer.render(surface.extent(), frameId));
			}

			if (riskLevel.exercisesUnlockedPresent())
			{
				String unlockedTransition = journal.intent("present_mode.unlocked", VulkanCrashJournal.fields());
				renderer.setPresentMode(VulkanPresentMode.UNLOCKED);
				journal.completed("present_mode.unlocked", unlockedTransition, VulkanCrashJournal.fields());
				for (int count = 0; count < 40; count++, frameId++)
					assertEquals(VulkanFrameOutcome.SUBMITTED, renderer.render(surface.extent(), frameId));
				String fifoTransition = journal.intent("present_mode.fifo", VulkanCrashJournal.fields());
				renderer.setPresentMode(VulkanPresentMode.FIFO);
				journal.completed("present_mode.fifo", fifoTransition, VulkanCrashJournal.fields());
				for (int count = 0; count < 20; count++, frameId++)
					assertEquals(VulkanFrameOutcome.SUBMITTED, renderer.render(surface.extent(), frameId));
			}

			if (riskLevel.exercisesFullscreen())
			{
				GraphicsDevice display = frame.getGraphicsConfiguration().getDevice();
				assertTrue("The selected fullscreen rung must be supported", display.isFullScreenSupported());
				String fullscreenEnter = journal.intent("fullscreen.enter", displayDetails(frame, canvas));
				EventQueue.invokeAndWait(() -> display.setFullScreenWindow(frame));
				journal.completed("fullscreen.enter", fullscreenEnter, displayDetails(frame, canvas));
				resize(surface, canvas);
				for (int count = 0; count < 20; count++, frameId++)
					assertEquals(VulkanFrameOutcome.SUBMITTED, renderer.render(surface.extent(), frameId));
				String fullscreenExit = journal.intent("fullscreen.exit", displayDetails(frame, canvas));
				EventQueue.invokeAndWait(() -> display.setFullScreenWindow(null));
				journal.completed("fullscreen.exit", fullscreenExit, displayDetails(frame, canvas));
				resize(surface, canvas);
				for (int count = 0; count < 20; count++, frameId++)
					assertEquals(VulkanFrameOutcome.SUBMITTED, renderer.render(surface.extent(), frameId));
			}
			VulkanControlCounters running = renderer.counters();
			assertEquals(riskLevel.presentsFrames(), running.submitted() > 0);
			assertTrue(running.maxInFlight() <= 2);
			assertEquals(running.drawableAcquisitionRequests(), running.drawableAcquisitionCompletions());
			renderer.close();
			VulkanControlCounters closed = renderer.counters();
			renderer = null;
			assertEquals(0, closed.liveNativeObjects());
			assertEquals(closed.submitted(), closed.completed());
			assertEquals(closed.submitted(), closed.presentRequested());
			assertEquals(closed.drawableAcquisitionRequests(), closed.drawableAcquisitionCompletions());
			if (riskLevel.exercisesResizeSuspendRestore() || riskLevel.exercisesUnlockedPresent())
				assertTrue(closed.resizeRebuilds() >= 2);
			else if (!riskLevel.exercisesFullscreen())
				assertEquals(0, closed.resizeRebuilds());
			assertEquals(0, closed.validationWarnings());
			assertEquals(0, closed.validationErrors());
			assertEquals(0, closed.timestampQueryErrors());
			assertFalse(closed.hasErrors());
			surface.detach();
			surface.close();
			List<String> lines = Files.readAllLines(log);
			VulkanTimingJsonSchema.validateLog(lines);
			assertTrue(lines.get(0).contains("\"validation_requested\":true"));
			assertTrue(lines.get(0).contains("\"validation_enabled\":true"));
			assertTrue(lines.get(0).contains("\"standard_queue_present\":true"));
			assertTrue(lines.get(0).contains("\"swapchain_maintenance1\":true"));
			assertTrue(lines.get(0).contains("\"presentation_fences\":true"));
			if (riskLevel.exercisesUnlockedPresent())
				assertTrue(lines.stream().anyMatch(line -> line.contains("\"effective_present_mode\":\"unlocked\"") ||
					line.contains("\"effective_present_mode\":\"mailbox\"")));
		}
		finally
		{
			if (renderer != null) renderer.close();
			try { surface.detach(); } catch (IllegalStateException ignored) {}
			try { surface.close(); } catch (IllegalStateException ignored) {}
			EventQueue.invokeAndWait(frame::dispose);
		}
			}
			catch (Exception | Error ex)
			{
				runFailure = ex;
				throw ex;
			}
			finally
			{
				journal.complete(runFailure == null ? "passed" : "failed", runFailure);
			}
		}
	}

	@Test
	public void injectedLiveBackendFailuresRemainDeterministicallyCloseable() throws Exception
	{
		Assume.assumeTrue(Boolean.getBoolean("rlhd.spike.vulkan.integration"));
		VulkanLiveRiskLevel riskLevel = VulkanLiveRiskLevel.fromSystemProperty();
		Assume.assumeTrue("Failure injection is isolated to the unlocked-present rung",
			riskLevel == VulkanLiveRiskLevel.UNLOCKED_PRESENT);
		try (VulkanCrashJournal journal = VulkanCrashJournal.openRequired("vulkan-control-failure-integration"))
		{
			recordRiskSelection(journal, riskLevel);
			Throwable runFailure = null;
			try
			{
		Frame[] frameHolder = new Frame[1];
		Canvas[] canvasHolder = new Canvas[1];
		String frameTransition = journal.intent("awt_frame.visible", VulkanCrashJournal.fields("width", 480, "height", 300));
		EventQueue.invokeAndWait(() ->
		{
			Frame frame = new Frame("RLHD Vulkan failure integration");
			Canvas canvas = new Canvas();
			frame.add(canvas);
			frame.setSize(480, 300);
			frame.setVisible(true);
			frameHolder[0] = frame;
			canvasHolder[0] = canvas;
		});
		Frame frame = frameHolder[0];
		Canvas canvas = canvasHolder[0];
		journal.completed("awt_frame.visible", frameTransition, displayDetails(frame, canvas));
		MacMetalSurface surface = new MacMetalSurface();
		try
		{
				String attachTransition = journal.intent("surface.attach", displayDetails(frame, canvas));
				surface.attach(canvas);
				journal.completed("surface.attach", attachTransition,
					VulkanCrashJournal.fields("metal_layer_handle_nonzero", surface.metalLayerHandle() != 0));
			resize(surface, canvas);
			SurfaceExtent extent = surface.extent();
			int width = Math.toIntExact(Math.round(extent.pixelWidth()));
			int height = Math.toIntExact(Math.round(extent.pixelHeight()));

			for (String point : new String[] {"command-resource-allocation", "buffer-allocation", "image-allocation",
				"shader-module-creation", "partial-swapchain-children"})
			{
				Path log = selectedTimingLog(journal, "rlhd-vulkan-partial-");
				FailureAt injected = new FailureAt(point);
				LwjglVulkanBackend incomplete = new LwjglVulkanBackend(surface.metalLayerHandle(), width, height,
					log, VulkanPresentMode.FIFO, true, injected, journal);
				assertFalse(incomplete.ready());
				long[] partialCounters = new long[VulkanControlCounters.FIELD_COUNT];
				incomplete.close(partialCounters);
				VulkanControlCounters counters = new VulkanControlCounters(partialCounters);
				assertTrue(injected.fired());
				assertEquals(0, counters.liveNativeObjects());
				assertEquals(1, counters.initErrors());
				assertEquals(0, counters.validationWarnings());
				assertEquals(0, counters.validationErrors());
				VulkanTimingJsonSchema.validateLog(Files.readAllLines(log));
			}

			for (String point : new String[] {"after-acquire", "after-record", "before-submit", "during-recreate"})
			{
				Path log = selectedTimingLog(journal, "rlhd-vulkan-failure-");
				FailureAt injected = new FailureAt(point);
				LwjglVulkanBackend backend = new LwjglVulkanBackend(surface.metalLayerHandle(), width, height,
					log, VulkanPresentMode.FIFO, true, injected, journal);
				assertTrue(backend.ready());
				VulkanControlRenderer renderer = new VulkanControlRenderer(backend, log);
				if ("during-recreate".equals(point))
				{
					String unlockedTransition = journal.intent("present_mode.unlocked", VulkanCrashJournal.fields());
					renderer.setPresentMode(VulkanPresentMode.UNLOCKED);
					journal.completed("present_mode.unlocked", unlockedTransition, VulkanCrashJournal.fields());
				}
				assertThrows(IllegalStateException.class, () -> renderer.render(extent, 1));
				renderer.close();
				VulkanControlCounters counters = renderer.counters();
				assertEquals(counters.drawableAcquisitionRequests(), counters.drawableAcquisitionCompletions());
				assertEquals(counters.submitted(), counters.completed());
				assertEquals(0, counters.liveNativeObjects());
				assertEquals(0, counters.validationWarnings());
				assertEquals(0, counters.validationErrors());
				VulkanTimingJsonSchema.validateLog(Files.readAllLines(log));
			}

			Path readbackLog = selectedTimingLog(journal, "rlhd-vulkan-readback-failure-");
			FailureAt readbackFailure = new FailureAt("readback-allocation");
			LwjglVulkanBackend readbackBackend = new LwjglVulkanBackend(surface.metalLayerHandle(), width, height,
				readbackLog, VulkanPresentMode.FIFO, true, readbackFailure, journal);
			VulkanControlRenderer readbackRenderer = new VulkanControlRenderer(readbackBackend, readbackLog);
			assertThrows(IllegalStateException.class, readbackRenderer::runReadbackCheck);
			assertTrue(readbackFailure.fired());
			readbackRenderer.close();
			assertEquals(0, readbackRenderer.counters().liveNativeObjects());
			assertEquals(0, readbackRenderer.counters().validationWarnings());
			assertEquals(0, readbackRenderer.counters().validationErrors());
			VulkanTimingJsonSchema.validateLog(Files.readAllLines(readbackLog));

			Path closeLog = selectedTimingLog(journal, "rlhd-vulkan-close-failure-");
			LwjglVulkanBackend closeBackend = new LwjglVulkanBackend(surface.metalLayerHandle(), width, height,
				closeLog, VulkanPresentMode.FIFO, true, new FailureAt("close-after-consumption"), journal);
			VulkanControlRenderer closeRenderer = new VulkanControlRenderer(closeBackend, closeLog);
			assertEquals(VulkanFrameOutcome.SUBMITTED, closeRenderer.render(extent, 2));
			assertThrows(VulkanBackendCloseException.class, closeRenderer::close);
			assertEquals(0, closeRenderer.counters().liveNativeObjects());
			assertEquals(0, closeRenderer.counters().validationWarnings());
			assertEquals(0, closeRenderer.counters().validationErrors());
			assertThrows(IllegalStateException.class, closeRenderer::close);
			VulkanTimingJsonSchema.validateLog(Files.readAllLines(closeLog));
		}
		finally
		{
			try { surface.detach(); } catch (IllegalStateException ignored) {}
			try { surface.close(); } catch (IllegalStateException ignored) {}
			EventQueue.invokeAndWait(frame::dispose);
		}
			}
			catch (Exception | Error ex)
			{
				runFailure = ex;
				throw ex;
			}
			finally
			{
				journal.complete(runFailure == null ? "passed" : "failed", runFailure);
			}
		}
	}

	private static Path selectedTimingLog(VulkanCrashJournal journal, String prefix) throws Exception
	{
		Path log = Files.createTempFile(prefix, ".jsonl");
		java.util.Map<String, Object> details = VulkanCrashJournal.fields(
			"path", log.toAbsolutePath().normalize().toString(), "validation_requested", true);
		String transition = journal.intent("timing_log.selected", details);
		journal.completed("timing_log.selected", transition, details);
		return log;
	}

	private static void recordRiskSelection(VulkanCrashJournal journal, VulkanLiveRiskLevel riskLevel)
	{
		java.util.Map<String, Object> details = VulkanCrashJournal.fields(
			"risk_level", riskLevel.optionName(), "rung", riskLevel.rung());
		String transition = journal.intent("risk_level.selected", details);
		journal.completed("risk_level.selected", transition, details);
	}

	private static final class FailureAt implements VulkanFailureInjector
	{
		private final String point;
		private boolean fired;

		private FailureAt(String point)
		{
			this.point = point;
		}

		@Override
		public void check(String candidate)
		{
			if (!fired && point.equals(candidate))
			{
				fired = true;
				throw new IllegalStateException("injected-" + point);
			}
		}

		private boolean fired()
		{
			return fired;
		}
	}

	private static void resize(MacMetalSurface surface, Canvas canvas)
	{
		surface.resize(Math.max(1, canvas.getWidth()), Math.max(1, canvas.getHeight()), scale(canvas));
	}

	private static double scale(Canvas canvas)
	{
		AffineTransform transform = canvas.getGraphicsConfiguration().getDefaultTransform();
		return transform.getScaleX();
	}

	private static java.util.Map<String, Object> displayDetails(Frame frame, Canvas canvas)
	{
		java.awt.DisplayMode mode = frame.getGraphicsConfiguration().getDevice().getDisplayMode();
		return VulkanCrashJournal.fields("display_id", frame.getGraphicsConfiguration().getDevice().getIDstring(),
			"window_width", frame.getWidth(), "window_height", frame.getHeight(),
			"canvas_width", canvas.getWidth(), "canvas_height", canvas.getHeight(), "scale", scale(canvas),
			"display_width", mode.getWidth(), "display_height", mode.getHeight(), "refresh_rate", mode.getRefreshRate());
	}
}
