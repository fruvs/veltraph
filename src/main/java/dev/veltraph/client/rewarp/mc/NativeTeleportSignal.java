package dev.veltraph.client.rewarp.mc;

import dev.veltraph.client.world.Vec3;
import java.lang.ref.WeakReference;
import net.minecraft.client.Minecraft;

/** Read-only receipt after vanilla has applied and acknowledged a received player teleport. */
public final class NativeTeleportSignal {
    public record Receipt(long serial,Vec3 position) {}
    private static WeakReference<Object> player=new WeakReference<>(null),world=new WeakReference<>(null),connection=new WeakReference<>(null);
    private static long serial;
    private static Vec3 position;
    private NativeTeleportSignal() {}
    public static void observe(Minecraft client,Object listener) {
        if(client.player==null||client.level==null||client.player.level()!=client.level||client.getConnection()!=listener||client.player.isPassenger())return;
        var p=client.player;player=new WeakReference<>(p);world=new WeakReference<>(client.level);connection=new WeakReference<>(listener);
        position=new Vec3(p.getX(),p.getY(),p.getZ());serial++;
    }
    public static Receipt read(Minecraft client) {
        return client.player==player.get()&&client.level==world.get()&&client.getConnection()==connection.get()
                ?new Receipt(serial,position):new Receipt(serial,null);
    }
}
