package com.arclinker.rpglevelitems.economy;

import com.arclinker.rpglevelitems.RPGLevelTier;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.lang.reflect.Method;
import java.util.UUID;

/**
 * Economy facade. Uses Vault (through reflection, no compile dependency) when present and
 * {@code economy.mode} is auto/vault; otherwise an internal currency ("monedas") stored in the
 * player record.
 */
public final class EconomyManager {
    private final RPGLevelTier plugin;
    private Object vault;
    private Method mBalance, mHas, mWithdraw, mDeposit, mSuccess;
    private boolean vaultChecked;

    public EconomyManager(RPGLevelTier plugin) {
        this.plugin = plugin;
    }

    private String mode() {
        return plugin.getConfig().getString("economy.mode", "auto").toLowerCase();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private boolean vault() {
        if (!mode().equals("auto") && !mode().equals("vault")) return false;
        if (vaultChecked) return vault != null;
        if (Bukkit.getPluginManager().getPlugin("Vault") == null) return false; // may load later: do not cache
        try {
            Class<?> eco = Class.forName("net.milkbowl.vault.economy.Economy");
            RegisteredServiceProvider rsp = Bukkit.getServicesManager().getRegistration((Class) eco);
            if (rsp == null) return false; // economy provider not registered yet: try again next call
            vault = rsp.getProvider();
            mBalance = eco.getMethod("getBalance", OfflinePlayer.class);
            mHas = eco.getMethod("has", OfflinePlayer.class, double.class);
            mWithdraw = eco.getMethod("withdrawPlayer", OfflinePlayer.class, double.class);
            mDeposit = eco.getMethod("depositPlayer", OfflinePlayer.class, double.class);
            Class<?> resp = Class.forName("net.milkbowl.vault.economy.EconomyResponse");
            mSuccess = resp.getMethod("transactionSuccess");
            vaultChecked = true;
            plugin.getLogger().info("[Economy] Using Vault.");
        } catch (Throwable t) {
            vault = null;
            vaultChecked = true;
            plugin.getLogger().warning("[Economy] Vault found but unusable (" + t + "); using internal coins.");
        }
        return vault != null;
    }

    public String currency() {
        return plugin.getConfig().getString("economy.currency", "monedas");
    }

    public String format(double amount) {
        return (amount == Math.rint(amount) ? String.valueOf((long) amount) : String.format("%.2f", amount)) + " " + currency();
    }

    public double balance(UUID id) {
        OfflinePlayer op = Bukkit.getOfflinePlayer(id);
        if (vault()) {
            try { return ((Number) mBalance.invoke(vault, op)).doubleValue(); } catch (Exception e) { return 0; }
        }
        return plugin.getPlayerStore().get(id).coins;
    }

    public boolean has(UUID id, double amount) {
        return balance(id) + 1e-9 >= amount;
    }

    public boolean withdraw(UUID id, double amount) {
        if (amount < 0) return false;
        if (vault()) {
            try {
                Object r = mWithdraw.invoke(vault, Bukkit.getOfflinePlayer(id), amount);
                return (boolean) mSuccess.invoke(r);
            } catch (Exception e) {
                return false;
            }
        }
        var rec = plugin.getPlayerStore().get(id);
        if (rec.coins < Math.ceil(amount)) return false;
        rec.coins -= (long) Math.ceil(amount);
        plugin.getPlayerStore().markDirty();
        return true;
    }

    public void deposit(UUID id, double amount) {
        if (amount <= 0) return;
        if (vault()) {
            try { mDeposit.invoke(vault, Bukkit.getOfflinePlayer(id), amount); } catch (Exception ignored) { }
            return;
        }
        plugin.getPlayerStore().get(id).coins += (long) Math.round(amount);
        plugin.getPlayerStore().markDirty();
    }

    public void deposit(Player p, double amount) {
        deposit(p.getUniqueId(), amount);
    }

    public boolean withdraw(Player p, double amount) {
        return withdraw(p.getUniqueId(), amount);
    }

    public boolean has(Player p, double amount) {
        return has(p.getUniqueId(), amount);
    }
}
