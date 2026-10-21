package dev.veltraph.client.observation.nativeinput;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class NativePlayerBirthOrderTest {
 final Object packet=new Object(),listener=new Object(),connection=new Object(),player=new Object();
 NativePlayerBirthOrder fresh(){return new NativePlayerBirthOrder(packet,listener,connection);}
 @Test void actualBirthAndServerInitializationConsumedOnce(){var o=fresh();assertTrue(o.dispatch(packet,listener,connection,true));assertTrue(o.created(player,true));assertTrue(o.installed(player,true));assertTrue(o.added(player,true));assertTrue(o.returned(true));assertTrue(o.positionInitialized(player,true,true));assertTrue(o.finish(true));assertTrue(o.complete());assertTrue(o.consume());assertFalse(o.consume());}
 @Test void directHandlerOrForeignIngressCannotCreateBirth(){var o=fresh();assertFalse(o.dispatch(packet,listener,connection,false));assertFalse(o.created(player,true));var f=fresh();assertFalse(f.dispatch(new Object(),listener,connection,true));}
 @Test void cancelledConstructorOrMissingActualInstallCannotReturn(){var o=fresh();o.dispatch(packet,listener,connection,true);assertFalse(o.created(player,false));var f=fresh();f.dispatch(packet,listener,connection,true);f.created(player,true);assertFalse(f.added(player,true));}
 @Test void rawSameValueOrRevertedHistoryCannotFinish(){var o=fresh();o.dispatch(packet,listener,connection,true);o.created(player,true);o.installed(player,true);o.added(player,true);o.returned(true);o.positionInitialized(player,true,true);assertFalse(o.finish(false));assertFalse(o.complete());}
 @Test void unrelatedPositionAndRecursiveBirthRemainDenied(){var o=fresh();o.dispatch(packet,listener,connection,true);o.created(player,true);assertFalse(o.created(player,true));var p=fresh();p.dispatch(packet,listener,connection,true);p.created(player,true);p.installed(player,true);p.added(player,true);p.returned(true);assertFalse(p.positionInitialized(new Object(),true,true));}
 @Test void oldNormalReturnCannotRenewAfterRefusal(){var o=fresh();o.dispatch(packet,listener,connection,true);o.created(player,true);o.installed(player,true);o.added(player,true);o.returned(true);o.refuse();assertFalse(o.positionInitialized(player,true,true));assertFalse(o.finish(true));}
}
