package rs117.hd.spikes.macos;

import java.awt.Canvas;
import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.geom.AffineTransform;
import org.junit.Assume;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

public class MacMetalSurfaceIntegrationTest
{
	@Test
	public void attachesAndDetachesHeadfulJawtCanvasRepeatedly() throws Exception
	{
		Assume.assumeTrue(MacMetalSurface.isMacOs());
		Assume.assumeTrue(Boolean.getBoolean("rlhd.spike.macos.integration"));

		Frame[] frameHolder = new Frame[1];
		Canvas[] canvasHolder = new Canvas[1];
		EventQueue.invokeAndWait(() ->
		{
			Frame frame = new Frame("RLHD macOS surface integration spike");
			Canvas canvas = new Canvas();
			frame.add(canvas);
			frame.setSize(320, 180);
			frame.setVisible(true);
			frameHolder[0] = frame;
			canvasHolder[0] = canvas;
		});

		MacMetalSurface surface = new MacMetalSurface();
		try
		{
			Canvas canvas = canvasHolder[0];
			AffineTransform transform = canvas.getGraphicsConfiguration().getDefaultTransform();
			double scale = transform.getScaleX();
			for (int cycle = 0; cycle < 100; cycle++)
			{
				surface.attach(canvas);
				assertNotEquals(0L, surface.metalLayerHandle());
				surface.resize(320, 180, scale);
				surface.assertLayerStateForTesting();
				assertEquals(320 * scale, surface.extent().pixelWidth(), 0.0);
				assertFalse(surface.extent().suspended());
				surface.resize(0, 0, scale);
				surface.assertLayerStateForTesting();
				assertTrue(surface.extent().suspended());
				surface.resize(320, 180, scale);
				surface.assertLayerStateForTesting();
				assertFalse(surface.extent().suspended());
				surface.detach();
			}
		}
		finally
		{
			surface.close();
			EventQueue.invokeAndWait(frameHolder[0]::dispose);
		}
	}
}
