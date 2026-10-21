package dev.veltraph.client.rewarp;

import dev.veltraph.client.game.GameArea;
import dev.veltraph.client.garden.pests.GardenPlotBounds;
import dev.veltraph.client.world.Vec3;
import java.util.Objects;

/** Source long-distance target-plot request, followed by observed plot arrival before exact travel. */
public final class GardenPlotReturnRuntime {
    public enum State { IDLE, WAITING_ARRIVAL, READY_FOR_TRAVEL, REJECTED, FAILED, CANCELLED }
    public interface Actions {boolean sendPlot(int id);}
    public record Observation(boolean valid,Object world,String dimension,GameArea area,Vec3 position,long now) {
        public Observation {
            if(world==null||dimension==null||dimension.isBlank()||area!=GameArea.GARDEN||position==null
                    ||!Double.isFinite(position.x())||!Double.isFinite(position.y())||!Double.isFinite(position.z()))valid=false;
        }
    }
    private final Actions actions;
    private State state=State.IDLE;
    private Observation origin,previous;
    private int targetPlot=-1;
    private boolean observedStep;
    private String reason="none";
    public GardenPlotReturnRuntime(Actions actions){this.actions=Objects.requireNonNull(actions);}
    /** Geometry is recovered; it is not an assertion that the player's target plot is unlocked. */
    public static int targetPlot(FarmReturnPoint target,Observation observation){
        if(target==null||!observation.valid()||target.area()!=GameArea.GARDEN
                ||!target.dimension().equals(observation.dimension())||target.position().distanceTo(observation.position())<=120)return -1;
        int plot=GardenPlotBounds.at(target.position().x(),target.position().z()).orElse(-1);
        if(!interior(plot,target.position()))return -1;
        return plot>0?plot:-1; // Barn/spawn and unknown plots retain the existing local/rewarp policy.
    }
    public boolean start(FarmReturnPoint target,Observation observation){
        cancel();targetPlot=targetPlot(target,observation);
        if(targetPlot<0){state=State.IDLE;return false;}
        origin=previous=observation;observedStep=false;state=State.WAITING_ARRIVAL;reason="requested target plot "+targetPlot;
        try{if(!actions.sendPlot(targetPlot)){state=State.FAILED;reason="Target plot command unavailable";}}
        catch(RuntimeException failed){state=State.FAILED;reason="Target plot command failed";}
        return true;
    }
    public void tick(Observation now){
        if(!active())return;
        if(!now.valid()||now.world()!=origin.world()||!now.dimension().equals(origin.dimension())){
            state=State.CANCELLED;reason="Target plot owner/world/area changed";return;
        }
        if(now.now()<previous.now()||now.now()-origin.now()>=10000){
            state=State.FAILED;reason="Target plot arrival deadline expired";return;
        }
        if(state==State.READY_FOR_TRAVEL) {
            if(!interior(targetPlot,now.position())){state=State.FAILED;reason="Observed target plot drifted before physical travel";}
            previous=now;return;
        }
        if(previous.position().distanceTo(now.position())>2)observedStep=true;
        if(observedStep){
            if(interior(targetPlot,now.position())) {
                state=State.READY_FOR_TRAVEL;reason="observed target plot arrival";
            }else{state=State.FAILED;reason="Target plot command arrived in another plot";}
        }
        previous=now;
    }
    /** Only active request failures may authorize the source permitted-flight fallback. */
    public void onServerMessage(String line){
        if(state!=State.WAITING_ARRIVAL||line==null)return;
        String text=line.replaceAll("§.","").strip();
        if(text.startsWith("Couldn't find Plot ")||text.startsWith("You have not unlocked Plot")) {
            state=State.REJECTED;reason="Server rejected target plot; permitted flight fallback";
        }
        // A textual success alone never authorizes travel or macro resume.
    }
    public boolean active(){return state==State.WAITING_ARRIVAL||state==State.READY_FOR_TRAVEL;}
    /** Read-only narrow acknowledgment seam for the central unexpected-teleport guard. */
    public boolean expectsTeleport(Vec3 position,long now){
        return state==State.WAITING_ARRIVAL&&position!=null&&now>=previous.now()&&now-origin.now()<10000
                &&previous.position().distanceTo(position)>2
                &&interior(targetPlot,position);
    }
    // The source map gives integer corner blocks, not a recovered floating boundary predicate.
    // Use a conservative one-block interior for the command/ack seam; edge targets travel locally.
    private static boolean interior(int plot,Vec3 position){return position.y()>=60&&position.y()<92&&GardenPlotBounds.ALL.stream().anyMatch(b->b.id()==plot
            &&position.x()>=b.minX()+1&&position.x()<b.maxX()-1&&position.z()>=b.minZ()+1&&position.z()<b.maxZ()-1);}
    public State state(){return state;}
    public void cancel(){state=State.IDLE;origin=previous=null;targetPlot=-1;observedStep=false;}
    public String status(){return "plot-return: "+state+" | "+reason;}
}
