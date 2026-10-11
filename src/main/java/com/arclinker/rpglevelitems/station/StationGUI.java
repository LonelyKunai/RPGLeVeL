package com.arclinker.rpglevelitems.station;

import com.arclinker.rpglevelitems.RPGLevelTier;
import com.arclinker.rpglevelitems.itemcreator.RlvpHandler;
import com.arclinker.rpglevelitems.station.StationManager.Station;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Station menu (recipes of one set) and the listener that opens it when a placed station block is
 * right-clicked. {@code preview} menus (opened with /station) only show the recipes.
 */
public class StationGUI implements Listener {
    private static final int PAGE_SIZE = 45;
    private static final int SLOT_PREV = 45, SLOT_INFO = 49, SLOT_CLOSE = 52, SLOT_NEXT = 53;

    private final RPGLevelTier plugin;

    public StationGUI(RPGLevelTier plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    private static final class Holder implements InventoryHolder {
        final Station station;
        final int page;
        final boolean preview;
        final List<String> ids;
        Inventory inventory;

        Holder(Station station, int page, boolean preview, List<String> ids) {
            this.station = station;
            this.page = page;
            this.preview = preview;
            this.ids = ids;
        }

        @Override
        public @NotNull Inventory getInventory() {
            return inventory;
        }
    }

    public void open(Player player, Station st, int page, boolean preview) {
        StationManager sm = plugin.getStationManager();
        List<String> all = sm.recipeIds(st);
        int pages = Math.max(1, (all.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        int p = Math.max(0, Math.min(page, pages - 1));
        List<String> ids = new ArrayList<>(all.subList(Math.min(all.size(), p * PAGE_SIZE), Math.min(all.size(), (p + 1) * PAGE_SIZE)));

        Holder holder = new Holder(st, p, preview, ids);
        Inventory inv = Bukkit.createInventory(holder, 54, "§6§lEstación §8- §f" + st.name() + (preview ? " §7(vista)" : ""));
        holder.inventory = inv;

        for (int i = 0; i < ids.size(); i++) {
            ItemStack shown = recipeIcon(player, sm, st, ids.get(i), preview);
            if (shown != null) inv.setItem(i, shown);
        }
        ItemStack filler = button(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int s = PAGE_SIZE; s < 54; s++) inv.setItem(s, filler);
        if (p > 0) inv.setItem(SLOT_PREV, button(Material.ARROW, "§ePágina anterior"));
        if (p < pages - 1) inv.setItem(SLOT_NEXT, button(Material.ARROW, "§ePágina siguiente"));
        inv.setItem(SLOT_CLOSE, button(Material.BARRIER, "§cCerrar"));
        List<String> info = new ArrayList<>();
        info.add("§7Tipo: §f" + switch (st.kind()) { case CLASS -> "clase"; case SUBCLASS -> "subclase"; case UNIVERSAL -> "universal"; });
        String deny = sm.denyReason(player, st, null);
        info.add(deny == null ? "§a✔ Puedes forjar aquí" : deny);
        info.add("§7Recetas: §f" + all.size());
        if (st.kind() != StationManager.Kind.UNIVERSAL) {
            info.add("§7Tu nivel: §e" + sm.levelOf(player, st));
            info.add("§7Niveles por tier: §f" + tierLevels(sm));
        }
        if (preview) info.add("§8Coloca la estación y úsala para forjar.");
        inv.setItem(SLOT_INFO, button(st.block(), "§6Estación de " + st.name(), info));
        player.openInventory(inv);
    }

    private String tierLevels(StationManager sm) {
        StringBuilder sb = new StringBuilder();
        for (int t = 1; t <= 5; t++) sb.append(t > 1 ? "§7/§f" : "").append(sm.levelRequired(t));
        return sb.toString();
    }

    private ItemStack recipeIcon(Player player, StationManager sm, Station st, String id, boolean preview) {
        RlvpHandler h = plugin.getRlvpHandler();
        ItemStack item = h.getItem(id);
        RlvpHandler.Recipe recipe = h.getRecipe(id);
        if (item == null || recipe == null) return null;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;
        List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
        lore.add("§6Ingredientes:");
        boolean enough = true;
        for (RlvpHandler.Ingredient ing : recipe.ingredients()) {
            int have = preview ? -1 : sm.count(player, ing);
            boolean ok = have >= ing.amount();
            if (!preview && !ok) enough = false;
            lore.add((preview ? "§7• " : ok ? "§a✔ " : "§c✘ ") + "§f" + ing.amount() + "x " + sm.ingredientName(ing)
                    + (preview ? "" : " §8(" + have + ")"));
        }
        if (st.kind() != StationManager.Kind.UNIVERSAL) {
            int need = sm.levelRequired(recipe.tier());
            boolean lvOk = sm.levelOf(player, st) >= need;
            lore.add((lvOk || preview ? "§7" : "§c") + "Nivel de " + (st.kind() == StationManager.Kind.CLASS ? "clase" : "subclase") + " requerido: §f" + need);
        }
        if (!preview) {
            lore.add("");
            lore.add(enough ? "§eClic: forjar 1 §8| §eShift+clic: forjar máx." : "§8Faltan ingredientes");
        }
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack button(Material mat, String name, List<String> lore) {
        ItemStack it = new ItemStack(mat);
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            if (lore != null) meta.setLore(lore);
            meta.addItemFlags(org.bukkit.inventory.ItemFlag.values());
            it.setItemMeta(meta);
        }
        return it;
    }

    private static ItemStack button(Material mat, String name) {
        return button(mat, name, null);
    }

    // ---- listeners ------------------------------------------------------------------

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof Holder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getClickedInventory() != event.getView().getTopInventory()) return;
        int slot = event.getRawSlot();
        if (slot == SLOT_CLOSE) {
            player.closeInventory();
        } else if (slot == SLOT_PREV && holder.page > 0) {
            open(player, holder.station, holder.page - 1, holder.preview);
        } else if (slot == SLOT_NEXT && event.getCurrentItem() != null && event.getCurrentItem().getType() == Material.ARROW) {
            open(player, holder.station, holder.page + 1, holder.preview);
        } else if (slot >= 0 && slot < holder.ids.size() && !holder.preview) {
            if (plugin.getStationManager().craft(player, holder.station, holder.ids.get(slot), event.isShiftClick()) > 0) {
                open(player, holder.station, holder.page, false);
            }
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Holder) event.setCancelled(true);
    }

    /** Right-click on a placed station block opens its menu (sneak + item in hand places blocks as usual). */
    @EventHandler(ignoreCancelled = false)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getHand() != EquipmentSlot.HAND) return;
        if (event.getClickedBlock() == null || plugin.getRlvpBlocks() == null) return;
        Player p = event.getPlayer();
        if (p.isSneaking() && !p.getInventory().getItemInMainHand().getType().isAir()) return;
        Station st = plugin.getStationManager().byItem(plugin.getRlvpBlocks().getBlockId(event.getClickedBlock()));
        if (st == null) return;
        event.setCancelled(true);
        open(p, st, 0, false);
    }
}
