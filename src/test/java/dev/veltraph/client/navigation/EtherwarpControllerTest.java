package dev.veltraph.client.navigation;
import dev.veltraph.client.world.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class EtherwarpControllerTest {
    final BlockPos target=new BlockPos(10,70,0);final Vec3 landing=new Vec3(10.5,71,.5),start=new Vec3(.5,71,.5);
    EtherwarpGate.Observation hit(BlockPos block,Direction face,double speed) { return new EtherwarpGate.Observation(block,face,71,speed,true,true,10); }
    @Test void exactFaceSpeedAndAllowedRegionAreRequired() {
        assertFalse(EtherwarpGate.rejection(target,Direction.UP,hit(target,Direction.DOWN,0),70,71).isEmpty());
        assertFalse(EtherwarpGate.rejection(target,Direction.UP,hit(target.up(),Direction.UP,0),70,71).isEmpty());
        assertFalse(EtherwarpGate.rejection(target,Direction.UP,hit(target,Direction.UP,.201),70,71).isEmpty());
        assertEquals("",EtherwarpGate.rejection(target,Direction.UP,hit(target,Direction.UP,.2),70,71));
        assertFalse(EtherwarpGate.rejection(target,Direction.UP,hit(target,Direction.UP,0),0,69).isEmpty());
    }
    @Test void usesOnlyOnceAndNeedsActualArrival() {
        var c=new EtherwarpController();c.start(target,Direction.UP,landing,0);
        assertEquals(EtherwarpController.Action.AIM_AND_SNEAK,c.tick(new EtherwarpController.Input(start,true,true,true,hit(target,Direction.UP,0),50),2,1000));
        assertEquals(EtherwarpController.Action.USE,c.tick(new EtherwarpController.Input(start,true,true,true,hit(target,Direction.UP,0),100),2,1000));
        assertEquals(EtherwarpController.Action.NONE,c.tick(new EtherwarpController.Input(start,true,true,true,hit(target,Direction.UP,0),200),2,1000));
        assertEquals(EtherwarpController.State.WAITING_TELEPORT,c.state());
        c.tick(new EtherwarpController.Input(landing,true,true,false,null,300),2,1000);
        assertEquals(EtherwarpController.State.ARRIVED,c.state());
    }
    @Test void losingStableAimResetsDwellAndMissingTeleportTimesOut() {
        var c=new EtherwarpController();c.start(target,Direction.UP,landing,0);
        c.tick(new EtherwarpController.Input(start,true,true,true,hit(target,Direction.UP,0),50),2,1000);
        c.tick(new EtherwarpController.Input(start,true,true,true,null,100),2,1000);
        assertEquals(EtherwarpController.Action.AIM_AND_SNEAK,c.tick(new EtherwarpController.Input(start,true,true,true,hit(target,Direction.UP,0),150),2,1000));
        assertEquals(EtherwarpController.Action.USE,c.tick(new EtherwarpController.Input(start,true,true,true,hit(target,Direction.UP,0),200),2,1000));
        c.tick(new EtherwarpController.Input(start,true,true,true,null,1201),2,1000);
        assertEquals(EtherwarpController.State.FAILED,c.state());
    }
}
