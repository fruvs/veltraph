package dev.veltraph.client.mining.commission;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static dev.veltraph.client.mining.commission.CommissionLifecycle.*;
import static dev.veltraph.client.mining.commission.CommissionObservation.*;

class CommissionLifecycleTest {
    static class NativePort implements Port {
        int starts, cancels, releases; boolean admit = true; Operation operation; Receipt outcome; ActionIssue issued; long baseline = 1000;
        public ActionIssue start(Operation op,List<Task> tasks,long ticket,Frame frame) { starts++; operation=op; issued = new ActionIssue(ticket,op,frame.context(),"native-worker","action-"+ticket,baseline,frame.nowMillis()); return admit ? issued : null; }
        Receipt result(long serial,long now,Result result) { return new Receipt(issued.ticket(),issued.operation(),issued.context(),issued.receiptDomain(),issued.actionNonce(),serial,now,result); }
        public Receipt receipt(long ticket) { return outcome; }
        public void cancel(long ticket) { cancels++; }
        public void releaseOwned() { releases++; }
    }
    static Task task(double progress) { return new Task("Mithril Miner",Work.MITHRIL,"","",progress,true,false,-1); }
    static Frame frame(long now,long serial,List<Task> tasks) {
        return new Frame("p",now,true,true,true,"Royal Mines",new CommissionObservation("p",serial,now,tasks,true),false,false);
    }
    static CommissionLifecycle lifecycle(NativePort p) { return new CommissionLifecycle(p,new Policy(1000,5000,2,500)); }
    @Test void nativeArrivalNewerReceiptStartsWorkAndSameSerialCannot() {
        var p=new NativePort();var c=lifecycle(p);var f=frame(100,1,List.of(task(0)));c.start(f);c.tick(f);
        assertEquals(Operation.NAVIGATE,p.operation);
        p.outcome=p.result(1000,101,Result.SUCCEEDED);c.tick(frame(101,1,List.of(task(0))));
        assertEquals(1,p.starts);
        p.outcome=p.result(1002,102,Result.SUCCEEDED);c.tick(frame(102,2,List.of(task(0))));
        assertEquals(Operation.MINE,p.operation);assertEquals(State.WORKING,c.state());
    }
    @Test void worldChangeOrClockRollbackStopsWithoutFurtherAction() {
        var p=new NativePort();var c=lifecycle(p);var f=frame(100,1,List.of(task(0)));c.start(f);c.tick(f);
        c.tick(new Frame("foreign",101,true,true,true,"",f.commissions(),false,false));
        assertEquals(State.PAUSED,c.state());assertEquals(1,p.cancels);assertEquals(1,p.starts);
        c.start(f);c.tick(f);c.tick(frame(99,2,List.of(task(0))));assertEquals(State.PAUSED,c.state());
    }
    @Test void pathRetryWaitIsRealAndBounded() {
        var p=new NativePort();var c=lifecycle(p);var f=frame(0,1,List.of(task(0)));c.start(f);c.tick(f);
        p.outcome=p.result(1002,1,Result.FAILED);c.tick(frame(1,2,List.of(task(0))));
        c.tick(frame(500,3,List.of(task(0))));assertEquals(1,p.starts);
        c.tick(frame(501,4,List.of(task(0))));assertEquals(2,p.starts);
        p.outcome=p.result(1005,502,Result.FAILED);c.tick(frame(502,5,List.of(task(0))));
        c.tick(frame(1002,6,List.of(task(0))));
        p.outcome=p.result(1007,1003,Result.FAILED);c.tick(frame(1003,7,List.of(task(0))));
        assertEquals(State.PAUSED,c.state());assertEquals(3,p.starts);
    }
    @Test void completedTaskInterruptsMinerAndClaimNeedsNewTaskReceipt() {
        var p=new NativePort();var c=lifecycle(p);var f=frame(0,1,List.of(task(0)));c.start(f);c.tick(f);
        p.outcome=p.result(1002,1,Result.SUCCEEDED);c.tick(frame(1,2,List.of(task(0))));
        c.tick(frame(2,3,List.of(task(100))));assertEquals(Operation.CLAIM,p.operation);assertEquals(2,p.cancels);
        p.outcome=p.result(1004,3,Result.SUCCEEDED);c.tick(frame(3,4,List.of(task(100))));
        assertEquals(0,c.confirmedClaimBatches());assertEquals(State.CLAIMING,c.state());
        c.tick(frame(4,5,List.of(task(0))));assertEquals(1,c.confirmedClaimBatches());
    }
    @Test void unsupportedWorkWaitsAndDoesNotStartMinerOrTeleport() {
        var p=new NativePort();var c=lifecycle(p);
        var f=frame(0,1,List.of(new Task("Lucky Raffle",Work.UNSUPPORTED,"","",0,true,false,15)));
        c.start(f);c.tick(f);assertEquals(0,p.starts);assertEquals(State.OBSERVING,c.state());
    }
    @Test void nativeAdmissionFailureCannotResurrectStoppedFeature() {
        var p=new NativePort();p.admit=false;var c=lifecycle(p);var f=frame(0,1,List.of(task(0)));c.start(f);c.tick(f);
        assertEquals(State.PAUSED,c.state());c.tick(frame(1,2,List.of(task(0))));assertEquals(1,p.starts);
    }
    @Test void coldAndFuelPreemptWorkThroughSeparateOwnedPorts() {
        var p=new NativePort();var c=lifecycle(p);var f=frame(0,1,List.of(task(0)));c.start(f);c.tick(f);
        c.tick(new Frame("p",1,true,true,true,"",new CommissionObservation("p",2,1,List.of(task(0)),true),false,true));
        assertEquals(Operation.COOL,p.operation);assertEquals(1,p.cancels);
    }
    @Test void timeoutDoesNotTreatStoppedUnderlyingFeatureAsSuccess() {
        var p=new NativePort();var c=lifecycle(p);var f=frame(0,1,List.of(task(0)));c.start(f);c.tick(f);
        c.tick(frame(5000,2,List.of(task(0))));assertEquals(State.OBSERVING,c.state());assertEquals(1,p.cancels);
    }
    @Test void coldSuccessRequiresFreshCooledFrameAndCancelsCompletedWorker() {
        var p=new NativePort();var c=lifecycle(p);var observation=new CommissionObservation("p",1,0,List.of(task(0)),true);
        var hot=new Frame("p",0,true,true,true,"",observation,false,true);c.start(hot);c.tick(hot);
        p.outcome=p.result(1002,1,Result.SUCCEEDED);
        c.tick(new Frame("p",1,true,true,true,"",new CommissionObservation("p",2,1,List.of(task(0)),true),false,true));
        assertEquals(State.COOLING,c.state());assertEquals(0,p.cancels);
        c.tick(frame(2,3,List.of(task(0))));assertEquals(State.OBSERVING,c.state());assertEquals(1,p.cancels);
    }
    @Test void changedTaskStopsBeforeNavigatingNewWork() {
        var p=new NativePort();var c=lifecycle(p);var f=frame(0,1,List.of(task(0)));c.start(f);c.tick(f);
        var next=new Task("Titanium Miner",Work.TITANIUM,"","",0,true,false,-1);
        c.tick(frame(1,2,List.of(next)));assertEquals(1,p.cancels);assertEquals(2,p.starts);
        assertEquals(Operation.NAVIGATE,p.operation);
    }
    @Test void mismatchedReceiptDomainNonceOperationAndOldActionSerialCannotComplete() {
        var p=new NativePort();var c=lifecycle(p);var f=frame(0,50_000,List.of(task(0)));c.start(f);c.tick(f);
        var issued=p.issued;
        for (var receipt : List.of(
                new Receipt(c.ticket(),Operation.NAVIGATE,"p","other-domain",issued.actionNonce(),1001,1,Result.SUCCEEDED),
                new Receipt(c.ticket(),Operation.NAVIGATE,"p",issued.receiptDomain(),"previous-action",1001,1,Result.SUCCEEDED),
                new Receipt(c.ticket(),Operation.CLAIM,"p",issued.receiptDomain(),issued.actionNonce(),1001,1,Result.SUCCEEDED),
                p.result(1000,1,Result.SUCCEEDED))) {
            p.outcome=receipt;c.tick(frame(1,50_001,List.of(task(0))));assertEquals(State.NAVIGATING,c.state());
        }
        p.outcome=p.result(1001,2,Result.SUCCEEDED);c.tick(frame(2,50_002,List.of(task(0))));
        assertEquals(State.WORKING,c.state());
    }
    @Test void movedCompletedSlotDoesNotCountClaimAndNewTaskWithoutActionAckDoesNotEither() {
        var p=new NativePort();var c=lifecycle(p);
        var done=new Task("Mithril Miner",Work.MITHRIL,"","",100,true,true,11);
        var initial=frame(0,1,List.of(done));c.start(initial);c.tick(initial);
        var moved=new Task("Mithril Miner",Work.MITHRIL,"","",100,true,true,15);
        p.outcome=p.result(1001,1,Result.SUCCEEDED);c.tick(frame(1,2,List.of(moved)));
        assertEquals(State.CLAIMING,c.state());assertEquals(0,c.confirmedClaimBatches());
        p.outcome=null;c.tick(frame(2,3,List.of(task(0))));
        assertEquals(State.CLAIMING,c.state());assertEquals(0,c.confirmedClaimBatches());
        p.outcome=p.result(1002,3,Result.SUCCEEDED);c.tick(frame(3,4,List.of(task(0))));
        assertEquals(State.OBSERVING,c.state());assertEquals(1,c.confirmedClaimBatches());
    }
    @Test void completedTaskReorderCannotErasePendingClaim() {
        var p=new NativePort();var c=lifecycle(p);
        var first=new Task("Mithril Miner",Work.MITHRIL,"","",100,true,true,11);
        var second=new Task("Titanium Miner",Work.TITANIUM,"","",100,true,true,15);
        var f=frame(0,1,List.of(first,second));c.start(f);c.tick(f);
        p.outcome=p.result(1001,1,Result.SUCCEEDED);c.tick(frame(1,2,List.of(second,first)));
        assertEquals(State.CLAIMING,c.state());assertEquals(0,c.confirmedClaimBatches());
    }
    @Test void delayedActualIssueTimeRejectsOutcomeBeforeDispatch() {
        var p=new NativePort() {
            @Override public ActionIssue start(Operation op,List<Task> tasks,long ticket,Frame frame) {
                super.start(op,tasks,ticket,frame);
                issued=new ActionIssue(ticket,op,frame.context(),"native-worker","action-"+ticket,baseline,frame.nowMillis()+2);
                return issued;
            }
        };
        var c=lifecycle(p);var f=frame(0,1,List.of(task(0)));c.start(f);c.tick(f);
        p.outcome=p.result(1001,1,Result.SUCCEEDED);c.tick(frame(3,2,List.of(task(0))));
        assertEquals(State.NAVIGATING,c.state());
        p.outcome=p.result(1001,2,Result.SUCCEEDED);c.tick(frame(3,3,List.of(task(0))));
        assertEquals(State.WORKING,c.state());
    }
    @Test void arrivalAtOrAfterOriginalDeadlineCannotLaunchMine() {
        for (long now : new long[]{5000,5001}) {
            var p=new NativePort();var c=lifecycle(p);var f=frame(0,1,List.of(task(0)));c.start(f);c.tick(f);
            p.outcome=p.result(1001,now,Result.SUCCEEDED);c.tick(frame(now,2,List.of(task(0))));
            assertEquals(State.OBSERVING,c.state());assertEquals(1,p.starts);assertEquals(1,p.cancels);
            assertEquals(Operation.NAVIGATE,p.operation);
        }
    }
    @Test void claimAtOrAfterOriginalDeadlineCannotIncrementClaims() {
        for (long now : new long[]{5000,5001}) {
            var p=new NativePort();var c=lifecycle(p);var f=frame(0,1,List.of(task(100)));c.start(f);c.tick(f);
            p.outcome=p.result(1001,now,Result.SUCCEEDED);c.tick(frame(now,2,List.of(task(0))));
            assertEquals(State.PAUSED,c.state());assertEquals(0,c.confirmedClaimBatches());assertEquals(1,p.starts);
        }
    }
    @Test void nullOutcomeCannotBecomeSuccess() {
        var p=new NativePort();var c=lifecycle(p);var f=frame(0,1,List.of(task(0)));c.start(f);c.tick(f);
        p.outcome=p.result(1001,1,null);c.tick(frame(1,2,List.of(task(0))));
        assertEquals(State.NAVIGATING,c.state());assertEquals(1,p.starts);assertEquals(0,p.cancels);
    }
}
