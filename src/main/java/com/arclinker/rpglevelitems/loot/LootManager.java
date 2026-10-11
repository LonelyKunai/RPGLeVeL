package com.arclinker.rpglevelitems.loot;

import com.arclinker.rpglevelitems.DataFiles;
import com.arclinker.rpglevelitems.RPGLevelTier;
import com.arclinker.rpglevelitems.itemcreator.RlvpHandler;
import com.arclinker.rpglevelitems.rank.PlayerRecord;
import com.arclinker.rpglevelitems.station.StationManager;
import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/** Loot tables (data/loot/loot.yml): rolled gear, gems, materials and coins added to mob drops. */
public final class LootManager implements Listener {
    private record Drop(String type, int weight, int tMin, int tMax, boolean auto, String id, int aMin, int aMax) {}

    private record Table(String name, boolean hostile, boolean boss, Set<String> entities, int minLevel, int maxLevel, double chance, List<Drop> drops) {}

    private final RPGLevelTier plugin;
    private final List<Table> tables = new ArrayList<>();
    private boolean enabled = true;
    private double classPref = 0.6;

    public LootManager(RPGLevelTier plugin) {
        this.plugin = plugin;
        reload();
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void reload() {
        tables.clear();
        YamlConfiguration cfg = DataFiles.loadMerged(plugin, "loot");
        enabled = cfg.getBoolean("settings.enabled", true);
        classPref = cfg.getDouble("settings.class_preference", 0.6);
        ConfigurationSection root = cfg.getConfigurationSection("tables");
        if (root == null) return;
        for (String name : root.getKeys(false)) {
            ConfigurationSection t = root.getConfigurationSection(name);
            if (t == null) continue;
            Set<String> ents = new HashSet<>();
            for (String s : t.getStringList("match.entities")) ents.add(s.toUpperCase(Locale.ROOT));
            List<Drop> drops = new ArrayList<>();
            for (Map<?, ?> m : t.getMapList("drops")) {
                String type = String.valueOf(m.get("type"));
                int w = m.get("weight") instanceof Number n ? n.intValue() : 1;
                int[] tier = range(m.get("tier"), m.get("level"), 1, 1);
                boolean auto = "auto".equals(String.valueOf(m.get("tier")));
                int[] amt = range(m.get("amount"), null, 1, 1);
                String id = m.get("id") != null ? String.valueOf(m.get("id")) : (m.get("gem") != null ? String.valueOf(m.get("gem")) : "");
                drops.add(new Drop(type, Math.max(1, w), tier[0], tier[1], auto, id, amt[0], amt[1]));
            }
            tables.add(new Table(name, t.getBoolean("match.hostile", false), t.getBoolean("match.boss", false), ents,
                    t.getInt("match.min_level", 0), t.getInt("match.max_level", 9999), t.getDouble("chance", 0.01), drops));
        }
        plugin.getLogger().info("[LootManager] Loaded " + tables.size() + " loot tables.");
    }

    private static int[] range(Object a, Object b, int dMin, int dMax) {
        Object o = a instanceof List<?> ? a : b;
        if (o instanceof List<?> l && l.size() >= 2 && l.get(0) instanceof Number n0 && l.get(1) instanceof Number n1) return new int[]{n0.intValue(), n1.intValue()};
        return new int[]{dMin, dMax};
    }

    private static int roll(int min, int max) {
        return max <= min ? min : ThreadLocalRandom.current().nextInt(min, max + 1);
    }

    private boolean isBoss(LivingEntity e) {
        return e instanceof EnderDragon || e instanceof Wither || e instanceof Warden || e instanceof ElderGuardian || e.getScoreboardTags().contains("rlv_boss");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDeath(EntityDeathEvent e) {
        if (!enabled || e.getEntity() instanceof Player) return;
        Player killer = e.getEntity().getKiller();
        if (killer == null) return;
        LivingEntity dead = e.getEntity();
        boolean boss = isBoss(dead), hostile = dead instanceof Enemy;
        int level = plugin.getMobs() == null ? 1 : plugin.getMobs().levelOf(dead);
        double mult = (plugin.getEvents() == null ? 1.0 : plugin.getEvents().dropMultiplier());
        AttributeInstance luck = killer.getAttribute(Attribute.GENERIC_LUCK);
        mult *= 1 + (luck == null ? 0 : Math.max(0, luck.getValue())) * 0.02;
        if (dead.getScoreboardTags().contains("rlv_spawner")) mult *= plugin.getConfig().getDouble("anti_abuse.spawner_multiplier", 0.2);
        for (Table t : tables) {
            if (t.boss() && !boss) continue;
            if (t.hostile() && !hostile && !boss) continue;
            if (!t.entities().isEmpty() && !t.entities().contains(dead.getType().name())) continue;
            if (level < t.minLevel() || level > t.maxLevel()) continue;
            if (ThreadLocalRandom.current().nextDouble() >= t.chance() * mult) continue;
            Drop d = pick(t.drops());
            if (d == null) continue;
            ItemStack item = make(killer, d, level, boss);
            if (item != null) e.getDrops().add(item);
        }
    }

    private Drop pick(List<Drop> drops) {
        int total = 0;
        for (Drop d : drops) total += d.weight();
        if (total <= 0) return null;
        int r = ThreadLocalRandom.current().nextInt(total);
        for (Drop d : drops) {
            r -= d.weight();
            if (r < 0) return d;
        }
        return drops.get(0);
    }

    private ItemStack make(Player killer, Drop d, int level, boolean boss) {
        RlvpHandler h = plugin.getRlvpHandler();
        switch (d.type()) {
            case "coins" -> {
                plugin.getEconomy().deposit(killer, roll(d.aMin(), d.aMax()));
                return null;
            }
            case "gem" -> {
                var gm = plugin.getGemManager();
                int lv = roll(d.tMin(), d.tMax());
                if (!d.id().isEmpty() && !d.id().equals("random")) return gm.item(d.id(), lv);
                return gm.randomItem(lv);
            }
            case "material" -> {
                ItemStack it = h == null ? null : h.getItem(d.id());
                if (it != null) it.setAmount(roll(d.aMin(), d.aMax()));
                return it;
            }
            case "gear" -> {
                int tier = d.auto() ? Math.max(1, Math.min(5, 1 + level / 12)) : roll(d.tMin(), d.tMax());
                return gear(killer, tier, boss);
            }
            default -> {
                return null;
            }
        }
    }

    /** Random equipment piece of the tier (preferring the player's class / subclass sets) with rolled affixes. */
    public ItemStack gear(Player p, int tier, boolean extraAffix) {
        RlvpHandler h = plugin.getRlvpHandler();
        if (h == null) return null;
        Set<String> preferred = new HashSet<>();
        StationManager sm = plugin.getStationManager();
        StationManager.Station cs = sm.ownStation(p, false), ss = sm.ownStation(p, true);
        if (cs != null) preferred.add(cs.id());
        if (ss != null) preferred.add(ss.id());
        boolean prefer = !preferred.isEmpty() && ThreadLocalRandom.current().nextDouble() < classPref;
        List<String> pool = new ArrayList<>();
        for (RlvpHandler.Category c : h.getCategories()) {
            if (!(c.group().equals("classes") || c.group().equals("subclasses") || c.group().equals("universal"))) continue;
            if (prefer && !preferred.contains(c.id())) continue;
            for (String id : c.ids()) {
                RlvpHandler.ItemInfo i = h.getInfo(id);
                if (i.tier() == tier && (i.type().equals("weapon") || i.type().equals("armor") || i.type().equals("offhand"))) pool.add(id);
            }
        }
        if (pool.isEmpty()) return null;
        ItemStack item = h.getItem(pool.get(ThreadLocalRandom.current().nextInt(pool.size())));
        if (item == null) return null;
        var am = plugin.getAffixManager();
        int n = am.countFor(tier) + (extraAffix ? 1 : 0);
        am.roll(item, tier, Math.min(n, 5));
        return item;
    }
}
