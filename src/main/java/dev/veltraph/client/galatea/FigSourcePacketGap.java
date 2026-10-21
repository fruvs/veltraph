package dev.veltraph.client.galatea;

/** Passive readable packet-gap predicate only. No packet limiting, traffic or telemetry.
 * Feed real incoming packet-event timestamps; source initialization is0 for both clocks.
 */
public final class FigSourcePacketGap {
    private long lastPacketAt,lastDetectedGapAt;
    public void received(long now) {
        if(now-lastPacketAt>500)lastDetectedGapAt=now;
        lastPacketAt=now;
    }
    public boolean observed(long now){return now-lastPacketAt>500 || now-lastDetectedGapAt<100;}
    public long lastPacketAt(){return lastPacketAt;}
    public long lastDetectedGapAt(){return lastDetectedGapAt;}
}
