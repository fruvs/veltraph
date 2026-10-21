package dev.veltraph.client.combat.sven;

import dev.veltraph.client.world.Vec3;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static dev.veltraph.client.combat.sven.SvenIngressPort.*;
import static dev.veltraph.client.combat.sven.SvenPreparationPolicy.*;

class SvenPreparationPolicyTest {
    static Observation observation(boolean area,boolean usable,Vec3 position){return new Observation(Prerequisite.READY,Optional.of(area),Optional.of(usable),position);}
    @Test void caveParentUsesParkSpawnAndStrictlyLessThanFifteen(){
        var p=SvenCaveProfile.PARK_SPAWN;
        var nearby=evaluate(observation(true,false,new Vec3(p.x()+14.999,p.y(),p.z())));assertEquals(Continuation.TRAVEL,nearby.result().continuation());assertEquals(1,nearby.delayTicks());
        var exactly=evaluate(observation(true,false,new Vec3(p.x()+15,p.y(),p.z())));assertEquals(Continuation.PARENT_WARP,exactly.result().continuation());assertEquals(1,exactly.delayTicks());
        assertEquals(Continuation.PARENT_WARP,evaluate(observation(true,false,new Vec3(-332,89,-55))).result().continuation());
    }
    @Test void wrongAreaSchedulesParentWarpFiveTicksBeforeLocationPathPredicate(){
        var result=evaluate(new Observation(Prerequisite.ABSENT,Optional.of(false),Optional.empty(),null));assertEquals(Outcome.COMPLETE,result.result().outcome());assertEquals(Continuation.PARENT_WARP,result.result().continuation());assertEquals(5,result.delayTicks());
    }
    @Test void missingHelperPredicatesAndUsableLocationAreExplicitStateBlockers(){
        assertEquals(Outcome.BLOCKED,evaluate(new Observation(Prerequisite.UNKNOWN,Optional.of(true),Optional.of(false),SvenCaveProfile.PARK_SPAWN)).result().outcome());
        assertEquals(Outcome.BLOCKED,evaluate(new Observation(Prerequisite.READY,Optional.of(true),Optional.empty(),SvenCaveProfile.PARK_SPAWN)).result().outcome());
        assertEquals(Continuation.COMBAT,evaluate(observation(true,true,SvenCaveProfile.PARK_SPAWN)).result().continuation());
    }
}
