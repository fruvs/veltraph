package dev.veltraph.client.navigation;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class GroundLocomotionInputsTest {
    @Test void explicitSprintAndJumpOutputsUseOnlyExistingScopedKeys(){
        var input=new GroundWalkInputsTest.Inputs();
        GroundLocomotionInputs.apply(input,new GroundLocomotionPolicy.Decision(true,false,true,false,"Sprint"));
        assertTrue(input.keys.get("forward"));assertTrue(input.keys.get("sprint"));assertFalse(input.keys.get("jump"));
        GroundLocomotionInputs.apply(input,new GroundLocomotionPolicy.Decision(true,true,false,false,"Step"));
        assertTrue(input.keys.get("forward"));assertTrue(input.keys.get("jump"));assertFalse(input.keys.get("sprint"));
        for(String key:java.util.List.of("back","left","right","sneak"))assertFalse(input.keys.get(key));
        assertEquals(0,input.releases);
    }
    @Test void nativeDepartureReleasesJumpWithoutPulsingOwnedForward(){
        var input=new GroundWalkInputsTest.Inputs();
        GroundLocomotionInputs.apply(input,new GroundLocomotionPolicy.Decision(true,true,false,false,"Takeoff"));
        GroundLocomotionInputs.apply(input,new GroundLocomotionPolicy.Decision(true,false,false,false,"Airborne"));
        assertFalse(input.writes.contains("forward=false"));assertFalse(input.keys.get("jump"));assertTrue(input.keys.get("forward"));
    }
    @Test void rejectedFreshGeometryReleasesEveryMovementChannelImmediately(){
        var input=new GroundWalkInputsTest.Inputs();
        GroundLocomotionInputs.apply(input,new GroundLocomotionPolicy.Decision(true,true,true,false,"Primed"));
        GroundLocomotionInputs.apply(input,GroundLocomotionPolicy.Decision.hold("Rejected"));
        assertEquals(1,input.releases);assertTrue(input.keys.values().stream().noneMatch(Boolean::booleanValue));
    }
}
