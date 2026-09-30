# EggAlarm

Server-side Rising World plugin that turns a rainbow egg into a cooking alarm.

Place the egg, look at it, and press Ctrl+O. Link it to a nearby furnace, grill, oven, or skewer. When that station finishes an item, the egg plays a short 3D sound for everyone in range. Players do not need to install anything.

## How it works

1. Place a persistent rainbow egg.
2. Look at the egg (within **5 m**) and press **Ctrl+O**.
3. Choose **Link**. The nearest furnace, grill, oven, or skewer within **10 m** is linked. The player who links becomes the owner.
4. Optional (owner or admin): **Sound** picks a clip, **Range** sets how far it can be heard (32 / 64 / 128 m, default **64**), **TEST** plays it once.
5. When the linked station finishes transforming an item, the egg plays that sound once.
6. If you pick the egg up and place it again, look at the new egg and choose **Relink**. The nearest orphaned link within 10 m (device still there, old egg gone) is moved onto this egg.

Notes:

- An unlinked egg can be opened by anyone (**Link** and **Relink**). A linked egg opens only for the owner or an admin (**Unlink**, **Sound**, **Range**, **TEST**).
- F pickup stays vanilla. There is no long-press.
- Several items finishing at once do not stack sounds: after a play, that device stays quiet for **5 seconds**.
- The alarm is a one-shot, not a loop.
- Chat and menu labels are English by default, and German when the player's game language starts with `de`. Sound file names stay as stored.
- Only the player who uses the menu gets chat feedback. The alarm itself has no chat message.

## Controls

Look at the egg, then **Ctrl+O** (left or right Ctrl).

| Entry  | When     | Effect                                                                                                                                                      |
| ------ | -------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Link   | Unlinked | Link the nearest furnace / grill / oven / skewer within 10 m. One egg per device; a new link drops the previous egg on that device.                         |
| Relink | Unlinked | On click, attach the nearest owned orphan link within 10 m. Keeps device, owner, sound, and range. Chat error if none.                                      |
| Unlink | Linked   | Drop the link. World objects stay.                                                                                                                          |
| Sound  | Linked   | Submenu of filled sound slots. Current slot is marked with `*`. Pick saves it and plays a preview. **Back** returns to the main menu.                       |
| Range  | Linked   | Submenu **32 / 64 / 128**. Current value marked with `*`. Pick saves it and plays a preview. Full volume stays within **5 m**; only the hear limit changes. |
| TEST   | Linked   | Play the current sound once at the egg.                                                                                                                     |

The skewer is the low-tech grill (object name `skewer`). Chat names the device and its position when you link or relink.

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

Each world gets its own SQLite file (from `World.getName()`). Links are loaded into memory on start. Missing eggs or devices are not deleted on startup; use **Unlink** or **Relink**, or leave them until a later cleanup.

Built against Rising World Plugin API **0.9.3.2**.

## License

MIT -- see [LICENSE](LICENSE).

Source: <https://github.com/HaggerShose/rw-egg-alarm>
