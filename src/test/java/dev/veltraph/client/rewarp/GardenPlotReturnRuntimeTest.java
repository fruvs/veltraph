package dev.veltraph.client.rewarp;

import dev.veltraph.client.game.GameArea;
import dev.veltraph.client.world.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GardenPlotReturnRuntimeTest {
    private final Object world=new Object();
    private final FarmReturnPoint target=new FarmReturnPoint("minecraft:overworld",GameArea.GARDEN,new Vec3(160.79,69.9375,.12),73,-12);
    private GardenPlotReturnRuntime.Observation at(double x,long now){return new GardenPlotReturnRuntime.Observation(true,world,"minecraft:overworld",GameArea.GARDEN,new Vec3(x,69.9375,.12),now);}
    @Test void recoveredDistanceAndPlotGeometryChooseOnlyKnownFarTargets(){
        assertEquals(11,GardenPlotReturnRuntime.targetPlot(target,at(0,0)));
        assertEquals(-1,GardenPlotReturnRuntime.targetPlot(target,at(40.79,0)),"strict source distance greater than120");
        assertEquals(-1,GardenPlotReturnRuntime.targetPlot(new FarmReturnPoint("minecraft:overworld",GameArea.GARDEN,new Vec3(0,70,0),0,0),at(160,0)),"Barn retains existing policy");
        assertEquals(-1,GardenPlotReturnRuntime.targetPlot(new FarmReturnPoint("minecraft:overworld",GameArea.GARDEN,new Vec3(400,70,0),0,0),at(0,0)));
        assertEquals(-1,GardenPlotReturnRuntime.targetPlot(new FarmReturnPoint("minecraft:overworld",GameArea.GARDEN,new Vec3(144.1,70,0),0,0),at(0,0)),"ambiguous map edge uses local travel");
        assertEquals(-1,GardenPlotReturnRuntime.targetPlot(new FarmReturnPoint("minecraft:overworld",GameArea.GARDEN,new Vec3(160.79,93,.12),0,0),at(0,0)),"outside recovered plot Y box uses local travel");
    }
    @Test void oneDispatchAndSuccessTextCannotReplaceActualPlotArrival(){
        int[] commands={0};var runtime=new GardenPlotReturnRuntime(id->{assertEquals(11,id);commands[0]++;return true;});
        assertTrue(runtime.start(target,at(0,0)));runtime.onServerMessage("Teleported you to Plot11!");runtime.tick(at(0,100));
        assertFalse(runtime.expectsTeleport(new Vec3(50,69.9375,.12),150));
        assertTrue(runtime.expectsTeleport(new Vec3(160,69.9375,.12),150));
        assertTrue(runtime.active());runtime.tick(at(160,200));
        assertEquals(GardenPlotReturnRuntime.State.READY_FOR_TRAVEL,runtime.state());assertEquals(1,commands[0]);
    }
    @Test void gradualMovementAndExactlyTwoBlocksCannotAcknowledgeTransport(){
        var runtime=new GardenPlotReturnRuntime(id->true);runtime.start(target,at(0,0));
        for(int x=2;x<=160;x+=2)runtime.tick(at(x,x));
        assertTrue(runtime.active());runtime.tick(at(160,10000));assertEquals(GardenPlotReturnRuntime.State.FAILED,runtime.state());
    }
    @Test void wrongPlotAndWorldReplacementFailWithoutCrossWorldOwnership(){
        var runtime=new GardenPlotReturnRuntime(id->true);runtime.start(target,at(0,0));runtime.tick(at(50,100));
        assertEquals(GardenPlotReturnRuntime.State.FAILED,runtime.state());
        runtime.start(target,at(0,0));runtime.tick(new GardenPlotReturnRuntime.Observation(true,new Object(),"minecraft:overworld",GameArea.GARDEN,target.position(),100));
        assertEquals(GardenPlotReturnRuntime.State.CANCELLED,runtime.state());
    }
    @Test void onlyOwnActiveServerRejectionCanSelectPermittedFlightFallback(){
        var runtime=new GardenPlotReturnRuntime(id->true);runtime.onServerMessage("You have not unlocked Plot11");assertEquals(GardenPlotReturnRuntime.State.IDLE,runtime.state());
        runtime.start(target,at(0,0));runtime.onServerMessage("§cCouldn't find Plot 11!");assertEquals(GardenPlotReturnRuntime.State.REJECTED,runtime.state());
        runtime.cancel();runtime.onServerMessage("Couldn't find Plot 11!");assertEquals(GardenPlotReturnRuntime.State.IDLE,runtime.state());
    }
    @Test void refusedCommandAndBackwardTimeFailWithoutRetry(){
        var refused=new GardenPlotReturnRuntime(id->false);assertTrue(refused.start(target,at(0,0)));assertEquals(GardenPlotReturnRuntime.State.FAILED,refused.state());
        var runtime=new GardenPlotReturnRuntime(id->true);runtime.start(target,at(0,100));runtime.tick(at(0,200));runtime.tick(at(0,199));
        assertEquals(GardenPlotReturnRuntime.State.FAILED,runtime.state());
    }
    @Test void observedPlotStillRevalidatesScopeAndSharesTheTransportDeadline(){
        var runtime=new GardenPlotReturnRuntime(id->true);runtime.start(target,at(0,0));runtime.tick(at(160,100));
        assertTrue(runtime.active());runtime.tick(at(160,10000));assertEquals(GardenPlotReturnRuntime.State.FAILED,runtime.state());
        runtime.start(target,at(0,0));runtime.tick(at(160,100));runtime.tick(new GardenPlotReturnRuntime.Observation(false,world,"minecraft:overworld",GameArea.GARDEN,target.position(),200));
        assertEquals(GardenPlotReturnRuntime.State.CANCELLED,runtime.state());
    }
}
