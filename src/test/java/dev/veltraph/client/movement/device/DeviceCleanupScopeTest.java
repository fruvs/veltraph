package dev.veltraph.client.movement.device;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class DeviceCleanupScopeTest {
 private final Object o=new Object(),p=new Object(),w=new Object(),c=new Object(),store=new Object();
 private DeviceCleanupScope.Scope old(){return new DeviceCleanupScope.Scope(o,p,w,c,store,4);}
 @Test void sameJourneyRemainsCleanupEligibleWithoutGameplayRevisionOrGround(){assertTrue(DeviceCleanupScope.same(old(),old()));}
 @Test void replacementJourneySameParentCannotUndoNewWork(){assertFalse(DeviceCleanupScope.same(old(),new DeviceCleanupScope.Scope(o,p,w,c,store,5)));}
 @Test void replacementStoreCannotUndoNewWork(){assertFalse(DeviceCleanupScope.same(old(),new DeviceCleanupScope.Scope(o,p,w,c,new Object(),4)));}
 @Test void foreignWorldCannotUndoCapturedWork(){assertFalse(DeviceCleanupScope.same(old(),new DeviceCleanupScope.Scope(o,p,new Object(),c,store,4)));}
 @Test void missingContextCannotGrantCleanup(){assertFalse(DeviceCleanupScope.same(old(),new DeviceCleanupScope.Scope(o,null,w,c,store,4)));}
}
