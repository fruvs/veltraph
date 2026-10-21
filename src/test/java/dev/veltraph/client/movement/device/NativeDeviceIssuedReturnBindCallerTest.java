package dev.veltraph.client.movement.device;
import java.lang.reflect.Modifier;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
/** Negative direct-caller controls. The real source/Session positive requires native integration. */
class NativeDeviceIssuedReturnBindCallerTest {
 private static boolean reflectedCallerProbe()throws Exception{
  var method=NativeDeviceTeleportReceipt.class.getDeclaredMethod("issuedReturnBindCallerCurrent");
  assertTrue(Modifier.isPrivate(method.getModifiers()));method.setAccessible(true);return (boolean)method.invoke(null);
 }
 private static boolean captureOwnedUseAttempt()throws Exception{return reflectedCallerProbe();}
 @Test void arbitraryDirectAndReflectiveCallerCannotAcquireBindPermission()throws Exception{
  assertFalse(reflectedCallerProbe());assertFalse(NativeDeviceTeleportReceipt.bindIssuedReturnTransit(null,null));
 }
 @Test void copiedCanonicalMethodNameOnWrongClassCannotPass()throws Exception{assertFalse(captureOwnedUseAttempt());}
 @Test void callbackInvocationCannotBorrowAnOuterNamedCall()throws Exception{
  java.util.concurrent.Callable<Boolean> callback=NativeDeviceIssuedReturnBindCallerTest::captureOwnedUseAttempt;
  assertFalse(callback.call());
 }
}
