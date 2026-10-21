package dev.veltraph.client.fishing.setup;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class GrappleRuntimeTest {
    @Test void sourceHazardHysteresisAndFarTrigger(){var gate=new GrappleRuntime.Eligibility();assertTrue(gate.test(15,false,false));for(int i=0;i<4;i++)assertFalse(gate.test(10,true,false));assertTrue(gate.test(10,true,false));for(int i=0;i<3;i++)assertTrue(gate.test(10,false,false));assertFalse(gate.test(10,false,false));}
    @Test void requestNeedsOutcomeAndDoesNotRepeatOnTimeout(){var f=new GrappleRuntime(100,1000);f.tick(input(true,true,false,false,false,false,0));assertEquals(GrappleRuntime.State.WAIT_REEL,f.state());f.tick(input(false,false,false,false,false,false,1));assertEquals(GrappleRuntime.Action.PREPARE,f.tick(input(false,false,false,false,false,false,2)));f.tick(input(false,false,true,false,false,false,3));assertEquals(GrappleRuntime.Action.USE,f.tick(input(false,false,true,true,false,false,4)));assertEquals(GrappleRuntime.Action.NONE,f.tick(input(false,false,true,true,false,false,105)));assertEquals(GrappleRuntime.State.FAILED,f.state());assertEquals(GrappleRuntime.Action.NONE,f.tick(input(false,false,true,true,true,true,106)));}
    private GrappleRuntime.Input input(boolean start,boolean bobber,boolean prepared,boolean aligned,boolean ack,boolean restored,long now){return new GrappleRuntime.Input(start,bobber,prepared,false,true,aligned,ack,restored,true,true,now);}
    @Test void unknownToolTimingsAreMandatory(){assertThrows(IllegalArgumentException.class,()->GrappleProfile.parse("{}"));assertThrows(IllegalArgumentException.class,()->new GrappleProfile("Fixture","minecraft:blaze_rod","",false,14d,.5,0,0L,100L,1000L));}
}
