package dev.veltraph.client.service.mc;

import dev.veltraph.client.observation.unexpected.UnexpectedActions;
import dev.veltraph.client.rotation.OwnedRotationTrajectory;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import java.util.function.Supplier;

/** Canonical actual camera pose. Root supplies sole live authority; mixins only consume that lease. */
public final class McOwnedRotation {
    private static McOwnedRotation installed;
    private final Minecraft client;
    private final Supplier<?> authority;
    private final OwnedRotationTrajectory trajectory=new OwnedRotationTrajectory();
    private long frameApplications;
    private IssuedFarmReturnNativeRetirement lastIssuedReturnRetirement;
    private IssuedReturnRetirementAttempt issuedReturnRetirementAttempt;
    private QueuedScopedPlan queuedWorkflow;
    private QueuedCancellation queuedCancellation;
    private dev.veltraph.client.navigation.mc.LegacyTravelPublicationLease.NormalIdlePermit normalIdle;
    private dev.veltraph.client.VeltraphBootstrap.RootNoOwnerObservation lastNoOwner;
    private NormalRootInputDrop inputDrop;
    private String normalArmDiagnostic="NOT_OBSERVED",normalDropDiagnostic="NOT_OBSERVED",normalCancelDiagnostic="NOT_OBSERVED";
    /** Cached original guard results only. Never resamples provider/permit/token/clock or native scope. */
    public static String cachedNormalIdleDiagnostic(Minecraft client){var next=installed;return next==null||next.client!=client?"INSTALLATION_MISSING":"arm="+next.normalArmDiagnostic+"; drop="+next.normalDropDiagnostic+"; cancel="+next.normalCancelDiagnostic;}
    private boolean idleArmUnavailable(String code){normalArmDiagnostic=code;return false;}
    private GuiAvailabilityRevocation guiAvailability;
    private dev.veltraph.client.VeltraphBootstrap.RootGuiAvailabilityObservation lastGuiAvailability;

    private McOwnedRotation(Minecraft client,Supplier<?> authority) {this.client=client;this.authority=authority;initialMetadata=new NativeMetadataSnapshot(this);knownIdleMetadata=initialMetadata;}
    private final dev.veltraph.client.rotation.NormalIdleInputVersion metadataHistory=new dev.veltraph.client.rotation.NormalIdleInputVersion();
    private NativeMetadataNormalReturn lastMetadataReturn;
    private final NativeMetadataSnapshot initialMetadata;
    private NativeMetadataSnapshot knownIdleMetadata;
    private static final ThreadLocal<NativeMetadataInvocation> METADATA_ENTRY=new ThreadLocal<>();
    /** Read-only actual installation metadata, never a native publishing or source-origin receipt. */
    public static final class NativeMetadataSnapshot {
        private final McOwnedRotation original;private final Object history;private final long epoch,serial;
        private NativeMetadataSnapshot(McOwnedRotation original){this.original=original;history=original.metadataHistory.snapshot();epoch=original.trajectory.epoch();serial=original.trajectory.publicationSerial();}
        public boolean current(){return original.client.isSameThread()&&installed==original&&history!=null&&original.metadataHistory.current((dev.veltraph.client.rotation.NormalIdleInputVersion.Snapshot)history)
            &&original.trajectory.epoch()==epoch&&original.trajectory.publicationSerial()==serial;}
    }
    public static NativeMetadataSnapshot nativeMetadataSnapshot(Minecraft client){
        if(client==null||!client.isSameThread()||!installed(client))return null;
        try{var result=new NativeMetadataSnapshot(installed);return result.current()?result:null;}catch(RuntimeException|Error unavailable){return null;}
    }
    /** Actual native method entry. Its private constructor cannot be replaced by a caller's epoch or owner. */
    public static final class NativeMetadataInvocation {
        private final McOwnedRotation original;private final NativeMetadataSnapshot before;
        private final boolean cancellation;private final float yaw,pitch;private final Thread thread=Thread.currentThread();
        private final NativeMetadataInvocation parent;private final RootNativeCameraPhase.Phase phase;
        private RootNativeHistory.GuiTicket guiSource;private boolean guiCancellation;
        private InstallationIdleOrigin installationIdle;private boolean installationIdleCancellation;
        private McRotationService.RootNativeCameraCall call;private dev.veltraph.client.VeltraphBootstrap.RootNoOwnerObservation idleCause;private boolean changed,returned,revoked,closed;
        private NativeMetadataInvocation(McOwnedRotation original,boolean cancellation,float yaw,float pitch,RootNativeCameraPhase.Phase phase){
            this.original=original;this.cancellation=cancellation;this.yaw=yaw;this.pitch=pitch;this.phase=phase;before=new NativeMetadataSnapshot(original);parent=METADATA_ENTRY.get();
        }
        public Minecraft client(){return original.client;}public boolean cancellation(){return cancellation;}
        public NativeMetadataSnapshot beforeSnapshot(){return before;}public float endpointYaw(){return yaw;}public float endpointPitch(){return pitch;}
        public boolean nativeEntryMatches(McRotationService.RootNativeCameraCall exact){return !closed&&!revoked&&!changed&&call==exact&&exact!=null
            &&METADATA_ENTRY.get()==this&&Thread.currentThread()==thread&&installed==original&&before.current();}
    }
    private static final class InstallationIdleOrigin {
        private final dev.veltraph.client.VeltraphBootstrap.RootOriginalNativeInstallation source;
        private final NativeMetadataSnapshot before;private final Minecraft client;
        private final Object player,world,listener,options,mode;
        private InstallationIdleOrigin(dev.veltraph.client.VeltraphBootstrap.RootOriginalNativeInstallation source,NativeMetadataSnapshot before,Minecraft client){
            this.source=source;this.before=before;this.client=client;player=client.player;world=client.level;listener=client.getConnection();options=client.options;mode=client.gameMode;
        }
        private boolean current(){return source==dev.veltraph.client.VeltraphBootstrap.originalNativeInstallationFor(client)
            &&source.currentNativeNoOwner(client)&&client.player==player&&client.level==world&&client.getConnection()==listener
            &&client.options==options&&client.gameMode==mode;}
    }
    private static void captureInstallationIdleOrigin(NativeMetadataInvocation exact){
        if(exact==null||exact.phase==null||exact.revoked||exact.call!=null||!exact.cancellation)return;
        var source=dev.veltraph.client.VeltraphBootstrap.originalNativeInstallationFor(exact.original.client);
        if(source==null||!source.currentNativeNoOwner(exact.original.client))return;
        var before=exact.original.knownIdleMetadata;
        if(before==null||!before.current())before=RootNativeHistory.terminalMetadataFor(exact.original.client);
        if(before==null||!before.current())return;
        // The selected origin is constructor/known normal-return data, never the fresh invocation snapshot.
        exact.installationIdle=new InstallationIdleOrigin(source,before,exact.original.client);
    }
    /** Privately recorded after one real original trajectory mutation normally returns. */
    public static final class NativeMetadataNormalReturn {
        private final NativeMetadataInvocation invocation;private final NativeMetadataSnapshot after;
        private final boolean cancellation;private final long ticket;
        private NativeMetadataNormalReturn(NativeMetadataInvocation invocation,boolean cancellation,long ticket){this.invocation=invocation;this.cancellation=cancellation;this.ticket=ticket;after=new NativeMetadataSnapshot(invocation.original);}
        public McRotationService.RootNativeCameraCall originalCall(){return invocation.call;}
        public boolean matchesCall(McRotationService.RootNativeCameraCall exact){return exact!=null&&invocation.call==exact&&cancellation==invocation.cancellation;}
        public boolean current(){return invocation.returned&&!invocation.revoked&&invocation.closed&&Thread.currentThread()==invocation.thread
            &&invocation.original.lastMetadataReturn==this&&after.current()&&(cancellation||ticket>0&&ticket==after.serial);}
        public boolean advancedFrom(NativeMetadataSnapshot old){return old!=null&&old.original==invocation.original&&current()
            &&old.epoch==invocation.before.epoch&&old.serial==invocation.before.serial
            &&invocation.original.metadataHistory.advancedOnly((dev.veltraph.client.rotation.NormalIdleInputVersion.Snapshot)old.history,
                (dev.veltraph.client.rotation.NormalIdleInputVersion.Snapshot)after.history,java.util.List.of(invocation));}
        public NativeMetadataSnapshot afterSnapshot(){return after;}public long ticket(){return ticket;}public long epoch(){return after.epoch;}public boolean cancellation(){return cancellation;}
        RootNativeHistory.GuiTicket originalGui(){return invocation.guiSource;}
        boolean guiSourceCurrent(){return invocation.guiCancellation&&invocation.phase!=null&&invocation.phase.normalCurrent(invocation.original.client)
            &&invocation.phase.matches(invocation)&&invocation.call==null&&cancellation&&current()
            &&RootNativeHistory.guiMetadataAffiliationCurrent(invocation.guiSource,invocation.original.client);}
        private boolean installationIdleSourceCurrent(){return invocation.installationIdleCancellation&&invocation.installationIdle!=null
            &&invocation.phase!=null&&invocation.phase.normalCurrent(invocation.original.client)&&invocation.phase.matches(invocation)
            &&invocation.call==null&&cancellation&&current()&&invocation.installationIdle.current();}
        private boolean idleSourceCurrent(){return invocation.phase!=null&&invocation.phase.normalCurrent(invocation.original.client)&&invocation.phase.matches(invocation)
            &&invocation.call==null&&cancellation&&invocation.idleCause!=null&&invocation.idleCause.current()&&current();}
    }
    /** Lookup only. A generic call or metadata read cannot mint canonical service normal return. */
    public static NativeMetadataNormalReturn nativeMetadataReturnFor(McRotationService.RootNativeCameraCall exact){
        if(exact==null)return null;var next=installed;var returned=next==null?null:next.lastMetadataReturn;
        return returned!=null&&returned.matchesCall(exact)&&returned.current()?returned:null;
    }
    /** Native metadata originated at the actual original installation, or its exact last SDK no-owner cancellation. */
    public static NativeMetadataSnapshot knownIdleMetadataSnapshot(Minecraft client){
        if(client==null||!client.isSameThread()||!installed(client)||!McInputService.canonicalFarmRootCurrent(client))return null;
        var original=dev.veltraph.client.VeltraphBootstrap.originalNativeInstallationFor(client);
        if(original==null||!original.currentNativeNoOwner(client))return null;
        var next=installed;var terminal=RootNativeHistory.terminalMetadataFor(client);
        return next.knownIdleMetadata!=null&&next.knownIdleMetadata.current()?next.knownIdleMetadata:terminal;
    }
    /** Existing authentic SDK idle return only. Never captures or creates origin evidence at lookup. */
    public static NativeMetadataNormalReturn peekNativeIdleMetadataReturn(Minecraft client){
        try{var next=client==null||!installed(client)?null:installed;var actual=next==null?null:next.lastMetadataReturn;
            return actual!=null&&actual.idleSourceCurrent()?actual:null;}
        catch(RuntimeException|Error unavailable){return null;}
    }
    private static NativeMetadataInvocation beginMetadata(Minecraft client,boolean cancellation,float yaw,float pitch,RootNativeCameraPhase.Phase phase){
        if(!installed(client))return null;
        try {
            var next=new NativeMetadataInvocation(installed,cancellation,yaw,pitch,phase);METADATA_ENTRY.set(next);
            next.call=McRotationService.rootNativeCameraCall(client,cancellation);
            if(next.call!=null&&!next.call.claimNativeInvocation(next))next.call=null;
            if(phase!=null&&!phase.claim(next))next.revoked=true;
            if(phase!=null&&!next.revoked&&next.call==null&&cancellation)next.guiSource=RootNativeHistory.retainedGuiForNativePhase(client);
            captureInstallationIdleOrigin(next);
            return next;
        }catch(RuntimeException|Error unavailable){var next=METADATA_ENTRY.get();if(next!=null){next.revoked=true;METADATA_ENTRY.set(next.parent);}return null;}
    }
    private static void finishMetadata(NativeMetadataInvocation exact){
        if(exact==null)return;
        try {exact.closed=true;if(METADATA_ENTRY.get()==exact)METADATA_ENTRY.set(exact.parent);else exact.revoked=true;}
        catch(RuntimeException|Error unavailable){exact.revoked=true;}
    }
    private boolean beginMetadataMutation(NativeMetadataInvocation exact){
        try {if(exact!=null){if(exact.changed||exact.original!=this||METADATA_ENTRY.get()!=exact)exact.revoked=true;exact.changed=true;}
            metadataHistory.begin(exact,client.isSameThread());return true;
        }catch(RuntimeException|Error unavailable){metadataHistory.invalidate();if(exact!=null)exact.revoked=true;return false;}
    }
    private void completeMetadataMutation(NativeMetadataInvocation exact,boolean observing,boolean normal,boolean cancellation,long ticket){
        try {if(observing)metadataHistory.end(false);if(exact==null||!normal||exact.revoked||exact.original!=this)return;
            var returned=new NativeMetadataNormalReturn(exact,cancellation,ticket);lastMetadataReturn=returned;
            if(poseLineage!=null&&!cancellation&&poseLineage.ticket==ticket)poseLineage.cameraOrigin=returned;
        }catch(RuntimeException|Error unavailable){metadataHistory.invalidate();if(exact!=null)exact.revoked=true;}
    }
    private long planObservedMetadata(NativeMetadataInvocation exact,OwnedRotationTrajectory.Scope scope,long tick,OwnedRotationTrajectory.Pose actual,OwnedRotationTrajectory.Pose requested){
        boolean observing=beginMetadataMutation(exact);long ticket=-1;boolean normal=false;
        try{ticket=trajectory.plan(scope,tick,actual,requested);normal=true;return ticket;}
        finally{completeMetadataMutation(exact,observing,normal,false,ticket);}
    }
    private void cancelObservedMetadata(){
        var exact=METADATA_ENTRY.get();boolean observing=beginMetadataMutation(exact);boolean normal=false;
        try{trajectory.cancel();normal=true;}finally{completeMetadataMutation(exact,observing,normal,true,0);}
    }
    private static void recordInstallationIdleCancellation(NativeMetadataInvocation exact){
        try{if(exact!=null&&METADATA_ENTRY.get()==exact&&!exact.changed&&!exact.revoked&&exact.installationIdle!=null
                &&exact.installationIdle.before.current()&&exact.installationIdle.current())exact.installationIdleCancellation=true;}
        catch(RuntimeException|Error unavailable){if(exact!=null)exact.revoked=true;}
    }
    private static void recordIdleCause(NativeMetadataInvocation exact,dev.veltraph.client.VeltraphBootstrap.RootNoOwnerObservation cause){
        try{if(exact!=null&&exact.phase!=null&&!exact.changed&&!exact.revoked&&METADATA_ENTRY.get()==exact&&cause!=null&&cause.current())exact.idleCause=cause;}
        catch(RuntimeException|Error unavailable){if(exact!=null)exact.revoked=true;}
    }
    private static void metadataApiReturned(NativeMetadataInvocation exact){if(exact!=null)exact.returned=true;}
    static void nativePhaseReturned(RootNativeCameraPhase.Phase phase){
        var next=installed;var actual=next==null?null:next.lastMetadataReturn;
        try{if(actual!=null&&actual.installationIdleSourceCurrent()&&actual.advancedFrom(actual.invocation.installationIdle.before))
                next.knownIdleMetadata=actual.afterSnapshot();
            if(actual!=null&&actual.guiSourceCurrent())RootNativeHistory.normalGuiCameraReturned(actual);
            else if(actual!=null&&actual.idleSourceCurrent())RootNativeHistory.normalIdleCameraReturned(actual);}
        catch(RuntimeException|Error unavailable){/* Passive native phase observations never alter the original callback. */}
    }

