package dev.veltraph.client.observation.nativeinput;
import dev.veltraph.client.service.mc.RootNativeSdlBoundaryPhase;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.world.entity.Entity;
/** One genuine original accumulation/turn/entity invocation chain; no public method ancestor supplies it. */
public final class NativeMouseInvocationPhase {
 private NativeMouseInvocationPhase(){}
 private static final ThreadLocal<Accumulation> ACCUMULATION=new ThreadLocal<>();
 private static final ThreadLocal<TurnPlayer> TURN=new ThreadLocal<>();
 private static final ThreadLocal<EntityTurn> ENTITY=new ThreadLocal<>();
 public static final class Accumulation {
  private final Minecraft client;private final MouseHandler handler;private final Thread thread=Thread.currentThread();
  private final RootNativeSdlBoundaryPhase.MouseFrame root;private final Accumulation prior;
  private boolean turnEntered,closed,revoked;
  private Accumulation(Minecraft client,MouseHandler handler,RootNativeSdlBoundaryPhase.MouseFrame root,Accumulation prior){this.client=client;this.handler=handler;this.root=root;this.prior=prior;}
  private boolean current(){return !closed&&!revoked&&Thread.currentThread()==thread&&ACCUMULATION.get()==this&&client.mouseHandler==handler&&root.originalInvocationCurrent(client,handler);}
 }
 public static Accumulation beginAccumulation(MouseHandler handler){
  var prior=ACCUMULATION.get();if(prior!=null)prior.revoked=true;
  if(!NativeMouseInvocationBindings.actual("ACCUMULATION")){RootNativeSdlBoundaryPhase.refuseOriginalMouseInvocation();return null;}
  var client=Minecraft.getInstance();var root=RootNativeSdlBoundaryPhase.captureOriginalMouseInvocationWindow(client,handler);
  if(root==null||prior!=null)return null;var next=new Accumulation(client,handler,root,prior);ACCUMULATION.set(next);return next;
 }
 public static void closeAccumulation(Accumulation actual,boolean normal){if(actual==null)return;
  if(!normal||!actual.current()){actual.revoked=true;RootNativeSdlBoundaryPhase.refuseOriginalMouseInvocation();}
  actual.closed=true;if(ACCUMULATION.get()==actual){if(actual.prior==null)ACCUMULATION.remove();else ACCUMULATION.set(actual.prior);}}
 public static final class TurnPlayer {
  private final Accumulation accumulation;private final TurnPlayer prior;private boolean entityEntered,closed,revoked;
  private TurnPlayer(Accumulation accumulation,TurnPlayer prior){this.accumulation=accumulation;this.prior=prior;}
  private boolean current(){return !closed&&!revoked&&TURN.get()==this&&accumulation.current();}
 }
 public static TurnPlayer beginTurnPlayer(MouseHandler handler){var a=ACCUMULATION.get();var prior=TURN.get();
  if(prior!=null)prior.revoked=true;
  if(!NativeMouseInvocationBindings.actual("TURN_PLAYER")||a==null||a.handler!=handler||a.turnEntered||prior!=null){if(a!=null)a.revoked=true;RootNativeSdlBoundaryPhase.refuseOriginalMouseInvocation();return null;}
  a.turnEntered=true; // Claim before provider/getter calls.
  if(!a.current())return null;var next=new TurnPlayer(a,prior);TURN.set(next);return next;
 }
 public static void closeTurnPlayer(TurnPlayer actual,boolean normal){if(actual==null)return;
  if(!normal||!actual.current()){actual.revoked=true;actual.accumulation.revoked=true;RootNativeSdlBoundaryPhase.refuseOriginalMouseInvocation();}
  actual.closed=true;if(TURN.get()==actual){if(actual.prior==null)TURN.remove();else TURN.set(actual.prior);}}
 public static final class EntityTurn {
  private final TurnPlayer turn;private final Entity entity;private final double dx,dy;private final EntityTurn prior;
  private boolean claimed,closed,revoked;
  private EntityTurn(TurnPlayer turn,Entity entity,double dx,double dy,EntityTurn prior){this.turn=turn;this.entity=entity;this.dx=dx;this.dy=dy;this.prior=prior;}
  public boolean current(Minecraft client,MouseHandler handler){return !closed&&!revoked&&ENTITY.get()==this&&turn.current()
   &&turn.accumulation.client==client&&turn.accumulation.handler==handler&&client.player==entity;}
 }
 public static EntityTurn beginEntityTurn(Entity entity,double dx,double dy){var t=TURN.get();var prior=ENTITY.get();if(prior!=null)prior.revoked=true;
  if(!NativeMouseInvocationBindings.actual("ENTITY_TURN")||t==null||t.entityEntered||prior!=null){if(t!=null){t.revoked=true;t.accumulation.revoked=true;}RootNativeSdlBoundaryPhase.refuseOriginalMouseInvocation();return null;}
  t.entityEntered=true;
  if(!t.current()||t.accumulation.client.player!=entity||!Double.isFinite(dx)||!Double.isFinite(dy))return null;
  var next=new EntityTurn(t,entity,dx,dy,prior);ENTITY.set(next);return next;
 }
 public static EntityTurn claimActualEntityTurn(Entity entity,double dx,double dy){var actual=ENTITY.get();
  if(actual==null||actual.claimed||actual.entity!=entity||Double.doubleToRawLongBits(actual.dx)!=Double.doubleToRawLongBits(dx)
   ||Double.doubleToRawLongBits(actual.dy)!=Double.doubleToRawLongBits(dy))return null;
  actual.claimed=true;return actual.current(actual.turn.accumulation.client,actual.turn.accumulation.handler)?actual:null;
 }
 /** Exact already-claimed original mouse chain affiliation for the private pending-birth predicate only. */
 static EntityTurn claimedPendingBirthTurn(Minecraft client,Entity player){
  boolean caller=StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).walk(s->s.filter(f->f.getDeclaringClass()!=NativeMouseInvocationPhase.class).findFirst().map(f->f.getDeclaringClass()==NativePlayerBirthProof.class&&f.getMethodName().equals("nativeMouseTurnCaller")&&f.getDescriptor().equals("(Lnet/minecraft/client/Minecraft;Lnet/minecraft/world/entity/Entity;)Z")).orElse(false));
  if(!caller||client==null||player==null)return null;
  var actual=claimedOriginalMouseInvocation(client,client.mouseHandler);
  return actual!=null&&actual.entity==player&&client.player==player?actual:null;
 }
 /** Read-only exact current inner cookie, consumed already by the real Entity.turn observer. */
 public static EntityTurn claimedOriginalMouseInvocation(Minecraft client,MouseHandler handler){var actual=ENTITY.get();
  return actual!=null&&actual.claimed&&actual.current(client,handler)?actual:null;}
 public static void closeEntityTurn(EntityTurn actual,boolean normal){if(actual==null)return;
  if(!normal||!actual.current(actual.turn.accumulation.client,actual.turn.accumulation.handler)){actual.revoked=true;actual.turn.revoked=true;actual.turn.accumulation.revoked=true;RootNativeSdlBoundaryPhase.refuseOriginalMouseInvocation();}
  actual.closed=true;if(ENTITY.get()==actual){if(actual.prior==null)ENTITY.remove();else ENTITY.set(actual.prior);}}
}
