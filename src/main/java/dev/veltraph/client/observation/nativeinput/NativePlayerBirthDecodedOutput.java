package dev.veltraph.client.observation.nativeinput;
import io.netty.channel.ChannelHandlerContext;
import io.netty.buffer.ByteBuf;
import java.util.*;
import java.lang.ref.WeakReference;
import net.minecraft.network.PacketDecoder;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.*;
/** Exact normally decoded eligible Packet output; source record alone never grants birth authority. */
public final class NativePlayerBirthDecodedOutput {
 private static final ThreadLocal<Decode> ACTIVE=new ThreadLocal<>();private static final ArrayDeque<Output> OUTPUTS=new ArrayDeque<>();
 private static final StackWalker STACK=walker();private static StackWalker walker(){try{return StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);}catch(RuntimeException|Error unavailable){return null;}}
 public static final class Decode {private final Object decoder,context,buffer,list,channel;private final NativePlayerBirthDecodeOrder order;private final NativePlayerBirthWireSource.Certificate wire;private Object packet;private boolean decoded,added;private volatile boolean normal,invalid;private Decode(Object d,Object c,Object b,Object l,Object ch,NativePlayerBirthWireSource.Certificate wire){this.wire=wire;decoder=d;context=c;buffer=b;list=l;channel=ch;order=new NativePlayerBirthDecodeOrder(d,b,l);}}
 public static final class Output {private final WeakReference<Object> packet,channel;private final Decode source;private final NativePlayerBirthBundleOrder bundle;private final NativePlayerBirthWireSource.Certificate bundleWire;private NativePlayerBirthWireSource.Certificate delivery;private boolean consumed;private Output(Decode d){packet=new WeakReference<>(d.packet);channel=new WeakReference<>(d.channel);source=d;bundle=null;bundleWire=null;}private Output(Object p,Object c,NativePlayerBirthBundleOrder b,NativePlayerBirthWireSource.Certificate w){packet=new WeakReference<>(p);channel=new WeakReference<>(c);source=null;bundle=b;bundleWire=w;}}
 private static boolean caller(String suffix){return STACK!=null&&STACK.walk(f->f.filter(x->x.getDeclaringClass()!=NativePlayerBirthDecodedOutput.class).findFirst().map(x->x.getDeclaringClass()==PacketDecoder.class&&x.getMethodName().endsWith(suffix)).orElse(false));}
 public static Decode begin(Object decoder,ChannelHandlerContext context,ByteBuf buffer,List<?> list){try{var old=ACTIVE.get();if(old!=null){old.invalid=true;return null;}if(!caller("veltraph$birthDecode")||context==null||buffer==null||list==null)return null;var channel=context.channel();boolean loop=context.executor().inEventLoop();if(channel==null||!loop)return null;var wire=NativePlayerBirthWireSource.decodeSource(decoder,context,buffer,list);if(wire==null)return null;var d=new Decode(decoder,context,buffer,list,channel,wire);ACTIVE.set(d);return d;}catch(RuntimeException|Error unavailable){return null;}}
 public static void decoded(Object result,boolean normal){try{var d=ACTIVE.get();if(d==null)return;if(!caller("veltraph$birthCodecOutput")||!normal||d.decoded||!(result instanceof Packet<?>)){d.invalid=true;return;}if(!d.order.decoded(d.decoder,d.buffer,result,true,normal)){d.invalid=true;return;}d.packet=result;d.decoded=true;}catch(RuntimeException|Error unavailable){var d=ACTIVE.get();if(d!=null)d.invalid=true;}}
 public static void added(Object list,Object packet,boolean normal){try{var d=ACTIVE.get();if(d==null)return;if(!caller("veltraph$birthDecodedListAdd")||!normal||!d.decoded||d.added||d.list!=list||d.packet!=packet){d.invalid=true;return;}if(!d.order.added(list,packet,normal)){d.invalid=true;return;}d.added=true;}catch(RuntimeException|Error unavailable){var d=ACTIVE.get();if(d!=null)d.invalid=true;}}
 public static synchronized void finish(Decode d,boolean normal){try{if(d==null)return;if(ACTIVE.get()!=d||!normal||!d.decoded||!d.added)d.invalid=true;else{d.order.finish(normal);d.normal=d.order.current();}if(!d.invalid&&d.normal&&(d.packet instanceof ClientboundLoginPacket||d.packet instanceof ClientboundPlayerPositionPacket)){OUTPUTS.removeIf(o->o.packet.get()==null||o.channel.get()==null||o.consumed);if(OUTPUTS.size()==64)OUTPUTS.removeFirst();OUTPUTS.addLast(new Output(d));}}catch(RuntimeException|Error unavailable){if(d!=null)d.invalid=true;}finally{if(ACTIVE.get()==d)ACTIVE.remove();}}
 static synchronized void recordBundle(Object packet,Object channel,NativePlayerBirthBundleOrder bundle,NativePlayerBirthWireSource.Certificate wire){
  if(STACK==null||!STACK.walk(f->f.filter(x->x.getDeclaringClass()!=NativePlayerBirthDecodedOutput.class).findFirst().map(x->x.getDeclaringClass()==NativePlayerBirthBundleSource.class&&x.getMethodName().equals("outputAdded")).orElse(false)))return;
  if(packet==null||channel==null||bundle==null||wire==null||!wire.viable())return;
  OUTPUTS.removeIf(o->o.packet.get()==null||o.channel.get()==null||o.consumed);
  if(OUTPUTS.size()==64)OUTPUTS.removeFirst();OUTPUTS.addLast(new Output(packet,channel,bundle,wire));
 }
 private static boolean sourceViable(Output o){return o!=null&&(o.bundle!=null?o.bundleWire!=null&&o.bundleWire.viable():o.source!=null&&o.source.normal&&!o.source.invalid&&o.source.order.current()&&o.source.wire.viable());}
 static synchronized Output consume(Object packet,Object channel){
  for(var o:OUTPUTS)if(!o.consumed&&o.packet.get()==packet&&o.channel.get()==channel&&sourceViable(o)){
   if(o.source!=null&&!o.source.order.take(packet))return null;
   var delivery=NativePlayerBirthWireSource.finalDelivery(packet,channel);if(delivery==null||!delivery.viable())return null;
   o.delivery=delivery;o.consumed=true;return o;
  }return null;
 }
 static NativePlayerBirthBundleOrder.Handler bundleHandler(Output o,Object packet){return o!=null&&o.bundle!=null&&o.packet.get()==packet&&sourceViable(o)?o.bundle.handler(packet):null;}
 static boolean viable(Output o){return sourceViable(o)&&o.delivery!=null&&o.delivery.viable();}
 static boolean current(Output o){return viable(o)&&(o.bundle!=null?o.bundleWire.complete():o.source.wire.complete())&&o.delivery.complete();}
 static synchronized void retireChannel(Object originalChannel){
  OUTPUTS.removeIf(o->{if(o.channel.get()!=originalChannel)return false;if(o.source!=null)o.source.invalid=true;if(o.bundle!=null)o.bundle.refuse();return true;});
  var d=ACTIVE.get();if(d!=null&&d.channel==originalChannel)d.invalid=true;
 }
}
