package de.mahagst.risingworld.eggalarm;

/**
 * One egg linked to one device. {@link #eggGlobalId} is session-only and not stored.
 */
final class EggLink {
	final long creationDate;
	final float x;
	final float y;
	final float z;
	final int variant;
	final long deviceObjectId;
	final int deviceCx;
	final int deviceCy;
	final int deviceCz;
	final String ownerUid;
	final int soundId;
	/** Hear radius in meters. Only 32, 64, or 128. */
	final int maxDistance;
	final long createdAt;
	Long eggGlobalId;

	EggLink(
			long creationDate,
			float x,
			float y,
			float z,
			int variant,
			long deviceObjectId,
			int deviceCx,
			int deviceCy,
			int deviceCz,
			String ownerUid,
			int soundId,
			int maxDistance,
			long createdAt) {
		this.creationDate = creationDate;
		this.x = x;
		this.y = y;
		this.z = z;
		this.variant = variant;
		this.deviceObjectId = deviceObjectId;
		this.deviceCx = deviceCx;
		this.deviceCy = deviceCy;
		this.deviceCz = deviceCz;
		this.ownerUid = ownerUid;
		this.soundId = soundId;
		this.maxDistance = normalizeDistance(maxDistance);
		this.createdAt = createdAt;
	}

	/** Allowed hear radii only; anything else becomes 64. */
	static int normalizeDistance(int meters) {
		return meters == 32 || meters == 64 || meters == 128 ? meters : 64;
	}
}
