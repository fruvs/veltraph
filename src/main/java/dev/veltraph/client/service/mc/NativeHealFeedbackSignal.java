package dev.veltraph.client.service.mc;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/** Read-only delivery of already received native healing feedback system chat. It has no sender or gameplay actions. */
public final class NativeHealFeedbackSignal {
    public enum Origin { SYSTEM_CHAT_PACKET }
    @FunctionalInterface public interface Receiver {void receive(Receipt receipt);}
    public record Receipt(WeakReference<Object> player,WeakReference<Object> world,WeakReference<Object> connection,
                          Component component,Origin origin,long serial,long receivedAt) {
        @Override public Component component(){return component.copy();}
        public boolean current(Minecraft client) {
            return client!=null&&client.isSameThread()&&client.player!=null&&client.level!=null
                    &&client.player==player.get()&&client.level==world.get()&&client.getConnection()==connection.get()
                    &&client.player.level()==client.level;
        }
    }
    /** Retain this subscription while observing; closing releases its only strong receiver reference. */
    public static final class Subscription implements AutoCloseable {
        private Receiver receiver;
        private final WeakReference<Receiver> slot;
        private Subscription(Receiver receiver){this.receiver=receiver;slot=new WeakReference<>(receiver);}
        @Override public void close(){synchronized(listeners){listeners.remove(slot);receiver=null;}}
    }
    private static final List<WeakReference<Receiver>> listeners=new ArrayList<>();
    private static long serial;
    private NativeHealFeedbackSignal() {}
    public static Subscription subscribe(Receiver receiver) {
        var subscription=new Subscription(Objects.requireNonNull(receiver));
        synchronized(listeners){listeners.removeIf(r->r.get()==null);listeners.add(subscription.slot);}return subscription;
    }
    /** Called at native system-chat handler tail with the actual packet listener identity. */
    public static boolean observeSystemChat(Minecraft client,Object listener,Component message,boolean overlay){
        return !overlay&&observe(client,listener,message,Origin.SYSTEM_CHAT_PACKET);
    }
    private static boolean observe(Minecraft client,Object listener,Component message,Origin origin) {
        if(client==null||!client.isSameThread()||message==null||listener==null||client.getConnection()!=listener
                ||client.player==null||client.level==null||client.player.level()!=client.level)return false;
        var receipt=new Receipt(new WeakReference<>(client.player),new WeakReference<>(client.level),new WeakReference<>(listener),
                message.copy(),origin,++serial,System.currentTimeMillis());
        List<WeakReference<Receiver>> targets;
        synchronized(listeners){listeners.removeIf(r->r.get()==null);targets=List.copyOf(listeners);}
        for(var target:targets){var receiver=target.get();if(receiver==null||!receipt.current(client))continue;
            try{receiver.receive(receipt);}catch(RuntimeException ignored){/* An observer must not break native packet handling. */}}
        return true;
    }
}
