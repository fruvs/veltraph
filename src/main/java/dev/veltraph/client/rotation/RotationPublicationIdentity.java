package dev.veltraph.client.rotation;
/** Identity of a service's own native endpoint publication, never authority for a foreign request. */
public final class RotationPublicationIdentity {
 private RotationPublicationIdentity() {}
 public record Transition(RotationReceiptIdentity.Stamp before,RotationReceiptIdentity.Stamp after,long ticket) {}
 public static boolean valid(Transition t){
  if(t==null||t.ticket()<=0||t.before()==null||t.after()==null)return false;
  var a=t.before();var b=t.after();
  return a.service()!=null&&a.service()==b.service()&&a.generation()==b.generation()&&a.authority()!=null&&a.authority()==b.authority()
   &&a.player()!=null&&a.player()==b.player()&&a.world()!=null&&a.world()==b.world()&&a.connection()!=null&&a.connection()==b.connection()
   &&a.epoch()>=0&&b.epoch()>=a.epoch()&&b.epoch()-a.epoch()<=1;
 }
 public static boolean refresh(RotationReceiptIdentity.Stamp expected,RotationReceiptIdentity.Stamp current,Transition t,
    Object nativePlayer,Object nativeWorld,Object nativeConnection,Object nativeAuthority){
  return valid(t)&&same(expected,t.before())&&same(current,t.after())
   &&RotationReceiptIdentity.current(t.after(),current,nativePlayer,nativeWorld,nativeConnection,nativeAuthority,false);
 }
 /** Actual own ticket, requiring the last own transition and the native publication serial simultaneously. */
 public static long ownedTicket(RotationReceiptIdentity.Stamp expected,RotationReceiptIdentity.Stamp current,Transition t,
    Object nativePlayer,Object nativeWorld,Object nativeConnection,Object nativeAuthority,long currentPublication){
  if(!valid(t)||t.ticket()!=currentPublication||currentPublication<=0
     ||!(same(expected,t.before())||same(expected,t.after()))||!same(current,t.after())
     ||!RotationReceiptIdentity.current(t.after(),current,nativePlayer,nativeWorld,nativeConnection,nativeAuthority,false))return -1;
  return t.ticket();
 }
 private static boolean same(RotationReceiptIdentity.Stamp a,RotationReceiptIdentity.Stamp b){
  return a!=null&&b!=null&&a.service()==b.service()&&a.generation()==b.generation()&&a.authority()==b.authority()
   &&a.player()==b.player()&&a.world()==b.world()&&a.connection()==b.connection()&&a.epoch()==b.epoch();
 }
}
