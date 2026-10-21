package dev.veltraph.client.inventory.selling;

import dev.veltraph.client.config.AutoSellMode;
import dev.veltraph.client.gui.MenuSnapshot;
import dev.veltraph.client.inventory.BazaarSaleRuntime;
import java.math.BigDecimal;
import java.util.*;
import java.util.regex.Pattern;

/** Source-separated NPC inventory clicks, George escrow transfer, and one-stack trash discard. */
public final class SellingRuntime {
    public enum State { IDLE, READY_SELL, WAIT_SALE, READY_PICKUP, WAIT_CURSOR, READY_DEPOSIT, WAIT_DEPOSIT,
        READY_ACCEPT, WAIT_CONFIRM, READY_CONFIRM, READY_DROP, WAIT_DROP, WAIT_CANCEL_DEPOSIT, RETURN_DEPOSIT, WAIT_RETURN_CURSOR,
        RETURN_ORIGIN, WAIT_RETURN_ORIGIN, CLOSING, COMPLETE, FAILED, CANCELLED }
    public enum Kind { SELL, PICKUP, DEPOSIT, ACCEPT, CONFIRM, DROP, RETURN_DEPOSIT, RETURN_ORIGIN, CLOSE }
    public record Settings(AutoSellMode channel,String menuTitle,String acceptLabel,String confirmTitle,String confirmLabel,
                           int depositSlot,int confirmPetSlot,String receiptTemplate,int maxQuantity,BigDecimal minProceeds,BigDecimal maxProceeds,
                           BigDecimal trashMaxValue,int delayTicks,int timeoutTicks) {
        public Settings {
            menuTitle=MenuSnapshot.clean(menuTitle);acceptLabel=MenuSnapshot.clean(acceptLabel);confirmTitle=MenuSnapshot.clean(confirmTitle);confirmLabel=MenuSnapshot.clean(confirmLabel);
            if(channel==null||channel==AutoSellMode.BAZAAR||maxQuantity<1||maxQuantity>2304||minProceeds.signum()<0
                    ||maxProceeds.compareTo(minProceeds)<0||trashMaxValue.signum()<0||delayTicks<1||timeoutTicks<delayTicks
                    ||channel!=AutoSellMode.TRASH&&(menuTitle.isBlank()||receiptTemplate==null
                    ||occurrences(receiptTemplate,"{quantity}")!=1||occurrences(receiptTemplate,"{item}")!=1||occurrences(receiptTemplate,"{coins}")!=1)
                    ||channel==AutoSellMode.GEORGE_PETS&&(acceptLabel.isBlank()||confirmTitle.isBlank()||confirmLabel.isBlank()||depositSlot<0||depositSlot>53||confirmPetSlot<0||confirmPetSlot>53))
                throw new IllegalArgumentException("Required selling labels, receipt and bounds are missing or invalid");
        }
    }
    private static int occurrences(String value,String token){return (value.length()-value.replace(token,"").length())/token.length();}
    public record Action(Kind kind,SellingObservation observation,int slot,String label) {}
    private State state=State.IDLE;
    private Settings settings;private SellingRule rule;
    private SellingObservation.Stack selected;
    private Map<Integer,String> before=Map.of(),after=Map.of();private BigDecimal beforeCoins;
    private int ownedId=-1,requestRevision;private String ownedTitle="";
    private long next,deadline;private Set<Integer> oldDrops=Set.of();
    private Action pending;private long matchedReceiptSerial;private boolean receipt;private String reason="Idle";private boolean escrowHeld;private Pattern receiptPattern;
    public record SaleProof(AutoSellMode channel,String itemIdentity,int inventoryIndex,int quantity,BigDecimal proceeds,BigDecimal purseBefore,BigDecimal purseAfter,int menuId,int requestRevision,int receiptRevision,long matchedReceiptSerial) {
        public SaleProof(AutoSellMode channel,String itemIdentity,int inventoryIndex,int quantity,BigDecimal proceeds,BigDecimal purseBefore,BigDecimal purseAfter){this(channel,itemIdentity,inventoryIndex,quantity,proceeds,purseBefore,purseAfter,-1,-1,-1,0);}
    }
    private SaleProof proof;private boolean retainNpcMenu;
    /** Available only after exact server item/receipt/purse/escrow acknowledgment and owned cleanup. */
    public Optional<SaleProof> acknowledgedSale(){return state==State.COMPLETE?Optional.ofNullable(proof):Optional.empty();}
    public State state(){return state;}
    public String reason(){return reason;}
    public SellingRule rule(){return rule;}
    public int ownedMenu(){return ownedId;}
    public boolean terminal(){return state==State.COMPLETE||state==State.FAILED||state==State.CANCELLED;}
    public boolean escrowHeld(){return escrowHeld;}
    public boolean cleaning(){return EnumSet.of(State.WAIT_CANCEL_DEPOSIT,State.RETURN_DEPOSIT,State.WAIT_RETURN_CURSOR,State.RETURN_ORIGIN,State.WAIT_RETURN_ORIGIN).contains(state);}
    public boolean allowsScreen(String title){return settings!=null&&(MenuSnapshot.clean(title).equals(ownedTitle)
            ||settings.channel==AutoSellMode.GEORGE_PETS&&MenuSnapshot.clean(title).equals(settings.confirmTitle));}
    public void reset(){proof=null;retainNpcMenu=false;state=State.IDLE;settings=null;rule=null;selected=null;ownedId=-1;pending=null;receipt=false;escrowHeld=false;reason="Idle";}
    public void start(SellingObservation observed,SellingRule exactRule,Settings options,long tick){
        start(observed,exactRule,options,tick,false);
    }
    /** Explicit NPC batch continuation only; ordinary callers still require acknowledged menu closure. */
    public void start(SellingObservation observed,SellingRule exactRule,Settings options,long tick,boolean retainNpcMenu){
        if(retainNpcMenu&&options.channel()!=AutoSellMode.NPC)throw new IllegalArgumentException("Only NPC batch may retain native seller menu");
        reset();this.retainNpcMenu=retainNpcMenu;settings=options;rule=exactRule;
        if(options.channel!=AutoSellMode.TRASH)receiptPattern=receiptPattern(options.receiptTemplate,rule.name());
        if(observed.menu()==null||options.channel!=exactRule.channel()){fail("Open the correct selling screen");return;}
        if(options.channel!=AutoSellMode.TRASH&&!MenuSnapshot.clean(observed.menu().title()).equals(options.menuTitle)){fail("Wrong selling menu");return;}
        if(!observed.cursorEmpty()){fail("Cursor must be empty before starting");return;}
        selected=observed.inventoryStack(rule.inventoryIndex()).orElse(null);
        if(!rule.matches(selected)||selected.slot()<0){fail("Exact configured stack unavailable or protected");return;}
        if(rule.quantity()>options.maxQuantity){fail("Quantity exceeds configured bound");return;}
        if(options.channel==AutoSellMode.TRASH){if(rule.total().compareTo(options.trashMaxValue)>0){fail("Configured trash value exceeds discard cap");return;}}
        else if(observed.coins()==null||rule.total().compareTo(options.minProceeds)<0||rule.total().compareTo(options.maxProceeds)>0){fail("Explicit purse/value observation or proceeds bounds failed");return;}
        before=observed.ledger();var expected=new HashMap<>(before);expected.remove(rule.inventoryIndex());after=Map.copyOf(expected);beforeCoins=observed.coins();
        own(observed.menu());next=tick+options.delayTicks;deadline=tick+options.timeoutTicks;
        oldDrops=new HashSet<>();for(var drop:observed.droppedItems())oldDrops.add(drop.entityId());
        state=switch(options.channel){case NPC->State.READY_SELL;case GEORGE_PETS->State.READY_PICKUP;case TRASH->State.READY_DROP;default->throw new IllegalArgumentException();};
        if(options.channel==AutoSellMode.NPC&&!quoted(observed,rule.quoteLabel()))fail("NPC stack needs an exact current quote");
        if(options.channel==AutoSellMode.GEORGE_PETS){var deposit=observed.slots().get(options.depositSlot);if(deposit==null||deposit.inventoryIndex()>=0||!deposit.empty())fail("George deposit slot is not empty container space");}
        reason=terminal()?reason:"Prepared exact "+rule.quantity()+"x "+rule.name()+" ("+options.channel+")";
    }
    private void own(MenuSnapshot menu){ownedId=menu.id();ownedTitle=MenuSnapshot.clean(menu.title());}
    private boolean owned(MenuSnapshot menu){return menu!=null&&menu.id()==ownedId&&MenuSnapshot.clean(menu.title()).equals(ownedTitle);}
    private boolean serverChanged(SellingObservation observed){return observed.menu()!=null&&observed.menu().revision()!=requestRevision;}
    private boolean quoted(SellingObservation observed,String label){
        var matches=observed.menu().slots().stream().filter(s->!s.playerInventory()&&!s.locked()&&MenuSnapshot.clean(s.name()).equals(label)).toList();
        if(matches.size()!=1)return false;
        var quote=BazaarSaleRuntime.parseQuote(matches.getFirst());return quote.isPresent()&&quote.get().quantities().equals(Map.of(rule.name(),rule.quantity()))
                &&quote.get().proceeds().compareTo(rule.total())==0;
    }
    private Optional<Action> issue(Kind kind,SellingObservation observed,int slot,String label){pending=new Action(kind,observed,slot,label);return Optional.of(pending);}
    private Optional<Action> button(Kind kind,SellingObservation observed,String label){
        if(!quoted(observed,label)){fail("Exact sale quote changed or missing");return Optional.empty();}
        var slot=observed.menu().slots().stream().filter(s->!s.playerInventory()&&MenuSnapshot.clean(s.name()).equals(label)).findFirst().orElseThrow();
        return issue(kind,observed,slot.id(),slot.name());
    }
    public Optional<Action> tick(SellingObservation observed,long tick){
        if(state==State.IDLE||terminal()||pending!=null)return Optional.empty();
        if(tick>deadline){fail("Server acknowledgment timed out; no retry");return Optional.empty();}
        if(state==State.CLOSING){if(observed.menu()==null){state=State.COMPLETE;reason="Sale/discard acknowledged and menu closed";return Optional.empty();}
            if(!owned(observed.menu())||!observed.cursorEmpty()){cancel(observed,"Cleanup ownership/cursor changed",tick);return Optional.empty();}
            return tick<next?Optional.empty():issue(Kind.CLOSE,observed,-1,"");}
        if(state==State.WAIT_CONFIRM){
            if(observed.menu()!=null&&MenuSnapshot.clean(observed.menu().title()).equals(settings.confirmTitle)
                    &&(observed.menu().id()!=ownedId||serverChanged(observed))&&observed.cursorEmpty()&&observed.ledger().equals(after)){
                own(observed.menu());state=State.READY_CONFIRM;next=tick+settings.delayTicks;deadline=tick+settings.timeoutTicks;
            }else if(!owned(observed.menu())){cancel(observed,"Unexpected confirmation replacement",tick);return Optional.empty();}
            else return Optional.empty();
        }
        if(!owned(observed.menu())){cancel(observed,"Selling container replaced or closed",tick);return Optional.empty();}
        if(cleaning())return cleanup(observed,tick);
        boolean predicted=observed.ownCursorPrediction()&&(state==State.WAIT_SALE||state==State.WAIT_CONFIRM);
        if(!observed.cursorEmpty()&&!predicted&&!EnumSet.of(State.WAIT_CURSOR,State.READY_DEPOSIT,State.WAIT_DEPOSIT).contains(state)){
            cancel(observed,"Unexpected carried stack",tick);return Optional.empty();}
        if(state==State.WAIT_SALE){
            boolean escrowGone=settings.channel!=AutoSellMode.GEORGE_PETS||observed.slots().get(settings.confirmPetSlot)!=null
                    &&observed.slots().get(settings.confirmPetSlot).inventoryIndex()<0&&observed.slots().get(settings.confirmPetSlot).empty();
            if(receipt&&serverChanged(observed)&&observed.cursorEmpty()&&observed.ledger().equals(after)&&observed.coins()!=null
                    &&escrowGone&&observed.coins().compareTo(beforeCoins.add(rule.total()))==0){proof=new SaleProof(settings.channel,rule.identity(),rule.inventoryIndex(),rule.quantity(),rule.total(),beforeCoins,observed.coins(),observed.menu().id(),requestRevision,observed.menu().revision(),matchedReceiptSerial);escrowHeld=false;state=retainNpcMenu?State.COMPLETE:State.CLOSING;next=tick+settings.delayTicks;deadline=tick+settings.timeoutTicks;reason="Receipt, item, escrow and purse deltas acknowledged";}
            return Optional.empty();
        }
        if(state==State.WAIT_DROP){
            var drops=observed.droppedItems().stream().filter(d->!oldDrops.contains(d.entityId())&&d.identity().equals(rule.identity())&&d.count()==rule.quantity()
                    &&d.distanceFromDropOrigin()<2.5).toList();
            if(serverChanged(observed)&&observed.cursorEmpty()&&observed.ledger().equals(after)&&drops.size()==1){state=State.CLOSING;next=tick+settings.delayTicks;deadline=tick+settings.timeoutTicks;reason="Server item spawn and exact inventory decrement acknowledged";}
            return Optional.empty();
        }
        if(state==State.WAIT_CURSOR){
            if(serverChanged(observed)&&observed.cursor()!=null&&observed.cursor().sameItem(rule)&&observed.ledger().equals(after)){
                escrowHeld=true;state=State.READY_DEPOSIT;next=tick+settings.delayTicks;deadline=tick+settings.timeoutTicks;reason="Server pickup acknowledged";
            }else if(!observed.cursorEmpty()&&(observed.cursor()==null||!observed.cursor().sameItem(rule))){cancel(observed,"Picked cursor item mismatch",tick);}
            return Optional.empty();
        }
        if(state==State.WAIT_DEPOSIT){
            var pet=observed.slots().get(settings.depositSlot);
            if(serverChanged(observed)&&observed.cursorEmpty()&&pet!=null&&pet.sameItem(rule)&&observed.ledger().equals(after)){
                state=State.READY_ACCEPT;next=tick+settings.delayTicks;deadline=tick+settings.timeoutTicks;reason="Server pet deposit acknowledged";
            }return Optional.empty();
        }
        if(tick<next)return Optional.empty();
        if(settings.channel!=AutoSellMode.TRASH&&(observed.coins()==null||observed.coins().compareTo(beforeCoins)!=0)){
            cancel(observed,"Purse changed before financial commit",tick);return Optional.empty();}
        if(state==State.READY_SELL||state==State.READY_PICKUP||state==State.READY_DROP){
            if(!observed.cursorEmpty()||!observed.ledger().equals(before)||!rule.matches(observed.inventoryStack(rule.inventoryIndex()).orElse(null))){cancel(observed,"Protected/original inventory changed",tick);return Optional.empty();}
            if(state==State.READY_SELL){if(!quoted(observed,rule.quoteLabel())){fail("NPC quote changed before sale");return Optional.empty();}return issue(Kind.SELL,observed,selected.slot(),rule.name());}
            if(state==State.READY_DROP){oldDrops=new HashSet<>();for(var drop:observed.droppedItems())oldDrops.add(drop.entityId());}
            return issue(state==State.READY_PICKUP?Kind.PICKUP:Kind.DROP,observed,selected.slot(),rule.name());
        }
        if(!observed.ledger().equals(after)){cancel(observed,"Unrelated inventory changed during pet transfer",tick);return Optional.empty();}
        if(state==State.READY_DEPOSIT){var slot=observed.slots().get(settings.depositSlot);
            if(observed.cursor()==null||!observed.cursor().sameItem(rule)||slot==null||slot.inventoryIndex()>=0||!slot.empty()){cancel(observed,"Pet/deposit slot changed",tick);return Optional.empty();}
            return issue(Kind.DEPOSIT,observed,settings.depositSlot,"");}
        if(state==State.READY_ACCEPT){var pet=observed.slots().get(settings.depositSlot);
            if(!observed.cursorEmpty()||pet==null||!pet.sameItem(rule)){cancel(observed,"Escrow pet changed",tick);return Optional.empty();}
            return button(Kind.ACCEPT,observed,settings.acceptLabel);}
        if(state==State.READY_CONFIRM){var pet=observed.slots().get(settings.confirmPetSlot);
            if(pet==null||pet.inventoryIndex()>=0||!pet.sameItem(rule)){cancel(observed,"Confirmation pet identity changed or absent",tick);return Optional.empty();}
            return button(Kind.CONFIRM,observed,settings.confirmLabel);}
        return Optional.empty();
    }
    public boolean valid(Action action,SellingObservation current){
        if(action!=pending||current.menu()==null||!owned(current.menu())||current.menu().revision()!=action.observation().menu().revision())return false;
        return current.ledger().equals(action.observation().ledger())&&Objects.equals(current.cursor(),action.observation().cursor())
                &&Objects.equals(current.slots().get(action.slot()),action.observation().slots().get(action.slot()))
                &&Objects.equals(current.coins(),action.observation().coins())
                &&(action.kind()!=Kind.ACCEPT&&action.kind()!=Kind.CONFIRM||quoted(current,action.label()))
                &&(action.kind()!=Kind.ACCEPT||ownedPet(current,settings.depositSlot))
                &&(action.kind()!=Kind.CONFIRM||ownedPet(current,settings.confirmPetSlot))
                &&(action.kind()!=Kind.SELL||quoted(current,rule.quoteLabel()));
    }
    private boolean ownedPet(SellingObservation observed,int slot){var value=observed.slots().get(slot);return value!=null&&value.inventoryIndex()<0&&value.sameItem(rule);}
    public void applied(Action action,boolean accepted,long tick){
        if(action!=pending)return;pending=null;if(!accepted){fail("Guarded selling action rejected");return;}
        requestRevision=action.observation().menu().revision();deadline=tick+settings.timeoutTicks;
        state=switch(action.kind()){case SELL,CONFIRM->State.WAIT_SALE;case PICKUP->State.WAIT_CURSOR;case DEPOSIT->State.WAIT_DEPOSIT;
            case ACCEPT->State.WAIT_CONFIRM;case DROP->State.WAIT_DROP;case RETURN_DEPOSIT->State.WAIT_RETURN_CURSOR;
            case RETURN_ORIGIN->State.WAIT_RETURN_ORIGIN;case CLOSE->State.CLOSING;};
        if(action.kind()==Kind.CLOSE)next=tick+settings.delayTicks;
    }
    public void onMessage(String raw){
        if(state!=State.WAIT_SALE)return;String line=MenuSnapshot.clean(raw);
        if(line.startsWith("You've reached the daily limit of coins")||line.startsWith("This block is already occupied!")
                ||line.startsWith("This menu has been throttled! Please slow down")){fail("Server refused sale: "+line);return;}
        var matched=receiptPattern.matcher(line);
        try{if(matched.matches()&&Integer.parseInt(matched.group("quantity").replace(",",""))==rule.quantity()
                &&new BigDecimal(matched.group("coins").replace(",","")).compareTo(rule.total())==0){if(!receipt)matchedReceiptSerial++;receipt=true;}}
        catch(NumberFormatException ignored){}
    }
    private static Pattern receiptPattern(String template,String item){
        StringBuilder regex=new StringBuilder("^");int at=0;
        while(at<template.length()){
            int found=template.length();String token=null;
            for(String candidate:List.of("{quantity}","{item}","{coins}")){int index=template.indexOf(candidate,at);if(index>=0&&index<found){found=index;token=candidate;}}
            regex.append(Pattern.quote(template.substring(at,found)));if(token==null)break;
            regex.append(switch(token){case "{item}"->Pattern.quote(item);case "{quantity}"->"(?<quantity>[1-9][0-9]*(?:,[0-9]{3})*)";
                default->"(?<coins>[0-9]+(?:,[0-9]{3})*(?:\\.[0-9]{1,2})?)";});at=found+token.length();
        }return Pattern.compile(regex.append("$").toString());
    }
    public void cancel(SellingObservation observed,String why,long tick){
        if(terminal()&&!escrowHeld)return;boolean failed=state==State.FAILED;pending=null;reason=why;
        if(rule!=null&&settings.channel==AutoSellMode.GEORGE_PETS&&owned(observed.menu())){
            if(state==State.WAIT_DEPOSIT&&!serverChanged(observed)){state=State.WAIT_CANCEL_DEPOSIT;deadline=tick+settings.timeoutTicks;return;}
            if(observed.cursor()!=null&&observed.cursor().sameItem(rule)){
                state=serverChanged(observed)&&observed.ledger().equals(after)?State.RETURN_ORIGIN:State.WAIT_RETURN_CURSOR;
                next=tick;deadline=tick+settings.timeoutTicks;return;}
            int escrowSlot=ownedTitle.equals(settings.confirmTitle)?settings.confirmPetSlot:settings.depositSlot;var escrow=observed.slots().get(escrowSlot);
            if(observed.cursorEmpty()&&escrow!=null&&escrow.sameItem(rule)){state=State.RETURN_DEPOSIT;next=tick;deadline=tick+settings.timeoutTicks;return;}
        }
        state=failed?State.FAILED:State.CANCELLED;
    }
    private Optional<Action> cleanup(SellingObservation observed,long tick){
        if(!owned(observed.menu())){state=State.CANCELLED;reason="Cleanup menu replaced; preserve current cursor/menu";return Optional.empty();}
        if(state==State.WAIT_CANCEL_DEPOSIT){
            if(!serverChanged(observed))return Optional.empty();var pet=observed.slots().get(settings.depositSlot);
            if(observed.cursor()!=null&&observed.cursor().sameItem(rule)&&observed.ledger().equals(after))state=State.RETURN_ORIGIN;
            else if(observed.cursorEmpty()&&pet!=null&&pet.sameItem(rule)&&observed.ledger().equals(after))state=State.RETURN_DEPOSIT;
            else if(observed.cursorEmpty()&&observed.ledger().equals(before)){escrowHeld=false;state=State.CANCELLED;reason="Deposit rejected; original inventory intact";}
            next=tick+settings.delayTicks;return Optional.empty();
        }
        if(state==State.WAIT_RETURN_CURSOR){
            if(serverChanged(observed)&&observed.cursor()!=null&&observed.cursor().sameItem(rule)&&observed.ledger().equals(after)){
                escrowHeld=true;
                state=State.RETURN_ORIGIN;next=tick+settings.delayTicks;deadline=tick+settings.timeoutTicks;
            }else if(serverChanged(observed)&&observed.cursorEmpty()&&observed.ledger().equals(before)){
                escrowHeld=false;state=State.CANCELLED;reason="Pickup rejected; original inventory intact";
            }return Optional.empty();}
        if(state==State.WAIT_RETURN_ORIGIN){
            if(serverChanged(observed)&&observed.cursorEmpty()&&observed.ledger().equals(before)){escrowHeld=false;state=State.CANCELLED;reason="Cancelled; owned pet restored to exact original inventory slot";}
            return Optional.empty();}
        if(tick<next)return Optional.empty();
        if(state==State.RETURN_DEPOSIT){int escrowSlot=ownedTitle.equals(settings.confirmTitle)?settings.confirmPetSlot:settings.depositSlot;var pet=observed.slots().get(escrowSlot);
            if(!observed.cursorEmpty()||pet==null||!pet.sameItem(rule)){state=State.CANCELLED;reason="Escrow/cursor changed; preserve menu for manual recovery";return Optional.empty();}
            return issue(Kind.RETURN_DEPOSIT,observed,escrowSlot,rule.name());}
        var origin=observed.inventoryStack(rule.inventoryIndex()).orElse(null);
        if(observed.cursor()==null||!observed.cursor().sameItem(rule)||origin==null||origin.slot()<0||!origin.empty()){
            state=State.CANCELLED;reason="Original slot/cursor changed; preserve for manual recovery";return Optional.empty();}
        return issue(Kind.RETURN_ORIGIN,observed,origin.slot(),rule.name());
    }
    public void fail(String why){state=State.FAILED;pending=null;reason=why;}
    public void abandon(String why){state=State.CANCELLED;pending=null;reason=why;}
}
