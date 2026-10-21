package dev.veltraph.client.mixin;
import dev.veltraph.client.navigation.GroundPhysicsRevisionTracked;
import dev.veltraph.client.navigation.mc.GroundSharedFlagsMutationBridge;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.network.syncher.EntityDataAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(SynchedEntityData.class)
public abstract class GroundSharedFlagsDataRevisionMixin implements GroundPhysicsRevisionTracked {
    @Inject(method="set(Lnet/minecraft/network/syncher/EntityDataAccessor;Ljava/lang/Object;)V",at=@At("HEAD"),require=1)
    private void veltraph$set2(EntityDataAccessor<?> accessor,Object value,CallbackInfo ci){
        GroundSharedFlagsMutationBridge.attemptedData(this,accessor,value,1);}
    @Inject(method="set(Lnet/minecraft/network/syncher/EntityDataAccessor;Ljava/lang/Object;Z)V",at=@At("HEAD"),require=1)
    private void veltraph$set3(EntityDataAccessor<?> accessor,Object value,boolean force,CallbackInfo ci){
        // Native set2 delegates with false. A force=true raw write is always foreign.
        GroundSharedFlagsMutationBridge.attemptedData(this,accessor,value,force?4:2);}
    @Inject(method="assignValue(Lnet/minecraft/network/syncher/SynchedEntityData$DataItem;Lnet/minecraft/network/syncher/SynchedEntityData$DataValue;)V",at=@At("HEAD"),require=1)
    private void veltraph$packetItem(SynchedEntityData.DataItem<?> item,SynchedEntityData.DataValue<?> value,CallbackInfo ci){
        GroundSharedFlagsMutationBridge.packetItemAttempt(this,item,value);}
}
