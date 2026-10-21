package dev.veltraph.client.combat.heal;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class HealCameraLeaseTest {
 @Test void oldSnapshotCannotRevokeSamePublisherNewRequest(){Object p=new Object(),w=new Object(),c=new Object(),owner=new Object();var old=new HealCameraLease(p,w,c,owner,10);var replacement=new HealCameraLease(p,w,c,owner,11);assertTrue(old.owns(old));assertFalse(old.owns(replacement));}
 @Test void contextAndAuthorityReplacementCannotBeRevoked(){Object p=new Object(),w=new Object(),c=new Object(),o=new Object();var old=new HealCameraLease(p,w,c,o,1);for(var newer:new HealCameraLease[]{new HealCameraLease(new Object(),w,c,o,1),new HealCameraLease(p,new Object(),c,o,1),new HealCameraLease(p,w,new Object(),o,1),new HealCameraLease(p,w,c,new Object(),1)})assertFalse(old.owns(newer));}
}
