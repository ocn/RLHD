package rs117.hd.renderer.zone;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.ReadOnlyBufferException;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import net.runelite.api.Client;
import net.runelite.api.GameObject;
import net.runelite.api.Point;
import net.runelite.api.Scene;
import net.runelite.api.SceneTilePaint;
import net.runelite.api.Tile;
import net.runelite.api.WorldView;
import net.runelite.client.callback.RenderCallbackManager;
import org.junit.Test;
import rs117.hd.HdPlugin;
import rs117.hd.scene.MaterialManager;
import rs117.hd.scene.ProceduralGenerator;
import rs117.hd.scene.materials.Material;
import rs117.hd.scene.water_types.WaterType;
import rs117.hd.utils.buffer.GLBuffer;
import rs117.hd.utils.buffer.GLMappedBuffer;
import rs117.hd.utils.buffer.GLTextureBuffer;
import rs117.hd.utils.collections.Int2IntHashMap;
import rs117.hd.utils.collections.Int2ObjectHashMap;

import static net.runelite.api.Constants.EXTENDED_SCENE_SIZE;
import static net.runelite.api.Constants.MAX_Z;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class PreparedZoneGeometryTest {
	private static final String BASE_COMMIT = "7b2854c49b3c23980268fade96fdac16e5a5029a";
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
	private static final int[] BASE_LEVEL_OFFSETS = { 42, 126, 126, 126, 126, 126 };
	private static final String BASE_OPAQUE_SHA256 = "eb34eee67ce381cfeccf1a3917ca193fc47b4cd2f8badd64ea19b174a946db8f";
	private static final String BASE_FACE_SHA256 = "0d44981e3cf76b1413b172a9db5a08b1e6de170a65e2c35e6f098df8d3c02d0c";
	private static final List<PreparedFaceMaterials> BASE_FACE_MATERIALS = Arrays.asList(
		new PreparedFaceMaterials(0, 4, 3, 2),
		new PreparedFaceMaterials(3, 1, 2, 3),
		new PreparedFaceMaterials(6, 4, 3, 2),
		new PreparedFaceMaterials(9, 1, 2, 3)
	);
	private static final List<PreparedDrawRange> BASE_DRAW_RANGES = Arrays.asList(
		new PreparedDrawRange(0, 6, PreparedDrawRange.Pass.OPAQUE),
		new PreparedDrawRange(6, 12, PreparedDrawRange.Pass.OPAQUE)
	);

	@Test
	public void preparedGeometryMatchesBaseMappedUploaderGolden() throws Exception {
		Fixture fixture = new Fixture();
		fixture.estimate();
		PreparedZoneGeometry[] captured = new PreparedZoneGeometry[1];
		fixture.uploader.prepareZone(fixture.context, fixture.zone, 5, 5, geometry -> captured[0] = geometry);
		PreparedZoneGeometry geometry = captured[0];

		assertEquals(18, geometry.vertexCount());
		assertEquals(18, geometry.uvCount());
		assertEquals(18, geometry.normalCount());
		assertEquals(BASE_DRAW_RANGES, geometry.drawRanges());
		assertEquals(BASE_FACE_MATERIALS, geometry.faceMaterials());
		assertFaceMaterialReferences(geometry);
		assertBuffer(geometry.opaqueVertices(), BASE_OPAQUE_VERTICES, BASE_OPAQUE_SHA256);
		assertBuffer(geometry.faceMetadata(), BASE_FACE_METADATA, BASE_FACE_SHA256);
		assertEmptyAlpha(geometry.alphaVertices());
		assertZoneMetadata(fixture.zone);
	}

	@Test
	public void directMappedUploadMatchesBaseGolden() throws Exception {
		Fixture fixture = new Fixture();
		fixture.estimate();
		GLBuffer opaqueTarget = new GLBuffer("base-vertices", 0, 0);
		GLTextureBuffer faceTarget = new GLTextureBuffer("base-faces", 0);
		IntBuffer opaque = attachMappedBuffer(opaqueTarget, BASE_OPAQUE_VERTICES.length);
		IntBuffer faces = attachMappedBuffer(faceTarget, BASE_FACE_METADATA.length);
		fixture.zone.vboO = opaqueTarget;
		fixture.zone.tboF = faceTarget;

		fixture.uploader.uploadZone(fixture.context, fixture.zone, 5, 5);

		assertEquals(BASE_OPAQUE_VERTICES.length, opaque.position());
		assertEquals(BASE_OPAQUE_VERTICES.length, opaque.limit());
		assertEquals(BASE_FACE_METADATA.length, faces.position());
		assertEquals(BASE_FACE_METADATA.length, faces.limit());
		assertArrayEquals(BASE_OPAQUE_VERTICES, written(opaque));
		assertArrayEquals(BASE_FACE_METADATA, written(faces));
		assertEquals(BASE_OPAQUE_SHA256, sha256(written(opaque)));
		assertEquals(BASE_FACE_SHA256, sha256(written(faces)));
		assertNull(fixture.zone.vboA);
		assertZoneMetadata(fixture.zone);
	}

	@Test
	public void returnedBuffersAreIndependentReadOnlyZeroBasedViews() throws Exception {
		Fixture fixture = new Fixture();
		fixture.estimate();
		PreparedZoneGeometry[] captured = new PreparedZoneGeometry[1];
		fixture.uploader.prepareZone(fixture.context, fixture.zone, 5, 5, geometry -> captured[0] = geometry);

		IntBuffer first = captured[0].opaqueVertices();
		assertEquals(0, first.position());
		assertEquals(BASE_OPAQUE_VERTICES.length, first.limit());
		assertEquals(BASE_OPAQUE_VERTICES.length, first.capacity());
		assertTrue(first.isReadOnly());
		first.position(1);
		assertEquals(0, captured[0].opaqueVertices().position());
		assertThrows(ReadOnlyBufferException.class, () -> first.put(0, 0));
		assertEmptyAlpha(captured[0].alphaVertices());
	}

	@Test
	public void preparationDetachesMutableWriterAliasesBeforePublishing() throws Exception {
		Fixture fixture = new Fixture();
		fixture.estimate();
		PreparedZoneGeometry[] first = new PreparedZoneGeometry[1];
		fixture.uploader.prepareZone(fixture.context, fixture.zone, 5, 5, geometry -> {
			assertCacheOutputsDetached(fixture.uploader.writeCache);
			first[0] = geometry;
		});
		assertNotNull(first[0]);

		Zone reusedZone = new Zone();
		fixture.uploader.estimateZoneSize(fixture.context, reusedZone, 5, 5);
		fixture.uploader.prepareZone(fixture.context, reusedZone, 5, 5, geometry ->
			assertCacheOutputsDetached(fixture.uploader.writeCache)
		);
		assertArrayEquals(BASE_OPAQUE_VERTICES, remaining(first[0].opaqueVertices()));
		assertArrayEquals(BASE_FACE_METADATA, remaining(first[0].faceMetadata()));
		assertEquals(BASE_OPAQUE_SHA256, sha256(remaining(first[0].opaqueVertices())));
		assertEquals(BASE_FACE_SHA256, sha256(remaining(first[0].faceMetadata())));
	}

	@Test
	public void generationFailureDiscardsAndDetachesWriterAliases() throws Exception {
		Fixture fixture = new Fixture();
		fixture.estimate();
		AtomicInteger processedTiles = new AtomicInteger();
		AtomicBoolean sinkCalled = new AtomicBoolean();
		fixture.uploader.onBeforeProcessTile = (tile, isEstimate) -> {
			if (!isEstimate && processedTiles.incrementAndGet() == 2)
				throw new InterruptedException("generation interrupted");
		};

		InterruptedException failure = assertThrows(InterruptedException.class, () ->
			fixture.uploader.prepareZone(fixture.context, fixture.zone, 5, 5, geometry -> sinkCalled.set(true))
		);
		assertEquals("generation interrupted", failure.getMessage());
		assertFalse(sinkCalled.get());
		assertCacheOutputsDetached(fixture.uploader.writeCache);
	}

	@Test
	public void detachDiscardsStagedDataWithoutFlushing() {
		VertexWriteCache.Collection cache = new VertexWriteCache.Collection();
		IntBuffer opaque = IntBuffer.allocate(7);
		IntBuffer alpha = IntBuffer.allocate(7);
		IntBuffer opaqueFaces = IntBuffer.allocate(9);
		IntBuffer alphaFaces = IntBuffer.allocate(9);
		cache.setOutputBuffers(opaque, alpha, opaqueFaces, alphaFaces);
		cache.opaque.putStaticVertex(1, 2, 3, 0, 0, 0, 0, -1, 0, 0, false);

		cache.detachOutputBuffers();

		assertEquals(0, opaque.position());
		assertEquals(0, alpha.position());
		assertEquals(0, opaqueFaces.position());
		assertEquals(0, alphaFaces.position());
		assertCacheOutputsDetached(cache);
	}

	@Test
	public void sinkFailurePropagatesAfterMetadataGeneration() throws Exception {
		Fixture fixture = new Fixture();
		fixture.estimate();
		IllegalStateException failure = assertThrows(IllegalStateException.class, () ->
			fixture.uploader.prepareZone(fixture.context, fixture.zone, 5, 5, geometry -> {
				throw new IllegalStateException("sink failed");
			})
		);
		assertEquals("sink failed", failure.getMessage());
		assertZoneMetadata(fixture.zone);
	}

	private static void assertBuffer(IntBuffer actual, int[] expected, String expectedHash) throws Exception {
		assertEquals(0, actual.position());
		assertEquals(expected.length, actual.limit());
		assertEquals(expected.length, actual.capacity());
		assertTrue(actual.isReadOnly());
		assertArrayEquals(expected, remaining(actual));
		assertEquals(expectedHash, sha256(remaining(actual)));
	}

	private static void assertEmptyAlpha(IntBuffer alpha) {
		assertEquals(0, alpha.position());
		assertEquals(0, alpha.limit());
		assertEquals(0, alpha.capacity());
		assertTrue(alpha.isReadOnly());
	}

	private static void assertZoneMetadata(Zone zone) {
		assertArrayEquals(BASE_LEVEL_OFFSETS, zone.levelOffsets);
		assertEquals("[[], [], [], []]", Arrays.deepToString(zone.rids));
		assertEquals("[[], [], [], []]", Arrays.deepToString(zone.roofStart));
		assertEquals("[[], [], [], []]", Arrays.deepToString(zone.roofEnd));
	}

	private static void assertCacheOutputsDetached(VertexWriteCache.Collection cache) {
		assertNotNull(cache);
		assertNull(outputBuffer(cache.opaque));
		assertNull(outputBuffer(cache.alpha));
		assertNull(outputBuffer(cache.opaqueTex));
		assertNull(outputBuffer(cache.alphaTex));
	}

	private static IntBuffer outputBuffer(VertexWriteCache cache) {
		try {
			Field field = VertexWriteCache.class.getDeclaredField("outputBuffer");
			field.setAccessible(true);
			return (IntBuffer) field.get(cache);
		} catch (ReflectiveOperationException ex) {
			throw new AssertionError(ex);
		}
	}

	private static void assertFaceMaterialReferences(PreparedZoneGeometry geometry) {
		IntBuffer vertices = geometry.opaqueVertices();
		for (int vertex = 0; vertex < geometry.vertexCount(); vertex++) {
			int texturedFaceIndex = vertices.get(vertex * PreparedZoneGeometry.PACKED_VERTEX_STRIDE_INTS + 6) & 0x7FFFFFFF;
			assertTrue(geometry.faceMaterials().stream().anyMatch(materials ->
				materials.texturedFaceIndex() == texturedFaceIndex
			));
		}
	}

	private static int[] remaining(IntBuffer values) {
		IntBuffer copy = values.duplicate();
		int[] result = new int[copy.remaining()];
		copy.get(result);
		return result;
	}

	private static int[] written(IntBuffer values) {
		IntBuffer copy = values.duplicate();
		copy.flip();
		int[] result = new int[copy.remaining()];
		copy.get(result);
		return result;
	}

	private static String sha256(int[] values) throws Exception {
		MessageDigest digest = MessageDigest.getInstance("SHA-256");
		for (int value : values) {
			digest.update((byte) (value >>> 24));
			digest.update((byte) (value >>> 16));
			digest.update((byte) (value >>> 8));
			digest.update((byte) value);
		}
		StringBuilder hex = new StringBuilder();
		for (byte value : digest.digest())
			hex.append(String.format("%02x", value));
		return hex.toString();
	}

	private static IntBuffer attachMappedBuffer(GLBuffer owner, int capacityInts) throws Exception {
		Constructor<GLMappedBuffer> constructor = GLMappedBuffer.class.getDeclaredConstructor(GLBuffer.class, ByteBuffer.class);
		constructor.setAccessible(true);
		GLMappedBuffer mapped = constructor.newInstance(owner, ByteBuffer.allocateDirect(capacityInts * Integer.BYTES));
		Field field = GLBuffer.class.getDeclaredField("mappedBuffer");
		field.setAccessible(true);
		field.set(owner, mapped);
		return mapped.intView();
	}

	private static void setField(Object target, String name, Object value) throws Exception {
		Field field = SceneUploader.class.getDeclaredField(name);
		field.setAccessible(true);
		field.set(target, value);
	}

	private static Material material(int index) {
		Material material = new Material().name("BASE_" + BASE_COMMIT.substring(0, 8) + "_" + index);
		material.uboIndex = index;
		return material;
	}

	private static Tile tile(int level) {
		Tile tile = mock(Tile.class);
		SceneTilePaint paint = mock(SceneTilePaint.class);
		when(tile.getSceneLocation()).thenReturn(new Point(0, 0));
		when(tile.getPlane()).thenReturn(level);
		when(tile.getRenderLevel()).thenReturn(level);
		when(tile.getSceneTilePaint()).thenReturn(paint);
		when(tile.getGameObjects()).thenReturn(new GameObject[0]);
		when(paint.getSwColor()).thenReturn(10 + level);
		when(paint.getSeColor()).thenReturn(20 + level);
		when(paint.getNeColor()).thenReturn(30 + level);
		when(paint.getNwColor()).thenReturn(40 + level);
		when(paint.getTexture()).thenReturn(-1);
		return tile;
	}

	private static final class Fixture {
		final SceneUploader uploader = new SceneUploader();
		final Zone zone = new Zone();
		final ZoneSceneContext context;

		Fixture() throws Exception {
			Scene scene = mock(Scene.class);
			WorldView worldView = mock(WorldView.class);
			Client client = mock(Client.class);
			Tile tile0 = tile(0);
			Tile tile1 = tile(1);
			Tile[][][] tiles = new Tile[MAX_Z][EXTENDED_SCENE_SIZE][EXTENDED_SCENE_SIZE];
			byte[][][] settings = new byte[MAX_Z][EXTENDED_SCENE_SIZE][EXTENDED_SCENE_SIZE];
			int[][][] roofs = new int[MAX_Z][EXTENDED_SCENE_SIZE][EXTENDED_SCENE_SIZE];
			int[][][] heights = new int[MAX_Z][EXTENDED_SCENE_SIZE + 1][EXTENDED_SCENE_SIZE + 1];
			tiles[0][40][40] = tile0;
			tiles[1][40][40] = tile1;
			for (int level = 0; level < 2; level++) {
				heights[level][40][40] = 0;
				heights[level][41][40] = -16;
				heights[level][41][41] = -32;
				heights[level][40][41] = -48;
			}
			when(scene.isInstance()).thenReturn(false);
			when(scene.getBaseX()).thenReturn(3200);
			when(scene.getBaseY()).thenReturn(3200);
			when(scene.getExtendedTiles()).thenReturn(tiles);
			when(scene.getExtendedTileSettings()).thenReturn(settings);
			when(scene.getRoofs()).thenReturn(roofs);
			when(scene.getTileHeights()).thenReturn(heights);
			when(worldView.isTopLevel()).thenReturn(true);

			context = new ZoneSceneContext(client, worldView, scene, 0, null);
			context.tileOverrideIndices = new char[MAX_Z * EXTENDED_SCENE_SIZE * EXTENDED_SCENE_SIZE * 3];
			context.vertexTerrainTexture = new Int2ObjectHashMap<>();
			context.vertexTerrainColor = new Int2IntHashMap();
			context.vertexTerrainData = new Int2IntHashMap();
			context.vertexTerrainNormalIndices = new Int2IntHashMap();
			context.vertexTerrainNormals = new short[0];
			int[] keys = ProceduralGenerator.tileVertexKeys(context, tile0);
			for (int i = 0; i < keys.length; i++)
				context.vertexTerrainTexture.put(keys[i], material(i + 1));

			RenderCallbackManager callbacks = mock(RenderCallbackManager.class);
			when(callbacks.drawTile(any(), any())).thenReturn(true);
			ProceduralGenerator proceduralGenerator = mock(ProceduralGenerator.class);
			when(proceduralGenerator.seasonalWaterType(any(), anyInt())).thenReturn(WaterType.NONE);
			HdPlugin plugin = new HdPlugin();
			plugin.configGroundTextures = true;
			plugin.configGroundBlending = true;
			plugin.configGroundBlendingTextures = true;
			setField(uploader, "renderCallbackManager", callbacks);
			setField(uploader, "plugin", plugin);
			setField(uploader, "materialManager", mock(MaterialManager.class));
			setField(uploader, "proceduralGenerator", proceduralGenerator);
			uploader.setScene(scene);
		}

		void estimate() throws InterruptedException {
			uploader.estimateZoneSize(context, zone, 5, 5);
		}
	}
}
