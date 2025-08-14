package com.bergerkiller.bukkit.mw.commands;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import com.bergerkiller.bukkit.common.inventory.InventoryBaseImpl;
import com.bergerkiller.bukkit.common.nbt.CommonTagCompound;
import com.bergerkiller.bukkit.mw.Permission;
import com.bergerkiller.bukkit.mw.WorldConfig;
import com.bergerkiller.bukkit.mw.playerdata.PlayerDataFile;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class WorldClearInventory extends Command {
    
    private enum ClearType {
        INVENTORY("inventory", "inv"),
        ENDERCHEST("enderchest", "ender", "ec"),
        BOTH("both", "all");
        
        private final String[] aliases;
        
        ClearType(String... aliases) {
            this.aliases = aliases;
        }
        
        public static ClearType parse(String input) {
            String lower = input.toLowerCase();
            for (ClearType type : values()) {
                for (String alias : type.aliases) {
                    if (alias.equals(lower)) {
                        return type;
                    }
                }
            }
            return null;
        }
        
        public static List<String> getAllAliases() {
            return Arrays.stream(values())
                .flatMap(type -> Arrays.stream(type.aliases))
                .collect(Collectors.toList());
        }
    }

    public WorldClearInventory() {
        super(Permission.COMMAND_INVENTORY_CLEAR, "world.clearinventory");
    }

    @Override
    public void execute() {
        if (args.length < 2 || args.length > 3) {
            showUsage();
            return;
        }

        String playerInput = args[0];
        String worldName = args[1];
        ClearType clearType = ClearType.BOTH; // Default to clearing both
        
        // Parse the clear type if provided
        if (args.length == 3) {
            clearType = ClearType.parse(args[2]);
            if (clearType == null) {
                sender.sendMessage(ChatColor.RED + "Invalid clear type! Use: inventory, enderchest, or both");
                return;
            }
        }

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
            final ClearType finalClearType = clearType;
            
            // Handle online players
            if (targetPlayer.isOnline()) {
                Player player = targetPlayer.getPlayer();

                if (!player.getWorld().equals(targetWorld)) {
                    // Player is in a different world, update their saved data
                    playerData.updateIfExists(data -> {
                        if (shouldClearInventory(finalClearType)) {
                            clearInventoryData(data);
                        }
                        if (shouldClearEnderChest(finalClearType)) {
                            clearEnderChestData(data);
                        }
                    });
                } else {
                    // Player is in the target world, clear their active inventory
                    if (shouldClearInventory(finalClearType)) {
                        player.getInventory().clear();
                    }
                    if (shouldClearEnderChest(finalClearType)) {
                        player.getEnderChest().clear();
                    }
                }
            } else {
                // Offline player, only update saved data
                playerData.updateIfExists(data -> {
                    if (shouldClearInventory(finalClearType)) {
                        clearInventoryData(data);
                    }
                    if (shouldClearEnderChest(finalClearType)) {
                        clearEnderChestData(data);
                    }
                });
            }

            // Success message
            String clearedMessage = buildClearedMessage(targetPlayer.getName(), worldName, clearType);
            sender.sendMessage(ChatColor.GREEN + clearedMessage);
            
            // Log the action
            logAction("Cleared " + clearType.name().toLowerCase() + " for " + 
                     targetPlayer.getName() + " in world " + worldName);
                     
        } catch (IOException e) {
            sender.sendMessage(ChatColor.RED + "Error clearing data: " + e.getMessage());
            plugin.getLogger().severe("Failed to clear inventory data for " + 
                                    targetPlayer.getName() + " in world " + worldName);
            e.printStackTrace();
        }
    }
    
    private boolean shouldClearInventory(ClearType type) {
        return type == ClearType.INVENTORY || type == ClearType.BOTH;
    }
    
    private boolean shouldClearEnderChest(ClearType type) {
        return type == ClearType.ENDERCHEST || type == ClearType.BOTH;
    }
    
    private void clearInventoryData(CommonTagCompound data) {
        data.remove("Inventory");
        // Also clear other inventory-related data
        data.remove("SelectedItemSlot");
        data.remove("XpLevel");
        data.remove("XpTotal");
        data.remove("XpP");
    }
    
    private void clearEnderChestData(CommonTagCompound data) {
        data.remove("EnderItems");
    }
    
    private String buildClearedMessage(String playerName, String worldName, ClearType type) {
        switch (type) {
            case INVENTORY:
                return "Cleared inventory for " + playerName + " in world " + worldName;
            case ENDERCHEST:
                return "Cleared enderchest for " + playerName + " in world " + worldName;
            case BOTH:
                return "Cleared inventory and enderchest for " + playerName + " in world " + worldName;
            default:
                return "Cleared data for " + playerName + " in world " + worldName;
        }
    }

    private OfflinePlayer getOfflinePlayer(String input) {
        // First, try to parse as UUID
        try {
            UUID uuid = UUID.fromString(input);
            return Bukkit.getOfflinePlayer(uuid);
        } catch (IllegalArgumentException e) {
            // Not a UUID, try as a player name
        }

        // Try to get online player (exact match)
        Player onlinePlayer = Bukkit.getPlayerExact(input);
        if (onlinePlayer != null) {
            return onlinePlayer;
        }

        // Search offline players
        for (OfflinePlayer offlinePlayer : Bukkit.getOfflinePlayers()) {
            if (offlinePlayer.getName() != null && offlinePlayer.getName().equalsIgnoreCase(input)) {
                return offlinePlayer;
            }
        }

        return null;
    }
    
    @Override
    public boolean showUsage() {
        sender.sendMessage(ChatColor.YELLOW + "Usage: /world clearinventory <player> <world> [type]");
        sender.sendMessage(ChatColor.GRAY + "  Types: inventory, enderchest, both (default)");
        sender.sendMessage(ChatColor.GRAY + "  Example: /world clearinv Steve world_nether inventory");
        sender.sendMessage(ChatColor.GRAY + "  Example: /world clearinv Steve world enderchest");
        return true;
    }

    @Override
    public List<String> autocomplete() {
        if (args.length == 1) {
            // First argument: player name
            return processPlayerNameAutocomplete();
        } else if (args.length == 2) {
            // Second argument: world name
            return processWorldNameAutocomplete();
        } else if (args.length == 3) {
            // Third argument: clear type
            return processAutocomplete(ClearType.getAllAliases().stream());
        }
        return null;
    }
}