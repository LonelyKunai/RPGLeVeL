package com.arclinker.rpglevelitems.stats;

import com.arclinker.rpglevelitems.RPGLevelTier;
import org.bukkit.ChatColor;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;

/**
 * Stat data stored inside an item (PersistentDataContainer):
 * <ul>
 *   <li>{@code rlv_stats}: base stats "key=value;key=value" (from the .rlvp {@code stats:} section)</li>
 *   <li>{@code rlv_affixes}: rolled affixes "affixId=value;..." (see AffixManager)</li>
 *   <li>{@code rlv_gems}: socketed gems "gemId:level;..." and {@code rlv_sockets}: total sockets</li>
 *   <li>{@code rlv_req}: requirements "class=guerrero;class_level=5;..."</li>
 * </ul>
 * Lore lines of these blocks are marked with ▸ ◆ ◈ ◇ ⚠ so they can be rebuilt.
 */
public final class ItemData {
    private static final String MARKS = "▸◆◈◇⚠";

    private ItemData() {}

    private static NamespacedKey key(String k) {
        return new NamespacedKey(RPGLevelTier.KEY_NS, k);
    }

    // ---- primitive access -----------------------------------------------------------

    public static String getString(ItemMeta meta, String k) {
        if (meta == null) return null;
        return meta.getPersistentDataContainer().get(key(k), PersistentDataType.STRING);
    }

    public static void setString(ItemMeta meta, String k, String v) {
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        if (v == null || v.isEmpty()) pdc.remove(key(k)); else pdc.set(key(k), PersistentDataType.STRING, v);
    }

    public static Map<String, String> parse(String s, char pairSep, String kvSep) {
        Map<String, String> m = new LinkedHashMap<>();
        if (s == null || s.isEmpty()) return m;
        for (String part : s.split(String.valueOf(pairSep))) {
            int i = part.indexOf(kvSep);
            if (i > 0) m.put(part.substring(0, i), part.substring(i + kvSep.length()));
        }
        return m;
    }

