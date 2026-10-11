package com.arclinker.rpglevelitems;

import com.arclinker.rpglevelitems.gui.RankGUI;
import com.arclinker.rpglevelitems.rank.PlayerRecord;
import com.arclinker.rpglevelitems.rank.RankCategory;
import com.arclinker.rpglevelitems.rank.RankScope;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * /rank [class|subclass|job|quest] [perm|season] | me | season | history | top &lt;cat&gt; [n] [perm|season]
 * Permanent ranks: F..U. Season ranks: F-S1..U-S1.
 */
public class RankCommand implements CommandExecutor, TabCompleter {
    private final RPGLevelTier plugin;

    public RankCommand(RPGLevelTier plugin) {
        this.plugin = plugin;
    }

    private static RankScope scopeIn(String[] args, int from) {
        for (int i = from; i < args.length; i++) {
            RankScope s = RankScope.parse(args[i]);
            if (s != null) return s;
        }
        return null;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        var rm = plugin.getRankManager();
        if (args.length >= 1 && args[0].equalsIgnoreCase("season")) {
            sender.sendMessage("§6Temporada §e" + rm.season() + " §7(rangos F-" + rm.seasonTag() + " … U-" + rm.seasonTag() + ")");
            sender.sendMessage("§7Termina en: §f" + rm.timeLeft() + " §8| §7Los rangos permanentes (F … U) no se reinician.");
            return true;
        }
        if (args.length >= 1 && args[0].equalsIgnoreCase("history")) {
            if (!(sender instanceof Player p)) return true;
            List<String> h = rm.history(plugin.getPlayerStore().get(p));
            p.sendMessage("§6Tus temporadas anteriores:");
            if (h.isEmpty()) p.sendMessage("§7Aún no has completado ninguna temporada.");
            h.forEach(p::sendMessage);
            return true;
        }
        if (args.length >= 2 && args[0].equalsIgnoreCase("top")) {
            RankCategory cat = RankCategory.parse(args[1]);
            if (cat == null) {
                sender.sendMessage("§cCategoría inválida. Usa: class, subclass, job, quest, pvp.");
                return true;
            }
            int n = 10;
            if (args.length >= 3) {
                try { n = Math.max(1, Math.min(50, Integer.parseInt(args[2]))); } catch (NumberFormatException ignored) { }
            }
            RankScope scope = scopeIn(args, 2);
            if (scope == null) scope = RankScope.SEASON;
            List<PlayerRecord> top = rm.top(cat, scope, n);
            sender.sendMessage("§6Top " + n + " - " + cat.coloredLabel() + " §7(" + scope.label + (scope == RankScope.SEASON ? " " + rm.season() : "") + ")");
            if (top.isEmpty()) sender.sendMessage("§7Aún no hay jugadores en este ranking.");
            for (int i = 0; i < top.size(); i++) {
                PlayerRecord r = top.get(i);
                long pts = rm.pts(r, cat, scope);
                sender.sendMessage("§e#" + (i + 1) + " §f" + r.name + " §7- " + rm.display(rm.getRank(pts, scope), scope) + " §7(" + pts + " pts)");
            }
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§eUso: /rank top <class|subclass|job|quest|pvp> [n] [perm|season]");
            return true;
        }
        if (args.length >= 1 && args[0].equalsIgnoreCase("me")) {
            player.sendMessage("§6Tus rangos §7(temporada " + rm.season() + ", termina en " + rm.timeLeft() + "):");
            for (RankCategory c : RankCategory.values()) {
                player.sendMessage(" " + c.coloredLabel() + "§7: " + rm.line(player.getUniqueId(), c));
            }
            return true;
        }
        RankCategory cat = RankCategory.CLASS;
        if (args.length >= 1) {
            cat = RankCategory.parse(args[0]);
            if (cat == null) {
                player.sendMessage("§eUso: /rank [class|subclass|job|quest] [perm|season] | me | season | history | top <categoría> [n] [perm|season]");
                return true;
            }
        }
        RankScope scope = scopeIn(args, 1);
        new RankGUI(plugin).open(player, cat, scope == null ? RankScope.SEASON : scope);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        List<String> out = new ArrayList<>();
        List<String> cats = List.of("class", "subclass", "job", "quest", "pvp");
        List<String> scopes = List.of("perm", "season");
        if (args.length == 1) {
            out.addAll(cats);
            out.addAll(List.of("me", "top", "season", "history"));
        } else if (args.length == 2 && args[0].equalsIgnoreCase("top")) {
            out.addAll(cats);
        } else if (args.length == 2 && RankCategory.parse(args[0]) != null) {
            out.addAll(scopes);
        } else if (args.length == 3 && args[0].equalsIgnoreCase("top")) {
            out.addAll(List.of("5", "10", "20", "50"));
            out.addAll(scopes);
        } else if (args.length == 4 && args[0].equalsIgnoreCase("top")) {
            out.addAll(scopes);
        }
        String t = args[args.length - 1].toLowerCase();
        return out.stream().filter(s -> s.startsWith(t)).collect(Collectors.toList());
    }
}
