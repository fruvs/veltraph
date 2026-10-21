package dev.veltraph.client.service.mc;

import dev.veltraph.client.service.RotationService;
import dev.veltraph.client.world.Vec3;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

/**
 * Minecraft camera application with continuous bounded tracking and source-derived one-shot requests.
 * Aim publishes already-planned endpoints; tick/frame consumers apply the actual pose.
 * rotateTo retains the recovered quantized speed curve and completes only after actual application.
 */
public final class McRotationService implements RotationService {

    private static final RootCameraObservationEpisodes ROOT_CAMERA_EPISODES=new RootCameraObservationEpisodes();
    private static final ThreadLocal<RootNativeCameraCall> ROOT_NATIVE_CAMERA_CALL=new ThreadLocal<>();
    private CameraMethodEpisode cameraMethodEpisode;
    private RootNativeCameraCall retainedRootNativeCameraCall;
    private final class CameraMethodEpisode implements AutoCloseable {
        final RootCameraObservationEpisodes.Episode order;
        final CameraMethodEpisode previous;
        final dev.veltraph.client.core.Module original;
        final dev.veltraph.client.VeltraphBootstrap.RootOriginalNativeEnrollment source;
        final dev.veltraph.client.VeltraphBootstrap.RootOriginalNativeHelperPublication helperSource;
        final dev.veltraph.client.config.MapConfigService.StoredDoubleSnapshot settings;
        final dev.veltraph.client.input.ScriptingInputLock.ControlSnapshot input;
        final Object originalPlayer,originalWorld,originalListener,originalOptions;
        dev.veltraph.client.VeltraphBootstrap.RootOriginalCameraAdvanceInvocation advanceInvocation;
        boolean normal,closed,advancing;
        CameraMethodEpisode(){
            order=ROOT_CAMERA_EPISODES.enter();previous=cameraMethodEpisode;cameraMethodEpisode=this;
            // Stored/native anchors precede the source lookup and every existing service provider.
            settings=storedRotationSnapshot();input=dev.veltraph.client.input.ScriptingInputLock.controlSnapshot();
            originalPlayer=client.player;originalWorld=client.level;originalListener=client.getConnection();originalOptions=client.options;
            dev.veltraph.client.core.Module module=null;
            dev.veltraph.client.VeltraphBootstrap.RootOriginalNativeEnrollment enrolled=null;
            try{if(order.current()){
                module=dev.veltraph.client.VeltraphBootstrap.originalCameraIssuingModule(McRotationService.this);
                if(module!=null)enrolled=RootNativeHistory.enrolledSourceFor(module,client);
                if(!order.current())enrolled=null;
            }}catch(RuntimeException|Error unavailable){order.revoke();}
            original=module;source=enrolled;
            dev.veltraph.client.VeltraphBootstrap.RootOriginalNativeHelperPublication helper=null;
            try{if(order.current()&&module!=null)helper=dev.veltraph.client.VeltraphBootstrap.originalNativeHelperPublicationFor(module,client);}
            catch(RuntimeException|Error unavailable){order.revoke();}
            helperSource=helper;
        }
        boolean active(){return !closed&&cameraMethodEpisode==this&&order.current();}
        @Override public void close(){
            if(closed)return;closed=true;if(!normal)order.revoke();
            if(cameraMethodEpisode==this)cameraMethodEpisode=previous;else order.revoke();order.close();
        }
    }
    private CameraMethodEpisode enterCameraMethod(){
        if(!client.isSameThread())return null;
        var previous=cameraMethodEpisode;
        try{return new CameraMethodEpisode();}
        catch(RuntimeException|Error unavailable){
            var attempted=cameraMethodEpisode;
            if(attempted!=null&&attempted!=previous){attempted.order.revoke();attempted.close();}
            if(previous!=null)previous.order.revoke();
            return null; // Observation failure does not replace the original effect/return/throw.
        }
    }
    private QueuedRootCameraDeclaration queuedRootCameraDeclaration;
    /** Dormant original request identity. This record cannot mint Root's normally closed declaration. */
    private final class QueuedRootCameraDeclaration {
        final dev.veltraph.client.VeltraphBootstrap.RootOriginalCameraQueueSource source;
        final CameraMethodEpisode declaring;
        final long generation=requestGeneration,initialEpoch=requestEpoch,initialSerial=requestPublicationSerial;
        final Object player=owner,world=ownerLevel,listener=requestConnection,authority=requestAuthority;
        final float yaw,pitch;final Runnable completed;
        QueuedRootCameraDeclaration(dev.veltraph.client.VeltraphBootstrap.RootOriginalCameraQueueSource source,
                CameraMethodEpisode declaring,float yaw,float pitch,Runnable completed){
            this.source=source;this.declaring=declaring;this.yaw=yaw;this.pitch=pitch;this.completed=completed;
        }
        boolean metadataCurrent(){
            return queuedRootCameraDeclaration==this&&source!=null&&declaring!=null&&declaring.closed&&declaring.normal
                &&declaring.order.unambiguous()&&source.originalModule()==declaring.original&&source.enrollmentIdentity()==declaring.source
                &&requestGeneration==generation&&request.pending()&&owner==player&&ownerLevel==world&&requestConnection==listener
                &&requestAuthority==authority&&authority==source.originalModule()&&client.player==player&&client.level==world
                &&client.getConnection()==listener&&declaring.settings!=null&&declaring.settings.current()
                &&declaring.input!=null&&declaring.input.current();
        }
        boolean advancingCurrent(){return metadataCurrent()&&source.originalAdvanceCurrent(McRotationService.this)&&metadataCurrent();}
        boolean retainedCurrent(){return metadataCurrent()&&source.originalDeclarationCurrent()&&metadataCurrent();}
    }
    private void retainRootCameraDeclaration(dev.veltraph.client.VeltraphBootstrap.RootOriginalCameraQueueSource source,
            CameraMethodEpisode episode,long beforeGeneration,float yaw,float pitch,Runnable completed){
        try{
            if(source==null||episode==null||!episode.active()||episode.original==null||episode.source==null
                    ||source.originalModule()!=episode.original||source.enrollmentIdentity()!=episode.source
                    ||beforeGeneration==Long.MAX_VALUE||requestGeneration!=beforeGeneration+1||!request.pending()
                    ||owner!=client.player||ownerLevel!=client.level||requestConnection!=client.getConnection()
                    ||requestAuthority!=episode.original||episode.settings==null||!episode.settings.current()
                    ||episode.input==null||!episode.input.current()||client.player!=episode.originalPlayer
                    ||client.level!=episode.originalWorld||client.getConnection()!=episode.originalListener||client.options!=episode.originalOptions)return;
            // No request target is reread or adopted: these are the exact successful replace() arguments.
            queuedRootCameraDeclaration=new QueuedRootCameraDeclaration(source,episode,yaw,pitch,completed);
        }catch(RuntimeException|Error unavailable){episode.order.revoke();queuedRootCameraDeclaration=null;}
    }
    /** An actual canonical service call. There is no caller-supplied ticket or Root capability. */
    public final class RootNativeCameraCall {
        private final CameraMethodEpisode episode;
        private final QueuedRootCameraDeclaration queued;
        private final SavedReturnQueuedHandle saved;
        private final SavedReturnQueuedHandle.SavedReturnPublicationFrame savedFrame;
        private final SavedReturnCancellationFrame savedCancellation;
        private final dev.veltraph.client.VeltraphBootstrap.RootOriginalCameraAdvanceInvocation advanceInvocation;
        private final boolean cancellation;
        private final float yaw,pitch;
        private final Object player=client.player,world=client.level,listener=client.getConnection(),options=client.options,store=config;
        private final long generation=requestGeneration;
        private final dev.veltraph.client.core.ModuleManager root=dev.veltraph.client.VeltraphBootstrap.moduleManager();
        private dev.veltraph.client.core.ModuleManager.LifecycleSnapshot lifecycle=root==null?null:root.lifecycleSnapshot();
        private final dev.veltraph.client.input.ScriptingInputLock.ControlSnapshot input=dev.veltraph.client.input.ScriptingInputLock.controlSnapshot();
        private McOwnedRotation.NativeMetadataInvocation invocation;
        private McOwnedRotation.NativeMetadataNormalReturn returned;
        private McOwnedRotation.NativeMetadataNormalReturn observingNormalReturn;
        private boolean revoked;
        private RootNativeCameraCall(CameraMethodEpisode episode,boolean cancellation,float yaw,float pitch){this.episode=episode;queued=episode.advancing?queuedRootCameraDeclaration:null;saved=null;savedFrame=null;savedCancellation=null;advanceInvocation=episode.advanceInvocation;this.cancellation=cancellation;this.yaw=yaw;this.pitch=pitch;}
        private RootNativeCameraCall(CameraMethodEpisode episode,SavedReturnQueuedHandle saved,
                SavedReturnQueuedHandle.SavedReturnPublicationFrame frame,float yaw,float pitch){
            this.episode=episode;this.saved=saved;savedFrame=frame;savedCancellation=null;queued=null;advanceInvocation=null;cancellation=false;this.yaw=yaw;this.pitch=pitch;
        }
        private RootNativeCameraCall(CameraMethodEpisode episode,SavedReturnCancellationFrame frame){
            this.episode=episode;saved=frame.original;savedFrame=null;savedCancellation=frame;queued=null;advanceInvocation=null;
            cancellation=true;yaw=Float.NaN;pitch=Float.NaN;
        }
        private Minecraft clientIdentity(){return client;}
        public dev.veltraph.client.core.Module originalModule(){return saved==null?episode.original:saved.source.inputOwner();}
        public dev.veltraph.client.core.Module originalFarm(){return saved!=null?saved.source.originalFarm():
            episode.helperSource!=null?episode.helperSource.originalFarm():episode.original;}
        /** Read-only native origin after the exact original metadata API has already returned normally. */
        private boolean savedReturnedObservationCurrent(){
            var actual=returned!=null?returned:observingNormalReturn;
            return saved!=null&&!cancellation&&invocation!=null&&actual!=null&&actual.matchesCall(this)&&actual.current()
                &&!actual.cancellation()&&actual.advancedFrom(invocation.beforeSnapshot());
        }
        private boolean savedSourceLeafCurrent(){
            if(!savedReturnedObservationCurrent())return saved.source.currentFinalNative();
            // This private observer never supplies an action leaf. Requiring the old history here
            // would circularly refuse the genuine event before normalCameraReturned can record it.
            return saved.source.publicationAffiliationCurrent(saved.source.originalInputPublication())
                &&saved.source.cleanupFinalNative();
        }
        private boolean savedSourceCurrent(){
            if(savedCancellation!=null)return episode.source!=null&&saved.rootSource.enrollmentIdentity()==episode.source
                &&savedCancellation.current()&&episode.source.originalProducerCurrent(saved.source.inputOwner(),client)
                &&savedCancellation.current();
            if(saved==null||savedFrame==null||episode.source==null||saved.rootSource.enrollmentIdentity()!=episode.source
                    ||savedFrame.original.sourceIdentity()!=saved.rootSource||!savedFrame.current()||!savedSourceLeafCurrent())return false;
            if(!episode.source.originalProducerCurrent(saved.source.inputOwner(),client))return false;
            return saved.metadataCurrent()&&savedFrame.current()&&savedSourceLeafCurrent()
                &&saved.source.originalFarm()!=null&&saved.source.cameraOwner()!=null;
        }
        /** Retained exact issuer/scope binding; token scope never derives identity from current owner bits. */
        public boolean poseScopeOriginCurrent(dev.veltraph.client.rotation.OwnedRotationTrajectory.Scope exact){
            if(cancellation||exact==null||returned==null||!normalReturnedCurrent(returned)||exact.player()!=player||exact.world()!=world||exact.connection()!=listener)return false;
            if(saved!=null)return exact.owner()==saved.source.cameraOwner()&&savedSourceCurrent();
            if(exact.owner()!=episode.original)return false;
            return episode.helperSource==null||episode.helperSource.helperIdentity()==episode.original
                &&episode.helperSource.enrollmentIdentity()==episode.source&&episode.helperSource.currentFor(episode.original,client);
        }
        public boolean nativeSourceCurrent(Minecraft actual){
            if(revoked||actual!=client||episode.source==null||originalModule()==null||!client.isSameThread()
                    ||saved!=null&&!savedSourceCurrent()
                    ||saved==null&&episode.advancing&&(queued==null||advanceInvocation==null||advanceInvocation.sourceIdentity()!=queued.source
                        ||!(advanceInvocation.issuingCurrent()||advanceInvocation.publicationCurrent())))return false;
            try{if(!episode.source.originalProducerCurrent(originalModule(),client))return false;
                // Final stored/native leaves follow the source check; no owner or source is recaptured.
                return !revoked&&episode.order.unambiguous()&&(!episode.closed||episode.normal)
                    && (saved!=null?savedSourceCurrent():!episode.advancing||queued!=null&&queued.metadataCurrent()&&advanceInvocation!=null
                        &&(advanceInvocation.issuingCurrent()||advanceInvocation.publicationCurrent()))
                    &&episode.settings!=null&&episode.settings.current()&&episode.input!=null&&episode.input.current()
                    &&client.player==episode.originalPlayer&&client.level==episode.originalWorld
                    &&client.getConnection()==episode.originalListener&&client.options==episode.originalOptions
                    &&root==dev.veltraph.client.VeltraphBootstrap.moduleManager()&&lifecycle!=null&&root.lifecycleCurrent(lifecycle)
                    &&input!=null&&input.current()&&config==store&&dev.veltraph.client.VeltraphBootstrap.config()==store
                    &&client.player==player&&client.level==world&&client.getConnection()==listener&&client.options==options
                    &&requestGeneration==generation&&McInputService.canonicalFarmRotationCurrent(McRotationService.this,client);
            }catch(RuntimeException|Error unavailable){revoked=true;return false;}
        }
        public boolean claimNativeInvocation(McOwnedRotation.NativeMetadataInvocation actual){
            if(invocation!=null){revoked=true;return false;}
            if(actual==null||!episode.active()||ROOT_NATIVE_CAMERA_CALL.get()!=this||actual.client()!=client||actual.cancellation()!=cancellation
                    ||!cancellation&&(Float.floatToIntBits(actual.endpointYaw())!=Float.floatToIntBits(yaw)||Float.floatToIntBits(actual.endpointPitch())!=Float.floatToIntBits(pitch))
                    ||!actual.nativeEntryMatches(this)||!nativeSourceCurrent(client))return false;
            invocation=actual;return true;
        }
        private void nativeReturned(McOwnedRotation.NativeMetadataNormalReturn actual){
            if(actual==null||invocation==null||returned!=null||observingNormalReturn!=null||!episode.active()
                    ||!actual.matchesCall(this)||actual.cancellation()!=cancellation||!actual.current()
                    ||!actual.advancedFrom(invocation.beforeSnapshot())){revoked=true;return;}
            observingNormalReturn=actual;
            try{if(!nativeSourceCurrent(client)){revoked=true;return;}
                returned=actual;RootNativeHistory.normalCameraReturned(actual);
                if(!revoked&&actual.current())retainedRootNativeCameraCall=this;
            }finally{observingNormalReturn=null;}
        }
        public boolean normalReturnedCurrent(McOwnedRotation.NativeMetadataNormalReturn actual){return !revoked&&returned==actual&&actual!=null&&actual.matchesCall(this)&&actual.current()&&nativeSourceCurrent(client);}
    }
    /** Lookup only inside the exact original native operation. */
    public static RootNativeCameraCall rootNativeCameraCall(Minecraft client,boolean cancellation){
        var call=ROOT_NATIVE_CAMERA_CALL.get();return call!=null&&call.clientIdentity()==client&&call.cancellation==cancellation&&call.episode.active()?call:null;
    }
    private RootNativeCameraCall enterRootNativeCall(boolean cancellation,float yaw,float pitch){
        var episode=cameraMethodEpisode;RootNativeCameraCall call=null;
        try{if(episode!=null&&episode.active()&&episode.source!=null
                &&(!episode.advancing||queuedRootCameraDeclaration!=null&&queuedRootCameraDeclaration.metadataCurrent()
                    &&episode.advanceInvocation!=null&&episode.advanceInvocation.sourceIdentity()==queuedRootCameraDeclaration.source
                    &&episode.advanceInvocation.issuingCurrent()))
                call=new RootNativeCameraCall(episode,cancellation,yaw,pitch);}
        catch(RuntimeException|Error unavailable){if(episode!=null)episode.order.revoke();}
        return call;
    }
    private long observeRootNativePlan(float yaw,float pitch,java.util.function.LongSupplier original){
        var call=enterRootNativeCall(false,yaw,pitch);var previous=ROOT_NATIVE_CAMERA_CALL.get();
        if(previous!=null){previous.revoked=true;if(call!=null)call.revoked=true;}if(call!=null)ROOT_NATIVE_CAMERA_CALL.set(call);
        try{long result=original.getAsLong();if(call!=null)try{call.nativeReturned(McOwnedRotation.nativeMetadataReturnFor(call));}catch(RuntimeException|Error unavailable){call.revoked=true;}return result;}
        finally{if(call!=null&&ROOT_NATIVE_CAMERA_CALL.get()==call){if(previous==null)ROOT_NATIVE_CAMERA_CALL.remove();else ROOT_NATIVE_CAMERA_CALL.set(previous);}}
    }
    private McOwnedRotation.QueuedScopedPlan observeSavedReturnNativePlan(SavedReturnQueuedHandle saved,
            SavedReturnQueuedHandle.SavedReturnPublicationFrame frame,float yaw,float pitch,
            java.util.function.Supplier<McOwnedRotation.QueuedScopedPlan> original){
        var episode=cameraMethodEpisode;RootNativeCameraCall call=null;
        try{if(episode!=null&&episode.active()&&episode.source!=null&&frame.original.issuingCurrent()
                &&saved.rootSource.enrollmentIdentity()==episode.source)call=new RootNativeCameraCall(episode,saved,frame,yaw,pitch);}
        catch(RuntimeException|Error unavailable){if(episode!=null)episode.order.revoke();}
        var previous=ROOT_NATIVE_CAMERA_CALL.get();if(previous!=null){previous.revoked=true;if(call!=null)call.revoked=true;}
        if(call!=null)ROOT_NATIVE_CAMERA_CALL.set(call);
        try{var result=original.get();if(call!=null)try{call.nativeReturned(McOwnedRotation.nativeMetadataReturnFor(call));}
            catch(RuntimeException|Error unavailable){call.revoked=true;}return result;}
        finally{if(call!=null&&ROOT_NATIVE_CAMERA_CALL.get()==call){if(previous==null)ROOT_NATIVE_CAMERA_CALL.remove();else ROOT_NATIVE_CAMERA_CALL.set(previous);}}
    }
    /** One exact normally returned scoped cancellation; no old plan can become a new publication. */
    private final class SavedReturnCancellationFrame {
        final SavedReturnQueuedHandle original;
        final McOwnedRotation.QueuedScopedPlan plan;
        final long generation,epoch,serial;
        McOwnedRotation.QueuedCancellation returned;
        SavedReturnCancellationFrame(SavedReturnQueuedHandle exact){
            original=exact;plan=exact.plan;generation=exact.generation;epoch=exact.epoch;serial=exact.serial;
        }
        boolean current(){
            if(original==null||plan==null||original.plan!=plan||original.generation!=generation||original.epoch!=epoch
                    ||original.serial!=serial||!original.cleanupMetadataCurrent())return false;
            if(returned==null)return original.cleanupNativeCurrent(epoch)&&plan.cleanupNativeCurrent();
            return returned.original()==plan&&returned.beforeEpoch()==epoch&&epoch<Long.MAX_VALUE&&returned.afterEpoch()==epoch+1
                &&returned.publicationSerial()==serial&&returned.cleanupNativeCurrent()&&original.cleanupNativeCurrent(returned.afterEpoch());
        }
    }
    private McOwnedRotation.QueuedCancellation observeSavedReturnNativeCancel(SavedReturnCancellationFrame frame,
            java.util.function.Supplier<McOwnedRotation.QueuedCancellation> original){
        var episode=cameraMethodEpisode;RootNativeCameraCall call=null;
        try{if(episode!=null&&episode.active()&&episode.source!=null&&frame.current()
                &&frame.original.rootSource.enrollmentIdentity()==episode.source)call=new RootNativeCameraCall(episode,frame);}
        catch(RuntimeException|Error unavailable){if(episode!=null)episode.order.revoke();}
        var previous=ROOT_NATIVE_CAMERA_CALL.get();if(previous!=null){previous.revoked=true;if(call!=null)call.revoked=true;}
        if(call!=null)ROOT_NATIVE_CAMERA_CALL.set(call);
        try{var result=original.get();frame.returned=result;
            if(call!=null)try{call.nativeReturned(McOwnedRotation.nativeMetadataReturnFor(call));}
            catch(RuntimeException|Error unavailable){call.revoked=true;}return result;}
        finally{if(call!=null&&ROOT_NATIVE_CAMERA_CALL.get()==call){if(previous==null)ROOT_NATIVE_CAMERA_CALL.remove();else ROOT_NATIVE_CAMERA_CALL.set(previous);}}
    }
    private void observeRootNativeCancel(Runnable original){
        var call=enterRootNativeCall(true,Float.NaN,Float.NaN);var previous=ROOT_NATIVE_CAMERA_CALL.get();
        if(previous!=null){previous.revoked=true;if(call!=null)call.revoked=true;}if(call!=null)ROOT_NATIVE_CAMERA_CALL.set(call);
        try{original.run();if(call!=null)try{call.nativeReturned(McOwnedRotation.nativeMetadataReturnFor(call));}catch(RuntimeException|Error unavailable){call.revoked=true;}}
        finally{if(call!=null&&ROOT_NATIVE_CAMERA_CALL.get()==call){if(previous==null)ROOT_NATIVE_CAMERA_CALL.remove();else ROOT_NATIVE_CAMERA_CALL.set(previous);}}
    }

