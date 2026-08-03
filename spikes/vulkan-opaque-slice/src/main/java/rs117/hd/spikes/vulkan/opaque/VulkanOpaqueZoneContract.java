package rs117.hd.spikes.vulkan.opaque;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import rs117.hd.renderer.zone.PreparedDrawRange;
import rs117.hd.renderer.zone.PreparedFaceMaterials;
import rs117.hd.renderer.zone.PreparedZoneGeometry;

public final class VulkanOpaqueZoneContract {
	private static final int VERTEX_STRIDE_INTS = 7;
	private static final int FACE_STRIDE_INTS = 9;
	private static final int MATERIAL_SHIFT = 21;
	private static final Manifest MANIFEST = new Manifest();

	private VulkanOpaqueZoneContract() {}

	public static PreparedUpload prepare(PreparedZoneGeometry geometry) {
		Objects.requireNonNull(geometry, "geometry");
		IntBuffer vertices = geometry.opaqueVertices();
		IntBuffer alpha = geometry.alphaVertices();
		IntBuffer metadata = geometry.faceMetadata();
		if (alpha.hasRemaining()) throw new IllegalArgumentException("The offline slice accepts opaque geometry only");
		if (vertices.remaining() % VERTEX_STRIDE_INTS != 0)
			throw new IllegalArgumentException("Opaque vertex words are not aligned to the 28-byte record");
		int opaqueCount = vertices.remaining() / VERTEX_STRIDE_INTS;
		if (opaqueCount % 3 != 0) throw new IllegalArgumentException("Opaque vertex count must be triangle-aligned");
		if (metadata.remaining() % FACE_STRIDE_INTS != 0)
			throw new IllegalArgumentException("Face metadata words are not aligned to the 36-byte record");
		int faceCount = metadata.remaining() / FACE_STRIDE_INTS;
		validateRanges(geometry.drawRanges(), opaqueCount);
		validateFaceReferences(vertices, faceCount);
		List<PreparedFaceMaterials> materials = materialAssociations(metadata);
		if (!materials.equals(geometry.faceMaterials()))
			throw new IllegalArgumentException("Prepared face/material associations drifted from packed metadata");
		return new PreparedUpload(toLittleEndian(vertices), toLittleEndian(metadata), opaqueCount, materials);
	}

	public static Manifest manifest() { return MANIFEST; }

	public static int metadataCorner(int vertexIndex, int faceReference) {
		if (vertexIndex < 0) throw new IllegalArgumentException("vertexIndex must be non-negative");
		int corner = vertexIndex % 3;
		return faceReference < 0 ? 2 - corner : corner;
	}

	public static float[] toVulkanClip(float[] openGlClip) {
		if (openGlClip == null || openGlClip.length != 4)
			throw new IllegalArgumentException("Clip position must contain four floats");
		return new float[] { openGlClip[0], openGlClip[1], .5f * (openGlClip[2] + openGlClip[3]), openGlClip[3] };
	}

	public static boolean passesReverseZ(float incomingDepth, float storedDepth) {
		return incomingDepth >= storedDepth;
	}

	public static float[] negativeViewportPoint(float[] clip, int width, int height) {
		if (clip == null || clip.length != 4 || clip[3] == 0)
			throw new IllegalArgumentException("A projectable clip position requires four values and nonzero W");
		if (width < 0 || height < 0) throw new IllegalArgumentException("Viewport extent must be non-negative");
		float ndcX = clip[0] / clip[3];
		float ndcY = clip[1] / clip[3];
		return new float[] { (ndcX + 1) * width * .5f, (1 - ndcY) * height * .5f };
	}

	public static float signedArea2(float[] a, float[] b, float[] c) {
		if (a == null || b == null || c == null || a.length != 2 || b.length != 2 || c.length != 2)
			throw new IllegalArgumentException("Projected points must contain exactly X and Y");
		return (b[0] - a[0]) * (c[1] - a[1]) - (b[1] - a[1]) * (c[0] - a[0]);
	}

	public static boolean isFrontFacing(float[] a, float[] b, float[] c) {
		return signedArea2(a, b, c) > 0;
	}

	private static void validateRanges(List<PreparedDrawRange> ranges, int opaqueCount) {
		List<PreparedDrawRange> ordered = new ArrayList<>(ranges);
		ordered.sort(Comparator.comparingInt(PreparedDrawRange::firstVertex));
		int next = 0;
		for (PreparedDrawRange range : ordered) {
			if (range.pass() != PreparedDrawRange.Pass.OPAQUE || range.firstVertex() % 3 != 0 ||
				range.vertexCount() % 3 != 0 || range.firstVertex() != next)
				throw new IllegalArgumentException("Draw ranges must be contiguous opaque triangles");
			next = Math.addExact(next, range.vertexCount());
			if (next > opaqueCount) throw new IllegalArgumentException("Draw range exceeds the opaque stream");
		}
		if (next != opaqueCount) throw new IllegalArgumentException("Draw ranges must cover the opaque stream exactly");
	}

