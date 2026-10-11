package com.arclinker.rpglevelitems.world;

import com.arclinker.rpglevelitems.DataFiles;
import com.arclinker.rpglevelitems.RPGLevelTier;
import com.arclinker.rpglevelitems.combat.StatusManager;
import com.arclinker.rpglevelitems.rank.RankCategory;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * World bosses (data/bosses/): custom health / damage, scripted abilities, boss bar, damage ranking and
 * shared loot (everybody above min_damage_percent, extra rolls for the top damager).
 */
public final class BossManager implements Listener {
    private final class Live {
        final String id;
        final LivingEntity entity;
        final ConfigurationSection def;
        final BossBar bar;
        final Map<UUID, Double> damage = new HashMap<>();
        final Map<Integer, Integer> counters = new HashMap<>();
        final long spawnedAt = System.currentTimeMillis();
        boolean enraged;
        int tick;

        Live(String id, LivingEntity entity, ConfigurationSection def, BossBar bar) {
            this.id = id;
            this.entity = entity;
            this.def = def;
            this.bar = bar;
        }
    }

    private final RPGLevelTier plugin;
    private YamlConfiguration cfg = new YamlConfiguration();
    private final Map<UUID, Live> live = new HashMap<>();
    private final Map<String, Long> lastSpawn = new HashMap<>();

    public BossManager(RPGLevelTier plugin) {
        this.plugin = plugin;
        reload();
        Bukkit.getPluginManager().registerEvents(this, plugin);
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    public void reload() {
        cfg = DataFiles.loadMerged(plugin, "bosses");
        plugin.getLogger().info("[BossManager] Loaded " + ids().size() + " bosses.");
    }

    public Set<String> ids() {
        ConfigurationSection s = cfg.getConfigurationSection("bosses");
        return s == null ? Set.of() : s.getKeys(false);
    }

    public boolean isBoss(Entity e) {
        return live.containsKey(e.getUniqueId());
    }

    public Collection<String> aliveDescriptions() {
        List<String> out = new ArrayList<>();
        for (Live l : live.values()) {
            Location loc = l.entity.getLocation();
            out.add(l.id + " §7(" + loc.getWorld().getName() + " " + loc.getBlockX() + " " + loc.getBlockY() + " " + loc.getBlockZ() + ") §c" + (int) l.entity.getHealth() + " HP");
        }
        return out;
    }

    // ---- spawning ---------------------------------------------------------------------

    public LivingEntity spawn(String id, Location at) {
        ConfigurationSection def = cfg.getConfigurationSection("bosses." + id);
        if (def == null || at.getWorld() == null) return null;
        EntityType type;
        try { type = EntityType.valueOf(def.getString("entity", "ZOMBIE").toUpperCase(Locale.ROOT)); } catch (IllegalArgumentException e) { return null; }
        if (type.getEntityClass() == null || !LivingEntity.class.isAssignableFrom(type.getEntityClass())) return null;
        // the tag must exist DURING the spawn event so MobScaler does not level-scale the boss
        LivingEntity e = at.getWorld().spawn(at, type.getEntityClass().asSubclass(LivingEntity.class), ent -> ent.addScoreboardTag("rlv_boss"));
        e.setPersistent(false); // never leave orphans in unloaded chunks
        String name = def.getString("name", id);
        e.setCustomName(name);
        e.setCustomNameVisible(true);
        e.setRemoveWhenFarAway(false);
        if (e instanceof Slime s) s.setSize(def.getInt("size", 4));
        int level = def.getInt("level", 20);
        e.getPersistentDataContainer().set(new NamespacedKey(RPGLevelTier.KEY_NS, "rlv_level"), org.bukkit.persistence.PersistentDataType.INTEGER, level);
        AttributeInstance hp = e.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        if (hp != null) {
            hp.setBaseValue(def.getDouble("health", 300));
            e.setHealth(hp.getValue());
        }
        setBase(e, Attribute.GENERIC_ATTACK_DAMAGE, def.getDouble("damage", 8));
        setBase(e, Attribute.GENERIC_ARMOR, def.getDouble("armor", 0));
        AttributeInstance kb = e.getAttribute(Attribute.GENERIC_KNOCKBACK_RESISTANCE);
        if (kb != null) kb.setBaseValue(0.7);
        AttributeInstance sp = e.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED);
        if (sp != null && def.contains("speed")) sp.setBaseValue(def.getDouble("speed"));
        AttributeInstance fr = e.getAttribute(Attribute.GENERIC_FOLLOW_RANGE);
        if (fr != null) fr.setBaseValue(48);
        equip(e, def.getConfigurationSection("equipment"));
        BarColor color;
        try { color = BarColor.valueOf(def.getString("bar_color", "RED").toUpperCase(Locale.ROOT)); } catch (IllegalArgumentException ex) { color = BarColor.RED; }
        BossBar bar = Bukkit.createBossBar(name, color, BarStyle.SEGMENTED_10);
        live.put(e.getUniqueId(), new Live(id, e, def, bar));
        Bukkit.broadcastMessage("§4§l☠ " + name + " §capareció en §f" + at.getWorld().getName() + " " + at.getBlockX() + " " + at.getBlockY() + " " + at.getBlockZ() + "§c. ¡Nivel " + level + "!");
        lastSpawn.put(id, System.currentTimeMillis());
        return e;
    }

