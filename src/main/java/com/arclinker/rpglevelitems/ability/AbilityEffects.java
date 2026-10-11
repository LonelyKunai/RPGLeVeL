package com.arclinker.rpglevelitems.ability;

import com.arclinker.rpglevelitems.RPGLevelTier;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.*;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Executes ability effects. Types: bolt, aoe, heal, buff, dash, blink, slam, lightning, chain,
 * drain, volley. Numeric params accept {@code <name>_per_level}.
 */
public final class AbilityEffects {
    private static final Set<String> NEGATIVE = Set.of("slowness", "mining_fatigue", "nausea", "blindness", "hunger", "weakness",
            "poison", "wither", "levitation", "unluck", "darkness", "glowing");

    private final RPGLevelTier plugin;
    private final AbilityManager manager;

    AbilityEffects(RPGLevelTier plugin, AbilityManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    // ---- param helpers -------------------------------------------------------------

    static double num(Map<String, Object> e, String key, int level, double def) {
        Object base = e.get(key);
        double v = base instanceof Number n ? n.doubleValue() : def;
        Object per = e.get(key + "_per_level");
        if (per instanceof Number n) v += n.doubleValue() * (Math.max(1, level) - 1);
        return v;
    }

    private static boolean flag(Map<String, Object> e, String key) {
        return e.get(key) instanceof Boolean b && b;
    }

    private static String str(Map<String, Object> e, String key) {
        Object o = e.get(key);
        return o == null ? null : o.toString();
    }

    @SuppressWarnings("unchecked")
    private static List<String> potions(Map<String, Object> e) {
        Object o = e.get("potions");
        return o instanceof List<?> l ? (List<String>) l : List.of();
    }

    private void applyPotions(Map<String, Object> e, int level, LivingEntity target) {
        double extra = num(e, "duration", level, 0) - 0; // duration_per_level adds seconds per ability level
        double perLevel = e.get("duration_per_level") instanceof Number n ? n.doubleValue() * (Math.max(1, level) - 1) : 0;
        for (String spec : potions(e)) {
            String[] parts = spec.split(":");
            PotionEffectType type = Registry.EFFECT.get(NamespacedKey.minecraft(parts[0].toLowerCase(Locale.ROOT)));
            if (type == null) continue;
            double seconds = (parts.length > 1 ? parseD(parts[1], 5) : 5) + perLevel;
            int amp = parts.length > 2 ? (int) parseD(parts[2], 0) : 0;
            target.addPotionEffect(new PotionEffect(type, (int) Math.max(20, seconds * 20), amp, true, true, true));
        }
    }

    private static double parseD(String s, double def) {
        try { return Double.parseDouble(s); } catch (NumberFormatException ex) { return def; }
    }

    private static Particle particle(String name, Particle def) {
        if (name == null) return def;
        try {
            Particle p = Particle.valueOf(name.toUpperCase(Locale.ROOT));
            return p.getDataType() == Void.class ? p : def;
        } catch (IllegalArgumentException ex) {
            return def;
        }
    }

    private static void sound(Location at, String name, float pitch) {
        if (name == null) return;
        try {
            at.getWorld().playSound(at, Sound.valueOf(name.toUpperCase(Locale.ROOT)), 0.9f, pitch);
        } catch (IllegalArgumentException ignored) { }
    }

    // ---- targeting -----------------------------------------------------------------

    private boolean validTarget(Player caster, Entity e) {
        if (!(e instanceof LivingEntity le) || e == caster || le.isDead() || le.isInvulnerable()) return false;
        if (e instanceof ArmorStand || e instanceof Villager || e instanceof WanderingTrader) return false;
        if (e instanceof Player tp && !(manager.pvp || plugin.getZones().pvpAllowed(caster, tp))) return false;
        if (e instanceof Tameable t && t.isTamed()) return false;
        return true;
    }

    /** Effects being cast (innermost on top): statuses applied by hurt() come from the active one; re-entrant safe. */
    private final Deque<Object[]> ctx = new ArrayDeque<>();

    private void pushCtx(Map<String, Object> e, int level) {
        ctx.push(new Object[]{e, level});
    }

    private void popCtx() {
        if (!ctx.isEmpty()) ctx.pop();
    }

    private void hurt(Player caster, LivingEntity target, double amount) {
        if (amount <= 0) return;
        var combat = plugin.getCombat();
        double dealt = combat.outgoing(caster, target, amount, true).damage();
        double before = target.getHealth();
        manager.setCasting(caster, true);
        try {
            target.damage(dealt, caster); // shock multiplier is applied by CombatEngine
        } finally {
            manager.setCasting(caster, false);
        }
        double applied = before - target.getHealth();
        if (applied > 0 || target.isDead()) {
            combat.afterHit(caster, target, Math.max(applied, 0));
            applyStatuses(caster, target);
        }
    }

    /** statuses: ["bleed:5:1"] = status : seconds : power (seconds grow 0.25 per ability level). */
    private void applyStatuses(Player caster, LivingEntity target) {
        Object[] top = ctx.peek();
        if (top == null) return;
        @SuppressWarnings("unchecked") Map<String, Object> curEffect = (Map<String, Object>) top[0];
        int curLevel = (Integer) top[1];
        if (!(curEffect.get("statuses") instanceof List<?> list)) return;
        for (Object o : list) {
            String[] p = String.valueOf(o).split(":");
            var st = com.arclinker.rpglevelitems.combat.StatusManager.Status.parse(p[0]);
            if (st == null) continue;
            double secs = (p.length > 1 ? parseD(p[1], 4) : 4) + 0.25 * (Math.max(1, curLevel) - 1);
            double power = p.length > 2 ? parseD(p[2], 1) : 1;
            plugin.getStatuses().apply(target, st, secs, power, caster);
        }
    }

    /** First valid entity in the player's line of sight (blocks stop the ray). */
    private LivingEntity rayTarget(Player p, double range) {
        Location eye = p.getEyeLocation();
        World w = p.getWorld();
        RayTraceResult entityHit = w.rayTraceEntities(eye, eye.getDirection(), range, 0.7, e -> validTarget(p, e));
        if (entityHit == null || !(entityHit.getHitEntity() instanceof LivingEntity le)) return null;
        RayTraceResult blockHit = w.rayTraceBlocks(eye, eye.getDirection(), range, FluidCollisionMode.NEVER, true);
        if (blockHit != null && blockHit.getHitPosition().distance(eye.toVector()) < entityHit.getHitPosition().distance(eye.toVector())) return null;
        return le;
    }

    /** Point the player is aiming at (entity, block or end of range). */
    private Location aimPoint(Player p, double range) {
        Location eye = p.getEyeLocation();
        LivingEntity t = rayTarget(p, range);
        if (t != null) return t.getLocation();
        RayTraceResult blockHit = p.getWorld().rayTraceBlocks(eye, eye.getDirection(), range, FluidCollisionMode.NEVER, true);
        if (blockHit != null) return blockHit.getHitPosition().toLocation(p.getWorld());
        return eye.clone().add(eye.getDirection().multiply(range));
    }

    private List<LivingEntity> enemiesAround(Player caster, Location center, double radius) {
        List<LivingEntity> out = new ArrayList<>();
        for (Entity e : center.getWorld().getNearbyEntities(center, radius, radius, radius)) {
            if (validTarget(caster, e) && e.getLocation().distanceSquared(center) <= (radius + 0.5) * (radius + 0.5)) out.add((LivingEntity) e);
        }
        return out;
    }

    private List<Player> alliesAround(Player caster, double radius) {
        List<Player> out = new ArrayList<>();
        out.add(caster);
        if (radius <= 0) return out;
        for (Entity e : caster.getNearbyEntities(radius, radius, radius)) {
            if (e instanceof Player pl && !pl.isDead()) out.add(pl);
        }
        return out;
    }

    private void line(Location from, Location to, Particle particle) {
        Vector dir = to.toVector().subtract(from.toVector());
        double len = dir.length();
        if (len < 0.1) return;
        dir.normalize();
        for (double d = 0; d < len; d += 0.6) {
            from.getWorld().spawnParticle(particle, from.clone().add(dir.clone().multiply(d)), 1, 0, 0, 0, 0);
        }
    }

    private void burst(Location c, Particle particle, double radius) {
        c.getWorld().spawnParticle(particle, c.clone().add(0, 1, 0), (int) Math.min(80, 10 + radius * 6), radius / 2, 0.5, radius / 2, 0.02);
    }

    // ---- entry point ---------------------------------------------------------------

    /**
     * Casts an effect. {@code target} (optional) is used by bolt/lightning/chain/drain instead of aiming.
     * @return false if the ability could not be used (e.g. no target), so no cooldown is consumed
     */
    public boolean cast(Player p, Map<String, Object> e, int level, LivingEntity target) {
        return cast(p, e, level, target, true);
    }

    /** @param notify show "no target" feedback (false for automatic procs) */
    public boolean cast(Player p, Map<String, Object> e, int level, LivingEntity target, boolean notify) {
        String type = str(e, "type");
        if (type == null) return false;
        Particle particle = particle(str(e, "particle"), Particle.CRIT);
        String snd = str(e, "sound");
        Location origin = p.getLocation();
        boolean ok;
        pushCtx(e, level);
        try {
            ok = dispatch(type, p, e, level, target, particle);
        } finally {
            popCtx();
        }
        if (!ok) {
            if (notify && target == null) com.arclinker.rpglevelitems.Util.actionBar(p, plugin.getLanguageManager().tr(p, "ability.no_target"));
            return false;
        }
        sound(origin, snd, 1.0f);
        double self = num(e, "self_damage", level, 0);
        if (self > 0) p.setHealth(Math.max(1.0, p.getHealth() - self));
        return true;
    }

    private boolean dispatch(String type, Player p, Map<String, Object> e, int level, LivingEntity target, Particle particle) {
        return switch (type) {
            case "bolt" -> bolt(p, e, level, target, particle);
            case "aoe" -> aoe(p, e, level, particle);
            case "heal" -> heal(p, e, level, particle);
            case "buff" -> buff(p, e, level, particle);
            case "dash" -> dash(p, e, level, particle);
            case "blink" -> blink(p, e, level, particle);
            case "slam" -> slam(p, e, level, particle);
            case "lightning" -> lightning(p, e, level, target);
            case "chain" -> chain(p, e, level, target, particle);
            case "drain" -> drain(p, e, level, target, particle);
            case "volley" -> volley(p, e, level);
            default -> false;
        };
    }

    // ---- effects -------------------------------------------------------------------

    private double damageAgainst(Map<String, Object> e, int level, LivingEntity t) {
        double dmg = num(e, "damage", level, 0);
        double below = num(e, "execute_below", level, 0);
        if (below > 0 && dmg > 0) {
            AttributeInstance max = t.getAttribute(Attribute.GENERIC_MAX_HEALTH);
            double maxHp = max != null ? max.getValue() : 20;
            if (t.getHealth() / maxHp <= below) dmg *= num(e, "execute_mult", level, 2.0);
        }
        return dmg;
    }

    private void knock(Player p, LivingEntity t, Location from, double power) {
        if (power <= 0) return;
        Vector v = t.getLocation().toVector().subtract(from.toVector());
        if (v.lengthSquared() < 0.01) v = p.getLocation().getDirection();
        t.setVelocity(v.normalize().multiply(power).setY(0.35));
    }

    private boolean bolt(Player p, Map<String, Object> e, int level, LivingEntity forced, Particle particle) {
        double range = num(e, "range", level, 20);
        LivingEntity t = forced != null ? forced : rayTarget(p, range);
        if (t == null) return false;
        Location eye = p.getEyeLocation();
        line(eye, t.getEyeLocation(), particle);
        hurt(p, t, damageAgainst(e, level, t));
        double fire = num(e, "fire_seconds", level, 0);
        if (fire > 0) t.setFireTicks((int) (fire * 20));
        applyPotions(e, level, t);
        knock(p, t, p.getLocation(), num(e, "knockback", level, 0));
        t.getWorld().spawnParticle(particle, t.getLocation().add(0, 1, 0), 12, 0.3, 0.4, 0.3, 0.05);
        return true;
    }

    private boolean aoe(Player p, Map<String, Object> e, int level, Particle particle) {
        double radius = num(e, "radius", level, 4);
        Location center = flag(e, "at_target") ? aimPoint(p, num(e, "range", level, 20)) : p.getLocation();
        burst(center, particle, radius);
        if (flag(e, "lightning")) center.getWorld().strikeLightningEffect(center);
        double fire = num(e, "fire_seconds", level, 0);
        for (LivingEntity t : enemiesAround(p, center, radius)) {
            hurt(p, t, damageAgainst(e, level, t));
            if (fire > 0) t.setFireTicks((int) (fire * 20));
            applyPotions(e, level, t);
            knock(p, t, center, num(e, "knockback", level, 0));
        }
        return true;
    }

    private void heal(LivingEntity target, double amount) {
        AttributeInstance max = target.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        double maxHp = max != null ? max.getValue() : 20;
        target.setHealth(Math.min(maxHp, target.getHealth() + amount));
    }

    private boolean heal(Player p, Map<String, Object> e, int level, Particle particle) {
        double amount = num(e, "amount", level, 4);
        boolean cleanse = flag(e, "cleanse");
        for (Player ally : alliesAround(p, num(e, "radius", level, 0))) {
            heal(ally, amount);
            if (cleanse) {
                for (PotionEffect pe : new ArrayList<>(ally.getActivePotionEffects())) {
                    var key = pe.getType().getKey();
                    if (key != null && NEGATIVE.contains(key.getKey())) ally.removePotionEffect(pe.getType());
                }
            }
            applyPotions(e, level, ally);
            ally.getWorld().spawnParticle(particle, ally.getLocation().add(0, 1.2, 0), 8, 0.4, 0.5, 0.4, 0.02);
        }
        return true;
    }

    private boolean buff(Player p, Map<String, Object> e, int level, Particle particle) {
        for (Player ally : alliesAround(p, num(e, "radius", level, 0))) {
            applyPotions(e, level, ally);
            ally.getWorld().spawnParticle(particle, ally.getLocation().add(0, 1.2, 0), 14, 0.5, 0.7, 0.5, 0.03);
        }
        return true;
    }

    private boolean dash(Player p, Map<String, Object> e, int level, Particle particle) {
        Vector dir = p.getEyeLocation().getDirection();
        if (flag(e, "backward")) dir.multiply(-1);
        Vector v = dir.setY(0);
        if (v.lengthSquared() < 0.001) v = new Vector(0, 0, 1);
        v.normalize().multiply(num(e, "power", level, 1.5)).setY(num(e, "up", level, 0.2));
        p.setVelocity(v);
        applyPotions(e, level, p);
        double dmg = num(e, "damage", level, 0), radius = num(e, "radius", level, 0);
        if (dmg > 0 && radius > 0) {
            Set<UUID> hit = new HashSet<>();
            new BukkitRunnable() {
                int ticks = 0;
                @Override
                public void run() {
                    if (!p.isOnline() || ticks++ > 10) { cancel(); return; }
                    p.getWorld().spawnParticle(particle, p.getLocation().add(0, 1, 0), 6, 0.3, 0.3, 0.3, 0.02);
                    for (LivingEntity t : enemiesAround(p, p.getLocation(), radius)) {
                        if (hit.add(t.getUniqueId())) {
                            pushCtx(e, level);
                            try {
                                hurt(p, t, dmg);
                            } finally {
                                popCtx();
                            }
                            knock(p, t, p.getLocation(), 0.5);
                        }
                    }
                }
            }.runTaskTimer(plugin, 1L, 1L);
        }
        return true;
    }

    private boolean blink(Player p, Map<String, Object> e, int level, Particle particle) {
        double dist = num(e, "distance", level, 8);
        Location eye = p.getEyeLocation();
        Vector dir = eye.getDirection();
        RayTraceResult hit = p.getWorld().rayTraceBlocks(eye, dir, dist, FluidCollisionMode.NEVER, true);
        double travel = hit == null ? dist : Math.max(0, hit.getHitPosition().distance(eye.toVector()) - 0.8);
        Location dest = p.getLocation().clone().add(dir.clone().multiply(travel));
        for (int i = 0; i < 4 && (!dest.getBlock().isPassable() || !dest.clone().add(0, 1, 0).getBlock().isPassable()); i++) {
            dest.add(0, 1, 0);
        }
        if (!dest.getBlock().isPassable()) return false;
        Location from = p.getLocation();
        p.getWorld().spawnParticle(particle, from.clone().add(0, 1, 0), 25, 0.3, 0.6, 0.3, 0.1);
        p.teleport(dest);
        p.getWorld().spawnParticle(particle, dest.clone().add(0, 1, 0), 25, 0.3, 0.6, 0.3, 0.1);
        p.setFallDistance(0);
        applyPotions(e, level, p);
        return true;
    }

    private boolean slam(Player p, Map<String, Object> e, int level, Particle particle) {
        Vector dir = p.getEyeLocation().getDirection().setY(0);
        if (dir.lengthSquared() > 0.001) dir.normalize().multiply(0.3);
        p.setVelocity(dir.setY(num(e, "up", level, 0.9)));
        int delay = (int) num(e, "delay", level, 10);
        new BukkitRunnable() {
            int ticks = 0;
            @Override
            public void run() {
                if (!p.isOnline() || p.isDead()) { cancel(); return; }
                ticks++;
                if (ticks == delay) p.setVelocity(new Vector(0, -1.4, 0));
                boolean landed = ticks > delay && p.getLocation().subtract(0, 0.3, 0).getBlock().getType().isSolid();
                if (landed || ticks > delay + 40) {
                    cancel();
                    Location c = p.getLocation();
                    p.setFallDistance(0);
                    burst(c, particle, num(e, "radius", level, 5));
                    sound(c, str(e, "sound"), 0.8f);
                    pushCtx(e, level);
                    try {
                        for (LivingEntity t : enemiesAround(p, c, num(e, "radius", level, 5))) {
                            hurt(p, t, num(e, "damage", level, 6));
                            applyPotions(e, level, t);
                            knock(p, t, c, num(e, "knockback", level, 1));
                        }
                    } finally {
                        popCtx();
                    }
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
        return true;
    }

    private boolean lightning(Player p, Map<String, Object> e, int level, LivingEntity forced) {
        LivingEntity t = forced != null ? forced : rayTarget(p, num(e, "range", level, 20));
        if (t == null) return false;
        Location at = t.getLocation();
        at.getWorld().strikeLightningEffect(at);
        double dmg = num(e, "damage", level, 8);
        hurt(p, t, damageAgainst(e, level, t));
        double splash = num(e, "radius", level, 0);
        if (splash > 0) {
            for (LivingEntity o : enemiesAround(p, at, splash)) if (o != t) hurt(p, o, dmg * 0.5);
        }
        applyPotions(e, level, t);
        return true;
    }

    private boolean chain(Player p, Map<String, Object> e, int level, LivingEntity forced, Particle particle) {
        LivingEntity first = forced != null ? forced : rayTarget(p, num(e, "range", level, 8) + 12);
        if (first == null) return false;
        double dmg = num(e, "damage", level, 6), falloff = num(e, "falloff", level, 0.9), jump = num(e, "range", level, 8);
        int jumps = (int) num(e, "targets", level, 4);
        double fire = num(e, "fire_seconds", level, 0);
        Set<UUID> hit = new HashSet<>();
        LivingEntity current = first;
        Location prev = p.getEyeLocation();
        for (int i = 0; i < jumps && current != null; i++) {
            hit.add(current.getUniqueId());
            line(prev, current.getEyeLocation(), particle);
            hurt(p, current, dmg);
            if (fire > 0) current.setFireTicks((int) (fire * 20));
            applyPotions(e, level, current);
            prev = current.getEyeLocation();
            dmg *= falloff;
            LivingEntity next = null;
            double best = Double.MAX_VALUE;
            for (LivingEntity c : enemiesAround(p, current.getLocation(), jump)) {
                double d = c.getLocation().distanceSquared(current.getLocation());
                if (!hit.contains(c.getUniqueId()) && d < best) { best = d; next = c; }
            }
            current = next;
        }
        return true;
    }

    private boolean drain(Player p, Map<String, Object> e, int level, LivingEntity forced, Particle particle) {
        LivingEntity t = forced != null ? forced : rayTarget(p, num(e, "range", level, 12));
        if (t == null) return false;
        double dmg = damageAgainst(e, level, t);
        double before = t.getHealth();
        line(t.getEyeLocation(), p.getEyeLocation(), particle);
        hurt(p, t, dmg);
        double dealt = Math.max(0, before - Math.max(0, t.getHealth()));
        heal(p, dealt * num(e, "ratio", level, 0.5));
        applyPotions(e, level, t);
        p.getWorld().spawnParticle(Particle.HEART, p.getLocation().add(0, 1.8, 0), 3, 0.2, 0.2, 0.2, 0);
        return true;
    }

    private boolean volley(Player p, Map<String, Object> e, int level) {
        int arrows = (int) num(e, "arrows", level, 6);
        double dmg = num(e, "damage", level, 4), spread = num(e, "spread", level, 0.3), speed = num(e, "speed", level, 2.2);
        double fire = num(e, "fire_seconds", level, 0);
        Vector base = p.getEyeLocation().getDirection();
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        for (int i = 0; i < arrows; i++) {
            Vector v = base.clone().add(new Vector(rnd.nextGaussian() * spread * 0.5, rnd.nextGaussian() * spread * 0.3, rnd.nextGaussian() * spread * 0.5)).normalize().multiply(speed);
            Arrow a = p.launchProjectile(Arrow.class, v);
            a.setDamage(dmg);
            a.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
            a.setCritical(true);
            if (fire > 0) a.setFireTicks((int) (fire * 20));
            Bukkit_runLater(a);
        }
        return true;
    }

    private void Bukkit_runLater(Arrow a) {
        plugin.getServer().getScheduler().runTaskLater(plugin, a::remove, 100L);
    }
}
