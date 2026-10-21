package dev.veltraph.client.service.mc;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OwnedBreakingCommitFenceTest {
    @Test void unknownNativeReturnCannotEvaluateSourceOrCommit(){var calls=new ArrayList<String>();assertFalse(OwnedBreakingCommitFence.current(false,()->{calls.add("history");return true;},()->{calls.add("source");return true;},()->{calls.add("native");return true;}));assertTrue(calls.isEmpty());}
    @Test void foreignHistoryBeforeSourceRefusesWithoutProviderCallback(){var calls=new ArrayList<String>();assertFalse(OwnedBreakingCommitFence.current(true,()->false,()->{calls.add("source");return true;},()->{calls.add("native");return true;}));assertTrue(calls.isEmpty());}
    @Test void sourceCanOnlyRefuseNeverGrantAnInvalidNativeLeaf(){assertFalse(OwnedBreakingCommitFence.current(true,()->true,()->true,()->false));}
    @Test void sourceCallbackReplacingNativeContextIsCheckedAfterCallback(){var current=new AtomicBoolean(true);assertFalse(OwnedBreakingCommitFence.current(true,()->true,()->{current.set(false);return true;},current::get));}
    @Test void sourceCallbackNativeKeyAbaIsCheckedAfterCallback(){var history=new AtomicBoolean(true);assertFalse(OwnedBreakingCommitFence.current(true,history::get,()->{history.set(false);return true;},()->true));}
    @Test void sourceRefusalNeverRunsNativeLeaf(){var calls=new ArrayList<String>();assertFalse(OwnedBreakingCommitFence.current(true,()->true,()->false,()->{calls.add("native");return true;}));assertTrue(calls.isEmpty());}
    @Test void throwingPostPublicationSourceRefusesForCallerOwnedCleanup(){assertFalse(OwnedBreakingCommitFence.current(true,()->true,()->{throw new IllegalStateException("source unavailable");},()->true));}
    @Test void throwingStoredLeafCannotCommitServiceState(){assertFalse(OwnedBreakingCommitFence.current(true,()->true,()->true,()->{throw new IllegalStateException("native unavailable");}));}
    @Test void successfulOriginalPublicationRunsExactOrderedChecks(){var calls=new ArrayList<String>();assertTrue(OwnedBreakingCommitFence.current(true,()->{calls.add("history");return true;},()->{calls.add("source");return true;},()->{calls.add("native");return true;}));assertEquals(List.of("history","source","native","history"),calls);}
}
