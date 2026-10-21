package dev.veltraph.client.garden.visitor.history.mc.mixin;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.veltraph.client.garden.visitor.history.VisitorHistoryNativeCallCarrier;
import dev.veltraph.client.garden.visitor.history.VisitorHistoryNativeCallFrame;
import dev.veltraph.client.garden.visitor.history.mc.McVisitorHistoryPacketObserver;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
/** Actual private upper origin; original vanilla prediction/send executes exactly once, with unchanged arguments/result/exception. */
@Mixin(MultiPlayerGameMode.class)
public abstract class OwnedGuiInputSourceMixin implements VisitorHistoryNativeCallCarrier {
    @Unique private final ThreadLocal<VisitorHistoryNativeCallFrame> veltraph$ownedGuiInputs=new ThreadLocal<>();
    @Override public Object veltraph$claimHistoryNativeCall(Object p,Object a1,Object a2,int kind){var f=veltraph$ownedGuiInputs.get();return f==null?null:f.claim(p,a1,a2,kind);}
    @Override public boolean veltraph$historyNativeCallCurrent(Object t,Object p,Object a1,Object a2,int kind,boolean normal){var f=veltraph$ownedGuiInputs.get();return f!=null&&f.current(t,p,a1,a2,kind,normal);}
    @WrapMethod(method="handleContainerInput(IIILnet/minecraft/world/inventory/ContainerInput;Lnet/minecraft/world/entity/player/Player;)V",require=1)
    private void history$ownedInput(int menu,int slot,int button,ContainerInput input,Player player,Operation<Void> original){
        var key=new Object();VisitorHistoryNativeCallFrame previous=null,frame=null;boolean installed=false;McVisitorHistoryPacketObserver.GuiInputInvocation call=null;
        try{previous=veltraph$ownedGuiInputs.get();frame=new VisitorHistoryNativeCallFrame(key,this,player,7);veltraph$ownedGuiInputs.set(frame);installed=true;call=McVisitorHistoryPacketObserver.beginGuiInput(this,key,player,menu,slot,button,input);}catch(Throwable failure){/* Observation failure cannot suppress native input. */}
        boolean normal=false;try{original.call(menu,slot,button,input,player);normal=true;}
        finally{try{if(frame!=null)frame.originalReturned(normal);McVisitorHistoryPacketObserver.finishGuiInput(call,normal);}catch(Throwable failure){/* Preserve native result/exception. */}
            finally{try{if(installed){if(previous==null)veltraph$ownedGuiInputs.remove();else veltraph$ownedGuiInputs.set(previous);}}catch(Throwable failure){/* Preserve native result/exception. */}}}
    }
}
