package dev.veltraph.client.farming.module;

import dev.veltraph.client.config.*;
import dev.veltraph.client.farming.domain.FarmLayoutRegistry;
import dev.veltraph.client.farming.movement.planner.*;
import dev.veltraph.client.service.*;
import dev.veltraph.client.service.stub.NoOpInventoryService;
import dev.veltraph.client.testutil.*;
import dev.veltraph.client.world.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Fixed creator view, actual contact pressure, and passive end evidence are separate concerns. */
class MelonKingDeHeldCourseTest {
    private final Map<BlockPos,String> blocks=new HashMap<>();
    private final Set<BlockPos> unloaded=new HashSet<>();
    private final FakePlayerService actor=new FakePlayerService();
    private final FakeRotationService rotation=new FakeRotationService(0,-59);
    private final RecordingInputService input=new RecordingInputService();
    private final MapConfigService config=new MapConfigService();
    private final BlockPos fruit=new BlockPos(30,71,11);
    private final Breaking breaking=new Breaking();
    private final PumpkinMelonFarmModule module;
    private final class Breaking implements BlockBreakingService {
        boolean held;BlockPos target=fruit;
        public void setBreaking(boolean value){held=value;}public boolean isBreaking(){return held;}
        public String targetBlockId(){return blocks.getOrDefault(target,"minecraft:air");}
        public Optional<BlockPos> targetBlockPos(){return Optional.ofNullable(target);}
    }
    MelonKingDeHeldCourseTest() {
        actor.position=new Vec3(31.3,69.875,10.5);
        for(int x=30;x<=40;x++)blocks.put(new BlockPos(x,69,10),"minecraft:soul_sand");
        blocks.put(new BlockPos(31,70,10),"minecraft:water");
        blocks.put(new BlockPos(30,70,10),"minecraft:polished_andesite");
        for(int tier=0;tier<3;tier++)for(int along=0;along<3;along++) {
            BlockPos crop=new BlockPos(30+along,71+tier*2,11+tier);
            blocks.put(crop.down(),"minecraft:dirt");
            blocks.put(crop.offset(Direction.SOUTH),"minecraft:pumpkin_stem");
            blocks.put(crop.offset(Direction.SOUTH).down(),"minecraft:farmland");
        }
        blocks.put(fruit,"minecraft:pumpkin");
        WorldService world=new WorldService(){
            public boolean isLoaded(BlockPos p){return !unloaded.contains(p);}
            public String blockId(BlockPos p){return blocks.getOrDefault(p,"minecraft:air");}
            public boolean isPassable(BlockPos p){String id=blockId(p);return id.equals("minecraft:air")||id.equals("minecraft:water")||id.endsWith("_stem");}
            public boolean isStandable(BlockPos p){return !isPassable(p);}
            public boolean bodyClear(Vec3 feet,double width,double height){return true;}
            public boolean hasGroundSupport(Vec3 feet,double width){return true;}
            public Optional<Vec3> interactionPoint(BlockPos p,Vec3 eye,double range){return Optional.of(new Vec3(p.x()+.5,p.y()+.5,p.z()+.5));}
        };
        config.set(FarmingConfig.REWARP_REQUIRED,false);
        config.set(FarmingConfig.PUMPKIN_FARM_DESIGN,PumpkinFarmDesign.MELONKINGDE_WATER);
        config.set(FarmingConfig.ROTATION_USE_MOUSE_GCD,false);
        config.set(FarmingConfig.ROTATION_JITTER,true);
        module=new PumpkinMelonFarmModule(new FarmServices(new FakeClock(),actor,world,input,rotation,breaking,new NoOpInventoryService()),config,FarmLayoutRegistry.createDefault());
    }
    private MelonKingDeCornerPlanner corner() throws Exception {
        var field=PumpkinMelonFarmModule.class.getDeclaredField("cornerPlanner");field.setAccessible(true);
        return (MelonKingDeCornerPlanner)field.get(module);
    }
    private MelonKingDeWaterPlanner.World course() throws Exception {
        var field=PumpkinMelonFarmModule.class.getDeclaredField("waterPlanner");field.setAccessible(true);
        var planner=field.get(module);var world=MelonKingDeWaterPlanner.class.getDeclaredField("world");world.setAccessible(true);
        return (MelonKingDeWaterPlanner.World)world.get(planner);
    }
    @Test void startupCardinalComesFromPlayerRatherThanHardCodedSouth() {
        for(float yaw:new float[]{-85,85,175,5}) {
            if(module.isEnabled())module.setEnabled(false);
            rotation.yaw=yaw;rotation.pitch=-59;module.setEnabled(true);
            assertEquals(Direction.fromYaw(yaw),module.heading());
        }
    }
    @Test void remainingEndFruitDoesNotRotateOrInterruptOrdinaryLaneMovement() throws Exception {
        module.setEnabled(true);
        for(int tick=0;tick<20;tick++) {
            module.onTick();
            assertEquals(0,rotation.yaw,.0001);assertEquals(-59,rotation.pitch,.0001);
            assertFalse(input.jump||input.sneak);
            assertFalse(corner().active(),"End evidence must not take over ordinary lane movement");
            assertFalse(module.cycleEndReady(),"A remaining original end crop cannot be counted cleared");
        }
        assertTrue(input.left||input.right,"No fruit-by-fruit stop while traversing the working lane");
    }
    @Test void cropHeightAndCurrentRayChangesKeepTheStartupView() {
        module.setEnabled(true);
        for(int height:new int[]{71,73,75,71}) {
            breaking.target=new BlockPos(31,height,11);blocks.put(breaking.target,"minecraft:pumpkin");
            module.onTick();assertEquals(0,rotation.yaw,.0001);assertEquals(-59,rotation.pitch,.0001);
        }
    }
    @Test void contactPressureIsConditionalAndRelativeToEveryCardinal() throws Exception {
        for(float yaw:new float[]{0,90,180,-90}) {
            if(module.isEnabled())module.setEnabled(false);
            rotation.yaw=yaw;rotation.pitch=-59;module.setEnabled(true);
            Direction h=module.heading();
            actor.position=new Vec3(h.dx()*10.5,69.875,h.dz()*10.5);
            BlockPos front=new Vec3(actor.position.x(),actor.position.y()+.15,actor.position.z()).toBlockPos().offset(h);
            blocks.put(front,"minecraft:dirt");assertTrue(course().forwardWhileFarming());
            blocks.remove(front);assertFalse(course().forwardWhileFarming(),"Open lane must not receive crop-bank W pressure");
            blocks.put(front,"minecraft:dirt");unloaded.add(front);assertFalse(course().forwardWhileFarming());unloaded.clear();
            actor.position=new Vec3(h.dx()*10.8,69.875,h.dz()*10.8);
            front=new Vec3(actor.position.x(),actor.position.y()+.15,actor.position.z()).toBlockPos().offset(h);
            blocks.put(front,"minecraft:dirt");assertFalse(course().forwardWhileFarming(),"Settled fractional contact needs lateral keys only");
        }
    }
    @Test void negativeCoordinateContactUsesTheSameHeldAxisWindow() throws Exception {
        rotation.yaw=0;module.setEnabled(true);actor.position=new Vec3(31.3,69.875,-10.5);
        BlockPos front=new BlockPos(31,70,-10);blocks.put(front,"minecraft:dirt");
        assertTrue(course().forwardWhileFarming());actor.position=new Vec3(31.3,69.875,-10.2);
        assertFalse(course().forwardWhileFarming());
    }
    @Test void passiveEndRequiresActualClearanceAndRejectsRegrowth() throws Exception {
        module.setEnabled(true);var end=corner();var water=new BlockPos(31,70,10);
        end.observeCourse(water,Direction.SOUTH);assertFalse(end.endReady());assertFalse(end.active());
        blocks.put(fruit.down(),"minecraft:stone");end.observeCourse(water,Direction.SOUTH);
        assertFalse(end.endReady(),"Present fruit with broken soil is not a harvest");
        blocks.put(fruit.down(),"minecraft:dirt");blocks.remove(fruit.offset(Direction.SOUTH));
        end.observeCourse(water,Direction.SOUTH);assertFalse(end.endReady(),"Present fruit without its stem is not a harvest");
        blocks.put(fruit.offset(Direction.SOUTH),"minecraft:pumpkin_stem");
        blocks.remove(fruit);end.observeCourse(water,Direction.SOUTH);assertTrue(end.endReady());
        blocks.put(fruit.down(),"minecraft:stone");end.observeCourse(water,Direction.SOUTH);
        assertFalse(end.endReady(),"AIR on a destroyed planting bed cannot qualify clearance");
        blocks.put(fruit.down(),"minecraft:dirt");blocks.put(fruit,"minecraft:dirt");
        end.observeCourse(water,Direction.SOUTH);assertFalse(end.endReady(),"A structural replacement is not a cleared fruit cell");
        blocks.remove(fruit);end.observeCourse(water,Direction.SOUTH);assertTrue(end.endReady());
        for(String present:new String[]{"minecraft:pumpkin","minecraft:melon","minecraft:carved_pumpkin"}) {
            blocks.put(fruit,present);end.observeCourse(water,Direction.SOUTH);assertFalse(end.endReady());
        }
        assertFalse(input.forward||input.back||input.left||input.right||input.jump||breaking.held,"Passive observation may not emit inputs");
    }
    @Test void unknownEndCellCannotReusePriorClearance() throws Exception {
        module.setEnabled(true);var end=corner();var water=new BlockPos(31,70,10);
        blocks.remove(fruit);end.observeCourse(water,Direction.SOUTH);assertTrue(end.endReady());
        unloaded.add(new BlockPos(32,75,13));end.observeCourse(water,Direction.SOUTH);assertFalse(end.endReady());
        unloaded.clear();end.observeCourse(null,Direction.SOUTH);assertFalse(end.endReady());
        end.observeCourse(new BlockPos(32,70,10),Direction.SOUTH);assertFalse(end.endReady(),"Foreign water cell cannot reuse the observed bank");
        blocks.remove(water);end.observeCourse(water,Direction.SOUTH);assertFalse(end.endReady(),"Missing source water cannot qualify END");
        blocks.put(water,"minecraft:water");actor.position=new Vec3(31.3,70.875,10.5);
        end.observeCourse(water,Direction.SOUTH);assertFalse(end.endReady(),"Different feet height cannot qualify a walking course END");
        for(Vec3 invalid:List.of(new Vec3(Double.NaN,69.875,10.5),new Vec3(31.3,Double.NaN,10.5),
                new Vec3(31.3,69.875,Double.NaN),new Vec3(Double.POSITIVE_INFINITY,69.875,10.5),
                new Vec3(31.3,Double.NEGATIVE_INFINITY,10.5),new Vec3(31.3,69.875,Double.POSITIVE_INFINITY))) {
            actor.position=invalid;end.observeCourse(water,Direction.SOUTH);
            assertFalse(end.endReady(),"Nonfinite coordinates cannot qualify a walking-course END");
        }
    }
    @Test void airborneObservationCannotKeepAnOldEndReady() throws Exception {
        module.setEnabled(true);blocks.remove(fruit);corner().observeCourse(new BlockPos(31,70,10),Direction.SOUTH);assertTrue(module.cycleEndReady());
        actor.onGround=false;module.onTick();assertFalse(module.cycleEndReady());assertFalse(input.jump);
    }
    @Test void saigoRetainsItsExistingForwardPressureDefault() throws Exception {
        config.set(FarmingConfig.PUMPKIN_FARM_DESIGN,PumpkinFarmDesign.SAIGO_CURRENT);module.setEnabled(true);
        assertTrue(course().forwardWhileFarming());
    }
}
