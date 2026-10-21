package dev.veltraph.client.farming.movement;

import dev.veltraph.client.config.*;
import dev.veltraph.client.service.*;
import dev.veltraph.client.service.stub.*;
import dev.veltraph.client.testutil.*;
import dev.veltraph.client.world.*;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Actual fixed-view engine sequencing. Model inputs never confer native movement permission. */
class SShapeTransitionContinuityTest {
    static final class Player implements PlayerService {
        final FakePlayerService delegate = new FakePlayerService();
        Optional<Vec3> velocity = Optional.of(new Vec3(0, 0, 0));
        Runnable velocityRead;
        public boolean isAvailable(){return delegate.isAvailable();}
        public Vec3 position(){return delegate.position();}
        public BlockPos blockPos(){return delegate.blockPos();}
        public float yaw(){return delegate.yaw();}
        public float pitch(){return delegate.pitch();}
        public boolean onGround(){return delegate.onGround();}
        public double horizontalSpeed(){return delegate.horizontalSpeed();}
        public Direction facing(){return delegate.facing();}
        public Optional<Vec3> horizontalVelocity(){
            var original=velocity;
            if(velocityRead!=null){var callback=velocityRead;velocityRead=null;callback.run();}
            return original;
        }
    }
    static final class Geometry implements SShapeGeometry {
        Direction heading; boolean end;Runnable targetRead;int targetReads;
        final Vec3 target = new Vec3(-.5,64,1.5);
        public void setHeading(Direction value){heading=value;}
        public Direction heading(){return heading;}
        public boolean nearMiddle(){return true;}
        public boolean blockInFront(){return true;}
        public boolean canStrafe(TurnDirection side){return !end||side==TurnDirection.LEFT;}
        public boolean canStepForward(){return true;}
        public TurnDirection movableLateral(){return TurnDirection.LEFT;}
        public Vec3 targetVec(){return new Vec3(.5,64,1.5);}
        public FixedRowView initialFixedRowView(){return new FixedRowView(0,3);}
        public Vec3 rowTransitionTarget(TurnDirection side){targetReads++;if(targetRead!=null){var callback=targetRead;targetRead=null;callback.run();}return target;}
        public boolean requiresObservedRowDestination(){return true;}
    }
    static final class HookInput implements InputService {
        final RecordingInputService delegate;Runnable forward;int forwardWrites;
        HookInput(RecordingInputService delegate){this.delegate=delegate;}
        public void setForwardHeld(boolean held){delegate.setForwardHeld(held);forwardWrites++;if(forward!=null){var callback=forward;forward=null;callback.run();}}
        public void setBackHeld(boolean held){delegate.setBackHeld(held);}
        public void setLeftHeld(boolean held){delegate.setLeftHeld(held);}
        public void setRightHeld(boolean held){delegate.setRightHeld(held);}
        public void setSneakHeld(boolean held){delegate.setSneakHeld(held);}
        public void setJumpHeld(boolean held){delegate.setJumpHeld(held);}
        public void setSprintHeld(boolean held){delegate.setSprintHeld(held);}
        public void releaseAllMovement(){delegate.releaseAllMovement();}
    }
    record Rig(Player player,Geometry geometry,FakeClock clock,RecordingInputService input,
               FakeRotationService rotation,SShapeRowEngine engine,HookInput hooks) {}
    Rig active(){
        var config=new MapConfigService();config.set(FarmingConfig.ROTATION_USE_MOUSE_GCD,false);
        config.set(FarmingConfig.ROTATION_ACCELERATION,1d);
        config.set(FarmingConfig.ROTATION_MAX_YAW_DEG_PER_TICK,90d);
        config.set(FarmingConfig.ROTATION_MAX_PITCH_DEG_PER_TICK,90d);
        config.set(FarmingConfig.NETHERWART_STARTING_DIRECTION,LaneSide.RIGHT);
        var player=new Player();var geometry=new Geometry();var clock=new FakeClock();
        var input=new RecordingInputService();var hooks=new HookInput(input);var rotation=new FakeRotationService();
        var services=new FarmServices(clock,player,new NoOpWorldService(),hooks,rotation,
                new RecordingBlockBreaking(),new NoOpInventoryService());
        var engine=new SShapeRowEngine(config,services,geometry,()->true);
        engine.start();for(int i=0;i<20;i++){clock.advanceTick();engine.tick();}
        assertEquals(SShapeState.FARMING_SIDEWAYS,engine.state());
        return new Rig(player,geometry,clock,input,rotation,engine,hooks);
    }
    void tick(Rig rig){rig.clock.advanceTick();rig.engine.tick();}
    void begin(Rig rig){rig.geometry.end=true;tick(rig);assertEquals(SShapeState.ROW_TRANSITION_FORWARD,rig.engine.state());}

