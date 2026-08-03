package rs117.hd.spikes.vulkan.control;

public final class SwapchainLifecycle
{
	private enum Phase { EMPTY, ACTIVE, RECREATE_PENDING, SUSPENDED, CLOSED }

	private final long[] slotGenerations;
	private Phase phase = Phase.EMPTY;
	private long generation;
	private int width;
	private int height;
	private int requestedWidth;
	private int requestedHeight;

	public SwapchainLifecycle(int frameSlots)
	{
		if (frameSlots != 2) throw new IllegalArgumentException("The control spike requires exactly two frame slots.");
		slotGenerations = new long[frameSlots];
	}

	public synchronized void activate(long generation, int width, int height)
	{
		if (phase != Phase.EMPTY) throw new IllegalStateException("Swapchain is already initialized.");
		validatePositive(generation, width, height);
		this.generation = generation;
		this.width = width;
		this.height = height;
		phase = Phase.ACTIVE;
	}

	public synchronized long acquire(int slot)
	{
		if (!canAcquire()) throw new IllegalStateException("Swapchain cannot acquire an image.");
		checkSlot(slot);
		if (slotGenerations[slot] != 0) throw new IllegalStateException("Frame slot still owns swapchain resources.");
		return slotGenerations[slot] = generation;
	}

	public synchronized void complete(int slot, long completedGeneration)
	{
		checkSlot(slot);
		if (slotGenerations[slot] != completedGeneration) throw new IllegalArgumentException("Stale generation completion.");
		slotGenerations[slot] = 0;
	}

	public synchronized void requestRecreation(int width, int height)
	{
		ensureOpen();
		requestedWidth = width;
		requestedHeight = height;
		phase = width <= 0 || height <= 0 ? Phase.SUSPENDED : Phase.RECREATE_PENDING;
	}

	public synchronized void suspend()
	{
		requestRecreation(0, 0);
	}

	public synchronized boolean canAcquire()
	{
		return phase == Phase.ACTIVE;
	}

	public synchronized boolean canRecreate()
	{
		if (phase != Phase.RECREATE_PENDING) return false;
		for (long slot : slotGenerations) if (slot != 0) return false;
		return true;
	}

	public synchronized long recreate(long nextGeneration)
	{
		if (!canRecreate()) throw new IllegalStateException("Swapchain recreation is not ready.");
		if (nextGeneration <= generation) throw new IllegalArgumentException("Generation must advance.");
		long retired = generation;
		generation = nextGeneration;
		width = requestedWidth;
		height = requestedHeight;
		phase = Phase.ACTIVE;
		return retired;
	}

	public synchronized void close()
	{
		ensureOpen();
		for (long slot : slotGenerations) if (slot != 0) throw new IllegalStateException("Cannot close with live frame resources.");
		phase = Phase.CLOSED;
		generation = 0;
		width = 0;
		height = 0;
	}

	public synchronized long generation() { return generation; }
	public synchronized int width() { return width; }
	public synchronized int height() { return height; }

	private void ensureOpen()
	{
		if (phase == Phase.CLOSED) throw new IllegalStateException("Swapchain lifecycle is closed.");
	}

	private void checkSlot(int slot)
	{
		if (slot < 0 || slot >= slotGenerations.length) throw new IndexOutOfBoundsException("Invalid frame slot.");
	}

	private static void validatePositive(long generation, int width, int height)
	{
		if (generation <= 0 || width <= 0 || height <= 0) throw new IllegalArgumentException("Generation and extent must be positive.");
	}
}
