package com.bergerkiller.bukkit.mw.events;

import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;

// Fired after MyWorlds applied a stored inventory profile to a player who changed worlds
public class PlayerProfileAppliedEvent extends PlayerEvent {
    private static final HandlerList handlers = new HandlerList();
    private final GameMode profileGameMode;
    private final String storageWorld;

    public PlayerProfileAppliedEvent(Player player, GameMode profileGameMode, String storageWorld) {
        super(player);
        this.profileGameMode = profileGameMode;
        this.storageWorld = storageWorld;
    }

    // Game mode the applied inventory was saved under, null when the profile had none
    public GameMode getProfileGameMode() {
        return profileGameMode;
    }

    // World whose storage holds the profile, shared by every world of the inventory group
    public String getStorageWorld() {
        return storageWorld;
    }

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }
}
