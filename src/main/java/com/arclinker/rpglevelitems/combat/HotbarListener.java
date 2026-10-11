package com.arclinker.rpglevelitems.combat;

import com.arclinker.rpglevelitems.RPGLevelTier;
import com.arclinker.rpglevelitems.Util;
import com.arclinker.rpglevelitems.ability.AbilityDef;
import com.arclinker.rpglevelitems.rank.PlayerRecord;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Ability hotbar: bind up to 4 unlocked abilities (/ability bind). The swap-hand key (F) casts the
 * selected slot; Shift+F selects the next bound slot. Only active if the player has bindings.
 */
public final class HotbarListener implements Listener {
    private final RPGLevelTier plugin;
    private final Map<UUID, Integer> selected = new HashMap<>();

    public HotbarListener(RPGLevelTier plugin) {
        this.plugin = plugin;
    }

    public int selected(Player p) {
        return selected.getOrDefault(p.getUniqueId(), 0);
    }

    public String describe(Player p) {
        PlayerRecord r = plugin.getPlayerStore().get(p);
        StringBuilder sb = new StringBuilder();
        int sel = selected(p);
        for (int i = 0; i < r.hotbar.length; i++) {
            AbilityDef d = r.hotbar[i].isEmpty() ? null : plugin.getAbilityManager().get(r.hotbar[i]);
            sb.append(i == sel ? "§a▶" : "§7").append('[').append(i + 1).append("] ").append(d == null ? "§8-" : "§f" + d.name).append("  ");
        }
        return sb.toString().trim();
    }

    @EventHandler
    public void onSwap(PlayerSwapHandItemsEvent e) {
        if (!plugin.getConfig().getBoolean("hotbar.enabled", true)) return;
        Player p = e.getPlayer();
        PlayerRecord r = plugin.getPlayerStore().get(p);
        boolean any = false;
        for (String s : r.hotbar) if (!s.isEmpty()) any = true;
        if (!any) return;
        e.setCancelled(true);
        if (p.isSneaking()) {
            int sel = selected(p);
            for (int i = 1; i <= r.hotbar.length; i++) {
                int idx = (sel + i) % r.hotbar.length;
                if (!r.hotbar[idx].isEmpty()) {
                    selected.put(p.getUniqueId(), idx);
                    break;
                }
            }
            Util.actionBar(p, describe(p));
            return;
        }
        int sel = selected(p);
        if (r.hotbar[sel].isEmpty()) {
            Util.actionBar(p, "§cRanura " + (sel + 1) + " vacía. " + describe(p));
            return;
        }
        AbilityDef d = plugin.getAbilityManager().get(r.hotbar[sel]);
        if (d == null) {
            r.hotbar[sel] = "";
            return;
        }
        plugin.getAbilityManager().castUnlocked(p, d);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        selected.remove(e.getPlayer().getUniqueId());
    }
}
