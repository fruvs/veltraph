package dev.veltraph.client.observation.nativeinput;
import java.lang.ref.WeakReference;
import java.util.*;
import io.netty.channel.ChannelHandlerContext;
import net.minecraft.network.*;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.*;
/** Actual incoming channel opcode to actual queued processor linkage only. No public handler enrollment. */
public final class NativePlayerBirthIngress {
 private static final StackWalker STACK=safeWalker();private static StackWalker safeWalker(){try{return StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);}catch(RuntimeException|Error unavailable){return null;}}
 private static final ThreadLocal<PipelineInvocation> PIPELINE=new ThreadLocal<>();
 private static final ThreadLocal<BundleInvocation> BUNDLE=new ThreadLocal<>();
 private static final ArrayDeque<Stamp> STAMPS=new ArrayDeque<>();private static final ThreadLocal<Dispatch> ACTIVE=new ThreadLocal<>();
 public static final class PipelineInvocation {private final Object handler,context,message,channel,loop;private final NativePlayerBirthPipelineOrder order;private final NativePlayerBirthDecodedOutput.Output decoded;private volatile boolean invalid,normalReturned;private PipelineInvocation(Object h,Object c,Object m,Object channel,Object loop,NativePlayerBirthDecodedOutput.Output decoded){handler=h;context=c;message=m;this.channel=channel;this.loop=loop;this.decoded=decoded;order=new NativePlayerBirthPipelineOrder(h,c,m);if(!order.nativeEnter(h,c,m,true,true,true,true))invalid=true;}}
 private record Stamp(WeakReference<Object> packet,WeakReference<Object> listener,WeakReference<Object> connection,WeakReference<Object> channel,PipelineInvocation origin){}
 public static final class Dispatch {private final Object packet,listener,connection;private final PipelineInvocation origin;private final NativePlayerBirthDispatchOrder order;private BundleInvocation bundle;private volatile boolean invalid,normalReturned;private Dispatch(Object p,Object l,Object c,PipelineInvocation origin){packet=p;listener=l;connection=c;this.origin=origin;order=new NativePlayerBirthDispatchOrder(p);}}
 private static boolean caller(String name){if(STACK==null)return false;return STACK.walk(f->f.filter(x->x.getDeclaringClass()!=NativePlayerBirthIngress.class).findFirst().map(x->x.getDeclaringClass().getName().equals(name)&&x.getDeclaringClass().getClassLoader()==Connection.class.getClassLoader()).orElse(false));}
 public static PipelineInvocation pipelineEntered(Object handler,ChannelHandlerContext context,Object message){try{
  var old=PIPELINE.get();if(old!=null){old.invalid=true;old.order.refuse();return null;}
  if(STACK==null||!STACK.walk(f->f.filter(x->x.getDeclaringClass()!=NativePlayerBirthIngress.class).findFirst().map(x->x.getDeclaringClass().getName().equals("io.netty.channel.AbstractChannelHandlerContext")&&x.getDeclaringClass().getClassLoader()==io.netty.buffer.ByteBuf.class.getClassLoader()&&x.getMethodName().endsWith("veltraph$birthUpstream")).orElse(false)))return null;
  if(STACK==null||!(handler instanceof Connection)||!(message instanceof ClientboundLoginPacket||message instanceof ClientboundPlayerPositionPacket||message instanceof ClientboundBundlePacket)||context==null)return null;

  var raw=((dev.veltraph.client.mixin.NativePlayerBirthConnectionAccessor)handler).veltraph$birthChannel();var channel=context.channel();var loop=context.executor();boolean inLoop=loop.inEventLoop();
  if(!inLoop||channel==null||channel!=raw||raw!=((dev.veltraph.client.mixin.NativePlayerBirthConnectionAccessor)handler).veltraph$birthChannel())return null;var decoded=NativePlayerBirthDecodedOutput.consume(message,channel);if(decoded==null)return null;var token=new PipelineInvocation(handler,context,message,channel,loop,decoded);PIPELINE.set(token);return token;
 }catch(RuntimeException|Error unavailable){return null;}}
 public static void pipelineExited(PipelineInvocation token,boolean normal){try{if(token!=null){if(PIPELINE.get()!=token||!normal)token.invalid=true;else{token.order.returned(true);token.normalReturned=true;}if(PIPELINE.get()==token)PIPELINE.remove();}}catch(RuntimeException|Error unavailable){}}
 public static synchronized void ingress(Connection c,ChannelHandlerContext context,Packet<?> p,PacketListener l){
  var pipeline=PIPELINE.get();if(pipeline==null||pipeline.invalid||!NativePlayerBirthDecodedOutput.viable(pipeline.decoded)||pipeline.handler!=c||pipeline.context!=context||pipeline.message!=p||pipeline.channel!=context.channel()||pipeline.loop!=context.executor()||!caller("net.minecraft.network.Connection")||!(p instanceof ClientboundLoginPacket||p instanceof ClientboundPlayerPositionPacket||p instanceof ClientboundBundlePacket)||context==null)return;
  var channel=context.channel();boolean connected=c.isConnected();if(!connected||channel==null||channel!=((dev.veltraph.client.mixin.NativePlayerBirthConnectionAccessor)(Object)c).veltraph$birthChannel())return;
  if(pipeline.invalid||!pipeline.order.stamp(c,context,p))return;
  STAMPS.removeIf(s->s.packet.get()==null||s.listener.get()==null||s.connection.get()==null||s.channel.get()==null);if(STAMPS.size()==64)STAMPS.removeFirst();STAMPS.addLast(new Stamp(new WeakReference<>(p),new WeakReference<>(l),new WeakReference<>(c),new WeakReference<>(channel),pipeline));
 }
 public static synchronized Dispatch processEntered(Packet<?> packet,PacketListener listener){
  if(!caller("net.minecraft.network.PacketProcessor$ListenerAndPacket")||!(listener instanceof net.minecraft.client.multiplayer.ClientPacketListener l))return null;
  var old=ACTIVE.get();if(old!=null){old.invalid=true;return null;}var connection=l.getConnection();var channel=((dev.veltraph.client.mixin.NativePlayerBirthConnectionAccessor)(Object)connection).veltraph$birthChannel();
  var it=STAMPS.iterator();while(it.hasNext()){var s=it.next();if(s.packet.get()==packet&&s.listener.get()==listener&&s.connection.get()==connection&&s.channel.get()==channel&&!s.origin.invalid&&NativePlayerBirthDecodedOutput.viable(s.origin.decoded)&&s.origin.order.usableQueued()){it.remove();var d=new Dispatch(packet,listener,connection,s.origin);ACTIVE.set(d);return d;}}return null;
 }
 public static void processExited(Dispatch d,boolean normal){if(d==null)return;try{if(ACTIVE.get()!=d||!normal){d.invalid=true;d.order.refuse();}d.order.returned(d.packet,normal&&ACTIVE.get()==d);if(!d.invalid)d.normalReturned=true;}finally{if(ACTIVE.get()==d)ACTIVE.remove();}}
 static Dispatch retainedCurrent(Object packet,Object listener,Connection connection){var d=ACTIVE.get();return current(packet,listener,connection)?d:null;}
 static boolean normalOriginCurrent(Dispatch d){return d!=null&&!d.invalid&&d.normalReturned&&d.order.complete()&&(d.bundle==null||d.bundle.order.complete()&&normalOriginCurrent(d.bundle.parent))&&!d.origin.invalid&&NativePlayerBirthDecodedOutput.current(d.origin.decoded)&&d.origin.normalReturned&&d.origin.order.complete();}
 static boolean current(Object packet,Object listener,Connection connection){var d=ACTIVE.get();return d!=null&&!d.invalid&&!d.origin.invalid&&d.packet==packet&&d.listener==listener&&d.connection==connection;}

 public static final class BundleInvocation {
  private final Dispatch parent;
  private final NativePlayerBirthBundleOrder.Handler order;
  private BundleInvocation(Dispatch parent,NativePlayerBirthBundleOrder.Handler order){this.parent=parent;this.order=order;}
 }
 private static boolean bundleCaller(String suffix){return STACK!=null&&STACK.walk(f->f.filter(x->x.getDeclaringClass()!=NativePlayerBirthIngress.class).findFirst().map(x->x.getDeclaringClass().getName().equals("net.minecraft.client.multiplayer.ClientPacketListener")&&x.getDeclaringClass().getClassLoader()==net.minecraft.client.multiplayer.ClientPacketListener.class.getClassLoader()&&x.getMethodName().endsWith(suffix)).orElse(false));}
 public static BundleInvocation beginBundle(Object listener,Object packet){
  if(!bundleCaller("veltraph$birthWireBundleHandler"))return null;
  var parent=ACTIVE.get();if(parent==null||parent.packet!=packet||parent.listener!=listener||parent.invalid||!NativePlayerBirthDecodedOutput.viable(parent.origin.decoded))return null;
  var old=BUNDLE.get();if(old!=null){old.parent.invalid=true;old.parent.origin.invalid=true;old.order.finish(false);return null;}
  var order=NativePlayerBirthDecodedOutput.bundleHandler(parent.origin.decoded,packet);if(order==null)return null;
  var b=new BundleInvocation(parent,order);BUNDLE.set(b);return b;
 }
 public static void bundleIterator(Object originalList,Object iterator){
  if(!bundleCaller("veltraph$birthWireBundleIterator"))return;var b=BUNDLE.get();if(b!=null&&!b.order.iteratorReturned(originalList,iterator)){b.parent.invalid=true;b.parent.origin.invalid=true;}
 }
 public static void bundleNext(Object iterator,Object packet){
  if(!bundleCaller("veltraph$birthWireBundleNext"))return;var b=BUNDLE.get();if(b!=null&&!b.order.nextReturned(iterator,packet)){b.parent.invalid=true;b.parent.origin.invalid=true;}
 }
 public static Dispatch beginBundleMember(Object packet,Object listener){
  if(!bundleCaller("veltraph$birthWireBundleMember"))return null;
  var b=BUNDLE.get();if(b==null||ACTIVE.get()!=b.parent||b.parent.listener!=listener||b.parent.invalid||!b.order.viable())return null;
  if(!b.order.beginMember(packet)){b.parent.invalid=true;b.parent.origin.invalid=true;return null;}
  var d=new Dispatch(packet,listener,b.parent.connection,b.parent.origin);d.bundle=b;ACTIVE.set(d);return d;
 }
 public static void finishBundleMember(Dispatch d,boolean normal){
  if(!bundleCaller("veltraph$birthWireBundleMember")||d==null)return;
  try{if(d.bundle==null||ACTIVE.get()!=d||!normal){d.invalid=true;d.order.refuse();if(d.bundle!=null){d.bundle.parent.invalid=true;d.bundle.parent.origin.invalid=true;d.bundle.order.finish(false);}}
   d.order.returned(d.packet,normal&&ACTIVE.get()==d);if(!d.invalid)d.normalReturned=true;
  }finally{if(ACTIVE.get()==d){if(d.bundle==null)ACTIVE.remove();else ACTIVE.set(d.bundle.parent);}}
 }
 public static void finishBundle(BundleInvocation b,boolean normal){
  if(!bundleCaller("veltraph$birthWireBundleHandler")||b==null)return;
  try{if(BUNDLE.get()!=b||ACTIVE.get()!=b.parent){b.parent.invalid=true;b.parent.origin.invalid=true;normal=false;}b.order.finish(normal);}
  finally{if(BUNDLE.get()==b)BUNDLE.remove();}
 }
 static synchronized void retireChannel(Object originalChannel){
  STAMPS.removeIf(s->{if(s.channel.get()!=originalChannel)return false;s.origin.invalid=true;s.origin.order.refuse();return true;});
  var p=PIPELINE.get();if(p!=null&&p.channel==originalChannel){p.invalid=true;p.order.refuse();}
  var d=ACTIVE.get();if(d!=null&&d.origin.channel==originalChannel){d.invalid=true;d.origin.invalid=true;d.order.refuse();}
 }
}
