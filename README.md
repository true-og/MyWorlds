# MyWorlds (TrueOG fork)

A fork of [MyWorlds](https://www.spigotmc.org/resources/myworlds.39594/) by bergerkiller / Berger Healer, maintained by [TrueOG Network](https://trueog.net). World management for Bukkit; this fork keeps per-world inventories in the TrueOG Purpur RocksDB player-data database instead of flat `.dat` files.

## Changes from upstream

Per-world inventories live in Purpur's RocksDB (`<level>/playerdatadb`), reached through the [Utilities-OG](https://github.com/true-og/Utilities-OG) player data API. The server keeps one vanilla profile per player and nothing else; MyWorlds nests everything of its own inside that record: the level's inventory group is the vanilla profile itself, every other group is a profile under `BukkitValues.myworlds:groups.<storage world>`, the persistent data container the server loads into the live player and writes back on every save. Each save is one record write, so the profiles it touches land together. Flat `.dat` files remain the fallback on non-fork servers. Data-safety rules:

- The stored profile holds the **survival** inventory per world/group. When [GameModeInventories-OG](https://github.com/true-og/GameModeInventories-OG) is present it owns the **creative** inventories, so no inventory lives in two stores: MyWorlds saves survival when a player leaves it for creative and restores it when they return, and never overwrites the stored survival inventory with a non-survival player's live items.
- A failed read kicks the player and refuses to save, so the stored profile is never overwritten with half-applied state; a failed save does the same, so a world change cannot overwrite the unsaved profile.
- A legacy `.dat` import that fails keeps its folder and refuses that player a fresh profile until it succeeds, so the retained file is never buried. Imports are flushed to disk before the folder is moved aside.
- `/world delete` and `/world copy` abort (and clean up any partial clone) when the stored player data cannot be dropped or fully copied, so a reused name never inherits old data.
- Offline edits and saves are serialized per player through the Utilities-OG lock (OpenInv and DiamondBank hold it too). A server running without MyWorlds keeps the nested groups through its own saves, because they ride in the persistent data container; other plugins editing the stored profile directly must write back the `BukkitValues` they found in it.
- A profile the server could not read is reported as an error, never as a new player: the player is kicked and nothing is written over it.

## Building

```
./gradlew build   # plugin jar in build/libs; Utilities-OG is a git submodule under libs/ built from source
```

## License

MIT. MyWorlds © 2013–2015 bergerkiller, © 2016–2024 Berger Healer.
