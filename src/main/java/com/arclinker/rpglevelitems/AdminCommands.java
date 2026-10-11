package com.arclinker.rpglevelitems;

import com.arclinker.rpglevelitems.stats.ItemData;
import com.arclinker.rpglevelitems.zones.ZoneManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.*;

/** Admin sub-commands of /rlv: roll, gem, sockets, affix, boss, event, zone, coins, debug. */
public final class AdminCommands {
    public static final List<String> NAMES = List.of("roll", "gem", "sockets", "affix", "boss", "event", "zone", "coins", "debug", "npc", "editor", "userinfo", "jobability", "shop");

    private final RPGLevelTier plugin;

    public AdminCommands(RPGLevelTier plugin) {
        this.plugin = plugin;
    }

    /** @return true if the sub-command was handled */
    public boolean handle(CommandSender sender, String[] args) {
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (!NAMES.contains(sub)) return false;
        String[] a = Arrays.copyOfRange(args, 1, args.length);
        switch (sub) {
            case "roll" -> roll(sender, a);
            case "gem" -> gem(sender, a);
            case "sockets" -> sockets(sender, a);
            case "affix" -> affix(sender);
            case "boss" -> boss(sender, a);
            case "event" -> event(sender, a);
            case "zone" -> zone(sender, a);
            case "coins" -> coins(sender, a);
            case "debug" -> debug(sender, a);
            case "npc" -> npc(sender, a);
            case "userinfo" -> userinfo(sender, a);
            case "jobability" -> jobability(sender, a);
            case "shop" -> shop(sender, a);
            case "editor" -> { Player p = player(sender); if (p != null) plugin.getEditor().open(p); }
            default -> { }
        }
        return true;
    }

    private Player player(CommandSender s) {
        if (s instanceof Player p) return p;
        s.sendMessage("§cEste subcomando solo puede usarlo un jugador.");
        return null;
    }

    private ItemStack hand(Player p) {
        ItemStack it = p.getInventory().getItemInMainHand();
        if (it.getType().isAir()) {
            p.sendMessage("§cTen un ítem en la mano.");
            return null;
        }
        return it;
    }

    private void roll(CommandSender s, String[] a) {
        Player p = player(s);
        if (p == null) return;
        ItemStack it = hand(p);
        if (it == null) return;
        var am = plugin.getAffixManager();
        int tier = a.length > 0 ? parse(a[0], am.tierOf(it)) : am.tierOf(it);
        int count = a.length > 1 ? parse(a[1], -1) : -1;
        int n = am.roll(it, tier, count);
        p.getInventory().setItemInMainHand(it);
        p.sendMessage("§aAfijos aplicados: §f" + n + " §7(tier " + tier + ")");
    }

    private void gem(CommandSender s, String[] a) {
        var gm = plugin.getGemManager();
        if (a.length == 0 || a[0].equalsIgnoreCase("list")) {
            s.sendMessage("§6Gemas: §f" + String.join(", ", gm.all().stream().map(g -> g.id()).toList()));
            s.sendMessage("§7/rlv gem give <id> [nivel] [cantidad] | clear (ítem en mano)");
            return;
        }
        Player p = player(s);
        if (p == null) return;
        if (a[0].equalsIgnoreCase("clear")) {
            ItemStack it = hand(p);
            if (it == null) return;
            p.sendMessage("§eGemas destruidas: " + gm.clear(it));
            p.getInventory().setItemInMainHand(it);
            return;
        }
        if (a[0].equalsIgnoreCase("give") && a.length > 1) {
            ItemStack g = gm.item(a[1], a.length > 2 ? parse(a[2], 1) : 1);
            if (g == null) {
                p.sendMessage("§cGema desconocida.");
                return;
            }
            g.setAmount(a.length > 3 ? Math.max(1, Math.min(64, parse(a[3], 1))) : 1);
            p.getInventory().addItem(g);
            p.sendMessage("§aGema entregada.");
        }
    }

