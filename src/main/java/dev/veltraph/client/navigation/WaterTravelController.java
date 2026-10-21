package dev.veltraph.client.navigation;

import dev.veltraph.client.world.Vec3;
import java.util.List;

/** Ordinary survival inputs, with an observed-air detour that retains the original destination. */
public final class WaterTravelController {
    public enum State { IDLE, TRAVELLING, SURFACING, REFILLING, ARRIVED, BLOCKED }
    public record Input(Vec3 position,Vec3 velocity,float yaw,boolean onGround,boolean inWater,boolean submerged,
                        int air,int maxAir,float health,boolean available,boolean lava,boolean automaticSneak,long now) {}
    public record Output(State state,float yaw,boolean forward,boolean back,boolean left,boolean right,boolean jump,boolean sneak) {}
    public interface Geometry {
        boolean connection(Vec3 from,Vec3 to);
        boolean ground(Vec3 p);
        boolean water(Vec3 p);
    }
    private List<Vec3> route=List.of(),detour=List.of();private int index,detourIndex;
    private State state=State.IDLE;private String reason="Idle";private float minimumHealth;
    private double best=Double.POSITIVE_INFINITY;private long progressAt,refillAt;
    private double terminalRadius=Double.NaN;
    private final TravelHeading heading=new TravelHeading();
    public void start(List<Vec3> nodes,float health,long now,double terminalRadius) {
        if(!Double.isFinite(terminalRadius)||terminalRadius<.05||terminalRadius>.3)throw new IllegalArgumentException("Water terminal radius must be finite and stricter than the existing bound");
        start(nodes,health,now);this.terminalRadius=terminalRadius;
    }
    public void start(List<Vec3> nodes,float health,long now) {
        terminalRadius=Double.NaN;
        route=List.copyOf(nodes);detour=List.of();index=detourIndex=0;minimumHealth=health;heading.reset();best=Double.POSITIVE_INFINITY;progressAt=now;
        state=route.isEmpty()?State.ARRIVED:State.TRAVELLING;reason=state.name();
    }
    public boolean needsAir(Input in) { return state==State.TRAVELLING&&in.submerged()&&in.air()<=Math.max(40,in.maxAir()*.4); }
    public void surface(List<Vec3> nodes,long now) {
        if(nodes==null||nodes.isEmpty()) { block("No loaded clear route to breathable surface");return; }
        detour=List.copyOf(nodes);detourIndex=0;state=State.SURFACING;best=Double.POSITIVE_INFINITY;progressAt=now;reason="Surfacing for air";
    }
    /** Replan from the observed refill pose; keep heading/health ownership and the original goal. */
    public void resume(List<Vec3> nodes,long now) {
        if(nodes==null||nodes.isEmpty()) { block("Retained goal has no loaded clear route after air refill");return; }
        route=List.copyOf(nodes);index=0;detour=List.of();detourIndex=0;state=State.TRAVELLING;best=Double.POSITIVE_INFINITY;progressAt=now;reason="Resuming retained water goal";
    }
    public Vec3 target() {
        if(state==State.SURFACING||state==State.REFILLING)return detourIndex<detour.size()?detour.get(detourIndex):null;
        return index<route.size()?route.get(index):null;
    }
    public Output tick(Input in,Geometry geometry,long stallMs,double speed) {
        if(!in.available()||in.lava()||!Float.isFinite(in.health())||in.health()<=0||in.health()<minimumHealth-.01f) {
            block(in.lava()?"Lava is not a supported traversal medium":"Travel availability or health changed");return release(in.yaw());
        }
        minimumHealth=in.health();
        if(state==State.IDLE||state==State.BLOCKED||state==State.ARRIVED)return release(in.yaw());
        if(in.submerged()&&in.air()<=0) { block("No breathing reserve remaining");return release(in.yaw()); }
        Vec3 target=target();if(target==null||!geometry.connection(in.position(),target)) { block("Travel body/transition obstructed or unloaded");return release(in.yaw()); }
        if(state==State.REFILLING) {
            if(in.now()-refillAt>8000) { block("Surface did not restore breathing reserve");return release(in.yaw()); }
            if(!in.submerged()&&in.air()>=Math.max(1,in.maxAir())*.9) {
                state=State.TRAVELLING;detour=List.of();detourIndex=0;best=Double.POSITIVE_INFINITY;progressAt=in.now();reason="Resuming retained water goal";
                return release(in.yaw());
            }
        }
        List<Vec3> nodes=(state==State.SURFACING||state==State.REFILLING)?detour:route;
        int cursor=(state==State.SURFACING||state==State.REFILLING)?detourIndex:index;
        double horizontal=in.position().horizontalDistanceTo(target),dy=target.y()-in.position().y();
        // A real intentional fall may carry feet below intermediate descent nodes before water
        // damping takes effect. Advance only to an independently swept-clear descending water leg.
        while(state==State.TRAVELLING&&in.inWater()&&cursor<nodes.size()-1&&target.y()>in.position().y()+.6
                &&geometry.water(target)&&nodes.get(cursor+1).y()<=target.y()&&geometry.water(nodes.get(cursor+1))
                &&geometry.connection(in.position(),nodes.get(cursor+1))) {
            target=nodes.get(++cursor);horizontal=in.position().horizontalDistanceTo(target);dy=target.y()-in.position().y();best=Double.POSITIVE_INFINITY;progressAt=in.now();
        }
        while(state!=State.REFILLING&&cursor<nodes.size()-1&&in.position().distanceTo(target)<.55&&geometry.connection(in.position(),nodes.get(cursor+1))) {
            target=nodes.get(++cursor);horizontal=in.position().horizontalDistanceTo(target);dy=target.y()-in.position().y();best=Double.POSITIVE_INFINITY;progressAt=in.now();
        }
        if(state==State.SURFACING||state==State.REFILLING)detourIndex=cursor;else index=cursor;
        boolean finalNode=cursor==nodes.size()-1;
        if(state==State.SURFACING&&finalNode&&!in.submerged()&&horizontal<.35) {
            state=State.REFILLING;refillAt=in.now();reason="Refilling observed air";
        }
        boolean strictTerminal=state==State.TRAVELLING&&Double.isFinite(terminalRadius);
        boolean groundGoal=geometry.ground(target),atGoal=finalNode&&horizontal<(strictTerminal?terminalRadius:.3)&&Math.abs(dy)<(groundGoal?.12:.25)
                &&(!strictTerminal||in.position().distanceTo(route.getLast())<terminalRadius);
        if(state==State.TRAVELLING&&atGoal&&(groundGoal?in.onGround():in.inWater())
                &&in.position().distanceTo(route.getLast())<(strictTerminal?terminalRadius:.4)&&Math.hypot(in.velocity().x(),in.velocity().z())<.025
                &&(groundGoal&&in.onGround()&&geometry.ground(in.position())||Math.abs(in.velocity().y())<.06)) {
            state=State.ARRIVED;reason="Observed dry/water goal reached";return release(in.yaw());
        }
        double distance=in.position().distanceTo(target);
        if(distance<best-.035) { best=distance;progressAt=in.now(); }
        if(state!=State.REFILLING&&in.now()-progressAt>Math.max(1000,stallMs)) { block("Water/drop progress stalled");return release(in.yaw()); }
        float desired=horizontal>.4?(float)Math.toDegrees(Math.atan2(-(target.x()-in.position().x()),target.z()-in.position().z())):in.yaw();
        float yaw=heading.step(in.yaw(),desired,speed);double angle=Math.toRadians(yaw);
        if(state==State.TRAVELLING&&atGoal&&groundGoal&&in.onGround()&&geometry.ground(in.position())) {
            // Ground friction settles ordinary landing momentum. Full opposite key pulses can
            // alternate indefinitely around a close supported goal instead of letting it settle.
            return new Output(state,yaw,false,false,false,false,false,false);
        }
        double gain=finalNode?.16:.25,limit=in.inWater()?.12:.18;
        double wx=atGoal?0:(target.x()-in.position().x())*gain,wz=atGoal?0:(target.z()-in.position().z())*gain,n=Math.hypot(wx,wz);
        if(n>limit) { wx*=limit/n;wz*=limit/n; }
        double ex=wx-in.velocity().x(),ez=wz-in.velocity().z();
        double forward=-Math.sin(angle)*ex+Math.cos(angle)*ez,side=Math.cos(angle)*ex+Math.sin(angle)*ez;
        boolean exitJump=in.inWater()&&groundGoal&&dy>.2;
        // Latch normal ascent until the actual eyes are clear, then float without vertical keys.
        // Continuous space at shallow immersion can trigger vanilla's full surface jump.
        boolean breathing=(state==State.REFILLING||state==State.SURFACING&&finalNode&&horizontal<.35);
        boolean jump=in.inWater()&&(breathing?in.submerged():exitJump||dy>.2);
        // Crouch applies native ledge protection while grounded, even in shallow water. A lower
        // target must first leave its ledge normally; diving input is only for actual immersion.
        boolean sneak=!breathing&&in.inWater()&&!in.onGround()&&!jump&&dy<-.2&&in.automaticSneak();
        if(!in.inWater()&&in.onGround()&&dy>.6) {
            jump=Math.abs(WalkController.wrap(desired-yaw))<15;
        }
        if(state!=State.SURFACING&&state!=State.REFILLING)reason="Dry/water travel "+(index+1)+"/"+route.size();
        if(!in.inWater()&&in.onGround()&&!atGoal&&horizontal>.6) {
            boolean aligned=Math.abs(WalkController.wrap(desired-yaw))<15;
            return new Output(state,yaw,aligned,false,false,false,jump&&aligned,false);
        }
        if(in.inWater()&&!finalNode&&state!=State.REFILLING&&horizontal>.35) {
            boolean aligned=Math.abs(WalkController.wrap(desired-yaw))<15;
            // Keep ordinary forward held along a clear intermediate leg. Velocity countersteering
            // remains available while turning and on the final approach, without a per-tick pulse.
            return new Output(state,yaw,aligned||forward>.025,!aligned&&forward<-.025,side>.025,side<-.025,jump,sneak);
        }
        // The legacy projected-key deadzone is sized for its .3 horizontal arrival bound.
        // A caller's tighter final receipt scales only that deadzone while still outside its goal;
        // retain native gain, vector polarity, ordinary keys and inside-goal friction settling.
        double keyThreshold=strictTerminal&&finalNode&&!atGoal?.025*(terminalRadius/.3):.025;
        return new Output(state,yaw,forward>keyThreshold,forward<-keyThreshold,side>keyThreshold,side<-keyThreshold,jump,sneak);
    }
    /** Optional retained terminal input; no heading or camera publication. */
    public Output tickTerminalHold(Input in,Geometry geometry){
        if(!terminalHoldReady()||!in.available()||in.lava()||!Float.isFinite(in.health())||in.health()<=0||in.health()<minimumHealth-.01f
                ||in.air()<=0||in.submerged()&&in.air()<=Math.max(40,in.maxAir()*.4)){
            block("Retained terminal ownership, health or breathing unavailable");return release(in.yaw());
        }
        Vec3 target=route.getLast();
        if(!geometry.connection(in.position(),target)||!geometry.ground(target)&&!geometry.water(target)
                ||!geometry.ground(in.position())&&!geometry.water(in.position())){
            block("Retained terminal body/support obstructed or unloaded");return release(in.yaw());
        }
        minimumHealth=in.health();
        double dx=target.x()-in.position().x(),dz=target.z()-in.position().z();
        double wx=dx*.16,wz=dz*.16,n=Math.hypot(wx,wz),limit=in.inWater()?.12:.18;
        if(n>limit){wx*=limit/n;wz*=limit/n;}
        double angle=Math.toRadians(in.yaw()),ex=wx-in.velocity().x(),ez=wz-in.velocity().z();
        double forward=-Math.sin(angle)*ex+Math.cos(angle)*ez,side=Math.cos(angle)*ex+Math.sin(angle)*ez;
        double threshold=.025*(terminalRadius/.3);
        boolean settled=in.position().distanceTo(target)<terminalRadius&&Math.hypot(in.velocity().x(),in.velocity().z())<.025
                &&(geometry.ground(target)?in.onGround()&&geometry.ground(in.position()):in.inWater()&&Math.abs(in.velocity().y())<.06);
        // Internal ARRIVED retains the exact completed route receipt; output reports current centering.
        reason=settled?"Retained terminal position contained":"Centering retained terminal position";
        return new Output(settled?State.ARRIVED:State.TRAVELLING,in.yaw(),forward>threshold,forward<-threshold,side>threshold,side<-threshold,false,false);
    }
    public boolean terminalHoldReady(){return state==State.ARRIVED&&!route.isEmpty()&&Double.isFinite(terminalRadius);}
    public double terminalRadius(){return terminalRadius;}
    private void block(String why) { state=State.BLOCKED;reason=why; }
    private Output release(float yaw) { return new Output(state,yaw,false,false,false,false,false,false); }
    /** Immutable concrete waypoints, both cursors, phase, health, timers and heading. */
    public dev.veltraph.client.farming.module.FarmMaintenanceState maintenanceState() {
        return dev.veltraph.client.farming.module.FarmMaintenanceState.of(this,
                "route",route,"detour",detour,"index",index,"detourIndex",detourIndex,"state",state,
                "reason",reason,"minimumHealth",minimumHealth,"best",best,"progressAt",progressAt,
                "refillAt",refillAt,"terminalRadius",terminalRadius,"heading",heading.maintenanceState());
    }
    /** Recheck retained geometry without advancing either cursor or phase. */
    public boolean maintenanceGeometryClear(Vec3 anchor,Geometry geometry) {
        if(state!=State.TRAVELLING&&state!=State.SURFACING&&state!=State.REFILLING)return false;
        var nodes=(state==State.SURFACING||state==State.REFILLING)?detour:route;
        int cursor=(state==State.SURFACING||state==State.REFILLING)?detourIndex:index;
        if(route.isEmpty()||index<0||index>=route.size()||detourIndex<0||detourIndex>detour.size()||anchor==null||cursor<0||cursor>=nodes.size()||!geometry.connection(anchor,nodes.get(cursor)))return false;
        // Preserve the concrete original route and detour; certify all remaining edges.
        for(int i=index;i<route.size();i++) {
            if(!geometry.ground(route.get(i))&&!geometry.water(route.get(i)))return false;
            if(i>index&&!geometry.connection(route.get(i-1),route.get(i)))return false;
        }
        for(int i=detourIndex;i<detour.size();i++) {
            if(!geometry.ground(detour.get(i))&&!geometry.water(detour.get(i)))return false;
            if(i>detourIndex&&!geometry.connection(detour.get(i-1),detour.get(i)))return false;
        }
        return true;
    }
    public State state() { return state; }
    public String status() { return reason; }
    public void stop() { state=State.IDLE;route=detour=List.of();heading.reset();reason="Stopped"; }
}
