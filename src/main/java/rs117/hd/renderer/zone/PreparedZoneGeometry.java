package rs117.hd.renderer.zone;

import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Immutable prepared geometry. The package-private constructor takes exclusive ownership of the supplied buffers;
 * callers must not retain or mutate aliases after construction.
 */
public final class PreparedZoneGeometry {
	public static final int PACKED_VERTEX_STRIDE_INTS = 7;
	public static final int FACE_METADATA_STRIDE_INTS = 9;
	private static final int MATERIAL_INDEX_SHIFT = 21;

	private final IntBuffer opaqueVertices;
	private final IntBuffer alphaVertices;
	private final IntBuffer faceMetadata;
	private final List<PreparedDrawRange> drawRanges;
	private final List<PreparedFaceMaterials> faceMaterials;

	PreparedZoneGeometry(IntBuffer opaqueVertices, IntBuffer alphaVertices, IntBuffer faceMetadata, int[] levelOffsets) {
		this.opaqueVertices = takeOwnershipOfWritten(opaqueVertices);
		this.alphaVertices = takeOwnershipOfWritten(alphaVertices);
		this.faceMetadata = takeOwnershipOfWritten(faceMetadata);
		faceMaterials = buildFaceMaterials(this.faceMetadata);
		drawRanges = buildDrawRanges(levelOffsets);
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

	public List<PreparedFaceMaterials> faceMaterials() {
		return faceMaterials;
	}

	private static IntBuffer takeOwnershipOfWritten(IntBuffer source) {
		if (source == null)
			return IntBuffer.allocate(0).asReadOnlyBuffer();
		source.flip();
		return source.slice().asReadOnlyBuffer();
	}

	private static List<PreparedFaceMaterials> buildFaceMaterials(IntBuffer metadata) {
		List<PreparedFaceMaterials> materials = new ArrayList<>();
		for (int face = 0; face < metadata.remaining() / FACE_METADATA_STRIDE_INTS; face++) {
			int materialOffset = face * FACE_METADATA_STRIDE_INTS + 3;
			materials.add(new PreparedFaceMaterials(
				face * 3,
				metadata.get(materialOffset) >>> MATERIAL_INDEX_SHIFT,
				metadata.get(materialOffset + 1) >>> MATERIAL_INDEX_SHIFT,
				metadata.get(materialOffset + 2) >>> MATERIAL_INDEX_SHIFT
			));
		}
		return Collections.unmodifiableList(materials);
	}

	private static List<PreparedDrawRange> buildDrawRanges(int[] levelOffsets) {
		List<PreparedDrawRange> ranges = new ArrayList<>();
		int start = 0;
		for (int end : levelOffsets) {
			if (end > start) {
				ranges.add(new PreparedDrawRange(
					start / PACKED_VERTEX_STRIDE_INTS,
					(end - start) / PACKED_VERTEX_STRIDE_INTS,
					PreparedDrawRange.Pass.OPAQUE
				));
				start = end;
			}
		}
		return Collections.unmodifiableList(ranges);
	}
}
