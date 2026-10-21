package dev.veltraph.client.farming.movement.planner;
import dev.veltraph.client.farming.movement.*;
import dev.veltraph.client.service.*;
import dev.veltraph.client.service.stub.NoOpInventoryService;
import dev.veltraph.client.testutil.*;
import dev.veltraph.client.world.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
final class CapturedGourdLeftBlendTest {
 static class World implements CapturedGourdWallPlanner.World {
  boolean safe=true,blocked;Vec3 entry=new Vec3(1.5,64,.5);
  public boolean requireLeftStart(){return true;}
  public Optional<CapturedGourdWallPlanner.Band> detect(Vec3 pos,Direction forward){return Optional.of(new CapturedGourdWallPlanner.Band(pos.x()>1?Direction.EAST:Direction.WEST,pos.x()>1?Direction.WEST:Direction.EAST,1,"same-first-band"));}
  public Optional<Vec3> leftStartEntry(CapturedGourdWallPlanner.Band b,Direction f,Vec3 pos){return safe?Optional.of(entry):Optional.empty();}
  public boolean leftStartAlreadySatisfied(CapturedGourdWallPlanner.Band b,Direction f,Vec3 p){return b.wall()==Direction.EAST&&Math.abs(p.x()-entry.x())<=.20;}
  public boolean supported(){return true;}public boolean forwardBlocked(Direction f){return blocked;}public boolean cropTargeted(){return true;}
  public Optional<CapturedGourdWallPlanner.Next> next(CapturedGourdWallPlanner.Band b,Direction f,Set<String> visited){return Optional.of(new CapturedGourdWallPlanner.Next(b,new Vec3(5.5,64,.5),Direction.EAST));}
  public boolean turningMovementClear(Vec3 from,Vec3 to){return safe;}public boolean movementClear(Vec3 from,Vec3 to,Direction d){return safe;}
 }
 record Rig(FakePlayerService player,RecordingInputService input,FakeRotationService rotation,World world,CapturedGourdWallPlanner planner,boolean[] aligned){}
 Rig rig(){var p=new FakePlayerService();var input=new RecordingInputService();var rotation=new FakeRotationService();var world=new World();boolean[] aligned={true};var planner=new CapturedGourdWallPlanner(new FarmServices(new FakeClock(),p,new FakeWorldService(),input,rotation,new RecordingBlockBreaking(),new NoOpInventoryService()),world,()->aligned[0]);return new Rig(p,input,rotation,world,planner,aligned);}
 @Test void rightSideDryStartupWalksToPlayerLeftWithinSameBand(){var r=rig();r.planner.start(Direction.SOUTH);assertEquals(CapturedGourdWallPlanner.State.START_ENTRY,r.planner.state());r.planner.tick();assertTrue(r.input.left);assertFalse(r.input.right||r.input.sneak);r.player.position=r.world.entry;for(int i=0;i<3;i++)r.planner.tick();assertEquals(CapturedGourdWallPlanner.State.ALIGNING,r.planner.state());assertEquals(Direction.EAST,r.planner.band().wall());assertEquals(0,r.planner.passes());}
 @Test void blockedLeftStartupFailsClosedRatherThanCrossIntactFruit(){var r=rig();r.world.safe=false;r.planner.start(Direction.SOUTH);assertEquals(CapturedGourdWallPlanner.State.BLOCKED,r.planner.state());r.planner.tick();assertFalse(r.input.forward||r.input.left||r.input.right||r.input.sneak);}
 @Test void endTurnCanWalkWhileUnalignedOnlyIfActualSweepClear(){var r=rig();r.player.position=r.world.entry;r.planner.start(Direction.SOUTH);r.world.blocked=true;for(int i=0;i<4;i++)r.planner.tick();assertEquals(CapturedGourdWallPlanner.State.END_TRANSFER,r.planner.state());r.aligned[0]=false;r.rotation.yaw=45;r.planner.tick();assertTrue(r.input.forward||r.input.back||r.input.left||r.input.right);assertFalse(r.input.sneak);r.world.safe=false;r.planner.tick();assertFalse(r.input.forward||r.input.back||r.input.left||r.input.right||r.input.sneak);}
 @Test void startupAlignmentGapCannotReuseOldSettledProof(){var r=rig();r.planner.start(Direction.SOUTH);r.player.position=r.world.entry;r.planner.tick();r.planner.tick();r.aligned[0]=false;r.planner.tick();r.aligned[0]=true;r.planner.tick();assertEquals(CapturedGourdWallPlanner.State.START_ENTRY,r.planner.state());r.planner.tick();assertEquals(CapturedGourdWallPlanner.State.START_ENTRY,r.planner.state());r.planner.tick();assertEquals(CapturedGourdWallPlanner.State.ALIGNING,r.planner.state());}

}
