package de.mahagst.risingworld.eggalarm;

/**
 * Registered alarm device. Owner, sound, range, and volume live here.
 * The egg is a separate row and can be replaced without resetting settings.
 * {@link #volume} is stored for a later control; playback still uses a fixed volume.
 */
final class AlarmDevice {
	/** Default playback volume stored on a new device. */
	static final float DEFAULT_VOLUME = 1f;

	final long objectId;
	final int cx;
	final int cy;
	final int cz;
	final String ownerUid;
	final int soundId;
	/** Hear radius in meters. Only 32, 64, or 128. */
	final int maxDistance;
	/** Stored volume. Not applied by {@code playAt} yet. */
	final float volume;
	final long createdAt;

	AlarmDevice(
			long objectId,
			int cx,
			int cy,
			int cz,
			String ownerUid,
			int soundId,
			int maxDistance,
			float volume,
			long createdAt) {
		this.objectId = objectId;
		this.cx = cx;
		this.cy = cy;
		this.cz = cz;
		this.ownerUid = ownerUid;
		this.soundId = soundId;
		this.maxDistance = normalizeDistance(maxDistance);
		this.volume = volume;
		this.createdAt = createdAt;
	}

	/** Allowed hear radii only; anything else becomes 64. */
	static int normalizeDistance(int meters) {
		return meters == 32 || meters == 64 || meters == 128 ? meters : 64;
	}
}
