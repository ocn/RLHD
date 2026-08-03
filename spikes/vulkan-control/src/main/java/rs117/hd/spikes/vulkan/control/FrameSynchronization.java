package rs117.hd.spikes.vulkan.control;

import java.util.Arrays;

public final class FrameSynchronization
{
	private enum State { IDLE, ACQUIRING, ACQUIRED, SUBMITTED }

	private final State[] states;
	private final boolean[] fenceSignaled;
	private final int[] acquiredImages;
	private final int imageCount;

	public FrameSynchronization(int frameSlots, int imageCount)
	{
		if (frameSlots != 2) throw new IllegalArgumentException("The control spike requires exactly two frame slots.");
		if (imageCount < 1) throw new IllegalArgumentException("At least one swapchain image is required.");
		this.imageCount = imageCount;
		states = new State[frameSlots];
		Arrays.fill(states, State.IDLE);
		fenceSignaled = new boolean[frameSlots];
		Arrays.fill(fenceSignaled, true);
		acquiredImages = new int[frameSlots];
		Arrays.fill(acquiredImages, -1);
	}

	public synchronized void beginAcquire(int slot)
	{
		checkSlot(slot);
		if (states[slot] != State.IDLE || !fenceSignaled[slot])
		{
			throw new IllegalStateException("Frame slot is not ready for acquisition.");
		}
		states[slot] = State.ACQUIRING;
	}

	public synchronized void acquisitionFailed(int slot)
	{
		requireState(slot, State.ACQUIRING);
		states[slot] = State.IDLE;
	}

	public synchronized int acquired(int slot, int imageIndex)
	{
		requireState(slot, State.ACQUIRING);
		if (imageIndex < 0 || imageIndex >= imageCount) throw new IndexOutOfBoundsException("Invalid swapchain image.");
		for (int index = 0; index < states.length; index++)
		{
			if (index != slot && acquiredImages[index] == imageIndex && states[index] != State.IDLE)
			{
				throw new IllegalStateException("Swapchain image is already associated with another frame.");
			}
		}
		acquiredImages[slot] = imageIndex;
		states[slot] = State.ACQUIRED;
		return imageIndex;
	}

	public synchronized void resetFenceAndSubmit(int slot)
	{
		requireState(slot, State.ACQUIRED);
		fenceSignaled[slot] = false;
		states[slot] = State.SUBMITTED;
	}

	public synchronized void complete(int slot)
	{
		requireState(slot, State.SUBMITTED);
		fenceSignaled[slot] = true;
		states[slot] = State.IDLE;
		acquiredImages[slot] = -1;
	}

	public synchronized boolean fenceSignaled(int slot)
	{
		checkSlot(slot);
		return fenceSignaled[slot];
	}

	private void requireState(int slot, State expected)
	{
		checkSlot(slot);
		if (states[slot] != expected) throw new IllegalStateException("Unexpected frame synchronization state.");
	}

	private void checkSlot(int slot)
	{
		if (slot < 0 || slot >= states.length) throw new IndexOutOfBoundsException("Invalid frame slot.");
	}
}
