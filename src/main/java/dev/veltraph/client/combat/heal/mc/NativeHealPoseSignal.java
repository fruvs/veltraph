package dev.veltraph.client.combat.heal.mc;

import java.lang.ref.WeakReference;
import dev.veltraph.client.combat.heal.HealSourcePosePacketContract;
import net.minecraft.client.Minecraft;

/** Heal-only receipt of original pre-apply position/rotation packet event; no global teleport mutation. */
public final class NativeHealPoseSignal {
    public record Receipt(long serial,HealSourcePosePacketContract.Kind kind,boolean current) {}
    private static long serial;
    private static HealSourcePosePacketContract.Kind kind=HealSourcePosePacketContract.Kind.OTHER;
    private static WeakReference<Object> player=new WeakReference<>(null),world=new WeakReference<>(null),connection=new WeakReference<>(null);
    private NativeHealPoseSignal() {}
    public static void observe(Minecraft client,Object listener,HealSourcePosePacketContract.Kind next){
        if(!client.isSameThread()||client.player==null||client.level==null||client.getConnection()!=listener
                ||client.player.level()!=client.level||next==HealSourcePosePacketContract.Kind.OTHER)return;
        player=new WeakReference<>(client.player);world=new WeakReference<>(client.level);connection=new WeakReference<>(listener);
        kind=next;serial++;
    }
    public static Receipt read(Minecraft client){return new Receipt(serial,kind,
            client.isSameThread()&&client.player!=null&&client.level!=null&&client.player==player.get()
            &&client.level==world.get()&&client.getConnection()==connection.get());}
}
