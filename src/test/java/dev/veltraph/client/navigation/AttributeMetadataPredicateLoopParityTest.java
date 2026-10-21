package dev.veltraph.client.navigation;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Closed JDK captured-list predicate parity only; these metadata objects are not SDK attributes. */
final class AttributeMetadataPredicateLoopParityTest {
    private static class TrackedMetadata implements GroundPhysicsRevisionTracked {}
    private static final class SubMetadata extends TrackedMetadata {}
    private static final class UntrackedMetadata {}
    private static void same(List<?> rows,Class<?> expected){
        assertEquals(FrozenAGAttributeMetadataPredicate.invalid(rows,expected),ActualAGAttributeMetadataPredicate.invalid(rows,expected));
    }
    private static List<?> snapshot(Object[] rows){return Stream.of(rows).toList();}
    @Test void allSixExactTrackedCellsRemainValidInFreshCapturedSnapshot(){
        var rows=snapshot(new Object[]{new TrackedMetadata(),new TrackedMetadata(),new TrackedMetadata(),new TrackedMetadata(),new TrackedMetadata(),new TrackedMetadata()});
        same(rows,TrackedMetadata.class);assertFalse(ActualAGAttributeMetadataPredicate.invalid(rows,TrackedMetadata.class));
    }
    @Test void firstNullOrBadExactClassAtEveryPositionStillRefuses(){
        for(int at=0;at<6;at++)for(Object bad:new Object[]{null,new UntrackedMetadata(),new SubMetadata(),new Object()}){
            Object[] rows=new Object[6];for(int i=0;i<6;i++)rows[i]=new TrackedMetadata();rows[at]=bad;
            same(snapshot(rows),TrackedMetadata.class);assertTrue(ActualAGAttributeMetadataPredicate.invalid(snapshot(rows),TrackedMetadata.class));
        }
    }
    @Test void exactClassWithoutMarkerStillRefusesAndSubclassNeverPasses(){
        var untracked=snapshot(new Object[]{new UntrackedMetadata(),new UntrackedMetadata()});
        same(untracked,UntrackedMetadata.class);assertTrue(ActualAGAttributeMetadataPredicate.invalid(untracked,UntrackedMetadata.class));
        var sub=snapshot(new Object[]{new SubMetadata()});same(sub,TrackedMetadata.class);assertTrue(ActualAGAttributeMetadataPredicate.invalid(sub,TrackedMetadata.class));
    }
    @Test void emptyAndMixedClosedListsRetainStreamAnyMatchSemantics(){
        for(List<?> rows:List.of(List.of(),snapshot(new Object[]{null}),snapshot(new Object[]{new TrackedMetadata(),null,new Object()}),
                snapshot(new Object[]{new TrackedMetadata(),new TrackedMetadata()}),List.of(new TrackedMetadata())))same(rows,TrackedMetadata.class);
        assertFalse(ActualAGAttributeMetadataPredicate.invalid(List.of(),TrackedMetadata.class));
    }
    @Test void nullListAndNullExpectedClassKeepOriginalFailureOrBoolean(){
        assertThrows(NullPointerException.class,()->FrozenAGAttributeMetadataPredicate.invalid(null,TrackedMetadata.class));
        assertThrows(NullPointerException.class,()->ActualAGAttributeMetadataPredicate.invalid(null,TrackedMetadata.class));
        for(List<?> rows:List.of(List.of(),snapshot(new Object[]{null}),snapshot(new Object[]{new TrackedMetadata()})))same(rows,null);
    }
    @Test void deterministicMixedSnapshotsPreserveAllOriginalPredicateOutcomes(){
        Random random=new Random(0x43160);for(int sample=0;sample<1000;sample++){
            Object[] rows=new Object[random.nextInt(13)];for(int i=0;i<rows.length;i++)rows[i]=switch(random.nextInt(5)){
                case 0->new TrackedMetadata();case 1->new SubMetadata();case 2->new UntrackedMetadata();case 3->new Object();default->null;};
            same(snapshot(rows),TrackedMetadata.class);same(snapshot(rows),UntrackedMetadata.class);
        }
    }
}
