package dev.veltraph.client.navigation;

import dev.veltraph.client.world.Vec3;
import java.util.List;

/** Input-driven flight follower. The recovered vertical arrival tolerance is 0.2 blocks.
 * Steering gains are explicit local replacements, not claimed as recovered PID constants.
 */
public final class FlightController {
    public enum State { IDLE, FLYING, ARRIVED, BLOCKED }
    public record Input(Vec3 position,Vec3 velocity,float yaw,boolean available,boolean mayFly,boolean pathClear,boolean supportedOnGround,long now) {
        public Input(Vec3 position,Vec3 velocity,float yaw,boolean available,boolean mayFly,boolean pathClear,long now) {
            this(position,velocity,yaw,available,mayFly,pathClear,false,now);
        }
    }
    public record Output(State state,float yaw,boolean forward,boolean back,boolean left,boolean right,boolean up,boolean down) {}
    private Vec3 courseOrigin;
    private List<Vec3> path=List.of(); private int index; private State state=State.IDLE;
    private boolean landingDescent;
    private boolean destinationSupportKnown;private double arrivalRadius=.3;private boolean precisionKnown;private double coastDecay=(double).91f;private Vec3 selectedLead;
    private double best=Double.POSITIVE_INFINITY; private long progressAt; private String reason="Idle";
    private java.util.function.BiPredicate<Vec3,Vec3> clearance=(a,b)->true;
    public void clearance(java.util.function.BiPredicate<Vec3,Vec3> check) { clearance=check; }
    private java.util.function.BiPredicate<Vec3,Vec3> landingClearance=(a,b)->false;
    /** Opt-in physical support/loaded descent proof; the default retains tight column centering. */
    public void landingClearance(java.util.function.BiPredicate<Vec3,Vec3> check) { landingClearance=java.util.Objects.requireNonNull(check); }
    private final TravelHeading rotation=new TravelHeading();
    public FlightController() {}
    public FlightController(double sessionMultiplier) {}
    public void start(List<Vec3> nodes,long now) {
        start(nodes,now,false,false);
    }
    /** The native planner may pin a supported destination; vertical-shaped floating routes stay floating. */
    public void start(List<Vec3> nodes,long now,boolean supportedGoal) {
        start(nodes,now,supportedGoal,true);
    }
    /** Shared precision is part of its immutable request, never a widened native arrival window. */
    public void startSupported(List<Vec3> nodes,long now,double terminalRadius){
        startSupported(nodes,now,terminalRadius,(double).91f);
    }
    public void startSupported(List<Vec3> nodes,long now,double terminalRadius,double horizontalDecay){
        if(!Double.isFinite(terminalRadius)||terminalRadius<=0||terminalRadius>.35)throw new IllegalArgumentException("Finite shared terminal radius required");
        if(!Double.isFinite(horizontalDecay)||horizontalDecay<0||horizontalDecay>=1)throw new IllegalArgumentException("Finite passive flight decay required");
        start(nodes,now,true,true);coastDecay=Math.max((double).91f,horizontalDecay);arrivalRadius=Math.min(.3,terminalRadius);precisionKnown=arrivalRadius<.3;
    }
    private void start(List<Vec3> nodes,long now,boolean supportedGoal,boolean supportKnown) {
        courseOrigin=null;path=List.copyOf(nodes);rotation.reset();index=0;landingDescent=supportedGoal;best=Double.POSITIVE_INFINITY;progressAt=now;
        destinationSupportKnown=supportKnown;arrivalRadius=.3;precisionKnown=false;coastDecay=(double).91f;selectedLead=null;
        state=path.isEmpty()?State.ARRIVED:State.FLYING;reason=state.name();
    }
    public Vec3 target() { return index<path.size()?path.get(index):null; }
    public Output tick(Input in,double maxSpeed,long stallMs) {
        return tick(in,maxSpeed,stallMs,.5,1);
    }
    public Output tick(Input in,double maxSpeed,long stallMs,double sensitivity,double speedMultiplier) {
        if(state!=State.FLYING) return release(in.yaw());
        if(!in.available()||!in.mayFly()||!in.pathClear()) {
            state=State.BLOCKED;reason=!in.mayFly()?"Server flight permission unavailable":"Flight path obstructed or world unavailable";return release(in.yaw());
        }
        if(courseOrigin==null)courseOrigin=in.position();
        Vec3 target=path.get(index); double distance=in.position().distanceTo(target);
        double horizontal=in.position().horizontalDistanceTo(target), dy=target.y()-in.position().y();
        while(index<path.size()-1 && distance<.65
                && (!landingColumn(index)||((horizontal<.1 || verifiedLanding(in,index,horizontal)) && Math.abs(dy)<.2
                    && Math.hypot(in.velocity().x(),in.velocity().z())<.02 && Math.abs(in.velocity().y())<.035))
                && clearance.test(in.position(),path.get(index+1))
                && clearance.test(new Vec3(in.position().x()+in.velocity().x()*4,in.position().y()+in.velocity().y()*2,in.position().z()+in.velocity().z()*4),path.get(index+1))) {
            if(landingColumn(index)&&verifiedLanding(in,index,horizontal))landingDescent=true;
            target=path.get(++index);distance=in.position().distanceTo(target);
            horizontal=in.position().horizontalDistanceTo(target);dy=target.y()-in.position().y();
            best=Double.POSITIVE_INFINITY;progressAt=in.now();
        }
        boolean atGoal=index==path.size()-1 && horizontal<arrivalRadius && Math.abs(dy)<.2;
        boolean landed=atGoal&&in.supportedOnGround();
        // Low current speed alone does not prove rest: the native .91 horizontal tail can
        // carry an outward boundary pose outside .3 after release (P2 sample82). Keep the
        // original instantaneous gates and require a clear passive resting footprint too.
        double horizontalTail=1/(1-coastDecay);
        Vec3 passiveRest=new Vec3(in.position().x()+in.velocity().x()*horizontalTail,
                landed?in.position().y():in.position().y()+in.velocity().y()*2.5,
                in.position().z()+in.velocity().z()*horizontalTail);
        boolean restSafe=passiveRest.horizontalDistanceTo(target)<arrivalRadius&&Math.abs(passiveRest.y()-target.y())<.2
                &&clearance.test(in.position(),passiveRest);
        if(atGoal && restSafe && (!landingDescent||landed) && Math.hypot(in.velocity().x(),in.velocity().z())<.02 && (landed||Math.abs(in.velocity().y())<.035)) {
            index++;state=State.ARRIVED;reason="Arrived";return release(in.yaw());
        }
        if(distance<best-.04) { best=distance;progressAt=in.now(); }
        if(in.now()-progressAt>Math.max(500,stallMs)) { state=State.BLOCKED;reason="Flight progress stalled";return release(in.yaw()); }
        boolean landingApproach=landingColumn(index);
        Vec3 steering=landingApproach||index==path.size()-1?target:
                OpenTravelSteering.target(courseOrigin,path,index,in.position(),1.1,clearance);
        selectedLead=steering;
        double dx=steering.x()-in.position().x()-in.velocity().x()*2, dz=steering.z()-in.position().z()-in.velocity().z()*2;
        boolean settling=(index==path.size()-1||landingApproach)&&horizontal<1.5;
        float desired=!settling&&horizontal>.15?(float)Math.toDegrees(Math.atan2(-dx,dz)):in.yaw();
        float yaw=rotation.step(in.yaw(),desired,speedMultiplier);
        double rad=Math.toRadians(yaw);
        double limit=Double.isFinite(maxSpeed)?Math.max(.05,Math.min(.5,maxSpeed)):.2;
        double x=steering.x()-in.position().x(),z=steering.z()-in.position().z();
        double gain=settling?.18:.28;
        boolean braking=atGoal&&restSafe||(landingApproach&&horizontal<.1&&Math.abs(dy)<.2);
        double wx=braking?0:x*gain,wz=braking?0:z*gain,length=Math.hypot(wx,wz);
        if(length>limit) { wx*=limit/length;wz*=limit/length; }
        // Correct world-space velocity on both axes even while the camera is still turning. This
        // brakes lateral inertia at intermediate corners instead of coasting into the obstacle.
        double ex=wx-in.velocity().x(),ez=wz-in.velocity().z();
        double forwardError=-Math.sin(rad)*ex+Math.cos(rad)*ez;
        double sideError=Math.cos(rad)*ex+Math.sin(rad)*ez;
        boolean grounding=landingDescent&&index==path.size()-1&&!landed;
        double vertical=grounding?-.12:braking?0:Math.max(-.25,Math.min(.25,dy*.4));
        // Native ability flight damps vertical momentum by .6 without an up/down key. If its
        // projected resting Y already fits the unchanged goal window, let it damp; opposite
        // full key pulses can otherwise alternate around the goal without reaching low motion.
        boolean coastVertical=!grounding&&atGoal&&Math.abs(target.y()-(in.position().y()+in.velocity().y()*2.5))<.2;
        reason=(settling?"Settling ":"Flying ")+(index+1)+"/"+path.size()+(atGoal?" passiveRest="+passiveRest+" restSafe="+restSafe:"");
        // The ordinary .025 velocity dead zone can strand the .1-block landing-column gate:
        // .13 blocks * .18 gain = .0234. Use fine correction outside the column, retaining the
        // normal braking dead zone inside it so a full key pulse does not amplify residual drift.
        double horizontalDeadZone=landingApproach&&!braking?.012:.025;
        if(precisionKnown)horizontalDeadZone=Math.min(horizontalDeadZone,arrivalRadius*.08);
        return new Output(state,yaw,forwardError>horizontalDeadZone,forwardError< -horizontalDeadZone,sideError>horizontalDeadZone,sideError< -horizontalDeadZone,
                !landed&&!coastVertical&&in.velocity().y()<vertical-.035,!landed&&!coastVertical&&in.velocity().y()>vertical+.035);
    }

