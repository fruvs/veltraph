package dev.veltraph.client.farming.module;

import dev.veltraph.client.config.*;
import dev.veltraph.client.farming.domain.*;
import dev.veltraph.client.farming.movement.FarmCollisionProbe;
import dev.veltraph.client.world.*;
import java.util.OptionalDouble;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Actual installed crop predicate scope; native ownership/range guards are exercised separately. */
class NormalSShapeHarvestPolicyTest {
    static final BlockPos CROP=new BlockPos(0,65,1);
    static final class AgeProbe implements FarmCollisionProbe {
        boolean mature;int maturityReads;
        public boolean loaded(Vec3 feet){return true;}public boolean clear(Vec3 feet,boolean water){return true;}
        public OptionalDouble supportY(double x,double z,double y,int down){return OptionalDouble.of(64);}
        public boolean water(Vec3 feet){return false;}
        public boolean matureCrop(BlockPos crop){maturityReads++;return mature;}
    }
    record Rig(FarmMaintenanceWitnessTest.Rig services,WartCropsFarmModule farm,AgeProbe probe,Predicate<BlockPos> guard) {}
    @SuppressWarnings("unchecked")
    Rig start(WartVariety variety,FarmMovementMode mode,WartCropsDesign design){
        var r=new FarmMaintenanceWitnessTest.Rig();r.config.set(FarmingConfig.WART_CROPS_DESIGN,design);
        var farm=(WartCropsFarmModule)r.farm("wart");farm.setVariety(variety);farm.setMode(mode);
        r.world.blocks.put(CROP,crop(variety));r.world.blocks.put(CROP.down(),soil(variety));
        assertTrue(farm.preStartCheck().isEmpty());farm.setEnabled(true);assertTrue(farm.isEnabled());
        var probe=new AgeProbe();farm.setCollisionProbe(probe);
        return new Rig(r,farm,probe,(Predicate<BlockPos>)r.breaking.guard.predicate());
    }
    static String crop(WartVariety variety){return switch(variety){case WHEAT->"minecraft:wheat";case CARROT->"minecraft:carrots";case POTATO->"minecraft:potatoes";case NETHER_WART->"minecraft:nether_wart";};}
    static String soil(WartVariety variety){return variety==WartVariety.NETHER_WART?"minecraft:soul_sand":"minecraft:farmland";}

    @Test void installedNormalSGuardAllowsEveryAgeOfEachSelectedVariety(){
        for(var variety:WartVariety.values()){
            var r=start(variety,FarmMovementMode.S_SHAPE,WartCropsDesign.CLASSICAL);
            int maximum=variety==WartVariety.NETHER_WART?3:7;
            for(int age=0;age<=maximum;age++){
                r.services.world.age=Integer.toString(age);r.probe.mature=age==maximum;
                assertTrue(r.guard.test(CROP),variety+" age="+age);
            }
            assertEquals(0,r.probe.maturityReads,"Normal S does not condition attack on age");
        }
    }
    @Test void verticalRetainsMaturityForEachVariety(){
        for(var variety:WartVariety.values()){
            var r=start(variety,FarmMovementMode.VERTICAL,WartCropsDesign.CLASSICAL);
            assertFalse(r.guard.test(CROP));r.probe.mature=true;assertTrue(r.guard.test(CROP));
            assertEquals(2,r.probe.maturityReads);
        }
    }
    @Test void aceRetainsMaturity(){
        var r=start(WartVariety.WHEAT,FarmMovementMode.S_SHAPE,WartCropsDesign.ACE_TWO_KEY_LOOP);
        assertFalse(r.guard.test(CROP));r.probe.mature=true;assertTrue(r.guard.test(CROP));
        assertEquals(2,r.probe.maturityReads);
    }
    @Test void growingCropAllowanceRetainsSelectedSpeciesSoilAndLoadedCellChecks(){
        for(var variety:WartVariety.values()){
            var r=start(variety,FarmMovementMode.S_SHAPE,WartCropsDesign.CLASSICAL);
            assertTrue(r.guard.test(CROP));
            assertFalse(r.guard.test(CROP.down()),"Soil is never a crop target");
            r.services.world.blocks.put(CROP,"minecraft:stone");assertFalse(r.guard.test(CROP));
            for(var other:WartVariety.values())if(other!=variety){
                r.services.world.blocks.put(CROP,crop(other));assertFalse(r.guard.test(CROP));
            }
            r.services.world.blocks.put(CROP,crop(variety));r.services.world.blocks.put(CROP.down(),"minecraft:stone");assertFalse(r.guard.test(CROP));
            r.services.world.blocks.put(CROP.down(),soil(variety));r.services.world.loaded=false;assertFalse(r.guard.test(CROP));
            r.services.world.loaded=true;assertTrue(r.guard.test(CROP));
        }
    }
    @Test void normalGrowingCropPolicyDoesNotBypassRetainedToolReadiness(){
        var r=start(WartVariety.WHEAT,FarmMovementMode.S_SHAPE,WartCropsDesign.CLASSICAL);
        assertTrue(r.farm.selectedFarmingToolReady());r.services.breaking.held=true;r.services.input.right=true;
        r.services.inventory.setItem(2,"stone");assertFalse(r.farm.selectedFarmingToolReady());r.farm.onTick();
        assertFalse(r.services.breaking.held);assertFalse(r.services.input.right);
    }
}
