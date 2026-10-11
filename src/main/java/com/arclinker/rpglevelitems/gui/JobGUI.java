package com.arclinker.rpglevelitems.gui;

import com.arclinker.rpglevelitems.JobManager;
import com.arclinker.rpglevelitems.RPGLevelTier;
import com.arclinker.rpglevelitems.rank.RankCategory;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.util.ArrayList;
import java.util.List;

/** Paginated job menu. Left click = slot 1, right click = slot 2, shift+click = remove. */
public class JobGUI {
    private final RPGLevelTier plugin;

    public JobGUI(RPGLevelTier plugin) {
        this.plugin = plugin;
    }

    public void open(Player player) {
        open(player, 0);
    }

    public void open(Player player, int page) {
        JobManager jm = plugin.getJobManager();
        List<String> all = new ArrayList<>(jm.getJobIds());
        int pages = GuiUtil.pages(all.size());
        int p = GuiUtil.clamp(page, pages);
        String s1 = jm.getGlobalJob(player, 1), s2 = jm.getGlobalJob(player, 2);

        List<String> slots = new ArrayList<>();
        MenuHolder holder = new MenuHolder(MenuHolder.Type.JOB, p, slots, RankCategory.JOB);
        Inventory inv = Bukkit.createInventory(holder, 54, "§6§lSelección de Trabajo §8(" + (p + 1) + "/" + pages + ")");
        holder.setInventory(inv);

        for (int i = p * GuiUtil.PAGE_SIZE; i < Math.min(all.size(), (p + 1) * GuiUtil.PAGE_SIZE); i++) {
            JobManager.JobInfo info = jm.getJobInfo(all.get(i));
            boolean active = jm.hasJob(player, info.id);
            List<String> lore = new ArrayList<>();
            lore.add("§7" + info.description);
            lore.add("§7Bonus: §f" + info.bonus);
            lore.add("");
            int xp = jm.getJobXP(player, info.id);
            int jlv = JobManager.levelForXp(xp);
            lore.add("§7Nivel: §e" + jlv + " §7(" + xp + " XP)");
            for (var pe : info.perks.entrySet()) {
                StringBuilder sb = new StringBuilder(jlv >= pe.getKey() ? "§a✔ " : "§8✘ ").append("Nv").append(pe.getKey()).append(": ");
                pe.getValue().forEach((k, v) -> sb.append(com.arclinker.rpglevelitems.stats.Stats.line(k, v)).append(' '));
                lore.add(sb.toString());
            }
            if (info.id.equalsIgnoreCase(s1)) lore.add("§a✔ Ranura 1");
            if (info.id.equalsIgnoreCase(s2)) lore.add("§a✔ Ranura 2");
            var jabLines = plugin.getJobAbilities().describe(player, info.id);
            if (!jabLines.isEmpty()) {
                lore.add("§dHabilidades de oficio:");
                jabLines.forEach(l -> lore.add(" " + l));
            }
            lore.add("");
            lore.add("§eClic izq: ranura 1  §8|  §eClic der: ranura 2");
            lore.add("§eShift+clic: quitar");
            inv.setItem(slots.size(), GuiUtil.item(info.icon, "§b" + info.name, lore, active));
            slots.add(info.id);
        }
        GuiUtil.navigation(inv, p, pages, plugin, player, RankCategory.JOB);
        player.openInventory(inv);
    }
}
