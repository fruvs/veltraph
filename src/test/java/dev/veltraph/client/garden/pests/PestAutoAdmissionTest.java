package dev.veltraph.client.garden.pests;

import dev.veltraph.client.config.PestConfig;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

final class PestAutoAdmissionTest {
    private final PestAutoAdmission.Scope scope=new PestAutoAdmission.Scope(new Object(),new Object(),new Object(),"farm",1);
    private PestAutoAdmission.Snapshot snapshot(Integer total,int entityExclusions,int plotExclusions,int scheduled,int completed,Boolean blocked) {
        return new PestAutoAdmission.Snapshot(scope,10,new PestAutoAdmission.Counts(total,entityExclusions,plotExclusions,scheduled,completed),blocked);
    }
    private boolean ready(Integer total,int entityExclusions,int plotExclusions,int scheduled,int completed,boolean leaveOne) {
        return PestAutoAdmission.ready(snapshot(total,entityExclusions,plotExclusions,scheduled,completed,false),scope,10,leaveOne);
    }
    @Test void sourceDefaultIsFalse(){assertFalse(PestConfig.LEAVE_ONE_PEST.defaultValue());assertEquals("LEAVE_ONE_PEST",PestConfig.LEAVE_ONE_PEST.name());}
    @Test void twoCountsAtOneAreRejectedWithLeaveOne(){assertFalse(ready(1,0,0,1,0,true));}
    @Test void twoCountsAtTwoAreAdmittedWithLeaveOne(){assertTrue(ready(2,0,0,2,0,true));}
    @Test void availableOnePendingTwoIsRejected(){assertFalse(ready(1,0,0,2,0,true));}
    @Test void availableTwoPendingOneIsRejected(){assertFalse(ready(2,0,0,1,0,true));}
    @Test void disablingRetentionAdmitsOneInBothCounts(){assertTrue(ready(1,0,0,1,0,false));}
    @Test void zeroAvailableOrPendingNeverAdmits(){assertFalse(ready(0,0,0,2,0,false));assertFalse(ready(2,0,0,2,2,false));}
    @Test void exclusionsSubtractBothIndependentSetSizes(){assertFalse(ready(3,1,1,4,0,true));assertTrue(ready(4,1,1,4,0,true));}
    @Test void adjustedAvailableRatherThanNativeTotalControlsThreshold(){assertFalse(ready(4,2,1,5,0,true));assertTrue(ready(4,2,1,5,0,false));}
    @Test void completionOffsetRatherThanTargetTotalControlsSecondThreshold(){assertFalse(ready(8,0,0,8,7,true));assertTrue(ready(8,0,0,8,7,false));}
    @Test void unavailableNativeCountCannotAuthorize(){assertFalse(ready(null,0,0,2,0,false));}
    @Test void negativeSignedNativeOrParentCountCannotAuthorize(){assertFalse(ready(-1,0,0,2,0,false));assertFalse(ready(2,0,0,-1,0,false));}
    @Test void invalidNegativeOffsetsCannotIncreaseAdmission(){assertFalse(ready(1,-1,0,2,0,true));assertFalse(ready(1,0,-1,2,0,true));assertFalse(ready(2,0,0,1,-1,true));}
    @Test void overSubtractionNeverWrapsToPositiveAdmission(){assertFalse(ready(1,Integer.MAX_VALUE,Integer.MAX_VALUE,2,0,false));assertFalse(ready(2,0,0,1,Integer.MAX_VALUE,false));}
    @Test void sourceBlockedAndUnknownFlagsCannotAuthorize(){assertFalse(PestAutoAdmission.ready(snapshot(2,0,0,2,0,true),scope,10,true));assertFalse(PestAutoAdmission.ready(snapshot(2,0,0,2,0,null),scope,10,true));}
    @Test void everyNativeIdentityActualProfileAndSelectedPlotMustMatch(){
        var observed=snapshot(2,0,0,2,0,false);
        for(var changed:List.of(new PestAutoAdmission.Scope(new Object(),scope.world(),scope.connection(),scope.profile(),1),
                new PestAutoAdmission.Scope(scope.player(),new Object(),scope.connection(),scope.profile(),1),
                new PestAutoAdmission.Scope(scope.player(),scope.world(),new Object(),scope.profile(),1),
                new PestAutoAdmission.Scope(scope.player(),scope.world(),scope.connection(),"other-farm",1),
                new PestAutoAdmission.Scope(scope.player(),scope.world(),scope.connection(),scope.profile(),2)))
            assertEquals(PestAutoAdmission.Decision.CONTEXT_CHANGED,PestAutoAdmission.decision(observed,changed,10,true));
    }
    @Test void pastFutureAndInvalidTicksCannotUseCachedObservation(){var observed=snapshot(2,0,0,2,0,false);for(long tick:new long[]{9,11,-1})assertEquals(PestAutoAdmission.Decision.STALE,PestAutoAdmission.decision(observed,scope,tick,true));}
    @Test void stoppedUnboundOrInvalidPlotContextsCannotAuthorize(){assertFalse(PestAutoAdmission.ready(snapshot(2,0,0,2,0,false),null,10,true));for(int plot:new int[]{0,25})assertFalse(PestAutoAdmission.ready(snapshot(2,0,0,2,0,false),new PestAutoAdmission.Scope(scope.player(),scope.world(),scope.connection(),scope.profile(),plot),10,true));}
    @Test void previewIsReadOnlyAndDoesNotConsumeCounts(){var observed=snapshot(2,0,0,2,0,false);for(int i=0;i<10;i++)assertTrue(PestAutoAdmission.ready(observed,scope,10,true));assertEquals(2,observed.counts().available());assertEquals(2,observed.counts().pending());}
    @Test void missingSnapshotOrCountsCannotAuthorize(){assertFalse(PestAutoAdmission.ready(null,scope,10,true));assertFalse(PestAutoAdmission.ready(new PestAutoAdmission.Snapshot(scope,10,null,false),scope,10,true));}
}
