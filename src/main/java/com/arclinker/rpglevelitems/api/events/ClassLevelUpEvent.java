package com.arclinker.rpglevelitems.api.events;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/** A player's class or subclass levelled up. */
public class ClassLevelUpEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final Player player;
    private final boolean subclass;
    private final String id;
    private final int from;
    private final int to;

    public ClassLevelUpEvent(Player player, boolean subclass, String id, int from, int to) {
        this.player = player;
        this.subclass = subclass;
        this.id = id;
        this.from = from;
        this.to = to;
    }

    public Player getPlayer() {
        return player;
    }

    public boolean getSubclass() {
        return subclass;
    }

    public String getId() {
        return id;
    }

    public int getFrom() {
        return from;
    }

    public int getTo() {
        return to;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
