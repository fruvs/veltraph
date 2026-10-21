package dev.veltraph.client.navigation;
import dev.veltraph.client.service.InputService;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class GroundWalkInputsTest {
    static class Inputs implements InputService {
        final Map<String,Boolean> keys=new HashMap<>();final List<String> writes=new ArrayList<>();int releases;
        void write(String key,boolean held){keys.put(key,held);writes.add(key+"="+held);}
        public void setForwardHeld(boolean b){write("forward",b);}public void setBackHeld(boolean b){write("back",b);}
        public void setLeftHeld(boolean b){write("left",b);}public void setRightHeld(boolean b){write("right",b);}
        public void setSneakHeld(boolean b){write("sneak",b);}public void setJumpHeld(boolean b){write("jump",b);}public void setSprintHeld(boolean b){write("sprint",b);}
        public void releaseAllMovement(){releases++;for(String key:List.of("forward","back","left","right","sneak","jump","sprint"))write(key,false);}
    }
    @Test void ownedForwardDoesNotPulseOffWhileEveryOtherChannelIsExplicit() {
        var input=new Inputs();for(String key:List.of("back","left","right","sneak","sprint"))input.keys.put(key,true);
        for(int tick=0;tick<3;tick++)GroundWalkInputs.apply(input,new WalkController.Output(true,false,-45,WalkController.State.WALKING));
        assertEquals(0,input.releases);assertFalse(input.writes.contains("forward=false"));assertTrue(input.keys.get("forward"));
        for(String key:List.of("back","left","right","sneak","jump","sprint"))assertFalse(input.keys.get(key));assertEquals(21,input.writes.size());
    }
    @Test void turningAndLandingDoNotRetainPriorForwardOrJump() {
        var input=new Inputs();GroundWalkInputs.apply(input,new WalkController.Output(true,true,0,WalkController.State.WALKING));
        GroundWalkInputs.apply(input,new WalkController.Output(false,false,0,WalkController.State.WALKING));assertFalse(input.keys.get("forward"));assertFalse(input.keys.get("jump"));
    }
    @Test void EveryTerminalStateReleasesAllImmediately() {
        for(var state:List.of(WalkController.State.IDLE,WalkController.State.ARRIVED,WalkController.State.BLOCKED)){
            var input=new Inputs();GroundWalkInputs.apply(input,new WalkController.Output(true,true,0,WalkController.State.WALKING));
            GroundWalkInputs.apply(input,new WalkController.Output(true,true,0,state));assertEquals(1,input.releases);assertTrue(input.keys.values().stream().noneMatch(Boolean::booleanValue));
        }
    }
}
