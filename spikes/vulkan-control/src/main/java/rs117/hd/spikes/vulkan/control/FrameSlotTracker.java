package rs117.hd.spikes.vulkan.control;

import java.util.Arrays;

public final class FrameSlotTracker
{
	private final long[] serials;
	private final long[] generations;
	private final boolean[] busy;
	private long nextSerial = 1;

	public FrameSlotTracker(int slotCount)
	{
		if (slotCount != 2) throw new IllegalArgumentException("The control spike requires exactly two frame slots.");
		serials = new long[slotCount];
		generations = new long[slotCount];
		busy = new boolean[slotCount];
	}

	public synchronized long submit(int slot, long generation)
	{
		checkSlot(slot);
		if (generation <= 0) throw new IllegalArgumentException("Generation must be positive.");
		if (busy[slot]) throw new IllegalStateException("Frame slot is still in flight.");
		busy[slot] = true;
		generations[slot] = generation;
		return serials[slot] = nextSerial++;
	}

	public synchronized long complete(int slot, long serial)
	{
		checkSlot(slot);
		if (!busy[slot]) throw new IllegalStateException("Frame slot is not in flight.");
		if (serials[slot] != serial) throw new IllegalArgumentException("Stale fence completion.");
		busy[slot] = false;
		return generations[slot];
	}

	public synchronized int inFlight()
	{
		int count = 0;
		for (boolean value : busy) if (value) count++;
		return count;
	}

	public synchronized long retireGeneration(long generation)
	{
		if (inFlight() != 0) throw new IllegalStateException("Cannot retire resources while frames are in flight.");
		for (long value : generations) if (value > generation) throw new IllegalStateException("Newer generation is still referenced.");
		Arrays.fill(generations, 0);
		return generation;
	}

	private void checkSlot(int slot)
	{
		if (slot < 0 || slot >= busy.length) throw new IndexOutOfBoundsException("Invalid frame slot.");
	}
}
