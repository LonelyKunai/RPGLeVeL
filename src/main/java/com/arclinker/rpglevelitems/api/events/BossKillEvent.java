package com.arclinker.rpglevelitems.api.events;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/** A plugin world boss died. */
public class BossKillEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final String bossId;
    private final Player topDamager;

    public BossKillEvent(String bossId, Player topDamager) {
        this.bossId = bossId;
        this.topDamager = topDamager;
    }

    public String getBossId() {
        return bossId;
    }

    public Player getTopDamager() {
        return topDamager;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
