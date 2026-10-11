package com.arclinker.rpglevelitems.rank;

/** Player rank, lowest to highest: F, E, D, C, B, A, S, S+, U (Ultimate). */
public enum Rank {
    F("F", "Novato", "§7"),
    E("E", "Aprendiz", "§f"),
    D("D", "Competente", "§a"),
    C("C", "Veterano", "§b"),
    B("B", "Experto", "§9"),
    A("A", "Maestro", "§5"),
    S("S", "Leyenda", "§6"),
    S_PLUS("S+", "Mítico", "§c"),
    U("U", "Ultimate", "§d§l");

    public final String label;
    public final String title;
    public final String color;

    Rank(String label, String title, String color) {
        this.label = label;
        this.title = title;
        this.color = color;
    }

    /** Colored letter, e.g. "§6S". */
    public String display() {
        return color + label + "§r";
    }

    /** Colored letter plus title, e.g. "§6S §7(Leyenda)". */
    public String fullDisplay() {
        return display() + " §7(" + title + ")";
    }

    public Rank next() {
        Rank[] v = values();
        return ordinal() + 1 < v.length ? v[ordinal() + 1] : null;
    }

    public boolean isMax() {
        return this == U;
    }

    /** Rank for the given points; thresholds[i] = minimum points for values()[i]. */
    public static Rank fromPoints(long points, long[] thresholds) {
        Rank result = F;
        Rank[] v = values();
        for (int i = 0; i < v.length && i < thresholds.length; i++) {
            if (points >= thresholds[i]) result = v[i];
        }
        return result;
    }

    public static Rank parse(String s) {
        if (s == null) return null;
        for (Rank r : values()) {
            if (r.label.equalsIgnoreCase(s.trim())) return r;
        }
        return null;
    }
}
