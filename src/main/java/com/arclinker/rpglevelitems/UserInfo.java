package com.arclinker.rpglevelitems;

import com.arclinker.rpglevelitems.ability.AbilityDef;
import com.arclinker.rpglevelitems.rank.PlayerRecord;
import com.arclinker.rpglevelitems.rank.RankCategory;
import com.arclinker.rpglevelitems.rank.RankScope;
import com.arclinker.rpglevelitems.stats.ItemData;
import com.arclinker.rpglevelitems.stats.Stats;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Admin inspector: /rlv userinfo [player] [section]. Shows everything the plugin knows about a player:
 * profile, class / subclass levels, stats, ability trees, jobs, quests, ranks, talents, achievements, gear, economy and social.
 * Offline players are supported for everything stored in their record (live stats / gear need the player online).
 */
public final class UserInfo {
    public static final List<String> SECTIONS = List.of("all", "profile", "class", "stats", "abilities", "jobs", "quests", "ranks", "talents", "achievements", "gear", "social");

    private final RPGLevelTier plugin;

    public UserInfo(RPGLevelTier plugin) {
        this.plugin = plugin;
    }

    public void show(CommandSender to, OfflinePlayer target, String section) {
        PlayerRecord r = plugin.getPlayerStore().get(target.getUniqueId());
        Player online = target.getPlayer();
        String sec = section == null ? "all" : section.toLowerCase(Locale.ROOT);
        if (!SECTIONS.contains(sec)) {
            to.sendMessage("§cSección inválida. Usa: " + String.join(", ", SECTIONS));
            return;
        }
        boolean all = sec.equals("all");
        to.sendMessage("§6§m          §r §e§l" + (r.name.isEmpty() ? String.valueOf(target.getName()) : r.name) + " §6§m          ");
        if (all || sec.equals("profile")) profile(to, target, r, online);
        if (all || sec.equals("class")) classes(to, r, online);
        if (all || sec.equals("stats")) stats(to, online);
        if (all || sec.equals("abilities")) abilities(to, r, online);
        if (all || sec.equals("jobs")) jobs(to, r);
        if (all || sec.equals("quests")) quests(to, r, online);
        if (all || sec.equals("ranks")) ranks(to, r, target);
        if (all || sec.equals("talents")) talents(to, r, online);
        if (all || sec.equals("achievements")) achievements(to, r);
        if (all || sec.equals("gear")) gear(to, online);
        if (all || sec.equals("social")) social(to, r, target, online);
    }

    private static String head(String s) {
        return "§6▌ " + s;
    }

    private void profile(CommandSender to, OfflinePlayer t, PlayerRecord r, Player on) {
        to.sendMessage(head("Perfil"));
        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd HH:mm");
        to.sendMessage(" §7UUID: §f" + t.getUniqueId() + " §8| " + (on != null ? "§aen línea" : "§cdesconectado"));
        if (t.getFirstPlayed() > 0) to.sendMessage(" §7Primera vez: §f" + f.format(new Date(t.getFirstPlayed())) + " §8| última: §f" + f.format(new Date(t.getLastPlayed())));
        if (on != null) {
            var l = on.getLocation();
            to.sendMessage(" §7Mundo: §f" + l.getWorld().getName() + " §7(" + l.getBlockX() + ", " + l.getBlockY() + ", " + l.getBlockZ() + ") §8| vida: §c"
                    + String.format(Locale.ROOT, "%.1f", on.getHealth()) + " §8| modo: §f" + on.getGameMode());
            var zones = plugin.getZones().at(l);
            if (!zones.isEmpty()) to.sendMessage(" §7Zonas: §f" + zones.stream().map(z -> z.id + z.flags).toList());
        }
        to.sendMessage(" §7Dinero: §e" + plugin.getEconomy().format(plugin.getEconomy().balance(t.getUniqueId())) + " §8| idioma: §f" + (r.language.isEmpty() ? "(servidor)" : r.language));
    }

