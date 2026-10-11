package com.arclinker.rpglevelitems.hook;

import com.arclinker.rpglevelitems.RPGLevelTier;
import com.arclinker.rpglevelitems.rank.PlayerRecord;
import com.arclinker.rpglevelitems.rank.RankCategory;
import com.arclinker.rpglevelitems.rank.RankScope;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;

/**
 * PlaceholderAPI expansion (identifier {@code rlv}). Only loaded when PlaceholderAPI is installed.
 * <pre>
 * %rlv_class% %rlv_class_level% %rlv_subclass% %rlv_subclass_level%
 * %rlv_rank_<class|subclass|job|quest|pvp>%          permanent rank (F..U)
 * %rlv_rank_<cat>_season%                             season rank (F-S1..U-S1)
 * %rlv_points_<cat>% %rlv_points_<cat>_season%
 * %rlv_season% %rlv_season_left% %rlv_mana% %rlv_mana_max% %rlv_stat_<key>%
 * %rlv_title% %rlv_prestige% %rlv_guild% %rlv_guild_tag% %rlv_coins% %rlv_party_size% %rlv_event%
 * %rlv_job_<id>_level%
 * %rlv_class_xp_bar% %rlv_class_xp_pct% %rlv_mana_bar% (barras de 20 marcas) %rlv_build% (diagnóstico)
 * %rlv_level_suffix% " Nv.12" (vacío sin clase) %rlv_class_text% %rlv_subclass_text% %rlv_event_text% (vacío sin evento: la línea se oculta)  (textos coloreados para TAB)
 * </pre>
 */
public final class PapiHook extends PlaceholderExpansion {
    private final RPGLevelTier plugin;

    public PapiHook(RPGLevelTier plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "rlv";
    }

