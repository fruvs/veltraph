package dev.veltraph.client.rewarp.mc;

import dev.veltraph.client.movement.device.DeviceUseReceipt;
import dev.veltraph.client.movement.device.NativeDeviceTeleportReceipt;
import java.lang.reflect.Modifier;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Ownership and actual shared ACK protocol controls; native handler/transport acceptance is separate. */
class IssuedFarmDeviceAckProducerOwnershipTest {
 @Test void nativeReceiptAndReceiveHaveNoCallableConstructorsOrRecordFactories(){
  for(var type:new Class<?>[]{IssuedFarmAppliedLanding.DeviceAppliedAck.class,NativeDeviceTeleportReceipt.DeviceReceive.class}){
   assertFalse(type.isRecord());assertEquals(0,type.getConstructors().length);
   for(var ctor:type.getDeclaredConstructors())assertTrue(Modifier.isPrivate(ctor.getModifiers()));
  }
 }
 @Test void missingRealSessionAndMissingNativeCounterpartCannotManufactureAuthority(){
  assertFalse(NativeDeviceTeleportReceipt.bindIssuedReturnTransit(null,null));
  assertNull(NativeDeviceTeleportReceipt.captureIssuedReturnReceive(null,null,null));
  assertFalse(IssuedFarmAppliedLanding.deviceHandlerReturned(null));
  assertNull(IssuedFarmAppliedLanding.claimDeviceAck(null));
  assertDoesNotThrow(()->NativeDeviceTeleportReceipt.completeIssuedReturnReceive(null));
 }
 @Test void proofStoresFinalActualAppliedIdentityInsteadOfReconstructedValueInputs()throws Exception{
  var applied=IssuedFarmAppliedLanding.DeviceAppliedAck.class.getDeclaredField("applied");
  assertEquals(DeviceUseReceipt.Applied.class,applied.getType());assertTrue(Modifier.isPrivate(applied.getModifiers()));
  assertTrue(Modifier.isFinal(applied.getModifiers()));
  var original=IssuedFarmAppliedLanding.DeviceAppliedAck.class.getDeclaredField("original");
  assertTrue(Modifier.isPrivate(original.getModifiers()));assertTrue(Modifier.isFinal(original.getModifiers()));
 }
 @Test void scheduledAckCannotAlsoClaimDirectWrite(){
  var packet=new Object();var order=new IssuedFarmAckBodyOrder(packet);
  assertTrue(order.enter(packet,true));assertFalse(order.written(packet,true));
  assertFalse(order.returned(packet,true));assertFalse(order.outer(packet,true));assertFalse(order.complete());
 }
 @Test void directAckWrongWriteIdentityPermanentlyRefusesEvenAfterMatchingWrite(){
  var packet=new Object();var order=new IssuedFarmAckBodyOrder(packet);
  assertTrue(order.enter(packet,false));assertFalse(order.written(new Object(),true));
  assertFalse(order.written(packet,true));assertFalse(order.returned(packet,true));assertFalse(order.complete());
 }
 @Test void normalBodyCannotAuthorizeExceptionalOuterNativeSend(){
  var packet=new Object();var order=new IssuedFarmAckBodyOrder(packet);
  assertTrue(order.enter(packet,false));assertTrue(order.written(packet,true));assertTrue(order.returned(packet,true));
  assertFalse(order.outer(packet,false));assertFalse(order.complete());
 }
 @Test void replayedOuterReturnRevokesCompletedSamePacketWitness(){
  var packet=new Object();var order=new IssuedFarmAckBodyOrder(packet);
  assertTrue(order.enter(packet,false));assertTrue(order.written(packet,true));assertTrue(order.returned(packet,true));
  assertTrue(order.outer(packet,true));assertTrue(order.complete());
  assertFalse(order.outer(packet,true));assertFalse(order.complete());
 }
 @Test void actualDirectAndScheduledAcknowledgementsRequireTheirWholeOwnSequence(){
  var directPacket=new Object();var direct=new IssuedFarmAckBodyOrder(directPacket);
  assertTrue(direct.enter(directPacket,false));assertTrue(direct.written(directPacket,true));
  assertTrue(direct.returned(directPacket,true));assertFalse(direct.complete());assertTrue(direct.outer(directPacket,true));assertTrue(direct.complete());
  var scheduledPacket=new Object();var scheduled=new IssuedFarmAckBodyOrder(scheduledPacket);
  assertTrue(scheduled.enter(scheduledPacket,true));assertTrue(scheduled.returned(scheduledPacket,true));
  assertFalse(scheduled.complete());assertTrue(scheduled.outer(scheduledPacket,true));assertTrue(scheduled.complete());
 }
}
