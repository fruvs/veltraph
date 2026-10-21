package dev.veltraph.client.failsafe.response;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class FailsafeOwnedResetTest {
    private static class Sink implements FailsafeActionSink {
        boolean held,owned;int writes,releases,looks;Runnable yawCallback=()->{},releaseCallback=()->{};FailsafeResponseRunner runner;boolean activeDuringRelease;
        public float currentYaw(){yawCallback.run();return 0;}public float currentPitch(){return 0;}
        public void applyLook(float yaw,float pitch){looks++;}
        public void setMovement(boolean f,boolean b,boolean l,boolean r,boolean j,boolean s){held=f||b||l||r||j||s;owned=held;writes++;}
        public void setAttack(boolean held){this.held=held;owned=held;writes++;}public void setUse(boolean held){this.held=held;owned=held;writes++;}
        public void sendCommand(String command){}
        public boolean hasOwnedControls(){return owned;}
        public void releaseAll(){activeDuringRelease=runner!=null&&runner.active();releaseCallback.run();held=owned=false;writes++;releases++;}
    }
    private FailsafeResponsePlan move(){return new FailsafeResponsePlan("move",List.of(FailsafeResponseAction.move(true,false,false,false,20)));}
    private FailsafeResponsePlan look(){return new FailsafeResponsePlan("look",List.of(FailsafeResponseAction.look(30,0,20)));}
    @Test void idleResetDoesNotPublishFalseToForeignHeldState(){var r=new FailsafeResponseRunner();var s=new Sink();s.held=true;for(int i=0;i<40;i++)r.reset(s);assertTrue(s.held);assertEquals(0,s.writes);assertEquals(0,s.releases);}
    @Test void emptyStartDoesNotTakeForeignControls(){var r=new FailsafeResponseRunner();var s=new Sink();s.held=true;r.start(new FailsafeResponsePlan("empty",List.of()),6,20,s);assertFalse(r.active());assertTrue(s.held);assertEquals(0,s.writes);r.reset(s);assertTrue(s.held);assertEquals(0,s.writes);}
    @Test void idleResetCanReleaseIndependentlyIssuedOwnSinkControls(){var r=new FailsafeResponseRunner();var s=new Sink();s.held=s.owned=true;r.reset(s);assertFalse(s.held);assertEquals(1,s.releases);r.reset(s);assertEquals(1,s.releases);}
    @Test void activeResetKeepsRealResponseOwnershipUntilItsReleaseReturns(){var r=new FailsafeResponseRunner();var s=new Sink();s.runner=r;r.start(move(),6,40,s);r.tick(s);assertTrue(s.held);r.reset(s);assertTrue(s.activeDuringRelease);assertFalse(s.held);assertFalse(r.active());}
    @Test void resetUsesActualOldSinkInsteadOfForeignArgument(){var r=new FailsafeResponseRunner();var old=new Sink();var foreign=new Sink();foreign.held=true;r.start(move(),6,40,old);r.tick(old);r.reset(foreign);assertFalse(old.held);assertTrue(foreign.held);assertEquals(0,foreign.writes);}
    @Test void wrongSinkCannotAdvanceAnActualBoundPlan(){var r=new FailsafeResponseRunner();var own=new Sink();var foreign=new Sink();r.start(move(),6,40,own);r.tick(foreign);assertEquals(0,own.writes);assertEquals(0,foreign.writes);assertEquals(40,r.remainingTicks());r.tick(own);assertTrue(own.held);}
    @Test void yawCallbackCannotMakeOldLookPublishIntoNewPlan(){var r=new FailsafeResponseRunner();var s=new Sink();r.start(look(),6,40,s);boolean[] fired={false};s.yawCallback=()->{if(!fired[0]){fired[0]=true;r.start(move(),6,40,s);}};r.tick(s);assertEquals("move",r.label());assertEquals(0,s.looks);r.tick(s);assertTrue(s.held);}
    @Test void primeFailureDoesNotFinishNewerResponseStartedByItsCallback(){var r=new FailsafeResponseRunner();var s=new Sink();boolean[] fired={false};s.yawCallback=()->{if(!fired[0]){fired[0]=true;r.start(move(),6,40,s);throw new IllegalStateException("old look callback failed");}};assertThrows(IllegalStateException.class,()->r.start(look(),6,40,s));assertEquals("move",r.label());r.tick(s);assertTrue(s.held);}
    @Test void cleanupCallbackCannotBeOverwrittenByOlderResetLogicalState(){var r=new FailsafeResponseRunner();var s=new Sink();r.start(move(),6,40,s);r.tick(s);boolean[] fired={false};s.releaseCallback=()->{if(!fired[0]){fired[0]=true;r.start(look(),6,40,s);}};r.reset(s);assertTrue(r.active());assertEquals("look",r.label());}
    @Test void oldCleanupUsesCapturedControlsRatherThanRecapturingNewPublication(){var r=new FailsafeResponseRunner();
        class ScopedSink extends Sink {Object publication=new Object();Object captured;public Object captureOwnedControls(){return publication;}public void releaseOwnedControls(Object old){captured=old;var replacement=new Object();publication=replacement;owned=true;r.start(look(),6,40,this);if(publication==old){owned=held=false;}}}
        var s=new ScopedSink();r.start(move(),6,40,s);r.tick(s);var original=s.publication;r.reset(s);assertSame(original,s.captured);assertNotSame(original,s.publication);assertTrue(s.owned);assertEquals("look",r.label());}
}
