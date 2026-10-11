package com.arclinker.rpglevelitems;

import com.arclinker.rpglevelitems.rank.PlayerRecord;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlotGroup;

import java.util.*;

/**
 * Class and subclass levels. XP is stored per class / subclass id, so progress is kept when
 * switching. Every level improves the player's stats (data/class_stats.yml) and grants
 * ability XP for the matching ability tree.
 */
public class ClassLevelManager {

    /** Stats a class can improve (yml key -> attribute). */
    public enum Stat {
        MAX_HEALTH("max_health", "Vida máxima", Attribute.GENERIC_MAX_HEALTH, AttributeModifier.Operation.ADD_NUMBER),
        ATTACK_DAMAGE("attack_damage", "Daño", Attribute.GENERIC_ATTACK_DAMAGE, AttributeModifier.Operation.ADD_NUMBER),
        ARMOR("armor", "Armadura", Attribute.GENERIC_ARMOR, AttributeModifier.Operation.ADD_NUMBER),
        ARMOR_TOUGHNESS("armor_toughness", "Dureza", Attribute.GENERIC_ARMOR_TOUGHNESS, AttributeModifier.Operation.ADD_NUMBER),
        MOVEMENT_SPEED("movement_speed", "Velocidad", Attribute.GENERIC_MOVEMENT_SPEED, AttributeModifier.Operation.ADD_SCALAR),
        ATTACK_SPEED("attack_speed", "Vel. de ataque", Attribute.GENERIC_ATTACK_SPEED, AttributeModifier.Operation.ADD_SCALAR),
        LUCK("luck", "Suerte", Attribute.GENERIC_LUCK, AttributeModifier.Operation.ADD_NUMBER),
        KNOCKBACK_RESISTANCE("knockback_resistance", "Res. empuje", Attribute.GENERIC_KNOCKBACK_RESISTANCE, AttributeModifier.Operation.ADD_NUMBER);

        public final String key, label;
        public final Attribute attribute;
        public final AttributeModifier.Operation operation;

        Stat(String key, String label, Attribute attribute, AttributeModifier.Operation operation) {
            this.key = key;
            this.label = label;
            this.attribute = attribute;
            this.operation = operation;
        }

        public boolean isPercent() {
            return operation == AttributeModifier.Operation.ADD_SCALAR;
        }
    }

    private final RPGLevelTier plugin;
    private final Map<String, Map<Stat, Double>> classStats = new HashMap<>();
    private final Map<String, Map<Stat, Double>> subClassStats = new HashMap<>();
    private Map<Stat, Double> defaults = new EnumMap<>(Stat.class);

    public ClassLevelManager(RPGLevelTier plugin) {
        this.plugin = plugin;
        loadStats();
    }

    // ---- config ------------------------------------------------------------------

    public int maxLevel() {
        return Math.max(1, plugin.getConfig().getInt("class_levels.max_level", 50));
    }

    private long baseXp() {
        return Math.max(1L, plugin.getConfig().getLong("class_levels.base_xp", 100L));
    }

    private void loadStats() {
        YamlConfiguration cfg = DataFiles.loadMerged(plugin, "stats");
        defaults = parseStats(cfg.getConfigurationSection("defaults"));
        readGroup(cfg.getConfigurationSection("classes"), classStats);
        readGroup(cfg.getConfigurationSection("subclasses"), subClassStats);
        plugin.getLogger().info("[ClassLevelManager] Stats loaded for " + classStats.size() + " classes and " + subClassStats.size() + " subclasses.");
    }

    private void readGroup(ConfigurationSection sec, Map<String, Map<Stat, Double>> out) {
        if (sec == null) return;
        for (String id : sec.getKeys(false)) out.put(id.toLowerCase(Locale.ROOT), parseStats(sec.getConfigurationSection(id)));
    }

    private Map<Stat, Double> parseStats(ConfigurationSection sec) {
        Map<Stat, Double> m = new EnumMap<>(Stat.class);
        if (sec == null) {
            m.put(Stat.MAX_HEALTH, 0.4);
            m.put(Stat.ATTACK_DAMAGE, 0.08);
            m.put(Stat.ARMOR, 0.05);
            return m;
        }
        for (Stat s : Stat.values()) if (sec.contains(s.key)) m.put(s, sec.getDouble(s.key));
        return m;
    }

