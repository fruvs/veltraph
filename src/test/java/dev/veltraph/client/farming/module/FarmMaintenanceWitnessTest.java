package dev.veltraph.client.farming.module;

import dev.veltraph.client.config.*;
import dev.veltraph.client.farming.domain.*;
import dev.veltraph.client.farming.movement.planner.SugarcanePlanner;
import dev.veltraph.client.service.*;
import dev.veltraph.client.testutil.*;
import dev.veltraph.client.world.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.*;
import java.lang.reflect.Field;
import static org.junit.jupiter.api.Assertions.*;

class FarmMaintenanceWitnessTest {
    static final class GuardedBreaking implements BlockBreakingService {
        record Binding(Object owner,Object context,Object predicate) implements TargetGuardBinding {}
        Binding guard;Object context=new Object();boolean held;boolean unknown;boolean rejectCas;
        Set<BlockPos> pending=Set.of();int rebinds;int installs;
        public void setBreaking(boolean value){held=value;}public boolean isBreaking(){return held;}
        public void installTargetGuard(Object owner,java.util.function.Predicate<BlockPos> predicate){guard=new Binding(owner,context,predicate);installs++;}
        public void clearTargetGuard(Object owner){if(guard!=null&&guard.owner()==owner)guard=null;}
        public Optional<TargetGuardBinding> targetGuardBinding(Object owner){return guard!=null&&guard.owner()==owner?Optional.of(guard):Optional.empty();}
        public boolean targetGuardBoundToCurrentContext(Object owner,TargetGuardBinding binding){return binding==guard&&guard!=null&&guard.owner()==owner&&guard.context()==context;}
        public Optional<Set<BlockPos>> pendingBlockChanges(Object owner){return unknown||guard==null||guard.owner()!=owner||guard.context()!=context?Optional.empty():Optional.of(Set.copyOf(pending));}
        public Optional<TargetGuardBinding> rebindTargetGuard(Object owner,TargetGuardBinding expected){
            if(rejectCas||expected!=guard||guard.owner()!=owner||unknown||!pending.isEmpty()||held)return Optional.empty();
            rebinds++;guard=new Binding(owner,context,guard.predicate());return Optional.of(guard);
        }
    }
    static final class CurrentWorld extends FakeWorldService {
        final Map<BlockPos,String> blocks=new HashMap<>();boolean loaded=true;String age="2";
        @Override public boolean isLoaded(BlockPos position){return loaded;}
        @Override public String blockId(BlockPos position){return blocks.getOrDefault(position,"");}
        @Override public Optional<String> blockProperty(BlockPos position,String property){return property.equals("age")?Optional.of(age):Optional.empty();}
    }
    static final class Rig {
        final FakeClock clock=new FakeClock();final CurrentWorld world=new CurrentWorld();
        final FakePlayerService player=new FakePlayerService();final RecordingInputService input=new RecordingInputService();
        final GuardedBreaking breaking=new GuardedBreaking();final FakeRotationService rotation=new FakeRotationService(0,0);
        final FakeInventoryService inventory=new FakeInventoryService().setItem(2,"hoe wart axe dicer cactus knife cocoa fungi");
        final MapConfigService config=new MapConfigService();final FarmLayoutRegistry registry=FarmLayoutRegistry.createDefault();
        final FarmServices services=new FarmServices(clock,player,world,input,rotation,breaking,inventory);
        FarmModule farm(String family){
            config.set(FarmingConfig.FARMING_TOOL_SLOT,2);config.set(FarmingConfig.REWARP_REQUIRED,false);
            player.position=new Vec3(.5,64,.5);player.blockPos=new BlockPos(0,64,0);player.onGround=true;
            return switch(family){case "wart"->new WartCropsFarmModule(services,config,registry);
                case "cane"->new SugarcaneFlowersFarmModule(services,config,registry);
                case "cocoa"->new CocoaFarmModule(services,config,registry);
                case "gourd"->new PumpkinMelonFarmModule(services,config,registry);
                case "mushroom"->new MushroomFarmModule(services,config,registry);
                case "cactus"->new CactusFarmModule(services,config,registry);default->throw new AssertionError();};
        }
        FarmModule start(String family){var farm=farm(family);assertTrue(farm.preStartCheck().isEmpty());farm.setEnabled(true);assertTrue(farm.isEnabled());return farm;}
    }
    @ParameterizedTest @ValueSource(strings={"wart","cane","cocoa","gourd","mushroom","cactus"})
    void allFamiliesRetainExactEngineCheckpointAndResumeNextTick(String family)throws Exception {
        var r=new Rig();var farm=r.start(family);Object token=new Object();
        // These represent actual retained source counters/targets, rather than an identity-only oracle.
        seedCheckpoint(farm,family);
        r.breaking.setBreaking(true);assertTrue(farm.maintenanceEngineReadinessError().isEmpty(),"continuous attack permits pause admission");
        assertTrue(farm.suspendForMaintenance(token));assertFalse(r.breaking.held);
        var witness=farm.captureMaintenanceEngineWitness(token).orElseThrow();Object predicate=r.breaking.guard.predicate();
        int slotCalls=r.inventory.selectCalls,installs=r.breaking.installs;
        r.clock.advanceMillis(25_000);for(int i=0;i<20;i++)farm.onTick();
        assertTrue(farm.maintenanceEngineUnchanged(token,witness));
        r.breaking.context=new Object();assertTrue(farm.rebindForMaintenance(token,witness));
        assertEquals(witness.progress(),captureProgress(farm));assertSame(predicate,r.breaking.guard.predicate());
        assertEquals(installs,r.breaking.installs);assertEquals(slotCalls,r.inventory.selectCalls);
        assertEquals(1,r.breaking.rebinds);assertFalse(farm.rebindForMaintenance(token,witness),"one rebind only");
        assertTrue(farm.resumeAfterMaintenance(token));assertTrue(farm.isEnabled());assertFalse(farm.isSuspended());
        assertFalse(farm.maintenanceEngineUnchanged(token,witness));
        farm.onTick();assertNotEquals(witness.progress(),captureProgress(farm),"natural next tick advances the retained engine");
    }
    private static void seedCheckpoint(FarmModule farm,String family)throws Exception {
        switch(family){
            case "wart"->{Object e=field(farm,"sShapeEngine");set(e,"completedRowTransitions",7);set(e,"transitionStartedMs",1234L);}
            case "cane"->{((SugarcanePlanner)field(farm,"planner")).onLaneSwitch();set(farm,"lastTurnAt",new BlockPos(3,64,5));}
            case "cocoa"->{set(field(farm,"world"),"switchOrigin",new BlockPos(9,64,2));set(field(farm,"boundaryHarvest"),"targetSince",1234L);}
            case "gourd"->{set(field(farm,"sourcePlanner"),"farmingTicks",31);set(field(farm,"waterPlanner"),"stationaryTicks",9);}
            case "mushroom"->{set(field(farm,"ring"),"turns",4);set(farm,"stillSinceMs",2345L);}
            case "cactus"->{set(field(farm,"aceRecentering"),"pulses",3);set(field(farm,"aceDeferredHarvest"),"started",3456L);@SuppressWarnings("unchecked")var targets=(Set<BlockPos>)field(farm,"aceInterruptedTargets");targets.add(new BlockPos(8,75,4));}
        }
    }
    private static FarmMaintenanceState captureProgress(FarmModule farm)throws Exception {
        var m=FarmModule.class.getDeclaredMethod("fullMaintenanceProgress");m.setAccessible(true);
        @SuppressWarnings("unchecked")var state=(Optional<FarmMaintenanceState>)m.invoke(farm);return state.orElseThrow();
    }
    static Object field(Object owner,String name)throws Exception{var f=owner.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(owner);}
    static void set(Object owner,String name,Object value)throws Exception{var f=owner.getClass().getDeclaredField(name);f.setAccessible(true);f.set(owner,value);}
    @Test void pauseDrainsObservedPredictionsBeforeAnyCaptureOrRebind(){
        var r=new Rig();var farm=r.start("cane");Object token=new Object();r.breaking.pending=Set.of(new BlockPos(0,65,0));r.breaking.held=true;
        assertTrue(farm.maintenanceEngineReadinessError().isEmpty());assertTrue(farm.suspendForMaintenance(token));
        assertTrue(farm.captureMaintenanceEngineWitness(token).isEmpty());assertEquals(0,r.breaking.rebinds);
        assertTrue(farm.maintenanceWitnessReadinessError(token).orElseThrow().contains("pending"));
        r.breaking.pending=Set.of();assertTrue(farm.captureMaintenanceEngineWitness(token).isPresent());
    }
    @ParameterizedTest @ValueSource(strings={"wart","cane","cocoa","gourd","mushroom","cactus"})
    void actualLogicalMutationRejectsBeforeNativeGuardMutation(String family)throws Exception{
        var r=new Rig();var farm=r.start(family);Object token=new Object();farm.suspendForMaintenance(token);
        var witness=farm.captureMaintenanceEngineWitness(token).orElseThrow();seedCheckpoint(farm,family);
        assertFalse(farm.maintenanceEngineUnchanged(token,witness));assertFalse(farm.rebindForMaintenance(token,witness));
        assertEquals(0,r.breaking.rebinds);assertFalse(farm.resumeAfterMaintenance(token));
    }
    @Test void foreignGuardCannotBeOverwrittenAndWrongTokenCannotCapture(){
        var r=new Rig();var farm=r.start("cane");Object token=new Object();farm.suspendForMaintenance(token);
        assertTrue(farm.captureMaintenanceEngineWitness(new Object()).isEmpty());var w=farm.captureMaintenanceEngineWitness(token).orElseThrow();
        Object foreign=new Object();r.breaking.installTargetGuard(foreign,p->true);Object guard=r.breaking.guard;
        assertFalse(farm.rebindForMaintenance(token,w));assertSame(guard,r.breaking.guard);assertEquals(0,r.breaking.rebinds);
    }
    @Test void replayCannotCrossDisableOrNewSuspensionEvenWithSameToken(){
        var r=new Rig();var farm=r.start("cane");Object token=new Object();farm.suspendForMaintenance(token);var old=farm.captureMaintenanceEngineWitness(token).orElseThrow();
        farm.setEnabled(false);farm.setEnabled(true);assertTrue(farm.suspendForMaintenance(token));var fresh=farm.captureMaintenanceEngineWitness(token).orElseThrow();
        assertFalse(farm.rebindForMaintenance(token,old));assertTrue(farm.rebindForMaintenance(token,fresh));
    }
    @Test void unknownAckOrNewPendingContextOrCasRaceCannotResume(){
        for(String condition:List.of("unknown","pending","race")){
            var r=new Rig();var farm=r.start("cane");Object token=new Object();farm.suspendForMaintenance(token);var w=farm.captureMaintenanceEngineWitness(token).orElseThrow();
            if(condition.equals("unknown"))r.breaking.unknown=true;
            if(condition.equals("pending"))r.breaking.pending=Set.of(new BlockPos(1,64,1));
            if(condition.equals("race"))r.breaking.rejectCas=true;
            assertFalse(farm.rebindForMaintenance(token,w));assertFalse(farm.resumeAfterMaintenance(token));assertEquals(0,r.breaking.rebinds);
        }
    }
    @Test void guardOrLogicalReplacementAfterRebindRejectsResume()throws Exception{
        for(boolean replace:List.of(true,false)){
            var r=new Rig();var farm=r.start("cane");Object token=new Object();farm.suspendForMaintenance(token);var w=farm.captureMaintenanceEngineWitness(token).orElseThrow();assertTrue(farm.rebindForMaintenance(token,w));
            if(replace)r.breaking.installTargetGuard(new Object(),p->true);else seedCheckpoint(farm,"cane");assertFalse(farm.resumeAfterMaintenance(token));
        }
    }
    @Test void unknownFamilyCannotPretendToHaveEngineWitness(){
        var r=new Rig();var farm=new FarmModule("unknown","unknown",r.services,r.config,r.registry){
            public FarmLayoutProfile activeProfile(){return r.registry.byId(FarmLayoutRegistry.WART_CROPS_SSHAPE).orElseThrow();}
            public String debugState(){return "fake identity";}protected List<String> toolKeywords(){return List.of("hoe");}
            protected void onTickFarming(){}public void onEnable(){r.breaking.installTargetGuard(this,p->true);}
        };farm.setEnabled(true);assertTrue(farm.maintenanceEngineReadinessError().orElseThrow().contains("no exact"));
        Object token=new Object();farm.suspendForMaintenance(token);assertTrue(farm.captureMaintenanceEngineWitness(token).isEmpty());
    }
    @Test void pendingCollectionsAreImmutableAndTheirMutationIsDetected()throws Exception{
        var r=new Rig();var farm=r.start("cactus");seedCheckpoint(farm,"cactus");Object token=new Object();farm.suspendForMaintenance(token);var w=farm.captureMaintenanceEngineWitness(token).orElseThrow();
        assertThrows(UnsupportedOperationException.class,()->w.progress().fields().clear());
        @SuppressWarnings("unchecked")var targets=(Set<BlockPos>)field(farm,"aceInterruptedTargets");targets.add(new BlockPos(9,75,4));
        assertFalse(farm.maintenanceEngineUnchanged(token,w));
    }

