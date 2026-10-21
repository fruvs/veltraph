package dev.veltraph.client.rewarp.mc;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class IssuedFarmTransportOrderTest {
 final Object token=new Object(),backend=new Object(),rest=new Object();
 IssuedFarmTransportOrder fresh(){return new IssuedFarmTransportOrder(token,backend,rest);}
 @Test void authenticDisconnectConnectIngressProcessorAndNativeFieldsRequired(){var o=fresh();assertTrue(o.disconnect(token,backend,rest,true,true));assertTrue(o.connect(backend,rest,true,true));assertTrue(o.incoming(true,true,true));assertTrue(o.playerWritten());assertTrue(o.entityAdded());assertTrue(o.finish(true,true));assertTrue(o.complete());}
 @Test void missingActualDisconnectNormalBodyAndForeignBackendRefuse(){var o=fresh();assertFalse(o.disconnect(token,backend,rest,false,true));var f=fresh();assertFalse(f.disconnect(token,new Object(),rest,true,true));}
 @Test void manualHandleOrUnissuedNativeConnectionCannotMintContext(){var o=fresh();o.disconnect(token,backend,rest,true,true);o.connect(backend,rest,true,true);assertFalse(o.incoming(true,false,true));var p=fresh();p.disconnect(token,backend,rest,true,true);p.connect(backend,rest,true,true);assertFalse(p.incoming(true,true,false));}
 @Test void cancelledNativeLoginAndMissingBodyFieldsCannotComplete(){var o=fresh();o.disconnect(token,backend,rest,true,true);o.connect(backend,rest,true,true);o.incoming(true,true,true);assertFalse(o.finish(true,true));}
 @Test void wrongLoadingTrackerAndRecursiveFieldMutationRefuse(){var o=fresh();o.disconnect(token,backend,rest,true,true);o.connect(backend,rest,true,true);o.incoming(true,true,true);o.playerWritten();o.entityAdded();assertFalse(o.finish(true,false));var r=fresh();r.disconnect(token,backend,rest,true,true);r.connect(backend,rest,true,true);r.incoming(true,true,true);r.playerWritten();assertFalse(r.playerWritten());}
 @Test void nativePlayerAndEntityOrderingDoesNotInventOldScene(){var o=fresh();o.disconnect(token,backend,rest,true,true);o.connect(backend,rest,true,true);o.incoming(true,true,true);o.entityAdded();o.playerWritten();assertTrue(o.finish(true,true));assertTrue(o.complete());}
}
