package rs117.hd.spikes.vulkan.control;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class FrameSynchronizationTest
{
	@Test
	public void acquisitionFailureLeavesFenceSignaledAndImageSemaphoresFollowImages()
	{
		FrameSynchronization synchronization = new FrameSynchronization(2, 3);
		synchronization.beginAcquire(0);
		synchronization.acquisitionFailed(0);
		assertTrue(synchronization.fenceSignaled(0));

		synchronization.beginAcquire(0);
		assertEquals(2, synchronization.acquired(0, 2));
		synchronization.resetFenceAndSubmit(0);
		assertFalse(synchronization.fenceSignaled(0));
		synchronization.complete(0);

		synchronization.beginAcquire(1);
		assertEquals(2, synchronization.acquired(1, 2));
		synchronization.beginAcquire(0);
		assertThrows(IllegalStateException.class, () -> synchronization.acquired(0, 2));
	}

	@Test
	public void resetIsRejectedBeforeSuccessfulAcquisition()
	{
		FrameSynchronization synchronization = new FrameSynchronization(2, 3);
		synchronization.beginAcquire(0);
		assertThrows(IllegalStateException.class, () -> synchronization.resetFenceAndSubmit(0));
	}
}
