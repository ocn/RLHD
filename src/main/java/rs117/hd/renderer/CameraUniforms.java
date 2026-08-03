package rs117.hd.renderer;

import java.util.Arrays;

public final class CameraUniforms {
	private static final int MATRIX_FLOATS = 16;
	private final float[] clipFromWorld;

	public CameraUniforms(float[] clipFromWorld) {
		if (clipFromWorld == null || clipFromWorld.length != MATRIX_FLOATS)
			throw new IllegalArgumentException("clipFromWorld must contain exactly 16 floats");
		this.clipFromWorld = clipFromWorld.clone();
	}

	public float[] clipFromWorld() { return clipFromWorld.clone(); }

	@Override
	public boolean equals(Object other) {
		return this == other || other instanceof CameraUniforms &&
			Arrays.equals(clipFromWorld, ((CameraUniforms) other).clipFromWorld);
	}

	@Override
	public int hashCode() { return Arrays.hashCode(clipFromWorld); }
}
