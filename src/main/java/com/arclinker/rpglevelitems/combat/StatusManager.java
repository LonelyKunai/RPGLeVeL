package com.arclinker.rpglevelitems.combat;

import com.arclinker.rpglevelitems.RPGLevelTier;
import org.bukkit.Bukkit;
import org.bukkit.Particle;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.*;

/**
 * Status effects: bleed, burn, poison (damage over time), freeze (slow), stun (immobile), shock
 * (takes extra damage). Applied by abilities ({@code statuses:} in the effect) and by the
 * {@code *_chance} stats of items. Players resist with the {@code status_resist} stat.
 */
public final class StatusManager {
    public enum Status {
        BLEED("Sangrado", "§4"), BURN("Quemadura", "§6"), POISON("Veneno", "§2"),
        FREEZE("Congelado", "§b"), STUN("Aturdido", "§e"), SHOCK("Electrocutado", "§9");

        public final String label, color;

        Status(String label, String color) {
            this.label = label;
            this.color = color;
        }

        public static Status parse(String s) {
            try { return valueOf(s.trim().toUpperCase(Locale.ROOT)); } catch (Exception e) { return null; }
        }
    }

    private record Active(long until, double power, UUID source) {}

    private final RPGLevelTier plugin;
    private final Map<UUID, EnumMap<Status, Active>> active = new HashMap<>();
    private boolean dot;
    private final org.bukkit.NamespacedKey jumpKey;

