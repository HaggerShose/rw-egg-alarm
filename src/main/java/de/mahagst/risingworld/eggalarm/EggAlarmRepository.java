package de.mahagst.risingworld.eggalarm;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import net.risingworld.api.database.Database;

/**
 * SQLite persistence for alarm devices and their egg assignment.
 * No gameplay lookups. Devices hold settings; eggs are replaceable.
 */
final class EggAlarmRepository {
	private final Database database;

	EggAlarmRepository(Database database) {
		this.database = database;
	}

	/**
	 * Creates {@code alarm_devices} and {@code alarm_eggs}, copies a legacy
	 * {@code egg_links} table when present, then drops it.
	 */
	void createSchema() {
		database.execute("PRAGMA foreign_keys = ON");
		database.execute("PRAGMA journal_mode=DELETE");
		database.execute("""
				CREATE TABLE IF NOT EXISTS alarm_devices (
				  device_object_id INTEGER NOT NULL,
				  device_cx INTEGER NOT NULL,
				  device_cy INTEGER NOT NULL,
				  device_cz INTEGER NOT NULL,
				  owner_uid TEXT NOT NULL,
				  sound_id INTEGER NOT NULL DEFAULT 1,
				  max_distance INTEGER NOT NULL DEFAULT 64,
				  volume REAL NOT NULL DEFAULT 1.0,
				  created_at INTEGER NOT NULL,
				  PRIMARY KEY (device_object_id, device_cx, device_cy, device_cz)
				)
				""");
		database.execute("""
				CREATE TABLE IF NOT EXISTS alarm_eggs (
				  egg_creation_date INTEGER NOT NULL,
				  egg_x REAL NOT NULL,
				  egg_y REAL NOT NULL,
				  egg_z REAL NOT NULL,
				  egg_variant INTEGER NOT NULL,
				  device_object_id INTEGER NOT NULL,
				  device_cx INTEGER NOT NULL,
				  device_cy INTEGER NOT NULL,
				  device_cz INTEGER NOT NULL,
				  PRIMARY KEY (egg_creation_date, egg_x, egg_y, egg_z),
				  UNIQUE (device_object_id, device_cx, device_cy, device_cz),
				  FOREIGN KEY (device_object_id, device_cx, device_cy, device_cz)
				    REFERENCES alarm_devices (device_object_id, device_cx, device_cy, device_cz)
				    ON DELETE CASCADE
				)
				""");
		SqliteSchema.ensureColumn(database, "alarm_devices", "volume", "REAL NOT NULL DEFAULT 1.0");
		migrateLegacyLinks();
	}

	/**
	 * @return all devices, or {@code null} if the query failed
	 */
	List<AlarmDevice> findDevices() {
		var devices = new ArrayList<AlarmDevice>();
		var sql = "SELECT * FROM alarm_devices";
		try (var prep = database.getConnection().prepareStatement(sql);
				var result = prep.executeQuery()) {
			while (result.next()) {
				devices.add(new AlarmDevice(
						result.getLong("device_object_id"),
						result.getInt("device_cx"),
						result.getInt("device_cy"),
						result.getInt("device_cz"),
						result.getString("owner_uid"),
						result.getInt("sound_id"),
						result.getInt("max_distance"),
						result.getFloat("volume"),
						result.getLong("created_at")));
			}
		} catch (SQLException e) {
			e.printStackTrace();
			return null;
		}
		return devices;
	}

	/**
	 * @return all egg rows, or {@code null} if the query failed
	 */
	List<AlarmEgg> findEggs() {
		var eggs = new ArrayList<AlarmEgg>();
		var sql = "SELECT * FROM alarm_eggs";
		try (var prep = database.getConnection().prepareStatement(sql);
				var result = prep.executeQuery()) {
			while (result.next()) {
				eggs.add(new AlarmEgg(
						result.getLong("egg_creation_date"),
						result.getFloat("egg_x"),
						result.getFloat("egg_y"),
						result.getFloat("egg_z"),
						result.getInt("egg_variant"),
						result.getLong("device_object_id"),
						result.getInt("device_cx"),
						result.getInt("device_cy"),
						result.getInt("device_cz")));
			}
		} catch (SQLException e) {
			e.printStackTrace();
			return null;
		}
		return eggs;
	}

	/** Insert a new device. Fails if that device key already exists. */
	boolean insertDevice(AlarmDevice device) {
		var sql = """
				INSERT INTO alarm_devices (
				  device_object_id, device_cx, device_cy, device_cz,
				  owner_uid, sound_id, max_distance, volume, created_at
				) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
				""";
		try (var prep = database.getConnection().prepareStatement(sql)) {
			prep.setLong(1, device.objectId);
			prep.setInt(2, device.cx);
			prep.setInt(3, device.cy);
			prep.setInt(4, device.cz);
			prep.setString(5, device.ownerUid);
			prep.setInt(6, device.soundId);
			prep.setInt(7, device.maxDistance);
			prep.setDouble(8, device.volume);
			prep.setLong(9, device.createdAt);
			prep.executeUpdate();
			return true;
		} catch (SQLException e) {
			e.printStackTrace();
			return false;
		}
	}

	/** Writes the linker as owner. Sound, range, volume, and created_at stay. */
	boolean updateOwner(AlarmDevice device) {
		var sql = """
				UPDATE alarm_devices
				SET owner_uid = ?
				WHERE device_object_id = ? AND device_cx = ? AND device_cy = ? AND device_cz = ?
				""";
		try (var prep = database.getConnection().prepareStatement(sql)) {
			prep.setString(1, device.ownerUid);
			prep.setLong(2, device.objectId);
			prep.setInt(3, device.cx);
			prep.setInt(4, device.cy);
			prep.setInt(5, device.cz);
			return prep.executeUpdate() > 0;
		} catch (SQLException e) {
			e.printStackTrace();
			return false;
		}
	}

