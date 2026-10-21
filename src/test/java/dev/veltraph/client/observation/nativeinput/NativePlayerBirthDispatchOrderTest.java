package dev.veltraph.client.observation.nativeinput;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class NativePlayerBirthDispatchOrderTest {
 @Test void innerHandlerNormalIsNotOuterProcessorNormal(){var p=new Object();var o=new NativePlayerBirthDispatchOrder(p);assertFalse(o.complete());o.returned(p,true);assertTrue(o.complete());}
 @Test void lateOuterCallbackExceptionRevokesSourceAfterInnerNormal(){var p=new Object();var o=new NativePlayerBirthDispatchOrder(p);o.returned(p,false);assertFalse(o.complete());}
 @Test void secondActualPositionDuringPendingAckCannotReplaceFirst(){var p=new Object();var o=new NativePlayerBirthDispatchOrder(p);assertTrue(o.firstPosition(p));assertFalse(o.firstPosition(new Object()));o.returned(p,true);assertFalse(o.complete());}
 @Test void replayedOrForeignOuterReturnCannotMintProof(){var p=new Object();var o=new NativePlayerBirthDispatchOrder(p);o.returned(new Object(),true);assertFalse(o.complete());var r=new NativePlayerBirthDispatchOrder(p);r.returned(p,true);r.returned(p,true);assertFalse(r.complete());}
}
