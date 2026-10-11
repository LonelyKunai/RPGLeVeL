package com.arclinker.rpglevelitems.quests;

import com.arclinker.rpglevelitems.DataFiles;
import com.arclinker.rpglevelitems.RPGLevelTier;
import com.arclinker.rpglevelitems.rank.PlayerRecord;
import com.arclinker.rpglevelitems.rank.Rank;
import com.arclinker.rpglevelitems.rank.RankCategory;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;

import java.time.LocalDate;
import java.time.temporal.IsoFields;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Weekly quests, story quest chains with quest-giver NPCs and an optional sidebar tracker.
 * Data: {@code weekly:} and {@code chains:} in data/quests/*.yml. Progress comes from
 * {@link #progress} (called by the same hooks as the daily quests).
 */
public final class QuestExtras implements Listener {
    public record Weekly(String id, String name, String description, String type, String target, int amount, int rank, int points, int coins) {}

    public record Step(String text, String type, String target, int amount, ConfigurationSection reward) {}

    public record Chain(String id, String name, String npc, int minLevel, String intro, List<Step> steps, ConfigurationSection finalReward) {}

    private final RPGLevelTier plugin;
    private final Map<String, Weekly> weekly = new LinkedHashMap<>();
    private final Map<Integer, List<String>> weeklyByRank = new HashMap<>();
    private final Map<String, Chain> chains = new LinkedHashMap<>();
    private final NamespacedKey npcKey;
    private final Map<UUID, Scoreboard> trackers = new HashMap<>();

    public QuestExtras(RPGLevelTier plugin) {
        this.plugin = plugin;
        this.npcKey = new NamespacedKey(RPGLevelTier.KEY_NS, "rlv_npc");
        reload();
        Bukkit.getPluginManager().registerEvents(this, plugin);
        Bukkit.getScheduler().runTaskTimer(plugin, this::refreshTrackers, 100L, 100L);
    }

    public void reload() {
        weekly.clear();
        weeklyByRank.clear();
        chains.clear();
        YamlConfiguration cfg = DataFiles.loadMerged(plugin, "quests");
        ConfigurationSection w = cfg.getConfigurationSection("weekly");
        if (w != null) {
            for (String id : w.getKeys(false)) {
                ConfigurationSection s = w.getConfigurationSection(id);
                if (s == null) continue;
                int rank = Math.max(0, Math.min(Rank.values().length - 1, s.getInt("rank_index", 0)));
                weekly.put(id, new Weekly(id, s.getString("name", id), s.getString("description", ""), s.getString("type", "kill").toLowerCase(Locale.ROOT),
                        s.getString("target", "ANY"), Math.max(1, s.getInt("amount", 1)), rank, s.getInt("reward_points", 50), s.getInt("reward_coins", 0)));
                weeklyByRank.computeIfAbsent(rank, k -> new ArrayList<>()).add(id);
            }
        }
        ConfigurationSection c = cfg.getConfigurationSection("chains");
        if (c != null) {
            for (String id : c.getKeys(false)) {
                ConfigurationSection s = c.getConfigurationSection(id);
                if (s == null) continue;
                List<Step> steps = new ArrayList<>();
                for (Map<?, ?> m : s.getMapList("steps")) {
                    YamlConfiguration tmp = new YamlConfiguration();
                    Object rw = m.get("reward");
                    if (rw instanceof Map<?, ?> rm) for (Map.Entry<?, ?> e : rm.entrySet()) tmp.set(String.valueOf(e.getKey()), e.getValue());
                    steps.add(new Step(String.valueOf(m.get("text")), String.valueOf(m.get("type")).toLowerCase(Locale.ROOT), String.valueOf(m.get("target")),
                            m.get("amount") instanceof Number n ? Math.max(1, n.intValue()) : 1, tmp));
                }
                chains.put(id, new Chain(id, s.getString("name", id), s.getString("npc", "Aldeano"), s.getInt("min_class_level", 1), s.getString("intro", ""), steps,
                        s.getConfigurationSection("final_reward")));
            }
        }
        plugin.getLogger().info("[QuestExtras] Loaded " + weekly.size() + " weekly quests and " + chains.size() + " quest chains.");
    }

    public Collection<Chain> chains() {
        return chains.values();
    }

    public Chain chain(String id) {
        return chains.get(id);
    }

    public Weekly weeklyDef(String id) {
        return weekly.get(id);
    }

    // ---- weekly -----------------------------------------------------------------------

    private static String weekId() {
        LocalDate d = LocalDate.now();
        return d.get(IsoFields.WEEK_BASED_YEAR) + "-W" + d.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
    }

