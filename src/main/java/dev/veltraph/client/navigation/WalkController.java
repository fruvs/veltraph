package dev.veltraph.client.navigation;

import dev.veltraph.client.world.BlockPos;
import dev.veltraph.client.world.Vec3;
import java.util.List;

/** Pure route follower; movement is released on arrival, obstruction, timeout and reset. */
public final class WalkController {
    public enum State { IDLE, WALKING, ARRIVED, BLOCKED }
    public record Input(Vec3 position, float yaw, boolean onGround, long now) {}
    public record Output(boolean forward, boolean jump, float yaw, State state) {}
    private List<BlockPos> path=List.of();
    private List<Vec3> course;
    private List<Boolean> certifiedDrops=List.of();
    private Vec3 destination;
    private Vec3 courseOrigin;
    private Vec3 steeringTarget;
    private int index;
    private int completedDropIndex=-1;
    private List<Boolean> continuationBroadEdges=List.of();
    private double terminalRadius=.35;
    private float lastYawStep;
    private State state=State.IDLE;
    private double best=Double.POSITIVE_INFINITY;
    private long progressAt;
    // Exactly one value owns the diagnostic: an already evaluated String, or pure retained
    // starting-course data. Replacing it at each original assignment also retires deferred data.
    private Object diagnostic="idle";
    private static final class StartingCourseDiagnostic {
        private final List<Vec3> originalCourse;
        private final Vec3 originalDestination;
        private StartingCourseDiagnostic(List<Vec3> course,Vec3 destination){
            originalCourse=course;originalDestination=destination;
        }
        // Existing checkpoint freezer recursively retains only known immutable data values.
        // This tag + original course + original destination conserves the message without
        // rendering record/list text or introducing opaque helper identity as permission.
        private List<?> checkpoint(){
            return java.util.Arrays.asList("starting proved dynamic course",originalCourse,originalDestination);
        }
        @Override public String toString(){
            return "starting proved dynamic course="+originalCourse+" destination="+originalDestination;
        }
    }
    private final java.util.function.LongSupplier routeSeed;
    private final TravelHeading rotation=new TravelHeading();
    public WalkController() { this(()->java.util.concurrent.ThreadLocalRandom.current().nextLong()); }
    public WalkController(double sessionMultiplier) { this(); }
    /** Fixed per-run seed for reproducible private routes; never sampled per tick. */
    public WalkController(long seed) { this(()->seed); }
    WalkController(java.util.function.LongSupplier routeSeed) { this.routeSeed=java.util.Objects.requireNonNull(routeSeed); }
    public void start(List<BlockPos> steps,long now) {
        continuationBroadEdges=List.of();terminalRadius=.35; steeringTarget=null; destination=null; path=List.copyOf(steps); course=null; certifiedDrops=List.of(); rotation.reset(); lastYawStep=0; index=0; completedDropIndex=-1; best=Double.POSITIVE_INFINITY; progressAt=now;
        state=path.isEmpty()?State.ARRIVED:State.WALKING;
        diagnostic="starting path="+path+" destination="+destination;
    }
    public void start(List<BlockPos> steps,Vec3 exactDestination,long now) {
        start(steps,exactDestination,now,.35);
    }
    /** Only the final exact pose uses this stricter tolerance; intermediate route nodes retain .35. */
    public void start(List<BlockPos> steps,Vec3 exactDestination,long now,double terminalRadius) {
        if(!Double.isFinite(terminalRadius)||terminalRadius<=0||terminalRadius>.35)throw new IllegalArgumentException("Invalid terminal walk tolerance");
        start(steps,now);destination=exactDestination;this.terminalRadius=terminalRadius;state=State.WALKING;
    }
    /** Install an already completely proved finite course; each ordinary tick still proves its actual edge. */
    public void startCourse(List<Vec3> points,Vec3 origin,long now,double radius) {
        startCourse(points,origin,now,radius,null);
    }
    /** Original graph-derived drop certificates survive broadening of adjacent level windows. */
    public void startCourse(List<Vec3> points,Vec3 origin,long now,double radius,GroundPathfinder.Terrain terrain) {
        if(points==null||points.isEmpty()||points.size()>6000||origin==null
                ||!Double.isFinite(origin.x())||!Double.isFinite(origin.y())||!Double.isFinite(origin.z())
                ||points.stream().anyMatch(p->p==null||!Double.isFinite(p.x())||!Double.isFinite(p.y())||!Double.isFinite(p.z())))
            throw new IllegalArgumentException("Invalid finite dynamic course");
        start(List.of(),points.getLast(),now,radius);
        course=List.copyOf(points);courseOrigin=origin;
        var drops=new java.util.ArrayList<Boolean>();Vec3 previous=origin;
        for(Vec3 point:course){drops.add(terrain!=null&&previous.y()-point.y()>.6&&previous.y()-point.y()<=1.001
                &&GroundPathfinder.standable(terrain,GroundPathfinder.node(previous))
                &&GroundPathfinder.canStep(terrain,GroundPathfinder.node(previous),GroundPathfinder.node(point)));previous=point;}
        certifiedDrops=List.copyOf(drops);
        diagnostic=new StartingCourseDiagnostic(course,destination);
    }
    public Output tick(Input in, GroundPathfinder.Terrain terrain, long timeout) {
        return tick(in,terrain,timeout,.5,1);
    }
    public Output tick(Input in, GroundPathfinder.Terrain terrain, long timeout,double sensitivity,double speed) {
        steeringTarget=null;
        if (state!=State.WALKING) return new Output(false,false,in.yaw(),state);
        if(course==null) {
            var nodes=new java.util.ArrayList<>(path.stream().map(terrain::position).toList());
            if(destination!=null) {
                if(!nodes.isEmpty() && nodes.get(nodes.size()-1).distanceTo(destination)<.001) nodes.remove(nodes.size()-1);
                nodes.add(destination);
            }
            courseOrigin=in.position();
            course=TravelPath.shapeOpen(in.position(),nodes,(a,b)->TravelPath.walkClear(terrain,a,b),routeSeed.getAsLong());
            var supported=new java.util.ArrayList<Vec3>(course);
            for(int i=0;i<supported.size()-1;i++)supported.set(i,terrain.surfacePosition(supported.get(i)));
            course=List.copyOf(supported);
            var drops=new java.util.ArrayList<Boolean>();Vec3 previous=courseOrigin;
            for(Vec3 point:course) {
                drops.add(previous.y()-point.y()>.6&&previous.y()-point.y()<=1.001
                        &&GroundPathfinder.standable(terrain,GroundPathfinder.node(previous))
                        &&GroundPathfinder.canStep(terrain,GroundPathfinder.node(previous),GroundPathfinder.node(point)));
                previous=point;
            }
            certifiedDrops=List.copyOf(drops);
        }
        Vec3 center=course.get(index);
        while(index<course.size()-1 && in.position().distanceTo(center)<.65
                && TravelPath.walkClear(terrain,in.position(),course.get(index+1))) {
            center=course.get(++index);best=Double.POSITIVE_INFINITY;progressAt=in.now();
        }
        double distance=in.position().distanceTo(center);
        diagnostic=describe(in,terrain,center,distance);
        // A world change at the endpoint must not be hidden by the arrival tolerance.
        if(!terrain.standingClear(center)) {
            diagnostic="endpoint obstruction | "+diagnostic;
            state=State.BLOCKED;return new Output(false,false,in.yaw(),state);
        }
        boolean nextClear=index==course.size()-1||edgeClear(terrain,in.position(),course.get(index+1))||continuesCertifiedDrop(terrain,in.position(),index+1);
        // A reached intermediate point cannot become a stationary turning target when
        // its continuation is newly obstructed. Keep valid tight-corner recentering: a
        // blocked shortcut from the off-center actual pose alone is not a route failure.
        // The admitted center-to-next edge must also fail fresh step/sweep proofs, and
        // the actual certified-drop continuation above must not still be valid.
        if(index<course.size()-1&&distance<.35&&in.onGround()&&terrain.standingClear(in.position())
                &&!nextClear&&!edgeClear(terrain,center,course.get(index+1))) {
            diagnostic="reached waypoint continuation obstructed | "+diagnostic;
            state=State.BLOCKED;return new Output(false,false,in.yaw(),state);
        }
        if(distance<(index==course.size()-1?terminalRadius:.35) && in.onGround() && terrain.standingClear(in.position())&&nextClear) {
            if(index==course.size()-1) {
                // Arrival releases physical movement immediately. An active camera rate must
                // decelerate before the terminal zero-rate handoff; simply skipping the last
                // heading step can exceed the same 1.2-degree acceleration limit as turning.
                // Account only for float angle representation, bounded far below the native
                // oracle's .01 allowance; wrap() adds avoidable rounding to a small step.
                if(Math.abs(lastYawStep)>1.2+Math.min(.0001,2*Math.ulp(in.yaw()))) {
                    float settled=rotation.step(in.yaw(),in.yaw(),speed);
                    lastYawStep=settled-in.yaw();diagnostic="heading settling inputYaw="+in.yaw()+" outputYaw="+settled+" yawStep="+lastYawStep+" | "+diagnostic;
                    return new Output(false,false,settled,state);
                }
                state=State.ARRIVED;return new Output(false,false,in.yaw(),state);
            }
            center=course.get(++index);distance=in.position().distanceTo(center);best=Double.POSITIVE_INFINITY;progressAt=in.now();
        }
        BlockPos target=GroundPathfinder.node(center),current=GroundPathfinder.node(in.position());
        diagnostic=describe(in,terrain,center,distance);
        if (!GroundPathfinder.standable(terrain,target)
                || (in.onGround() && !TravelPath.walkClear(terrain,in.position(),center)
                    && !(GroundPathfinder.canStep(terrain,current,target)&&terrain.stepClear(in.position(),center))
                    && !continuesCertifiedDrop(terrain,in.position(),index))) {
            diagnostic="edge obstruction targetStandable="+GroundPathfinder.standable(terrain,target)
                    +" canStep="+GroundPathfinder.canStep(terrain,current,target)+" stepClear="+terrain.stepClear(in.position(),center)+" | "+diagnostic;
            state=State.BLOCKED; return new Output(false,false,in.yaw(),state);
        }
        if (distance < best-.05) { best=distance; progressAt=in.now(); }
        if (in.now()-progressAt > Math.max(500,timeout)) {
            diagnostic="progress timeout | "+diagnostic;
            state=State.BLOCKED; return new Output(false,false,in.yaw(),state);
        }
        // Retain the exact node/step proofs above. Only a flat, wide, swept-clear lead may
        // guide the camera beyond that node; tight corridors and the final pose use it directly.
        Vec3 steering=OpenTravelSteering.target(courseOrigin,course,index,in.position(),1.25,
                (a,b)->Math.abs(a.y()-b.y())<.05&&TravelPath.walkClear(terrain,a,b));
        steeringTarget=steering;
        double dx=steering.x()-in.position().x(), dz=steering.z()-in.position().z();
        float desired=(float)Math.toDegrees(Math.atan2(-dx,dz));
        float yaw=rotation.step(in.yaw(),desired,speed);
        lastYawStep=yaw-in.yaw();
        boolean aligned=Math.abs(wrap(desired-yaw))<15;
        diagnostic+=" desiredYaw="+desired+" outputYaw="+yaw+" forward="+aligned+" jump="+(aligned && in.onGround() && center.y()>in.position().y()+.6);
        return new Output(aligned,aligned && in.onGround() && center.y()>in.position().y()+.6,yaw,state);
    }
    /** Native grounding can still report the preceding support after the body clears a ledge.
     * Continue only the finite horizontal projection of the original admitted cardinal descent;
     * never derive new same-column vertical permission from the current cell or ground flag.
     */
    private boolean continuesCertifiedDrop(GroundPathfinder.Terrain terrain,Vec3 actual,int edge) {
        if(edge>=certifiedDrops.size()||!certifiedDrops.get(edge))return false;
        Vec3 from=edge==0?courseOrigin:course.get(edge-1),to=course.get(edge);
        BlockPos source=GroundPathfinder.node(from),landing=GroundPathfinder.node(to),current=GroundPathfinder.node(actual);
        if(current.x()!=landing.x()||current.z()!=landing.z()||current.y()!=source.y()
                ||Math.abs(actual.y()-from.y())>=.08||source.y()-landing.y()!=1)return false;
        int dx=landing.x()-source.x(),dz=landing.z()-source.z();
        if(Math.abs(dx)+Math.abs(dz)!=1)return false;
        double progress=certifiedDropProgress(actual,edge);
        // R2's actual body had cleared old support, but remained before the landing center
        // with a fresh safe sweep. Requiring old footprint overlap incorrectly blocked it.
        if(!Double.isFinite(progress)||progress<0||progress>1)return false;
        return GroundPathfinder.standable(terrain,source)&&GroundPathfinder.canStep(terrain,source,landing)
                &&terrain.stepClear(actual,to);
    }
    private double certifiedDropProgress(Vec3 actual,int edge) {
        if(edge>=certifiedDrops.size()||!certifiedDrops.get(edge))return Double.NaN;
        Vec3 from=edge==0?courseOrigin:course.get(edge-1),to=course.get(edge);
        double dx=to.x()-from.x(),dz=to.z()-from.z(),length=dx*dx+dz*dz;
        return length==0?Double.NaN:((actual.x()-from.x())*dx+(actual.z()-from.z())*dz)/length;
    }
    /** Explicit passive checkpoint; no route shaping, RNG, clock or tick is invoked. */
    public dev.veltraph.client.farming.module.FarmMaintenanceState maintenanceState() {
        return dev.veltraph.client.farming.module.FarmMaintenanceState.of(this,
                "path",path,"course",course,"continuationBroadEdges",continuationBroadEdges,"certifiedDrops",certifiedDrops,"destination",destination,
                "courseOrigin",courseOrigin,"steeringTarget",steeringTarget,"index",index,"completedDropIndex",completedDropIndex,"terminalRadius",terminalRadius,
                "lastYawStep",lastYawStep,"state",state,"best",best,"progressAt",progressAt,
                "diagnostic",diagnostic instanceof StartingCourseDiagnostic starting?starting.checkpoint():diagnostic,"routeSeed",routeSeed,"rotation",rotation.maintenanceState());
    }
    /** Immutable admitted graph only; no shaping, timer or random draw. */
    public List<BlockPos> admittedPath(){return path;}
    /** A specific actually landed original drop cell may complete its nonterminal graph point.
     * All other waypoint radii, terminal pose, original edge and loaded geometry stay unchanged. */
    public boolean acknowledgeDropLanding(GroundDropBrakeForecast.Episode receipt,long now,GroundPathfinder.Terrain terrain,java.util.function.BooleanSupplier current){
        if(receipt==null||!receipt.nextGround()||!receipt.remaining().isEmpty())return false;
        Vec3 actual=receipt.actual(),origin=receipt.origin(),landing=receipt.target();
        if(state!=State.WALKING||course==null||index>=course.size()||!landing.equals(course.get(index))
                ||!locomotionDropCertified(origin,landing)||actual.distanceTo(landing)>.65
                ||!GroundPathfinder.node(actual).equals(GroundPathfinder.node(landing)))return false;
        var fence=new DynamicRouteQueryFence(System::nanoTime,current,50_000_000L);
        if(!fence.query(()->terrain.standingClear(actual))||!fence.query(()->TravelPath.walkClear(terrain,actual,landing))||!current.getAsBoolean())return false;
        // This edge has physically landed. A terminal pose still requires its original strict
        // radius; a newly blocked next edge cannot become permission to skip the current node.
        boolean nextClear=index<course.size()-1&&fence.query(()->edgeClear(terrain,actual,course.get(index+1)));
        if(!fence.available()||!current.getAsBoolean())return false;
        completedDropIndex=index;
        if(nextClear){index++;best=Double.POSITIVE_INFINITY;progressAt=now;}
        return true;
    }
    /** Passive last admitted flat/broad lead; this observation never grants body clearance or input authority. */
    public Vec3 locomotionSteeringTarget(){return steeringTarget;}
    /** Passive current ground edge target; never advances or reshapes a course. */
    public Vec3 locomotionTarget(){return course==null||index>=course.size()?null:course.get(index);}
    /** Original graph certificate only; never infers a new descent from the current body pose. */
    public Vec3 locomotionDropOrigin(){return index!=completedDropIndex&&index<certifiedDrops.size()&&certifiedDrops.get(index)
            ?index==0?courseOrigin:course.get(index-1):null;}
    /** Passive lookup of an immutable original edge, including a just-completed landing edge. */
    public boolean locomotionDropCertified(Vec3 origin,Vec3 landing){
        if(course==null||origin==null||landing==null)return false;
        for(int i=Math.max(0,index-1);i<=index&&i<course.size();i++)
            if(i<certifiedDrops.size()&&certifiedDrops.get(i)&&landing.equals(course.get(i))
                    &&origin.equals(i==0?courseOrigin:course.get(i-1)))return true;
        return false;
    }
    /** Passive bounded straight continuation. Fresh native sweep, never this hint, grants sprint. */
    public Vec3 locomotionSprintTarget(Vec3 actual){var candidates=locomotionSprintCandidates(actual);return candidates.isEmpty()?locomotionTarget():candidates.getLast();}
    /** Passive nearest-to-farthest hints within the exact original straight continuation bounds. */
    public List<Vec3> locomotionSprintCandidates(Vec3 actual){
        Vec3 first=locomotionTarget();if(first==null||actual==null)return List.of();
        double length=actual.horizontalDistanceTo(first);var result=new java.util.ArrayList<Vec3>();result.add(first);
        if(length<.1)return List.copyOf(result);
        double dx=(first.x()-actual.x())/length,dz=(first.z()-actual.z())/length;
        for(int i=index+1;i<course.size()&&i<=index+32;i++){
            Vec3 next=course.get(i);double nx=next.x()-actual.x(),nz=next.z()-actual.z();
            double along=nx*dx+nz*dz,lateral=Math.abs(nx*dz-nz*dx);
            if(Math.abs(next.y()-actual.y())>=.05||along<length||along>6||lateral>.15)break;
            result.add(next);length=along;
        }
        return List.copyOf(result);
    }
    public void startCertifiedDynamicCourse(List<Vec3> points,Vec3 origin,long now,double radius,GroundPathfinder.Terrain terrain,List<Boolean> broad){
        if(broad==null||broad.size()!=points.size())throw new IllegalArgumentException("Missing exact broad edge provenance");
        startCourse(points,origin,now,radius,terrain);continuationBroadEdges=List.copyOf(broad);
    }
    public record ContinuationBasis(Vec3 previous,List<Vec3> points,List<Boolean> broadEdges){public ContinuationBasis{points=List.copyOf(points);broadEdges=List.copyOf(broadEdges);}}
    public ContinuationBasis locomotionContinuationBasis(){
        if(course==null||continuationBroadEdges.size()!=course.size())return new ContinuationBasis(null,List.of(),List.of());
        int end=Math.min(course.size(),index+33);return new ContinuationBasis(index==0?courseOrigin:course.get(index-1),course.subList(index,end),continuationBroadEdges.subList(index,end));
    }
    /** Passive bounded original course window; no route mutation, terrain query or random sampling. */
    public List<Vec3> locomotionContinuationCourse(){return course==null?List.of():List.copyOf(course.subList(index,Math.min(course.size(),index+33)));}
    /** Passive original progress deadline for a captured native episode: no geometry, node
     * advance, shaping, RNG or tick is invoked. The owning navigator supplies its actual clock. */
    /** Passive original clock floor remains applicable during physically settling ARRIVED output. */
    public boolean locomotionClockCurrent(long now){return now>=progressAt;}
    public boolean locomotionProgressCurrent(long now,long timeout) {
        return state==State.WALKING&&now>=progressAt&&now-progressAt<=Math.max(500L,timeout);
    }
    public State state() { return state; }
    private static boolean edgeClear(GroundPathfinder.Terrain terrain,Vec3 from,Vec3 to) {
        return TravelPath.walkClear(terrain,from,to)
                ||(GroundPathfinder.canStep(terrain,GroundPathfinder.node(from),GroundPathfinder.node(to))&&terrain.stepClear(from,to));
    }
    private String describe(Input in,GroundPathfinder.Terrain terrain,Vec3 goal,double distance) {
        return "index="+index+" course="+course+" goal="+goal+" distance="+distance
                +" dropCertificate="+(index<certifiedDrops.size()&&certifiedDrops.get(index))+" dropProgress="+certifiedDropProgress(in.position(),index)
                +" endpointClear="+terrain.standingClear(goal)+" currentClear="+terrain.standingClear(in.position())
                +" walkClear="+TravelPath.walkClear(terrain,in.position(),goal)+" best="+best+" stuckMs="+(in.now()-progressAt);
    }
    /** Last evaluated route geometry and controller decision; never advances the controller. */
    public String diagnostics() { return state+" | "+diagnostic; }
    public void reset() { continuationBroadEdges=List.of();path=List.of(); course=null; certifiedDrops=List.of(); destination=null; steeringTarget=null; rotation.reset(); lastYawStep=0; index=0; completedDropIndex=-1; state=State.IDLE; }
    public static float wrap(float value) { return (value%360+540)%360-180; }
}
