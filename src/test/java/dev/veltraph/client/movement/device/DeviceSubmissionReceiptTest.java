package dev.veltraph.client.movement.device;
import dev.veltraph.client.world.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class DeviceSubmissionReceiptTest {
 private final Object parent=new Object(),p=new Object(),w=new Object(),c=new Object(),policy=new Object(),domain=new Object(),nonce=new Object();
 private DeviceUseReceipt.Context ctx(){return new DeviceUseReceipt.Context(parent,p,w,c,policy,2,7,8);}
 private DeviceUseReceipt.Issue issue(){return new DeviceUseReceipt.Issue(ctx(),domain,nonce,10,1000,2000,new Vec3(8,70,0),.30);}
 private DeviceSubmissionReceipt sent(){var r=new DeviceSubmissionReceipt(issue());r.open();assertTrue(r.submitted(ctx(),1001));r.close();r.returnedNormally();assertTrue(r.dispatched());return r;}
 @Test void exactSubmissionThenAppliedCreatesCapturedIssueReceipt(){var r=sent();r.observe(ctx(),11,1002,new Vec3(8,70,0));var a=r.applied();assertNotNull(a);assertSame(nonce,a.nonce());assertSame(domain,a.domain());assertEquals(1002,a.receivedAt());}
 @Test void noActualUseSubmissionCannotAcceptPosition(){var r=new DeviceSubmissionReceipt(issue());r.observe(ctx(),11,1002,new Vec3(8,70,0));assertNull(r.applied());assertTrue(r.revoked());}
 @Test void merelyOpeningWindowDoesNotAuthorizeArrival(){var r=new DeviceSubmissionReceipt(issue());r.open();r.observe(ctx(),11,1002,new Vec3(8,70,0));assertNull(r.applied());}
 @Test void noNormalUseReturnCannotComplete(){var r=new DeviceSubmissionReceipt(issue());r.open();r.submitted(ctx(),1001);r.close();r.observe(ctx(),11,1002,new Vec3(8,70,0));assertNull(r.applied());}
 @Test void packetOutsideWindowRevokes(){var r=new DeviceSubmissionReceipt(issue());assertFalse(r.submitted(ctx(),1001));assertTrue(r.revoked());}
 @Test void foreignSubmissionContextRevokes(){var r=new DeviceSubmissionReceipt(issue());r.open();assertFalse(r.submitted(new DeviceUseReceipt.Context(parent,p,new Object(),c,policy,2,7,8),1001));}
 @Test void duplicateOutgoingUseDenied(){var r=new DeviceSubmissionReceipt(issue());r.open();assertTrue(r.submitted(ctx(),1001));assertFalse(r.submitted(ctx(),1002));r.returnedNormally();assertFalse(r.dispatched());}
 @Test void positionBeforeActualSubmissionDenied(){var r=sent();r.observe(ctx(),11,1000,new Vec3(8,70,0));assertNull(r.applied());}
 @Test void wrongPositionPacketRevokesInsteadOfLaterSuccess(){var r=sent();r.observe(ctx(),11,1002,new Vec3(5,70,0));r.observe(ctx(),12,1003,new Vec3(8,70,0));assertNull(r.applied());}
 @Test void foreignReceivedContextRevokes(){var r=sent();r.observe(new DeviceUseReceipt.Context(parent,p,w,new Object(),policy,2,7,8),11,1002,new Vec3(8,70,0));assertNull(r.applied());}
 @Test void lateReceiveDoesNotExtendOriginalDeadline(){var r=sent();r.observe(ctx(),11,2000,new Vec3(8,70,0));assertNull(r.applied());}
 @Test void staleAppliedSerialDenied(){var r=sent();r.observe(ctx(),10,1002,new Vec3(8,70,0));assertNull(r.applied());}
 @Test void beyondTightPointThreeDenied(){var r=sent();r.observe(ctx(),11,1002,new Vec3(8.31,70,0));assertNull(r.applied());}
}
