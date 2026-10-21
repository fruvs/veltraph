package dev.veltraph.client.galatea;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
final class FigJumpLatchTest {
    @Test void requestPersistsThroughFlightUntilGlobalReleaseEquivalent() {
        var latch=new FigJumpLatch();assertTrue(latch.observe(0,0,0));
        assertTrue(latch.observe(0,.42,0));assertTrue(latch.observe(0,-.3,0));
        assertTrue(latch.observe(0,0,0));latch.release();assertFalse(latch.held());
    }
    @Test void strictThresholdIncludesVerticalAndBothHorizontalAxes() {
        var latch=new FigJumpLatch();assertFalse(latch.observe(0,.1,0));
        assertFalse(latch.observe(.08,0,.08));assertTrue(latch.observe(.03,.04,.05));
    }
    @Test void landingPromotesCounterForBothEqualPriorityCacheOrders() {
        for(boolean updaterFirst:new boolean[]{true,false}) {
            var latch=new FigJumpLatch();var cache=new FigEndDisplacementCache();
            var counter=new FigJumpCounter();counter.enable(3,0);counter.calculatingCandidate();
            Object player=new Object(),world=new Object(),connection=new Object();cache.bind(player,world,connection);
            double[] motion={0,0,.42,.33,.24,.16,.08,0,-.08,-.16,-.25,-.34,0,0,0};
            for(int tick=0;tick<motion.length;tick++) {
                boolean ground=tick<2||tick>=12;
                if(updaterFirst)cache.refresh(player,world,connection,0,motion[tick],0);
                counter.observeTarget(tick,false,false,false,false);
                counter.observeJumpTick(ground,latch.held());
                var sample=cache.sample();if(sample!=null)latch.observe(sample.x(),sample.y(),sample.z());
                if(!updaterFirst)cache.refresh(player,world,connection,0,motion[tick],0);
            }
            assertTrue(counter.counter()>=2,"cache order="+updaterFirst);
            assertEquals(FigJumpAimPolicy.Mode.STRONG,FigJumpAimPolicy.effective(FigJumpAimPolicy.Mode.LAZY,counter.counter()));
        }
    }
    @Test void cacheRefusesPlayerWorldConnectionDriftUntilExplicitActivationBind() {
        Object player=new Object(),world=new Object(),connection=new Object();
        for(int drift=0;drift<3;drift++) {
            var cache=new FigEndDisplacementCache();var latch=new FigJumpLatch();
            cache.bind(player,world,connection);
            assertTrue(cache.refresh(player,world,connection,0,0,0));
            var sample=cache.sample();assertTrue(latch.observe(sample.x(),sample.y(),sample.z()));
            Object nextPlayer=drift==0?new Object():player,nextWorld=drift==1?new Object():world,nextConnection=drift==2?new Object():connection;
            if(!cache.refresh(nextPlayer,nextWorld,nextConnection,0,0,0))latch.release();
            assertNull(cache.sample());assertFalse(latch.held());
            assertFalse(cache.refresh(nextPlayer,nextWorld,nextConnection,0,0,0));
            cache.bind(nextPlayer,nextWorld,nextConnection);
            assertTrue(cache.refresh(nextPlayer,nextWorld,nextConnection,.03,.04,.05));
            assertEquals(new FigEndDisplacementCache.Sample(.03,.04,.05),cache.sample());
            assertFalse(cache.refresh(nextPlayer,nextWorld,null,0,0,0));assertNull(cache.sample());
        }
    }
}