    private void sockets(CommandSender s, String[] a) {
        Player p = player(s);
        if (p == null) return;
        ItemStack it = hand(p);
        if (it == null) return;
        int n = a.length > 0 ? Math.max(0, Math.min(6, parse(a[0], 1))) : 1;
        var meta = it.getItemMeta();
        ItemData.setSockets(meta, n);
        it.setItemMeta(meta);
        ItemData.rebuildLore(it);
        p.getInventory().setItemInMainHand(it);
        p.sendMessage("§aRanuras: §f" + n);
    }

    private void affix(CommandSender s) {
        s.sendMessage("§6Afijos disponibles:");
        for (var a : plugin.getAffixManager().all()) {
            s.sendMessage(" §b" + a.id() + " §7- " + a.name() + " §8(" + com.arclinker.rpglevelitems.stats.Stats.label(a.stat()) + " " + a.min() + "–" + a.max() + ", " + a.slots() + ")");
        }
    }

    private void boss(CommandSender s, String[] a) {
        var bm = plugin.getBosses();
        String sub = a.length == 0 ? "list" : a[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "spawn" -> {
                Player p = player(s);
                if (p == null) return;
                if (a.length < 2 || bm.spawn(a[1], p.getLocation()) == null) p.sendMessage("§cJefe desconocido. Usa /rlv boss list");
            }
            case "killall" -> {
                bm.killAll();
                s.sendMessage("§eJefes eliminados.");
            }
            default -> {
                s.sendMessage("§6Jefes definidos: §f" + String.join(", ", bm.ids()));
                s.sendMessage("§6Vivos:");
                bm.aliveDescriptions().forEach(d -> s.sendMessage(" §c" + d));
            }
        }
    }

    private void event(CommandSender s, String[] a) {
        var em = plugin.getEvents();
        String sub = a.length == 0 ? "status" : a[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "start" -> {
                if (a.length < 2 || !em.start(a[1])) s.sendMessage("§cEvento desconocido: " + em.ids());
            }
            case "stop" -> em.stop();
            default -> {
                s.sendMessage("§6Evento activo: §f" + (em.active() ? em.activeName() : "ninguno"));
                s.sendMessage("§7Eventos: " + em.ids() + " | /rlv event start <id> | stop");
            }
        }
    }

    private void zone(CommandSender s, String[] a) {
        ZoneManager zm = plugin.getZones();
        String sub = a.length == 0 ? "list" : a[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "pos1", "pos2" -> {
                Player p = player(s);
                if (p == null) return;
                zm.selection(p)[sub.equals("pos1") ? 0 : 1] = p.getLocation().getBlock().getLocation();
                p.sendMessage("§a" + sub + " guardado.");
            }
            case "create" -> {
                Player p = player(s);
                if (p == null) return;
                Location[] sel = zm.selection(p);
                if (a.length < 2 || sel[0] == null || sel[1] == null) {
                    p.sendMessage("§eUso: /rlv zone pos1, pos2 y luego /rlv zone create <id>");
                    return;
                }
                if (!sel[0].getWorld().equals(sel[1].getWorld())) {
                    p.sendMessage("§cLas posiciones deben estar en el mismo mundo.");
                    return;
                }
                zm.create(a[1], sel[0], sel[1]);
                p.sendMessage("§aZona creada: " + a[1] + ". Añade flags con /rlv zone flag " + a[1] + " <flag> <valor>");
            }
            case "delete" -> s.sendMessage(a.length > 1 && zm.delete(a[1]) ? "§aZona borrada." : "§cZona no encontrada.");
            case "flag" -> {
                ZoneManager.Zone z = a.length > 3 ? zm.get(a[1]) : null;
                if (z == null) {
                    s.sendMessage("§eUso: /rlv zone flag <id> <pvp|abilities|xp|mob_level|mob_range|stations> <valor|remove>");
                    return;
                }
                if (a[3].equalsIgnoreCase("remove")) z.flags.remove(a[2]); else z.flags.put(a[2], a[3]);
                zm.save();
                s.sendMessage("§aFlag actualizada.");
            }
            case "info" -> {
                if (a.length > 1 && zm.get(a[1]) != null) {
                    ZoneManager.Zone z = zm.get(a[1]);
                    s.sendMessage("§6" + z.id + " §7" + z.world + " (" + z.x1 + "," + z.y1 + "," + z.z1 + ") → (" + z.x2 + "," + z.y2 + "," + z.z2 + ") §f" + z.flags);
                } else if (s instanceof Player p) {
                    p.sendMessage("§6Zonas aquí: §f" + zm.at(p.getLocation()).stream().map(z -> z.id + z.flags).toList());
                }
            }
            default -> {
                s.sendMessage("§6Zonas (" + zm.all().size() + "): /rlv zone pos1|pos2|create|delete|flag|info");
                for (ZoneManager.Zone z : zm.all()) s.sendMessage(" §e" + z.id + " §7" + z.world + " §f" + z.flags);
            }
        }
    }

