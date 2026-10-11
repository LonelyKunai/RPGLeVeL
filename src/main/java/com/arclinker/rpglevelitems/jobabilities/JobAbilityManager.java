package com.arclinker.rpglevelitems.jobabilities;

import com.arclinker.rpglevelitems.DataFiles;
import com.arclinker.rpglevelitems.JobManager;
import com.arclinker.rpglevelitems.RPGLevelTier;
import com.arclinker.rpglevelitems.Util;
import com.arclinker.rpglevelitems.rank.PlayerRecord;
import com.arclinker.rpglevelitems.rank.Rank;
import com.arclinker.rpglevelitems.rank.RankCategory;
import com.arclinker.rpglevelitems.stats.Stats;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Job abilities (data/job_abilities/): passive perks / procs of each job (AuraSkills-style) that are unlocked
 * - by job level ({@code unlock.job_level}),
 * - by the player's permanent JOB rank ({@code unlock.rank}),
 * - or at random as a reward for quests, dungeons and bosses ({@code unlock.random}).
 * They only work while their job is equipped in a job slot. The ability level grows with the job level.
 */
public final class JobAbilityManager {
    public record Def(String id, String job, String name, String description, Material icon, String type, int maxLevel, int levelStep,
                      int unlockLevel, Rank unlockRank, boolean random, Map<String, Object> params, Map<String, Double> stats) {}

    private final RPGLevelTier plugin;
    private final Map<String, Def> defs = new LinkedHashMap<>();
    private final Map<String, Double> randomChance = new HashMap<>();

