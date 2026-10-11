package com.arclinker.rpglevelitems.jobabilities;

import com.arclinker.rpglevelitems.RPGLevelTier;
import com.arclinker.rpglevelitems.jobabilities.JobAbilityManager.Def;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.inventory.FurnaceExtractEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/** Effects of the job abilities (see {@link JobAbilityManager}). */
public final class JobAbilityListener implements Listener {
    private final RPGLevelTier plugin;
    private final JobAbilityManager ja;
    /** Players currently breaking extra blocks (vein miner / treecapitator / harvest area): ignore their nested events. */
    private final Set<UUID> chaining = new HashSet<>();

    public JobAbilityListener(RPGLevelTier plugin, JobAbilityManager ja) {
        this.plugin = plugin;
        this.ja = ja;
    }

    // ---- helpers -------------------------------------------------------------------------------

    private static boolean isOre(Material m) {
        return m.name().endsWith("_ORE") || m == Material.ANCIENT_DEBRIS;
    }

    private static boolean matureCrop(Block b) {
        if (!(b.getBlockData() instanceof Ageable a) || a.getAge() < a.getMaximumAge()) return false;
        Material t = b.getType();
        return t == Material.WHEAT || t == Material.CARROTS || t == Material.POTATOES || t == Material.BEETROOTS || t == Material.NETHER_WART;
    }

    private static boolean sameOre(Material a, Material b) {
        return a == b || a.name().replace("DEEPSLATE_", "").equals(b.name().replace("DEEPSLATE_", ""));
    }

    private void extraDrops(Player p, Block b, ItemStack tool) {
        Location at = b.getLocation().add(0.5, 0.5, 0.5);
        for (ItemStack drop : b.getDrops(tool, p)) b.getWorld().dropItemNaturally(at, drop.clone());
    }

    private void damage(Player p, ItemStack tool) {
        if (!(tool.getItemMeta() instanceof Damageable d)) return;
        d.setDamage(d.getDamage() + 1);
        tool.setItemMeta(d);
        p.getInventory().setItemInMainHand(tool);
    }

    private boolean toolHasDurability(ItemStack tool) {
        return !(tool.getItemMeta() instanceof Damageable d) || tool.getType().getMaxDurability() <= 0 || d.getDamage() + 2 < tool.getType().getMaxDurability();
    }

    /** Breaks a block as the player would (so protection plugins can veto it). */
    private boolean breakAs(Player p, Block b, ItemStack tool) {
        BlockBreakEvent ev = new BlockBreakEvent(b, p);
        chaining.add(p.getUniqueId());
        try {
            Bukkit.getPluginManager().callEvent(ev);
        } finally {
            chaining.remove(p.getUniqueId());
        }
        if (ev.isCancelled()) return false;
        if (ev.isDropItems()) b.breakNaturally(tool); else b.setType(Material.AIR);
        damage(p, tool);
        return true;
    }

