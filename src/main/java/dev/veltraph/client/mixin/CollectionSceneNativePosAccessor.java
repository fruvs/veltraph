package dev.veltraph.client.mixin;
import dev.veltraph.client.navigation.mc.CollectionSceneNativeAccess;
import net.minecraft.core.Vec3i;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(Vec3i.class)
public interface CollectionSceneNativePosAccessor extends CollectionSceneNativeAccess.NativePos {
    @Accessor("x") int veltraph$sceneX();
    @Accessor("y") int veltraph$sceneY();
    @Accessor("z") int veltraph$sceneZ();
}
