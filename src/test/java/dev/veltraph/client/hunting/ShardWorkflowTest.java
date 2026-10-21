package dev.veltraph.client.hunting;

import dev.veltraph.client.gui.MenuSnapshot;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ShardWorkflowTest {
    static final ShardCatalog CATALOG=ShardCatalog.parse("Cod=COMMON;Reptiloid=LEGENDARY;Naga=RARE");
    static ShardWorkflow.Settings settings(boolean mass){return new ShardWorkflow.Settings(true,true,true,mass,2,100,1000,64,1000,CATALOG,List.of("Cod","Reptiloid"),
        "Shard menu","Confirm syphon","Mass","Confirm","Attributes","Enable advanced","Advanced active","Next","Progress: ([0-9]+)",
        "Bazaar","Buy Instantly","Amount","Buy one","Custom","Purchase","Buy","Price: ([0-9,.]+) Coins");}
    static MenuSnapshot menu(int id,int revision,String title,MenuSnapshot.Slot... slots){return new MenuSnapshot(id,title,List.of(slots),revision);}
    static MenuSnapshot.Slot button(int slot,String name,String... lore){return new MenuSnapshot.Slot(slot,name,List.of(lore),false);}
    static ShardWorkflow.Observation obs(MenuSnapshot menu,int cod){return new ShardWorkflow.Observation(menu,Map.of("Cod",cod,"Naga",0,"Reptiloid",0),true,false);}
    static final class Actions implements ShardWorkflow.Actions {
        int clicks,opens,closes,uses,signs,cleanups;int entered;
        public boolean openShards(){opens++;return true;}public boolean openAttributes(){opens++;return true;}
        public boolean searchShard(String name){opens++;return true;}
        public boolean click(MenuSnapshot m,MenuSnapshot.Slot s){clicks++;return true;}
        public boolean close(MenuSnapshot m){closes++;return true;}
        public boolean enterAmount(int q){signs++;entered=q;return true;}
        public boolean consume(String n,int q){uses++;return true;}
        public void cleanup(){cleanups++;}
    }
    @Test void exactRecoveredCapturePatternsAndCatalog(){
        var tracker=new ShardCaptureTracker();
        assertTrue(tracker.observe("§aYou caught x3 Cod Shards!",CATALOG));
        assertTrue(tracker.observe("CHARM You charmed a Naga and captured its Shard.",CATALOG));
        assertTrue(tracker.observe("naga You charmed a Cod and captured 5 Shards from it.",CATALOG));
        assertTrue(tracker.observe("Cod Shard ( 12.5% )",CATALOG));
        assertFalse(tracker.observe("You caught a Unknown Shard!",CATALOG));
        assertFalse(tracker.observe("[Player] You caught a Cod Shard!",CATALOG));
        assertFalse(tracker.observe("You caught x0 Cod Shards!",CATALOG));
        assertFalse(tracker.observe("You caught x999999999999999 Cod Shards!",CATALOG));
        assertEquals(Map.of("Cod",9,"Naga",1),tracker.counts());tracker.reset();assertTrue(tracker.counts().isEmpty());
    }
    @Test void exactCostTablesAndPartialPaidSemantics(){
        int[] totals={96,64,48,32,24};
        for(var rarity:ShardCatalog.Rarity.values())assertEquals(totals[rarity.ordinal()],ShardCosts.needed(rarity,0,10,null));
        assertEquals(3,ShardCosts.needed(ShardCatalog.Rarity.COMMON,1,2,null));
        assertEquals(0,ShardCosts.needed(ShardCatalog.Rarity.COMMON,1,2,0));
        assertEquals(2,ShardCosts.needed(ShardCatalog.Rarity.COMMON,1,2,2));
        assertEquals(3,ShardCosts.needed(ShardCatalog.Rarity.COMMON,1,2,999));
        assertEquals(0,ShardCosts.needed(ShardCatalog.Rarity.COMMON,10,10,null));
        assertThrows(IllegalArgumentException.class,()->ShardCosts.needed(ShardCatalog.Rarity.COMMON,-1,1,null));
        int[] copy=ShardCosts.table(ShardCatalog.Rarity.COMMON);copy[1]=99;assertEquals(1,ShardCosts.table(ShardCatalog.Rarity.COMMON)[1]);
    }
    @Test void massSyphonRequiresActualInventoryAndMenuAckAndNeverRepeatsMutation(){
        var actions=new Actions();var runtime=new ShardWorkflow(actions);var settings=settings(true);
        var initial=menu(1,1,"Shard menu",button(10,"Mass"));
        assertTrue(runtime.start(settings,obs(initial,4),0));runtime.tick(true,settings,obs(initial,4),1);
        runtime.tick(true,settings,obs(initial,4),2);assertEquals(1,actions.clicks);
        runtime.tick(true,settings,new ShardWorkflow.Observation(initial,obs(initial,4).inventory(),false,false),3);
        assertTrue(runtime.active());assertEquals(1,actions.clicks);
        for(int tick=4;tick<30;tick++)runtime.tick(true,settings,obs(initial,4),tick);
        assertEquals(1,actions.clicks);
        var confirm=menu(2,1,"Confirm syphon",button(11,"Confirm"));
        runtime.tick(true,settings,obs(confirm,4),31);runtime.tick(true,settings,obs(confirm,4),32);assertEquals(2,actions.clicks);
        runtime.tick(true,settings,obs(initial,4),33);assertEquals(ShardWorkflow.State.WAIT_SYPHON_ACK,runtime.state());
        runtime.tick(true,settings,obs(confirm,0),34);assertEquals(ShardWorkflow.State.WAIT_SYPHON_ACK,runtime.state());
        runtime.tick(true,settings,obs(initial,0),35);runtime.tick(true,settings,obs(initial,0),36);assertEquals(1,actions.closes);
        runtime.tick(true,settings,obs(null,0),37);assertEquals(ShardWorkflow.State.COMPLETE,runtime.state());
        assertEquals(2,actions.clicks);
    }
    @Test void guardsRefuseDuplicatePlayerSlotsMissingMatchersAndLostAck(){
        var actions=new Actions();var runtime=new ShardWorkflow(actions);var settings=settings(true);
        var initial=menu(1,1,"Shard menu",button(10,"Mass"),button(11,"Mass"));
        runtime.start(settings,obs(initial,1),0);runtime.tick(true,settings,obs(initial,1),1);runtime.tick(true,settings,obs(initial,1),2);
        assertEquals(ShardWorkflow.State.FAILED,runtime.state());assertEquals(0,actions.clicks);
        initial=menu(2,1,"Shard menu",new MenuSnapshot.Slot(10,"Mass",List.of(),true));
        runtime.start(settings,obs(initial,1),3);runtime.tick(true,settings,obs(initial,1),4);runtime.tick(true,settings,obs(initial,1),5);
        assertEquals(0,actions.clicks);runtime.tick(true,settings,obs(initial,1),1005);assertEquals(ShardWorkflow.State.FAILED,runtime.state());
        runtime.start(settings,obs(menu(3,1,"Shard menu",button(10,"Mass")),1),2000);runtime.cancel();
        assertEquals(ShardWorkflow.State.CANCELLED,runtime.state());assertEquals(0,actions.clicks);
    }
    @Test void attributePaginationAndCustomPurchaseWaitForChatAndInventory(){
        var actions=new Actions();var runtime=new ShardWorkflow(actions);var cfg=settings(false);
        var attrs=menu(1,1,"Attributes",button(1,"Advanced active"),button(10,"Cod I","Progress: 2"),button(43,"Next"));
        runtime.start(cfg,obs(attrs,0),0);runtime.tick(true,cfg,obs(attrs,0),1);runtime.tick(true,cfg,obs(attrs,0),2);runtime.tick(true,cfg,obs(attrs,0),3);
        assertEquals(1,actions.clicks);
        var second=menu(1,2,"Attributes",button(1,"Advanced active"),button(11,"Reptiloid I"));
        runtime.tick(true,cfg,obs(second,0),4);runtime.tick(true,cfg,obs(second,0),5);
        assertEquals(List.of(new ShardWorkflow.Job("Cod",ShardCatalog.Rarity.COMMON,2)),runtime.jobs());
        runtime.tick(true,cfg,obs(second,0),6);runtime.tick(true,cfg,obs(null,0),7);runtime.tick(true,cfg,obs(null,0),8);
        var buy=menu(2,1,"Bazaar",button(13,"Buy Instantly","Price: 5.5 Coins"));
        runtime.tick(true,cfg,obs(buy,0),9);runtime.tick(true,cfg,obs(buy,0),10);runtime.tick(true,cfg,obs(buy,0),11);
        var amount=menu(3,1,"Amount",button(10,"Custom"));
        runtime.tick(true,cfg,obs(amount,0),12);runtime.tick(true,cfg,obs(amount,0),13);
        runtime.tick(true,cfg,new ShardWorkflow.Observation(null,obs(null,0).inventory(),true,true),14);
        assertEquals(2,actions.entered);
        var purchase=menu(4,1,"Purchase",button(10,"Buy","Price: 11 Coins"));
        runtime.tick(true,cfg,obs(purchase,0),15);assertEquals(ShardWorkflow.State.AWAIT_BOUGHT_ONE,runtime.state());
        runtime.tick(true,cfg,obs(purchase,2),16);assertEquals(ShardWorkflow.State.AWAIT_BOUGHT_ONE,runtime.state());
        runtime.message("[Bazaar] Bought 2 Cod Shards for 11 coins!");runtime.tick(true,cfg,obs(purchase,2),17);
        assertEquals(11,runtime.sessionCost());runtime.tick(true,cfg,obs(purchase,2),18);runtime.tick(true,cfg,obs(null,2),19);
        runtime.tick(true,cfg,obs(null,2),20);assertEquals(1,actions.uses);
        runtime.tick(true,cfg,obs(null,2),21);assertEquals(ShardWorkflow.State.WAIT_CONSUME_ACK,runtime.state());
        runtime.tick(true,cfg,obs(null,0),22);runtime.tick(true,cfg,obs(null,0),23);assertEquals(ShardWorkflow.State.COMPLETE,runtime.state());
    }
    @Test void priceGuardRejectsAmbiguousOrUnknownAndUsesRecoveredRounding(){
        assertEquals(1001,ShardWorkflow.price(button(1,"Buy","Price: 1,000.5 Coins"),settings(false).priceRegex()).orElseThrow());
        assertTrue(ShardWorkflow.price(button(1,"Buy","Price: 2 Coins","Price: 3 Coins"),settings(false).priceRegex()).isEmpty());
        assertTrue(ShardWorkflow.price(button(1,"Buy","No quote"),settings(false).priceRegex()).isEmpty());
        assertTrue(ShardWorkflow.price(new MenuSnapshot.Slot(1,"Buy",List.of("Price: 2 Coins"),false,"minecraft:barrier"),settings(false).priceRegex()).isEmpty());
        assertEquals(2,ShardWorkflow.price(new MenuSnapshot.Slot(1,"Buy",List.of("Price: 2 Coins"),false,"minecraft:written_book"),settings(false).priceRegex()).orElseThrow());
    }
    @Test void buyOneRequoteCannotExceedRecordedPrice(){
        var actions=new Actions();var runtime=new ShardWorkflow(actions);var cfg=settings(false);
        var attrs=menu(1,1,"Attributes",button(1,"Advanced active"),button(10,"Cod I","Progress: 1"));
        runtime.start(cfg,obs(attrs,0),0);runtime.tick(true,cfg,obs(attrs,0),1);runtime.tick(true,cfg,obs(attrs,0),2);runtime.tick(true,cfg,obs(attrs,0),3);
        runtime.tick(true,cfg,obs(attrs,0),4);runtime.tick(true,cfg,obs(null,0),5);
        var buy=menu(2,1,"Bazaar",button(13,"Buy Instantly","Price: 5 Coins"));
        runtime.tick(true,cfg,obs(buy,0),6);runtime.tick(true,cfg,obs(buy,0),7);runtime.tick(true,cfg,obs(buy,0),8);
        assertEquals(1,actions.clicks);
        var quantity=menu(3,1,"Amount",button(10,"Buy one","Price: 101 Coins"));
        runtime.tick(true,cfg,obs(quantity,0),9);runtime.tick(true,cfg,obs(quantity,0),10);
        assertEquals(ShardWorkflow.State.FAILED,runtime.state());assertEquals(1,actions.clicks);assertEquals(0,runtime.sessionCost());
    }
    @Test void completedMutationDoesNotCloseAnUnrelatedVanillaMenu(){
        var actions=new Actions();var runtime=new ShardWorkflow(actions);var cfg=settings(true);
        var shards=menu(1,1,"Shard menu",button(10,"Mass"));var confirm=menu(2,1,"Confirm syphon",button(11,"Confirm"));
        runtime.start(cfg,obs(shards,1),0);runtime.tick(true,cfg,obs(shards,1),1);runtime.tick(true,cfg,obs(shards,1),2);
        runtime.tick(true,cfg,obs(confirm,1),3);runtime.tick(true,cfg,obs(confirm,1),4);runtime.tick(true,cfg,obs(shards,0),5);
        assertEquals(ShardWorkflow.State.EXIT_SHARDS_MENU,runtime.state());
        runtime.tick(true,cfg,obs(menu(3,1,"Vanilla chest",button(10,"Wheat")),0),6);
        assertEquals(ShardWorkflow.State.CANCELLED,runtime.state());assertEquals(0,actions.closes);assertEquals(2,actions.clicks);
    }
    @Test void inventoryReadyRecoveryDoesNotBuyDuplicateShards(){
        var actions=new Actions();var runtime=new ShardWorkflow(actions);var cfg=settings(false);
        var attrs=menu(1,1,"Attributes",button(1,"Advanced active"),button(10,"Cod I","Progress: 2"));
        runtime.start(cfg,obs(attrs,2),0);runtime.tick(true,cfg,obs(attrs,2),1);runtime.tick(true,cfg,obs(attrs,2),2);runtime.tick(true,cfg,obs(attrs,2),3);
        runtime.tick(true,cfg,obs(attrs,2),4);runtime.tick(true,cfg,obs(null,2),5);runtime.tick(true,cfg,obs(null,2),6);runtime.tick(true,cfg,obs(null,2),7);
        assertEquals(1,actions.uses);assertEquals(0,actions.clicks);assertEquals(0,actions.opens);
        runtime.tick(true,cfg,obs(null,0),8);runtime.tick(true,cfg,obs(null,0),9);
        assertEquals(ShardWorkflow.State.COMPLETE,runtime.state());assertEquals(0,runtime.sessionCost());
    }
    @Test void sourceSearchPrefixIsLowercaseAndBoundedWhileProductIdentityRemainsExact(){
        assertEquals("cod shard",ShardSearch.query("Cod Shard",12));
        assertEquals("long attribu",ShardSearch.query("Long Attribute Name Shard",12));
        assertEquals("long attribute n",ShardSearch.query("Long Attribute Name Shard",16));
        assertThrows(IllegalArgumentException.class,()->ShardSearch.query("Cod Shard",11));
    }
}
