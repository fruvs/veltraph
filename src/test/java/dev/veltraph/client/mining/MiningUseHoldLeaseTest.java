package dev.veltraph.client.mining;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** The exact release predicate used by McMiningServices, with independent custody changes. */
class MiningUseHoldLeaseTest {
    final Object owner=new Object(),player=new Object(),world=new Object(),mode=new Object();
    final MiningUseHoldLease lease=new MiningUseHoldLease(owner,4,player,world,mode,"cobblestone");
    @Test void currentOwnerAndManualDisableCanReleaseOnlyCapturedGeneration(){
        assertTrue(lease.mayRelease(owner,4,player,world,mode,owner,"cobblestone"));
        assertTrue(lease.mayRelease(null,4,player,world,mode,owner,"cobblestone"));
        assertTrue(lease.mayRelease(null,4,player,world,mode,owner,null));
        assertFalse(lease.mayRelease(owner,5,player,world,mode,owner,"cobblestone"));
    }
    @Test void foreignOwnerAndRegistryReplacementCannotBeReleasedEvenWithSameId(){
        var newer=new Object();assertFalse(lease.mayRelease(newer,4,player,world,mode,owner,"cobblestone"));
        assertFalse(lease.mayRelease(null,4,player,world,mode,newer,"cobblestone"));
        assertTrue(lease.mayRelease(null,4,player,world,mode,null,"cobblestone"));
        assertFalse(lease.mayRelease(null,4,player,world,mode,owner,"fishing"));
    }
    @Test void sameLiveActivationReleasesAfterPlayerWorldOrGameModeReplacement(){
        assertTrue(lease.mayRelease(owner,4,new Object(),world,mode,owner,"cobblestone"));
        assertTrue(lease.mayRelease(owner,4,player,new Object(),mode,owner,"cobblestone"));
        assertTrue(lease.mayRelease(owner,4,player,world,new Object(),owner,"cobblestone"));
        // Disconnected, stopped old owner must release its captured hardware mapping.
        assertTrue(lease.mayRelease(null,4,null,null,null,owner,null));
        assertTrue(lease.mayRelease(null,4,new Object(),new Object(),new Object(),null,null));
        assertFalse(lease.mayRelease(new Object(),4,null,null,null,owner,null));
        assertFalse(lease.mayRelease(null,4,null,null,null,new Object(),"cobblestone"));
    }
}
