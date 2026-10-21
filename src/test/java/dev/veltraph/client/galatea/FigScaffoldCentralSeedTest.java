package dev.veltraph.client.galatea;
import dev.veltraph.client.world.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class FigScaffoldCentralSeedTest {
    @Test void emptyAndSingletonNeverEvaluateUnnecessaryRanking(){var only=new BlockPos(1,5,0);assertTrue(FigScaffoldCentralSeed.select(List.of(),new Vec3(0,0,0),0,p->{throw new AssertionError();}).isEmpty());assertEquals(only,FigScaffoldCentralSeed.select(List.of(only),new Vec3(0,0,0),0,p->{throw new AssertionError();}).orElseThrow());}
    @Test void rawBaseToRawSeedYawDoesNotIntroduceHalfBlockBias(){var east=new BlockPos(1,0,0);var north=new BlockPos(0,0,-1);assertEquals(east,FigScaffoldCentralSeed.select(List.of(east,north),new Vec3(0,0,0),-90,p->p.equals(east)?5.5:0).orElseThrow());var sameColumn=new BlockPos(0,1,0);assertEquals(sameColumn,FigScaffoldCentralSeed.select(List.of(sameColumn,east),new Vec3(0,0,0),-90,p->p.equals(east)?1:0).orElseThrow());}
    @Test void equalScoresRetainOriginalModeListOrder(){var a=new BlockPos(1,0,0);var b=new BlockPos(1,1,0);assertEquals(a,FigScaffoldCentralSeed.select(List.of(a,b),new Vec3(0,0,0),-90,p->0).orElseThrow());assertEquals(b,FigScaffoldCentralSeed.select(List.of(b,a),new Vec3(0,0,0),-90,p->0).orElseThrow());}
    @Test void nativeNonLogRemovalPreservesLiveOrderAndOriginalDescriptorGeometry(){var lower=new BlockPos(2,70,0);var upper=new BlockPos(2,75,0);var branch=new BlockPos(3,76,0);var original=List.of(lower,upper,branch);var live=FigScaffoldCentralSeed.liveModeList(original,p->!p.equals(lower));assertEquals(List.of(upper,branch),live);assertEquals(List.of(lower,upper,branch),original);assertEquals(upper,FigScaffoldCentralSeed.select(live,new Vec3(2,70,0),-90,p->p.equals(lower)?-100:0).orElseThrow());}
}
