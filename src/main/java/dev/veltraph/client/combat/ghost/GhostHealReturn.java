package dev.veltraph.client.combat.ghost;

import dev.veltraph.client.world.Vec3;

/** Acknowledged heal then exact physical standing/view return; issuing an item use is not healing. */
public final class GhostHealReturn {
    /** Existing exact recovery radius, also used by the Ghost caller's local final walk. */
    public static final double RETURN_POSITION_RADIUS=.18;
    public enum State { IDLE, AIM, WAIT_ACK, RETURN, RESTORE, COMPLETE, FAILED }
    public enum Action { NONE, AIM_DOWN, CAST, WALK_RETURN, RESTORE_VIEW, COMPLETE, FAILED }
    public record Pose(Vec3 position,float yaw,float pitch,int slot,double health,float absorption) {}
    private State state=State.IDLE;private Pose pose;private long started,castAt;private String status="Idle";private boolean castRefused;
    public void start(Pose pose,long now){this.pose=pose;started=now;state=State.AIM;castRefused=false;status="Aiming down for heal";}
    public Pose pose(){return pose;}public State state(){return state;}public String status(){return status;}
    /** No use occurred: restore the existing owned pose without inventing a healing receipt. */
    public void refuseCast(){if(state==State.WAIT_ACK){castRefused=true;state=State.RETURN;status="Heal skipped; returning owned view/slot";}}
    public boolean castRefused(){return castRefused;}
    public Action tick(Vec3 position,float yaw,float pitch,double hp,float absorption,boolean clear,boolean grounded,double speed,long now,long wait,long timeout){
        if(state==State.IDLE)return Action.NONE;if(state==State.COMPLETE)return Action.COMPLETE;if(state==State.FAILED)return Action.FAILED;
        if(now-started>Math.max(1000,timeout)){state=State.FAILED;status="Heal/return timed out";return Action.FAILED;}
        if(state==State.AIM){
            if(!clear){state=State.FAILED;status="Heal clearance blocked";return Action.FAILED;}
            if(Math.abs(pitch-80)>1||!grounded||speed>.02)return Action.AIM_DOWN;
            castAt=now;state=State.WAIT_ACK;status="Heal used once; awaiting health/shield acknowledgment";return Action.CAST;
        }
        if(state==State.WAIT_ACK){
            if(now-castAt<Math.max(0,wait)||hp<=pose.health()&&absorption<=pose.absorption())return Action.NONE;
            state=State.RETURN;status="Heal acknowledged; physically returning";
        }
        if(state==State.RETURN){if(position.distanceTo(pose.position())>RETURN_POSITION_RADIUS||!grounded||speed>.02)return Action.WALK_RETURN;state=State.RESTORE;status="Standing point reached; restoring view";}
        if(state==State.RESTORE){if(Math.abs(dev.veltraph.client.navigation.WalkController.wrap(yaw-pose.yaw()))>1||Math.abs(pitch-pose.pitch())>1)return Action.RESTORE_VIEW;state=State.COMPLETE;status="Heal, standing point and view restored";return Action.COMPLETE;}
        return Action.NONE;
    }
    public void reset(){state=State.IDLE;pose=null;castRefused=false;status="Idle";}
}
