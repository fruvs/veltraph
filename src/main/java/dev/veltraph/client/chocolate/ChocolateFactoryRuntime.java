package dev.veltraph.client.chocolate;

import java.math.BigDecimal;
import java.util.*;

/** One deliberate row selection per receipt. Any uncertain delivery/result latches until explicit reconciliation. */
public final class ChocolateFactoryRuntime {
    public enum State { IDLE, SELECTED, WAITING_RECEIPT, ACKNOWLEDGED, DENIED, UNKNOWN }
    public enum Delivery { SENT, DENIED, UNKNOWN }
    public interface Port {
        ChocolateFactoryObservation read(ChocolateFactoryProfile profile);
        /** Fresh native producers use the caller's acquisition boundary; legacy timestamps stay untouched. */
        default ChocolateFactoryObservation read(ChocolateFactoryProfile profile,long observationTime){return read(profile);}
        Delivery click(ChocolateFactoryObservation expected,int slot);
        /** Native port repeats this original-intent fence immediately before its input effect. */
        default Delivery click(ChocolateFactoryObservation expected,int slot,DispatchFence fence){return fence.current(expected,slot)?click(expected,slot):Delivery.DENIED;}
        default int currentHotbarSlot(){return -1;}
    }
    public record Selection(ChocolateFactoryObservation observation,ChocolateFactoryObservation.Quote quote,BigDecimal budget,BigDecimal reserve,int selectedHotbar,String intentId) {public Selection(ChocolateFactoryObservation o,ChocolateFactoryObservation.Quote q,BigDecimal b,BigDecimal r){this(o,q,b,r,-1,UUID.randomUUID().toString());}public Selection(ChocolateFactoryObservation o,ChocolateFactoryObservation.Quote q,BigDecimal b,BigDecimal r,int h){this(o,q,b,r,h,UUID.randomUUID().toString());}}
    /** Minted only by this runtime; its final read invokes no port, supplier or native codec. */
    public static final class DispatchFence {
        private final ChocolateFactoryRuntime runtime;private final Selection original;private final Thread thread;
        private DispatchFence(ChocolateFactoryRuntime runtime,Selection original){this.runtime=runtime;this.original=original;this.thread=Thread.currentThread();}
        public boolean current(){return current(original.observation(),original.quote().slot());}
        public boolean current(ChocolateFactoryObservation expected,int slot){return Thread.currentThread()==thread&&expected==original.observation()&&slot==original.quote().slot()&&runtime.dispatching&&runtime.state==State.WAITING_RECEIPT&&runtime.selected==original&&runtime.latestIntentId==original.intentId();}
    }
    public record AcknowledgedUpgrade(String id,Selection selection,ChocolateFactoryObservation receipt,long sequence) {}
    private final List<AcknowledgedUpgrade> acknowledgedUpgrades=new ArrayList<>();private long acknowledgedSequence;
    public List<AcknowledgedUpgrade> acknowledgedUpgrades(){return List.copyOf(acknowledgedUpgrades);}
    private final Port port;private State state=State.IDLE;private String reason="Select one observed upgrade";private Selection selected;private long sentAt;private String latestIntentId="";private BigDecimal spent=BigDecimal.ZERO,manualDeclaredDebit=BigDecimal.ZERO;private final ArrayDeque<ChocolateFactoryObservation.Quote> queue=new ArrayDeque<>();private ChocolateFactoryObservation lastReceipt;private ReceiptDiagnostics receiptDiagnostics;private boolean dispatching;
    public ChocolateFactoryRuntime(Port port){this.port=Objects.requireNonNull(port);}
    /** Bounded public menu facts/failed predicates only; no identities, registry data or account fields. */
    public record ReceiptDiagnostics(int beforeRevision,int revision,long elapsed,boolean layoutComplete,boolean contextBound,boolean cursorEmpty,boolean fresh,Integer level,BigDecimal balance,List<String> missing) {
        public ReceiptDiagnostics {missing=List.copyOf(missing);}
        public String summary(){return "revision="+beforeRevision+"->"+revision+",elapsed="+elapsed+"ms,complete="+layoutComplete+",bound="+contextBound+",cursorEmpty="+cursorEmpty+",fresh="+fresh+",level="+level+",balance="+balance+",missing="+missing;}
    }
    public ReceiptDiagnostics receiptDiagnostics(){return receiptDiagnostics;}
    public State state(){return state;}public String reason(){return reason;}public Selection selection(){return selected;}public BigDecimal spent(){return spent;}
    /** New non-queue ownership cannot expose acknowledgments from a previous finished queue run. */
    public void clearCompletedReceiptView(){if(state==State.WAITING_RECEIPT||state==State.UNKNOWN)throw new IllegalStateException("Reconcile queue first");cancel();acknowledgedUpgrades.clear();spent=BigDecimal.ZERO;manualDeclaredDebit=BigDecimal.ZERO;}
    public int queued(){return queue.size();}
    public BigDecimal manualDeclaredDebit(){return manualDeclaredDebit;}
    public long requestSentAtMillis(){return sentAt;}
    public String retainedQueueIntentId(){return latestIntentId;}
    record ManualClosureSnapshot(State state,String reason,Selection selection,BigDecimal spent,BigDecimal manual,List<AcknowledgedUpgrade> acknowledgments,List<ChocolateFactoryObservation.Quote> queued,String intentId) {}
    ManualClosureSnapshot manualClosureSnapshot(){return new ManualClosureSnapshot(state,reason,selected,spent,manualDeclaredDebit,List.copyOf(acknowledgedUpgrades),List.copyOf(queue),latestIntentId);}
    void restoreOwnedManualClosure(ManualClosureSnapshot snapshot){if(!Objects.equals(latestIntentId,snapshot.intentId())||selected!=null&&selected!=snapshot.selection())throw new IllegalStateException("Another intent replaced the owned closure; never overwrite its runtime");state=snapshot.state();reason=snapshot.reason();selected=snapshot.selection();spent=snapshot.spent();manualDeclaredDebit=snapshot.manual();acknowledgedUpgrades.clear();acknowledgedUpgrades.addAll(snapshot.acknowledgments());queue.clear();queue.addAll(snapshot.queued());}
    boolean matchesManualClosure(ChocolateQueueManualReconciliation.Plan plan){return !dispatching&&state==plan.state()&&selected==plan.selection()&&spent.compareTo(plan.knownSpent())==0&&manualDeclaredDebit.compareTo(plan.manualDebit())==0;}
    void closeManualReconciliation(ChocolateQueueManualReconciliation.Plan plan){if(!matchesManualClosure(plan))throw new IllegalStateException("Retained intent changed before durable manual closure");manualDeclaredDebit=plan.nextManualDebit();state=State.IDLE;selected=null;queue.clear();reason="Durably recorded manual declaration; known spend and native ACKs unchanged; manual Chocolate debit="+manualDeclaredDebit+"; fresh deliberate selection required";}
    void closeRecoveryReconciliation(ChocolateQueueRecovery.Plan plan){if(dispatching)throw new IllegalStateException("Queue native input still in progress; retain original pending intent");if(!ChocolateQueueRecovery.unchangedRuntime(this,plan))throw new IllegalStateException("Runtime ownership changed before recovery closure");spent=plan.knownSpent();manualDeclaredDebit=plan.manualDebit();state=State.IDLE;selected=null;queue.clear();reason="Durable explicit human recovery declaration; account identity unproven; no native ACK/financial receipt created; fresh deliberate selection required";}
    public void restoreSpent(BigDecimal value){if(state!=State.IDLE||value==null||value.signum()<0)throw new IllegalArgumentException("Invalid persisted spend");spent=value;}
    public boolean selectQueue(ChocolateFactoryProfile p,ChocolateFactoryObservation o,List<String> ids,BigDecimal budget,BigDecimal reserve,long now){return selectQueue(p,o,ids,budget,reserve,now,null);}
    boolean selectQueue(ChocolateFactoryProfile p,ChocolateFactoryObservation o,List<String> ids,BigDecimal budget,BigDecimal reserve,long now,ChocolateFactoryModule.RepeatPreparation preparation){
        if(dispatching||state==State.WAITING_RECEIPT||state==State.UNKNOWN)return false;
        if(ids==null||ids.isEmpty()||ids.size()>16||new HashSet<>(ids).size()!=ids.size())return deny("Select 1-16 distinct observed upgrades; future levels cannot be queued");
        var staged=new ArrayList<ChocolateFactoryObservation.Quote>();BigDecimal cost=BigDecimal.ZERO;
        for(String id:ids){var q=o==null?null:o.quotes().get(id);if(q==null||q.kind()!=ChocolateFactoryProfile.Kind.EMPLOYEE&&q.kind()!=ChocolateFactoryProfile.Kind.PRODUCTION&&q.kind()!=ChocolateFactoryProfile.Kind.CAPACITY)return deny("Queue includes unknown or consequential action");cost=cost.add(q.price());staged.add(q);}
        if(budget==null||cost.compareTo(budget)>0)return deny("Selected observed queue exceeds total budget");
        if(reserve==null||reserve.signum()<0||o.balance().isEmpty()||o.balance().get().subtract(cost).compareTo(reserve)<0)return deny("Selected observed queue exceeds protected reserve");
        if(preparation!=null){if(!preparation.current()||!select(p,o,ids.getFirst(),budget,reserve,now,null,preparation))return false;queue.clear();acknowledgedUpgrades.clear();for(int i=1;i<staged.size();i++)queue.addLast(staged.get(i));return true;}
        var previousSpent=spent;var previousManual=manualDeclaredDebit;spent=BigDecimal.ZERO;manualDeclaredDebit=BigDecimal.ZERO;if(!select(p,o,ids.getFirst(),budget,reserve,now)){spent=previousSpent;manualDeclaredDebit=previousManual;return false;}queue.clear();acknowledgedUpgrades.clear();for(int i=1;i<staged.size();i++)queue.addLast(staged.get(i));return true;
    }
    /** Advances only previously selected unchanged targets, from the exact receipt's fresh observation. */
    public boolean advanceQueue(ChocolateFactoryProfile p,long now){
        if(dispatching||state!=State.ACKNOWLEDGED||queue.isEmpty())return false;var wanted=queue.removeFirst();var o=lastReceipt;var fresh=o.quotes().get(wanted.id());
        if(fresh==null||fresh.slot()!=wanted.slot()||fresh.kind()!=wanted.kind()||fresh.level()!=wanted.level()||fresh.price().compareTo(wanted.price())!=0||!Objects.equals(fresh.fullIdentity(),wanted.fullIdentity())){queue.clear();return deny("Queued target quote/identity/level changed; select it again");}
        var policy=selected;return select(p,o,wanted.id(),policy.budget(),policy.reserve(),now);
    }
    public boolean select(ChocolateFactoryProfile p,ChocolateFactoryObservation o,String id,BigDecimal budget,BigDecimal reserve,long now){return select(p,o,id,budget,reserve,now,null);}
    boolean select(ChocolateFactoryProfile p,ChocolateFactoryObservation o,String id,BigDecimal budget,BigDecimal reserve,long now,ChocolateFactoryModule.RepeatTick episode){return select(p,o,id,budget,reserve,now,episode,null);}
    private boolean select(ChocolateFactoryProfile p,ChocolateFactoryObservation o,String id,BigDecimal budget,BigDecimal reserve,long now,ChocolateFactoryModule.RepeatTick episode,ChocolateFactoryModule.RepeatPreparation preparation){
        if(dispatching||state==State.WAITING_RECEIPT||state==State.UNKNOWN)return false;
        try{p.validate();}catch(RuntimeException e){return deny(e.getMessage());}
        if(o==null||!o.scope().equals(p.scope())||!o.title().equals(p.title())||!o.profileVerified()||!o.contextBound()||!o.cursorEmpty()||!o.fresh(now,p.freshnessMillis()))return deny("Fresh exact profile-bound menu and empty cursor required");
        if(o.balance().isEmpty()||o.production().isEmpty()||o.capacity().isEmpty()||o.balance().get().signum()<0||o.production().get().signum()<0||o.capacity().get().signum()<=0||o.balance().get().compareTo(o.capacity().get())>0)return deny("Known consistent balance, production and storage required");
        var q=o.quotes().get(id);if(q==null)return deny("Exact visible upgrade price/level unavailable");
        if(q.kind()!=ChocolateFactoryProfile.Kind.EMPLOYEE&&q.kind()!=ChocolateFactoryProfile.Kind.PRODUCTION&&q.kind()!=ChocolateFactoryProfile.Kind.CAPACITY)return deny("This consequential action requires manual native selection");
        if(budget==null||reserve==null||budget.signum()<0||reserve.signum()<0||q.price().signum()<=0||(preparation==null?spent.add(manualDeclaredDebit):BigDecimal.ZERO).add(q.price()).compareTo(budget)>0||o.balance().isEmpty()||o.balance().get().subtract(q.price()).compareTo(reserve)<0)return deny("Price exceeds selected budget or protected reserve");
        if(episode!=null&&!episode.acknowledgmentCurrent()||preparation!=null&&!preparation.current())return false;
        int hotbar=port.currentHotbarSlot();
        if(episode!=null&&!episode.acknowledgmentCurrent()||preparation!=null&&!preparation.current())return false;
        var proposal=new Selection(o,q,budget,reserve,hotbar);
        if(preparation!=null&&!preparation.current())return false;
        if(preparation!=null){spent=BigDecimal.ZERO;manualDeclaredDebit=BigDecimal.ZERO;}
        selected=proposal;latestIntentId=selected.intentId();sentAt=0;receiptDiagnostics=null;state=State.SELECTED;reason="Selected "+id+" at exact price "+q.price();return true;
    }
    public void tick(ChocolateFactoryProfile p,long now){
        if(dispatching)return;
        if(state==State.SELECTED){
            if(!selected.observation().fresh(now,p.freshnessMillis())||!selected.observation().scope().equals(p.scope())){deny("Selection expired or profile changed");return;}
            var sending=selected;state=State.WAITING_RECEIPT;sentAt=now;dispatching=true;
            Delivery delivery;
            try{delivery=port.click(sending.observation(),sending.quote().slot(),new DispatchFence(this,sending));}
            catch(RuntimeException e){delivery=Delivery.UNKNOWN;}
            finally{dispatching=false;}
            if(state!=State.WAITING_RECEIPT||selected!=sending||!Objects.equals(latestIntentId,sending.intentId())){unknown("Queue intent changed during native dispatch; do not retry");return;}
            if(delivery==Delivery.SENT){reason="Request sent once; waiting for observed upgrade receipt";}else if(delivery==Delivery.DENIED){deny("Native menu/session/revision/components changed before request");}else unknown("Input delivery is unknown; do not retry");
        }else if(state==State.WAITING_RECEIPT){
            // Fresh observations cannot extend this request's original receipt deadline.
            if(now<sentAt||now-sentAt>p.receiptTimeoutMillis()){unknown("Original Factory request deadline expired; do not retry or adopt a late receipt");return;}
            var o=port.read(p,now);var before=selected.observation();var q=selected.quote();
            receiptDiagnostics=receiptDiagnostics(before,q,o,p,now);
            var contextFailure=contextFailure(before,o);if(contextFailure!=null){unknown(contextFailure);return;}
            if(!protectedInventory(before,o)){unknown("Protected inventory/equipment changed before receipt");return;}
            // Vanilla predicts PICKUP locally before the server packet:the owned button/cursor can be
            // incomplete temporarily. Binding remains required; no action/spend/receipt is inferred.
            if(!o.profileVerified()){
                reason="Request sent once; waiting for complete menu/cursor receipt in the bound native context";
                if(now<sentAt||now-sentAt>p.receiptTimeoutMillis())unknown("Incomplete menu did not produce an unambiguous receipt before deadline; do not retry");return;
            }
            var after=o.targets().get(q.id());
            if(now-sentAt>=150&&o.fresh(now,p.freshnessMillis())&&o.cursorEmpty()&&protectedInventory(before,o)&&o.revision()!=before.revision()&&after!=null&&after.slot()==q.slot()&&after.kind()==q.kind()&&after.level()==q.level()+1&&!Objects.equals(after.fullIdentity(),q.fullIdentity())&&o.balance().isPresent()&&o.capacity().isPresent()&&o.production().isPresent()&&before.production().isPresent()){
                var production=o.production().get().max(before.production().get()).multiply(BigDecimal.valueOf(Math.max(0,now-sentAt))).divide(BigDecimal.valueOf(1000));
                var maximum=before.balance().get().subtract(q.price()).add(production).min(o.capacity().get());
                if(o.balance().get().signum()>=0&&o.balance().get().compareTo(before.balance().get().subtract(q.price()))>=0&&o.balance().get().compareTo(maximum)<=0&&o.balance().get().compareTo(selected.reserve())>=0){spent=spent.add(q.price());lastReceipt=o;if(acknowledgedUpgrades.size()>=64)acknowledgedUpgrades.removeFirst();acknowledgedUpgrades.add(new AcknowledgedUpgrade(UUID.randomUUID().toString(),selected,o,++acknowledgedSequence));state=State.ACKNOWLEDGED;reason="Observed level +1 and bounded balance receipt for "+q.id();return;}
            }
            if(now<sentAt||now-sentAt>p.receiptTimeoutMillis())unknown("No unambiguous upgrade receipt before deadline; do not retry | "+receiptDiagnostics.summary());
        }
    }
    private ReceiptDiagnostics receiptDiagnostics(ChocolateFactoryObservation before,ChocolateFactoryObservation.Quote q,ChocolateFactoryObservation o,ChocolateFactoryProfile p,long now){
        var missing=new ArrayList<String>();if(o==null){missing.add("OBSERVATION");return new ReceiptDiagnostics(before.revision(),-1,now-sentAt,false,false,false,false,null,null,missing);}
        if(contextFailure(before,o)!=null)missing.add("CONTEXT");if(!o.profileVerified())missing.add("COMPLETE_LAYOUT");if(now-sentAt<150)missing.add("MINIMUM_DELAY");if(!o.fresh(now,p.freshnessMillis()))missing.add("FRESH_OBSERVATION");if(!o.cursorEmpty())missing.add("EMPTY_CURSOR");if(!protectedInventory(before,o))missing.add("PROTECTED_INVENTORY");if(o.revision()==before.revision())missing.add("NEW_REVISION");
        var after=o.targets().get(q.id());if(after==null||after.slot()!=q.slot()||after.kind()!=q.kind()||after.level()!=q.level()+1)missing.add("EXPECTED_TARGET_LEVEL");if(after==null||Objects.equals(after.fullIdentity(),q.fullIdentity()))missing.add("CHANGED_TARGET_IDENTITY");
        if(o.balance().isEmpty()||o.capacity().isEmpty()||o.production().isEmpty()||before.production().isEmpty())missing.add("BALANCE_RATE_CAPACITY");
        else {var income=o.production().get().max(before.production().get()).multiply(BigDecimal.valueOf(Math.max(0,now-sentAt))).divide(BigDecimal.valueOf(1000));var lower=before.balance().orElseThrow().subtract(q.price());var upper=lower.add(income).min(o.capacity().get());var balance=o.balance().get();if(balance.signum()<0||balance.compareTo(lower)<0||balance.compareTo(upper)>0)missing.add("QUOTED_DEBIT_BOUNDS");if(balance.compareTo(selected.reserve())<0)missing.add("RESERVE");}
        return new ReceiptDiagnostics(before.revision(),o.revision(),now-sentAt,o.profileVerified(),o.contextBound(),o.cursorEmpty(),o.fresh(now,p.freshnessMillis()),after==null?null:after.level(),o.balance().orElse(null),missing);
    }
    private static String contextFailure(ChocolateFactoryObservation before,ChocolateFactoryObservation current){
        if(current==null)return "Native menu observation unavailable before receipt";
        if(current.session()!=before.session())return "Native player/world/connection generation changed before receipt";
        if(current.menu()!=before.menu())return "Native container changed before receipt";
        if(current.screen()!=before.screen())return "Native screen changed before receipt";
        if(!current.scope().equals(before.scope())||!current.title().equals(before.title()))return "Factory scope/title changed before receipt";
        if(!current.contextBound())return "Native scope binding or exact profile marker lost before receipt";
        return null;
    }
    private static boolean protectedInventory(ChocolateFactoryObservation before,ChocolateFactoryObservation after){
        var a=before.rows().values().stream().filter(ChocolateFactoryObservation.Row::playerInventory).toList();
        var b=after.rows().values().stream().filter(ChocolateFactoryObservation.Row::playerInventory).toList();
        return a.size()==b.size()&&a.stream().allMatch(r->Objects.equals(r,after.rows().get(r.slot())));
    }
    private boolean deny(String text){state=State.DENIED;reason=text;selected=null;queue.clear();return false;}
    private void unknown(String text){queue.clear();state=State.UNKNOWN;reason=text;}
    public void cancel(){queue.clear();if(state==State.WAITING_RECEIPT){unknown("Cancelled after request; result requires reconciliation");}else if(state!=State.UNKNOWN&&state!=State.ACKNOWLEDGED&&state!=State.DENIED){state=State.IDLE;selected=null;reason="Selection cancelled";}}
    /** Old inspection-free runtime primitive is retained only to fail closed; use the module transaction. */
    @Deprecated public void reconcileUnknown(boolean acknowledged){throw new IllegalStateException("Original-intent native inspection and durable manual transaction required");}
}
