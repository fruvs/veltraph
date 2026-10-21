package dev.veltraph.client.navigation;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class GroundPublicationFenceTest {
    private static final class Actor implements GroundPublicationActor {
        boolean valid=true;long ticket=11;int publications,cleanup;Runnable during=()->{};
        public boolean current(){return valid;}
        public long publishYaw(float yaw){publications++;during.run();return ticket;}
        public void cleanupOwned(){if(valid)cleanup++;}
    }
    @Test void rawOrForeignInputCannotBindToAChosenPublicationActor(){
        var actor=new Actor();var input=new GroundWalkInputsTest.Inputs();
        assertThrows(IllegalArgumentException.class,()->GroundPublicationInputs.require(input,actor));
        final class Guarded extends GroundWalkInputsTest.Inputs implements GroundPublicationInputs {
            final GroundPublicationActor owner;Guarded(GroundPublicationActor owner){this.owner=owner;}public GroundPublicationActor actor(){return owner;}
        }
        var own=new Guarded(actor);assertSame(own,GroundPublicationInputs.require(own,actor));
        assertThrows(IllegalArgumentException.class,()->GroundPublicationInputs.require(new Guarded(new Actor()),actor));
    }
    @Test void firstOutputUsesActualPositivePublisherTicketBeforeNativeEpochSyncAndKeys(){
        var actor=new Actor();var order=new StringBuilder();actor.during=()->order.append("publish;");
        assertTrue(GroundPublicationFence.publish(actor,()->true,true,42,()->order.append("sync;"),()->order.append("keys;")));
        assertEquals("publish;sync;keys;",order.toString());assertEquals(1,actor.publications);
    }
    @Test void revokedSameParentGenerationAfterGeometryPublishesNeitherCameraNorKeys(){
        var actor=new Actor();actor.valid=false;var effects=new AtomicInteger();
        assertFalse(GroundPublicationFence.publish(actor,()->true,true,42,effects::incrementAndGet,effects::incrementAndGet));
        assertEquals(0,actor.publications);assertEquals(0,effects.get());
    }
    @Test void changedNativeScopeAfterAllDiagnosticReadsPublishesNoEffects(){
        var actor=new Actor();var effects=new AtomicInteger();
        assertFalse(GroundPublicationFence.publish(actor,()->false,true,42,effects::incrementAndGet,effects::incrementAndGet));
        assertEquals(0,actor.publications);assertEquals(0,effects.get());
    }
    @Test void rejectedZeroOrNegativeActualTicketCannotSyncEpochOrPublishKeys(){
        for(long ticket:new long[]{0,-1}){var actor=new Actor();actor.ticket=ticket;var effects=new AtomicInteger();
            assertFalse(GroundPublicationFence.publish(actor,()->true,true,42,effects::incrementAndGet,effects::incrementAndGet));
            assertEquals(1,actor.publications);assertEquals(0,effects.get());}
    }
    @Test void ownerChangedDuringActualPublicationCannotSyncEpochOrPublishKeys(){
        var actor=new Actor();actor.during=()->actor.valid=false;var effects=new AtomicInteger();
        assertFalse(GroundPublicationFence.publish(actor,()->true,true,42,effects::incrementAndGet,effects::incrementAndGet));assertEquals(0,effects.get());
    }
    @Test void ownerChangedAfterOwnCameraSyncCannotPublishAnyKey(){
        var actor=new Actor();var keys=new AtomicInteger();
        assertFalse(GroundPublicationFence.publish(actor,()->true,true,42,()->actor.valid=false,keys::incrementAndGet));assertEquals(0,keys.get());
    }
    @Test void nativeEpochSynchronizesOnlyTheActorsJustPublishedOwnTicket(){
        var actor=new Actor();var nativeEpoch=new AtomicInteger(3);var expectedEpoch=new AtomicInteger(3);var keys=new AtomicInteger();actor.during=()->nativeEpoch.set(4);
        assertTrue(GroundPublicationFence.publish(actor,()->nativeEpoch.get()==expectedEpoch.get(),true,42,()->expectedEpoch.set(nativeEpoch.get()),keys::incrementAndGet));assertEquals(1,keys.get());
    }
    @Test void arrivalDoesNotCreateACameraTicketAndStillFencesEveryLaterOutput(){
        var actor=new Actor();var sync=new AtomicInteger();var keys=new AtomicInteger();
        assertTrue(GroundPublicationFence.publish(actor,()->true,false,Float.NaN,sync::incrementAndGet,keys::incrementAndGet));
        assertEquals(0,actor.publications);assertEquals(0,sync.get());assertEquals(1,keys.get());
        actor.valid=false;assertFalse(GroundPublicationFence.publish(actor,()->true,false,0,sync::incrementAndGet,keys::incrementAndGet));assertEquals(1,keys.get());
    }
    @Test void callbackGenerationRevocationAbortsLaterKeysWithoutThrowingToParentDispatcher(){
        var actor=new Actor();var first=new AtomicInteger();var later=new AtomicInteger();
        boolean result=GroundPublicationFence.publish(actor,()->true,true,42,()->{},()->{
            if(!actor.current())throw new GroundPublicationRevokedException("Before first key");
            first.incrementAndGet();actor.valid=false; // An external same-parent generation replaces this leg.
            if(!actor.current())throw new GroundPublicationRevokedException("Before second key");
            later.incrementAndGet();
        });
        assertFalse(result);assertEquals(1,first.get());assertEquals(0,later.get());
    }
    @Test void unrelatedKeyCallbackFailureIsNotConvertedToAScopeRefusal(){
        var actor=new Actor();var failure=new IllegalStateException("Unrelated native failure");
        var observed=assertThrows(IllegalStateException.class,()->GroundPublicationFence.publish(actor,()->true,true,42,()->{},()->{throw failure;}));
        assertSame(failure,observed);
    }
    @Test void revokeDuringIndividuallyGuardedKeysRejectsFinalSuccess(){
        var actor=new Actor();var keys=new AtomicInteger();
        assertFalse(GroundPublicationFence.publish(actor,()->true,true,42,()->{},()->{keys.incrementAndGet();actor.valid=false;}));assertEquals(1,keys.get());
    }
}
