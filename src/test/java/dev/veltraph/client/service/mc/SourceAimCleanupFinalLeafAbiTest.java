package dev.veltraph.client.service.mc;
import java.lang.reflect.Modifier;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
/** Authored and unrun; source-level consumer regression must additionally exercise genuine native handles. */
class SourceAimCleanupFinalLeafAbiTest {
 @Test void actualOutcomeFinalLeafIsPublicBooleanAndHasNoCustodyConstructor()throws Exception{
  var method=McRotationService.SourceAimOutcome.class.getDeclaredMethod("cleanupNativeCurrent",boolean.class);
  assertEquals(boolean.class,method.getReturnType());assertTrue(Modifier.isPublic(method.getModifiers()));
  for(var constructor:McRotationService.SourceAimOutcome.class.getDeclaredConstructors())assertTrue(Modifier.isPrivate(constructor.getModifiers()));
 }
 @Test void actualCancellationFinalLeafIsPublicBooleanAndHasNoCustodyConstructor()throws Exception{
  var method=McRotationService.SourceAimCancellation.class.getDeclaredMethod("cleanupNativeCurrent",boolean.class);
  assertEquals(boolean.class,method.getReturnType());assertTrue(Modifier.isPublic(method.getModifiers()));
  for(var constructor:McRotationService.SourceAimCancellation.class.getDeclaredConstructors())assertTrue(Modifier.isPrivate(constructor.getModifiers()));
 }
}
