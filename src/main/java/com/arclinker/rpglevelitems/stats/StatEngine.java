package com.arclinker.rpglevelitems.stats;

import com.arclinker.rpglevelitems.ClassLevelManager;
import com.arclinker.rpglevelitems.DataFiles;
import com.arclinker.rpglevelitems.RPGLevelTier;
import com.arclinker.rpglevelitems.rank.PlayerRecord;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.util.function.Function;

/**
 * Aggregates every stat of a player once per second from: class / subclass custom stats (per level),
 * equipped items (base + affixes + gems), gear-set bonuses and registered providers (prestige, talents,
 * titles, guild, events...). Vanilla-backed extras become attribute modifiers ("xstat_*").
 * Also enforces item requirements on armor / offhand.
 */
public final class StatEngine implements Listener {
    private final RPGLevelTier plugin;
    private final Map<UUID, Map<String, Double>> cache = new HashMap<>();
    private final List<Function<Player, Map<String, Double>>> providers = new ArrayList<>();
    private final Map<String, Map<String, Double>> classPerLevel = new HashMap<>();
    private final Map<String, Map<String, Double>> subPerLevel = new HashMap<>();
    private final Map<UUID, Long> warned = new HashMap<>();

    public StatEngine(RPGLevelTier plugin) {
        this.plugin = plugin;
        loadPerLevel();
        Bukkit.getPluginManager().registerEvents(this, plugin);
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) tick(p);
        }, 40L, 20L);
    }

    public void reload() {
        classPerLevel.clear();
        subPerLevel.clear();
        loadPerLevel();
    }

    private void loadPerLevel() {
        YamlConfiguration cfg = DataFiles.loadMerged(plugin, "stats");
        read(cfg.getConfigurationSection("classes"), classPerLevel);
        read(cfg.getConfigurationSection("subclasses"), subPerLevel);
    }

    private void read(ConfigurationSection sec, Map<String, Map<String, Double>> out) {
        if (sec == null) return;
        for (String id : sec.getKeys(false)) {
            ConfigurationSection s = sec.getConfigurationSection(id);
            if (s == null) continue;
            Map<String, Double> m = new HashMap<>();
            for (String k : s.getKeys(false)) if (Stats.isCustom(k)) m.put(k, s.getDouble(k));
            out.put(id.toLowerCase(Locale.ROOT), m);
        }
    }

    public void addProvider(Function<Player, Map<String, Double>> provider) {
        providers.add(provider);
    }

    // ---- queries --------------------------------------------------------------------

    /** Cached totals (computed on demand if missing). */
    public Map<String, Double> all(Player p) {
        Map<String, Double> m = cache.get(p.getUniqueId());
        if (m == null) {
            m = compute(p);
            cache.put(p.getUniqueId(), m);
        }
        return m;
    }

    public double get(Player p, String key) {
        return all(p).getOrDefault(key, 0.0);
    }

    // ---- computation ----------------------------------------------------------------

    private boolean isArmorPiece(ItemStack it) {
        String n = it.getType().name();
        return n.endsWith("_HELMET") || n.endsWith("_CHESTPLATE") || n.endsWith("_LEGGINGS") || n.endsWith("_BOOTS");
    }

    private void addItem(Player p, ItemStack it, Map<String, Double> out) {
        if (it == null || it.getType().isAir() || !it.hasItemMeta()) return;
        if (Requirements.check(plugin, p, it) != null) return;
        Stats.addAll(out, ItemData.totalStats(it));
    }

    /** Stats from class / subclass levels that are NOT vanilla-backed (those are applied by ClassLevelManager). */
    private void addLevels(Player p, Map<String, Double> out) {
        PlayerRecord r = plugin.getPlayerStore().get(p);
        var lm = plugin.getClassLevelManager();
        if (!r.classId.isEmpty()) {
            Map<String, Double> m = classPerLevel.get(r.classId.toLowerCase(Locale.ROOT));
            int lv = lm.getClassLevel(p) - 1;
            if (m != null) m.forEach((k, v) -> Stats.add(out, k, v * lv));
        }
        if (!r.subclassId.isEmpty()) {
            Map<String, Double> m = subPerLevel.get(r.subclassId.toLowerCase(Locale.ROOT));
            int lv = lm.getSubClassLevel(p) - 1;
            if (m != null) m.forEach((k, v) -> Stats.add(out, k, v * lv));
        }
    }

    public Map<String, Double> compute(Player p) {
        Map<String, Double> out = new LinkedHashMap<>();
        addLevels(p, out);
        ItemStack main = p.getInventory().getItemInMainHand();
        if (!isArmorPiece(main)) addItem(p, main, out);
        addItem(p, p.getInventory().getItemInOffHand(), out);
        for (ItemStack a : p.getInventory().getArmorContents()) addItem(p, a, out);
        if (plugin.getGearSetManager() != null) Stats.addAll(out, plugin.getGearSetManager().bonusFor(p));
        for (Function<Player, Map<String, Double>> f : providers) {
            try {
                Stats.addAll(out, f.apply(p));
            } catch (Exception e) {
                plugin.getLogger().warning("[StatEngine] provider failed: " + e);
            }
        }
        return out;
    }

    /** Recomputes, applies vanilla extras and enforces requirements. Runs every second. */
    public void tick(Player p) {
        enforce(p);
        Map<String, Double> now = compute(p);
        cache.put(p.getUniqueId(), now);
        double regen = now.getOrDefault("health_regen", 0.0);
        if (regen > 0 && !p.isDead()) {
            AttributeInstance mh = p.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH);
            double maxHp = mh == null ? 20 : mh.getValue();
            if (p.getHealth() < maxHp) p.setHealth(Math.min(maxHp, p.getHealth() + regen));
        }
        for (ClassLevelManager.Stat s : ClassLevelManager.Stat.values()) {
            AttributeInstance inst = p.getAttribute(s.attribute);
            if (inst == null) continue;
            NamespacedKey key = new NamespacedKey(RPGLevelTier.KEY_NS, "xstat_" + s.key);
            double want = now.getOrDefault(s.key, 0.0);
            AttributeModifier cur = null;
            for (AttributeModifier m : inst.getModifiers()) if (key.equals(m.getKey())) cur = m;
            if (cur != null && Math.abs(cur.getAmount() - want) < 1e-9) continue;
            if (cur != null) inst.removeModifier(cur);
            if (want != 0) inst.addModifier(new AttributeModifier(key, want, s.operation, EquipmentSlotGroup.ANY));
            if (s == ClassLevelManager.Stat.MAX_HEALTH && p.getHealth() > inst.getValue()) p.setHealth(inst.getValue());
        }
    }

    /** Armor / offhand whose requirements are not met goes back to the inventory. */
    private void enforce(Player p) {
        if (!Requirements.enabled(plugin) || Requirements.bypass(plugin, p)) return;
        var inv = p.getInventory();
        ItemStack[] armor = inv.getArmorContents();
        boolean changed = false;
        for (int i = 0; i < armor.length; i++) {
            String why = Requirements.check(plugin, p, armor[i]);
            if (why != null) {
                giveBack(p, armor[i], why);
                armor[i] = null;
                changed = true;
            }
        }
        if (changed) inv.setArmorContents(armor);
        String why = Requirements.check(plugin, p, inv.getItemInOffHand());
        if (why != null) {
            giveBack(p, inv.getItemInOffHand(), why);
            inv.setItemInOffHand(null);
        }
    }

    private void giveBack(Player p, ItemStack it, String why) {
        Map<Integer, ItemStack> left = p.getInventory().addItem(it);
        for (ItemStack rest : left.values()) p.getWorld().dropItemNaturally(p.getLocation(), rest);
        long now = System.currentTimeMillis();
        if (now - warned.getOrDefault(p.getUniqueId(), 0L) > 2000) {
            warned.put(p.getUniqueId(), now);
            p.sendMessage(plugin.getLanguageManager().tr(p, "combat.req_returned", why));
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        cache.remove(e.getPlayer().getUniqueId());
        warned.remove(e.getPlayer().getUniqueId());
    }
}
