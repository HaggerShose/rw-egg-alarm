package de.mahagst.risingworld.eggalarm;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.risingworld.api.Plugin;
import net.risingworld.api.World;
import net.risingworld.api.definitions.Objects;
import net.risingworld.api.objects.Player;
import net.risingworld.api.objects.WorldItem;
import net.risingworld.api.objects.world.Chunk;
import net.risingworld.api.objects.world.ObjectElement;
import net.risingworld.api.utils.Layer;
import net.risingworld.api.utils.RaycastResult;
import net.risingworld.api.utils.Utils;
import net.risingworld.api.utils.Vector3f;

/**
 * Look-up, egg links, radial menu gate, sound pick, and the transform alarm.
 * Hot path uses RAM maps; SQLite only on enable load and on link / unlink / sound / range change.
 * <p>
 * A device keeps owner, sound, range, and volume. Linking attaches this egg to the
 * nearest device and drops any previous egg on that device. Settings stay when the
 * device is already registered.
 */
public class EggAlarmService {
	/** Item variant of the rainbow egg (definitions.db {@code items_variants}). */
	private static final int RAINBOW_VARIANT = 3;
	/** Default sound slot stored on a new device. */
	private static final int DEFAULT_SOUND_ID = 1;
	/** Default hear radius in meters. Allowed values: 32, 64, 128. */
	private static final int DEFAULT_MAX_DISTANCE = 64;
	/** Max look distance for Ctrl+O (world units / meters). */
	private static final float LOOK_DISTANCE = 5f;
	/** Nearest furnace / grill / oven search radius. */
	private static final float LINK_RADIUS = 10f;
	/** Positions within this distance count as the same placed egg. */
	private static final float POSITION_EPSILON = 0.05f;
	/** Wall-clock silence after an alarm on a device (stacked transforms share one sound). */
	private static final long ALARM_COOLDOWN_MS = 5000L;

	private static final int LOOK_MASK = Layer.getBitmask(
			Layer.ITEM,
			Layer.OBJECT,
			Layer.CONSTRUCTION,
			Layer.TRANSPARENT_CONSTRUCTION,
			Layer.TERRAIN);

	private final Plugin plugin;
	private final EggAlarmRepository repository;
	private final EggAlarmSounds sounds;
	private EggAlarmUI ui;
	/** Set on disable so in-flight look and menu callbacks return. */
	private boolean closed;

	private final Map<DeviceKey, AlarmDevice> byDevice = new HashMap<>();
	private final Map<EggKey, AlarmEgg> byEgg = new HashMap<>();
	private final Map<Long, AlarmEgg> byGlobalId = new HashMap<>();
	/** At most one egg per device. Absent when the device has no egg. */
	private final Map<DeviceKey, AlarmEgg> deviceEggs = new HashMap<>();
	/** Last alarm wall-clock ms per device; transform path only. */
	private final Map<DeviceKey, Long> lastAlarmAt = new HashMap<>();

	public EggAlarmService(Plugin plugin, EggAlarmRepository repository, EggAlarmSounds sounds) {
		this.plugin = plugin;
		this.repository = repository;
		this.sounds = sounds;
	}

	void attach(EggAlarmUI ui) {
		this.ui = ui;
	}

	/**
	 * Drops RAM links. In-flight look and menu callbacks see {@link #closed} and return.
	 */
	void close() {
		closed = true;
		byDevice.clear();
		byEgg.clear();
		byGlobalId.clear();
		deviceEggs.clear();
		lastAlarmAt.clear();
		ui = null;
	}

	/**
	 * Load every device, then every egg, into RAM. Binds a session egg id when the
	 * egg is already loaded; missing eggs stay. No deletes on enable.
	 *
	 * @return false if a query itself failed
	 */
	public boolean loadLinks() {
		var devices = repository.findDevices();
		var eggs = repository.findEggs();
		if (devices == null || eggs == null) {
			return false;
		}
		for (AlarmDevice device : devices) {
			byDevice.put(deviceKey(device), device);
		}
		for (AlarmEgg egg : eggs) {
			rebindEgg(egg);
			rememberEgg(egg);
		}
		return true;
	}

