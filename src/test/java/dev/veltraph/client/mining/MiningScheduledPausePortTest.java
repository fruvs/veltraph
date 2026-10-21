package dev.veltraph.client.mining;
import dev.veltraph.client.config.*;
import dev.veltraph.client.mining.module.MiningModule;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MiningScheduledPausePortTest {
    static class Services extends MiningControllerTest.Services {
        Object ticket;String bound;boolean ready=true;int captures,releases,rebinds;
        public boolean scheduledPauseReady(int reserve){return ticket==null&&ready;}
        public boolean captureScheduledPause(Object value,int reserve){if(value==null||!scheduledPauseReady(reserve))return false;ticket=value;bound=identity;captures++;return true;}
        public void releaseScheduledPause(Object value){if(ticket==value){releases++;breaking.setBreaking(false);stopWalking();}}
        public boolean scheduledArrivalReady(Object value,int reserve){return ticket==value&&ready;}
        public boolean rebindScheduledPause(Object value,int reserve){if(!scheduledArrivalReady(value,reserve))return false;bound=identity;rebinds++;return true;}
        public boolean resumeScheduledPause(Object value,int reserve){if(!scheduledArrivalReady(value,reserve)||!bound.equals(identity))return false;ticket=null;return true;}
        public void cancelScheduledPause(Object value){if(ticket==value)ticket=null;}
    }
    private MiningModule module(Services services,MapConfigService config,MiningMode mode){var m=new MiningModule("scheduled_mining","Scheduled mining",mode,services,config);m.setEnabled(true);return m;}
    @Test void exactPauseKeepsEngineAndReleasesOnceWhileAllTickResetStopCallbacksAreGated(){
        var s=new Services();var m=module(s,new MapConfigService(),MiningMode.COBBLESTONE);var port=new MiningScheduledPausePort(m);var engine=m.controller();Object token=new Object();
        assertTrue(port.pause(token));assertTrue(port.pause(token));assertEquals(1,s.releases);assertTrue(m.isEnabled());assertSame(engine,m.controller());assertEquals(1,m.enableRuns());assertFalse(m.ownsGameplayInput());
        int stopped=s.stopped;s.input.jump=true;
        for(int i=0;i<20;i++){m.onTick();engine.tick();m.resetAtRewarp();engine.stop();}
        assertEquals(stopped,s.stopped);assertTrue(s.input.jump);assertEquals(MiningController.State.READY,engine.state());assertTrue(port.owns(token));assertFalse(engine.allowsTarget(MiningControllerTest.TARGET));
        assertThrows(IllegalStateException.class,engine::start);
    }
    @Test void foreignTokenCannotResumeRebindStopOrReplaceCurrentOwner(){
        var s=new Services();var m=module(s,new MapConfigService(),MiningMode.COBBLESTONE);var p=new MiningScheduledPausePort(m);Object token=new Object(),foreign=new Object();
        assertTrue(p.pause(token));assertFalse(p.pause(foreign));assertFalse(p.rebindAfterOwnedArrival(foreign));assertFalse(p.resume(foreign));p.stop(foreign);assertTrue(m.isEnabled());assertTrue(p.owns(token));
        assertTrue(p.resume(token));assertTrue(m.isEnabled());assertFalse(p.resume(token));assertEquals(1,m.enableRuns());
    }
    @Test void breakPickupWalkingHazardAndResourceLoanAreNotPauseBoundaries(){
        var s=new Services();var config=new MapConfigService();var m=module(s,config,MiningMode.MINE_DOWN);var p=new MiningScheduledPausePort(m);
        s.ready=false;assertFalse(p.pause(new Object()));s.ready=true;m.onTick();assertEquals(MiningController.State.BREAKING,m.controller().state());assertFalse(p.pause(new Object()));
        s.id="minecraft:air";m.onTick();assertEquals(MiningController.State.SETTLING,m.controller().state());assertFalse(p.pause(new Object()));
        for(int i=0;i<29;i++)m.onTick();assertTrue(m.controller().scheduledBoundary());
        var other=new Services();config.set(MiningConfig.ECHO_ROUTE,"1,64,0>0,63,0");var echo=module(other,config,MiningMode.ECHO);echo.onTick();assertEquals(MiningController.State.WALKING,echo.controller().state());assertFalse(new MiningScheduledPausePort(echo).pause(new Object()));
        other.safe=false;echo.onTick();assertFalse(new MiningScheduledPausePort(echo).pause(new Object()));
    }
    @Test void matchedRebindPreservesProgressEvenWhenRememberSessionIsDisabled(){
        var s=new Services();var cfg=new MapConfigService();cfg.set(MiningConfig.ECHO_ROUTE,"0,64,0>0,63,0;0,64,0>0,63,0");cfg.set(MiningConfig.ECHO_REMEMBER_LAST_SESSION,false);
        var m=module(s,cfg,MiningMode.ECHO);m.onTick();s.id="minecraft:air";m.onTick();for(int i=0;i<29;i++)m.onTick();s.id="minecraft:cobblestone";
        var p=new MiningScheduledPausePort(m);Object token=new Object();assertTrue(p.pause(token));assertEquals(1,m.controller().removed());assertEquals(1,m.controller().checkpoint());
        s.identity="fresh world";m.onTick();assertFalse(p.resume(token));s.ready=false;assertFalse(p.rebindAfterOwnedArrival(token));s.ready=true;
        assertTrue(p.rebindAfterOwnedArrival(token));assertTrue(p.owns(token));assertFalse(m.controller().allowsTarget(MiningControllerTest.TARGET));assertTrue(p.resume(token));
        assertEquals(1,m.enableRuns());assertEquals(1,m.controller().removed());assertEquals(1,m.controller().checkpoint());m.onTick();assertEquals(MiningController.State.BREAKING,m.controller().state());
    }
    @Test void configChangeCannotRebindAndManualDisableCancelsWithoutAffectingLaterTicket(){
        var s=new Services();var cfg=new MapConfigService();var m=module(s,cfg,MiningMode.COBBLESTONE);var p=new MiningScheduledPausePort(m);Object old=new Object();assertTrue(p.pause(old));
        cfg.set(MiningConfig.COBBLE_AUTO_SPRINT,true);assertFalse(p.rebindAfterOwnedArrival(old));assertFalse(p.resume(old));m.setEnabled(false);assertFalse(p.owns(old));assertNull(s.ticket);
        m.setEnabled(true);Object fresh=new Object();assertTrue(p.pause(fresh));p.stop(old);assertTrue(p.owns(fresh));p.stop(fresh);assertFalse(m.isEnabled());assertNull(s.ticket);
    }
    @Test void ordinaryWorldChangeStillLatchesAndCannotAcquireScheduledAuthority(){
        var s=new Services();var m=module(s,new MapConfigService(),MiningMode.MINE_DOWN);s.identity="foreign world";m.onTick();
        assertEquals(MiningController.State.PAUSED,m.controller().state());assertFalse(new MiningScheduledPausePort(m).pause(new Object()));assertFalse(m.controller().allowsTarget(MiningControllerTest.TARGET));
    }
}