	private static void validateFaceReferences(IntBuffer vertices, int faceCount) {
		for (int vertex = 0; vertex < vertices.remaining() / VERTEX_STRIDE_INTS; vertex += 3) {
			int reference = vertices.get(vertex * VERTEX_STRIDE_INTS + 6);
			int absolute = reference & 0x7FFFFFFF;
			if (absolute % 3 != 0 || absolute / 3 >= faceCount)
				throw new IllegalArgumentException("Vertex face reference is out of bounds");
			for (int corner = 1; corner < 3; corner++)
				if (vertices.get((vertex + corner) * VERTEX_STRIDE_INTS + 6) != reference)
					throw new IllegalArgumentException("A triangle must use one signed face reference");
		}
	}

	private static List<PreparedFaceMaterials> materialAssociations(IntBuffer metadata) {
		List<PreparedFaceMaterials> materials = new ArrayList<>();
		for (int face = 0; face < metadata.remaining() / FACE_STRIDE_INTS; face++) {
			int base = face * FACE_STRIDE_INTS + 3;
			materials.add(new PreparedFaceMaterials(face * 3, metadata.get(base) >>> MATERIAL_SHIFT,
				metadata.get(base + 1) >>> MATERIAL_SHIFT, metadata.get(base + 2) >>> MATERIAL_SHIFT));
		}
		return Collections.unmodifiableList(materials);
	}

	private static ByteBuffer toLittleEndian(IntBuffer words) {
		ByteBuffer bytes = ByteBuffer.allocate(words.remaining() * Integer.BYTES).order(ByteOrder.LITTLE_ENDIAN);
		IntBuffer copy = words.duplicate();
		while (copy.hasRemaining()) bytes.putInt(copy.get());
		bytes.flip();
		return bytes.asReadOnlyBuffer().order(ByteOrder.LITTLE_ENDIAN);
	}

	public static final class PreparedUpload {
		private final ByteBuffer vertexBytes;
		private final ByteBuffer faceMetadataBytes;
		private final int opaqueVertexCount;
		private final List<PreparedFaceMaterials> faceMaterials;

		private PreparedUpload(ByteBuffer vertexBytes, ByteBuffer faceMetadataBytes, int opaqueVertexCount,
			List<PreparedFaceMaterials> faceMaterials) {
			this.vertexBytes = vertexBytes;
			this.faceMetadataBytes = faceMetadataBytes;
			this.opaqueVertexCount = opaqueVertexCount;
			this.faceMaterials = faceMaterials;
		}

		public ByteBuffer vertexBytes() { return vertexBytes.asReadOnlyBuffer().order(ByteOrder.LITTLE_ENDIAN); }
		public ByteBuffer faceMetadataBytes() { return faceMetadataBytes.asReadOnlyBuffer().order(ByteOrder.LITTLE_ENDIAN); }
		public int opaqueVertexCount() { return opaqueVertexCount; }
		public List<PreparedFaceMaterials> faceMaterials() { return faceMaterials; }
	}

	public static final class Manifest {
		private static final int[] VERTEX_OFFSETS = { 0, 8, 16, 24 };
		private static final String[] VERTEX_FORMATS = {
			"R16G16B16A16_SINT", "R16G16B16A16_SFLOAT", "R16G16B16A16_SINT", "R32_SINT"
		};
		private static final int[] PUSH_OFFSETS = { 0, 64 };

		private Manifest() {}
		public int vertexStride() { return 28; }
		public int[] vertexOffsets() { return VERTEX_OFFSETS.clone(); }
		public String[] vertexFormats() { return VERTEX_FORMATS.clone(); }
		public int metadataArrayStride() { return 4; }
		public int pushConstantSize() { return 72; }
		public int[] pushConstantOffsets() { return PUSH_OFFSETS.clone(); }
		public String pushConstantStage() { return "VERTEX"; }
		public String depthFormat() { return "D32_SFLOAT"; }
		public float depthClear() { return 0; }
		public String depthCompare() { return "GREATER_OR_EQUAL"; }
		public boolean negativeViewportHeight() { return true; }
		public String colorFormat() { return "B8G8R8A8_UNORM"; }
		public String colorSpace() { return "SRGB_NONLINEAR"; }
		public String uiSourceColorBlendFactor() { return "ONE"; }
		public String uiDestinationColorBlendFactor() { return "ONE_MINUS_SRC_ALPHA"; }
		public String frontFace() { return "CLOCKWISE"; }
		public String cullMode() { return "BACK"; }
	}
}
