package dev.veltraph.client.galatea;

import dev.veltraph.client.world.BlockPos;
import dev.veltraph.client.world.Vec3;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class GalateaPlannerTest {
    private final BlockPos root=new BlockPos(0,70,0);
    @Test void connectedBranchesRemainAfterStemRemoval() {
        Set<BlockPos> logs=new HashSet<>(List.of(root,root.up(),root.up(2),root.add(1,3,0),root.add(2,3,0)));
        var tree=GalateaPlanner.component(root,logs::contains,p->true,256).orElseThrow();
        assertEquals(5,tree.logs().size());
        var session=new GalateaSession(tree);logs.remove(root);session.observe(logs::contains);
        assertFalse(session.complete());assertEquals(4,session.remaining());assertEquals(1,session.removed());
        logs.clear();session.observe(logs::contains);assertTrue(session.complete());assertEquals(5,session.removed());
    }
    @Test void predictionRollbackReopensTree() {
        var session=new GalateaSession(new GalateaPlanner.Tree(root,List.of(root,root.up()),0,0,false));
        session.observe(p->false);assertTrue(session.complete());
        session.observe(p->p.equals(root));assertFalse(session.complete());assertEquals(1,session.removed());
    }
    @Test void truncatedComponentIsRejected() {
        Set<BlockPos> logs=Set.of(root,root.up(),root.up(2));
        assertTrue(GalateaPlanner.component(root,logs::contains,p->true,2).isEmpty());
        assertTrue(GalateaPlanner.component(root,logs::contains,p->p.y()<72,256).isEmpty());
    }
    @Test void sourceModePriorityAndSkipThrowing() {
        assertEquals(GalateaPlanner.AxeMode.JUMPING,GalateaPlanner.nextMode(GalateaPlanner.AxeMode.STEM,true,true,false));
        assertEquals(GalateaPlanner.AxeMode.SCAFFOLD,GalateaPlanner.nextMode(GalateaPlanner.AxeMode.STEM,false,true,false));
        assertEquals(GalateaPlanner.AxeMode.THROWING,GalateaPlanner.nextMode(GalateaPlanner.AxeMode.STEM,false,false,false));
        assertNull(GalateaPlanner.nextMode(GalateaPlanner.AxeMode.STEM,false,false,true));
        assertNull(GalateaPlanner.nextMode(GalateaPlanner.AxeMode.THROWING,true,true,false));
    }
    @Test void sourceRegionMismatchAndFiveTransitionCosts() {
        var player=new Vec3(.5,70.5,.5);
        var prev=new GalateaPlanner.Tree(root,List.of(root),1,0,false);
        var next=new GalateaPlanner.Tree(root.up(),List.of(root.up()),2,7,false);
        var five=new GalateaPlanner.Tree(root.up(),List.of(root.up()),5,7,false);
        assertEquals(38,GalateaPlanner.score(next,player,0,prev,null,false),.001);
        assertEquals(8,GalateaPlanner.score(five,player,0,prev,null,false),.001);
        var bounds=GalateaPlanner.Bounds.parse("10,60,10,20,80,20").orElseThrow();
        assertEquals(38+200+Math.hypot(14.5,14.5)*2,GalateaPlanner.score(next,player,0,prev,bounds,false),.001);
    }
    @Test void invalidRegionDoesNotBecomeOrigin() {
        assertTrue(GalateaPlanner.Bounds.parse("").isEmpty());
        assertTrue(GalateaPlanner.Bounds.parse("0 1 0 10 0 10").isEmpty());
        assertTrue(GalateaPlanner.Bounds.parse("not a region").isEmpty());
        assertTrue(GalateaPlanner.Bounds.parse("-2147483648 0 0 1 90 1").isEmpty());
        assertTrue(GalateaPlanner.Bounds.parse("0 0 0 1 9000 1").isEmpty());
        assertTrue(GalateaPlanner.Bounds.parse("-4 0 -4 4 90 4").orElseThrow().contains(root));
    }
    @Test void failedLogDoesNotHideOtherBranchAndModeResetRetries() {
        var session=new GalateaSession(new GalateaPlanner.Tree(root,List.of(root,root.up()),0,0,false));
        session.fail(root);assertEquals(root.up(),session.choose(p->true,Comparator.comparingInt(BlockPos::y)).orElseThrow());
        assertTrue(session.advance(true,false,false));assertEquals(root,session.choose(p->true,Comparator.comparingInt(BlockPos::y)).orElseThrow());
    }
    @Test void figSmallTreeBoundaryIsAHardAdmissionBeforeDistanceRanking() {
        var player=new Vec3(.5,70.5,.5);
        var admitted=tree(199,new BlockPos(10,70,0));
        var denied=tree(200,root);
        assertTrue(Double.isFinite(GalateaPlanner.score(admitted,player,0,null,null,true)));
        assertEquals(Double.POSITIVE_INFINITY,GalateaPlanner.score(denied,player,0,null,null,true));
    }
    @Test void admittedSmallTreeRankingHasNoHeightOrTwentyFourLogSurrogate() {
        var player=new Vec3(.5,70.5,.5);
        var tall=tree(199,root);
        assertEquals(GalateaPlanner.score(tall,player,0,null,null,false),GalateaPlanner.score(tall,player,0,null,null,true));
    }
    @Test void optionOffDoesNotImposeTheSmallTreeBoundary() {
        assertTrue(Double.isFinite(GalateaPlanner.score(tree(200,root),new Vec3(.5,70.5,.5),0,null,null,false)));
        assertTrue(Double.isFinite(GalateaPlanner.score(tree(256,root),new Vec3(.5,70.5,.5),0,null,null,false)));
    }
    @Test void fullComponentBoundAndUnavailableGeometryRemainRejected() {
        var logs=new LinkedHashSet<>(tree(256,root).logs());
        assertEquals(256,GalateaPlanner.component(root,logs::contains,p->true,256).orElseThrow().logs().size());
        logs.add(root.up(256));assertTrue(GalateaPlanner.component(root,logs::contains,p->true,256).isEmpty());
        var partial=new HashSet<>(tree(200,root).logs());
        assertTrue(GalateaPlanner.component(root,partial::contains,p->!p.equals(root.up(199)),256).isEmpty());
    }
    private static GalateaPlanner.Tree tree(int count,BlockPos root) {
        var logs=new ArrayList<BlockPos>();for(int i=0;i<count;i++)logs.add(root.up(i));
        return new GalateaPlanner.Tree(root,logs,0,0,false);
    }
    @Test void smallTreeOptionIsExplicitlyFigOnly() {
        var large=tree(200,root);var player=new Vec3(.5,70.5,.5);
        assertEquals(Double.POSITIVE_INFINITY,GalateaPlanner.score(GalateaPlanner.Kind.FIG,large,player,0,null,null,true));
        for(var kind:List.of(GalateaPlanner.Kind.MANGROVE,GalateaPlanner.Kind.MATERIALS))
            assertEquals(GalateaPlanner.score(kind,large,player,0,null,null,false),GalateaPlanner.score(kind,large,player,0,null,null,true));
    }
    @Test void closestDeniedTreeCannotDisplaceAdmittedOrRemainAsOnlyCandidate() {
        var player=new Vec3(.5,70.5,.5);var denied=tree(200,root);var allowed=tree(199,new BlockPos(10,70,0));
        var candidates=new ArrayList<>(List.of(denied,allowed));
        candidates.removeIf(t->!GalateaPlanner.eligible(GalateaPlanner.Kind.FIG,t,true));
        candidates.sort(Comparator.comparingDouble(t->GalateaPlanner.score(GalateaPlanner.Kind.FIG,t,player,0,null,null,true)));
        assertEquals(List.of(allowed),candidates);
        assertFalse(GalateaPlanner.eligible(GalateaPlanner.Kind.FIG,denied,true));
        assertTrue(GalateaPlanner.eligible(GalateaPlanner.Kind.FIG,denied,false));
    }
}
