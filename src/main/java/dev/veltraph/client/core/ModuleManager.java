package dev.veltraph.client.core;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Registers modules and drives their per-tick updates. Pure (JDK-only) so it can be unit tested
 * without the Minecraft runtime.
 *
 * <p>Safety: {@link #tick()} guards each module's update; a thrown exception disables that module
 * (which releases its inputs via {@link Module#onDisable()}) so one misbehaving module cannot wedge
 * the input state or break the others. {@link #stopAll()} disables every enabled module.
 */
public final class ModuleManager {

    private static final Logger LOGGER = Logger.getLogger("Veltraph.ModuleManager");

    private final Map<String, Module> byId = new LinkedHashMap<>();
    private long registrationVersion,inputBindingVersion,stopCallbackVersion;private boolean stopCallbackExhausted;
    private boolean snapshotVersionExhausted;
    private OwnEnableInvocation ownEnableInvocation;
    private OwnEnableReceipt lastConfirmedOwnEnable;
    public interface ActualEnableObserver {Object before(ActualEnableStart actual);void after(OwnEnableReceipt actual,Object captured);}
    public static final class ActualEnableRegistration {
        private final ModuleManager registry;private final ActualEnableObserver observer;
        private ActualEnableRegistration(ModuleManager registry,ActualEnableObserver observer){this.registry=registry;this.observer=observer;}
        public boolean current(){return registry.actualEnableRegistration==this;}
    }
    private ActualEnableRegistration actualEnableRegistration;
    public ActualEnableRegistration observeActualEnables(ActualEnableObserver observer){return actualEnableRegistration=new ActualEnableRegistration(this,java.util.Objects.requireNonNull(observer));}
    /** Private invocation, captured before the actual enabled field or original log is changed. */
    public static final class ActualEnableStart {
        private final OwnEnableInvocation invocation;
        private ActualEnableStart(OwnEnableInvocation invocation){this.invocation=invocation;}
        public ModuleManager registryIdentity(){return invocation.registry;}
        public Module parent(){return invocation.parent;}
        public LifecycleSnapshot beforeSnapshot(){return invocation.before;}
        public boolean startedFrom(LifecycleSnapshot retained){return retained!=null&&invocation.before.same(retained);}
        public boolean startingCurrent(){return invocation.registry.ownEnableInvocation==invocation&&!invocation.revoked
            &&Thread.currentThread()==invocation.thread&&invocation.registry.lifecycleCurrent(invocation.before)
            &&invocation.registration!=null&&invocation.registration.current();}
        public boolean enabledCurrent(){return invocation.receipt!=null&&invocation.receipt.invocationCurrent()
            &&invocation.registration!=null&&invocation.registration.current();}
    }
    public interface ActualDisableObserver {Object before(ActualDisableStart actual);void after(OwnDisableReceipt actual,Object captured);default void closed(ActualDisableStart actual,Object captured){}}
    public static final class ActualDisableRegistration {
        private final ModuleManager registry;private final ActualDisableObserver observer;
        private ActualDisableRegistration(ModuleManager registry,ActualDisableObserver observer){this.registry=registry;this.observer=observer;}
        public boolean current(){return registry.actualDisableRegistration==this;}
    }
    private ActualDisableRegistration actualDisableRegistration;
    public ActualDisableRegistration observeActualDisables(ActualDisableObserver observer){return actualDisableRegistration=new ActualDisableRegistration(this,java.util.Objects.requireNonNull(observer));}
    public static final class ActualDisableStart {
        private final OwnDisableInvocation invocation;private ActualDisableStart(OwnDisableInvocation invocation){this.invocation=invocation;}
        public ModuleManager registryIdentity(){return invocation.registry;}public Module parent(){return invocation.parent;}
        public LifecycleSnapshot beforeSnapshot(){return invocation.before;}
        public ManualDisableInstruction originalManualInstruction(){var source=invocation.manualInstruction;return source!=null&&source.currentFor(this)?source:null;}
        public boolean startedFrom(LifecycleSnapshot retained){return retained!=null&&invocation.before.same(retained);}
        public boolean startingCurrent(){return invocation.registry.ownDisableInvocation==invocation&&!invocation.revoked&&Thread.currentThread()==invocation.thread
            &&invocation.registration!=null&&invocation.registration.current()&&invocation.registry.lifecycleCurrent(invocation.before);}
        public boolean disabledCurrent(){return invocation.registration!=null&&invocation.registration.current()&&invocation.registry.pendingOwnDisableFromMatches(invocation.before,invocation.parent);}
    }
    private OwnDisableInvocation ownDisableInvocation;
    private final ThreadLocal<ControllerScratch> controllerScratch = ThreadLocal.withInitial(ControllerScratch::new);
    private java.util.function.Consumer<Module> beforeDisable=module->{};
    private Runnable beforeStopAll=()->{};
    private java.util.function.Supplier<Module> leasedInputOwner=()->null;

    public static final class OriginalStopBindings {
        private final ModuleManager registry;private final long version;private final Object beforeAll,beforeEach,registration;
        private OriginalStopBindings(ModuleManager registry){this.registry=registry;version=registry.stopCallbackVersion;beforeAll=registry.beforeStopAll;beforeEach=registry.beforeDisable;registration=registry.manualStopRegistration;}
        public boolean current(){return !registry.stopCallbackExhausted&&version==registry.stopCallbackVersion&&beforeAll==registry.beforeStopAll&&beforeEach==registry.beforeDisable&&registration==registry.manualStopRegistration;}
    }
    public OriginalStopBindings originalStopBindingsFrom(dev.veltraph.client.VeltraphBootstrap.RootOriginalControlStartup actual){
        return actual!=null&&actual.actualStopBindingCapture(this)?new OriginalStopBindings(this):null;
    }
    /** Exact owned handoffs may cancel their captured pair before manual/failsafe controller stop. */
    public void beforeDisable(java.util.function.Consumer<Module> callback){var exact=java.util.Objects.requireNonNull(callback);advanceStopCallbacks();beforeDisable=exact;}
    /** Global cancel also runs when there are no currently enabled controllers. */
    public void beforeStopAll(Runnable callback){var exact=java.util.Objects.requireNonNull(callback);advanceStopCallbacks();beforeStopAll=exact;}
    private void advanceStopCallbacks(){if(stopCallbackVersion==Long.MAX_VALUE)stopCallbackExhausted=true;else stopCallbackVersion++;}
    public void leasedInputOwner(java.util.function.Supplier<Module> supplier){leasedInputOwner=java.util.Objects.requireNonNull(supplier);if(inputBindingVersion==Long.MAX_VALUE)snapshotVersionExhausted=true;else inputBindingVersion++;}
    public Optional<Module> inputOwner(){
        Module leased=leasedInputOwner.get();
        if(leased!=null&&byId.get(leased.id())==leased&&leased.isEnabled()&&!leased.isPassiveObserver()&&leased.ownsGameplayInput())return Optional.of(leased);
        var scratch=controllerScratch.get();
        var enabled=scratch.borrow();
        try {
            // Preserve complete enabled enumeration before any fallback role getter.
            collectEnabledControllersSnapshot(enabled);
            for(int controllerIndex=0;controllerIndex<enabled.size();controllerIndex++) {
                Module module=enabled.get(controllerIndex);
                if(!module.isPassiveObserver()&&module.ownsGameplayInput())return Optional.of(module);
            }
            return Optional.empty();
        } finally {
            scratch.release(enabled);
        }
    }

    public void register(Module module) {
        if (byId.putIfAbsent(module.id(), module) != null) {
            throw new IllegalArgumentException("Duplicate module id: " + module.id());
        }
        if(registrationVersion==Long.MAX_VALUE)snapshotVersionExhausted=true;else registrationVersion++;
        if(module instanceof AbstractModule actual)actual.attachOwnedRegistry(this);
    }

    /** Callback-free exact registry/lifecycle evidence; unknown module implementations refuse scoped proof. */
    private List<LifecycleEntry> reusableLifecycleData=List.of();
    public LifecycleSnapshot lifecycleSnapshot(){
        var prior=reusableLifecycleData;ArrayList<LifecycleEntry> changed=null;
        boolean known=!snapshotVersionExhausted;int index=0;
        for(var entry:byId.entrySet()){
            var stamp=entry.getValue() instanceof AbstractModule module?module.storedLifecycle():null;
            if(stamp==null||!stamp.known())known=false;
            var observedId=entry.getKey();var observedModule=entry.getValue();
            var previous=index<prior.size()?prior.get(index):null;
            boolean same=known&&stamp!=null&&previous!=null&&previous.registrationId()==observedId&&previous.module()==observedModule&&previous.stamp()==stamp;
            if(changed==null&&!same){changed=new ArrayList<>();for(int prefix=0;prefix<index;prefix++)changed.add(prior.get(prefix));}
            if(changed!=null)changed.add(new LifecycleEntry(observedId,observedModule,stamp));
            index++;
        }
        List<LifecycleEntry> entries=changed!=null?List.copyOf(changed):index==prior.size()?prior:List.copyOf(prior.subList(0,index));
        reusableLifecycleData=entries;
        // Fresh public snapshot identity is preserved; private values never cache a permission result.
        return new LifecycleSnapshot(this,registrationVersion,inputBindingVersion,leasedInputOwner,entries,known);
    }
    /** Performs only another private-field snapshot; no Module, supplier, role or native callback runs. */
    public boolean lifecycleCurrent(LifecycleSnapshot captured){return captured!=null&&captured.currentStored(this);}
    /** Original registry/history plus only the expected one normal pause of this actor. */
    public boolean lifecyclePausedCurrent(LifecycleSnapshot original,AbstractModule parent){return original!=null&&original.scheduledPauseCurrent(this,parent);}
    /** Negative cleanup projection only; cannot authorize pause, resume or any SDK publication. */
    public boolean originalEnableLifetimeCurrent(LifecycleSnapshot original,AbstractModule actor){return original!=null&&original.originalEnableLifetimeCurrent(this,actor);}
    public boolean lifecycleSameHistory(LifecycleSnapshot first,LifecycleSnapshot second){return first!=null&&second!=null&&first.registry==this&&second.registry==this&&first.same(second);}
    /** Negative exact actor check against an original private stamp, independent of expected other-actor transitions. */
    public boolean originalModuleLifecycleCurrent(LifecycleSnapshot original,Module exact){
        if(original==null||exact==null||!original.known||original.registry!=this||original.registrationVersion!=registrationVersion
            ||original.inputBindingVersion!=inputBindingVersion||original.inputSupplier!=leasedInputOwner)return false;
        for(var entry:original.entries)if(entry.module()==exact)return byId.get(entry.registrationId())==exact
            &&exact instanceof AbstractModule actual&&actual.storedLifecycleCurrent(entry.stamp());
        return false;
    }
    /** Exact old actor stamp only, for logical retirement; never input/camera authority or a roster waiver. */
    public boolean originalActorLifecycleCurrent(LifecycleSnapshot original,Module exact){
        if(original==null||exact==null||!original.known||original.registry!=this)return false;
        for(var entry:original.entries)if(entry.module()==exact)return byId.get(entry.registrationId())==exact
            &&exact instanceof AbstractModule actual&&actual.storedLifecycleCurrent(entry.stamp());
        return false;
    }
    /** Negative original no-gameplay-owner roster check; no actor role or supplier callback runs. */
    public boolean noGameplayOwnerRosterCurrent(LifecycleSnapshot original,dev.veltraph.client.VeltraphBootstrap.RootPrivatePassiveSource roles){
        if(original==null||roles==null||!original.currentStored(this)||!roles.originalRoleHistoryCurrent(this))return false;
        for(var entry:original.entries)if(entry.stamp().enabled()&&!roles.declaredActor(entry.module()))return false;
        return true;
    }
    /** Invocation object exists only around an actual registered AbstractModule private enabled transition. */
    static final class OwnEnableInvocation {
        final ModuleManager registry;final AbstractModule parent;final LifecycleSnapshot before;final Thread thread;
        boolean revoked;OwnEnableReceipt receipt;final ActualEnableStart start;final ActualEnableRegistration registration;Object captured;
        private OwnEnableInvocation(ModuleManager registry,AbstractModule parent,LifecycleSnapshot before){this.registry=registry;this.parent=parent;this.before=before;thread=Thread.currentThread();registration=registry.actualEnableRegistration;start=new ActualEnableStart(this);}
    }
    public static final class OwnEnableReceipt {
        private final OwnEnableInvocation invocation;private final LifecycleSnapshot after;private boolean completed,successful,closed;
        private OwnEnableReceipt(OwnEnableInvocation invocation,LifecycleSnapshot after){this.invocation=invocation;this.after=after;}
        public Module parent(){return invocation.parent;}
        public ActualEnableStart actualStart(){return invocation.start;}
        public LifecycleSnapshot completedSnapshot(){return current()?after:null;}
        /** Parent's exact private completed enable; callers must separately retain the whole roster. */
        public boolean parentEnableHistoryCurrent(){return closed&&completed&&successful&&invocation.registry.ownEnableInvocation!=invocation&&!invocation.revoked&&Thread.currentThread()==invocation.thread
            &&after.parentEnableHistoryCurrent(invocation.registry,invocation.parent);}
        /** Completed private enable and full private actor lineage; current values cannot restore an old transition. */
        public boolean enabledHistoryCurrent(){return closed&&completed&&successful&&invocation.registry.ownEnableInvocation!=invocation&&!invocation.revoked&&Thread.currentThread()==invocation.thread
            &&after.enabledHistoryCurrent(invocation.registry,invocation.parent);}
        /** Read-only in-callback evidence; tentative events grant no restart/admission. */
        public boolean invocationCurrent(){return !completed&&invocation.registry.ownEnableInvocation==invocation&&!invocation.revoked
            &&Thread.currentThread()==invocation.thread&&invocation.registry.lifecycleCurrent(after);}
        /** Only normal return from the original actual enabled transition can certify this receipt. */
        public boolean current(){return closed&&completed&&successful&&invocation.registry.ownEnableInvocation!=invocation&&!invocation.revoked&&Thread.currentThread()==invocation.thread&&invocation.registry.lifecycleCurrent(after);}
    }
    OwnEnableInvocation beforeActualEnable(AbstractModule parent){
        if(manualStopInvocation!=null)manualStopInvocation.revoked=true;
        if(ownDisableInvocation!=null){ownDisableInvocation.revoked=true;return null;}
        if(ownEnableInvocation!=null){ownEnableInvocation.revoked=true;return null;}
        try {var before=lifecycleSnapshot();if(!before.known()||byId.get(parent.id())!=parent||parent.storedLifecycle().enabled())return null;
            var invocation=new OwnEnableInvocation(this,parent,before);ownEnableInvocation=invocation;
            if(invocation.registration!=null&&invocation.start!=null)try{invocation.captured=invocation.registration.observer.before(invocation.start);}catch(RuntimeException|Error unavailable){invocation.captured=null;}
            return invocation;
        }catch(RuntimeException unavailable){return null;}
    }
    void enterActualEnable(OwnEnableInvocation invocation){
        if(invocation!=ownEnableInvocation||invocation.revoked)return;
        try {var after=lifecycleSnapshot();if(!invocation.before.ownEnableTo(after,invocation.parent)){invocation.revoked=true;return;}
            invocation.receipt=new OwnEnableReceipt(invocation,after);
        }catch(RuntimeException unavailable){invocation.revoked=true;}
    }
    void finishActualEnable(OwnEnableInvocation invocation,boolean successful){
        OwnEnableReceipt observed=null;
        try{
            if(invocation.receipt!=null){var receipt=invocation.receipt;
                receipt.successful=successful&&!invocation.revoked&&lifecycleCurrent(receipt.after);receipt.completed=true;
                if(receipt.successful)observed=receipt;
            }
            if(!successful)invocation.revoked=true;
        }catch(RuntimeException unavailable){invocation.revoked=true;if(invocation.receipt!=null){invocation.receipt.successful=false;invocation.receipt.completed=true;}}
        finally{
            if(ownEnableInvocation==invocation)ownEnableInvocation=null;
            if(invocation.receipt!=null)invocation.receipt.closed=true;
        }
        // The actual private invocation is CLOSED before the extra passive observer can borrow its receipt.
        if(observed!=null&&observed.current()){
            lastConfirmedOwnEnable=observed;
            if(invocation.registration!=null&&invocation.registration.current()&&invocation.captured!=null)
                try{invocation.registration.observer.after(observed,invocation.captured);}catch(RuntimeException|Error unavailable){}
        }
    }
    /** No public onEnable/counter or caller snapshot can mint this private actual-method receipt. */
    public Optional<OwnEnableReceipt> currentOwnEnableReceipt(Module exactParent){
        var invocation=ownEnableInvocation;var receipt=invocation==null?null:invocation.receipt;
        return invocation!=null&&invocation.parent==exactParent&&receipt!=null&&receipt.invocationCurrent()?Optional.of(receipt):Optional.empty();
    }
    /** Exact disabled snapshot of a privately owned cleanup token must precede this actual single enable. */
    public boolean ownEnableMatches(OwnEnableReceipt receipt,LifecycleSnapshot originalDisabled,Module exactParent){
        return receipt!=null&&receipt.invocation.registry==this&&receipt.invocation.parent==exactParent&&originalDisabled!=null
            &&originalDisabled.same(receipt.invocation.before)&&receipt.current();
    }
    /** Completed receipts are retained from actual normal return; this never creates an enable event. */
    public Optional<OwnEnableReceipt> completedOwnEnableFrom(LifecycleSnapshot before,Module exact){
        var receipt=lastConfirmedOwnEnable;return ownEnableMatches(receipt,before,exact)?Optional.of(receipt):Optional.empty();
    }
    public boolean ownEnableInvocationMatches(OwnEnableReceipt receipt,LifecycleSnapshot before,Module exact){
        return receipt!=null&&receipt.invocation.registry==this&&receipt.parent()==exact&&before!=null
            &&before.same(receipt.invocation.before)&&receipt.invocationCurrent();
    }
    /** Separate manual cause; never mints the normal-business/G7 input-drop cause. */
    public interface ManualStopObserver {Object before(ManualStopStart actual);void after(ManualStopReceipt actual,Object captured);default void closedNoGameplayStop(OwnNoGameplayStopReceipt actual,Object captured){}}
    public static final class ManualStopRegistration {
        private final ModuleManager root;private final ManualStopObserver observer;
        private ManualStopRegistration(ModuleManager root,ManualStopObserver observer){this.root=root;this.observer=observer;}
        public boolean current(){return root.manualStopRegistration==this;}
    }
    private ManualStopRegistration manualStopRegistration;private ManualStopInvocation manualStopInvocation;
    public ManualStopRegistration observeManualStops(ManualStopObserver observer){return manualStopRegistration=new ManualStopRegistration(this,java.util.Objects.requireNonNull(observer));}
    private static final class ManualStopInvocation {
        final ModuleManager root;final LifecycleSnapshot before;final Thread thread;final ManualStopRegistration registration;
        final List<ActualManualDisableReturn> returns=new ArrayList<>();final ManualStopStart start;
        final ManualStopInvocation previous;final long callbackVersion;final Runnable beforeAll;final java.util.function.Consumer<Module> beforeEach;boolean revoked,normal,closed;Object captured;ManualStopReceipt receipt;
        ManualStopInvocation(ModuleManager root,ManualStopInvocation previous){this.root=root;this.previous=previous;before=root.lifecycleSnapshot();thread=Thread.currentThread();registration=root.manualStopRegistration;callbackVersion=root.stopCallbackVersion;beforeAll=root.beforeStopAll;beforeEach=root.beforeDisable;start=new ManualStopStart(this);revoked=previous!=null;}
    }
    public static final class ManualStopStart {
        private final ManualStopInvocation call;private ManualStopStart(ManualStopInvocation call){this.call=call;}
        public ModuleManager registryIdentity(){return call.root;}
        public LifecycleSnapshot originalBeforeLifecycle(){return call.before;}
        public boolean startingCurrent(){return !call.revoked&&call.root.manualStopInvocation==call&&Thread.currentThread()==call.thread
            &&call.before.known()&&call.root.lifecycleCurrent(call.before)&&call.registration!=null&&call.registration.current()&&!call.root.stopCallbackExhausted&&call.root.stopCallbackVersion==call.callbackVersion&&call.root.beforeStopAll==call.beforeAll&&call.root.beforeDisable==call.beforeEach;}
        /** Exact captured manual stop plus authenticated pending/normal disabled transitions, never current-bit adoption. */
        public boolean nativeStopCurrent(Module exact){
            if(call.revoked||call.root.manualStopInvocation!=call||Thread.currentThread()!=call.thread||call.registration==null||!call.registration.current()
                ||call.root.stopCallbackExhausted||call.callbackVersion!=call.root.stopCallbackVersion||call.beforeAll!=call.root.beforeStopAll||call.beforeEach!=call.root.beforeDisable
                ||!call.before.enabledParent(exact))return false;
            var now=call.root.lifecycleSnapshot();if(!call.before.sameRoster(now))return false;
            for(int i=0;i<call.before.entries.size();i++){
                var original=call.before.entries.get(i);var current=now.entries.get(i);
                if(original.stamp().same(current.stamp()))continue;
                var pending=call.root.ownDisableInvocation;
                if(pending!=null&&!pending.revoked&&pending.parent==original.module()&&pending.before.sameRoster(call.before)
                    &&pending.before.entries.get(i).stamp().same(original.stamp())&&pending.before.ownDisableInProgressTo(now,pending.parent))continue;
                boolean returned=false;
                for(var actual:call.returns)if(actual.actual.parent==original.module()&&actual.actual.before.entries.get(i).stamp().same(original.stamp())
                    &&actual.actual.before.manualDisableTo(actual.after,actual.actual.parent)&&actual.after.entries.get(i).stamp().same(current.stamp())){returned=true;break;}
                if(!returned)return false;
            }
            return true;
        }
        public boolean enabledFromStart(Module exact){return startingCurrent()&&call.before.enabledParent(exact);}
    }
    /** Authenticated by the actual final setEnabled(false) body normal return, including PAUSED. */
    private static final class ActualManualDisableReturn {
        final OwnDisableInvocation actual;final LifecycleSnapshot after;
        ActualManualDisableReturn(OwnDisableInvocation actual,LifecycleSnapshot after){this.actual=actual;this.after=after;}
    }
    void observeManualDisableNormalReturned(OwnDisableInvocation actual){var manual=manualStopInvocation;
        if(manual==null||manual.revoked||actual==null||!actual.manualNormalReturned||Thread.currentThread()!=manual.thread||Thread.currentThread()!=actual.thread)return;
        try{var after=lifecycleSnapshot();if(actual.before.manualDisableTo(after,actual.parent))manual.returns.add(new ActualManualDisableReturn(actual,after));else manual.revoked=true;}
        catch(RuntimeException unavailable){manual.revoked=true;}
    }
    public static final class ManualStopReceipt {
        private final ManualStopInvocation call;private final LifecycleSnapshot after;
        private ManualStopReceipt(ManualStopInvocation call,LifecycleSnapshot after){this.call=call;this.after=after;}
        public boolean matches(ManualStopStart start){return start!=null&&start.call==call&&current();}
        public boolean current(){return call.normal&&!call.revoked&&call.root.manualStopInvocation==call&&Thread.currentThread()==call.thread
            &&call.registration!=null&&call.registration.current()&&!call.root.stopCallbackExhausted&&call.root.stopCallbackVersion==call.callbackVersion&&call.root.beforeStopAll==call.beforeAll&&call.root.beforeDisable==call.beforeEach&&call.before.sameRoster(after)&&after.noneEnabledCurrent(call.root)&&everyOriginallyEnabledReturned();}
        private boolean everyOriginallyEnabledReturned(){
            for(var entry:call.before.entries)if(entry.stamp().enabled()&&call.returns.stream().noneMatch(r->r.actual.parent==entry.module()
                &&r.actual.registry==call.root&&r.actual.thread==call.thread&&r.actual.before.manualDisableTo(r.after,r.actual.parent)))return false;
            return true;
        }
        public boolean disabledOwnerFromStart(Module exact){return current()&&call.before.enabledParent(exact)
            &&call.returns.stream().anyMatch(r->r.actual.parent==exact&&r.actual.registry==call.root&&r.actual.thread==call.thread
                &&r.actual.before.manualDisableTo(r.after,r.actual.parent));}
    }
    /** Real closed normal manual-stop body; distinct from legacy all-disabled ManualStopReceipt.current(). */
    public static final class OwnNoGameplayStopReceipt {
        private final ManualStopInvocation call;private final LifecycleSnapshot after;
        private OwnNoGameplayStopReceipt(ManualStopInvocation call,LifecycleSnapshot after){this.call=call;this.after=after;}
        public boolean matches(ManualStopStart start,dev.veltraph.client.VeltraphBootstrap.RootPrivatePassiveSource roles){
            if(start==null||start.call!=call||roles==null||!call.closed||!call.normal||call.revoked||call.root.manualStopInvocation==call
                ||Thread.currentThread()!=call.thread||call.registration==null||!call.registration.current()||call.root.stopCallbackExhausted
                ||call.callbackVersion!=call.root.stopCallbackVersion||call.beforeAll!=call.root.beforeStopAll||call.beforeEach!=call.root.beforeDisable
                ||!call.before.sameRoster(after)||!after.currentStored(call.root)||!roles.originalRoleHistoryCurrent(call.root))return false;
            for(int i=0;i<call.before.entries.size();i++){
                var before=call.before.entries.get(i);var now=after.entries.get(i);
                if(now.stamp().enabled()&&!roles.declaredActor(now.module()))return false;
                if(before.stamp().same(now.stamp()))continue;
                boolean returned=false;
                for(var actual:call.returns)if(actual.actual.parent==before.module()&&actual.actual.before.entries.get(i).stamp().same(before.stamp())
                        &&actual.actual.before.manualDisableTo(actual.after,actual.actual.parent)&&actual.after.entries.get(i).stamp().same(now.stamp())){returned=true;break;}
                if(!returned)return false;
            }
            return true;
        }
        public boolean disabledOwnerFromStart(ManualStopStart start,dev.veltraph.client.VeltraphBootstrap.RootPrivatePassiveSource roles,Module exact){
            return matches(start,roles)&&call.before.enabledParent(exact)&&call.returns.stream().anyMatch(r->r.actual.parent==exact
                &&r.actual.registry==call.root&&r.actual.thread==call.thread&&r.actual.before.manualDisableTo(r.after,r.actual.parent));
        }
        public LifecycleSnapshot normalReturnedLifecycle(ManualStopStart start,dev.veltraph.client.VeltraphBootstrap.RootPrivatePassiveSource roles){return matches(start,roles)?after:null;}
    }
    private ManualDisableInstruction manualDisableInstruction;
    /** Privately selected original safeDisable/setEnabled instruction, after beforeDisable has returned. */
    public static final class ManualDisableInstruction {
        private final ModuleManager root;private final ManualStopInvocation stop;private final Module module;private final Thread thread=Thread.currentThread();
        private ActualDisableStart actual;private boolean revoked,normal,closed;
        private ManualDisableInstruction(ModuleManager root,Module module){this.root=root;this.module=module;stop=root.manualStopInvocation;}
        public boolean currentFor(ActualDisableStart exact){return !revoked&&!closed&&root.manualDisableInstruction==this&&Thread.currentThread()==thread
            &&stop!=null&&!stop.revoked&&root.manualStopInvocation==stop&&actual==exact&&exact!=null&&exact.parent()==module;}
    }
    private void disableFromOriginalManualInstruction(Module module){
        ManualDisableInstruction actual=null;
        try{if(manualDisableInstruction!=null)manualDisableInstruction.revoked=true;else{actual=new ManualDisableInstruction(this,module);manualDisableInstruction=actual;}}
        catch(RuntimeException|Error unavailable){}
        try{module.setEnabled(false);if(actual!=null)actual.normal=true;}
        finally{if(actual!=null){actual.closed=true;if(!actual.normal&&actual.stop!=null)actual.stop.revoked=true;if(manualDisableInstruction==actual)manualDisableInstruction=null;}}
    }
    private ManualStopInvocation beginManualStop(){var previous=manualStopInvocation;if(previous!=null)previous.revoked=true;
        ManualStopInvocation call;try{call=new ManualStopInvocation(this,previous);}catch(RuntimeException unavailable){return null;}manualStopInvocation=call;
        if(call.registration!=null&&!call.revoked)try{call.captured=call.registration.observer.before(call.start);}catch(RuntimeException|Error unavailable){call.captured=null;}
        return call;
    }
    private void finishManualStop(ManualStopInvocation call,boolean normal){
        if(call==null)return;
        OwnNoGameplayStopReceipt closed=null;
        try{call.normal=normal;if(!normal)call.revoked=true;var after=lifecycleSnapshot();call.receipt=new ManualStopReceipt(call,after);
            // Existing all-disabled callback order and original failure behavior are preserved.
            if(call.receipt.current()&&call.registration!=null&&call.captured!=null)try{call.registration.observer.after(call.receipt,call.captured);}catch(RuntimeException|Error unavailable){}
            if(call.normal&&!call.revoked)try{closed=new OwnNoGameplayStopReceipt(call,after);}catch(RuntimeException|Error unavailable){}
        }catch(RuntimeException unavailable){call.revoked=true;}finally{call.closed=true;if(manualStopInvocation==call)manualStopInvocation=call.previous;}
        if(closed!=null&&call.registration!=null&&call.registration.current()&&call.captured!=null)
            try{call.registration.observer.closedNoGameplayStop(closed,call.captured);}catch(RuntimeException|Error unavailable){}
    }
    private OwnDisableReceipt lastConfirmedOwnDisable;
    static final class OwnDisableInvocation {
        final ModuleManager registry;final AbstractModule parent;final LifecycleSnapshot before;final Thread thread;
        boolean revoked,manualNormalReturned;OwnDisableReceipt receipt;ManualDisableInstruction manualInstruction;PendingOwnDisable pending;final ActualDisableStart start;final ActualDisableRegistration registration;Object captured;
        private OwnDisableInvocation(ModuleManager registry,AbstractModule parent,LifecycleSnapshot before){this.registry=registry;this.parent=parent;this.before=before;thread=Thread.currentThread();registration=registry.actualDisableRegistration;ActualDisableStart observed=null;try{observed=new ActualDisableStart(this);}catch(RuntimeException|Error unavailable){}start=observed;}
    }
    public Module actualOwnDisableModule(){var actual=ownDisableInvocation;return actual!=null&&!actual.revoked&&Thread.currentThread()==actual.thread?actual.parent:null;}
    /** Actual in-progress invocation observation only; no publication or normal-return permission. */
    public static final class PendingOwnDisable {
        private final OwnDisableInvocation invocation;
        private PendingOwnDisable(OwnDisableInvocation invocation){this.invocation=invocation;}
        public boolean current(Module exactParent){return invocation.parent==exactParent
            &&invocation.registry.pendingOwnDisableFromMatches(invocation.before,exactParent);}
        public ModuleManager registryIdentity(){return invocation.registry;}
    }
    /** Cannot be minted by public onDisable, booleans, versions, or a supplied lifecycle snapshot. */
    public Optional<PendingOwnDisable> pendingActualOwnDisable(Module exactParent){
        var call=ownDisableInvocation;
        if(call==null||!pendingOwnDisableFromMatches(call.before,exactParent))return Optional.empty();
        if(call.pending==null)call.pending=new PendingOwnDisable(call);
        return Optional.of(call.pending);
    }
    public static final class OwnDisableReceipt {
        private final OwnDisableInvocation invocation;private final LifecycleSnapshot stopped;private boolean closed,successful;
        private OwnDisableReceipt(OwnDisableInvocation invocation,LifecycleSnapshot stopped){this.invocation=invocation;this.stopped=stopped;}
        public Module parent(){return invocation.parent;}
        public ActualDisableStart actualStart(){return invocation.start;}
        public boolean closedCurrent(){return current()&&invocation.registry.ownDisableInvocation!=invocation;}
        /** Actual original onDisable/state/log already returned; the read-only post-return hook is in progress. */
        public boolean returnedCurrent(){return invocation.registry.ownDisableInvocation==invocation&&!closed&&!invocation.revoked
            &&Thread.currentThread()==invocation.thread&&invocation.registry.lifecycleCurrent(stopped);}
        public boolean current(){return closed&&successful&&!invocation.revoked&&Thread.currentThread()==invocation.thread&&invocation.registry.lifecycleCurrent(stopped);}
        public LifecycleSnapshot stoppedSnapshot(){return returnedCurrent()||current()?stopped:null;}
    }
    OwnDisableInvocation beforeActualDisable(AbstractModule parent){
        if(manualStopInvocation!=null&&ownDisableInvocation!=null)manualStopInvocation.revoked=true;
        if(ownEnableInvocation!=null)ownEnableInvocation.revoked=true;
        if(ownDisableInvocation!=null){ownDisableInvocation.revoked=true;return null;}
        try {var before=lifecycleSnapshot();if(!before.known()||byId.get(parent.id())!=parent||!parent.storedLifecycle().enabled())return null;
            var invocation=new OwnDisableInvocation(this,parent,before);ownDisableInvocation=invocation;
            var issued=manualDisableInstruction;if(issued!=null&&!issued.revoked&&issued.module==parent&&issued.actual==null){issued.actual=invocation.start;invocation.manualInstruction=issued;}
            if(invocation.registration!=null&&invocation.start!=null)try{invocation.captured=invocation.registration.observer.before(invocation.start);}catch(RuntimeException|Error unavailable){invocation.captured=null;}
            return invocation;
        }catch(RuntimeException unavailable){return null;}
    }
    void enterDisableNormalReturned(OwnDisableInvocation invocation){
        if(invocation!=ownDisableInvocation||invocation.revoked)return;
        try {var stopped=lifecycleSnapshot();if(!invocation.before.ownDisableTo(stopped,invocation.parent)){invocation.revoked=true;return;}
            invocation.receipt=new OwnDisableReceipt(invocation,stopped);
        }catch(RuntimeException unavailable){invocation.revoked=true;}
    }
    void finishActualDisable(OwnDisableInvocation invocation,boolean successful){
        try {if(invocation.receipt!=null){var receipt=invocation.receipt;receipt.successful=successful&&!invocation.revoked&&lifecycleCurrent(receipt.stopped);receipt.closed=true;if(receipt.successful)lastConfirmedOwnDisable=receipt;}
            if(!successful)invocation.revoked=true;
        }catch(RuntimeException unavailable){invocation.revoked=true;if(invocation.receipt!=null){invocation.receipt.successful=false;invocation.receipt.closed=true;}}
        finally{invocation.manualNormalReturned=successful;if(ownDisableInvocation==invocation)ownDisableInvocation=null;}
        try{var receipt=invocation.receipt;
            if(receipt!=null&&receipt.closedCurrent()&&invocation.registration!=null&&invocation.registration.current()&&invocation.captured!=null)
                invocation.registration.observer.after(receipt,invocation.captured);
        }catch(RuntimeException|Error unavailable){}
        finally{if(invocation.registration!=null&&invocation.captured!=null)
            try{invocation.registration.observer.closed(invocation.start,invocation.captured);}catch(RuntimeException|Error unavailable){}}
    }
    public Optional<OwnDisableReceipt> currentOwnDisableReceipt(Module exactParent){
        var invocation=ownDisableInvocation;var receipt=invocation==null?null:invocation.receipt;
        return invocation!=null&&invocation.parent==exactParent&&receipt!=null&&receipt.returnedCurrent()?Optional.of(receipt):Optional.empty();
    }
    /** Stable private normal-return confirmation, not a claim that an old disabled state is still current after re-enable. */
    public boolean ownDisableMatches(OwnDisableReceipt receipt,LifecycleSnapshot actualStopped,Module exactParent){
        return receipt!=null&&receipt.invocation.registry==this&&receipt.invocation.parent==exactParent&&receipt.closed&&receipt.successful&&!receipt.invocation.revoked
            &&Thread.currentThread()==receipt.invocation.thread&&actualStopped!=null&&receipt.stopped.same(actualStopped);
    }
    /** Read-only original actual setEnabled(false) invocation; no callback/role/input provider is evaluated. */
    public boolean pendingOwnDisableFromMatches(LifecycleSnapshot originalEnabled,Module exactParent){
        var call=ownDisableInvocation;
        return originalEnabled!=null&&exactParent!=null&&call!=null&&call.registry==this&&call.parent==exactParent&&!call.revoked
            &&Thread.currentThread()==call.thread&&originalEnabled.same(call.before)&&call.before.ownDisableInProgressTo(lifecycleSnapshot(),call.parent);
    }
    /** Only unchanged original lifecycle or its authentic exact pending/confirmed disable can retain cleanup. */
    public boolean ownCleanupLifecycleCurrent(LifecycleSnapshot originalEnabled,Module exactParent){
        if(originalEnabled==null||exactParent==null||!originalEnabled.enabledParent(exactParent))return false;
        if(lifecycleCurrent(originalEnabled))return true;
        if(pendingOwnDisableFromMatches(originalEnabled,exactParent))return true;
        var confirmed=lastConfirmedOwnDisable;
        return confirmed!=null&&confirmed.invocation.registry==this&&confirmed.invocation.parent==exactParent
            &&originalEnabled.same(confirmed.invocation.before)&&confirmed.current();
    }
    /** Exact original lifecycle and one normally completed actual disable, with every other stored actor unchanged.
     * In-progress cleanup and an arbitrary disabled state never return this private receipt. */
    public Optional<OwnDisableReceipt> completedOwnDisableFrom(LifecycleSnapshot originalEnabled,Module exactParent){
        var confirmed=lastConfirmedOwnDisable;
        return confirmed!=null&&confirmed.invocation.registry==this&&confirmed.invocation.parent==exactParent
            &&originalEnabled!=null&&originalEnabled.same(confirmed.invocation.before)&&confirmed.current()
            ?Optional.of(confirmed):Optional.empty();
    }
    /** Exact old enabled actor and normal-completed disable are both required; supplied versions/Booleans cannot certify this. */
    public boolean ownDisableFromMatches(OwnDisableReceipt receipt,LifecycleSnapshot actualBefore,LifecycleSnapshot actualStopped,Module exactParent){
        return ownDisableMatches(receipt,actualStopped,exactParent)&&actualBefore!=null
                &&actualBefore.same(receipt.invocation.before)&&receipt.current();
    }
    private record LifecycleEntry(String registrationId,Module module,AbstractModule.LifecycleStamp stamp){}
    public static final class LifecycleSnapshot {
        private final ModuleManager registry;private final long registrationVersion,inputBindingVersion;
        private final Object inputSupplier;private final List<LifecycleEntry> entries;private final boolean known;
        private LifecycleSnapshot(ModuleManager registry,long registrationVersion,long inputBindingVersion,Object inputSupplier,List<LifecycleEntry> entries,boolean known){this.registry=registry;this.registrationVersion=registrationVersion;this.inputBindingVersion=inputBindingVersion;this.inputSupplier=inputSupplier;this.entries=entries;this.known=known;}
        public boolean known(){return known;}
        private boolean sameRoster(LifecycleSnapshot other){
            if(!known||other==null||!other.known||registry!=other.registry||registrationVersion!=other.registrationVersion||inputBindingVersion!=other.inputBindingVersion||inputSupplier!=other.inputSupplier||entries.size()!=other.entries.size())return false;
            for(int i=0;i<entries.size();i++){var a=entries.get(i);var b=other.entries.get(i);if(!a.registrationId().equals(b.registrationId())||a.module()!=b.module())return false;}return true;
        }
        public boolean noneEnabledCurrent(ModuleManager root){if(!currentStored(root))return false;for(var entry:entries)if(entry.stamp().enabled())return false;return true;}
        private boolean manualDisableTo(LifecycleSnapshot other,AbstractModule parent){
            if(!sameRoster(other))return false;boolean found=false;
            for(int i=0;i<entries.size();i++){var a=entries.get(i);var b=other.entries.get(i);
                if(a.module()==parent){if(found||!a.stamp().manualDisableTo(b.stamp()))return false;found=true;}
                else if(!a.stamp().same(b.stamp()))return false;}return found;
        }
        private boolean ownEnableTo(LifecycleSnapshot other,AbstractModule parent){
            if(!known||other==null||!other.known||registry!=other.registry||registrationVersion!=other.registrationVersion||inputBindingVersion!=other.inputBindingVersion||inputSupplier!=other.inputSupplier||entries.size()!=other.entries.size())return false;
            boolean found=false;
            for(int i=0;i<entries.size();i++){var a=entries.get(i);var b=other.entries.get(i);if(!a.registrationId().equals(b.registrationId())||a.module()!=b.module())return false;
                if(a.module()==parent){if(found||!a.stamp().ownEnableTo(b.stamp()))return false;found=true;}else if(!a.stamp().same(b.stamp()))return false;}
            return found;
        }
        private boolean enabledParent(Module expected){
            if(!known||registry==null||expected==null)return false;
            for(var entry:entries)if(entry.module()==expected)return entry.stamp()!=null&&entry.stamp().known()&&entry.stamp().enabled();
            return false;
        }
        /** Exact private one-transition state while original onDisable or its normal-return hook executes. */
        private boolean ownDisableInProgressTo(LifecycleSnapshot other,AbstractModule parent){
            if(!known||other==null||!other.known||registry!=other.registry||registrationVersion!=other.registrationVersion||inputBindingVersion!=other.inputBindingVersion||inputSupplier!=other.inputSupplier||entries.size()!=other.entries.size())return false;
            boolean found=false;
            for(int i=0;i<entries.size();i++){var a=entries.get(i);var b=other.entries.get(i);
                if(!a.registrationId().equals(b.registrationId())||a.module()!=b.module())return false;
                if(a.module()==parent){if(found||!a.stamp().ownDisableInProgressTo(b.stamp()))return false;found=true;}
                else if(!a.stamp().same(b.stamp()))return false;
            }
            return found;
        }
        private boolean ownDisableTo(LifecycleSnapshot other,AbstractModule parent){
            if(!known||other==null||!other.known||registry!=other.registry||registrationVersion!=other.registrationVersion||inputBindingVersion!=other.inputBindingVersion||inputSupplier!=other.inputSupplier||entries.size()!=other.entries.size())return false;
            boolean found=false;
            for(int i=0;i<entries.size();i++){var a=entries.get(i);var b=other.entries.get(i);if(!a.registrationId().equals(b.registrationId())||a.module()!=b.module())return false;
                if(a.module()==parent){if(found||!a.stamp().ownDisableTo(b.stamp()))return false;found=true;}else if(!a.stamp().same(b.stamp()))return false;}
            return found;
        }
        private boolean parentEnableHistoryCurrent(ModuleManager expected,AbstractModule parent){
            if(!known||registry!=expected||expected.snapshotVersionExhausted||registrationVersion!=expected.registrationVersion
                ||inputBindingVersion!=expected.inputBindingVersion||inputSupplier!=expected.leasedInputOwner||entries.size()!=expected.byId.size())return false;
            boolean found=false;int index=0;
            for(var entry:expected.byId.entrySet()){
                var captured=entries.get(index++);
                if(!captured.registrationId().equals(entry.getKey())||captured.module()!=entry.getValue()
                    ||!(entry.getValue() instanceof AbstractModule actual)||!actual.storedLifecycle().known())return false;
                if(actual==parent){if(found||!actual.storedEnableHistoryCurrent(captured.stamp()))return false;found=true;}
            }
            return found;
        }
        private boolean enabledHistoryCurrent(ModuleManager expected,AbstractModule parent){
            if(!known||registry!=expected||expected.snapshotVersionExhausted||registrationVersion!=expected.registrationVersion
                ||inputBindingVersion!=expected.inputBindingVersion||inputSupplier!=expected.leasedInputOwner||entries.size()!=expected.byId.size())return false;
            boolean found=false;int index=0;
            for(var entry:expected.byId.entrySet()){
                var captured=entries.get(index++);
                if(!captured.registrationId().equals(entry.getKey())||captured.module()!=entry.getValue()
                    ||!(entry.getValue() instanceof AbstractModule actual)||!actual.storedLifecycle().known())return false;
                if(actual==parent){if(found||!actual.storedEnableHistoryCurrent(captured.stamp()))return false;found=true;}
                else if(!actual.storedLifecycleCurrent(captured.stamp()))return false;
            }
            return found;
        }
        private boolean originalEnableLifetimeCurrent(ModuleManager expected,AbstractModule parent){
            if(!known||registry!=expected||parent==null)return false;
            // Cleanup follows only the original registered actor and enable lifetime.
            // Unrelated roster changes revoke admission, never the exact old token disposal.
            for(var row:entries)if(row.module()==parent)return expected.byId.get(row.registrationId())==parent&&parent.storedEnableLifetimeCurrent(row.stamp());
            return false;
        }
        private boolean scheduledPauseCurrent(ModuleManager expected,AbstractModule parent){
            if(!known||registry!=expected||parent==null||expected.snapshotVersionExhausted||registrationVersion!=expected.registrationVersion
                ||inputBindingVersion!=expected.inputBindingVersion||inputSupplier!=expected.leasedInputOwner||entries.size()!=expected.byId.size())return false;
            boolean found=false;int index=0;
            for(var entry:expected.byId.entrySet()){
                var captured=entries.get(index++);
                if(!captured.registrationId().equals(entry.getKey())||captured.module()!=entry.getValue()||!(entry.getValue() instanceof AbstractModule actual))return false;
                if(actual==parent){if(found||!captured.stamp().scheduledPauseTo(actual.storedLifecycle()))return false;found=true;}
                else if(!actual.storedLifecycleCurrent(captured.stamp()))return false;
            }
            return found;
        }
        /** Re-read every authentic private field; this computes freshness, never caches a permission result. */
        private boolean currentStored(ModuleManager expected){
            if(!known||registry!=expected||expected.snapshotVersionExhausted||registrationVersion!=expected.registrationVersion
                ||inputBindingVersion!=expected.inputBindingVersion||inputSupplier!=expected.leasedInputOwner||entries.size()!=expected.byId.size())return false;
            int index=0;
            for(var entry:expected.byId.entrySet()){
                var captured=entries.get(index++);
                if(!captured.registrationId().equals(entry.getKey())||captured.module()!=entry.getValue()
                    ||!(entry.getValue() instanceof AbstractModule actual)||!actual.storedLifecycleCurrent(captured.stamp()))return false;
            }
            return true;
        }
        private boolean same(LifecycleSnapshot other){
            if(!known||other==null||!other.known||registry!=other.registry||registrationVersion!=other.registrationVersion||inputBindingVersion!=other.inputBindingVersion||inputSupplier!=other.inputSupplier||entries.size()!=other.entries.size())return false;
            for(int i=0;i<entries.size();i++){var a=entries.get(i);var b=other.entries.get(i);if(!a.registrationId().equals(b.registrationId())||a.module()!=b.module()||!a.stamp().same(b.stamp()))return false;}
            return true;
        }
    }

    public Optional<Module> get(String id) {
        return Optional.ofNullable(byId.get(id));
    }

    public Collection<Module> all() {
        return List.copyOf(byId.values());
    }

    /** Data-only reason for this fresh scan, never a lifecycle/input/publication receipt. */
    public enum UniqueOwnerStatus { NONE, OWNER, AMBIGUOUS, WARP_REFUSED }
    public record UniqueOwnerScan(UniqueOwnerStatus status,Module owner) {}
    /** Complete original registry-reference snapshot BEFORE every role/warp callback. A nested
     * call owns another scratch depth; no mutable snapshot escapes and no roster/result is cached.
     * Public all(), ordinary inputOwner() and their exact semantics remain unchanged. */
    public UniqueOwnerScan scanUniqueGameplayOwner(java.util.function.Predicate<Module> warpRefused){
        java.util.Objects.requireNonNull(warpRefused);
        var scratch=controllerScratch.get();var snapshot=scratch.borrow();
        try {
            for(Module module:byId.values())snapshot.add(module);
            // List.copyOf originally validates all references before entering Root's role loop.
            // Preserve that null rejection before ANY getter, after complete reference capture.
            for(int controllerIndex=0;controllerIndex<snapshot.size();controllerIndex++)java.util.Objects.requireNonNull(snapshot.get(controllerIndex));
            Module owner=null;
            for(int controllerIndex=0;controllerIndex<snapshot.size();controllerIndex++){
                Module module=snapshot.get(controllerIndex);
                if(!module.isEnabled()||module.isPassiveObserver()||!module.ownsGameplayInput())continue;
                if(owner!=null)return new UniqueOwnerScan(UniqueOwnerStatus.AMBIGUOUS,null);
                if(warpRefused.test(module))return new UniqueOwnerScan(UniqueOwnerStatus.WARP_REFUSED,null);
                owner=module;
            }
            return new UniqueOwnerScan(owner==null?UniqueOwnerStatus.NONE:UniqueOwnerStatus.OWNER,owner);
        }finally{scratch.release(snapshot);}
    }

    public List<Module> byCategory(ModuleCategory category) {
        List<Module> out = new ArrayList<>();
        for (Module m : byId.values()) {
            if (m.category() == category) {
                out.add(m);
            }
        }
        return out;
    }

    public List<Module> enabledModules() {
        List<Module> out = new ArrayList<>();
        for (Module m : byId.values()) {
            if (m.isEnabled()) {
                out.add(m);
            }
        }
        return out;
    }

    public List<Module> enabledControllers() {
        return enabledModules().stream().filter(module->!module.isPassiveObserver()).toList();
    }

    /** Fresh complete enumeration, preserving enabledControllers' two-phase getter order. */
    public int enabledControllerCount() {
        var scratch = controllerScratch.get();
        var enabled = scratch.borrow();
        try {
            collectEnabledControllersSnapshot(enabled);
            int count = 0;
            for (int controllerIndex=0;controllerIndex<enabled.size();controllerIndex++) {
                Module module=enabled.get(controllerIndex);
                if (!module.isPassiveObserver()) count++;
            }
            return count;
        } finally {
            scratch.release(enabled);
        }
    }

    /** Scans every enabled reference's role even after finding the first controller. */
    public Module firstEnabledController() {
        var scratch = controllerScratch.get();
        var enabled = scratch.borrow();
        try {
            collectEnabledControllersSnapshot(enabled);
            Module first = null;
            for (int controllerIndex=0;controllerIndex<enabled.size();controllerIndex++) {
                Module module=enabled.get(controllerIndex);
                boolean passive = module.isPassiveObserver();
                if (!passive && first == null) first = module;
            }
            if (first == null) throw new NoSuchElementException();
            return first;
        } finally {
            scratch.release(enabled);
        }
    }

    private void collectEnabledControllersSnapshot(List<Module> enabled) {
        for (Module module : byId.values()) {
            if (module.isEnabled()) enabled.add(module);
        }
    }

    /** Each reentrant depth owns a separate list; release clears every retained module reference. */
    private static final class ControllerScratch {
        private final ArrayList<ArrayList<Module>> lists = new ArrayList<>();
        private int depth;

        ArrayList<Module> borrow() {
            if (depth == lists.size()) lists.add(new ArrayList<>());
            var enabled = lists.get(depth);
            depth++;
            return enabled;
        }

        void release(ArrayList<Module> enabled) {
            enabled.clear();
            depth--;
        }
    }

    public void stopControllers() {
        var manual=beginManualStop();boolean normal=false;
        try {
        beforeStopAll.run();
        for(Module module:enabledControllers()) {
            safeDisable(module);
        }
    
            normal=true;
        }finally{finishManualStop(manual,normal);}
    }

    public void maintenanceTick() {
        for(Module module:byId.values()) {
            try { module.maintenanceTick(); }
            catch(Throwable error) {
                LOGGER.log(Level.WARNING,"Owned-item cleanup failed: "+module.id(),error);
                if(module.isEnabled())safeDisable(module);
            }
        }
    }

    public boolean enable(String id) {
        return setEnabled(id, true);
    }

    public boolean disable(String id) {
        return setEnabled(id, false);
    }

    public boolean toggle(String id) {
        Module m = byId.get(id);
        if (m == null) {
            return false;
        }
        boolean enable=!m.isEnabled();
        if(!enable){var manual=beginManualStop();boolean normal=false;try{beforeDisable.accept(m);disableFromOriginalManualInstruction(m);normal=true;}finally{finishManualStop(manual,normal);}}
        else m.setEnabled(true);
        return true;
    }

    private boolean setEnabled(String id, boolean enabled) {
        Module m = byId.get(id);
        if (m == null) {
            return false;
        }
        if(!enabled&&m.isEnabled()){var manual=beginManualStop();boolean normal=false;try{beforeDisable.accept(m);disableFromOriginalManualInstruction(m);normal=true;}finally{finishManualStop(manual,normal);}}
        else m.setEnabled(enabled);
        return true;
    }

    private ActualModuleTick actualModuleTick;
    public interface ActualModuleTickObserver {Object before(ModuleTickDeclaration actual);void after(ModuleTickDeclaration actual,Object captured);void closed(ModuleTickDeclaration actual,Object captured);}
    public static final class ActualModuleTickRegistration {
        private final ModuleManager registry;private final ActualModuleTickObserver observer;
        private ActualModuleTickRegistration(ModuleManager registry,ActualModuleTickObserver observer){this.registry=registry;this.observer=observer;}
        public boolean current(){return registry.actualModuleTickRegistration==this;}
    }
    private ActualModuleTickRegistration actualModuleTickRegistration;
    public ActualModuleTickRegistration observeActualModuleTicks(ActualModuleTickObserver observer){return actualModuleTickRegistration=new ActualModuleTickRegistration(this,java.util.Objects.requireNonNull(observer));}
    private static final class ActualModuleTick {
        final ModuleManager registry;final Module module;final LifecycleSnapshot before;final Thread thread=Thread.currentThread();boolean revoked,closed,normal;LifecycleSnapshot after;
        final ModuleTickDeclaration declaration;final ActualModuleTickRegistration registration;Object captured;
        private ActualModuleTick(ModuleManager registry,Module module,LifecycleSnapshot before){this.registry=registry;this.module=module;this.before=before;declaration=new ModuleTickDeclaration(this);registration=registry.actualModuleTickRegistration;}
    }
    public static final class ModuleTickDeclaration {
        private final ActualModuleTick actual;private LifecycleSnapshot continuedLifecycle;
        private ModuleTickPassiveContinuation passiveContinuation;
        private ModuleTickDeclaration(ActualModuleTick actual){this.actual=actual;}
        private LifecycleSnapshot effectiveLifecycle(){return continuedLifecycle==null?actual.after:continuedLifecycle;}
        private boolean closedOriginalCurrent(Module exact){return actual.module==exact&&actual.closed&&actual.normal&&!actual.revoked
            &&Thread.currentThread()==actual.thread&&actual.after!=null&&actual.registration!=null&&actual.registration.current();}
        public ModuleTickPassiveContinuation capturePassiveContinuation(dev.veltraph.client.VeltraphBootstrap.RootPassiveTransition transition,Module exact){
            if(passiveContinuation!=null){passiveContinuation.revoked=true;return null;}
            if(transition==null||!closedOriginalCurrent(exact)||!normalReturnedCurrent(exact)
                ||transition.registryIdentity()!=actual.registry||!transition.sourceBeforeCurrent(net.minecraft.client.Minecraft.getInstance())
                ||!actual.registry.lifecycleSameHistory(effectiveLifecycle(),transition.beforeLifecycle()))return null;
            var next=new ModuleTickPassiveContinuation(this,transition,exact);passiveContinuation=next;return next.rawCurrent()?next:null;
        }
        public Module module(){return actual.module;}public ModuleManager registryIdentity(){return actual.registry;}
        public LifecycleSnapshot beforeSnapshot(){return actual.before;}
        public LifecycleSnapshot normalReturnedSnapshot(){return normalReturnedCurrent(actual.module)?actual.after:null;}
        public boolean issuingCurrent(Module exact){return actual.module==exact&&!actual.closed&&!actual.revoked&&Thread.currentThread()==actual.thread
            &&actual.registry.actualModuleTick==actual&&actual.before.known()&&actual.registry.lifecycleCurrent(actual.before);}
        public boolean normalReturnedCurrent(Module exact){return actual.module==exact&&actual.closed&&actual.normal&&!actual.revoked&&Thread.currentThread()==actual.thread
            &&actual.after!=null&&actual.registry.lifecycleCurrent(effectiveLifecycle());}
    }
    /** Continues only a closed original declaration; the original ActualModuleTick.after is never rewritten. */
    public static final class ModuleTickPassiveContinuation {
        private final ModuleTickDeclaration declaration;private final ActualModuleTick original;
        private final dev.veltraph.client.VeltraphBootstrap.RootPassiveTransition transition;
        private final Module module;private final LifecycleSnapshot before,originalAfter,originalProjection;
        private final Thread thread=Thread.currentThread();private LifecycleSnapshot after;
        private boolean prepared,claimed,revoked;
        private ModuleTickPassiveContinuation(ModuleTickDeclaration declaration,dev.veltraph.client.VeltraphBootstrap.RootPassiveTransition transition,Module module){
            this.declaration=declaration;original=declaration.actual;this.transition=transition;this.module=module;
            before=declaration.effectiveLifecycle();originalAfter=original.after;originalProjection=declaration.continuedLifecycle;
        }
        public boolean rawCurrent(){return !claimed&&!revoked&&Thread.currentThread()==thread&&declaration.passiveContinuation==this
            &&declaration.actual==original&&original.module==module&&original.after==originalAfter&&original.closed&&original.normal&&!original.revoked
            &&declaration.continuedLifecycle==originalProjection&&declaration.effectiveLifecycle()==before
            &&original.registration!=null&&original.registry.actualModuleTickRegistration==original.registration;}
        public boolean prepare(){
            if(prepared||!rawCurrent()){revoked=true;return false;}
            if(transition.registryIdentity()!=original.registry||!transition.normalReturnedCurrent(net.minecraft.client.Minecraft.getInstance())
                ||!original.registry.lifecycleSameHistory(before,transition.beforeLifecycle())){revoked=true;return false;}
            var next=transition.normalReturnedLifecycle();
            if(next==null||!next.known()||!original.registry.lifecycleCurrent(next)||!rawCurrent()
                ||!transition.normalReturnedCurrent(net.minecraft.client.Minecraft.getInstance())){revoked=true;return false;}
            after=next;prepared=true;return true;
        }
        public boolean commit(){
            if(!prepared||after==null||!transition.passiveDeclarationCommitWindowCurrent()||!rawCurrent()){revoked=true;return false;}
            claimed=true;declaration.continuedLifecycle=after;declaration.passiveContinuation=null;return true;
        }
    }
    public ModuleTickDeclaration currentModuleTickDeclaration(Module exact){var actual=actualModuleTick;return actual!=null&&actual.declaration.issuingCurrent(exact)?actual.declaration:null;}
    /** Negative inspection of the exact private frame around the original module callback. */
    public boolean actualModuleTickCurrent(Module exact){var actual=actualModuleTick;return actual!=null&&!actual.revoked&&actual.registry==this
        &&actual.module==exact&&Thread.currentThread()==actual.thread&&actual.before.known()&&lifecycleCurrent(actual.before);}
    public Module actualModuleTickModule(){var actual=actualModuleTick;return actual!=null&&actualModuleTickCurrent(actual.module)?actual.module:null;}
    private ActualModuleTick beginActualModuleTick(Module module){
        if(actualModuleTick!=null){actualModuleTick.revoked=true;return null;}
        try{var before=lifecycleSnapshot();if(!before.known()||!(module instanceof AbstractModule)||byId.get(module.id())!=module)return null;
            var actual=new ActualModuleTick(this,module,before);actualModuleTick=actual;
            if(actual.registration!=null)try{actual.captured=actual.registration.observer.before(actual.declaration);}catch(RuntimeException|Error unavailable){actual.captured=null;}
            return actual;
        }catch(RuntimeException|Error unavailable){return null;}
    }
    private void finishActualModuleTick(ActualModuleTick actual){if(actual!=null){
        try{if(actual.normal&&!actual.revoked)actual.after=lifecycleSnapshot();}catch(RuntimeException|Error unavailable){actual.revoked=true;}
        finally{actual.closed=true;if(actualModuleTick==actual)actualModuleTick=null;}
        try{if(actual.registration!=null&&actual.registration.current()&&actual.captured!=null){
            if(actual.declaration.normalReturnedCurrent(actual.module))actual.registration.observer.after(actual.declaration,actual.captured);
        }}catch(RuntimeException|Error unavailable){}
        finally{if(actual.registration!=null&&actual.captured!=null)try{actual.registration.observer.closed(actual.declaration,actual.captured);}catch(RuntimeException|Error unavailable){}}
    }}
    /** Update every enabled module once. Exceptions disable the offending module. */
    public void tick() {
        for (Module m : byId.values()) {
            if (!m.isEnabled()) {
                continue;
            }
            var actual=beginActualModuleTick(m);
            try {
                m.onTick();
                if(actual!=null)actual.normal=true;
            } catch (Throwable t) {
                LOGGER.log(Level.SEVERE, "Module '" + m.id() + "' threw during tick; disabling it.", t);
                safeDisable(m);
            }finally{finishActualModuleTick(actual);}
        }
    }

    /** Disable all enabled modules (releases their inputs). Safe to call at any time. */
    public void stopAll() {
        var manual=beginManualStop();boolean normal=false;
        try {
        try { beforeStopAll.run(); }
        catch (RuntimeException | Error originalFailure) {
            // Remain inside this genuine manual stop while unchanged original actors reach their
            // actual disable cleanup. Collected failure never certifies a normal stop or idle handout.
            retireOriginalActorsAfterBeforeAllFailure(manual,originalFailure);
            throw originalFailure;
        }
        for (Module m : byId.values()) {
            if (m.isEnabled()) {
                safeDisable(m);
            }
        }
    
            normal=true;
        }finally{finishManualStop(manual,normal);}
    }

    private void retireOriginalActorsAfterBeforeAllFailure(ManualStopInvocation manual,Throwable originalFailure) {
        if(manual==null||!manual.before.known())return;
        for(var entry:manual.before.entries) {
            Module original=entry.module();
            // Captured actor, original callbacks, same actual invocation/roster and only genuine
            // pending/normal disable transitions. A nested stop, callback replacement or ABA refuses.
            if(!entry.stamp().enabled()||!manual.start.nativeStopCurrent(original)
                    ||!originalActorLifecycleCurrent(manual.before,original))continue;
            try { manual.beforeEach.accept(original); }
            catch(RuntimeException | Error problem) { suppressCleanupFailure(originalFailure,problem); }
            // A throwing beforeDisable callback does not itself revoke unchanged old custody. The
            // separate actual private setter is attempted once, only after the source is checked again.
            if(!manual.start.nativeStopCurrent(original)||!originalActorLifecycleCurrent(manual.before,original))continue;
            try { disableFromOriginalManualInstruction(original); }
            catch(RuntimeException | Error problem) { suppressCleanupFailure(originalFailure,problem); }
        }
    }
    private static void suppressCleanupFailure(Throwable originalFailure,Throwable problem) {
        if(problem!=originalFailure)originalFailure.addSuppressed(problem);
    }

    private void safeDisable(Module m) {
        var manual=manualStopInvocation==null?beginManualStop():null;boolean normal=false;
        try {
            beforeDisable.accept(m);
            disableFromOriginalManualInstruction(m);normal=true;
        } catch (Throwable t) {
            if(manualStopInvocation!=null)manualStopInvocation.revoked=true;
            LOGGER.log(Level.SEVERE, "Module '" + m.id() + "' threw during disable.", t);
        }finally{if(manual!=null)finishManualStop(manual,normal);}
    }
}
