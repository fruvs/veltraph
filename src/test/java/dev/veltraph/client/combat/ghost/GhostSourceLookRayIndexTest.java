package dev.veltraph.client.combat.ghost;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import dev.veltraph.client.combat.ghost.GhostSourceSampledRay.*;
class GhostSourceLookRayIndexTest {
    private final Point eye=new Point(0,0,0),look=new Point(1,0,0),center=new Point(5.5,.5,.5);
    @Test void firstHitIndexZeroAndHalfStepIndexThreeAreActualSamples(){
        assertEquals(0,GhostSourceLookRayIndex.sampleIndex(eye,look,new Box(-.1,-1,-1,.1,1,1),center));
        assertEquals(3,GhostSourceLookRayIndex.sampleIndex(eye,look,new Box(1.4,-1,-1,1.6,1,1),center));
    }
    @Test void indexFourDoublesPenaltyWhileIndexThreeDoesNot(){
        int index=GhostSourceLookRayIndex.sampleIndex(eye,look,new Box(1.9,-1,-1,2.1,1,1),center);
        assertEquals(4,index);assertEquals(108,GhostSourceLookRayIndex.score(100,index));
        assertEquals(103,GhostSourceLookRayIndex.score(100,3));
    }
    @Test void indexTwentyIncludedAndIndexTwentyOneNeverSampled(){
        assertEquals(20,GhostSourceLookRayIndex.sampleIndex(eye,look,new Box(9.9,-1,-1,10.1,1,1),center));
        assertEquals(0,GhostSourceLookRayIndex.sampleIndex(eye,look,new Box(10.4,-1,-1,10.6,1,1),center));
        assertEquals(0,GhostSourceLookRayIndex.sampleIndex(eye,new Point(0,1,0),new Box(1.9,-1,-1,2.1,1,1),center));
    }
    @Test void actualNativeLookVectorIsNotNormalizedAndNegativeCoordinatesRetained(){
        assertEquals(4,GhostSourceLookRayIndex.sampleIndex(new Point(-5,70,-3),new Point(.5,0,0),
                new Box(-4.1,69,-4,-3.9,71,-2),new Point(-3.5,70.5,-2.5)));
    }
    @Test void originalUnusedLimitStillKeepsHardThousandBoundary(){
        assertEquals(0,GhostSourceLookRayIndex.sampleIndex(eye,look,new Box(-.1,-1,-1,.1,1,1),new Point(997,0,0)));
        assertThrows(IllegalArgumentException.class,()->GhostSourceLookRayIndex.sampleIndex(eye,look,new Box(-.1,-1,-1,.1,1,1),new Point(997.0001,0,0)));
    }
}
