# MyWorlds

[Spigot Resource Page](https://www.spigotmc.org/resources/myworlds.39594/) | [Dev Builds](https://ci.mg-dev.eu/job/MyWorlds/)

World Management plugin for Bukkit.

## Fork Modifications

This fork adds the following features to the original MyWorlds plugin:

### New Commands

- `/world clearinventory <player> <world>` (alias: `/world clearinv`) - Clears a player's inventory data for a specific world
    - Players can be specified by name or UUID
    - Works for both online and offline players
    - Clears both normal inventory and ender chest items

### New Permissions

- `world.clearinventory` - Allows clearing a player's inventory in a specific world (Default: OP)

### Technical Improvements

- Fixed dependency issues with MythicDungeons and AvnGUI
- Added local repository support for more reliable builds
- Improved GitHub Actions workflow for automated releases

## Installation

Download the latest release JAR from the [Releases](https://github.com/regix1/MyWorlds/releases) page and place it in your server's plugins folder.

## Building from Source

```bash
git clone https://github.com/regix1/MyWorlds.git
cd MyWorlds
mvn clean package
```

The built plugin will be in the `target` directory.

## Usage Examples

Clear a player's inventory in a specific world:

```
/world clearinventory PlayerName worldname
```

## Dependencies

* BKCommonLib (included)
* Optional: Multiverse-Core, PlaceholderAPI, MythicDungeons

## License

This plugin maintains the same license as the original MyWorlds plugin.