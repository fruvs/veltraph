package dev.veltraph.client.skills;

import dev.veltraph.client.config.AutoSellMode;
import dev.veltraph.client.inventory.selling.*;
import java.math.BigDecimal;
import java.util.*;

/** One produced batch: exact Bazaar INVENTORY/confirmation or one observed cookie fallback. */
public final class CarpentrySaleParent {
    public enum State { IDLE, PREPARE_BAZAAR, BAZAAR, CLOSE_FOR_COOKIE, OPEN_SB_MENU, WAIT_SB_MENU,
        OPEN_COOKIE, WAIT_COOKIE, COOKIE_SALE, COMPLETE, FAILED, CANCELLED }
    public enum Kind { BAZAAR, NPC, CLOSE, COMMAND, COOKIE_BUTTON }
    public record Scope(Object player,Object world,Object connection,String profile) {
        boolean same(Scope other){return other!=null&&player==other.player&&world==other.world&&connection==other.connection&&profile.equals(other.profile);}
    }
    public record Observation(Scope scope,Object nativeMenu,int selected,boolean cookieActive,SellingObservation value) {}
    public record Action(Kind kind,Observation observed,Object child,int slot,String command) {}
    public record Proof(AutoSellMode channel,int quantity,BigDecimal proceeds,Object nativeMenu,int menuId,
                        int requestRevision,int receiptRevision,long receiptSerial,Scope scope) {}
    private State state=State.IDLE;private String reason="Idle";private CarpentryFlowProfile.Sale sale;
    private BazaarCustodyPlan plan;private BazaarCustodyRuntime bazaar;private SellingRuntime npc;private SellingRule rule;
    private Observation initial;private Object menu;private int lastId,lastRevision;private long deadline;private int timeout;
    private boolean rejection,committed;private Action pending;private Proof proof;private Object proofMenu;
    public State state(){return state;}public String status(){return state+": "+reason;}
    public boolean terminal(){return EnumSet.of(State.COMPLETE,State.FAILED,State.CANCELLED).contains(state);}
    public Optional<Proof> acknowledged(){return state==State.COMPLETE?Optional.ofNullable(proof):Optional.empty();}
    public SellingRule fallbackRule(){return rule;}
    public Object ownedNativeMenu(){return menu;}
    public void start(Observation observed,CarpentryFlowProfile.Sale sale,int sink,int quantity,long tick,int timeout) {
        this.sale=Objects.requireNonNull(sale);this.timeout=timeout;initial=observed;menu=observed.nativeMenu();
        pending=null;proof=null;proofMenu=null;rule=null;rejection=committed=false;deadline=tick+timeout;
        var value=observed.value();var stack=value.inventoryStack(sink).orElse(null);
        if(observed.scope()==null||observed.scope().player()==null||observed.scope().world()==null||observed.scope().connection()==null
                ||sale.protocol()==null||value.menu()==null||!value.menu().title().equals(sale.target().title())
                ||!value.cursorEmpty()||value.coins()==null||stack==null||stack.count()!=quantity||stack.protectedItem()||stack.wearable()) {fail("Exact produced main-stack Bazaar handoff required");return;}
        plan=new BazaarCustodyPlan(sale.target().command().isEmpty()?"explicit-block":sale.target().command(),sale.target().title(),sale.protocol().confirmationTitle(),sale.quoteLabel(),sale.protocol().confirmationLabel(),"","",
            sale.receipt().replace("{item}",stack.name()),"explicit-parent-purse",SellingAcknowledgment.Denomination.UNKNOWN,
            List.of(new BazaarCustodyPlan.InventoryItem(sink,stack.registryId(),stack.name(),stack.components(),quantity,sale.unitProceeds())),List.of(),quantity,sale.maxProceeds(),1,timeout);
        bazaar=new BazaarCustodyRuntime();npc=null;state=State.PREPARE_BAZAAR;reason="Waiting for exact priced quote or declared uncommitted rejection";
    }
    private boolean conserved(Observation o){return o.value().cursorEmpty()&&o.value().ledger().equals(initial.value().ledger())&&Objects.equals(o.value().coins(),initial.value().coins())&&o.selected()==initial.selected();}
    private boolean own(Observation o){return o.nativeMenu()==menu;}
    private boolean marker(Observation o){var f=sale.protocol().fallback();return f!=null&&o.value().menu()!=null&&o.value().menu().slots().stream().filter(s->!s.playerInventory()&&s.name().equals(f.rejectionLabel())&&s.count()>0).count()==1;}
    private void transition(State next,long tick){state=next;deadline=tick+timeout;}
    private Optional<Action> issue(Kind kind,Observation observed,Object child,int slot,String command){pending=new Action(kind,observed,child,slot,command);return Optional.of(pending);}
    public Optional<Action> tick(Observation o,long tick) {
        if(terminal()||state==State.IDLE||pending!=null)return Optional.empty();
        if(!initial.scope().same(o.scope())||o.selected()!=initial.selected()){cancel("Exact native player/world/connection/profile/selection changed");return Optional.empty();}
        if(tick>deadline){fail("Bounded sale/ingress acknowledgment timeout; no fallback or retry");return Optional.empty();}
        var value=o.value();
        if(state==State.PREPARE_BAZAAR) {
            if(!own(o)||!conserved(o)){cancel("Bazaar custody/menu changed before commit");return Optional.empty();}
            if(rejection) {
                if(!o.cookieActive()||!marker(o)){fail("Rejected Bazaar output lacks current cookie/declared menu eligibility");return Optional.empty();}
                transition(State.CLOSE_FOR_COOKIE,tick);reason="Declared uncommitted rejection; conserved exact batch eligible for cookie fallback";
            } else {
                var trial=new BazaarCustodyRuntime();trial.start(value,plan,BazaarCustodyRuntime.Stage.INVENTORY,tick);
                if(!trial.terminal()){bazaar=trial;transition(State.BAZAAR,tick);reason="Bazaar inventory quote bound";}
                return Optional.empty();
            }
        }
        if(state==State.CLOSE_FOR_COOKIE) {
            if(!conserved(o)||!o.cookieActive()){cancel("Cookie fallback custody/eligibility changed");return Optional.empty();}
            if(value.menu()==null&&o.nativeMenu()==null){menu=null;transition(State.OPEN_SB_MENU,tick);}
            else {if(!own(o)||!marker(o)){cancel("Fallback close ownership changed");return Optional.empty();}return issue(Kind.CLOSE,o,null,-1,"");}
        }
        if(state==State.OPEN_SB_MENU){if(!conserved(o)||value.menu()!=null||!o.cookieActive()){cancel("SkyBlock opener custody/eligibility changed");return Optional.empty();}return issue(Kind.COMMAND,o,null,-1,sale.protocol().fallback().skyblock().command());}
        if(state==State.WAIT_COOKIE&&own(o)&&value.ownCursorPrediction()&&value.menu()!=null&&value.menu().id()==lastId&&value.menu().revision()==lastRevision
                &&value.ledger().equals(initial.value().ledger())&&Objects.equals(value.coins(),initial.value().coins())&&o.cookieActive())return Optional.empty();
        if(state==State.WAIT_SB_MENU||state==State.WAIT_COOKIE) {
            if(!conserved(o)||!o.cookieActive()){cancel("Fallback ingress changed output/purse/cursor/cookie");return Optional.empty();}
            String expected=state==State.WAIT_SB_MENU?sale.protocol().fallback().skyblock().title():sale.protocol().fallback().cookieTitle();
            if(value.menu()==null)return Optional.empty();
            if(state==State.WAIT_COOKIE&&own(o)&&value.menu().id()==lastId&&value.menu().revision()==lastRevision)return Optional.empty();
            if(!value.menu().title().equals(expected)||o.nativeMenu()==null||state==State.WAIT_COOKIE&&own(o)&&value.menu().revision()==lastRevision){cancel("Unissued/replaced fallback menu");return Optional.empty();}
            menu=o.nativeMenu();
            if(state==State.WAIT_SB_MENU){transition(State.OPEN_COOKIE,tick);}
            else {
                var f=sale.protocol().fallback();var item=plan.inventory().getFirst();rule=new SellingRule(AutoSellMode.NPC,item.index(),item.registryId(),item.name(),item.components(),item.quantity(),f.unitProceeds(),f.quoteLabel());
                npc=new SellingRuntime();npc.start(value,rule,new SellingRuntime.Settings(AutoSellMode.NPC,f.cookieTitle(),"","","",0,0,f.receipt(),item.quantity(),rule.total(),sale.maxProceeds(),BigDecimal.ZERO,1,timeout),tick);
                if(npc.terminal()){fail("Cookie sale exact quote rejected: "+npc.reason());return Optional.empty();}transition(State.COOKIE_SALE,tick);
            }
        }
        if(state==State.OPEN_COOKIE) {
            if(!own(o)||!conserved(o)||!o.cookieActive()){cancel("SkyBlock cookie opener changed");return Optional.empty();}
            var button=sale.protocol().fallback().cookieButton();var matches=value.slots().values().stream().filter(s->s.inventoryIndex()<0&&s.count()==1&&s.registryId().equals(button.id())&&s.name().equals(button.name())&&s.components().equals(button.components())).toList();
            if(matches.size()!=1){fail("One exact full-component native cookie button required");return Optional.empty();}
            return issue(Kind.COOKIE_BUTTON,o,null,matches.getFirst().slot(),"");
        }
        if(state==State.BAZAAR) {
            boolean confirmation=bazaar.state()==BazaarCustodyRuntime.State.WAIT_CONFIRM&&value.menu()!=null&&value.menu().title().equals(plan.confirmTitle())
                    &&(value.menu().id()!=lastId||value.menu().revision()!=lastRevision);
            if(value.menu()!=null&&!own(o)&&!confirmation){cancel("Native Bazaar object replaced outside issued confirmation");return Optional.empty();}
            if(confirmation)menu=o.nativeMenu();
            var action=bazaar.tick(value,tick);if(action.isPresent())return issue(Kind.BAZAAR,o,action.get(),action.get().slot(),"");
            if(bazaar.state()==BazaarCustodyRuntime.State.CLOSING)proofMenu=menu;
            if(bazaar.state()==BazaarCustodyRuntime.State.COMPLETE){var p=bazaar.acknowledged().orElseThrow();proof=new Proof(AutoSellMode.BAZAAR,plan.maxQuantity(),p.proceeds(),proofMenu,p.menuId(),p.requestRevision(),p.receiptRevision(),p.matchedReceiptSerial(),initial.scope());state=State.COMPLETE;reason="Bazaar receipt, exact inventory/purse/revision and closed menu acknowledged";}
            else if(bazaar.terminal())fail(bazaar.status());
        } else if(state==State.COOKIE_SALE) {
            if(value.menu()!=null&&!own(o)||!o.cookieActive()){cancel("Cookie sale menu/eligibility changed");return Optional.empty();}
            var action=npc.tick(value,tick);if(action.isPresent())return issue(Kind.NPC,o,action.get(),action.get().slot(),"");
            if(npc.state()==SellingRuntime.State.CLOSING)proofMenu=menu;
            if(npc.state()==SellingRuntime.State.COMPLETE){var p=npc.acknowledgedSale().orElseThrow();var expected=new HashMap<>(initial.value().ledger());expected.remove(plan.inventory().getFirst().index());
                if(!value.cursorEmpty()||!value.ledger().equals(expected)||value.coins()==null||value.coins().compareTo(initial.value().coins().add(p.proceeds()))!=0){cancel("Cookie sale cleanup changed acknowledged custody/purse");return Optional.empty();}
                proof=new Proof(AutoSellMode.NPC,p.quantity(),p.proceeds(),proofMenu,p.menuId(),p.requestRevision(),p.receiptRevision(),p.matchedReceiptSerial(),initial.scope());state=State.COMPLETE;reason="Cookie sale and owned cleanup acknowledged";}
            else if(npc.terminal())fail(npc.reason());
        }
        return Optional.empty();
    }
    public boolean valid(Action a,Observation current) {
        if(a!=pending||!initial.scope().same(current.scope())||a.observed().nativeMenu()!=current.nativeMenu()||a.observed().selected()!=current.selected()||!a.observed().value().equals(current.value())||a.observed().cookieActive()!=current.cookieActive())return false;
        return switch(a.kind()){case BAZAAR->bazaar.valid((BazaarCustodyRuntime.Action)a.child(),current.value());case NPC->npc.valid((SellingRuntime.Action)a.child(),current.value());default->conserved(current)&&current.cookieActive();};
    }
    public void applied(Action a,boolean accepted,long tick) {
        if(a!=pending)return;pending=null;if(!accepted){fail("Exact guarded native sale-parent request refused");return;}
        switch(a.kind()) {
            case BAZAAR->{var child=(BazaarCustodyRuntime.Action)a.child();if(!child.close()){committed=true;lastId=child.observation().menu().id();lastRevision=child.observation().menu().revision();}bazaar.applied(child,true,tick);}
            case NPC->npc.applied((SellingRuntime.Action)a.child(),true,tick);
            case CLOSE->{}
            case COMMAND->transition(State.WAIT_SB_MENU,tick);
            case COOKIE_BUTTON->{lastId=a.observed().value().menu().id();lastRevision=a.observed().value().menu().revision();transition(State.WAIT_COOKIE,tick);}
        }
        deadline=tick+timeout;
    }
    public void onMessage(String line) {
        if(state==State.PREPARE_BAZAAR&&!committed&&sale.protocol().fallback()!=null&&line.replaceAll("§.","").strip().equals(sale.protocol().fallback().rejectionMessage()))rejection=true;
        if(state==State.BAZAAR)bazaar.onMessage(line);if(state==State.COOKIE_SALE)npc.onMessage(line);
    }
    public void cancel(String why){if(!terminal()){state=State.CANCELLED;pending=null;reason=why;}}
    private void fail(String why){state=State.FAILED;pending=null;reason=why;}
}
