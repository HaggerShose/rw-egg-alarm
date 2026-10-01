package de.mahagst.risingworld.eggalarm;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

import net.risingworld.api.definitions.Objects;
import net.risingworld.api.objects.Player;
import net.risingworld.api.utils.Vector3f;

/**
 * Player-facing chat and radial labels. English is the default;
 * German is used when the player's game language starts with {@code de}.
 */
public final class Messages {
	public enum Key {
		MENU_UNLINK,
		MENU_SOUND,
		MENU_RANGE,
		MENU_TEST,
		MENU_LINK,
		MENU_BACK,
		SOUND_SAVE_FAILED,
		SOUND_SET,
		RANGE_SAVE_FAILED,
		RANGE_SET,
		NO_DEVICE,
		LINK_FAILED,
		LINKED,
		UNLINK_FAILED,
		UNLINKED
	}

	private static final Map<Key, String> EN = new EnumMap<>(Key.class);
	private static final Map<Key, String> DE = new EnumMap<>(Key.class);

	static {
		put(Key.MENU_UNLINK, "Unlink", "Trennen");
		put(Key.MENU_SOUND, "Sound", "Sound");
		put(Key.MENU_RANGE, "Range", "Reichweite");
		put(Key.MENU_TEST, "TEST", "TEST");
		put(Key.MENU_LINK, "Link", "Verknüpfen");
		put(Key.MENU_BACK, "Back", "Zurück");
		put(Key.SOUND_SAVE_FAILED, "Failed to save sound.", "Sound speichern fehlgeschlagen.");
		put(Key.SOUND_SET, "Sound: {0}", "Sound: {0}");
		put(Key.RANGE_SAVE_FAILED, "Failed to save range.", "Reichweite speichern fehlgeschlagen.");
		put(Key.RANGE_SET, "Range: {0}", "Reichweite: {0}");
		put(Key.NO_DEVICE,
				"No furnace, grill, oven, or skewer within 10 m.",
				"Kein Ofen, Grill, Backofen oder Spieß in 10 m.");
		put(Key.LINK_FAILED, "Linking failed.", "Verknüpfen fehlgeschlagen.");
		put(Key.LINKED,
				"<color=#88ccff>Linked</color> to {0} at {1}.",
				"<color=#88ccff>Verknüpft</color> mit {0} bei {1}.");
		put(Key.UNLINK_FAILED, "Unlink failed.", "Trennen fehlgeschlagen.");
		put(Key.UNLINKED, "Unlinked.", "Getrennt.");
	}

	private Messages() {
	}

	/** Text for the player's language. Missing keys fall back to English. */
	public static String get(Player player, Key key) {
		Map<Key, String> table = german(player) ? DE : EN;
		String text = table.get(key);
		return text != null ? text : EN.get(key);
	}

	/** Same as {@link #get}, replacing {@code {0}}, {@code {1}}, ... in order. */
	public static String format(Player player, Key key, String... args) {
		String text = get(player, key);
		if (args == null) {
			return text;
		}
		for (int i = 0; i < args.length; i++) {
			text = text.replace("{" + i + "}", args[i] == null ? "" : args[i]);
		}
		return text;
	}

	/**
	 * Success line for link: device label plus world position.
	 * Skewer is a {@link Objects.Type#Grill} whose definition name is {@code skewer}.
	 */
	public static String linkedTo(Player player, Objects.ObjectDefinition definition, Vector3f position) {
		return format(player, Key.LINKED, deviceLabel(player, definition), coords(position));
	}

	private static String deviceLabel(Player player, Objects.ObjectDefinition definition) {
		boolean de = german(player);
		if (definition != null && "skewer".equalsIgnoreCase(definition.name)) {
			return de ? "Spieß" : "Skewer";
		}
		Objects.Type type = definition == null ? null : definition.type;
		if (type == Objects.Type.Furnace) {
			return de ? "Ofen" : "Furnace";
		}
		if (type == Objects.Type.Oven) {
			return de ? "Backofen" : "Oven";
		}
		if (type == Objects.Type.Grill) {
			return "Grill";
		}
		return de ? "Gerät" : "device";
	}

	private static String coords(Vector3f position) {
		if (position == null) {
			return "?";
		}
		return String.format(Locale.US, "%.1f, %.1f, %.1f", position.x, position.y, position.z);
	}

	private static boolean german(Player player) {
		String lang = player.getLanguage();
		if (lang == null || lang.isBlank()) {
			lang = player.getSystemLanguage();
		}
		return lang != null && lang.toLowerCase(Locale.ROOT).startsWith("de");
	}

	private static void put(Key key, String en, String de) {
		EN.put(key, en);
		DE.put(key, de);
	}
}
