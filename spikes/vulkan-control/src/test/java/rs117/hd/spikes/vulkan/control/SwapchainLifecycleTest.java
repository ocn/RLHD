package rs117.hd.spikes.vulkan.control;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SwapchainLifecycleTest
{
	@Test
	public void recreationWaitsForBothFrameSlotsAndDestroysOldGenerationOnce()
	{
		SwapchainLifecycle lifecycle = new SwapchainLifecycle(2);
		lifecycle.activate(1, 800, 600);
		assertEquals(1, lifecycle.acquire(0));
		assertEquals(1, lifecycle.acquire(1));
		lifecycle.requestRecreation(1024, 768);
		assertFalse(lifecycle.canRecreate());
		lifecycle.complete(0, 1);
		assertFalse(lifecycle.canRecreate());
		lifecycle.complete(1, 1);
		assertTrue(lifecycle.canRecreate());
		assertEquals(1, lifecycle.recreate(2));
		assertEquals(2, lifecycle.generation());
		assertEquals(1024, lifecycle.width());
		assertEquals(768, lifecycle.height());
	}

	@Test
	public void suspensionInvalidatesAcquisitionUntilPositiveRestore()
	{
		SwapchainLifecycle lifecycle = new SwapchainLifecycle(2);
		lifecycle.activate(4, 640, 480);
		lifecycle.suspend();
		assertFalse(lifecycle.canAcquire());
		lifecycle.requestRecreation(0, 480);
		assertFalse(lifecycle.canRecreate());
		lifecycle.requestRecreation(320, 240);
		assertTrue(lifecycle.canRecreate());
		lifecycle.recreate(5);
		assertTrue(lifecycle.canAcquire());
	}

	@Test(expected = IllegalStateException.class)
	public void closeRejectsLiveFrameResources()
	{
		SwapchainLifecycle lifecycle = new SwapchainLifecycle(2);
		lifecycle.activate(1, 640, 480);
		lifecycle.acquire(0);
		lifecycle.close();
	}
}
