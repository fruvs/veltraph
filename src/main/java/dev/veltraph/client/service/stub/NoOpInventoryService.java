package dev.veltraph.client.service.stub;

import dev.veltraph.client.service.InventoryService;

import java.util.Collections;
import java.util.List;

/** Safe placeholder {@link InventoryService}: an empty hotbar on slot 0. */
public final class NoOpInventoryService implements InventoryService {

    private int selected;

    @Override
    public int selectedSlot() {
        return selected;
    }

    @Override
    public void selectSlot(int slot) {
        if (slot >= 0 && slot <= 8) {
            selected = slot;
        }
    }

    @Override
    public List<String> hotbarItemNames() {
        return Collections.nCopies(9, "");
    }
}
