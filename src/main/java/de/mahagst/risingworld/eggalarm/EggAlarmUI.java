package de.mahagst.risingworld.eggalarm;

import java.util.ArrayList;
import java.util.List;

import net.risingworld.api.Plugin;
import net.risingworld.api.Timer;
import net.risingworld.api.World;
import net.risingworld.api.assets.TextureAsset;
import net.risingworld.api.objects.Player;
import net.risingworld.api.objects.WorldItem;

/**
 * Radial menus for a looked-at egg. Actions are handled by {@link EggAlarmService}.
 */
public class EggAlarmUI {
	private static final String ICON_LINK = "/icons/link.png";
	private static final String ICON_UNLINK = "/icons/unlink.png";
	private static final String ICON_SOUND = "/icons/sound.png";
	private static final String ICON_RANGE = "/icons/range.png";
	private static final String ICON_VOLUME = "/icons/volume.png";
	private static final String ICON_PLAY = "/icons/play.png";
	private static final String ICON_CLOSE = "/icons/close.png";
	private static final String ICON_BACK = "/icons/back.png";
	private static final String ICON_SOUND_SLOT = "/icons/sound-slot-disc.png";
	private static final String ICON_RANGE_32 = "/icons/range-32.png";
	private static final String ICON_RANGE_64 = "/icons/range-64.png";
	private static final String ICON_RANGE_128 = "/icons/range-128.png";
	private static final String ICON_RANGE_256 = "/icons/range-256.png";
	private static final String ICON_VOLUME_UP = "/icons/volume_up.png";
	private static final String ICON_VOLUME_DOWN = "/icons/volume_down.png";
	private static final String ICON_VOLUME_25 = "/icons/volume-25.png";
	private static final String ICON_VOLUME_50 = "/icons/volume-50.png";
	private static final String ICON_VOLUME_75 = "/icons/volume-75.png";
	private static final String ICON_VOLUME_MAX = "/icons/volume-max.png";
	/**
	 * Wait before the next radial menu. Opening one inside the previous
	 * selection callback gets closed by the client as it dismisses the old menu.
	 */
	private static final float MENU_SWAP_DELAY = 0.1f;

	private final Plugin plugin;
	private final EggAlarmService service;
	private final List<Timer> pending = new ArrayList<>();
	private TextureAsset linkIcon;
	private TextureAsset unlinkIcon;
	private TextureAsset soundIcon;
	private TextureAsset rangeIcon;
	private TextureAsset volumeIcon;
	private TextureAsset playIcon;
	private TextureAsset closeIcon;
	private TextureAsset backIcon;
	private TextureAsset soundSlotIcon;
	private TextureAsset range32Icon;
	private TextureAsset range64Icon;
	private TextureAsset range128Icon;
	private TextureAsset range256Icon;
	private TextureAsset volumeUpIcon;
	private TextureAsset volumeDownIcon;
	private TextureAsset volume25Icon;
	private TextureAsset volume50Icon;
	private TextureAsset volume75Icon;
	private TextureAsset volumeMaxIcon;
	private boolean ready;
	/** Set on disable so a late timer or menu callback does not touch the world. */
	private boolean closed;

	public EggAlarmUI(Plugin plugin, EggAlarmService service) {
		this.plugin = plugin;
		this.service = service;
	}

