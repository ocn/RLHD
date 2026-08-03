package rs117.hd.spikes.vulkan.control;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class FrameSlotTrackerTest
{
	@Test
	public void twoFramesInFlightMustCompleteBeforeResourcesAreRetired()
	{
		FrameSlotTracker tracker = new FrameSlotTracker(2);
		long first = tracker.submit(0, 9);
		long second = tracker.submit(1, 9);
		assertEquals(2, tracker.inFlight());
		tracker.complete(0, first);
		tracker.complete(1, second);
		assertEquals(0, tracker.inFlight());
		assertEquals(9, tracker.retireGeneration(9));
	}

	@Test(expected = IllegalStateException.class)
	public void frameSlotCannotBeReusedBeforeFenceCompletion()
	{
		FrameSlotTracker tracker = new FrameSlotTracker(2);
		tracker.submit(0, 1);
		tracker.submit(0, 1);
	}

	@Test(expected = IllegalArgumentException.class)
	public void staleFenceCompletionIsRejected()
	{
		FrameSlotTracker tracker = new FrameSlotTracker(2);
		long serial = tracker.submit(0, 1);
		tracker.complete(0, serial + 1);
	}
}
