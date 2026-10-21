package dev.veltraph.client.garden.visitor;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class VisitorServeRuntimeTest {
    static final Object MENU=new Object();static final String W="minecraft:wheat|Wheat";
    static VisitorServeRuntime.Settings settings(boolean enabled,int qty,long cost){return new VisitorServeRuntime.Settings(enabled,qty,cost,VisitorSettings.of(cost,true,VisitorAcceptFilter.ALL),1000);}
    static VisitorServeRuntime.Snapshot snapshot(int revision,int slot,int qty,long price){
        return new VisitorServeRuntime.Snapshot(MENU,7,revision,"Jacob",slot,slot<0?null:VisitorOffer.of("Jacob",VisitorRarity.RARE,false,List.of(new VisitorItem("Wheat",qty,price))),
                slot<0?List.of():List.of(new VisitorServeRuntime.Need(W,qty,price)),true);
    }
    @Test void requestIsNotCompletionAndBothAcknowledgmentsAreRequired(){
        int[] clicks={0};var rt=new VisitorServeRuntime(s->{clicks[0]++;return true;});var cfg=settings(true,8,100);
        assertTrue(rt.start(snapshot(1,13,4,3),Map.of(W,8),cfg,0));
        rt.tick(true,snapshot(1,13,4,3),Map.of(W,8),cfg,50);assertEquals(1,clicks[0]);assertEquals(VisitorServeRuntime.State.WAITING_ACK,rt.state());
        rt.tick(true,snapshot(2,-1,4,3),Map.of(W,8),cfg,100);assertTrue(rt.active());
        rt.tick(true,snapshot(1,-1,4,3),Map.of(W,4),cfg,150);assertTrue(rt.active());
        rt.tick(true,snapshot(2,-1,4,3),Map.of(W,4),cfg,200);assertEquals(VisitorServeRuntime.State.COMPLETE,rt.state());assertEquals(1,clicks[0]);
    }
    @Test void disabledUnknownQuoteQuantityAndMissingInventoryNeverClick(){
        for(int test=0;test<4;test++){
            int[] clicks={0};var rt=new VisitorServeRuntime(s->{clicks[0]++;return true;});
            assertFalse(rt.start(snapshot(1,13,4,test==1?-1:3),Map.of(W,test==3?3:8),settings(test!=0,test==2?3:8,100),0));
            assertEquals(0,clicks[0]);assertEquals(VisitorServeRuntime.State.FAILED,rt.state());
        }
    }
    @Test void aggregateCostAndArithmeticOverflowFailClosed(){
        var offer=VisitorOffer.of("Jacob",VisitorRarity.RARE,false,List.of(new VisitorItem("Wheat",2,3),new VisitorItem("Carrot",2,3)));
        var frame=new VisitorServeRuntime.Snapshot(MENU,7,1,"Jacob",13,offer,List.of(new VisitorServeRuntime.Need(W,2,3),new VisitorServeRuntime.Need("C",2,3)),true);
        assertTrue(VisitorServeRuntime.validate(frame,Map.of(W,2,"C",2),settings(true,64,10)).isPresent());
        assertTrue(VisitorServeRuntime.validate(snapshot(1,13,4,Long.MAX_VALUE),Map.of(W,8),settings(true,64,Long.MAX_VALUE)).isPresent());
    }
    @Test void staleRepricedMenuOrChangedQuantityFailsBeforeClick(){
        int[] clicks={0};var cfg=settings(true,64,100);var rt=new VisitorServeRuntime(s->{clicks[0]++;return true;});
        rt.start(snapshot(1,13,4,3),Map.of(W,8),cfg,0);rt.tick(true,snapshot(2,13,4,4),Map.of(W,8),cfg,50);
        assertEquals(0,clicks[0]);assertEquals(VisitorServeRuntime.State.FAILED,rt.state());
    }
    @Test void replacementWithSameTitleAndIdStillCancels(){
        var cfg=settings(true,64,100);var rt=new VisitorServeRuntime(s->true);rt.start(snapshot(1,13,4,3),Map.of(W,8),cfg,0);
        var other=new VisitorServeRuntime.Snapshot(new Object(),7,2,"Jacob",-1,null,List.of(),true);
        rt.tick(true,other,Map.of(W,4),cfg,50);assertEquals(VisitorServeRuntime.State.CANCELLED,rt.state());
    }
    @Test void manualCloseDeathOrOptOutDoesNotReportSuccess(){
        for(int mode=0;mode<3;mode++){
            var cfg=settings(true,64,100);var rt=new VisitorServeRuntime(s->true);rt.start(snapshot(1,13,4,3),Map.of(W,8),cfg,0);
            rt.tick(mode!=1,mode==0?null:snapshot(2,-1,4,3),Map.of(W,4),mode==2?settings(false,64,100):cfg,50);
            assertEquals(VisitorServeRuntime.State.CANCELLED,rt.state());
        }
    }
    @Test void serverClosureWaitsForInventorySynchronizationThenCompletes(){
        var cfg=settings(true,64,100);var rt=new VisitorServeRuntime(s->true);rt.start(snapshot(1,13,4,3),Map.of(W,8),cfg,0);
        rt.tick(true,snapshot(1,13,4,3),Map.of(W,8),cfg,10);rt.tick(true,null,Map.of(W,8),cfg,20);assertTrue(rt.active());
        rt.tick(true,null,Map.of(W,4),cfg,30);assertEquals(VisitorServeRuntime.State.COMPLETE,rt.state());
    }
    @Test void closedMenuWithoutConsumptionTimesOutWithoutDuplicateRequest(){
        int[] clicks={0};var cfg=settings(true,64,100);var rt=new VisitorServeRuntime(s->{clicks[0]++;return true;});rt.start(snapshot(1,13,4,3),Map.of(W,8),cfg,0);
        rt.tick(true,snapshot(1,13,4,3),Map.of(W,8),cfg,10);rt.tick(true,null,Map.of(W,8),cfg,20);rt.tick(true,null,Map.of(W,8),cfg,1000);
        assertEquals(VisitorServeRuntime.State.FAILED,rt.state());assertEquals(1,clicks[0]);
    }
    @Test void cursorOrOffByOneConsumptionCannotComplete(){
        var cfg=settings(true,64,100);var rt=new VisitorServeRuntime(s->true);rt.start(snapshot(1,13,4,3),Map.of(W,8),cfg,0);
        rt.tick(true,snapshot(1,13,4,3),Map.of(W,8),cfg,50);
        rt.tick(true,snapshot(2,-1,4,3),Map.of(W,3),cfg,100);assertTrue(rt.active());
        var carried=new VisitorServeRuntime.Snapshot(MENU,7,2,"Jacob",-1,null,List.of(),false);
        rt.tick(true,carried,Map.of(W,4),cfg,150);assertTrue(rt.active());
    }
    @Test void deniedRequestAndTimeoutNeverRetry(){
        int[] clicks={0};var cfg=settings(true,64,100);var rt=new VisitorServeRuntime(s->{clicks[0]++;return true;});
        rt.start(snapshot(1,13,4,3),Map.of(W,8),cfg,0);
        rt.tick(true,snapshot(1,13,4,3),Map.of(W,8),cfg,50);
        rt.tick(true,snapshot(1,13,4,3),Map.of(W,8),cfg,1000);
        assertEquals(VisitorServeRuntime.State.FAILED,rt.state());assertEquals(1,clicks[0]);
        rt.start(snapshot(1,13,4,3),Map.of(W,8),cfg,2000);rt.reject("Server rejected request");
        assertEquals(VisitorServeRuntime.State.FAILED,rt.state());assertEquals(1,clicks[0]);
    }
    @Test void sinkFailureCancelsTheOneRequest(){
        var cfg=settings(true,64,100);var rt=new VisitorServeRuntime(s->{throw new IllegalStateException("UI failure");});
        rt.start(snapshot(1,13,4,3),Map.of(W,8),cfg,0);rt.tick(true,snapshot(1,13,4,3),Map.of(W,8),cfg,50);
        assertEquals(VisitorServeRuntime.State.FAILED,rt.state());
    }
    @Test void capCannotBeDisabledAndDuplicateMaterialRowsAreRejected(){
        var cfg=new VisitorServeRuntime.Settings(true,64,100,VisitorSettings.of(100,false,VisitorAcceptFilter.ALL),1000);
        assertTrue(VisitorServeRuntime.validate(snapshot(1,13,4,3),Map.of(W,8),cfg).isPresent());
        var offer=VisitorOffer.of("Jacob",VisitorRarity.RARE,false,List.of(new VisitorItem("Wheat",2,3),new VisitorItem("Wheat",2,3)));
        var duplicate=new VisitorServeRuntime.Snapshot(MENU,7,1,"Jacob",13,offer,List.of(new VisitorServeRuntime.Need(W,2,3),new VisitorServeRuntime.Need(W,2,3)),true);
        assertTrue(VisitorServeRuntime.validate(duplicate,Map.of(W,2),settings(true,64,100)).isPresent());
    }
}
