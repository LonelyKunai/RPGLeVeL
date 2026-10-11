package com.arclinker.rpglevelitems.api.events;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/** A player unlocked an achievement. */
public class AchievementUnlockEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final Player player;
    private final String achievementId;

    public AchievementUnlockEvent(Player player, String achievementId) {
        this.player = player;
        this.achievementId = achievementId;
    }

    public Player getPlayer() {
        return player;
    }

    public String getAchievementId() {
        return achievementId;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
