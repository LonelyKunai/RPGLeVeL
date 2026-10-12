package com.arclinker.rpglevelitems.itemcreator;

import com.arclinker.rpglevelitems.RPGLevelTier;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;

/**
 * Keeps .rlvp identity on placeable items + AnacroniCraft backpacks 27-135 paginated + RecipeBook GUI shared
 * Original: block identity (chunk PDC) + drop real .rlvp item
 * AnacroniCraft: mochilas T1-T5 27/45/81/108/135 - >45 usa GUI 54 nav 45/49/53 compartido con libro recetas
 * MC 26.3 - Sin ShadowJar - data/items/ orden 01_ a 05_ .rlvp
 */
public final class RlvpBlockListener implements Listener {
    private final RPGLevelTier plugin;
    private final NamespacedKey blocksKey;
    private final NamespacedKey backpackTierKey;
    private final NamespacedKey backpackIdKey;
    private final NamespacedKey backpackSlotsKey;

    private final Map<UUID, BackpackData> openBackpacks = new HashMap<>();
    private final Map<UUID, Integer> currentPage = new HashMap<>();
    private final Map<UUID, String> currentCategory = new HashMap<>();

    public static class BackpackData {
        String id;
        int tier;
        int totalSlots;
        List<ItemStack> contents;
        boolean paginated;
        int pages;
        int slotsPerPage = 45;
        BackpackData(String id, int tier, int slots) {
            this.id = id;
            this.tier = tier;
            this.totalSlots = slots;
            this.paginated = slots > 45;
            this.pages = (int) Math.ceil((double) slots / 45.0);
            this.contents = new ArrayList<>(Collections.nCopies(slots, null));
        }
    }

    public enum MasterCategory {
        ITEMS_BASE("§fItems Base", Material.IRON_INGOT, "01_items_base.rlvp"),
        ERAS("§aEras AnacroniCraft", Material.CLOCK, "02_anacronicraft_eras.rlvp"),
        CLASES("§6Clases y Subclases", Material.NETHERITE_SWORD, "03_clases_y_subclases.rlvp"),
        MECANISMOS("§cMecanismos Industriales", Material.PISTON, "04_mecanismos_industriales.rlvp"),
        UNIVERSAL("§aUso Universal", Material.CRAFTING_TABLE, "05_uso_universal.rlvp");

        final String display;
        final Material icon;
        final String rlvpFile;
        MasterCategory(String display, Material icon, String rlvpFile) {
            this.display = display; this.icon = icon; this.rlvpFile = rlvpFile;
        }
    }

    public RlvpBlockListener(RPGLevelTier plugin) {
        this.plugin = plugin;
        this.blocksKey = new NamespacedKey(RPGLevelTier.KEY_NS, "rlvp_blocks");
        this.backpackTierKey = new NamespacedKey(RPGLevelTier.KEY_NS, "backpack_tier");
        this.backpackIdKey = new NamespacedKey(RPGLevelTier.KEY_NS, "backpack_id");
        this.backpackSlotsKey = new NamespacedKey(RPGLevelTier.KEY_NS, "backpack_slots");
    }

    private static String pos(Block b) {
        return (b.getX() & 15) + "," + b.getY() + "," + (b.getZ() & 15);
    }

    private List<String> read(Chunk c) {
        List<String> l = c.getPersistentDataContainer().get(blocksKey, PersistentDataType.LIST.strings());
        return l == null ? new ArrayList<>() : new ArrayList<>(l);
    }

    private void write(Chunk c, List<String> l) {
        PersistentDataContainer pdc = c.getPersistentDataContainer();
        if (l.isEmpty()) pdc.remove(blocksKey); else pdc.set(blocksKey, PersistentDataType.LIST.strings(), l);
    }

    public String getBlockId(Block b) { return idAt(b); }

    private String idAt(Block b) {
        String p = pos(b) + "|";
        for (String e : read(b.getChunk())) if (e.startsWith(p)) return e.substring(p.length());
        return null;
    }

