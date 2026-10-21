package dev.veltraph.client.navigation.mc;
import java.util.BitSet;
import java.util.IdentityHashMap;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class CollectionSceneWatchTest {
    private static CollectionSceneWatch watch(Object container) {
        BitSet bits=new BitSet(); bits.set(37); bits.set(4095);
        IdentityHashMap<Object,BitSet> selected=new IdentityHashMap<>(); selected.put(container,bits);
        return new CollectionSceneWatch(selected);
    }
    @Test void selectedNativeCellABARevokesEvenIfValueIdentityWasRestored() {
        Object section=new Object(); CollectionSceneWatch watch=watch(section);
        watch.cellWrite(section,37); // AIR -> solid
        watch.cellWrite(section,37); // solid -> original AIR
        assertFalse(watch.current());
    }
    @Test void otherCellAndOtherContainerDoNotRevokeWatchedNativeCells() {
        Object section=new Object(); CollectionSceneWatch watch=watch(section);
        watch.cellWrite(section,38); watch.cellWrite(new Object(),37); assertTrue(watch.current());
        watch.cellWrite(section,4095); assertFalse(watch.current());
    }
    @Test void fullSectionNativePacketReadRevokesRegardlessOfRestoredShape() {
        Object section=new Object(); CollectionSceneWatch watch=watch(section);
        watch.cellWrite(section,-1); assertFalse(watch.current());
    }
    @Test void colliderEntryAndReturnOutsideRemainRevoked() {
        Object world=new Object(),player=new Object(),owned=new Object(),collider=new Object();
        CollectionSceneWatch watch=watch(new Object());
        entity(watch,world,world,player,owned,collider,8,0,8,9,2,9); assertTrue(watch.current());
        entity(watch,world,world,player,owned,collider,0.9,0,0,1.9,2,1); assertFalse(watch.current());
        entity(watch,world,world,player,owned,collider,8,0,8,9,2,9); assertFalse(watch.current());
    }
    @Test void onlyExactOwnedItemAndPlayerAreExcludedOtherItemsStillRevoke() {
        Object world=new Object(),player=new Object(),owned=new Object();
        CollectionSceneWatch watch=watch(new Object());
        entity(watch,world,world,player,owned,player,0,0,0,1,2,1);
        entity(watch,world,world,player,owned,owned,0,0,0,1,2,1); assertTrue(watch.current());
        entity(watch,world,world,player,owned,new Object(),0,0,0,1,2,1); assertFalse(watch.current());
    }
    @Test void foreignWorldAndTouchingOutsideDoNotRevoke() {
        Object world=new Object(); CollectionSceneWatch watch=watch(new Object());
        entity(watch,world,new Object(),new Object(),new Object(),new Object(),0,0,0,1,2,1);
        entity(watch,world,world,new Object(),new Object(),new Object(),1,0,0,2,2,1); assertTrue(watch.current());
    }
    @Test void hiddenTrackedHiddenVisibilityABARevokesAnUnmovedCollider() {
        Object world=new Object(),player=new Object(),owned=new Object(),parkedCollider=new Object();
        CollectionSceneWatch watch=watch(new Object());
        // Visibility observer uses actual section contents even when a prior native query hid them.
        entity(watch,world,world,player,owned,parkedCollider,0,0,0,1,2,1);
        entity(watch,world,world,player,owned,parkedCollider,0,0,0,1,2,1);
        assertFalse(watch.current());
    }
    @Test void outsideSectionVisibilityChangesPreserveTheWatchedRegion() {
        Object world=new Object(); CollectionSceneWatch watch=watch(new Object());
        entity(watch,world,world,new Object(),new Object(),new Object(),20,0,20,21,2,21);
        assertTrue(watch.current());
    }
    private static void entity(CollectionSceneWatch watch,Object world,Object actualWorld,Object player,Object owned,Object entity,
        double x0,double y0,double z0,double x1,double y1,double z1) {
        watch.entityWrite(world,actualWorld,player,owned,entity,0,0,0,1,2,1,x0,y0,z0,x1,y1,z1);
    }
}
