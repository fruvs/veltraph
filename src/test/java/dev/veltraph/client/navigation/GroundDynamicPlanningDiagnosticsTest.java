package dev.veltraph.client.navigation;

import dev.veltraph.client.world.Vec3;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class GroundDynamicPlanningDiagnosticsTest {
    @AfterEach void cleanup(){GroundDynamicPlanningDiagnostics.abandon();GroundInitialDirectionalDiagnostics.abandon();}
    @Test void fullOriginalQueryOrderAndSuccessNeverFreezeFailure(){
        var events=new ArrayList<String>();AtomicInteger ticks=new AtomicInteger();var f=new DynamicRouteQueryFence(()->{events.add("clock");return ticks.getAndIncrement();},()->{events.add("current");return true;},50);
        GroundDynamicPlanningDiagnostics.begin(f);assertTrue(f.query(()->{events.add("body");return true;}));assertEquals(List.of("clock","clock","current","body","clock","current"),events);
        assertNull(GroundDynamicPlanningDiagnostics.completeOriginal("VARIED","proof",false));assertNull(GroundDynamicPlanningDiagnostics.cachedFirstFailure());assertNull(GroundDynamicPlanningDiagnostics.cachedLatestFailure());
    }
    @Test void currentBeforeFalseAndTerrainBodyFalseRemainDistinctOriginalOperands(){
        for(boolean current:new boolean[]{false,true}){
            AtomicInteger clocks=new AtomicInteger(),calls=new AtomicInteger();var f=new DynamicRouteQueryFence(()->clocks.getAndIncrement(),()->current,50);GroundDynamicPlanningDiagnostics.begin(f);GroundDynamicPlanningDiagnostics.candidate(3);GroundDynamicPlanningDiagnostics.point();GroundDynamicPlanningDiagnostics.noteCurrent("standing");
            assertFalse(f.query(()->{calls.incrementAndGet();return false;}));var s=GroundDynamicPlanningDiagnostics.completeOriginal("ORIGINAL","No complete broad window proof",true);
            assertEquals(current?7:3,s.firstRefusedQueryVisited());assertEquals(current?3:1,s.firstRefusedQueryTrue());assertEquals(current?1:0,calls.get());assertEquals(2,clocks.get());assertEquals(3,s.firstRefusedQueryCandidate());assertEquals(0,s.firstRefusedQueryPoint());assertEquals("standing",s.firstRefusedQueryCall());
        }
    }
    @Test void latePostBudgetFalseSkipsCurrentAfterAndDoesNotResetOriginalClock(){
        long[] values={0,1,51};AtomicInteger c=new AtomicInteger(),cur=new AtomicInteger();var f=new DynamicRouteQueryFence(()->values[c.getAndIncrement()],()->{cur.incrementAndGet();return true;},50);GroundDynamicPlanningDiagnostics.begin(f);assertFalse(f.query(()->true));var s=GroundDynamicPlanningDiagnostics.completeOriginal("REVOKED","budget",true);
        assertEquals(3,c.get());assertEquals(1,cur.get());assertEquals(15,s.firstRefusedQueryVisited());assertEquals(7,s.firstRefusedQueryTrue());assertEquals(51L,s.originalElapsedNanos());assertFalse(s.originalAvailableResult());
    }
    @Test void negativeOriginalElapsedSkipsSecondClockComparisonAndEverythingElse(){
        long[] values={100,90};AtomicInteger c=new AtomicInteger();var f=new DynamicRouteQueryFence(()->values[c.getAndIncrement()],()->{fail("current skipped");return true;},50);GroundDynamicPlanningDiagnostics.begin(f);assertFalse(f.query(()->{fail("body skipped");return true;}));var s=GroundDynamicPlanningDiagnostics.completeOriginal("ORIGINAL","budget",true);assertEquals(-10L,s.originalElapsedNanos());assertEquals(1,s.originalClockVisited());assertEquals(0,s.originalClockTrue());
    }
    @Test void firstLatestRejectedQueryKeepExactCandidatePointCallWithoutEventList(){
        AtomicInteger c=new AtomicInteger();var f=new DynamicRouteQueryFence(()->c.getAndIncrement(),()->true,50);GroundDynamicPlanningDiagnostics.begin(f);
        GroundDynamicPlanningDiagnostics.candidate(0);GroundDynamicPlanningDiagnostics.point();GroundDynamicPlanningDiagnostics.noteCurrent("bounded");assertFalse(f.query(()->false));
        GroundDynamicPlanningDiagnostics.candidate(7);GroundDynamicPlanningDiagnostics.point();GroundDynamicPlanningDiagnostics.point();GroundDynamicPlanningDiagnostics.noteCurrent("sweep");assertFalse(f.query(()->false));
        var s=GroundDynamicPlanningDiagnostics.completeOriginal("ORIGINAL","none",true);assertEquals(0,s.firstRefusedQueryCandidate());assertEquals(0,s.firstRefusedQueryPoint());assertEquals("bounded",s.firstRefusedQueryCall());assertEquals(7,s.latestRefusedQueryCandidate());assertEquals(1,s.latestRefusedQueryPoint());assertEquals("sweep",s.latestRefusedQueryCall());assertEquals(2,s.diagnosticCandidatesTried());assertEquals(3,s.diagnosticPointsVisited());assertEquals(-1,s.reads());assertEquals(-1,s.bodies());assertSame(GroundDynamicPlanningDiagnostics.cachedFirstFailure(),GroundDynamicPlanningDiagnostics.cachedLatestFailure());
    }
    @Test void immutablePureArgumentsAndOriginalSeedAreDataOnly(){
        var f=new DynamicRouteQueryFence(()->0,()->true,50);GroundDynamicPlanningDiagnostics.begin(f);GroundDynamicPlanningDiagnostics.model(new Vec3(.5,70,.12),new Vec3(3,70,4),123,9);GroundDynamicPlanningDiagnostics.geometryCall("walk",new Vec3(1,70,2),null);GroundDynamicPlanningDiagnostics.returned("planner.originalFalse");var s=GroundDynamicPlanningDiagnostics.completeOriginal("ORIGINAL","none",true);assertEquals(.5D,s.originalOriginX());assertEquals(4D,s.originalGoalZ());assertEquals(123L,s.originalSeed());assertEquals(9L,s.originalOrdinal());assertEquals(1D,s.latestGeometryFromX());assertNull(s.latestGeometryToX());
    }
    @Test void foreignFenceAndExistingInitialDiagnosticNeverMergeNamespaces(){
        AtomicInteger ticks=new AtomicInteger();var f=new DynamicRouteQueryFence(()->ticks.getAndIncrement(),()->true,50);var foreign=new DynamicRouteQueryFence(()->100,()->false,50);GroundInitialDirectionalDiagnostics.begin(f);GroundDynamicPlanningDiagnostics.begin(f);assertFalse(f.query(()->false));assertFalse(foreign.query(()->false));var p=GroundDynamicPlanningDiagnostics.completeOriginal("ORIGINAL","none",true);var i=GroundInitialDirectionalDiagnostics.completeOriginal("CUSTODY_REFUSED",true);assertEquals(i.originalElapsedNanos(),p.originalElapsedNanos());assertEquals(i.firstRefusedQueryVisited(),p.firstRefusedQueryVisited());assertEquals(1,p.latestQueryOrdinal());assertEquals(1,i.latestQueryOrdinal());
    }
    @Test void invalidSlotSkippedOperandNestedDropAndExceptionAbandonRemainNonthrowing(){
        var a=new DynamicRouteQueryFence(()->0,()->true,50);var b=new DynamicRouteQueryFence(()->100,()->true,50);GroundDynamicPlanningDiagnostics.begin(a);AtomicInteger other=new AtomicInteger();assertTrue(GroundDynamicPlanningDiagnostics.copy(0,true)||GroundDynamicPlanningDiagnostics.copy(1,other.incrementAndGet()>0));assertEquals(0,other.get());assertTrue(GroundDynamicPlanningDiagnostics.copy(200,true));GroundDynamicPlanningDiagnostics.begin(b);assertTrue(b.available());var s=GroundDynamicPlanningDiagnostics.completeOriginal("ORIGINAL","nested",true);assertEquals("NESTED_SCOPE_DROPPED",s.diagnosticScopeStatus());assertEquals(0,s.visitedLow());assertNull(s.originalElapsedNanos());GroundDynamicPlanningDiagnostics.completeOriginal("ORIGINAL","outer",true);GroundDynamicPlanningDiagnostics.abandon();GroundDynamicPlanningDiagnostics.begin(a);GroundDynamicPlanningDiagnostics.abandon();assertNull(GroundDynamicPlanningDiagnostics.cachedFirstFailure());
    }
}
