package dev.veltraph.client.mixin;
import dev.veltraph.client.navigation.mc.CollectionSceneNativeAccess;
import java.util.Set;
import net.minecraft.core.Holder;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(Holder.Reference.class)
public abstract class CollectionSceneHolderMixin implements CollectionSceneNativeAccess.HolderFields {
    @Unique private Object veltraph$sceneHolderRevision=new Object();
    @Unique private boolean veltraph$sceneTagsAssigned;
    public Object veltraph$sceneHolderRevision() { return veltraph$sceneHolderRevision; }
    public boolean veltraph$sceneTagsAssigned() { return veltraph$sceneTagsAssigned; }
    public Set<TagKey<Block>> veltraph$sceneHolderTags() {
        return (Object)this instanceof CollectionSceneHolderAccessor raw ? raw.veltraph$sceneRawHolderTags() : null;
    }
    public Object veltraph$sceneHolderValue() {
        return (Object)this instanceof CollectionSceneHolderAccessor raw ? raw.veltraph$sceneRawHolderValue() : null;
    }
    @Inject(method={"bindTags(Ljava/util/Collection;)V","bindValue(Ljava/lang/Object;)V",
        "bindKey(Lnet/minecraft/resources/ResourceKey;)V"},at=@At("HEAD"),require=1)
    private void veltraph$sceneHolderAttempt(CallbackInfo ci) { veltraph$sceneHolderRevision=new Object(); }
    @Inject(method="bindTags(Ljava/util/Collection;)V",at=@At(value="FIELD",
        target="Lnet/minecraft/core/Holder$Reference;tags:Ljava/util/Set;",opcode=181,shift=At.Shift.AFTER),require=1)
    private void veltraph$sceneActualTagCopy(CallbackInfo ci) { veltraph$sceneTagsAssigned=true; }
}
