package dev.veltraph.client.movement.device;
/** Pure time refusal only; no source, native invocation or deadline can be minted here. */
final class OwnedUseTimeWindow {
 private OwnedUseTimeWindow(){}
 static boolean permits(long now,long issuedAt,long deviceDeadline,long journeyDeadline){
  return issuedAt>=0&&deviceDeadline>issuedAt&&journeyDeadline>issuedAt&&now>=issuedAt&&now<deviceDeadline&&now<journeyDeadline;
 }
}
