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
import rs117.hd.spikes.macos.SurfaceExtent;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
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
			assertTrue(finalCounters.presentationCallbacks() > 0);
			assertEquals(finalCounters.drawableAcquisitionRequests(), finalCounters.drawableAcquisitionCompletions());

			List<String> lines = Files.readAllLines(log);
			assertEquals(242, lines.size());
			TimingJsonSchema.validateLog(lines);
			assertTrue(lines.get(0).contains("\"type\":\"run_start\""));
			assertTrue(lines.get(lines.size() - 1).contains("\"type\":\"run_end\""));
			assertTrue(lines.get(lines.size() - 1).contains("\"live_native_objects\":0"));
			assertTrue(lines.stream().anyMatch(line -> line.contains("\"callback_status\":\"presented\"")));
			assertTrue(lines.stream().anyMatch(line -> line.contains("\"requested_present_mode\":\"unlocked\"")));
			assertTrue(lines.stream().anyMatch(line -> line.contains("\"requested_present_mode\":\"fifo-like\"")));

			assertRetainedLayerSafety(surface, canvas);
			assertFailureSeams(surface);
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

	private static void assertRetainedLayerSafety(MacMetalSurface surface, Canvas canvas) throws Exception
	{
		Path log = Files.createTempFile("rlhd-metal-retained-layer-", ".jsonl");
		MetalControlRenderer renderer = renderer(surface, log, PresentMode.FIFO_LIKE, "stalled-drawable");
		surface.detach();
		assertEquals(FrameOutcome.SKIPPED_IN_FLIGHT, renderer.render(SurfaceExtent.of(64, 64, 1.0), 800));
		renderer.close();
		assertEquals(0, renderer.counters().liveNativeObjects());
		TimingJsonSchema.validateLog(Files.readAllLines(log));
		surface.attach(canvas);
		resize(surface, canvas.getWidth(), canvas.getHeight(), canvas);
	}

	private static void assertFailureSeams(MacMetalSurface surface) throws Exception
	{
		Path closeLog = Files.createTempFile("rlhd-metal-close-retry-", ".jsonl");
		MetalControlRenderer closeRetry = renderer(surface, closeLog, PresentMode.FIFO_LIKE, "close-preconsume");
		assertThrows(IllegalStateException.class, closeRetry::close);
		closeRetry.render(surface.extent(), 900);
		closeRetry.close();
		assertEquals(0, closeRetry.counters().liveNativeObjects());
		TimingJsonSchema.validateLog(Files.readAllLines(closeLog));

		Path stalledLog = Files.createTempFile("rlhd-metal-stalled-", ".jsonl");
		MetalControlRenderer stalled = renderer(surface, stalledLog, PresentMode.FIFO_LIKE, "stalled-drawable");
		long started = System.nanoTime();
		FrameOutcome stalledOutcome = stalled.render(surface.extent(), 1000);
		long elapsedMillis = (System.nanoTime() - started) / 1_000_000;
		assertEquals(FrameOutcome.SKIPPED_IN_FLIGHT, stalledOutcome);
		assertTrue("render blocked for " + elapsedMillis + "ms", elapsedMillis < 75);
		stalled.close();
		assertEquals(1, stalled.counters().drawableAcquisitionRequests());
		assertEquals(1, stalled.counters().drawableAcquisitionCompletions());
		TimingJsonSchema.validateLog(Files.readAllLines(stalledLog));

		Path modeLog = Files.createTempFile("rlhd-metal-mode-", ".jsonl");
		MetalControlRenderer ignoredMode = renderer(surface, modeLog, PresentMode.UNLOCKED, "ignored-present-mode");
		ignoredMode.close();
		assertTrue(ignoredMode.counters().presentModeDivergences() > 0);
		List<String> modeLines = Files.readAllLines(modeLog);
		TimingJsonSchema.validateLog(modeLines);
		assertTrue(modeLines.get(0).contains("\"requested_present_mode\":\"unlocked\""));
		assertTrue(modeLines.get(0).contains("\"effective_present_mode\":\"fifo-like\""));

		Path unsupportedLog = Files.createTempFile("rlhd-metal-unsupported-", ".jsonl");
		MetalControlRenderer unsupported = renderer(surface, unsupportedLog, PresentMode.FIFO_LIKE, "unsupported-present-callback");
		submitOne(unsupported, surface, 2000);
		unsupported.close();
		List<String> unsupportedLines = Files.readAllLines(unsupportedLog);
		TimingJsonSchema.validateLog(unsupportedLines);
		assertTrue(unsupportedLines.stream().anyMatch(line -> line.contains("\"callback_status\":\"unsupported\"")));

		Path droppedLog = Files.createTempFile("rlhd-metal-dropped-", ".jsonl");
		MetalControlRenderer dropped = renderer(surface, droppedLog, PresentMode.FIFO_LIKE, "dropped-present-callback");
		submitOne(dropped, surface, 3000);
		dropped.close();
		List<String> droppedLines = Files.readAllLines(droppedLog);
		TimingJsonSchema.validateLog(droppedLines);
		assertTrue(droppedLines.stream().anyMatch(line -> line.contains("\"callback_status\":\"dropped\"")));

		Path bypassPresentLog = Files.createTempFile("rlhd-metal-no-present-", ".jsonl");
		MetalControlRenderer bypassPresent = renderer(surface, bypassPresentLog, PresentMode.FIFO_LIKE, "bypass-present");
		submitOne(bypassPresent, surface, 4000);
		bypassPresent.close();
		assertTrue(bypassPresent.counters().presentationTimeouts() > 0);
		List<String> bypassPresentLines = Files.readAllLines(bypassPresentLog);
		TimingJsonSchema.validateLog(bypassPresentLines);
		assertTrue(bypassPresentLines.stream().anyMatch(line -> line.contains("\"callback_status\":\"callback-timeout\"")));

		Path bypassUploadLog = Files.createTempFile("rlhd-metal-no-upload-", ".jsonl");
		MetalControlRenderer bypassUpload = renderer(surface, bypassUploadLog, PresentMode.FIFO_LIKE, "bypass-upload");
		assertFalse(bypassUpload.runReadbackCheck());
		bypassUpload.close();
		TimingJsonSchema.validateLog(Files.readAllLines(bypassUploadLog));
	}

	private static MetalControlRenderer renderer(MacMetalSurface surface, Path log, PresentMode mode, String failure)
	{
		return new MetalControlRenderer(MetalControlNative.INSTANCE, surface.metalLayerHandle(), log, mode, failure);
	}

	private static void submitOne(MetalControlRenderer renderer, MacMetalSurface surface, long firstFrame) throws Exception
	{
		for (int attempt = 0; attempt < 100 && renderer.counters().submitted() == 0; attempt++)
		{
			renderer.render(surface.extent(), firstFrame + attempt);
			Thread.sleep(5);
		}
		assertTrue(renderer.counters().submitted() > 0);
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