    @Test void nearbyOrdinaryTransitionKeepsForwardAndLateralOnEveryTick(){
        var rig=active();float yaw=rig.rotation.yaw,pitch=rig.rotation.pitch;begin(rig);
        rig.player.delegate.position=new Vec3(-.2,64,1.2);
        for(int tick=0;tick<6;tick++){
            tick(rig);
            assertTrue(rig.input.forward);assertTrue(rig.input.right);
            assertFalse(rig.input.sneak||rig.input.jump||rig.input.sprint);
            assertEquals(yaw,rig.rotation.yaw);assertEquals(pitch,rig.rotation.pitch);
            assertEquals(0,rig.engine.completedRowTransitions());
        }
    }

    @Test void transientRadiusEntryCannotCompleteAndActualArrivalStartsReturnImmediately(){
        var rig=active();begin(rig);
        rig.player.delegate.position=new Vec3(-.5,64,1.4);tick(rig);
        assertEquals(0,rig.engine.completedRowTransitions());
        rig.player.delegate.position=new Vec3(-.5,64,1.8);tick(rig);
        assertEquals(0,rig.engine.completedRowTransitions());
        rig.player.delegate.position=rig.geometry.target;tick(rig);
        assertEquals(0,rig.engine.completedRowTransitions());
        tick(rig);
        assertEquals(1,rig.engine.completedRowTransitions());
        assertEquals(SShapeState.FARMING_SIDEWAYS,rig.engine.state());
        assertEquals(TurnDirection.LEFT,rig.engine.lateralSide());assertTrue(rig.input.left);
        assertFalse(rig.input.forward||rig.input.back||rig.input.right);
    }

    @Test void restartDuringMomentumReadCannotPublishOldKeysOrBorrowOldArrival(){
        var rig=active();begin(rig);
        rig.player.delegate.position=rig.geometry.target;tick(rig);
        rig.player.velocityRead=()->{rig.engine.stop();rig.geometry.end=false;rig.engine.start();};
        tick(rig);
        assertEquals(SShapeState.PREPARE_TOOL,rig.engine.state());
        assertEquals(0,rig.engine.completedRowTransitions());assertNull(rig.engine.transitionTarget());
        assertFalse(rig.input.forward||rig.input.back||rig.input.left||rig.input.right);
        for(int i=0;i<20;i++)tick(rig);begin(rig);tick(rig);
        assertEquals(0,rig.engine.completedRowTransitions(),"Fresh corridor needs its own actual observations");
    }

    @Test void sourceQuiescenceDuringMomentumReadCannotPublishOldTail(){
        var rig=active();begin(rig);
        rig.player.velocityRead=()->{rig.engine.quiesceInitialCoverage();rig.input.releaseAllMovement();};
        tick(rig);
        assertEquals(SShapeState.STOPPED,rig.engine.state());assertEquals(0,rig.engine.completedRowTransitions());
        assertFalse(rig.input.forward||rig.input.back||rig.input.left||rig.input.right);
    }

    @Test void duplicateOrSkippedClientTicksCannotReuseOneFrameCredit(){
        var rig=active();begin(rig);rig.player.delegate.position=rig.geometry.target;tick(rig);
        rig.engine.tick();assertEquals(0,rig.engine.completedRowTransitions(),"Same client tick is not a second observation");
        rig.clock.advanceTick();rig.clock.advanceTick();rig.engine.tick();
        assertEquals(0,rig.engine.completedRowTransitions(),"A suspended/skipped interval loses consecutive evidence");
        tick(rig);assertEquals(1,rig.engine.completedRowTransitions());
    }

