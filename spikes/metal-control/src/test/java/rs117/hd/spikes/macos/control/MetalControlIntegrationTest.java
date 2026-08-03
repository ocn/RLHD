package rs117.hd.spikes.macos.control;

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

public class MetalControlIntegrationTest
{
	@Test
	public void presentsChangingUiAcrossLifecycleAndDrainsBeforeSurfaceDetach() throws Exception
	{
		Assume.assumeTrue(MacMetalSurface.isMacOs());
		Assume.assumeTrue(Boolean.getBoolean("rlhd.spike.metal.integration"));
		Path log = Files.createTempFile("rlhd-metal-control-", ".jsonl");
		Frame[] frameHolder = new Frame[1];
		Canvas[] canvasHolder = new Canvas[1];
		EventQueue.invokeAndWait(() ->
		{
			Frame frame = new Frame("RLHD direct-Metal smoke");
			Canvas canvas = new Canvas();
			frame.add(canvas);
			frame.setSize(360, 240);
			frame.setVisible(true);
			frameHolder[0] = frame;
			canvasHolder[0] = canvas;
		});

		Frame frame = frameHolder[0];
		Canvas canvas = canvasHolder[0];
		GraphicsDevice display = frame.getGraphicsConfiguration().getDevice();
		MacMetalSurface surface = new MacMetalSurface();
		MetalControlRenderer renderer = null;
		boolean fullscreen = false;
		try
		{
			surface.attach(canvas);
			resize(surface, canvas.getWidth(), canvas.getHeight(), canvas);
			renderer = new MetalControlRenderer(surface, log, PresentMode.FIFO_LIKE);
			assertTrue(renderer.runReadbackCheck());
			for (int frameId = 0; frameId < 240; frameId++)
			{
				if (frameId == 40)
				{
					EventQueue.invokeAndWait(() -> frame.setSize(480, 300));
					resize(surface, canvas.getWidth(), canvas.getHeight(), canvas);
				}
				else if (frameId == 80)
				{
					surface.resize(0, 0, scale(canvas));
				}
				else if (frameId == 90)
				{
					resize(surface, canvas.getWidth(), canvas.getHeight(), canvas);
				}
				else if (frameId == 110)
				{
					renderer.setPresentMode(PresentMode.UNLOCKED);
				}
				else if (frameId == 150 && display.isFullScreenSupported())
				{
					EventQueue.invokeAndWait(() -> display.setFullScreenWindow(frame));
					fullscreen = true;
					resize(surface, canvas.getWidth(), canvas.getHeight(), canvas);
				}
				else if (frameId == 170 && fullscreen)
				{
					EventQueue.invokeAndWait(() -> display.setFullScreenWindow(null));
					fullscreen = false;
					resize(surface, canvas.getWidth(), canvas.getHeight(), canvas);
				}
				else if (frameId == 190)
				{
					renderer.setPresentMode(PresentMode.FIFO_LIKE);
				}
				renderer.render(surface.extent(), frameId);
				Thread.sleep(2);
			}
			renderer.close();
			MetalControlCounters finalCounters = renderer.counters();
			assertFalse(finalCounters.hasErrors());
			assertTrue(finalCounters.submitted() > 100);
			assertEquals(finalCounters.submitted(), finalCounters.completed());
			assertEquals(0, finalCounters.inFlight());
			assertEquals(0, finalCounters.liveNativeObjects());
			assertTrue(finalCounters.maxInFlight() <= 3);
			assertTrue(finalCounters.skippedSuspended() >= 10);
			assertTrue(finalCounters.resizeRebuilds() >= 3);

			List<String> lines = Files.readAllLines(log);
			assertTrue(lines.size() >= 3);
			for (String line : lines) TimingJsonSchema.validateLine(line);
			assertTrue(lines.get(0).contains("\"type\":\"run_start\""));
			assertTrue(lines.get(lines.size() - 1).contains("\"type\":\"run_end\""));
			assertTrue(lines.get(lines.size() - 1).contains("\"live_native_objects\":0"));
		}
		finally
		{
			if (fullscreen) EventQueue.invokeAndWait(() -> display.setFullScreenWindow(null));
			if (renderer != null && renderer.counters().liveNativeObjects() != 0) renderer.close();
			try
			{
				surface.detach();
			}
			finally
			{
				surface.close();
				EventQueue.invokeAndWait(frame::dispose);
			}
		}
	}

	private static void resize(MacMetalSurface surface, int width, int height, Canvas canvas)
	{
		surface.resize(Math.max(0, width), Math.max(0, height), scale(canvas));
	}

	private static double scale(Canvas canvas)
	{
		AffineTransform transform = canvas.getGraphicsConfiguration().getDefaultTransform();
		return transform.getScaleX();
	}
}
