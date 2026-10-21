package dev.veltraph.client.navigation.mc;

import java.util.List;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CollectionSceneFrozenBoxesTest {
    private static CollectionSceneFrozenBoxes.Box box(double x0,double y0,double z0,double x1,double y1,double z1) {
        return new CollectionSceneFrozenBoxes.Box(x0,y0,z0,x1,y1,z1);
    }
    private static final CollectionSceneFrozenBoxes.Box REGION=box(-1,-1,-1,4,4,2);
    private static final CollectionSceneFrozenBoxes.Border BORDER=new CollectionSceneFrozenBoxes.Border(-10,-10,10,10);
    private static final List<CollectionSceneFrozenBoxes.Box> FLOOR=List.of(box(0,-1,0,1,0,1),box(1,-1,0,2,0,1),box(2,-1,0,3,0,1));
    private static CollectionSceneFrozenBoxes.Box body(double x,double y) { return box(x-.3,y,.2,x+.3,y+1.8,.8); }
    @Test void oneFrozenJourneySceneAllowsOrdinaryNativeMovement() {
        assertTrue(CollectionSceneFrozenBoxes.clear(body(.5,0),.5,0,.5,REGION,BORDER,FLOOR,FLOOR));
        assertTrue(CollectionSceneFrozenBoxes.clear(body(1,0),1,0,.5,REGION,BORDER,FLOOR,FLOOR));
        assertTrue(CollectionSceneFrozenBoxes.clear(body(1.5,0),1.5,0,.5,REGION,BORDER,FLOOR,FLOOR));
    }
    @Test void callbackMoveIntoFrozenRoofRefusesWithoutAnyWorldMutation() {
        var roof=box(1,1.4,0,2,2.4,1);
        var obstacles=new ArrayList<>(FLOOR); obstacles.add(roof);
        assertTrue(CollectionSceneFrozenBoxes.clear(body(.5,0),.5,0,.5,REGION,BORDER,obstacles,FLOOR));
        assertFalse(CollectionSceneFrozenBoxes.clear(body(1.5,0),1.5,0,.5,REGION,BORDER,obstacles,FLOOR));
    }
    @Test void callbackMoveIntoFrozenFootCellRefuses() {
        var obstacles=new ArrayList<>(FLOOR); obstacles.add(box(2,0,0,3,1,1));
        assertFalse(CollectionSceneFrozenBoxes.clear(body(2.5,0),2.5,0,.5,REGION,BORDER,obstacles,FLOOR));
    }
    @Test void allFourSupportedCornersStillRefuseAnInteriorFootprintHole() {
        var partial=List.of(box(0,-1,0,1,0,.4),box(0,-1,.6,1,0,1),box(0,-1,.4,.4,0,.6),box(.6,-1,.4,1,0,.6));
        assertFalse(CollectionSceneFrozenBoxes.clear(body(.5,0),.5,0,.5,REGION,BORDER,partial,partial));
    }
    @Test void nativeHalfSlabSupportsOnlyItsActualTopHeight() {
        var slabs=List.of(box(0,0,0,1,.5,1));
        assertTrue(CollectionSceneFrozenBoxes.clear(body(.5,.5),.5,.5,.5,REGION,BORDER,slabs,slabs));
        assertFalse(CollectionSceneFrozenBoxes.clear(body(.5,.6),.5,.6,.5,REGION,BORDER,slabs,slabs));
    }
    @Test void bboxPositionMismatchAndBodyCrossingStaticBorderRefuse() {
        assertFalse(CollectionSceneFrozenBoxes.clear(body(.5,.1),.5,0,.5,REGION,BORDER,FLOOR,FLOOR));
        var narrow=new CollectionSceneFrozenBoxes.Border(0,0,3,1);
        assertFalse(CollectionSceneFrozenBoxes.clear(body(.1,0),.1,0,.5,REGION,narrow,FLOOR,FLOOR));
    }
    @Test void totalFrozenBoxBudgetIsEnforcedBeforeGeometryWork() {
        var overflow=new ArrayList<CollectionSceneFrozenBoxes.Box>();
        for (int i=0;i<4097;i++) overflow.add(box(20,-1,20,21,0,21));
        assertFalse(CollectionSceneFrozenBoxes.clear(body(.5,0),.5,0,.5,REGION,BORDER,overflow,List.of()));
    }
}
