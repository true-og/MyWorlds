package com.bergerkiller.bukkit.mw.playerdata;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.ZipException;

import org.bukkit.Bukkit;

import com.bergerkiller.bukkit.common.nbt.CommonTagCompound;
import com.bergerkiller.bukkit.mw.MyWorlds;

// Flat file helpers for <folder>/<uuid>.dat profiles. Used by the file backend and the legacy importer.
public class PlayerDataFile {

    static void init() {
    }

    public static File getPlayerDataFile(File playerDataFolder, UUID playerUUID) {
        return new File(playerDataFolder, playerUUID.toString() + ".dat");
    }

    public static boolean exists(File playerDataFolder, UUID playerUUID) {
        return getPlayerDataFile(playerDataFolder, playerUUID).exists();
    }

    // Every <uuid>.dat in the folder. Skips .dat_old and names that are not a UUID.
    public static List<UUID> list(File playerDataFolder) {
        File[] files = playerDataFolder.listFiles();
        if (files == null) {
            return Collections.emptyList();
        }
        List<UUID> result = new ArrayList<>(files.length);
        for (File file : files) {
            String fileName = file.getName().toLowerCase(Locale.ENGLISH);
            if (!file.isFile() || !fileName.endsWith(".dat")) {
                continue;
            }
            try {
                result.add(UUID.fromString(fileName.substring(0, fileName.length() - 4)));
            } catch (IllegalArgumentException ex) {
                /* ignore */
            }
        }
        return result;
    }

    public static CommonTagCompound readIfExists(MyWorlds plugin, File playerDataFolder, UUID playerUUID) {
        Logger logger = (plugin != null) ? plugin.getLogger() : Bukkit.getLogger();
        return tryReadIfExists(logger, getPlayerDataFile(playerDataFolder, playerUUID), playerUUID);
    }

    private static CommonTagCompound tryReadIfExists(Logger logger, File file, Object playerName) {
        try {
            if (file.exists()) {
                return CommonTagCompound.readFromFile(file, true);
            }
        } catch (ZipException ex) {
            logger.warning("Failed to read player data for " + playerName + " (ZIP-exception: file corrupted)");
        } catch (Throwable t) {
            // Return an empty data constant for now
            logger.log(Level.WARNING, "Failed to read player data for " + playerName, t);
        }
        return null;
    }

    public static void write(CommonTagCompound data, File playerDataFolder, UUID playerUUID) throws IOException {
        playerDataFolder.mkdirs();
        data.writeToFile(getPlayerDataFile(playerDataFolder, playerUUID), true);
    }
}
