package dev.veltraph.client.inventory.selling;

import dev.veltraph.client.game.GameArea;
import dev.veltraph.client.world.Vec3;
import java.util.*;

/** Finite source warp capability. Root alone may commit an admitted native world replacement. */
public final class GeorgeWarpPermit {
    public enum State { WAITING, OBSERVED, COMMITTED, CANCELLED }
    public record Destination(GameArea area,String dimension,Vec3 position,double radius) {
        public Destination {if(area!=GameArea.HUB&&area!=GameArea.GARDEN||dimension==null||!dimension.matches("[a-z0-9_.-]+:[a-z0-9_/.-]+")||!finite(position)||!Double.isFinite(radius)||radius<=0||radius>1)throw new IllegalArgumentException("Explicit supported Hub/Garden warp destination required");}
    }
    public record Observation(UUID uuid,Object player,Object world,Object connection,GameArea area,String dimension,Vec3 position,
                              boolean healthy,boolean loaded,boolean supported,long nativeSerial,Vec3 nativeTeleport,long tick,boolean loading) {
        public Observation(UUID uuid,Object player,Object world,Object connection,GameArea area,String dimension,Vec3 position,
                           boolean healthy,boolean loaded,boolean supported,long nativeSerial,Vec3 nativeTeleport,long tick){
            this(uuid,player,world,connection,area,dimension,position,healthy,loaded,supported,nativeSerial,nativeTeleport,tick,false);
        }
    }
    public record Pending(UUID uuid,Object player,Object previousWorld,Object connection,Destination destination,long afterSerial,long deadline) {}
    private final Pending pending;private Observation cached;private State state=State.WAITING;private boolean expectedConsumed;private long last;
    private GeorgeWarpPermit(Pending pending,long tick){this.pending=pending;last=tick;}
    public static GeorgeWarpPermit issue(Observation origin,Destination target,int timeoutTicks){
        if(!live(origin)||origin.loading()||!origin.loaded()||!origin.supported()||timeoutTicks<1||timeoutTicks>1200)throw new IllegalArgumentException("Native source warp owner and finite deadline required");
        return new GeorgeWarpPermit(new Pending(origin.uuid(),origin.player(),origin.world(),origin.connection(),Objects.requireNonNull(target),origin.nativeSerial(),Math.addExact(origin.tick(),timeoutTicks)),origin.tick());
    }
    private static boolean finite(Vec3 p){return p!=null&&Double.isFinite(p.x())&&Double.isFinite(p.y())&&Double.isFinite(p.z());}
    private static boolean live(Observation o){return o!=null&&o.healthy()&&o.uuid()!=null&&o.player()!=null&&o.world()!=null&&o.connection()!=null&&finite(o.position());}
    private boolean transport(Observation o){return o!=null&&pending.uuid().equals(o.uuid())&&o.connection()==pending.connection();}
    private boolean owner(Observation o){return live(o)&&transport(o);}
    private boolean atDestination(Vec3 p){return finite(p)&&p.distanceTo(pending.destination().position())<=pending.destination().radius();}
    private boolean arrival(Observation o){return owner(o)&&!o.loading()&&o.loaded()&&o.supported()&&o.nativeSerial()>pending.afterSerial()&&o.area()==pending.destination().area()&&Objects.equals(o.dimension(),pending.destination().dimension())&&atDestination(o.nativeTeleport())&&atDestination(o.position());}
    /** Cache the exact packet-applied receipt; UNKNOWN/loading may wait, never grant admission. */
    public boolean observe(Observation o){
        if(state==State.CANCELLED||state==State.COMMITTED)return false;
        if(!transport(o)||o.tick()<last||o.tick()>=pending.deadline()||!owner(o)&&!o.loading()){cancel();return false;}last=o.tick();
        if(o.nativeSerial()<=pending.afterSerial()){if(cached!=null)cancel();else state=State.WAITING;return false;}
        if(o.loading()&&cached==null&&o.nativeTeleport()==null){state=State.WAITING;return false;}
        if(!atDestination(o.nativeTeleport())){cancel();return false;}
        if(cached!=null&&(cached.player()!=o.player()||cached.world()!=o.world()||cached.nativeSerial()!=o.nativeSerial()||!Objects.equals(cached.nativeTeleport(),o.nativeTeleport()))){cancel();return false;}
        // Native player/world identity exists only after the loading transition supplies a real receipt scope.
        if(cached==null&&o.player()!=null&&o.world()!=null)cached=o;
        if(!o.loading()&&o.area()!=GameArea.UNKNOWN&&o.area()!=pending.destination().area()
                ||o.dimension()!=null&&!o.dimension().isBlank()&&!o.dimension().equals(pending.destination().dimension())){cancel();return false;}
        if(o.loading()||o.area()==GameArea.UNKNOWN||!o.loaded()||!o.supported()){state=State.WAITING;return false;}
        if(!arrival(o)){cancel();return false;}
        cached=o;state=State.OBSERVED;return true;
    }
    /** Immutable descriptor survives internal COMMITTED → Root port handoff until explicit cancel. */
    public Optional<Pending> cachedPendingNativeWarp(){return state==State.CANCELLED?Optional.empty():Optional.of(pending);}
    public Optional<Observation> cachedNativeReceipt(){return Optional.ofNullable(cached);}
    public boolean consumeExpectedTeleport(Vec3 position,long tick){if(expectedConsumed||state!=State.OBSERVED||cached==null||tick<last||tick>=pending.deadline()||!atDestination(position))return false;expectedConsumed=true;return true;}
    /** Return this exact native observation to the coordinator's typed admission hook. Never replace capture directly. */
    public Optional<Observation> commit(Observation current){
        if(state!=State.OBSERVED||cached==null||current.tick()<last||current.tick()>=pending.deadline()||!arrival(current)||current.player()!=cached.player()||current.world()!=cached.world()||current.nativeSerial()!=cached.nativeSerial()||!Objects.equals(current.nativeTeleport(),cached.nativeTeleport())){cancel();return Optional.empty();}
        state=State.COMMITTED;return Optional.of(current);
    }
    public void cancel(){state=State.CANCELLED;cached=null;}
    public State state(){return state;}public Pending pending(){return pending;}
}