    private void coins(CommandSender s, String[] a) {
        if (a.length < 3) {
            s.sendMessage("§eUso: /rlv coins <give|take|set> <jugador> <cantidad>");
            return;
        }
        OfflinePlayer t = Bukkit.getOfflinePlayer(a[1]);
        double amt = parse(a[2], 0);
        var eco = plugin.getEconomy();
        switch (a[0].toLowerCase(Locale.ROOT)) {
            case "give" -> eco.deposit(t.getUniqueId(), amt);
            case "take" -> eco.withdraw(t.getUniqueId(), amt);
            case "set" -> {
                double cur = eco.balance(t.getUniqueId());
                if (amt > cur) eco.deposit(t.getUniqueId(), amt - cur); else eco.withdraw(t.getUniqueId(), cur - amt);
            }
            default -> { }
        }
        s.sendMessage("§aSaldo de " + a[1] + ": §e" + eco.format(eco.balance(t.getUniqueId())));
    }

    private void shop(CommandSender s, String[] a) {
        var sh = plugin.getShop();
        String sub = a.length == 0 ? "info" : a[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "refill" -> {
                sh.refill(true);
                s.sendMessage("§aTienda rellenada.");
            }
            case "stock" -> {
                if (a.length < 3 || sh.entry(a[1]) == null) {
                    s.sendMessage("§eUso: /rlv shop stock <objeto> <cantidad>");
                    return;
                }
                sh.setStock(a[1], parse(a[2], 0));
                s.sendMessage("§aStock actualizado.");
            }
            case "reload" -> {
                sh.reload();
                s.sendMessage("§aTienda recargada.");
            }
            default -> s.sendMessage("§6Tienda: §f" + sh.all().size() + " objetos, " + sh.activePlayers() + " jugadores activos, relleno en " + sh.timeToRefill()
                    + " §7(/rlv shop refill | stock <objeto> <n> | reload)");
        }
    }

    private void jobability(CommandSender s, String[] a) {
        var jab = plugin.getJobAbilities();
        String sub = a.length == 0 ? "list" : a[0].toLowerCase(Locale.ROOT);
        if (sub.equals("list")) {
            s.sendMessage("§6Habilidades de oficio (" + jab.all().size() + "):");
            for (var d : jab.all()) s.sendMessage(" §d" + d.id() + " §7- " + d.name() + " §8(" + jab.condition(d) + ")");
            s.sendMessage("§7/rlv jobability give|remove <jugador> <id|random>");
            return;
        }
        Player t = a.length > 1 ? Bukkit.getPlayerExact(a[1]) : null;
        if (t == null || a.length < 3) {
            s.sendMessage("§eUso: /rlv jobability <give|remove> <jugador en línea> <id|random>");
            return;
        }
        if (sub.equals("give")) {
            if (a[2].equalsIgnoreCase("random")) {
                var d = jab.grantRandom(t, "regalo del staff");
                s.sendMessage(d == null ? "§cNo quedan habilidades aleatorias por desbloquear." : "§aOtorgada: " + d.id());
            } else {
                var d = jab.get(a[2]);
                s.sendMessage(d == null ? "§cHabilidad desconocida." : jab.unlock(t, d, "regalo del staff") ? "§aOtorgada." : "§eYa la tenía.");
            }
        } else if (sub.equals("remove")) {
            s.sendMessage(jab.remove(t, a[2]) ? "§aQuitada." : "§eNo la tenía.");
        }
    }

