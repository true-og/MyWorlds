package com.bergerkiller.bukkit.mw.playerdata;

import java.io.IOException;
import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.common.Common;
import com.bergerkiller.bukkit.common.entity.CommonEntity;
import com.bergerkiller.bukkit.common.entity.type.CommonPlayer;
import com.bergerkiller.bukkit.common.nbt.CommonTagCompound;
import com.bergerkiller.bukkit.common.utils.WorldUtil;
import com.bergerkiller.bukkit.common.wrappers.PlayerRespawnPoint;
import com.bergerkiller.bukkit.mw.MWPlayerDataController;
import com.bergerkiller.bukkit.mw.MyWorlds;
import com.bergerkiller.bukkit.mw.WorldConfig;
import com.bergerkiller.bukkit.mw.WorldManager;

// A player's profile on one storage world, read and written through the selected PlayerDataStore
public class PlayerDataEntry {
    private static final boolean SAVE_HEAL_F = Common.evaluateMCVersion("<=", "1.8.8");

    public final OfflinePlayer player;
    public final WorldConfig world;

    public PlayerDataEntry(OfflinePlayer player, WorldConfig worldConfig) {
        this.player = player;
        this.world = worldConfig;
    }

    static void init() {
    }

    public String storageWorld() {
        return world.worldname;
    }

    public boolean isSameStorage(PlayerDataEntry other) {
        return storageWorld().equalsIgnoreCase(other.storageWorld());
    }

    public boolean exists() {
        return PlayerDataStores.current().has(storageWorld(), player.getUniqueId());
    }

    public CommonTagCompound readIfExists() {
        return PlayerDataStores.current().read(storageWorld(), player.getUniqueId());
    }

    public CommonTagCompound read(Player player) {
        CommonTagCompound data = this.readIfExists();
        if (data != null) {
            return data;
        }
        // A pending failed import must not pass as a new player, or the empty profile would bury the retained file
        if (LegacyPlayerDataImporter.importFailed(storageWorld(), player.getUniqueId())) {
            throw new IllegalStateException("Legacy player data import pending for " + player.getName() + " on " + storageWorld());
        }
        return createEmptyData(player);
    }

    public void write(CommonTagCompound data) throws IOException {
        PlayerDataStores.current().write(storageWorld(), player.getUniqueId(), data);
    }

    public static PlayerDataEntry main(OfflinePlayer player) {
        return new PlayerDataEntry(player, WorldConfig.getInventoryMain());
    }

    /**
     * Creates new player data information as if the player just joined the server.
     * 
     * @param player to generate information about
     * @return empty data
     */
    public static CommonTagCompound createEmptyData(Player player) {
        final Vector velocity = player.getVelocity();
        CommonTagCompound empty = new CommonTagCompound();
        CommonPlayer playerEntity = CommonEntity.get(player);
        empty.putUUID("", player.getUniqueId());
        if (SAVE_HEAL_F) {
            empty.putValue("HealF", (float) playerEntity.getMaxHealth());
        }
        empty.putValue("Health", (float) playerEntity.getMaxHealth());
        empty.putValue("HurtTime", (short) 0);
        empty.putValue("DeathTime", (short) 0);
        empty.putValue("AttackTime", (short) 0);
        empty.putListValues("Motion", velocity.getX(), velocity.getY(), velocity.getZ());
        setLocation(empty, WorldManager.getSpawnLocation(MyWorlds.getMainWorld()));
        PlayerRespawnPoint.forPlayer(player).toNBT(empty);
        return empty;
    }

    public static void setLocation(CommonTagCompound tagCompound, Location location) {
        tagCompound.putListValues("Pos", location.getX(), location.getY(), location.getZ());
        tagCompound.putListValues("Rotation", location.getYaw(), location.getPitch());
        final World world = location.getWorld();
        tagCompound.putValue("World", world.getName());
        tagCompound.putUUID("World", world.getUID());

        if (Common.evaluateMCVersion(">=","1.16")) {
            // Dimension stored as a string key
            tagCompound.putMinecraftKey("Dimension", WorldUtil.getDimensionKey(world).getName());
        } else {
            // Dimension TYPE stored as an ID. Server uses world name / uuid fields to figure out what world.
            tagCompound.putValue("Dimension", WorldUtil.getDimensionType(world).getId());
        }
    }

    /**
     * Checks whether the main world file data stores data for all worlds on the server. This is the case
     * when a profile is saved before MyWorlds multi-world inventories is enabled. or before MyWorlds is even
     * installed.
     *
     * @param mainWorldData
     * @return True if the player profile data is self-contained
     */
    public static boolean isSelfContained(CommonTagCompound mainWorldData) {
        // On modern versions it has a separate data tag to track whether all player data was shared.
        // This prevents some annoying edge-cases from tripping this code up.
        {
            CommonTagCompound myworlds = mainWorldData.get(MWPlayerDataController.DATA_TAG_ROOT, CommonTagCompound.class);
            if (myworlds != null) {
                Boolean is_self_contained = myworlds.getValue(MWPlayerDataController.DATA_TAG_IS_SELF_CONTAINED, Boolean.class);
                if (is_self_contained != null) {
                    return is_self_contained.booleanValue();
                }
            }
        }

        // Legacy stuff...
        {
            // Check whether the inventory sharing rules for the data's world specify a different world
            UUID worldUUID = mainWorldData.getUUID("World");
            UUID mw_legacy_inventory_world = mainWorldData.getUUID(MWPlayerDataController.LEGACY_DATA_TAG_LASTWORLD);
            if (worldUUID != null && mw_legacy_inventory_world != null) {
                // If different that is a strong indication that inventory data was stored split per world.
                // This is not perfect, however. And assumes the main world is also an inventory storing world.
                return worldUUID.equals(mw_legacy_inventory_world);
            }

            // Assume true
            return true;
        }
    }
}
