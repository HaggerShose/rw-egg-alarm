package de.mahagst.risingworld.eggalarm;

import java.util.ArrayList;
import java.util.List;

import net.risingworld.api.Plugin;
import net.risingworld.api.Timer;
import net.risingworld.api.World;
import net.risingworld.api.assets.TextureAsset;
import net.risingworld.api.definitions.Definitions;
import net.risingworld.api.definitions.Items;
import net.risingworld.api.objects.Player;
import net.risingworld.api.objects.WorldItem;

/**
 * Radial menus for a looked-at egg. Actions are handled by {@link EggAlarmService}.
 */
public class EggAlarmUI {
	/** Item variant of the rainbow egg icon. */
	private static final int RAINBOW_VARIANT = 3;
	/**
	 * Wait before the next radial menu. Opening one inside the previous
	 * selection callback gets closed by the client as it dismisses the old menu.
	 */
	private static final float MENU_SWAP_DELAY = 0.25f;

	private final Plugin plugin;
	private final EggAlarmService service;
	private TextureAsset eggIcon;
	private boolean ready;

	public EggAlarmUI(Plugin plugin, EggAlarmService service) {
		this.plugin = plugin;
		this.service = service;
	}

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
	 * Linked (owner / admin): Trennen, Sound, TEST.
	 * Unlinked: Verknüpfen, optional Neu verknüpfen when an owned orphan device is nearby.
	 */
	public void showEggMenu(Player player, WorldItem egg, boolean linked, boolean canRelink) {
		long eggId = egg.getGlobalID();
		List<EggAlarmService.MenuAction> actions = new ArrayList<>();
		List<String> labels = new ArrayList<>();
		List<TextureAsset> icons = new ArrayList<>();
		if (linked) {
			actions.add(EggAlarmService.MenuAction.UNLINK);
			labels.add("Trennen");
			icons.add(eggIcon);
			actions.add(EggAlarmService.MenuAction.SOUNDS);
			labels.add("Sound");
			icons.add(eggIcon);
			actions.add(EggAlarmService.MenuAction.TEST);
			labels.add("TEST");
			icons.add(eggIcon);
		} else {
			actions.add(EggAlarmService.MenuAction.LINK);
			labels.add("Verknüpfen");
			icons.add(eggIcon);
			if (canRelink) {
				actions.add(EggAlarmService.MenuAction.RELINK);
				labels.add("Neu verknüpfen");
				icons.add(eggIcon);
			}
		}

		EggAlarmService.MenuAction[] actionArray = actions.toArray(EggAlarmService.MenuAction[]::new);
		player.showRadialMenu(
				icons.toArray(TextureAsset[]::new),
				labels.toArray(String[]::new),
				null,
				true,
				selection -> plugin.enqueue(() -> service.handleMenu(player, eggId, selection, actionArray)));
	}

	/**
	 * Filled sound slots plus Zurück. The current slot is marked with {@code *}.
	 * Zurück reopens the linked egg menu. Both opens wait out {@link #MENU_SWAP_DELAY}
	 * so the previous radial can finish closing.
	 */
	public void showSoundMenu(Player player, WorldItem egg, int currentSoundId) {
		long eggId = egg.getGlobalID();
		List<EggAlarmSounds.SoundInfo> slots = service.filledSlots();
		int back = slots.size();
		TextureAsset[] icons = new TextureAsset[back + 1];
		String[] labels = new String[back + 1];
		int[] slotIds = new int[back];
		for (int i = 0; i < back; i++) {
			EggAlarmSounds.SoundInfo info = slots.get(i);
			slotIds[i] = info.slot();
			labels[i] = info.slot() == currentSoundId ? info.displayName() + " *" : info.displayName();
			icons[i] = eggIcon;
		}
		labels[back] = "Zurück";
		icons[back] = eggIcon;
		openAfterClose(() -> {
			if (!player.isConnected()) {
				return;
			}
			player.showRadialMenu(icons, labels, null, true, selection -> plugin.enqueue(() -> {
				if (selection == null || selection < 0 || selection > back) {
					return;
				}
				if (selection == back) {
					openAfterClose(() -> {
						WorldItem again = World.getItem(eggId);
						if (service.isTargetEgg(again)) {
							showEggMenu(player, again, true, false);
						}
					});
					return;
				}
				service.setSound(player, eggId, slotIds[selection]);
			}));
		});
	}

	private void openAfterClose(Runnable open) {
		Timer timer = new Timer(0f, MENU_SWAP_DELAY, 0, () -> plugin.enqueue(open));
		timer.start();
	}
}
