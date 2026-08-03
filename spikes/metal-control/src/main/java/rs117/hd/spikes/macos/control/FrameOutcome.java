package rs117.hd.spikes.macos.control;

public enum FrameOutcome
{
	SUBMITTED(0, "submitted"),
	SKIPPED_SUSPENDED(1, "skipped-suspended"),
	SKIPPED_IN_FLIGHT(2, "skipped-in-flight"),
	NIL_DRAWABLE(3, "nil-drawable"),
	REJECTED(4, "rejected"),
	ERROR(5, "error");

	private final int nativeCode;
	private final String wireName;

	FrameOutcome(int nativeCode, String wireName)
	{
		this.nativeCode = nativeCode;
		this.wireName = wireName;
	}

	public String wireName()
	{
		return wireName;
	}

	static FrameOutcome fromNativeCode(int value)
	{
		for (FrameOutcome outcome : values())
		{
			if (outcome.nativeCode == value)
			{
				return outcome;
			}
		}
		throw new IllegalStateException("Unknown native frame outcome: " + value);
	}
}
