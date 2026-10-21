package dev.veltraph.client.core;

import dev.veltraph.client.game.GameArea;
import dev.veltraph.client.game.GameContext;
import dev.veltraph.client.game.GameContextService;
import dev.veltraph.client.service.ChatService;

import java.util.Optional;

/**
 * Shared activation gate used by the UI, keybinds, and commands. Enforces the recovered location
 * requirements before a module starts and, while running, stops a module whose location becomes
 * invalid (unless it has a documented travel/setup phase).
 *
 * <p>The rule itself ({@link #evaluate(Module, GameContext)}) is pure and unit-testable; the instance
 * methods apply it against the live {@link GameContextService} and emit a local chat error.
 */
public final class ModuleActivationGuard {

    private final ModuleManager manager;
    private final GameContextService context;
    private final ChatService chat;
    @FunctionalInterface public interface RunningLocationExemption {
        /** An existing controller may only be exempt while another service owns its suspended recovery. */
        boolean permits(Module module,GameContext context);
    }
    private RunningLocationExemption runningLocationExemption=(module,ctx)->false;
    @FunctionalInterface public interface ActivationConflictExemption {
        /** Admit only a prepared, identity-bound controller handoff; default remains exclusive. */
        boolean permits(Module running, Module starting);
    }
    private ActivationConflictExemption activationConflictExemption=(running,starting)->false;
    @FunctionalInterface public interface ActivationTransaction {
        boolean start(Module module,java.util.function.BooleanSupplier standardActivation);
    }
    private ActivationTransaction activationTransaction=(module,activation)->activation.getAsBoolean();
    private java.util.function.Function<Module,Optional<String>> externalControlBlocker=module->Optional.empty();
    private java.util.function.Function<Module,Optional<String>> standardReadiness=module->Optional.empty();
    private java.util.function.Consumer<Module> beforeActivation=module->{};
    private java.util.function.Consumer<Module> afterFreshStart=module->{};
    public void standardReadiness(java.util.function.Function<Module,Optional<String>> check){observeOriginalBindingAttempt();standardReadiness=java.util.Objects.requireNonNull(check);}
    public void beforeActivation(java.util.function.Consumer<Module> action){observeOriginalBindingAttempt();beforeActivation=java.util.Objects.requireNonNull(action);}
    public void afterFreshStart(java.util.function.Consumer<Module> action){observeOriginalBindingAttempt();afterFreshStart=java.util.Objects.requireNonNull(action);}
    /** Survival/failsafe owners outside the module registry must not be overwritten by activation. */
    public void externalControlBlocker(java.util.function.Function<Module,Optional<String>> blocker){observeOriginalBindingAttempt();externalControlBlocker=java.util.Objects.requireNonNull(blocker);}
    public void activationTransaction(ActivationTransaction transaction){observeOriginalBindingAttempt();activationTransaction=java.util.Objects.requireNonNull(transaction);}
    public void activationConflictExemption(ActivationConflictExemption exemption){observeOriginalBindingAttempt();
        activationConflictExemption=java.util.Objects.requireNonNull(exemption);
    }
    public void runningLocationExemption(RunningLocationExemption exemption){observeOriginalBindingAttempt();
        runningLocationExemption=java.util.Objects.requireNonNull(exemption);
    }

    public ModuleActivationGuard(ModuleManager manager, GameContextService context, ChatService chat) {
        this.manager = manager;
        this.context = context;
        this.chat = chat;
    }

