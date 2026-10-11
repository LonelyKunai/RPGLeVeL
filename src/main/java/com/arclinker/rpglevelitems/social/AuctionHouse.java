package com.arclinker.rpglevelitems.social;

import com.arclinker.rpglevelitems.RPGLevelTier;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * Auction house: /ah sell &lt;price&gt; lists the item in hand, /ah browses, /ah mine manages own listings.
 * Expired or cancelled items and sale earnings wait in /ah claim. Uses the economy (Vault or internal coins).
 * Stored in auctions.yml.
 */
public final class AuctionHouse implements Listener {
    private static final class Listing {
        String id;
        UUID seller;
        String sellerName;
        double price;
        long expires;
        ItemStack item;
    }

    enum Mode { BROWSE, MINE }

    private static final class Holder implements InventoryHolder {
        final Mode mode;
        final int page;
        final List<String> ids = new ArrayList<>();
        Inventory inv;

        Holder(Mode mode, int page) {
            this.mode = mode;
            this.page = page;
        }

        @Override
        public @NotNull Inventory getInventory() {
            return inv;
        }
    }

    private final RPGLevelTier plugin;
    private final File file;
    private final Map<String, Listing> listings = new LinkedHashMap<>();
    private final Map<UUID, List<ItemStack>> claims = new HashMap<>();
    private final Map<UUID, Double> earnings = new HashMap<>();
    private int nextId = 1;

