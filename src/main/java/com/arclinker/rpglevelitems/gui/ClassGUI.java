package com.arclinker.rpglevelitems.gui;

import com.arclinker.rpglevelitems.ClassManager;
import com.arclinker.rpglevelitems.RPGLevelTier;
import com.arclinker.rpglevelitems.rank.RankCategory;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.util.ArrayList;
import java.util.List;

/** Paginated class selection menu. */
public class ClassGUI {
    private final RPGLevelTier plugin;

    public ClassGUI(RPGLevelTier plugin) {
        this.plugin = plugin;
    }

    public void open(Player player) {
        open(player, 0);
    }

    public void open(Player player, int page) {
        List<String> all = new ArrayList<>(plugin.getClassManager().getClassIds());
        int pages = GuiUtil.pages(all.size());
        int p = GuiUtil.clamp(page, pages);
        String current = plugin.getClassManager().getClassId(player);
        String rank = plugin.getRankManager().getRank(player.getUniqueId(), RankCategory.CLASS).display();

        List<String> slots = new ArrayList<>();
        MenuHolder holder = new MenuHolder(MenuHolder.Type.CLASS, p, slots, RankCategory.CLASS);
        Inventory inv = Bukkit.createInventory(holder, 54, "§6§lSelección de Clase §8(" + (p + 1) + "/" + pages + ")");
        holder.setInventory(inv);

        for (int i = p * GuiUtil.PAGE_SIZE; i < Math.min(all.size(), (p + 1) * GuiUtil.PAGE_SIZE); i++) {
            ClassManager.ClassInfo info = plugin.getClassManager().getClassInfo(all.get(i));
            boolean selected = info.id.equalsIgnoreCase(current);
            List<String> lore = new ArrayList<>();
            lore.add("§7" + info.description);
            lore.add("");
            int subs = plugin.getSubClassManager().getSubClassIdsOf(info.id).size();
            lore.add("§7Subclases: §f" + subs);
            lore.add(selected ? "§a✔ Clase actual §7(tu rango: " + rank + "§7)" : "§eClic para seleccionar");
            inv.setItem(slots.size(), GuiUtil.item(info.icon, "§b" + info.name, lore, selected));
            slots.add(info.id);
        }
        GuiUtil.navigation(inv, p, pages, plugin, player, RankCategory.CLASS);
        player.openInventory(inv);
    }
}
