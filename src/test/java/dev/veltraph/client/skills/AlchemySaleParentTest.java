package dev.veltraph.client.skills;

import dev.veltraph.client.config.AutoSellMode;
import dev.veltraph.client.gui.MenuSnapshot;
import dev.veltraph.client.inventory.selling.*;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AlchemySaleParentTest {
    static final AlchemyFlowProfile.Item ICON=new AlchemyFlowProfile.Item("minecraft:paper","Bound opener","[exact]",1);
    static AlchemySaleProfile profile(){return new AlchemySaleProfile(8,ICON,"Bound SkyBlock",22,ICON,List.of("Bound cookie lore"),"Bound cookie dealer","Exact quote",new BigDecimal("4"),"Sold {quantity} {item} for {coins}","Purse:","Cookie active", "",1,40);}
    static SellingObservation.Stack stack(int slot,int index,String id,String name){return new SellingObservation.Stack(slot,index,id,name,"["+name+"]",1,false);}
    static final class Port implements AlchemySaleParent.Port {
        Object player=new Object(),world=new Object(),connection=new Object(),menu=new Object();
        final Map<Integer,SellingObservation.Stack> inventory=new TreeMap<>();
        final List<Integer> sold=new ArrayList<>();
        final List<Object> saleMenus=new ArrayList<>();
        String title="";int selected=3,revision=2,opens,clicks,restores,closes;boolean cookie=true,foreignCursor,withholdInventory,withholdPurse,rejectDispatch;
        BigDecimal purse=new BigDecimal("100");
        SellingRuntime.Action pending;
        Port(){inventory.put(9,stack(-1,9,"minecraft:potion","First"));inventory.put(10,stack(-1,10,"minecraft:potion","Second"));inventory.put(11,stack(-1,11,"minecraft:potion","Protected preexisting"));inventory.put(8,stack(-1,8,"minecraft:paper","Bound opener"));}
        SellingObservation sale(){
            List<SellingObservation.Stack> items=new ArrayList<>();Map<Integer,SellingObservation.Stack> slots=new HashMap<>();
            for(var s:inventory.values()){var value=new SellingObservation.Stack(title.isEmpty()?-1:s.inventoryIndex()+40,s.inventoryIndex(),s.registryId(),s.name(),s.components(),s.count(),false);items.add(value);if(value.slot()>=0)slots.put(value.slot(),value);}
            List<MenuSnapshot.Slot> buttons=new ArrayList<>();
            var quote=inventory.get(sold.isEmpty()?9:10);
            if(title.equals("Bound cookie dealer")&&quote!=null)buttons.add(new MenuSnapshot.Slot(12,"Exact quote",List.of("Selling: 1x "+quote.name(),"Total: 4 Coins"),false));
            var screen=title.isEmpty()?null:new MenuSnapshot(7,title,buttons,revision);
            return new SellingObservation(screen,items,slots,foreignCursor?stack(-1,-1,"minecraft:diamond","Foreign"):null,purse,List.of(),false);
        }
        public AlchemySaleParent.Observation read(AlchemySaleProfile p){return new AlchemySaleParent.Observation(new AlchemySaleParent.Scope(player,world,connection),menu,sale(),title.isEmpty(),cookie,selected);}
        public boolean openSkyblock(AlchemySaleParent.Observation e,AlchemySaleProfile p){opens++;selected=8;title=p.skyblockTitle();menu=new Object();revision=2;return true;}
        public boolean openCookie(AlchemySaleParent.Observation e,AlchemySaleProfile p){clicks++;title=p.saleTitle();menu=new Object();revision=3;return true;}
        public boolean act(SellingRuntime.Action action,SellingRule rule,AlchemySaleProfile p){if(rejectDispatch)return false;if(action.kind()==SellingRuntime.Kind.SELL){pending=action;sold.add(rule.inventoryIndex());saleMenus.add(menu);if(!withholdInventory)inventory.remove(rule.inventoryIndex());if(!withholdPurse)purse=purse.add(rule.total());revision++;}else if(action.kind()==SellingRuntime.Kind.CLOSE){title="";menu=new Object();closes++;}else fail("Unexpected sale action "+action.kind());return true;}
        public void closeOwned(Object m,AlchemySaleProfile p){if(menu==m){title="";menu=new Object();closes++;}}
        public void restoreSelectedSlot(int previous,int issued){if(selected==issued){selected=previous;restores++;}}
    }
    static List<SellingObservation.Stack> queue(Port port){return List.of(port.inventory.get(9),port.inventory.get(10));}
    static AlchemySaleParent start(Port p){var parent=new AlchemySaleParent(p);parent.start(profile(),queue(p),0);return parent;}
    static void run(AlchemySaleParent parent,Port p,boolean receipt){for(int t=1;t<150&&parent.active();t++){parent.tick(t);if(p.pending!=null){if(receipt)parent.onMessage("Sold 1 "+p.pending.label()+" for 4");p.pending=null;}}}
    @Test void exactFifoReceiptsCompleteAndProtectUnrelatedPotion(){var p=new Port();var parent=start(p);run(parent,p,true);assertEquals(AlchemySaleParent.State.COMPLETE,parent.state(),parent.reason());assertEquals(List.of(9,10),p.sold);assertEquals(2,parent.acknowledgedCount());assertTrue(p.inventory.containsKey(11));assertEquals(new BigDecimal("108"),p.purse);assertEquals(3,p.selected);assertEquals(1,p.opens);assertEquals(1,p.clicks);assertEquals(1,p.closes);assertSame(p.saleMenus.get(0),p.saleMenus.get(1));assertTrue(parent.proofs().stream().allMatch(r->r.receiptRevision()!=r.requestRevision()&&r.matchedReceiptSerial()>0));}
    @Test void missingReceiptDoesNotCreditRetryOrContinue(){var p=new Port();var parent=start(p);run(parent,p,false);assertEquals(AlchemySaleParent.State.FAILED,parent.state());assertEquals(List.of(9),p.sold);assertEquals(0,parent.acknowledgedCount());assertTrue(p.inventory.containsKey(10));}
    @Test void oneReceiptCannotCompleteTwoPotionFifoOrIssueNextOpener(){var p=new Port();var parent=start(p);for(int t=1;t<150&&parent.active();t++){parent.tick(t);if(p.pending!=null){if(p.sold.size()==1)parent.onMessage("Sold 1 First for 4");p.pending=null;}}assertEquals(AlchemySaleParent.State.FAILED,parent.state());assertEquals(1,parent.acknowledgedCount());assertEquals(List.of(9,10),p.sold);assertEquals(1,p.opens);assertEquals(1,p.clicks);}
    @Test void finalCookieCloseReceiptIsRequiredEvenAfterBothTransferReceipts(){var p=new Port();var parent=start(p);for(int t=1;t<30&&parent.state()!=AlchemySaleParent.State.CLOSE_SB_MENU;t++){parent.tick(t);if(p.pending!=null){parent.onMessage("Sold 1 "+p.pending.label()+" for 4");p.pending=null;}}assertEquals(2,parent.acknowledgedCount());assertEquals(AlchemySaleParent.State.CLOSE_SB_MENU,parent.state());p.world=new Object();parent.tick(30);assertEquals(AlchemySaleParent.State.FAILED,parent.state());assertEquals(0,p.closes);}
    @Test void missingPurseDeltaDoesNotCredit(){var p=new Port();p.withholdPurse=true;var parent=start(p);run(parent,p,true);assertEquals(AlchemySaleParent.State.FAILED,parent.state());assertEquals(0,parent.acknowledgedCount());assertEquals(1,p.sold.size());}
    @Test void missingInventoryDeltaDoesNotCredit(){var p=new Port();p.withholdInventory=true;var parent=start(p);run(parent,p,true);assertEquals(AlchemySaleParent.State.FAILED,parent.state());assertEquals(0,parent.acknowledgedCount());}
    @Test void staleRevisionCannotCreditEvenWithPurseInventoryAndReceipt(){var p=new Port();var parent=start(p);for(int t=1;t<10;t++){parent.tick(t);if(p.pending!=null){p.revision=p.pending.observation().menu().revision();parent.onMessage("Sold 1 First for 4");p.pending=null;break;}}run(parent,p,true);assertEquals(0,parent.acknowledgedCount());assertEquals(AlchemySaleParent.State.FAILED,parent.state());}
    @Test void lostCookieStopsBeforeDispatch(){var p=new Port();var parent=start(p);p.cookie=false;parent.tick(1);assertEquals(AlchemySaleParent.State.FAILED,parent.state());assertEquals(0,p.opens);}
    @Test void replacementNativeMenuCannotBorrowMatchingIdTitleRevision(){var p=new Port();var parent=start(p);for(int t=1;t<=4;t++)parent.tick(t);p.menu=new Object();parent.tick(5);assertEquals(AlchemySaleParent.State.FAILED,parent.state());assertEquals(0,p.sold.size());}
    @Test void changedConnectionStopsAndPreservesReplacementMenu(){var p=new Port();var parent=start(p);parent.tick(1);int closed=p.closes;p.connection=new Object();parent.tick(2);assertEquals(AlchemySaleParent.State.FAILED,parent.state());assertEquals(closed,p.closes);}
    @Test void changedPlayerAndWorldCannotContinueOrCloseNewState(){for(boolean world:List.of(true,false)){var p=new Port();var parent=start(p);parent.tick(1);if(world)p.world=new Object();else p.player=new Object();parent.tick(2);assertEquals(AlchemySaleParent.State.FAILED,parent.state());assertEquals(0,p.closes);assertEquals(0,p.sold.size());}}
    @Test void foreignCursorStopsWithoutClosingOrSelling(){var p=new Port();var parent=start(p);parent.tick(1);p.foreignCursor=true;parent.tick(2);assertEquals(AlchemySaleParent.State.FAILED,parent.state());assertEquals(0,p.closes);assertEquals(0,p.sold.size());}
    @Test void unrelatedInventoryChangeStopsBeforeOpening(){var p=new Port();var parent=start(p);p.inventory.remove(11);parent.tick(1);assertEquals(AlchemySaleParent.State.FAILED,parent.state());assertEquals(0,p.opens);}
    @Test void manualStopWithLateReceiptCannotContinueOrCredit(){var p=new Port();var parent=start(p);for(int t=1;t<10&&p.sold.isEmpty();t++)parent.tick(t);parent.cancel();parent.onMessage("Sold 1 First for 4");parent.tick(50);assertEquals(AlchemySaleParent.State.CANCELLED,parent.state());assertEquals(0,parent.acknowledgedCount());assertEquals(List.of(9),p.sold);assertTrue(p.inventory.containsKey(10));assertEquals(3,p.selected);}
    @Test void externalSlotChangeIsPreserved(){var p=new Port();var parent=start(p);parent.tick(1);p.selected=5;parent.tick(2);assertEquals(AlchemySaleParent.State.FAILED,parent.state());assertEquals(5,p.selected);}
    @Test void saleDispatchRejectionDoesNotRetry(){var p=new Port();p.rejectDispatch=true;var parent=start(p);run(parent,p,true);assertEquals(AlchemySaleParent.State.FAILED,parent.state());assertEquals(0,p.sold.size());assertEquals(0,parent.acknowledgedCount());}
    @Test void onlyOneNewEmptyMainPotionSlotCanBecomeCollectionReceipt(){var p=new Port();p.inventory.remove(9);var before=p.sale();p.inventory.put(9,stack(-1,9,"minecraft:potion","First"));assertEquals(9,AlchemySaleParent.collected(before,p.sale()).inventoryIndex());p.inventory.put(10,stack(-1,10,"minecraft:potion","Changed"));assertThrows(IllegalStateException.class,()->AlchemySaleParent.collected(before,p.sale()));}
    @Test void preexistingPotionChangeHotbarAndNonPotionDoNotBecomeCollectionReceipt(){for(int index:List.of(8,9)){var p=new Port();var before=p.sale();p.inventory.put(index,stack(-1,index,"minecraft:potion","Replacement"));assertThrows(IllegalStateException.class,()->AlchemySaleParent.collected(before,p.sale()));}var p=new Port();var before=p.sale();p.inventory.put(12,stack(-1,12,"minecraft:splash_potion","Splash"));assertThrows(IllegalStateException.class,()->AlchemySaleParent.collected(before,p.sale()));}
    @Test void missingExplicitCookiePriceOrReceiptContractsReject(){assertThrows(IllegalArgumentException.class,()->new AlchemySaleProfile(8,ICON,"SB",22,ICON,List.of("Cookie"),"Sale","quote",BigDecimal.ZERO,"Sold {quantity} {item} for {coins}","Purse:","active","",1,40));assertThrows(IllegalArgumentException.class,()->new AlchemySaleProfile(8,ICON,"SB",22,ICON,List.of("Cookie"),"Sale","quote",BigDecimal.ONE,"Unknown","Purse:","active","",1,40));assertThrows(IllegalArgumentException.class,()->new AlchemySaleProfile(8,ICON,"SB",22,ICON,List.of("Cookie"),"Sale","quote",BigDecimal.ONE,"Sold {quantity} {item} for {coins}","Purse:","","",1,40));}
}