    private long originalBindingVersion;private boolean originalBindingExhausted;
    private void observeOriginalBindingAttempt(){if(originalBindingVersion==Long.MAX_VALUE)originalBindingExhausted=true;else originalBindingVersion++;}
    public interface OriginalActivationObserver {Object beforeRequest(OriginalActivationStart actual);void beforeEnabled(OriginalActivationStart actual,Object captured);void afterEnabled(OriginalActivationStart actual,Object captured);default void closedRequest(OriginalActivationStart actual,Object captured){}}
    public static final class OriginalActivationRegistration {
        private final ModuleActivationGuard guard;private final OriginalActivationObserver observer;
        private OriginalActivationRegistration(ModuleActivationGuard guard,OriginalActivationObserver observer){this.guard=guard;this.observer=observer;}
        public boolean current(){return guard.originalActivationRegistration==this;}
    }
    private OriginalActivationRegistration originalActivationRegistration;private OriginalActivationStart originalActivationStart;
    private long installedOriginalBindingVersion=-1;private OriginalActivationRegistration installedOriginalRegistration;
    public OriginalActivationRegistration observeOriginalActivations(OriginalActivationObserver observer){observeOriginalBindingAttempt();return originalActivationRegistration=new OriginalActivationRegistration(this,java.util.Objects.requireNonNull(observer));}
    public void sealOriginalActivationSource(dev.veltraph.client.VeltraphBootstrap.RootOriginalControlStartup actual){
        if(actual!=null&&actual.actualGuardInstallationCapture(this)){installedOriginalBindingVersion=originalBindingVersion;installedOriginalRegistration=originalActivationRegistration;}
    }
    /** Actual public request, before providers, and actual private validated before-enabled stage. No caller can construct it. */
    public static final class OriginalActivationStart {
        private final ModuleActivationGuard guard;private final Module module;private final ModuleManager.LifecycleSnapshot before;
        private final OriginalActivationRegistration registration;private final Thread thread=Thread.currentThread();private final long version;
        private final OriginalActivationStart previous;private Object captured;private ModuleManager.OwnEnableReceipt normalEnable;private boolean revoked,validated,enabling,enableReturned,closed,requestReturnedNormally,requestSucceeded;
        private OriginalActivationStart(ModuleActivationGuard guard,Module module,OriginalActivationStart previous){this.guard=guard;this.module=module;this.previous=previous;
            before=guard.manager.lifecycleSnapshot();registration=guard.originalActivationRegistration;version=guard.originalBindingVersion;revoked=previous!=null;}
        public ModuleActivationGuard guardIdentity(){return guard;}public ModuleManager registryIdentity(){return guard.manager;}public Module module(){return module;}
        public ModuleManager.LifecycleSnapshot originalBeforeLifecycle(){return before;}
        public boolean startedFrom(ModuleManager.LifecycleSnapshot retained){return retained!=null&&guard.manager.lifecycleSameHistory(before,retained);}
        private boolean installedCurrent(){return !guard.originalBindingExhausted&&version==guard.originalBindingVersion
            &&version==guard.installedOriginalBindingVersion&&registration!=null&&registration==guard.installedOriginalRegistration&&registration.current();}
        /** Retained exact installation binding, including every same-value/failed hook replacement attempt. */
        public boolean originalBindingsCurrent(){return !revoked&&Thread.currentThread()==thread&&installedCurrent();}
        /** Actual pending private enable or its exact normally returned closed Guard source; lifecycle belongs to Core's retained chain. */
        public boolean originalEnableSourceCurrent(Module exact,ModuleManager.OwnEnableReceipt completed){
            if(module!=exact||!originalBindingsCurrent()||!validated)return false;
            if(enabling)return !closed&&!enableReturned&&guard.originalActivationStart==this;
            return enableReturned&&normalEnable!=null&&normalEnable==completed&&normalEnable.parent()==exact
                &&normalEnable.parentEnableHistoryCurrent()&&(closed?requestReturnedNormally&&requestSucceeded:guard.originalActivationStart==this);
        }
        public boolean requestCurrent(){return !closed&&!revoked&&guard.originalActivationStart==this&&Thread.currentThread()==thread&&installedCurrent();}
        public boolean entryCurrent(){return requestCurrent()&&!enableReturned&&guard.manager.lifecycleCurrent(before);}
        public boolean beforeEnabledCurrent(){return entryCurrent()&&validated&&enabling;}
        public boolean normalEnabledReturned(){return requestCurrent()&&validated&&!enabling&&enableReturned;}
        public boolean closedNormalEnableSourceCurrent(Module exact,ModuleManager.OwnEnableReceipt completed){return closed&&requestReturnedNormally&&requestSucceeded
            &&originalEnableSourceCurrent(exact,completed);}
        public boolean closedNormalEnabledReturned(){return closed&&requestReturnedNormally&&requestSucceeded&&!revoked&&validated&&!enabling&&enableReturned&&Thread.currentThread()==thread
            &&installedCurrent()&&normalEnable!=null&&normalEnable.current()&&normalEnable.parent()==module;}
    }
    public OriginalActivationStart originalActivationFor(Module exact){var request=originalActivationStart;return request!=null&&request.module==exact&&request.beforeEnabledCurrent()?request:null;}
    private OriginalActivationStart beginOriginalActivationRequest(Module module){
        var previous=originalActivationStart;if(previous!=null)previous.revoked=true;
        try{var registration=originalActivationRegistration;if(registration==null||registration!=installedOriginalRegistration||!registration.current())return null;
            var request=new OriginalActivationStart(this,module,previous);originalActivationStart=request;
            try{request.captured=registration.observer.beforeRequest(request);}catch(RuntimeException|Error unavailable){request.revoked=true;request.captured=null;}
            return request;
        }catch(RuntimeException|Error unavailable){return null;}
    }
    private void closeOriginalActivationRequest(OriginalActivationStart request,boolean normal,boolean succeeded){if(request!=null){
        request.requestReturnedNormally=normal;request.requestSucceeded=succeeded;request.closed=true;if(!normal)request.revoked=true;
        if(originalActivationStart==request)originalActivationStart=request.previous;
        if(request.captured!=null)try{request.registration.observer.closedRequest(request,request.captured);}catch(RuntimeException|Error unavailable){request.revoked=true;}
    }}
    private void observeOriginalBeforeEnabled(OriginalActivationStart request){if(request==null)return;request.validated=true;request.enabling=true;
        if(request.captured!=null&&request.registration.current())try{request.registration.observer.beforeEnabled(request,request.captured);}catch(RuntimeException|Error unavailable){request.revoked=true;}}
    private void observeOriginalEnabledReturned(OriginalActivationStart request){if(request==null)return;request.enabling=false;request.enableReturned=true;
        try{request.normalEnable=manager.completedOwnEnableFrom(request.before,request.module).orElse(null);}catch(RuntimeException|Error unavailable){request.revoked=true;}
        if(request.captured!=null&&request.registration.current())try{request.registration.observer.afterEnabled(request,request.captured);}catch(RuntimeException|Error unavailable){request.revoked=true;}}

