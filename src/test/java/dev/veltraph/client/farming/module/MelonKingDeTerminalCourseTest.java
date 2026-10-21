package dev.veltraph.client.farming.module;

import dev.veltraph.client.config.*;
import dev.veltraph.client.farming.domain.FarmLayoutRegistry;
import dev.veltraph.client.farming.movement.planner.MelonKingDeCornerPlanner;
import dev.veltraph.client.farming.movement.planner.MelonKingDeWaterPlanner;
import dev.veltraph.client.farming.movement.TurnDirection;
import dev.veltraph.client.service.*;
import dev.veltraph.client.service.stub.*;
import dev.veltraph.client.testutil.*;
import dev.veltraph.client.world.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Declared pure counterparts of the closed AW final four and physical initial bank contact. */
class MelonKingDeTerminalCourseTest {
    private final Map<BlockPos,String> cells=new HashMap<>();private final Set<BlockPos> unloaded=new HashSet<>();
    private final FakePlayerService actor=new FakePlayerService();private final FakeRotationService rotation=new FakeRotationService(0,-59);
    private final RecordingInputService input=new RecordingInputService();private final MapConfigService values=new MapConfigService();private final ConfigService config=new CallbackConfig();
    private final PumpkinMelonFarmModule module;private Runnable afterBody,afterConfig,afterGround,afterForward,afterBlock;private boolean support=true,unrelatedProbeObstruction;private int backCalls;
    private final List<BlockPos> actualFinalFour=List.of(new BlockPos(1,73,39),new BlockPos(1,75,40),new BlockPos(2,73,39),new BlockPos(2,75,40));
    private final class CallbackConfig implements ConfigService {
        public <T>T get(ConfigKey<T> key){T result=values.get(key);if(key.equals(FarmingConfig.PUMPKIN_FARM_DESIGN)&&afterConfig!=null){var call=afterConfig;afterConfig=null;call.run();}return result;}
        public <T>void set(ConfigKey<T> key,T value){values.set(key,value);}public boolean isSet(ConfigKey<?> key){return values.isSet(key);}public void reset(ConfigKey<?> key){values.reset(key);}
    }
    private final class CallbackPlayer implements PlayerService {
        public boolean isAvailable(){return actor.isAvailable();}public Vec3 position(){return actor.position();}public BlockPos blockPos(){return actor.blockPos();}
        public float yaw(){return actor.yaw();}public float pitch(){return actor.pitch();}
        public boolean onGround(){boolean value=actor.onGround();if(afterGround!=null){var call=afterGround;afterGround=null;call.run();}return value;}
        public double horizontalSpeed(){return actor.horizontalSpeed();}public Direction facing(){return actor.facing();}
    }
    private final class CallbackInput implements InputService {
        public void setForwardHeld(boolean v){input.setForwardHeld(v);if(afterForward!=null){var call=afterForward;afterForward=null;call.run();}}
        public void setBackHeld(boolean v){backCalls++;input.setBackHeld(v);}public void setLeftHeld(boolean v){input.setLeftHeld(v);}public void setRightHeld(boolean v){input.setRightHeld(v);}
        public void setJumpHeld(boolean v){input.setJumpHeld(v);}public void setSneakHeld(boolean v){input.setSneakHeld(v);}public void setSprintHeld(boolean v){input.setSprintHeld(v);}
        public void releaseAllMovement(){input.releaseAllMovement();}
    }
    private final class ShapeWorld implements WorldService {
        public boolean isLoaded(BlockPos p){return !unloaded.contains(p);}public String blockId(BlockPos p){String result=cells.getOrDefault(p,"minecraft:air");if(afterBlock!=null){var call=afterBlock;afterBlock=null;call.run();}return result;}
        public boolean isPassable(BlockPos p){return Set.of("minecraft:air","minecraft:water","minecraft:pumpkin_stem").contains(blockId(p));}
        public boolean isStandable(BlockPos p){return !isPassable(p);}
        public boolean bodyClear(Vec3 feet,double width,double height){
            boolean clear=true;
            for(var entry:cells.entrySet()) {
                var p=entry.getKey();String id=entry.getValue();if(Set.of("minecraft:air","minecraft:water","minecraft:pumpkin_stem").contains(id))continue;
                double top=p.y()+(id.equals("minecraft:soul_sand")?.875:id.equals("minecraft:farmland")?.9375:1);
                if(feet.x()+width-1e-7>p.x()&&feet.x()-width+1e-7<p.x()+1
                        &&feet.z()+width-1e-7>p.z()&&feet.z()-width+1e-7<p.z()+1
                        &&feet.y()+height>p.y()&&feet.y()+.001<top)clear=false;
            }
            if(unrelatedProbeObstruction&&feet.z()>actor.position.z()+.00005)clear=false;
            if(afterBody!=null){var call=afterBody;afterBody=null;call.run();}return clear;
        }
        public boolean hasGroundSupport(Vec3 feet,double width){
            boolean floor=false;for(int x=(int)Math.floor(feet.x()-width);x<=(int)Math.floor(feet.x()+width-1e-7);x++)
                for(int z=(int)Math.floor(feet.z()-width);z<=(int)Math.floor(feet.z()+width-1e-7);z++)
                    floor|=blockId(new BlockPos(x,69,z)).equals("minecraft:soul_sand");
            return support&&floor&&Math.abs(feet.y()-69.875)<.025;
        }
    }
    MelonKingDeTerminalCourseTest(){
        actor.position=new Vec3(.5,69.875,37.69999998807907);
        for(int x=0;x<=9;x++){cells.put(new BlockPos(x,69,37),"minecraft:soul_sand");cells.put(new BlockPos(x,70,37),"minecraft:water");}
        for(int tier=0;tier<3;tier++)for(int x=0;x<=2;x++){
            var crop=new BlockPos(x,71+tier*2,38+tier);cells.put(crop.down(),"minecraft:dirt");
            cells.put(crop.offset(Direction.SOUTH),"minecraft:pumpkin_stem");cells.put(crop.offset(Direction.SOUTH).down(),"minecraft:farmland");
        }
        for(var crop:actualFinalFour)cells.put(crop,"minecraft:pumpkin");
        config.set(FarmingConfig.REWARP_REQUIRED,false);config.set(FarmingConfig.ROTATION_USE_MOUSE_GCD,false);
        config.set(FarmingConfig.PUMPKIN_FARM_DESIGN,PumpkinFarmDesign.MELONKINGDE_WATER);config.set(FarmingConfig.PUMPKIN_MELONKING_START_SIDE,LaneSide.RIGHT);
        module=new PumpkinMelonFarmModule(new FarmServices(new FakeClock(),new CallbackPlayer(),new ShapeWorld(),new CallbackInput(),rotation,new NoOpBlockBreakingService(),new NoOpInventoryService()),config,FarmLayoutRegistry.createDefault());module.setEnabled(true);
    }
    private MelonKingDeCornerPlanner corner() throws Exception{var f=PumpkinMelonFarmModule.class.getDeclaredField("cornerPlanner");f.setAccessible(true);return (MelonKingDeCornerPlanner)f.get(module);}
    private void nativeEntry(BlockPos water) throws Exception{var f=PumpkinMelonFarmModule.class.getDeclaredField("nativeEntryWater");f.setAccessible(true);f.set(module,water);}
    private Object nativeEntry() throws Exception{var f=PumpkinMelonFarmModule.class.getDeclaredField("nativeEntryWater");f.setAccessible(true);return f.get(module);}
    private void assertHeldView(){assertEquals(0,rotation.yaw,.0001);assertEquals(-59,rotation.pitch,.0001);assertFalse(input.jump||input.sneak||input.sprint);}
    @Test void finalFourTriggerAContinuousSupportedInwardPassWithoutCropFocus() throws Exception {
        module.onTick();assertTrue(corner().courseStrip().isPresent());assertFalse(module.cycleEndReady());
        assertTrue(input.left);assertFalse(input.right);assertHeldView();assertTrue(corner().focusAngles().isEmpty());
        assertEquals(4,actualFinalFour.stream().filter(p->cells.containsKey(p)).count());
    }
    @Test void terminalTurnsUseThreeColumnBoundsRatherThanIndividualFruitPositions() {
        module.onTick();actor.position=new Vec3(2.7,69.875,37.69999998807907);module.onTick();assertTrue(input.right);assertFalse(input.left);assertHeldView();
        actor.position=new Vec3(.35,69.875,37.69999998807907);module.onTick();assertTrue(input.left);assertFalse(input.right);assertHeldView();
    }
    @Test void actualNineClearanceRemainsRequiredAndSupportedEndCanHold() {
        module.onTick();for(var crop:actualFinalFour)cells.remove(crop);actor.position=new Vec3(1.5,69.875,37.69999998807907);module.onTick();
        assertTrue(module.cycleEndReady());assertFalse(input.forward||input.back||input.left||input.right);assertHeldView();
        cells.put(actualFinalFour.getFirst(),"minecraft:pumpkin");module.onTick();assertFalse(module.cycleEndReady());
    }
    @Test void unknownOrChangedBedCannotActivateTerminalMotion() throws Exception {
        unloaded.add(new BlockPos(2,75,40));module.onTick();assertTrue(corner().courseStrip().isEmpty());assertFalse(module.cycleEndReady());
        unloaded.clear();cells.put(new BlockPos(2,74,40),"minecraft:stone");module.onTick();assertTrue(corner().courseStrip().isEmpty());assertFalse(module.cycleEndReady());
    }
    @Test void cappedExitOrFollowingSourceWaterKeepsOrdinaryCoursePolicy() throws Exception {
        cells.put(new BlockPos(-1,70,37),"minecraft:polished_andesite");module.onTick();assertTrue(corner().courseStrip().isEmpty());
        cells.remove(new BlockPos(-1,70,37));cells.put(new BlockPos(1,69,41),"minecraft:soul_sand");cells.put(new BlockPos(1,70,41),"minecraft:water");
        module.onTick();assertTrue(corner().courseStrip().isEmpty());assertHeldView();
    }
    @Test void unsupportedOrReplacedActivationCannotPublishTerminalDirection() {
        support=false;module.onTick();assertFalse(input.left||input.right);
        support=true;afterBody=()->{module.setEnabled(false);module.setEnabled(true);};module.onTick();assertFalse(input.left||input.right);assertHeldView();
    }
    private void firstBank() throws Exception {
        cells.clear();for(int x=0;x<=9;x++){cells.put(new BlockPos(x,69,0),"minecraft:soul_sand");cells.put(new BlockPos(x,70,0),"minecraft:water");cells.put(new BlockPos(x,70,1),"minecraft:dirt");}
        actor.position=new Vec3(2.5,69.875,.6689423876783609);nativeEntry(new BlockPos(2,70,0));
    }
    @Test void firstWalkingHeightDoesNotReleaseEntryBeforePhysicalSoilBankContact() throws Exception {
        firstBank();module.onTick();assertNotNull(nativeEntry());assertTrue(input.forward);assertHeldView();
        actor.position=new Vec3(2.5,69.875,.699999988079071);module.onTick();assertNull(nativeEntry());assertHeldView();
    }
    @Test void unloadedOrUnsupportedEntryBankDoesNotReceiveForwardPressure() throws Exception {
        firstBank();unloaded.add(new BlockPos(2,70,1));module.onTick();assertFalse(input.forward||input.left||input.right);assertNotNull(nativeEntry());
        unloaded.clear();support=false;module.onTick();assertFalse(input.forward||input.left||input.right);assertHeldView();
    }
    private void replacement(){module.setEnabled(false);module.setEnabled(true);}
    @Test void originalInvocationPrecedesConfigurationAndGroundProviders() {
        afterConfig=this::replacement;module.onTick();assertFalse(input.left||input.right);
        module.onTick();assertTrue(input.left);
        afterGround=this::replacement;module.onTick();assertFalse(input.left||input.right);
        module.onTick();assertTrue(input.left);
    }
    @Test void recursiveGeometryCannotCommitTheOldPassiveEndAndLaterFreshTickSucceeds() throws Exception {
        afterBody=module::onTick;module.onTick();assertTrue(corner().courseStrip().isEmpty());assertFalse(input.left||input.right);
        module.onTick();assertTrue(corner().courseStrip().isPresent());assertTrue(input.left);
    }
    @Test void firstSetterRestartStopsOldTailAndLeavesReplacementMetadata() throws Exception {
        afterForward=this::replacement;backCalls=0;module.onTick();assertEquals(0,backCalls);
        assertFalse(input.left||input.right);assertTrue(corner().courseStrip().isEmpty());
        var f=PumpkinMelonFarmModule.class.getDeclaredField("terminalTicks");f.setAccessible(true);assertEquals(0,f.getInt(module));
        module.onTick();assertTrue(input.left);
    }
    @Test void passiveObservationGuardRefusesOldCommitsAfterCallerRetirement() throws Exception {
        var live=new java.util.concurrent.atomic.AtomicBoolean(true);var planner=corner();
        afterBlock=()->{planner.reset();live.set(false);};
        planner.observeCourse(new BlockPos(0,70,37),Direction.SOUTH,live::get);
        assertTrue(planner.courseStrip().isEmpty());assertFalse(planner.endReady());
        live.set(true);planner.observeCourse(new BlockPos(0,70,37),Direction.SOUTH,live::get);assertTrue(planner.courseStrip().isPresent());
    }
    @Test void unrelatedShiftedObstructionIsNotSpecificSoilBankContact() throws Exception {
        firstBank();unrelatedProbeObstruction=true;
        var contact=PumpkinMelonFarmModule.class.getDeclaredMethod("nativeEntryBankContact",Vec3.class);contact.setAccessible(true);
        assertFalse((Boolean)contact.invoke(module,actor.position));assertNotNull(nativeEntry());
        unrelatedProbeObstruction=false;actor.position=new Vec3(2.5,69.875,.699999988079071);
        assertTrue((Boolean)contact.invoke(module,actor.position));
    }

