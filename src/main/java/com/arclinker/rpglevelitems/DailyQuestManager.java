package com.arclinker.rpglevelitems;

import com.arclinker.rpglevelitems.rank.PlayerRecord;
import com.arclinker.rpglevelitems.rank.Rank;
import com.arclinker.rpglevelitems.rank.RankCategory;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.time.LocalDate;
import java.util.*;

/**
 * Daily quests. Definitions live in {@code data/quests.yml}; every quest has a rank
 * (rank_index 0=F ... 8=U). Each day a player gets {@value #DAILY_COUNT} quests chosen
 * from the pool of their QUEST rank (and one a rank below / above). Progress is tracked
 * by ActivityListener; completing a quest grants QUEST ranking points (reward_xp).
 */
public class DailyQuestManager {
    public static final int DAILY_COUNT = 5;

    private final RPGLevelTier plugin;
    private final Map<String, QuestDefinition> questDefs = new LinkedHashMap<>();
    private final Map<Integer, List<String>> byRank = new HashMap<>();

    public DailyQuestManager(RPGLevelTier plugin) {
        this.plugin = plugin;
        loadQuestDefinitions();
    }

    private void loadQuestDefinitions() {
        YamlConfiguration cfg = DataFiles.loadMerged(plugin, "quests");
        ConfigurationSection root = cfg.getConfigurationSection("quests");
        if (root == null) {
            plugin.getLogger().warning("[DailyQuestManager] No quests found in data/quests/.");
            return;
        }
        int maxRank = Rank.values().length - 1;
        for (String id : root.getKeys(false)) {
            ConfigurationSection s = root.getConfigurationSection(id);
            if (s == null) continue;
            int rank = Math.max(0, Math.min(maxRank, s.getInt("rank_index", 0)));
            QuestDefinition def = new QuestDefinition(id, s.getString("name", id), s.getString("description", ""),
                    s.getString("type", "kill").toLowerCase(Locale.ROOT), s.getString("target", ""),
                    Math.max(1, s.getInt("amount", 1)), rank, Math.max(1, s.getInt("reward_xp", 10)));
            questDefs.put(id, def);
            byRank.computeIfAbsent(rank, k -> new ArrayList<>()).add(id);
        }
        plugin.getLogger().info("[DailyQuestManager] Loaded " + questDefs.size() + " quests.");
    }

    /** Makes sure the player has today's quests (resets progress on a new day). */
    public void assignDailyQuests(Player player) {
        PlayerRecord r = plugin.getPlayerStore().get(player);
        String today = LocalDate.now().toString();
        if (today.equals(r.questDate) && !r.questIds.isEmpty()) return;
        r.questDate = today;
        r.questIds.clear();
        r.questIds.addAll(selectQuestsForPlayer(player));
        r.questProgress.clear();
        r.questDone.clear();
        plugin.getPlayerStore().markDirty();
    }

    /** Today's quest ids for the player (assigned on first call of the day). */
    public List<String> getTodaysQuests(Player player) {
        assignDailyQuests(player);
        return new ArrayList<>(plugin.getPlayerStore().get(player).questIds);
    }

    private List<String> selectQuestsForPlayer(Player player) {
        int rank = plugin.getRankManager().getRank(player.getUniqueId(), RankCategory.QUEST).ordinal();
        int max = Rank.values().length - 1;
        // 3 at own rank, 1 easier, 1 harder (falling back to own rank)
        int[] wanted = {rank, rank, rank, Math.max(0, rank - 1), Math.min(max, rank + 1)};
        Set<String> picked = new LinkedHashSet<>();
        for (int want : wanted) {
            List<String> pool = new ArrayList<>(byRank.getOrDefault(want, List.of()));
            pool.removeAll(picked);
            if (pool.isEmpty()) pool = new ArrayList<>(byRank.getOrDefault(rank, List.of()));
            pool.removeAll(picked);
            if (pool.isEmpty()) continue;
            picked.add(pool.get(new Random().nextInt(pool.size())));
        }
        if (picked.size() < DAILY_COUNT) {
            List<String> rest = new ArrayList<>(questDefs.keySet());
            rest.removeAll(picked);
            Collections.shuffle(rest);
            for (String id : rest) {
                if (picked.size() >= DAILY_COUNT) break;
                picked.add(id);
            }
        }
        return new ArrayList<>(picked);
    }

    public int getProgress(Player player, String questId) {
        return plugin.getPlayerStore().get(player).questProgress.getOrDefault(questId, 0);
    }

    public boolean isDone(Player player, String questId) {
        return plugin.getPlayerStore().get(player).questDone.contains(questId);
    }

    /**
     * Registers progress for matching quests of today (type kill/gather/fish and target name).
     * Completing a quest grants QUEST ranking points.
     */
    public void addProgress(Player player, String type, String target, int amount) {
        if (plugin.getQuestExtras() != null) plugin.getQuestExtras().progress(player, type, target, amount);
        if (questDefs.isEmpty()) return;
        assignDailyQuests(player);
        PlayerRecord r = plugin.getPlayerStore().get(player);
        for (String id : r.questIds) {
            QuestDefinition def = questDefs.get(id);
            if (def == null || r.questDone.contains(id)) continue;
            if (!def.type.equals(type) || !def.target.equalsIgnoreCase(target)) continue;
            int now = Math.min(def.amount, r.questProgress.merge(id, amount, Integer::sum));
            r.questProgress.put(id, now);
            plugin.getPlayerStore().markDirty();
            if (now >= def.amount) {
                r.questDone.add(id);
                player.sendMessage(plugin.getLanguageManager().tr(player, "quest.done", def.name, def.rewardXp));
                plugin.getRankManager().addPoints(player, RankCategory.QUEST, def.rewardXp);
                long coins = Math.round(def.rewardXp * plugin.getConfig().getDouble("quests.coins_per_point", 0.4));
                if (coins > 0) {
                    plugin.getEconomy().deposit(player, coins);
                    player.sendMessage(plugin.getLanguageManager().tr(player, "quest.coins", coins));
                }
                plugin.getAchievements().add(player, "quests_done", 1);
                plugin.getJobAbilities().questReward(player, "daily");
                plugin.getGuilds().addXp(player, Math.round(def.rewardXp * plugin.getConfig().getDouble("guild.xp_share", 0.5)));
                long classXp = Math.round(def.rewardXp * plugin.getConfig().getDouble("class_levels.xp.quest_share", 0.5));
                plugin.getClassLevelManager().addClassXp(player, classXp);
                plugin.getClassLevelManager().addSubClassXp(player, classXp);
                if (r.questDone.containsAll(r.questIds)) {
                    player.sendMessage("§6¡Completaste todas las misiones de hoy!");
                }
            }
        }
    }

    public QuestDefinition getDefinition(String id) {
        return id == null ? null : questDefs.get(id);
    }

    public Map<String, QuestDefinition> getQuestDefs() {
        return Collections.unmodifiableMap(questDefs);
    }

    public static class QuestDefinition {
        public final String id;
        public final String name;
        public final String description;
        public final String type;   // kill, gather, fish
        public final String target; // entity type / block material / caught item
        public final int amount;
        public final int rankIndex; // 0..8 = F..U
        public final int rewardXp;

        public QuestDefinition(String id, String name, String description, String type, String target,
                               int amount, int rankIndex, int rewardXp) {
            this.id = id;
            this.name = name;
            this.description = description;
            this.type = type;
            this.target = target;
            this.amount = amount;
            this.rankIndex = rankIndex;
            this.rewardXp = rewardXp;
        }

        public Rank rank() {
            return Rank.values()[rankIndex];
        }
    }
}
