package com.arclinker.rpglevelitems;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.Material;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import net.kyori.adventure.audience.Audience;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.util.Collection;
import com.arclinker.rpglevelitems.rank.RankCategory;
import com.arclinker.rpglevelitems.rank.RankScope;
import java.util.Locale;
import com.arclinker.rpglevelitems.gui.ItemListGUI;

@SuppressWarnings("unused")
public class LevelCommand implements CommandExecutor, TabCompleter {

    private final RPGLevelTier plugin;
    private final ItemProgressionManager manager;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private final ClassCommand classCommand;
    private final SubClassCommand subClassCommand;
    private final JobCommand jobCommand;
    private final QuestCommand questCommand;
    private final RankCommand rankCommand;
    private final AbilityCommand abilityCommand;
    private final AdminCommands adminCommands;

    public LevelCommand(RPGLevelTier plugin, ItemProgressionManager manager) {
        this.plugin = plugin;
        this.manager = manager;
        this.classCommand = new ClassCommand(plugin);
        this.subClassCommand = new SubClassCommand(plugin);
        this.jobCommand = new JobCommand(plugin);
        this.questCommand = new QuestCommand(plugin);
        this.rankCommand = new RankCommand(plugin);
        this.abilityCommand = new AbilityCommand(plugin);
        this.adminCommands = new AdminCommands(plugin);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        String prefix = plugin.getConfig().getString("messages.prefix", "<gradient:#FFD700:#FFA500><b>[RPG]</b></gradient> ");

        // Help
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            Audience a = (Audience) sender;
            a.sendMessage(miniMessage.deserialize(prefix + "<yellow>Comandos disponibles <gray>(/rlv = /rpglevel = /rpgtier):"));
            for (String[] c : HELP) {
                a.sendMessage(miniMessage.deserialize("<gold>" + escape(c[0]) + " <gray>- " + c[1]));
            }
            return true;
        }

        // Reload configuration
        if (args[0].equalsIgnoreCase("reload")) {
            plugin.reloadAll();
            ((Audience) sender).sendMessage(miniMessage.deserialize(prefix + "<green>¡Configuración recargada correctamente!"));
            return true;
        }

        // Section commands delegate to their own executors (with the remaining args)
        String[] rest = java.util.Arrays.copyOfRange(args, 1, args.length);
        if (AdminLookup.tryHandle(plugin, sender, args[0], rest)) return true; // /rlv class @jugador, /rlv rank @jugador...
        switch (args[0].toLowerCase()) {
            case "class": return classCommand.onCommand(sender, command, label, rest);
            case "subclass": return subClassCommand.onCommand(sender, command, label, rest);
            case "jobs": case "job": return jobCommand.onCommand(sender, command, label, rest);
            case "quest": case "quests": return questCommand.onCommand(sender, command, label, rest);
            case "rank": case "ranking": return rankCommand.onCommand(sender, command, label, rest);
            case "ability": case "abilities": case "skills": return abilityCommand.onCommand(sender, command, label, rest);
            default: break;
        }
        if (adminCommands.handle(sender, args)) return true;