    private void setBase(LivingEntity e, Attribute a, double v) {
        AttributeInstance i = e.getAttribute(a);
        if (i != null) i.setBaseValue(v);
    }

    private void equip(LivingEntity e, ConfigurationSection s) {
        if (s == null || e.getEquipment() == null) return;
        var eq = e.getEquipment();
        eq.setItemInMainHand(item(s.getString("mainhand")));
        eq.setHelmet(item(s.getString("helmet")));
        eq.setChestplate(item(s.getString("chestplate")));
        eq.setLeggings(item(s.getString("leggings")));
        eq.setBoots(item(s.getString("boots")));
        eq.setItemInMainHandDropChance(0f);
        eq.setHelmetDropChance(0f);
        eq.setChestplateDropChance(0f);
        eq.setLeggingsDropChance(0f);
        eq.setBootsDropChance(0f);
    }

    private ItemStack item(String spec) {
        if (spec == null) return null;
        ItemStack it = plugin.getRlvpHandler() == null ? null : plugin.getRlvpHandler().getItem(spec);
        if (it != null) return it;
        Material m = Material.matchMaterial(spec);
        return m == null ? null : new ItemStack(m);
    }

    public boolean spawnRandomNearPlayers() {
        List<String> ids = new ArrayList<>(ids());
        List<? extends Player> players = new ArrayList<>(Bukkit.getOnlinePlayers());
        if (ids.isEmpty() || players.isEmpty()) return false;
        Player p = players.get(ThreadLocalRandom.current().nextInt(players.size()));
        Location l = p.getLocation().add(ThreadLocalRandom.current().nextInt(-25, 26), 0, ThreadLocalRandom.current().nextInt(-25, 26));
        l.setY(p.getWorld().getHighestBlockYAt(l) + 1);
        return spawn(ids.get(ThreadLocalRandom.current().nextInt(ids.size())), l) != null;
    }

    public void killAll() {
        for (Live l : new ArrayList<>(live.values())) {
            l.bar.removeAll();
            l.entity.remove();
        }
        live.clear();
    }

    // ---- behavior ---------------------------------------------------------------------

    private void tick() {
        scheduled();
        for (Iterator<Live> it = live.values().iterator(); it.hasNext(); ) {
            Live l = it.next();
            LivingEntity e = l.entity;
            if (e.isDead()) {
                l.bar.removeAll();
                it.remove();
                continue;
            }
            if (!e.isValid()) {
                // removed, or its chunk is unloaded: only forget it once the chunk is loaded and the entity is really gone
                var loc = e.getLocation();
                if (loc.getWorld() != null && loc.getWorld().isChunkLoaded(loc.getBlockX() >> 4, loc.getBlockZ() >> 4)) {
                    l.bar.removeAll();
                    it.remove();
                }
                continue;
            }
            if (System.currentTimeMillis() - l.spawnedAt > plugin.getConfig().getLong("bosses.max_minutes", 30) * 60_000L) {
                Bukkit.broadcastMessage("§7" + l.def.getString("name", l.id) + " §7desapareció en las sombras.");
                l.bar.removeAll();
                e.remove();
                it.remove();
                continue;
            }
            AttributeInstance hp = e.getAttribute(Attribute.GENERIC_MAX_HEALTH);
            double max = hp == null ? 1 : hp.getValue();
            l.bar.setProgress(Math.max(0, Math.min(1, e.getHealth() / max)));
            for (Player p : e.getWorld().getPlayers()) {
                boolean near = p.getLocation().distanceSquared(e.getLocation()) < 70 * 70;
                if (near) l.bar.addPlayer(p); else l.bar.removePlayer(p);
            }
            if (!l.enraged && e.getHealth() / max < l.def.getDouble("enrage_below", 0.0)) enrage(l);
            l.tick++;
            List<Map<?, ?>> abilities = l.def.getMapList("abilities");
            for (int i = 0; i < abilities.size(); i++) {
                Map<?, ?> a = abilities.get(i);
                int every = a.get("every") instanceof Number n ? Math.max(2, n.intValue()) : 15;
                if (l.tick % (l.enraged ? Math.max(2, every * 2 / 3) : every) == 0 && hasTarget(e)) cast(l, a);
            }
        }
    }