    // ---- level math ----------------------------------------------------------------

    /** Total XP needed to reach {@code level} (going from L to L+1 costs base * L). */
    public long xpForLevel(int level) {
        long l = Math.max(1, level);
        return baseXp() * l * (l - 1) / 2;
    }

    public int levelForXp(long xp) {
        int level = 1;
        while (level < maxLevel() && xp >= xpForLevel(level + 1)) level++;
        return level;
    }

    // ---- queries (active class / subclass) -----------------------------------------

    public long getClassXp(Player p) {
        PlayerRecord r = plugin.getPlayerStore().get(p);
        return r.classId.isEmpty() ? 0 : r.classXp.getOrDefault(r.classId, 0L);
    }

    public long getSubClassXp(Player p) {
        PlayerRecord r = plugin.getPlayerStore().get(p);
        return r.subclassId.isEmpty() ? 0 : r.subClassXp.getOrDefault(r.subclassId, 0L);
    }

    public int getClassLevel(Player p) {
        return levelForXp(getClassXp(p));
    }

    public int getSubClassLevel(Player p) {
        return levelForXp(getSubClassXp(p));
    }

    /** Level of the given class for the player (works for non-active classes too). */
    public int getClassLevel(PlayerRecord r, String classId) {
        return levelForXp(r.classXp.getOrDefault(classId, 0L));
    }

    public int getSubClassLevel(PlayerRecord r, String subClassId) {
        return levelForXp(r.subClassXp.getOrDefault(subClassId, 0L));
    }

    // ---- XP ----------------------------------------------------------------------

    public void addClassXp(Player p, long amount) {
        PlayerRecord r = plugin.getPlayerStore().get(p);
        if (r.classId.isEmpty() || amount <= 0) return;
        gain(p, r.classXp, r.classId, amount, false);
    }

    public void addSubClassXp(Player p, long amount) {
        PlayerRecord r = plugin.getPlayerStore().get(p);
        if (r.subclassId.isEmpty() || amount <= 0) return;
        gain(p, r.subClassXp, r.subclassId, amount, true);
    }

    /** Sets the level of the active class / subclass directly (admin). */
    public void setLevel(Player p, boolean subclass, int level) {
        PlayerRecord r = plugin.getPlayerStore().get(p);
        String id = subclass ? r.subclassId : r.classId;
        if (id.isEmpty()) return;
        Map<String, Long> map = subclass ? r.subClassXp : r.classXp;
        long before = map.getOrDefault(id, 0L);
        long target = xpForLevel(Math.max(1, Math.min(maxLevel(), level)));
        gain(p, map, id, target - before, subclass);
        if (target < before) {
            map.put(id, target);
            plugin.getPlayerStore().markDirty();
            applyStats(p);
        }
    }

    private void gain(Player p, Map<String, Long> map, String id, long amount, boolean subclass) {
        long cap = xpForLevel(maxLevel());
        long before = map.getOrDefault(id, 0L);
        long after = Math.min(cap, before + amount);
        if (after == before) return;
        map.put(id, after);
        plugin.getPlayerStore().markDirty();
        int lvBefore = levelForXp(before), lvAfter = levelForXp(after);
        if (lvAfter > lvBefore) {
            String name = subclass ? plugin.getSubClassManager().getInfo(id).name : plugin.getClassManager().getClassInfo(id).name;
            String kind = plugin.getLanguageManager().tr(p, subclass ? "level.kind_subclass" : "level.kind_class");
            p.sendMessage(plugin.getLanguageManager().tr(p, "level.up", kind, name, lvAfter));
            p.sendTitle(plugin.getLanguageManager().tr(p, "level.title", lvAfter), "§7" + name, 5, 40, 10);
            applyStats(p);
            plugin.getAbilityManager().onLevelUp(p, subclass, id, lvBefore, lvAfter);
            org.bukkit.Bukkit.getPluginManager().callEvent(new com.arclinker.rpglevelitems.api.events.ClassLevelUpEvent(p, subclass, id, lvBefore, lvAfter));
            if (!subclass) plugin.getAchievements().max(p, "max_class_level", lvAfter);
        }
    }