    @Override
    public @NotNull String getAuthor() {
        return "Arclinker";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer off, @NotNull String params) {
        String k = params.toLowerCase(Locale.ROOT);
        var rm = plugin.getRankManager();
        if (k.equals("season")) return String.valueOf(rm.season());
        if (k.equals("season_left")) return rm.timeLeft();
        if (k.equals("event_text")) return plugin.getEvents().active() ? "§c§l» EVENTO §f" + plugin.getEvents().activeName() : "";
        if (k.equals("event")) return plugin.getEvents().active() ? plugin.getEvents().activeName() : "";
        if (off == null) return "";
        PlayerRecord r = plugin.getPlayerStore().peek(off.getUniqueId()); // never create records for NPCs / unknown ids
        if (r == null) return "";
        Player p = off.getPlayer();
        switch (k) {
            case "class": return r.classId;
            case "subclass": return r.subclassId;
            case "class_name": { var ci = plugin.getClassManager().getClassInfo(r.classId); return ci == null ? "" : ci.name; }
            case "subclass_name": { var si = plugin.getSubClassManager().getInfo(r.subclassId); return si == null ? "" : si.name; }
            // textos listos para TAB (sin depender de %condition:...%); colores con §
            case "level_suffix": return r.classId.isEmpty() ? "" : " §8Nv.§a" + plugin.getClassLevelManager().getClassLevel(r, r.classId);
            case "class_text": {
                var ci = plugin.getClassManager().getClassInfo(r.classId);
                return r.classId.isEmpty() || ci == null ? "§cElige una (/class)" : "§f" + ci.name + " §8Nv.§a" + plugin.getClassLevelManager().getClassLevel(r, r.classId);
            }
            case "subclass_text": {
                var si = plugin.getSubClassManager().getInfo(r.subclassId);
                return r.subclassId.isEmpty() || si == null ? "§8ninguna" : "§f" + si.name + " §8Nv.§a" + plugin.getClassLevelManager().getSubClassLevel(r, r.subclassId);
            }
            case "class_xp_pct": return String.valueOf(classXpPct(r));
            case "class_xp_bar": return r.classId.isEmpty() ? bar(0, 20, "§a") : bar(classXpPct(r) / 100.0, 20, "§a");
            case "build": return "tab3"; // diagnóstico: /papi parse me %rlv_build% debe devolver tab3 con el jar actual
            case "job_1": return r.job1;
            case "job_2": return r.job2;
            case "class_level": return r.classId.isEmpty() ? "0" : String.valueOf(plugin.getClassLevelManager().getClassLevel(r, r.classId));
            case "subclass_level": return r.subclassId.isEmpty() ? "0" : String.valueOf(plugin.getClassLevelManager().getSubClassLevel(r, r.subclassId));
            case "coins": return String.valueOf((long) plugin.getEconomy().balance(off.getUniqueId()));
            case "title": return plugin.getAchievements().activeTitleDisplay(r);
            case "guild": { var g = plugin.getGuilds().of(off.getUniqueId()); return g == null ? "" : g.name; }
            case "guild_tag": { var g = plugin.getGuilds().of(off.getUniqueId()); return g == null ? "" : g.tag; }
            default: break;
        }
        if (p != null) {
            switch (k) {
                case "mana": return String.valueOf((int) plugin.getMana().get(p));
                case "mana_max": return String.valueOf((int) plugin.getMana().max(p));
                case "mana_bar": { double mx = plugin.getMana().max(p); return bar(mx <= 0 ? 0 : plugin.getMana().get(p) / mx, 20, "§9"); }
                case "prestige": return String.valueOf(plugin.getPrestigeManager().total(p));
                case "party_size": { var party = plugin.getParties().of(p); return String.valueOf(party == null ? 0 : party.members.size()); }
                default: break;
            }
            if (k.startsWith("stat_")) return String.format(Locale.ROOT, "%.2f", plugin.getStatEngine().get(p, k.substring(5)));
        }
        if (k.startsWith("job_") && k.endsWith("_level")) {
            String id = k.substring(4, k.length() - 6);
            return String.valueOf(JobLevels.of(plugin, r, id));
        }
        for (String prefix : new String[]{"rank_", "points_"}) {
            if (!k.startsWith(prefix)) continue;
            String rest = k.substring(prefix.length());
            RankScope scope = RankScope.PERMANENT;
            if (rest.endsWith("_season")) {
                scope = RankScope.SEASON;
                rest = rest.substring(0, rest.length() - 7);
            }
            RankCategory c = RankCategory.parse(rest);
            if (c == null) return null;
            return prefix.equals("rank_") ? rm.label(rm.getRank(off.getUniqueId(), c, scope), scope) : String.valueOf(rm.getPoints(off.getUniqueId(), c, scope));
        }
        return null;
    }

    /** Barra de progreso de {@code len} marcas: las llenas con {@code color}, el resto en gris oscuro. */
    private static String bar(double frac, int len, String color) {
        int on = (int) Math.round(Math.max(0, Math.min(1, frac)) * len);
        return color + "|".repeat(on) + "§8" + "|".repeat(len - on);
    }

    /** Porcentaje (0-100) de XP hacia el siguiente nivel de la clase activa; 100 en el nivel máximo. */
    private int classXpPct(PlayerRecord r) {
        if (r.classId.isEmpty()) return 0;
        var clm = plugin.getClassLevelManager();
        long xp = r.classXp.getOrDefault(r.classId, 0L);
        int lvl = clm.levelForXp(xp);
        if (lvl >= clm.maxLevel()) return 100;
        long lo = clm.xpForLevel(lvl), hi = clm.xpForLevel(lvl + 1);
        return hi <= lo ? 0 : (int) Math.max(0, Math.min(100, (xp - lo) * 100 / (hi - lo)));
    }

    private static final class JobLevels {
        static int of(RPGLevelTier plugin, PlayerRecord r, String jobId) {
            return com.arclinker.rpglevelitems.JobManager.levelForXp(r.jobXp.getOrDefault(jobId, 0));
        }
    }
}
