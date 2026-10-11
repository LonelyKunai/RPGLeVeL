package com.arclinker.rpglevelitems;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.title.Title;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Boss;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import net.kyori.adventure.audience.Audience;
import org.bukkit.event.EventPriority;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.util.Vector;


import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

@SuppressWarnings("unused")
public class LevelCombatListener implements Listener {

    private final RPGLevelTier plugin;
    private final ItemProgressionManager manager;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    public LevelCombatListener(RPGLevelTier plugin, ItemProgressionManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onAttack(EntityDamageByEntityEvent event) {
        if (plugin.getStatuses() != null && plugin.getStatuses().isDot()) return; // status damage is not a weapon hit
        Player player = null;
        if (event.getDamager() instanceof Player p) {
            player = p;
        } else if (event.getDamager() instanceof Projectile proj && proj.getShooter() instanceof Player shooter) {
            player = shooter;
        }

        // Damage dealt by an ability must not trigger weapon effects / XP again
        if (player != null && plugin.getAbilityManager() != null && plugin.getAbilityManager().isCasting(player)) return;

        if (player != null && event.getEntity() instanceof LivingEntity target && target != player) {
            handleWeaponAttack(player, target, event);
        }

        if (event.getEntity() instanceof Player defender) {
            handleArmorDefense(defender, event);
        }
    }

    private void handleWeaponAttack(Player player, LivingEntity target, EntityDamageByEntityEvent event) {
        ItemStack weapon = player.getInventory().getItemInMainHand();
        if (!manager.isRpgItem(weapon)) return;

        manager.initializeItem(weapon);
        ItemMeta meta = weapon.getItemMeta();
        if (meta == null) return;

        int level = manager.getLevel(meta);
        int tier = manager.getTier(meta);

        // Calculate and apply combat abilities based on Tier
        double damageMultiplier = 1.0;
        ThreadLocalRandom rand = ThreadLocalRandom.current();

        // Tier II+: Impacto Crítico (15% prob)
        if (tier >= 2 && rand.nextDouble() < 0.15) {
            damageMultiplier += 0.50;
            target.getWorld().spawnParticle(Particle.CRIT, target.getLocation().add(0, 1, 0), 15, 0.2, 0.2, 0.2, 0.1);
            player.playSound(target.getLocation(), Sound.ENTITY_PLAYER_ATTACK_CRIT, 1.0f, 1.2f);
        }

        // Tier III+: Llama Volcánica
        if (tier >= 3) {
            target.setFireTicks(Math.max(target.getFireTicks(), 80));
            target.getWorld().spawnParticle(Particle.FLAME, target.getLocation().add(0, 0.8, 0), 10, 0.2, 0.3, 0.2, 0.05);
        }

        // Tier IV+: Corte Desgarrador (Sangrado instantáneo)
        if (tier >= 4 && rand.nextDouble() < 0.25) {
            event.setDamage(event.getDamage() + 2.5);
            target.getWorld().spawnParticle(Particle.DAMAGE_INDICATOR, target.getLocation().add(0, 1, 0), 6, 0.2, 0.2, 0.2, 0.05);
            player.playSound(target.getLocation(), Sound.ENTITY_PLAYER_HURT_SWEET_BERRY_BUSH, 1.0f, 0.9f);
        }

        // Tier V+: Furia del Trueno (20% prob)
        if (tier >= 5 && rand.nextDouble() < 0.20) {
            target.getWorld().strikeLightningEffect(target.getLocation());
            event.setDamage(event.getDamage() + 4.0);
            player.playSound(target.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.8f, 1.3f);
        }

        // Tier VI+: Robo de Vida (Vampirismo)
        if (tier >= 6) {
            double currentHp = player.getHealth();
            @SuppressWarnings("deprecation")
            double maxHp = player.getMaxHealth();
            double healAmount = Math.max(0.5, event.getDamage() * 0.15);
            player.setHealth(Math.min(maxHp, currentHp + healAmount));
            player.getWorld().spawnParticle(Particle.HEART, player.getLocation().add(0, 1.5, 0), 3, 0.2, 0.2, 0.2, 0.05);
        }

        // Tier VIII+: Onda Sísmica (20% prob)
        if (tier >= 8 && rand.nextDouble() < 0.20) {
            target.getWorld().spawnParticle(Particle.SWEEP_ATTACK, target.getLocation().add(0, 0.5, 0), 2, 0.1, 0.1, 0.1, 0.05);
            player.playSound(target.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.0f, 0.8f);
            for (Entity nearby : target.getNearbyEntities(4.0, 3.0, 4.0)) {
                if (nearby instanceof LivingEntity le && nearby != player && nearby != target) {
                    le.damage(4.0, player);
                    Vector push = le.getLocation().toVector().subtract(player.getLocation().toVector()).normalize().multiply(0.6).setY(0.3);
                    le.setVelocity(push);
                }
            }
        }

        // Tier X: Poder Divino
        if (tier >= 10) {
            damageMultiplier += 0.30;
            target.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, target.getLocation().add(0, 1, 0), 12, 0.3, 0.3, 0.3, 0.1);
        }

        if (damageMultiplier > 1.0) {
            event.setDamage(event.getDamage() * damageMultiplier);
        }

        // Add XP to weapon
        long hitXp = plugin.getConfig().getLong("progression.weapon_xp_per_hit", 2L);
        ItemProgressionManager.ProgressionResult result = manager.addXp(weapon, hitXp);
        player.getInventory().setItemInMainHand(weapon);

        handleProgressionNotice(player, weapon, result);
    }

