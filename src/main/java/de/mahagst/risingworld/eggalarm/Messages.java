package de.mahagst.risingworld.eggalarm;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

import net.risingworld.api.objects.Player;

/**
 * Player-facing chat and radial labels. English is the default;
 * German is used when the player's game language starts with {@code de}.
 */
public final class Messages {
	public enum Key {
		MENU_UNLINK,
		MENU_SOUND,
		MENU_TEST,
		MENU_LINK,
		MENU_RELINK,
		MENU_BACK,
		SOUND_SAVE_FAILED,
		SOUND_SET,
		NO_DEVICE,
		LINK_FAILED,
		LINKED,
		ALREADY_LINKED,
		NO_ORPHAN,
		RELINK_FAILED,
		RELINKED,
		UNLINK_FAILED,
		UNLINKED
	}

	private static final Map<Key, String> EN = new EnumMap<>(Key.class);
	private static final Map<Key, String> DE = new EnumMap<>(Key.class);

	static {
		put(Key.MENU_UNLINK, "Unlink", "Trennen");
		put(Key.MENU_SOUND, "Sound", "Sound");
		put(Key.MENU_TEST, "TEST", "TEST");
		put(Key.MENU_LINK, "Link", "Verknüpfen");
		put(Key.MENU_RELINK, "Relink", "Neu verknüpfen");
		put(Key.MENU_BACK, "Back", "Zurück");
		put(Key.SOUND_SAVE_FAILED, "Failed to save sound.", "Sound speichern fehlgeschlagen.");
		put(Key.SOUND_SET, "Sound: {0}", "Sound: {0}");
		put(Key.NO_DEVICE, "No furnace, grill, or oven within 10 m.", "Kein Ofen, Grill oder Backofen in 10 m.");
		put(Key.LINK_FAILED, "Linking failed.", "Verknüpfen fehlgeschlagen.");
		put(Key.LINKED, "Linked.", "Verknüpft.");
		put(Key.ALREADY_LINKED, "Egg is already linked.", "Ei ist bereits verknüpft.");
		put(Key.NO_ORPHAN, "No orphaned link within 10 m.", "Kein verwaister Link in 10 m.");
		put(Key.RELINK_FAILED, "Relink failed.", "Neu verknüpfen fehlgeschlagen.");
		put(Key.RELINKED, "Relinked.", "Neu verknüpft.");
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

	/** Same as {@link #get}, with {@code {0}} replaced by {@code arg}. */
	public static String format(Player player, Key key, String arg) {
		return get(player, key).replace("{0}", arg == null ? "" : arg);
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
