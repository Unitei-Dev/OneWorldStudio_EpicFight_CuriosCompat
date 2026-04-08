# EpicFight x Curios Compat

Standalone Curios compatibility for Epic Fight on Minecraft `1.20.1`, with live per-item transform editing, backpack bridges, and Curious Lanterns light support.

## Features

- Restores standalone Curios rendering support for Epic Fight.
- Adds an in-game Curios Position Editor for per-slot, per-item transform overrides.
- Stores standing and sitting offsets in `config/epicfight_curios_compat_positions.json`.
- Bridges common back-slot mods such as Backpacked, Curios Back Slot, L2 Backpack, and Supplementaries layers.
- Syncs Curious Lanterns belt and waist lanterns with dynamic light placement.

## Supported Versions

| Component | Version |
| --- | --- |
| Minecraft | `1.20.1` |
| Forge | `47.4.x` |
| Curios | `5.14.1+1.20.1` |
| Epic Fight | `20.13.6-1.20.1` |
| Java | `17` |

## Player Installation

1. Install Minecraft Forge `47.4.x` for `1.20.1`.
2. Install Curios and Epic Fight for the same game version.
3. Add the release jar for this project to your `mods/` folder.
4. Remove or disable the Curios compatibility that ships inside your Epic Fight setup if your modpack already bundles one.

## Curios Position Editor

- Default keybind: `L`
- Permission requirement: OP on multiplayer servers, or cheats enabled in singleplayer
- Modes: separate standing and sitting offsets
- Controls: translation, rotation, and scale per selected Curios slot item
- Save location: `config/epicfight_curios_compat_positions.json`

## Compatibility Notes

- Backpacked backpacks register into the Curios back slot.
- Curios Back Slot, L2 Backpack, and Supplementaries player back layers are cleaned up so Epic Fight can render the compatible result instead.
- Curious Lanterns belt and waist items can emit synced dynamic light while equipped.

## Developer Setup

This repository is public-ready, but it is not a fully clean-clone compile yet because Epic Fight is still treated as a manual local dependency.

### Clean Clone Validation

The following command works from a fresh checkout and is what CI runs publicly:

```bash
./gradlew help
```

### Full Local Build

1. Install Java `17`.
2. Download `epicfight-forge-20.13.6-1.20.1.jar`.
3. Place it in `libs/`.
4. Run:

```bash
./gradlew clean build
```

Curios is resolved from its public Maven repository. Epic Fight remains a local jar dependency in this project, so the jar must not be committed.

## Repository Links

- [Source](https://github.com/OneWorldStudio-Unitei/OneWorldStudio_EpicFight_CuriosCompat)
- [Issues](https://github.com/OneWorldStudio-Unitei/OneWorldStudio_EpicFight_CuriosCompat/issues)
- [Releases](https://github.com/OneWorldStudio-Unitei/OneWorldStudio_EpicFight_CuriosCompat/releases)

## License

This project is licensed under the Apache License 2.0. See `LICENSE` for the full text.
