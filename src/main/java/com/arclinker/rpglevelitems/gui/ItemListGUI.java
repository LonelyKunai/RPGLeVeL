package com.arclinker.rpglevelitems.gui;

import com.arclinker.rpglevelitems.RPGLevelTier;
import com.arclinker.rpglevelitems.itemcreator.RlvpHandler;
import com.arclinker.rpglevelitems.itemcreator.RlvpHandler.Category;
import com.arclinker.rpglevelitems.itemcreator.RlvpHandler.ItemInfo;
import org.bukkit.Bukkit;
import org.bukkit.Material;
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

import java.util.*;

/**
 * Item list with three levels of navigation:
 * <ol>
 *   <li>Category (one per .rlvp file: class, subclass, universal set, materials, stations), filterable by group</li>
 *   <li>Type inside the category: complete armor, weapons, offhand... (stations: by kind)</li>
 *   <li>Tier inside the type; the item view lists the pieces and, for armor, gives the complete set</li>
 * </ol>
 * Click an item to receive it (requires rpgleveltier.items).
 */
public class ItemListGUI implements Listener {

    public static final String PERMISSION = "rpgleveltier.items";
    private static final int PAGE_SIZE = 45;
    private static final int SLOT_PREV = 45, SLOT_BACK = 48, SLOT_INFO = 51, SLOT_CLOSE = 52, SLOT_NEXT = 53;
    private static final int[] GROUP_SLOTS = {46, 47, 48, 49, 50};
    private static final String[][] GROUPS = {{"all", "Todo", "COMPASS"}, {"classes", "Clases", "IRON_SWORD"}, {"subclasses", "Subclases", "NETHER_STAR"},
            {"universal", "Universal", "SHIELD"}, {"other", "Materiales y estaciones", "SMITHING_TABLE"}};
    private static final String[] ROMAN = {"I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};

    private enum View { CATEGORIES, TYPES, TIERS, ITEMS }

    private final RPGLevelTier plugin;

    public ItemListGUI(RPGLevelTier plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    /** Menu state carried by the inventory. */
    private static final class Holder implements InventoryHolder {
        View view = View.CATEGORIES;
        String group = "all", category = "", type = "";
        int tier = -1, page = 0;
        /** key per slot: category id / type key / tier number / item id. */
        List<String> keys = new ArrayList<>();
        Inventory inventory;

        @Override
        public @NotNull Inventory getInventory() {
            return inventory;
        }

        Holder copy() {
            Holder h = new Holder();
            h.view = view; h.group = group; h.category = category; h.type = type; h.tier = tier; h.page = page;
            return h;
        }
    }

    // ---- entry points ------------------------------------------------------------------

    public void open(Player player, int page) {
        Holder h = new Holder();
        h.page = Math.max(0, page); // zero-based
        show(player, h);
    }

    public void open(Player player, String category) {
        Holder h = new Holder();
        RlvpHandler rh = plugin.getRlvpHandler();
        if (rh != null && rh.getCategory(category) != null) {
            h.category = category.toLowerCase(Locale.ROOT);
            h.view = View.TYPES;
        }
        show(player, h);
    }

    // ---- rendering -----------------------------------------------------------------------

    private static String typeLabel(String type) {
        return switch (type) {
            case "armor" -> "Armadura completa";
            case "weapon" -> "Armas";
            case "offhand" -> "Objeto secundario";
            case "material" -> "Materiales";
            case "station" -> "Estaciones";
            default -> "Otros";
        };
    }

    private static ItemStack button(Material mat, String name, String... lore) {
        ItemStack it = new ItemStack(mat);
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            if (lore.length > 0) meta.setLore(new ArrayList<>(Arrays.asList(lore)));
            meta.addItemFlags(org.bukkit.inventory.ItemFlag.values());
            it.setItemMeta(meta);
        }
        return it;
    }

    private List<ItemInfo> infos(Category c) {
        List<ItemInfo> out = new ArrayList<>();
        for (String id : c.ids()) out.add(plugin.getRlvpHandler().getInfo(id));
        return out;
    }

    /** Sub-category key of an item: its type, or (stations) its kind. */
    private static String subKey(ItemInfo i) {
        return i.type().equals("station") ? "station:" + i.piece() : i.type();
    }

    private static String subLabel(String key) {
        if (key.startsWith("station:")) {
            return switch (key.substring(8)) {
                case "class" -> "Estaciones de clase";
                case "subclass" -> "Estaciones de subclase";
                default -> "Estaciones universales";
            };
        }
        return typeLabel(key);
    }

    private void show(Player player, Holder h) {
        RlvpHandler rh = plugin.getRlvpHandler();
        if (rh == null || rh.getCategories().isEmpty()) {
            player.sendMessage("§cNo hay ítems cargados. Revisa la consola y la carpeta plugins/RPGLeVeL/data/items (solo archivos .rlvp).");
            return;
        }
        // lists (keys + icons) of the current view
        List<String> keys = new ArrayList<>();
        List<ItemStack> icons = new ArrayList<>();
        String title;
        int extraBackSlot = SLOT_BACK;

        switch (h.view) {
            case CATEGORIES -> {
                title = "Ítems RLVP";
                for (Category c : rh.getCategories()) {
                    boolean other = !(c.group().equals("classes") || c.group().equals("subclasses") || c.group().equals("universal"));
                    if (!(h.group.equals("all") || h.group.equals(c.group()) || (h.group.equals("other") && other))) continue;
                    keys.add(c.id());
                    icons.add(button(c.icon(), "§6§l" + c.name(), "§7Grupo: §f" + c.group(), "§7Ítems: §f" + c.ids().size(), "", "§eClic para abrir"));
                }
            }
            case TYPES -> {
                Category c = rh.getCategory(h.category);
                title = c == null ? "Ítems" : c.name();
                if (c != null) {
                    LinkedHashMap<String, List<ItemInfo>> by = new LinkedHashMap<>();
                    for (ItemInfo i : infos(c)) by.computeIfAbsent(subKey(i), k -> new ArrayList<>()).add(i);
                    for (Map.Entry<String, List<ItemInfo>> e : by.entrySet()) {
                        keys.add(e.getKey());
                        List<ItemInfo> list = e.getValue();
                        Material icon = list.get(0).material();
                        if (e.getKey().equals("armor")) for (ItemInfo i : list) if ("chestplate".equals(i.piece())) { icon = i.material(); break; }
                        TreeSet<Integer> tiers = new TreeSet<>();
                        for (ItemInfo i : list) if (i.tier() > 0) tiers.add(i.tier());
                        List<String> lore = new ArrayList<>();
                        lore.add("§7Ítems: §f" + list.size());
                        if (e.getKey().equals("armor")) lore.add("§7Casco, pechera, pantalones y botas");
                        if (!tiers.isEmpty()) lore.add("§7Tiers: §f" + tiers.first() + "-" + tiers.last());
                        lore.add("");
                        lore.add("§eClic para abrir");
                        icons.add(button(icon, "§e§l" + subLabel(e.getKey()), lore.toArray(new String[0])));
                    }
                    keys.add("*");
                    icons.add(button(Material.CHEST, "§bTodos los ítems de la categoría", "§7Ítems: §f" + c.ids().size(), "", "§eClic para listar"));
                }
            }
            case TIERS -> {
                Category c = rh.getCategory(h.category);
                title = (c == null ? "" : c.name()) + " §8> §6" + subLabel(h.type);
                if (c != null) {
                    TreeMap<Integer, Integer> counts = new TreeMap<>();
                    for (ItemInfo i : infos(c)) if (matches(i, h.type)) counts.merge(i.tier(), 1, Integer::sum);
                    for (Map.Entry<Integer, Integer> e : counts.entrySet()) {
                        keys.add(String.valueOf(e.getKey()));
                        int t = e.getKey();
                        List<String> lore = new ArrayList<>();
                        lore.add("§7Piezas: §f" + e.getValue());
                        if (h.type.equals("armor")) lore.add("§7Clic: ver piezas y obtener el set completo");
                        lore.add("");
                        lore.add("§eClic para abrir");
                        icons.add(button(tierIcon(t), "§6§lTier " + (t >= 1 && t <= ROMAN.length ? ROMAN[t - 1] : t), lore.toArray(new String[0])));
                    }
                    keys.add("*");
                    icons.add(button(Material.CHEST, "§bTodos los tiers", "§7Ítems: §f" + counts.values().stream().mapToInt(Integer::intValue).sum(), "", "§eClic para listar"));
                }
            }
            default -> {
                Category c = rh.getCategory(h.category);
                title = (c == null ? "" : c.name())
                        + (h.type.isEmpty() || h.type.equals("*") ? "" : " §8> §6" + subLabel(h.type))
                        + (h.tier > 0 ? " §8> §6Tier " + (h.tier <= ROMAN.length ? ROMAN[h.tier - 1] : h.tier) : "");
                if (c != null) {
                    for (ItemInfo i : infos(c)) {
                        if (!h.type.isEmpty() && !h.type.equals("*") && !matches(i, h.type)) continue;
                        if (h.tier > 0 && i.tier() != h.tier) continue;
                        keys.add(i.id());
                    }
                }
            }
        }

        int pages = Math.max(1, (keys.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        h.page = Math.max(0, Math.min(h.page, pages - 1));
        int from = h.page * PAGE_SIZE, to = Math.min(keys.size(), from + PAGE_SIZE);
        h.keys = new ArrayList<>(keys.subList(from, to));

        Inventory inv = Bukkit.createInventory(h, 54, ("§6§l" + title).substring(0, Math.min(("§6§l" + title).length(), 60)) + " §8(" + (h.page + 1) + "/" + pages + ")");
        h.inventory = inv;
        for (int i = 0; i < h.keys.size(); i++) {
            if (h.view == View.ITEMS) {
                ItemStack item = rh.getItem(h.keys.get(i));
                if (item == null) continue;
                ItemMeta meta = item.getItemMeta();
                if (meta != null) {
                    List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
                    lore.add("");
                    lore.add("§8ID: §7" + h.keys.get(i));
                    lore.add("§eClic para obtener §8| §eShift: pila");
                    meta.setLore(lore);
                    item.setItemMeta(meta);
                }
                inv.setItem(i, item);
            } else {
                inv.setItem(i, icons.get(from + i));
            }
        }

        ItemStack filler = button(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int s = PAGE_SIZE; s < 54; s++) inv.setItem(s, filler);
        if (h.page > 0) inv.setItem(SLOT_PREV, button(Material.ARROW, "§ePágina anterior"));
        if (h.page < pages - 1) inv.setItem(SLOT_NEXT, button(Material.ARROW, "§ePágina siguiente"));
        inv.setItem(SLOT_CLOSE, button(Material.BARRIER, "§cCerrar"));
        if (h.view == View.CATEGORIES) {
            for (int g = 0; g < GROUPS.length; g++) {
                boolean sel = GROUPS[g][0].equals(h.group);
                Material m = Material.matchMaterial(GROUPS[g][2]);
                inv.setItem(GROUP_SLOTS[g], button(m == null ? Material.PAPER : m, (sel ? "§a▶ " : "§e") + GROUPS[g][1], sel ? "§7Mostrando" : "§eClic para filtrar"));
            }
        } else {
            inv.setItem(extraBackSlot, button(Material.OAK_DOOR, "§eVolver"));
            if (h.view == View.ITEMS && h.type.equals("armor") && h.tier > 0 && !h.keys.isEmpty()) {
                inv.setItem(50, button(Material.NETHERITE_CHESTPLATE, "§a§lObtener set completo", "§7Casco, pechera, pantalones y botas", "§7del tier seleccionado", "", "§eClic para obtener"));
            }
        }
        inv.setItem(SLOT_INFO, button(Material.BOOK, "§6Página " + (h.page + 1) + "/" + pages, "§7Entradas: §f" + keys.size(),
                "§8Total de ítems: " + rh.getItemIds().size()));
        player.openInventory(inv);
    }

    private static boolean matches(ItemInfo i, String type) {
        return type.equals("*") || type.isEmpty() || subKey(i).equals(type);
    }

    private static Material tierIcon(int tier) {
        return switch (tier) {
            case 1 -> Material.COPPER_INGOT;
            case 2 -> Material.IRON_INGOT;
            case 3 -> Material.GOLD_INGOT;
            case 4 -> Material.DIAMOND;
            case 5 -> Material.NETHERITE_INGOT;
            default -> Material.PAPER;
        };
    }

    // ---- clicks --------------------------------------------------------------------------

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof Holder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getClickedInventory() != event.getView().getTopInventory()) return;
        int slot = event.getRawSlot();
        ItemStack current = event.getCurrentItem();

        if (slot == SLOT_CLOSE) {
            player.closeInventory();
            return;
        }
        if (slot == SLOT_PREV && holder.page > 0) {
            Holder n = holder.copy();
            n.page--;
            show(player, n);
            return;
        }
        if (slot == SLOT_NEXT && current != null && current.getType() == Material.ARROW) {
            Holder n = holder.copy();
            n.page++;
            show(player, n);
            return;
        }
        if (holder.view == View.CATEGORIES) {
            for (int g = 0; g < GROUP_SLOTS.length; g++) {
                if (slot == GROUP_SLOTS[g]) {
                    Holder n = new Holder();
                    n.group = GROUPS[g][0];
                    show(player, n);
                    return;
                }
            }
        } else if (slot == SLOT_BACK) {
            Holder n = holder.copy();
            n.page = 0;
            switch (holder.view) {
                case TYPES -> { n.view = View.CATEGORIES; n.category = ""; }
                case TIERS -> { n.view = View.TYPES; n.type = ""; }
                default -> {
                    if (holder.tier > 0) { n.view = View.TIERS; n.tier = -1; }
                    else { n.view = View.TYPES; n.type = ""; }
                }
            }
            show(player, n);
            return;
        }
        if (holder.view == View.ITEMS && slot == 50 && current != null && current.getType() == Material.NETHERITE_CHESTPLATE) {
            giveSet(player, holder);
            return;
        }
        if (slot < 0 || slot >= holder.keys.size()) return;
        String key = holder.keys.get(slot);
        Holder n = holder.copy();
        n.page = 0;
        switch (holder.view) {
            case CATEGORIES -> {
                n.view = View.TYPES;
                n.category = key;
                show(player, n);
            }
            case TYPES -> {
                n.type = key;
                RlvpHandler rh = plugin.getRlvpHandler();
                Category c = rh.getCategory(holder.category);
                boolean tiered = false;
                if (c != null) for (ItemInfo i : infos(c)) if (matches(i, key) && i.tier() > 0) { tiered = true; break; }
                n.view = tiered && !key.equals("*") ? View.TIERS : View.ITEMS;
                n.tier = -1;
                show(player, n);
            }
            case TIERS -> {
                n.view = View.ITEMS;
                n.tier = key.equals("*") ? -1 : Integer.parseInt(key);
                show(player, n);
            }
            default -> giveItem(player, key, event.isShiftClick());
        }
    }

    private boolean allowed(Player player) {
        if (player.hasPermission(PERMISSION)) return true;
        player.sendMessage("§cNo tienes permiso para obtener ítems.");
        return false;
    }

    private void giveItem(Player player, String id, boolean stack) {
        if (!allowed(player)) return;
        ItemStack item = plugin.getRlvpHandler().getItem(id);
        if (item == null) return;
        int amount = stack ? item.getMaxStackSize() : 1;
        item.setAmount(amount);
        if (!player.getInventory().addItem(item).isEmpty()) {
            player.sendMessage("§cNo hay espacio en el inventario.");
            return;
        }
        player.sendMessage("§eHas recibido §f" + id.toLowerCase(Locale.ROOT) + (amount > 1 ? " §ex" + amount : "") + "§e.");
    }

    private void giveSet(Player player, Holder holder) {
        if (!allowed(player)) return;
        int given = 0;
        for (String id : holder.keys) {
            ItemStack item = plugin.getRlvpHandler().getItem(id);
            if (item == null) continue;
            for (ItemStack rest : player.getInventory().addItem(item).values()) player.getWorld().dropItemNaturally(player.getLocation(), rest);
            given++;
        }
        player.sendMessage("§aRecibiste el set completo §7(" + given + " piezas).");
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Holder) event.setCancelled(true);
    }
}
