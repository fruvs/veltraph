package dev.veltraph.client.observation.nativeinput;
import org.junit.jupiter.api.Test;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import static org.junit.jupiter.api.Assertions.*;
/** Actual bare/null/missing-cookie refusals only. No mouse phase/Root frame/Scene/actor or native source is constructed. */
final class PendingBirthMouseBindingSourceTest {
 @Test void bareForeignCallerCannotReadPendingBirthCookie(){assertNull(NativeMouseInvocationPhase.claimedPendingBirthTurn(null,null));assertNull(foreignBridge());}
 private static Object foreignBridge(){return NativeMouseInvocationPhase.claimedPendingBirthTurn(null,null);}
 @Test void privateBirthPredicateCannotInventCookieForNullSource()throws Exception{
  var method=NativePlayerBirthProof.class.getDeclaredMethod("nativeMouseTurnCaller",Minecraft.class,Entity.class);method.setAccessible(true);
  assertEquals(Boolean.FALSE,method.invoke(null,new Object[]{null,null}));
  assertEquals(Boolean.FALSE,method.invoke(null,new Object[]{null,null}));
 }
 @Test void existingClaimedLookupRemainsEmptyAndDoesNotCreatePhase(){
  assertNull(NativeMouseInvocationPhase.claimedOriginalMouseInvocation(null,null));
  assertNull(NativeMouseInvocationPhase.claimedPendingBirthTurn(null,null));
  assertNull(NativeMouseInvocationPhase.claimedOriginalMouseInvocation(null,null));
 }
}
