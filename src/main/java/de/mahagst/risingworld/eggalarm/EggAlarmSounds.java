package de.mahagst.risingworld.eggalarm;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.risingworld.api.Plugin;
import net.risingworld.api.Server;
import net.risingworld.api.assets.SoundAsset;
import net.risingworld.api.objects.Player;
import net.risingworld.api.sounds.Sound;
import net.risingworld.api.utils.Vector3f;

/**
 * Sound catalog for slots 1..7. Built-ins come from the jar via {@code loadFromPlugin}
 * and are copied into the plugin {@code sounds} folder on first enable. Files there
 * that differ from the jar override the slot ({@code SoundAsset.load(bytes)});
 * identical seed copies keep the plugin asset (re-loading them via {@code loadFromFile}
 * has crashed after SP world unload when playing).
 * Hot path only looks up the map.
 */
public class EggAlarmSounds {
	private static final int MIN_SLOT = 1;
	private static final int MAX_SLOT = 7;
	private static final float VOLUME = 1f;
	private static final float PITCH = 1f;
	private static final float MIN_DISTANCE = 5f;
	private static final float MAX_DISTANCE = 40f;
	private static final Pattern FILE_NAME = Pattern.compile(
			"^(\\d{1,2})(?:_(.*))?\\.(ogg|wav|mp3|flac)$",
			Pattern.CASE_INSENSITIVE);

	private final Plugin plugin;
	private final Map<Integer, Entry> bySlot = new HashMap<>();
	/** Instances from {@link #playAt}. Stopped on unload so a still-playing clip does not outlive the asset reset. */
	private final List<Sound> playing = new ArrayList<>();

	public EggAlarmSounds(Plugin plugin) {
		this.plugin = plugin;
	}

	/**
	 * Seeds {@code sounds/} once from the jar, then loads built-ins and custom files.
	 * Custom wins on the same slot.
	 */
	public void load() {
		unload();
		ensureSoundsFolder();
		loadBuiltIns();
		loadCustom();
		if (bySlot.isEmpty()) {
			System.out.println("[EggAlarm] No sounds in slots 1-7");
			return;
		}
		for (Entry entry : bySlot.values()) {
			System.out.println("[EggAlarm] sound slot " + entry.slot + " = " + entry.displayName);
		}
	}

	/**
	 * Stops every tracked playback, then drops the catalog.
	 * Do not {@link SoundAsset#dispose()} here -- PluginAssetManager frees plugin assets.
	 * A clip still playing when that reset runs crashes singleplayer world unload.
	 */
	public void unload() {
		stopPlaying();
		bySlot.clear();
	}

	/** Immediate stop. Fade-out would still be playing when the asset manager resets. */
	private void stopPlaying() {
		for (Sound sound : playing) {
			sound.stop(true);
		}
		playing.clear();
	}

	/**
	 * Loaded slots in ascending order. Empty slots are omitted.
	 */
	List<SoundInfo> filledSlots() {
		return bySlot.values().stream()
				.sorted(Comparator.comparingInt(Entry::slot))
				.map(entry -> new SoundInfo(entry.slot, entry.displayName))
				.toList();
	}

	/** Display name of a loaded slot, or {@code null} if the slot is empty. */
	String displayName(int slot) {
		Entry entry = bySlot.get(slot);
		return entry == null ? null : entry.displayName;
	}

	/**
	 * Plays the slot as a one-shot 3D sound at {@code position} for every connected,
	 * spawned player within {@link #MAX_DISTANCE}. Missing slot: log and return.
	 * Each instance is tracked until {@link #unload()} so disable can stop it.
	 */
	public void playAt(int slot, Vector3f position) {
		Entry entry = bySlot.get(slot);
		if (entry == null) {
			System.out.println("[EggAlarm] No sound in slot " + slot);
			return;
		}
		float maxDistSq = MAX_DISTANCE * MAX_DISTANCE;
		for (Player player : Server.getAllPlayers()) {
			if (!player.isConnected() || !player.isSpawned()) {
				continue;
			}
			if (player.getPosition().distanceSquared(position) > maxDistSq) {
				continue;
			}
			Sound sound = player.playSound(entry.asset, false, VOLUME, PITCH, MIN_DISTANCE, MAX_DISTANCE, position);
			if (sound != null) {
				playing.add(sound);
			}
		}
	}