    private final Minecraft client;
    private final dev.veltraph.client.config.ConfigService config;
    private static final double SESSION_MULTIPLIER=java.util.concurrent.ThreadLocalRandom.current().nextDouble(.97,1.02);
    private final dev.veltraph.client.rotation.RotationRequest request=new dev.veltraph.client.rotation.RotationRequest(
            new dev.veltraph.client.rotation.SourceRotationStepper(SESSION_MULTIPLIER));
    private final dev.veltraph.client.navigation.TravelHeading trackingYaw=new dev.veltraph.client.navigation.TravelHeading();
    private final dev.veltraph.client.navigation.TravelHeading trackingPitch=new dev.veltraph.client.navigation.TravelHeading();
    private int lastTrackTick=Integer.MIN_VALUE;
    private boolean stepped;
    private Object requestAuthority;
    private net.minecraft.client.multiplayer.ClientPacketListener requestConnection;
    private dev.veltraph.client.rotation.SourceRotationStepper.Step awaitingEndpoint;
    private long awaitingTicket,requestEpoch,requestGeneration,requestPublicationSerial;
    private dev.veltraph.client.rotation.RotationPublicationIdentity.Transition publicationTransition;
    private DirectPublicationReceipt lastDirectPublication;
    private String cachedDirectScopeAuthority="NOT_OBSERVED";
    /** Cached original directScope operand only; no source/native callback executes here. */
    public String cachedDirectScopeAuthority(){return cachedDirectScopeAuthority;}
    private DirectCancellationReceipt lastDirectCancellation;
    private CleanupReceipt guiHandedPrevious,guiHandedLatest;
    private long guiHandedGeneration=Long.MIN_VALUE;private GuiRequestRetirement lastGuiRetirement;
    /** Two canonical genuine handouts suffice for the latest own before/after publication stamps. */
    private CleanupReceipt handGuiSource(CleanupReceipt receipt){
        if(guiHandedGeneration!=requestGeneration){guiHandedPrevious=guiHandedLatest=null;guiHandedGeneration=requestGeneration;}
        if(sameGuiHandout(receipt,guiHandedLatest))return guiHandedLatest;if(sameGuiHandout(receipt,guiHandedPrevious))return guiHandedPrevious;
        guiHandedPrevious=guiHandedLatest;guiHandedLatest=receipt;return receipt;
    }
    private static boolean sameGuiHandout(CleanupReceipt a,CleanupReceipt b){return a!=null&&b!=null&&a.service()==b.service()&&a.generation()==b.generation()&&a.authority()==b.authority()
        &&a.player()==b.player()&&a.world()==b.world()&&a.connection()==b.connection()&&a.epoch()==b.epoch();}
    private boolean guiHandedSource(CleanupReceipt exact){return exact!=null&&(exact==guiHandedLatest||exact==guiHandedPrevious);}
    private LocalPlayer owner;
    private net.minecraft.client.multiplayer.ClientLevel ownerLevel;
    public static double sessionMultiplier() { return SESSION_MULTIPLIER; }

    public McRotationService(Minecraft client) {
        this(client,new dev.veltraph.client.config.MapConfigService());
    }
    public McRotationService(Minecraft client,dev.veltraph.client.config.ConfigService config) {
        this.client = client;
        this.config = config;
    }

    private final class FarmCamera {
        final McFarmOwnedCleanup.NativeScope scope; final DirectPublicationReceipt direct;
        final long generation, epoch, serial; final boolean nativePublished,queued;
        FarmCamera(McFarmOwnedCleanup.NativeScope scope,DirectPublicationReceipt direct,boolean nativePublished,boolean queued) {
            this.scope=scope;this.direct=direct;this.nativePublished=nativePublished;this.queued=queued;
            generation=requestGeneration;epoch=McOwnedRotation.epoch(client);serial=McOwnedRotation.publicationSerial(client);
        }
        private FarmCamera(FarmCamera original,McFarmOwnedCleanup.NativeScope continued){
            scope=continued;direct=original.direct;nativePublished=original.nativePublished;queued=original.queued;
            generation=original.generation;epoch=original.epoch;serial=original.serial;
        }
        boolean storedCurrent() {
            return farmCamera==this&&McInputService.canonicalFarmRotationCurrent(McRotationService.this,client)
                &&scope.stopCurrent()&&requestGeneration==generation
                &&McOwnedRotation.epoch(client)==epoch&&McOwnedRotation.publicationSerial(client)==serial
                &&(direct!=null?lastDirectPublication==direct:!queued||requestAuthority==scope.farm&&owner==scope.player
                    &&ownerLevel==scope.world&&requestConnection==scope.connection);
        }
    }
    private FarmCamera farmCamera;
    /** Exact old camera custody participates in the same genuine passive lifecycle handoff as keys/breaking. */
    static final class PassiveCameraContinuation {
        final McRotationService service;final RootNativeHistory.PassiveTransitionObservation source;
        final FarmCamera before;final RootPassiveCameraSlot<FarmCamera> slot;
        final long generation,epoch,serial,ticket;
        final Object authority,player,world,listener,endpoint,direct,cancellation;
        final boolean pending;
        FarmCamera next;
        final QueuedRootCameraDeclaration declaration;
        final SavedReturnQueuedHandle saved;
        final QueuedRequestHandle queued;
        final RootNativeCameraCall call;
        final dev.veltraph.client.core.ModuleManager.LifecycleSnapshot savedLifecycle,queuedLifecycle,callLifecycle;
        final Object declarationPointer,savedPointer,queuedPointer,callPointer;
        final java.util.IdentityHashMap<Object,dev.veltraph.client.VeltraphBootstrap.RootCameraDeclarationPassiveContinuation> declarations=new java.util.IdentityHashMap<>();
        dev.veltraph.client.rewarp.mc.FarmMaintenanceCoordinator.MaintenancePassiveContinuation maintenance;
        dev.veltraph.client.navigation.mc.McFarmMaintenanceAuthorityBridge.QueuedRotationPassiveContinuation bridge;
        private boolean prepared,committed,closed;
        PassiveCameraContinuation(McRotationService service,RootNativeHistory.PassiveTransitionObservation source){
            this.service=service;this.source=source;before=service.farmCamera;slot=new RootPassiveCameraSlot<>(before);
            generation=service.requestGeneration;epoch=service.requestEpoch;serial=service.requestPublicationSerial;ticket=service.awaitingTicket;
            authority=service.requestAuthority;player=service.owner;world=service.ownerLevel;listener=service.requestConnection;
            endpoint=service.awaitingEndpoint;direct=service.lastDirectPublication;cancellation=service.lastDirectCancellation;pending=service.request.pending();
            declarationPointer=service.queuedRootCameraDeclaration;savedPointer=service.savedReturnQueue;
            queuedPointer=service.lastQueuedRequest;callPointer=service.retainedRootNativeCameraCall;
            declaration=service.queuedRootCameraDeclaration!=null&&service.queuedRootCameraDeclaration.generation==generation&&pending?service.queuedRootCameraDeclaration:null;
            saved=service.savedReturnQueue!=null&&!service.savedReturnQueue.cancelled&&!service.savedReturnQueue.revoked
                &&service.savedReturnQueue.generation==generation?service.savedReturnQueue:null;
            queued=service.lastQueuedRequest!=null&&!service.lastQueuedRequest.cancelled&&service.lastQueuedRequest.generation==generation?service.lastQueuedRequest:null;
            var retained=service.retainedRootNativeCameraCall;
            call=retained!=null&&retained.generation==generation&&retained.returned!=null&&retained.returned.current()?retained:null;
            savedLifecycle=saved==null?null:saved.lifecycle;queuedLifecycle=queued==null?null:queued.lifecycle;callLifecycle=call==null?null:call.lifecycle;
        }
        boolean retainDeclaration(dev.veltraph.client.VeltraphBootstrap.RootOriginalCameraQueueSource original){
            if(original==null||declarations.containsKey(original))return original!=null;
            var next=original.capturePassiveContinuation(source.actual(),service);if(next==null)return false;
            declarations.put(original,next);return true;
        }
        boolean retainDeclaration(dev.veltraph.client.VeltraphBootstrap.RootSavedReturnCameraQueueSource original){
            if(original==null||declarations.containsKey(original))return original!=null;
            var next=original.capturePassiveContinuation(source.actual(),service);if(next==null)return false;
            declarations.put(original,next);return true;
        }
    }
    static PassiveCameraContinuation capturePassiveCameraContinuation(RootNativeHistory.PassiveTransitionObservation source){
        var installation=source.actual().originalInstallation();
        var service=installation==null?null:installation.rotationServiceIdentity(source.client());
        if(service==null||service.farmPlanCall!=null||service.cameraMethodEpisode!=null)return null;
        var captured=new PassiveCameraContinuation(service,source);boolean accepted=false;
        try{
            if(captured.before!=null&&!source.beforeScopeCurrent(captured.before.scope))return null;
            var root=dev.veltraph.client.VeltraphBootstrap.moduleManager();
            if(root==null||root!=source.actual().registryIdentity())return null;
            var coordinator=dev.veltraph.client.VeltraphBootstrap.farmMaintenance();
            captured.maintenance=coordinator==null?null:coordinator.capturePassiveMaintenanceContinuation(source.actual());
            if(captured.maintenance==null)return null;
            if(captured.declaration!=null&&(!captured.declaration.retainedCurrent()||!captured.retainDeclaration(captured.declaration.source)))return null;
            if(captured.saved!=null&&(!captured.saved.current()||!root.lifecycleSameHistory(captured.savedLifecycle,source.actual().beforeLifecycle())
                    ||!captured.retainDeclaration(captured.saved.rootSource)))return null;
            if(captured.queued!=null){
                if(!captured.queued.current()||!root.lifecycleSameHistory(captured.queuedLifecycle,source.actual().beforeLifecycle()))return null;
                captured.bridge=dev.veltraph.client.navigation.mc.McFarmMaintenanceAuthorityBridge.captureQueuedRotationPassiveContinuation(
                    captured.queued.binding,service.client,service.config,service,captured.queued.source,captured.maintenance);
                if(captured.bridge==null)return null;
            }
            if(captured.call!=null){
                if(!captured.call.normalReturnedCurrent(captured.call.returned)
                        ||!root.lifecycleSameHistory(captured.callLifecycle,source.actual().beforeLifecycle()))return null;
                if(captured.call.queued!=null&&!captured.retainDeclaration(captured.call.queued.source))return null;
                if(captured.call.saved!=null&&!captured.retainDeclaration(captured.call.saved.rootSource))return null;
            }
            if(!passiveCameraCommitCurrent(captured)||!source.actual().sourceBeforeCurrent(service.client))return null;
            accepted=true;return captured;
        }finally{if(!accepted)closePassiveCameraContinuation(captured);}
    }
    static PassiveCameraContinuation preparePassiveCameraContinuation(PassiveCameraContinuation captured,RootNativeHistory.PassiveTransitionObservation source){
        if(captured==null||captured.source!=source||captured.prepared||!source.normalCurrent()||!passiveCameraCommitCurrent(captured))return null;
        FarmCamera next=null;
        if(captured.before!=null){
            if(!source.normalScopeCurrent(captured.before.scope))return null;
            var scope=captured.before.scope.continueAfterPassive(source.actual());if(scope==null)return null;
            next=captured.service.new FarmCamera(captured.before,scope);
        }
        // All prospective source/declaration/Journey/native reads precede every private lifecycle assignment.
        if(!captured.maintenance.prepare())return null;
        for(var declaration:captured.declarations.values())if(!declaration.prepare())return null;
        if(captured.bridge!=null&&!captured.bridge.prepare())return null;
        if(!source.normalCurrent()||!passiveCameraCommitCurrent(captured)||!captured.slot.prepare(next,captured.service.farmCamera))return null;
        captured.next=next;captured.prepared=true;return captured;
    }
    /** Raw private-field identity checks only; no authority/provider or fresh native snapshot is obtained. */
    static boolean passiveCameraCommitCurrent(PassiveCameraContinuation captured){
        var s=captured.service;
        if(captured.closed||captured.committed||!captured.slot.originalCurrent(s.farmCamera)||s.farmPlanCall!=null||s.cameraMethodEpisode!=null
            ||s.requestGeneration!=captured.generation||s.requestEpoch!=captured.epoch||s.requestPublicationSerial!=captured.serial
            ||s.awaitingTicket!=captured.ticket||s.requestAuthority!=captured.authority||s.owner!=captured.player
            ||s.ownerLevel!=captured.world||s.requestConnection!=captured.listener||s.awaitingEndpoint!=captured.endpoint
            ||s.lastDirectPublication!=captured.direct||s.lastDirectCancellation!=captured.cancellation||s.request.pending()!=captured.pending
            ||s.queuedRootCameraDeclaration!=captured.declarationPointer||s.savedReturnQueue!=captured.savedPointer
            ||s.lastQueuedRequest!=captured.queuedPointer||s.retainedRootNativeCameraCall!=captured.callPointer
            ||captured.saved!=null&&captured.saved.lifecycle!=captured.savedLifecycle
            ||captured.queued!=null&&captured.queued.lifecycle!=captured.queuedLifecycle
            ||captured.call!=null&&captured.call.lifecycle!=captured.callLifecycle)return false;
        if(captured.maintenance!=null&&!captured.maintenance.rawCurrent())return false;
        for(var declaration:captured.declarations.values())if(!declaration.rawCurrent())return false;
        return captured.bridge==null||captured.bridge.rawCurrent();
    }
    static boolean commitPassiveCameraContinuation(PassiveCameraContinuation captured){
        if(!captured.prepared||!captured.source.commitWindowCurrent()||!passiveCameraCommitCurrent(captured)
                ||!captured.slot.claim(captured.service.farmCamera))return false;
        // Each commit performs raw identity checks only; no callback observes a partially carried chain.
        if(!captured.maintenance.commit())return false;
        for(var declaration:captured.declarations.values())if(!declaration.commit())return false;
        if(captured.bridge!=null&&!captured.bridge.commit())return false;
        var after=captured.source.actual().normalReturnedLifecycle();
        if(captured.saved!=null)captured.saved.lifecycle=after;
        if(captured.queued!=null)captured.queued.lifecycle=after;
        if(captured.call!=null)captured.call.lifecycle=after;
        captured.service.farmCamera=captured.slot.successor();captured.committed=true;return true;
    }
    static void closePassiveCameraContinuation(PassiveCameraContinuation captured){
        if(captured==null||captured.closed)return;captured.closed=true;
        if(captured.maintenance!=null)captured.maintenance.close();
    }
    private final class FarmPlanCall {
        final McFarmOwnedCleanup.NativeScope scope;boolean revoked;
        FarmPlanCall(McFarmOwnedCleanup.NativeScope scope){this.scope=scope;}
    }
    private FarmPlanCall farmPlanCall;
    private FarmPlanCall enterFarmPlan(McFarmOwnedCleanup.NativeScope scope) {
        var prior=farmPlanCall;if(prior!=null)prior.revoked=true;
        var call=new FarmPlanCall(scope);farmPlanCall=call;return call;
    }
    private void leaveFarmPlan(FarmPlanCall call){if(farmPlanCall==call)farmPlanCall=null;}
    /** Cleanup-only custody is retained immediately after this exact normally returned native plan. */
    private void rememberFarmNativeTicket(McFarmOwnedCleanup.NativeScope scope,long ticket,long generation,boolean queued) {
        if(scope!=null&&ticket>0&&requestGeneration==generation&&scope.activeCurrent()
            &&McInputService.canonicalFarmRotationCurrent(this,client)&&McOwnedRotation.publicationSerial(client)==ticket)
            farmCamera=new FarmCamera(scope,null,true,queued);
    }

