package dev.veltraph.client.farming.movement;

import dev.veltraph.client.config.*;
import dev.veltraph.client.service.FarmServices;
import dev.veltraph.client.service.stub.*;
import dev.veltraph.client.testutil.*;
import dev.veltraph.client.world.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Real row engine and passive phase classification; no native permission is supplied by models. */
class GroundedCropWorkTest {
    static final class Geometry implements SShapeGeometry {
        Direction heading;boolean mature=true,end,normal=true,tool=true;int sightlines;
        public void setHeading(Direction value){heading=value;}public Direction heading(){return heading;}
        public boolean nearMiddle(){return true;}public boolean blockInFront(){return true;}
        public boolean canStrafe(TurnDirection side){return !end||side==TurnDirection.LEFT;}
        public boolean canStepForward(){return true;}public TurnDirection movableLateral(){return TurnDirection.RIGHT;}
        public Vec3 targetVec(){sightlines++;return new Vec3(.5,64,1.5);}
        public FixedRowView initialFixedRowView(){return new FixedRowView(0,3);}
        public boolean requiresNormalFixedRowView(){return normal;}
        public boolean fixedViewHarvestTargetAvailable(){return mature;}
        public Vec3 rowTransitionTarget(TurnDirection side){return new Vec3(-.5,64,1.5);}
        public boolean requiresObservedRowDestination(){return true;}
    }
    record Rig(SShapeRowEngine engine,Geometry geometry,RecordingInputService input,
               RecordingBlockBreaking breaking,FakeRotationService rotation,MapConfigService config) {}
    Rig setup(){
        var config=new MapConfigService();config.set(FarmingConfig.ROTATION_USE_MOUSE_GCD,false);
        config.set(FarmingConfig.ROTATION_ACCELERATION,1d);config.set(FarmingConfig.ROTATION_MAX_YAW_DEG_PER_TICK,90d);
        config.set(FarmingConfig.ROTATION_MAX_PITCH_DEG_PER_TICK,90d);config.set(FarmingConfig.NETHERWART_STARTING_DIRECTION,LaneSide.RIGHT);
        var geometry=new Geometry();var input=new RecordingInputService();var breaking=new RecordingBlockBreaking();
        var rotation=new FakeRotationService();var services=new FarmServices(new FakeClock(),new FakePlayerService(),
                new NoOpWorldService(),input,rotation,breaking,new NoOpInventoryService());
        return new Rig(new SShapeRowEngine(config,services,geometry,()->geometry.tool),geometry,input,breaking,rotation,config);
    }
    void settle(Rig rig){rig.engine.start();for(int i=0;i<20;i++)rig.engine.tick();assertEquals(SShapeState.FARMING_SIDEWAYS,rig.engine.state());}

    @Test void admittedStartupSweepAndTurnAreCoveredWhileStoppedStateIsExcluded(){
        var rig=setup();assertFalse(rig.engine.groundedCropWorkActive());rig.engine.start();
        assertEquals(SShapeState.PREPARE_TOOL,rig.engine.state());assertTrue(rig.engine.groundedCropWorkActive());
        rig.engine.tick();assertEquals(SShapeState.AIMING,rig.engine.state());assertTrue(rig.engine.groundedCropWorkActive());
        for(int i=0;i<20;i++)rig.engine.tick();assertTrue(rig.engine.groundedCropWorkActive());
        rig.geometry.end=true;rig.engine.tick();assertEquals(SShapeState.ROW_TRANSITION_FORWARD,rig.engine.state());
        assertTrue(rig.engine.groundedCropWorkActive());rig.engine.stop();assertFalse(rig.engine.groundedCropWorkActive());
    }

    @Test void immatureHitKeepsContinuousAttackAndTheSameGroundedLateralMovementAndView(){
        var rig=setup();settle(rig);float yaw=rig.rotation.yaw,pitch=rig.rotation.pitch;
        for(boolean mature:new boolean[]{true,false,true,false}){
            rig.geometry.mature=mature;rig.input.jump=true;rig.engine.tick();
            assertTrue(rig.input.right);assertFalse(rig.input.jump||rig.input.sneak);
            assertTrue(rig.breaking.breaking);assertTrue(rig.engine.groundedCropWorkActive());
            assertEquals(yaw,rig.rotation.yaw);assertEquals(pitch,rig.rotation.pitch);
        }
        assertEquals(0,rig.geometry.sightlines,"Admitted normal profile never retargets toward growing crops");
    }

    @Test void otherFixedProfilesStillRespectHarvestAvailability(){
        var rig=setup();rig.geometry.normal=false;settle(rig);
        rig.geometry.mature=false;rig.engine.tick();assertFalse(rig.breaking.breaking);
        rig.geometry.mature=true;rig.engine.tick();assertTrue(rig.breaking.breaking);
    }

    @Test void continuousNormalAttackStillAppliesConfiguredCadence(){
        var rig=setup();settle(rig);rig.config.set(FarmingConfig.ATTACK_CADENCE_TICKS,3);
        rig.geometry.mature=false;int held=0,released=0;
        for(int i=0;i<12;i++){rig.engine.tick();if(rig.breaking.breaking)held++;else released++;
            assertTrue(rig.input.right);assertFalse(rig.input.jump);}
        assertEquals(8,held);assertEquals(4,released);
    }

    @Test void immatureCropDoesNotBypassToolRefusal(){
        var rig=setup();settle(rig);rig.geometry.mature=false;rig.geometry.tool=false;rig.engine.tick();
        assertFalse(rig.breaking.breaking);assertFalse(rig.input.right||rig.input.left||rig.input.forward||rig.input.jump);
    }

    @Test void ordinaryTransitionReassertsJumpReleasedWithoutChangingItsDestination(){
        var rig=setup();settle(rig);rig.geometry.end=true;rig.engine.tick();var target=rig.engine.transitionTarget();
        rig.input.jump=true;rig.engine.tick();assertFalse(rig.input.jump);
        assertEquals(target,rig.engine.transitionTarget());assertTrue(rig.input.forward&&rig.input.right);
    }

    @Test void terrainSupplementAndSharedDropAreOutsideTheCropAutoJumpVeto() throws Exception {
        var rig=setup();settle(rig);var field=SShapeRowEngine.class.getDeclaredField("state");field.setAccessible(true);
        for(var state:new SShapeState[]{SShapeState.SUPPLEMENTAL_COVERAGE,SShapeState.INTENTIONAL_DROP,SShapeState.RECOVERING,SShapeState.STOPPED}){
            field.set(rig.engine,state);assertFalse(rig.engine.groundedCropWorkActive(),state.name());
        }
    }
}
