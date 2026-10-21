package dev.veltraph.client.galatea;

import dev.veltraph.client.world.BlockPos;
import dev.veltraph.client.world.Vec3;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FigSourceGeometryTest {
    private static final BlockPos BASE=new BlockPos(0,70,0);
    static class ObservedWorld implements FigSourceGeometry.World {
        final Map<BlockPos,String> types=new HashMap<>();
        final Set<BlockPos> leaves=new HashSet<>(),standing=new HashSet<>(),stemRays=new HashSet<>();
        @Override public boolean passable(BlockPos p){return !types.containsKey(p);}
        @Override public boolean log(BlockPos p){return types.containsKey(p);}
        @Override public boolean leaves(BlockPos p){return leaves.contains(p);}
        @Override public Object blockType(BlockPos p){return types.get(p);}
        @Override public boolean standing(BlockPos p){return standing.contains(p);}
        @Override public boolean dryStanding(BlockPos p){return standing(p);}
        @Override public Vec3 standingEye(Vec3 p){return new Vec3(p.x(),70+1.62,p.z());}
        @Override public boolean sourceRay(Vec3 eye,BlockPos p,boolean leavesAllowed){return !leavesAllowed||stemRays.contains(p);}
        void log(BlockPos p,String type){types.put(p,type);}
    }
    @Test void groupsKeepSourceConcatenationOrderAndPersistentExclusions() {
        var stem=BASE.up();var jump=BASE.up(5);var scaffold=BASE.up(8);
        var w=new ObservedWorld();for(var p:List.of(stem,jump,scaffold))w.log(p,"oak");
        w.standing.add(BASE.add(2,0,0));w.stemRays.add(stem);
        var g=new FigSourceGeometry(BASE,List.of(scaffold,jump,stem),w,1,4);
        assertEquals(List.of(stem),g.candidates(GalateaPlanner.AxeMode.STEM,Set.of()));
        assertEquals(List.of(stem,jump),g.candidates(GalateaPlanner.AxeMode.JUMPING,Set.of()));
        assertEquals(List.of(stem,jump,scaffold),g.candidates(GalateaPlanner.AxeMode.SCAFFOLD,Set.of()));
        assertEquals(List.of(scaffold,jump,stem),g.candidates(GalateaPlanner.AxeMode.THROWING,Set.of()));
        assertEquals(List.of(stem,scaffold),g.candidates(GalateaPlanner.AxeMode.SCAFFOLD,Set.of(jump)));
    }
    @Test void leafCostIsFrozenAtDescriptorConstructionAndIncludesDiagonalNeighbor() {
        var w=new ObservedWorld();w.log(BASE,"oak");w.leaves.add(BASE.add(1,1,1));
        var g=new FigSourceGeometry(BASE,List.of(BASE),w,1,0);
        w.leaves.clear();assertEquals(1,g.leafCost(BASE));assertEquals(0,g.leafCost(BASE.up(10)));
    }
    @Test void baseMinusTwoIsStrictlyExcluded() {
        var w=new ObservedWorld();for(var p:List.of(BASE.down(),BASE.down(2)))w.log(p,"oak");
        var g=new FigSourceGeometry(BASE,List.of(BASE.down(2),BASE.down()),w,1,0);
        assertEquals(List.of(BASE.down()),g.candidates(GalateaPlanner.AxeMode.THROWING,Set.of()));
    }
    @Test void sourceJumpIntegrationUsesNativePowerAndTruncatesMaximum() {
        assertEquals(1,FigSourceGeometry.jumpHeight(.42f));assertEquals(4,FigSourceGeometry.jumpHeight(.9f));
        assertEquals(0,FigSourceGeometry.jumpHeight(0));
    }
    @Test void throwingUsesSixFacesAndExactTypeInsteadOfDiagonalComponent() {
        var w=new ObservedWorld();var diagonal=BASE.add(1,1,0);var different=BASE.add(1,0,0);
        w.log(BASE,"oak");w.log(diagonal,"oak");w.log(different,"birch");
        assertEquals(Map.of(BASE,1,diagonal,1,different,1),FigSourceGeometry.throwingCounts(List.of(BASE,diagonal,different),w));
    }
    @Test void throwingBoundaryIsStrictlyBelowOneHundred() {
        var list=new ArrayList<BlockPos>();for(int i=0;i<100;i++)list.add(BASE.add(i,0,0));
        var w=new ObservedWorld();assertTrue(FigSourceGeometry.throwingCounts(list,w).isEmpty());
    }
    @Test void throwingFloodIsCappedAtSeventyFive() {
        var w=new ObservedWorld();for(int i=0;i<90;i++)w.log(BASE.add(i,0,0),"oak");
        var counts=FigSourceGeometry.throwingCounts(List.of(BASE),w);
        assertEquals(75,counts.size());assertTrue(counts.values().stream().allMatch(n->n==75));
    }
}
