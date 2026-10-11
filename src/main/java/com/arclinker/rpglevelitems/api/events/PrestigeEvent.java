package com.arclinker.rpglevelitems.api.events;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/** A player prestiged a class. */
public class PrestigeEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final Player player;
    private final String classId;
    private final int prestige;

    public PrestigeEvent(Player player, String classId, int prestige) {
        this.player = player;
        this.classId = classId;
        this.prestige = prestige;
    }

    public Player getPlayer() {
        return player;
    }

    public String getClassId() {
        return classId;
    }

    public int getPrestige() {
        return prestige;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
