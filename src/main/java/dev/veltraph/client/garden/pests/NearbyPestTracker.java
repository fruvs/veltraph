package dev.veltraph.client.garden.pests;

import dev.veltraph.client.world.Vec3;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Read-only local admission policy. Geometry never grants native effect or maintenance authority. */
public final class NearbyPestTracker {
    public record Settings(double fovDegrees,int dwellTicks,double maximumStep) {
        public Settings {
            if(!Double.isFinite(fovDegrees)||fovDegrees<=0||fovDegrees>180||dwellTicks<1||dwellTicks>200
                ||!Double.isFinite(maximumStep)||maximumStep<=0||maximumStep>8)
                throw new IllegalArgumentException("Explicit local FOV (0,180], dwell 1..200 ticks and motion step (0,8] required");
        }
    }
    public record Scope(Object player,Object world,Object connection,Object farm,Object config,Object camera,Object policy,int plot) {}
    public record Target(Object nativeEntity,int nativeId,UUID uuid,String model,Vec3 eye) {}
    public record Observation(Scope scope,long tick,Vec3 playerEye,float yaw,Target target,boolean visible,double reach) {}
    public static final class Claim {
        private final Observation observed;
        private Claim(Observation observed){this.observed=observed;}
        public Observation observation(){return observed;}
    }
    private Settings settings;private Observation previous;private long began;private Claim claim;private record Retired(java.lang.ref.WeakReference<Object> world,UUID uuid) {}
    private final java.util.List<Retired> retired=new java.util.ArrayList<>();
    private String status="Nearby pest observation unavailable";
    public Optional<Claim> observe(Observation o,Settings requested) {
        claim=null;
        if(!valid(o)||requested==null){reset();return Optional.empty();}
        retired.removeIf(r->r.world().get()==null);
        if(retired.stream().anyMatch(r->r.world().get()==o.scope().world()&&r.uuid().equals(o.target().uuid()))||retired.size()>=256){previous=null;status="Same native pest already attempted; no uncertain retry";return Optional.empty();}
        boolean continuity=previous!=null&&sameScope(previous.scope(),o.scope())&&sameTarget(previous.target(),o.target())
            &&requested.equals(settings)&&previous.tick()<Long.MAX_VALUE&&o.tick()==previous.tick()+1
            &&previous.playerEye().distanceTo(o.playerEye())<=requested.maximumStep()
            &&previous.target().eye().distanceTo(o.target().eye())<=requested.maximumStep();
        if(!eligible(o,requested)){previous=null;status="Native pest outside visible FOV/range";return Optional.empty();}
        // Repeated or backwards frames cannot count as the first tick of a fresh dwell.
        if(previous!=null&&o.tick()<=previous.tick()){previous=null;status="Non-monotonic nearby frame rejected";return Optional.empty();}
        if(!continuity)began=o.tick();settings=requested;previous=o;
        if(o.tick()-began<requested.dwellTicks()){status="Same native pest visible; dwelling";return Optional.empty();}
        claim=new Claim(o);status="Same native pest completed continuous local dwell";return Optional.of(claim);
    }
    public boolean current(Claim c,Observation fresh,Settings current) {
        return c!=null&&c==claim&&valid(fresh)&&current!=null&&current.equals(settings)
            &&fresh.tick()==c.observed.tick()&&sameScope(c.observed.scope(),fresh.scope())
            &&sameTarget(c.observed.target(),fresh.target())&&eligible(fresh,current)
            &&c.observed.playerEye().equals(fresh.playerEye())
            &&c.observed.target().eye().equals(fresh.target().eye())&&Float.compare(c.observed.yaw(),fresh.yaw())==0;
    }
    /** Retire only after actual admission attempt; an observation or preview is not an outcome. */
    public boolean attempted(Claim c){if(c==null||c!=claim)return false;retired.add(new Retired(new java.lang.ref.WeakReference<>(c.observed.scope().world()),c.observed.target().uuid()));previous=null;claim=null;status="Nearby native pest attempt retired";return true;}
    /** Interrupt dwell on all ordinary Root stop/suspension/preemption boundaries; retirement survives. */
    public void reset(){previous=null;claim=null;settings=null;began=0;status="Nearby pest observation unavailable";}
    public String status(){return status;}
    private static boolean finite(Vec3 v){return v!=null&&Double.isFinite(v.x())&&Double.isFinite(v.y())&&Double.isFinite(v.z());}
    private static boolean valid(Observation o){return o!=null&&o.tick()>=0&&o.scope()!=null&&o.scope().player()!=null&&o.scope().world()!=null&&o.scope().connection()!=null
        &&o.scope().farm()!=null&&o.scope().config()!=null&&o.scope().camera()!=null&&o.scope().policy()!=null&&o.scope().plot()>=1&&o.scope().plot()<=24
        &&finite(o.playerEye())&&Float.isFinite(o.yaw())&&o.target()!=null&&o.target().nativeEntity()!=null&&o.target().uuid()!=null&&o.target().model()!=null&&!o.target().model().isBlank()
        &&finite(o.target().eye())&&Double.isFinite(o.reach())&&o.reach()>.5;}
    public static boolean eligible(Observation o,Settings s){return valid(o)&&visibleWithin(o.playerEye(),o.yaw(),o.target().eye(),o.reach(),o.visible(),s);}
    /** Geometry only: PREPARED intentionally owns no camera. This cannot authorize any native effect. */
    public static boolean visibleWithin(Vec3 playerEye,float yaw,Vec3 targetEye,double reach,boolean visible,Settings s){
        if(!finite(playerEye)||!finite(targetEye)||!Float.isFinite(yaw)||!Double.isFinite(reach)||reach<=.5||s==null||!visible||playerEye.distanceTo(targetEye)>reach-.5)return false;
        double dx=targetEye.x()-playerEye.x(),dz=targetEye.z()-playerEye.z();
        double needed=Math.toDegrees(Math.atan2(-dx,dz)),difference=(needed-yaw)%360;
        if(difference>=180)difference-=360;if(difference< -180)difference+=360;
        return Math.abs(difference)<=s.fovDegrees()/2;
    }
    private static boolean sameScope(Scope a,Scope b){return a!=null&&b!=null&&a.player()==b.player()&&a.world()==b.world()&&a.connection()==b.connection()
        &&a.farm()==b.farm()&&a.config()==b.config()&&a.camera()==b.camera()&&Objects.equals(a.policy(),b.policy())&&a.plot()==b.plot();}
    private static boolean sameTarget(Target a,Target b){return a!=null&&b!=null&&a.nativeEntity()==b.nativeEntity()&&a.nativeId()==b.nativeId()
        &&a.uuid().equals(b.uuid())&&a.model().equals(b.model());}
}
