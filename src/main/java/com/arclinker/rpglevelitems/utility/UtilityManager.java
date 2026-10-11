package com.arclinker.rpglevelitems.utility;

import com.arclinker.rpglevelitems.DataFiles;
import com.arclinker.rpglevelitems.RPGLevelTier;
import com.arclinker.rpglevelitems.station.StationManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Objetos de utilidad de uso general (clic derecho): estaciones portátiles (vanilla y de forja RPG), mochilas mejorables,
 * bolsa de ender, bolsa de basura, kits de reparación, pergamino de regreso, bolsas de monedas y tomos de experiencia.
 * Los ítems se definen en {@code data/items/utility/utility.rlvp} y su comportamiento en {@code data/utility/utility.yml}.
 * El contenido de cada mochila se guarda en {@code plugins/RPGLeVeL/backpacks/<uuid>.yml} (el ítem solo lleva el uuid), así
 * una mejora conserva el contenido y no se envían datos pesados al cliente.
 */
public final class UtilityManager implements Listener {
    private record Def(String id, String type, ConfigurationSection cfg) {}

    private static final class BackpackHolder implements InventoryHolder {
        final UUID id;
        Inventory inv;
        BackpackHolder(UUID id) { this.id = id; }
        @Override public @NotNull Inventory getInventory() { return inv; }
    }

    private static final class TrashHolder implements InventoryHolder {
        Inventory inv;
        @Override public @NotNull Inventory getInventory() { return inv; }
    }

    private final RPGLevelTier plugin;
    private final NamespacedKey keyBag = new NamespacedKey(RPGLevelTier.KEY_NS, "bp_uuid");
    private final Map<String, Def> defs = new HashMap<>();
    private final TreeMap<Integer, String> bagByTier = new TreeMap<>();
    private final Map<UUID, BackpackHolder> open = new HashMap<>();
    private final Map<UUID, BukkitTask> recalls = new HashMap<>();
    private final Map<UUID, Long> recallCooldown = new HashMap<>();
    private boolean blockShulkers = true;
    private BukkitTask autosave;

    public UtilityManager(RPGLevelTier plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        defs.clear();
        bagByTier.clear();
        YamlConfiguration cfg = DataFiles.loadMerged(plugin, "utility");
        blockShulkers = cfg.getBoolean("settings.block_shulkers_in_backpack", true);
        ConfigurationSection root = cfg.getConfigurationSection("items");
        if (root != null) {
            for (String id : root.getKeys(false)) {
                ConfigurationSection s = root.getConfigurationSection(id);
                if (s == null) continue;
                String type = s.getString("type", "").toLowerCase(Locale.ROOT);
                defs.put(id.toLowerCase(Locale.ROOT), new Def(id.toLowerCase(Locale.ROOT), type, s));
                if (type.equals("backpack")) bagByTier.put(s.getInt("tier", 1), id.toLowerCase(Locale.ROOT));
            }
        }
        if (autosave != null) autosave.cancel();
        long period = Math.max(10, cfg.getInt("settings.autosave_seconds", 60)) * 20L;
        autosave = Bukkit.getScheduler().runTaskTimer(plugin, this::saveAll, period, period);
        plugin.getLogger().info("[Utility] " + defs.size() + " objetos de utilidad cargados.");
    }

    public void shutdown() {
        if (autosave != null) autosave.cancel();
        for (BukkitTask t : recalls.values()) t.cancel();
        recalls.clear();
        for (BackpackHolder h : new java.util.ArrayList<>(open.values())) {
            save(h.id, h.inv);
            for (org.bukkit.entity.HumanEntity v : new java.util.ArrayList<>(h.inv.getViewers())) v.closeInventory();
        }
        open.clear();
    }

    private void saveAll() {
        for (BackpackHolder h : open.values()) save(h.id, h.inv);
    }

    // ---- identificación -------------------------------------------------------------

    private Def def(ItemStack item) {
        if (item == null || item.getType().isAir() || plugin.getRlvpHandler() == null) return null;
        String id = plugin.getRlvpHandler().getId(item);
        return id == null ? null : defs.get(id.toLowerCase(Locale.ROOT));
    }

    private boolean forbiddenInBackpack(ItemStack it) {
        if (it == null || it.getType().isAir()) return false;
        Def d = def(it);
        if (d != null && d.type().equals("backpack")) return true;
        return blockShulkers && it.getType().name().endsWith("SHULKER_BOX");
    }