    private String remove(Block b) {
        List<String> l = read(b.getChunk());
        String p = pos(b) + "|", found = null;
        for (Iterator<String> it = l.iterator(); it.hasNext(); ) {
            String e = it.next();
            if (e.startsWith(p)) { found = e.substring(p.length()); it.remove(); }
        }
        if (found != null) write(b.getChunk(), l);
        return found;
    }

    // ===== Original block identity logic =====
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (plugin.getRlvpHandler() == null) return;
        String id = plugin.getRlvpHandler().getId(event.getItemInHand());
        if (id == null) return;
        Block b = event.getBlockPlaced();
        List<String> l = read(b.getChunk());
        l.removeIf(e -> e.startsWith(pos(b) + "|"));
        l.add(pos(b) + "|" + id);
        write(b.getChunk(), l);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Block b = event.getBlock();
        String id = idAt(b);
        if (id == null) return;
        remove(b);
        if (event.getPlayer().getGameMode() == GameMode.CREATIVE) return;
        ItemStack item = plugin.getRlvpHandler() == null ? null : plugin.getRlvpHandler().getItem(id);
        if (item == null) return;
        event.setDropItems(false);
        b.getWorld().dropItemNaturally(b.getLocation().add(0.5, 0.5, 0.5), item);
    }

    private void dropAll(List<Block> blocks, boolean explosion) {
        for (Iterator<Block> it = blocks.iterator(); it.hasNext(); ) {
            Block b = it.next();
            String id = idAt(b);
            if (id == null) continue;
            remove(b);
            ItemStack item = plugin.getRlvpHandler() == null ? null : plugin.getRlvpHandler().getItem(id);
            if (item == null) continue;
            Location at = b.getLocation().add(0.5, 0.5, 0.5);
            b.setType(Material.AIR);
            at.getWorld().dropItemNaturally(at, item);
            it.remove();
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent event) { dropAll(event.blockList(), true); }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) { dropAll(event.blockList(), true); }

    @EventHandler(ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        for (Block b : event.getBlocks()) if (idAt(b) != null) { event.setCancelled(true); return; }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        for (Block b : event.getBlocks()) if (idAt(b) != null) { event.setCancelled(true); return; }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBurn(BlockBurnEvent event) { remove(event.getBlock()); }

    // ===== AnacroniCraft - Backpacks 27-135 paginated - GUI compartido con RecipeBook =====
    private boolean isBackpack(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        var pdc = item.getItemMeta().getPersistentDataContainer();
        if (pdc.has(backpackTierKey, PersistentDataType.INTEGER)) return true;
        String id = plugin.getRlvpHandler() != null ? plugin.getRlvpHandler().getId(item) : null;
        return id != null && id.startsWith("backpack_t");
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBackpackInteract(PlayerInteractEvent e) {
        if (e.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_AIR && e.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) return;
        ItemStack item = e.getItem();
        if (item == null || !isBackpack(item)) return;
        e.setCancelled(true);
        Player p = e.getPlayer();
        var pdc = item.getItemMeta().getPersistentDataContainer();
        int tier = pdc.getOrDefault(backpackTierKey, PersistentDataType.INTEGER, 1);
        String id = pdc.getOrDefault(backpackIdKey, PersistentDataType.STRING, "backpack_t" + tier);
        if (id.equals("backpack_t" + tier) && plugin.getRlvpHandler() != null) {
            String realId = plugin.getRlvpHandler().getId(item);
            if (realId != null) id = realId;
        }
        int slots = plugin.getRlvpHandler() != null ? plugin.getRlvpHandler().getBackpackSlots(id) : (tier == 1 ? 27 : tier == 2 ? 45 : tier == 3 ? 81 : tier == 4 ? 108 : 135);
        BackpackData data = new BackpackData(id, tier, slots);
        openBackpack(p, data, 0);
    }

    public void openBackpack(Player p, BackpackData data, int page) {
        if (!data.paginated) {
            Inventory inv = Bukkit.createInventory(null, data.totalSlots, "§6🎒 " + data.id + " [" + data.totalSlots + "] T" + data.tier);
            for (int i = 0; i < data.contents.size(); i++) if (data.contents.get(i) != null) inv.setItem(i, data.contents.get(i));
            openBackpacks.put(p.getUniqueId(), data);
            currentPage.put(p.getUniqueId(), 0);
            p.openInventory(inv);
            return;
        }
        Inventory inv = Bukkit.createInventory(null, 54, "§6🎒 " + data.id + " T" + data.tier + " Pag " + (page+1) + "/" + data.pages);
        int start = page * data.slotsPerPage;
        int end = Math.min(start + data.slotsPerPage, data.totalSlots);
        for (int i = start; i < end; i++) if (i < data.contents.size() && data.contents.get(i) != null) inv.setItem(i - start, data.contents.get(i));
        inv.setItem(45, nav(Material.ARROW, "§e← Anterior", page > 0));
        inv.setItem(46, pane()); inv.setItem(47, pane());
        inv.setItem(49, infoBackpack(data, page));
        inv.setItem(51, pane()); inv.setItem(52, pane());
        inv.setItem(53, nav(Material.ARROW, "§aSiguiente →", page < data.pages - 1));
        openBackpacks.put(p.getUniqueId(), data);
        currentPage.put(p.getUniqueId(), page);
        p.openInventory(inv);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (!openBackpacks.containsKey(p.getUniqueId()) && !currentCategory.containsKey(p.getUniqueId())) return;
        if (openBackpacks.containsKey(p.getUniqueId())) {
            BackpackData data = openBackpacks.get(p.getUniqueId());
            if (!data.paginated) return;
            int page = currentPage.getOrDefault(p.getUniqueId(), 0);
            if (e.getSlot() == 45 && page > 0) { e.setCancelled(true); openBackpack(p, data, page-1); }
            else if (e.getSlot() == 53 && page < data.pages - 1) { e.setCancelled(true); openBackpack(p, data, page+1); }
            else if (e.getSlot() >= 45) e.setCancelled(true);
        }
    }

    public void openMasterCategories(Player p) {
        Inventory inv = Bukkit.createInventory(null, 27, "§8📖 Libro Recetas - .rlvp - Categorias Maestras");
        List<MasterCategory> ordered = List.of(MasterCategory.ITEMS_BASE, MasterCategory.ERAS, MasterCategory.CLASES, MasterCategory.MECANISMOS, MasterCategory.UNIVERSAL);
        int[] slots = {10,11,13,15,16};
        for (int i = 0; i < ordered.size(); i++) {
            MasterCategory cat = ordered.get(i);
            ItemStack it = new ItemStack(cat.icon);
            ItemMeta m = it.getItemMeta();
            m.setDisplayName(cat.display);
            m.setLore(List.of("§7data/items/" + cat.rlvpFile, "§7Extension .rlvp", "§7Orden 01_ a 05_", "§7GUI 54 compartido mochilas >45"));
            it.setItemMeta(m);
            inv.setItem(slots[i], it);
        }
        p.openInventory(inv);
    }

    private ItemStack nav(Material mat, String name, boolean en) {
        ItemStack it = new ItemStack(en ? mat : Material.BARRIER);
        ItemMeta m = it.getItemMeta(); m.setDisplayName(en ? name : "§7" + name + " (bloqueado)"); it.setItemMeta(m); return it;
    }
    private ItemStack pane() {
        ItemStack it = new ItemStack(Material.GRAY_STAINED_GLASS_PANE); ItemMeta m = it.getItemMeta(); m.setDisplayName(" "); it.setItemMeta(m); return it;
    }
    private ItemStack infoBackpack(BackpackData data, int page) {
        ItemStack it = new ItemStack(Material.BOOK); ItemMeta m = it.getItemMeta();
        m.setDisplayName("§6" + data.id); m.setLore(List.of("§7Slots: " + data.totalSlots, "§7Pag: " + (page+1) + "/" + data.pages, "§7GUI compartido RecipeBook", "§7Tier: " + data.tier + "/5")); it.setItemMeta(m); return it;
    }
}
