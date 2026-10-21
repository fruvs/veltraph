package dev.veltraph.client.combat.ghost;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import dev.veltraph.client.combat.ghost.GhostSourceOwnedParentArea.Stamp;

class GhostNativeSearchPhaseTest {
    @Test void actualChildIsUnboundUntilSelectionPublishedAndDifferentTargetCannotBorrowReceipt(){
        Object parent=new Object(),target=new Object();var stamp=new Stamp(parent,1,2,new Object(),new Object(),new Object());
        var child=new GhostNativeSearchPhase(parent);assertEquals(GhostNativeSearchPhase.State.UNBOUND,child.state());assertNull(child.capture(stamp,target));
        child.observeSelected(stamp,target);var receipt=child.capture(stamp,target);
        assertSame(child,receipt.childIdentity());assertTrue(receipt.current(stamp,target));assertFalse(receipt.current(stamp,new Object()));
    }
    @Test void withdrawalOrNewSelectionDoesNotRegrantEarlierContinuation(){
        Object parent=new Object(),target=new Object();var stamp=new Stamp(parent,1,2,new Object(),new Object(),new Object());
        var child=new GhostNativeSearchPhase(parent);child.observeSelected(stamp,target);var old=child.capture(stamp,target);
        child.invalidate();assertFalse(old.current(stamp,target));child.observeSelected(stamp,target);
        assertFalse(old.current(stamp,target));assertTrue(child.capture(stamp,target).current(stamp,target));
    }
    @Test void changedParentFrameOrForeignParentCannotPublishSourceSelection(){
        Object parent=new Object(),target=new Object(),player=new Object(),world=new Object(),connection=new Object();
        var stamp=new Stamp(parent,1,2,player,world,connection);var child=new GhostNativeSearchPhase(parent);child.observeSelected(stamp,target);
        var receipt=child.capture(stamp,target);var changed=new Stamp(parent,2,2,player,world,connection);
        assertFalse(receipt.current(changed,target));assertNull(child.capture(changed,target));
        assertThrows(IllegalArgumentException.class,()->child.observeSelected(new Stamp(new Object(),1,2,player,world,connection),target));
        assertFalse(receipt.current(stamp,target));
    }
}
