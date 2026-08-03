package rs117.hd.spikes.macos.control;

import java.nio.file.Paths;
import org.junit.Test;
import rs117.hd.spikes.macos.SurfaceExtent;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class MetalControlRendererTest
{
	@Test
	public void tracksRenderSuspendModeAndDeterministicClose()
	{
		FakeNative access = new FakeNative();
		MetalControlRenderer renderer = new MetalControlRenderer(access, 0x117L, Paths.get("timing.jsonl"), PresentMode.FIFO_LIKE, "");

		assertEquals(FrameOutcome.SUBMITTED, renderer.render(SurfaceExtent.of(4, 4, 1.0), 1));
		assertEquals(FrameOutcome.SKIPPED_SUSPENDED, renderer.render(SurfaceExtent.of(0, 4, 1.0), 2));
		renderer.setPresentMode(PresentMode.UNLOCKED);
		assertTrue(renderer.runReadbackCheck());
		assertEquals(1, renderer.counters().submitted());
		assertEquals(1, renderer.counters().skippedSuspended());
		assertEquals(0, renderer.counters().inFlight());

		renderer.close();
		assertEquals(0, renderer.counters().liveNativeObjects());
		assertThrows(IllegalStateException.class, () -> renderer.render(SurfaceExtent.of(1, 1, 1), 3));
		assertThrows(IllegalStateException.class, renderer::close);
		assertEquals(PresentMode.UNLOCKED, access.presentMode);
	}

	@Test
	public void initializationFailureIsClosedWithoutEscapingHandle()
	{
		FakeNative access = new FakeNative();
		access.ready = false;
		assertThrows(IllegalStateException.class, () -> new MetalControlRenderer(
			access, 1, Paths.get("timing.jsonl"), PresentMode.FIFO_LIKE, "pipeline"));
		assertEquals(1, access.closeCalls);
		assertEquals("pipeline", access.failureStage);
	}

	@Test
	public void failedTeardownRestoresRunningStateForRetry()
	{
		FakeNative access = new FakeNative();
		access.failCloseOnce = true;
		MetalControlRenderer renderer = new MetalControlRenderer(access, 1, Paths.get("timing.jsonl"), PresentMode.FIFO_LIKE, "");

		assertThrows(IllegalStateException.class, renderer::close);
		assertFalse(access.consumed);
		assertEquals(FrameOutcome.SUBMITTED, renderer.render(SurfaceExtent.of(2, 2, 1), 1));
		renderer.close();
		assertTrue(access.consumed);
		assertEquals(2, access.closeCalls);
		assertFalse(renderer.counters().hasErrors());
	}

	@Test
	public void postConsumptionMaterializationFailureIsTerminalAndUsesRawSnapshot()
	{
		FakeNative access = new FakeNative();
		int[] materializations = {0};
		MetalControlRenderer renderer = new MetalControlRenderer(access, 1, Paths.get("timing.jsonl"),
			PresentMode.FIFO_LIKE, "", values ->
			{
				if (materializations[0]++ == 0) throw new IllegalStateException("injected materialization failure");
				return new MetalControlCounters(values);
			});

		assertThrows(IllegalStateException.class, renderer::close);
		assertEquals(1, access.closeCalls);
		assertTrue(access.consumed);
		assertThrows(IllegalStateException.class, () -> renderer.render(SurfaceExtent.of(1, 1, 1), 1));
		assertThrows(IllegalStateException.class, renderer::close);
		assertEquals(0, renderer.counters().liveNativeObjects());
		assertEquals(1, access.closeCalls);
		assertEquals(2, materializations[0]);
	}

	private static final class FakeNative implements NativeRendererAccess
	{
		private boolean ready = true;
		private boolean failCloseOnce;
		private int closeCalls;
		private boolean consumed;
		private String failureStage;
		private PresentMode presentMode;
		private final long[] counters = new long[MetalControlCounters.FIELD_COUNT];

		@Override
		public long create(long layerHandle, String logPath, PresentMode requestedMode, String failureStage)
		{
			assertTrue(layerHandle != 0);
			this.presentMode = requestedMode;
			this.failureStage = failureStage;
			counters[13] = 7;
			counters[14] = 7;
			return 9;
		}

		@Override public boolean ready(long stateHandle) { return ready; }

		@Override
		public int render(long stateHandle, int width, int height, long frameId, long uiGenerateNs, byte[] uiBytes)
		{
			assertEquals(width * height * 4, uiBytes.length);
			assertTrue(uiGenerateNs >= 0);
			counters[3]++;
			counters[4]++;
			counters[6]++;
			counters[10] += uiBytes.length;
			counters[15] = 1;
			return 0;
		}

		@Override
		public int skipSuspended(long stateHandle, long frameId)
		{
			counters[8]++;
			return 1;
		}

		@Override public void setPresentMode(long stateHandle, PresentMode requestedMode) { presentMode = requestedMode; }
		@Override public long[] counters(long stateHandle) { return counters.clone(); }
		@Override
		public boolean runReadbackCheck(long stateHandle, byte[] firstUiBytes, byte[] secondUiBytes)
		{
			assertEquals(SyntheticUi.byteCount(8, 8), firstUiBytes.length);
			assertEquals(firstUiBytes.length, secondUiBytes.length);
			assertNotEquals(firstUiBytes[(6 * 8 + 6) * 4], secondUiBytes[(6 * 8 + 6) * 4]);
			return true;
		}

		@Override
		public void close(long stateHandle, long[] finalCounters)
		{
			closeCalls++;
			if (failCloseOnce)
			{
				failCloseOnce = false;
				throw new IllegalStateException("injected close failure");
			}
			consumed = true;
			counters[13] = 0;
			System.arraycopy(counters, 0, finalCounters, 0, counters.length);
		}
	}
}
