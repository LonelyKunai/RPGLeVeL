package com.arclinker.rpglevelitems.achievements;

import com.arclinker.rpglevelitems.DataFiles;
import com.arclinker.rpglevelitems.RPGLevelTier;
import com.arclinker.rpglevelitems.rank.PlayerRecord;
import com.arclinker.rpglevelitems.stats.Stats;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.util.*;

/**
 * Achievements (data/achievements/): counters increase from gameplay hooks; reaching a goal unlocks the
 * achievement and gives its reward: coins and/or a title (titles can give small stats while active).
 */
public final class AchievementManager {
    public record Achievement(String id, String name, String description, String counter, long goal, long coins, String title) {}
    public record Title(String id, String display, Map<String, Double> stats) {}

    private final RPGLevelTier plugin;
    private final Map<String, Achievement> achievements = new LinkedHashMap<>();
    private final Map<String, Title> titles = new LinkedHashMap<>();
    private final Map<String, List<Achievement>> byCounter = new HashMap<>();

    public AchievementManager(RPGLevelTier plugin) {
        this.plugin = plugin;
        reload();
        plugin.getStatEngine().addProvider(this::titleStats);
    }

    public void reload() {
        achievements.clear();
        titles.clear();
        byCounter.clear();
        YamlConfiguration cfg = DataFiles.loadMerged(plugin, "achievements");
        ConfigurationSection t = cfg.getConfigurationSection("titles");
        if (t != null) {
            for (String id : t.getKeys(false)) {
                ConfigurationSection s = t.getConfigurationSection(id);
                if (s == null) continue;
                Map<String, Double> st = new LinkedHashMap<>();
                ConfigurationSection ss = s.getConfigurationSection("stats");
                if (ss != null) for (String k : ss.getKeys(false)) if (Stats.isKnown(k)) st.put(k, ss.getDouble(k));
                titles.put(id, new Title(id, s.getString("display", id), st));
            }
        }
        ConfigurationSection a = cfg.getConfigurationSection("achievements");
        if (a != null) {
            for (String id : a.getKeys(false)) {
                ConfigurationSection s = a.getConfigurationSection(id);
                if (s == null) continue;
                Achievement ac = new Achievement(id, s.getString("name", id), s.getString("description", ""), s.getString("counter", ""),
                        s.getLong("goal", 1), s.getLong("reward.coins", 0), s.getString("reward.title", ""));
                achievements.put(id, ac);
                byCounter.computeIfAbsent(ac.counter(), k -> new ArrayList<>()).add(ac);
            }
        }
        plugin.getLogger().info("[AchievementManager] Loaded " + achievements.size() + " achievements, " + titles.size() + " titles.");
    }

    public Collection<Achievement> all() {
        return achievements.values();
    }

    public Title title(String id) {
        return titles.get(id);
    }

    public Collection<Title> allTitles() {
        return titles.values();
    }

    public long counter(Player p, String counter) {
        return plugin.getPlayerStore().get(p).counters.getOrDefault(counter, 0L);
    }

    /** Increments a counter and checks its achievements. */
    public void add(Player p, String counter, long amount) {
        if (amount <= 0) return;
        PlayerRecord r = plugin.getPlayerStore().get(p);
        long now = r.counters.merge(counter, amount, Long::sum);
        plugin.getPlayerStore().markDirty();
        check(p, r, counter, now);
    }

    /** Raises a counter to at least {@code value} (for "reach level N" style achievements). */
    public void max(Player p, String counter, long value) {
        PlayerRecord r = plugin.getPlayerStore().get(p);
        if (r.counters.getOrDefault(counter, 0L) >= value) return;
        r.counters.put(counter, value);
        plugin.getPlayerStore().markDirty();
        check(p, r, counter, value);
    }

    private void check(Player p, PlayerRecord r, String counter, long value) {
        List<Achievement> list = byCounter.get(counter);
        if (list == null) return;
        for (Achievement a : list) {
            if (value < a.goal() || r.achievements.contains(a.id())) continue;
            r.achievements.add(a.id());
            Bukkit.getPluginManager().callEvent(new com.arclinker.rpglevelitems.api.events.AchievementUnlockEvent(p, a.id()));
            p.sendMessage(plugin.getLanguageManager().tr(p, "achievement.unlocked", a.name(), a.description()));
            p.sendTitle(plugin.getLanguageManager().tr(p, "achievement.title_header"), "§e" + a.name(), 5, 40, 10);
            p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
            if (a.coins() > 0) {
                plugin.getEconomy().deposit(p, a.coins());
                p.sendMessage("§7+§e" + a.coins() + " §7monedas");
            }
            Title t = titles.get(a.title());
            if (t != null && r.titles.add(t.id())) {
                p.sendMessage(plugin.getLanguageManager().tr(p, "achievement.title_new", t.display()));
            }
            plugin.getPlayerStore().markDirty();
        }
    }

    public String activeTitleDisplay(PlayerRecord r) {
        Title t = titles.get(r.activeTitle);
        return t == null || !r.titles.contains(t.id()) ? "" : t.display();
    }

    private Map<String, Double> titleStats(Player p) {
        PlayerRecord r = plugin.getPlayerStore().get(p);
        Title t = titles.get(r.activeTitle);
        return t == null || !r.titles.contains(t.id()) ? Map.of() : t.stats();
    }

    public boolean setTitle(Player p, String id) {
        PlayerRecord r = plugin.getPlayerStore().get(p);
        if (id == null || id.isEmpty()) {
            r.activeTitle = "";
        } else {
            if (!titles.containsKey(id) || !r.titles.contains(id)) return false;
            r.activeTitle = id;
        }
        plugin.getPlayerStore().markDirty();
        return true;
    }
}
