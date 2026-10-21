package dev.veltraph.client.navigation;
import dev.veltraph.client.world.Vec3;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
/** Adversarial dependency and budget tests. None supplies native input authority. */
class GroundStationaryNativeDependenciesTest {
    private static final GroundGeometryCellJournal.Cell CELL=new GroundGeometryCellJournal.Cell(0,69,0);
    private static GroundGeometryCellJournal.Observation observation(Object state,float friction,float speed,boolean sturdy,List<GroundGeometryCellJournal.Box> shape){
        return new GroundGeometryCellJournal.Observation(state,shape,friction,speed,shape,sturdy);
    }
    @Test void fullBodyIncludesOriginalBottomStripAndRejectsThinIntersection(){
        var p=new Vec3(.5,70,.12);var full=GroundStationaryBodyEnvelope.bounds(p,.6D,1.8D,.025D).orElseThrow();
        var old=GroundLandingBodyEnvelope.bounds(p,p,.6D,1.8D).orElseThrow();
        var thin=new GroundLandingBodyEnvelope.Bounds(.3,70.0002,-.1,.7,70.0008,.3);
        assertEquals(70D,full.minY());assertEquals(70.001D,old.minY());assertTrue(full.intersects(thin));assertFalse(old.intersects(thin));
    }
    @Test void bodyBoundsNeverWidenPaddingOrAcceptHugeAndInvalidTraits(){
        var p=new Vec3(.5,70,.12);
        assertTrue(GroundStationaryBodyEnvelope.bounds(p,.6,1.8,.02500001).isEmpty());
        assertTrue(GroundStationaryBodyEnvelope.bounds(p,Double.NaN,1.8,0).isEmpty());
        assertTrue(GroundStationaryBodyEnvelope.bounds(p,.6,0,0).isEmpty());
        assertTrue(GroundStationaryBodyEnvelope.bounds(new Vec3(Double.POSITIVE_INFINITY,70,0),.6,1.8,0).isEmpty());
    }
    @Test void finalNativeStateChangeRefusesEvenWhenCollisionShapeRestores(){
        var shape=List.of(new GroundGeometryCellJournal.Box(0,0,0,1,1,1));var initial=new Object();var changed=new Object();
        var journal=new GroundGeometryCellJournal(4096);assertTrue(journal.observe(CELL,observation(initial,.6F,1F,true,shape)));
        assertFalse(journal.revalidate(c->observation(changed,.6F,1F,true,shape),()->true));
    }
    @Test void supportSturdyFrictionSpeedAndCollisionShapeAreFinalDependencies(){
        var state=new Object();var shape=List.of(new GroundGeometryCellJournal.Box(0,0,0,1,1,1));
        for(var changed:List.of(observation(state,.5F,1F,true,shape),observation(state,.6F,.4F,true,shape),
                observation(state,.6F,1F,false,shape),observation(state,.6F,1F,true,List.of(new GroundGeometryCellJournal.Box(0,0,0,1,.9375,1))))){
            var journal=new GroundGeometryCellJournal(4096);assertTrue(journal.observe(CELL,observation(state,.6F,1F,true,shape)));
            assertFalse(journal.revalidate(c->changed,()->true));
        }
    }
    @Test void everyFinalUniqueCellMustBeReadAndBudgetCannotSkipTheRest(){
        var state=new Object();var value=observation(state,.6F,1F,true,List.of());var journal=new GroundGeometryCellJournal(4096);
        assertTrue(journal.observe(CELL,value));assertTrue(journal.observe(new GroundGeometryCellJournal.Cell(1,69,0),value));
        var reads=new AtomicInteger();assertFalse(journal.revalidate(c->{reads.incrementAndGet();return value;},()->reads.get()<1));assertEquals(1,reads.get());
    }
    @Test void originalFiftyMillisecondPostQueryFailureNeverBecomesPermission(){
        var time=new AtomicLong();var currentCalls=new AtomicInteger();var fence=new DynamicRouteQueryFence(time::get,()->{currentCalls.incrementAndGet();return true;},50_000_000L);
        assertFalse(fence.query(()->{time.set(51_491_542L);return true;}));assertEquals(1,currentCalls.get());assertFalse(fence.available());
    }
    @Test void expiredOriginalBudgetCannotBeRestartedByARevalidation(){
        var time=new AtomicLong();var fence=new DynamicRouteQueryFence(time::get,()->true,50_000_000L);
        time.set(50_000_001L);assertFalse(fence.query(()->true));assertFalse(fence.available());
        assertFalse(fence.query(()->{fail("Expired original fence must not execute a new native group");return true;}));
    }
}
