package com.arclinker.rpglevelitems;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Wraps every user command so admins (rpgleveltier.admin) can inspect another player by adding
 * {@code @<jugador>} as the last argument, e.g. {@code /class @Steve}, {@code /ability @Steve},
 * {@code /jobs @Steve}, {@code /quest @Steve}, {@code /rank @Steve}, {@code /stats @Steve}.
 * It shows the matching section of {@code /rlv userinfo}.
 */
public final class AdminLookup implements CommandExecutor, TabCompleter {
    private static final Map<String, String> SECTION = Map.ofEntries(
            Map.entry("class", "class"), Map.entry("subclass", "class"), Map.entry("jobs", "jobs"), Map.entry("quest", "quests"),
            Map.entry("quests", "quests"), Map.entry("rank", "ranks"), Map.entry("ability", "abilities"), Map.entry("stats", "stats"),
            Map.entry("title", "achievements"), Map.entry("achievements", "achievements"), Map.entry("talent", "talents"),
            Map.entry("prestige", "class"), Map.entry("party", "social"), Map.entry("guild", "social"), Map.entry("coins", "profile"),
            Map.entry("station", "gear"), Map.entry("duel", "ranks"), Map.entry("dungeon", "social"), Map.entry("ah", "social"), Map.entry("trade", "social"));

    private final RPGLevelTier plugin;
    private final CommandExecutor exec;
    private final TabCompleter tab;

    public AdminLookup(RPGLevelTier plugin, CommandExecutor exec) {
        this.plugin = plugin;
        this.exec = exec;
        this.tab = exec instanceof TabCompleter t ? t : null;
    }

    /** Handles "... @player" for admins. Returns true if the command was answered. */
    public static boolean tryHandle(RPGLevelTier plugin, CommandSender sender, String commandName, String[] args) {
        if (args.length == 0 || !args[args.length - 1].startsWith("@") || args[args.length - 1].length() <= 1 || !sender.hasPermission("rpgleveltier.admin")) return false;
        String name = args[args.length - 1].substring(1);
        OfflinePlayer target = find(name);
        if (target == null) return false; // not a known player: let the command treat "@text" as normal text
        new UserInfo(plugin).show(sender, target, SECTION.getOrDefault(commandName.toLowerCase(Locale.ROOT), "all"));
        return true;
    }

    /** Online player or a player present in the server's offline-player cache (no network lookup). */
    static OfflinePlayer find(String name) {
        OfflinePlayer online = Bukkit.getPlayerExact(name);
        if (online != null) return online;
        for (OfflinePlayer op : Bukkit.getOfflinePlayers()) if (name.equalsIgnoreCase(op.getName())) return op;
        return null;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (tryHandle(plugin, sender, command.getName(), args)) return true;
        return exec.onCommand(sender, command, label, args);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        List<String> base = tab == null ? new ArrayList<>() : tab.onTabComplete(sender, command, label, args);
        List<String> out = base == null ? new ArrayList<>() : new ArrayList<>(base);
        if (args.length > 0 && sender.hasPermission("rpgleveltier.admin")) {
            String typed = args[args.length - 1];
            if (typed.startsWith("@") || typed.isEmpty()) {
                for (var p : Bukkit.getOnlinePlayers()) {
                    String s = "@" + p.getName();
                    if (s.toLowerCase(Locale.ROOT).startsWith(typed.toLowerCase(Locale.ROOT))) out.add(s);
                }
            }
        }
        return out;
    }
}
