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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

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
			for (int frameId = 0; frameId < 60; frameId++) renderer.render(surface.extent(), frameId);

			EventQueue.invokeAndWait(() -> frame.setSize(777, 431));
			resize(surface, canvas);
			for (int frameId = 60; frameId < 100; frameId++) renderer.render(surface.extent(), frameId);

			surface.resize(0, 0, scale(canvas));
			assertEquals(VulkanFrameOutcome.SKIPPED_SUSPENDED, renderer.render(surface.extent(), 100));
			resize(surface, canvas);
			renderer.setPresentMode(VulkanPresentMode.UNLOCKED);
			for (int frameId = 101; frameId < 140; frameId++) renderer.render(surface.extent(), frameId);
			renderer.setPresentMode(VulkanPresentMode.FIFO);

			GraphicsDevice display = frame.getGraphicsConfiguration().getDevice();
			if (display.isFullScreenSupported())
			{
				EventQueue.invokeAndWait(() -> display.setFullScreenWindow(frame));
				resize(surface, canvas);
				for (int frameId = 140; frameId < 160; frameId++) renderer.render(surface.extent(), frameId);
				EventQueue.invokeAndWait(() -> display.setFullScreenWindow(null));
				resize(surface, canvas);
			}
			for (int frameId = 160; frameId < 200; frameId++) renderer.render(surface.extent(), frameId);
			VulkanControlCounters running = renderer.counters();
			assertTrue(running.submitted() > 0);
			assertTrue(running.maxInFlight() <= 2);
			assertEquals(running.drawableAcquisitionRequests(), running.drawableAcquisitionCompletions());
			renderer.close();
			assertEquals(0, renderer.counters().liveNativeObjects());
			assertFalse(renderer.counters().hasErrors());
			renderer = null;
			surface.detach();
			surface.close();
			List<String> lines = Files.readAllLines(log);
			VulkanTimingJsonSchema.validateLog(lines);
			assertTrue(lines.get(0).contains("\"validation_requested\":true"));
			assertTrue(lines.get(0).contains("\"validation_enabled\":true"));
			assertTrue(lines.get(0).contains("\"standard_queue_present\":true"));
		}
		finally
		{
			if (renderer != null) renderer.close();
			try { surface.detach(); } catch (IllegalStateException ignored) {}
			try { surface.close(); } catch (IllegalStateException ignored) {}
			EventQueue.invokeAndWait(frame::dispose);
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
