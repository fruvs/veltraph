package dev.veltraph.client.combat.ghost;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import dev.veltraph.client.combat.ghost.GhostSourceWalkFrontier.Cell;
import dev.veltraph.client.combat.ghost.GhostSourceSampledRay.Point;
class GhostLootStandingScanTest {
    private static final Cell ITEM=new Cell(0,0,0);
    private static GhostLootStandingScan scan(Point player){return new GhostLootStandingScan(ITEM,player);}
    private static void exhaust(GhostLootStandingScan scan,Set<Cell> actualObservedFeet){int turns=0;Cell next;
        while((next=scan.nextCell())!=null){assertTrue(++turns<500);scan.observe(next,GhostLootStandingScan.Availability.OBSERVED,actualObservedFeet.contains(next),false);}}
    @Test void ActualOriginalCellWithObservedNeighborIsFastPath(){var scan=scan(new Point(.5,.5,.5));assertEquals(ITEM,scan.nextCell());assertTrue(scan.originalProbe());scan.observe(ITEM,GhostLootStandingScan.Availability.OBSERVED,true,true);assertEquals(GhostLootStandingScan.State.READY,scan.state());assertEquals(ITEM,scan.selected());assertNull(scan.nextCell());}
    @Test void OriginalCellWithoutNeighborUsesActualShellInstead(){var scan=scan(new Point(.5,.5,.5));scan.observe(scan.nextCell(),GhostLootStandingScan.Availability.OBSERVED,true,false);assertEquals(GhostLootStandingScan.State.SCANNING,scan.state());assertFalse(scan.originalProbe());exhaust(scan,Set.of(ITEM));assertEquals(ITEM,scan.selected());}
    @Test void FirstSuccessfulRadiusStopsBeforeFurtherRadiusCandidates(){var scan=scan(new Point(10,.5,.5));var inner=new Cell(-1,0,0);var outer=new Cell(2,0,0);exhaust(scan,Set.of(inner,outer));assertEquals(inner,scan.selected());}
    @Test void FullThreeDimensionalShellIncludesExactThreeBelowBoundary(){var scan=scan(new Point(.5,.5,.5));var foot=new Cell(0,-3,0);exhaust(scan,Set.of(foot));assertEquals(GhostLootStandingScan.State.READY,scan.state());assertEquals(foot,scan.selected());}
    @Test void DefaultRankingUsesActualPlayerDistancePlusDistanceFromItemCell(){var scan=scan(new Point(10,.5,.5));var nearPlayer=new Cell(1,0,0);var farPlayer=new Cell(-1,0,0);exhaust(scan,Set.of(nearPlayer,farPlayer));assertEquals(nearPlayer,scan.selected());}
    @Test void EqualScoresPreserveOriginalNestedCellOrder(){var scan=scan(new Point(.5,.5,.5));var first=new Cell(-1,0,0);var last=new Cell(1,0,0);exhaust(scan,Set.of(first,last));assertEquals(first,scan.selected());}
    @Test void UnknownRequiredFootObservationRevokesInsteadOfInventingAir(){var scan=scan(new Point(.5,.5,.5));scan.observe(scan.nextCell(),GhostLootStandingScan.Availability.UNKNOWN,false,false);assertEquals(GhostLootStandingScan.State.REVOKED,scan.state());assertNull(scan.nextCell());assertNull(scan.selected());}
    @Test void PendingCellIsIdempotentAndForeignObservationCannotAdvance(){var scan=scan(new Point(.5,.5,.5));assertSame(scan.nextCell(),scan.nextCell());assertThrows(IllegalStateException.class,()->scan.observe(new Cell(3,0,0),GhostLootStandingScan.Availability.OBSERVED,true,true));assertEquals(ITEM,scan.nextCell());scan.observe(ITEM,GhostLootStandingScan.Availability.OBSERVED,false,false);assertNotNull(scan.nextCell());}
    @Test void NoObservedFeetFinishesFiniteScanAndExplicitRevokeDestroysSelection(){var scan=scan(new Point(.5,.5,.5));exhaust(scan,Set.of());assertEquals(GhostLootStandingScan.State.NO_STANDING,scan.state());assertNull(scan.nextCell());assertNull(scan.selected());var other=scan(new Point(.5,.5,.5));other.observe(other.nextCell(),GhostLootStandingScan.Availability.OBSERVED,true,true);assertNotNull(other.selected());other.revoke();assertNull(other.selected());assertEquals(GhostLootStandingScan.State.REVOKED,other.state());}
}
