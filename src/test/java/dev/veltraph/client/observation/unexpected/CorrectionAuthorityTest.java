package dev.veltraph.client.observation.unexpected;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static dev.veltraph.client.observation.unexpected.UnexpectedBehaviorMonitor.*;

/** Packet application supersedes older camera claims, including claims in the same clock millisecond. */
final class CorrectionAuthorityTest {
    private final Context helper = new Context(new Activation("maintenance_helper", 7, true), 1, 1, 1);
    private final UnexpectedBehaviorMonitor monitor = new UnexpectedBehaviorMonitor(f -> {});
    private Finding rotation(long now) {
        return monitor.observe(new Observation(helper, Kind.ROTATION, Value.rotation(0, 0), Value.rotation(90, 0), now));
    }

    @Test void serverCorrectionCannotBorrowEarlierHelperCameraTarget() {
        monitor.intent(helper, Kind.ROTATION, Value.rotation(90, 0), .01, 100, 250);
        monitor.signal(helper, Kind.ROTATION, Cause.SERVER_POSITION_PACKET, 101, 150);
        var finding = rotation(102);
        assertEquals(Classification.UNEXPECTED, finding.classification());
        assertTrue(finding.evidence().contains(Cause.SERVER_POSITION_PACKET));
        assertFalse(finding.evidence().contains(Cause.MOD_INTENT));
        assertEquals(0, monitor.pendingIntents());
    }

    @Test void dispatchOrderRemainsAuthoritativeWithinOneMillisecond() {
        monitor.intent(helper, Kind.ROTATION, Value.rotation(90, 0), .01, 100, 250);
        monitor.signal(helper, Kind.ROTATION, Cause.SERVER_POSITION_PACKET, 100, 150);
        assertEquals(Classification.UNEXPECTED, rotation(100).classification());
    }

    @Test void freshModWriteAfterAppliedCorrectionCanStillAcknowledgeItsTargetOnce() {
        monitor.signal(helper, Kind.ROTATION, Cause.SERVER_POSITION_PACKET, 100, 150);
        monitor.intent(helper, Kind.ROTATION, Value.rotation(90, 0), .01, 100, 250);
        assertEquals(Classification.EXPLAINED, rotation(100).classification());
        assertEquals(Classification.UNEXPECTED, rotation(101).classification());
    }

    @Test void oldNativeMouseReceiptCannotReappearWhenPacketEvidenceExpires() {
        monitor.nativeReceipt(helper, Kind.ROTATION, Cause.NATIVE_MOUSE_EVENT, Value.rotation(90, 0), .01, 100, 250);
        monitor.signal(helper, Kind.ROTATION, Cause.SERVER_POSITION_PACKET, 101, 1);
        assertEquals(Classification.UNEXPECTED, rotation(103).classification());
    }

    @Test void returnLandingContractSurvivesRotationCorrectionAndOtherPacketKinds() {
        monitor.intent(helper, Kind.POSITION, Value.position(40, 0, 0), .5, 100, 1000);
        monitor.signal(helper, Kind.POSITION, Cause.SERVER_POSITION_PACKET, 101, 150);
        monitor.signal(helper, Kind.ROTATION, Cause.SERVER_POSITION_PACKET, 101, 150);
        assertEquals(Classification.EXPLAINED, monitor.observe(new Observation(helper, Kind.POSITION,
                Value.position(0, 0, 0), Value.position(40, 0, 0), 102)).classification());
        monitor.intent(helper, Kind.ROTATION, Value.rotation(90, 0), .01, 103, 250);
        monitor.signal(helper, Kind.GAMEMODE, Cause.SERVER_GAME_MODE_PACKET, 104, 150);
        assertEquals(Classification.EXPLAINED, rotation(105).classification());
    }
}