    // ---- stats ---------------------------------------------------------------------

    /** Total bonus of a stat for the player's active class + subclass at their current levels. */
    public double getBonus(Player p, Stat stat) {
        PlayerRecord r = plugin.getPlayerStore().get(p);
        double total = 0;
        if (!r.classId.isEmpty()) {
            total += perLevel(classStats, r.classId, stat) * (getClassLevel(r, r.classId) - 1);
        }
        if (!r.subclassId.isEmpty()) {
            total += perLevel(subClassStats, r.subclassId, stat) * (getSubClassLevel(r, r.subclassId) - 1);
        }
        return total;
    }

    private double perLevel(Map<String, Map<Stat, Double>> groups, String id, Stat stat) {
        Map<Stat, Double> m = groups.get(id.toLowerCase(Locale.ROOT));
        if (m == null) m = defaults;
        return m.getOrDefault(stat, 0.0);
    }

    /** (Re)applies the attribute modifiers for the player's class and subclass levels. */
    public void applyStats(Player p) {
        for (Stat stat : Stat.values()) {
            AttributeInstance inst = p.getAttribute(stat.attribute);
            if (inst == null) continue;
            NamespacedKey key = new NamespacedKey(RPGLevelTier.KEY_NS, "stat_" + stat.key);
            for (AttributeModifier m : new ArrayList<>(inst.getModifiers())) {
                if (key.equals(m.getKey())) inst.removeModifier(m);
            }
            double bonus = getBonus(p, stat);
            if (bonus > 0) inst.addModifier(new AttributeModifier(key, bonus, stat.operation, EquipmentSlotGroup.ANY));
        }
        AttributeInstance hp = p.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        if (hp != null && p.getHealth() > hp.getValue()) p.setHealth(hp.getValue());
    }

    /** Human readable lines for /class stats and /subclass stats. */
    public List<String> describe(Player p, boolean subclass) {
        PlayerRecord r = plugin.getPlayerStore().get(p);
        String id = subclass ? r.subclassId : r.classId;
        List<String> out = new ArrayList<>();
        if (id.isEmpty()) {
            out.add(subclass ? "§eNo tienes subclase." : "§eNo tienes clase.");
            return out;
        }
        String name = subclass ? plugin.getSubClassManager().getInfo(id).name : plugin.getClassManager().getClassInfo(id).name;
        long xp = subclass ? getSubClassXp(p) : getClassXp(p);
        int lv = levelForXp(xp);
        out.add("§6" + (subclass ? "Subclase" : "Clase") + ": §f" + name + " §7- nivel §e" + lv + "§7/" + maxLevel());
        if (lv >= maxLevel()) {
            out.add("§7XP: §f" + xp + " §6(nivel máximo)");
        } else {
            long next = xpForLevel(lv + 1), base = xpForLevel(lv);
            out.add("§7XP: §f" + (xp - base) + "§7/§f" + (next - base) + " §7para el nivel " + (lv + 1));
        }
        Map<String, Long> pool = r.abilityXp;
        String tree = (subclass ? "sub:" : "class:") + id;
        out.add("§7Ability XP disponible: §d" + pool.getOrDefault(tree, 0L) + " §7| Puntos de habilidad: §a" + plugin.getAbilityManager().availablePoints(p, tree));
        for (Stat s : Stat.values()) {
            double b = (subclass ? perLevel(subClassStats, id, s) : perLevel(classStats, id, s)) * (lv - 1);
            if (b <= 0) continue;
            out.add(String.format(Locale.ROOT, " §8• §7%s: §a+%s §8(%s por nivel)", s.label,
                    s.isPercent() ? String.format(Locale.ROOT, "%.1f%%", b * 100) : String.format(Locale.ROOT, "%.2f", b),
                    s.isPercent() ? String.format(Locale.ROOT, "%.2f%%", perLevel(subclass ? subClassStats : classStats, id, s) * 100)
                                  : String.format(Locale.ROOT, "%.2f", perLevel(subclass ? subClassStats : classStats, id, s))));
        }
        return out;
    }
}
