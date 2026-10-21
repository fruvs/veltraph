package dev.veltraph.client.galatea;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** STAGED, uncompiled/unrun: source history must survive the native inner queue refusal. */
class OwnedNativeUseAdmissionTest {
    private final Object owner=new Object();
    private OwnedNativeUseTicket.Frame frame(boolean healthy){return new OwnedNativeUseTicket.Frame(owner,1,new Object(),new Object(),new Object(),
            8,List.of("full loadout"),"cfg",owner,owner,0,1000,100,healthy);}
    @Test void unhealthyOuterPermissionDoesNotRecordThrottleHistory(){var order=new ArrayList<String>();assertFalse(OwnedNativeUseAdmission.ready(frame(false),1000,()->{order.add("history");return true;},()->{order.add("pending");return 0;},()->{order.add("inner");return true;}));assertTrue(order.isEmpty());}
    @Test void pendingManualClickRefusesInnerAfterSourceHistoryAlreadyRecords(){var order=new ArrayList<String>();assertFalse(OwnedNativeUseAdmission.ready(frame(true),1000,()->{order.add("history");return true;},()->{order.add("pending");return 1;},()->{order.add("inner");return true;}));assertEquals(List.of("history","pending"),order);}
    @Test void refusedSourceThrottleNeverReadsOrMutatesQueue(){var order=new ArrayList<String>();assertFalse(OwnedNativeUseAdmission.ready(frame(true),1000,()->{order.add("history");return false;},()->{order.add("pending");return 0;},()->true));assertEquals(List.of("history"),order);}
    @Test void unknownInnerReadinessStillRetainsRecordedHistory(){var order=new ArrayList<String>();assertFalse(OwnedNativeUseAdmission.ready(frame(true),1000,()->{order.add("history");return true;},()->{order.add("pending");return 0;},()->{order.add("inner");return false;}));assertEquals(List.of("history","pending","inner"),order);}
    @Test void permittedQueueOrderIsOuterThenHistoryThenNativeInner(){var order=new ArrayList<String>();assertTrue(OwnedNativeUseAdmission.ready(frame(true),1000,()->{order.add("history");return true;},()->{order.add("pending");return 0;},()->{order.add("inner");return true;}));assertEquals(List.of("history","pending","inner"),order);}
}
