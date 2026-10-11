package com.arclinker.rpglevelitems;

import com.arclinker.rpglevelitems.rank.PlayerRecord;
import com.arclinker.rpglevelitems.rank.RankCategory;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Player jobs, defined in {@code data/jobs.yml}.
 * A player can hold two global jobs (slots 1 and 2) plus an optional class job.
 * Active jobs earn job XP from activities (see ActivityListener); every XP point
 * is also a ranking point of the JOB category.
 */
public class JobManager {
    final RPGLevelTier plugin;
    private final Map<String, JobInfo> jobs = new LinkedHashMap<>();

    public JobManager(RPGLevelTier plugin) {
        this.plugin = plugin;
        loadJobs();
    }

    private void loadJobs() {
        YamlConfiguration cfg = DataFiles.loadMerged(plugin, "jobs");
        ConfigurationSection root = cfg.getConfigurationSection("jobs");
        if (root == null) {
            plugin.getLogger().warning("[JobManager] No jobs found in data/jobs/.");
            return;
        }
        for (String id : root.getKeys(false)) {
            ConfigurationSection s = root.getConfigurationSection(id);
            if (s == null) continue;
            String key = id.toLowerCase(Locale.ROOT);
            JobInfo info = new JobInfo(key, s.getString("name", id), s.getString("description", ""),
                    s.getString("bonus", ""), Util.icon(s.getString("icon"), Material.IRON_PICKAXE),
                    s.getBoolean("global", true));
            ConfigurationSection perks = s.getConfigurationSection("perks");
            if (perks != null) {
                for (String lvl : perks.getKeys(false)) {
                    ConfigurationSection st = perks.getConfigurationSection(lvl + ".stats");
                    if (st == null) continue;
                    Map<String, Double> m = new LinkedHashMap<>();
                    for (String k : st.getKeys(false)) if (com.arclinker.rpglevelitems.stats.Stats.isKnown(k)) m.put(k, st.getDouble(k));
                    try { info.perks.put(Integer.parseInt(lvl), m); } catch (NumberFormatException ignored) { }
                }
            }
            jobs.put(key, info);
        }
        plugin.getLogger().info("[JobManager] Loaded " + jobs.size() + " jobs.");
    }

    /** Assigns the class-specific job (a job with the same id as the class, if one exists). */
    public void assignClassJob(Player player, String classId) {
        PlayerRecord r = plugin.getPlayerStore().get(player);
        JobInfo job = getJobInfo(classId);
        r.jobClass = job != null ? job.id : "";
        plugin.getPlayerStore().markDirty();
    }

    /** Sets a global job in slot 1 or 2. Returns true on success. */
    public boolean setGlobalJob(Player player, int slot, String jobId) {
        if (slot != 1 && slot != 2) {
            player.sendMessage("§cLa ranura debe ser 1 o 2.");
            return false;
        }
        JobInfo job = getJobInfo(jobId);
        if (job == null) {
            player.sendMessage("§cTrabajo no encontrado: " + jobId);
            return false;
        }
        if (!job.global) {
            player.sendMessage("§cEse trabajo no se puede elegir como trabajo global.");
            return false;
        }
        PlayerRecord r = plugin.getPlayerStore().get(player);
        String other = slot == 1 ? r.job2 : r.job1;
        if (job.id.equalsIgnoreCase(other)) {
            player.sendMessage("§cYa tienes ese trabajo en la otra ranura.");
            return false;
        }
        if (slot == 1) r.job1 = job.id; else r.job2 = job.id;
        plugin.getPlayerStore().markDirty();
        player.sendMessage("§aHas seleccionado el trabajo §f" + job.name + "§a en la ranura " + slot + ".");
        return true;
    }

    public void clearGlobalJob(Player player, int slot) {
        PlayerRecord r = plugin.getPlayerStore().get(player);
        if (slot == 1) r.job1 = ""; else if (slot == 2) r.job2 = "";
        else {
            player.sendMessage("§cLa ranura debe ser 1 o 2.");
            return;
        }
        plugin.getPlayerStore().markDirty();
        player.sendMessage("§eRanura " + slot + " vaciada.");
    }

    public String getClassJob(Player player) {
        return plugin.getPlayerStore().get(player).jobClass;
    }

    public String getGlobalJob(Player player, int slot) {
        PlayerRecord r = plugin.getPlayerStore().get(player);
        return slot == 1 ? r.job1 : slot == 2 ? r.job2 : "";
    }

