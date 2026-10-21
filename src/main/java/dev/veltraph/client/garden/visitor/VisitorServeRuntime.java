package dev.veltraph.client.garden.visitor;

import java.util.*;

/** Inventory-ready local serve subset: one guarded request, then observed inventory + menu acknowledgment. */
public final class VisitorServeRuntime {
    public enum State { IDLE, READY, WAITING_ACK, COMPLETE, FAILED, CANCELLED }
    public record Need(String key,int quantity,long unitPrice) {}
    public record Snapshot(Object menu,int menuId,int revision,String title,int acceptSlot,
                           VisitorOffer offer,List<Need> needs,boolean cursorEmpty) {
        public Snapshot { needs=List.copyOf(needs); }
    }
    public record Settings(boolean enabled,int maxQuantity,long maxCost,VisitorSettings policy,long timeoutMs) {}
    public interface Actions { boolean accept(Snapshot expected); }
    private final Actions actions;
    private State state=State.IDLE;
    private Snapshot original;
    private Map<String,Integer> before=Map.of();
    private long started,lastTick;
    private String reason="Idle";
    public VisitorServeRuntime(Actions actions){this.actions=actions;}
    public static Optional<String> validate(Snapshot snapshot,Map<String,Integer> inventory,Settings settings){
        if(!settings.enabled())return Optional.of("Inventory-ready serving requires explicit Auto Serve opt-in");
        if(settings.maxQuantity()<1||settings.maxCost()<1||settings.timeoutMs()<1||!settings.policy().haveMaxPrices())
            return Optional.of("Serving requires positive quantity/cost guards and Max Prices enabled");
        if(snapshot==null||snapshot.offer()==null||snapshot.acceptSlot()<0||snapshot.needs().isEmpty()||!snapshot.cursorEmpty())
            return Optional.of("Open one readable visitor offer with an empty cursor");
        if(snapshot.offer().requiredItems().size()!=snapshot.needs().size())return Optional.of("Offer item identity mismatch");
        for(int i=0;i<snapshot.needs().size();i++){
            var need=snapshot.needs().get(i);var item=snapshot.offer().requiredItems().get(i);
            if(item.amount()!=need.quantity()||item.unitPrice()!=need.unitPrice())return Optional.of("Offer quantity/quote mismatch");
        }
        var keys=new HashSet<String>();long quantity=0,cost=0;
        try{
            for(var need:snapshot.needs()){
                if(need.key()==null||need.key().isBlank()||!keys.add(need.key())||need.quantity()<1||need.unitPrice()<0)
                    return Optional.of("Unknown/ambiguous visitor item, quantity or unit price");
                quantity=Math.addExact(quantity,need.quantity());cost=Math.addExact(cost,Math.multiplyExact(need.unitPrice(),need.quantity()));
                if(inventory.getOrDefault(need.key(),0)<need.quantity())return Optional.of("Required visitor items are not already in inventory");
            }
        }catch(ArithmeticException overflow){return Optional.of("Visitor quantity/cost overflow");}
        if(quantity>settings.maxQuantity())return Optional.of("Visitor quantity exceeds serving cap");
        if(cost>settings.maxCost())return Optional.of("Total visitor item value exceeds serving cap");
        if(!snapshot.offer().allItemPricesKnown()||!VisitorPolicy.decide(snapshot.offer(),settings.policy()).isAccept())
            return Optional.of("Visitor offer failed the configured price/accept policy");
        return Optional.empty();
    }
    public boolean start(Snapshot snapshot,Map<String,Integer> inventory,Settings settings,long now){
        cancel();var invalid=validate(snapshot,inventory,settings);
        if(invalid.isPresent()){state=State.FAILED;reason=invalid.get();return false;}
        original=snapshot;before=Map.copyOf(inventory);started=lastTick=now;state=State.READY;reason="Ready: inventory items and value checked";return true;
    }
    public void tick(boolean valid,Snapshot current,Map<String,Integer> inventory,Settings settings,long now){
        if(!active())return;
        if(!valid||!settings.enabled()){finish(State.CANCELLED,"Visitor context or opt-in changed");return;}
        if(now<lastTick||now-started>=settings.timeoutMs()){finish(State.FAILED,"Visitor acknowledgment timed out");return;}
        lastTick=now;
        if(state==State.WAITING_ACK&&current==null){
            if(consumed(inventory))finish(State.COMPLETE,"Visitor menu closed and exact inventory consumption observed");
            return; // Closing can arrive before inventory synchronization. Keep waiting, without retrying.
        }
        if(current==null||current.menu()!=original.menu()||current.menuId()!=original.menuId()||!Objects.equals(current.title(),original.title())){
            finish(State.CANCELLED,"Visitor menu closed or was replaced");return;
        }
        if(state==State.READY){
            var invalid=validate(current,inventory,settings);
            if(invalid.isPresent()||current.revision()!=original.revision()||!Objects.equals(current.offer(),original.offer())
                    ||current.acceptSlot()!=original.acceptSlot()||!current.needs().equals(original.needs())){
                finish(State.FAILED,"Visitor offer/inventory changed before request");return;
            }
            // The click is a request. Set the state first so failure cannot trigger a duplicate click.
            state=State.WAITING_ACK;reason="Awaiting inventory consumption and fresh menu acknowledgment";
            try{if(!actions.accept(original))finish(State.FAILED,"Visitor request rejected by local menu guard");}
            catch(RuntimeException failure){finish(State.FAILED,"Visitor click failed: "+failure.getMessage());}
            return;
        }
        if(current.acceptSlot()<0&&current.revision()>original.revision()&&current.cursorEmpty()){
            if(consumed(inventory))finish(State.COMPLETE,"Server menu and exact inventory consumption observed");
        }
    }
    private boolean consumed(Map<String,Integer> inventory){
        for(var need:original.needs())if(inventory.getOrDefault(need.key(),0)!=before.getOrDefault(need.key(),0)-need.quantity())return false;
        return true;
    }
    public void reject(String message){if(active())finish(State.FAILED,message);}
    public void cancel(){if(active())finish(State.CANCELLED,"Manual stop");else clear();}
    private void finish(State next,String why){state=next;reason=why;clear();}
    private void clear(){original=null;before=Map.of();}
    public boolean active(){return state==State.READY||state==State.WAITING_ACK;}
    public State state(){return state;}
    public String status(){return state+": "+reason;}
}