	/**
	 * Creates {@code plugins/EggAlarm/sounds} and copies built-ins into it when the folder
	 * does not exist yet. An existing folder is left alone, even if empty.
	 */
	private void ensureSoundsFolder() {
		Path dest = Path.of(plugin.getPath(), "sounds");
		if (Files.isDirectory(dest)) {
			return;
		}
		try {
			Files.createDirectories(dest);
		} catch (IOException e) {
			System.out.println("[EggAlarm] Could not create sounds folder: " + e.getMessage());
			return;
		}
		try {
			Path code = Path.of(EggAlarmSounds.class.getProtectionDomain().getCodeSource().getLocation().toURI());
			if (Files.isDirectory(code)) {
				copyDirectorySounds(code.resolve("sounds"), dest);
			} else {
				copyJarSounds(code, dest);
			}
		} catch (Exception e) {
			System.out.println("[EggAlarm] Could not seed sounds folder: " + e.getMessage());
		}
	}

	private void copyJarSounds(Path jarPath, Path dest) throws IOException {
		try (JarFile jar = new JarFile(jarPath.toFile())) {
			var entries = jar.entries();
			while (entries.hasMoreElements()) {
				JarEntry jarEntry = entries.nextElement();
				String name = jarEntry.getName();
				if (jarEntry.isDirectory() || !name.startsWith("sounds/")) {
					continue;
				}
				String fileName = name.substring("sounds/".length());
				if (fileName.isEmpty() || fileName.indexOf('/') >= 0 || parse(fileName) == null) {
					continue;
				}
				try (var in = jar.getInputStream(jarEntry)) {
					Files.copy(in, dest.resolve(fileName));
				} catch (IOException e) {
					System.out.println("[EggAlarm] Could not copy sound " + fileName + ": " + e.getMessage());
				}
			}
		}
	}

	private void copyDirectorySounds(Path src, Path dest) throws IOException {
		if (!Files.isDirectory(src)) {
			return;
		}
		try (var files = Files.list(src)) {
			files.filter(Files::isRegularFile).forEach(file -> {
				String fileName = file.getFileName().toString();
				if (parse(fileName) == null) {
					return;
				}
				try {
					Files.copy(file, dest.resolve(fileName));
				} catch (IOException e) {
					System.out.println("[EggAlarm] Could not copy sound " + fileName + ": " + e.getMessage());
				}
			});
		}
	}

	private void loadBuiltIns() {
		try {
			Path code = Path.of(EggAlarmSounds.class.getProtectionDomain().getCodeSource().getLocation().toURI());
			if (Files.isDirectory(code)) {
				loadDirectory(code.resolve("sounds"), true);
				return;
			}
			loadJar(code);
		} catch (Exception e) {
			System.out.println("[EggAlarm] Could not scan built-in sounds: " + e.getMessage());
		}
	}

	private void loadJar(Path jarPath) throws IOException {
		try (JarFile jar = new JarFile(jarPath.toFile())) {
			var entries = jar.entries();
			while (entries.hasMoreElements()) {
				JarEntry jarEntry = entries.nextElement();
				String name = jarEntry.getName();
				if (jarEntry.isDirectory() || !name.startsWith("sounds/")) {
					continue;
				}
				String fileName = name.substring("sounds/".length());
				if (fileName.isEmpty() || fileName.indexOf('/') >= 0) {
					continue;
				}
				Entry parsed = parse(fileName);
				if (parsed == null) {
					continue;
				}
				SoundAsset asset = SoundAsset.loadFromPlugin(plugin, "/" + name);
				put(parsed, asset);
			}
		}
	}

