package rs117.hd.spikes.vulkan.control;

import java.util.Arrays;

public final class VulkanControlCounters
{
	static final int FIELD_COUNT = 25;
	private final long[] values;

	VulkanControlCounters(long[] values)
	{
		if (values == null || values.length != FIELD_COUNT)
		{
			throw new IllegalArgumentException("Expected " + FIELD_COUNT + " Vulkan counter values.");
		}
		this.values = values.clone();
	}

	public long initErrors() { return values[0]; }
	public long shaderErrors() { return values[1]; }
	public long pipelineErrors() { return values[2]; }
	public long submitted() { return values[3]; }
	public long completed() { return values[4]; }
	public long commandErrors() { return values[5]; }
	public long presentRequested() { return values[6]; }
	public long nilDrawable() { return values[7]; }
	public long skippedSuspended() { return values[8]; }
	public long skippedInFlight() { return values[9]; }
	public long uiUploadBytes() { return values[10]; }
	public long resizeRebuilds() { return values[11]; }
	public long deviceRebuilds() { return values[12]; }
	public long liveNativeObjects() { return values[13]; }
	public long highWaterNativeObjects() { return values[14]; }
	public long maxInFlight() { return values[15]; }
	public long presentationCallbacks() { return values[16]; }
	public long presentationDropped() { return values[17]; }
	public long presentationTimeouts() { return values[18]; }
	public long presentModeDivergences() { return values[19]; }
	public long drawableAcquisitionRequests() { return values[20]; }
	public long drawableAcquisitionCompletions() { return values[21]; }
	public long validationWarnings() { return values[22]; }
	public long validationErrors() { return values[23]; }
	public long timestampQueryErrors() { return values[24]; }
	public long inFlight() { return submitted() - completed(); }
	public boolean hasErrors() { return initErrors() != 0 || shaderErrors() != 0 || pipelineErrors() != 0 || commandErrors() != 0 ||
		validationErrors() != 0 || timestampQueryErrors() != 0; }

	@Override
	public String toString()
	{
		return "VulkanControlCounters" + Arrays.toString(values);
	}
}
