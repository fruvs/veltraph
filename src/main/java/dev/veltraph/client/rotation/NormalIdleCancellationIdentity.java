package dev.veltraph.client.rotation;
/** Evidence model only. Native private constructors and actual Root/cancel witnesses supply permission. */
public final class NormalIdleCancellationIdentity {
 private NormalIdleCancellationIdentity(){}
 public enum Cause { ROOT_FINAL_NO_OWNER,GUI,PAUSE,PLAYER_WORLD_UNAVAILABLE,DISCONNECT_OR_WARP,GENERIC_NULL,FOREIGN_OWNER }
 public enum Site { BEFORE_TICK,BEFORE_FRAME }
 public record Scope(Object installation,Object provider,Object token,Object root,Object parent,Object player,Object world,Object connection,Object store,Object service,Object options,Object hardware,
   long admission,long generation,long epoch,long serial,long positiveOwnedTicket,boolean confirmedNormal,boolean sourceCurrent,boolean nativeReady,boolean noEnabledOwner){}
 public static boolean eligible(Scope s,Cause cause){return s!=null&&cause==Cause.ROOT_FINAL_NO_OWNER&&s.installation()!=null&&s.provider()!=null&&s.token()!=null&&s.root()!=null&&s.parent()!=null
  &&s.player()!=null&&s.world()!=null&&s.connection()!=null&&s.store()!=null&&s.service()!=null&&s.options()!=null&&s.hardware()!=null&&s.admission()>=0&&s.generation()>=0&&s.epoch()>=0&&s.epoch()<Long.MAX_VALUE
  &&s.positiveOwnedTicket()>0&&s.serial()==s.positiveOwnedTicket()&&s.confirmedNormal()&&s.sourceCurrent()&&s.nativeReady()&&s.noEnabledOwner();}
 public static boolean transition(Scope before,Scope after,Cause cause,Site site){return site!=null&&eligible(before,cause)&&after!=null&&after.epoch()==before.epoch()+1
  &&before.installation()==after.installation()&&before.provider()==after.provider()&&before.token()==after.token()&&before.root()==after.root()&&before.parent()==after.parent()
  &&before.player()==after.player()&&before.world()==after.world()&&before.connection()==after.connection()&&before.store()==after.store()&&before.service()==after.service()
  &&before.options()==after.options()&&before.hardware()==after.hardware()&&before.admission()==after.admission()&&before.generation()==after.generation()&&before.serial()==after.serial()
  &&before.positiveOwnedTicket()==after.positiveOwnedTicket()&&after.confirmedNormal()&&after.sourceCurrent()&&after.nativeReady()&&after.noEnabledOwner();}
}
