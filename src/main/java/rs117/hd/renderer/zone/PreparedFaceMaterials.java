package rs117.hd.renderer.zone;

import java.util.Objects;

public final class PreparedFaceMaterials {
	private final int texturedFaceIndex;
	private final int materialIdA;
	private final int materialIdB;
	private final int materialIdC;

	public PreparedFaceMaterials(int texturedFaceIndex, int materialIdA, int materialIdB, int materialIdC) {
		if (texturedFaceIndex < 0 || materialIdA < 0 || materialIdB < 0 || materialIdC < 0)
			throw new IllegalArgumentException("Face and material indices must be non-negative");
		this.texturedFaceIndex = texturedFaceIndex;
		this.materialIdA = materialIdA;
		this.materialIdB = materialIdB;
		this.materialIdC = materialIdC;
	}

	public int texturedFaceIndex() {
		return texturedFaceIndex;
	}

	public int materialIdA() {
		return materialIdA;
	}

	public int materialIdB() {
		return materialIdB;
	}

	public int materialIdC() {
		return materialIdC;
	}

	@Override
	public boolean equals(Object other) {
		if (this == other)
			return true;
		if (!(other instanceof PreparedFaceMaterials))
			return false;
		PreparedFaceMaterials materials = (PreparedFaceMaterials) other;
		return texturedFaceIndex == materials.texturedFaceIndex &&
			materialIdA == materials.materialIdA &&
			materialIdB == materials.materialIdB &&
			materialIdC == materials.materialIdC;
	}

	@Override
	public int hashCode() {
		return Objects.hash(texturedFaceIndex, materialIdA, materialIdB, materialIdC);
	}
}
