package rs117.hd.spikes.vulkan.opaque;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import rs117.hd.renderer.FrameOutcome;
import rs117.hd.renderer.PreparedFrame;
import rs117.hd.renderer.RendererBackend;
import rs117.hd.renderer.ZoneKey;
import rs117.hd.renderer.zone.PreparedZoneGeometry;

public final class VulkanZoneResourcePlan implements RendererBackend {
	public enum ResourceKind { OPAQUE_VERTEX_BUFFER, FACE_METADATA_BUFFER }
	public interface ResourceAllocator { Resource allocate(ResourceKind kind, ByteBuffer bytes); }
	public interface Resource extends AutoCloseable { @Override void close(); }

	private final ResourceAllocator allocator;
	private final ResourceLedger ledger = new ResourceLedger();
	private final Map<Location, Allocation> zones = new HashMap<>();
	private boolean closed;
	private long renderWorkCount;

	public VulkanZoneResourcePlan(ResourceAllocator allocator) {
		this.allocator = Objects.requireNonNull(allocator, "allocator");
	}

	@Override
	public synchronized void uploadZone(ZoneKey key, PreparedZoneGeometry geometry) {
		requireOpen();
		Objects.requireNonNull(key, "key");
		VulkanOpaqueZoneContract.PreparedUpload upload = VulkanOpaqueZoneContract.prepare(geometry);
		List<Resource> staged = new ArrayList<>(2);
		try {
			staged.add(acquire(ResourceKind.OPAQUE_VERTEX_BUFFER, upload.vertexBytes()));
			staged.add(acquire(ResourceKind.FACE_METADATA_BUFFER, upload.faceMetadataBytes()));
		} catch (RuntimeException failure) {
			closeResources(staged, failure);
			throw failure;
		}
		Allocation replacement = new Allocation(key, staged);
		Allocation previous = zones.put(Location.of(key), replacement);
		if (previous != null) throwIfTeardownFailed(closeAllocation(previous));
	}

	@Override
	public synchronized FrameOutcome render(PreparedFrame frame) {
		if (closed || frame == null) return FrameOutcome.BACKEND_FAILURE;
		if (frame.viewport().isZero()) return FrameOutcome.SUSPENDED_ZERO_EXTENT;
		Allocation allocation = zones.get(Location.of(frame.zone()));
		if (allocation == null || !allocation.key.equals(frame.zone())) return FrameOutcome.REJECTED_INPUT;
		renderWorkCount++;
		return FrameOutcome.RENDERED;
	}

	@Override
	public synchronized void destroyZone(ZoneKey key) {
		requireOpen();
		Objects.requireNonNull(key, "key");
		Location location = Location.of(key);
		Allocation allocation = zones.get(location);
		if (allocation == null || !allocation.key.equals(key)) return;
		zones.remove(location);
		throwIfTeardownFailed(closeAllocation(allocation));
	}

	public synchronized int liveResourceCount() { return ledger.liveCount(); }
	public synchronized long renderWorkCount() { return renderWorkCount; }

	@Override
	public synchronized void close() {
		if (closed) return;
		closed = true;
		List<RuntimeException> failures = new ArrayList<>();
		for (Allocation allocation : zones.values()) failures.addAll(closeAllocation(allocation));
		zones.clear();
		throwIfTeardownFailed(failures);
	}

	private Resource acquire(ResourceKind kind, ByteBuffer bytes) {
		Resource resource = Objects.requireNonNull(allocator.allocate(kind, bytes.asReadOnlyBuffer()), "allocated resource");
		ledger.acquire();
		return resource;
	}

	private List<RuntimeException> closeAllocation(Allocation allocation) {
		List<RuntimeException> failures = new ArrayList<>();
		closeResources(allocation.resources, failures);
		return failures;
	}

	private void closeResources(List<Resource> resources, RuntimeException primary) {
		List<RuntimeException> failures = new ArrayList<>();
		closeResources(resources, failures);
		for (RuntimeException failure : failures) primary.addSuppressed(failure);
	}

	private void closeResources(List<Resource> resources, List<RuntimeException> failures) {
		for (int index = resources.size() - 1; index >= 0; index--) {
			try {
				resources.get(index).close();
			} catch (RuntimeException failure) {
				failures.add(failure);
			} finally {
				ledger.release();
			}
		}
	}

	private static void throwIfTeardownFailed(List<RuntimeException> failures) {
		if (failures.isEmpty()) return;
		ResourceTeardownException aggregate = new ResourceTeardownException("One or more planned resources failed to close");
		for (RuntimeException failure : failures) aggregate.addSuppressed(failure);
		throw aggregate;
	}

	private void requireOpen() {
		if (closed) throw new IllegalStateException("Resource plan is closed");
	}

	public static final class ResourceLedger {
		private int live;
		public synchronized void acquire() { live = Math.addExact(live, 1); }
		public synchronized void release() {
			if (live == 0) throw new IllegalStateException("Resource ledger underflow");
			live--;
		}
		public synchronized int liveCount() { return live; }
	}

	public static final class ResourceTeardownException extends RuntimeException {
		public ResourceTeardownException(String message) { super(message); }
	}

	private static final class Allocation {
		final ZoneKey key;
		final List<Resource> resources;
		Allocation(ZoneKey key, List<Resource> resources) { this.key = key; this.resources = resources; }
	}

	private static final class Location {
		final int worldViewId;
		final int zoneX;
		final int zoneZ;
		Location(int worldViewId, int zoneX, int zoneZ) {
			this.worldViewId = worldViewId;
			this.zoneX = zoneX;
			this.zoneZ = zoneZ;
		}
		static Location of(ZoneKey key) { return new Location(key.worldViewId(), key.zoneX(), key.zoneZ()); }
		@Override public boolean equals(Object other) {
			return this == other || other instanceof Location && worldViewId == ((Location) other).worldViewId &&
				zoneX == ((Location) other).zoneX && zoneZ == ((Location) other).zoneZ;
		}
		@Override public int hashCode() { return Objects.hash(worldViewId, zoneX, zoneZ); }
	}
}
