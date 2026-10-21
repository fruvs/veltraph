package dev.veltraph.client.failsafe.response;

import dev.veltraph.client.navigation.GroundPathfinder;
import dev.veltraph.client.world.BlockPos;
import dev.veltraph.client.world.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FailsafeMovementGuardTest {
    private static GroundPathfinder.Terrain lane(boolean wall) {
        return new GroundPathfinder.Terrain() {
            public boolean clear(BlockPos p) { return p.y() >= 1 && (!wall || p.z() < 2); }
            public boolean floor(BlockPos p) { return p.y() == 0 && p.x() == 0; }
        };
    }
    private final Vec3 origin = new Vec3(.5, 1, .5), still = new Vec3(0,0,0);

    @Test void followsNarrowLaneButRejectsSidewaysDrop() {
        assertTrue(FailsafeMovementGuard.allows(lane(false),origin,still,0,true,false,false,false));
        assertFalse(FailsafeMovementGuard.allows(lane(false),origin,still,0,false,false,true,false));
        assertFalse(FailsafeMovementGuard.allows(lane(false),origin,still,0,false,false,false,true));
    }
    @Test void checksSweptBodyAndMomentumInsteadOfOnlyDestination() {
        assertFalse(FailsafeMovementGuard.allows(lane(true),new Vec3(.5,1,1.5),still,0,true,false,false,false));
        assertFalse(FailsafeMovementGuard.allows(lane(false),origin,new Vec3(.2,0,0),0,true,false,false,false));
    }
    @Test void rejectsMissingWorldAndNonfinitePose() {
        assertFalse(FailsafeMovementGuard.allows(lane(false),origin,still,Float.NaN,true,false,false,false));
        assertFalse(FailsafeMovementGuard.allows(lane(false),new Vec3(Double.NaN,1,0),still,0,true,false,false,false));
        GroundPathfinder.Terrain unloaded = new GroundPathfinder.Terrain() {
            public boolean clear(BlockPos p) { return false; }
            public boolean floor(BlockPos p) { return false; }
        };
        assertFalse(FailsafeMovementGuard.allows(unloaded,origin,still,0,true,false,false,false));
    }
    @Test void jumpRequiresClearanceOverEntireBody() {
        assertTrue(FailsafeMovementGuard.allowsJump(lane(false),origin));
        var overhang = new GroundPathfinder.Terrain() {
            public boolean floor(BlockPos p) { return p.y()==0; }
            public boolean clear(BlockPos p) { return p.y()>0 && !(p.y()==3 && p.x()==1); }
        };
        assertFalse(FailsafeMovementGuard.allowsJump(overhang,new Vec3(.8,1,.5)),"corner under overhang");
    }
}
