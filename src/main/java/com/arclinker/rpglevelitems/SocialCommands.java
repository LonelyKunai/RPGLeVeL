package com.arclinker.rpglevelitems;

import com.arclinker.rpglevelitems.social.PartyManager;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.util.stream.Collectors;

/** /party, /pc (party chat), /duel, /achievements. */
public class SocialCommands implements CommandExecutor, TabCompleter {
    private final RPGLevelTier plugin;

    public SocialCommands(RPGLevelTier plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("§cSolo jugadores.");
            return true;
        }
        switch (command.getName().toLowerCase(Locale.ROOT)) {
            case "party" -> party(p, args);
            case "pc" -> plugin.getParties().chat(p, String.join(" ", args));
            case "duel" -> duel(p, args);
            case "achievements" -> plugin.getAchievementGUI().open(p, 0);
            case "dungeon" -> dungeon(p, args);
            case "guild" -> guild(p, args);
            case "gc" -> plugin.getGuilds().chat(p, String.join(" ", args));
            case "trade" -> trade(p, args);
            case "ah" -> auction(p, args);
            case "shop" -> shop(p, args);
            default -> { }
        }
        return true;
    }

    private void party(Player p, String[] args) {
        PartyManager pm = plugin.getParties();
        String sub = args.length == 0 ? "list" : args[0].toLowerCase(Locale.ROOT);
        String err = null;
        switch (sub) {
            case "create" -> { err = pm.create(p); if (err == null) p.sendMessage("§aParty creada. Invita con /party invite <jugador>."); }
            case "invite" -> {
                Player t = args.length > 1 ? Bukkit.getPlayerExact(args[1]) : null;
                if (t == null) err = "Jugador no disponible.";
                else { err = pm.invite(p, t); if (err == null) p.sendMessage("§aInvitación enviada a " + t.getName() + "."); }
            }
            case "accept" -> { err = pm.accept(p); if (err == null) p.sendMessage("§aTe uniste a la party."); }
            case "decline" -> p.sendMessage("§eInvitación ignorada.");
            case "leave" -> { pm.leave(p); p.sendMessage("§eSaliste de la party."); }
            case "kick" -> err = args.length > 1 ? pm.kick(p, args[1]) : "Uso: /party kick <jugador>";
            case "chat" -> pm.chat(p, String.join(" ", Arrays.copyOfRange(args, 1, args.length)));
            default -> {
                PartyManager.Party party = pm.of(p);
                if (party == null) {
                    p.sendMessage("§eNo estás en una party. §f/party create §7| §f/party invite|accept|leave|kick|chat|list");
                } else {
                    p.sendMessage("§6Party §7(" + party.members.size() + "/" + pm.maxSize() + ") §8- bonus de XP por clases distintas: x" + String.format(Locale.ROOT, "%.2f", pm.bonusMultiplier(p)));
                    for (UUID id : party.members) {
                        Player m = Bukkit.getPlayer(id);
                        var r = plugin.getPlayerStore().get(id);
                        p.sendMessage(" " + (id.equals(party.leader) ? "§6★ " : "§7• ") + "§f" + (m == null ? r.name + " §8(offline)" : m.getName()) + " §7" + r.classId);
                    }
                }
            }
        }
        if (err != null) p.sendMessage("§c" + err);
    }

    private void guild(Player p, String[] args) {
        var gm = plugin.getGuilds();
        String sub = args.length == 0 ? "info" : args[0].toLowerCase(Locale.ROOT);
        String err = null;
        switch (sub) {
            case "create" -> {
                err = args.length < 3 ? "Uso: /guild create <nombre> <etiqueta>" : gm.create(p, args[1], args[2]);
                if (err == null) p.sendMessage("§aGremio creado.");
            }
            case "invite" -> {
                Player t = args.length > 1 ? Bukkit.getPlayerExact(args[1]) : null;
                err = t == null ? "Jugador no disponible." : gm.invite(p, t);
                if (err == null) p.sendMessage("§aInvitación enviada.");
            }
            case "accept" -> { err = gm.accept(p); if (err == null) p.sendMessage("§aTe uniste al gremio."); }
            case "leave" -> err = gm.leave(p);
            case "kick" -> err = args.length > 1 ? gm.kick(p, args[1]) : "Uso: /guild kick <jugador>";
            case "promote" -> err = args.length > 1 ? gm.promote(p, args[1]) : "Uso: /guild promote <jugador>";
            case "demote" -> err = args.length > 1 ? gm.demote(p, args[1]) : "Uso: /guild demote <jugador>";
            case "disband" -> err = gm.disband(p);
            case "deposit" -> err = args.length > 1 ? gm.deposit(p, parseLong(args[1])) : "Uso: /guild deposit <cantidad>";
            case "withdraw" -> err = args.length > 1 ? gm.withdraw(p, parseLong(args[1])) : "Uso: /guild withdraw <cantidad>";
            case "chest" -> gm.openChest(p);
            case "chat" -> gm.chat(p, String.join(" ", Arrays.copyOfRange(args, 1, args.length)));
            case "list" -> {
                p.sendMessage("§6Gremios:");
                for (var g : gm.all()) p.sendMessage(" §b[" + g.tag + "] §f" + g.name + " §7nv " + g.level + " - " + g.members.size() + " miembros");
            }
            default -> {
                var g = args.length > 1 ? gm.byId(args[1]) : gm.of(p);
                if (g == null) {
                    p.sendMessage("§eNo estás en un gremio. §f/guild create <nombre> <etiqueta> §7| list | invite | accept | leave | kick | promote | demote | disband | deposit | withdraw | chest | chat");
                } else {
                    p.sendMessage("§6Gremio §b[" + g.tag + "] §f" + g.name + " §7- nivel §e" + g.level + " §7(" + g.xp + "/" + gm.xpForNext(g) + " XP)");
                    p.sendMessage("§7Banco: §e" + g.bank + " §7| Miembros: §f" + g.members.size() + "/" + gm.maxMembers(g));
                    for (var e : g.members.entrySet()) {
                        Player m = Bukkit.getPlayer(e.getKey());
                        p.sendMessage(" " + (e.getValue() == com.arclinker.rpglevelitems.social.GuildManager.Role.LEADER ? "§6★ " : e.getValue() == com.arclinker.rpglevelitems.social.GuildManager.Role.OFFICER ? "§b◆ " : "§7• ")
                                + (m != null ? "§a" : "§8") + plugin.getPlayerStore().get(e.getKey()).name);
                    }
                }
            }
        }
        if (err != null) p.sendMessage("§c" + err);
    }

    private static long parseLong(String s) {
        try { return Long.parseLong(s); } catch (NumberFormatException e) { return 0; }
    }

    private void trade(Player p, String[] args) {
        var tm = plugin.getTrades();
        if (args.length > 0 && args[0].equalsIgnoreCase("accept")) {
            String e = tm.accept(p);
            if (e != null) p.sendMessage("§c" + e);
            return;
        }
        Player t = args.length > 0 ? Bukkit.getPlayerExact(args[0]) : null;
        if (t == null) {
            p.sendMessage("§eUso: /trade <jugador> | /trade accept");
            return;
        }
        String e = tm.request(p, t);
        p.sendMessage(e == null ? "§aSolicitud enviada a " + t.getName() + "." : "§c" + e);
    }

    private void shop(Player p, String[] args) {
        var sh = plugin.getShop();
        String sub = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "sell" -> {
                ItemStack hand = p.getInventory().getItemInMainHand();
                String key = sh.keyOf(hand);
                if (key == null) {
                    p.sendMessage("§cLa tienda no compra el ítem de tu mano (o está renombrado / encantado).");
                    return;
                }
                int qty = hand.getAmount();
                if (args.length > 1) {
                    if (args[1].equalsIgnoreCase("all")) qty = Integer.MAX_VALUE;
                    else { try { qty = Math.max(1, Integer.parseInt(args[1])); } catch (NumberFormatException ignored) { } }
                }
                String err = sh.sell(p, key, qty);
                if (err != null) p.sendMessage("§c" + err);
            }
            case "info" -> {
                p.sendMessage("§6Tienda §7- relleno en §f" + sh.timeToRefill() + " §7- jugadores activos: §f" + sh.activePlayers());
                p.sendMessage("§7Límite de compra por objeto y ciclo: §f" + (int) sh.playerLimitPercent() + "% §7por jugador, §f" + (int) sh.guildLimitPercent() + "% §7por gremio entero.");
                p.sendMessage("§7Más stock = precio más bajo. Lo que vendes sube el stock; lo que compras lo baja.");
            }
            case "price" -> {
                var e = args.length > 1 ? sh.entry(args[1].replace('|', ':')) : null;
                if (e == null) {
                    p.sendMessage("§eUso: /shop price <objeto>");
                    return;
                }
                p.sendMessage("§6" + sh.display(e.key) + " §7stock §f" + e.stock + "/" + e.capacity + " §7compra §e" + plugin.getEconomy().format(sh.buyPrice(e))
                        + " §7venta §e" + plugin.getEconomy().format(sh.sellPrice(e)) + " §7(base " + (long) e.basePrice + ")");
            }
            default -> plugin.getShopGUI().open(p, sub, 0);
        }
    }

    private void auction(Player p, String[] args) {
        var ah = plugin.getAuction();
        String sub = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "sell" -> {
                double price;
                try { price = args.length > 1 ? Double.parseDouble(args[1]) : -1; } catch (NumberFormatException e) { price = -1; }
                String err = ah.sell(p, price);
                p.sendMessage(err == null ? "§aÍtem publicado por §e" + plugin.getEconomy().format(Math.floor(price)) + "§a." : "§c" + err);
            }
            case "mine" -> ah.openMine(p);
            case "claim" -> {
                int n = ah.claim(p);
                p.sendMessage(n > 0 ? "§aRecogiste " + n + " ítem(s)." : "§7Nada que recoger.");
            }
            default -> ah.open(p);
        }
    }

    private void dungeon(Player p, String[] args) {
        var dm = plugin.getDungeons();
        String sub = args.length == 0 ? "list" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "start", "enter" -> {
                String e = args.length > 1 ? dm.start(p, args[1]) : "Uso: /dungeon start <id>";
                if (e != null) p.sendMessage("§c" + e);
            }
            case "leave", "exit" -> dm.leave(p);
            default -> {
                p.sendMessage("§6Mazmorras §7(/dungeon start <id> | leave):");
                for (String id : dm.ids()) p.sendMessage(" " + dm.describe(id));
            }
        }
    }

    private void duel(Player p, String[] args) {
        var dm = plugin.getDuels();
        String sub = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "accept" -> { String e = dm.accept(p); if (e != null) p.sendMessage("§c" + e); }
            case "decline" -> { dm.decline(p); p.sendMessage("§eReto rechazado."); }
            case "forfeit", "surrender" -> dm.forfeit(p);
            case "" -> p.sendMessage("§eUso: /duel <jugador> [apuesta] | accept | decline | forfeit");
            default -> {
                Player t = Bukkit.getPlayerExact(args[0]);
                if (t == null) { p.sendMessage("§cJugador no disponible."); return; }
                double bet = 0;
                if (args.length > 1) { try { bet = Math.max(0, Math.floor(Double.parseDouble(args[1]))); } catch (NumberFormatException ignored) { } }
                String e = dm.request(p, t, bet);
                p.sendMessage(e == null ? "§aReto enviado a " + t.getName() + "." : "§c" + e);
            }
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        List<String> out = new ArrayList<>();
        String name = command.getName().toLowerCase(Locale.ROOT);
        if (name.equals("guild") && args.length == 1) out.addAll(List.of("create", "invite", "accept", "leave", "kick", "promote", "demote", "disband", "deposit", "withdraw", "chest", "chat", "list", "info"));
        if (name.equals("guild") && args.length == 2 && List.of("invite").contains(args[0].toLowerCase(Locale.ROOT))) Bukkit.getOnlinePlayers().forEach(pl -> out.add(pl.getName()));
        if (name.equals("trade") && args.length == 1) { out.add("accept"); Bukkit.getOnlinePlayers().forEach(pl -> out.add(pl.getName())); }
        if (name.equals("shop") && args.length == 1) { out.addAll(List.of("sell", "info", "price")); plugin.getShop().categories().forEach(c -> out.add(c.id())); }
        if (name.equals("shop") && args.length == 2 && args[0].equalsIgnoreCase("price")) plugin.getShop().all().forEach(e -> out.add(e.key));
        if (name.equals("ah") && args.length == 1) out.addAll(List.of("sell", "mine", "claim"));
        if (name.equals("dungeon") && args.length == 1) out.addAll(List.of("start", "leave", "list"));
        if (name.equals("dungeon") && args.length == 2 && args[0].equalsIgnoreCase("start")) out.addAll(plugin.getDungeons().ids());
        if (name.equals("party") && args.length == 1) out.addAll(List.of("create", "invite", "accept", "leave", "kick", "chat", "list"));
        if ((name.equals("party") && args.length == 2 && (args[0].equalsIgnoreCase("invite") || args[0].equalsIgnoreCase("kick")))
                || (name.equals("duel") && args.length == 1)) Bukkit.getOnlinePlayers().forEach(pl -> out.add(pl.getName()));
        if (name.equals("duel") && args.length == 1) out.addAll(List.of("accept", "decline", "forfeit"));
        String t = args[args.length - 1].toLowerCase(Locale.ROOT);
        return out.stream().filter(s -> s.toLowerCase(Locale.ROOT).startsWith(t)).collect(Collectors.toList());
    }
}
