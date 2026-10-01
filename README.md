# EggAlarm

Server-side Rising World plugin that turns a rainbow egg into a cooking alarm.

Place the egg, look at it, and press Ctrl+O. Link it to a nearby furnace, grill, oven, or skewer. When that station finishes an item, the egg plays a short 3D sound for everyone in range. Players do not need to install anything.

## How it works

1. Place a persistent rainbow egg.
2. Look at the egg (within **5 m**) and press **Ctrl+O**.
3. Choose **Link**. The nearest furnace, grill, oven, or skewer within **10 m** is used. A new station stores you as owner, sound 1, range 64, and volume **80%**. A station that was linked before keeps its sound, range, and volume, and you become the owner. A new egg replaces the previous egg on that station. The linked menu opens again.
4. Optional (owner or admin): **Sound** picks a clip, **Range** sets how far it can be heard (32 / 64 / 128 / 256 m, default **64**), **Volume** steps by **5%** or jumps to **25% / 50% / 75% / Max**, **TEST** plays it once.
5. When the linked station finishes transforming an item, the egg plays that sound once.
6. If you pick the egg up and place a new one, look at the new egg and choose **Link** again. If that same station is still the nearest, its settings stay.

Notes:

- An unlinked egg can be opened by anyone (**Link**, **Close**). A linked egg opens only for the owner or an admin (**Unlink**, **Sound**, **Range**, **Volume**, **TEST**, **Close**). Linking onto someone else's station makes you the owner; their settings stay.
- F pickup stays vanilla. There is no long-press.
- Several items finishing at once do not stack sounds: after a play, that device stays quiet for **5 seconds**.
- The alarm is a one-shot, not a loop.
- Chat and menu labels are English by default, and German when the player's game language starts with `de`. Sound file names stay as stored.
- Only the player who uses the menu gets chat feedback. The alarm itself has no chat message.

## Controls

Look at the egg, then **Ctrl+O** (left or right Ctrl).

| Entry  | When     | Effect                                                                                                                                                      |
| ------ | -------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Link   | Unlinked | Nearest furnace / grill / oven / skewer within 10 m. New station: you own it (sound 1, range 64, volume 80%). Already registered: you become owner, sound/range/volume stay, this egg replaces the old one. Opens the linked menu. |
| Unlink | Linked   | Drop the station registration and its egg. World objects stay. The menu closes.                                                                                      |
| Sound  | Linked   | Submenu of filled sound slots. Current slot is marked with `*`. Pick saves it, plays a preview, and opens this menu again. **Back** returns to the main menu. |
| Range  | Linked   | Submenu **32 / 64 / 128 / 256**. Current value marked with `*`. Pick saves it and returns to the main menu (no preview). Full volume stays within **1 m**; only the hear limit changes. |
| Volume | Linked   | **Louder** / **Quieter** (5% steps, menu stays open), presets **25% / 50% / 75% / Max**. A preset saves, previews, and returns to the main menu. Chat shows the percent. The matching preset is marked with `*`. **Back** returns to the main menu. |
| TEST   | Linked   | Play the current sound once at the egg, then return to the main menu.                                                                                       |
| Close  | Always   | Close the radial.                                                                                                                                            |

The skewer is the low-tech grill (object name `skewer`). Chat names the device and its position when you link.

## Sounds

Built-in clips ship in the jar. On first start, if `Plugins/EggAlarm/sounds/` does not exist, the plugin creates it and copies those files there. An existing folder is left alone.

Drop extra files into that folder to add or replace a slot. Same slot number: the file in the folder wins.

Filename: `NN_DisplayName.ogg` (also wav / mp3 / flac). Slot is `1`..`7` (leading zeros are fine). A missing name (`01.ogg`) is shown as `Sound 1`. Other files are ignored. Prefer short `.ogg` clips.

## Install

Put the jar here and restart the server:

```text
Plugins/EggAlarm/EggAlarm.jar
```

State is stored automatically in:

```text
Plugins/EggAlarm/<WorldName>.db
```

Custom sounds (optional):

```text
Plugins/EggAlarm/sounds/
```

Each world gets its own SQLite file (from `World.getName()`). Devices and eggs are loaded into memory on start. About 10 seconds after the world is ready, stations that no longer exist are removed (their egg row goes with them). A station that is still there keeps its settings even if the egg was picked up. An old `egg_links` table is copied once into the new tables and then removed.

Built against Rising World Plugin API **0.9.3.2**.

## License

MIT -- see [LICENSE](LICENSE).

Source: <https://github.com/HaggerShose/rw-egg-alarm>
