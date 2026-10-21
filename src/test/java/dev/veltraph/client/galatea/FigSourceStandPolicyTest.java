package dev.veltraph.client.galatea;

import dev.veltraph.client.world.BlockPos;
import dev.veltraph.client.world.Vec3;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FigSourceStandPolicyTest {
    private final BlockPos base=new BlockPos(0,70,0);
    @Test void preparationChoosesActualDryPadAndNeverTargetCenter() {
        var target=base.up(2);var pad=base.add(2,0,0);var w=new FigSourceGeometryTest.ObservedWorld();
        w.log(target,"oak");w.standing.add(pad);w.stemRays.add(target);
        var geometry=new FigSourceGeometry(base,List.of(target),w,1,0);
        assertEquals(new Vec3(2.5,70,.5),FigSourceStandPolicy.stem(geometry,w,target,new Vec3(2.5,70,.5),90,true).orElseThrow());
    }
    @Test void noRayAcceptedPadReturnsEmptyForBoundedPreparationRetry() {
        var target=base.up(2);var w=new FigSourceGeometryTest.ObservedWorld();w.log(target,"oak");w.standing.add(base.add(2,0,0));
        var geometry=new FigSourceGeometry(base,List.of(target),w,1,0);
        assertTrue(FigSourceStandPolicy.stem(geometry,w,target,new Vec3(.5,70,.5),0,true).isEmpty());
    }
    @Test void sourceEdgeUsesEightNeighborFourCellColumns() {
        var w=new FigSourceGeometryTest.ObservedWorld();assertTrue(FigSourceStandPolicy.edge(w,base));
        for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)if(x!=0||z!=0)w.log(base.add(x,-2,z),"stone");
        assertFalse(FigSourceStandPolicy.edge(w,base));
    }
    @Test void allEightNeighborColumnsNeedAStandingLevelWithinHalfBlock() {
        var w=new FigSourceGeometryTest.ObservedWorld();
        for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)if(x!=0||z!=0)w.standing.add(base.add(x,0,z));
        assertTrue(FigSourceStandPolicy.levelNeighbors(w,base));
        w.standing.remove(base.add(1,0,1));assertFalse(FigSourceStandPolicy.levelNeighbors(w,base));
    }
    @Test void throwingUsesCurrentFeetOnObservedScaffoldSupport() {
        var w=new FigSourceGeometryTest.ObservedWorld();w.log(base.up(8),"oak");
        var geometry=new FigSourceGeometry(base,List.of(base.up(8)),w,1,0);var position=new Vec3(2.3,75,.7);
        assertEquals(position,FigSourceStandPolicy.throwing(geometry,w,base.up(8),position,new Vec3(2.3,76.62,.7),0,true).orElseThrow());
    }
    @Test void throwingDoesNotApplyStemFourBlockEligibilityLimit() {
        var target=base.up(8);var pad=base.add(2,0,0);var w=new FigSourceGeometryTest.ObservedWorld();
        w.log(target,"oak");w.standing.add(pad);w.stemRays.add(target);
        var geometry=new FigSourceGeometry(base,List.of(target),w,1,0);
        assertTrue(FigSourceStandPolicy.stem(geometry,w,target,new Vec3(2.5,70,.5),0,true).isEmpty());
        assertEquals(new Vec3(2.5,70,.5),FigSourceStandPolicy.throwing(geometry,w,target,new Vec3(2.5,70,.5),new Vec3(2.5,71.62,.5),0,false).orElseThrow());
    }
}
