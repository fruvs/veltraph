package dev.veltraph.client.farming.movement.planner;

import dev.veltraph.client.farming.movement.TurnDirection;
import dev.veltraph.client.service.FarmServices;
import dev.veltraph.client.service.stub.NoOpInventoryService;
import dev.veltraph.client.service.stub.NoOpRotationService;
import dev.veltraph.client.testutil.*;
import dev.veltraph.client.world.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MelonKingDeWaterPlannerTest {
    private static final class World implements MelonKingDeWaterPlanner.World {
        boolean supported=true, loaded=true, obstructed, transferClear=true, crop=true;
        int spacing=3;
        boolean sprint, reverseBefore, lateralEvidence;
        public boolean supportedDesignPosition(){return supported;}
        public boolean sideLoaded(TurnDirection side){return loaded;}
        public boolean sideObstructed(TurnDirection side){return obstructed;}
        public boolean transferClear(){return transferClear;}
        public int nextLaneDistance(TurnDirection side){return spacing;}
        public boolean cropTargeted(){return crop;}
        public boolean sprintWhileFarming(){return sprint;}
        public boolean reverseBeforeTransfer(){return reverseBefore;}
        public double obstructionMotion(Vec3 current,Vec3 previous){return lateralEvidence?Math.abs(current.x()-previous.x()):current.horizontalDistanceTo(previous);}
    }
    private final World world=new World();
    private final FakePlayerService player=new FakePlayerService();
    private final RecordingInputService input=new RecordingInputService();
    private final RecordingBlockBreaking breaking=new RecordingBlockBreaking();
    private final MelonKingDeWaterPlanner planner=new MelonKingDeWaterPlanner(
            new FarmServices(new FakeClock(),player,new FakeWorldService(),input,
                    new NoOpRotationService(),breaking,new NoOpInventoryService()),world);

    @Test void farmingUsesLiteralForwardAndConfiguredSideOnly() {
        planner.start(TurnDirection.LEFT); planner.tick();
        assertTrue(input.forward && input.left && breaking.breaking);
        assertFalse(input.right || input.back || input.sneak);
    }
    @Test void steadyWaterLaneAppliesHeldStateWithoutReleaseRepressPulses() {
        world.sprint=true;planner.start(TurnDirection.LEFT);
        int releases=input.releaseAllCount;
        for(int tick=0;tick<20;tick++)planner.tick();
        assertEquals(releases,input.releaseAllCount);
        assertTrue(input.forward&&input.left&&input.sprint);assertFalse(input.jump||input.sneak);
        planner.pause("unknown position");assertTrue(input.releaseAllCount>releases);
        assertFalse(input.forward||input.left||input.sprint);
    }
    @Test void protectedNonCropCrosshairReleasesAttackWhileKeepingTheWaterLaneInput() {
        planner.start(TurnDirection.RIGHT); planner.tick(); assertTrue(breaking.breaking);
        world.crop=false; planner.tick();
        assertFalse(breaking.breaking); assertTrue(input.forward && input.right);
    }
    @Test void loadedWaterSupportCanDriveWhileNotGroundedButUnknownAirCannot() {
        player.onGround=false;
        planner.start(TurnDirection.LEFT); planner.tick();
        assertTrue(input.left,"World has verified bounded support/fluid design position");
        world.supported=false; planner.tick();
        assertFalse(input.forward || input.left || breaking.breaking);
    }
    @Test void slidingAtAnEndDoesNotBecomeAStationaryTransition() {
        planner.start(TurnDirection.LEFT); world.obstructed=true;
        for(int i=0;i<5;i++) {
            player.position=new Vec3(i*.1,64,.7); planner.tick();
        }
        assertEquals(MelonKingDeWaterPlanner.State.FARMING,planner.state());
    }
    @Test void mkForwardProgressAlongSnowDoesNotHideBlockedSideAxis() {
        world.lateralEvidence=true;world.obstructed=true;planner.start(TurnDirection.LEFT);
        player.position=new Vec3(.7,64,.7);planner.tick();
        player.position=new Vec3(.7,64,.9);planner.tick();
        world.spacing=2;player.position=new Vec3(.7,64,1.1);planner.tick();
        assertEquals(MelonKingDeWaterPlanner.State.TRANSFERRING,planner.state());
        world.obstructed=false;player.position=new Vec3(.7,64,3.7);planner.tick();
        assertEquals(MelonKingDeWaterPlanner.State.FARMING,planner.state());assertEquals(TurnDirection.RIGHT,planner.side());
    }
    @Test void mkLateralSlidingStillRequiresObservedStop() {
        world.lateralEvidence=true;world.obstructed=true;planner.start(TurnDirection.LEFT);
        for(int i=0;i<6;i++){player.position=new Vec3(i*.1,64,.7+i*.2);planner.tick();}
        assertEquals(MelonKingDeWaterPlanner.State.FARMING,planner.state());
    }
    private void reachEnd() {
        world.obstructed=true;
        planner.tick(); planner.tick(); planner.tick();
        assertEquals(MelonKingDeWaterPlanner.State.TRANSFERRING,planner.state());
    }
    @Test void actualThreeThenFourBlockWaterSpacingTransfersWithoutViewFlips() {
        planner.start(TurnDirection.LEFT); reachEnd();
        assertTrue(input.forward); assertFalse(input.left || input.right || breaking.breaking);
        player.position=new Vec3(0.5,64,2.5); planner.tick();
        assertEquals(MelonKingDeWaterPlanner.State.TRANSFERRING,planner.state(),"old fractional centre isn't arrival");
        world.obstructed=false; player.position=new Vec3(.5,64,3.7); planner.tick();
        assertEquals(TurnDirection.RIGHT,planner.side()); assertTrue(input.forward && input.right);
        world.spacing=4; reachEnd();
        world.obstructed=false; player.position=new Vec3(.5,64,7.7); planner.tick();
        assertEquals(TurnDirection.LEFT,planner.side()); assertTrue(input.forward && input.left);
    }
    @Test void absentNextWaterLaneStopsRatherThanInventingAnExit() {
        planner.start(TurnDirection.LEFT); world.obstructed=true; world.spacing=-1;
        planner.tick(); planner.tick(); planner.tick();
        assertEquals(MelonKingDeWaterPlanner.State.BLOCKED,planner.state());
        assertFalse(input.forward || input.left || breaking.breaking);
    }
    @Test void blockedTransferTimesOutAndStopReleasesAllInputs() {
        planner.start(TurnDirection.LEFT); reachEnd(); world.transferClear=false;
        for(int i=0;i<161;i++) planner.tick();
        assertEquals(MelonKingDeWaterPlanner.State.BLOCKED,planner.state());
        assertFalse(input.forward || input.left || breaking.breaking);
        planner.start(TurnDirection.RIGHT); world.obstructed=false; planner.tick();
        planner.stop(); assertFalse(input.forward || input.right || breaking.breaking);
    }
    @Test void pauseDropsTheStationaryObservationAndResumeRequiresFreshEvidence() {
        planner.start(TurnDirection.LEFT); world.obstructed=true; planner.tick(); planner.tick();
        planner.pause("aim correction"); planner.tick();
        assertEquals(MelonKingDeWaterPlanner.State.FARMING,planner.state());
    }
    @Test void saigoSixBlockCornerReversesOnceBeforeTransferAndOwnsSprintCleanup() {
        world.sprint=true; world.reverseBefore=true; world.spacing=6;
        planner.start(TurnDirection.LEFT); planner.tick();
        assertTrue(input.sprint && input.forward && input.left);
        reachEnd();
        assertEquals(TurnDirection.RIGHT,planner.side());
        assertTrue(input.sprint && input.forward && input.right);
        world.obstructed=false; player.position=new Vec3(.5,64,6.7); planner.tick();
        assertEquals(TurnDirection.RIGHT,planner.side(),"arrival must not reverse a second time");
        planner.pause("unknown chunk"); assertFalse(input.sprint || input.forward || input.right);
        planner.stop(); assertFalse(input.sprint);
    }
    @Test void interruptedReturnKeepsReturnSideAndPendingTransferButNewCycleRestarts() {
        planner.start(TurnDirection.LEFT);reachEnd();
        world.obstructed=false;player.position=new Vec3(.5,64,3.7);planner.tick();
        assertEquals(TurnDirection.RIGHT,planner.side());
        planner.pause("failsafe");planner.resumeAtInterruptedPose();planner.tick();
        assertEquals(TurnDirection.RIGHT,planner.side());assertTrue(input.right);
        world.spacing=4;reachEnd();planner.pause("failsafe during corner");planner.resumeAtInterruptedPose();
        assertEquals(MelonKingDeWaterPlanner.State.TRANSFERRING,planner.state());
        world.obstructed=false;player.position=new Vec3(.5,64,7.7);planner.tick();
        assertEquals(TurnDirection.LEFT,planner.side());
        planner.start(TurnDirection.RIGHT);assertEquals(MelonKingDeWaterPlanner.State.FARMING,planner.state());
        assertEquals(TurnDirection.RIGHT,planner.side());
    }
}
