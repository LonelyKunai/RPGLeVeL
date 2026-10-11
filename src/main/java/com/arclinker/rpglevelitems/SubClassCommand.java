package com.arclinker.rpglevelitems;

import com.arclinker.rpglevelitems.gui.RankGUI;
import com.arclinker.rpglevelitems.gui.SubClassGUI;
import com.arclinker.rpglevelitems.rank.RankCategory;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/** /subclass [gui | list | info <id> | stats | abilities | rank | <id>] */
public class SubClassCommand implements CommandExecutor, TabCompleter {
    private final RPGLevelTier plugin;

    public SubClassCommand(RPGLevelTier plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cEste comando solo puede ser usado por jugadores.");
            return true;
        }
        if (!player.hasPermission("rpgleveltier.subclass")) {
            player.sendMessage("§cNo tienes permiso para usar este comando.");
            return true;
        }
        var sm = plugin.getSubClassManager();
        if (args.length == 0 || args[0].equalsIgnoreCase("gui")) {
            if (!player.hasPermission("rpgleveltier.subclass.gui")) {
                player.sendMessage("§cNo tienes permiso para abrir la GUI de subclases.");
                return true;
            }
            new SubClassGUI(plugin).open(player);
            return true;
        }
        if (args[0].equalsIgnoreCase("stats") || args[0].equalsIgnoreCase("level")) {
            for (String line : plugin.getClassLevelManager().describe(player, true)) player.sendMessage(line);
            return true;
        }
        if (args[0].equalsIgnoreCase("abilities") || args[0].equalsIgnoreCase("skills")) {
            plugin.getAbilityGUI().open(player, true);
            return true;
        }
        if (args[0].equalsIgnoreCase("rank") || args[0].equalsIgnoreCase("ranking")) {
            new RankGUI(plugin).open(player, RankCategory.SUBCLASS);
            return true;
        }
        if (args[0].equalsIgnoreCase("list")) {
            String classId = plugin.getClassManager().getClassId(player);
            List<String> ids = classId.isEmpty() ? new ArrayList<>(sm.getSubClassIds()) : sm.getSubClassIdsOf(classId);
            if (ids.isEmpty()) {
                player.sendMessage("§eNo hay subclases disponibles.");
            } else {
                player.sendMessage("§aSubclases" + (classId.isEmpty() ? "" : " de tu clase") + ": §f" + String.join("§7, §f", ids));
            }
            return true;
        }
        if (args[0].equalsIgnoreCase("info")) {
            String id = args.length >= 2 ? args[1] : sm.getSubClassId(player);
            var info = sm.getInfo(id);
            if (info == null) {
                player.sendMessage(id.isEmpty() ? "§eNo tienes subclase. Usa /subclass." : "§cSubclase no encontrada: " + id);
                return true;
            }
            var parent = plugin.getClassManager().getClassInfo(info.parentClass);
            player.sendMessage("§aSubclase: §f" + info.name + " §7(" + info.id + ")");
            player.sendMessage("§bClase: §f" + (parent != null ? parent.name : info.parentClass));
            player.sendMessage("§bDescripción: §7" + info.description);
            player.sendMessage("§bTu rango de subclase: " + plugin.getRankManager().line(player.getUniqueId(), RankCategory.SUBCLASS));
            return true;
        }
        if (args.length != 1) {
            player.sendMessage("§eUso: /subclass [gui | list | info [id] | stats | abilities | rank | <id>]");
            return true;
        }
        sm.setSubClass(player, args[0]);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        List<String> out = new ArrayList<>();
        List<String> own = sender instanceof Player p && !plugin.getClassManager().getClassId(p).isEmpty()
                ? plugin.getSubClassManager().getSubClassIdsOf(plugin.getClassManager().getClassId(p))
                : new ArrayList<>(plugin.getSubClassManager().getSubClassIds());
        if (args.length == 1) {
            out.addAll(List.of("gui", "list", "info", "stats", "abilities", "rank"));
            out.addAll(own);
        } else if (args.length == 2 && args[0].equalsIgnoreCase("info")) {
            out.addAll(plugin.getSubClassManager().getSubClassIds());
        }
        String t = args[args.length - 1].toLowerCase();
        return out.stream().filter(s -> s.toLowerCase().startsWith(t)).collect(Collectors.toList());
    }
}
