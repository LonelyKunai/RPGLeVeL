package com.arclinker.rpglevelitems;

import com.arclinker.rpglevelitems.rank.PlayerRecord;
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

/**
 * Player subclasses, defined in {@code data/subclasses.yml} grouped by parent class:
 * <pre>
 * subclasses:
 *   guerrero:
 *     paladin: { name, description, icon? }
 * </pre>
 * A player needs a class first, and can only pick subclasses of that class.
 */
public class SubClassManager {
    private final RPGLevelTier plugin;
    private final Map<String, SubClassInfo> subclassMap = new LinkedHashMap<>();

    public SubClassManager(RPGLevelTier plugin) {
        this.plugin = plugin;
        loadSubClasses();
    }

    private void loadSubClasses() {
        YamlConfiguration cfg = DataFiles.loadMerged(plugin, "subclasses");
        ConfigurationSection root = cfg.getConfigurationSection("subclasses");
        if (root == null) {
            plugin.getLogger().warning("[SubClassManager] No subclasses found in data/subclasses/.");
            return;
        }
        for (String parent : root.getKeys(false)) {
            ConfigurationSection group = root.getConfigurationSection(parent);
            if (group == null) continue;
            ClassManager.ClassInfo parentInfo = plugin.getClassManager().getClassInfo(parent);
            Material fallback = parentInfo != null ? parentInfo.icon : Material.NETHER_STAR;
            if (parentInfo == null) {
                plugin.getLogger().warning("[SubClassManager] Unknown parent class '" + parent + "' in data/subclasses/.");
            }
            for (String id : group.getKeys(false)) {
                ConfigurationSection s = group.getConfigurationSection(id);
                if (s == null) continue;
                String key = id.toLowerCase(Locale.ROOT);
                subclassMap.put(key, new SubClassInfo(key, s.getString("name", id), s.getString("description", ""),
                        parent.toLowerCase(Locale.ROOT), Util.icon(s.getString("icon"), fallback)));
            }
        }
        plugin.getLogger().info("[SubClassManager] Loaded " + subclassMap.size() + " subclasses.");
    }

    /** Selects a subclass. Returns false (and tells the player why) if not allowed. */
    public boolean setSubClass(Player player, String subClassId) {
        SubClassInfo info = getInfo(subClassId);
        if (info == null) {
            player.sendMessage("§cSubclase no encontrada: " + subClassId);
            return false;
        }
        PlayerRecord r = plugin.getPlayerStore().get(player);
        if (r.classId.isEmpty()) {
            player.sendMessage("§cDebes seleccionar una clase primero (/class).");
            return false;
        }
        if (!info.parentClass.equalsIgnoreCase(r.classId)) {
            ClassManager.ClassInfo parent = plugin.getClassManager().getClassInfo(info.parentClass);
            player.sendMessage("§cEsa subclase pertenece a la clase " + (parent != null ? parent.name : info.parentClass) + ".");
            return false;
        }
        int need = plugin.getConfig().getInt("subclass.min_class_level", 10);
        if (!player.hasPermission("rpgleveltier.admin") && plugin.getClassLevelManager().getClassLevel(player) < need) {
            player.sendMessage("§cNecesitas nivel de clase " + need + " para elegir una subclase (tienes " + plugin.getClassLevelManager().getClassLevel(player) + ").");
            return false;
        }
        if (!r.subclassId.isEmpty() && !r.subclassId.equalsIgnoreCase(info.id) && !player.hasPermission("rpgleveltier.admin")) {
            double cost = plugin.getConfig().getDouble("subclass.change_cost", 150);
            if (cost > 0 && !plugin.getEconomy().withdraw(player, cost)) {
                player.sendMessage("§cCambiar de subclase cuesta " + plugin.getEconomy().format(cost) + ".");
                return false;
            }
        }
        r.subclassId = info.id;
        plugin.getPlayerStore().markDirty();
        plugin.getClassLevelManager().applyStats(player);
        player.sendMessage("§aHas seleccionado la subclase §f" + info.name + "§a.");
        return true;
    }

    public String getSubClassId(Player player) {
        return plugin.getPlayerStore().get(player).subclassId;
    }

    public Set<String> getSubClassIds() {
        return subclassMap.keySet();
    }

    /** Subclass ids belonging to the given class. */
    public List<String> getSubClassIdsOf(String classId) {
        List<String> out = new ArrayList<>();
        for (SubClassInfo i : subclassMap.values()) {
            if (i.parentClass.equalsIgnoreCase(classId)) out.add(i.id);
        }
        return out;
    }

    public Map<String, SubClassInfo> getSubClassMap() {
        return subclassMap;
    }

    public SubClassInfo getInfo(String id) {
        return id == null ? null : subclassMap.get(id.toLowerCase(Locale.ROOT));
    }

    public static class SubClassInfo {
        public final String id;
        public final String name;
        public final String description;
        public final String parentClass;
        public final Material icon;

        public SubClassInfo(String id, String name, String description, String parentClass, Material icon) {
            this.id = id;
            this.name = name;
            this.description = description;
            this.parentClass = parentClass;
            this.icon = icon;
        }
    }
}
