package com.arclinker.rpglevelitems.api.events;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/** A player is about to forge something at a work station (cancel to prevent it). */
public class StationCraftEvent extends Event implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();
    private final Player player;
    private final String stationId;
    private final String itemId;
    private final int amount;
    private boolean cancelled;

    public StationCraftEvent(Player player, String stationId, String itemId, int amount) {
        this.player = player;
        this.stationId = stationId;
        this.itemId = itemId;
        this.amount = amount;
    }

    public Player getPlayer() {
        return player;
    }

    public String getStationId() {
        return stationId;
    }

    public String getItemId() {
        return itemId;
    }

    public int getAmount() {
        return amount;
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