    private void handleArmorDefense(Player player, EntityDamageByEntityEvent event) {
        ItemStack[] armor = player.getInventory().getArmorContents();
        double totalReductionPercent = 0.0;
        long hitXp = plugin.getConfig().getLong("progression.armor_xp_per_hit_taken", 3L);

        double redPerLevel = plugin.getConfig().getDouble("scaling.armor_reduction_per_level", 0.2);
        double redPerTier = plugin.getConfig().getDouble("scaling.armor_reduction_per_tier", 1.5);

        for (int i = 0; i < armor.length; i++) {
            ItemStack piece = armor[i];
            if (manager.isRpgItem(piece)) {
                manager.initializeItem(piece);
                ItemMeta meta = piece.getItemMeta();
                if (meta != null) {
                    int level = manager.getLevel(meta);
                    int tier = manager.getTier(meta);
                    totalReductionPercent += ((tier - 1) * redPerTier) + ((level - 1) * redPerLevel);

                    ItemProgressionManager.ProgressionResult result = manager.addXp(piece, hitXp);
                    armor[i] = piece;

                    if (result.tierAscended()) {
                        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
                        Component titleComp = miniMessage.deserialize(plugin.getConfig().getString("messages.tier_ascend_title", "<gold><b>¡ASCENSIÓN DE TIER!</b>"));
                        ((Audience) player).showTitle(Title.title(titleComp, miniMessage.deserialize("")));
                    } else if (result.leveledUp()) {
                        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.2f);
                    }
                }
            }
        }

        player.getInventory().setArmorContents(armor);

        if (totalReductionPercent > 0) {
            double multiplier = Math.max(0.1, 1.0 - (totalReductionPercent / 100.0));
            event.setDamage(event.getDamage() * multiplier);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onKill(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        Player killer = entity.getKiller();
        if (killer == null) return;

        ItemStack weapon = killer.getInventory().getItemInMainHand();
        if (!manager.isRpgItem(weapon)) return;

        long killXp = (entity instanceof Boss)
                ? plugin.getConfig().getLong("progression.boss_kill_xp", 300L)
                : plugin.getConfig().getLong("progression.weapon_xp_per_kill", 15L);

        ItemProgressionManager.ProgressionResult result = manager.addXp(weapon, killXp);
        killer.getInventory().setItemInMainHand(weapon);

        handleProgressionNotice(killer, weapon, result);
    }

    private void handleProgressionNotice(Player player, ItemStack item, ItemProgressionManager.ProgressionResult result) {
        if (result.tierAscended()) {
            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 0.6f);

            String titleStr = plugin.getConfig().getString("messages.tier_ascend_title", "<gold><b>¡ASCENSIÓN DE TIER!</b>");
            String itemName = item.getItemMeta() != null && item.getItemMeta().hasDisplayName()
                    ? item.getItemMeta().getDisplayName()
                    : item.getType().name();

            String subStr = plugin.getConfig().getString("messages.tier_ascend_subtitle", "<yellow>{item_name} <white>ascendió a <gold><b>Tier {tier_roman} (Nivel 1)</b>")
                    .replace("{item_name}", itemName)
                    .replace("{tier_roman}", manager.toRoman(result.newTier()))
                    .replace("{tier}", String.valueOf(result.newTier()));

            Title title = Title.title(
                    miniMessage.deserialize(titleStr),
                    miniMessage.deserialize(subStr),
                    Title.Times.times(Duration.ofMillis(300), Duration.ofMillis(2500), Duration.ofMillis(500))
            );
            ((Audience) player).showTitle(title);
            ((Audience) player).sendMessage(miniMessage.deserialize(plugin.getConfig().getString("messages.prefix", "") + subStr));

        } else if (result.leveledUp()) {
            float pitch = 0.8f + (result.newLevel() * 0.03f);
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7f, Math.min(2.0f, pitch));

            ItemMeta meta = item.getItemMeta();
            int level = meta != null ? manager.getLevel(meta) : result.newLevel();
            int tier = meta != null ? manager.getTier(meta) : result.newTier();
            double bonusPerLevel = plugin.getConfig().getDouble("scaling.bonus_damage_per_level", 0.5);
            double bonusPerTier = plugin.getConfig().getDouble("scaling.bonus_damage_per_tier", 2.0);
            double bonusDamage = ((tier - 1) * bonusPerTier) + ((level - 1) * bonusPerLevel);

            String actionStr = plugin.getConfig().getString("messages.level_up_actionbar", "<green>¡Tu arma subió al <yellow><b>Nivel {level}</b></yellow>! <gray>(+{bonus_damage} daño)")
                    .replace("{level}", String.valueOf(result.newLevel()))
                    .replace("{bonus_damage}", String.format("%.1f", bonusDamage));

            ((Audience) player).sendActionBar(miniMessage.deserialize(actionStr));
        }
    }
}
