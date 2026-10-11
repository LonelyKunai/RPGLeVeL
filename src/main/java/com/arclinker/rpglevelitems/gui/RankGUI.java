package com.arclinker.rpglevelitems.gui;

import com.arclinker.rpglevelitems.RPGLevelTier;
import com.arclinker.rpglevelitems.rank.PlayerRecord;
import com.arclinker.rpglevelitems.rank.Rank;
import com.arclinker.rpglevelitems.rank.RankCategory;
import com.arclinker.rpglevelitems.rank.RankScope;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.List;

/** Leaderboard between players for one category and ladder (permanent F..U or season F-S1..U-S1). */
public class RankGUI {
    static final int[] TOP_SLOTS = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34};
    static final int[] TAB_SLOTS = {45, 46, 47, 48, 50};
    static final int SLOT_MINE = 49, SLOT_SCOPE = 51, SLOT_CLOSE = 53;

    private final RPGLevelTier plugin;

    public RankGUI(RPGLevelTier plugin) {
        this.plugin = plugin;
    }

    public void open(Player player, RankCategory cat) {
        open(player, cat, RankScope.PERMANENT);
    }

    public void open(Player player, RankCategory cat, RankScope scope) {
        var rm = plugin.getRankManager();
        MenuHolder holder = new MenuHolder(MenuHolder.Type.RANK, 0, new ArrayList<>(), cat, scope);
        String title = scope == RankScope.PERMANENT ? "Permanente" : "Temporada " + rm.season();
        Inventory inv = Bukkit.createInventory(holder, 54, "§6§lRanking §8- " + cat.coloredLabel() + " §8(" + title + ")");
        holder.setInventory(inv);

        ItemStack filler = GuiUtil.item(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 54; i++) inv.setItem(i, filler);

        List<PlayerRecord> top = rm.top(cat, scope, TOP_SLOTS.length);
        for (int i = 0; i < TOP_SLOTS.length; i++) {
            if (i >= top.size()) {
                inv.setItem(TOP_SLOTS[i], null);
                continue;
            }
            PlayerRecord r = top.get(i);
            long pts = rm.pts(r, cat, scope);
            Rank rank = rm.getRank(pts, scope);
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            meta.setOwningPlayer(Bukkit.getOfflinePlayer(r.uuid));
            meta.setDisplayName((i < 3 ? "§6" : "§e") + "#" + (i + 1) + " §f" + (r.name.isEmpty() ? "???" : r.name));
            List<String> lore = new ArrayList<>();
            lore.add("§7Rango: " + rm.fullDisplay(rank, scope));
            lore.add("§7Puntos: §f" + pts);
            if (cat == RankCategory.CLASS && !r.classId.isEmpty()) lore.add("§7Clase: §f" + r.classId);
            if (cat == RankCategory.SUBCLASS && !r.subclassId.isEmpty()) lore.add("§7Subclase: §f" + r.subclassId);
            meta.setLore(lore);
            head.setItemMeta(meta);
            inv.setItem(TOP_SLOTS[i], head);
        }
        if (top.isEmpty()) {
            inv.setItem(22, GuiUtil.item(Material.PAPER, "§7Aún no hay jugadores en este ranking"));
        }

        RankCategory[] cats = RankCategory.values();
        for (int i = 0; i < cats.length; i++) {
            RankCategory c = cats[i];
            inv.setItem(TAB_SLOTS[i], GuiUtil.item(c.icon, c.coloredLabel(),
                    new ArrayList<>(List.of(c == cat ? "§a▶ Mostrando" : "§eClic para ver")), c == cat));
        }
        inv.setItem(SLOT_MINE, GuiUtil.rankSummary(plugin, player, cat, scope));
        List<String> sl = new ArrayList<>();
        sl.add("§7Mostrando: §f" + scope.label);
        sl.add(scope == RankScope.PERMANENT ? "§8Rangos F … U, nunca se reinician" : "§8Rangos F-S" + rm.season() + " … U-S" + rm.season() + ", se reinician");
        sl.add("§7Temporada " + rm.season() + " termina en: §f" + rm.timeLeft());
        sl.add("");
        sl.add("§eClic para ver " + scope.other().label.toLowerCase());
        inv.setItem(SLOT_SCOPE, GuiUtil.item(scope == RankScope.PERMANENT ? Material.NETHER_STAR : Material.CLOCK,
                "§6Ladder: " + scope.label, sl, false));
        inv.setItem(SLOT_CLOSE, GuiUtil.item(Material.BARRIER, "§cCerrar"));
        player.openInventory(inv);
    }
}
