package dev.veltraph.client.combat.ghost;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import dev.veltraph.client.combat.ghost.GhostSourceSampledRay.*;
import dev.veltraph.client.combat.ghost.GhostSourceWalkFrontier.Cell;
import static dev.veltraph.client.combat.ghost.GhostSourceWalkWorker.*;
class GhostSourceActorReservationsTest {
    Actor blocker(double x,double z){return new Actor(new Box(x-.3,76,z-.3,x+.3,78,z+.3),new Point(x,76,z),true,false,false,false);}
    GhostSourceActorReservations guard(Actor...actors){return new GhostSourceActorReservations(List.of(actors),.6,1.8,.08);}
    Point center(Cell c){return new Point(c.x()+.5,c.y(),c.z()+.5);}
    Result actualSourceSolve(GhostSourceActorReservations reservations,boolean narrow){var start=new Cell(0,76,0);var end=new Cell(7,76,0);
        return GhostSourceWalkWorker.solve(new Inputs(start,end,6000,1,1,false,false,Observed.YES,Observed.YES,Observed.YES,Observed.YES,Map.of()),new ObservedEdges(){
            public boolean current(){return true;}
            public Edge observe(EdgeKey e){var c=e.to();boolean ground=c.y()==76&&c.x()>=0&&c.x()<=7&&(narrow?c.z()==0:c.z()>=-3&&c.z()<=3);
                boolean clear=ground&&reservations.clear(center(e.from()),center(c));return clear?new Edge(Observed.YES,1):new Edge(Observed.NO,Double.NaN);}
        });}
    @Test void actualSourceFrontierRoutesAroundStationaryMidpathBodyToSameDestination(){var reservations=guard(blocker(3.5,.5));var result=actualSourceSolve(reservations,false);
        assertEquals(Status.FOUND,result.status());assertEquals(new Cell(7,76,0),result.orderedNodes().getLast());assertTrue(result.orderedNodes().stream().anyMatch(c->c.z()!=0));
        for(int i=1;i<result.orderedNodes().size();i++)assertTrue(reservations.clear(center(result.orderedNodes().get(i-1)),center(result.orderedNodes().get(i))));
        assertFalse(reservations.clear(center(result.orderedNodes().getFirst()),center(result.orderedNodes().getLast()))); // V2 direct shortcut must retain safe raw path.
    }
    @Test void exactNarrowCorridorCannotInventSideRouteOrIgnoreBlockingBody(){var result=actualSourceSolve(guard(blocker(3.5,.5)),true);assertEquals(Status.NO_UNVISITED,result.status());assertTrue(result.orderedNodes().isEmpty());}
    @Test void continuousDiagonalSweepRejectsCollisionBetweenClearEndpointNodes(){var reservations=guard(blocker(1.5,1.5));assertTrue(reservations.clear(new Point(.5,76,.5),new Point(.5,76,.5)));assertTrue(reservations.clear(new Point(2.5,76,2.5),new Point(2.5,76,2.5)));assertFalse(reservations.clear(new Point(.5,76,.5),new Point(2.5,76,2.5)));}
    @Test void movingBodyRequiresFreshCurrentSweepRatherThanUnchangedActorCount(){var old=guard(blocker(3.5,3));assertTrue(old.clear(new Point(.5,76,.5),new Point(7.5,76,.5)));var live=guard(blocker(3.5,.5));assertFalse(live.clear(new Point(.5,76,.5),new Point(7.5,76,.5)));assertEquals(old.blockerCount(),live.blockerCount());}
    @Test void selfAndSelectedTargetStayOutOfTheQualifiedOtherActorOverlay(){var box=new Box(2,76,0,4,78,1);var pos=new Point(3,76,.5);
        var reservations=guard(new Actor(box,pos,true,false,true,false),new Actor(box,pos,true,false,false,true));assertEquals(0,reservations.blockerCount());assertTrue(reservations.clear(new Point(.5,76,.5),new Point(7.5,76,.5)));}
    @Test void invisibleOtherLivingBodyRemainsAPhysicalObstacleUnderModernSafety(){var reservations=guard(new Actor(new Box(2.9,76,.2,3.1,78,.8),new Point(3,76,.5),true,true,false,false));assertEquals(1,reservations.blockerCount());assertFalse(reservations.clear(new Point(.5,76,.5),new Point(7.5,76,.5)));}
    @Test void standingBodyHeightPreventsPassingUnderAnOverlappingActor(){var actor=new Actor(new Box(2.9,77.5,.2,3.1,79,.8),new Point(3,77.5,.5),true,false,false,false);assertFalse(guard(actor).clear(new Point(.5,76,.5),new Point(7.5,76,.5)));}
    @Test void exhaustedActualCursorSkipsTraversedBodyFootprintAtArrival(){
        var first=new Object();var second=new Object();var sourceEdges=List.of(first,second);
        assertEquals(2,GhostSourceActorReservations.remainingStartIndex(sourceEdges,java.util.Optional.empty()));
        assertEquals(1,GhostSourceActorReservations.remainingStartIndex(sourceEdges,java.util.Optional.of(second)));
        var live=guard(blocker(1.5,.5));assertFalse(live.clear(new Point(.5,76,.5),new Point(2.5,76,.5)));
        assertTrue(live.clear(new Point(4.5,76,.5),new Point(7.5,76,.5)));
    }
    @Test void unknownOrEqualButUnownedEdgeIdentityCannotSkipSourceActorChecks(){
        var actual=new String("edge");var equalButDifferent=new String("edge");
        assertEquals(0,GhostSourceActorReservations.remainingStartIndex(List.of(actual),java.util.Optional.of(equalButDifferent)));
    }

}
