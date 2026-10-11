package com.arclinker.rpglevelitems.ability;

import com.arclinker.rpglevelitems.RPGLevelTier;
import com.arclinker.rpglevelitems.Util;
import com.arclinker.rpglevelitems.rank.PlayerRecord;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.util.*;

/**
 * Loads rpg_abilities.yml and manages the ability trees:
 * skill points (every N class/subclass levels), ability XP (gained per level, spent to level
 * up unlocked abilities), unlocking, upgrading and casting.
 */
public final class AbilityManager {
    private final RPGLevelTier plugin;
    private final AbilityEffects effects;
    private final Map<String, AbilityDef> defs = new LinkedHashMap<>();
    private final Map<String, List<AbilityDef>> trees = new HashMap<>();
    private final List<AbilityDef> itemAbilities = new ArrayList<>();
    private final Map<UUID, Map<String, Long>> cooldowns = new HashMap<>();
    private final Set<UUID> casting = new HashSet<>();

    private int pointEvery = 3, initialPoints = 1, xpPerLevel = 150, xpCostBase = 100;
    boolean pvp = false;
    private boolean castMessage = true;

    public AbilityManager(RPGLevelTier plugin) {
        this.plugin = plugin;
        this.effects = new AbilityEffects(plugin, this);
        load();
    }

    // ---- loading -------------------------------------------------------------------

    private void load() {
        YamlConfiguration cfg = com.arclinker.rpglevelitems.DataFiles.loadMerged(plugin, "abilities");
        if (cfg.getKeys(false).isEmpty()) {
            plugin.getLogger().warning("No abilities found in data/abilities/ – abilities will be disabled.");
            return;
        }
        ConfigurationSection st = cfg.getConfigurationSection("settings");
        if (st != null) {
            pointEvery = Math.max(1, st.getInt("skill_point_every_levels", 3));
            initialPoints = Math.max(0, st.getInt("initial_skill_points", 1));
            xpPerLevel = Math.max(0, st.getInt("ability_xp_per_level", 150));
            xpCostBase = Math.max(1, st.getInt("ability_xp_cost_base", 100));
            pvp = st.getBoolean("pvp", false);
            castMessage = st.getBoolean("cast_message", true);
        }
        readGroup(cfg.getConfigurationSection("classes"), AbilityDef.Kind.CLASS);
        readGroup(cfg.getConfigurationSection("subclasses"), AbilityDef.Kind.SUBCLASS);
        ConfigurationSection items = cfg.getConfigurationSection("items");
        if (items != null) {
            for (String id : items.getKeys(false)) {
                ConfigurationSection s = items.getConfigurationSection(id);
                if (s == null) continue;
                AbilityDef d = parse(id, AbilityDef.Kind.ITEM, "", s);
                if (d != null) {
                    defs.put(id, d);
                    itemAbilities.add(d);
                }
            }
        }
        plugin.getLogger().info("[AbilityManager] Loaded " + defs.size() + " abilities (" + itemAbilities.size() + " item abilities).");
    }

    private void readGroup(ConfigurationSection group, AbilityDef.Kind kind) {
        if (group == null) return;
        for (String owner : group.getKeys(false)) {
            ConfigurationSection abilities = group.getConfigurationSection(owner + ".abilities");
            if (abilities == null) continue;
            for (String id : abilities.getKeys(false)) {
                ConfigurationSection s = abilities.getConfigurationSection(id);
                if (s == null) continue;
                AbilityDef d = parse(id, kind, owner.toLowerCase(Locale.ROOT), s);
                if (d == null) continue;
                defs.put(id, d);
                trees.computeIfAbsent(d.treeKey(), k -> new ArrayList<>()).add(d);
            }
        }
    }

