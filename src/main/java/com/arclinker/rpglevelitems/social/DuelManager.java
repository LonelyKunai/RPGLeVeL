package com.arclinker.rpglevelitems.social;

import com.arclinker.rpglevelitems.RPGLevelTier;
import com.arclinker.rpglevelitems.rank.RankCategory;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.*;

/** Duels: consented PvP with optional bet, PvP ranking points (RankCategory.PVP). Nobody dies in a duel. */
public final class DuelManager implements Listener {
    private static final class Duel {
        final UUID a, b;
        final double bet;
        boolean started;
        long endAt;

        Duel(UUID a, UUID b, double bet) {
            this.a = a;
            this.b = b;
            this.bet = bet;
        }
    }

    private record Request(UUID from, double bet) {}

    private final RPGLevelTier plugin;
    private final Map<UUID, Duel> duels = new HashMap<>();
    private final Map<UUID, Request> requests = new HashMap<>(); // target -> request

    public DuelManager(RPGLevelTier plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    /** Plugin shutdown: refund every bet and end all duels. */
    public void shutdown() {
        for (Duel d : new HashSet<>(duels.values())) finish(d, null, null);
        duels.clear();
    }

    public boolean inDuelWith(Player a, Player b) {
        Duel d = duels.get(a.getUniqueId());
        return d != null && d.started && (d.a.equals(b.getUniqueId()) || d.b.equals(b.getUniqueId()));
    }

    public boolean inDuel(Player p) {
        return duels.containsKey(p.getUniqueId());
    }

    public String request(Player from, Player to, double bet) {
        if (from == to) return "No puedes retarte.";
        if (inDuel(from) || inDuel(to)) return "Alguno ya está en un duelo.";
        if (bet > 0 && (!plugin.getEconomy().has(from, bet) || !plugin.getEconomy().has(to, bet))) return "Ambos necesitan " + plugin.getEconomy().format(bet) + " para la apuesta.";
        requests.put(to.getUniqueId(), new Request(from.getUniqueId(), bet));
        to.sendMessage("§c⚔ " + from.getName() + " te reta a un duelo" + (bet > 0 ? " (apuesta " + plugin.getEconomy().format(bet) + ")" : "") + ". §e/duel accept §7o §e/duel decline");
        Bukkit.getScheduler().runTaskLater(plugin, () -> requests.remove(to.getUniqueId()), 20L * 30);
        return null;
    }

    public String accept(Player to) {
        Request r = requests.remove(to.getUniqueId());
        Player from = r == null ? null : Bukkit.getPlayer(r.from());
        if (r == null || from == null) return "No tienes retos pendientes.";
        if (inDuel(from) || inDuel(to)) return "Alguno ya está en un duelo.";
        if (r.bet() > 0) {
            if (!plugin.getEconomy().has(from, r.bet()) || !plugin.getEconomy().has(to, r.bet())) return "Alguien ya no tiene la apuesta.";
            plugin.getEconomy().withdraw(from, r.bet());
            plugin.getEconomy().withdraw(to, r.bet());
        }
        Duel d = new Duel(from.getUniqueId(), to.getUniqueId(), r.bet());
        duels.put(from.getUniqueId(), d);
        duels.put(to.getUniqueId(), d);
        from.sendMessage("§c⚔ Duelo contra " + to.getName() + ": empieza en 5 segundos.");
        to.sendMessage("§c⚔ Duelo contra " + from.getName() + ": empieza en 5 segundos.");
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (duels.get(d.a) != d) return;
            d.started = true;
            d.endAt = System.currentTimeMillis() + plugin.getConfig().getInt("duel.max_seconds", 300) * 1000L;
            for (UUID id : List.of(d.a, d.b)) {
                Player p = Bukkit.getPlayer(id);
                if (p != null) p.sendTitle("§c¡PELEA!", "", 0, 25, 5);
            }
        }, 100L);
        return null;
    }

    public void decline(Player to) {
        Request r = requests.remove(to.getUniqueId());
        if (r != null) {
            Player from = Bukkit.getPlayer(r.from());
            if (from != null) from.sendMessage("§e" + to.getName() + " rechazó el duelo.");
        }
    }

    public void forfeit(Player loser) {
        Duel d = duels.get(loser.getUniqueId());
        if (d == null) return;
        UUID wid = d.a.equals(loser.getUniqueId()) ? d.b : d.a;
        finish(d, Bukkit.getPlayer(wid), loser);
    }

    private void finish(Duel d, Player winner, Player loser) {
        duels.remove(d.a);
        duels.remove(d.b);
        for (UUID id : List.of(d.a, d.b)) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                p.setHealth(p.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH).getValue());
                p.setFireTicks(0);
                plugin.getStatuses().clear(p);
            }
        }
        if (winner == null || loser == null) {
            if (d.bet > 0) {
                plugin.getEconomy().deposit(d.a, d.bet);
                plugin.getEconomy().deposit(d.b, d.bet);
            }
            return;
        }
        Bukkit.broadcastMessage("§c⚔ §f" + winner.getName() + " §7venció a §f" + loser.getName() + " §7en un duelo.");
        if (d.bet > 0) {
            double tax = plugin.getConfig().getDouble("duel.bet_tax", 0.05);
            plugin.getEconomy().deposit(winner, Math.floor(d.bet * 2 * (1 - tax)));
        }
        plugin.getRankManager().addPoints(winner, RankCategory.PVP, plugin.getConfig().getInt("duel.points_win", 25));
        if (plugin.getAchievements() != null) plugin.getAchievements().add(winner, "pvp_wins", 1);
    }

    private void tick() {
        long now = System.currentTimeMillis();
        for (Duel d : new HashSet<>(duels.values())) {
            Player a = Bukkit.getPlayer(d.a), b = Bukkit.getPlayer(d.b);
            if (a == null || b == null) { finish(d, a != null ? a : b, a != null ? b : a); continue; }
            if (!d.started) continue;
            if (!a.getWorld().equals(b.getWorld()) || a.getLocation().distanceSquared(b.getLocation()) > 50 * 50) {
                a.sendMessage("§cDuelo cancelado: se alejaron demasiado.");
                b.sendMessage("§cDuelo cancelado: se alejaron demasiado.");
                finish(d, null, null);
            } else if (now > d.endAt) {
                a.sendMessage("§eDuelo terminado por tiempo: empate.");
                b.sendMessage("§eDuelo terminado por tiempo: empate.");
                finish(d, null, null);
            }
        }
    }

    /** Lethal damage during a duel ends it instead of killing. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        Duel d = duels.get(p.getUniqueId());
        if (d == null) return;
        if (!d.started) { e.setCancelled(true); return; }
        if (p.getHealth() - e.getFinalDamage() <= 0) {
            e.setCancelled(true);
            forfeit(p);
        }
    }

    @EventHandler
    public void onMove(PlayerMoveEvent e) {
        Duel d = duels.get(e.getPlayer().getUniqueId());
        if (d != null && !d.started && e.getFrom().distanceSquared(e.getTo()) > 0.0001) {
            e.setTo(e.getFrom().setDirection(e.getTo().getDirection()));
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        forfeit(e.getPlayer());
        requests.remove(e.getPlayer().getUniqueId());
    }
}
