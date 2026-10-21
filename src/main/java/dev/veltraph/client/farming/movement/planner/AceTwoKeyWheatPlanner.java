package dev.veltraph.client.farming.movement.planner;

import dev.veltraph.client.config.LaneSide;
import dev.veltraph.client.world.Vec3;
import java.util.Objects;

/** Posted Ace A/D+attack loop. Real authored collisions supply guide/ramp/layer transfers. */
public final class AceTwoKeyWheatPlanner {
    public enum State { ALIGNING, HARVESTING, DROPPING, END_TURN, BLOCKED, STOPPED }
    public record Facts(Vec3 position,double speed,boolean loaded,boolean bodyClear,
            boolean supported,boolean grounded,boolean wet,boolean safeLanding,boolean canTravel,
            boolean aligned,boolean wheatTarget,long now) {}
    public record Output(boolean left,boolean right,boolean attack,State state) {}
    private State state=State.STOPPED;
    private LaneSide side=LaneSide.RIGHT;
    private Vec3 last,lastGround;
    private double distance;
    private long lastProgress;
    private int stillTicks,ends,drops;
    private boolean primed;
    private String reason="Stopped";
    public void start(LaneSide side,Vec3 position,long now){
        this.side=Objects.requireNonNull(side);last=lastGround=position;lastProgress=now;
        distance=0;stillTicks=ends=drops=0;primed=false;state=State.ALIGNING;reason="Aligning posted Ace view";
    }
    public Output tick(Facts in){
        if(state==State.STOPPED||state==State.BLOCKED)return out(false);
        if(!in.loaded()||!in.bodyClear()||!Double.isFinite(in.speed())||in.speed()<0)
            return block("Loaded, clear authored corridor required");
        if(!in.aligned()){last=in.position();stillTicks=0;lastProgress=in.now();state=State.ALIGNING;return out(false);}
        double moved=in.position().horizontalDistanceTo(last);last=in.position();
        if(moved>.002){distance+=moved;lastProgress=in.now();stillTicks=0;}
        if(!in.grounded()&&!in.wet()){
            if(!in.safeLanding())return block("Airborne without loaded bounded landing");
            stillTicks=0;state=State.DROPPING;reason="Native A/D toward verified landing guide";
            return in.canTravel()?out(in.wheatTarget()):new Output(false,false,false,state);
        }
        if(!in.supported()&&!in.wet()){
            if(!in.safeLanding()||!in.canTravel())return block("Original fractional support missing");
            stillTicks=0;state=State.HARVESTING;reason="Continuous native transfer to verified next guide";
            return out(in.wheatTarget());
        }
        if(in.grounded()){
            if(lastGround.y()-in.position().y()>=1.5)drops++;
            lastGround=in.position();
        }
        if(!primed){
            if(!in.canTravel())return block("No supported direction at Ace entry");
            primed=true;state=State.HARVESTING;reason="Starting posted A/D input";return out(in.wheatTarget());
        }
        if(!in.canTravel()||moved<.002&&in.speed()<.006){
            if(++stillTicks>=2){
                if(distance<.75)return block("No observed traversal before blocked end");
                side=side==LaneSide.LEFT?LaneSide.RIGHT:LaneSide.LEFT;ends++;distance=0;stillTicks=0;
                lastProgress=in.now();state=State.END_TURN;reason="Observed authored end, reversed A/D";
                return out(in.wheatTarget());
            }
            state=State.END_TURN;reason="Confirming physical end";
            return new Output(false,false,false,state);
        }
        if(in.now()-lastProgress>10000)return block("No observed Ace route progress");
        state=State.HARVESTING;reason="Fixed-view "+side+" on original guides";
        return out(in.wheatTarget());
    }
    private Output out(boolean attack){
        boolean active=state!=State.STOPPED&&state!=State.BLOCKED&&state!=State.ALIGNING;
        return new Output(active&&side==LaneSide.LEFT,active&&side==LaneSide.RIGHT,active&&attack,state);
    }
    private Output block(String reason){state=State.BLOCKED;this.reason=reason;return out(false);}
    public void stop(){state=State.STOPPED;reason="Stopped";}
    public State state(){return state;} public LaneSide side(){return side;}
    public int completedEnds(){return ends;} public int observedDrops(){return drops;}
    public String status(){return state+" "+side+" ends="+ends+" drops="+drops+" | "+reason;}

    /** Passive exact maintenance checkpoint. No services, geometry, clocks or planner callbacks run. */
    public dev.veltraph.client.farming.module.FarmMaintenanceState maintenanceState() {
        return dev.veltraph.client.farming.module.FarmMaintenanceState.of(this,
                "state", state,
                "side", side,
                "last", last,
                "lastGround", lastGround,
                "distance", distance,
                "lastProgress", lastProgress,
                "stillTicks", stillTicks,
                "ends", ends,
                "drops", drops,
                "primed", primed,
                "reason", reason);
    }
}
