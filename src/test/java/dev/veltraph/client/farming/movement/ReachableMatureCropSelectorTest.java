package dev.veltraph.client.farming.movement;
import dev.veltraph.client.world.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
final class ReachableMatureCropSelectorTest {
 Object owner=new Object(),player=new Object(),world=new Object(),connection=new Object();
 ReachableMatureCropSelector.Scope scope(long tick){return new ReachableMatureCropSelector.Scope(owner,player,world,connection,1,tick);}
 ReachableMatureCropSelector.Crop crop(int x,boolean mature,boolean visible){return new ReachableMatureCropSelector.Crop(new BlockPos(x,71,0),new Vec3(x+.5,71.65,.5),true,mature,true,visible);}
 @Test void behindColumnReachableFromNextLane(){var eye=new Vec3(57.5,71.62,.5);var r=ReachableMatureCropSelector.select(scope(1),eye,4.5,List.of(crop(56,true,true)),null);assertEquals(56,r.orElseThrow().cell().x());}
 @Test void farColumnNotReachableFromPriorLane(){assertTrue(ReachableMatureCropSelector.select(scope(1),new Vec3(50.5,71.62,.5),4.5,List.of(crop(56,true,true)),null).isEmpty());}
 @Test void immatureOccludedUnloadedAndWrongSoilRejected(){var eye=new Vec3(57.5,71.62,.5);assertTrue(ReachableMatureCropSelector.select(scope(1),eye,4.5,List.of(crop(56,false,true),crop(55,true,false),new ReachableMatureCropSelector.Crop(new BlockPos(58,71,0),new Vec3(58.5,71.65,.5),false,true,true,true),new ReachableMatureCropSelector.Crop(new BlockPos(59,71,0),new Vec3(59.5,71.65,.5),true,true,false,true)),null).isEmpty());}
 @Test void nearestVisibleLateralWins(){var eye=new Vec3(57.5,71.62,.5);assertEquals(56,ReachableMatureCropSelector.select(scope(1),eye,4.5,List.of(crop(54,true,true),crop(56,true,true)),null).orElseThrow().cell().x());}
 @Test void laterFrameOwnerReplacementEpochOrOcclusionRevokes(){var eye=new Vec3(57.5,71.62,.5);var c=crop(56,true,true);var selected=ReachableMatureCropSelector.select(scope(1),eye,4.5,List.of(c),null).orElseThrow();assertTrue(ReachableMatureCropSelector.current(selected,scope(1),eye,4.5,c));assertFalse(ReachableMatureCropSelector.current(selected,scope(2),eye,4.5,c));assertFalse(ReachableMatureCropSelector.current(selected,new ReachableMatureCropSelector.Scope(new Object(),player,world,connection,1,1),eye,4.5,c));assertFalse(ReachableMatureCropSelector.current(selected,new ReachableMatureCropSelector.Scope(owner,player,world,connection,2,1),eye,4.5,c));assertFalse(ReachableMatureCropSelector.current(selected,scope(1),eye,4.5,crop(56,true,false)));}
 @Test void cancellationAvoidsReselectingSameTimedOutCell(){var eye=new Vec3(57.5,71.62,.5);assertTrue(ReachableMatureCropSelector.select(scope(1),eye,4.5,List.of(crop(56,true,true)),new BlockPos(56,71,0)).isEmpty());}
 @Test void forwardPrimarySectorIsNeverCapturedByCompletion(){var eye=new Vec3(57.5,71.62,.5);assertTrue(ReachableMatureCropSelector.selectMissed(scope(1),eye,4.5,List.of(crop(58,true,true),crop(59,true,true)),null,-90).isEmpty());}
 @Test void mixedFieldSelectsMissedBehindAndLeavesForwardPrimary(){var eye=new Vec3(57.5,71.62,.5);assertEquals(56,ReachableMatureCropSelector.selectMissed(scope(1),eye,4.5,List.of(crop(58,true,true),crop(56,true,true)),null,-90).orElseThrow().cell().x());}

}
