package com.arclinker.rpglevelitems.achievements;

import com.arclinker.rpglevelitems.RPGLevelTier;
import com.arclinker.rpglevelitems.rank.PlayerRecord;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/** Paginated list of achievements with progress; unlocked ones are highlighted. */
public final class AchievementGUI implements Listener {
    private static final int PAGE = 45;
    private final RPGLevelTier plugin;

    public AchievementGUI(RPGLevelTier plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    private static final class Holder implements InventoryHolder {
        final int page;
        Inventory inv;

        Holder(int page) {
            this.page = page;
        }

        @Override
        public @NotNull Inventory getInventory() {
            return inv;
        }
    }

    private static ItemStack icon(Material m, String name, List<String> lore, boolean glow) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(lore);
        meta.addItemFlags(ItemFlag.values());
        if (glow) meta.setEnchantmentGlintOverride(true);
        it.setItemMeta(meta);
        return it;
    }

    public void open(Player p, int page) {
        var am = plugin.getAchievements();
        PlayerRecord r = plugin.getPlayerStore().get(p);
        List<AchievementManager.Achievement> all = new ArrayList<>(am.all());
        int pages = Math.max(1, (all.size() + PAGE - 1) / PAGE);
        int pg = Math.max(0, Math.min(page, pages - 1));
        Holder h = new Holder(pg);
        Inventory inv = Bukkit.createInventory(h, 54, "§6§lLogros §8(" + r.achievements.size() + "/" + all.size() + ")");
        h.inv = inv;
        for (int i = 0; i < PAGE && pg * PAGE + i < all.size(); i++) {
            var a = all.get(pg * PAGE + i);
            boolean done = r.achievements.contains(a.id());
            long cur = Math.min(a.goal(), r.counters.getOrDefault(a.counter(), 0L));
            List<String> lore = new ArrayList<>();
            lore.add("§7" + a.description());
            lore.add(done ? "§a✔ Completado" : "§7Progreso: §f" + cur + "§7/§f" + a.goal());
            if (a.coins() > 0) lore.add("§7Recompensa: §e" + a.coins() + " monedas");
            var t = am.title(a.title());
            if (t != null) lore.add("§7Título: " + t.display());
            inv.setItem(i, icon(done ? Material.LIME_DYE : Material.GRAY_DYE, (done ? "§a" : "§7") + a.name(), lore, done));
        }
        ItemStack fill = icon(Material.GRAY_STAINED_GLASS_PANE, " ", new ArrayList<>(), false);
        for (int s = PAGE; s < 54; s++) inv.setItem(s, fill);
        if (pg > 0) inv.setItem(45, icon(Material.ARROW, "§ePágina anterior", new ArrayList<>(), false));
        if (pg < pages - 1) inv.setItem(53, icon(Material.ARROW, "§ePágina siguiente", new ArrayList<>(), false));
        inv.setItem(49, icon(Material.NAME_TAG, "§6Títulos", List.of("§7Activo: " + (r.activeTitle.isEmpty() ? "§8ninguno" : am.activeTitleDisplay(r)), "§7Usa §f/title §7para cambiarlo"), false));
        p.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof Holder h)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p) || e.getClickedInventory() != e.getView().getTopInventory()) return;
        if (e.getRawSlot() == 45 && h.page > 0) open(p, h.page - 1);
        else if (e.getRawSlot() == 53 && e.getCurrentItem() != null && e.getCurrentItem().getType() == Material.ARROW) open(p, h.page + 1);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof Holder) e.setCancelled(true);
    }
}
