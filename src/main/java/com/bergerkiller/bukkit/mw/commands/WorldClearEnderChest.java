package com.bergerkiller.bukkit.mw.commands;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.entity.Player;

import com.bergerkiller.bukkit.common.nbt.CommonTagCompound;
import com.bergerkiller.bukkit.mw.Permission;
import com.bergerkiller.bukkit.mw.WorldConfig;
import com.bergerkiller.bukkit.mw.playerdata.PlayerDataFile;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

/**
 * Command specifically for clearing a player's enderchest in a specific world.
 * This provides a cleaner interface when you only want to clear the enderchest.
 */
public class WorldClearEnderChest extends Command {

    public WorldClearEnderChest() {
        super(Permission.COMMAND_INVENTORY_CLEAR, "world.clearenderchest");
    }

    @Override
    public void execute() {
        if (args.length != 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /world clearenderchest <player> <world>");
            return;
        }

        String playerInput = args[0];
        String worldName = args[1];

        OfflinePlayer targetPlayer = getOfflinePlayer(playerInput);

        if (targetPlayer == null) {
            sender.sendMessage(ChatColor.RED + "Player '" + playerInput + "' not found!");
            return;
        }

        World targetWorld = Bukkit.getWorld(worldName);

        if (targetWorld == null) {
            sender.sendMessage(ChatColor.RED + "World '" + worldName + "' not found!");
            return;
        }

        WorldConfig worldConfig = WorldConfig.get(targetWorld);

        try {
            PlayerDataFile playerData = new PlayerDataFile(targetPlayer, worldConfig);

            if (targetPlayer.isOnline()) {
                Player player = targetPlayer.getPlayer();

                if (!player.getWorld().equals(targetWorld)) {
                    // Player is in a different world, update saved data
                    playerData.updateIfExists(data -> {
                        data.remove("EnderItems");
                    });
                } else {
                    // Player is in the target world, clear active enderchest
                    player.getEnderChest().clear();
                }
            } else {
                // Offline player, update saved data
                playerData.updateIfExists(data -> {
                    data.remove("EnderItems");
                });
            }

            sender.sendMessage(ChatColor.GREEN + "Cleared enderchest for " + 
                             targetPlayer.getName() + " in world " + worldName);
            
            logAction("Cleared enderchest for " + targetPlayer.getName() + 
                     " in world " + worldName);
                     
        } catch (IOException e) {
            sender.sendMessage(ChatColor.RED + "Error clearing enderchest: " + e.getMessage());
            plugin.getLogger().severe("Failed to clear enderchest for " + 
                                    targetPlayer.getName() + " in world " + worldName);
            e.printStackTrace();
        }
    }

    private OfflinePlayer getOfflinePlayer(String input) {
        // Try to parse as UUID
        try {
            UUID uuid = UUID.fromString(input);
            return Bukkit.getOfflinePlayer(uuid);
        } catch (IllegalArgumentException e) {
            // Not a UUID, continue
        }

        // Try online player
        Player onlinePlayer = Bukkit.getPlayerExact(input);
        if (onlinePlayer != null) {
            return onlinePlayer;
        }

        // Search offline players
        for (OfflinePlayer offlinePlayer : Bukkit.getOfflinePlayers()) {
            if (offlinePlayer.getName() != null && 
                offlinePlayer.getName().equalsIgnoreCase(input)) {
                return offlinePlayer;
            }
        }

        return null;
    }

    @Override
    public List<String> autocomplete() {
        if (args.length == 1) {
            return processPlayerNameAutocomplete();
        } else if (args.length == 2) {
            return processWorldNameAutocomplete();
        }
        return null;
    }
}