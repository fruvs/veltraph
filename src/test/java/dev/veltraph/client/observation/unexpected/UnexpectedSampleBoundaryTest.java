package dev.veltraph.client.observation.unexpected;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static dev.veltraph.client.observation.unexpected.UnexpectedBehaviorMonitor.*;
final class UnexpectedSampleBoundaryTest {
    final Context owner=new Context(new Activation("farm",1,true),1,1,1);
    @Test void preAndPostSnapshotsOfSameUnchangedFrameHaveNoSecondEvent() {
        var before=Value.number(0);var slot=Value.number(2);
        assertTrue(UnexpectedSampleBoundary.changed(before,slot,Kind.SELECTED_SLOT,0));
        assertFalse(UnexpectedSampleBoundary.changed(slot,slot,Kind.SELECTED_SLOT,0));
    }
    @Test void ownerGenerationAndEveryNativeEpochRequireFreshBaseline() {
        assertTrue(UnexpectedSampleBoundary.comparable(owner,owner));
        for(var next:new Context[]{new Context(new Activation("farm",2,true),1,1,1),new Context(new Activation("slayer",1,true),1,1,1),new Context(owner.activation(),2,1,1),new Context(owner.activation(),1,2,1),new Context(owner.activation(),1,1,2)})assertFalse(UnexpectedSampleBoundary.comparable(owner,next));
    }
    @Test void smallDistinctOwnedPreAndPostChangesRemainObservable() {
        assertTrue(UnexpectedSampleBoundary.changed(Value.rotation(0,0),Value.rotation(6,0),Kind.ROTATION,.5));
        assertTrue(UnexpectedSampleBoundary.changed(Value.rotation(6,0),Value.rotation(7,0),Kind.ROTATION,.5));
        assertFalse(UnexpectedSampleBoundary.changed(Value.rotation(359,0),Value.rotation(-1,0),Kind.ROTATION,.5));
    }
}
