package dev.veltraph.client.navigation;
/** Read-only opaque native transport history; no mutation, packet receipt or cache setter API. */
public interface NativeCarryPacketOrder {
    NativeCarryPublicationWitness.PacketHistory.Snapshot veltraph$nativeCarryPacketOrder();
}