	/**
	 * Meta-object finished an item transform. RAM lookup only; one-shot sound at the egg.
	 * Live pose when the egg is loaded, otherwise the stored position.
	 * Per-device cooldown: after a play, further transforms are silent for
	 * {@link #ALARM_COOLDOWN_MS} so stacked items do not stack sounds.
	 * Devices without an egg stay silent. Stored volume is not applied yet.
	 *
	 * @param objectId device global id (same as {@code ObjectElement.getGlobalID()})
	 * @param cx device chunk x
	 * @param cy device chunk y
	 * @param cz device chunk z
	 */
	public void onItemTransformed(long objectId, int cx, int cy, int cz) {
		if (closed) {
			return;
		}
		DeviceKey key = new DeviceKey(objectId, cx, cy, cz);
		AlarmDevice device = byDevice.get(key);
		AlarmEgg egg = deviceEggs.get(key);
		if (device == null || egg == null) {
			return;
		}
		long now = System.currentTimeMillis();
		Long last = lastAlarmAt.get(key);
		if (last != null && now - last < ALARM_COOLDOWN_MS) {
			return;
		}
		Vector3f position;
		if (egg.eggGlobalId != null) {
			WorldItem live = World.getItem(egg.eggGlobalId);
			position = live != null ? live.getPosition() : new Vector3f(egg.x, egg.y, egg.z);
		} else {
			position = new Vector3f(egg.x, egg.y, egg.z);
		}
		sounds.playAt(device.soundId, position, device.maxDistance);
		lastAlarmAt.put(key, now);
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
	 * Single ray from the crosshair. Opens the menu for an unlinked egg, or for the owner / admin.
	 */
	public void tryOpenFromLook(Player player) {
		if (closed || ui == null) {
			return;
		}
		player.raycast(LOOK_DISTANCE, LOOK_MASK, false, result -> plugin.enqueue(() -> {
			if (closed || ui == null) {
				return;
			}
			WorldItem item = resolveLookedEgg(result);
			if (item == null || ui == null) {
				return;
			}
			AlarmEgg egg = findEgg(item);
			AlarmDevice device = deviceOf(egg);
			if (device != null && !canEdit(player, device)) {
				return;
			}
			ui.showEggMenu(player, item, device != null);
		}));
	}

	/** Handles a radial selection; {@code actions} matches the menu entry order. */
	void handleMenu(Player player, long eggId, Integer selection, MenuAction[] actions) {
		if (closed) {
			return;
		}
		if (selection == null || selection < 0 || actions == null || selection >= actions.length) {
			return;
		}
		WorldItem egg = World.getItem(eggId);
		if (!isTargetEgg(egg)) {
			return;
		}
		AlarmEgg link = findEgg(egg);
		AlarmDevice device = deviceOf(link);
		if (device != null && !canEdit(player, device)) {
			return;
		}
		switch (actions[selection]) {
			case LINK -> linkEgg(player, egg);
			case UNLINK -> {
				if (link != null) {
					unlinkEgg(player, link);
				}
			}
			case SOUNDS -> {
				if (device != null && ui != null) {
					ui.showSoundMenu(player, egg, device.soundId);
				}
			}
			case RANGE -> {
				if (device != null && ui != null) {
					ui.showRangeMenu(player, egg, device.maxDistance);
				}
			}
			case TEST -> {
				if (device != null) {
					sounds.playAt(device.soundId, egg.getPosition(), device.maxDistance);
				}
			}
		}
	}

	/** Loaded sound slots, ascending. Empty slots are omitted. */
	List<EggAlarmSounds.SoundInfo> filledSlots(Player player) {
		return sounds.filledSlots(player);
	}

	/**
	 * Persist {@code slot} on the device and play it once as a preview.
	 * Same slot skips the write and still previews.
	 */
	void setSound(Player player, long eggId, int slot) {
		if (closed) {
			return;
		}
		WorldItem egg = World.getItem(eggId);
		if (!isTargetEgg(egg)) {
			return;
		}
		AlarmDevice device = deviceOf(findEgg(egg));
		if (device == null || !canEdit(player, device)) {
			return;
		}
		if (!sounds.canPickSlot(player, slot)) {
			return;
		}
		String name = sounds.displayName(slot);
		if (name == null) {
			return;
		}
		if (device.soundId != slot) {
			AlarmDevice next = new AlarmDevice(
					device.objectId,
					device.cx,
					device.cy,
					device.cz,
					device.ownerUid,
					slot,
					device.maxDistance,
					device.volume,
					device.createdAt);
			if (!repository.updateSettings(next)) {
				player.sendTextMessage(Messages.get(player, Messages.Key.SOUND_SAVE_FAILED));
				return;
			}
			byDevice.put(deviceKey(next), next);
			device = next;
		}
		sounds.playAt(slot, egg.getPosition(), device.maxDistance);
		player.sendTextMessage(Messages.format(player, Messages.Key.SOUND_SET, name));
	}

	/**
	 * Persist hear radius on the device and play the current sound once.
	 * Same radius skips the write and still previews.
	 */
	void setRange(Player player, long eggId, int meters) {
		if (closed) {
			return;
		}
		int distance = AlarmDevice.normalizeDistance(meters);
		if (distance != meters) {
			return;
		}
		WorldItem egg = World.getItem(eggId);
		if (!isTargetEgg(egg)) {
			return;
		}
		AlarmDevice device = deviceOf(findEgg(egg));
		if (device == null || !canEdit(player, device)) {
			return;
		}
		if (device.maxDistance != distance) {
			AlarmDevice next = new AlarmDevice(
					device.objectId,
					device.cx,
					device.cy,
					device.cz,
					device.ownerUid,
					device.soundId,
					distance,
					device.volume,
					device.createdAt);
			if (!repository.updateSettings(next)) {
				player.sendTextMessage(Messages.get(player, Messages.Key.RANGE_SAVE_FAILED));
				return;
			}
			byDevice.put(deviceKey(next), next);
			device = next;
		}
		sounds.playAt(device.soundId, egg.getPosition(), distance);
		player.sendTextMessage(Messages.format(player, Messages.Key.RANGE_SET, Integer.toString(distance)));
	}

	/**
	 * Attaches this egg to the nearest device within {@link #LINK_RADIUS}.
	 * A new device stores defaults. An existing device keeps owner, sound, range,
	 * and volume, including when another player owns it. Any other egg on that
	 * device is removed. This egg is detached from a different device first.
	 */
	private void linkEgg(Player player, WorldItem egg) {
		ObjectElement deviceObject = nearestDevice(egg.getPosition());
		if (deviceObject == null) {
			player.sendTextMessage(Messages.get(player, Messages.Key.NO_DEVICE));
			return;
		}
		DeviceKey key = deviceKey(deviceObject);
		AlarmDevice device = byDevice.get(key);
		if (device == null) {
			device = new AlarmDevice(
					deviceObject.getGlobalID(),
					deviceObject.getChunkPositionX(),
					deviceObject.getChunkPositionY(),
					deviceObject.getChunkPositionZ(),
					player.getUID(),
					DEFAULT_SOUND_ID,
					DEFAULT_MAX_DISTANCE,
					AlarmDevice.DEFAULT_VOLUME,
					System.currentTimeMillis());
			if (!repository.insertDevice(device)) {
				player.sendTextMessage(Messages.get(player, Messages.Key.LINK_FAILED));
				return;
			}
			byDevice.put(key, device);
		}
		Vector3f pos = egg.getPosition();
		AlarmEgg previous = findEgg(egg);
		if (previous != null && !sameStoredPose(previous, egg)) {
			if (!repository.deleteEgg(previous)) {
				player.sendTextMessage(Messages.get(player, Messages.Key.LINK_FAILED));
				return;
			}
			forgetEgg(previous);
		}
		AlarmEgg occupant = deviceEggs.get(key);
		if (occupant != null && !sameStoredPose(occupant, egg)) {
			if (!repository.deleteEgg(occupant)) {
				player.sendTextMessage(Messages.get(player, Messages.Key.LINK_FAILED));
				return;
			}
			forgetEgg(occupant);
		}
		AlarmEgg next = new AlarmEgg(
				egg.getCreationDate(),
				pos.x,
				pos.y,
				pos.z,
				egg.getVariant(),
				device.objectId,
				device.cx,
				device.cy,
				device.cz);
		next.eggGlobalId = egg.getGlobalID();
		if (!repository.upsertEgg(next)) {
			player.sendTextMessage(Messages.get(player, Messages.Key.LINK_FAILED));
			return;
		}
		rememberEgg(next);
		player.sendTextMessage(Messages.linkedTo(player, deviceObject.getDefinition(), deviceObject.getWorldPosition()));
	}

	/** Drops the device and, via cascade, its egg. World objects stay. */
	private void unlinkEgg(Player player, AlarmEgg egg) {
		AlarmDevice device = byDevice.get(deviceKey(egg));
		if (device == null) {
			if (!repository.deleteEgg(egg)) {
				player.sendTextMessage(Messages.get(player, Messages.Key.UNLINK_FAILED));
				return;
			}
			forgetEgg(egg);
			player.sendTextMessage(Messages.get(player, Messages.Key.UNLINKED));
			return;
		}
		if (!repository.deleteDevice(device)) {
			player.sendTextMessage(Messages.get(player, Messages.Key.UNLINK_FAILED));
			return;
		}
		forgetDevice(device);
		player.sendTextMessage(Messages.get(player, Messages.Key.UNLINKED));
	}

	private boolean canEdit(Player player, AlarmDevice device) {
		return player.isAdmin() || player.getUID().equals(device.ownerUid);
	}

	private AlarmEgg findEgg(WorldItem egg) {
		AlarmEgg link = byGlobalId.get(egg.getGlobalID());
		if (link == null) {
			link = byEgg.get(eggKey(egg));
		}
		if (link != null) {
			link.eggGlobalId = egg.getGlobalID();
			byGlobalId.put(egg.getGlobalID(), link);
		}
		return link;
	}

	private AlarmDevice deviceOf(AlarmEgg egg) {
		return egg == null ? null : byDevice.get(deviceKey(egg));
	}

	private ObjectElement nearestDevice(Vector3f origin) {
		ObjectElement best = null;
		float bestSq = LINK_RADIUS * LINK_RADIUS;
		for (ObjectElement object : devicesInRange(origin)) {
			float distSq = object.getWorldPosition().distanceSquared(origin);
			if (distSq <= bestSq) {
				bestSq = distSq;
				best = object;
			}
		}
		return best;
	}

	private List<ObjectElement> devicesInRange(Vector3f origin) {
		var chunk = Utils.ChunkUtils.getChunkPosition(origin);
		int size = Math.max(1, Chunk.SIZE_X);
		int span = (int) Math.ceil(LINK_RADIUS / size);
		float maxSq = LINK_RADIUS * LINK_RADIUS;
		List<ObjectElement> found = new ArrayList<>();
		for (int cx = chunk.x - span; cx <= chunk.x + span; cx++) {
			for (int cz = chunk.z - span; cz <= chunk.z + span; cz++) {
				Chunk area = World.getChunk(cx, cz);
				ObjectElement[] objects = area.getAllObjects();
				if (objects == null) {
					continue;
				}
				for (ObjectElement object : objects) {
					if (!isDevice(object)) {
						continue;
					}
					if (object.getWorldPosition().distanceSquared(origin) <= maxSq) {
						found.add(object);
					}
				}
			}
		}
		return found;
	}

	/** Furnace, grill, oven, or the low-tech skewer ({@code Grill} named {@code skewer}). */
	private static boolean isDevice(ObjectElement object) {
		if (object == null || object.getDefinition() == null) {
			return false;
		}
		Objects.Type type = object.getDefinition().type;
		return type == Objects.Type.Furnace
				|| type == Objects.Type.Grill
				|| type == Objects.Type.Oven;
	}

	/** Binds the session item id when the egg is already loaded nearby. */
	private void rebindEgg(AlarmEgg egg) {
		WorldItem item = findEggAt(egg);
		if (item != null) {
			egg.eggGlobalId = item.getGlobalID();
		}
	}

	private WorldItem findEggAt(AlarmEgg egg) {
		Vector3f pos = new Vector3f(egg.x, egg.y, egg.z);
		WorldItem[] items = World.getAllItemsInRange(pos, 1f);
		if (items == null) {
			return null;
		}
		float maxSq = POSITION_EPSILON * POSITION_EPSILON;
		for (WorldItem item : items) {
			if (!isTargetEgg(item) || item.getCreationDate() != egg.creationDate) {
				continue;
			}
			if (item.getPosition().distanceSquared(pos) > maxSq) {
				continue;
			}
			return item;
		}
		return null;
	}

	private void rememberEgg(AlarmEgg egg) {
		byEgg.put(eggKey(egg), egg);
		deviceEggs.put(deviceKey(egg), egg);
		if (egg.eggGlobalId != null) {
			byGlobalId.put(egg.eggGlobalId, egg);
		}
	}

	private void forgetEgg(AlarmEgg egg) {
		byEgg.remove(eggKey(egg));
		deviceEggs.remove(deviceKey(egg), egg);
		if (egg.eggGlobalId != null) {
			byGlobalId.remove(egg.eggGlobalId, egg);
		}
	}

	private void forgetDevice(AlarmDevice device) {
		DeviceKey key = deviceKey(device);
		byDevice.remove(key);
		lastAlarmAt.remove(key);
		AlarmEgg egg = deviceEggs.remove(key);
		if (egg != null) {
			byEgg.remove(eggKey(egg));
			if (egg.eggGlobalId != null) {
				byGlobalId.remove(egg.eggGlobalId, egg);
			}
		}
	}

	/** True when the stored egg primary key is exactly this item. */
	private static boolean sameStoredPose(AlarmEgg egg, WorldItem item) {
		Vector3f pos = item.getPosition();
		return egg.creationDate == item.getCreationDate()
				&& egg.x == pos.x
				&& egg.y == pos.y
				&& egg.z == pos.z;
	}

	private static EggKey eggKey(WorldItem egg) {
		Vector3f pos = egg.getPosition();
		return new EggKey(egg.getCreationDate(), q(pos.x), q(pos.y), q(pos.z), egg.getVariant());
	}

	private static EggKey eggKey(AlarmEgg egg) {
		return new EggKey(egg.creationDate, q(egg.x), q(egg.y), q(egg.z), egg.variant);
	}

	private static DeviceKey deviceKey(ObjectElement object) {
		return new DeviceKey(
				object.getGlobalID(),
				object.getChunkPositionX(),
				object.getChunkPositionY(),
				object.getChunkPositionZ());
	}

	private static DeviceKey deviceKey(AlarmDevice device) {
		return new DeviceKey(device.objectId, device.cx, device.cy, device.cz);
	}

	private static DeviceKey deviceKey(AlarmEgg egg) {
		return new DeviceKey(egg.deviceObjectId, egg.deviceCx, egg.deviceCy, egg.deviceCz);
	}

	private static int q(float value) {
		return Math.round(value / POSITION_EPSILON);
	}

	private WorldItem resolveLookedEgg(RaycastResult result) {
		if (result == null || result.getLayer() != Layer.ITEM) {
			return null;
		}
		WorldItem item = World.getItem(result.getObjectGlobalID());
		return isTargetEgg(item) ? item : null;
	}

	enum MenuAction {
		LINK,
		UNLINK,
		SOUNDS,
		RANGE,
		TEST
	}

	private record EggKey(long creationDate, int qx, int qy, int qz, int variant) {
	}

	private record DeviceKey(long objectId, int cx, int cy, int cz) {
	}
}
