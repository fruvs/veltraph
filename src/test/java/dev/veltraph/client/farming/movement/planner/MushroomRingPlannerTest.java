package dev.veltraph.client.farming.movement.planner;

import org.junit.jupiter.api.Test;
import static dev.veltraph.client.farming.movement.planner.MushroomRingLaneClassifier.Lane.*;
import static org.junit.jupiter.api.Assertions.*;

class MushroomRingPlannerTest {
    @Test void laneRoleOverridesConfiguredStartingInputWithoutChangingView() {
        var p=new MushroomRingPlanner();p.start(-117,false);
        assertTrue(p.selectLane(NON_TRAPDOOR));assertTrue(p.forward());assertEquals(-116.57f,p.yaw());assertEquals(0,p.turns());
        p.start(154,true);assertTrue(p.selectLane(TRAPDOOR));assertFalse(p.forward());assertEquals(153.43f,p.yaw());
    }
    @Test void repeatedLaneObservationsNeverBlindlyReverse() {
        var p=new MushroomRingPlanner();p.start(-116.57f);
        for(int i=0;i<100;i++)assertTrue(p.selectLane(TRAPDOOR));
        assertFalse(p.forward());assertEquals(0,p.turns());
        assertTrue(p.selectLane(NON_TRAPDOOR));assertTrue(p.forward());assertEquals(1,p.turns());
    }
    @Test void unknownAndAmbiguousCannotChooseMovementOrRecordATurn() {
        var p=new MushroomRingPlanner();p.start(63,false);p.selectLane(TRAPDOOR);p.pause();
        assertFalse(p.selectLane(UNKNOWN));assertFalse(p.selectLane(AMBIGUOUS));assertEquals(0,p.turns());
        p.start(63,true);p.selectLane(TRAPDOOR);assertEquals(0,p.turns());assertFalse(p.forward());
    }
}
