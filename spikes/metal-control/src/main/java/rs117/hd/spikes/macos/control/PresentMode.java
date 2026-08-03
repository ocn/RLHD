package rs117.hd.spikes.macos.control;

public enum PresentMode
{
	FIFO_LIKE("fifo-like"),
	UNLOCKED("unlocked");

	private final String wireName;

	PresentMode(String wireName)
	{
		this.wireName = wireName;
	}

	public String wireName()
	{
		return wireName;
	}

	public static PresentMode fromWireName(String value)
	{
		for (PresentMode mode : values())
		{
			if (mode.wireName.equals(value))
			{
				return mode;
			}
		}
		throw new IllegalArgumentException("Unknown present mode: " + value);
	}
}