	/** Loads menu icons. A missing PNG leaves the UI disabled. */
	public void load() {
		linkIcon = loadIcon(ICON_LINK);
		unlinkIcon = loadIcon(ICON_UNLINK);
		soundIcon = loadIcon(ICON_SOUND);
		rangeIcon = loadIcon(ICON_RANGE);
		volumeIcon = loadIcon(ICON_VOLUME);
		playIcon = loadIcon(ICON_PLAY);
		closeIcon = loadIcon(ICON_CLOSE);
		backIcon = loadIcon(ICON_BACK);
		soundSlotIcon = loadIcon(ICON_SOUND_SLOT);
		range32Icon = loadIcon(ICON_RANGE_32);
		range64Icon = loadIcon(ICON_RANGE_64);
		range128Icon = loadIcon(ICON_RANGE_128);
		range256Icon = loadIcon(ICON_RANGE_256);
		volumeUpIcon = loadIcon(ICON_VOLUME_UP);
		volumeDownIcon = loadIcon(ICON_VOLUME_DOWN);
		volume25Icon = loadIcon(ICON_VOLUME_25);
		volume50Icon = loadIcon(ICON_VOLUME_50);
		volume75Icon = loadIcon(ICON_VOLUME_75);
		volumeMaxIcon = loadIcon(ICON_VOLUME_MAX);
		if (linkIcon == null || unlinkIcon == null || soundIcon == null || rangeIcon == null
				|| volumeIcon == null || playIcon == null || closeIcon == null || backIcon == null
				|| soundSlotIcon == null || range32Icon == null || range64Icon == null
				|| range128Icon == null || range256Icon == null || volumeUpIcon == null
				|| volumeDownIcon == null || volume25Icon == null || volume50Icon == null
				|| volume75Icon == null || volumeMaxIcon == null) {
			System.out.println("[EggAlarm] A menu icon is missing; UI disabled");
			ready = false;
			return;
		}
		ready = true;
	}

	/** True after a successful {@link #load()}. */
	public boolean isReady() {
		return ready && !closed;
	}

	/**
	 * Kills menu-swap timers and ignores later callbacks.
	 * Plugin-loaded textures are left for PluginAssetManager.
	 */
	void shutdown() {
		closed = true;
		ready = false;
		linkIcon = null;
		unlinkIcon = null;
		soundIcon = null;
		rangeIcon = null;
		volumeIcon = null;
		playIcon = null;
		closeIcon = null;
		backIcon = null;
		soundSlotIcon = null;
		range32Icon = null;
		range64Icon = null;
		range128Icon = null;
		range256Icon = null;
		volumeUpIcon = null;
		volumeDownIcon = null;
		volume25Icon = null;
		volume50Icon = null;
		volume75Icon = null;
		volumeMaxIcon = null;
		List<Timer> timers = new ArrayList<>(pending);
		pending.clear();
		for (Timer timer : timers) {
			if (!timer.isKilled()) {
				timer.kill();
			}
		}
	}

	/**
	 * Linked (owner / admin): Trennen, Sound, Range, Lautstaerke, TEST, Schliessen.
	 * Unlinked: Verknuepfen, Schliessen. Linking makes the player the owner.
	 */
	public void showEggMenu(Player player, WorldItem egg, boolean linked) {
		if (closed || !ready) {
			return;
		}
		long eggId = egg.getGlobalID();
		List<EggAlarmService.MenuAction> actions = new ArrayList<>();
		List<String> labels = new ArrayList<>();
		List<TextureAsset> icons = new ArrayList<>();
		if (linked) {
			actions.add(EggAlarmService.MenuAction.UNLINK);
			labels.add(Messages.get(player, Messages.Key.MENU_UNLINK));
			icons.add(unlinkIcon);
			actions.add(EggAlarmService.MenuAction.SOUNDS);
			labels.add(Messages.get(player, Messages.Key.MENU_SOUND));
			icons.add(soundIcon);
			actions.add(EggAlarmService.MenuAction.RANGE);
			labels.add(Messages.get(player, Messages.Key.MENU_RANGE));
			icons.add(rangeIcon);
			actions.add(EggAlarmService.MenuAction.VOLUME);
			labels.add(Messages.get(player, Messages.Key.MENU_VOLUME));
			icons.add(volumeIcon);
			actions.add(EggAlarmService.MenuAction.TEST);
			labels.add(Messages.get(player, Messages.Key.MENU_TEST));
			icons.add(playIcon);
		} else {
			actions.add(EggAlarmService.MenuAction.LINK);
			labels.add(Messages.get(player, Messages.Key.MENU_LINK));
			icons.add(linkIcon);
		}
		actions.add(EggAlarmService.MenuAction.CLOSE);
		labels.add(Messages.get(player, Messages.Key.MENU_CLOSE));
		icons.add(closeIcon);

		EggAlarmService.MenuAction[] actionArray = actions.toArray(EggAlarmService.MenuAction[]::new);
		player.showRadialMenu(
				icons.toArray(TextureAsset[]::new),
				labels.toArray(String[]::new),
				null,
				true,
				selection -> {
					if (closed) {
						return;
					}
					plugin.enqueue(() -> {
						if (closed) {
							return;
						}
						service.handleMenu(player, eggId, selection, actionArray);
					});
				});
	}

