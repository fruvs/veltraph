package dev.veltraph.client.observation.nativeinput;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.veltraph.client.mixin.NativePlayerBirthLocalWireMixin;
import io.netty.channel.local.LocalChannel;
import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
/** Actual opcode wrapper with null receiver only. No healthy native scope/phase/channel/bytes/origin fabricated. */
final class NativeLocalReadOperationTest {
 private static void run(Operation<Void> original)throws Throwable{
  var method=NativePlayerBirthLocalWireMixin.class.getDeclaredMethod("veltraph$birthWireNestedRead",LocalChannel.class,Operation.class);method.setAccessible(true);
  try{method.invoke(new NativePlayerBirthLocalWireMixin(){},null,original);}catch(InvocationTargetException wrapper){throw wrapper.getCause();}
 }
 @Test void actualOriginalOperationStillRunsOnceWithoutAdmission()throws Throwable{var calls=new AtomicInteger();run(args->{calls.incrementAndGet();assertNull(args[0]);return null;});assertEquals(1,calls.get());}
 @Test void actualOriginalErrorEscapesByIdentityAndIsNotRetried(){var calls=new AtomicInteger();var error=new AssertionError("original readInbound error");assertSame(error,assertThrows(AssertionError.class,()->run(args->{calls.incrementAndGet();throw error;})));assertEquals(1,calls.get());}
 @Test void barePublicLifetimeAndEdgeCallsCannotMintAdmission(){var owner=new Object();assertNull(NativePlayerBirthWireSource.enterLocalReadBody(owner));assertNull(NativePlayerBirthWireSource.enterLocalReadEdge(owner,owner));NativePlayerBirthWireSource.exitLocalReadBody(null);NativePlayerBirthWireSource.exitLocalReadEdge(null,true);}
 @Test void recursiveNullSeamStillInvokesEachOriginalOperationOnce()throws Throwable{
  var calls=new AtomicInteger();run(args->{calls.incrementAndGet();try{run(nested->{calls.incrementAndGet();return null;});}catch(Throwable error){throw new AssertionError(error);}return null;});assertEquals(2,calls.get());
 }
 @Test void deferredStyleBareCallbackCannotCreatePc51Admission(){
  var owner=new Object();Runnable callback=()->assertNull(NativePlayerBirthWireSource.enterLocalReadEdge(owner,owner));callback.run();
 }
}
