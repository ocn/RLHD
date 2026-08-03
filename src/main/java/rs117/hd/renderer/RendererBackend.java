package rs117.hd.renderer;

import rs117.hd.renderer.zone.PreparedZoneGeometry;

public interface RendererBackend extends AutoCloseable {
	void uploadZone(ZoneKey key, PreparedZoneGeometry geometry);
	FrameOutcome render(PreparedFrame frame);
	void destroyZone(ZoneKey key);
	@Override void close();
}