	/**
	 * Filled sound slots plus Zurueck. The current slot is marked with {@code *}.
	 * A pick persists and previews, then reopens this menu.
	 * {@code closeOnSelect} stays true: a radial left open after a click takes no further input.
	 */
	public void showSoundMenu(Player player, WorldItem egg, int currentSoundId) {
		if (closed || !ready) {
			return;
		}
		openAfterClose(() -> presentSoundMenu(player, egg, currentSoundId));
	}

	private void presentSoundMenu(Player player, WorldItem egg, int currentSoundId) {
		if (closed || !ready || !player.isConnected()) {
			return;
		}
		long eggId = egg.getGlobalID();
		List<EggAlarmSounds.SoundInfo> slots = service.filledSlots(player);
		int back = slots.size();
		TextureAsset[] icons = new TextureAsset[back + 1];
		String[] labels = new String[back + 1];
		int[] slotIds = new int[back];
		for (int i = 0; i < back; i++) {
			EggAlarmSounds.SoundInfo info = slots.get(i);
			slotIds[i] = info.slot();
			labels[i] = info.slot() == currentSoundId ? info.displayName() + " *" : info.displayName();
			icons[i] = soundSlotIcon;
		}
		labels[back] = Messages.get(player, Messages.Key.MENU_BACK);
		icons[back] = backIcon;
		player.showRadialMenu(icons, labels, null, true, selection -> {
			if (closed) {
				return;
			}
			plugin.enqueue(() -> {
				if (closed || selection == null || selection < 0 || selection > back) {
					return;
				}
				if (selection == back) {
					openAfterClose(() -> reopenEggMenu(player, eggId));
					return;
				}
				int slot = slotIds[selection];
				service.setSound(player, eggId, slot);
				openAfterClose(() -> {
					WorldItem again = World.getItem(eggId);
					if (service.isTargetEgg(again)) {
						presentSoundMenu(player, again, slot);
					}
				});
			});
		});
	}

	/**
	 * Hear radii 32 / 64 / 128 / 256 plus Zurueck. The current value is marked with {@code *}.
	 * A pick persists and previews, then reopens the linked egg menu.
	 */
	public void showRangeMenu(Player player, WorldItem egg, int currentDistance) {
		if (closed || !ready) {
			return;
		}
		openAfterClose(() -> presentRangeMenu(player, egg, currentDistance));
	}

	private void presentRangeMenu(Player player, WorldItem egg, int currentDistance) {
		if (closed || !ready || !player.isConnected()) {
			return;
		}
		long eggId = egg.getGlobalID();
		int[] ranges = { 32, 64, 128, 256 };
		TextureAsset[] rangeIcons = { range32Icon, range64Icon, range128Icon, range256Icon };
		int back = ranges.length;
		TextureAsset[] icons = new TextureAsset[back + 1];
		String[] labels = new String[back + 1];
		for (int i = 0; i < back; i++) {
			String label = Integer.toString(ranges[i]);
			labels[i] = ranges[i] == currentDistance ? label + " *" : label;
			icons[i] = rangeIcons[i];
		}
		labels[back] = Messages.get(player, Messages.Key.MENU_BACK);
		icons[back] = backIcon;
		player.showRadialMenu(icons, labels, null, true, selection -> {
			if (closed) {
				return;
			}
			plugin.enqueue(() -> {
				if (closed || selection == null || selection < 0 || selection > back) {
					return;
				}
				if (selection != back) {
					service.setRange(player, eggId, ranges[selection]);
				}
				openAfterClose(() -> reopenEggMenu(player, eggId));
			});
		});
	}

