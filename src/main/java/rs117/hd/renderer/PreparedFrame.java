package rs117.hd.renderer;

import java.util.Objects;

public final class PreparedFrame {
	private final ZoneKey zone;
	private final CameraUniforms camera;
	private final int sceneBaseX;
	private final int sceneBaseZ;
	private final SurfaceExtent viewport;
	private final PreparedUiTexture ui;

	public PreparedFrame(ZoneKey zone, CameraUniforms camera, int sceneBaseX, int sceneBaseZ,
		SurfaceExtent viewport, PreparedUiTexture ui) {
		this.zone = Objects.requireNonNull(zone, "zone");
		this.camera = Objects.requireNonNull(camera, "camera");
		this.sceneBaseX = sceneBaseX;
		this.sceneBaseZ = sceneBaseZ;
		this.viewport = Objects.requireNonNull(viewport, "viewport");
		this.ui = Objects.requireNonNull(ui, "ui");
	}

	public ZoneKey zone() { return zone; }
	public CameraUniforms camera() { return camera; }
	public int sceneBaseX() { return sceneBaseX; }
	public int sceneBaseZ() { return sceneBaseZ; }
	public SurfaceExtent viewport() { return viewport; }
	public PreparedUiTexture ui() { return ui; }
}
