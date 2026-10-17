package com.bergerkiller.bukkit.mw.playerdata;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

import org.bukkit.Bukkit;

import com.bergerkiller.bukkit.common.nbt.CommonTagCompound;
import com.bergerkiller.bukkit.mw.MyWorlds;

import net.trueog.utilitiesog.UtilitiesOG;

// Server RocksDB backend. Every profile of a player lives inside the one vanilla record the server keeps for them:
// the level's inventory group is that record itself, every other group is nested under BukkitValues.myworlds:groups.
// BukkitValues is the persistent data container the server loads into the live player and writes back on every save,
// so the groups survive a vanilla save on a server without MyWorlds. The record is reached through the Utilities-OG
// API only, so the fork stores vanilla profiles and nothing else.
public final class RocksPlayerDataStore implements PlayerDataStore {
    private static final String DATA_TAG_BUKKIT_VALUES = "BukkitValues";
    private static final String DATA_TAG_GROUPS = "myworlds:groups";
    private static final String DATA_TAG_DATA_VERSION = "DataVersion";
    private static final String DATA_TAG_POS = "Pos";

    private final String levelName = Bukkit.getWorlds().get(0).getName();

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

    // True on a TrueOG Purpur server, whether or not Utilities-OG made its storage reachable.
    // The storage class is WorldNBTStorage on the shipped jar and PlayerDataStorage on a Mojang-mapped one.
    public static boolean isServerRocksDB() {
        for (String name : new String[] { "net.minecraft.world.level.storage.WorldNBTStorage", "net.minecraft.world.level.storage.PlayerDataStorage" }) {
            try {
                Class.forName(name, false, Bukkit.class.getClassLoader()).getMethod("hasPlayerData", UUID.class);
                return true;
            } catch (ClassNotFoundException | NoSuchMethodException ex) {
                // try the next name
            }
        }
        return false;
    }

    @Override
    public String name() {
        return "rocksdb";
    }

    // The level's storage world is the vanilla profile itself
    private boolean isLevel(String storageWorld) {
        return storageWorld.equalsIgnoreCase(levelName);
    }

    private static CommonTagCompound wrap(Object rawTag) {
        return (rawTag == null) ? null : CommonTagCompound.create(rawTag);
    }

    // The player's stored record, null when the server has none. The server throws IllegalStateException for a
    // record it could not read, which callers let through so a broken profile is never replaced by a fresh one.
    private static CommonTagCompound record(UUID uuid) {
        return wrap(UtilitiesOG.getPlayerData(uuid));
    }

    private static CommonTagCompound groups(CommonTagCompound record, boolean create) {
        if (create) {
            return record.createCompound(DATA_TAG_BUKKIT_VALUES).createCompound(DATA_TAG_GROUPS);
        }
        CommonTagCompound values = record.get(DATA_TAG_BUKKIT_VALUES, CommonTagCompound.class);
        return (values == null) ? null : values.get(DATA_TAG_GROUPS, CommonTagCompound.class);
    }

    // Removes the nested groups from a tag; an empty container goes too, as the server never writes one
    private static void stripGroups(CommonTagCompound tag) {
        CommonTagCompound values = tag.get(DATA_TAG_BUKKIT_VALUES, CommonTagCompound.class);
        if (values != null) {
            values.remove(DATA_TAG_GROUPS);
            if (values.isEmpty()) {
                tag.remove(DATA_TAG_BUKKIT_VALUES);
            }
        }
    }

    // Storage world names are canonical, but match the stored key like the file backend matched folder names
    private static String groupKey(CommonTagCompound groups, String storageWorld) {
        if (groups == null) {
            return null;
        }
        if (groups.containsKey(storageWorld)) {
            return storageWorld;
        }
        for (String key : groups.keySet()) {
            if (key.equalsIgnoreCase(storageWorld)) {
                return key;
            }
        }
        return null;
    }

    private static CommonTagCompound groupProfile(CommonTagCompound record, String storageWorld) {
        CommonTagCompound groups = groups(record, false);
        String key = groupKey(groups, storageWorld);
        return (key == null) ? null : groups.get(key, CommonTagCompound.class);
    }

