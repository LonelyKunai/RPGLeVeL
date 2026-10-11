package com.arclinker.rpglevelitems.ability;

import com.arclinker.rpglevelitems.ItemProgressionManager;
import com.arclinker.rpglevelitems.RPGLevelTier;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

/** Ability tomes (right click) and item abilities (right_click / on_hit / on_damaged). */
public final class AbilityListener implements Listener {
    private final RPGLevelTier plugin;
    private final AbilityManager abilities;
    private final ItemProgressionManager items;

    public AbilityListener(RPGLevelTier plugin) {
        this.plugin = plugin;
        this.abilities = plugin.getAbilityManager();
        this.items = plugin.getProgressionManager();
    }

    // ---- matching ------------------------------------------------------------------

    private boolean matches(AbilityDef d, Material mat) {
        String n = mat.name();
        for (String token : d.appliesTo) {
            switch (token.toUpperCase(Locale.ROOT)) {
                case "SWORD" -> { if (n.endsWith("_SWORD")) return true; }
                case "AXE" -> { if (n.endsWith("_AXE")) return true; }
                case "MACE" -> { if (mat == Material.MACE) return true; }
                case "TRIDENT" -> { if (mat == Material.TRIDENT) return true; }
                case "BOW" -> { if (mat == Material.BOW) return true; }
                case "CROSSBOW" -> { if (mat == Material.CROSSBOW) return true; }
                case "STAFF" -> { if (mat == Material.BLAZE_ROD || mat == Material.STICK) return true; }
                case "SHIELD" -> { if (mat == Material.SHIELD) return true; }
                case "HELMET" -> { if (n.endsWith("_HELMET")) return true; }
                case "CHESTPLATE" -> { if (n.endsWith("_CHESTPLATE")) return true; }
                case "LEGGINGS" -> { if (n.endsWith("_LEGGINGS")) return true; }
                case "BOOTS" -> { if (n.endsWith("_BOOTS")) return true; }
                case "ARMOR" -> { if (items.isArmor(mat) && mat != Material.SHIELD) return true; }
                case "WEAPON" -> { if (items.isWeapon(mat)) return true; }
                default -> { }
            }
        }
        return false;
    }

    /** Ability level granted by the item's tier, or 0 if the item does not qualify. */
    private int levelFor(Player holder, AbilityDef d, ItemStack item) {
        if (item == null || item.getType() == Material.AIR || !matches(d, item.getType()) || !items.isRpgItem(item)) return 0;
        if (com.arclinker.rpglevelitems.stats.Requirements.check(plugin, holder, item) != null) return 0;
        items.initializeItem(item);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return 0;
        int tier = items.getTier(meta);
        if (tier < d.minTier) return 0;
        return Math.min(d.maxLevel, tier - d.minTier + 1);
    }

    private boolean tryCast(Player p, AbilityDef d, int level, LivingEntity target, boolean notify) {
        String key = "item:" + d.id;
        if (abilities.cooldownLeftMs(p, key) > 0) return false;
        boolean active = "right_click".equals(d.trigger);
        double mana = active ? plugin.getMana().itemCost() : 0;
        if (active) {
            if (!plugin.getZones().abilitiesAllowed(p)) return false;
            if (!plugin.getMana().has(p, mana)) {
                com.arclinker.rpglevelitems.Util.actionBar(p, "§cManá insuficiente §7(" + (int) mana + ")");
                return false;
            }
        }
        if (!abilities.effects().cast(p, d.effect, level, target, notify)) return false;
        plugin.getMana().spend(p, mana);
        abilities.startCooldown(p, key, abilities.reducedCooldown(p, d.cooldownAt(level)));
        return true;
    }



    // ---- events --------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        Action a = event.getAction();
        if (a != Action.RIGHT_CLICK_AIR && a != Action.RIGHT_CLICK_BLOCK) return;
        Player p = event.getPlayer();
        ItemStack hand = p.getInventory().getItemInMainHand();

        // Ability tome
        String tomeId = plugin.getAbilityItems().readId(hand);
        if (tomeId != null) {
            event.setCancelled(true);
            AbilityDef d = abilities.get(tomeId);
            if (d == null) {
                p.sendMessage("§cEsta habilidad ya no existe.");
                return;
            }
            abilities.castUnlocked(p, d);
            return;
        }

        // Item abilities (right click). Block clicks only count while sneaking so chests etc. keep working.
        if (a == Action.RIGHT_CLICK_BLOCK && !p.isSneaking()) return;
        for (AbilityDef d : abilities.itemAbilities()) {
            if (!"right_click".equals(d.trigger)) continue;
            int level = levelFor(p, d, hand);
            if (level <= 0) continue;
            if (abilities.cooldownLeftMs(p, "item:" + d.id) > 0) {
                com.arclinker.rpglevelitems.Util.actionBar(p, "§c" + d.name + " en enfriamiento");
                continue;
            }
            if (tryCast(p, d, level, null, true)) {
                com.arclinker.rpglevelitems.Util.actionBar(p, "§6✦ " + d.name + " §7(nivel " + level + ")");
                break;
            }
        }
    }

    private static Player shooterOrPlayer(Entity damager) {
        if (damager instanceof Player p) return p;
        if (damager instanceof Projectile proj && proj.getShooter() instanceof Player p) return p;
        return null;
    }

    private static LivingEntity attackerOf(Entity damager) {
        if (damager instanceof LivingEntity le) return le;
        if (damager instanceof Projectile proj && proj.getShooter() instanceof LivingEntity le) return le;
        return null;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (plugin.getStatuses().isDot()) return;
        ThreadLocalRandom rnd = ThreadLocalRandom.current();

        // on_hit: weapon in hand of the attacker
        Player attacker = shooterOrPlayer(event.getDamager());
        if (attacker != null && !abilities.isCasting(attacker) && event.getEntity() instanceof LivingEntity victim && victim != attacker) {
            ItemStack weapon = attacker.getInventory().getItemInMainHand();
            for (AbilityDef d : abilities.itemAbilities()) {
                if (!"on_hit".equals(d.trigger) || rnd.nextDouble() > d.chance) continue;
                int level = levelFor(attacker, d, weapon);
                if (level > 0 && tryCast(attacker, d, level, victim, false)) break;
            }
        }

        // on_damaged: armor of the victim
        if (event.getEntity() instanceof Player defender && !abilities.isCasting(defender)) {
            LivingEntity source = attackerOf(event.getDamager());
            ItemStack[] armor = defender.getInventory().getArmorContents();
            outer:
            for (AbilityDef d : abilities.itemAbilities()) {
                if (!"on_damaged".equals(d.trigger) || rnd.nextDouble() > d.chance) continue;
                for (ItemStack piece : armor) {
                    int level = levelFor(defender, d, piece);
                    if (level > 0 && tryCast(defender, d, level, source, false)) break outer;
                }
            }
        }
    }
}
