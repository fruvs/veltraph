package dev.veltraph.client.farming.movement;

import dev.veltraph.client.config.MapConfigService;
import dev.veltraph.client.service.FarmServices;
import dev.veltraph.client.service.stub.*;
import dev.veltraph.client.testutil.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Exact checkpoint compatibility plus genuine retained/in-flight source observation retirement. */
class RowTransitionObservationRetirementTest {
    @Test void unusedEngineRetirementPreservesItsExactIdleCheckpoint(){
        var config=new MapConfigService();var services=new FarmServices(new FakeClock(),new FakePlayerService(),
                new NoOpWorldService(),new RecordingInputService(),new FakeRotationService(),
                new RecordingBlockBreaking(),new NoOpInventoryService());
        var engine=new SShapeRowEngine(config,services,new FakeSShapeGeometry(),()->true);
        var before=engine.maintenanceState();engine.retireRowTransitionObservation();
        assertEquals(before,engine.maintenanceState());
        assertEquals(SShapeState.IDLE,engine.state());
    }

    @Test void retainedActualObservationStillAdvancesEpochAndLosesItsCredit(){
        var fixture=new SShapeTransitionContinuityTest();var rig=fixture.active();fixture.begin(rig);
        rig.player().delegate.position=rig.geometry().target;fixture.tick(rig);
        var before=rig.engine().maintenanceState();rig.engine().retireRowTransitionObservation();
        var after=rig.engine().maintenanceState();assertNotEquals(before,after);
        assertNull(after.fields().get("previousTransitionPose"));
        assertEquals(Long.MIN_VALUE,after.fields().get("previousTransitionTick"));
        assertEquals((long)before.fields().get("transitionObservationGeneration")+1,
                after.fields().get("transitionObservationGeneration"));
    }

    @Test void liveInvocationWithoutAnyPriorPoseStillRetiresTheOldProviderTail(){
        var fixture=new SShapeTransitionContinuityTest();var rig=fixture.active();fixture.begin(rig);
        assertNull(rig.engine().maintenanceState().fields().get("previousTransitionPose"));
        var before=rig.engine().maintenanceState();
        rig.player().velocityRead=()->{rig.engine().retireRowTransitionObservation();rig.input().releaseAllMovement();};
        fixture.tick(rig);var after=rig.engine().maintenanceState();
        assertEquals((long)before.fields().get("transitionObservationGeneration")+1,
                after.fields().get("transitionObservationGeneration"));
        assertNull(after.fields().get("previousTransitionPose"));
        assertFalse(rig.input().forward||rig.input().back||rig.input().left||rig.input().right);
        assertEquals(0,rig.engine().completedRowTransitions());
    }
}
