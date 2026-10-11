package com.arclinker.rpglevelitems.social;

import com.arclinker.rpglevelitems.RPGLevelTier;
import com.arclinker.rpglevelitems.rank.PlayerRecord;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.*;

/**
 * Parties: shared combat XP for members nearby (+bonus for different classes), no friendly fire,
 * party chat. Config: {@code party.*}.
 */
public final class PartyManager implements Listener {
    public static final class Party {
        public UUID leader;
        public final Set<UUID> members = new LinkedHashSet<>();
    }

    private final RPGLevelTier plugin;
    private final Map<UUID, Party> byPlayer = new HashMap<>();
    private final Map<UUID, UUID> invites = new HashMap<>(); // invited -> inviter

    public PartyManager(RPGLevelTier plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public Party of(Player p) {
        return byPlayer.get(p.getUniqueId());
    }

    public boolean sameParty(Player a, Player b) {
        Party pa = byPlayer.get(a.getUniqueId());
        return pa != null && pa == byPlayer.get(b.getUniqueId());
    }

    public int maxSize() {
        return plugin.getConfig().getInt("party.max_size", 5);
    }

    public String create(Player p) {
        if (of(p) != null) return "Ya estás en una party.";
        Party party = new Party();
        party.leader = p.getUniqueId();
        party.members.add(p.getUniqueId());
        byPlayer.put(p.getUniqueId(), party);
        return null;
    }

    public String invite(Player leader, Player target) {
        Party party = of(leader);
        if (party == null) return "No tienes party. Usa /party create.";
        if (!party.leader.equals(leader.getUniqueId())) return "Solo el líder puede invitar.";
        if (target == leader) return "No puedes invitarte.";
        if (of(target) != null) return target.getName() + " ya está en una party.";
        if (party.members.size() >= maxSize()) return "La party está llena (" + maxSize() + ").";
        invites.put(target.getUniqueId(), leader.getUniqueId());
        target.sendMessage("§a" + leader.getName() + " te invitó a su party. §e/party accept §apara unirte.");
        Bukkit.getScheduler().runTaskLater(plugin, () -> invites.remove(target.getUniqueId(), leader.getUniqueId()), 20L * 60);
        return null;
    }

    public String accept(Player p) {
        UUID from = invites.remove(p.getUniqueId());
        if (from == null) return "No tienes invitaciones.";
        Party party = byPlayer.get(from);
        if (party == null) return "Esa party ya no existe.";
        if (party.members.size() >= maxSize()) return "La party está llena.";
        if (of(p) != null) return "Ya estás en una party.";
        broadcast(party, "§a" + p.getName() + " se unió a la party.");
        party.members.add(p.getUniqueId());
        byPlayer.put(p.getUniqueId(), party);
        return null;
    }

    public void leave(Player p) {
        Party party = byPlayer.remove(p.getUniqueId());
        if (party == null) return;
        party.members.remove(p.getUniqueId());
        if (party.members.size() <= 1) {
            broadcast(party, "§eLa party se disolvió.");
            for (UUID id : party.members) byPlayer.remove(id);
            party.members.clear();
            return;
        }
        if (party.leader.equals(p.getUniqueId())) {
            party.leader = party.members.iterator().next();
            Player nl = Bukkit.getPlayer(party.leader);
            broadcast(party, "§e" + (nl == null ? "Otro jugador" : nl.getName()) + " es el nuevo líder.");
        }
        broadcast(party, "§e" + p.getName() + " salió de la party.");
    }

    public String kick(Player leader, String name) {
        Party party = of(leader);
        if (party == null || !party.leader.equals(leader.getUniqueId())) return "Solo el líder puede expulsar.";
        Player t = Bukkit.getPlayerExact(name);
        if (t == null || !party.members.contains(t.getUniqueId()) || t == leader) return "Jugador no encontrado en tu party.";
        leave(t);
        t.sendMessage("§cFuiste expulsado de la party.");
        return null;
    }

    public void broadcast(Party party, String msg) {
        for (UUID id : party.members) {
            Player m = Bukkit.getPlayer(id);
            if (m != null) m.sendMessage(msg);
        }
    }

    public void chat(Player p, String msg) {
        Party party = of(p);
        if (party == null) {
            p.sendMessage("§cNo estás en una party.");
            return;
        }
        broadcast(party, "§b[Party] §f" + p.getName() + "§7: §f" + msg);
    }

    /** Online members within range of the player (including the player). */
    public List<Player> nearby(Player p) {
        List<Player> out = new ArrayList<>();
        Party party = of(p);
        if (party == null) {
            out.add(p);
            return out;
        }
        double range = plugin.getConfig().getDouble("party.share_range", 40);
        for (UUID id : party.members) {
            Player m = Bukkit.getPlayer(id);
            if (m != null && m.getWorld().equals(p.getWorld()) && m.getLocation().distanceSquared(p.getLocation()) <= range * range) out.add(m);
        }
        return out;
    }

    /** XP multiplier for the killer: +X% per different class among nearby members (max 4 extra). */
    public double bonusMultiplier(Player killer) {
        List<Player> near = nearby(killer);
        if (near.size() <= 1) return 1.0;
        Set<String> classes = new HashSet<>();
        for (Player m : near) {
            PlayerRecord r = plugin.getPlayerStore().get(m);
            if (!r.classId.isEmpty()) classes.add(r.classId);
        }
        double per = plugin.getConfig().getDouble("party.class_bonus", 0.05);
        return 1.0 + per * Math.min(4, Math.max(0, classes.size() - 1));
    }

    /** Shares class XP of a kill with nearby members. */
    public void share(Player killer, long xp) {
        Party party = of(killer);
        if (party == null || xp <= 0) return;
        long shared = Math.round(xp * plugin.getConfig().getDouble("party.xp_share", 0.5));
        if (shared <= 0) return;
        for (Player m : nearby(killer)) {
            if (m == killer) continue;
            plugin.getClassLevelManager().addClassXp(m, shared);
            plugin.getClassLevelManager().addSubClassXp(m, shared);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        leave(e.getPlayer());
        invites.remove(e.getPlayer().getUniqueId());
    }
}
