package rs117.hd.renderer.zone;

import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class PreparedZoneGeometry {
	public static final int PACKED_VERTEX_STRIDE_INTS = 7;
	public static final int FACE_METADATA_STRIDE_INTS = 9;
	private static final int MATERIAL_INDEX_SHIFT = 21;

	private final IntBuffer opaqueVertices;
	private final IntBuffer alphaVertices;
	private final IntBuffer faceMetadata;
	private final List<PreparedDrawRange> drawRanges;
	private final List<Integer> materialIds;

	PreparedZoneGeometry(IntBuffer opaqueVertices, IntBuffer alphaVertices, IntBuffer faceMetadata, int[] levelOffsets) {
		this.opaqueVertices = copyWritten(opaqueVertices);
		this.alphaVertices = copyWritten(alphaVertices);
		this.faceMetadata = copyWritten(faceMetadata);
		materialIds = collectMaterialIds(this.faceMetadata);
		drawRanges = buildDrawRanges(levelOffsets, commonMaterialId(materialIds));
	}

	public IntBuffer opaqueVertices() {
		return opaqueVertices.asReadOnlyBuffer();
	}

	public IntBuffer alphaVertices() {
		return alphaVertices.asReadOnlyBuffer();
	}

	public IntBuffer faceMetadata() {
		return faceMetadata.asReadOnlyBuffer();
	}

	public int vertexCount() {
		return (opaqueVertices.remaining() + alphaVertices.remaining()) / PACKED_VERTEX_STRIDE_INTS;
	}

	public int uvCount() {
		return vertexCount();
	}

	public int normalCount() {
		return vertexCount();
	}

	public List<PreparedDrawRange> drawRanges() {
		return drawRanges;
	}

	public List<Integer> materialIds() {
		return materialIds;
	}

	private static IntBuffer copyWritten(IntBuffer source) {
		if (source == null)
			return IntBuffer.allocate(0).asReadOnlyBuffer();
		IntBuffer written = source.duplicate();
		written.flip();
		IntBuffer copy = IntBuffer.allocate(written.remaining());
		copy.put(written).flip();
		return copy.asReadOnlyBuffer();
	}

	private static List<Integer> collectMaterialIds(IntBuffer metadata) {
		Set<Integer> ids = new LinkedHashSet<>();
		for (int face = 0; face < metadata.remaining() / FACE_METADATA_STRIDE_INTS; face++) {
			int materialOffset = face * FACE_METADATA_STRIDE_INTS + 3;
			ids.add(metadata.get(materialOffset) >>> MATERIAL_INDEX_SHIFT);
			ids.add(metadata.get(materialOffset + 1) >>> MATERIAL_INDEX_SHIFT);
			ids.add(metadata.get(materialOffset + 2) >>> MATERIAL_INDEX_SHIFT);
		}
		return Collections.unmodifiableList(new ArrayList<>(ids));
	}

	private static int commonMaterialId(List<Integer> materialIds) {
		return materialIds.size() == 1 ? materialIds.get(0) : PreparedDrawRange.MIXED_MATERIAL_ID;
	}

	private static List<PreparedDrawRange> buildDrawRanges(int[] levelOffsets, int materialId) {
		List<PreparedDrawRange> ranges = new ArrayList<>();
		int start = 0;
		for (int end : levelOffsets) {
			if (end > start) {
				ranges.add(new PreparedDrawRange(
					start / PACKED_VERTEX_STRIDE_INTS,
					(end - start) / PACKED_VERTEX_STRIDE_INTS,
					materialId,
					PreparedDrawRange.Pass.OPAQUE
				));
				start = end;
			}
		}
		return Collections.unmodifiableList(ranges);
	}
}
