package dev.veltraph.client.service.mc;

import dev.veltraph.client.service.InputService;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

/**
 * Minecraft-backed {@link InputService}. Drives the vanilla movement key bindings by holding/releasing
 * their pressed state ({@link KeyMapping#setDown(boolean)}), exactly as if the user held the key.
 *
 * <p>{@link #releaseAllMovement()} releases every movement, sneak, and jump key; the attack/break key
 * is owned by {@link McBlockBreakingService}, not here.
 */
public final class McInputService implements InputService {

    /** Actual false method-entry observation only; no input permission and no public constructor. */
    public static final class NegativeInputObservation {
        private final RootNativeInputEpisode episode;private final boolean wartReach;
        private NegativeInputObservation(RootNativeInputEpisode episode,boolean wartReach){this.episode=episode;this.wartReach=wartReach;}
        public boolean currentFor(McInputService exact){return episode.negativeCurrent(exact);}
        public boolean matchesOriginalFarm(dev.veltraph.client.farming.module.FarmModule exact){return exact!=null
            &&(!wartReach||exact instanceof dev.veltraph.client.farming.module.WartCropsFarmModule);}
    }
    static NegativeInputObservation observeActualNegativeEntry(RootNativeInputEpisode actual){
        if(actual==null||!actual.negativeEntryCurrent())return null;
        var caller=StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).walk(frames->frames.skip(1).findFirst().orElse(null));
        if(caller==null||caller.getDeclaringClass()!=RootNativeInputEpisode.class||!caller.getMethodName().equals("enter0")
            ||actual==null||RootNativeInputEpisode.peek()!=actual)return null;
        var frames=StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).walk(stream->stream.toList());
        for(int i=0;i<frames.size();i++){
            var frame=frames.get(i);var type=frame.getDeclaringClass();
            if(type==dev.veltraph.client.farming.module.FarmModule.class){
                return frame.getMethodName().equals("releaseInputs")&&i+1<frames.size()
                    &&frames.get(i+1).getDeclaringClass()==type&&frames.get(i+1).getMethodName().equals("suspendForCycleReset")
                    ?new NegativeInputObservation(actual,false):null;
            }
            if(type==dev.veltraph.client.farming.movement.McReachableCropCompletion.class){
                return frame.getMethodName().equals("cancel")&&i+2<frames.size()
                    &&frames.get(i+1).getDeclaringClass()==dev.veltraph.client.farming.module.WartCropsFarmModule.class
                    &&frames.get(i+1).getMethodName().equals("onBeforeFarmSuspension")
                    &&frames.get(i+2).getDeclaringClass()==dev.veltraph.client.farming.module.FarmModule.class
                    &&frames.get(i+2).getMethodName().equals("suspendForCycleReset")?new NegativeInputObservation(actual,true):null;
            }
            if(type==dev.veltraph.client.farming.module.WartCropsFarmModule.class)return null;
        }
        return null;
    }
    public static NegativeInputObservation pendingNegativeObservation(McInputService exact){
        var actual=RootNativeInputEpisode.peek();var observed=actual==null?null:actual.negativeObservation;
        return observed!=null&&observed.currentFor(exact)?observed:null;
    }
    private final Minecraft client;
    private boolean normalReleaseCallback;
    private final Runnable compatibilityRelease;
    private static dev.veltraph.client.VeltraphBootstrap.RootInputReleaseRegistration canonicalRootRelease;
    private static String cachedRootReleaseRouting="NOT_OBSERVED";
    /** Read-only cached routing result, never invokes a provider/callback or grants scope. */
    public static String cachedRootReleaseRouting(){return cachedRootReleaseRouting;}
    /** Only the private real Bootstrap registration can affiliate its immutable canonical FarmServices input. */
    public static boolean registerCanonicalRootRelease(dev.veltraph.client.VeltraphBootstrap.RootInputReleaseRegistration registration){
        if(registration==null)return false;var actual=registration.actualInput();
        if(actual==null||!registration.current(actual,actual.client))return false;
        if(canonicalRootRelease==registration)return true;
        canonicalRootRelease=registration;return true;
    }
    public boolean actualNormalReleaseCurrent(){return normalReleaseCallback&&client.isSameThread();}

    public McInputService(Minecraft client) {
        this.client = client;
        compatibilityRelease=()->{
            normalReleaseCallback=true;try{
                var normal=McOwnedRotation.normalRootInputDrop(this);
                if(normal!=null){normal.releaseActual(this);return;}
            releaseAllMovement();
            if(client.options!=null) {
                McHeldKeys.setHeld(client.options.keyAttack,false);
                McHeldKeys.setHeld(client.options.keyUse,false);
            }
            }finally{normalReleaseCallback=false;}
        };
        dev.veltraph.client.input.ScriptingInputLock.onRelease(()->{
            var registration=canonicalRootRelease;
            var canonical=registration==null?null:registration.actualInput();
            if(canonical!=null&&registration.current(canonical,client)){
                cachedRootReleaseRouting="ACTUAL_CANONICAL_ROOT_CALLBACK";
                canonical.compatibilityRelease.run();return;
            }
            cachedRootReleaseRouting="ORIGINAL_CONSTRUCTOR_CALLBACK_UNKNOWN_OR_REPLACED_ROOT";
            compatibilityRelease.run();
        });
    }

    private record FarmHeld(dev.veltraph.client.farming.module.FarmModule owner,
            McFarmOwnedCleanup.NativeScope scope, McHeldKeys.ScopedHeldReceipt receipt) {}
    // One latest publication per actual mapping, not a growing history of stopped modules.
    private final java.util.IdentityHashMap<KeyMapping,FarmHeld> farmHeld = new java.util.IdentityHashMap<>();
    static boolean canonicalFarmRootCurrent(Minecraft actualClient) {
        var registration=canonicalRootRelease;var input=registration==null?null:registration.actualInput();
        return input!=null&&registration.current(input,actualClient);
    }
    static boolean canonicalFarmRotationCurrent(McRotationService rotation,Minecraft actualClient) {
        var registration=canonicalRootRelease;
        return registration!=null&&registration.currentFarmRotation(rotation,actualClient);
    }
    static boolean canonicalFarmBreakingCurrent(McBlockBreakingService breaking,Minecraft actualClient) {
        var registration=canonicalRootRelease;
        return registration!=null&&registration.currentFarmBreaking(breaking,actualClient);
    }
    static boolean canonicalFarmInventoryCurrent(McInventoryService inventory,Minecraft actualClient) {
        var registration=canonicalRootRelease;
        return registration!=null&&registration.currentFarmInventory(inventory,actualClient);
    }
    private boolean canonicalRootCurrent() {
        var registration=canonicalRootRelease;
        return registration!=null&&registration.current(this,client);
    }
    /** Private actual canonical scoped normal return; no caller can construct this observation. */
    static final class KeyNormalReturn {
        private final McInputService service;private final McFarmOwnedCleanup.NativeScope scope;private final RootNativeInputEpisode issuing;
        final McHeldKeys.ScopedHeldReceipt receipt;
        private KeyNormalReturn(McInputService service,McFarmOwnedCleanup.NativeScope scope,McHeldKeys.ScopedHeldReceipt receipt){this.service=service;this.scope=scope;this.receipt=receipt;issuing=RootNativeInputEpisode.peek();}
        dev.veltraph.client.core.Module originalModule(){return scope.farm;}
        boolean nativeSourceCurrent(){return issuing!=null&&issuing.matches(service,scope,receipt)&&service.canonicalRootCurrent()&&(scope.activeReleaseCurrent()||scope.stopCurrent());}
    }
    private void observeOriginalKeyReturn(McFarmOwnedCleanup.NativeScope scope,McHeldKeys.ScopedHeldReceipt actual){
        try {RootNativeHistory.normalKeyReturned(new KeyNormalReturn(this,scope,actual));}
        catch(RuntimeException|Error unavailable){/* Passive observation cannot change original publication semantics. */}
    }
    static McInputService canonicalFarmInput(Minecraft client){
        var registration=canonicalRootRelease;var exact=registration==null?null:registration.actualInput();
        return exact!=null&&registration.current(exact,client)?exact:null;
    }
    static McBlockBreakingService canonicalFarmBreaking(Minecraft client){
        var registration=canonicalRootRelease;var exact=registration==null?null:registration.actualBreaking();
        return exact!=null&&registration.currentFarmBreaking(exact,client)?exact:null;
    }
    /** Old private custody only; the SDK transition can retire it but cannot create a held receipt. */
    static final class GuiHeldRetirement {
        private final McInputService service;private final RootNativeHistory.GuiTicket ticket;
        private final java.util.IdentityHashMap<KeyMapping,FarmHeld> records;
        private GuiHeldRetirement(McInputService service,RootNativeHistory.GuiTicket ticket,java.util.IdentityHashMap<KeyMapping,FarmHeld> records){this.service=service;this.ticket=ticket;this.records=records;}
    }
    static GuiHeldRetirement captureGuiHeldRetirement(RootNativeHistory.GuiTicket ticket,Minecraft client){
        var registration=canonicalRootRelease;var service=registration==null?null:registration.actualInput();
        if(service==null||!service.canonicalRootCurrent()||!RootNativeHistory.guiCaptureCurrent(ticket,client))return null;
        var records=new java.util.IdentityHashMap<KeyMapping,FarmHeld>();
        for(var entry:service.farmHeld.entrySet()){
            var key=entry.getKey();var exact=entry.getValue();
            if(exact==null||exact.scope().farm!=exact.owner()||!RootNativeHistory.guiOriginalCurrent(ticket,exact.owner())
                    ||exact.receipt().key()!=key||!exact.receipt().held()||!exact.receipt().confirmed()
                    ||!exact.receipt().normalIdleHistoryCurrent()||RootNativeHistory.guiBeforeKey(ticket,key)==null)return null;
            records.put(key,exact);
        }
        return new GuiHeldRetirement(service,ticket,records);
    }
    static boolean retireGuiClearedOwnedKeys(GuiHeldRetirement capture,RootNativeHistory.GuiTicket ticket,
            dev.veltraph.client.observation.nativeinput.NativeGuiKeyResetLineage.NormalTransition transition){
        if(capture==null||capture.ticket!=ticket||!capture.service.canonicalRootCurrent()
                ||!RootNativeHistory.guiRetirementCurrent(ticket,transition,capture.service.client)
                ||capture.service.farmHeld.size()!=capture.records.size())return false;
        for(var entry:capture.records.entrySet()){
            var key=entry.getKey();if(capture.service.farmHeld.get(key)!=entry.getValue()
                    ||transition.held(key)||!transition.advances(key,RootNativeHistory.guiBeforeKey(ticket,key)))return false;
        }
        // No native write: retire only the exact old records whose real SDK false reset normally returned.
        for(var entry:capture.records.entrySet())capture.service.farmHeld.remove(entry.getKey(),entry.getValue());
        return true;
    }
    static boolean adoptRetainedResumeOwnedKeys(RootNativeHistory.RetainedResumeCustody actual){
        var registration=canonicalRootRelease;var service=registration==null?null:registration.actualInput();
        if(service==null||!service.canonicalRootCurrent())return false;
        var beforeOptions=service.farmHeldOptions;
        var before=new java.util.IdentityHashMap<KeyMapping,FarmHeld>(service.farmHeld);
        if(beforeOptions!=service.client.options&&!before.isEmpty())return false;
        if(!actual.current())return false;
        var next=new java.util.IdentityHashMap<KeyMapping,FarmHeld>();
        for(var key:new KeyMapping[]{service.client.options.keyUp,service.client.options.keyDown,service.client.options.keyLeft,
                service.client.options.keyRight,service.client.options.keyJump,service.client.options.keyShift,service.client.options.keySprint}){
            var receipt=actual.receipt(key);if(receipt==null||receipt.key()!=key||!receipt.confirmed()||!receipt.normalIdleHistoryCurrent())return false;
            if(!receipt.held())continue;
            if(receipt.normallyReturnedOwnedHeldAttempt())next.put(key,new FarmHeld(actual.farm,actual.scope,receipt));
            else{var old=before.get(key);
                // A zero-effect true can retain only already-owned, still-current private native custody.
                if(old!=null&&old.owner()==actual.farm&&old.receipt().normallyReturnedOwnedHeldAttempt()&&old.receipt().normalIdleHistoryCurrent())
                    next.put(key,new FarmHeld(actual.farm,actual.scope,old.receipt()));
            }
        }
        if(!actual.current()||!service.canonicalRootCurrent())return false;
        if(service.farmHeldOptions!=beforeOptions)return false;
        for(var entry:next.entrySet())if(service.farmHeld.get(entry.getKey())!=before.get(entry.getKey()))return false;
        // No callbacks after these exact private-record identity checks.
        service.farmHeldOptions=service.client.options;
        for(var entry:next.entrySet())service.farmHeld.put(entry.getKey(),entry.getValue());
        return true;
    }
    static boolean adoptSavedResumeOwnedKeys(RootNativeHistory.SavedResumeCustody actual){
        var registration=canonicalRootRelease;var service=registration==null?null:registration.actualInput();
        if(service==null||!service.canonicalRootCurrent())return false;
        var beforeOptions=service.farmHeldOptions;
        var before=new java.util.IdentityHashMap<KeyMapping,FarmHeld>(service.farmHeld);
        if(beforeOptions!=service.client.options&&!before.isEmpty())return false;
        if(!actual.current())return false;
        var next=new java.util.IdentityHashMap<KeyMapping,FarmHeld>();
        for(var key:new KeyMapping[]{service.client.options.keyUp,service.client.options.keyDown,service.client.options.keyLeft,
                service.client.options.keyRight,service.client.options.keyJump,service.client.options.keyShift,service.client.options.keySprint}){
            var receipt=actual.receipt(key);if(receipt==null||receipt.key()!=key||!receipt.confirmed()||!receipt.normalIdleHistoryCurrent())return false;
            if(!receipt.held())continue;
            if(receipt.normallyReturnedOwnedHeldAttempt())next.put(key,new FarmHeld(actual.farm,actual.scope,receipt));
            else{var old=before.get(key);
                // A zero-effect true can retain only already-owned, still-current private native custody.
                if(old!=null&&old.owner()==actual.farm&&old.receipt().normallyReturnedOwnedHeldAttempt()&&old.receipt().normalIdleHistoryCurrent())
                    next.put(key,new FarmHeld(actual.farm,actual.scope,old.receipt()));
            }
        }
        if(!actual.current()||!service.canonicalRootCurrent())return false;
        if(service.farmHeldOptions!=beforeOptions)return false;
        for(var entry:next.entrySet())if(service.farmHeld.get(entry.getKey())!=before.get(entry.getKey()))return false;
        // No callbacks after these exact private-record identity checks.
        service.farmHeldOptions=service.client.options;
        for(var entry:next.entrySet())service.farmHeld.put(entry.getKey(),entry.getValue());
        return true;
    }
    static boolean adoptSavedRollbackOwnedKeys(RootNativeHistory.SavedRollbackCustody actual){
        var registration=canonicalRootRelease;var service=registration==null?null:registration.actualInput();
        if(service==null||!service.canonicalRootCurrent())return false;
        var beforeOptions=service.farmHeldOptions;
        var before=new java.util.IdentityHashMap<KeyMapping,FarmHeld>(service.farmHeld);
        if(beforeOptions!=service.client.options&&!before.isEmpty())return false;
        if(!actual.current())return false;
        var next=new java.util.IdentityHashMap<KeyMapping,FarmHeld>();
        for(var key:new KeyMapping[]{service.client.options.keyUp,service.client.options.keyDown,service.client.options.keyLeft,
                service.client.options.keyRight,service.client.options.keyJump,service.client.options.keyShift,service.client.options.keySprint}){
            var receipt=actual.receipt(key);if(receipt==null||receipt.key()!=key||!receipt.confirmed()||!receipt.normalIdleHistoryCurrent())return false;
            if(!receipt.held())continue;
            if(receipt.normallyReturnedOwnedHeldAttempt())next.put(key,new FarmHeld(actual.farm,actual.scope,receipt));
            else{var old=before.get(key);
                // A zero-effect true can retain only already-owned, still-current private native custody.
                if(old!=null&&old.owner()==actual.farm&&old.receipt().normallyReturnedOwnedHeldAttempt()&&old.receipt().normalIdleHistoryCurrent())
                    next.put(key,new FarmHeld(actual.farm,actual.scope,old.receipt()));
            }
        }
        if(!actual.current()||!service.canonicalRootCurrent())return false;
        if(service.farmHeldOptions!=beforeOptions)return false;
        for(var entry:next.entrySet())if(service.farmHeld.get(entry.getKey())!=before.get(entry.getKey()))return false;
        // No callbacks after these exact private-record identity checks.
        service.farmHeldOptions=service.client.options;
        for(var entry:next.entrySet())service.farmHeld.put(entry.getKey(),entry.getValue());
        return true;
    }
    static final class PassiveHeldContinuation {
        private final McInputService service;
        private final RootNativeHistory.PassiveTransitionObservation source;
        private final Object options;
        private final java.util.IdentityHashMap<KeyMapping,FarmHeld> before;
        private java.util.IdentityHashMap<KeyMapping,FarmHeld> next;
        private PassiveHeldContinuation(McInputService service,RootNativeHistory.PassiveTransitionObservation source){
            this.service=service;this.source=source;options=service.farmHeldOptions;before=new java.util.IdentityHashMap<>(service.farmHeld);
        }
    }
    static PassiveHeldContinuation capturePassiveHeldContinuation(RootNativeHistory.PassiveTransitionObservation source){
        var service=canonicalFarmInput(source.client());if(service==null)return null;
        var capture=new PassiveHeldContinuation(service,source);
        if(capture.options!=service.client.options&&!capture.before.isEmpty())return null;
        for(var entry:capture.before.entrySet()){
            var old=entry.getValue();if(old==null||old.owner()!=old.scope().farm||old.receipt().key()!=entry.getKey()
                    ||!old.receipt().normallyReturnedOwnedHeldAttempt()||!source.beforeScopeCurrent(old.scope()))return null;
        }
        return passiveHeldCommitCurrent(capture)?capture:null;
    }
    static PassiveHeldContinuation preparePassiveHeldContinuation(PassiveHeldContinuation capture,RootNativeHistory.PassiveTransitionObservation source){
        if(capture==null||capture.source!=source||!source.normalCurrent())return null;
        var next=new java.util.IdentityHashMap<KeyMapping,FarmHeld>();
        for(var entry:capture.before.entrySet()){
            var old=entry.getValue();if(!source.normalScopeCurrent(old.scope())||!old.receipt().normallyReturnedOwnedHeldAttempt())return null;
            var scope=old.scope().continueAfterPassive(source.actual());if(scope==null)return null;
            next.put(entry.getKey(),new FarmHeld(old.owner(),scope,old.receipt()));
        }
        if(!source.normalCurrent()||!passiveHeldCommitCurrent(capture))return null;
        capture.next=next;return capture;
    }
    /** Raw private identity checks only; all native/source/provider reads precede this final CAS. */
    static boolean passiveHeldCommitCurrent(PassiveHeldContinuation capture){
        var service=capture.service;if(service.farmHeldOptions!=capture.options||service.farmHeld.size()!=capture.before.size())return false;
        for(var entry:capture.before.entrySet())if(service.farmHeld.get(entry.getKey())!=entry.getValue())return false;
        return true;
    }
    static boolean commitPassiveHeldContinuation(PassiveHeldContinuation capture){
        if(capture.next==null||!capture.source.commitWindowCurrent()||!passiveHeldCommitCurrent(capture))return false;
        for(var entry:capture.next.entrySet())capture.service.farmHeld.put(entry.getKey(),entry.getValue());
        return true;
    }
    private Object farmHeldOptions;
    private boolean farmKey(KeyMapping key, boolean held) {return farmKey(key,held,null,null);}
    private boolean farmKey(KeyMapping key,boolean held,dev.veltraph.client.VeltraphBootstrap.RootWartDropPublication wartDrop,McWartDropWaterActor wartDropActor) {
        if(wartDrop!=null&&(wartDropActor==null||wartDrop.farmServicesIdentity().input()!=this||!wartDropActor.inputPublicationCurrent(wartDrop)||!wartDrop.current()))return true;
        if(farmHeldOptions!=client.options){farmHeld.clear();farmHeldOptions=client.options;}
        var refused=McFarmOwnedCleanup.toolRefusalFarm();
        if(refused!=null&&!McFarmOwnedCleanup.stopping()) {
            var exact=farmHeld.get(key);
            if(held||exact==null||exact.owner()!=refused||!canonicalRootCurrent()
                    ||!exact.scope().activeReleaseProvidersCurrent())return true;
            var released=McHeldKeys.releaseAttemptedOwnedScoped(exact.receipt(),
                    ()->McFarmOwnedCleanup.toolRefusalFarm()==refused&&farmHeld.get(key)==exact
                            &&canonicalRootCurrent()&&exact.scope().activeReleaseCurrent()&&(wartDrop==null||wartDropActor.inputPublicationCurrent(wartDrop)));
            observeOriginalKeyReturn(exact.scope(),released);
            if(released.confirmed())farmHeld.remove(key);
            return true;
        }
        if(McFarmOwnedCleanup.stopping()) {
            var exact=farmHeld.get(key);
            if(held||exact==null||exact.owner()!=McFarmOwnedCleanup.stoppingFarm())return true;
            if(!canonicalRootCurrent()||!exact.scope().cleanupProvidersCurrent())return true;
            var released=McHeldKeys.releaseAttemptedOwnedScoped(exact.receipt(),
                    ()->farmHeld.get(key)==exact&&canonicalRootCurrent()&&exact.scope().stopCurrent()&&(wartDrop==null||wartDropActor.inputPublicationCurrent(wartDrop)));
            observeOriginalKeyReturn(exact.scope(),released);
            if(released.confirmed())farmHeld.remove(key);
            return true;
        }
        var prior=farmHeld.get(key);
        if(!held&&prior!=null&&canonicalRootCurrent()&&prior.scope().activeReleaseProvidersCurrent()) {
            var released=McHeldKeys.releaseAttemptedOwnedScoped(prior.receipt(),
                    ()->farmHeld.get(key)==prior&&canonicalRootCurrent()&&prior.scope().activeReleaseCurrent()&&(wartDrop==null||wartDropActor.inputPublicationCurrent(wartDrop)));
            observeOriginalKeyReturn(prior.scope(),released);
            if(released.confirmed())farmHeld.remove(key);
            return true;
        }
        var root=dev.veltraph.client.VeltraphBootstrap.moduleManager();
        var owner=root==null?null:root.inputOwner().orElse(null);
        if(!(owner instanceof dev.veltraph.client.farming.module.FarmModule farm)||!canonicalRootCurrent()) {
            if(McFarmOwnedCleanup.farmingTickFarm()!=null)return true;
            farmHeld.remove(key);return false;
        }
        if(McFarmOwnedCleanup.farmingTickFarm()!=null&&McFarmOwnedCleanup.farmingTickFarm()!=farm)return true;
        var scope=McFarmOwnedCleanup.NativeScope.capture(client,farm);
        if(scope==null||wartDrop!=null&&scope.farm!=wartDrop.farmIdentity())return true;
        if(!held) {
            if(prior!=null&&prior.owner()==farm&&prior.scope().activeCurrent()) {
                var released=McHeldKeys.releaseAttemptedOwnedScoped(prior.receipt(),
                        ()->farmHeld.get(key)==prior&&canonicalRootCurrent()&&scope.activeCurrent()&&(wartDrop==null||wartDropActor.inputPublicationCurrent(wartDrop)));
                observeOriginalKeyReturn(scope,released);
                if(released.confirmed())farmHeld.remove(key);
            }
            return true;
        }
        var published=McHeldKeys.setHeldFromObservedScoped(key,true,
                ()->canonicalRootCurrent()&&scope.activeCurrent()&&(wartDrop==null||wartDropActor.inputPublicationCurrent(wartDrop)));
        observeOriginalKeyReturn(scope,published);
        if(published.normallyReturnedOwnedHeldAttempt())farmHeld.put(key,new FarmHeld(farm,scope,published));
        // A no-op true never adopts a foreign/raw same-value publication. Retain only the old exact history.
        else if(published.confirmed()&&prior!=null&&prior.owner()==farm
                &&prior.receipt().normalIdleHistoryCurrent()&&prior.scope().sameNativeProducer(scope)
                &&scope.activeCurrent())farmHeld.put(key,new FarmHeld(farm,scope,prior.receipt()));
        else farmHeld.remove(key);
        return true;
    }
    private WartDropKeyCall wartDropKeyCall;
    private final class WartDropKeyCall {
        final dev.veltraph.client.VeltraphBootstrap.RootWartDropPublication source;
        final McWartDropWaterActor actor;final RootNativeInputEpisode episode;final KeyMapping key;final boolean held;boolean revoked;
        WartDropKeyCall(dev.veltraph.client.VeltraphBootstrap.RootWartDropPublication source,McWartDropWaterActor actor,RootNativeInputEpisode episode,KeyMapping key,boolean held){
            this.source=source;this.actor=actor;this.episode=episode;this.key=key;this.held=held;}
        boolean beforeNative(KeyMapping actual,boolean desired,boolean restore,java.util.List<McHeldKeys.NativeInputInvocation> returned){
            return !revoked&&wartDropKeyCall==this&&RootNativeInputEpisode.peek()==episode&&actual==key&&(restore?!desired:desired==held||!held&&desired&&actual.getClass()==net.minecraft.client.ToggleKeyMapping.class&&!returned.isEmpty())
                &&actor.inputPublicationCurrent(source)&&source.beforeKeyEffectCurrent(key,returned)&&actor.inputPublicationCurrent(source);
        }
    }
    /** Last preeffect native leaf; post-own mutation uses only the genuine tokens supplied by McHeldKeys. */
    static boolean beforeWartDropNativeInput(KeyMapping key,boolean held,boolean restore,java.util.List<McHeldKeys.NativeInputInvocation> returned){
        var registration=canonicalRootRelease;var input=registration==null?null:registration.actualInput();
        var call=input==null?null:input.wartDropKeyCall;
        if(call==null)return true;
        // Canonical farmKey stopCurrent/old FarmHeld receipt still independently owns every release.
        // Only Root's genuine own-disable source may close the outer typed guard for that cleanup.
        if(!held&&McFarmOwnedCleanup.stoppingFarm()==call.source.farmIdentity()&&call.source.ownDisableCleanupCurrent()){
            call.revoked=true;return true;
        }
        return call.beforeNative(key,held,restore,returned);
    }
    /** Privately captured at nativeInput admission; public data cannot construct a selected SDK field guard. */
    static final class WartNativeFieldGuard {
        private final McInputService service;private final WartDropKeyCall call;private final RootNativeInputEpisode episode;
        private final KeyMapping key;private final boolean cleanup;private boolean revoked;
        private WartNativeFieldGuard(McInputService service,WartDropKeyCall call,KeyMapping key,boolean cleanup){
            this.service=service;this.call=call;this.key=key;this.cleanup=cleanup;episode=RootNativeInputEpisode.peek();}
        void revoke(){revoked=true;call.revoked=true;}
        boolean fieldCurrent(KeyMapping receiver,boolean down,java.util.List<McHeldKeys.NativeInputInvocation> returned,McHeldKeys.NativeInputInvocation pending,int depth){
            if(revoked||service.wartDropKeyCall!=call||RootNativeInputEpisode.peek()!=episode||receiver!=key)return false;
            if(cleanup)return !down&&call.source.ownDisableCleanupCurrent()&&McFarmOwnedCleanup.stoppingFarm()==call.source.farmIdentity()
                &&call.source.keyFieldHistoryCurrent(key,returned,pending,depth)&&call.source.ownDisableCleanupCurrent();
            return !call.revoked&&down==call.held&&call.actor.inputPublicationCurrent(call.source)
                &&call.source.keyFieldHistoryCurrent(key,returned,pending,depth)&&call.actor.inputPublicationCurrent(call.source);
        }
    }
    static WartNativeFieldGuard captureWartNativeFieldGuard(KeyMapping key){
        var registration=canonicalRootRelease;var service=registration==null?null:registration.actualInput();
        var call=service==null?null:service.wartDropKeyCall;if(call==null)return null;
        boolean cleanup=McFarmOwnedCleanup.stoppingFarm()==call.source.farmIdentity()&&call.source.ownDisableCleanupCurrent();
        if(cleanup)call.revoked=true;
        return new WartNativeFieldGuard(service,call,key,cleanup);
    }
    /** Canonical method-entry and native FarmHeld receipt, with the exact private Wart source as the final veto. */
    public boolean publishWartDropMovement(dev.veltraph.client.VeltraphBootstrap.RootWartDropPublication source,McWartDropWaterActor actor,KeyMapping key,boolean held){
        if(wartDropKeyCall!=null||source==null||actor==null||!actor.inputPublicationCurrent(source)||source.farmServicesIdentity().input()!=this||client.options==null||!source.current()
                ||key!=client.options.keyUp&&key!=client.options.keyDown&&key!=client.options.keyLeft&&key!=client.options.keyRight
                    &&key!=client.options.keyJump&&key!=client.options.keyShift&&key!=client.options.keySprint)return false;
        var episode=RootNativeInputEpisode.enter(this,client,key,held);if(episode==null)return false;
        var call=new WartDropKeyCall(source,actor,episode,key,held);wartDropKeyCall=call;
        try{farmKey(key,held,source,actor);var actual=farmHeld.get(key);
            return source.current()&&key.isDown()==held&&(!held||actual!=null&&actual.owner()==source.farmIdentity()
                    &&actual.receipt().normalIdleHistoryCurrent())&&actor.inputPublicationCurrent(source);
        }finally{if(wartDropKeyCall==call)wartDropKeyCall=null;episode.close();}
    }
    private void press(KeyMapping key,boolean held){
        RootNativeInputEpisode observation=null;
        try{observation=RootNativeInputEpisode.enter(this,client,key,held);}catch(RuntimeException|Error unavailable){}
        try{pressOriginal(key,held);}finally{if(observation!=null)try{observation.close();}catch(RuntimeException|Error unavailable){}}
    }
    private void pressOriginal(KeyMapping key, boolean held) {
        if (key != null) {
            if(McIssuedFarmReturnCleanup.releaseCanonical(this,key,held))return;
            if(McIssuedFarmResumeGuard.release(this,key,held))return;
            if(farmKey(key,held))return;
            McHeldKeys.setHeld(key,held);
        }
    }

    @Override
    public void setForwardHeld(boolean held) {
        if (client.options != null) {
            press(client.options.keyUp, held);
        }
    }

    @Override
    public void setBackHeld(boolean held) {
        if (client.options != null) {
            press(client.options.keyDown, held);
        }
    }

    @Override
    public void setLeftHeld(boolean held) {
        if (client.options != null) {
            press(client.options.keyLeft, held);
        }
    }

    @Override
    public void setRightHeld(boolean held) {
        if (client.options != null) {
            press(client.options.keyRight, held);
        }
    }

    @Override
    public void setSneakHeld(boolean held) {
        var actual=dev.veltraph.client.VeltraphBootstrap.originalSavedMaintenancePauseForNative();
        if(actual!=null){
            if(!canonicalRootCurrent()||!actual.nativePostureIssuingCurrent(this)||client.options==null
                    ||client.options.keyShift!=actual.originalPostureKey()||held!=actual.desiredPostureHeld())
                throw new IllegalStateException("Original Saved posture source changed");
            McHeldKeys.setSavedMaintenancePosture(this,actual,held);return;
        }
        if (client.options != null) {
            press(client.options.keyShift, held);
        }
    }

    @Override
    public void setJumpHeld(boolean held) {
        if (client.options != null) {
            press(client.options.keyJump, held);
        }
    }

    @Override public void setSprintHeld(boolean held) {
        if(client.options!=null)press(client.options.keySprint,held);
    }

    @Override
    public void releaseAllMovement() {
        if (client.options == null) {
            return;
        }
        press(client.options.keyUp, false);
        press(client.options.keyDown, false);
        press(client.options.keyLeft, false);
        press(client.options.keyRight, false);
        press(client.options.keyShift, false);
        press(client.options.keyJump, false);
        press(client.options.keySprint, false);
    }
}