    public List<String> weeklyQuests(Player p) {
        PlayerRecord r = plugin.getPlayerStore().get(p);
        String wk = weekId();
        if (!wk.equals(r.weeklyId) || r.weeklyIds.isEmpty()) {
            r.weeklyId = wk;
            r.weeklyIds.clear();
            r.weeklyProgress.clear();
            r.weeklyDone.clear();
            int rank = plugin.getRankManager().getRank(p.getUniqueId(), RankCategory.QUEST).ordinal();
            List<String> pool = new ArrayList<>(weeklyByRank.getOrDefault(rank, List.of()));
            if (pool.isEmpty()) pool = new ArrayList<>(weekly.keySet());
            Collections.shuffle(pool);
            for (int i = 0; i < Math.min(3, pool.size()); i++) r.weeklyIds.add(pool.get(i));
            plugin.getPlayerStore().markDirty();
        }
        return new ArrayList<>(r.weeklyIds);
    }

    // ---- chains -----------------------------------------------------------------------

    public boolean started(Player p, String id) {
        return plugin.getPlayerStore().get(p).chainStep.containsKey(id);
    }

    public boolean finished(Player p, String id) {
        return plugin.getPlayerStore().get(p).chainDone.contains(id);
    }

    public String start(Player p, String id) {
        Chain c = chains.get(id);
        if (c == null) return "Cadena desconocida.";
        PlayerRecord r = plugin.getPlayerStore().get(p);
        if (r.chainDone.contains(id)) return "Ya completaste esta cadena.";
        if (r.chainStep.containsKey(id)) return "Ya la tienes en curso.";
        if (plugin.getClassLevelManager().getClassLevel(p) < c.minLevel()) return "Necesitas nivel de clase " + c.minLevel() + ".";
        r.chainStep.put(id, 0);
        r.chainProgress.put(id, 0);
        plugin.getPlayerStore().markDirty();
        p.sendMessage("§6§l» Nueva misión en cadena: §e" + c.name());
        if (!c.intro().isEmpty()) p.sendMessage("§7" + c.intro());
        showStep(p, c, 0);
        return null;
    }

    public void abandon(Player p, String id) {
        PlayerRecord r = plugin.getPlayerStore().get(p);
        r.chainStep.remove(id);
        r.chainProgress.remove(id);
        plugin.getPlayerStore().markDirty();
    }

    private void showStep(Player p, Chain c, int idx) {
        Step s = c.steps().get(idx);
        p.sendMessage("§e[" + (idx + 1) + "/" + c.steps().size() + "] §f" + c.npc() + "§7: " + s.text());
        p.sendMessage("§7Objetivo: §f" + describe(s));
    }

    private static String describe(Step s) {
        return switch (s.type()) {
            case "kill" -> "eliminar " + s.amount() + " " + s.target();
            case "gather" -> "conseguir " + s.amount() + " " + s.target();
            case "fish" -> "pescar " + s.amount();
            case "forge" -> "forjar " + s.amount() + " piezas";
            case "boss" -> "derrotar " + s.amount() + " jefe(s)";
            case "talk" -> "hablar con el NPC";
            default -> s.type();
        };
    }

    public List<String> chainLines(Player p, String id) {
        Chain c = chains.get(id);
        List<String> out = new ArrayList<>();
        if (c == null) return out;
        PlayerRecord r = plugin.getPlayerStore().get(p);
        if (r.chainDone.contains(id)) {
            out.add("§a✔ " + c.name() + " §7(completada)");
            return out;
        }
        Integer idx = r.chainStep.get(id);
        if (idx == null) {
            out.add("§7• " + c.name() + " §8(nivel de clase " + c.minLevel() + ", habla con " + c.npc() + " o /quest chain start " + id + ")");
            return out;
        }
        Step s = c.steps().get(Math.min(idx, c.steps().size() - 1));
        out.add("§e▶ " + c.name() + " §7paso " + (idx + 1) + "/" + c.steps().size() + ": §f" + describe(s) + " §7(" + r.chainProgress.getOrDefault(id, 0) + "/" + s.amount() + ")");
        return out;
    }

    // ---- progress hook -----------------------------------------------------------------

    private static boolean matches(String def, String target) {
        return def.equalsIgnoreCase("ANY") || def.equalsIgnoreCase(target);
    }

