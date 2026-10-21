package dev.veltraph.client.skills;

import dev.veltraph.client.config.AutoSellMode;
import dev.veltraph.client.gui.MenuSnapshot;
import dev.veltraph.client.inventory.selling.*;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class CarpentrySaleParentTest {
    final Object player=new Object(),world=new Object(),connection=new Object(),market=new Object(),confirmation=new Object(),sb=new Object(),cookie=new Object();
    final CarpentrySaleParent.Scope scope=new CarpentrySaleParent.Scope(player,world,connection,"frozen");
    CarpentryFlowProfile.Sale sale(){return new CarpentryFlowProfile.Sale(new CarpentryFlowProfile.Target("Bazaar",null,"","bazaar"),BigDecimal.valueOf(3),BigDecimal.valueOf(24),"Sell Inventory","Receipt {quantity} {item} {coins}",new CarpentrySaleProtocol("Confirm","Confirm Inventory",new CarpentrySaleProtocol.Fallback(new CarpentryFlowProfile.Target("SkyBlock",null,"","sb"),"Cookie",new CarpentryFlowProfile.Item("minecraft:paper","Open Cookie","button",1),"Bazaar unavailable","Unavailable","Cookie active",BigDecimal.valueOf(2),"Sell Output","Sold {quantity} {item} {coins}")));}
    CarpentrySaleParent.Observation observe(Object nativeMenu,String title,int id,int rev,int quantity,int money,boolean active,boolean rejected){
        var output=new SellingObservation.Stack(27,9,"minecraft:oak_planks","Output","exact",quantity,false);
        var tool=new SellingObservation.Stack(50,0,"minecraft:diamond_sword","Tool","protected",1,true);
        var button=new SellingObservation.Stack(11,-1,"minecraft:paper","Open Cookie","button",1,false);
        var label=title.equals("Confirm")?"Confirm Inventory":title.equals("Cookie")?"Sell Output":rejected?"Unavailable":"Sell Inventory";
        var slots=List.of(new MenuSnapshot.Slot(11,title.equals("SkyBlock")?"Open Cookie":label,List.of("Selling: 8x Output","Total: "+(title.equals("Cookie")?16:24)+" Coins"),false));
        var menu=nativeMenu==null?null:new MenuSnapshot(id,title,slots,rev);
        return new CarpentrySaleParent.Observation(scope,nativeMenu,2,active,new SellingObservation(menu,List.of(output,tool),Map.of(27,output,50,tool,11,button),null,BigDecimal.valueOf(money),List.of(),false));
    }
    CarpentrySaleParent begin(boolean reject){var p=new CarpentrySaleParent();p.start(observe(market,"Bazaar",1,1,8,100,true,reject),sale(),9,8,0,20);return p;}
    void apply(CarpentrySaleParent p,CarpentrySaleParent.Observation o,int tick){var a=p.tick(o,tick).orElseThrow(()->new AssertionError(p.status()));assertTrue(p.valid(a,o));p.applied(a,true,tick);}
    void commit(CarpentrySaleParent p){p.tick(observe(market,"Bazaar",1,1,8,100,true,false),1);apply(p,observe(market,"Bazaar",1,1,8,100,true,false),2);p.tick(observe(confirmation,"Confirm",2,1,8,100,true,false),3);apply(p,observe(confirmation,"Confirm",2,1,8,100,true,false),4);}
    @Test void bazaarContinuationRequiresActualReceiptInventoryPurseRevisionAndCleanup(){var p=begin(false);commit(p);p.onMessage("Receipt 8 Output 24");p.tick(observe(confirmation,"Confirm",2,2,0,124,true,false),5);assertTrue(p.acknowledged().isEmpty());apply(p,observe(confirmation,"Confirm",2,2,0,124,true,false),6);p.tick(observe(null,"",0,0,0,124,true,false),7);var proof=p.acknowledged().orElseThrow();assertEquals(AutoSellMode.BAZAAR,proof.channel());assertSame(confirmation,proof.nativeMenu());assertEquals(1,proof.requestRevision());assertEquals(2,proof.receiptRevision());assertEquals(1,proof.receiptSerial());assertTrue(scope.same(proof.scope()));}
    @Test void exactRejectionThenCurrentCookieRoutesOnlyOnceThroughNativeSkyblockMenu(){var p=begin(true);p.onMessage("Bazaar unavailable");apply(p,observe(market,"Bazaar",1,1,8,100,true,true),1);apply(p,observe(null,"",0,0,8,100,true,false),2);apply(p,observe(sb,"SkyBlock",3,1,8,100,true,false),3);p.tick(observe(cookie,"Cookie",4,1,8,100,true,false),4);apply(p,observe(cookie,"Cookie",4,1,8,100,true,false),5);p.onMessage("Sold 8 Output 16");p.tick(observe(cookie,"Cookie",4,2,0,116,true,false),6);apply(p,observe(cookie,"Cookie",4,2,0,116,true,false),7);p.tick(observe(null,"",0,0,0,116,true,false),8);assertEquals(AutoSellMode.NPC,p.acknowledged().orElseThrow().channel());assertSame(cookie,p.acknowledged().orElseThrow().nativeMenu());}
    @Test void unknownQuoteDoesNotInventFallback(){var p=begin(true);assertTrue(p.tick(observe(market,"Bazaar",1,1,8,100,true,true),1).isEmpty());p.tick(observe(market,"Bazaar",1,1,8,100,true,true),21);assertEquals(CarpentrySaleParent.State.FAILED,p.state());assertTrue(p.acknowledged().isEmpty());}
    @Test void staleReceiptCannotSatisfyNewBatch(){var p=begin(false);p.onMessage("Receipt 8 Output 24");commit(p);p.tick(observe(confirmation,"Confirm",2,2,0,124,true,false),5);assertEquals(CarpentrySaleParent.State.BAZAAR,p.state());assertTrue(p.acknowledged().isEmpty());}
    @Test void allPartialAndRefundedOutcomesStopWithoutFallback(){for(int omitted=0;omitted<4;omitted++){var p=begin(false);commit(p);if(omitted!=0)p.onMessage("Receipt 8 Output 24");p.onMessage("Bazaar unavailable");p.tick(observe(confirmation,"Confirm",2,omitted==1?1:2,omitted==2?8:0,omitted==3?100:124,true,false),5);assertTrue(p.acknowledged().isEmpty());assertTrue(p.tick(observe(confirmation,"Confirm",2,3,8,100,true,true),25).isEmpty());assertEquals(CarpentrySaleParent.State.FAILED,p.state());}}
    @Test void cookieAndConservedOutputAreRequiredAtRejection(){for(int change=0;change<3;change++){var p=begin(true);p.onMessage("Bazaar unavailable");assertTrue(p.tick(observe(market,"Bazaar",1,1,change==1?7:8,change==2?99:100,change!=0,true),1).isEmpty());assertTrue(p.terminal());}}
    @Test void nativeObjectSwapWithSameNumericMenuNeverAdopts(){var p=begin(false);p.tick(observe(new Object(),"Bazaar",1,1,8,100,true,false),1);assertEquals(CarpentrySaleParent.State.CANCELLED,p.state());}
    @Test void nativeConnectionOrProfileReplacementStops(){for(boolean profile:List.of(false,true)){var p=begin(false);var old=observe(market,"Bazaar",1,1,8,100,true,false);var changed=new CarpentrySaleParent.Observation(new CarpentrySaleParent.Scope(player,world,profile?connection:new Object(),profile?"edited":"frozen"),old.nativeMenu(),2,true,old.value());p.tick(changed,1);assertEquals(CarpentrySaleParent.State.CANCELLED,p.state());}}
    @Test void foreignCursorAndFinalRereadPreventMutation(){var p=begin(false);p.tick(observe(market,"Bazaar",1,1,8,100,true,false),1);var o=observe(market,"Bazaar",1,1,8,100,true,false);var a=p.tick(o,2).orElseThrow();var v=o.value();var cursor=new SellingObservation.Stack(-1,-1,"minecraft:diamond","Foreign","foreign",1,false);var altered=new CarpentrySaleParent.Observation(scope,market,2,true,new SellingObservation(v.menu(),v.inventory(),v.slots(),cursor,v.coins(),List.of(),false));assertFalse(p.valid(a,altered));p.applied(a,false,2);assertTrue(p.terminal());}
    @Test void stopAndLateReceiptCannotContinueOrCredit(){var p=begin(false);commit(p);p.cancel("manual stop");p.onMessage("Receipt 8 Output 24");assertTrue(p.tick(observe(confirmation,"Confirm",2,2,0,124,true,false),5).isEmpty());assertTrue(p.acknowledged().isEmpty());}
    @Test void aSecondBatchCannotReuseThePriorFinancialProofOrReceipt(){var p=begin(false);for(int batch=0;batch<2;batch++){if(batch>0){p.start(observe(market,"Bazaar",1,1,8,100,true,false),sale(),9,8,0,20);assertTrue(p.acknowledged().isEmpty());}commit(p);p.onMessage("Receipt 8 Output 24");p.tick(observe(confirmation,"Confirm",2,2,0,124,true,false),5);apply(p,observe(confirmation,"Confirm",2,2,0,124,true,false),6);p.tick(observe(null,"",0,0,0,124,true,false),7);assertEquals(8,p.acknowledged().orElseThrow().quantity());}}
    @Test void npcOnlyProfileCannotSilentlySelectADifferentDealer(){var json=com.google.gson.JsonParser.parseString("{\"quoteLabel\":\"NPC\"}").getAsJsonObject();assertThrows(IllegalArgumentException.class,()->CarpentrySaleProtocol.parse(json));}

    @Test void nativeNpcRequestRetainsTheExactAdapterBoundObservation(){
        var raw=observe(cookie,"Cookie",4,1,8,100,true,false).value();
        assertSame(raw,CarpentryFlowModule.saleObservation(raw,false,CarpentrySaleParent.State.COOKIE_SALE));
        assertSame(raw,CarpentryFlowModule.saleObservation(raw,true,CarpentrySaleParent.State.COOKIE_SALE));
        var bindings=new IdentityHashMap<SellingObservation,String>();bindings.put(raw,"native binding");
        assertEquals("native binding",bindings.get(CarpentryFlowModule.saleObservation(raw,false,CarpentrySaleParent.State.COOKIE_SALE)));
        var predicted=new SellingObservation(raw.menu(),raw.inventory(),raw.slots(),raw.cursor(),raw.coins(),raw.droppedItems(),true);
        assertSame(predicted,CarpentryFlowModule.saleObservation(predicted,true,CarpentrySaleParent.State.COOKIE_SALE));
        var enriched=CarpentryFlowModule.saleObservation(raw,true,CarpentrySaleParent.State.BAZAAR);assertNotSame(raw,enriched);assertTrue(enriched.ownCursorPrediction());assertEquals(raw.ledger(),enriched.ledger());
    }

}
