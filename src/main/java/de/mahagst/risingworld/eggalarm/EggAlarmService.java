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
 * Hot path uses RAM maps; SQLite only on enable load and on link / unlink / relink / sound change.
 */
public class EggAlarmService {
	/** Item variant of the rainbow egg (definitions.db {@code items_variants}). */
	private static final int RAINBOW_VARIANT = 3;
	/** Default sound slot stored on a new link. */
	private static final int DEFAULT_SOUND_ID = 1;
	/** Max look distance for Ctrl+O (world units / meters). */
	private static final float LOOK_DISTANCE = 3f;
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

	private final Map<EggKey, EggLink> byEgg = new HashMap<>();
	private final Map<DeviceKey, EggLink> byDevice = new HashMap<>();
	private final Map<Long, EggLink> byGlobalId = new HashMap<>();
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
	 * Load every row into RAM. Binds a session egg id when the egg is already
	 * loaded; missing eggs and devices stay. No deletes on enable.
	 *
	 * @return false if the query itself failed
	 */
	public boolean loadLinks() {
		var rows = repository.findAll();
		if (rows == null) {
			return false;
		}
		for (EggLink link : rows) {
			rebindEgg(link);
			remember(link);
		}
		return true;
	}

	/**
	 * Meta-object finished an item transform. RAM lookup only; one-shot sound at the egg.
	 * Live pose when the egg is loaded, otherwise the stored position.
	 * Per-device cooldown: after a play, further transforms are silent for
	 * {@link #ALARM_COOLDOWN_MS} so stacked items do not stack sounds.
	 *
	 * @param objectId device global id (same as {@code ObjectElement.getGlobalID()})
	 * @param cx device chunk x
	 * @param cy device chunk y
	 * @param cz device chunk z
	 */
	public void onItemTransformed(long objectId, int cx, int cy, int cz) {
		DeviceKey key = new DeviceKey(objectId, cx, cy, cz);
		EggLink link = byDevice.get(key);
		if (link == null) {
			return;
		}
		long now = System.currentTimeMillis();
		Long last = lastAlarmAt.get(key);
		if (last != null && now - last < ALARM_COOLDOWN_MS) {
			return;
		}
		Vector3f position;
		if (link.eggGlobalId != null) {
			WorldItem egg = World.getItem(link.eggGlobalId);
			position = egg != null ? egg.getPosition() : new Vector3f(link.x, link.y, link.z);
		} else {
			position = new Vector3f(link.x, link.y, link.z);
		}
		sounds.playAt(link.soundId, position);
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
		player.raycast(LOOK_DISTANCE, LOOK_MASK, false, result -> plugin.enqueue(() -> {
			WorldItem item = resolveLookedEgg(result);
			if (item == null || ui == null) {
				return;
			}
			EggLink link = findLink(item);
			if (link != null && !canEdit(player, link)) {
				return;
			}
			ui.showEggMenu(player, item, link != null);
		}));
	}

	/** Handles a radial selection; {@code actions} matches the menu entry order. */
	void handleMenu(Player player, long eggId, Integer selection, MenuAction[] actions) {
		if (selection == null || selection < 0 || actions == null || selection >= actions.length) {
			return;
		}
		WorldItem egg = World.getItem(eggId);
		if (!isTargetEgg(egg)) {
			return;
		}
		EggLink link = findLink(egg);
		if (link != null && !canEdit(player, link)) {
			return;
		}
		switch (actions[selection]) {
			case LINK -> linkEgg(player, egg);
			case UNLINK -> {
				if (link != null) {
					unlinkEgg(player, link);
				}
			}
			case RELINK -> relinkEgg(player, egg);
			case SOUNDS -> {
				if (link != null && ui != null) {
					ui.showSoundMenu(player, egg, link.soundId);
				}
			}
			case TEST -> {
				if (link != null) {
					sounds.playAt(link.soundId, egg.getPosition());
				}
			}
		}
	}

	/** Loaded sound slots, ascending. Empty slots are omitted. */
	List<EggAlarmSounds.SoundInfo> filledSlots() {
		return sounds.filledSlots();
	}

