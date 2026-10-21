package dev.veltraph.client.movement.device;
/** Cleanup identity only. Revoked values may retire this journey; replacement stores/journeys may not. */
public final class DeviceCleanupScope {
 private DeviceCleanupScope(){}
 public record Scope(Object parent,Object player,Object world,Object connection,Object policy,long journey){}
 public static boolean same(Scope a,Scope b){return a!=null&&b!=null&&a.parent()!=null&&a.parent()==b.parent()
  &&a.player()!=null&&a.player()==b.player()&&a.world()!=null&&a.world()==b.world()&&a.connection()!=null&&a.connection()==b.connection()
  &&a.policy()!=null&&a.policy()==b.policy()&&a.journey()>0&&a.journey()==b.journey();}
}