	/**
	 * Louder / quieter in 5% steps, presets 25 / 50 / 75 / Max, plus Zurueck.
	 * A step reopens this menu. A preset persists, previews, and reopens the egg menu.
	 * The matching preset is marked with {@code *} when the current percent is exact.
	 * Chat shows the percent. Zurueck reopens the linked egg menu.
	 *
	 * @param currentPercent stored volume as 0..100
	 */
	public void showVolumeMenu(Player player, WorldItem egg, int currentPercent) {
		if (closed || !ready) {
			return;
		}
		openAfterClose(() -> presentVolumeMenu(player, egg, currentPercent));
	}

	private void presentVolumeMenu(Player player, WorldItem egg, int currentPercent) {
		if (closed || !ready || !player.isConnected()) {
			return;
		}
		long eggId = egg.getGlobalID();
		int[] presets = { 25, 50, 75, 100 };
		Messages.Key[] presetKeys = {
				Messages.Key.MENU_VOLUME_25,
				Messages.Key.MENU_VOLUME_50,
				Messages.Key.MENU_VOLUME_75,
				Messages.Key.MENU_VOLUME_MAX
		};
		TextureAsset[] presetIcons = { volume25Icon, volume50Icon, volume75Icon, volumeMaxIcon };
		int back = 2 + presets.length;
		TextureAsset[] icons = new TextureAsset[back + 1];
		String[] labels = new String[back + 1];
		icons[0] = volumeUpIcon;
		labels[0] = Messages.get(player, Messages.Key.MENU_LOUDER);
		icons[1] = volumeDownIcon;
		labels[1] = Messages.get(player, Messages.Key.MENU_QUIETER);
		for (int i = 0; i < presets.length; i++) {
			String label = Messages.get(player, presetKeys[i]);
			labels[i + 2] = presets[i] == currentPercent ? label + " *" : label;
			icons[i + 2] = presetIcons[i];
		}
		labels[back] = Messages.get(player, Messages.Key.MENU_BACK);
		icons[back] = backIcon;
		player.showRadialMenu(icons, labels, null, true, selection -> {
			if (closed) {
				return;
			}
			plugin.enqueue(() -> {
				if (closed || selection == null || selection < 0 || selection > back) {
					return;
				}
				if (selection == back) {
					openAfterClose(() -> reopenEggMenu(player, eggId));
					return;
				}
				if (selection <= 1) {
					int delta = selection == 0 ? 5 : -5;
					int percent = service.adjustVolume(player, eggId, delta);
					if (percent < 0) {
						return;
					}
					openAfterClose(() -> {
						WorldItem again = World.getItem(eggId);
						if (service.isTargetEgg(again)) {
							presentVolumeMenu(player, again, percent);
						}
					});
					return;
				}
				if (service.setVolumePercent(player, eggId, presets[selection - 2]) < 0) {
					return;
				}
				openAfterClose(() -> reopenEggMenu(player, eggId));
			});
		});
	}

	/** Plugin PNG, or {@code null} when the file is missing. */
	private TextureAsset loadIcon(String path) {
		TextureAsset icon = TextureAsset.loadFromPlugin(plugin, path);
		if (icon == null) {
			System.out.println("[EggAlarm] Icon missing: " + path);
		}
		return icon;
	}

	/** Opens the linked egg menu after the current radial has closed. */
	void scheduleEggMenu(Player player, long eggId) {
		openAfterClose(() -> reopenEggMenu(player, eggId));
	}

	/** Linked egg menu after a submenu closes. No-op if the egg is gone. */
	private void reopenEggMenu(Player player, long eggId) {
		if (closed || !player.isConnected()) {
			return;
		}
		WorldItem again = World.getItem(eggId);
		if (service.isTargetEgg(again)) {
			showEggMenu(player, again, true);
		}
	}

	private void openAfterClose(Runnable open) {
		if (closed) {
			return;
		}
		Timer timer = new Timer(0f, MENU_SWAP_DELAY, 0, null);
		timer.setTask(() -> {
			pending.remove(timer);
			if (closed) {
				return;
			}
			plugin.enqueue(() -> {
				if (closed) {
					return;
				}
				open.run();
			});
		});
		pending.add(timer);
		timer.start();
	}
}
