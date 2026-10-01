# AGENTS.md -- rw-egg-alarm

Rising World server plugin (Unity API **0.9.3.2**): Ctrl+O on a placed rainbow egg opens a radial menu. A linked furnace, grill, oven, or skewer plays a chosen 3D sound at the egg when items transform.

Chat with the user in German. Code, identifiers, and commits in English. ASCII punctuation in files (`--`, `...`, `->`); German umlauts in prose are fine.

Javadoc: local under `RisingWorld/Data/SDK`, online at <https://javadoc.rising-world.net/latest/>

## Flow

```text
Ctrl+O on a persistent rainbow egg (crosshair ray, 5 m)
  unlinked: anyone -> Verknuepfen + Schliessen
  linked: owner uid or admin only -> Trennen + Sound + Range + Lautstaerke + TEST + Schliessen
Verknuepfen: nearest Furnace / Grill / Oven / Skewer within 10 m
  skewer is object name skewer, type Grill
  device not registered: new row, owner = linker, sound 1, range 64, volume 0.8
  device already registered: linker becomes owner; sound, range, volume stay
  one egg per device: the new egg replaces the previous egg row
  if this egg was on another device, that assignment is removed; the other device stays
  success reopens the linked main menu (same delay as other swaps)
Sound (linked only): submenu of filled slots 1..7 (display name; current marked with *)
  opens after a short delay so the closing main menu does not dismiss it
  pick -> persist sound_id + one preview, then this submenu again (same delay)
  closeOnSelect stays true: a radial left open after a click takes no further input
  Zurueck -> main menu again (same delay)
Range (linked only): submenu 32 / 64 / 128 / 256 (current marked with *)
  pick -> persist max_distance on the device (no preview), then the main menu (same delay)
  min distance stays 1
  Zurueck -> main menu again (same delay)
Volume (linked only): submenu Lauter / Leiser / 25% / 50% / 75% / Max / Zurueck
  Lauter / Leiser: step 5 percent, clamped 0..100; persist + preview; chat shows the percent
  then this submenu opens again
  preset 25 / 50 / 75 / Max (100): persist + preview, then the main menu
  current preset is marked with * when the stored percent matches exactly
  Zurueck -> main menu (same delay)
TEST: one preview, then the linked main menu again
Trennen: delete the device; ON DELETE CASCADE removes its egg. World objects stay. Menu stays closed.
Schliessen: no-op; the radial closes because closeOnSelect is true.
ItemTransformEvent on that device (cancelled / non-meta ignored)
  -> RAM device key -> one-shot 3D sound at the egg (live pose, else stored xyz)
     for every player within max distance. No SQLite. No egg -> silent.
  -> 5 s wall-clock cooldown per device after a play (stacked items do not stack sounds)
  playAt uses the stored volume (0..1, 5% steps) and stops tracked clips first
F pickup stays vanilla. No long-press.
```

Player chat and radial labels go through `Messages`. English by default; German when `getLanguage()` (else `getSystemLanguage()`) starts with `de`. Sound file display names stay as stored. Server logs stay English.

## Layout

| File                 | Role                                                     |
| -------------------- | -------------------------------------------------------- |
| `EggAlarmPlugin`     | Lifecycle, world SQLite, Ctrl+O                          |
| `EggAlarmService`    | Look, links, owner gate, nearest device, transform alarm |
| `EggAlarmUI`         | Radial menus                                             |
| `Messages`           | Player chat and radial labels (EN default, DE if `de`)   |
| `EggAlarmSounds`     | Catalog + `playAt`                                       |
| `EggAlarmRepository` | SQLite only                                              |
| `AlarmDevice`        | Device settings (owner, sound, range, volume)            |
| `AlarmEgg`           | Egg identity + device key + session item id              |

No second plugin. No OZ requirement. One world db: `getPath() + "/" + World.getName() + ".db"` (unsafe name chars become `_`). `foreign_keys=ON`, `journal_mode=DELETE`, WAL checkpoint on disable.

## Disable

Follow the workspace root [`AGENTS.md`](../AGENTS.md) (release what you touched; do not `SoundAsset.dispose()`). EggAlarm-specific:

