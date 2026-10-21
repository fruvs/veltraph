package dev.veltraph.client.rewarp;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FarmMaintenanceLeaseTest {
    @Test void preparedRollbackLeavesNoOwnerOrToken(){
        var lease=new FarmMaintenanceLease();var farm=new Object();var helper=new Object();
        var token=lease.prepare(farm,helper,0,1000);assertNotNull(token);assertEquals(FarmMaintenanceLease.State.PREPARED,lease.state());
        lease.rollback(token);assertFalse(lease.active());assertFalse(lease.owns(token));assertNull(lease.farm());
    }
    @Test void exactTokenAndExplicitReadyAreRequiredForResume(){
        var lease=new FarmMaintenanceLease();var farm=new Object();var helper=new Object();var token=lease.prepare(farm,helper,0,1000);
        assertTrue(lease.commit(token));assertFalse(lease.resume(token));assertTrue(lease.beginRestore(token));
        assertTrue(lease.beginReturn(token));assertFalse(lease.resume(token));assertTrue(lease.ready(token));
        assertTrue(lease.resume(token));assertFalse(lease.active());assertFalse(lease.resume(token));
        var next=lease.prepare(farm,helper,100,1000);assertNotSame(token,next);assertFalse(lease.owns(token));assertFalse(lease.commit(token));
    }
    @Test void competingOwnerAndStaleIdentityCancelWithoutResume(){
        var lease=new FarmMaintenanceLease();var farm=new Object();var helper=new Object();var token=lease.prepare(farm,helper,0,1000);
        assertNull(lease.prepare(new Object(),helper,1,1000));lease.commit(token);
        assertFalse(lease.validate(token,new Object(),helper,2,true));assertEquals(FarmMaintenanceLease.State.CANCELLED,lease.state());
        assertFalse(lease.resume(token));
    }
    @Test void totalDeadlineAndClockReversalCannotExtendOwnership(){
        for(boolean backwards:new boolean[]{false,true}){
            var lease=new FarmMaintenanceLease();var farm=new Object();var helper=new Object();var token=lease.prepare(farm,helper,100,1000);lease.commit(token);
            assertTrue(lease.validate(token,farm,helper,200,true));assertFalse(lease.validate(token,farm,helper,backwards?199:1100,true));
            assertEquals(FarmMaintenanceLease.State.CANCELLED,lease.state());assertFalse(lease.ready(token));
        }
    }
    @Test void helperDisableNeverMeansSuccessfulCompletion(){
        var lease=new FarmMaintenanceLease();var farm=new Object();var helper=new Object();var token=lease.prepare(farm,helper,0,1000);lease.commit(token);
        assertFalse(lease.validate(token,farm,helper,1,false));assertEquals(FarmMaintenanceLease.State.CANCELLED,lease.state());
        assertFalse(lease.resume(token));
    }
}
