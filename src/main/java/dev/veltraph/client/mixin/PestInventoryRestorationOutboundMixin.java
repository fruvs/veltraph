package dev.veltraph.client.mixin;
import dev.veltraph.client.garden.pests.mc.McPestInventoryRestorationObserver;
import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import net.minecraft.network.protocol.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
/** All native outgoing traffic observes only the envelope/window captured at this exact entry. */
@Mixin(ClientCommonPacketListenerImpl.class)
public abstract class PestInventoryRestorationOutboundMixin implements McPestInventoryRestorationObserver.SendCarrier {
    @Unique private final ThreadLocal<java.util.ArrayList<McPestInventoryRestorationObserver.SendEnvelope>> pest$envelopes=ThreadLocal.withInitial(java.util.ArrayList::new);
    public int pest$pushEnvelope(McPestInventoryRestorationObserver.SendEnvelope envelope){var a=pest$envelopes.get();int depth=a.size();a.add(envelope);return depth;}
    public void pest$replaceEnvelope(int depth,McPestInventoryRestorationObserver.SendEnvelope envelope){var a=pest$envelopes.get();if(depth>=0&&depth<a.size())a.set(depth,envelope);}
    public void pest$popEnvelope(int depth,McPestInventoryRestorationObserver.SendEnvelope envelope){var a=pest$envelopes.get();if(depth>=0&&depth<a.size()&&a.get(depth)==envelope)a.remove(depth);if(a.isEmpty())pest$envelopes.remove();}
    public int pest$envelopeDepth(){return pest$envelopes.get().size();}
    public boolean pest$envelopeCurrent(int depth,McPestInventoryRestorationObserver.SendEnvelope envelope){var a=pest$envelopes.get();return depth>=0&&a.size()==depth+1&&a.get(depth)==envelope;}
    @WrapMethod(method="send(Lnet/minecraft/network/protocol/Packet;)V",require=1)
    private void pest$allInventoryOutbound(Packet<?> packet,Operation<Void> original){
        McPestInventoryRestorationObserver.OutboundInvocation call=null;
        try{var a=pest$envelopes.get();call=McPestInventoryRestorationObserver.beginOutbound(this,packet,!a.isEmpty(),a.isEmpty()?null:a.getLast());}catch(Throwable observerFailure){}
        boolean normal=false;
        try{original.call(packet);normal=true;}
        finally{try{McPestInventoryRestorationObserver.finishOutbound(call,normal);}catch(Throwable observerFailure){/* Preserve native exception; never borrow a newer Window. */}}
    }
}
