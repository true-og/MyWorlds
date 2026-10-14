package com.bergerkiller.bukkit.mw.playerdata;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.bukkit.Bukkit;
import org.rocksdb.ColumnFamilyHandle;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;
import org.rocksdb.RocksIterator;
import org.rocksdb.WriteBatch;
import org.rocksdb.WriteOptions;

import com.bergerkiller.bukkit.common.nbt.CommonTagCompound;

import net.trueog.utilitiesog.UtilitiesOG;

// Server RocksDB backend. The level's profile is the vanilla one, reached through the Utilities-OG API;
// every other storage world is a world:<name> column family this class owns in the same database.
public final class RocksPlayerDataStore implements PlayerDataStore {
    private static final String PREFIX = "world:";
    private static final int COPY_BATCH = 512;

    // Reads and writes hold the read lock, drop holds the write lock, so a column family never vanishes mid-use
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
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
                Class.forName(name, false, Bukkit.class.getClassLoader()).getMethod("getDatabase");
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

    // The level's storage world is the vanilla profile, which only the server writes directly
    private boolean isLevel(String storageWorld) {
        return storageWorld.equalsIgnoreCase(levelName);
    }

    private static String familyName(String storageWorld) {
        return PREFIX + storageWorld;
    }

    // Same key layout as the server's vanilla profiles
    private static byte[] key(UUID uuid) {
        ByteBuffer buffer = ByteBuffer.wrap(new byte[16]);
        buffer.putLong(uuid.getMostSignificantBits());
        buffer.putLong(uuid.getLeastSignificantBits());
        return buffer.array();
    }

    private static UUID uuid(byte[] key) {
        ByteBuffer buffer = ByteBuffer.wrap(key);
        return new UUID(buffer.getLong(), buffer.getLong());
    }

