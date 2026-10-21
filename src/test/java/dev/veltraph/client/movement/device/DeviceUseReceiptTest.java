package dev.veltraph.client.movement.device;
import dev.veltraph.client.world.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class DeviceUseReceiptTest {
 private final Object parent=new Object(),p=new Object(),w=new Object(),c=new Object(),policy=new Object(),domain=new Object(),nonce=new Object();
 private final Vec3 goal=new Vec3(8,70,0);
 private DeviceUseReceipt.Context ctx(){return new DeviceUseReceipt.Context(parent,p,w,c,policy,4,1,8);}
 private DeviceUseReceipt armed(){var r=new DeviceUseReceipt();assertTrue(r.arm(new DeviceUseReceipt.Issue(ctx(),domain,nonce,20,1000,2000,goal,.89)));return r;}
 private DeviceUseReceipt.Applied applied(DeviceUseReceipt.Context ctx,Object d,Object n,long serial,long at,Vec3 pos){return new DeviceUseReceipt.Applied(ctx,d,n,serial,at,pos);}
 @Test void newerAppliedReceiptConfirmsOnce(){var r=armed();var a=applied(ctx(),domain,nonce,21,1001,goal);assertTrue(r.observe(ctx(),a,1001));assertFalse(r.observe(ctx(),a,1002));assertEquals(DeviceUseReceipt.State.APPLIED,r.state());}
 @Test void mereActualPositionCannotComplete(){var r=armed();assertFalse(r.observe(ctx(),null,1100));assertEquals(DeviceUseReceipt.State.WAITING,r.state());}
 @Test void staleSerialDenied(){assertFalse(armed().observe(ctx(),applied(ctx(),domain,nonce,20,1100,goal),1100));}
 @Test void wrongReceiptDomainDenied(){assertFalse(armed().observe(ctx(),applied(ctx(),new Object(),nonce,21,1100,goal),1100));}
 @Test void wrongNonceDenied(){assertFalse(armed().observe(ctx(),applied(ctx(),domain,new Object(),21,1100,goal),1100));}
 @Test void preUseReceiptDenied(){assertFalse(armed().observe(ctx(),applied(ctx(),domain,nonce,21,999,goal),1100));}
 @Test void futureReceiptDenied(){assertFalse(armed().observe(ctx(),applied(ctx(),domain,nonce,21,1101,goal),1100));}
 @Test void distantReceiptDenied(){assertFalse(armed().observe(ctx(),applied(ctx(),domain,nonce,21,1100,new Vec3(7,70,0)),1100));}
 @Test void changedWorldRevokes(){var r=armed();var foreign=new DeviceUseReceipt.Context(parent,p,new Object(),c,policy,4,1,8);assertFalse(r.observe(foreign,applied(foreign,domain,nonce,21,1100,goal),1100));assertEquals(DeviceUseReceipt.State.REVOKED,r.state());}
 @Test void cameraGenerationRevokes(){var r=armed();var newer=new DeviceUseReceipt.Context(parent,p,w,c,policy,4,1,9);assertFalse(r.validate(newer,1100));assertEquals(DeviceUseReceipt.State.REVOKED,r.state());}
 @Test void configABARevisionRevokes(){var r=armed();assertFalse(r.validate(new DeviceUseReceipt.Context(parent,p,w,c,policy,6,1,8),1100));}
 @Test void policyReplacementRevokes(){assertFalse(armed().validate(new DeviceUseReceipt.Context(parent,p,w,c,new Object(),4,1,8),1100));}
 @Test void journeyReplacementRevokes(){assertFalse(armed().validate(new DeviceUseReceipt.Context(parent,p,w,c,policy,4,2,8),1100));}
 @Test void deadlineWinsOverArrival(){var r=armed();assertFalse(r.observe(ctx(),applied(ctx(),domain,nonce,21,2000,goal),2000));assertEquals(DeviceUseReceipt.State.TIMED_OUT,r.state());}
 @Test void rollbackRevokes(){var r=armed();assertTrue(r.validate(ctx(),1200));assertFalse(r.validate(ctx(),1199));assertEquals(DeviceUseReceipt.State.REVOKED,r.state());}
 @Test void revokedCannotRearm(){var r=armed();r.revoke();assertFalse(r.arm(new DeviceUseReceipt.Issue(ctx(),domain,nonce,20,1000,2000,goal,.89)));}
 @Test void nonfiniteReceiptDenied(){assertFalse(armed().observe(ctx(),applied(ctx(),domain,nonce,21,1100,new Vec3(Double.NaN,70,0)),1100));}
 @Test void invalidAdmissionValuesThrow(){assertThrows(IllegalArgumentException.class,()->new DeviceUseReceipt.Issue(ctx(),domain,nonce,20,1000,2000,goal,2));}
}
