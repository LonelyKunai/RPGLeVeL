package com.arclinker.rpglevelitems.gui;

import com.arclinker.rpglevelitems.rank.RankCategory;
import com.arclinker.rpglevelitems.rank.RankScope;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/** Identifies a plugin menu and carries its state (page and the id shown in each slot). */
public final class MenuHolder implements InventoryHolder {
    public enum Type { CLASS, SUBCLASS, JOB, QUEST, RANK }

    public final Type type;
    public final int page;
    /** Entry id per slot (index = slot), null for decoration slots. */
    public final List<String> ids;
    /** Category shown by RANK menus and used by the "ranking" button of the others. */
    public final RankCategory category;
    /** Ladder shown by RANK menus. */
    public final RankScope scope;
    private Inventory inventory;

    public MenuHolder(Type type, int page, List<String> ids, RankCategory category) {
        this(type, page, ids, category, RankScope.PERMANENT);
    }

    public MenuHolder(Type type, int page, List<String> ids, RankCategory category, RankScope scope) {
        this.type = type;
        this.page = page;
        this.ids = ids;
        this.category = category;
        this.scope = scope;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }
}
