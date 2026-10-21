package dev.veltraph.client.farming.movement.planner;

import dev.veltraph.client.farming.movement.TurnDirection;
import dev.veltraph.client.service.FarmServices;
import dev.veltraph.client.world.Vec3;

/** Fixed-view water-lane input kernel; distinct historical/SAIGO geometry is supplied by World. */
public final class MelonKingDeWaterPlanner {
    public enum State { FARMING, TRANSFERRING, BLOCKED }
    public interface World {
        /** Negative caller invocation fence; standalone and historical other designs retain their behavior. */
        default boolean currentForTick(){return true;}
        /** Only an active MKD caller needs individual original-frame negative key releases. */
        default boolean fencedRelease(){return false;}
        boolean supportedDesignPosition();
        boolean sideObstructed(TurnDirection side);
        boolean sideLoaded(TurnDirection side);
        boolean transferClear();
        /** Find the actual next water lane, including the creator's alternating 3/4-block spacing. */
        int nextLaneDistance(TurnDirection side);
        boolean cropTargeted();
        default double forwardCoordinate(Vec3 position) { return position.z(); }
        default boolean sprintWhileFarming() { return false; }
        /** Apply crop-bank contact pressure only while the authored lane needs it. */
        default boolean forwardWhileFarming() { return true; }
        /** SAIGO's authored corner cuts are entered while already holding the new return side. */
        default boolean reverseBeforeTransfer() { return false; }
        /** Motion relevant to proving the held side is physically blocked. */
        default double obstructionMotion(Vec3 current,Vec3 previous) { return current.horizontalDistanceTo(previous); }
    }
    private final FarmServices services;
    private final World world;
    private State state = State.FARMING;
    private TurnDirection side = TurnDirection.LEFT;
    private Vec3 blockedAt;
    private int stationaryTicks;
    private int transferTicks;
    private double targetRow;
    private String blocker = "idle";

    public MelonKingDeWaterPlanner(FarmServices services, World world) {
        this.services = services;
        this.world = world;
    }
    public void start(TurnDirection startingSide) {
        if (startingSide != TurnDirection.LEFT && startingSide != TurnDirection.RIGHT)
            throw new IllegalArgumentException("Water-lane start requires LEFT or RIGHT");
        state = State.FARMING;
        side = startingSide;
        blockedAt = null;
        stationaryTicks = transferTicks = 0;
        blocker = "starting water lane";
        release();
    }
    public void stop() { state = State.BLOCKED; blocker = "stopped"; release(); }
    public State state() { return state; }
    public TurnDirection side() { return side; }
    public String blocker() { return blocker; }
    public void pause(String reason) {
        if(!world.currentForTick())return;
        blockedAt = null;stationaryTicks = 0;blocker = reason;release();
    }
    /** Exact interrupted-pose return preserves traversal side/phase; only collision evidence expires. */
    public void resumeAtInterruptedPose() {
        blockedAt=null;stationaryTicks=0;release();
        blocker="resumed interrupted water-lane phase";
    }

