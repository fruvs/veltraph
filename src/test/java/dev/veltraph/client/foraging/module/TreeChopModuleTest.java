package dev.veltraph.client.foraging.module;

import dev.veltraph.client.config.MapConfigService;
import dev.veltraph.client.foraging.BlockCoord;
import dev.veltraph.client.foraging.ForagingServices;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertNotSame;

/** Glue tests for the tree-chop module (the break loop is covered by TreeBreakerTest). */
class TreeChopModuleTest {

    private static final class FakeServices implements ForagingServices {
        boolean available = true;
        boolean onLog = true;
        BlockCoord target = new BlockCoord(1, 64, 1);
        double dist = 3.0;
        boolean moving;
        Boolean lastBreaking;
        int rebinds;
        @Override public boolean rebindForScheduledRest(){rebinds++;return available;}
        @Override public boolean scheduledAbilityIdle(){return true;}
        @Override public boolean available() {
            return available;
        }
        @Override public boolean lookingAtLog() {
            return onLog;
        }
        @Override public BlockCoord targetLog() {
            return target;
        }
        @Override public double eyeDistance() {
            return dist;
        }
        @Override public boolean moving() {
            return moving;
        }
        @Override public void setBreaking(boolean breaking) {
            lastBreaking = breaking;
        }
    }

    private TreeChopModule module(FakeServices s) {
        return new TreeChopModule(s, new MapConfigService());
    }

    @Test
    void doesNothingWhenNeverEnabled() {
        FakeServices s = new FakeServices();
        module(s).onTick(); // breaker null
        assertEquals(null, s.lastBreaking);
    }

    @Test
    void holdsBreakOnLogWhenEnabled() {
        FakeServices s = new FakeServices();
        TreeChopModule m = module(s);
        m.setEnabled(true);
        m.onTick();
        assertTrue(s.lastBreaking, "should hold the break key on a reachable log");
    }

    @Test
    void releasesWhenNotOnLog() {
        FakeServices s = new FakeServices();
        s.onLog = false;
        s.target = null;
        TreeChopModule m = module(s);
        m.setEnabled(true);
        m.onTick();
        assertFalse(s.lastBreaking, "should release the break key off a log");
    }

    @Test
    void disableReleasesTheBreakKey() {
        FakeServices s = new FakeServices();
        TreeChopModule m = module(s);
        m.setEnabled(true);
        m.onTick();
        m.setEnabled(false); // onDisable -> release
        assertFalse(s.lastBreaking);
    }
    @Test void pausePreservesBreakerSuppressesCallbacksAndRequiresExactRebind(){
        FakeServices s=new FakeServices();s.onLog=false;s.target=null;var m=module(s);m.setEnabled(true);m.onTick();Object engine=m.scheduledEngineIdentity(),ticket=new Object();
        assertTrue(m.pauseForScheduledRest(ticket));assertFalse(m.ownsGameplayInput());assertFalse(m.pauseForScheduledRest(ticket));assertFalse(m.resumeFromScheduledRest(ticket));
        s.onLog=true;s.target=new BlockCoord(2,64,2);m.onTick();m.onFeedback("You cannot damage a tree while resting");assertFalse(s.lastBreaking);assertSame(engine,m.scheduledEngineIdentity());
        assertFalse(m.rebindForScheduledRest(new Object()));assertEquals(0,s.rebinds);assertTrue(m.rebindForScheduledRest(ticket));assertTrue(m.scheduledRestOwnedBy(ticket));assertTrue(m.resumeFromScheduledRest(ticket));assertTrue(m.ownsGameplayInput());m.onTick();assertTrue(s.lastBreaking);assertSame(engine,m.scheduledEngineIdentity());
    }
    @Test void activeTargetAndManualRestartCannotReusePauseTicket(){
        FakeServices s=new FakeServices();var m=module(s);m.setEnabled(true);m.onTick();Object old=new Object();assertFalse(m.pauseForScheduledRest(old));s.onLog=false;s.target=null;m.onTick();assertFalse(m.pauseForScheduledRest(old),"Pending break outcome must settle");m.setEnabled(false);m.setEnabled(true);assertTrue(m.pauseForScheduledRest(old));Object engine=m.scheduledEngineIdentity();m.setEnabled(false);m.setEnabled(true);assertFalse(m.scheduledRestOwnedBy(old));assertFalse(m.resumeFromScheduledRest(old));assertNotSame(engine,m.scheduledEngineIdentity());
    }
}
