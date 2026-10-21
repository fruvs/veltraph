package dev.veltraph.client.mixin;
import dev.veltraph.client.navigation.mc.CollectionSceneNativeAccess;
import net.minecraft.world.entity.Entity;
import net.minecraft.core.BlockPos;
import java.util.Optional;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(Entity.class)
public interface CollectionSceneEntityAccessor extends CollectionSceneNativeAccess.EntityFields {
    @Accessor("level") Level veltraph$sceneLevel();
    @Accessor("mainSupportingBlockPos") Optional<BlockPos> veltraph$sceneSupportingBlock();
    @Accessor("position") Vec3 veltraph$scenePosition();
    @Accessor("bb") AABB veltraph$sceneBox();
    @Accessor("entityData") SynchedEntityData veltraph$sceneEntityData();
}
