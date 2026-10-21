package dev.veltraph.client.farming.movement;
import dev.veltraph.client.config.*;
import dev.veltraph.client.service.*;
import dev.veltraph.client.service.stub.*;
import dev.veltraph.client.testutil.*;
import dev.veltraph.client.world.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
final class SShapeInitialCoverageTest {
 static class Geometry implements SShapeGeometry {
  boolean proved=true,canStrafeLeft=true,canStrafeRight=true;Vec3 target;Direction heading;
  public void setHeading(Direction value){heading=value;}public Direction heading(){return heading;}public boolean nearMiddle(){return true;}public boolean blockInFront(){return true;}public boolean canStrafe(TurnDirection side){return side==TurnDirection.LEFT?canStrafeLeft:canStrafeRight;}public boolean canStepForward(){return true;}public TurnDirection movableLateral(){return canStrafeRight?TurnDirection.RIGHT:TurnDirection.LEFT;}public Vec3 targetVec(){return target;}public boolean initialCoverageEndProved(TurnDirection side){return proved;}public boolean initialCoverageEndProved(TurnDirection side,boolean requiresForwardExit){return proved;}
 }
 record Rig(SShapeRowEngine engine,Geometry geometry,FakePlayerService player,RecordingInputService input,boolean[] permit){}
 Rig rig(){var cfg=new MapConfigService();cfg.set(FarmingConfig.NETHERWART_STARTING_DIRECTION,LaneSide.LEFT);cfg.set(FarmingConfig.ROTATION_USE_MOUSE_GCD,false);cfg.set(FarmingConfig.ROTATION_ACCELERATION,1.0);cfg.set(FarmingConfig.ROTATION_MAX_YAW_DEG_PER_TICK,90.0);cfg.set(FarmingConfig.ROTATION_MAX_PITCH_DEG_PER_TICK,90.0);var geometry=new Geometry();geometry.target=new Vec3(1.5,64,.5);var player=new FakePlayerService();player.yaw=-90;var rotation=new FakeRotationService(-90,0);var input=new RecordingInputService();var engine=new SShapeRowEngine(cfg,new FarmServices(new FakeClock(),player,new NoOpWorldService(),input,rotation,new RecordingBlockBreaking(),new NoOpInventoryService()),geometry,FarmingConfig.SSHAPE_HOLD_FORWARD_POLICY,()->true);boolean[] permit={true};engine.setInitialLaneCoverage(true);engine.setInitialCoveragePermission(()->permit[0]);engine.start();for(int i=0;i<20&&engine.state()!=SShapeState.FARMING_SIDEWAYS;i++)engine.tick();assertEquals(SShapeState.FARMING_SIDEWAYS,engine.state());return new Rig(engine,geometry,player,input,permit);}
 @Test void actualInitialReversalsNeverIncrementLaneCounter(){var r=rig();r.geometry.canStrafeLeft=false;r.geometry.canStrafeRight=true;r.player.position=new Vec3(.5,64,-20.5);r.engine.tick();assertEquals(TurnDirection.RIGHT,r.engine.lateralSide());assertEquals(0,r.engine.completedRowTransitions());r.geometry.canStrafeLeft=true;r.geometry.canStrafeRight=false;r.player.position=new Vec3(.5,64,30.5);r.engine.tick();assertEquals(TurnDirection.LEFT,r.engine.lateralSide());assertEquals(0,r.engine.completedRowTransitions());assertTrue(r.engine.initialCoveragePending());}
 @Test void unknownEndReleasesAndRetainsInitialLane(){var r=rig();r.geometry.canStrafeLeft=false;r.geometry.proved=false;r.engine.tick();assertFalse(r.input.left||r.input.right||r.input.forward);assertEquals(0,r.engine.completedRowTransitions());assertEquals(TurnDirection.LEFT,r.engine.lateralSide());}
 @Test void deniedActivationQuiescesAndReleasesOwnedPreviouslyPressedKeys(){var r=rig();r.engine.tick();assertTrue(r.input.left||r.input.right);r.permit[0]=false;r.engine.tick();assertFalse(r.input.left||r.input.right||r.input.forward||r.input.back);assertFalse(r.engine.initialCoveragePending());assertEquals(SShapeState.STOPPED,r.engine.state());assertEquals(0,r.engine.completedRowTransitions());r.permit[0]=true;r.engine.tick();assertEquals(SShapeState.STOPPED,r.engine.state());assertFalse(r.input.left||r.input.right);}
 @Test void foreignOwnershipRevocationQuiescesWithoutReleasingForeignKeys(){var r=rig();r.engine.setInitialCoverageRevocation(()->{});r.input.right=true;r.permit[0]=false;r.engine.tick();assertEquals(SShapeState.STOPPED,r.engine.state());assertFalse(r.engine.initialCoveragePending());assertTrue(r.input.right);r.permit[0]=true;r.engine.tick();assertTrue(r.input.right);}

}
