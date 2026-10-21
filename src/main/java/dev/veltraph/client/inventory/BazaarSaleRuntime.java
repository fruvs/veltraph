package dev.veltraph.client.inventory;

import dev.veltraph.client.gui.MenuSnapshot;
import java.math.BigDecimal;
import java.util.*;
import java.util.regex.*;

/** One inventory sale, with no optimistic completion, retries, purchases, sacks or dropped items. */
public final class BazaarSaleRuntime {
    public enum State { IDLE, READY, WAIT_CONFIRM, READY_CONFIRM, WAIT_ACK, CLOSING, COMPLETE, FAILED, CANCELLED }
    public record Item(String registryId,String name,int count,String components) {
        public Item(String registryId,String name,int count){this(registryId,name,count,"");}
        public Item { name=MenuSnapshot.clean(name);components=components==null?"":components; }
    }
    public record Settings(String menuTitle,String sellButton,String confirmTitle,String confirmButton,
                           int maxQuantity,long minProceeds,long maxProceeds,int delayTicks,int timeoutTicks) {
        public Settings {
            menuTitle=MenuSnapshot.clean(menuTitle);sellButton=MenuSnapshot.clean(sellButton);
            confirmTitle=MenuSnapshot.clean(confirmTitle);confirmButton=MenuSnapshot.clean(confirmButton);
            if(menuTitle.isBlank()||sellButton.isBlank()||confirmTitle.isBlank()||confirmButton.isBlank()
                    ||menuTitle.equals(confirmTitle)||maxQuantity<1||minProceeds<0||maxProceeds<minProceeds
                    ||delayTicks<1||timeoutTicks<delayTicks) throw new IllegalArgumentException("Invalid sale settings");
        }
    }
    public record Action(int menuId,int revision,int slot,String expectedName,boolean close) {}
    public record Quote(Map<String,Integer> quantities,BigDecimal proceeds,int total) {
        public Quote { quantities=Map.copyOf(quantities); }
    }
    private static final String QUANTITY="(?:[1-9][0-9]*|[1-9][0-9]{0,2}(?:,[0-9]{3})+)";
    private static final Pattern ROW=Pattern.compile("^Selling: ("+QUANTITY+")x (.+)$");
    private static final Pattern TOTAL=Pattern.compile("^Total: ([0-9]+(?:,[0-9]{3})*(?:\\.[0-9]{1,2})?) [Cc]oins$");
    private static final Pattern ACK=Pattern.compile("^\\[Bazaar] Sold ("+QUANTITY+")(?:x .+| items?) for ([0-9]+(?:,[0-9]{3})*(?:\\.[0-9]{1,2})?) coins!$");
    private State state=State.IDLE;
    private Settings settings;
    private Map<String,Integer> before=Map.of(),expected=Map.of();
    private MenuSnapshot original,owned;
    private Quote quote;
    private long next,deadline;
    private boolean chatAck;
    private Action pending;
    private String reason="Idle";
    public State state(){return state;}
    public void reset(){state=State.IDLE;settings=null;owned=null;original=null;pending=null;chatAck=false;quote=null;before=expected=Map.of();reason="Idle";}
    public String reason(){return reason;}
    public boolean terminal(){return state==State.COMPLETE||state==State.FAILED||state==State.CANCELLED;}
    public OptionalInt ownedMenu(){return owned==null?OptionalInt.empty():OptionalInt.of(owned.id());}
    public boolean allowsTitle(String title){return settings!=null&&(settings.menuTitle.equals(MenuSnapshot.clean(title))||settings.confirmTitle.equals(MenuSnapshot.clean(title)));}

