package com.arclinker.rpglevelitems.stats;

import com.arclinker.rpglevelitems.ClassLevelManager;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Catalog of every stat that items, gems, sets, talents, titles... can grant.
 * Vanilla-backed stats (health, damage...) become attribute modifiers; custom stats are read by the
 * combat / mana / cooldown code through {@link StatEngine#get}.
 * Custom percent stats use plain percent numbers (5.0 = 5%).
 */
public final class Stats {
    public record Info(String key, String label, boolean percent, double displayScale, boolean vanilla) {}

    public static final Map<String, Info> ALL = new LinkedHashMap<>();

    private static void custom(String key, String label, boolean percent) {
        ALL.put(key, new Info(key, label, percent, 1.0, false));
    }

    static {
        for (ClassLevelManager.Stat s : ClassLevelManager.Stat.values()) {
            boolean pct = s.isPercent() || s == ClassLevelManager.Stat.KNOCKBACK_RESISTANCE;
            ALL.put(s.key, new Info(s.key, s.label, pct, pct ? 100.0 : 1.0, true));
        }
        custom("crit_chance", "Prob. crítico", true);
        custom("crit_damage", "Daño crítico", true);
        custom("evasion", "Evasión", true);
        custom("penetration", "Penetración", true);
        custom("lifesteal", "Robo de vida", true);
        custom("damage_reduction", "Reducción de daño", true);
        custom("cooldown_reduction", "Reducción de enfriamiento", true);
        custom("spell_power", "Poder de habilidad", true);
        custom("mana_max", "Maná máximo", false);
        custom("mana_regen", "Regeneración de maná", false);
        custom("xp_bonus", "Bonus de XP", true);
        custom("status_resist", "Resistencia a estados", true);
        custom("bleed_chance", "Prob. de sangrado", true);
        custom("burn_chance", "Prob. de quemar", true);
        custom("poison_chance", "Prob. de envenenar", true);
        custom("freeze_chance", "Prob. de congelar", true);
        custom("stun_chance", "Prob. de aturdir", true);
        custom("shock_chance", "Prob. de electrocutar", true);
        custom("execute", "Ejecución (daño a <30% de vida)", true);
        custom("mana_on_hit", "Maná por golpe", false);
        custom("thorns", "Espinas (daño reflejado)", true);
        custom("health_regen", "Regeneración de vida (por s)", false);
    }

    private Stats() {}

    public static boolean isKnown(String key) {
        return ALL.containsKey(key);
    }

    public static boolean isCustom(String key) {
        Info i = ALL.get(key);
        return i != null && !i.vanilla();
    }

    public static ClassLevelManager.Stat vanilla(String key) {
        for (ClassLevelManager.Stat s : ClassLevelManager.Stat.values()) if (s.key.equals(key)) return s;
        return null;
    }

    public static String label(String key) {
        Info i = ALL.get(key);
        return i == null ? key : i.label();
    }

    /** "+3.5% Prob. crítico" / "+2 Vida máxima". */
    public static String line(String key, double v) {
        Info i = ALL.get(key);
        double shown = i == null ? v : v * i.displayScale();
        String num = Math.abs(shown - Math.rint(shown)) < 0.005 ? String.valueOf((long) Math.rint(shown)) : String.format(Locale.ROOT, "%.1f", shown);
        return (shown >= 0 ? "+" : "") + num + (i != null && i.percent() ? "%" : "") + " " + label(key);
    }

    public static void add(Map<String, Double> into, String key, double v) {
        if (v != 0) into.merge(key, v, Double::sum);
    }

    public static void addAll(Map<String, Double> into, Map<String, Double> from) {
        from.forEach((k, v) -> add(into, k, v));
    }
}
