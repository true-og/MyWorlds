package com.bergerkiller.bukkit.mw.playerdata;

import java.util.Map;

import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.entity.Player;

import com.bergerkiller.bukkit.common.nbt.CommonTagCompound;
import com.bergerkiller.bukkit.mw.MyWorlds;
import com.bergerkiller.bukkit.mw.WorldConfig;

public class PlayerDataFileCollection {
    public final OfflinePlayer player;
    public PlayerDataEntry mainWorldFile; /* Stores the name of the main world the player is on */
    public PlayerDataEntry positionFile;  /* Stores the position of the player on a particular world */
    public PlayerDataEntry currentFile;   /* Stores the inventory and data of the player on a particular world. Merge/split rules apply. */

    static void init() {
    }

    public PlayerDataFileCollection(OfflinePlayer player, World world) {
        this.player = player;
        this.mainWorldFile = PlayerDataEntry.main(player);

        setCurrentWorld(world);
    }

    /**
     * Sets the current world the player is in, switching the file locations
     * 
     * @param currentWorld the player is in
     */
    public void setCurrentWorld(World currentWorld) {
        WorldConfig currentWorldConfig = WorldConfig.get(currentWorld);
        this.positionFile = new PlayerDataEntry(player, currentWorldConfig);
        if (MyWorlds.useWorldInventories) {
            WorldConfig inventoryWorld = WorldConfig.get(currentWorldConfig.inventory.getSharedWorldName());
            this.currentFile = new PlayerDataEntry(player, inventoryWorld);
        } else {
            this.currentFile = this.mainWorldFile;
        }
    }

    /**
     * Both position and inventory state information is saved in the same file
     * 
     * @return True if it is the same file, False if not
     */
    public boolean isSingleDataFile() {
        return positionFile.isSameStorage(currentFile);
    }

    /**
     * The inventory information is stored in the same file as the main world information.
     * If this is the case, the world the player is on does not have to be updated separately.
     * 
     * @return True if the inventory and main world are saved in the same file
     */
    public boolean isMainWorld() {
        return mainWorldFile.isSameStorage(currentFile);
    }

    // Tag already staged for this storage world, else a fresh read. Two roles can share one storage world.
    public CommonTagCompound tagFor(Map<String, CommonTagCompound> batch, PlayerDataEntry entry, Player player) {
        CommonTagCompound staged = batch.get(entry.storageWorld());
        return (staged != null) ? staged : entry.read(player);
    }

    /* Debug */
}