    private void userinfo(CommandSender s, String[] a) {
        OfflinePlayer target = null;
        String section = null;
        if (a.length >= 1 && UserInfo.SECTIONS.contains(a[0].toLowerCase(Locale.ROOT)) && s instanceof Player self) {
            target = self; // /rlv userinfo <section>
            section = a[0];
        } else if (a.length >= 1) {
            target = AdminLookup.find(a[0]);
            if (target == null) {
                s.sendMessage("§cJugador desconocido: " + a[0]);
                return;
            }
            if (a.length >= 2) section = a[1];
        } else if (s instanceof Player self) {
            target = self;
        }
        if (target == null) {
            s.sendMessage("§eUso: /rlv userinfo <jugador> [" + String.join("|", UserInfo.SECTIONS) + "]");
            return;
        }
        new UserInfo(plugin).show(s, target, section);
    }

    private void npc(CommandSender s, String[] a) {
        var qx = plugin.getQuestExtras();
        String sub = a.length == 0 ? "list" : a[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "create" -> {
                Player p = player(s);
                if (p == null) return;
                if (a.length < 2 || qx.spawnNpc(p.getLocation(), a[1]) == null) p.sendMessage("§cCadena desconocida: " + qx.chains().stream().map(c -> c.id()).toList());
                else p.sendMessage("§aNPC creado.");
            }
            case "remove" -> s.sendMessage("§eNPC eliminados: " + qx.removeNpcs(a.length > 1 ? a[1] : null));
            default -> s.sendMessage("§6/rlv npc create <cadena> | remove [cadena]  §7Cadenas: " + qx.chains().stream().map(c -> c.id()).toList());
        }
    }

    private void debug(CommandSender s, String[] a) {
        s.sendMessage("§6--- RPGLeVeL debug ---");
        try {
            double[] tps = (double[]) Bukkit.class.getMethod("getTPS").invoke(null);
            s.sendMessage("§7TPS: §f" + String.format(Locale.ROOT, "%.1f / %.1f / %.1f", tps[0], tps[1], tps[2]));
        } catch (Exception ignored) { }
        Runtime rt = Runtime.getRuntime();
        s.sendMessage("§7Memoria: §f" + (rt.totalMemory() - rt.freeMemory()) / 1048576 + "/" + rt.maxMemory() / 1048576 + " MB");
        s.sendMessage("§7Jugadores guardados: §f" + plugin.getPlayerStore().all().size() + " §8| almacenamiento: " + plugin.getPlayerStore().backendName());
        s.sendMessage("§7Ítems: §f" + (plugin.getRlvpHandler() == null ? 0 : plugin.getRlvpHandler().getItemIds().size())
                + " §8| habilidades: " + plugin.getAbilityManager().all().size() + " | afijos: " + plugin.getAffixManager().all().size()
                + " | gemas: " + plugin.getGemManager().all().size() + " | estaciones: " + plugin.getStationManager().all().size());
        s.sendMessage("§7Temporada de rangos: §f" + plugin.getRankManager().season() + " §8(" + plugin.getRankManager().timeLeft() + ")");
        s.sendMessage("§7Evento: §f" + (plugin.getEvents().active() ? plugin.getEvents().activeName() : "ninguno")
                + " §8| jefes vivos: " + plugin.getBosses().aliveDescriptions().size());
        if (a.length > 0) {
            Player t = Bukkit.getPlayerExact(a[0]);
            if (t != null) {
                s.sendMessage("§7Estadísticas de " + t.getName() + ":");
                plugin.getStatEngine().all(t).forEach((k, v) -> s.sendMessage("  §9" + k + " §f" + v));
                s.sendMessage("  §bmaná §f" + (int) plugin.getMana().get(t) + "/" + (int) plugin.getMana().max(t));
            }
        }
    }

