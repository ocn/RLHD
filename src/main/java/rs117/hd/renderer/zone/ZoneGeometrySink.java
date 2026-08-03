package rs117.hd.renderer.zone;

@FunctionalInterface
public interface ZoneGeometrySink {
	void accept(PreparedZoneGeometry geometry);
}
