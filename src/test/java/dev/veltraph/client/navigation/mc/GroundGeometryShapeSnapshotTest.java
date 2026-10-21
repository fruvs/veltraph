package dev.veltraph.client.navigation.mc;

import dev.veltraph.client.navigation.GroundGeometryCellJournal;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class GroundGeometryShapeSnapshotTest {
    private record Raw(double minX,double minY,double minZ,double maxX,double maxY,double maxZ) {}
    private static GroundGeometryCellJournal.Box box(Raw b){return new GroundGeometryCellJournal.Box(b.minX,b.minY,b.minZ,b.maxX,b.maxY,b.maxZ);}
    private static long[] bits(GroundGeometryCellJournal.Box b){return new long[]{Double.doubleToRawLongBits(b.minX()),Double.doubleToRawLongBits(b.minY()),Double.doubleToRawLongBits(b.minZ()),Double.doubleToRawLongBits(b.maxX()),Double.doubleToRawLongBits(b.maxY()),Double.doubleToRawLongBits(b.maxZ())};}
    private static GroundGeometryCellJournal.Observation observed(Object state,List<GroundGeometryCellJournal.Box> shape){return new GroundGeometryCellJournal.Observation(state,shape,.6F,1F,shape,true);}
    @Test void emptyShapeCallsNoMapperAndRemainsImmutable(){
        AtomicInteger calls=new AtomicInteger();var mapped=GroundGeometryShapeSnapshot.mapNonNull(List.<Raw>of(),b->{calls.incrementAndGet();return box(b);});
        assertTrue(mapped.isEmpty());assertEquals(0,calls.get());assertThrows(UnsupportedOperationException.class,()->mapped.add(new GroundGeometryCellJournal.Box(0,0,0,1,1,1)));
    }
    @Test void singletonMapsExactlyOnceWithoutAliasingSource(){
        var source=new ArrayList<>(List.of(new Raw(0,0,0,1,1,1)));AtomicInteger calls=new AtomicInteger();
        var mapped=GroundGeometryShapeSnapshot.mapNonNull(source,b->{calls.incrementAndGet();return box(b);});source.clear();
        assertEquals(1,calls.get());assertEquals(List.of(new GroundGeometryCellJournal.Box(0,0,0,1,1,1)),mapped);
        assertThrows(UnsupportedOperationException.class,()->mapped.set(0,new GroundGeometryCellJournal.Box(0,0,0,2,2,2)));
    }
    @Test void directLoopPreservesOriginalStreamEncounterOrderAndDuplicateBoxes(){
        var a=new Raw(1,2,3,4,5,6);var b=new Raw(6,5,4,3,2,1);var source=List.of(a,b,a);var visits=new ArrayList<Raw>();
        var mapped=GroundGeometryShapeSnapshot.mapNonNull(source,v->{visits.add(v);return box(v);});
        assertEquals(source,visits);assertEquals(source.stream().map(GroundGeometryShapeSnapshotTest::box).toList(),mapped);
        assertThrows(UnsupportedOperationException.class,()->mapped.remove(0));
    }
    @Test void allBoundedNativeShapeSizesPreserveEveryCoordinateBit(){
        for(int n=0;n<=128;n++){
            var source=new ArrayList<Raw>();for(int i=0;i<n;i++)source.add(new Raw(i%2==0?-0D:0D,Double.MIN_VALUE,-i,i+.5D,Double.MAX_VALUE,Double.longBitsToDouble(0x7ff8000000000001L)));
            var expected=source.stream().map(GroundGeometryShapeSnapshotTest::box).toList();var actual=GroundGeometryShapeSnapshot.mapNonNull(source,GroundGeometryShapeSnapshotTest::box);
            assertEquals(expected,actual);for(int i=0;i<n;i++)assertArrayEquals(bits(expected.get(i)),bits(actual.get(i)));
        }
    }
    @Test void repeatedFreshSnapshotsDoNotCacheOrHideDirtyFinalShape(){
        Object state=new Object();var source=new ArrayList<>(List.of(new Raw(0,0,0,1,1,1)));var journal=new GroundGeometryCellJournal(4096);var cell=new GroundGeometryCellJournal.Cell(0,69,0);
        assertTrue(journal.observe(cell,observed(state,GroundGeometryShapeSnapshot.mapNonNull(source,GroundGeometryShapeSnapshotTest::box))));
        source.set(0,new Raw(0,0,0,1,.9375D,1));
        assertFalse(journal.revalidate(c->observed(state,GroundGeometryShapeSnapshot.mapNonNull(source,GroundGeometryShapeSnapshotTest::box)),()->true));
    }
    @Test void changedFinalSupportShapeIsRefusedEvenWhenCollisionShapeAndStateAreIdentical(){
        Object state=new Object();var collision=List.of(new Raw(0,0,0,1,1,1));var support=new ArrayList<>(collision);var journal=new GroundGeometryCellJournal(4096);var cell=new GroundGeometryCellJournal.Cell(0,69,0);
        assertTrue(journal.observe(cell,new GroundGeometryCellJournal.Observation(state,
                GroundGeometryShapeSnapshot.mapNonNull(collision,GroundGeometryShapeSnapshotTest::box),.6F,1F,
                GroundGeometryShapeSnapshot.mapNonNull(support,GroundGeometryShapeSnapshotTest::box),true)));
        support.set(0,new Raw(0,0,0,1,.9375D,1));
        assertFalse(journal.revalidate(c->new GroundGeometryCellJournal.Observation(state,
                GroundGeometryShapeSnapshot.mapNonNull(collision,GroundGeometryShapeSnapshotTest::box),.6F,1F,
                GroundGeometryShapeSnapshot.mapNonNull(support,GroundGeometryShapeSnapshotTest::box),true),()->true));
    }
    @Test void identicalFreshShapesStillRefuseChangedNativeStateIdentity(){
        var source=List.of(new Raw(0,0,0,1,1,1));var journal=new GroundGeometryCellJournal(4096);var cell=new GroundGeometryCellJournal.Cell(0,69,0);
        assertTrue(journal.observe(cell,observed(new Object(),GroundGeometryShapeSnapshot.mapNonNull(source,GroundGeometryShapeSnapshotTest::box))));
        assertFalse(journal.revalidate(c->observed(new Object(),GroundGeometryShapeSnapshot.mapNonNull(source,GroundGeometryShapeSnapshotTest::box)),()->true));
    }
    @Test void originalBudgetRefusalSkipsFreshReaderWithoutAdoption(){
        var source=List.of(new Raw(0,0,0,1,1,1));Object state=new Object();var journal=new GroundGeometryCellJournal(4096);var cell=new GroundGeometryCellJournal.Cell(0,69,0);AtomicInteger reads=new AtomicInteger();
        assertTrue(journal.observe(cell,observed(state,GroundGeometryShapeSnapshot.mapNonNull(source,GroundGeometryShapeSnapshotTest::box))));
        assertFalse(journal.revalidate(c->{reads.incrementAndGet();return observed(state,GroundGeometryShapeSnapshot.mapNonNull(source,GroundGeometryShapeSnapshotTest::box));},()->false));assertEquals(0,reads.get());
    }
    @Test void snapshotsContinueOriginalJournalSizeAndRefusalBound(){
        var journal=new GroundGeometryCellJournal(1);Object state=new Object();var boxes=GroundGeometryShapeSnapshot.mapNonNull(List.of(new Raw(0,0,0,1,1,1)),GroundGeometryShapeSnapshotTest::box);
        assertTrue(journal.observe(new GroundGeometryCellJournal.Cell(0,0,0),observed(state,boxes)));
        assertFalse(journal.observe(new GroundGeometryCellJournal.Cell(1,0,0),observed(state,boxes)));assertEquals(1,journal.size());
    }
    @Test void pureMapperFailuresPreserveOrderedPrefixAndSnapshotsRejectNull(){
        var visits=new ArrayList<Integer>();assertThrows(IllegalStateException.class,()->GroundGeometryShapeSnapshot.mapNonNull(List.of(0,1,2),i->{visits.add(i);if(i==1)throw new IllegalStateException();return new GroundGeometryCellJournal.Box(i,0,0,i+1,1,1);}));
        assertEquals(List.of(0,1),visits);assertThrows(NullPointerException.class,()->GroundGeometryShapeSnapshot.mapNonNull(List.of(0),i->null));
        assertThrows(NullPointerException.class,()->GroundGeometryShapeSnapshot.mapNonNull(List.of(),null));
        assertThrows(NullPointerException.class,()->GroundGeometryShapeSnapshot.mapNonNull(null,GroundGeometryShapeSnapshotTest::box));
    }
}
