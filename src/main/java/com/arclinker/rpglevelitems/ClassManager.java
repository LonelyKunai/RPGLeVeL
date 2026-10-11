package com.arclinker.rpglevelitems;

import com.arclinker.rpglevelitems.rank.PlayerRecord;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Player classes, defined in {@code data/classes.yml}. The selection is stored in the
 * player's record (players.yml), so it persists and can be used for rankings.
 */
public class ClassManager {
    private final RPGLevelTier plugin;
    private final Map<String, ClassInfo> classMap = new LinkedHashMap<>();

    public ClassManager(RPGLevelTier plugin) {
        this.plugin = plugin;
        loadClasses();
    }

    private void loadClasses() {
        YamlConfiguration cfg = DataFiles.loadMerged(plugin, "classes");
        ConfigurationSection root = cfg.getConfigurationSection("classes");
        if (root == null) {
            plugin.getLogger().warning("[ClassManager] No classes found in data/classes/.");
            return;
        }
        for (String id : root.getKeys(false)) {
            ConfigurationSection s = root.getConfigurationSection(id);
            if (s == null) continue;
            classMap.put(id.toLowerCase(Locale.ROOT), new ClassInfo(id.toLowerCase(Locale.ROOT),
                    s.getString("name", id), s.getString("description", ""),
                    Util.icon(s.getString("icon"), Material.IRON_SWORD)));
        }
        plugin.getLogger().info("[ClassManager] Loaded " + classMap.size() + " classes.");
    }

    /** Selects a class. Returns false (and tells the player) if it does not exist. */
    public boolean setClass(Player player, String classId) {
        ClassInfo info = getClassInfo(classId);
        if (info == null) {
            player.sendMessage("§cClase no encontrada: " + classId);
            return false;
        }
        PlayerRecord r = plugin.getPlayerStore().get(player);
        boolean changed = !info.id.equalsIgnoreCase(r.classId);
        // Switching away from an existing class has a cooldown and a cost (the first choice is free)
        if (changed && !r.classId.isEmpty() && !player.hasPermission("rpgleveltier.admin")) {
            long wait = r.lastClassChange + plugin.getConfig().getLong("class.change_cooldown_hours", 6) * 3_600_000L - System.currentTimeMillis();
            if (wait > 0) {
                player.sendMessage("§cPodrás cambiar de clase de nuevo en " + (wait / 60000 + 1) + " min.");
                return false;
            }
            double cost = plugin.getConfig().getDouble("class.change_cost", 300);
            if (cost > 0 && !plugin.getEconomy().withdraw(player, cost)) {
                player.sendMessage("§cCambiar de clase cuesta " + plugin.getEconomy().format(cost) + ".");
                return false;
            }
        }
        String previous = r.classId;
        if (changed) {
            r.lastClassChange = System.currentTimeMillis();
            // remember the class we leave (subclass + ability hotbar) and restore what we had in the new one
            if (!previous.isEmpty()) {
                PlayerRecord.Snapshot snap = r.snapshots.computeIfAbsent(previous.toLowerCase(Locale.ROOT), k -> new PlayerRecord.Snapshot());
                snap.subclass = r.subclassId;
                System.arraycopy(r.hotbar, 0, snap.hotbar, 0, snap.hotbar.length);
            }
        }
        r.classId = info.id;
        if (changed) {
            PlayerRecord.Snapshot back = r.snapshots.get(info.id.toLowerCase(Locale.ROOT));
            r.subclassId = "";
            Arrays.fill(r.hotbar, "");
            if (back != null) {
                SubClassManager.SubClassInfo sub = back.subclass.isEmpty() ? null : plugin.getSubClassManager().getInfo(back.subclass);
                if (sub != null && sub.parentClass.equalsIgnoreCase(info.id)) {
                    r.subclassId = sub.id;
                    player.sendMessage("§7Recuperaste tu subclase §f" + sub.name + "§7 y tu barra de habilidades de esta clase.");
                }
                System.arraycopy(back.hotbar, 0, r.hotbar, 0, r.hotbar.length);
            }
            syncLuckPermsGroup(player, previous, info.id);
        }
        plugin.getJobManager().assignClassJob(player, info.id);
        plugin.getPlayerStore().markDirty();
        plugin.getClassLevelManager().applyStats(player);
        player.sendMessage("§aHas seleccionado la clase §f" + info.name + "§a.");
        return true;
    }

    private static final java.util.regex.Pattern SAFE_ID = java.util.regex.Pattern.compile("[a-z0-9_]{1,32}");

    /**
     * Keeps the LuckPerms group {@code <prefix><classId>} (default class_<id>, see docs/LUCKPERMS.md) in sync:
     * removes the group of the class we leave and adds the one of the new class. Fixed command shapes only
     * (ids are validated); disable with integrations.luckperms.class_groups: false.
     */
    public void syncLuckPermsGroup(Player player, String oldClass, String newClass) {
        if (!plugin.getConfig().getBoolean("integrations.luckperms.class_groups", true)) return;
        if (Bukkit.getPluginManager().getPlugin("LuckPerms") == null) return;
        String prefix = plugin.getConfig().getString("integrations.luckperms.group_prefix", "class_").toLowerCase(Locale.ROOT);
        if (!SAFE_ID.matcher(prefix).matches()) return;
        String who = player.getUniqueId().toString();
        if (oldClass != null && !oldClass.isEmpty() && SAFE_ID.matcher(oldClass.toLowerCase(Locale.ROOT)).matches()) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "lp user " + who + " parent remove " + prefix + oldClass.toLowerCase(Locale.ROOT));
        }
        if (newClass != null && !newClass.isEmpty() && SAFE_ID.matcher(newClass.toLowerCase(Locale.ROOT)).matches()) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "lp user " + who + " parent add " + prefix + newClass.toLowerCase(Locale.ROOT));
        }
    }

    /** Selected class id (lowercase) or empty string. */
    public String getClassId(Player player) {
        return plugin.getPlayerStore().get(player).classId;
    }

    public ClassInfo getClassInfo(String id) {
        return id == null ? null : classMap.get(id.toLowerCase(Locale.ROOT));
    }

    public Map<String, ClassInfo> getClassMap() {
        return classMap;
    }

    /** Class ids (lowercase) for tab completion and GUIs. */
    public Set<String> getClassIds() {
        return classMap.keySet();
    }

    public static class ClassInfo {
        public final String id;
        public final String name;
        public final String description;
        public final Material icon;

        public ClassInfo(String id, String name, String description, Material icon) {
            this.id = id;
            this.name = name;
            this.description = description;
            this.icon = icon;
        }
    }
}
