package rs117.hd.spikes.macos;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class SurfaceExtentTest
{
	@Test
	public void calculatesLogicalAndPixelExtent()
	{
		SurfaceExtent extent = SurfaceExtent.of(321, 181, 1.5);

		assertEquals(321, extent.logicalWidth());
		assertEquals(181, extent.logicalHeight());
		assertEquals(481.5, extent.pixelWidth(), 0.0);
		assertEquals(271.5, extent.pixelHeight(), 0.0);
		assertEquals(1.5, extent.backingScale(), 0.0);
		assertFalse(extent.suspended());
	}

	@Test
	public void zeroInEitherDimensionSuspends()
	{
		assertTrue(SurfaceExtent.of(0, 480, 2.0).suspended());
		assertTrue(SurfaceExtent.of(640, 0, 2.0).suspended());
		assertTrue(SurfaceExtent.of(0, 0, 2.0).suspended());
		assertFalse(SurfaceExtent.of(640, 480, 2.0).suspended());
	}

	@Test
	public void rejectsInvalidExtentValues()
	{
		assertThrows(IllegalArgumentException.class, () -> SurfaceExtent.of(-1, 1, 1.0));
		assertThrows(IllegalArgumentException.class, () -> SurfaceExtent.of(1, -1, 1.0));
		assertThrows(IllegalArgumentException.class, () -> SurfaceExtent.of(1, 1, 0.0));
		assertThrows(IllegalArgumentException.class, () -> SurfaceExtent.of(1, 1, Double.NaN));
		assertThrows(IllegalArgumentException.class, () -> SurfaceExtent.of(1, 1, Double.POSITIVE_INFINITY));
		assertThrows(IllegalArgumentException.class, () -> SurfaceExtent.of(Integer.MAX_VALUE, 1, Double.MAX_VALUE));
	}
}
