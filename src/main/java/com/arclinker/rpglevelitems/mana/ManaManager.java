package com.arclinker.rpglevelitems.mana;

import com.arclinker.rpglevelitems.RPGLevelTier;
import com.arclinker.rpglevelitems.Util;
import com.arclinker.rpglevelitems.ability.AbilityDef;
import com.arclinker.rpglevelitems.rank.PlayerRecord;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Mana: abilities cost mana. Max mana = base + stat {@code mana_max} + class/subclass levels;
 * regenerates every second (base + stat {@code mana_regen}). Config: {@code mana.*}.
 */
public final class ManaManager implements Listener {
    private final RPGLevelTier plugin;
    private final Map<UUID, Double> mana = new HashMap<>();
    private final Map<UUID, Long> lastUse = new HashMap<>();

    public ManaManager(RPGLevelTier plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    public boolean enabled() {
        return plugin.getConfig().getBoolean("mana.enabled", true);
    }

    public double max(Player p) {
        double base = plugin.getConfig().getDouble("mana.base", 100);
        var lm = plugin.getClassLevelManager();
        PlayerRecord r = plugin.getPlayerStore().get(p);
        double perClass = plugin.getConfig().getDouble("mana.per_class_level", 1.5);
        double lv = (r.classId.isEmpty() ? 0 : lm.getClassLevel(p) - 1) + (r.subclassId.isEmpty() ? 0 : lm.getSubClassLevel(p) - 1) * 0.6;
        return base + lv * perClass + plugin.getStatEngine().get(p, "mana_max");
    }

    public double regen(Player p) {
        return plugin.getConfig().getDouble("mana.regen_base", 2.0) + plugin.getStatEngine().get(p, "mana_regen");
    }

    public double get(Player p) {
        return Math.min(max(p), mana.getOrDefault(p.getUniqueId(), max(p)));
    }

    public void set(Player p, double v) {
        mana.put(p.getUniqueId(), Math.max(0, Math.min(max(p), v)));
    }

    public boolean has(Player p, double cost) {
        return !enabled() || cost <= 0 || get(p) + 1e-9 >= cost;
    }

    public void spend(Player p, double cost) {
        if (!enabled() || cost <= 0) return;
        set(p, get(p) - cost);
        lastUse.put(p.getUniqueId(), System.currentTimeMillis());
    }

    /** Mana cost of a class / subclass ability at the given level. */
    public double cost(AbilityDef d, int level) {
        if (d.manaOverride >= 0) return d.manaOverride;
        double c = plugin.getConfig().getDouble("mana.cost_base", 10) + plugin.getConfig().getDouble("mana.cost_per_node_cost", 4) * d.cost;
        return Math.round(c * (1 + plugin.getConfig().getDouble("mana.cost_growth_per_level", 0.04) * (Math.max(1, level) - 1)));
    }

    public double itemCost() {
        return plugin.getConfig().getDouble("mana.item_cost", 8);
    }

    private void tick() {
        if (!enabled()) return;
        boolean bar = plugin.getConfig().getBoolean("mana.actionbar", true);
        long now = System.currentTimeMillis();
        for (Player p : Bukkit.getOnlinePlayers()) {
            double max = max(p);
            double cur = Math.min(max, mana.getOrDefault(p.getUniqueId(), max));
            boolean changed = cur < max;
            if (changed) cur = Math.min(max, cur + regen(p));
            mana.put(p.getUniqueId(), cur);
            boolean recent = now - lastUse.getOrDefault(p.getUniqueId(), 0L) < 6000;
            PlayerRecord r = plugin.getPlayerStore().get(p);
            if (bar && !r.classId.isEmpty() && (changed || recent)) {
                Util.actionBar(p, plugin.getLanguageManager().tr(p, "mana.bar", (int) cur, (int) max, bar(cur / max)));
            }
        }
    }

    private static String bar(double f) {
        int n = (int) Math.round(Math.max(0, Math.min(1, f)) * 10);
        return "§b" + "▮".repeat(n) + "§8" + "▮".repeat(10 - n);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        // keep the mana value in memory so logging out and in does not refill it
        lastUse.remove(e.getPlayer().getUniqueId());
    }
}