    private AbilityDef parse(String id, AbilityDef.Kind kind, String owner, ConfigurationSection s) {
        ConfigurationSection eff = s.getConfigurationSection("effect");
        if (eff == null) {
            plugin.getLogger().warning("[AbilityManager] Ability '" + id + "' has no effect; skipped.");
            return null;
        }
        Map<String, Integer> reqs = new LinkedHashMap<>();
        ConfigurationSection ra = s.getConfigurationSection("requires.abilities");
        if (ra != null) for (String k : ra.getKeys(false)) reqs.put(k, ra.getInt(k));
        return new AbilityDef(id, kind, owner, s.getString("name", id), s.getString("description", ""),
                Util.icon(s.getString("icon"), Material.BOOK), s.getInt("row", 0), s.getInt("col", 4),
                Math.max(1, s.getInt("cost", 1)), Math.max(1, s.getInt("max_level", 5)),
                s.getDouble("cooldown", 10), s.getDouble("cooldown_per_level", 0), s.getInt("requires.level", 1),
                reqs, new LinkedHashMap<>(eff.getValues(true)),
                s.getString("trigger", ""), s.getStringList("applies_to"), s.getInt("min_tier", 1), s.getDouble("chance", 1.0), s.getDouble("mana", -1));
    }

    // ---- queries -------------------------------------------------------------------

    public AbilityDef get(String id) {
        return id == null ? null : defs.get(id);
    }

    public Collection<AbilityDef> all() {
        return defs.values();
    }

    public List<AbilityDef> tree(String treeKey) {
        return trees.getOrDefault(treeKey, List.of());
    }

    public List<AbilityDef> itemAbilities() {
        return itemAbilities;
    }

    public AbilityEffects effects() {
        return effects;
    }

    public int xpPerLevel() { return xpPerLevel; }
    public int pointEvery() { return pointEvery; }

    /** Active tree keys of the player: "class:<id>" and "sub:<id>" (when selected). */
    public List<String> activeTrees(Player p) {
        PlayerRecord r = plugin.getPlayerStore().get(p);
        List<String> out = new ArrayList<>();
        if (!r.classId.isEmpty()) out.add("class:" + r.classId);
        if (!r.subclassId.isEmpty()) out.add("sub:" + r.subclassId);
        return out;
    }

    public boolean isTreeActive(Player p, AbilityDef d) {
        return d.kind == AbilityDef.Kind.ITEM || activeTrees(p).contains(d.treeKey());
    }

    public int treeLevel(Player p, String treeKey) {
        PlayerRecord r = plugin.getPlayerStore().get(p);
        var lm = plugin.getClassLevelManager();
        if (treeKey.startsWith("class:")) return lm.getClassLevel(r, treeKey.substring(6));
        if (treeKey.startsWith("sub:")) return lm.getSubClassLevel(r, treeKey.substring(4));
        return 1;
    }

    public int totalPoints(Player p, String treeKey) {
        int extra = plugin.getPrestigeManager() == null ? 0 : plugin.getPrestigeManager().extraPoints(p, treeKey);
        return initialPoints + (treeLevel(p, treeKey) - 1) / pointEvery + extra;
    }

    /** Points granted by levels for a tree at a given level (no prestige). */
    public int initialPointsFor(String treeKey, int level) {
        return initialPoints + (level - 1) / pointEvery;
    }

    public int spentPoints(Player p, String treeKey) {
        PlayerRecord r = plugin.getPlayerStore().get(p);
        int spent = 0;
        for (AbilityDef d : tree(treeKey)) if (r.abilities.containsKey(d.id)) spent += d.cost;
        return spent;
    }

    public int availablePoints(Player p, String treeKey) {
        return totalPoints(p, treeKey) - spentPoints(p, treeKey);
    }

    public long abilityXp(Player p, String treeKey) {
        return plugin.getPlayerStore().get(p).abilityXp.getOrDefault(treeKey, 0L);
    }

    public int levelOf(Player p, String abilityId) {
        return plugin.getPlayerStore().get(p).abilities.getOrDefault(abilityId, 0);
    }

    public boolean isUnlocked(Player p, String abilityId) {
        return levelOf(p, abilityId) > 0;
    }

    public long upgradeCost(int currentLevel) {
        return (long) xpCostBase * Math.max(1, currentLevel);
    }

    // ---- unlock / upgrade ------------------------------------------------------------

