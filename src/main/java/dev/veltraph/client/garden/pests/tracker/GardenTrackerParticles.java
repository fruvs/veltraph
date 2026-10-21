package dev.veltraph.client.garden.pests.tracker;

import dev.veltraph.client.world.Vec3;
import java.lang.ref.WeakReference;
import java.util.*;

/** Bounded read-only native particle receipts; a consumer must separately own a tracker request. */
public final class GardenTrackerParticles {
    public record Receipt(long serial,String kind,String options,Vec3 position,Vec3 velocity,long observed,boolean added){
        public Receipt(long serial,String kind,String options,Vec3 position,Vec3 velocity,long observed){this(serial,kind,options,position,velocity,observed,true);}}
    private static WeakReference<Object> player=new WeakReference<>(null),world=new WeakReference<>(null),connection=new WeakReference<>(null);
    private static final ArrayDeque<Receipt> receipts=new ArrayDeque<>();private static long serial;
    private GardenTrackerParticles(){}
    private static boolean finite(Vec3 p){return p!=null&&Double.isFinite(p.x())&&Double.isFinite(p.y())&&Double.isFinite(p.z());}
    private static boolean scope(Object p,Object w,Object c){return p!=null&&w!=null&&c!=null&&player.get()==p&&world.get()==w&&connection.get()==c;}
    public static void receive(Object p,Object w,Object c,String kind,String options,Vec3 position,Vec3 velocity,long now,boolean added){
        if(p==null||w==null||c==null||kind==null||kind.isBlank()||options==null||options.isBlank()||options.length()>4096||!finite(position)||!finite(velocity))return;
        if(!scope(p,w,c)){receipts.clear();player=new WeakReference<>(p);world=new WeakReference<>(w);connection=new WeakReference<>(c);}
        receipts.addLast(new Receipt(++serial,kind,options,position,velocity,now,added));while(receipts.size()>256)receipts.removeFirst();
    }
    public static List<Receipt> read(Object p,Object w,Object c,long now){if(!scope(p,w,c))return List.of();receipts.removeIf(r->now<r.observed()||now-r.observed()>10000);return List.copyOf(receipts);}
    public static long serial(){return serial;}
}
