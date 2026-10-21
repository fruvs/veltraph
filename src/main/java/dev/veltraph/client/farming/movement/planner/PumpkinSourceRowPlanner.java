package dev.veltraph.client.farming.movement.planner;

import dev.veltraph.client.farming.movement.TurnDirection;
import dev.veltraph.client.service.FarmServices;
import dev.veltraph.client.world.Vec3;
import java.util.function.BooleanSupplier;
import java.util.function.DoubleConsumer;
import java.util.random.RandomGenerator;

/** Decoded lateral/turn sequence; original intState update rhythm is modeled as local farming ticks. */
public final class PumpkinSourceRowPlanner {
    public enum State { FARMING, ON_END_ROW }
    public interface World {
        boolean atRowEnd(TurnDirection side);
        default boolean movementSupported(TurnDirection side){return true;}
        default boolean atTurnCorner(Vec3 current,Vec3 previous){return true;}
    }
    private final FarmServices services;
    private final World world;
    private final BooleanSupplier aligned;
    private final DoubleConsumer turnTarget;
    private final RandomGenerator random;
    private State state=State.FARMING;
    private TurnDirection side=TurnDirection.LEFT;
    private float originalYaw;
    private int farmingTicks;
    private boolean turning;
    private Vec3 previousPosition;

    public PumpkinSourceRowPlanner(FarmServices services,World world,BooleanSupplier aligned,
                                   DoubleConsumer turnTarget,RandomGenerator random) {
        this.services=services;this.world=world;this.aligned=aligned;this.turnTarget=turnTarget;this.random=random;
    }
    public void start(float yaw){originalYaw=yaw;side=TurnDirection.LEFT;state=State.FARMING;farmingTicks=0;turning=false;previousPosition=null;release();}
    public void stop(){release();state=State.FARMING;previousPosition=null;turning=false;farmingTicks=0;}
    public State state(){return state;}
    public TurnDirection side(){return side;}
    public void tick() {
        Vec3 position=services.player().position();
        try {
            if(state==State.ON_END_ROW) {
                release();
                if(!turning) {
                    if(!world.atTurnCorner(position,previousPosition)) {
                        // Original ON_END_ROW releases forward/attack while retaining old side
                        // pressure until both current/previous coordinates reach its corner gate.
                        if(world.movementSupported(side))applySide();
                        return;
                    }
                    float yaw=side==TurnDirection.LEFT?originalYaw+108+random.nextFloat()*4:originalYaw;
                    side=side.opposite();turning=true;turnTarget.accept(yaw);return;
                }
                if(!aligned.getAsBoolean()||!services.player().onGround())return;
                state=State.FARMING;farmingTicks=0;turning=false;
            }
            if(++farmingTicks>=20&&world.atRowEnd(side)) {
                state=State.ON_END_ROW;turning=false;release();
                if(world.movementSupported(side))applySide();
                return;
            }
            release();
            if(!world.movementSupported(side))return;
            services.input().setForwardHeld(true);applySide();services.breaking().setBreaking(true);
        } finally {previousPosition=position;}
    }
    private void applySide(){services.input().setLeftHeld(side==TurnDirection.LEFT);services.input().setRightHeld(side==TurnDirection.RIGHT);}
    private void release(){services.input().releaseAllMovement();services.breaking().setBreaking(false);}

    /** Passive exact maintenance checkpoint. No services, geometry, clocks or planner callbacks run. */
    public dev.veltraph.client.farming.module.FarmMaintenanceState maintenanceState() {
        return dev.veltraph.client.farming.module.FarmMaintenanceState.of(this,
                "services", services,
                "world", world,
                "aligned", aligned,
                "turnTarget", turnTarget,
                "random", random,
                "state", state,
                "side", side,
                "originalYaw", originalYaw,
                "farmingTicks", farmingTicks,
                "turning", turning,
                "previousPosition", previousPosition);
    }
}
