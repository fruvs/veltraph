package dev.veltraph.client.fishing.bait;

import dev.veltraph.client.gui.MenuSnapshot;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class BaitRuntimeTest {
    static BaitProfile profile(){return profile(6);}
    static BaitProfile profile(int spaces){return new BaitProfile("Fixture Menu","minecraft:nether_star","Fixture Bait","minecraft:string","veltraph_bait",64,
            Map.of("root","Root","bags","Bags","bag","Bag","bazaar","Bazaar","fishing","Fishing","baits","Baits","product","Product","buy","Buy"),
            Map.of("bags","Open bags","bag","Open bag","fishing","Fishing","baits","Baits","buy","Buy now","stack","Buy stack","fill","Fill","enable","Bait usage"),
            java.util.stream.IntStream.range(0,spaces).boxed().toList(),"Item: ","Quantity: ","Cost: "," Coins","Disabled","Enabled","[Fixture] Bought {quantity}x {item} for {cost} coins!");}
    static final class Harness {
        final BaitRuntime engine=new BaitRuntime();final BaitProfile profile;final Object player=new Object(),world=new Object();
        List<BaitRuntime.Stock> inventory=new ArrayList<>();Map<Integer,BaitRuntime.Stock> bag=new HashMap<>();
        BaitRuntime.Snapshot current;long now;int purchases,moves,requests;boolean enabled,ambiguous;int revision;
        Harness(){this(4,4,10000);}
        Harness(int free,int cap,long budget){this(free,cap,budget,6);}
        Harness(int free,int cap,long budget,int spaces){this(free,cap,budget,spaces,BaitLocation.BAG);}
        Harness(int free,int cap,long budget,int spaces,BaitLocation location){
            profile=profile(spaces);
            for(int i=0;i<36;i++)inventory.add(new BaitRuntime.Stock(i,"minecraft:stone","Stone",64,"stone"));
            for(int i=9;i<9+free;i++)inventory.set(i,empty(i));for(int i=0;i<spaces;i++)bag.put(i,empty(i));
            current=snapshot(null,new Object());engine.start(current,profile,new BaitRuntime.Settings(location,cap,1000,budget),0);
        }
        BaitRuntime.Stock empty(int i){return new BaitRuntime.Stock(i,"minecraft:air","",0,"");}
        BaitRuntime.Snapshot snapshot(String title,Object token){
            List<MenuSnapshot.Slot> slots=new ArrayList<>();
            if(title!=null){
                String button=switch(title){case "Root"->"Open bags";case "Bags"->"Open bag";case "Bazaar"->"Fishing";case "Fishing"->"Baits";case "Baits"->"Fixture Bait";case "Product"->"Buy now";default->"";};
                if(!button.isEmpty())slots.add(new MenuSnapshot.Slot(10,button,List.of(),false));
                if(title.equals("Buy")){
                    slots.add(new MenuSnapshot.Slot(11,"Buy stack",List.of("Item: Fixture Bait","Quantity: 64","Cost: 64 Coins"),false));
                    var lore=new ArrayList<>(List.of("Item: Fixture Bait","Quantity: "+free()*64,"Cost: "+free()*64+" Coins"));if(ambiguous)lore.add("Cost: 1 Coins");
                    slots.add(new MenuSnapshot.Slot(12,"Fill",lore,false));
                }
                if(title.equals("Bag"))slots.add(new MenuSnapshot.Slot(20,"Bait usage",List.of(enabled?"Enabled":"Disabled"),false));
                for(var stock:inventory)slots.add(new MenuSnapshot.Slot(100+stock.index(),stock.name(),List.of(),true,stock.itemId(),stock.count()));
            }
            return new BaitRuntime.Snapshot(player,world,token,title==null?null:new MenuSnapshot(1,title,slots,revision),inventory,title!=null&&title.equals("Bag")?bag:Map.of(),true,false,false);
        }
        int free(){return (int)inventory.stream().filter(s->s.count()==0).count();}
        Optional<BaitRuntime.Action> step(boolean apply){now+=350;var action=engine.tick(current,now);if(action.isPresent()){requests++;if(apply)apply(action.get());}return action;}
        void open(String title){revision=0;current=snapshot(title,new Object());}
        void apply(BaitRuntime.Action action){switch(action.kind()){
            case REEL -> throw new AssertionError("No synthetic hook expected");
            case USE_MENU -> open("Root");case COMMAND->open("Bazaar");case CLOSE->current=snapshot(null,new Object());case RESTORE->{}
            case CLICK -> {
                String label=action.expected().menu().slots().stream().filter(s->s.id()==action.slot()).findFirst().orElseThrow().name();
                String title=switch(label){case "Open bags"->"Bags";case "Open bag"->"Bag";case "Fishing"->"Fishing";case "Baits"->"Baits";case "Fixture Bait"->"Product";case "Buy now"->"Buy";default->null;};
                if(title!=null)open(title);else{enabled=true;revision++;current=snapshot("Bag",current.menuToken());}
            }
            case PURCHASE -> {purchases++;give(action.quantity());engine.message("[Fixture] Bought "+action.quantity()+"x Fixture Bait for "+action.quantity()+" coins!");}
            case MOVE -> {
                moves++;int index=action.slot()-100;inventory.set(index,empty(index));int target=bag.values().stream().filter(s->s.count()==0).mapToInt(BaitRuntime.Stock::index).min().orElseThrow();
                bag.put(target,new BaitRuntime.Stock(target,"minecraft:string","Fixture Bait",action.quantity(),"bait"));revision++;current=snapshot("Bag",current.menuToken());
            }
        }}
        void give(int units){for(int i=0;i<inventory.size()&&units>0;i++)if(inventory.get(i).count()==0){int count=Math.min(64,units);inventory.set(i,new BaitRuntime.Stock(i,"minecraft:string","Fixture Bait",count,"bait"));units-=count;}assertEquals(0,units);revision++;current=snapshot("Buy",current.menuToken());}
        void until(BaitRuntime.State wanted){for(int i=0;i<200&&engine.state()!=wanted&&!engine.terminal();i++)step(true);assertEquals(wanted,engine.state(),engine.reason());}
        int bagUnits(){return bag.values().stream().filter(s->s.name().equals("Fixture Bait")).mapToInt(BaitRuntime.Stock::count).sum();}
    }
    @Test void fillEnableTransferAndFinalCloseAreAcknowledged(){var h=new Harness();h.until(BaitRuntime.State.COMPLETE);assertEquals(1,h.purchases);assertEquals(4,h.moves);assertEquals(256,h.bagUnits());assertEquals(256,h.engine.purchased());assertEquals(0,h.inventory.stream().filter(s->s.name().equals("Fixture Bait")).mapToInt(BaitRuntime.Stock::count).sum());}
    @Test void heldBaitsMoveBeforeBoundedNewPurchases(){var h=new Harness(4,8,10000,12);h.inventory.set(13,new BaitRuntime.Stock(13,"minecraft:string","Fixture Bait",64,"bait"));h.current=h.snapshot(null,h.current.menuToken());h.until(BaitRuntime.State.COMPLETE);assertEquals(1,h.purchases);assertEquals(320,h.engine.purchased());assertEquals(384,h.engine.transferred());assertEquals(384,h.bagUnits());}
    @Test void inventoryDestinationUsesSourceTwoStackQuotaAndSkipsBag(){var h=new Harness(8,8,10000,6,BaitLocation.INVENTORY);h.until(BaitRuntime.State.COMPLETE);assertEquals(2,h.purchases);assertEquals(0,h.moves);assertFalse(h.enabled);assertEquals(0,h.bagUnits());assertEquals(128,h.inventory.stream().filter(s->s.name().equals("Fixture Bait")).mapToInt(BaitRuntime.Stock::count).sum());}
    @Test void sourceBagRecheckPerformsSecondBoundedFillCycle(){var h=new Harness(4,8,10000,12);h.until(BaitRuntime.State.COMPLETE);assertEquals(2,h.purchases);assertEquals(8,h.moves);assertEquals(512,h.bagUnits());}
    @Test void stackBranchUsesTwoSeparatelyAcknowledgedRequests(){var h=new Harness(8,2,10000);h.until(BaitRuntime.State.COMPLETE);assertEquals(2,h.purchases);assertEquals(128,h.bagUnits());}
    @Test void missingInventoryAckDoesNotRetryAfterReceipt(){var h=new Harness();h.until(BaitRuntime.State.BUY);var request=h.step(false).orElseThrow();assertEquals(BaitRuntime.Kind.PURCHASE,request.kind());h.engine.message("[Fixture] Bought 256x Fixture Bait for 256 coins!");for(int i=0;i<8;i++)assertTrue(h.step(false).isEmpty());assertEquals(BaitRuntime.State.WAIT_PURCHASE,h.engine.state());h.now+=4500;h.step(false);assertEquals(BaitRuntime.State.FAILED,h.engine.state());}
    @Test void inventoryMutationAloneDoesNotCompletePurchase(){var h=new Harness();h.until(BaitRuntime.State.BUY);h.step(false);h.give(256);h.step(false);assertEquals(BaitRuntime.State.WAIT_PURCHASE,h.engine.state());assertEquals(0,h.engine.purchased());h.engine.message("[Fixture] Bought 256x Fixture Bait for 256 coins!");h.until(BaitRuntime.State.COMPLETE);assertEquals(256,h.bagUnits());}
    @Test void ambiguousPriceAndSessionCostBlockRequest(){for(int mode=0;mode<2;mode++){var h=new Harness(4,4,mode==0?10000:255);h.until(BaitRuntime.State.BUY);if(mode==0){h.ambiguous=true;h.current=h.snapshot("Buy",h.current.menuToken());}assertTrue(h.step(false).isEmpty());assertEquals(BaitRuntime.State.FAILED,h.engine.state());assertEquals(0,h.purchases);}}
    @Test void sameTitleReplacementAndWorldChangeCancelWithoutClicks(){for(int mode=0;mode<2;mode++){var h=new Harness();h.until(BaitRuntime.State.BUY);if(mode==0)h.open("Buy");else{var s=h.current;h.current=new BaitRuntime.Snapshot(s.player(),new Object(),s.menuToken(),s.menu(),s.inventory(),s.storage(),true,false,false);}assertTrue(h.step(false).isEmpty());assertTrue(h.engine.terminal());assertEquals(0,h.purchases);}}
    @Test void menuReplacementCancelsEvenBeforeActionPacingExpires(){var h=new Harness();h.until(BaitRuntime.State.BUY);h.open("Buy");h.now-=300;assertTrue(h.engine.tick(h.current,h.now).isEmpty());assertEquals(BaitRuntime.State.CANCELLED,h.engine.state());}
    @Test void manualCursorAndWrongReceiptStopOwnedWork(){var h=new Harness();h.until(BaitRuntime.State.BUY);h.step(false);var s=h.current;h.current=new BaitRuntime.Snapshot(s.player(),s.world(),s.menuToken(),s.menu(),s.inventory(),s.storage(),false,false,false);assertTrue(h.step(false).isEmpty());assertEquals(BaitRuntime.State.CANCELLED,h.engine.state());var other=new Harness();other.until(BaitRuntime.State.BUY);other.step(false);other.engine.message("[Fixture] Bought 256x Fixture Bait for 999 coins!");assertEquals(BaitRuntime.State.FAILED,other.engine.state());}
    @Test void quoteRequiresUniqueTypedIdentityAndFiniteMoney(){var p=profile();assertThrows(IllegalArgumentException.class,()->p.quote(new MenuSnapshot.Slot(1,"Buy",List.of("Item: Other Bait","Quantity: 64","Cost: 64 Coins"),false)));assertThrows(IllegalArgumentException.class,()->BaitProfile.money("NaN"));assertThrows(IllegalArgumentException.class,()->BaitProfile.money("-1"));assertThrows(IllegalArgumentException.class,()->BaitProfile.parse("{}"));}
}