    // ---- uso por clic derecho ---------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        EquipmentSlot hand = e.getHand();
        if (hand == null || hand == EquipmentSlot.HEAD) return;
        Def d = def(e.getPlayer().getInventory().getItem(hand));
        if (d == null) return;
        e.setCancelled(true);
        Player p = e.getPlayer();
        if (!p.hasPermission("rpgleveltier.utility")) {
            p.sendMessage("§cNo tienes permiso para usar este objeto.");
            return;
        }
        use(p, hand, d);
    }

    /** Los objetos con forma de bloque nunca se colocan. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        if (def(e.getItemInHand()) != null) e.setCancelled(true);
    }

    private void use(Player p, EquipmentSlot hand, Def d) {
        ItemStack stack = p.getInventory().getItem(hand);
        switch (d.type()) {
            case "station" -> openVanilla(p, d.cfg().getString("arg", ""));
            case "rpg_station" -> openRpgStation(p, d.cfg().getString("arg", ""));
            case "backpack" -> openBackpack(p, stack, d);
            case "backpack_kit" -> {
                EquipmentSlot other = hand == EquipmentSlot.HAND ? EquipmentSlot.OFF_HAND : EquipmentSlot.HAND;
                ItemStack bag = p.getInventory().getItem(other);
                ItemStack up = upgradedBag(p, bag, d.cfg().getInt("tier", 2));
                if (up == null) return;
                p.getInventory().setItem(other, up);
                consume(p, hand);
                p.sendMessage("§a¡Mochila mejorada! §7Ahora es " + up.getItemMeta().getDisplayName() + "§7.");
                p.playSound(p.getLocation(), org.bukkit.Sound.BLOCK_SMITHING_TABLE_USE, 1f, 1.2f);
            }
            case "ender" -> p.openInventory(p.getEnderChest());
            case "trash" -> {
                TrashHolder h = new TrashHolder();
                h.inv = Bukkit.createInventory(h, 27, "§8Bolsa de Basura §c(se destruye al cerrar)");
                p.openInventory(h.inv);
            }
            case "repair" -> repair(p, hand, d.cfg().getInt("percent", 40));
            case "recall" -> recall(p, d);
            case "coins" -> {
                double amount = d.cfg().getDouble("amount", 0);
                plugin.getEconomy().deposit(p, amount);
                consume(p, hand);
                p.sendMessage("§6+" + plugin.getEconomy().format(amount) + " §7(bolsa de monedas).");
                p.playSound(p.getLocation(), org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f);
            }
            case "xp" -> {
                if (plugin.getPlayerStore().get(p).classId.isEmpty()) {
                    p.sendMessage("§cNecesitas una clase activa (/class) para usar este tomo.");
                    return;
                }
                long amount = d.cfg().getLong("amount", 0);
                plugin.getClassLevelManager().addClassXp(p, amount);
                consume(p, hand);
                p.sendMessage("§b+" + amount + " XP §7de clase.");
                p.playSound(p.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 1.4f);
            }
            default -> { }
        }
    }

    private void consume(Player p, EquipmentSlot hand) {
        ItemStack s = p.getInventory().getItem(hand);
        if (s == null) return;
        if (s.getAmount() <= 1) p.getInventory().setItem(hand, null);
        else s.setAmount(s.getAmount() - 1);
    }

    private void consumeById(Player p, String rlvpId) {
        PlayerInventory inv = p.getInventory();
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s == null || s.getType().isAir()) continue;
            String id = plugin.getRlvpHandler().getId(s);
            if (rlvpId.equalsIgnoreCase(id)) {
                if (s.getAmount() <= 1) inv.setItem(i, null);
                else s.setAmount(s.getAmount() - 1);
                return;
            }
        }
    }

    // ---- estaciones portátiles ---------------------------------------------------------

    /** Paper/Purpur: HumanEntity#openAnvil(Location, boolean) etc. (no están en la API de Spigot, de ahí la reflexión); si no existen, inventario virtual del tipo. */
    private void openVanilla(Player p, String arg) {
        String method = switch (arg) {
            case "workbench" -> "openWorkbench";
            case "enchanting" -> "openEnchanting";
            case "anvil" -> "openAnvil";
            case "smithing" -> "openSmithingTable";
            case "grindstone" -> "openGrindstone";
            case "stonecutter" -> "openStonecutter";
            case "loom" -> "openLoom";
            case "cartography" -> "openCartographyTable";
            default -> null;
        };
        if (method == null) {
            p.sendMessage("§cEstación desconocida: " + arg);
            return;
        }
        try {
            org.bukkit.entity.HumanEntity.class.getMethod(method, Location.class, boolean.class).invoke(p, null, true);
            return;
        } catch (ReflectiveOperationException ex) {
            // sin API de Paper: se usa el inventario virtual
        }
        org.bukkit.event.inventory.InventoryType type = switch (arg) {
            case "workbench" -> org.bukkit.event.inventory.InventoryType.WORKBENCH;
            case "enchanting" -> org.bukkit.event.inventory.InventoryType.ENCHANTING;
            case "anvil" -> org.bukkit.event.inventory.InventoryType.ANVIL;
            case "smithing" -> org.bukkit.event.inventory.InventoryType.SMITHING;
            case "grindstone" -> org.bukkit.event.inventory.InventoryType.GRINDSTONE;
            case "stonecutter" -> org.bukkit.event.inventory.InventoryType.STONECUTTER;
            case "loom" -> org.bukkit.event.inventory.InventoryType.LOOM;
            default -> org.bukkit.event.inventory.InventoryType.CARTOGRAPHY;
        };
        p.openInventory(Bukkit.createInventory(p, type));
    }

    private void openRpgStation(Player p, String arg) {
        StationManager sm = plugin.getStationManager();
        StationManager.Station st = switch (arg) {
            case "class" -> sm.ownStation(p, false);
            case "subclass" -> sm.ownStation(p, true);
            default -> sm.get(arg);
        };
        if (st == null) {
            p.sendMessage("§cNo tienes " + (arg.equals("subclass") ? "una subclase" : "una clase") + " con estación disponible (/class, /subclass).");
            return;
        }
        plugin.getStationGUI().open(p, st, 0, false);
    }

    // ---- mochilas ---------------------------------------------------------------------

    private UUID bagUuid(ItemStack bag, boolean create) {
        ItemMeta m = bag.getItemMeta();
        if (m == null) return null;
        String s = m.getPersistentDataContainer().get(keyBag, PersistentDataType.STRING);
        if (s != null) {
            try { return UUID.fromString(s); } catch (IllegalArgumentException ignored) { }
        }
        if (!create) return null;
        UUID id = UUID.randomUUID();
        m.getPersistentDataContainer().set(keyBag, PersistentDataType.STRING, id.toString());
        bag.setItemMeta(m);
        return id;
    }

    private void openBackpack(Player p, ItemStack bag, Def d) {
        if (bag == null) return;
        if (bag.getAmount() != 1) {
            p.sendMessage("§cSepara las mochilas: solo puedes abrir una a la vez.");
            return;
        }
        UUID id = bagUuid(bag, true);
        if (open.containsKey(id)) {
            p.sendMessage("§cEsta mochila ya está abierta.");
            return;
        }
        int slots = Math.max(9, Math.min(54, (d.cfg().getInt("slots", 9) + 8) / 9 * 9));
        BackpackHolder h = new BackpackHolder(id);
        ItemMeta m = bag.getItemMeta();
        String title = m != null && m.hasDisplayName() ? m.getDisplayName() : "§6Mochila";
        h.inv = Bukkit.createInventory(h, slots, title);
        load(id, h.inv);
        open.put(id, h);
        p.openInventory(h.inv);
        p.playSound(p.getLocation(), org.bukkit.Sound.ITEM_ARMOR_EQUIP_LEATHER, 1f, 1f);
    }

    /** Devuelve la mochila del siguiente nivel (mismo uuid, contenido intacto) o null avisando del motivo. */
    private ItemStack upgradedBag(Player p, ItemStack bag, int kitTier) {
        Def bd = def(bag);
        if (bd == null || !bd.type().equals("backpack")) {
            p.sendMessage("§cPon una §fmochila §cen la otra mano (o haz clic sobre ella con el kit).");
            return null;
        }
        int cur = bd.cfg().getInt("tier", 1);
        if (cur >= bagByTier.lastKey()) {
            p.sendMessage("§cEsa mochila ya está al nivel máximo.");
            return null;
        }
        if (kitTier != cur + 1) {
            p.sendMessage(kitTier <= cur ? "§cEsa mochila ya es de nivel igual o superior al del kit."
                    : "§cNecesitas el kit de mejora de nivel §f" + (cur + 1) + "§c para esta mochila.");
            return null;
        }
        UUID id = bagUuid(bag, false);
        if (id != null && open.containsKey(id)) {
            p.sendMessage("§cCierra la mochila antes de mejorarla.");
            return null;
        }
        ItemStack up = plugin.getRlvpHandler().getItem(bagByTier.get(kitTier));
        if (up == null) {
            p.sendMessage("§cNo se pudo crear la mochila nueva (revisa data/items/utility).");
            return null;
        }
        if (id != null) {
            ItemMeta m = up.getItemMeta();
            m.getPersistentDataContainer().set(keyBag, PersistentDataType.STRING, id.toString());
            up.setItemMeta(m);
        }
        return up;
    }

    private File file(UUID id) {
        return new File(plugin.getDataFolder(), "backpacks/" + id + ".yml");
    }

    private void load(UUID id, Inventory inv) {
        File f = file(id);
        if (!f.isFile()) return;
        ConfigurationSection s = YamlConfiguration.loadConfiguration(f).getConfigurationSection("items");
        if (s == null) return;
        for (String k : s.getKeys(false)) {
            try {
                int slot = Integer.parseInt(k);
                ItemStack it = s.getItemStack(k);
                if (it != null && slot >= 0 && slot < inv.getSize()) inv.setItem(slot, it);
            } catch (NumberFormatException ignored) { }
        }
    }

    private void save(UUID id, Inventory inv) {
        YamlConfiguration y = new YamlConfiguration();
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack it = inv.getItem(i);
            if (it != null && !it.getType().isAir()) y.set("items." + i, it);
        }
        try {
            File f = file(id);
            f.getParentFile().mkdirs();
            y.save(f);
        } catch (IOException ex) {
            plugin.getLogger().severe("[Utility] No se pudo guardar la mochila " + id + ": " + ex.getMessage());
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        InventoryHolder h = e.getInventory().getHolder();
        if (h instanceof BackpackHolder bh) {
            save(bh.id, bh.inv);
            if (bh.inv.getViewers().size() <= 1) open.remove(bh.id);
            if (e.getPlayer() instanceof Player p) p.playSound(p.getLocation(), org.bukkit.Sound.ITEM_ARMOR_EQUIP_LEATHER, 1f, 0.8f);
        } else if (h instanceof TrashHolder th) {
            int n = 0;
            for (ItemStack it : th.inv.getContents()) if (it != null && !it.getType().isAir()) n += it.getAmount();
            th.inv.clear();
            if (n > 0 && e.getPlayer() instanceof Player p) p.sendMessage("§8Destruiste §f" + n + " §8objetos.");
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        Inventory top = e.getView().getTopInventory();
        boolean inBag = top.getHolder() instanceof BackpackHolder;
        ItemStack current = e.getCurrentItem();
        ItemStack cursor = e.getCursor();

        // mejorar: kit en el cursor sobre una mochila
        Def kit = def(cursor);
        if (kit != null && kit.type().equals("backpack_kit") && current != null && !inBag && e.getClickedInventory() != null) {
            Def target = def(current);
            if (target != null && target.type().equals("backpack")) {
                e.setCancelled(true);
                ItemStack up = upgradedBag(p, current, kit.cfg().getInt("tier", 2));
                if (up != null) {
                    e.setCurrentItem(up);
                    cursor.setAmount(cursor.getAmount() - 1);
                    p.setItemOnCursor(cursor.getAmount() > 0 ? cursor : null);
                    p.sendMessage("§a¡Mochila mejorada! §7Ahora es " + up.getItemMeta().getDisplayName() + "§7.");
                    p.playSound(p.getLocation(), org.bukkit.Sound.BLOCK_SMITHING_TABLE_USE, 1f, 1.2f);
                }
                return;
            }
        }

        // abrir una mochila con clic derecho sobre ella en el inventario
        if (!inBag && e.getClick() == ClickType.RIGHT && (cursor == null || cursor.getType().isAir()) && current != null
                && e.getClickedInventory() == p.getInventory()) {
            Def d = def(current);
            if (d != null && d.type().equals("backpack")) {
                e.setCancelled(true);
                Bukkit.getScheduler().runTask(plugin, () -> openBackpack(p, current, d));
                return;
            }
        }

        if (!inBag) return;
        // dentro de una mochila: nada de mochilas ni shulkers
        Inventory clicked = e.getClickedInventory();
        if (clicked == null) return;
        boolean deny = false;
        if (clicked == top) {
            if (forbiddenInBackpack(cursor)) deny = true;
            if (e.getClick() == ClickType.NUMBER_KEY && e.getHotbarButton() >= 0 && forbiddenInBackpack(p.getInventory().getItem(e.getHotbarButton()))) deny = true;
            if (e.getClick() == ClickType.SWAP_OFFHAND && forbiddenInBackpack(p.getInventory().getItemInOffHand())) deny = true;
        } else if (e.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY && forbiddenInBackpack(current)) {
            deny = true;
        }
        if (deny) {
            e.setCancelled(true);
            p.sendMessage("§cNo puedes guardar mochilas ni shulkers dentro de una mochila.");
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDrag(InventoryDragEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof BackpackHolder)) return;
        if (!forbiddenInBackpack(e.getOldCursor())) return;
        int size = e.getView().getTopInventory().getSize();
        for (int raw : e.getRawSlots()) if (raw < size) { e.setCancelled(true); return; }
    }

    // ---- reparar ----------------------------------------------------------------------

    private void repair(Player p, EquipmentSlot hand, int percent) {
        PlayerInventory inv = p.getInventory();
        int fixed = 0;
        ItemStack[] armor = inv.getArmorContents();
        for (int i = 0; i < armor.length; i++) if (repairOne(armor[i], percent)) fixed++;
        inv.setArmorContents(armor);
        EquipmentSlot other = hand == EquipmentSlot.HAND ? EquipmentSlot.OFF_HAND : EquipmentSlot.HAND;
        ItemStack side = inv.getItem(other);
        if (repairOne(side, percent)) { inv.setItem(other, side); fixed++; }
        if (fixed == 0) {
            p.sendMessage("§7Nada que reparar: lleva puesta tu armadura dañada o sostén el objeto en la otra mano.");
            return;
        }
        consume(p, hand);
        p.sendMessage("§aReparaste §f" + fixed + " §apieza(s) (" + percent + "%).");
        p.playSound(p.getLocation(), org.bukkit.Sound.BLOCK_ANVIL_USE, 0.6f, 1.4f);
    }

    private boolean repairOne(ItemStack it, int percent) {
        if (it == null || it.getType().isAir() || it.getType().getMaxDurability() <= 0) return false;
        if (!(it.getItemMeta() instanceof Damageable dm) || dm.isUnbreakable() || dm.getDamage() <= 0) return false;
        int max = it.getType().getMaxDurability();
        dm.setDamage(Math.max(0, dm.getDamage() - (int) Math.ceil(max * percent / 100.0)));
        it.setItemMeta(dm);
        return true;
    }

    // ---- pergamino de regreso ---------------------------------------------------------

    private void recall(Player p, Def d) {
        UUID u = p.getUniqueId();
        if (recalls.containsKey(u)) {
            p.sendMessage("§cYa te estás teletransportando.");
            return;
        }
        long now = System.currentTimeMillis();
        long until = recallCooldown.getOrDefault(u, 0L);
        if (until > now) {
            p.sendMessage("§cEnfriamiento: §f" + ((until - now) / 1000 + 1) + " s§c.");
            return;
        }
        int warmup = Math.max(1, d.cfg().getInt("warmup", 5));
        int cooldown = Math.max(0, d.cfg().getInt("cooldown", 30));
        Location start = p.getLocation().clone();
        p.sendMessage("§dTeletransporte en §f" + warmup + " s§d. No te muevas ni recibas daño.");
        final int[] ticks = {0};
        BukkitTask t = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!p.isOnline()) { cancelRecall(u, null); return; }
            Location l = p.getLocation();
            if (l.getWorld() != start.getWorld() || l.distanceSquared(start) > 0.5) { cancelRecall(u, p); return; }
            ticks[0] += 5;
            if (ticks[0] % 20 == 0) p.spawnParticle(org.bukkit.Particle.PORTAL, l.clone().add(0, 1, 0), 30, 0.4, 0.6, 0.4, 0.2);
            if (ticks[0] >= warmup * 20) {
                cancelRecall(u, null);
                Location dest = p.getBedSpawnLocation();
                if (dest == null) dest = Bukkit.getWorlds().get(0).getSpawnLocation();
                consumeById(p, d.id());
                recallCooldown.put(u, System.currentTimeMillis() + cooldown * 1000L);
                p.teleport(dest);
                p.playSound(dest, org.bukkit.Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1f);
                p.sendMessage("§d¡Regresaste!");
            }
        }, 5L, 5L);
        recalls.put(u, t);
    }

    private void cancelRecall(UUID u, Player notify) {
        BukkitTask t = recalls.remove(u);
        if (t != null) t.cancel();
        if (notify != null) notify.sendMessage("§cTeletransporte cancelado.");
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageEvent e) {
        if (e.getEntity() instanceof Player p && recalls.containsKey(p.getUniqueId())) cancelRecall(p.getUniqueId(), p);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        cancelRecall(e.getPlayer().getUniqueId(), null);
    }
}
