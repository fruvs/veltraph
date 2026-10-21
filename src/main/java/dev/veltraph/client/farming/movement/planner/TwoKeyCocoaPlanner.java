package dev.veltraph.client.farming.movement.planner;

import dev.veltraph.client.config.LaneSide;
import dev.veltraph.client.world.Vec3;
import java.util.Objects;

/** Fixed-camera A/D Cocoa loop; authored trapdoor geometry supplies the actual corridor transfer. */
public final class TwoKeyCocoaPlanner {
    public enum State { ALIGNING, HARVESTING, END_PAUSE, BLOCKED, STOPPED }
    public record Facts(boolean loaded,boolean supported,boolean grounded,boolean aligned,
                        Vec3 position,double horizontalSpeed,boolean cocoaTarget,boolean harvestHold,long now){
        public Facts(boolean loaded,boolean supported,boolean grounded,boolean aligned,Vec3 position,
                     double horizontalSpeed,boolean cocoaTarget,long now){
            this(loaded,supported,grounded,aligned,position,horizontalSpeed,cocoaTarget,false,now);
        }
    }
    public record Output(boolean left,boolean right,boolean attack,State state){}
    private State state=State.STOPPED;
    private LaneSide side=LaneSide.LEFT;
    private Vec3 last;
    private long stillSince=-1,harvestSince=-1,harvestSettleUntil=-1,lastProgress,started;
    private double distance;
    private int ends;
    private String reason="Stopped";
    public void start(LaneSide side,Vec3 position,long now){
        this.side=Objects.requireNonNull(side);last=position;started=lastProgress=now;
        stillSince=harvestSince=harvestSettleUntil=-1;distance=0;ends=0;state=State.ALIGNING;reason="Aligning posted Cocoa camera";
    }
    public Output tick(Facts in){
        if(state==State.STOPPED||state==State.BLOCKED)return new Output(false,false,false,state);
        if(!Double.isFinite(in.position().x())||!Double.isFinite(in.position().y())||!Double.isFinite(in.position().z())
                ||!Double.isFinite(in.horizontalSpeed())||in.horizontalSpeed()<0)return block("Invalid player motion observation");
        if(!in.loaded()||!in.supported())return block("Authored loaded snow/slab corridor required");
        if(in.now()<started)return block("Clock moved backwards");
        if(!in.grounded()||!in.aligned()){
            stillSince=harvestSince=harvestSettleUntil=-1;last=in.position();lastProgress=in.now();state=State.ALIGNING;
            reason=in.grounded()?"Aligning posted yaw/pitch":"Waiting for ground contact";
            return new Output(false,false,false,state);
        }
        double moved=in.position().horizontalDistanceTo(last);last=in.position();
        if(moved>.002){distance+=moved;lastProgress=in.now();stillSince=harvestSince=-1;}
        if(in.harvestHold()){
            harvestSettleUntil=in.now()+100;
            if(harvestSince<0)harvestSince=in.now();
            if(in.now()-harvestSince>3000)return block("Native Cocoa harvest target did not clear");
            stillSince=-1;lastProgress=in.now();state=State.HARVESTING;
            reason=in.cocoaTarget()?"Waiting for native Cocoa ray harvest":"Waiting for cached ray to reach observed Cocoa";
            return new Output(false,false,in.cocoaTarget(),state);
        }
        harvestSince=-1;
        if(in.now()<harvestSettleUntil){
            stillSince=-1;lastProgress=in.now();state=State.HARVESTING;
            reason="Waiting for native ray refresh after Cocoa removal";
            return new Output(false,false,false,state);
        }
        boolean still=in.horizontalSpeed()<.006&&moved<.002;
        if(still){
            if(stillSince<0)stillSince=in.now();
            if(in.now()-stillSince>=300){
                if(distance<.75)return block("No verified traversal before wall stall");
                side=side==LaneSide.LEFT?LaneSide.RIGHT:LaneSide.LEFT;ends++;distance=0;
                stillSince=-1;lastProgress=in.now();state=State.END_PAUSE;reason="Reversed A/D after authored end stall";
                return new Output(false,false,false,state);
            }
        }else stillSince=-1;
        if(in.now()-lastProgress>5000)return block("Cocoa traversal made no progress");
        state=State.HARVESTING;reason="Fixed-camera "+side+" traversal";
        return new Output(side==LaneSide.LEFT,side==LaneSide.RIGHT,in.cocoaTarget(),state);
    }
    private Output block(String why){state=State.BLOCKED;reason=why;return new Output(false,false,false,state);}
    public void stop(){state=State.STOPPED;reason="Stopped";last=null;}
    public State state(){return state;}public LaneSide side(){return side;}public int completedEnds(){return ends;}
    public String status(){return state+" "+side+" ends="+ends+" | "+reason;}

    /** Passive exact maintenance checkpoint. No services, geometry, clocks or planner callbacks run. */
    public dev.veltraph.client.farming.module.FarmMaintenanceState maintenanceState() {
        return dev.veltraph.client.farming.module.FarmMaintenanceState.of(this,
                "state", state,
                "side", side,
                "last", last,
                "stillSince", stillSince,
                "harvestSince", harvestSince,
                "harvestSettleUntil", harvestSettleUntil,
                "lastProgress", lastProgress,
                "started", started,
                "distance", distance,
                "ends", ends,
                "reason", reason);
    }
}
