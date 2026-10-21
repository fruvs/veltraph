package dev.veltraph.client.combat.ghost;

import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import dev.veltraph.client.combat.ghost.GhostSourceFallbackAnchors.*;
import dev.veltraph.client.combat.ghost.GhostSourceWalkFrontier.Cell;

class GhostSourceFallbackFractionalPositionTest {
    private static final Cell ANCHOR=new Cell(174,76,59),LAST=new Cell(0,0,0);
    private static boolean contains(Position point){return GhostSourceFallbackAnchors.capture(point,LAST,(a,b)->0).stream().anyMatch(c->c.anchor().equals(ANCHOR));}
    @Test void exactTenStillExcludedButActualFractionBeyondTenIsIncluded(){
        assertFalse(contains(new Position(184,76,59)));
        assertFalse(contains(new Position(183.999,76,59)));
        var actual=new Position(184.5,76,59);assertTrue(contains(actual));
        assertFalse(GhostSourceFallbackAnchors.capture(actual.blockCell(),LAST,(a,b)->0).stream().anyMatch(c->c.anchor().equals(ANCHOR)));
    }
    @Test void exactSixtyStillExcludedButFractionInsideSixtyIsIncluded(){
        assertFalse(contains(new Position(114,76,59)));
        assertFalse(contains(new Position(113.999,76,59)));
        var actual=new Position(114.5,76,59);assertTrue(contains(actual));
        assertFalse(GhostSourceFallbackAnchors.capture(actual.blockCell(),LAST,(a,b)->0).stream().anyMatch(c->c.anchor().equals(ANCHOR)));
    }
    @Test void sourceDistancePreservesFractionalYAndZAsWellAsX(){
        assertTrue(contains(new Position(184,76.5,59)));
        assertTrue(contains(new Position(184,76,59.5)));
        assertEquals(Math.sqrt(100+.25),GhostSourceFallbackAnchors.distance(ANCHOR,new Position(184,76.5,59)),0.0);
    }
    @Test void graphBlockConversionUsesFloorOfTheSameCapturedPositionIncludingNegatives(){
        var actual=new Position(-.25,76.75,-1.01);
        assertEquals(new Cell(-1,76,-2),actual.blockCell());
        assertEquals(Math.sqrt(Math.pow(174.25,2)+Math.pow(-.75,2)+Math.pow(60.01,2)),GhostSourceFallbackAnchors.distance(ANCHOR,actual),0.0);
    }
    @Test void integerCallerCompatibilityKeepsOriginalRosterDistancesAndEndpoints(){
        for(var cell:List.of(new Cell(142,76,50),new Cell(-14,76,92),new Cell(174,76,59))){
            assertEquals(GhostSourceFallbackAnchors.capture(cell,LAST,(a,b)->0),GhostSourceFallbackAnchors.capture(new Position(cell.x(),cell.y(),cell.z()),LAST,(a,b)->0));
        }
    }
    @Test void fractionalCaptureRetainsSourceRosterOrderAndZThenXDraws(){
        var point=new Position(142.5,76.25,50.5);var draws=new AtomicInteger();var calls=new ArrayList<String>();
        var actual=GhostSourceFallbackAnchors.capture(point,LAST,(a,b)->{calls.add(a+":"+b);return draws.getAndIncrement()%2==0?-5:4;});
        var expectedAnchors=GhostSourceFallbackAnchors.ANCHORS.stream().filter(a->{double d=Math.sqrt(Math.pow(a.x()-142.5,2)+Math.pow(a.y()-76.25,2)+Math.pow(a.z()-50.5,2));return d>10&&d<60;}).toList();
        assertEquals(expectedAnchors,actual.stream().map(Candidate::anchor).toList());assertEquals(2*actual.size(),draws.get());
        for(var c:actual){assertEquals(new Cell(c.anchor().x()+4,c.anchor().y(),c.anchor().z()-5),c.endpoint());assertEquals(GhostSourceFallbackAnchors.distance(c.anchor(),point),c.distance(),0.0);}
        assertTrue(calls.stream().allMatch(call->call.equals("-5:5")));
    }
    @Test void lastAnchorExclusionAndVisitLimitRemainExact(){
        var point=new Position(142.5,76,50.5);
        assertTrue(GhostSourceFallbackAnchors.capture(point,ANCHOR,(a,b)->0).stream().noneMatch(c->c.anchor().equals(ANCHOR)));
        assertEquals(400,GhostSourceFallbackAnchors.SOURCE_VISIT_LIMIT);assertEquals(21,GhostSourceFallbackAnchors.ANCHORS.size());
    }
    @Test void comparatorRemainsAscendingExactDoubleAndDoesNotTruncateSubUnitDifferences(){
        var farther=new Candidate(new Cell(1,0,0),new Cell(1,0,0),10.9);
        var nearer=new Candidate(new Cell(2,0,0),new Cell(2,0,0),10.1);
        assertSame(nearer,GhostSourceFallbackAnchors.select(List.of(farther,nearer),c->c,n->0));
        assertSame(farther,GhostSourceFallbackAnchors.select(List.of(farther,nearer),c->c,n->1));
    }
    @Test void exactDoubleTiesRemainStableAndSelectionIncludesWholeSuccessfulList(){
        var first=new Candidate(new Cell(1,0,0),new Cell(1,0,0),10.25);
        var second=new Candidate(new Cell(2,0,0),new Cell(2,0,0),10.25);
        var third=new Candidate(new Cell(3,0,0),new Cell(3,0,0),20);
        assertSame(first,GhostSourceFallbackAnchors.select(List.of(first,second,third),c->c,n->0));
        assertSame(second,GhostSourceFallbackAnchors.select(List.of(first,second,third),c->c,n->1));
        assertSame(third,GhostSourceFallbackAnchors.select(List.of(first,second,third),c->c,n->{assertEquals(3,n);return 2;}));
    }
    @Test void invalidNativePointFailsBeforeAnyRandomDraw(){
        assertThrows(IllegalArgumentException.class,()->new Position(Double.NaN,76,50));
        assertThrows(IllegalArgumentException.class,()->new Position(142,Double.POSITIVE_INFINITY,50));
        assertThrows(IllegalArgumentException.class,()->new Position(142,76,Double.NEGATIVE_INFINITY));
    }
}
