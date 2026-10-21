package dev.veltraph.client.builder;
import dev.veltraph.client.config.BuilderCropType;
/** Original selected crop profile's second resource; source95 quota128 is not the construction block. */
public final class Source95CropResource {
 private Source95CropResource(){}
 public static BuilderCropType crop(String item){for(var crop:BuilderCropType.values())if(item(crop).equals(item))return crop;throw new IllegalArgumentException("Source95 crop acquisition item is not a recovered crop resource");}
 /** Group5 wrapper's a4 is literallytrue, not an inferred game/account mode. */
 public static boolean oneAtMost16(){return true;}
 /** All four selected a5 descriptors have different symbols from the source special-resource singleton. */
 public static boolean special(BuilderCropType crop){java.util.Objects.requireNonNull(crop);return false;}
 public static String item(BuilderCropType crop){return switch(crop){case WART->"minecraft:nether_wart";case WHEAT->"minecraft:wheat_seeds";case CARROT->"minecraft:carrot";case POTATO->"minecraft:potato";};}
}
