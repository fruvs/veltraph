package dev.veltraph.client.rewarp.mc;
import java.lang.ref.WeakReference;
import java.util.*;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import io.netty.channel.ChannelHandlerContext;
import dev.veltraph.client.mixin.IssuedFarmConnectionAccessor;
/** Actual native network ingress only; bounded weak identity stamps, no public origin factory. */
public final class IssuedFarmIncomingOrigin {
 private static final StackWalker STACK=StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);
 private static final ArrayDeque<Origin> ORIGINS=new ArrayDeque<>();
 private static long serial;
 private static final class Origin {final WeakReference<Object> packet,listener,connection,channel;final long serial;boolean taken;Origin(Object p,Object l,Object c,Object ch,long s){packet=new WeakReference<>(p);listener=new WeakReference<>(l);connection=new WeakReference<>(c);channel=new WeakReference<>(ch);serial=s;}}
 private static boolean nativeCaller(){return STACK.walk(f->f.filter(x->x.getDeclaringClass()!=IssuedFarmIncomingOrigin.class).findFirst().map(x->x.getDeclaringClass()==Connection.class).orElse(false));}
 public static synchronized void observe(Connection connection,ChannelHandlerContext context,Packet<?> packet,PacketListener listener){
  if(!(packet instanceof ClientboundPlayerPositionPacket)||!nativeCaller()||context==null||listener==null)return;
  var actual=((IssuedFarmConnectionAccessor)(Object)connection).veltraph$issuedFarmChannel();var channel=context.channel();
  if(channel==null||actual!=channel||!connection.isConnected())return;
  ORIGINS.removeIf(o->o.packet.get()==null||o.listener.get()==null||o.connection.get()==null||o.channel.get()==null||o.taken);
  if(ORIGINS.size()==32)ORIGINS.removeFirst();ORIGINS.addLast(new Origin(packet,listener,connection,channel,++serial));
 }
 /** Historical native ingress counter only. The actual native command body binds this operand privately. */
 static synchronized long nativeIngressSerial(){return serial;}
 static synchronized long take(Object packet,Object listener,Connection connection){
  var channel=((IssuedFarmConnectionAccessor)(Object)connection).veltraph$issuedFarmChannel();
  for(var o:ORIGINS)if(!o.taken&&o.packet.get()==packet&&o.listener.get()==listener&&o.connection.get()==connection&&o.channel.get()==channel){o.taken=true;return o.serial;}
  return -1;
 }
}
