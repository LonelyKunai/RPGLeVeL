package com.arclinker.rpglevelitems.rank;

import java.util.Locale;

/** PERMANENT ranks (F..U) never reset; SEASON ranks (F-S1..U-S1) reset every season. */
public enum RankScope {
    PERMANENT("Permanente", "perm"),
    SEASON("Temporada", "season");

    public final String label;
    public final String id;

    RankScope(String label, String id) {
        this.label = label;
        this.id = id;
    }

    public RankScope other() {
        return this == PERMANENT ? SEASON : PERMANENT;
    }

    public static RankScope parse(String s) {
        if (s == null) return null;
        switch (s.toLowerCase(Locale.ROOT)) {
            case "perm": case "permanent": case "permanente": case "permanentes": case "p": return PERMANENT;
            case "season": case "temporada": case "temp": case "s": case "t": return SEASON;
            default: return null;
        }
    }
}
