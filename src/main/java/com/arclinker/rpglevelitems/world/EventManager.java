package com.arclinker.rpglevelitems.world;

import com.arclinker.rpglevelitems.RPGLevelTier;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Temporary server events (config {@code events.*}): double_xp, double_drops, blood_moon, invasion, boss_night.
 * One event at a time; they start randomly (checked every few minutes) or with /rlv event start.
 */
public final class EventManager implements Listener {
    private record Def(String id, String name, int minutes, double xp, double drops, int mobLevel, String type, String bar) {}

    private final RPGLevelTier plugin;
    private final Map<String, Def> defs = new LinkedHashMap<>();
    private Def current;
    private long endsAt;
    private BossBar bar;
    private int invasionTick;
    private long nextCheck = System.currentTimeMillis() + 5 * 60_000L;

    public EventManager(RPGLevelTier plugin) {
        this.plugin = plugin;
        reload();
        Bukkit.getPluginManager().registerEvents(this, plugin);
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    public void reload() {
        defs.clear();
        ConfigurationSection root = plugin.getConfig().getConfigurationSection("events.types");
        if (root == null) return;
        for (String id : root.getKeys(false)) {
            ConfigurationSection s = root.getConfigurationSection(id);
            if (s == null) continue;
            defs.put(id, new Def(id, s.getString("name", id), s.getInt("duration_minutes", 30), s.getDouble("xp", 1.0), s.getDouble("drops", 1.0),
                    s.getInt("mob_level_bonus", 0), s.getString("kind", ""), s.getString("bar_color", "YELLOW")));
        }
    }

    public Collection<String> ids() {
        return defs.keySet();
    }

    public boolean active() {
        return current != null;
    }

    public String activeName() {
        return current == null ? null : current.name();
    }

    public double xpMultiplier() {
        return current == null ? 1.0 : current.xp();
    }

    public double dropMultiplier() {
        return current == null ? 1.0 : current.drops();
    }

    public int mobLevelBonus() {
        return current == null ? 0 : current.mobLevel();
    }

    public boolean start(String id) {
        Def d = defs.get(id);
        if (d == null) return false;
        stop();
        current = d;
        endsAt = System.currentTimeMillis() + d.minutes() * 60_000L;
        BarColor color;
        try { color = BarColor.valueOf(d.bar().toUpperCase(Locale.ROOT)); } catch (IllegalArgumentException e) { color = BarColor.YELLOW; }
        bar = Bukkit.createBossBar("§6§l" + d.name(), color, BarStyle.SOLID);
        for (Player p : Bukkit.getOnlinePlayers()) bar.addPlayer(p);
        Bukkit.broadcastMessage("§6§l✦ EVENTO: " + d.name() + " §e(" + d.minutes() + " min)");
        if (d.type().equals("boss_night") && plugin.getBosses() != null) plugin.getBosses().spawnRandomNearPlayers();
        return true;
    }

    public void stop() {
        if (current != null) Bukkit.broadcastMessage("§e✦ El evento §f" + current.name() + " §eterminó.");
        current = null;
        if (bar != null) {
            bar.removeAll();
            bar = null;
        }
    }

    private void tick() {
        if (!plugin.getConfig().getBoolean("events.enabled", true)) return;
        long now = System.currentTimeMillis();
        if (current != null) {
            if (now >= endsAt) {
                stop();
                return;
            }
            double left = (endsAt - now) / (current.minutes() * 60_000.0);
            if (bar != null) bar.setProgress(Math.max(0, Math.min(1, left)));
            if (current.type().equals("invasion") && ++invasionTick % 30 == 0) invade();
            return;
        }
        long every = Math.max(1, plugin.getConfig().getInt("events.check_interval_minutes", 15)) * 60_000L;
        if (now < nextCheck) return;
        nextCheck = now + every;
        if (!defs.isEmpty() && ThreadLocalRandom.current().nextDouble() < plugin.getConfig().getDouble("events.random_chance", 0.2)
                && !Bukkit.getOnlinePlayers().isEmpty()) {
            List<String> ids = new ArrayList<>(defs.keySet());
            start(ids.get(ThreadLocalRandom.current().nextInt(ids.size())));
        }
    }

    /** Invasion: groups of hostile mobs appear around each player. */
    private void invade() {
        EntityType[] pool = {EntityType.ZOMBIE, EntityType.SKELETON, EntityType.SPIDER, EntityType.CREEPER, EntityType.HUSK};
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getWorld().getEnvironment() != org.bukkit.World.Environment.NORMAL) continue;
            int n = 3 + ThreadLocalRandom.current().nextInt(3);
            for (int i = 0; i < n; i++) {
                double ang = ThreadLocalRandom.current().nextDouble() * Math.PI * 2, r = 10 + ThreadLocalRandom.current().nextDouble() * 6;
                Location l = p.getLocation().add(Math.cos(ang) * r, 0, Math.sin(ang) * r);
                l.setY(p.getWorld().getHighestBlockYAt(l) + 1);
                p.getWorld().spawn(l, pool[ThreadLocalRandom.current().nextInt(pool.length)].getEntityClass().asSubclass(LivingEntity.class),
                        ent -> ent.addScoreboardTag("rlv_invasion"));
            }
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        if (bar != null) bar.addPlayer(e.getPlayer());
    }
}
