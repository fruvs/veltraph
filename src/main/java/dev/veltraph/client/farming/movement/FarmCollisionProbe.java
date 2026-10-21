package dev.veltraph.client.farming.movement;

import dev.veltraph.client.world.Vec3;
import dev.veltraph.client.world.BlockPos;
import java.util.OptionalDouble;

/** Read-only collision evidence for fractional creator guides and bounded intentional drops. */
public interface FarmCollisionProbe {
    boolean loaded(Vec3 feet);
    boolean clear(Vec3 feet,boolean allowWater);
    /** Highest loaded collision top intersecting the player footprint below the supplied bound. */
    OptionalDouble supportY(double x,double z,double highest,int downBlocks);
    boolean water(Vec3 feet);
    /** Actual actor fluid contact, distinct from a water-tag block in a queried volume. */
    default boolean playerInWater(){return false;}
    /** Current physical crosshair candidate only; native crop/owner/range guards remain mandatory. */
    default boolean matureHarvestHit(String cropId){return false;}
    /** Pure fixtures without state properties may retain ID-only evidence; MC resolves actual age. */
    default boolean matureCrop(BlockPos crop){return true;}
    /** Actual selected mature block outline height; absent shape is not startup evidence. */
    default OptionalDouble matureOutlineHeight(BlockPos crop){return OptionalDouble.empty();}
    default float eyeHeight(){return 1.62f;}
}
