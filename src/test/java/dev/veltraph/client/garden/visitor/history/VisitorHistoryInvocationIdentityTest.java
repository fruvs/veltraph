package dev.veltraph.client.garden.visitor.history;

import dev.veltraph.client.movement.device.DeviceInvocationIdentity;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Identity kernel used by the actual native capsules; these pure tests do not mint native receipts. */
class VisitorHistoryInvocationIdentityTest {
    @Test void replacementObserverCannotReceiveOldNormalCallback(){var old=new Object();var packet=new Object();var wire=new Object();var call=DeviceInvocationIdentity.head(old,packet,wire,true);assertEquals(DeviceInvocationIdentity.Result.INERT,DeviceInvocationIdentity.finish(call,new Object(),packet,wire,true));}
    @Test void throwingNativeHandlerCannotMintNormalEvidence(){var owner=new Object();var packet=new Object();var wire=new Object();var call=DeviceInvocationIdentity.head(owner,packet,wire,true);assertEquals(DeviceInvocationIdentity.Result.THREW,DeviceInvocationIdentity.finish(call,owner,packet,wire,false));}
    @Test void refusedOffThreadEntryCannotBecomeNormalEvidence(){var owner=new Object();var packet=new Object();var wire=new Object();var call=DeviceInvocationIdentity.head(owner,packet,wire,false);assertEquals(DeviceInvocationIdentity.Result.REFUSED,DeviceInvocationIdentity.finish(call,owner,packet,wire,true));}
    @Test void nativeCallbackIsOneShot(){var owner=new Object();var packet=new Object();var wire=new Object();var call=DeviceInvocationIdentity.head(owner,packet,wire,true);assertEquals(DeviceInvocationIdentity.Result.RETURNED,DeviceInvocationIdentity.finish(call,owner,packet,wire,true));assertEquals(DeviceInvocationIdentity.Result.INERT,DeviceInvocationIdentity.finish(call,owner,packet,wire,true));}
}
