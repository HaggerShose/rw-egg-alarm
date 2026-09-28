package de.mahagst.risingworld.eggalarm;

import net.risingworld.api.Plugin;
import net.risingworld.api.World;
import net.risingworld.api.objects.Player;
import net.risingworld.api.objects.WorldItem;
import net.risingworld.api.utils.Layer;
import net.risingworld.api.utils.RaycastResult;

/**
 * Egg look-up and open flow: crosshair raycast (single ray) -> target egg -> UI menu.
 */
public class EggAlarmService {
	/** Item variant of the rainbow egg (definitions.db {@code items_variants}). */
	private static final int RAINBOW_VARIANT = 3;

	/** Max look distance for Ctrl+O (world units / meters). */
	private static final float LOOK_DISTANCE = 3f;

	/**
	 * Blocking world layers plus items so walls hide eggs behind them.
	 * First hit must be {@link Layer#ITEM} to count as an egg.
	 */
	private static final int LOOK_MASK = Layer.getBitmask(
			Layer.ITEM,
			Layer.OBJECT,
			Layer.CONSTRUCTION,
			Layer.TRANSPARENT_CONSTRUCTION,
			Layer.TERRAIN);

	private final Plugin plugin;
	private final EggAlarmUI ui;

	public EggAlarmService(Plugin plugin, EggAlarmUI ui) {
		this.plugin = plugin;
		this.ui = ui;
	}

	/**
	 * Placed rainbow egg only: persistent, not a meta-object item, name {@code egg}, variant rainbow.
	 */
	public boolean isTargetEgg(WorldItem item) {
		return item != null
				&& item.isValid()
				&& item.isPersistent()
				&& !item.isMetaObjectItem()
				&& "egg".equals(item.getName())
				&& item.getVariant() == RAINBOW_VARIANT;
	}

	/**
	 * Single ray from the player view (crosshair center) up to {@link #LOOK_DISTANCE}.
	 * Opens the menu only if the first hit is a target egg.
	 */
	public void tryOpenFromLook(Player player) {
		player.raycast(LOOK_DISTANCE, LOOK_MASK, false, result -> plugin.enqueue(() -> {
			WorldItem item = resolveLookedEgg(result);
			if (item != null) {
				ui.showEggMenu(player, item);
			}
		}));
	}

	private WorldItem resolveLookedEgg(RaycastResult result) {
		if (result == null || result.getLayer() != Layer.ITEM) {
			return null;
		}
		WorldItem item = World.getItem(result.getObjectGlobalID());
		return isTargetEgg(item) ? item : null;
	}
}
