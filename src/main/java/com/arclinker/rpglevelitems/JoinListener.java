package com.arclinker.rpglevelitems;

import com.arclinker.rpglevelitems.rank.PlayerRecord;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.persistence.PersistentDataType;

/** Keeps the player record up to date and migrates old PDC class/subclass data. */
public class JoinListener implements Listener {
    private final RPGLevelTier plugin;

    public JoinListener(RPGLevelTier plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        PlayerRecord r = plugin.getPlayerStore().get(player);
        // Migration from the old storage (PersistentDataContainer of the player)
        if (r.classId.isEmpty()) {
            String old = player.getPersistentDataContainer().get(new NamespacedKey(RPGLevelTier.KEY_NS, "rlv_class"), PersistentDataType.STRING);
            if (old != null && plugin.getClassManager().getClassInfo(old) != null) {
                r.classId = plugin.getClassManager().getClassInfo(old).id;
                plugin.getJobManager().assignClassJob(player, r.classId);
            }
        }
        if (r.subclassId.isEmpty()) {
            String old = player.getPersistentDataContainer().get(new NamespacedKey(RPGLevelTier.KEY_NS, "rlv_subclass"), PersistentDataType.STRING);
            SubClassManager.SubClassInfo info = plugin.getSubClassManager().getInfo(old);
            if (info != null && info.parentClass.equalsIgnoreCase(r.classId)) r.subclassId = info.id;
        }
        plugin.getPlayerStore().markDirty();
        if (!r.classId.isEmpty()) {
            // re-sync the LuckPerms class group (idempotent): covers players that chose a class before it existed
            final String cid = r.classId;
            org.bukkit.Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline()) plugin.getClassManager().syncLuckPermsGroup(player, "", cid);
            }, 40L);
        }
        plugin.getClassLevelManager().applyStats(player);
        if (plugin.getRecipeManager() != null) plugin.getRecipeManager().discover(player);
        plugin.getRankManager().flushRewards(player);
        if (plugin.getConfig().getBoolean("quests.remind_on_join", true) && player.hasPermission("rpgleveltier.quest")) {
            plugin.getDailyQuestManager().assignDailyQuests(player);
        }
    }

    @EventHandler
    public void onRespawn(org.bukkit.event.player.PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        plugin.getServer().getScheduler().runTask(plugin, () -> plugin.getClassLevelManager().applyStats(player));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.getAntiAbuse().forget(event.getPlayer().getUniqueId());
        plugin.getPlayerStore().saveSoon();
        plugin.getAbilityManager().forget(event.getPlayer().getUniqueId());
    }
}
