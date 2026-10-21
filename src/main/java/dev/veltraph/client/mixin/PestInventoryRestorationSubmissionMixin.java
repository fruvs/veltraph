package dev.veltraph.client.mixin;
import dev.veltraph.client.garden.pests.mc.McPestInventoryRestorationObserver;
import dev.veltraph.client.garden.pests.PestInventoryRestorationJournal.InvocationBoundaries;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
/** The whole upper vanilla body owns its capsule; recursive calls have separate finally-popped entries. */
@Mixin(MultiPlayerGameMode.class)
public abstract class PestInventoryRestorationSubmissionMixin {
    @Unique private final ThreadLocal<InvocationBoundaries<McPestInventoryRestorationObserver.InputInvocation>> pest$restoreCalls=ThreadLocal.withInitial(InvocationBoundaries::new);
    @WrapMethod(method="handleContainerInput(IIILnet/minecraft/world/inventory/ContainerInput;Lnet/minecraft/world/entity/player/Player;)V",require=1)
    private void pest$restoreInput(int menu,int slot,int button,ContainerInput input,Player player,Operation<Void> original){
        McPestInventoryRestorationObserver.InputInvocation call=null;InvocationBoundaries<McPestInventoryRestorationObserver.InputInvocation> stack=null;
        InvocationBoundaries.Boundary<McPestInventoryRestorationObserver.InputInvocation> boundary=null;
        try{stack=pest$restoreCalls.get();boundary=stack.enter();}catch(Throwable observerFailure){}
        if(boundary!=null){
            try{call=McPestInventoryRestorationObserver.captureInputEntry(this,player);stack.capture(boundary,call);}catch(Throwable observerFailure){}
            // Preserve the exact captured old entry if qualification throws. Its owned flag remains false.
            try{var qualified=McPestInventoryRestorationObserver.qualifyInputEntry(call,menu,slot,button,input);if(qualified!=null){call=qualified;stack.capture(boundary,call);}}catch(Throwable observerFailure){}
        }
        boolean normal=false;
        try{original.call(menu,slot,button,input,player);normal=true;}
        finally{
            try{if(stack!=null&&boundary!=null)stack.exit(boundary);if(stack!=null&&stack.empty())pest$restoreCalls.remove();}catch(Throwable observerFailure){}
            try{McPestInventoryRestorationObserver.finishInput(call,normal);}catch(Throwable observerFailure){/* Preserve native exception; an old body cannot revoke a replacement window. */}
        }
    }
    @WrapOperation(method="handleContainerInput",at=@At(value="INVOKE",target="Lnet/minecraft/client/multiplayer/ClientPacketListener;send(Lnet/minecraft/network/protocol/Packet;)V"),require=1)
    private void pest$restoreSubmission(ClientPacketListener listener,Packet<?> packet,Operation<Void> original){
        McPestInventoryRestorationObserver.SendInvocation call=null;
        McPestInventoryRestorationObserver.SendCarrier carrier=listener instanceof McPestInventoryRestorationObserver.SendCarrier c?c:null;
        McPestInventoryRestorationObserver.SendEnvelope envelope=null;int envelopeDepth=-1;
        try{if(carrier!=null){envelopeDepth=carrier.pest$pushEnvelope(null);var stack=pest$restoreCalls.get();var input=stack.top();
                envelope=McPestInventoryRestorationObserver.inputEnvelope(input,packet);carrier.pest$replaceEnvelope(envelopeDepth,envelope);}}
        catch(Throwable observerFailure){envelope=null;}
        boolean normal=false;
        try{original.call(listener,packet);normal=true;}
        finally{try{if(carrier!=null&&envelopeDepth>=0)carrier.pest$popEnvelope(envelopeDepth,envelope);}catch(Throwable observerFailure){/* Preserve original sending exception. */}}
    }
}
