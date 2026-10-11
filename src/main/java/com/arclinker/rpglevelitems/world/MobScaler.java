package com.arclinker.rpglevelitems.world;

import com.arclinker.rpglevelitems.RPGLevelTier;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.persistence.PersistentDataType;

import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Hostile mob levels: from the zone flag {@code mob_level} or the distance to the world spawn
 * ({@code mobs.blocks_per_level}). Higher levels have more health / damage and give more XP and loot.
 */
public final class MobScaler implements Listener {
    private final RPGLevelTier plugin;
    private final NamespacedKey levelKey;

    public MobScaler(RPGLevelTier plugin) {
        this.plugin = plugin;
        this.levelKey = new NamespacedKey(RPGLevelTier.KEY_NS, "rlv_level");
    }

    public int levelOf(LivingEntity e) {
        Integer l = e.getPersistentDataContainer().get(levelKey, PersistentDataType.INTEGER);
        return l == null ? 1 : l;
    }

    /** XP / points multiplier for a kill of this mob. */
    public double rewardMultiplier(LivingEntity e) {
        return 1.0 + (levelOf(e) - 1) * plugin.getConfig().getDouble("mobs.xp_per_level", 0.04);
    }

    private int baseLevel(Location at) {
        var zones = plugin.getZones();
        String zl = zones.flag(at, "mob_level");
        int range = (int) zones.number(at, "mob_range", 0);
        int lvl;
        if (zl != null) {
            try { lvl = Integer.parseInt(zl.trim()); } catch (NumberFormatException e) { lvl = 1; }
        } else {
            World w = at.getWorld();
            double dist = Math.hypot(at.getX() - w.getSpawnLocation().getX(), at.getZ() - w.getSpawnLocation().getZ());
            lvl = 1 + (int) (dist / Math.max(10, plugin.getConfig().getDouble("mobs.blocks_per_level", 150)));
            lvl += plugin.getConfig().getInt("mobs.world_bonus." + w.getName().toLowerCase(Locale.ROOT), 0);
        }
        if (range > 0) lvl += ThreadLocalRandom.current().nextInt(-range, range + 1);
        if (plugin.getEvents() != null) lvl += plugin.getEvents().mobLevelBonus();
        return Math.max(1, Math.min(plugin.getConfig().getInt("mobs.max_level", 60), lvl));
    }

    public void apply(LivingEntity e, int level) {
        e.getPersistentDataContainer().set(levelKey, PersistentDataType.INTEGER, level);
        double hp = plugin.getConfig().getDouble("mobs.health_per_level", 0.07);
        double dmg = plugin.getConfig().getDouble("mobs.damage_per_level", 0.035);
        mod(e, Attribute.GENERIC_MAX_HEALTH, hp * (level - 1), AttributeModifier.Operation.ADD_SCALAR);
        mod(e, Attribute.GENERIC_ATTACK_DAMAGE, dmg * (level - 1), AttributeModifier.Operation.ADD_SCALAR);
        AttributeInstance max = e.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        if (max != null) e.setHealth(max.getValue());
        if (plugin.getConfig().getBoolean("mobs.show_name", true) && !e.getScoreboardTags().contains("rlv_boss")) {
            String n = e.getType().name().toLowerCase(Locale.ROOT).replace('_', ' ');
            e.setCustomName("§8[§cLv." + level + "§8] §f" + Character.toUpperCase(n.charAt(0)) + n.substring(1));
            e.setCustomNameVisible(true);
            e.setRemoveWhenFarAway(true);
        }
    }

    private void mod(LivingEntity e, Attribute a, double amount, AttributeModifier.Operation op) {
        AttributeInstance inst = e.getAttribute(a);
        if (inst == null) return;
        NamespacedKey key = new NamespacedKey(RPGLevelTier.KEY_NS, "mob_level_" + a.getKey().getKey());
        for (AttributeModifier m : new java.util.ArrayList<>(inst.getModifiers())) if (key.equals(m.getKey())) inst.removeModifier(m);
        if (amount <= 0) return;
        inst.addModifier(new AttributeModifier(key, amount, op, EquipmentSlotGroup.ANY));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSpawn(CreatureSpawnEvent e) {
        if (!plugin.getConfig().getBoolean("mobs.enabled", true)) return;
        if (!(e.getEntity() instanceof Enemy) || !(e.getEntity() instanceof Mob)) return;
        if (e.getEntity().getScoreboardTags().contains("rlv_boss") || e.getEntity().getPersistentDataContainer().has(levelKey, PersistentDataType.INTEGER)) return;
        apply(e.getEntity(), baseLevel(e.getLocation()));
    }
}
