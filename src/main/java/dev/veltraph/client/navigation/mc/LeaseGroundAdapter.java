package dev.veltraph.client.navigation.mc;
import dev.veltraph.client.navigation.GroundPublicationActor;
import dev.veltraph.client.navigation.GroundPublicationInputs;
import java.util.Objects;

/** Exact actor/input pair; a refused key publication cannot become a successful legacy tick. */
public final class LeaseGroundAdapter implements GroundPublicationActor,GroundPublicationInputs {
    private final LegacyTravelPublicationLease.Leg leg;
    public LeaseGroundAdapter(LegacyTravelPublicationLease.Leg leg){this.leg=Objects.requireNonNull(leg);}
    @Override public GroundPublicationActor actor(){return this;}
    /** Actual producer delegates to this exact private-issued Leg, never a caller predicate. */
    public LegacyTravelPublicationLease.Leg.ProfileInputFrame profileInputFrame(){return leg.profileInputFrame();}
    public boolean ownsProfileFrame(LegacyTravelPublicationLease.Leg.ProfileInputFrame frame){return frame!=null&&frame.belongsTo(leg)&&frame.nativeCurrent();}
    /** Original private Leg snapshot: observation/admission checkpoint only, never new key authority. */
    public Object observationalSnapshot(){return leg.publicationSnapshot();}
    public boolean observationalSnapshotCurrent(Object exact){return leg.publicationCurrent(exact);}
    @Override public boolean current(){return leg.current();}
    @Override public long publishYaw(float yaw){return leg.publishYaw(yaw);}
    @Override public void cleanupOwned(){leg.releaseAllNineOwned();leg.cancelCameraOwned();}
    private static void admitted(boolean yes){if(!yes)throw new dev.veltraph.client.navigation.GroundPublicationRevokedException("Legacy ground publication custody revoked");}
    @Override public void setForwardHeld(boolean held){admitted(leg.setForwardHeld(held));}
    @Override public void setBackHeld(boolean held){admitted(leg.setBackHeld(held));}
    @Override public void setLeftHeld(boolean held){admitted(leg.setLeftHeld(held));}
    @Override public void setRightHeld(boolean held){admitted(leg.setRightHeld(held));}
    @Override public void setSneakHeld(boolean held){admitted(leg.setSneakHeld(held));}
    @Override public void setJumpHeld(boolean held){admitted(leg.setJumpHeld(held));}
    @Override public void setSprintHeld(boolean held){admitted(leg.setSprintHeld(held));}
    @Override public void releaseAllMovement(){admitted(leg.publishNeutralMovementOwned());}
}
