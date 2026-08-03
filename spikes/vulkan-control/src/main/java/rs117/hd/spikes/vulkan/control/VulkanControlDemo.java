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
		int seconds = Integer.parseInt(argument(args, "--seconds", "30"));
		Path log = Paths.get(argument(args, "--log", "build/spikes/vulkan-control/vulkan-control.jsonl"));
		boolean validation = Boolean.parseBoolean(argument(args, "--validation", "true"));
		Frame[] frameHolder = new Frame[1];
		Canvas[] canvasHolder = new Canvas[1];
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
		MacMetalSurface surface = new MacMetalSurface();
		VulkanControlRenderer renderer = null;
		Throwable primaryFailure = null;
		try
		{
			surface.attach(canvas);
			resize(surface, frame, canvas);
			renderer = new VulkanControlRenderer(surface, log, VulkanPresentMode.FIFO, validation);
			if (!renderer.runReadbackCheck()) throw new IllegalStateException("Vulkan triangle/UI GPU readback failed.");
			long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(seconds);
			long frameId = 0;
			boolean resized = false;
			boolean suspended = false;
			boolean restored = false;
			boolean unlocked = false;
			boolean fifo = false;
			boolean fullscreen = false;
			boolean fullscreenExited = false;
			while (System.nanoTime() < deadline && frame.isDisplayable())
			{
				long elapsed = seconds - TimeUnit.NANOSECONDS.toSeconds(Math.max(0, deadline - System.nanoTime()));
				if (!resized && elapsed >= Math.max(1, seconds / 6))
				{
					EventQueue.invokeAndWait(() -> frame.setSize(720, 420));
					resized = true;
				}
				if (!suspended && elapsed >= Math.max(2, seconds / 4))
				{
					surface.resize(0, 0, scale(canvas));
					suspended = true;
				}
				if (suspended && !restored && elapsed >= Math.max(3, seconds / 4 + 2))
				{
					resize(surface, frame, canvas);
					restored = true;
				}
				if (!unlocked && elapsed >= Math.max(1, seconds / 3))
				{
					renderer.setPresentMode(VulkanPresentMode.UNLOCKED);
					unlocked = true;
				}
				if (!fifo && elapsed >= Math.max(2, seconds * 2 / 3))
				{
					renderer.setPresentMode(VulkanPresentMode.FIFO);
					fifo = true;
				}
				if (!fullscreen && !fullscreenExited && display.isFullScreenSupported() && elapsed >= Math.max(2, seconds / 2))
				{
					EventQueue.invokeAndWait(() -> display.setFullScreenWindow(frame));
					fullscreen = true;
				}
				if (fullscreen && elapsed >= Math.max(3, seconds * 3 / 4))
				{
					EventQueue.invokeAndWait(() -> display.setFullScreenWindow(null));
					fullscreen = false;
					fullscreenExited = true;
				}
				if (!suspended || restored) resize(surface, frame, canvas);
				renderer.render(surface.extent(), frameId++);
				Thread.sleep(1);
			}
			System.out.println(renderer.counters());
		}
		catch (Exception | Error ex)
		{
			primaryFailure = ex;
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
			else if (cleanup instanceof Exception) throw (Exception) cleanup;
			else if (cleanup instanceof Error) throw (Error) cleanup;
		}
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
