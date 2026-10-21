package dev.veltraph.client.rewarp.mc;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class IssuedFarmAckBodyOrderTest {
 @Test void directAckNeedsRealWriteAndBodyOuterNormalReturns(){var packet=new Object();var o=new IssuedFarmAckBodyOrder(packet);assertTrue(o.enter(packet,false));assertTrue(o.written(packet,true));assertTrue(o.returned(packet,true));assertFalse(o.complete());assertTrue(o.outer(packet,true));assertTrue(o.complete());}
 @Test void innerCancellationAndMissingWriteCannotCreditAck(){var p=new Object();var o=new IssuedFarmAckBodyOrder(p);o.enter(p,false);assertFalse(o.returned(p,true));assertFalse(o.outer(p,true));assertFalse(o.complete());var n=new IssuedFarmAckBodyOrder(p);assertFalse(n.outer(p,true));}
 @Test void scheduledSubmissionDistinctFromWireDelivery(){var p=new Object();var o=new IssuedFarmAckBodyOrder(p);o.enter(p,true);assertTrue(o.returned(p,true));assertTrue(o.outer(p,true));assertTrue(o.complete());}
 @Test void wrongPacketReplayOrExceptionCannotComplete(){var p=new Object();var o=new IssuedFarmAckBodyOrder(p);assertFalse(o.enter(new Object(),true));var a=new IssuedFarmAckBodyOrder(p);a.enter(p,true);assertFalse(a.returned(p,false));var r=new IssuedFarmAckBodyOrder(p);r.enter(p,true);r.returned(p,true);assertFalse(r.returned(p,true));}
}
