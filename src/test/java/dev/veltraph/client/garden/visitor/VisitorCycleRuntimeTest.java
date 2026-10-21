package dev.veltraph.client.garden.visitor;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class VisitorCycleRuntimeTest {
    static final List<VisitorQueue.Entry> QUEUE=List.of(new VisitorQueue.Entry(UUID.randomUUID(),"Beth",VisitorRarity.RARE),new VisitorQueue.Entry(UUID.randomUUID(),"Jacob",VisitorRarity.COMMON));
    static final VisitorCycleRuntime.Settings SETTINGS=new VisitorCycleRuntime.Settings(true,true,"Loadout 3",30,1000);
    static class Actions implements VisitorCycleRuntime.Actions {
        boolean valid=true,resumeAllowed=true;int loadouts,restore,returns,resumed,cancelled,sells;long allowance;final List<String> started=new ArrayList<>();
        VisitorCycleRuntime.Progress gear=VisitorCycleRuntime.Progress.ACTIVE,original=VisitorCycleRuntime.Progress.ACTIVE,pose=VisitorCycleRuntime.Progress.ACTIVE;
        VisitorCycleRuntime.Result job=new VisitorCycleRuntime.Result(VisitorCycleRuntime.Progress.ACTIVE,0);
        public boolean acquire(){return true;}public boolean valid(){return valid;}
        public boolean beginLoadout(String name){loadouts++;return true;}public VisitorCycleRuntime.Progress loadout(){return gear;}
        public boolean startVisitor(VisitorQueue.Entry entry,long budget){started.add(entry.name());allowance=budget;job=new VisitorCycleRuntime.Result(VisitorCycleRuntime.Progress.ACTIVE,0);return true;}
        public VisitorCycleRuntime.Result visitor(){return job;}public boolean restoreLoadout(){restore++;return true;}public VisitorCycleRuntime.Progress restoredLoadout(){return original;}
        VisitorCycleRuntime.Progress saleStart=VisitorCycleRuntime.Progress.ACTIVE,sale=VisitorCycleRuntime.Progress.ACTIVE;
        public VisitorCycleRuntime.Progress beginPostQueueAutoSell(){sells++;return saleStart;}
        public VisitorCycleRuntime.Progress postQueueAutoSell(){return sale;}
        public boolean startReturn(){returns++;return true;}public VisitorCycleRuntime.Progress returned(){return pose;}public boolean resume(){if(!resumeAllowed)return false;resumed++;return true;}public void cancel(){cancelled++;}
    }
    @Test void orderedJobsRequireGearRestorationAndExactReturnBeforeResume(){
        var a=new Actions();var r=new VisitorCycleRuntime(a);assertTrue(r.start(QUEUE,SETTINGS,0));r.tick(true,10);assertTrue(a.started.isEmpty());
        a.gear=VisitorCycleRuntime.Progress.COMPLETE;r.tick(true,20);assertEquals(List.of("Beth"),a.started);assertEquals(30,a.allowance);
        a.job=new VisitorCycleRuntime.Result(VisitorCycleRuntime.Progress.COMPLETE,12);r.tick(true,30);assertEquals(List.of("Beth","Jacob"),a.started);assertEquals(18,a.allowance);
        a.job=new VisitorCycleRuntime.Result(VisitorCycleRuntime.Progress.COMPLETE,8);r.tick(true,40);assertEquals(1,a.restore);assertEquals(0,a.returns);
        a.original=VisitorCycleRuntime.Progress.COMPLETE;r.tick(true,50);assertEquals(1,a.returns);assertEquals(0,a.resumed);
        a.pose=VisitorCycleRuntime.Progress.COMPLETE;r.tick(true,60);assertEquals(VisitorCycleRuntime.State.COMPLETE,r.state());assertEquals(1,a.resumed);assertEquals(20,r.spent());
    }
    @Test void staleFarmCaptureBeforeResumeNeverResumes(){var a=new Actions();var r=new VisitorCycleRuntime(a);r.start(List.of(QUEUE.getFirst()),new VisitorCycleRuntime.Settings(true,false,"",30,1000),0);a.job=new VisitorCycleRuntime.Result(VisitorCycleRuntime.Progress.COMPLETE,0);r.tick(true,10);a.pose=VisitorCycleRuntime.Progress.COMPLETE;a.valid=false;r.tick(true,20);assertEquals(VisitorCycleRuntime.State.CANCELLED,r.state());assertEquals(0,a.resumed);}
    @Test void uncertainTransactionStopsQueueWithoutRetryOrResume(){var a=new Actions();var r=new VisitorCycleRuntime(a);r.start(QUEUE,new VisitorCycleRuntime.Settings(true,false,"",30,1000),0);a.job=new VisitorCycleRuntime.Result(VisitorCycleRuntime.Progress.FAILED,0);r.tick(true,10);r.tick(true,20);assertEquals(List.of("Beth"),a.started);assertEquals(0,a.resumed);assertEquals(1,a.cancelled);}
    @Test void skippedOfferDoesNotConsumeBudgetOrAbortFollowingVisitor(){var a=new Actions();var r=new VisitorCycleRuntime(a);r.start(QUEUE,new VisitorCycleRuntime.Settings(true,false,"",30,1000),0);a.job=new VisitorCycleRuntime.Result(VisitorCycleRuntime.Progress.SKIPPED,0);r.tick(true,10);assertEquals(1,r.skipped());assertEquals(30,a.allowance);assertEquals(2,a.started.size());}
    @Test void manualStopOptOutAndOverBudgetCanNeverResume(){for(int mode=0;mode<3;mode++){var a=new Actions();var r=new VisitorCycleRuntime(a);r.start(QUEUE,new VisitorCycleRuntime.Settings(true,false,"",30,1000),0);if(mode==0)r.cancel();else if(mode==1)r.tick(false,10);else{a.job=new VisitorCycleRuntime.Result(VisitorCycleRuntime.Progress.COMPLETE,31);r.tick(true,10);}assertFalse(r.active());assertEquals(0,a.resumed);assertEquals(1,a.started.size());}}
    @Test void refusedFinalResumeLeavesCapturedEngineStopped(){var a=new Actions();var r=new VisitorCycleRuntime(a);r.start(List.of(QUEUE.getFirst()),new VisitorCycleRuntime.Settings(true,false,"",30,1000),0);a.job=new VisitorCycleRuntime.Result(VisitorCycleRuntime.Progress.COMPLETE,0);r.tick(true,10);a.pose=VisitorCycleRuntime.Progress.COMPLETE;a.resumeAllowed=false;r.tick(true,20);assertEquals(VisitorCycleRuntime.State.FAILED,r.state());assertEquals(0,a.resumed);}
    @Test void selectedPostQueueSellWaitsForReceiptBeforeOneCapturedReturn(){
        var a=new Actions();var r=new VisitorCycleRuntime(a);r.start(List.of(QUEUE.getFirst()),new VisitorCycleRuntime.Settings(true,false,"",30,1000,true),0);
        a.job=new VisitorCycleRuntime.Result(VisitorCycleRuntime.Progress.COMPLETE,12);r.tick(true,10);
        assertEquals(1,a.sells);assertEquals(0,a.returns);assertEquals(VisitorCycleRuntime.State.AUTO_SELL,r.state());
        r.tick(true,20);assertEquals(1,a.sells);assertEquals(0,a.resumed);assertEquals(0,a.returns);
        a.sale=VisitorCycleRuntime.Progress.COMPLETE;r.tick(true,30);assertEquals(1,a.returns);assertEquals(12,r.spent());
        a.pose=VisitorCycleRuntime.Progress.COMPLETE;r.tick(true,40);r.tick(true,50);assertEquals(1,a.resumed);assertEquals(1,a.sells);assertEquals(VisitorCycleRuntime.State.COMPLETE,r.state());
    }
    @Test void optedOutPostQueueSellNeverInvokesSeller(){
        var a=new Actions();var r=new VisitorCycleRuntime(a);r.start(List.of(QUEUE.getFirst()),new VisitorCycleRuntime.Settings(true,false,"",30,1000,false),0);
        a.job=new VisitorCycleRuntime.Result(VisitorCycleRuntime.Progress.COMPLETE,0);r.tick(true,10);assertEquals(0,a.sells);assertEquals(1,a.returns);
    }
    @Test void allFilteredOffersStillUseActualInventoryGate(){
        var a=new Actions();var r=new VisitorCycleRuntime(a);r.start(QUEUE,new VisitorCycleRuntime.Settings(true,false,"",30,1000,true),0);
        a.job=new VisitorCycleRuntime.Result(VisitorCycleRuntime.Progress.SKIPPED,0);r.tick(true,10);
        assertEquals(0,a.sells);a.job=new VisitorCycleRuntime.Result(VisitorCycleRuntime.Progress.SKIPPED,0);r.tick(true,20);
        assertEquals(0,r.completed());assertEquals(2,r.skipped());assertEquals(1,a.sells);assertEquals(0,a.returns);
    }
    @Test void noApprovedStackSkipsPostQueueSellingAndReturns(){
        var a=new Actions();a.saleStart=VisitorCycleRuntime.Progress.SKIPPED;var r=new VisitorCycleRuntime(a);r.start(List.of(QUEUE.getFirst()),new VisitorCycleRuntime.Settings(true,false,"",30,1000,true),0);
        a.job=new VisitorCycleRuntime.Result(VisitorCycleRuntime.Progress.SKIPPED,0);r.tick(true,10);assertEquals(1,a.sells);assertEquals(1,a.returns);assertEquals(VisitorCycleRuntime.State.RETURNING,r.state());
    }
    @Test void restoredGearMustPrecedeSelling(){
        var a=new Actions();var r=new VisitorCycleRuntime(a);r.start(List.of(QUEUE.getFirst()),new VisitorCycleRuntime.Settings(true,true,"Loadout 3",30,1000,true),0);
        a.gear=VisitorCycleRuntime.Progress.COMPLETE;r.tick(true,10);a.job=new VisitorCycleRuntime.Result(VisitorCycleRuntime.Progress.COMPLETE,0);r.tick(true,20);
        assertEquals(1,a.restore);assertEquals(0,a.sells);a.original=VisitorCycleRuntime.Progress.COMPLETE;r.tick(true,30);assertEquals(1,a.sells);assertEquals(0,a.returns);
    }
    @Test void uncertainSaleCancellationOptOutAndDeadlineCannotReturnOrRetry(){
        for(int mode=0;mode<5;mode++){var a=new Actions();var r=new VisitorCycleRuntime(a);r.start(List.of(QUEUE.getFirst()),new VisitorCycleRuntime.Settings(true,false,"",30,100,true),0);
            a.job=new VisitorCycleRuntime.Result(VisitorCycleRuntime.Progress.COMPLETE,0);r.tick(true,10);assertEquals(1,a.sells);
            if(mode==0){a.sale=VisitorCycleRuntime.Progress.FAILED;r.tick(true,20);}else if(mode==1)r.cancel();else if(mode==2)r.tick(false,20);else if(mode==3)r.tick(true,100);else {a.valid=false;r.tick(true,20);}
            r.tick(true,110);assertFalse(r.active());assertEquals(0,a.returns);assertEquals(0,a.resumed);assertEquals(1,a.sells);assertEquals(1,a.cancelled);
        }
    }
    @Test void sellingActivationFailureCannotMasqueradeAsEmptyInventory(){
        for(var result:List.of(VisitorCycleRuntime.Progress.FAILED,VisitorCycleRuntime.Progress.CANCELLED,VisitorCycleRuntime.Progress.COMPLETE)){
            var a=new Actions();a.saleStart=result;var r=new VisitorCycleRuntime(a);r.start(List.of(QUEUE.getFirst()),new VisitorCycleRuntime.Settings(true,false,"",30,1000,true),0);
            a.job=new VisitorCycleRuntime.Result(VisitorCycleRuntime.Progress.COMPLETE,0);r.tick(true,10);assertFalse(r.active());assertEquals(1,a.sells);assertEquals(0,a.returns);assertEquals(0,a.resumed);
        }
    }
    @Test void skippedOrCancelledSellerCallbackCannotAdvanceToReturn(){
        for(var result:List.of(VisitorCycleRuntime.Progress.SKIPPED,VisitorCycleRuntime.Progress.CANCELLED)){
            var a=new Actions();var r=new VisitorCycleRuntime(a);r.start(List.of(QUEUE.getFirst()),new VisitorCycleRuntime.Settings(true,false,"",30,1000,true),0);
            a.job=new VisitorCycleRuntime.Result(VisitorCycleRuntime.Progress.COMPLETE,0);r.tick(true,10);a.sale=result;r.tick(true,20);assertFalse(r.active());assertEquals(0,a.returns);assertEquals(0,a.resumed);
        }
    }
    @Test void invalidVisitorBudgetAndEmptyQueueCannotStartSelling(){
        var a=new Actions();var r=new VisitorCycleRuntime(a);r.start(List.of(),new VisitorCycleRuntime.Settings(true,false,"",30,1000,true),0);assertEquals(0,a.sells);
        r.start(List.of(QUEUE.getFirst()),new VisitorCycleRuntime.Settings(true,false,"",30,1000,true),10);a.job=new VisitorCycleRuntime.Result(VisitorCycleRuntime.Progress.COMPLETE,31);r.tick(true,20);assertEquals(0,a.sells);assertEquals(0,a.returns);
    }
    @Test void lateReceiptAtDeadlineCannotInvokeReturn(){
        var a=new Actions();var r=new VisitorCycleRuntime(a);r.start(List.of(QUEUE.getFirst()),new VisitorCycleRuntime.Settings(true,false,"",30,100,true),0);
        a.job=new VisitorCycleRuntime.Result(VisitorCycleRuntime.Progress.COMPLETE,0);r.tick(true,10);a.sale=VisitorCycleRuntime.Progress.COMPLETE;r.tick(true,100);assertEquals(VisitorCycleRuntime.State.FAILED,r.state());assertEquals(0,a.returns);assertEquals(0,a.resumed);
    }
    @Test void saleReceiptDoesNotReplaceExactFarmReturnProof(){
        var a=new Actions();var r=new VisitorCycleRuntime(a);r.start(List.of(QUEUE.getFirst()),new VisitorCycleRuntime.Settings(true,false,"",30,1000,true),0);
        a.job=new VisitorCycleRuntime.Result(VisitorCycleRuntime.Progress.COMPLETE,0);r.tick(true,10);a.sale=VisitorCycleRuntime.Progress.COMPLETE;r.tick(true,20);a.pose=VisitorCycleRuntime.Progress.FAILED;r.tick(true,30);assertEquals(VisitorCycleRuntime.State.FAILED,r.state());assertEquals(0,a.resumed);assertEquals(1,a.sells);
    }

    @Test void cancelledOrUnacknowledgedVisitorNeverReachesSelectedSeller(){
        for(var result:List.of(VisitorCycleRuntime.Progress.CANCELLED,VisitorCycleRuntime.Progress.FAILED)){
            var a=new Actions();var r=new VisitorCycleRuntime(a);r.start(List.of(QUEUE.getFirst()),new VisitorCycleRuntime.Settings(true,false,"",30,1000,true),0);
            a.job=new VisitorCycleRuntime.Result(result,0);r.tick(true,10);assertFalse(r.active());assertEquals(0,a.sells);assertEquals(0,a.returns);assertEquals(0,a.resumed);
        }
    }

}
