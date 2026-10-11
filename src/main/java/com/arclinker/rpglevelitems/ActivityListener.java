package com.arclinker.rpglevelitems;

import com.arclinker.rpglevelitems.abuse.AntiAbuse;
import com.arclinker.rpglevelitems.rank.PlayerRecord;
import com.arclinker.rpglevelitems.rank.RankCategory;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.FurnaceExtractEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Locale;

/**
 * Turns gameplay into progress: ranking points for class/subclass, job XP for active jobs
 * and daily quest progress.
 */
public class ActivityListener implements Listener {
    private final RPGLevelTier plugin;

    public ActivityListener(RPGLevelTier plugin) {
        this.plugin = plugin;
    }

    private static boolean isBoss(Entity e) {
        return e instanceof EnderDragon || e instanceof Wither || e instanceof Warden || e instanceof ElderGuardian || e.getScoreboardTags().contains("rlv_boss");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onKill(EntityDeathEvent event) {
        LivingEntity dead = event.getEntity();
        Player killer = dead.getKiller();
        if (killer == null || dead instanceof Player) return;
        PlayerRecord r = plugin.getPlayerStore().get(killer);
        double throttle = plugin.getAntiAbuse().killMultiplier(killer, dead);
        if (throttle <= 0) return;
        double mult = throttle;
        mult *= plugin.getMobs().rewardMultiplier(dead);
        mult *= plugin.getEvents().xpMultiplier();
        mult *= 1 + plugin.getStatEngine().get(killer, "xp_bonus") / 100.0;
        mult *= plugin.getZones().number(killer.getLocation(), "xp", 1.0);
        mult *= plugin.getParties().bonusMultiplier(killer);
        giveCombatPoints(killer, r, dead instanceof Enemy, isBoss(dead), mult);
        plugin.getJobManager().addJobXP(killer, "hunter", (int) AntiAbuse.scale(isBoss(dead) ? 25 : 2, mult));
        if (throttle >= 0.99) plugin.getDailyQuestManager().addProgress(killer, "kill", dead.getType().name(), 1);
    }

    private void giveCombatPoints(Player p, PlayerRecord r, boolean hostile, boolean boss, double mult) {
        if (!hostile) return;
        int points = boss ? plugin.getConfig().getInt("ranks.points.boss_kill", 25)
                          : plugin.getConfig().getInt("ranks.points.kill", 1);
        points = (int) AntiAbuse.scale(points, mult);
        if (!r.classId.isEmpty()) plugin.getRankManager().addPoints(p, RankCategory.CLASS, points);
        if (!r.subclassId.isEmpty()) plugin.getRankManager().addPoints(p, RankCategory.SUBCLASS, points);
        // Class / subclass levels
        long xp = AntiAbuse.scale(boss ? plugin.getConfig().getLong("class_levels.xp.boss_kill", 100L) : plugin.getConfig().getLong("class_levels.xp.kill", 5L), mult);
        plugin.getClassLevelManager().addClassXp(p, xp);
        plugin.getClassLevelManager().addSubClassXp(p, xp);
        plugin.getParties().share(p, xp);
        plugin.getGuilds().addXp(p, Math.round(xp * plugin.getConfig().getDouble("guild.xp_share", 0.5)));
        var ach = plugin.getAchievements();
        ach.add(p, boss ? "boss_kills" : "kills", 1);
        if (boss) {
            ach.add(p, "kills", 1);
            plugin.getDailyQuestManager().addProgress(p, "boss", "ANY", 1);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Player p = event.getPlayer();
        Block block = event.getBlock();
        Material type = block.getType();
        String name = type.name();
        if (plugin.getAntiAbuse().consumePlaced(block)) return; // player-placed: no progress

        if (name.endsWith("_ORE") || type == Material.ANCIENT_DEBRIS) {
            plugin.getJobManager().addJobXP(p, "miner", type == Material.ANCIENT_DEBRIS ? 8 : 3);
        } else if (Tag.LOGS.isTagged(type)) {
            plugin.getJobManager().addJobXP(p, "lumberjack", 2);
        } else if (block.getBlockData() instanceof Ageable crop && crop.getAge() >= crop.getMaximumAge()
                && type != Material.SWEET_BERRY_BUSH && type != Material.CAVE_VINES) {
            plugin.getJobManager().addJobXP(p, "farmer", 2);
        }
        plugin.getDailyQuestManager().addProgress(p, "gather", name, 1);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFish(PlayerFishEvent event) {
        if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH) return;
        if (!(event.getCaught() instanceof Item item)) return;
        Player p = event.getPlayer();
        plugin.getJobManager().addJobXP(p, "fisher", 3);
        plugin.getDailyQuestManager().addProgress(p, "fish", item.getItemStack().getType().name(), 1);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCraft(CraftItemEvent event) {
        if (!(event.getWhoClicked() instanceof Player p)) return;
        ItemStack result = event.getRecipe().getResult();
        String n = result.getType().name().toLowerCase(Locale.ROOT);
        if (n.endsWith("_sword") || n.endsWith("_pickaxe") || n.endsWith("_axe") || n.endsWith("_shovel")
                || n.endsWith("_hoe") || n.endsWith("_helmet") || n.endsWith("_chestplate")
                || n.endsWith("_leggings") || n.endsWith("_boots") || n.equals("shield")) {
            plugin.getJobManager().addJobXP(p, "blacksmith", 2);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEnchant(EnchantItemEvent event) {
        plugin.getJobManager().addJobXP(event.getEnchanter(), "enchanter", Math.max(3, event.getExpLevelCost() * 2));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFurnace(FurnaceExtractEvent event) {
        Player p = event.getPlayer();
        Material m = event.getItemType();
        if (m.isEdible()) plugin.getJobManager().addJobXP(p, "cook", Math.max(1, event.getItemAmount()));
        else if (m.name().endsWith("_INGOT")) plugin.getJobManager().addJobXP(p, "miner", event.getItemAmount());
    }

    /** Alchemist XP: taking potions out of a brewing stand. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBrewTake(InventoryClickEvent event) {
        if (event.getView().getTopInventory().getType() != InventoryType.BREWING) return;
        if (event.getClickedInventory() != event.getView().getTopInventory() || event.getRawSlot() > 2) return;
        ItemStack it = event.getCurrentItem();
        if (it == null || !it.getType().name().contains("POTION") || !(event.getWhoClicked() instanceof Player p)) return;
        plugin.getJobManager().addJobXP(p, "alchemist", 6);
    }
}
