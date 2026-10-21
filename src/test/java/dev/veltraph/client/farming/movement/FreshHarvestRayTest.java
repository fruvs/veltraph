package dev.veltraph.client.farming.movement;

import dev.veltraph.client.world.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Controlled read ordering, not native permission or full-farm evidence. */
class FreshHarvestRayTest {
    static class Read implements FreshHarvestRay.Read {
        boolean scene=true,camera=true,connected=true,loaded=true,mature=true,soil=true;
        double range=4.5,distance=3.7;int picks;String retireAt;
        final List<String> reads=new ArrayList<>();
        FreshHarvestRay.Hit rendererCached=null,fresh=new FreshHarvestRay.Hit(new BlockPos(70,71,1),new Vec3(70,71.5,1));
        void read(String name){reads.add(name);if(name.equals(retireAt))scene=false;}
        public boolean current(){return scene&&camera&&connected;}
        public double range(){read("range");return range;}
        public FreshHarvestRay.Hit pick(double value){read("pick");picks++;assertEquals(range,value);return fresh;}
        public boolean loaded(BlockPos cell){read("loaded");return loaded;}
        public boolean matureWheat(BlockPos cell){read("mature");return mature;}
        public boolean farmland(BlockPos cell){read("soil");return soil;}
        public double distance(FreshHarvestRay.Hit hit){read("distance");return distance;}
    }
    @Test void staleRendererAirDoesNotSuppressFreshMatureCurrentRay(){var r=new Read();assertNull(r.rendererCached);assertTrue(FreshHarvestRay.available(r));assertEquals(1,r.picks);assertEquals(List.of("range","pick","loaded","loaded","mature","soil","distance"),r.reads);}
    @Test void freshMissOrObstructionCannotBorrowOlderMatureHit(){var r=new Read();r.rendererCached=r.fresh;r.fresh=null;assertFalse(FreshHarvestRay.available(r));assertEquals(List.of("range","pick"),r.reads);}
    @Test void freshImmatureOrNonWheatRefusesBeforeSoilAndDistance(){var r=new Read();r.mature=false;assertFalse(FreshHarvestRay.available(r));assertEquals(List.of("range","pick","loaded","loaded","mature"),r.reads);}
    @Test void wrongSoilRemainsRefused(){var r=new Read();r.soil=false;assertFalse(FreshHarvestRay.available(r));assertFalse(r.reads.contains("distance"));}
    @Test void unloadedCellRemainsRefused(){var r=new Read();r.loaded=false;assertFalse(FreshHarvestRay.available(r));assertEquals(List.of("range","pick","loaded"),r.reads);}
    @Test void disconnectRefusesWithoutPick(){var r=new Read();r.connected=false;assertFalse(FreshHarvestRay.available(r));assertTrue(r.reads.isEmpty());}
    @Test void foreignCameraRefusesWithoutPick(){var r=new Read();r.camera=false;assertFalse(FreshHarvestRay.available(r));assertTrue(r.reads.isEmpty());}
    @Test void contextChangeDuringFreshPickSkipsAllLaterReads(){var r=new Read();r.retireAt="pick";assertFalse(FreshHarvestRay.available(r));assertEquals(List.of("range","pick"),r.reads);}
    @Test void contextChangeDuringMaturitySkipsSoilAndDistance(){var r=new Read();r.retireAt="mature";assertFalse(FreshHarvestRay.available(r));assertFalse(r.reads.contains("soil"));assertFalse(r.reads.contains("distance"));}
    @Test void finalContextChangeCannotPublishCandidate(){var r=new Read();r.retireAt="distance";assertFalse(FreshHarvestRay.available(r));}
    @Test void nonfiniteOrUnboundedRangeRefusesBeforePick(){for(double range:new double[]{Double.NaN,Double.POSITIVE_INFINITY,0,-1,64.1}){var r=new Read();r.range=range;assertFalse(FreshHarvestRay.available(r));assertEquals(0,r.picks);}}
    @Test void distanceOutsideNativeRangeOrNonfiniteRemainsRefused(){for(double distance:new double[]{4.5001,Double.NaN,Double.POSITIVE_INFINITY,-1}){var r=new Read();r.distance=distance;assertFalse(FreshHarvestRay.available(r));}}
    @Test void providerExceptionIsNotConvertedIntoSuccess(){var r=new Read(){public FreshHarvestRay.Hit pick(double range){throw new IllegalStateException("original");}};var e=assertThrows(IllegalStateException.class,()->FreshHarvestRay.available(r));assertEquals("original",e.getMessage());}
}