    private boolean landingColumn(int node) {
        // The native planner knows whether the destination is supported. An ordinary floating
        // vertical interpolation waypoint must not inherit landing-only centering/braking gates.
        // Legacy two-argument starts retain their structural column policy when support is unknown.
        return (!destinationSupportKnown||landingDescent)&&node==path.size()-2 && path.get(node).horizontalDistanceTo(path.get(node+1))<.001
                && path.get(node).y()-path.get(node+1).y()>.6;
    }

    private boolean verifiedLanding(Input in,int node,double horizontal) {
        if(horizontal>=.25)return false;
        Vec3 landing=path.get(node+1),p=in.position(),v=in.velocity();
        return landingClearance.test(p,landing)&&landingClearance.test(new Vec3(p.x()+v.x()*4,p.y()+v.y()*2,p.z()+v.z()*4),landing);
    }

    private Output release(float yaw) { return new Output(state,yaw,false,false,false,false,false,false); }
    public void reset() { path=List.of();index=0;landingDescent=false;destinationSupportKnown=false;arrivalRadius=.3;precisionKnown=false;coastDecay=(double).91f;selectedLead=null;state=State.IDLE;reason="Stopped"; }
    /** Actual chosen lead, cached after all model clearance callbacks; no authority is exposed. */
    public Vec3 selectedLead(){return selectedLead;}
    public String status() { return reason; }
}
