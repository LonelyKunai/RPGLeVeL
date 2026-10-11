package com.arclinker.rpglevelitems.rank;

import com.arclinker.rpglevelitems.RPGLevelTier;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Ranking system. Every category (class, subclass, job, quest) has two ladders:
 * <ul>
 *   <li>PERMANENT: F, E, D, C, B, A, S, S+, U. Never resets; thresholds are multiplied
 *       ({@code ranks.permanent_multiplier}) so they take longer to climb.</li>
 *   <li>SEASON: F-S1 ... U-S1 (the id carries the season number). Points reset each season
 *       (manual or every {@code ranks.season.duration_days}); the final rank is archived and
 *       rewarded with console commands from {@code ranks.season.rewards}.</li>
 * </ul>
 * Every point earned counts for both ladders.
 */
public final class RankManager {
    private static final long[] DEFAULT_THRESHOLDS = {0, 100, 300, 700, 1500, 3000, 6000, 12000, 25000};

    private final RPGLevelTier plugin;
    private final PlayerStore store;
    private final File seasonFile;
    private long[] seasonThresholds = DEFAULT_THRESHOLDS;
    private long[] permThresholds = DEFAULT_THRESHOLDS;

    private int season = 1;
    private long seasonStart = System.currentTimeMillis();
    private int durationDays = 30;
    private final List<String> seasonLog = new ArrayList<>();

    public RankManager(RPGLevelTier plugin, PlayerStore store) {
        this.plugin = plugin;
        this.store = store;
        this.seasonFile = new File(plugin.getDataFolder(), "seasons.yml");
        loadSeasonFile();
        reload();
    }

    // ---- config ------------------------------------------------------------------

    public void reload() {
        List<Long> list = plugin.getConfig().getLongList("ranks.thresholds");
        if (list.size() == Rank.values().length) {
            seasonThresholds = list.stream().mapToLong(Long::longValue).toArray();
        } else {
            seasonThresholds = DEFAULT_THRESHOLDS;
            if (!list.isEmpty()) plugin.getLogger().warning("[RankManager] ranks.thresholds needs " + Rank.values().length + " values; using defaults.");
        }
        List<Long> perm = plugin.getConfig().getLongList("ranks.permanent.thresholds");
        if (perm.size() == Rank.values().length) {
            permThresholds = perm.stream().mapToLong(Long::longValue).toArray();
        } else {
            double mult = Math.max(1.0, plugin.getConfig().getDouble("ranks.permanent_multiplier", 5.0));
            permThresholds = new long[seasonThresholds.length];
            for (int i = 0; i < permThresholds.length; i++) permThresholds[i] = Math.round(seasonThresholds[i] * mult);
        }
        durationDays = Math.max(0, plugin.getConfig().getInt("ranks.season.duration_days", 30));
    }

    private long[] th(RankScope scope) {
        return scope == RankScope.PERMANENT ? permThresholds : seasonThresholds;
    }

    // ---- season state --------------------------------------------------------------

    private void loadSeasonFile() {
        if (!seasonFile.exists()) {
            saveSeasonFile();
            return;
        }
        YamlConfiguration y = YamlConfiguration.loadConfiguration(seasonFile);
        season = Math.max(1, y.getInt("season", 1));
        seasonStart = y.getLong("start", System.currentTimeMillis());
        seasonLog.addAll(y.getStringList("log"));
    }

    private void saveSeasonFile() {
        YamlConfiguration y = new YamlConfiguration();
        y.set("season", season);
        y.set("start", seasonStart);
        y.set("log", seasonLog);
        try {
            plugin.getDataFolder().mkdirs();
            y.save(seasonFile);
        } catch (IOException e) {
            plugin.getLogger().severe("[RankManager] Could not save seasons.yml: " + e.getMessage());
        }
    }

    public int season() {
        return season;
    }

    public String seasonTag() {
        return "S" + season;
    }

    /** Epoch millis when the season ends, or 0 if seasons are manual. */
    public long seasonEnd() {
        return durationDays <= 0 ? 0 : seasonStart + durationDays * 86_400_000L;
    }

