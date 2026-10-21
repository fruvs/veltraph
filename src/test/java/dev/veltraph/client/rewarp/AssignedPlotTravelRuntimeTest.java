package dev.veltraph.client.rewarp;

import dev.veltraph.client.game.GameArea;
import dev.veltraph.client.world.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AssignedPlotTravelRuntimeTest {
    private final Object player=new Object(),world=new Object(),connection=new Object();
    private final AssignedPlotTravelPort.Target target=new AssignedPlotTravelPort.Target(3,new Vec3(96.5,70,.5),32,-12);
    private AssignedPlotTravelRuntime.Observation at(Vec3 p,long serial,Vec3 receipt,boolean supported,long now) {
        return new AssignedPlotTravelRuntime.Observation(true,player,world,connection,"minecraft:overworld",GameArea.GARDEN,p,supported,serial,receipt,now);
    }
    private AssignedPlotTravelRuntime.Observation origin(){return at(target.spawn(),7,target.spawn(),true,100);}
    @Test void samePoseAlwaysDispatchesOnceAndNeedsPostRequestNativeReceipt() {
        int[] commands={0};var r=new AssignedPlotTravelRuntime(id->{assertEquals(3,id);commands[0]++;return true;});
        assertTrue(r.start(target,origin()));r.onServerMessage("Teleported you to Plot3!");
        assertEquals(AssignedPlotTravelRuntime.State.WAITING_ARRIVAL,r.tick(target,at(target.spawn(),7,target.spawn(),true,200)));
        assertEquals(AssignedPlotTravelRuntime.State.ARRIVED,r.tick(target,at(target.spawn(),8,target.spawn(),true,300)));
        r.tick(target,at(target.spawn(),8,target.spawn(),true,400));assertEquals(1,commands[0]);
    }
    @Test void walkingNearbyOrFarCannotReplaceReceivedTeleport() {
        var r=new AssignedPlotTravelRuntime(id->true);assertTrue(r.start(target,at(new Vec3(94.5,70,.5),7,null,true,100)));
        assertEquals(AssignedPlotTravelRuntime.State.WAITING_ARRIVAL,r.tick(target,at(target.spawn(),7,null,true,200)));
    }
    @Test void freshReceiptNeedsSourceThreeDimensionalOneBlockAndLoadedActualSupport() {
        var r=new AssignedPlotTravelRuntime(id->true);r.start(target,origin());
        assertEquals(AssignedPlotTravelRuntime.State.WAITING_ARRIVAL,r.tick(target,at(target.spawn(),8,target.spawn(),false,200)));
        assertEquals(AssignedPlotTravelRuntime.State.ARRIVED,r.tick(target,at(new Vec3(97.5,70,.5),8,target.spawn(),true,300)));
        assertEquals(AssignedPlotTravelRuntime.State.FAILED,r.tick(target,at(new Vec3(97.500001,70,.5),8,target.spawn(),true,400)));
        r.start(target,origin());assertEquals(AssignedPlotTravelRuntime.State.FAILED,r.tick(target,at(target.spawn(),9,new Vec3(96.5,71.01,.5),true,200)));
    }
    @Test void wrongPlotOrWrongConfiguredSpawnFailsWithoutAnotherCommand() {
        int[] commands={0};var r=new AssignedPlotTravelRuntime(id->{commands[0]++;return true;});r.start(target,origin());
        assertEquals(AssignedPlotTravelRuntime.State.FAILED,r.tick(target,at(new Vec3(.5,70,.5),8,new Vec3(.5,70,.5),true,200)));assertEquals(1,commands[0]);
        r.start(target,origin());assertEquals(AssignedPlotTravelRuntime.State.FAILED,r.tick(target,at(target.spawn(),9,new Vec3(110.5,70,.5),true,200)));assertEquals(2,commands[0]);
    }
    @Test void identityConfigurationAreaAndGuiValidityArePinned() {
        var r=new AssignedPlotTravelRuntime(id->true);r.start(target,origin());
        assertEquals(AssignedPlotTravelRuntime.State.CANCELLED,r.tick(new AssignedPlotTravelPort.Target(3,target.spawn(),33,-12),origin()));
        for(int replaced=0;replaced<3;replaced++) {
            r.start(target,origin());var o=new AssignedPlotTravelRuntime.Observation(true,replaced==0?new Object():player,replaced==1?new Object():world,
                    replaced==2?new Object():connection,"minecraft:overworld",GameArea.GARDEN,target.spawn(),true,8,target.spawn(),200);
            assertEquals(AssignedPlotTravelRuntime.State.CANCELLED,r.tick(target,o));
        }
        r.start(target,origin());assertEquals(AssignedPlotTravelRuntime.State.CANCELLED,r.tick(target,new AssignedPlotTravelRuntime.Observation(false,player,world,connection,
                "minecraft:overworld",GameArea.GARDEN,target.spawn(),true,8,target.spawn(),200)));
    }
    @Test void deadlineRejectionBackwardTimeAndRefusedCommandDoNotRetry() {
        int[] commands={0};var r=new AssignedPlotTravelRuntime(id->{commands[0]++;return true;});r.start(target,origin());
        assertEquals(AssignedPlotTravelRuntime.State.FAILED,r.tick(target,at(target.spawn(),7,target.spawn(),true,10100)));assertEquals(1,commands[0]);
        r.start(target,origin());assertEquals(AssignedPlotTravelRuntime.State.FAILED,r.tick(target,at(target.spawn(),7,target.spawn(),true,99)));
        r.start(target,origin());r.onServerMessage("§cYou have not unlocked Plot3");assertEquals(AssignedPlotTravelRuntime.State.FAILED,r.state());
        var refused=new AssignedPlotTravelRuntime(id->false);assertFalse(refused.start(target,origin()));assertEquals(AssignedPlotTravelRuntime.State.FAILED,refused.state());
    }
    @Test void invalidPlotGeometryNeverSendsAndCancelRejectsLateReceipt() {
        int[] commands={0};var r=new AssignedPlotTravelRuntime(id->{commands[0]++;return true;});
        assertFalse(r.start(new AssignedPlotTravelPort.Target(3,new Vec3(.5,70,.5),0,0),origin()));assertEquals(0,commands[0]);
        r.start(target,origin());r.cancel();assertEquals(AssignedPlotTravelRuntime.State.IDLE,r.tick(target,at(target.spawn(),8,target.spawn(),true,200)));
    }
    @Test void expectedTeleportAcknowledgmentIsFreshScopedAndSingleUse() {
        var r=new AssignedPlotTravelRuntime(id->true);r.start(target,origin());
        assertFalse(r.consumeExpectedTeleport(target,origin(),target.spawn(),100));
        var fresh=at(target.spawn(),8,target.spawn(),true,200);
        assertFalse(r.consumeExpectedTeleport(target,fresh,new Vec3(.5,70,.5),200));
        assertTrue(r.consumeExpectedTeleport(target,fresh,target.spawn(),200));assertFalse(r.consumeExpectedTeleport(target,fresh,target.spawn(),200));
        r.cancel();assertFalse(r.consumeExpectedTeleport(target,fresh,target.spawn(),200));
    }
}
