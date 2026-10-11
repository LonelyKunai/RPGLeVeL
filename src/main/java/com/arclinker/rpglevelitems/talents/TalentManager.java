package com.arclinker.rpglevelitems.talents;

import com.arclinker.rpglevelitems.DataFiles;
import com.arclinker.rpglevelitems.RPGLevelTier;
import com.arclinker.rpglevelitems.Util;
import com.arclinker.rpglevelitems.rank.PlayerRecord;
import com.arclinker.rpglevelitems.stats.Stats;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.util.*;

/**
 * Global passive talents (data/talents/): 4 branches, grid positions, prerequisites and per-level stats.
 * Talent points come from class / subclass levels, prestige and achievements.
 */
public final class TalentManager {
    public record Talent(String id, String name, String description, Material icon, String branch, int row, int col, int max, int cost,
                         Map<String, Integer> requires, Map<String, Double> stats) {}

    public record Branch(String id, String name, Material icon) {}

    private final RPGLevelTier plugin;
    private final Map<String, Talent> talents = new LinkedHashMap<>();
    private final Map<String, Branch> branches = new LinkedHashMap<>();
    private int levelsPerPoint = 5, perPrestige = 2, achPerPoint = 5;
    private double respecCost = 300;

    public TalentManager(RPGLevelTier plugin) {
        this.plugin = plugin;
        reload();
        plugin.getStatEngine().addProvider(this::bonus);
    }

    public void reload() {
        talents.clear();
        branches.clear();
        YamlConfiguration cfg = DataFiles.loadMerged(plugin, "talents");
        levelsPerPoint = Math.max(1, cfg.getInt("settings.levels_per_point", 5));
        perPrestige = cfg.getInt("settings.points_per_prestige", 2);
        achPerPoint = Math.max(1, cfg.getInt("settings.achievements_per_point", 5));
        respecCost = cfg.getDouble("settings.respec_cost", 300);
        ConfigurationSection b = cfg.getConfigurationSection("branches");
        if (b != null) {
            for (String id : b.getKeys(false)) {
                branches.put(id, new Branch(id, b.getString(id + ".name", id), Util.icon(b.getString(id + ".icon"), Material.BOOK)));
            }
        }
        ConfigurationSection t = cfg.getConfigurationSection("talents");
        if (t != null) {
            for (String id : t.getKeys(false)) {
                ConfigurationSection s = t.getConfigurationSection(id);
                if (s == null) continue;
                Map<String, Integer> req = new LinkedHashMap<>();
                ConfigurationSection rs = s.getConfigurationSection("requires");
                if (rs != null) for (String k : rs.getKeys(false)) req.put(k, rs.getInt(k));
                Map<String, Double> st = new LinkedHashMap<>();
                ConfigurationSection ss = s.getConfigurationSection("stats_per_level");
                if (ss != null) for (String k : ss.getKeys(false)) if (Stats.isKnown(k)) st.put(k, ss.getDouble(k));
                talents.put(id, new Talent(id, s.getString("name", id), s.getString("description", ""), Util.icon(s.getString("icon"), Material.PAPER),
                        s.getString("branch", "utility"), s.getInt("row"), s.getInt("col"), Math.max(1, s.getInt("max_level", 5)), Math.max(1, s.getInt("cost", 1)), req, st));
            }
        }
        plugin.getLogger().info("[TalentManager] Loaded " + talents.size() + " talents.");
    }

    public Collection<Talent> all() {
        return talents.values();
    }

    public Collection<Branch> branches() {
        return branches.values();
    }

    public Talent get(String id) {
        return talents.get(id);
    }

    public double respecCost() {
        return respecCost;
    }

    public int rank(Player p, String id) {
        return plugin.getPlayerStore().get(p).talents.getOrDefault(id, 0);
    }

    public int totalPoints(Player p) {
        PlayerRecord r = plugin.getPlayerStore().get(p);
        var lm = plugin.getClassLevelManager();
        int levels = 0;
        for (long xp : r.classXp.values()) levels += lm.levelForXp(xp) - 1;
        for (long xp : r.subClassXp.values()) levels += lm.levelForXp(xp) - 1;
        int prestige = plugin.getPrestigeManager().total(p);
        return levels / levelsPerPoint + prestige * perPrestige + r.achievements.size() / achPerPoint;
    }

    public int spentPoints(Player p) {
        int spent = 0;
        for (Map.Entry<String, Integer> e : plugin.getPlayerStore().get(p).talents.entrySet()) {
            Talent t = talents.get(e.getKey());
            if (t != null) spent += t.cost() * e.getValue();
        }
        return spent;
    }

    public int available(Player p) {
        return totalPoints(p) - spentPoints(p);
    }

    /** Reason the talent cannot be raised, or null. */
    public String blocker(Player p, Talent t) {
        int cur = rank(p, t.id());
        if (cur >= t.max()) return "Ya está al máximo.";
        for (Map.Entry<String, Integer> e : t.requires().entrySet()) {
            if (rank(p, e.getKey()) < e.getValue()) {
                Talent r = talents.get(e.getKey());
                return "Requiere " + (r == null ? e.getKey() : r.name()) + " nivel " + e.getValue() + ".";
            }
        }
        if (available(p) < t.cost()) return "Necesitas " + t.cost() + " punto(s) de talento.";
        return null;
    }

    public boolean upgrade(Player p, Talent t) {
        String why = blocker(p, t);
        if (why != null) {
            p.sendMessage("§c" + why);
            return false;
        }
        plugin.getPlayerStore().get(p).talents.merge(t.id(), 1, Integer::sum);
        plugin.getPlayerStore().markDirty();
        p.playSound(p.getLocation(), org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.3f);
        return true;
    }

    public void respec(Player p) {
        plugin.getPlayerStore().get(p).talents.clear();
        plugin.getPlayerStore().markDirty();
    }

    private Map<String, Double> bonus(Player p) {
        Map<String, Double> out = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> e : plugin.getPlayerStore().get(p).talents.entrySet()) {
            Talent t = talents.get(e.getKey());
            if (t == null) continue;
            t.stats().forEach((k, v) -> Stats.add(out, k, v * e.getValue()));
        }
        return out;
    }
}
