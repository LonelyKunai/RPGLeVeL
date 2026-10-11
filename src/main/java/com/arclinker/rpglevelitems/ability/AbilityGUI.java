package com.arclinker.rpglevelitems.ability;

import com.arclinker.rpglevelitems.RPGLevelTier;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * Ability tree menu. Rows 0-4 show the nodes at their (row, col) position; the bottom row has the
 * class / subclass tabs and the point + ability XP counters.
 * Left click: unlock (skill points) or level up (ability XP). Right / shift click: copy to a tome.
 */
public final class AbilityGUI implements Listener {
    private static final int TAB_CLASS = 45, TAB_SUB = 46, SLOT_POINTS = 48, SLOT_XP = 49, SLOT_STATS = 50, SLOT_HELP = 52, SLOT_CLOSE = 53;

    private final RPGLevelTier plugin;

    public AbilityGUI(RPGLevelTier plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    private static final class Holder implements InventoryHolder {
        final String treeKey;
        final boolean sub;
        final Map<Integer, String> nodes = new HashMap<>();
        Inventory inventory;

        Holder(String treeKey, boolean sub) {
            this.treeKey = treeKey;
            this.sub = sub;
        }

        @Override
        public @NotNull Inventory getInventory() {
            return inventory;
        }
    }

    public void open(Player p, boolean subTab) {
        AbilityManager am = plugin.getAbilityManager();
        var rec = plugin.getPlayerStore().get(p);
        String id = subTab ? rec.subclassId : rec.classId;
        if (id.isEmpty()) {
            p.sendMessage(subTab ? "§cNo tienes subclase. Usa /subclass." : "§cNo tienes clase. Usa /class.");
            return;
        }
        String tree = (subTab ? "sub:" : "class:") + id;
        String title = subTab ? plugin.getSubClassManager().getInfo(id).name : plugin.getClassManager().getClassInfo(id).name;
        Holder holder = new Holder(tree, subTab);
        Inventory inv = Bukkit.createInventory(holder, 54, "§5§lHabilidades §8- §f" + title);
        holder.inventory = inv;

        ItemStack empty = item(Material.BLACK_STAINED_GLASS_PANE, " ", List.of());
        for (int i = 0; i < 54; i++) inv.setItem(i, empty);

        for (AbilityDef d : am.tree(tree)) {
            int slot = d.row * 9 + d.col;
            if (slot < 0 || slot >= 45) continue;
            holder.nodes.put(slot, d.id);
            inv.setItem(slot, nodeItem(p, d));
        }

        inv.setItem(TAB_CLASS, item(Material.IRON_SWORD, (subTab ? "§7" : "§a▶ ") + "Árbol de clase", List.of("§eClic para ver")));
        inv.setItem(TAB_SUB, item(Material.NETHER_STAR, (subTab ? "§a▶ " : "§7") + "Árbol de subclase", List.of("§eClic para ver")));
        inv.setItem(SLOT_POINTS, item(Material.EMERALD, "§aPuntos de habilidad: " + am.availablePoints(p, tree),
                List.of("§7Totales: §f" + am.totalPoints(p, tree) + " §7| Gastados: §f" + am.spentPoints(p, tree),
                        "§7Ganas 1 punto cada §f" + am.pointEvery() + " §7niveles.")));
        inv.setItem(SLOT_XP, item(Material.EXPERIENCE_BOTTLE, "§dAbility XP: " + am.abilityXp(p, tree),
                List.of("§7Ganas §d" + am.xpPerLevel() + " §7por cada nivel de la " + (subTab ? "subclase" : "clase") + ".",
                        "§7Úsala para subir el nivel de tus habilidades.")));
        List<String> statLines = new ArrayList<>();
        for (String l : plugin.getClassLevelManager().describe(p, subTab)) statLines.add(l);
        inv.setItem(SLOT_STATS, item(Material.PLAYER_HEAD, "§6Nivel y estadísticas", statLines));
        inv.setItem(SLOT_HELP, item(Material.BOOK, "§bCómo funciona", List.of(
                "§eClic izq. §7en gris/amarillo: desbloquear",
                "§eClic izq. §7en verde: subir nivel (ability XP)",
                "§eClic der. / shift §7en verde: copiar a un tomo",
                "§7El tomo solo funciona si la tienes desbloqueada.")));
        inv.setItem(SLOT_CLOSE, item(Material.BARRIER, "§cCerrar", List.of()));
        p.openInventory(inv);
    }

    private ItemStack item(Material mat, String name, List<String> lore) {
        ItemStack it = new ItemStack(mat);
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(new ArrayList<>(lore));
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP, ItemFlag.HIDE_ENCHANTS);
            it.setItemMeta(meta);
        }
        return it;
    }

    private ItemStack nodeItem(Player p, AbilityDef d) {
        AbilityManager am = plugin.getAbilityManager();
        int level = am.levelOf(p, d.id);
        boolean unlocked = level > 0;
        String blocker = unlocked ? null : am.unlockBlocker(p, d);
        String color = unlocked ? "§a" : blocker == null ? "§e" : "§c";

        List<String> lore = new ArrayList<>();
        lore.add("§7" + d.description);
        lore.add("");
        int shown = Math.max(1, level);
        lore.addAll(summary(d, shown));
        lore.add("§7Enfriamiento: §f" + String.format(Locale.ROOT, "%.1f", d.cooldownAt(shown)) + "s");
        lore.add("");
        if (unlocked) {
            lore.add("§7Nivel: §e" + level + "§7/§e" + d.maxLevel);
            if (level < d.maxLevel) {
                long cost = am.upgradeCost(level);
                lore.add("§7Siguiente nivel: §d" + cost + " ability XP §7(tienes " + am.abilityXp(p, d.treeKey()) + ")");
                lore.add("§eClic izq.: subir nivel");
            } else {
                lore.add("§6Nivel máximo");
            }
            lore.add("§eClic der. / shift: copiar a un tomo");
        } else {
            lore.add("§7Coste: §a" + d.cost + " punto(s) §7| Nivel requerido: §f" + d.requiredLevel);
            for (Map.Entry<String, Integer> r : d.requiredAbilities.entrySet()) {
                AbilityDef rd = am.get(r.getKey());
                boolean ok = am.levelOf(p, r.getKey()) >= r.getValue();
                lore.add((ok ? "§a✔ " : "§c✘ ") + "§7Requiere " + (rd != null ? rd.name : r.getKey()) + " nv. " + r.getValue());
            }
            if (blocker == null) lore.add("§eClic izq.: desbloquear");
            else lore.add("§c" + blocker);
        }
        ItemStack it = item(d.icon, color + d.name + (unlocked ? " §7[Nv " + level + "]" : ""), lore);
        if (unlocked) {
            ItemMeta meta = it.getItemMeta();
            meta.setEnchantmentGlintOverride(true);
            it.setItemMeta(meta);
        }
        return it;
    }

    /** Key numbers of the effect at the given level. */
    private List<String> summary(AbilityDef d, int level) {
        Map<String, Object> e = d.effect;
        List<String> out = new ArrayList<>();
        double dmg = AbilityEffects.num(e, "damage", level, 0);
        if (dmg > 0) out.add("§7Daño: §c" + fmt(dmg));
        double heal = AbilityEffects.num(e, "amount", level, 0);
        if (heal > 0) out.add("§7Curación: §a" + fmt(heal));
        double radius = AbilityEffects.num(e, "radius", level, 0);
        if (radius > 0) out.add("§7Radio: §f" + fmt(radius));
        double range = AbilityEffects.num(e, "range", level, 0);
        if (range > 0 && !"aoe".equals(e.get("type"))) out.add("§7Alcance: §f" + fmt(range));
        double dist = AbilityEffects.num(e, "distance", level, 0);
        if (dist > 0) out.add("§7Distancia: §f" + fmt(dist));
        double arrows = AbilityEffects.num(e, "arrows", level, 0);
        if (arrows > 0) out.add("§7Proyectiles: §f" + (int) arrows);
        double targets = AbilityEffects.num(e, "targets", level, 0);
        if (targets > 0) out.add("§7Objetivos: §f" + (int) targets);
        Object pot = e.get("potions");
        if (pot instanceof List<?> l && !l.isEmpty()) {
            double extra = e.get("duration_per_level") instanceof Number n ? n.doubleValue() * (level - 1) : 0;
            StringBuilder sb = new StringBuilder("§7Efectos: §f");
            for (int i = 0; i < l.size(); i++) {
                String[] parts = l.get(i).toString().split(":");
                double secs = (parts.length > 1 ? parseD(parts[1]) : 5) + extra;
                sb.append(parts[0].replace('_', ' ')).append(parts.length > 2 ? " " + (parseInt(parts[2]) + 1) : "")
                  .append(" (").append(fmt(secs)).append("s)").append(i + 1 < l.size() ? ", " : "");
            }
            out.add(sb.toString());
        }
        double self = AbilityEffects.num(e, "self_damage", level, 0);
        if (self > 0) out.add("§7Coste de vida: §c" + fmt(self));
        return out;
    }

    private static double parseD(String s) {
        try { return Double.parseDouble(s); } catch (NumberFormatException ex) { return 5; }
    }

    private static int parseInt(String s) {
        try { return (int) Double.parseDouble(s); } catch (NumberFormatException ex) { return 0; }
    }

    private static String fmt(double v) {
        return v == Math.floor(v) ? String.valueOf((long) v) : String.format(Locale.ROOT, "%.1f", v);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Holder) event.setCancelled(true);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof Holder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player p)) return;
        if (event.getClickedInventory() != event.getView().getTopInventory()) return;
        int slot = event.getRawSlot();

        if (slot == TAB_CLASS) { open(p, false); return; }
        if (slot == TAB_SUB) { open(p, true); return; }
        if (slot == SLOT_CLOSE) { p.closeInventory(); return; }

        String id = holder.nodes.get(slot);
        if (id == null) return;
        AbilityDef d = plugin.getAbilityManager().get(id);
        if (d == null) return;
        AbilityManager am = plugin.getAbilityManager();
        ClickType click = event.getClick();
        if (am.isUnlocked(p, id) && (click.isRightClick() || click.isShiftClick())) {
            giveTome(p, d);
            return;
        }
        if (am.isUnlocked(p, id)) am.upgrade(p, d); else am.unlock(p, d);
        open(p, holder.sub);
    }

    /** Gives a tome copy of an unlocked ability. */
    public void giveTome(Player p, AbilityDef d) {
        if (!p.hasPermission("rpgleveltier.ability.copy")) {
            p.sendMessage("§cNo tienes permiso para copiar habilidades.");
            return;
        }
        if (!plugin.getAbilityManager().isUnlocked(p, d.id)) {
            p.sendMessage("§cSolo puedes copiar habilidades que hayas desbloqueado.");
            return;
        }
        ItemStack tome = plugin.getAbilityItems().create(d);
        Map<Integer, ItemStack> left = p.getInventory().addItem(tome);
        if (!left.isEmpty()) {
            p.sendMessage("§cNo hay espacio en el inventario.");
            return;
        }
        p.sendMessage("§dCopiaste §f" + d.name + " §da un tomo. §7Clic derecho para usarlo.");
    }
}
