package com.arclinker.rpglevelitems.stats;

import com.arclinker.rpglevelitems.DataFiles;
import com.arclinker.rpglevelitems.RPGLevelTier;
import com.arclinker.rpglevelitems.itemcreator.RlvpHandler;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/** Random affixes (data/affixes/): rolled onto items by /rlv roll, loot drops and blacksmith forging. */
public final class AffixManager {
    public record Affix(String id, String name, String stat, double min, double max, List<String> slots) {}

    private final RPGLevelTier plugin;
    private final Map<String, Affix> affixes = new LinkedHashMap<>();
    private final Map<Integer, int[]> counts = new HashMap<>();
    private double tierScale = 0.35;

    public AffixManager(RPGLevelTier plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        affixes.clear();
        counts.clear();
        YamlConfiguration cfg = DataFiles.loadMerged(plugin, "affixes");
        tierScale = cfg.getDouble("settings.tier_scale", 0.35);
        ConfigurationSection cs = cfg.getConfigurationSection("settings.count_by_tier");
        if (cs != null) {
            for (String k : cs.getKeys(false)) {
                List<Integer> l = cs.getIntegerList(k);
                if (l.size() == 2) counts.put(Integer.parseInt(k), new int[]{l.get(0), l.get(1)});
            }
        }
        ConfigurationSection root = cfg.getConfigurationSection("affixes");
        if (root != null) {
            for (String id : root.getKeys(false)) {
                ConfigurationSection s = root.getConfigurationSection(id);
                if (s == null) continue;
                String stat = s.getString("stat", "");
                if (!Stats.isKnown(stat)) {
                    plugin.getLogger().warning("[AffixManager] Affix '" + id + "' has unknown stat '" + stat + "'");
                    continue;
                }
                affixes.put(id.toLowerCase(Locale.ROOT), new Affix(id.toLowerCase(Locale.ROOT), s.getString("name", id), stat,
                        s.getDouble("min"), s.getDouble("max"), s.getStringList("slots")));
            }
        }
        plugin.getLogger().info("[AffixManager] Loaded " + affixes.size() + " affixes.");
    }

    public Collection<Affix> all() {
        return affixes.values();
    }

    public String statOf(String id) {
        Affix a = affixes.get(id);
        return a == null ? null : a.stat();
    }

    public String nameOf(String id) {
        Affix a = affixes.get(id);
        return a == null ? id : a.name();
    }

    /** weapon | armor | offhand | other, from the item list info or the material. */
    public static String slotOf(RlvpHandler h, ItemStack item) {
        String id = h == null ? null : h.getId(item);
        RlvpHandler.ItemInfo info = id == null ? null : h.getInfo(id);
        if (info != null) {
            if (info.type().equals("weapon") || info.type().equals("armor") || info.type().equals("offhand")) return info.type();
        }
        String n = item.getType().name();
        if (n.endsWith("_HELMET") || n.endsWith("_CHESTPLATE") || n.endsWith("_LEGGINGS") || n.endsWith("_BOOTS")) return "armor";
        if (n.endsWith("_SWORD") || n.endsWith("_AXE") || item.getType() == Material.BOW || item.getType() == Material.CROSSBOW
                || item.getType() == Material.TRIDENT || item.getType() == Material.MACE || item.getType() == Material.BLAZE_ROD) return "weapon";
        if (item.getType() == Material.SHIELD) return "offhand";
        return "other";
    }

    private boolean fits(Affix a, String slot) {
        return a.slots().contains("any") || a.slots().contains(slot);
    }

    /** Number of affixes for a tier (random inside the configured range). */
    public int countFor(int tier) {
        int[] r = counts.getOrDefault(Math.max(1, Math.min(5, tier)), new int[]{0, 1});
        return r[0] + (r[1] > r[0] ? ThreadLocalRandom.current().nextInt(r[1] - r[0] + 1) : 0);
    }

    /**
     * Rolls (replaces) the affixes of the item for the given tier. {@code count < 0} = random by tier.
     * Returns how many were applied.
     */
    public int roll(ItemStack item, int tier, int count) {
        if (item == null || !item.hasItemMeta()) return 0;
        ItemMeta meta = item.getItemMeta();
        String slot = slotOf(plugin.getRlvpHandler(), item);
        int n = count >= 0 ? count : countFor(tier);
        List<Affix> pool = new ArrayList<>();
        for (Affix a : affixes.values()) if (fits(a, slot)) pool.add(a);
        Collections.shuffle(pool);
        Map<String, Double> rolls = new LinkedHashMap<>();
        Set<String> stats = new HashSet<>(ItemData.baseStats(meta).keySet());
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        double scale = 1 + tierScale * (Math.max(1, tier) - 1);
        for (Affix a : pool) {
            if (rolls.size() >= n) break;
            if (!stats.add(a.stat())) continue; // distinct stats only
            double q = Math.sqrt(rnd.nextDouble()); // skewed toward higher rolls
            double v = (a.min() + (a.max() - a.min()) * q) * scale;
            rolls.put(a.id(), Math.round(v * 1000.0) / 1000.0);
        }
        ItemData.setAffixes(meta, rolls);
        item.setItemMeta(meta);
        ItemData.rebuildLore(item);
        return rolls.size();
    }

    /** Tier of an item for rolling purposes (item list info, default 1). */
    public int tierOf(ItemStack item) {
        RlvpHandler h = plugin.getRlvpHandler();
        String id = h == null ? null : h.getId(item);
        RlvpHandler.ItemInfo i = id == null ? null : h.getInfo(id);
        return i == null || i.tier() <= 0 ? 1 : i.tier();
    }
}
