package dev.veltraph.client.mining;

import dev.veltraph.client.config.*;
import dev.veltraph.client.mining.module.MiningModule;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/** Actual module/controller calling the native adapter's shared ownership and hardware producer. */
class CobbleRootOwnedUsePortTest {
    static final class Port extends MiningControllerTest.Services {
        final List<Boolean> writes=new ArrayList<>();boolean keyDown;
        final RootUseKeyEffects<Object> fx=new RootUseKeyEffects<>((key,held)->{writes.add(held);keyDown=held;},key->keyDown);
        final Object key=new Object();Object p=new Object(),w=new Object(),g=new Object();
        MiningModule current,registered,captured;String lock="cobblestone";Object camera;
        @Override public boolean ownedUseHeld(Object caller,int run){return fx.ownedHeld(caller,run);}
        @Override public boolean setUseHeld(Object caller,int run,boolean held){
            if(held){
                if(current==null||!MiningUseHoldLease.mayAcquire(caller,run,current,current.enableRuns(),registered,lock,camera)){fx.retireOwned(caller,run);return false;}
                if(!fx.acquire(new MiningUseHoldLease(caller,run,p,w,g,"cobblestone"),key))return false;
                captured=current;return true;
            }
            if(captured==null)return true;if(!fx.ownedBy(caller,run))return false;
            var cameraLock=camera!=null&&camera!=captured?"foreign_camera_authority":lock;
            boolean result=fx.release(current,captured.enableRuns(),p,w,g,registered,cameraLock);
            if(!fx.hasLease())captured=null;return result;
        }
        void own(MiningModule m){current=m;registered=m;camera=m;}
    }
    static MapConfigService config(){var c=new MapConfigService();c.set(MiningConfig.COBBLE_AUTO_WALK,true);c.set(MiningConfig.COBBLE_HOLD_RIGHT_CLICK,true);return c;}
    static MiningModule module(Port p,MapConfigService c){return new MiningModule("cobblestone","Cobblestone",MiningMode.COBBLESTONE,p,c);}
    @Test void rootResponseQuiescenceAndNativeClearResumeTheSameEngineWithOnlyRequiredEdges(){
        var p=new Port();var m=module(p,config());p.own(m);m.setEnabled(true);m.onTick();var engine=m.controller();assertTrue(p.keyDown);
        m.suspendOwnedUse();assertFalse(p.keyDown);assertEquals(List.of(true,false),p.writes);
        m.onTick();m.onTick();assertSame(engine,m.controller());assertEquals(List.of(true,false,true),p.writes);
        p.keyDown=false;m.onTick();m.onTick();assertTrue(p.keyDown);assertEquals(List.of(true,false,true,true),p.writes);
    }
    @Test void sharedServicesCannotLetOldControllerHoldOrReleaseForReplacementModule(){
        var p=new Port();var cfg=config();var old=module(p,cfg);p.own(old);old.setEnabled(true);old.onTick();
        var next=module(p,cfg);p.own(next);next.setEnabled(true);next.onTick();assertTrue(p.keyDown);assertEquals(List.of(true),p.writes);
        old.controller().stop();old.onTick();old.suspendOwnedUse();old.setEnabled(false);assertTrue(p.keyDown);assertEquals(List.of(true),p.writes);
        next.suspendOwnedUse();assertFalse(p.keyDown);assertEquals(List.of(true,false),p.writes);
    }
    @Test void enabledSameActivationContextDriftPausesAndCancelsHardwareImmediately(){
        for(int drift=0;drift<3;drift++){
            var p=new Port();var m=module(p,config());p.own(m);m.setEnabled(true);m.onTick();assertTrue(p.keyDown);
            switch(drift){case 0->p.p=new Object();case 1->p.w=new Object();case 2->p.g=new Object();}
            p.identity="changed native context";m.onTick();assertTrue(m.isEnabled());assertEquals(MiningController.State.PAUSED,m.controller().state());
            assertFalse(p.keyDown);assertFalse(p.fx.hasLease());assertEquals(List.of(true,false),p.writes);
        }
    }
    @Test void foreignHelperUseRemainsHeldWhileOldEngineSuspendsAndStops(){
        var p=new Port();var m=module(p,config());p.own(m);m.setEnabled(true);m.onTick();p.camera=new Object();
        m.suspendOwnedUse();assertTrue(p.keyDown);assertTrue(p.fx.retired());assertEquals(List.of(true),p.writes);
        m.onTick();m.setEnabled(false);assertTrue(p.keyDown);assertEquals(List.of(true),p.writes);
    }
    @Test void olderRunRequestCannotModifyTheSameModulesNewActivation(){
        var p=new Port();var m=module(p,config());p.own(m);m.setEnabled(true);m.onTick();int oldRun=m.enableRuns();
        m.setEnabled(false);m.setEnabled(true);m.onTick();assertTrue(p.keyDown);
        int n=p.writes.size();assertFalse(p.setUseHeld(m,oldRun,false));assertFalse(p.setUseHeld(m,oldRun,true));assertTrue(p.keyDown);assertEquals(n,p.writes.size());
    }
    @Test void actualHydraLookAdmissionQuiescesMiningBeforeReactionAuthorityExists(){
        var p=new Port();var m=module(p,config());p.own(m);m.setEnabled(true);m.onTick();
        var reaction=new java.util.concurrent.atomic.AtomicReference<dev.veltraph.client.hydra.mc.McHydraResponder>();
        var responder=new dev.veltraph.client.hydra.mc.McHydraResponder(null,null,p.farm().rotation(),p.input,()->{},()->{
            assertFalse(reaction.get().hasActiveLocalReaction());m.suspendOwnedUse();assertFalse(p.keyDown);
        });reaction.set(responder);responder.fixAngle(40,5);assertTrue(responder.hasActiveLocalReaction());assertFalse(p.keyDown);
        assertEquals(List.of(true,false),p.writes);
    }
    @Test void actualHydraMovementAdmissionQuiescesMiningBeforeReactionAuthorityExists(){
        var p=new Port();var m=module(p,config());p.own(m);m.setEnabled(true);m.onTick();
        var reaction=new java.util.concurrent.atomic.AtomicReference<dev.veltraph.client.hydra.mc.McHydraResponder>();
        var responder=new dev.veltraph.client.hydra.mc.McHydraResponder(null,null,p.farm().rotation(),p.input,()->{},()->{
            assertFalse(reaction.get().hasActiveLocalReaction());m.suspendOwnedUse();assertFalse(p.keyDown);
        });reaction.set(responder);responder.move(dev.veltraph.client.hydra.response.HydraReaction.BACKUP,List.of(dev.veltraph.client.hydra.response.HydraMovementStep.back(4,false)));
        assertTrue(responder.hasActiveLocalReaction());assertFalse(p.keyDown);assertEquals(List.of(true,false),p.writes);
    }

