package dev.veltraph.client.combat.grind;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.Random;

class ZealotArenaIdleScanTest {
    @Test void sourceDefaultHasStrictPointTwoBoundary() {
        assertFalse(ZealotArenaIdleScan.sampledDefault(0));assertFalse(ZealotArenaIdleScan.sampledDefault(.2));
        assertTrue(ZealotArenaIdleScan.sampledDefault(Math.nextUp(.2)));assertTrue(ZealotArenaIdleScan.sampledDefault(.999));
        assertThrows(IllegalArgumentException.class,()->ZealotArenaIdleScan.sampledDefault(1));
    }
    @Test void eachGateAvoidsConsumingRandomAndIssuingCameraRequest() {
        var random=new Random(){@Override public int nextInt(int bound){throw new AssertionError("Rejected scan consumed random");}};
        assertTrue(ZealotArenaIdleScan.target(false,false,false,false,true,0,0,random).isEmpty());
        assertTrue(ZealotArenaIdleScan.target(true,true,false,false,true,0,0,random).isEmpty());
        assertTrue(ZealotArenaIdleScan.target(true,false,true,false,true,0,0,random).isEmpty());
        assertTrue(ZealotArenaIdleScan.target(true,false,false,true,true,0,0,random).isEmpty());
        assertTrue(ZealotArenaIdleScan.target(true,false,false,false,false,0,0,random).isEmpty());
    }
    @Test void sourceIntegerOffsetsUseCurrentViewAndExactUpperExclusiveBounds() {
        var random=new Random(){int n;@Override public int nextInt(int bound){assertEquals(n++==0?360:25,bound);return bound-1;}};
        var target=ZealotArenaIdleScan.target(true,false,false,false,true,40,5,random).orElseThrow();
        assertEquals(219,target.yaw());assertEquals(19,target.pitch());
        var lower=new Random(){@Override public int nextInt(int bound){return 0;}};
        target=ZealotArenaIdleScan.target(true,false,false,false,true,40,5,lower).orElseThrow();
        assertEquals(-140,target.yaw());assertEquals(-5,target.pitch());
    }
    @Test void nativePitchLimitDoesNotInventImpossiblePose() {
        var random=new Random(){@Override public int nextInt(int bound){return bound-1;}};
        assertEquals(90,ZealotArenaIdleScan.target(true,false,false,false,true,0,89,random).orElseThrow().pitch());
    }
}
