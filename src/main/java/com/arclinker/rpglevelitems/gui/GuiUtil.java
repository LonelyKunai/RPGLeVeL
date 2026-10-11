package com.arclinker.rpglevelitems.gui;

import com.arclinker.rpglevelitems.RPGLevelTier;
import com.arclinker.rpglevelitems.rank.Rank;
import com.arclinker.rpglevelitems.rank.RankCategory;
import com.arclinker.rpglevelitems.rank.RankScope;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/** Shared helpers for the paginated menus (54 slots: 45 entries + navigation row). */
final class GuiUtil {
    static final int PAGE_SIZE = 45;
    static final int SLOT_PREV = 45, SLOT_RANK_INFO = 47, SLOT_RANKING = 49, SLOT_CLOSE = 51, SLOT_NEXT = 53;

    private GuiUtil() {}

    static ItemStack item(Material mat, String name, List<String> lore, boolean glow) {
        ItemStack it = new ItemStack(mat);
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            if (lore != null) meta.setLore(lore);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_UNBREAKABLE, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
            if (glow) meta.setEnchantmentGlintOverride(true);
            it.setItemMeta(meta);
        }
        return it;
    }

    static ItemStack item(Material mat, String name, String... lore) {
        return item(mat, name, new ArrayList<>(List.of(lore)), false);
    }

    /** Fills the navigation row: prev/next, own rank summary, ranking button and close. */
    static void navigation(Inventory inv, int page, int pages, RPGLevelTier plugin, Player player, RankCategory cat) {
        ItemStack filler = item(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int s = PAGE_SIZE; s < 54; s++) inv.setItem(s, filler);
        if (page > 0) inv.setItem(SLOT_PREV, item(Material.ARROW, "§ePágina anterior"));
        if (page < pages - 1) inv.setItem(SLOT_NEXT, item(Material.ARROW, "§ePágina siguiente"));
        inv.setItem(SLOT_CLOSE, item(Material.BARRIER, "§cCerrar"));
        if (cat != null) {
            inv.setItem(SLOT_RANK_INFO, rankSummary(plugin, player, cat));
            inv.setItem(SLOT_RANKING, item(Material.GOLDEN_HELMET, "§6Ver ranking de " + cat.label,
                    "§7Mejores jugadores en " + cat.label.toLowerCase(), "", "§eClic para abrir"));
        }
    }

    static ItemStack rankSummary(RPGLevelTier plugin, Player player, RankCategory cat) {
        return rankSummary(plugin, player, cat, null);
    }

    /** Both ladders (permanent + season) of the player; {@code highlight} marks the one being viewed. */
    static ItemStack rankSummary(RPGLevelTier plugin, Player player, RankCategory cat, RankScope highlight) {
        var rm = plugin.getRankManager();
        var id = player.getUniqueId();
        List<String> lore = new ArrayList<>();
        for (RankScope sc : RankScope.values()) {
            Rank rank = rm.getRank(id, cat, sc);
            lore.add((sc == highlight ? "§a▶ " : "§e") + sc.label + "§7: " + rm.fullDisplay(rank, sc) + " §7(" + rm.getPoints(id, cat, sc) + " pts)");
            long toNext = rm.pointsToNext(id, cat, sc);
            lore.add("  " + (toNext < 0 ? "§6¡Rango máximo!" : "§7Siguiente (" + rm.display(rank.next(), sc) + "§7): §f" + toNext + " pts"));
            lore.add("  " + rm.progressBar(id, cat, sc, 20));
            int pos = rm.position(id, cat, sc);
            lore.add("  " + (pos > 0 ? "§7Posición: §e#" + pos : "§7Posición: §8sin puntos"));
        }
        lore.add("§7Temporada §f" + rm.season() + " §7termina en: §f" + rm.timeLeft());
        return item(cat.icon, "§aTus rangos de " + cat.label, lore, false);
    }

    static int pages(int total) {
        return Math.max(1, (total + PAGE_SIZE - 1) / PAGE_SIZE);
    }

    static int clamp(int page, int pages) {
        return Math.max(0, Math.min(page, pages - 1));
    }
}