    @ParameterizedTest @ValueSource(strings={"wart-vertical","wart-ace","cane-ace","cocoa-current","gourd-captured","gourd-saigo","gourd-compact","mushroom-staircase","mushroom-ring","cactus-ace"})
    void alternateSourceBindingsPreserveAllNestedCheckpoints(String variant)throws Exception{
        var r=new Rig();String family=variant.substring(0,variant.indexOf('-'));
        if(variant.equals("wart-ace"))r.config.set(FarmingConfig.WART_CROPS_DESIGN,WartCropsDesign.ACE_TWO_KEY_LOOP);
        if(variant.equals("cocoa-current"))r.config.set(FarmingConfig.COCOA_FARM_MODE,CocoaFarmMode.CURRENT_TWO_KEY_LOOP);
        if(variant.equals("gourd-captured"))r.config.set(FarmingConfig.PUMPKIN_FARM_DESIGN,PumpkinFarmDesign.CAPTURED_DEFAULT);
        if(variant.equals("gourd-saigo"))r.config.set(FarmingConfig.PUMPKIN_FARM_DESIGN,PumpkinFarmDesign.SAIGO_CURRENT);
        if(variant.equals("gourd-compact")){r.config.set(FarmingConfig.PUMPKIN_FARM_DESIGN,PumpkinFarmDesign.MELONKINGDE_WATER);r.config.set(FarmingConfig.ROTATION_USE_MOUSE_GCD,false);}
        if(variant.equals("mushroom-ring"))r.config.set(FarmingConfig.MUSHROOM_FARM_DESIGN,MushroomFarmDesign.RING_CURRENT);
        if(variant.equals("cactus-ace"))r.config.set(CactusFarmModule.ACE_PTP,true);
        FarmModule farm=r.farm(family);
        if(variant.equals("wart-vertical"))((WartCropsFarmModule)farm).setMode(FarmMovementMode.VERTICAL);
        if(variant.equals("mushroom-staircase"))((MushroomFarmModule)farm).setMode(FarmMovementMode.STAIRCASE);
        if(variant.equals("cane-ace"))((SugarcaneFlowersFarmModule)farm).setDesign(SugarcaneFlowersFarmModule.Design.ACE_TWO_PLOT);
        assertTrue(farm.preStartCheck().isEmpty());farm.setEnabled(true);seedCheckpoint(farm,family);
        Object token=new Object();assertTrue(farm.suspendForMaintenance(token));var w=farm.captureMaintenanceEngineWitness(token).orElseThrow();
        checkFieldCoverage(w.progress());r.clock.advanceMillis(50_000);farm.onTick();r.breaking.context=new Object();
        assertTrue(farm.rebindForMaintenance(token,w));assertTrue(farm.maintenanceEngineUnchanged(token,w));
        assertTrue(farm.resumeAfterMaintenance(token));assertEquals(w.progress(),captureProgress(farm));
    }
    private static void checkFieldCoverage(FarmMaintenanceState state){
        Object actual=state.engineIdentity();
        // The base checkpoint adds lease metadata; each nested exact source checkpoint must expand
        // every declared field. This prevents future planner work from silently evading the contract.
        if(!state.fields().containsKey("family")){
            var declared=new HashSet<String>();
            for(Field field:actual.getClass().getDeclaredFields())if(!field.isSynthetic()&&!java.lang.reflect.Modifier.isStatic(field.getModifiers()))declared.add(field.getName());
            assertEquals(declared,state.fields().keySet(),actual.getClass().getName());
        }
        for(Object value:state.fields().values())if(value instanceof FarmMaintenanceState nested)checkFieldCoverage(nested);
    }
    /** Pure checkpoint fixtures below create no Root source, native actor, private driver issue or input permission. */
    @ParameterizedTest @ValueSource(strings={"wart-vertical","wart-ace"})
    void wartDropDispatchMutationRejectsBeforeGuardRebind(String variant)throws Exception{
        var r=new Rig();var farm=wartVariant(r,variant);Object token=new Object();
        assertTrue(farm.suspendForMaintenance(token));var witness=farm.captureMaintenanceEngineWitness(token).orElseThrow();
        var vertical=nestedVertical(witness.progress());
        assertTrue(vertical.fields().containsKey("dropAttempt"));assertNull(vertical.fields().get("dropAttempt"));
        assertEquals(false,vertical.fields().get("dropDispatch"));
        set(field(farm,"verticalWorld"),"dropDispatch",true);
        assertFalse(farm.maintenanceEngineUnchanged(token,witness));
        assertFalse(farm.rebindForMaintenance(token,witness));assertEquals(0,r.breaking.rebinds);
        assertFalse(farm.resumeAfterMaintenance(token));
    }
    @ParameterizedTest @ValueSource(strings={"wart-vertical","wart-ace"})
    void wartAttemptReferenceIsOpaqueAndMutationRejectsBeforeGuardRebind(String variant)throws Exception{
        var r=new Rig();var farm=wartVariant(r,variant);Object token=new Object();
        assertTrue(farm.suspendForMaintenance(token));var witness=farm.captureMaintenanceEngineWitness(token).orElseThrow();
        Object attempt=pureDropAttempt(farm);set(field(farm,"verticalWorld"),"dropAttempt",attempt);
        assertFalse(farm.maintenanceEngineUnchanged(token,witness));
        var passive=nestedVertical(captureProgress(farm));Object opaque=passive.fields().get("dropAttempt");
        assertNotNull(opaque);assertNotSame(attempt,opaque,"checkpoint must not hand out the positive attempt object");
        assertFalse(farm.rebindForMaintenance(token,witness));assertEquals(0,r.breaking.rebinds);
        assertFalse(farm.resumeAfterMaintenance(token));
    }
    @ParameterizedTest @ValueSource(strings={"attempt","dispatch"})
    void transientWartCustodyCannotObtainMaintenanceWitness(String transientField)throws Exception{
        var r=new Rig();var farm=wartVariant(r,"wart-vertical");
        var checkpointRoute=new dev.veltraph.client.farming.movement.FarmRouteDriver(){
            public boolean startDrop(Vec3 target,int bound){return false;}public State tick(){return State.BLOCKED;}
            public void stop(){}public String status(){return "Pure checkpoint fixture; no movement authority";}
            public Optional<FarmMaintenanceState> maintenanceState(){return Optional.of(FarmMaintenanceState.of(this));}
        };
        farm.setRouteDriver(checkpointRoute);assertTrue(checkpointRoute.maintenanceState().isPresent());
        Object vertical=field(farm,"verticalWorld");set(vertical,"dropTarget",new Vec3(.5,61,.5));
        if(transientField.equals("attempt"))set(vertical,"dropAttempt",pureDropAttempt(farm));else set(vertical,"dropDispatch",true);
        assertTrue(farm.maintenanceEngineReadinessError().orElseThrow().contains("original WATER drop custody"));
        Object token=new Object();assertTrue(farm.suspendForMaintenance(token),"legacy suspension is negative-only; it cannot grant a checkpoint");
        assertTrue(farm.captureMaintenanceEngineWitness(token).isEmpty());assertEquals(0,r.breaking.rebinds);
        assertFalse(farm.resumeAfterMaintenance(token),"legacy no-witness resume cannot reactivate retained drop custody");
        assertTrue(farm.isSuspendedForMaintenance());
    }
    @ParameterizedTest @ValueSource(strings={"wart-vertical","wart-ace"})
    void idleWartLegacyNoWitnessResumePreservesOriginalCheckpoint(String variant)throws Exception{
        var r=new Rig();var farm=wartVariant(r,variant);var before=captureProgress(farm);Object token=new Object();
        int installs=r.breaking.installs,slotCalls=r.inventory.selectCalls;
        assertTrue(farm.suspendForMaintenance(token));
        assertTrue(farm.resumeAfterMaintenance(token));assertFalse(farm.isSuspendedForMaintenance());
        assertEquals(before,captureProgress(farm),"idle legacy resume does not reset or restore the source engine");
        assertEquals(0,r.breaking.rebinds);assertEquals(installs,r.breaking.installs);assertEquals(slotCalls,r.inventory.selectCalls);
    }
    private static WartCropsFarmModule wartVariant(Rig r,String variant){
        if(variant.equals("wart-ace"))r.config.set(FarmingConfig.WART_CROPS_DESIGN,WartCropsDesign.ACE_TWO_KEY_LOOP);
        var farm=(WartCropsFarmModule)r.farm("wart");
        if(variant.equals("wart-vertical"))farm.setMode(FarmMovementMode.VERTICAL);
        assertTrue(farm.preStartCheck().isEmpty());farm.setEnabled(true);return farm;
    }
    private static FarmMaintenanceState nestedVertical(FarmMaintenanceState progress){
        var family=(FarmMaintenanceState)progress.fields().get("family");return (FarmMaintenanceState)family.fields().get("verticalWorld");
    }
    private static Object pureDropAttempt(WartCropsFarmModule farm)throws Exception{
        var ctor=WartCropsFarmModule.VerticalDropAttempt.class.getDeclaredConstructor(WartCropsFarmModule.class,Vec3.class,Vec3.class);
        ctor.setAccessible(true);return ctor.newInstance(farm,new Vec3(.5,64,.5),new Vec3(.5,61,.5));
    }
    @Test void activeNativeDropRouteExplicitlyRejectsBeforeLease()throws Exception{
        var r=new Rig();var farm=r.start("wart");set(field(farm,"sShapeEngine"),"dropTarget",new Vec3(.5,62,.5));
        assertTrue(farm.maintenanceEngineReadinessError().orElseThrow().contains("active native drop"));
        Object token=new Object();farm.suspendForMaintenance(token);assertTrue(farm.captureMaintenanceEngineWitness(token).isEmpty());
    }
    @Test void unknownNativePredictionsBlockAdmissionAndDifferentOwnerCannotReuseWitness(){
        var r=new Rig();var a=r.start("cane");r.breaking.unknown=true;assertTrue(a.maintenanceEngineReadinessError().isPresent());
        r.breaking.unknown=false;Object token=new Object();a.suspendForMaintenance(token);var w=a.captureMaintenanceEngineWitness(token).orElseThrow();
        var r2=new Rig();var b=r2.start("cane");b.suspendForMaintenance(token);b.captureMaintenanceEngineWitness(token).orElseThrow();assertFalse(b.rebindForMaintenance(token,w));
    }