    @Test void suspensionRetiresArrivalCreditAndCheckpointReadsRemainPassive(){
        var rig=active();begin(rig);rig.player.delegate.position=rig.geometry.target;tick(rig);
        var before=rig.engine.maintenanceState();
        assertEquals(rig.geometry.target,before.fields().get("previousTransitionPose"));
        assertEquals(rig.clock.currentTick(),before.fields().get("previousTransitionTick"));
        rig.player.velocityRead=()->{throw new AssertionError("Checkpoint/suspension must not read native momentum");};
        rig.engine.retireRowTransitionObservation();var retired=rig.engine.maintenanceState();
        assertNull(retired.fields().get("previousTransitionPose"));
        assertEquals(Long.MIN_VALUE,retired.fields().get("previousTransitionTick"));
        assertNotEquals(before,retired);
        rig.player.velocityRead=null;tick(rig);assertEquals(0,rig.engine.completedRowTransitions());
        tick(rig);assertEquals(1,rig.engine.completedRowTransitions());
    }

    @Test void suspensionCallbackCannotRestoreOldArrivalCreditOrPublishMovement(){
        var rig=active();begin(rig);rig.player.delegate.position=rig.geometry.target;tick(rig);
        rig.player.velocityRead=()->{rig.engine.retireRowTransitionObservation();rig.input.releaseAllMovement();};
        tick(rig);
        assertNull(rig.engine.maintenanceState().fields().get("previousTransitionPose"));
        assertEquals(0,rig.engine.completedRowTransitions());
        assertFalse(rig.input.forward||rig.input.back||rig.input.left||rig.input.right);
        tick(rig);assertEquals(0,rig.engine.completedRowTransitions());
        tick(rig);assertEquals(1,rig.engine.completedRowTransitions());
    }

    @Test void recursiveMomentumProviderCannotCompleteAnInnerFrameAndOverwriteItsReturn(){
        var rig=active();begin(rig);rig.player.delegate.position=new Vec3(-.5,64,1.4);tick(rig);
        rig.player.velocityRead=rig.engine::tick;tick(rig);
        assertEquals(1,rig.engine.completedRowTransitions());
        assertEquals(SShapeState.FARMING_SIDEWAYS,rig.engine.state());
        assertNull(rig.engine.transitionTarget());
        var checkpoint=rig.engine.maintenanceState();
        assertNull(checkpoint.fields().get("previousTransitionPose"));
        assertEquals(Long.MIN_VALUE,checkpoint.fields().get("previousTransitionTick"));
        assertNull(checkpoint.fields().get("transitionTickFrame"));
        assertTrue(rig.input.left);assertFalse(rig.input.forward||rig.input.back||rig.input.right);
    }

    @Test void recursiveGeometryProviderCannotBorrowRowAdmissionOrBeginASecondCorridor(){
        var rig=active();rig.geometry.targetRead=rig.engine::tick;begin(rig);
        assertEquals(2,rig.geometry.targetReads,"One admission query and one actual begin query");
        assertEquals(rig.geometry.target,rig.engine.transitionTarget());
        assertEquals(0,rig.engine.completedRowTransitions());
        assertTrue(rig.input.forward&&rig.input.right);
        assertNull(rig.engine.maintenanceState().fields().get("fixedTickFrame"));
    }

    @Test void completionInputCallbackCannotBorrowTheNewOrdinarySweep(){
        var rig=active();begin(rig);rig.player.delegate.position=rig.geometry.target;tick(rig);
        int before=rig.hooks.forwardWrites;rig.hooks.forward=rig.engine::tick;tick(rig);
        assertEquals(before+1,rig.hooks.forwardWrites,"Only the original completion forward release may publish");
        assertEquals(1,rig.engine.completedRowTransitions());assertEquals(SShapeState.FARMING_SIDEWAYS,rig.engine.state());
        assertTrue(rig.input.left);assertFalse(rig.input.forward||rig.input.back||rig.input.right);
        assertNull(rig.engine.maintenanceState().fields().get("fixedTickFrame"));
    }
}
