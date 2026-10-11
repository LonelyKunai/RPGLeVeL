package com.arclinker.rpglevelitems.api.events;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/** A class / subclass ability is about to be cast (cancel to prevent it). */
public class AbilityCastEvent extends Event implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();
    private final Player player;
    private final String abilityId;
    private final int level;
    private boolean cancelled;

    public AbilityCastEvent(Player player, String abilityId, int level) {
        this.player = player;
        this.abilityId = abilityId;
        this.level = level;
    }

    public Player getPlayer() {
        return player;
    }

    public String getAbilityId() {
        return abilityId;
    }

    public int getLevel() {
        return level;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