    public long seasonStart() {
        return seasonStart;
    }

    public List<String> seasonLog() {
        return seasonLog;
    }

    /** "12d 3h" style time left, or "manual". */
    public String timeLeft() {
        long end = seasonEnd();
        if (end == 0) return "manual";
        long ms = Math.max(0, end - System.currentTimeMillis());
        long d = ms / 86_400_000L, h = (ms / 3_600_000L) % 24, m = (ms / 60_000L) % 60;
        return d > 0 ? d + "d " + h + "h" : h + "h " + m + "m";
    }

    /** Called every minute: ends the season when its time is up. */
    public void tick() {
        long end = seasonEnd();
        if (end > 0 && System.currentTimeMillis() >= end) endSeason();
    }

    /** Archives the current season for everyone, rewards them and starts the next one. */
    public synchronized void endSeason() {
        int finished = season;
        season++;
        seasonStart = System.currentTimeMillis();
        seasonLog.add("S" + finished + " terminó " + new Date());
        saveSeasonFile();
        for (PlayerRecord r : store.all()) rollover(r);
        store.markDirty();
        store.save();
        Bukkit.getPluginManager().callEvent(new com.arclinker.rpglevelitems.api.events.SeasonEndEvent(finished));
        Bukkit.broadcastMessage("§6§l¡La temporada " + finished + " terminó! §eComienza la temporada " + season + ". Tus rangos de temporada se reiniciaron (los permanentes se mantienen).");
        for (Player p : Bukkit.getOnlinePlayers()) flushRewards(p);
    }

    /** Brings a record to the current season: archives the old one and queues rewards. */
    public void sync(PlayerRecord r) {
        if (r.seasonNumber != season) rollover(r);
    }

    private void rollover(PlayerRecord r) {
        int old = r.seasonNumber;
        if (old > 0 && old != season) {
            for (RankCategory c : RankCategory.values()) {
                long pts = r.seasonPoints[c.ordinal()];
                if (pts <= 0) continue;
                Rank rank = Rank.fromPoints(pts, seasonThresholds);
                r.seasonHistory.put(old + "." + c.id, rank.label + "-S" + old + "|" + pts);
                for (String cmd : plugin.getConfig().getStringList("ranks.season.rewards." + rank.label)) {
                    r.pendingRewards.add(cmd.replace("%category%", c.id).replace("%rank%", rank.label + "-S" + old)
                            .replace("%season%", String.valueOf(old)).replace("%uuid%", r.uuid.toString()));
                }
            }
        }
        Arrays.fill(r.seasonPoints, 0L);
        r.seasonNumber = season;
        store.markDirty();
    }

