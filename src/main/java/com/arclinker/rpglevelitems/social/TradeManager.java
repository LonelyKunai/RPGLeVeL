package com.arclinker.rpglevelitems.social;

import com.arclinker.rpglevelitems.RPGLevelTier;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * Safe player-to-player trading. One shared 54-slot inventory: left half = player A's offer, right half = B's.
 * Items move only through clicks handled here (all vanilla inventory actions are cancelled), so nothing can be
 * duplicated. Both players must confirm; any change resets confirmations; closing cancels and returns items.
 */
public final class TradeManager implements Listener {
    private static final int[] A_SLOTS, B_SLOTS;
    // bottom row: items AND money are offered together. A: 45 [-] 46 [amount] 47 [+] 48 [confirm] | 49 info | B: 50 [confirm] 51 [-] 52 [amount] 53 [+]
    private static final int A_MINUS = 45, A_AMOUNT = 46, A_PLUS = 47, A_CONFIRM = 48, INFO = 49, B_CONFIRM = 50, B_MINUS = 51, B_AMOUNT = 52, B_PLUS = 53;
    private static final long[] STEPS = {1, 10, 100, 1000, 10000, 100000};

    static {
        A_SLOTS = new int[20];
        B_SLOTS = new int[20];
        int a = 0, b = 0;
        for (int row = 0; row < 5; row++) {
            for (int col = 0; col < 4; col++) {
                A_SLOTS[a++] = row * 9 + col;
                B_SLOTS[b++] = row * 9 + 5 + col;
            }
        }
    }

    private final class Trade implements InventoryHolder {
        final Player a, b;
        boolean confirmA, confirmB, done;
        Inventory inv;
        int countdown = -1;
        long moneyA, moneyB;
        int stepA = 2, stepB = 2; // index in STEPS (default 100)

        Trade(Player a, Player b) {
            this.a = a;
            this.b = b;
        }

        @Override
        public @NotNull Inventory getInventory() {
            return inv;
        }

        int[] slotsOf(Player p) {
            return p == a ? A_SLOTS : B_SLOTS;
        }
    }

    private final RPGLevelTier plugin;
    private final Map<UUID, UUID> requests = new HashMap<>(); // target -> from
    private final Map<UUID, Trade> trades = new HashMap<>();

    public TradeManager(RPGLevelTier plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public String request(Player from, Player to) {
        if (from == to) return "No puedes comerciar contigo.";
        if (trades.containsKey(from.getUniqueId()) || trades.containsKey(to.getUniqueId())) return "Alguno ya está en un intercambio.";
        if (from.getWorld() != to.getWorld() || from.getLocation().distanceSquared(to.getLocation()) > 30 * 30) return "Deben estar cerca (30 bloques).";
        requests.put(to.getUniqueId(), from.getUniqueId());
        to.sendMessage("§6" + from.getName() + " quiere intercambiar contigo. §e/trade accept");
        Bukkit.getScheduler().runTaskLater(plugin, () -> requests.remove(to.getUniqueId(), from.getUniqueId()), 20L * 30);
        return null;
    }

    public String accept(Player to) {
        UUID f = requests.remove(to.getUniqueId());
        Player from = f == null ? null : Bukkit.getPlayer(f);
        if (from == null) return "No tienes solicitudes.";
        if (trades.containsKey(from.getUniqueId()) || trades.containsKey(to.getUniqueId())) return "Alguno ya está en un intercambio.";
        Trade t = new Trade(from, to);
        t.inv = Bukkit.createInventory(t, 54, "§6Intercambio §8- " + from.getName() + " ⇄ " + to.getName());
        trades.put(from.getUniqueId(), t);
        trades.put(to.getUniqueId(), t);
        ItemStack pane = named(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 54; i++) t.inv.setItem(i, pane);
        for (int s : A_SLOTS) t.inv.setItem(s, null);
        for (int s : B_SLOTS) t.inv.setItem(s, null);
        refresh(t);
        from.openInventory(t.inv);
        to.openInventory(t.inv);
        return null;
    }

    private static ItemStack named(Material m, String name, String... lore) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        meta.setDisplayName(name);
        if (lore.length > 0) meta.setLore(Arrays.asList(lore));
        it.setItemMeta(meta);
        return it;
    }

