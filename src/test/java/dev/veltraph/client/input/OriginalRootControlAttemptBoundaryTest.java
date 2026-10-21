package dev.veltraph.client.input;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Exercises real generic control writes; it never constructs a Root producer or native authority. */
class OriginalRootControlAttemptBoundaryTest {
    @BeforeEach void before(){ScriptingInputLock.onRelease(()->{});ScriptingInputLock.releaseAll();}
    @AfterEach void after(){ScriptingInputLock.onRelease(()->{});ScriptingInputLock.releaseAll();}
    @Test void sameOwnerAttemptInvalidatesStrictHistoryButKeepsEffectiveControl(){
        ScriptingInputLock.acquire("original");var before=ScriptingInputLock.originalPublicationSnapshot();
        ScriptingInputLock.acquire("original");var after=ScriptingInputLock.originalPublicationSnapshot();
        assertTrue(before.current());assertFalse(before.publicationCurrent());assertFalse(before.sameHistory(after));
        assertTrue(after.publicationCurrent());assertTrue(after.immediateReturnedSingleFrom(before));
    }
    @Test void actualSetHasTwoAttemptBoundariesAndStillAcquiresOnce(){
        var before=ScriptingInputLock.originalPublicationSnapshot();ScriptingInputLock.set(true,"farm");
        var after=ScriptingInputLock.originalPublicationSnapshot();
        assertTrue(after.immediateReturnedSetFrom(before));assertFalse(after.immediateReturnedSingleFrom(before));
        assertTrue(ScriptingInputLock.isActive());assertEquals("farm",ScriptingInputLock.ownerModuleId());
    }
    @Test void releaseCallbackSeesClearedFlagsButCannotCaptureStrictNormalReturnYet(){
        var calls=new java.util.concurrent.atomic.AtomicInteger();
        ScriptingInputLock.onRelease(()->{assertFalse(ScriptingInputLock.isActive());assertNull(ScriptingInputLock.ownerModuleId());
            assertNotNull(ScriptingInputLock.controlSnapshot());assertNull(ScriptingInputLock.originalPublicationSnapshot());calls.incrementAndGet();});
        ScriptingInputLock.acquire("farm");ScriptingInputLock.releaseAll();
        assertEquals(1,calls.get());assertTrue(ScriptingInputLock.originalPublicationSnapshot().publicationCurrent());
    }
    @Test void throwingActualReleaseKeepsPrimaryAndEndsThePendingPublicationWindow(){
        var primary=new IllegalStateException("actual release");var calls=new java.util.concurrent.atomic.AtomicInteger();
        ScriptingInputLock.onRelease(()->{calls.incrementAndGet();throw primary;});ScriptingInputLock.acquire("farm");
        assertSame(primary,assertThrows(IllegalStateException.class,ScriptingInputLock::releaseAll));
        assertEquals(1,calls.get());assertFalse(ScriptingInputLock.isActive());assertNull(ScriptingInputLock.ownerModuleId());
        assertNotNull(ScriptingInputLock.originalPublicationSnapshot());
    }
    @Test void callbackReplacementAttemptInvalidatesStrictHistoryWithoutChangingEffectiveOwner(){
        ScriptingInputLock.acquire("farm");var before=ScriptingInputLock.originalPublicationSnapshot();
        ScriptingInputLock.onRelease(()->{});
        assertTrue(before.current());assertFalse(before.publicationCurrent());
    }
    @Test void invalidCallbackRegistrationCountsTheRealFailedAttempt(){
        ScriptingInputLock.acquire("farm");var before=ScriptingInputLock.originalPublicationSnapshot();
        assertThrows(NullPointerException.class,()->ScriptingInputLock.onRelease(null));
        assertTrue(before.current());assertFalse(before.publicationCurrent());assertNotNull(ScriptingInputLock.originalPublicationSnapshot());
    }
    @Test void missingIssuedProducerPerformsNoCompatibilityWrite(){
        ScriptingInputLock.acquire("original");var before=ScriptingInputLock.originalPublicationSnapshot();
        assertNull(ScriptingInputLock.publishIssuedRootOwnership(null,true,"foreign"));
        assertTrue(before.publicationCurrent());assertEquals("original",ScriptingInputLock.ownerModuleId());
        assertNull(ScriptingInputLock.originalReleaseInstallationFrom(null));
    }
    @Test void missingOrdinaryProducerRetainsGenericBehaviorButMintsNoReceipt(){
        assertNull(ScriptingInputLock.publishOriginalRootOwnership(null,true,"ordinary"));
        assertTrue(ScriptingInputLock.isActive());assertEquals("ordinary",ScriptingInputLock.ownerModuleId());
        assertNull(ScriptingInputLock.publishActualRootOwnership(null,false,null));assertFalse(ScriptingInputLock.isActive());
    }
}
