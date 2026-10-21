package dev.veltraph.client.navigation;
import dev.veltraph.client.world.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ExpectedTeleportTest {
    @Test void anInvalidDestinationCannotLeaveAnOlderReceiptArmed() {
        var token=new ExpectedTeleport();var target=new Vec3(10,70,0);token.arm(target,1000,5000);
        assertThrows(IllegalArgumentException.class,()->token.arm(new Vec3(Double.NaN,70,0),1100,5000));
        assertFalse(token.consume(target,1200));
    }
    @Test void invalidPositionCannotConsumeAnAcknowledgment() {
        var token=new ExpectedTeleport();var target=new Vec3(10,70,0);token.arm(target,1000,5000);
        assertFalse(token.consume(new Vec3(Double.NaN,70,0),1100));assertTrue(token.consume(target,1200));
    }
    @Test void acceptsOnlyOneMatchingArrivalAndNeverAnotherDestination() {
        var token=new ExpectedTeleport();var target=new Vec3(10,70,0);
        token.arm(target,1000,5000);
        assertFalse(token.consume(new Vec3(30,70,0),1100));
        assertTrue(token.consume(target,1200));assertFalse(token.consume(target,1300));
    }
    @Test void expiryCancellationAndClockReversalInvalidateTheReceipt() {
        var token=new ExpectedTeleport();var target=new Vec3(10,70,0);
        token.arm(target,1000,500);assertFalse(token.consume(target,1501));
        token.arm(target,2000,5000);token.clear();assertFalse(token.consume(target,2100));
        token.arm(target,3000,5000);assertFalse(token.consume(target,2999));
    }
}
