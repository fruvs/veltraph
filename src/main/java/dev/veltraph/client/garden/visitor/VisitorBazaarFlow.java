package dev.veltraph.client.garden.visitor;

import dev.veltraph.client.gui.MenuSnapshot;
import java.math.*;
import java.util.*;
import java.util.regex.Pattern;

/** One product/amount transaction. Every step waits for a fresh server menu; confirmation is never retried. */
public final class VisitorBazaarFlow {
    public enum State { IDLE, PRODUCT, AMOUNT, SIGN, CONFIRM, ACK, COMPLETE, FAILED }
    public enum Kind { NONE, CLICK, SIGN, CLOSE }
    public record Action(Kind kind,MenuSnapshot menu,MenuSnapshot.Slot slot,String text){
        static Action none(){return new Action(Kind.NONE,null,null,"");}
    }
    public record Quote(int quantity,long total,long unitCeiling){}
    private State state=State.IDLE;private String item,reason="Idle";private int quantity,before;
    private long cap,started,last,timeout,actualCost=-1;private boolean buy,ack;private MenuSnapshot previous;private Quote quote;
    public void start(String item,int quantity,int before,boolean buy,long cap,long now,long timeout){
        this.item=item;this.quantity=quantity;this.before=before;this.buy=buy;this.cap=cap;this.started=this.last=now;this.timeout=timeout;
        ack=false;actualCost=-1;previous=null;quote=null;state=State.PRODUCT;reason="Waiting for Bazaar product "+item;
        if(item==null||!item.matches("[\\p{L}\\p{N} '\\-]+")||quantity<1||before<0||cap<1||timeout<1)fail("Invalid Bazaar request");
    }
    public Action tick(MenuSnapshot menu,boolean sign,int inventory,long now){
        if(!active())return Action.none();
        if(now<last||now-started>=timeout){fail("Bazaar step timed out; request will not be retried");return Action.none();}last=now;
        if(state==State.ACK){
            if(ack&&inventory==before+quantity){state=State.COMPLETE;reason="Purchase message and exact inventory increase observed";return menu==null?Action.none():new Action(Kind.CLOSE,menu,null,"");}
            return Action.none();
        }
        if(state==State.SIGN){
            if(sign){state=State.CONFIRM;previous=null;return new Action(Kind.SIGN,null,null,String.valueOf(quantity));}
            return Action.none();
        }
        if(menu==null||Objects.equals(menu,previous))return Action.none();
        if(!menu.title().toLowerCase(Locale.ROOT).contains("bazaar")&&!menu.title().equalsIgnoreCase(item)
                &&!menu.title().toLowerCase(Locale.ROOT).contains("instant buy")&&!menu.title().equals("How many do you want?")){fail("Unexpected Bazaar menu: "+menu.title());return Action.none();}
        if(state==State.PRODUCT){
            var instant=unique(menu,"Buy Instantly");
            if(instant!=null){if(!product(menu)){fail("Bazaar product identity not confirmed");return Action.none();}return click(menu,instant,State.AMOUNT);}
            var product=unique(menu,item);if(product!=null)return click(menu,product,State.PRODUCT);
        }else if(state==State.AMOUNT){
            var custom=unique(menu,"Custom Amount");if(custom!=null)return click(menu,custom,State.SIGN);
        }else if(state==State.CONFIRM){
            var confirmations=menu.slots().stream().filter(s->!s.playerInventory()&&!s.locked()
                    &&Set.of("Confirm Instant Buy","Confirm Purchase","Buy Instantly").contains(MenuSnapshot.clean(s.name()))).toList();
            if(confirmations.size()!=1)return Action.none();var confirm=confirmations.getFirst();
            var parsed=readQuote(confirm.lore(),item,quantity);
            if(parsed.isEmpty()||parsed.get().total()>cap){fail("Unknown, changed product/quantity, or over-budget Bazaar confirmation");return Action.none();}
            quote=parsed.get();
            if(!buy){state=State.COMPLETE;reason="Quoted exact requested product and quantity";return new Action(Kind.CLOSE,menu,null,"");}
            ack=false;return click(menu,confirm,State.ACK);
        }
        return Action.none();
    }
    private boolean product(MenuSnapshot menu){
        return menu.title().equalsIgnoreCase(item)||menu.slots().stream().anyMatch(s->!s.playerInventory()&&MenuSnapshot.clean(s.name()).equalsIgnoreCase(item));
    }
    private static MenuSnapshot.Slot unique(MenuSnapshot menu,String name){var slots=menu.slots().stream().filter(s->!s.playerInventory()&&!s.locked()&&MenuSnapshot.clean(s.name()).equalsIgnoreCase(name)).toList();return slots.size()==1?slots.getFirst():null;}
    private Action click(MenuSnapshot menu,MenuSnapshot.Slot slot,State next){previous=menu;state=next;reason="Waiting after "+slot.name();return new Action(Kind.CLICK,menu,slot,"");}
    public static Optional<Quote> readQuote(List<String> lore,String item,int quantity){
        String product=null;Long cost=null;Integer amount=null;
        var requested=Pattern.compile("^(?:Buying:|Item:)\\s*(?:(\\d+(?:,\\d{3})*)x\\s+(.+)|(.+)\\s+x(\\d+(?:,\\d{3})*))$",Pattern.CASE_INSENSITIVE);
        var itemLine=Pattern.compile("^Item:\\s*(.+)$",Pattern.CASE_INSENSITIVE);
        var amountLine=Pattern.compile("^Amount:\\s*([0-9]+(?:,[0-9]{3})*)x?$",Pattern.CASE_INSENSITIVE);
        var price=Pattern.compile("^(?:Cost:|Total Cost:|Price:)\\s*([0-9]+(?:,[0-9]{3})*(?:\\.[0-9]+)?)\\s+Coins$",Pattern.CASE_INSENSITIVE);
        try{for(String raw:lore){String line=BazaarPriceParser.stripColorCodes(raw).strip();var p=requested.matcher(line);var c=price.matcher(line);var il=itemLine.matcher(line);var al=amountLine.matcher(line);
            if(p.matches()){if(product!=null||amount!=null)return Optional.empty();amount=Integer.parseInt((p.group(1)!=null?p.group(1):p.group(4)).replace(",",""));product=p.group(2)!=null?p.group(2):p.group(3);}
            else if(il.matches()){if(product!=null)return Optional.empty();product=il.group(1);}
            else if(al.matches()){if(amount!=null)return Optional.empty();amount=Integer.parseInt(al.group(1).replace(",",""));}
            if(c.matches()){if(cost!=null)return Optional.empty();cost=new BigDecimal(c.group(1).replace(",","")).setScale(0,RoundingMode.CEILING).longValueExact();}
        }
        if(product==null||!product.equalsIgnoreCase(item)||amount==null||amount!=quantity||cost==null||cost<0||quantity<1)return Optional.empty();
        long unit=cost/quantity+(cost%quantity==0?0:1);return Optional.of(new Quote(quantity,cost,unit));
        }catch(ArithmeticException|NumberFormatException invalid){return Optional.empty();}
    }
    public void message(String message){
        if(state!=State.ACK||message==null)return;
        String clean=BazaarPriceParser.stripColorCodes(message).strip();
        var bought=Pattern.compile("^\\[Bazaar\\] Bought ([0-9]+(?:,[0-9]{3})*)x (.+) for ([0-9]+(?:,[0-9]{3})*(?:\\.[0-9]+)?) coins!$",Pattern.CASE_INSENSITIVE).matcher(clean);
        if(bought.matches()){
            try{
                int count=Integer.parseInt(bought.group(1).replace(",",""));long charged=new BigDecimal(bought.group(3).replace(",",""))
                        .setScale(0,RoundingMode.CEILING).longValueExact();
                if(count!=quantity||!bought.group(2).equalsIgnoreCase(item)||quote==null||charged>quote.total()||charged>cap){fail("Purchase acknowledgment product, quantity or cost differs from guarded request");return;}
                actualCost=charged;ack=true;
            }catch(ArithmeticException|NumberFormatException invalid){fail("Invalid purchase acknowledgment quantity/cost");}
        }else if(clean.startsWith("[Bazaar] Bought "))fail("Unreadable purchase acknowledgment");
    }
    public void fail(String why){state=State.FAILED;reason=why;}
    /** Refresh the remaining budget before a request; increasing a setting never loosens its original guard. */
    public void capAtMost(long remaining){if(active()&&state!=State.ACK){cap=Math.min(cap,remaining);if(cap<1)fail("Bazaar budget was reduced below the remaining request");}}
    public boolean active(){return state!=State.IDLE&&state!=State.COMPLETE&&state!=State.FAILED;}
    public State state(){return state;}public Quote quote(){return quote;}public long actualCost(){return actualCost;}public String status(){return state+": "+reason;}
}
