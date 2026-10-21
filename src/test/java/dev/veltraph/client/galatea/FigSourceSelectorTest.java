package dev.veltraph.client.galatea;

import dev.veltraph.client.world.BlockPos;
import dev.veltraph.client.world.Vec3;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FigSourceSelectorTest {
    private final BlockPos base=new BlockPos(0,70,0);
    private final Vec3 position=new Vec3(.5,70,.5),eye=new Vec3(.5,71.62,.5);
    private FigSourceSelector.View view(){return new FigSourceSelector.View(base,position,eye,0);}
    private static final class Rays extends FigSourceGeometryTest.ObservedWorld {
        final Set<BlockPos> available=new HashSet<>();
        final List<Vec3> helperOrigins=new ArrayList<>();
        @Override public boolean sourceRay(Vec3 from,BlockPos p,boolean leavesAllowed){return leavesAllowed||available.contains(p);}
        @Override public Vec3 standingEye(Vec3 p){helperOrigins.add(p);return super.standingEye(p);}
    }
    @Test void sourceJumpFeasibilityPenaltyChangesProductiveSelection() {
        var first=base.up(5);var second=base.add(2,5,0);var w=new Rays();
        w.log(first,"oak");w.log(second,"oak");w.available.addAll(List.of(first,second));
        var geometry=new FigSourceGeometry(base,List.of(first,second),w,1,0);
        var selector=new FigSourceSelector(geometry,w);selector.enterJump(position,1);
        w.available.remove(first);
        assertEquals(second,selector.select(GalateaPlanner.AxeMode.JUMPING,List.of(first,second),p->true,view(),false,new BlockPos(0,0,0)).orElseThrow());
    }
    @Test void jumpHelperOriginIsCapturedAtEntryAndSuccessfulQueriesStayCached() {
        var p=base.up(5);var q=base.add(2,5,0);var w=new Rays();w.log(p,"oak");w.log(q,"oak");w.available.addAll(List.of(p,q));
        var selector=new FigSourceSelector(new FigSourceGeometry(base,List.of(p,q),w,1,0),w);
        w.helperOrigins.clear();selector.enterJump(position,1);
        selector.select(GalateaPlanner.AxeMode.JUMPING,List.of(p,q),x->true,view(),false,new BlockPos(0,0,0));
        assertFalse(w.helperOrigins.isEmpty());assertTrue(w.helperOrigins.stream().allMatch(position::equals));
        int probes=w.helperOrigins.size();w.available.clear();
        selector.select(GalateaPlanner.AxeMode.JUMPING,List.of(p,q),x->true,view(),false,new BlockPos(0,0,0));
        assertEquals(probes,w.helperOrigins.size());
        selector.enterJump(new Vec3(1.5,70,.5),1);
        selector.select(GalateaPlanner.AxeMode.JUMPING,List.of(p,q),x->true,view(),false,new BlockPos(0,0,0));
        assertTrue(w.helperOrigins.size()>probes);
    }
    @Test void missingModeEntryCaptureRefusesInsteadOfInventingOrigin() {
        var p=base.up(5);var w=new Rays();w.log(p,"oak");w.available.add(p);
        var selector=new FigSourceSelector(new FigSourceGeometry(base,List.of(p),w,1,0),w);
        assertThrows(IllegalStateException.class,()->selector.select(GalateaPlanner.AxeMode.JUMPING,List.of(p),x->true,view(),false,new BlockPos(0,0,0)));
    }
    @Test void independentAttemptFilterIsAppliedBeforeRankingAndNativeRayPreference() {
        var first=base.up(5);var second=base.add(2,5,0);var w=new Rays();
        w.log(first,"oak");w.log(second,"oak");w.available.addAll(List.of(first,second));
        var selector=new FigSourceSelector(new FigSourceGeometry(base,List.of(first,second),w,1,0),w);selector.enterJump(position,1);
        assertEquals(second,selector.select(GalateaPlanner.AxeMode.JUMPING,List.of(first,second),p->!p.equals(first),view(),false,new BlockPos(0,0,0)).orElseThrow());
        assertTrue(selector.select(GalateaPlanner.AxeMode.JUMPING,List.of(first,second),p->false,view(),false,new BlockPos(0,0,0)).isEmpty());
    }
}
