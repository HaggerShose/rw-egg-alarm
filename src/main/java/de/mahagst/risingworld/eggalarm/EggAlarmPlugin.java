package de.mahagst.risingworld.eggalarm;

import net.risingworld.api.Plugin;
import net.risingworld.api.Server;
import net.risingworld.api.events.EventMethod;
import net.risingworld.api.events.Listener;
import net.risingworld.api.events.Threading;
import net.risingworld.api.events.player.PlayerKeyEvent;
import net.risingworld.api.events.player.PlayerSpawnEvent;
import net.risingworld.api.objects.Player;
import net.risingworld.api.utils.Key;

/**
 * Opens the egg radial menu with Ctrl+O while looking at a placed rainbow egg.
 * Normal F pickup is unchanged.
 */
public class EggAlarmPlugin extends Plugin implements Listener {
	private EggAlarmUI ui;
	private EggAlarmService service;

	@Override
	public void onEnable() {
		ui = new EggAlarmUI();
		ui.load();
		service = new EggAlarmService(this, ui);
		registerEventListener(this);
		for (Player player : Server.getAllPlayers()) {
			registerHotkeys(player);
		}
		System.out.println("[EggAlarm] enabled");
	}

	@Override
	public void onDisable() {
		unregisterEventListener(this);
		for (Player player : Server.getAllPlayers()) {
			unregisterHotkeys(player);
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
		if (!event.isPressed() || event.getKey() != Key.O) {
			return;
		}
		Player player = event.getPlayer();
		if (!player.isKeyPressed(Key.LeftCtrl) && !player.isKeyPressed(Key.RightCtrl)) {
			return;
		}
		if (!ui.isReady()) {
			return;
		}
		service.tryOpenFromLook(player);
	}

	private static void registerHotkeys(Player player) {
		player.registerKeys(Key.O, Key.LeftCtrl, Key.RightCtrl);
		player.setListenForKeyInput(true);
	}

	private static void unregisterHotkeys(Player player) {
		player.unregisterKeys(Key.O, Key.LeftCtrl, Key.RightCtrl);
	}
}
