package dev.veltraph.client.navigation;

import java.util.Objects;
import java.util.function.BooleanSupplier;

/** A read-only route candidate cannot publish an input, camera plan or cleanup. */
public final class DormantGroundPublication implements GroundPublicationActor, GroundPublicationInputs {
    private final BooleanSupplier planningCurrent;
    public DormantGroundPublication(BooleanSupplier planningCurrent) {
        this.planningCurrent = Objects.requireNonNull(planningCurrent);
    }
    @Override public GroundPublicationActor actor() { return this; }
    @Override public boolean current() { return planningCurrent.getAsBoolean(); }
    @Override public long publishYaw(float yaw) { return -1; }
    @Override public void cleanupOwned() { /* Candidate owns no native publication. */ }
    private static void refused() {
        throw new GroundPublicationRevokedException("Dormant ground geometry cannot publish input");
    }
    @Override public void setForwardHeld(boolean held) { refused(); }
    @Override public void setBackHeld(boolean held) { refused(); }
    @Override public void setLeftHeld(boolean held) { refused(); }
    @Override public void setRightHeld(boolean held) { refused(); }
    @Override public void setSneakHeld(boolean held) { refused(); }
    @Override public void setJumpHeld(boolean held) { refused(); }
    @Override public void setSprintHeld(boolean held) { refused(); }
    @Override public void releaseAllMovement() { refused(); }
}
