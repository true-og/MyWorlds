package com.bergerkiller.bukkit.mw.playerdata;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

import org.bukkit.Bukkit;

import com.bergerkiller.bukkit.common.nbt.CommonTagCompound;
import com.bergerkiller.bukkit.mw.MyWorlds;
import com.bergerkiller.bukkit.mw.WorldConfig;

// Moves <world>/playerdata/<uuid>.dat files into the store once, mirroring the server's own migration
public final class LegacyPlayerDataImporter {

    // (storageWorld|uuid) pairs whose import failed this session; the load path must not hand them a fresh profile
    private static final Set<String> failedImports = ConcurrentHashMap.newKeySet();

    private LegacyPlayerDataImporter() {
    }

    static void init() {
    }

    private static String key(String storageWorld, UUID uuid) {
        return storageWorld.toLowerCase() + "|" + uuid;
    }

    // True when this player's file for this world failed to import, so loading must refuse rather than start fresh
    public static boolean importFailed(String storageWorld, UUID uuid) {
        return failedImports.contains(key(storageWorld, uuid));
    }

    // The server's own level was already imported by the server itself, every other world is done here
    public static void run(MyWorlds plugin, PlayerDataStore store) {
        String levelName = Bukkit.getWorlds().get(0).getName();
        for (WorldConfig config : new ArrayList<>(WorldConfig.all())) {
            if (config.worldname.equalsIgnoreCase(levelName)) {
                continue;
            }
            File folder = config.getPlayerFolder();
            if (folder.isDirectory()) {
                importWorld(plugin, store, config.worldname, folder);
            }
        }
    }

    private static void importWorld(MyWorlds plugin, PlayerDataStore store, String worldName, File folder) {
        int imported = 0;
        int skipped = 0;
        int failed = 0;
        for (UUID uuid : PlayerDataFile.list(folder)) {
            try {
                // An entry already stored came from a later live save and is newer than the file
                if (store.has(worldName, uuid)) {
                    skipped++;
                    continue;
                }
                CommonTagCompound data = CommonTagCompound.readFromFile(PlayerDataFile.getPlayerDataFile(folder, uuid), true);
                store.write(worldName, uuid, data);
                failedImports.remove(key(worldName, uuid));
                imported++;
            } catch (Throwable t) {
                failed++;
                // Remember the failure so this player is refused rather than given an empty profile that hides the file
                failedImports.add(key(worldName, uuid));
                plugin.getLogger().log(Level.WARNING, "Failed to import legacy player data " + uuid + " of world " + worldName, t);
            }
        }

        String summary = "Imported " + imported + " legacy player data file(s) for world " + worldName +
                " (" + skipped + " already stored, " + failed + " failed)";
        if (failed > 0) {
            plugin.log(Level.WARNING, summary + ", keeping " + folder + " for a retry on next start");
            return;
        }

        // Flush whatever the backend buffers before the source is renamed away; the server's own store persists on write
        if (!store.flush()) {
            plugin.log(Level.WARNING, summary + ", but could not flush player data to disk; keeping " + folder + " for a retry on next start");
            return;
        }

        File target = new File(folder.getParentFile(), "playerdatamigrated");
        if (target.exists()) {
            target = new File(folder.getParentFile(), "playerdatamigrated-" + System.currentTimeMillis());
        }
        try {
            Files.move(folder.toPath(), target.toPath());
            plugin.log(Level.INFO, summary + ", moved " + folder + " to " + target.getName());
        } catch (IOException ex) {
            plugin.getLogger().log(Level.WARNING, summary + ", but could not move " + folder + " aside", ex);
        }
    }
}
