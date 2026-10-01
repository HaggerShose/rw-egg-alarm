package de.mahagst.risingworld.eggalarm;

/**
 * Egg assigned to one {@link AlarmDevice}. {@link #eggGlobalId} is session-only
 * and is not stored. At most one egg row per device.
 */
final class AlarmEgg {
	final long creationDate;
	final float x;
	final float y;
	final float z;
	final int variant;
	final long deviceObjectId;
	final int deviceCx;
	final int deviceCy;
	final int deviceCz;
	/** Session item id. Null until the egg is loaded or looked at. */
	Long eggGlobalId;

	AlarmEgg(
			long creationDate,
			float x,
			float y,
			float z,
			int variant,
			long deviceObjectId,
			int deviceCx,
			int deviceCy,
			int deviceCz) {
		this.creationDate = creationDate;
		this.x = x;
		this.y = y;
		this.z = z;
		this.variant = variant;
		this.deviceObjectId = deviceObjectId;
		this.deviceCx = deviceCx;
		this.deviceCy = deviceCy;
		this.deviceCz = deviceCz;
	}
}