    @Test void freshPredictionWorkAfterRebindBlocksFinalResume(){
        var r=new Rig();var farm=r.start("cane");Object token=new Object();farm.suspendForMaintenance(token);var w=farm.captureMaintenanceEngineWitness(token).orElseThrow();assertTrue(farm.rebindForMaintenance(token,w));
        r.breaking.pending=Set.of(new BlockPos(1,65,1));assertFalse(farm.resumeAfterMaintenance(token));assertTrue(farm.isSuspendedForMaintenance());
    }

    @ParameterizedTest @ValueSource(strings={"wart","cane","cocoa","gourd","mushroom","cactus"})
    void preservedSourcePredicateReadsCurrentWorldAfterNativeGuardRebind(String family){
        var r=new Rig();var farm=r.start(family);BlockPos target=new BlockPos(5,66,5);
        String crop=switch(family){case "wart"->"minecraft:nether_wart";case "cane"->"minecraft:sugar_cane";
            case "cocoa"->"minecraft:cocoa";case "gourd"->"minecraft:melon";case "mushroom"->"minecraft:brown_mushroom";default->"minecraft:cactus";};
        r.world.blocks.put(target,crop);r.world.blocks.put(target.down(),family.equals("wart")?"minecraft:soul_sand":crop);
        @SuppressWarnings("unchecked")var policy=(java.util.function.Predicate<BlockPos>)r.breaking.guard.predicate();assertTrue(policy.test(target));
        Object token=new Object();farm.suspendForMaintenance(token);var witness=farm.captureMaintenanceEngineWitness(token).orElseThrow();
        r.breaking.context=new Object();assertTrue(farm.rebindForMaintenance(token,witness));assertSame(policy,r.breaking.guard.predicate());
        r.world.blocks.put(target,"minecraft:stone");assertFalse(policy.test(target),"fresh protected block must reject through unchanged source predicate");
        r.world.blocks.put(target,crop);r.world.loaded=false;assertFalse(policy.test(target),"unloaded replacement world cannot pass cached policy");
        assertTrue(farm.maintenanceEngineUnchanged(token,witness),"geometry observations are read-only and do not reset logical progress");
    }
    @Test void currentCocoaPredicateReadsFreshMaturityMetadataWithoutPlannerReset(){
        var r=new Rig();r.config.set(FarmingConfig.COCOA_FARM_MODE,CocoaFarmMode.CURRENT_TWO_KEY_LOOP);var farm=r.start("cocoa");
        BlockPos target=new BlockPos(5,66,5);r.world.blocks.put(target,"minecraft:cocoa");
        @SuppressWarnings("unchecked")var policy=(java.util.function.Predicate<BlockPos>)r.breaking.guard.predicate();assertTrue(policy.test(target));
        Object token=new Object();farm.suspendForMaintenance(token);var witness=farm.captureMaintenanceEngineWitness(token).orElseThrow();r.breaking.context=new Object();assertTrue(farm.rebindForMaintenance(token,witness));
        r.world.age="1";assertFalse(policy.test(target),"same crop ID with changed native maturity must reject");assertTrue(farm.maintenanceEngineUnchanged(token,witness));
    }
}
