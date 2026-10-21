package dev.veltraph.client.service.mc;
/** Passive actual setter history. No mutable version object or camera permission is exposed. */
public interface OwnedPoseHistoryCarrier {
 Object ownedPoseHistorySnapshot();
 boolean ownedPoseHistoryCurrent(Object exact);
 boolean ownedPoseHistoryAdvanced(Object before,Object after,java.util.List<?> actualYaw,java.util.List<?> actualPitch);
}
