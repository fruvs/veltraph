package dev.veltraph.client.service.mc;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.ToggleKeyMapping;

/** Absolute script-held state for vanilla hold and toggle mappings, without changing preferences. */
public final class McHeldKeys {
    private McHeldKeys() {}
    public static void setHeld(KeyMapping key,boolean held) {
        if(McIssuedFarmResumeGuard.active()){McIssuedFarmResumeGuard.refuseUnscopedMutation();return;} // Issued hooks use only exact original neutral histories below.
        if(McIssuedFarmReturnCleanup.active()){
            var ledger=McIssuedFarmReturnCleanup.currentKeyHistory();if(ledger!=null)ledger.failure();return;
        }
        if(key==null)return;
        var client=net.minecraft.client.Minecraft.getInstance();KeyHistory observationBefore;
        try{observationBefore=KeyHistory.capture(key);}catch(RuntimeException unavailable){observationBefore=null;}
        var priorCanonical=CANONICAL_TOGGLE.get();if(priorCanonical!=null)priorCanonical.revoked=true;
        CanonicalToggleTrace canonical=null;
        try{if(priorCanonical==null&&held&&key.getClass()==ToggleKeyMapping.class&&observationBefore!=null)canonical=CanonicalToggleTrace.capture(client,key);}catch(RuntimeException|Error unavailable){}
        if(canonical!=null)CANONICAL_TOGGLE.set(canonical);
        try {
        var observed=dev.veltraph.client.observation.unexpected.UnexpectedActions.beginHeldPublication(client,key,held);int actualSetters=0;
        if(key.isDown()!=held) {
            actualSetters++;normalSetDown(key,held,canonical);
            // ToggleKeyMapping ignores false. A single true event then flips it to false.
            if(key.isDown()!=held){actualSetters++;normalSetDown(key,true,canonical);}
            if(key.isDown()!=held)throw new IllegalStateException("Key mapping refused absolute held state");
        }
        // A GUI release can already have cleared isDown while retaining a pending restoration.
        // An explicit script release must not be restored when that screen closes.
        if(!held&&key instanceof ToggleKeyMapping toggle)toggle.shouldRestoreStateOnScreenClosed();
        // Observation only. Canonical Toggle presses require the actual direct super edge and both normal returns.
        try{var after=KeyHistory.capture(key);CanonicalTogglePublication paired=canonical==null?null:canonical.finish(observationBefore,after,actualSetters);
        if(observed!=null&&actualSetters==1&&observationBefore!=null&&after!=null&&after.key==observationBefore.key&&after.carrier==observationBefore.carrier
                &&(key.getClass()==KeyMapping.class&&after.carrier.normalIdleUnscopedAdvanced(observationBefore.snapshot,after.snapshot)
                   ||key.getClass()==ToggleKeyMapping.class&&paired!=null&&after.carrier.normalIdleCanonicalToggleAdvanced(observationBefore.snapshot,after.snapshot,paired))
                &&after.current()&&key.isDown()==held)
            dev.veltraph.client.observation.unexpected.UnexpectedActions.completeHeldPublication(observed,new HeldPublicationReceipt(client,key,held,after,observed,paired));
        }catch(RuntimeException|Error unavailable){/* Passive observation cannot change an actual normal setter result. */}
        }finally{if(canonical!=null)CANONICAL_TOGGLE.remove();}
    }
    private static final ThreadLocal<CanonicalToggleTrace> CANONICAL_TOGGLE=new ThreadLocal<>();
    /** Private native event identity, separate from scoped input invocations and input authority. */
    public static final class CanonicalToggleEvent {
        private final CanonicalToggleTrace trace;private boolean modeStarted,modeReturned,mode,superStarted,superReturned,baseStarted,baseReturned,outerReturned;
        private CanonicalToggleEvent(CanonicalToggleTrace trace){this.trace=trace;}
    }
    private static final class CanonicalModeCall {final CanonicalToggleEvent event;private CanonicalModeCall(CanonicalToggleEvent event){this.event=event;}}
    private static final class CanonicalSuperCall {final CanonicalToggleEvent event;private CanonicalSuperCall(CanonicalToggleEvent event){this.event=event;}}
    private static final class CanonicalToggleTrace {
        final net.minecraft.client.Minecraft client;final KeyMapping key;final Thread thread;
        final dev.veltraph.client.movement.device.OwnedUseMappedKeyCarrier mapping;final Object originalMapping;
        final Object player,world,connection,options,nativeConnection;CanonicalToggleEvent event;boolean armed,normalCallReturned,revoked;
        private CanonicalToggleTrace(net.minecraft.client.Minecraft client,KeyMapping key,dev.veltraph.client.movement.device.OwnedUseMappedKeyCarrier mapping,Object originalMapping){
            this.client=client;this.key=key;this.mapping=mapping;this.originalMapping=originalMapping;thread=Thread.currentThread();player=client.player;world=client.level;connection=client.getConnection();options=client.options;nativeConnection=client.getConnection().getConnection();
        }
        static CanonicalToggleTrace capture(net.minecraft.client.Minecraft client,KeyMapping key){
            if(client==null||!client.isSameThread()||client.player==null||client.level==null||client.getConnection()==null||NATIVE_INPUT.get()!=null||key.isDown()
                    ||!(key instanceof dev.veltraph.client.movement.device.OwnedUseMappedKeyCarrier mapping)||!canonicalMapping(client,key))return null;
            var original=mapping.ownedUseMappingSnapshot();return original==null||!mapping.ownedUseMappingCurrent(original)?null:new CanonicalToggleTrace(client,key,mapping,original);
        }
        boolean current(){return !revoked&&Thread.currentThread()==thread&&client.isSameThread()&&key.getClass()==ToggleKeyMapping.class&&canonicalMapping(client,key)
                &&client.player==player&&client.level==world&&client.options==options&&client.getConnection()==connection&&client.getConnection().getConnection()==nativeConnection
                &&((net.minecraft.network.Connection)nativeConnection).isConnected()&&mapping.ownedUseMappingCurrent(originalMapping);}
        CanonicalTogglePublication finish(KeyHistory before,KeyHistory after,int setters){
            if(setters!=1||!normalCallReturned||armed||!current()||event==null||!event.outerReturned||!event.modeStarted||!event.modeReturned
                    ||!event.superStarted||!event.superReturned||!event.baseStarted||!event.baseReturned||before==null||after==null||before.carrier!=after.carrier||before.key!=key||after.key!=key||!key.isDown())return null;
            return new CanonicalTogglePublication(this,before.snapshot,after.snapshot,event);
        }
    }
    private static boolean canonicalMapping(net.minecraft.client.Minecraft c,KeyMapping key){var o=c.options;return o!=null&&(key==o.keyUp||key==o.keyDown||key==o.keyLeft||key==o.keyRight||key==o.keyJump||key==o.keyShift||key==o.keySprint||key==o.keyAttack||key==o.keyUse);}
    private static void normalSetDown(KeyMapping key,boolean requested,CanonicalToggleTrace trace){
        if(trace==null){key.setDown(requested);return;}
        try{if(trace.armed||trace.event!=null||!requested||!trace.current())trace.revoked=true;}catch(RuntimeException|Error unavailable){trace.revoked=true;}trace.armed=true;
        try{key.setDown(requested);trace.normalCallReturned=true;}finally{trace.armed=false;}
    }
    /** No public mint/getter. This certificate is accepted only for passive observation, never scoped input. */
    public static final class CanonicalTogglePublication {
        private final CanonicalToggleTrace trace;private final Object before,after;private final CanonicalToggleEvent event;
        private CanonicalTogglePublication(CanonicalToggleTrace trace,Object before,Object after,CanonicalToggleEvent event){this.trace=trace;this.before=before;this.after=after;this.event=event;}
        public boolean matchesNativeHistory(KeyMapping key,Object original,Object current){return key==trace.key&&original==before&&current==after&&trace.event==event&&event.outerReturned&&current();}
        public Object nativeEventIdentity(){return event;}
        private boolean current(){try{return trace.current();}catch(RuntimeException|Error unavailable){trace.revoked=true;return false;}}
    }
    private static CanonicalToggleTrace activeCanonical(KeyMapping key){var trace=CANONICAL_TOGGLE.get();if(trace==null)return null;if(trace.key!=key||NATIVE_INPUT.get()!=null||!trace.armed||!trace.current()){trace.revoked=true;return null;}return trace;}
    private static void recordCanonicalToggleOuterBegin(KeyMapping key,boolean requested){var trace=activeCanonical(key);if(trace==null)return;if(!requested||trace.event!=null||key.isDown()){trace.revoked=true;return;}trace.event=new CanonicalToggleEvent(trace);}
    private static void recordCanonicalToggleOuterReturned(KeyMapping key,boolean requested){var trace=activeCanonical(key);if(trace==null)return;var e=trace.event;if(!requested||e==null||e.outerReturned||!e.modeReturned||!e.superReturned||!e.baseReturned||!key.isDown()){trace.revoked=true;return;}e.outerReturned=true;}
    private static Object recordCanonicalToggleModeBegin(KeyMapping key){var trace=activeCanonical(key);if(trace==null)return null;var e=trace.event;if(e==null||e.modeStarted){trace.revoked=true;return null;}e.modeStarted=true;return new CanonicalModeCall(e);}
    private static void recordCanonicalToggleModeReturned(Object opaque,boolean mode,boolean normal){if(!(opaque instanceof CanonicalModeCall call))return;var e=call.event;var trace=activeCanonical(e.trace.key);if(trace!=e.trace||!normal||e.modeReturned||e.superStarted){e.trace.revoked=true;return;}e.mode=mode;e.modeReturned=true;}
    private static Object recordCanonicalToggleSuperBegin(KeyMapping key,boolean requested){var trace=activeCanonical(key);if(trace==null)return null;var e=trace.event;if(e==null||!e.modeReturned||e.superStarted||!requested||key.isDown()){trace.revoked=true;return null;}e.superStarted=true;return new CanonicalSuperCall(e);}
    private static void recordCanonicalToggleSuperReturned(Object opaque,boolean normal){if(!(opaque instanceof CanonicalSuperCall call))return;var e=call.event;var trace=activeCanonical(e.trace.key);if(trace!=e.trace||!normal||e.superReturned||!e.baseStarted||!e.baseReturned||!trace.key.isDown()){e.trace.revoked=true;return;}e.superReturned=true;}
    private static void recordCanonicalToggleBaseBegin(KeyMapping key,boolean requested){var trace=activeCanonical(key);if(trace==null)return;var e=trace.event;if(e==null||!e.modeReturned||!e.superStarted||e.superReturned||e.baseStarted||!requested||key.isDown()){trace.revoked=true;return;}e.baseStarted=true;}
    private static void recordCanonicalToggleBaseReturned(KeyMapping key,boolean requested){var trace=activeCanonical(key);if(trace==null)return;var e=trace.event;if(e==null||!e.baseStarted||e.baseReturned||!requested||!key.isDown()){trace.revoked=true;return;}e.baseReturned=true;}
    private static Object recordCanonicalToggleHistoryEvent(KeyMapping key,boolean requested,boolean restoration){var trace=activeCanonical(key);if(trace==null)return null;var e=trace.event;if(restoration||!requested||e==null){trace.revoked=true;return null;}return e;}
    private static void revokeCanonicalBookkeeping(){try{var trace=CANONICAL_TOGGLE.get();if(trace!=null)trace.revoked=true;}catch(RuntimeException|Error ignored){}}
    public static void canonicalToggleOuterBegin(KeyMapping key,boolean requested){try{recordCanonicalToggleOuterBegin(key,requested);}catch(RuntimeException|Error unavailable){revokeCanonicalBookkeeping();}}
    public static void canonicalToggleOuterReturned(KeyMapping key,boolean requested){try{recordCanonicalToggleOuterReturned(key,requested);}catch(RuntimeException|Error unavailable){revokeCanonicalBookkeeping();}}
    public static Object canonicalToggleModeBegin(KeyMapping key){try{return recordCanonicalToggleModeBegin(key);}catch(RuntimeException|Error unavailable){revokeCanonicalBookkeeping();return null;}}
    public static void canonicalToggleModeReturned(Object opaque,boolean mode,boolean normal){try{recordCanonicalToggleModeReturned(opaque,mode,normal);}catch(RuntimeException|Error unavailable){revokeCanonicalBookkeeping();}}
    public static Object canonicalToggleSuperBegin(KeyMapping key,boolean requested){try{return recordCanonicalToggleSuperBegin(key,requested);}catch(RuntimeException|Error unavailable){revokeCanonicalBookkeeping();return null;}}
    public static void canonicalToggleSuperReturned(Object opaque,boolean normal){try{recordCanonicalToggleSuperReturned(opaque,normal);}catch(RuntimeException|Error unavailable){revokeCanonicalBookkeeping();}}
    public static void canonicalToggleBaseBegin(KeyMapping key,boolean requested){try{recordCanonicalToggleBaseBegin(key,requested);}catch(RuntimeException|Error unavailable){revokeCanonicalBookkeeping();}}
    public static void canonicalToggleBaseReturned(KeyMapping key,boolean requested){try{recordCanonicalToggleBaseReturned(key,requested);}catch(RuntimeException|Error unavailable){revokeCanonicalBookkeeping();}}
    public static Object canonicalToggleHistoryEvent(KeyMapping key,boolean requested,boolean restoration){try{return recordCanonicalToggleHistoryEvent(key,requested,restoration);}catch(RuntimeException|Error unavailable){revokeCanonicalBookkeeping();return null;}}
    /** Privately minted only after an actual normal-return method; observation evidence, never input authority. */
    public static final class HeldPublicationReceipt {
        private final net.minecraft.client.Minecraft client;private final KeyMapping key;private final boolean held;private final KeyHistory after;
        private final Object player,world,connection,nativeConnection,options;
        private final dev.veltraph.client.observation.unexpected.UnexpectedActions.HeldPublicationObservation observed;
        private final CanonicalTogglePublication canonical;
        private HeldPublicationReceipt(net.minecraft.client.Minecraft client,KeyMapping key,boolean held,KeyHistory after,
                dev.veltraph.client.observation.unexpected.UnexpectedActions.HeldPublicationObservation observed){
            this(client,key,held,after,observed,null);
        }
        private HeldPublicationReceipt(net.minecraft.client.Minecraft client,KeyMapping key,boolean held,KeyHistory after,
                dev.veltraph.client.observation.unexpected.UnexpectedActions.HeldPublicationObservation observed,CanonicalTogglePublication canonical){
            this.client=client;this.key=key;this.held=held;this.after=after;this.observed=observed;this.canonical=canonical;
            player=client.player;world=client.level;connection=client.getConnection();nativeConnection=client.getConnection().getConnection();options=client.options;
        }
        public KeyMapping key(){return key;}public boolean held(){return held;}
        public boolean observedBy(dev.veltraph.client.observation.unexpected.UnexpectedActions.HeldPublicationObservation candidate){return observed!=null&&observed==candidate;}
        public boolean historyCurrent(){return client.isSameThread()&&client.player==player&&client.level==world&&client.getConnection()==connection
            &&client.options==options&&nativeConnection==client.getConnection().getConnection()&&((net.minecraft.network.Connection)nativeConnection).isConnected()
            &&key.isDown()==held&&after.current()&&(canonical==null||canonical.current());}
    }
    public enum ScopedStatus { CONFIRMED,REFUSED,FAILED }
    /** Actual native attempt accounting only; this opaque result grants no admission or restart. */
    public static final class ScopedHeldReceipt {
        private final ScopedStatus status;private final KeyMapping key;private final boolean held;private final int setters,restoration;private KeyHistory history;private java.util.List<NativeInputInvocation> actualHistory=java.util.List.of();
        private dev.veltraph.client.navigation.mc.LegacyTravelPublicationLease.Leg.ImmediateHeldAttempt immediateLegAttempt;
        private Object savedRestorationBinding;
        private boolean savedBindingCurrent(){return savedRestorationBinding==null||dev.veltraph.client.observation.nativeinput.NativeKeyBindingHistory.current(key,savedRestorationBinding);}
        private ScopedHeldReceipt(ScopedStatus status,KeyMapping key,boolean held,int setters,int restoration){this.status=status;this.key=key;this.held=held;this.setters=setters;this.restoration=restoration;}
        public ScopedStatus status(){return status;}public boolean confirmed(){return status==ScopedStatus.CONFIRMED;}
        public KeyMapping key(){return key;}public boolean held(){return held;}
        /** Identity-only private sink pairing; neither a copied receipt nor this Boolean supplies gameplay authority. */
        public boolean immediateHeldAttemptOf(dev.veltraph.client.navigation.mc.LegacyTravelPublicationLease.Leg.ImmediateHeldAttempt exact){return exact!=null&&immediateLegAttempt==exact;}
        public boolean normalIdleHistoryCurrent(){return history!=null&&history.current()&&savedBindingCurrent();}
        /** Passive actual event match only; the actual transaction retains its own original window and issued receipt. */
        public boolean normalIdleHistoryAdvancedFrom(Object originalSnapshot){return confirmed()&&history!=null&&history.current()&&savedBindingCurrent()
            &&history.carrier.normalIdleKeyAdvanced(originalSnapshot,history.snapshot,actualHistory);}
        /** Cleanup-only custody of actual claimed normally returned true setters, including a post-publication refusal. */
        public boolean normallyReturnedOwnedHeldAttempt(){return held&&history!=null&&history.current()&&savedBindingCurrent()&&actualHistory.stream().anyMatch(i->i.complete&&!i.restore&&i.requested);}
        /** Actual normally returned own false publication; a later refusal is never a successful gameplay receipt. */
        public boolean normallyReturnedOwnedReleaseAttempt(){return !held&&history!=null&&history.current()&&savedBindingCurrent()&&!key.isDown()&&actualHistory.stream().anyMatch(i->i.complete&&!i.restore);}
        Object historySnapshotForRollbackCleanup(){return history==null?null:history.snapshot;}
        KeyHistory historyForRollbackCleanup(){return normallyReturnedOwnedHeldAttempt()?history:null;}
        boolean ownNegativeHistoryAdvancedFrom(Object before){return !held&&history!=null&&history.current()&&savedBindingCurrent()&&!key.isDown()
            &&actualHistory.stream().allMatch(c->c.complete&&c.claimed&&!c.revoked)&&actualHistory.stream().anyMatch(c->!c.restore)
            &&history.carrier.normalIdleKeyAdvanced(before,history.snapshot,actualHistory);}
        public int nativeSetAttempts(){return setters;}public int restorationAttempts(){return restoration;}
    }
    /** Observe first; actual caller custody then native stored scope are checked immediately before each mutation. */
    public static ScopedHeldReceipt setHeldScoped(KeyMapping key,boolean held,java.util.function.BooleanSupplier refusalOnlyFence){
        if(!claimIssuedReturnKeyCall(key,held))return new ScopedHeldReceipt(ScopedStatus.REFUSED,key,held,0,0);
        return setHeldScopedInternal(key,held,refusalOnlyFence,null);
    }
    /** First owned publication retains the exact native key history observed before any callback. */
    public static ScopedHeldReceipt setHeldFromObservedScoped(KeyMapping key,boolean held,java.util.function.BooleanSupplier refusalOnlyFence){
        if(!claimIssuedReturnKeyCall(key,held))return new ScopedHeldReceipt(ScopedStatus.REFUSED,key,held,0,0);
        var original=KeyHistory.capture(key);
        if(original==null||!original.current())return new ScopedHeldReceipt(ScopedStatus.REFUSED,key,held,0,0);
        return setHeldScopedInternal(key,held,refusalOnlyFence,original,true);
    }
    /** Concrete service-private in-flight sink; never exposed as a caller callback or public receipt getter. */
    static ScopedHeldReceipt setHeldFromObservedScoped(KeyMapping key,boolean held,java.util.function.BooleanSupplier refusalOnlyFence,
            McBlockBreakingService.ImmediateHeldAttempt exactAttempt){
        if(!claimIssuedReturnKeyCall(key,held))return new ScopedHeldReceipt(ScopedStatus.REFUSED,key,held,0,0);
        var original=KeyHistory.capture(key);
        if(original==null||!original.current())return new ScopedHeldReceipt(ScopedStatus.REFUSED,key,held,0,0);
        return setHeldScopedInternal(key,held,refusalOnlyFence,original,true,exactAttempt);
    }
    /** Concrete Leg-private sink. Only the actual issued return pair mints its one in-flight attempt. */
    public static ScopedHeldReceipt setHeldFromObservedScoped(KeyMapping key,boolean held,java.util.function.BooleanSupplier refusalOnlyFence,
            dev.veltraph.client.navigation.mc.LegacyTravelPublicationLease.Leg.ImmediateHeldAttempt exactAttempt){
        if(!claimIssuedReturnKeyCall(key,held))return new ScopedHeldReceipt(ScopedStatus.REFUSED,key,held,0,0);
        if(!held||exactAttempt==null)return new ScopedHeldReceipt(ScopedStatus.REFUSED,key,held,0,0);
        var original=KeyHistory.capture(key);
        if(original==null||!original.current())return new ScopedHeldReceipt(ScopedStatus.REFUSED,key,held,0,0);
        return setHeldScopedInternal(key,true,refusalOnlyFence,original,true,null,exactAttempt);
    }
    /** Exact own release: original history plus only this call's privately claimed native transitions. */
    public static ScopedHeldReceipt releaseOwnedScoped(ScopedHeldReceipt original,java.util.function.BooleanSupplier refusalOnlyFence){
        if(!claimIssuedReturnKeyCall(original==null?null:original.key,false))return new ScopedHeldReceipt(ScopedStatus.REFUSED,original==null?null:original.key,false,0,0);
        if(original==null||!original.confirmed()||!original.held()||original.history==null||!original.history.current()||!original.savedBindingCurrent())
            return new ScopedHeldReceipt(ScopedStatus.REFUSED,original==null?null:original.key,false,0,0);
        return setHeldScopedInternal(original.key,false,refusalOnlyFence,original.history);
    }
    /** No gameplay confirmation is inferred from this cleanup-only attempted-native history. */
    public static ScopedHeldReceipt releaseAttemptedOwnedScoped(ScopedHeldReceipt original,java.util.function.BooleanSupplier refusalOnlyFence){
        if(!claimIssuedReturnKeyCall(original==null?null:original.key,false))return new ScopedHeldReceipt(ScopedStatus.REFUSED,original==null?null:original.key,false,0,0);
        if(original==null||!original.normallyReturnedOwnedHeldAttempt())
            return new ScopedHeldReceipt(ScopedStatus.REFUSED,original==null?null:original.key,false,0,0);
        return setHeldScopedInternal(original.key,false,refusalOnlyFence,original.history,true);
    }
    public static ScopedHeldReceipt releaseOwnedFromObservedScoped(ScopedHeldReceipt original,java.util.function.BooleanSupplier refusalOnlyFence){
        if(!claimIssuedReturnKeyCall(original==null?null:original.key,false))return new ScopedHeldReceipt(ScopedStatus.REFUSED,original==null?null:original.key,false,0,0);
        if(original==null||!original.confirmed()||!original.held()||original.history==null||!original.history.current()||!original.savedBindingCurrent())
            return new ScopedHeldReceipt(ScopedStatus.REFUSED,original==null?null:original.key,false,0,0);
        return setHeldScopedInternal(original.key,false,refusalOnlyFence,original.history,true);
    }
    private static final class ResumeKeyCall {
        final KeyMapping key;boolean claimed;
        ResumeKeyCall(KeyMapping key){this.key=key;}
    }
    private static final ThreadLocal<ResumeKeyCall> RESUME_KEY = new ThreadLocal<>();
    /** Package-private exact neutral history; public callers cannot open an issued hook. */
    static ScopedHeldReceipt releaseIssuedResumeObserved(KeyMapping key,Object originalSnapshot,java.util.function.BooleanSupplier refusalOnlyFence){
        if(McIssuedFarmReturnCleanup.active()){
            var ledger=McIssuedFarmReturnCleanup.currentKeyHistory();if(ledger!=null)ledger.failure();
            return new ScopedHeldReceipt(ScopedStatus.REFUSED,key,false,0,0);
        }
        var original=KeyHistory.capture(key);
        if(!McIssuedFarmResumeGuard.active()||original==null||originalSnapshot==null||key.isDown()
                ||!original.carrier.normalIdleKeyCurrent(originalSnapshot))return new ScopedHeldReceipt(ScopedStatus.REFUSED,key,false,0,0);
        if(RESUME_KEY.get()!=null){McIssuedFarmResumeGuard.refuseUnscopedMutation();return new ScopedHeldReceipt(ScopedStatus.REFUSED,key,false,0,0);}
        var actual=new ResumeKeyCall(key);RESUME_KEY.set(actual);
        try{return setHeldScopedInternal(key,false,refusalOnlyFence,new KeyHistory(key,original.carrier,originalSnapshot),true);}
        finally{RESUME_KEY.remove();}
    }
    private static ScopedHeldReceipt setHeldScopedInternal(KeyMapping key,boolean held,java.util.function.BooleanSupplier refusalOnlyFence,KeyHistory originalHistory){
        return setHeldScopedInternal(key,held,refusalOnlyFence,originalHistory,false);
    }
    private static ScopedHeldReceipt setHeldScopedInternal(KeyMapping key,boolean held,java.util.function.BooleanSupplier refusalOnlyFence,KeyHistory originalHistory,boolean retainNormallyReturnedAttempts){
        return setHeldScopedInternal(key,held,refusalOnlyFence,originalHistory,retainNormallyReturnedAttempts,null);
    }
    private static ScopedHeldReceipt setHeldScopedInternal(KeyMapping key,boolean held,java.util.function.BooleanSupplier refusalOnlyFence,KeyHistory originalHistory,boolean retainNormallyReturnedAttempts,
            McBlockBreakingService.ImmediateHeldAttempt exactAttempt){
        return setHeldScopedInternal(key,held,refusalOnlyFence,originalHistory,retainNormallyReturnedAttempts,exactAttempt,null);
    }
    private static ScopedHeldReceipt setHeldScopedInternal(KeyMapping key,boolean held,java.util.function.BooleanSupplier refusalOnlyFence,KeyHistory originalHistory,boolean retainNormallyReturnedAttempts,
            McBlockBreakingService.ImmediateHeldAttempt exactAttempt,
            dev.veltraph.client.navigation.mc.LegacyTravelPublicationLease.Leg.ImmediateHeldAttempt immediateLegAttempt){
        if(McIssuedFarmResumeGuard.active()){
            var issued=RESUME_KEY.get();
            if(issued==null||issued.claimed||issued.key!=key||held){McIssuedFarmResumeGuard.refuseUnscopedMutation();return new ScopedHeldReceipt(ScopedStatus.REFUSED,key,held,0,0);}
            issued.claimed=true;
        }
        if(!issuedReturnKeyCallCurrent(key,held))return new ScopedHeldReceipt(ScopedStatus.REFUSED,key,held,0,0);
        var client=net.minecraft.client.Minecraft.getInstance();var nativeLeaf=McScopedInputReadFence.capture(client);
        if(key==null||refusalOnlyFence==null||nativeLeaf==null)return new ScopedHeldReceipt(ScopedStatus.REFUSED,key,held,0,0);
        int[] attempts={0,0};dev.veltraph.client.observation.unexpected.UnexpectedActions.HeldPublicationObservation[] observed={null};var historyBefore=KeyHistory.capture(key);var actualInvocations=new java.util.ArrayList<NativeInputInvocation>();
        try {boolean confirmed=dev.veltraph.client.navigation.ScopedHeldKeyPublication.apply(held,new dev.veltraph.client.navigation.ScopedHeldKeyPublication.Effects(){
            public void observe(){observed[0]=dev.veltraph.client.observation.unexpected.UnexpectedActions.beginHeldPublication(client,key,held);}
            public boolean current(){return dev.veltraph.client.navigation.ScopedHeldKeyPublication.afterRefusal(refusalOnlyFence,
                ()->nativeLeaf.current()&&ownHistoryCurrent(originalHistory,key,actualInvocations));}
            public boolean down(){return key.isDown();}
            public void setDown(boolean down){
                if(!McInputService.beforeWartDropNativeInput(key,down,false,actualInvocations))throw new dev.veltraph.client.navigation.WaterPublicationRevokedException();
                attempts[0]++;nativeInput(key,down,false,nativeLeaf,actualInvocations,()->key.setDown(down));
                // No observer/source callback occurs between nativeInput normal return and this private cache.
                if(exactAttempt!=null&&held&&down)cacheImmediateAttempt(exactAttempt,key,historyBefore,actualInvocations,attempts[0]);
                if(immediateLegAttempt!=null&&held&&down)cacheImmediateLegAttempt(immediateLegAttempt,key,originalHistory,actualInvocations,attempts[0]);}
            public boolean toggle(){return key instanceof ToggleKeyMapping;}
            public void clearRestore(){
                if(!McInputService.beforeWartDropNativeInput(key,false,true,actualInvocations))throw new dev.veltraph.client.navigation.WaterPublicationRevokedException();
                attempts[1]++;nativeInput(key,false,true,nativeLeaf,actualInvocations,()->((ToggleKeyMapping)key).shouldRestoreStateOnScreenClosed());}
        });var receipt=new ScopedHeldReceipt(confirmed?ScopedStatus.CONFIRMED:ScopedStatus.REFUSED,key,held,attempts[0],attempts[1]);
            var after=KeyHistory.capture(key);if((confirmed||retainNormallyReturnedAttempts&&actualInvocations.stream().anyMatch(i->i.complete&&!i.restore&&(!held||i.requested)))&&historyBefore!=null&&after!=null&&actualInvocations.stream().allMatch(i->i.complete)
                &&after.carrier.normalIdleKeyAdvanced(historyBefore.snapshot,after.snapshot,actualInvocations)){receipt.history=after;receipt.actualHistory=java.util.List.copyOf(actualInvocations);}
            try{if(receipt.confirmed()&&receipt.normalIdleHistoryCurrent()&&attempts[0]>0&&observed[0]!=null&&nativeLeaf.current()&&key.isDown()==held)
                dev.veltraph.client.observation.unexpected.UnexpectedActions.completeHeldPublication(observed[0],new HeldPublicationReceipt(client,key,held,after,observed[0]));
            }catch(RuntimeException unavailable){/* Actual CONFIRMED/REFUSED semantics survive passive completion failure. */}
            return receipt;
        }catch(RuntimeException unavailable){
            var failed=new ScopedHeldReceipt(ScopedStatus.FAILED,key,held,attempts[0],attempts[1]);
            // A thrown later source callback cannot erase normally returned, privately claimed native history.
            // The failed action remains failed; only this exact attempt may supply cleanup-only custody.
            if(retainNormallyReturnedAttempts&&historyBefore!=null&&actualInvocations.stream().anyMatch(i->i.complete&&!i.restore&&(!held||i.requested))
                &&actualInvocations.stream().allMatch(i->i.complete)){
                var after=KeyHistory.capture(key);
                if(after!=null&&after.carrier.normalIdleKeyAdvanced(historyBefore.snapshot,after.snapshot,actualInvocations)){
                    failed.history=after;failed.actualHistory=java.util.List.copyOf(actualInvocations);
                }
            }
            return failed;
        }
    }
    private static void cacheImmediateAttempt(McBlockBreakingService.ImmediateHeldAttempt exactAttempt,KeyMapping key,
            KeyHistory before,java.util.List<NativeInputInvocation> actual,int setters){
        if(before==null||actual.isEmpty()||actual.stream().anyMatch(i->!i.complete)||actual.stream().noneMatch(i->!i.restore&&i.requested))return;
        var after=KeyHistory.capture(key);
        if(after==null||after.carrier!=before.carrier||!after.carrier.normalIdleKeyAdvanced(before.snapshot,after.snapshot,actual))return;
        // REFUSED deliberately: even a genuine native setter grants cleanup only before source validation.
        var receipt=new ScopedHeldReceipt(ScopedStatus.REFUSED,key,true,setters,0);receipt.history=after;receipt.actualHistory=java.util.List.copyOf(actual);
        exactAttempt.cache(receipt);
    }
    /** Cache real normally returned TRUE before the next caller freshness/observer callback. Never confirms gameplay. */
    private static void cacheImmediateLegAttempt(dev.veltraph.client.navigation.mc.LegacyTravelPublicationLease.Leg.ImmediateHeldAttempt exactAttempt,
            KeyMapping key,KeyHistory original,java.util.List<NativeInputInvocation> actual,int setters){
        if(original==null||actual.isEmpty()||actual.stream().anyMatch(i->!i.complete||!i.claimed||i.revoked)
            ||actual.stream().noneMatch(i->!i.restore&&i.requested))return;
        var after=KeyHistory.capture(key);
        if(after==null||after.key!=original.key||after.carrier!=original.carrier
            ||!after.carrier.normalIdleKeyAdvanced(original.snapshot,after.snapshot,actual))return;
        var receipt=new ScopedHeldReceipt(ScopedStatus.REFUSED,key,true,setters,0);
        receipt.history=after;receipt.actualHistory=java.util.List.copyOf(actual);receipt.immediateLegAttempt=exactAttempt;
        exactAttempt.cache(receipt);
    }
    private static boolean ownHistoryCurrent(KeyHistory original,KeyMapping key,java.util.List<NativeInputInvocation> actual){
        if(original==null)return true;var after=KeyHistory.capture(key);
        return after!=null&&after.key==original.key&&after.carrier==original.carrier&&actual.stream().allMatch(i->i.complete)
            &&after.carrier.normalIdleKeyAdvanced(original.snapshot,after.snapshot,actual);
    }
    private static final ThreadLocal<NativeInputInvocation> NATIVE_INPUT=new ThreadLocal<>();
    public static final class NativeInputInvocation {
        private final KeyMapping key;private final boolean requested,restore;private final Thread thread;private final McScopedInputReadFence scope;
        private boolean claimed,complete,revoked,nativeReturned;
        private McInputService.WartNativeFieldGuard wartFieldGuard;
        private java.util.List<NativeInputInvocation> wartReturned;
        private boolean wartMethodEntered,wartFieldWritten;
        private NativeInputInvocation(KeyMapping key,boolean requested,boolean restore,McScopedInputReadFence scope){this.key=key;this.requested=requested;this.restore=restore;this.scope=scope;thread=Thread.currentThread();}
    }
    /** Only a real scoped call's private in-flight exact setter/restore object can be observed by native hooks. */
    public static NativeInputInvocation claimNativeInput(KeyMapping key,boolean requested,boolean restore){var i=NATIVE_INPUT.get();
        if(i==null||i.claimed||i.revoked||i.key!=key||i.requested!=requested||i.restore!=restore||i.thread!=Thread.currentThread()||!i.scope.current())return null;
        i.claimed=true;return i;
    }
    private static void nativeInput(KeyMapping key,boolean requested,boolean restore,McScopedInputReadFence scope,java.util.List<NativeInputInvocation> issued,Runnable effect){
        if(McIssuedFarmReturnCleanup.active()){nativeIssuedReturnInput(key,requested,restore,scope,issued,effect);return;}
        var previous=NATIVE_INPUT.get();var actual=new NativeInputInvocation(key,requested,restore,scope);if(previous!=null){previous.revoked=true;effect.run();return;}
        actual.wartFieldGuard=McInputService.captureWartNativeFieldGuard(key);actual.wartReturned=java.util.List.copyOf(issued);
        NATIVE_INPUT.set(actual);try{effect.run();actual.complete=actual.claimed&&!actual.revoked&&scope.current();issued.add(actual);}finally{NATIVE_INPUT.remove();}
    }
    private static final ThreadLocal<WartNativeMethodCall> WART_NATIVE_METHOD=new ThreadLocal<>();
    private static final ThreadLocal<WartNativeSuperCall> WART_NATIVE_SUPER=new ThreadLocal<>();
    private static final class WartNativeMethodCall {
        final NativeInputInvocation input;final WartNativeMethodCall parent;final KeyMapping receiver;
        final boolean requested,base,selected;boolean closed;
        WartNativeMethodCall(NativeInputInvocation input,WartNativeMethodCall parent,KeyMapping receiver,boolean requested,boolean base,boolean selected){
            this.input=input;this.parent=parent;this.receiver=receiver;this.requested=requested;this.base=base;this.selected=selected;}
    }
    private static final class WartNativeSuperCall {
        final WartNativeMethodCall parent;final ToggleKeyMapping receiver;final boolean down;boolean entered;
        WartNativeSuperCall(WartNativeMethodCall parent,ToggleKeyMapping receiver,boolean down){this.parent=parent;this.receiver=receiver;this.down=down;}
    }
    /** SDK method classification precedes HEAD history. Ordinary reentry remains an ordinary native setter. */
    public static Object beginWartNativeMethod(KeyMapping receiver,boolean requested,boolean base){
        var input=NATIVE_INPUT.get();var parent=WART_NATIVE_METHOD.get();var edge=WART_NATIVE_SUPER.get();
        if(parent==null&&(input==null||input.wartFieldGuard==null))return null;
        if(!wartNativeHookCaller(base?KeyMapping.class:ToggleKeyMapping.class,base?"veltraph$manualKeySetter":"veltraph$manualToggleOuter")){
            if(input!=null&&input.wartFieldGuard!=null){input.revoked=true;input.wartFieldGuard.revoke();}return null;
        }
        boolean outer=parent==null&&input!=null&&input.wartFieldGuard!=null&&!input.wartMethodEntered
            &&input.key==receiver&&input.requested==requested&&!input.restore&&base==(receiver.getClass()==KeyMapping.class)
            &&input.thread==Thread.currentThread();
        boolean child=parent!=null&&parent.selected&&!parent.closed&&base&&edge!=null&&!edge.entered&&edge.parent==parent
            &&edge.receiver==receiver&&edge.down==requested&&parent.receiver==receiver&&parent.input==input&&!parent.base;
        boolean selected=outer||child;
        if(outer&&selected)input.wartMethodEntered=true;if(child&&selected)edge.entered=true;
        if(!selected&&input!=null&&input.wartFieldGuard!=null){input.revoked=true;input.wartFieldGuard.revoke();}
        var call=new WartNativeMethodCall(input,parent,receiver,requested,base,selected);WART_NATIVE_METHOD.set(call);return call;
    }
    public static void endWartNativeMethod(Object token){
        if(!(token instanceof WartNativeMethodCall call))return;
        if(!wartNativeHookCaller(call.base?KeyMapping.class:ToggleKeyMapping.class,call.base?"veltraph$manualKeySetter":"veltraph$manualToggleOuter")){
            if(call.input!=null&&call.input.wartFieldGuard!=null){call.input.revoked=true;call.input.wartFieldGuard.revoke();}return;
        }
        if(WART_NATIVE_METHOD.get()!=call){if(call.input!=null&&call.input.wartFieldGuard!=null)call.input.wartFieldGuard.revoke();return;}
        call.closed=true;if(call.parent==null)WART_NATIVE_METHOD.remove();else WART_NATIVE_METHOD.set(call.parent);
    }
    /** Exact native-computed superclass argument, after the original Toggle mode callback. */
    public static Object beginWartNativeSuper(ToggleKeyMapping receiver,boolean down){
        var call=WART_NATIVE_METHOD.get();if(call==null||!call.selected)return null;
        var input=call.input;
        if(!wartNativeHookCaller(ToggleKeyMapping.class,"veltraph$manualToggleSuper")){
            if(input!=null&&input.wartFieldGuard!=null){input.revoked=true;input.wartFieldGuard.revoke();}return null;
        }
        if(call.closed||call.base||receiver!=call.receiver||input==null||NATIVE_INPUT.get()!=input||input.revoked
            ||input.wartFieldGuard==null||WART_NATIVE_SUPER.get()!=null){if(input!=null&&input.wartFieldGuard!=null)input.wartFieldGuard.revoke();throw new dev.veltraph.client.navigation.WaterPublicationRevokedException();}
        var edge=new WartNativeSuperCall(call,receiver,down);WART_NATIVE_SUPER.set(edge);return edge;
    }
    public static void endWartNativeSuper(Object token){
        if(!(token instanceof WartNativeSuperCall edge))return;
        if(!wartNativeHookCaller(ToggleKeyMapping.class,"veltraph$manualToggleSuper")){
            if(edge.parent.input.wartFieldGuard!=null){edge.parent.input.revoked=true;edge.parent.input.wartFieldGuard.revoke();}return;
        }
        if(WART_NATIVE_SUPER.get()!=edge||WART_NATIVE_METHOD.get()!=edge.parent){if(edge.parent.input.wartFieldGuard!=null)edge.parent.input.wartFieldGuard.revoke();}
        if(WART_NATIVE_SUPER.get()==edge)WART_NATIVE_SUPER.remove();
    }
    /** Final actual SDK FIELD: selected identity stays selected even when a late callback revokes its source. */
    public static void beforeWartNativeField(KeyMapping receiver,boolean down){
        var call=WART_NATIVE_METHOD.get();
        if(call==null){var pending=NATIVE_INPUT.get();
            if(pending!=null&&pending.wartFieldGuard!=null){pending.revoked=true;pending.wartFieldGuard.revoke();throw new dev.veltraph.client.navigation.WaterPublicationRevokedException();}
            return;
        }
        if(!call.selected)return;
        var input=call.input;int depth=call.parent==null?1:2;
        if(!wartNativeHookCaller(KeyMapping.class,"veltraph$manualActualKeyWrite")||call.closed||!call.base||receiver!=call.receiver||down!=call.requested||input==null||NATIVE_INPUT.get()!=input
            ||!input.claimed||input.revoked||input.wartFieldWritten||input.thread!=Thread.currentThread()||input.wartFieldGuard==null
            ||!input.wartFieldGuard.fieldCurrent(receiver,down,input.wartReturned,input,depth)){
            if(input!=null){input.revoked=true;if(input.wartFieldGuard!=null)input.wartFieldGuard.revoke();}
            throw new dev.veltraph.client.navigation.WaterPublicationRevokedException();
        }
        input.wartFieldWritten=true;
    }
    /** Native source validation only; a caller name cannot construct a private pending Wart invocation. */
    private static boolean wartNativeHookCaller(Class<?> owner,String hook){
        return StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).walk(frames->{var caller=frames.filter(f->f.getDeclaringClass()!=McHeldKeys.class).findFirst().orElse(null);
            return caller!=null&&caller.getDeclaringClass()==owner&&(caller.getMethodName().equals(hook)||caller.getMethodName().endsWith("$"+hook));});
    }
    /** One-use private-source claim BEFORE each public wrapper's first native snapshot or observer. */
    private static boolean claimIssuedReturnKeyCall(KeyMapping key,boolean held){
        if(!McIssuedFarmReturnCleanup.active())return true;
        var ledger=McIssuedFarmReturnCleanup.currentKeyHistory();
        if(!McIssuedFarmReturnCleanup.claimKeyCall(key,held)||ledger==null||!ledger.current()){
            if(ledger!=null)ledger.failure();return false;
        }
        return true;
    }
    /** Private call admission is idempotent and refusal-only; a ledger or public Boolean supplies no authority. */
    private static boolean issuedReturnKeyCallCurrent(KeyMapping key,boolean held){
        if(!McIssuedFarmReturnCleanup.active())return true;
        var ledger=McIssuedFarmReturnCleanup.currentKeyHistory();
        if(!McIssuedFarmReturnCleanup.permitKeyCall(key,held)||ledger==null||!ledger.current()){
            if(ledger!=null)ledger.failure();return false;
        }
        return true;
    }
    /** Same real native method, with additional private cleanup history observations only inside the active Context. */
    private static void nativeIssuedReturnInput(KeyMapping key,boolean requested,boolean restore,McScopedInputReadFence scope,
            java.util.List<NativeInputInvocation> issued,Runnable effect){
        var ledger=McIssuedFarmReturnCleanup.currentKeyHistory();var previous=NATIVE_INPUT.get();
        if(previous!=null){previous.revoked=true;if(ledger!=null)ledger.failure();throw new IllegalStateException("Recursive return cleanup input");}
        var actual=new NativeInputInvocation(key,requested,restore,scope);
        if(ledger==null||!McIssuedFarmReturnCleanup.nativeCurrent()||!ledger.beforeNativeInput(actual)){
            if(ledger!=null)ledger.failure();throw new IllegalStateException("Return cleanup native history changed");
        }
        NATIVE_INPUT.set(actual);
        try{
            effect.run();
            if(McIssuedFarmReturnCleanup.currentKeyHistory()!=ledger||!ledger.afterNormalNativeInput(actual)){
                ledger.failure();throw new IllegalStateException("Return cleanup native return history changed");
            }
            // The journal has already retained this real normally returned native token.
            // NormalIdleInputVersion compares opaque event identity without relying on .complete.
            actual.complete=actual.claimed&&!actual.revoked&&scope.current();issued.add(actual);
        }catch(RuntimeException|Error failure){ledger.failure();throw failure;}
        finally{NATIVE_INPUT.remove();}
    }
    /** Integrity history only. Neither this object nor its observations grant key publication authority. */
    public static final class IssuedReturnKeyHistory {
        private final net.minecraft.client.Minecraft client;
        private final Object options;
        private final Thread thread=Thread.currentThread();
        private final KeyHistory[] original;
        private final boolean[] held;
        private final java.util.List<java.util.ArrayList<NativeInputInvocation>> returned;
        private NativeInputInvocation pending;
        private boolean revoked;
        private IssuedReturnKeyHistory(net.minecraft.client.Minecraft client,KeyHistory[] original,boolean[] held){
            this.client=client;options=client.options;this.original=original;this.held=held;
            var histories=new java.util.ArrayList<java.util.ArrayList<NativeInputInvocation>>();
            for(int i=0;i<9;i++)histories.add(new java.util.ArrayList<>());returned=java.util.List.copyOf(histories);
        }
        private boolean headerCurrent(){
            if(revoked||thread!=Thread.currentThread()||client!=net.minecraft.client.Minecraft.getInstance()||!client.isSameThread()
                ||client.options!=options||client.options==null||original.length!=9)return false;
            var o=client.options;var live=new KeyMapping[]{o.keyUp,o.keyDown,o.keyLeft,o.keyRight,o.keyJump,o.keyShift,o.keySprint,o.keyAttack,o.keyUse};
            for(int i=0;i<9;i++)if(original[i]==null||live[i]!=original[i].key)return false;
            return true;
        }
        /** Compares to the ORIGINAL nine observations plus actual private normally returned tokens, never a fresh baseline. */
        public boolean current(){
            if(!headerCurrent())return false;
            for(int i=0;i<9;i++){
                var before=original[i];var after=KeyHistory.capture(before.key);
                if(after==null||after.carrier!=before.carrier||before.key.isDown()!=held[i]
                    ||!after.carrier.normalIdleKeyAdvanced(before.snapshot,after.snapshot,returned.get(i)))return false;
            }
            return headerCurrent();
        }
        private int index(KeyMapping key){for(int i=0;i<9;i++)if(original[i].key==key)return i;return -1;}
        private boolean beforeNativeInput(NativeInputInvocation actual){
            if(actual==null||pending!=null||index(actual.key)<0||!current()){revoked=true;return false;}
            pending=actual;return true;
        }
        /** Native method has returned. Cache its genuine token BEFORE the outer scope completion predicate. */
        private boolean afterNormalNativeInput(NativeInputInvocation actual){
            if(pending!=actual||actual==null||!actual.claimed||actual.revoked||!headerCurrent()){revoked=true;return false;}
            int slot=index(actual.key);if(slot<0){revoked=true;return false;}
            var tokens=returned.get(slot);var extended=new java.util.ArrayList<NativeInputInvocation>(tokens);extended.add(actual);
            for(int i=0;i<9;i++){
                var before=original[i];var after=KeyHistory.capture(before.key);
                if(after==null||after.carrier!=before.carrier||i!=slot&&before.key.isDown()!=held[i]
                    ||!after.carrier.normalIdleKeyAdvanced(before.snapshot,after.snapshot,i==slot?extended:returned.get(i))){revoked=true;return false;}
            }
            tokens.add(actual);held[slot]=actual.key.isDown();pending=null;
            if(!current()){revoked=true;return false;}return true;
        }
        private void failure(){revoked=true;pending=null;}
    }
    /** Package-private capture used only by the concrete private return-cleanup Context before callbacks. */
    static IssuedReturnKeyHistory captureIssuedReturnKeyHistory(net.minecraft.client.Minecraft client){
        if(client==null||client!=net.minecraft.client.Minecraft.getInstance()||!client.isSameThread()||client.options==null)return null;
        var o=client.options;var keys=new KeyMapping[]{o.keyUp,o.keyDown,o.keyLeft,o.keyRight,o.keyJump,o.keyShift,o.keySprint,o.keyAttack,o.keyUse};
        var histories=new KeyHistory[9];var held=new boolean[9];
        for(int i=0;i<9;i++){for(int j=0;j<i;j++)if(keys[j]==keys[i])return null;histories[i]=KeyHistory.capture(keys[i]);if(histories[i]==null)return null;held[i]=keys[i].isDown();}
        var result=new IssuedReturnKeyHistory(client,histories,held);return result.current()?result:null;
    }
    private static final ThreadLocal<SavedMaintenanceKeyInvocation> SAVED_MAINTENANCE_KEY=new ThreadLocal<>();
    /** Private actual restoration invocation. Public getters are identity observations, never instructions. */
    public static final class SavedMaintenanceKeyInvocation {
        private final dev.veltraph.client.rewarp.mc.FarmMaintenanceCoordinator.SavedMaintenanceRestoreKey instruction;
        private final dev.veltraph.client.rewarp.mc.FarmMaintenanceCoordinator.SavedMaintenanceRollbackKey rollbackInstruction;
        private final dev.veltraph.client.rewarp.mc.FarmMaintenanceCoordinator.RetainedMaintenanceRestoreKey retainedInstruction;
        private RetainedMaintenanceKeyInvocation retainedInvocation;
        private RootNativeHistory.RetainedMaintenanceKeyObservation retainedObservation;
        private RollbackMaintenanceKeyInvocation rollbackInvocation;
        private RollbackCleanupKeyInvocation cleanupInvocation;
        private RootNativeHistory.SavedMaintenanceRollbackKeyObservation rollbackObservation;
        private final net.minecraft.client.Minecraft client;
        private final Thread thread=Thread.currentThread();
        private final Object player,world,listener,options,gameMode;
        private final KeyMapping key;
        private final dev.veltraph.client.VeltraphBootstrap.RootOriginalNativeMaintenancePauseInvocation pause;
        private final McInputService postureService;
        private RootNativeHistory.SavedMaintenancePauseObservation pauseObservation;
        private RootNativeHistory.SavedMaintenanceKeyObservation observation;
        private boolean started,normalReturned,closed,revoked;
        private KeyHistory originalKey;private final java.util.ArrayList<NativeInputInvocation> returnedNative=new java.util.ArrayList<>();
        private NativeInputInvocation pendingNative;private boolean pendingReturned,savedSuper,methodEntered;private int fieldWrites;
        private int ordinaryFallbackDepth;private SavedNativeMethodCall ordinaryFallbackParent;
        private SavedMaintenanceKeyInvocation(dev.veltraph.client.rewarp.mc.FarmMaintenanceCoordinator.SavedMaintenanceRestoreKey instruction){
            this.instruction=instruction;retainedInstruction=null;rollbackInstruction=null;pause=null;postureService=null;client=instruction.client();player=client.player;world=client.level;listener=client.getConnection();options=client.options;gameMode=client.gameMode;key=instruction.key();
        }
        private SavedMaintenanceKeyInvocation(McInputService service,dev.veltraph.client.VeltraphBootstrap.RootOriginalNativeMaintenancePauseInvocation pause){
            instruction=null;retainedInstruction=null;rollbackInstruction=null;this.pause=pause;postureService=service;client=net.minecraft.client.Minecraft.getInstance();
            player=client.player;world=client.level;listener=client.getConnection();options=client.options;gameMode=client.gameMode;key=pause.originalPostureKey();
        }
        private SavedMaintenanceKeyInvocation(dev.veltraph.client.rewarp.mc.FarmMaintenanceCoordinator.SavedMaintenanceRollbackKey instruction){
            this.instruction=null;retainedInstruction=null;rollbackInstruction=instruction;pause=null;postureService=null;client=instruction.client();
            player=client.player;world=client.level;listener=client.getConnection();options=client.options;gameMode=client.gameMode;key=instruction.key();
        }
        private SavedMaintenanceKeyInvocation(RollbackCleanupKeyInvocation cleanup){
            instruction=null;retainedInstruction=null;rollbackInstruction=null;pause=null;postureService=null;cleanupInvocation=cleanup;client=cleanup.client();
            player=client.player;world=client.level;listener=client.getConnection();options=client.options;gameMode=client.gameMode;key=cleanup.key();
        }
        private boolean cleanupSceneCurrent(){return cleanupInvocation!=null&&cleanupInvocation.matchesCarrier(this)
            &&client==net.minecraft.client.Minecraft.getInstance()&&client.isSameThread()&&client.player==player&&client.level==world
            &&client.getConnection()==listener&&client.options==options&&client.gameMode==gameMode&&key==cleanupInvocation.key()
            &&cleanupInvocation.sourceCurrent();}
        private SavedMaintenanceKeyInvocation(dev.veltraph.client.rewarp.mc.FarmMaintenanceCoordinator.RetainedMaintenanceRestoreKey instruction){
            this.instruction=null;retainedInstruction=instruction;rollbackInstruction=null;pause=null;postureService=null;client=instruction.client();
            player=client.player;world=client.level;listener=client.getConnection();options=client.options;gameMode=client.gameMode;key=instruction.key();
        }
        private boolean retainedSceneCurrent(){return retainedInvocation!=null&&retainedInvocation.matchesInstruction(retainedInstruction)
            &&client==net.minecraft.client.Minecraft.getInstance()&&client.isSameThread()&&client.player==player&&client.level==world
            &&client.getConnection()==listener&&client.options==options&&client.gameMode==gameMode&&key==retainedInstruction.key()
            &&retainedInstruction.normalNativeCurrent()&&retainedObservation!=null
            &&((started||normalReturned)?retainedObservation.otherNativeCurrent():retainedObservation.beforeNativeCurrent());}
        private boolean rollbackSceneCurrent(){return rollbackInvocation!=null&&rollbackInvocation.matchesInstruction(rollbackInstruction)
            &&client==net.minecraft.client.Minecraft.getInstance()&&client.isSameThread()&&client.player==player&&client.level==world
            &&client.getConnection()==listener&&client.options==options&&client.gameMode==gameMode&&key==rollbackInstruction.key()
            &&rollbackInstruction.normalNativeCurrent()&&rollbackObservation!=null
            &&((started||normalReturned)?rollbackObservation.otherNativeCurrent():rollbackObservation.beforeNativeCurrent());}
        boolean matchesPosture(dev.veltraph.client.VeltraphBootstrap.RootOriginalNativeMaintenancePauseInvocation exact){return exact!=null&&pause==exact
            &&instruction==null&&!closed&&!revoked&&SAVED_MAINTENANCE_KEY.get()==this&&Thread.currentThread()==thread;}
        boolean postureNormalReturnedCurrent(){return pause!=null&&normalReturned&&nativeCurrent();}
        private boolean postureSceneCurrent(){return matchesPosture(pause)&&client==net.minecraft.client.Minecraft.getInstance()&&client.isSameThread()
            &&client.player==player&&client.level==world&&client.getConnection()==listener&&client.options==options&&client.gameMode==gameMode
            &&key==pause.originalPostureKey()&&pause.nativePostureIssuingCurrent(postureService);}
        public net.minecraft.client.Minecraft client(){return client;}
        public dev.veltraph.client.rewarp.mc.FarmMaintenanceCoordinator.SavedMaintenanceRestoreKey instruction(){return instruction;}
        public boolean matchesInstruction(dev.veltraph.client.rewarp.mc.FarmMaintenanceCoordinator.SavedMaintenanceRestoreKey exact){
            return exact!=null&&instruction==exact&&!closed&&!revoked&&SAVED_MAINTENANCE_KEY.get()==this&&Thread.currentThread()==thread;
        }
        private boolean nativeCurrent(){if(retainedInstruction!=null)return retainedSceneCurrent();if(cleanupInvocation!=null)return cleanupSceneCurrent();if(rollbackInstruction!=null)return rollbackSceneCurrent();if(pause!=null)return postureSceneCurrent()&&pauseObservation!=null
            &&pauseObservation.postureOtherCurrent(this);
            return matchesInstruction(instruction)&&client==net.minecraft.client.Minecraft.getInstance()&&client.isSameThread()
            &&client.player==player&&client.level==world&&client.getConnection()==listener&&client.options==options&&client.gameMode==gameMode
            &&key==instruction.key()&&instruction.normalNativeCurrent()&&observation!=null
            &&((started||normalReturned)?observation.otherNativeCurrent():observation.beforeNativeCurrent());}
        /** No source/provider/arrival/Toggle-mode callback occurs in this final stored leaf. */
        private boolean finalNativeCurrent(){
            if(!finalNativeCarrierCurrent())return false;
            // All actual native getters and in-flight prefix checks precede this original stored-source leaf.
            if(retainedInstruction==null)return true;
            if(!retainedInstruction.finalStoredFieldFactsCurrent())return false;
            // Direct original scene identities follow every provider, native-prefix and stored-source check.
            return client.player==player&&client.level==world&&client.options==options&&client.gameMode==gameMode
                &&client.player!=null&&client.player.connection==listener;
        }
        private boolean finalNativeCarrierCurrent(){
            if(retainedInstruction!=null){
                if(retainedInvocation==null||!retainedInvocation.matchesInstruction(retainedInstruction)
                    ||client!=net.minecraft.client.Minecraft.getInstance()||!client.isSameThread()||client.player!=player||client.level!=world
                    ||client.getConnection()!=listener||client.options!=options||client.gameMode!=gameMode||key!=retainedInstruction.key()
                    ||originalKey==null||!retainedInstruction.finalNativeSourceCurrent()||retainedObservation==null
                    ||!retainedObservation.finalNativeCurrent(retainedInvocation))return false;
            }else if(cleanupInvocation!=null){
                if(!cleanupSceneCurrent()||originalKey==null||!cleanupInvocation.finalSourceCurrent())return false;
            }else if(rollbackInstruction!=null){
                if(rollbackInvocation==null||!rollbackInvocation.matchesInstruction(rollbackInstruction)
                    ||client!=net.minecraft.client.Minecraft.getInstance()||!client.isSameThread()||client.player!=player||client.level!=world
                    ||client.getConnection()!=listener||client.options!=options||client.gameMode!=gameMode||key!=rollbackInstruction.key()
                    ||originalKey==null||!rollbackInstruction.finalNativeSourceCurrent()||rollbackObservation==null
                    ||!rollbackObservation.finalNativeCurrent(rollbackInvocation))return false;
            }else if(pause!=null){
                if(!matchesPosture(pause)||client!=net.minecraft.client.Minecraft.getInstance()||!client.isSameThread()
                    ||client.player!=player||client.level!=world||client.getConnection()!=listener||client.options!=options||client.gameMode!=gameMode
                    ||key!=pause.originalPostureKey()||originalKey==null||pauseObservation==null||!pauseObservation.finalPostureCurrent(this))return false;
            }else if(!matchesInstruction(instruction)||client!=net.minecraft.client.Minecraft.getInstance()||!client.isSameThread()
                ||client.player!=player||client.level!=world||client.getConnection()!=listener||client.options!=options||client.gameMode!=gameMode
                ||key!=instruction.key()||originalKey==null||!instruction.finalNativeSourceCurrent()||observation==null||!observation.finalNativeCurrent(this))return false;
            var pending=pendingNative;
            if(pending==null)return ownHistoryCurrent(originalKey,key,returnedNative);
            if(pending.key!=key||pending.revoked||pending.thread!=Thread.currentThread()||NATIVE_INPUT.get()!=pending)return false;
            if(!pending.claimed)return ownHistoryCurrent(originalKey,key,returnedNative);
            if(pendingReturned){var prefix=new java.util.ArrayList<NativeInputInvocation>(returnedNative);prefix.add(pending);var after=KeyHistory.capture(key);
                return after!=null&&after.carrier==originalKey.carrier&&after.carrier.normalIdleKeyAdvanced(originalKey.snapshot,after.snapshot,prefix);}
            return originalKey.carrier.normalIdleKeyInFlightCurrent(originalKey.snapshot,returnedNative,pending,savedSuper?2:1);
        }
    }
    /** Exact actual normally returned restoration. The observer compares its private prefix to old Root history. */
    public static final class SavedMaintenanceKeyReceipt {
        private final SavedMaintenanceKeyInvocation invocation;
        private final ScopedHeldReceipt nativeReceipt;
        private final boolean confirmed,normallyReturned;
        private SavedMaintenanceKeyReceipt(SavedMaintenanceKeyInvocation invocation,ScopedHeldReceipt nativeReceipt,boolean confirmed,boolean normallyReturned){
            this.invocation=invocation;this.nativeReceipt=nativeReceipt;this.confirmed=confirmed;this.normallyReturned=normallyReturned;
        }
        public dev.veltraph.client.rewarp.mc.FarmMaintenanceCoordinator.SavedMaintenanceRestoreKey instruction(){return invocation.instruction;}
        public SavedMaintenanceKeyInvocation invocationIdentity(){return invocation;}
        public ScopedHeldReceipt nativeReceipt(){return nativeReceipt;}
        public boolean confirmed(){return confirmed;}
        public boolean normallyReturned(){return normallyReturned;}
        /** Valid only during actual after-normal delivery; no future call or closed invocation can borrow it. */
        public boolean normalReturnedNativeCurrent(){return normallyReturned&&confirmed&&invocation.normalReturned&&invocation.nativeCurrent()
            &&nativeReceipt!=null&&nativeReceipt.confirmed()&&nativeReceipt.key()==invocation.key&&nativeReceipt.held()==invocation.instruction.desiredHeld()
            &&nativeReceipt.normalIdleHistoryCurrent()&&invocation.key.isDown()==nativeReceipt.held();}
    }
    public static SavedMaintenanceKeyReceipt restoreSavedMaintenanceKey(dev.veltraph.client.rewarp.mc.FarmMaintenanceCoordinator.SavedMaintenanceRestoreKey instruction){
        if(instruction==null)throw new IllegalStateException("Original saved restoration instruction missing");
        var previous=SAVED_MAINTENANCE_KEY.get();
        if(previous!=null){previous.revoked=true;throw new IllegalStateException("Recursive original saved restoration");}
        if(McIssuedFarmReturnCleanup.active()||McIssuedFarmResumeGuard.active())throw new IllegalStateException("Conflicting original saved restoration");
        var actual=new SavedMaintenanceKeyInvocation(instruction);SAVED_MAINTENANCE_KEY.set(actual);
        Throwable primary=null;
        try{
            // The Coordinator's private instruction claims this exact real method before any provider/observer.
            if(!instruction.claimNativeInstruction(actual))throw new IllegalStateException("Original saved restoration source refused");
            var original=KeyHistory.capture(actual.key);actual.originalKey=original;
            if(original==null||!original.current())throw new IllegalStateException("Original saved key history unavailable");
            actual.observation=RootNativeHistory.beginSavedMaintenanceKey(instruction,actual);
            if(actual.observation==null||!actual.nativeCurrent()||!original.carrier.normalIdleKeyAdvanced(actual.observation.originalKeyHistory(),original.snapshot,java.util.List.of()))
                throw new IllegalStateException("Original Root saved key observation refused");
            dev.veltraph.client.observation.unexpected.UnexpectedActions.HeldPublicationObservation observed=null;
            try{observed=dev.veltraph.client.observation.unexpected.UnexpectedActions.beginHeldPublication(actual.client,actual.key,instruction.desiredHeld());}
            catch(RuntimeException|Error observationUnavailable){/* Typed Root evidence remains separate; MOD origin is unavailable. */}
            var scope=McScopedInputReadFence.capture(actual.client);
            if(scope==null||!actual.observation.beforeNativeCurrent()||!instruction.nativeBeforeCurrent()||!original.current())
                throw new IllegalStateException("Original saved restoration changed before native dispatch");
            var issued=actual.returnedNative;int setters=0,restoration=0;
            boolean desired=instruction.desiredHeld();
            if(instruction.directSetter()){
                actual.started=true;setters++;
                savedMaintenanceNativeInput(actual,desired,false,scope,()->actual.key.setDown(desired));
            }else{
                if(actual.key.isDown()!=desired){
                    if(!actual.nativeCurrent()||!ownHistoryCurrent(original,actual.key,issued))throw new IllegalStateException("Original saved restoration key changed");
                    actual.started=true;setters++;
                    savedMaintenanceNativeInput(actual,desired,false,scope,()->actual.key.setDown(desired));
                    if(actual.key.isDown()!=desired){
                        if(!actual.nativeCurrent()||!ownHistoryCurrent(original,actual.key,issued))throw new IllegalStateException("Original saved restoration key changed");
                        setters++;savedMaintenanceNativeInput(actual,true,false,scope,()->actual.key.setDown(true));
                    }
                    if(actual.key.isDown()!=desired)throw new IllegalStateException("Key mapping refused absolute held state");
                }
                if(!desired&&actual.key instanceof ToggleKeyMapping toggle){
                    if(!actual.nativeCurrent()||!ownHistoryCurrent(original,actual.key,issued))throw new IllegalStateException("Original saved restoration key changed");
                    actual.started=true;restoration++;
                    savedMaintenanceNativeInput(actual,false,true,scope,()->toggle.shouldRestoreStateOnScreenClosed());
                }
            }
            var after=KeyHistory.capture(actual.key);
            boolean proved=after!=null&&after.key==original.key&&after.carrier==original.carrier&&issued.stream().allMatch(i->i.complete&&i.claimed&&!i.revoked)
                &&after.carrier.normalIdleKeyAdvanced(original.snapshot,after.snapshot,issued)&&actual.key.isDown()==desired&&actual.nativeCurrent();
            var originalBinding=instruction.originalBindingHistory();
            proved=proved&&originalBinding!=null&&dev.veltraph.client.observation.nativeinput.NativeKeyBindingHistory.current(actual.key,originalBinding);
            var receipt=new ScopedHeldReceipt(proved?ScopedStatus.CONFIRMED:ScopedStatus.REFUSED,actual.key,desired,setters,restoration);
            if(proved){receipt.history=after;receipt.actualHistory=java.util.List.copyOf(issued);receipt.savedRestorationBinding=originalBinding;}
            actual.normalReturned=true;
            var returned=new SavedMaintenanceKeyReceipt(actual,receipt,proved,true);
            RootNativeHistory.acceptSavedMaintenanceKeyNormal(actual.observation,returned);
            // All nine actual setter observations are passive and success-only. No CanonicalToggleTrace is opened.
            try{if(observed!=null&&setters>0&&returned.normalReturnedNativeCurrent()&&receipt.normalIdleHistoryCurrent()&&scope.current())
                dev.veltraph.client.observation.unexpected.UnexpectedActions.completeHeldPublication(observed,
                    new HeldPublicationReceipt(actual.client,actual.key,desired,after,observed));}
            catch(RuntimeException|Error observationUnavailable){/* Never replace native result or grant origin to failed/unknown restoration. */}
            if(!returned.normalReturnedNativeCurrent())throw new IllegalStateException("Original saved restoration normal return refused");
            return returned;
        }catch(RuntimeException|Error failure){primary=failure;actual.revoked=true;throw failure;}
        finally{
            try{RootNativeHistory.closeSavedMaintenanceKey(actual.observation,actual);}
            catch(RuntimeException|Error observerFailure){
                actual.revoked=true;
                if(primary!=null)SavedMaintenanceFailurePreservation.suppressWithoutReplacing(primary,observerFailure);
                else throw observerFailure;
            }
            finally{actual.closed=true;SAVED_MAINTENANCE_KEY.remove();}
        }
    }
    /** Distinct actual retained identity. Its private carrier shares only vanilla opcode fencing. */
    public static final class RetainedMaintenanceKeyInvocation {
        private final SavedMaintenanceKeyInvocation carrier;
        private final dev.veltraph.client.rewarp.mc.FarmMaintenanceCoordinator.RetainedMaintenanceRestoreKey instruction;
        private RetainedMaintenanceKeyInvocation(SavedMaintenanceKeyInvocation carrier){this.carrier=carrier;instruction=carrier.retainedInstruction;carrier.retainedInvocation=this;}
        public net.minecraft.client.Minecraft client(){return carrier.client;}
        public dev.veltraph.client.rewarp.mc.FarmMaintenanceCoordinator.RetainedMaintenanceRestoreKey instruction(){return instruction;}
        public boolean matchesInstruction(dev.veltraph.client.rewarp.mc.FarmMaintenanceCoordinator.RetainedMaintenanceRestoreKey exact){
            return exact!=null&&instruction==exact&&carrier.retainedInstruction==exact&&carrier.instruction==null&&carrier.pause==null
                &&carrier.retainedInvocation==this&&!carrier.closed&&!carrier.revoked&&SAVED_MAINTENANCE_KEY.get()==carrier
                &&Thread.currentThread()==carrier.thread;
        }
    }
    /** Only the real, normally returned retained opcode can construct this receipt. */
    public static final class RetainedMaintenanceKeyReceipt {
        private final RetainedMaintenanceKeyInvocation invocation;private final ScopedHeldReceipt nativeReceipt;
        private final boolean confirmed,normallyReturned;
        private RetainedMaintenanceKeyReceipt(RetainedMaintenanceKeyInvocation invocation,ScopedHeldReceipt nativeReceipt,boolean confirmed,boolean normallyReturned){
            this.invocation=invocation;this.nativeReceipt=nativeReceipt;this.confirmed=confirmed;this.normallyReturned=normallyReturned;
        }
        public dev.veltraph.client.rewarp.mc.FarmMaintenanceCoordinator.RetainedMaintenanceRestoreKey instruction(){return invocation.instruction;}
        public RetainedMaintenanceKeyInvocation invocationIdentity(){return invocation;}
        public ScopedHeldReceipt nativeReceipt(){return nativeReceipt;}
        public boolean confirmed(){return confirmed;}
        public boolean normallyReturned(){return normallyReturned;}
        public boolean normalReturnedNativeCurrent(){var carrier=invocation.carrier;return normallyReturned&&confirmed&&carrier.normalReturned
            &&invocation.matchesInstruction(invocation.instruction)&&carrier.nativeCurrent()&&nativeReceipt!=null&&nativeReceipt.confirmed()
            &&nativeReceipt.key()==carrier.key&&nativeReceipt.held()==invocation.instruction.desiredHeld()
            &&nativeReceipt.normalIdleHistoryCurrent()&&carrier.key.isDown()==nativeReceipt.held();}
    }
    public static RetainedMaintenanceKeyReceipt restoreRetainedMaintenanceKey(dev.veltraph.client.rewarp.mc.FarmMaintenanceCoordinator.RetainedMaintenanceRestoreKey instruction){
        if(instruction==null)throw new IllegalStateException("Original retained instruction missing");
        var previous=SAVED_MAINTENANCE_KEY.get();
        if(previous!=null){previous.revoked=true;throw new IllegalStateException("Recursive original retained");}
        if(McIssuedFarmReturnCleanup.active()||McIssuedFarmResumeGuard.active())throw new IllegalStateException("Conflicting original retained");
        SavedMaintenanceKeyInvocation actual=null;RetainedMaintenanceKeyInvocation retained=null;
        Throwable primary=null;
        try{
            actual=new SavedMaintenanceKeyInvocation(instruction);retained=new RetainedMaintenanceKeyInvocation(actual);SAVED_MAINTENANCE_KEY.set(actual);
            final var selected=actual;
            // The Coordinator's private instruction claims this exact real method before any provider/observer.
            if(!instruction.claimNativeInstruction(retained))throw new IllegalStateException("Original retained source refused");
            var original=KeyHistory.capture(actual.key);actual.originalKey=original;
            if(original==null||!original.current())throw new IllegalStateException("Original saved key history unavailable");
            actual.retainedObservation=RootNativeHistory.beginRetainedMaintenanceKey(instruction,retained);
            if(actual.retainedObservation==null||!actual.nativeCurrent()||!original.carrier.normalIdleKeyAdvanced(actual.retainedObservation.originalKeyHistory(),original.snapshot,java.util.List.of()))
                throw new IllegalStateException("Original Root saved key observation refused");
            dev.veltraph.client.observation.unexpected.UnexpectedActions.HeldPublicationObservation observed=null;
            try{observed=dev.veltraph.client.observation.unexpected.UnexpectedActions.beginHeldPublication(actual.client,actual.key,instruction.desiredHeld());}
            catch(RuntimeException|Error observationUnavailable){/* Typed Root evidence remains separate; MOD origin is unavailable. */}
            var scope=McScopedInputReadFence.capture(actual.client);
            if(scope==null||!actual.retainedObservation.beforeNativeCurrent()||!instruction.nativeBeforeCurrent()||!original.current())
                throw new IllegalStateException("Original retained changed before native dispatch");
            var issued=actual.returnedNative;int setters=0,restoration=0;
            boolean desired=instruction.desiredHeld();
            if(instruction.directSetter()){
                actual.started=true;setters++;
                savedMaintenanceNativeInput(actual,desired,false,scope,()->selected.key.setDown(desired));
            }else{
                if(actual.key.isDown()!=desired){
                    if(!actual.nativeCurrent()||!ownHistoryCurrent(original,actual.key,issued))throw new IllegalStateException("Original retained key changed");
                    actual.started=true;setters++;
                    savedMaintenanceNativeInput(actual,desired,false,scope,()->selected.key.setDown(desired));
                    if(actual.key.isDown()!=desired){
                        if(!actual.nativeCurrent()||!ownHistoryCurrent(original,actual.key,issued))throw new IllegalStateException("Original retained key changed");
                        setters++;savedMaintenanceNativeInput(actual,true,false,scope,()->selected.key.setDown(true));
                    }
                    if(actual.key.isDown()!=desired)throw new IllegalStateException("Key mapping refused absolute held state");
                }
                if(!desired&&actual.key instanceof ToggleKeyMapping toggle){
                    if(!actual.nativeCurrent()||!ownHistoryCurrent(original,actual.key,issued))throw new IllegalStateException("Original retained key changed");
                    actual.started=true;restoration++;
                    savedMaintenanceNativeInput(actual,false,true,scope,()->toggle.shouldRestoreStateOnScreenClosed());
                }
            }
            var after=KeyHistory.capture(actual.key);
            boolean proved=after!=null&&after.key==original.key&&after.carrier==original.carrier&&issued.stream().allMatch(i->i.complete&&i.claimed&&!i.revoked)
                &&after.carrier.normalIdleKeyAdvanced(original.snapshot,after.snapshot,issued)&&actual.key.isDown()==desired&&actual.nativeCurrent();
            var originalBinding=instruction.originalBindingHistory();
            proved=proved&&originalBinding!=null&&dev.veltraph.client.observation.nativeinput.NativeKeyBindingHistory.current(actual.key,originalBinding);
            var receipt=new ScopedHeldReceipt(proved?ScopedStatus.CONFIRMED:ScopedStatus.REFUSED,actual.key,desired,setters,restoration);
            if(proved){receipt.history=after;receipt.actualHistory=java.util.List.copyOf(issued);receipt.savedRestorationBinding=originalBinding;}
            actual.normalReturned=true;
            var returned=new RetainedMaintenanceKeyReceipt(retained,receipt,proved,true);
            RootNativeHistory.acceptRetainedMaintenanceKeyNormal(actual.retainedObservation,returned);
            // All nine actual setter observations are passive and success-only. No CanonicalToggleTrace is opened.
            try{if(observed!=null&&setters>0&&returned.normalReturnedNativeCurrent()&&receipt.normalIdleHistoryCurrent()&&scope.current())
                dev.veltraph.client.observation.unexpected.UnexpectedActions.completeHeldPublication(observed,
                    new HeldPublicationReceipt(actual.client,actual.key,desired,after,observed));}
            catch(RuntimeException|Error observationUnavailable){/* Never replace native result or grant origin to failed/unknown restoration. */}
            if(!returned.normalReturnedNativeCurrent())throw new IllegalStateException("Original retained normal return refused");
            return returned;
        }catch(RuntimeException|Error failure){primary=failure;if(actual!=null)actual.revoked=true;throw failure;}
        finally{
            try{if(actual!=null)RootNativeHistory.closeRetainedMaintenanceKey(actual.retainedObservation,retained);}
            catch(RuntimeException|Error observerFailure){
                if(actual!=null)actual.revoked=true;
                if(primary!=null)SavedMaintenanceFailurePreservation.suppressWithoutReplacing(primary,observerFailure);
                else throw observerFailure;
            }
            finally{if(actual!=null){actual.closed=true;SAVED_MAINTENANCE_KEY.remove();}}
        }
    }
    /** Distinct actual rollback identity. Its private carrier shares only vanilla opcode fencing. */
    public static final class RollbackMaintenanceKeyInvocation {
        private final SavedMaintenanceKeyInvocation carrier;
        private final dev.veltraph.client.rewarp.mc.FarmMaintenanceCoordinator.SavedMaintenanceRollbackKey instruction;
        private RollbackMaintenanceKeyInvocation(SavedMaintenanceKeyInvocation carrier){this.carrier=carrier;instruction=carrier.rollbackInstruction;carrier.rollbackInvocation=this;}
        public net.minecraft.client.Minecraft client(){return carrier.client;}
        public dev.veltraph.client.rewarp.mc.FarmMaintenanceCoordinator.SavedMaintenanceRollbackKey instruction(){return instruction;}
        public boolean matchesInstruction(dev.veltraph.client.rewarp.mc.FarmMaintenanceCoordinator.SavedMaintenanceRollbackKey exact){
            return exact!=null&&instruction==exact&&carrier.rollbackInstruction==exact&&carrier.instruction==null&&carrier.pause==null
                &&carrier.rollbackInvocation==this&&!carrier.closed&&!carrier.revoked&&SAVED_MAINTENANCE_KEY.get()==carrier
                &&Thread.currentThread()==carrier.thread;
        }
    }
    /** Only the real, normally returned rollback opcode can construct this receipt. */
    public static final class RollbackMaintenanceKeyReceipt {
        private final RollbackMaintenanceKeyInvocation invocation;private final ScopedHeldReceipt nativeReceipt;
        private final boolean confirmed,normallyReturned;
        private RollbackMaintenanceKeyReceipt(RollbackMaintenanceKeyInvocation invocation,ScopedHeldReceipt nativeReceipt,boolean confirmed,boolean normallyReturned){
            this.invocation=invocation;this.nativeReceipt=nativeReceipt;this.confirmed=confirmed;this.normallyReturned=normallyReturned;
        }
        public dev.veltraph.client.rewarp.mc.FarmMaintenanceCoordinator.SavedMaintenanceRollbackKey instruction(){return invocation.instruction;}
        public RollbackMaintenanceKeyInvocation invocationIdentity(){return invocation;}
        public ScopedHeldReceipt nativeReceipt(){return nativeReceipt;}
        public boolean confirmed(){return confirmed;}
        public boolean normallyReturned(){return normallyReturned;}
        public boolean normalReturnedNativeCurrent(){var carrier=invocation.carrier;return normallyReturned&&confirmed&&carrier.normalReturned
            &&invocation.matchesInstruction(invocation.instruction)&&carrier.nativeCurrent()&&nativeReceipt!=null&&nativeReceipt.confirmed()
            &&nativeReceipt.key()==carrier.key&&nativeReceipt.held()==invocation.instruction.desiredHeld()
            &&nativeReceipt.normalIdleHistoryCurrent()&&carrier.key.isDown()==nativeReceipt.held();}
    }
    public static RollbackMaintenanceKeyReceipt restoreSavedMaintenanceRollbackKey(dev.veltraph.client.rewarp.mc.FarmMaintenanceCoordinator.SavedMaintenanceRollbackKey instruction){
        if(instruction==null)throw new IllegalStateException("Original saved rollback instruction missing");
        var previous=SAVED_MAINTENANCE_KEY.get();
        if(previous!=null){previous.revoked=true;throw new IllegalStateException("Recursive original saved rollback");}
        if(McIssuedFarmReturnCleanup.active()||McIssuedFarmResumeGuard.active())throw new IllegalStateException("Conflicting original saved rollback");
        SavedMaintenanceKeyInvocation actual=null;RollbackMaintenanceKeyInvocation rollback=null;
        Throwable primary=null;
        try{
            actual=new SavedMaintenanceKeyInvocation(instruction);rollback=new RollbackMaintenanceKeyInvocation(actual);SAVED_MAINTENANCE_KEY.set(actual);
            final var selected=actual;
            // The Coordinator's private instruction claims this exact real method before any provider/observer.
            if(!instruction.claimNativeInstruction(rollback))throw new IllegalStateException("Original saved rollback source refused");
            var original=KeyHistory.capture(actual.key);actual.originalKey=original;
            if(original==null||!original.current())throw new IllegalStateException("Original saved key history unavailable");
            actual.rollbackObservation=RootNativeHistory.beginSavedMaintenanceRollbackKey(instruction,rollback);
            if(actual.rollbackObservation==null||!actual.nativeCurrent()||!original.carrier.normalIdleKeyAdvanced(actual.rollbackObservation.originalKeyHistory(),original.snapshot,java.util.List.of()))
                throw new IllegalStateException("Original Root saved key observation refused");
            dev.veltraph.client.observation.unexpected.UnexpectedActions.HeldPublicationObservation observed=null;
            try{observed=dev.veltraph.client.observation.unexpected.UnexpectedActions.beginHeldPublication(actual.client,actual.key,instruction.desiredHeld());}
            catch(RuntimeException|Error observationUnavailable){/* Typed Root evidence remains separate; MOD origin is unavailable. */}
            var scope=McScopedInputReadFence.capture(actual.client);
            if(scope==null||!actual.rollbackObservation.beforeNativeCurrent()||!instruction.nativeBeforeCurrent()||!original.current())
                throw new IllegalStateException("Original saved rollback changed before native dispatch");
            var issued=actual.returnedNative;int setters=0,restoration=0;
            boolean desired=instruction.desiredHeld();
            if(instruction.directSetter()){
                actual.started=true;setters++;
                savedMaintenanceNativeInput(actual,desired,false,scope,()->selected.key.setDown(desired));
            }else{
                if(actual.key.isDown()!=desired){
                    if(!actual.nativeCurrent()||!ownHistoryCurrent(original,actual.key,issued))throw new IllegalStateException("Original saved rollback key changed");
                    actual.started=true;setters++;
                    savedMaintenanceNativeInput(actual,desired,false,scope,()->selected.key.setDown(desired));
                    if(actual.key.isDown()!=desired){
                        if(!actual.nativeCurrent()||!ownHistoryCurrent(original,actual.key,issued))throw new IllegalStateException("Original saved rollback key changed");
                        setters++;savedMaintenanceNativeInput(actual,true,false,scope,()->selected.key.setDown(true));
                    }
                    if(actual.key.isDown()!=desired)throw new IllegalStateException("Key mapping refused absolute held state");
                }
                if(!desired&&actual.key instanceof ToggleKeyMapping toggle){
                    if(!actual.nativeCurrent()||!ownHistoryCurrent(original,actual.key,issued))throw new IllegalStateException("Original saved rollback key changed");
                    actual.started=true;restoration++;
                    savedMaintenanceNativeInput(actual,false,true,scope,()->toggle.shouldRestoreStateOnScreenClosed());
                }
            }
            var after=KeyHistory.capture(actual.key);
            boolean proved=after!=null&&after.key==original.key&&after.carrier==original.carrier&&issued.stream().allMatch(i->i.complete&&i.claimed&&!i.revoked)
                &&after.carrier.normalIdleKeyAdvanced(original.snapshot,after.snapshot,issued)&&actual.key.isDown()==desired&&actual.nativeCurrent();
            var originalBinding=instruction.originalBindingHistory();
            proved=proved&&originalBinding!=null&&dev.veltraph.client.observation.nativeinput.NativeKeyBindingHistory.current(actual.key,originalBinding);
            var receipt=new ScopedHeldReceipt(proved?ScopedStatus.CONFIRMED:ScopedStatus.REFUSED,actual.key,desired,setters,restoration);
            if(proved){receipt.history=after;receipt.actualHistory=java.util.List.copyOf(issued);receipt.savedRestorationBinding=originalBinding;}
            actual.normalReturned=true;
            var returned=new RollbackMaintenanceKeyReceipt(rollback,receipt,proved,true);
            RootNativeHistory.acceptSavedMaintenanceRollbackKeyNormal(actual.rollbackObservation,returned);
            // All nine actual setter observations are passive and success-only. No CanonicalToggleTrace is opened.
            try{if(observed!=null&&setters>0&&returned.normalReturnedNativeCurrent()&&receipt.normalIdleHistoryCurrent()&&scope.current())
                dev.veltraph.client.observation.unexpected.UnexpectedActions.completeHeldPublication(observed,
                    new HeldPublicationReceipt(actual.client,actual.key,desired,after,observed));}
            catch(RuntimeException|Error observationUnavailable){/* Never replace native result or grant origin to failed/unknown restoration. */}
            if(!returned.normalReturnedNativeCurrent())throw new IllegalStateException("Original saved rollback normal return refused");
            return returned;
        }catch(RuntimeException|Error failure){primary=failure;if(actual!=null)actual.revoked=true;throw failure;}
        finally{
            try{if(actual!=null)RootNativeHistory.closeSavedMaintenanceRollbackKey(actual.rollbackObservation,rollback);}
            catch(RuntimeException|Error observerFailure){
                if(actual!=null)actual.revoked=true;
                if(primary!=null)SavedMaintenanceFailurePreservation.suppressWithoutReplacing(primary,observerFailure);
                else throw observerFailure;
            }
            finally{if(actual!=null){actual.closed=true;SAVED_MAINTENANCE_KEY.remove();}}
        }
    }
    /** Selected only by the actual canonical McInput.setSneakHeld call during original Saved posture restoration. */
    static void setSavedMaintenancePosture(McInputService service,dev.veltraph.client.VeltraphBootstrap.RootOriginalNativeMaintenancePauseInvocation pause,boolean desired){
        if(service==null||pause==null||!postureEntryCaller()||!pause.nativePostureIssuingCurrent(service)
                ||desired!=pause.desiredPostureHeld())throw new IllegalStateException("Original Saved posture instruction missing");
        var previous=SAVED_MAINTENANCE_KEY.get();
        if(previous!=null){previous.revoked=true;throw new IllegalStateException("Recursive original Saved posture");}
        if(McIssuedFarmReturnCleanup.active()||McIssuedFarmResumeGuard.active())throw new IllegalStateException("Conflicting original Saved posture");
        SavedMaintenanceKeyInvocation actual=null;Throwable primary=null;
        try{
            actual=new SavedMaintenanceKeyInvocation(service,pause);SAVED_MAINTENANCE_KEY.set(actual);
            var original=KeyHistory.capture(actual.key);actual.originalKey=original;
            if(original==null||!original.current())throw new IllegalStateException("Original posture key history unavailable");
            actual.pauseObservation=RootNativeHistory.beginSavedMaintenancePosture(pause,actual,actual.client);
            if(actual.pauseObservation==null||!actual.nativeCurrent()
                ||!original.carrier.normalIdleKeyAdvanced(actual.pauseObservation.originalShiftHistory(),original.snapshot,java.util.List.of()))
                throw new IllegalStateException("Original posture native observation refused");
            dev.veltraph.client.observation.unexpected.UnexpectedActions.HeldPublicationObservation observed=null;
            try{observed=dev.veltraph.client.observation.unexpected.UnexpectedActions.beginHeldPublication(actual.client,actual.key,desired);}
            catch(RuntimeException|Error unavailable){/* A missing passive observation cannot create native evidence. */}
            var scope=McScopedInputReadFence.capture(actual.client);
            if(scope==null||!actual.nativeCurrent()||!original.current())throw new IllegalStateException("Original posture changed before native operation");
            var issued=actual.returnedNative;int setters=0,restoration=0;
            // Same absolute hold/toggle instructions as McHeldKeys.setHeld, using the selected SDK FIELD fence.
            var selected=actual;
            if(actual.key.isDown()!=desired){
                actual.started=true;setters++;
                savedMaintenanceNativeInput(actual,desired,false,scope,()->selected.key.setDown(desired));
                if(actual.key.isDown()!=desired){
                    if(!actual.nativeCurrent()||!ownHistoryCurrent(original,actual.key,issued))throw new IllegalStateException("Original posture history changed");
                    setters++;savedMaintenanceNativeInput(actual,true,false,scope,()->selected.key.setDown(true));
                }
                if(actual.key.isDown()!=desired)throw new IllegalStateException("Key mapping refused absolute held state");
            }
            if(!desired&&actual.key instanceof ToggleKeyMapping toggle){
                if(!actual.nativeCurrent()||!ownHistoryCurrent(original,actual.key,issued))throw new IllegalStateException("Original posture history changed");
                actual.started=true;restoration++;savedMaintenanceNativeInput(actual,false,true,scope,()->toggle.shouldRestoreStateOnScreenClosed());
            }
            var after=KeyHistory.capture(actual.key);var binding=actual.pauseObservation.originalShiftBinding();
            boolean proved=after!=null&&after.key==original.key&&after.carrier==original.carrier
                &&issued.stream().allMatch(i->i.complete&&i.claimed&&!i.revoked)
                &&after.carrier.normalIdleKeyAdvanced(original.snapshot,after.snapshot,issued)&&actual.key.isDown()==desired
                &&actual.nativeCurrent()&&binding!=null&&dev.veltraph.client.observation.nativeinput.NativeKeyBindingHistory.current(actual.key,binding);
            var receipt=new ScopedHeldReceipt(proved?ScopedStatus.CONFIRMED:ScopedStatus.REFUSED,actual.key,desired,setters,restoration);
            if(proved){receipt.history=after;receipt.actualHistory=java.util.List.copyOf(issued);receipt.savedRestorationBinding=binding;}
            actual.normalReturned=true;
            RootNativeHistory.acceptSavedMaintenancePostureNormal(actual.pauseObservation,actual,receipt);
            try{if(observed!=null&&setters>0&&proved&&actual.postureNormalReturnedCurrent()&&receipt.normalIdleHistoryCurrent()&&scope.current())
                dev.veltraph.client.observation.unexpected.UnexpectedActions.completeHeldPublication(observed,
                    new HeldPublicationReceipt(actual.client,actual.key,desired,after,observed));}
            catch(RuntimeException|Error unavailable){/* Passive completion does not change the real native result. */}
            if(!proved||!actual.postureNormalReturnedCurrent())throw new IllegalStateException("Original posture normal return refused");
        }catch(RuntimeException|Error failure){primary=failure;if(actual!=null)actual.revoked=true;throw failure;}
        finally{
            try{if(actual!=null)RootNativeHistory.closeSavedMaintenancePosture(actual.pauseObservation,actual);}
            catch(RuntimeException|Error observerFailure){if(actual!=null)actual.revoked=true;
                if(primary!=null)SavedMaintenanceFailurePreservation.suppressWithoutReplacing(primary,observerFailure);else throw observerFailure;}
            finally{if(actual!=null){actual.closed=true;SAVED_MAINTENANCE_KEY.remove();}}
        }
    }
    private static boolean postureEntryCaller(){return StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).walk(frames->{
        var caller=frames.filter(f->f.getDeclaringClass()!=McHeldKeys.class).findFirst().orElse(null);
        return caller!=null&&caller.getDeclaringClass()==McInputService.class&&caller.getMethodName().equals("setSneakHeld");});}
    /** Saved-only dispatcher; ordinary nativeInput/Breaking/KeyC/R68 bodies remain unchanged. */
    private static void savedMaintenanceNativeInput(SavedMaintenanceKeyInvocation context,boolean requested,boolean restore,McScopedInputReadFence scope,Runnable effect){
        if(SAVED_MAINTENANCE_KEY.get()!=context||context.pendingNative!=null||NATIVE_INPUT.get()!=null||!context.finalNativeCurrent()){
            context.revoked=true;throw new IllegalStateException("Original saved native dispatcher refused");}
        var nativeCall=new NativeInputInvocation(context.key,requested,restore,scope);context.pendingNative=nativeCall;context.pendingReturned=false;context.fieldWrites=0;context.methodEntered=false;
        NATIVE_INPUT.set(nativeCall);
        try{
            if(!scope.current()||!context.finalNativeCurrent())throw new IllegalStateException("Original saved native scope refused");
            effect.run();nativeCall.nativeReturned=true;context.pendingReturned=true;
            if(context.rollbackInvocation!=null)cacheRollbackPrefix(context,nativeCall);
            else if(context.pause!=null)cacheSavedPauseNegativePrefix(context,nativeCall);
            nativeCall.complete=nativeCall.claimed&&!nativeCall.revoked&&scope.current()&&context.finalNativeCurrent();context.returnedNative.add(nativeCall);
            if(!nativeCall.complete)throw new IllegalStateException("Original saved native completion refused");
        }catch(RuntimeException|Error failure){context.revoked=true;throw failure;}
        finally{NATIVE_INPUT.remove();context.pendingNative=null;context.pendingReturned=false;context.savedSuper=false;}
    }
    private static final ThreadLocal<SavedNativeMethodCall> SAVED_NATIVE_METHOD=new ThreadLocal<>();
    private static final ThreadLocal<SavedSuperCall> SAVED_NATIVE_SUPER=new ThreadLocal<>();
    private static final Object SAVED_ORDINARY_FALLBACK=new Object();
    /** Retains selected classification even after its source is revoked; it can never become an ordinary write. */
    private static final class SavedNativeMethodCall {
        final SavedMaintenanceKeyInvocation context;final NativeInputInvocation input;final SavedNativeMethodCall parent;
        final KeyMapping receiver;final boolean requested,restore,base,selected;private boolean closed;
        private SavedNativeMethodCall(SavedMaintenanceKeyInvocation context,NativeInputInvocation input,SavedNativeMethodCall parent,
                KeyMapping receiver,boolean requested,boolean restore,boolean base,boolean selected){
            this.context=context;this.input=input;this.parent=parent;this.receiver=receiver;this.requested=requested;this.restore=restore;this.base=base;this.selected=selected;
        }
    }
    /** Actual WrapMethod entry precedes native HEAD observers. Stack origin alone cannot select an operation. */
    public static Object beginSavedMaintenanceNativeMethod(KeyMapping receiver,boolean requested,boolean restore,boolean base){
        var context=SAVED_MAINTENANCE_KEY.get();var parent=SAVED_NATIVE_METHOD.get();
        if(context!=null&&context.ordinaryFallbackDepth>0){
            context.revoked=true;if(context.pendingNative!=null)context.pendingNative.revoked=true;
            context.ordinaryFallbackDepth=SavedMaintenanceNativeMethodShape.enterOrdinaryFallback(context.ordinaryFallbackDepth);return SAVED_ORDINARY_FALLBACK;
        }
        if(parent==null&&(context==null||context.pendingNative==null))return null;
        var input=context==null?null:context.pendingNative;var superCall=SAVED_NATIVE_SUPER.get();
        boolean outer=SavedMaintenanceNativeMethodShape.outer(context!=null,parent==null,context!=null&&!context.methodEntered,
            input,NATIVE_INPUT.get(),input==null?null:input.key,receiver,input!=null&&input.requested,requested,input!=null&&input.restore,restore,
            base,receiver.getClass()==KeyMapping.class,input!=null&&input.thread==Thread.currentThread());
        boolean child=SavedMaintenanceNativeMethodShape.child(context!=null,base,parent!=null&&parent.selected&&!parent.closed,
            superCall!=null&&!superCall.entered,superCall==null?null:superCall.parent,parent,superCall==null?null:superCall.context,context,
            superCall==null?null:superCall.input,input,NATIVE_INPUT.get(),superCall==null?null:superCall.receiver,receiver,
            superCall!=null&&superCall.requested,requested,parent==null?null:parent.receiver,restore,parent!=null&&parent.restore);
        boolean selected=outer||child;
        if(outer)context.methodEntered=true;
        if(child)superCall.entered=true;
        if(!selected&&context!=null){context.revoked=true;if(input!=null)input.revoked=true;}
        SavedNativeMethodCall call;
        try{call=new SavedNativeMethodCall(context,input,parent,receiver,requested,restore,base,selected);SAVED_NATIVE_METHOD.set(call);}
        catch(RuntimeException|Error allocationFailure){
            if(context!=null){context.revoked=true;if(input!=null)input.revoked=true;}
            if(selected)throw allocationFailure;
            if(context==null)throw allocationFailure;
            context.ordinaryFallbackParent=parent;context.ordinaryFallbackDepth=1;SAVED_NATIVE_METHOD.remove();return SAVED_ORDINARY_FALLBACK;
        }
        if(selected){
            Class<?> owner=base?KeyMapping.class:ToggleKeyMapping.class;
            String hook=base?"veltraph$savedKeyMethod":restore?"veltraph$savedRestoreMethod":"veltraph$savedToggleMethod";
            try{if(!savedNativeHookCaller(owner,hook)){context.revoked=true;input.revoked=true;}}
            catch(RuntimeException|Error unavailable){context.revoked=true;input.revoked=true;}
        }
        return call;
    }
    public static void endSavedMaintenanceNativeMethod(Object token){
        if(token==SAVED_ORDINARY_FALLBACK){var context=SAVED_MAINTENANCE_KEY.get();
            if(context==null||context.ordinaryFallbackDepth<=0)throw new IllegalStateException("Saved ordinary fallback custody missing");
            context.ordinaryFallbackDepth=SavedMaintenanceNativeMethodShape.leaveOrdinaryFallback(context.ordinaryFallbackDepth);
            if(context.ordinaryFallbackDepth==0){var parent=context.ordinaryFallbackParent;context.ordinaryFallbackParent=null;if(parent==null)SAVED_NATIVE_METHOD.remove();else SAVED_NATIVE_METHOD.set(parent);}return;}
        if(!(token instanceof SavedNativeMethodCall call))return;
        if(SAVED_NATIVE_METHOD.get()!=call){if(call.context!=null)call.context.revoked=true;return;}
        call.closed=true;if(call.parent==null)SAVED_NATIVE_METHOD.remove();else SAVED_NATIVE_METHOD.set(call.parent);
    }
    public static void closeSavedMaintenanceNativeMethod(Object token,Throwable primary){
        try{endSavedMaintenanceNativeMethod(token);}
        catch(RuntimeException|Error failure){var context=SAVED_MAINTENANCE_KEY.get();if(context!=null)context.revoked=true;
            if(primary!=null)SavedMaintenanceFailurePreservation.suppressWithoutReplacing(primary,failure);else throw failure;}
    }
    /** Classify the real operation before source validation: ordinary nested effects delegate and revoke the old source. */
    public static void beforeSavedMaintenanceField(KeyMapping receiver,boolean restore){
        var call=SAVED_NATIVE_METHOD.get();var context=SAVED_MAINTENANCE_KEY.get();
        if(call==null){if(context!=null&&context.ordinaryFallbackDepth>0){context.revoked=true;return;}if(context!=null&&context.pendingNative!=null){context.revoked=true;throw new IllegalStateException("Saved native field classification unavailable");}return;}
        if(!call.selected){if(context!=null)context.revoked=true;return;}
        context=call.context;var actual=call.input;
        if(!savedNativeHookCaller(restore?ToggleKeyMapping.class:KeyMapping.class,restore?"veltraph$savedRestoreField":"veltraph$savedKeyField")
            ||call.closed||context==null||actual==null||receiver!=call.receiver||call.restore!=restore||restore==call.base
            ||SAVED_MAINTENANCE_KEY.get()!=context||context.pendingNative!=actual||NATIVE_INPUT.get()!=actual||!actual.claimed||actual.revoked
            ||receiver!=context.key||actual.key!=receiver||actual.restore!=restore||context.pendingReturned||context.fieldWrites!=0
            ||!context.finalNativeCurrent()){
            if(context!=null)context.revoked=true;throw new IllegalStateException("Original saved native field refused");}
        if(context.cleanupInvocation!=null&&!restore&&call.requested){context.revoked=true;throw new IllegalStateException("Rollback negative cleanup cannot write true");}
        context.fieldWrites++;
    }
    private static boolean savedNativeHookCaller(Class<?> owner,String method){
        return StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).walk(frames->{var caller=frames.filter(f->f.getDeclaringClass()!=McHeldKeys.class).findFirst().orElse(null);
            return caller!=null&&caller.getDeclaringClass()==owner&&(caller.getMethodName().equals(method)||caller.getMethodName().endsWith("$"+method));});
    }
    private static final class SavedSuperCall {
        final SavedMaintenanceKeyInvocation context;final NativeInputInvocation input;final SavedNativeMethodCall parent;
        final ToggleKeyMapping receiver;final boolean requested;private boolean entered;
        private SavedSuperCall(SavedMaintenanceKeyInvocation context,NativeInputInvocation input,SavedNativeMethodCall parent,ToggleKeyMapping receiver,boolean requested){
            this.context=context;this.input=input;this.parent=parent;this.receiver=receiver;this.requested=requested;
        }
    }
    /** Cookie from the actual selected invokespecial edge, with the exact native-computed base argument. */
    public static Object beginSavedMaintenanceSuper(ToggleKeyMapping receiver,boolean requested){
        var call=SAVED_NATIVE_METHOD.get();var context=SAVED_MAINTENANCE_KEY.get();
        if(call==null||!call.selected){if(context!=null)context.revoked=true;return null;}
        var input=call.input;
        if(!savedNativeHookCaller(ToggleKeyMapping.class,"veltraph$savedSuper")||call.closed||call.base||call.restore||call.receiver!=receiver
            ||context!=call.context||input==null||context.pendingNative!=input||NATIVE_INPUT.get()!=input||SAVED_NATIVE_SUPER.get()!=null
            ||!input.claimed||!context.finalNativeCurrent()){
            if(context!=null)context.revoked=true;throw new IllegalStateException("Original saved native super edge refused");}
        var cookie=new SavedSuperCall(context,input,call,receiver,requested);context.savedSuper=true;SAVED_NATIVE_SUPER.set(cookie);return cookie;
    }
    public static void endSavedMaintenanceSuper(Object token){
        if(!(token instanceof SavedSuperCall actual))return;
        if(SAVED_NATIVE_SUPER.get()!=actual||SAVED_NATIVE_METHOD.get()!=actual.parent)actual.context.revoked=true;
        actual.context.savedSuper=false;SAVED_NATIVE_SUPER.remove();
    }
    public static void closeSavedMaintenanceSuper(Object token,Throwable primary){
        try{endSavedMaintenanceSuper(token);}
        catch(RuntimeException|Error failure){var context=SAVED_MAINTENANCE_KEY.get();if(context!=null)context.revoked=true;
            if(primary!=null)SavedMaintenanceFailurePreservation.suppressWithoutReplacing(primary,failure);else throw failure;}
    }
    /** Cleanup-only actual SDK prefix, constructed before any post-return source observer. */
    static final class RollbackNativePrefixReceipt {
        final RollbackMaintenanceKeyInvocation invocation;final SavedMaintenanceKeyInvocation pauseInvocation;final KeyHistory history;final NativeInputInvocation returned;
        final boolean held;final Object binding;final boolean ownTrue;
        private RollbackNativePrefixReceipt(RollbackMaintenanceKeyInvocation invocation,KeyHistory history,NativeInputInvocation returned,
                boolean held,Object binding,boolean ownTrue){this.invocation=invocation;pauseInvocation=null;this.history=history;this.returned=returned;this.held=held;this.binding=binding;this.ownTrue=ownTrue;}
        private RollbackNativePrefixReceipt(SavedMaintenanceKeyInvocation pauseInvocation,KeyHistory history,NativeInputInvocation returned,
                boolean held,Object binding,boolean ownTrue){invocation=null;this.pauseInvocation=pauseInvocation;this.history=history;this.returned=returned;this.held=held;this.binding=binding;this.ownTrue=ownTrue;}
        boolean current(){return returned.nativeReturned&&returned.claimed&&!returned.revoked&&history.current()
            &&history.key.isDown()==held&&binding!=null&&dev.veltraph.client.observation.nativeinput.NativeKeyBindingHistory.current(history.key,binding);}
        boolean advances(Object before){return current()&&history.carrier.normalIdleKeyAdvanced(before,history.snapshot,java.util.List.of(returned));}
        Object snapshot(){return history.snapshot;}KeyMapping key(){return history.key;}
    }
    private static void cacheRollbackPrefix(SavedMaintenanceKeyInvocation carrier,NativeInputInvocation returned){
        if(carrier.rollbackInvocation==null||carrier.rollbackObservation==null||!returned.nativeReturned||!returned.claimed||returned.revoked)return;
        var after=KeyHistory.capture(carrier.key);var binding=carrier.rollbackInstruction.originalBindingHistory();
        if(after==null||after.carrier!=carrier.originalKey.carrier)return;
        var prefix=new java.util.ArrayList<NativeInputInvocation>(carrier.returnedNative);prefix.add(returned);
        if(!after.carrier.normalIdleKeyAdvanced(carrier.originalKey.snapshot,after.snapshot,prefix))return;
        var receipt=new RollbackNativePrefixReceipt(carrier.rollbackInvocation,after,returned,carrier.key.isDown(),binding,
            carrier.key.isDown()&&prefix.stream().anyMatch(c->c.nativeReturned&&c.claimed&&!c.revoked&&!c.restore&&c.requested));
        RootNativeHistory.retainRollbackNativePrefix(carrier.rollbackObservation,receipt);
    }
    /** Genuine posture return cached before the normal pause observer; this never certifies a completed pause. */
    private static void cacheSavedPauseNegativePrefix(SavedMaintenanceKeyInvocation carrier,NativeInputInvocation returned){
        if(carrier.pause==null||carrier.pauseObservation==null||!returned.nativeReturned||!returned.claimed||returned.revoked)return;
        var after=KeyHistory.capture(carrier.key);var binding=carrier.pauseObservation.originalShiftBinding();
        if(after==null||carrier.originalKey==null||after.carrier!=carrier.originalKey.carrier)return;
        var prefix=new java.util.ArrayList<NativeInputInvocation>(carrier.returnedNative);prefix.add(returned);
        if(!after.carrier.normalIdleKeyAdvanced(carrier.originalKey.snapshot,after.snapshot,prefix))return;
        var receipt=new RollbackNativePrefixReceipt(carrier,after,returned,carrier.key.isDown(),binding,
            carrier.key.isDown()&&prefix.stream().anyMatch(c->c.nativeReturned&&c.claimed&&!c.revoked&&!c.restore&&c.requested));
        RootNativeHistory.retainSavedPauseNativePrefix(carrier.pauseObservation,receipt);
    }
    /** Privately selected failed-rollback negative key instruction. It never represents Resume or a true publication. */
    public static final class RollbackCleanupKeyInvocation {
        private final dev.veltraph.client.VeltraphBootstrap.RootSavedMaintenanceRollbackCleanupInvocation source;
        private final RootNativeHistory.RollbackPrefixCleanupPlan plan;private final int index;
        private final Thread thread=Thread.currentThread();private SavedMaintenanceKeyInvocation carrier;private boolean closed;
        private RollbackCleanupKeyInvocation(dev.veltraph.client.VeltraphBootstrap.RootSavedMaintenanceRollbackCleanupInvocation source,
                RootNativeHistory.RollbackPrefixCleanupPlan plan,int index){this.source=source;this.plan=plan;this.index=index;}
        public dev.veltraph.client.VeltraphBootstrap.RootSavedMaintenanceRollbackCleanupInvocation sourceIdentity(){return source;}
        public net.minecraft.client.Minecraft client(){return source.client();}public KeyMapping key(){return plan.key(index);}
        public boolean matchesSource(dev.veltraph.client.VeltraphBootstrap.RootSavedMaintenanceRollbackCleanupInvocation exact){return exact==source&&!closed
            &&Thread.currentThread()==thread&&carrier!=null&&carrier.cleanupInvocation==this&&SAVED_MAINTENANCE_KEY.get()==carrier;}
        private boolean matchesCarrier(SavedMaintenanceKeyInvocation exact){return exact==carrier&&!exact.closed&&!exact.revoked&&matchesSource(source);}
        private boolean sourceCurrent(){return source.beforeCleanupNativeCurrent()&&plan.otherCurrent(index);}
        private boolean finalSourceCurrent(){return matchesSource(source)&&source.finalCleanupKeyNativeCurrent()&&plan.otherCurrent(index);}
    }
    /** Once-only selected FALSE cleanup against an old real true prefix. No public Boolean supplies an effect fence. */
    static ScopedHeldReceipt clearRollbackPrefix(dev.veltraph.client.VeltraphBootstrap.RootSavedMaintenanceRollbackCleanupInvocation source,
            RootNativeHistory.RollbackPrefixCleanupPlan plan,int index){
        var previous=SAVED_MAINTENANCE_KEY.get();
        if(previous!=null){previous.revoked=true;throw new IllegalStateException("Recursive rollback prefix cleanup");}
        if(source==null||plan==null||!plan.claim(index,source))return null;
        SavedMaintenanceKeyInvocation carrier=null;RollbackCleanupKeyInvocation instruction=null;Throwable primary=null;
        try{
            instruction=new RollbackCleanupKeyInvocation(source,plan,index);carrier=new SavedMaintenanceKeyInvocation(instruction);instruction.carrier=carrier;
            carrier.originalKey=plan.originalOwnedHistory(index);SAVED_MAINTENANCE_KEY.set(carrier);final var selected=carrier;
            if(carrier.originalKey==null||!carrier.originalKey.current()||!source.claimNativeKeyCleanup(instruction)||!carrier.nativeCurrent())return null;
            var scope=McScopedInputReadFence.capture(carrier.client);
            if(scope==null||!carrier.finalNativeCurrent())return null;
            int setters=0,restoration=0;
            if(carrier.key.isDown()){
                carrier.started=true;setters++;savedMaintenanceNativeInput(carrier,false,false,scope,()->selected.key.setDown(false));
                if(carrier.key.isDown()){
                    if(!(carrier.key instanceof ToggleKeyMapping)||!carrier.finalNativeCurrent())return null;
                    setters++;savedMaintenanceNativeInput(carrier,true,false,scope,()->selected.key.setDown(true));
                }
                if(carrier.key.isDown())return null;
            }
            if(carrier.key instanceof ToggleKeyMapping toggle){carrier.started=true;restoration++;
                savedMaintenanceNativeInput(carrier,false,true,scope,()->toggle.shouldRestoreStateOnScreenClosed());}
            var after=KeyHistory.capture(carrier.key);var calls=carrier.returnedNative;
            if(after==null||!calls.stream().allMatch(c->c.complete&&c.claimed&&!c.revoked)||!carrier.finalNativeCurrent()||carrier.key.isDown()
                ||!after.carrier.normalIdleKeyAdvanced(carrier.originalKey.snapshot,after.snapshot,calls))return null;
            var receipt=new ScopedHeldReceipt(ScopedStatus.CONFIRMED,carrier.key,false,setters,restoration);
            receipt.history=after;receipt.actualHistory=java.util.List.copyOf(calls);receipt.savedRestorationBinding=plan.binding(index);
            carrier.normalReturned=true;return receipt;
        }catch(RuntimeException|Error failure){primary=failure;if(carrier!=null)carrier.revoked=true;throw failure;}
        finally{if(instruction!=null)instruction.closed=true;if(carrier!=null){carrier.closed=true;SAVED_MAINTENANCE_KEY.remove();}}
    }
    static final class KeyHistory {
        final KeyMapping key;final NormalIdleKeyCarrier carrier;final Object snapshot;
        private KeyHistory(KeyMapping key,NormalIdleKeyCarrier carrier,Object snapshot){this.key=key;this.carrier=carrier;this.snapshot=snapshot;}
        static KeyHistory capture(KeyMapping key){if(key==null||(key.getClass()!=KeyMapping.class&&key.getClass()!=ToggleKeyMapping.class)||!(key instanceof NormalIdleKeyCarrier carrier))return null;
            var snapshot=carrier.normalIdleKeySnapshot();return snapshot==null?null:new KeyHistory(key,carrier,snapshot);}
        boolean current(){return carrier.normalIdleKeyCurrent(snapshot);}
    }
}
