package com.bergerkiller.bukkit.mw.playerdata;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.bergerkiller.bukkit.common.nbt.CommonTagCompound;
import com.bergerkiller.bukkit.mw.MyWorlds;
import com.bergerkiller.bukkit.mw.WorldConfig;

// Legacy backend: <worldfolder>/playerdata/<uuid>.dat, gzip compressed NBT
public final class FilePlayerDataStore implements PlayerDataStore {

    static void init() {
    }

    private static File folder(String storageWorld) {
        return WorldConfig.get(storageWorld).getPlayerFolder();
    }

    @Override
    public String name() {
        return "files";
    }

    @Override
    public CommonTagCompound read(String storageWorld, UUID uuid) {
        return PlayerDataFile.readIfExists(MyWorlds.plugin, folder(storageWorld), uuid);
    }

    @Override
    public void write(String storageWorld, UUID uuid, CommonTagCompound data) throws IOException {
        PlayerDataFile.write(data, folder(storageWorld), uuid);
    }

    @Override
    public void writeAll(UUID uuid, Map<String, CommonTagCompound> perWorld) throws IOException {
        for (Map.Entry<String, CommonTagCompound> entry : perWorld.entrySet()) {
            write(entry.getKey(), uuid, entry.getValue());
        }
    }

    @Override
    public boolean has(String storageWorld, UUID uuid) {
        return PlayerDataFile.exists(folder(storageWorld), uuid);
    }

    @Override
    public List<UUID> list(String storageWorld) {
        return PlayerDataFile.list(folder(storageWorld));
    }

    // The playerdata folder is removed together with the world folder
    @Override
    public boolean drop(String storageWorld) {
        return true;
    }

    // The world folder copy already carries the playerdata folder
    @Override
    public int copy(String fromWorld, String toWorld) {
        return 0;
    }
}
