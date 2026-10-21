package dev.veltraph.client.farming.movement;

import dev.veltraph.client.world.BlockPos;
import dev.veltraph.client.world.Vec3;

/** Read-only current-view candidate. It grants no action, range, input or native target permission. */
public final class FreshHarvestRay {
    private FreshHarvestRay() {}
    public record Hit(BlockPos cell,Vec3 location) {}
    public interface Read {
        /** Refusal against the SAME captured scene/view; implementations must never refresh it. */
        boolean current();
        double range();
        Hit pick(double range);
        boolean loaded(BlockPos cell);
        boolean matureWheat(BlockPos cell);
        boolean farmland(BlockPos cell);
        /** Selected-crop implementations retain the same ordered proof without assuming wheat. */
        default boolean matureSelectedCrop(BlockPos cell){return matureWheat(cell);}
        default boolean selectedCropSupport(BlockPos cell){return farmland(cell);}
        double distance(Hit hit);
    }
    public static boolean available(Read read){
        if(!read.current())return false;
        double range=read.range();if(!read.current())return false;
        if(!Double.isFinite(range)||range<=0||range>64)return false;
        Hit hit=read.pick(range);if(!read.current())return false;
        if(hit==null||hit.cell()==null||hit.location()==null||!finite(hit.location()))return false;
        boolean loaded=read.loaded(hit.cell());if(!read.current()||!loaded)return false;
        BlockPos soil=hit.cell().down();loaded=read.loaded(soil);if(!read.current()||!loaded)return false;
        boolean mature=read.matureSelectedCrop(hit.cell());if(!read.current()||!mature)return false;
        boolean farmland=read.selectedCropSupport(soil);if(!read.current()||!farmland)return false;
        double distance=read.distance(hit);if(!read.current())return false;
        return Double.isFinite(distance)&&distance>=0&&distance<=range;
    }
    private static boolean finite(Vec3 value){return Double.isFinite(value.x())&&Double.isFinite(value.y())&&Double.isFinite(value.z());}
}
