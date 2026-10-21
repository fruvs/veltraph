package dev.veltraph.client.chocolate;

import com.google.gson.Gson;
import dev.veltraph.client.config.*;
import dev.veltraph.client.core.*;
import dev.veltraph.client.service.mc.McHeldKeys;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import java.math.BigDecimal;
import java.util.*;

/** Existing native menu workflow, installed ingestion and local profile-scoped crash journal. */
public final class ChocolateFactoryModule extends AbstractModule {
    public static final String ID="chocolate_factory";
    private final Minecraft client;private final ConfigService config;private final McChocolateFactoryPort port;private final ChocolateFactoryRuntime runtime;private final ChocolateFactoryPlanner planner=new ChocolateFactoryPlanner();
    private final ChocolateConsequentialRuntime consequential;private boolean consequentialMode;
    private final ChocolateSelectedTransactionRuntime transactions;private boolean transactionMode;private ChocolateSelectedTransactionRuntime.Binding transactionBinding;private Object transactionBindingSession;private long transactionBindingEpoch,selectedBindingEpoch;private OptionalLong selectedProfileRevision=OptionalLong.empty(),selectedEnabledRevision=OptionalLong.empty();
    private ChocolateQueueRecovery.Inspection recoveryInspection;private ChocolateQueueIntent recoveryIntent;private Journal recoveryPending;private String recoveryConfigJson;
    private ChocolateUpgradeRepeat repeat;private String repeatReason="";private RepeatTick repeatTick,repeatEnable;private RepeatPreparation repeatPreparation;
    public static final class RepeatTick {
        final ChocolateFactoryModule module;final ChocolateUpgradeRepeat policy;final ModuleManager root;final ModuleManager.LifecycleSnapshot lifecycle;final Thread thread;final boolean enabling,registered;
        ChocolateFactoryRuntime.Selection selected;String intent;ChocolateFactoryRuntime.AcknowledgedUpgrade acknowledgment;
        private RepeatTick(ChocolateFactoryModule module,ChocolateUpgradeRepeat policy){this(module,policy,false);}
        private RepeatTick(ChocolateFactoryModule module,ChocolateUpgradeRepeat policy,boolean enabling){this.module=module;this.policy=policy;this.enabling=enabling;root=dev.veltraph.client.VeltraphBootstrap.moduleManager();registered=root!=null&&root.get(ID).orElse(null)==module;lifecycle=!registered?null:root.lifecycleSnapshot();thread=Thread.currentThread();selected=module.runtime.selection();intent=module.runtime.retainedQueueIntentId();}
        boolean ownerCurrent(){return registered&&Thread.currentThread()==thread&&(enabling?module.repeatEnable==this:module.repeatTick==this)&&module.repeat==policy&&module.isEnabled()&&root==dev.veltraph.client.VeltraphBootstrap.moduleManager()&&root!=null&&root.get(ID).orElse(null)==module&&root.lifecycleCurrent(lifecycle);}
        boolean selectionCurrent(){return ownerCurrent()&&module.runtime.selection()==selected&&module.runtime.retainedQueueIntentId()==intent;}
        boolean terminalCurrent(){return ownerCurrent()&&module.runtime.retainedQueueIntentId()==intent&&(module.runtime.selection()==selected||module.runtime.state()==ChocolateFactoryRuntime.State.DENIED&&module.runtime.selection()==null);}
        boolean acknowledgmentCurrent(){return selectionCurrent()&&module.runtime.state()==ChocolateFactoryRuntime.State.ACKNOWLEDGED&&acknowledgment!=null&&acknowledgment.selection()==selected&&!module.runtime.acknowledgedUpgrades().isEmpty()&&module.runtime.acknowledgedUpgrades().getLast()==acknowledgment;}
    }
    private ChocolateFactoryProfile captured;private String capturedJson;private boolean unreconciled;private java.util.function.BooleanSupplier persistence=()->false;private String persistenceReason="Bind a durable local journal writer before purchases";
    private static final class RepeatRetired extends RuntimeException {}
    public static final class RepeatPreparation {
        final ChocolateFactoryModule module;final ModuleManager root;final ModuleManager.LifecycleSnapshot lifecycle;final Thread thread;final boolean registered;
        private boolean revoked;
        final ChocolateFactoryRuntime.Selection selection;final String intent;final ChocolateFactoryRuntime.State state;final ChocolateUpgradeRepeat prior;
        final String transactionId,consequentialId,profileJson;final ChocolateSelectedTransactionRuntime.State transactionState;final ChocolateConsequentialRuntime.State consequentialState;final boolean transactionMode,consequentialMode,unreconciled;final ChocolateFactoryProfile profile;
        private RepeatPreparation(ChocolateFactoryModule module){this.module=module;root=dev.veltraph.client.VeltraphBootstrap.moduleManager();registered=root!=null&&root.get(ID).orElse(null)==module;lifecycle=!registered?null:root.lifecycleSnapshot();thread=Thread.currentThread();selection=module.runtime.selection();intent=module.runtime.retainedQueueIntentId();state=module.runtime.state();prior=module.repeat;transactionId=module.transactions.id();transactionState=module.transactions.state();consequentialId=module.consequential.id();consequentialState=module.consequential.state();transactionMode=module.transactionMode;consequentialMode=module.consequentialMode;unreconciled=module.unreconciled;profile=module.captured;profileJson=module.capturedJson;}
        boolean ownerCurrent(){return registered&&!revoked&&module.repeatPreparation==this&&Thread.currentThread()==thread&&!module.isEnabled()&&module.repeat==prior&&module.transactions.id()==transactionId&&module.transactions.state()==transactionState&&module.consequential.id()==consequentialId&&module.consequential.state()==consequentialState&&module.transactionMode==transactionMode&&module.consequentialMode==consequentialMode&&module.unreconciled==unreconciled&&module.captured==profile&&module.capturedJson==profileJson&&root!=null&&root==dev.veltraph.client.VeltraphBootstrap.moduleManager()&&root.get(ID).orElse(null)==module&&root.lifecycleCurrent(lifecycle);}
        boolean current(){return ownerCurrent()&&module.runtime.selection()==selection&&module.runtime.retainedQueueIntentId()==intent&&module.runtime.state()==state;}
        boolean originalIntentCurrent(){return ownerCurrent()&&module.runtime.retainedQueueIntentId()==intent&&(module.runtime.selection()==selection||module.runtime.state()==ChocolateFactoryRuntime.State.DENIED&&module.runtime.selection()==null);}
    }
    public record Journal(int schema,String scope,String verifiedOn,String status,String upgrade,String price,String spent,String reason,String manualDeclaredDebit,String intentId) {public Journal(int schema,String scope,String date,String status,String upgrade,String price,String spent,String reason){this(schema,scope,date,status,upgrade,price,spent,reason,"0","");}public Journal(int schema,String scope,String date,String status,String upgrade,String price,String spent,String reason,String manual){this(schema,scope,date,status,upgrade,price,spent,reason,manual,"");}public Journal{manualDeclaredDebit=manualDeclaredDebit==null?"0":manualDeclaredDebit;intentId=intentId==null?"":intentId;}}
    public ChocolateFactoryModule(Minecraft client,ConfigService config){super(ID,"Chocolate Factory",ModuleCategory.MISC);this.client=client;this.config=config;port=new McChocolateFactoryPort(client);runtime=new ChocolateFactoryRuntime(port);consequential=new ChocolateConsequentialRuntime(port);transactions=new ChocolateSelectedTransactionRuntime(port);unreconciled=!config.get(ChocolateFactoryConfig.CHOCOLATE_FACTORY_HISTORY).isBlank()&&pendingJournal();}
    public void bindPersistence(java.util.function.BooleanSupplier flushAndConfirm){persistence=Objects.requireNonNull(flushAndConfirm);persistenceReason="";}
    private boolean flush(){try{return persistence.getAsBoolean();}catch(RuntimeException failed){return false;}}
    private boolean pendingJournal(){try{return ChocolateFactoryHistory.decode(config.get(ChocolateFactoryConfig.CHOCOLATE_FACTORY_HISTORY)).pending();}catch(RuntimeException invalid){return true;}}
    private void writeJournal(Journal journal){
        var history=ChocolateFactoryHistory.decode(config.get(ChocolateFactoryConfig.CHOCOLATE_FACTORY_HISTORY));if("PENDING".equals(journal.status()))history=history.withContract(ChocolateQueueIntent.capture(runtime,captured));config.set(ChocolateFactoryConfig.CHOCOLATE_FACTORY_HISTORY,history.with(journal,"ACKNOWLEDGED".equals(journal.status())&&!runtime.acknowledgedUpgrades().isEmpty()?runtime.acknowledgedUpgrades().getLast().id():null));
    }
    public ChocolateFactoryRuntime runtime(){return runtime;}
    public ChocolateConsequentialRuntime consequential(){return consequential;}
    public ChocolateSelectedTransactionRuntime transactions(){return transactions;}
    private boolean transactionAuthority(){return isEnabled()&&config.get(ChocolateFactoryConfig.CHOCOLATE_FACTORY_ENABLED)&&Objects.equals(capturedJson,config.get(ChocolateFactoryConfig.CHOCOLATE_FACTORY_PROFILE))&&selectedProfileRevision.equals(config.revision(ChocolateFactoryConfig.CHOCOLATE_FACTORY_PROFILE))&&selectedEnabledRevision.equals(config.revision(ChocolateFactoryConfig.CHOCOLATE_FACTORY_ENABLED))&&selectedBindingEpoch==transactionBindingEpoch;}
    public void bindTransactionResource(ChocolateSelectedTransactions.Currency currency,int slot,String prefix,String suffix){
        if(currency==null||currency==ChocolateSelectedTransactions.Currency.NONE)throw new IllegalArgumentException("Explicit CHOCOLATE or COINS denomination required");if(isEnabled()||unreconciled||transactions.hasIntent()||consequential.hasIntent())throw new IllegalStateException("Stop/reconcile before native resource binding");var p=profile();var o=port.read(p);var row=o==null?null:o.rows().get(slot);if(row==null||row.playerInventory()||!o.contextBound()||!o.cursorEmpty())throw new IllegalArgumentException("Current explicitly bound native resource row required");
        var field=new ChocolateFactoryProfile.Field(new ChocolateFactoryProfile.Marker(slot,row.itemId(),row.name(),List.of()),prefix,suffix);if(field.read(o.rows()).isEmpty())throw new IllegalArgumentException("Exactly one full native resource amount required; menu spend/stock counters cannot substitute");if(slot==50||slot==4||slot==11||slot==15)throw new IllegalArgumentException("Captured spend/egg/confirmation rows are not independent funding");
        transactionBinding=new ChocolateSelectedTransactionRuntime.Binding(currency,field,-1,null,0,null);if(!ChocolateSelectedTransactionRuntime.independentResource(transactionBinding,o)){transactionBinding=null;throw new IllegalArgumentException("Price/progress/stock/action metadata cannot supply native spendable funds");}transactionBindingSession=o.session();transactionBindingEpoch++;
    }
    public void bindTransactionOutput(int inventoryIndex){if(isEnabled()||transactions.hasIntent()||unreconciled||transactionBinding==null)throw new IllegalStateException("Bind resource while stopped before output exemplar");var o=port.read(profile());if(o==null||o.session()!=transactionBindingSession||!o.contextBound()||!o.cursorEmpty())throw new IllegalArgumentException("Native binding session changed");transactionBinding=new ChocolateSelectedTransactionRuntime.Binding(transactionBinding.currency(),transactionBinding.resource(),10000+inventoryIndex,o.rows().get(10000+inventoryIndex).fullIdentity(),o.rows().get(10000+inventoryIndex).count(),port.transactionOutputIdentity(inventoryIndex));transactionBindingEpoch++;}
    public boolean selectTransaction(ChocolateSelectedTransactions.Action action,int slot,BigDecimal budget,BigDecimal reserve){
        if(isEnabled()||unreconciled||consequential.hasIntent()||transactions.hasIntent())return false;if(config.revision(ChocolateFactoryConfig.CHOCOLATE_FACTORY_PROFILE).isEmpty()||config.revision(ChocolateFactoryConfig.CHOCOLATE_FACTORY_ENABLED).isEmpty())throw new IllegalStateException("Mutation epochs required for selected transaction config ABA protection");var p=profile();var o=port.read(p);var q=ChocolateSelectedTransactions.quote(action,o,slot);if(q.isEmpty())return false;var h=ChocolateFactoryHistory.decode(config.get(ChocolateFactoryConfig.CHOCOLATE_FACTORY_HISTORY));var bound=transactionBindingSession==o.session()?transactionBinding:null;
        if(!transactions.select(p,o,action,slot,bound,budget,reserve,ChocolateSelectedTransactionRuntime.knownSpend(h,p.scope(),q.get().currency()),System.currentTimeMillis()))return false;
        repeat=null;repeatReason="";runtime.cancel();runtime.clearCompletedReceiptView();transactionMode=true;consequentialMode=false;captured=p;capturedJson=config.get(ChocolateFactoryConfig.CHOCOLATE_FACTORY_PROFILE);selectedProfileRevision=config.revision(ChocolateFactoryConfig.CHOCOLATE_FACTORY_PROFILE);selectedBindingEpoch=transactionBindingEpoch;return true;
    }
    public boolean confirmTransaction(){if(!transactionMode||!transactionAuthority()||!transactions.confirm(captured,System.currentTimeMillis()))return false;if(!persistTransaction("PENDING")){transactions.persistenceFailed();unreconciled=true;setEnabled(false);return false;}return true;}
    private boolean persistTransaction(String status){return ChocolateConsequentialPersistence.persist(transactions.journal(status),this::writeActionJournal,this::flush);}
    private void tickTransaction(){transactions.tick(captured,System.currentTimeMillis(),this::transactionAuthority);var state=transactions.state();if(state==ChocolateSelectedTransactionRuntime.State.ACKNOWLEDGED){if(!persistTransaction("ACKNOWLEDGED"))unreconciled=true;setEnabled(false);}else if(state==ChocolateSelectedTransactionRuntime.State.UNKNOWN||state==ChocolateSelectedTransactionRuntime.State.DENIED){unreconciled=state==ChocolateSelectedTransactionRuntime.State.UNKNOWN;if(!persistTransaction(unreconciled?"UNKNOWN":"DENIED")){unreconciled=true;transactions.persistenceFailed();}setEnabled(false);}}

