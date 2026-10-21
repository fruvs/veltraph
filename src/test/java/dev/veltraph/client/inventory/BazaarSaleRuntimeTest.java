package dev.veltraph.client.inventory;
import dev.veltraph.client.gui.MenuSnapshot;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class BazaarSaleRuntimeTest {
    private static final BazaarSaleRuntime.Settings SETTINGS=new BazaarSaleRuntime.Settings("Bazaar","Sell Inventory Now","Confirm Inventory Sale","Confirm",64,1,100,2,20);
    private static List<BazaarSaleRuntime.Item> inventory(int count){return List.of(new BazaarSaleRuntime.Item("minecraft:wheat","Wheat",count));}
    private static MenuSnapshot menu(int id,String title,String name,String... lore){return new MenuSnapshot(id,title,List.of(new MenuSnapshot.Slot(11,name,List.of(lore),false)),1);}
    private static MenuSnapshot startMenu(){return menu(1,"Bazaar","Sell Inventory Now","Selling: 4x Wheat","Total: 12 Coins");}
    private static MenuSnapshot confirm(){return menu(2,"Confirm Inventory Sale","Confirm","Selling: 4x Wheat","Total: 12 Coins");}
    private static BazaarSaleRuntime started(){var r=new BazaarSaleRuntime();r.start(startMenu(),inventory(8),SETTINGS,0,true);assertEquals(BazaarSaleRuntime.State.READY,r.state());return r;}
    private static void confirmSale(BazaarSaleRuntime r){var a=r.tick(startMenu(),inventory(8),2,true).orElseThrow();r.applied(a,true,2);
        assertTrue(r.tick(confirm(),inventory(8),3,true).isEmpty());a=r.tick(confirm(),inventory(8),5,true).orElseThrow();r.applied(a,true,5);}
    @Test void actualQuoteInventoryMessageAndCloseAreAllRequired(){
        var r=started();confirmSale(r);r.tick(confirm(),inventory(4),6,true);assertEquals(BazaarSaleRuntime.State.WAIT_ACK,r.state());
        r.onMessage("[Bazaar] Sold 4x Wheat for 12 coins!");r.tick(confirm(),inventory(4),7,true);assertEquals(BazaarSaleRuntime.State.CLOSING,r.state());
        var close=r.tick(confirm(),inventory(4),8,true).orElseThrow();assertTrue(close.close());r.applied(close,true,8);
        r.tick(null,inventory(4),9,true);assertEquals(BazaarSaleRuntime.State.COMPLETE,r.state());
    }
    @Test void chatAndMenuAloneDoNotCompleteOrRetry(){var r=started();confirmSale(r);r.onMessage("[Bazaar] Sold 4 items for 12 coins!");
        assertTrue(r.tick(confirm(),inventory(8),8,true).isEmpty());assertEquals(BazaarSaleRuntime.State.WAIT_ACK,r.state());
        r.tick(confirm(),inventory(8),26,true);assertEquals(BazaarSaleRuntime.State.FAILED,r.state());}
    @Test void staleAcknowledgmentAndWrongQuantityAreIgnored(){var r=started();r.onMessage("[Bazaar] Sold 4x Wheat for 12 coins!");confirmSale(r);
        r.onMessage("[Bazaar] Sold 5 items for 12 coins!");r.tick(confirm(),inventory(4),7,true);assertEquals(BazaarSaleRuntime.State.WAIT_ACK,r.state());
        r.onMessage("[Bazaar] Sold 4 items for 13 coins!");r.tick(confirm(),inventory(4),8,true);assertEquals(BazaarSaleRuntime.State.WAIT_ACK,r.state());}
    @Test void unrelatedInventoryChangesCannotSatisfySale(){var r=started();confirmSale(r);r.onMessage("[Bazaar] Sold 4 items for 12 coins!");
        var changed=new ArrayList<>(inventory(4));changed.add(new BazaarSaleRuntime.Item("minecraft:carrot","Carrot",1));
        r.tick(confirm(),changed,7,true);assertEquals(BazaarSaleRuntime.State.WAIT_ACK,r.state());}
    @Test void freshQuoteChangesFailBeforeConfirmation(){var r=started();var a=r.tick(startMenu(),inventory(8),2,true).orElseThrow();r.applied(a,true,2);
        var changed=menu(2,"Confirm Inventory Sale","Confirm","Selling: 4x Wheat","Total: 13 Coins");r.tick(changed,inventory(8),3,true);
        assertTrue(r.tick(changed,inventory(8),5,true).isEmpty());assertEquals(BazaarSaleRuntime.State.FAILED,r.state());}
    @Test void reReadGuardRejectsSameNameChangedLoreAndRevision(){var r=started();var a=r.tick(startMenu(),inventory(8),2,true).orElseThrow();
        assertTrue(r.validates(a,startMenu()));assertFalse(r.validates(a,menu(1,"Bazaar","Sell Inventory Now","Selling: 4x Wheat","Total: 99 Coins")));
        assertFalse(r.validates(a,new MenuSnapshot(1,"Bazaar",startMenu().slots(),2)));r.applied(a,false,2);assertEquals(BazaarSaleRuntime.State.FAILED,r.state());}
    @Test void quantityCostIdentityAndCursorGuards(){
        for(var m:List.of(menu(1,"Bazaar","Sell Inventory Now","Selling: 65x Wheat","Total: 12 Coins"),menu(1,"Bazaar","Sell Inventory Now","Selling: 4x Wheat","Total: 101 Coins"),
                menu(1,"Bazaar","Sell Inventory Now","Selling: 4x Wheat","Total: 0 Coins"),menu(1,"Bazaar","Sell Inventory Now","Selling: 4x Wheat","Price: 12 Coins"))){
            var r=new BazaarSaleRuntime();r.start(m,inventory(100),SETTINGS,0,true);assertEquals(BazaarSaleRuntime.State.FAILED,r.state());}
        var r=new BazaarSaleRuntime();r.start(startMenu(),List.of(new BazaarSaleRuntime.Item("minecraft:carrot","Wheat",8),new BazaarSaleRuntime.Item("minecraft:wheat","Wheat",8)),SETTINGS,0,true);assertEquals(BazaarSaleRuntime.State.FAILED,r.state());
        r.start(startMenu(),List.of(new BazaarSaleRuntime.Item("minecraft:wheat","Wheat",4,"plain"),new BazaarSaleRuntime.Item("minecraft:wheat","Wheat",4,"enchanted")),SETTINGS,0,true);assertEquals(BazaarSaleRuntime.State.FAILED,r.state());
        r.start(startMenu(),inventory(8),SETTINGS,0,false);assertEquals(BazaarSaleRuntime.State.FAILED,r.state());
        r=started();r.tick(startMenu(),inventory(8),2,false);assertEquals(BazaarSaleRuntime.State.CANCELLED,r.state());
    }
    @Test void unexpectedMenuAndInputChangesCancel(){var r=started();r.tick(menu(3,"Other","Confirm"),inventory(8),2,true);assertEquals(BazaarSaleRuntime.State.CANCELLED,r.state());
        r=started();r.tick(startMenu(),inventory(7),2,true);assertEquals(BazaarSaleRuntime.State.CANCELLED,r.state());}
    @Test void throttleStopsWithoutRetries(){var r=started();var a=r.tick(startMenu(),inventory(8),2,true).orElseThrow();r.applied(a,true,2);
        r.onMessage("This menu has been throttled! Please slow down");assertEquals(BazaarSaleRuntime.State.FAILED,r.state());assertTrue(r.tick(confirm(),inventory(8),10,true).isEmpty());}
    @Test void exactOwnPickupPredictionWaitsForServerClearWithoutCompletion(){var r=started();var a=r.tick(startMenu(),inventory(8),2,true).orElseThrow();r.applied(a,true,2);
        assertTrue(r.tick(startMenu(),inventory(8),3,false,true).isEmpty());assertEquals(BazaarSaleRuntime.State.WAIT_CONFIRM,r.state());
        r.tick(confirm(),inventory(8),4,true);a=r.tick(confirm(),inventory(8),6,true).orElseThrow();r.applied(a,true,6);
        r.onMessage("[Bazaar] Sold 4 items for 12 coins!");r.tick(confirm(),inventory(4),7,false,true);assertEquals(BazaarSaleRuntime.State.WAIT_ACK,r.state());
        r.tick(confirm(),inventory(4),8,true);assertEquals(BazaarSaleRuntime.State.CLOSING,r.state());}
    @Test void duplicateAmbiguousPlayerLockedAndMalformedQuoteRejected(){
        for(var slot:List.of(new MenuSnapshot.Slot(11,"Confirm",List.of("Selling: 4x Wheat","Total: 12 Coins"),true),
                new MenuSnapshot.Slot(11,"Confirm",List.of("Selling: 4x Wheat","Total: 12 Coins"),false,"minecraft:barrier"),
                new MenuSnapshot.Slot(11,"Confirm",List.of("Selling: 4x Wheat","Selling: 2x Wheat","Total: 12 Coins"),false),
                new MenuSnapshot.Slot(11,"Confirm",List.of("Selling: 4x Wheat","Total: NaN Coins"),false),
                new MenuSnapshot.Slot(11,"Confirm",List.of("Selling: 4,,0x Wheat","Total: 12 Coins"),false))){assertTrue(BazaarSaleRuntime.parseQuote(slot).isEmpty());}
        var r=new BazaarSaleRuntime();r.start(new MenuSnapshot(1,"Bazaar",List.of(startMenu().slots().getFirst(),startMenu().slots().getFirst())),inventory(8),SETTINGS,0,true);assertEquals(BazaarSaleRuntime.State.FAILED,r.state());
    }
}
