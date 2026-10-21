package dev.veltraph.client.mining;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/** Executes the exact native hardware effect producer with independent key-state receipts. */
class RootUseKeyEffectsTest {
    static final class Key {boolean down;}
    final Key key=new Key();final List<Boolean> writes=new ArrayList<>();
    final Object owner=new Object(),p=new Object(),w=new Object(),g=new Object();
    final MiningUseHoldLease lease=new MiningUseHoldLease(owner,7,p,w,g,"cobblestone");
    final RootUseKeyEffects<Key> fx=new RootUseKeyEffects<>((k,v)->{writes.add(v);k.down=v;},k->k.down);
    @Test void sameLiveOwnerReleasesHardwareForEachIndependentContextReplacement(){
        for(int changed=0;changed<3;changed++){
            fx.acquire(lease,key);assertTrue(key.down);
            assertTrue(fx.release(owner,7,changed==0?new Object():p,changed==1?new Object():w,changed==2?new Object():g,owner,"cobblestone"));
            assertFalse(key.down);assertFalse(fx.hasLease());
        }
        assertEquals(List.of(true,false,true,false,true,false),writes);
    }
    @Test void detachedStoppedActivationReleasesItsCapturedMapping(){
        fx.acquire(lease,key);assertTrue(fx.release(null,7,null,null,null,null,null));
        assertFalse(key.down);assertEquals(List.of(true,false),writes);
    }
    @Test void foreignOwnerRetiresWithoutClaimingReleaseAndCannotResurrectOldWrites(){
        fx.acquire(lease,key);var foreign=new Object();
        assertFalse(fx.release(foreign,7,p,w,g,owner,"cobblestone"));assertTrue(fx.retired());assertTrue(key.down);assertEquals(List.of(true),writes);
        assertFalse(fx.release(owner,7,p,w,g,owner,"cobblestone"));assertTrue(key.down);
        key.down=false;assertTrue(fx.release(owner,7,p,w,g,owner,"cobblestone"));assertFalse(fx.hasLease());assertEquals(List.of(true),writes);
    }
    @Test void sameIdRegistryReplacementAndNewRunKeepNewHardwareHold(){
        fx.acquire(lease,key);assertFalse(fx.release(null,7,p,w,g,new Object(),"cobblestone"));assertTrue(key.down);
        fx.acquire(new MiningUseHoldLease(owner,8,p,w,g,"cobblestone"),key);assertFalse(fx.ownedBy(owner,7));assertTrue(fx.ownedBy(owner,8));
        assertTrue(key.down);assertEquals(List.of(true),writes);assertTrue(fx.release(owner,8,p,w,g,owner,"cobblestone"));assertFalse(key.down);
    }
    @Test void foreignCameraRetiresWithoutReleasingNewHelperUse(){
        fx.acquire(lease,key);assertFalse(fx.release(owner,7,p,w,g,owner,"foreign_camera_authority"));assertTrue(key.down);assertEquals(List.of(true),writes);
    }
    @Test void independentNativeClearIsReconciledAndValidResumeReholdsExactlyOnce(){
        fx.acquire(lease,key);fx.acquire(lease,key);assertEquals(List.of(true),writes);
        key.down=false;assertFalse(fx.ownedHeld(owner,7));fx.acquire(lease,key);fx.acquire(lease,key);
        assertTrue(key.down);assertEquals(List.of(true,true),writes);assertTrue(fx.release(owner,7,p,w,g,owner,"cobblestone"));assertEquals(List.of(true,true,false),writes);
    }
    @Test void releaseWriterWithoutAnUpReceiptKeepsLeaseAndReportsFalse(){
        var refusal=new RootUseKeyEffects<Key>((k,v)->{if(v)k.down=true;},k->k.down);refusal.acquire(lease,key);
        assertFalse(refusal.release(owner,7,p,w,g,owner,"cobblestone"));assertTrue(refusal.hasLease());assertFalse(refusal.retired());assertTrue(key.down);
    }
    @Test void exactAcquisitionPredicateRejectsStaleControllerActivationLockAndCamera(){
        assertTrue(MiningUseHoldLease.mayAcquire(owner,7,owner,7,owner,"cobblestone",owner));
        assertFalse(MiningUseHoldLease.mayAcquire(new Object(),7,owner,7,owner,"cobblestone",owner));
        assertFalse(MiningUseHoldLease.mayAcquire(owner,6,owner,7,owner,"cobblestone",owner));
        assertFalse(MiningUseHoldLease.mayAcquire(owner,7,owner,7,new Object(),"cobblestone",owner));
        assertFalse(MiningUseHoldLease.mayAcquire(owner,7,owner,7,owner,"fishing",owner));
        assertFalse(MiningUseHoldLease.mayAcquire(owner,7,owner,7,owner,"cobblestone",new Object()));
    }
    @Test void retiredSameRunStillDownCannotReacquireOrCancelAfterAuthorityReturns(){
        assertTrue(fx.acquire(lease,key));fx.retireOwned(owner,7);assertTrue(fx.retired());
        for(int i=0;i<8;i++)assertFalse(fx.acquire(lease,key));
        assertTrue(key.down);assertTrue(fx.retired());assertFalse(fx.release(owner,7,p,w,g,owner,"cobblestone"));assertEquals(List.of(true),writes);
        key.down=false;assertTrue(fx.acquire(lease,key));assertFalse(fx.retired());assertTrue(fx.ownedHeld(owner,7));assertEquals(List.of(true,true),writes);
        assertTrue(fx.release(owner,7,p,w,g,owner,"cobblestone"));assertEquals(List.of(true,true,false),writes);
    }
    @Test void foreignOldRequestCannotRetireCurrentActivationLease(){
        assertTrue(fx.acquire(lease,key));fx.retireOwned(new Object(),7);fx.retireOwned(owner,6);
        assertFalse(fx.retired());assertTrue(fx.ownedHeld(owner,7));assertEquals(List.of(true),writes);
    }

}
