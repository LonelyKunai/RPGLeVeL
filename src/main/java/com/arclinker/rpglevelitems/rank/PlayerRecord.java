package com.arclinker.rpglevelitems.rank;

import org.bukkit.configuration.ConfigurationSection;

import java.util.*;

/** Persistent data of one player: class, subclass, jobs, quests and ranking points. */
public final class PlayerRecord {
    public final UUID uuid;
    public String name = "";

    public String classId = "";
    public String subclassId = "";
    public String jobClass = "";
    public String job1 = "";
    public String job2 = "";

    /** Ranking points per category (index = RankCategory ordinal). */
    public final long[] points = new long[RankCategory.values().length];
    /** Season ranking points per category (reset when the season changes). */
    public final long[] seasonPoints = new long[RankCategory.values().length];
    /** Season the {@link #seasonPoints} belong to (0 = never synced). */
    public int seasonNumber = 0;
    /** Final result of past seasons: "<season>.<category>" -> "<rank label>|<points>". */
    public final Map<String, String> seasonHistory = new LinkedHashMap<>();
    /** Console commands (season rewards) waiting for the player to be online. */
    public final List<String> pendingRewards = new ArrayList<>();
    /** XP per job id (lowercase). */
    public final Map<String, Integer> jobXp = new HashMap<>();

    /** Class / subclass XP per id (progress is kept when switching). */
    public final Map<String, Long> classXp = new HashMap<>();
    public final Map<String, Long> subClassXp = new HashMap<>();
    /** Unlocked abilities: ability id -> ability level. */
    public final Map<String, Integer> abilities = new HashMap<>();
    /** Available ability XP per tree ("class:<id>" / "sub:<id>"). */
    public final Map<String, Long> abilityXp = new HashMap<>();

    // ---- progression extras ----
    /** Prestige count per class id. */
    public final Map<String, Integer> prestige = new HashMap<>();
    /** Passive talents: talent id -> level. */
    public final Map<String, Integer> talents = new HashMap<>();
    /** Activity counters used by achievements (kills, mined, forged...). */
    public final Map<String, Long> counters = new HashMap<>();
    public final Set<String> achievements = new LinkedHashSet<>();
    public final Set<String> titles = new LinkedHashSet<>();
    public String activeTitle = "";
    /** Unlocked job abilities (ids from data/job_abilities). */
    public final Set<String> jobAbilities = new LinkedHashSet<>();
    /** Ability ids bound to the 4 hotbar slots ("" = empty). */
    public final String[] hotbar = {"", "", "", ""};
    public long coins = 0;
    public String guild = "";
    public String language = "";
    public long lastRespec = 0;
    public long lastClassChange = 0;
    /** Per-class memory (subclass + ability hotbar) restored when the player returns to that class. */
    public static final class Snapshot {
        public String subclass = "";
        public final String[] hotbar = {"", "", "", ""};
    }
    public final Map<String, Snapshot> snapshots = new HashMap<>();
    /** Dungeon id -> epoch ms when it can be run again. */
    public final Map<String, Long> dungeonCooldowns = new HashMap<>();
    /** Quest chains: chain id -> current step (index), and progress inside the step. */
    public final Map<String, Integer> chainStep = new HashMap<>();
    public final Map<String, Integer> chainProgress = new HashMap<>();
    public final Set<String> chainDone = new HashSet<>();
    /** Weekly quests (per ISO week id). */
    public String weeklyId = "";
    public final List<String> weeklyIds = new ArrayList<>();
    public final Map<String, Integer> weeklyProgress = new HashMap<>();
    public final Set<String> weeklyDone = new HashSet<>();

    public String questDate = "";
    public final List<String> questIds = new ArrayList<>();
    public final Map<String, Integer> questProgress = new HashMap<>();
    public final Set<String> questDone = new HashSet<>();

    public PlayerRecord(UUID uuid) {
        this.uuid = uuid;
    }

    public long getPoints(RankCategory c) {
        return points[c.ordinal()];
    }

    public long getSeasonPoints(RankCategory c) {
        return seasonPoints[c.ordinal()];
    }

