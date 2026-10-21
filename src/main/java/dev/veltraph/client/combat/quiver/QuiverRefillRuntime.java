package dev.veltraph.client.combat.quiver;

import dev.veltraph.client.gui.MenuSnapshot;
import java.math.BigDecimal;
import java.util.*;
import java.util.regex.*;

/** One explicitly quoted refill. Neither a message, a menu change nor extra arrows alone completes it. */
public final class QuiverRefillRuntime {
    public enum State { IDLE, READY, WAIT_ACK, CLOSING, COMPLETE, FAILED, CANCELLED }
    public record Item(String id,String name,String components,int count) {}
    public record Settings(String title,String button,Item ammo,int quantity,long maxCost,String quoteTemplate,String ackTemplate,int delay,int timeout,
                           QuiverSupply supply,String supplyButton,String supplyTemplate,boolean requireCurrency) {
        public Settings {
            title=MenuSnapshot.clean(title);button=MenuSnapshot.clean(button);
            if(title.isBlank()||button.isBlank()||ammo==null||ammo.id().isBlank()||ammo.name().isBlank()||quantity<1
                    ||maxCost<0||delay<1||timeout<delay||timeout>2400)throw new IllegalArgumentException("Configure exact quiver labels, item, quantity, cost and timing");
            pattern(quoteTemplate);pattern(ackTemplate,!requireCurrency);
            if(!ackTemplate.startsWith("You filled your quiver with"))throw new IllegalArgumentException("Acknowledgment must retain recovered refill prefix");
            if(supply==null||supply==QuiverSupply.CONTAINER&&(supplyButton==null||supplyButton.isBlank()||supplyTemplate==null||supplyTemplate.isBlank()))throw new IllegalArgumentException("Configure owned quiver count label and template");
            if(supply==QuiverSupply.CONTAINER)countPattern(supplyTemplate);
        }
    }
    public record Quote(int quantity,String item,BigDecimal cost) {}
    public record Action(MenuSnapshot menu,int slot,boolean close) {}
    private Settings settings;private State state=State.IDLE;private MenuSnapshot owned;
    private Map<String,Integer> before=Map.of(),expected=Map.of();private BigDecimal funds,expectedFunds;
    private Quote quote;private Action pending;private boolean messageAck;private long next,deadline;private int supplyBefore=-1;
    private MenuSnapshot.Slot quotedButton;private int closedSupply=-1;
    private String status="Idle";
    public State state(){return state;}public String status(){return status;}
    public boolean terminal(){return state==State.COMPLETE||state==State.FAILED||state==State.CANCELLED;}
    public boolean owns(MenuSnapshot menu){return menu!=null&&owned!=null&&menu.id()==owned.id()&&menu.title().equals(owned.title());}
    private static String key(Item item){return item.id()+"\n"+item.name()+"\n"+item.components();}
    private static Map<String,Integer> counts(List<Item> items){Map<String,Integer> map=new HashMap<>();for(var i:items)if(i.count()>0)map.merge(key(i),i.count(),Math::addExact);return Map.copyOf(map);}
    private static Pattern pattern(String template){
        return pattern(template,true);
    }
    private static Pattern pattern(String template,boolean costRequired){
        if(template==null||template.isBlank()||template.length()>512)throw new IllegalArgumentException("Configure quote and acknowledgment templates");
        StringBuilder regex=new StringBuilder("^");Set<String> fields=new HashSet<>();int start=0;
        Matcher matcher=Pattern.compile("\\{([^{}]+)}").matcher(template);
        while(matcher.find()){
            regex.append(Pattern.quote(template.substring(start,matcher.start())));String field=matcher.group(1);
            if(!Set.of("quantity","item","cost").contains(field)||!fields.add(field))throw new IllegalArgumentException("Templates require one quantity, item and cost placeholder");
            regex.append("(?<").append(field).append(">").append(field.equals("item")?".+?":field.equals("quantity")?"[1-9][0-9]*(?:,[0-9]{3})*":"[0-9]+(?:,[0-9]{3})*(?:\\.[0-9]{1,2})?").append(')');start=matcher.end();
        }
        if(!fields.contains("quantity")||!fields.contains("item")||costRequired&&!fields.contains("cost"))throw new IllegalArgumentException("Templates require quantity/item and an observable cost");
        return Pattern.compile(regex.append(Pattern.quote(template.substring(start))).append('$').toString());
    }
    private static Optional<Quote> parse(String text,String template){
        var match=pattern(template).matcher(MenuSnapshot.clean(text));if(!match.matches())return Optional.empty();
        try{return Optional.of(new Quote(Integer.parseInt(match.group("quantity").replace(",","")),match.group("item"),new BigDecimal(match.group("cost").replace(",",""))));}
        catch(NumberFormatException e){return Optional.empty();}
    }
    private Optional<Quote> receipt(String text){
        var match=pattern(settings.ackTemplate(),!settings.requireCurrency()).matcher(MenuSnapshot.clean(text));if(!match.matches())return Optional.empty();
        try{return Optional.of(new Quote(Integer.parseInt(match.group("quantity").replace(",","")),match.group("item"),settings.ackTemplate().contains("{cost}")?new BigDecimal(match.group("cost").replace(",","")):null));}
        catch(NumberFormatException e){return Optional.empty();}
    }
    private static Pattern countPattern(String template){
        if(template==null||template.isBlank()||template.length()>512)throw new IllegalArgumentException("Count template required");
        String quoted=Pattern.quote(template);if(!template.contains("{quantity}")||!template.contains("{item}")||template.indexOf("{quantity}")!=template.lastIndexOf("{quantity}")||template.indexOf("{item}")!=template.lastIndexOf("{item}"))throw new IllegalArgumentException("Count template requires one quantity and item");
        return Pattern.compile("^"+quoted.replace("{quantity}","\\E(?<quantity>[0-9]+(?:,[0-9]{3})*)\\Q").replace("{item}","\\E(?<item>.+?)\\Q")+"$");
    }
    public OptionalInt observedSupply(MenuSnapshot menu){
        if(settings==null||menu==null||settings.supply()!=QuiverSupply.CONTAINER||!menu.title().equals(settings.title()))return OptionalInt.empty();
        var slots=menu.slots().stream().filter(s->!s.playerInventory()&&!s.locked()&&s.count()>0&&s.name().equals(settings.supplyButton())).toList();if(slots.size()!=1)return OptionalInt.empty();
        var pattern=countPattern(settings.supplyTemplate());Integer count=null;
        for(var line:slots.getFirst().lore()){var match=pattern.matcher(MenuSnapshot.clean(line));if(match.matches())try{
            if(count!=null||!match.group("item").equals(settings.ammo().name()))return OptionalInt.empty();count=Integer.parseInt(match.group("quantity").replace(",",""));
        }catch(NumberFormatException e){return OptionalInt.empty();}}
        return count==null?OptionalInt.empty():OptionalInt.of(count);
    }
    /** Read-only final server contents of the exact owned container after its close packet. */
    public void observeClosedSupply(MenuSnapshot menu){if(state==State.WAIT_ACK&&owns(menu))closedSupply=observedSupply(menu).orElse(-1);}
    private MenuSnapshot.Slot button(MenuSnapshot menu){
        var slots=menu.slots().stream().filter(s->!s.playerInventory()&&!s.locked()&&s.count()>0&&MenuSnapshot.clean(s.name()).equals(settings.button())).toList();
        return slots.size()==1?slots.getFirst():null;
    }
    private Optional<Quote> quote(MenuSnapshot menu){var slot=button(menu);if(slot==null)return Optional.empty();var quotes=slot.lore().stream().map(l->parse(l,settings.quoteTemplate())).flatMap(Optional::stream).toList();return quotes.size()==1?Optional.of(quotes.getFirst()):Optional.empty();}
    public void start(MenuSnapshot menu,List<Item> inventory,BigDecimal currency,Settings options,long tick,boolean cursorEmpty){
        settings=options;state=State.IDLE;owned=null;pending=null;messageAck=false;closedSupply=-1;status="Starting refill";
        if(!cursorEmpty||menu==null||!MenuSnapshot.clean(menu.title()).equals(settings.title())){fail("Unknown menu or occupied cursor");return;}
        var parsed=quote(menu);if(parsed.isEmpty()){fail("Exact refill quote missing or ambiguous");return;}quote=parsed.get();quotedButton=button(menu);
        if(quote.quantity()!=settings.quantity()||!quote.item().equals(settings.ammo().name())||quote.cost().signum()<0||quote.cost().compareTo(BigDecimal.valueOf(settings.maxCost()))>0
                ||settings.requireCurrency()&&(currency==null||currency.compareTo(quote.cost())<0)){fail("Item, quantity, cost or funds outside configured contract");return;}
        before=counts(inventory);var after=new HashMap<>(before);
        if(settings.supply()==QuiverSupply.PLAYER_INVENTORY)after.merge(key(settings.ammo()),settings.quantity(),Math::addExact);
        else {var observed=observedSupply(menu);if(observed.isEmpty()){fail("Owned quiver count absent or ambiguous");return;}supplyBefore=observed.getAsInt();if(supplyBefore>Integer.MAX_VALUE-settings.quantity()){fail("Refill count exceeds integer range");return;}}
        expected=Map.copyOf(after);
        funds=currency;expectedFunds=currency==null?null:currency.subtract(quote.cost());owned=menu;state=State.READY;next=tick+settings.delay();deadline=tick+settings.timeout();status="Refill quote accepted; waiting to revalidate";
    }
    public Optional<Action> tick(MenuSnapshot menu,List<Item> inventory,BigDecimal currency,long tick,boolean cursorEmpty,boolean ownPrediction){
        if(terminal()||state==State.IDLE||pending!=null)return Optional.empty();
        if(tick>deadline){fail("Refill acknowledgment timed out; no retry");return Optional.empty();}
        if(!cursorEmpty&&!(state==State.WAIT_ACK&&ownPrediction)){cancel("Cursor ownership changed");return Optional.empty();}
        if(menu!=null&&!owns(menu)){cancel("Refill menu ownership changed");return Optional.empty();}
        if(state==State.WAIT_ACK){
            boolean supplyAck=settings.supply()==QuiverSupply.PLAYER_INVENTORY||observedSupply(menu).orElse(menu==null?closedSupply:-1)==supplyBefore+settings.quantity();
            boolean currencyAck=!settings.requireCurrency()||currency!=null&&currency.compareTo(expectedFunds)==0;
            if(messageAck&&supplyAck&&cursorEmpty&&currencyAck&&counts(inventory).equals(expected)){
                state=menu==null?State.COMPLETE:State.CLOSING;status="Item, quantity and message acknowledged"+(settings.requireCurrency()?"; funds debit verified":"; exact quoted receipt cost matched");deadline=tick+settings.timeout();
            }return Optional.empty();
        }
        if(state==State.CLOSING){if(menu==null){state=State.COMPLETE;status="Refill acknowledged; owned menu closed";return Optional.empty();}return issue(new Action(menu,-1,true));}
        if(menu==null){cancel("Refill menu closed before purchase");return Optional.empty();}
        if(!before.equals(counts(inventory))||settings.requireCurrency()&&(currency==null||currency.compareTo(funds)!=0)||settings.supply()==QuiverSupply.CONTAINER&&observedSupply(menu).orElse(-1)!=supplyBefore){cancel("Inventory, quiver count or currency changed before purchase");return Optional.empty();}
        if(tick<next)return Optional.empty();
        if(!quotedButton.equals(button(menu))||quote(menu).filter(q->same(q,quote)).isEmpty()){fail("Refill button identity or quote changed before purchase");return Optional.empty();}
        return issue(new Action(menu,button(menu).id(),false));
    }
    private Optional<Action> issue(Action action){pending=action;return Optional.of(action);}
    private static boolean same(Quote a,Quote b){return a.quantity()==b.quantity()&&a.item().equals(b.item())&&a.cost().compareTo(b.cost())==0;}
    public boolean validates(Action action,MenuSnapshot fresh){return action==pending&&fresh!=null&&owns(fresh)&&fresh.revision()==action.menu().revision()
            &&(action.close()||quotedButton.equals(button(fresh))&&button(fresh).id()==action.slot()&&quote(fresh).filter(q->same(q,quote)).isPresent());}
    public void applied(Action action,boolean accepted){if(pending!=action)return;pending=null;if(!accepted){fail("Guarded refill action rejected");return;}if(!action.close()){state=State.WAIT_ACK;status="One refill sent; waiting for server acknowledgments";}}
    public void onMessage(String text){if(state==State.WAIT_ACK&&receipt(text).filter(q->q.quantity()==quote.quantity()&&q.item().equals(quote.item())&&(q.cost()==null||q.cost().compareTo(quote.cost())==0)).isPresent())messageAck=true;}
    public void cancel(String why){if(!terminal()){state=State.CANCELLED;pending=null;status=why;}}
    private void fail(String why){state=State.FAILED;pending=null;status=why;}
}
