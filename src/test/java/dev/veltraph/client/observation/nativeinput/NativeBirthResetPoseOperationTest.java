package dev.veltraph.client.observation.nativeinput;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.veltraph.client.mixin.NativePlayerBirthLoginMixin;
import dev.veltraph.client.mixin.NativePlayerBirthResetPoseMixin;
import net.minecraft.client.player.LocalPlayer;
import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
/** Actual operation seams without native admission, null receiver only. No healthy SDK pose scope fabricated. */
final class NativeBirthResetPoseOperationTest {
 private static void outer(Operation<Void> original)throws Throwable{
  var m=NativePlayerBirthLoginMixin.class.getDeclaredMethod("veltraph$birthInitialReset",LocalPlayer.class,Operation.class);m.setAccessible(true);
  try{m.invoke(new NativePlayerBirthLoginMixin(){},null,original);}catch(InvocationTargetException wrapped){throw wrapped.getCause();}
 }
 private static void inner(Operation<Void> original)throws Throwable{
  var m=NativePlayerBirthResetPoseMixin.class.getDeclaredMethod("veltraph$birthResetPitch",LocalPlayer.class,float.class,Operation.class);m.setAccessible(true);
  try{m.invoke(new NativePlayerBirthResetPoseMixin(){},null,0F,original);}catch(InvocationTargetException wrapped){throw wrapped.getCause();}
 }
 @Test void outerOriginalResetCalledOnceWithoutAdmission()throws Throwable{var n=new AtomicInteger();outer(args->{n.incrementAndGet();assertNull(args[0]);return null;});assertEquals(1,n.get());}
 @Test void nestedOriginalPitchCalledOnceWithExactArguments()throws Throwable{var n=new AtomicInteger();inner(args->{n.incrementAndGet();assertNull(args[0]);assertEquals(Float.floatToIntBits(0F),Float.floatToIntBits((Float)args[1]));return null;});assertEquals(1,n.get());}
 @Test void originalErrorsEscapeByIdentityAndNoRetry(){var error=new AssertionError("original reset");var n=new AtomicInteger();assertSame(error,assertThrows(AssertionError.class,()->outer(args->{n.incrementAndGet();throw error;})));assertEquals(1,n.get());var pitchError=new IllegalStateException("original pitch");assertSame(pitchError,assertThrows(IllegalStateException.class,()->inner(args->{n.incrementAndGet();throw pitchError;})));assertEquals(2,n.get());}
 @Test void bareAndOrdinaryCallsCannotCreateBirthResetScope(){assertNull(NativePlayerBirthProof.loginResetEntered(null,null));assertNull(NativePlayerBirthProof.loginResetPitchEntered(null,0F));NativePlayerBirthProof.loginResetPitchExited(null,true);NativePlayerBirthProof.loginResetExited(null,true);}
}
