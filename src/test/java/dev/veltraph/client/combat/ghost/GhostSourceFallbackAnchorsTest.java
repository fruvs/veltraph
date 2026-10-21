package dev.veltraph.client.combat.ghost;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.ArrayList;
import dev.veltraph.client.combat.ghost.GhostSourceWalkFrontier.Cell;
class GhostSourceFallbackAnchorsTest {
    @Test void originalRosterAndEncounterOrderRetained(){assertEquals(21,GhostSourceFallbackAnchors.ANCHORS.size());assertEquals(new Cell(174,76,59),GhostSourceFallbackAnchors.ANCHORS.getFirst());assertEquals(new Cell(-26,76,132),GhostSourceFallbackAnchors.ANCHORS.getLast());assertEquals(400,GhostSourceFallbackAnchors.SOURCE_VISIT_LIMIT);}
    @Test void strictTenSixtyAndLastCoordinateExcludeBeforeRandomDraws(){
        var a=GhostSourceFallbackAnchors.ANCHORS.getFirst();var exactTen=new Cell(a.x()+10,a.y(),a.z());
        var calls=new ArrayList<String>();var captured=GhostSourceFallbackAnchors.capture(exactTen,new Cell(0,0,0),(low,high)->{calls.add(low+":"+high);return 0;});
        assertTrue(captured.stream().noneMatch(c->c.anchor().equals(a)));assertEquals(captured.size()*2,calls.size());assertTrue(calls.stream().allMatch(v->v.equals("-5:5")));
        var exactSixty=new Cell(a.x()+60,a.y(),a.z());assertTrue(GhostSourceFallbackAnchors.capture(exactSixty,new Cell(0,0,0),(l,h)->0).stream().noneMatch(c->c.anchor().equals(a)));
        assertTrue(GhostSourceFallbackAnchors.capture(new Cell(150,76,59),a,(l,h)->0).stream().noneMatch(c->c.anchor().equals(a)));
    }
    @Test void originalRandomDrawZThenXAndExclusiveUpperBound(){
        int[] draw={0};var values=GhostSourceFallbackAnchors.capture(new Cell(150,76,59),new Cell(0,0,0),(l,h)->draw[0]++%2==0?-5:4);
        assertFalse(values.isEmpty());for(var value:values){assertEquals(value.anchor().x()+4,value.endpoint().x());assertEquals(value.anchor().z()-5,value.endpoint().z());assertEquals(value.anchor().y(),value.endpoint().y());}
        assertThrows(IllegalArgumentException.class,()->GhostSourceFallbackAnchors.capture(new Cell(150,76,59),new Cell(0,0,0),(l,h)->5));
    }
    @Test void randomOrNullOperatesAfterStableDistanceSortRatherThanChoosingNearest(){
        var near=new GhostSourceFallbackAnchors.Candidate(new Cell(1,0,0),new Cell(1,0,0),11);
        var far=new GhostSourceFallbackAnchors.Candidate(new Cell(2,0,0),new Cell(2,0,0),59);
        assertSame(far,GhostSourceFallbackAnchors.select(List.of(far,near),v->v,n->n-1));
        assertNull(GhostSourceFallbackAnchors.select(List.<GhostSourceFallbackAnchors.Candidate>of(),v->v,n->{fail("empty randomOrNull must not draw");return 0;}));
    }
    @Test void equalDistancesKeepSourceEncounterOrder(){var a=new GhostSourceFallbackAnchors.Candidate(new Cell(1,0,0),new Cell(1,0,0),11);var b=new GhostSourceFallbackAnchors.Candidate(new Cell(2,0,0),new Cell(2,0,0),11);assertSame(a,GhostSourceFallbackAnchors.select(List.of(a,b),v->v,n->0));}
    @Test void lifetimeSearchEntryClearsActiveButPreservesLastAnchor(){var l=new GhostSourceFallbackLifetime();assertEquals(new Cell(0,0,0),l.lastAnchor());assertFalse(l.active());var a=new Cell(174,76,59);l.installed(a);assertTrue(l.active());l.sourceSearchEntry();assertFalse(l.active());assertEquals(a,l.lastAnchor());}
}
