package dev.veltraph.client.ui.widget;

import dev.veltraph.client.farming.domain.FarmMovementMode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The widget framework is pure, so the cycler's selection logic is unit-testable without MC. */
class EnumCyclerTest {

    @Test
    void leftClickAdvancesRightClickReverses() {
        AtomicReference<FarmMovementMode> ref = new AtomicReference<>(FarmMovementMode.S_SHAPE);
        EnumCycler<FarmMovementMode> cycler = new EnumCycler<>(
                0, 0, 100, 14, "Mode",
                List.of(FarmMovementMode.S_SHAPE, FarmMovementMode.VERTICAL),
                ref::get, ref::set, Enum::name);

        // Left click -> next.
        assertTrue(cycler.mouseClicked(5, 5, 0));
        assertEquals(FarmMovementMode.VERTICAL, ref.get());
        // Wraps around.
        assertTrue(cycler.mouseClicked(5, 5, 0));
        assertEquals(FarmMovementMode.S_SHAPE, ref.get());
        // Right click -> previous.
        assertTrue(cycler.mouseClicked(5, 5, 1));
        assertEquals(FarmMovementMode.VERTICAL, ref.get());
    }

    @Test
    void clickOutsideBoundsIsIgnored() {
        AtomicReference<FarmMovementMode> ref = new AtomicReference<>(FarmMovementMode.S_SHAPE);
        EnumCycler<FarmMovementMode> cycler = new EnumCycler<>(
                0, 0, 100, 14, "Mode",
                List.of(FarmMovementMode.S_SHAPE, FarmMovementMode.VERTICAL),
                ref::get, ref::set, Enum::name);
        assertEquals(false, cycler.mouseClicked(500, 500, 0));
        assertEquals(FarmMovementMode.S_SHAPE, ref.get());
    }
}
