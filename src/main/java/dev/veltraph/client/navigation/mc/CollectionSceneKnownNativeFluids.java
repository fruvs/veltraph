package dev.veltraph.client.navigation.mc;
import java.util.Set;
import net.minecraft.world.level.material.Fluid;
/** Fixed concrete fluid owners from the inert official 26.3 SDK descriptor audit. */
final class CollectionSceneKnownNativeFluids {
    private CollectionSceneKnownNativeFluids() {}
    private static final Set<String> TYPES=Set.of(
        "net.minecraft.world.level.material.EmptyFluid",
        "net.minecraft.world.level.material.WaterFluid$Source",
        "net.minecraft.world.level.material.WaterFluid$Flowing",
        "net.minecraft.world.level.material.LavaFluid$Source",
        "net.minecraft.world.level.material.LavaFluid$Flowing");
    static boolean known(Fluid value) {
        return value != null && value.getClass().getClassLoader() == Fluid.class.getClassLoader()
            && TYPES.contains(value.getClass().getName());
    }
}
