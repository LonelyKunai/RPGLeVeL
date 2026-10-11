package com.arclinker.rpglevelitems;

import com.arclinker.rpglevelitems.gui.JobGUI;
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

/** /jobs [gui | list | info <id> | me | set <1|2> <id> | clear <1|2> | rank] */
public class JobCommand implements CommandExecutor, TabCompleter {
    private final RPGLevelTier plugin;

    public JobCommand(RPGLevelTier plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cEste comando solo puede ser usado por jugadores.");
            return true;
        }
        if (!player.hasPermission("rpgleveltier.jobs")) {
            player.sendMessage("§cNo tienes permiso para usar este comando.");
            return true;
        }
        var jm = plugin.getJobManager();
        if (args.length == 0 || args[0].equalsIgnoreCase("gui")) {
            new JobGUI(plugin).open(player);
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "rank", "ranking" -> new RankGUI(plugin).open(player, RankCategory.JOB);
            case "list" -> {
                if (jm.getJobIds().isEmpty()) {
                    player.sendMessage("§eNo hay trabajos disponibles.");
                } else {
                    player.sendMessage("§aTrabajos disponibles: §f" + String.join("§7, §f", jm.getJobIds()));
                }
            }
            case "me" -> {
                List<String> active = jm.getActiveJobs(player);
                if (active.isEmpty()) {
                    player.sendMessage("§eNo tienes trabajos. Usa /jobs set <1|2> <id>.");
                } else {
                    for (String id : active) {
                        var info = jm.getJobInfo(id);
                        int xp = jm.getJobXP(player, id);
                        player.sendMessage("§a" + (info != null ? info.name : id) + " §7- nivel §e" + JobManager.levelForXp(xp)
                                + " §7(" + xp + " XP, faltan " + jm.getJobProgressToNextLevel(player, id) + ")");
                    }
                }
                player.sendMessage("§bTu rango de trabajo: " + plugin.getRankManager().line(player.getUniqueId(), RankCategory.JOB));
            }
            case "abilities", "habilidades" -> {
                var jab = plugin.getJobAbilities();
                String only = args.length >= 2 ? args[1].toLowerCase(java.util.Locale.ROOT) : null;
                player.sendMessage("§6Habilidades de oficio §7(se desbloquean por nivel/rango de trabajo o al azar como recompensa; solo funcionan con el trabajo equipado):");
                for (String id : jm.getJobIds()) {
                    if (only != null && !id.equals(only)) continue;
                    var lines = jab.describe(player, id);
                    if (lines.isEmpty()) continue;
                    var ji = jm.getJobInfo(id);
                    player.sendMessage(" §b" + (ji == null ? id : ji.name) + (jm.hasJob(player, id) ? " §a(equipado)" : " §8(no equipado)"));
                    lines.forEach(l -> player.sendMessage("   " + l));
                }
            }
            case "info" -> {
                var job = args.length >= 2 ? jm.getJobInfo(args[1]) : null;
                if (job == null) {
                    player.sendMessage(args.length >= 2 ? "§cTrabajo no encontrado: " + args[1] : "§eUso: /jobs info <id>");
                } else {
                    player.sendMessage("§aTrabajo: §f" + job.name + " §7(" + job.id + ")");
                    player.sendMessage("§bDescripción: §7" + job.description);
                    player.sendMessage("§bBonus: §7" + job.bonus);
                    int lv = jm.getJobLevel(player, job.id);
                    player.sendMessage("§bTu nivel: §e" + lv);
                    if (!job.perks.isEmpty()) player.sendMessage("§bVentajas por nivel:");
                    job.perks.forEach((l, st) -> {
                        StringBuilder sb = new StringBuilder(lv >= l ? " §a✔ " : " §8✘ ").append("Nv ").append(l).append(": ");
                        st.forEach((k, v) -> sb.append(com.arclinker.rpglevelitems.stats.Stats.line(k, v)).append("  "));
                        player.sendMessage(sb.toString());
                    });
                }
            }
            case "set" -> {
                if (args.length != 3) {
                    player.sendMessage("§eUso: /jobs set <1|2> <id>");
                    return true;
                }
                try {
                    jm.setGlobalJob(player, Integer.parseInt(args[1]), args[2]);
                } catch (NumberFormatException e) {
                    player.sendMessage("§cLa ranura debe ser un número (1 o 2).");
                }
            }
            case "clear" -> {
                if (args.length != 2) {
                    player.sendMessage("§eUso: /jobs clear <1|2>");
                    return true;
                }
                try {
                    jm.clearGlobalJob(player, Integer.parseInt(args[1]));
                } catch (NumberFormatException e) {
                    player.sendMessage("§cLa ranura debe ser un número (1 o 2).");
                }
            }
            default -> player.sendMessage("§eUso: /jobs [gui | list | me | info <id> | set <1|2> <id> | clear <1|2> | rank]");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            out.addAll(List.of("gui", "list", "me", "info", "abilities", "set", "clear", "rank"));
        } else if (args.length == 2) {
            switch (args[0].toLowerCase()) {
                case "info", "abilities" -> out.addAll(plugin.getJobManager().getJobIds());
                case "set", "clear" -> out.addAll(List.of("1", "2"));
                default -> { }
            }
        } else if (args.length == 3 && args[0].equalsIgnoreCase("set")) {
            out.addAll(plugin.getJobManager().getJobIds());
        }
        String t = args[args.length - 1].toLowerCase();
        return out.stream().filter(s -> s.toLowerCase().startsWith(t)).collect(Collectors.toList());
    }
}