    /** Original service metadata, bound once before the actual family hook. */
    final class ResumeRetirement {
        final dev.veltraph.client.farming.module.FarmModule farm;
        final FarmCamera originalFarm=farmCamera;
        final Object originalOwner=owner,originalWorld=ownerLevel,originalAuthority=requestAuthority,originalConnection=requestConnection;
        final Object originalEndpoint=awaitingEndpoint;
        final long originalTicket=awaitingTicket,originalEpoch=requestEpoch,originalSerial=requestPublicationSerial;
        final DirectPublicationReceipt originalDirect=lastDirectPublication;
        final DirectCancellationReceipt originalCancellation=lastDirectCancellation;
        final Object originalTransition=publicationTransition;
        final boolean originalPending=request.pending(),originalStepped=stepped;
        final long originalGeneration=requestGeneration;
        long epoch=McOwnedRotation.epoch(client),serial=McOwnedRotation.publicationSerial(client);
        final McOwnedRotation.ResumeEmptyObservation empty=McOwnedRotation.observeIssuedResumeEmpty(client,epoch,serial);
        private McOwnedRotation.ResumeCancellation actualNativeCancellation;
        boolean retired;
        ResumeRetirement(dev.veltraph.client.farming.module.FarmModule farm){this.farm=farm;}
        private boolean originalPositiveNative(){return originalFarm!=null&&originalFarm.nativePublished&&originalFarm.scope.farm==farm
            &&originalFarm.generation==originalGeneration&&originalFarm.epoch==epoch&&originalFarm.serial==serial&&serial>0;}
        boolean current(){
            if(!McInputService.canonicalFarmRotationCurrent(McRotationService.this,client)
                    ||McOwnedRotation.epoch(client)!=epoch||McOwnedRotation.publicationSerial(client)!=serial)return false;
            if(retired)return (empty!=null&&empty.current()||actualNativeCancellation!=null&&actualNativeCancellation.current())&&requestGeneration==originalGeneration+1&&!request.pending()&&farmCamera==null&&owner==null&&ownerLevel==null
                &&requestAuthority==null&&requestConnection==null&&awaitingEndpoint==null&&awaitingTicket==0
                &&lastDirectPublication==null&&lastDirectCancellation==null&&publicationTransition==null&&!stepped;
            return (originalPositiveNative()||empty!=null&&empty.current()||actualNativeCancellation!=null&&actualNativeCancellation.current())&&requestGeneration==originalGeneration&&farmCamera==originalFarm&&owner==originalOwner&&ownerLevel==originalWorld
                &&requestAuthority==originalAuthority&&requestConnection==originalConnection&&awaitingEndpoint==originalEndpoint
                &&awaitingTicket==originalTicket&&requestEpoch==originalEpoch&&requestPublicationSerial==originalSerial
                &&lastDirectPublication==originalDirect&&lastDirectCancellation==originalCancellation&&publicationTransition==originalTransition
                &&request.pending()==originalPending&&stepped==originalStepped
                &&(originalFarm==null||originalFarm.scope.farm==farm&&originalFarm.generation==originalGeneration)
                &&(originalDirect==null||originalDirect.after().parent()==farm)
                &&(!originalPending&&originalEndpoint==null||originalFarm!=null&&originalFarm.scope.farm==farm
                    &&originalFarm.generation==originalGeneration&&originalAuthority==farm);
        }
        boolean retire(java.util.function.BooleanSupplier source,java.util.function.BooleanSupplier header){
            if(retired)return current()&&source.getAsBoolean()&&current();
            if(originalGeneration==Long.MAX_VALUE||!current()||!source.getAsBoolean()||!current())return false;
            // Only a retained positive publication from this actual service owns native cancellation.
            if(originalPositiveNative()) {
                var actual=McOwnedRotation.cancelIssuedResumeOwned(client,farm,epoch,serial,source,header);
                if(actual==null||!actual.current())return false;
                actualNativeCancellation=actual; // Exact private own transition survives the following source callback.
                epoch=actual.afterEpoch(); // Private normal-return receipt, never raw current-epoch adoption.
            }
            if(!header.getAsBoolean()||!current())return false;
            requestGeneration=originalGeneration+1;lastDirectCancellation=null;lastDirectPublication=null;publicationTransition=null;
            request.cancel();awaitingEndpoint=null;awaitingTicket=0;requestAuthority=null;requestConnection=null;
            owner=null;ownerLevel=null;stepped=false;trackingYaw.reset();trackingPitch.reset();lastTrackTick=Integer.MIN_VALUE;farmCamera=null;
            retired=true;
            return current()&&source.getAsBoolean()&&current();
        }
    }
    ResumeRetirement captureIssuedResumeRetirement(dev.veltraph.client.farming.module.FarmModule farm){
        var actual=new ResumeRetirement(farm);return actual.current()?actual:null;
    }
    /** A frozen actual source-service request; callback replacements never become predecessors. */
    public final class IssuedReturnMetadata {
        private final dev.veltraph.client.rewarp.mc.McSavedAnchorRecovery.IssuedReturnCleanup source;
        private final long generation=requestGeneration,epoch=requestEpoch,serial=requestPublicationSerial,ticket=awaitingTicket;
        private final Object authority=requestAuthority,connection=requestConnection,player=owner,world=ownerLevel,endpoint=awaitingEndpoint;
        private final Object direct=lastDirectPublication,cancellation=lastDirectCancellation,transition=publicationTransition,sourceAim=lastSourceAimOutcome;
        private final boolean pending=request.pending(),wasStepped=stepped;
        private boolean retired;
        private IssuedReturnMetadata(dev.veltraph.client.rewarp.mc.McSavedAnchorRecovery.IssuedReturnCleanup source){this.source=source;}
        public boolean current(){
            if(!McInputService.canonicalFarmRotationCurrent(McRotationService.this,client))return false;
            if(retired)return requestGeneration==generation+1&&!request.pending()&&requestAuthority==null&&requestConnection==null
                &&owner==null&&ownerLevel==null&&awaitingEndpoint==null&&awaitingTicket==0&&lastDirectPublication==null&&lastDirectCancellation==null&&publicationTransition==null&&!stepped;
            return requestGeneration==generation&&requestEpoch==epoch&&requestPublicationSerial==serial&&awaitingTicket==ticket
                &&requestAuthority==authority&&requestConnection==connection&&owner==player&&ownerLevel==world&&awaitingEndpoint==endpoint
                &&lastDirectPublication==direct&&lastDirectCancellation==cancellation&&publicationTransition==transition&&lastSourceAimOutcome==sourceAim
                &&request.pending()==pending&&stepped==wasStepped;
        }
        public boolean retireLogical(java.util.function.BooleanSupplier oldSourceVeto){
            if(retired)return current();
            if(generation==Long.MAX_VALUE||!current()||!oldSourceVeto.getAsBoolean()||!current())return false;
            if(pending&&authority!=source.driverIdentity())return false;
            if(direct instanceof DirectPublicationReceipt actual&&actual.after().parent()!=source.driverIdentity())return false;
            requestGeneration=generation+1;lastDirectCancellation=null;lastDirectPublication=null;publicationTransition=null;
            request.cancel();awaitingEndpoint=null;awaitingTicket=0;requestAuthority=null;requestConnection=null;owner=null;ownerLevel=null;stepped=false;
            trackingYaw.reset();trackingPitch.reset();lastTrackTick=Integer.MIN_VALUE;farmCamera=null;retired=true;return current();
        }
        public boolean retireOwned(McOwnedRotation.IssuedReturnCameraWindow camera,java.util.function.BooleanSupplier before,java.util.function.BooleanSupplier nativeVeto){
            if(retired)return current()&&nativeVeto.getAsBoolean()&&current();
            if(generation==Long.MAX_VALUE||!current()||!before.getAsBoolean()||!current()||!camera.belongs(source))return false;
            if(pending&&authority!=source.driverIdentity())return false;
            if(direct instanceof DirectPublicationReceipt actual&&actual.after().parent()!=source.driverIdentity())return false;
            if(!camera.retireOwned(source,before,nativeVeto)||!current())return false;
            requestGeneration=generation+1;lastDirectCancellation=null;lastDirectPublication=null;publicationTransition=null;
            request.cancel();awaitingEndpoint=null;awaitingTicket=0;requestAuthority=null;requestConnection=null;
            owner=null;ownerLevel=null;stepped=false;trackingYaw.reset();trackingPitch.reset();lastTrackTick=Integer.MIN_VALUE;farmCamera=null;
            retired=true;return current()&&nativeVeto.getAsBoolean()&&current();
        }
    }
    public IssuedReturnMetadata captureIssuedReturnMetadata(dev.veltraph.client.rewarp.mc.McSavedAnchorRecovery.IssuedReturnCleanup source){
        if(source==null||source.sourceHeader().farmServicesIdentity().rotation()!=this)return null;
        var actual=new IssuedReturnMetadata(source);return actual.current()?actual:null;
    }
    private McFarmOwnedCleanup.NativeScope farmCameraScope() {
        if(!McInputService.canonicalFarmRotationCurrent(this,client))return null;
        var root=dev.veltraph.client.VeltraphBootstrap.moduleManager();
        var active=root==null?null:root.inputOwner().orElse(null);
        return active instanceof dev.veltraph.client.farming.module.FarmModule farm
            ?McFarmOwnedCleanup.NativeScope.capture(client,farm):null;
    }
    private void rememberFarmDirect(McFarmOwnedCleanup.NativeScope scope,DirectPublicationReceipt receipt) {
        if(scope!=null&&receipt!=null&&lastDirectPublication==receipt&&scope.activeCurrent()
            &&receipt.after().parent()==scope.farm&&receipt.current()&&scope.activeCurrent())
            farmCamera=new FarmCamera(scope,receipt,true,false);
    }
    /** An actual farm stop cannot erase another service request or camera publication. */
    private boolean interceptFarmStop() {
        if(!McFarmOwnedCleanup.stopping())return false;
        var exact=farmCamera;
        if(exact==null||exact.scope.farm!=McFarmOwnedCleanup.stoppingFarm()
            ||!exact.scope.cleanupProvidersCurrent()||!exact.storedCurrent())return true;
        // No providers occur after this original-scope leaf. Queue admission alone never owns a camera ticket.
        requestGeneration++;lastDirectCancellation=null;lastDirectPublication=null;publicationTransition=null;
        request.cancel();awaitingEndpoint=null;awaitingTicket=0;requestAuthority=null;requestConnection=null;
        if(exact.nativePublished)observeRootNativeCancel(()->McOwnedRotation.cancel(client));
        owner=null;ownerLevel=null;stepped=false;trackingYaw.reset();trackingPitch.reset();lastTrackTick=Integer.MIN_VALUE;
        farmCamera=null;return true;
    }
    @Override public void rotateTo(float yaw,float pitch,Runnable completed) {
        if(client.isSameThread())queuedRootCameraDeclaration=null;var beforeGeneration=requestGeneration;
        var episode=enterCameraMethod();if(episode==null){originalCameraEntry0(yaw,pitch,completed);return;}
        dev.veltraph.client.VeltraphBootstrap.RootOriginalCameraQueueSource source=null;
        try{if(episode.active()&&episode.original!=null)source=dev.veltraph.client.VeltraphBootstrap.captureOriginalCameraQueueSource(episode.original,this);}
        catch(RuntimeException|Error unavailable){episode.order.revoke();}
        try{originalCameraEntry0(yaw,pitch,completed);retainRootCameraDeclaration(source,episode,beforeGeneration,yaw,pitch,completed);episode.normal=true;}
        finally{episode.close();}
    }
    private void originalCameraEntry0(float yaw,float pitch,Runnable completed){
        if(!client.isSameThread()) throw new IllegalStateException("Rotation requests must run on the client thread");
        if(McFarmOwnedCleanup.stopping())return;
        var farmScope=farmCameraScope();
        McOwnedRotation.validate(client); // Revoke stale prior-publisher work before binding a fresh request epoch.
        owner=client.player;ownerLevel=client.level;requestConnection=client.getConnection();requestAuthority=McOwnedRotation.authority(client);
        awaitingEndpoint=null;awaitingTicket=0;requestEpoch=McOwnedRotation.epoch(client);requestPublicationSerial=McOwnedRotation.publicationSerial(client);requestGeneration++;lastDirectCancellation=null;lastDirectPublication=null;publicationTransition=null;request.replace(yaw,pitch,completed);
        if(farmScope!=null&&farmScope.activeCurrent()&&requestAuthority==farmScope.farm)farmCamera=new FarmCamera(farmScope,null,false,true);
    }
    private SavedReturnQueuedHandle savedReturnQueue;
    /** Exact captured-pose maintenance return; a raw token or Boolean cannot admit this queue. */
    public SavedReturnQueuedHandle rotateToSavedReturnOwned(float yaw,float pitch,Runnable completed,
            dev.veltraph.client.rewarp.mc.FarmMaintenanceCoordinator.SavedReturnAlignmentSource source){
        if(!client.isSameThread())throw new IllegalStateException("Rotation requests must run on the client thread");
        if(source==null||source.rotationServiceIdentity()!=this||source.target()==null
                ||Float.floatToIntBits(yaw)!=Float.floatToIntBits(source.target().yaw())
                ||Float.floatToIntBits(pitch)!=Float.floatToIntBits(source.target().pitch())
                ||!Float.isFinite(yaw)||!Float.isFinite(pitch)||Math.abs(pitch)>90)return null;
        var rootSource=dev.veltraph.client.VeltraphBootstrap.captureSavedReturnCameraQueueSource(source,this);
        if(rootSource==null)return null; // Authentic Root instruction declaration; no handle/current-owner fallback.
        long generation=requestGeneration,epoch=McOwnedRotation.epoch(client),serial=McOwnedRotation.publicationSerial(client);
        var root=dev.veltraph.client.VeltraphBootstrap.moduleManager();var lifecycle=root==null?null:root.lifecycleSnapshot();
        var installation=McOwnedRotation.installationSnapshot(client);var settings=storedRotationSnapshot();
        var input=dev.veltraph.client.input.ScriptingInputLock.controlSnapshot();
        if(root==null||source.registryIdentity()!=root||lifecycle==null||!lifecycle.known()||installation==null||settings==null
                ||input==null||!input.active()||generation>=Long.MAX_VALUE-1||epoch<0||serial<0
                ||!java.util.Objects.equals(input.ownerModuleId(),source.inputOwner().id())||!source.current())return null;
        var candidate=new SavedReturnQueuedHandle(source,rootSource,root,lifecycle,installation,settings,input,generation,epoch,serial);
        var admission=McOwnedRotation.observeQueuedAdmission(client,new dev.veltraph.client.rotation.OwnedRotationTrajectory.Scope(
                source.cameraOwner(),candidate.player,candidate.world,candidate.connection));
        if(admission==null||!finalWorkflowAllowed(source::currentFinalNative,
                ()->candidate.admissionCurrent(generation,epoch,serial)&&admission.nativeCurrent()))return null;
        // No providers follow this exact private-source/native leaf before the real replacement.
        owner=candidate.player;ownerLevel=candidate.world;requestConnection=candidate.connection;requestAuthority=source.cameraOwner();
        awaitingEndpoint=null;awaitingTicket=0;requestEpoch=epoch;requestPublicationSerial=serial;requestGeneration++;
        lastDirectCancellation=null;lastDirectPublication=null;publicationTransition=null;farmCamera=null;
        candidate.generation=requestGeneration;savedReturnQueue=candidate;
        request.replace(yaw,pitch,()->{if(candidate.current()&&!candidate.completed){candidate.completed=true;if(completed!=null)completed.run();}});
        return candidate;
    }
    public final class SavedReturnQueuedHandle {
        private final McRotationService issuingService=McRotationService.this;
        private final dev.veltraph.client.VeltraphBootstrap.RootSavedReturnCameraQueueSource rootSource;
        private final dev.veltraph.client.rewarp.mc.FarmMaintenanceCoordinator.SavedReturnAlignmentSource source;
        private final dev.veltraph.client.core.ModuleManager root;private dev.veltraph.client.core.ModuleManager.LifecycleSnapshot lifecycle;
        private final McOwnedRotation.InstallationSnapshot installation;
        private final dev.veltraph.client.config.MapConfigService.StoredDoubleSnapshot settings;
        private final dev.veltraph.client.input.ScriptingInputLock.ControlSnapshot input;
        private final LocalPlayer player;private final net.minecraft.client.multiplayer.ClientLevel world;
        private final net.minecraft.client.multiplayer.ClientPacketListener connection;
        private long generation,epoch,serial;private McOwnedRotation.QueuedScopedPlan plan;
        private SavedReturnPublicationFrame publicationFrame;
        private final class SavedReturnPublicationFrame {
            final dev.veltraph.client.VeltraphBootstrap.RootSavedReturnCameraAdvanceInvocation original;
            McOwnedRotation.QueuedScopedPlan returned;
            SavedReturnPublicationFrame(dev.veltraph.client.VeltraphBootstrap.RootSavedReturnCameraAdvanceInvocation original){this.original=original;}
            boolean current(){return original!=null&&original.sourceIdentity()==rootSource
                &&(original.issuingCurrent()||returned!=null&&returned==plan&&original.publicationCurrent()&&returned.nativeCurrent());}
        }
        private boolean completed,cancelled,revoked;
        private SavedReturnQueuedHandle(dev.veltraph.client.rewarp.mc.FarmMaintenanceCoordinator.SavedReturnAlignmentSource source,
                dev.veltraph.client.VeltraphBootstrap.RootSavedReturnCameraQueueSource rootSource,dev.veltraph.client.core.ModuleManager root,dev.veltraph.client.core.ModuleManager.LifecycleSnapshot lifecycle,
                McOwnedRotation.InstallationSnapshot installation,dev.veltraph.client.config.MapConfigService.StoredDoubleSnapshot settings,
                dev.veltraph.client.input.ScriptingInputLock.ControlSnapshot input,long generation,long epoch,long serial){
            this.source=source;this.rootSource=rootSource;this.root=root;this.lifecycle=lifecycle;this.installation=installation;this.settings=settings;this.input=input;
            this.generation=generation;this.epoch=epoch;this.serial=serial;player=client.player;world=client.level;connection=client.getConnection();
        }
        private boolean serviceCurrent(long expectedGeneration){return client.isSameThread()&&installation.current()
            &&root==dev.veltraph.client.VeltraphBootstrap.moduleManager()&&config==dev.veltraph.client.VeltraphBootstrap.config()
            &&settings.current()&&input.current()&&client.player==player&&client.level==world&&client.getConnection()==connection
            &&requestGeneration==expectedGeneration&&player!=null&&world!=null&&connection!=null&&player.level()==world;}
        private boolean admissionCurrent(long g,long e,long s){return source.currentFinalNative()&&root.lifecycleCurrent(lifecycle)
            &&serviceCurrent(g)&&McOwnedRotation.epoch(client)==e&&McOwnedRotation.publicationSerial(client)==s
            &&player.isAlive()&&!player.isPassenger()&&!player.isScoping()&&!client.isPaused()&&client.gui.screen()==null
            &&client.getCameraEntity()==player&&!world.tickRateManager().isEntityFrozen(player)
            &&player.containerMenu==player.inventoryMenu&&player.inventoryMenu.getCarried().isEmpty();}
        private boolean metadataCurrent(){return savedReturnQueue==this&&!cancelled&&!revoked&&serviceCurrent(generation)
            &&owner==player&&ownerLevel==world&&requestConnection==connection&&requestAuthority==source.cameraOwner()
            &&requestEpoch==epoch&&requestPublicationSerial==serial;}
        private boolean nativeCurrent(){return metadataCurrent()&&admissionCurrent(generation,epoch,serial)
            &&rootSource.originalDeclarationCurrent()&&(plan==null||plan.nativeCurrent()&&publicationFrame!=null&&publicationFrame.current())&&source.currentFinalNative();}
        /** Remains valid after endpoint completion throughout original settling, until explicit retirement. */
        public boolean current(){return source.current()&&metadataCurrent()&&admissionCurrent(generation,epoch,serial)
            &&(plan==null||rootSource.originalDeclarationCurrent()&&plan.nativeCurrent()&&publicationFrame!=null&&publicationFrame.current())&&source.currentFinalNative();}
        public boolean completed(){return completed&&current();}
        public boolean nativeTicketPublished(){return plan!=null&&plan.ticket()>0&&plan==this.plan&&metadataCurrent();}
        private boolean cleanupMetadataCurrent(){return savedReturnQueue==this&&!cancelled&&!revoked&&serviceCurrent(generation)
            &&owner==player&&ownerLevel==world&&requestConnection==connection&&requestAuthority==source.cameraOwner()
            &&requestEpoch==epoch&&requestPublicationSerial==serial&&source.cleanupFinalNative();}
        private boolean cleanupNativeCurrent(long nativeEpoch){return cleanupMetadataCurrent()
            &&McOwnedRotation.epoch(client)==nativeEpoch&&McOwnedRotation.publicationSerial(client)==serial;}
    }
    public final class SavedReturnQueueRetirement {
        public enum Status { NATIVE_CANCELLED, METADATA_ONLY, REFUSED }
        private final SavedReturnQueuedHandle original;private final Status status;private final long afterGeneration;
        private final McOwnedRotation.QueuedCancellation nativeCancellation;
        private SavedReturnQueueRetirement(SavedReturnQueuedHandle original,Status status,long afterGeneration,
                McOwnedRotation.QueuedCancellation nativeCancellation){this.original=original;this.status=status;this.afterGeneration=afterGeneration;this.nativeCancellation=nativeCancellation;}
        public Status status(){return status;}
        public boolean current(){return status!=Status.REFUSED&&original!=null&&original.cancelled&&requestGeneration==afterGeneration
            &&savedReturnQueue!=original&&!request.pending()&&requestAuthority==null&&requestConnection==null&&owner==null&&ownerLevel==null
            &&awaitingEndpoint==null&&awaitingTicket==0&&original.source.cleanupFinalNative()
            &&(status==Status.METADATA_ONLY||nativeCancellation!=null&&nativeCancellation.cleanupNativeCurrent());}
    }
    private SavedReturnQueueRetirement refuseSavedReturn(SavedReturnQueuedHandle exact){
        if(exact!=null&&exact.issuingService==this&&client.isSameThread())exact.revoked=true; // The retired caller never needs a retry to prevent old queue publication.
        return new SavedReturnQueueRetirement(exact,SavedReturnQueueRetirement.Status.REFUSED,requestGeneration,null);
    }
    public SavedReturnQueueRetirement cancelSavedReturnOwned(SavedReturnQueuedHandle exact){
        var episode=enterCameraMethod();
        if(episode==null)return originalSavedReturnCancelEntry(exact);
        try{var result=originalSavedReturnCancelEntry(exact);episode.normal=true;return result;}
        finally{episode.close();}
    }
    private SavedReturnQueueRetirement originalSavedReturnCancelEntry(SavedReturnQueuedHandle exact){
        if(exact==null||exact.issuingService!=this||!client.isSameThread()||savedReturnQueue!=exact||exact.cancelled||exact.revoked||!exact.source.cleanupCurrent()
                ||!exact.cleanupMetadataCurrent()||exact.generation==Long.MAX_VALUE)return refuseSavedReturn(exact);
        var authority=McOwnedRotation.authority(client); // Provider before the last source/native checks.
        McOwnedRotation.QueuedCancellation cancellation=null;
        boolean ownsNative=exact.plan!=null&&authority==exact.source.cleanupCameraOwner()
            &&exact.cleanupNativeCurrent(exact.epoch)&&exact.plan.cleanupNativeCurrent();
        if(ownsNative){
            var frame=new SavedReturnCancellationFrame(exact);
            cancellation=observeSavedReturnNativeCancel(frame,()->McOwnedRotation.cancelQueuedScoped(frame.plan,exact.source::cleanupFinalNative,
                    ()->exact.cleanupNativeCurrent(frame.epoch)&&frame.current()));
            if(cancellation==null||cancellation.original()!=exact.plan||cancellation.beforeEpoch()!=exact.epoch
                    ||cancellation.publicationSerial()!=exact.serial||!cancellation.cleanupNativeCurrent()
                    ||!exact.cleanupNativeCurrent(cancellation.afterEpoch()))return refuseSavedReturn(exact);
        }else if(!exact.source.closedForMetadataRetirement()&&!exact.cleanupMetadataCurrent())return refuseSavedReturn(exact);
        if(!exact.cleanupMetadataCurrent()||savedReturnQueue!=exact||requestGeneration!=exact.generation)return refuseSavedReturn(exact);
        // Only this old request's metadata retires. A foreign native plan is never cancelled here.
        requestGeneration++;lastDirectCancellation=null;lastDirectPublication=null;publicationTransition=null;
        request.cancel();awaitingEndpoint=null;awaitingTicket=0;requestAuthority=null;requestConnection=null;owner=null;ownerLevel=null;
        stepped=false;trackingYaw.reset();trackingPitch.reset();lastTrackTick=Integer.MIN_VALUE;
        exact.cancelled=true;savedReturnQueue=null;
        return new SavedReturnQueueRetirement(exact,cancellation==null?SavedReturnQueueRetirement.Status.METADATA_ONLY:
            SavedReturnQueueRetirement.Status.NATIVE_CANCELLED,requestGeneration,cancellation);
    }
    private void advanceSavedReturnQueue(SavedReturnQueuedHandle exact,
            dev.veltraph.client.VeltraphBootstrap.RootSavedReturnCameraAdvanceInvocation originalAdvance){
        if(originalAdvance==null||originalAdvance.sourceIdentity()!=exact.rootSource||!originalAdvance.issuingCurrent()
            ||!request.pending()||exact.revoked||!exact.current())return;
        Object authority=McOwnedRotation.authority(client);
        if(authority!=exact.source.cameraOwner()||!exact.current())return;
        var player=exact.player;
        if(awaitingEndpoint!=null&&exact.plan!=null&&exact.plan.completed()
            &&Math.abs(dev.veltraph.client.rotation.SourceRotationStepper.wrap(player.getYRot()-awaitingEndpoint.yaw()))<.001
            &&Math.abs(player.getXRot()-awaitingEndpoint.pitch())<.001&&exact.current()){
            var endpoint=awaitingEndpoint;awaitingEndpoint=null;awaitingTicket=0;request.applied(endpoint);
        }
        if(savedReturnQueue!=exact||requestGeneration!=exact.generation||!request.pending()||exact.revoked)return;
        double speed=config.get(dev.veltraph.client.config.RotationConfig.ROTATION_SPEED_MULTIPLIER);
        double sensitivity=client.options.sensitivity().get();if(!Double.isFinite(speed)||speed<=0||!exact.current())return;
        var result=request.advance(player.getYRot(),player.getXRot(),dev.veltraph.client.rotation.SourceRotationStepper.Settings.standard(sensitivity,speed),true,player.isScoping());
        if(result.rejected())return;long oldEpoch=exact.epoch,oldSerial=exact.serial;var before=publicationStamp(oldEpoch);
        if(!originalAdvance.issuingCurrent()||!exact.nativeCurrent())return;
        var frame=exact.new SavedReturnPublicationFrame(originalAdvance);
        var actual=observeSavedReturnNativePlan(exact,frame,result.yaw(),result.pitch(),()->McOwnedRotation.planQueuedScoped(client,result.yaw(),result.pitch(),
            new dev.veltraph.client.rotation.OwnedRotationTrajectory.Scope(exact.source.cameraOwner(),player,exact.world,exact.connection),
            ()->exact.nativeCurrent()&&frame.current(),()->exact.source.currentFinalNative()&&frame.current()));
        if(actual==null||savedReturnQueue!=exact||requestGeneration!=exact.generation||exact.epoch!=oldEpoch||exact.serial!=oldSerial
            ||!originalAdvance.issuingCurrent())return;
        var after=publicationStamp(actual.epoch());var transition=new dev.veltraph.client.rotation.RotationPublicationIdentity.Transition(before,after,actual.ticket());
        if(!actual.nativeCurrent()||!dev.veltraph.client.rotation.RotationPublicationIdentity.valid(transition)||actual.ticket()<=oldSerial
                ||!exact.source.currentFinalNative())return;
        exact.epoch=actual.epoch();exact.serial=actual.ticket();exact.plan=actual;frame.returned=actual;exact.publicationFrame=frame;
        requestEpoch=exact.epoch;requestPublicationSerial=exact.serial;publicationTransition=transition;
        if(result.complete()){awaitingEndpoint=result;awaitingTicket=actual.ticket();}
    }

