package dev.veltraph.client.service.mc;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RootPassiveCameraSlotTest {
    @Test void retainedOwnerContinuesOnce(){Object old=new Object(),next=new Object();var slot=new RootPassiveCameraSlot<>(old);
        assertTrue(slot.prepare(next,old));assertTrue(slot.claim(old));assertSame(next,slot.successor());assertFalse(slot.claim(old));}
    @Test void emptyCustodyContinuesWithoutInventingPublication(){var slot=new RootPassiveCameraSlot<Object>(null);
        assertTrue(slot.prepare(null,null));assertTrue(slot.claim(null));assertNull(slot.successor());}
    @Test void replacementBeforePreparationIsPreserved(){Object old=new Object(),foreign=new Object();var slot=new RootPassiveCameraSlot<>(old);
        assertFalse(slot.prepare(new Object(),foreign));assertFalse(slot.claim(old));}
    @Test void replacementAfterPreparationCannotBeOverwritten(){Object old=new Object(),foreign=new Object();var slot=new RootPassiveCameraSlot<>(old);
        assertTrue(slot.prepare(new Object(),old));assertFalse(slot.claim(foreign));assertFalse(slot.claim(old));}
    @Test void noPreparedSuccessorCannotBeClaimed(){Object old=new Object();var slot=new RootPassiveCameraSlot<>(old);
        assertFalse(slot.claim(old));assertFalse(slot.prepare(new Object(),old));}
    @Test void duplicatePreparationPermanentlyRefuses(){Object old=new Object();var slot=new RootPassiveCameraSlot<>(old);
        assertTrue(slot.prepare(new Object(),old));assertFalse(slot.prepare(new Object(),old));assertFalse(slot.claim(old));}
    @Test void staleNullCannotAdoptNewOwner(){var slot=new RootPassiveCameraSlot<Object>(null);
        assertTrue(slot.prepare(null,null));assertFalse(slot.claim(new Object()));assertFalse(slot.claim(null));}
    @Test void successorUnavailableBeforeClaim(){Object old=new Object();var slot=new RootPassiveCameraSlot<>(old);
        assertTrue(slot.prepare(new Object(),old));assertThrows(IllegalStateException.class,slot::successor);}
    @Test void foreignThreadCannotPrepare()throws Exception{Object old=new Object();var slot=new RootPassiveCameraSlot<>(old);
        var result=new java.util.concurrent.atomic.AtomicReference<Boolean>();
        Thread t=new Thread(()->result.set(slot.prepare(new Object(),old)));t.start();t.join();assertEquals(Boolean.FALSE,result.get());assertFalse(slot.prepare(new Object(),old));}
    @Test void foreignThreadCannotConsumePrepared()throws Exception{Object old=new Object();var slot=new RootPassiveCameraSlot<>(old);
        assertTrue(slot.prepare(new Object(),old));var result=new java.util.concurrent.atomic.AtomicReference<Boolean>();
        Thread t=new Thread(()->result.set(slot.claim(old)));t.start();t.join();assertEquals(Boolean.FALSE,result.get());assertFalse(slot.claim(old));}
}
