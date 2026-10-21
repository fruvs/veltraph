package dev.veltraph.client.farming.movement.planner;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AceCaneFlowerPlannerTest {
    @Test void authorNormalRowsUseChordAndLeft() {
        var p=new AceCaneFlowerPlanner();
        assertEquals(new AceCaneFlowerPlanner.Keys(true,false,false,true,true),p.keys());
        p.observe(true,false,false);
        assertEquals(new AceCaneFlowerPlanner.Keys(false,false,true,false,false),p.keys());
        p.observe(false,false,true);
        assertEquals(AceCaneFlowerPlanner.Phase.ROW_CHORD,p.phase());
        assertEquals(2,p.rows());
    }
    @Test void returnLoopHasSeparateEntryExitAndFirstLeftRow() {
        var p=new AceCaneFlowerPlanner();
        p.observe(true,true,false);
        assertEquals(new AceCaneFlowerPlanner.Keys(true,true,false,true,true),p.keys());
        p.observe(false,false,true);
        assertEquals(new AceCaneFlowerPlanner.Keys(false,true,false,false,false),p.keys());
        p.observe(false,false,true);
        assertEquals(AceCaneFlowerPlanner.Phase.ROW_LEFT,p.phase());
        assertEquals(1,p.loops());
        p.reset(); assertEquals(AceCaneFlowerPlanner.Phase.ROW_CHORD,p.phase());
        assertEquals(0,p.loops()); assertEquals(0,p.rows());
    }
    @Test void missingCropsAndUnconfirmedCornersDoNotChangeLeg() {
        var p=new AceCaneFlowerPlanner();
        for(int i=0;i<100;i++) p.observe(false,false,false);
        assertEquals(AceCaneFlowerPlanner.Phase.ROW_CHORD,p.phase());
        p.observe(false,false,true);
        assertEquals(AceCaneFlowerPlanner.Phase.ROW_CHORD,p.phase());
    }
}