    private static String a0(String[] args) {
        return args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "";
    }

    private static int parse(String s, int def) {
        try { return Integer.parseInt(s); } catch (NumberFormatException e) { return def; }
    }

    public List<String> tab(CommandSender sender, String[] args) {
        List<String> out = new ArrayList<>();
        String sub = args[0].toLowerCase(Locale.ROOT);
        int n = args.length;
        switch (sub) {
            case "roll" -> { if (n == 2) out.addAll(List.of("1", "2", "3", "4", "5")); if (n == 3) out.addAll(List.of("0", "1", "2", "3", "4")); }
            case "gem" -> {
                if (n == 2) out.addAll(List.of("list", "give", "clear"));
                if (n == 3 && args[1].equalsIgnoreCase("give")) plugin.getGemManager().all().forEach(g -> out.add(g.id()));
                if (n == 4 && args[1].equalsIgnoreCase("give")) out.addAll(List.of("1", "2", "3"));
            }
            case "sockets" -> { if (n == 2) out.addAll(List.of("0", "1", "2", "3", "4")); }
            case "boss" -> {
                if (n == 2) out.addAll(List.of("list", "spawn", "killall"));
                if (n == 3 && args[1].equalsIgnoreCase("spawn")) out.addAll(plugin.getBosses().ids());
            }
            case "event" -> {
                if (n == 2) out.addAll(List.of("status", "start", "stop"));
                if (n == 3 && args[1].equalsIgnoreCase("start")) out.addAll(plugin.getEvents().ids());
            }
            case "zone" -> {
                if (n == 2) out.addAll(List.of("list", "pos1", "pos2", "create", "delete", "flag", "info"));
                if (n == 3 && List.of("delete", "flag", "info").contains(args[1].toLowerCase(Locale.ROOT))) plugin.getZones().all().forEach(z -> out.add(z.id));
                if (n == 4 && args[1].equalsIgnoreCase("flag")) out.addAll(List.of("pvp", "abilities", "xp", "mob_level", "mob_range", "stations"));
                if (n == 5 && args[1].equalsIgnoreCase("flag")) out.addAll(List.of("true", "false", "remove"));
            }
            case "coins" -> {
                if (n == 2) out.addAll(List.of("give", "take", "set"));
                if (n == 3) Bukkit.getOnlinePlayers().forEach(p -> out.add(p.getName()));
            }
            case "shop" -> {
                if (n == 2) out.addAll(List.of("refill", "stock", "reload"));
                if (n == 3 && a0(args).equals("stock")) plugin.getShop().all().forEach(e -> out.add(e.key));
            }
            case "jobability" -> {
                if (n == 2) out.addAll(List.of("list", "give", "remove"));
                if (n == 3) Bukkit.getOnlinePlayers().forEach(p -> out.add(p.getName()));
                if (n == 4) { out.add("random"); plugin.getJobAbilities().all().forEach(d -> out.add(d.id())); }
            }
            case "userinfo" -> {
                if (n == 2) { Bukkit.getOnlinePlayers().forEach(p -> out.add(p.getName())); out.addAll(UserInfo.SECTIONS); }
                if (n == 3) out.addAll(UserInfo.SECTIONS);
            }
            case "npc" -> {
                if (n == 2) out.addAll(List.of("create", "remove"));
                if (n == 3) plugin.getQuestExtras().chains().forEach(c -> out.add(c.id()));
            }
            case "debug" -> { if (n == 2) Bukkit.getOnlinePlayers().forEach(p -> out.add(p.getName())); }
            default -> { }
        }
        String t = args[n - 1].toLowerCase(Locale.ROOT);
        out.removeIf(x -> !x.toLowerCase(Locale.ROOT).startsWith(t));
        return out;
    }
}