    /** Returns null if the ability can be unlocked, otherwise the reason (Spanish). */
    public String unlockBlocker(Player p, AbilityDef d) {
        if (d.kind == AbilityDef.Kind.ITEM) return "Las habilidades de ítems no se desbloquean: dependen del ítem.";
        if (isUnlocked(p, d.id)) return "Ya la tienes desbloqueada.";
        if (!isTreeActive(p, d)) return "Pertenece a " + (d.kind == AbilityDef.Kind.CLASS ? "la clase " : "la subclase ") + d.owner + ", que no tienes seleccionada.";
        int lvl = treeLevel(p, d.treeKey());
        if (lvl < d.requiredLevel) return "Requiere nivel " + d.requiredLevel + " (tienes " + lvl + ").";
        for (Map.Entry<String, Integer> req : d.requiredAbilities.entrySet()) {
            AbilityDef rd = get(req.getKey());
            if (levelOf(p, req.getKey()) < req.getValue()) {
                return "Requiere " + (rd != null ? rd.name : req.getKey()) + " nivel " + req.getValue() + ".";
            }
        }
        int avail = availablePoints(p, d.treeKey());
        if (avail < d.cost) return "Necesitas " + d.cost + " punto(s) de habilidad (tienes " + avail + ").";
        return null;
    }

    public boolean unlock(Player p, AbilityDef d) {
        String why = unlockBlocker(p, d);
        if (why != null) {
            p.sendMessage("§c" + why);
            return false;
        }
        plugin.getPlayerStore().get(p).abilities.put(d.id, 1);
        plugin.getPlayerStore().markDirty();
        p.sendMessage(plugin.getLanguageManager().tr(p, "ability.unlocked", d.name));
        p.playSound(p.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.4f);
        return true;
    }

    /** Spends ability XP to raise an unlocked ability by one level. */
    public boolean upgrade(Player p, AbilityDef d) {
        PlayerRecord r = plugin.getPlayerStore().get(p);
        int lvl = r.abilities.getOrDefault(d.id, 0);
        if (lvl <= 0) {
            p.sendMessage("§cPrimero debes desbloquear esa habilidad.");
            return false;
        }
        if (lvl >= d.maxLevel) {
            p.sendMessage("§eEsa habilidad ya está al nivel máximo.");
            return false;
        }
        String tree = d.treeKey();
        long cost = upgradeCost(lvl);
        long have = r.abilityXp.getOrDefault(tree, 0L);
        if (have < cost) {
            p.sendMessage("§cNecesitas " + cost + " ability XP (tienes " + have + ").");
            return false;
        }
        r.abilityXp.put(tree, have - cost);
        r.abilities.put(d.id, lvl + 1);
        plugin.getPlayerStore().markDirty();
        p.sendMessage(plugin.getLanguageManager().tr(p, "ability.upgraded", d.name, lvl + 1, cost));
        p.playSound(p.getLocation(), org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.2f);
        return true;
    }

    /** Called when a class / subclass levels up: grants ability XP and announces new skill points. */
    public void onLevelUp(Player p, boolean subclass, String id, int from, int to) {
        String tree = (subclass ? "sub:" : "class:") + id;
        long gained = (long) xpPerLevel * (to - from);
        if (gained > 0) {
            PlayerRecord r = plugin.getPlayerStore().get(p);
            r.abilityXp.merge(tree, gained, Long::sum);
            plugin.getPlayerStore().markDirty();
            p.sendMessage("§d+" + gained + " ability XP §7(úsala en /ability para subir tus habilidades)");
        }
        int newPoints = (to - 1) / pointEvery - (from - 1) / pointEvery;
        if (newPoints > 0) p.sendMessage("§a+" + newPoints + " punto(s) de habilidad §7- puntos disponibles: §a" + availablePoints(p, tree));
    }

    // ---- casting ---------------------------------------------------------------------

    public boolean isCasting(Player p) {
        return casting.contains(p.getUniqueId());
    }

    void setCasting(Player p, boolean value) {
        if (value) casting.add(p.getUniqueId()); else casting.remove(p.getUniqueId());
    }

