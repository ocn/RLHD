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
		Path log = Files.createTempFile("rlhd-vulkan-control-", ".jsonl");
		Frame[] frameHolder = new Frame[1];
		Canvas[] canvasHolder = new Canvas[1];
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
		MacMetalSurface surface = new MacMetalSurface();
		VulkanControlRenderer renderer = null;
		try
		{
			surface.attach(canvas);
			resize(surface, canvas);
			renderer = new VulkanControlRenderer(surface, log, VulkanPresentMode.FIFO, true);
			assertTrue("Actual Vulkan triangle/UI GPU readback failed", renderer.runReadbackCheck());
			for (int frameId = 0; frameId < 60; frameId++)
				assertEquals(VulkanFrameOutcome.SUBMITTED, renderer.render(surface.extent(), frameId));

			EventQueue.invokeAndWait(() -> frame.setSize(777, 431));
			resize(surface, canvas);
			for (int frameId = 60; frameId < 100; frameId++)
				assertEquals(VulkanFrameOutcome.SUBMITTED, renderer.render(surface.extent(), frameId));

			surface.resize(0, 0, scale(canvas));
			assertEquals(VulkanFrameOutcome.SKIPPED_SUSPENDED, renderer.render(surface.extent(), 100));
			resize(surface, canvas);
			renderer.setPresentMode(VulkanPresentMode.UNLOCKED);
			for (int frameId = 101; frameId < 140; frameId++)
				assertEquals(VulkanFrameOutcome.SUBMITTED, renderer.render(surface.extent(), frameId));
			renderer.setPresentMode(VulkanPresentMode.FIFO);

			GraphicsDevice display = frame.getGraphicsConfiguration().getDevice();
			int fullscreenFrames = 0;
			if (display.isFullScreenSupported())
			{
				EventQueue.invokeAndWait(() -> display.setFullScreenWindow(frame));
				resize(surface, canvas);
				for (int frameId = 140; frameId < 160; frameId++)
				{
					assertEquals(VulkanFrameOutcome.SUBMITTED, renderer.render(surface.extent(), frameId));
					fullscreenFrames++;
				}
				EventQueue.invokeAndWait(() -> display.setFullScreenWindow(null));
				resize(surface, canvas);
			}
			if (display.isFullScreenSupported()) assertEquals(20, fullscreenFrames);
			for (int frameId = 160; frameId < 200; frameId++)
				assertEquals(VulkanFrameOutcome.SUBMITTED, renderer.render(surface.extent(), frameId));
			VulkanControlCounters running = renderer.counters();
			assertTrue(running.submitted() > 0);
			assertTrue(running.maxInFlight() <= 2);
			assertEquals(running.drawableAcquisitionRequests(), running.drawableAcquisitionCompletions());
			renderer.close();
			VulkanControlCounters closed = renderer.counters();
			renderer = null;
			assertEquals(0, closed.liveNativeObjects());
			assertEquals(closed.submitted(), closed.completed());
			assertEquals(closed.submitted(), closed.presentRequested());
			assertEquals(closed.drawableAcquisitionRequests(), closed.drawableAcquisitionCompletions());
			assertTrue(closed.resizeRebuilds() >= 4);
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

	@Test
	public void injectedLiveBackendFailuresRemainDeterministicallyCloseable() throws Exception
	{
		Assume.assumeTrue(Boolean.getBoolean("rlhd.spike.vulkan.integration"));
		Frame[] frameHolder = new Frame[1];
		Canvas[] canvasHolder = new Canvas[1];
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
		MacMetalSurface surface = new MacMetalSurface();
		try
		{
			surface.attach(canvas);
			resize(surface, canvas);
			SurfaceExtent extent = surface.extent();
			int width = Math.toIntExact(Math.round(extent.pixelWidth()));
			int height = Math.toIntExact(Math.round(extent.pixelHeight()));

			FailureAt partial = new FailureAt("partial-swapchain-children");
			LwjglVulkanBackend incomplete = new LwjglVulkanBackend(surface.metalLayerHandle(), width, height,
				Files.createTempFile("rlhd-vulkan-partial-", ".jsonl"), VulkanPresentMode.FIFO, true, partial);
			assertFalse(incomplete.ready());
			long[] partialCounters = new long[VulkanControlCounters.FIELD_COUNT];
			incomplete.close(partialCounters);
			assertEquals(0, new VulkanControlCounters(partialCounters).liveNativeObjects());
			assertEquals(1, new VulkanControlCounters(partialCounters).initErrors());

			for (String point : new String[] {"after-acquire", "after-record", "before-submit", "during-recreate"})
			{
				Path log = Files.createTempFile("rlhd-vulkan-failure-", ".jsonl");
				FailureAt injected = new FailureAt(point);
				LwjglVulkanBackend backend = new LwjglVulkanBackend(surface.metalLayerHandle(), width, height,
					log, VulkanPresentMode.FIFO, true, injected);
				assertTrue(backend.ready());
				VulkanControlRenderer renderer = new VulkanControlRenderer(backend, log);
				if ("during-recreate".equals(point)) renderer.setPresentMode(VulkanPresentMode.UNLOCKED);
				assertThrows(IllegalStateException.class, () -> renderer.render(extent, 1));
				renderer.close();
				VulkanControlCounters counters = renderer.counters();
				assertEquals(counters.drawableAcquisitionRequests(), counters.drawableAcquisitionCompletions());
				assertEquals(counters.submitted(), counters.completed());
				assertEquals(0, counters.liveNativeObjects());
				VulkanTimingJsonSchema.validateLog(Files.readAllLines(log));
			}

			Path closeLog = Files.createTempFile("rlhd-vulkan-close-failure-", ".jsonl");
			LwjglVulkanBackend closeBackend = new LwjglVulkanBackend(surface.metalLayerHandle(), width, height,
				closeLog, VulkanPresentMode.FIFO, true, new FailureAt("close-after-consumption"));
			VulkanControlRenderer closeRenderer = new VulkanControlRenderer(closeBackend, closeLog);
			assertEquals(VulkanFrameOutcome.SUBMITTED, closeRenderer.render(extent, 2));
			assertThrows(VulkanBackendCloseException.class, closeRenderer::close);
			assertEquals(0, closeRenderer.counters().liveNativeObjects());
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
}