    /** Runs pending season rewards for an online player. */
    public void flushRewards(Player p) {
        PlayerRecord r = store.get(p);
        if (r.pendingRewards.isEmpty()) return;
        List<String> cmds = new ArrayList<>(r.pendingRewards);
        r.pendingRewards.clear();
        store.markDirty();
        for (String cmd : cmds) {
            try {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd.replace("%player%", p.getName()));
            } catch (Exception e) {
                plugin.getLogger().warning("[RankManager] Reward command failed: " + cmd + " (" + e.getMessage() + ")");
            }
        }
        p.sendMessage("§6Recibiste las recompensas de temporada.");
    }

    // ---- labels --------------------------------------------------------------------

    /** Rank id: "S+" for permanent, "S+-S1" for season 1. */
    public String label(Rank rank, RankScope scope) {
        return scope == RankScope.PERMANENT ? rank.label : rank.label + "-" + seasonTag();
    }

    /** Colored rank id, e.g. "§6S-S1". */
    public String display(Rank rank, RankScope scope) {
        return rank.color + label(rank, scope) + "§r";
    }

    public String fullDisplay(Rank rank, RankScope scope) {
        return display(rank, scope) + " §7(" + rank.title + ")";
    }

    // ---- queries -------------------------------------------------------------------

    public long threshold(Rank rank, RankScope scope) {
        return th(scope)[rank.ordinal()];
    }

    public long getPoints(UUID id, RankCategory c, RankScope scope) {
        PlayerRecord r = store.get(id);
        if (scope == RankScope.SEASON) sync(r);
        return scope == RankScope.PERMANENT ? r.getPoints(c) : r.getSeasonPoints(c);
    }

    public long getPoints(UUID id, RankCategory c) {
        return getPoints(id, c, RankScope.PERMANENT);
    }

    public Rank getRank(long points, RankScope scope) {
        return Rank.fromPoints(points, th(scope));
    }

    public Rank getRank(UUID id, RankCategory c, RankScope scope) {
        return getRank(getPoints(id, c, scope), scope);
    }

    /** Permanent rank (default scope). */
    public Rank getRank(UUID id, RankCategory c) {
        return getRank(id, c, RankScope.PERMANENT);
    }

    public Rank getRank(long permanentPoints) {
        return getRank(permanentPoints, RankScope.PERMANENT);
    }

    /** Adds points to both ladders and announces rank ups. */
    public void addPoints(Player player, RankCategory c, long amount) {
        if (amount <= 0) return;
        PlayerRecord r = store.get(player);
        sync(r);
        int i = c.ordinal();
        Rank permBefore = Rank.fromPoints(r.points[i], permThresholds);
        Rank seasBefore = Rank.fromPoints(r.seasonPoints[i], seasonThresholds);
        r.points[i] += amount;
        r.seasonPoints[i] += amount;
        store.markDirty();
        announce(player, c, permBefore, Rank.fromPoints(r.points[i], permThresholds), RankScope.PERMANENT);
        announce(player, c, seasBefore, Rank.fromPoints(r.seasonPoints[i], seasonThresholds), RankScope.SEASON);
    }

    private void announce(Player player, RankCategory c, Rank before, Rank after, RankScope scope) {
        if (after.ordinal() <= before.ordinal()) return;
        if (scope == RankScope.PERMANENT) {
            // console commands per permanent rank reached (e.g. LuckPerms groups / prefixes): ranks.permanent_rewards.<RANK>
            for (int i = before.ordinal() + 1; i <= after.ordinal(); i++) {
                Rank reached = Rank.values()[i];
                for (String cmd : plugin.getConfig().getStringList("ranks.permanent_rewards." + reached.label)) {
                    try {
                        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd.replace("%player%", player.getName())
                                .replace("%category%", c.id).replace("%rank%", reached.label));
                    } catch (Exception e) {
                        plugin.getLogger().warning("[RankManager] Rank reward failed: " + cmd + " (" + e.getMessage() + ")");
                    }
                }
            }
        }
        org.bukkit.Bukkit.getPluginManager().callEvent(new com.arclinker.rpglevelitems.api.events.RankChangeEvent(player, c.id, scope.id, label(before, scope), label(after, scope)));
        if (scope == RankScope.PERMANENT && c == RankCategory.CLASS && plugin.getAchievements() != null) plugin.getAchievements().max(player, "rank_class", after.ordinal());
        var lang = plugin.getLanguageManager();
        player.sendMessage(lang.tr(player, "rank.up", lang.tr(player, scope == RankScope.PERMANENT ? "rank.permanent" : "rank.season"), c.coloredLabel(), fullDisplay(after, scope)));
        player.sendTitle("§6¡RANGO " + display(after, scope) + "§6!", c.coloredLabel() + " §7- " + scope.label, 10, 50, 10);
    }

    /** Sets points directly (admin). */
    public void setPoints(UUID id, RankCategory c, RankScope scope, long points) {
        PlayerRecord r = store.get(id);
        sync(r);
        if (scope == RankScope.PERMANENT) r.points[c.ordinal()] = Math.max(0, points);
        else r.seasonPoints[c.ordinal()] = Math.max(0, points);
        store.markDirty();
    }

    /** Points still needed for the next rank, or -1 at max rank. */
    public long pointsToNext(UUID id, RankCategory c, RankScope scope) {
        Rank rank = getRank(id, c, scope);
        Rank next = rank.next();
        return next == null ? -1 : threshold(next, scope) - getPoints(id, c, scope);
    }

    /** Progress bar toward the next rank, e.g. "§a■■■■§7■■■■■■". */
    public String progressBar(UUID id, RankCategory c, RankScope scope, int length) {
        Rank rank = getRank(id, c, scope);
        Rank next = rank.next();
        double frac = 1.0;
        if (next != null) {
            long base = threshold(rank, scope);
            long span = Math.max(1, threshold(next, scope) - base);
            frac = Math.min(1.0, (double) (getPoints(id, c, scope) - base) / span);
        }
        int filled = (int) Math.round(frac * length);
        StringBuilder sb = new StringBuilder("§a");
        for (int i = 0; i < length; i++) {
            if (i == filled) sb.append("§7");
            sb.append('■');
        }
        return sb.toString();
    }

    /** Players with points in the category, best first. */
    public List<PlayerRecord> top(RankCategory c, RankScope scope, int limit) {
        for (PlayerRecord r : store.all()) if (scope == RankScope.SEASON) sync(r);
        return store.all().stream()
                .filter(r -> pts(r, c, scope) > 0)
                .sorted(Comparator.<PlayerRecord>comparingLong(r -> pts(r, c, scope)).reversed()
                        .thenComparing(r -> r.name, String.CASE_INSENSITIVE_ORDER))
                .limit(limit)
                .collect(Collectors.toCollection(ArrayList::new));
    }

    public long pts(PlayerRecord r, RankCategory c, RankScope scope) {
        return scope == RankScope.PERMANENT ? r.getPoints(c) : r.getSeasonPoints(c);
    }

    /** 1-based position in the leaderboard, or -1 if the player has no points. */
    public int position(UUID id, RankCategory c, RankScope scope) {
        if (getPoints(id, c, scope) <= 0) return -1;
        List<PlayerRecord> all = top(c, scope, Integer.MAX_VALUE);
        for (int i = 0; i < all.size(); i++) if (all.get(i).uuid.equals(id)) return i + 1;
        return -1;
    }

    /** Past seasons of a player, newest first: "S1 Clase: B-S1 (1500 pts)". */
    public List<String> history(PlayerRecord r) {
        List<String> out = new ArrayList<>();
        List<Map.Entry<String, String>> entries = new ArrayList<>(r.seasonHistory.entrySet());
        Collections.reverse(entries);
        for (Map.Entry<String, String> e : entries) {
            String[] k = e.getKey().split("\\.");
            String[] v = e.getValue().split("\\|");
            RankCategory c = k.length > 1 ? RankCategory.parse(k[1]) : null;
            out.add("§8S" + k[0] + " §7" + (c == null ? k[1] : c.label) + ": §f" + v[0] + (v.length > 1 ? " §7(" + v[1] + " pts)" : ""));
        }
        return out;
    }

    /** "§aPermanente: S (1200 pts) §7| §aTemporada: A-S1 (800 pts)". */
    public String line(UUID id, RankCategory c) {
        StringBuilder sb = new StringBuilder();
        for (RankScope sc : RankScope.values()) {
            Rank r = getRank(id, c, sc);
            if (sb.length() > 0) sb.append(" §8| ");
            sb.append("§e").append(sc.label).append("§7: ").append(fullDisplay(r, sc)).append(" §7(").append(getPoints(id, c, sc)).append(" pts)");
        }
        return sb.toString();
    }

    // ---- permanent-scope shortcuts (used by older callers) ---------------------------

    public long threshold(Rank rank) { return threshold(rank, RankScope.PERMANENT); }
    public void setPoints(UUID id, RankCategory c, long points) { setPoints(id, c, RankScope.PERMANENT, points); }
    public long pointsToNext(UUID id, RankCategory c) { return pointsToNext(id, c, RankScope.PERMANENT); }
    public String progressBar(UUID id, RankCategory c, int length) { return progressBar(id, c, RankScope.PERMANENT, length); }
    public List<PlayerRecord> top(RankCategory c, int limit) { return top(c, RankScope.PERMANENT, limit); }
    public int position(UUID id, RankCategory c) { return position(id, c, RankScope.PERMANENT); }
}