- Ctrl+O keys + this plugin's key-listen flag; `hideRadialMenu` for any open egg menu
- kill pending menu-swap timers; closed flags on UI / service so look and menu callbacks no-op
- stop tracked `Sound` instances (`stop(true)`) before clearing the catalog (a clip still playing crashes SP unload)
- clear sound catalog map only (PluginAssetManager frees the assets)
- egg icon from the item definition is never disposed
- plugin-loaded radial icons under `/icons/` are only nulled, never disposed
- RAM link maps cleared; SQLite checkpoint + close

A failed enable clears the sound catalog (and closes the DB, if it was opened) the same way.

## Identity and links

`WorldItem.getGlobalID()` is session-only. Persist:

- `egg_creation_date` -- `WorldItem.getCreationDate()`, unix seconds (not world time)
- egg position + variant (rainbow = 3)
- no rotation in the key

```text
alarm_devices:
  device_object_id, device_cx, device_cy, device_cz  PK
  owner_uid
  sound_id
  max_distance
  volume          -- stored, default 1; playAt still uses 1 until a later control
  created_at

alarm_eggs:
  egg_creation_date, egg_x, egg_y, egg_z  PK
  egg_variant
  device_object_id, device_cx, device_cy, device_cz  UNIQUE
    FK -> alarm_devices ON DELETE CASCADE
```

Enable migrates a legacy `egg_links` table into these two (volume `1.0`, first device row wins), then drops it. `SqliteSchema.ensureColumn` adds `volume` if an `alarm_devices` table predates that column.

RAM: `byDevice`, `byEgg`, `byGlobalId`, `deviceEggs` (at most one egg per device). Enable loads devices then eggs immediately and rebinds `eggGlobalId` when the egg is already loaded. After `World.isInitialized()` plus 10 s, one sweep calls `World.getObject` per device. A miss deletes that device (`ON DELETE CASCADE` drops its egg) and forgets RAM. A hit keeps the device, including when the egg is gone. No remove-event GC. SQLite otherwise only on link / unlink / owner claim / sound change / range change / volume change. Picking the egg up does not delete the device; the next Link on a new egg attaches to the nearest device, claims ownership, and keeps sound, range, and volume.

Owner gate: linked egg opens only when `player.getUID()` equals the device `owner_uid` or `player.isAdmin()`. Linking always sets `owner_uid` to the linker. Unlinked eggs stay open to everyone.

Device whitelist: `Objects.Type.Furnace`, `Grill`, `Oven`. The skewer is a `Grill` whose definition name is `skewer` (no separate type). Search radius 10 m from the egg. Link chat names the device and its position (`Skewer` / `Spieß` when the name is `skewer`).

## Sounds

Filename: `NN_DisplayName.ext` or `NN.ext` (ogg/wav/mp3/flac). Slot is the integer `1`..`7` (leading zeros ok). Other files are ignored. Display name is the text after the first `_`. A missing or blank name (`01.ogg`, `01_.ogg`) becomes `Sound N`.

- Built-in: `src/main/resources/sounds/` packed in the jar, `SoundAsset.loadFromPlugin`.
- On first enable, if `plugins/EggAlarm/sounds/` does not exist, create it and copy the built-ins there. An existing folder is left alone.
- Custom: that folder. Byte-identical seed copies are skipped (keep jar asset). Changed/extra files override via `SoundAsset.load(bytes)` -- not `loadFromFile` (file-source play crashed after SP unload).

DB stores the slot id, never a file path. Do not stream short effects.

`playAt` plays for every connected, spawned player within the device `max_distance` (32, 64, 128, or 256; default 64). Volume is the stored device level (new devices default `0.8`, steps of 5%), pitch `1`, min distance `1` (full volume nearby; does not scale with hear range). Radial entries use PNGs under `/icons/` (sound slots share `sound-slot-disc.png`). `sound-slot-wave.png`, `sound-slot-note.png`, and `icons/oz/` are unused.

## Phases

1. Sounds catalog + TEST. Done.
2. Device + egg tables, link / unlink, owner on the device, rebind after restart. Done.
3. `ItemTransformEvent` -> device key -> alarm using `sound_id`. Done.
4. Sound-picker radial (filled slots only), persist `sound_id` on the device. Done.
5. Startup device sweep after world ready + 10 s (`getObject` miss deletes the device and its egg). No event GC. Volume submenu is Lauter / Leiser (5% steps, menu stays) plus presets 25 / 50 / 75 / Max (then main menu).

## Not in scope

Loot tables, OZ UI, long-press F, blocking vanilla pickup, looping alarms, ownership from the place event.
