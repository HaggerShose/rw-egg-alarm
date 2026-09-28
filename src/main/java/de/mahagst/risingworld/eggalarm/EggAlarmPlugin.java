package de.mahagst.risingworld.eggalarm;

import net.risingworld.api.Plugin;
import net.risingworld.api.Server;
import net.risingworld.api.World;
import net.risingworld.api.database.Database;
import net.risingworld.api.events.EventMethod;
import net.risingworld.api.events.Listener;
import net.risingworld.api.events.Threading;
import net.risingworld.api.events.player.PlayerKeyEvent;
import net.risingworld.api.events.player.PlayerSpawnEvent;
import net.risingworld.api.events.world.ItemTransformEvent;
import net.risingworld.api.objects.MetaObject;
import net.risingworld.api.objects.Player;
import net.risingworld.api.utils.Key;

/**
 * Opens the egg radial menu with Ctrl+O while looking at a placed rainbow egg.
 * Linked eggs open only for the linking player or an admin. F pickup stays vanilla.
 * A finished item transform on a linked device plays the alarm at the egg.
 */
public class EggAlarmPlugin extends Plugin implements Listener {
	private Database database;
	private EggAlarmUI ui;
	private EggAlarmService service;
	private boolean listening;

	@Override
	public void onEnable() {
		EggAlarmSounds sounds = new EggAlarmSounds(this);
		sounds.load();
		String dbFile = worldDbFileName();
		database = getSQLiteConnection(getPath() + "/" + dbFile);
		if (database == null) {
			System.out.println("[EggAlarm] Failed to open SQLite database: " + dbFile);
			return;
		}
		EggAlarmRepository repository = new EggAlarmRepository(database);
		repository.createSchema();
		service = new EggAlarmService(this, repository, sounds);
		if (!service.loadLinks()) {
			System.out.println("[EggAlarm] Failed to load egg links");
			database.close();
			database = null;
			service = null;
			return;
		}
		ui = new EggAlarmUI(this, service);
		ui.load();
		service.attach(ui);
		registerEventListener(this);
		listening = true;
		for (Player player : Server.getAllPlayers()) {
			registerHotkeys(player);
		}
		System.out.println("[EggAlarm] enabled (" + dbFile + ")");
	}

	@Override
	public void onDisable() {
		if (listening) {
			unregisterEventListener(this);
			listening = false;
		}
		for (Player player : Server.getAllPlayers()) {
			unregisterHotkeys(player);
		}
		if (database != null) {
			database.execute("PRAGMA wal_checkpoint(TRUNCATE)");
			database.close();
			database = null;
		}
		System.out.println("[EggAlarm] disabled");
	}

	/** Registers Ctrl+O for players that spawn after enable. */
	@EventMethod(Threading.Sync)
	public void onSpawn(PlayerSpawnEvent event) {
		registerHotkeys(event.getPlayer());
	}

	/**
	 * Ctrl+O: raycast along the crosshair and open the menu if a rainbow egg is hit.
	 * Other keys (including plain O / pocket watch) are ignored.
	 */
	@EventMethod(Threading.Sync)
	public void onKey(PlayerKeyEvent event) {
		if (!listening || service == null || !event.isPressed() || event.getKey() != Key.O) {
			return;
		}
		Player player = event.getPlayer();
		if (!player.isKeyPressed(Key.LeftCtrl) && !player.isKeyPressed(Key.RightCtrl)) {
			return;
		}
		if (ui == null || !ui.isReady()) {
			return;
		}
		service.tryOpenFromLook(player);
	}

	/**
	 * Linked furnace / grill / oven finished a transform: play the alarm at the egg.
	 * Cancelled transforms and non-meta triggers are ignored. No SQLite on this path.
	 */
	@EventMethod(Threading.Sync)
	public void onItemTransform(ItemTransformEvent event) {
		if (service == null || event.isCancelled()) {
			return;
		}
		if (event.getTrigger() != ItemTransformEvent.Trigger.MetaObject) {
			return;
		}
		MetaObject meta = event.getMetaObject();
		if (meta == null || event.getObjectID() < 0) {
			return;
		}
		service.onItemTransformed(
				event.getObjectID(),
				meta.getChunkPositionX(),
				meta.getChunkPositionY(),
				meta.getChunkPositionZ());
	}

	/**
	 * One SQLite file per world: {@code <World.getName()>.db}.
	 * Path-unsafe characters become {@code _}.
	 */
	private static String worldDbFileName() {
		String name = World.getName();
		if (name == null || name.isBlank()) {
			return "world.db";
		}
		String safe = name.trim().replaceAll("[\\\\/:*?\"<>|]", "_");
		if (safe.isBlank()) {
			return "world.db";
		}
		return safe + ".db";
	}

	private static void registerHotkeys(Player player) {
		player.registerKeys(Key.O, Key.LeftCtrl, Key.RightCtrl);
		player.setListenForKeyInput(true);
	}

	private static void unregisterHotkeys(Player player) {
		player.unregisterKeys(Key.O, Key.LeftCtrl, Key.RightCtrl);
	}
}
