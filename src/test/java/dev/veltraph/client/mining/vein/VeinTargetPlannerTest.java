package dev.veltraph.client.mining.vein;
import dev.veltraph.client.world.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class VeinTargetPlannerTest {
 final Vec3 player=new Vec3(0,70,0);final VeinTargetPlanner.Weights weights=new VeinTargetPlanner.Weights(1,1,1);
 VeinTargetPlanner.Option option(int x,boolean ray,long time){return new VeinTargetPlanner.Option(new BlockPos(x,71,2),"minecraft:gray_wool",player,new Vec3(0,71.62,0),new Vec3(x+.5,71.5,2),true,true,true,true,ray,500,time);}
 Optional<VeinTargetPlanner.Choice> choose(List<VeinTargetPlanner.Option> options){return VeinTargetPlanner.choose(options,Map.of(MiningMaterial.GRAY_MITHRIL,1),player,0,0,4.5,1000,250,weights);}
 @Test void laterValidVantageIsNotSuppressedByInvalidFirst(){assertEquals(option(0,true,1000),choose(List.of(option(0,false,1000),option(0,true,1000))).orElseThrow().option());}
 @Test void candidateListOrderDoesNotPretendToBeHeapSorted(){assertEquals(0,choose(List.of(option(2,true,1000),option(0,true,1000))).orElseThrow().option().cell().x());}
 @Test void staleAndFutureProofsAreRejected(){assertTrue(choose(List.of(option(0,true,749),option(0,true,1001))).isEmpty());}
 @Test void rayMissCannotBeSelected(){assertTrue(choose(List.of(option(0,false,1000))).isEmpty());}
 @Test void actualPointReachBoundsSelection(){assertTrue(choose(List.of(option(8,true,1000))).isEmpty());}
 @Test void zeroPriorityDoesNotDivideByZero(){assertTrue(VeinTargetPlanner.choose(List.of(option(0,true,1000)),Map.of(MiningMaterial.GRAY_MITHRIL,0),player,0,0,4.5,1000,250,weights).isEmpty());}
 @Test void nonfinitePoseIsRejected(){assertTrue(VeinTargetPlanner.choose(List.of(option(0,true,1000)),Map.of(MiningMaterial.GRAY_MITHRIL,1),new Vec3(Double.NaN,70,0),0,0,4.5,1000,250,weights).isEmpty());}
 @Test void boundedInputRejectsOversizedScanning(){assertThrows(IllegalArgumentException.class,()->choose(Collections.nCopies(VeinTargetPlanner.MAX_OPTIONS+1,option(0,true,1000))));}
 @Test void invalidWeightsAreRejected(){assertThrows(IllegalArgumentException.class,()->new VeinTargetPlanner.Weights(-1,1,1));assertThrows(IllegalArgumentException.class,()->new VeinTargetPlanner.Weights(0,0,0));}
 @Test void unloadedUnsupportedUnsafeBodyAreRejected(){var o=option(0,true,1000);for(int flag=0;flag<4;flag++){var bad=new VeinTargetPlanner.Option(o.cell(),o.state(),o.standing(),o.eye(),o.aim(),flag!=0,flag!=1,flag!=2,flag!=3,true,500,1000);assertTrue(choose(List.of(bad)).isEmpty());}}
}
