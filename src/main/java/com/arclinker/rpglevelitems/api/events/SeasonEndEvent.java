package com.arclinker.rpglevelitems.api.events;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/** A ranking season ended. */
public class SeasonEndEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final int season;

    public SeasonEndEvent(int season) {
        this.season = season;
    }

    public int getSeason() {
        return season;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
