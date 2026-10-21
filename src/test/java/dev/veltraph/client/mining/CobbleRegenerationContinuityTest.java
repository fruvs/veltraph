package dev.veltraph.client.mining;

import dev.veltraph.client.config.*;
import dev.veltraph.client.mining.crafting.*;
import dev.veltraph.client.mining.module.MiningModule;
import dev.veltraph.client.service.*;
import dev.veltraph.client.testutil.FakeWorldService;
import dev.veltraph.client.world.BlockPos;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Actual controller/module paths over per-cell observations. No clock advancement or fabricated pickup. */
class CobbleRegenerationContinuityTest {
    static final BlockPos FIRST=new BlockPos(0,64,-1),SECOND=FIRST.up();
    static final class Services extends MiningControllerTest.Services {
        final List<Boolean> useEdges=new ArrayList<>();boolean use;
        final Map<BlockPos,String> cells=new HashMap<>();final Set<BlockPos> unloaded=new HashSet<>();
        final WorldService terrain=new FakeWorldService(){
            public boolean isLoaded(BlockPos p){return !unloaded.contains(p);}
            public String blockId(BlockPos p){return cells.getOrDefault(p,"minecraft:air");}
            public boolean isPassable(BlockPos p){return blockId(p).equals("minecraft:air");}
        };
        final FarmServices actualFarm=new FarmServices(clock,player,terrain,input,
                new dev.veltraph.client.testutil.FakeRotationService(),breaking,
                new dev.veltraph.client.service.stub.NoOpInventoryService());
        final Craft craft=new Craft();boolean supplyCraft;int toolChecks;Boolean attributedCapacity;
        Services(){cells.put(FIRST,"minecraft:cobblestone");}
        @Override public FarmServices farm(){return actualFarm;}
        @Override public boolean setUseHeld(Object caller,int activation,boolean held){if(use!=held){use=held;useEdges.add(held);}return true;}
        @Override public boolean ownedUseHeld(Object caller,int activation){return use;}
        @Override public String selectTool(BlockPos p,int reserve){toolChecks++;return super.selectTool(p,reserve);}
        @Override public boolean hasCobbleRegenerationCapacity(BlockPos removed){return attributedCapacity==null?hasCapacity(removed):attributedCapacity;}
        @Override public Optional<CobbleCraftStorePort> craftStorePort(){return supplyCraft?Optional.of(craft):Optional.empty();}
    }
    static final class Craft implements CobbleCraftStorePort {
        boolean materials,stationary=true,active,stopSupported=true;int starts,stopChecks;
        public String readiness(CobbleCraftStoreProfile p){return !materials?"Exact recipe materials absent":!stationary?"Player still moving":"";}
        public boolean materialsReadyToStop(CobbleCraftStoreProfile p){stopChecks++;return stopSupported&&materials;}
        public boolean start(CobbleCraftStoreProfile p){assertTrue(readiness(p).isEmpty(),"start must retain full readiness");starts++;active=true;return true;}
        public void tick(){} public void stop(){active=false;}
        public boolean active(){return active;}public boolean failed(){return false;}
        public boolean allowsScreen(String s){return false;}public String status(){return "fixture";}
        public int stored(){return 0;}
    }
    static MapConfigService config(){var cfg=new MapConfigService();cfg.set(MiningConfig.COBBLE_AUTO_WALK,true);cfg.set(MiningConfig.COBBLE_HOLD_RIGHT_CLICK,true);return cfg;}
    static MiningController started(Services s,MapConfigService cfg){var c=new MiningController(MiningMode.COBBLESTONE,s,cfg);c.start();c.tick();assertEquals(MiningController.State.BREAKING,c.state());assertTrue(s.use);return c;}
    static MiningModule module(Services s,MapConfigService cfg){s.supplyCraft=true;cfg.set(MiningConfig.COBBLE_CRAFT_STORE_ENABLED,true);cfg.set(MiningConfig.COBBLE_CRAFT_STORE_PROFILE,profile());var m=new MiningModule("cobblestone","Cobblestone",MiningMode.COBBLESTONE,s,cfg);m.setEnabled(true);m.onTick();assertTrue(s.input.forward);return m;}
    @Test void airRegenerationKeepsSafeDirectionalMotionAndNeverAttacksAir(){
        var s=new Services();var c=started(s,config());int releases=s.input.releaseAllCount,stops=s.stopped;
        s.cells.remove(FIRST);c.tick();assertEquals(1,c.removed());assertEquals(MiningController.State.READY,c.state());assertFalse(s.breaking.isBreaking());assertFalse(c.allowsTarget(FIRST));
        for(int i=0;i<40;i++){c.tick();assertTrue(s.input.forward);assertTrue(s.use);assertFalse(s.breaking.isBreaking());}
        assertEquals(releases,s.input.releaseAllCount);assertEquals(stops,s.stopped);assertEquals(List.of(true),s.useEdges);assertEquals(0,s.collected);assertEquals(1,c.removed());
        s.cells.put(FIRST,"minecraft:cobblestone");c.tick();assertEquals(MiningController.State.BREAKING,c.state());assertTrue(c.allowsTarget(FIRST));assertTrue(s.input.forward);assertEquals(List.of(true),s.useEdges);assertEquals(releases,s.input.releaseAllCount);
    }
    @Test void attributedAirCapacityCanContinueButCannotBypassRealTargetCapacity(){
        var s=new Services();var c=started(s,config());s.cells.remove(FIRST);s.capacity=false;s.attributedCapacity=true;int releases=s.input.releaseAllCount;
        c.tick();assertTrue(s.input.forward);assertTrue(s.use);assertFalse(s.breaking.isBreaking());assertEquals(releases,s.input.releaseAllCount);
        s.cells.put(FIRST,"minecraft:cobblestone");c.tick();assertFalse(s.breaking.isBreaking());assertFalse(s.use);assertFalse(s.input.forward);assertEquals(MiningController.State.PAUSED,c.state());
    }
    @Test void anotherAvailableCellIsValidatedAndMinedInTheSameRemovalCycle(){
        var s=new Services();s.cells.put(SECOND,"minecraft:cobblestone");var c=started(s,config());int releases=s.input.releaseAllCount,checks=s.toolChecks;
        s.cells.remove(FIRST);c.tick();assertEquals(1,c.removed());assertEquals(MiningController.State.BREAKING,c.state());assertEquals(SECOND,s.checked);assertTrue(s.toolChecks>checks);assertTrue(c.allowsTarget(SECOND));assertFalse(c.allowsTarget(FIRST));assertEquals(releases,s.input.releaseAllCount);assertEquals(List.of(true),s.useEdges);assertEquals(0,s.collected);
    }
    @Test void removalDoesNotStopTheModuleWhenExactMaterialsAreAbsent(){
        var s=new Services();s.cells.put(SECOND,"minecraft:cobblestone");var m=module(s,config());int releases=s.input.releaseAllCount;
        s.cells.remove(FIRST);m.onTick();assertEquals(1,m.controller().removed());assertEquals(MiningController.State.BREAKING,m.controller().state());assertTrue(s.input.forward);assertEquals(0,s.craft.starts);assertEquals(releases,s.input.releaseAllCount);assertEquals(List.of(true),s.useEdges);
    }
    @Test void actualInventoryBoundaryIsObservedBeforeRegeneratedMaterialCanStarveCrafting(){
        var s=new Services();s.cells.put(SECOND,"minecraft:cobblestone");var m=module(s,config());s.cells.remove(FIRST);s.craft.materials=true;
        m.onTick();assertTrue(s.craft.active());assertEquals(1,s.craft.starts);assertEquals(1,m.controller().removed());assertFalse(s.use);assertFalse(s.input.forward);assertFalse(s.breaking.isBreaking());assertFalse(m.controller().allowsTarget(SECOND));assertEquals(1,m.enableRuns());assertFalse(m.controller().scheduledBoundary());
    }
    @Test void lateNativeInventoryCapacityBlockStillAllowsExactInventoryTriggeredCrafting(){
        var s=new Services();var m=module(s,config());s.capacity=false;m.onTick();assertEquals(MiningController.State.PAUSED,m.controller().state());assertFalse(s.breaking.isBreaking());assertFalse(m.controller().scheduledBoundary());
        s.craft.materials=true;m.onTick();assertEquals(1,s.craft.starts);assertTrue(s.craft.active());assertFalse(m.controller().allowsTarget(FIRST));assertEquals(1,m.enableRuns());
    }
    @Test void movingInventoryReadyCraftStopsOnlyForItsExactLoanAndWaitsForFullReadiness(){
        var s=new Services();s.craft.stationary=false;var m=module(s,config());s.cells.remove(FIRST);s.craft.materials=true;m.onTick();
        assertFalse(s.input.forward);assertFalse(s.use);assertEquals(0,s.craft.starts);assertFalse(s.craft.active());assertFalse(m.controller().scheduledBoundary());assertTrue(m.scheduledRestReadiness().isPresent());
        s.cells.put(FIRST,"minecraft:cobblestone");for(int i=0;i<5;i++){m.onTick();assertEquals(0,s.craft.starts);assertFalse(s.breaking.isBreaking());assertFalse(s.input.forward);assertFalse(m.controller().allowsTarget(FIRST));}
        s.craft.stationary=true;m.onTick();assertEquals(1,s.craft.starts);assertTrue(s.craft.active());assertEquals(1,m.enableRuns());assertEquals(List.of(true,false),s.useEdges);
    }
    @Test void stoppingObservationDoesNotBypassLostMaterialsOrTheAdmissionDeadline(){
        var s=new Services();s.craft.stationary=false;var m=module(s,config());s.cells.remove(FIRST);s.craft.materials=true;m.onTick();s.craft.materials=false;s.craft.stationary=true;
        for(int i=0;i<40;i++){m.onTick();assertEquals(0,s.craft.starts);assertFalse(s.input.forward);}
        assertTrue(m.isEnabled());m.onTick();assertFalse(m.isEnabled());assertEquals(0,s.craft.starts);assertFalse(s.use);assertFalse(s.breaking.isBreaking());
    }
    @Test void manualStopAndConfigurationChangesCancelAStillPendingCraftLoan(){
        for(boolean manual:List.of(false,true)){
            var s=new Services();s.craft.stationary=false;var cfg=config();var m=module(s,cfg);s.cells.remove(FIRST);s.craft.materials=true;m.onTick();
            if(manual)m.setEnabled(false);else{cfg.set(MiningConfig.COBBLE_CRAFT_STORE_ENABLED,false);m.onTick();}
            s.craft.stationary=true;m.onTick();assertFalse(m.isEnabled());assertEquals(0,s.craft.starts);assertFalse(s.input.forward);assertFalse(s.use);
        }
    }
    @Test void pendingLoanCannotStartInAChangedWorldOrOpenScreenEvenIfMaterialsAndSpeedLookReady(){
        for(boolean world:List.of(false,true)){
            var s=new Services();s.craft.stationary=false;var m=module(s,config());s.cells.remove(FIRST);s.craft.materials=true;m.onTick();
            s.craft.stationary=true;if(world)s.identity="replacement world";else s.available=false;m.onTick();assertFalse(m.isEnabled());assertEquals(0,s.craft.starts);assertFalse(s.input.forward);assertFalse(s.use);
        }
    }
    @Test void anAdapterWithoutStopAdmissionCannotBorrowWhileItsFullReadinessRefuses(){
        var s=new Services();s.craft.stationary=false;s.craft.stopSupported=false;var m=module(s,config());s.cells.remove(FIRST);s.craft.materials=true;m.onTick();assertTrue(s.input.forward);assertTrue(s.use);assertEquals(0,s.craft.starts);assertTrue(m.controller().ownedSubflowReady());
    }
    @Test void airWaitReleasesForUnsafeCorridorWrongToolFullInventoryAndNonAirReplacement(){
        for(String issue:List.of("CORRIDOR","TOOL","CAPACITY","LAVA")){
            var s=new Services();var c=started(s,config());s.cells.remove(FIRST);switch(issue){case "CORRIDOR"->s.safe=false;case "TOOL"->s.heldTool=false;case "CAPACITY"->s.capacity=false;case "LAVA"->s.cells.put(FIRST,"minecraft:lava");}
            c.tick();assertEquals(1,c.removed());assertFalse(s.breaking.isBreaking(),issue);assertFalse(s.use,issue);assertFalse(s.input.forward,issue);assertFalse(c.allowsTarget(FIRST),issue);assertEquals(0,s.collected);
        }
    }
    @Test void noPastRemovalWitnessCannotStartWalkingAcrossAnEmptyFace(){
        var s=new Services();s.cells.clear();var c=new MiningController(MiningMode.COBBLESTONE,s,config());c.start();c.tick();assertFalse(s.input.forward);assertFalse(s.use);assertFalse(s.breaking.isBreaking());assertEquals(0,c.removed());
    }
    @Test void freshRegeneratedTargetRechecksToolCapacityAimAndHazard(){
        for(String issue:List.of("TOOL","CAPACITY","AIM","HAZARD")){
            var s=new Services();var c=started(s,config());s.cells.remove(FIRST);c.tick();s.cells.put(FIRST,"minecraft:cobblestone");switch(issue){case "TOOL"->s.tool="Worn tool";case "CAPACITY"->s.capacity=false;case "AIM"->s.aimed=false;case "HAZARD"->s.problem="Exposed lava";}
            c.tick();assertFalse(s.breaking.isBreaking(),issue);assertFalse(s.use,issue);assertFalse(s.input.forward,issue);assertFalse(c.allowsTarget(FIRST),issue);
        }
    }
    @Test void unloadedRemovalAndWorldScreenRevocationKeepCreditAndControlGuards(){
        for(String issue:List.of("UNLOADED","WORLD","SCREEN")){
            var s=new Services();var c=started(s,config());s.cells.remove(FIRST);switch(issue){case "UNLOADED"->s.unloaded.add(FIRST);case "WORLD"->s.identity="new world";case "SCREEN"->s.available=false;}
            assertFalse(c.observeCobbleInventoryBoundary());c.tick();assertEquals(0,c.removed());assertEquals(MiningController.State.PAUSED,c.state());assertFalse(s.use);assertFalse(s.input.forward);assertFalse(c.ownedSubflowReady());
        }
    }
    @Test void stopAndReversalStillReleaseOrChangeOnlyTheRequiredHolds(){
        var s=new Services();var c=started(s,config());s.cells.remove(FIRST);s.wall=true;c.tick();assertFalse(c.forward());assertTrue(s.input.back);assertFalse(s.use);s.wall=false;c.tick();assertTrue(s.input.back);
        c.stop();assertFalse(s.input.back);assertFalse(s.breaking.isBreaking());assertFalse(c.allowsTarget(FIRST));
    }
    @Test void nonCobbleModesKeepThirtyTicksOfDropCollectionAndRejectCobbleObservation(){
        for(var mode:List.of(MiningMode.MINE_DOWN,MiningMode.ECHO)){
            var s=new MiningControllerTest.Services();var cfg=new MapConfigService();cfg.set(MiningConfig.ECHO_ROUTE,"0,64,0>0,63,0;0,64,0>0,63,0");var c=new MiningController(mode,s,cfg);c.start();c.tick();s.id="minecraft:air";assertFalse(c.observeCobbleInventoryBoundary());c.tick();assertEquals(MiningController.State.SETTLING,c.state());assertEquals(1,s.collected);assertFalse(c.ownedSubflowReady());
            for(int i=0;i<28;i++)c.tick();assertEquals(MiningController.State.SETTLING,c.state());assertEquals(29,s.collected);c.tick();assertEquals(MiningController.State.READY,c.state());assertEquals(30,s.collected);assertEquals(1,c.removed());assertEquals(mode==MiningMode.ECHO?1:0,c.checkpoint());
        }
    }
    private static String profile(){
        var rows=new ArrayList<String>();for(int slot:new int[]{1,2,3,4,6,7,8,9})rows.add("{\"slot\":"+slot+",\"id\":\"minecraft:cobblestone\",\"name\":\"Cobblestone\",\"components\":\"default\",\"lore\":[],\"units\":1}");
        return "{\"adapter\":\"native_table\",\"craft\":{\"position\":\"-2,70,0\",\"block\":\"minecraft:crafting_table\",\"title\":\"Crafting\"},\"store\":{\"position\":\"2,70,0\",\"block\":\"minecraft:chest\",\"title\":\"Chest\"},\"inputs\":["+String.join(",",rows)+"],\"output\":{\"slot\":0,\"id\":\"minecraft:furnace\",\"name\":\"Furnace\",\"components\":\"default\",\"lore\":[],\"units\":1,\"delivery\":\"cursor\"},\"storeSlot\":0,\"sink\":9,\"batches\":2,\"maxConsumed\":16,\"maxOutput\":2,\"maxSpend\":0,\"timeoutTicks\":600,\"delayTicks\":2}";
    }
}