    public AuctionHouse(RPGLevelTier plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "auctions.yml");
        load();
        Bukkit.getPluginManager().registerEvents(this, plugin);
        Bukkit.getScheduler().runTaskTimer(plugin, this::expire, 1200L, 1200L);
    }

    // ---- persistence ------------------------------------------------------------------------

    private void load() {
        if (!file.exists()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        nextId = y.getInt("next_id", 1);
        ConfigurationSection l = y.getConfigurationSection("listings");
        if (l != null) {
            for (String id : l.getKeys(false)) {
                ConfigurationSection s = l.getConfigurationSection(id);
                if (s == null || !(s.get("item") instanceof ItemStack it)) continue;
                Listing li = new Listing();
                li.id = id;
                try { li.seller = UUID.fromString(s.getString("seller", "")); } catch (IllegalArgumentException e) { continue; }
                li.sellerName = s.getString("seller_name", "?");
                li.price = s.getDouble("price");
                li.expires = s.getLong("expires");
                li.item = it;
                listings.put(id, li);
            }
        }
        ConfigurationSection c = y.getConfigurationSection("claims");
        if (c != null) {
            for (String u : c.getKeys(false)) {
                List<ItemStack> items = new ArrayList<>();
                for (Object o : c.getList(u, List.of())) if (o instanceof ItemStack it) items.add(it);
                try { claims.put(UUID.fromString(u), items); } catch (IllegalArgumentException ignored) { }
            }
        }
        ConfigurationSection e = y.getConfigurationSection("earnings");
        if (e != null) for (String u : e.getKeys(false)) { try { earnings.put(UUID.fromString(u), e.getDouble(u)); } catch (IllegalArgumentException ignored) { } }
    }

    private void save() {
        YamlConfiguration y = new YamlConfiguration();
        y.set("next_id", nextId);
        for (Listing li : listings.values()) {
            String p = "listings." + li.id + ".";
            y.set(p + "seller", li.seller.toString());
            y.set(p + "seller_name", li.sellerName);
            y.set(p + "price", li.price);
            y.set(p + "expires", li.expires);
            y.set(p + "item", li.item);
        }
        for (Map.Entry<UUID, List<ItemStack>> e : claims.entrySet()) if (!e.getValue().isEmpty()) y.set("claims." + e.getKey(), e.getValue());
        for (Map.Entry<UUID, Double> e : earnings.entrySet()) if (e.getValue() > 0) y.set("earnings." + e.getKey(), e.getValue());
        try {
            plugin.getDataFolder().mkdirs();
            y.save(file);
        } catch (IOException ex) {
            plugin.getLogger().severe("[AuctionHouse] Could not save auctions.yml: " + ex.getMessage());
        }
    }

    // ---- operations -------------------------------------------------------------------------

    private int countOf(UUID id) {
        int n = 0;
        for (Listing l : listings.values()) if (l.seller.equals(id)) n++;
        return n;
    }

    public String sell(Player p, double price) {
        ItemStack hand = p.getInventory().getItemInMainHand();
        if (hand.getType().isAir()) return "Ten el ítem en la mano.";
        double min = plugin.getConfig().getDouble("auction.min_price", 1), max = plugin.getConfig().getDouble("auction.max_price", 10_000_000);
        if (price < min || price > max) return "El precio debe estar entre " + (long) min + " y " + (long) max + ".";
        if (countOf(p.getUniqueId()) >= plugin.getConfig().getInt("auction.max_listings", 8)) return "Máximo de publicaciones alcanzado.";
        double fee = Math.floor(price * plugin.getConfig().getDouble("auction.listing_fee", 0.0));
        if (fee > 0 && !plugin.getEconomy().withdraw(p, fee)) return "La tarifa de publicación es " + plugin.getEconomy().format(fee) + ".";
        Listing li = new Listing();
        li.id = String.valueOf(nextId++);
        li.seller = p.getUniqueId();
        li.sellerName = p.getName();
        li.price = Math.floor(price);
        li.expires = System.currentTimeMillis() + plugin.getConfig().getLong("auction.duration_hours", 48) * 3_600_000L;
        li.item = hand.clone();
        p.getInventory().setItemInMainHand(null);
        listings.put(li.id, li);
        save();
        return null;
    }

    private String buy(Player p, Listing li) {
        if (li.seller.equals(p.getUniqueId())) return "Es tu propia publicación (gestiónala en /ah mine).";
        if (!listings.containsKey(li.id)) return "Esa publicación ya no existe.";
        if (!plugin.getEconomy().withdraw(p, li.price)) return "Necesitas " + plugin.getEconomy().format(li.price) + ".";
        double tax = plugin.getConfig().getDouble("auction.tax", 0.05);
        double net = Math.floor(li.price * (1 - tax));
        listings.remove(li.id);
        Player seller = Bukkit.getPlayer(li.seller);
        if (seller != null) {
            plugin.getEconomy().deposit(seller, net);
            seller.sendMessage("§a" + p.getName() + " compró tu ítem en la subasta: §e+" + plugin.getEconomy().format(net));
            plugin.getAchievements().add(seller, "auctions", Math.max(1, li.item.getAmount()));
        } else {
            earnings.merge(li.seller, net, Double::sum);
        }
        for (ItemStack r : p.getInventory().addItem(li.item).values()) p.getWorld().dropItemNaturally(p.getLocation(), r);
        save();
        return null;
    }

    private void expire() {
        long now = System.currentTimeMillis();
        boolean changed = false;
        for (Iterator<Listing> it = listings.values().iterator(); it.hasNext(); ) {
            Listing li = it.next();
            if (li.expires > now) continue;
            claims.computeIfAbsent(li.seller, k -> new ArrayList<>()).add(li.item);
            it.remove();
            changed = true;
            Player s = Bukkit.getPlayer(li.seller);
            if (s != null) s.sendMessage("§eUna de tus publicaciones expiró. Recógela con §f/ah claim§e.");
        }
        if (changed) save();
    }

    public int claim(Player p) {
        int n = 0;
        List<ItemStack> items = claims.remove(p.getUniqueId());
        if (items != null) {
            for (ItemStack it : items) {
                for (ItemStack r : p.getInventory().addItem(it).values()) p.getWorld().dropItemNaturally(p.getLocation(), r);
                n++;
            }
        }
        Double money = earnings.remove(p.getUniqueId());
        if (money != null && money > 0) {
            plugin.getEconomy().deposit(p, money);
            p.sendMessage("§aRecogiste §e" + plugin.getEconomy().format(money) + " §ade ventas.");
        }
        if (n > 0 || money != null) save();
        return n;
    }

    public boolean hasClaims(UUID id) {
        return !claims.getOrDefault(id, List.of()).isEmpty() || earnings.getOrDefault(id, 0.0) > 0;
    }

    // ---- GUI --------------------------------------------------------------------------------

    public void open(Player p, Mode mode, int page) {
        List<Listing> all = new ArrayList<>();
        for (Listing l : listings.values()) if (mode == Mode.BROWSE || l.seller.equals(p.getUniqueId())) all.add(l);
        Collections.reverse(all);
        int pages = Math.max(1, (all.size() + 44) / 45);
        int pg = Math.max(0, Math.min(page, pages - 1));
        Holder h = new Holder(mode, pg);
        Inventory inv = Bukkit.createInventory(h, 54, (mode == Mode.BROWSE ? "§6§lSubasta" : "§6§lMis publicaciones") + " §8(" + (pg + 1) + "/" + pages + ")");
        h.inv = inv;
        for (int i = 0; i < 45 && pg * 45 + i < all.size(); i++) {
            Listing l = all.get(pg * 45 + i);
            ItemStack it = l.item.clone();
            ItemMeta meta = it.getItemMeta();
            List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
            lore.add("");
            lore.add("§6Precio: §e" + plugin.getEconomy().format(l.price));
            lore.add("§7Vendedor: §f" + l.sellerName);
            long left = Math.max(0, l.expires - System.currentTimeMillis()) / 60000;
            lore.add("§7Expira en: §f" + left / 60 + "h " + left % 60 + "m");
            lore.add(mode == Mode.BROWSE ? "§eClic para comprar" : "§cClic para cancelar y recuperar");
            meta.setLore(lore);
            it.setItemMeta(meta);
            inv.setItem(i, it);
            h.ids.add(l.id);
        }
        ItemStack fill = named(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int s = 45; s < 54; s++) inv.setItem(s, fill);
        if (pg > 0) inv.setItem(45, named(Material.ARROW, "§ePágina anterior"));
        if (pg < pages - 1) inv.setItem(53, named(Material.ARROW, "§ePágina siguiente"));
        inv.setItem(48, named(mode == Mode.BROWSE ? Material.CHEST : Material.EMERALD, mode == Mode.BROWSE ? "§aMis publicaciones" : "§aVer subasta"));
        inv.setItem(49, named(Material.GOLD_INGOT, "§6Saldo: §e" + plugin.getEconomy().format(plugin.getEconomy().balance(p.getUniqueId())),
                "§7Vender: §f/ah sell <precio>", "§7Impuesto: §f" + (int) (plugin.getConfig().getDouble("auction.tax", 0.05) * 100) + "%"));
        inv.setItem(50, named(Material.BUNDLE, hasClaims(p.getUniqueId()) ? "§a¡Tienes cosas por recoger! §7/ah claim" : "§7Nada por recoger"));
        inv.setItem(52, named(Material.BARRIER, "§cCerrar"));
        p.openInventory(inv);
    }

    public void open(Player p) {
        open(p, Mode.BROWSE, 0);
    }

    public void openMine(Player p) {
        open(p, Mode.MINE, 0);
    }

    private static ItemStack named(Material m, String name, String... lore) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        meta.setDisplayName(name);
        if (lore.length > 0) meta.setLore(Arrays.asList(lore));
        it.setItemMeta(meta);
        return it;
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof Holder h)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p) || e.getClickedInventory() != e.getView().getTopInventory()) return;
        int slot = e.getRawSlot();
        if (slot == 52) { p.closeInventory(); return; }
        if (slot == 45 && h.page > 0) { open(p, h.mode, h.page - 1); return; }
        if (slot == 53 && e.getCurrentItem() != null && e.getCurrentItem().getType() == Material.ARROW) { open(p, h.mode, h.page + 1); return; }
        if (slot == 48) { open(p, h.mode == Mode.BROWSE ? Mode.MINE : Mode.BROWSE, 0); return; }
        if (slot == 50) { claim(p); open(p, h.mode, h.page); return; }
        if (slot < 0 || slot >= h.ids.size()) return;
        Listing li = listings.get(h.ids.get(slot));
        if (li == null) { open(p, h.mode, h.page); return; }
        if (h.mode == Mode.BROWSE) {
            String err = buy(p, li);
            p.sendMessage(err == null ? "§aCompraste el ítem por §e" + plugin.getEconomy().format(li.price) + "§a." : "§c" + err);
        } else {
            listings.remove(li.id);
            for (ItemStack r : p.getInventory().addItem(li.item).values()) p.getWorld().dropItemNaturally(p.getLocation(), r);
            save();
            p.sendMessage("§ePublicación cancelada.");
        }
        open(p, h.mode, h.page);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof Holder) e.setCancelled(true);
    }
}
