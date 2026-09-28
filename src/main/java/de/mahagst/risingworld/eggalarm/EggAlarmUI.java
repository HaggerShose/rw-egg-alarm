package de.mahagst.risingworld.eggalarm;

import net.risingworld.api.assets.TextureAsset;
import net.risingworld.api.definitions.Definitions;
import net.risingworld.api.definitions.Items;
import net.risingworld.api.objects.Player;
import net.risingworld.api.objects.WorldItem;

/**
 * Player-facing UI for EggAlarm (radial menu and later status/feedback).
 * Loads icons once; menu entries stay here as the surface grows.
 */
public class EggAlarmUI {
	/** Item variant of the rainbow egg icon. */
	private static final int RAINBOW_VARIANT = 3;

	private TextureAsset eggIcon;
	private boolean ready;

	/** Loads menu icons. Missing assets leave the UI disabled. */
	public void load() {
		Items.ItemDefinition definition = Definitions.getItemDefinition("egg");
		if (definition == null) {
			System.out.println("[EggAlarm] Item definition 'egg' missing; UI disabled");
			return;
		}
		eggIcon = definition.getIcon(RAINBOW_VARIANT);
		if (eggIcon == null) {
			System.out.println("[EggAlarm] Rainbow egg icon missing; UI disabled");
			return;
		}
		ready = true;
	}

	/** True after a successful {@link #load()}. */
	public boolean isReady() {
		return ready;
	}

	/**
	 * Prototype radial menu: only TEST (no action).
	 * {@code egg} is the looked-at target for later entries (link, alarm, ...).
	 */
	public void showEggMenu(Player player, WorldItem egg) {
		long eggId = egg.getGlobalID();
		player.showRadialMenu(
				new TextureAsset[] { eggIcon },
				new String[] { "TEST" },
				null,
				true,
				selection -> onEggMenuSelect(player, eggId, selection));
	}

	/**
	 * Handles a radial selection. Index 0 = TEST (no-op); -1 = closed without pick.
	 */
	private void onEggMenuSelect(Player player, long eggId, Integer selection) {
		if (selection == null || selection < 0) {
			return;
		}
		// TEST and future entries branch here on selection / eggId.
	}
}
