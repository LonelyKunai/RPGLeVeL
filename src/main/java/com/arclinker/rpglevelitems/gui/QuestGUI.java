package com.arclinker.rpglevelitems.gui;

import com.arclinker.rpglevelitems.DailyQuestManager;
import com.arclinker.rpglevelitems.DailyQuestManager.QuestDefinition;
import com.arclinker.rpglevelitems.RPGLevelTier;
import com.arclinker.rpglevelitems.rank.RankCategory;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.util.ArrayList;
import java.util.List;

/** Shows today's quests with progress. */
public class QuestGUI {
    private final RPGLevelTier plugin;

    public QuestGUI(RPGLevelTier plugin) {
        this.plugin = plugin;
    }

    public void open(Player player) {
        DailyQuestManager qm = plugin.getDailyQuestManager();
        List<String> today = qm.getTodaysQuests(player);

        List<String> slots = new ArrayList<>();
        MenuHolder holder = new MenuHolder(MenuHolder.Type.QUEST, 0, slots, RankCategory.QUEST);
        Inventory inv = Bukkit.createInventory(holder, 54, "§6§lMisiones Diarias");
        holder.setInventory(inv);

        int[] layout = {20, 21, 22, 23, 24};
        for (int i = 0; i < today.size() && i < layout.length; i++) {
            QuestDefinition def = qm.getDefinition(today.get(i));
            if (def == null) continue;
            boolean done = qm.isDone(player, def.id);
            int progress = done ? def.amount : qm.getProgress(player, def.id);
            List<String> lore = new ArrayList<>();
            lore.add("§7" + def.description);
            lore.add("");
            lore.add("§7Rango: " + def.rank().display());
            lore.add("§7Progreso: §f" + progress + "§7/§f" + def.amount);
            lore.add(bar(progress, def.amount));
            lore.add("§7Recompensa: §e+" + def.rewardXp + " pts de misiones");
            lore.add(done ? "§a✔ Completada" : "§7En progreso");
            Material mat = done ? Material.LIME_DYE : switch (def.type) {
                case "kill" -> Material.IRON_SWORD;
                case "gather" -> Material.IRON_PICKAXE;
                case "fish" -> Material.FISHING_ROD;
                default -> Material.BOOK;
            };
            while (slots.size() <= layout[i]) slots.add(null);
            inv.setItem(layout[i], GuiUtil.item(mat, (done ? "§a" : "§b") + def.name, lore, done));
            slots.set(layout[i], def.id);
        }
        GuiUtil.navigation(inv, 0, 1, plugin, player, RankCategory.QUEST);
        player.openInventory(inv);
    }

    private static String bar(int value, int max) {
        int filled = (int) Math.round(10.0 * Math.min(value, max) / Math.max(1, max));
        StringBuilder sb = new StringBuilder("§a");
        for (int i = 0; i < 10; i++) {
            if (i == filled) sb.append("§7");
            sb.append('■');
        }
        return sb.toString();
    }
}