    public void tick() {
        if(!world.currentForTick())return;
        if (state == State.BLOCKED) { release(); return; }
        boolean supported=world.supportedDesignPosition();if(!world.currentForTick())return;
        if(!supported){pause("outside loaded supported water design");return;}
        if (state == State.TRANSFERRING) {
            Vec3 position = services.player().position();if(!world.currentForTick())return;
            double coordinate = world.forwardCoordinate(position);if(!world.currentForTick())return;
            if (coordinate >= targetRow + 0.35 && coordinate <= targetRow + 0.75) {
                boolean reverse=world.reverseBeforeTransfer();if(!world.currentForTick())return;
                if(!reverse)side=side.opposite();
                state = State.FARMING;
                stationaryTicks = 0;
                blockedAt = null;
                transferTicks = 0;
            } else {
                if (++transferTicks > 160) {
                    state = State.BLOCKED; blocker = "water-lane transfer timed out"; release(); return;
                }
                if(!applyTransferMovement())return;
                if(!world.currentForTick())return;
                boolean clear=world.transferClear();if(!world.currentForTick())return;
                blocker=clear?"advancing to water row "+targetRow:"end transfer obstructed or unloaded";
                return;
            }
        }
        boolean sideLoaded=world.sideLoaded(side);if(!world.currentForTick())return;
        if(!sideLoaded){pause("lateral lane unloaded or unsupported");return;}
        Vec3 position=services.player().position();if(!world.currentForTick())return;
        boolean obstructed=world.sideObstructed(side);if(!world.currentForTick())return;
        if(obstructed){
            double motion=blockedAt==null?Double.POSITIVE_INFINITY:world.obstructionMotion(position,blockedAt);
            if(!world.currentForTick())return;
            if(motion<.05)stationaryTicks++;else stationaryTicks=0;
            blockedAt = position;
            if (stationaryTicks >= 2) {
                int spacing = world.nextLaneDistance(side);if(!world.currentForTick())return;
                if (spacing < 1) {
                    state = State.BLOCKED; blocker = "no next loaded water lane"; release(); return;
                }
                double coordinate=world.forwardCoordinate(position);if(!world.currentForTick())return;
                targetRow=Math.floor(coordinate)+spacing;
                boolean reverse=world.reverseBeforeTransfer();if(!world.currentForTick())return;
                if(reverse)side=side.opposite();
                state = State.TRANSFERRING;
                transferTicks = 0;
                if(!applyTransferMovement())return;
                if(!world.currentForTick())return;
                blocker="advancing to water row "+targetRow;
                return;
            }
        } else { blockedAt = null; stationaryTicks = 0; }
        boolean forward=world.forwardWhileFarming();if(!world.currentForTick())return;
        TurnDirection lateral=side;
        boolean sprint=world.sprintWhileFarming();if(!world.currentForTick())return;
        boolean attack=world.cropTargeted();if(!world.currentForTick())return;
        applyMovement(forward,lateral,sprint,attack);
        if(world.currentForTick())blocker="water lane "+side;
    }
    private boolean applyTransferMovement() {
        // Preserve original provider order and lazy short circuits, but never ask a
        // later provider to continue an invocation retired by an earlier callback.
        boolean forward=world.transferClear();if(!world.currentForTick())return false;
        boolean reverseSide=world.reverseBeforeTransfer();if(!world.currentForTick())return false;
        boolean loaded=false;
        if(reverseSide){loaded=world.sideLoaded(side);if(!world.currentForTick())return false;}
        TurnDirection lateral=reverseSide&&loaded?side:TurnDirection.FORWARD;
        boolean reverseSprint=world.reverseBeforeTransfer();if(!world.currentForTick())return false;
        boolean sprint=false;
        if(reverseSprint){sprint=world.sprintWhileFarming();if(!world.currentForTick())return false;}
        boolean reverseAttack=world.reverseBeforeTransfer();if(!world.currentForTick())return false;
        boolean attack=false;
        if(reverseAttack){attack=world.cropTargeted();if(!world.currentForTick())return false;}
        applyMovement(forward,lateral,sprint,attack);
        return world.currentForTick();
    }
    private void applyMovement(boolean forward,TurnDirection lateral,boolean sprint,boolean attack) {
        // Desired held state, not a release/re-press pulse. The shared adapter preserves that
        // state under Minecraft's hold and toggle accessibility preferences.
        if(!world.currentForTick())return;
        services.input().setForwardHeld(forward);if(!world.currentForTick())return;
        services.input().setBackHeld(false);if(!world.currentForTick())return;
        services.input().setLeftHeld(lateral==TurnDirection.LEFT);if(!world.currentForTick())return;
        services.input().setRightHeld(lateral==TurnDirection.RIGHT);if(!world.currentForTick())return;
        services.input().setJumpHeld(false);if(!world.currentForTick())return;
        services.input().setSneakHeld(false);if(!world.currentForTick())return;
        services.input().setSprintHeld(sprint);if(!world.currentForTick())return;
        services.breaking().setBreaking(attack);
    }
    private void release() {
        if(!world.currentForTick())return;
        boolean fenced=world.fencedRelease();if(!world.currentForTick())return;
        if(fenced) {
            // Match the actual adapter's Up/Down/Left/Right/Shift/Jump/Sprint order.
            services.input().setForwardHeld(false);if(!world.currentForTick())return;
            services.input().setBackHeld(false);if(!world.currentForTick())return;
            services.input().setLeftHeld(false);if(!world.currentForTick())return;
            services.input().setRightHeld(false);if(!world.currentForTick())return;
            services.input().setSneakHeld(false);if(!world.currentForTick())return;
            services.input().setJumpHeld(false);if(!world.currentForTick())return;
            services.input().setSprintHeld(false);if(!world.currentForTick())return;
        } else {services.input().releaseAllMovement();if(!world.currentForTick())return;}
        services.breaking().setBreaking(false);
    }

    /** Passive exact maintenance checkpoint. No services, geometry, clocks or planner callbacks run. */
    public dev.veltraph.client.farming.module.FarmMaintenanceState maintenanceState() {
        return dev.veltraph.client.farming.module.FarmMaintenanceState.of(this,
                "services", services,
                "world", world,
                "state", state,
                "side", side,
                "blockedAt", blockedAt,
                "stationaryTicks", stationaryTicks,
                "transferTicks", transferTicks,
                "targetRow", targetRow,
                "blocker", blocker);
    }
}
