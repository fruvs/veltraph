package dev.veltraph.client.farming.module;

import dev.veltraph.client.testutil.FakeInventoryService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Crop tool selection: configured slot first, then hotbar name scan, else a clear failure. */
class CropToolSelectorTest {

    @Test
    void configuredSlotUsedWhenOccupied() {
        FakeInventoryService inv = new FakeInventoryService().setItem(3, "Cactus Knife");
        ToolSelection sel = CropToolSelector.select(inv, 3, List.of("cactus", "knife"), "Cactus");
        assertTrue(sel.ok());
        assertEquals(3, sel.slot());
    }

    @Test
    void configuredButEmptySlotFails() {
        FakeInventoryService inv = new FakeInventoryService();
        ToolSelection sel = CropToolSelector.select(inv, 2, List.of("hoe"), "Wart/Crops");
        assertFalse(sel.ok());
        assertTrue(sel.error().toLowerCase().contains("slot"));
    }

    @Test
    void nameScanFindsToolWhenSlotAuto() {
        FakeInventoryService inv = new FakeInventoryService()
                .setItem(0, "Stick")
                .setItem(5, "Newton Nether Wart Hoe");
        ToolSelection sel = CropToolSelector.select(inv, -1, List.of("hoe", "wart"), "Wart/Crops");
        assertTrue(sel.ok());
        assertEquals(5, sel.slot());
    }

    @Test
    void missingToolBlocksWithClearError() {
        FakeInventoryService inv = new FakeInventoryService().setItem(0, "Diamond Sword");
        ToolSelection sel = CropToolSelector.select(inv, -1, List.of("dicer", "cleaver"), "Pumpkin/Melon");
        assertFalse(sel.ok());
        assertEquals(-1, sel.slot());
        assertTrue(sel.error().contains("Pumpkin/Melon"));
        assertTrue(sel.error().toLowerCase().contains("tool") || sel.error().toLowerCase().contains("dicer"));
    }
}
