package rs117.hd.renderer;

import java.util.HashSet;
import java.util.Set;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

public class RendererBackendContractTest {
	@Test
	public void zoneIdentityIncludesGeneration() {
		ZoneKey first = new ZoneKey(0, 4, 9, 17);
		ZoneKey same = new ZoneKey(0, 4, 9, 17);
		ZoneKey replacement = new ZoneKey(0, 4, 9, 18);
		Set<ZoneKey> keys = new HashSet<>();
		keys.add(first);

		assertEquals(first, same);
		assertEquals(first.hashCode(), same.hashCode());
		assertNotEquals(first, replacement);
		assertTrue(keys.contains(same));
		assertFalse(keys.contains(replacement));
	}

	@Test
	public void frameOutcomesKeepOfflinePlanningDistinctFromLiveRendering() {
		assertEquals(4, FrameOutcome.values().length);
		assertEquals(FrameOutcome.RENDERED, FrameOutcome.valueOf("RENDERED"));
		assertEquals(FrameOutcome.SUSPENDED_ZERO_EXTENT, FrameOutcome.valueOf("SUSPENDED_ZERO_EXTENT"));
		assertEquals(FrameOutcome.REJECTED_INPUT, FrameOutcome.valueOf("REJECTED_INPUT"));
		assertEquals(FrameOutcome.BACKEND_FAILURE, FrameOutcome.valueOf("BACKEND_FAILURE"));
	}
}