    private void writeActionJournal(ChocolateActionJournal journal){var history=ChocolateFactoryHistory.decode(config.get(ChocolateFactoryConfig.CHOCOLATE_FACTORY_HISTORY));config.set(ChocolateFactoryConfig.CHOCOLATE_FACTORY_HISTORY,history.withAction(journal));}
    private void writeAction(String status){writeActionJournal(consequential.journal(status));}
    private boolean persistAction(String status){return ChocolateConsequentialPersistence.persist(consequential.journal(status),this::writeActionJournal,this::flush);}
    private boolean selectConsequential(boolean tower,BigDecimal maximumWallet){if(isEnabled()||unreconciled||consequential.hasIntent()||transactions.hasIntent())return false;var p=profile();var o=port.read(p);boolean selected=tower?consequential.selectTower(p,o,System.currentTimeMillis()):consequential.selectPrestige(p,o,maximumWallet,System.currentTimeMillis());if(!selected){consequentialMode=true;return false;}repeat=null;repeatReason="";transactionMode=false;runtime.clearCompletedReceiptView();consequentialMode=true;captured=p;capturedJson=config.get(ChocolateFactoryConfig.CHOCOLATE_FACTORY_PROFILE);return true;}
    public boolean selectTower(){return selectConsequential(true,null);}
    public boolean selectPrestigePreview(BigDecimal maximumResetWallet){return selectConsequential(false,maximumResetWallet);}
    public boolean confirmPrestige(){if(!isEnabled()||!consequentialMode||!consequential.confirmPrestige(captured,System.currentTimeMillis()))return false;if(!persistAction("PENDING")){consequential.persistenceFailed();unreconciled=true;setEnabled(false);return false;}return true;}
    public void stopSelected(){if(repeatPreparation!=null)repeatPreparation.revoked=true;if(repeat!=null){if(isEnabled()){setEnabled(false);return;}repeatReason=repeat.diagnostics()+" | cancelled before start";repeat=null;runtime.cancel();return;}setEnabled(false);transactions.cancel();if(consequentialMode){consequential.cancel();if(consequential.state()!=ChocolateConsequentialRuntime.State.UNKNOWN)consequentialMode=false;}runtime.cancel();}
    public List<ChocolateActionJournal> actionJournals(){return ChocolateFactoryHistory.decode(config.get(ChocolateFactoryConfig.CHOCOLATE_FACTORY_HISTORY)).actions().values().stream().sorted(Comparator.comparing(ChocolateActionJournal::id)).toList();}
    public void reconcileAction(String id,boolean applied,String originalScope){
        if(isEnabled())throw new IllegalStateException("Stop Factory before consequential reconciliation");if(transactions.dispatchInProgress())throw new IllegalStateException("Selected native input still in progress; retain original pending intent");var history=ChocolateFactoryHistory.decode(config.get(ChocolateFactoryConfig.CHOCOLATE_FACTORY_HISTORY));var pending=history.actions().get(id);var visible=port.visibleMenu();var inspection=ChocolateConsequentialReconciliation.inspect(pending,originalScope,visible,System.currentTimeMillis());port.bindReconciliationInspection(originalScope,visible);
        var resolved=new ChocolateActionJournal(pending.schema(),pending.id(),pending.scope(),pending.action(),"RECONCILED",pending.phase(),pending.evidence(),(applied?"User confirmed native action applied; ":"User confirmed native action not applied; ")+inspection,pending.result());var expectedState=consequential.state();var expectedId=consequential.id();var originalRuntime=consequential.manualClosureSnapshot();var expectedTransactionId=transactions.id();var expectedTransactionState=transactions.state();unreconciled=true;
        boolean committed=ChocolateManualReconciliationTransaction.commit(pending,resolved,this::writeActionJournal,this::flush,()->{var current=port.visibleMenu();return Objects.equals(transactions.id(),expectedTransactionId)&&transactions.state()==expectedTransactionState&&Objects.equals(consequential.id(),expectedId)&&consequential.state()==expectedState&&current!=null&&current.fresh(System.currentTimeMillis(),5000)&&current.session()==visible.session()&&current.menu()==visible.menu()&&current.screen()==visible.screen()&&current.revision()==visible.revision()&&current.rows().equals(visible.rows())&&current.cursorEmpty();},()->{if(Objects.equals(consequential.id(),id))consequential.reconcile();},()->consequential.restoreOwnedManualClosure(originalRuntime));
        if(!committed)throw new IllegalStateException("Manual consequential reconciliation not durably confirmed; exact original intent/pending journal preserved for retry");if(Objects.equals(transactions.id(),id))transactions.reconcile();unreconciled=pendingJournal();
    }