    // Strips the nested groups off a record, leaving the vanilla profile. A record that never held one (a group was
    // written, or an offline edit landed, before the player ever joined) has no position: every profile the server
    // or MyWorlds writes carries a Pos list, so that is what makes a record a profile.
    private static CommonTagCompound mainProfile(CommonTagCompound record) {
        if (record == null) {
            return null;
        }
        stripGroups(record);
        return record.containsKey(DATA_TAG_POS) ? record : null;
    }

    // A profile without a data version was built in the current format, one with an older version keeps it so the
    // read path still runs the server's data fixes on it (a legacy .dat import, for example)
    private static void stampDataVersion(CommonTagCompound profile) {
        if (!profile.containsKey(DATA_TAG_DATA_VERSION)) {
            profile.putValue(DATA_TAG_DATA_VERSION, Bukkit.getUnsafe().getDataVersion());
        }
    }

    // A copy to nest, which never carries groups of its own (a player loaded by the vanilla path has them in its container)
    private static CommonTagCompound nested(CommonTagCompound profile) {
        CommonTagCompound copy = profile.clone();
        stripGroups(copy);
        stampDataVersion(copy);
        return copy;
    }

    // Null when absent. Throws IllegalStateException when the stored record could not be read.
    @Override
    public CommonTagCompound read(String storageWorld, UUID uuid) {
        CommonTagCompound record = record(uuid);
        if (isLevel(storageWorld)) {
            return mainProfile(record);
        }
        if (record == null) {
            return null;
        }
        CommonTagCompound profile = groupProfile(record, storageWorld);
        if (profile == null) {
            return null;
        }
        // The server only runs its data fixes on the vanilla profile, so nested ones get the same pass here
        return wrap(UtilitiesOG.upgradePlayerData(profile.getRawHandle()));
    }

    @Override
    public void write(String storageWorld, UUID uuid, CommonTagCompound data) throws IOException {
        writeAll(uuid, Collections.singletonMap(storageWorld, data));
    }

    // One record write: a new vanilla profile keeps the groups already stored, group profiles are nested into
    // the stored record. The player lock keeps an offline edit through Utilities-OG from interleaving.
    @Override
    public void writeAll(UUID uuid, Map<String, CommonTagCompound> perWorld) throws IOException {
        UtilitiesOG.lockPlayerData(uuid);
        try {
            CommonTagCompound stored;
            try {
                stored = record(uuid);
            } catch (IllegalStateException ex) {
                throw new IOException("Refusing to overwrite the unreadable stored player data of " + uuid, ex);
            }

            CommonTagCompound record = null;
            for (Map.Entry<String, CommonTagCompound> entry : perWorld.entrySet()) {
                if (isLevel(entry.getKey())) {
                    record = entry.getValue().clone();
                    stripGroups(record);
                    stampDataVersion(record);
                    CommonTagCompound storedGroups = (stored == null) ? null : groups(stored, false);
                    if (storedGroups != null && !storedGroups.isEmpty()) {
                        record.createCompound(DATA_TAG_BUKKIT_VALUES).put(DATA_TAG_GROUPS, storedGroups);
                    }
                    break;
                }
            }
            if (record == null) {
                record = (stored != null) ? stored : new CommonTagCompound();
            }

            for (Map.Entry<String, CommonTagCompound> entry : perWorld.entrySet()) {
                if (isLevel(entry.getKey())) {
                    continue;
                }
                CommonTagCompound groups = groups(record, true);
                String key = groupKey(groups, entry.getKey());
                groups.put((key == null) ? entry.getKey() : key, nested(entry.getValue()));
            }

            try {
                UtilitiesOG.savePlayerData(uuid, record.getRawHandle());
            } catch (IllegalStateException ex) {
                throw new IOException("Player data storage failed the write of " + uuid + " on " + perWorld.keySet(), ex);
            }
        } finally {
            UtilitiesOG.unlockPlayerData(uuid);
        }
    }

