package dev.veltraph.client.navigation;

import dev.veltraph.client.rewarp.FarmMaintenanceLease;
import dev.veltraph.client.world.Vec3;
import java.util.Optional;
import java.util.Objects;

/** Pure role/lineage predicate. Only the native bridge supplies authentic coordinator observations. */
public final class MaintenanceMovementAuthorityFrame {
    public record Pair(Object coordinator,Object capture,Object sourcePolicy,Object farm,Object helper,
                       FarmMaintenanceLease.Token token,Object capturedPose,Object restorationWitness) {
        private boolean complete(){return coordinator!=null&&capture!=null&&sourcePolicy!=null&&farm!=null
            &&helper!=null&&farm!=helper&&token!=null&&capturedPose!=null;}
        private boolean same(Pair other){return other!=null&&coordinator==other.coordinator&&capture==other.capture
            &&sourcePolicy==other.sourcePolicy&&farm==other.farm&&helper==other.helper
            &&token==other.token&&capturedPose==other.capturedPose&&restorationWitness==other.restorationWitness;}
    }
    public record Context(Object player,Object world,Object connection,Object configStore,
                          long rotationRevision,long approachRevision) {
        private boolean complete(){return player!=null&&world!=null&&connection!=null&&configStore!=null
            &&rotationRevision>=0&&approachRevision>=0;}
        private boolean same(Context other){return other!=null&&player==other.player&&world==other.world
            &&connection==other.connection&&configStore==other.configStore
            &&rotationRevision==other.rotationRevision&&approachRevision==other.approachRevision;}
    }
    public record Observation(Pair pair,Context context,FarmMaintenanceLease.State phase,
            SharedMovementPolicy.Purpose purpose,Vec3 destination,Vec3 capturedReturn,
            Object inputOwner,Object cameraOwner,String helperId,String lockOwner,
            boolean exactRegisteredPair,boolean farmOwnsToken,boolean helperEnabled,boolean helperOwnsInput,
            boolean active,boolean sourcePhaseProved,boolean healthy,boolean policyCurrent,boolean lockActive) {}
    private final Observation issued;
    private MaintenanceMovementAuthorityFrame(Observation issued){this.issued=issued;}
    public static Optional<MaintenanceMovementAuthorityFrame> capture(Observation actual){
        return qualified(actual)?Optional.of(new MaintenanceMovementAuthorityFrame(actual)):Optional.empty();
    }
    public Pair pair(){return issued.pair();}
    public Context context(){return issued.context();}
    public String helperId(){return issued.helperId();}
    public boolean current(Observation actual){
        if(!qualified(actual)||!issued.pair().same(actual.pair())||!issued.context().same(actual.context())
                ||issued.purpose()!=actual.purpose()||!issued.destination().equals(actual.destination())
                ||!issued.capturedReturn().equals(actual.capturedReturn()))return false;
        // The real coordinator starts protected return under RESTORING, then commits RETURNING.
        return issued.phase()==actual.phase()||issued.phase()==FarmMaintenanceLease.State.RESTORING
            &&actual.phase()==FarmMaintenanceLease.State.RETURNING
            &&issued.purpose()==SharedMovementPolicy.Purpose.EXACT_RETURN;
    }
    /** Cancellation is narrower than gameplay: same PWC/store/pair only, never new gear or movement permission. */
    public boolean cancellationCurrent(Observation actual){
        if(actual==null||actual.pair()==null||!issued.pair().same(actual.pair())||actual.context()==null
                ||issued.context().player()!=actual.context().player()||issued.context().world()!=actual.context().world()
                ||issued.context().connection()!=actual.context().connection()||issued.context().configStore()!=actual.context().configStore()
                ||!actual.exactRegisteredPair()||!actual.farmOwnsToken()||!actual.helperEnabled()||!actual.helperOwnsInput()
                ||!actual.active()||!actual.sourcePhaseProved()||!actual.lockActive()
                ||actual.inputOwner()!=issued.pair().helper()||actual.cameraOwner()!=issued.pair().token()
                ||!Objects.equals(actual.helperId(),actual.lockOwner()))return false;
        return actual.phase()==FarmMaintenanceLease.State.HELPER_ACTIVE||actual.phase()==FarmMaintenanceLease.State.RESTORING
            ||actual.phase()==FarmMaintenanceLease.State.RETURNING||actual.phase()==FarmMaintenanceLease.State.READY;
    }
    /** Authentic source closure and privately held native publication ids are mandatory even for key release. */
    public record RetirementObservation(Pair pair,Context context,Object issuedClosure,Object currentClosure,
            boolean sourceWindowActive,boolean exactRegisteredPair,boolean helperDisabled,boolean noForeignController,
            boolean inputAbsent,boolean cameraAbsent,boolean originalLock,long expectedEpoch,long actualEpoch,
            long expectedSerial,long actualSerial,long expectedGeneration,long actualGeneration){}
    public boolean retirementKeysCurrent(RetirementObservation actual){
        return actual!=null&&issued.pair().same(actual.pair())&&actual.context()!=null
            &&issued.context().player()==actual.context().player()&&issued.context().world()==actual.context().world()
            &&issued.context().connection()==actual.context().connection()&&issued.context().configStore()==actual.context().configStore()
            &&actual.issuedClosure()!=null&&actual.issuedClosure()==actual.currentClosure()&&actual.sourceWindowActive()
            &&actual.exactRegisteredPair()&&actual.helperDisabled()&&actual.noForeignController()
            &&actual.inputAbsent()&&actual.cameraAbsent()&&actual.originalLock()
            &&actual.expectedEpoch()>=0&&actual.expectedEpoch()==actual.actualEpoch()
            &&actual.expectedSerial()>0&&actual.expectedSerial()==actual.actualSerial()
            &&actual.expectedGeneration()>=0&&actual.expectedGeneration()==actual.actualGeneration();
    }
    private static boolean qualified(Observation actual){
        if(actual==null||actual.pair()==null||!actual.pair().complete()||actual.context()==null
                ||!actual.context().complete()||actual.purpose()==null
                ||!SharedMovementPolicy.finite(actual.destination())||!SharedMovementPolicy.finite(actual.capturedReturn()))return false;
        if(!actual.exactRegisteredPair()||!actual.farmOwnsToken()||!actual.helperEnabled()||!actual.helperOwnsInput()
                ||!actual.active()||!actual.sourcePhaseProved()||!actual.healthy()||!actual.policyCurrent()||!actual.lockActive()
                ||actual.inputOwner()!=actual.pair().helper()||actual.cameraOwner()!=actual.pair().token()
                ||actual.helperId()==null||actual.helperId().isEmpty()||!Objects.equals(actual.helperId(),actual.lockOwner()))return false;
        return actual.phase()==FarmMaintenanceLease.State.HELPER_ACTIVE
            ||(actual.phase()==FarmMaintenanceLease.State.RESTORING||actual.phase()==FarmMaintenanceLease.State.RETURNING)
                &&actual.purpose()==SharedMovementPolicy.Purpose.EXACT_RETURN
                &&actual.pair().restorationWitness()!=null&&actual.destination().equals(actual.capturedReturn());
    }
}
