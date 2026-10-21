package dev.veltraph.client.farming.movement;
import dev.veltraph.client.config.*;
import dev.veltraph.client.service.*;
import dev.veltraph.client.service.stub.*;
import dev.veltraph.client.testutil.*;
import dev.veltraph.client.world.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
/** Actual same engine reset/permission/transition regressions; no native permission is supplied by these models. */
class SShapeAtomicCycleStartTest {
 private SShapeInitialCoverageTest.Rig active(){return new SShapeInitialCoverageTest().rig();}
 @Test void activeMetadataReconfigurationStillThrowsWithoutChangingProgress(){var r=active();var before=r.engine().maintenanceState();assertThrows(IllegalStateException.class,()->r.engine().setInitialLaneCoverage(false));assertThrows(IllegalStateException.class,()->r.engine().setInitialLaneCoverage(true));assertEquals(before,r.engine().maintenanceState());}
 @Test void completeOriginalInitialLaneThenRestartSameEngineClearsOldEnds(){var r=active();var e=r.engine();r.geometry().canStrafeLeft=false;r.geometry().canStrafeRight=true;r.player().position=new Vec3(.5,64,-20.5);e.tick();r.geometry().canStrafeLeft=true;r.geometry().canStrafeRight=false;r.player().position=new Vec3(.5,64,30.5);e.tick();r.geometry().canStrafeLeft=false;r.geometry().canStrafeRight=true;r.player().position=new Vec3(.5,64,-20.5);e.tick();assertFalse(e.initialCoveragePending());e.start(true);assertSame(e,r.engine());assertEquals(SShapeState.PREPARE_TOOL,e.state());assertTrue(e.initialCoveragePending());assertEquals(0,e.completedRowTransitions());assertNull(e.transitionTarget());}
 @Test void actualPreparedCycleRestartCanDisableCoverageOnlyThroughCompleteStart(){var r=active();var e=r.engine();e.start(false);assertEquals(SShapeState.PREPARE_TOOL,e.state());assertFalse(e.initialCoveragePending());assertThrows(IllegalStateException.class,()->e.setInitialLaneCoverage(true));e.stop();e.setInitialLaneCoverage(true);e.start();assertTrue(e.initialCoveragePending());}
 @Test void restartedCoverageStillRequiresFreshPermissionAndStopsOnRevocation(){var r=active();r.engine().start(true);r.permit()[0]=false;r.engine().tick();assertEquals(SShapeState.STOPPED,r.engine().state());assertFalse(r.engine().initialCoveragePending());r.permit()[0]=true;r.engine().tick();assertEquals(SShapeState.STOPPED,r.engine().state());}
 @Test void foreignRevocationAfterRestartDoesNotClearForeignHeldKeys(){var r=active();r.engine().start(true);r.engine().setInitialCoverageRevocation(()->{});r.input().right=true;r.permit()[0]=false;r.engine().tick();assertEquals(SShapeState.STOPPED,r.engine().state());assertTrue(r.input().right);}
 @Test void interruptedExistingStartEffectsDoNotPublishNewCoverageState(){var cfg=new MapConfigService();var p=new FakePlayerService();var input=new RecordingInputService();var rotation=new RotationService(){public float yaw(){return 0;}public float pitch(){return 0;}public void aim(float y,float p){}public void lookAt(Vec3 v){}@Override public void cancelRotation(){throw new IllegalStateException("Declared start cancellation failure");}};var e=new SShapeRowEngine(cfg,new FarmServices(new FakeClock(),p,new NoOpWorldService(),input,rotation,new RecordingBlockBreaking(),new NoOpInventoryService()),new SShapeInitialCoverageTest.Geometry(),FarmingConfig.SSHAPE_HOLD_FORWARD_POLICY,()->true);e.setInitialLaneCoverage(false);assertThrows(IllegalStateException.class,()->e.start(true));assertEquals(SShapeState.IDLE,e.state());assertFalse(e.initialCoveragePending());}
}