    public void save(ConfigurationSection s) {
        s.set("name", name);
        s.set("class", classId);
        s.set("subclass", subclassId);
        s.set("job_class", jobClass);
        s.set("job_1", job1);
        s.set("job_2", job2);
        for (RankCategory c : RankCategory.values()) s.set("points." + c.id, points[c.ordinal()]);
        for (RankCategory c : RankCategory.values()) s.set("season_points." + c.id, seasonPoints[c.ordinal()]);
        s.set("season_number", seasonNumber);
        for (Map.Entry<String, String> e : seasonHistory.entrySet()) s.set("season_history." + e.getKey().replace('.', '|'), e.getValue());
        s.set("pending_rewards", new ArrayList<>(pendingRewards));
        for (Map.Entry<String, Integer> e : jobXp.entrySet()) s.set("job_xp." + e.getKey(), e.getValue());
        for (Map.Entry<String, Long> e : classXp.entrySet()) s.set("class_xp." + e.getKey(), e.getValue());
        for (Map.Entry<String, Long> e : subClassXp.entrySet()) s.set("subclass_xp." + e.getKey(), e.getValue());
        for (Map.Entry<String, Integer> e : abilities.entrySet()) s.set("abilities." + e.getKey(), e.getValue());
        for (Map.Entry<String, Long> e : abilityXp.entrySet()) s.set("ability_xp." + e.getKey().replace(':', '|'), e.getValue());
        for (Map.Entry<String, Integer> e : prestige.entrySet()) s.set("prestige." + e.getKey(), e.getValue());
        for (Map.Entry<String, Integer> e : talents.entrySet()) s.set("talents." + e.getKey(), e.getValue());
        for (Map.Entry<String, Long> e : counters.entrySet()) s.set("counters." + e.getKey(), e.getValue());
        s.set("achievements", new ArrayList<>(achievements));
        s.set("titles", new ArrayList<>(titles));
        s.set("active_title", activeTitle);
        s.set("job_abilities", new ArrayList<>(jobAbilities));
        s.set("hotbar", new ArrayList<>(Arrays.asList(hotbar)));
        s.set("coins", coins);
        s.set("guild", guild);
        s.set("language", language);
        s.set("last_respec", lastRespec);
        s.set("last_class_change", lastClassChange);
        for (Map.Entry<String, Snapshot> e : snapshots.entrySet()) {
            s.set("snapshots." + e.getKey() + ".subclass", e.getValue().subclass);
            s.set("snapshots." + e.getKey() + ".hotbar", new ArrayList<>(Arrays.asList(e.getValue().hotbar)));
        }
        for (Map.Entry<String, Long> e : dungeonCooldowns.entrySet()) s.set("dungeon_cd." + e.getKey(), e.getValue());
        for (Map.Entry<String, Integer> e : chainStep.entrySet()) s.set("chain.step." + e.getKey(), e.getValue());
        for (Map.Entry<String, Integer> e : chainProgress.entrySet()) s.set("chain.progress." + e.getKey(), e.getValue());
        s.set("chain.done", new ArrayList<>(chainDone));
        s.set("weekly.id", weeklyId);
        s.set("weekly.ids", new ArrayList<>(weeklyIds));
        for (Map.Entry<String, Integer> e : weeklyProgress.entrySet()) s.set("weekly.progress." + e.getKey(), e.getValue());
        s.set("weekly.done", new ArrayList<>(weeklyDone));
        s.set("quest.date", questDate);
        s.set("quest.ids", new ArrayList<>(questIds));
        for (Map.Entry<String, Integer> e : questProgress.entrySet()) s.set("quest.progress." + e.getKey(), e.getValue());
        s.set("quest.done", new ArrayList<>(questDone));
    }

