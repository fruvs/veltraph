package dev.veltraph.client.mining;

import dev.veltraph.client.config.*;
import dev.veltraph.client.world.BlockPos;
import java.util.List;

/** Recoverable mining loop. A changed loaded block confirms removal; attack never edits the world. */
public final class MiningController {
    public enum State { READY, WALKING, AIMING, BREAKING, SETTLING, PAUSED, COMPLETE }
    private final MiningMode mode;
    private final MiningServices services;
    private final ConfigService config;
    private List<EchoCheckpoint> route=List.of();
    private int checkpoint, removed, settleTicks;
    private boolean forward=true;
    private Object useCaller=this;
    private int useActivation;
    private dev.veltraph.client.world.Direction cobbleFacing;
    private BlockPos pending, timedOut, recentlyRemoved, previousObservedBlock;
    private String pendingId="", reason="Ready", worldIdentity="";
    private boolean worldChanged,capacityBlocked;
    private Object scheduledTicket,resourceTicket;
    private long breakStarted;
    private State state=State.READY;
    public MiningController(MiningMode mode,MiningServices services,ConfigService config) {
        this.mode=mode;this.services=services;this.config=config;
    }
    public void start() {
        if(scheduledTicket!=null||resourceTicket!=null)throw new IllegalStateException("Scheduled mining engine is preserved");
        stop();removed=0;forward=true;timedOut=null;recentlyRemoved=null;settleTicks=0;worldChanged=capacityBlocked=false;
        String nextWorld=services.worldIdentity();
        if(!nextWorld.equals(worldIdentity))checkpoint=0;
        worldIdentity=nextWorld;
        if(mode==MiningMode.ECHO) {
            var next=EchoCheckpoint.parse(config.get(MiningConfig.ECHO_ROUTE));
            if(!next.equals(route) || !config.get(MiningConfig.ECHO_REMEMBER_LAST_SESSION))checkpoint=0;
            route=next;
        }
        cobbleFacing=services.farm().player().facing();state=State.READY;reason="Ready";
    }
    public void stop() {
        if(scheduledTicket!=null||resourceTicket!=null)return;
        holdUse(false);services.farm().breaking().setBreaking(false);services.stopWalking();
        services.farm().input().releaseAllMovement();services.farm().rotation().cancelRotation();pending=null;previousObservedBlock=null;
    }
    /** Preserve one continuous native hold instead of pulsing release/press on every tick. */
    private void holdUse(boolean held) {
        // Continuous desired holds still cross the adapter's fresh custody boundary every frame.
        // Its producer writes only actual edges, so this validation never pulses a healthy hold.
        if(held || services.ownedUseHeld(useCaller,useActivation))services.setUseHeld(useCaller,useActivation,held);
    }
    /** Only the containing module binds its exact activation before start. */
    public void bindUseActivation(Object caller,int activation){useCaller=java.util.Objects.requireNonNull(caller);useActivation=activation;}
    /** Root response admission clears only this engine's use lease, preserving borrowed controls. */
    public void suspendOwnedUse(){if(scheduledTicket==null&&resourceTicket==null)holdUse(false);}
    private void pause(String why) { capacityBlocked=false;stop();state=State.PAUSED;reason=why; }
    private void complete(String why) { stop();state=State.COMPLETE;reason=why; }
    public void resetAtRewarp() { if(scheduledTicket!=null||resourceTicket!=null)return; if(config.get(MiningConfig.ECHO_RESET_AT_REWARP)){checkpoint=0;stop();} }
    /** Observe the actual loaded generator change before this cycle's inventory readiness.
     * No control effect or time delay: only an accepted craft/rest loan stops safe motion. */
    public boolean observeCobbleInventoryBoundary() {
        if(mode!=MiningMode.COBBLESTONE||scheduledTicket!=null||resourceTicket!=null||pending==null
                ||worldChanged||!services.available()||!worldIdentity.equals(services.worldIdentity())
                ||!services.farm().world().isLoaded(pending)
                ||pendingId.equals(services.farm().world().blockId(pending)))return false;
        removed++;recentlyRemoved=pending;pending=null;state=State.READY;
        reason="Generator material changed; checking actual inventory";
        return true;
    }
    public void tick() {
        if(scheduledTicket!=null || resourceTicket!=null || state==State.COMPLETE)return;
        var farm=services.farm();
        if(!services.available()){pause("Player/world unavailable or screen open");return;}
        if(!worldIdentity.equals(services.worldIdentity()))worldChanged=true;
        if(worldChanged){pause("World changed; restart mining after inspecting the new world");return;}
        var observed=farm.player().blockPos();
        boolean stable=observed.equals(previousObservedBlock);
        previousObservedBlock=observed;
        if(pending!=null) {
            if(!farm.world().isLoaded(pending)){pause("Target chunk unloaded");return;}
            if(!farm.world().blockId(pending).equals(pendingId)) {
                if(mode==MiningMode.COBBLESTONE)observeCobbleInventoryBoundary();
                else {
                    removed++;recentlyRemoved=pending;pending=null;farm.breaking().setBreaking(false);services.stopWalking();
                    if(mode==MiningMode.ECHO)checkpoint++;
                    settleTicks=30;state=State.SETTLING;reason="Collecting drops / settling";
                }
            }
        }
        if(settleTicks>0) { holdUse(false);farm.breaking().setBreaking(false);services.collectDrops(recentlyRemoved);settleTicks--;state=State.SETTLING;
            if(settleTicks==0){services.stopWalking();state=State.READY;reason="Drop phase completed";}return; }
        if(state==State.SETTLING)services.stopWalking();
        if(!farm.player().onGround()){pause("Waiting for supported landing");return;}
        if(!config.get(MiningConfig.AUTO_BREAK_MINING)){pause("Auto break disabled");return;}
        BlockPos target;
        if(mode==MiningMode.MINE_DOWN) {
            target=farm.player().blockPos().down();
            if(target.y()<config.get(MiningConfig.MINING_MIN_Y)){complete("Minimum Y reached");return;}
        } else if(mode==MiningMode.ECHO) {
            if(route.isEmpty()){pause("Configure an Echo route");return;}
            if(checkpoint>=route.size()){complete("Echo route complete");return;}
            if(stable && config.get(MiningConfig.ECHO_ALLOW_CHECKPOINT_SKIPPING)) {
                for(int i=checkpoint+1;i<route.size();i++)
                    if(observed.equals(route.get(i).standing()) && at(route.get(i).standing()) && farm.world().isLoaded(route.get(i).standing()) && farm.world().isLoaded(route.get(i).target())){checkpoint=i;break;}
            }
            var point=route.get(checkpoint);
            if(!farm.world().isLoaded(point.standing()) || !farm.world().isLoaded(point.target())){
                pause("Echo checkpoint chunk unloaded");return;
            }
            if(!at(point.standing())) {
                farm.breaking().setBreaking(false);pending=null;
                if(!services.walkTo(point.standing())){pause("Echo path blocked: "+services.navigationStatus());return;}
                state=State.WALKING;reason="Echo checkpoint "+(checkpoint+1);return;
            }
            services.stopWalking();target=point.target();
            if(farm.world().isPassable(target)){checkpoint++;state=State.READY;reason="Checkpoint already clear";return;}
        } else {
            target=farm.breaking().targetBlockPos().filter(p->!p.equals(farm.player().blockPos().down()))
                    .filter(p->farm.world().isLoaded(p)&&farm.world().isBlock(p,"minecraft:cobblestone")).orElse(null);
            // Local bounded acquisition, constrained to the starting face and feet/head cells.
            // This retargets regeneration or the second wall block without touching supporting floor.
            if(target==null && cobbleFacing!=null) {
                for(int distance=1;distance<=3 && target==null;distance++)for(int height:new int[]{0,1,-1}) {
                    var candidate=farm.player().blockPos().offset(cobbleFacing,distance).up(height);
                    if(farm.world().isLoaded(candidate)&&farm.world().isBlock(candidate,"minecraft:cobblestone")){target=candidate;break;}
                }
            }
            if(target==null){
                farm.breaking().setBreaking(false);state=State.READY;
                reason="Waiting for generated cobblestone on the starting face";
                // AIR never authorizes attack. A genuine nearby removal can retain
                // source-direction movement only through fresh native corridor/tool gates.
                boolean witnessed=recentlyRemoved!=null&&farm.world().isLoaded(recentlyRemoved)
                        &&java.util.Set.of("minecraft:air","minecraft:cave_air","minecraft:void_air").contains(farm.world().blockId(recentlyRemoved))
                        &&farm.world().isPassable(recentlyRemoved)
                        &&farm.player().position().horizontalDistanceTo(new dev.veltraph.client.world.Vec3(recentlyRemoved.x()+.5,recentlyRemoved.y(),recentlyRemoved.z()+.5))<=3.5
                        &&Math.abs(farm.player().position().y()-recentlyRemoved.y())<=1.5;
                if(witnessed&&services.heldToolReady(recentlyRemoved,Math.max(1,config.get(MiningConfig.MINING_DURABILITY_RESERVE)))
                        &&services.hasCobbleRegenerationCapacity(recentlyRemoved))cobbleMotion();
                else {holdUse(false);services.stopWalking();farm.input().releaseAllMovement();farm.rotation().cancelRotation();}
                return;
            }
        }
        boolean floor=mode==MiningMode.MINE_DOWN || target.equals(farm.player().blockPos().down());
        String issue=services.targetProblem(target,floor);
        if(!issue.isEmpty()){pause(issue);return;}
        issue=services.selectTool(target,Math.max(1,config.get(MiningConfig.MINING_DURABILITY_RESERVE)));
        if(!issue.isEmpty()){pause(issue);return;}
        if(!services.hasCapacity(target)){pause("Inventory has no drop capacity");capacityBlocked=mode==MiningMode.COBBLESTONE;return;}
        if(target.equals(timedOut)){pause("Break timed out; restart after inspecting target");return;}
        if(mode!=MiningMode.COBBLESTONE)farm.input().releaseAllMovement();
        farm.rotation().lookAt(new dev.veltraph.client.world.Vec3(target.x()+.5,target.y()+.5,target.z()+.5));
        if(!services.aimedAt(target)) {holdUse(false);farm.breaking().setBreaking(false);farm.input().releaseAllMovement();state=State.AIMING;reason="Aiming at "+target;return;}
        if(pending==null || !pending.equals(target)) {pending=target;pendingId=farm.world().blockId(target);breakStarted=farm.clock().nowMillis();}
        if(farm.clock().nowMillis()-breakStarted>=Math.max(250,config.get(MiningConfig.MINING_BREAK_TIMEOUT_MS))) {
            timedOut=target;pause("Block did not break before timeout");return;
        }
        farm.breaking().setBreaking(true);state=State.BREAKING;reason="Mining "+pendingId;
        if(mode==MiningMode.COBBLESTONE)cobbleMotion();else holdUse(false);
    }
    private void cobbleMotion() {
        var input=services.farm().input();
        if(config.get(MiningConfig.COBBLE_AUTO_WALK)&&config.get(MiningConfig.COBBLE_AUTO_CHANGE_DIR)&&services.endWall(forward))forward=!forward;
        boolean walking=config.get(MiningConfig.COBBLE_AUTO_WALK)&&services.safeStep(forward);
        // Absolute desired holds preserve a healthy forward/back edge across material
        // regeneration. Every publication still crosses the original native input guard.
        input.setLeftHeld(false);input.setRightHeld(false);input.setSneakHeld(false);input.setJumpHeld(false);
        input.setForwardHeld(walking&&forward);input.setBackHeld(walking&&!forward);
        input.setSprintHeld(walking&&forward&&config.get(MiningConfig.COBBLE_AUTO_SPRINT));
        holdUse(walking&&forward&&config.get(MiningConfig.COBBLE_HOLD_RIGHT_CLICK));
    }
    private boolean at(BlockPos p) {
        var current=services.farm().player().position();
        return current.horizontalDistanceTo(new dev.veltraph.client.world.Vec3(p.x()+.5,p.y(),p.z()+.5))<.28
                && Math.abs(current.y()-p.y())<.12;
    }
    /** Interaction-boundary predicate; prevents a stale held attack from breaking a new crosshair block. */
    public boolean allowsTarget(BlockPos actual) {
        if(scheduledTicket!=null || resourceTicket!=null || state!=State.BREAKING || pending==null || !pending.equals(actual) || !services.available()
                || worldChanged || !worldIdentity.equals(services.worldIdentity())
                || !services.farm().world().isLoaded(actual) || !pendingId.equals(services.farm().world().blockId(actual)))return false;
        boolean floor=mode==MiningMode.MINE_DOWN || actual.equals(services.farm().player().blockPos().down());
        return services.targetProblem(actual,floor).isEmpty() && services.hasCapacity(actual)
                && services.heldToolReady(actual,Math.max(1,config.get(MiningConfig.MINING_DURABILITY_RESERVE)));
    }
    /** Only quiescent between-operation states may lend the engine to scheduling. */
    public boolean scheduledBoundary(){return resourceTicket==null&&pending==null && settleTicks==0 && !worldChanged
            && (state==State.READY || state==State.AIMING)
            && (mode!=MiningMode.ECHO || checkpoint<route.size())
            && (mode!=MiningMode.MINE_DOWN || services.farm().player().blockPos().down().y()>=config.get(MiningConfig.MINING_MIN_Y));}
    public boolean pauseForScheduledRest(Object ticket){
        if(ticket==null || scheduledTicket!=null || !scheduledBoundary() || !worldIdentity.equals(services.worldIdentity()))return false;
        holdUse(false);scheduledTicket=ticket;return true;
    }
    public boolean scheduledOwnedBy(Object ticket){return ticket!=null && scheduledTicket==ticket;}
    public boolean rebindForScheduledRest(Object ticket){
        if(!scheduledOwnedBy(ticket)||!scheduledBoundary())return false;
        worldIdentity=services.worldIdentity();worldChanged=false;return true;
    }
    public boolean resumeScheduledRest(Object ticket){
        if(!scheduledOwnedBy(ticket)||!scheduledBoundary()||!worldIdentity.equals(services.worldIdentity()))return false;
        scheduledTicket=null;return true;
    }
    public void cancelScheduledRest(Object ticket){if(scheduledOwnedBy(ticket))scheduledTicket=null;}
    public boolean scheduledGeometryReady(){
        var farm=services.farm();
        if(mode==MiningMode.MINE_DOWN){var target=farm.player().blockPos().down();
            return farm.world().isLoaded(target)&&services.targetProblem(target,true).isEmpty();}
        if(mode==MiningMode.ECHO)return checkpoint<route.size()&&farm.world().isLoaded(route.get(checkpoint).standing())&&farm.world().isLoaded(route.get(checkpoint).target());
        return farm.world().isLoaded(farm.player().blockPos())&&farm.world().isLoaded(farm.player().blockPos().down());
    }
    /** A menu workflow may borrow only this healthy engine at a between-operation boundary. */
    public boolean ownedSubflowReady(){return scheduledTicket==null&&resourceTicket==null
            &&(scheduledBoundary()||mode==MiningMode.COBBLESTONE&&capacityBlocked&&pending==null&&state==State.PAUSED)
            &&services.available()&&!worldChanged&&worldIdentity.equals(services.worldIdentity());}
    public boolean beginOwnedSubflow(Object ticket){if(ticket==null||resourceTicket!=null||!ownedSubflowReady())return false;stop();capacityBlocked=false;resourceTicket=ticket;return true;}
    public boolean endOwnedSubflow(Object ticket){if(ticket==null||resourceTicket!=ticket)return false;resourceTicket=null;if(!worldIdentity.equals(services.worldIdentity()))worldChanged=true;return true;}
    public boolean resourceOwnedBy(Object ticket){return ticket!=null&&resourceTicket==ticket;}
    /** Pending admission cannot carry its exact loan into a replaced native world/body. */
    public boolean resourceLoanCurrent(Object ticket){return resourceOwnedBy(ticket)&&services.available()&&!worldChanged&&worldIdentity.equals(services.worldIdentity());}
    public boolean scheduledPaused(){return scheduledTicket!=null;}
    public String scheduledProfile(){return mode+"|"+(mode==MiningMode.ECHO?route.toString():String.valueOf(cobbleFacing));}
    public boolean configuredProfileMatches(){return mode!=MiningMode.ECHO||route.equals(EchoCheckpoint.parse(config.get(MiningConfig.ECHO_ROUTE)));}
    public State state(){return state;}
    public String reason(){return reason;}
    public int removed(){return removed;}
    public int checkpoint(){return checkpoint;}
    public boolean forward(){return forward;}
}
