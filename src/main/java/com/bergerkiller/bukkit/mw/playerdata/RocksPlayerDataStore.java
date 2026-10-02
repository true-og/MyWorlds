package com.bergerkiller.bukkit.mw.playerdata;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;

import com.bergerkiller.bukkit.common.nbt.CommonTagCompound;

import net.trueog.utilitiesog.UtilitiesOG;

// Server RocksDB backend through the Utilities-OG API, which maps the level name to the default storage
public final class RocksPlayerDataStore implements PlayerDataStore {

    static void init() {
    }

    // Utilities-OG is a soft dependency, so probe for the class before calling into it
    public static boolean isAvailable() {
        try {
            Class.forName("net.trueog.utilitiesog.UtilitiesOG");
        } catch (ClassNotFoundException ex) {
            return false;
        }
        return UtilitiesOG.isPlayerDataApiAvailable();
    }

    // True on a TrueOG Purpur server, whether or not Utilities-OG made its storage reachable
    public static boolean isServerRocksDB() {
        try {
            Class.forName("net.minecraft.world.level.storage.PlayerDataApi", false, Bukkit.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException ex) {
            return false;
        }
    }

    @Override
    public String name() {
        return "rocksdb";
    }

    @Override
    public CommonTagCompound read(String storageWorld, UUID uuid) {
        Object tag = UtilitiesOG.getPlayerData(storageWorld, uuid);
        return (tag == null) ? null : CommonTagCompound.create(tag);
    }

    // The server serializes inside the call, so the live handle is safe to hand over
    @Override
    public void write(String storageWorld, UUID uuid, CommonTagCompound data) throws IOException {
        // Lock the UUID so an offline edit through Utilities-OG never interleaves with this save
        UtilitiesOG.lockPlayerData(uuid);
        try {
            if (!UtilitiesOG.savePlayerData(storageWorld, uuid, data.getRawHandle())) {
                throw new IOException("Player data storage refused the write of " + uuid + " on " + storageWorld);
            }
        } finally {
            UtilitiesOG.unlockPlayerData(uuid);
        }
    }

    @Override
    public void writeAll(UUID uuid, Map<String, CommonTagCompound> perWorld) throws IOException {
        Map<String, Object> raw = new HashMap<>(perWorld.size() * 2);
        for (Map.Entry<String, CommonTagCompound> entry : perWorld.entrySet()) {
            raw.put(entry.getKey(), entry.getValue().getRawHandle());
        }
        // Lock the UUID so an offline edit through Utilities-OG never interleaves with this batch save
        UtilitiesOG.lockPlayerData(uuid);
        try {
            if (!UtilitiesOG.savePlayerData(uuid, raw)) {
                throw new IOException("Player data storage refused the batch write of " + uuid + " on " + perWorld.keySet());
            }
        } finally {
            UtilitiesOG.unlockPlayerData(uuid);
        }
    }

    @Override
    public boolean flush() {
        return UtilitiesOG.flushPlayerData();
    }

    @Override
    public boolean has(String storageWorld, UUID uuid) {
        return UtilitiesOG.hasPlayerData(storageWorld, uuid);
    }

    @Override
    public List<UUID> list(String storageWorld) {
        return UtilitiesOG.getSeenPlayers(storageWorld);
    }

    @Override
    public boolean drop(String storageWorld) {
        return UtilitiesOG.dropWorldPlayerData(storageWorld);
    }

    @Override
    public int copy(String fromWorld, String toWorld) {
        return UtilitiesOG.copyWorldPlayerData(fromWorld, toWorld);
    }
}
