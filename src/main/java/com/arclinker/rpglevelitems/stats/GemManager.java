package com.arclinker.rpglevelitems.stats;

import com.arclinker.rpglevelitems.DataFiles;
import com.arclinker.rpglevelitems.RPGLevelTier;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;

/**
 * Gems (data/gems/): items that are socketed into item sockets (click a gem on top of an item in the
 * inventory) and add stats. Three equal gems combine into the next level in a crafting table.
 */
public final class GemManager implements Listener {
    public record Gem(String id, String name, String color, Material material, int index, Map<Integer, Map<String, Double>> stats) {}

    private final RPGLevelTier plugin;
    private final NamespacedKey gemKey;
    private final Map<String, Gem> gems = new LinkedHashMap<>();
    private final List<NamespacedKey> recipeKeys = new ArrayList<>();
    private int maxLevel = 3, combine = 3;
    private final Map<Integer, String> levelNames = new HashMap<>();

    public GemManager(RPGLevelTier plugin) {
        this.plugin = plugin;
        this.gemKey = new NamespacedKey(RPGLevelTier.KEY_NS, "rlv_gem");
        reload();
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void reload() {
        for (NamespacedKey k : recipeKeys) Bukkit.removeRecipe(k);
        recipeKeys.clear();
        gems.clear();
        YamlConfiguration cfg = DataFiles.loadMerged(plugin, "gems");
        maxLevel = Math.max(1, cfg.getInt("settings.max_level", 3));
        combine = Math.max(2, Math.min(9, cfg.getInt("settings.combine_amount", 3)));
        levelNames.clear();
        for (int i = 1; i <= maxLevel; i++) levelNames.put(i, cfg.getString("settings.level_names." + i, String.valueOf(i)));
        ConfigurationSection root = cfg.getConfigurationSection("gems");
        if (root != null) {
            int idx = 0;
            for (String id : root.getKeys(false)) {
                ConfigurationSection s = root.getConfigurationSection(id);
                if (s == null) continue;
                Material m = Material.matchMaterial(s.getString("material", "EMERALD"));
                Map<Integer, Map<String, Double>> stats = new HashMap<>();
                ConfigurationSection ss = s.getConfigurationSection("stats");
                if (ss != null) {
                    for (String lv : ss.getKeys(false)) {
                        ConfigurationSection l = ss.getConfigurationSection(lv);
                        if (l == null) continue;
                        Map<String, Double> sm = new LinkedHashMap<>();
                        for (String k : l.getKeys(false)) if (Stats.isKnown(k)) sm.put(k, l.getDouble(k));
                        stats.put(Integer.parseInt(lv), sm);
                    }
                }
                String key = id.toLowerCase(Locale.ROOT);
                gems.put(key, new Gem(key, s.getString("name", id), s.getString("color", "§f"), m == null ? Material.EMERALD : m, idx++, stats));
            }
        }
        registerRecipes();
        plugin.getLogger().info("[GemManager] Loaded " + gems.size() + " gems.");
    }

    private void registerRecipes() {
        for (Gem g : gems.values()) {
            for (int lv = 1; lv < maxLevel; lv++) {
                NamespacedKey key = new NamespacedKey(RPGLevelTier.KEY_NS, "gem_" + g.id() + "_" + lv);
                ShapelessRecipe r = new ShapelessRecipe(key, item(g.id(), lv + 1));
                RecipeChoice c = new RecipeChoice.ExactChoice(item(g.id(), lv));
                for (int i = 0; i < combine; i++) r.addIngredient(c);
                Bukkit.removeRecipe(key);
                if (Bukkit.addRecipe(r)) recipeKeys.add(key);
            }
        }
    }

    public void unregister() {
        for (NamespacedKey k : recipeKeys) Bukkit.removeRecipe(k);
        recipeKeys.clear();
    }

    public Collection<Gem> all() {
        return gems.values();
    }

    public Gem get(String id) {
        return id == null ? null : gems.get(id.toLowerCase(Locale.ROOT));
    }

    /** A random gem item of the given level, or null if no gems are defined. */
    public ItemStack randomItem(int level) {
        if (gems.isEmpty()) return null;
        List<Gem> list = new ArrayList<>(gems.values());
        return item(list.get(java.util.concurrent.ThreadLocalRandom.current().nextInt(list.size())).id(), level);
    }

    public int maxLevel() {
        return maxLevel;
    }

    public ItemStack item(String id, int level) {
        Gem g = gems.get(id.toLowerCase(Locale.ROOT));
        if (g == null) return null;
        int lv = Math.max(1, Math.min(maxLevel, level));
        ItemStack it = new ItemStack(g.material());
        ItemMeta meta = it.getItemMeta();
        meta.setDisplayName(g.color() + "§l" + g.name() + " " + levelNames.getOrDefault(lv, String.valueOf(lv)));
        List<String> lore = new ArrayList<>();
        lore.add("§7Gema de nivel §f" + lv);
        Map<String, Double> st = g.stats().getOrDefault(lv, Map.of());
        st.forEach((k, v) -> lore.add("§d◈ " + Stats.line(k, v)));
        lore.add("");
        lore.add("§eClic sobre un ítem con ranura en tu inventario");
        lore.add("§8" + combine + " iguales se combinan en la mesa de crafteo");
        meta.setLore(lore);
        meta.setCustomModelData(990000 + g.index() * 10 + lv);
        meta.addItemFlags(ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        meta.getPersistentDataContainer().set(gemKey, PersistentDataType.STRING, g.id() + ":" + lv);
        it.setItemMeta(meta);
        return it;
    }

    /** "ruby:2" of a gem item, or null. */
    public String tokenOf(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer().get(gemKey, PersistentDataType.STRING);
    }

    public Map<String, Double> statsOf(String token) {
        String[] p = token.split(":");
        Gem g = gems.get(p[0]);
        if (g == null) return Map.of();
        int lv = p.length > 1 ? Integer.parseInt(p[1]) : 1;
        return g.stats().getOrDefault(lv, Map.of());
    }

    /** "§cRubí I §7(+0.3 Daño)". */
    public String displayOf(String token) {
        String[] p = token.split(":");
        Gem g = gems.get(p[0]);
        if (g == null) return token;
        int lv = p.length > 1 ? Integer.parseInt(p[1]) : 1;
        StringBuilder sb = new StringBuilder(g.color()).append(g.name()).append(' ').append(levelNames.getOrDefault(lv, String.valueOf(lv)));
        Map<String, Double> st = g.stats().getOrDefault(lv, Map.of());
        if (!st.isEmpty()) {
            sb.append(" §7(");
            boolean first = true;
            for (Map.Entry<String, Double> e : st.entrySet()) {
                sb.append(first ? "" : ", ").append(Stats.line(e.getKey(), e.getValue()));
                first = false;
            }
            sb.append(')');
        }
        return sb.toString();
    }

    /** Sockets the gem into the first free socket of the item. Returns an error message or null on success. */
    public String socket(ItemStack target, String token) {
        if (target == null || !target.hasItemMeta()) return "Ese ítem no tiene ranuras.";
        ItemMeta meta = target.getItemMeta();
        int sockets = ItemData.sockets(meta);
        List<String> list = ItemData.gems(meta);
        if (sockets <= 0) return "Ese ítem no tiene ranuras.";
        if (list.size() >= sockets) return "Ese ítem no tiene ranuras libres.";
        list.add(token);
        ItemData.setGems(meta, list);
        target.setItemMeta(meta);
        ItemData.rebuildLore(target);
        return null;
    }

    /** Removes the gems of an item (they are destroyed). */
    public int clear(ItemStack target) {
        if (target == null || !target.hasItemMeta()) return 0;
        ItemMeta meta = target.getItemMeta();
        int n = ItemData.gems(meta).size();
        ItemData.setGems(meta, new ArrayList<>());
        target.setItemMeta(meta);
        ItemData.rebuildLore(target);
        return n;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player p)) return;
        if (event.getClick() != ClickType.LEFT) return;
        ItemStack cursor = event.getCursor();
        String token = tokenOf(cursor);
        if (token == null) return;
        ItemStack target = event.getCurrentItem();
        if (target == null || target.getType().isAir() || tokenOf(target) != null) return;
        if (event.getClickedInventory() == null || event.getClickedInventory().getType() != org.bukkit.event.inventory.InventoryType.PLAYER) return;
        if (target.getAmount() != 1) return;
        event.setCancelled(true);
        String err = socket(target, token);
        if (err != null) {
            p.sendMessage("§c" + err);
            return;
        }
        if (cursor.getAmount() <= 1) event.getView().setCursor(null); else cursor.setAmount(cursor.getAmount() - 1);
        event.setCurrentItem(target);
        p.playSound(p.getLocation(), org.bukkit.Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 1.2f);
        p.sendMessage("§dEngarzaste " + displayOf(token) + "§d.");
    }
}
