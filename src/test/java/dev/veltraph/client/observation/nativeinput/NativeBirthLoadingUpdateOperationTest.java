package dev.veltraph.client.observation.nativeinput;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.veltraph.client.mixin.NativePlayerBirthLoadingMixin;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.LevelLoadTracker;
import net.minecraft.client.player.LocalPlayer;
import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
/** Actual wrappers with null native operands and no admission; original once/result/error only, never a healthy retained reset proof. */
final class NativeBirthLoadingUpdateOperationTest {
 private static void operations(boolean fail)throws Throwable{
  var actor=new NativePlayerBirthLoadingMixin(){};var type=NativePlayerBirthLoadingMixin.class;
  String[] names={"veltraph$birthLoadingTrackerStart","veltraph$birthLoadingOriginalScreen","veltraph$birthLoadingOriginalUpdate"};
  Class<?>[][] sig={{LevelLoadTracker.class,LocalPlayer.class,ClientLevel.class,Operation.class},{Gui.class,Operation.class},{LevelLoadingScreen.class,LevelLoadTracker.class,LevelLoadingScreen.Reason.class,Operation.class}};
  for(int i=0;i<names.length;i++){var method=type.getDeclaredMethod(names[i],sig[i]);method.setAccessible(true);var count=new AtomicInteger();var error=new AssertionError("original native operation");Object[] args=new Object[sig[i].length];
   args[args.length-1]=(Operation<Object>)(operands->{count.incrementAndGet();for(Object operand:operands)assertNull(operand);if(fail)throw error;return null;});
   if(fail)assertSame(error,assertThrows(AssertionError.class,()->invoke(method,actor,args)));else assertNull(invoke(method,actor,args));assertEquals(1,count.get());
  }
 }
 private static Object invoke(java.lang.reflect.Method method,Object actor,Object[] args)throws Throwable{try{return method.invoke(actor,args);}catch(InvocationTargetException wrapped){throw wrapped.getCause();}}
 @Test void originalThreeOperationsRunOnceWithoutNativeAdmission()throws Throwable{operations(false);}
 @Test void originalErrorIdentityEscapesWithoutRetry()throws Throwable{operations(true);}
 @Test void bareSourceCallbacksCannotIssueRetainedOpening(){assertNull(NativePlayerBirthProof.loadingTrackerEntered(null,null,null));assertNull(NativePlayerBirthProof.loadingUpdateEntered(null,null,null));NativePlayerBirthProof.loadingOriginalScreenReturned(null);NativePlayerBirthProof.loadingTrackerExited(null,true);NativePlayerBirthProof.loadingUpdateExited(null,true);}
}
