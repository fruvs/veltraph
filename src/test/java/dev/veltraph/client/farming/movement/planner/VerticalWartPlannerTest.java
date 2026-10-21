package dev.veltraph.client.farming.movement.planner;

import dev.veltraph.client.config.MapConfigService;
import dev.veltraph.client.farming.movement.TurnDirection;
import dev.veltraph.client.world.ProbeResult;
import dev.veltraph.client.world.Vec3;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Raw-source counter, horizontal-entry probe, direct direction toggle and height reset tests. */
class VerticalWartPlannerTest {

    /** Controllable world for the planner. */
    private static final class FakeWorld implements VerticalWartPlanner.World {
        Vec3 pos = new Vec3(0, 64, 0);
        double vy = 64.0;
        double speed = 0.0;
        boolean rowAdvance = true; // phase B early-returns so it doesn't toggle the direction
        boolean stepPreds = false;
        ProbeResult scanResult = ProbeResult.NONE;
        ProbeResult secondScanResult = ProbeResult.NONE;
        int scans,commits,heightReleases; // source horizontal probe result

        int doRowTurnCount;
        TurnDirection lastTurnDir;
        int lastTurnTicks;

        @Override
        public Vec3 position() {
            return pos;
        }

        @Override
        public double verticalPos() {
            return vy;
        }

        @Override
        public double horizontalSpeed() {
            return speed;
        }

        @Override
        public boolean rowAdvanceCheck() {
            return rowAdvance;
        }

        @Override
        public boolean stepPredicates() {
            return stepPreds;
        }

        @Override
        public void onStill(TurnDirection dir, int ticks) {
            doRowTurnCount++;
            lastTurnDir = dir;
            lastTurnTicks = ticks;
        }

        @Override
        public ProbeResult scanDirection(boolean second, int maxDepth) {
            scans++; return second ? secondScanResult : scanResult;
        }

        @Override
        public void commitTurn() { commits++;
        }

        @Override
        public void releaseForHeightChange() { heightReleases++;
        }
    }

    private MapConfigService config;
    private FakeWorld world;
    private VerticalWartPlanner planner;

    @BeforeEach
    void setUp() {
        config = new MapConfigService();
        world = new FakeWorld();
        planner = new VerticalWartPlanner(config, world);
    }

    @Test
    void startUsesConfiguredStartingDirectionRight() {
        planner.start();
        assertEquals(TurnDirection.RIGHT, planner.currentDir(), "NETHERWART_STARTING_DIRECTION default RIGHT");
    }

    @Test
    void stillForFortyTicksTriggersRowTurn() {
        planner.start();
        for (int i = 0; i < VerticalWartPlanner.STILL_CHECK_PERIOD; i++) {
            planner.tick();
        }
        assertEquals(1, world.doRowTurnCount);
        assertEquals(TurnDirection.RIGHT, world.lastTurnDir);
        assertEquals(VerticalWartPlanner.DIAGNOSTIC_SCHEDULE_UNITS, world.lastTurnTicks);
    }

    @Test
    void movingFartherThanThresholdDoesNotTriggerStillTurn() {
        planner.start();
        world.pos = new Vec3(10, 64, 0); // moved 10 > 0.25 still distance
        for (int i = 0; i < VerticalWartPlanner.STILL_CHECK_PERIOD; i++) {
            planner.tick();
        }
        assertEquals(0, world.doRowTurnCount);
    }


    @Test void entryResolverUsesTwoIndependentProbesAndNoneKeepsConfiguredSide() {
        world.secondScanResult=ProbeResult.BLOCKED;planner.start();
        assertEquals(2,world.scans);assertEquals(TurnDirection.LEFT,planner.currentDir());
        world.scans=0;world.scanResult=ProbeResult.FOUND;planner.start();
        assertEquals(1,world.scans);assertEquals(TurnDirection.LEFT,planner.currentDir());
        world.scans=0;world.scanResult=ProbeResult.NONE;world.secondScanResult=ProbeResult.NONE;planner.start();
        assertEquals(TurnDirection.RIGHT,planner.currentDir());assertEquals(2,world.scans);
    }
    @Test void fallbackFlipsDirectlyWithoutInventedParityOrAdditionalDownScan() {
        planner.start();world.rowAdvance=false;
        for(int i=0;i<20;i++)planner.tick();
        assertEquals(TurnDirection.LEFT,planner.currentDir());assertEquals(1,world.commits);
        for(int i=0;i<21;i++)planner.tick();
        assertEquals(TurnDirection.RIGHT,planner.currentDir());assertEquals(2,world.commits);
        assertEquals(2,world.scans,"only entry probes scan; fallback is the actual opposite enum helper");
    }
    @Test void movingDuringHeightChangeReleasesInputBeforeDirectionReconsideration() {
        planner.start();world.rowAdvance=false;world.vy=62;world.speed=.1;
        for(int i=0;i<20;i++)planner.tick();
        assertEquals(1,world.heightReleases);assertEquals(0,world.commits);
        assertEquals(TurnDirection.RIGHT,planner.currentDir());
    }
    @Test void teleportResetsObservedHeightAndPositionAndSwitchesOnlyWhenConfigured() {
        planner.start();world.vy=60;world.pos=new Vec3(8,60,3);
        planner.resumeAfterTeleport();assertEquals(TurnDirection.RIGHT,planner.currentDir());
        assertEquals(60,planner.recordedY());assertEquals(0,planner.stillCounter());
        config.set(dev.veltraph.client.config.FarmingConfig.NETHERWART_SWITCH_DIRECTION_ON_TP,true);
        planner.resumeAfterTeleport();assertEquals(TurnDirection.LEFT,planner.currentDir());
    }

    @Test
    void recoveredConstants() {
        assertEquals(40, VerticalWartPlanner.STILL_CHECK_PERIOD);
        assertEquals(20, VerticalWartPlanner.FORWARD_RECHECK_PERIOD);
        assertEquals(0.25, VerticalWartPlanner.STILL_DISTANCE, 1e-9);
        assertEquals(256, VerticalWartPlanner.MAX_SCAN_DISTANCE);
    }
}