    @Test void firstNegativeKeyRestartStopsTheRemainingClearedHoldReleases() {
        for(var crop:actualFinalFour)cells.remove(crop);actor.position=new Vec3(1.5,69.875,37.69999998807907);
        afterForward=this::replacement;backCalls=0;module.onTick();assertEquals(0,backCalls,"Old negative release tail must stop after its first callback");
        assertFalse(input.left||input.right);module.onTick();assertTrue(module.cycleEndReady());
    }
    private final class RetiringWorld implements MelonKingDeWaterPlanner.World {
        boolean live=true,retireForward,retireTransfer;int reverseCalls,sprintCalls,cropCalls;
        public boolean currentForTick(){return live;}
        public boolean supportedDesignPosition(){return true;}public boolean sideLoaded(TurnDirection side){return true;}
        public boolean sideObstructed(TurnDirection side){return false;}
        public boolean transferClear(){if(retireTransfer)live=false;return true;}
        public boolean forwardWhileFarming(){if(retireForward)live=false;return true;}
        public boolean reverseBeforeTransfer(){reverseCalls++;return true;}
        public boolean sprintWhileFarming(){sprintCalls++;return true;}
        public boolean cropTargeted(){cropCalls++;return true;}
        public int nextLaneDistance(TurnDirection side){return 3;}
    }
    private MelonKingDeWaterPlanner isolatedPlanner(RetiringWorld world) {
        return new MelonKingDeWaterPlanner(new FarmServices(new FakeClock(),actor,new FakeWorldService(),input,
                new NoOpRotationService(),new NoOpBlockBreakingService(),new NoOpInventoryService()),world);
    }
    @Test void retiringFirstFarmingProviderDoesNotQuerySprintOrCrop() {
        var world=new RetiringWorld();var planner=isolatedPlanner(world);planner.start(TurnDirection.LEFT);world.retireForward=true;planner.tick();
        assertEquals(0,world.sprintCalls);assertEquals(0,world.cropCalls);assertFalse(input.forward||input.left||input.right);
    }
    @Test void retiringTransferProviderDoesNotQueryLaterLazyProviders() throws Exception {
        var world=new RetiringWorld();var planner=isolatedPlanner(world);planner.start(TurnDirection.LEFT);
        var state=MelonKingDeWaterPlanner.class.getDeclaredField("state");state.setAccessible(true);state.set(planner,MelonKingDeWaterPlanner.State.TRANSFERRING);
        var row=MelonKingDeWaterPlanner.class.getDeclaredField("targetRow");row.setAccessible(true);row.setDouble(planner,100);
        world.retireTransfer=true;planner.tick();assertEquals(0,world.reverseCalls);assertEquals(0,world.sprintCalls);assertEquals(0,world.cropCalls);
        assertFalse(input.forward||input.left||input.right);
    }

}
