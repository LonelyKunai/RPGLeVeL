package com.arclinker.rpglevelitems.zones;

import com.arclinker.rpglevelitems.RPGLevelTier;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * Cuboid zones with flags (stored in zones.yml, managed with /rlv zone). Flags:
 * pvp (true/false), abilities (true/false), xp (multiplier), mob_level (base level), mob_range (+/- levels),
 * stations (true/false), boss_spawn (boss id for world boss spawns). Smallest matching zone wins per flag.
 * This is the plugin's own region system (no WorldGuard dependency).
 */
public final class ZoneManager {
    public static final class Zone {
        public final String id;
        public String world;
        public int x1, y1, z1, x2, y2, z2;
        public final Map<String, String> flags = new LinkedHashMap<>();

        Zone(String id) {
            this.id = id;
        }

        public boolean contains(Location l) {
            if (l.getWorld() == null || !l.getWorld().getName().equals(world)) return false;
            int x = l.getBlockX(), y = l.getBlockY(), z = l.getBlockZ();
            return x >= Math.min(x1, x2) && x <= Math.max(x1, x2) && y >= Math.min(y1, y2) && y <= Math.max(y1, y2)
                    && z >= Math.min(z1, z2) && z <= Math.max(z1, z2);
        }

        long volume() {
            return (long) (Math.abs(x1 - x2) + 1) * (Math.abs(y1 - y2) + 1) * (Math.abs(z1 - z2) + 1);
        }
    }

    private final RPGLevelTier plugin;
    private final File file;
    private final Map<String, Zone> zones = new LinkedHashMap<>();
    private final Map<UUID, Location[]> selections = new HashMap<>();

    public ZoneManager(RPGLevelTier plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "zones.yml");
        load();
    }

    private void load() {
        zones.clear();
        if (!file.exists()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = y.getConfigurationSection("zones");
        if (root == null) return;
        for (String id : root.getKeys(false)) {
            ConfigurationSection s = root.getConfigurationSection(id);
            if (s == null) continue;
            Zone z = new Zone(id);
            z.world = s.getString("world", "world");
            z.x1 = s.getInt("x1"); z.y1 = s.getInt("y1"); z.z1 = s.getInt("z1");
            z.x2 = s.getInt("x2"); z.y2 = s.getInt("y2"); z.z2 = s.getInt("z2");
            ConfigurationSection f = s.getConfigurationSection("flags");
            if (f != null) for (String k : f.getKeys(false)) z.flags.put(k, f.getString(k, ""));
            zones.put(id.toLowerCase(Locale.ROOT), z);
        }
        plugin.getLogger().info("[ZoneManager] Loaded " + zones.size() + " zones.");
    }

    public void save() {
        YamlConfiguration y = new YamlConfiguration();
        for (Zone z : zones.values()) {
            String p = "zones." + z.id + ".";
            y.set(p + "world", z.world);
            y.set(p + "x1", z.x1); y.set(p + "y1", z.y1); y.set(p + "z1", z.z1);
            y.set(p + "x2", z.x2); y.set(p + "y2", z.y2); y.set(p + "z2", z.z2);
            for (Map.Entry<String, String> e : z.flags.entrySet()) y.set(p + "flags." + e.getKey(), e.getValue());
        }
        try {
            plugin.getDataFolder().mkdirs();
            y.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("[ZoneManager] Could not save zones.yml: " + e.getMessage());
        }
    }

    public Collection<Zone> all() {
        return zones.values();
    }

    public Zone get(String id) {
        return id == null ? null : zones.get(id.toLowerCase(Locale.ROOT));
    }

    public Zone create(String id, Location a, Location b) {
        Zone z = new Zone(id);
        z.world = a.getWorld().getName();
        z.x1 = a.getBlockX(); z.y1 = a.getBlockY(); z.z1 = a.getBlockZ();
        z.x2 = b.getBlockX(); z.y2 = b.getBlockY(); z.z2 = b.getBlockZ();
        zones.put(id.toLowerCase(Locale.ROOT), z);
        save();
        return z;
    }

    public boolean delete(String id) {
        boolean ok = zones.remove(id.toLowerCase(Locale.ROOT)) != null;
        if (ok) save();
        return ok;
    }

    public Location[] selection(Player p) {
        return selections.computeIfAbsent(p.getUniqueId(), k -> new Location[2]);
    }

    /** Value of a flag at a location (smallest zone defining it), or null. */
    public String flag(Location l, String flag) {
        Zone best = null;
        for (Zone z : zones.values()) {
            if (!z.flags.containsKey(flag) || !z.contains(l)) continue;
            if (best == null || z.volume() < best.volume()) best = z;
        }
        return best == null ? null : best.flags.get(flag);
    }

    public boolean bool(Location l, String flag, boolean def) {
        String v = flag(l, flag);
        return v == null ? def : v.equalsIgnoreCase("true") || v.equalsIgnoreCase("yes") || v.equalsIgnoreCase("on");
    }

    public double number(Location l, String flag, double def) {
        String v = flag(l, flag);
        if (v == null) return def;
        try { return Double.parseDouble(v); } catch (NumberFormatException e) { return def; }
    }

    /** Zones at a location (all matches). */
    public List<Zone> at(Location l) {
        List<Zone> out = new ArrayList<>();
        for (Zone z : zones.values()) if (z.contains(l)) out.add(z);
        return out;
    }

    /** Can {@code attacker} hurt {@code victim} (both players)? Server setting + zone flag + duels. */
    public boolean pvpAllowed(Player attacker, Player victim) {
        if (plugin.getDuels() != null && plugin.getDuels().inDuelWith(attacker, victim)) return true;
        if (plugin.getParties() != null && plugin.getParties().sameParty(attacker, victim)
                && !plugin.getConfig().getBoolean("party.friendly_fire", false)) return false;
        boolean def = plugin.getConfig().getBoolean("pvp.default", false);
        return bool(victim.getLocation(), "pvp", def) && bool(attacker.getLocation(), "pvp", def);
    }

    public boolean abilitiesAllowed(Player p) {
        return bool(p.getLocation(), "abilities", true);
    }
}
