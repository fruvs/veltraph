package dev.veltraph.client.farming.movement;
import dev.veltraph.client.service.WorldService;
import dev.veltraph.client.world.BlockPos;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class GourdCropTargetTest {
    private static final BlockPos FRUIT=new BlockPos(0,71,-141),STEM=FRUIT.add(1,0,0);
    private WorldService world(Map<BlockPos,String> blocks,Set<BlockPos> unloaded){return new WorldService(){
        public boolean isLoaded(BlockPos p){return !unloaded.contains(p);}
        public boolean isPassable(BlockPos p){return !blocks.containsKey(p);}
        public boolean isStandable(BlockPos p){return blocks.containsKey(p);}
        public String blockId(BlockPos p){return blocks.getOrDefault(p,"minecraft:air");}
    };}
    private Map<BlockPos,String> bed(String fruit){return Map.of(FRUIT,fruit,FRUIT.down(),"minecraft:dirt",STEM,fruit.equals("minecraft:melon")?"minecraft:melon_stem":"minecraft:pumpkin_stem",STEM.down(),"minecraft:farmland");}
    @Test void acceptsRecordedCarvedCropBedAndVanillaGourds(){
        for(String fruit:List.of("minecraft:carved_pumpkin","minecraft:pumpkin","minecraft:melon"))assertTrue(GourdCropTarget.harvestable(world(bed(fruit),Set.of()),FRUIT));
    }
    @Test void decorativeCarvedHeadWithoutSoilAndStemIsRejected(){
        assertFalse(GourdCropTarget.harvestable(world(Map.of(FRUIT,"minecraft:carved_pumpkin",FRUIT.down(),"minecraft:stone"),Set.of()),FRUIT));
        assertFalse(GourdCropTarget.harvestable(world(Map.of(FRUIT,"minecraft:carved_pumpkin",FRUIT.down(),"minecraft:dirt"),Set.of()),FRUIT));
    }
    @Test void mismatchedStemDoesNotMakeDecorativeFruitHarvestable(){
        var blocks=new HashMap<>(bed("minecraft:carved_pumpkin"));
        blocks.put(STEM,"minecraft:melon_stem");
        assertFalse(GourdCropTarget.harvestable(world(blocks,Set.of()),FRUIT));
    }
    @Test void incompleteLoadedBedCannotAuthorizeAttack(){
        for(BlockPos p:List.of(FRUIT,FRUIT.down(),STEM,STEM.down()))assertFalse(GourdCropTarget.harvestable(world(bed("minecraft:carved_pumpkin"),Set.of(p)),FRUIT));
        assertFalse(GourdCropTarget.harvestable(world(bed("minecraft:wheat"),Set.of()),FRUIT));
    }

    @Test void clearedBedCanIdentifyTraversalWithoutAuthorizingAnAirAttack(){
        var blocks=new HashMap<>(bed("minecraft:carved_pumpkin"));blocks.remove(FRUIT);
        assertTrue(GourdCropTarget.cropBed(world(blocks,Set.of()),FRUIT,"pumpkin"));
        assertFalse(GourdCropTarget.harvestable(world(blocks,Set.of()),FRUIT));
        assertFalse(GourdCropTarget.cropBed(world(blocks,Set.of(STEM.down())),FRUIT,"pumpkin"));
        assertFalse(GourdCropTarget.cropBed(world(blocks,Set.of()),FRUIT,"melon"));
        assertFalse(GourdCropTarget.cropBed(world(blocks,Set.of()),FRUIT,null));
    }
}
