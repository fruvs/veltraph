package dev.veltraph.client.garden.visitor;

import dev.veltraph.client.config.*;
import dev.veltraph.client.farming.domain.*;
import dev.veltraph.client.farming.module.*;
import dev.veltraph.client.service.*;
import dev.veltraph.client.testutil.*;
import dev.veltraph.client.world.BlockPos;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.function.Predicate;
import static org.junit.jupiter.api.Assertions.*;

/** Exercises the actual FarmModule admission/checkpoint contract with the visitor's stationary fixture engine. */
class VisitorFixtureMaintenanceProtocolTest {
    static final class Breaking implements BlockBreakingService {
        record Binding(Object owner,Object context,Predicate<BlockPos> predicate) implements TargetGuardBinding {}
        Object context=new Object();Binding binding;boolean held;int installs,rebinds;
        public void setBreaking(boolean held){this.held=held;}public boolean isBreaking(){return held;}
        public void installTargetGuard(Object owner,Predicate<BlockPos> predicate){binding=new Binding(owner,context,predicate);installs++;}
        public Optional<TargetGuardBinding> targetGuardBinding(Object owner){return binding!=null&&binding.owner()==owner?Optional.of(binding):Optional.empty();}
        public boolean targetGuardBoundToCurrentContext(Object owner,TargetGuardBinding candidate){return candidate==binding&&binding!=null&&binding.owner()==owner&&binding.context()==context;}
        public Optional<Set<BlockPos>> pendingBlockChanges(Object owner){return binding!=null&&binding.owner()==owner&&binding.context()==context?Optional.of(Set.of()):Optional.empty();}
        public Optional<TargetGuardBinding> rebindTargetGuard(Object owner,TargetGuardBinding candidate){if(candidate!=binding||binding.owner()!=owner||held)return Optional.empty();binding=new Binding(owner,context,binding.predicate());rebinds++;return Optional.of(binding);}
    }
    static final class StationaryFarm extends FarmModule {
        final boolean checkpoint,nativeGuard;int starts,ticks,resumeHooks;Object engine;
        StationaryFarm(FarmServices services,ConfigService config,boolean checkpoint,boolean nativeGuard){super("visitor_protocol_fixture","Visitor protocol fixture",services,config,FarmLayoutRegistry.createDefault());this.checkpoint=checkpoint;this.nativeGuard=nativeGuard;}
        public FarmLayoutProfile activeProfile(){return registry.byId(FarmLayoutRegistry.WART_CROPS_SSHAPE).orElseThrow();}public String debugState(){return "stationary fixture";}protected List<String> toolKeywords(){return List.of("hoe");}
        public void onEnable(){starts++;engine=new Object();if(nativeGuard)services.breaking().installTargetGuard(this,pos->false);}
        protected void onTickFarming(){ticks++;}protected void onResumeAfterRewarp(){resumeHooks++;}
        protected Optional<FarmMaintenanceState> maintenanceProgress(){return checkpoint?Optional.of(FarmMaintenanceState.of(engine,"starts",starts,"ticks",ticks,"resumeHooks",resumeHooks)):Optional.empty();}
    }
    static final class Rig {
        final Breaking breaking=new Breaking();final MapConfigService config=new MapConfigService();final FakeInventoryService inventory=new FakeInventoryService().setItem(0,"Iron Hoe");
        final FarmServices services=new FarmServices(new FakeClock(),new FakePlayerService(),new FakeWorldService(),new RecordingInputService(),new FakeRotationService(0,0),breaking,inventory);
        StationaryFarm start(boolean checkpoint,boolean guard){config.set(FarmingConfig.REWARP_REQUIRED,false);config.set(FarmingConfig.FARMING_TOOL_SLOT,0);var farm=new StationaryFarm(services,config,checkpoint,guard);assertTrue(farm.preStartCheck().isEmpty());farm.setEnabled(true);farm.onTick();return farm;}
    }
    @Test void originalFixtureWithoutFamilyCheckpointIsAlwaysRejectedBeforeReservation(){var rig=new Rig();var farm=rig.start(false,false);assertEquals("Farm family has no exact maintenance checkpoint",farm.maintenanceEngineReadinessError().orElseThrow());assertEquals(0,rig.breaking.installs);assertFalse(farm.isSuspended());assertEquals(1,farm.starts);assertEquals(1,farm.ticks);}
    @Test void checkpointWithoutCurrentNativeCropGuardRemainsRejected(){var rig=new Rig();var farm=rig.start(true,false);assertEquals("Farm native crop guard is unavailable or stale",farm.maintenanceEngineReadinessError().orElseThrow());}
    @Test void correctedStationaryEngineRetainsSnapshotDenyAllGuardAndSameEnableAcrossPauseResume(){
        var rig=new Rig();var farm=rig.start(true,true);assertTrue(farm.maintenanceEngineReadinessError().isEmpty());assertFalse(rig.breaking.binding.predicate().test(new BlockPos(0,69,0)));
        Object engine=farm.engine,token=new Object();assertTrue(farm.suspendForMaintenance(token));var witness=farm.captureMaintenanceEngineWitness(token).orElseThrow();
        for(int tick=0;tick<12;tick++)farm.onTick();assertTrue(farm.maintenanceEngineUnchanged(token,witness));assertEquals(1,farm.ticks);assertEquals(1,farm.starts);assertEquals(0,farm.resumeHooks);assertSame(engine,farm.engine);
        assertTrue(farm.rebindForMaintenance(token,witness));assertTrue(farm.resumeAfterMaintenance(token));assertEquals(1,rig.breaking.installs);assertEquals(1,rig.breaking.rebinds);assertSame(engine,farm.engine);assertEquals(1,farm.starts);assertEquals(0,farm.resumeHooks);farm.onTick();assertEquals(2,farm.ticks);
    }
    @Test void changedFixtureEngineCountersCannotReuseCapturedCheckpoint(){var rig=new Rig();var farm=rig.start(true,true);Object token=new Object();farm.suspendForMaintenance(token);var witness=farm.captureMaintenanceEngineWitness(token).orElseThrow();farm.ticks++;assertFalse(farm.maintenanceEngineUnchanged(token,witness));assertFalse(farm.rebindForMaintenance(token,witness));assertFalse(farm.resumeAfterMaintenance(token));}
}
