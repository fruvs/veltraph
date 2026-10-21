package dev.veltraph.client.skills;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class AlchemyApproachGateTest {
    @Test void enteringReachNeverDropsTheOriginalRoute(){var gate=new AlchemyApproachGate();gate.started();for(double range:new double[]{3.5,3.39,3.41,3.1,2.7})assertEquals(AlchemyApproachGate.Decision.WALK,gate.decide(range,.14));}
    @Test void arrivalMustDecelerateBeforeInteraction(){var gate=new AlchemyApproachGate();gate.started();gate.arrived();assertEquals(AlchemyApproachGate.Decision.SETTLE,gate.decide(2.7,.08));assertEquals(AlchemyApproachGate.Decision.AIM,gate.decide(2.7,.01));}
    @Test void aFreshFarTargetStartsOnceAndRemainsLatched(){var gate=new AlchemyApproachGate();assertEquals(AlchemyApproachGate.Decision.START,gate.decide(8,0));gate.started();assertEquals(AlchemyApproachGate.Decision.WALK,gate.decide(8,0));gate.reset();assertEquals(AlchemyApproachGate.Decision.START,gate.decide(8,0));}
    @Test void invalidNativeGeometryIsRejected(){var gate=new AlchemyApproachGate();assertThrows(IllegalArgumentException.class,()->gate.decide(Double.NaN,0));assertThrows(IllegalArgumentException.class,()->gate.decide(2,-1));}
}
