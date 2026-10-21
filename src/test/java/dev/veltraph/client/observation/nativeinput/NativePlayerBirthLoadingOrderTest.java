package dev.veltraph.client.observation.nativeinput;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class NativePlayerBirthLoadingOrderTest {
 @Test void normalPacketAndAckCannotSubstituteForTypedLoadingClose(){
  Object opening=new Object(),screen=new Object();var order=new NativePlayerBirthLoadingOrder(opening);
  assertFalse(order.readyToFinalize(false,null));assertTrue(order.opened(opening,screen,true,true));
  assertFalse(order.readyToFinalize(false,screen));assertFalse(order.readyToFinalize(false,null));
  assertTrue(order.close(screen,null,true,true,true));assertFalse(order.readyToFinalize(true,null));
  assertFalse(order.readyToFinalize(false,screen));assertTrue(order.readyToFinalize(false,null));
 }
 @Test void authenticatedOriginalOpenThenTypedCloseCarriesOldNine(){var call=new Object();var screen=new Object();var o=new NativePlayerBirthLoadingOrder(call);assertTrue(o.opened(call,screen,true,true));assertTrue(o.close(screen,null,true,true,true));assertTrue(o.complete());}
 @Test void genericGuiCloseCurrentBitsAndForeignScreenCannotAdvance(){var call=new Object();var screen=new Object();assertFalse(new NativePlayerBirthLoadingOrder(call).close(screen,null,true,true,true));var o=new NativePlayerBirthLoadingOrder(call);o.opened(call,screen,true,true);assertFalse(o.close(new Object(),null,true,true,true));var p=new NativePlayerBirthLoadingOrder(call);p.opened(call,screen,true,true);assertFalse(p.close(screen,null,false,true,true));}
 @Test void bindingOrHeldChangeAndReplayReject(){var call=new Object();var screen=new Object();var o=new NativePlayerBirthLoadingOrder(call);o.opened(call,screen,true,true);assertFalse(o.close(screen,null,true,false,true));var h=new NativePlayerBirthLoadingOrder(call);h.opened(call,screen,true,true);assertFalse(h.close(screen,null,true,true,false));var r=new NativePlayerBirthLoadingOrder(call);r.opened(call,screen,true,true);r.close(screen,null,true,true,true);assertFalse(r.close(screen,null,true,true,true));}
}
