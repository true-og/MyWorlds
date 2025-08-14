# MyWorlds 🌍

[![Spigot Resource](https://img.shields.io/badge/Spigot-MyWorlds-orange)](https://www.spigotmc.org/resources/myworlds.39594/)
[![Dev Builds](https://img.shields.io/badge/Dev%20Builds-CI-blue)](https://ci.mg-dev.eu/job/MyWorlds/)
[![GitHub Release](https://img.shields.io/github/v/release/regix1/MyWorlds)](https://github.com/regix1/MyWorlds/releases)

> Advanced World Management plugin for Bukkit/Spigot servers

## ✨ Fork Features

This enhanced fork adds powerful inventory management capabilities to the original MyWorlds plugin.

### 🎯 Enhanced Inventory Management

#### **New Commands**

| Command | Description | Aliases |
|---------|-------------|---------|
| `/world clearinventory <player> <world> [type]` | Clear player inventories with granular control | `/world clearinv` |
| `/world clearenderchest <player> <world>` | Quick enderchest clearing | `/world clearender`, `/world clearec` |

**Clear Types:**
- `inventory` - Clears only the regular inventory and XP data
- `enderchest` - Clears only the enderchest
- `both` - Clears everything (default)

#### **Features**
- ✅ Works with **online and offline players**
- ✅ Support for **UUID or player name**
- ✅ **Granular control** over what gets cleared
- ✅ **Smart autocomplete** for all parameters
- ✅ **XP data management** when clearing inventories
- ✅ **Full backward compatibility** with existing scripts

### 🔐 Permissions

| Permission | Description | Default |
|------------|-------------|---------|
| `world.clearinventory` | Allows clearing player inventories | OP |
| `world.clearenderchest` | Allows clearing player enderchests | OP |

### 🔧 Technical Improvements

- **Fixed** dependency issues with MythicDungeons and AvnGUI
- **Added** local repository support for more reliable builds
- **Improved** GitHub Actions workflow for automated releases
- **Enhanced** error handling and logging

## 📦 Installation

1. Download the latest release JAR from the [Releases](https://github.com/regix1/MyWorlds/releases) page
2. Place it in your server's `plugins` folder
3. Restart your server

## 🛠️ Building from Source

```bash
git clone https://github.com/regix1/MyWorlds.git
cd MyWorlds
mvn clean package
```

The built plugin will be in the `target` directory.

## 📚 Usage Examples

### Clear Everything (Default)
```
/world clearinventory Steve world
```

### Clear Only Inventory
```
/world clearinv Steve world_nether inventory
```

### Clear Only EnderChest
```
/world clearenderchest Steve world_the_end
```
or
```
/world clearinv Steve world enderchest
```

### Using UUID
```
/world clearinv 069a79f4-44e9-4726-a5be-fca90e38aaf5 world
```

## 🔌 Dependencies

| Dependency | Required | Description |
|------------|----------|-------------|
| **BKCommonLib** | ✅ Yes | Core library (included) |
| Multiverse-Core | ❌ Optional | World management integration |
| PlaceholderAPI | ❌ Optional | Placeholder support |
| MythicDungeons | ❌ Optional | Dungeon instance support |

## 📄 License

This plugin maintains the same license as the original MyWorlds plugin.

## 🤝 Contributing

Feel free to submit issues and enhancement requests!

## 🙏 Credits

- Original MyWorlds plugin by [bergerkiller](https://github.com/bergerhealer/MyWorlds)
- Fork enhancements by [regix1](https://github.com/regix1)

---

<div align="center">
  
**[Download Latest Release](https://github.com/regix1/MyWorlds/releases/latest)** | **[Report Issue](https://github.com/regix1/MyWorlds/issues)**