	/**
	 * Persist {@code slot} on the linked egg and play it once as a preview.
	 * Same slot skips the write and still previews.
	 */
	void setSound(Player player, long eggId, int slot) {
		WorldItem egg = World.getItem(eggId);
		if (!isTargetEgg(egg)) {
			return;
		}
		EggLink link = findLink(egg);
		if (link == null || !canEdit(player, link)) {
			return;
		}
		String name = sounds.displayName(slot);
		if (name == null) {
			return;
		}
		if (link.soundId != slot) {
			EggLink next = new EggLink(
					link.creationDate,
					link.x,
					link.y,
					link.z,
					link.variant,
					link.deviceObjectId,
					link.deviceCx,
					link.deviceCy,
					link.deviceCz,
					link.ownerUid,
					slot,
					link.createdAt);
			next.eggGlobalId = link.eggGlobalId != null ? link.eggGlobalId : egg.getGlobalID();
			if (!repository.upsert(next)) {
				player.sendTextMessage("Sound speichern fehlgeschlagen.");
				return;
			}
			forget(link);
			remember(next);
		}
		sounds.playAt(slot, egg.getPosition());
		player.sendTextMessage("Sound: " + name);
	}

	private void linkEgg(Player player, WorldItem egg) {
		ObjectElement device = nearestDevice(egg.getPosition());
		if (device == null) {
			player.sendTextMessage("Kein Ofen, Grill oder Backofen in 10 m.");
			return;
		}
		EggLink existing = findLink(egg);
		DeviceKey deviceKey = deviceKey(device);
		EggLink other = byDevice.get(deviceKey);
		if (other != null && !sameEgg(other, egg)) {
			if (!repository.delete(other)) {
				player.sendTextMessage("Verknüpfen fehlgeschlagen.");
				return;
			}
			forget(other);
		}
		Vector3f pos = egg.getPosition();
		long now = System.currentTimeMillis();
		EggLink next = new EggLink(
				egg.getCreationDate(),
				pos.x,
				pos.y,
				pos.z,
				egg.getVariant(),
				device.getGlobalID(),
				device.getChunkPositionX(),
				device.getChunkPositionY(),
				device.getChunkPositionZ(),
				existing != null ? existing.ownerUid : player.getUID(),
				existing != null ? existing.soundId : DEFAULT_SOUND_ID,
				existing != null ? existing.createdAt : now);
		next.eggGlobalId = egg.getGlobalID();
		if (!repository.upsert(next)) {
			player.sendTextMessage("Verknüpfen fehlgeschlagen.");
			return;
		}
		if (existing != null) {
			forget(existing);
		}
		remember(next);
		player.sendTextMessage("Verknüpft.");
	}

	/**
	 * Moves an orphaned device link (egg missing, device within 10 m) onto this egg.
	 * Keeps owner, sound and device; updates egg identity.
	 */
	private void relinkEgg(Player player, WorldItem egg) {
		if (findLink(egg) != null) {
			player.sendTextMessage("Ei ist bereits verknüpft.");
			return;
		}
		EggLink orphan = nearestOrphan(player, egg.getPosition());
		if (orphan == null) {
			player.sendTextMessage("Kein verwaister Link in 10 m.");
			return;
		}
		Vector3f pos = egg.getPosition();
		EggLink next = new EggLink(
				egg.getCreationDate(),
				pos.x,
				pos.y,
				pos.z,
				egg.getVariant(),
				orphan.deviceObjectId,
				orphan.deviceCx,
				orphan.deviceCy,
				orphan.deviceCz,
				orphan.ownerUid,
				orphan.soundId,
				orphan.createdAt);
		next.eggGlobalId = egg.getGlobalID();
		if (!repository.delete(orphan)) {
			player.sendTextMessage("Neu verknüpfen fehlgeschlagen.");
			return;
		}
		forget(orphan);
		if (!repository.upsert(next)) {
			player.sendTextMessage("Neu verknüpfen fehlgeschlagen.");
			return;
		}
		remember(next);
		player.sendTextMessage("Neu verknüpft.");
	}

	private void unlinkEgg(Player player, EggLink link) {
		if (!repository.delete(link)) {
			player.sendTextMessage("Trennen fehlgeschlagen.");
			return;
		}
		forget(link);
		player.sendTextMessage("Getrennt.");
	}

