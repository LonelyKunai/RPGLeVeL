package com.arclinker.rpglevelitems.world;

import com.arclinker.rpglevelitems.DataFiles;
import com.arclinker.rpglevelitems.RPGLevelTier;
import com.arclinker.rpglevelitems.rank.PlayerRecord;
import com.arclinker.rpglevelitems.rank.RankCategory;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Dungeons (data/dungeons/): scripted wave rooms in a pre-built area. One run per dungeon at a time;
 * the group (party) is teleported in, each room must be cleared, the last one can be a boss.
 */
public final class DungeonManager implements Listener {
    private final class Run {
        final String id;
        final ConfigurationSection def;
        final Set<UUID> players = new HashSet<>();
        final String tag;
        final Set<UUID> spawned = new HashSet<>();
        int room = -1;
        long endsAt;
        boolean boss;
        UUID bossId;
        int cooldownTicks;

        Run(String id, ConfigurationSection def) {
            this.id = id;
            this.def = def;
            this.tag = "rlv_dg_" + id + "_" + System.nanoTime();
        }
    }

    private final RPGLevelTier plugin;
    private YamlConfiguration cfg = new YamlConfiguration();
    private final Map<String, Run> runs = new HashMap<>();

    public DungeonManager(RPGLevelTier plugin) {
        this.plugin = plugin;
        reload();
        Bukkit.getPluginManager().registerEvents(this, plugin);
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    public void reload() {
        cfg = DataFiles.loadMerged(plugin, "dungeons");
        plugin.getLogger().info("[DungeonManager] Loaded " + ids().size() + " dungeons.");
    }

    public Set<String> ids() {
        ConfigurationSection s = cfg.getConfigurationSection("dungeons");
        return s == null ? Set.of() : s.getKeys(false);
    }

    public String describe(String id) {
        ConfigurationSection d = cfg.getConfigurationSection("dungeons." + id);
        if (d == null) return id;
        return (d.getBoolean("enabled", false) ? "§a" : "§8") + id + " §7- " + d.getString("name", id) + " §8(nv. clase " + d.getInt("min_class_level", 1)
                + ", " + d.getInt("max_players", 4) + " jugadores)" + (runs.containsKey(id) ? " §c[en curso]" : "");
    }

    private Location loc(ConfigurationSection s, double dx, double dy, double dz) {
        World w = Bukkit.getWorld(s.getString("world", "world"));
        return w == null ? null : new Location(w, s.getDouble("x") + dx, s.getDouble("y") + dy, s.getDouble("z") + dz);
    }

    public String start(Player leader, String id) {
        ConfigurationSection d = cfg.getConfigurationSection("dungeons." + id);
        if (d == null) return "Mazmorra desconocida.";
        if (!d.getBoolean("enabled", false)) return "Esa mazmorra está desactivada (configúrala en data/dungeons).";
        if (runs.containsKey(id)) return "Esa mazmorra ya está en curso. Espera a que termine.";
        for (Run r : runs.values()) if (r.players.contains(leader.getUniqueId())) return "Ya estás en una mazmorra (/dungeon leave).";
        List<Player> group = new ArrayList<>();
        var party = plugin.getParties().of(leader);
        if (party != null) {
            if (!party.leader.equals(leader.getUniqueId())) return "Solo el líder de la party puede iniciarla.";
            for (UUID u : party.members) {
                Player m = Bukkit.getPlayer(u);
                if (m != null) group.add(m);
            }
        } else {
            group.add(leader);
        }
        if (group.size() > d.getInt("max_players", 4)) return "Máximo " + d.getInt("max_players", 4) + " jugadores.";
        for (Player m : group) {
            PlayerRecord r = plugin.getPlayerStore().get(m);
            if (plugin.getClassLevelManager().getClassLevel(m) < d.getInt("min_class_level", 1)) return m.getName() + " necesita nivel de clase " + d.getInt("min_class_level", 1) + ".";
            long cd = r.dungeonCooldowns.getOrDefault(id, 0L) - System.currentTimeMillis();
            if (cd > 0) return m.getName() + " debe esperar " + (cd / 60000 + 1) + " min.";
        }
        Location entrance = loc(d.getConfigurationSection("entrance"), 0, 0, 0);
        if (entrance == null) return "La entrada de la mazmorra no es válida (¿mundo inexistente?).";
        Run run = new Run(id, d);
        run.endsAt = System.currentTimeMillis() + d.getLong("time_limit_minutes", 20) * 60_000L;
        runs.put(id, run);
        for (Player m : group) {
            run.players.add(m.getUniqueId());
            m.teleport(entrance);
            m.sendTitle("§6" + d.getString("name", id), "§7¡Limpien todas las salas!", 10, 60, 10);
            plugin.getPlayerStore().get(m).dungeonCooldowns.put(id, System.currentTimeMillis() + d.getLong("cooldown_minutes", 45) * 60_000L);
        }
        plugin.getPlayerStore().markDirty();
        Bukkit.getScheduler().runTaskLater(plugin, () -> { if (runs.get(run.id) == run) nextRoom(run); }, 60L);
        return null;
    }

    public void leave(Player p) {
        for (Run r : new ArrayList<>(runs.values())) {
            if (r.players.remove(p.getUniqueId())) {
                Location exit = loc(r.def.getConfigurationSection("exit"), 0, 0, 0);
                if (exit != null) p.teleport(exit);
                p.sendMessage("§eSaliste de la mazmorra.");
                if (r.players.isEmpty()) end(r, false);
            }
        }
    }

    private void nextRoom(Run run) {
        if (runs.get(run.id) != run) return;
        run.room++;
        List<Map<?, ?>> rooms = run.def.getMapList("rooms");
        if (run.room >= rooms.size()) {
            end(run, true);
            return;
        }
        Map<?, ?> room = rooms.get(run.room);
        broadcast(run, "§6Sala " + (run.room + 1) + "/" + rooms.size());
        World w = Bukkit.getWorld(run.def.getString("entrance.world", "world"));
        if (w == null) {
            end(run, false);
            return;
        }
        if (room.get("boss") != null) {
            Location at = new Location(w, num(room, "x"), num(room, "y"), num(room, "z"));
            LivingEntity b = plugin.getBosses().spawn(String.valueOf(room.get("boss")), at);
            if (b == null) {
                broadcast(run, "§cError al invocar al jefe; revisa la configuración.");
                end(run, false);
                return;
            }
            run.boss = true;
            run.bossId = b.getUniqueId();
            return;
        }
        int level = room.get("level") instanceof Number n ? n.intValue() : 10;
        Object spawns = room.get("spawns");
        if (spawns instanceof List<?> list) {
            for (Object o : list) {
                if (!(o instanceof Map<?, ?> sp)) continue;
                EntityType t;
                try { t = EntityType.valueOf(String.valueOf(sp.get("entity")).toUpperCase(Locale.ROOT)); } catch (Exception ex) { continue; }
                if (t.getEntityClass() == null || !LivingEntity.class.isAssignableFrom(t.getEntityClass())) continue;
                int count = sp.get("count") instanceof Number n ? n.intValue() : 3;
                double spread = sp.get("spread") instanceof Number n ? n.doubleValue() : 3;
                for (int i = 0; i < count; i++) {
                    Location at = new Location(w, num(sp, "x") + (ThreadLocalRandom.current().nextDouble() - 0.5) * spread * 2, num(sp, "y"),
                            num(sp, "z") + (ThreadLocalRandom.current().nextDouble() - 0.5) * spread * 2);
                    LivingEntity e = (LivingEntity) w.spawn(at, t.getEntityClass().asSubclass(LivingEntity.class), ent -> {
                        ent.addScoreboardTag(run.tag);
                        ent.setPersistent(false); // never leave orphans behind after a crash / shutdown
                    });
                    e.setRemoveWhenFarAway(false);
                    run.spawned.add(e.getUniqueId());
                    if (plugin.getMobs() != null) plugin.getMobs().apply(e, level);
                }
            }
        }
    }

    private static double num(Map<?, ?> m, String k) {
        return m.get(k) instanceof Number n ? n.doubleValue() : 0;
    }

    private void broadcast(Run run, String msg) {
        for (UUID id : run.players) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) p.sendMessage(msg);
        }
    }

    private void tick() {
        for (Run run : new ArrayList<>(runs.values())) {
            run.players.removeIf(id -> Bukkit.getPlayer(id) == null);
            if (run.players.isEmpty()) {
                end(run, false);
                continue;
            }
            if (System.currentTimeMillis() > run.endsAt) {
                broadcast(run, "§c¡Se acabó el tiempo!");
                end(run, false);
                continue;
            }
            if (run.room < 0 || run.cooldownTicks > 0) {
                if (run.cooldownTicks > 0 && --run.cooldownTicks == 0) nextRoom(run);
                continue;
            }
            boolean cleared;
            if (run.boss) {
                Entity b = run.bossId == null ? null : Bukkit.getEntity(run.bossId);
                cleared = b == null || b.isDead() || !b.isValid();
            } else {
                cleared = true;
                for (UUID id : run.spawned) {
                    Entity e = Bukkit.getEntity(id);
                    if (e != null && !e.isDead() && e.isValid()) { cleared = false; break; }
                }
            }
            if (cleared) {
                run.boss = false;
                run.bossId = null;
                broadcast(run, "§a¡Sala despejada!");
                run.cooldownTicks = 4;
            }
        }
    }

    private void end(Run run, boolean success) {
        if (runs.get(run.id) != run) return;
        runs.remove(run.id);
        for (UUID id : run.spawned) {
            Entity e = Bukkit.getEntity(id);
            if (e != null) e.remove();
        }
        if (run.bossId != null) {
            Entity b = Bukkit.getEntity(run.bossId);
            if (b != null && !b.isDead()) b.remove();
        }
        ConfigurationSection rw = run.def.getConfigurationSection("rewards");
        Location exit = loc(run.def.getConfigurationSection("exit"), 0, 0, 0);
        for (UUID id : new ArrayList<>(run.players)) {
            Player p = Bukkit.getPlayer(id);
            if (p == null) continue;
            if (success && rw != null) reward(p, rw);
            else p.sendMessage("§cMazmorra fallida.");
            if (exit != null) Bukkit.getScheduler().runTaskLater(plugin, () -> p.teleport(exit), success ? 100L : 20L);
        }
        if (success) broadcast(run, "§6§l¡Mazmorra completada! §7Serás teletransportado en 5 segundos.");
        run.players.clear();
    }

    private void reward(Player p, ConfigurationSection rw) {
        List<Double> coins = rw.getDoubleList("coins");
        if (coins.size() == 2) plugin.getEconomy().deposit(p, Math.round(coins.get(0) + ThreadLocalRandom.current().nextDouble() * (coins.get(1) - coins.get(0))));
        long xp = rw.getLong("class_xp", 0);
        if (xp > 0) {
            plugin.getClassLevelManager().addClassXp(p, xp);
            plugin.getClassLevelManager().addSubClassXp(p, xp);
        }
        int pts = rw.getInt("ranking_points", 0);
        PlayerRecord r = plugin.getPlayerStore().get(p);
        if (pts > 0 && !r.classId.isEmpty()) plugin.getRankManager().addPoints(p, RankCategory.CLASS, pts);
        ConfigurationSection g = rw.getConfigurationSection("gear");
        if (g != null) {
            List<Integer> t = g.getIntegerList("tier");
            int lo = t.size() > 0 ? t.get(0) : 2, hi = t.size() > 1 ? t.get(1) : lo;
            for (int i = 0; i < g.getInt("rolls", 1); i++) give(p, plugin.getLoot().gear(p, ThreadLocalRandom.current().nextInt(lo, hi + 1), true));
        }
        ConfigurationSection gm = rw.getConfigurationSection("gems");
        if (gm != null) {
            List<Integer> t = gm.getIntegerList("level");
            int lo = t.size() > 0 ? t.get(0) : 1, hi = t.size() > 1 ? t.get(1) : lo;
            for (int i = 0; i < gm.getInt("rolls", 1); i++) give(p, plugin.getGemManager().randomItem(ThreadLocalRandom.current().nextInt(lo, hi + 1)));
        }
        if (plugin.getAchievements() != null) plugin.getAchievements().add(p, "dungeons", 1);
        plugin.getJobAbilities().questReward(p, "dungeon");
    }

    private void give(Player p, ItemStack it) {
        if (it == null) return;
        for (ItemStack rest : p.getInventory().addItem(it).values()) p.getWorld().dropItemNaturally(p.getLocation(), rest);
    }

    /** Plugin shutdown: remove every dungeon mob and boss. */
    public void shutdown() {
        for (Run r : new ArrayList<>(runs.values())) end(r, false);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        for (Run r : new ArrayList<>(runs.values())) {
            if (r.players.remove(e.getPlayer().getUniqueId()) && r.players.isEmpty()) end(r, false);
        }
    }
}
