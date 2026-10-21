package dev.veltraph.client.galatea;
import net.minecraft.client.Minecraft;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
/** Only real network ingress clocks; no delayed applied-TAIL approximation or new network activity. */
public final class McFigPacketGapObservation {
    private static final FigPacketGapRegistry JOURNAL=new FigPacketGapRegistry();
    private McFigPacketGapObservation(){}
    public static void received(Connection connection,long now){
        if(connection!=null&&connection.getReceiving()==PacketFlow.CLIENTBOUND)JOURNAL.received(connection,now);
    }
    public static boolean observed(Minecraft client){
        if(!client.isSameThread())throw new IllegalStateException("Packet gap query requires client thread");
        var listener=client.getConnection();if(listener==null)throw new IllegalStateException("Current native connection unavailable");
        return JOURNAL.observed(listener.getConnection(),System.currentTimeMillis());
    }
}
