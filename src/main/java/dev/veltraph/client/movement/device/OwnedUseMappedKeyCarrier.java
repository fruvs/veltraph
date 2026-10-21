package dev.veltraph.client.movement.device;
/** Read-only actual native mapping history. Private native snapshots are additional vetoes, never input permission. */
public interface OwnedUseMappedKeyCarrier { Object ownedUseMappingSnapshot();boolean ownedUseMappingCurrent(Object exact); }