    private void classes(CommandSender to, PlayerRecord r, Player on) {
        to.sendMessage(head("Clase y subclase"));
        var lm = plugin.getClassLevelManager();
        if (r.classId.isEmpty()) {
            to.sendMessage(" §7Sin clase.");
        } else {
            var ci = plugin.getClassManager().getClassInfo(r.classId);
            to.sendMessage(" §7Clase: §f" + (ci == null ? r.classId : ci.name) + " §7nivel §e" + lm.getClassLevel(r, r.classId) + "§7/" + lm.maxLevel()
                    + " §8(XP " + r.classXp.getOrDefault(r.classId, 0L) + ") §7prestigio: §6" + r.prestige.getOrDefault(r.classId, 0));
        }
        if (!r.subclassId.isEmpty()) {
            var si = plugin.getSubClassManager().getInfo(r.subclassId);
            to.sendMessage(" §7Subclase: §f" + (si == null ? r.subclassId : si.name) + " §7nivel §e" + lm.getSubClassLevel(r, r.subclassId)
                    + " §8(XP " + r.subClassXp.getOrDefault(r.subclassId, 0L) + ")");
        }
        if (r.classXp.size() > 1 || (r.classXp.size() == 1 && !r.classXp.containsKey(r.classId))) {
            StringBuilder sb = new StringBuilder(" §7Niveles de todas las clases: ");
            r.classXp.forEach((id, xp) -> sb.append("§f").append(id).append(" §e").append(lm.levelForXp(xp)).append("§7, "));
            to.sendMessage(sb.toString());
        }
        if (!r.subClassXp.isEmpty()) {
            StringBuilder sb = new StringBuilder(" §7Niveles de subclases: ");
            r.subClassXp.forEach((id, xp) -> sb.append("§f").append(id).append(" §e").append(lm.levelForXp(xp)).append("§7, "));
            to.sendMessage(sb.toString());
        }
        if (on != null) {
            lm.describe(on, false).forEach(l -> to.sendMessage(" " + l));
            if (!r.subclassId.isEmpty()) lm.describe(on, true).forEach(l -> to.sendMessage(" " + l));
        }
    }

    private void stats(CommandSender to, Player on) {
        to.sendMessage(head("Estadísticas totales"));
        if (on == null) {
            to.sendMessage(" §8(requiere jugador en línea)");
            return;
        }
        Map<String, Double> all = plugin.getStatEngine().all(on);
        if (all.isEmpty()) to.sendMessage(" §7Sin bonos.");
        all.forEach((k, v) -> to.sendMessage(" §9▸ §f" + Stats.line(k, v)));
        to.sendMessage(" §b✦ Maná: §f" + (int) plugin.getMana().get(on) + "/" + (int) plugin.getMana().max(on) + " §7regen §f" + String.format(Locale.ROOT, "%.2f", plugin.getMana().regen(on)) + "/s");
        plugin.getGearSetManager().describe(on).forEach(l -> to.sendMessage(" " + l));
    }

    private void abilities(CommandSender to, PlayerRecord r, Player on) {
        to.sendMessage(head("Árboles de habilidades"));
        var am = plugin.getAbilityManager();
        var lm = plugin.getClassLevelManager();
        Set<String> trees = new LinkedHashSet<>();
        if (!r.classId.isEmpty()) trees.add("class:" + r.classId);
        if (!r.subclassId.isEmpty()) trees.add("sub:" + r.subclassId);
        for (String id : r.classXp.keySet()) trees.add("class:" + id);
        for (String id : r.subClassXp.keySet()) trees.add("sub:" + id);
        for (AbilityDef d : am.all()) if (r.abilities.containsKey(d.id) && !d.treeKey().isEmpty()) trees.add(d.treeKey());
        if (trees.isEmpty()) to.sendMessage(" §7Sin árboles.");
        for (String tree : trees) {
            boolean sub = tree.startsWith("sub:");
            String id = tree.substring(sub ? 4 : 6);
            int lvl = sub ? lm.getSubClassLevel(r, id) : lm.getClassLevel(r, id);
            boolean active = tree.equals("class:" + r.classId) || tree.equals("sub:" + r.subclassId);
            int total = am.initialPointsFor(tree, lvl) + (plugin.getPrestigeManager().extraPointsFor(r, tree));
            int spent = 0, unlocked = 0;
            List<AbilityDef> defs = am.tree(tree);
            for (AbilityDef d : defs) if (r.abilities.containsKey(d.id)) { spent += d.cost; unlocked++; }
            to.sendMessage(" " + (active ? "§a▶ " : "§7• ") + "§f" + tree + " §7nivel §e" + lvl + " §8| §7puntos: §a" + (total - spent) + "§7 libres (" + spent + "/" + total + " gastados) §8| §dability XP: "
                    + r.abilityXp.getOrDefault(tree, 0L) + " §8| §7desbloqueadas: §f" + unlocked + "/" + defs.size());
            for (AbilityDef d : defs) {
                int lv = r.abilities.getOrDefault(d.id, 0);
                if (lv > 0) to.sendMessage("    §a✔ §f" + d.name + " §7nv §e" + lv + "§7/" + d.maxLevel + " §8(" + d.id + ")");
            }
        }
        StringBuilder hb = new StringBuilder(" §7Hotbar: ");
        for (int i = 0; i < r.hotbar.length; i++) {
            AbilityDef d = r.hotbar[i].isEmpty() ? null : am.get(r.hotbar[i]);
            hb.append("§8[").append(i + 1).append("] ").append(d == null ? "§8-" : "§f" + d.name).append("  ");
        }
        to.sendMessage(hb.toString());
    }