    private void refresh(Trade t) {
        t.inv.setItem(A_CONFIRM, named(t.confirmA ? Material.LIME_CONCRETE : Material.RED_CONCRETE, (t.confirmA ? "§a✔ " : "§c✘ ") + t.a.getName(), "§7Clic (solo " + t.a.getName() + ") para confirmar"));
        t.inv.setItem(B_CONFIRM, named(t.confirmB ? Material.LIME_CONCRETE : Material.RED_CONCRETE, (t.confirmB ? "§a✔ " : "§c✘ ") + t.b.getName(), "§7Clic (solo " + t.b.getName() + ") para confirmar"));
        t.inv.setItem(INFO, named(Material.PAPER, "§6Intercambio", "§7Clic en un ítem de tu inventario para ofrecerlo.", "§7Clic en tu oferta para retirarlo.",
                "§7Abajo: ofrece dinero a la vez que ítems (− / + y clic en el monto cambia el paso).", "§7Ambos deben confirmar."));
        moneyButtons(t, true);
        moneyButtons(t, false);
        for (int s : new int[]{4, 13, 22, 31, 40}) t.inv.setItem(s, named(Material.BLACK_STAINED_GLASS_PANE, " "));
    }

    private void moneyButtons(Trade t, boolean isA) {
        long money = isA ? t.moneyA : t.moneyB;
        long step = STEPS[isA ? t.stepA : t.stepB];
        String owner = (isA ? t.a : t.b).getName();
        var eco = plugin.getEconomy();
        t.inv.setItem(isA ? A_MINUS : B_MINUS, named(Material.RED_STAINED_GLASS_PANE, "§c− " + step, "§7Resta " + step + " a la oferta de " + owner));
        t.inv.setItem(isA ? A_PLUS : B_PLUS, named(Material.LIME_STAINED_GLASS_PANE, "§a+ " + step, "§7Suma " + step + " a la oferta de " + owner));
        t.inv.setItem(isA ? A_AMOUNT : B_AMOUNT, named(Material.GOLD_INGOT, "§6Dinero de " + owner + ": §e" + eco.format(money),
                "§7Paso actual: §f" + step, "§7Clic aquí cambia el paso (1, 10, 100, 1000...)", "§7Clic derecho: poner a 0"));
    }

    private void resetConfirm(Trade t) {
        t.confirmA = false;
        t.confirmB = false;
        t.countdown = -1;
        refresh(t);
    }

