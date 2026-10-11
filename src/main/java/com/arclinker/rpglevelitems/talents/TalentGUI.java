package com.arclinker.rpglevelitems.talents;

import com.arclinker.rpglevelitems.RPGLevelTier;
import com.arclinker.rpglevelitems.stats.Stats;
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

/** Talent grid per branch (row/col from the talent definition). Click to spend a point. */
public final class TalentGUI implements Listener {
    private static final int[] TAB_SLOTS = {45, 46, 47, 48};
    private final RPGLevelTier plugin;

    public TalentGUI(RPGLevelTier plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    private static final class Holder implements InventoryHolder {
        final String branch;
        final Map<Integer, String> slots = new HashMap<>();
        final List<String> branchOrder = new ArrayList<>();
        Inventory inv;

        Holder(String branch) {
            this.branch = branch;
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

    public void open(Player p, String branch) {
        var tm = plugin.getTalents();
        List<TalentManager.Branch> branches = new ArrayList<>(tm.branches());
        if (branches.isEmpty()) {
            p.sendMessage("§cNo hay talentos cargados.");
            return;
        }
        String br = branch == null ? branches.get(0).id() : branch;
        Holder h = new Holder(br);
        TalentManager.Branch cur = branches.stream().filter(b -> b.id().equals(br)).findFirst().orElse(branches.get(0));
        Inventory inv = Bukkit.createInventory(h, 54, "§6§lTalentos §8- §f" + cur.name() + " §8(" + tm.available(p) + " pts)");
        h.inv = inv;
        ItemStack fill = icon(Material.GRAY_STAINED_GLASS_PANE, " ", new ArrayList<>(), false);
        for (int i = 0; i < 54; i++) inv.setItem(i, fill);
        for (TalentManager.Talent t : tm.all()) {
            if (!t.branch().equals(cur.id()) || t.row() > 4 || t.col() > 8) continue;
            int slot = t.row() * 9 + t.col();
            int lv = tm.rank(p, t.id());
            String why = tm.blocker(p, t);
            List<String> lore = new ArrayList<>();
            lore.add("§7" + t.description());
            lore.add("§7Nivel: §f" + lv + "§7/§f" + t.max() + " §8| coste " + t.cost());
            t.stats().forEach((k, v) -> lore.add("§9▸ " + Stats.line(k, v) + " §8por nivel" + (lv > 0 ? " §7(actual " + Stats.line(k, v * lv) + ")" : "")));
            if (!t.requires().isEmpty()) {
                for (Map.Entry<String, Integer> r : t.requires().entrySet()) {
                    var rt = tm.get(r.getKey());
                    boolean ok = tm.rank(p, r.getKey()) >= r.getValue();
                    lore.add((ok ? "§a✔ " : "§c✘ ") + "Requiere " + (rt == null ? r.getKey() : rt.name()) + " " + r.getValue());
                }
            }
            lore.add("");
            lore.add(lv >= t.max() ? "§6Nivel máximo" : why == null ? "§eClic para mejorar" : "§8" + why);
            h.slots.put(slot, t.id());
            inv.setItem(slot, icon(t.icon(), (lv >= t.max() ? "§6" : lv > 0 ? "§a" : why == null ? "§e" : "§7") + t.name(), lore, lv > 0));
        }
        for (int i = 0; i < branches.size() && i < TAB_SLOTS.length; i++) {
            TalentManager.Branch b = branches.get(i);
            h.branchOrder.add(b.id());
            inv.setItem(TAB_SLOTS[i], icon(b.icon(), (b.id().equals(cur.id()) ? "§a▶ " : "§e") + b.name(), new ArrayList<>(List.of("§7Clic para ver")), b.id().equals(cur.id())));
        }
        inv.setItem(49, icon(Material.EXPERIENCE_BOTTLE, "§6Puntos de talento", List.of("§7Disponibles: §a" + tm.available(p), "§7Totales: §f" + tm.totalPoints(p),
                "§8Se ganan con niveles de clase, prestigio y logros", "", "§7Reiniciar: §f/talent respec"), false));
        inv.setItem(53, icon(Material.BARRIER, "§cCerrar", new ArrayList<>(), false));
        p.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof Holder h)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p) || e.getClickedInventory() != e.getView().getTopInventory()) return;
        int slot = e.getRawSlot();
        if (slot == 53) {
            p.closeInventory();
            return;
        }
        for (int i = 0; i < TAB_SLOTS.length && i < h.branchOrder.size(); i++) {
            if (slot == TAB_SLOTS[i]) {
                open(p, h.branchOrder.get(i));
                return;
            }
        }
        String id = h.slots.get(slot);
        if (id == null) return;
        var t = plugin.getTalents().get(id);
        if (t != null && plugin.getTalents().upgrade(p, t)) open(p, h.branch);
        else if (t != null) {
            String why = plugin.getTalents().blocker(p, t);
            if (why != null) p.sendMessage("§c" + why);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof Holder) e.setCancelled(true);
    }
}
