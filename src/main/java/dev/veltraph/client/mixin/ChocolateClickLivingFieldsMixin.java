package dev.veltraph.client.mixin;
import dev.veltraph.client.chocolate.ChocolateClickNativeFields;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
/** Direct original native field read; no observer, codec, callback or mutation. */
@Mixin(targets="net.minecraft.world.entity.LivingEntity")
public abstract class ChocolateClickLivingFieldsMixin implements ChocolateClickNativeFields.LivingFields {
    @Shadow @org.spongepowered.asm.mixin.Final private static net.minecraft.network.syncher.EntityDataAccessor<Float> DATA_HEALTH_ID;
    public net.minecraft.network.syncher.EntityDataAccessor<Float> chocolate$clickHealthId(){return DATA_HEALTH_ID;}
}