    private QueuedRequestHandle lastQueuedRequest;
    /**
     * Additive maintenance queue. The private workflow can refuse; the issued Binding, genuine Root
     * installation and source snapshots independently authorize the actual request replacement.
     */
    public QueuedRequestHandle rotateToMaintenanceOwned(float yaw,float pitch,Runnable completed,
            dev.veltraph.client.navigation.mc.McFarmMaintenanceAuthorityBridge.Binding binding,FinalWorkflowLeaf workflow){
        java.util.Objects.requireNonNull(binding);java.util.Objects.requireNonNull(workflow);
        if(!client.isSameThread())throw new IllegalStateException("Rotation requests must run on the client thread");
        if(!Float.isFinite(yaw)||!Float.isFinite(pitch)||Math.abs(pitch)>90)throw new IllegalArgumentException("Invalid target");
        long generation=requestGeneration,epoch=McOwnedRotation.epoch(client),serial=McOwnedRotation.publicationSerial(client);
        var root=dev.veltraph.client.VeltraphBootstrap.moduleManager();var lifecycle=root==null?null:root.lifecycleSnapshot();
        var installation=McOwnedRotation.installationSnapshot(client);var settings=storedRotationSnapshot();
        var input=dev.veltraph.client.input.ScriptingInputLock.controlSnapshot();
        if(root==null||lifecycle==null||!lifecycle.known()||installation==null||settings==null||input==null||!input.active()
            ||generation>=Long.MAX_VALUE-1||epoch<0||serial<0||!java.util.Objects.equals(input.ownerModuleId(),binding.inputOwner().id()))return null;
        Object source=dev.veltraph.client.navigation.mc.McFarmMaintenanceAuthorityBridge.queuedRotationSourceSnapshot(binding,client,config,this);
        if(source==null)return null;
        var candidate=new QueuedRequestHandle(binding,workflow,root,lifecycle,installation,settings,input,source,generation,epoch,serial);
        // Read-only native validation preserves a predecessor or a replacement published by reentry.
        var admission=McOwnedRotation.observeQueuedAdmission(client,new dev.veltraph.client.rotation.OwnedRotationTrajectory.Scope(binding.cameraOwner(),candidate.player,candidate.world,candidate.connection));
        if(admission==null||!finalWorkflowAllowed(workflow,()->candidate.admissionNativeCurrent(generation,epoch,serial)&&admission.nativeCurrent()))return null;
        // No callback occurs between the final source/native leaf and the actual source request replacement.
        owner=client.player;ownerLevel=client.level;requestConnection=client.getConnection();requestAuthority=binding.cameraOwner();
        awaitingEndpoint=null;awaitingTicket=0;requestEpoch=epoch;requestPublicationSerial=serial;requestGeneration++;
        lastDirectCancellation=null;lastDirectPublication=null;publicationTransition=null;
        candidate.generation=requestGeneration;lastQueuedRequest=candidate;
        request.replace(yaw,pitch,()->{
            if(finalWorkflowAllowed(workflow,candidate::nativeCurrent)&&!candidate.completed){candidate.completed=true;if(completed!=null)completed.run();}
        });
        return candidate;
    }
    /** Genuine actual queue handle; constructors and own endpoint adoption remain private. */
    public final class QueuedRequestHandle {
        private final dev.veltraph.client.navigation.mc.McFarmMaintenanceAuthorityBridge.Binding binding;
        private final FinalWorkflowLeaf workflow;private final dev.veltraph.client.core.ModuleManager root;
        private dev.veltraph.client.core.ModuleManager.LifecycleSnapshot lifecycle;
        private final McOwnedRotation.InstallationSnapshot installation;
        private final dev.veltraph.client.config.MapConfigService.StoredDoubleSnapshot settings;
        private final dev.veltraph.client.input.ScriptingInputLock.ControlSnapshot input;
        private final Object source;private final LocalPlayer player;private final net.minecraft.client.multiplayer.ClientLevel world;
        private final net.minecraft.client.multiplayer.ClientPacketListener connection;
        private long generation,epoch,serial;private McOwnedRotation.QueuedScopedPlan plan;private boolean completed,cancelled;
        private QueuedRequestHandle(dev.veltraph.client.navigation.mc.McFarmMaintenanceAuthorityBridge.Binding binding,FinalWorkflowLeaf workflow,
                dev.veltraph.client.core.ModuleManager root,dev.veltraph.client.core.ModuleManager.LifecycleSnapshot lifecycle,
                McOwnedRotation.InstallationSnapshot installation,dev.veltraph.client.config.MapConfigService.StoredDoubleSnapshot settings,
                dev.veltraph.client.input.ScriptingInputLock.ControlSnapshot input,Object source,long generation,long epoch,long serial){
            this.binding=binding;this.workflow=workflow;this.root=root;this.lifecycle=lifecycle;this.installation=installation;this.settings=settings;this.input=input;this.source=source;
            this.generation=generation;this.epoch=epoch;this.serial=serial;player=client.player;world=client.level;connection=client.getConnection();
        }
        private boolean storedServiceIdentity(long expectedGeneration){
            return client.isSameThread()&&installation.current()&&dev.veltraph.client.VeltraphBootstrap.moduleManager()==root
                &&dev.veltraph.client.VeltraphBootstrap.config()==config&&input.current()&&input.active()
                &&client.player==player&&client.level==world&&client.getConnection()==connection&&player!=null&&world!=null&&connection!=null
                &&player.level()==world&&client.getCameraEntity()==player&&requestGeneration==expectedGeneration;
        }
        private boolean storedNativeIdentity(long expectedGeneration,long expectedEpoch,long expectedSerial){
            return storedServiceIdentity(expectedGeneration)&&McOwnedRotation.epoch(client)==expectedEpoch&&McOwnedRotation.publicationSerial(client)==expectedSerial;
        }
        private boolean healthy(){return player.isAlive()&&!player.isPassenger()&&!player.isScoping()&&!client.isPaused()&&client.gui.screen()==null
            &&!world.tickRateManager().isEntityFrozen(player)&&player.containerMenu==player.inventoryMenu&&player.inventoryMenu.getCarried().isEmpty();}
        private boolean admissionNativeCurrent(long expectedGeneration,long expectedEpoch,long expectedSerial){
            return settings.current()&&root.lifecycleCurrent(lifecycle)
                &&dev.veltraph.client.navigation.mc.McFarmMaintenanceAuthorityBridge.queuedRotationSourceCurrent(binding,client,config,McRotationService.this,source)
                &&storedNativeIdentity(expectedGeneration,expectedEpoch,expectedSerial)&&healthy();
        }
        /** Callback-free final check of the original queue and its own genuine native endpoint. */
        public boolean nativeCurrent(){return lastQueuedRequest==this&&!cancelled&&admissionNativeCurrent(generation,epoch,serial)
            &&owner==player&&ownerLevel==world&&requestConnection==connection&&requestAuthority==binding.cameraOwner()
            &&requestEpoch==epoch&&requestPublicationSerial==serial&&(plan==null||plan.nativeCurrent());}
        public boolean current(){return finalWorkflowAllowed(workflow,this::nativeCurrent);}
        public boolean completed(){return completed&&current();}
        public boolean sourceBusy(){return current()&&request.sourceBusy(player.getYRot(),player.getXRot())&&nativeCurrent();}
        /** Genuine existing service handout, derived from this admitted original request alone. */
        public CleanupReceipt cleanupSource(){return lastQueuedRequest==this&&!cancelled&&plan!=null&&plan.cleanupNativeCurrent()&&requestGeneration==generation
            &&requestEpoch==epoch&&requestPublicationSerial==serial&&requestAuthority==binding.cameraOwner()?cleanupReceipt(binding.cameraOwner()):null;}
        private boolean cleanupMetadataCurrent(Object cleanup,long expectedGeneration,long expectedEpoch,long expectedSerial){
            return lastQueuedRequest==this&&!cancelled&&root.ownCleanupLifecycleCurrent(lifecycle,binding.inputOwner())
                &&dev.veltraph.client.navigation.mc.McFarmMaintenanceAuthorityBridge.queuedRotationCleanupCurrent(binding,client,config,McRotationService.this,source,cleanup)
                &&storedServiceIdentity(expectedGeneration)
                &&owner==player&&ownerLevel==world&&requestConnection==connection&&requestAuthority==binding.cameraOwner()
                &&requestEpoch==expectedEpoch&&requestPublicationSerial==expectedSerial;
        }
        private boolean cleanupNativeCurrent(Object cleanup,long expectedGeneration,long expectedEpoch,long expectedSerial,long nativeEpoch){
            return cleanupMetadataCurrent(cleanup,expectedGeneration,expectedEpoch,expectedSerial)
                &&McOwnedRotation.epoch(client)==nativeEpoch&&McOwnedRotation.publicationSerial(client)==expectedSerial;
        }
    }
    /** Actual owned cleanup; source history cannot authorize another request or a foreign native ticket. */
    public boolean cancelOwnedHandle(QueuedRequestHandle exact,FinalWorkflowLeaf cleanupWorkflow){
        java.util.Objects.requireNonNull(cleanupWorkflow);
        if(exact==null||lastQueuedRequest!=exact||exact.cancelled||!client.isSameThread())return false;
        long generation=exact.generation,epoch=exact.epoch,serial=exact.serial;
        Object cleanup=dev.veltraph.client.navigation.mc.McFarmMaintenanceAuthorityBridge.queuedRotationCleanupSnapshot(exact.binding,client,config,this,exact.source);
        if(cleanup==null)return false;
        if(exact.plan!=null&&McOwnedRotation.authority(client)!=dev.veltraph.client.navigation.mc.McFarmMaintenanceAuthorityBridge.queuedRotationCleanupCamera(cleanup))return false;
        if(!finalWorkflowAllowed(cleanupWorkflow,()->exact.plan==null?exact.cleanupMetadataCurrent(cleanup,generation,epoch,serial):exact.cleanupNativeCurrent(cleanup,generation,epoch,serial,epoch)))return false;
        if(exact.plan!=null){
            // Native cancellation additionally requires the original privately minted own endpoint.
            var nativeCancellation=McOwnedRotation.cancelQueuedScoped(exact.plan,cleanupWorkflow,
                ()->exact.cleanupNativeCurrent(cleanup,generation,epoch,serial,epoch));
            if(nativeCancellation==null||nativeCancellation.original()!=exact.plan||nativeCancellation.beforeEpoch()!=epoch
                ||nativeCancellation.publicationSerial()!=serial
                ||!finalWorkflowAllowed(cleanupWorkflow,()->exact.cleanupNativeCurrent(cleanup,generation,epoch,serial,nativeCancellation.afterEpoch()))
                ||!nativeCancellation.cleanupNativeCurrent())return false;
        }
        // Admission-only custody clears metadata without touching a predecessor's native plan.
        // Own native cancellation, when present, has already returned its actual typed proof.
        requestGeneration++;lastDirectCancellation=null;lastDirectPublication=null;publicationTransition=null;
        request.cancel();awaitingEndpoint=null;awaitingTicket=0;requestAuthority=null;requestConnection=null;owner=null;ownerLevel=null;
        stepped=false;trackingYaw.reset();trackingPitch.reset();lastTrackTick=Integer.MIN_VALUE;
        exact.cancelled=true;lastQueuedRequest=null;return true;
    }
    private void advanceOwnedQueue(QueuedRequestHandle exact){
        if(!request.pending())return;
        Object actualAuthority=McOwnedRotation.authority(client);
        if(actualAuthority!=exact.binding.cameraOwner()||!exact.current())return;
        var p=exact.player;
        if(awaitingEndpoint!=null&&exact.plan!=null&&exact.plan.completed()
            &&Math.abs(dev.veltraph.client.rotation.SourceRotationStepper.wrap(p.getYRot()-awaitingEndpoint.yaw()))<.001
            &&Math.abs(p.getXRot()-awaitingEndpoint.pitch())<.001&&exact.current()){
            var endpoint=awaitingEndpoint;awaitingEndpoint=null;awaitingTicket=0;request.applied(endpoint);
        }
        // A completion callback may replace the request. Never step that replacement through this old handle.
        if(lastQueuedRequest!=exact||requestGeneration!=exact.generation||!request.pending())return;
        double speed=config.get(dev.veltraph.client.config.RotationConfig.ROTATION_SPEED_MULTIPLIER);
        double sensitivity=client.options.sensitivity().get();
        if(!Double.isFinite(speed)||speed<=0||!exact.current())return;
        var result=request.advance(p.getYRot(),p.getXRot(),dev.veltraph.client.rotation.SourceRotationStepper.Settings.standard(sensitivity,speed),true,p.isScoping());
        if(result.rejected())return;
        long beforeEpoch=exact.epoch,beforeSerial=exact.serial;
        var before=publicationStamp(beforeEpoch);
        var actual=McOwnedRotation.planQueuedScoped(client,result.yaw(),result.pitch(),
            new dev.veltraph.client.rotation.OwnedRotationTrajectory.Scope(exact.binding.cameraOwner(),p,exact.world,exact.connection),exact::nativeCurrent,exact.workflow);
        if(actual==null||lastQueuedRequest!=exact||requestGeneration!=exact.generation||exact.epoch!=beforeEpoch||exact.serial!=beforeSerial)return;
        var after=publicationStamp(actual.epoch());
        var transition=new dev.veltraph.client.rotation.RotationPublicationIdentity.Transition(before,after,actual.ticket());
        // Only this exact private native producer can advance the original source request's native custody.
        if(!actual.nativeCurrent()||!dev.veltraph.client.rotation.RotationPublicationIdentity.valid(transition)||actual.ticket()<=beforeSerial)return;
        exact.epoch=actual.epoch();exact.serial=actual.ticket();exact.plan=actual;
        requestEpoch=exact.epoch;requestPublicationSerial=exact.serial;publicationTransition=transition;
        if(result.complete()){awaitingEndpoint=result;awaitingTicket=actual.ticket();}
    }