	/** Writes sound, range, and volume. Owner and created_at stay. */
	boolean updateSettings(AlarmDevice device) {
		var sql = """
				UPDATE alarm_devices
				SET sound_id = ?, max_distance = ?, volume = ?
				WHERE device_object_id = ? AND device_cx = ? AND device_cy = ? AND device_cz = ?
				""";
		try (var prep = database.getConnection().prepareStatement(sql)) {
			prep.setInt(1, device.soundId);
			prep.setInt(2, device.maxDistance);
			prep.setDouble(3, device.volume);
			prep.setLong(4, device.objectId);
			prep.setInt(5, device.cx);
			prep.setInt(6, device.cy);
			prep.setInt(7, device.cz);
			return prep.executeUpdate() > 0;
		} catch (SQLException e) {
			e.printStackTrace();
			return false;
		}
	}

	/** Deletes the device. {@code ON DELETE CASCADE} removes its egg row. */
	boolean deleteDevice(AlarmDevice device) {
		var sql = """
				DELETE FROM alarm_devices
				WHERE device_object_id = ? AND device_cx = ? AND device_cy = ? AND device_cz = ?
				""";
		try (var prep = database.getConnection().prepareStatement(sql)) {
			prep.setLong(1, device.objectId);
			prep.setInt(2, device.cx);
			prep.setInt(3, device.cy);
			prep.setInt(4, device.cz);
			prep.executeUpdate();
			return true;
		} catch (SQLException e) {
			e.printStackTrace();
			return false;
		}
	}

	/** Removes one egg assignment. The device row stays. */
	boolean deleteEgg(AlarmEgg egg) {
		var sql = """
				DELETE FROM alarm_eggs
				WHERE egg_creation_date = ? AND egg_x = ? AND egg_y = ? AND egg_z = ?
				""";
		try (var prep = database.getConnection().prepareStatement(sql)) {
			prep.setLong(1, egg.creationDate);
			prep.setDouble(2, egg.x);
			prep.setDouble(3, egg.y);
			prep.setDouble(4, egg.z);
			prep.executeUpdate();
			return true;
		} catch (SQLException e) {
			e.printStackTrace();
			return false;
		}
	}

	/** Insert or replace the egg row (same primary key updates the device). */
	boolean upsertEgg(AlarmEgg egg) {
		var sql = """
				INSERT INTO alarm_eggs (
				  egg_creation_date, egg_x, egg_y, egg_z, egg_variant,
				  device_object_id, device_cx, device_cy, device_cz
				) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
				ON CONFLICT(egg_creation_date, egg_x, egg_y, egg_z) DO UPDATE SET
				  egg_variant = excluded.egg_variant,
				  device_object_id = excluded.device_object_id,
				  device_cx = excluded.device_cx,
				  device_cy = excluded.device_cy,
				  device_cz = excluded.device_cz
				""";
		try (var prep = database.getConnection().prepareStatement(sql)) {
			prep.setLong(1, egg.creationDate);
			prep.setDouble(2, egg.x);
			prep.setDouble(3, egg.y);
			prep.setDouble(4, egg.z);
			prep.setInt(5, egg.variant);
			prep.setLong(6, egg.deviceObjectId);
			prep.setInt(7, egg.deviceCx);
			prep.setInt(8, egg.deviceCy);
			prep.setInt(9, egg.deviceCz);
			prep.executeUpdate();
			return true;
		} catch (SQLException e) {
			e.printStackTrace();
			return false;
		}
	}

	/**
	 * Copies {@code egg_links} into the two tables, then drops it.
	 * One row per device: {@code INSERT OR IGNORE} keeps the first device row
	 * and its egg. Volume is stored as {@code 1.0}. A failed copy does not drop
	 * the old table.
	 */
	private void migrateLegacyLinks() {
		if (!tableExists("egg_links")) {
			return;
		}
		SqliteSchema.ensureColumn(database, "egg_links", "sound_id", "INTEGER NOT NULL DEFAULT 1");
		SqliteSchema.ensureColumn(database, "egg_links", "owner_uid", "TEXT NOT NULL DEFAULT ''");
		SqliteSchema.ensureColumn(database, "egg_links", "max_distance", "INTEGER NOT NULL DEFAULT 64");
		try (var statement = database.getConnection().createStatement()) {
			statement.executeUpdate("""
					INSERT OR IGNORE INTO alarm_devices (
					  device_object_id, device_cx, device_cy, device_cz,
					  owner_uid, sound_id, max_distance, volume, created_at
					)
					SELECT device_object_id, device_cx, device_cy, device_cz,
					  owner_uid, sound_id, max_distance, 1.0, created_at
					FROM egg_links
					""");
			statement.executeUpdate("""
					INSERT OR IGNORE INTO alarm_eggs (
					  egg_creation_date, egg_x, egg_y, egg_z, egg_variant,
					  device_object_id, device_cx, device_cy, device_cz
					)
					SELECT egg_creation_date, egg_x, egg_y, egg_z, egg_variant,
					  device_object_id, device_cx, device_cy, device_cz
					FROM egg_links
					""");
			statement.executeUpdate("DROP TABLE egg_links");
		} catch (SQLException e) {
			e.printStackTrace();
			throw new IllegalStateException("Failed to migrate egg_links", e);
		}
	}

	private boolean tableExists(String table) {
		var sql = "SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = ?";
		try (var prep = database.getConnection().prepareStatement(sql)) {
			prep.setString(1, table);
			try (var result = prep.executeQuery()) {
				return result.next();
			}
		} catch (SQLException e) {
			e.printStackTrace();
			throw new IllegalStateException("Failed to read sqlite_master", e);
		}
	}
}
