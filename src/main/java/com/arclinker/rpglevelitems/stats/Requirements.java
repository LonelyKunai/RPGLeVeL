package com.arclinker.rpglevelitems.stats;

import com.arclinker.rpglevelitems.RPGLevelTier;
import com.arclinker.rpglevelitems.rank.PlayerRecord;
import com.arclinker.rpglevelitems.rank.Rank;
import com.arclinker.rpglevelitems.rank.RankCategory;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Map;

/** Item requirements (rlv_req): class, subclass, class_level, subclass_level, rank, prestige. */
public final class Requirements {
    private Requirements() {}

    public static boolean enabled(RPGLevelTier plugin) {
        return plugin.getConfig().getBoolean("requirements.enabled", true);
    }

    public static boolean bypass(RPGLevelTier plugin, Player p) {
        return p.hasPermission(plugin.getConfig().getString("requirements.bypass_permission", "rpgleveltier.bypass"));
    }

    /** Why the player cannot use the item, or null if allowed. */
    public static String check(RPGLevelTier plugin, Player p, ItemStack item) {
        if (item == null || !item.hasItemMeta() || !enabled(plugin) || bypass(plugin, p)) return null;
        Map<String, String> req = ItemData.requirements(item.getItemMeta());
        if (req.isEmpty()) return null;
        PlayerRecord r = plugin.getPlayerStore().get(p);
        var lm = plugin.getClassLevelManager();
        String c = req.get("class");
        if (c != null && !r.classId.equalsIgnoreCase(c)) return "Requiere la clase " + nice(plugin, c, false);
        String s = req.get("subclass");
        if (s != null && !r.subclassId.equalsIgnoreCase(s)) return "Requiere la subclase " + nice(plugin, s, true);
        int cl = intOf(req.get("class_level"));
        if (cl > 0 && lm.getClassLevel(p) < cl) return "Requiere nivel de clase " + cl + " (tienes " + lm.getClassLevel(p) + ")";
        int sl = intOf(req.get("subclass_level"));
        if (sl > 0 && lm.getSubClassLevel(p) < sl) return "Requiere nivel de subclase " + sl + " (tienes " + lm.getSubClassLevel(p) + ")";
        String rk = req.get("rank");
        if (rk != null) {
            Rank need = Rank.parse(rk);
            if (need != null && plugin.getRankManager().getRank(p.getUniqueId(), RankCategory.CLASS).ordinal() < need.ordinal()) {
                return "Requiere rango permanente " + need.label;
            }
        }
        int pr = intOf(req.get("prestige"));
        if (pr > 0 && plugin.getPrestigeManager() != null && plugin.getPrestigeManager().total(p) < pr) return "Requiere prestigio " + pr;
        return null;
    }

    private static int intOf(String s) {
        try { return s == null ? 0 : Integer.parseInt(s.trim()); } catch (NumberFormatException e) { return 0; }
    }

    private static String nice(RPGLevelTier plugin, String id, boolean sub) {
        try {
            return sub ? plugin.getSubClassManager().getInfo(id).name : plugin.getClassManager().getClassInfo(id).name;
        } catch (Exception e) {
            return id;
        }
    }
}
