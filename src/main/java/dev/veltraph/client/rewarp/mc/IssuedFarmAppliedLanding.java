package dev.veltraph.client.rewarp.mc;
import java.lang.ref.WeakReference;
import net.minecraft.client.Minecraft;
import dev.veltraph.client.VeltraphBootstrap;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.Connection;
import io.netty.channel.*;
import dev.veltraph.client.mixin.IssuedFarmConnectionAccessor;
import net.minecraft.network.protocol.game.*;
/** Authenticated native incoming application and ACK operation witness; no active-window authorization. */
public final class IssuedFarmAppliedLanding {
 private static final ThreadLocal<Invocation> ACTIVE=new ThreadLocal<>();
 private static final ThreadLocal<DeviceInvocation> DEVICE=new ThreadLocal<>();
 private static DeviceInvocation latestDevice;
 private static final StackWalker STACK=StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);
 private static long serial;private static Receipt latest;private static final ThreadLocal<Object[]> PROCESSOR=new ThreadLocal<>();
 public static final class Invocation {private DeviceInvocation device;final ClientboundPlayerPositionPacket packet;final ClientPacketListener listener;final Connection connection;final Object player,world;final long origin;final McSuspendedFarmReturnCoordinator.LandingCall call;final McSuspendedFarmReturnCoordinator.LandingCall.OperationalAttempt attempt;IssuedFarmAckBodyOrder ackOrder;Object ackPacket;Channel ackChannel;EventLoop ackLoop;boolean nativePositionBody,applied,ack,ackBody,ackWrite,ackScheduled,refused;Invocation(ClientboundPlayerPositionPacket p,ClientPacketListener l,Connection c,Object player,Object world,long origin,McSuspendedFarmReturnCoordinator.LandingCall call,McSuspendedFarmReturnCoordinator.LandingCall.OperationalAttempt attempt){packet=p;listener=l;connection=c;this.player=player;this.world=world;this.origin=origin;this.call=call;this.attempt=attempt;}}
 public static final class Receipt {private final WeakReference<Object> player,world,listener,connection;private final long serial,origin;private final dev.veltraph.client.world.Vec3 position;private final int teleportId;private final float yaw,pitch;private final McSuspendedFarmReturnCoordinator.LandingCall call;private final McSuspendedFarmReturnCoordinator.LandingCall.OperationalAttempt attempt;private final long nativeSignalSerial;private McSuspendedFarmReturnCoordinator.LandingCall.OperationalResponse operational;private boolean consumed;private Receipt(Invocation i,Minecraft c){player=new WeakReference<>(i.player);world=new WeakReference<>(i.world);listener=new WeakReference<>(i.listener);connection=new WeakReference<>(i.connection);serial=++IssuedFarmAppliedLanding.serial;origin=i.origin;position=new dev.veltraph.client.world.Vec3(c.player.getX(),c.player.getY(),c.player.getZ());teleportId=i.packet.id();yaw=c.player.getYRot();pitch=c.player.getXRot();call=i.call;attempt=i.attempt;nativeSignalSerial=NativeTeleportSignal.read(c).serial();}
  long nativeSignalSerial(){return nativeSignalSerial;}
  boolean belongsTo(McSuspendedFarmReturnCoordinator.LandingCall exact,McSuspendedFarmReturnCoordinator.LandingCall.OperationalAttempt original){return call==exact&&attempt==original;}
  public long serial(){return serial;}public long originSerial(){return origin;}public dev.veltraph.client.world.Vec3 position(){return position;}public int teleportId(){return teleportId;}public float yaw(){return yaw;}public float pitch(){return pitch;}
  public boolean current(Minecraft c){return c.isSameThread()&&c.player==player.get()&&c.level==world.get()&&c.getConnection()==listener.get()&&c.getConnection()!=null&&c.getConnection().getConnection()==connection.get();}
 }
 public static Object processorEntered(Object packet,Object listener){boolean caller=STACK.walk(f->f.filter(x->x.getDeclaringClass()!=IssuedFarmAppliedLanding.class).findFirst().map(x->x.getDeclaringClass().getName().equals("net.minecraft.network.PacketProcessor$ListenerAndPacket")).orElse(false));if(!caller||!(packet instanceof ClientboundPlayerPositionPacket)||PROCESSOR.get()!=null)return null;var token=new Object[]{packet,listener};PROCESSOR.set(token);return token;}
 public static void processorExited(Object token){if(token!=null&&PROCESSOR.get()==token)PROCESSOR.remove();}
 public static void nativePositionReturned(Object player,boolean normal){if(DEVICE.get()!=null){try{devicePositionReturned(player,normal);}catch(Throwable observationFailure){refuseDevice();}return;}var i=ACTIVE.get();if(i==null)return;if(!nativeCaller()||player!=i.player||!normal||!current(i)){i.refused=true;return;}i.nativePositionBody=true;}
 private static boolean nativeConnectionCaller(){return STACK.walk(f->f.filter(x->x.getDeclaringClass()!=IssuedFarmAppliedLanding.class).findFirst().map(x->x.getDeclaringClass()==Connection.class).orElse(false));}
 private static boolean nativeCaller(){return STACK.walk(f->f.filter(x->x.getDeclaringClass()!=IssuedFarmAppliedLanding.class).findFirst().map(x->x.getDeclaringClass()==ClientPacketListener.class).orElse(false));}
 public static Invocation entered(ClientPacketListener listener,ClientboundPlayerPositionPacket packet){try{return enteredNative(listener,packet);}catch(RuntimeException|Error unavailable){return null;}}
 private static Invocation enteredNative(ClientPacketListener listener,ClientboundPlayerPositionPacket packet){
  Invocation device;try{device=enteredDeviceNative(listener,packet);}catch(Throwable observationFailure){refuseDevice();device=null;}if(device!=null)return device;
  var c=Minecraft.getInstance();var process=PROCESSOR.get();if(process==null||process[0]!=packet||process[1]!=listener||!nativeCaller()||!c.isSameThread())return null;
  var coordinator=VeltraphBootstrap.suspendedFarmReturnCoordinator();var call=coordinator==null?null:coordinator.originalLandingIdentityForNativeIngress(listener);if(call==null)return null;
  try{
   if(ACTIVE.get()!=null){ACTIVE.get().refused=true;call.refuseUnprovenOriginalIngress();return null;}
   if(c.player==null||c.level==null||c.getConnection()!=listener||c.player.level()!=c.level||c.player.isPassenger()){call.refuseUnprovenOriginalIngress();return null;}
   long origin=IssuedFarmIncomingOrigin.take(packet,listener,listener.getConnection());long now=System.currentTimeMillis();
   if(origin<0||call.client()!=c||call.listener()!=listener||call.connection()!=listener.getConnection()||now<0||now>=call.deadlineMillis()||!call.beforeTeleportNativeCurrent()){call.refuseUnprovenOriginalIngress();return null;}
   var attempt=call.claimOriginalOperationalResponse(origin);if(attempt==null){call.loseOriginalOperationalObservation();return null;}
   var i=new Invocation(packet,listener,listener.getConnection(),c.player,c.level,origin,call,attempt);ACTIVE.set(i);return i;
  }catch(RuntimeException|Error unavailable){call.loseOriginalOperationalObservation();return null;}
 }
 private static boolean current(Invocation i){var c=Minecraft.getInstance();return i!=null&&ACTIVE.get()==i&&!i.refused&&c.isSameThread()&&c.player==i.player&&c.level==i.world&&c.getConnection()==i.listener&&i.listener.getConnection()==i.connection&&!c.player.isPassenger()&&i.call.currentFinalNative();}
 public static void applied(ClientPacketListener listener,Object player,boolean normal){if(DEVICE.get()!=null){try{deviceApplied(listener,player,normal);}catch(Throwable observationFailure){refuseDevice();}return;}var i=ACTIVE.get();if(i==null)return;if(!nativeCaller()||listener!=i.listener||player!=i.player||!normal||!i.nativePositionBody||i.applied||!current(i)){i.refused=true;return;}i.applied=true;}
 public static boolean activeAck(){if(DEVICE.get()!=null)return true;return ACTIVE.get()!=null;}
 public static boolean ackBodyEntered(Connection connection,Object packet,boolean scheduled,EventLoop actualLoop){if(DEVICE.get()!=null){try{return deviceAckBodyEntered(connection,packet,scheduled,actualLoop);}catch(Throwable observationFailure){refuseDevice();return false;}}var i=ACTIVE.get();if(i==null)return false;var c=Minecraft.getInstance();var channel=((IssuedFarmConnectionAccessor)(Object)connection).veltraph$issuedFarmChannel();var loop=channel==null?null:channel.eventLoop();boolean exact=packet instanceof ServerboundAcceptTeleportationPacket p&&p.id()==i.packet.id();if(!nativeConnectionCaller()||channel==null||loop==null||(scheduled&&actualLoop!=loop)||connection!=i.connection||!i.applied||i.ackBody||!exact||!current(i)){i.refused=true;return false;}i.ackBody=true;i.ackScheduled=scheduled;i.ackChannel=channel;i.ackLoop=loop;if(i.ackOrder==null||i.ackPacket!=packet){i.refused=true;return false;}return i.ackOrder.enter(packet,scheduled);}
 public static boolean ackWriteReturned(Connection connection,Channel channel,Object packet,boolean normal){if(DEVICE.get()!=null){try{return deviceAckWriteReturned(connection,channel,packet,normal);}catch(Throwable observationFailure){refuseDevice();return false;}}var i=ACTIVE.get();if(i==null)return false;if(!nativeConnectionCaller()||channel!=i.ackChannel||((IssuedFarmConnectionAccessor)(Object)connection).veltraph$issuedFarmChannel()!=channel||connection!=i.connection||!i.ackBody||i.ackScheduled||i.ackWrite||!normal||!(packet instanceof ServerboundAcceptTeleportationPacket p)||p.id()!=i.packet.id()||!current(i)){i.refused=true;return false;}i.ackWrite=true;return i.ackOrder!=null&&i.ackOrder.written(packet,normal);}
 public static void ackRequested(Connection connection,Object packet){if(DEVICE.get()!=null){try{deviceAckRequested(connection,packet);}catch(Throwable observationFailure){refuseDevice();}return;}var i=ACTIVE.get();if(i==null)return;if(!nativeCaller()||connection!=i.connection||!i.applied||i.ackOrder!=null||!current(i)){i.refused=true;return;}i.ackPacket=packet;i.ackOrder=new IssuedFarmAckBodyOrder(packet);}
 public static void ackBodyReturned(Connection connection,Object packet,boolean normal){if(DEVICE.get()!=null){try{deviceAckBodyReturned(connection,packet,normal);}catch(Throwable observationFailure){refuseDevice();}return;}var i=ACTIVE.get();if(i==null)return;var channel=((IssuedFarmConnectionAccessor)(Object)connection).veltraph$issuedFarmChannel();var loop=channel==null?null:channel.eventLoop();if(channel!=i.ackChannel||loop!=i.ackLoop){i.refused=true;return;}if(!nativeConnectionCaller()||connection!=i.connection||!normal||!i.ackBody||(!i.ackScheduled&&!i.ackWrite)||!(packet instanceof ServerboundAcceptTeleportationPacket p)||p.id()!=i.packet.id()||!current(i))i.refused=true;else if(i.ackOrder==null||!i.ackOrder.returned(packet,normal))i.refused=true;}
 public static void acknowledged(ClientPacketListener listener,Connection connection,Object packet,boolean normal){if(DEVICE.get()!=null){try{deviceAcknowledged(listener,connection,packet,normal);}catch(Throwable observationFailure){refuseDevice();}return;}var i=ACTIVE.get();if(i==null)return;var c=Minecraft.getInstance();
  boolean exact=packet instanceof ServerboundAcceptTeleportationPacket p&&p.id()==i.packet.id()&&Double.doubleToLongBits(p.x())==Double.doubleToLongBits(c.player.getX())&&Double.doubleToLongBits(p.y())==Double.doubleToLongBits(c.player.getY())&&Double.doubleToLongBits(p.z())==Double.doubleToLongBits(c.player.getZ());
  if(!nativeCaller()||listener!=i.listener||connection!=i.connection||!normal||!i.applied||!i.ackBody||(!i.ackScheduled&&!i.ackWrite)||i.ack||!exact||!current(i)){i.refused=true;return;}if(i.ackOrder==null||!i.ackOrder.outer(packet,normal)||!i.ackOrder.complete()){i.refused=true;return;}i.ack=true;
 }
 public static void exited(Invocation i,ClientPacketListener listener,ClientboundPlayerPositionPacket packet,boolean normal){if(i!=null&&i.device!=null){try{deviceExited(i.device,listener,packet,normal);}catch(Throwable observationFailure){i.device.refused=true;i.device.receive.refuse();if(DEVICE.get()==i.device)DEVICE.remove();}return;}if(i==null)return;try{
  if(nativeCaller()&&listener==i.listener&&packet==i.packet&&normal&&i.applied&&i.ack&&current(i)){
   var actual=new Receipt(i,Minecraft.getInstance());var operational=i.call.completeOriginalOperationalResponse(i.attempt,actual);
   if(operational!=null){actual.operational=operational;latest=actual;}else i.refused=true;
  }else{i.refused=true;i.call.refuseOriginalOperationalResponse(i.attempt);}
 }catch(RuntimeException|Error observationFailure){i.refused=true;i.call.loseOriginalOperationalObservation();}
 finally{if(ACTIVE.get()==i)ACTIVE.remove();}}

 /** Device branch: its only authority is the exact privately bound native receive and completed ACK path. */
 private static final class DeviceInvocation {
  final dev.veltraph.client.movement.device.NativeDeviceTeleportReceipt.DeviceReceive receive;
  final ClientboundPlayerPositionPacket packet;final ClientPacketListener listener;final Connection connection;
  final Object player,world;final long origin;
  IssuedFarmAckBodyOrder ackOrder;Object ackPacket;Channel ackChannel;EventLoop ackLoop;
  boolean nativePositionBody,applied,ack,ackBody,ackWrite,ackScheduled,refused,handlerReturned,claimed;
  DeviceInvocation(dev.veltraph.client.movement.device.NativeDeviceTeleportReceipt.DeviceReceive receive,
    ClientboundPlayerPositionPacket packet,ClientPacketListener listener,Connection connection,Object player,Object world,long origin){
   this.receive=receive;this.packet=packet;this.listener=listener;this.connection=connection;this.player=player;this.world=world;this.origin=origin;
  }
 }
 /** Non-reconstructible proof, issued only after native ingress, application, ACK and both handler returns. */
 public static final class DeviceAppliedAck {
  private final DeviceInvocation original;
  private final dev.veltraph.client.movement.device.DeviceUseReceipt.Applied applied;
  private final NativeTeleportSignal.Receipt signal;
  private DeviceAppliedAck(DeviceInvocation original){this.original=original;applied=original.receive.nativeAppliedIdentity();signal=original.receive.signalIdentity();}
  public boolean matchesTransit(McSuspendedFarmReturnCoordinator.DeviceAckTransit exact,
      dev.veltraph.client.movement.device.DeviceUseReceipt.Issue issue,Object source,long baseline,NativeTeleportSignal.Receipt observed){
   var receive=original.receive;
   return original.claimed&&original.handlerReturned&&!original.refused&&original.origin>=0
     &&receive.ackTransitIdentity()==exact&&receive.issueIdentity()==issue&&receive.sourceIdentity()==source
     &&exact!=null&&exact.deviceTransitIdentity()==receive.transitIdentity()&&exact.issueIdentity()==issue
     &&exact.sourceIdentity()==source&&exact.baselineSignalSerial()==baseline&&baseline>=0&&baseline<Long.MAX_VALUE
     &&signal!=null&&observed!=null&&signal.serial()==baseline+1&&observed.serial()==signal.serial()
     &&signal.position()!=null&&signal.position().equals(observed.position())
     &&receive.packetIdentity()==original.packet&&receive.listenerIdentity()==original.listener&&receive.incomingOriginAfterSubmission(original.origin)
     &&receive.matchesNativeApplied(applied)&&receive.completedNativeCurrent();
  }
  public boolean matchesApplied(dev.veltraph.client.movement.device.DeviceUseReceipt.Applied exact){return exact!=null&&exact==applied&&original.receive.matchesNativeApplied(exact);}
  public boolean current(Minecraft c){return c!=null&&c==original.receive.client()&&c.isSameThread()
    &&c.player==original.player&&c.level==original.world&&c.getConnection()==original.listener
    &&original.listener.getConnection()==original.connection&&!original.refused&&original.handlerReturned
    &&original.receive.completedNativeCurrent();}
 }
 private static Invocation enteredDeviceNative(ClientPacketListener listener,ClientboundPlayerPositionPacket packet){
  var c=Minecraft.getInstance();var receive=dev.veltraph.client.movement.device.NativeDeviceTeleportReceipt.captureIssuedReturnReceive(c,listener,packet);
  if(receive==null)return null;
  var process=PROCESSOR.get();
  if(process==null||process[0]!=packet||process[1]!=listener||!nativeCaller()||!c.isSameThread()||c.player==null||c.level==null
      ||c.getConnection()!=listener||c.player.level()!=c.level||c.player.isPassenger()||!receive.beforeNativeHeadCurrent()){
   receive.refuse();return null;
  }
  if(DEVICE.get()!=null||ACTIVE.get()!=null){if(DEVICE.get()!=null)DEVICE.get().refused=true;receive.refuse();return null;}
  long origin=IssuedFarmIncomingOrigin.take(packet,listener,listener.getConnection());if(origin<0||!receive.incomingOriginAfterSubmission(origin)){receive.refuse();return null;}
  var device=new DeviceInvocation(receive,packet,listener,listener.getConnection(),c.player,c.level,origin);
  DEVICE.set(device);var result=new Invocation(packet,listener,listener.getConnection(),c.player,c.level,origin,null,null);result.device=device;return result;
 }
 private static void refuseDevice(){var i=DEVICE.get();if(i!=null){i.refused=true;i.receive.refuse();}}
 private static boolean deviceCurrent(DeviceInvocation i){var c=Minecraft.getInstance();return i!=null&&DEVICE.get()==i&&!i.refused
   &&c.isSameThread()&&c.player==i.player&&c.level==i.world&&c.getConnection()==i.listener&&i.listener.getConnection()==i.connection
   &&!c.player.isPassenger()&&i.receive.nativeSourceCurrent();}
 private static void devicePositionReturned(Object player,boolean normal){var i=DEVICE.get();
  if(!nativeCaller()||player!=i.player||!normal||i.nativePositionBody||!deviceCurrent(i)){i.refused=true;return;}i.nativePositionBody=true;
 }
 private static void deviceApplied(ClientPacketListener listener,Object player,boolean normal){var i=DEVICE.get();
  if(!nativeCaller()||listener!=i.listener||player!=i.player||!normal||!i.nativePositionBody||i.applied||!deviceCurrent(i)){i.refused=true;return;}i.applied=true;
 }
 private static void deviceAckRequested(Connection connection,Object packet){var i=DEVICE.get();
  if(!nativeCaller()||connection!=i.connection||!i.applied||i.ackOrder!=null||!deviceCurrent(i)){i.refused=true;return;}
  i.ackPacket=packet;i.ackOrder=new IssuedFarmAckBodyOrder(packet);
 }
 private static boolean deviceAckBodyEntered(Connection connection,Object packet,boolean scheduled,EventLoop actualLoop){var i=DEVICE.get();
  var channel=((IssuedFarmConnectionAccessor)(Object)connection).veltraph$issuedFarmChannel();var loop=channel==null?null:channel.eventLoop();
  boolean exact=packet instanceof ServerboundAcceptTeleportationPacket p&&p.id()==i.packet.id();
  if(!nativeConnectionCaller()||channel==null||loop==null||(scheduled&&actualLoop!=loop)||connection!=i.connection
      ||!i.applied||i.ackBody||!exact||!deviceCurrent(i)){i.refused=true;return false;}
  i.ackBody=true;i.ackScheduled=scheduled;i.ackChannel=channel;i.ackLoop=loop;
  if(i.ackOrder==null||i.ackPacket!=packet){i.refused=true;return false;}return i.ackOrder.enter(packet,scheduled);
 }
 private static boolean deviceAckWriteReturned(Connection connection,Channel channel,Object packet,boolean normal){var i=DEVICE.get();
  if(!nativeConnectionCaller()||channel!=i.ackChannel||((IssuedFarmConnectionAccessor)(Object)connection).veltraph$issuedFarmChannel()!=channel
      ||connection!=i.connection||!i.ackBody||i.ackScheduled||i.ackWrite||!normal
      ||!(packet instanceof ServerboundAcceptTeleportationPacket p)||p.id()!=i.packet.id()||!deviceCurrent(i)){i.refused=true;return false;}
  i.ackWrite=true;return i.ackOrder!=null&&i.ackOrder.written(packet,normal);
 }
 private static void deviceAckBodyReturned(Connection connection,Object packet,boolean normal){var i=DEVICE.get();
  var channel=((IssuedFarmConnectionAccessor)(Object)connection).veltraph$issuedFarmChannel();var loop=channel==null?null:channel.eventLoop();
  if(channel!=i.ackChannel||loop!=i.ackLoop||!nativeConnectionCaller()||connection!=i.connection||!normal||!i.ackBody
      ||(!i.ackScheduled&&!i.ackWrite)||!(packet instanceof ServerboundAcceptTeleportationPacket p)||p.id()!=i.packet.id()
      ||!deviceCurrent(i)||i.ackOrder==null||!i.ackOrder.returned(packet,normal))i.refused=true;
 }
 private static void deviceAcknowledged(ClientPacketListener listener,Connection connection,Object packet,boolean normal){var i=DEVICE.get();var c=Minecraft.getInstance();
  boolean exact=c.player!=null&&packet instanceof ServerboundAcceptTeleportationPacket p&&p.id()==i.packet.id()
      &&Double.doubleToLongBits(p.x())==Double.doubleToLongBits(c.player.getX())
      &&Double.doubleToLongBits(p.y())==Double.doubleToLongBits(c.player.getY())
      &&Double.doubleToLongBits(p.z())==Double.doubleToLongBits(c.player.getZ());
  if(!nativeCaller()||listener!=i.listener||connection!=i.connection||!normal||!i.applied||!i.ackBody
      ||(!i.ackScheduled&&!i.ackWrite)||i.ack||!exact||!deviceCurrent(i)
      ||i.ackOrder==null||!i.ackOrder.outer(packet,normal)||!i.ackOrder.complete()){i.refused=true;return;}i.ack=true;
 }
 private static void deviceExited(DeviceInvocation i,ClientPacketListener listener,ClientboundPlayerPositionPacket packet,boolean normal){
  try{if(nativeCaller()&&listener==i.listener&&packet==i.packet&&normal&&i.nativePositionBody&&i.applied&&i.ack
      &&i.ackOrder!=null&&i.ackOrder.complete()&&deviceCurrent(i)){
    i.handlerReturned=true;latestDevice=i;
   }else{i.refused=true;i.receive.refuse();}}
  finally{if(DEVICE.get()==i)DEVICE.remove();}
  if(i.handlerReturned)dev.veltraph.client.movement.device.NativeDeviceTeleportReceipt.completeIssuedReturnReceive(i.receive);
 }
 /** Read-only native ingress baseline: the authenticated device body bridge alone reads this operand. */
 public static long deviceBodyIncomingBaseline(){
  boolean body=STACK.walk(frames->frames.filter(frame->frame.getDeclaringClass()!=IssuedFarmAppliedLanding.class).findFirst()
      .map(frame->frame.getDeclaringClass()==dev.veltraph.client.movement.device.NativeDeviceTeleportReceipt.class
          &&frame.getMethodName().equals("captureIssuedReturnSendBody")).orElse(false));
  return body?IssuedFarmIncomingOrigin.nativeIngressSerial():-1;
 }
 public static void retireDeviceReceive(dev.veltraph.client.movement.device.NativeDeviceTeleportReceipt.DeviceReceive receive){
  if(receive!=null&&latestDevice!=null&&latestDevice.receive==receive)latestDevice=null;
 }
 /** Read-only counterpart readiness. It cannot mint or credit an ACK proof. */
 public static boolean deviceHandlerReturned(dev.veltraph.client.movement.device.NativeDeviceTeleportReceipt.DeviceReceive receive){
  var i=latestDevice;return i!=null&&i.receive==receive&&!i.claimed&&!i.refused&&i.handlerReturned
    &&i.nativePositionBody&&i.applied&&i.ack&&i.ackOrder!=null&&i.ackOrder.complete();
 }
 /** The journal bridge can read this completed private proof; no request or copied values can manufacture it. */
 public static DeviceAppliedAck claimDeviceAck(dev.veltraph.client.movement.device.NativeDeviceTeleportReceipt.DeviceReceive receive){
  boolean bridge=STACK.walk(f->f.filter(x->x.getDeclaringClass()!=IssuedFarmAppliedLanding.class).findFirst()
      .map(x->x.getDeclaringClass()==dev.veltraph.client.movement.device.NativeDeviceTeleportReceipt.class).orElse(false));
  var i=latestDevice;if(!bridge||i==null||i.receive!=receive||i.claimed||i.refused||!i.handlerReturned
      ||!i.nativePositionBody||!i.applied||!i.ack||i.ackOrder==null||!i.ackOrder.complete()
      ||!receive.completedNativeCurrent()||receive.nativeAppliedIdentity()==null)return null;
  i.claimed=true;return new DeviceAppliedAck(i);
 }
 static long serial(Minecraft c){return latest!=null&&latest.current(c)?latest.serial():serial;}
 static Receipt currentFor(McSuspendedFarmReturnCoordinator.LandingCall call){if(call==null)return null;var receipt=latest;long now=System.currentTimeMillis();if(receipt==null||receipt.call!=call||receipt.consumed||receipt.serial()!=call.baselineReceiptSerial()+1||!receipt.current(call.client())||receipt.operational==null||!call.originalOperationalAdmissionCurrent(receipt)||now<0||now>=call.deadlineMillis()||!call.currentFinalNative())return null;return receipt;}
 static Receipt consume(McSuspendedFarmReturnCoordinator.LandingCall call){var receipt=currentFor(call);if(receipt==null)return null;receipt.consumed=true;return receipt;}
 static Receipt read(Minecraft c){return latest!=null&&latest.current(c)?latest:null;}
}