    /** Called with every kill / gather / fish / forge / boss event of a player. */
    public void progress(Player p, String type, String target, int amount) {
        PlayerRecord r = plugin.getPlayerStore().get(p);
        if (!weekly.isEmpty()) {
            for (String id : weeklyQuests(p)) {
                Weekly w = weekly.get(id);
                if (w == null || r.weeklyDone.contains(id) || !w.type().equals(type) || !matches(w.target(), target)) continue;
                int now = Math.min(w.amount(), r.weeklyProgress.merge(id, amount, Integer::sum));
                r.weeklyProgress.put(id, now);
                plugin.getPlayerStore().markDirty();
                if (now >= w.amount()) {
                    r.weeklyDone.add(id);
                    p.sendMessage("§d✔ Misión semanal completada: §f" + w.name() + " §7(+" + w.points() + " pts, +" + w.coins() + " monedas)");
                    plugin.getRankManager().addPoints(p, RankCategory.QUEST, w.points());
                    if (w.coins() > 0) plugin.getEconomy().deposit(p, w.coins());
                    long xp = Math.round(w.points() * plugin.getConfig().getDouble("class_levels.xp.quest_share", 0.5));
                    plugin.getClassLevelManager().addClassXp(p, xp);
                    plugin.getClassLevelManager().addSubClassXp(p, xp);
                    plugin.getAchievements().add(p, "quests_done", 1);
                    plugin.getJobAbilities().questReward(p, "weekly");
                }
            }
        }
        for (Chain c : chains.values()) {
            Integer idx = r.chainStep.get(c.id());
            if (idx == null || idx >= c.steps().size()) continue;
            Step s = c.steps().get(idx);
            if (!s.type().equals(type) || !matches(s.target(), target)) continue;
            int now = r.chainProgress.merge(c.id(), amount, Integer::sum);
            plugin.getPlayerStore().markDirty();
            if (now >= s.amount()) advance(p, c, idx);
        }
    }

    private void advance(Player p, Chain c, int idx) {
        PlayerRecord r = plugin.getPlayerStore().get(p);
        Step s = c.steps().get(idx);
        pay(p, s.reward(), false);
        p.sendMessage("§a✔ Paso completado §7(" + c.name() + ")");
        if (idx + 1 >= c.steps().size()) {
            r.chainStep.remove(c.id());
            r.chainProgress.remove(c.id());
            r.chainDone.add(c.id());
            p.sendMessage("§6§l★ ¡Misión en cadena completada: " + c.name() + "!");
            pay(p, c.finalReward(), true);
            plugin.getAchievements().add(p, "quests_done", 3);
            plugin.getJobAbilities().questReward(p, "chain");
        } else {
            r.chainStep.put(c.id(), idx + 1);
            r.chainProgress.put(c.id(), 0);
            showStep(p, c, idx + 1);
        }
        plugin.getPlayerStore().markDirty();
    }

    private void pay(Player p, ConfigurationSection rw, boolean finale) {
        if (rw == null) return;
        if (rw.getDouble("coins") > 0) plugin.getEconomy().deposit(p, rw.getDouble("coins"));
        long xp = rw.getLong("class_xp");
        if (xp > 0) {
            plugin.getClassLevelManager().addClassXp(p, xp);
            plugin.getClassLevelManager().addSubClassXp(p, xp);
        }
        int pts = rw.getInt("ranking_points");
        if (pts > 0) plugin.getRankManager().addPoints(p, RankCategory.QUEST, pts);
        if (!finale) return;
        List<ItemStack> items = new ArrayList<>();
        ConfigurationSection g = rw.getConfigurationSection("gear");
        if (g != null) {
            List<Integer> t = g.getIntegerList("tier");
            int lo = t.size() > 0 ? t.get(0) : 2, hi = t.size() > 1 ? t.get(1) : lo;
            items.add(plugin.getLoot().gear(p, ThreadLocalRandom.current().nextInt(lo, hi + 1), true));
        }
        ConfigurationSection gm = rw.getConfigurationSection("gem");
        if (gm != null) {
            List<Integer> t = gm.getIntegerList("level");
            int lv = t.isEmpty() ? 1 : t.get(0);
            items.add(plugin.getGemManager().randomItem(lv));
        }
        for (ItemStack it : items) {
            if (it == null) continue;
            for (ItemStack rest : p.getInventory().addItem(it).values()) p.getWorld().dropItemNaturally(p.getLocation(), rest);
        }
    }

    // ---- NPCs ---------------------------------------------------------------------------

    public Villager spawnNpc(Location at, String chainId) {
        Chain c = chains.get(chainId);
        if (c == null) return null;
        Villager v = (Villager) at.getWorld().spawnEntity(at, EntityType.VILLAGER);
        v.setAI(false);
        v.setInvulnerable(true);
        v.setSilent(true);
        v.setPersistent(true);
        v.setRemoveWhenFarAway(false);
        v.setCustomName("§6" + c.npc() + " §8[Misión]");
        v.setCustomNameVisible(true);
        v.getPersistentDataContainer().set(npcKey, PersistentDataType.STRING, chainId);
        return v;
    }

