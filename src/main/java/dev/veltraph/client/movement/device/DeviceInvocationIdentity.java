package dev.veltraph.client.movement.device;
/** Per-invocation identity evidence only. The native bridge privately captures the actual session. */
public final class DeviceInvocationIdentity {
 public enum Result { INERT, RETURNED, THREW, REFUSED }
 public static final class Invocation {
  private final Object session,packet,transport;private final boolean eligible;private boolean consumed;
  private Invocation(Object session,Object packet,Object transport,boolean eligible){this.session=session;this.packet=packet;this.transport=transport;this.eligible=eligible;}
 }
 private DeviceInvocationIdentity(){}
 public static Invocation head(Object session,Object packet,Object transport,boolean eligible){return session==null||packet==null||transport==null?null:new Invocation(session,packet,transport,eligible);}
 public static Result finish(Invocation call,Object activeSession,Object packet,Object transport,boolean returned){
  if(call==null||call.consumed)return Result.INERT;call.consumed=true;
  if(call.session!=activeSession||call.packet!=packet||call.transport!=transport)return Result.INERT;
  if(!returned)return Result.THREW;
  return call.eligible?Result.RETURNED:Result.REFUSED;
 }
 public static boolean capturedCurrent(Invocation call,Object activeSession){return call!=null&&call.session==activeSession;}
}
