package dev.veltraph.client.farming.movement.planner;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class MelonKingDeCompactAimTest {
    @Test void oldCreatorViewRequiresRealReachableCorrection(){assertEquals(-58.95,MelonKingDeCompactAim.target(-58.5,true,.5).orElseThrow(),1e-6);assertFalse(MelonKingDeCompactAim.ready(-58.5f));assertTrue(MelonKingDeCompactAim.ready(-58.95f));}
    @Test void unquantizedViewUsesSourceCompact59(){assertEquals(-59,MelonKingDeCompactAim.target(-10,false,.5).orElseThrow());}
    @Test void returnPhaseSelectsNearestSafeMouseStep(){var target=MelonKingDeCompactAim.target(-10,true,.5).orElseThrow();assertEquals(-59.05,target,1e-6);assertTrue(MelonKingDeCompactAim.ready((float)target));}
    @Test void unreachableCoarsePhaseFailsRatherThanAimIntoLadderOrMissLowerFruit(){assertTrue(MelonKingDeCompactAim.target(-58.3,true,1).isEmpty());}
    @Test void invalidConfigurationNeverAuthorizesPitch(){for(double value:new double[]{Double.NaN,Double.POSITIVE_INFINITY,-.1,1.1})assertTrue(MelonKingDeCompactAim.target(-58.5,true,value).isEmpty());}
}