    public int removeNpcs(String chainId) {
        int n = 0;
        for (var w : Bukkit.getWorlds()) {
            for (Entity e : w.getEntities()) {
                String id = e.getPersistentDataContainer().get(npcKey, PersistentDataType.STRING);
                if (id != null && (chainId == null || id.equals(chainId))) {
                    e.remove();
                    n++;
                }
            }
        }
        return n;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEntityEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        String id = e.getRightClicked().getPersistentDataContainer().get(npcKey, PersistentDataType.STRING);
        if (id == null) return;
        e.setCancelled(true);
        Player p = e.getPlayer();
        Chain c = chains.get(id);
        if (c == null) return;
        PlayerRecord r = plugin.getPlayerStore().get(p);
        if (r.chainDone.contains(id)) {
            p.sendMessage("§6" + c.npc() + "§7: «Gracias por todo, héroe.»");
            return;
        }
        Integer idx = r.chainStep.get(id);
        if (idx == null) {
            String err = start(p, id);
            if (err != null) p.sendMessage("§6" + c.npc() + "§7: «" + err + "»");
            return;
        }
        Step s = c.steps().get(idx);
        if (s.type().equals("talk")) {
            progress(p, "talk", "self", 1);
            return;
        }
        showStep(p, c, idx);
        p.sendMessage("§7Progreso: §f" + r.chainProgress.getOrDefault(id, 0) + "/" + s.amount());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onNpcDamage(EntityDamageEvent e) {
        if (e.getEntity().getPersistentDataContainer().has(npcKey, PersistentDataType.STRING)) e.setCancelled(true);
    }

    // ---- sidebar tracker -----------------------------------------------------------------

    public boolean toggleTracker(Player p) {
        if (trackers.remove(p.getUniqueId()) != null) {
            p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
            return false;
        }
        trackers.put(p.getUniqueId(), Bukkit.getScoreboardManager().getNewScoreboard());
        refresh(p);
        return true;
    }

    private void refreshTrackers() {
        for (UUID id : new ArrayList<>(trackers.keySet())) {
            Player p = Bukkit.getPlayer(id);
            if (p == null) trackers.remove(id); else refresh(p);
        }
    }

    public void refresh(Player p) {
        Scoreboard sb = trackers.get(p.getUniqueId());
        if (sb == null) return;
        Objective old = sb.getObjective("rlv_quests");
        if (old != null) old.unregister();
        Objective o = sb.registerNewObjective("rlv_quests", "dummy", "§6§lMisiones");
        o.setDisplaySlot(DisplaySlot.SIDEBAR);
        List<String> lines = new ArrayList<>();
        var dq = plugin.getDailyQuestManager();
        for (String id : dq.getTodaysQuests(p)) {
            var d = dq.getDefinition(id);
            if (d == null || dq.isDone(p, id)) continue;
            lines.add("§f" + shorten(d.name) + " §7" + dq.getProgress(p, id) + "/" + d.amount);
        }
        PlayerRecord r = plugin.getPlayerStore().get(p);
        for (String id : weeklyQuests(p)) {
            Weekly w = weekly.get(id);
            if (w == null || r.weeklyDone.contains(id)) continue;
            lines.add("§dS: " + shorten(w.name()) + " §7" + r.weeklyProgress.getOrDefault(id, 0) + "/" + w.amount());
        }
        for (Chain c : chains.values()) {
            Integer idx = r.chainStep.get(c.id());
            if (idx == null || idx >= c.steps().size()) continue;
            Step s = c.steps().get(idx);
            lines.add("§e" + shorten(c.name()) + " §7" + r.chainProgress.getOrDefault(c.id(), 0) + "/" + s.amount());
        }
        if (lines.isEmpty()) lines.add("§7Sin misiones activas");
        int score = lines.size();
        Set<String> used = new HashSet<>();
        for (String l : lines.subList(0, Math.min(14, lines.size()))) {
            String entry = l;
            while (!used.add(entry)) entry += "§r";
            o.getScore(entry.length() > 40 ? entry.substring(0, 40) : entry).setScore(score--);
        }
        p.setScoreboard(sb);
    }

    private static String shorten(String s) {
        String t = ChatColor.stripColor(s).replaceFirst("^\\[[^\\]]*\\]\\s*", "");
        return t.length() > 22 ? t.substring(0, 21) + "…" : t;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        trackers.remove(e.getPlayer().getUniqueId());
    }
}