    /** Active job ids of the player (class job + global jobs). */
    public List<String> getActiveJobs(Player player) {
        PlayerRecord r = plugin.getPlayerStore().get(player);
        List<String> out = new ArrayList<>();
        for (String id : new String[]{r.jobClass, r.job1, r.job2}) {
            if (!id.isEmpty() && !out.contains(id)) out.add(id);
        }
        return out;
    }

    public boolean hasJob(Player player, String jobId) {
        for (String id : getActiveJobs(player)) if (id.equalsIgnoreCase(jobId)) return true;
        return false;
    }

    public JobInfo getJobInfo(String id) {
        return id == null ? null : jobs.get(id.toLowerCase(Locale.ROOT));
    }

    public Set<String> getJobIds() {
        return jobs.keySet();
    }

    public Map<String, JobInfo> getJobMap() {
        return jobs;
    }

    /** Convenience: sets a job in slot 1. */
    public void setJob(Player player, String jobId) {
        setGlobalJob(player, 1, jobId);
    }

    // ---- job XP / level (per job) -------------------------------------

    public int getJobXP(Player player, String jobId) {
        return plugin.getPlayerStore().get(player).jobXp.getOrDefault(jobId.toLowerCase(Locale.ROOT), 0);
    }

    /** Level L needs 100*(L-1)^2 XP. */
    public static int levelForXp(int xp) {
        return (int) Math.floor(Math.sqrt(xp / 100.0)) + 1;
    }

    public int getJobLevel(Player player, String jobId) {
        return levelForXp(getJobXP(player, jobId));
    }

    /** XP missing for the next level of the job. */
    public int getJobProgressToNextLevel(Player player, String jobId) {
        int level = getJobLevel(player, jobId);
        return 100 * level * level - getJobXP(player, jobId);
    }

    /**
     * Adds XP to a job the player currently holds. Also grants JOB ranking points.
     * @return true if the job leveled up
     */
    public boolean addJobXP(Player player, String jobId, int amount) {
        if (amount <= 0 || !hasJob(player, jobId)) return false;
        JobInfo job = getJobInfo(jobId);
        if (job == null) return false;
        PlayerRecord r = plugin.getPlayerStore().get(player);
        int before = levelForXp(r.jobXp.getOrDefault(job.id, 0));
        int xp = r.jobXp.merge(job.id, amount, Integer::sum);
        plugin.getPlayerStore().markDirty();
        plugin.getRankManager().addPoints(player, RankCategory.JOB, amount);
        double rate = plugin.getConfig().getDouble("jobs.coins_per_xp", 0.5);
        if (rate > 0) plugin.getEconomy().deposit(player, amount * rate); // job income (replaces paid-jobs plugins)
        int after = levelForXp(xp);
        if (after > before) {
            player.sendMessage("§a¡" + job.name + " subió al nivel §e" + after + "§a!");
            if (plugin.getJobAbilities() != null) plugin.getJobAbilities().check(player);
            JobInfo ji = job;
            if (ji.perks.containsKey(after)) {
                StringBuilder sb = new StringBuilder("§d★ Ventaja de trabajo: ");
                ji.perks.get(after).forEach((k, v) -> sb.append(com.arclinker.rpglevelitems.stats.Stats.line(k, v)).append(' '));
                player.sendMessage(sb.toString());
            }
            return true;
        }
        return false;
    }

    /** Registers the job perks as a stat provider (called once the StatEngine exists). */
    public void registerPerks() {
        plugin.getStatEngine().addProvider(p -> {
            Map<String, Double> out = new LinkedHashMap<>();
            for (String id : getActiveJobs(p)) {
                JobInfo job = getJobInfo(id);
                if (job == null) continue;
                int lvl = getJobLevel(p, id);
                for (Map.Entry<Integer, Map<String, Double>> e : job.perks.entrySet()) {
                    if (lvl >= e.getKey()) com.arclinker.rpglevelitems.stats.Stats.addAll(out, e.getValue());
                }
            }
            return out;
        });
    }

    public static class JobInfo {
        public final TreeMap<Integer, Map<String, Double>> perks = new TreeMap<>();
        public final String id;
        public final String name;
        public final String description;
        public final String bonus;
        public final Material icon;
        public final boolean global;

        public JobInfo(String id, String name, String description, String bonus, Material icon, boolean global) {
            this.id = id;
            this.name = name;
            this.description = description;
            this.bonus = bonus;
            this.icon = icon;
            this.global = global;
        }
    }
}
