package dev.veltraph.client.combat.slayer;

import dev.veltraph.client.inventory.NativeStackIdentity;
import java.lang.ref.WeakReference;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.item.ItemEntity;

/** Read-only native take-item observations. A receipt alone never establishes inventory delivery. */
public final class SlayerPickupSignal {
    public record Receipt(long serial,UUID player,UUID item,int entityId,int amount,
                          NativeStackIdentity.Snapshot content,long tick) {}
    private static WeakReference<Object> world=new WeakReference<>(null),connection=new WeakReference<>(null);
    private static final ArrayDeque<Receipt> receipts=new ArrayDeque<>();
    private static long serial;
    private SlayerPickupSignal() {}
    private static void scope(Object level,Object listener){if(world.get()!=level||connection.get()!=listener){world=new WeakReference<>(level);connection=new WeakReference<>(listener);receipts.clear();}}
    public static void capture(Minecraft client,ClientLevel level,Object listener,int itemId,int playerId,int amount){
        if(!client.isSameThread()||client.level!=level||client.getConnection()!=listener||client.player==null||amount<=0)return;
        if(level.getEntity(playerId)!=client.player||!(level.getEntity(itemId) instanceof ItemEntity item)||item.getItem().isEmpty())return;
        scope(level,listener);
        try {var prototype=item.getItem().copyWithCount(1);receipts.addLast(new Receipt(++serial,client.player.getUUID(),item.getUUID(),itemId,amount,
                NativeStackIdentity.capture(prototype,level.registryAccess()),client.player.tickCount));while(receipts.size()>128)receipts.removeFirst();}
        catch(RuntimeException unsupported){/* An unsupported full stack identity cannot authorize pickup. */}
    }
    public static long serial(Object level,Object listener){scope(level,listener);return serial;}
    public static List<Receipt> read(Object level,Object listener,UUID player,long after){
        if(level==null||listener==null||world.get()!=level||connection.get()!=listener)return List.of();
        return receipts.stream().filter(r->r.serial()>after&&r.player().equals(player)).toList();
    }
}
