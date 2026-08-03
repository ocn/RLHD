package rs117.hd.renderer.zone;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.security.MessageDigest;
import java.util.Collections;
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
import rs117.hd.scene.water_types.WaterType;
import rs117.hd.utils.buffer.GLBuffer;
import rs117.hd.utils.buffer.GLMappedBuffer;
import rs117.hd.utils.buffer.GLTextureBuffer;
import rs117.hd.utils.collections.Int2IntHashMap;
import rs117.hd.utils.collections.Int2ObjectHashMap;

import static net.runelite.api.Constants.EXTENDED_SCENE_SIZE;
import static net.runelite.api.Constants.MAX_Z;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class PreparedZoneGeometryTest {
	@Test
	public void characterizesFixedSceneThroughCurrentUploader() throws Exception {
		Fixture fixture = new Fixture();
		fixture.uploader.estimateZoneSize(fixture.context, fixture.zone, 5, 5);

		PreparedZoneGeometry[] captured = new PreparedZoneGeometry[1];
		fixture.uploader.prepareZone(fixture.context, fixture.zone, 5, 5, geometry -> captured[0] = geometry);
		PreparedZoneGeometry geometry = captured[0];

		assertEquals(6, geometry.vertexCount());
		assertEquals(6, geometry.uvCount());
		assertEquals(6, geometry.normalCount());
		assertEquals(Collections.singletonList(0), geometry.materialIds());
		assertEquals(Collections.singletonList(new PreparedDrawRange(0, 6, 0, PreparedDrawRange.Pass.OPAQUE)), geometry.drawRanges());
		assertEquals("c9a3f5dc45838f37398e7727bf1b879cf2f78ecf5c18160db9be9317a7d1638b", sha256(geometry.opaqueVertices()));
		assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", sha256(geometry.alphaVertices()));
		assertEquals("dfc5d37caae5a388d808e161150898851a8a05f29fab32bd39a59414718681ed", sha256(geometry.faceMetadata()));
		assertTrue(geometry.opaqueVertices().isReadOnly());
		assertTrue(geometry.alphaVertices().isReadOnly());
		assertTrue(geometry.faceMetadata().isReadOnly());

		GLBuffer opaqueTarget = new GLBuffer("fixture-vertices", 0, 0);
		GLTextureBuffer faceTarget = new GLTextureBuffer("fixture-faces", 0);
		IntBuffer uploadedOpaque = attachMappedBuffer(opaqueTarget, 42);
		IntBuffer uploadedFaces = attachMappedBuffer(faceTarget, 18);
		fixture.zone.vboO = opaqueTarget;
		fixture.zone.tboF = faceTarget;
		fixture.uploader.uploadZone(fixture.context, fixture.zone, 5, 5);
		assertEquals("c9a3f5dc45838f37398e7727bf1b879cf2f78ecf5c18160db9be9317a7d1638b", sha256Written(uploadedOpaque));
		assertEquals("dfc5d37caae5a388d808e161150898851a8a05f29fab32bd39a59414718681ed", sha256Written(uploadedFaces));
	}

	private static String sha256(IntBuffer values) throws Exception {
		MessageDigest digest = MessageDigest.getInstance("SHA-256");
		IntBuffer copy = values.duplicate();
		while (copy.hasRemaining()) {
			int value = copy.get();
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

	private static String sha256Written(IntBuffer values) throws Exception {
		IntBuffer written = values.duplicate();
		written.flip();
		return sha256(written);
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

	private static final class Fixture {
		final SceneUploader uploader = new SceneUploader();
		final Zone zone = new Zone();
		final ZoneSceneContext context;

		Fixture() throws Exception {
			Scene scene = mock(Scene.class);
			WorldView worldView = mock(WorldView.class);
			Client client = mock(Client.class);
			Tile tile = mock(Tile.class);
			SceneTilePaint paint = mock(SceneTilePaint.class);
			Tile[][][] tiles = new Tile[MAX_Z][EXTENDED_SCENE_SIZE][EXTENDED_SCENE_SIZE];
			byte[][][] settings = new byte[MAX_Z][EXTENDED_SCENE_SIZE][EXTENDED_SCENE_SIZE];
			int[][][] roofs = new int[MAX_Z][EXTENDED_SCENE_SIZE][EXTENDED_SCENE_SIZE];
			int[][][] heights = new int[MAX_Z][EXTENDED_SCENE_SIZE + 1][EXTENDED_SCENE_SIZE + 1];
			tiles[0][40][40] = tile;
			heights[0][40][40] = 0;
			heights[0][41][40] = -16;
			heights[0][41][41] = -32;
			heights[0][40][41] = -48;

			when(scene.isInstance()).thenReturn(false);
			when(scene.getBaseX()).thenReturn(3200);
			when(scene.getBaseY()).thenReturn(3200);
			when(scene.getExtendedTiles()).thenReturn(tiles);
			when(scene.getExtendedTileSettings()).thenReturn(settings);
			when(scene.getRoofs()).thenReturn(roofs);
			when(scene.getTileHeights()).thenReturn(heights);
			when(worldView.isTopLevel()).thenReturn(true);
			when(tile.getSceneLocation()).thenReturn(new Point(0, 0));
			when(tile.getPlane()).thenReturn(0);
			when(tile.getRenderLevel()).thenReturn(0);
			when(tile.getSceneTilePaint()).thenReturn(paint);
			when(tile.getGameObjects()).thenReturn(new GameObject[0]);
			when(paint.getSwColor()).thenReturn(10);
			when(paint.getSeColor()).thenReturn(20);
			when(paint.getNeColor()).thenReturn(30);
			when(paint.getNwColor()).thenReturn(40);
			when(paint.getTexture()).thenReturn(-1);

			context = new ZoneSceneContext(client, worldView, scene, 0, null);
			context.tileOverrideIndices = new char[MAX_Z * EXTENDED_SCENE_SIZE * EXTENDED_SCENE_SIZE * 3];
			context.vertexTerrainTexture = new Int2ObjectHashMap<>();
			context.vertexTerrainColor = new Int2IntHashMap();
			context.vertexTerrainData = new Int2IntHashMap();
			context.vertexTerrainNormalIndices = new Int2IntHashMap();
			context.vertexTerrainNormals = new short[0];

			RenderCallbackManager callbacks = mock(RenderCallbackManager.class);
			when(callbacks.drawTile(scene, tile)).thenReturn(true);
			ProceduralGenerator proceduralGenerator = mock(ProceduralGenerator.class);
			when(proceduralGenerator.seasonalWaterType(any(), anyInt())).thenReturn(WaterType.NONE);
			setField(uploader, "renderCallbackManager", callbacks);
			setField(uploader, "plugin", mock(HdPlugin.class));
			setField(uploader, "materialManager", mock(MaterialManager.class));
			setField(uploader, "proceduralGenerator", proceduralGenerator);
			uploader.setScene(scene);
		}
	}
}
