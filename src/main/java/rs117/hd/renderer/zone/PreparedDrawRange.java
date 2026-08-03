package rs117.hd.renderer.zone;

import java.util.Objects;

public final class PreparedDrawRange {
	public static final int MIXED_MATERIAL_ID = -1;

	public enum Pass {
		OPAQUE
	}

	private final int firstVertex;
	private final int vertexCount;
	private final int materialId;
	private final Pass pass;

	public PreparedDrawRange(int firstVertex, int vertexCount, int materialId, Pass pass) {
		if (firstVertex < 0 || vertexCount < 0)
			throw new IllegalArgumentException("Draw ranges must be non-negative");
		this.firstVertex = firstVertex;
		this.vertexCount = vertexCount;
		this.materialId = materialId;
		this.pass = Objects.requireNonNull(pass);
	}

	public int firstVertex() {
		return firstVertex;
	}

	public int vertexCount() {
		return vertexCount;
	}

	public int materialId() {
		return materialId;
	}

	public Pass pass() {
		return pass;
	}

	@Override
	public boolean equals(Object other) {
		if (this == other)
			return true;
		if (!(other instanceof PreparedDrawRange))
			return false;
		PreparedDrawRange range = (PreparedDrawRange) other;
		return firstVertex == range.firstVertex &&
			vertexCount == range.vertexCount &&
			materialId == range.materialId &&
			pass == range.pass;
	}

	@Override
	public int hashCode() {
		return Objects.hash(firstVertex, vertexCount, materialId, pass);
	}
}
