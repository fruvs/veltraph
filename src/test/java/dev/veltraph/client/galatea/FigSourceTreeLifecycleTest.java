package dev.veltraph.client.galatea;

import dev.veltraph.client.world.BlockPos;
import dev.veltraph.client.world.Vec3;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FigSourceTreeLifecycleTest {
    private final BlockPos root=new BlockPos(0,70,0);
    private final FigSourceSelector.View view=new FigSourceSelector.View(root,new Vec3(.5,70,.5),new Vec3(.5,71.62,.5),0);
    @Test void unboundOrdinaryTreeRemainsAutomaticallyUsableWithoutManualCatalog() {
        var tree=new GalateaPlanner.Tree(root,List.of(root,root.up()),0,0,false);
        assertTrue(FigObservedDescriptors.parse("").find(tree).isEmpty());
        var w=new FigSourceGeometryTest.ObservedWorld();w.log(root,"oak");w.log(root.up(),"oak");w.standing.add(root.add(2,0,0));w.stemRays.addAll(tree.logs());
        var selector=new FigSourceSelector(new FigSourceGeometry(tree.root(),tree.logs(),w,1,0),w);
        var session=new GalateaSession(tree);
        assertTrue(selector.select(GalateaPlanner.AxeMode.STEM,session.remainingLogs(),session::canAttempt,view,false,new BlockPos(0,0,0)).isPresent());
    }
    @Test void partialCutKeepsOriginalDescriptorIdentityAndReacquiresRegrownLog() {
        var tree=new GalateaPlanner.Tree(root,List.of(root,root.up(),root.up(2)),0,0,false);var session=new GalateaSession(tree);
        var w=new FigSourceGeometryTest.ObservedWorld();for(var p:tree.logs())w.log(p,"oak");w.standing.add(root.add(2,0,0));w.stemRays.addAll(tree.logs());
        var geometry=new FigSourceGeometry(root,tree.logs(),w,1,0);var selector=new FigSourceSelector(geometry,w);
        w.types.remove(root);session.observe(w::log);
        assertEquals(root,geometry.base());assertSame(tree,session.tree());assertEquals(List.of(root.up(),root.up(2)),session.remainingLogs());
        assertTrue(selector.select(GalateaPlanner.AxeMode.STEM,session.remainingLogs(),session::canAttempt,view,false,root).isPresent());
        w.log(root,"oak");session.observe(w::log);
        assertEquals(3,session.remaining());assertTrue(session.canAttempt(root));
        assertTrue(selector.select(GalateaPlanner.AxeMode.STEM,session.remainingLogs(),session::canAttempt,view,false,root).isPresent());
    }
    @Test void exclusionsSurviveRegrowthAndModeChangesIndependentlyOfTemporaryFailures() {
        var tree=new GalateaPlanner.Tree(root,List.of(root,root.up()),0,0,false);var session=new GalateaSession(tree);
        session.exclude(root);session.fail(root.up());
        assertFalse(session.canAttempt(root));assertFalse(session.canAttempt(root.up()));
        session.setMode(GalateaPlanner.AxeMode.JUMPING);session.observe(p->true);
        assertFalse(session.canAttempt(root));assertTrue(session.canAttempt(root.up()));
    }
    @Test void restartWithShiftedPartialRootAndLaterRegrowthDoesNotRequireCaptureRewrite() {
        var bindings=new FigObservedDescriptors(List.of(new FigObservedDescriptors.Descriptor(root,root,List.of(root,root.up(),root.up(2)),"Captured source base")));
        var partial=new GalateaPlanner.Tree(root.up(),List.of(root.up(),root.up(2)),0,0,false);
        assertTrue(bindings.find(partial).isEmpty());
        var regrown=new GalateaPlanner.Tree(root,List.of(root,root.up(),root.up(2)),0,0,false);
        assertEquals(root,bindings.find(regrown).orElseThrow().base());
    }
}