    public long cooldownLeftMs(Player p, String key) {
        Map<String, Long> m = cooldowns.get(p.getUniqueId());
        if (m == null) return 0;
        return Math.max(0, m.getOrDefault(key, 0L) - System.currentTimeMillis());
    }

    /** Drops per-player runtime state (cooldowns) when a player leaves. */
    public void forget(UUID id) {
        cooldowns.remove(id);
        casting.remove(id);
    }

    /** Cooldown after the cooldown_reduction stat (capped at 50%). */
    public double reducedCooldown(Player p, double seconds) {
        double cdr = Math.min(50, plugin.getStatEngine().get(p, "cooldown_reduction"));
        return Math.max(0.5, seconds * (1 - cdr / 100.0));
    }

    /** Ability pools refunded: removes every unlocked ability of the active class/subclass trees. Returns refunded ability XP. */
    public long respec(Player p) {
        PlayerRecord r = plugin.getPlayerStore().get(p);
        long refund = 0;
        for (String tree : activeTrees(p)) {
            long treeRefund = 0;
            for (AbilityDef d : tree(tree)) {
                int lv = r.abilities.getOrDefault(d.id, 0);
                if (lv <= 0) continue;
                for (int l = 1; l < lv; l++) treeRefund += upgradeCost(l);
                r.abilities.remove(d.id);
                for (int i = 0; i < r.hotbar.length; i++) if (r.hotbar[i].equals(d.id)) r.hotbar[i] = "";
            }
            r.abilityXp.merge(tree, treeRefund, Long::sum);
            refund += treeRefund;
        }
        r.lastRespec = System.currentTimeMillis();
        plugin.getPlayerStore().markDirty();
        return refund;
    }

    public void startCooldown(Player p, String key, double seconds) {
        cooldowns.computeIfAbsent(p.getUniqueId(), k -> new HashMap<>()).put(key, System.currentTimeMillis() + (long) (seconds * 1000));
    }

    /**
     * Casts a class/subclass ability for the player. Only works if the player has unlocked it
     * (and its class/subclass is currently selected) - this is what the ability tomes call.
     */
    public boolean castUnlocked(Player p, AbilityDef d) {
        int level = levelOf(p, d.id);
        if (level <= 0) {
            p.sendMessage(plugin.getLanguageManager().tr(p, "ability.locked", d.name));
            return false;
        }
        if (!isTreeActive(p, d)) {
            p.sendMessage("§cEsa habilidad pertenece a " + (d.kind == AbilityDef.Kind.CLASS ? "la clase " : "la subclase ") + d.owner + ", que no tienes seleccionada.");
            return false;
        }
        if (!plugin.getZones().abilitiesAllowed(p)) {
            p.sendMessage(plugin.getLanguageManager().tr(p, "ability.zone_off"));
            return false;
        }
        long left = cooldownLeftMs(p, d.id);
        if (left > 0) {
            com.arclinker.rpglevelitems.Util.actionBar(p, plugin.getLanguageManager().tr(p, "ability.cooldown", d.name, String.format(Locale.ROOT, "%.1f", left / 1000.0)));
            return false;
        }
        var castEvent = new com.arclinker.rpglevelitems.api.events.AbilityCastEvent(p, d.id, level);
        org.bukkit.Bukkit.getPluginManager().callEvent(castEvent);
        if (castEvent.isCancelled()) return false;
        double mana = plugin.getMana().cost(d, level);
        if (!plugin.getMana().has(p, mana)) {
            com.arclinker.rpglevelitems.Util.actionBar(p, plugin.getLanguageManager().tr(p, "ability.mana_low", (int) mana, (int) plugin.getMana().get(p)));
            return false;
        }
        boolean ok = effects.cast(p, d.effect, level, null);
        if (!ok) return false;
        plugin.getMana().spend(p, mana);
        startCooldown(p, d.id, reducedCooldown(p, d.cooldownAt(level)));
        if (plugin.getAchievements() != null) plugin.getAchievements().add(p, "casts", 1);
        if (castMessage) com.arclinker.rpglevelitems.Util.actionBar(p, plugin.getLanguageManager().tr(p, "ability.cast", d.name, level));
        return true;
    }
}
