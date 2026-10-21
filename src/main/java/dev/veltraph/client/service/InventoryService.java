package dev.veltraph.client.service;

import java.util.List;

/**
 * Read/select the player's hotbar. Farming selects the crop tool by changing the held hotbar slot
 * (never by clicking inventory GUI slots). Implementations must be defensive (no player → safe stub).
 */
public interface InventoryService {

    /** Currently selected hotbar slot, 0..8. */
    int selectedSlot();

    /** Select hotbar slot {@code slot} (0..8). Out-of-range values are ignored. */
    void selectSlot(int slot);

    /** Lowercased display names of the 9 hotbar slots ("" for an empty slot). Always size 9. */
    List<String> hotbarItemNames();
}
