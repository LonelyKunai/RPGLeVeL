package com.arclinker.rpglevelitems;

import com.arclinker.rpglevelitems.DailyQuestManager.QuestDefinition;
import com.arclinker.rpglevelitems.gui.QuestGUI;
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

/** /quest [gui | list | info <id> | rank] */
public class QuestCommand implements CommandExecutor, TabCompleter {
    private final RPGLevelTier plugin;

    public QuestCommand(RPGLevelTier plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cEste comando solo puede ser usado por jugadores.");
            return true;
        }
        if (!player.hasPermission("rpgleveltier.quest")) {
            player.sendMessage("§cNo tienes permiso para usar este comando.");
            return true;
        }
        var qm = plugin.getDailyQuestManager();
        if (args.length == 0 || args[0].equalsIgnoreCase("gui")) {
            new QuestGUI(plugin).open(player);
            return true;
        }
        if (args[0].equalsIgnoreCase("rank") || args[0].equalsIgnoreCase("ranking")) {
            new RankGUI(plugin).open(player, RankCategory.QUEST);
            return true;
        }
        if (args[0].equalsIgnoreCase("list")) {
            List<String> today = qm.getTodaysQuests(player);
            if (today.isEmpty()) {
                player.sendMessage("§eNo hay misiones disponibles.");
                return true;
            }
            player.sendMessage("§aMisiones de hoy §7(rango " + plugin.getRankManager().getRank(player.getUniqueId(), RankCategory.QUEST).display() + " §7permanente):");
            for (String id : today) {
                QuestDefinition d = qm.getDefinition(id);
                if (d == null) continue;
                boolean done = qm.isDone(player, id);
                int prog = done ? d.amount : qm.getProgress(player, id);
                player.sendMessage((done ? "§a✔ " : "§7• ") + "§f" + d.name + " §7- " + prog + "/" + d.amount + " §8(" + d.id + ")");
            }
            return true;
        }
        if (args[0].equalsIgnoreCase("info") && args.length == 2) {
            QuestDefinition d = qm.getDefinition(args[1]);
            if (d == null) {
                player.sendMessage("§cMisión no encontrada: " + args[1]);
                return true;
            }
            player.sendMessage("§aMisión: §f" + d.name + " §7(" + d.id + ")");
            player.sendMessage("§bRango: " + d.rank().display() + " §7| Tipo: §f" + d.type + " §7| Objetivo: §f" + d.target);
            player.sendMessage("§bCantidad: §f" + d.amount + " §7| Recompensa: §e+" + d.rewardXp + " pts");
            return true;
        }
        if (args[0].equalsIgnoreCase("weekly")) {
            var qx = plugin.getQuestExtras();
            player.sendMessage("§dMisiones semanales:");
            var r = plugin.getPlayerStore().get(player);
            for (String id : qx.weeklyQuests(player)) {
                var w = qx.weeklyDef(id);
                if (w == null) continue;
                boolean done = r.weeklyDone.contains(id);
                player.sendMessage((done ? "§a✔ " : "§7• ") + "§f" + w.name() + " §7- " + (done ? w.amount() : r.weeklyProgress.getOrDefault(id, 0)) + "/" + w.amount()
                        + " §8(+" + w.points() + " pts, +" + w.coins() + " monedas)");
            }
            return true;
        }
        if (args[0].equalsIgnoreCase("chain") || args[0].equalsIgnoreCase("chains")) {
            var qx = plugin.getQuestExtras();
            String sub = args.length > 1 ? args[1].toLowerCase() : "list";
            if (sub.equals("start") && args.length > 2) {
                String err = qx.start(player, args[2]);
                if (err != null) player.sendMessage("§c" + err);
            } else if (sub.equals("abandon") && args.length > 2) {
                qx.abandon(player, args[2]);
                player.sendMessage("§eMisión abandonada.");
            } else {
                player.sendMessage("§6Misiones en cadena §7(/quest chain start <id> | abandon <id>):");
                for (var c : qx.chains()) qx.chainLines(player, c.id()).forEach(player::sendMessage);
            }
            return true;
        }
        if (args[0].equalsIgnoreCase("track")) {
            boolean on = plugin.getQuestExtras().toggleTracker(player);
            player.sendMessage(on ? "§aRastreador de misiones activado (/quest track para quitarlo)." : "§eRastreador desactivado.");
            return true;
        }
        player.sendMessage("§eUso: /quest [gui | list | info <id> | weekly | chain [list|start|abandon] | track | rank]");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            out.addAll(List.of("gui", "list", "info", "weekly", "chain", "track", "rank"));
        } else if (args.length == 2 && args[0].equalsIgnoreCase("chain")) {
            out.addAll(List.of("list", "start", "abandon"));
        } else if (args.length == 3 && args[0].equalsIgnoreCase("chain")) {
            plugin.getQuestExtras().chains().forEach(c -> out.add(c.id()));
        } else if (args.length == 2 && args[0].equalsIgnoreCase("info") && sender instanceof Player p) {
            out.addAll(plugin.getDailyQuestManager().getTodaysQuests(p));
        }
        String t = args[args.length - 1].toLowerCase();
        return out.stream().filter(s -> s.toLowerCase().startsWith(t)).collect(Collectors.toList());
    }
}