    private boolean hasTarget(LivingEntity e) {
        for (Entity n : e.getNearbyEntities(20, 12, 20)) if (n instanceof Player p && p.getGameMode() != GameMode.SPECTATOR && p.getGameMode() != GameMode.CREATIVE) return true;
        return false;
    }

    private void enrage(Live l) {
        l.enraged = true;
        LivingEntity e = l.entity;
        Bukkit.broadcastMessage("§c" + l.def.getString("name", l.id) + " §centra en §4§lFURIA§c!");
        e.getWorld().playSound(e.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 2f, 0.8f);
        AttributeInstance dmg = e.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE);
        if (dmg != null) dmg.addModifier(new AttributeModifier(new NamespacedKey(RPGLevelTier.KEY_NS, "boss_enrage_dmg"), 0.4, AttributeModifier.Operation.ADD_SCALAR, EquipmentSlotGroup.ANY));
        AttributeInstance sp = e.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED);
        if (sp != null) sp.addModifier(new AttributeModifier(new NamespacedKey(RPGLevelTier.KEY_NS, "boss_enrage_spd"), 0.25, AttributeModifier.Operation.ADD_SCALAR, EquipmentSlotGroup.ANY));
    }

    private List<Player> around(LivingEntity e, double r) {
        List<Player> out = new ArrayList<>();
        for (Entity n : e.getNearbyEntities(r, r, r)) if (n instanceof Player p && p.getGameMode() != GameMode.SPECTATOR && p.getGameMode() != GameMode.CREATIVE && !p.isDead()) out.add(p);
        return out;
    }

    private double num(Map<?, ?> a, String k, double def) {
        return a.get(k) instanceof Number n ? n.doubleValue() : def;
    }

    private void cast(Live l, Map<?, ?> a) {
        LivingEntity e = l.entity;
        String type = String.valueOf(a.get("type"));
        Location loc = e.getLocation();
        double scale = l.enraged ? 1.3 : 1.0;
        switch (type) {
            case "slam" -> {
                double r = num(a, "radius", 6);
                loc.getWorld().spawnParticle(Particle.EXPLOSION, loc, 3, r / 3, 0.3, r / 3);
                loc.getWorld().playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 1.5f, 0.7f);
                for (Player p : around(e, r)) {
                    p.damage(num(a, "damage", 8) * scale, e);
                    Vector v = p.getLocation().toVector().subtract(loc.toVector()).setY(0);
                    if (v.lengthSquared() > 0.01) p.setVelocity(v.normalize().multiply(1.1).setY(0.45));
                }
            }
            case "summon" -> {
                EntityType t;
                try { t = EntityType.valueOf(String.valueOf(a.get("entity")).toUpperCase(Locale.ROOT)); } catch (Exception ex) { return; }
                if (t.getEntityClass() == null) return;
                int n = (int) num(a, "count", 3);
                int existing = 0;
                for (Entity x : e.getNearbyEntities(25, 15, 25)) if (x.getScoreboardTags().contains("rlv_minion")) existing++;
                if (existing > 12) return;
                for (int i = 0; i < n; i++) {
                    Location s = loc.clone().add(ThreadLocalRandom.current().nextInt(-4, 5), 0, ThreadLocalRandom.current().nextInt(-4, 5));
                    Entity m = loc.getWorld().spawn(s, t.getEntityClass().asSubclass(Entity.class), ent -> {
                        ent.addScoreboardTag("rlv_minion");
                        ent.setPersistent(false);
                    });
                }
                loc.getWorld().spawnParticle(Particle.SOUL, loc, 30, 1.5, 1, 1.5, 0.02);
            }
            case "leap" -> {
                List<Player> ps = around(e, 18);
                if (ps.isEmpty()) return;
                Player t = ps.get(ThreadLocalRandom.current().nextInt(ps.size()));
                Vector v = t.getLocation().toVector().subtract(loc.toVector());
                e.setVelocity(v.setY(Math.max(0.6, v.getY() * 0.1 + 0.9)).normalize().multiply(1.3).setY(0.9));
                double dmg = num(a, "damage", 8) * scale, rad = num(a, "radius", 4);
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (e.isDead()) return;
                    e.getWorld().spawnParticle(Particle.EXPLOSION, e.getLocation(), 2, 1, 0.2, 1);
                    for (Player p : around(e, rad)) p.damage(dmg, e);
                }, 20L);
            }
            case "fire_ring" -> {
                double r = num(a, "radius", 8);
                loc.getWorld().spawnParticle(Particle.FLAME, loc, 80, r / 2, 0.4, r / 2, 0.05);
                for (Player p : around(e, r)) {
                    p.setFireTicks((int) (num(a, "seconds", 4) * 20));
                    plugin.getStatuses().apply(p, StatusManager.Status.BURN, num(a, "seconds", 4), 2, null);
                }
            }
            case "lightning" -> {
                List<Player> ps = around(e, 25);
                if (ps.isEmpty()) return;
                Player t = ps.get(ThreadLocalRandom.current().nextInt(ps.size()));
                t.getWorld().strikeLightningEffect(t.getLocation());
                t.damage(num(a, "damage", 10) * scale, e);
            }
            case "pull" -> {
                for (Player p : around(e, num(a, "radius", 9))) {
                    Vector v = loc.toVector().subtract(p.getLocation().toVector());
                    p.setVelocity(v.normalize().multiply(1.0).setY(0.3));
                }
                loc.getWorld().playSound(loc, Sound.ENTITY_EVOKER_CAST_SPELL, 1.5f, 0.6f);
            }
            case "poison_cloud" -> {
                double r = num(a, "radius", 7);
                loc.getWorld().spawnParticle(Particle.SNEEZE, loc, 80, r / 2, 0.5, r / 2, 0.02);
                for (Player p : around(e, r)) plugin.getStatuses().apply(p, StatusManager.Status.POISON, num(a, "seconds", 6), 2, null);
            }
            case "stun" -> {
                for (Player p : around(e, num(a, "radius", 5))) plugin.getStatuses().apply(p, StatusManager.Status.STUN, num(a, "seconds", 2), 1, null);
                loc.getWorld().playSound(loc, Sound.ENTITY_IRON_GOLEM_ATTACK, 1.5f, 0.5f);
            }
            case "heal" -> {
                AttributeInstance hp = e.getAttribute(Attribute.GENERIC_MAX_HEALTH);
                double max = hp == null ? 100 : hp.getValue();
                e.setHealth(Math.min(max, e.getHealth() + max * num(a, "percent", 4) / 100.0));
                loc.getWorld().spawnParticle(Particle.HEART, loc.clone().add(0, e.getHeight(), 0), 10, 0.6, 0.4, 0.6);
            }
            default -> { }
        }
    }

    // ---- scheduled spawns --------------------------------------------------------------

    private void scheduled() {
        ConfigurationSection root = plugin.getConfig().getConfigurationSection("bosses.schedule");
        if (root == null || Bukkit.getOnlinePlayers().isEmpty()) return;
        long now = System.currentTimeMillis();
        for (String key : root.getKeys(false)) {
            ConfigurationSection s = root.getConfigurationSection(key);
            if (s == null) continue;
            String boss = s.getString("boss", "");
            long every = Math.max(1, s.getLong("interval_minutes", 120)) * 60_000L;
            String k = "sched:" + key;
            Long last = lastSpawn.get(k);
            if (last == null) { // first run after start: spawn two minutes from now
                lastSpawn.put(k, now - every + 120_000L);
                continue;
            }
            if (now - last < every) continue;
            boolean alive = false;
            for (Live l : live.values()) if (l.id.equals(boss)) alive = true;
            World w = Bukkit.getWorld(s.getString("world", "world"));
            if (alive || w == null || !ids().contains(boss)) {
                lastSpawn.put(k, now);
                continue;
            }
            Location at = new Location(w, s.getDouble("x"), s.getDouble("y", 70), s.getDouble("z"));
            spawn(boss, at);
            lastSpawn.put(k, now);
        }
    }

    // ---- damage and death ---------------------------------------------------------------

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent e) {
        Live l = live.get(e.getEntity().getUniqueId());
        if (l == null) return;
        Entity d = e.getDamager();
        if (d instanceof Projectile pr && pr.getShooter() instanceof Entity s) d = s;
        if (d instanceof Player p) l.damage.merge(p.getUniqueId(), e.getFinalDamage(), Double::sum);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDeath(EntityDeathEvent e) {
        Live l = live.remove(e.getEntity().getUniqueId());
        if (l == null) return;
        l.bar.removeAll();
        e.getDrops().clear();
        e.setDroppedExp(e.getDroppedExp() * 3);
        reward(l);
    }

    private void reward(Live l) {
        ConfigurationSection rw = l.def.getConfigurationSection("rewards");
        String name = l.def.getString("name", l.id);
        double total = l.damage.values().stream().mapToDouble(Double::doubleValue).sum();
        Bukkit.broadcastMessage("§6☠ " + name + " §6fue derrotado.");
        Player topP = null;
        double topD = 0;
        for (Map.Entry<UUID, Double> en : l.damage.entrySet()) if (en.getValue() > topD) { topD = en.getValue(); topP = Bukkit.getPlayer(en.getKey()); }
        Bukkit.getPluginManager().callEvent(new com.arclinker.rpglevelitems.api.events.BossKillEvent(l.id, topP));
        if (total <= 0 || rw == null) return;
        List<Map.Entry<UUID, Double>> ranking = new ArrayList<>(l.damage.entrySet());
        ranking.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));
        double minPct = rw.getDouble("min_damage_percent", 5);
        int pos = 0;
        for (Map.Entry<UUID, Double> en : ranking) {
            pos++;
            Player p = Bukkit.getPlayer(en.getKey());
            double pct = en.getValue() / total * 100.0;
            if (pos <= 3) Bukkit.broadcastMessage("§e  #" + pos + " §f" + (p == null ? "?" : p.getName()) + " §7- " + String.format(Locale.ROOT, "%.0f%%", pct));
            if (p == null || pct < minPct) continue;
            give(p, rw, pos == 1, l);
        }
    }

    private void give(Player p, ConfigurationSection rw, boolean top, Live l) {
        List<Double> coins = rw.getDoubleList("coins");
        if (coins.size() == 2) plugin.getEconomy().deposit(p, Math.round(coins.get(0) + ThreadLocalRandom.current().nextDouble() * (coins.get(1) - coins.get(0))));
        long xp = rw.getLong("class_xp", 0);
        if (xp > 0) {
            plugin.getClassLevelManager().addClassXp(p, xp);
            plugin.getClassLevelManager().addSubClassXp(p, xp);
        }
        int pts = rw.getInt("ranking_points", 0);
        if (pts > 0) {
            if (!plugin.getPlayerStore().get(p).classId.isEmpty()) plugin.getRankManager().addPoints(p, RankCategory.CLASS, pts);
            if (!plugin.getPlayerStore().get(p).subclassId.isEmpty()) plugin.getRankManager().addPoints(p, RankCategory.SUBCLASS, pts);
        }
        for (Map<?, ?> m : rw.getMapList("guaranteed")) deliver(p, m);
        if (top) for (Map<?, ?> m : rw.getMapList("top_rolls")) deliver(p, m);
        if (plugin.getAchievements() != null) plugin.getAchievements().add(p, "world_bosses", 1);
        plugin.getJobAbilities().questReward(p, "boss");
        p.sendMessage("§6Recibiste tu botín de §f" + l.def.getString("name", l.id) + (top ? " §e(¡mayor daño!)" : "") + "§6.");
    }

    private void deliver(Player p, Map<?, ?> m) {
        String type = String.valueOf(m.get("type"));
        ItemStack it = null;
        switch (type) {
            case "gear" -> {
                int[] t = pair(m.get("tier"), 3, 4);
                it = plugin.getLoot().gear(p, rnd(t[0], t[1]), true);
            }
            case "gem" -> {
                int[] t = pair(m.get("level"), 1, 2);
                it = plugin.getGemManager().randomItem(rnd(t[0], t[1]));
            }
            case "material" -> {
                it = plugin.getRlvpHandler().getItem(String.valueOf(m.get("id")));
                int[] a = pair(m.get("amount"), 1, 1);
                if (it != null) it.setAmount(rnd(a[0], a[1]));
            }
            default -> { }
        }
        if (it == null) return;
        for (ItemStack rest : p.getInventory().addItem(it).values()) p.getWorld().dropItemNaturally(p.getLocation(), rest);
    }

    private static int[] pair(Object o, int dMin, int dMax) {
        if (o instanceof List<?> l && l.size() >= 2 && l.get(0) instanceof Number a && l.get(1) instanceof Number b) return new int[]{a.intValue(), b.intValue()};
        return new int[]{dMin, dMax};
    }

    private static int rnd(int a, int b) {
        return b <= a ? a : ThreadLocalRandom.current().nextInt(a, b + 1);
    }
}
