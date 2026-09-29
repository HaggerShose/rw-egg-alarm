# AGENTS.md -- rw-egg-alarm

Rising World server plugin (Unity API **0.9.3.2**): Ctrl+O on a placed rainbow egg opens a radial menu. A linked furnace, grill, or oven plays a chosen 3D sound at the egg when items transform.

Chat with the user in German. Code, identifiers, and commits in English. ASCII punctuation in files (`--`, `...`, `->`); German umlauts in prose are fine.

Javadoc: local under `RisingWorld/Data/SDK`, online at <https://javadoc.rising-world.net/latest/>

## Flow

```text
Ctrl+O on a persistent rainbow egg (crosshair ray, 3 m)
  unlinked: anyone -> Verknuepfen + Neu verknuepfen
  linked: owner uid or admin only -> Trennen + Sound + TEST
Verknuepfen: nearest Furnace / Grill / Oven within 10 m
  owner_uid = the player who links (not the placer)
  one egg <-> one device (a new link drops the previous egg on that device)
  default sound_id = 1
Neu verknuepfen: on click, nearest owned orphan within 10 m (egg missing at stored pose)
  attach that link to this egg (keep device, owner, sound); else chat error
Sound (linked only): submenu of filled slots 1..7 (display name; current marked with *)
  opens after a short delay so the closing main menu does not dismiss it
  pick -> persist sound_id + one preview at the egg
  Zurueck -> main menu again (same delay)
ItemTransformEvent on that device (cancelled / non-meta ignored)
  -> RAM device key -> one-shot 3D sound at the egg (live pose, else stored xyz)
     for every player within max distance. No SQLite.
  -> 5 s wall-clock cooldown per device after a play (stacked items do not stack sounds)
F pickup stays vanilla. No long-press.
```

## Layout

| File                 | Role                                                     |
| -------------------- | -------------------------------------------------------- |
| `EggAlarmPlugin`     | Lifecycle, world SQLite, Ctrl+O                          |
| `EggAlarmService`    | Look, links, owner gate, nearest device, transform alarm |
| `EggAlarmUI`         | Radial menus                                             |
| `EggAlarmSounds`     | Catalog + `playAt`                                       |
| `EggAlarmRepository` | SQLite only                                              |
| `EggLink`            | Link row + session item id                               |

No second plugin. No OZ requirement. One world db: `getPath() + "/" + World.getName() + ".db"` (unsafe name chars become `_`). `foreign_keys=ON`, `journal_mode=DELETE`, WAL checkpoint on disable.

## Identity and links

`WorldItem.getGlobalID()` is session-only. Persist:

- `egg_creation_date` -- `WorldItem.getCreationDate()`, unix seconds (not world time)
- egg position + variant (rainbow = 3)
- no rotation in the key

```text
egg_links:
  egg_creation_date, egg_x, egg_y, egg_z  PK
  egg_variant
  device_object_id, device_cx, device_cy, device_cz  UNIQUE
  owner_uid
  sound_id
  created_at
```

RAM: egg key, device key, and session global id. Enable loads every row into RAM and rebinds `eggGlobalId` when the egg is already loaded. No deletes on enable (missing device or egg stays until unlink, relink, or a later GC). SQLite only on enable load and on link / unlink / relink / sound change. Mid-session orphan (egg picked up): Neu verknuepfen near the device.

Owner gate: linked egg opens only when `player.getUID()` equals `owner_uid` or `player.isAdmin()`. Unlinked eggs stay open to everyone.

Device whitelist: `Objects.Type.Furnace`, `Grill`, `Oven`. Search radius 10 m from the egg.

## Sounds

Filename: `NN_DisplayName.ogg` (also wav/mp3/flac). Slot is the integer `1`..`7` (leading zeros ok). Other files are ignored. Display name is the text after the first `_`.

- Built-in: `src/main/resources/sounds/` packed in the jar, `SoundAsset.loadFromPlugin`.
- Custom: `plugins/EggAlarm/sounds/`, `loadFromFile`. Same slot: custom replaces built-in.

DB stores the slot id, never a file path. Do not stream short effects.

`playAt` plays for every connected, spawned player within max distance. Defaults until tuned: volume `1`, pitch `1`, min distance `5`, max distance `40`.

## Phases

1. Sounds catalog + TEST. Done.
2. `egg_links`, link / unlink, owner on link, rebind after restart. Done.
3. `ItemTransformEvent` -> device key -> alarm using `sound_id`. Done.
4. Sound-picker radial (filled slots only), persist `sound_id`. Done.
5. Garbage-collect broken links later. Startup no longer drops missing eggs or devices.

## Not in scope

Loot tables, OZ UI, long-press F, blocking vanilla pickup, looping alarms, ownership from the place event.
