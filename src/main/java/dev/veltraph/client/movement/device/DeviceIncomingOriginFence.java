package dev.veltraph.client.movement.device;
/** Historical ordering veto only; this class cannot create a Session, native body, issue or ACK receipt. */
final class DeviceIncomingOriginFence {
 private DeviceIncomingOriginFence(){}
 static boolean validBaseline(long baseline){return baseline>=0&&baseline<Long.MAX_VALUE;}
 static boolean strictlyAfter(long baseline,long actualOrigin){return validBaseline(baseline)&&actualOrigin>baseline;}
}
