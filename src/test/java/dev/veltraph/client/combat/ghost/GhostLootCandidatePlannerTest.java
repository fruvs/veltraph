package dev.veltraph.client.combat.ghost;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import dev.veltraph.client.combat.ghost.GhostSourceSampledRay.Point;
class GhostLootCandidatePlannerTest {
    private static GhostLootCandidatePlanner.Candidate candidate(String id,double x,double y,double z,boolean alive){return new GhostLootCandidatePlanner.Candidate(new Object(),1,UUID.randomUUID(),id,"unit-components",1,new Point(x,y,z),alive);}
    private static GhostLootCandidatePlanner.Candidate nearest(List<GhostLootCandidatePlanner.Candidate> items){return GhostLootCandidatePlanner.nearest(GhostLootCandidatePlanner.Availability.OBSERVED,true,new Point(0,0,0),items,24);}
    @Test void exactRecoveredIdsAreAcceptedAndHoverNameSubstitutesAreNot(){
        for(var id:List.of("SORROW","CRYSTAL_FRAGMENT","PLASMA","VOLTA")){var item=candidate(id,2,0,0,true);assertSame(item,nearest(List.of(item)));}
        assertNull(nearest(List.of(candidate("Sorrow",2,0,0,true),candidate("fake-SORROW",1,0,0,true))));
    }
    @Test void nearestThreeDimensionalItemWinsWithoutRarityPriority(){var sorrow=candidate("SORROW",3,4,0,true);var volta=candidate("VOLTA",4,0,0,true);assertSame(volta,nearest(List.of(sorrow,volta)));}
    @Test void exactRadiusIsExcludedAndDeadNativeItemsCannotWin(){assertNull(nearest(List.of(candidate("SORROW",24,0,0,true),candidate("PLASMA",1,0,0,false))));assertNotNull(nearest(List.of(candidate("SORROW",23.999,0,0,true))));}
    @Test void stableNativeOrderBreaksEqualDistanceTie(){var first=candidate("PLASMA",3,0,0,true);var second=candidate("SORROW",-3,0,0,true);assertSame(first,nearest(List.of(first,second)));assertSame(second,nearest(List.of(second,first)));}
    @Test void UnknownFrameAndActualDisabledConfigurationYieldNoCandidate(){var items=List.of(candidate("SORROW",1,0,0,true));assertNull(GhostLootCandidatePlanner.nearest(GhostLootCandidatePlanner.Availability.UNKNOWN,true,null,items,24));assertNull(GhostLootCandidatePlanner.nearest(GhostLootCandidatePlanner.Availability.OBSERVED,false,null,items,24));assertNull(nearest(List.of()));}
    @Test void NonfiniteOrUnboundedRadiusCannotSelectAPathGoal(){var items=List.of(candidate("SORROW",1,0,0,true));for(double radius:new double[]{0,-1,Double.NaN,Double.POSITIVE_INFINITY,Double.MAX_VALUE})assertThrows(IllegalArgumentException.class,()->GhostLootCandidatePlanner.nearest(GhostLootCandidatePlanner.Availability.OBSERVED,true,new Point(0,0,0),items,radius));}
}
