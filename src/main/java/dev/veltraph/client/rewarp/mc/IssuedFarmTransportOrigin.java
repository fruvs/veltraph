package dev.veltraph.client.rewarp.mc;
import dev.veltraph.client.VeltraphBootstrap;
import dev.veltraph.client.scheduling.mc.NativeRestTransport;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.PacketListener;
import net.minecraft.network.Connection;
import io.netty.channel.ChannelHandlerContext;
import dev.veltraph.client.mixin.IssuedFarmConnectionAccessor;
import java.lang.ref.WeakReference;
import java.util.ArrayDeque;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.*;
import dev.veltraph.client.mixin.ScheduledConnectScreenAccessor;
import dev.veltraph.client.mixin.ScheduledClientLevelLoadAccessor;
import dev.veltraph.client.mixin.ScheduledLevelLoadingScreenAccessor;
/** Typed native transport instruction/return witnesses; neither a source-active window nor a generic expected-context flag. */
public final class IssuedFarmTransportOrigin {
 private static final StackWalker STACK=StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);
 private static IssuedFarmExpectedObservation expected;private static NativeRestTransport.IssuedFarmSnapshot postSnapshot;
 public static final class DisconnectInvocation {final McSuspendedFarmReturnCoordinator.DepartureCall call;final NativeRestTransport backend;final Screen rest;final Object player,world,listener;boolean returned,levelCleared,playerCleared;private DisconnectInvocation(McSuspendedFarmReturnCoordinator.DepartureCall call,NativeRestTransport b,Screen rest,Minecraft c){this.call=call;backend=b;this.rest=rest;player=c.player;world=c.level;listener=c.getConnection();}}
 public static final class ConnectInvocation {final McSuspendedFarmReturnCoordinator.RejoinCall call;final NativeRestTransport backend;final Screen rest;Screen connector;boolean returned;private ConnectInvocation(McSuspendedFarmReturnCoordinator.RejoinCall call,NativeRestTransport b,Screen rest){this.call=call;backend=b;this.rest=rest;}}
 private static IssuedFarmAppliedLanding.Receipt expectedPositionReceipt;private static final ThreadLocal<DisconnectInvocation> DISCONNECT=new ThreadLocal<>();private static ConnectInvocation issuedConnect;private static DisconnectInvocation authenticatedDeparture;
 private static boolean nativeCaller(){return STACK.walk(f->f.filter(x->x.getDeclaringClass()!=IssuedFarmTransportOrigin.class).findFirst().map(x->x.getDeclaringClass()==NativeRestTransport.class).orElse(false));}
 public static DisconnectInvocation disconnectEntered(NativeRestTransport backend,Minecraft c,Screen rest,boolean transferring){
  var core=VeltraphBootstrap.suspendedFarmReturnCoordinator();var call=core==null?null:core.currentDepartureCall();var snapshot=backend.issuedFarmSnapshot();
  if(!nativeCaller()||transferring||call==null||call.client()!=c||call.transport()!=backend||snapshot.client()!=c||snapshot.restScreen()!=rest||snapshot.originalConnection()!=c.getConnection().getConnection()||!snapshot.current()||!call.currentFinalNative())return null;
  var entered=new DisconnectInvocation(call,backend,rest,c);DISCONNECT.set(entered);return entered;
 }
 public static void disconnectFieldWritten(Minecraft c,Object value,boolean player){var i=DISCONNECT.get();boolean caller=STACK.walk(f->f.filter(x->x.getDeclaringClass()!=IssuedFarmTransportOrigin.class).findFirst().map(x->x.getDeclaringClass()==Minecraft.class).orElse(false));if(i==null||!caller||c!=i.call.client()||value!=null)return;if(player){if(c.player==null)i.playerCleared=true;}else if(c.level==null)i.levelCleared=true;}
 public static void disconnectReturned(DisconnectInvocation i,Minecraft c,Screen rest,boolean normal){
  if(i==null)return;try{var snapshot=i.backend.issuedFarmSnapshot();
  if(!nativeCaller()||DISCONNECT.get()!=i||!i.levelCleared||!i.playerCleared||i.returned||!normal||c!=i.call.client()||rest!=i.rest||c.player!=null||c.level!=null||c.getConnection()!=null||c.gui.screen()!=i.rest||snapshot.restScreen()!=i.rest||snapshot.originalConnection()==null||!snapshot.current()||!i.call.sourceNativeAffiliationCurrent())return;
  DISCONNECT.remove();i.returned=true;authenticatedDeparture=i;postSnapshot=snapshot;expected=IssuedFarmExpectedObservation.departure(i.call,i.player,i.world,i.listener,snapshot,VeltraphBootstrap.suspendedFarmReturnCoordinator());
 }finally{if(DISCONNECT.get()==i)DISCONNECT.remove();}
 }
 public static ConnectInvocation connectEntered(NativeRestTransport backend,Minecraft c,Screen parent,Object server){
  var core=VeltraphBootstrap.suspendedFarmReturnCoordinator();var call=core==null?null:core.currentRejoinCall();var snapshot=backend.issuedFarmSnapshot();
  if(!nativeCaller()||authenticatedDeparture==null||!authenticatedDeparture.returned||authenticatedDeparture.backend!=backend||authenticatedDeparture.rest!=parent||call==null||call.client()!=c||call.transport()!=backend||c.player!=null||c.level!=null||c.getConnection()!=null||c.gui.screen()!=parent||snapshot.restScreen()!=parent||snapshot.target()!=server||!snapshot.current()||!call.currentFinalNative())return null;
  return new ConnectInvocation(call,backend,parent);
 }
 public static void connectReturned(ConnectInvocation i,Minecraft c,boolean normal){if(i==null)return;var snapshot=i.backend.issuedFarmSnapshot();
  if(!nativeCaller()||i.returned||!normal||c!=i.call.client()||snapshot.restScreen()!=i.rest||!(c.gui.screen() instanceof net.minecraft.client.gui.screens.ConnectScreen)||!snapshot.current()||!i.call.sourceNativeAffiliationCurrent())return;i.returned=true;i.connector=c.gui.screen();issuedConnect=i;postSnapshot=snapshot;expected=IssuedFarmExpectedObservation.joined(i.call,null,null,null,null,null,null,snapshot,VeltraphBootstrap.suspendedFarmReturnCoordinator());
 }
 private record Ingress(WeakReference<Object> packet,WeakReference<Object> listener,WeakReference<Object> connection,WeakReference<Object> channel) {}
 private static final ArrayDeque<Ingress> ingress=new ArrayDeque<>();
 public static synchronized void observeIngress(Connection connection,ChannelHandlerContext context,Packet<?> packet,PacketListener listener){
  boolean caller=STACK.walk(f->f.filter(x->x.getDeclaringClass()!=IssuedFarmTransportOrigin.class).findFirst().map(x->x.getDeclaringClass()==Connection.class).orElse(false));
  if(!caller||!(packet instanceof ClientboundLoginPacket||packet instanceof ClientboundRespawnPacket)||context==null)return;
  var channel=context.channel();boolean connected=connection.isConnected();
  if(channel==null||channel!=((IssuedFarmConnectionAccessor)(Object)connection).veltraph$issuedFarmChannel()||!connected)return;
  ingress.removeIf(i->i.packet.get()==null||i.listener.get()==null||i.connection.get()==null||i.channel.get()==null);if(ingress.size()==32)ingress.removeFirst();
  ingress.addLast(new Ingress(new WeakReference<>(packet),new WeakReference<>(listener),new WeakReference<>(connection),new WeakReference<>(channel)));
 }
 private static synchronized boolean takeIngress(Packet<?> packet,ClientPacketListener listener,Connection connection){
  var channel=((IssuedFarmConnectionAccessor)(Object)connection).veltraph$issuedFarmChannel();var it=ingress.iterator();while(it.hasNext()){var i=it.next();if(i.packet.get()==packet&&i.listener.get()==listener&&i.connection.get()==connection&&i.channel.get()==channel){it.remove();return true;}}return false;
 }
 public static void connectOuterReturned(NativeRestTransport backend,boolean normal){var i=issuedConnect;if(i==null||i.backend!=backend||!normal)return;var c=i.call.client();var snapshot=backend.issuedFarmSnapshot();if(!nativeCaller()||snapshot.connectScreen()!=i.connector||c.gui.screen()!=i.connector||!snapshot.current()||!i.call.sourceNativeAffiliationCurrent())return;postSnapshot=snapshot;expected=IssuedFarmExpectedObservation.joined(i.call,null,null,null,null,null,null,snapshot,VeltraphBootstrap.suspendedFarmReturnCoordinator());}
 public static final class IncomingInvocation {final ConnectInvocation connect;final Packet<?> packet;final ClientPacketListener listener;final Object previousPlayer,previousWorld,previousConnection;Object appliedPlayer,appliedLevel;boolean playerWritten,entityAdded;private IncomingInvocation(ConnectInvocation connect,Packet<?> packet,ClientPacketListener listener,Minecraft c){this.connect=connect;this.packet=packet;this.listener=listener;previousPlayer=c.player;previousWorld=c.level;previousConnection=c.getConnection();}}
 private static final ThreadLocal<IncomingInvocation> INCOMING=new ThreadLocal<>();
 /** Called only from actual packet processor's Packet.handle opcode with a previously authenticated ingress packet. */
 public static IncomingInvocation incomingEntered(Packet<?> packet,PacketListener listener){
  var caller=STACK.walk(f->f.filter(x->x.getDeclaringClass()!=IssuedFarmTransportOrigin.class).findFirst().map(x->x.getDeclaringClass().getName()).orElse(""));
  var i=issuedConnect;var c=Minecraft.getInstance();
  if(!caller.equals("net.minecraft.network.PacketProcessor$ListenerAndPacket")||i==null||!i.returned||INCOMING.get()!=null||!c.isSameThread()||!(listener instanceof ClientPacketListener nativeListener)
    ||!(packet instanceof ClientboundLoginPacket||packet instanceof ClientboundRespawnPacket)||!(i.connector instanceof ScheduledConnectScreenAccessor connector))return null;
  var connection=connector.veltraph$getConnection();var snapshot=i.backend.issuedFarmSnapshot();
  if(connection==null||connection==snapshot.originalConnection()||nativeListener.getConnection()!=connection||c.getConnection()!=nativeListener||snapshot.connectScreen()!=i.connector||!snapshot.current()||!takeIngress(packet,nativeListener,connection)||!i.call.sourceNativeAffiliationCurrent())return null;
  var result=new IncomingInvocation(i,packet,nativeListener,c);INCOMING.set(result);return result;
 }
 public static void playerWritten(Minecraft c,Object player){var i=INCOMING.get();if(i==null)return;boolean caller=STACK.walk(f->f.filter(x->x.getDeclaringClass()!=IssuedFarmTransportOrigin.class).findFirst().map(x->x.getDeclaringClass()==ClientPacketListener.class).orElse(false));if(caller&&c==i.connect.call.client()&&c.getConnection()==i.listener&&c.player==player&&player!=null&&!i.playerWritten){i.playerWritten=true;i.appliedPlayer=player;}}
 public static void entityAdded(Object level,Object player,boolean normal){var i=INCOMING.get();if(i==null)return;boolean caller=STACK.walk(f->f.filter(x->x.getDeclaringClass()!=IssuedFarmTransportOrigin.class).findFirst().map(x->x.getDeclaringClass()==ClientPacketListener.class).orElse(false));var c=i.connect.call.client();if(caller&&normal&&!i.entityAdded&&level==c.level&&player!=null){i.entityAdded=true;i.appliedLevel=level;if(i.appliedPlayer==null)i.appliedPlayer=player;}}
 public static void incomingReturned(IncomingInvocation i,Packet<?> packet,PacketListener listener,boolean normal){if(i==null)return;try{var c=i.connect.call.client();var snapshot=i.connect.backend.issuedFarmSnapshot();
  Object nativeTracker=i.listener instanceof ScheduledClientLevelLoadAccessor tracker?tracker.veltraph$levelLoadTracker():null;
  Object screenTracker=c.gui.screen() instanceof ScheduledLevelLoadingScreenAccessor screen?screen.veltraph$loadTracker():null;
  if(!i.playerWritten||!i.entityAdded||i.appliedPlayer!=c.player||i.appliedLevel!=c.level||INCOMING.get()!=i||i.packet!=packet||i.listener!=listener||!normal||c.player==null||c.level==null||c.getConnection()!=i.listener||c.player.level()!=c.level
    ||nativeTracker==null||screenTracker!=nativeTracker||snapshot.connectScreen()!=i.connect.connector||!snapshot.current()||!i.connect.call.sourceNativeAffiliationCurrent())return;
  postSnapshot=snapshot;expected=IssuedFarmExpectedObservation.joined(i.connect.call,null,null,null,c.player,c.level,c.getConnection(),snapshot,VeltraphBootstrap.suspendedFarmReturnCoordinator());
 }finally{if(INCOMING.get()==i)INCOMING.remove();}}
 public static boolean retireOwnedTransport(McSuspendedFarmReturnCoordinator.CloseCall call){
  if(call==null)return false;var i=issuedConnect;var departure=authenticatedDeparture;var core=VeltraphBootstrap.suspendedFarmReturnCoordinator();
  if(core==null||!call.current())return false;
  var backend=i!=null?i.backend:departure==null?null:departure.backend;if(backend==null)return true;
  var snapshot=postSnapshot;if(snapshot==null||snapshot.transport()!=backend)return false;
  var c=call.client();var screen=i==null?null:i.connector;
  io.netty.channel.ChannelFuture future=null;net.minecraft.network.Connection connection=null;
  if(screen instanceof ScheduledConnectScreenAccessor access){future=access.veltraph$getChannelFuture();connection=access.veltraph$getConnection();}
  boolean sameSource=(i!=null?i.call.sourceHeader():departure.call.sourceHeader())==call.sourceHeader()&&(i!=null?i.call.serial():departure.call.serial())==call.serial();
  boolean nativeAbort=sameSource&&i!=null&&i.returned&&screen!=null&&snapshot.connectScreen()==screen&&IssuedFarmTransportRetirement.nativeMatch(i.call.sourceHeader(),call.sourceHeader(),screen,c.gui.screen()==snapshot.restScreen()?screen:c.gui.screen(),future,future,connection,connection,c.player==null&&c.level==null&&c.getConnection()==null,snapshot.current(),call.current())
    &&(c.gui.screen()==screen||c.gui.screen()==snapshot.restScreen())&&snapshot.current()&&call.current();
  if(nativeAbort&&screen instanceof ScheduledConnectScreenAccessor access){synchronized(screen){
    if(access.veltraph$getChannelFuture()==future&&access.veltraph$getConnection()==connection&&snapshot.current()&&call.current()){
      access.veltraph$setAborted(true);
      if(future!=null)future.cancel(true);
      if(access.veltraph$getChannelFuture()==future&&access.veltraph$getConnection()==connection&&snapshot.current()&&call.current()){
        if(future!=null)access.veltraph$setChannelFuture(null);if(connection!=null)connection.disconnect(net.minecraft.client.gui.screens.ConnectScreen.ABORT_CONNECTION);
      }
    }
  }}
  boolean retired=sameSource&&snapshot.retireMetadata();if(retired){if(issuedConnect==i)issuedConnect=null;if(authenticatedDeparture==departure)authenticatedDeparture=null;postSnapshot=null;expected=null;}
  return retired;
 }
 public static boolean hasOriginalTransport(McSuspendedFarmReturnCoordinator core,McSuspendedFarmReturnCoordinator.SourceHeader source){if(core==null||source==null||core!=VeltraphBootstrap.suspendedFarmReturnCoordinator())return false;return issuedConnect!=null&&issuedConnect.call.sourceHeader()==source||authenticatedDeparture!=null&&authenticatedDeparture.call.sourceHeader()==source;}
 public static boolean retireTransportMetadata(McSuspendedFarmReturnCoordinator core,McSuspendedFarmReturnCoordinator.SourceHeader source){
  if(core==null||source==null||core!=VeltraphBootstrap.suspendedFarmReturnCoordinator())return false;var snapshot=postSnapshot;
  if(snapshot==null)return true;if(!hasOriginalTransport(core,source))return false;boolean retired=snapshot.retireMetadata();if(retired){issuedConnect=null;authenticatedDeparture=null;postSnapshot=null;expected=null;}return retired;
 }
 public static IssuedFarmExpectedObservation expectedForOriginalRoot(McSuspendedFarmReturnCoordinator core){return currentExpected(core);}
 static IssuedFarmExpectedObservation currentExpected(McSuspendedFarmReturnCoordinator core){
  if(core==null||core!=VeltraphBootstrap.suspendedFarmReturnCoordinator())return null;
  var call=core.currentLandingCall();var receipt=call==null?null:IssuedFarmAppliedLanding.currentFor(call);
  if(receipt!=null&&receipt!=expectedPositionReceipt){expectedPositionReceipt=receipt;expected=IssuedFarmExpectedObservation.position(call,receipt);postSnapshot=null;}
  var e=expected;return e!=null&&e.currentScene(Minecraft.getInstance(),core)?e:null;
 }
}
