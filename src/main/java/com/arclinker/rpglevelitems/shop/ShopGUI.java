package com.arclinker.rpglevelitems.shop;

import com.arclinker.rpglevelitems.RPGLevelTier;
import com.arclinker.rpglevelitems.shop.ShopManager.Category;
import com.arclinker.rpglevelitems.shop.ShopManager.Entry;
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

import java.util.*;

/** Shop menu: categories, then items with live stock, price and personal / guild purchase limits. */
public final class ShopGUI implements Listener {
    private static final int PAGE = 45;

    private final RPGLevelTier plugin;

    public ShopGUI(RPGLevelTier plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    private static final class Holder implements InventoryHolder {
        String category = "";
        int page;
        final List<String> keys = new ArrayList<>();
        final List<String> cats = new ArrayList<>();
        Inventory inv;

        @Override
        public @NotNull Inventory getInventory() {
            return inv;
        }
    }

    private static ItemStack named(Material m, String name, String... lore) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        meta.setDisplayName(name);
        if (lore.length > 0) meta.setLore(new ArrayList<>(Arrays.asList(lore)));
        meta.addItemFlags(ItemFlag.values());
        it.setItemMeta(meta);
        return it;
    }

    private static String bar(double ratio) {
        int n = (int) Math.round(Math.max(0, Math.min(1, ratio)) * 10);
        String color = ratio > 0.6 ? "§a" : ratio > 0.25 ? "§e" : "§c";
        return color + "▮".repeat(n) + "§8" + "▮".repeat(10 - n);
    }

    public void open(Player p, String category, int page) {
        var shop = plugin.getShop();
        Holder h = new Holder();
        Category cat = category == null || category.isEmpty() ? null : shop.category(category);
        if (cat == null) {
            Inventory inv = Bukkit.createInventory(h, 27, "§6§lTienda");
            h.inv = inv;
            ItemStack fill = named(Material.GRAY_STAINED_GLASS_PANE, " ");
            for (int i = 0; i < 27; i++) inv.setItem(i, fill);
            int[] slots = {11, 13, 15, 10, 12, 14, 16};
            int i = 0;
            for (Category c : shop.categories()) {
                if (i >= slots.length) break;
                long inStock = c.keys().stream().filter(k -> shop.entry(k).stock > 0).count();
                inv.setItem(slots[i], named(c.icon(), "§e§l" + c.name(), "§7Objetos: §f" + c.keys().size() + " §8(" + inStock + " con stock)",
                        "§7Nivel de stock: §f" + switch (c.tier()) { case "high" -> "alto"; case "low" -> "bajo"; default -> "medio"; }, "", "§eClic para abrir"));
                h.cats.add(c.id());
                i++;
            }
            inv.setItem(22, named(Material.CLOCK, "§6Próximo relleno: §f" + shop.timeToRefill(), "§7Jugadores activos: §f" + shop.activePlayers(),
                    "§7Tu dinero: §e" + plugin.getEconomy().format(plugin.getEconomy().balance(p.getUniqueId())),
                    "§8Más jugadores activos = más stock.", "§8Límite de compra por objeto y ciclo: " + (int) shop.playerLimitPercent() + "% por jugador, " + (int) shop.guildLimitPercent() + "% por gremio."));
            inv.setItem(26, named(Material.BARRIER, "§cCerrar"));
            p.openInventory(inv);
            return;
        }
        h.category = cat.id();
        int pages = Math.max(1, (cat.keys().size() + PAGE - 1) / PAGE);
        h.page = Math.max(0, Math.min(page, pages - 1));
        Inventory inv = Bukkit.createInventory(h, 54, "§6§l" + cat.name() + " §8(" + (h.page + 1) + "/" + pages + ")");
        h.inv = inv;
        var eco = plugin.getEconomy();
        for (int i = 0; i < PAGE && h.page * PAGE + i < cat.keys().size(); i++) {
            String key = cat.keys().get(h.page * PAGE + i);
            Entry e = shop.entry(key);
            ItemStack icon = shop.create(key, 1);
            ItemMeta meta = icon.getItemMeta();
            List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
            lore.add("");
            lore.add("§7Stock: §f" + e.stock + "§7/§f" + e.capacity + " " + bar((double) e.stock / Math.max(1, e.capacity)));
            lore.add(e.stock > 0 ? "§6Comprar: §e" + eco.format(shop.buyPrice(e)) : "§cAgotado");
            lore.add("§aVender: §e" + eco.format(shop.sellPrice(e)));
            int pl = shop.playerLeft(e, p.getUniqueId());
            lore.add("§7Tu límite: §f" + pl + "§7/§f" + shop.playerLimit(e));
            int gl = shop.guildLeft(e, p);
            if (gl >= 0) lore.add("§7Límite del gremio: §f" + gl + "§7/§f" + shop.guildLimit(e));
            lore.add("§8Clic: comprar 1 · Shift: 16 · Der: vender 1 · Shift+der: vender todo");
            meta.setLore(lore);
            icon.setItemMeta(meta);
            inv.setItem(i, icon);
            h.keys.add(key);
        }
        ItemStack fill = named(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int s = PAGE; s < 54; s++) inv.setItem(s, fill);
        if (h.page > 0) inv.setItem(45, named(Material.ARROW, "§ePágina anterior"));
        if (h.page < pages - 1) inv.setItem(53, named(Material.ARROW, "§ePágina siguiente"));
        inv.setItem(48, named(Material.OAK_DOOR, "§eVolver"));
        inv.setItem(49, named(Material.CLOCK, "§6Relleno en §f" + shop.timeToRefill(), "§7Jugadores activos: §f" + shop.activePlayers(),
                "§7Dinero: §e" + eco.format(eco.balance(p.getUniqueId()))));
        inv.setItem(52, named(Material.BARRIER, "§cCerrar"));
        p.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof Holder h)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p) || e.getClickedInventory() != e.getView().getTopInventory()) return;
        int slot = e.getRawSlot();
        if (h.category.isEmpty()) {
            if (slot == 26) { p.closeInventory(); return; }
            int[] slots = {11, 13, 15, 10, 12, 14, 16};
            for (int i = 0; i < h.cats.size() && i < slots.length; i++) if (slot == slots[i]) open(p, h.cats.get(i), 0);
            return;
        }
        if (slot == 52) { p.closeInventory(); return; }
        if (slot == 48) { open(p, "", 0); return; }
        if (slot == 45 && h.page > 0) { open(p, h.category, h.page - 1); return; }
        if (slot == 53 && e.getCurrentItem() != null && e.getCurrentItem().getType() == Material.ARROW) { open(p, h.category, h.page + 1); return; }
        if (slot < 0 || slot >= h.keys.size()) return;
        String key = h.keys.get(slot);
        String err;
        if (e.isRightClick()) err = plugin.getShop().sell(p, key, e.isShiftClick() ? Integer.MAX_VALUE : 1);
        else err = plugin.getShop().buy(p, key, e.isShiftClick() ? 16 : 1);
        if (err != null) p.sendMessage("§c" + err);
        open(p, h.category, h.page);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof Holder) e.setCancelled(true);
    }
}
