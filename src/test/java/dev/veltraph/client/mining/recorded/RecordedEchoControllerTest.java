package dev.veltraph.client.mining.recorded;

import dev.veltraph.client.mining.MiningServices;
import dev.veltraph.client.service.*;
import dev.veltraph.client.service.stub.NoOpInventoryService;
import dev.veltraph.client.testutil.*;
import dev.veltraph.client.world.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class RecordedEchoControllerTest {
    static String profile(String keys,String binding){return "{\"yaw\":0,\"pitch\":0,\"checkpoints\":[{\"position\":[0,64,0],\"keys\":["+keys+"]"+binding+"},{\"position\":[0,64,1],\"keys\":[]}]}";}
    static final class Port implements RecordedEchoPort,MiningServices {
        final FakeClock clock=new FakeClock();final FakePlayerService player=new FakePlayerService();final RecordingInputService input=new RecordingInputService();
        final FakeRotationService rotation=new FakeRotationService();
        boolean available=true,loaded=true,support=true,clear=true,capacity=true,aim=true,tool=true,complete;
        String world="one",targetId="minecraft:cobblestone",problem="",placeProblem="";int calls,release;
        final BlockBreakingService breaking=new BlockBreakingService(){boolean held;public void setBreaking(boolean value){held=value;}public boolean isBreaking(){return held;}};
        final WorldService blocks=new FakeWorldService(){
            public boolean isLoaded(BlockPos pos){return loaded;}
            public boolean bodyClear(Vec3 feet,double half,double height){return clear;}
            public boolean hasGroundSupport(Vec3 feet,double half){return support;}
            public String blockId(BlockPos p){return p.equals(new BlockPos(0,64,2))?targetId:"minecraft:air";}
            public boolean isPassable(BlockPos p){return blockId(p).equals("minecraft:air");}
        };
        final FarmServices farm=new FarmServices(clock,player,blocks,input,rotation,breaking,new NoOpInventoryService());
        public MiningServices mining(){return this;}public FarmServices farm(){return farm;}public boolean available(){return available;}public String worldIdentity(){return world;}
        public String placementProblem(RecordedEchoProfile.Placement p){return placeProblem;}
        public boolean placementComplete(RecordedEchoProfile.Placement p){return complete;}
        public boolean place(RecordedEchoProfile.Placement p){calls++;return true;}public void releasePlacement(){release++;}
        public String selectTool(BlockPos p,int reserve){return tool?"":"tool unavailable";}public boolean heldToolReady(BlockPos p,int reserve){return tool;}
        public boolean hasCapacity(BlockPos p){return capacity;}public String targetProblem(BlockPos p,boolean floor){return problem;}
        public boolean aimedAt(BlockPos p){return aim;}public boolean safeStep(boolean f){return true;}public boolean endWall(boolean f){return false;}
        public boolean walkTo(BlockPos p){throw new AssertionError("Recorded keys must not be replaced by generic walking");}public void stopWalking(){}
        public String navigationStatus(){return "";}
    }
    static void tick(RecordedEchoController c){c.tick(false,8,12000,true);}
    @Test void recordsAllNineKeysWithMandatoryExplicitClickBindings(){
        var p=RecordedEchoProfile.parse(profile("\"FORWARD\",\"SPRINT\",\"SNEAK\",\"JUMP\"",""));assertEquals(4,p.checkpoints().getFirst().keys().size());
        assertThrows(RuntimeException.class,()->RecordedEchoProfile.parse(profile("\"RIGHT_CLICK\"","")));
        assertThrows(RuntimeException.class,()->RecordedEchoProfile.parse(profile("\"LEFT_CLICK\"","")));
        assertThrows(RuntimeException.class,()->RecordedEchoProfile.parse(profile("\"FORWARD\",\"BACK\"","")));
        assertThrows(RuntimeException.class,()->RecordedEchoProfile.parse(profile("\"FORWARD\",\"FORWARD\"","")));
        assertThrows(RuntimeException.class,()->RecordedEchoProfile.parse(profile("\"BLINK\"","")));
        assertThrows(RuntimeException.class,()->RecordedEchoProfile.parse(profile("\"LEFT\"","").replace("[0,64,0]","[0.5,64,0]")));
    }
    @Test void heldRecordedKeysReplacePreviousListOnActualCheckpointArrival(){
        var p=new Port();var c=new RecordedEchoController(p);c.start(RecordedEchoProfile.parse(profile("\"FORWARD\",\"SNEAK\",\"SPRINT\"","")),false);tick(c);
        assertTrue(p.input.forward);assertTrue(p.input.sneak);assertTrue(p.input.sprint);assertFalse(p.input.jump);
        p.player.blockPos=new BlockPos(0,64,1);p.player.position=new Vec3(.5,64,1.1);tick(c);tick(c);assertEquals(RecordedEchoController.State.COMPLETE,c.state());assertFalse(p.input.forward);assertFalse(p.input.sneak);assertEquals(1,c.checkpoint());
    }
    @Test void leftRightBackAreActualHeldKeys(){for(String key:List.of("LEFT","RIGHT","BACK")){var p=new Port();var c=new RecordedEchoController(p);c.start(RecordedEchoProfile.parse(profile("\""+key+"\"","")),false);tick(c);assertEquals(key.equals("LEFT"),p.input.left);assertEquals(key.equals("RIGHT"),p.input.right);assertEquals(key.equals("BACK"),p.input.back);}}
    @Test void jumpRequiresSupportedClearBallisticLandingAndReleasesOnLostSupport(){
        var p=new Port();var c=new RecordedEchoController(p);c.start(RecordedEchoProfile.parse(profile("\"FORWARD\",\"JUMP\"","")),false);tick(c);assertTrue(p.input.jump);
        p.player.onGround=false;p.player.position=new Vec3(.5,64.8,.8);tick(c);assertTrue(p.input.jump);
        p.support=false;tick(c);assertEquals(RecordedEchoController.State.PAUSED,c.state());assertFalse(p.input.jump);assertFalse(p.input.forward);
    }
    @Test void unsupportedInitialAirborneJumpCannotCreateLandingWitness(){var p=new Port();p.player.onGround=false;var c=new RecordedEchoController(p);c.start(RecordedEchoProfile.parse(profile("\"JUMP\"","")),false);tick(c);assertFalse(p.input.jump);assertEquals(RecordedEchoController.State.PAUSED,c.state());}
    @Test void unloadedAndBlockedMovementReleasesKeysWithoutAdvancing(){for(boolean unloaded:List.of(true,false)){var p=new Port();var c=new RecordedEchoController(p);c.start(RecordedEchoProfile.parse(profile("\"FORWARD\"","")),false);tick(c);if(unloaded)p.loaded=false;else p.clear=false;tick(c);assertEquals(0,c.checkpoint());assertFalse(p.input.forward);assertEquals(RecordedEchoController.State.PAUSED,c.state());}}
    @Test void sourceDesignOrientationMustActuallyBeReachedBeforeKeys(){var p=new Port();p.player.yaw=20;var c=new RecordedEchoController(p);c.start(RecordedEchoProfile.parse(profile("\"FORWARD\"","")),false);tick(c);assertEquals(RecordedEchoController.State.AIMING,c.state());assertFalse(p.input.forward);p.player.yaw=0;tick(c);assertTrue(p.input.forward);}
    @Test void boundBreakCountsLoadedRemovalAndInteractionGuardChecksFreshFacts(){
        var p=new Port();var c=new RecordedEchoController(p);c.start(RecordedEchoProfile.parse(profile("\"LEFT_CLICK\",\"FORWARD\"",",\"leftTarget\":[0,64,2]")),false);tick(c);
        assertTrue(p.breaking.isBreaking());assertFalse(p.input.forward);assertTrue(c.allowsTarget(new BlockPos(0,64,2),8));
        p.tool=false;assertFalse(c.allowsTarget(new BlockPos(0,64,2),8));p.tool=true;p.world="two";assertFalse(c.allowsTarget(new BlockPos(0,64,2),8));p.world="one";
        p.targetId="minecraft:air";tick(c);assertEquals(1,c.removed());assertFalse(p.breaking.isBreaking());assertTrue(p.input.forward);
    }
    @Test void rightClickWaitsForBoundPlacementAndMaterialOutcomeBeforeMoving(){var p=new Port();var c=new RecordedEchoController(p);c.start(RecordedEchoProfile.parse(profile("\"RIGHT_CLICK\",\"FORWARD\"",",\"placement\":{\"item\":\"minecraft:stone\",\"support\":[1,63,0],\"face\":\"UP\",\"result\":[1,64,0],\"block\":\"minecraft:stone\"}")),false);tick(c);assertEquals(1,p.calls);assertEquals(0,c.placed());assertFalse(p.input.forward);p.complete=true;tick(c);assertEquals(1,c.placed());assertTrue(p.input.forward);}
    @Test void placementPausePreservesCustodyUntilOutcomeOrExplicitStop(){var p=new Port();var c=new RecordedEchoController(p);c.start(RecordedEchoProfile.parse(profile("\"RIGHT_CLICK\"",",\"placement\":{\"item\":\"minecraft:stone\",\"support\":[1,63,0],\"face\":\"UP\",\"result\":[1,64,0],\"block\":\"minecraft:stone\"}")),false);tick(c);int held=p.release;p.placeProblem="material changed";tick(c);assertEquals(held,p.release);assertEquals(RecordedEchoController.State.PAUSED,c.state());c.stop();assertEquals(held+1,p.release);}
    @Test void worldChangeLatchesAndRememberOnlyRestoresSameProfileSameWorld(){var p=new Port();var c=new RecordedEchoController(p);var profile=RecordedEchoProfile.parse(profile("\"FORWARD\"",""));c.start(profile,false);tick(c);p.player.blockPos=new BlockPos(0,64,1);tick(c);tick(c);assertEquals(1,c.checkpoint());c.start(profile,true);assertEquals(1,c.checkpoint());p.world="two";tick(c);assertEquals(RecordedEchoController.State.PAUSED,c.state());p.world="one";tick(c);assertEquals(RecordedEchoController.State.PAUSED,c.state());c.start(profile,true);assertEquals(1,c.checkpoint());p.world="two";c.start(profile,true);assertEquals(0,c.checkpoint());}
    @Test void initialAdmissionCannotBeBypassedByPause(){var p=new Port();p.player.blockPos=new BlockPos(10,64,0);var c=new RecordedEchoController(p);c.start(RecordedEchoProfile.parse(profile("\"FORWARD\"","")),false);tick(c);tick(c);assertFalse(p.input.forward);assertEquals(RecordedEchoController.State.PAUSED,c.state());}
    @Test void rewarpResetRequiresInitialRecordedGeometryAgain(){var p=new Port();var c=new RecordedEchoController(p);c.start(RecordedEchoProfile.parse(profile("\"FORWARD\"","")),false);tick(c);p.player.blockPos=new BlockPos(0,64,1);tick(c);tick(c);assertEquals(RecordedEchoController.State.COMPLETE,c.state());c.resetAtRewarp();tick(c);assertEquals(RecordedEchoController.State.PAUSED,c.state());assertFalse(p.input.forward);assertEquals(0,c.checkpoint());}
    @Test void facadeDelegateKeepsExistingEchoIdentityAndDeniesOutstandingScheduling(){var p=new Port();var config=new dev.veltraph.client.config.MapConfigService();config.set(RecordedEchoConfig.PROFILE,profile("\"FORWARD\"",""));var delegate=new RecordedEchoDelegate(config,p);assertTrue(delegate.configured());assertTrue(delegate.preStartCheck().isEmpty());delegate.start(this);assertTrue(delegate.tick());assertTrue(p.input.forward);assertTrue(delegate.scheduledRestReadiness().isPresent());config.set(RecordedEchoConfig.PROFILE,"");assertFalse(delegate.tick());assertFalse(p.input.forward);}

    static RecordedEchoProfile duplicates(){return RecordedEchoProfile.parse("{\"yaw\":0,\"pitch\":0,\"checkpoints\":[{\"position\":[0,64,0],\"keys\":[\"FORWARD\"]},{\"position\":[0,64,2],\"keys\":[\"BACK\"]},{\"position\":[0,64,0],\"keys\":[\"LEFT\"]},{\"position\":[0,64,2],\"keys\":[\"RIGHT\"]},{\"position\":[2,64,2],\"keys\":[]}]}");}
    static void move(Port p,int x,int z){p.player.blockPos=new BlockPos(x,64,z);p.player.position=new Vec3(x+.5,64,z+.5);}
    @Test void firstEligibleFutureDuplicateWinsOnlyAfterRepeatedBlockObservation(){var p=new Port();var c=new RecordedEchoController(p);c.start(duplicates(),false);c.tick(true,8,12000,true);move(p,0,2);c.tick(true,8,12000,true);assertEquals(0,c.checkpoint());c.tick(true,8,12000,true);assertEquals(1,c.checkpoint());assertTrue(p.input.back);assertFalse(p.input.right);}
    @Test void skippingDisabledDoesNotJumpOverNextCheckpoint(){var p=new Port();var c=new RecordedEchoController(p);c.start(duplicates(),false);tick(c);move(p,2,2);tick(c);tick(c);assertEquals(0,c.checkpoint());assertNotEquals(RecordedEchoController.State.COMPLETE,c.state());}
    @Test void finalArrivalRequiresRepeatedObservationAndReleasesKeys(){var p=new Port();var c=new RecordedEchoController(p);c.start(duplicates(),false);c.tick(true,8,12000,true);move(p,2,2);c.tick(true,8,12000,true);assertNotEquals(RecordedEchoController.State.COMPLETE,c.state());c.tick(true,8,12000,true);assertEquals(4,c.checkpoint());assertEquals(RecordedEchoController.State.COMPLETE,c.state());assertFalse(p.input.forward);assertFalse(p.input.back);}
    @Test void stopAndRestartCannotReusePreviousBlockObservation(){var p=new Port();var c=new RecordedEchoController(p);c.start(duplicates(),false);c.tick(true,8,12000,true);move(p,0,2);c.tick(true,8,12000,true);c.stop();move(p,0,0);c.start(RecordedEchoProfile.parse(profile("\"FORWARD\"","")),false);c.tick(true,8,12000,true);assertEquals(0,c.checkpoint());move(p,0,1);c.tick(true,8,12000,true);assertEquals(0,c.checkpoint());}

    @Test void changedBlockOrUnavailableObservationCannotAuthorizeSkip(){for(boolean unavailable:List.of(false,true)){var p=new Port();var c=new RecordedEchoController(p);c.start(duplicates(),false);c.tick(true,8,12000,true);move(p,0,2);c.tick(true,8,12000,true);if(unavailable){p.available=false;c.tick(true,8,12000,true);p.available=true;}else{move(p,1,2);c.tick(true,8,12000,true);move(p,0,2);}c.tick(true,8,12000,true);assertEquals(0,c.checkpoint());c.tick(true,8,12000,true);assertEquals(1,c.checkpoint());}}
    @Test void stableFutureArrivalCannotDiscardPendingNativeBreakReceipt(){var p=new Port();var c=new RecordedEchoController(p);c.start(RecordedEchoProfile.parse(profile("\"LEFT_CLICK\",\"FORWARD\"",",\"leftTarget\":[0,64,2]")),false);c.tick(true,8,12000,true);move(p,0,1);c.tick(true,8,12000,true);c.tick(true,8,12000,true);assertEquals(0,c.checkpoint());assertEquals(0,c.removed());p.targetId="minecraft:air";c.tick(true,8,12000,true);assertEquals(1,c.removed());assertEquals(0,c.checkpoint());c.tick(true,8,12000,true);assertEquals(RecordedEchoController.State.COMPLETE,c.state());}
    @Test void stableButUnloadedFutureArrivalCannotRemovePrefix(){var p=new Port();var c=new RecordedEchoController(p);c.start(duplicates(),false);c.tick(true,8,12000,true);move(p,0,2);c.tick(true,8,12000,true);p.loaded=false;c.tick(true,8,12000,true);assertEquals(0,c.checkpoint());assertEquals(RecordedEchoController.State.PAUSED,c.state());assertFalse(p.input.forward);}
}
