package dev.veltraph.client.farming.movement;

import dev.veltraph.client.config.*;
import dev.veltraph.client.service.FarmServices;
import dev.veltraph.client.service.stub.*;
import dev.veltraph.client.testutil.*;
import dev.veltraph.client.world.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Actual engine behavior across different crop heights, lateral reversals and recovery. */
class SShapeFixedSessionViewTest {
    static final class Geometry implements SShapeGeometry {
        Direction heading=Direction.NORTH;boolean right=true,left=true,forward=true,harvest=true;
        Vec3 target=new Vec3(.5,64,-.5),transition,drop;int reads;
        public void setHeading(Direction h){heading=h;}public Direction heading(){return heading;}
        public boolean nearMiddle(){return true;}public boolean blockInFront(){return true;}
        public boolean canStrafe(TurnDirection side){return side==TurnDirection.RIGHT?right:side==TurnDirection.LEFT&&left;}
        public boolean canStepForward(){return forward;}public TurnDirection movableLateral(){return right?TurnDirection.RIGHT:left?TurnDirection.LEFT:TurnDirection.FORWARD;}
        public Vec3 targetVec(){reads++;return target;}public Vec3 rowTransitionTarget(TurnDirection side){return transition;}
        public boolean harvestTargetAvailable(){return harvest;}public Vec3 safeDropTarget(TurnDirection side){return drop;}
        public boolean dropLandingValid(Vec3 target){return drop!=null&&drop.equals(target);}
    }
    record Setup(MapConfigService config,Geometry geometry,FakePlayerService player,FakeRotationService rotation,
                 RecordingInputService input,RecordingBlockBreaking breaking,SShapeRowEngine engine) {}
    Setup setup(){var cfg=new MapConfigService();cfg.set(FarmingConfig.ROTATION_USE_MOUSE_GCD,false);
        cfg.set(FarmingConfig.ROTATION_JITTER,true);cfg.set(FarmingConfig.ROTATION_JITTER_MIN_INTERVAL_TICKS,1);
        cfg.set(FarmingConfig.ROTATION_JITTER_MAX_INTERVAL_TICKS,2);cfg.set(FarmingConfig.ROTATION_ACCELERATION,1d);
        cfg.set(FarmingConfig.ROTATION_MAX_YAW_DEG_PER_TICK,90d);cfg.set(FarmingConfig.ROTATION_MAX_PITCH_DEG_PER_TICK,90d);
        var geometry=new Geometry();var player=new FakePlayerService();var rotation=new FakeRotationService();
        var input=new RecordingInputService();var breaking=new RecordingBlockBreaking();
        var services=new FarmServices(new FakeClock(),player,new NoOpWorldService(),input,rotation,breaking,new NoOpInventoryService());
        return new Setup(cfg,geometry,player,rotation,input,breaking,new SShapeRowEngine(cfg,services,geometry,FarmingConfig.SSHAPE_HOLD_FORWARD_POLICY,()->true));}
    void settle(Setup s){s.engine.start();for(int i=0;i<20;i++)s.engine.tick();assertEquals(SShapeState.FARMING_SIDEWAYS,s.engine.state());assertTrue(s.engine.aimError()<=.75);}
    @Test void cropHeightAndEyeHeightChangesCannotRetargetHeldSession(){var s=setup();settle(s);float pitch=s.engine.targetPitch(),held=s.rotation.pitch;
        for(int i=0;i<120;i++){s.geometry.target=new Vec3(.5,i%2==0?67:61,-.5);s.player.position=new Vec3(.5,i%3==0?64.25:64,.5);s.engine.tick();
            assertEquals(pitch,s.engine.targetPitch());assertEquals(held,s.rotation.pitch);assertTrue(s.input.right);}
        assertEquals(1,s.geometry.reads,"Crop sightline is sampled only at complete start");}
    @Test void globalJitterPreferenceCannotMoveSettledSShapeView(){var s=setup();settle(s);float yaw=s.rotation.yaw,pitch=s.rotation.pitch;
        for(int i=0;i<100;i++){s.engine.tick();assertEquals(yaw,s.rotation.yaw);assertEquals(pitch,s.rotation.pitch);}
        assertTrue(s.config.get(FarmingConfig.ROTATION_JITTER),"Other aim controllers retain their global setting");}
    @Test void forwardAndLateralCorridorKeysPublishTogetherAtRowEnd(){var s=setup();settle(s);float yaw=s.rotation.yaw,pitch=s.rotation.pitch;
        s.geometry.right=false;s.geometry.transition=new Vec3(-.5,64,1.5);s.engine.tick();
        assertEquals(SShapeState.ROW_TRANSITION_FORWARD,s.engine.state());assertTrue(s.input.forward);assertTrue(s.input.right);assertEquals(yaw,s.rotation.yaw);assertEquals(pitch,s.rotation.pitch);
        s.player.position=s.geometry.transition;s.engine.tick();assertEquals(TurnDirection.LEFT,s.engine.lateralSide());assertTrue(s.input.left);}
    @Test void reverseWithoutForwardKeepsSameViewAndRespectsHarvestRefusal(){var s=setup();settle(s);float pitch=s.engine.targetPitch();
        s.geometry.forward=false;s.geometry.right=false;s.geometry.harvest=false;s.engine.tick();s.engine.tick();assertEquals(TurnDirection.LEFT,s.engine.lateralSide());assertTrue(s.input.left);assertFalse(s.breaking.breaking);assertEquals(pitch,s.engine.targetPitch());}
    @Test void observedReturnAndLargeExternalLookRestoreOriginalPitch(){var s=setup();settle(s);float pitch=s.engine.targetPitch();
        s.geometry.target=new Vec3(.5,70,-.5);s.rotation.pitch=-30;s.engine.resumeFromObservedPosition();assertEquals(pitch,s.engine.targetPitch());
        for(int i=0;i<20;i++)s.engine.tick();assertEquals(pitch,s.engine.targetPitch());assertTrue(s.engine.aimError()<=.75);assertEquals(1,s.geometry.reads);}
    @Test void freshStopStartSamplesNewInitialCropSightline(){var s=setup();settle(s);float old=s.engine.targetPitch();s.engine.stop();assertFalse(s.input.forward||s.input.left||s.input.right);
        s.geometry.target=new Vec3(.5,65,-.5);s.engine.start();assertNotEquals(old,s.engine.targetPitch());assertEquals(2,s.geometry.reads);}
    @Test void intentionalDropTemporarilyOwnsViewButDoesNotResetRowPitch(){var s=setup();class Driver implements FarmRouteDriver{State state=State.ACTIVE;public boolean startDrop(Vec3 target,int bound){assertEquals(3,bound);return true;}public State tick(){return state;}public void stop(){}public String status(){return state.name();}}
        var driver=new Driver();s.engine.setDropDriver(driver);settle(s);float pitch=s.engine.targetPitch();s.geometry.right=false;s.geometry.drop=new Vec3(-.5,61,.5);s.engine.tick();assertEquals(SShapeState.INTENTIONAL_DROP,s.engine.state());
        s.rotation.yaw=90;s.rotation.pitch=-20;s.player.onGround=false;s.engine.tick();assertEquals(90,s.rotation.yaw);assertEquals(-20,s.rotation.pitch);
        driver.state=FarmRouteDriver.State.ARRIVED;s.player.position=s.geometry.drop;s.player.onGround=true;s.engine.tick();assertEquals(1,s.engine.completedHeightDrops());assertEquals(pitch,s.engine.targetPitch());assertEquals(1,s.geometry.reads);}
    @Test void unadaptedRotatingPortIsCancelledWithoutLegacyTick(){class Port implements CropCompletionPort{boolean running=true;int cancels;public Result tick(boolean a,float y,float p){throw new AssertionError("Rotating path borrowed");}public void cancel(){cancels++;running=false;}public boolean active(){return running;}public dev.veltraph.client.farming.module.FarmMaintenanceState maintenanceState(){return null;}}
        var port=new Port();assertEquals(CropCompletionPort.Result.IDLE,port.tickFixedRowView(true,0,30));assertEquals(1,port.cancels);
        assertEquals(CropCompletionPort.Result.IDLE,port.tickFixedRowView(true,0,30));assertEquals(1,port.cancels);}
}