    @Override public void track(float yaw,float pitch) {
        if(client.isSameThread())queuedRootCameraDeclaration=null;var episode=enterCameraMethod();if(episode==null){originalCameraEntry1(yaw,pitch);return;}try{originalCameraEntry1(yaw,pitch);episode.normal=true;}finally{episode.close();}
    }
    private void originalCameraEntry1(float yaw,float pitch){
        var invokedFarm=McFarmOwnedCleanup.farmingTickFarm();
        if(invokedFarm!=null&&McFarmOwnedCleanup.NativeScope.capture(client,invokedFarm)==null)return;
        if(McFarmOwnedCleanup.stopping())return;
        var scope=farmCameraScope();var call=enterFarmPlan(scope);
        try{var receipt=trackInternal(yaw,pitch,null,false);rememberFarmDirect(scope,receipt);}finally{leaveFarmPlan(call);}
    }
    /** One genuine private Journey ALIGN call; its final source leaf is refusal-only. */
    public DirectPublicationReceipt trackIssuedReturnView(
            dev.veltraph.client.rewarp.mc.McSavedAnchorRecovery.IssuedReturnTravelRequest source,
            Object originalInstruction,float yaw,float pitch){
        if(source==null||originalInstruction==null||!client.isSameThread()
                ||source.sourceHeader().farmServicesIdentity().rotation()!=this
                ||!source.claimOriginalCameraDispatch(this,originalInstruction))return null;
        // Claim is sealed before any Root/role/configuration provider in the original scoped path.
        // The exact private instruction, not a global selected Core view, survives those callbacks.
        return trackWithReceipt(yaw,pitch,
                ()->source.current()&&source.publicationSourceSnapshotCurrent(originalInstruction),
                ()->source.publicationSourceSnapshotCurrent(originalInstruction));
    }
    @Override public Object trackWithFinalNativeVeto(float yaw,float pitch,java.util.function.BooleanSupplier finalNativeVeto){
        java.util.Objects.requireNonNull(finalNativeVeto);
        return trackWithReceipt(yaw,pitch,finalNativeVeto,finalNativeVeto::getAsBoolean);
    }
    /** The supplied fence can refuse a publication; native Root identity is independently required. */
    public DirectPublicationReceipt trackWithReceipt(float yaw,float pitch,java.util.function.BooleanSupplier publicationFence){
        if(client.isSameThread())queuedRootCameraDeclaration=null;var episode=enterCameraMethod();if(episode==null){return originalCameraEntry2(yaw,pitch,publicationFence);}try{var result=originalCameraEntry2(yaw,pitch,publicationFence);episode.normal=true;return result;}finally{episode.close();}
    }
    private DirectPublicationReceipt originalCameraEntry2(float yaw,float pitch,java.util.function.BooleanSupplier publicationFence){
        return trackInternal(yaw,pitch,java.util.Objects.requireNonNull(publicationFence),true);
    }
    public DirectPublicationReceipt trackWithReceipt(float yaw,float pitch){return trackWithReceipt(yaw,pitch,()->true);}
    private DirectPublicationReceipt trackInternal(float yaw,float pitch,java.util.function.BooleanSupplier fence,boolean required){
        if(!client.isSameThread())throw new IllegalStateException("Rotation requests must run on the client thread");
        if(!Float.isFinite(yaw)||!Float.isFinite(pitch)||Math.abs(pitch)>90)throw new IllegalArgumentException("Invalid target");
        long entryGeneration=requestGeneration;var before=directScope();
        if(requestGeneration!=entryGeneration||before!=null&&before.generation()!=entryGeneration)return null;
        var prior=lastDirectPublication;lastDirectPublication=null;
        var p=client.player;if(p==null||!p.isAlive()||client.level==null||client.gui.screen()!=null){cancelRotation();return null;}
        if(p.isScoping()||required&&before==null)return null;
        if(fence!=null&&!fence.getAsBoolean())return null;
        if(before!=null&&!dev.veltraph.client.rotation.DirectRotationPublicationIdentity.same(before,directScope()))return null;
        if(p!=owner||client.level!=ownerLevel||p.tickCount-lastTrackTick>2){trackingYaw.reset();trackingPitch.reset();lastTrackTick=Integer.MIN_VALUE;}
        owner=p;ownerLevel=client.level;requestGeneration++;lastDirectCancellation=null;publicationTransition=null;request.cancel();awaitingEndpoint=null;awaitingTicket=0;requestAuthority=null;requestConnection=null;
        if(lastTrackTick==p.tickCount){
            if(prior!=null&&before!=null&&(fence==null||fence.getAsBoolean())&&dev.veltraph.client.rotation.DirectRotationPublicationIdentity.current(prior.proof,before)){
                var after=directScope();var proof=new dev.veltraph.client.rotation.DirectRotationPublicationIdentity.Proof(before,after,prior.actualTicket(),dev.veltraph.client.rotation.DirectRotationPublicationIdentity.Mode.SAME_TICK_TRACK);
                if(dev.veltraph.client.rotation.DirectRotationPublicationIdentity.retained(proof,prior.proof)){
                    var retained=new DirectPublicationReceipt(proof,prior.yaw,prior.pitch,false);lastDirectPublication=retained;return retained;
                }
            }
            return null;
        }
        lastTrackTick=p.tickCount;
        double speed=config.get(dev.veltraph.client.config.RotationConfig.ROTATION_SPEED_MULTIPLIER);
        float nextYaw=trackingYaw.step(p.getYRot(),yaw,speed),nextPitch=trackingPitch.stepLinear(p.getXRot(),pitch,speed);
        // Reentrant config/heading callbacks cannot let this old call cancel a newer queued request.
        if(before!=null&&!dev.veltraph.client.rotation.DirectRotationPublicationIdentity.preplan(before,directScope(),dev.veltraph.client.rotation.DirectRotationPublicationIdentity.Mode.AIM))return null;
        var result=aimInternal(nextYaw,nextPitch,fence,required,before,dev.veltraph.client.rotation.DirectRotationPublicationIdentity.Mode.TRACK);
        if(before==null||result!=null)stepped=true;return result;
    }
    /** Refusal-only leaf implemented by a caller-private immutable workflow snapshot; never Root authority. */
    @FunctionalInterface public interface FinalWorkflowLeaf { boolean current(); }
    /** Ordering helper only: true here cannot mint a native ticket or receipt. */
    public static boolean finalWorkflowAllowed(FinalWorkflowLeaf workflow,java.util.function.BooleanSupplier nativeLeaf){
        java.util.Objects.requireNonNull(workflow);java.util.Objects.requireNonNull(nativeLeaf);
        return workflow.current()&&nativeLeaf.getAsBoolean();
    }
    /** Additive workflow-bound path; the final veto must read only the caller's private workflow snapshot. */
    public DirectPublicationReceipt trackWithReceipt(float yaw,float pitch,java.util.function.BooleanSupplier publicationFence,FinalWorkflowLeaf finalWorkflowVeto){
        if(client.isSameThread())queuedRootCameraDeclaration=null;var episode=enterCameraMethod();if(episode==null){return originalCameraEntry3(yaw,pitch,publicationFence,finalWorkflowVeto);}try{var result=originalCameraEntry3(yaw,pitch,publicationFence,finalWorkflowVeto);episode.normal=true;return result;}finally{episode.close();}
    }
    private DirectPublicationReceipt originalCameraEntry3(float yaw,float pitch,java.util.function.BooleanSupplier publicationFence,FinalWorkflowLeaf finalWorkflowVeto){
        java.util.Objects.requireNonNull(publicationFence);java.util.Objects.requireNonNull(finalWorkflowVeto);
        if(!client.isSameThread())throw new IllegalStateException("Rotation requests must run on the client thread");
        var root=dev.veltraph.client.VeltraphBootstrap.moduleManager();var lifecycle=root==null?null:root.lifecycleSnapshot();
        var installation=McOwnedRotation.installationSnapshot(client);var configSnapshot=storedRotationSnapshot();
        var inputSnapshot=dev.veltraph.client.input.ScriptingInputLock.controlSnapshot();
        if(lifecycle==null||!lifecycle.known()||installation==null||configSnapshot==null||inputSnapshot==null||!inputSnapshot.active())return null;
        return trackInternalFinal(yaw,pitch,publicationFence,true,finalWorkflowVeto,lifecycle,installation,configSnapshot,inputSnapshot);
    }
    private DirectPublicationReceipt trackInternalFinal(float yaw,float pitch,java.util.function.BooleanSupplier fence,boolean required,FinalWorkflowLeaf finalWorkflowVeto,dev.veltraph.client.core.ModuleManager.LifecycleSnapshot lifecycle,McOwnedRotation.InstallationSnapshot installation,dev.veltraph.client.config.MapConfigService.StoredDoubleSnapshot configSnapshot,dev.veltraph.client.input.ScriptingInputLock.ControlSnapshot inputSnapshot){
        if(!client.isSameThread())throw new IllegalStateException("Rotation requests must run on the client thread");
        if(!Float.isFinite(yaw)||!Float.isFinite(pitch)||Math.abs(pitch)>90)throw new IllegalArgumentException("Invalid target");
        long entryGeneration=requestGeneration;var before=directScope();
        if(requestGeneration!=entryGeneration||before!=null&&before.generation()!=entryGeneration)return null;
        var prior=lastDirectPublication;lastDirectPublication=null;
        var p=client.player;if(p==null||!p.isAlive()||client.level==null||client.gui.screen()!=null)return null;
        if(p.isScoping()||required&&before==null)return null;
        if(fence!=null&&!fence.getAsBoolean())return null;
        if(before!=null&&!dev.veltraph.client.rotation.DirectRotationPublicationIdentity.same(before,directScope()))return null;
        if(p!=owner||client.level!=ownerLevel||p.tickCount-lastTrackTick>2){trackingYaw.reset();trackingPitch.reset();lastTrackTick=Integer.MIN_VALUE;}
        owner=p;ownerLevel=client.level;requestGeneration++;lastDirectCancellation=null;publicationTransition=null;request.cancel();awaitingEndpoint=null;awaitingTicket=0;requestAuthority=null;requestConnection=null;
        if(lastTrackTick==p.tickCount){
            if(prior!=null&&before!=null&&(fence==null||fence.getAsBoolean())&&dev.veltraph.client.rotation.DirectRotationPublicationIdentity.current(prior.proof,before)){
                var after=directScope();var proof=new dev.veltraph.client.rotation.DirectRotationPublicationIdentity.Proof(before,after,prior.actualTicket(),dev.veltraph.client.rotation.DirectRotationPublicationIdentity.Mode.SAME_TICK_TRACK);
                if(dev.veltraph.client.rotation.DirectRotationPublicationIdentity.retained(proof,prior.proof)&&finalWorkflowAllowed(finalWorkflowVeto,()->finalNativeLeaf(after,lifecycle,installation,configSnapshot,inputSnapshot,after.generation(),after.epoch(),after.publicationSerial()))){
                    var retained=new DirectPublicationReceipt(proof,prior.yaw,prior.pitch,false);lastDirectPublication=retained;return retained;
                }
            }
            return null;
        }
        lastTrackTick=p.tickCount;
        double speed=config.get(dev.veltraph.client.config.RotationConfig.ROTATION_SPEED_MULTIPLIER);
        float nextYaw=trackingYaw.step(p.getYRot(),yaw,speed),nextPitch=trackingPitch.stepLinear(p.getXRot(),pitch,speed);
        // Reentrant config/heading callbacks cannot let this old call cancel a newer queued request.
        if(before!=null&&!dev.veltraph.client.rotation.DirectRotationPublicationIdentity.preplan(before,directScope(),dev.veltraph.client.rotation.DirectRotationPublicationIdentity.Mode.AIM))return null;
        var result=aimInternalFinal(nextYaw,nextPitch,fence,required,before,dev.veltraph.client.rotation.DirectRotationPublicationIdentity.Mode.TRACK,finalWorkflowVeto,lifecycle,installation,configSnapshot,inputSnapshot);
        if(before==null||result!=null)stepped=true;return result;
    }
    private DirectPublicationReceipt aimInternalFinal(float yaw,float pitch,java.util.function.BooleanSupplier fence,boolean required,
            dev.veltraph.client.rotation.DirectRotationPublicationIdentity.Scope before,dev.veltraph.client.rotation.DirectRotationPublicationIdentity.Mode mode,FinalWorkflowLeaf finalWorkflowVeto,dev.veltraph.client.core.ModuleManager.LifecycleSnapshot lifecycle,McOwnedRotation.InstallationSnapshot installation,dev.veltraph.client.config.MapConfigService.StoredDoubleSnapshot configSnapshot,dev.veltraph.client.input.ScriptingInputLock.ControlSnapshot inputSnapshot){
        if(required&&(before==null||fence!=null&&!fence.getAsBoolean()))return null;
        if(before!=null){
            var entered=directScope();boolean same=mode==dev.veltraph.client.rotation.DirectRotationPublicationIdentity.Mode.TRACK
                ?dev.veltraph.client.rotation.DirectRotationPublicationIdentity.preplan(before,entered,dev.veltraph.client.rotation.DirectRotationPublicationIdentity.Mode.AIM)
                :dev.veltraph.client.rotation.DirectRotationPublicationIdentity.same(before,entered);
            if(!same)return null;
        }
        requestGeneration++;lastDirectCancellation=null;publicationTransition=null;lastDirectPublication=null;
        request.cancel();awaitingEndpoint=null;awaitingTicket=0;requestAuthority=null;requestConnection=null;
        var p=player();if(p==null)return null;
        var preplan=directScope();
        if((required||before!=null)&&!dev.veltraph.client.rotation.DirectRotationPublicationIdentity.preplan(before,preplan,mode))return null;
        if(fence!=null&&!fence.getAsBoolean())return null;
        // Fence again after the caller's refusal-only callback; it cannot authorize a changed native scene.
        if((required||before!=null)&&!dev.veltraph.client.rotation.DirectRotationPublicationIdentity.preplan(before,directScope(),mode))return null;
        float bounded=Math.max(-90f,Math.min(90f,pitch));
        var finalBefore=directScope();if(!dev.veltraph.client.rotation.DirectRotationPublicationIdentity.preplan(before,finalBefore,mode))return null;
        long ticket=observeRootNativePlan(yaw,bounded,()->McOwnedRotation.planScoped(client,yaw,bounded,new dev.veltraph.client.rotation.OwnedRotationTrajectory.Scope(finalBefore.cameraOwner(),finalBefore.player(),finalBefore.world(),finalBefore.connection()),()->finalNativeLeaf(finalBefore,lifecycle,installation,configSnapshot,inputSnapshot,finalBefore.generation(),finalBefore.epoch(),finalBefore.publicationSerial()),finalWorkflowVeto));
        var after=directScope();var proof=new dev.veltraph.client.rotation.DirectRotationPublicationIdentity.Proof(before,after,ticket,mode);
        if(dev.veltraph.client.rotation.DirectRotationPublicationIdentity.publication(proof)&&finalWorkflowAllowed(finalWorkflowVeto,()->finalNativeLeaf(after,lifecycle,installation,configSnapshot,inputSnapshot,after.generation(),after.epoch(),ticket))){
            var published=new DirectPublicationReceipt(proof,yaw,bounded,true);lastDirectPublication=published;return published;
        }
        return null;
    }