    public static Optional<Quote> parseQuote(MenuSnapshot.Slot slot) {
        if(slot.playerInventory()||slot.locked())return Optional.empty();
        Map<String,Integer> quantities=new LinkedHashMap<>();BigDecimal coins=null;int total=0;
        try {
            for(String raw:slot.lore()) {
                String line=MenuSnapshot.clean(raw);Matcher row=ROW.matcher(line),price=TOTAL.matcher(line);
                if(row.matches()) {
                    int count=Integer.parseInt(row.group(1).replace(",",""));String name=row.group(2).strip();
                    if(name.isBlank()||quantities.putIfAbsent(name,count)!=null)return Optional.empty();
                    total=Math.addExact(total,count);
                } else if(price.matches()) {
                    if(coins!=null)return Optional.empty();coins=new BigDecimal(price.group(1).replace(",",""));
                } else if(line.startsWith("Selling:")||line.startsWith("Total:")) return Optional.empty();
            }
        } catch(ArithmeticException|NumberFormatException e){return Optional.empty();}
        return total>0&&coins!=null?Optional.of(new Quote(quantities,coins,total)):Optional.empty();
    }
    private static String key(Item item){return item.registryId+"\n"+item.name+"\n"+item.components;}
    private static Map<String,Integer> counts(List<Item> items){
        Map<String,Integer> result=new HashMap<>();
        for(Item item:items)if(item.count>0)result.merge(key(item),item.count,Math::addExact);
        return Map.copyOf(result);
    }
    private MenuSnapshot.Slot button(MenuSnapshot menu,String label){
        var matches=menu.slots().stream().filter(s->!s.playerInventory()&&!s.locked()&&MenuSnapshot.clean(s.name()).equals(label)).toList();
        return matches.size()==1?matches.getFirst():null;
    }
    public void start(MenuSnapshot menu,List<Item> inventory,Settings options,long tick,boolean cursorEmpty){
        state=State.IDLE;settings=options;owned=null;original=null;pending=null;chatAck=false;quote=null;
        try{before=counts(inventory);}catch(ArithmeticException e){fail("Inventory quantity overflow");return;}
        if(!cursorEmpty){fail("Cursor must be empty");return;}
        if(menu==null||!MenuSnapshot.clean(menu.title()).equals(options.menuTitle)){fail("Open the configured Bazaar menu");return;}
        var sell=button(menu,options.sellButton);
        if(sell==null){fail("Sell button missing, ambiguous or locked");return;}
        var parsed=parseQuote(sell);
        if(parsed.isEmpty()){fail("Sale requires explicit Selling and Total quote lines");return;}
        quote=parsed.get();
        if(quote.total>options.maxQuantity||quote.proceeds.compareTo(BigDecimal.valueOf(options.minProceeds))<0
                ||quote.proceeds.compareTo(BigDecimal.valueOf(options.maxProceeds))>0){fail("Sale quantity/proceeds outside configured bounds");return;}
        Map<String,Integer> target=new HashMap<>(before);
        for(var entry:quote.quantities.entrySet()){
            var matching=inventory.stream().filter(i->i.count>0&&i.name.equals(entry.getKey())).map(BazaarSaleRuntime::key).distinct().toList();
            if(matching.size()!=1||target.getOrDefault(matching.getFirst(),0)<entry.getValue()){fail("Quoted item unavailable or display-name identity ambiguous");return;}
            var identity=matching.getFirst();int remaining=target.get(identity)-entry.getValue();
            if(remaining==0)target.remove(identity);else target.put(identity,remaining);
        }
        expected=Map.copyOf(target);owned=original=menu;state=State.READY;reason="Quoted "+quote.total+" items for "+quote.proceeds+" coins";
        next=tick+options.delayTicks;deadline=tick+options.timeoutTicks;
    }
    public Optional<Action> tick(MenuSnapshot menu,List<Item> inventory,long tick,boolean cursorEmpty){
        return tick(menu,inventory,tick,cursorEmpty,false);
    }
    public Optional<Action> tick(MenuSnapshot menu,List<Item> inventory,long tick,boolean cursorEmpty,boolean ownPredictedCursor){
        if(terminal()||state==State.IDLE||pending!=null)return Optional.empty();
        if(tick>deadline){fail("Timed out waiting for server acknowledgment");return Optional.empty();}
        if(!cursorEmpty&&!(ownPredictedCursor&&(state==State.WAIT_CONFIRM||state==State.WAIT_ACK))){cancel("Cursor changed during sale");return Optional.empty();}
        if(state==State.CLOSING){
            if(menu==null){state=State.COMPLETE;reason="Sale acknowledged; menu closed";return Optional.empty();}
            if(!sameOwned(menu)){cancel("Menu replaced during cleanup");return Optional.empty();}
            if(tick<next)return Optional.empty();
            return issue(new Action(menu.id(),menu.revision(),-1,"",true));
        }
        if(state==State.WAIT_ACK){
            if(menu!=null&&!sameOwned(menu)){cancel("Unexpected menu while awaiting sale");return Optional.empty();}
            if(chatAck&&cursorEmpty&&counts(inventory).equals(expected)){
                state=menu==null?State.COMPLETE:State.CLOSING;reason="Sale message and inventory delta acknowledged";deadline=tick+settings.timeoutTicks;
            }
            return Optional.empty();
        }
        if(menu==null){cancel("Sale menu closed");return Optional.empty();}
        if(!counts(inventory).equals(before)){cancel("Inventory changed before confirmation");return Optional.empty();}
        if(state==State.WAIT_CONFIRM){
            if(MenuSnapshot.clean(menu.title()).equals(settings.confirmTitle)
                    &&(menu.id()!=original.id()||menu.revision()!=original.revision())){
                owned=menu;state=State.READY_CONFIRM;next=tick+settings.delayTicks;deadline=tick+settings.timeoutTicks;
            }else if(!sameOwned(menu)){cancel("Unexpected confirmation menu");return Optional.empty();}
            else return Optional.empty();
        }
        if(!sameOwned(menu)){cancel("Sale menu identity changed");return Optional.empty();}
        if(tick<next)return Optional.empty();
        String label=state==State.READY?settings.sellButton:settings.confirmButton;
        var slot=button(menu,label);var current=slot==null?Optional.<Quote>empty():parseQuote(slot);
        if(current.isEmpty()||!current.get().equals(quote)){fail("Sale quote changed or confirmation is not explicitly priced");return Optional.empty();}
        return issue(new Action(menu.id(),menu.revision(),slot.id(),slot.name(),false));
    }
    private boolean sameOwned(MenuSnapshot menu){return owned!=null&&menu.id()==owned.id()&&MenuSnapshot.clean(menu.title()).equals(MenuSnapshot.clean(owned.title()));}
    private Optional<Action> issue(Action action){pending=action;return Optional.of(action);}
    /** The adapter must re-read and compare the complete quote immediately before issuing this action. */
    public boolean validates(Action action,MenuSnapshot current){
        if(pending!=action||current==null||current.id()!=action.menuId||current.revision()!=action.revision||!sameOwned(current))return false;
        if(action.close)return true;
        var slot=button(current,action.expectedName);
        return slot!=null&&slot.id()==action.slot&&parseQuote(slot).filter(quote::equals).isPresent();
    }
    public void applied(Action action,boolean accepted,long tick){
        if(action!=pending)return;pending=null;
        if(!accepted){fail("Guarded menu action rejected");return;}
        if(action.close){next=tick+settings.delayTicks;return;}
        state=state==State.READY?State.WAIT_CONFIRM:State.WAIT_ACK;deadline=tick+settings.timeoutTicks;
    }
    public void onMessage(String raw){
        if(state!=State.WAIT_CONFIRM&&state!=State.READY_CONFIRM&&state!=State.WAIT_ACK)return;
        String line=MenuSnapshot.clean(raw);
        for(String failure:List.of("This server is too laggy","You can't use this when the server is about to restart!","You need SkyBlock Level 7",
                "[Bazaar] No items could be matched","[Bazaar] There are no Sell Offers for this product!","[Bazaar] You cannot afford this!",
                "[Bazaar] You don't have anything to sell","This menu has been throttled! Please slow down","You don't have enough of an item in your inventories.",
                "[Bazaar] Couldn't match the","[Bazaar] You don't have enough"))if(line.startsWith(failure)){fail("Server refused sale: "+line);return;}
        if(state!=State.WAIT_ACK)return;
        Matcher ack=ACK.matcher(line);
        try{if(ack.matches()&&Integer.parseInt(ack.group(1).replace(",",""))==quote.total
                &&new BigDecimal(ack.group(2).replace(",","")).compareTo(quote.proceeds)==0)chatAck=true;}
        catch(NumberFormatException ignored){}
    }
    public void cancel(String why){if(!terminal()){state=State.CANCELLED;pending=null;reason=why;}}
    private void fail(String why){state=State.FAILED;pending=null;reason=why;}
}
