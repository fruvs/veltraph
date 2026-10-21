package dev.veltraph.client.movement.device;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
/** Actuator only for a genuine dispatcher-private attempt plus actual native invocation/constructor facts. */
public final class NativeOwnedDeviceUseBoundary {
 private static final ThreadLocal<McInstantTransmission.OwnedUseAttempt> source=new ThreadLocal<>();
 private NativeOwnedDeviceUseBoundary(){}
 static boolean openSource(McInstantTransmission.OwnedUseAttempt actual){
  if(actual==null||source.get()!=null||!actual.openable())return false;source.set(actual);return true;
 }
 static void closeSource(McInstantTransmission.OwnedUseAttempt exact){if(source.get()==exact)source.remove();if(exact!=null)exact.close();}
 static boolean sourceCurrent(McInstantTransmission.OwnedUseAttempt exact){return exact!=null&&source.get()==exact;}
 public static McInstantTransmission.OwnedUseAttempt claimActualNativeUse(MultiPlayerGameMode mode,Object actual,Player player,InteractionHand hand){
  var attempt=source.get();if(attempt==null||mode==null||mode.getClass()!=MultiPlayerGameMode.class||!(mode instanceof OwnedNativeUseCarrier nativeBody)
    ||!nativeBody.ownedUseInvocationMatches(actual,player,hand))return null;
  // Classification retains a genuine first attempt even if its ordinal is exhausted; permission is checked at both effects.
  return attempt.claim(mode,actual,player,hand)?attempt:null;
 }
 public static boolean beforeLocalUse(MultiPlayerGameMode mode,Object actual,ItemStack stack,Level level,Player player,InteractionHand hand){
  if(!(mode instanceof OwnedNativeUseCarrier nativeBody)||!nativeBody.ownedUseInvocationMatches(actual,player,hand))return true;
  var attempt=nativeBody.ownedUseAttempt(actual);return attempt==null||attempt.beforeLocal(actual,stack,level,player,hand);
 }
 public static final class SendBoundary {
  private final McInstantTransmission.OwnedUseAttempt attempt;private final Object invocation;
  private final ServerboundUseItemPacket packet;private final Connection transport;private boolean evaluated;
  private SendBoundary(McInstantTransmission.OwnedUseAttempt attempt,Object invocation,ServerboundUseItemPacket packet,Connection transport){this.attempt=attempt;this.invocation=invocation;this.packet=packet;this.transport=transport;}
 }
 /** Exact packet was recorded only inside the actual native NEW wrapper; a journal capsule is not an origin. */
 public static SendBoundary captureActualSend(Connection transport,Packet<?> packet){
  if(!(packet instanceof ServerboundUseItemPacket use)||use.hand()!=InteractionHand.MAIN_HAND)return null;
  var sourceAttempt=source.get();var mode=sourceAttempt==null?null:sourceAttempt.originalMode();
  if(mode==null||mode.getClass()!=MultiPlayerGameMode.class||!(mode instanceof OwnedNativeUseCarrier nativeBody))return null;
  Object invocation=sourceAttempt.actualNativeInvocation();var attempt=nativeBody.ownedUseAttempt(invocation);
  if(attempt==null||attempt!=sourceAttempt||nativeBody.ownedUseConstructedPacket(invocation)!=packet)return null;
  return new SendBoundary(attempt,invocation,use,transport);
 }
 /** Null means ordinary/unbound native flow. Passive observer refusal suppresses nothing without this owned origin. */
 public static boolean afterObservation(SendBoundary actual,NativeDeviceTeleportReceipt.SendInvocation observed){
  if(actual==null)return true;if(actual.evaluated)return false;actual.evaluated=true;
  return actual.attempt.beforeSend(actual.invocation,actual.packet,actual.transport,observed);
 }
 /** Read-only fact for native passive counters; this cannot mint an attempt, invocation or packet. */
 public static boolean isActualOwnedPacket(Packet<?> packet){
  var a=source.get();return packet!=null&&a!=null&&a.originalMode() instanceof OwnedNativeUseCarrier nativeBody
    &&nativeBody.ownedUseAttempt(a.actualNativeInvocation())==a&&nativeBody.ownedUseConstructedPacket(a.actualNativeInvocation())==packet;
 }
}