    /** Only known final stores provide a callback-free final value/revision observation. */
    private dev.veltraph.client.config.MapConfigService.StoredDoubleSnapshot storedRotationSnapshot(){
        var key=dev.veltraph.client.config.RotationConfig.ROTATION_SPEED_MULTIPLIER;
        if(config instanceof dev.veltraph.client.config.MapConfigService map)return map.storedDoubleSnapshot(key);
        if(config instanceof dev.veltraph.client.config.JsonConfigService json)return json.storedDoubleSnapshot(key);
        return null;
    }
    /** Callback-free native/lifecycle equality, independent of the caller's refusal-only workflow check. */
    private boolean finalNativeLeaf(dev.veltraph.client.rotation.DirectRotationPublicationIdentity.Scope expected,dev.veltraph.client.core.ModuleManager.LifecycleSnapshot lifecycle,McOwnedRotation.InstallationSnapshot installation,dev.veltraph.client.config.MapConfigService.StoredDoubleSnapshot configSnapshot,dev.veltraph.client.input.ScriptingInputLock.ControlSnapshot inputSnapshot,long generation,long epoch,long serial){
        if(expected==null||!(expected.root() instanceof dev.veltraph.client.core.ModuleManager root)||lifecycle==null||installation==null||configSnapshot==null||inputSnapshot==null)return false;
        return configSnapshot.current()&&inputSnapshot.current()&&inputSnapshot.active()&&installation.current()&&client.isSameThread()&&dev.veltraph.client.VeltraphBootstrap.moduleManager()==root&&root.lifecycleCurrent(lifecycle)
            &&dev.veltraph.client.VeltraphBootstrap.config()==expected.configStore()&&config==expected.configStore()&&expected.service()==this
            &&client.player==expected.player()&&client.level==expected.world()&&client.getConnection()==expected.connection()
            &&client.player!=null&&client.level!=null&&client.getConnection()!=null&&client.player.level()==client.level
            &&client.player.isAlive()&&!client.player.isPassenger()&&!client.player.isScoping()&&!client.isPaused()&&client.gui.screen()==null
            &&client.getCameraEntity()==client.player&&!client.level.tickRateManager().isEntityFrozen(client.player)
            &&client.player.containerMenu==client.player.inventoryMenu&&client.player.inventoryMenu.getCarried().isEmpty()
            &&requestGeneration==generation&&McOwnedRotation.epoch(client)==epoch&&McOwnedRotation.publicationSerial(client)==serial;
    }
    @Override public boolean sourceBusy(){
        if(!client.isSameThread())throw new IllegalStateException("Rotation query requires client thread");
        var p=client.player;
        if(p==null||p!=owner||client.level!=ownerLevel||client.getConnection()!=requestConnection||!p.isAlive()
                ||client.gui.screen()!=null||client.isPaused()||p.isScoping())return false;
        if(McOwnedRotation.installed(client)&&(requestAuthority!=McOwnedRotation.authority(client)||requestEpoch!=McOwnedRotation.epoch(client)||requestPublicationSerial!=McOwnedRotation.publicationSerial(client)))return false;
        return request.sourceBusy(p.getYRot(),p.getXRot());
    }
    @Override public void tickRotation() {
        var episode=enterCameraMethod();if(episode==null){originalCameraEntry4();return;}episode.advancing=true;try{originalCameraEntry4();episode.normal=true;}finally{episode.close();}
    }
    private void originalCameraEntry4(){
        if(stepped) { stepped=false;return; }
        advance();
    }
    private void advance() {
        var saved=savedReturnQueue;if(saved!=null&&requestGeneration==saved.generation&&!saved.cancelled){
            // The actual named Root advance capsule is captured before any original provider or completion callback.
            var originalAdvance=saved.rootSource.captureAdvanceInvocation(this);
            advanceSavedReturnQueue(saved,originalAdvance);return;
        }
        // Capture the one actual Root invocation before any original authority/config/completion callbacks.
        var declaredRoot=queuedRootCameraDeclaration;var originalEpisode=cameraMethodEpisode;
        if(originalEpisode!=null&&originalEpisode.advancing){
            try{originalEpisode.advanceInvocation=declaredRoot!=null&&declaredRoot.metadataCurrent()
                ?declaredRoot.source.captureAdvanceInvocation(this):null;}
            catch(RuntimeException|Error unavailable){originalEpisode.order.revoke();}
        }
        var queued=lastQueuedRequest;if(queued!=null&&requestGeneration==queued.generation&&!queued.cancelled){advanceOwnedQueue(queued);return;}
        if(!request.pending())return; // Idle service must not revoke navigation/combat's published pose.
        var p=client.player;
        McOwnedRotation.validate(client);
        if(McOwnedRotation.installed(client)&&(requestAuthority!=McOwnedRotation.authority(client)||requestEpoch!=McOwnedRotation.epoch(client)||requestPublicationSerial!=McOwnedRotation.publicationSerial(client))){
            request.cancel();awaitingEndpoint=null;awaitingTicket=0;return; // Do not revoke a new owner's published segment.
        }
        if(p==null||p!=owner||!p.isAlive()||client.level==null||client.level!=ownerLevel
                ||client.getConnection()!=requestConnection||client.gui.screen()!=null) { cancelRotation();return; }
        if(awaitingEndpoint!=null&&McOwnedRotation.completed(client,awaitingTicket)
                &&Math.abs(dev.veltraph.client.rotation.SourceRotationStepper.wrap(p.getYRot()-awaitingEndpoint.yaw()))<.001
                &&Math.abs(p.getXRot()-awaitingEndpoint.pitch())<.001) {
            var endpoint=awaitingEndpoint;awaitingEndpoint=null;awaitingTicket=0;request.applied(endpoint);
        }
        if(!request.pending())return;
        double speed=config.get(dev.veltraph.client.config.RotationConfig.ROTATION_SPEED_MULTIPLIER);
        if(!Double.isFinite(speed)||speed<=0) { cancelRotation();throw new IllegalArgumentException("Shared Rotation Speed must be positive and finite"); }
        var result=request.advance(p.getYRot(),p.getXRot(),dev.veltraph.client.rotation.SourceRotationStepper.Settings.standard(
                client.options.sensitivity().get(),speed),true,p.isScoping());
        if(result.rejected()) { dev.veltraph.client.log.VeltraphLog.details("Rotation","Calculated speeds exceed source guard");return; }
        var before=publicationStamp(requestEpoch);
        long originalFarmSerial=requestPublicationSerial;
        if(p!=client.player||p!=owner||McOwnedRotation.installed(client)&&requestPublicationSerial!=McOwnedRotation.publicationSerial(client))return;
        if(McOwnedRotation.installed(client)&&!dev.veltraph.client.rotation.RotationReceiptIdentity.current(before,publicationStamp(McOwnedRotation.epoch(client)),
                client.player,client.level,client.getConnection(),McOwnedRotation.authority(client),false))return;
        var queueFarm=farmCamera;long actualFarmGeneration=requestGeneration;
        long ticket=observeRootNativePlan(result.yaw(),result.pitch(),()->McOwnedRotation.plan(client,result.yaw(),result.pitch()));
        if(queueFarm!=null&&farmCamera==queueFarm&&queueFarm.queued
            &&queueFarm.generation==before.generation()&&queueFarm.epoch==before.epoch()&&queueFarm.serial==originalFarmSerial)
            rememberFarmNativeTicket(queueFarm.scope,ticket,actualFarmGeneration,true);
        if(ticket==0)request.applied(result);
        else if(ticket>0){
            var after=publicationStamp(McOwnedRotation.epoch(client));
            var transition=new dev.veltraph.client.rotation.RotationPublicationIdentity.Transition(before,after,ticket);
            if(dev.veltraph.client.rotation.RotationPublicationIdentity.ownedTicket(before,after,transition,
                    client.player,client.level,client.getConnection(),McOwnedRotation.authority(client),McOwnedRotation.publicationSerial(client))!=ticket){
                if(requestGeneration==before.generation()){request.cancel();awaitingEndpoint=null;awaitingTicket=0;publicationTransition=null;}return;
            }
            // Only this exact own plan may advance the pending request's epoch.
            requestEpoch=after.epoch();requestPublicationSerial=ticket;publicationTransition=transition;
            var ownFarm=farmCamera;
            if(ownFarm!=null&&ownFarm.direct==null&&ownFarm.scope.activeCurrent()
                &&ownFarm.generation==before.generation()&&ownFarm.epoch==before.epoch()&&ownFarm.serial==originalFarmSerial)
                farmCamera=new FarmCamera(ownFarm.scope,null,true,true);
            if(result.complete()){awaitingEndpoint=result;awaitingTicket=ticket;}
        }
    }
    @Override public void cancelRotation() {
        if(client.isSameThread())queuedRootCameraDeclaration=null;var episode=enterCameraMethod();if(episode==null){originalCameraEntry5();return;}try{originalCameraEntry5();episode.normal=true;}finally{episode.close();}
    }
    private void originalCameraEntry5(){ if(McIssuedFarmReturnCleanup.cancelCanonical(this))return;if(McIssuedFarmResumeGuard.retireRotation(this))return;if(interceptFarmStop())return;farmCamera=null;requestGeneration++;lastDirectCancellation=null;lastDirectPublication=null;publicationTransition=null;request.cancel();awaitingEndpoint=null;awaitingTicket=0;requestAuthority=null;requestConnection=null;observeRootNativeCancel(()->McOwnedRotation.cancel(client));owner=null;ownerLevel=null;stepped=false;trackingYaw.reset();trackingPitch.reset();lastTrackTick=Integer.MIN_VALUE; }

    /** Read-only request generation; this is identity evidence, never gameplay permission. */
    public long requestGeneration(){if(!client.isSameThread())throw new IllegalStateException("Rotation generation requires client thread");return requestGeneration;}

    /** Cancellation receipt grants no gameplay authority and cannot cancel a newer request. */
    public record CleanupReceipt(Object service,long generation,Object authority,Object player,Object world,Object connection,long epoch){}
    public CleanupReceipt cleanupReceipt(Object authority){
        if(!client.isSameThread()||authority==null||requestAuthority!=authority||owner==null)return null;
        return handGuiSource(new CleanupReceipt(this,requestGeneration,authority,owner,ownerLevel,requestConnection,requestEpoch));
    }
    public boolean ownsCleanup(CleanupReceipt receipt,boolean allowAbsentAuthority){
        if(receipt==null||!client.isSameThread()||McOwnedRotation.installed(client)&&requestPublicationSerial!=McOwnedRotation.publicationSerial(client))return false;
        var expected=new dev.veltraph.client.rotation.RotationReceiptIdentity.Stamp(receipt.service(),receipt.generation(),receipt.authority(),receipt.player(),receipt.world(),receipt.connection(),receipt.epoch());
        var actual=new dev.veltraph.client.rotation.RotationReceiptIdentity.Stamp(this,requestGeneration,requestAuthority,owner,ownerLevel,requestConnection,McOwnedRotation.epoch(client));
        return dev.veltraph.client.rotation.RotationReceiptIdentity.current(expected,actual,client.player,client.level,client.getConnection(),McOwnedRotation.authority(client),allowAbsentAuthority);
    }
    private dev.veltraph.client.rotation.RotationReceiptIdentity.Stamp publicationStamp(long epoch){
        return new dev.veltraph.client.rotation.RotationReceiptIdentity.Stamp(this,requestGeneration,requestAuthority,owner,ownerLevel,requestConnection,epoch);
    }
    /** Refresh only across the last recorded own endpoint plan, preserving exact request generation. */
    public CleanupReceipt refreshOwnedReceipt(CleanupReceipt receipt){
        if(receipt==null||!client.isSameThread()||McOwnedRotation.installed(client)&&requestPublicationSerial!=McOwnedRotation.publicationSerial(client))return null;
        if(currentRequest(receipt))return receipt;
        var expected=new dev.veltraph.client.rotation.RotationReceiptIdentity.Stamp(receipt.service(),receipt.generation(),receipt.authority(),receipt.player(),receipt.world(),receipt.connection(),receipt.epoch());
        var actual=publicationStamp(McOwnedRotation.epoch(client));
        if(!dev.veltraph.client.rotation.RotationPublicationIdentity.refresh(expected,actual,publicationTransition,
                client.player,client.level,client.getConnection(),McOwnedRotation.authority(client)))return null;
        var refreshed=new CleanupReceipt(this,requestGeneration,requestAuthority,owner,ownerLevel,requestConnection,requestEpoch);
        // Ordinary public-stamp refresh remains compatible; GUI custody propagates only from a real handout.
        return guiHandedGeneration==requestGeneration&&guiHandedSource(receipt)?handGuiSource(refreshed):refreshed;
    }
    /** Read-only exact own queued-publication ticket. Raw publication serial alone grants nothing. */
    public long ownedPublicationTicket(CleanupReceipt receipt){
        if(receipt==null||!client.isSameThread())return -1;
        var expected=new dev.veltraph.client.rotation.RotationReceiptIdentity.Stamp(receipt.service(),receipt.generation(),receipt.authority(),receipt.player(),receipt.world(),receipt.connection(),receipt.epoch());
        var actual=publicationStamp(McOwnedRotation.epoch(client));
        return dev.veltraph.client.rotation.RotationPublicationIdentity.ownedTicket(expected,actual,publicationTransition,
                client.player,client.level,client.getConnection(),McOwnedRotation.authority(client),McOwnedRotation.publicationSerial(client));
    }
    /** Gameplay freshness refusal only; does not grant owner/context/tool authority. */
    public boolean currentRequest(CleanupReceipt receipt){return ownsCleanup(receipt,false);}
    public boolean cancelOwned(CleanupReceipt receipt,boolean allowAbsentAuthority){
        if(!ownsCleanup(receipt,allowAbsentAuthority))return false;
        cancelRotation();return true;
    }

    /** Exact actual GUI cancellation retires only this privately handed request; never cancels camera again. */
    public GuiRequestRetirement retireGuiAvailability(CleanupReceipt exact,McOwnedRotation.GuiAvailabilityRevocation gui,java.util.function.BooleanSupplier refusalOnlyFence){
        java.util.Objects.requireNonNull(refusalOnlyFence);
        if(!guiRequestBefore(exact,gui))return null;
        long generation=requestGeneration,epoch=McOwnedRotation.epoch(client);var transition=publicationTransition;
        if(!refusalOnlyFence.getAsBoolean()||requestGeneration!=generation||publicationTransition!=transition||!guiRequestBefore(exact,gui))return null;
        requestGeneration++;lastDirectCancellation=null;lastDirectPublication=null;publicationTransition=null;
        request.cancel();awaitingEndpoint=null;awaitingTicket=0;requestAuthority=null;requestConnection=null;owner=null;ownerLevel=null;
        stepped=false;trackingYaw.reset();trackingPitch.reset();lastTrackTick=Integer.MIN_VALUE;
        var receipt=new GuiRequestRetirement(this,exact,gui,generation,requestGeneration,epoch,McOwnedRotation.epoch(client),gui.actualTicket());
        lastGuiRetirement=receipt;guiHandedPrevious=guiHandedLatest=null;
        if(!refusalOnlyFence.getAsBoolean()||!receipt.current())return null;
        return receipt;
    }
    private boolean guiRequestBefore(CleanupReceipt exact,McOwnedRotation.GuiAvailabilityRevocation gui){
        if(exact==null||gui==null||!client.isSameThread()||requestGeneration==Long.MAX_VALUE||guiHandedGeneration!=requestGeneration||!guiHandedSource(exact)
            ||exact.service()!=this||exact.generation()!=requestGeneration||exact.authority()!=requestAuthority||exact.player()!=owner||exact.world()!=ownerLevel||exact.connection()!=requestConnection
            ||gui.storeIdentity()!=config||gui.registryIdentity()!=dev.veltraph.client.VeltraphBootstrap.moduleManager())return false;
        var transition=publicationTransition;if(!dev.veltraph.client.rotation.RotationPublicationIdentity.valid(transition)||transition.ticket()!=requestPublicationSerial)return false;
        var expected=new dev.veltraph.client.rotation.RotationReceiptIdentity.Stamp(exact.service(),exact.generation(),exact.authority(),exact.player(),exact.world(),exact.connection(),exact.epoch());
        if(!sameGuiStamp(expected,transition.before())&&!sameGuiStamp(expected,transition.after()))return false;
        if(!sameGuiStamp(publicationStamp(requestEpoch),transition.after())||requestPublicationSerial!=gui.actualTicket())return false;
        return gui.matchesOriginal(requestAuthority,owner,ownerLevel,requestConnection,requestEpoch,requestPublicationSerial);
    }
    private static boolean sameGuiStamp(dev.veltraph.client.rotation.RotationReceiptIdentity.Stamp a,dev.veltraph.client.rotation.RotationReceiptIdentity.Stamp b){
        return a!=null&&b!=null&&a.service()==b.service()&&a.generation()==b.generation()&&a.authority()==b.authority()&&a.player()==b.player()&&a.world()==b.world()&&a.connection()==b.connection()&&a.epoch()==b.epoch();}
    /** Private actual request-only cancellation proof. It cannot mint an arrival or new publication. */
    public static final class GuiRequestRetirement {
        private final McRotationService service;private final CleanupReceipt original;private final McOwnedRotation.GuiAvailabilityRevocation gui;
        private final long beforeGeneration,afterGeneration,beforeEpoch,afterEpoch,ticket;
        private GuiRequestRetirement(McRotationService service,CleanupReceipt original,McOwnedRotation.GuiAvailabilityRevocation gui,long beforeGeneration,long afterGeneration,long beforeEpoch,long afterEpoch,long ticket){this.service=service;this.original=original;this.gui=gui;this.beforeGeneration=beforeGeneration;this.afterGeneration=afterGeneration;this.beforeEpoch=beforeEpoch;this.afterEpoch=afterEpoch;this.ticket=ticket;}
        public CleanupReceipt originalSource(){return original;}public McOwnedRotation.GuiAvailabilityRevocation guiProof(){return gui;}
        public McOwnedRotation.GuiAvailabilityRevocation currentGuiProof(){return current()?gui.currentDescendant():null;}
        public long beforeGeneration(){return beforeGeneration;}public long afterGeneration(){return afterGeneration;}
        /** Service retirement leaves the already GUI-cancelled native epoch unchanged. */
        public long beforeEpoch(){return beforeEpoch;}public long afterEpoch(){return afterEpoch;}public long actualTicket(){return ticket;}
        public boolean current(){return service.client.isSameThread()&&service.lastGuiRetirement==this&&beforeGeneration<Long.MAX_VALUE&&afterGeneration==beforeGeneration+1
            &&beforeEpoch==afterEpoch&&afterEpoch==gui.afterEpoch()&&ticket==gui.actualTicket()&&gui.currentDescendant()!=null
            &&service.requestGeneration==afterGeneration&&service.requestAuthority==null&&service.requestConnection==null&&service.owner==null&&service.ownerLevel==null
            &&service.publicationTransition==null&&!service.request.pending()&&McOwnedRotation.publicationSerial(service.client)==ticket;}
    }
    private LocalPlayer player() {
        return client.player;
    }

    @Override
    public float yaw() {
        LocalPlayer p = player();
        return p == null ? 0f : p.getYRot();
    }

    @Override
    public float pitch() {
        LocalPlayer p = player();
        return p == null ? 0f : p.getXRot();
    }

