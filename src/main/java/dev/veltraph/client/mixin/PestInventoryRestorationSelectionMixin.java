package dev.veltraph.client.mixin;
import dev.veltraph.client.garden.pests.mc.McPestInventoryRestorationObserver;
import net.minecraft.world.entity.player.Inventory;
import org.spongepowered.asm.mixin.Mixin;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
/** Native local setter attempts get an opaque entry/exit version, including selection ABA. */
@Mixin(Inventory.class)
public abstract class PestInventoryRestorationSelectionMixin implements McPestInventoryRestorationObserver.SlotVersionCarrier {
    @WrapMethod(method="setSelectedSlot(I)V",require=1)
    private void pest$nativeSlotVersion(int next,Operation<Void> original){
        McPestInventoryRestorationObserver.LocalSelectionInvocation call=null;
        try{call=McPestInventoryRestorationObserver.beginLocalSelection((Inventory)(Object)this,next);}catch(Throwable observerFailure){}
        boolean normal=false;
        try{original.call(next);normal=true;}
        finally{try{McPestInventoryRestorationObserver.finishLocalSelection(call,normal);}catch(Throwable observerFailure){/* Never mask native setter/exception. */}}
    }
}