    @Test void continuousDesiredTrueRevalidatesAllCustodyAndReturningAuthorityCannotAdoptStillDownKey(){
        for(int lost=0;lost<4;lost++){
            var p=new Port();var cfg=config();var m=module(p,cfg);p.own(m);m.setEnabled(true);m.onTick();assertTrue(p.keyDown);
            switch(lost){case 0->p.current=module(p,cfg);case 1->p.registered=module(p,cfg);case 2->p.lock="foreign_lock";case 3->p.camera=new Object();}
            m.onTick();assertTrue(p.fx.retired());assertTrue(p.keyDown);assertEquals(List.of(true),p.writes);
            p.own(m);p.lock="cobblestone";
            for(int i=0;i<6;i++)m.onTick();assertTrue(p.fx.retired());assertTrue(p.keyDown);assertEquals(List.of(true),p.writes);
            p.capacity=false;m.onTick();assertEquals(MiningController.State.PAUSED,m.controller().state());assertTrue(p.keyDown);assertEquals(List.of(true),p.writes);
            m.suspendOwnedUse();m.setEnabled(false);assertTrue(p.keyDown);assertEquals(List.of(true),p.writes);
        }
    }
    @Test void observedKeyUpAfterRetirementPermitsSameEngineValidResumeAndLaterOwnedStop(){
        var p=new Port();var m=module(p,config());p.own(m);m.setEnabled(true);m.onTick();var engine=m.controller();
        p.camera=new Object();m.onTick();assertTrue(p.fx.retired());p.own(m);m.onTick();assertTrue(p.fx.retired());assertEquals(List.of(true),p.writes);
        p.keyDown=false;m.onTick();m.onTick();assertFalse(p.fx.retired());assertSame(engine,m.controller());assertTrue(p.keyDown);assertEquals(List.of(true,true),p.writes);
        m.suspendOwnedUse();assertFalse(p.keyDown);assertEquals(List.of(true,true,false),p.writes);
    }
    @Test void genuineNewActivationCanAcquireAfterOldLeaseRetiresWithoutResurrectingOldCaller(){
        var p=new Port();var m=module(p,config());p.own(m);m.setEnabled(true);m.onTick();int oldRun=m.enableRuns();
        p.camera=new Object();m.onTick();assertTrue(p.fx.retired());m.setEnabled(false);assertTrue(p.keyDown);
        p.own(m);m.setEnabled(true);m.onTick();assertTrue(m.enableRuns()>oldRun);assertFalse(p.fx.retired());assertTrue(p.keyDown);
        assertFalse(p.setUseHeld(m,oldRun,false));assertFalse(p.setUseHeld(m,oldRun,true));assertTrue(p.keyDown);assertFalse(p.fx.retired());assertEquals(List.of(true),p.writes);
        m.suspendOwnedUse();assertFalse(p.keyDown);assertEquals(List.of(true,false),p.writes);
    }

}