    @Override public void aim(float yaw,float pitch){
        if(client.isSameThread())queuedRootCameraDeclaration=null;var episode=enterCameraMethod();if(episode==null){originalCameraEntry6(yaw,pitch);return;}try{originalCameraEntry6(yaw,pitch);episode.normal=true;}finally{episode.close();}
    }
    private void originalCameraEntry6(float yaw,float pitch){
        var invokedFarm=McFarmOwnedCleanup.farmingTickFarm();
        if(invokedFarm!=null&&McFarmOwnedCleanup.NativeScope.capture(client,invokedFarm)==null)return;
        if(McFarmOwnedCleanup.stopping())return;
        var scope=farmCameraScope();var call=enterFarmPlan(scope);
        try{var receipt=aimEntry(yaw,pitch,null,false);rememberFarmDirect(scope,receipt);}finally{leaveFarmPlan(call);}
    }
    public DirectPublicationReceipt aimWithReceipt(float yaw,float pitch,java.util.function.BooleanSupplier publicationFence){
        if(client.isSameThread())queuedRootCameraDeclaration=null;var episode=enterCameraMethod();if(episode==null){return originalCameraEntry7(yaw,pitch,publicationFence);}try{var result=originalCameraEntry7(yaw,pitch,publicationFence);episode.normal=true;return result;}finally{episode.close();}
    }
    private DirectPublicationReceipt originalCameraEntry7(float yaw,float pitch,java.util.function.BooleanSupplier publicationFence){
        return aimEntry(yaw,pitch,java.util.Objects.requireNonNull(publicationFence),true);
    }
    private DirectPublicationReceipt aimEntry(float yaw,float pitch,java.util.function.BooleanSupplier fence,boolean required){
        long generation=requestGeneration;var prior=lastDirectPublication;var before=directScope();
        if(requestGeneration!=generation||before!=null&&before.generation()!=generation)return null;
        if(lastDirectPublication==prior)lastDirectPublication=null;
        return aimInternal(yaw,pitch,fence,required,before,dev.veltraph.client.rotation.DirectRotationPublicationIdentity.Mode.AIM);
    }
    public DirectPublicationReceipt aimWithReceipt(float yaw,float pitch){return aimWithReceipt(yaw,pitch,()->true);}
    /** Typed original Root Wart endpoint; canonical native camera episode and Farm cleanup custody stay intact. */
    public SourceAimOutcome aimWartDropWithOutcome(dev.veltraph.client.VeltraphBootstrap.RootWartDropPublication source,McWartDropWaterActor actor,float yaw,float pitch){
        if(wartDropAimActor!=null||source==null||actor==null||!actor.publishingFor(source)||source.farmServicesIdentity().rotation()!=this||!source.current())return null;
        if(client.isSameThread())queuedRootCameraDeclaration=null;
        var episode=enterCameraMethod();if(episode==null)return null;
        var scope=farmCameraScope();var call=enterFarmPlan(scope);
        wartDropAimActor=actor;wartDropAimScope=scope;wartDropAimEpisode=episode;wartDropAimPlan=call;
        try{if(scope==null||scope.farm!=source.farmIdentity()||!source.current())return null;
            var outcome=originalCameraEntry8(yaw,pitch,source::current,source::beforeEffectCurrent);
            if(outcome!=null)rememberFarmDirect(scope,outcome.cleanupPublication());
            episode.normal=true;return outcome;
        }finally{if(wartDropAimActor==actor){wartDropAimActor=null;wartDropAimScope=null;wartDropAimEpisode=null;wartDropAimPlan=null;}leaveFarmPlan(call);episode.close();}
    }
    private McWartDropWaterActor wartDropAimActor;
    private McFarmOwnedCleanup.NativeScope wartDropAimScope;
    private CameraMethodEpisode wartDropAimEpisode;
    private FarmPlanCall wartDropAimPlan;
    private final class WartDropAimInvocation {
        final McWartDropWaterActor actor=wartDropAimActor;
        final McFarmOwnedCleanup.NativeScope scope=wartDropAimScope;
        final CameraMethodEpisode episode=wartDropAimEpisode;
        final FarmPlanCall call=wartDropAimPlan;
        boolean exactCurrent(){return actor!=null&&scope!=null&&episode!=null&&call!=null&&!call.revoked
            &&wartDropAimActor==actor&&wartDropAimScope==scope&&cameraMethodEpisode==episode&&episode.active()
            &&wartDropAimEpisode==episode&&wartDropAimPlan==call&&farmPlanCall==call;}
    }
    /** Cache only THIS privately captured typed invocation's own outcome before any later provider can disable the farm. */
    private void retainWartDropAimOutcome(WartDropAimInvocation exact,SourceAimOutcome actual){
        if(exact==null||!exact.exactCurrent()||actual!=lastSourceAimOutcome||actual.after().parent()!=exact.scope.farm||actual.after().service()!=this)return;
        farmCamera=new FarmCamera(exact.scope,actual.cleanupPublication(),actual.cleanupPublication()!=null||actual.priorPublication()!=null,false);
        exact.actor.retainSourceAimOutcome(this,actual);
    }
    private SourceAimOutcome lastSourceAimOutcome;
    private SourceAimCancellation lastSourceAimCancellation;
    /** Original AIM endpoint semantics; only gameplay publication is returned through the old additive ABI. */
    public DirectPublicationReceipt aimWithReceipt(float yaw,float pitch,java.util.function.BooleanSupplier refusalOnlyFence,FinalWorkflowLeaf privateSource){
        var outcome=aimWithOutcome(yaw,pitch,refusalOnlyFence,privateSource);
        return outcome==null?null:outcome.gameplayPublication();
    }
    /** Authentic own outcome: a metadata-only refusal and a positive native plan grant cleanup custody only. */
    public SourceAimOutcome aimWithOutcome(float yaw,float pitch,java.util.function.BooleanSupplier refusalOnlyFence,FinalWorkflowLeaf privateSource){
        if(client.isSameThread())queuedRootCameraDeclaration=null;var episode=enterCameraMethod();if(episode==null){return originalCameraEntry8(yaw,pitch,refusalOnlyFence,privateSource);}try{var result=originalCameraEntry8(yaw,pitch,refusalOnlyFence,privateSource);episode.normal=true;return result;}finally{episode.close();}
    }
    private SourceAimOutcome originalCameraEntry8(float yaw,float pitch,java.util.function.BooleanSupplier refusalOnlyFence,FinalWorkflowLeaf privateSource){
        var wartInvocation=wartDropAimActor!=null&&cameraMethodEpisode==wartDropAimEpisode?new WartDropAimInvocation():null;
        java.util.Objects.requireNonNull(refusalOnlyFence);java.util.Objects.requireNonNull(privateSource);
        if(!client.isSameThread())throw new IllegalStateException("Rotation requests must run on the client thread");
        var root=dev.veltraph.client.VeltraphBootstrap.moduleManager();var lifecycle=root==null?null:root.lifecycleSnapshot();
        var installation=McOwnedRotation.installationSnapshot(client);var configSnapshot=storedRotationSnapshot();
        var inputSnapshot=dev.veltraph.client.input.ScriptingInputLock.controlSnapshot();
        if(lifecycle==null||!lifecycle.known()||installation==null||configSnapshot==null||inputSnapshot==null||!inputSnapshot.active())return null;
        long generation=requestGeneration;var prior=lastDirectPublication;var before=directScope();
        if(requestGeneration!=generation||generation==Long.MAX_VALUE||before==null||before.generation()!=generation||lastDirectPublication!=prior)return null;
        // A copied scope cannot replace an actual privately minted C7 handle.
        if(prior!=null&&!dev.veltraph.client.rotation.DirectRotationPublicationIdentity.current(prior.proof,before))return null;
        var context=new SourceAimContext(before,prior,lifecycle,installation,configSnapshot,inputSnapshot);
        if(!finalWorkflowAllowed(privateSource,()->context.nativeCurrent(before))||!refusalOnlyFence.getAsBoolean())return null;
        var entered=directScope();
        if(!dev.veltraph.client.rotation.DirectRotationPublicationIdentity.same(before,entered)||lastDirectPublication!=prior
            ||!finalWorkflowAllowed(privateSource,()->context.nativeCurrent(before)))return null;
        if(wartInvocation!=null&&(!wartInvocation.exactCurrent()||!wartInvocation.actor.beforeMetadataCurrent()))return null;
        requestGeneration++;lastDirectCancellation=null;publicationTransition=null;lastDirectPublication=null;
        lastSourceAimOutcome=null;lastSourceAimCancellation=null;
        request.cancel();awaitingEndpoint=null;awaitingTicket=0;requestAuthority=null;requestConnection=null;
        // Seal actual own metadata custody BEFORE a provider can enter normal disable/onDisable.
        var metadataAfter=sourceAimStamp(before);
        if(!metadataAimTransition(before,metadataAfter)||!context.nativeCurrent(metadataAfter))return null;
        var metadataCustody=new SourceAimOutcome(context,metadataAfter,null,null);lastSourceAimOutcome=metadataCustody;retainWartDropAimOutcome(wartInvocation,metadataCustody);
        var p=player();if(p==null)return sourceAimRefusal(context);
        var preplan=directScope();
        if(!metadataAimTransition(before,preplan))return sourceAimRefusal(context);
        if(!refusalOnlyFence.getAsBoolean())return sourceAimRefusal(context);
        var finalBefore=directScope();
        if(!metadataAimTransition(before,finalBefore))return sourceAimRefusal(context);
        float bounded=Math.max(-90f,Math.min(90f,pitch));
        long ticket=observeRootNativePlan(yaw,bounded,()->McOwnedRotation.planScoped(client,yaw,bounded,new dev.veltraph.client.rotation.OwnedRotationTrajectory.Scope(finalBefore.cameraOwner(),finalBefore.player(),finalBefore.world(),finalBefore.connection()),
            ()->context.nativeCurrent(finalBefore),privateSource));
        if(ticket<=0)return sourceAimRefusal(context);
        // Native plan return is the grant. Cache its C7 BEFORE the final authority/source callback.
        var after=sourceAimStamp(finalBefore);
        var proof=new dev.veltraph.client.rotation.DirectRotationPublicationIdentity.Proof(before,after,ticket,dev.veltraph.client.rotation.DirectRotationPublicationIdentity.Mode.AIM);
        if(!dev.veltraph.client.rotation.DirectRotationPublicationIdentity.publication(proof)||!context.nativeCurrent(after))return null;
        var published=new DirectPublicationReceipt(proof,yaw,bounded,true);lastDirectPublication=published;
        var custody=new SourceAimOutcome(context,after,published,null);lastSourceAimOutcome=custody;retainWartDropAimOutcome(wartInvocation,custody);
        var actual=directScope();
        boolean gameplay=dev.veltraph.client.rotation.DirectRotationPublicationIdentity.same(after,actual)
            &&finalWorkflowAllowed(privateSource,()->context.nativeCurrent(after));
        if(lastSourceAimOutcome!=custody||lastDirectPublication!=published||!context.nativeCurrent(after))return null;
        if(!gameplay)return custody;
        var allowed=new SourceAimOutcome(context,after,published,published);lastSourceAimOutcome=allowed;retainWartDropAimOutcome(wartInvocation,allowed);return allowed;
    }
    /** Public evidence predicate only; never constructs or grants an outcome. */
    public static boolean metadataAimTransition(dev.veltraph.client.rotation.DirectRotationPublicationIdentity.Scope before,dev.veltraph.client.rotation.DirectRotationPublicationIdentity.Scope after){
        return dev.veltraph.client.rotation.DirectRotationPublicationIdentity.preplan(before,after,dev.veltraph.client.rotation.DirectRotationPublicationIdentity.Mode.AIM);
    }
    private static boolean sourceAimCleanupSame(dev.veltraph.client.rotation.DirectRotationPublicationIdentity.Scope issued,dev.veltraph.client.rotation.DirectRotationPublicationIdentity.Scope actual,boolean allowDisabled){
        return dev.veltraph.client.rotation.DirectRotationPublicationIdentity.cleanupScene(issued,actual,allowDisabled)
            &&issued.generation()==actual.generation()&&issued.epoch()==actual.epoch()&&issued.publicationSerial()==actual.publicationSerial()&&issued.rotationRevision()==actual.rotationRevision();
    }
    /** Callback-free stamp only after a real scoped native plan; it is not a receipt factory. */
    private dev.veltraph.client.rotation.DirectRotationPublicationIdentity.Scope sourceAimStamp(dev.veltraph.client.rotation.DirectRotationPublicationIdentity.Scope scene){
        return new dev.veltraph.client.rotation.DirectRotationPublicationIdentity.Scope(scene.root(),scene.parent(),scene.inputOwner(),scene.cameraOwner(),scene.player(),scene.world(),scene.connection(),scene.configStore(),this,
            requestGeneration,McOwnedRotation.epoch(client),McOwnedRotation.publicationSerial(client),scene.rotationRevision());
    }
    private SourceAimOutcome sourceAimRefusal(SourceAimContext context){
        var after=directScope();
        if(lastDirectPublication!=null||!metadataAimTransition(context.before,after)||!context.nativeCurrent(after)||request.pending()
            ||awaitingEndpoint!=null||awaitingTicket!=0||requestAuthority!=null||requestConnection!=null)return null;
        var outcome=lastSourceAimOutcome;
        return outcome!=null&&outcome.context==context&&outcome.metadataOnlyRefusal()&&sourceAimCleanupSame(outcome.after,after,false)?outcome:null;
    }
    /** Reads only an already minted actual outcome; a raw scope can never populate this cache. */
    public SourceAimOutcome observedSourceAimOutcome(boolean allowDisabled){
        var outcome=lastSourceAimOutcome;
        return outcome!=null&&client.isSameThread()&&lastDirectPublication==outcome.cleanup&&outcome.context.cleanupNativeCurrent(outcome.after,allowDisabled)?outcome:null;
    }
    private final class SourceAimContext {
        private final dev.veltraph.client.rotation.DirectRotationPublicationIdentity.Scope before;private final DirectPublicationReceipt prior;
        private final dev.veltraph.client.core.ModuleManager.LifecycleSnapshot lifecycle;private final McOwnedRotation.InstallationSnapshot installation;
        private final dev.veltraph.client.config.MapConfigService.StoredDoubleSnapshot configSnapshot;private final dev.veltraph.client.input.ScriptingInputLock.ControlSnapshot inputSnapshot;
        private SourceAimContext(dev.veltraph.client.rotation.DirectRotationPublicationIdentity.Scope before,DirectPublicationReceipt prior,dev.veltraph.client.core.ModuleManager.LifecycleSnapshot lifecycle,McOwnedRotation.InstallationSnapshot installation,
            dev.veltraph.client.config.MapConfigService.StoredDoubleSnapshot configSnapshot,dev.veltraph.client.input.ScriptingInputLock.ControlSnapshot inputSnapshot){this.before=before;this.prior=prior;this.lifecycle=lifecycle;this.installation=installation;this.configSnapshot=configSnapshot;this.inputSnapshot=inputSnapshot;}
        private boolean nativeCurrent(dev.veltraph.client.rotation.DirectRotationPublicationIdentity.Scope expected){return finalNativeLeaf(expected,lifecycle,installation,configSnapshot,inputSnapshot,expected.generation(),expected.epoch(),expected.publicationSerial());}
        private boolean cleanupNativeCurrent(dev.veltraph.client.rotation.DirectRotationPublicationIdentity.Scope expected,boolean allowDisabled){
            if(!allowDisabled)return finalCancellationLeaf(expected,lifecycle,installation,configSnapshot,inputSnapshot);
            if(expected==null||!(expected.root() instanceof dev.veltraph.client.core.ModuleManager root)||!(expected.parent() instanceof dev.veltraph.client.core.Module parent))return false;
            // Only Root's real pending/confirmed original disable can cross the original lifecycle fence.
            return configSnapshot.current()&&inputSnapshot.current()&&installation.current()&&client.isSameThread()
                &&dev.veltraph.client.VeltraphBootstrap.moduleManager()==root&&root.get(parent.id()).orElse(null)==parent&&root.ownCleanupLifecycleCurrent(lifecycle,parent)
                &&dev.veltraph.client.VeltraphBootstrap.config()==expected.configStore()&&config==expected.configStore()&&expected.service()==McRotationService.this
                &&client.player==expected.player()&&client.level==expected.world()&&client.getConnection()==expected.connection()
                &&client.player!=null&&client.level!=null&&client.getConnection()!=null&&client.player.level()==client.level&&client.getCameraEntity()==client.player
                &&requestGeneration==expected.generation()&&McOwnedRotation.epoch(client)==expected.epoch()&&McOwnedRotation.publicationSerial(client)==expected.publicationSerial();
        }
    }
    /** No public constructor, copied record, raw generation rebase, or gameplay permission. */
    public final class SourceAimOutcome {
        private final SourceAimContext context;private final dev.veltraph.client.rotation.DirectRotationPublicationIdentity.Scope after;
        private final DirectPublicationReceipt cleanup,gameplay;
        private SourceAimOutcome(SourceAimContext context,dev.veltraph.client.rotation.DirectRotationPublicationIdentity.Scope after,DirectPublicationReceipt cleanup,DirectPublicationReceipt gameplay){this.context=context;this.after=after;this.cleanup=cleanup;this.gameplay=gameplay;}
        public dev.veltraph.client.rotation.DirectRotationPublicationIdentity.Scope before(){return context.before;}
        public dev.veltraph.client.rotation.DirectRotationPublicationIdentity.Scope after(){return after;}
        public DirectPublicationReceipt priorPublication(){return context.prior;}
        public DirectPublicationReceipt cleanupPublication(){return cleanup;}
        public DirectPublicationReceipt gameplayPublication(){return gameplay;}
        public boolean metadataOnlyRefusal(){return cleanup==null;}
        /** Final stored/native refusal leaf after the consumer's last callback; never recaptures or grants authority. */
        public boolean cleanupNativeCurrent(boolean allowDisabled){
            if(lastSourceAimOutcome!=this||!client.isSameThread()||!context.cleanupNativeCurrent(after,allowDisabled)
                ||lastDirectPublication!=cleanup||request.pending()||awaitingEndpoint!=null||awaitingTicket!=0||requestAuthority!=null||requestConnection!=null
                ||publicationTransition!=null||lastDirectCancellation!=null)return false;
            if(cleanup!=null)return cleanup.proof.after()==after&&cleanup.proof.ticket()==after.publicationSerial();
            return metadataAimTransition(context.before,after)
                &&(context.prior==null||dev.veltraph.client.rotation.DirectRotationPublicationIdentity.current(context.prior.proof,context.before));
        }
        public boolean cleanupCurrent(boolean allowDisabled){
            if(lastSourceAimOutcome!=this||!client.isSameThread()||!context.cleanupNativeCurrent(after,allowDisabled))return false;
            var actual=directCleanupScope(after.parent(),allowDisabled);
            if(lastSourceAimOutcome!=this||!context.cleanupNativeCurrent(after,allowDisabled)||!sourceAimCleanupSame(after,actual,allowDisabled))return false;
            if(cleanup!=null)return lastDirectPublication==cleanup&&dev.veltraph.client.rotation.DirectRotationPublicationIdentity.ownedCleanup(cleanup.proof,actual,allowDisabled);
            return lastDirectPublication==null&&metadataAimTransition(context.before,after)&&!request.pending()&&awaitingEndpoint==null&&awaitingTicket==0&&requestAuthority==null&&requestConnection==null
                &&(context.prior==null||dev.veltraph.client.rotation.DirectRotationPublicationIdentity.current(context.prior.proof,context.before));
        }
    }
    public SourceAimCancellation cancelSourceAimOwned(SourceAimOutcome exact,java.util.function.BooleanSupplier refusalOnlyFence,boolean allowDisabled){
        return cancelSourceAimOwned(exact,refusalOnlyFence,allowDisabled,()->true);
    }
    /** Retires exactly this actual outcome. The cleanup leaf is distinct from the revoked gameplay source. */
    public SourceAimCancellation cancelSourceAimOwned(SourceAimOutcome exact,java.util.function.BooleanSupplier refusalOnlyFence,boolean allowDisabled,FinalWorkflowLeaf cleanupLeaf){
        java.util.Objects.requireNonNull(refusalOnlyFence);java.util.Objects.requireNonNull(cleanupLeaf);
        if(exact==null||!exact.cleanupCurrent(allowDisabled)||!refusalOnlyFence.getAsBoolean()||!cleanupLeaf.current()||!exact.cleanupCurrent(allowDisabled))return null;
        var before=directCleanupScope(exact.after.parent(),allowDisabled);
        if(exact!=lastSourceAimOutcome||before==null||before.generation()==Long.MAX_VALUE||!exact.context.cleanupNativeCurrent(before,allowDisabled)
            ||!sourceAimCleanupSame(exact.after,before,allowDisabled))return null;
        if(!finalWorkflowAllowed(cleanupLeaf,()->exact.context.cleanupNativeCurrent(before,allowDisabled))||exact!=lastSourceAimOutcome
            ||lastDirectPublication!=exact.cleanup||requestGeneration!=before.generation())return null;
        boolean nativeCancelled=exact.cleanup!=null||exact.context.prior!=null;
        if(nativeCancelled)cancelRotation();
        else{
            // First-aim metadata custody cannot authorize cancellation of any unowned native plan.
            requestGeneration++;lastDirectCancellation=null;lastDirectPublication=null;publicationTransition=null;
            request.cancel();awaitingEndpoint=null;awaitingTicket=0;requestAuthority=null;requestConnection=null;owner=null;ownerLevel=null;
            stepped=false;trackingYaw.reset();trackingPitch.reset();lastTrackTick=Integer.MIN_VALUE;
        }
        var after=sourceAimStamp(before);
        if(after.generation()!=before.generation()+1||after.publicationSerial()!=before.publicationSerial()
            ||after.epoch()!=before.epoch()+(nativeCancelled?1:0)||!exact.context.cleanupNativeCurrent(after,allowDisabled))return null;
        var cancellation=new SourceAimCancellation(exact,before,after,nativeCancelled);lastSourceAimCancellation=cancellation;lastSourceAimOutcome=null;
        return cancellation;
    }
    public final class SourceAimCancellation {
        private final SourceAimOutcome outcome;private final dev.veltraph.client.rotation.DirectRotationPublicationIdentity.Scope before,after;private final boolean nativeCancelled;
        private SourceAimCancellation(SourceAimOutcome outcome,dev.veltraph.client.rotation.DirectRotationPublicationIdentity.Scope before,dev.veltraph.client.rotation.DirectRotationPublicationIdentity.Scope after,boolean nativeCancelled){this.outcome=outcome;this.before=before;this.after=after;this.nativeCancelled=nativeCancelled;}
        public SourceAimOutcome outcome(){return outcome;}public dev.veltraph.client.rotation.DirectRotationPublicationIdentity.Scope before(){return before;}
        public dev.veltraph.client.rotation.DirectRotationPublicationIdentity.Scope after(){return after;}public boolean nativeCancelled(){return nativeCancelled;}
        public long actualTicket(){return !nativeCancelled?-1:outcome.cleanup!=null?outcome.cleanup.actualTicket():outcome.context.prior.actualTicket();}
        /** Final stored/native refusal leaf after the consumer's last callback; no owner or provider query. */
        public boolean cleanupNativeCurrent(boolean allowDisabled){
            return lastSourceAimCancellation==this&&client.isSameThread()&&outcome.context.cleanupNativeCurrent(after,allowDisabled)
                &&lastSourceAimOutcome==null&&lastDirectPublication==null&&lastDirectCancellation==null&&publicationTransition==null
                &&!request.pending()&&awaitingEndpoint==null&&awaitingTicket==0&&requestAuthority==null&&requestConnection==null&&owner==null&&ownerLevel==null&&!stepped;
        }
        public boolean cleanupCurrent(boolean allowDisabled){
            if(lastSourceAimCancellation!=this||!outcome.context.cleanupNativeCurrent(after,allowDisabled))return false;
            var actual=directCleanupScope(after.parent(),allowDisabled);
            return lastSourceAimCancellation==this&&outcome.context.cleanupNativeCurrent(after,allowDisabled)&&sourceAimCleanupSame(after,actual,allowDisabled)
                &&lastDirectPublication==null&&!request.pending()&&awaitingEndpoint==null&&awaitingTicket==0&&requestAuthority==null&&requestConnection==null&&owner==null&&ownerLevel==null;
        }
    }

