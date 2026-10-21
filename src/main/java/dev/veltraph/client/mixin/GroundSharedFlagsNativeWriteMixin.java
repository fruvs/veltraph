package dev.veltraph.client.mixin;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.veltraph.client.navigation.GroundPhysicsNativeRevisions;
import dev.veltraph.client.navigation.GroundSharedFlagsWriteSequence;
import dev.veltraph.client.navigation.mc.GroundSharedFlagsNativeCarrier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
@Mixin(Entity.class)
public abstract class GroundSharedFlagsNativeWriteMixin implements GroundSharedFlagsNativeCarrier {
    @Unique private GroundSharedFlagsWriteSequence veltraph$actualSharedWrite;
    public void observeSharedFlagsAttempt(Object data,Object item,Object accessor,Object value,int stage){
        var c=Minecraft.getInstance();if(c==null||c.player!=(Object)this||c.player.getClass()!=LocalPlayer.class)return;
        if(!c.isSameThread()||veltraph$actualSharedWrite==null){GroundPhysicsNativeRevisions.mutation(this);return;}
        var observed=veltraph$actualSharedWrite.observe(data,item,accessor,value,stage);
        if(observed==GroundSharedFlagsWriteSequence.Result.FOREIGN)GroundPhysicsNativeRevisions.mutation(this);
        else if(observed==GroundSharedFlagsWriteSequence.Result.NATIVE_SPRINT)
            GroundPhysicsNativeRevisions.mutation(this,switch(stage){case 1->GroundPhysicsNativeRevisions.Kind.SPRINT_DATA_SET2;
                case 2->GroundPhysicsNativeRevisions.Kind.SPRINT_DATA_SET3;default->GroundPhysicsNativeRevisions.Kind.SPRINT_DATA_ITEM;},value);
        // The single original native operation on another shared bit is irrelevant to bit 3.
        // Any nested, duplicate, forged, raw or out-of-order attempt was stamped FOREIGN above.
    }
    @WrapOperation(method="setSharedFlag(IZ)V",at=@At(value="INVOKE",target="Lnet/minecraft/network/syncher/SynchedEntityData;set(Lnet/minecraft/network/syncher/EntityDataAccessor;Ljava/lang/Object;)V"),require=2)
    private void veltraph$originalSharedWrite(SynchedEntityData data,EntityDataAccessor<?> accessor,Object value,
            Operation<Void> original,int flag,boolean enabled){
        var c=Minecraft.getInstance();var p=c==null?null:c.player;
        boolean actual=p!=null&&p.getClass()==LocalPlayer.class&&p==(Object)this&&c.isSameThread()
            &&data==p.getEntityData()&&accessor==GroundSharedFlagsAccessor.veltraph$nativeSharedFlags()
            &&value instanceof Byte&&veltraph$actualSharedWrite==null;
        var prior=veltraph$actualSharedWrite;veltraph$actualSharedWrite=null;
        if(actual){var flags=GroundSharedFlagsAccessor.veltraph$nativeSharedFlags();
            var item=((GroundSharedFlagsItemInvoker)(Object)data).veltraph$nativeSharedItem(flags);
            veltraph$actualSharedWrite=new GroundSharedFlagsWriteSequence(data,item,flags,data.get(flags),(Byte)value,flag,enabled);
        }else if(p==(Object)this)GroundPhysicsNativeRevisions.mutation(this);
        try{original.call(data,accessor,value);}finally{
            if(actual&&!veltraph$actualSharedWrite.complete(data.get(GroundSharedFlagsAccessor.veltraph$nativeSharedFlags())))
                GroundPhysicsNativeRevisions.mutation(this);
            veltraph$actualSharedWrite=prior;
        }
    }
}