    private boolean isMine(Trade t, Player p, int slot) {
        for (int s : t.slotsOf(p)) if (s == slot) return true;
        return false;
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof Trade t)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p) || t.done) return;
        if (p != t.a && p != t.b) return;
        Inventory top = e.getView().getTopInventory();
        if (e.getClickedInventory() == top) {
            int slot = e.getRawSlot();
            // money controls (each player edits only their own side)
            boolean isA = p == t.a;
            if ((isA && (slot == A_MINUS || slot == A_PLUS || slot == A_AMOUNT)) || (!isA && (slot == B_MINUS || slot == B_PLUS || slot == B_AMOUNT))) {
                int stepIdx = isA ? t.stepA : t.stepB;
                long money = isA ? t.moneyA : t.moneyB;
                if (slot == A_AMOUNT || slot == B_AMOUNT) {
                    if (e.isRightClick()) money = 0;
                    else stepIdx = (stepIdx + 1) % STEPS.length;
                } else {
                    long delta = STEPS[stepIdx] * (slot == A_PLUS || slot == B_PLUS ? 1 : -1);
                    money = Math.max(0, money + delta);
                    double bal = plugin.getEconomy().balance(p.getUniqueId());
                    if (money > bal) {
                        money = (long) Math.floor(bal);
                        p.sendMessage("§cNo tienes más dinero para ofrecer.");
                    }
                }
                if (isA) { t.stepA = stepIdx; t.moneyA = money; } else { t.stepB = stepIdx; t.moneyB = money; }
                resetConfirm(t);
                return;
            }
            if (slot == A_CONFIRM && p == t.a || slot == B_CONFIRM && p == t.b) {
                if (!moneyOk(t, p)) {
                    p.sendMessage("§cYa no tienes el dinero que ofreces.");
                    return;
                }
                if (p == t.a) t.confirmA = !t.confirmA; else t.confirmB = !t.confirmB;
                refresh(t);
                if (t.confirmA && t.confirmB) startCountdown(t);
                else t.countdown = -1;
                return;
            }
            if (isMine(t, p, slot)) {
                ItemStack it = top.getItem(slot);
                if (it == null || it.getType().isAir()) return;
                Map<Integer, ItemStack> rest = p.getInventory().addItem(it.clone());
                if (rest.isEmpty()) {
                    top.setItem(slot, null);
                    resetConfirm(t);
                } else {
                    // only part fitted: keep the remainder in the offer so nothing is duplicated
                    top.setItem(slot, rest.values().iterator().next());
                    resetConfirm(t);
                    p.sendMessage("§cTu inventario está lleno.");
                }
            }
        } else if (e.getClickedInventory() == p.getInventory()) {
            ItemStack it = e.getCurrentItem();
            if (it == null || it.getType().isAir()) return;
            for (int s : t.slotsOf(p)) {
                if (top.getItem(s) == null) {
                    top.setItem(s, it.clone());
                    e.setCurrentItem(null);
                    resetConfirm(t);
                    return;
                }
            }
            p.sendMessage("§cTu oferta está llena.");
        }
    }

    private void startCountdown(Trade t) {
        t.countdown = 3;
        for (Player p : List.of(t.a, t.b)) p.sendMessage("§eAmbos confirmaron. Completando en 3 segundos (cambia algo para cancelar)...");
        Bukkit.getScheduler().runTaskLater(plugin, () -> step(t), 20L);
    }

    private void step(Trade t) {
        if (t.done || t.countdown < 0 || !(t.confirmA && t.confirmB)) return;
        if (--t.countdown > 0) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> step(t), 20L);
            return;
        }
        complete(t);
    }

    private boolean moneyOk(Trade t, Player p) {
        long m = p == t.a ? t.moneyA : t.moneyB;
        return m <= 0 || plugin.getEconomy().has(p, m);
    }

    private void complete(Trade t) {
        // money first: both must still have it; transfer atomically (rolled back if the second withdraw fails)
        if (!moneyOk(t, t.a) || !moneyOk(t, t.b)) {
            resetConfirm(t);
            for (Player p : List.of(t.a, t.b)) p.sendMessage("§cAlguien ya no tiene el dinero ofrecido; confirmen de nuevo.");
            return;
        }
        var eco = plugin.getEconomy();
        double tax = plugin.getConfig().getDouble("trade.money_tax", 0.0);
        if (t.moneyA > 0 && !eco.withdraw(t.a, t.moneyA)) {
            resetConfirm(t);
            return;
        }
        if (t.moneyB > 0 && !eco.withdraw(t.b, t.moneyB)) {
            if (t.moneyA > 0) eco.deposit(t.a, t.moneyA);
            resetConfirm(t);
            return;
        }
        if (t.moneyA > 0) eco.deposit(t.b, Math.floor(t.moneyA * (1 - tax)));
        if (t.moneyB > 0) eco.deposit(t.a, Math.floor(t.moneyB * (1 - tax)));
        t.done = true;
        List<ItemStack> fromA = new ArrayList<>(), fromB = new ArrayList<>();
        for (int s : A_SLOTS) if (t.inv.getItem(s) != null) fromA.add(t.inv.getItem(s));
        for (int s : B_SLOTS) if (t.inv.getItem(s) != null) fromB.add(t.inv.getItem(s));
        for (int s : A_SLOTS) t.inv.setItem(s, null);
        for (int s : B_SLOTS) t.inv.setItem(s, null);
        trades.remove(t.a.getUniqueId());
        trades.remove(t.b.getUniqueId());
        give(t.b, fromA);
        give(t.a, fromB);
        t.a.closeInventory();
        t.b.closeInventory();
        t.a.sendMessage("§a¡Intercambio completado!");
        t.b.sendMessage("§a¡Intercambio completado!");
        if (plugin.getAchievements() != null) {
            plugin.getAchievements().add(t.a, "trades", 1);
            plugin.getAchievements().add(t.b, "trades", 1);
        }
    }

    private void give(Player p, List<ItemStack> items) {
        for (ItemStack it : items) for (ItemStack r : p.getInventory().addItem(it).values()) p.getWorld().dropItemNaturally(p.getLocation(), r);
    }

    /** Cancels the trade and returns every offered item to its owner. */
    private void cancel(Trade t, String why) {
        if (t.done) return;
        t.done = true;
        List<ItemStack> a = new ArrayList<>(), b = new ArrayList<>();
        for (int s : A_SLOTS) if (t.inv.getItem(s) != null) a.add(t.inv.getItem(s));
        for (int s : B_SLOTS) if (t.inv.getItem(s) != null) b.add(t.inv.getItem(s));
        for (int s : A_SLOTS) t.inv.setItem(s, null);
        for (int s : B_SLOTS) t.inv.setItem(s, null);
        trades.remove(t.a.getUniqueId());
        trades.remove(t.b.getUniqueId());
        give(t.a, a);
        give(t.b, b);
        t.a.sendMessage("§cIntercambio cancelado" + (why.isEmpty() ? "." : ": " + why));
        t.b.sendMessage("§cIntercambio cancelado" + (why.isEmpty() ? "." : ": " + why));
        t.a.closeInventory();
        t.b.closeInventory();
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof Trade) e.setCancelled(true);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (e.getInventory().getHolder() instanceof Trade t && !t.done) {
            String name = e.getPlayer().getName();
            Bukkit.getScheduler().runTask(plugin, () -> cancel(t, name + " cerró la ventana."));
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Trade t = trades.get(e.getPlayer().getUniqueId());
        if (t != null) cancel(t, e.getPlayer().getName() + " se desconectó.");
        requests.remove(e.getPlayer().getUniqueId());
    }

    public void cancelAll() {
        for (Trade t : new HashSet<>(trades.values())) cancel(t, "el servidor se detiene.");
    }
}
