package dev.veltraph.client.foraging.hub;

import dev.veltraph.client.foraging.HubTreeRoute;
import dev.veltraph.client.world.BlockPos;
import dev.veltraph.client.world.Vec3;
import java.util.Optional;

/** Readable Hub entry → confirmed warp → anchors → checkpoint tree → retry parent.
 * Saved restore re-enters the area check, retaining target exclusion; it never restores stale input.
 * Server warp identity/receipt belongs to the explicit port, not a fabricated atlas or ready flag. */
public final class HubSourceLoop {
    public enum Phase { STOPPED, ENTRY, SEARCH, HARVEST, RETRY, DELAY_WARP, WAIT_WARP, BEGIN_ANCHORS, WALK_ANCHORS, BLOCKED }
    public enum Motion { ACTIVE, ARRIVED, FAILED }
    public interface Port extends HubTreePlanner.World {
        boolean inHub();
        boolean sourceStopRequested();
        BlockPos playerBlock();
        Vec3 playerPosition();
        boolean pathExists(BlockPos target);
        boolean startAnchorWalk(BlockPos target);
        Motion anchorMotion();
        /** Cached observation only; must not advance navigation or read a replacement as proof. */
        default String anchorDiagnostics(){return "";}
        boolean requestHubWarp();
        Motion hubWarpMotion();
        void release();
    }
    public record Saved(BlockPos previousCheckpoint,int walkRetries) {}
    private final Port port;
    private Phase phase=Phase.STOPPED;
    private BlockPos previous;
    private int retries,anchor;
    private long readyTick,ticks;
    private String reason="";
    public HubSourceLoop(Port port){this.port=port;}
    public void start(){port.release();previous=null;retries=0;ticks=0;reason="";phase=Phase.ENTRY;}
    public Saved save(){return new Saved(previous,retries);}
    public boolean restore(Saved saved){
        if(saved==null||saved.walkRetries()<0||saved.walkRetries()>4||port.sourceStopRequested())return false;
        port.release();previous=saved.previousCheckpoint();retries=saved.walkRetries();phase=Phase.ENTRY;reason="saved restore through Hub entry";return true;
    }
    public void stop(){port.release();phase=Phase.STOPPED;}
    public Optional<HubTreePlanner.Selection> tick(long now){
        ticks++;
        if(phase==Phase.STOPPED||phase==Phase.BLOCKED)return Optional.empty();
        if(port.sourceStopRequested()){block("Hub source stop condition");return Optional.empty();}
        switch(phase) {
            case ENTRY -> {if(port.inHub()&&port.pathExists(HubTreeRoute.AREA_PROBE)){phase=Phase.SEARCH;reason="already in Hub tree area";}else warp();}
            case SEARCH -> {
                var selected=HubTreePlanner.select(port,port.playerBlock(),previous,now);
                if(selected.isEmpty()){port.release();phase=Phase.DELAY_WARP;readyTick=ticks+10;reason="no eligible checkpoint tree; warp after 10 ticks";}
                else {phase=Phase.HARVEST;return selected;}
            }
            case RETRY -> {if(ticks>=readyTick){retries++;phase=Phase.SEARCH;}}
            case DELAY_WARP -> {if(ticks>=readyTick)warp();}
            case WAIT_WARP -> {
                var state=port.hubWarpMotion();
                if(state==Motion.FAILED)block("Hub warp rejected or timed out");
                else if(state==Motion.ARRIVED) {
                    if(!port.inHub()){block("warp receipt outside Hub");break;}
                    confirmedWarpArrival();
                }
            }
            case BEGIN_ANCHORS -> {
                if(!port.inHub()){block("Hub arrival context changed before anchors");break;}
                anchor=HubTreePlanner.entryIndex(port.playerPosition());startAnchor();
            }
            case WALK_ANCHORS -> {
                var state=port.anchorMotion();
                if(state==Motion.FAILED)block(anchorFailure("Hub entry anchor path failed"));
                else if(state==Motion.ARRIVED){anchor++;if(anchor>=HubTreeRoute.ENTRY_ANCHORS.size()){port.release();phase=Phase.SEARCH;reason="Hub anchors complete";}else startAnchor();}
            }
            default -> { }
        }
        return Optional.empty();
    }
    /** Consumes only the confirmed warp callback. Ordinary anchor movement begins on the next tick. */
    public boolean confirmedWarpArrival(){if(phase!=Phase.WAIT_WARP)return false;phase=Phase.BEGIN_ANCHORS;reason="confirmed Hub warp; entry anchors pending";return true;}
    /** Source sets previous log only after the path starts, not during candidate selection. */
    public void treeWalkStarted(BlockPos checkpoint){if(phase==Phase.HARVEST)previous=checkpoint;}
    public void treeWalkFailed(){
        if(phase!=Phase.HARVEST)return;port.release();
        if(retries>3){phase=Phase.DELAY_WARP;readyTick=ticks+1;reason="tree walk retry limit; rewarp";}
        else {phase=Phase.RETRY;readyTick=ticks+20;reason="retrying tree walk after 20 ticks";}
    }
    public void treeFinished(){if(phase==Phase.HARVEST){retries=0;phase=Phase.SEARCH;}}
    private void warp(){port.release();if(!port.requestHubWarp()){block("Hub warp contract unavailable");return;}phase=Phase.WAIT_WARP;reason="awaiting actual Hub warp receipt";}
    private void startAnchor(){if(!port.startAnchorWalk(HubTreeRoute.ENTRY_ANCHORS.get(anchor)))block(anchorFailure("Hub entry anchor path unavailable"));else {phase=Phase.WALK_ANCHORS;reason="walking Hub entry anchor "+anchor;}}
    private String anchorFailure(String reason){String details=port.anchorDiagnostics();return details==null||details.isBlank()?reason:reason+" | "+details;}
    public int entryAnchorIndex(){return anchor;}
    private void block(String reason){port.release();this.reason=reason;phase=Phase.BLOCKED;}
    /** A visible terminal failure has no input lease; observation does not restart or clear it. */
    public boolean permitsGameplayInput(){return phase!=Phase.BLOCKED&&phase!=Phase.STOPPED;}
    public Phase phase(){return phase;}
    public String reason(){return reason;}
    public BlockPos previousCheckpoint(){return previous;}
    public int walkRetries(){return retries;}
}
