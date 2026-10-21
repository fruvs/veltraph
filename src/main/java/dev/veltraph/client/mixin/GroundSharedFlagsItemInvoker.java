package dev.veltraph.client.mixin;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.network.syncher.EntityDataAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
@Mixin(SynchedEntityData.class)
public interface GroundSharedFlagsItemInvoker {
    @Invoker("getItem") <T> SynchedEntityData.DataItem<T> veltraph$nativeSharedItem(EntityDataAccessor<T> accessor);
}
