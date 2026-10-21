package dev.veltraph.client.mixin;
import dev.veltraph.client.navigation.GroundPhysicsNativeRevisions;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(Entity.class)
public abstract class GroundPhysicsSprintFlagRevisionMixin {
    @Inject(method="setSprinting(Z)V",at=@At("HEAD"),require=1)
    private void veltraph$sprintFlag(boolean value,CallbackInfo ci){GroundPhysicsNativeRevisions.mutation(this,GroundPhysicsNativeRevisions.Kind.SPRINT_FLAG,value);}
    @Inject(method="setSharedFlag(IZ)V",at=@At("HEAD"),require=1)
    private void veltraph$sharedSprint(int flag,boolean value,CallbackInfo ci){if(flag==3)
        GroundPhysicsNativeRevisions.mutation(this,GroundPhysicsNativeRevisions.Kind.SPRINT_SHARED,value);}
}

