package rs117.hd.spikes.vulkan.opaque;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import org.junit.Test;
import rs117.hd.renderer.CameraUniforms;
import rs117.hd.renderer.FrameOutcome;
import rs117.hd.renderer.PreparedFrame;
import rs117.hd.renderer.PreparedUiTexture;
import rs117.hd.renderer.SurfaceExtent;
import rs117.hd.renderer.ZoneKey;
import rs117.hd.renderer.zone.PreparedFaceMaterials;
import rs117.hd.renderer.zone.PreparedZoneGeometry;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class VulkanOpaqueZoneContractTest {
	private static final int[] BASE_OPAQUE_VERTICES = {
		-2097024, 128, 0, 0, -65536, 0, 0, -3145728, 128, 15360, 0, -65536, 0, 0,
		-1048448, 0, 1006632960, 0, -65536, 0, 0, 0, 0, 1006648320, 0, -65536, 0, 3,
		-1048448, 0, 1006632960, 0, -65536, 0, 3, -3145728, 128, 15360, 0, -65536, 0, 3,
		-2097024, 128, 0, 0, -65536, 0, 6, -3145728, 128, 15360, 0, -65536, 0, 6,
		-1048448, 0, 1006632960, 0, -65536, 0, 6, -1048448, 0, 1006632960, 0, 65536, 0, -2147483642,
		-3145728, 128, 15360, 0, 65536, 0, -2147483642, -2097024, 128, 0, 0, 65536, 0, -2147483642,
		0, 0, 1006648320, 0, -65536, 0, 9, -1048448, 0, 1006632960, 0, -65536, 0, 9,
		-3145728, 128, 15360, 0, -65536, 0, 9, -3145728, 128, 15360, 0, 65536, 0, -2147483639,
		-1048448, 0, 1006632960, 0, 65536, 0, -2147483639, 0, 0, 1006648320, 0, 65536, 0, -2147483639
	};
	private static final int[] BASE_FACE_METADATA = {
		30, 40, 20, 8400896, 6303744, 4206592, 1, 1, 1,
		10, 20, 40, 2109440, 4206592, 6303744, 1, 1, 1,
		31, 41, 21, 8400896, 6303744, 4206592, 3, 3, 3,
		11, 21, 41, 2109440, 4206592, 6303744, 3, 3, 3
	};
	private static final int[] LEVEL_OFFSETS = { 42, 126, 126, 126, 126, 126 };

	@Test
	public void baseFixtureSerializesLittleEndianAndPinsOpaqueManifest() throws Exception {
		VulkanOpaqueZoneContract.PreparedUpload upload = VulkanOpaqueZoneContract.prepare(baseGeometry());
		assertEquals(18, upload.opaqueVertexCount());
		assertEquals(BASE_OPAQUE_VERTICES.length * Integer.BYTES, upload.vertexBytes().remaining());
		assertEquals(BASE_FACE_METADATA.length * Integer.BYTES, upload.faceMetadataBytes().remaining());
		assertEquals(BASE_OPAQUE_VERTICES[0], upload.vertexBytes().order(ByteOrder.LITTLE_ENDIAN).getInt(0));
		assertEquals(BASE_FACE_METADATA[0], upload.faceMetadataBytes().order(ByteOrder.LITTLE_ENDIAN).getInt(0));

		VulkanOpaqueZoneContract.Manifest manifest = VulkanOpaqueZoneContract.manifest();
		assertEquals(28, manifest.vertexStride());
		assertArrayEquals(new int[] { 0, 8, 16, 24 }, manifest.vertexOffsets());
		assertArrayEquals(new String[] { "R16G16B16A16_SINT", "R16G16B16A16_SFLOAT", "R16G16B16A16_SINT", "R32_SINT" }, manifest.vertexFormats());
		assertEquals(4, manifest.metadataArrayStride());
		assertEquals(72, manifest.pushConstantSize());
		assertArrayEquals(new int[] { 0, 64 }, manifest.pushConstantOffsets());
		assertEquals("VERTEX", manifest.pushConstantStage());
		assertEquals("D32_SFLOAT", manifest.depthFormat());
		assertEquals(0f, manifest.depthClear(), 0);
		assertEquals("GREATER_OR_EQUAL", manifest.depthCompare());
		assertTrue(manifest.negativeViewportHeight());
		assertEquals("B8G8R8A8_UNORM", manifest.colorFormat());
		assertEquals("SRGB_NONLINEAR", manifest.colorSpace());
		assertEquals("ONE", manifest.uiSourceColorBlendFactor());
		assertEquals("ONE_MINUS_SRC_ALPHA", manifest.uiDestinationColorBlendFactor());
	}

	@Test
	public void baseFixturePinsFaceAssociationAndSignedWinding() throws Exception {
		VulkanOpaqueZoneContract.PreparedUpload upload = VulkanOpaqueZoneContract.prepare(baseGeometry());
		assertEquals(Arrays.asList(
			new PreparedFaceMaterials(0, 4, 3, 2),
			new PreparedFaceMaterials(3, 1, 2, 3),
			new PreparedFaceMaterials(6, 4, 3, 2),
			new PreparedFaceMaterials(9, 1, 2, 3)
		), upload.faceMaterials());
		assertEquals(0, VulkanOpaqueZoneContract.metadataCorner(0, 6));
		assertEquals(2, VulkanOpaqueZoneContract.metadataCorner(0, Integer.MIN_VALUE | 6));
		assertEquals(1, VulkanOpaqueZoneContract.metadataCorner(1, Integer.MIN_VALUE | 6));
		assertEquals(0, VulkanOpaqueZoneContract.metadataCorner(2, Integer.MIN_VALUE | 6));
	}

	@Test
	public void contractRejectsNonOpaqueOrMisalignedGeometry() throws Exception {
		assertThrows(IllegalArgumentException.class, () -> VulkanOpaqueZoneContract.prepare(geometry(
			Arrays.copyOf(BASE_OPAQUE_VERTICES, BASE_OPAQUE_VERTICES.length - 1), new int[0], BASE_FACE_METADATA, LEVEL_OFFSETS)));
		assertThrows(IllegalArgumentException.class, () -> VulkanOpaqueZoneContract.prepare(geometry(
			BASE_OPAQUE_VERTICES, new int[7], BASE_FACE_METADATA, LEVEL_OFFSETS)));
		int[] badReference = BASE_OPAQUE_VERTICES.clone();
		badReference[6] = 12;
		assertThrows(IllegalArgumentException.class, () -> VulkanOpaqueZoneContract.prepare(geometry(
			badReference, new int[0], BASE_FACE_METADATA, LEVEL_OFFSETS)));
	}

	@Test
	public void cpuProjectionPinsVulkanDepthAndReverseZ() {
		assertArrayEquals(new float[] { 2, 4, 4, 6 },
			VulkanOpaqueZoneContract.toVulkanClip(new float[] { 2, 4, 2, 6 }), 0);
		assertTrue(VulkanOpaqueZoneContract.passesReverseZ(0.75f, 0.5f));
		assertFalse(VulkanOpaqueZoneContract.passesReverseZ(0.25f, 0.5f));
	}

	@Test
	public void resourcePlanReplacesGenerationAndRejectsUnknownZone() throws Exception {
		RecordingAllocator allocator = new RecordingAllocator();
		VulkanZoneResourcePlan plan = new VulkanZoneResourcePlan(allocator);
		ZoneKey first = new ZoneKey(0, 5, 5, 1);
		ZoneKey next = new ZoneKey(0, 5, 5, 2);
		plan.uploadZone(first, baseGeometry());
		assertEquals(FrameOutcome.RENDERED, plan.render(frame(first, new SurfaceExtent(640, 480))));
		plan.uploadZone(next, baseGeometry());
		assertEquals(2, allocator.closed.get());
		assertEquals(FrameOutcome.REJECTED_INPUT, plan.render(frame(first, new SurfaceExtent(640, 480))));
		assertEquals(FrameOutcome.RENDERED, plan.render(frame(next, new SurfaceExtent(640, 480))));
		plan.destroyZone(first);
		assertEquals(2, allocator.closed.get());
		plan.destroyZone(next);
		assertEquals(4, allocator.closed.get());
		assertEquals(FrameOutcome.REJECTED_INPUT, plan.render(frame(next, new SurfaceExtent(640, 480))));
	}

	@Test
	public void zeroExtentSuspendsWithoutAllocatingOrRendering() throws Exception {
		RecordingAllocator allocator = new RecordingAllocator();
		VulkanZoneResourcePlan plan = new VulkanZoneResourcePlan(allocator);
		ZoneKey key = new ZoneKey(0, 5, 5, 1);
		plan.uploadZone(key, baseGeometry());
		assertEquals(FrameOutcome.SUSPENDED_ZERO_EXTENT, plan.render(frame(key, new SurfaceExtent(0, 480))));
		assertEquals(2, allocator.created.get());
	}

	@Test
	public void partialUploadRollsBackAndPreservesPriorGeneration() throws Exception {
		RecordingAllocator allocator = new RecordingAllocator();
		VulkanZoneResourcePlan plan = new VulkanZoneResourcePlan(allocator);
		ZoneKey first = new ZoneKey(0, 5, 5, 1);
		ZoneKey next = new ZoneKey(0, 5, 5, 2);
		plan.uploadZone(first, baseGeometry());
		allocator.failAt = 4;
		assertThrows(IllegalStateException.class, () -> plan.uploadZone(next, baseGeometry()));
		assertEquals(1, allocator.closed.get());
		assertEquals(FrameOutcome.RENDERED, plan.render(frame(first, new SurfaceExtent(640, 480))));
		assertEquals(FrameOutcome.REJECTED_INPUT, plan.render(frame(next, new SurfaceExtent(640, 480))));
	}

	@Test
	public void teardownAggregatesFailuresAndStillBalancesLedger() throws Exception {
		RecordingAllocator allocator = new RecordingAllocator();
		allocator.failClose = true;
		VulkanZoneResourcePlan plan = new VulkanZoneResourcePlan(allocator);
		plan.uploadZone(new ZoneKey(0, 1, 1, 1), baseGeometry());
		plan.uploadZone(new ZoneKey(0, 2, 2, 1), baseGeometry());

		VulkanZoneResourcePlan.ResourceTeardownException failure = assertThrows(
			VulkanZoneResourcePlan.ResourceTeardownException.class, plan::close);
		assertEquals(4, failure.getSuppressed().length);
		assertEquals(4, allocator.closed.get());
		assertEquals(0, plan.liveResourceCount());
	}

	@Test
	public void resourceLedgerRejectsUnderflow() {
		VulkanZoneResourcePlan.ResourceLedger ledger = new VulkanZoneResourcePlan.ResourceLedger();
		assertThrows(IllegalStateException.class, ledger::release);
		ledger.acquire();
		ledger.release();
		assertEquals(0, ledger.liveCount());
	}

	@Test
	public void reflectionPinsShaderInterfacesWithoutClaimingPipelineCorrectness() throws Exception {
		String opaqueVertex = resource("/shaders/vulkan-opaque-slice/opaque.vert.reflect.json");
		String opaqueFragment = resource("/shaders/vulkan-opaque-slice/opaque.frag.reflect.json");
		String uiVertex = resource("/shaders/vulkan-opaque-slice/ui.vert.reflect.json");
		String uiFragment = resource("/shaders/vulkan-opaque-slice/ui.frag.reflect.json");
		for (int location = 0; location < 4; location++) assertTrue(hasNumber(opaqueVertex, "location", location));
		assertTrue(hasNumber(opaqueVertex, "set", 0));
		assertTrue(hasNumber(opaqueVertex, "binding", 0));
		assertTrue(hasNumber(opaqueVertex, "array_stride", 4));
		assertTrue(opaqueVertex.contains("\"push_constants\""));
		assertTrue(hasNumber(opaqueVertex, "block_size", 72));
		assertTrue(Pattern.compile("clipFromWorld.*?\\\"offset\\\"\\s*:\\s*0", Pattern.DOTALL).matcher(opaqueVertex).find());
		assertTrue(Pattern.compile("sceneBase.*?\\\"offset\\\"\\s*:\\s*64", Pattern.DOTALL).matcher(opaqueVertex).find());
		assertTrue(hasNumber(opaqueFragment, "location", 0));
		assertTrue(hasNumber(uiVertex, "location", 0));
		assertTrue(uiFragment.contains("\"textures\""));
		assertTrue(hasNumber(uiFragment, "set", 0));
		assertTrue(hasNumber(uiFragment, "binding", 0));
	}

	private static boolean hasNumber(String json, String field, int value) {
		return Pattern.compile("\\\"" + Pattern.quote(field) + "\\\"\\s*:\\s*" + value + "(?:,|\\s|})").matcher(json).find();
	}

	private static PreparedFrame frame(ZoneKey key, SurfaceExtent extent) {
		return new PreparedFrame(key, new CameraUniforms(new float[] {
			1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1
		}), extent, new PreparedUiTexture(1, 1, 4,
			PreparedUiTexture.PixelFormat.BGRA8_SRGB_PREMULTIPLIED, ByteBuffer.allocate(4)));
	}

	private static PreparedZoneGeometry baseGeometry() throws Exception {
		return geometry(BASE_OPAQUE_VERTICES, new int[0], BASE_FACE_METADATA, LEVEL_OFFSETS);
	}

	private static PreparedZoneGeometry geometry(int[] opaque, int[] alpha, int[] metadata, int[] offsets) throws Exception {
		Constructor<PreparedZoneGeometry> constructor = PreparedZoneGeometry.class.getDeclaredConstructor(
			IntBuffer.class, IntBuffer.class, IntBuffer.class, int[].class);
		constructor.setAccessible(true);
		return constructor.newInstance(written(opaque), written(alpha), written(metadata), offsets.clone());
	}

	private static IntBuffer written(int[] values) {
		IntBuffer buffer = IntBuffer.allocate(values.length);
		buffer.put(values);
		return buffer;
	}

	private static String resource(String name) throws Exception {
		try (InputStream stream = VulkanOpaqueZoneContractTest.class.getResourceAsStream(name)) {
			assertNotNull(name, stream);
			ByteArrayOutputStream bytes = new ByteArrayOutputStream();
			byte[] chunk = new byte[4096];
			for (int read; (read = stream.read(chunk)) != -1;) bytes.write(chunk, 0, read);
			return new String(bytes.toByteArray(), StandardCharsets.UTF_8);
		}
	}

	private static final class RecordingAllocator implements VulkanZoneResourcePlan.ResourceAllocator {
		final AtomicInteger created = new AtomicInteger();
		final AtomicInteger closed = new AtomicInteger();
		int failAt = Integer.MAX_VALUE;
		boolean failClose;

		@Override
		public VulkanZoneResourcePlan.Resource allocate(VulkanZoneResourcePlan.ResourceKind kind, ByteBuffer bytes) {
			int index = created.incrementAndGet();
			if (index == failAt) throw new IllegalStateException("allocation " + index);
			return () -> {
				closed.incrementAndGet();
				if (failClose) throw new IllegalStateException("close " + index);
			};
		}
	}
}
