package dev.veltraph.client.movement.device;
import java.lang.ref.WeakReference;
import java.util.function.BooleanSupplier;
import dev.veltraph.client.world.Vec3;
import net.minecraft.client.Minecraft;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.world.InteractionHand;

/** Passive native journal with exact actual invocation/session identity. No sender or response prediction. */
public final class NativeDeviceTeleportReceipt {
 public record Receipt(long serial,long receivedAt,Vec3 position) {}
 private static WeakReference<Object> player=new WeakReference<>(null),world=new WeakReference<>(null),connection=new WeakReference<>(null);
 private static long serial;private static Receipt receipt;private static Session active;
 private record Session(Minecraft client,DeviceUseReceipt.Issue issue,BooleanSupplier fresh,DeviceSubmissionReceipt proof) {}
 private static DeviceBinding returnBinding;
 private static final ThreadLocal<SendInvocation> DEVICE_SEND=new ThreadLocal<>();
 private static final StackWalker RETURN_BIND_STACK=StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);
 /** Extra veto only. Genuine Session, issue, source and private transit gates still independently apply. */
 private static boolean issuedReturnBindCallerCurrent(){return RETURN_BIND_STACK.walk(frames->frames
     .filter(frame->frame.getDeclaringClass()!=NativeDeviceTeleportReceipt.class).findFirst()
     .map(frame->frame.getDeclaringClass()==McInstantTransmission.class&&frame.getMethodName().equals("captureOwnedUseAttempt"))
     .orElse(false));}
 private static final class DeviceBinding {
  final Session session;final dev.veltraph.client.rewarp.mc.McSavedAnchorRecovery.IssuedReturnTravelRequest.DeviceTransit transit;DeviceReceive receive;DeviceSendBody submission;
  DeviceBinding(Session session,dev.veltraph.client.rewarp.mc.McSavedAnchorRecovery.IssuedReturnTravelRequest.DeviceTransit transit){this.session=session;this.transit=transit;}
 }
 public static final class SendInvocation {
  private final Session session;private final Packet<?> packet;private final Connection transport;private final DeviceInvocationIdentity.Invocation identity;private final boolean eligible;
  private SendInvocation(Session session,Packet<?> packet,Connection transport,boolean eligible){this.session=session;this.packet=packet;this.transport=transport;this.eligible=eligible;identity=DeviceInvocationIdentity.head(session,packet,transport,eligible);}
 }
 public static final class ReceiveInvocation {
  private DeviceReceive device;
  private final Session session;private final Minecraft client;private final Object listener;private final ClientboundPlayerPositionPacket packet;private final DeviceInvocationIdentity.Invocation identity;
  private ReceiveInvocation(Session session,Minecraft client,Object listener,ClientboundPlayerPositionPacket packet,boolean eligible){this.session=session;this.client=client;this.listener=listener;this.packet=packet;identity=DeviceInvocationIdentity.head(session,packet,listener,eligible);}
 }
 /** Read-only identity of the observer actually bound by the real source; alone it grants no suppression. */
 static final class SourceSession {
  private final Session original;
  private SourceSession(Session original){this.original=original;}
  boolean currentOpen(DeviceUseReceipt.Issue exact){return active==original&&original.issue()==exact&&original.client().isSameThread()
    &&original.proof().windowOpen()&&!original.proof().revoked()&&nativeContext(original.client(),exact.context());}
  boolean matches(SendInvocation actual,DeviceUseReceipt.Issue exact,Packet<?> packet,Connection transport){return currentOpen(exact)&&actual!=null
    &&actual.session==original&&actual.packet==packet&&actual.transport==transport&&actual.eligible;}
 }
 static SourceSession sourceSession(DeviceUseReceipt.Issue exact){return same(exact)?new SourceSession(active):null;}
 private NativeDeviceTeleportReceipt() {}
 public static boolean arm(Minecraft c,DeviceUseReceipt.Issue issue,BooleanSupplier fresh){
  if(c==null||!c.isSameThread()||issue==null||fresh==null||active!=null||!nativeContext(c,issue.context())||!fresh.getAsBoolean()||active!=null||!nativeContext(c,issue.context()))return false;
  active=new Session(c,issue,fresh,new DeviceSubmissionReceipt(issue));return true;
 }
 public static void openSubmissionWindow(DeviceUseReceipt.Issue issue){if(same(issue))active.proof().open();}
 public static void closeSubmissionWindow(DeviceUseReceipt.Issue issue){if(same(issue))active.proof().close();}
 public static void returnedNormally(DeviceUseReceipt.Issue issue){if(same(issue))active.proof().returnedNormally();}
 public static boolean submitted(DeviceUseReceipt.Issue issue){return same(issue)&&active.proof().dispatched();}
 public static SendInvocation beginSend(Connection transport,Packet<?> packet){
  var a=active;if(a==null||!a.client().isSameThread()||!(packet instanceof ServerboundUseItemPacket use)||use.hand()!=InteractionHand.MAIN_HAND)return null;
  boolean eligible=false;
  try{eligible=a.client().getConnection()!=null&&transport==a.client().getConnection().getConnection()&&a.proof().windowOpen()
       &&nativeContext(a.client(),a.issue().context())&&a.fresh().getAsBoolean()&&active==a;}catch(Throwable observationFailure){/* Refuse only this captured invocation/session. */}
  var call=new SendInvocation(a,packet,transport,eligible);
  if(returnBinding!=null&&returnBinding.session==a){
   if(DEVICE_SEND.get()!=null){failCaptured(a);return call;}
   DEVICE_SEND.set(call);
  }
  return call;
 }
 public static void finishSend(SendInvocation call,boolean returned){
  try{finishSendOriginal(call,returned);}finally{
   finishIssuedReturnSubmission(call,returned);if(DEVICE_SEND.get()==call)DEVICE_SEND.remove();
  }
 }
 private static void finishSendOriginal(SendInvocation call,boolean returned){
  if(call==null||!call.session.client().isSameThread())return;
  var result=DeviceInvocationIdentity.finish(call.identity,active,call.packet,call.transport,returned);
  if(result==DeviceInvocationIdentity.Result.INERT)return;
  try{
   if(result!=DeviceInvocationIdentity.Result.RETURNED||!nativeContext(call.session.client(),call.session.issue().context())||!call.session.fresh().getAsBoolean()){
    failCaptured(call.session);return;
   }
   if(active==call.session)call.session.proof().submitted(call.session.issue().context(),System.currentTimeMillis());
  }catch(Throwable observationFailure){failCaptured(call.session);}
 }
 public static ReceiveInvocation beginReceive(Minecraft c,Object listener,ClientboundPlayerPositionPacket packet){
  var a=active;boolean eligible=false;
  try{eligible=a!=null&&a.client()==c&&c!=null&&c.isSameThread()&&c.getConnection()==listener&&a.proof().dispatched()
       &&nativeContext(c,a.issue().context())&&a.fresh().getAsBoolean()&&active==a;}catch(Throwable observationFailure){/* Captured refusal, never a new global session. */}
  var call=new ReceiveInvocation(a,c,listener,packet,eligible);
  if(eligible&&returnBinding!=null&&returnBinding.session==a)call.device=captureIssuedReturnReceive(c,listener,packet);
  return call;
 }
 public static void finishReceive(ReceiveInvocation call,boolean returned){
  if(call==null||call.client==null||!call.client.isSameThread())return;
  // Freshness journal follows actual native state independently of operation correlation, even after an old invocation retires.
  try{recordRaw(call.client,call.listener);}catch(Throwable observationFailure){failCaptured(call.session);return;}
  var result=DeviceInvocationIdentity.finish(call.identity,active,call.packet,call.listener,returned);
  if(result==DeviceInvocationIdentity.Result.INERT)return;
  var a=call.session;
  if(call.device!=null){finishIssuedReturnReceive(call,result);return;}
  try{
   if(result!=DeviceInvocationIdentity.Result.RETURNED||!nativeContext(call.client,a.issue().context())||!a.fresh().getAsBoolean()){
    failCaptured(a);return;
   }
   if(active==a&&receipt!=null&&player.get()==a.issue().context().player()&&world.get()==a.issue().context().world()&&connection.get()==a.issue().context().connection())
    a.proof().observe(a.issue().context(),receipt.serial(),receipt.receivedAt(),receipt.position());
   else failCaptured(a);
  }catch(Throwable observationFailure){failCaptured(a);}
 }

 /** Binds only the actual dispatcher-private return capsule to its already armed observer session. */
 public static boolean bindIssuedReturnTransit(DeviceUseReceipt.Issue issue,
     dev.veltraph.client.rewarp.mc.McSavedAnchorRecovery.IssuedReturnTravelRequest.DeviceTransit transit){
  if(!issuedReturnBindCallerCurrent())return false;
  var a=active;
  if(a==null||a.issue()!=issue||transit==null||!a.client().isSameThread()||a.proof().windowOpen()||a.proof().dispatched()
      ||returnBinding!=null&&returnBinding.session==a||issue.baseline()<0||issue.baseline()==Long.MAX_VALUE||transit.issueIdentity()!=issue||transit.sourceIdentity()==null
      ||transit.ackTransitIdentity()==null||transit.ackTransitIdentity().deviceTransitIdentity()!=transit
      ||transit.ackTransitIdentity().issueIdentity()!=issue||transit.ackTransitIdentity().sourceIdentity()!=transit.sourceIdentity()
      ||!transit.nativeCurrent()||!transit.ackTransitIdentity().beforeReceiveCurrent()||!nativeContext(a.client(),issue.context()))return false;
  returnBinding=new DeviceBinding(a,transit);return true;
 }

 /** Actual SDK body token. Neither a public issue nor a supplied counter can construct this witness. */
 public static final class DeviceSendBody {
  private final DeviceBinding binding;private final SendInvocation call;
  private final Connection transport;private final Object packet;private final io.netty.channel.Channel channel;
  private final io.netty.channel.EventLoop loop;private final boolean scheduled;private final long incomingBaseline;
  private boolean bodyReturned,sendReturned,write,refused;
  private DeviceSendBody(DeviceBinding binding,SendInvocation call,Connection transport,Object packet,
      io.netty.channel.Channel channel,io.netty.channel.EventLoop loop,boolean scheduled,long incomingBaseline){
   this.binding=binding;this.call=call;this.transport=transport;this.packet=packet;this.channel=channel;this.loop=loop;
   this.scheduled=scheduled;this.incomingBaseline=incomingBaseline;
  }
 }
 private static boolean issuedDeviceConnectionCaller(){return RETURN_BIND_STACK.walk(frames->frames
     .filter(frame->frame.getDeclaringClass()!=NativeDeviceTeleportReceipt.class).findFirst()
     .map(frame->frame.getDeclaringClass()==Connection.class).orElse(false));}
 /** Capture only at the registered direct-body or scheduled-body opcode, before the actual native call. */
 public static DeviceSendBody captureIssuedReturnSendBody(Connection transport,Object packet,boolean scheduled,io.netty.channel.EventLoop actualLoop){
  var call=DEVICE_SEND.get();if(call==null||!call.session.client().isSameThread())return null;var binding=returnBinding;
  if(binding==null||binding.session!=call.session||active!=call.session||!call.eligible
      ||call.packet!=packet||call.transport!=transport)return null;
  try{
   if(!issuedDeviceConnectionCaller()||!call.session.client().isSameThread()||binding.submission!=null
       ||!call.session.proof().windowOpen()||!nativeContext(call.session.client(),call.session.issue().context())
       ||!(packet instanceof ServerboundUseItemPacket use)||use.hand()!=InteractionHand.MAIN_HAND
       ||!NativeOwnedDeviceUseBoundary.isActualOwnedPacket(use)||binding.transit.issueIdentity()!=call.session.issue()
       ||binding.transit.sourceIdentity()==null||!binding.transit.nativeIssueCurrent()){failCaptured(call.session);return null;}
   var channel=((dev.veltraph.client.mixin.IssuedFarmConnectionAccessor)(Object)transport).veltraph$issuedFarmChannel();
   var loop=channel==null?null:channel.eventLoop();
   if(channel==null||loop==null||scheduled&&actualLoop!=loop||!transport.isConnected()){failCaptured(call.session);return null;}
   long baseline=dev.veltraph.client.rewarp.mc.IssuedFarmAppliedLanding.deviceBodyIncomingBaseline();
   if(!DeviceIncomingOriginFence.validBaseline(baseline)){failCaptured(call.session);return null;}
   var body=new DeviceSendBody(binding,call,transport,packet,channel,loop,scheduled,baseline);binding.submission=body;return body;
  }catch(Throwable unavailable){failCaptured(call.session);return null;}
 }
 public static void issuedReturnSendBodyReturned(DeviceSendBody body,Connection transport,Object packet,boolean normal){
  if(body==null||!body.call.session.client().isSameThread())return;
  try{
   var channel=((dev.veltraph.client.mixin.IssuedFarmConnectionAccessor)(Object)transport).veltraph$issuedFarmChannel();
   if(!issuedDeviceConnectionCaller()||DEVICE_SEND.get()!=body.call||returnBinding!=body.binding||active!=body.call.session
       ||body.binding.submission!=body||body.transport!=transport||body.packet!=packet||!normal||body.bodyReturned
       ||body.refused||channel!=body.channel||channel.eventLoop()!=body.loop||(!body.scheduled&&!body.write)){
    body.refused=true;failCaptured(body.call.session);return;
   }
   body.bodyReturned=true;
  }catch(Throwable unavailable){body.refused=true;failCaptured(body.call.session);}
 }
 public static void issuedReturnSendWriteReturned(Connection transport,io.netty.channel.Channel channel,Object packet,boolean normal){
  var call=DEVICE_SEND.get();if(call==null||!call.session.client().isSameThread())return;var binding=returnBinding;var body=binding==null?null:binding.submission;
  if(body==null||body.call!=call||body.packet!=packet||body.transport!=transport)return;
  try{
   if(!issuedDeviceConnectionCaller()||active!=call.session||returnBinding!=body.binding||body.scheduled||body.write
       ||body.bodyReturned||!normal||channel!=body.channel
       ||((dev.veltraph.client.mixin.IssuedFarmConnectionAccessor)(Object)transport).veltraph$issuedFarmChannel()!=channel){
    body.refused=true;failCaptured(call.session);return;
   }
   body.write=true;
  }catch(Throwable unavailable){body.refused=true;failCaptured(call.session);}
 }
 private static void finishIssuedReturnSubmission(SendInvocation call,boolean normal){
  if(call==null||!call.session.client().isSameThread())return;var binding=returnBinding;if(binding==null||binding.session!=call.session)return;
  var body=binding.submission;
  if(DEVICE_SEND.get()!=call||body==null||body.call!=call||body.refused||!body.bodyReturned||body.sendReturned
      ||!normal||!call.eligible||active!=call.session||call.session.proof().revoked()){
   if(body!=null)body.refused=true;failCaptured(call.session);return;
  }
  body.sendReturned=true;
 }
 /** Opaque captured receive identity; it cannot stand in for the actual native application/ACK witness. */
 public static final class DeviceReceive {
  private final DeviceBinding binding;private final Session session;private final Minecraft client;
  private final Object listener;private final ClientboundPlayerPositionPacket packet;
  private final dev.veltraph.client.rewarp.mc.McSuspendedFarmReturnCoordinator.DeviceAckTransit ack;
  private final Object source;private final long signalBaseline;private final DeviceSendBody submission;
  private boolean refused,journalReturned,joined;private Receipt raw;
  private dev.veltraph.client.rewarp.mc.NativeTeleportSignal.Receipt signal;
  private DeviceUseReceipt.Applied nativeApplied;
  private DeviceReceive(DeviceBinding binding,Minecraft client,Object listener,ClientboundPlayerPositionPacket packet){
   this.binding=binding;session=binding.session;this.client=client;this.listener=listener;this.packet=packet;
   ack=binding.transit.ackTransitIdentity();source=binding.transit.sourceIdentity();signalBaseline=ack.baselineSignalSerial();submission=binding.submission;
  }
  public Minecraft client(){return client;}public Object packetIdentity(){return packet;}public Object listenerIdentity(){return listener;}
  public DeviceUseReceipt.Issue issueIdentity(){return session.issue();}
  public dev.veltraph.client.rewarp.mc.McSavedAnchorRecovery.IssuedReturnTravelRequest.DeviceTransit transitIdentity(){return binding.transit;}
  public dev.veltraph.client.rewarp.mc.McSuspendedFarmReturnCoordinator.DeviceAckTransit ackTransitIdentity(){return ack;}
  public Object sourceIdentity(){return source;}
  public boolean beforeNativeHeadCurrent(){return nativeSourceCurrent()&&submittedIncomingBaselineCurrent()&&!journalReturned&&!joined
     &&baseline(client)==session.issue().baseline()&&ack.beforeReceiveCurrent();}
  public boolean nativeSourceCurrent(){long now=System.currentTimeMillis();return now>=session.issue().issuedAt()&&now<session.issue().deadline()&&!refused&&active==session&&returnBinding==binding&&binding.receive==this
     &&client!=null&&client.isSameThread()&&client.getConnection()==listener&&nativeContext(client,session.issue().context())
     &&session.proof().dispatched()&&submittedIncomingBaselineCurrent()&&binding.transit.issueIdentity()==session.issue()
     &&binding.transit.sourceIdentity()==source&&binding.transit.ackTransitIdentity()==ack&&binding.transit.nativeIssueCurrent();}
  public boolean completedNativeCurrent(){return nativeSourceCurrent()&&journalReturned&&raw!=null&&signal!=null&&nativeApplied!=null
     &&session.proof().applied()==nativeApplied&&receipt==raw&&raw.serial()==session.issue().baseline()+1
     &&signalBaseline>=0&&signalBaseline<Long.MAX_VALUE&&signal.serial()==signalBaseline+1
     &&dev.veltraph.client.rewarp.mc.NativeTeleportSignal.read(client).serial()==signal.serial()
     &&signal.position()!=null&&signal.position().equals(raw.position())&&raw.position().equals(new Vec3(client.player.getX(),client.player.getY(),client.player.getZ()));}
  private boolean submittedIncomingBaselineCurrent(){return submission!=null&&binding.submission==submission
     &&submission.binding==binding&&submission.call.session==session&&submission.call.packet==submission.packet
     &&submission.call.transport==submission.transport&&submission.bodyReturned&&submission.sendReturned&&!submission.refused
     &&(submission.scheduled||submission.write)&&DeviceIncomingOriginFence.validBaseline(submission.incomingBaseline)
     &&((dev.veltraph.client.mixin.IssuedFarmConnectionAccessor)(Object)submission.transport).veltraph$issuedFarmChannel()==submission.channel
     &&submission.channel.eventLoop()==submission.loop&&submission.transport.isConnected();}
  /** Historical-origin veto only. Actual private native ingress/application/ACK still supplies the receipt. */
  public boolean incomingOriginAfterSubmission(long actualOrigin){return nativeSourceCurrent()&&submittedIncomingBaselineCurrent()
     &&DeviceIncomingOriginFence.strictlyAfter(submission.incomingBaseline,actualOrigin);}
  public DeviceUseReceipt.Applied nativeAppliedIdentity(){return nativeApplied;}
  public dev.veltraph.client.rewarp.mc.NativeTeleportSignal.Receipt signalIdentity(){return signal;}
  public boolean matchesNativeApplied(DeviceUseReceipt.Applied actual){return actual!=null&&actual==nativeApplied&&session.proof().applied()==actual;}
  public void refuse(){refused=true;dev.veltraph.client.rewarp.mc.IssuedFarmAppliedLanding.retireDeviceReceive(this);failCaptured(session);}
 }
 public static DeviceReceive captureIssuedReturnReceive(Minecraft c,Object listener,ClientboundPlayerPositionPacket packet){
  var binding=returnBinding;var a=active;
  if(binding==null||a==null||binding.session!=a||packet==null||c!=a.client()||!c.isSameThread()||c.getConnection()!=listener
      ||!a.proof().dispatched()||binding.submission==null||!binding.submission.bodyReturned||!binding.submission.sendReturned
      ||binding.submission.refused||!nativeContext(c,a.issue().context())||!binding.transit.nativeIssueCurrent())return null;
  var captured=binding.receive;
  if(captured!=null){if(captured.client==c&&captured.listener==listener&&captured.packet==packet&&!captured.journalReturned&&!captured.joined)return captured;
   captured.refuse();return null;}
  if(baseline(c)!=a.issue().baseline()||!binding.transit.ackTransitIdentity().beforeReceiveCurrent())return null;
  var next=new DeviceReceive(binding,c,listener,packet);binding.receive=next;return next;
 }
 private static void finishIssuedReturnReceive(ReceiveInvocation call,DeviceInvocationIdentity.Result result){
  var captured=call.device;
  if(result!=DeviceInvocationIdentity.Result.RETURNED||captured==null||captured.session!=call.session
      ||captured.client!=call.client||captured.listener!=call.listener||captured.packet!=call.packet
      ||captured.journalReturned||!captured.nativeSourceCurrent()||receipt==null
      ||player.get()!=call.session.issue().context().player()||world.get()!=call.session.issue().context().world()
      ||connection.get()!=call.session.issue().context().connection()){
   if(captured!=null)captured.refuse();else failCaptured(call.session);return;
  }
  captured.raw=receipt;captured.signal=dev.veltraph.client.rewarp.mc.NativeTeleportSignal.read(call.client);captured.journalReturned=true;
  if(captured.raw.serial()!=call.session.issue().baseline()+1||captured.signal.serial()!=captured.signalBaseline+1
      ||captured.signal.position()==null||!captured.signal.position().equals(captured.raw.position())){captured.refuse();return;}
  // The other real wrapper may not have returned yet. No fresh supplier runs until the full native ACK proof joins.
  completeIssuedReturnReceive(captured);
 }
 public static void completeIssuedReturnReceive(DeviceReceive captured){
  if(captured==null||!captured.journalReturned||captured.joined||captured.refused)return;
  if(!captured.nativeSourceCurrent()){captured.refuse();return;}
  if(!dev.veltraph.client.rewarp.mc.IssuedFarmAppliedLanding.deviceHandlerReturned(captured))return;
  var a=captured.session;
  try{
   if(captured.nativeApplied==null){a.proof().observe(a.issue().context(),captured.raw.serial(),captured.raw.receivedAt(),captured.raw.position());captured.nativeApplied=a.proof().applied();}
   if(captured.nativeApplied==null||!captured.completedNativeCurrent()){captured.refuse();return;}
   var nativeAck=dev.veltraph.client.rewarp.mc.IssuedFarmAppliedLanding.claimDeviceAck(captured);
   if(nativeAck==null)return; // Only the genuine full handler-return hook can produce the missing counterpart.
   if(!captured.ack.creditNativeAck(nativeAck,captured.nativeApplied)){captured.refuse();return;}
   captured.joined=true;
   if(!nativeContext(captured.client,a.issue().context())||!a.fresh().getAsBoolean()||active!=a)captured.refuse();
  }catch(Throwable observationFailure){captured.refuse();}
 }
 private static void recordRaw(Minecraft c,Object listener){
  if(c.player==null||c.level==null||c.player.level()!=c.level||c.getConnection()!=listener||c.player.isPassenger())return;
  player=new WeakReference<>(c.player);world=new WeakReference<>(c.level);connection=new WeakReference<>(listener);
  receipt=new Receipt(++serial,System.currentTimeMillis(),new Vec3(c.player.getX(),c.player.getY(),c.player.getZ()));
 }
 private static void failCaptured(Session session){if(session!=null&&active==session&&session.client().isSameThread())session.proof().revoke();}
 public static long baseline(Minecraft c){return c!=null&&c.isSameThread()?serial:-1;}
 public static Receipt current(Minecraft c){return c!=null&&c.isSameThread()&&c.player!=null&&c.level!=null&&c.getConnection()!=null&&c.player==player.get()&&c.level==world.get()&&c.getConnection()==connection.get()?receipt:null;}
 public static DeviceUseReceipt.Applied applied(DeviceUseReceipt.Issue issue){return same(issue)?active.proof().applied():null;}
 public static boolean rejected(DeviceUseReceipt.Issue issue){return same(issue)&&active.proof().revoked();}
 public static void retire(DeviceUseReceipt.Issue issue){if(same(issue)){active.proof().revoke();if(returnBinding!=null&&returnBinding.session==active){dev.veltraph.client.rewarp.mc.IssuedFarmAppliedLanding.retireDeviceReceive(returnBinding.receive);returnBinding=null;}active=null;}}
 private static boolean same(DeviceUseReceipt.Issue issue){return active!=null&&active.client().isSameThread()&&active.issue()==issue;}
 private static boolean nativeContext(Minecraft c,DeviceUseReceipt.Context ctx){return c.isSameThread()&&c.player==ctx.player()&&c.level==ctx.world()&&c.getConnection()==ctx.connection();}
}
