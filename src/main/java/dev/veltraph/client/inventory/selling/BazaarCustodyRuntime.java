package dev.veltraph.client.inventory.selling;

import dev.veltraph.client.gui.MenuSnapshot;
import dev.veltraph.client.inventory.BazaarSaleRuntime;
import java.math.BigDecimal;
import java.util.*;
import java.util.regex.Pattern;

/** One source inventory or sacks stage. Every success has exact native custody and purse proof. */
public final class BazaarCustodyRuntime {
    public enum State { IDLE, READY, WAIT_CONFIRM, READY_CONFIRM, WAIT_ACK, CLOSING, COMPLETE, FAILED, CANCELLED }
    public enum Stage { INVENTORY, SACKS }
    public record Action(SellingObservation observation,int slot,boolean close) {}
    public record Proof(Stage stage,Map<String,Integer> quantities,BigDecimal proceeds,BigDecimal purseBefore,BigDecimal purseAfter,int menuId,int requestRevision,int receiptRevision,long matchedReceiptSerial) {
        public Proof(Stage stage,Map<String,Integer> quantities,BigDecimal proceeds,BigDecimal purseBefore,BigDecimal purseAfter){this(stage,quantities,proceeds,purseBefore,purseAfter,-1,-1,-1,0);}
        public Proof {quantities=Map.copyOf(quantities);}
    }
    private State state=State.IDLE;private Stage stage;private BazaarCustodyPlan plan;private String reason="Idle";
    private Map<Integer,String> before=Map.of(),after=Map.of();private Map<String,Integer> quantities=Map.of();
    private BigDecimal proceeds,purse;private MenuSnapshot owned;private long next,deadline;private int requestRevision;
    private long matchedReceiptSerial;private boolean receipt;private Pattern receiptPattern;private Action pending;private Proof proof;
    public State state(){return state;}public String status(){return state+": "+reason;}
    public boolean terminal(){return state==State.COMPLETE||state==State.FAILED||state==State.CANCELLED;}
    public Optional<Proof> acknowledged(){return state==State.COMPLETE?Optional.ofNullable(proof):Optional.empty();}
    public boolean allowsScreen(String title){return plan!=null&&(MenuSnapshot.clean(title).equals(plan.menuTitle())||MenuSnapshot.clean(title).equals(plan.confirmTitle()));}
    public int ownedMenu(){return owned==null?-1:owned.id();}
    public void start(SellingObservation observed,BazaarCustodyPlan plan,Stage stage,long tick){
        this.plan=plan;this.stage=stage;state=State.IDLE;pending=null;proof=null;receipt=false;owned=null;
        if(observed.menu()==null||!MenuSnapshot.clean(observed.menu().title()).equals(plan.menuTitle())||!observed.cursorEmpty()||observed.coins()==null){fail("Exact native Bazaar menu/cursor/purse required");return;}
        before=observed.ledger();var expected=new HashMap<>(before);var counts=new LinkedHashMap<String,Integer>();proceeds=BigDecimal.ZERO;
        try {
            if(stage==Stage.INVENTORY){
                if(plan.inventory().isEmpty()){fail("No configured inventory sale");return;}
                for(var item:plan.inventory()){
                    var actual=observed.inventoryStack(item.index()).orElse(null);
                    if(actual==null||actual.slot()<0||actual.protectedItem()||actual.wearable()||!actual.identity().equals(item.identity())||actual.count()!=item.quantity()){fail("Exact configured main inventory stack unavailable/protected");return;}
                    expected.remove(item.index());counts.merge(item.name(),item.quantity(),Math::addExact);proceeds=proceeds.add(item.unitValue().multiply(BigDecimal.valueOf(item.quantity())));
                }
                // A displayed name cannot silently authorize distinct component identities.
                for(String name:counts.keySet())if(plan.inventory().stream().filter(i->i.name().equals(name)).map(BazaarCustodyPlan.InventoryItem::identity).distinct().count()!=1){fail("Ambiguous display-name component identity");return;}
            }else{
                if(plan.sacks().isEmpty()||!sacksMatch(observed,false)){fail("Explicit native sack counters missing/changed");return;}
                for(var sack:plan.sacks()){counts.put(sack.name(),sack.quantity());proceeds=proceeds.add(sack.unitValue().multiply(BigDecimal.valueOf(sack.quantity())));}
            }
        }catch(ArithmeticException overflow){fail("Quantity overflow");return;}
        if(proceeds.signum()<=0){fail("Explicit positive-value Bazaar stage required");return;}
        quantities=Map.copyOf(counts);after=Map.copyOf(expected);purse=observed.coins();owned=observed.menu();
        if(!quote(observed,button(false))){fail("Exact current priced quote required");return;}
        receiptPattern=receiptPattern(plan.receiptTemplate());state=State.READY;next=tick+plan.delayTicks();deadline=tick+plan.timeoutTicks();reason="Exact "+stage+" custody selected";
    }
    private String button(boolean confirmation){return stage==Stage.INVENTORY?(confirmation?plan.inventoryConfirm():plan.inventoryButton()):(confirmation?plan.sacksConfirm():plan.sacksButton());}
    private boolean own(MenuSnapshot menu){return owned!=null&&menu!=null&&menu.id()==owned.id()&&MenuSnapshot.clean(menu.title()).equals(MenuSnapshot.clean(owned.title()));}
    private boolean sacksMatch(SellingObservation observed,boolean consumed){
        for(var sack:plan.sacks()){
            var marker=observed.slots().get(sack.counterSlot());
            if(marker==null||marker.inventoryIndex()>=0||marker.protectedItem()||marker.count()!=1||!marker.registryId().equals(sack.markerRegistryId())
                    ||!marker.name().equals(sack.markerName())||!marker.components().equals(sack.components(consumed?0:sack.quantity())))return false;
        }return true;
    }
    private MenuSnapshot.Slot find(SellingObservation observed,String label){
        if(observed.menu()==null)return null;
        var matches=observed.menu().slots().stream().filter(s->!s.playerInventory()&&!s.locked()&&MenuSnapshot.clean(s.name()).equals(label)).toList();
        return matches.size()==1?matches.getFirst():null;
    }
    private boolean quote(SellingObservation observed,String label){
        var slot=find(observed,label);var parsed=slot==null?Optional.<BazaarSaleRuntime.Quote>empty():BazaarSaleRuntime.parseQuote(slot);
        return parsed.isPresent()&&parsed.get().quantities().equals(quantities)&&parsed.get().proceeds().compareTo(proceeds)==0;
    }
    private Optional<Action> issue(SellingObservation observed,int slot,boolean close){pending=new Action(observed,slot,close);return Optional.of(pending);}
    public Optional<Action> tick(SellingObservation observed,long tick){
        if(state==State.IDLE||terminal()||pending!=null)return Optional.empty();
        if(tick>deadline){fail("Bounded server acknowledgment timeout; no retry");return Optional.empty();}
        if(state==State.CLOSING){
            if(!observed.ledger().equals(after)||observed.coins()==null||observed.coins().compareTo(purse.add(proceeds))!=0){cancel("Acknowledged protected inventory/purse changed during cleanup");return Optional.empty();}
            if(observed.menu()==null&&observed.cursorEmpty()){state=State.COMPLETE;reason="Exact custody/purse/receipt and owned cleanup acknowledged";return Optional.empty();}
            if(!own(observed.menu())||!observed.cursorEmpty()){cancel("Cleanup menu/cursor changed");return Optional.empty();}
            return tick<next?Optional.empty():issue(observed,-1,true);
        }
        boolean prediction=observed.ownCursorPrediction()&&(state==State.WAIT_CONFIRM||state==State.WAIT_ACK);
        if(!observed.cursorEmpty()&&!prediction){cancel("Foreign native cursor");return Optional.empty();}
        if(state==State.WAIT_CONFIRM){
            if(observed.menu()!=null&&MenuSnapshot.clean(observed.menu().title()).equals(plan.confirmTitle())
                    &&(observed.menu().id()!=owned.id()||observed.menu().revision()!=requestRevision)&&observed.cursorEmpty()){
                owned=observed.menu();state=State.READY_CONFIRM;next=tick+plan.delayTicks();deadline=tick+plan.timeoutTicks();
            }else if(!own(observed.menu())){cancel("Unexpected confirmation replacement");return Optional.empty();}
            else return Optional.empty();
        }
        if(!own(observed.menu())){cancel("Owned selling menu replaced/closed");return Optional.empty();}
        if(state==State.WAIT_ACK){
            boolean counters=stage!=Stage.SACKS||sacksMatch(observed,true);
            if(receipt&&observed.menu().revision()!=requestRevision&&observed.cursorEmpty()&&observed.ledger().equals(after)&&counters
                    &&observed.coins()!=null&&observed.coins().compareTo(purse.add(proceeds))==0){
                proof=new Proof(stage,quantities,proceeds,purse,observed.coins(),observed.menu().id(),requestRevision,observed.menu().revision(),matchedReceiptSerial);state=State.CLOSING;next=tick+plan.delayTicks();deadline=tick+plan.timeoutTicks();
            }return Optional.empty();
        }
        if(!observed.ledger().equals(before)||observed.coins()==null||observed.coins().compareTo(purse)!=0||stage==Stage.SACKS&&!sacksMatch(observed,false)){
            cancel("Protected inventory/custody/purse changed before commit");return Optional.empty();
        }
        if(tick<next)return Optional.empty();String label=button(state==State.READY_CONFIRM);
        if(!quote(observed,label)){fail("Quote missing/changed on final reread");return Optional.empty();}
        return issue(observed,find(observed,label).id(),false);
    }
    public boolean valid(Action action,SellingObservation current){
        if(action!=pending||!own(current.menu())||current.menu().revision()!=action.observation().menu().revision()
                ||!current.ledger().equals(action.observation().ledger())||!Objects.equals(current.cursor(),action.observation().cursor())
                ||!Objects.equals(current.coins(),action.observation().coins())||!current.slots().equals(action.observation().slots()))return false;
        return action.close()?current.cursorEmpty():quote(current,button(state==State.READY_CONFIRM));
    }
    public void applied(Action action,boolean accepted,long tick){
        if(action!=pending)return;pending=null;if(!accepted){fail("Guarded native request refused");return;}
        requestRevision=action.observation().menu().revision();deadline=tick+plan.timeoutTicks();
        if(action.close()){next=tick+plan.delayTicks();return;}
        state=state==State.READY?State.WAIT_CONFIRM:State.WAIT_ACK;
    }
    public void onMessage(String raw){
        if(state!=State.WAIT_ACK)return;var match=receiptPattern.matcher(MenuSnapshot.clean(raw));
        try{if(match.matches()&&Integer.parseInt(match.group("quantity").replace(",",""))==quantities.values().stream().mapToInt(Integer::intValue).sum()
                &&new BigDecimal(match.group("coins").replace(",","")).compareTo(proceeds)==0){if(!receipt)matchedReceiptSerial++;receipt=true;}}catch(RuntimeException malformed){}
    }
    private static Pattern receiptPattern(String template){
        var result=new StringBuilder("^");int at=0;
        while(at<template.length()){
            int found=template.length();String token=null;
            for(String value:List.of("{quantity}","{coins}")){int index=template.indexOf(value,at);if(index>=0&&index<found){found=index;token=value;}}
            result.append(Pattern.quote(template.substring(at,found)));if(token==null)break;
            result.append(token.equals("{quantity}")?"(?<quantity>[1-9][0-9]*(?:,[0-9]{3})*)":"(?<coins>[0-9]+(?:,[0-9]{3})*(?:\\.[0-9]{1,2})?)");at=found+token.length();
        }return Pattern.compile(result.append("$").toString());
    }
    public void cancel(String why){if(!terminal()){state=State.CANCELLED;pending=null;reason=why;}}
    private void fail(String why){state=State.FAILED;pending=null;reason=why;}
}
