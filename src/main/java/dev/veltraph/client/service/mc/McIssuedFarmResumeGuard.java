package dev.veltraph.client.service.mc;

import dev.veltraph.client.farming.module.FarmModule;
import dev.veltraph.client.rewarp.mc.McSuspendedFarmReturnCoordinator;
import dev.veltraph.client.service.BlockBreakingService;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

/** Exact typed issued-return hook custody. No public callback or raw current epoch grants it. */
public final class McIssuedFarmResumeGuard {
    private McIssuedFarmResumeGuard() {}
    private static final ThreadLocal<Invocation> CURRENT = new ThreadLocal<>();
    private static final class Invocation {
        final FarmModule farm;
        final FarmModule.IssuedReturnCheckpoint checkpoint;
        final McSuspendedFarmReturnCoordinator.ResumeCall source;
        final Minecraft client;
        final McInputService input;
        final McRotationService rotation;
        final McBlockBreakingService breaking;
        final Thread thread = Thread.currentThread();
        final Object player,world,connection,options,gameMode;
        final McOwnedRotation.InstallationSnapshot installation;
        final KeyMapping[] keys;
        final Object[] history;
        final McHeldKeys.ScopedHeldReceipt[] releases = new McHeldKeys.ScopedHeldReceipt[9];
        McRotationService.ResumeRetirement rotationState;
        McBlockBreakingService.ResumeBreakingRetirement breakingState;
        final dev.veltraph.client.farming.movement.FarmRouteDriver route;
        final dev.veltraph.client.farming.module.FarmMaintenanceState originalRoute;
        dev.veltraph.client.navigation.mc.McNavigator.IssuedResumeStopReceipt routeRetirement;
        BlockBreakingService.TargetGuardBinding crop;
        BlockBreakingService.TargetGuardBinding installedCrop;
        int pendingKey = -1;
        int operation; // Refusal-only reentry gate, never native mutation authority.
        boolean revoked;
        Invocation(FarmModule farm,FarmModule.IssuedReturnCheckpoint checkpoint,
                McSuspendedFarmReturnCoordinator.ResumeCall source) {
            this.farm=farm;this.checkpoint=checkpoint;this.source=source;client=Minecraft.getInstance();
            var services=source.sourceHeader().farmServicesIdentity();
            input=services.input() instanceof McInputService nativeInput?nativeInput:null;
            rotation=services.rotation() instanceof McRotationService nativeRotation?nativeRotation:null;
            breaking=services.breaking() instanceof McBlockBreakingService nativeBreaking?nativeBreaking:null;
            player=client.player;world=client.level;connection=client.getConnection();options=client.options;gameMode=client.gameMode;
            installation=McOwnedRotation.installationSnapshot(client);
            var o=client.options;
            keys=o==null?new KeyMapping[0]:new KeyMapping[]{o.keyUp,o.keyDown,o.keyLeft,o.keyRight,o.keyJump,o.keyShift,o.keySprint,o.keyAttack,o.keyUse};
            history=new Object[keys.length];
            for(int i=0;i<keys.length;i++)if(exactNativeKey(keys[i])&&keys[i] instanceof NormalIdleKeyCarrier carrier)history[i]=carrier.normalIdleKeySnapshot();
            route=checkpoint.originalRouteDriver();originalRoute=checkpoint.originalRouteState();
            crop=checkpoint.originalTargetGuardBinding();
            rotationState=rotation==null?null:rotation.captureIssuedResumeRetirement(farm);
            breakingState=breaking==null?null:breaking.captureIssuedResumeBreaking(farm);
        }
        boolean headerCurrent() {
            if(revoked||CURRENT.get()!=this||thread!=Thread.currentThread()||!source.phaseCurrent()||!source.nativeCurrent())return false;
            // Last leaf after any source supplier/codec callback. No authority/provider callbacks below.
            return !revoked&&CURRENT.get()==this&&source.phaseCurrent()&&client==Minecraft.getInstance()&&client.isSameThread()
                &&client.player==player&&client.level==world&&client.getConnection()==connection&&client.options==options&&client.gameMode==gameMode
                &&player!=null&&world!=null&&connection!=null&&options!=null&&gameMode!=null&&installation!=null&&installation.current()
                &&input!=null&&rotation!=null&&breaking!=null&&McInputService.canonicalFarmRootCurrent(client)
                &&McInputService.canonicalFarmRotationCurrent(rotation,client)&&McInputService.canonicalFarmBreakingCurrent(breaking,client);
        }
        boolean current() {
            if(!headerCurrent()||keys.length!=9||rotationState==null||!rotationState.current()||breakingState==null||!breakingState.current()||!breaking.issuedResumeGuardCurrent(farm,crop))return false;
            if(route!=null&&!(route instanceof dev.veltraph.client.farming.movement.McFarmRouteDriver))return false;
            if(route!=null&&(routeRetirement==null?!java.util.Objects.equals(route.maintenanceState().orElse(null),originalRoute):!routeRetirement.current()))return false;
            for(int i=0;i<9;i++) {
                if(i==pendingKey)continue;
                if(!exactNativeKey(keys[i])||keys[i].isDown()||!(keys[i] instanceof NormalIdleKeyCarrier carrier)||history[i]==null)return false;
                var released=releases[i];
                if(released==null?!carrier.normalIdleKeyCurrent(history[i]):!released.confirmed()||released.held()
                        ||released.key()!=keys[i]||!released.normalIdleHistoryAdvancedFrom(history[i]))return false;
            }
            return !revoked&&CURRENT.get()==this&&source.phaseCurrent();
        }
    }
    /** Only the Farm's private checkpoint calls this with the actual privately minted COMMIT call. */
    public static boolean run(FarmModule farm,FarmModule.IssuedReturnCheckpoint checkpoint,
            McSuspendedFarmReturnCoordinator.ResumeCall call,Runnable originalHook) {
        java.util.Objects.requireNonNull(originalHook);
        var prior=CURRENT.get();
        if(prior!=null){prior.revoked=true;return false;}
        if(call==null||checkpoint==null||farm==null)return false;
        var invocation=new Invocation(farm,checkpoint,call);CURRENT.set(invocation);
        try {
            if(!call.current(farm,checkpoint)||!invocation.current())return false;
            originalHook.run();
            return invocation.current();
        } finally {CURRENT.remove();}
    }
    private static boolean exactNativeKey(KeyMapping key){return key!=null&&(key.getClass()==KeyMapping.class||key.getClass()==net.minecraft.client.ToggleKeyMapping.class);}
    public static boolean active(){return CURRENT.get()!=null;}
    public static boolean activeFor(FarmModule farm){var call=CURRENT.get();return call!=null&&call.farm==farm;}
    static void refuseUnscopedMutation(){var call=CURRENT.get();if(call!=null)call.revoked=true;}
    static boolean current(){var call=CURRENT.get();return call!=null&&call.current();}
    static boolean headerCurrent(){var call=CURRENT.get();return call!=null&&call.headerCurrent();}
    static FarmModule farm(){var call=CURRENT.get();return call==null?null:call.farm;}
    /** Exact private guard handout only; FarmModule must not re-read a raw same-owner binding. */
    public static BlockBreakingService.TargetGuardBinding installedTargetGuard(FarmModule farm){
        var call=CURRENT.get();return call!=null&&call.farm==farm&&call.current()?call.installedCrop:null;
    }
    private static final StackWalker CALLER = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);
    /** Refusal-only actual producer origin. Typed private source/checkpoint still supply all authority. */
    private static boolean originalCaller(Invocation call,int operation) {
        var kind=switch(operation){
            case 1->dev.veltraph.client.rewarp.IssuedFarmResumeCallerOrigin.Operation.KEY_RELEASE;
            case 2->dev.veltraph.client.rewarp.IssuedFarmResumeCallerOrigin.Operation.CAMERA_RETIRE;
            case 3->dev.veltraph.client.rewarp.IssuedFarmResumeCallerOrigin.Operation.CROP_INSTALL;
            case 4->dev.veltraph.client.rewarp.IssuedFarmResumeCallerOrigin.Operation.BREAKING_RETIRE;
            default->null;
        };
        return CALLER.walk(stream->dev.veltraph.client.rewarp.IssuedFarmResumeCallerOrigin.matches(
            stream.limit(48).map(f->new dev.veltraph.client.rewarp.IssuedFarmResumeCallerOrigin.Frame(f.getDeclaringClass(),f.getMethodName())).toList(),
            call.farm.getClass(),FarmModule.class,dev.veltraph.client.farming.movement.SShapeRowEngine.class,
            java.util.Set.of(McIssuedFarmResumeGuard.class,McInputService.class,McRotationService.class,McBlockBreakingService.class,McHeldKeys.class),kind));
    }
    public static final class RouteStopCall {
        private final Invocation original;
        private final dev.veltraph.client.farming.movement.McFarmRouteDriver driver;
        private final dev.veltraph.client.navigation.mc.McNavigator navigator;
        private RouteStopCall(Invocation original,dev.veltraph.client.farming.movement.McFarmRouteDriver driver,dev.veltraph.client.navigation.mc.McNavigator navigator){this.original=original;this.driver=driver;this.navigator=navigator;}
        public boolean beforeNativeCurrent(dev.veltraph.client.navigation.mc.McNavigator actual){
            return CURRENT.get()==original&&original.operation==5&&original.route==driver&&navigator==actual
                &&original.routeRetirement==null&&original.current();
        }
        public boolean acceptOwnLogicalRetirement(dev.veltraph.client.navigation.mc.McNavigator actual,dev.veltraph.client.navigation.mc.McNavigator.IssuedResumeStopReceipt receipt){
            if(CURRENT.get()!=original||original.operation!=5||original.route!=driver||navigator!=actual||original.routeRetirement!=null
                    ||receipt==null||!receipt.belongs(this,actual)||!receipt.current())return false;
            original.routeRetirement=receipt;
            return original.current();
        }
        public boolean releaseMovementAfterOwnRetirement(dev.veltraph.client.navigation.mc.McNavigator actual){
            if(CURRENT.get()!=original||original.operation!=5||actual!=navigator||original.routeRetirement==null||!original.current())return false;
            for(int i=0;i<7;i++){releaseInside(original,original.input,original.keys[i],false);if(!original.current())return false;}
            return original.current();
        }
    }
    public static boolean stopRoute(dev.veltraph.client.farming.movement.McFarmRouteDriver driver,dev.veltraph.client.navigation.mc.McNavigator navigator){
        var call=CURRENT.get();if(call==null)return false;
        // The route is the exact private checkpoint's source port, never a caller-selected navigator.
        if(call.operation!=0||call.route!=driver||!routeCaller(call)||!call.current()){call.revoked=true;return true;}
        call.operation=5;
        try {
            if(call.routeRetirement!=null)return true;
            var actual=new RouteStopCall(call,driver,navigator);
            if(!navigator.stopIssuedFarmResume(actual)||!call.current())call.revoked=true;
            return true;
        } finally {call.operation=0;}
    }
    private static boolean routeCaller(Invocation call){
        return CALLER.walk(stream->{var frames=stream.limit(48).toList();
            var first=frames.stream().filter(f->f.getDeclaringClass()!=McIssuedFarmResumeGuard.class
                &&f.getDeclaringClass()!=dev.veltraph.client.farming.movement.McFarmRouteDriver.class).findFirst().orElse(null);
            if(first==null)return false;
            var source=frames.stream().filter(f->f.getDeclaringClass()==FarmModule.class
                &&java.util.Set.of("resumeAfterRewarp","resumeIssuedReturn","rebindForScheduledRest").contains(f.getMethodName())).findFirst().orElse(null);
            if(source==null||!source.getMethodName().equals("resumeIssuedReturn")
                ||frames.stream().anyMatch(f->f.getDeclaringClass()==call.farm.getClass()
                    &&java.util.Set.of("onEnable","onDisableFarming","onTickFarming").contains(f.getMethodName())))return false;
            return (first.getDeclaringClass()==call.farm.getClass()||first.getDeclaringClass().getEnclosingClass()==call.farm.getClass()
                ||first.getDeclaringClass()==dev.veltraph.client.farming.movement.SShapeRowEngine.class
                ||first.getDeclaringClass()==dev.veltraph.client.farming.movement.FarmDropHandoff.class)
                &&frames.stream().anyMatch(f->f.getDeclaringClass()==call.farm.getClass()&&f.getMethodName().equals("onResumeIssuedReturn"));
        });
    }
    private static boolean begin(Invocation call,int operation) {
        if(call.operation!=0||!originalCaller(call,operation)){call.revoked=true;return false;}
        call.operation=operation;return true;
    }
    static boolean release(McInputService service,KeyMapping key,boolean held) {
        var call=CURRENT.get();if(call==null)return false;
        if(!begin(call,1))return true;
        try {releaseInside(call,service,key,held);return true;}
        finally {call.operation=0;}
    }
    private static void releaseInside(Invocation call,McInputService service,KeyMapping key,boolean held) {
        if(held||service!=call.input||call.pendingKey!=-1||!call.current()){call.revoked=true;return;}
        int index=-1;for(int i=0;i<call.keys.length;i++)if(call.keys[i]==key)index=i;
        if(index<0){call.revoked=true;return;}
        if(call.releases[index]!=null)return;
        call.pendingKey=index;
        McHeldKeys.ScopedHeldReceipt receipt;
        try {receipt=McHeldKeys.releaseIssuedResumeObserved(key,call.history[index],call::current);}
        catch(RuntimeException|Error unavailable){call.revoked=true;throw unavailable;}
        finally {call.pendingKey=-1;}
        call.releases[index]=receipt;
        if(!call.current())call.revoked=true;
    }
    static boolean retireRotation(McRotationService service) {
        var call=CURRENT.get();if(call==null)return false;
        if(!begin(call,2))return true;
        try {
            if(service!=call.rotation||!call.current()||!call.rotationState.retire(call::current,call::headerCurrent))call.revoked=true;
            return true;
        } finally {call.operation=0;}
    }
    static boolean installCrop(McBlockBreakingService service,Object owner,java.util.function.Predicate<dev.veltraph.client.world.BlockPos> predicate) {
        var call=CURRENT.get();if(call==null)return false;
        // A foreign actual publication remains foreign; the original hook cannot erase it.
        if(service!=call.breaking||owner!=call.farm||!originalCaller(call,3)){call.revoked=true;return false;}
        if(!begin(call,3))return true;
        try {
            if(service!=call.breaking||owner!=call.farm||call.installedCrop!=null||!call.current()){call.revoked=true;return true;}
            var actual=service.installIssuedResumeGuard(call.farm,call.crop,predicate,call::current);
            if(actual==null){call.revoked=true;return true;}
            call.crop=actual;call.installedCrop=actual;
            if(!call.current())call.revoked=true;
            return true;
        } finally {call.operation=0;}
    }
    static boolean releaseBreaking(McBlockBreakingService service,boolean requested) {
        var call=CURRENT.get();if(call==null)return false;
        if(!begin(call,4))return true;
        try {
            if(requested||service!=call.breaking||!call.current()){call.revoked=true;return true;}
            // Arrival is neutral. Never infer ownership of a true attack key from its current value.
            releaseInside(call,call.input,call.keys[7],false);
            if(!call.current()||!call.breakingState.retire(call::current))call.revoked=true;
            return true;
        } finally {call.operation=0;}
    }
}
