package dev.veltraph.client.combat.sven;

import dev.veltraph.client.world.Vec3;
import java.util.Optional;
import static dev.veltraph.client.combat.sven.SvenIngressPort.*;

/** Readable PreparationStage branches; helper predicates must be supplied from actual observations. */
public final class SvenPreparationPolicy {
    private SvenPreparationPolicy(){}
    public enum Prerequisite { ABSENT, READY, NOT_READY, UNKNOWN }
    public record Observation(Prerequisite prerequisite,Optional<Boolean> correctArea,Optional<Boolean> usableLocation,Vec3 position) {}
    public record Decision(Result result,int delayTicks) {}
    public static Decision evaluate(Observation observation){
        if(observation==null||observation.prerequisite()==null||observation.prerequisite()==Prerequisite.UNKNOWN)
            return blocked("shared prerequisite collaborator and its readiness predicate are unbound");
        if(observation.prerequisite()==Prerequisite.NOT_READY)
            return blocked("shared prerequisite child identity/continuation is unbound (source schedules 5 ticks)");
        if(observation.correctArea()==null||observation.correctArea().isEmpty())return blocked("actual THE_PARK area observation unavailable");
        if(!observation.correctArea().get())return new Decision(Result.complete(Continuation.PARENT_WARP),5);
        if(observation.usableLocation()==null||observation.usableLocation().isEmpty())return blocked("already-usable-location/path predicate is unbound");
        if(observation.usableLocation().get())return new Decision(Result.complete(Continuation.COMBAT),1);
        var p=observation.position();if(p==null||!Double.isFinite(p.x())||!Double.isFinite(p.y())||!Double.isFinite(p.z()))return blocked("actual native feet position unavailable");
        return new Decision(Result.complete(p.distanceTo(SvenCaveProfile.PARK_SPAWN)<15.0?Continuation.TRAVEL:Continuation.PARENT_WARP),1);
    }
    private static Decision blocked(String reason){return new Decision(Result.blocked(reason),0);}
}
