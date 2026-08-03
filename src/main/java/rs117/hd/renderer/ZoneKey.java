package rs117.hd.renderer;

import java.util.Objects;

public final class ZoneKey {
	private final int worldViewId;
	private final int zoneX;
	private final int zoneZ;
	private final long generation;

	public ZoneKey(int worldViewId, int zoneX, int zoneZ, long generation) {
		if (zoneX < 0 || zoneZ < 0 || generation < 0)
			throw new IllegalArgumentException("Zone coordinates and generation must be non-negative");
		this.worldViewId = worldViewId;
		this.zoneX = zoneX;
		this.zoneZ = zoneZ;
		this.generation = generation;
	}

	public int worldViewId() { return worldViewId; }
	public int zoneX() { return zoneX; }
	public int zoneZ() { return zoneZ; }
	public long generation() { return generation; }

	@Override
	public boolean equals(Object other) {
		if (this == other) return true;
		if (!(other instanceof ZoneKey)) return false;
		ZoneKey key = (ZoneKey) other;
		return worldViewId == key.worldViewId && zoneX == key.zoneX && zoneZ == key.zoneZ && generation == key.generation;
	}

	@Override
	public int hashCode() { return Objects.hash(worldViewId, zoneX, zoneZ, generation); }
}
