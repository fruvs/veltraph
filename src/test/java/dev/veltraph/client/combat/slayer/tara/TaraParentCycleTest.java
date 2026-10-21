package dev.veltraph.client.combat.slayer.tara;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TaraParentCycleTest {
    @Test void absentChildCannotAcquireOrProgress(){var p=new TaraParentCycle();var boss=new Object();assertFalse(p.acquire(null,boss));assertFalse(p.waitingEffect(null,boss,true));assertFalse(p.eggEffect(null,boss,true));assertEquals(0,p.completed());}
    @Test void onlyBothIndependentEffectsCanReleaseTheSameChild(){var p=new TaraParentCycle();var child=new Object();var boss=new Object();assertTrue(p.acquire(child,boss));assertTrue(p.pending());assertFalse(p.eggEffect(child,boss,true));assertFalse(p.waitingEffect(child,boss,false));assertTrue(p.owns(child,boss));assertTrue(p.waitingEffect(child,boss,true));assertTrue(p.pending());assertFalse(p.eggEffect(child,boss,false));assertTrue(p.eggEffect(child,boss,true));assertEquals(1,p.completed());assertFalse(p.pending());assertFalse(p.eggEffect(child,boss,true));}
    @Test void replacingEitherNativeBossOrEnabledChildNeverAcknowledges(){var p=new TaraParentCycle();var child=new Object();var boss=new Object();p.acquire(child,boss);assertFalse(p.waitingEffect(new Object(),boss,true));assertFalse(p.waitingEffect(child,new Object(),true));p.waitingEffect(child,boss,true);assertFalse(p.eggEffect(new Object(),boss,true));assertFalse(p.eggEffect(child,new Object(),true));assertEquals(0,p.completed());assertTrue(p.pending());}
    @Test void stopCannotBecomeACompletionOrAReusableLease(){var p=new TaraParentCycle();var child=new Object();var boss=new Object();p.acquire(child,boss);p.fail();assertFalse(p.pending());assertFalse(p.owns(child,boss));assertFalse(p.waitingEffect(child,boss,true));assertFalse(p.eggEffect(child,boss,true));assertFalse(p.acquire(child,boss));assertEquals(0,p.completed());}
    @Test void nextCycleRequiresFreshAcquisitionAndWaitingEffect(){var p=new TaraParentCycle();var child=new Object();var boss=new Object();p.acquire(child,boss);p.waitingEffect(child,boss,true);p.eggEffect(child,boss,true);assertTrue(p.acquire(child,boss));assertFalse(p.eggEffect(child,boss,true));assertEquals(TaraParentCycle.Phase.WAITING,p.phase());assertEquals(1,p.completed());}
}
