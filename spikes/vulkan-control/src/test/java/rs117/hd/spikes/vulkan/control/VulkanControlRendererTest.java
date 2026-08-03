package rs117.hd.spikes.vulkan.control;

import java.nio.file.Paths;
import org.junit.Test;
import rs117.hd.spikes.macos.SurfaceExtent;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class VulkanControlRendererTest
{
	@Test
	public void usesTheExactSyntheticUiWorkloadAndClosesDeterministically()
	{
		FakeBackend backend = new FakeBackend();
		VulkanControlRenderer renderer = new VulkanControlRenderer(backend, Paths.get("timing.jsonl"));
		assertEquals(VulkanFrameOutcome.SUBMITTED, renderer.render(SurfaceExtent.of(4, 4, 1), 7));
		assertEquals(VulkanFrameOutcome.SKIPPED_SUSPENDED, renderer.render(SurfaceExtent.of(0, 4, 1), 8));
		assertTrue(renderer.runReadbackCheck());
		renderer.setPresentMode(VulkanPresentMode.UNLOCKED);
		renderer.close();
		assertEquals(0, renderer.counters().liveNativeObjects());
		assertThrows(IllegalStateException.class, () -> renderer.render(SurfaceExtent.of(1, 1, 1), 9));
		assertEquals(VulkanPresentMode.UNLOCKED, backend.requestedMode);
	}

	@Test
	public void failedPreConsumptionCloseCanBeRetried()
	{
		FakeBackend backend = new FakeBackend();
		backend.failCloseOnce = true;
		VulkanControlRenderer renderer = new VulkanControlRenderer(backend, Paths.get("timing.jsonl"));
		assertThrows(IllegalStateException.class, renderer::close);
		assertFalse(backend.consumed);
		assertEquals(VulkanFrameOutcome.SUBMITTED, renderer.render(SurfaceExtent.of(2, 2, 1), 1));
		renderer.close();
		assertTrue(backend.consumed);
	}

	@Test
	public void failedPostConsumptionCloseLeavesRendererClosed()
	{
		FakeBackend backend = new FakeBackend();
		backend.failCloseAfterConsumption = true;
		VulkanControlRenderer renderer = new VulkanControlRenderer(backend, Paths.get("timing.jsonl"));
		assertThrows(VulkanBackendCloseException.class, renderer::close);
		assertTrue(backend.consumed);
		assertEquals(0, renderer.counters().liveNativeObjects());
		assertThrows(IllegalStateException.class, () -> renderer.render(SurfaceExtent.of(1, 1, 1), 9));
		assertThrows(IllegalStateException.class, renderer::close);
	}

	private static final class FakeBackend implements VulkanBackendAccess
	{
		private final long[] counters = new long[VulkanControlCounters.FIELD_COUNT];
		private boolean failCloseOnce;
		private boolean failCloseAfterConsumption;
		private boolean consumed;
		private VulkanPresentMode requestedMode = VulkanPresentMode.FIFO;

		private FakeBackend()
		{
			counters[13] = 5;
			counters[14] = 5;
		}

		@Override public boolean ready() { return true; }
		@Override
		public VulkanFrameOutcome render(int width, int height, long frameId, long uiGenerateNs, byte[] uiBytes)
		{
			assertEquals(width * height * 4, uiBytes.length);
			assertTrue(uiGenerateNs >= 0);
			assertNotEquals(0, uiBytes[((height - 1) * width + width - 1) * 4]);
			counters[3]++;
			counters[4]++;
			counters[6]++;
			counters[10] += uiBytes.length;
			return VulkanFrameOutcome.SUBMITTED;
		}
		@Override public VulkanFrameOutcome skipSuspended(long frameId) { counters[8]++; return VulkanFrameOutcome.SKIPPED_SUSPENDED; }
		@Override public void setPresentMode(VulkanPresentMode mode) { requestedMode = mode; }
		@Override public long[] counters() { return counters.clone(); }
		@Override public boolean runReadbackCheck(byte[] first, byte[] second) { return first.length == second.length && first[252] != second[252]; }
		@Override
		public void close(long[] finalCounters)
		{
			if (failCloseOnce) { failCloseOnce = false; throw new IllegalStateException("injected"); }
			consumed = true;
			counters[13] = 0;
			System.arraycopy(counters, 0, finalCounters, 0, counters.length);
			if (failCloseAfterConsumption) throw new VulkanBackendCloseException("injected post-consumption failure", true);
		}
	}
}
