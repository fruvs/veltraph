package dev.veltraph.client.inventory.selling;

import dev.veltraph.client.gui.MenuSnapshot;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BazaarCustodyRuntimeTest {
    private static final BazaarCustodyPlan PLAN=new BazaarCustodyPlan("localbazaar","Local Bazaar","Local Confirm","Inventory","Confirm Inventory","Sacks","Confirm Sacks",
        "Receipt {quantity} for {coins}","Purse:",SellingAcknowledgment.Denomination.UNKNOWN,
        List.of(new BazaarCustodyPlan.InventoryItem(9,"minecraft:wheat","Wheat","c",2,new BigDecimal("3")),new BazaarCustodyPlan.InventoryItem(10,"minecraft:carrot","Carrot","d",3,new BigDecimal("2"))),
        List.of(new BazaarCustodyPlan.Sack("Wheat",8,new BigDecimal("3"),20,"minecraft:paper","Sack counter","counter={quantity}")),100,new BigDecimal("100"),1,20);
    private static SellingObservation view(BazaarCustodyRuntime.Stage stage,int id,int revision,boolean confirm,boolean consumed,int counter,int coins){
        var wheat=new SellingObservation.Stack(27,9,"minecraft:wheat","Wheat","c",consumed&&stage==BazaarCustodyRuntime.Stage.INVENTORY?0:2,false);
        var carrot=new SellingObservation.Stack(28,10,"minecraft:carrot","Carrot","d",consumed&&stage==BazaarCustodyRuntime.Stage.INVENTORY?0:3,false);
        var tool=new SellingObservation.Stack(54,0,"minecraft:diamond_sword","Sword","protected",1,true);
        var marker=new SellingObservation.Stack(20,-1,"minecraft:paper","Sack counter","counter="+counter,1,false);
        String label=stage==BazaarCustodyRuntime.Stage.INVENTORY?(confirm?"Confirm Inventory":"Inventory"):(confirm?"Confirm Sacks":"Sacks");
        var lore=stage==BazaarCustodyRuntime.Stage.INVENTORY?List.of("Selling: 2x Wheat","Selling: 3x Carrot","Total: 12 Coins"):List.of("Selling: 8x Wheat","Total: 24 Coins");
        var menu=id<0?null:new MenuSnapshot(id,confirm?"Local Confirm":"Local Bazaar",List.of(new MenuSnapshot.Slot(11,label,lore,false)),revision);
        return new SellingObservation(menu,List.of(wheat,carrot,tool),Map.of(27,wheat,28,carrot,54,tool,20,marker),null,BigDecimal.valueOf(coins),List.of(),false);
    }
    private static BazaarCustodyRuntime start(BazaarCustodyRuntime.Stage stage){var r=new BazaarCustodyRuntime();r.start(view(stage,1,1,false,false,8,100),PLAN,stage,0);assertEquals(BazaarCustodyRuntime.State.READY,r.state(),r.status());return r;}
    private static void action(BazaarCustodyRuntime r,SellingObservation view,int tick){var action=r.tick(view,tick).orElseThrow();assertTrue(r.valid(action,view));r.applied(action,true,tick);}
    private static void commit(BazaarCustodyRuntime r,BazaarCustodyRuntime.Stage stage){action(r,view(stage,1,1,false,false,8,100),1);r.tick(view(stage,2,1,true,false,8,100),2);assertEquals(BazaarCustodyRuntime.State.READY_CONFIRM,r.state());action(r,view(stage,2,1,true,false,8,100),3);}
    @Test void bulkInventoryHasExactPositionalItemPurseReceiptRevisionAndClosedMenuProof(){var stage=BazaarCustodyRuntime.Stage.INVENTORY;var r=start(stage);commit(r,stage);
        r.onMessage("Receipt 5 for 12");r.tick(view(stage,2,1,true,true,8,112),4);assertEquals(BazaarCustodyRuntime.State.WAIT_ACK,r.state());
        r.tick(view(stage,2,2,true,true,8,112),5);assertEquals(BazaarCustodyRuntime.State.CLOSING,r.state());assertTrue(r.acknowledged().isEmpty());
        action(r,view(stage,2,2,true,true,8,112),6);r.tick(view(stage,-1,0,true,true,8,112),7);var proof=r.acknowledged().orElseThrow();
        assertEquals(Map.of("Wheat",2,"Carrot",3),proof.quantities());assertEquals(BigDecimal.valueOf(12),proof.proceeds());assertEquals(2,proof.menuId());assertEquals(1,proof.requestRevision());assertEquals(2,proof.receiptRevision());assertEquals(1,proof.matchedReceiptSerial());r.onMessage("Receipt 5 for 12");assertEquals(proof,r.acknowledged().orElseThrow());}
    @Test void sacksNeedNativeExactCounterConsumptionAndUnchangedPlayerInventory(){var stage=BazaarCustodyRuntime.Stage.SACKS;var r=start(stage);commit(r,stage);r.onMessage("Receipt 8 for 24");
        r.tick(view(stage,2,2,true,false,8,124),4);assertEquals(BazaarCustodyRuntime.State.WAIT_ACK,r.state());
        r.tick(view(stage,2,3,true,false,0,124),5);assertEquals(BazaarCustodyRuntime.State.CLOSING,r.state());action(r,view(stage,2,3,true,false,0,124),6);
        r.tick(view(stage,-1,0,true,false,0,124),7);assertEquals(BazaarCustodyRuntime.Stage.SACKS,r.acknowledged().orElseThrow().stage());}
    @Test void noSinglePartialOutcomeCompletes(){for(int withheld=0;withheld<4;withheld++){var stage=BazaarCustodyRuntime.Stage.INVENTORY;var r=start(stage);commit(r,stage);
        if(withheld!=0)r.onMessage("Receipt 5 for 12");r.tick(view(stage,2,withheld==1?1:2,true,withheld!=2,8,withheld==3?100:112),4);
        assertEquals(BazaarCustodyRuntime.State.WAIT_ACK,r.state());assertTrue(r.acknowledged().isEmpty());r.tick(view(stage,2,2,true,false,8,100),24);assertEquals(BazaarCustodyRuntime.State.FAILED,r.state());}}
    @Test void foreignProtectedInventoryChangeRefusesBeforeFirstAction(){var stage=BazaarCustodyRuntime.Stage.INVENTORY;var r=start(stage);var original=view(stage,1,1,false,false,8,100);
        var changed=new SellingObservation(original.menu(),original.inventory().stream().filter(i->i.inventoryIndex()!=0).toList(),original.slots(),null,original.coins(),List.of(),false);
        assertTrue(r.tick(changed,1).isEmpty());assertEquals(BazaarCustodyRuntime.State.CANCELLED,r.state());}
    @Test void quoteOrCurrencyChangedOnFinalRereadCannotCommit(){var stage=BazaarCustodyRuntime.Stage.INVENTORY;var r=start(stage);var original=view(stage,1,1,false,false,8,100);var action=r.tick(original,1).orElseThrow();
        assertFalse(r.valid(action,view(stage,1,1,false,false,8,101)));r.applied(action,false,1);assertTrue(r.acknowledged().isEmpty());assertEquals(BazaarCustodyRuntime.State.FAILED,r.state());}
    @Test void sackCounterChangedBeforeConfirmationStops(){var stage=BazaarCustodyRuntime.Stage.SACKS;var r=start(stage);action(r,view(stage,1,1,false,false,8,100),1);
        assertTrue(r.tick(view(stage,2,1,true,false,7,100),2).isEmpty());assertEquals(BazaarCustodyRuntime.State.CANCELLED,r.state());}
    @Test void staleReceiptStopAndReplacementNeverProduceProof(){var stage=BazaarCustodyRuntime.Stage.SACKS;var r=start(stage);r.onMessage("Receipt 8 for 24");commit(r,stage);
        r.tick(view(stage,2,2,true,false,0,124),4);assertEquals(BazaarCustodyRuntime.State.WAIT_ACK,r.state());r.cancel("user stop");assertTrue(r.acknowledged().isEmpty());
        var second=start(stage);assertTrue(second.tick(view(stage,5,1,false,false,8,100),1).isEmpty());assertEquals(BazaarCustodyRuntime.State.CANCELLED,second.state());}
    @Test void unknownIncompleteDuplicateOrBudgetInvalidProfilesFail(){assertThrows(RuntimeException.class,()->BazaarCustodyPlan.parse("{}"));
        assertThrows(IllegalArgumentException.class,()->new BazaarCustodyPlan.InventoryItem(0,"minecraft:wheat","Wheat","c",2,BigDecimal.ONE));
        assertThrows(IllegalArgumentException.class,()->new BazaarCustodyPlan.Sack("Wheat",8,BigDecimal.ONE,20,"minecraft:paper","Sack counter","no-placeholder"));
        assertThrows(IllegalArgumentException.class,()->new BazaarCustodyPlan("x","Shop","Confirm","a","b","c","d","{quantity} {coins}","Purse:",SellingAcknowledgment.Denomination.UNKNOWN,PLAN.inventory(),PLAN.sacks(),1,BigDecimal.ONE,1,20));}
}
