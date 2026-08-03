package rs117.hd.spikes.macos.control;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
	import static org.junit.Assert.assertThrows;

public class PresentModeTest
{
	@Test
	public void hasStableRequestedModeWireNames()
	{
		assertEquals("fifo-like", PresentMode.FIFO_LIKE.wireName());
		assertEquals("unlocked", PresentMode.UNLOCKED.wireName());
		assertEquals(PresentMode.FIFO_LIKE, PresentMode.fromWireName("fifo-like"));
		assertEquals(PresentMode.UNLOCKED, PresentMode.fromWireName("unlocked"));
		assertThrows(IllegalArgumentException.class, () -> PresentMode.fromWireName("fifo"));
	}

	@Test
	public void mapsEveryNativeOutcome()
	{
		for (int code = 0; code < FrameOutcome.values().length; code++)
		{
			assertEquals(FrameOutcome.values()[code], FrameOutcome.fromNativeCode(code));
		}
		assertThrows(IllegalStateException.class, () -> FrameOutcome.fromNativeCode(99));
	}
}
