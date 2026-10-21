package dev.veltraph.client.galatea;

import dev.veltraph.client.world.BlockPos;
import dev.veltraph.client.world.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FigSourceCandidateScoreTest {
    private final BlockPos base=new BlockPos(0,70,0),previous=new BlockPos(9,70,9);
    @Test void modeScoresUseCenteredYawForStemAndIntegerYawForJumpAndThrow() {
        var p=base.add(0,0,1);var nativePosition=new Vec3(.5,70,.5);
        assertEquals(3,FigSourceCandidateScore.score(GalateaPlanner.AxeMode.STEM,p,base,base,nativePosition,0,false,2,0,true,false,null,previous),1e-9);
        assertEquals(4,FigSourceCandidateScore.score(GalateaPlanner.AxeMode.JUMPING,p,base,base,nativePosition,0,false,2,0,true,false,null,previous),1e-9);
        assertEquals(11.5,FigSourceCandidateScore.score(GalateaPlanner.AxeMode.THROWING,p,base,base,nativePosition,0,false,2,0,true,false,null,previous),1e-9);
    }
    @Test void sourceScoreCanPreferFartherLowLogToCloserHighLog() {
        var high=base.up(4);var low=base.add(3,0,0);var nativePosition=new Vec3(.5,70,.5);var eye=new Vec3(.5,71.62,.5);
        assertTrue(eye.distanceTo(FigSourceGeometry.center(high))<eye.distanceTo(FigSourceGeometry.center(low)));
        double highScore=FigSourceCandidateScore.score(GalateaPlanner.AxeMode.STEM,high,base,base,nativePosition,-90,false,0,0,true,false,null,previous);
        double lowScore=FigSourceCandidateScore.score(GalateaPlanner.AxeMode.STEM,low,base,base,nativePosition,-90,false,0,0,true,false,null,previous);
        assertTrue(lowScore<highScore);
    }
    @Test void feasibilityAndLeavesBelowAreIndependentJumpPenalties() {
        var p=base.add(0,0,1);var pos=new Vec3(.5,70,.5);
        double clean=FigSourceCandidateScore.score(GalateaPlanner.AxeMode.JUMPING,p,base,base,pos,0,false,0,0,true,false,null,previous);
        double penalized=FigSourceCandidateScore.score(GalateaPlanner.AxeMode.JUMPING,p,base,base,pos,0,false,0,0,false,true,null,previous);
        assertEquals(1010,penalized-clean,1e-9);
    }
    @Test void repeatPenaltiesRetainStrictBoundariesAndResetGate() {
        assertEquals(21,FigSourceCandidateScore.localPenalty(base,base,true,false),1e-9);
        assertEquals(0,FigSourceCandidateScore.localPenalty(base.up(3),base,true,false),1e-9);
        assertEquals(0,FigSourceCandidateScore.localPenalty(base,base,true,true),1e-9);
        assertEquals(0,FigSourceCandidateScore.localPenalty(base,base,false,false),1e-9);
    }
    @Test void throwingConnectionCountDoesNotBecomeDegreeOrInventMissingCost() {
        var p=base.add(0,0,1);var pos=new Vec3(.5,70,.5);
        double missing=FigSourceCandidateScore.score(GalateaPlanner.AxeMode.THROWING,p,base,base,pos,0,false,0,0,true,false,null,previous);
        assertEquals(4,FigSourceCandidateScore.score(GalateaPlanner.AxeMode.THROWING,p,base,base,pos,0,false,0,0,true,false,1,previous)-missing,1e-9);
        assertEquals(0,FigSourceCandidateScore.score(GalateaPlanner.AxeMode.THROWING,p,base,base,pos,0,false,0,0,true,false,75,previous)-missing,1e-9);
    }
    @Test void jumpingIsHorizontalWhileScaffoldIsThreeDimensionalAndBothAreStrict() {
        var p=base.up(100);var eye=new Vec3(.5,71.62,.5);
        assertTrue(FigSourceSelector.modeEligible(GalateaPlanner.AxeMode.JUMPING,eye,p));
        assertFalse(FigSourceSelector.modeEligible(GalateaPlanner.AxeMode.SCAFFOLD,eye,p));
        var center=FigSourceGeometry.center(base);var boundary=new Vec3(center.x()+4.25,center.y(),center.z());
        assertFalse(FigSourceSelector.modeEligible(GalateaPlanner.AxeMode.JUMPING,boundary,base));
        assertFalse(FigSourceSelector.modeEligible(GalateaPlanner.AxeMode.SCAFFOLD,boundary,base));
    }
}
