package com.arclinker.rpglevelitems.combat;

import com.arclinker.rpglevelitems.RPGLevelTier;
import com.arclinker.rpglevelitems.Util;
import com.arclinker.rpglevelitems.stats.Requirements;
import org.bukkit.Bukkit;
import org.bukkit.Particle;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Combat stats: critical hits, penetration, lifesteal, evasion, damage reduction, shock, status procs
 * and item requirements for weapons. Abilities use {@link #outgoing} / {@link #afterHit} through
 * AbilityEffects so the same stats apply to spells.
 */
public final class CombatEngine implements Listener {
    public record Result(double damage, boolean crit) {}

    private final RPGLevelTier plugin;
    private final Map<UUID, Long> warned = new HashMap<>();

    public CombatEngine(RPGLevelTier plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    private double stat(Player p, String key) {
        return plugin.getStatEngine().get(p, key);
    }

    /** Damage dealt by {@code attacker} after crit / spell power / penetration. */
    public Result outgoing(Player attacker, LivingEntity victim, double dmg, boolean spell) {
        boolean crit = false;
        double chance = plugin.getConfig().getDouble("combat.base_crit_chance", 3.0) + stat(attacker, "crit_chance");
        if (ThreadLocalRandom.current().nextDouble() * 100 < Math.min(95, chance)) {
            crit = true;
            double bonus = plugin.getConfig().getDouble("combat.base_crit_damage", 50.0) + stat(attacker, "crit_damage");
            dmg *= 1 + bonus / 100.0;
            victim.getWorld().spawnParticle(Particle.CRIT, victim.getLocation().add(0, victim.getHeight() * 0.7, 0), 12, 0.3, 0.3, 0.3, 0.2);
            Util.actionBar(attacker, plugin.getLanguageManager().tr(attacker, "combat.crit", String.format(Locale.ROOT, "%.1f", dmg)));
        }
        if (spell) dmg *= 1 + stat(attacker, "spell_power") / 100.0;
        double exec = stat(attacker, "execute");
        if (exec > 0) {
            AttributeInstance mh = victim.getAttribute(Attribute.GENERIC_MAX_HEALTH);
            double maxHp = mh == null ? 20 : mh.getValue();
            if (victim.getHealth() < maxHp * 0.30) dmg *= 1 + Math.min(100, exec) / 100.0;
        }
        double pen = Math.min(70, stat(attacker, "penetration"));
        if (pen > 0) {
            AttributeInstance armor = victim.getAttribute(Attribute.GENERIC_ARMOR);
            double a = armor == null ? 0 : armor.getValue();
            dmg += dmg * (pen / 100.0) * Math.min(0.8, a * 0.04);
        }
        return new Result(dmg, crit);
    }

    /** Lifesteal and status procs after a hit that dealt {@code dealt} damage. */
    public void afterHit(Player attacker, LivingEntity victim, double dealt) {
        double ls = stat(attacker, "lifesteal");
        if (ls > 0 && dealt > 0) {
            AttributeInstance max = attacker.getAttribute(Attribute.GENERIC_MAX_HEALTH);
            double maxHp = max == null ? 20 : max.getValue();
            attacker.setHealth(Math.min(maxHp, attacker.getHealth() + dealt * Math.min(50, ls) / 100.0));
        }
        double moh = stat(attacker, "mana_on_hit");
        if (moh > 0 && plugin.getMana().enabled()) {
            var mm = plugin.getMana();
            mm.set(attacker, Math.min(mm.max(attacker), mm.get(attacker) + moh));
        }
        double secs = plugin.getConfig().getDouble("combat.proc_seconds", 4.0);
        for (StatusManager.Status s : StatusManager.Status.values()) {
            double c = stat(attacker, s.name().toLowerCase(Locale.ROOT) + "_chance");
            if (c > 0 && ThreadLocalRandom.current().nextDouble() * 100 < c) plugin.getStatuses().apply(victim, s, secs, 1, attacker);
        }
    }

    private static Player playerOf(Entity damager) {
        if (damager instanceof Player p) return p;
        if (damager instanceof Projectile proj && proj.getShooter() instanceof Player p) return p;
        return null;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent e) {
        if (plugin.getStatuses().isDot()) return;
        if (!(e.getEntity() instanceof LivingEntity victim)) return;
        Player attacker = playerOf(e.getDamager());
        boolean casting = attacker != null && plugin.getAbilityManager().isCasting(attacker);

        if (attacker != null && victim instanceof Player vp && attacker != vp) {
            boolean enforce = plugin.getConfig().getBoolean("pvp.enforce", false);
            boolean ff = plugin.getParties().sameParty(attacker, vp) && !plugin.getConfig().getBoolean("party.friendly_fire", false);
            if (ff || (enforce && !plugin.getZones().pvpAllowed(attacker, vp))) {
                e.setCancelled(true);
                return;
            }
        }
        if (attacker != null && !casting) {
            if (e.getDamager() instanceof Player) {
                String why = Requirements.check(plugin, attacker, attacker.getInventory().getItemInMainHand());
                if (why != null) {
                    e.setCancelled(true);
                    long now = System.currentTimeMillis();
                    if (now - warned.getOrDefault(attacker.getUniqueId(), 0L) > 1500) {
                        warned.put(attacker.getUniqueId(), now);
                        attacker.sendMessage("§c" + why);
                    }
                    return;
                }
            }
            Result r = outgoing(attacker, victim, e.getDamage(), false);
            e.setDamage(r.damage());
        }
        if (victim instanceof Player vp) {
            double ev = Math.min(60, stat(vp, "evasion"));
            if (ev > 0 && ThreadLocalRandom.current().nextDouble() * 100 < ev) {
                e.setCancelled(true);
                Util.actionBar(vp, plugin.getLanguageManager().tr(vp, "combat.dodge"));
                vp.getWorld().spawnParticle(Particle.CLOUD, vp.getLocation().add(0, 1, 0), 8, 0.3, 0.4, 0.3, 0.02);
                return;
            }
            double dr = Math.min(70, stat(vp, "damage_reduction"));
            if (dr > 0) e.setDamage(e.getDamage() * (1 - dr / 100.0));
            double th = Math.min(50, stat(vp, "thorns"));
            if (th > 0 && e.getDamager() instanceof LivingEntity src && src != vp && !(src instanceof Player)) {
                // reflected damage has no source entity, so it does not re-enter this handler
                src.damage(Math.max(0.5, e.getDamage() * th / 100.0));
            }
        }
        double shock = plugin.getStatuses().damageTakenMultiplier(victim);
        if (shock != 1.0) e.setDamage(e.getDamage() * shock);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent e) {
        if (plugin.getStatuses().isDot() || !(e.getEntity() instanceof LivingEntity victim)) return;
        Player attacker = playerOf(e.getDamager());
        if (attacker == null || attacker == victim || plugin.getAbilityManager().isCasting(attacker)) return;
        afterHit(attacker, victim, e.getFinalDamage());
    }

    /** Weapon requirements also apply to bows, crossbows and tridents. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onShoot(org.bukkit.event.entity.EntityShootBowEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        String why = Requirements.check(plugin, p, e.getBow());
        if (why != null) {
            e.setCancelled(true);
            Util.actionBar(p, "§c" + why);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onLaunch(org.bukkit.event.entity.ProjectileLaunchEvent e) {
        if (!(e.getEntity() instanceof org.bukkit.entity.Trident) || !(e.getEntity().getShooter() instanceof Player p)) return;
        String why = Requirements.check(plugin, p, p.getInventory().getItemInMainHand());
        if (why != null) {
            e.setCancelled(true);
            Util.actionBar(p, "§c" + why);
        }
    }

    @EventHandler
    public void onDeath(EntityDeathEvent e) {
        plugin.getStatuses().clear(e.getEntity());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        warned.remove(e.getPlayer().getUniqueId());
        plugin.getStatuses().clear(e.getPlayer());
    }
}