    private DirectPublicationReceipt aimInternal(float yaw,float pitch,java.util.function.BooleanSupplier fence,boolean required,
            dev.veltraph.client.rotation.DirectRotationPublicationIdentity.Scope before,dev.veltraph.client.rotation.DirectRotationPublicationIdentity.Mode mode){
        if(required&&(before==null||fence!=null&&!fence.getAsBoolean()))return null;
        if(before!=null){
            var entered=directScope();boolean same=mode==dev.veltraph.client.rotation.DirectRotationPublicationIdentity.Mode.TRACK
                ?dev.veltraph.client.rotation.DirectRotationPublicationIdentity.preplan(before,entered,dev.veltraph.client.rotation.DirectRotationPublicationIdentity.Mode.AIM)
                :dev.veltraph.client.rotation.DirectRotationPublicationIdentity.same(before,entered);
            if(!same)return null;
        }
        requestGeneration++;lastDirectCancellation=null;publicationTransition=null;lastDirectPublication=null;
        request.cancel();awaitingEndpoint=null;awaitingTicket=0;requestAuthority=null;requestConnection=null;
        var p=player();if(p==null)return null;
        var preplan=directScope();
        if((required||before!=null)&&!dev.veltraph.client.rotation.DirectRotationPublicationIdentity.preplan(before,preplan,mode))return null;
        if(fence!=null&&!fence.getAsBoolean())return null;
        // Fence again after the caller's refusal-only callback; it cannot authorize a changed native scene.
        if((required||before!=null)&&!dev.veltraph.client.rotation.DirectRotationPublicationIdentity.preplan(before,directScope(),mode))return null;
        float bounded=Math.max(-90f,Math.min(90f,pitch));
        var ownCall=farmPlanCall;long actualGeneration=requestGeneration;
        long ticket=observeRootNativePlan(yaw,bounded,()->McOwnedRotation.plan(client,yaw,bounded));
        if(ownCall!=null&&farmPlanCall==ownCall&&!ownCall.revoked)
            rememberFarmNativeTicket(ownCall.scope,ticket,actualGeneration,false);
        var after=directScope();var proof=new dev.veltraph.client.rotation.DirectRotationPublicationIdentity.Proof(before,after,ticket,mode);
        if(dev.veltraph.client.rotation.DirectRotationPublicationIdentity.publication(proof)){
            var published=new DirectPublicationReceipt(proof,yaw,bounded,true);lastDirectPublication=published;return published;
        }
        return null;
    }

    /** Privately minted only from the exact native public call, never from a raw epoch/serial read. */
    public final class DirectPublicationReceipt {
        private final dev.veltraph.client.rotation.DirectRotationPublicationIdentity.Proof proof;private final float yaw,pitch;private final boolean published;
        private DirectPublicationReceipt(dev.veltraph.client.rotation.DirectRotationPublicationIdentity.Proof proof,float yaw,float pitch,boolean published){this.proof=proof;this.yaw=yaw;this.pitch=pitch;this.published=published;}
        public dev.veltraph.client.rotation.DirectRotationPublicationIdentity.Scope before(){return proof.before();}
        public dev.veltraph.client.rotation.DirectRotationPublicationIdentity.Scope after(){return proof.after();}
        public long actualTicket(){return proof.ticket();}public boolean newPublication(){return published;}public boolean sameTickRetained(){return !published;}
        public float yaw(){return yaw;}public float pitch(){return pitch;}
        public boolean current(){return lastDirectPublication==this&&dev.veltraph.client.rotation.DirectRotationPublicationIdentity.current(proof,directScope());}
        public boolean cleanupCurrent(boolean allowDisabled){return directCleanupCurrent(this,allowDisabled);}
    }
    public DirectPublicationReceipt observedDirectPublication(){var receipt=lastDirectPublication;return receipt!=null&&receipt.current()?receipt:null;}
    private dev.veltraph.client.rotation.DirectRotationPublicationIdentity.Scope directScope(){
        if(!client.isSameThread()||!McOwnedRotation.installed(client))return null;
        var root=dev.veltraph.client.VeltraphBootstrap.moduleManager();var authority=McOwnedRotation.authority(client);
        cachedDirectScopeAuthority=root==null?"ORIGINAL_ROOT_NULL":authority==null?"ORIGINAL_AUTHORITY_NULL":authority instanceof dev.veltraph.client.core.Module?"ORIGINAL_REGISTERED_MODULE_TEST_PENDING":authority instanceof dev.veltraph.client.rewarp.FarmMaintenanceLease.Token?"ORIGINAL_AUTHORITY_MAINTENANCE_TOKEN_REJECTED_BY_MODULE_GUARD":"ORIGINAL_AUTHORITY_NON_MODULE";
        if(root==null||!(authority instanceof dev.veltraph.client.core.Module parent)||root.get(parent.id()).orElse(null)!=parent||!parent.isEnabled()
                ||root.inputOwner().orElse(null)!=parent||!dev.veltraph.client.input.ScriptingInputLock.isActive()
                ||!parent.id().equals(dev.veltraph.client.input.ScriptingInputLock.ownerModuleId())||client.player==null||client.level==null||client.getConnection()==null
                ||client.player.level()!=client.level||!client.player.isAlive()||client.isPaused()||client.gui.screen()!=null||client.player.isScoping()
                ||client.getCameraEntity()!=client.player||client.level.tickRateManager().isEntityFrozen(client.player)
                ||client.player.containerMenu!=client.player.inventoryMenu||!client.player.inventoryMenu.getCarried().isEmpty()
                ||dev.veltraph.client.VeltraphBootstrap.config()!=config)return null;
        var revision=config.revision(dev.veltraph.client.config.RotationConfig.ROTATION_SPEED_MULTIPLIER);if(revision.isEmpty())return null;
        return new dev.veltraph.client.rotation.DirectRotationPublicationIdentity.Scope(root,parent,parent,parent,client.player,client.level,client.getConnection(),config,this,requestGeneration,McOwnedRotation.epoch(client),McOwnedRotation.publicationSerial(client),revision.getAsLong());
    }
    /** Actual one-shot ordinary cancellation, never permission obtained from a raw epoch read. */
    public DirectCancellationReceipt cancelDirectOwned(DirectPublicationReceipt exact,java.util.function.BooleanSupplier refusalOnlyFence,boolean allowDisabled){
        java.util.Objects.requireNonNull(refusalOnlyFence);
        if(exact==null||exact!=lastDirectPublication||!exact.cleanupCurrent(allowDisabled)||!refusalOnlyFence.getAsBoolean())return null;
        if(exact!=lastDirectPublication||!exact.cleanupCurrent(allowDisabled))return null;
        var before=directCleanupScope(exact.after().parent(),allowDisabled);
        if(!dev.veltraph.client.rotation.DirectRotationPublicationIdentity.ownedCleanup(exact.proof,before,allowDisabled)||!refusalOnlyFence.getAsBoolean())return null;
        if(exact!=lastDirectPublication||!exact.cleanupCurrent(allowDisabled)||requestGeneration!=before.generation()
                ||McOwnedRotation.epoch(client)!=before.epoch()||McOwnedRotation.publicationSerial(client)!=before.publicationSerial())return null;
        cancelRotation(); // This native call is the sole source of the resulting cancellation transition.
        var after=directCleanupScope(exact.after().parent(),allowDisabled);
        if(!dev.veltraph.client.rotation.DirectRotationPublicationIdentity.cancellation(exact.proof,before,after,allowDisabled))return null;
        var receipt=new DirectCancellationReceipt(exact,before,after,allowDisabled);lastDirectCancellation=receipt;return receipt;
    }
    public DirectCancellationReceipt cancelDirectOwned(DirectPublicationReceipt exact,java.util.function.BooleanSupplier refusalOnlyFence,boolean allowDisabled,FinalWorkflowLeaf finalWorkflowVeto){
        java.util.Objects.requireNonNull(refusalOnlyFence);java.util.Objects.requireNonNull(finalWorkflowVeto);
        if(!client.isSameThread())return null;
        var root=dev.veltraph.client.VeltraphBootstrap.moduleManager();var lifecycle=root==null?null:root.lifecycleSnapshot();
        var installation=McOwnedRotation.installationSnapshot(client);var configSnapshot=storedRotationSnapshot();var inputSnapshot=dev.veltraph.client.input.ScriptingInputLock.controlSnapshot();
        if(lifecycle==null||!lifecycle.known()||installation==null||configSnapshot==null||inputSnapshot==null||!inputSnapshot.active()
            ||exact==null||!(exact.after().parent() instanceof dev.veltraph.client.core.Module parent)||!java.util.Objects.equals(inputSnapshot.ownerModuleId(),parent.id()))return null;
        if(exact==null||exact!=lastDirectPublication||!exact.cleanupCurrent(allowDisabled)||!refusalOnlyFence.getAsBoolean())return null;
        if(exact!=lastDirectPublication||!exact.cleanupCurrent(allowDisabled))return null;
        var before=directCleanupScope(exact.after().parent(),allowDisabled);
        if(!dev.veltraph.client.rotation.DirectRotationPublicationIdentity.ownedCleanup(exact.proof,before,allowDisabled)||!refusalOnlyFence.getAsBoolean())return null;
        if(exact!=lastDirectPublication||!exact.cleanupCurrent(allowDisabled)||requestGeneration!=before.generation()
                ||McOwnedRotation.epoch(client)!=before.epoch()||McOwnedRotation.publicationSerial(client)!=before.publicationSerial())return null;
        if(!finalWorkflowAllowed(finalWorkflowVeto,()->finalCancellationLeaf(before,lifecycle,installation,configSnapshot,inputSnapshot)))return null;
        cancelRotation(); // This native call is the sole source of the resulting cancellation transition.
        var after=directCleanupScope(exact.after().parent(),allowDisabled);
        if(!dev.veltraph.client.rotation.DirectRotationPublicationIdentity.cancellation(exact.proof,before,after,allowDisabled)
                ||!finalWorkflowAllowed(finalWorkflowVeto,()->finalCancellationLeaf(after,lifecycle,installation,configSnapshot,inputSnapshot)))return null;
        var receipt=new DirectCancellationReceipt(exact,before,after,allowDisabled);lastDirectCancellation=receipt;return receipt;
    }
    /** Stored/native cancellation equality; disabled cleanup never runs the gameplay availability checks. */
    private boolean finalCancellationLeaf(dev.veltraph.client.rotation.DirectRotationPublicationIdentity.Scope expected,dev.veltraph.client.core.ModuleManager.LifecycleSnapshot lifecycle,McOwnedRotation.InstallationSnapshot installation,dev.veltraph.client.config.MapConfigService.StoredDoubleSnapshot configSnapshot,dev.veltraph.client.input.ScriptingInputLock.ControlSnapshot inputSnapshot){
        if(expected==null||!(expected.root() instanceof dev.veltraph.client.core.ModuleManager root)||lifecycle==null||installation==null||configSnapshot==null||inputSnapshot==null)return false;
        return configSnapshot.current()&&inputSnapshot.current()&&installation.current()&&client.isSameThread()&&dev.veltraph.client.VeltraphBootstrap.moduleManager()==root&&root.lifecycleCurrent(lifecycle)
            &&dev.veltraph.client.VeltraphBootstrap.config()==expected.configStore()&&config==expected.configStore()&&expected.service()==this
            &&client.player==expected.player()&&client.level==expected.world()&&client.getConnection()==expected.connection()
            &&client.player!=null&&client.level!=null&&client.getConnection()!=null&&client.getCameraEntity()==client.player
            &&requestGeneration==expected.generation()&&McOwnedRotation.epoch(client)==expected.epoch()&&McOwnedRotation.publicationSerial(client)==expected.publicationSerial();
    }
    public final class DirectCancellationReceipt {
        private final DirectPublicationReceipt publication;private final dev.veltraph.client.rotation.DirectRotationPublicationIdentity.Scope before,after;private final boolean absentAllowed;
        private DirectCancellationReceipt(DirectPublicationReceipt publication,dev.veltraph.client.rotation.DirectRotationPublicationIdentity.Scope before,
                dev.veltraph.client.rotation.DirectRotationPublicationIdentity.Scope after,boolean absentAllowed){this.publication=publication;this.before=before;this.after=after;this.absentAllowed=absentAllowed;}
        public DirectPublicationReceipt publication(){return publication;}public dev.veltraph.client.rotation.DirectRotationPublicationIdentity.Scope before(){return before;}
        public dev.veltraph.client.rotation.DirectRotationPublicationIdentity.Scope after(){return after;}public long actualTicket(){return publication.actualTicket();}
        public boolean current(){return lastDirectCancellation==this&&dev.veltraph.client.rotation.DirectRotationPublicationIdentity.cancellationCurrent(after,directCleanupScope(after.parent(),absentAllowed),absentAllowed,true);}
        public boolean cleanupCurrent(boolean allowDisabled){return lastDirectCancellation==this&&dev.veltraph.client.rotation.DirectRotationPublicationIdentity.cancellationCurrent(after,directCleanupScope(after.parent(),allowDisabled&&absentAllowed),allowDisabled&&absentAllowed,false);}
    }
    private dev.veltraph.client.rotation.DirectRotationPublicationIdentity.Scope directCleanupScope(Object expectedParent,boolean allowDisabled){
        if(!client.isSameThread()||!(expectedParent instanceof dev.veltraph.client.core.Module parent))return null;
        var root=dev.veltraph.client.VeltraphBootstrap.moduleManager();
        if(root==null||root.get(parent.id()).orElse(null)!=parent||dev.veltraph.client.VeltraphBootstrap.config()!=config
            ||client.player==null||client.level==null||client.getConnection()==null||client.player.level()!=client.level||client.getCameraEntity()!=client.player)return null;
        var input=root.inputOwner().orElse(null);var camera=McOwnedRotation.authority(client);
        if(!(input==parent&&camera==parent)&&!(allowDisabled&&!parent.isEnabled()&&input==null&&camera==null
                &&dev.veltraph.client.input.ScriptingInputLock.isActive()&&parent.id().equals(dev.veltraph.client.input.ScriptingInputLock.ownerModuleId())))return null;
        var revision=config.revision(dev.veltraph.client.config.RotationConfig.ROTATION_SPEED_MULTIPLIER);if(revision.isEmpty())return null;
        return new dev.veltraph.client.rotation.DirectRotationPublicationIdentity.Scope(root,parent,input,camera,client.player,client.level,client.getConnection(),config,this,requestGeneration,McOwnedRotation.epoch(client),McOwnedRotation.publicationSerial(client),revision.getAsLong());
    }
    private boolean directCleanupCurrent(DirectPublicationReceipt receipt,boolean allowDisabled){
        if(receipt==null||receipt!=lastDirectPublication||!client.isSameThread())return false;
        var after=receipt.after();var root=dev.veltraph.client.VeltraphBootstrap.moduleManager();
        if(root!=after.root()||!(after.parent() instanceof dev.veltraph.client.core.Module parent)||root.get(parent.id()).orElse(null)!=parent
                ||config!=after.configStore()||dev.veltraph.client.VeltraphBootstrap.config()!=config||client.player!=after.player()||client.level!=after.world()
                ||client.getConnection()!=after.connection()||client.getCameraEntity()!=client.player||requestGeneration!=after.generation()
                ||McOwnedRotation.epoch(client)!=after.epoch()||McOwnedRotation.publicationSerial(client)!=receipt.actualTicket())return false;
        var input=root.inputOwner().orElse(null);var camera=McOwnedRotation.authority(client);
        if(input==parent&&camera==parent)return true;
        return allowDisabled&&!parent.isEnabled()&&input==null&&camera==null&&dev.veltraph.client.input.ScriptingInputLock.isActive()
                &&parent.id().equals(dev.veltraph.client.input.ScriptingInputLock.ownerModuleId());
    }

    @Override
    public void lookAt(Vec3 target) {
        LocalPlayer p = player();
        if (p == null) {
            return;
        }
        double dx = target.x() - p.getX();
        double dy = target.y() - p.getEyeY();
        double dz = target.z() - p.getZ();
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        float pitch = (float) (-Math.toDegrees(Math.atan2(dy, horizontal)));
        aim(yaw, pitch);
    }
}