    // Uncompressed NBT, the same bytes the server stores for vanilla profiles
    private static byte[] toBytes(CommonTagCompound data) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        data.writeToStream(out, false);
        return out.toByteArray();
    }

    private static CommonTagCompound fromBytes(byte[] bytes) throws IOException {
        return CommonTagCompound.readFromStream(new ByteArrayInputStream(bytes), false);
    }

    private static RocksDB database() throws IOException {
        RocksDB db = UtilitiesOG.getPlayerDataDatabase();
        if (db == null) {
            throw new IOException("The server's player data storage is closed");
        }
        return db;
    }

    private static CommonTagCompound wrap(Object rawTag) {
        return (rawTag == null) ? null : CommonTagCompound.create(rawTag);
    }

    // Null when absent. Throws IllegalStateException when the storage failed, never a fresh profile in that case.
    @Override
    public CommonTagCompound read(String storageWorld, UUID uuid) {
        if (isLevel(storageWorld)) {
            return wrap(UtilitiesOG.getPlayerData(uuid));
        }
        lock.readLock().lock();
        try {
            ColumnFamilyHandle family = UtilitiesOG.getPlayerDataColumnFamily(familyName(storageWorld), false);
            if (family == null) {
                return null;
            }
            byte[] bytes = database().get(family, key(uuid));
            if (bytes == null) {
                return null;
            }
            // Upgraded like a vanilla profile, so old item formats load the same way
            return wrap(UtilitiesOG.upgradePlayerData(fromBytes(bytes).getRawHandle()));
        } catch (IOException | RocksDBException | RuntimeException ex) {
            throw new IllegalStateException("Player data storage failed for " + uuid + " on " + storageWorld, ex);
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public void write(String storageWorld, UUID uuid, CommonTagCompound data) throws IOException {
        writeAll(uuid, Collections.singletonMap(storageWorld, data));
    }

    // One atomic batch: the vanilla profile is staged by the server so it keeps its previous backup
    @Override
    public void writeAll(UUID uuid, Map<String, CommonTagCompound> perWorld) throws IOException {
        // Lock the UUID so an offline edit through Utilities-OG never interleaves with this save
        UtilitiesOG.lockPlayerData(uuid);
        lock.readLock().lock();
        try (WriteBatch batch = new WriteBatch(); WriteOptions options = new WriteOptions()) {
            RocksDB db = database();
            for (Map.Entry<String, CommonTagCompound> entry : perWorld.entrySet()) {
                String storageWorld = entry.getKey();
                if (isLevel(storageWorld)) {
                    if (!UtilitiesOG.stagePlayerData(batch, uuid, entry.getValue().getRawHandle())) {
                        throw new IOException("Player data storage refused the write of " + uuid + " on " + storageWorld);
                    }
                    continue;
                }
                ColumnFamilyHandle family = UtilitiesOG.getPlayerDataColumnFamily(familyName(storageWorld), true);
                if (family == null) {
                    throw new IOException("Player data storage has no column family for " + storageWorld);
                }
                batch.put(family, key(uuid), toBytes(entry.getValue()));
            }
            db.write(options, batch);
        } catch (RocksDBException ex) {
            throw new IOException("Player data storage failed the write of " + uuid + " on " + perWorld.keySet(), ex);
        } finally {
            lock.readLock().unlock();
            UtilitiesOG.unlockPlayerData(uuid);
        }
    }

    @Override
    public boolean flush() {
        try {
            database().flushWal(true);
            return true;
        } catch (IOException | RocksDBException ex) {
            return false;
        }
    }

    @Override
    public boolean has(String storageWorld, UUID uuid) {
        if (isLevel(storageWorld)) {
            return UtilitiesOG.hasPlayerData(uuid);
        }
        lock.readLock().lock();
        try {
            ColumnFamilyHandle family = UtilitiesOG.getPlayerDataColumnFamily(familyName(storageWorld), false);
            return family != null && database().get(family, key(uuid)) != null;
        } catch (IOException | RocksDBException ex) {
            throw new IllegalStateException("Player data storage failed for " + uuid + " on " + storageWorld, ex);
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public List<UUID> list(String storageWorld) {
        if (isLevel(storageWorld)) {
            return UtilitiesOG.getSeenPlayers();
        }
        lock.readLock().lock();
        try {
            ColumnFamilyHandle family = UtilitiesOG.getPlayerDataColumnFamily(familyName(storageWorld), false);
            if (family == null) {
                return Collections.emptyList();
            }
            List<UUID> players = new ArrayList<>();
            try (RocksIterator it = database().newIterator(family)) {
                for (it.seekToFirst(); it.isValid(); it.next()) {
                    players.add(uuid(it.key()));
                }
            }
            return players;
        } catch (IOException ex) {
            return Collections.emptyList();
        } finally {
            lock.readLock().unlock();
        }
    }

    // The vanilla profiles are never dropped. True when removed or absent.
    @Override
    public boolean drop(String storageWorld) {
        if (isLevel(storageWorld)) {
            return false;
        }
        lock.writeLock().lock();
        try {
            return UtilitiesOG.dropPlayerDataColumnFamily(familyName(storageWorld));
        } finally {
            lock.writeLock().unlock();
        }
    }

    // Count copied, 0 when the source is empty or absent, -1 on failure. The vanilla profiles can not be a target.
    @Override
    public int copy(String fromWorld, String toWorld) {
        if (isLevel(toWorld) || fromWorld.equalsIgnoreCase(toWorld)) {
            return isLevel(toWorld) ? -1 : 0;
        }
        lock.readLock().lock();
        try (WriteBatch batch = new WriteBatch(); WriteOptions options = new WriteOptions()) {
            RocksDB db = database();
            ColumnFamilyHandle target = UtilitiesOG.getPlayerDataColumnFamily(familyName(toWorld), true);
            if (target == null) {
                return -1;
            }
            int copied = 0;
            if (isLevel(fromWorld)) {
                // The vanilla profiles are only reachable through the API, one at a time
                for (UUID player : UtilitiesOG.getSeenPlayers()) {
                    CommonTagCompound data = wrap(UtilitiesOG.getPlayerData(player));
                    if (data == null) {
                        continue;
                    }
                    batch.put(target, key(player), toBytes(data));
                    copied++;
                    if (batch.count() >= COPY_BATCH) {
                        db.write(options, batch);
                        batch.clear();
                    }
                }
            } else {
                ColumnFamilyHandle source = UtilitiesOG.getPlayerDataColumnFamily(familyName(fromWorld), false);
                if (source == null) {
                    return 0;
                }
                try (RocksIterator it = db.newIterator(source)) {
                    for (it.seekToFirst(); it.isValid(); it.next()) {
                        batch.put(target, it.key(), it.value());
                        copied++;
                        if (batch.count() >= COPY_BATCH) {
                            db.write(options, batch);
                            batch.clear();
                        }
                    }
                }
            }
            if (batch.count() > 0) {
                db.write(options, batch);
            }
            return copied;
        } catch (IOException | RocksDBException | RuntimeException ex) {
            return -1;
        } finally {
            lock.readLock().unlock();
        }
    }
}
