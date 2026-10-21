package dev.veltraph.client.navigation;

import dev.veltraph.client.world.Vec3;
import java.util.List;

/** Recovered FlyToGoalHandler goal/candidate ownership and exact chunk-equality confirmation. */
public final class ChunkGoalController {
    public enum State { IDLE, WAITING_LOADED, TRAVELLING, ARRIVED, FAILED }
    public enum DriveState { ACTIVE, ARRIVED, FAILED }
    public interface Driver {
        boolean ready(Vec3 candidate);
        boolean start(Vec3 candidate);
        DriveState tick();
        void stop();
        default String status() { return ""; }
    }
    public record Input(Vec3 position,boolean available,boolean enabled,boolean calculating,long now) {}
    private State state=State.IDLE;private List<Vec3> candidates=List.of();private int x,z,index;private long started;
    private String reason="Idle";
    public boolean start(int x,int z,List<Vec3> standingPositions,long now) {
        if(standingPositions==null||standingPositions.isEmpty()||standingPositions.stream().anyMatch(p->!finite(p)||chunk(p.x())!=x||chunk(p.z())!=z)) {
            state=State.FAILED;reason="Standing positions must belong to the exact goal chunk";return false;
        }
        this.x=x;this.z=z;candidates=List.copyOf(standingPositions);index=0;started=now;state=State.WAITING_LOADED;reason="Waiting for loaded goal chunk";return true;
    }
    public State tick(Input in,Driver driver,long waitMs) {
        if(!in.available()||!in.enabled()||!finite(in.position())) { driver.stop();state=State.FAILED;reason="Chunk goal ownership or availability changed";return state; }
        if(state==State.IDLE||state==State.FAILED)return state;
        if(in.calculating()) {
            if(state==State.TRAVELLING)driver.stop();state=State.WAITING_LOADED;reason="Goal is still calculating";
            if(in.now()-started>Math.max(500,waitMs)) { state=State.FAILED;reason="Goal calculation/loaded wait expired"; }
            return state;
        }
        if(state==State.ARRIVED)return state;
        if(state==State.WAITING_LOADED) {
            while(index<candidates.size()&&!driver.ready(candidates.get(index))) {
                boolean anyLater=false;for(int i=index+1;i<candidates.size();i++)if(driver.ready(candidates.get(i))) { index=i;anyLater=true;break; }
                if(!anyLater)break;
            }
            if(index>=candidates.size()||!driver.ready(candidates.get(index))) {
                if(in.now()-started>Math.max(500,waitMs)) { state=State.FAILED;reason="Goal chunk/standing position remains unloaded or obstructed";driver.stop(); }
                return state;
            }
            if(chunk(in.position().x())==x&&chunk(in.position().z())==z) { state=State.ARRIVED;reason="Observed goal chunk reached";driver.stop();return state; }
            if(!driver.start(candidates.get(index))) { state=State.FAILED;reason="Unable to start permitted flight to goal chunk";driver.stop();return state; }
            state=State.TRAVELLING;reason="Travelling to goal chunk "+x+","+z;
        }
        var drive=driver.tick();
        if(drive==DriveState.FAILED) { state=State.FAILED;reason="Goal flight failed: "+driver.status();driver.stop(); }
        if(drive==DriveState.ARRIVED) {
            driver.stop();
            if(chunk(in.position().x())==x&&chunk(in.position().z())==z) { state=State.ARRIVED;reason="Observed goal chunk reached"; }
            else if(++index<candidates.size()&&index<=1) { state=State.WAITING_LOADED;started=in.now();reason="Arrival outside goal chunk; discarding old candidate"; }
            else { state=State.FAILED;reason="Arrival outside exact goal chunk"; }
        }
        return state;
    }
    public void stop(Driver driver) { driver.stop();state=State.IDLE;candidates=List.of();reason="Stopped"; }
    public State state() { return state; }
    public String status() { return reason; }
    public static int chunk(double coordinate) { return ((int)Math.floor(coordinate))>>4; }
    private static boolean finite(Vec3 p) { return p!=null&&Double.isFinite(p.x())&&Double.isFinite(p.y())&&Double.isFinite(p.z()); }
}
