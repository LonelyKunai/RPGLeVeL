package com.arclinker.rpglevelitems.stats;

import com.arclinker.rpglevelitems.RPGLevelTier;
import org.bukkit.Keyed;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.*;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Keeps plugin items (gems and forge materials / stations) from being used as their vanilla base material:
 * not accepted by vanilla recipes, villagers, enchanting tables, beacons, brewing stands, furnaces, anvils..., not placeable (gem redstone).
 * Our own recipes only accept them where the recipe explicitly asks for that exact item.
 */
public final class CraftGuard implements Listener {
    private static final Set<InventoryType> STORAGE = EnumSet.of(InventoryType.CHEST, InventoryType.ENDER_CHEST, InventoryType.SHULKER_BOX,
            InventoryType.BARREL, InventoryType.HOPPER, InventoryType.DROPPER, InventoryType.DISPENSER, InventoryType.PLAYER,
            InventoryType.CRAFTING, InventoryType.CREATIVE, InventoryType.WORKBENCH);

    private final RPGLevelTier plugin;

    public CraftGuard(RPGLevelTier plugin) {
        this.plugin = plugin;
    }

    /** Gems and .rlvp materials / stations: items whose base material must not count as the vanilla item. */
    private boolean guarded(ItemStack it) {
        if (it == null || it.getType().isAir() || !it.hasItemMeta()) return false;
        if (plugin.getGemManager() != null && plugin.getGemManager().tokenOf(it) != null) return true;
        var h = plugin.getRlvpHandler();
        String id = h == null ? null : h.getId(it);
        if (id == null) return false;
        var info = h.getInfo(id);
        return info != null && (info.type().equals("material") || info.type().equals("station"));
    }

    private boolean exactMatch(Recipe r, ItemStack it) {
        List<RecipeChoice> choices = new ArrayList<>();
        if (r instanceof ShapedRecipe s) choices.addAll(s.getChoiceMap().values());
        else if (r instanceof ShapelessRecipe s) choices.addAll(s.getChoiceList());
        for (RecipeChoice c : choices) if (c instanceof RecipeChoice.ExactChoice && c.test(it)) return true;
        return false;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPrepare(PrepareItemCraftEvent e) {
        Recipe r = e.getRecipe();
        if (r == null) return;
        boolean ours = r instanceof Keyed k && k.getKey().getNamespace().equalsIgnoreCase(RPGLevelTier.KEY_NS);
        for (ItemStack it : e.getInventory().getMatrix()) {
            if (!guarded(it)) continue;
            if (!ours || !exactMatch(r, it)) {
                e.getInventory().setResult(null);
                return;
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        if (plugin.getGemManager() != null && plugin.getGemManager().tokenOf(e.getItemInHand()) != null) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onClick(InventoryClickEvent e) {
        InventoryType top = e.getView().getTopInventory().getType();
        if (STORAGE.contains(top) || e.getView().getTopInventory().getHolder() != null && !(e.getView().getTopInventory().getHolder() instanceof org.bukkit.block.BlockState)
                && top == InventoryType.CHEST) return;
        boolean inTop = e.getClickedInventory() != null && e.getClickedInventory() == e.getView().getTopInventory();
        if (inTop && guarded(e.getCursor())) { e.setCancelled(true); return; }
        if (e.isShiftClick() && !inTop && guarded(e.getCurrentItem())) { e.setCancelled(true); return; }
        if (inTop && e.getHotbarButton() >= 0 && e.getWhoClicked() instanceof Player p && guarded(p.getInventory().getItem(e.getHotbarButton()))) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDrag(InventoryDragEvent e) {
        InventoryType top = e.getView().getTopInventory().getType();
        if (STORAGE.contains(top) || !guarded(e.getOldCursor())) return;
        int size = e.getView().getTopInventory().getSize();
        for (int slot : e.getRawSlots()) if (slot < size) { e.setCancelled(true); return; }
    }

    @EventHandler(ignoreCancelled = true)
    public void onFuel(org.bukkit.event.inventory.FurnaceBurnEvent e) {
        if (guarded(e.getFuel())) e.setCancelled(true);
    }
}
