package com.arclinker.rpglevelitems;

import com.arclinker.rpglevelitems.station.StationManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** /station [list | own | <id>] - preview recipes. Admin: /station open|give <id> (via rpgleveltier.admin). */
public class StationCommand implements CommandExecutor, TabCompleter {
    private final RPGLevelTier plugin;

    public StationCommand(RPGLevelTier plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        StationManager sm = plugin.getStationManager();
        if (args.length >= 1 && args[0].equalsIgnoreCase("list")) {
            sender.sendMessage("§6Estaciones:");
            for (StationManager.Station s : sm.all()) {
                sender.sendMessage(" §e" + s.id() + " §7- " + s.name() + " §8(" + s.kind().name().toLowerCase(Locale.ROOT) + ", ítem " + s.itemId() + ")");
            }
            return true;
        }
        if (!(sender instanceof Player p)) {
            sender.sendMessage("§eUso: /station list");
            return true;
        }
        if (args.length >= 1 && args[0].equalsIgnoreCase("give")) {
            if (!p.hasPermission("rpgleveltier.items")) {
                p.sendMessage("§cNo tienes permiso.");
                return true;
            }
            StationManager.Station s = args.length > 1 ? sm.get(args[1]) : null;
            var item = s == null || plugin.getRlvpHandler() == null ? null : plugin.getRlvpHandler().getItem(s.itemId());
            if (item == null) {
                p.sendMessage("§eUso: /station give <id>");
                return true;
            }
            p.getInventory().addItem(item);
            p.sendMessage("§aTe di la estación de " + s.name() + ".");
            return true;
        }
        StationManager.Station st;
        if (args.length == 0 || args[0].equalsIgnoreCase("own")) {
            st = sm.ownStation(p, true);
            if (st == null) st = sm.ownStation(p, false);
            if (st == null) {
                p.sendMessage("§eElige una clase primero, o usa /station <id>. Ver ids: /station list");
                return true;
            }
        } else {
            st = sm.get(args[0]);
            if (st == null) {
                p.sendMessage("§cEstación desconocida. Usa /station list");
                return true;
            }
        }
        plugin.getStationGUI().open(p, st, 0, true);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        List<String> out = new ArrayList<>();
        String t = args[args.length - 1].toLowerCase(Locale.ROOT);
        if (args.length == 1) {
            out.add("list");
            out.add("own");
            if (sender.hasPermission("rpgleveltier.items")) out.add("give");
            for (var s : plugin.getStationManager().all()) out.add(s.id());
        } else if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
            for (var s : plugin.getStationManager().all()) out.add(s.id());
        }
        out.removeIf(s -> !s.startsWith(t));
        return out;
    }
}