    private void jobs(CommandSender to, PlayerRecord r) {
        to.sendMessage(head("Trabajos"));
        var jm = plugin.getJobManager();
        to.sendMessage(" §7Ranuras: §f" + (r.job1.isEmpty() ? "-" : r.job1) + " §8/ §f" + (r.job2.isEmpty() ? "-" : r.job2) + " §8| §7trabajo de clase: §f" + (r.jobClass.isEmpty() ? "-" : r.jobClass));
        if (r.jobXp.isEmpty()) to.sendMessage(" §7Sin XP de trabajos.");
        r.jobXp.forEach((id, xp) -> to.sendMessage("  §f" + id + " §7nivel §e" + JobManager.levelForXp(xp) + " §8(" + xp + " XP)"));
        if (r.jobAbilities.isEmpty()) {
            to.sendMessage(" §7Habilidades de oficio: ninguna");
        } else {
            StringBuilder sb = new StringBuilder(" §7Habilidades de oficio: ");
            for (String id : r.jobAbilities) {
                var d = plugin.getJobAbilities().get(id);
                sb.append("§d").append(d == null ? id : d.name()).append("§7, ");
            }
            to.sendMessage(sb.toString());
        }
    }

    private void quests(CommandSender to, PlayerRecord r, Player on) {
        to.sendMessage(head("Misiones"));
        var dq = plugin.getDailyQuestManager();
        to.sendMessage(" §7Diarias (" + (r.questDate.isEmpty() ? "sin asignar" : r.questDate) + "):");
        for (String id : r.questIds) {
            var d = dq.getDefinition(id);
            if (d == null) continue;
            boolean done = r.questDone.contains(id);
            to.sendMessage("  " + (done ? "§a✔ " : "§7• ") + "§f" + d.name + " §7" + (done ? d.amount : r.questProgress.getOrDefault(id, 0)) + "/" + d.amount);
        }
        to.sendMessage(" §7Semanales (" + (r.weeklyId.isEmpty() ? "-" : r.weeklyId) + "):");
        for (String id : r.weeklyIds) {
            var w = plugin.getQuestExtras().weeklyDef(id);
            if (w == null) continue;
            boolean done = r.weeklyDone.contains(id);
            to.sendMessage("  " + (done ? "§a✔ " : "§7• ") + "§f" + w.name() + " §7" + (done ? w.amount() : r.weeklyProgress.getOrDefault(id, 0)) + "/" + w.amount());
        }
        to.sendMessage(" §7Cadenas: §f" + (r.chainStep.isEmpty() ? "ninguna activa" : r.chainStep + " progreso " + r.chainProgress) + " §8| completadas: §f" + r.chainDone);
    }

    private void ranks(CommandSender to, PlayerRecord r, OfflinePlayer t) {
        to.sendMessage(head("Rangos §8(temporada " + plugin.getRankManager().season() + ", termina en " + plugin.getRankManager().timeLeft() + ")"));
        var rm = plugin.getRankManager();
        for (RankCategory c : RankCategory.values()) {
            to.sendMessage(" " + c.coloredLabel() + "§7: " + rm.line(t.getUniqueId(), c) + " §8#" + rm.position(t.getUniqueId(), c, RankScope.PERMANENT) + "/#" + rm.position(t.getUniqueId(), c, RankScope.SEASON));
        }
        List<String> hist = rm.history(r);
        if (!hist.isEmpty()) {
            to.sendMessage(" §7Temporadas anteriores:");
            hist.stream().limit(10).forEach(h -> to.sendMessage("  " + h));
        }
        if (!r.pendingRewards.isEmpty()) to.sendMessage(" §eRecompensas de temporada pendientes: §f" + r.pendingRewards.size());
    }

