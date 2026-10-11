package com.arclinker.rpglevelitems.stats;

import com.arclinker.rpglevelitems.RPGLevelTier;
import com.arclinker.rpglevelitems.Util;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockDispenseArmorEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Stops class / subclass equipment from being worn by players that do not meet its requirements
 * (class, subclass, level...) at the moment they try to equip it. {@link StatEngine} still returns anything
 * that slipped through every second (and never counts its stats), this just avoids the flicker.
 */
public final class ArmorGuard implements Listener {
    private final RPGLevelTier plugin;

    public ArmorGuard(RPGLevelTier plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    private static boolean isArmor(ItemStack it) {
        if (it == null) return false;
        String n = it.getType().name();
        return n.endsWith("_HELMET") || n.endsWith("_CHESTPLATE") || n.endsWith("_LEGGINGS") || n.endsWith("_BOOTS")
                || it.getType() == org.bukkit.Material.ELYTRA || it.getType() == org.bukkit.Material.TURTLE_HELMET;
    }

    /** Denies (and tells the player) when {@code it} cannot be equipped by {@code p}. */
    private boolean denied(Player p, ItemStack it) {
        String why = Requirements.check(plugin, p, it);
        if (why == null) return false;
        Util.actionBar(p, "§c" + why);
        return true;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        ItemStack it = e.getItem();
        if (!isArmor(it) || e.getHand() == null) return;
        if (denied(e.getPlayer(), it)) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (e.getClickedInventory() == null) return;
        ItemStack moving = null;
        boolean equipSlot = e.getSlotType() == org.bukkit.event.inventory.InventoryType.SlotType.ARMOR
                || (e.getClickedInventory() == p.getInventory() && e.getSlot() == 40);
        if (equipSlot) {
            if (e.getClick() == ClickType.NUMBER_KEY && e.getHotbarButton() >= 0) moving = p.getInventory().getItem(e.getHotbarButton());
            else if (e.getClick() == ClickType.SWAP_OFFHAND) moving = e.getCurrentItem();
            else moving = e.getCursor();
        } else if (e.isShiftClick() && e.getClickedInventory() == p.getInventory() && isArmor(e.getCurrentItem())) {
            moving = e.getCurrentItem(); // shift-click auto-equips armor
        }
        if (moving != null && !moving.getType().isAir() && denied(p, moving)) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDrag(InventoryDragEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        ItemStack cursor = e.getOldCursor();
        if (cursor == null || cursor.getType().isAir()) return;
        for (int raw : e.getRawSlots()) {
            if (e.getView().getSlotType(raw) == org.bukkit.event.inventory.InventoryType.SlotType.ARMOR && denied(p, cursor)) {
                e.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSwap(PlayerSwapHandItemsEvent e) {
        // the item that would land in the off hand
        ItemStack toOff = e.getMainHandItem();
        if (toOff != null && !toOff.getType().isAir() && denied(e.getPlayer(), toOff)) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onDispense(BlockDispenseArmorEvent e) {
        if (e.getTargetEntity() instanceof Player p && Requirements.check(plugin, p, e.getItem()) != null) e.setCancelled(true);
    }
}
