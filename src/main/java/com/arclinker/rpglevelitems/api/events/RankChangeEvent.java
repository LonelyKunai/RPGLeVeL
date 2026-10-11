package com.arclinker.rpglevelitems.api.events;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/** A player's rank in a category/ladder went up. */
public class RankChangeEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final Player player;
    private final String category;
    private final String scope;
    private final String oldRank;
    private final String newRank;

    public RankChangeEvent(Player player, String category, String scope, String oldRank, String newRank) {
        this.player = player;
        this.category = category;
        this.scope = scope;
        this.oldRank = oldRank;
        this.newRank = newRank;
    }

    public Player getPlayer() {
        return player;
    }

    public String getCategory() {
        return category;
    }

    public String getScope() {
        return scope;
    }

    public String getOldRank() {
        return oldRank;
    }

    public String getNewRank() {
        return newRank;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
