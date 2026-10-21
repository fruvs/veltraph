package dev.veltraph.client.rewarp.mc;

import java.util.Objects;
import java.util.function.BooleanSupplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundChatCommandPacket;
import net.minecraft.network.protocol.game.ServerboundChatCommandSignedPacket;

/** One private saved-plot command invocation. Refusal only; no connection creation or server ACK. */
public final class SavedAnchorCommandFence {
    private static final ThreadLocal<Scope> ACTIVE=new ThreadLocal<>();
    private static final class Scope {
        final Minecraft client;final ClientPacketListener listener;final Connection connection;final String command;
        final BooleanSupplier finalNativeVeto;final Thread thread=Thread.currentThread();
        Packet<?> packet;boolean submitted,refused;
        Scope(Minecraft client,ClientPacketListener listener,Connection connection,String command,BooleanSupplier veto){this.client=client;this.listener=listener;this.connection=connection;this.command=command;finalNativeVeto=veto;}
        boolean current(){
            if(refused||Thread.currentThread()!=thread||!client.isSameThread()||client.getConnection()!=listener
                    ||listener.getConnection()!=connection)return false;
            try{return finalNativeVeto.getAsBoolean()&&client.getConnection()==listener&&listener.getConnection()==connection;}
            catch(RuntimeException unavailable){return false;}
        }
    }
    private SavedAnchorCommandFence(){}
    static boolean send(Minecraft client,ClientPacketListener listener,Connection connection,String command,BooleanSupplier finalNativeVeto){
        Objects.requireNonNull(finalNativeVeto);
        if(listener==null||connection==null||command==null||!command.matches("tptoplot [1-9][0-9]?"))return false;
        var scope=new Scope(client,listener,connection,command,finalNativeVeto);if(!scope.current())return false;
        var prior=ACTIVE.get();ACTIVE.set(scope);
        try{listener.sendCommand(command);return scope.submitted&&!scope.refused&&scope.current();}
        finally{if(prior==null)ACTIVE.remove();else ACTIVE.set(prior);}
    }
    /** Exact immutable packet is bound before Connection.flushQueue can call arbitrary consumers. */
    public static void commandEntered(Object listener,Packet<?> packet){
        var scope=ACTIVE.get();if(scope==null||scope.listener!=listener||scope.packet!=null)return;
        String command=packet instanceof ServerboundChatCommandPacket plain?plain.command()
                :packet instanceof ServerboundChatCommandSignedPacket signed?signed.command():null;
        if(scope.command.equals(command))scope.packet=packet;
    }
    /** Connected branch's final synchronous leaf, after flushQueue and before actual sendPacket. */
    public static boolean allowSubmission(Connection connection,Packet<?> packet){
        var scope=ACTIVE.get();if(scope==null||scope.connection!=connection||scope.packet!=packet)return true;
        if(scope.submitted||!scope.current()){scope.refused=true;return false;}
        scope.submitted=true;return true;
    }
    /** A disconnected captured request cannot be queued for a future, unobserved context. */
    public static boolean allowEnqueue(Connection connection,Packet<?> packet){
        var scope=ACTIVE.get();if(scope==null||scope.connection!=connection||scope.packet!=packet)return true;
        scope.refused=true;return false;
    }
}
