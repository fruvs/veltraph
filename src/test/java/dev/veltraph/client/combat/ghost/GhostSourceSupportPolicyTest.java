package dev.veltraph.client.combat.ghost;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import dev.veltraph.client.combat.ghost.GhostSourceSupportPolicy.Kind;

class GhostSourceSupportPolicyTest {
    @Test void rawDefaultCollisionInversionAndFamilyOverridesRemainDistinctFromRayPassability(){
        assertFalse(GhostSourceSupportPolicy.allows(true,Kind.OTHER)); // Water/air empty defaults.
        assertTrue(GhostSourceSupportPolicy.allows(false,Kind.OTHER)); // Solid or classifier exception false.
        for(var kind:new Kind[]{Kind.FENCE,Kind.FENCE_GATE,Kind.WALL,Kind.WEB,Kind.STANDING_SIGN}){
            assertFalse(GhostSourceSupportPolicy.allows(false,kind));assertFalse(GhostSourceSupportPolicy.allows(true,kind));
        }
        for(var kind:new Kind[]{Kind.CARPET,Kind.MOSSY_CARPET,Kind.SNOW,Kind.LILY_PAD}){
            assertTrue(GhostSourceSupportPolicy.allows(false,kind));assertTrue(GhostSourceSupportPolicy.allows(true,kind));
        }
        assertThrows(IllegalArgumentException.class,()->GhostSourceSupportPolicy.allows(false,null));
    }
}