    /** No provider/scope/cancel/tick calls: exact already-empty original Root trajectory only. */
    public static final class LogicalIdleObservation {
        private final RootInstallation root;private final long epoch,serial;
        private LogicalIdleObservation(RootInstallation root,long epoch,long serial){this.root=root;this.epoch=epoch;this.serial=serial;}
        public boolean current(){return root.current(root.client,root.provider)&&root.original.trajectory.epoch()==epoch
            &&root.original.trajectory.publicationSerial()==serial&&root.original.trajectory.emptyAt(epoch,serial);}
    }
    /** Root registration. Temporary private fixtures can close the returned installation to restore Root. */
    public static AutoCloseable install(Minecraft client,Supplier<?> authority) {
        java.util.Objects.requireNonNull(client);var prior=installed;if(prior!=null)prior.trajectory.cancel();
        var next=new McOwnedRotation(client,java.util.Objects.requireNonNull(authority));installed=next;
        return ()->{requireThread(client);if(installed==next){next.trajectory.cancel();installed=prior;}};
    }
    /** Actual original Root installation provenance. Inert capture is legal during constructor-time bootstrap; no publication permission. */
    public static final class RootInstallation {
        private final McOwnedRotation original;private final Minecraft client;private final dev.veltraph.client.rotation.RootInstallationIdentity.State captured;
        private final dev.veltraph.client.VeltraphBootstrap.RootRotationAuthorityProvider provider;
        private RootInstallation(McOwnedRotation original,Minecraft client,dev.veltraph.client.VeltraphBootstrap.RootRotationAuthorityProvider provider){this.original=original;this.client=client;this.provider=provider;captured=new dev.veltraph.client.rotation.RootInstallationIdentity.State(original,client,provider,false);}
        public LogicalIdleObservation observeLogicalIdle(){
            if(!current(client,provider))return null;long epoch=original.trajectory.epoch(),serial=original.trajectory.publicationSerial();
            if(original.trajectory.controlSnapshot()!=null||!original.trajectory.emptyAt(epoch,serial))return null;
            var observed=new LogicalIdleObservation(this,epoch,serial);return observed.current()?observed:null;
        }
        /** Actual native phase only. Never captures/rebases a different or later installation. */
        public boolean current(Minecraft actualClient,dev.veltraph.client.VeltraphBootstrap.RootRotationAuthorityProvider actualProvider){
            return actualClient==client&&actualProvider==provider&&dev.veltraph.client.rotation.RootInstallationIdentity.sameOriginal(captured,new dev.veltraph.client.rotation.RootInstallationIdentity.State(installed,actualClient,actualProvider,client.isSameThread()))
                &&original.client==client&&original.authority==provider;
        }
    }
    /** Bootstrap's genuine private provider receives the exact local installation created here, not a later global getter. */
    public static RootInstallation installRoot(Minecraft client,dev.veltraph.client.VeltraphBootstrap.RootRotationAuthorityProvider provider){
        java.util.Objects.requireNonNull(client);java.util.Objects.requireNonNull(provider);
        var prior=installed;if(prior!=null)prior.trajectory.cancel();
        var next=new McOwnedRotation(client,provider);installed=next;
        return new RootInstallation(next,client,provider);
    }
    public static boolean installed(Minecraft client) {return installed!=null&&installed.client==client;}
    public static Object authority(Minecraft client) {return installed(client)?installed.authority.get():null;}
    /** Publish an endpoint, never a future actual pose. Returns a positive receipt when cadence is installed. */
    public static long plan(Minecraft client,float yaw,float pitch) {
        requireThread(client);
        var metadataInvocation=beginMetadata(client,false,yaw,pitch,null);
        try {
        var invokedFarm=McFarmOwnedCleanup.farmingTickFarm();
        if(invokedFarm!=null&&!farmToolCurrent(client,invokedFarm))return -1;
        var pose=new OwnedRotationTrajectory.Pose(yaw,pitch);
        if(client.player==null)return -1;
        if(!installed(client)){apply(client,pose);return 0;} // Standalone adapter/component compatibility.
        var next=installed;var poseHistory=PoseHistory.capture(client);if(!next.directionalHistoryCurrent())return -1;var scope=next.scope();
        if(scope==null){next.cancelObserved("plan.scope-null");return -1;}
        if(!dev.veltraph.client.mining.commission.CommissionModule.nativeTravelCurrent(client,scope.owner())||!farmToolCurrent(client,scope.owner()))return -1;
        next.guiAvailability=null;if(!next.directionalHistoryCurrent())return -1;long ticket=next.planObservedMetadata(metadataInvocation,scope,client.player.tickCount,actual(client),pose);next.recordedPlan(scope,ticket,poseHistory);metadataApiReturned(metadataInvocation);return ticket;
    
        }finally{finishMetadata(metadataInvocation);}
    }
    /** Authentic installation identity only; it grants no publication permission. */
    public static final class InstallationSnapshot {
        private final Minecraft client;private final McOwnedRotation installation;
        private InstallationSnapshot(Minecraft client,McOwnedRotation installation){this.client=client;this.installation=installation;}
        public boolean current(){return client.isSameThread()&&installed==installation&&installation.client==client;}
    }
    public static InstallationSnapshot installationSnapshot(Minecraft client){requireThread(client);return installed(client)?new InstallationSnapshot(client,installed):null;}
    /** Scoped publication: caller veto grants no authority and follows the actual native scope supplier. */
    public static long planScoped(Minecraft client,float yaw,float pitch,OwnedRotationTrajectory.Scope expectedScope,java.util.function.BooleanSupplier nativeLeaf,McRotationService.FinalWorkflowLeaf finalWorkflowVeto){
        requireThread(client);
        var metadataInvocation=beginMetadata(client,false,yaw,pitch,null);
        try {java.util.Objects.requireNonNull(expectedScope);java.util.Objects.requireNonNull(nativeLeaf);java.util.Objects.requireNonNull(finalWorkflowVeto);
        var pose=new OwnedRotationTrajectory.Pose(yaw,pitch);if(client.player==null||!installed(client))return -1;
        var next=installed;var poseHistory=PoseHistory.capture(client);if(!next.directionalHistoryCurrent())return -1;var scope=next.scope(); // Actual authority provider is evaluated before the final workflow read.
        if(scope==null||scope.owner()!=expectedScope.owner()||scope.player()!=expectedScope.player()||scope.world()!=expectedScope.world()||scope.connection()!=expectedScope.connection())return -1;
        if(!McRotationService.finalWorkflowAllowed(finalWorkflowVeto,nativeLeaf)||installed!=next)return -1;
        if(!dev.veltraph.client.mining.commission.CommissionModule.nativeTravelCurrent(client,scope.owner())||!farmToolCurrent(client,scope.owner()))return -1;
        next.guiAvailability=null;if(!next.directionalHistoryCurrent())return -1;long ticket=next.planObservedMetadata(metadataInvocation,scope,client.player.tickCount,actual(client),pose);next.recordedPlan(scope,ticket,poseHistory);metadataApiReturned(metadataInvocation);return ticket;
    
        }finally{finishMetadata(metadataInvocation);}
    }
    /** Read-only admission around one genuine provider call; never validates/cancels a reentrant publisher. */
    public static QueuedAdmissionObservation observeQueuedAdmission(Minecraft client,OwnedRotationTrajectory.Scope expectedScope){
        requireThread(client);java.util.Objects.requireNonNull(expectedScope);
        if(!installed(client)||client.player==null)return null;
        var next=installed;
        if(!(next.authority instanceof dev.veltraph.client.VeltraphBootstrap.RootRotationAuthorityProvider provider)||!provider.registeredCurrent()
                ||!nativeSceneCurrent(next,expectedScope))return null;
        var originalControl=next.trajectory.controlSnapshot();
        long epoch=next.trajectory.epoch(),serial=next.trajectory.publicationSerial();
        int tick=client.player.tickCount;var pose=actual(client);
        if(originalControl==null?!next.trajectory.emptyAt(epoch,serial):!originalControl.current()||!originalControl.poseCurrent(pose))return null;
        var result=new QueuedAdmissionObservation(next,provider,expectedScope,originalControl,epoch,serial,tick,pose);
        // Sole actual provider read; the captured observation is returned only after genuine scope match and final native check.
        var observed=dev.veltraph.client.rotation.QueuedRotationAdmissionFence.observe(next::scope,scope->sameScope(scope,expectedScope),result::nativeCurrent);
        return observed==null?null:result;
    }
    /** Privately minted read-only original native observation, not a publication or cancellation receipt. */
    public static final class QueuedAdmissionObservation {
        private final McOwnedRotation installation;
        private final dev.veltraph.client.VeltraphBootstrap.RootRotationAuthorityProvider provider;
        private final OwnedRotationTrajectory.Scope scope;private final OwnedRotationTrajectory.ControlSnapshot control;
        private final long epoch,serial;private final int tick;private final OwnedRotationTrajectory.Pose pose;
        private QueuedAdmissionObservation(McOwnedRotation installation,dev.veltraph.client.VeltraphBootstrap.RootRotationAuthorityProvider provider,
                OwnedRotationTrajectory.Scope scope,OwnedRotationTrajectory.ControlSnapshot control,long epoch,long serial,int tick,OwnedRotationTrajectory.Pose pose){
            this.installation=installation;this.provider=provider;this.scope=scope;this.control=control;this.epoch=epoch;this.serial=serial;this.tick=tick;this.pose=pose;
        }
        /** No owner supplier, Commission, workflow callback, trajectory advance or cancellation. */
        public boolean nativeCurrent(){
            var c=installation.client;
            return c.isSameThread()&&installed==installation&&installation.authority==provider&&provider.registeredCurrent()
                &&nativeSceneCurrent(installation,scope)&&c.player.tickCount==tick&&pose.equals(actual(c))
                &&installation.trajectory.epoch()==epoch&&installation.trajectory.publicationSerial()==serial
                &&(control==null?installation.trajectory.emptyAt(epoch,serial):control.current()&&control.poseCurrent(actual(c)));
        }
    }
    /** Actual queued publication, with refusal leaves retained through genuine tick/frame application. */
    public static QueuedScopedPlan planQueuedScoped(Minecraft client,float yaw,float pitch,OwnedRotationTrajectory.Scope expectedScope,
            java.util.function.BooleanSupplier nativeLeaf,McRotationService.FinalWorkflowLeaf workflow){
        requireThread(client);
        var metadataInvocation=beginMetadata(client,false,yaw,pitch,null);
        try {java.util.Objects.requireNonNull(expectedScope);java.util.Objects.requireNonNull(nativeLeaf);java.util.Objects.requireNonNull(workflow);
        var pose=new OwnedRotationTrajectory.Pose(yaw,pitch);
        if(client.player==null||!installed(client))return null;
        var next=installed;var poseHistory=PoseHistory.capture(client);if(!next.directionalHistoryCurrent())return null;
        if(!(next.authority instanceof dev.veltraph.client.VeltraphBootstrap.RootRotationAuthorityProvider provider)||!provider.registeredCurrent())return null;
        long beforeSerial=next.trajectory.publicationSerial(),beforeEpoch=next.trajectory.epoch();
        int nativeTick=client.player.tickCount;var originalPose=actual(client);
        var scope=next.scope();
        if(!sameScope(scope,expectedScope))return null;
        if(!dev.veltraph.client.mining.commission.CommissionModule.nativeTravelCurrent(client,scope.owner()))return null;
        // Provider and Commission callbacks have finished. Unknown leaves can only refuse.
        if(!dev.veltraph.client.rotation.QueuedRotationWorkflowFence.allowed(workflow::current,nativeLeaf,
                ()->installed==next&&next.authority==provider&&provider.registeredCurrent()&&nativeSceneCurrent(next,scope)
                    &&client.player.tickCount==nativeTick&&samePose(originalPose,actual(client))
                    &&next.trajectory.publicationSerial()==beforeSerial&&next.trajectory.epoch()==beforeEpoch))return null;
        next.guiAvailability=null;
        if(!next.directionalHistoryCurrent())return null;long ticket=next.planObservedMetadata(metadataInvocation,scope,nativeTick,originalPose,pose);next.recordedPlan(scope,ticket,poseHistory);
        var control=next.trajectory.controlSnapshot();
        if(ticket<=0||control==null||control.ticket()!=ticket||!sameScope(control.scope(),scope))return null;
        var result=new QueuedScopedPlan(next,provider,control.scope(),ticket,control.epoch(),nativeLeaf,workflow);
        next.queuedWorkflow=result;next.queuedCancellation=null;
        metadataApiReturned(metadataInvocation);return result;
    
        }finally{finishMetadata(metadataInvocation);}
    }
    /** Privately minted by the genuine plan above. Read-only receipt, never standalone authority. */
    public static final class QueuedScopedPlan {
        private final McOwnedRotation installation;
        private final dev.veltraph.client.VeltraphBootstrap.RootRotationAuthorityProvider provider;
        private final OwnedRotationTrajectory.Scope scope;private final long ticket,epoch;
        private final java.util.function.BooleanSupplier nativeLeaf;private final McRotationService.FinalWorkflowLeaf workflow;
        private QueuedScopedPlan(McOwnedRotation installation,dev.veltraph.client.VeltraphBootstrap.RootRotationAuthorityProvider provider,
                OwnedRotationTrajectory.Scope scope,long ticket,long epoch,java.util.function.BooleanSupplier nativeLeaf,McRotationService.FinalWorkflowLeaf workflow){
            this.installation=installation;this.provider=provider;this.scope=scope;this.ticket=ticket;this.epoch=epoch;this.nativeLeaf=nativeLeaf;this.workflow=workflow;
        }
        public long ticket(){return ticket;}public long epoch(){return epoch;}
        /** Stored/native checks only: no owner supplier, Commission, workflow callback, advance or cancellation. */
        public boolean nativeCurrent(){
            return cleanupNativeCurrent()&&nativeSceneCurrent(installation,scope);
        }
        private boolean storedPlanCurrent(){
            var control=installation.trajectory.controlSnapshot();
            return installation.client.isSameThread()&&installed==installation&&installation.queuedWorkflow==this
                &&control!=null&&control.scope()==scope&&control.ticket()==ticket&&control.epoch()==epoch&&control.current();
        }
        /** Exact private plan/PWC/provider identity only; unhealthy GUI or player state never becomes new authority. */
        public boolean cleanupNativeCurrent(){
            var c=installation.client;
            return storedPlanCurrent()&&installation.authority==provider&&provider.registeredCurrent()
                &&c.player==scope.player()&&c.level==scope.world()&&c.getConnection()==scope.connection();
        }
        /** Actual consumed/endpoint receipt and physical pose, without invoking the authority supplier. */
        public boolean completed(){
            if(!nativeCurrent()||!installation.trajectory.completed(scope,ticket))return false;
            var c=installation.client;var read=installation.trajectory.readQueued(scope,c.player.tickCount,actual(c),ticket,epoch);
            return read!=null&&samePose(read.endpoint(),actual(c));
        }
        private boolean allowed(){return dev.veltraph.client.rotation.QueuedRotationWorkflowFence.allowed(workflow::current,nativeLeaf,this::nativeCurrent);}
    }
    /** Cancels only a genuine original queued plan; cleanup callbacks confer refusal, never authority. */
    public static QueuedCancellation cancelQueuedScoped(QueuedScopedPlan exact,McRotationService.FinalWorkflowLeaf cleanupWorkflow,
            java.util.function.BooleanSupplier cleanupNativeLeaf){
        java.util.Objects.requireNonNull(cleanupWorkflow);java.util.Objects.requireNonNull(cleanupNativeLeaf);
        if(exact==null)return null;
        var metadataInvocation=beginMetadata(exact.installation.client,true,Float.NaN,Float.NaN,null);
        try{
            var returned=cancelQueuedScopedOriginal(exact,cleanupWorkflow,cleanupNativeLeaf);
            if(returned!=null)metadataApiReturned(metadataInvocation);
            return returned;
        }finally{finishMetadata(metadataInvocation);}
    }
    private static QueuedCancellation cancelQueuedScopedOriginal(QueuedScopedPlan exact,McRotationService.FinalWorkflowLeaf cleanupWorkflow,
            java.util.function.BooleanSupplier cleanupNativeLeaf){
        java.util.Objects.requireNonNull(cleanupWorkflow);java.util.Objects.requireNonNull(cleanupNativeLeaf);
        if(exact==null||!exact.cleanupNativeCurrent()||exact.epoch==Long.MAX_VALUE)return null;
        var next=exact.installation;
        // S39 cancelObserved has no observer callback before trajectory.cancel. Its original
        // diagnostic snapshot is callback-free; all caller callbacks finish before this last leaf.
        if(!dev.veltraph.client.rotation.QueuedRotationCancellationFence.run(new dev.veltraph.client.rotation.QueuedRotationCancellationFence.Effects(){
            public boolean beforeCancel(){return dev.veltraph.client.rotation.QueuedRotationWorkflowFence.allowed(cleanupWorkflow::current,cleanupNativeLeaf,exact::cleanupNativeCurrent);}
            public void cancel(){next.cancelObserved("queued.scoped.cancel");}
            public boolean afterCancel(){return installed==next&&next.queuedWorkflow==exact&&next.authority==exact.provider&&exact.provider.registeredCurrent()
                &&next.client.player==exact.scope.player()&&next.client.level==exact.scope.world()&&next.client.getConnection()==exact.scope.connection()
                &&next.trajectory.epoch()==exact.epoch+1&&next.trajectory.emptyAt(exact.epoch+1,exact.ticket);}
        }))return null;
        var result=new QueuedCancellation(exact,exact.epoch+1);next.queuedCancellation=result;return result;
    }
    /** Read-only proof of the actual native cancellation; raw epoch values cannot construct it. */
    public static final class QueuedCancellation {
        private final QueuedScopedPlan original;private final long after;
        private QueuedCancellation(QueuedScopedPlan original,long after){this.original=original;this.after=after;}
        public QueuedScopedPlan original(){return original;}
        public long beforeEpoch(){return original.epoch;}public long afterEpoch(){return after;}public long publicationSerial(){return original.ticket;}public long serial(){return original.ticket;}
        public boolean nativeCurrent(){return cleanupNativeCurrent();}
        public boolean cleanupNativeCurrent(){
            var next=original.installation;var c=next.client;
            return c.isSameThread()&&installed==next&&next.queuedCancellation==this&&next.queuedWorkflow==original&&next.authority==original.provider
                &&original.provider.registeredCurrent()&&c.player==original.scope.player()&&c.level==original.scope.world()&&c.getConnection()==original.scope.connection()
                &&after==original.epoch+1&&next.trajectory.emptyAt(after,original.ticket);
        }
    }
    private static boolean sameScope(OwnedRotationTrajectory.Scope a,OwnedRotationTrajectory.Scope b){
        return a!=null&&b!=null&&a.owner()==b.owner()&&a.player()==b.player()&&a.world()==b.world()&&a.connection()==b.connection();
    }
    private static boolean samePose(OwnedRotationTrajectory.Pose a,OwnedRotationTrajectory.Pose b){
        return Math.abs(dev.veltraph.client.rotation.SourceRotationStepper.wrap(a.yaw()-b.yaw()))<.001&&Math.abs(a.pitch()-b.pitch())<.001;
    }
    /** Negative-only selected-item fact; all original native ownership and source guards remain required. */
    private static boolean farmToolCurrent(Minecraft client,Object owner) {
        if(!(owner instanceof dev.veltraph.client.farming.module.FarmModule farm))return true;
        var control=dev.veltraph.client.input.ScriptingInputLock.controlSnapshot();
        return control!=null&&control.active()&&farm.id().equals(control.ownerModuleId())&&control.current()
                &&farm.nativeSelectedFarmingToolCurrent(client);
    }
    private static boolean nativeSceneCurrent(McOwnedRotation installation,OwnedRotationTrajectory.Scope scope){
        var c=installation.client;var p=c.player;
        return installed==installation&&p==scope.player()&&c.level==scope.world()&&c.getConnection()==scope.connection()
            &&p!=null&&c.level!=null&&p.level()==c.level&&p.isAlive()&&!c.isPaused()&&!c.level.tickRateManager().isEntityFrozen(p)&&farmToolCurrent(c,scope.owner())
            &&c.gui.screen()==null&&!p.isScoping()&&c.getCameraEntity()==p;
    }
    /** Capture before all provider, GUI, Commission, delta and observer callbacks. */
    private static QueuedScopedPlan captureQueuedWorkflow(McOwnedRotation installation){
        var guard=installation.queuedWorkflow;
        return guard!=null&&guard.storedPlanCurrent()?guard:null;
    }
    private static boolean applyQueuedWorkflow(McOwnedRotation installation,QueuedScopedPlan guard,boolean tickHead,DeltaTracker delta){
        var c=installation.client;var control=installation.trajectory.controlSnapshot();
        // Do not cancel when a callback replaces ownership or publishes a newer ticket.
        var scope=installation.scope();
        if(!sameScope(scope,guard.scope)||!guard.nativeCurrent())return false;
        if(!dev.veltraph.client.mining.commission.CommissionModule.nativeTravelCurrent(c,scope.owner())||!guard.nativeCurrent())return false;
        int nativeTick=c.player.tickCount;
        float partial=tickHead?0:delta.getGameTimeDeltaPartialTick(true);
        if(!tickHead&&(!dev.veltraph.client.mining.commission.CommissionModule.nativeTravelCurrent(c,scope.owner())||!guard.nativeCurrent()))return false;
        if(control==null||!guard.allowed()||!control.current()||!control.poseCurrent(actual(c)))return false;
        var preview=tickHead?installation.trajectory.previewBeforeTick(scope,nativeTick,actual(c)):installation.trajectory.previewFrame(scope,nativeTick,partial,actual(c));
        if(preview==null)return false;var proposed=preview.application();
        return dev.veltraph.client.rotation.CollectionCameraApplicationFence.run(new dev.veltraph.client.rotation.CollectionCameraApplicationFence.Effects(){
            public boolean beforeObserver(){return guard.allowed()&&c.player.tickCount==nativeTick&&preview.current(actual(c));}
            public void observe(){UnexpectedActions.rotation(c,proposed.pose().yaw(),proposed.pose().pitch());}
            public boolean beforeAdvance(){return guard.allowed()&&c.player.tickCount==nativeTick&&preview.current(actual(c));}
            public java.util.Optional<OwnedRotationTrajectory.Application> advance(){return tickHead?installation.trajectory.beforeTick(scope,nativeTick,actual(c)):installation.trajectory.frame(scope,nativeTick,partial,actual(c));}
            public boolean beforeApply(OwnedRotationTrajectory.Application a){return a.ticket()==guard.ticket&&a.pose().equals(proposed.pose())&&guard.allowed()&&c.player.tickCount==nativeTick&&control.poseCurrent(actual(c));}
            public boolean apply(OwnedRotationTrajectory.Application a){
                // All callback-capable leaves have finished; exact installation/ticket/scope and pose are read again.
                var p=c.player;if(!guard.nativeCurrent()||p==null||!control.poseCurrent(actual(c)))return false;
                var attempt=installation.beginAppliedPose(scope,a);if(!installation.directionalHistoryCurrent())return false;float oldPitch=p.getXRot();nativePose(attempt,p,true,a.pose().yaw(),()->p.setYRot(a.pose().yaw()));
                if(!guard.nativeCurrent()||c.player!=p||p.getYRot()!=a.pose().yaw()||p.getXRot()!=oldPitch)return false;
                nativePose(attempt,p,false,a.pose().pitch(),()->p.setXRot(a.pose().pitch()));
                if(!guard.nativeCurrent()||c.player!=p||p.getYRot()!=a.pose().yaw()||p.getXRot()!=a.pose().pitch())return false;
                p.yRotO=p.getYRot();p.xRotO=p.getXRot();if(attempt!=null)attempt.complete();return true;
            }
        });
    }
    public static void planYaw(Minecraft client,float yaw) {plan(client,yaw,requestedPitch(client));}
    public static void planPitch(Minecraft client,float pitch) {plan(client,requestedYaw(client),pitch);}
    public static float requestedYaw(Minecraft client) {return requested(client).yaw();}
    public static float requestedPitch(Minecraft client) {return requested(client).pitch();}
    private static OwnedRotationTrajectory.Pose requested(Minecraft client) {
        if(client.player==null)return new OwnedRotationTrajectory.Pose(0,0);
        if(!installed(client))return actual(client);
        var next=installed;var scope=next.scope();
        return scope==null?actual(client):next.trajectory.requested(scope,client.player.tickCount,actual(client));
    }
    public static void validate(Minecraft client) {
        requireThread(client);if(!installed(client))return;var next=installed;var scope=next.scope();
        if(scope==null){next.cancelObserved("validate.scope-null");return;}
        next.trajectory.validate(scope,client.player.tickCount,actual(client));
    }
    /** Read-only latest actual plan ticket, including a consumed endpoint. No gameplay authority. */
    public static long publicationSerial(Minecraft client){return installed(client)?installed.trajectory.publicationSerial():0;}
    public static long epoch(Minecraft client) {return installed(client)?installed.trajectory.epoch():0;}
    private PosePlanLineage poseLineage;
    private static final class PoseHistory {
        final Object player;final OwnedPoseHistoryCarrier carrier;final Object snapshot;
        private PoseHistory(Object player,OwnedPoseHistoryCarrier carrier,Object snapshot){this.player=player;this.carrier=carrier;this.snapshot=snapshot;}
        static PoseHistory capture(Minecraft c){if(c==null||!c.isSameThread()||c.player==null||c.player.getClass()!=net.minecraft.client.player.LocalPlayer.class||!((Object)c.player instanceof OwnedPoseHistoryCarrier carrier))return null;
            var snapshot=carrier.ownedPoseHistorySnapshot();return snapshot==null?null:new PoseHistory(c.player,carrier,snapshot);}
        boolean current(Minecraft c){return c.player==player&&carrier.ownedPoseHistoryCurrent(snapshot);}
    }
    private final class PosePlanLineage {
        final OwnedRotationTrajectory.Scope scope;final long ticket,epoch;PoseHistory actual;boolean directional;NativeMetadataNormalReturn cameraOrigin;
        PosePlanLineage(OwnedRotationTrajectory.Scope scope,long ticket,long epoch,PoseHistory actual){this.scope=scope;this.ticket=ticket;this.epoch=epoch;this.actual=actual;}
        boolean matchesControl(){var c=trajectory.controlSnapshot();return c!=null&&c.ticket()==ticket&&c.epoch()==epoch&&sameScope(c.scope(),scope);}
        boolean current(){return actual!=null&&actual.current(client)&&matchesControl();}
    }
    private boolean directionalHistoryCurrent(){return poseLineage==null||!poseLineage.directional||!poseLineage.matchesControl()||poseLineage.current();}
    private void recordedPlan(OwnedRotationTrajectory.Scope scope,long ticket,PoseHistory original){
        boolean directional=poseLineage!=null&&poseLineage.directional&&sameScope(poseLineage.scope,scope);
        poseLineage=ticket>0?new PosePlanLineage(scope,ticket,trajectory.epoch(),original!=null&&original.current(client)?original:null):null;
        if(poseLineage!=null){poseLineage.directional=directional;var returned=lastMetadataReturn;
            if(returned!=null&&!returned.cancellation&&returned.ticket==ticket&&returned.invocation==METADATA_ENTRY.get())poseLineage.cameraOrigin=returned;}
    }
    private static final ThreadLocal<AppliedPoseAttempt> NATIVE_APPLY=new ThreadLocal<>();
    private static final ThreadLocal<PoseApplicationInvocation> NATIVE_POSE=new ThreadLocal<>();
    public static final class PoseApplicationInvocation {
        private final AppliedPoseAttempt actual;private final net.minecraft.world.entity.Entity player;private final boolean yaw;private final float value;private final Thread thread;
        private boolean claimed,returned,revoked;
        private PoseApplicationInvocation(AppliedPoseAttempt actual,net.minecraft.world.entity.Entity player,boolean yaw,float value){this.actual=actual;this.player=player;this.yaw=yaw;this.value=value;thread=Thread.currentThread();}
    }
    /** Native hook can only claim the exact privately issued setter call; never a caller Boolean or numeric pose. */
    public static PoseApplicationInvocation claimPoseApplication(net.minecraft.world.entity.Entity player,boolean yaw,float value){
        var i=NATIVE_POSE.get();if(i==null||i.claimed||i.revoked||i.player!=player||i.yaw!=yaw||Float.floatToIntBits(i.value)!=Float.floatToIntBits(value)||i.thread!=Thread.currentThread()||!i.actual.header())return null;
        i.claimed=true;return i;
    }
    private static void nativePose(AppliedPoseAttempt actual,net.minecraft.client.player.LocalPlayer player,boolean yaw,float value,Runnable setter){
        if(actual==null){setter.run();return;}if(actual.lineage.directional&&!actual.partialCurrent()){actual.revoked=true;return;}var prior=NATIVE_POSE.get();if(prior!=null){prior.revoked=true;actual.revoked=true;setter.run();return;}
        var i=new PoseApplicationInvocation(actual,player,yaw,value);NATIVE_POSE.set(i);
        try{setter.run();i.returned=i.claimed&&!i.revoked&&actual.header();if(i.returned){(yaw?actual.yawCalls:actual.pitchCalls).add(i);if(!actual.partialCurrent())actual.revoked=true;}else actual.revoked=true;}finally{NATIVE_POSE.remove();}
    }
    private static void applyObserved(AppliedPoseAttempt attempt,Runnable application){var prior=NATIVE_APPLY.get();if(prior!=null){prior.revoked=true;if(attempt!=null)attempt.revoked=true;application.run();return;}
        NATIVE_APPLY.set(attempt);try{application.run();}finally{NATIVE_APPLY.remove();}}
    private AppliedPoseEvent lastAppliedPose;
    private final class AppliedPoseAttempt {
        final OwnedRotationTrajectory.Scope scope;final OwnedRotationTrajectory.Application application;
        final PosePlanLineage lineage=poseLineage;final PoseHistory before=lineage==null?null:lineage.actual;boolean revoked;
        final java.util.ArrayList<PoseApplicationInvocation> yawCalls=new java.util.ArrayList<>(),pitchCalls=new java.util.ArrayList<>();
        final OwnedRotationTrajectory.ControlSnapshot control=trajectory.controlSnapshot();
        final dev.veltraph.client.core.ModuleManager root=dev.veltraph.client.VeltraphBootstrap.moduleManager();
        final dev.veltraph.client.core.ModuleManager.LifecycleSnapshot life=root==null?null:root.lifecycleSnapshot();
        final dev.veltraph.client.input.ScriptingInputLock.ControlSnapshot input=dev.veltraph.client.input.ScriptingInputLock.controlSnapshot();
        final Object authorityIdentity=authority;final int tick=client.player==null?-1:client.player.tickCount;
        Minecraft client(){return McOwnedRotation.this.client;}
        AppliedPoseAttempt(OwnedRotationTrajectory.Scope scope,OwnedRotationTrajectory.Application application){this.scope=scope;this.application=application;}
        boolean header(){return !revoked&&lineage!=null&&lineage==poseLineage&&before!=null&&lineage.ticket==application.ticket()&&lineage.epoch==trajectory.epoch()&&installed==McOwnedRotation.this&&client.isSameThread()&&authority==authorityIdentity&&root!=null
            &&dev.veltraph.client.VeltraphBootstrap.moduleManager()==root&&life!=null&&life.known()&&root.lifecycleCurrent(life)&&input!=null&&input.current()
            &&client.player==scope.player()&&client.level==scope.world()&&client.getConnection()==scope.connection()
            &&control!=null&&control.current()&&control.ticket()==application.ticket()&&sameScope(control.scope(),scope)&&control.poseCurrent(application.pose());}
        boolean partialCurrent(){var after=PoseHistory.capture(client);return header()&&after!=null&&before!=null&&after.carrier==before.carrier&&after.carrier.ownedPoseHistoryAdvanced(before.snapshot,after.snapshot,yawCalls,pitchCalls);}
        void complete(){if(!header()||client.player.tickCount!=tick||!samePose(application.pose(),actual(client))||yawCalls.size()!=1||pitchCalls.size()!=1)return;
            var after=PoseHistory.capture(client);if(after==null||after.carrier!=before.carrier||!after.carrier.ownedPoseHistoryAdvanced(before.snapshot,after.snapshot,yawCalls,pitchCalls))return;
            lineage.actual=after;lastAppliedPose=new AppliedPoseEvent(this);
            try {RootNativeHistory.normalPoseReturned(new PoseNormalReturn(this,after));}
            catch(RuntimeException|Error unavailable){/* Passive history cannot change the original applied pose. */}}

    }
    /** Minted only by the exact completed original native pose application. */
    static final class PoseNormalReturn {
        private final AppliedPoseAttempt actual;private final PoseHistory after;
        private PoseNormalReturn(AppliedPoseAttempt actual,PoseHistory after){this.actual=actual;this.after=after;}
        dev.veltraph.client.core.Module originalModule(){
            // The actual paired issuer carries actor identity; a token owner or current owner bits cannot classify it.
            var origin=actual.lineage.cameraOrigin;var call=origin==null?null:origin.originalCall();
            if(call==null)return null;
            var issuer=call.originalModule();
            if(actual.scope.owner() instanceof dev.veltraph.client.core.Module scoped&&scoped!=issuer)return null;
            return issuer;
        }
        boolean nativeSourceCurrent(){var origin=actual.lineage.cameraOrigin;return actual.header()&&actual.lineage.current()&&after.current(actual.client())
            &&origin!=null&&origin.current()&&origin.originalCall()!=null&&origin.originalCall().normalReturnedCurrent(origin)
            &&origin.originalCall().nativeSourceCurrent(actual.client())&&origin.originalCall().poseScopeOriginCurrent(actual.scope)
            &&origin.originalCall().originalModule()==originalModule();}
        boolean advancedFrom(OwnedPoseHistoryCarrier carrier,Object original){return after.carrier==carrier&&nativeSourceCurrent()
            &&carrier.ownedPoseHistoryAdvanced(original,after.snapshot,actual.yawCalls,actual.pitchCalls);}
        float yaw(){return actual.application.pose().yaw();}float pitch(){return actual.application.pose().pitch();}
    }
    private final class AppliedPoseEvent {
        final AppliedPoseAttempt actual;AppliedPoseEvent(AppliedPoseAttempt actual){this.actual=actual;}
        boolean matches(QueuedPoseObservation original){return lastAppliedPose==this&&original.installation==McOwnedRotation.this
            &&actual.application.ticket()==original.ticket&&actual.control.epoch()==original.epoch&&sameScope(actual.scope,original.actual.scopeForObservation())
            &&actual.header()&&actual.lineage.current()&&actual.control.poseCurrent(actual(client));}
    }
    private AppliedPoseAttempt beginAppliedPose(OwnedRotationTrajectory.Scope scope,OwnedRotationTrajectory.Application actual){
        if(scope==null||actual==null)return null;var attempt=new AppliedPoseAttempt(scope,actual);return attempt.header()&&attempt.lineage.current()?attempt:null;
    }
    /** Read-only actual installed queued END, bound by the original Leg's positive publication ticket. */
    public static final class QueuedPoseObservation {
        private final McOwnedRotation installation;private final OwnedRotationTrajectory.QueuedRead actual;
        private final long ticket,epoch;private final int tick;private final PosePlanLineage originalLineage;
        private QueuedPoseObservation(McOwnedRotation installation,OwnedRotationTrajectory.QueuedRead actual,long ticket,long epoch,int tick){
            this.installation=installation;this.actual=actual;this.ticket=ticket;this.epoch=epoch;this.tick=tick;this.originalLineage=installation.poseLineage;}
        public float endpointYaw(){return actual.endpoint().yaw();}public boolean pending(){return actual.pending();}
        public boolean directionalHistoryCurrent(){return originalLineage!=null&&originalLineage==installation.poseLineage&&originalLineage.ticket==ticket&&originalLineage.epoch==epoch&&originalLineage.current();}
        public boolean bindDirectionalHistory(){var lineage=originalLineage;if(!nativeCurrent()||lineage!=installation.poseLineage||lineage==null||lineage.ticket!=ticket||lineage.epoch!=epoch||!lineage.current())return false;lineage.directional=true;return true;}
        /** Exact original queue's applied pose at +1 native tick; no source/authority getter or new ticket. */
        public boolean appliedNativeCurrent(){
            var c=installation.client;var scope=actual.scopeForObservation();
            return c.isSameThread()&&installed==installation&&c.player==scope.player()&&c.level==scope.world()&&c.getConnection()==scope.connection()
                &&tick<Integer.MAX_VALUE&&c.player.tickCount==tick+1&&installation.trajectory.publicationSerial()==ticket
                &&installation.trajectory.epoch()==epoch&&Float.isFinite(c.player.getYRot())&&Float.isFinite(c.player.getXRot())&&Math.abs(c.player.getXRot())<=90
                &&actual.appliedCurrent(c.player.tickCount,actual(c))&&installation.lastAppliedPose!=null&&installation.lastAppliedPose.matches(this);
        }