    public StatusManager(RPGLevelTier plugin) {
        this.plugin = plugin;
        this.jumpKey = new org.bukkit.NamespacedKey(plugin, "stun_jump");
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 10L, 10L);
    }

    /** True while the plugin itself is dealing status damage (listeners must ignore it). */
    public boolean isDot() {
        return dot;
    }

    public boolean has(LivingEntity e, Status s) {
        EnumMap<Status, Active> m = active.get(e.getUniqueId());
        if (m == null) return false;
        Active a = m.get(s);
        return a != null && a.until > System.currentTimeMillis();
    }

    public double power(LivingEntity e, Status s) {
        EnumMap<Status, Active> m = active.get(e.getUniqueId());
        Active a = m == null ? null : m.get(s);
        return a == null || a.until <= System.currentTimeMillis() ? 0 : a.power;
    }

    /** Extra damage multiplier from SHOCK. */
    public double damageTakenMultiplier(LivingEntity e) {
        return has(e, Status.SHOCK) ? 1.0 + plugin.getConfig().getDouble("combat.shock_bonus", 0.15) * power(e, Status.SHOCK) : 1.0;
    }

    public void apply(LivingEntity target, Status s, double seconds, double power, Player source) {
        if (target.isDead() || seconds <= 0) return;
        if (target instanceof Player pl) {
            double resist = Math.min(75, plugin.getStatEngine().get(pl, "status_resist"));
            if (resist > 0 && Math.random() * 100 < resist) return;
        }
        if (target.getScoreboardTags().contains("rlv_boss") && (s == Status.STUN || s == Status.FREEZE)) seconds *= 0.4;
        long until = System.currentTimeMillis() + (long) (seconds * 1000);
        EnumMap<Status, Active> m = active.computeIfAbsent(target.getUniqueId(), k -> new EnumMap<>(Status.class));
        Active old = m.get(s);
        if (old != null && old.until > until && old.power >= power) return;
        m.put(s, new Active(until, Math.max(1, power), source == null ? null : source.getUniqueId()));
        switch (s) {
            case BURN -> target.setFireTicks((int) (seconds * 20));
            case FREEZE -> {
                target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, (int) (seconds * 20), 2, true, false, false));
                target.setFreezeTicks(Math.min(target.getMaxFreezeTicks() + 40, (int) (seconds * 20)));
            }
            case STUN -> stun(target, seconds);
            default -> { }
        }
        if (source != null) com.arclinker.rpglevelitems.Util.actionBar(source, s.color + s.label + " §7aplicado");
        if (target instanceof Player pl) pl.sendMessage("§c¡Estás " + s.color + s.label.toLowerCase(Locale.ROOT) + "§c!");
    }

    /** Stun = no movement (huge slowness), no jumping (jump strength modifier) and mobs lose their target. */
    private void stun(LivingEntity t, double seconds) {
        int ticks = (int) (seconds * 20);
        t.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, ticks, 9, true, false, false));
        var jump = t.getAttribute(org.bukkit.attribute.Attribute.GENERIC_JUMP_STRENGTH);
        if (jump != null && findJump(jump) == null) {
            jump.addModifier(new org.bukkit.attribute.AttributeModifier(jumpKey, -1.0, org.bukkit.attribute.AttributeModifier.Operation.ADD_SCALAR, org.bukkit.inventory.EquipmentSlotGroup.ANY));
        }
        if (t instanceof Mob mob) mob.setTarget(null);
    }

    public void clear(LivingEntity e) {
        EnumMap<Status, Active> m = active.remove(e.getUniqueId());
        if (m != null && m.containsKey(Status.STUN)) endStun(e);
    }

    private org.bukkit.attribute.AttributeModifier findJump(org.bukkit.attribute.AttributeInstance inst) {
        for (var m : inst.getModifiers()) if (jumpKey.equals(m.getKey())) return m;
        return null;
    }

    private void endStun(LivingEntity e) {
        var jump = e.getAttribute(org.bukkit.attribute.Attribute.GENERIC_JUMP_STRENGTH);
        if (jump != null) {
            var m = findJump(jump);
            if (m != null) jump.removeModifier(m);
        }
        e.removePotionEffect(PotionEffectType.SLOWNESS);
    }

    /** Plugin shutdown: remove stun modifiers from everything still stunned. */
    public void shutdown() {
        for (Map.Entry<UUID, EnumMap<Status, Active>> en : active.entrySet()) {
            if (!en.getValue().containsKey(Status.STUN)) continue;
            if (Bukkit.getEntity(en.getKey()) instanceof LivingEntity le) endStun(le);
        }
        active.clear();
    }

    private void tick() {
        long now = System.currentTimeMillis();
        for (Iterator<Map.Entry<UUID, EnumMap<Status, Active>>> it = active.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, EnumMap<Status, Active>> en = it.next();
            Entity ent = Bukkit.getEntity(en.getKey());
            if (!(ent instanceof LivingEntity le) || le.isDead() || !le.isValid()) {
                // entity gone (dead / removed / chunk unloaded): the stun jump modifier is persistent on the entity, so clear it if we can
                if (ent instanceof LivingEntity gone && en.getValue().containsKey(Status.STUN)) endStun(gone);
                it.remove();
                continue;
            }
            EnumMap<Status, Active> m = en.getValue();
            for (Iterator<Map.Entry<Status, Active>> i2 = m.entrySet().iterator(); i2.hasNext(); ) {
                Map.Entry<Status, Active> st = i2.next();
                Active a = st.getValue();
                if (a.until <= now) {
                    if (st.getKey() == Status.STUN) endStun(le);
                    i2.remove();
                    continue;
                }
                effect(le, st.getKey(), a, now);
            }
            if (m.isEmpty()) it.remove();
        }
    }

    private void effect(LivingEntity e, Status s, Active a, long now) {
        var loc = e.getLocation().add(0, e.getHeight() * 0.6, 0);
        boolean second = (now / 500) % 2 == 0; // damage once per second (task runs every 0.5 s)
        switch (s) {
            case BLEED -> {
                e.getWorld().spawnParticle(Particle.DAMAGE_INDICATOR, loc, 2, 0.25, 0.3, 0.25, 0.0);
                if (second) dmg(e, a, 1.5 * a.power);
            }
            case BURN -> {
                e.getWorld().spawnParticle(Particle.FLAME, loc, 3, 0.25, 0.3, 0.25, 0.01);
                if (e.getFireTicks() < 20) e.setFireTicks(30);
                if (second) dmg(e, a, 1.0 * a.power);
            }
            case POISON -> {
                e.getWorld().spawnParticle(Particle.SNEEZE, loc, 2, 0.25, 0.3, 0.25, 0.0);
                if (second && e.getHealth() > 2.0) dmg(e, a, Math.min(1.2 * a.power, e.getHealth() - 1.0));
            }
            case FREEZE -> e.getWorld().spawnParticle(Particle.SNOWFLAKE, loc, 4, 0.3, 0.4, 0.3, 0.01);
            case STUN -> {
                e.getWorld().spawnParticle(Particle.CRIT, e.getEyeLocation().add(0, 0.4, 0), 3, 0.2, 0.1, 0.2, 0.0);
                if (e instanceof Mob mob) mob.setTarget(null);
            }
            case SHOCK -> e.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, loc, 4, 0.3, 0.4, 0.3, 0.05);
        }
    }

    private void dmg(LivingEntity e, Active a, double amount) {
        if (amount <= 0) return;
        Player src = a.source == null ? null : Bukkit.getPlayer(a.source);
        dot = true;
        try {
            if (src != null) e.damage(amount, src); else e.damage(amount);
        } finally {
            dot = false;
        }
    }
}