    public JobAbilityManager(RPGLevelTier plugin) {
        this.plugin = plugin;
        reload();
        plugin.getStatEngine().addProvider(this::statBonus);
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) check(p);
        }, 200L, 200L);
    }

    public void reload() {
        defs.clear();
        randomChance.clear();
        YamlConfiguration cfg = DataFiles.loadMerged(plugin, "job_abilities");
        ConfigurationSection rc = cfg.getConfigurationSection("settings.random_chance");
        if (rc != null) for (String k : rc.getKeys(false)) randomChance.put(k, rc.getDouble(k));
        ConfigurationSection root = cfg.getConfigurationSection("abilities");
        if (root != null) {
            for (String id : root.getKeys(false)) {
                ConfigurationSection s = root.getConfigurationSection(id);
                if (s == null) continue;
                Map<String, Object> params = new LinkedHashMap<>();
                ConfigurationSection ps = s.getConfigurationSection("params");
                if (ps != null) params.putAll(ps.getValues(false));
                Map<String, Double> stats = new LinkedHashMap<>();
                ConfigurationSection ss = s.getConfigurationSection("stats_per_level");
                if (ss != null) for (String k : ss.getKeys(false)) if (Stats.isKnown(k)) stats.put(k, ss.getDouble(k));
                Rank rank = s.contains("unlock.rank") ? Rank.parse(s.getString("unlock.rank")) : null;
                defs.put(id, new Def(id, s.getString("job", "").toLowerCase(Locale.ROOT), s.getString("name", id), s.getString("description", ""),
                        Util.icon(s.getString("icon"), Material.BOOK), s.getString("type", "stat"), Math.max(1, s.getInt("max_level", 5)),
                        Math.max(1, s.getInt("level_step", cfg.getInt("settings.level_step_default", 10))),
                        s.getInt("unlock.job_level", 0), rank, s.getBoolean("unlock.random", false), params, stats));
            }
        }
        plugin.getLogger().info("[JobAbilityManager] Loaded " + defs.size() + " job abilities.");
    }

    public Collection<Def> all() {
        return defs.values();
    }

    public Def get(String id) {
        return id == null ? null : defs.get(id);
    }

    public List<Def> ofJob(String job) {
        List<Def> out = new ArrayList<>();
        for (Def d : defs.values()) if (d.job().equalsIgnoreCase(job)) out.add(d);
        return out;
    }

    // ---- state -----------------------------------------------------------------------------------

    public boolean has(Player p, String id) {
        return plugin.getPlayerStore().get(p).jobAbilities.contains(id);
    }

    public int jobLevel(PlayerRecord r, String job) {
        return JobManager.levelForXp(r.jobXp.getOrDefault(job, 0));
    }

    public int level(Player p, Def d) {
        int jl = jobLevel(plugin.getPlayerStore().get(p), d.job());
        return Math.min(d.maxLevel(), 1 + Math.max(0, jl - 1) / d.levelStep());
    }

    /** Unlocked abilities of the given type whose job is currently equipped, with their level. */
    public Map<Def, Integer> active(Player p, String type) {
        Map<Def, Integer> out = new LinkedHashMap<>();
        PlayerRecord r = plugin.getPlayerStore().get(p);
        if (r.jobAbilities.isEmpty()) return out;
        for (String id : r.jobAbilities) {
            Def d = defs.get(id);
            if (d == null || !d.type().equals(type) || !plugin.getJobManager().hasJob(p, d.job())) continue;
            out.put(d, Math.min(d.maxLevel(), 1 + Math.max(0, jobLevel(r, d.job()) - 1) / d.levelStep()));
        }
        return out;
    }

    public static double param(Def d, int level, String key, double def) {
        Object base = d.params().get(key);
        double v = base instanceof Number n ? n.doubleValue() : def;
        Object per = d.params().get(key + "_per_level");
        if (per instanceof Number n) v += n.doubleValue() * (Math.max(1, level) - 1);
        return v;
    }

    /** Sum of a parameter over every active ability of the type (e.g. thrifty_forge chance). */
    public double sum(Player p, String type, String key) {
        double total = 0;
        for (Map.Entry<Def, Integer> e : active(p, type).entrySet()) total += param(e.getKey(), e.getValue(), key, 0);
        return total;
    }

    /** True with probability (sum of "chance" of the active abilities of the type) %. */
    public boolean roll(Player p, String type) {
        double c = sum(p, type, "chance");
        return c > 0 && ThreadLocalRandom.current().nextDouble() * 100 < Math.min(95, c);
    }

    private Map<String, Double> statBonus(Player p) {
        Map<String, Double> out = new LinkedHashMap<>();
        for (Map.Entry<Def, Integer> e : active(p, "stat").entrySet()) {
            final int lvl = e.getValue();
            e.getKey().stats().forEach((k, v) -> Stats.add(out, k, v * lvl));
        }
        return out;
    }

    // ---- unlocking ---------------------------------------------------------------------------------

    /** Why an automatic unlock condition is met (job level / rank), or null. */
    private boolean conditionMet(Player p, PlayerRecord r, Def d) {
        if (d.random()) return false;
        if (d.unlockLevel() > 0 && jobLevel(r, d.job()) >= d.unlockLevel()) return true;
        if (d.unlockRank() != null && jobLevel(r, d.job()) >= 2) {
            Rank have = plugin.getRankManager().getRank(p.getUniqueId(), RankCategory.JOB);
            return have.ordinal() >= d.unlockRank().ordinal();
        }
        return false;
    }

    /** Unlocks everything whose level / rank condition is now met. */
    public void check(Player p) {
        PlayerRecord r = plugin.getPlayerStore().get(p);
        for (Def d : defs.values()) {
            if (r.jobAbilities.contains(d.id()) || !conditionMet(p, r, d)) continue;
            unlock(p, d, d.unlockRank() != null && d.unlockLevel() == 0 ? "rango de trabajo " + d.unlockRank().label : "nivel " + d.unlockLevel() + " de " + d.job());
        }
    }

    public boolean unlock(Player p, Def d, String reason) {
        PlayerRecord r = plugin.getPlayerStore().get(p);
        if (!r.jobAbilities.add(d.id())) return false;
        plugin.getPlayerStore().markDirty();
        p.sendMessage("§d★ Habilidad de oficio desbloqueada: §f" + d.name() + " §7(" + reason + ")");
        p.sendMessage("  §8" + d.description() + (plugin.getJobManager().hasJob(p, d.job()) ? "" : " §c(equipa el trabajo " + d.job() + " para usarla)"));
        p.sendTitle("§d★ " + d.name(), "§7Habilidad de oficio", 5, 40, 10);
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.5f);
        return true;
    }

    public boolean remove(Player p, String id) {
        boolean ok = plugin.getPlayerStore().get(p).jobAbilities.remove(id);
        if (ok) plugin.getPlayerStore().markDirty();
        return ok;
    }

    /** Grants a random not-yet-unlocked "random" ability (prefers the player's equipped jobs). Returns it or null. */
    public Def grantRandom(Player p, String reason) {
        PlayerRecord r = plugin.getPlayerStore().get(p);
        List<Def> pool = new ArrayList<>(), preferred = new ArrayList<>();
        for (Def d : defs.values()) {
            if (!d.random() || r.jobAbilities.contains(d.id())) continue;
            pool.add(d);
            if (plugin.getJobManager().hasJob(p, d.job())) preferred.add(d);
        }
        if (pool.isEmpty()) return null;
        List<Def> from = !preferred.isEmpty() && ThreadLocalRandom.current().nextDouble() < 0.8 ? preferred : pool;
        Def d = from.get(ThreadLocalRandom.current().nextInt(from.size()));
        unlock(p, d, reason);
        return d;
    }

    /** Quest / dungeon / boss reward hook: source = daily | weekly | chain | dungeon | boss. */
    public void questReward(Player p, String source) {
        double chance = randomChance.getOrDefault(source, 0.0);
        if (chance > 0 && ThreadLocalRandom.current().nextDouble() < chance) grantRandom(p, "recompensa: " + source);
    }

    // ---- info ------------------------------------------------------------------------------------------

    public String condition(Def d) {
        if (d.random()) return "al azar como recompensa de misiones, mazmorras o jefes";
        List<String> c = new ArrayList<>();
        if (d.unlockLevel() > 0) c.add("nivel " + d.unlockLevel() + " del trabajo");
        if (d.unlockRank() != null) c.add("rango de trabajo " + d.unlockRank().label);
        return String.join(" o ", c);
    }

    /** Lines for /jobs abilities and the job menu. */
    public List<String> describe(Player p, String job) {
        List<String> out = new ArrayList<>();
        PlayerRecord r = plugin.getPlayerStore().get(p);
        for (Def d : ofJob(job)) {
            boolean on = r.jobAbilities.contains(d.id());
            out.add((on ? "§a✔ " : "§8✘ ") + (on ? "§f" : "§7") + d.name() + (on ? " §7nv " + level(p, d) + "/" + d.maxLevel() : " §8(" + condition(d) + ")"));
        }
        return out;
    }
}