    public ChocolateFactoryProfile profile(){return ChocolateFactoryProfile.decode(config.get(ChocolateFactoryConfig.CHOCOLATE_FACTORY_PROFILE));}
    public ChocolateFactoryObservation visibleMenu(){return port.visibleMenu();}
    public ChocolateFactoryObservation inspect(){return port.read(profile());}
    public ChocolateFactoryPlanner.Plan previewPlan(ChocolateFactoryProfile partial,BigDecimal reserve){var o=port.read(partial);if(o==null)throw new IllegalArgumentException("Open the native menu being captured");return planner.plan(partial,o,System.currentTimeMillis(),reserve);}
    public ChocolateFactoryPlanner.Plan plan(BigDecimal reserve){var p=profile();var o=port.read(p);if(o==null)throw new IllegalArgumentException("Open the captured native Factory menu");return planner.plan(p,o,System.currentTimeMillis(),reserve);}
    public boolean selectUpgrade(String id,BigDecimal budget,BigDecimal reserve){return selectUpgrades(List.of(id),budget,reserve);}
    public boolean selectUpgrades(List<String> ids,BigDecimal budget,BigDecimal reserve){
        if(isEnabled()||unreconciled||consequential.hasIntent()||transactions.hasIntent())return false;var p=profile();var o=port.read(p);if(!runtime.selectQueue(p,o,ids,budget,reserve,System.currentTimeMillis()))return false;
        repeat=null;repeatReason="";transactionMode=false;consequentialMode=false;captured=p;capturedJson=config.get(ChocolateFactoryConfig.CHOCOLATE_FACTORY_PROFILE);return true;
    }
    public boolean selectRepeatedUpgrade(String id,int count,BigDecimal budget,BigDecimal reserve,long waitMillis){
        if(repeatPreparation!=null||isEnabled()||unreconciled||consequential.hasIntent()||transactions.hasIntent())return false;
        var preparation=new RepeatPreparation(this);repeatPreparation=preparation;
        try{
            if(!preparation.current())return false;var policy=new ChocolateUpgradeRepeat(id,count,waitMillis);
            var json=config.get(ChocolateFactoryConfig.CHOCOLATE_FACTORY_PROFILE);if(!preparation.current())return false;
            var p=ChocolateFactoryProfile.decode(json);var o=port.read(p);if(!preparation.current())return false;var q=o==null?null:o.quotes().get(id);
            if(q==null||q.kind()!=ChocolateFactoryProfile.Kind.EMPLOYEE)throw new IllegalArgumentException("Repeat requires one exact observed employee promotion/hire quote");
            var actual=config.get(ChocolateFactoryConfig.CHOCOLATE_FACTORY_PROFILE);if(!preparation.current()||!Objects.equals(json,actual)||unreconciled||consequential.hasIntent()||transactions.hasIntent())return false;
            if(!runtime.selectQueue(p,o,List.of(id),budget,reserve,System.currentTimeMillis(),preparation))return false;
            if(!preparation.ownerCurrent())return false;
            repeat=policy;repeatReason=policy.diagnostics();transactionMode=false;consequentialMode=false;captured=p;capturedJson=json;return true;
        }finally{if(repeatPreparation==preparation)repeatPreparation=null;}
    }
    private boolean persistRepeatAcknowledgment(RepeatTick episode){
        var ack=currentJournal("ACKNOWLEDGED");String receiptId=episode.acknowledgment.id();
        try{return ChocolateFactoryAcknowledgmentPersistence.confirm(ack,j->{
            if(!episode.acknowledgmentCurrent())throw new RepeatRetired();
            var text=config.get(ChocolateFactoryConfig.CHOCOLATE_FACTORY_HISTORY);
            if(!episode.acknowledgmentCurrent())throw new RepeatRetired();
            var history=ChocolateFactoryHistory.decode(text);var changed=history.with(j,"ACKNOWLEDGED".equals(j.status())?receiptId:null);
            if(!episode.acknowledgmentCurrent())throw new RepeatRetired();
            config.set(ChocolateFactoryConfig.CHOCOLATE_FACTORY_HISTORY,changed);
        },()->episode.acknowledgmentCurrent()&&flush()&&episode.acknowledgmentCurrent());}catch(RepeatRetired retired){return false;}
    }
    private boolean persistRepeatPending(RepeatTick episode){
        if(!episode.selectionCurrent()||runtime.state()!=ChocolateFactoryRuntime.State.SELECTED)return false;
        var pending=currentJournal("PENDING");var intent=ChocolateQueueIntent.capture(runtime,captured);
        if(!episode.selectionCurrent())return false;
        var text=config.get(ChocolateFactoryConfig.CHOCOLATE_FACTORY_HISTORY);if(!episode.selectionCurrent())return false;
        var history=ChocolateFactoryHistory.decode(text);var changed=history.withContract(intent).with(pending);
        if(!episode.selectionCurrent())return false;config.set(ChocolateFactoryConfig.CHOCOLATE_FACTORY_HISTORY,changed);
        if(!episode.selectionCurrent())return false;boolean durable=flush();return durable&&episode.selectionCurrent();
    }
    private void finishRepeatTerminal(RepeatTick episode,ChocolateFactoryRuntime.State expected){
        if(!episode.terminalCurrent()||runtime.state()!=expected)return;
        if(expected==ChocolateFactoryRuntime.State.UNKNOWN)unreconciled=true;
        var terminal=currentJournal(expected.name());var text=config.get(ChocolateFactoryConfig.CHOCOLATE_FACTORY_HISTORY);
        if(!episode.terminalCurrent()||runtime.state()!=expected)return;
        var history=ChocolateFactoryHistory.decode(text);var changed=history.with(terminal);
        if(!episode.terminalCurrent()||runtime.state()!=expected)return;config.set(ChocolateFactoryConfig.CHOCOLATE_FACTORY_HISTORY,changed);
        if(!episode.terminalCurrent()||runtime.state()!=expected)return;flush();if(!episode.terminalCurrent()||runtime.state()!=expected)return;setEnabled(false);
    }
    private boolean tickRepeat(){
        var episode=repeatTick;var policy=repeat;if(policy==null||runtime.state()!=ChocolateFactoryRuntime.State.ACKNOWLEDGED)return false;
        if(episode==null||!episode.selectionCurrent())return true;
        episode.acknowledgment=runtime.acknowledgedUpgrades().isEmpty()?null:runtime.acknowledgedUpgrades().getLast();
        if(!episode.acknowledgmentCurrent())return true;
        if(policy.needsAcknowledgment(runtime)){
            boolean durable=persistRepeatAcknowledgment(episode);
            if(!episode.acknowledgmentCurrent())return true;
            if(!durable){unreconciled=true;setEnabled(false);return true;}
            policy.acknowledgedDurably(runtime,System.currentTimeMillis());
        }
        long now=System.currentTimeMillis();var o=port.read(captured,now);if(!episode.acknowledgmentCurrent())return true;
        int hotbar=port.currentHotbarSlot();if(!episode.acknowledgmentCurrent())return true;
        var result=policy.advance(runtime,captured,o,hotbar,now,()->episode.acknowledgmentCurrent()&&!unreconciled&&Objects.equals(capturedJson,config.get(ChocolateFactoryConfig.CHOCOLATE_FACTORY_PROFILE))&&episode.acknowledgmentCurrent(),episode);
        if(!episode.ownerCurrent())return true;
        if(result==ChocolateUpgradeRepeat.Result.SELECTED){episode.selected=runtime.selection();episode.intent=runtime.retainedQueueIntentId();episode.acknowledgment=null;
            if(!episode.selectionCurrent())return true;repeatReason=policy.diagnostics();boolean durable=persistRepeatPending(episode);
            if(!episode.selectionCurrent())return true;if(!durable){runtime.cancel();unreconciled=true;setEnabled(false);}return true;
        }
        if(!episode.acknowledgmentCurrent())return true;repeatReason=policy.diagnostics();
        if(result==ChocolateUpgradeRepeat.Result.WAITING)return true;
        setEnabled(false);return true;
    }
    /** Deliberate local scope selection binds this live native context, never a persisted account identity. */
    public void captureAutomaticProfile(String scope){
        if(isEnabled()||unreconciled||runtime.state()==ChocolateFactoryRuntime.State.UNKNOWN||transactions.hasIntent())throw new IllegalStateException("Stop and reconcile Factory results first");port.bindObservedScope(scope);
        var p=new ChocolateFactoryProfile(1,scope,java.time.LocalDate.now().toString(),CapturedChocolateFactoryParser.TITLE,null,null,null,null,null,null,List.of(),5000,10000,null,null,null,null,false,ChocolateFactoryProfile.ContextMode.OBSERVED_NATIVE_SESSION,CapturedChocolateFactoryParser.VERSION);captureProfile(p);
    }
    public CapturedChocolateFactoryParser.State capturedState(){var raw=visibleMenu();if(raw==null)throw new IllegalArgumentException("Open the Factory menu first");return CapturedChocolateFactoryParser.parse(raw.title(),raw.rows());}
    public void captureProfile(ChocolateFactoryProfile p){if(isEnabled()||runtime.state()==ChocolateFactoryRuntime.State.WAITING_RECEIPT||runtime.state()==ChocolateFactoryRuntime.State.UNKNOWN||unreconciled||transactions.hasIntent())throw new IllegalStateException("Stop and reconcile pending Factory results first");var json=p.encode();var observation=port.read(p);if(observation==null||!observation.profileVerified()||observation.balance().isEmpty()||observation.production().isEmpty()||observation.capacity().isEmpty())throw new IllegalArgumentException("Captured contracts do not match current visible profile fields");var saved=savedProfiles();saved.addProperty(p.scope(),json);config.set(ChocolateFactoryConfig.CHOCOLATE_FACTORY_PROFILES,saved.toString());config.set(ChocolateFactoryConfig.CHOCOLATE_FACTORY_PROFILE,json);runtime.cancel();consequential.cancel();repeat=null;repeatReason="";transactionMode=false;consequentialMode=false;captured=null;flush();}
    private com.google.gson.JsonObject savedProfiles(){var json=config.get(ChocolateFactoryConfig.CHOCOLATE_FACTORY_PROFILES);if(json.isBlank())return new com.google.gson.JsonObject();try{return com.google.gson.JsonParser.parseString(json).getAsJsonObject();}catch(RuntimeException invalid){throw new IllegalArgumentException("Saved local Factory profiles are unreadable");}}
    public void useProfile(String scope){if(isEnabled()||unreconciled||transactions.hasIntent())throw new IllegalStateException("Stop and reconcile before switching local profile");var p=savedProfiles().get(scope);if(p==null||!p.isJsonPrimitive()||!p.getAsJsonPrimitive().isString())throw new IllegalArgumentException("No saved local Factory profile "+scope);ChocolateFactoryProfile.decode(p.getAsString());config.set(ChocolateFactoryConfig.CHOCOLATE_FACTORY_PROFILE,p.getAsString());runtime.cancel();consequential.cancel();repeat=null;repeatReason="";transactionMode=false;consequentialMode=false;captured=null;flush();}
    /** Explicit confirmation from UI/command after checking actual menu. Journal survives restart. */
    /** Inspection-free compatibility route is intentionally unavailable in this separate staged adapter. */
    @Deprecated public void reconcileUnknown(boolean purchased){throw new IllegalArgumentException("Repeat original local scope: reconcile purchased|not-purchased <original-scope>");}
    private void writeManualQueueJournal(Journal journal){var current=ChocolateFactoryHistory.decode(config.get(ChocolateFactoryConfig.CHOCOLATE_FACTORY_HISTORY));config.set(ChocolateFactoryConfig.CHOCOLATE_FACTORY_HISTORY,current.withManualQueueJournal(journal));}
    public void reconcileUnknown(boolean purchased,String originalScope){
        if(isEnabled()||consequentialMode)throw new IllegalStateException("Stop the original queue before explicit reconciliation");
        var selected=runtime.selection();if(selected==null||captured==null)throw new IllegalStateException("Original selection/native proof unavailable after restart; retain pending journal until a serialized intent or trusted recovery binding is implemented");
        var history=ChocolateFactoryHistory.decode(config.get(ChocolateFactoryConfig.CHOCOLATE_FACTORY_HISTORY));var pending=history.profiles().get(selected.observation().scope());long now=System.currentTimeMillis();var observed=port.read(captured,now);
        var plan=ChocolateQueueManualReconciliation.prepare(runtime,captured,Objects.equals(capturedJson,config.get(ChocolateFactoryConfig.CHOCOLATE_FACTORY_PROFILE)),pending,originalScope,purchased,observed,port.currentHotbarSlot(),now);
        var originalRuntime=runtime.manualClosureSnapshot();unreconciled=true;
        boolean committed=ChocolateManualReconciliationTransaction.commit(plan.pending(),plan.resolved(),this::writeManualQueueJournal,this::flush,()->{
            if(!Objects.equals(capturedJson,config.get(ChocolateFactoryConfig.CHOCOLATE_FACTORY_PROFILE))||!runtime.matchesManualClosure(plan))return false;
            var current=port.read(captured,System.currentTimeMillis());return current!=null&&current.fresh(System.currentTimeMillis(),captured.freshnessMillis())&&port.currentHotbarSlot()==plan.hotbar()&&ChocolateQueueManualReconciliation.sameInspection(plan.inspection(),current);
        },()->runtime.closeManualReconciliation(plan),()->runtime.restoreOwnedManualClosure(originalRuntime));
        if(!committed)throw new IllegalStateException("Manual queue reconciliation not durably confirmed; original pending intent and runtime remain latched for the same declaration retry");
        unreconciled=pendingJournal();
    }
    /** Read-only listing exposes local action IDs and original scopes, never native/account UUIDs. */
    public List<Journal> unresolvedQueueIntents(){var h=ChocolateFactoryHistory.decode(config.get(ChocolateFactoryConfig.CHOCOLATE_FACTORY_HISTORY));return h.queueIntents().values().stream().filter(j->Set.of("PENDING","UNKNOWN","ACKNOWLEDGED_UNPERSISTED").contains(j.status())).sorted(Comparator.comparing(Journal::intentId)).toList();}
    /** Two deliberate commands: inspect exact original intent, then declare its observed result. */
    public void inspectQueueRecovery(String intentId,String originalScope,String declaration){
        if(isEnabled()||consequential.hasIntent())throw new IllegalStateException("Stop Factory and retain unresolved action before queue recovery");
        var h=ChocolateFactoryHistory.decode(config.get(ChocolateFactoryConfig.CHOCOLATE_FACTORY_HISTORY));var intent=h.queueContracts().get(intentId);var pending=h.queueIntents().get(intentId);
        if(intent==null||pending==null||!Set.of("PENDING","UNKNOWN","ACKNOWLEDGED_UNPERSISTED").contains(pending.status())||!intent.scope().equals(originalScope)||!ChocolateQueueRecovery.DECLARATION.equals(declaration))throw new IllegalArgumentException("Serialized original intent, original scope and explicit original-account declaration required; legacy journals remain blocked without retained recovery facts");
        var raw=port.visibleMenu();if(raw==null||!raw.fresh(System.currentTimeMillis(),ChocolateFactoryProfile.decode(intent.profileJson()).freshnessMillis())||!raw.cursorEmpty())throw new IllegalArgumentException("Open a freshly loaded exact native Factory inspection first");port.bindReconciliationInspection(originalScope,raw);
        long now=System.currentTimeMillis();recoveryInspection=ChocolateQueueRecovery.inspect(intent,intentId,originalScope,declaration,port.read(ChocolateFactoryProfile.decode(intent.profileJson()),now),port.currentHotbarSlot(),now);recoveryIntent=intent;recoveryPending=pending;recoveryConfigJson=config.get(ChocolateFactoryConfig.CHOCOLATE_FACTORY_PROFILE);
    }
    public void reconcileQueueRecovery(String intentId,boolean purchased,String originalScope,String declaration){
        if(isEnabled()||consequential.hasIntent()||recoveryInspection==null||!recoveryInspection.intentId().equals(intentId)||!recoveryInspection.originalScope().equals(originalScope)||!recoveryInspection.declaration().equals(declaration)||!Objects.equals(recoveryConfigJson,config.get(ChocolateFactoryConfig.CHOCOLATE_FACTORY_PROFILE)))throw new IllegalStateException("Repeat exact original-account native inspection before manual recovery declaration");
        var h=ChocolateFactoryHistory.decode(config.get(ChocolateFactoryConfig.CHOCOLATE_FACTORY_HISTORY));var intent=h.queueContracts().get(intentId);var pending=h.queueIntents().get(intentId);if(intent==null||!intent.equals(recoveryIntent)||!Objects.equals(pending,recoveryPending))throw new IllegalArgumentException("Original serialized intent/journal changed after declaration; inspect again");long inspectionNow=System.currentTimeMillis();var refreshed=port.read(ChocolateFactoryProfile.decode(intent.profileJson()),inspectionNow);if(!ChocolateQueueRecovery.matchesInspection(intent,recoveryInspection,refreshed,port.currentHotbarSlot(),inspectionNow))throw new IllegalArgumentException("Native account/menu/target/cursor/hotbar inspection changed before any reconciliation write");var plan=ChocolateQueueRecovery.prepare(intent,pending,recoveryInspection,purchased,runtime,System.currentTimeMillis());unreconciled=true;
        boolean committed=ChocolateManualReconciliationTransaction.commit(plan.pending(),plan.resolved(),this::writeManualQueueJournal,this::flush,()->{
            var currentHistory=ChocolateFactoryHistory.decode(config.get(ChocolateFactoryConfig.CHOCOLATE_FACTORY_HISTORY));if(!Objects.equals(currentHistory.queueContracts().get(intentId),intent)||!Objects.equals(currentHistory.queueIntents().get(intentId),plan.resolved())||!Objects.equals(recoveryConfigJson,config.get(ChocolateFactoryConfig.CHOCOLATE_FACTORY_PROFILE))||!ChocolateQueueRecovery.unchangedRuntime(runtime,plan))return false;
            long now=System.currentTimeMillis();var current=port.read(ChocolateFactoryProfile.decode(intent.profileJson()),now);return ChocolateQueueRecovery.matchesInspection(intent,plan.inspection(),current,port.currentHotbarSlot(),now);
        },()->runtime.closeRecoveryReconciliation(plan),()->runtime.restoreOwnedManualClosure(plan.runtimeBefore()));
        if(!committed)throw new IllegalStateException("Manual recovery not durably committed; original intent/pending and separate ledgers retained for same explicit declaration retry");recoveryInspection=null;recoveryIntent=null;recoveryPending=null;recoveryConfigJson=null;consequentialMode=false;unreconciled=pendingJournal();
    }
    @Override public boolean requiresSkyBlock(){return true;}
    @Override public boolean ownsGameplayInput(){return true;}
    @Override public boolean allowsScreen(String title){try{if(transactionMode&&transactions.quote()!=null)return title.equals(ChocolateSelectedTransactions.title(transactions.quote().action()))||transactions.quote().action()==ChocolateSelectedTransactions.Action.HITMAN&&title.equals("Confirm Purchase");return title.equals(profile().title())||consequentialMode&&consequential.action()==ChocolateConsequentialRuntime.Action.PRESTIGE&&title.equals(ChocolateConsequentialParser.PREVIEW);}catch(RuntimeException incomplete){return false;}}
    @Override public Optional<String> preStartCheck(){
        if(!persistenceReason.isEmpty())return Optional.of(persistenceReason);
        if(!config.get(ChocolateFactoryConfig.CHOCOLATE_FACTORY_ENABLED))return Optional.of("Enable Chocolate Factory explicitly");
        if(unreconciled||runtime.state()==ChocolateFactoryRuntime.State.UNKNOWN||runtime.state()==ChocolateFactoryRuntime.State.WAITING_RECEIPT)return Optional.of("Reconcile the previous Factory result before selecting another upgrade");
        if(transactionMode){if(captured==null||!Objects.equals(capturedJson,config.get(ChocolateFactoryConfig.CHOCOLATE_FACTORY_PROFILE))||!selectedProfileRevision.equals(config.revision(ChocolateFactoryConfig.CHOCOLATE_FACTORY_PROFILE))||selectedBindingEpoch!=transactionBindingEpoch||!transactions.ready(captured,System.currentTimeMillis()))return Optional.of("Select a fresh exact typed transaction first");return Optional.empty();}
        if(consequentialMode){if(captured==null||!Objects.equals(capturedJson,config.get(ChocolateFactoryConfig.CHOCOLATE_FACTORY_PROFILE))||!consequential.ready(captured,System.currentTimeMillis()))return Optional.of("Select a fresh exact consequential Factory intent first");return Optional.empty();}
        if(runtime.state()!=ChocolateFactoryRuntime.State.SELECTED||captured==null)return Optional.of("Select one freshly observed native Factory upgrade first");
        if(!Objects.equals(capturedJson,config.get(ChocolateFactoryConfig.CHOCOLATE_FACTORY_PROFILE)))return Optional.of("Factory profile changed; select the observed row again");
        var o=port.read(captured);var selected=runtime.selection();if(o==null||selected==null||!o.cursorEmpty()||!o.profileVerified()||!o.fresh(System.currentTimeMillis(),captured.freshnessMillis())||o.session()!=selected.observation().session()||o.menu()!=selected.observation().menu()||o.screen()!=selected.observation().screen()||o.revision()!=selected.observation().revision()||!o.rows().equals(selected.observation().rows()))return Optional.of("Selected menu changed; refresh and select the native row again");return Optional.empty();
    }
    private void release(){var o=client.options;for(KeyMapping key:new KeyMapping[]{o.keyUp,o.keyDown,o.keyLeft,o.keyRight,o.keyJump,o.keyShift,o.keySprint,o.keyAttack,o.keyUse})McHeldKeys.setHeld(key,false);}
    private Journal currentJournal(String status){var s=runtime.selection();return new Journal(1,captured.scope(),captured.verifiedOn(),status,s==null?"":s.quote().id(),s==null?"":s.quote().price().toPlainString(),runtime.spent().toPlainString(),runtime.reason(),runtime.manualDeclaredDebit().toPlainString(),runtime.retainedQueueIntentId());}
    private void journal(String status){writeJournal(currentJournal(status));}
    private boolean persistAcknowledgment(){return ChocolateFactoryAcknowledgmentPersistence.confirm(currentJournal("ACKNOWLEDGED"),this::writeJournal,this::flush);}
    private void enableRepeatOwned(ChocolateUpgradeRepeat policy){
        var existing=repeatEnable;if(existing!=null&&existing.policy==policy&&existing.selectionCurrent())return;
        var episode=new RepeatTick(this,policy,true);repeatEnable=episode;
        try{if(!episode.selectionCurrent())return;release();if(!episode.selectionCurrent())return;var blocker=preStartCheck();if(!episode.selectionCurrent())return;if(blocker.isPresent()){setEnabled(false);return;}
            boolean durable=persistRepeatPending(episode);if(!episode.selectionCurrent())return;if(!durable){unreconciled=true;runtime.cancel();episode.selected=runtime.selection();setEnabled(false);}
        }finally{if(repeatEnable==episode)repeatEnable=null;}
    }
    @Override public void onEnable(){if(repeat!=null){enableRepeatOwned(repeat);return;}release();var blocker=preStartCheck();if(blocker.isPresent()){setEnabled(false);return;}if(transactionMode){selectedEnabledRevision=config.revision(ChocolateFactoryConfig.CHOCOLATE_FACTORY_ENABLED);if(!persistTransaction("PENDING")){unreconciled=true;transactions.persistenceFailed();setEnabled(false);}return;}if(consequentialMode){if(!persistAction("PENDING")){unreconciled=true;consequential.persistenceFailed();setEnabled(false);}return;}journal("PENDING");if(!flush()){unreconciled=true;runtime.cancel();setEnabled(false);}}
    private void tickConsequential(){if(!Objects.equals(capturedJson,config.get(ChocolateFactoryConfig.CHOCOLATE_FACTORY_PROFILE))){consequential.cancel();setEnabled(false);return;}var previous=consequential.state();consequential.tick(captured,System.currentTimeMillis());var state=consequential.state();if(state==ChocolateConsequentialRuntime.State.PREVIEW_READY&&previous!=state){if(!persistAction("PREVIEW_READY")){unreconciled=true;consequential.persistenceFailed();setEnabled(false);}return;}if(state==ChocolateConsequentialRuntime.State.ACKNOWLEDGED){if(!persistAction("ACKNOWLEDGED"))unreconciled=true;setEnabled(false);}else if(state==ChocolateConsequentialRuntime.State.UNKNOWN){unreconciled=true;writeAction("UNKNOWN");flush();setEnabled(false);}else if(state==ChocolateConsequentialRuntime.State.DENIED){writeAction("DENIED");flush();setEnabled(false);}}
    @Override public void onTick(){if(repeatTick!=null||repeatEnable!=null)return;var policy=repeat;if(policy==null){tickBody();return;}var episode=new RepeatTick(this,policy);repeatTick=episode;try{if(episode.selectionCurrent())tickBody();}finally{if(repeatTick==episode)repeatTick=null;}}
    private void tickBody(){release();if(repeatTick!=null&&!repeatTick.terminalCurrent())return;if(unreconciled){setEnabled(false);return;}if(transactionMode){tickTransaction();return;}if(consequentialMode){tickConsequential();return;}String expectedProfile=capturedJson;String currentProfile=config.get(ChocolateFactoryConfig.CHOCOLATE_FACTORY_PROFILE);if(repeatTick!=null&&!repeatTick.terminalCurrent())return;if(!Objects.equals(expectedProfile,currentProfile)){if(repeatTick!=null){var episode=repeatTick;if(!episode.terminalCurrent())return;runtime.cancel();episode.selected=runtime.selection();if(runtime.state()==ChocolateFactoryRuntime.State.UNKNOWN)finishRepeatTerminal(episode,runtime.state());else if(episode.terminalCurrent())setEnabled(false);return;}runtime.cancel();if(runtime.state()==ChocolateFactoryRuntime.State.UNKNOWN){unreconciled=true;journal("UNKNOWN");}setEnabled(false);return;}runtime.tick(captured,System.currentTimeMillis());if(repeatTick!=null&&!repeatTick.terminalCurrent())return;var state=runtime.state();if(repeatTick!=null&&(state==ChocolateFactoryRuntime.State.DENIED||state==ChocolateFactoryRuntime.State.UNKNOWN)){finishRepeatTerminal(repeatTick,state);return;}if(tickRepeat())return;state=runtime.state();if(state==ChocolateFactoryRuntime.State.ACKNOWLEDGED&&runtime.queued()>0){if(!persistAcknowledgment()){unreconciled=true;setEnabled(false);return;}if(runtime.advanceQueue(captured,System.currentTimeMillis())){journal("PENDING");if(!flush()){runtime.cancel();unreconciled=true;setEnabled(false);}return;}state=runtime.state();}if(state==ChocolateFactoryRuntime.State.ACKNOWLEDGED||state==ChocolateFactoryRuntime.State.DENIED||state==ChocolateFactoryRuntime.State.UNKNOWN){unreconciled=state==ChocolateFactoryRuntime.State.UNKNOWN;if(state==ChocolateFactoryRuntime.State.ACKNOWLEDGED){if(!persistAcknowledgment())unreconciled=true;}else{journal(state==ChocolateFactoryRuntime.State.UNKNOWN?"UNKNOWN":state.name());flush();}setEnabled(false);}}
    private void disableRepeatOwned(ChocolateUpgradeRepeat policy){
        var root=dev.veltraph.client.VeltraphBootstrap.moduleManager();if(root==null||root.get(ID).orElse(null)!=this)return;var lifecycle=root.lifecycleSnapshot();var selection=runtime.selection();var intent=runtime.retainedQueueIntentId();
        if(isEnabled()||repeat!=policy||root!=dev.veltraph.client.VeltraphBootstrap.moduleManager()||root.get(ID).orElse(null)!=this||!root.lifecycleCurrent(lifecycle))return;release();if(isEnabled()||repeat!=policy||root==null||root!=dev.veltraph.client.VeltraphBootstrap.moduleManager()||root.get(ID).orElse(null)!=this||!root.lifecycleCurrent(lifecycle)||runtime.selection()!=selection||runtime.retainedQueueIntentId()!=intent)return;
        repeatReason=policy.diagnostics()+" | stopped; no automatic rearm";repeat=null;runtime.cancel();
        var state=runtime.state();var after=runtime.selection();
        if(state==ChocolateFactoryRuntime.State.UNKNOWN&&captured!=null)unreconciled=true;
        if(captured!=null&&(state==ChocolateFactoryRuntime.State.UNKNOWN||!unreconciled&&state!=ChocolateFactoryRuntime.State.ACKNOWLEDGED)){
            var journal=currentJournal(state==ChocolateFactoryRuntime.State.UNKNOWN?"UNKNOWN":"STOPPED");var text=config.get(ChocolateFactoryConfig.CHOCOLATE_FACTORY_HISTORY);
            if(isEnabled()||repeat!=null||runtime.selection()!=after||runtime.retainedQueueIntentId()!=intent||root!=dev.veltraph.client.VeltraphBootstrap.moduleManager()||root.get(ID).orElse(null)!=this||!root.lifecycleCurrent(lifecycle))return;
            var history=ChocolateFactoryHistory.decode(text);config.set(ChocolateFactoryConfig.CHOCOLATE_FACTORY_HISTORY,history.with(journal));
        }
        if(isEnabled()||repeat!=null||runtime.selection()!=after||runtime.retainedQueueIntentId()!=intent||root!=dev.veltraph.client.VeltraphBootstrap.moduleManager()||root.get(ID).orElse(null)!=this||!root.lifecycleCurrent(lifecycle))return;flush();
    }
    @Override public void onDisable(){if(repeat!=null){disableRepeatOwned(repeat);return;}release();if(transactionMode){transactions.cancel();if(transactions.id()!=null&&transactions.state()!=ChocolateSelectedTransactionRuntime.State.ACKNOWLEDGED){if(transactions.state()==ChocolateSelectedTransactionRuntime.State.UNKNOWN){unreconciled=true;persistTransaction("UNKNOWN");}else if(!persistTransaction("STOPPED")){unreconciled=true;transactions.persistenceFailed();}}return;}if(consequentialMode){consequential.cancel();if(consequential.id()!=null){if(consequential.state()==ChocolateConsequentialRuntime.State.UNKNOWN){unreconciled=true;writeAction("UNKNOWN");}else if(consequential.state()!=ChocolateConsequentialRuntime.State.ACKNOWLEDGED)writeAction("STOPPED");flush();}return;}runtime.cancel();if(runtime.state()==ChocolateFactoryRuntime.State.UNKNOWN&&captured!=null){unreconciled=true;journal("UNKNOWN");}else if(captured!=null&&!unreconciled&&runtime.state()!=ChocolateFactoryRuntime.State.ACKNOWLEDGED)journal("STOPPED");flush();}
    @Override public String diagnostics(){return (transactionMode?transactions.state()+": "+transactions.reason()+" | intent "+transactions.id():consequentialMode?consequential.state()+": "+consequential.reason()+" | intent "+consequential.id():runtime.state()+": "+runtime.reason())+(repeatReason.isEmpty()?"":" | "+repeatReason)+(unreconciled?" | persisted result requires user reconciliation":"");}
}
