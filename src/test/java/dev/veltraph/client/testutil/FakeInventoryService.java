package dev.veltraph.client.testutil;

import dev.veltraph.client.service.InventoryService;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Controllable {@link InventoryService}: settable hotbar item names; records slot selections. */
public final class FakeInventoryService implements InventoryService {

    public int selected;
    public int selectCalls;
    private final List<String> hotbar = new ArrayList<>();

    public FakeInventoryService() {
        for (int i = 0; i < 9; i++) {
            hotbar.add("");
        }
    }

    /** Put an item (display name) in a hotbar slot; null/blank clears it. */
    public FakeInventoryService setItem(int slot, String name) {
        hotbar.set(slot, name == null ? "" : name.toLowerCase(Locale.ROOT));
        return this;
    }

    @Override
    public int selectedSlot() {
        return selected;
    }

    @Override
    public void selectSlot(int slot) {
        if (slot >= 0 && slot <= 8) {
            selected = slot;
            selectCalls++;
        }
    }

    @Override
    public List<String> hotbarItemNames() {
        return List.copyOf(hotbar);
    }
}