    private ActualActivationTransactionCall actualActivationTransaction;
    /** The real installed transaction instruction, never an activation request/current-owner factory. */
    public static final class ActualActivationTransactionCall {
        private final ModuleActivationGuard guard;private final OriginalActivationStart request;private final Module module;
        private final ActivationTransaction callback;private final Thread thread=Thread.currentThread();private boolean revoked,closed,normal;
        private ActualActivationTransactionCall(ModuleActivationGuard guard,OriginalActivationStart request,Module module,ActivationTransaction callback){
            this.guard=guard;this.request=request;this.module=module;this.callback=callback;
        }
        public ModuleActivationGuard guardIdentity(){return guard;}public ModuleManager registryIdentity(){return guard.manager;}
        public Module moduleIdentity(){return module;}public OriginalActivationStart requestIdentity(){return request;}
        public ModuleManager.LifecycleSnapshot beforeLifecycle(){return request.originalBeforeLifecycle();}
        public OriginalActivationStart originalRequestIdentity(){return request;}
        public boolean currentFor(ModuleActivationGuard exactGuard,Module exactModule){return !closed&&!revoked&&guard==exactGuard&&module==exactModule
            &&Thread.currentThread()==thread&&guard.actualActivationTransaction==this&&guard.activationTransaction==callback
            &&guard.originalActivationStart==request&&request.module()==module&&request.requestCurrent()&&request.originalBindingsCurrent();}
        public boolean normalReturnedCurrent(){return closed&&normal&&!revoked&&Thread.currentThread()==thread&&guard.activationTransaction==callback&&request.originalBindingsCurrent();}
    }
    public ActualActivationTransactionCall actualActivationTransactionForRoot(Module exact){var actual=actualActivationTransaction;
        return actual!=null&&actual.currentFor(this,exact)?actual:null;}
    private boolean invokeOriginalActivationTransaction(Module module){
        var callback=activationTransaction;java.util.function.BooleanSupplier standard=()->startNormally(module);ActualActivationTransactionCall actual=null;
        try{var request=originalActivationStart;
            if(actualActivationTransaction!=null)actualActivationTransaction.revoked=true;
            else if(request!=null&&request.module()==module&&request.entryCurrent()){
                actual=new ActualActivationTransactionCall(this,request,module,callback);actualActivationTransaction=actual;
            }
        }catch(RuntimeException|Error unavailable){}
        try{boolean result=callback.start(module,standard);if(actual!=null)actual.normal=true;return result;}
        finally{if(actual!=null){actual.closed=true;if(!actual.normal)actual.revoked=true;if(actualActivationTransaction==actual)actualActivationTransaction=null;}}
    }

