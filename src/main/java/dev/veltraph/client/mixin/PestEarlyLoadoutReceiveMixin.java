package dev.veltraph.client.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.veltraph.client.garden.pests.mc.PestEarlyMessageCarrier;
import dev.veltraph.client.garden.pests.mc.PestEarlyReceiveBookkeeping.Call;
import dev.veltraph.client.garden.pests.mc.PestEarlyReceiveBookkeeping.NativeReceipt;
import dev.veltraph.client.game.LocationText;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import java.util.*;

/** Passive normal receive history; original native method is invoked exactly once with exceptions intact. */
@Mixin(ClientPacketListener.class)
public abstract class PestEarlyLoadoutReceiveMixin implements PestEarlyMessageCarrier {
    @Unique private final ArrayDeque<Receipt> early$messages=new ArrayDeque<>();
    @Unique private long early$serial;@Unique private boolean early$unknown;@Unique private Call early$active;
    @Unique private Call early$begin(ClientboundSystemChatPacket packet){
        Call call=null;
        try{var c=Minecraft.getInstance();if(!c.isSameThread()||c.getConnection()!=(Object)this||c.getConnection().getClass()!=ClientPacketListener.class||c.player==null||c.level==null||c.player.level()!=c.level||packet.overlay())return null;
            if(early$active!=null){early$active.revoked=true;early$unknown=true;early$messages.clear();return null;}
            if(early$unknown||early$serial==Long.MAX_VALUE){early$unknown=true;early$messages.clear();return null;}
            Object player=c.player,world=c.level,mode=c.gameMode,scope=dev.veltraph.client.VeltraphBootstrap.earlyLoadoutReceiptScope();if(scope==null)return null;long time=System.currentTimeMillis();
            call=new Call(c,player,world,mode,this,scope,packet,packet.content(),time);early$active=call; // Reserve before Component callbacks.
            String text=LocationText.stripColorCodes(call.component.getString());
            if(early$active!=call||call.revoked||early$unknown||!call.current()||text.length()>2048){call.revoked=true;early$unknown=true;early$messages.clear();return call;}
            call.message=text;return call;
        }catch(Throwable unavailable){if(call!=null)call.revoked=true;early$unknown=true;early$messages.clear();return call;}
    }
    @Unique private void early$finish(Call call,boolean normal){
        if(call==null||early$active!=call)return;
        try{if(!normal||call.message==null||call.revoked||early$unknown||!call.current()){early$unknown=true;early$messages.clear();return;}
            early$messages.addLast(new NativeReceipt(this,call.scope,++early$serial,call.time,call.message));while(early$messages.size()>64)early$messages.removeFirst();
        }catch(Throwable unavailable){early$unknown=true;early$messages.clear();}
        finally{if(early$active==call)early$active=null;}
    }
    @WrapMethod(method="handleSystemChat",require=1)
    private void early$actualChat(ClientboundSystemChatPacket packet,Operation<Void> original){
        Call call=early$begin(packet);boolean normal=false;
        try{original.call(packet);normal=true;}finally{early$finish(call,normal);}
    }
    public boolean earlyLoadoutMessageHistoryKnown(){var c=Minecraft.getInstance();return !early$unknown&&early$active==null&&c.isSameThread()&&c.getConnection()==(Object)this;}
    public List<Receipt> earlyLoadoutReceivedMessages(){return earlyLoadoutMessageHistoryKnown()?List.copyOf(early$messages):List.of();}
}
