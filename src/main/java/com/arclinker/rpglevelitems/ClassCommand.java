package com.arclinker.rpglevelitems;

import com.arclinker.rpglevelitems.gui.ClassGUI;
import com.arclinker.rpglevelitems.gui.RankGUI;
import com.arclinker.rpglevelitems.rank.RankCategory;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/** /class [gui | list | info <id> | stats | abilities | rank | <id>] */
public class ClassCommand implements CommandExecutor, TabCompleter {
    private final RPGLevelTier plugin;

    public ClassCommand(RPGLevelTier plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cEste comando solo puede ser usado por jugadores.");
            return true;
        }
        if (!player.hasPermission("rpgleveltier.class")) {
            player.sendMessage("§cNo tienes permiso para usar este comando.");
            return true;
        }
        var cm = plugin.getClassManager();
        if (args.length == 0 || args[0].equalsIgnoreCase("gui")) {
            new ClassGUI(plugin).open(player);
            return true;
        }
        if (args[0].equalsIgnoreCase("stats") || args[0].equalsIgnoreCase("level")) {
            for (String line : plugin.getClassLevelManager().describe(player, false)) player.sendMessage(line);
            return true;
        }
        if (args[0].equalsIgnoreCase("abilities") || args[0].equalsIgnoreCase("skills")) {
            plugin.getAbilityGUI().open(player, false);
            return true;
        }
        if (args[0].equalsIgnoreCase("rank") || args[0].equalsIgnoreCase("ranking")) {
            new RankGUI(plugin).open(player, RankCategory.CLASS);
            return true;
        }
        if (args[0].equalsIgnoreCase("list")) {
            if (cm.getClassIds().isEmpty()) {
                player.sendMessage("§eNo hay clases disponibles.");
            } else {
                player.sendMessage("§aClases disponibles: §f" + String.join("§7, §f", cm.getClassIds()));
            }
            return true;
        }
        if (args[0].equalsIgnoreCase("info")) {
            String id = args.length >= 2 ? args[1] : cm.getClassId(player);
            var info = cm.getClassInfo(id);
            if (info == null) {
                player.sendMessage(id.isEmpty() ? "§eNo tienes clase. Usa /class." : "§cClase no encontrada: " + id);
                return true;
            }
            player.sendMessage("§aClase: §f" + info.name + " §7(" + info.id + ")");
            player.sendMessage("§bDescripción: §7" + info.description);
            player.sendMessage("§bSubclases: §f" + String.join("§7, §f", plugin.getSubClassManager().getSubClassIdsOf(info.id)));
            player.sendMessage("§bTu rango de clase: " + plugin.getRankManager().line(player.getUniqueId(), RankCategory.CLASS));
            return true;
        }
        if (args.length != 1) {
            player.sendMessage("§eUso: /class [gui | list | info [id] | stats | abilities | rank | <id>]");
            return true;
        }
        cm.setClass(player, args[0]);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            out.addAll(List.of("gui", "list", "info", "stats", "abilities", "rank"));
            out.addAll(plugin.getClassManager().getClassIds());
        } else if (args.length == 2 && args[0].equalsIgnoreCase("info")) {
            out.addAll(plugin.getClassManager().getClassIds());
        }
        String t = args[args.length - 1].toLowerCase();
        return out.stream().filter(s -> s.toLowerCase().startsWith(t)).collect(Collectors.toList());
    }
}
