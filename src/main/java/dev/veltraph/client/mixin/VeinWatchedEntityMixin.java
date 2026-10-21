package dev.veltraph.client.mixin;
import dev.veltraph.client.mining.vein.VeinWatchNativeFields;
import dev.veltraph.client.mining.vein.VeinWatchedRevisions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.Level;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.network.syncher.EntityDataAccessor;
import java.util.UUID;
@Mixin(Entity.class)
public abstract class VeinWatchedEntityMixin implements VeinWatchNativeFields.EntityFields {
    @Shadow private Vec3 position;
    @Shadow @Final protected SynchedEntityData entityData;
    @Shadow private Level level;
    @Shadow protected UUID uuid;
    @Shadow private int id;
    @Shadow private Entity.RemovalReason removalReason;
    public Vec3 veltraph$watchPosition(){return position;}
    public SynchedEntityData veltraph$watchData(){return entityData;}
    public Object veltraph$watchWorld(){return level;}
    public UUID veltraph$watchUuid(){return uuid;}
    public int veltraph$watchId(){return id;}
    public Object veltraph$watchRemoval(){return removalReason;}
    @Inject(method="setPosRaw(DDD)V",at=@At("HEAD"),require=1)
    private void veltraph$watchPosition(double x,double y,double z,CallbackInfo ci){
        if ((Object)this instanceof ItemEntity) {
            if (position==null) VeinWatchedRevisions.mutation(this);
            else VeinWatchedRevisions.position(this,position.x,position.y,position.z,x,y,z);
        }
    }
    @Inject(method="setPose(Lnet/minecraft/world/entity/Pose;)V",at=@At("HEAD"),require=1)
    private void veltraph$watchPose(Pose pose,CallbackInfo ci){VeinWatchedRevisions.mutation(this);}
    @Inject(method="onSyncedDataUpdated(Lnet/minecraft/network/syncher/EntityDataAccessor;)V",at=@At("HEAD"),require=1)
    private void veltraph$watchSynced(EntityDataAccessor<?> accessor,CallbackInfo ci){VeinWatchedRevisions.mutation(this);}
    @Inject(method={"setId(I)V","setUUID(Ljava/util/UUID;)V","setLevel(Lnet/minecraft/world/level/Level;)V"},at=@At("HEAD"),require=1)
    private void veltraph$watchIdentity(CallbackInfo ci){VeinWatchedRevisions.mutation(this);}
    @Inject(method={"setRemoved(Lnet/minecraft/world/entity/Entity$RemovalReason;)V","unsetRemoved()V"},at=@At("HEAD"),require=1)
    private void veltraph$watchRemoval(CallbackInfo ci){VeinWatchedRevisions.mutation(this);}
}
