package rs117.hd.spikes.macos;

import java.awt.Canvas;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class MacMetalSurfaceTest
{
	@Test
	public void supportsOneHundredAttachResizeSuspendRestoreDetachCycles()
	{
		FakeNativeSurfaceAccess nativeAccess = new FakeNativeSurfaceAccess();
		MacMetalSurface surface = new MacMetalSurface(nativeAccess, false, false);

		for (int cycle = 0; cycle < 100; cycle++)
		{
			surface.attach(new Canvas());
			surface.resize(320 + cycle, 180 + cycle, 2.0);
			SurfaceExtent firstSnapshot = surface.extent();
			assertFalse(firstSnapshot.suspended());
			assertEquals((320 + cycle) * 2.0, firstSnapshot.pixelWidth(), 0.0);
			assertEquals(0x117L, surface.metalLayerHandle());

			surface.resize(0, 180 + cycle, 2.0);
			assertTrue(surface.extent().suspended());
			assertFalse(firstSnapshot.suspended());

			surface.resize(640, 360, 1.5);
			assertFalse(surface.extent().suspended());
			assertEquals(960.0, surface.extent().pixelWidth(), 0.0);
			surface.detach();
			assertTrue(surface.extent().suspended());
		}

		assertEquals(1, nativeAccess.createCalls);
		assertEquals(100, nativeAccess.attachCalls);
		assertEquals(300, nativeAccess.resizeCalls);
		assertEquals(100, nativeAccess.detachCalls);
		surface.close();
		assertEquals(1, nativeAccess.closeCalls);
	}

	@Test
	public void rejectsDoubleAttachAndDetachedOperations()
	{
		FakeNativeSurfaceAccess nativeAccess = new FakeNativeSurfaceAccess();
		MacMetalSurface surface = new MacMetalSurface(nativeAccess, false, false);
		surface.attach(new Canvas());

		assertThrows(IllegalStateException.class, () -> surface.attach(new Canvas()));
		assertEquals(1, nativeAccess.attachCalls);
		surface.detach();
		assertThrows(IllegalStateException.class, () -> surface.resize(1, 1, 1.0));
		assertThrows(IllegalStateException.class, surface::metalLayerHandle);
		assertThrows(IllegalStateException.class, surface::detach);
		surface.close();
	}

	@Test
	public void rejectsEveryOperationAfterClose()
	{
		MacMetalSurface surface = new MacMetalSurface(new FakeNativeSurfaceAccess(), false, false);
		surface.attach(new Canvas());
		surface.close();

		assertThrows(IllegalStateException.class, () -> surface.attach(new Canvas()));
		assertThrows(IllegalStateException.class, () -> surface.resize(1, 1, 1.0));
		assertThrows(IllegalStateException.class, surface::extent);
		assertThrows(IllegalStateException.class, surface::metalLayerHandle);
		assertThrows(IllegalStateException.class, surface::detach);
		assertThrows(IllegalStateException.class, surface::close);
	}

	@Test
	public void preservesAttachedStateWhenCloseFailsAndAllowsRetry()
	{
		FakeNativeSurfaceAccess nativeAccess = new FakeNativeSurfaceAccess();
		nativeAccess.failCloseOnce = true;
		MacMetalSurface surface = new MacMetalSurface(nativeAccess, false, false);
		surface.attach(new Canvas());
		surface.resize(640, 360, 2.0);
		SurfaceExtent extentBeforeClose = surface.extent();

		assertThrows(IllegalStateException.class, surface::close);
		assertEquals(extentBeforeClose, surface.extent());
		assertEquals(0x117L, surface.metalLayerHandle());
		assertTrue(nativeAccess.attached);
		assertEquals(1, nativeAccess.closeCalls);

		surface.resize(320, 180, 1.5);
		assertEquals(SurfaceExtent.of(320, 180, 1.5), surface.extent());
		surface.close();
		assertEquals(2, nativeAccess.closeCalls);
		assertThrows(IllegalStateException.class, surface::extent);
		assertThrows(IllegalStateException.class, surface::metalLayerHandle);
		assertThrows(IllegalStateException.class, surface::close);
	}

	@Test
	public void validatesCanvasBeforeCreatingNativeState()
	{
		FakeNativeSurfaceAccess nativeAccess = new FakeNativeSurfaceAccess();
		MacMetalSurface surface = new MacMetalSurface(nativeAccess, false, true);

		assertThrows(IllegalArgumentException.class, () -> surface.attach(new Canvas()));
		assertEquals(0, nativeAccess.createCalls);
		surface.close();
	}

	@Test
	public void rejectsAttachWhileCallerHoldsAwtTreeLock()
	{
		FakeNativeSurfaceAccess nativeAccess = new FakeNativeSurfaceAccess();
		MacMetalSurface surface = new MacMetalSurface(nativeAccess, false, false);
		Canvas canvas = new Canvas();

		synchronized (canvas.getTreeLock())
		{
			assertThrows(IllegalStateException.class, () -> surface.attach(canvas));
		}
		assertEquals(0, nativeAccess.createCalls);
		surface.close();
	}

	@Test
	public void rejectsUnsupportedPlatformBeforeInitializingNativeBindings()
	{
		String originalOsName = System.getProperty("os.name");
		System.setProperty("os.name", "Linux");
		try
		{
			MacMetalSurface surface = new MacMetalSurface(null, true, false);
			assertThrows(UnsupportedOperationException.class, () -> surface.attach(new Canvas()));
			surface.close();
		}
		finally
		{
			if (originalOsName == null)
			{
				System.clearProperty("os.name");
			}
			else
			{
				System.setProperty("os.name", originalOsName);
			}
		}
	}

	private static final class FakeNativeSurfaceAccess implements NativeSurfaceAccess
	{
		private int createCalls;
		private int attachCalls;
		private int resizeCalls;
		private int detachCalls;
		private int closeCalls;
		private boolean attached;
		private boolean failCloseOnce;

		@Override
		public long create()
		{
			createCalls++;
			return 1L;
		}

		@Override
		public void attach(long stateHandle, Canvas canvas)
		{
			assertEquals(1L, stateHandle);
			assertFalse(attached);
			attached = true;
			attachCalls++;
		}

		@Override
		public void resize(long stateHandle, SurfaceExtent extent)
		{
			assertEquals(1L, stateHandle);
			assertTrue(attached);
			resizeCalls++;
		}

		@Override
		public long layerHandle(long stateHandle)
		{
			assertEquals(1L, stateHandle);
			assertTrue(attached);
			return 0x117L;
		}

		@Override
		public void assertLayerState(long stateHandle, SurfaceExtent extent)
		{
			assertEquals(1L, stateHandle);
			assertTrue(attached);
		}

		@Override
		public void detach(long stateHandle)
		{
			assertEquals(1L, stateHandle);
			assertTrue(attached);
			attached = false;
			detachCalls++;
		}

		@Override
		public void close(long stateHandle)
		{
			assertEquals(1L, stateHandle);
			closeCalls++;
			if (failCloseOnce)
			{
				failCloseOnce = false;
				throw new IllegalStateException("Simulated native close failure.");
			}
			attached = false;
		}
	}
}
