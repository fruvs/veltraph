package dev.veltraph.client.mixin;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.veltraph.client.navigation.GroundPhysicsNativeRevisions;
import dev.veltraph.client.navigation.mc.GroundOwnedSpeedRefreshCarrier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
@Mixin(Player.class)
public abstract class GroundOwnedSpeedRefreshMixin implements GroundOwnedSpeedRefreshCarrier {
    @Unique private Refresh veltraph$refresh;
    public Refresh ownedSpeedRefresh(){return veltraph$refresh;}
    @WrapOperation(method="aiStep()V",at=@At(value="INVOKE",target="Lnet/minecraft/world/entity/player/Player;setSpeed(F)V"),require=1)
    private void veltraph$normalSpeed(Player receiver,float speed,Operation<Void> original){
        var c=Minecraft.getInstance();boolean local=c!=null&&c.isSameThread()&&c.player==receiver
                &&receiver.getClass()==LocalPlayer.class;
        var before=local?GroundPhysicsNativeRevisions.watch(receiver):null;float old=receiver.getSpeed();
        veltraph$refresh=null;
        original.call(receiver,speed); // The exact original normal native operation, once.
        if(local&&before!=null&&Float.floatToIntBits(speed)==Float.floatToIntBits(receiver.getSpeed()))
            veltraph$refresh=new Refresh(receiver.tickCount,old,receiver.getSpeed(),before,GroundPhysicsNativeRevisions.watch(receiver));
    }
}