    // The stored container of a player with the nested groups in it, raw, or null without any. Used when MyWorlds
    // detaches at runtime, so the live player carries the groups through the server's own saves like a server
    // without MyWorlds does.
    public Object storedContainer(UUID uuid) {
        CommonTagCompound record = record(uuid);
        CommonTagCompound groups = (record == null) ? null : groups(record, false);
        if (groups == null || groups.isEmpty()) {
            return null;
        }
        CommonTagCompound container = new CommonTagCompound();
        container.put(DATA_TAG_GROUPS, groups);
        return container.getRawHandle();
    }

    @Override
    public boolean has(String storageWorld, UUID uuid) {
        if (!UtilitiesOG.hasPlayerData(uuid)) {
            return false;
        }
        CommonTagCompound record = record(uuid);
        return isLevel(storageWorld) ? mainProfile(record) != null : groupProfile(record, storageWorld) != null;
    }

    // Every player with a vanilla profile for the level, else every record holding that group
    @Override
    public List<UUID> list(String storageWorld) {
        List<UUID> seen = UtilitiesOG.getSeenPlayers();
        if (isLevel(storageWorld)) {
            return seen;
        }
        List<UUID> players = new ArrayList<>();
        for (UUID uuid : seen) {
            try {
                if (groupProfile(record(uuid), storageWorld) != null) {
                    players.add(uuid);
                }
            } catch (IllegalStateException ex) {
                // An unreadable record can not be listed; the server already logged it
            }
        }
        return players;
    }

    // Removes the group from every record. The vanilla profiles are never dropped. True when removed or absent.
    @Override
    public boolean drop(String storageWorld) {
        if (isLevel(storageWorld)) {
            return false;
        }
        boolean ok = true;
        for (UUID uuid : UtilitiesOG.getSeenPlayers()) {
            UtilitiesOG.lockPlayerData(uuid);
            try {
                CommonTagCompound record = record(uuid);
                CommonTagCompound groups = (record == null) ? null : groups(record, false);
                String key = groupKey(groups, storageWorld);
                if (key == null) {
                    continue;
                }
                groups.remove(key);
                if (groups.isEmpty()) {
                    stripGroups(record);
                }
                UtilitiesOG.savePlayerData(uuid, record.getRawHandle());
            } catch (RuntimeException ex) {
                MyWorlds.plugin.getLogger().log(Level.SEVERE, "Failed to drop the " + storageWorld + " profile of " + uuid, ex);
                ok = false;
            } finally {
                UtilitiesOG.unlockPlayerData(uuid);
            }
        }
        return ok;
    }

    // Nests a copy of every profile of one storage world under another. Count copied, 0 when nothing,
    // -1 on failure. The vanilla profiles can not be a target.
    @Override
    public int copy(String fromWorld, String toWorld) {
        if (isLevel(toWorld)) {
            return -1;
        }
        if (fromWorld.equalsIgnoreCase(toWorld)) {
            return 0;
        }
        int copied = 0;
        for (UUID uuid : UtilitiesOG.getSeenPlayers()) {
            UtilitiesOG.lockPlayerData(uuid);
            try {
                CommonTagCompound record = record(uuid);
                if (record == null) {
                    continue;
                }
                CommonTagCompound source = isLevel(fromWorld) ? mainProfile(record.clone()) : groupProfile(record, fromWorld);
                if (source == null) {
                    continue;
                }
                CommonTagCompound groups = groups(record, true);
                String key = groupKey(groups, toWorld);
                groups.put((key == null) ? toWorld : key, nested(source));
                UtilitiesOG.savePlayerData(uuid, record.getRawHandle());
                copied++;
            } catch (RuntimeException ex) {
                MyWorlds.plugin.getLogger().log(Level.SEVERE, "Failed to copy the " + fromWorld + " profile of " + uuid + " to " + toWorld, ex);
                return -1;
            } finally {
                UtilitiesOG.unlockPlayerData(uuid);
            }
        }
        return copied;
    }
}