    /** Connected blocks (26-neighbourhood) accepted by the predicate, up to max, excluding the origin. */
    private List<Block> connected(Block origin, java.util.function.Predicate<Block> accept, int max) {
        List<Block> out = new ArrayList<>();
        Set<Block> seen = new HashSet<>(List.of(origin));
        Deque<Block> queue = new ArrayDeque<>(List.of(origin));
        while (!queue.isEmpty() && out.size() < max) {
            Block cur = queue.poll();
            for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dy == 0 && dz == 0) continue;
                Block n = cur.getRelative(dx, dy, dz);
                if (!seen.add(n) || !accept.test(n)) continue;
                out.add(n);
                queue.add(n);
                if (out.size() >= max) return out;
            }
        }
        return out;
    }

    // ---- mining / logging / farming --------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        Player p = e.getPlayer();
        if (chaining.contains(p.getUniqueId()) || p.getGameMode() == org.bukkit.GameMode.CREATIVE) return;
        if (plugin.getPlayerStore().get(p).jobAbilities.isEmpty()) return;
        Block b = e.getBlock();
        Material m = b.getType();
        ItemStack tool = p.getInventory().getItemInMainHand();
        String tn = tool.getType().name();
        boolean placed = plugin.getAntiAbuse().isPlaced(b);

        if (isOre(m) && !placed) {
            if (ja.roll(p, "double_drops_ore")) extraDrops(p, b, tool);
            if (p.isSneaking() && tn.endsWith("_PICKAXE")) {
                var act = ja.active(p, "vein_miner");
                if (!act.isEmpty()) {
                    int max = 0;
                    for (var en : act.entrySet()) max = Math.max(max, (int) JobAbilityManager.param(en.getKey(), en.getValue(), "max_blocks", 6));
                    for (Block n : connected(b, x -> sameOre(x.getType(), m) && !plugin.getAntiAbuse().isPlaced(x), max)) {
                        if (!toolHasDurability(tool)) break;
                        breakAs(p, n, tool);
                    }
                }
            }
        } else if (Tag.LOGS.isTagged(m) && !placed) {
            if (ja.roll(p, "double_drops_log")) extraDrops(p, b, tool);
            if (p.isSneaking() && tn.endsWith("_AXE")) {
                var act = ja.active(p, "treecapitator");
                if (!act.isEmpty()) {
                    int max = 0;
                    for (var en : act.entrySet()) max = Math.max(max, (int) JobAbilityManager.param(en.getKey(), en.getValue(), "max_blocks", 16));
                    for (Block n : connected(b, x -> Tag.LOGS.isTagged(x.getType()) && !plugin.getAntiAbuse().isPlaced(x), max)) {
                        if (!toolHasDurability(tool)) break;
                        breakAs(p, n, tool);
                    }
                }
            }
        } else if (matureCrop(b)) {
            if (ja.roll(p, "double_drops_crop")) extraDrops(p, b, tool);
            replant(p, b);
            if (p.isSneaking() && tn.endsWith("_HOE")) {
                var act = ja.active(p, "harvest_area");
                if (!act.isEmpty()) {
                    int radius = 0;
                    for (var en : act.entrySet()) radius = Math.max(radius, (int) Math.floor(JobAbilityManager.param(en.getKey(), en.getValue(), "radius", 1)));
                    radius = Math.max(1, Math.min(3, radius));
                    for (int dx = -radius; dx <= radius; dx++) for (int dz = -radius; dz <= radius; dz++) {
                        if (dx == 0 && dz == 0) continue;
                        Block n = b.getRelative(dx, 0, dz);
                        if (!matureCrop(n) || !toolHasDurability(tool)) continue;
                        Material type = n.getType();
                        if (breakAs(p, n, tool)) {
                            replantType(p, n, type);
                        }
                    }
                }
            }
        }
    }

    private void replant(Player p, Block b) {
        replantType(p, b, b.getType());
    }

    private void replantType(Player p, Block b, Material type) {
        var act = ja.active(p, "replenish");
        if (act.isEmpty()) return;
        double chance = 0;
        for (var en : act.entrySet()) chance = Math.max(chance, JobAbilityManager.param(en.getKey(), en.getValue(), "chance", 100));
        if (ThreadLocalRandom.current().nextDouble() * 100 >= chance) return;
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!b.getType().isAir()) return;
            Material below = b.getRelative(BlockFace.DOWN).getType();
            boolean ok = type == Material.NETHER_WART ? below == Material.SOUL_SAND : below == Material.FARMLAND;
            if (!ok) return;
            b.setType(type);
            if (b.getBlockData() instanceof Ageable a) {
                a.setAge(0);
                b.setBlockData(a);
            }
        });
    }

    // ---- fishing ---------------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFish(PlayerFishEvent e) {
        Player p = e.getPlayer();
        if (plugin.getPlayerStore().get(p).jobAbilities.isEmpty()) return;
        if (e.getState() == PlayerFishEvent.State.FISHING) {
            double cut = Math.min(60, ja.sum(p, "quick_hook", "reduction_percent"));
            if (cut > 0) {
                var hook = e.getHook();
                hook.setMinWaitTime(Math.max(20, (int) (hook.getMinWaitTime() * (1 - cut / 100.0))));
                hook.setMaxWaitTime(Math.max(hook.getMinWaitTime() + 20, (int) (hook.getMaxWaitTime() * (1 - cut / 100.0))));
            }
        } else if (e.getState() == PlayerFishEvent.State.CAUGHT_FISH && e.getCaught() instanceof Item caught) {
            if (ja.roll(p, "lucky_catch")) {
                p.getWorld().dropItem(caught.getLocation(), caught.getItemStack().clone()).setVelocity(caught.getVelocity());
                p.sendMessage("§b✦ ¡Captura afortunada!");
            }
            if (ja.roll(p, "treasure_hunter")) treasure(p, caught.getLocation());
        }
    }

    private void treasure(Player p, Location at) {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        ItemStack reward = null;
        int roll = rnd.nextInt(3);
        if (roll == 0) reward = plugin.getGemManager().randomItem(1 + (rnd.nextInt(4) == 0 ? 1 : 0));
        else if (roll == 1 && plugin.getRlvpHandler() != null) {
            reward = plugin.getRlvpHandler().getItem(rnd.nextBoolean() ? "steel_ingot" : "cobalt_ingot");
            if (reward != null) reward.setAmount(1 + rnd.nextInt(2));
        }
        if (reward == null) {
            long coins = 20 + rnd.nextInt(60);
            plugin.getEconomy().deposit(p, coins);
            p.sendMessage("§6✦ ¡Tesoro! §e+" + coins + " monedas");
            return;
        }
        p.getWorld().dropItem(at, reward);
        p.sendMessage("§6✦ ¡Pescaste un tesoro!");
    }

    // ---- hunting ---------------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onKill(EntityDeathEvent e) {
        LivingEntity dead = e.getEntity();
        Player p = dead.getKiller();
        if (p == null || dead instanceof Player || !(dead instanceof Enemy) || dead.getScoreboardTags().contains("rlv_spawner")) return;
        if (plugin.getPlayerStore().get(p).jobAbilities.isEmpty()) return;
        if (ja.roll(p, "trophy_hunter")) {
            List<ItemStack> extra = new ArrayList<>();
            for (ItemStack d : e.getDrops()) extra.add(d.clone());
            e.getDrops().addAll(extra);
        }
        var act = ja.active(p, "bounty_hunter");
        for (var en : act.entrySet()) {
            if (ThreadLocalRandom.current().nextDouble() * 100 < JobAbilityManager.param(en.getKey(), en.getValue(), "chance", 15)) {
                long coins = Math.round(JobAbilityManager.param(en.getKey(), en.getValue(), "coins", 6));
                plugin.getEconomy().deposit(p, coins);
                com.arclinker.rpglevelitems.Util.actionBar(p, "§6✦ Recompensa: §e+" + coins + " monedas");
                break;
            }
        }
    }

    // ---- cooking / brewing / enchanting / eating ------------------------------------------------------

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFurnace(FurnaceExtractEvent e) {
        Player p = e.getPlayer();
        if (!e.getItemType().isEdible() || !ja.roll(p, "hearty_meals")) return;
        ItemStack extra = new ItemStack(e.getItemType(), Math.max(1, e.getItemAmount() / 2));
        for (ItemStack rest : p.getInventory().addItem(extra).values()) p.getWorld().dropItemNaturally(p.getLocation(), rest);
        com.arclinker.rpglevelitems.Util.actionBar(p, "§6✦ ¡Comida abundante!");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBrew(InventoryClickEvent e) {
        if (e.getView().getTopInventory().getType() != InventoryType.BREWING || e.getClickedInventory() != e.getView().getTopInventory() || e.getRawSlot() > 2) return;
        if (!(e.getWhoClicked() instanceof Player p)) return;
        ItemStack it = e.getCurrentItem();
        if (it == null || !it.getType().name().contains("POTION")) return;
        if (ja.roll(p, "potion_cache")) {
            for (ItemStack rest : p.getInventory().addItem(it.clone()).values()) p.getWorld().dropItemNaturally(p.getLocation(), rest);
            com.arclinker.rpglevelitems.Util.actionBar(p, "§d✦ ¡Poción extra!");
        }
        if (ja.roll(p, "volatile_brew")) {
            for (var en : ja.active(p, "volatile_brew").entrySet()) {
                Object list = en.getKey().params().get("potions");
                if (!(list instanceof List<?> l)) continue;
                for (Object o : l) {
                    String[] parts = String.valueOf(o).split(":");
                    PotionEffectType t = org.bukkit.Registry.EFFECT.get(NamespacedKey.minecraft(parts[0].toLowerCase(Locale.ROOT)));
                    if (t == null) continue;
                    int secs = parts.length > 1 ? Integer.parseInt(parts[1]) : 30;
                    int amp = parts.length > 2 ? Integer.parseInt(parts[2]) : 0;
                    p.addPotionEffect(new PotionEffect(t, secs * 20, amp));
                }
                break;
            }
            com.arclinker.rpglevelitems.Util.actionBar(p, "§d✦ ¡Mezcla volátil!");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEnchant(EnchantItemEvent e) {
        Player p = e.getEnchanter();
        if (ja.roll(p, "enchant_refund")) {
            int lv = (int) Math.max(1, ja.sum(p, "enchant_refund", "levels"));
            Bukkit.getScheduler().runTask(plugin, () -> p.giveExpLevels(lv));
            com.arclinker.rpglevelitems.Util.actionBar(p, "§b✦ Recuperas " + lv + " nivel(es)");
        }
        if (ja.roll(p, "arcane_insight")) {
            ItemStack gem = plugin.getGemManager().randomItem(1);
            if (gem != null) {
                for (ItemStack rest : p.getInventory().addItem(gem).values()) p.getWorld().dropItemNaturally(p.getLocation(), rest);
                p.sendMessage("§d✦ Visión arcana: obtuviste una gema.");
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEat(PlayerItemConsumeEvent e) {
        Player p = e.getPlayer();
        if (!e.getItem().getType().isEdible()) return;
        var act = ja.active(p, "feast");
        for (var en : act.entrySet()) {
            int secs = (int) JobAbilityManager.param(en.getKey(), en.getValue(), "seconds", 20);
            p.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, secs * 20, Math.max(0, en.getValue() / 2)));
            p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, Math.max(3, secs / 2) * 20, 0));
            com.arclinker.rpglevelitems.Util.actionBar(p, "§6✦ ¡Banquete!");
            break;
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        chaining.remove(e.getPlayer().getUniqueId());
    }
}
