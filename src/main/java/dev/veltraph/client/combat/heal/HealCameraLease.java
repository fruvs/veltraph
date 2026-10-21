package dev.veltraph.client.combat.heal;
/** Publisher identity for revoking a camera request; a replacement epoch is independent custody. */
public record HealCameraLease(Object player,Object world,Object connection,Object authority,long epoch) {
 public boolean owns(HealCameraLease now){return now!=null && player==now.player && world==now.world && connection==now.connection && authority==now.authority && epoch==now.epoch;}
}