        // /rlv setclasslevel <player> <class|subclass> <level> - admin tool
        if (args[0].equalsIgnoreCase("setclasslevel")) {
            if (args.length != 4) {
                sender.sendMessage("§eUso: /rlv setclasslevel <jugador> <class|subclass> <nivel>");
                return true;
            }
            Player target = plugin.getServer().getPlayerExact(args[1]);
            if (target == null || !(args[2].equalsIgnoreCase("class") || args[2].equalsIgnoreCase("subclass"))) {
                sender.sendMessage("§cJugador desconectado o tipo inválido (class|subclass).");
                return true;
            }
            try {
                plugin.getClassLevelManager().setLevel(target, args[2].equalsIgnoreCase("subclass"), Integer.parseInt(args[3]));
                sender.sendMessage("§aNivel actualizado.");
            } catch (NumberFormatException e) {
                sender.sendMessage("§cNivel inválido.");
            }
            return true;
        }
        if (args[0].equalsIgnoreCase("language")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("§cEste comando solo puede ser usado por jugadores.");
                return true;
            }
            if (!player.hasPermission("rpgleveltier.language")) {
                player.sendMessage("§cNo tienes permiso para usar este comando.");
                return true;
            }
            if (args.length != 2) {
                player.sendMessage("§eUso: /rlv language <code>");
                return true;
            }
            plugin.getLanguageManager().setLanguage(args[1]);
            player.sendMessage("§aIdioma cambiado a " + args[1]);
            return true;
        }

        // /rlv setpoints <player> <category> <points> [perm|season] - admin tool for rankings
        if (args[0].equalsIgnoreCase("setpoints")) {
            if (args.length < 4) {
                sender.sendMessage("§eUso: /rlv setpoints <jugador> <class|subclass|job|quest> <puntos> [perm|season]");
                return true;
            }
            org.bukkit.OfflinePlayer target = plugin.getServer().getOfflinePlayer(args[1]);
            RankCategory cat = RankCategory.parse(args[2]);
            if (cat == null || (!target.isOnline() && !target.hasPlayedBefore())) {
                sender.sendMessage("§cJugador desconocido o categoría inválida.");
                return true;
            }
            RankScope scope = args.length > 4 ? RankScope.parse(args[4]) : RankScope.PERMANENT;
            if (scope == null) {
                sender.sendMessage("§cLadder inválido (perm|season).");
                return true;
            }
            try {
                long pts = Long.parseLong(args[3]);
                var rm = plugin.getRankManager();
                rm.setPoints(target.getUniqueId(), cat, scope, pts);
                sender.sendMessage("§aPuntos " + scope.label.toLowerCase() + " de " + cat.coloredLabel() + " §ade " + args[1] + ": §f" + Math.max(0, pts)
                        + " §7(" + rm.display(rm.getRank(target.getUniqueId(), cat, scope), scope) + "§7)");
            } catch (NumberFormatException e) {
                sender.sendMessage("§cPuntos inválidos.");
            }
            return true;
        }

        // /rlv season [info|end|log] - season management
        if (args[0].equalsIgnoreCase("season")) {
            var rm = plugin.getRankManager();
            String sub = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "info";
            switch (sub) {
                case "end", "new", "next" -> {
                    rm.endSeason();
                    sender.sendMessage("§aTemporada terminada. Ahora: temporada " + rm.season() + ".");
                }
                case "log" -> {
                    sender.sendMessage("§6Historial de temporadas:");
                    rm.seasonLog().forEach(l -> sender.sendMessage(" §7" + l));
                }
                default -> {
                    sender.sendMessage("§6Temporada §e" + rm.season() + " §7- termina en §f" + rm.timeLeft());
                    sender.sendMessage("§7Uso: /rlv season [info|end|log]");
                }
            }
            return true;
        }

        // /rlv give subcommand
        if (args[0].equalsIgnoreCase("give")) {
            // /rlv give <item_id> [jugador] [cantidad]  (usable desde consola / kits)
            if (!sender.hasPermission(ItemListGUI.PERMISSION)) {
                sender.sendMessage("§cNo tienes permiso para usar este comando.");
                return true;
            }
            if (args.length < 2) {
                sender.sendMessage("§eUso: /rlv give <item_id> [jugador] [cantidad]");
                return true;
            }
            Player target = args.length > 2 ? Bukkit.getPlayerExact(args[2]) : (sender instanceof Player sp ? sp : null);
            if (target == null) {
                sender.sendMessage("§cIndica un jugador conectado.");
                return true;
            }
            String itemId = args[1].toLowerCase();
            ItemStack item = plugin.getRlvpHandler().getItem(itemId);
            if (item == null) {
                sender.sendMessage("§cÍtem '" + itemId + "' no encontrado.");
                return true;
            }
            int amount = 1;
            if (args.length > 3) {
                try { amount = Math.max(1, Math.min(64, Integer.parseInt(args[3]))); } catch (NumberFormatException ignored) { }
            }
            item.setAmount(amount);
            for (ItemStack left : target.getInventory().addItem(item).values()) {
                target.getWorld().dropItemNaturally(target.getLocation(), left);
            }
            target.sendMessage("§eHas recibido §f" + item.getItemMeta().getDisplayName() + " §e(§f" + itemId + (amount > 1 ? " x" + amount : "") + "§e).");
            return true;
        }

        // /rlv itemlist | list | listitems - paginated GUI with all .rlvp items
        if (args[0].equalsIgnoreCase("itemlist") || args[0].equalsIgnoreCase("list") || args[0].equalsIgnoreCase("listitems")) {
            if (!(sender instanceof Player player)) {
                ((Audience) sender).sendMessage(miniMessage.deserialize("Este comando solo puede ser ejecutado por un jugador."));
                return true;
            }
            if (!player.hasPermission(ItemListGUI.PERMISSION)) {
                player.sendMessage("§cNo tienes permiso para usar este comando.");
                return true;
            }
            int page = 0;
            if (args.length >= 2) {
                try {
                    page = Math.max(0, Integer.parseInt(args[1]) - 1);
                } catch (NumberFormatException e) {
                    if (plugin.getRlvpHandler() != null && plugin.getRlvpHandler().getCategory(args[1]) != null) {
                        plugin.getItemListGUI().open(player, args[1]);
                    } else {
                        player.sendMessage("§eUso: /rlv itemlist [página|categoría]");
                    }
                    return true;
                }
            }
            plugin.getItemListGUI().open(player, page);
            return true;
        }

        // Item‑hand commands
        if (!(sender instanceof Player player)) {
            ((Audience) sender).sendMessage(miniMessage.deserialize("Este comando solo puede ser ejecutado por un jugador."));
            return true;
        }
        ItemStack item = player.getInventory().getItemInMainHand();
        if (!manager.isRpgItem(item)) {
            ((Audience) player).sendMessage(miniMessage.deserialize(prefix + "<red>Debes sostener un arma o armadura válida en tu mano principal."));
            return true;
        }
        manager.initializeItem(item);

        if (args[0].equalsIgnoreCase("setlevel") && args.length >= 2) {
            try {
                int level = Integer.parseInt(args[1]);
                if (level < 1 || level > 30) {
                    ((Audience) player).sendMessage(miniMessage.deserialize(prefix + "<red>El nivel debe estar entre 1 y 30."));
                    return true;
                }
                manager.setLevel(item, level);
                ((Audience) player).sendMessage(miniMessage.deserialize(prefix + "<green>Nivel establecido a <yellow>" + level + "<green>."));
            } catch (NumberFormatException e) {
                ((Audience) player).sendMessage(miniMessage.deserialize(prefix + "<red>Número inválido."));
            }
            return true;
        }
        if (args[0].equalsIgnoreCase("settier") && args.length >= 2) {
            try {
                int tier = Integer.parseInt(args[1]);
                if (tier < 1 || tier > 10) {
                    ((Audience) player).sendMessage(miniMessage.deserialize(prefix + "<red>El tier debe estar entre 1 y 10."));
                    return true;
                }
                manager.setTier(item, tier);
                ((Audience) player).sendMessage(miniMessage.deserialize(prefix + "<green>Tier establecido a <gold>Tier " + manager.toRoman(tier) + " (" + tier + ")<green>."));
            } catch (NumberFormatException e) {
                ((Audience) player).sendMessage(miniMessage.deserialize(prefix + "<red>Número inválido."));
            }
            return true;
        }
        if (args[0].equalsIgnoreCase("addxp") && args.length >= 2) {
            try {
                long xp = Long.parseLong(args[1]);
                ItemProgressionManager.ProgressionResult res = manager.addXp(item, xp);
                player.getInventory().setItemInMainHand(item);
                ((Audience) player).sendMessage(miniMessage.deserialize(prefix + "<green>Se añadieron <yellow>" + xp + " XP<green>. Nivel actual: <yellow>" + res.newLevel() + "<green>, Tier: <gold>" + manager.toRoman(res.newTier())));
            } catch (NumberFormatException e) {
                ((Audience) player).sendMessage(miniMessage.deserialize(prefix + "<red>Cantidad de XP inválida."));
            }
            return true;
        }
        ((Audience) player).sendMessage(miniMessage.deserialize(prefix + "<red>Subcomando desconocido. Usa /rpglevel help"));
        return true;
    }

    private static final String[][] HELP = {
        {"/rlv help", "Muestra esta ayuda"},
        {"/rlv setlevel <1-30>", "Establece el nivel del ítem en mano"},
        {"/rlv settier <1-10>", "Establece el tier del ítem en mano"},
        {"/rlv addxp <cantidad>", "Añade experiencia al ítem en mano"},
        {"/rlv give <item_id>", "Te da un ítem .rlvp"},
        {"/rlv itemlist [página|categoría]", "GUI de ítems: categoría > tipo (armadura completa, armas…) > tier"},
        {"/rlv list [página]", "Alias de itemlist"},
        {"/rlv class [gui|list|info|rank|<id>]", "Clases (menú, lista, info, ranking o seleccionar)"},
        {"/rlv subclass [gui|list|info|rank|<id>]", "Subclases de tu clase"},
        {"/rlv jobs [gui|list|me|info|set|clear|rank]", "Trabajos (set <1|2> <id>, clear <1|2>)"},
        {"/rlv quest [gui|list|info|rank]", "Misiones diarias y su progreso"},
        {"/rlv ability [gui|list|info|unlock|upgrade|copy|points]", "Árbol de habilidades, tomos y puntos"},
        {"/station [list|own|<id>|give <id>]", "Estaciones de trabajo de clase/subclase y sus recetas"},
        {"/rlv rank [class|subclass|job|quest|me|season|history|top] [perm|season]", "Rankings permanentes (F…U) y de temporada (F-S1…U-S1)"},
        {"/rlv setclasslevel <jugador> <class|subclass> <nivel>", "Fija el nivel de clase/subclase de un jugador"},
        {"/rlv setpoints <jugador> <categoría> <pts> [perm|season]", "Fija los puntos de ranking de un jugador"},
        {"/rlv roll [tier] [afijos]", "Aplica afijos aleatorios al ítem en mano"},
        {"/rlv gem [list|give <id> [nivel] [n]|clear]", "Gemas (clear destruye las del ítem en mano)"},
        {"/rlv sockets <n>", "Fija las ranuras del ítem en mano"},
        {"/rlv affix", "Lista de afijos"},
        {"/rlv boss [list|spawn <id>|killall]", "Jefes mundiales"},
        {"/rlv event [status|start <id>|stop]", "Eventos del servidor"},
        {"/rlv zone [list|pos1|pos2|create|delete|flag|info]", "Zonas con flags (pvp, abilities, xp, mob_level...)"},
        {"/rlv coins <give|take|set> <jugador> <n>", "Dinero interno"},
        {"/rlv npc <create <cadena>|remove>", "NPC de misiones en cadena"},
        {"/rlv userinfo [jugador] [sección]", "Toda la info de un jugador: perfil, clases, stats, árboles de habilidades, trabajos, misiones, rangos, talentos, logros, equipo, social"},
        {"/rlv jobability [list|give|remove <jugador> <id|random>]", "Habilidades de oficio (dar / quitar / aleatoria)"},
        {"/rlv shop [refill|stock <objeto> <n>|reload]", "Tienda: rellenar, fijar stock, recargar"},
        {"/rlv editor", "Editor in-game de todos los archivos de data/ (clic en un valor y escribe el nuevo)"},
        {"/rlv debug [jugador]", "Diagnóstico del plugin"},
        {"/rlv season [info|end|log]", "Temporada de rangos (end = terminar y empezar la siguiente)"},
        {"/rlv language <código>", "Cambia el idioma del plugin"},
        {"/rlv reload", "Recarga config, rangos e ítems .rlvp"},
    };

    private static final List<String> SUBCOMMANDS = List.of(
        "help", "setlevel", "settier", "addxp", "give", "itemlist", "list",
        "class", "subclass", "jobs", "quest", "ability", "rank", "setpoints", "season", "setclasslevel", "roll", "gem", "sockets", "affix", "boss", "event", "zone", "coins", "debug", "npc", "editor", "userinfo", "jobability", "shop", "language", "reload");

    private static String escape(String s) {
        return s.replace("<", "\\<");
    }

    private static List<String> filter(Collection<String> options, String typed, int limit) {
        String t = typed.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String o : options) {
            if (o.toLowerCase(Locale.ROOT).startsWith(t)) {
                out.add(o);
                if (out.size() >= limit) break;
            }
        }
        return out;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) {
            return filter(SUBCOMMANDS, args[0], 50);
        }
        if (args[args.length - 1].startsWith("@") && sender.hasPermission("rpgleveltier.admin")) {
            return filter(plugin.getServer().getOnlinePlayers().stream().map(p -> "@" + p.getName()).collect(java.util.stream.Collectors.toList()), args[args.length - 1], 50);
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        String typed = args[args.length - 1];
        String[] rest = java.util.Arrays.copyOfRange(args, 1, args.length);
        switch (sub) {
            case "class": return classCommand.onTabComplete(sender, command, label, rest);
            case "subclass": return subClassCommand.onTabComplete(sender, command, label, rest);
            case "jobs": case "job": return jobCommand.onTabComplete(sender, command, label, rest);
            case "quest": case "quests": return questCommand.onTabComplete(sender, command, label, rest);
            case "rank": case "ranking": return rankCommand.onTabComplete(sender, command, label, rest);
            case "ability": case "abilities": case "skills": return abilityCommand.onTabComplete(sender, command, label, rest);
            default: break;
        }
        if (sub.equals("setclasslevel")) {
            if (args.length == 2) return filter(plugin.getServer().getOnlinePlayers().stream().map(Player::getName).collect(java.util.stream.Collectors.toList()), typed, 50);
            if (args.length == 3) return filter(List.of("class", "subclass"), typed, 50);
            if (args.length == 4) return filter(List.of("1", "10", "25", "50"), typed, 50);
            return new ArrayList<>();
        }
        if (sub.equals("setpoints")) {
            if (args.length == 2) return filter(plugin.getServer().getOnlinePlayers().stream().map(Player::getName).collect(java.util.stream.Collectors.toList()), typed, 50);
            if (args.length == 3) return filter(List.of("class", "subclass", "job", "quest", "pvp"), typed, 50);
            if (args.length == 4) return filter(List.of("0", "100", "1000", "10000"), typed, 50);
            if (args.length == 5) return filter(List.of("perm", "season"), typed, 50);
            return new ArrayList<>();
        }
        if (AdminCommands.NAMES.contains(sub)) return adminCommands.tab(sender, args);
        if (sub.equals("season")) return args.length == 2 ? filter(List.of("info", "end", "log"), typed, 50) : new ArrayList<>();
        if (args.length == 2) {
            switch (sub) {
                case "setlevel":
                    return filter(List.of("1", "5", "10", "15", "20", "25", "30"), typed, 50);
                case "settier":
                    return filter(List.of("1", "2", "3", "4", "5", "6", "7", "8", "9", "10"), typed, 50);
                case "addxp":
                    return filter(List.of("50", "100", "500", "1000", "5000"), typed, 50);
                case "give":
                    return plugin.getRlvpHandler() == null ? new ArrayList<>() : filter(plugin.getRlvpHandler().getItemIds(), typed, 50);
                case "itemlist":
                case "list": {
                    List<String> opts = new ArrayList<>(List.of("1", "2", "3"));
                    if (plugin.getRlvpHandler() != null) plugin.getRlvpHandler().getCategories().forEach(c -> opts.add(c.id()));
                    return filter(opts, typed, 60);
                }
                case "language":
                    return filter(List.of("es", "en", "fr", "pt", "zh_cn"), typed, 50);
                default:
                    return new ArrayList<>();
            }
        }
        return new ArrayList<>();
    }
}
