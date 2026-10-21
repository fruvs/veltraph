package dev.veltraph.client.skills;

import dev.veltraph.client.config.AutoSellMode;
import dev.veltraph.client.gui.MenuSnapshot;
import dev.veltraph.client.inventory.BazaarSaleRuntime;
import dev.veltraph.client.inventory.selling.*;
import java.math.BigDecimal;
import java.util.*;
import java.util.regex.Pattern;

/** One acknowledged potion transfer while the Alchemy parent retains the same cookie GUI. */
final class AlchemyPotionSaleCustody {
    enum State { READY, WAIT_ACK, ACKNOWLEDGED, FAILED }
    private State state;
    private SellingRule rule;
    private AlchemySaleProfile profile;
    private Map<Integer,String> before,after;
    private BigDecimal purse;
    private int menuId,requestRevision;
    private long next,deadline,receiptSerial;
    private boolean receipt;
    private Pattern receiptPattern;
    private SellingRuntime.Action pending;
    private SellingRuntime.SaleProof proof;
    private String reason="Idle";

    State state(){return state;}
    String reason(){return reason;}
    SellingRuntime.SaleProof proof(){if(state!=State.ACKNOWLEDGED)throw new IllegalStateException("Potion transfer not acknowledged");return proof;}

    void start(SellingObservation observed,SellingRule rule,AlchemySaleProfile profile,long tick){
        this.rule=rule;this.profile=profile;state=State.READY;proof=null;pending=null;receipt=false;receiptSerial=0;
        receiptPattern=receiptPattern(profile.receiptTemplate(),rule.name());
        before=observed.ledger();var expected=new HashMap<>(before);expected.remove(rule.inventoryIndex());after=Map.copyOf(expected);
        purse=observed.coins();menuId=observed.menu()==null?-1:observed.menu().id();
        next=tick+profile.delayTicks();deadline=tick+profile.timeoutTicks();
        if(!owned(observed)||!observed.cursorEmpty()||purse==null||!rule.matches(observed.inventoryStack(rule.inventoryIndex()).orElse(null))||!quoted(observed))fail("Exact potion custody, purse and quote required");
    }

    private boolean owned(SellingObservation observed){return observed.menu()!=null&&observed.menu().id()==menuId&&observed.menu().title().equals(profile.saleTitle());}
    private boolean quoted(SellingObservation observed){
        if(observed.menu()==null)return false;
        var buttons=observed.menu().slots().stream().filter(s->!s.playerInventory()&&!s.locked()&&MenuSnapshot.clean(s.name()).equals(profile.quoteLabel())).toList();
        if(buttons.size()!=1)return false;
        var quote=BazaarSaleRuntime.parseQuote(buttons.getFirst());
        return quote.isPresent()&&quote.get().quantities().equals(Map.of(rule.name(),rule.quantity()))&&quote.get().proceeds().compareTo(rule.total())==0;
    }

    Optional<SellingRuntime.Action> tick(SellingObservation observed,long tick){
        if(state==State.FAILED||state==State.ACKNOWLEDGED||pending!=null)return Optional.empty();
        if(tick>deadline){fail("Potion receipt timed out; no retry");return Optional.empty();}
        if(!owned(observed)){fail("Potion sale menu replaced or closed");return Optional.empty();}
        if(state==State.WAIT_ACK){
            boolean prediction=observed.ownCursorPrediction()&&observed.cursor()!=null&&observed.cursor().sameItem(rule);
            if(!observed.cursorEmpty()&&!prediction){fail("Foreign potion sale cursor");return Optional.empty();}
            if(!observed.ledger().equals(before)&&!observed.ledger().equals(after)){fail("Protected inventory changed during potion sale");return Optional.empty();}
            if(observed.coins()==null||observed.coins().compareTo(purse)!=0&&observed.coins().compareTo(purse.add(rule.total()))!=0){fail("Purse changed outside exact potion proceeds");return Optional.empty();}
            if(receipt&&observed.menu().revision()!=requestRevision&&observed.cursorEmpty()&&observed.ledger().equals(after)&&observed.coins().compareTo(purse.add(rule.total()))==0){
                proof=new SellingRuntime.SaleProof(AutoSellMode.NPC,rule.identity(),rule.inventoryIndex(),rule.quantity(),rule.total(),purse,observed.coins(),menuId,requestRevision,observed.menu().revision(),receiptSerial);
                state=State.ACKNOWLEDGED;reason="Exact potion receipt, revision, purse, inventory and cursor acknowledged";
            }
            return Optional.empty();
        }
        if(tick<next)return Optional.empty();
        if(!observed.cursorEmpty()||!observed.ledger().equals(before)||observed.coins()==null||observed.coins().compareTo(purse)!=0||!quoted(observed)||!rule.matches(observed.inventoryStack(rule.inventoryIndex()).orElse(null))){fail("Potion custody or quote changed before request");return Optional.empty();}
        var selected=observed.inventoryStack(rule.inventoryIndex()).orElseThrow();
        pending=new SellingRuntime.Action(SellingRuntime.Kind.SELL,observed,selected.slot(),rule.name());
        return Optional.of(pending);
    }

    boolean valid(SellingRuntime.Action action,SellingObservation current){
        var expected=action.observation();
        return action==pending&&owned(current)&&current.menu().revision()==expected.menu().revision()
                &&current.ledger().equals(expected.ledger())&&Objects.equals(current.cursor(),expected.cursor())
                &&Objects.equals(current.slots().get(action.slot()),expected.slots().get(action.slot()))
                &&Objects.equals(current.coins(),expected.coins())&&quoted(current);
    }
    void applied(SellingRuntime.Action action,boolean accepted,long tick){
        if(action!=pending)return;pending=null;
        if(!accepted){fail("Guarded native potion request rejected");return;}
        requestRevision=action.observation().menu().revision();state=State.WAIT_ACK;deadline=tick+profile.timeoutTicks();
    }
    void onMessage(String raw){
        if(state!=State.WAIT_ACK)return;
        String line=MenuSnapshot.clean(raw);if(line.length()>2048)return;
        if(line.startsWith("You've reached the daily limit of coins")||line.startsWith("This block is already occupied!")||line.startsWith("This menu has been throttled! Please slow down")){fail("Server refused potion sale");return;}
        var match=receiptPattern.matcher(line);
        if(match.matches())try{
            if(Integer.parseInt(match.group("quantity").replace(",",""))==rule.quantity()&&new BigDecimal(match.group("coins").replace(",","")).compareTo(rule.total())==0){if(!receipt)receiptSerial++;receipt=true;}
        }catch(NumberFormatException invalid){fail("Malformed potion receipt");}
    }
    private void fail(String why){state=State.FAILED;reason=why;pending=null;}
    private static Pattern receiptPattern(String template,String item){
        var regex=new StringBuilder("^");int at=0;
        while(at<template.length()){
            int found=template.length();String token=null;
            for(String candidate:List.of("{quantity}","{item}","{coins}")){int index=template.indexOf(candidate,at);if(index>=0&&index<found){found=index;token=candidate;}}
            regex.append(Pattern.quote(template.substring(at,found)));if(token==null)break;
            regex.append(switch(token){case "{item}"->Pattern.quote(item);case "{quantity}"->"(?<quantity>[1-9][0-9]*(?:,[0-9]{3})*)";default->"(?<coins>[0-9]+(?:,[0-9]{3})*(?:\\.[0-9]{1,2})?)";});
            at=found+token.length();
        }
        return Pattern.compile(regex.append("$").toString());
    }
}
