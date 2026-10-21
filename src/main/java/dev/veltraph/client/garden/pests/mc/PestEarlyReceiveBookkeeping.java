package dev.veltraph.client.garden.pests.mc;

import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import dev.veltraph.client.garden.pests.mc.PestEarlyMessageCarrier.Receipt;

/** Bytecode data only. Public copies cannot enter the private injected native history or grant effects. */
public final class PestEarlyReceiveBookkeeping {
    private PestEarlyReceiveBookkeeping(){}
    /** Public bytecode bookkeeping type for the transformed target. Copies cannot enter the private native ring. */
    public static final class NativeReceipt implements Receipt {
        private final Object receiver,scope;private final long serial,time;private final String message;
        public NativeReceipt(Object receiver,Object scope,long serial,long time,String message){this.receiver=receiver;this.scope=scope;this.serial=serial;this.time=time;this.message=message;}
        public Object receiver(){return receiver;}public Object observationScope(){return scope;}public long serial(){return serial;}public long observedMillis(){return time;}public String message(){return message;}
    }
    /** Never exposed by a carrier API; public access avoids cross-nest calls from transformed target code. */
    public static final class Call {
        public final Minecraft client;public final Object player,world,gameMode,listener,scope;public final Thread thread;public final long time;public final ClientboundSystemChatPacket packet;public final net.minecraft.network.chat.Component component;public String message;public boolean revoked;
        public Call(Minecraft c,Object player,Object world,Object gameMode,Object listener,Object scope,ClientboundSystemChatPacket packet,net.minecraft.network.chat.Component component,long time){client=c;this.player=player;this.world=world;this.gameMode=gameMode;this.listener=listener;this.scope=scope;thread=Thread.currentThread();this.packet=packet;this.component=component;this.time=time;}
        public boolean current(){return Thread.currentThread()==thread&&client.isSameThread()&&client.player==player&&client.level==world&&client.gameMode==gameMode&&client.getConnection()==listener&&dev.veltraph.client.VeltraphBootstrap.earlyLoadoutReceiptScope()==scope;}
    }
}