    public static String join(Map<String, ?> m, String kvSep) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, ?> e : m.entrySet()) {
            if (sb.length() > 0) sb.append(';');
            sb.append(e.getKey()).append(kvSep).append(e.getValue());
        }
        return sb.toString();
    }

    // ---- stats ----------------------------------------------------------------------

    public static Map<String, Double> baseStats(ItemMeta meta) {
        Map<String, Double> out = new LinkedHashMap<>();
        parse(getString(meta, "rlv_stats"), ';', "=").forEach((k, v) -> {
            try { out.put(k, Double.parseDouble(v)); } catch (NumberFormatException ignored) { }
        });
        return out;
    }

    public static void setBaseStats(ItemMeta meta, Map<String, Double> stats) {
        Map<String, String> m = new LinkedHashMap<>();
        stats.forEach((k, v) -> m.put(k, trim(v)));
        setString(meta, "rlv_stats", join(m, "="));
    }

    /** Affix rolls: affix id -> value. */
    public static Map<String, Double> affixes(ItemMeta meta) {
        Map<String, Double> out = new LinkedHashMap<>();
        parse(getString(meta, "rlv_affixes"), ';', "=").forEach((k, v) -> {
            try { out.put(k, Double.parseDouble(v)); } catch (NumberFormatException ignored) { }
        });
        return out;
    }

    public static void setAffixes(ItemMeta meta, Map<String, Double> rolls) {
        Map<String, String> m = new LinkedHashMap<>();
        rolls.forEach((k, v) -> m.put(k, trim(v)));
        setString(meta, "rlv_affixes", join(m, "="));
    }

    /** Socketed gems in order: "gemId:level". */
    public static List<String> gems(ItemMeta meta) {
        String s = getString(meta, "rlv_gems");
        return s == null || s.isEmpty() ? new ArrayList<>() : new ArrayList<>(Arrays.asList(s.split(";")));
    }

    public static void setGems(ItemMeta meta, List<String> gems) {
        setString(meta, "rlv_gems", String.join(";", gems));
    }

    public static int sockets(ItemMeta meta) {
        String s = getString(meta, "rlv_sockets");
        try { return s == null ? 0 : Integer.parseInt(s); } catch (NumberFormatException e) { return 0; }
    }

    public static void setSockets(ItemMeta meta, int n) {
        setString(meta, "rlv_sockets", n <= 0 ? null : String.valueOf(n));
    }

    public static Map<String, String> requirements(ItemMeta meta) {
        return parse(getString(meta, "rlv_req"), ';', "=");
    }

    public static void setRequirements(ItemMeta meta, Map<String, String> req) {
        setString(meta, "rlv_req", join(req, "="));
    }

    static String trim(double v) {
        return Math.abs(v - Math.rint(v)) < 1e-9 ? String.valueOf((long) Math.rint(v)) : String.format(Locale.ROOT, "%.3f", v).replaceAll("0+$", "");
    }

    // ---- totals ---------------------------------------------------------------------

    /** All stats an item gives: base + affixes + gems. */
    public static Map<String, Double> totalStats(ItemStack item) {
        Map<String, Double> out = new LinkedHashMap<>();
        if (item == null || !item.hasItemMeta()) return out;
        ItemMeta meta = item.getItemMeta();
        Stats.addAll(out, baseStats(meta));
        RPGLevelTier plugin = RPGLevelTier.getInstance();
        if (plugin.getAffixManager() != null) {
            for (Map.Entry<String, Double> e : affixes(meta).entrySet()) {
                String stat = plugin.getAffixManager().statOf(e.getKey());
                if (stat != null) Stats.add(out, stat, e.getValue());
            }
        }
        if (plugin.getGemManager() != null) {
            for (String g : gems(meta)) Stats.addAll(out, plugin.getGemManager().statsOf(g));
        }
        return out;
    }

    // ---- lore -----------------------------------------------------------------------

    private static boolean managed(String line) {
        String s = ChatColor.stripColor(line);
        return s != null && !s.isEmpty() && MARKS.indexOf(s.charAt(0)) >= 0;
    }

    private static String pretty(String id) {
        String raw = id.replace('_', ' ');
        return raw.isEmpty() ? raw : Character.toUpperCase(raw.charAt(0)) + raw.substring(1);
    }

    /** Rebuilds the managed lore blocks (stats, affixes, gems/sockets, requirements) of an item. */
    public static void rebuildLore(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return;
        ItemMeta meta = item.getItemMeta();
        List<String> lore = new ArrayList<>();
        if (meta.hasLore()) for (String l : meta.getLore()) if (!managed(l)) lore.add(l);
        while (!lore.isEmpty() && ChatColor.stripColor(lore.get(lore.size() - 1)).isEmpty()) lore.remove(lore.size() - 1);

        List<String> block = new ArrayList<>();
        Map<String, Double> base = baseStats(meta);
        base.forEach((k, v) -> block.add("§9▸ " + Stats.line(k, v)));
        RPGLevelTier plugin = RPGLevelTier.getInstance();
        if (plugin.getAffixManager() != null) {
            for (Map.Entry<String, Double> e : affixes(meta).entrySet()) {
                String stat = plugin.getAffixManager().statOf(e.getKey());
                if (stat != null) block.add("§b◆ " + Stats.line(stat, e.getValue()) + " §8(" + plugin.getAffixManager().nameOf(e.getKey()) + ")");
            }
        }
        List<String> gems = gems(meta);
        int sockets = sockets(meta);
        for (String g : gems) {
            String name = plugin.getGemManager() == null ? g : plugin.getGemManager().displayOf(g);
            block.add("§d◈ " + name);
        }
        for (int i = gems.size(); i < sockets; i++) block.add("§8◇ Ranura vacía");
        Map<String, String> req = requirements(meta);
        if (!req.isEmpty()) block.add("§7⚠ Requiere: §f" + describeRequirements(req));

        if (!block.isEmpty()) {
            lore.add("");
            lore.addAll(block);
        }
        meta.setLore(lore.isEmpty() ? null : lore);
        item.setItemMeta(meta);
    }

    public static String describeRequirements(Map<String, String> req) {
        List<String> parts = new ArrayList<>();
        if (req.containsKey("class")) parts.add("clase " + pretty(req.get("class")));
        if (req.containsKey("subclass")) parts.add("subclase " + pretty(req.get("subclass")));
        if (req.containsKey("class_level")) parts.add("nivel de clase " + req.get("class_level"));
        if (req.containsKey("subclass_level")) parts.add("nivel de subclase " + req.get("subclass_level"));
        if (req.containsKey("rank")) parts.add("rango " + req.get("rank"));
        if (req.containsKey("prestige")) parts.add("prestigio " + req.get("prestige"));
        return String.join(", ", parts);
    }
}