    public static PlayerRecord load(UUID uuid, ConfigurationSection s) {
        PlayerRecord r = new PlayerRecord(uuid);
        r.name = s.getString("name", "");
        r.classId = s.getString("class", "");
        r.subclassId = s.getString("subclass", "");
        r.jobClass = s.getString("job_class", "");
        r.job1 = s.getString("job_1", "");
        r.job2 = s.getString("job_2", "");
        for (RankCategory c : RankCategory.values()) r.points[c.ordinal()] = s.getLong("points." + c.id, 0L);
        for (RankCategory c : RankCategory.values()) r.seasonPoints[c.ordinal()] = s.getLong("season_points." + c.id, 0L);
        r.seasonNumber = s.getInt("season_number", 0);
        ConfigurationSection sh = s.getConfigurationSection("season_history");
        if (sh != null) for (String k : sh.getKeys(false)) r.seasonHistory.put(k.replace('|', '.'), sh.getString(k, ""));
        r.pendingRewards.addAll(s.getStringList("pending_rewards"));
        ConfigurationSection jx = s.getConfigurationSection("job_xp");
        if (jx != null) for (String k : jx.getKeys(false)) r.jobXp.put(k, jx.getInt(k));
        ConfigurationSection cx = s.getConfigurationSection("class_xp");
        if (cx != null) for (String k : cx.getKeys(false)) r.classXp.put(k, cx.getLong(k));
        ConfigurationSection sx = s.getConfigurationSection("subclass_xp");
        if (sx != null) for (String k : sx.getKeys(false)) r.subClassXp.put(k, sx.getLong(k));
        ConfigurationSection ab = s.getConfigurationSection("abilities");
        if (ab != null) for (String k : ab.getKeys(false)) r.abilities.put(k, ab.getInt(k));
        ConfigurationSection ax = s.getConfigurationSection("ability_xp");
        if (ax != null) for (String k : ax.getKeys(false)) r.abilityXp.put(k.replace('|', ':'), ax.getLong(k));
        ConfigurationSection pr = s.getConfigurationSection("prestige");
        if (pr != null) for (String k : pr.getKeys(false)) r.prestige.put(k, pr.getInt(k));
        ConfigurationSection tl = s.getConfigurationSection("talents");
        if (tl != null) for (String k : tl.getKeys(false)) r.talents.put(k, tl.getInt(k));
        ConfigurationSection ct = s.getConfigurationSection("counters");
        if (ct != null) for (String k : ct.getKeys(false)) r.counters.put(k, ct.getLong(k));
        r.achievements.addAll(s.getStringList("achievements"));
        r.titles.addAll(s.getStringList("titles"));
        r.activeTitle = s.getString("active_title", "");
        r.jobAbilities.addAll(s.getStringList("job_abilities"));
        List<String> hb = s.getStringList("hotbar");
        for (int i = 0; i < r.hotbar.length && i < hb.size(); i++) r.hotbar[i] = hb.get(i) == null ? "" : hb.get(i);
        r.coins = s.getLong("coins", 0);
        r.guild = s.getString("guild", "");
        r.language = s.getString("language", "");
        r.lastRespec = s.getLong("last_respec", 0);
        r.lastClassChange = s.getLong("last_class_change", 0);
        ConfigurationSection sn = s.getConfigurationSection("snapshots");
        if (sn != null) {
            for (String k : sn.getKeys(false)) {
                Snapshot snap = new Snapshot();
                snap.subclass = sn.getString(k + ".subclass", "");
                List<String> hb2 = sn.getStringList(k + ".hotbar");
                for (int i = 0; i < snap.hotbar.length && i < hb2.size(); i++) snap.hotbar[i] = hb2.get(i) == null ? "" : hb2.get(i);
                r.snapshots.put(k, snap);
            }
        }
        ConfigurationSection dc = s.getConfigurationSection("dungeon_cd");
        if (dc != null) for (String k : dc.getKeys(false)) r.dungeonCooldowns.put(k, dc.getLong(k));
        ConfigurationSection cs = s.getConfigurationSection("chain.step");
        if (cs != null) for (String k : cs.getKeys(false)) r.chainStep.put(k, cs.getInt(k));
        ConfigurationSection cp = s.getConfigurationSection("chain.progress");
        if (cp != null) for (String k : cp.getKeys(false)) r.chainProgress.put(k, cp.getInt(k));
        r.chainDone.addAll(s.getStringList("chain.done"));
        r.weeklyId = s.getString("weekly.id", "");
        r.weeklyIds.addAll(s.getStringList("weekly.ids"));
        ConfigurationSection wp = s.getConfigurationSection("weekly.progress");
        if (wp != null) for (String k : wp.getKeys(false)) r.weeklyProgress.put(k, wp.getInt(k));
        r.weeklyDone.addAll(s.getStringList("weekly.done"));
        r.questDate = s.getString("quest.date", "");
        r.questIds.addAll(s.getStringList("quest.ids"));
        ConfigurationSection qp = s.getConfigurationSection("quest.progress");
        if (qp != null) for (String k : qp.getKeys(false)) r.questProgress.put(k, qp.getInt(k));
        r.questDone.addAll(s.getStringList("quest.done"));
        return r;
    }
}
