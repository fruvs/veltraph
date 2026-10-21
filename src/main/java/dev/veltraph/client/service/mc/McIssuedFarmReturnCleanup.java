package dev.veltraph.client.service.mc;

import dev.veltraph.client.rewarp.mc.McSavedAnchorRecovery;
import dev.veltraph.client.navigation.mc.LegacyTravelPublicationLease;
import dev.veltraph.client.navigation.mc.McTravelNavigator;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

/** A genuine original Journey retirement scope, distinct from ordinary travel dispatch. */
public final class McIssuedFarmReturnCleanup {
    private McIssuedFarmReturnCleanup() {}
    private static final ThreadLocal<Context> ACTIVE=new ThreadLocal<>();
    private static final class Context {
        final McSavedAnchorRecovery.IssuedReturnCleanup source;
        final Minecraft client;
        final McInputService input;
        final McRotationService rotation;
        final McHeldKeys.IssuedReturnKeyHistory keys;
        final McOwnedRotation.IssuedReturnCameraWindow camera;
        final McRotationService.IssuedReturnMetadata service;
        final McTravelNavigator.IssuedReturnStopSnapshot travel;
        final Thread thread=Thread.currentThread();
        Object keyCall;LegCameraCall cameraCall;LegRetirementCall legRetirement;boolean revoked,metadataOnly,checking;int effect;
        Context(McSavedAnchorRecovery.IssuedReturnCleanup source){
            this.source=source;client=source.client();var services=source.sourceHeader().farmServicesIdentity();
            input=services.input() instanceof McInputService nativeInput?nativeInput:null;
            rotation=services.rotation() instanceof McRotationService nativeRotation?nativeRotation:null;
            // Native originals precede every issuer/role/source callback.
            keys=McHeldKeys.captureIssuedReturnKeyHistory(client);
            camera=McOwnedRotation.captureIssuedReturnCameraWindow(source);
            service=rotation==null?null:rotation.captureIssuedReturnMetadata(source);
            travel=source.travelIdentity().captureIssuedReturnStop(source);
        }
        boolean metadataCurrent(){return !revoked&&ACTIVE.get()==this&&Thread.currentThread()==thread&&source.metadataCurrent()&&service!=null&&travel!=null&&service.current()&&travel.current();}
        boolean nativeCurrent(){return !metadataOnly&&!revoked&&ACTIVE.get()==this&&Thread.currentThread()==thread&&source.nativeCurrent()
            &&keys!=null&&input!=null&&rotation!=null&&camera!=null&&service!=null&&travel!=null
            &&camera.current()&&service.current()&&travel.current()&&originalCameraOwned()
            &&McInputService.canonicalFarmRootCurrent(client)&&McInputService.canonicalFarmRotationCurrent(rotation,client);}
        private boolean originalCameraOwned(){
            if(!camera.hadOriginalPositive())return true;
            var aim=source.originalOwnAim();
            return travel.ownsOriginalCamera(camera)||aim!=null&&aim.actualTicket()==camera.originalPositiveTicket()
                &&aim.after().epoch()==camera.originalEpoch()&&aim.after().parent()==source.driverIdentity();
        }
        boolean current(){
            if(checking){revoked=true;return false;}checking=true;
            try{return metadataOnly?metadataCurrent():source.current()&&nativeCurrent()&&keys.current();}
            finally{checking=false;}
        }
    }
    public static final class Prepared {
        private final Context original;private boolean consumed;
        private Prepared(Context original){this.original=original;}
    }
    public static Prepared prepare(McSavedAnchorRecovery.IssuedReturnCleanup source){
        return source==null?null:new Prepared(new Context(source));
    }
    public static boolean runPrepared(Prepared prepared,Runnable actualOriginalRelease){
        java.util.Objects.requireNonNull(actualOriginalRelease);var prior=ACTIVE.get();
        if(prior!=null){prior.revoked=true;return false;}
        if(prepared==null||prepared.consumed)return false;prepared.consumed=true;var context=prepared.original;
        context.metadataOnly=context.source.originalOperation()==null&&!context.source.nativeCurrent();
        ACTIVE.set(context);
        if(context.source.originalOperation()==null&&(!context.nativeCurrent()||context.keys==null||!context.keys.current()))context.metadataOnly=true;
        try {if(!context.current())return false;actualOriginalRelease.run();return context.current();}
        finally {ACTIVE.remove();}
    }
    public static boolean run(McSavedAnchorRecovery.IssuedReturnCleanup source,Runnable actualOriginalRelease){return runPrepared(prepare(source),actualOriginalRelease);}
    public static boolean runMetadata(McSavedAnchorRecovery.IssuedReturnCleanup source,Runnable actualOriginalRelease){
        var prepared=prepare(source);if(prepared==null)return false;prepared.original.metadataOnly=true;
        // This route has no original native operation or close cause; it always remains logical-only.
        return runPrepared(prepared,actualOriginalRelease);
    }
    public static boolean metadataCurrent(){var c=ACTIVE.get();return c!=null&&c.metadataCurrent();}
    public static boolean logicalOnly(){var c=ACTIVE.get();return c!=null&&c.metadataOnly;}
    public static boolean ownsOriginalJourney(Object exact){var c=ACTIVE.get();return c!=null&&c.source.journeyIdentity()==exact;}
    public static boolean active(){return ACTIVE.get()!=null;}
    public static boolean nativeCurrent(){var c=ACTIVE.get();return c!=null&&c.nativeCurrent();}
    static McHeldKeys.IssuedReturnKeyHistory currentKeyHistory(){var c=ACTIVE.get();return c==null?null:c.keys;}
    public static boolean ownsOriginalLeg(LegacyTravelPublicationLease.Leg leg){var c=ACTIVE.get();return c!=null&&c.travel.ownsOriginalLeg(leg);}
    public static boolean ownsOriginalRequest(McSavedAnchorRecovery.IssuedReturnTravelRequest request,LegacyTravelPublicationLease.Leg leg){
        var c=ACTIVE.get();return c!=null&&c.source.requestIdentity()==request&&c.travel.ownsOriginalLeg(leg);
    }
    /** Claimed actual parent stop before its original Leg/actuator callback chain. */
    public static final class LegRetirementCall implements AutoCloseable {
        private final Context context;private final LegacyTravelPublicationLease.Leg leg;private boolean closed,entryClaimed;private final java.util.IdentityHashMap<Object,Boolean> resetActors=new java.util.IdentityHashMap<>();
        private LegRetirementCall(Context context,LegacyTravelPublicationLease.Leg leg){this.context=context;this.leg=leg;}
        public boolean current(){return !closed&&ACTIVE.get()==context&&context.legRetirement==this&&context.effect==3&&context.travel.ownsOriginalLeg(leg)&&context.metadataCurrent();}
        public void close(){closed=true;if(context.legRetirement==this)context.legRetirement=null;}
    }
    public static boolean resetOriginalActuator(Object actual,Runnable logicalReset){
        var c=ACTIVE.get();if(c==null)return false;var original=c.legRetirement;
        boolean caller=StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).walk(stream->{
            var first=stream.limit(48).filter(f->f.getDeclaringClass()!=McIssuedFarmReturnCleanup.class).findFirst().orElse(null);
            return first!=null&&(first.getDeclaringClass().getEnclosingClass()==McTravelNavigator.class&&first.getMethodName().equals("stopOwned")
                ||first.getDeclaringClass()==dev.veltraph.client.navigation.mc.McSharedMovementNavigator.class&&first.getMethodName().equals("stopOwnedIssuedReturn"));
        });
        if(actual==null||logicalReset==null||original==null||!original.entryClaimed||!caller
            ||!original.current()||!c.travel.ownsOriginalActuator(actual)||original.resetActors.put(actual,Boolean.TRUE)!=null){c.revoked=true;return false;}
        // Claim the exact original actor before any source/provider callback. This window cannot be replayed.
        if(!c.current())return false;
        boolean before=c.metadataOnly;c.metadataOnly=true;
        try{logicalReset.run();return c.metadataCurrent();}
        finally{c.metadataOnly=before;}
    }
    public static boolean originalLegRetirementCurrent(LegacyTravelPublicationLease.Leg exact){var c=ACTIVE.get();return c!=null&&c.legRetirement!=null&&c.legRetirement.leg==exact&&c.legRetirement.entryClaimed&&c.legRetirement.current();}
    public static boolean claimOriginalLegRetirement(LegacyTravelPublicationLease.Leg exact){
        var c=ACTIVE.get();if(c==null)return false;var call=c.legRetirement;
        boolean original=StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).walk(stream->{
            var frames=stream.limit(48).toList();var producer=frames.stream().filter(f->f.getDeclaringClass()!=McIssuedFarmReturnCleanup.class
                &&f.getDeclaringClass()!=LegacyTravelPublicationLease.Leg.class).findFirst().orElse(null);
            return producer!=null&&(producer.getDeclaringClass().getEnclosingClass()==McTravelNavigator.class&&producer.getMethodName().equals("stopOwned")
                ||producer.getDeclaringClass()==dev.veltraph.client.navigation.mc.McSharedMovementNavigator.class&&producer.getMethodName().equals("stopOwnedIssuedReturn"));
        });
        if(call==null||call.leg!=exact||call.entryClaimed||!call.current()||!original){c.revoked=true;return false;}
        call.entryClaimed=true;return true;
    }
    public static LegRetirementCall beginOriginalLegRetirement(LegacyTravelPublicationLease.Leg leg,Object sourceWrapper){
        var c=ACTIVE.get();if(c==null)return null;
        boolean original=StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).walk(stream->{
            var frames=stream.limit(48).toList();var first=frames.stream().filter(f->f.getDeclaringClass()!=McIssuedFarmReturnCleanup.class).findFirst().orElse(null);
            if(first==null)return false;
            if(first.getDeclaringClass().getEnclosingClass()==McTravelNavigator.class&&first.getMethodName().equals("stopOwned"))return true;
            if(first.getDeclaringClass()!=dev.veltraph.client.navigation.mc.McSharedMovementNavigator.class||!first.getMethodName().equals("stopOwnedIssuedReturn"))return false;
            var producer=frames.stream().filter(f->f.getDeclaringClass()!=McIssuedFarmReturnCleanup.class
                &&f.getDeclaringClass()!=dev.veltraph.client.navigation.mc.McSharedMovementNavigator.class).findFirst().orElse(null);
            return producer!=null&&producer.getDeclaringClass()==dev.veltraph.client.navigation.SharedMovementCoordinator.class&&producer.getMethodName().equals("stopScoped");
        });
        if(c.effect!=3||c.legRetirement!=null||!original||!c.travel.ownsOriginalLeg(leg)
                ||!c.travel.ownsOriginalWrapper(sourceWrapper)||!c.current()){c.revoked=true;return null;}
        var actual=new LegRetirementCall(c,leg);c.legRetirement=actual;return actual;
    }
    private static boolean legSourceEntered(Context c){var call=c.legRetirement;return call!=null&&call.entryClaimed;}
    public static final class LegKeyCall implements AutoCloseable {
        private final Context context;private final LegacyTravelPublicationLease.Leg leg;private final KeyMapping key;private boolean closed,claimed;
        private LegKeyCall(Context context,LegacyTravelPublicationLease.Leg leg,KeyMapping key){this.context=context;this.leg=leg;this.key=key;}
        public boolean current(){return !closed&&ACTIVE.get()==context&&context.keyCall==this&&context.travel.ownsOriginalLeg(leg)&&context.nativeCurrent();}
        public void close(){closed=true;if(context.keyCall==this)context.keyCall=null;}
    }
    public static LegKeyCall beginOriginalLegKeyCall(LegacyTravelPublicationLease.Leg leg,KeyMapping key){
        var c=ACTIVE.get();if(c==null||c.metadataOnly)return null;
        if(c.effect!=3||c.legRetirement==null||!c.legRetirement.current()||!legSourceEntered(c)||c.keyCall!=null||!c.travel.ownsOriginalLeg(leg)||key==null||!originalLegCaller()||!c.current()){c.revoked=true;return null;}
        var call=new LegKeyCall(c,leg,key);c.keyCall=call;return call;
    }
    private static boolean originalLegEffectCaller(String expected){return StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).walk(stream->{
        var frames=stream.limit(48).toList();var first=frames.stream().filter(f->f.getDeclaringClass()!=McIssuedFarmReturnCleanup.class).findFirst().orElse(null);
        if(first==null||first.getDeclaringClass()!=LegacyTravelPublicationLease.Leg.class||!first.getMethodName().equals(expected))return false;
        // A public Leg method supplies its own frames. Only the nearest actual retirement kernel may be its source.
        var producer=frames.stream().filter(f->f.getDeclaringClass()!=McIssuedFarmReturnCleanup.class
            &&f.getDeclaringClass()!=LegacyTravelPublicationLease.Leg.class&&f.getDeclaringClass().getEnclosingClass()!=LegacyTravelPublicationLease.Leg.class).findFirst().orElse(null);
        return producer!=null&&producer.getDeclaringClass()==dev.veltraph.client.navigation.LegacyTravelRetirement.class&&producer.getMethodName().equals("retire");
    });}
    private static boolean originalLegCaller(){return originalLegEffectCaller("releaseKeyScoped");}
    private static final class CanonicalKeyCall {
        final Context context;final KeyMapping key;boolean claimed;CanonicalKeyCall(Context context,KeyMapping key){this.context=context;this.key=key;}
    }
    /** Actual public scoped-entry claim precedes its first observer and snapshot. Internal reads stay idempotent. */
    public static boolean claimKeyCall(KeyMapping key,boolean outerHeld){
        var c=ACTIVE.get();if(c==null||c.metadataOnly)return false;
        var call=c.keyCall;
        boolean originalCall=call instanceof LegKeyCall leg?leg.key==key&&leg.current()
            :call instanceof CanonicalKeyCall canonical&&canonical.context==c&&canonical.key==key&&c.nativeCurrent();
        if(outerHeld||key==null||!originalCall){c.revoked=true;return false;}
        boolean original=StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).walk(stream->{
            var frames=stream.limit(48).toList();var first=frames.stream().filter(f->f.getDeclaringClass()!=McIssuedFarmReturnCleanup.class&&f.getDeclaringClass()!=McHeldKeys.class).findFirst().orElse(null);
            return call instanceof LegKeyCall?first!=null&&first.getDeclaringClass()==LegacyTravelPublicationLease.Leg.class&&first.getMethodName().equals("releaseKeyScoped")
                :frames.stream().anyMatch(f->f.getDeclaringClass()==McIssuedFarmReturnCleanup.class&&f.getMethodName().equals("releaseCanonical"))
                    &&first!=null&&first.getDeclaringClass()==McInputService.class;
        });
        if(!original){c.revoked=true;return false;}
        if(call instanceof LegKeyCall actual){if(actual.claimed){c.revoked=true;return false;}actual.claimed=true;return true;}
        if(call instanceof CanonicalKeyCall actual){if(actual.claimed){c.revoked=true;return false;}actual.claimed=true;return true;}
        c.revoked=true;return false;
    }
    public static boolean permitKeyCall(KeyMapping key,boolean outerHeld){
        var c=ACTIVE.get();if(c==null||c.metadataOnly||outerHeld||key==null)return false;var call=c.keyCall;
        if(call instanceof LegKeyCall actual)return actual.claimed&&actual.key==key&&actual.current();
        return call instanceof CanonicalKeyCall actual&&actual.claimed&&actual.context==c&&actual.key==key&&c.nativeCurrent();
    }
    static boolean releaseCanonical(McInputService input,KeyMapping key,boolean held){
        var c=ACTIVE.get();if(c==null)return false;if(c.metadataOnly)return true;
        if(held||input!=c.input||key==null||key.isDown()||c.keyCall!=null||!originalCanonicalCaller()){c.revoked=true;return true;}
        var actual=new CanonicalKeyCall(c,key);c.keyCall=actual;
        try {if(!c.current()){c.revoked=true;return true;}var receipt=McHeldKeys.setHeldScoped(key,false,c::current);if(!receipt.confirmed()||!c.current())c.revoked=true;}
        finally{if(c.keyCall==actual)c.keyCall=null;}
        return true;
    }
    private static boolean originalCanonicalCaller(){return StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).walk(stream->{
        var first=stream.limit(48).filter(f->f.getDeclaringClass()!=McIssuedFarmReturnCleanup.class&&f.getDeclaringClass()!=McInputService.class).findFirst().orElse(null);
        return first!=null&&first.getDeclaringClass().getEnclosingClass()==McSavedAnchorRecovery.class&&first.getMethodName().equals("performOriginalIssuedRelease");
    });}
    public static final class LegCameraCall implements AutoCloseable {
        private final Context context;private final LegacyTravelPublicationLease.Leg leg;private boolean closed,claimed;
        private LegCameraCall(Context context,LegacyTravelPublicationLease.Leg leg){this.context=context;this.leg=leg;}
        public boolean current(){return !closed&&ACTIVE.get()==context&&context.cameraCall==this&&context.travel.ownsOriginalLeg(leg)&&context.nativeCurrent();}
        public void close(){closed=true;if(context.cameraCall==this)context.cameraCall=null;}
    }
    public static LegCameraCall beginOriginalLegCameraCall(LegacyTravelPublicationLease.Leg leg){
        var c=ACTIVE.get();if(c==null||c.metadataOnly)return null;
        if(c.effect!=3||c.legRetirement==null||!c.legRetirement.current()||!legSourceEntered(c)||c.cameraCall!=null
            ||!originalLegEffectCaller("cancelCameraOwned")||!c.travel.ownsOriginalLeg(leg)||!c.current()){c.revoked=true;return null;}
        var actual=new LegCameraCall(c,leg);c.cameraCall=actual;return actual;
    }
    private static boolean originalLegCameraNativeCaller(){return StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).walk(stream->{
        var first=stream.limit(48).filter(f->f.getDeclaringClass()!=McIssuedFarmReturnCleanup.class&&f.getDeclaringClass()!=McRotationService.class&&f.getDeclaringClass()!=McOwnedRotation.class).findFirst().orElse(null);
        return first!=null&&first.getDeclaringClass()==LegacyTravelPublicationLease.Leg.class&&first.getMethodName().equals("cancelCameraOwned");
    });}
    private static boolean claimEffect(Context c,int effect){
        if(c.effect!=0){
            var call=c.cameraCall;
            if(c.effect!=3||effect!=2||call==null||call.claimed||!call.current()||!originalLegCameraNativeCaller()){c.revoked=true;return false;}
            call.claimed=true;return true;
        }
        var original=StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).walk(stream->{
            var first=stream.limit(48).filter(f->f.getDeclaringClass()!=McIssuedFarmReturnCleanup.class&&f.getDeclaringClass()!=McRotationService.class
                &&f.getDeclaringClass()!=McOwnedRotation.class&&f.getDeclaringClass()!=McTravelNavigator.class).findFirst().orElse(null);
            return first!=null&&(first.getDeclaringClass().getEnclosingClass()==McSavedAnchorRecovery.class&&first.getMethodName().equals("performOriginalIssuedRelease")
                ||first.getDeclaringClass()==McSavedAnchorRecovery.class
                    &&java.util.Set.of("performOriginalIssuedClose","performOriginalIssuedMetadataClose").contains(first.getMethodName()));
        });
        if(!original){c.revoked=true;return false;}c.effect=effect;return true;
    }
    static boolean cancelCanonical(McRotationService exact){var c=ACTIVE.get();if(c==null)return false;int previous=c.effect;
        if(!claimEffect(c,2))return true;
        try{if(c.metadataOnly){if(!c.service.retireLogical(c::metadataCurrent))c.revoked=true;return true;}
            if(exact!=c.rotation||!c.current()||!c.service.retireOwned(c.camera,c::current,c::nativeCurrent))c.revoked=true;return true;
        }finally{c.effect=previous;}}
    public static boolean ownsCameraWindow(McOwnedRotation.IssuedReturnCameraWindow window){var c=ACTIVE.get();return c!=null&&c.camera==window;}
    static boolean cancelNative(Minecraft exact){var c=ACTIVE.get();if(c==null)return false;if(c.metadataOnly)return true;int previous=c.effect;
        if(!claimEffect(c,2))return true;
        try{if(c.metadataOnly)return true;
            if(c.client!=exact||!c.current()||!c.camera.retireOwned(c.source,c::current,c::nativeCurrent))c.revoked=true;return true;
        }finally{c.effect=previous;}}
    public static boolean stopTravel(McTravelNavigator exact){var c=ACTIVE.get();if(c==null)return false;
        if(!claimEffect(c,3))return true;
        try{if(exact!=c.source.travelIdentity()||!c.current()||!c.travel.stopOwned(c::current,c::nativeCurrent))c.revoked=true;return true;}
        finally{c.effect=0;}}
}