    /** Appended to a wrong-location error when the location could not be determined. */
    public static final String DEBUG_HINT = "Run /veltraphdebug location and save the output.";

    /**
     * Pure rule: returns a chat-ready error if {@code module} may not start in {@code ctx}, else empty.
     * Example: {@code "Veltraph: Wart/Crops requires the Garden. Current location: Hub."} When the
     * location is unknown, the message ends with {@link #DEBUG_HINT} so the tester can capture evidence.
     */
    public static Optional<String> evaluate(Module module, GameContext ctx) {
        if (module.requiresSkyBlock() && !ctx.inSkyBlock()) {
            return Optional.of(withHint("Veltraph: " + module.displayName()
                    + " requires Hypixel SkyBlock. Current location: " + describe(ctx) + ".", ctx));
        }
        Optional<GameArea> required = module.requiredArea();
        boolean ingressStart = !module.isEnabled() && ctx.onHypixel() && ctx.inSkyBlock()
                && ctx.area().isSkyBlock() && module.ingressStartAreas().contains(ctx.area());
        if (required.isPresent() && ctx.area() != required.get() && !ingressStart) {
            return Optional.of(withHint("Veltraph: " + module.displayName() + " requires the "
                    + required.get().displayName() + ". Current location: " + describe(ctx) + ".", ctx));
        }
        return Optional.empty();
    }

    /** True when the current location is undetermined, so the wrong-location error should add a hint. */
    public static boolean locationUnknown(GameContext ctx) {
        return !ctx.onHypixel() || ctx.area() == GameArea.UNKNOWN;
    }

    private static String withHint(String message, GameContext ctx) {
        return locationUnknown(ctx) ? message + " " + DEBUG_HINT : message;
    }

    /** Human-readable current-location phrase for error messages. */
    public static String describe(GameContext ctx) {
        if (!ctx.onHypixel()) {
            return "not on Hypixel";
        }
        if (!ctx.inSkyBlock()) {
            return ctx.area() == GameArea.UNKNOWN ? "not in SkyBlock" : "not in SkyBlock (" + ctx.area().displayName() + ")";
        }
        return ctx.area() == GameArea.UNKNOWN ? "Unknown" : ctx.area().displayName();
    }

    public boolean canStart(Module module) {
        return evaluate(module, context.current()).isEmpty();
    }

    /**
     * Toggle a module by id with gating: enabling is blocked (with a chat error) when the location is
     * wrong; disabling is always allowed.
     *
     * @return true if the module is enabled after the call
     */
    public boolean toggle(String id) {
        Module module = manager.get(id).orElse(null);
        if (module == null) {
            return false;
        }
        if (module.isEnabled()) {
            manager.disable(module.id());
            return false;
        }
        return start(module);
    }

    /**
     * Start a module if its location gate <i>and</i> its pre-start readiness check both pass;
     * otherwise emit the relevant chat error and do nothing.
     */
    public boolean start(Module module) {
        var original=beginOriginalActivationRequest(module);boolean normal=false,succeeded=false;
        try{succeeded=startRequested(module);normal=true;return succeeded;}
        finally{closeOriginalActivationRequest(original,normal,succeeded);}
    }
    private boolean startRequested(Module module){
        if(!module.isPassiveObserver()) {
            try {
                var blocked=externalControlBlocker.apply(module);
                if(blocked.isPresent()){chat.send(blocked.get());return false;}
            } catch(RuntimeException failure) {
                chat.send("Veltraph: control ownership could not be verified; macro was not started.");
                dev.veltraph.client.log.VeltraphLog.basic("ModuleActivationGuard","External control check failed: "+failure);
                return false;
            }
        }
        boolean alreadyEnabled=module.isEnabled();
        try{
            if(!alreadyEnabled && module instanceof dev.veltraph.client.rewarp.mc.FarmMaintenanceCoordinator.Participant participant){
                var notReady=participant.preReservationReadiness();
                if(notReady.isPresent()){chat.send(notReady.get());return false;}
            }
            if(!alreadyEnabled)beforeActivation.accept(module);
            boolean started=invokeOriginalActivationTransaction(module);
            if(started&&!alreadyEnabled&&module.isEnabled())afterFreshStart.accept(module);
            return started;
        }catch(RuntimeException error){
            try{manager.disable(module.id());}catch(RuntimeException cleanup){error.addSuppressed(cleanup);}
            chat.send("Veltraph: could not start "+module.displayName()+": "+error.getMessage());
            dev.veltraph.client.log.VeltraphLog.basic("ModuleActivationGuard","Activation hook failed: "+error);return false;
        }
    }

