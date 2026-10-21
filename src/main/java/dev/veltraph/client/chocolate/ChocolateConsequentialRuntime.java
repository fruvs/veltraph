package dev.veltraph.client.chocolate;

import java.math.BigDecimal;
import java.util.*;

/** One deliberately selected captured action; preview never authorizes its destructive confirmation. */
public final class ChocolateConsequentialRuntime {
    public enum Action { TOWER, PRESTIGE }
    public enum State { IDLE, SELECTED, WAITING_TOWER, WAITING_PREVIEW, PREVIEW_READY, CONFIRM_SELECTED, WAITING_RESET, ACKNOWLEDGED, DENIED, UNKNOWN }
    public interface Port {
        ChocolateFactoryObservation read(ChocolateFactoryProfile p,long now);
        ChocolateFactoryRuntime.Delivery click(ChocolateFactoryObservation o,int slot,int button);
        /** Native ports recheck this intent fence immediately before their irreversible effect. */
        default ChocolateFactoryRuntime.Delivery click(ChocolateFactoryObservation o,int slot,int button,java.util.function.BooleanSupplier authority){
            return authority.getAsBoolean()?click(o,slot,button):ChocolateFactoryRuntime.Delivery.DENIED;
        }
        default ChocolateFactoryRuntime.Delivery click(ChocolateFactoryObservation o,int slot,int button,DispatchFence fence){
            return fence.current(o,slot,button)?click(o,slot,button):ChocolateFactoryRuntime.Delivery.DENIED;
        }
    }
    /** Concrete original dispatch authority; final reads invoke no port, supplier or native codec. */
    public static final class DispatchFence {
        private final ChocolateConsequentialRuntime runtime;private final String intent;private final State waiting;private final Action action;
        private final ChocolateFactoryObservation expected;private final int slot,button;private final Thread thread;
        private DispatchFence(ChocolateConsequentialRuntime runtime,String intent,State waiting,ChocolateFactoryObservation expected,int slot,int button){this.runtime=runtime;this.intent=intent;this.waiting=waiting;this.action=runtime.action;this.expected=expected;this.slot=slot;this.button=button;this.thread=Thread.currentThread();}
        public boolean current(){return current(expected,slot,button);}
        public boolean current(ChocolateFactoryObservation observation,int slot,int button){return Thread.currentThread()==thread&&observation==expected&&slot==this.slot&&button==this.button&&runtime.dispatching&&runtime.state==waiting&&runtime.id==intent&&runtime.action==action;}
    }
    private final Port port;private State state=State.IDLE;private Action action;private String id,reason="No consequential action selected",evidence="";private ChocolateFactoryObservation parent,preview,receipt;private long sentAt;private BigDecimal maximumResetWallet;private boolean dispatching;
    public ChocolateConsequentialRuntime(Port port){this.port=Objects.requireNonNull(port);}
    public State state(){return state;}public Action action(){return action;}public String id(){return id;}public String reason(){return reason;}public ChocolateFactoryObservation receipt(){return receipt;}
    public boolean hasIntent(){return dispatching||switch(state){case SELECTED,WAITING_TOWER,WAITING_PREVIEW,PREVIEW_READY,CONFIRM_SELECTED,WAITING_RESET,UNKNOWN->true;default->false;};}
    private boolean base(ChocolateFactoryProfile p,ChocolateFactoryObservation o,long now){if(hasIntent())return false;try{p.validate();}catch(RuntimeException invalid){return deny(invalid.getMessage());}if(!p.usesCapturedLayout()||o==null||!o.profileVerified()||!o.contextBound()||!o.scope().equals(p.scope())||!o.title().equals(p.title())||!o.cursorEmpty()||!o.fresh(now,p.freshnessMillis())||!java.util.stream.IntStream.range(10000,10036).allMatch(i->o.rows().containsKey(i)&&o.rows().get(i).playerInventory())||!o.rows().containsKey(1000))return deny("Fresh complete explicitly bound captured Factory menu and empty cursor required");return true;}
    public boolean selectTower(ChocolateFactoryProfile p,ChocolateFactoryObservation o,long now){if(!base(p,o,now))return false;if(!ChocolateConsequentialParser.towerReady(o))return deny("Only observed inactive XV Tower with known charge, +1.5x/1h and exact right-click cue is supported");select(Action.TOWER,o);var t=ChocolateConsequentialParser.tower(o).orElseThrow();evidence="Tower XV; charges="+t.charges().orElseThrow()+"/3; selected debit=1 charge; boost=1.5x; duration=3600s; no future recharge selected";return true;}
    public boolean selectPrestige(ChocolateFactoryProfile p,ChocolateFactoryObservation o,BigDecimal maximumWallet,long now){if(!base(p,o,now))return false;if(!ChocolateConsequentialParser.eligiblePrestige(o)||maximumWallet==null||maximumWallet.signum()<0||o.balance().isEmpty()||o.balance().get().compareTo(maximumWallet)>0)return deny("Explicit eligible captured III-to-IV preview and selected maximum observed parent wallet required; threshold is not inferred");select(Action.PRESTIGE,o);maximumResetWallet=maximumWallet;evidence="Selected III-to-IV preview only; wallet="+o.balance().orElseThrow()+"; maximum observed parent wallet="+maximumWallet+"; earned="+o.prestigeProgress()+"; all-time="+CapturedChocolateFactoryParser.parse(o.title(),o.rows()).allTime()+"; captured retained Barn/Collection/Realm terms; preview does not show current wallet; separate explicit reset-all confirmation required";return true;}
    private void select(Action type,ChocolateFactoryObservation o){action=type;id=UUID.randomUUID().toString();parent=o;preview=null;receipt=null;state=State.SELECTED;reason=type==Action.TOWER?"Inactive exact Tower activation selected; start deliberately":"Eligible III-to-IV preview selected; start opens preview only";}
    public boolean ready(ChocolateFactoryProfile p,long now){if(state!=State.SELECTED)return false;var o=port.read(p,now);return exact(parent,o)&&parent.fresh(now,p.freshnessMillis())&&o.fresh(now,p.freshnessMillis())&&o.profileVerified()&&o.contextBound()&&o.cursorEmpty();}
    /** Called solely by the separate user confirmation command; no tick calls this method. */
    public boolean confirmPrestige(ChocolateFactoryProfile p,long now){if(action!=Action.PRESTIGE||state!=State.PREVIEW_READY)return false;var o=port.read(p,now);if(!exact(preview,o)||!o.fresh(now,p.freshnessMillis())||!o.contextBound()||!o.cursorEmpty()||!ChocolateConsequentialParser.preview(o))return deny("Preview identity/context changed; inspect and select again");preview=o;state=State.CONFIRM_SELECTED;reason="User explicitly confirmed captured III-to-IV reset; one request selected";return true;}
    public void tick(ChocolateFactoryProfile p,long now){
        // A callback during native input may stop this intent, but cannot dispatch or consume a receipt.
        if(dispatching)return;
        if(state==State.SELECTED||state==State.CONFIRM_SELECTED){
            var expected=state==State.SELECTED?parent:preview;
            if(!expected.fresh(now,p.freshnessMillis())||!expected.scope().equals(p.scope())){deny("Consequential selection expired or profile changed");return;}
            var prior=state;var sendingIntent=id;
            var waiting=prior==State.CONFIRM_SELECTED?State.WAITING_RESET:action==Action.TOWER?State.WAITING_TOWER:State.WAITING_PREVIEW;
            // Latch before crossing the port boundary so a reentrant stop remains an unresolved request.
            state=waiting;sentAt=now;dispatching=true;
            ChocolateFactoryRuntime.Delivery result;
            try{int slot=prior==State.CONFIRM_SELECTED?11:action==Action.TOWER?38:27;int button=action==Action.TOWER?1:0;result=port.click(expected,slot,button,new DispatchFence(this,sendingIntent,waiting,expected,slot,button));}
            catch(RuntimeException failure){result=ChocolateFactoryRuntime.Delivery.UNKNOWN;}
            finally{dispatching=false;}
            if(state!=waiting||!Objects.equals(id,sendingIntent)){unknown("Consequential intent changed during native dispatch; never retry");return;}
            if(result==ChocolateFactoryRuntime.Delivery.DENIED){deny("Exact native identity changed before action; no request issued");return;}
            if(result!=ChocolateFactoryRuntime.Delivery.SENT){unknown("Consequential delivery unknown; never retry");return;}
            reason="One request dispatched; waiting for fresh semantic result";return;
        }
        if(state!=State.WAITING_TOWER&&state!=State.WAITING_PREVIEW&&state!=State.WAITING_RESET)return;
        // Fresh native packets cannot extend the original dispatch deadline in any waiting phase.
        if(now<sentAt||now-sentAt>p.receiptTimeoutMillis()){unknown("Original consequential dispatch deadline expired; never retry or adopt a late receipt");return;}
        ChocolateFactoryObservation o;try{o=port.read(p,now);}catch(RuntimeException failure){unknown("Native consequential result observation failed; never retry");return;}
        if(o==null||o.session()!=parent.session()||!o.scope().equals(parent.scope())||!o.contextBound()||!protectedInventory(parent,o)){unknown("Consequential native session/scope/protected inventory changed; never retry");return;}
        boolean fresh=now>=sentAt&&now-sentAt>=150&&o.fresh(now,p.freshnessMillis())&&o.cursorEmpty();
        if(state==State.WAITING_TOWER){if(o.menu()!=parent.menu()||o.screen()!=parent.screen()||!o.title().equals(parent.title())){unknown("Tower native menu changed before result");return;}if(fresh&&o.profileVerified()&&o.revision()!=parent.revision()&&towerReceipt(o,now)){ack(o,"Observed one Tower charge consumed, fresh ACTIVE duration and matching boost/composition");return;}}
        else if(state==State.WAITING_PREVIEW){if(o.menu()!=parent.menu()){if(!o.title().equals(ChocolateConsequentialParser.PREVIEW)){unknown("Unexpected menu while awaiting captured prestige preview");return;}if(fresh&&o.screen()!=parent.screen()&&ChocolateConsequentialParser.preview(o)){preview=o;state=State.PREVIEW_READY;reason="Exact III-to-IV preview observed; no reset request is authorized until explicit prestige-confirm";return;}}else if(!o.title().equals(parent.title())){unknown("Prestige parent title drifted");return;}}
        else if(state==State.WAITING_RESET){if(o.menu()!=preview.menu()){if(!o.title().equals(parent.title())){unknown("Unexpected menu while awaiting prestige reset");return;}if(fresh&&o.profileVerified()&&o.menu()!=parent.menu()&&o.screen()!=preview.screen()&&ChocolateConsequentialParser.reset(parent,o)){ack(o,"Observed new Factory IV, zero wallet/earned progress, unchanged all-time and captured persistent Barn/Collection/Realm fields");return;}}else if(!o.title().equals(preview.title())){unknown("Prestige confirmation context drifted");return;}}
    }
    private boolean towerReceipt(ChocolateFactoryObservation o,long now){var before=ChocolateConsequentialParser.tower(parent).orElseThrow();var after=ChocolateConsequentialParser.tower(o);if(after.isEmpty())return false;var t=after.get();long duration=t.activeRemainingSeconds().orElse(-1),lower=Math.max(1,3600-(now-sentAt)/1000-2);if(!t.status().equals("ACTIVE")||t.charges().orElse(-1)!=before.charges().orElseThrow()-1||duration<lower||duration>3600||Objects.equals(parent.rows().get(38).fullIdentity(),o.rows().get(38).fullIdentity()))return false;var a=CapturedChocolateFactoryParser.parse(parent.title(),parent.rows()).breakdown();var b=CapturedChocolateFactoryParser.parse(o.title(),o.rows()).breakdown();return a.isPresent()&&b.isPresent()&&a.get().breakdownConsistent()&&b.get().breakdownConsistent()&&a.get().base().compareTo(b.get().base())==0&&a.get().multiplier().add(before.displayedBoost().orElseThrow()).compareTo(b.get().multiplier())==0;}
    private static boolean exact(ChocolateFactoryObservation a,ChocolateFactoryObservation b){return a!=null&&b!=null&&a.session()==b.session()&&a.menu()==b.menu()&&a.screen()==b.screen()&&a.revision()==b.revision()&&a.title().equals(b.title())&&a.rows().equals(b.rows());}
    private static boolean protectedInventory(ChocolateFactoryObservation before,ChocolateFactoryObservation after){var a=before.rows().values().stream().filter(r->r.playerInventory()&&r.slot()>=1000).toList();var b=after.rows().values().stream().filter(r->r.playerInventory()&&r.slot()>=1000).toList();return a.size()==b.size()&&a.stream().allMatch(r->Objects.equals(r,after.rows().get(r.slot())));}
    private void ack(ChocolateFactoryObservation o,String text){receipt=o;state=State.ACKNOWLEDGED;reason=text;}
    private boolean deny(String text){state=State.DENIED;reason=text;return false;}
    private void unknown(String text){state=State.UNKNOWN;reason=text;}
    public void persistenceFailed(){state=State.UNKNOWN;reason="Consequential intent/receipt persistence not confirmed; reconcile explicitly";}
    public void cancel(){if(state==State.WAITING_TOWER||state==State.WAITING_PREVIEW||state==State.WAITING_RESET)unknown("Cancelled after consequential dispatch; reconcile before further action");else if(state!=State.UNKNOWN&&state!=State.ACKNOWLEDGED){state=State.IDLE;reason="Consequential selection cancelled";}}
    record ManualClosureSnapshot(State state,String reason,String id) {}
    ManualClosureSnapshot manualClosureSnapshot(){return new ManualClosureSnapshot(state,reason,id);}
    void restoreOwnedManualClosure(ManualClosureSnapshot snapshot){if(!Objects.equals(id,snapshot.id()))throw new IllegalStateException("Another consequential intent replaced the owned closure");state=snapshot.state();reason=snapshot.reason();}
    public void reconcile(){if(dispatching)throw new IllegalStateException("Consequential native input still in progress; retain original pending intent");if(state==State.UNKNOWN){state=State.IDLE;reason="User reconciled consequential intent; fresh deliberate selection required";}}
    public ChocolateActionJournal journal(String status){return new ChocolateActionJournal(1,id,parent.scope(),action.name(),status,state.name(),evidence,reason,receipt==null?"":action==Action.TOWER?"Tower receipt revision="+receipt.revision()+"; "+CapturedChocolateFactoryParser.tower(receipt.rows()).orElseThrow():"Factory IV receipt revision="+receipt.revision()+"; wallet="+receipt.balance()+"; earned="+receipt.prestigeProgress()+"; all-time="+CapturedChocolateFactoryParser.parse(receipt.title(),receipt.rows()).allTime()+"; capacity="+receipt.capacity()+"; observed persistent Barn/Collection/Realm fields unchanged");}
}
