package dev.veltraph.client.combat.ghost;
import java.util.List;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import dev.veltraph.client.combat.ghost.GhostSourceWalkCursor.*;
class GhostSourceWalkCursorTest {
    private static GhostSourceSampledRay.Point p(double x,double y,double z){return new GhostSourceSampledRay.Point(x,y,z);}
    private static EdgeObservation edge(double x,double y,Observed corner){return new EdgeObservation(p(x,y,0),.5,corner);}
    @Test void radiusBranchesPreserveActualOverrideEndModeAndUnknownNativeProbe(){
        assertEquals(.12,GhostSourceWalkCursor.observedThreshold(1,.12,EndMode.WHOLE_PATH,Observed.UNKNOWN));
        assertEquals(.25,GhostSourceWalkCursor.observedThreshold(1,null,EndMode.WHOLE_PATH,Observed.UNKNOWN));
        assertEquals(.35,GhostSourceWalkCursor.observedThreshold(2,null,EndMode.WHOLE_PATH,Observed.UNKNOWN));
        assertEquals(.75,GhostSourceWalkCursor.observedThreshold(2,null,EndMode.NONE,Observed.YES));
        assertEquals(.5,GhostSourceWalkCursor.observedThreshold(2,null,EndMode.NONE,Observed.NO));
        assertTrue(Double.isNaN(GhostSourceWalkCursor.observedThreshold(2,null,EndMode.NONE,Observed.UNKNOWN)));
    }
    @Test void horizontalEqualityDoesNotReachButVerticalEqualityDoes(){
        assertEquals(0,GhostSourceWalkCursor.observeAdvance(List.of(edge(.5,0,Observed.UNKNOWN)),p(0,0,0),Observed.YES).pollCount());
        assertEquals(Status.STOP_SUCCESS,GhostSourceWalkCursor.observeAdvance(List.of(edge(.49999,1,Observed.UNKNOWN)),p(0,0,0),Observed.YES).status());
        assertEquals(0,GhostSourceWalkCursor.observeAdvance(List.of(edge(.1,1.00001,Observed.UNKNOWN)),p(0,0,0),Observed.YES).pollCount());
    }
    @Test void lastSatisfiedDestinationCanPollAcrossEarlierUnsatisfiedEdges(){
        var result=GhostSourceWalkCursor.observeAdvance(List.of(edge(10,0,Observed.NO),edge(.1,0,Observed.NO),edge(20,0,Observed.UNKNOWN)),p(0,0,0),Observed.YES);
        assertEquals(Status.CONTINUE,result.status());assertEquals(2,result.pollCount());assertEquals(p(.1,0,0),result.lastPolledDestination());assertTrue(result.refreshRemainingGeometry());
    }
    @Test void nativeCornerObservationIsRequiredOnlyForApplicableFirstFiveLookaheads(){
        assertEquals(Status.UNKNOWN,GhostSourceWalkCursor.observeAdvance(List.of(edge(10,0,Observed.UNKNOWN),edge(20,0,Observed.NO)),p(0,0,0),Observed.YES).status());
        var list=new ArrayList<EdgeObservation>();for(int i=0;i<7;i++)list.add(edge(10+i,0,i<5?Observed.NO:Observed.UNKNOWN));
        assertEquals(Status.CONTINUE,GhostSourceWalkCursor.observeAdvance(list,p(0,0,0),Observed.YES).status());
        list.set(0,edge(10,0,Observed.YES));assertEquals(1,GhostSourceWalkCursor.observeAdvance(list,p(0,0,0),Observed.YES).pollCount());
    }
    @Test void unavailableControlNeverConsumesAndEmptySuccessFollowsControlGate(){
        assertEquals(Status.DONT_MOVE,GhostSourceWalkCursor.observeAdvance(List.of(),null,Observed.NO).status());
        assertEquals(Status.UNKNOWN,GhostSourceWalkCursor.observeAdvance(List.of(),null,Observed.UNKNOWN).status());
        assertEquals(Status.STOP_SUCCESS,GhostSourceWalkCursor.observeAdvance(List.of(),null,Observed.YES).status());
    }
}
