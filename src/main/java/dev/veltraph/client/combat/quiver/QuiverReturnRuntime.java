package dev.veltraph.client.combat.quiver;

import dev.veltraph.client.world.Vec3;

/** Local safety adaptation: callers may resume only after physical return and captured camera restoration. */
public final class QuiverReturnRuntime {
    public record Pose(Vec3 position,float yaw,float pitch) {
        public Pose {if(position==null||!Double.isFinite(position.x())||!Double.isFinite(position.y())||!Double.isFinite(position.z())||!Float.isFinite(yaw)||!Float.isFinite(pitch))throw new IllegalArgumentException("Observed finite return pose required");}
    }
    public enum State { WALK, AIM, COMPLETE, FAILED }
    private final Pose destination;private final long deadline;private State state=State.WALK;private String reason="Returning to captured caller position";
    public QuiverReturnRuntime(Pose destination,long tick,int timeout){if(timeout<1||timeout>2400)throw new IllegalArgumentException("Bounded return timeout required");this.destination=destination;deadline=tick+timeout;}
    public Pose destination(){return destination;}public State state(){return state;}public String reason(){return reason;}
    public boolean atPosition(Vec3 observed){return observed!=null&&Math.hypot(observed.x()-destination.position().x(),observed.z()-destination.position().z())<=.25&&Math.abs(observed.y()-destination.position().y())<=.15;}
    public void observe(Pose observed,long tick,boolean owner,boolean inventoryUnchanged,boolean pathBlocked){
        if(state==State.COMPLETE||state==State.FAILED)return;
        if(!owner||!inventoryUnchanged||pathBlocked||tick>deadline){fail("Quiver physical return blocked, timed out or lost ownership; caller must stop");return;}
        if(!atPosition(observed.position())){if(state==State.AIM)fail("Captured return position changed while restoring camera");return;}
        state=State.AIM;reason="Restoring captured caller camera";
        double yaw=Math.abs(Math.IEEEremainder(observed.yaw()-destination.yaw(),360));
        if(yaw<=3&&Math.abs(observed.pitch()-destination.pitch())<=3){state=State.COMPLETE;reason="Refill acknowledged and caller position/camera restored";}
    }
    public void fail(String message){state=State.FAILED;reason=message;}
}
