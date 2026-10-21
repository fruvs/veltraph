package dev.veltraph.client.chocolate;

import com.google.gson.Gson;
import java.math.BigDecimal;
import java.util.*;
import dev.veltraph.client.chocolate.ChocolateSelectedTransactions.Currency;
import java.util.function.BooleanSupplier;
import static dev.veltraph.client.chocolate.ChocolateSelectedTransactions.*;

/** One explicitly selected quote under a cumulative per-scope denomination budget. No autonomous shop, claims or confirmations. */
public final class ChocolateSelectedTransactionRuntime {
    public enum State { IDLE, SELECTED, WAITING_PREVIEW, PREVIEW_READY, CONFIRM_SELECTED, WAITING_RECEIPT, ACKNOWLEDGED, DENIED, UNKNOWN }
    public interface Port {
        ChocolateFactoryObservation read(ChocolateFactoryProfile p,long now);
        ChocolateFactoryRuntime.Delivery click(ChocolateFactoryObservation o,int slot,int button,BooleanSupplier authority);
        int currentHotbarSlot();
        /** Native adapter returns null only for a privately attested owned pending chain; unavailable/default null is a refusal. */
        default ChocolateFactoryObservation readSelectedReceipt(ChocolateFactoryProfile p,long now){var value=read(p,now);if(value==null)throw new IllegalStateException("Selected receipt source unavailable");return value;}
        default ChocolateFactoryRuntime.Delivery selectedClick(ChocolateFactoryObservation o,int slot,int button,Binding binding,BooleanSupplier authority){return click(o,slot,button,authority);}
        default ChocolateFactoryRuntime.Delivery selectedClick(ChocolateFactoryObservation o,int slot,int button,Binding binding,BooleanSupplier authority,DispatchFence fence){return selectedClick(o,slot,button,binding,()->fence.current(o,slot,button,binding)&&authority.getAsBoolean()&&fence.current(o,slot,button,binding));}
        default void closeSelectedReceiptSource(){}

    }
    /** Resource and optional exact output expectation must come from a deliberate current native binding. */
    public record Binding(Currency currency,ChocolateFactoryProfile.Field resource,int outputSlot,Object outputBeforeIdentity,int outputBeforeCount,Object outputIdentity) {}
    /** Captured price/progress/stock controls can never be re-labelled as spendable funding. */
    public static boolean independentResource(Binding b,ChocolateFactoryObservation o){
        if(b==null||b.currency()==null||b.currency()==Currency.NONE||b.resource()==null||b.resource().marker()==null||b.resource().prefix()==null||b.resource().prefix().isBlank())return false;
        int slot=b.resource().marker().slot();if(Set.of(4,11,15,48,49,50).contains(slot)||o.title().equals(title(Action.SHOP))&&SHOP_SLOTS.contains(slot)||o.title().equals(title(Action.HITMAN))&&HITMAN_SLOTS.contains(slot))return false;
        var row=o.rows().get(slot);return row!=null&&!row.playerInventory()&&row.count()==1&&!row.lore().stream().anyMatch(l->l.equals("Cost")||l.startsWith("Chocolate Spent:")||l.startsWith("Available eggs:")||l.startsWith("Purchased slots:")||l.startsWith("Annual Stock")||l.endsWith(" remaining")||l.startsWith("Click to ")||l.startsWith("Requires "))&&b.resource().read(o.rows()).isPresent();
    }
    /** Original selected effect only; final reads invoke no authority supplier, port or codec. */
    public static final class DispatchFence {
        private final ChocolateSelectedTransactionRuntime runtime;private final String intent;private final State waiting;private final Quote quote;private final Binding binding;
        private final ChocolateFactoryObservation expected;private final int slot,button;private final Thread thread;
        private DispatchFence(ChocolateSelectedTransactionRuntime runtime,String intent,State waiting,ChocolateFactoryObservation expected,int slot,int button){this.runtime=runtime;this.intent=intent;this.waiting=waiting;this.quote=runtime.quote;this.binding=runtime.binding;this.expected=expected;this.slot=slot;this.button=button;this.thread=Thread.currentThread();}
        public boolean current(ChocolateFactoryObservation observation,int slot,int button,Binding binding){return Thread.currentThread()==thread&&observation==expected&&slot==this.slot&&button==this.button&&binding==this.binding&&runtime.dispatching&&runtime.state==waiting&&runtime.id==intent&&runtime.quote==quote&&runtime.binding==this.binding;}
    }
    public record Receipt(String scope,String intent,Action action,Currency currency,String cost,String outcome) {}
    /** Bounded cached diagnostic; null means an operand was skipped by the original short circuit. No native re-query or component serialization. */
    public record ProtectedRowOperand(int slot,boolean rowEqual,Boolean absentAfter,Boolean countMismatch,Boolean expectedOutputIdentityEqual) {}
    public record ReceiptGuardTrace(String phase,String intent,String action,Map<String,Boolean> contextOperands,Integer expectedHotbar,Integer observedHotbar,Boolean sameSession,Boolean protectedInventoryAccepted,List<ProtectedRowOperand> protectedRows,Long observedProtectedCount,Long expectedProtectedCount) {
        public ReceiptGuardTrace {contextOperands=Collections.unmodifiableMap(new LinkedHashMap<>(contextOperands));protectedRows=List.copyOf(protectedRows);}
    }
    private Map<String,Boolean> contextOperands=new LinkedHashMap<>();private Integer contextHotbar;
    private List<ProtectedRowOperand> protectedOperands=new ArrayList<>();private Long observedProtectedCount,expectedProtectedCount;
    private ReceiptGuardTrace lastReceiptGuardTrace;
    public Optional<ReceiptGuardTrace> lastReceiptGuardTrace(){return Optional.ofNullable(lastReceiptGuardTrace);}
    private boolean contextOperand(String name,boolean value){contextOperands.put(name,value);return value;}
    private boolean contextHotbarOperand(int actual){contextHotbar=actual;return contextOperand("hotbarMatches",actual==hotbar);}
    private boolean guardedRowEquals(ChocolateFactoryObservation.Row beforeRow,ChocolateFactoryObservation.Row afterRow,boolean allowOutput){
        boolean equal=Objects.equals(beforeRow,afterRow);if(!allowOutput)protectedOperands.add(new ProtectedRowOperand(beforeRow.slot(),equal,null,null,null));return equal;
    }
    private void cacheReceiptGuard(String phase,Boolean sameSession,Boolean inventoryAccepted){lastReceiptGuardTrace=new ReceiptGuardTrace(phase,id,quote.action().name(),contextOperands,hotbar,contextHotbar,sameSession,inventoryAccepted,protectedOperands,observedProtectedCount,expectedProtectedCount);}
    private final Port port;private State state=State.IDLE;private String id,reason="No selected transaction",evidence="";private Quote quote;private ChocolateFactoryObservation before,preview;private Binding binding;private BigDecimal resource,budget,reserve,spent;private int hotbar;private long sentAt;private Receipt receipt;private boolean dispatching;
    public ChocolateSelectedTransactionRuntime(Port port){this.port=Objects.requireNonNull(port);}
    public State state(){return state;}public String id(){return id;}public Quote quote(){return quote;}public String reason(){return reason;}public Receipt receipt(){return receipt;}
    public boolean hasIntent(){return dispatching||Set.of(State.SELECTED,State.WAITING_PREVIEW,State.PREVIEW_READY,State.CONFIRM_SELECTED,State.WAITING_RECEIPT,State.UNKNOWN).contains(state);}
    public static BigDecimal knownSpend(ChocolateFactoryHistory history,String scope,Currency currency){
        BigDecimal total=BigDecimal.ZERO;for(var j:history.actions().values())if(j.scope().equals(scope)&&j.action().equals(currency==Currency.COINS?"SELECTED_HITMAN":currency==Currency.CHOCOLATE?"SELECTED_SHOP":"NONE")&&j.status().equals("RECONCILED")&&j.reason().startsWith("User confirmed native action applied;"))throw new IllegalArgumentException("Manually applied selected result has unknown native financial debit; cumulative paid budget remains unbound");for(var j:history.actions().values())if(j.scope().equals(scope)&&j.status().equals("ACKNOWLEDGED")&&j.action().startsWith("SELECTED_")){
            var r=new Gson().fromJson(j.result(),Receipt.class);if(r==null||!r.scope().equals(scope)||!r.intent().equals(j.id())||!j.action().equals("SELECTED_"+r.action())||r.currency()==null||r.cost()==null)throw new IllegalArgumentException("Selected receipt budget evidence is malformed");var cost=new BigDecimal(r.cost());if(cost.signum()<0)throw new IllegalArgumentException("Negative receipt cost");if(r.currency()==currency)total=total.add(cost);
        }return total;
    }
    private static boolean protectedComplete(ChocolateFactoryObservation o){return java.util.stream.IntStream.range(10000,10036).allMatch(i->o.rows().containsKey(i)&&o.rows().get(i).playerInventory())&&o.rows().containsKey(1000);}
    private static boolean exact(ChocolateFactoryObservation a,ChocolateFactoryObservation b){return a!=null&&b!=null&&a.session()==b.session()&&a.menu()==b.menu()&&a.screen()==b.screen()&&a.revision()==b.revision()&&a.title().equals(b.title())&&a.rows().equals(b.rows())&&a.scope().equals(b.scope());}
    private boolean context(ChocolateFactoryProfile p,ChocolateFactoryObservation o,long now){
        contextOperands=new LinkedHashMap<>();contextHotbar=null;
        return contextOperand("observationPresent",o!=null)&&contextOperand("contextBound",o.contextBound())&&contextOperand("scopeMatches",o.scope().equals(p.scope()))&&contextOperand("cursorEmpty",o.cursorEmpty())&&contextOperand("fresh",o.fresh(now,p.freshnessMillis()))&&contextOperand("menuComplete",complete(o))&&contextOperand("protectedComplete",protectedComplete(o))&&contextHotbarOperand(port.currentHotbarSlot());
    }
    public boolean select(ChocolateFactoryProfile p,ChocolateFactoryObservation o,Action action,int slot,Binding bound,BigDecimal max,BigDecimal keep,BigDecimal alreadySpent,long now){
        if(hasIntent())return false;p.validate();hotbar=port.currentHotbarSlot();if(!p.usesCapturedLayout()||hotbar<0||!context(p,o,now))return deny("Fresh complete scope-bound native menu, inventory and empty cursor required");
        var q=ChocolateSelectedTransactions.quote(action,o,slot);if(q.isEmpty())return deny("Exact purchasable/claimable captured transaction grammar required; locked/extra-item costs remain unbound");
        if(max==null||keep==null||alreadySpent==null||max.signum()<0||keep.signum()<0||alreadySpent.signum()<0||alreadySpent.add(q.get().cost()).compareTo(max)>0)return deny("Cumulative same-scope denomination budget exceeded");
        var balance=bound==null||bound.resource()==null?Optional.<BigDecimal>empty():bound.resource().read(o.rows());
        if(action!=Action.MILESTONE&&(bound==null||bound.currency()!=q.get().currency()||!independentResource(bound,o)||balance.isEmpty()||balance.get().subtract(keep).compareTo(q.get().cost())<0))return deny("Independent exact native resource/funding field missing or selected reserve violated; captured menu counters are not funds");
        if(action==Action.SHOP&&(bound==null||bound.outputSlot()<10009||bound.outputSlot()>10035||bound.outputIdentity()==null||bound.outputBeforeIdentity()==null||o.rows().get(bound.outputSlot()).count()!=bound.outputBeforeCount()||!Objects.equals(o.rows().get(bound.outputSlot()).fullIdentity(),bound.outputBeforeIdentity())))return deny("Shop requires deliberate exact existing inventory exemplar and one-item output expectation; live delivery remains unbound");
        lastReceiptGuardTrace=null;before=o;quote=q.get();binding=bound;resource=balance.orElse(BigDecimal.ZERO);budget=max;reserve=keep;spent=alreadySpent;receipt=null;preview=null;id=UUID.randomUUID().toString();state=State.SELECTED;
        evidence="selected="+new Gson().toJson(quote)+"; scope="+p.scope()+"; resource="+resource+"; cumulativeKnownSpend="+spent+"; maximum="+budget+"; reserve="+reserve+"; outputSlot="+(bound==null?-1:bound.outputSlot())+"; account identity UNPROVEN";reason="Exact transaction selected; start through Root guard";return true;
    }
    public boolean ready(ChocolateFactoryProfile p,long now){return state==State.SELECTED&&before.fresh(now,p.freshnessMillis())&&context(p,port.read(p,now),now)&&exact(before,port.read(p,now));}
    public boolean confirm(ChocolateFactoryProfile p,long now){if(state!=State.PREVIEW_READY||quote.action()!=Action.HITMAN)return false;ChocolateFactoryObservation o;try{o=port.readSelectedReceipt(p,now);}catch(RuntimeException refused){unknown("Original Hitman source invalid before explicit confirmation: "+refused.getMessage());return false;}if(o==null)return false;if(!context(p,o,now)||!exact(preview,o)||!ChocolateSelectedTransactions.preview(o)||now<sentAt||now-sentAt>p.receiptTimeoutMillis()||!independentResource(binding,o)||binding.resource().read(o.rows()).filter(v->v.compareTo(resource)==0).isEmpty())return deny("Exact retained parent quote, fresh preview and independently bound unchanged Coins required");preview=o;state=State.CONFIRM_SELECTED;reason="Explicitly confirmed retained Hitman slot quote; one spending request selected";return true;}
    public void tick(ChocolateFactoryProfile p,long now,BooleanSupplier authority){
        if(dispatching)return;
        if(state==State.SELECTED||state==State.CONFIRM_SELECTED){
            var phase=state;var sendingIntent=id;var expected=phase==State.SELECTED?before:preview;var current=port.read(p,now);if(!authority.getAsBoolean()||state!=phase||!Objects.equals(id,sendingIntent)||!expected.fresh(now,p.freshnessMillis())||!context(p,current,now)||!exact(expected,current)){if(state!=phase||!Objects.equals(id,sendingIntent))return;deny("Selection/config/native inventory or menu changed before input");return;}if(state!=phase||!Objects.equals(id,sendingIntent))return;
            var outcome=ChocolateFactoryRuntime.Delivery.UNKNOWN;var dispatchState=phase==State.SELECTED&&quote.action()==Action.HITMAN?State.WAITING_PREVIEW:State.WAITING_RECEIPT;state=dispatchState;sentAt=now;
            // Latch waiting before effect: reentrant callbacks cannot mint a second request.
            dispatching=true;
            try{int slot=phase==State.CONFIRM_SELECTED?11:quote.slot();outcome=port.selectedClick(expected,slot,0,binding,()->state==dispatchState&&Objects.equals(id,sendingIntent)&&authority.getAsBoolean()&&context(p,port.read(p,now),now)&&exact(expected,port.read(p,now))&&state==dispatchState&&Objects.equals(id,sendingIntent),new DispatchFence(this,sendingIntent,dispatchState,expected,slot,0));}catch(RuntimeException failure){}
            finally{dispatching=false;}
            if(outcome==ChocolateFactoryRuntime.Delivery.DENIED){if(state!=dispatchState)return;deny("Final effect authority refused; no native request");return;}if(outcome!=ChocolateFactoryRuntime.Delivery.SENT){unknown("Selected transaction delivery unknown; never retry");return;}if(state!=dispatchState){unknown("Authority changed during native dispatch; reconcile original intent");return;}reason="One native request dispatched; waiting for semantic receipt";return;
        }
        if(state!=State.WAITING_PREVIEW&&state!=State.WAITING_RECEIPT&&state!=State.PREVIEW_READY)return;
        if(!authority.getAsBoolean()||now<sentAt||now-sentAt>p.receiptTimeoutMillis()){unknown("Original selected transaction deadline/config authority ended; never retry");return;}
        ChocolateFactoryObservation o;try{o=port.readSelectedReceipt(p,now);}catch(RuntimeException refused){unknown("Owned native receipt provenance refused: "+refused.getMessage());return;}
        if(o==null){reason="One owned native input remains pending in its exact predicted/ordered applied state; full native receipt required";return;}
        protectedOperands=new ArrayList<>();observedProtectedCount=null;expectedProtectedCount=null;
        Boolean sameSession=null,inventoryAccepted=null;String originalPhase=state.name();
        if(!context(p,o,now)||(sameSession=o.session()==before.session())==false||(inventoryAccepted=protectedInventory(o,state==State.WAITING_RECEIPT&&quote.action()==Action.SHOP))==false){
            cacheReceiptGuard(originalPhase,sameSession,inventoryAccepted);unknown("Native session/cursor/hotbar/protected inventory changed after request");return;
        }
        cacheReceiptGuard(originalPhase,sameSession,inventoryAccepted);
        if(now-sentAt<150)return;
        if(state==State.WAITING_PREVIEW){if(o.menu()==before.menu()){if(o.screen()!=before.screen()||!o.title().equals(before.title()))unknown("Hitman parent context changed");return;}if(o.screen()==before.screen()||!ChocolateSelectedTransactions.preview(o)||!independentResource(binding,o)||binding.resource().read(o.rows()).filter(v->v.compareTo(resource)==0).isEmpty()){unknown("Generic confirmation has no retained exact resource context");return;}preview=o;state=State.PREVIEW_READY;reason="Fresh generic preview bound to original priced Hitman slot; explicit transaction-confirm required";return;}
        if(state==State.PREVIEW_READY){if(!exact(preview,o))unknown("Selected Hitman preview changed before deliberate confirmation");return;}
        if(quote.action()==Action.HITMAN){if(o.menu()==preview.menu())return;if(o.menu()==before.menu()||o.screen()==preview.screen()){unknown("Hitman result must be a newly received parent menu");return;}}
        else if(o.menu()!=before.menu()||o.screen()!=before.screen()){unknown("Selected transaction menu replaced before result");return;}
        if(o.revision()==(quote.action()==Action.HITMAN?preview.revision():before.revision())&&quote.action()!=Action.HITMAN)return;
        if(ChocolateSelectedTransactions.receipt(quote,before,o)&&fundsReceipt(o)&&protectedInventory(o,quote.action()==Action.SHOP)&&outputReceipt(o)){
            receipt=new Receipt(before.scope(),id,quote.action(),quote.currency(),quote.cost().toPlainString(),quote.action()==Action.MILESTONE?"Exact XVI reward row became UNLOCKED; no Coin/item delivery inferred":quote.action()==Action.HITMAN?"Exact selected slot READY and count +1 with native Coins debit":"Exact stock/spent counters, resource debit and exact inventory item +1");state=State.ACKNOWLEDGED;reason=receipt.outcome();
        }
    }
    private boolean outputReceipt(ChocolateFactoryObservation o){if(quote.action()!=Action.SHOP)return true;var a=before.rows().get(binding.outputSlot());var b=o.rows().get(binding.outputSlot());return b!=null&&b.count()==a.count()+1&&Objects.equals(b.fullIdentity(),binding.outputIdentity());}
    private boolean fundsReceipt(ChocolateFactoryObservation o){return quote.currency()==Currency.NONE||independentResource(binding,o)&&binding.resource().read(o.rows()).filter(v->v.compareTo(resource.subtract(quote.cost()))==0&&v.compareTo(reserve)>=0).isPresent();}
    private boolean protectedInventory(ChocolateFactoryObservation o,boolean allowOutput){
        protectedOperands=new ArrayList<>();observedProtectedCount=null;expectedProtectedCount=null;
        for(var r:before.rows().values())if(r.playerInventory()&&r.slot()>=1000){var after=o.rows().get(r.slot());
            if(allowOutput&&r.slot()==binding.outputSlot()){
                boolean equal=guardedRowEquals(r,after,true);Boolean absent=null,countMismatch=null,outputEqual=null;
                boolean refused=!equal&&((absent=after==null)||(countMismatch=after.count()!=r.count()+1)||!(outputEqual=Objects.equals(after.fullIdentity(),binding.outputIdentity())));
                protectedOperands.add(new ProtectedRowOperand(r.slot(),equal,absent,countMismatch,outputEqual));if(refused)return false;
            }else if(!guardedRowEquals(r,after,false))return false;
        }
        observedProtectedCount=o.rows().values().stream().filter(r->r.playerInventory()&&r.slot()>=1000).count();
        expectedProtectedCount=before.rows().values().stream().filter(r->r.playerInventory()&&r.slot()>=1000).count();
        return observedProtectedCount.longValue()==expectedProtectedCount.longValue();
    }
    private boolean deny(String text){state=State.DENIED;reason=text;return false;}private void unknown(String text){port.closeSelectedReceiptSource();state=State.UNKNOWN;reason=text;}
    public void cancel(){port.closeSelectedReceiptSource();if(Set.of(State.WAITING_PREVIEW,State.PREVIEW_READY,State.CONFIRM_SELECTED,State.WAITING_RECEIPT).contains(state))unknown("Stopped after selected native request; explicit original intent reconciliation required");else if(state!=State.UNKNOWN&&state!=State.ACKNOWLEDGED){state=State.IDLE;reason="Selected transaction cancelled before request";}}
    public void persistenceFailed(){unknown("Selected transaction journal durability unknown; explicit reconciliation required");}
    boolean dispatchInProgress(){return dispatching;}
    public void reconcile(){if(dispatching)throw new IllegalStateException("Selected native input still in progress; retain original pending intent");if(state==State.UNKNOWN){state=State.IDLE;reason="User manually reconciled original selected transaction; fresh binding required";}}
    public ChocolateActionJournal journal(String status){return new ChocolateActionJournal(1,id,before.scope(),"SELECTED_"+quote.action(),status,state.name(),evidence,reason,receipt==null?"":new Gson().toJson(receipt));}
}
