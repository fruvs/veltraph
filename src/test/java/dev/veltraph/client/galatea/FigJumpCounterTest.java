package dev.veltraph.client.galatea;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class FigJumpCounterTest {
    @Test void sourceDelayUsesRawSettingAndExclusiveJitter() {
        var state=new FigJumpCounter();state.enable(3,-10);assertEquals(190,state.delayMs());
        state.enable(4,9);assertEquals(159,state.delayMs());
        state.enable(8,-10);assertEquals(0,state.delayMs());
        state.enable(-2,0);assertEquals(450,state.delayMs());
        assertThrows(IllegalArgumentException.class,()->state.enable(3,10));
    }
    @Test void absentTargetUsesStrictDelayAndRotationGateThenAvailableClears() {
        var state=new FigJumpCounter();state.enable(3,0);
        state.observeTarget(100,false,true,false,false);assertEquals(100L,state.absentSince());
        state.observeTarget(300,false,true,false,false);assertFalse(state.helperComplete());
        state.observeTarget(301,false,true,false,true);assertFalse(state.helperComplete());
        state.observeTarget(302,false,true,false,false);assertTrue(state.helperComplete());
        state.observeTarget(303,false,false,false,false);assertFalse(state.helperComplete());assertNull(state.absentSince());
    }
    @Test void zeroImmediatelyCompletesWithoutClearingRetainedTimestamp() {
        var state=new FigJumpCounter();state.enable(3,0);state.observeTarget(50,false,true,false,false);
        state.observeTarget(51,true,false,false,true);assertTrue(state.helperComplete());assertEquals(50L,state.absentSince());
        state.observeTarget(52,false,false,false,false);assertFalse(state.helperComplete());assertNull(state.absentSince());
    }
    @Test void excludedAvailableBlockStillStartsCompletionWindow() {
        var state=new FigJumpCounter();state.enable(3,0);state.observeTarget(50,false,false,true,false);
        state.observeTarget(251,false,false,true,false);assertTrue(state.helperComplete());
    }
    @Test void realCounterChangesLazyToAverageThenStrongAndResetAirborneToLazy() {
        var state=new FigJumpCounter();state.enable(3,0);state.enterJumpOrThrow();
        state.observeJumpTick(true,true);assertEquals(1,state.counter());
        assertEquals(FigJumpAimPolicy.Mode.AVERAGE,FigJumpAimPolicy.effective(FigJumpAimPolicy.Mode.LAZY,state.counter()));
        state.calculatingCandidate();state.observeTarget(0,false,false,false,false);
        state.observeJumpTick(true,true);assertEquals(2,state.counter());
        assertEquals(FigJumpAimPolicy.Mode.STRONG,FigJumpAimPolicy.effective(FigJumpAimPolicy.Mode.LAZY,state.counter()));
        state.observeTarget(1,false,true,false,false);state.observeTarget(202,false,true,false,false);
        state.observeJumpTick(false,true);assertEquals(-1,state.counter());
        assertEquals(FigJumpAimPolicy.Mode.LAZY,FigJumpAimPolicy.effective(FigJumpAimPolicy.Mode.LAZY,state.counter()));
    }
    @Test void excludedTargetsRemainExcludedAcrossModeChanges() {
        var p=new dev.veltraph.client.world.BlockPos(2,70,0);
        var q=new dev.veltraph.client.world.BlockPos(2,71,0);
        var tree=new GalateaPlanner.Tree(p,java.util.List.of(p,q),0,0,false);
        var session=new GalateaSession(tree);session.exclude(p);
        session.setMode(GalateaPlanner.AxeMode.JUMPING);
        assertTrue(session.excluded(p));assertEquals(q,session.choose(x->true,java.util.Comparator.comparingInt(dev.veltraph.client.world.BlockPos::y)).orElseThrow());
        session.setMode(GalateaPlanner.AxeMode.THROWING);assertTrue(session.excluded(p));
        assertFalse(new GalateaSession(tree).excluded(p));
    }
    @Test void modeResetPersistsUntilCalculationAndNewActivationClearsLifetime() {
        var state=new FigJumpCounter();state.enable(3,0);state.enterJumpOrThrow();
        state.observeJumpTick(true,true);state.observeJumpTick(true,true);assertEquals(1,state.counter());
        state.calculatingCandidate();state.observeJumpTick(true,true);assertEquals(2,state.counter());
        state.observeJumpTick(true,false);assertEquals(2,state.counter());
        state.enable(4,0);assertEquals(0,state.counter());assertFalse(state.figReset());assertFalse(state.helperComplete());assertNull(state.absentSince());
    }
}
