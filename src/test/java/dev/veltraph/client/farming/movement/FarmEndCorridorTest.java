package dev.veltraph.client.farming.movement;

import dev.veltraph.client.config.MapConfigService;
import dev.veltraph.client.service.WorldService;
import dev.veltraph.client.testutil.FakePlayerService;
import dev.veltraph.client.world.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** End solve regressions derived from the captured lower paths, with no movement simulated. */
class FarmEndCorridorTest {
    private static class Corridor implements WorldService {
        final Map<BlockPos,String> blocks = new HashMap<>();
        final Set<BlockPos> unloaded = new HashSet<>();
        public boolean isLoaded(BlockPos p) { return !unloaded.contains(p); }
        public String blockId(BlockPos p) { return blocks.getOrDefault(p,p.y()<70 ? "minecraft:dirt" : "minecraft:air"); }
        public boolean isPassable(BlockPos p) { return Set.of("minecraft:air","minecraft:water","minecraft:wheat",
                "minecraft:carrots","minecraft:potatoes","minecraft:nether_wart").contains(blockId(p)); }
        public boolean isStandable(BlockPos p) { return !isPassable(p); }
        void bed(int x,int z,String crop) { blocks.put(new BlockPos(x,70,z),"minecraft:farmland"); blocks.put(new BlockPos(x,71,z),crop); }
    }
    private FarmGeometry geometry(Corridor world,Vec3 position) {
        var player = new FakePlayerService(); player.position = position;
        var result = new FarmGeometry(new MapConfigService(),player,world); result.setHeading(Direction.EAST); return result;
    }
    @Test void raisedNearestCropThenNextCropAfterRemovalForEveryGroundVariety() {
        for (String crop : List.of("minecraft:wheat","minecraft:carrots","minecraft:potatoes","minecraft:nether_wart")) {
            var world = new Corridor(); world.bed(51,0,crop); world.bed(52,0,crop);
            var g = geometry(world,new Vec3(50.5,70,0.5));
            assertEquals(51.5,g.targetVec().x()); assertTrue(g.targetVec().y()>71.3);
            assertTrue(g.harvestTargetAvailable());
            world.blocks.remove(new BlockPos(51,71,0));
            assertEquals(52.5,g.targetVec().x(),"harvested raised support does not occlude the next crop");
            world.blocks.remove(new BlockPos(52,71,0)); assertFalse(g.harvestTargetAvailable());
            assertEquals("minecraft:farmland",world.blockId(new BlockPos(51,70,0)));
        }
    }
    @Test void sevenBlockCorridorSkipsWaterAndStopsAtActualReturnLane() {
        var world = new Corridor();
        for (int x=51;x<=56;x++) world.bed(x,0,"minecraft:wheat");
        world.blocks.put(new BlockPos(55,70,0),"minecraft:water"); world.bed(58,0,"minecraft:wheat");
        var g = geometry(world,new Vec3(50.5,70,-0.5));
        assertEquals(new Vec3(57.5,70,-0.5),g.rowTransitionTarget(TurnDirection.LEFT));
        // The lane remains inferable from its crop support after all visible crops were harvested.
        world.blocks.remove(new BlockPos(58,71,0));
        assertEquals(new Vec3(57.5,70,-0.5),g.rowTransitionTarget(TurnDirection.LEFT));
    }
    @Test void blockedOrUnloadedIntermediateCorridorCannotProduceDestination() {
        var world = new Corridor();
        for (int x=51;x<=56;x++) world.bed(x,0,"minecraft:wheat"); world.bed(58,0,"minecraft:wheat");
        var g = geometry(world,new Vec3(50.5,70,-0.5));
        world.unloaded.add(new BlockPos(54,70,-1)); assertNull(g.rowTransitionTarget(TurnDirection.LEFT));
        world.unloaded.clear(); world.blocks.put(new BlockPos(54,71,-1),"minecraft:stone");
        assertNull(g.rowTransitionTarget(TurnDirection.LEFT));
    }
    @Test void cropOwnerSelectionDoesNotAttackTheOtherGroundVarieties() {
        var world = new Corridor(); world.bed(51,0,"minecraft:wheat"); world.bed(52,0,"minecraft:nether_wart");
        var g = geometry(world,new Vec3(50.5,70,0.5));
        g.setSelectedCrop("minecraft:nether_wart"); assertFalse(g.harvestTargetAvailable(),"a different front crop occludes the selected crop behind it");
        g.setSelectedCrop("minecraft:wheat"); assertTrue(g.harvestTargetAvailable());
    }
    @Test void thinAuthoredDropApertureReturnsOnlyAValidatedCenteredNavigatorGoal(){
        var world=new Corridor();world.blocks.put(new BlockPos(7,73,82),"minecraft:soul_sand");
        var player=new FakePlayerService();player.position=new Vec3(6.5,76.875,83.538);
        var g=new FarmGeometry(new MapConfigService(),player,world);g.setHeading(Direction.NORTH);
        g.setSelectedCrop("minecraft:nether_wart");
        class Aperture implements FarmCollisionProbe {
            boolean loaded=true;double floor=73.875;
            public boolean loaded(Vec3 p){return loaded;}
            public boolean clear(Vec3 p,boolean water){return p.y()<76||Math.abs(p.z()-83.5)<.012;}
            public OptionalDouble supportY(double x,double z,double highest,int bound){return OptionalDouble.of(floor);}
            public boolean water(Vec3 p){return false;}
        }
        var probe=new Aperture();g.setCollisionProbe(probe);
        assertEquals(new Vec3(7.4,73.875,83.5),g.safeDropTarget(TurnDirection.RIGHT));
        assertEquals(new Vec3(6.5,76.875,83.538),player.position,"geometry never moves the player");
        probe.loaded=false;assertNull(g.safeDropTarget(TurnDirection.RIGHT));
        probe.loaded=true;probe.floor=70;assertNull(g.safeDropTarget(TurnDirection.RIGHT),"centering cannot bypass the dry3drop bound");
    }

}
