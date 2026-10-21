package dev.veltraph.client.hunting;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class HuntingTacticsTest {
    @Test void sourceNativeAgeScaleAndSinglePassengerHeightAreLiteralFloats(){assertEquals(6f,HuntingTactics.nativeAimHeight(4,1.5f,false));assertEquals(3f,HuntingTactics.nativeAimHeight(4,1.5f,true));}
    @Test void recoveredAimHeightHasNativeClampAndMidpointBranches(){
        assertEquals(3.004,HuntingTactics.aimHeight(4,6,.81,.22),1e-12);
        assertEquals(1.116,HuntingTactics.aimHeight(4,-1,.81,.22),1e-12);
        assertEquals(2.012,HuntingTactics.aimHeight(4,2,.81,.22),1e-12);
    }
    @Test void sourceAimHeightScalesWithTheActualNativeBody(){
        assertEquals(.3004,HuntingTactics.aimHeight(.4,2,.81,.22),1e-12);
        assertEquals(.1116,HuntingTactics.aimHeight(.4,-2,.81,.22),1e-12);
    }
    @Test void recoveredScoringIncludesHeightVisibilityAndLowYCosts(){
        assertEquals(7,HuntingTactics.score(4,8,10,88,true,80));
        assertEquals(22,HuntingTactics.score(4,8,10,88,false,80));
        assertEquals(213,HuntingTactics.score(4,8,10,76,true,80));
    }
    @Test void codThresholdsAreStrictWithZeroProgressSpecialCase(){
        assertTrue(HuntingTactics.codBlocked(0,0));assertTrue(HuntingTactics.codBlocked(6,100));
        assertFalse(HuntingTactics.codBlocked(0,100));assertFalse(HuntingTactics.codBlocked(10,100));
        assertFalse(HuntingTactics.codBlocked(Double.NaN,100));assertFalse(HuntingTactics.codBlocked(0,50));
    }
    @Test void previousCounterGreaterThan40BypassesOn42ndBlockedCallback(){
        var spin=new HuntingTactics.CodSpin();for(int i=0;i<41;i++)assertTrue(spin.block(true,0,105,false,1000,0));
        assertTrue(spin.block(true,0,105,false,1000,0));assertTrue(spin.bypass());
        assertFalse(spin.block(true,0,105,false,1000,0));
        assertFalse(spin.block(true,0,0,false,0,500));spin.reset();
        assertFalse(spin.block(false,0,0,true,0,500));assertTrue(spin.block(true,0,50,true,499,500));
        assertFalse(spin.block(true,0,50,true,500,500));
    }
}
