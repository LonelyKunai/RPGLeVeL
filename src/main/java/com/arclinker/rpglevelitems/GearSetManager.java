package com.arclinker.rpglevelitems;

import com.arclinker.rpglevelitems.stats.Requirements;
import com.arclinker.rpglevelitems.stats.Stats;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;

/**
 * Gear sets (data/gear_sets/sets.yml): wearing N pieces of the same set (items with custom_data
 * {@code set: <id>}) grants the bonuses of every threshold up to N.
 * <pre>
 * sets:
 *   warrior:
 *     name: Guerrero
 *     bonuses:
 *       2: {stats: {damage_reduction: 2.4}}
 *       4: {stats: {attack_damage: 1.8}}
 * </pre>
 */
public class GearSetManager {
    private final RPGLevelTier plugin;
    private final Map<String, GearSet> sets = new LinkedHashMap<>();
    private final NamespacedKey setKey;

    public GearSetManager(RPGLevelTier plugin) {
        this.plugin = plugin;
        this.setKey = new NamespacedKey(RPGLevelTier.KEY_NS, "set");
        reload();
    }

    public void reload() {
        sets.clear();
        YamlConfiguration cfg = DataFiles.loadMerged(plugin, "gear_sets");
        ConfigurationSection root = cfg.getConfigurationSection("sets");
        if (root == null) {
            plugin.getLogger().warning("[GearSetManager] No sets found in data/gear_sets/.");
            return;
        }
        for (String id : root.getKeys(false)) {
            ConfigurationSection s = root.getConfigurationSection(id);
            if (s == null) continue;
            GearSet set = new GearSet(id.toLowerCase(Locale.ROOT), s.getString("name", id));
            ConfigurationSection b = s.getConfigurationSection("bonuses");
            if (b != null) {
                for (String n : b.getKeys(false)) {
                    ConfigurationSection st = b.getConfigurationSection(n + ".stats");
                    if (st == null) continue;
                    Map<String, Double> m = new LinkedHashMap<>();
                    for (String k : st.getKeys(false)) if (Stats.isKnown(k)) m.put(k, st.getDouble(k));
                    try { set.bonuses.put(Integer.parseInt(n), m); } catch (NumberFormatException ignored) { }
                }
            }
            sets.put(set.id, set);
        }
        plugin.getLogger().info("[GearSetManager] Loaded " + sets.size() + " gear sets.");
    }

    public GearSet getSet(String id) {
        return id == null ? null : sets.get(id.toLowerCase(Locale.ROOT));
    }

    public Collection<GearSet> getAllSets() {
        return sets.values();
    }

    /** Equipped pieces per set id (armor + hands, only items the player may use). */
    public Map<String, Integer> equippedPieces(Player p) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        // armor slots: one piece each
        for (ItemStack it : p.getInventory().getArmorContents()) count(p, it, counts);
        // hands: weapon / offhand pieces only (never armor pieces held in hand), each distinct item once
        Set<String> seen = new HashSet<>();
        for (ItemStack it : new ItemStack[]{p.getInventory().getItemInMainHand(), p.getInventory().getItemInOffHand()}) {
            if (it == null || it.getType().isAir()) continue;
            String n = it.getType().name();
            if (n.endsWith("_HELMET") || n.endsWith("_CHESTPLATE") || n.endsWith("_LEGGINGS") || n.endsWith("_BOOTS")) continue;
            String id = plugin.getRlvpHandler() == null ? null : plugin.getRlvpHandler().getId(it);
            if (id != null && !seen.add(id)) continue;
            count(p, it, counts);
        }
        return counts;
    }

    private void count(Player p, ItemStack it, Map<String, Integer> counts) {
        if (it == null || it.getType().isAir() || !it.hasItemMeta()) return;
        String set = it.getItemMeta().getPersistentDataContainer().get(setKey, PersistentDataType.STRING);
        if (set == null || Requirements.check(plugin, p, it) != null) return;
        counts.merge(set.toLowerCase(Locale.ROOT), 1, Integer::sum);
    }

    /** Sum of every active set bonus. */
    public Map<String, Double> bonusFor(Player p) {
        Map<String, Double> out = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> e : equippedPieces(p).entrySet()) {
            GearSet set = sets.get(e.getKey());
            if (set == null) continue;
            for (Map.Entry<Integer, Map<String, Double>> b : set.bonuses.entrySet()) {
                if (e.getValue() >= b.getKey()) Stats.addAll(out, b.getValue());
            }
        }
        return out;
    }

    /** Lines describing the sets the player is wearing. */
    public List<String> describe(Player p) {
        List<String> out = new ArrayList<>();
        for (Map.Entry<String, Integer> e : equippedPieces(p).entrySet()) {
            GearSet set = sets.get(e.getKey());
            if (set == null) continue;
            out.add("§6Set " + set.name + " §7(" + e.getValue() + " piezas)");
            for (Map.Entry<Integer, Map<String, Double>> b : set.bonuses.entrySet()) {
                boolean on = e.getValue() >= b.getKey();
                StringBuilder sb = new StringBuilder(on ? "§a✔ " : "§8✘ ").append(b.getKey()).append(" piezas: ");
                boolean first = true;
                for (Map.Entry<String, Double> s : b.getValue().entrySet()) {
                    sb.append(first ? "" : ", ").append(Stats.line(s.getKey(), s.getValue()));
                    first = false;
                }
                out.add("  " + (on ? "" : "§7") + sb);
            }
        }
        return out;
    }

    /** Data holder for a gear set. */
    public static class GearSet {
        public final String id;
        public final String name;
        public final TreeMap<Integer, Map<String, Double>> bonuses = new TreeMap<>();

        public GearSet(String id, String name) {
            this.id = id;
            this.name = name;
        }
    }
}
