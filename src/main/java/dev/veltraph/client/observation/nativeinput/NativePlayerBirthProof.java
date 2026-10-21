package dev.veltraph.client.observation.nativeinput;
import java.util.*;
import dev.veltraph.client.VeltraphBootstrap;
import dev.veltraph.client.service.mc.OwnedPoseHistoryCarrier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundLoginPacket;
/** Genuine private birth episode; final proof cannot be constructed from a current-pose read. */
public final class NativePlayerBirthProof {
 private static final ThreadLocal<Episode> ACTIVE=new ThreadLocal<>();private static Episode pending;
 private static final StackWalker STACK=safeWalker();
 private static StackWalker safeWalker(){try{return StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);}catch(RuntimeException|Error unavailable){return null;}}
 public static final class Episode {final Minecraft client;final ClientPacketListener listener;final Connection connection;final Object packet,options;final NativePlayerBirthIngress.Dispatch loginDispatch;NativePlayerBirthIngress.Dispatch positionDispatch;final VeltraphBootstrap.RootOriginalNativeInstallation installation;final NativePlayerBirthInstalledContext installedContext;final NativePlayerBirthOrder order;final NativePlayerBirthDispatchOrder initialization;final net.minecraft.client.KeyMapping[] keys;final NativeKeyBirthProof[] keyBirth;final Object[] keyHistory,bindingHistory;final String[] bindings;final boolean[] held;Object loadingScreen;LocalPlayer player;OwnedPoseHistoryCarrier carrier;Object beforePose,afterPose,positionBefore;LoginResetScope resetScope;boolean resetAttempted;final List<Object> yaw=new ArrayList<>(),pitch=new ArrayList<>();NativePlayerBirthAckOrder ack;Object positionPacket;io.netty.channel.Channel ackChannel;io.netty.channel.EventLoop ackLoop;boolean constructing,constructorReturned,loadingBody;Object loadingPlayer,loadingWorld;LoadingUpdateScope loadingUpdateScope;NativePlayerBirthLoadingOrder loadingOrder;volatile boolean invalid,handlerReturned,asyncWriteReturned,scheduledAck;Object scheduledTask;NativePlayerBirthTaskOrder taskOrder;boolean taskEntered;volatile boolean taskReturned;float retainedYaw,retainedPitch;private Episode(Minecraft c,ClientPacketListener l,Object packet){client=c;installedContext=new NativePlayerBirthInstalledContext(c);listener=l;connection=l.getConnection();this.packet=packet;loginDispatch=NativePlayerBirthIngress.retainedCurrent(packet,l,connection);if(loginDispatch==null)throw new IllegalStateException("Original native login dispatch missing");options=c.options;installation=VeltraphBootstrap.originalNativeInstallationFor(c);if(installation==null||!installation.currentNativeNoOwner(c))throw new IllegalStateException("Original no-owner installation unavailable");keys=new net.minecraft.client.KeyMapping[]{c.options.keyUp,c.options.keyDown,c.options.keyLeft,c.options.keyRight,c.options.keyJump,c.options.keyShift,c.options.keySprint,c.options.keyAttack,c.options.keyUse};keyBirth=new NativeKeyBirthProof[9];keyHistory=new Object[9];bindingHistory=new Object[9];bindings=new String[9];held=new boolean[9];for(int i=0;i<9;i++){keyBirth[i]=NativeKeyBirthProof.peek(keys[i]);if(keyBirth[i]==null)throw new IllegalStateException("Original constructor key proof unavailable");keyHistory[i]=keyBirth[i].keySnapshot(keys[i]);bindingHistory[i]=keyBirth[i].bindingSnapshot(keys[i]);bindings[i]=keyBirth[i].binding(keys[i]);held[i]=keyBirth[i].held(keys[i]);if(bindingHistory[i]==null)throw new IllegalStateException("Constructor binding proof unavailable");}order=new NativePlayerBirthOrder(packet,l,connection);initialization=new NativePlayerBirthDispatchOrder(packet);}}
 public static final class PoseAttempt {final Episode episode;final Object actor;final boolean yaw;final float value;Object token;boolean fieldReturned;private PoseAttempt(Episode e,Object actor,boolean yaw,float value){episode=e;this.actor=actor;this.yaw=yaw;this.value=value;}}
 private record AckLink(java.lang.ref.WeakReference<Object> packet,java.lang.ref.WeakReference<Episode> episode){}
 private static final java.util.ArrayDeque<AckLink> ACKS=new java.util.ArrayDeque<>();
 private static synchronized void retainAck(Object p,Episode e){ACKS.removeIf(x->x.packet.get()==null||x.episode.get()==null);if(ACKS.size()==32)ACKS.removeFirst();ACKS.addLast(new AckLink(new java.lang.ref.WeakReference<>(p),new java.lang.ref.WeakReference<>(e)));}
 private static synchronized Episode retainedAck(Object p){for(var x:ACKS)if(x.packet.get()==p)return x.episode.get();return null;}
 private static final ThreadLocal<Episode> ACK_TASK=new ThreadLocal<>();
 private static final ThreadLocal<PoseAttempt> POSE=new ThreadLocal<>();
 private static final ThreadLocal<LoginResetScope> LOGIN_RESET=new ThreadLocal<>();
 private final Episode episode;private final Object retainedPose;private final float yaw,pitch;
 private NativePlayerBirthProof(Episode e){episode=e;retainedPose=e.afterPose;yaw=e.retainedYaw;pitch=e.retainedPitch;}
 private static boolean namedCaller(Class<?> owner,String suffix){if(STACK==null)return false;return STACK.walk(f->f.filter(x->x.getDeclaringClass()!=NativePlayerBirthProof.class).findFirst().map(x->x.getDeclaringClass()==owner&&x.getMethodName().endsWith(suffix)).orElse(false));}
 private static boolean caller(Class<?> owner){if(STACK==null)return false;return STACK.walk(f->f.filter(x->x.getDeclaringClass()!=NativePlayerBirthProof.class).findFirst().map(x->x.getDeclaringClass()==owner).orElse(false));}
 public static Episode loginEntered(ClientPacketListener l,ClientboundLoginPacket packet){try{return loginEnteredNative(l,packet);}catch(RuntimeException|Error unavailable){return null;}}
 private static Episode loginEnteredNative(ClientPacketListener l,ClientboundLoginPacket packet){var c=Minecraft.getInstance();if(!caller(ClientPacketListener.class)||!c.isSameThread()||ACTIVE.get()!=null||!NativePlayerBirthIngress.current(packet,l,l.getConnection())||c.options==null)return null;var e=new Episode(c,l,packet);if(!e.order.dispatch(packet,l,l.getConnection(),true))return null;ACTIVE.set(e);return e;}

 public static final class InstallationAttempt {
  private final Episode episode;private final Minecraft client;private final Object value,token;private final boolean mode;
  private InstallationAttempt(Episode episode,Minecraft client,Object value,Object token,boolean mode){this.episode=episode;this.client=client;this.value=value;this.token=token;this.mode=mode;}
 }
 public static InstallationAttempt gameModeInstallationEntered(Minecraft client,Object gameMode){
  try{var e=ACTIVE.get();if(e==null||e.invalid||client!=e.client||!namedCaller(ClientPacketListener.class,"veltraph$birthAssignedGameMode")||!NativePlayerBirthIngress.current(e.packet,e.listener,e.connection))return null;
   var token=e.installedContext.beginMode(client,gameMode);if(token==null){e.invalid=true;return null;}return new InstallationAttempt(e,client,gameMode,token,true);
  }catch(RuntimeException|Error unavailable){var e=ACTIVE.get();if(e!=null)e.invalid=true;return null;}
 }
 public static InstallationAttempt worldInstallationEntered(Minecraft client,Object world){
  try{var e=ACTIVE.get();if(e==null||e.invalid||client!=e.client||!namedCaller(ClientPacketListener.class,"veltraph$birthInstalledWorld")||!NativePlayerBirthIngress.current(e.packet,e.listener,e.connection))return null;
   var token=e.installedContext.beginWorld(client,world,client.gameMode);if(token==null){e.invalid=true;return null;}return new InstallationAttempt(e,client,world,token,false);
  }catch(RuntimeException|Error unavailable){var e=ACTIVE.get();if(e!=null)e.invalid=true;return null;}
 }
 public static void installationReturned(InstallationAttempt attempt,boolean normal){
  try{if(attempt==null)return;var e=attempt.episode;
   if(ACTIVE.get()!=e||!namedCaller(ClientPacketListener.class,attempt.mode?"veltraph$birthAssignedGameMode":"veltraph$birthInstalledWorld")){e.invalid=true;e.installedContext.refuse();return;}
   if(attempt.mode)e.installedContext.modeReturned(attempt.token,attempt.client,attempt.client.gameMode,normal);
   else e.installedContext.worldReturned(attempt.token,attempt.client,attempt.client.level,attempt.client.gameMode,normal);
   if(!normal||attempt.mode&&e.installedContext.mode()!=attempt.value||!attempt.mode&&e.installedContext.world()!=attempt.value)e.invalid=true;
  }catch(RuntimeException|Error unavailable){if(attempt!=null){attempt.episode.invalid=true;attempt.episode.installedContext.refuse();}}
 }
 private static boolean installedContextCurrent(Episode e,boolean playerRequired){
  Object playerWorld=null;
  if(playerRequired&&e!=null&&(Object)e.player instanceof dev.veltraph.client.mixin.CollectionSceneEntityAccessor field)playerWorld=field.veltraph$sceneLevel();
  boolean current=e!=null&&!e.invalid&&e.installedContext.current(e.client,e.client.level,playerWorld,e.client.gameMode,playerRequired);
  if(e!=null&&!current)e.invalid=true;return current;
 }
 public static Object creationEntered(){try{var e=ACTIVE.get();if(e==null||e.invalid||e.constructing||!installedContextCurrent(e,false)||!caller(net.minecraft.client.multiplayer.MultiPlayerGameMode.class)||!NativePlayerBirthIngress.current(e.packet,e.listener,e.connection))return null;e.constructing=true;return e;}catch(RuntimeException|Error unavailable){return null;}}
 public static PoseAttempt superConstructorYawEntered(net.minecraft.world.entity.LivingEntity receiver,float value){try{var e=ACTIVE.get();if(e==null||e.invalid||!e.constructing||receiver.getClass()!=LocalPlayer.class||!caller(net.minecraft.world.entity.LivingEntity.class)||e.player!=null||!Float.isFinite(value)||POSE.get()!=null)return null;e.player=(LocalPlayer)receiver;if(!((Object)receiver instanceof OwnedPoseHistoryCarrier carrier)){e.invalid=true;return null;}e.carrier=carrier;e.beforePose=carrier.ownedPoseHistorySnapshot();if(e.beforePose==null){e.invalid=true;return null;}var attempt=new PoseAttempt(e,receiver,true,value);POSE.set(attempt);return attempt;}catch(RuntimeException|Error unavailable){var e=ACTIVE.get();if(e!=null)e.invalid=true;return null;}}
 public static void constructorEntered(LocalPlayer player){try{var e=ACTIVE.get();if(e==null||!e.constructing||!caller(LocalPlayer.class))return;if(e.player!=player){e.invalid=true;return;}if(!((Object)player instanceof OwnedPoseHistoryCarrier carrier)){e.invalid=true;return;}if(e.carrier!=carrier||e.beforePose==null)e.invalid=true;}catch(RuntimeException|Error unavailable){var e=ACTIVE.get();if(e!=null)e.invalid=true;}}
 public static void created(LocalPlayer player){try{createdObserved(player);}catch(RuntimeException|Error unavailable){var active=ACTIVE.get();if(active!=null)active.invalid=true;return;}}
 private static void createdObserved(LocalPlayer player){var e=ACTIVE.get();if(e==null)return;if(!caller(net.minecraft.client.multiplayer.MultiPlayerGameMode.class)||player==null||player.getClass()!=LocalPlayer.class||!(Object.class.cast(player) instanceof OwnedPoseHistoryCarrier carrier)||!e.order.created(player,true)){e.invalid=true;return;}if(!e.constructing||e.player!=player||e.carrier!=carrier||e.beforePose==null||!installedContextCurrent(e,true)){e.invalid=true;return;}e.constructing=false;e.constructorReturned=true;}
 /** Receiver identification only inside the actual authenticated NEW episode; no mutable-history reset or native action permission. */
 public static boolean constructorReceiverCurrent(net.minecraft.world.entity.Entity receiver){try{return constructorReceiverCurrentObserved(receiver);}catch(RuntimeException|Error unavailable){var active=ACTIVE.get();if(active!=null)active.invalid=true;return false;}}
 private static boolean constructorReceiverCurrentObserved(net.minecraft.world.entity.Entity receiver){var e=ACTIVE.get();return e!=null&&!e.invalid&&e.player==receiver&&e.client.isSameThread()&&e.client.options==e.options&&NativePlayerBirthIngress.current(e.packet,e.listener,e.connection);}
 public static void installed(Minecraft c,LocalPlayer player){try{installedObserved(c, player);}catch(RuntimeException|Error unavailable){var active=ACTIVE.get();if(active!=null)active.invalid=true;return;}}
 private static void installedObserved(Minecraft c,LocalPlayer player){var e=ACTIVE.get();if(e==null)return;if(!caller(ClientPacketListener.class)||c!=e.client||c.player!=player||player!=e.player||!e.order.installed(player,true)||!installedContextCurrent(e,true))e.invalid=true;}
 public static void added(Object level,Object player,boolean normal){try{addedObserved(level, player, normal);}catch(RuntimeException|Error unavailable){var active=ACTIVE.get();if(active!=null)active.invalid=true;return;}}
 private static void addedObserved(Object level,Object player,boolean normal){var e=ACTIVE.get();if(e==null)return;if(!caller(ClientPacketListener.class)||level!=e.installedContext.world()||!installedContextCurrent(e,true)||e.player!=player||!e.order.added(player,normal))e.invalid=true;}
 /** Actual handleLogin resetPos lifetime only; it neither captures pose history nor grants later writes. */
 public static final class LoginResetScope {
  private final Episode episode;private final LocalPlayer player;private final Object beforePose;private final OwnedPoseHistoryCarrier carrier;
  private final Object world,gameMode,options;private final NativePlayerBirthIngress.Dispatch dispatch;private final NativeBirthResetPoseOrder order;
  private PoseAttempt attempt;private boolean closed,pitchReturned;private Object returnedToken;
  private LoginResetScope(Episode e){episode=e;player=e.player;beforePose=e.beforePose;carrier=e.carrier;world=e.installedContext.world();gameMode=e.installedContext.mode();options=e.options;dispatch=e.loginDispatch;order=new NativeBirthResetPoseOrder(e,player);}
  private boolean sourceCurrent(){
   var e=episode;
   return !closed&&LOGIN_RESET.get()==this&&ACTIVE.get()==e&&e.resetScope==this&&!e.invalid
    &&e.constructorReturned&&!e.constructing&&!e.handlerReturned&&e.positionPacket==null&&e.ack==null
    &&e.player==player&&player.getClass()==LocalPlayer.class&&e.carrier==carrier&&e.beforePose==beforePose&&beforePose!=null
    &&e.client.isSameThread()&&e.client.player==player&&e.client.options==options&&e.options==options
    &&e.client.getConnection()==e.listener&&e.listener.getConnection()==e.connection&&e.installedContext.world()==world&&e.installedContext.mode()==gameMode
    &&e.order.preAddInstalled(player)&&installedContextCurrent(e,true)&&keyHistoryCurrent(e)
    &&e.installation.currentNativeNoOwner(e.client)&&NativePlayerBirthIngress.current(e.packet,e.listener,e.connection)
    &&NativePlayerBirthIngress.retainedCurrent(e.packet,e.listener,e.connection)==dispatch;
  }
  private void refuse(){episode.invalid=true;order.refuse();}
 }
 public static LoginResetScope loginResetEntered(ClientPacketListener listener,LocalPlayer originalPlayer){
  if(!namedCaller(ClientPacketListener.class,"veltraph$birthInitialReset"))return null;
  var e=ACTIVE.get();if(e==null)return null;
  if(e.resetAttempted){e.invalid=true;return null;}e.resetAttempted=true;
  LoginResetScope scope=null;
  try{
  if(e.invalid||e.listener!=listener||e.player!=originalPlayer||originalPlayer==null||e.carrier==null||e.beforePose==null
    ||!e.constructorReturned||e.constructing||e.handlerReturned||e.positionPacket!=null||e.ack!=null
    ||LOGIN_RESET.get()!=null||POSE.get()!=null||!e.order.preAddInstalled(originalPlayer)||e.pitch.size()!=0){e.invalid=true;return null;}
   scope=new LoginResetScope(e);e.resetScope=scope;LOGIN_RESET.set(scope);
   if(!scope.sourceCurrent()){scope.refuse();if(LOGIN_RESET.get()==scope)LOGIN_RESET.remove();return null;}return scope;
  }catch(RuntimeException|Error authorityError){e.invalid=true;if(scope!=null){scope.refuse();if(LOGIN_RESET.get()==scope)LOGIN_RESET.remove();}throw authorityError;}
 }
 public static PoseAttempt loginResetPitchEntered(LocalPlayer originalPlayer,float actualRequested){
  if(!namedCaller(LocalPlayer.class,"veltraph$birthResetPitch"))return null;
  var scope=LOGIN_RESET.get();if(scope==null)return null;
  try{
   if(!scope.order.pitchEntered(originalPlayer,Float.floatToIntBits(actualRequested))||originalPlayer!=scope.player
     ||Float.floatToIntBits(actualRequested)!=Float.floatToIntBits(0.0F)||POSE.get()!=null||scope.attempt!=null||!scope.sourceCurrent()){scope.refuse();return null;}
   var a=new PoseAttempt(scope.episode,originalPlayer,false,actualRequested);scope.attempt=a;POSE.set(a);return a;
  }catch(RuntimeException|Error authorityError){scope.refuse();throw authorityError;}
 }
 public static void loginResetPitchExited(PoseAttempt actual,boolean originalNormal){
  if(!namedCaller(LocalPlayer.class,"veltraph$birthResetPitch"))return;
  var scope=LOGIN_RESET.get();if(scope==null)return;
  try{
   if(actual==null||actual!=scope.attempt||POSE.get()!=actual||actual.episode!=scope.episode||actual.actor!=scope.player
     ||actual.yaw||Float.floatToIntBits(actual.value)!=Float.floatToIntBits(0.0F)||!scope.sourceCurrent()
     ||!scope.order.pitchReturned(actual.actor,actual.token,originalNormal&&actual.fieldReturned)){scope.refuse();return;}
   Object token=actual.token;poseExited(actual,originalNormal);
   if(scope.episode.invalid||POSE.get()!=null||scope.episode.pitch.size()!=1||scope.episode.pitch.getLast()!=token){scope.refuse();return;}
   scope.returnedToken=token;scope.pitchReturned=true;
  }catch(RuntimeException|Error authorityError){scope.refuse();throw authorityError;}
  finally{if(actual!=null&&actual==scope.attempt&&POSE.get()==actual)POSE.remove();}
 }
 public static void loginResetExited(LoginResetScope scope,boolean originalNormal){
  if(!namedCaller(ClientPacketListener.class,"veltraph$birthInitialReset")||scope==null)return;
  if(scope.closed||LOGIN_RESET.get()!=scope){scope.refuse();return;}
  try{
   if(!originalNormal||!scope.sourceCurrent()||!scope.pitchReturned||scope.returnedToken==null||POSE.get()!=null
      ||scope.episode.pitch.size()!=1||scope.episode.pitch.getLast()!=scope.returnedToken
      ||!scope.order.resetReturned(scope.episode,scope.player,scope.returnedToken,originalNormal))scope.refuse();
  }catch(RuntimeException|Error authorityError){scope.refuse();throw authorityError;}
  finally{scope.closed=true;if(LOGIN_RESET.get()==scope)LOGIN_RESET.remove();}
 }

 public static PoseAttempt poseEntered(Object player,boolean yaw,float value){try{return poseEnteredObserved(player, yaw, value);}catch(RuntimeException|Error unavailable){var active=ACTIVE.get();if(active!=null)active.invalid=true;return null;}}
 private static PoseAttempt poseEnteredObserved(Object player,boolean yaw,float value){var e=ACTIVE.get();if(e==null)return null;if(!caller(ClientPacketListener.class)||player!=e.player||!Float.isFinite(value)||POSE.get()!=null){e.invalid=true;return null;}var a=new PoseAttempt(e,player,yaw,value);POSE.set(a);return a;}
 private static boolean actualFieldCaller(boolean yaw){if(STACK==null)return false;return STACK.walk(f->f.filter(x->x.getDeclaringClass()!=NativePlayerBirthProof.class).findFirst().map(x->x.getDeclaringClass()==net.minecraft.world.entity.Entity.class&&x.getMethodName().endsWith(yaw?"veltraph$manualPoseYawField":"veltraph$manualPosePitchField")).orElse(false));}
 public static Object claimNativePoseSetter(net.minecraft.world.entity.Entity actor,boolean yaw,float value){try{return claimNativePoseSetterObserved(actor, yaw, value);}catch(RuntimeException|Error unavailable){var active=ACTIVE.get();if(active!=null)active.invalid=true;return null;}}
 private static Object claimNativePoseSetterObserved(net.minecraft.world.entity.Entity actor,boolean yaw,float value){var a=POSE.get();if(a==null)return null;if(!caller(net.minecraft.world.entity.Entity.class)||a.actor!=actor||a.yaw!=yaw||Float.floatToIntBits(a.value)!=Float.floatToIntBits(value)||a.token!=null){a.episode.invalid=true;return null;}a.token=new Object();return a.token;}
 public static void nativePoseFieldReturned(Object token,net.minecraft.world.entity.Entity actor,boolean yaw,float value,boolean normal){try{nativePoseFieldReturnedObserved(token, actor, yaw, value, normal);}catch(RuntimeException|Error unavailable){var active=ACTIVE.get();if(active!=null)active.invalid=true;return;}}
 private static void nativePoseFieldReturnedObserved(Object token,net.minecraft.world.entity.Entity actor,boolean yaw,float value,boolean normal){var a=POSE.get();if(a==null)return;if(!actualFieldCaller(yaw)||token==null||token!=a.token||actor!=a.actor||yaw!=a.yaw||Float.floatToIntBits(value)!=Float.floatToIntBits(a.value)||!normal||a.fieldReturned){a.episode.invalid=true;return;}a.fieldReturned=true;}
 public static void poseExited(PoseAttempt a,boolean normal){try{poseExitedObserved(a, normal);}catch(RuntimeException|Error unavailable){var active=ACTIVE.get();if(active!=null)active.invalid=true;return;}}
 private static void poseExitedObserved(PoseAttempt a,boolean normal){if(a==null)return;try{if(POSE.get()!=a||!normal||!a.fieldReturned||a.token==null)a.episode.invalid=true;else(a.yaw?a.episode.yaw:a.episode.pitch).add(a.token);}finally{if(POSE.get()==a)POSE.remove();}}
 public static void loginExited(Episode e,boolean normal){try{loginExitedObserved(e, normal);}catch(RuntimeException|Error unavailable){var active=ACTIVE.get();if(active!=null)active.invalid=true;return;}}
 private static void loginExitedObserved(Episode e,boolean normal){if(e==null)return;try{if(ACTIVE.get()!=e||e.invalid||!normal||!e.order.returned(true)||e.player==null||e.client.player!=e.player||e.client.getConnection()!=e.listener||!installedContextCurrent(e,true))return;e.afterPose=e.carrier.ownedPoseHistorySnapshot();if(!e.carrier.ownedPoseHistoryAdvanced(e.beforePose,e.afterPose,e.yaw,e.pitch)||!installedContextCurrent(e,true))return;pending=e;}finally{if(ACTIVE.get()==e)ACTIVE.remove();}}
 public static Episode positionEntered(ClientPacketListener listener,net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket packet){try{return positionEnteredObserved(listener, packet);}catch(RuntimeException|Error unavailable){var active=ACTIVE.get();if(active!=null)active.invalid=true;return null;}}
 private static Episode positionEnteredObserved(ClientPacketListener listener,net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket packet){var e=pending;var c=Minecraft.getInstance();if(e!=null&&e.positionPacket!=null){e.initialization.firstPosition(packet);e.invalid=true;return null;}if(e==null||e.invalid||!installedContextCurrent(e,true)||!keyHistoryCurrent(e)||!caller(ClientPacketListener.class)||!NativePlayerBirthIngress.current(packet,listener,listener.getConnection())||ACTIVE.get()!=null||c!=e.client||c.player!=e.player||c.getConnection()!=e.listener||e.options!=c.options||!e.carrier.ownedPoseHistoryCurrent(e.afterPose))return null;if(!e.initialization.firstPosition(packet)){e.invalid=true;return null;}e.positionDispatch=NativePlayerBirthIngress.retainedCurrent(packet,listener,listener.getConnection());if(e.positionDispatch==null)return null;ACTIVE.set(e);e.positionPacket=packet;e.ack=new NativePlayerBirthAckOrder(packet);e.positionBefore=e.afterPose;e.yaw.clear();e.pitch.clear();return e;}
 public static void positionExited(Episode e,boolean normal){try{positionExitedObserved(e, normal);}catch(RuntimeException|Error unavailable){var active=ACTIVE.get();if(active!=null)active.invalid=true;return;}}
 private static void positionExitedObserved(Episode e,boolean normal){if(e==null)return;try{if(ACTIVE.get()!=e||e.invalid||!normal||e.client.player!=e.player||e.client.getConnection()!=e.listener||e.ack==null||!installedContextCurrent(e,true))return;var after=e.carrier.ownedPoseHistorySnapshot();if(!e.carrier.ownedPoseHistoryAdvanced(e.positionBefore,after,e.yaw,e.pitch)){e.invalid=true;return;}e.afterPose=after;e.retainedYaw=e.player.getYRot();e.retainedPitch=e.player.getXRot();if(!installedContextCurrent(e,true))return;e.handlerReturned=true; // No positive handoff inside Packet.handle; actual processor/upstream closures finish later.
 }finally{if(ACTIVE.get()==e)ACTIVE.remove();}}
 public static void actualPositionReturned(Object player,boolean normal){try{actualPositionReturnedObserved(player, normal);}catch(RuntimeException|Error unavailable){var active=ACTIVE.get();if(active!=null)active.invalid=true;return;}}
 private static void actualPositionReturnedObserved(Object player,boolean normal){var e=ACTIVE.get();if(e==null||e.ack==null)return;if(!caller(ClientPacketListener.class)||player!=e.player||!e.ack.position(e.positionPacket,normal))e.invalid=true;}
 public static void ackRequested(Connection connection,Object packet){try{ackRequestedObserved(connection, packet);}catch(RuntimeException|Error unavailable){var active=ACTIVE.get();if(active!=null)active.invalid=true;return;}}
 private static void ackRequestedObserved(Connection connection,Object packet){var e=ACTIVE.get();if(e==null||e.ack==null)return;boolean exact=packet instanceof net.minecraft.network.protocol.game.ServerboundAcceptTeleportationPacket a&&e.positionPacket instanceof net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket p&&a.id()==p.id()&&Double.doubleToLongBits(a.x())==Double.doubleToLongBits(e.player.getX())&&Double.doubleToLongBits(a.y())==Double.doubleToLongBits(e.player.getY())&&Double.doubleToLongBits(a.z())==Double.doubleToLongBits(e.player.getZ());if(!caller(ClientPacketListener.class)||connection!=e.connection||!e.ack.ack(packet,exact))e.invalid=true;else retainAck(packet,e);}
 public static void ackBodyEntered(Connection connection,Object packet,boolean scheduled,io.netty.channel.EventLoop actualLoop){try{ackBodyEnteredObserved(connection, packet, scheduled, actualLoop);}catch(RuntimeException|Error unavailable){var active=ACTIVE.get();if(active!=null)active.invalid=true;return;}}
 private static void ackBodyEnteredObserved(Connection connection,Object packet,boolean scheduled,io.netty.channel.EventLoop actualLoop){var e=ACTIVE.get();if(e==null||e.ack==null)return;var channel=((dev.veltraph.client.mixin.NativePlayerBirthConnectionAccessor)(Object)connection).veltraph$birthChannel();var loop=channel==null?null:channel.eventLoop();if(!caller(Connection.class)||channel==null||loop==null||(scheduled&&actualLoop!=loop)||connection!=e.connection||!e.ack.body(packet,scheduled))e.invalid=true;else{e.ackChannel=channel;e.ackLoop=loop;e.scheduledAck=scheduled;}}
 public static void ackWriteReturned(Connection connection,io.netty.channel.Channel channel,Object packet,boolean normal){try{ackWriteReturnedObserved(connection, channel, packet, normal);}catch(RuntimeException|Error unavailable){try{var active=retainedAck(packet);if(active!=null)active.invalid=true;}catch(RuntimeException|Error ignored){}return;}}
 private static void ackWriteReturnedObserved(Connection connection,io.netty.channel.Channel channel,Object packet,boolean normal){var e=ACTIVE.get();if(e==null)e=ACK_TASK.get();if(e==null||e.ack==null)return;if(!caller(Connection.class)||channel!=e.ackChannel||channel!=((dev.veltraph.client.mixin.NativePlayerBirthConnectionAccessor)(Object)connection).veltraph$birthChannel()||connection!=e.connection||!normal)e.invalid=true;else if(e.scheduledAck&&ACK_TASK.get()==e&&e.taskEntered&&!e.taskReturned&&e.ackLoop!=null&&e.ackLoop.inEventLoop()){e.asyncWriteReturned=e.taskOrder!=null&&e.taskOrder.written(packet,connection,e.ackLoop,normal);}else if(!e.ack.write(packet,normal))e.invalid=true;}
 public static Runnable bindScheduledTask(Connection connection,Object packet,io.netty.channel.EventLoop loop,Runnable original){try{if(!namedCaller(net.minecraft.network.Connection.class,"veltraph$birthScheduled"))return original;var e=ACTIVE.get();if(e==null||e.invalid||e.connection!=connection||e.ackLoop!=loop||!e.scheduledAck||original==null||e.scheduledTask!=null)return original;e.scheduledTask=original;e.taskOrder=new NativePlayerBirthTaskOrder(packet,connection,loop,original);
  return ()->{boolean normal=false;try{boolean exact=e.ackLoop==loop&&loop.inEventLoop()&&e.connection==connection&&retainedAck(packet)==e&&e.scheduledTask==original&&!e.taskEntered&&ACK_TASK.get()==null&&e.taskOrder.enter(packet,connection,loop,original,true);if(exact){e.taskEntered=true;ACK_TASK.set(e);}else e.invalid=true;}catch(RuntimeException|Error unavailable){e.invalid=true;}
   try{original.run();normal=true;}finally{try{if(ACK_TASK.get()==e&&normal&&e.asyncWriteReturned&&e.taskOrder!=null&&e.taskOrder.returned(normal))e.taskReturned=true;else e.invalid=true;}catch(RuntimeException|Error unavailable){e.invalid=true;}finally{if(ACK_TASK.get()==e)ACK_TASK.remove();}}};
 }catch(RuntimeException|Error unavailable){var e=ACTIVE.get();if(e!=null)e.invalid=true;return original;}}
 public static void ackBodyReturned(Connection connection,Object packet,boolean normal){try{ackBodyReturnedObserved(connection, packet, normal);}catch(RuntimeException|Error unavailable){var active=ACTIVE.get();if(active!=null)active.invalid=true;return;}}
 private static void ackBodyReturnedObserved(Connection connection,Object packet,boolean normal){var e=ACTIVE.get();if(e==null||e.ack==null)return;var channel=((dev.veltraph.client.mixin.NativePlayerBirthConnectionAccessor)(Object)connection).veltraph$birthChannel();var loop=channel==null?null:channel.eventLoop();if(!caller(Connection.class)||channel!=e.ackChannel||loop!=e.ackLoop||connection!=e.connection||!e.ack.bodyReturned(packet,normal))e.invalid=true;}
 public static void ackReturned(Connection connection,Object packet,boolean normal){try{ackReturnedObserved(connection, packet, normal);}catch(RuntimeException|Error unavailable){var active=ACTIVE.get();if(active!=null)active.invalid=true;return;}}
 private static void ackReturnedObserved(Connection connection,Object packet,boolean normal){var e=ACTIVE.get();if(e==null||e.ack==null)return;if(!caller(ClientPacketListener.class)||connection!=e.connection||!e.ack.returned(packet,normal))e.invalid=true;}
 private static boolean keyHistoryCurrent(Episode e){if(!installedContextCurrent(e,true))return false;var o=e.client.options;if(o!=e.options)return false;var actual=new net.minecraft.client.KeyMapping[]{o.keyUp,o.keyDown,o.keyLeft,o.keyRight,o.keyJump,o.keyShift,o.keySprint,o.keyAttack,o.keyUse};for(int i=0;i<9;i++)if(actual[i]!=e.keys[i]||e.keyBirth[i]==null||!(actual[i] instanceof dev.veltraph.client.service.mc.NormalIdleKeyCarrier carrier)||!carrier.normalIdleKeyCurrent(e.keyHistory[i])||!NativeKeyBindingHistory.current(actual[i],e.bindingHistory[i])||actual[i].isDown()!=e.held[i]||!e.bindings[i].equals(actual[i].saveString()))return false;return installedContextCurrent(e,true);}
 public Object bindingSnapshot(net.minecraft.client.KeyMapping key){for(int i=0;i<9;i++)if(episode.keys[i]==key)return episode.bindingHistory[i];return null;}
 public Object keySnapshot(net.minecraft.client.KeyMapping key){for(int i=0;i<9;i++)if(episode.keys[i]==key)return episode.keyHistory[i];return null;}
 public String binding(net.minecraft.client.KeyMapping key){for(int i=0;i<9;i++)if(episode.keys[i]==key)return episode.bindings[i];return null;}
 public boolean held(net.minecraft.client.KeyMapping key){for(int i=0;i<9;i++)if(episode.keys[i]==key)return episode.held[i];throw new IllegalArgumentException("Foreign key");}
 public static void retireStale(Minecraft c){try{retireStaleObserved(c);}catch(RuntimeException|Error unavailable){var active=ACTIVE.get();if(active!=null)active.invalid=true;return;}}
 private static void retireStaleObserved(Minecraft c){var e=pending;if(e!=null&&(!installedContextCurrent(e,true)||c!=e.client||c.player!=e.player||c.getConnection()!=e.listener||c.options!=e.options||!keyHistoryCurrent(e)||e.carrier==null||e.afterPose==null||!e.carrier.ownedPoseHistoryCurrent(e.afterPose))){e.invalid=true;pending=null;}}
 private static boolean nativeMouseTurnCaller(Minecraft client,net.minecraft.world.entity.Entity player){return NativeMouseInvocationPhase.claimedPendingBirthTurn(client,player)!=null;}
 /** Only the actual authenticated startWaiting tracker/screen/update opcode chain may retain a prior real opening. */
 public static final class LoadingUpdateScope {
  private final Episode episode;private final net.minecraft.client.multiplayer.LevelLoadTracker tracker;private final LocalPlayer player;private final Object world,options,gameMode;
  private final NativePlayerBirthLoadingOrder loadingOrder;private final NativePlayerBirthIngress.Dispatch dispatch;private final NativeBirthLoadingUpdateOrder order;
  private net.minecraft.client.gui.screens.LevelLoadingScreen screen;private NativeGuiKeyResetLineage.RetainedOpeningReset receipt;private boolean closed,retained;
  private LoadingUpdateScope(Episode e,net.minecraft.client.multiplayer.LevelLoadTracker tracker){episode=e;this.tracker=tracker;player=e.player;world=e.loadingWorld;options=e.options;gameMode=e.installedContext.mode();loadingOrder=e.loadingOrder;dispatch=e.loginDispatch;order=new NativeBirthLoadingUpdateOrder(e,tracker,player,world);}
  private boolean sourceCurrent(){var e=episode;return !closed&&ACTIVE.get()==e&&e.loadingUpdateScope==this&&!e.invalid&&e.loadingBody&&e.loadingOrder==loadingOrder
   &&e.loadingPlayer==player&&e.player==player&&e.loadingWorld==world&&e.installedContext.world()==world&&e.options==options&&e.client.options==options
   &&e.constructorReturned&&!e.constructing&&!e.handlerReturned&&e.client.isSameThread()&&e.client.player==player&&e.client.getConnection()==e.listener&&e.listener.getConnection()==e.connection
   &&installedContextCurrent(e,true)&&keyHistoryCurrent(e)
   &&NativePlayerBirthIngress.current(e.packet,e.listener,e.connection)&&NativePlayerBirthIngress.retainedCurrent(e.packet,e.listener,e.connection)==dispatch
   &&e.installation.currentNativeNoOwner(e.client);}
  private void refuse(){closed=true;episode.invalid=true;order.refuse();}
 }
 static final class LoadingUpdateCompletion {
  private final LoadingUpdateScope scope;private boolean consumed;private LoadingUpdateCompletion(LoadingUpdateScope scope){this.scope=scope;}
  boolean consumeFor(Object originalEpisode){var e=scope.episode;if(consumed||e!=originalEpisode||scope.receipt==null
    ||!scope.receipt.ownedProjectionCurrent(scope,e.client,e.options,scope.screen,e.keys,e.keyBirth,e.keyHistory,e.bindingHistory,e.bindings)
    ||!scope.sourceCurrent()||!scope.order.consume(scope.screen,scope.tracker))return false;
   if(e.invalid||ACTIVE.get()!=e||e.loadingUpdateScope!=scope||scope.closed||!e.loadingBody||e.loadingOrder!=scope.loadingOrder||e.loadingScreen!=null
     ||e.player!=scope.player||e.loadingPlayer!=scope.player||e.loadingWorld!=scope.world||e.options!=scope.options||e.handlerReturned
     ||e.client.player!=scope.player||e.client.level!=scope.world||e.client.gameMode!=scope.gameMode||e.client.options!=scope.options
     ||!scope.receipt.finalRawCurrent(scope,e.keys,e.keyBirth,e.keyHistory,e.bindingHistory))return false;consumed=true;return true;}
  Object screenIdentity(){return scope.screen;}
 }
 public static LoadingUpdateScope loadingTrackerEntered(net.minecraft.client.multiplayer.LevelLoadTracker tracker,LocalPlayer originalPlayer,Object originalWorld){
  if(!namedCaller(ClientPacketListener.class,"veltraph$birthLoadingTrackerStart"))return null;var e=ACTIVE.get();if(e==null)return null;
  try{if(tracker==null||e.loadingUpdateScope!=null||e.loadingOrder==null||!e.loadingBody||e.player!=originalPlayer||e.loadingWorld!=originalWorld){e.invalid=true;return null;}
   var scope=new LoadingUpdateScope(e,tracker);e.loadingUpdateScope=scope;if(!scope.sourceCurrent()){scope.refuse();return null;}return scope;
  }catch(RuntimeException|Error sourceError){e.invalid=true;throw sourceError;}
 }
 public static void loadingTrackerExited(LoadingUpdateScope scope,boolean originalNormal){
  if(!namedCaller(ClientPacketListener.class,"veltraph$birthLoadingTrackerStart")||scope==null)return;
  try{if(!scope.sourceCurrent()||!scope.order.startReturned(scope.tracker,scope.player,scope.world,originalNormal))scope.refuse();}
  catch(RuntimeException|Error sourceError){scope.refuse();throw sourceError;}
 }
 public static void loadingOriginalScreenReturned(net.minecraft.client.gui.screens.Screen originalScreen){
  if(!namedCaller(ClientPacketListener.class,"veltraph$birthLoadingOriginalScreen"))return;var e=ACTIVE.get();var scope=e==null?null:e.loadingUpdateScope;if(scope==null)return;
  try{if(!(originalScreen instanceof net.minecraft.client.gui.screens.LevelLoadingScreen loading)){scope.closed=true;return;}
   if(!scope.sourceCurrent()||!scope.order.screenReturned(loading)){scope.refuse();return;}scope.screen=loading;
  }catch(RuntimeException|Error sourceError){scope.refuse();throw sourceError;}
 }
 public static LoadingUpdateScope loadingUpdateEntered(net.minecraft.client.gui.screens.LevelLoadingScreen originalScreen,net.minecraft.client.multiplayer.LevelLoadTracker tracker,net.minecraft.client.gui.screens.LevelLoadingScreen.Reason originalReason){
  if(!namedCaller(ClientPacketListener.class,"veltraph$birthLoadingOriginalUpdate"))return null;var e=ACTIVE.get();var scope=e==null?null:e.loadingUpdateScope;if(scope==null)return null;
  try{if(!scope.sourceCurrent()||scope.screen!=originalScreen||e.client.gui.screen()!=originalScreen||!scope.order.updateEntered(originalScreen,tracker,originalReason)){scope.refuse();return null;}
   scope.receipt=NativeGuiKeyResetLineage.claimRetainedOpening(scope,e.client,e.options,originalScreen,e.keys,e.keyBirth,e.keyHistory,e.bindingHistory,e.bindings);
   if(scope.receipt==null){scope.refuse();return null;}return scope;
  }catch(RuntimeException|Error sourceError){scope.refuse();throw sourceError;}
 }
 public static void loadingUpdateExited(LoadingUpdateScope scope,boolean originalNormal){
  if(!namedCaller(ClientPacketListener.class,"veltraph$birthLoadingOriginalUpdate")||scope==null)return;var e=scope.episode;
  try{if(!scope.sourceCurrent()||e.client.gui.screen()!=scope.screen||!scope.order.updateReturned(scope.screen,scope.tracker,originalNormal)||scope.receipt==null){scope.refuse();return;}
   var completion=new LoadingUpdateCompletion(scope);if(!scope.loadingOrder.retainOpening(completion)){scope.refuse();return;}e.loadingScreen=scope.screen;scope.retained=true;
  }catch(RuntimeException|Error sourceError){scope.refuse();throw sourceError;}
  finally{scope.closed=true;}
 }
 public static Object loadingEntered(Object player,Object world){try{var e=ACTIVE.get();if(e==null||e.invalid||e.loadingBody||player!=e.player||world!=e.installedContext.world()||!installedContextCurrent(e,true)||!caller(ClientPacketListener.class)||!NativePlayerBirthIngress.current(e.packet,e.listener,e.connection))return null;e.loadingBody=true;e.loadingOrder=new NativePlayerBirthLoadingOrder(e);e.loadingPlayer=player;e.loadingWorld=world;return e;}catch(RuntimeException|Error unavailable){return null;}}
 public static void loadingExited(Object token,boolean normal){try{var e=ACTIVE.get();if(token!=null&&token==e){if(!normal||!keyHistoryCurrent(e))e.invalid=true;e.loadingBody=false;}}catch(RuntimeException|Error unavailable){var e=ACTIVE.get();if(e!=null)e.invalid=true;}}
 public static void actualLoadingResetReturned(NativeGuiKeyResetLineage.NormalReset reset){try{var e=ACTIVE.get();if(e==null||e.invalid||reset==null||e.player==null||!e.loadingBody||e.loadingPlayer!=e.player||e.loadingWorld!=e.installedContext.world()||!installedContextCurrent(e,true)||!caller(McNativeManualInputLineage.class)||!NativePlayerBirthIngress.current(e.packet,e.listener,e.connection))return;
  e.loadingScreen=reset.nextScreen();var next=new NativeKeyBirthProof[9];for(int i=0;i<9;i++){next[i]=NativeKeyBirthProof.nativeGuiResetSuccessor(e.keyBirth[i],e.keys[i],reset);if(next[i]==null)return;}
  if(e.loadingOrder==null||!e.loadingOrder.opened(e,reset.nextScreen(),true,true)){e.invalid=true;return;}
  for(int i=0;i<9;i++){e.keyBirth[i]=next[i];e.keyHistory[i]=next[i].keySnapshot(e.keys[i]);if(e.keyHistory[i]==null||next[i].bindingSnapshot(e.keys[i])!=e.bindingHistory[i]){e.invalid=true;return;}}
 }catch(RuntimeException|Error unavailable){var e=ACTIVE.get();if(e!=null)e.invalid=true;}}
 public static void actualLoadingTransitionReturned(NativeGuiKeyResetLineage.NormalTransition transition){try{var e=pending;if(e==null||e.invalid||!installedContextCurrent(e,true)||e.loadingWorld!=e.installedContext.world()||transition==null||!caller(McNativeManualInputLineage.class)||e.loadingScreen==null||transition.oldScreen()!=e.loadingScreen||transition.nextScreen()!=null||!transition.matchesNative(e.client,e.options,e.player,e.installedContext.world(),e.listener,e.installation.managerIdentity()))return;
  var after=new Object[9];var successors=new NativeKeyBirthProof[9];for(int i=0;i<9;i++){if(!transition.advances(e.keys[i],e.keyHistory[i])||!e.bindings[i].equals(transition.binding(e.keys[i]))||transition.held(e.keys[i])!=e.held[i]||!NativeKeyBindingHistory.current(e.keys[i],e.bindingHistory[i]))return;successors[i]=NativeKeyBirthProof.nativeGuiCloseSuccessor(e.keyBirth[i],e.keys[i],transition);if(successors[i]==null)return;after[i]=successors[i].keySnapshot(e.keys[i]);if(after[i]==null||successors[i].bindingSnapshot(e.keys[i])!=e.bindingHistory[i])return;}
  if(e.loadingOrder==null||!e.loadingOrder.close(transition.oldScreen(),transition.nextScreen(),true,true,true)){e.invalid=true;return;}
  for(int i=0;i<9;i++){e.keyBirth[i]=successors[i];e.keyHistory[i]=after[i];}e.loadingScreen=null;if(!keyHistoryCurrent(e)||!e.installation.currentNativeNoOwner(e.client))e.invalid=true;
 }catch(RuntimeException|Error unavailable){var e=pending;if(e!=null)e.invalid=true;}}
 public static final class PendingBirthPoseObservation {
  private final Episode episode;private final Object before;private final float yaw,pitch;private boolean consumed;
  private PendingBirthPoseObservation(Episode e){episode=e;before=e.afterPose;yaw=e.retainedYaw;pitch=e.retainedPitch;}
  public Minecraft client(){return episode.client;}public OwnedPoseHistoryCarrier poseCarrier(){return episode.carrier;}public Object beforePoseSnapshot(){return before;}public float yaw(){return yaw;}public float pitch(){return pitch;}
  public Object keySnapshot(net.minecraft.client.KeyMapping key){for(int i=0;i<9;i++)if(episode.keys[i]==key)return episode.keyHistory[i];return null;}
  public Object bindingSnapshot(net.minecraft.client.KeyMapping key){for(int i=0;i<9;i++)if(episode.keys[i]==key)return episode.bindingHistory[i];return null;}
  public String binding(net.minecraft.client.KeyMapping key){for(int i=0;i<9;i++)if(episode.keys[i]==key)return episode.bindings[i];return null;}
  public boolean held(net.minecraft.client.KeyMapping key){for(int i=0;i<9;i++)if(episode.keys[i]==key)return episode.held[i];throw new IllegalArgumentException("Foreign key");}
  public boolean currentBefore(Minecraft c){return !consumed&&pending==episode&&!episode.invalid&&episode.handlerReturned&&c==episode.client&&c.isSameThread()&&c.player==episode.player&&c.level==episode.installedContext.world()&&c.getConnection()==episode.listener&&keyHistoryCurrent(episode)&&episode.carrier.ownedPoseHistoryCurrent(before)&&Float.floatToIntBits(c.player.getYRot())==Float.floatToIntBits(yaw)&&Float.floatToIntBits(c.player.getXRot())==Float.floatToIntBits(pitch)&&episode.installation.currentNativeNoOwner(c)&&installedContextCurrent(episode,true);}
 }
 public static PendingBirthPoseObservation beginPendingStationary(Minecraft c,net.minecraft.world.entity.Entity player){try{if(!(namedCaller(McNativeManualInputLineage.class,"beginNativeTurn")&&nativeMouseTurnCaller(c,player))||c==null||!c.isSameThread())return null;var e=pending;if(e==null||e.invalid||!e.handlerReturned||e.player!=player||e.ack==null||!keyHistoryCurrent(e)||!e.carrier.ownedPoseHistoryCurrent(e.afterPose)||!e.installation.currentNativeNoOwner(c))return null;var ticket=new PendingBirthPoseObservation(e);return ticket.currentBefore(c)?ticket:null;}catch(RuntimeException|Error unavailable){return null;}}
 public static void finishPendingStationary(PendingBirthPoseObservation ticket,McNativeManualInputLineage.PendingStationaryReturn actual){try{if(ticket==null||actual==null||ticket.consumed||pending!=ticket.episode||!actual.matchesBefore(ticket)||!actual.advancedFrom(ticket.poseCarrier(),ticket.beforePoseSnapshot())||!actual.current(ticket.client())||!keyHistoryCurrent(ticket.episode)||!ticket.episode.installation.currentNativeNoOwner(ticket.client()))return;var after=actual.afterPoseSnapshot(ticket.poseCarrier());if(after==null||!ticket.poseCarrier().ownedPoseHistoryCurrent(after)||!installedContextCurrent(ticket.episode,true))return;ticket.consumed=true;ticket.episode.afterPose=after;ticket.episode.retainedYaw=actual.yaw();ticket.episode.retainedPitch=actual.pitch();}catch(RuntimeException|Error unavailable){if(ticket!=null)ticket.episode.invalid=true;}}
 public static void finalizeRetainedInitialization(Minecraft c){try{if(!namedCaller(Minecraft.class,"veltraph$birthFinalizeRetained")||!c.isSameThread())return;var e=pending;if(e==null||!e.handlerReturned||e.invalid||!installedContextCurrent(e,true)||e.loadingWorld!=e.installedContext.world()||e.loadingOrder==null||!e.loadingOrder.readyToFinalize(e.loadingBody,e.loadingScreen)||!NativePlayerBirthIngress.normalOriginCurrent(e.loginDispatch)||!NativePlayerBirthIngress.normalOriginCurrent(e.positionDispatch)||e.ack==null||!e.ack.complete()||e.scheduledAck&&(!e.asyncWriteReturned||!e.taskReturned))return;if(c!=e.client||c.player!=e.player||c.getConnection()!=e.listener||!keyHistoryCurrent(e)||!e.carrier.ownedPoseHistoryCurrent(e.afterPose)||Float.floatToIntBits(c.player.getYRot())!=Float.floatToIntBits(e.retainedYaw)||Float.floatToIntBits(c.player.getXRot())!=Float.floatToIntBits(e.retainedPitch)||!e.installation.currentNativeNoOwner(c)||!installedContextCurrent(e,true))return;if(e.order.positionInitialized(e.player,true,true)&&e.order.finish(true)&&e.order.consume()){pending=null;McNativeManualInputLineage.acceptNativeBirth(new NativePlayerBirthProof(e));}}catch(RuntimeException|Error unavailable){var e=pending;if(e!=null)e.invalid=true;}}
 public boolean normalInitializationCurrent(Minecraft c){return episode.handlerReturned&&!episode.invalid&&installedContextCurrent(episode,true)&&episode.loadingWorld==episode.installedContext.world()&&episode.loadingOrder!=null&&episode.loadingOrder.readyToFinalize(episode.loadingBody,episode.loadingScreen)&&NativePlayerBirthIngress.normalOriginCurrent(episode.loginDispatch)&&NativePlayerBirthIngress.normalOriginCurrent(episode.positionDispatch)&&episode.ack!=null&&episode.ack.complete()&&(!episode.scheduledAck||episode.asyncWriteReturned&&episode.taskReturned)&&current(c);}
 public Minecraft client(){return episode.client;}public Object player(){return episode.player;}public Object world(){return episode.installedContext.world();}public Object listener(){return episode.listener;}public Object nativeConnection(){return episode.connection;}public Object options(){return episode.options;}public OwnedPoseHistoryCarrier poseCarrier(){return episode.carrier;}public Object beforePoseSnapshot(){return episode.beforePose;}public Object poseSnapshot(){return retainedPose;}public float yaw(){return yaw;}public float pitch(){return pitch;}
 public boolean current(Minecraft c){return installedContextCurrent(episode,true)&&c==episode.client&&c.isSameThread()&&c.player==episode.player&&c.level==episode.installedContext.world()&&c.getConnection()==episode.listener&&episode.listener.getConnection()==episode.connection&&c.options==episode.options&&keyHistoryCurrent(episode)&&episode.installation.currentNativeNoOwner(c)&&episode.carrier.ownedPoseHistoryCurrent(retainedPose)&&Float.floatToIntBits(c.player.getYRot())==Float.floatToIntBits(yaw)&&Float.floatToIntBits(c.player.getXRot())==Float.floatToIntBits(pitch)&&installedContextCurrent(episode,true);}
}
