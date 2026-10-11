package com.arclinker.rpglevelitems.prestige;

import com.arclinker.rpglevelitems.RPGLevelTier;
import com.arclinker.rpglevelitems.rank.PlayerRecord;
import com.arclinker.rpglevelitems.stats.Stats;
import org.bukkit.entity.Player;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Prestige: at max class level a player can reset that class' level to 1 in exchange for permanent
 * bonuses (config {@code prestige.*}): stats per prestige, extra skill points and a star title.
 * Ability progress is kept; only the level (and therefore the stats from levels) restarts.
 */
public final class PrestigeManager {
    private final RPGLevelTier plugin;

    public PrestigeManager(RPGLevelTier plugin) {
        this.plugin = plugin;
        plugin.getStatEngine().addProvider(this::bonus);
    }

    public int total(Player p) {
        int n = 0;
        for (int v : plugin.getPlayerStore().get(p).prestige.values()) n += v;
        return n;
    }

    public int of(Player p, String classId) {
        return plugin.getPlayerStore().get(p).prestige.getOrDefault(classId, 0);
    }

    /** Extra skill points for a class tree (one per prestige by default). */
    public int extraPoints(Player p, String treeKey) {
        if (!treeKey.startsWith("class:")) return 0;
        return of(p, treeKey.substring(6)) * plugin.getConfig().getInt("prestige.skill_points_per_prestige", 1);
    }

    /** Extra skill points for a record (offline inspection). */
    public int extraPointsFor(PlayerRecord r, String treeKey) {
        if (!treeKey.startsWith("class:")) return 0;
        return r.prestige.getOrDefault(treeKey.substring(6), 0) * plugin.getConfig().getInt("prestige.skill_points_per_prestige", 1);
    }

    private Map<String, Double> bonus(Player p) {
        Map<String, Double> out = new LinkedHashMap<>();
        int t = total(p);
        if (t <= 0) return out;
        var cfg = plugin.getConfig().getConfigurationSection("prestige.stats_per_prestige");
        if (cfg == null) return out;
        for (String k : cfg.getKeys(false)) if (Stats.isKnown(k)) Stats.add(out, k, cfg.getDouble(k) * t);
        return out;
    }

    public String star(Player p) {
        int t = total(p);
        return t <= 0 ? "" : "§6★" + (t > 1 ? t : "") + " ";
    }

    /** Resets the active class level and grants the prestige. Returns an error message or null. */
    public String prestige(Player p) {
        PlayerRecord r = plugin.getPlayerStore().get(p);
        if (r.classId.isEmpty()) return "Necesitas una clase.";
        var lm = plugin.getClassLevelManager();
        if (lm.getClassLevel(p) < lm.maxLevel()) return "Necesitas el nivel máximo de clase (" + lm.maxLevel() + ").";
        int max = plugin.getConfig().getInt("prestige.max", 10);
        if (of(p, r.classId) >= max) return "Ya alcanzaste el prestigio máximo (" + max + ").";
        r.classXp.put(r.classId, 0L);
        r.prestige.merge(r.classId, 1, Integer::sum);
        plugin.getPlayerStore().markDirty();
        lm.applyStats(p);
        p.sendTitle("§6§l★ PRESTIGIO " + of(p, r.classId) + " ★", "§7" + r.classId, 10, 70, 20);
        p.sendMessage("§6¡Prestigio " + of(p, r.classId) + " en §f" + r.classId + "§6! Tu nivel de clase se reinició; conservas tus habilidades y ganas bonos permanentes.");
        org.bukkit.Bukkit.getPluginManager().callEvent(new com.arclinker.rpglevelitems.api.events.PrestigeEvent(p, r.classId, of(p, r.classId)));
        if (plugin.getAchievements() != null) plugin.getAchievements().add(p, "prestige", 1);
        return null;
    }
}
