package dev.veltraph.client.movement.device;
import dev.veltraph.client.rotation.RotationReceiptIdentity;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class DeviceCleanupIdentityTest {
 private final Object s=new Object(),o=new Object(),p=new Object(),w=new Object(),c=new Object();
 private RotationReceiptIdentity.Stamp stamp(long generation){return new RotationReceiptIdentity.Stamp(s,generation,o,p,w,c,8);}
 @Test void sameParentNewRequestSameEpochCannotBeCancelled(){assertFalse(RotationReceiptIdentity.current(stamp(2),stamp(3),p,w,c,o,false));}
 @Test void cleanupIdentityDoesNotRequireGameplayGroundState(){assertTrue(RotationReceiptIdentity.current(stamp(2),stamp(2),p,w,c,o,false));}
 @Test void foreignContextNeverGetsAirborneCleanup(){assertFalse(RotationReceiptIdentity.current(stamp(2),stamp(2),p,new Object(),c,o,false));}
}