    private boolean startNormally(Module module) {
        GameContext ctx = context.current();
        dev.veltraph.client.log.VeltraphLog.verbose("ModuleActivationGuard.start",
                () -> module.displayName() + " gate check @ " + describe(ctx));
        Optional<String> reason = evaluate(module, ctx);
        if (reason.isPresent()) {
            dev.veltraph.client.log.VeltraphLog.basic("ModuleActivationGuard",
                    "blocked " + module.displayName() + " (location gate): " + reason.get());
            chat.send(reason.get());
            return false;
        }
        for (Module running : manager.enabledModules()) {
            if (!running.id().equals(module.id())&&!running.isPassiveObserver()&&!module.isPassiveObserver()
                    &&!activationConflictExemption.permits(running,module)) {
                String message = "Veltraph: stop " + running.displayName()
                        + " before starting " + module.displayName() + ".";
                dev.veltraph.client.log.VeltraphLog.basic("ModuleActivationGuard",
                        "blocked " + module.displayName() + " (another macro running): " + running.displayName());
                chat.send(message);
                return false;
            }
        }
        if(!module.isPassiveObserver())for(Module owner:manager.all()) {
            if(owner.hasPendingCleanup()) {
                chat.send("Veltraph: wait for "+owner.displayName()+" to return its owned item before starting "+module.displayName()+".");
                return false;
            }
        }
        try {
            var sharedNotReady=standardReadiness.apply(module);
            if(sharedNotReady.isPresent()){chat.send(sharedNotReady.get());return false;}
            Optional<String> notReady = module.preStartCheck();
            if (notReady.isPresent()) {
                dev.veltraph.client.log.VeltraphLog.basic("ModuleActivationGuard",
                        "blocked " + module.displayName() + " (pre-start): " + notReady.get());
                chat.send(notReady.get());
                return false;
            }
            var original=originalActivationStart;
            if(original!=null&&original.module==module)observeOriginalBeforeEnabled(original);
            module.setEnabled(true); // Original actual lifecycle operation; original exceptions/cleanup stay unchanged.
            if(original!=null&&original.module==module)observeOriginalEnabledReturned(original);
            return true;
        } catch (RuntimeException error) {
            try { module.setEnabled(false); } catch (RuntimeException cleanup) { error.addSuppressed(cleanup); }
            chat.send("Veltraph: could not start " + module.displayName() + ": " + error.getMessage());
            dev.veltraph.client.log.VeltraphLog.basic("ModuleActivationGuard", "Start failed: " + error);
            return false;
        }
    }

    /**
     * Stop any running module whose location is now invalid and which has no travel/setup phase.
     * Call once per client tick. Emits a chat notice for each module stopped.
     */
    public void enforceRunning() {
        GameContext ctx = context.current();
        for (Module module : manager.enabledModules()) {
            if (module.hasTravelSetupPhase()) {
                continue;
            }
            Optional<String> reason = evaluate(module, ctx);
            if (reason.isPresent()) {
                // This never changes activation rules or exemptions in unknown/limbo/non-SkyBlock.
                boolean exempt=false;
                if(ctx.onHypixel()&&ctx.inSkyBlock()&&ctx.area().isSkyBlock()
                        &&ctx.area()!=GameArea.UNKNOWN&&ctx.area()!=GameArea.LIMBO) {
                    try{exempt=runningLocationExemption.permits(module,ctx);}
                    catch(RuntimeException failure){dev.veltraph.client.log.VeltraphLog.basic("ModuleActivationGuard","Recovery exemption rejected: "+failure);}
                }
                if(exempt)continue;
                dev.veltraph.client.log.VeltraphLog.basic("ModuleActivationGuard",
                        "auto-stopped " + module.displayName() + " — location gate failed @ " + describe(ctx));
                manager.disable(module.id());
                chat.send("Veltraph: stopped " + module.displayName() + " — " + describe(ctx) + ".");
            }
        }
    }
}
