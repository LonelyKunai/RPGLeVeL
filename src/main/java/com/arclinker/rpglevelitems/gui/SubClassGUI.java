package com.arclinker.rpglevelitems.gui;

import com.arclinker.rpglevelitems.RPGLevelTier;
import com.arclinker.rpglevelitems.SubClassManager.SubClassInfo;
import com.arclinker.rpglevelitems.rank.RankCategory;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.util.ArrayList;
import java.util.List;

/** Paginated menu with the subclasses of the player's current class. */
public class SubClassGUI {
    private final RPGLevelTier plugin;

    public SubClassGUI(RPGLevelTier plugin) {
        this.plugin = plugin;
    }

    public void open(Player player) {
        open(player, 0);
    }

    public void open(Player player, int page) {
        String classId = plugin.getClassManager().getClassId(player);
        if (classId.isEmpty()) {
            player.sendMessage("§cDebes seleccionar una clase primero (/class).");
            return;
        }
        List<String> all = plugin.getSubClassManager().getSubClassIdsOf(classId);
        int pages = GuiUtil.pages(all.size());
        int p = GuiUtil.clamp(page, pages);
        String current = plugin.getSubClassManager().getSubClassId(player);
        String rank = plugin.getRankManager().getRank(player.getUniqueId(), RankCategory.SUBCLASS).display();

        List<String> slots = new ArrayList<>();
        MenuHolder holder = new MenuHolder(MenuHolder.Type.SUBCLASS, p, slots, RankCategory.SUBCLASS);
        Inventory inv = Bukkit.createInventory(holder, 54, "§6§lSelección de Subclase §8(" + (p + 1) + "/" + pages + ")");
        holder.setInventory(inv);

        for (int i = p * GuiUtil.PAGE_SIZE; i < Math.min(all.size(), (p + 1) * GuiUtil.PAGE_SIZE); i++) {
            SubClassInfo info = plugin.getSubClassManager().getInfo(all.get(i));
            boolean selected = info.id.equalsIgnoreCase(current);
            List<String> lore = new ArrayList<>();
            lore.add("§7" + info.description);
            lore.add("");
            lore.add(selected ? "§a✔ Subclase actual §7(tu rango: " + rank + "§7)" : "§eClic para seleccionar");
            inv.setItem(slots.size(), GuiUtil.item(info.icon, "§b" + info.name, lore, selected));
            slots.add(info.id);
        }
        GuiUtil.navigation(inv, p, pages, plugin, player, RankCategory.SUBCLASS);
        player.openInventory(inv);
    }
}