        public boolean nativeCurrent(){
            var c=installation.client;
            return c.isSameThread()&&installed==installation&&c.player!=null&&c.player.tickCount==tick
                    &&installation.trajectory.publicationSerial()==ticket&&installation.trajectory.epoch()==epoch&&actual.current(actual(c));
        }
    }
    /** No authority supplier, trajectory advance or pose write. The frame separately proves original ownership/history. */
    public static QueuedPoseObservation readQueuedPose(Minecraft client,OwnedRotationTrajectory.Scope actualOwnedScope,
            int nativeTick,long positiveOwnedTicket,long actualEpoch){
        if(!client.isSameThread()||!installed(client)||client.player==null||client.player.tickCount!=nativeTick)return null;
        var next=installed;var read=next.trajectory.readQueued(actualOwnedScope,nativeTick,actual(client),positiveOwnedTicket,actualEpoch);
        return read==null?null:new QueuedPoseObservation(next,read,positiveOwnedTicket,actualEpoch,nativeTick);
    }
    /** Negative observation only: unknown active queue must be refused, never assumed idle. */
    public static boolean hasQueuedApplication(Minecraft client){return installed(client)&&installed.trajectory.active();}
    public static boolean completed(Minecraft client,long ticket) {return installed(client)&&installed.trajectory.completed(installed.scope(),ticket);}
    public static void cancel(Minecraft client) {requireThread(client);if(McIssuedFarmReturnCleanup.cancelNative(client))return;if(McIssuedFarmResumeGuard.active()){McIssuedFarmResumeGuard.refuseUnscopedMutation();return;}
        var metadataInvocation=beginMetadata(client,true,Float.NaN,Float.NaN,null);
        try {if(installed(client))installed.cancelObserved("explicit.cancel");metadataApiReturned(metadataInvocation);
        }finally{finishMetadata(metadataInvocation);}
    }
    /** Original actual camera state retained by one privately issued Journey cleanup. */
    public static final class IssuedReturnCameraWindow {
        private final McOwnedRotation original;
        private final dev.veltraph.client.rewarp.mc.McSavedAnchorRecovery.IssuedReturnCleanup source;
        private final OwnedRotationTrajectory.ControlSnapshot control;
        private final long before,serial;
        private final Object player,world,connection,options,gameMode;
        private final int tick;
        private long after;
        private boolean retired,attempted;
        private IssuedReturnCameraWindow(McOwnedRotation original,dev.veltraph.client.rewarp.mc.McSavedAnchorRecovery.IssuedReturnCleanup source){
            this.original=original;this.source=source;control=original.trajectory.controlSnapshot();before=original.trajectory.epoch();serial=original.trajectory.publicationSerial();
            var c=original.client;player=c.player;world=c.level;connection=c.getConnection();options=c.options;gameMode=c.gameMode;tick=c.player.tickCount;
        }
        public boolean current(){var c=original.client;
            return installed==original&&c.isSameThread()&&c.player==player&&c.level==world&&c.getConnection()==connection&&c.options==options&&c.gameMode==gameMode
                &&c.player!=null&&c.player.tickCount==tick&&original.trajectory.publicationSerial()==serial
                &&(retired?after==before+1&&original.trajectory.emptyAt(after,serial):original.trajectory.epoch()==before
                    &&(control==null?original.trajectory.emptyAt(before,serial):control.current()&&control.poseCurrent(actual(c))));
        }
        public boolean hadOriginalPositive(){return control!=null;}
        public long originalPositiveTicket(){return control==null?0:control.ticket();}
        public long originalEpoch(){return before;}
        public boolean belongs(dev.veltraph.client.rewarp.mc.McSavedAnchorRecovery.IssuedReturnCleanup exact){return source==exact;}
        public boolean retireOwned(dev.veltraph.client.rewarp.mc.McSavedAnchorRecovery.IssuedReturnCleanup exact,
                java.util.function.BooleanSupplier sourceVeto,java.util.function.BooleanSupplier nativeVeto){
            if(exact!=source||!McIssuedFarmReturnCleanup.ownsCameraWindow(this)||!current())return false;
            if(retired)return nativeVeto.getAsBoolean()&&current();
            if(attempted||before==Long.MAX_VALUE||!sourceVeto.getAsBoolean()||!nativeVeto.getAsBoolean()||!current())return false;
            if(control!=null&&control.scope().owner()!=source.driverIdentity())return false;
            attempted=true;
            original.cancelObserved("issued.journey.original.native.retirement");
            // The literal expected transition is retained before any following source callback.
            after=before+1;retired=true;
            return current()&&source.nativeCurrent()&&current();
        }
    }
    public static IssuedReturnCameraWindow captureIssuedReturnCameraWindow(dev.veltraph.client.rewarp.mc.McSavedAnchorRecovery.IssuedReturnCleanup source){
        if(source==null)return null;var c=source.client();requireThread(c);var original=installed;
        if(original==null||original.client!=c||c.player==null)return null;
        var observed=new IssuedReturnCameraWindow(original,source);
        if(observed.control!=null&&observed.control.scope().owner()!=source.driverIdentity())return null;
        return observed.current()?observed:null;
    }
    private static final class IssuedReturnRetirementAttempt {
        final McOwnedRotation installation;
        final dev.veltraph.client.rewarp.mc.McSuspendedFarmReturnCoordinator.ReturnCleanupCall source;
        final OwnedRotationTrajectory.ControlSnapshot original;
        final Object owner;
        final Object player,world,connection,options,gameMode;
        final int tick;
        boolean revoked;
        IssuedReturnRetirementAttempt(McOwnedRotation installation,
                dev.veltraph.client.rewarp.mc.McSuspendedFarmReturnCoordinator.ReturnCleanupCall source,
                OwnedRotationTrajectory.ControlSnapshot original,Object owner){
            this.installation=installation;this.source=source;this.original=original;this.owner=owner;
            var c=installation.client;player=c.player;world=c.level;connection=c.getConnection();options=c.options;gameMode=c.gameMode;tick=c.player.tickCount;
        }
        boolean nativeCurrent(){var c=installation.client;
            return !revoked&&installed==installation&&installation.issuedReturnRetirementAttempt==this&&c.isSameThread()
                &&c.player==player&&c.level==world&&c.getConnection()==connection&&c.options==options&&c.gameMode==gameMode
                &&player!=null&&world!=null&&connection!=null&&options!=null&&gameMode!=null&&c.player.tickCount==tick
                &&original.scope().owner()==owner&&original.scope().player()==player&&original.scope().world()==world&&original.scope().connection()==connection
                &&original.current()&&original.poseCurrent(actual(c));
        }
    }
    /** Historical event and exact native empty state remain distinct from source admission. */
    public static final class IssuedFarmReturnNativeRetirement {
        private final IssuedReturnRetirementAttempt original;
        private final long before,after,ticket;
        private boolean checking;
        private IssuedFarmReturnNativeRetirement(IssuedReturnRetirementAttempt original){this.original=original;before=original.original.epoch();after=before+1;ticket=original.original.ticket();}
        public boolean belongs(dev.veltraph.client.rewarp.mc.McSuspendedFarmReturnCoordinator.ReturnCleanupCall actual){return original.source==actual;}
        /** One actual normally returned native cancellation, including later source refusal. */
        public boolean normallyReturned(){return true;}
        public long beforeEpoch(){return before;}public long afterEpoch(){return after;}public long positiveTicket(){return ticket;}
        /** Callback-free negative observation; this does not grant an owner role or a resume. */
        public boolean emptyNativeCurrent(){var c=original.installation.client;
            return installed==original.installation&&c.isSameThread()&&original.installation.lastIssuedReturnRetirement==this
                &&after==before+1&&ticket>0&&original.installation.trajectory.emptyAt(after,ticket)
                &&c.player==original.player&&c.level==original.world&&c.getConnection()==original.connection&&c.options==original.options&&c.gameMode==original.gameMode
                &&c.player!=null&&c.player.tickCount==original.tick&&original.original.poseCurrent(actual(c));
        }
        public boolean positiveCurrent(){
            if(checking){original.revoked=true;return false;}checking=true;
            try{return !original.revoked&&original.source.nativeCurrent()&&emptyNativeCurrent();}
            finally{checking=false;}
        }
    }
    /** Actual private issuer only; no callback or caller-supplied epoch can choose a native predecessor. */
    public static IssuedFarmReturnNativeRetirement retireIssuedFarmReturn(
            dev.veltraph.client.rewarp.mc.McSuspendedFarmReturnCoordinator.ReturnCleanupCall call){
        if(call==null)return null;var c=call.client();requireThread(c);
        var installation=installed;
        if(installation==null||installation.client!=c||c.player==null||c.level==null||c.getConnection()==null)return null;
        var entered=installation.issuedReturnRetirementAttempt;
        if(entered!=null){entered.revoked=true;return null;}
        var cached=installation.lastIssuedReturnRetirement;
        if(cached!=null&&cached.belongs(call))return cached.positiveCurrent()?cached:null;
        // All native control and hardware fields precede the first issuer/provider callback.
        var control=installation.trajectory.controlSnapshot();var owner=call.ownerIdentity();
        if(control==null||owner==null||control.scope().owner()!=owner||control.ticket()<=0||control.epoch()==Long.MAX_VALUE
                ||!control.current()||!control.poseCurrent(actual(c)))return null;
        var attempt=new IssuedReturnRetirementAttempt(installation,call,control,owner);installation.issuedReturnRetirementAttempt=attempt;
        try {
            if(!call.current()||!call.nativeCurrent()||!attempt.nativeCurrent())return null;
            // Final callback-free native predecessor check, after the actual source's last supplier.
            if(!call.nativeCurrent()||!attempt.nativeCurrent())return null;
            installation.cancelObserved("issued.farm.return.positive.retirement");
            var receipt=new IssuedFarmReturnNativeRetirement(attempt);
            installation.lastIssuedReturnRetirement=receipt;
            // Preserve the actual historical event even when a later source/native check refuses gameplay.
            return receipt.positiveCurrent()?receipt:null;
        } finally {if(installation.issuedReturnRetirementAttempt==attempt)installation.issuedReturnRetirementAttempt=null;}
    }
    /** Callback-free negative native observation, captured before the issued hook's first provider. */
    static final class ResumeEmptyObservation {
        private final McOwnedRotation original;private final long epoch,serial;
        private ResumeEmptyObservation(McOwnedRotation original,long epoch,long serial){this.original=original;this.epoch=epoch;this.serial=serial;}
        boolean current(){return installed==original&&original.client.isSameThread()
            &&original.trajectory.emptyAt(epoch,serial)&&original.trajectory.epoch()==epoch&&original.trajectory.publicationSerial()==serial;}
    }
    static ResumeEmptyObservation observeIssuedResumeEmpty(Minecraft client,long epoch,long serial){
        requireThread(client);var original=installed;
        if(original==null||original.client!=client||original.trajectory.controlSnapshot()!=null||!original.trajectory.emptyAt(epoch,serial))return null;
        var actual=new ResumeEmptyObservation(original,epoch,serial);return actual.current()?actual:null;
    }
    /** Privately minted from one actual native cancellation; no public epoch value supplies it. */
    static final class ResumeCancellation {
        private final McOwnedRotation original;private final long before,after,serial;
        private ResumeCancellation(McOwnedRotation original,long before,long after,long serial){this.original=original;this.before=before;this.after=after;this.serial=serial;}
        long afterEpoch(){return after;}
        boolean current(){return installed==original&&original.client.isSameThread()&&after==before+1
            &&original.trajectory.epoch()==after&&original.trajectory.publicationSerial()==serial&&!original.trajectory.active();}
    }
    static ResumeCancellation cancelIssuedResumeOwned(Minecraft client,Object farm,long beforeEpoch,long serial,
            java.util.function.BooleanSupplier source,java.util.function.BooleanSupplier header){
        requireThread(client);if(!McIssuedFarmResumeGuard.active()||!installed(client)||!source.getAsBoolean())return null;
        var original=installed;var before=original.trajectory.debugSnapshot();
        var owner=before.scope()!=null?before.scope():before.completedScope();
        if(owner==null||owner.owner()!=farm||before.epoch()!=beforeEpoch||before.serial()!=serial||serial<=0
            ||beforeEpoch==Long.MAX_VALUE||!(before.ticket()==serial||before.completedTicket()==serial)
            ||!McIssuedFarmResumeGuard.current())return null;
        // No provider callback between the final source/native leaf and actual destructive call.
        if(installed!=original||original.trajectory.epoch()!=beforeEpoch||original.trajectory.publicationSerial()!=serial)return null;
        original.cancelObserved("issued.farm.resume.owned.cancel");
        var receipt=new ResumeCancellation(original,beforeEpoch,beforeEpoch+1,serial);
        return receipt.current()&&header.getAsBoolean()&&receipt.current()?receipt:null;
    }
    public static long frameApplications(Minecraft client) {return installed(client)?installed.frameApplications:0;}
    private static RootNativeCameraPhase.Phase peekOriginalCameraTick(Minecraft client){
        try{return RootNativeCameraPhase.peekTick(client);}catch(Throwable unavailable){try{RootNativeCameraPhase.observerFailure();}catch(Throwable ignored){}return null;}
    }
    private static RootNativeCameraPhase.Phase peekOriginalCameraFrame(Minecraft client,DeltaTracker delta){
        try{return RootNativeCameraPhase.peekFrame(client,delta);}catch(Throwable unavailable){try{RootNativeCameraPhase.observerFailure();}catch(Throwable ignored){}return null;}
    }
    public static void beforeTick(Minecraft client) {
        if(!installed(client))return;requireThread(client);
        var metadataInvocation=beginMetadata(client,true,Float.NaN,Float.NaN,peekOriginalCameraTick(client));
        try {var next=installed;var queued=captureQueuedWorkflow(next);
        if(queued!=null){applyQueuedWorkflow(next,queued,true,null);return;}
        var collection=captureCollectionApplication(next);var gui=beginGuiCancel(next);var scope=next.scope();
        if(scope==null){recordInstallationIdleCancellation(metadataInvocation);recordIdleCause(metadataInvocation,next.lastNoOwner);var normal=beginIdleCancel(next,dev.veltraph.client.rotation.NormalIdleCancellationIdentity.Site.BEFORE_TICK);next.cancelGuiObserved("beforeTick.scope-null",gui);finishIdleCancel(normal);metadataApiReturned(metadataInvocation);return;}
        if(!dev.veltraph.client.mining.commission.CommissionModule.nativeTravelCurrent(client,scope.owner()))return;
        if(scope.owner() instanceof dev.veltraph.client.mining.vein.VeinMiningModule){applyCollection(next,scope,collection,null,true);return;}
        if(!farmToolCurrent(client,scope.owner()))return;
        next.trajectory.beforeTick(scope,client.player.tickCount,actual(client)).ifPresent(a->{if(dev.veltraph.client.mining.commission.CommissionModule.nativeTravelCurrent(client,scope.owner())&&farmToolCurrent(client,scope.owner())){
            var attempt=next.beginAppliedPose(scope,a);if(!next.directionalHistoryCurrent())return;boolean[] applied={false};applyObserved(attempt,()->applied[0]=apply(client,a.pose(),scope.owner()));if(applied[0]&&attempt!=null)attempt.complete();}});
    
        }finally{finishMetadata(metadataInvocation);}
    }
    public static void beforeFrame(Minecraft client,DeltaTracker delta) {
        if(!installed(client))return;requireThread(client);
        var metadataInvocation=beginMetadata(client,true,Float.NaN,Float.NaN,peekOriginalCameraFrame(client,delta));
        try {var next=installed;var queued=captureQueuedWorkflow(next);
        if(queued!=null){if(applyQueuedWorkflow(next,queued,false,delta))next.frameApplications++;return;}
        var collection=captureCollectionApplication(next);var gui=beginGuiCancel(next);var scope=next.scope();
        if(scope==null||client.isPaused()){recordInstallationIdleCancellation(metadataInvocation);recordIdleCause(metadataInvocation,next.lastNoOwner);var normal=beginIdleCancel(next,dev.veltraph.client.rotation.NormalIdleCancellationIdentity.Site.BEFORE_FRAME);next.cancelGuiObserved("beforeFrame.scope-null-or-paused",gui);finishIdleCancel(normal);metadataApiReturned(metadataInvocation);return;}
        if(!dev.veltraph.client.mining.commission.CommissionModule.nativeTravelCurrent(client,scope.owner()))return;
        int originalTick=client.player.tickCount;
        float partial=delta.getGameTimeDeltaPartialTick(true);
        if(!dev.veltraph.client.mining.commission.CommissionModule.nativeTravelCurrent(client,scope.owner()))return;
        if(scope.owner() instanceof dev.veltraph.client.mining.vein.VeinMiningModule){if(applyCollection(next,scope,collection,partial,false))next.frameApplications++;return;}
        if(!farmToolCurrent(client,scope.owner()))return;
        next.trajectory.frame(scope,originalTick,partial,actual(client)).ifPresent(a->{
            if(dev.veltraph.client.mining.commission.CommissionModule.nativeTravelCurrent(client,scope.owner())&&farmToolCurrent(client,scope.owner())){var attempt=next.beginAppliedPose(scope,a);if(!next.directionalHistoryCurrent())return;boolean[] applied={false};applyObserved(attempt,()->applied[0]=apply(client,a.pose(),scope.owner()));if(applied[0]){if(attempt!=null)attempt.complete();next.frameApplications++;}}
        });
    
        }finally{finishMetadata(metadataInvocation);}
    }
    private static final class CollectionApplication {
        final McOwnedRotation installation;final OwnedRotationTrajectory.ControlSnapshot original;final Object source;
        private CollectionApplication(McOwnedRotation installation,OwnedRotationTrajectory.ControlSnapshot original,Object source){this.installation=installation;this.original=original;this.source=source;}
        private boolean sourceCurrent(OwnedRotationTrajectory.Scope scope){return installed==installation&&original!=null&&scope.owner()==original.scope().owner()
            &&scope.player()==original.scope().player()&&scope.world()==original.scope().world()&&scope.connection()==original.scope().connection()
            &&installation.trajectory.publicationSerial()==original.ticket()&&installation.trajectory.epoch()==original.epoch()
            &&dev.veltraph.client.navigation.mc.LegacyTravelPublicationLease.collectionCameraSnapshotCurrent(installation.client,scope.owner(),original.ticket(),original.epoch(),source,System.nanoTime()/1_000_000);}
    }
    /** Original private queue is sampled before all authority/GUI/delta/observer callbacks. */
    private static CollectionApplication captureCollectionApplication(McOwnedRotation installation){
        var control=installation.trajectory.controlSnapshot();if(control==null)return null;
        var source=dev.veltraph.client.navigation.mc.LegacyTravelPublicationLease.collectionCameraSnapshot(installation.client,control.scope().owner(),control.ticket(),control.epoch());
        return new CollectionApplication(installation,control,source);
    }
    private static boolean applyCollection(McOwnedRotation installation,OwnedRotationTrajectory.Scope scope,CollectionApplication capture,Float partial,boolean tickHead){
        var c=installation.client;if(capture==null||!capture.sourceCurrent(scope)||!capture.original.current()||!capture.original.poseCurrent(actual(c)))return false;
        int nativeTick=c.player.tickCount;
        var preview=tickHead?installation.trajectory.previewBeforeTick(scope,nativeTick,actual(c)):installation.trajectory.previewFrame(scope,nativeTick,partial,actual(c));
        if(preview==null)return false;var proposed=preview.application();
        return dev.veltraph.client.rotation.CollectionCameraApplicationFence.run(new dev.veltraph.client.rotation.CollectionCameraApplicationFence.Effects(){
            public boolean beforeObserver(){return preview.current(actual(c))&&capture.original.current()&&capture.sourceCurrent(scope);}
            public void observe(){UnexpectedActions.rotation(c,proposed.pose().yaw(),proposed.pose().pitch());}
            public boolean beforeAdvance(){return capture.sourceCurrent(scope)&&c.player!=null&&c.player.tickCount==nativeTick&&preview.current(actual(c))&&capture.original.current();}
            public java.util.Optional<OwnedRotationTrajectory.Application> advance(){return tickHead?installation.trajectory.beforeTick(scope,nativeTick,actual(c)):installation.trajectory.frame(scope,nativeTick,partial,actual(c));}
            public boolean beforeApply(OwnedRotationTrajectory.Application a){return a.ticket()==capture.original.ticket()&&a.pose().equals(proposed.pose())&&capture.sourceCurrent(scope)&&capture.original.poseCurrent(actual(c));}
            public boolean apply(OwnedRotationTrajectory.Application a){var attempt=installation.beginAppliedPose(scope,a);if(!installation.directionalHistoryCurrent())return false;boolean[] accepted={false};applyObserved(attempt,()->accepted[0]=applyCollectionPose(c,a.pose(),capture,scope));boolean applied=accepted[0];if(applied&&attempt!=null)attempt.complete();return applied;}
        });
    }
    /** Existing observer already ran exactly once before advance; final native setter leaves add no callback. */
    private static boolean applyCollectionPose(Minecraft c,OwnedRotationTrajectory.Pose pose,CollectionApplication capture,OwnedRotationTrajectory.Scope scope){
        var p=c.player;if(p==null||!capture.sourceCurrent(scope)||!capture.original.poseCurrent(actual(c)))return false;
        float originalPitch=p.getXRot();nativePose(NATIVE_APPLY.get(),p,true,pose.yaw(),()->p.setYRot(pose.yaw()));
        if(c.player!=p||!capture.sourceCurrent(scope)||p.getYRot()!=pose.yaw()||p.getXRot()!=originalPitch)return false;
        nativePose(NATIVE_APPLY.get(),p,false,pose.pitch(),()->p.setXRot(pose.pitch()));
        if(c.player!=p||!capture.sourceCurrent(scope)||p.getYRot()!=pose.yaw()||p.getXRot()!=pose.pitch())return false;
        p.yRotO=p.getYRot();p.xRotO=p.getXRot();return true;
    }
    private OwnedRotationTrajectory.Scope scope() {
        var p=client.player;Object owner;
        if(authority instanceof dev.veltraph.client.VeltraphBootstrap.RootRotationAuthorityProvider actual&&actual.registeredCurrent()){
            var observed=actual.read();owner=observed.owner();lastNoOwner=owner==null?observed.noOwner():null;lastGuiAvailability=owner==null?observed.guiAvailability():null;
        }else{owner=authority.get();lastNoOwner=null;lastGuiAvailability=null;}
        if(owner!=null)guiAvailability=null; // Actual returned ownership ends the GUI-only cleanup lineage.
        if(owner==null||p==null||client.level==null||client.getConnection()==null||!p.isAlive()||client.isPaused()
                ||client.level.tickRateManager().isEntityFrozen(p)
                ||client.gui.screen()!=null||p.isScoping()||client.getCameraEntity()!=p)return null;
        return new OwnedRotationTrajectory.Scope(owner,p,client.level,client.getConnection());
    }
    /** Original GUI unavailability cancellation only. Private proofs grant cleanup, never a camera token. */
    private static final class PendingGuiCancel {
        final McOwnedRotation installation;final OwnedRotationTrajectory.ControlSnapshot original;
        final dev.veltraph.client.VeltraphBootstrap.RootGuiAvailabilityHeader header;final GuiAvailabilityRevocation predecessor;
        final long before;
        private PendingGuiCancel(McOwnedRotation installation,OwnedRotationTrajectory.ControlSnapshot original,dev.veltraph.client.VeltraphBootstrap.RootGuiAvailabilityHeader header,GuiAvailabilityRevocation predecessor,long before){this.installation=installation;this.original=original;this.header=header;this.predecessor=predecessor;this.before=before;}
        private boolean beforeCurrent(){return installed==installation&&installation.guiAvailability==predecessor&&header.current()&&original.poseCurrent(actual(installation.client))
            &&(predecessor==null?original.current():predecessor.current());}
    }
    private static PendingGuiCancel beginGuiCancel(McOwnedRotation installation){
        if(!(installation.authority instanceof dev.veltraph.client.VeltraphBootstrap.RootRotationAuthorityProvider provider)||!provider.registeredCurrent())return null;
        var predecessor=installation.guiAvailability;
        if(predecessor!=null&&predecessor.current()&&predecessor.ordinal<Long.MAX_VALUE)return new PendingGuiCancel(installation,predecessor.original,predecessor.header,predecessor,predecessor.after);
        var original=installation.trajectory.controlSnapshot();var header=dev.veltraph.client.VeltraphBootstrap.guiAvailabilityHeader();
        if(original==null||header==null||!original.current()||!original.poseCurrent(actual(installation.client))||!header.matchesNative(original.scope().player(),original.scope().world(),original.scope().connection()))return null;
        return new PendingGuiCancel(installation,original,header,null,original.epoch());
    }
    private static void recordActualGuiCancellation(NativeMetadataInvocation exact,Minecraft client){
        try{if(exact!=null&&exact.phase!=null&&!exact.revoked&&!exact.changed&&METADATA_ENTRY.get()==exact
                &&exact.guiSource!=null&&RootNativeHistory.guiMetadataAffiliationCurrent(exact.guiSource,client))exact.guiCancellation=true;}
        catch(RuntimeException|Error unavailable){if(exact!=null)exact.revoked=true;}
    }
    private void cancelGuiObserved(String origin,PendingGuiCancel pending){
        var cause=lastGuiAvailability;boolean qualified=pending!=null&&pending.beforeCurrent()&&cause!=null&&cause.matches(pending.header);
        recordActualGuiCancellation(METADATA_ENTRY.get(),client);
        cancelObserved(origin); // Preserve the original cancellation exactly once, including diagnostics.
        if(!qualified||pending.before==Long.MAX_VALUE||installed!=this||!trajectory.emptyAt(pending.before+1,pending.original.ticket())
            ||!pending.header.current()||!cause.current()||!pending.original.poseCurrent(actual(client)))return;
        guiAvailability=new GuiAvailabilityRevocation(this,pending.original,pending.header,cause,pending.predecessor,pending.before+1);
    }
    /** A privately minted bounded causal chain, advanced only after an actual verified cancellation. */
    private static final class GuiLineage {
        final McOwnedRotation installation;final OwnedRotationTrajectory.ControlSnapshot original;
        final dev.veltraph.client.VeltraphBootstrap.RootGuiAvailabilityHeader header;
        long ordinal;GuiAvailabilityRevocation latest;
        private GuiLineage(McOwnedRotation installation,OwnedRotationTrajectory.ControlSnapshot original,dev.veltraph.client.VeltraphBootstrap.RootGuiAvailabilityHeader header){this.installation=installation;this.original=original;this.header=header;}
    }
    public static final class GuiAvailabilityRevocation {
        private final McOwnedRotation installation;private final OwnedRotationTrajectory.ControlSnapshot original;
        private final dev.veltraph.client.VeltraphBootstrap.RootGuiAvailabilityHeader header;
        private final dev.veltraph.client.VeltraphBootstrap.RootGuiAvailabilityObservation cause;
        private final java.lang.ref.WeakReference<GuiAvailabilityRevocation> predecessor;private final GuiLineage lineage;private final long ordinal,after;
        private GuiAvailabilityRevocation(McOwnedRotation installation,OwnedRotationTrajectory.ControlSnapshot original,dev.veltraph.client.VeltraphBootstrap.RootGuiAvailabilityHeader header,dev.veltraph.client.VeltraphBootstrap.RootGuiAvailabilityObservation cause,GuiAvailabilityRevocation predecessor,long after){this.installation=installation;this.original=original;this.header=header;this.cause=cause;this.predecessor=new java.lang.ref.WeakReference<>(predecessor);this.lineage=predecessor==null?new GuiLineage(installation,original,header):predecessor.lineage;
            this.ordinal=++lineage.ordinal;lineage.latest=this;this.after=after;}
        public long beforeEpoch(){return original.epoch();}public long afterEpoch(){return after;}public long actualTicket(){return original.ticket();}
        /** Weak diagnostic operand only; never use this to determine cleanup ancestry. */
        public GuiAvailabilityRevocation predecessor(){return predecessor.get();}
        /** Certified private causal ancestry, independent of raw epoch comparisons or retained history. */
        public boolean descendsFrom(GuiAvailabilityRevocation exact){return exact!=null&&exact.lineage==lineage&&exact.installation==installation&&exact.original==original&&exact.header==header
            &&exact.ordinal>0&&exact.ordinal<=ordinal&&lineage.original==original&&lineage.header==header&&lineage.installation==installation&&current();}
        /** Actual recorded GUI descendants only; never a numerical adoption of the latest epoch. */
        public GuiAvailabilityRevocation currentDescendant(){var latest=installation.guiAvailability;
            return latest!=null&&latest.lineage==lineage&&latest.original==original&&latest.header==header&&latest.descendsFrom(this)?latest:null;}
        public Object storeIdentity(){return header.storeIdentity();}public Object registryIdentity(){return header.registryIdentity();}
        public boolean capturedInputActive(){return header.capturedInputActive();}public String capturedInputOwnerModuleId(){return header.capturedInputOwnerModuleId();}
        public boolean matchesOriginal(Object authority,Object player,Object world,Object connection,long beforeEpoch,long ticket){var scope=original.scope();
            return scope.owner()==authority&&scope.player()==player&&scope.world()==world&&scope.connection()==connection&&original.epoch()==beforeEpoch&&original.ticket()==ticket&&current();}
        public boolean current(){return installed==installation&&installation.guiAvailability==this&&lineage.latest==this&&lineage.ordinal==ordinal&&ordinal>0&&header.current()&&cause.current()
            &&header.matchesNative(original.scope().player(),original.scope().world(),original.scope().connection())&&original.poseCurrent(actual(installation.client))
            &&installation.trajectory.emptyAt(after,original.ticket());}
    }
    /** No query of authority/provider; the returned proof must still be checked for exact source consumption. */
    public static GuiAvailabilityRevocation guiAvailabilityRevocation(Minecraft client){requireThread(client);var next=installed;
        return next!=null&&next.client==client&&next.guiAvailability!=null&&next.guiAvailability.current()?next.guiAvailability:null;}
    /** Actual normal token only; generic installations never gain native idle certificate eligibility. */
    public static boolean armNormalIdle(Minecraft client,dev.veltraph.client.navigation.mc.LegacyTravelPublicationLease.NativeParentCleanupToken token){
        if(!installed(client))return false;var next=installed;
        if(!client.isSameThread())return next.idleArmUnavailable("CLIENT_THREAD_FALSE");
        if(token==null)return next.idleArmUnavailable("ACTUAL_NORMAL_TOKEN_NULL");
        if(!(installed.authority instanceof dev.veltraph.client.VeltraphBootstrap.RootRotationAuthorityProvider provider))return next.idleArmUnavailable("AUTHENTIC_ROOT_PROVIDER_MISSING");
        if(!provider.registeredCurrent())return next.idleArmUnavailable("AUTHENTIC_PROVIDER_REGISTRATION_FALSE");
        var permit=token.normalIdlePermit();
        if(permit==null)return next.idleArmUnavailable("TOKEN_PERMIT_NULL");
        if(installed!=next)return next.idleArmUnavailable("INSTALLATION_CHANGED");
        if(!permit.armCurrent())return next.idleArmUnavailable("EXACT_PERMIT_ARM_CURRENT_FALSE");
        next.normalIdle=permit;next.normalArmDiagnostic="ACTUAL_NORMAL_TOKEN_REGISTERED";return true;
    }
    public static boolean hasNormalIdleCandidate(Minecraft client){return installed(client)&&installed.normalIdle!=null&&installed.normalIdle.originalRegistered();}
    public static final class NormalOperation {
        private final McOwnedRotation installation;private final dev.veltraph.client.navigation.mc.LegacyTravelPublicationLease.NormalIdlePermit permit;
        private final dev.veltraph.client.VeltraphBootstrap.RootNoOwnerObservation cause;
        private NormalOperation(McOwnedRotation installation,dev.veltraph.client.navigation.mc.LegacyTravelPublicationLease.NormalIdlePermit permit,dev.veltraph.client.VeltraphBootstrap.RootNoOwnerObservation cause){this.installation=installation;this.permit=permit;this.cause=cause;}
        public boolean belongsTo(dev.veltraph.client.navigation.mc.LegacyTravelPublicationLease.NormalIdlePermit exact){return exact==permit&&installed==installation&&installation.normalIdle==permit;}
        public Object installationIdentity(){return installation;}
        public Object providerIdentity(){return installation.authority;}
        public boolean rootCauseCurrent(){return cause!=null&&cause.current()&&installed==installation&&installation.authority instanceof dev.veltraph.client.VeltraphBootstrap.RootRotationAuthorityProvider provider&&provider.registeredCurrent();}
    }
    private static final class PendingIdleCancel {
        final NormalOperation operation;final dev.veltraph.client.navigation.mc.LegacyTravelPublicationLease.NormalIdleWitness witness;
        final NormalIdleCertificate prior;final dev.veltraph.client.rotation.NormalIdleCancellationIdentity.Site site;final long serial;final dev.veltraph.client.rotation.NormalIdleCancellationIdentity.Scope before;
        private PendingIdleCancel(NormalOperation operation,dev.veltraph.client.navigation.mc.LegacyTravelPublicationLease.NormalIdleWitness witness,NormalIdleCertificate prior,dev.veltraph.client.rotation.NormalIdleCancellationIdentity.Site site,long serial){this.operation=operation;this.witness=witness;this.prior=prior;this.site=site;this.serial=serial;before=witness.identity(witness.beforeEpoch());}
    }
    private static PendingIdleCancel beginIdleCancel(McOwnedRotation next,dev.veltraph.client.rotation.NormalIdleCancellationIdentity.Site site){
        try{if(installed!=next){next.normalCancelDiagnostic="INSTALLATION_CHANGED";return null;}
            if(next.normalIdle==null){next.normalCancelDiagnostic="ACTUAL_NORMAL_CANDIDATE_MISSING";return null;}
            if(next.lastNoOwner==null){next.normalCancelDiagnostic="AUTHENTIC_ROOT_FINAL_NO_OWNER_CAUSE_MISSING";return null;}
            if(!next.lastNoOwner.current()){next.normalCancelDiagnostic="AUTHENTIC_ROOT_NO_OWNER_CAUSE_REVOKED";return null;}
            var operation=new NormalOperation(next,next.normalIdle,next.lastNoOwner);var witness=next.normalIdle.witness(operation);
            if(witness==null){next.normalCancelDiagnostic="ACTUAL_NORMAL_WITNESS_NULL";return null;}
            if(!operation.rootCauseCurrent()){next.normalCancelDiagnostic="FINAL_ROOT_CAUSE_FALSE";return null;}
            if(!witness.current(witness.beforeEpoch(),(NormalIdleHardwareSnapshot)witness.hardwareIdentity())){next.normalCancelDiagnostic="FINAL_NATIVE_WITNESS_FALSE";return null;}
            next.normalCancelDiagnostic="BEFORE_ACTUAL_CANCEL_CAPTURED";
            return new PendingIdleCancel(operation,witness,next.normalIdle.latest(),site,next.trajectory.publicationSerial());
        }catch(RuntimeException unavailable){next.normalCancelDiagnostic="ORIGINAL_BOUNDARY_EXCEPTION_"+unavailable.getClass().getSimpleName();return null;}
    }
    /** This constructor is reached only after the original native trajectory.cancel at its real tick/frame site. */
    private static void finishIdleCancel(PendingIdleCancel actual){
        if(actual==null)return;try{var installation=actual.operation.installation;var permit=actual.operation.permit;long after=installation.trajectory.epoch();
            if(installed!=installation){installation.normalCancelDiagnostic="POST_CANCEL_INSTALLATION_CHANGED";return;}
            if(actual.witness.beforeEpoch()==Long.MAX_VALUE){installation.normalCancelDiagnostic="ORIGINAL_EPOCH_EXHAUSTED";return;}
            if(after!=actual.witness.beforeEpoch()+1){installation.normalCancelDiagnostic="POST_CANCEL_ACTUAL_EPOCH_TRANSITION_FALSE";return;}
            if(installation.trajectory.publicationSerial()!=actual.serial){installation.normalCancelDiagnostic="POST_CANCEL_ACTUAL_SERIAL_CHANGED";return;}
            if(!actual.operation.rootCauseCurrent()){installation.normalCancelDiagnostic="POST_CANCEL_ROOT_CAUSE_REVOKED";return;}
            if(!actual.witness.current(after,(NormalIdleHardwareSnapshot)actual.witness.hardwareIdentity())){installation.normalCancelDiagnostic="POST_CANCEL_NATIVE_WITNESS_FALSE";return;}
            if(!dev.veltraph.client.rotation.NormalIdleCancellationIdentity.transition(actual.before,actual.witness.identity(after),
                dev.veltraph.client.rotation.NormalIdleCancellationIdentity.Cause.ROOT_FINAL_NO_OWNER,actual.site)){installation.normalCancelDiagnostic="ACTUAL_CAUSE_TRANSITION_KERNEL_FALSE";return;}
            var receipt=new NormalIdleCertificate(actual.operation,actual.witness,actual.prior,actual.site,after,actual.serial);boolean accepted=permit.acceptNativeCancellation(receipt);installation.normalCancelDiagnostic=accepted?"ACTUAL_NATIVE_CERTIFICATE_ACCEPTED":"ACTUAL_PRIVATE_CERTIFICATE_ACCEPTANCE_FALSE";
        }catch(RuntimeException unavailable){}
    }
    public static final class NormalIdleCertificate {
        private final NormalOperation operation;private final dev.veltraph.client.navigation.mc.LegacyTravelPublicationLease.NormalIdleWitness witness;
        private final NormalIdleCertificate prior;private final dev.veltraph.client.rotation.NormalIdleCancellationIdentity.Site site;private final long after,serial;
        private NormalIdleCertificate(NormalOperation operation,dev.veltraph.client.navigation.mc.LegacyTravelPublicationLease.NormalIdleWitness witness,NormalIdleCertificate prior,dev.veltraph.client.rotation.NormalIdleCancellationIdentity.Site site,long after,long serial){this.operation=operation;this.witness=witness;this.prior=prior;this.site=site;this.after=after;this.serial=serial;}
        public boolean belongsTo(dev.veltraph.client.navigation.mc.LegacyTravelPublicationLease.NormalIdlePermit exact){return operation.belongsTo(exact);}
        public NormalIdleCertificate predecessor(){return prior;}
        public boolean sampledTimeCurrent(long sample){return witness.sampledTimeCurrent(sample);}
        public long certifiedEpoch(dev.veltraph.client.navigation.mc.LegacyTravelPublicationLease.NormalIdlePermit exact){return nativeCurrent(exact)?after:-1;}
        public boolean nativeCurrent(dev.veltraph.client.navigation.mc.LegacyTravelPublicationLease.NormalIdlePermit exact){return belongsTo(exact)&&exact.nativeControlCurrent(after)&&operation.installation.trajectory.publicationSerial()==serial;}
    }
    public static NormalRootInputDrop beginNormalRootInputDrop(Minecraft client,dev.veltraph.client.VeltraphBootstrap.RootInputDropCall actual){
        if(!installed(client))return null;var next=installed;
        if(actual==null){next.normalDropDiagnostic="ACTUAL_ROOT_DROP_CALL_MISSING";return null;}
        if(!client.isSameThread()){next.normalDropDiagnostic="CLIENT_THREAD_FALSE";return null;}
        if(installed.normalIdle==null){next.normalDropDiagnostic="ACTUAL_NORMAL_CANDIDATE_MISSING";return null;}
        if(installed.inputDrop!=null){next.normalDropDiagnostic="DROP_REENTRANT";return null;}
        var permit=next.normalIdle;
        if(!actual.matchesOriginal(permit.parent())){next.normalDropDiagnostic="ORIGINAL_ROOT_DROP_PARENT_CAUSE_FALSE";return null;}
        if(!permit.originalRegistered()){next.normalDropDiagnostic="ORIGINAL_REGISTRATION_ADM_CORE_FALSE";return null;}
        var operation=new NormalOperation(next,permit,actual.noOwner());var witness=permit.witness(operation);
        next.normalDropDiagnostic=witness==null?"DROP_WITNESS_NULL":"BEFORE_ACTUAL_LOCK_DROP_CAPTURED";
        var drop=new NormalRootInputDrop(operation,actual,witness);next.inputDrop=drop;return drop;
    }
    public static NormalRootInputDrop normalRootInputDrop(McInputService exact){var next=installed;var drop=next==null?null:next.inputDrop;
        return drop!=null&&drop.actualInput(exact)?drop:null;}
    public static void finishNormalRootInputDrop(NormalRootInputDrop actual){if(actual==null)return;try{
        if(actual.operation.installation.inputDrop==actual&&actual.completed){boolean accepted=actual.operation.permit.acceptRootDrop(actual);actual.operation.installation.normalDropDiagnostic=accepted?"ACTUAL_SCOPED9_DROP_ACCEPTED":"ACTUAL_SCOPED9_DROP_HISTORY_REFUSED";}
        else actual.operation.installation.normalDropDiagnostic=actual.completed?"DROP_IDENTITY_CHANGED":"ACTUAL_SCOPED9_DROP_INCOMPLETE_count="+actual.actual.size();
    }finally{if(actual.operation.installation.inputDrop==actual)actual.operation.installation.inputDrop=null;}}
    public static final class NormalRootInputDrop {
        private final NormalOperation operation;private final dev.veltraph.client.VeltraphBootstrap.RootInputDropCall source;
        private final dev.veltraph.client.navigation.mc.LegacyTravelPublicationLease.NormalIdleWitness witness;
        private final java.util.List<McHeldKeys.ScopedHeldReceipt> actual=new java.util.ArrayList<>();private boolean invoked,completed;private int pendingIndex=-1;
        private NormalRootInputDrop(NormalOperation operation,dev.veltraph.client.VeltraphBootstrap.RootInputDropCall source,dev.veltraph.client.navigation.mc.LegacyTravelPublicationLease.NormalIdleWitness witness){this.operation=operation;this.source=source;this.witness=witness;}
        public boolean belongsTo(dev.veltraph.client.navigation.mc.LegacyTravelPublicationLease.NormalIdlePermit exact){return operation.belongsTo(exact);}
        private boolean actualInput(McInputService exact){return operation.permit.actualInput(exact)&&source.current();}
        private KeyMapping[] keys(){var o=operation.installation.client.options;return new KeyMapping[]{o.keyUp,o.keyDown,o.keyLeft,o.keyRight,o.keyJump,o.keyShift,o.keySprint,o.keyAttack,o.keyUse};}
        private boolean prefixCurrent(){if(witness==null||!source.current()||!witness.nativeFieldsCurrent(witness.beforeEpoch()))return false;
            var original=(NormalIdleHardwareSnapshot)witness.hardwareIdentity();if(!original.mousePoseCurrent())return false;var keys=keys();
            for(int i=0;i<9;i++){if(i==pendingIndex)continue;if(i<actual.size()){var receipt=actual.get(i);if(receipt.key()!=keys[i]||!receipt.confirmed()||receipt.held()||!receipt.normalIdleHistoryAdvancedFrom(original.keyObservation(keys[i])))return false;}
                else if(!(keys[i] instanceof NormalIdleKeyCarrier carrier)||!carrier.normalIdleKeyCurrent(original.keyObservation(keys[i])))return false;}return true;
        }
        /** Only installed actual native release callback enters here; all effects remain ordinary scoped false-key publications. */
        public void releaseActual(McInputService exact){if(invoked||!actualInput(exact))return;invoked=true;if(witness==null)return;
            for(var key:keys()){if(!prefixCurrent())return;pendingIndex=actual.size();McHeldKeys.ScopedHeldReceipt receipt;try{receipt=McHeldKeys.setHeldScoped(key,false,this::prefixCurrent);}finally{pendingIndex=-1;}actual.add(receipt);if(!receipt.confirmed()||!prefixCurrent())return;}
            completed=actual.size()==9&&prefixCurrent();
        }
        public McHeldKeys.ScopedHeldReceipt actualKey(int index){return completed&&index>=0&&index<actual.size()?actual.get(index):null;}
        public boolean afterCurrent(NormalIdleHardwareSnapshot hardware){return completed&&source.current()&&witness!=null&&witness.current(witness.beforeEpoch(),hardware)&&prefixCurrent();}
    }
    private static OwnedRotationTrajectory.Pose actual(Minecraft client) {
        return new OwnedRotationTrajectory.Pose(client.player.getYRot(),client.player.getXRot());
    }
    private static void apply(Minecraft client,OwnedRotationTrajectory.Pose pose) { apply(client,pose,null); }
    private static boolean apply(Minecraft client,OwnedRotationTrajectory.Pose pose,Object expectedOwner) {
        var farm=expectedOwner instanceof dev.veltraph.client.farming.module.FarmModule actual?actual:null;
        var farmScope=farm==null?null:McFarmOwnedCleanup.NativeScope.capture(client,farm);
        if(farm!=null&&farmScope==null)return false;
        // Attribute the actual final mutation, not the requested/future or discarded intermediate pose.
        UnexpectedActions.rotation(client,pose.yaw(),pose.pitch());
        if(farmScope!=null&&!farmScope.activeCurrent())return false;
        var p=client.player;nativePose(NATIVE_APPLY.get(),p,true,pose.yaw(),()->p.setYRot(pose.yaw()));
        if(farmScope!=null&&(!farmScope.activeCurrent()||client.player!=p))return false;
        nativePose(NATIVE_APPLY.get(),p,false,pose.pitch(),()->p.setXRot(pose.pitch()));
        if(farmScope!=null&&(!farmScope.activeCurrent()||client.player!=p))return false;
        p.yRotO=p.getYRot();p.xRotO=p.getXRot();return true;
    }
    private long diagnosticSequence;private final java.util.ArrayDeque<String> cancellationDiagnostics=new java.util.ArrayDeque<>();
    public static java.util.List<String> cancellationDiagnostics(Minecraft client){return installed(client)?java.util.List.copyOf(installed.cancellationDiagnostics):java.util.List.of();}
    private void cancelObserved(String origin){
        guiAvailability=null;lastAppliedPose=null;poseLineage=null; // Actual revocation drops observational pose custody too.
        OwnedRotationTrajectory.DebugSnapshot before=null;try{before=trajectory.debugSnapshot();}catch(Throwable unavailable){}
        cancelObservedMetadata();
        // Ignore repeated empty cadence revocations; retain the first actual scoped/completed revocation and explicit calls.
        if(before!=null&&before.scope()==null&&before.completedScope()==null&&!origin.equals("explicit.cancel"))return;
        try{var after=trajectory.debugSnapshot();var p=client.player;var scope=before==null?null:(before.scope()!=null?before.scope():before.completedScope());
            String row="seq="+(++diagnosticSequence)+" origin="+origin+" scopeOwner="+objectId(scope==null?null:scope.owner())
                +" scopePlayer="+objectId(scope==null?null:scope.player())+" scopeWorld="+objectId(scope==null?null:scope.world())
                +" scopeConnection="+objectId(scope==null?null:scope.connection())+" tick="+(p==null?-1:p.tickCount)
                +" beforeEpoch="+(before==null?-1:before.epoch())+" afterEpoch="+after.epoch()+" serial="+after.serial()
                +" ticket="+(before==null?-1:before.ticket())+" completed="+(before==null?-1:before.completedTicket())
                +" player="+objectId(p)+" world="+objectId(client.level)+" connection="+objectId(client.getConnection())
                +" gui="+objectId(client.gui.screen());
            if(cancellationDiagnostics.size()>=64)cancellationDiagnostics.removeFirst();cancellationDiagnostics.addLast(row);
            try{System.out.println("NATIVE_CAMERA_CANCEL_TRACE "+row);}catch(Throwable reportUnavailable){}
        }catch(Throwable unavailable){}
    }
    private static String objectId(Object value){return value==null?"null":value.getClass().getName()+"@"+System.identityHashCode(value);}
    private static void requireThread(Minecraft client) {
        if(!client.isSameThread())throw new IllegalStateException("Camera cadence requires the client thread");
    }
    
}
