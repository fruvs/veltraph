package dev.veltraph.client.observation.unexpected;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static dev.veltraph.client.observation.unexpected.UnexpectedBehaviorMonitor.*;

/** Pure one-event consumer tests. These do not mint or validate a native transport proof. */
final class IssuedReturnObservationTest {
    private final Context original=new Context(new Activation("farm_return",1,true),1,1,1);
    private final UnexpectedBehaviorMonitor monitor=new UnexpectedBehaviorMonitor(f->{});
    private static final class Port implements UnexpectedBehaviorMonitor.IssuedReturnObservation {
        private Observation event;
        Port(Observation event){this.event=event;}
        public Observation consume(){var result=event;event=null;return result;}
    }
    private Observation movement(Context context,long now){return new Observation(context,Kind.POSITION,Value.position(0,0,0),Value.position(30,0,0),now);}
    @Test void exactNativeEventLeavesNoWindowForSecondMovement(){
        monitor.context(original,100);
        var finding=monitor.observeIssuedReturn(new Port(movement(original,101)));
        assertEquals(Classification.EXPLAINED,finding.classification());
        assertEquals(java.util.Set.of(Cause.ROOT_ISSUED_FARM_RETURN),finding.evidence());
        assertEquals(Classification.UNEXPECTED,monitor.observe(movement(original,102)).classification());
    }
    @Test void consumedHandoutCannotExplainAnotherEvent(){
        monitor.context(original,100);var port=new Port(movement(original,101));
        monitor.observeIssuedReturn(port);assertThrows(IllegalStateException.class,()->monitor.observeIssuedReturn(port));
    }
    @Test void changedOwnerHasNoIssuedAttribution(){
        monitor.context(original,100);var replacement=new Context(new Activation("foreign",2,true),1,1,1);
        var finding=monitor.observeIssuedReturn(new Port(movement(replacement,101)));
        assertEquals(Classification.UNCERTAIN,finding.classification());
        assertFalse(finding.evidence().contains(Cause.ROOT_ISSUED_FARM_RETURN));
    }
    @Test void unrelatedRotationIsNotAnIssuedTransition(){
        monitor.context(original,100);
        var finding=monitor.observeIssuedReturn(new Port(new Observation(original,Kind.ROTATION,Value.rotation(0,0),Value.rotation(90,0),101)));
        assertEquals(Classification.UNEXPECTED,finding.classification());
        assertFalse(finding.evidence().contains(Cause.ROOT_ISSUED_FARM_RETURN));
    }
    @Test void joinedContextExplainsOnlyItsOwnMatchedEvent(){
        var joined=new Context(original.activation(),2,2,2);monitor.context(joined,100);
        var finding=monitor.observeIssuedReturn(new Port(new Observation(joined,Kind.CONTEXT,Value.number(1),Value.number(2),101)));
        assertEquals(Classification.EXPLAINED,finding.classification());
        var ordinary=monitor.observe(new Observation(joined,Kind.CONTEXT,Value.number(2),Value.number(3),102));
        assertEquals(Classification.UNCERTAIN,ordinary.classification());
        assertFalse(ordinary.evidence().contains(Cause.ROOT_ISSUED_FARM_RETURN));
    }
    @Test void pairedNativeViewExplainsOnlyOneMatchedView(){
        monitor.context(original,100);
        var event=new Observation(original,Kind.ROTATION,Value.rotation(0,0),Value.rotation(90,0),101);
        var port=new UnexpectedBehaviorMonitor.IssuedReturnObservation(){
            private Observation pending=event;
            public Observation consume(){var result=pending;pending=null;return result;}
            public boolean pairedView(){return true;}
        };
        assertEquals(Classification.EXPLAINED,monitor.observeIssuedReturn(port).classification());
        assertThrows(IllegalStateException.class,()->monitor.observeIssuedReturn(port));
        assertEquals(Classification.UNEXPECTED,monitor.observe(new Observation(original,Kind.ROTATION,
                Value.rotation(90,0),Value.rotation(180,0),102)).classification());
    }
    @Test void pairedViewDoesNotCrossActivationBoundary(){
        monitor.context(original,100);var foreign=new Context(new Activation("foreign",2,true),1,1,1);
        var port=new UnexpectedBehaviorMonitor.IssuedReturnObservation(){
            public Observation consume(){return new Observation(foreign,Kind.ROTATION,Value.rotation(0,0),Value.rotation(90,0),101);}
            public boolean pairedView(){return true;}
        };
        var finding=monitor.observeIssuedReturn(port);assertEquals(Classification.UNCERTAIN,finding.classification());
        assertFalse(finding.evidence().contains(Cause.ROOT_ISSUED_FARM_RETURN));
    }
}
