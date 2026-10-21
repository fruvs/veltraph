package dev.veltraph.client.navigation;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

final class GroundInitialDirectionalDiagnosticsTest {
    @AfterEach void cleanup(){GroundInitialDirectionalDiagnostics.abandon();}
    @Test void originalQueryOrderAndSuccessfulPathAllocateNoSnapshot(){
        var events=new ArrayList<String>();AtomicInteger t=new AtomicInteger();var f=new DynamicRouteQueryFence(()->{events.add("clock");return t.getAndIncrement();},()->{events.add("current");return true;},50);
        GroundInitialDirectionalDiagnostics.begin(f);assertTrue(f.query(()->{events.add("body");return true;}));
        assertEquals(List.of("clock","clock","current","body","clock","current"),events);assertNull(GroundInitialDirectionalDiagnostics.completeOriginal("INITIAL_SUPPORTED",false));assertNull(GroundInitialDirectionalDiagnostics.cachedFailure());
    }
    @Test void originalCurrentBeforeFalseSkipsBodyPostClockAndCurrentAfter(){
        AtomicInteger clocks=new AtomicInteger(),currents=new AtomicInteger(),body=new AtomicInteger();var f=new DynamicRouteQueryFence(()->clocks.getAndIncrement(),()->{currents.incrementAndGet();return false;},50);
        GroundInitialDirectionalDiagnostics.begin(f);assertFalse(f.query(()->{body.incrementAndGet();return true;}));GroundInitialDirectionalDiagnostics.returned("outer.originalFalse");
        var s=GroundInitialDirectionalDiagnostics.completeOriginal("CUSTODY_REFUSED",true);assertEquals(2,clocks.get());assertEquals(1,currents.get());assertEquals(0,body.get());assertEquals(3,s.firstRefusedQueryVisited());assertEquals(1,s.firstRefusedQueryTrue());assertEquals("originalFence.query.originalFalseReturn",s.firstOriginalRefusalSite());assertEquals("outer.originalFalse",s.latestOriginalRefusalSite());
    }
    @Test void originalQueryBodyFalseSkipsPostAvailabilityAndCurrentAfter(){
        AtomicInteger clocks=new AtomicInteger();var f=new DynamicRouteQueryFence(()->clocks.getAndIncrement(),()->true,50);GroundInitialDirectionalDiagnostics.begin(f);assertFalse(f.query(()->false));
        var s=GroundInitialDirectionalDiagnostics.completeOriginal("CUSTODY_REFUSED",true);assertEquals(7,s.firstRefusedQueryVisited());assertEquals(3,s.firstRefusedQueryTrue());assertEquals(2,clocks.get());
    }
    @Test void lateOriginalPostBudgetFalseKeepsCurrentAfterSkippedAndClockOnce(){
        long[] values={0,1,51};AtomicInteger clocks=new AtomicInteger(),current=new AtomicInteger();var f=new DynamicRouteQueryFence(()->values[clocks.getAndIncrement()],()->{current.incrementAndGet();return true;},50);GroundInitialDirectionalDiagnostics.begin(f);assertFalse(f.query(()->true));
        var s=GroundInitialDirectionalDiagnostics.completeOriginal("CUSTODY_REFUSED",true);assertEquals(3,clocks.get());assertEquals(1,current.get());assertEquals(15,s.firstRefusedQueryVisited());assertEquals(7,s.firstRefusedQueryTrue());assertEquals(51L,s.originalElapsedNanos());assertFalse(s.originalAvailableResult());assertEquals(3,s.originalClockVisited());assertEquals(1,s.originalClockTrue());
    }
    @Test void negativeOriginalElapsedKeepsSecondClockComparisonSkipped(){
        long[] values={100,90};AtomicInteger clocks=new AtomicInteger();var f=new DynamicRouteQueryFence(()->values[clocks.getAndIncrement()],()->{fail("original current skipped");return true;},50);GroundInitialDirectionalDiagnostics.begin(f);assertFalse(f.query(()->{fail("original body skipped");return true;}));var s=GroundInitialDirectionalDiagnostics.completeOriginal("CUSTODY_REFUSED",true);assertEquals(-10L,s.originalElapsedNanos());assertEquals(1,s.originalClockVisited());assertEquals(0,s.originalClockTrue());
    }
    @Test void nestedForeignFenceCannotOverwriteBoundOriginalObservation(){
        AtomicInteger a=new AtomicInteger(),b=new AtomicInteger();var f=new DynamicRouteQueryFence(()->a.getAndIncrement(),()->true,50);var foreign=new DynamicRouteQueryFence(()->b.getAndIncrement()*100L,()->false,50);GroundInitialDirectionalDiagnostics.begin(f);assertTrue(f.available());assertFalse(foreign.query(()->true));var s=GroundInitialDirectionalDiagnostics.completeOriginal("CUSTODY_REFUSED",true);assertEquals(1L,s.originalElapsedNanos());assertNull(s.firstRefusedQueryOrdinal());assertNull(s.latestQueryOrdinal());
    }
    @Test void skippedGeometryOperandsInvalidSlotsAndCountersArePassive(){
        var f=new DynamicRouteQueryFence(()->0,()->true,50);GroundInitialDirectionalDiagnostics.begin(f);AtomicInteger second=new AtomicInteger();assertTrue(GroundInitialDirectionalDiagnostics.copy(16,true)||GroundInitialDirectionalDiagnostics.copy(17,second.incrementAndGet()>0));assertEquals(0,second.get());assertTrue(GroundInitialDirectionalDiagnostics.copy(-1,true));assertFalse(GroundInitialDirectionalDiagnostics.copy(128,false));GroundInitialDirectionalDiagnostics.counts(4,2,1,4090);GroundInitialDirectionalDiagnostics.cell(0,69,-1);GroundInitialDirectionalDiagnostics.returned("read.originalFalse");var s=GroundInitialDirectionalDiagnostics.completeOriginal("CUSTODY_REFUSED",true);assertEquals(1L<<16,s.visitedLow());assertEquals(4,s.planCells());assertEquals(4090,s.readRemaining());assertEquals(-1,s.cellZ());
    }
    @Test void exceptionAbandonAndNestedScopeDropReleaseLiveFenceAndNeverMerge(){
        var a=new DynamicRouteQueryFence(()->0,()->true,50);var b=new DynamicRouteQueryFence(()->100,()->true,50);GroundInitialDirectionalDiagnostics.begin(a);GroundInitialDirectionalDiagnostics.copy(16,true);GroundInitialDirectionalDiagnostics.begin(b);assertTrue(b.available());var s=GroundInitialDirectionalDiagnostics.completeOriginal("CUSTODY_REFUSED",true);assertEquals("NESTED_SCOPE_DROPPED",s.diagnosticScopeStatus());assertEquals(0,s.visitedLow());assertNull(s.originalElapsedNanos());assertNull(s.cellX());GroundInitialDirectionalDiagnostics.completeOriginal("CUSTODY_REFUSED",true);GroundInitialDirectionalDiagnostics.abandon();GroundInitialDirectionalDiagnostics.begin(a);GroundInitialDirectionalDiagnostics.abandon();assertTrue(a.available());assertNull(GroundInitialDirectionalDiagnostics.completeOriginal("none",true));
    }
}
