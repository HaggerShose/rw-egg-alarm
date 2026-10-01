package de.mahagst.risingworld.eggalarm;

/**
 * Registered alarm device. Owner, sound, range, and volume live here.
 * The egg is a separate row and can be replaced without resetting settings.
 * {@link #volume} is {@code 0}..{@code 1} in 5% steps and is used by playback.
 */
final class AlarmDevice {
	/** Default playback volume stored on a new device (80%). */
	static final float DEFAULT_VOLUME = 0.8f;

	final long objectId;
	final int cx;
	final int cy;
	final int cz;
	final String ownerUid;
	final int soundId;
	/** Hear radius in meters. Only 32, 64, 128, or 256. */
	final int maxDistance;
	/** Playback volume, 0..1 in steps of 0.05. */
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
		this.volume = normalizeVolume(volume);
		this.createdAt = createdAt;
	}

	/** Clamps to 0..1 and snaps to the nearest 5%. */
	static float normalizeVolume(float volume) {
		return volumePercent(volume) / 100f;
	}

	/** {@link #volume} as 0..100, snapped to 5. */
	static int volumePercent(float volume) {
		int percent = Math.round(volume * 100f);
		if (percent < 0) {
			percent = 0;
		} else if (percent > 100) {
			percent = 100;
		}
		int snapped = Math.round(percent / 5f) * 5;
		if (snapped < 0) {
			return 0;
		}
		if (snapped > 100) {
			return 100;
		}
		return snapped;
	}

	/** Allowed hear radii only; anything else becomes 64. */
	static int normalizeDistance(int meters) {
		return meters == 32 || meters == 64 || meters == 128 || meters == 256 ? meters : 64;
	}
}
