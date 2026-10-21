package dev.veltraph.client.combat.ghost;
import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

final class GhostSourceWalkReadProfileTest {
    @Test void cachedEvidenceNeverReadsManagementCountersAndLongestBatchPairsItsOwnDeltas(){
        var calls=new AtomicInteger();var profile=new GhostSourceWalkReadProfile(()->switch(calls.getAndIncrement()){
            case 0 -> new GhostSourceWalkReadProfile.RuntimeCounters(2,10,30,1000);
            case 1 -> new GhostSourceWalkReadProfile.RuntimeCounters(3,14,34,900);
            case 2 -> new GhostSourceWalkReadProfile.RuntimeCounters(3,14,34,900);
            default -> new GhostSourceWalkReadProfile.RuntimeCounters(3,14,35,1100);});
        var first=profile.beginBatch();profile.beginRead();profile.add(GhostSourceWalkReadProfile.Part.COLLISION_SHAPE,80);profile.recordRead(100);profile.endBatch(first,150);
        var second=profile.beginBatch();profile.beginRead();profile.recordRead(20);profile.endBatch(second,50);
        var evidence=profile.cached();assertEquals(4,calls.get());assertEquals(2,evidence.batches());
        assertEquals(100,evidence.longestRead().elapsedNanos());assertEquals(80,evidence.longestRead().componentNanos().get(GhostSourceWalkReadProfile.Part.COLLISION_SHAPE).longValue());
        assertEquals(new GhostSourceWalkReadProfile.Batch(150,100,1,4,4,-100),evidence.longestBatch());
        assertEquals(new GhostSourceWalkReadProfile.Sample(1,80,80),evidence.samples().get(GhostSourceWalkReadProfile.Part.COLLISION_SHAPE));
        profile.cached();assertEquals(4,calls.get());assertThrows(UnsupportedOperationException.class,()->evidence.samples().clear());
    }
    @Test void unavailableOrResetCountersRemainUnknownRatherThanZero(){
        var calls=new AtomicInteger();var profile=new GhostSourceWalkReadProfile(()->calls.getAndIncrement()==0?
            new GhostSourceWalkReadProfile.RuntimeCounters(-1,20,30,1000):new GhostSourceWalkReadProfile.RuntimeCounters(-1,10,-1,800));
        var before=profile.beginBatch();profile.add(GhostSourceWalkReadProfile.Part.CURRENT,-5);profile.endBatch(before,30);
        var batch=profile.cached().longestBatch();assertEquals(-1,batch.gcCollectionsDelta());assertEquals(-1,batch.gcMillisDelta());assertEquals(-1,batch.compilationMillisDelta());
        assertEquals(-200,batch.netHeapBytesDelta());assertEquals(0,profile.cached().samples().get(GhostSourceWalkReadProfile.Part.CURRENT).totalNanos());
    }
}
