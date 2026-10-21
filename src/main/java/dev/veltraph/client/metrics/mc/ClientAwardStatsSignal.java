package dev.veltraph.client.metrics.mc;

import java.lang.ref.WeakReference;
import java.util.*;
import dev.veltraph.client.metrics.LocalSessionMetrics;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundAwardStatsPacket;
import net.minecraft.stats.Stats;

/** Bounded, read-only journal of actual native AwardStats ingress, enabled only by a local observer. */
public final class ClientAwardStatsSignal {
    public record Receipt(long serial,UUID generation,long receivedAt,Map<String,Long> blockTotals,
                          Map<LocalSessionMetrics.Counter,Long> totals) {
        public Receipt {blockTotals=Map.copyOf(blockTotals);totals=Map.copyOf(totals);}
    }
    private record Scope(Object player,Object world,Object listener) {
        boolean same(Scope other){return other!=null&&player==other.player&&world==other.world&&listener==other.listener;}
    }
    private static final Map<Object,Scope> observers=new WeakHashMap<>();
    private static WeakReference<Object> player=new WeakReference<>(null),world=new WeakReference<>(null),listener=new WeakReference<>(null);
    private static final ArrayDeque<Receipt> receipts=new ArrayDeque<>();
    private static long serial;private static UUID generation=UUID.randomUUID();
    private ClientAwardStatsSignal() {}
    public static void activate(Object owner,Object localPlayer,Object level,Object connection){
        var scope=new Scope(localPlayer,level,connection);observers.put(owner,scope);
        if(player.get()!=localPlayer||world.get()!=level||listener.get()!=connection){
            player=new WeakReference<>(localPlayer);world=new WeakReference<>(level);listener=new WeakReference<>(connection);
            generation=UUID.randomUUID();receipts.clear();
        }
    }
    public static void deactivate(Object owner){observers.remove(owner);}
    public static void capture(Minecraft client,ClientLevel level,Object connection,ClientboundAwardStatsPacket packet){
        if(!client.isSameThread()||client.player==null||client.level!=level||client.getConnection()!=connection)return;
        var current=new Scope(client.player,level,connection);
        if(observers.values().stream().noneMatch(current::same))return;
        if(player.get()!=current.player||world.get()!=level||listener.get()!=connection)return;
        var blocks=new HashMap<String,Long>();var totals=new EnumMap<LocalSessionMetrics.Counter,Long>(LocalSessionMetrics.Counter.class);
        // Read the packet's actual received cumulative values. Never manufacture absent keys from a cache.
        for(var entry:packet.stats().object2IntEntrySet()){
            var stat=entry.getKey();long value=entry.getIntValue();if(value<0)continue;
            if(stat.getType()==Stats.BLOCK_MINED){
                var block=(net.minecraft.world.level.block.Block)stat.getValue();
                blocks.put(BuiltInRegistries.BLOCK.getKey(block).toString(),value);
            }else if(stat.equals(Stats.CUSTOM.get(Stats.MOB_KILLS)))totals.put(LocalSessionMetrics.Counter.NATIVE_MOB_KILLS,value);
            else if(stat.equals(Stats.CUSTOM.get(Stats.FISH_CAUGHT)))totals.put(LocalSessionMetrics.Counter.NATIVE_FISH_CAUGHT,value);
        }
        receipts.addLast(new Receipt(++serial,generation,System.currentTimeMillis(),blocks,totals));
        while(receipts.size()>128)receipts.removeFirst();
    }
    public static long serial(Object localPlayer,Object level,Object connection){return scoped(localPlayer,level,connection)?serial:0;}
    private static boolean scoped(Object localPlayer,Object level,Object connection){return player.get()==localPlayer&&world.get()==level&&listener.get()==connection;}
    public static List<Receipt> read(Object localPlayer,Object level,Object connection,long after){
        if(!scoped(localPlayer,level,connection))return List.of();
        return receipts.stream().filter(r->r.serial()>after).toList();
    }
}
