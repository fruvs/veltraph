package dev.veltraph.client.rewarp;

import dev.veltraph.client.game.GameArea;
import dev.veltraph.client.garden.pests.GardenPlotBounds;
import dev.veltraph.client.world.Vec3;
import java.util.Objects;

/** One explicit request, fresh native arrival receipt, then loaded supported configured-spawn proof. */
public final class AssignedPlotTravelRuntime {
    public enum State { IDLE, WAITING_ARRIVAL, ARRIVED, FAILED, CANCELLED }
    public interface Actions { boolean sendPlot(int plotId); }
    public record Observation(boolean valid,Object player,Object world,Object connection,String dimension,
            GameArea area,Vec3 position,boolean supported,long teleportSerial,Vec3 teleportPosition,long now) {}
    private final Actions actions;
    private AssignedPlotTravelPort.Target target;
    private Observation origin,previous;
    private State state=State.IDLE;
    private long seenSerial;
    private boolean received,acknowledged;
    private String reason="Idle";
    public AssignedPlotTravelRuntime(Actions actions) { this.actions=Objects.requireNonNull(actions); }
    private static boolean finite(Vec3 p) {return p!=null&&Double.isFinite(p.x())&&Double.isFinite(p.y())&&Double.isFinite(p.z());}
    private static boolean valid(Observation o) {return o!=null&&o.valid()&&o.player()!=null&&o.world()!=null&&o.connection()!=null
            &&o.dimension()!=null&&!o.dimension().isBlank()&&o.area()==GameArea.GARDEN&&finite(o.position());}
    private static boolean valid(AssignedPlotTravelPort.Target t) {return t!=null&&t.plotId()>0&&t.plotId()<=24&&finite(t.spawn())
            &&Float.isFinite(t.yaw())&&Float.isFinite(t.pitch())&&Math.abs(t.pitch())<=90
            &&t.spawn().y()>=60&&t.spawn().y()<92&&GardenPlotBounds.contains(t.plotId(),t.spawn().x(),t.spawn().z());}
    public boolean start(AssignedPlotTravelPort.Target requested,Observation observation) {
        cancel();
        if(!valid(requested)||!valid(observation)) {state=State.FAILED;reason="Configured plot or Garden owner unavailable";return false;}
        target=requested;origin=previous=observation;seenSerial=observation.teleportSerial();
        received=acknowledged=false;state=State.WAITING_ARRIVAL;reason="Requested configured plot "+target.plotId();
        try {if(!actions.sendPlot(target.plotId())) {state=State.FAILED;reason="Configured plot command unavailable";return false;}}
        catch(RuntimeException failure) {state=State.FAILED;reason="Configured plot command failed";return false;}
        return true;
    }
    private boolean owner(Observation o) {return valid(o)&&o.player()==origin.player()&&o.world()==origin.world()
            &&o.connection()==origin.connection()&&o.dimension().equals(origin.dimension());}
    private boolean atSpawn(Vec3 p) {return finite(p)&&GardenPlotBounds.contains(target.plotId(),p.x(),p.z())&&p.distanceTo(target.spawn())<=1.0;}
    public State tick(AssignedPlotTravelPort.Target selected,Observation observation) {
        if(!active())return state;
        if(!Objects.equals(target,selected)||!owner(observation)) {state=State.CANCELLED;reason="Configured plot or player/world/connection changed";return state;}
        if(observation.now()<previous.now()||observation.now()-origin.now()>=10000&&state!=State.ARRIVED) {
            state=State.FAILED;reason="Configured plot native arrival deadline expired";return state;
        }
        if(observation.teleportSerial()>seenSerial) {
            seenSerial=observation.teleportSerial();
            if(!atSpawn(observation.teleportPosition())) {state=State.FAILED;reason="Native teleport arrived outside configured plot spawn";return state;}
            received=true;
        }
        if(state==State.ARRIVED&&(!atSpawn(observation.position())||!observation.supported())) {
            state=State.FAILED;reason="Configured spawn support or pose changed before helper work";return state;
        }
        if(received&&atSpawn(observation.position())&&observation.supported()) {
            state=State.ARRIVED;reason="Fresh native configured plot arrival on loaded support";
        }
        previous=observation;return state;
    }
    public boolean consumeExpectedTeleport(AssignedPlotTravelPort.Target selected,Observation observation,Vec3 position,long now) {
        if(!active()||acknowledged||!Objects.equals(target,selected)||!owner(observation)||now<previous.now()
                ||now-origin.now()>=10000||!atSpawn(position)||!atSpawn(observation.teleportPosition())
                ||observation.teleportSerial()<=origin.teleportSerial())return false;
        acknowledged=true;return true;
    }
    public void onServerMessage(String message) {
        if(state!=State.WAITING_ARRIVAL||message==null)return;
        String text=message.replaceAll("§.","").strip();
        if(text.startsWith("Couldn't find Plot ")||text.startsWith("You have not unlocked Plot")) {
            state=State.FAILED;reason="Server rejected configured plot";
        }
        // Success text cannot substitute for a new native teleport receipt and physical support.
    }
    public boolean active() {return state==State.WAITING_ARRIVAL||state==State.ARRIVED;}
    public State state() {return state;}
    public void cancel() {state=State.IDLE;target=null;origin=previous=null;received=acknowledged=false;seenSerial=0;reason="Cancelled";}
    public String status() {return "Assigned plot travel: "+state+" | "+reason;}
}
