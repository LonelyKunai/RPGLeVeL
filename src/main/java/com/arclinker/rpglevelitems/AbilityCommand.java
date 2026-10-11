package com.arclinker.rpglevelitems;

import com.arclinker.rpglevelitems.ability.AbilityDef;
import com.arclinker.rpglevelitems.ability.AbilityManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/** /ability [gui [class|subclass] | list [class|subclass|items] | info <id> | unlock <id> | upgrade <id> | copy <id> | points] */
public class AbilityCommand implements CommandExecutor, TabCompleter {
    private final RPGLevelTier plugin;

    public AbilityCommand(RPGLevelTier plugin) {
        this.plugin = plugin;
    }

    private List<AbilityDef> ownTree(Player p, boolean sub) {
        var r = plugin.getPlayerStore().get(p);
        String id = sub ? r.subclassId : r.classId;
        return id.isEmpty() ? List.of() : plugin.getAbilityManager().tree((sub ? "sub:" : "class:") + id);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("§cEste comando solo puede ser usado por jugadores.");
            return true;
        }
        if (!p.hasPermission("rpgleveltier.ability")) {
            p.sendMessage("§cNo tienes permiso para usar este comando.");
            return true;
        }
        AbilityManager am = plugin.getAbilityManager();
        if (args.length == 0 || args[0].equalsIgnoreCase("gui")) {
            boolean sub = args.length >= 2 && args[1].toLowerCase(Locale.ROOT).startsWith("sub");
            plugin.getAbilityGUI().open(p, sub);
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "points" -> {
                for (String tree : am.activeTrees(p)) {
                    p.sendMessage("§6" + tree + "§7: §a" + am.availablePoints(p, tree) + " §7puntos libres | §d" + am.abilityXp(p, tree) + " §7ability XP | nivel §e" + am.treeLevel(p, tree));
                }
                if (am.activeTrees(p).isEmpty()) p.sendMessage("§eElige una clase con /class.");
            }
            case "list" -> {
                String what = args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : "class";
                if (what.equals("items") || what.equals("item")) {
                    p.sendMessage("§6Habilidades de ítems (se activan por tier del ítem RPG):");
                    for (AbilityDef d : am.itemAbilities()) {
                        p.sendMessage(" §8• §f" + d.name + " §7[" + d.trigger + ", " + String.join("/", d.appliesTo) + ", tier " + d.minTier + "+] §8(" + d.id + ")");
                    }
                } else {
                    boolean sub = what.startsWith("sub");
                    List<AbilityDef> tree = ownTree(p, sub);
                    if (tree.isEmpty()) {
                        p.sendMessage(sub ? "§eNo tienes subclase." : "§eNo tienes clase.");
                        return true;
                    }
                    p.sendMessage("§6Habilidades de tu " + (sub ? "subclase" : "clase") + ":");
                    for (AbilityDef d : tree) {
                        int lv = am.levelOf(p, d.id);
                        p.sendMessage(" " + (lv > 0 ? "§a✔ " : am.unlockBlocker(p, d) == null ? "§e● " : "§c✘ ") + "§f" + d.name
                                + (lv > 0 ? " §7nv " + lv + "/" + d.maxLevel : " §7(nivel " + d.requiredLevel + ", " + d.cost + " pt)") + " §8" + d.id);
                    }
                }
            }
            case "bind" -> {
                if (args.length < 3) {
                    p.sendMessage("§eUso: /ability bind <1-4> <id>   §7(usa F para lanzar, Shift+F para cambiar de ranura)");
                    return true;
                }
                int slot;
                try { slot = Integer.parseInt(args[1]); } catch (NumberFormatException e) { slot = 0; }
                AbilityDef d = am.get(args[2]);
                if (slot < 1 || slot > 4 || d == null || d.kind == AbilityDef.Kind.ITEM) {
                    p.sendMessage("§cRanura (1-4) o habilidad inválida.");
                    return true;
                }
                if (!am.isUnlocked(p, d.id)) {
                    p.sendMessage("§cPrimero desbloquea esa habilidad.");
                    return true;
                }
                plugin.getPlayerStore().get(p).hotbar[slot - 1] = d.id;
                plugin.getPlayerStore().markDirty();
                p.sendMessage("§aRanura " + slot + " = §f" + d.name + "§a. " + plugin.getHotbar().describe(p));
            }
            case "unbind" -> {
                int slot;
                try { slot = args.length > 1 ? Integer.parseInt(args[1]) : 0; } catch (NumberFormatException e) { slot = 0; }
                if (slot < 1 || slot > 4) {
                    p.sendMessage("§eUso: /ability unbind <1-4>");
                    return true;
                }
                plugin.getPlayerStore().get(p).hotbar[slot - 1] = "";
                plugin.getPlayerStore().markDirty();
                p.sendMessage("§eRanura " + slot + " liberada. " + plugin.getHotbar().describe(p));
            }
            case "hotbar" -> p.sendMessage("§6Hotbar de habilidades: " + plugin.getHotbar().describe(p));
            case "respec" -> {
                double cost = plugin.getConfig().getDouble("respec.cost", 200);
                if (args.length < 2 || !args[1].equalsIgnoreCase("confirm")) {
                    p.sendMessage("§eRespec: quita todas tus habilidades de clase y subclase, devuelve los puntos y la ability XP gastada en mejoras.");
                    p.sendMessage("§eCuesta §f" + plugin.getEconomy().format(cost) + "§e. Escribe §f/ability respec confirm§e.");
                    return true;
                }
                if (!plugin.getEconomy().withdraw(p, cost)) {
                    p.sendMessage("§cNo tienes " + plugin.getEconomy().format(cost) + ".");
                    return true;
                }
                long refund = am.respec(p);
                p.sendMessage("§aHabilidades reiniciadas. Ability XP devuelta: §d" + refund + "§a. Vuelve a gastar tus puntos en /ability.");
            }
            case "info", "unlock", "upgrade", "copy" -> {
                if (args.length < 2) {
                    p.sendMessage("§eUso: /ability " + args[0] + " <id>");
                    return true;
                }
                AbilityDef d = am.get(args[1]);
                if (d == null) {
                    p.sendMessage("§cHabilidad no encontrada: " + args[1]);
                    return true;
                }
                switch (args[0].toLowerCase(Locale.ROOT)) {
                    case "unlock" -> am.unlock(p, d);
                    case "upgrade" -> am.upgrade(p, d);
                    case "copy" -> plugin.getAbilityGUI().giveTome(p, d);
                    default -> {
                        int lv = am.levelOf(p, d.id);
                        p.sendMessage("§d" + d.name + " §8(" + d.id + ")");
                        p.sendMessage("§7" + d.description);
                        p.sendMessage("§7Coste: §a" + d.cost + " §7| Nivel req.: §f" + d.requiredLevel + " §7| Máx: §f" + d.maxLevel + " §7| Tu nivel: §e" + lv);
                        if (!d.requiredAbilities.isEmpty()) p.sendMessage("§7Requiere: §f" + d.requiredAbilities);
                        p.sendMessage("§7Enfriamiento: §f" + d.cooldownAt(Math.max(1, lv)) + "s");
                    }
                }
            }
            default -> p.sendMessage("§eUso: /ability [gui [class|subclass] | list [class|subclass|items] | info|unlock|upgrade|copy <id> | points | bind <1-4> <id> | unbind <1-4> | hotbar | respec]");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            out.addAll(List.of("gui", "list", "info", "unlock", "upgrade", "copy", "points", "bind", "unbind", "hotbar", "respec"));
        } else if (args.length == 2) {
            switch (args[0].toLowerCase(Locale.ROOT)) {
                case "gui" -> out.addAll(List.of("class", "subclass"));
                case "bind", "unbind" -> out.addAll(List.of("1", "2", "3", "4"));
                case "respec" -> out.add("confirm");
                case "list" -> out.addAll(List.of("class", "subclass", "items"));
                case "info", "unlock", "upgrade", "copy" -> {
                    if (sender instanceof Player p) {
                        for (String tree : plugin.getAbilityManager().activeTrees(p)) {
                            for (AbilityDef d : plugin.getAbilityManager().tree(tree)) {
                                boolean unlocked = plugin.getAbilityManager().isUnlocked(p, d.id);
                                String a = args[0].toLowerCase(Locale.ROOT);
                                if (a.equals("unlock") && unlocked) continue;
                                if ((a.equals("upgrade") || a.equals("copy")) && !unlocked) continue;
                                out.add(d.id);
                            }
                        }
                    }
                }
                default -> { }
            }
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("bind") && sender instanceof Player bp) {
            for (String tree : plugin.getAbilityManager().activeTrees(bp)) {
                for (AbilityDef d : plugin.getAbilityManager().tree(tree)) if (plugin.getAbilityManager().isUnlocked(bp, d.id)) out.add(d.id);
            }
        }
        String t = args[args.length - 1].toLowerCase(Locale.ROOT);
        return out.stream().filter(s -> s.toLowerCase(Locale.ROOT).startsWith(t)).collect(Collectors.toList());
    }
}
