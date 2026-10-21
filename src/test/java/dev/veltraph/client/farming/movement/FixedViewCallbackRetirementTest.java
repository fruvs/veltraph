package dev.veltraph.client.farming.movement;

import dev.veltraph.client.config.*;
import dev.veltraph.client.service.*;
import dev.veltraph.client.service.stub.*;
import dev.veltraph.client.testutil.*;
import dev.veltraph.client.world.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Real engine caller sequencing with adversarial model callbacks; supplies no native authority. */
class FixedViewCallbackRetirementTest {
    static class Config implements ConfigService {
        final MapConfigService store=new MapConfigService();Runnable cadence,switchOnTp;
        public <T>T get(ConfigKey<T> key){if(key==FarmingConfig.ATTACK_CADENCE_TICKS&&cadence!=null){var r=cadence;cadence=null;r.run();}if(key==FarmingConfig.NETHERWART_SWITCH_DIRECTION_ON_TP&&switchOnTp!=null){var r=switchOnTp;switchOnTp=null;r.run();}return store.get(key);}
        public <T>void set(ConfigKey<T> k,T v){store.set(k,v);}public boolean isSet(ConfigKey<?> k){return store.isSet(k);}public void reset(ConfigKey<?> k){store.reset(k);}
    }
    static class Geometry implements SShapeGeometry {
        Direction heading;FixedRowView view=new FixedRowView(-70,2);Runnable viewCallback;
        public void setHeading(Direction h){heading=h;}public Direction heading(){return heading;}
        public boolean nearMiddle(){return true;}public boolean blockInFront(){return true;}
        public boolean canStrafe(TurnDirection s){return true;}public boolean canStepForward(){return true;}
        public TurnDirection movableLateral(){return TurnDirection.LEFT;}public Vec3 targetVec(){return new Vec3(51.5,71.5,-41.5);}
        public boolean fixedViewHarvestTargetAvailable(){return true;}
        public FixedRowView initialFixedRowView(){var original=view;if(viewCallback!=null){var r=viewCallback;viewCallback=null;r.run();}return original;}
    }
    static class Input implements InputService {
        final List<String> events=new ArrayList<>();Runnable left,forward;
        public void setForwardHeld(boolean h){events.add("F"+h);if(forward!=null){var r=forward;forward=null;r.run();}}
        public void setBackHeld(boolean h){events.add("B"+h);}public void setLeftHeld(boolean h){events.add("L"+h);if(left!=null){var r=left;left=null;r.run();}}
        public void setRightHeld(boolean h){events.add("R"+h);}public void setSneakHeld(boolean h){events.add("C"+h);}public void setJumpHeld(boolean h){events.add("J"+h);}
        public void setSprintHeld(boolean h){events.add("S"+h);}public void releaseAllMovement(){throw new AssertionError("Fixed cleanup borrowed unguarded bulk writer");}
    }
    record Setup(Config config,Geometry geometry,Input input,SShapeRowEngine engine) {}
    Setup setup(){var c=new Config();c.set(FarmingConfig.ROTATION_USE_MOUSE_GCD,false);c.set(FarmingConfig.ROTATION_ACCELERATION,1d);
        c.set(FarmingConfig.ROTATION_MAX_YAW_DEG_PER_TICK,90d);c.set(FarmingConfig.ROTATION_MAX_PITCH_DEG_PER_TICK,90d);
        var g=new Geometry();var input=new Input();var p=new FakePlayerService();p.position=new Vec3(50.5,70,-41.5);
        var rotation=new FakeRotationService();rotation.yaw=-90;rotation.pitch=30;
        var breakPort=new BlockBreakingService(){public void setBreaking(boolean h){input.events.add("A"+h);}public boolean isBreaking(){return false;}};
        var services=new FarmServices(new FakeClock(),p,new NoOpWorldService(),input,rotation,breakPort,new NoOpInventoryService());
        return new Setup(c,g,input,new SShapeRowEngine(c,services,g,()->true));}
    void settle(Setup s){s.engine.start();for(int i=0;i<30;i++)s.engine.tick();assertEquals(SShapeState.FARMING_SIDEWAYS,s.engine.state());s.input.events.clear();}
    void replace(Setup s){s.engine.stop();s.geometry.view=new FixedRowView(-65,3);s.engine.start();s.input.events.clear();s.input.events.add("CUT");}
    @Test void firstStrafeSetterCannotWriteSecondKeyIntoReplacementRun(){var s=setup();settle(s);s.input.left=()->replace(s);s.engine.tick();assertEquals(List.of("CUT"),s.input.events);assertEquals(SShapeState.PREPARE_TOOL,s.engine.state());}
    @Test void cadenceCallbackCannotPublishOldAttackOrStrafe(){var s=setup();settle(s);s.config.cadence=()->replace(s);s.engine.tick();assertEquals(List.of("CUT"),s.input.events);assertEquals(-65,s.engine.targetYaw());}
    @Test void firstCleanupSetterCannotRunOldBulkTailOrResetNewState(){var s=setup();settle(s);s.input.forward=()->replace(s);s.engine.stop();assertEquals(List.of("CUT"),s.input.events);assertEquals(SShapeState.PREPARE_TOOL,s.engine.state());assertEquals(3,s.engine.targetPitch());}
    @Test void firstViewProviderCannotCommitReturnedOldViewAfterNewStart(){var s=setup();s.geometry.viewCallback=()->{s.geometry.view=new FixedRowView(-65,3);s.engine.start();};s.engine.start();assertEquals(-65,s.engine.targetYaw());assertEquals(3,s.engine.targetPitch());}
    @Test void viewCallbackCannotPumpPartiallyConstructedRun(){var s=setup();settle(s);s.geometry.viewCallback=()->{s.engine.tick();assertTrue(s.input.events.isEmpty(),"Setup permits no tick inputs before its view/anchor are complete");};s.engine.start();assertTrue(s.input.events.isEmpty());assertEquals(SShapeState.PREPARE_TOOL,s.engine.state());}
    @Test void resumeCallbackCannotPumpOldFarmingStateIntoNewSetup(){var s=setup();settle(s);s.config.switchOnTp=()->{s.engine.tick();assertTrue(s.input.events.isEmpty(),"Resume setup callback cannot borrow an active tick");};s.engine.resumeFromObservedPosition();assertEquals(SShapeState.AIMING,s.engine.state());assertTrue(s.input.events.stream().noneMatch(e->e.endsWith("true")));}
    @Test void quiesceDuringFirstStrafeSetterSkipsEveryOldTail(){var s=setup();settle(s);s.input.left=()->{s.engine.quiesceInitialCoverage();s.input.events.clear();s.input.events.add("CUT");};s.engine.tick();assertEquals(List.of("CUT"),s.input.events);assertEquals(SShapeState.STOPPED,s.engine.state());}
    @Test void quiesceDuringCadenceReadCannotPublishAttackOrMovement(){var s=setup();settle(s);s.config.cadence=()->{assertEquals(List.of("Cfalse","Jfalse"),s.input.events,"Original posture releases precede cadence read");s.engine.quiesceInitialCoverage();s.input.events.clear();s.input.events.add("CUT");};s.engine.tick();assertEquals(List.of("CUT"),s.input.events);assertEquals(SShapeState.STOPPED,s.engine.state());}
    @Test void quiesceDuringFirstCleanupSetterSkipsOldReleaseTail(){var s=setup();settle(s);s.input.forward=()->{s.engine.quiesceInitialCoverage();s.input.events.clear();s.input.events.add("CUT");};s.engine.stop();assertEquals(List.of("CUT"),s.input.events);assertEquals(SShapeState.STOPPED,s.engine.state());}
    @Test void quiesceDuringInitialViewProviderCannotCommitOldSetup(){var s=setup();s.geometry.viewCallback=s.engine::quiesceInitialCoverage;s.engine.start();assertEquals(SShapeState.STOPPED,s.engine.state());assertTrue(s.input.events.isEmpty());}
    @Test void quiesceIsCallbackFreeAndFreshStartStillWorks(){var s=setup();settle(s);s.engine.quiesceInitialCoverage();s.engine.tick();assertTrue(s.input.events.isEmpty());assertEquals(SShapeState.STOPPED,s.engine.state());s.engine.start();assertEquals(SShapeState.PREPARE_TOOL,s.engine.state());}

}