    private void talents(CommandSender to, PlayerRecord r, Player on) {
        to.sendMessage(head("Talentos"));
        var tm = plugin.getTalents();
        if (on != null) to.sendMessage(" §7Puntos: total §f" + tm.totalPoints(on) + " §8| gastados §f" + tm.spentPoints(on) + " §8| libres §a" + tm.available(on));
        if (r.talents.isEmpty()) to.sendMessage(" §7Sin talentos.");
        r.talents.forEach((id, lv) -> {
            var t = tm.get(id);
            to.sendMessage("  §f" + (t == null ? id : t.name()) + " §7nv §e" + lv + (t == null ? "" : "§7/" + t.max()));
        });
    }

    private void achievements(CommandSender to, PlayerRecord r) {
        to.sendMessage(head("Logros y títulos"));
        var am = plugin.getAchievements();
        to.sendMessage(" §7Logros: §f" + r.achievements.size() + "/" + am.all().size() + " §8| títulos: §f" + r.titles.size() + " §8| activo: " + (am.activeTitleDisplay(r).isEmpty() ? "§8ninguno" : am.activeTitleDisplay(r)));
        StringBuilder sb = new StringBuilder(" §7Contadores: ");
        r.counters.forEach((k, v) -> sb.append("§f").append(k).append("§7=§e").append(v).append("§7, "));
        to.sendMessage(sb.toString());
    }

    private void describeItem(CommandSender to, String slot, ItemStack it) {
        if (it == null || it.getType().isAir()) return;
        String name = it.hasItemMeta() && it.getItemMeta().hasDisplayName() ? it.getItemMeta().getDisplayName() : it.getType().name();
        String id = plugin.getRlvpHandler() == null ? null : plugin.getRlvpHandler().getId(it);
        to.sendMessage("  §7" + slot + ": " + name + (id == null ? "" : " §8(" + id + ")"));
        if (it.hasItemMeta()) {
            ItemData.totalStats(it).forEach((k, v) -> to.sendMessage("    §9▸ " + Stats.line(k, v)));
            var gems = ItemData.gems(it.getItemMeta());
            int sockets = ItemData.sockets(it.getItemMeta());
            if (sockets > 0) to.sendMessage("    §d◈ Ranuras " + gems.size() + "/" + sockets + (gems.isEmpty() ? "" : " " + gems));
        }
    }

    private void gear(CommandSender to, Player on) {
        to.sendMessage(head("Equipo"));
        if (on == null) {
            to.sendMessage(" §8(requiere jugador en línea)");
            return;
        }
        var inv = on.getInventory();
        describeItem(to, "Mano", inv.getItemInMainHand());
        describeItem(to, "Secundaria", inv.getItemInOffHand());
        describeItem(to, "Casco", inv.getHelmet());
        describeItem(to, "Pechera", inv.getChestplate());
        describeItem(to, "Pantalones", inv.getLeggings());
        describeItem(to, "Botas", inv.getBoots());
    }

    private void social(CommandSender to, PlayerRecord r, OfflinePlayer t, Player on) {
        to.sendMessage(head("Social"));
        var g = plugin.getGuilds().of(t.getUniqueId());
        to.sendMessage(" §7Gremio: §f" + (g == null ? "ninguno" : "[" + g.tag + "] " + g.name + " §7(" + plugin.getGuilds().role(g, t.getUniqueId()) + ", nivel " + g.level + ")"));
        if (on != null) {
            var party = plugin.getParties().of(on);
            to.sendMessage(" §7Party: §f" + (party == null ? "ninguna" : party.members.size() + " miembros"));
            to.sendMessage(" §7En duelo: §f" + plugin.getDuels().inDuel(on) + " §8| maná: " + (int) plugin.getMana().get(on));
        }
        if (!r.dungeonCooldowns.isEmpty()) {
            StringBuilder sb = new StringBuilder(" §7Mazmorras en enfriamiento: ");
            long now = System.currentTimeMillis();
            r.dungeonCooldowns.forEach((id, until) -> { if (until > now) sb.append("§f").append(id).append(" §7(").append((until - now) / 60000).append("min) "); });
            to.sendMessage(sb.toString());
        }
        to.sendMessage(" §7Auction: §f" + (plugin.getAuction().hasClaims(t.getUniqueId()) ? "tiene cosas por recoger" : "nada pendiente"));
    }
}
