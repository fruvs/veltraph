package dev.veltraph.client.metrics;

import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class OwnerMetricEpochsTest {
    @Test void actualFreshStartCreatesGenerationAndQuestTotalsNeverBecomeBossKills(){
        var owner=new OwnerMetricEpochs();var kind=OwnerMetricEpochs.OwnerCounter.SLAYER_QUESTS;
        assertTrue(owner.sample(kind,5).isEmpty());var first=owner.freshStart(kind,5);var next=owner.sample(kind,6).orElseThrow();
        assertEquals(first.series(),next.series());assertEquals(2,next.serial());assertEquals(LocalSessionMetrics.Counter.CONFIRMED_SLAYER_QUESTS,next.counter());
        var restarted=owner.freshStart(kind,0);assertNotEquals(first.series(),restarted.series());assertEquals(1,restarted.serial());
        owner.clear();assertTrue(owner.sample(kind,7).isEmpty());
    }
    @Test void treeOwnersHaveIndependentCumulativeBaselinesButOneDeclaredTreeMetric(){
        var owner=new OwnerMetricEpochs();var ctx=new LocalSessionMetrics.Context(new Object(),new Object(),new Object());var metrics=new LocalSessionMetrics(MetricContracts.empty(),0,10000,3000,2000);metrics.bind(ctx,Map.of(),0);
        for(var kind:new OwnerMetricEpochs.OwnerCounter[]{OwnerMetricEpochs.OwnerCounter.AUTO_FORAGING_TREES,OwnerMetricEpochs.OwnerCounter.GALATEA_TREES}){
            var base=owner.freshStart(kind,20);metrics.cumulative(ctx,base.series(),base.counter(),base.serial(),base.cumulativeTotal(),0);var next=owner.sample(kind,22).orElseThrow();metrics.cumulative(ctx,next.series(),next.counter(),next.serial(),next.cumulativeTotal(),0);
        }assertEquals(4,metrics.snapshot(0).count(LocalSessionMetrics.Counter.CONFIRMED_TREES).orElseThrow());assertTrue(metrics.snapshot(0).count(LocalSessionMetrics.Counter.CONFIRMED_BOSS_KILLS).isEmpty());
    }
}
