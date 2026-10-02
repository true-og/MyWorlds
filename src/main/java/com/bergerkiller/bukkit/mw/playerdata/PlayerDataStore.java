package com.bergerkiller.bukkit.mw.playerdata;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.bergerkiller.bukkit.common.nbt.CommonTagCompound;

// One player profile tag per storage world. Storage world names are canonical WorldConfig names.
public interface PlayerDataStore {

    // Short backend name for the enable log line
    String name();

    // Profile of a player on a storage world, or null when absent or unreadable
    CommonTagCompound read(String storageWorld, UUID uuid);

    void write(String storageWorld, UUID uuid, CommonTagCompound data) throws IOException;

    // Writes several storage worlds for one player. Atomic on backends that support it.
    void writeAll(UUID uuid, Map<String, CommonTagCompound> perWorld) throws IOException;

    boolean has(String storageWorld, UUID uuid);

    // Every player with a profile on a storage world
    List<UUID> list(String storageWorld);

    // Removes a storage world entirely. False when unknown or refused.
    boolean drop(String storageWorld);

    // Copies every profile of one storage world into another. Count, 0 when nothing, -1 on failure.
    int copy(String fromWorld, String toWorld);

    // Forces buffered writes to durable storage. Default true for backends that persist on write.
    default boolean flush() {
        return true;
    }
}
