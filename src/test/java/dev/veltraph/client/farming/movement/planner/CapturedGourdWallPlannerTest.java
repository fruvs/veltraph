package dev.veltraph.client.farming.movement.planner;

import dev.veltraph.client.farming.movement.TurnDirection;
import dev.veltraph.client.service.*;
import dev.veltraph.client.service.stub.NoOpInventoryService;
import dev.veltraph.client.testutil.*;
import dev.veltraph.client.world.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CapturedGourdWallPlannerTest {
    @Test void diagonalVectorAlwaysAdvancesAndPressesPhysicalWallUnderEveryFacing(){
        for(Direction forward:List.of(Direction.NORTH,Direction.EAST,Direction.SOUTH,Direction.WEST))for(Direction wall:List.of(forward.rotateYClockwise(),forward.rotateYCounterclockwise())){
            var player=new FakePlayerService();var input=new RecordingInputService();var breaking=new RecordingBlockBreaking();
            var band=new CapturedGourdWallPlanner.Band(wall,wall.opposite(),1,"band");
            var world=new CapturedGourdWallPlanner.World(){
                public Optional<CapturedGourdWallPlanner.Band> detect(Vec3 p,Direction d){return Optional.of(band);}
                public boolean movementClear(Vec3 from,Vec3 goal,Direction actual){return true;}public boolean supported(){return true;}public boolean forwardBlocked(Direction d){return false;}
                public Optional<CapturedGourdWallPlanner.Next> next(CapturedGourdWallPlanner.Band b,Direction d,Set<String> visited){return Optional.empty();}
                public boolean cropTargeted(){return true;}
            };
            var planner=new CapturedGourdWallPlanner(new FarmServices(new FakeClock(),player,new FakeWorldService(),input,new FakeRotationService(),breaking,new NoOpInventoryService()),world,()->true);
            planner.start(forward);planner.tick();
            assertTrue(input.forward&&(input.left^input.right));
            double yaw=Math.toRadians(planner.yaw()+(input.left?-45:45));
            double dx=-Math.sin(yaw),dz=Math.cos(yaw);
            assertTrue(dx*forward.dx()+dz*forward.dz()>.95);
            assertTrue(dx*wall.dx()+dz*wall.dz()>.15,"must have actual pressure, not pure cardinal projection");
            assertTrue(breaking.breaking);assertFalse(input.sprint||input.jump||input.sneak);
            planner.stop();assertFalse(input.forward||input.left||input.right||breaking.breaking);
        }
    }

    @Test void supportedCurrentEndCanTransferEvenWhenForwardHasNoWalkableFloor(){
        var player=new FakePlayerService();var input=new RecordingInputService();var breaking=new RecordingBlockBreaking();
        var band=new CapturedGourdWallPlanner.Band(Direction.WEST,Direction.EAST,1,"first");
        var nextBand=new CapturedGourdWallPlanner.Band(Direction.WEST,Direction.EAST,1,"next");
        var world=new CapturedGourdWallPlanner.World(){
            public Optional<CapturedGourdWallPlanner.Band> detect(Vec3 p,Direction d){return Optional.of(band);}
            public boolean movementClear(Vec3 from,Vec3 goal,Direction actual){return true;}public boolean supported(){return true;}public boolean forwardBlocked(Direction d){return d==Direction.SOUTH;}
            public Optional<CapturedGourdWallPlanner.Next> next(CapturedGourdWallPlanner.Band b,Direction d,Set<String> visited){
                assertEquals(Set.of("first"),visited);return Optional.of(new CapturedGourdWallPlanner.Next(nextBand,new Vec3(5,player.position().y(),player.position().z()),Direction.EAST));
            }
            public boolean cropTargeted(){return true;}
        };
        var planner=new CapturedGourdWallPlanner(new FarmServices(new FakeClock(),player,new FakeWorldService(),input,new FakeRotationService(),breaking,new NoOpInventoryService()),world,()->true);
        planner.start(Direction.SOUTH);for(int i=0;i<4;i++)planner.tick();
        assertEquals(CapturedGourdWallPlanner.State.END_TRANSFER,planner.state());assertEquals(1,planner.passes());
        input.sneak=true;planner.tick();assertTrue(input.forward);assertFalse(input.sneak);assertFalse(input.left||input.right||breaking.breaking);
        player.position=new Vec3(5,player.position().y(),player.position().z());for(int settle=0;settle<3;settle++)planner.tick();
        assertEquals(Direction.NORTH,planner.forward());assertEquals(nextBand,planner.band());assertFalse(input.forward);
        planner.tick();assertTrue(input.forward&&input.left);assertFalse(input.right||input.sneak);
    }
    @Test void unloadedForwardReleasesRatherThanInferringAnEndConnector(){
        var player=new FakePlayerService();var input=new RecordingInputService();var breaking=new RecordingBlockBreaking();
        var world=new CapturedGourdWallPlanner.World(){
            public Optional<CapturedGourdWallPlanner.Band> detect(Vec3 p,Direction d){return Optional.of(new CapturedGourdWallPlanner.Band(Direction.WEST,Direction.EAST,1,"first"));}
            public boolean movementClear(Vec3 from,Vec3 goal,Direction actual){return true;}public boolean supported(){return true;}public boolean forwardLoaded(Direction d){return false;}public boolean forwardBlocked(Direction d){return true;}
            public Optional<CapturedGourdWallPlanner.Next> next(CapturedGourdWallPlanner.Band b,Direction d,Set<String> v){fail("unknown forward geometry cannot authorize a transfer");return Optional.empty();}
            public boolean cropTargeted(){return true;}
        };
        var planner=new CapturedGourdWallPlanner(new FarmServices(new FakeClock(),player,new FakeWorldService(),input,new FakeRotationService(),breaking,new NoOpInventoryService()),world,()->true);
        planner.start(Direction.SOUTH);for(int i=0;i<10;i++)planner.tick();
        assertEquals(0,planner.passes());assertFalse(input.forward||input.left||input.right||breaking.breaking);
    }

    @Test void outerFocusReleasesMotionUsesFreshCropGateAndResumesOnlyAfterObservedRemoval(){
        var player=new FakePlayerService();var input=new RecordingInputService();var breaking=new RecordingBlockBreaking();
        boolean[] exists={true},loaded={true},targeted={false};
        var target=new CapturedGourdWallPlanner.Focus(new BlockPos(2,64,0),-60,20);
        var world=new CapturedGourdWallPlanner.World(){
            public Optional<CapturedGourdWallPlanner.Band> detect(Vec3 p,Direction d){return Optional.of(new CapturedGourdWallPlanner.Band(Direction.WEST,Direction.EAST,1,"first"));}
            public boolean movementClear(Vec3 from,Vec3 goal,Direction actual){return true;}public boolean supported(){return true;}public boolean forwardBlocked(Direction d){return false;}
            public Optional<CapturedGourdWallPlanner.Next> next(CapturedGourdWallPlanner.Band b,Direction d,Set<String> v){return Optional.empty();}
            public Optional<CapturedGourdWallPlanner.Focus> nextFocus(CapturedGourdWallPlanner.Band b,Direction d){return exists[0]?Optional.of(target):Optional.empty();}
            public boolean focusLoaded(CapturedGourdWallPlanner.Focus f){return loaded[0];}public boolean focusRemoved(CapturedGourdWallPlanner.Focus f){return !exists[0];}
            public boolean cropTargeted(){return targeted[0];}
        };
        var planner=new CapturedGourdWallPlanner(new FarmServices(new FakeClock(),player,new FakeWorldService(),input,new FakeRotationService(),breaking,new NoOpInventoryService()),world,()->true);
        planner.start(Direction.SOUTH);planner.tick();assertEquals(CapturedGourdWallPlanner.State.FOCUSING,planner.state());
        assertEquals(-60,planner.yaw());assertEquals(20,planner.focusPitch().orElseThrow());
        planner.tick();assertFalse(input.forward||input.left||input.right||breaking.breaking);
        targeted[0]=true;planner.tick();assertTrue(breaking.breaking);assertFalse(input.forward||input.left||input.right);
        loaded[0]=false;exists[0]=false;planner.tick();assertEquals(CapturedGourdWallPlanner.State.FOCUSING,planner.state());assertFalse(breaking.breaking);
        loaded[0]=true;planner.tick();assertEquals(CapturedGourdWallPlanner.State.ALIGNING,planner.state());assertTrue(planner.focusPitch().isEmpty());
        planner.tick();assertTrue(input.forward&&input.right);planner.stop();assertFalse(input.forward||input.left||input.right||breaking.breaking);
    }

    @Test void closeApproachDistanceDoesNotAuthorizeAnObstructedConnector(){
        var player=new FakePlayerService();var input=new RecordingInputService();var breaking=new RecordingBlockBreaking();
        boolean[] clear={false};var band=new CapturedGourdWallPlanner.Band(Direction.WEST,Direction.EAST,1,"first");
        var world=new CapturedGourdWallPlanner.World(){
            public Optional<CapturedGourdWallPlanner.Band> detect(Vec3 p,Direction d){return Optional.of(band);}
            public boolean movementClear(Vec3 from,Vec3 goal,Direction actual){return true;}public boolean supported(){return true;}public boolean forwardBlocked(Direction d){return true;}
            public boolean connectorClear(CapturedGourdWallPlanner.Next n){return clear[0];}
            public Optional<CapturedGourdWallPlanner.Next> next(CapturedGourdWallPlanner.Band b,Direction d,Set<String> v){
                return Optional.of(new CapturedGourdWallPlanner.Next(band,new Vec3(5,64,-.5),Direction.EAST,new Vec3(.5,64,-.5),Direction.NORTH));
            }
            public boolean cropTargeted(){return true;}
        };
        var planner=new CapturedGourdWallPlanner(new FarmServices(new FakeClock(),player,new FakeWorldService(),input,new FakeRotationService(),breaking,new NoOpInventoryService()),world,()->true);
        planner.start(Direction.SOUTH);for(int i=0;i<4;i++)planner.tick();assertEquals(CapturedGourdWallPlanner.State.END_APPROACH,planner.state());
        assertEquals(180,planner.yaw());player.position=new Vec3(.5,64,-.44);planner.tick();
        assertEquals(CapturedGourdWallPlanner.State.END_APPROACH,planner.state());assertFalse(breaking.breaking);
        clear[0]=true;for(int settle=0;settle<3;settle++)planner.tick();assertEquals(CapturedGourdWallPlanner.State.END_TRANSFER,planner.state());assertFalse(input.forward);
        clear[0]=false;planner.tick();assertFalse(input.left||input.right||breaking.breaking);
    }
    @Test void loadedApproachWalksWithoutSneakAndNewObstructionStopsWithoutEdgeCrouch(){
        var player=new FakePlayerService();var input=new RecordingInputService();var breaking=new RecordingBlockBreaking();boolean[] safe={true};
        var band=new CapturedGourdWallPlanner.Band(Direction.WEST,Direction.EAST,1,"first");
        var world=new CapturedGourdWallPlanner.World(){
            public Optional<CapturedGourdWallPlanner.Band> detect(Vec3 p,Direction d){return Optional.of(band);}public boolean movementClear(Vec3 from,Vec3 goal,Direction actual){return safe[0];}public boolean supported(){return true;}public boolean forwardBlocked(Direction d){return true;}
            public Optional<CapturedGourdWallPlanner.Next> next(CapturedGourdWallPlanner.Band b,Direction f,Set<String> v){return Optional.of(new CapturedGourdWallPlanner.Next(band,new Vec3(5,64,-1.5),Direction.EAST,new Vec3(.5,64,-1.5),Direction.NORTH));}
            public boolean cropTargeted(){return true;}public boolean approachClear(CapturedGourdWallPlanner.Next n){return safe[0];}
        };
        var planner=new CapturedGourdWallPlanner(new FarmServices(new FakeClock(),player,new FakeWorldService(),input,new FakeRotationService(),breaking,new NoOpInventoryService()),world,()->true);
        planner.start(Direction.SOUTH);for(int j=0;j<4;j++)planner.tick();assertEquals(CapturedGourdWallPlanner.State.END_APPROACH,planner.state());
        input.sneak=true;planner.tick();assertTrue(input.forward);assertFalse(input.sneak||input.left||input.right||input.jump||input.sprint||breaking.breaking);
        safe[0]=false;input.sneak=true;planner.tick();assertFalse(input.forward||input.sneak||input.left||input.right||breaking.breaking);assertEquals(CapturedGourdWallPlanner.State.END_APPROACH,planner.state());
    }
    @Test void misalignedUnsupportedAndBlockedDefaultTicksReleaseInheritedSneak(){
        var player=new FakePlayerService();var input=new RecordingInputService();boolean[] supported={true},aligned={false};
        var world=new CapturedGourdWallPlanner.World(){public Optional<CapturedGourdWallPlanner.Band> detect(Vec3 p,Direction d){return Optional.of(new CapturedGourdWallPlanner.Band(Direction.WEST,Direction.EAST,1,"first"));}public boolean supported(){return supported[0];}public boolean forwardBlocked(Direction d){return false;}public Optional<CapturedGourdWallPlanner.Next> next(CapturedGourdWallPlanner.Band b,Direction f,Set<String> v){return Optional.empty();}public boolean cropTargeted(){return true;}};
        var planner=new CapturedGourdWallPlanner(new FarmServices(new FakeClock(),player,new FakeWorldService(),input,new FakeRotationService(),new RecordingBlockBreaking(),new NoOpInventoryService()),world,()->aligned[0]);
        planner.start(Direction.SOUTH);input.sneak=true;planner.tick();assertFalse(input.sneak);aligned[0]=true;supported[0]=false;input.sneak=true;planner.tick();assertFalse(input.sneak);planner.stop();input.sneak=true;planner.tick();assertFalse(input.sneak);
    }

    @Test void passedTransferTargetUsesCheckedBackCorrectionWithoutCrouching(){
        var player=new FakePlayerService();var input=new RecordingInputService();var band=new CapturedGourdWallPlanner.Band(Direction.WEST,Direction.EAST,1,"first");
        var world=new CapturedGourdWallPlanner.World(){public Optional<CapturedGourdWallPlanner.Band> detect(Vec3 p,Direction f){return Optional.of(band);}public boolean movementClear(Vec3 from,Vec3 goal,Direction actual){return true;}public boolean supported(){return true;}public boolean forwardBlocked(Direction f){return true;}public boolean cropTargeted(){return false;}public Optional<CapturedGourdWallPlanner.Next> next(CapturedGourdWallPlanner.Band b,Direction f,Set<String> v){return Optional.of(new CapturedGourdWallPlanner.Next(band,new Vec3(5,64,.5),Direction.EAST));}};
        var planner=new CapturedGourdWallPlanner(new FarmServices(new FakeClock(),player,new FakeWorldService(),input,new FakeRotationService(),new RecordingBlockBreaking(),new NoOpInventoryService()),world,()->true);
        planner.start(Direction.SOUTH);for(int j=0;j<4;j++)planner.tick();player.position=new Vec3(5.11,64,.5);input.sneak=true;planner.tick();assertEquals(CapturedGourdWallPlanner.State.END_TRANSFER,planner.state());assertFalse(input.forward||input.left||input.right||input.sneak);assertTrue(input.back);assertTrue(planner.blocker().contains("BACK"));
    }

    @Test void blockedActualMotionConsumesOriginalLegBudgetAndCheckpointIncludesArrival(){
        var player=new FakePlayerService();var input=new RecordingInputService();var band=new CapturedGourdWallPlanner.Band(Direction.WEST,Direction.EAST,1,"first");
        var world=new CapturedGourdWallPlanner.World(){public Optional<CapturedGourdWallPlanner.Band> detect(Vec3 p,Direction f){return Optional.of(band);}public boolean supported(){return true;}public boolean forwardBlocked(Direction f){return true;}public boolean cropTargeted(){return false;}public boolean movementClear(Vec3 from,Vec3 goal,Direction direction){return false;}public Optional<CapturedGourdWallPlanner.Next> next(CapturedGourdWallPlanner.Band b,Direction f,Set<String> v){return Optional.of(new CapturedGourdWallPlanner.Next(band,new Vec3(5,64,.5),Direction.EAST));}};
        var planner=new CapturedGourdWallPlanner(new FarmServices(new FakeClock(),player,new FakeWorldService(),input,new FakeRotationService(),new RecordingBlockBreaking(),new NoOpInventoryService()),world,()->true);
        planner.start(Direction.SOUTH);for(int i=0;i<4;i++)planner.tick();assertTrue(planner.maintenanceState().fields().containsKey("arrival"));for(int i=0;i<241;i++)planner.tick();assertEquals(CapturedGourdWallPlanner.State.BLOCKED,planner.state());assertFalse(input.forward||input.back||input.sneak);
    }

}
