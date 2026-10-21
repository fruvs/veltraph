package dev.veltraph.client.garden.visitor;
import dev.veltraph.client.gui.MenuSnapshot;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class VisitorBazaarFlowTest {
    private static MenuSnapshot menu(int revision,String name,String... lore){return new MenuSnapshot(7,"Wheat",List.of(new MenuSnapshot.Slot(13,name,List.of(lore),false)),revision);}
    private static void confirm(VisitorBazaarFlow flow,boolean buy){
        flow.start("Wheat",4,8,buy,20,0,1000);
        var product=new MenuSnapshot(7,"Wheat",List.of(new MenuSnapshot.Slot(11,"Wheat",List.of(),false),new MenuSnapshot.Slot(13,"Buy Instantly",List.of(),false)),1);
        assertEquals(VisitorBazaarFlow.Kind.CLICK,flow.tick(product,false,8,10).kind());
        assertEquals(VisitorBazaarFlow.Kind.NONE,flow.tick(product,false,8,20).kind());
        assertEquals(VisitorBazaarFlow.Kind.CLICK,flow.tick(menu(2,"Custom Amount"),false,8,30).kind());
        assertEquals("4",flow.tick(null,true,8,40).text());
    }
    @Test void quoteOnlyNeverClicksPurchase(){var flow=new VisitorBazaarFlow();confirm(flow,false);assertEquals(VisitorBazaarFlow.Kind.CLOSE,flow.tick(menu(3,"Confirm Instant Buy","Buying: 4x Wheat","Cost: 12 Coins"),false,8,50).kind());assertEquals(3,flow.quote().unitCeiling());assertEquals(VisitorBazaarFlow.State.COMPLETE,flow.state());}
    @Test void purchaseRequiresMatchingServerMessageAndExactInventoryIncrease(){
        var flow=new VisitorBazaarFlow();confirm(flow,true);assertEquals(VisitorBazaarFlow.Kind.CLICK,flow.tick(menu(3,"Confirm Instant Buy","Buying: 4x Wheat","Cost: 12 Coins"),false,8,50).kind());
        flow.tick(null,false,12,60);assertEquals(VisitorBazaarFlow.State.ACK,flow.state());
        flow.message("[Bazaar] Bought 4x Wheat for 12 coins!");flow.tick(null,false,11,70);assertEquals(VisitorBazaarFlow.State.ACK,flow.state());
        flow.tick(null,false,12,80);assertEquals(VisitorBazaarFlow.State.COMPLETE,flow.state());
    }
    @Test void unknownWrongProductQuantityAndCostNeverBuy(){
        for(String[] lines:List.of(new String[]{"Buying: 4x Carrot","Cost: 12 Coins"},new String[]{"Buying: 5x Wheat","Cost: 12 Coins"},new String[]{"Buying: 4x Wheat","Cost: 21 Coins"},new String[]{"Buying: 4x Wheat"})){
            var flow=new VisitorBazaarFlow();confirm(flow,true);assertEquals(VisitorBazaarFlow.Kind.NONE,flow.tick(menu(3,"Confirm Instant Buy",lines),false,8,50).kind());assertEquals(VisitorBazaarFlow.State.FAILED,flow.state());
        }
    }
    @Test void ackTimeoutNeverRetriesConfirmation(){var flow=new VisitorBazaarFlow();confirm(flow,true);flow.tick(menu(3,"Confirm Instant Buy","Buying: 4x Wheat","Cost: 12 Coins"),false,8,50);assertEquals(VisitorBazaarFlow.Kind.NONE,flow.tick(null,false,8,1000).kind());assertEquals(VisitorBazaarFlow.State.FAILED,flow.state());}
    @Test void pricesRoundUpAndOverflowOrConflictingQuotesFail(){assertEquals(4,VisitorBazaarFlow.readQuote(List.of("Buying: 4x Wheat","Cost: 12.1 Coins"),"Wheat",4).orElseThrow().unitCeiling());assertTrue(VisitorBazaarFlow.readQuote(List.of("Buying: 4x Wheat","Cost: 1 Coins","Cost: 2 Coins"),"Wheat",4).isEmpty());assertTrue(VisitorBazaarFlow.readQuote(List.of("Buying: 4x Wheat","Cost: 999999999999999999999999 Coins"),"Wheat",4).isEmpty());}
    @Test void separateItemAmountAndPriceLinesBindSameProductAndExactQuantity(){
        var quote=VisitorBazaarFlow.readQuote(List.of("Item: Wheat","Amount: 4x","Price: 12.1 coins","Price per unit: 3.025 coins"),"Wheat",4).orElseThrow();
        assertEquals(13,quote.total());assertTrue(VisitorBazaarFlow.readQuote(List.of("Item: Wheat","Amount: 5x","Price: 12 coins"),"Wheat",4).isEmpty());
    }
    @Test void contradictoryPurchaseMessageFails(){var flow=new VisitorBazaarFlow();confirm(flow,true);flow.tick(menu(3,"Confirm Instant Buy","Buying: 4x Wheat","Cost: 12 Coins"),false,8,50);flow.message("[Bazaar] Bought 5x Wheat for 12 coins!");assertEquals(VisitorBazaarFlow.State.FAILED,flow.state());}
    @Test void serverReportedChargeCannotExceedFinalQuote(){var flow=new VisitorBazaarFlow();confirm(flow,true);flow.tick(menu(3,"Confirm Instant Buy","Buying: 4x Wheat","Cost: 12 Coins"),false,8,50);flow.message("[Bazaar] Bought 4x Wheat for 13 coins!");assertEquals(VisitorBazaarFlow.State.FAILED,flow.state());}
    @Test void loweringBudgetBeforeConfirmationPreventsPurchase(){var flow=new VisitorBazaarFlow();confirm(flow,true);flow.capAtMost(10);assertEquals(VisitorBazaarFlow.Kind.NONE,flow.tick(menu(3,"Confirm Instant Buy","Buying: 4x Wheat","Cost: 12 Coins"),false,8,50).kind());assertEquals(VisitorBazaarFlow.State.FAILED,flow.state());}
    @Test void observedHypixelAmountMenuTitleIsSupported(){
        var flow=new VisitorBazaarFlow();flow.start("Wheat",4,8,false,20,0,1000);
        flow.tick(new MenuSnapshot(1,"Bazaar ➜ Wheat",List.of(new MenuSnapshot.Slot(11,"Wheat",List.of(),false),new MenuSnapshot.Slot(13,"Buy Instantly",List.of(),false)),1),false,8,10);
        var amount=new MenuSnapshot(2,"How many do you want?",List.of(new MenuSnapshot.Slot(13,"Custom Amount",List.of(),false)),1);
        assertEquals(VisitorBazaarFlow.Kind.CLICK,flow.tick(amount,false,8,20).kind());
    }
}
