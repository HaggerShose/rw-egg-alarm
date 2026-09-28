// Copied from _tools/templates/SqliteSchema.java. Package adjusted for EggAlarm.
// After CREATE TABLE IF NOT EXISTS, call ensureColumn for each column added after v1.
package de.mahagst.risingworld.eggalarm;

import java.sql.SQLException;

import net.risingworld.api.database.Database;

/** Idempotent SQLite column ensure. Table and column names must be code constants. */
final class SqliteSchema {
	private SqliteSchema() {
	}

	static void ensureColumn(Database database, String table, String column, String typeSql) {
		if (hasColumn(database, table, column)) {
			return;
		}
		String sql = "ALTER TABLE " + quoteIdent(table) + " ADD COLUMN " + quoteIdent(column) + " " + typeSql;
		try (var statement = database.getConnection().createStatement()) {
			statement.execute(sql);
		} catch (SQLException e) {
			e.printStackTrace();
			throw new IllegalStateException("Failed to add column " + column + " to " + table, e);
		}
	}

	private static boolean hasColumn(Database database, String table, String column) {
		String sql = "PRAGMA table_info(" + quoteIdent(table) + ")";
		try (var statement = database.getConnection().createStatement();
				var result = statement.executeQuery(sql)) {
			while (result.next()) {
				if (column.equalsIgnoreCase(result.getString("name"))) {
					return true;
				}
			}
		} catch (SQLException e) {
			e.printStackTrace();
			throw new IllegalStateException("Failed to read schema of " + table, e);
		}
		return false;
	}

	private static String quoteIdent(String ident) {
		if (ident == null || !ident.matches("[A-Za-z_][A-Za-z0-9_]*")) {
			throw new IllegalArgumentException("Invalid SQL identifier: " + ident);
		}
		return "\"" + ident + "\"";
	}
}
