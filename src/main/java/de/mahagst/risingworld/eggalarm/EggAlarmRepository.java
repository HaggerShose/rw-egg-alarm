package de.mahagst.risingworld.eggalarm;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import net.risingworld.api.database.Database;

/** SQLite persistence for egg-to-device links. No gameplay lookups. */
final class EggAlarmRepository {
	private final Database database;

	EggAlarmRepository(Database database) {
		this.database = database;
	}

	void createSchema() {
		database.execute("PRAGMA foreign_keys = ON");
		database.execute("PRAGMA journal_mode=DELETE");
		database.execute("""
				CREATE TABLE IF NOT EXISTS egg_links (
				  egg_creation_date INTEGER NOT NULL,
				  egg_x REAL NOT NULL,
				  egg_y REAL NOT NULL,
				  egg_z REAL NOT NULL,
				  egg_variant INTEGER NOT NULL,
				  device_object_id INTEGER NOT NULL,
				  device_cx INTEGER NOT NULL,
				  device_cy INTEGER NOT NULL,
				  device_cz INTEGER NOT NULL,
				  owner_uid TEXT NOT NULL,
				  sound_id INTEGER NOT NULL DEFAULT 1,
				  created_at INTEGER NOT NULL,
				  PRIMARY KEY (egg_creation_date, egg_x, egg_y, egg_z)
				)
				""");
		database.execute("""
				CREATE UNIQUE INDEX IF NOT EXISTS egg_links_device
				ON egg_links (device_object_id, device_cx, device_cy, device_cz)
				""");
		SqliteSchema.ensureColumn(database, "egg_links", "sound_id", "INTEGER NOT NULL DEFAULT 1");
		SqliteSchema.ensureColumn(database, "egg_links", "owner_uid", "TEXT NOT NULL DEFAULT ''");
	}

	/**
	 * @return all rows, or {@code null} if the query failed
	 */
	List<EggLink> findAll() {
		var links = new ArrayList<EggLink>();
		var sql = "SELECT * FROM egg_links";
		try (var prep = database.getConnection().prepareStatement(sql);
				var result = prep.executeQuery()) {
			while (result.next()) {
				links.add(new EggLink(
						result.getLong("egg_creation_date"),
						result.getFloat("egg_x"),
						result.getFloat("egg_y"),
						result.getFloat("egg_z"),
						result.getInt("egg_variant"),
						result.getLong("device_object_id"),
						result.getInt("device_cx"),
						result.getInt("device_cy"),
						result.getInt("device_cz"),
						result.getString("owner_uid"),
						result.getInt("sound_id"),
						result.getLong("created_at")));
			}
		} catch (SQLException e) {
			e.printStackTrace();
			return null;
		}
		return links;
	}

	/** Insert or replace the egg row (same primary key updates the device and sound). */
	boolean upsert(EggLink link) {
		var sql = """
				INSERT INTO egg_links (
				  egg_creation_date, egg_x, egg_y, egg_z, egg_variant,
				  device_object_id, device_cx, device_cy, device_cz,
				  owner_uid, sound_id, created_at
				) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
				ON CONFLICT(egg_creation_date, egg_x, egg_y, egg_z) DO UPDATE SET
				  egg_variant = excluded.egg_variant,
				  device_object_id = excluded.device_object_id,
				  device_cx = excluded.device_cx,
				  device_cy = excluded.device_cy,
				  device_cz = excluded.device_cz,
				  owner_uid = excluded.owner_uid,
				  sound_id = excluded.sound_id
				""";
		try (var prep = database.getConnection().prepareStatement(sql)) {
			bind(prep, link);
			prep.executeUpdate();
			return true;
		} catch (SQLException e) {
			e.printStackTrace();
			return false;
		}
	}

	boolean delete(EggLink link) {
		var sql = """
				DELETE FROM egg_links
				WHERE egg_creation_date = ? AND egg_x = ? AND egg_y = ? AND egg_z = ?
				""";
		try (var prep = database.getConnection().prepareStatement(sql)) {
			prep.setLong(1, link.creationDate);
			prep.setDouble(2, link.x);
			prep.setDouble(3, link.y);
			prep.setDouble(4, link.z);
			prep.executeUpdate();
			return true;
		} catch (SQLException e) {
			e.printStackTrace();
			return false;
		}
	}

	private static void bind(java.sql.PreparedStatement prep, EggLink link) throws SQLException {
		prep.setLong(1, link.creationDate);
		prep.setDouble(2, link.x);
		prep.setDouble(3, link.y);
		prep.setDouble(4, link.z);
		prep.setInt(5, link.variant);
		prep.setLong(6, link.deviceObjectId);
		prep.setInt(7, link.deviceCx);
		prep.setInt(8, link.deviceCy);
		prep.setInt(9, link.deviceCz);
		prep.setString(10, link.ownerUid);
		prep.setInt(11, link.soundId);
		prep.setLong(12, link.createdAt);
	}
}
