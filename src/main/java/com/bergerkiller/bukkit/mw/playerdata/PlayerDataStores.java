package com.bergerkiller.bukkit.mw.playerdata;

import java.util.logging.Level;

import com.bergerkiller.bukkit.mw.MyWorlds;

// Holds the selected backend. Never reset on disable: the save hook still runs during shutdown.
public final class PlayerDataStores {
    private static volatile PlayerDataStore current = new FilePlayerDataStore();

    private PlayerDataStores() {
    }

    static void init() {
        FilePlayerDataStore.init();
        if (isRocksDB()) {
            RocksPlayerDataStore.init();
        }
    }

    public static PlayerDataStore current() {
        return current;
    }

    // Uses the server's RocksDB player data through Utilities-OG when present, otherwise keeps flat files.
    // On a RocksDB server without that API it refuses: files written here would be imported over live profiles.
    public static void select(MyWorlds plugin) {
        if (RocksPlayerDataStore.isAvailable()) {
            current = new RocksPlayerDataStore();
        } else if (RocksPlayerDataStore.isServerRocksDB()) {
            throw new IllegalStateException("This server keeps player data in RocksDB but the Utilities-OG player data API is unavailable, refusing to fall back to files");
        } else {
            current = new FilePlayerDataStore();
        }
        plugin.log(Level.INFO, "Player data backend: " + current.name());
    }

    public static boolean isRocksDB() {
        return current instanceof RocksPlayerDataStore;
    }
}
