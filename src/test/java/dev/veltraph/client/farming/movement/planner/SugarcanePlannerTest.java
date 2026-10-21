package dev.veltraph.client.farming.movement.planner;

import dev.veltraph.client.config.FarmingConfig;
import dev.veltraph.client.config.LaneSide;
import dev.veltraph.client.config.MapConfigService;
import dev.veltraph.client.farming.movement.TurnDirection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Exercises the recovered SUGARCANE_DIR + currentDir<->BACK toggle and aim math (§5c). */
class SugarcanePlannerTest {

    private MapConfigService config;
    private SugarcanePlanner planner;

    @BeforeEach
    void setUp() {
        config = new MapConfigService();
        planner = new SugarcanePlanner(config);
    }

    @Test
    void defaultsToConfiguredLeftSide() {
        assertEquals(TurnDirection.LEFT, planner.currentDir());
        assertFalse(planner.isOnBackLeg());
    }

    @Test
    void laneSwitchTogglesSideAndBack() {
        planner.onLaneSwitch();
        assertEquals(TurnDirection.BACK, planner.currentDir());
        assertTrue(planner.isOnBackLeg());

        planner.onLaneSwitch();
        assertEquals(TurnDirection.LEFT, planner.currentDir());
        assertFalse(planner.isOnBackLeg());
    }

    @Test
    void aimYawIsPlus45WhenLeft() {
        assertEquals(45.0, planner.aimYaw(0.0), 1e-9);
        assertEquals(145.0, planner.aimYaw(100.0), 1e-9);
    }

    @Test
    void rightConfigFlipsAimAndStartingSide() {
        config.set(FarmingConfig.SUGARCANE_DIR, LaneSide.RIGHT);
        planner.reset();
        assertEquals(TurnDirection.RIGHT, planner.currentDir());
        assertEquals(-45.0, planner.aimYaw(0.0), 1e-9);
        planner.onLaneSwitch();
        assertEquals(TurnDirection.BACK, planner.currentDir());
    }

    @Test
    void backLegHasNoLateralInput() {
        // BACK is the literal backward key; do not add an inferred opposite strafe.
        assertEquals(TurnDirection.LEFT, planner.strafeSide());
        planner.onLaneSwitch();
        assertTrue(planner.isOnBackLeg());
        assertEquals(null, planner.strafeSide());
        planner.onLaneSwitch();
        assertEquals(TurnDirection.LEFT, planner.strafeSide());
    }

    @Test
    void rightConfigAlsoHasNoStrafeOnBack() {
        config.set(FarmingConfig.SUGARCANE_DIR, LaneSide.RIGHT);
        planner.reset();
        assertEquals(TurnDirection.RIGHT, planner.strafeSide());
        planner.onLaneSwitch();
        assertEquals(null, planner.strafeSide());
    }
}
