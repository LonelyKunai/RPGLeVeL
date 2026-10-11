package com.arclinker.rpglevelitems.gui;

import com.arclinker.rpglevelitems.RPGLevelTier;
import com.arclinker.rpglevelitems.rank.RankCategory;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

/** Click handling for every menu that uses {@link MenuHolder} (class, subclass, job, quest, rank). */
public class GUIListener implements Listener {
    private final RPGLevelTier plugin;

    public GUIListener(RPGLevelTier plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof MenuHolder) event.setCancelled(true);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof MenuHolder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getClickedInventory() != event.getView().getTopInventory()) return;
        int slot = event.getRawSlot();

        if (holder.type == MenuHolder.Type.RANK) {
            RankCategory[] cats = RankCategory.values();
            for (int i = 0; i < RankGUI.TAB_SLOTS.length; i++) {
                if (slot == RankGUI.TAB_SLOTS[i]) {
                    new RankGUI(plugin).open(player, cats[i], holder.scope);
                    return;
                }
            }
            if (slot == RankGUI.SLOT_SCOPE) {
                new RankGUI(plugin).open(player, holder.category, holder.scope.other());
                return;
            }
            if (slot == RankGUI.SLOT_CLOSE) player.closeInventory();
            return;
        }

        if (slot == GuiUtil.SLOT_CLOSE) {
            player.closeInventory();
            return;
        }
        if (slot == GuiUtil.SLOT_RANKING) {
            new RankGUI(plugin).open(player, holder.category);
            return;
        }
        if (slot == GuiUtil.SLOT_PREV && event.getCurrentItem() != null && holder.page > 0) {
            reopen(player, holder, holder.page - 1);
            return;
        }
        if (slot == GuiUtil.SLOT_NEXT && event.getCurrentItem() != null && event.getCurrentItem().getType() == org.bukkit.Material.ARROW) {
            reopen(player, holder, holder.page + 1);
            return;
        }
        if (slot < 0 || slot >= holder.ids.size() || holder.ids.get(slot) == null) return;
        String id = holder.ids.get(slot);

        switch (holder.type) {
            case CLASS -> {
                if (plugin.getClassManager().setClass(player, id)) new ClassGUI(plugin).open(player, holder.page);
            }
            case SUBCLASS -> {
                if (plugin.getSubClassManager().setSubClass(player, id)) new SubClassGUI(plugin).open(player, holder.page);
            }
            case JOB -> {
                ClickType click = event.getClick();
                if (click.isShiftClick()) {
                    var jm = plugin.getJobManager();
                    if (id.equalsIgnoreCase(jm.getGlobalJob(player, 1))) jm.clearGlobalJob(player, 1);
                    else if (id.equalsIgnoreCase(jm.getGlobalJob(player, 2))) jm.clearGlobalJob(player, 2);
                } else {
                    plugin.getJobManager().setGlobalJob(player, click.isRightClick() ? 2 : 1, id);
                }
                new JobGUI(plugin).open(player, holder.page);
            }
            default -> { }
        }
    }

    private void reopen(Player player, MenuHolder holder, int page) {
        switch (holder.type) {
            case CLASS -> new ClassGUI(plugin).open(player, page);
            case SUBCLASS -> new SubClassGUI(plugin).open(player, page);
            case JOB -> new JobGUI(plugin).open(player, page);
            default -> { }
        }
    }
}
