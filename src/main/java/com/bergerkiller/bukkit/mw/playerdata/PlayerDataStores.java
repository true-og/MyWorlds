package com.bergerkiller.bukkit.mw.playerdata;

import java.util.Collection;
import java.util.logging.Level;

import org.bukkit.entity.Player;

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

    // Puts the stored nested profiles of these players into their live persistent data containers, so the server's
    // own saves keep them once MyWorlds no longer handles saving (a reload or a plugin disable). The containers are
    // what a server without MyWorlds round-trips; while MyWorlds is attached the groups are stripped again on load.
    public static void restoreContainers(Collection<? extends Player> players) {
        if (!(current instanceof RocksPlayerDataStore)) {
            return;
        }
        RocksPlayerDataStore store = (RocksPlayerDataStore) current;
        for (Player player : players) {
            try {
                Object container = store.storedContainer(player.getUniqueId());
                if (container == null) {
                    continue;
                }
                Object persistentData = player.getPersistentDataContainer();
                persistentData.getClass().getMethod("putAll", container.getClass()).invoke(persistentData, container);
            } catch (Throwable t) {
                MyWorlds.plugin.getLogger().log(Level.SEVERE, "Could not hand the nested profiles of " + player.getName() + " to the server; its next save of this player drops them", t);
            }
        }
    }
}