	private void loadCustom() {
		Path dir = Path.of(plugin.getPath(), "sounds");
		if (!Files.isDirectory(dir)) {
			return;
		}
		try (var files = Files.list(dir)) {
			files.filter(Files::isRegularFile).forEach(file -> {
				String fileName = file.getFileName().toString();
				Entry parsed = parse(fileName);
				if (parsed == null) {
					return;
				}
				// Seeded copies of jar sounds: keep loadFromPlugin. Reloading the same
				// bytes via loadFromFile has crashed SP after world unload when playing.
				if (matchesBuiltIn(fileName, file)) {
					return;
				}
				try {
					SoundAsset asset = SoundAsset.load(Files.readAllBytes(file));
					put(parsed, asset);
				} catch (IOException e) {
					System.out.println("[EggAlarm] Could not load custom sound " + fileName + ": " + e.getMessage());
				}
			});
		} catch (IOException e) {
			System.out.println("[EggAlarm] Could not read sounds in " + dir + ": " + e.getMessage());
		}
	}

	/**
	 * True when {@code file} is byte-identical to the jar (or classes) resource
	 * {@code sounds/<fileName>}.
	 */
	private boolean matchesBuiltIn(String fileName, Path file) {
		try {
			Path code = Path.of(EggAlarmSounds.class.getProtectionDomain().getCodeSource().getLocation().toURI());
			if (Files.isDirectory(code)) {
				Path builtIn = code.resolve("sounds").resolve(fileName);
				return Files.isRegularFile(builtIn) && Files.mismatch(builtIn, file) == -1L;
			}
			try (JarFile jar = new JarFile(code.toFile())) {
				JarEntry entry = jar.getJarEntry("sounds/" + fileName);
				if (entry == null) {
					return false;
				}
				byte[] builtIn;
				try (var in = jar.getInputStream(entry)) {
					builtIn = in.readAllBytes();
				}
				return java.util.Arrays.equals(builtIn, Files.readAllBytes(file));
			}
		} catch (Exception e) {
			return false;
		}
	}

	private void loadDirectory(Path dir, boolean fromPlugin) {
		if (!Files.isDirectory(dir)) {
			return;
		}
		try (var files = Files.list(dir)) {
			files.filter(Files::isRegularFile).forEach(file -> {
				Entry parsed = parse(file.getFileName().toString());
				if (parsed == null) {
					return;
				}
				SoundAsset asset = fromPlugin
						? SoundAsset.loadFromPlugin(plugin, "/sounds/" + file.getFileName())
						: SoundAsset.loadFromFile(file.toString());
				put(parsed, asset);
			});
		} catch (IOException e) {
			System.out.println("[EggAlarm] Could not read sounds in " + dir + ": " + e.getMessage());
		}
	}

	private void put(Entry parsed, SoundAsset asset) {
		if (asset == null) {
			System.out.println("[EggAlarm] Failed to load sound slot " + parsed.slot);
			return;
		}
		bySlot.put(parsed.slot, new Entry(parsed.slot, parsed.displayName, asset));
	}

	/**
	 * {@code NN_DisplayName.ext} or {@code NN.ext} / {@code NN_.ext} with slot 1..7.
	 * A missing or blank name becomes {@code Sound N}.
	 */
	private static Entry parse(String fileName) {
		Matcher matcher = FILE_NAME.matcher(fileName);
		if (!matcher.matches()) {
			return null;
		}
		int slot = Integer.parseInt(matcher.group(1));
		if (slot < MIN_SLOT || slot > MAX_SLOT) {
			return null;
		}
		String raw = matcher.group(2);
		String displayName = raw == null || raw.isBlank() ? "Sound " + slot : raw;
		return new Entry(slot, displayName, null);
	}

	private record Entry(int slot, String displayName, SoundAsset asset) {
	}

	/** A loaded sound slot without the asset. */
	record SoundInfo(int slot, String displayName) {
	}
}