	private boolean canEdit(Player player, EggLink link) {
		return player.isAdmin() || player.getUID().equals(link.ownerUid);
	}

	private EggLink findLink(WorldItem egg) {
		EggLink link = byGlobalId.get(egg.getGlobalID());
		if (link == null) {
			link = byEgg.get(eggKey(egg));
		}
		if (link != null) {
			link.eggGlobalId = egg.getGlobalID();
			byGlobalId.put(egg.getGlobalID(), link);
		}
		return link;
	}

	/**
	 * Nearest owned orphan within {@link #LINK_RADIUS}: linked device nearby, egg missing at the stored pose.
	 */
	private EggLink nearestOrphan(Player player, Vector3f origin) {
		EggLink best = null;
		float bestSq = LINK_RADIUS * LINK_RADIUS;
		for (ObjectElement device : devicesInRange(origin)) {
			EggLink link = byDevice.get(deviceKey(device));
			if (link == null || !canEdit(player, link) || !isOrphan(link)) {
				continue;
			}
			float distSq = device.getWorldPosition().distanceSquared(origin);
			if (distSq <= bestSq) {
				bestSq = distSq;
				best = link;
			}
		}
		return best;
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

	private static boolean isDevice(ObjectElement object) {
		if (object == null || object.getDefinition() == null) {
			return false;
		}
		Objects.Type type = object.getDefinition().type;
		return type == Objects.Type.Furnace
				|| type == Objects.Type.Grill
				|| type == Objects.Type.Oven;
	}

	/** True when no matching egg remains at the stored pose. Used only on relink click. */
	private boolean isOrphan(EggLink link) {
		return findEggAt(link) == null;
	}

	/** Binds the session item id when the egg is already loaded nearby. */
	private void rebindEgg(EggLink link) {
		WorldItem item = findEggAt(link);
		if (item != null) {
			link.eggGlobalId = item.getGlobalID();
		}
	}

	private WorldItem findEggAt(EggLink link) {
		Vector3f pos = new Vector3f(link.x, link.y, link.z);
		WorldItem[] items = World.getAllItemsInRange(pos, 1f);
		if (items == null) {
			return null;
		}
		float maxSq = POSITION_EPSILON * POSITION_EPSILON;
		for (WorldItem item : items) {
			if (!isTargetEgg(item) || item.getCreationDate() != link.creationDate) {
				continue;
			}
			if (item.getPosition().distanceSquared(pos) > maxSq) {
				continue;
			}
			return item;
		}
		return null;
	}

	private void remember(EggLink link) {
		byEgg.put(eggKey(link), link);
		byDevice.put(deviceKey(link), link);
		if (link.eggGlobalId != null) {
			byGlobalId.put(link.eggGlobalId, link);
		}
	}

	private void forget(EggLink link) {
		DeviceKey key = deviceKey(link);
		byEgg.remove(eggKey(link));
		byDevice.remove(key);
		lastAlarmAt.remove(key);
		if (link.eggGlobalId != null) {
			byGlobalId.remove(link.eggGlobalId, link);
		}
	}

	private static boolean sameEgg(EggLink link, WorldItem egg) {
		return link.creationDate == egg.getCreationDate() && eggKey(link).equals(eggKey(egg));
	}

	private static EggKey eggKey(WorldItem egg) {
		Vector3f pos = egg.getPosition();
		return new EggKey(egg.getCreationDate(), q(pos.x), q(pos.y), q(pos.z), egg.getVariant());
	}

	private static EggKey eggKey(EggLink link) {
		return new EggKey(link.creationDate, q(link.x), q(link.y), q(link.z), link.variant);
	}

	private static DeviceKey deviceKey(ObjectElement object) {
		return new DeviceKey(
				object.getGlobalID(),
				object.getChunkPositionX(),
				object.getChunkPositionY(),
				object.getChunkPositionZ());
	}

	private static DeviceKey deviceKey(EggLink link) {
		return new DeviceKey(link.deviceObjectId, link.deviceCx, link.deviceCy, link.deviceCz);
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
		RELINK,
		SOUNDS,
		TEST
	}

	private record EggKey(long creationDate, int qx, int qy, int qz, int variant) {
	}

	private record DeviceKey(long objectId, int cx, int cy, int cz) {
	}
}
