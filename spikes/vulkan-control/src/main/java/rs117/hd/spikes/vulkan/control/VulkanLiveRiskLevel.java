package rs117.hd.spikes.vulkan.control;

import java.util.Arrays;
import java.util.stream.Collectors;

enum VulkanLiveRiskLevel
{
	SURFACE_SWAPCHAIN("surface-swapchain", 5, 0),
	FIRST_FIFO_PRESENT("first-fifo-present", 6, 1),
	SUSTAINED_FIFO("sustained-fifo", 7, 120),
	RESIZE_SUSPEND_RESTORE("resize-suspend-restore", 8, 30),
	UNLOCKED_PRESENT("unlocked-present", 9, 30),
	FULLSCREEN("fullscreen", 10, 30);

	static final String SYSTEM_PROPERTY = "rlhd.spike.vulkan.riskLevel";

	private final String optionName;
	private final int rung;
	private final int baselineFrameCount;

	VulkanLiveRiskLevel(String optionName, int rung, int baselineFrameCount)
	{
		this.optionName = optionName;
		this.rung = rung;
		this.baselineFrameCount = baselineFrameCount;
	}

	static VulkanLiveRiskLevel parseRequired(String value)
	{
		for (VulkanLiveRiskLevel riskLevel : values())
		{
			if (riskLevel.optionName.equals(value))
			{
				return riskLevel;
			}
		}
		throw new IllegalArgumentException("A single Vulkan live risk level is required: " + supportedOptions());
	}

	static VulkanLiveRiskLevel fromSystemProperty()
	{
		return parseRequired(System.getProperty(SYSTEM_PROPERTY));
	}

	static VulkanLiveRiskLevel selectRequired(String propertyValue, String argumentValue)
	{
		if (propertyValue != null && argumentValue != null && !propertyValue.equals(argumentValue))
			throw new IllegalArgumentException("The command-line risk level must match the Gradle-selected risk level.");
		return parseRequired(argumentValue == null ? propertyValue : argumentValue);
	}

	static String supportedOptions()
	{
		return Arrays.stream(values()).map(VulkanLiveRiskLevel::optionName).collect(Collectors.joining(", "));
	}

	String optionName()
	{
		return optionName;
	}

	int rung()
	{
		return rung;
	}

	int baselineFrameCount()
	{
		return baselineFrameCount;
	}

	boolean presentsFrames()
	{
		return baselineFrameCount > 0;
	}

	boolean exercisesResizeSuspendRestore()
	{
		return this == RESIZE_SUSPEND_RESTORE;
	}

	boolean exercisesUnlockedPresent()
	{
		return this == UNLOCKED_PRESENT;
	}

	boolean exercisesFullscreen()
	{
		return this == FULLSCREEN;
	}
}
