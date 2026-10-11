package com.arclinker.rpglevelitems;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * /language command – change plugin language.
 */
public class LanguageCommand implements CommandExecutor {
    private final RPGLevelTier plugin;

    public LanguageCommand(RPGLevelTier plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cEste comando solo puede ser usado por jugadores.");
            return true;
        }
        Player player = (Player) sender;
        if (!player.hasPermission("rpgleveltier.language")) {
            player.sendMessage("§cNo tienes permiso para usar este comando.");
            return true;
        }
        var lm = plugin.getLanguageManager();
        if (args.length != 1 || !lm.codes().contains(args[0].toLowerCase())) {
            player.sendMessage(lm.tr(player, "language.list", String.join(", ", lm.codes())));
            return true;
        }
        plugin.getPlayerStore().get(player).language = args[0].toLowerCase();
        plugin.getPlayerStore().markDirty();
        player.sendMessage(lm.tr(player, "language.changed", args[0].toLowerCase()));
        return true;
    }
}

