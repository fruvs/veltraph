package dev.veltraph.client.mixin;
import dev.veltraph.client.service.mc.McScopedCarriedItemSynchronization;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.Packet;
import java.util.ArrayDeque;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Final;
import net.minecraft.client.Minecraft;
import dev.veltraph.client.navigation.NativeCarryPublicationWitness;
import org.spongepowered.asm.mixin.injection.At;
import org.objectweb.asm.Opcodes;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
/** Ordinary calls stay ordinary; only the adapter's exact private invocation is fenced. */
@Mixin(MultiPlayerGameMode.class)
public abstract class ScopedCarriedItemSynchronizationMixin {
    @Shadow @Final private Minecraft minecraft;
    @Shadow @Final private ClientPacketListener connection;
    @Shadow private int carriedIndex;
    @Unique private final NativeCarryPublicationWitness.CacheHistory carry$cacheHistory=new NativeCarryPublicationWitness.CacheHistory();
    @Unique private final ThreadLocal<ArrayDeque<java.util.Optional<McScopedCarriedItemSynchronization.Request>>> carry$calls=new ThreadLocal<>();
    @WrapMethod(method="ensureHasSentCarriedItem",require=1)
    private void carry$nativeBody(Operation<Void> original) {
        var call=java.util.Optional.ofNullable(McScopedCarriedItemSynchronization.enterNative((MultiPlayerGameMode)(Object)this,minecraft,connection,()->carriedIndex,carry$cacheHistory::snapshot));
        var stack=carry$calls.get();
        if(call.isEmpty()&&(stack==null||stack.isEmpty())){original.call();return;}
        if(stack==null){stack=new ArrayDeque<>();carry$calls.set(stack);}stack.push(call);
        try{original.call();}finally{if(stack.peek()==call)stack.pop();if(stack.isEmpty())carry$calls.remove();}
    }
    @WrapOperation(method="ensureHasSentCarriedItem",at=@At(value="FIELD",target="Lnet/minecraft/client/multiplayer/MultiPlayerGameMode;carriedIndex:I",opcode=Opcodes.PUTFIELD),require=1)
    private void carry$nativeCache(MultiPlayerGameMode mode,int slot,Operation<Void> original) {
        var stack=carry$calls.get();var call=stack==null?null:stack.peek();var request=call==null?null:call.orElse(null);
        if(request==null){carry$originalCache(mode,slot,original);return;}
        McScopedCarriedItemSynchronization.stageCache(request,mode,slot,()->carry$originalCache(mode,slot,original));
    }
    @Unique private void carry$originalCache(MultiPlayerGameMode mode,int slot,Operation<Void> original){
        boolean returned=false;
        try{original.call(mode,slot);returned=true;}
        finally{if(returned)carry$cacheHistory.originalWriteReturned();else carry$cacheHistory.originalWriteFailed();}
    }
    @WrapOperation(method="ensureHasSentCarriedItem",at=@At(value="FIELD",target="Lnet/minecraft/client/multiplayer/MultiPlayerGameMode;carriedIndex:I",opcode=Opcodes.GETFIELD,ordinal=1),require=1)
    private int carry$constructorSlot(MultiPlayerGameMode mode,Operation<Integer> original){
        var stack=carry$calls.get();var call=stack==null?null:stack.peek();var request=call==null?null:call.orElse(null);
        return request==null?original.call(mode):McScopedCarriedItemSynchronization.constructorSlot(request,mode);
    }
    @WrapOperation(method="ensureHasSentCarriedItem",at=@At(value="INVOKE",target="Lnet/minecraft/client/multiplayer/ClientPacketListener;send(Lnet/minecraft/network/protocol/Packet;)V"),require=1)
    private void carry$nativePacket(ClientPacketListener listener,Packet<?> packet,Operation<Void> original) {
        var stack=carry$calls.get();var call=stack==null?null:stack.peek();var request=call==null?null:call.orElse(null);
        var send=McScopedCarriedItemSynchronization.beforeSend(request,listener,packet);
        try{original.call(listener,packet);}finally{McScopedCarriedItemSynchronization.closeSend(send);}
    }
}
