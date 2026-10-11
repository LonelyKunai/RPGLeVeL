package com.arclinker.rpglevelitems;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

/** Adds prestige star, active title and guild tag before the player name in chat (chat.prefix). */
public class ChatListener implements Listener {
    private final RPGLevelTier plugin;

    public ChatListener(RPGLevelTier plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent e) {
        if (!plugin.getConfig().getBoolean("chat.prefix", true)) return;
        Player p = e.getPlayer();
        var r = plugin.getPlayerStore().get(p);
        String prefix = plugin.getGuilds().tagOf(p) + plugin.getPrestigeManager().star(p)
                + (r.activeTitle.isEmpty() ? "" : plugin.getAchievements().activeTitleDisplay(r) + " ");
        if (prefix.isEmpty()) return;
        e.setFormat(prefix.replace("%", "%%") + e.getFormat());
    }
}
