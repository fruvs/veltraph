package dev.veltraph.client.inventory.selling;

import dev.veltraph.client.config.AutoSellMode;
import dev.veltraph.client.gui.MenuSnapshot;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class GeorgeBatchRuntimeTest {
    private static final UUID A=UUID.fromString("00000000-0000-0000-0000-000000000001"),B=UUID.fromString("00000000-0000-0000-0000-000000000002"),OTHER=UUID.fromString("00000000-0000-0000-0000-000000000003");
    private static SellingRule rule(int slot,UUID uuid){return new SellingRule(AutoSellMode.GEORGE_PETS,slot,"minecraft:player_head","Pet "+slot,"native "+uuid,1,new BigDecimal(slot==9?"5":"7"),"Quote "+slot,"pet","\"eligible\"");}
    private static SellingRuntime.Settings settings(){return new SellingRuntime.Settings(AutoSellMode.GEORGE_PETS,"Offer Pets","Accept","Confirm","Confirm",13,13,"George bought {quantity}x {item} for {coins} coins!",2,BigDecimal.ONE,new BigDecimal("20"),BigDecimal.ZERO,1,25);}
    private static GeorgeBatchPlan plan(){return new GeorgeBatchPlan(2,new BigDecimal("12"),100,"uuid",List.of(new GeorgeBatchPlan.Pet(9,A),new GeorgeBatchPlan.Pet(10,B)));}
    private static SellingObservation.Stack item(SellingRule r,UUID uuid,int slot,int index){return new SellingObservation.Stack(slot,index,r.registryId(),r.name(),r.components(),1,false,true,Map.of("pet","\"eligible\"","uuid","\""+uuid+"\""));}
    private static SellingObservation.Stack empty(int slot,int index){return new SellingObservation.Stack(slot,index,"minecraft:air","","[]",0,false);}
    private static final class Native implements GeorgeBatchRuntime.Port {
        final List<SellingRule> rules=List.of(rule(9,A),rule(10,B));final Map<Integer,SellingObservation.Stack> inventory=new HashMap<>();
        boolean valid=true,owner=true,contactFailed=false,commit=true,receipt=true;int requests,id,revision,pickups,confirms;String title="";BigDecimal purse=new BigDecimal("100");SellingObservation.Stack cursor=empty(-1,-1),escrow=empty(13,-1);SellingRule current;GeorgeBatchRuntime runtime;
        Native(){inventory.put(9,item(rules.get(0),A,27,9));inventory.put(10,item(rules.get(1),B,28,10));inventory.put(11,item(rule(11,OTHER),OTHER,29,11));inventory.put(0,new SellingObservation.Stack(54,0,"minecraft:diamond_hoe","Hoe","protected",1,true));}
        public SellingObservation read(){var all=new ArrayList<SellingObservation.Stack>(inventory.values());for(int i:new int[]{9,10,11})if(!inventory.containsKey(i))all.add(empty(18+i,i));var slots=new HashMap<Integer,SellingObservation.Stack>();for(var s:all)slots.put(s.slot(),s);slots.put(13,escrow);String label=title.equals("Confirm")?"Confirm":"Accept";slots.put(22,new SellingObservation.Stack(22,-1,"minecraft:paper",label,"quote",1,false));
            var quote=new MenuSnapshot.Slot(22,label,current==null?List.of():List.of("Selling: 1x "+current.name(),"Total: "+current.total()+" Coins"),false);
            var menu=title.isEmpty()?null:new MenuSnapshot(id,title,List.of(quote),revision);return new SellingObservation(menu,all,slots,cursor,purse,List.of(),false);}
        public boolean valid(){return valid;}public boolean owner(){return owner;}public boolean closed(){return title.isEmpty()&&cursor.empty();}
        public boolean beginContact(long tick){requests++;id++;revision=1;title="Offer Pets";current=rules.get(requests-1);runtime.bindMenu("menu-"+id);return true;}
        public FarmAutoSellRuntime.Progress contact(long tick){return contactFailed?FarmAutoSellRuntime.Progress.FAILED:FarmAutoSellRuntime.Progress.COMPLETE;}
        public boolean releaseContact(){return closed();}public void closeContact(){}
        public boolean act(SellingRuntime.Action a,SellingRule r){current=r;revision++;switch(a.kind()){
            case PICKUP->{pickups++;cursor=inventory.remove(r.inventoryIndex());}
            case DEPOSIT->{escrow=item(r,requests==1?A:B,13,-1);cursor=empty(-1,-1);}
            case ACCEPT->{id++;revision=1;title="Confirm";runtime.bindMenu("menu-"+id);}
            case CONFIRM->{confirms++;if(commit){escrow=empty(13,-1);purse=purse.add(r.total());}}
            case CLOSE->title="";
            case RETURN_DEPOSIT->{cursor=escrow;escrow=empty(13,-1);}
            case RETURN_ORIGIN->{inventory.put(r.inventoryIndex(),item(r,requests==1?A:B,18+r.inventoryIndex(),r.inventoryIndex()));cursor=empty(-1,-1);}
            default->throw new AssertionError(a.kind());}return true;}
        GeorgeBatchRuntime start(){runtime=new GeorgeBatchRuntime(this);assertTrue(runtime.start(plan(),rules,settings(),0));return runtime;}
        void frame(long t){if(receipt&&runtime.saleState()==SellingRuntime.State.WAIT_SALE)runtime.message("George bought 1x "+current.name()+" for "+current.total()+" coins!");runtime.tick(t);}
    }
    @Test void twoExactPetsReopenOnceAndRetainIndependentReceipts(){var nativePort=new Native();var protectedLedger=nativePort.read().ledger();var r=nativePort.start();for(int t=1;t<100&&r.active();t++)nativePort.frame(t);assertEquals(GeorgeBatchRuntime.State.COMPLETE,r.state(),r.reason());assertEquals(2,nativePort.requests);assertEquals(2,nativePort.pickups);assertEquals(2,nativePort.confirms);assertEquals(List.of(A,B),r.receipts().stream().map(GeorgeBatchRuntime.Receipt::petUuid).toList());assertEquals(new BigDecimal("12"),r.proceeds());assertEquals(protectedLedger.get(11),nativePort.read().ledger().get(11));assertEquals(protectedLedger.get(0),nativePort.read().ledger().get(0));assertEquals(new BigDecimal("105"),r.receipts().getFirst().proof().purseAfter());assertEquals(new BigDecimal("112"),r.receipts().getLast().proof().purseAfter());}
    @Test void failedSecondReceiptRetainsFirstOutcomeAndNeverReopensThird(){var p=new Native();var r=p.start();for(int t=1;t<100&&r.active();t++){if(r.completedCount()==1)p.receipt=false;p.frame(t);}assertEquals(GeorgeBatchRuntime.State.FAILED,r.state());assertEquals(1,r.completedCount());assertEquals(new BigDecimal("5"),r.proceeds());assertEquals(2,p.requests);assertEquals(2,p.confirms);}
    @Test void unknownSecondCommitReturnsOnlyItsOwnEscrow(){var p=new Native();var r=p.start();int t=1;for(;t<100&&r.active();t++){if(r.completedCount()==1){p.commit=false;p.receipt=false;}p.frame(t);}assertEquals(1,r.completedCount());assertTrue(r.cleaning());for(;t<150&&r.cleaning();t++)r.maintenance(t);assertFalse(r.cleaning());assertTrue(p.rules.getLast().matches(p.read().inventoryStack(10).orElseThrow()));assertFalse(p.inventory.containsKey(9));assertEquals(new BigDecimal("105"),p.purse);assertEquals(2,p.confirms);}
    @Test void stopAfterFirstReceiptNeverStartsAnotherSale(){var p=new Native();var r=p.start();int t=1;for(;t<100&&r.completedCount()==0;t++)p.frame(t);r.cancel(t);for(;t<130;t++)p.frame(t);assertEquals(GeorgeBatchRuntime.State.CANCELLED,r.state());assertEquals(1,r.receipts().size());assertEquals(1,p.confirms);assertTrue(p.inventory.containsKey(10));}
    @Test void changedOwnerConfigOrNativeLedgerStopsMidQueue(){for(int mode=0;mode<3;mode++){var p=new Native();var r=p.start();int t=1;for(;t<100&&r.completedCount()==0;t++)p.frame(t);if(mode==0)p.valid=false;else if(mode==1){p.owner=false;p.valid=false;}else p.inventory.put(11,empty(29,11));p.frame(t);assertEquals(GeorgeBatchRuntime.State.FAILED,r.state());assertEquals(1,r.completedCount());assertEquals(1,p.confirms);}}
    @Test void cumulativeDeadlineIsNotResetByReopening(){var p=new Native();p.runtime=new GeorgeBatchRuntime(p);var shorter=new GeorgeBatchPlan(2,new BigDecimal("12"),16,"uuid",plan().pets());assertTrue(p.runtime.start(shorter,p.rules,settings(),0));for(int t=1;t<80&&p.runtime.active();t++)p.frame(t);assertEquals(GeorgeBatchRuntime.State.FAILED,p.runtime.state());assertTrue(p.runtime.completedCount()<2);assertTrue(p.confirms<3);}
    @Test void aggregateCapAndDuplicateUuidRefuseBeforeContact(){var p=new Native();assertThrows(IllegalArgumentException.class,()->new GeorgeBatchPlan(2,new BigDecimal("11"),100,"uuid",plan().pets()).freeze(p.rules,p.read(),settings()));assertThrows(IllegalArgumentException.class,()->new GeorgeBatchPlan(2,new BigDecimal("20"),100,"uuid",List.of(new GeorgeBatchPlan.Pet(9,A),new GeorgeBatchPlan.Pet(10,A))));p.inventory.put(11,item(p.rules.getFirst(),A,29,11));assertThrows(IllegalArgumentException.class,()->plan().freeze(p.rules,p.read(),settings()));assertEquals(0,p.requests);}
    @Test void wrongUuidOrNativeLookalikeCannotEnterFrozenQueue(){var p=new Native();p.inventory.put(10,item(p.rules.getLast(),OTHER,28,10));assertThrows(IllegalArgumentException.class,()->plan().freeze(p.rules,p.read(),settings()));assertEquals(0,p.requests);}
    @Test void emptyApprovalsFreezeAllCurrentConfiguredMatches(){var p=new Native();var source=new GeorgeBatchPlan(2,new BigDecimal("12"),100,"uuid",List.of());assertEquals(List.of(A,B),source.freeze(p.rules,p.read(),settings()).stream().map(GeorgeBatchPlan.Entry::uuid).toList());p.inventory.remove(10);assertEquals(List.of(A),source.freeze(p.rules,p.read(),settings()).stream().map(GeorgeBatchPlan.Entry::uuid).toList());}
    @Test void unknownFieldsMissingApprovalsAndSourceBoundRefuse(){assertThrows(IllegalArgumentException.class,()->new GeorgeBatchPlan(21,new BigDecimal("20"),100,"uuid",plan().pets()));assertThrows(RuntimeException.class,()->GeorgeBatchPlan.parse("{}"));var p=new Native();assertThrows(IllegalArgumentException.class,()->plan().freeze(List.of(p.rules.getFirst()),p.read(),settings()));}
}
