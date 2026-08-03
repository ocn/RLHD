package rs117.hd.spikes.macos.control;

import java.awt.Canvas;
import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.GraphicsDevice;
import java.awt.geom.AffineTransform;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;
import rs117.hd.spikes.macos.MacMetalSurface;

public final class MetalControlDemo
{
	private MetalControlDemo()
	{
	}

	public static void main(String[] args) throws Exception
	{
		int seconds = integerArgument(args, "--seconds", 30);
		Path log = Paths.get(stringArgument(args, "--log", "build/spikes/metal-control/metal-control.jsonl"));
		Frame[] frameHolder = new Frame[1];
		Canvas[] canvasHolder = new Canvas[1];
		EventQueue.invokeAndWait(() ->
		{
			Frame frame = new Frame("RLHD direct-Metal control spike");
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
		MacMetalSurface surface = new MacMetalSurface();
		MetalControlRenderer renderer = null;
		GraphicsDevice display = frame.getGraphicsConfiguration().getDevice();
		try
		{
			surface.attach(canvas);
			resizeSurface(surface, frame, canvas);
			renderer = new MetalControlRenderer(surface, log, PresentMode.FIFO_LIKE);
			if (!renderer.runReadbackCheck())
			{
				throw new IllegalStateException("Offscreen triangle/UI BGRA readback check failed.");
			}
			long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(seconds);
			long frameId = 0;
			boolean resized = false;
			boolean forcedSuspended = false;
			boolean restored = false;
			boolean unlockedApplied = false;
			boolean fifoRestored = false;
			boolean fullscreen = false;
			boolean fullscreenExited = false;
			while (System.nanoTime() < deadline && frame.isDisplayable())
			{
				long elapsed = TimeUnit.NANOSECONDS.toSeconds(TimeUnit.SECONDS.toNanos(seconds) - (deadline - System.nanoTime()));
				if (!resized && elapsed >= Math.max(1, seconds / 6))
				{
					EventQueue.invokeAndWait(() -> frame.setSize(720, 420));
					resized = true;
				}
				if (!forcedSuspended && elapsed >= Math.max(2, seconds / 4))
				{
					surface.resize(0, 0, scale(canvas));
					forcedSuspended = true;
				}
				if (forcedSuspended && !restored && elapsed >= Math.max(3, seconds / 4 + 2))
				{
					resizeSurface(surface, frame, canvas);
					restored = true;
				}
				if (!unlockedApplied && elapsed >= Math.max(1, seconds / 3))
				{
					renderer.setPresentMode(PresentMode.UNLOCKED);
					unlockedApplied = true;
				}
				if (!fifoRestored && elapsed >= Math.max(2, seconds * 2 / 3))
				{
					renderer.setPresentMode(PresentMode.FIFO_LIKE);
					fifoRestored = true;
				}
				if (!fullscreen && !fullscreenExited && display.isFullScreenSupported() && elapsed >= Math.max(2, seconds / 2))
				{
					EventQueue.invokeAndWait(() -> display.setFullScreenWindow(frame));
					fullscreen = true;
				}
				if (fullscreen && !fullscreenExited && elapsed >= Math.max(3, seconds * 3 / 4))
				{
					EventQueue.invokeAndWait(() -> display.setFullScreenWindow(null));
					fullscreen = false;
					fullscreenExited = true;
				}
				if (!forcedSuspended || restored) resizeSurface(surface, frame, canvas);
				renderer.render(surface.extent(), frameId++);
				Thread.sleep(1);
			}
			if (fullscreen)
			{
				EventQueue.invokeAndWait(() -> display.setFullScreenWindow(null));
				resizeSurface(surface, frame, canvas);
			}
			MetalControlCounters beforeClose = renderer.counters();
			System.out.println("requested/effective presentation behavior is recorded in " + log.toAbsolutePath());
			System.out.println(beforeClose);
		}
		finally
		{
			if (renderer != null)
			{
				renderer.close();
				System.out.println("after close: " + renderer.counters());
			}
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

	private static void resizeSurface(MacMetalSurface surface, Frame frame, Canvas canvas)
	{
		double scale = scale(canvas);
		if ((frame.getExtendedState() & Frame.ICONIFIED) != 0)
		{
			surface.resize(0, 0, scale);
			return;
		}
		surface.resize(Math.max(0, canvas.getWidth()), Math.max(0, canvas.getHeight()), scale);
	}

	private static double scale(Canvas canvas)
	{
		AffineTransform transform = canvas.getGraphicsConfiguration().getDefaultTransform();
		return transform.getScaleX();
	}

	private static int integerArgument(String[] args, String name, int defaultValue)
	{
		return Integer.parseInt(stringArgument(args, name, Integer.toString(defaultValue)));
	}

	private static String stringArgument(String[] args, String name, String defaultValue)
	{
		for (int index = 0; index + 1 < args.length; index++)
		{
			if (name.equals(args[index])) return args[index + 1];
		}
		return defaultValue;
	}
}
