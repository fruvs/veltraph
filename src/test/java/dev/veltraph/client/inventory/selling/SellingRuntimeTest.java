package dev.veltraph.client.inventory.selling;

import dev.veltraph.client.config.AutoSellMode;
import dev.veltraph.client.gui.MenuSnapshot;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class SellingRuntimeTest {
    private static SellingRule rule(AutoSellMode channel){return new SellingRule(channel,9,channel==AutoSellMode.GEORGE_PETS?"minecraft:player_head":"minecraft:wheat",
            channel==AutoSellMode.GEORGE_PETS?"Pet":"Wheat","components",channel==AutoSellMode.GEORGE_PETS?1:4,new BigDecimal(channel==AutoSellMode.TRASH?"0":"3"),"Sell item",
            channel==AutoSellMode.GEORGE_PETS?"marker":"",channel==AutoSellMode.GEORGE_PETS?"\"pet\"":"");}
    private static SellingRuntime.Settings settings(AutoSellMode channel){return new SellingRuntime.Settings(channel,"Shop","Accept","Confirm","Confirm Sale",13,13,
            "You sold {quantity}x {item} for {coins} coins!",64,BigDecimal.ONE,new BigDecimal("100"),BigDecimal.ZERO,2,20);}
    private static SellingObservation.Stack item(SellingRule rule,int slot,int index,int count){return new SellingObservation.Stack(slot,index,rule.registryId(),rule.name(),rule.components(),count,false,
            rule.channel()==AutoSellMode.GEORGE_PETS,rule.channel()==AutoSellMode.GEORGE_PETS?Map.of("marker","\"pet\""):Map.of());}
    private static SellingObservation.Stack empty(int slot,int index){return new SellingObservation.Stack(slot,index,"minecraft:air","","[]",0,false);}
    private static SellingObservation observed(SellingRule rule,int id,int rev,String title,boolean inventory,boolean cursor,boolean deposit,String label,int coins,List<SellingObservation.DroppedItem> drops,boolean predicted){
        var player=item(rule,27,9,inventory?rule.quantity():0);var held=cursor?item(rule,-1,-1,rule.quantity()):empty(-1,-1);
        var escrow=deposit?item(rule,13,-1,rule.quantity()):empty(13,-1);
        var paper=new SellingObservation.Stack(11,-1,"minecraft:paper",label,"quote",1,false);
        var quote=new MenuSnapshot.Slot(11,label,List.of("Selling: "+rule.quantity()+"x "+rule.name(),"Total: "+rule.total()+" Coins"),false);
        var menu=id<0?null:new MenuSnapshot(id,title,List.of(quote,new MenuSnapshot.Slot(27,rule.name(),List.of(),true,rule.registryId(),player.count()),
                new MenuSnapshot.Slot(13,deposit?rule.name():"",List.of(),false,escrow.registryId(),escrow.count())),rev);
        var tool=new SellingObservation.Stack(54,0,"minecraft:diamond_sword","Sword","tool",1,true);
        return new SellingObservation(menu,List.of(player,tool),Map.of(27,player,13,escrow,11,paper),held,new BigDecimal(coins),drops,predicted);
    }
    private static SellingObservation observed(SellingRule rule,int id,int rev,String title,boolean inventory,boolean cursor,boolean deposit,String label,int coins){return observed(rule,id,rev,title,inventory,cursor,deposit,label,coins,List.of(),false);}
    private static SellingRuntime started(SellingRule rule){var r=new SellingRuntime();r.start(observed(rule,1,1,"Shop",true,false,false,"Sell item",100),rule,settings(rule.channel()),0);assertFalse(r.terminal(),r.reason());return r;}
    private static void apply(SellingRuntime runtime,SellingObservation view,long tick,SellingRuntime.Kind kind){var action=runtime.tick(view,tick).orElseThrow();assertEquals(kind,action.kind());assertTrue(runtime.valid(action,view));runtime.applied(action,true,tick);}
    private static void deposit(SellingRuntime r,SellingRule rule){
        apply(r,observed(rule,1,1,"Shop",true,false,false,"Sell item",100),2,SellingRuntime.Kind.PICKUP);
        r.tick(observed(rule,1,2,"Shop",false,true,false,"Accept",100),3);
        assertEquals(SellingRuntime.State.READY_DEPOSIT,r.state());
        apply(r,observed(rule,1,2,"Shop",false,true,false,"Accept",100),5,SellingRuntime.Kind.DEPOSIT);
        r.tick(observed(rule,1,3,"Shop",false,false,true,"Accept",100),6);assertEquals(SellingRuntime.State.READY_ACCEPT,r.state());
    }
    @Test void npcRequiresAuthoritativeInventoryPurseReceiptAndClosedMenu(){var rule=rule(AutoSellMode.NPC);var r=started(rule);
        apply(r,observed(rule,1,1,"Shop",true,false,false,"Sell item",100),2,SellingRuntime.Kind.SELL);
        r.onMessage("You sold 4x Wheat for 12 coins!");
        r.tick(observed(rule,1,1,"Shop",false,false,false,"Sell item",112),3);assertEquals(SellingRuntime.State.WAIT_SALE,r.state());
        r.tick(observed(rule,1,2,"Shop",false,false,false,"Sell item",100),4);assertEquals(SellingRuntime.State.WAIT_SALE,r.state());
        r.tick(observed(rule,1,2,"Shop",false,false,false,"Sell item",112),5);assertEquals(SellingRuntime.State.CLOSING,r.state());assertTrue(r.acknowledgedSale().isEmpty());
        apply(r,observed(rule,1,2,"Shop",false,false,false,"Sell item",112),7,SellingRuntime.Kind.CLOSE);
        r.tick(observed(rule,-1,0,"",false,false,false,"",112),8);assertEquals(SellingRuntime.State.COMPLETE,r.state());
        var proof=r.acknowledgedSale().orElseThrow();assertEquals(new BigDecimal("12"),proof.proceeds());assertEquals(new BigDecimal("100"),proof.purseBefore());assertEquals(new BigDecimal("112"),proof.purseAfter());
        assertEquals(rule.identity(),proof.itemIdentity());assertEquals(1,proof.menuId());assertEquals(1,proof.requestRevision());assertEquals(2,proof.receiptRevision());assertEquals(1,proof.matchedReceiptSerial());r.onMessage("You sold 4x Wheat for 12 coins!");assertEquals(proof,r.acknowledgedSale().orElseThrow());r.reset();assertTrue(r.acknowledgedSale().isEmpty());}
    private static void retain(SellingRuntime runtime,SellingObservation o,SellingRule rule,SellingRuntime.Settings settings,long tick){try{SellingRuntime.class.getMethod("start",SellingObservation.class,SellingRule.class,SellingRuntime.Settings.class,long.class,boolean.class).invoke(runtime,o,rule,settings,tick,true);}catch(NoSuchMethodException e){fail("Existing seller lacks the readable same-menu batch continuation",e);}catch(java.lang.reflect.InvocationTargetException e){if(e.getCause() instanceof RuntimeException cause)throw cause;throw new AssertionError(e.getCause());}catch(IllegalAccessException e){throw new AssertionError(e);}}
    @Test void optionalNpcBatchRetainsMenuOnlyAfterAllOriginalProofs(){var rule=rule(AutoSellMode.NPC);var r=new SellingRuntime();retain(r,observed(rule,1,1,"Shop",true,false,false,"Sell item",100),rule,settings(AutoSellMode.NPC),0);
        apply(r,observed(rule,1,1,"Shop",true,false,false,"Sell item",100),2,SellingRuntime.Kind.SELL);
        r.tick(observed(rule,1,2,"Shop",false,false,false,"Sell item",112),3);assertEquals(SellingRuntime.State.WAIT_SALE,r.state());assertTrue(r.acknowledgedSale().isEmpty());
        r.onMessage("You sold 4x Wheat for 12 coins!");r.tick(observed(rule,1,2,"Shop",false,false,false,"Sell item",112),4);assertEquals(SellingRuntime.State.COMPLETE,r.state());assertEquals(1,r.acknowledgedSale().orElseThrow().menuId());assertTrue(r.tick(observed(rule,1,2,"Shop",false,false,false,"Sell item",112),5).isEmpty());}
    @Test void retainOptionCannotChangeGeorgeOrTrashCleanup(){for(var mode:List.of(AutoSellMode.GEORGE_PETS,AutoSellMode.TRASH)){var rule=rule(mode);assertThrows(IllegalArgumentException.class,()->retain(new SellingRuntime(),observed(rule,1,1,"Shop",true,false,false,"Sell item",100),rule,settings(mode),0));}}
    @Test void reorderedNpcMoneyAndItemDeltasWaitForAllParts(){var rule=rule(AutoSellMode.NPC);var r=started(rule);
        apply(r,observed(rule,1,1,"Shop",true,false,false,"Sell item",100),2,SellingRuntime.Kind.SELL);
        r.tick(observed(rule,1,2,"Shop",true,false,false,"Sell item",112),3);assertEquals(SellingRuntime.State.WAIT_SALE,r.state());
        r.tick(observed(rule,1,3,"Shop",false,false,false,"Sell item",112),4);assertEquals(SellingRuntime.State.WAIT_SALE,r.state());
        r.onMessage("You sold 4x Wheat for 12 coins!");r.tick(observed(rule,1,3,"Shop",false,false,false,"Sell item",112),5);assertEquals(SellingRuntime.State.CLOSING,r.state());}
    @Test void staleReceiptAndWrongAmountsNeverAcknowledge(){var rule=rule(AutoSellMode.NPC);var r=started(rule);r.onMessage("You sold 4x Wheat for 12 coins!");
        apply(r,observed(rule,1,1,"Shop",true,false,false,"Sell item",100),2,SellingRuntime.Kind.SELL);
        r.onMessage("You sold 3x Wheat for 12 coins!");r.onMessage("You sold 4x Wheat for 11 coins!");
        r.tick(observed(rule,1,2,"Shop",false,false,false,"Sell item",112),3);assertEquals(SellingRuntime.State.WAIT_SALE,r.state());
        r.tick(observed(rule,1,2,"Shop",false,false,false,"Sell item",112),23);assertEquals(SellingRuntime.State.FAILED,r.state());assertTrue(r.acknowledgedSale().isEmpty());}
    @Test void changedNpcQuoteIsRejectedOnFinalReRead(){var rule=rule(AutoSellMode.NPC);var r=started(rule);var original=observed(rule,1,1,"Shop",true,false,false,"Sell item",100);
        var action=r.tick(original,2).orElseThrow();var badMenu=new MenuSnapshot(1,"Shop",List.of(new MenuSnapshot.Slot(11,"Sell item",List.of("Selling: 4x Wheat","Total: 99 Coins"),false)),1);
        var changed=new SellingObservation(badMenu,original.inventory(),original.slots(),original.cursor(),original.coins(),List.of(),false);
        assertFalse(r.valid(action,changed));r.applied(action,false,2);assertEquals(SellingRuntime.State.FAILED,r.state());}
    @Test void georgeUsesAcknowledgedCursorDepositAcceptanceAndConfirmation(){var rule=rule(AutoSellMode.GEORGE_PETS);var r=started(rule);deposit(r,rule);
        apply(r,observed(rule,1,3,"Shop",false,false,true,"Accept",100),8,SellingRuntime.Kind.ACCEPT);
        r.tick(observed(rule,2,1,"Confirm",false,false,true,"Confirm Sale",100),9);assertEquals(SellingRuntime.State.READY_CONFIRM,r.state());
        apply(r,observed(rule,2,1,"Confirm",false,false,true,"Confirm Sale",100),11,SellingRuntime.Kind.CONFIRM);
        r.onMessage("You sold 1x Pet for 3 coins!");r.tick(observed(rule,2,2,"Confirm",false,false,false,"Confirm Sale",103),12);assertEquals(SellingRuntime.State.CLOSING,r.state());}
    @Test void localPetPickupAndDepositPredictionsDoNotAdvance(){var rule=rule(AutoSellMode.GEORGE_PETS);var r=started(rule);
        apply(r,observed(rule,1,1,"Shop",true,false,false,"Sell item",100),2,SellingRuntime.Kind.PICKUP);
        r.tick(observed(rule,1,1,"Shop",false,true,false,"Accept",100),3);assertEquals(SellingRuntime.State.WAIT_CURSOR,r.state());
        r.tick(observed(rule,1,2,"Shop",false,true,false,"Accept",100),4);
        apply(r,observed(rule,1,2,"Shop",false,true,false,"Accept",100),6,SellingRuntime.Kind.DEPOSIT);
        r.tick(observed(rule,1,2,"Shop",false,false,true,"Accept",100),7);assertEquals(SellingRuntime.State.WAIT_DEPOSIT,r.state());}
    @Test void stoppedDepositedPetReturnsToExactOriginalSlotWithTwoServerAcknowledgments(){var rule=rule(AutoSellMode.GEORGE_PETS);var r=started(rule);deposit(r,rule);
        var view=observed(rule,1,3,"Shop",false,false,true,"Accept",100);r.cancel(view,"Stopped",7);assertTrue(r.cleaning());
        apply(r,view,7,SellingRuntime.Kind.RETURN_DEPOSIT);
        r.tick(observed(rule,1,3,"Shop",false,true,false,"Accept",100),8);assertEquals(SellingRuntime.State.WAIT_RETURN_CURSOR,r.state());
        r.tick(observed(rule,1,4,"Shop",false,true,false,"Accept",100),9);
        apply(r,observed(rule,1,4,"Shop",false,true,false,"Accept",100),11,SellingRuntime.Kind.RETURN_ORIGIN);
        r.tick(observed(rule,1,4,"Shop",true,false,false,"Accept",100),12);assertEquals(SellingRuntime.State.WAIT_RETURN_ORIGIN,r.state());
        r.tick(observed(rule,1,5,"Shop",true,false,false,"Accept",100),13);assertEquals(SellingRuntime.State.CANCELLED,r.state());assertFalse(r.escrowHeld());assertTrue(r.acknowledgedSale().isEmpty());}
    @Test void stopDuringUnacknowledgedPickupWaitsAndDoesNotSendBlindReturn(){var rule=rule(AutoSellMode.GEORGE_PETS);var r=started(rule);
        apply(r,observed(rule,1,1,"Shop",true,false,false,"Sell item",100),2,SellingRuntime.Kind.PICKUP);
        var predicted=observed(rule,1,1,"Shop",false,true,false,"Accept",100);r.cancel(predicted,"Stopped",3);
        assertEquals(SellingRuntime.State.WAIT_RETURN_CURSOR,r.state());assertTrue(r.tick(predicted,4).isEmpty());
        r.tick(observed(rule,1,2,"Shop",true,false,false,"Accept",100),5);assertEquals(SellingRuntime.State.CANCELLED,r.state());}
    @Test void restitutionNeverOverwritesReplacedOriginSlot(){var rule=rule(AutoSellMode.GEORGE_PETS);var r=started(rule);
        apply(r,observed(rule,1,1,"Shop",true,false,false,"Sell item",100),2,SellingRuntime.Kind.PICKUP);
        var cursor=observed(rule,1,2,"Shop",false,true,false,"Accept",100);r.tick(cursor,3);r.cancel(cursor,"Stopped",4);
        assertTrue(r.tick(observed(rule,1,2,"Shop",true,true,false,"Accept",100),4).isEmpty());assertEquals(SellingRuntime.State.CANCELLED,r.state());}
    @Test void trashRequiresOneFreshServerSpawnExactItemCountAndInventoryRevision(){var rule=rule(AutoSellMode.TRASH);var r=started(rule);
        apply(r,observed(rule,1,1,"Shop",true,false,false,"Sell item",100),2,SellingRuntime.Kind.DROP);
        var drop=new SellingObservation.DroppedItem(100,rule.identity(),4,1);
        r.tick(observed(rule,1,1,"Shop",false,false,false,"Sell item",100,List.of(drop),false),3);assertEquals(SellingRuntime.State.WAIT_DROP,r.state());
        r.tick(observed(rule,1,2,"Shop",false,false,false,"Sell item",100,List.of(),false),4);assertEquals(SellingRuntime.State.WAIT_DROP,r.state());
        r.tick(observed(rule,1,2,"Shop",false,false,false,"Sell item",100,List.of(drop),false),5);assertEquals(SellingRuntime.State.CLOSING,r.state());}
    @Test void oldWrongOrAmbiguousDroppedItemsCannotAcknowledge(){var rule=rule(AutoSellMode.TRASH);var r=new SellingRuntime();var old=new SellingObservation.DroppedItem(100,rule.identity(),4,1);
        r.start(observed(rule,1,1,"Shop",true,false,false,"Sell item",100,List.of(old),false),rule,settings(rule.channel()),0);
        apply(r,observed(rule,1,1,"Shop",true,false,false,"Sell item",100,List.of(old),false),2,SellingRuntime.Kind.DROP);
        for(var drops:List.of(List.of(old),List.of(new SellingObservation.DroppedItem(101,"wrong",4,1)),List.of(new SellingObservation.DroppedItem(101,rule.identity(),3,1)),
                List.of(new SellingObservation.DroppedItem(101,rule.identity(),4,8)),List.of(new SellingObservation.DroppedItem(101,rule.identity(),4,1),new SellingObservation.DroppedItem(102,rule.identity(),4,1)))){
            r.tick(observed(rule,1,2,"Shop",false,false,false,"Sell item",100,drops,false),3);assertEquals(SellingRuntime.State.WAIT_DROP,r.state());}}
    @Test void protectedHotbarAndInvalidRuleBudgetCannotStart(){assertThrows(IllegalArgumentException.class,()->new SellingRule(AutoSellMode.TRASH,0,"minecraft:wheat","Wheat","x",1,BigDecimal.ZERO,""));
        var rule=rule(AutoSellMode.NPC);var r=new SellingRuntime();var view=observed(rule,1,1,"Shop",true,false,false,"Sell item",100);
        var protectedStack=new SellingObservation.Stack(27,9,rule.registryId(),rule.name(),rule.components(),4,true);
        r.start(new SellingObservation(view.menu(),List.of(protectedStack),view.slots(),view.cursor(),view.coins(),List.of(),false),rule,settings(rule.channel()),0);assertEquals(SellingRuntime.State.FAILED,r.state());
        var caps=new SellingRuntime.Settings(rule.channel(),"Shop","","","",13,13,"{quantity} {item} {coins}",3,BigDecimal.ONE,new BigDecimal("100"),BigDecimal.ZERO,2,20);
        r.start(view,rule,caps,0);assertEquals(SellingRuntime.State.FAILED,r.state());}
    @Test void purseParserRequiresUniqueExactPrefixAndKnownDecimal(){assertEquals(new BigDecimal("1234.50"),SellingCurrency.parse(List.of("§6Purse: 1,234.50 Coins"),"Purse:"));
        assertNull(SellingCurrency.parse(List.of("Purse: 12","Purse: 13"),"Purse:"));assertNull(SellingCurrency.parse(List.of("Purse: NaN"),"Purse:"));
        assertNull(SellingCurrency.parse(List.of("Purse: 12"),""));}
    @Test void jsonRulesHaveRequiredExactIdentityAndNoWildcardDefaults(){assertThrows(IllegalArgumentException.class,()->SellingRule.parse("[]"));
        assertThrows(IllegalArgumentException.class,()->SellingRule.parse("[{\"channel\":\"TRASH\",\"inventoryIndex\":9,\"registryId\":\"minecraft:wheat\",\"name\":\"Wheat\",\"quantity\":4,\"unitValue\":0}]"));
        assertThrows(IllegalArgumentException.class,()->SellingRule.parse("[{\"channel\":\"TRASH\",\"inventoryIndex\":9.5,\"registryId\":\"minecraft:wheat\",\"name\":\"Wheat\",\"components\":\"exact\",\"quantity\":4,\"unitValue\":0}]"));}
}
