package com.arclinker.rpglevelitems.api;

import com.arclinker.rpglevelitems.RPGLevelTier;
import com.arclinker.rpglevelitems.rank.PlayerRecord;
import com.arclinker.rpglevelitems.rank.RankCategory;
import com.arclinker.rpglevelitems.rank.RankScope;
import org.bukkit.entity.Player;

import java.util.Map;

/**
 * Public facade for other plugins. Events live in {@code com.arclinker.rpglevelitems.api.events}
 * (ClassLevelUpEvent, AbilityCastEvent, RankChangeEvent, SeasonEndEvent, StationCraftEvent, BossKillEvent,
 * AchievementUnlockEvent, PrestigeEvent).
 */
public final class RPGLevelAPI {
    private RPGLevelAPI() {}

    private static RPGLevelTier plugin() {
        return RPGLevelTier.getInstance();
    }

    public static String getClassId(Player p) {
        return plugin().getPlayerStore().get(p).classId;
    }

    public static String getSubclassId(Player p) {
        return plugin().getPlayerStore().get(p).subclassId;
    }

    public static int getClassLevel(Player p) {
        return plugin().getClassLevelManager().getClassLevel(p);
    }

    public static int getSubclassLevel(Player p) {
        return plugin().getClassLevelManager().getSubClassLevel(p);
    }

    /** Rank id: "S" (permanent) or "S-S1" (season). */
    public static String getRank(Player p, RankCategory c, RankScope scope) {
        var rm = plugin().getRankManager();
        return rm.label(rm.getRank(p.getUniqueId(), c, scope), scope);
    }

    public static long getRankPoints(Player p, RankCategory c, RankScope scope) {
        return plugin().getRankManager().getPoints(p.getUniqueId(), c, scope);
    }

    public static void addRankPoints(Player p, RankCategory c, long amount) {
        plugin().getRankManager().addPoints(p, c, amount);
    }

    public static void addClassXp(Player p, long amount) {
        plugin().getClassLevelManager().addClassXp(p, amount);
        plugin().getClassLevelManager().addSubClassXp(p, amount);
    }

    /** Total value of a stat (see /rlv debug), e.g. "crit_chance", "mana_max". */
    public static double getStat(Player p, String key) {
        return plugin().getStatEngine().get(p, key);
    }

    public static Map<String, Double> getStats(Player p) {
        return plugin().getStatEngine().all(p);
    }

    public static double getMana(Player p) {
        return plugin().getMana().get(p);
    }

    public static boolean spendMana(Player p, double cost) {
        if (!plugin().getMana().has(p, cost)) return false;
        plugin().getMana().spend(p, cost);
        return true;
    }

    public static boolean hasUnlocked(Player p, String abilityId) {
        return plugin().getAbilityManager().isUnlocked(p, abilityId);
    }

    public static double getBalance(Player p) {
        return plugin().getEconomy().balance(p.getUniqueId());
    }

    public static int getPrestige(Player p) {
        return plugin().getPrestigeManager().total(p);
    }

    public static String getGuildName(Player p) {
        var g = plugin().getGuilds().of(p);
        return g == null ? "" : g.name;
    }

    public static PlayerRecord getRecord(Player p) {
        return plugin().getPlayerStore().get(p);
    }
}
