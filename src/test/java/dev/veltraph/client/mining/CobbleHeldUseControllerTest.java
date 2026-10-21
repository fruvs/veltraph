package dev.veltraph.client.mining;

import dev.veltraph.client.config.*;
import dev.veltraph.client.mining.module.MiningModule;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/** Actual mining controller transitions; a native port records every use edge. */
class CobbleHeldUseControllerTest {
    static final class Services extends MiningControllerTest.Services {
        final List<Boolean> useEdges=new ArrayList<>();boolean use, admit=true;
        @Override public boolean setUseHeld(Object caller,int activation,boolean held){if(held&&!admit)return false;if(use!=held){use=held;useEdges.add(held);}return true;}
        @Override public boolean ownedUseHeld(Object caller,int activation){return use;}
    }
    static final class Run {
        final Services s=new Services();final MapConfigService cfg=new MapConfigService();
        final MiningController c=new MiningController(MiningMode.COBBLESTONE,s,cfg);
        Run(){cfg.set(MiningConfig.COBBLE_AUTO_WALK,true);cfg.set(MiningConfig.COBBLE_HOLD_RIGHT_CLICK,true);c.start();c.tick();assertTrue(s.use);assertEquals(List.of(true),s.useEdges);}
        void released(){assertFalse(s.use);assertEquals(List.of(true,false),s.useEdges);assertFalse(s.breaking.isBreaking());}
    }
    @Test void sourceKeyDefaultsOffAndAlreadyRegisteredHolderPersistsIt(){
        var cfg=new MapConfigService();assertFalse(cfg.get(MiningConfig.COBBLE_HOLD_RIGHT_CLICK));
        assertTrue(PersistableKeys.all().contains(MiningConfig.COBBLE_HOLD_RIGHT_CLICK));
        var encoded=ConfigCodec.encode(java.util.Map.of(MiningConfig.COBBLE_HOLD_RIGHT_CLICK.name(),true),List.of());
        assertEquals(true,ConfigCodec.decode(encoded).settings().get(MiningConfig.COBBLE_HOLD_RIGHT_CLICK.name()));
        var s=new Services();cfg.set(MiningConfig.COBBLE_AUTO_WALK,true);var c=new MiningController(MiningMode.COBBLESTONE,s,cfg);c.start();c.tick();
        assertTrue(s.input.forward);assertFalse(s.use);assertTrue(s.useEdges.isEmpty());
    }
    @Test void holdsContinuouslyForwardReleasesOnceBackAndReacquiresOnNextForward(){
        var r=new Run();for(int i=0;i<40;i++)r.c.tick();assertEquals(List.of(true),r.s.useEdges);
        r.s.wall=true;r.c.tick();r.s.wall=false;assertFalse(r.c.forward());assertTrue(r.s.input.back);assertFalse(r.s.use);
        for(int i=0;i<10;i++)r.c.tick();assertEquals(List.of(true,false),r.s.useEdges);
        r.s.wall=true;r.c.tick();r.s.wall=false;assertTrue(r.c.forward());assertTrue(r.s.input.forward);assertTrue(r.s.use);
        r.c.tick();assertEquals(List.of(true,false,true),r.s.useEdges);
    }
    @Test void unsafeStepAndDisabledWalkingReleaseUseWithoutWeakeningBreakingGuards(){
        var r=new Run();r.s.safe=false;r.c.tick();assertFalse(r.s.use);assertFalse(r.s.input.forward);assertTrue(r.s.breaking.isBreaking());
        r.s.safe=true;r.c.tick();assertTrue(r.s.use);r.cfg.set(MiningConfig.COBBLE_AUTO_WALK,false);r.c.tick();assertFalse(r.s.use);
        assertEquals(List.of(true,false,true,false),r.s.useEdges);
    }
    @Test void disablingUseWhileMovingReleasesOnceAndDoesNotDisableMining(){
        var r=new Run();r.cfg.set(MiningConfig.COBBLE_HOLD_RIGHT_CLICK,false);for(int i=0;i<10;i++)r.c.tick();
        assertEquals(List.of(true,false),r.s.useEdges);assertTrue(r.s.input.forward);assertTrue(r.s.breaking.isBreaking());
    }
    @Test void toolHazardCapacityAndScreenPausesReleaseOwnedUse(){
        for(int issue=0;issue<4;issue++){
            var r=new Run();switch(issue){case 0->r.s.tool="Worn tool";case 1->r.s.problem="Lava";case 2->r.s.capacity=false;case 3->r.s.available=false;}
            r.c.tick();r.released();assertEquals(MiningController.State.PAUSED,r.c.state());r.c.tick();assertEquals(2,r.s.useEdges.size());
        }
    }
    @Test void changedWorldAndUnloadedTargetReleaseUseAndDoNotResumeInWrongContext(){
        var r=new Run();r.s.identity="another world";r.c.tick();r.released();r.c.tick();assertFalse(r.s.use);
        var other=new Run();other.s.loaded=false;other.c.tick();other.released();assertEquals(0,other.c.removed());
    }
    @Test void aimAndLandingWaitsReleasePreviousHold(){
        var r=new Run();r.s.aimed=false;r.c.tick();r.released();assertEquals(MiningController.State.AIMING,r.c.state());
        var landing=new Run();landing.s.player.onGround=false;landing.c.tick();landing.released();
    }
    @Test void removalRegenerationPreservesSafeMotionAndTimeoutStillReleasesUse(){
        var r=new Run();r.s.id="minecraft:air";r.c.tick();assertFalse(r.s.breaking.isBreaking());assertEquals(1,r.c.removed());assertEquals(MiningController.State.READY,r.c.state());
        for(int i=0;i<5;i++)r.c.tick();assertTrue(r.s.input.forward);assertTrue(r.s.use);assertEquals(List.of(true),r.s.useEdges);assertEquals(0,r.s.collected);
        var timed=new Run();timed.cfg.set(MiningConfig.MINING_BREAK_TIMEOUT_MS,250L);timed.s.clock.advanceMillis(250);timed.c.tick();timed.released();timed.c.tick();assertEquals(2,timed.s.useEdges.size());
    }
    @Test void manualModuleDisableReleasesOnceAndRepeatedCleanupHasNoNewUseEdges(){
        var s=new Services();var cfg=new MapConfigService();cfg.set(MiningConfig.COBBLE_AUTO_WALK,true);cfg.set(MiningConfig.COBBLE_HOLD_RIGHT_CLICK,true);
        var m=new MiningModule("cobblestone","Cobblestone",MiningMode.COBBLESTONE,s,cfg);m.setEnabled(true);m.onTick();assertTrue(s.use);
        m.setEnabled(false);assertFalse(s.use);assertEquals(List.of(true,false),s.useEdges);
        m.onTick();m.controller().stop();assertEquals(2,s.useEdges.size());assertFalse(s.input.forward);
    }
    @Test void ownedResourceLoanReleasesPriorUseAndDoesNotEraseBorrowersUse(){
        var r=new Run();r.s.id="minecraft:air";r.c.tick();assertTrue(r.s.use);
        assertEquals(MiningController.State.READY,r.c.state());assertTrue(r.c.scheduledBoundary());
        var token=new Object();assertTrue(r.c.beginOwnedSubflow(token));r.released();
        r.s.use=true;int edges=r.s.useEdges.size();for(int i=0;i<10;i++){r.c.tick();r.c.stop();}
        assertTrue(r.s.use);assertEquals(edges,r.s.useEdges.size());assertFalse(r.c.endOwnedSubflow(new Object()));assertTrue(r.c.endOwnedSubflow(token));
    }
    @Test void midoperationAimWaitDoesNotBecomeAResourceLoanBoundary(){
        var r=new Run();r.s.aimed=false;r.c.tick();r.released();
        assertEquals(MiningController.State.AIMING,r.c.state());assertFalse(r.c.scheduledBoundary());
        var token=new Object();assertFalse(r.c.beginOwnedSubflow(token));assertFalse(r.c.resourceOwnedBy(token));
    }
    @Test void nonCobbleModesNeverAcquireTheNewUseHold(){
        for(var mode:List.of(MiningMode.MINE_DOWN,MiningMode.ECHO)){
            var s=new Services();var cfg=new MapConfigService();cfg.set(MiningConfig.COBBLE_AUTO_WALK,true);cfg.set(MiningConfig.COBBLE_HOLD_RIGHT_CLICK,true);cfg.set(MiningConfig.ECHO_ROUTE,"0,64,0>0,63,0");
            var c=new MiningController(mode,s,cfg);c.start();c.tick();assertTrue(s.breaking.isBreaking());assertTrue(s.useEdges.isEmpty());c.stop();assertTrue(s.useEdges.isEmpty());
        }
    }
    @Test void deniedFirstOwnershipFrameRetriesWithoutPulsingAnAcceptedHold(){
        var s=new Services();s.admit=false;var cfg=new MapConfigService();cfg.set(MiningConfig.COBBLE_AUTO_WALK,true);cfg.set(MiningConfig.COBBLE_HOLD_RIGHT_CLICK,true);
        var c=new MiningController(MiningMode.COBBLESTONE,s,cfg);c.start();c.tick();assertFalse(s.use);assertTrue(s.useEdges.isEmpty());
        s.admit=true;c.tick();assertTrue(s.use);for(int i=0;i<5;i++)c.tick();assertEquals(List.of(true),s.useEdges);
    }
    @Test void actualModuleResponseCallbackQuiescesAndSameEngineReconcilesNativeClear(){
        var s=new Services();var cfg=new MapConfigService();cfg.set(MiningConfig.COBBLE_AUTO_WALK,true);cfg.set(MiningConfig.COBBLE_HOLD_RIGHT_CLICK,true);
        var m=new MiningModule("cobblestone","Cobblestone",MiningMode.COBBLESTONE,s,cfg);m.setEnabled(true);m.onTick();var engine=m.controller();
        assertTrue(s.use);m.suspendOwnedUse();assertFalse(s.use);assertEquals(List.of(true,false),s.useEdges);assertSame(engine,m.controller());assertTrue(m.isEnabled());
        m.onTick();assertTrue(s.use);assertEquals(List.of(true,false,true),s.useEdges);
        s.use=false; // Independent native/global hardware clear, without editing controller state.
        m.onTick();m.onTick();assertEquals(List.of(true,false,true,true),s.useEdges);assertTrue(s.use);
    }
    @Test void scheduledRestSignatureIncludesTheUseOption(){
        var s=new Services();var cfg=new MapConfigService();var m=new MiningModule("cobblestone","Cobblestone",MiningMode.COBBLESTONE,s,cfg);
        var before=m.scheduledSettingsSignature();cfg.set(MiningConfig.COBBLE_HOLD_RIGHT_CLICK,true);assertNotEquals(before,m.scheduledSettingsSignature());
    }
}
