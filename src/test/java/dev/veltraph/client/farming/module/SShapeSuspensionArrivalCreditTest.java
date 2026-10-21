package dev.veltraph.client.farming.module;

import dev.veltraph.client.farming.movement.CropCompletionPort;
import dev.veltraph.client.farming.movement.SShapeState;
import dev.veltraph.client.service.FarmServices;
import dev.veltraph.client.service.PlayerService;
import dev.veltraph.client.world.BlockPos;
import dev.veltraph.client.world.Direction;
import dev.veltraph.client.world.Vec3;
import java.lang.reflect.Field;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Actual farm hook and real row engine, with inert model fields and a cancellation-provider callback. */
class SShapeSuspensionArrivalCreditTest {
    @Test void cancellationReentryCreditIsRetiredEvenWhenTheSameProviderExceptionEscapes() throws Exception {
        for (boolean throwsAfterCallback : new boolean[]{false, true}) {
            var rig = new FarmMaintenanceWitnessTest.Rig();
            var farm = (WartCropsFarmModule) rig.farm("wart");
            var engine = farm.engine();
            var failure = new IllegalStateException("exact cancellation failure");
            boolean[] armed = {false};
            farm.setReachCompletionPort(new CropCompletionPort() {
                public Result tick(boolean admitted,float yaw,float pitch){return Result.IDLE;}
                public boolean active(){return false;}
                public FarmMaintenanceState maintenanceState(){return FarmMaintenanceState.of(this);}
                public void cancel(){
                    if(!armed[0])return;
                    engine.tick();
                    assertEquals(rig.player.position,engine.maintenanceState().fields().get("previousTransitionPose"),
                            "The actual cancellation callback supplied a fresh live observation");
                    if(throwsAfterCallback)throw failure;
                }
            });
            assertTrue(farm.preStartCheck().isEmpty());farm.setEnabled(true);
            set(engine,"fixedRowSteering",true);set(engine,"state",SShapeState.ROW_TRANSITION_FORWARD);
            set(engine,"transitionTarget",rig.player.position);set(engine,"previousTransitionPose",null);
            set(engine,"previousTransitionTick",Long.MIN_VALUE);armed[0]=true;
            if(throwsAfterCallback)assertSame(failure,assertThrows(IllegalStateException.class,farm::onBeforeFarmSuspension));
            else farm.onBeforeFarmSuspension();
            var retired=engine.maintenanceState();
            assertNull(retired.fields().get("previousTransitionPose"));
            assertEquals(Long.MIN_VALUE,retired.fields().get("previousTransitionTick"));
            assertNull(retired.fields().get("transitionTickFrame"));
            assertEquals(0,engine.completedRowTransitions());
            rig.clock.advanceTick();engine.tick();assertEquals(0,engine.completedRowTransitions());
            rig.clock.advanceTick();engine.tick();assertEquals(1,engine.completedRowTransitions());
        }
    }

    @Test void liveInvocationCannotBeLeasedAndLegitimateMaintenanceResumeWorksAfterItRetires() throws Exception {
        var rig=new FarmMaintenanceWitnessTest.Rig();rig.farm("wart");
        Runnable[] callback={null};
        var player=new PlayerService(){
            public boolean isAvailable(){return rig.player.isAvailable();}
            public Vec3 position(){return rig.player.position();}
            public BlockPos blockPos(){return rig.player.blockPos();}
            public float yaw(){return rig.player.yaw();}
            public float pitch(){return rig.player.pitch();}
            public boolean onGround(){return rig.player.onGround();}
            public double horizontalSpeed(){return rig.player.horizontalSpeed();}
            public Direction facing(){return rig.player.facing();}
            public Optional<Vec3> horizontalVelocity(){
                if(callback[0]!=null){var original=callback[0];callback[0]=null;original.run();}
                return Optional.of(new Vec3(0,0,0));
            }
        };
        var services=new FarmServices(rig.clock,player,rig.world,rig.input,rig.rotation,rig.breaking,rig.inventory);
        var farm=new WartCropsFarmModule(services,rig.config,rig.registry);
        assertTrue(farm.preStartCheck().isEmpty());farm.setEnabled(true);var engine=farm.engine();
        set(engine,"fixedRowSteering",true);set(engine,"state",SShapeState.ROW_TRANSITION_FORWARD);
        set(engine,"transitionTarget",rig.player.position);Object token=new Object();
        callback[0]=()->{
            assertTrue(engine.rowInvocationActive());assertTrue(farm.suspendForMaintenance(token));
            assertTrue(farm.captureMaintenanceEngineWitness(token).isEmpty());
            assertFalse(farm.resumeAfterMaintenance(token));
        };
        engine.tick();assertFalse(engine.rowInvocationActive());
        assertEquals(0,engine.completedRowTransitions());
        assertFalse(rig.input.forward||rig.input.back||rig.input.left||rig.input.right);
        var witness=farm.captureMaintenanceEngineWitness(token).orElseThrow();
        assertTrue(farm.rebindForMaintenance(token,witness));assertTrue(farm.resumeAfterMaintenance(token));
        assertFalse(farm.isSuspended());assertTrue(farm.isEnabled());
        assertNull(engine.maintenanceState().fields().get("fixedTickFrame"));
        assertNull(engine.maintenanceState().fields().get("transitionTickFrame"));
    }

    private static void set(Object target,String name,Object value) throws Exception {
        Field field=target.getClass().getDeclaredField(name);field.setAccessible(true);field.set(target,value);
    }
}
