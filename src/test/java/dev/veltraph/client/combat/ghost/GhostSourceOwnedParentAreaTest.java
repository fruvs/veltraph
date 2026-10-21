package dev.veltraph.client.combat.ghost;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import dev.veltraph.client.combat.ghost.GhostSourceOwnedParentArea.Stamp;
import dev.veltraph.client.combat.ghost.GhostSourceOrderedStandingProvider.ParentAreaObservation;

class GhostSourceOwnedParentAreaTest {
    @Test void unknownUntilOwnedConstructorNullIsObservedAndAbsentCannotReadField(){
        Object parent=new Object();var stamp=new Stamp(parent,1,2,new Object(),new Object(),new Object());
        var property=new GhostSourceOwnedParentArea(parent);
        assertEquals(ParentAreaObservation.UNKNOWN,property.capture(stamp).observation());
        property.observeOwnedConstructorAbsence(stamp);var receipt=property.capture(stamp);
        assertEquals(ParentAreaObservation.ABSENT,receipt.observation());assertTrue(receipt.current(stamp));
        assertThrows(IllegalStateException.class,()->receipt.fieldValue(new GhostSourceOrderedStandingProvider.Cell(0,0,0),stamp));
    }
    @Test void parentGenerationSettingsAndEachPwcIdentityDriftDenyOldReceipt(){
        Object parent=new Object(),player=new Object(),world=new String("world"),connection=new Object();
        var stamp=new Stamp(parent,1,2,player,world,connection);var property=new GhostSourceOwnedParentArea(parent);
        property.observeOwnedConstructorAbsence(stamp);var receipt=property.capture(stamp);
        for(var changed:new Stamp[]{new Stamp(new Object(),1,2,player,world,connection),new Stamp(parent,2,2,player,world,connection),
                new Stamp(parent,1,3,player,world,connection),new Stamp(parent,1,2,new Object(),world,connection),
                new Stamp(parent,1,2,player,new String("world"),connection),new Stamp(parent,1,2,player,world,new Object())}){
            assertFalse(receipt.current(changed));assertEquals(ParentAreaObservation.UNKNOWN,property.capture(changed).observation());
        }
    }
    @Test void invalidationAndReobservationNeverRegrantOldCallbackReceipt(){
        Object parent=new Object();var stamp=new Stamp(parent,1,2,new Object(),new Object(),new Object());
        var property=new GhostSourceOwnedParentArea(parent);property.observeOwnedConstructorAbsence(stamp);var first=property.capture(stamp);
        property.invalidate();assertFalse(first.current(stamp));property.observeOwnedConstructorAbsence(stamp);
        assertFalse(first.current(stamp));assertTrue(property.capture(stamp).current(stamp));
    }
    @Test void failedPresentVerificationRevokesAbsentRatherThanGuessingDefaultField(){
        Object parent=new Object();var stamp=new Stamp(parent,1,2,new Object(),new Object(),new Object());
        var property=new GhostSourceOwnedParentArea(parent);property.observeOwnedConstructorAbsence(stamp);var old=property.capture(stamp);
        assertThrows(IllegalArgumentException.class,()->property.observePresentResource(stamp,new byte[12],GhostSourceParentAreaResource.REV,10));
        assertFalse(old.current(stamp));assertEquals(ParentAreaObservation.UNKNOWN,property.capture(stamp).observation());
    }
    @Test void foreignParentObservationCannotProduceCurrentProperty(){
        Object parent=new Object();var property=new GhostSourceOwnedParentArea(parent);
        var foreign=new Stamp(new Object(),1,2,new Object(),new Object(),new Object());
        assertThrows(IllegalArgumentException.class,()->property.observeOwnedConstructorAbsence(foreign));
        assertEquals(ParentAreaObservation.UNKNOWN,property.capture(foreign).observation());
    }
}
