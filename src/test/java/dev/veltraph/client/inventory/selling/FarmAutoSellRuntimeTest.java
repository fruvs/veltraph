package dev.veltraph.client.inventory.selling;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FarmAutoSellRuntimeTest {
    private static final class Fake implements FarmAutoSellRuntime.Actions {
        boolean acquired=true,valid=true,started=true,begin=true,resumed=true;int starts,returns,resumes,cancels;
        FarmAutoSellRuntime.Progress child=FarmAutoSellRuntime.Progress.ACTIVE,cleanup=FarmAutoSellRuntime.Progress.ACTIVE,returned=FarmAutoSellRuntime.Progress.ACTIVE;
        public boolean acquire(){return acquired;}public boolean valid(){return valid;}public boolean startChild(){starts++;return started;}
        public FarmAutoSellRuntime.Progress child(){return child;}public FarmAutoSellRuntime.Progress cleanup(){return cleanup;}
        public boolean beginReturn(){returns++;return begin;}public FarmAutoSellRuntime.Progress returned(){return returned;}
        public boolean resume(){resumes++;return resumed;}public void cancel(){cancels++;}
    }
    @Test void explicitChildCleanupReturnAndResumeAreAllRequired(){var f=new Fake();var r=new FarmAutoSellRuntime(f);assertTrue(r.start(0,100));
        r.tick(1);assertEquals(FarmAutoSellRuntime.State.SELLING,r.state());assertEquals(0,f.returns);
        f.child=FarmAutoSellRuntime.Progress.COMPLETE;r.tick(2);assertEquals(FarmAutoSellRuntime.State.CLEANUP,r.state());r.tick(3);assertEquals(0,f.returns);
        f.cleanup=FarmAutoSellRuntime.Progress.COMPLETE;r.tick(4);assertEquals(FarmAutoSellRuntime.State.RETURN,r.state());assertEquals(0,f.resumes);
        r.tick(5);assertEquals(0,f.resumes);f.returned=FarmAutoSellRuntime.Progress.COMPLETE;r.tick(6);assertEquals(FarmAutoSellRuntime.State.COMPLETE,r.state());assertEquals(1,f.resumes);assertEquals(0,f.cancels);r.tick(7);assertEquals(1,f.resumes);}
    @Test void failedChildNeverStartsReturn(){var f=new Fake();var r=new FarmAutoSellRuntime(f);r.start(0,100);f.child=FarmAutoSellRuntime.Progress.FAILED;r.tick(1);assertEquals(FarmAutoSellRuntime.State.FAILED,r.state());assertEquals(0,f.returns);assertEquals(0,f.resumes);assertEquals(1,f.cancels);}
    @Test void cleanupFailureNeverReturns(){var f=new Fake();var r=new FarmAutoSellRuntime(f);r.start(0,100);f.child=FarmAutoSellRuntime.Progress.COMPLETE;r.tick(1);f.cleanup=FarmAutoSellRuntime.Progress.FAILED;r.tick(2);assertEquals(FarmAutoSellRuntime.State.FAILED,r.state());assertEquals(0,f.resumes);}
    @Test void noCommittedLeaseCannotLaunchChild(){var f=new Fake();f.acquired=false;var r=new FarmAutoSellRuntime(f);assertFalse(r.start(0,100));assertEquals(0,f.starts);assertEquals(1,f.cancels);}
    @Test void invalidCommittedLeaseCannotLaunchChild(){var f=new Fake();f.valid=false;var r=new FarmAutoSellRuntime(f);assertFalse(r.start(0,100));assertEquals(0,f.starts);}
    @Test void refusedChildLaunchCancelsTicket(){var f=new Fake();f.started=false;var r=new FarmAutoSellRuntime(f);assertFalse(r.start(0,100));assertEquals(1,f.cancels);}
    @Test void deadOrReplacedIdentityCancelsBeforeAnotherAction(){var f=new Fake();var r=new FarmAutoSellRuntime(f);r.start(0,100);f.valid=false;r.tick(1);assertEquals(FarmAutoSellRuntime.State.FAILED,r.state());assertEquals(0,f.resumes);}
    @Test void absoluteDeadlineCannotBeExtended(){var f=new Fake();var r=new FarmAutoSellRuntime(f);r.start(0,100);r.tick(99);r.tick(100);assertEquals(FarmAutoSellRuntime.State.FAILED,r.state());assertEquals(1,f.cancels);}
    @Test void reversalCancels(){var f=new Fake();var r=new FarmAutoSellRuntime(f);r.start(10,100);r.tick(20);r.tick(19);assertEquals(FarmAutoSellRuntime.State.FAILED,r.state());}
    @Test void cancellationAtEachStageNeverResumes(){for(var stage: new int[]{0,1,2}){var f=new Fake();var r=new FarmAutoSellRuntime(f);r.start(0,100);if(stage>0){f.child=FarmAutoSellRuntime.Progress.COMPLETE;r.tick(1);}if(stage>1){f.cleanup=FarmAutoSellRuntime.Progress.COMPLETE;r.tick(2);}r.cancel();r.tick(3);assertEquals(FarmAutoSellRuntime.State.CANCELLED,r.state());assertEquals(0,f.resumes);assertEquals(1,f.cancels);}}
    @Test void failedReturnOrFinalRestorationNeverCompletes(){for(boolean returnFailure:new boolean[]{true,false}){var f=new Fake();var r=new FarmAutoSellRuntime(f);r.start(0,100);f.child=f.cleanup=FarmAutoSellRuntime.Progress.COMPLETE;r.tick(1);r.tick(2);f.returned=returnFailure?FarmAutoSellRuntime.Progress.FAILED:FarmAutoSellRuntime.Progress.COMPLETE;f.resumed=false;r.tick(3);assertEquals(FarmAutoSellRuntime.State.FAILED,r.state());assertEquals(1,f.cancels);}}
    @Test void activeRunCannotBeReplaced(){var r=new FarmAutoSellRuntime(new Fake());r.start(0,100);assertThrows(IllegalStateException.class,()->r.start(1,100));}
}
