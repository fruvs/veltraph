package dev.veltraph.client.mixin;
import dev.veltraph.client.chocolate.ChocolateClickNativeFields;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
/** Direct original native field read; no observer, codec, callback or mutation. */
@Mixin(targets="net.minecraft.world.inventory.AbstractContainerMenu")
public abstract class ChocolateClickMenuFieldsMixin implements ChocolateClickNativeFields.MenuFields {
    @Shadow private int stateId;
    public int chocolate$clickRevision(){return stateId;}
}
