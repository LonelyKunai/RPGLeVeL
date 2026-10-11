package com.arclinker.rpglevelitems;

import com.arclinker.rpglevelitems.rank.PlayerRecord;
import com.arclinker.rpglevelitems.stats.Stats;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.stream.Collectors;

/** Small player commands: /stats, /coins, /pay, /title, /prestige. */
public class MiscCommands implements CommandExecutor, TabCompleter {
    private final RPGLevelTier plugin;

    public MiscCommands(RPGLevelTier plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("§cSolo jugadores.");
            return true;
        }
        switch (command.getName().toLowerCase(Locale.ROOT)) {
            case "stats" -> stats(p, args.length > 0 && p.hasPermission("rpgleveltier.admin") ? Bukkit.getPlayerExact(args[0]) : p);
            case "coins" -> p.sendMessage("§6Tienes §e" + plugin.getEconomy().format(plugin.getEconomy().balance(p.getUniqueId())) + "§6.");
            case "pay" -> pay(p, args);
            case "title" -> title(p, args);
            case "prestige" -> prestige(p, args);
            case "talent" -> talent(p, args);
            default -> { }
        }
        return true;
    }

    private void stats(Player p, Player target) {
        if (target == null) target = p;
        var engine = plugin.getStatEngine();
        Map<String, Double> all = engine.all(target);
        p.sendMessage("§6Estadísticas de §f" + target.getName() + "§6:");
        if (all.isEmpty()) p.sendMessage(" §7(sin bonos)");
        all.forEach((k, v) -> p.sendMessage(" §9▸ §f" + Stats.line(k, v)));
        double mana = plugin.getMana() == null ? 0 : plugin.getMana().max(target);
        if (mana > 0) p.sendMessage(" §b✦ Maná máximo: §f" + (int) mana);
        List<String> sets = plugin.getGearSetManager().describe(target);
        sets.forEach(p::sendMessage);
        PlayerRecord r = plugin.getPlayerStore().get(target);
        if (plugin.getPrestigeManager().total(target) > 0) p.sendMessage(" §6★ Prestigio total: §f" + plugin.getPrestigeManager().total(target));
        if (!r.activeTitle.isEmpty()) p.sendMessage(" §7Título: " + plugin.getAchievements().activeTitleDisplay(r));
    }

    private void pay(Player p, String[] args) {
        if (args.length != 2) {
            p.sendMessage("§eUso: /pay <jugador> <cantidad>");
            return;
        }
        Player to = Bukkit.getPlayerExact(args[0]);
        if (to == null || to == p) {
            p.sendMessage("§cJugador no disponible.");
            return;
        }
        double amount;
        try { amount = Math.floor(Double.parseDouble(args[1])); } catch (NumberFormatException e) { amount = 0; }
        if (amount < 1) {
            p.sendMessage("§cCantidad inválida.");
            return;
        }
        if (!plugin.getEconomy().withdraw(p, amount)) {
            p.sendMessage("§cNo tienes suficiente dinero.");
            return;
        }
        plugin.getEconomy().deposit(to, amount);
        p.sendMessage("§aEnviaste §e" + plugin.getEconomy().format(amount) + " §aa " + to.getName() + ".");
        to.sendMessage("§aRecibiste §e" + plugin.getEconomy().format(amount) + " §ade " + p.getName() + ".");
    }

    private void title(Player p, String[] args) {
        var am = plugin.getAchievements();
        PlayerRecord r = plugin.getPlayerStore().get(p);
        if (args.length == 0 || args[0].equalsIgnoreCase("list")) {
            p.sendMessage("§6Tus títulos §7(/title set <id>, /title clear):");
            if (r.titles.isEmpty()) p.sendMessage(" §7Aún no tienes títulos: desbloquea logros (/achievements).");
            for (String id : r.titles) {
                var t = am.title(id);
                if (t != null) p.sendMessage(" " + (id.equals(r.activeTitle) ? "§a▶ " : "§7") + id + " §8- " + t.display());
            }
            return;
        }
        if (args[0].equalsIgnoreCase("clear")) {
            am.setTitle(p, "");
            p.sendMessage("§eTítulo quitado.");
            return;
        }
        if (args[0].equalsIgnoreCase("set") && args.length > 1) {
            if (am.setTitle(p, args[1])) p.sendMessage("§aTítulo activo: " + am.title(args[1]).display());
            else p.sendMessage("§cNo tienes ese título.");
            return;
        }
        p.sendMessage("§eUso: /title [list|set <id>|clear]");
    }

    private void talent(Player p, String[] args) {
        var tm = plugin.getTalents();
        if (args.length > 0 && args[0].equalsIgnoreCase("respec")) {
            if (args.length < 2 || !args[1].equalsIgnoreCase("confirm")) {
                p.sendMessage("§eReiniciar talentos cuesta §f" + plugin.getEconomy().format(tm.respecCost()) + "§e. Escribe §f/talent respec confirm§e.");
                return;
            }
            if (!plugin.getEconomy().withdraw(p, tm.respecCost())) {
                p.sendMessage("§cNo tienes suficiente dinero.");
                return;
            }
            tm.respec(p);
            p.sendMessage("§aTalentos reiniciados.");
            return;
        }
        plugin.getTalentGUI().open(p, args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : null);
    }

    private void prestige(Player p, String[] args) {
        var pm = plugin.getPrestigeManager();
        PlayerRecord r = plugin.getPlayerStore().get(p);
        if (args.length == 0 || !args[0].equalsIgnoreCase("confirm")) {
            int cur = r.classId.isEmpty() ? 0 : pm.of(p, r.classId);
            p.sendMessage("§6Prestigio de tu clase: §f" + cur + " §7(total " + pm.total(p) + ")");
            p.sendMessage("§7Al nivel máximo puedes prestigiar: tu nivel de clase vuelve a 1, conservas habilidades y ganas bonos permanentes.");
            p.sendMessage("§eEscribe §f/prestige confirm §epara hacerlo.");
            return;
        }
        String err = pm.prestige(p);
        if (err != null) p.sendMessage("§c" + err);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        List<String> out = new ArrayList<>();
        String t = args[args.length - 1].toLowerCase(Locale.ROOT);
        switch (command.getName().toLowerCase(Locale.ROOT)) {
            case "title" -> {
                if (args.length == 1) out.addAll(List.of("list", "set", "clear"));
                else if (args.length == 2 && args[0].equalsIgnoreCase("set") && sender instanceof Player p) out.addAll(plugin.getPlayerStore().get(p).titles);
            }
            case "prestige" -> { if (args.length == 1) out.add("confirm"); }
            case "talent" -> {
                if (args.length == 1) { out.add("respec"); plugin.getTalents().branches().forEach(b -> out.add(b.id())); }
                if (args.length == 2) out.add("confirm");
            }
            case "pay", "stats" -> { if (args.length == 1) Bukkit.getOnlinePlayers().forEach(pl -> out.add(pl.getName())); }
            default -> { }
        }
        return out.stream().filter(s -> s.toLowerCase(Locale.ROOT).startsWith(t)).collect(Collectors.toList());
    }
}
