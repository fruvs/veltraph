package dev.veltraph.client.service.mc;

import dev.veltraph.client.VeltraphBootstrap;
import dev.veltraph.client.core.Module;
import dev.veltraph.client.observation.nativeinput.McNativeManualInputLineage;
import dev.veltraph.client.observation.nativeinput.NativeGuiKeyResetLineage;
import dev.veltraph.client.observation.nativeinput.NativeKeyBindingHistory;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ToggleKeyMapping;
import net.minecraft.client.player.LocalPlayer;

/** Passive original Root native history. No movement, release, cancellation or permission is produced. */
public final class RootNativeHistory {
    private static RootNativeHistory installed;
    private static TerminalNoOwnerHandout retainedTerminal;
    private static VeltraphBootstrap.RootOriginalNativeEnrollment attemptedEnrollment;
    private final VeltraphBootstrap.RootOriginalNativeEnrollment source;
    private final Minecraft client;
    private final Object player,world,listener,connection,options,gameMode;
    private final McOwnedRotation.InstallationSnapshot rotation;
    private final VeltraphBootstrap.RootOriginalNativeInstallation originalInstallation;
    private final KeyMapping[] keys;
    private final NormalIdleKeyCarrier[] carriers;
    private final Object[] histories;
    private final String[] bindings;
    private final Object[] bindingHistories;
    private final boolean[] expectedHeld;
    private final OwnedPoseHistoryCarrier poses;
    private Object poseHistory;
    private McOwnedRotation.NativeMetadataSnapshot metadata;
    private float expectedYaw,expectedPitch;
    private boolean available=true,retired;
    private TerminalTicket terminalTicket;
    private GuiTicket guiTicket;
    private NativePauseObservation pauseObservation;
    private SavedMaintenancePauseObservation savedMaintenancePause;
    private SavedMaintenanceKeyObservation savedMaintenanceKey;
    private SavedMaintenanceRestoreSequence savedRestoration;
    private RetainedMaintenanceKeyObservation retainedMaintenanceKey;
    private RetainedMaintenanceRestoreSequence retainedRestoration;
    private SavedMaintenanceRollbackKeyObservation savedMaintenanceRollbackKey;
    private SavedMaintenanceRollbackSequence savedRollback;
    private SavedMaintenanceRollbackCompletion savedRollbackCompletion;
    private RollbackPrefixHistory retainedRollbackPrefix;
    private SavedMaintenancePauseObservation retainedSavedPauseObservation;
    private RollbackPrefixHistory retainedSavedPausePrefix;
    private final McHeldKeys.ScopedHeldReceipt[] originalNormalOwnedKeys=new McHeldKeys.ScopedHeldReceipt[9];

    private PassiveTransitionObservation passiveTransition;

    /** Existing native anchors and private custody only. This does not create an input or a native scope. */
    public static final class PassiveTransitionObservation {
        private final RootNativeHistory history;
        private final VeltraphBootstrap.RootPassiveTransition actual;
        private final VeltraphBootstrap.RootOriginalInputPublication publication;
        private final VeltraphBootstrap.RootOriginalNativeTermination terminal;
        private final Thread thread=Thread.currentThread();
        private final Object[] keys,bindings;
        private final Object pose;
        private final McOwnedRotation.NativeMetadataSnapshot metadata;
        private McInputService.PassiveHeldContinuation held;
        private McBlockBreakingService.PassiveBreakingContinuation breaking;
        private McRotationService.PassiveCameraContinuation camera;
        private boolean finished,accepted,committing;
        private PassiveTransitionObservation(RootNativeHistory history,VeltraphBootstrap.RootPassiveTransition actual) {
            this.history=history;this.actual=actual;publication=actual.originalInputPublication();terminal=actual.originalTermination();
            keys=history.histories.clone();bindings=history.bindingHistories.clone();pose=history.poseHistory;metadata=history.metadata;
        }
        private boolean boundCurrent(){return Thread.currentThread()==thread&&installed==history&&!history.retired
            &&history.available&&history.passiveTransition==this&&actual.originalInputPublication()==publication
            &&actual.originalInstallation()==history.originalInstallation&&actual.originalTermination()==terminal
            &&(terminal==null||terminalBoundCurrent(history,actual));}
        private boolean unchangedNativeCurrent(){
            var h=history;if(!boundCurrent()||!h.nativeCurrent(-1,false)||pose!=h.poseHistory||metadata!=h.metadata)return false;
            for(int i=0;i<keys.length;i++)if(keys[i]!=h.histories[i]||bindings[i]!=h.bindingHistories[i])return false;
            return true;
        }
        private boolean beforeOriginalCurrent(Module original){
            var terminal=actual.originalTermination();
            return terminal==null?publication.currentFor(original):terminal.originalModule()==original&&terminalBoundCurrent(history,actual);
        }
        boolean beforeScopeCurrent(McFarmOwnedCleanup.NativeScope scope){return !finished&&scope!=null&&unchangedNativeCurrent()
            &&actual.sourceBeforeCurrent(history.client)&&beforeOriginalCurrent(scope.farm)
            &&scope.root==actual.registryIdentity()&&scope.root.lifecycleSameHistory(scope.lifecycle,actual.beforeLifecycle())
            &&scope.activeReleaseCurrent();}
        boolean normalScopeCurrent(McFarmOwnedCleanup.NativeScope scope){return !finished&&scope!=null&&unchangedNativeCurrent()
            &&actual.normalReturnedCurrent(history.client)&&scope.root==actual.registryIdentity()
            &&scope.root.lifecycleSameHistory(scope.lifecycle,actual.beforeLifecycle());}
        boolean normalCurrent(){return !finished&&unchangedNativeCurrent()&&actual.normalReturnedCurrent(history.client);}
        public boolean commitWindowCurrent(){return committing&&!finished&&boundCurrent();}
        VeltraphBootstrap.RootPassiveTransition actual(){return actual;}
        Minecraft client(){return history.client;}
    }
    /** Only the old ticket captured before genuine stop callbacks can span the later terminal passive rows. */
    private static boolean terminalBoundCurrent(RootNativeHistory h,VeltraphBootstrap.RootPassiveTransition actual){
        var terminal=actual.originalTermination();var ticket=h.terminalTicket;
        return terminal!=null&&ticket!=null&&ticket.source==terminal&&ticket.history==h&&!ticket.consumed
            &&terminal.originalEnrollment()==h.source&&terminal.installationIdentity()==h.originalInstallation;
    }
    public static PassiveTransitionObservation beginPassiveTransition(VeltraphBootstrap.RootPassiveTransition actual,Minecraft client){
        var h=installed;
        try{
            if(actual==null||h==null||h.client!=client||h.retired||!h.available||actual.originalInputPublication()==null
                    ||actual.originalInstallation()!=h.originalInstallation||!actual.sourceBeforeCurrent(client)
                    ||!h.nativeCurrent(-1,false))return null;
            // Farm may already be normally disabled in the real production registration order.
            // The exact previously captured terminal ticket replaces no history and never revives its old PUB.
            if(actual.originalTermination()!=null){if(!terminalBoundCurrent(h,actual))return null;}
            else if(!actual.originalInputPublication().nativeHistoryCurrent())return null;
            if(h.passiveTransition!=null){h.available=false;return null;}
            var next=new PassiveTransitionObservation(h,actual);h.passiveTransition=next;
            next.held=McInputService.capturePassiveHeldContinuation(next);
            next.breaking=McBlockBreakingService.capturePassiveBreakingContinuation(next);
            next.camera=McRotationService.capturePassiveCameraContinuation(next);
            if(next.held==null||next.breaking==null||next.camera==null||!next.unchangedNativeCurrent()||!actual.sourceBeforeCurrent(client)){
                McRotationService.closePassiveCameraContinuation(next.camera);
                h.available=false;h.passiveTransition=null;return null;
            }
            return next;
        }catch(RuntimeException|Error unavailable){if(h!=null&&h.passiveTransition!=null&&h.passiveTransition.actual==actual){h.available=false;h.passiveTransition=null;}return null;}
    }
    public static void finishPassiveTransition(PassiveTransitionObservation observation,VeltraphBootstrap.RootPassiveTransition actual,Minecraft client){
        if(observation==null||observation.actual!=actual||observation.history.client!=client)return;
        var h=observation.history;
        try{
            if(!observation.normalCurrent()){h.available=false;return;}
            var held=McInputService.preparePassiveHeldContinuation(observation.held,observation);
            var breaking=McBlockBreakingService.preparePassiveBreakingContinuation(observation.breaking,observation);
            var camera=McRotationService.preparePassiveCameraContinuation(observation.camera,observation);
            if(held==null||breaking==null||camera==null||!observation.normalCurrent()) {h.available=false;return;}
            // Both plans complete all source/native reads before either private ledger is changed.
            if(!McInputService.passiveHeldCommitCurrent(held)||!McBlockBreakingService.passiveBreakingCommitCurrent(breaking)
                    ||!McRotationService.passiveCameraCommitCurrent(camera)){
                h.available=false;return;
            }
            observation.committing=true;
            try{
                if(!McInputService.commitPassiveHeldContinuation(held)||!McBlockBreakingService.commitPassiveBreakingContinuation(breaking)
                        ||!McRotationService.commitPassiveCameraContinuation(camera)){
                    h.available=false;return;
                }
                observation.accepted=true;observation.finished=true;
            }finally{observation.committing=false;}
        }catch(RuntimeException|Error unavailable){h.available=false;}
    }
    public static void closePassiveTransition(PassiveTransitionObservation observation,VeltraphBootstrap.RootPassiveTransition actual,Minecraft client){
        if(observation==null||observation.actual!=actual||observation.history.client!=client
                ||Thread.currentThread()!=observation.thread)return;
        var h=observation.history;if(h.passiveTransition!=observation)return;
        if(!observation.finished||!observation.accepted){h.available=false;observation.finished=true;}
        McRotationService.closePassiveCameraContinuation(observation.camera);
        h.passiveTransition=null;
    }

    private RootNativeHistory(VeltraphBootstrap.RootOriginalNativeEnrollment source,Minecraft client,McNativeManualInputLineage.NoOwnerInputHandout handout,McOwnedRotation.NativeMetadataSnapshot actualMetadata) {
        this.source=source;this.client=client;player=client.player;world=client.level;listener=client.getConnection();
        connection=client.getConnection().getConnection();options=client.options;gameMode=client.gameMode;
        rotation=McOwnedRotation.installationSnapshot(client);originalInstallation=VeltraphBootstrap.originalNativeInstallationFor(client);metadata=actualMetadata;
        keys=currentKeys(client);carriers=new NormalIdleKeyCarrier[keys.length];histories=new Object[keys.length];
        bindings=new String[keys.length];bindingHistories=new Object[keys.length];expectedHeld=new boolean[keys.length];
        for(int i=0;i<keys.length;i++) {
            var key=keys[i];
            if(key==null||(key.getClass()!=KeyMapping.class&&key.getClass()!=ToggleKeyMapping.class)
                    ||!(key instanceof NormalIdleKeyCarrier carrier)) {available=false;continue;}
            carriers[i]=carrier;histories[i]=handout.keySnapshot(key);bindings[i]=handout.binding(key);bindingHistories[i]=handout.bindingSnapshot(key);expectedHeld[i]=handout.held(key);
            if(histories[i]==null||!carrier.normalIdleKeyCurrent(histories[i])||bindingHistories[i]==null
                    ||!NativeKeyBindingHistory.current(key,bindingHistories[i]))available=false;
        }
        poses=(OwnedPoseHistoryCarrier)(Object)client.player;
        poseHistory=handout.poseSnapshot(poses);expectedYaw=handout.yaw();expectedPitch=handout.pitch();
        if(poseHistory==null||!poses.ownedPoseHistoryCurrent(poseHistory)||!finitePose())available=false;
    }
    /** Existing enrolled source only. Issuers must capture this before callbacks/native mutation, never renew through lookup. */
    public static VeltraphBootstrap.RootOriginalNativeEnrollment enrolledSourceFor(Module original,Minecraft client){
        var next=installed;if(next==null||!next.available||next.client!=client||original==null)return null;
        try{return next.source.originalProducerCurrent(original,client)&&next.nativeCurrent(-1,false)?next.source:null;}
        catch(RuntimeException|Error unavailable){return null;}
    }
    /** An actual existing native/manual handout retained across the private fresh Guard request. No history is seeded here. */
    public static final class FreshEnableTicket {
        private final VeltraphBootstrap.RootOriginalNativeEnrollment enrollment;private final Minecraft client;
        private final McNativeManualInputLineage.NoOwnerInputHandout handout;private final McOwnedRotation.NativeMetadataSnapshot metadata;
        private boolean consumed;
        private FreshEnableTicket(VeltraphBootstrap.RootOriginalNativeEnrollment enrollment,Minecraft client,
                McNativeManualInputLineage.NoOwnerInputHandout handout,McOwnedRotation.NativeMetadataSnapshot metadata){
            this.enrollment=enrollment;this.client=client;this.handout=handout;this.metadata=metadata;
        }
        public boolean currentFor(VeltraphBootstrap.RootOriginalNativeEnrollment exact,Minecraft actualClient){
            try{return !consumed&&exact==enrollment&&actualClient==client&&client.isSameThread()&&enrollment.freshAdmissionCurrent(client)
                &&handout.current(client)&&metadata.current();}catch(RuntimeException|Error unavailable){return false;}
        }
    }
    public static FreshEnableTicket prepareFreshEnable(VeltraphBootstrap.RootOriginalNativeEnrollment enrollment,Minecraft client){
        try {
            if(enrollment==null||client==null||!client.isSameThread()||!enrollment.freshAdmissionCurrent(client)
                    ||!McInputService.canonicalFarmRootCurrent(client))return null;
            var nativeHandout=McNativeManualInputLineage.peekNoOwner(client);var metadata=McOwnedRotation.knownIdleMetadataSnapshot(client);
            if(nativeHandout==null||!nativeHandout.current(client)||metadata==null||!metadata.current())return null;
            var ticket=new FreshEnableTicket(enrollment,client,nativeHandout,metadata);return ticket.currentFor(enrollment,client)?ticket:null;
        }catch(RuntimeException|Error unavailable){return null;}
    }
    /** New origin only; an old revoked workflow is never refreshed from this handout or current bits. */
    public static RootNativeHistory enrollFreshEnable(VeltraphBootstrap.RootOriginalNativeEnrollment enrollment,FreshEnableTicket ticket,Minecraft client){
        if(enrollment==null||ticket==null||client==null||enrollment==attemptedEnrollment)return null;
        try {
            if(!ticket.currentFor(enrollment,client)||client.player==null||client.player.getClass()!=LocalPlayer.class
                    ||!((Object)client.player instanceof OwnedPoseHistoryCarrier)||client.level==null||client.getConnection()==null
                    ||client.options==null||client.gameMode==null)return null;
            attemptedEnrollment=enrollment;ticket.consumed=true;
            var next=new RootNativeHistory(enrollment,client,ticket.handout,ticket.metadata);
            if(!next.nativeCurrent(-1,false)||!ticket.handout.current(client)||!enrollment.freshAdmissionCurrent(client))return null;
            installed=next;return next;
        }catch(RuntimeException|Error unavailable){return null;}
    }
    /** Compatibility entry still requires the same authentic fresh request/native handout; it cannot seed current fields. */
    public static RootNativeHistory enroll(VeltraphBootstrap.RootOriginalNativeEnrollment source,Minecraft client){
        var ticket=prepareFreshEnable(source,client);return ticket==null?null:enrollFreshEnable(source,ticket,client);
    }
    /** Read-only origin check. Current bits are negative checks against authentic captured/returned values. */
    public boolean currentFor(Module original,Minecraft actualClient) {
        if(retired||!available||installed!=this||actualClient!=client||original==null)return false;
        try {
            if(!source.originalProducerCurrent(original,client)||!nativeCurrent(-1,false)){available=false;return false;}
            return true;
        } catch(RuntimeException|Error unavailable) {available=false;return false;}
    }
    /** Read-only preeffect check. Only the exact call's real already-returned tokens can explain its changed key. */
    public boolean wartDropKeyEffectCurrent(Module original,Minecraft actualClient,KeyMapping key,java.util.List<McHeldKeys.NativeInputInvocation> actual){
        if(actual==null||retired||!available||installed!=this||actualClient!=client||original==null)return false;
        int slot=-1;for(int i=0;i<keys.length;i++)if(keys[i]==key){slot=i;break;}
        if(slot<0||!source.originalProducerCurrent(original,client)||!nativeCurrent(actual.isEmpty()?-1:slot,false))return false;
        return carriers[slot].normalIdleKeyAdvanced(histories[slot],carriers[slot].normalIdleKeySnapshot(),actual);
    }
    /** Final-field history: unchanged OTHER8/pose/bindings/metadata plus the actual one-key in-flight prefix. */
    public boolean wartDropKeyFieldCurrent(Minecraft actualClient,KeyMapping key,java.util.List<McHeldKeys.NativeInputInvocation> returned,
            McHeldKeys.NativeInputInvocation pending,int depth){
        if(returned==null||pending==null||retired||!available||installed!=this||actualClient!=client)return false;
        int slot=-1;for(int i=0;i<keys.length;i++)if(keys[i]==key){slot=i;break;}
        return slot>=0&&nativeCurrent(slot,false)&&carriers[slot].normalIdleKeyInFlightCurrent(histories[slot],returned,pending,depth);
    }
    private static KeyMapping[] currentKeys(Minecraft client) {
        var o=client.options;
        return new KeyMapping[]{o.keyUp,o.keyDown,o.keyLeft,o.keyRight,o.keyJump,o.keyShift,o.keySprint,o.keyAttack,o.keyUse};
    }
    private boolean finitePose(){return Float.isFinite(expectedYaw)&&Float.isFinite(expectedPitch)&&Math.abs(expectedPitch)<=90;}
    private boolean nativeCurrent(int changedKey,boolean changedPose) {return nativeCurrent(changedKey,changedPose,false);}
    private boolean nativeCurrent(int changedKey,boolean changedPose,boolean changedMetadata) {
        if(!available||client!=Minecraft.getInstance()||!client.isSameThread()||!McInputService.canonicalFarmRootCurrent(client)
                ||client.player!=player||client.level!=world||client.getConnection()!=listener||client.options!=options
                ||client.gameMode!=gameMode||client.getConnection()==null||client.getConnection().getConnection()!=connection
                ||!((net.minecraft.network.Connection)connection).isConnected()||rotation==null||!rotation.current()
                ||originalInstallation==null||VeltraphBootstrap.originalNativeInstallationFor(client)!=originalInstallation||!originalInstallation.currentFor(client)
                ||!changedMetadata&&(metadata==null||!metadata.current()))return false;
        var actual=currentKeys(client);
        for(int i=0;i<keys.length;i++) {
            if(keys[i]!=actual[i]||carriers[i]==null||histories[i]==null||bindings[i]==null
                    ||!bindings[i].equals(keys[i].saveString())||bindingHistories[i]==null
                    ||!NativeKeyBindingHistory.current(keys[i],bindingHistories[i]))return false;
            if(changedKey!=-2&&i!=changedKey&&(!carriers[i].normalIdleKeyCurrent(histories[i])||keys[i].isDown()!=expectedHeld[i]))return false;
        }
        return changedPose||poses.ownedPoseHistoryCurrent(poseHistory)
                &&Float.floatToIntBits(client.player.getYRot())==Float.floatToIntBits(expectedYaw)
                &&Float.floatToIntBits(client.player.getXRot())==Float.floatToIntBits(expectedPitch);
    }
    private void keyReturned(Module original,KeyMapping key,McHeldKeys.ScopedHeldReceipt receipt,boolean actualSource) {
        if(!available||installed!=this)return;
        int index=-1;for(int i=0;i<keys.length;i++)if(keys[i]==key){index=i;break;}
        if(index<0||!actualSource||!source.originalProducerCurrent(original,client)||!nativeCurrent(index,false)
                ||receipt==null||!receipt.confirmed()||receipt.key()!=key||key.isDown()!=receipt.held()
                ||!receipt.normalIdleHistoryCurrent()||!receipt.normalIdleHistoryAdvancedFrom(histories[index])) {
            available=false;return;
        }
        // No-op calls retain the original history and expected value; they never enroll foreign history.
        if(receipt.nativeSetAttempts()==0&&receipt.restorationAttempts()==0) {
            if(!carriers[index].normalIdleKeyCurrent(histories[index])||expectedHeld[index]!=receipt.held())available=false;
            return;
        }
        var after=carriers[index].normalIdleKeySnapshot();
        if(after==null||!carriers[index].normalIdleKeyCurrent(after)||!receipt.normalIdleHistoryCurrent()
                ||!source.originalProducerCurrent(original,client)||!nativeCurrent(index,false)){available=false;return;}
        histories[index]=after;expectedHeld[index]=receipt.held();
        if(!receipt.held())originalNormalOwnedKeys[index]=null;
        else if(receipt.normallyReturnedOwnedHeldAttempt())originalNormalOwnedKeys[index]=receipt;
        if(!nativeCurrent(-1,false))available=false;
    }
    static void normalKeyReturned(McInputService.KeyNormalReturn actual) {
        var next=installed;if(next==null)return;
        try {next.retainPauseNormalNegativeReturn(actual.originalModule(),actual.receipt);
            next.keyReturned(actual.originalModule(),actual.receipt.key(),actual.receipt,actual.nativeSourceCurrent());}
        catch(RuntimeException|Error unavailable){next.available=false;}
    }
    static void normalBreakingReturned(McBlockBreakingService.KeyNormalReturn actual) {
        var next=installed;if(next==null)return;
        try {next.retainPauseNormalNegativeReturn(actual.originalModule(),actual.receipt);
            next.keyReturned(actual.originalModule(),actual.receipt.key(),actual.receipt,actual.nativeSourceCurrent());}
        catch(RuntimeException|Error unavailable){next.available=false;}
    }
    /** The SDK entry is captured before init/removed callbacks, independently of requested screen identity. */
    public static final class GuiTicket {
        private final RootNativeHistory history;private final NativeGuiKeyResetLineage.ActualGuiEntry entry;
        private final VeltraphBootstrap.RootOriginalInputPublication publication;
        private final VeltraphBootstrap.RootOriginalMenuRequest menu;private final Object[] beforeKeys;
        private final Object beforePose;private NativeGuiKeyResetLineage.ActualGuiOutcome outcome;
        private boolean finished,accepted,failed;
        private McInputService.GuiHeldRetirement heldRetirement;
        private McBlockBreakingService.GuiBreakingRetirement breakingRetirement;
        private GuiTicket(RootNativeHistory history,NativeGuiKeyResetLineage.ActualGuiEntry entry,VeltraphBootstrap.RootOriginalInputPublication publication,VeltraphBootstrap.RootOriginalMenuRequest menu){
            this.history=history;this.entry=entry;this.publication=publication;this.menu=menu;beforeKeys=history.histories.clone();beforePose=history.poseHistory;
        }
        private boolean sourceCurrent(){var h=history;return !failed&&installed==h&&!h.retired&&h.available
            &&(menu==null?VeltraphBootstrap.originalNativeGuiAffiliationCurrent(publication,h.client)
                :menu.enrollmentIdentity()==h.source&&(menu.nativeCloseObservationCurrent(h.client)||menu.normalReturnedNativeCloseCurrent(h.client)))
            &&(accepted?h.client.gui.screen()==entry.nextScreen()&&h.client.isWindowActive()&&!h.client.isPaused()
                :entry.matchesNative(h.client,h.options,h.player,h.world,h.listener,VeltraphBootstrap.moduleManager()));}
    }
    public static GuiTicket beginActualGui(NativeGuiKeyResetLineage.ActualGuiEntry actual,Minecraft client){
        var h=installed;
        try{if(actual==null||h==null||h.client!=client||h.retired||!h.available||h.guiTicket!=null&&!h.guiTicket.finished
                ||!actual.beforeCurrent(client)||!h.nativeCurrent(-1,false))return null;
            var publication=VeltraphBootstrap.originalPublicationForNativeGui(client);
            var menu=publication==null?VeltraphBootstrap.originalMenuRequestForNativeClose(client):null;
            if(publication==null&&(menu==null||actual.nextScreen()!=null||menu.enrollmentIdentity()!=h.source||!menu.beforeNativeCloseCurrent(client)))return null;
            if(publication!=null&&!VeltraphBootstrap.originalNativeGuiAffiliationCurrent(publication,client)
                    ||!actual.matchesNative(client,h.options,h.player,h.world,h.listener,VeltraphBootstrap.moduleManager()))return null;
            for(int i=0;i<h.keys.length;i++)if(actual.beforeHeld(h.keys[i])!=h.expectedHeld[i]||!h.bindings[i].equals(actual.binding(h.keys[i]))
                    ||!h.carriers[i].normalIdleKeyAdvanced(h.histories[i],actual.before(h.keys[i]),java.util.List.of()))return null;
            if(!h.poses.ownedPoseHistoryAdvanced(h.poseHistory,actual.beforePose(h.poses),java.util.List.of(),java.util.List.of()))return null;
            var ticket=new GuiTicket(h,actual,publication,menu);h.guiTicket=ticket;
            ticket.heldRetirement=McInputService.captureGuiHeldRetirement(ticket,client);
            ticket.breakingRetirement=McBlockBreakingService.captureGuiBreakingRetirement(ticket,client);
            if(ticket.heldRetirement==null||ticket.breakingRetirement==null){ticket.failed=true;h.available=false;return null;}return ticket;
        }catch(RuntimeException|Error unavailable){return null;}
    }
    public static void finishActualGui(GuiTicket ticket,NativeGuiKeyResetLineage.ActualGuiOutcome outcome,Minecraft client){
        if(ticket==null||ticket.finished||ticket.history.client!=client)return;ticket.finished=true;var h=ticket.history;
        try{ticket.outcome=outcome;var transition=outcome==null?null:outcome.normalTransition();
            if(h.guiTicket!=ticket||!ticket.sourceCurrent()||outcome==null||outcome.entryIdentity()!=ticket.entry||!outcome.wholeNormal()
                    ||!outcome.nativeContextCurrent(client)||transition==null||!transition.current(client)||!h.nativeCurrent(-2,false)){
                ticket.failed=true;h.available=false;return;
            }
            for(int i=0;i<h.keys.length;i++)if(!transition.advances(h.keys[i],ticket.beforeKeys[i])||transition.after(h.keys[i])==null
                    ||!h.bindings[i].equals(transition.binding(h.keys[i]))){ticket.failed=true;h.available=false;return;}
            // SDK false resets retire exact old held custody. No Root/field value creates a replacement receipt.
            if(!McInputService.retireGuiClearedOwnedKeys(ticket.heldRetirement,ticket,transition)
                    ||!McBlockBreakingService.retireGuiClearedOwnedBreaking(ticket.breakingRetirement,ticket,transition)){ticket.failed=true;h.available=false;return;}
            for(int i=0;i<h.keys.length;i++){h.histories[i]=transition.after(h.keys[i]);h.expectedHeld[i]=transition.held(h.keys[i]);}
            ticket.accepted=true;if(!h.nativeCurrent(-1,false)){ticket.failed=true;h.available=false;}
        }catch(RuntimeException|Error unavailable){ticket.failed=true;h.available=false;}
    }
    public static void failedActualGui(GuiTicket ticket,NativeGuiKeyResetLineage.ActualGuiOutcome outcome,Minecraft client){
        if(ticket==null||ticket.history.client!=client||ticket.finished)return;ticket.finished=true;ticket.failed=true;ticket.outcome=outcome;
        ticket.history.available=false; // A failed or unknown native GUI operation cannot become a normal handoff.
    }
    static boolean guiCaptureCurrent(GuiTicket ticket,Minecraft client){
        try{return ticket!=null&&ticket.history.client==client&&!ticket.finished&&!ticket.failed&&ticket.history.guiTicket==ticket
            &&ticket.entry.beforeCurrent(client)&&ticket.sourceCurrent()&&ticket.history.nativeCurrent(-1,false);}
        catch(RuntimeException|Error unavailable){return false;}
    }
    static boolean guiOriginalCurrent(GuiTicket ticket,Module original){
        if(ticket==null)return false;
        return ticket.menu==null?ticket.publication!=null&&ticket.publication.currentFor(original)
            :ticket.menu.originalModule()==original&&ticket.menu.enrollmentIdentity()==ticket.history.source
                &&ticket.menu.beforeNativeCloseCurrent(ticket.history.client)&&ticket.history.currentFor(original,ticket.history.client);
    }
    static Object guiBeforeKey(GuiTicket ticket,KeyMapping key){
        if(ticket==null)return null;for(int i=0;i<ticket.history.keys.length;i++)if(ticket.history.keys[i]==key)return ticket.beforeKeys[i];return null;
    }
    /** Callback-free data affiliation for exact canonical custody metadata retirement, never input permission. */
    static boolean guiRetirementCurrent(GuiTicket ticket,NativeGuiKeyResetLineage.NormalTransition actual,Minecraft client){
        try{return ticket!=null&&ticket.history.client==client&&!ticket.failed&&ticket.finished&&ticket.history.guiTicket==ticket
            &&ticket.outcome!=null&&ticket.outcome.normalTransition()==actual&&ticket.sourceCurrent()&&actual.current(client);}
        catch(RuntimeException|Error unavailable){return false;}
    }
    private static final class SavedMaintenanceRestoreSequence {
        private final RootNativeHistory history;private final VeltraphBootstrap.RootSavedMaintenanceResumeInvocation source;
        private final McHeldKeys.ScopedHeldReceipt[] receipts=new McHeldKeys.ScopedHeldReceipt[9];private int next;
        private SavedMaintenanceRestoreSequence(RootNativeHistory history,VeltraphBootstrap.RootSavedMaintenanceResumeInvocation source){this.history=history;this.source=source;}
    }
    /** A passive old-history window for one genuinely issued original saved-restoration instruction. */
    public static final class SavedMaintenanceKeyObservation {
        private final RootNativeHistory history;private final dev.veltraph.client.rewarp.mc.FarmMaintenanceCoordinator.SavedMaintenanceRestoreKey instruction;
        private final McHeldKeys.SavedMaintenanceKeyInvocation invocation;private final SavedMaintenanceRestoreSequence sequence;
        private final int index;private final Object before;private boolean consumed,accepted,closed;
        private SavedMaintenanceKeyObservation(RootNativeHistory history,dev.veltraph.client.rewarp.mc.FarmMaintenanceCoordinator.SavedMaintenanceRestoreKey instruction,
                McHeldKeys.SavedMaintenanceKeyInvocation invocation,SavedMaintenanceRestoreSequence sequence){
            this.history=history;this.instruction=instruction;this.invocation=invocation;this.sequence=sequence;index=instruction.index();before=history.histories[index];
        }
        private boolean sourceCurrent(){var h=history;var root=instruction.rootResumeIdentity();return !closed&&installed==h&&!h.retired&&h.available
            &&h.savedMaintenanceKey==this&&h.savedRestoration==sequence&&sequence.history==h&&sequence.source==root
            &&root!=null&&root.enrollmentIdentity()==h.source&&root.originalPublication()==instruction.originalInputPublication()
            &&root.originalFarm()==instruction.farm()&&root.beforeRestoreNativeCurrent(instruction.farm(),h.client)
            &&invocation.client()==h.client&&invocation.instruction()==instruction&&invocation.matchesInstruction(instruction)
            &&instruction.client()==h.client&&instruction.key()==h.keys[index]&&instruction.beforeNativeCurrent()
            &&h.source.originalProducerCurrent(instruction.farm(),h.client);}
        /** Final selected-field veto: pure source facts and OTHER8 old native anchors only. */
        public boolean finalNativeCurrent(McHeldKeys.SavedMaintenanceKeyInvocation exact){
            var h=history;
            try{return exact==invocation&&!closed&&installed==h&&!h.retired&&h.available&&h.savedMaintenanceKey==this
                &&h.savedRestoration==sequence&&sequence.history==h&&sequence.source==instruction.rootResumeIdentity()
                &&invocation.client()==h.client&&invocation.instruction()==instruction&&invocation.matchesInstruction(instruction)
                &&instruction.index()==index&&instruction.key()==h.keys[index]&&instruction.finalNativeSourceCurrent()
                &&instruction.rootResumeIdentity()!=null&&instruction.rootResumeIdentity().enrollmentIdentity()==h.source
                &&instruction.rootResumeIdentity().originalPublication()==instruction.originalInputPublication()
                &&h.nativeCurrent(index,false);}
            catch(RuntimeException|Error unavailable){return false;}
        }
        /** Original accepted selected-key history for the actual private carrier in-flight prefix check. */
        public Object originalKeyHistory(){return before;}
        /** Full original nine-key state immediately before an actual effect. */
        public boolean beforeNativeCurrent(){try{return !consumed&&sourceCurrent()&&history.nativeCurrent(-1,false);}
            catch(RuntimeException|Error unavailable){return false;}}
        /** After the actual selected opcode, only that key's old history is excluded. Remains readable after accepted consume. */
        public boolean otherNativeCurrent(){try{return sourceCurrent()&&history.nativeCurrent(index,false);}
            catch(RuntimeException|Error unavailable){return false;}}
    }
    public static SavedMaintenanceKeyObservation beginSavedMaintenanceKey(
            dev.veltraph.client.rewarp.mc.FarmMaintenanceCoordinator.SavedMaintenanceRestoreKey instruction,McHeldKeys.SavedMaintenanceKeyInvocation actual){
        var h=installed;
        try{if(instruction==null||actual==null||h==null||h.retired||!h.available||h.savedMaintenanceKey!=null
                ||h.savedMaintenanceRollbackKey!=null||h.savedRollback!=null||h.retainedMaintenanceKey!=null||h.retainedRestoration!=null
                ||actual.client()!=h.client||actual.instruction()!=instruction||!actual.matchesInstruction(instruction)
                ||instruction.index()<0||instruction.index()>=9||instruction.key()!=h.keys[instruction.index()]||!instruction.beforeNativeCurrent()
                ||instruction.rootResumeIdentity()==null||instruction.rootResumeIdentity().enrollmentIdentity()!=h.source
                ||instruction.rootResumeIdentity().originalPublication()!=instruction.originalInputPublication()
                ||!instruction.rootResumeIdentity().beforeRestoreNativeCurrent(instruction.farm(),h.client)
                ||!h.currentFor(instruction.farm(),h.client))return null;
            var sequence=h.savedRestoration;
            if(sequence==null){if(instruction.index()!=0)return null;sequence=new SavedMaintenanceRestoreSequence(h,instruction.rootResumeIdentity());h.savedRestoration=sequence;}
            if(sequence.source!=instruction.rootResumeIdentity()||sequence.next!=instruction.index())return null;
            var ticket=new SavedMaintenanceKeyObservation(h,instruction,actual,sequence);h.savedMaintenanceKey=ticket;
            if(!ticket.beforeNativeCurrent()){h.savedMaintenanceKey=null;h.available=false;return null;}return ticket;
        }catch(RuntimeException|Error unavailable){if(h!=null)h.available=false;return null;}
    }
    public static void acceptSavedMaintenanceKeyNormal(SavedMaintenanceKeyObservation ticket,McHeldKeys.SavedMaintenanceKeyReceipt actual){
        if(ticket==null||ticket.closed||ticket.consumed)return;var h=ticket.history;
        try{if(actual==null||actual.instruction()!=ticket.instruction||actual.invocationIdentity()!=ticket.invocation||!actual.confirmed()
                ||!actual.normalReturnedNativeCurrent()||!ticket.otherNativeCurrent()||ticket.sequence.next!=ticket.index){h.available=false;return;}
            var receipt=actual.nativeReceipt();
            if(receipt==null||!receipt.confirmed()||receipt.key()!=ticket.instruction.key()||receipt.held()!=ticket.instruction.desiredHeld()
                    ||!receipt.normalIdleHistoryCurrent()||!receipt.normalIdleHistoryAdvancedFrom(ticket.before)){h.available=false;return;}
            // This uses the same confirmed private opcode receipt; a zero-effect return cannot renew foreign history.
            h.keyReturned(ticket.instruction.farm(),receipt.key(),receipt,true);
            if(!h.available||!h.nativeCurrent(-1,false)){h.available=false;return;}
            ticket.sequence.receipts[ticket.index]=receipt;ticket.sequence.next=ticket.index+1;ticket.accepted=true;
        }catch(RuntimeException|Error unavailable){h.available=false;}
        finally{ticket.consumed=true;}
    }
    /** Retires only the exact private per-key data window, including exceptional partial native effects. */
    public static void closeSavedMaintenanceKey(SavedMaintenanceKeyObservation ticket,McHeldKeys.SavedMaintenanceKeyInvocation actual){
        if(ticket==null||actual!=ticket.invocation||ticket.closed)return;var h=ticket.history;
        try{if(!ticket.consumed||!ticket.accepted||!actual.matchesInstruction(ticket.instruction)||!ticket.otherNativeCurrent()
                ||!h.nativeCurrent(-1,false))h.available=false;}
        catch(RuntimeException|Error unavailable){h.available=false;}
        finally{ticket.closed=true;if(h.savedMaintenanceKey==ticket)h.savedMaintenanceKey=null;}
    }
    /** Confirmed actual native receipts retained through real whole-resume completion; no values create custody. */
    static final class SavedResumeCustody {
        final RootNativeHistory history;final VeltraphBootstrap.RootSavedMaintenanceResumeInvocation source;
        final dev.veltraph.client.farming.module.FarmModule farm;final McFarmOwnedCleanup.NativeScope scope;
        final McHeldKeys.ScopedHeldReceipt[] receipts;
        private SavedResumeCustody(SavedMaintenanceRestoreSequence sequence,McFarmOwnedCleanup.NativeScope scope){
            history=sequence.history;source=sequence.source;farm=(dev.veltraph.client.farming.module.FarmModule)source.originalFarm();this.scope=scope;receipts=sequence.receipts.clone();
        }
        Minecraft client(){return history.client;}
        boolean current(){
            if(installed!=history||history.retired||!history.available||!source.normalReturnedCurrent(history.client)
                    ||!history.currentFor(farm,history.client)||scope.farm!=farm||!scope.activeCurrent()||!history.nativeCurrent(-1,false))return false;
            for(int i=0;i<9;i++){var receipt=receipts[i];if(receipt==null||receipt.key()!=history.keys[i]||!receipt.confirmed()
                    ||!receipt.normalIdleHistoryCurrent()||receipt.held()!=history.expectedHeld[i])return false;}
            return true;
        }
        McHeldKeys.ScopedHeldReceipt receipt(KeyMapping key){for(int i=0;i<history.keys.length;i++)if(history.keys[i]==key)return receipts[i];return null;}
    }
    /** Actual Root close after real lease resume; no native write or current-value rebaseline occurs here. */
    public static void closeSavedMaintenanceResume(VeltraphBootstrap.RootSavedMaintenanceResumeInvocation actual,Minecraft client){
        var h=installed;var sequence=h==null?null:h.savedRestoration;
        if(actual==null||h==null||h.client!=client||sequence==null||sequence.source!=actual)return;
        try{if(h.savedMaintenanceKey!=null||sequence.next!=9||!actual.normalReturnedCurrent(client)||!h.currentFor(actual.originalFarm(),client)){
                h.available=false;return;}
            for(int i=0;i<9;i++){var receipt=sequence.receipts[i];if(receipt==null||!receipt.confirmed()||receipt.key()!=h.keys[i]
                    ||receipt.held()!=h.expectedHeld[i]||!receipt.normalIdleHistoryCurrent()){h.available=false;return;}}
            if(!(actual.originalFarm() instanceof dev.veltraph.client.farming.module.FarmModule farm)){h.available=false;return;}
            var scope=McFarmOwnedCleanup.NativeScope.capture(client,farm);
            if(scope==null||!actual.normalReturnedCurrent(client)||!h.nativeCurrent(-1,false)){h.available=false;return;}
            var custody=new SavedResumeCustody(sequence,scope);
            if(!custody.current()||!McInputService.adoptSavedResumeOwnedKeys(custody)||!McBlockBreakingService.adoptSavedResumeOwnedBreaking(custody)
                    ||!custody.current()){h.available=false;return;}
        }catch(RuntimeException|Error unavailable){h.available=false;}
        finally{if(h.savedRestoration==sequence)h.savedRestoration=null;}
    }
    private static final class RetainedMaintenanceRestoreSequence {
        private final RootNativeHistory history;private final VeltraphBootstrap.RootRetainedMaintenanceResumeInvocation source;
        private final McHeldKeys.ScopedHeldReceipt[] receipts=new McHeldKeys.ScopedHeldReceipt[9];private int next;
        private RetainedMaintenanceRestoreSequence(RootNativeHistory history,VeltraphBootstrap.RootRetainedMaintenanceResumeInvocation source){this.history=history;this.source=source;}
    }
    /** A passive old-history window for one genuinely issued original retained-restoration instruction. */
    public static final class RetainedMaintenanceKeyObservation {
        private final RootNativeHistory history;private final dev.veltraph.client.rewarp.mc.FarmMaintenanceCoordinator.RetainedMaintenanceRestoreKey instruction;
        private final McHeldKeys.RetainedMaintenanceKeyInvocation invocation;private final RetainedMaintenanceRestoreSequence sequence;
        private final int index;private final Object before;private boolean consumed,accepted,closed;
        private RetainedMaintenanceKeyObservation(RootNativeHistory history,dev.veltraph.client.rewarp.mc.FarmMaintenanceCoordinator.RetainedMaintenanceRestoreKey instruction,
                McHeldKeys.RetainedMaintenanceKeyInvocation invocation,RetainedMaintenanceRestoreSequence sequence){
            this.history=history;this.instruction=instruction;this.invocation=invocation;this.sequence=sequence;index=instruction.index();before=history.histories[index];
        }
        private boolean sourceCurrent(){var h=history;var root=instruction.rootResumeIdentity();return !closed&&installed==h&&!h.retired&&h.available
            &&h.retainedMaintenanceKey==this&&h.retainedRestoration==sequence&&sequence.history==h&&sequence.source==root
            &&root!=null&&root.enrollmentIdentity()==h.source&&root.originalPublication()==instruction.originalInputPublication()
            &&root.matchesRetained(instruction.sourceCall())&&root.originalFarm()==instruction.farm()&&root.beforeRestoreNativeCurrent(instruction.farm(),h.client)
            &&invocation.client()==h.client&&invocation.instruction()==instruction&&invocation.matchesInstruction(instruction)
            &&instruction.client()==h.client&&instruction.key()==h.keys[index]&&instruction.beforeNativeCurrent()
            &&h.source.originalProducerCurrent(instruction.farm(),h.client);}
        /** Final selected-field veto: pure source facts and OTHER8 old native anchors only. */
        public boolean finalNativeCurrent(McHeldKeys.RetainedMaintenanceKeyInvocation exact){
            var h=history;
            try{return exact==invocation&&!closed&&installed==h&&!h.retired&&h.available&&h.retainedMaintenanceKey==this
                &&h.retainedRestoration==sequence&&sequence.history==h&&sequence.source==instruction.rootResumeIdentity()
                &&invocation.client()==h.client&&invocation.instruction()==instruction&&invocation.matchesInstruction(instruction)
                &&instruction.index()==index&&instruction.key()==h.keys[index]&&instruction.finalNativeSourceCurrent()
                &&instruction.rootResumeIdentity()!=null&&instruction.rootResumeIdentity().matchesRetained(instruction.sourceCall())&&instruction.rootResumeIdentity().enrollmentIdentity()==h.source
                &&instruction.rootResumeIdentity().originalPublication()==instruction.originalInputPublication()
                &&h.nativeCurrent(index,false)&&instruction.finalStoredFieldFactsCurrent();}
            catch(RuntimeException|Error unavailable){return false;}
        }
        /** Original accepted selected-key history for the actual private carrier in-flight prefix check. */
        public Object originalKeyHistory(){return before;}
        /** Full original nine-key state immediately before an actual effect. */
        public boolean beforeNativeCurrent(){try{return !consumed&&sourceCurrent()&&history.nativeCurrent(-1,false);}
            catch(RuntimeException|Error unavailable){return false;}}
        /** After the actual selected opcode, only that key's old history is excluded. Remains readable after accepted consume. */
        public boolean otherNativeCurrent(){try{return sourceCurrent()&&history.nativeCurrent(index,false);}
            catch(RuntimeException|Error unavailable){return false;}}
    }
    public static RetainedMaintenanceKeyObservation beginRetainedMaintenanceKey(
            dev.veltraph.client.rewarp.mc.FarmMaintenanceCoordinator.RetainedMaintenanceRestoreKey instruction,McHeldKeys.RetainedMaintenanceKeyInvocation actual){
        var h=installed;
        try{if(instruction==null||actual==null||h==null||h.retired||!h.available||h.retainedMaintenanceKey!=null
                ||h.savedMaintenanceKey!=null||h.savedRestoration!=null||h.savedMaintenanceRollbackKey!=null||h.savedRollback!=null
                ||actual.client()!=h.client||actual.instruction()!=instruction||!actual.matchesInstruction(instruction)
                ||instruction.index()<0||instruction.index()>=9||instruction.key()!=h.keys[instruction.index()]||!instruction.beforeNativeCurrent()
                ||instruction.rootResumeIdentity()==null||instruction.rootResumeIdentity().enrollmentIdentity()!=h.source
                ||instruction.rootResumeIdentity().originalPublication()!=instruction.originalInputPublication()
                ||!instruction.rootResumeIdentity().beforeRestoreNativeCurrent(instruction.farm(),h.client)
                ||!h.currentFor(instruction.farm(),h.client))return null;
            var sequence=h.retainedRestoration;
            if(sequence==null){if(instruction.index()!=0)return null;sequence=new RetainedMaintenanceRestoreSequence(h,instruction.rootResumeIdentity());h.retainedRestoration=sequence;}
            if(sequence.source!=instruction.rootResumeIdentity()||sequence.next!=instruction.index())return null;
            var ticket=new RetainedMaintenanceKeyObservation(h,instruction,actual,sequence);h.retainedMaintenanceKey=ticket;
            if(!ticket.beforeNativeCurrent()){h.retainedMaintenanceKey=null;h.available=false;return null;}return ticket;
        }catch(RuntimeException|Error unavailable){if(h!=null)h.available=false;return null;}
    }
    public static void acceptRetainedMaintenanceKeyNormal(RetainedMaintenanceKeyObservation ticket,McHeldKeys.RetainedMaintenanceKeyReceipt actual){
        if(ticket==null||ticket.closed||ticket.consumed)return;var h=ticket.history;
        try{if(actual==null||actual.instruction()!=ticket.instruction||actual.invocationIdentity()!=ticket.invocation||!actual.confirmed()
                ||!actual.normalReturnedNativeCurrent()||!ticket.otherNativeCurrent()||ticket.sequence.next!=ticket.index){h.available=false;return;}
            var receipt=actual.nativeReceipt();
            if(receipt==null||!receipt.confirmed()||receipt.key()!=ticket.instruction.key()||receipt.held()!=ticket.instruction.desiredHeld()
                    ||!receipt.normalIdleHistoryCurrent()||!receipt.normalIdleHistoryAdvancedFrom(ticket.before)){h.available=false;return;}
            // This uses the same confirmed private opcode receipt; a zero-effect return cannot renew foreign history.
            h.keyReturned(ticket.instruction.farm(),receipt.key(),receipt,true);
            if(!h.available||!h.nativeCurrent(-1,false)){h.available=false;return;}
            ticket.sequence.receipts[ticket.index]=receipt;ticket.sequence.next=ticket.index+1;ticket.accepted=true;
        }catch(RuntimeException|Error unavailable){h.available=false;}
        finally{ticket.consumed=true;}
    }
    /** Retires only the exact private per-key data window, including exceptional partial native effects. */
    public static void closeRetainedMaintenanceKey(RetainedMaintenanceKeyObservation ticket,McHeldKeys.RetainedMaintenanceKeyInvocation actual){
        if(ticket==null||actual!=ticket.invocation||ticket.closed)return;var h=ticket.history;
        try{if(!ticket.consumed||!ticket.accepted||!actual.matchesInstruction(ticket.instruction)||!ticket.otherNativeCurrent()
                ||!h.nativeCurrent(-1,false))h.available=false;}
        catch(RuntimeException|Error unavailable){h.available=false;}
        finally{ticket.closed=true;if(h.retainedMaintenanceKey==ticket)h.retainedMaintenanceKey=null;}
    }
    /** Confirmed actual native receipts retained through real whole-resume completion; no values create custody. */
    static final class RetainedResumeCustody {
        final RootNativeHistory history;final VeltraphBootstrap.RootRetainedMaintenanceResumeInvocation source;
        final dev.veltraph.client.farming.module.FarmModule farm;final McFarmOwnedCleanup.NativeScope scope;
        final McHeldKeys.ScopedHeldReceipt[] receipts;
        private RetainedResumeCustody(RetainedMaintenanceRestoreSequence sequence,McFarmOwnedCleanup.NativeScope scope){
            history=sequence.history;source=sequence.source;farm=(dev.veltraph.client.farming.module.FarmModule)source.originalFarm();this.scope=scope;receipts=sequence.receipts.clone();
        }
        Minecraft client(){return history.client;}
        boolean current(){
            if(installed!=history||history.retired||!history.available||!source.normalReturnedCurrent(history.client)
                    ||!history.currentFor(farm,history.client)||scope.farm!=farm||!scope.activeCurrent()||!history.nativeCurrent(-1,false))return false;
            for(int i=0;i<9;i++){var receipt=receipts[i];if(receipt==null||receipt.key()!=history.keys[i]||!receipt.confirmed()
                    ||!receipt.normalIdleHistoryCurrent()||receipt.held()!=history.expectedHeld[i])return false;}
            return true;
        }
        McHeldKeys.ScopedHeldReceipt receipt(KeyMapping key){for(int i=0;i<history.keys.length;i++)if(history.keys[i]==key)return receipts[i];return null;}
    }
    /** Actual Root close after real lease resume; no native write or current-value rebaseline occurs here. */
    public static boolean closeRetainedMaintenanceResume(VeltraphBootstrap.RootRetainedMaintenanceResumeInvocation actual,Minecraft client){
        var h=installed;var sequence=h==null?null:h.retainedRestoration;
        if(actual==null||h==null||h.client!=client||sequence==null||sequence.source!=actual)return false;
        try{if(h.retainedMaintenanceKey!=null||sequence.next!=9||!actual.normalReturnedCurrent(client)||!h.currentFor(actual.originalFarm(),client)){
                h.available=false;return false;}
            for(int i=0;i<9;i++){var receipt=sequence.receipts[i];if(receipt==null||!receipt.confirmed()||receipt.key()!=h.keys[i]
                    ||receipt.held()!=h.expectedHeld[i]||!receipt.normalIdleHistoryCurrent()){h.available=false;return false;}}
            if(!(actual.originalFarm() instanceof dev.veltraph.client.farming.module.FarmModule farm)){h.available=false;return false;}
            var scope=McFarmOwnedCleanup.NativeScope.capture(client,farm);
            if(scope==null||!actual.normalReturnedCurrent(client)||!h.nativeCurrent(-1,false)){h.available=false;return false;}
            var custody=new RetainedResumeCustody(sequence,scope);
            if(!custody.current()||!McInputService.adoptRetainedResumeOwnedKeys(custody)||!McBlockBreakingService.adoptRetainedResumeOwnedBreaking(custody)
                    ||!custody.current()){h.available=false;return false;}
            return true;
        }catch(RuntimeException|Error unavailable){h.available=false;return false;}
        finally{if(h.retainedRestoration==sequence)h.retainedRestoration=null;}
    }
    private static final class SavedMaintenanceRollbackSequence {
        private final RootNativeHistory history;private final VeltraphBootstrap.RootSavedMaintenanceRollbackInvocation source;
        private final McHeldKeys.ScopedHeldReceipt[] receipts=new McHeldKeys.ScopedHeldReceipt[9];private int next;
        private SavedMaintenanceRollbackSequence(RootNativeHistory history,VeltraphBootstrap.RootSavedMaintenanceRollbackInvocation source){
            this.history=history;this.source=source;history.retainedRollbackPrefix=new RollbackPrefixHistory(history,source,source.originalCompletedPauseIdentity());
        }
    }
    /** A passive old-history window for one genuinely issued original saved-rollback instruction. */
    public static final class SavedMaintenanceRollbackKeyObservation {
        private final RootNativeHistory history;private final dev.veltraph.client.rewarp.mc.FarmMaintenanceCoordinator.SavedMaintenanceRollbackKey instruction;
        private final McHeldKeys.RollbackMaintenanceKeyInvocation invocation;private final SavedMaintenanceRollbackSequence sequence;
        private final int index;private final Object before;private boolean consumed,accepted,closed;
        private SavedMaintenanceRollbackKeyObservation(RootNativeHistory history,dev.veltraph.client.rewarp.mc.FarmMaintenanceCoordinator.SavedMaintenanceRollbackKey instruction,
                McHeldKeys.RollbackMaintenanceKeyInvocation invocation,SavedMaintenanceRollbackSequence sequence){
            this.history=history;this.instruction=instruction;this.invocation=invocation;this.sequence=sequence;index=instruction.index();before=history.histories[index];
        }
        private boolean sourceCurrent(){var h=history;var root=instruction.rootRollbackIdentity();return !closed&&installed==h&&!h.retired&&h.available
            &&h.savedMaintenanceRollbackKey==this&&h.savedRollback==sequence&&sequence.history==h&&sequence.source==root
            &&root!=null&&root.enrollmentIdentity()==h.source&&root.originalPublication()==instruction.originalInputPublication()
            &&root.originalFarm()==instruction.farm()&&root.beforeRestoreNativeCurrent(instruction.farm(),h.client)
            &&invocation.client()==h.client&&invocation.instruction()==instruction&&invocation.matchesInstruction(instruction)
            &&instruction.client()==h.client&&instruction.key()==h.keys[index]&&instruction.beforeNativeCurrent()
            &&h.source.originalProducerCurrent(instruction.farm(),h.client);}
        /** Final selected-field veto: pure source facts and OTHER8 old native anchors only. */
        public boolean finalNativeCurrent(McHeldKeys.RollbackMaintenanceKeyInvocation exact){
            var h=history;
            try{return exact==invocation&&!closed&&installed==h&&!h.retired&&h.available&&h.savedMaintenanceRollbackKey==this
                &&h.savedRollback==sequence&&sequence.history==h&&sequence.source==instruction.rootRollbackIdentity()
                &&invocation.client()==h.client&&invocation.instruction()==instruction&&invocation.matchesInstruction(instruction)
                &&instruction.index()==index&&instruction.key()==h.keys[index]&&instruction.finalNativeSourceCurrent()
                &&instruction.rootRollbackIdentity()!=null&&instruction.rootRollbackIdentity().enrollmentIdentity()==h.source
                &&instruction.rootRollbackIdentity().originalPublication()==instruction.originalInputPublication()
                &&h.nativeCurrent(index,false);}
            catch(RuntimeException|Error unavailable){return false;}
        }
        /** Original accepted selected-key history for the actual private carrier in-flight prefix check. */
        public Object originalKeyHistory(){return before;}
        /** Full original nine-key state immediately before an actual effect. */
        public boolean beforeNativeCurrent(){try{return !consumed&&sourceCurrent()&&history.nativeCurrent(-1,false);}
            catch(RuntimeException|Error unavailable){return false;}}
        /** After the actual selected opcode, only that key's old history is excluded. Remains readable after accepted consume. */
        public boolean otherNativeCurrent(){try{return sourceCurrent()&&history.nativeCurrent(index,false);}
            catch(RuntimeException|Error unavailable){return false;}}
    }
    public static SavedMaintenanceRollbackKeyObservation beginSavedMaintenanceRollbackKey(
            dev.veltraph.client.rewarp.mc.FarmMaintenanceCoordinator.SavedMaintenanceRollbackKey instruction,McHeldKeys.RollbackMaintenanceKeyInvocation actual){
        var h=installed;
        try{if(instruction==null||actual==null||h==null||h.retired||!h.available||h.savedMaintenanceRollbackKey!=null||h.retainedMaintenanceKey!=null||h.retainedRestoration!=null||h.savedMaintenanceKey!=null||h.savedRestoration!=null
                ||actual.client()!=h.client||actual.instruction()!=instruction||!actual.matchesInstruction(instruction)
                ||instruction.index()<0||instruction.index()>=9||instruction.key()!=h.keys[instruction.index()]||!instruction.beforeNativeCurrent()
                ||instruction.rootRollbackIdentity()==null||instruction.rootRollbackIdentity().enrollmentIdentity()!=h.source
                ||instruction.rootRollbackIdentity().originalPublication()!=instruction.originalInputPublication()
                ||!instruction.rootRollbackIdentity().beforeRestoreNativeCurrent(instruction.farm(),h.client)
                ||!h.currentFor(instruction.farm(),h.client))return null;
            var sequence=h.savedRollback;
            if(sequence==null){if(instruction.index()!=0)return null;sequence=new SavedMaintenanceRollbackSequence(h,instruction.rootRollbackIdentity());h.savedRollbackCompletion=null;h.savedRollback=sequence;}
            if(sequence.source!=instruction.rootRollbackIdentity()||sequence.next!=instruction.index())return null;
            var ticket=new SavedMaintenanceRollbackKeyObservation(h,instruction,actual,sequence);h.savedMaintenanceRollbackKey=ticket;
            if(!ticket.beforeNativeCurrent()){h.savedMaintenanceRollbackKey=null;h.available=false;return null;}return ticket;
        }catch(RuntimeException|Error unavailable){if(h!=null)h.available=false;return null;}
    }
    public static void acceptSavedMaintenanceRollbackKeyNormal(SavedMaintenanceRollbackKeyObservation ticket,McHeldKeys.RollbackMaintenanceKeyReceipt actual){
        if(ticket==null||ticket.closed||ticket.consumed)return;var h=ticket.history;
        try{if(actual==null||actual.instruction()!=ticket.instruction||actual.invocationIdentity()!=ticket.invocation||!actual.confirmed()
                ||!actual.normalReturnedNativeCurrent()||!ticket.otherNativeCurrent()||ticket.sequence.next!=ticket.index){h.available=false;return;}
            var receipt=actual.nativeReceipt();
            if(receipt==null||!receipt.confirmed()||receipt.key()!=ticket.instruction.key()||receipt.held()!=ticket.instruction.desiredHeld()
                    ||!receipt.normalIdleHistoryCurrent()||!receipt.normalIdleHistoryAdvancedFrom(ticket.before)){h.available=false;return;}
            // This uses the same confirmed private opcode receipt; a zero-effect return cannot renew foreign history.
            h.keyReturned(ticket.instruction.farm(),receipt.key(),receipt,true);
            if(!h.available||!h.nativeCurrent(-1,false)){h.available=false;return;}
            ticket.sequence.receipts[ticket.index]=receipt;ticket.sequence.next=ticket.index+1;ticket.accepted=true;
        }catch(RuntimeException|Error unavailable){h.available=false;}
        finally{ticket.consumed=true;}
    }
    /** Retires only the exact private per-key data window, including exceptional partial native effects. */
    public static void closeSavedMaintenanceRollbackKey(SavedMaintenanceRollbackKeyObservation ticket,McHeldKeys.RollbackMaintenanceKeyInvocation actual){
        if(ticket==null||actual!=ticket.invocation||ticket.closed)return;var h=ticket.history;
        try{if(!ticket.consumed||!ticket.accepted||!actual.matchesInstruction(ticket.instruction)||!ticket.otherNativeCurrent()
                ||!h.nativeCurrent(-1,false))h.available=false;}
        catch(RuntimeException|Error unavailable){h.available=false;}
        finally{ticket.closed=true;if(h.savedMaintenanceRollbackKey==ticket)h.savedMaintenanceRollbackKey=null;}
    }
    /** Confirmed actual native receipts retained through real whole-rollback completion; no values create custody. */
    static final class SavedRollbackCustody {
        final RootNativeHistory history;final VeltraphBootstrap.RootSavedMaintenanceRollbackInvocation source;
        final dev.veltraph.client.farming.module.FarmModule farm;final McFarmOwnedCleanup.NativeScope scope;
        final McHeldKeys.ScopedHeldReceipt[] receipts;
        private SavedRollbackCustody(SavedMaintenanceRollbackSequence sequence,McFarmOwnedCleanup.NativeScope scope){
            history=sequence.history;source=sequence.source;farm=(dev.veltraph.client.farming.module.FarmModule)source.originalFarm();this.scope=scope;receipts=sequence.receipts.clone();
        }
        Minecraft client(){return history.client;}
        boolean current(){
            if(installed!=history||history.retired||!history.available||!source.nativeCompletionSourceCurrent(history.client)
                    ||!history.currentFor(farm,history.client)||scope.farm!=farm||!scope.activeCurrent()||!history.nativeCurrent(-1,false))return false;
            for(int i=0;i<9;i++){var receipt=receipts[i];if(receipt==null||receipt.key()!=history.keys[i]||!receipt.confirmed()
                    ||!receipt.normalIdleHistoryCurrent()||receipt.held()!=history.expectedHeld[i])return false;}
            return true;
        }
        McHeldKeys.ScopedHeldReceipt receipt(KeyMapping key){for(int i=0;i<history.keys.length;i++)if(history.keys[i]==key)return receipts[i];return null;}
    }
    /** Actual whole rollback completion, backed by the exact normally returned nine-key sequence and adopted custody. */
    public static final class SavedMaintenanceRollbackCompletion {
        private final RootNativeHistory history;
        private final VeltraphBootstrap.RootSavedMaintenanceRollbackInvocation source;
        private final SavedRollbackCustody custody;
        private final Thread thread=Thread.currentThread();
        private SavedMaintenanceRollbackCompletion(SavedMaintenanceRollbackSequence sequence,SavedRollbackCustody custody){
            history=sequence.history;source=sequence.source;this.custody=custody;
        }
        /** Same source only; nativeCompletionSourceCurrent deliberately does not depend on this completion. */
        public boolean acceptedFor(VeltraphBootstrap.RootSavedMaintenanceRollbackInvocation exact,Minecraft client){
            try{return exact==source&&Thread.currentThread()==thread&&client==history.client&&installed==history&&!history.retired&&history.available
                &&history.savedRollbackCompletion==this&&source.nativeCompletionSourceCurrent(client)&&custody.source==source&&custody.history==history
                &&custody.current()&&history.nativeCurrent(-1,false);}
            catch(RuntimeException|Error unavailable){return false;}
        }
    }
    /** Actual Root close after real failed-start rollback; no native write or current-value rebaseline occurs here. */
    public static SavedMaintenanceRollbackCompletion closeSavedMaintenanceRollback(VeltraphBootstrap.RootSavedMaintenanceRollbackInvocation actual,Minecraft client){
        var h=installed;var sequence=h==null?null:h.savedRollback;
        if(actual==null||h==null||h.client!=client||sequence==null||sequence.source!=actual)return null;
        try{if(h.savedMaintenanceRollbackKey!=null||sequence.next!=9||!actual.nativeCompletionSourceCurrent(client)||!h.currentFor(actual.originalFarm(),client)){
                h.available=false;return null;}
            for(int i=0;i<9;i++){var receipt=sequence.receipts[i];if(receipt==null||!receipt.confirmed()||receipt.key()!=h.keys[i]
                    ||receipt.held()!=h.expectedHeld[i]||!receipt.normalIdleHistoryCurrent()){h.available=false;return null;}}
            if(!(actual.originalFarm() instanceof dev.veltraph.client.farming.module.FarmModule farm)){h.available=false;return null;}
            var scope=McFarmOwnedCleanup.NativeScope.capture(client,farm);
            if(scope==null||!actual.nativeCompletionSourceCurrent(client)||!h.nativeCurrent(-1,false)){h.available=false;return null;}
            var custody=new SavedRollbackCustody(sequence,scope);
            if(!custody.current()||!McInputService.adoptSavedRollbackOwnedKeys(custody)||!McBlockBreakingService.adoptSavedRollbackOwnedBreaking(custody)
                    ||!custody.current()){h.available=false;return null;}
            var completion=new SavedMaintenanceRollbackCompletion(sequence,custody);h.savedRollbackCompletion=completion;
            if(!completion.acceptedFor(actual,client)){h.savedRollbackCompletion=null;h.available=false;return null;}
            return completion;
        }catch(RuntimeException|Error unavailable){h.savedRollbackCompletion=null;h.available=false;return null;}
        finally{if(h.savedRollback==sequence)h.savedRollback=null;}
    }
    /** Independent old native negative custody. Initial anchors are copied only before the first rollback opcode. */
    private static final class RollbackPrefixHistory {
        final RootNativeHistory history;final VeltraphBootstrap.RootSavedMaintenanceRollbackInvocation source;
        final KeyMapping[] keys;final NormalIdleKeyCarrier[] carriers;final Object[] working,bindingHistories;final boolean[] held;
        final McHeldKeys.RollbackNativePrefixReceipt[] owned=new McHeldKeys.RollbackNativePrefixReceipt[9];
        final McHeldKeys.ScopedHeldReceipt[] pausedOwned=new McHeldKeys.ScopedHeldReceipt[9];
        final VeltraphBootstrap.RootOriginalNativeMaintenancePauseInvocation pauseSource;
        final Object pose;McOwnedRotation.NativeMetadataSnapshot metadata;final float yaw,pitch;
        boolean cleanupClaimed;int cleaning=-1;
        RollbackPrefixHistory(RootNativeHistory history,VeltraphBootstrap.RootSavedMaintenanceRollbackInvocation source,
                VeltraphBootstrap.RootOriginalNativeMaintenancePauseInvocation pauseSource){
            this.history=history;this.source=source;keys=history.keys.clone();carriers=history.carriers.clone();working=history.histories.clone();
            bindingHistories=history.bindingHistories.clone();held=history.expectedHeld.clone();pose=history.poseHistory;metadata=history.metadata;yaw=history.expectedYaw;pitch=history.expectedPitch;
            this.pauseSource=pauseSource;
            for(int i=0;i<9;i++){var old=history.originalNormalOwnedKeys[i];if(old!=null&&old.key()==keys[i]&&old.normallyReturnedOwnedHeldAttempt()
                &&old.normalIdleHistoryCurrent())pausedOwned[i]=old;}
            var prior=history.retainedSavedPausePrefix;
            if(prior!=null&&prior.pauseSource==pauseSource)for(int i=0;i<9;i++){
                var old=prior.owned[i];if(old!=null&&old.current())owned[i]=old;
                var pausedOld=prior.pausedOwned[i];if(pausedOld!=null&&pausedOld.normallyReturnedOwnedHeldAttempt()&&pausedOld.normalIdleHistoryCurrent())pausedOwned[i]=pausedOld;
            }
            var paused=history.retainedSavedPauseObservation;
            if(paused!=null&&paused.source==pauseSource&&paused.accepted&&paused.receipt!=null&&paused.receipt.confirmed()
                &&paused.receipt.key()==keys[5]&&paused.receipt.normallyReturnedOwnedHeldAttempt()&&paused.receipt.normalIdleHistoryCurrent())pausedOwned[5]=paused.receipt;
        }
        boolean sceneCurrent(){var h=history;var c=h.client;return installed==h&&!h.retired&&c==Minecraft.getInstance()&&c.isSameThread()
            &&McInputService.canonicalFarmRootCurrent(c)&&c.player==h.player&&c.level==h.world&&c.getConnection()==h.listener&&c.options==h.options&&c.gameMode==h.gameMode
            &&h.listener!=null&&c.getConnection().getConnection()==h.connection&&((net.minecraft.network.Connection)h.connection).isConnected()
            &&h.rotation!=null&&h.rotation.current()&&h.originalInstallation!=null&&VeltraphBootstrap.originalNativeInstallationFor(c)==h.originalInstallation
            &&h.originalInstallation.currentFor(c)&&metadata!=null&&metadata.current()&&h.poses.ownedPoseHistoryCurrent(pose)
            &&Float.floatToIntBits(c.player.getYRot())==Float.floatToIntBits(yaw)&&Float.floatToIntBits(c.player.getXRot())==Float.floatToIntBits(pitch);}
        boolean nativeCurrent(int excluded){if(!sceneCurrent())return false;var actual=currentKeys(history.client);
            for(int i=0;i<9;i++){if(keys[i]!=actual[i]||carriers[i]==null||working[i]==null||bindingHistories[i]==null
                    ||!NativeKeyBindingHistory.current(keys[i],bindingHistories[i]))return false;
                if(i!=excluded&&(!carriers[i].normalIdleKeyCurrent(working[i])||keys[i].isDown()!=held[i]))return false;}
            return true;}
    }
    /** Immediate passive cache of a real normal SDK return; no new root availability or gameplay confirmation. */
    static void retainRollbackNativePrefix(SavedMaintenanceRollbackKeyObservation observation,McHeldKeys.RollbackNativePrefixReceipt actual){
        if(observation==null||actual==null)return;var h=observation.history;var ledger=h.retainedRollbackPrefix;int i=observation.index;
        if(ledger==null||ledger.source!=observation.sequence.source||actual.invocation!=observation.invocation||actual.key()!=ledger.keys[i]
            ||ledger.cleanupClaimed||!actual.advances(ledger.working[i]))return;
        ledger.working[i]=actual.snapshot();ledger.held[i]=actual.held;
        if(actual.ownTrue)ledger.owned[i]=actual;else if(!actual.held)ledger.owned[i]=null;
        if(actual.ownTrue||!actual.held)ledger.pausedOwned[i]=null;
    }
    /** Exact private canonical FALSE return can retire old negative custody even when normal availability was lost. */
    private void retainPauseNormalNegativeReturn(Module original,McHeldKeys.ScopedHeldReceipt actual){
        var observation=savedMaintenancePause;var ledger=retainedSavedPausePrefix;
        if(observation==null||observation.closed||ledger==null||ledger.pauseSource!=observation.source||original!=observation.source.originalFarm()
            ||ledger.cleanupClaimed||actual==null||actual.held())return;
        for(int i=0;i<9;i++)if(actual.key()==ledger.keys[i]){
            if(!actual.ownNegativeHistoryAdvancedFrom(ledger.working[i]))return;
            ledger.working[i]=actual.historySnapshotForRollbackCleanup();ledger.held[i]=false;ledger.owned[i]=null;ledger.pausedOwned[i]=null;return;
        }
    }
    /** Only the genuine old farm's normally returned native cancellation may project negative metadata custody. */
    private void retainPauseNativeCancellation(McOwnedRotation.NativeMetadataNormalReturn actual){
        var observation=savedMaintenancePause;var ledger=retainedSavedPausePrefix;var call=actual==null?null:actual.originalCall();
        if(observation==null||observation.closed||ledger==null||ledger.pauseSource!=observation.source||ledger.cleanupClaimed
            ||call==null||call.originalModule()!=observation.source.originalFarm()||!actual.matchesCall(call)||!actual.cancellation()
            ||!actual.advancedFrom(ledger.metadata))return;
        ledger.metadata=actual.afterSnapshot();
    }
    /** Independently retained real posture prefix, including a genuine return followed by a failed pause observer. */
    static void retainSavedPauseNativePrefix(SavedMaintenancePauseObservation observation,McHeldKeys.RollbackNativePrefixReceipt actual){
        if(observation==null||actual==null)return;var ledger=observation.history.retainedSavedPausePrefix;
        if(ledger==null||ledger.pauseSource!=observation.source||actual.pauseInvocation!=observation.posture||actual.key()!=ledger.keys[5]
            ||ledger.cleanupClaimed||!actual.advances(ledger.working[5]))return;
        ledger.working[5]=actual.snapshot();ledger.held[5]=actual.held;
        if(actual.ownTrue){ledger.owned[5]=actual;ledger.pausedOwned[5]=null;}
        else if(!actual.held){ledger.owned[5]=null;ledger.pausedOwned[5]=null;}
    }
    /** One-use cleanup plan from the old prefix; it never captures a replacement key or refreshes an epoch. */
    static final class RollbackPrefixCleanupPlan {
        final RollbackPrefixHistory ledger;final VeltraphBootstrap.RootSavedMaintenanceRollbackCleanupInvocation source;int next;
        private RollbackPrefixCleanupPlan(RollbackPrefixHistory ledger,VeltraphBootstrap.RootSavedMaintenanceRollbackCleanupInvocation source){this.ledger=ledger;this.source=source;}
        KeyMapping key(int i){return ledger.keys[i];}Object binding(int i){return ledger.bindingHistories[i];}
        McHeldKeys.KeyHistory originalOwnedHistory(int i){var own=ledger.owned[i];if(own!=null&&own.ownTrue&&own.current())return own.history;
            var paused=ledger.pausedOwned[i];return paused==null?null:paused.historyForRollbackCleanup();}
        boolean otherCurrent(int i){return ledger.cleanupClaimed&&ledger.cleaning==i&&ledger.nativeCurrent(i);}
        boolean claim(int i,VeltraphBootstrap.RootSavedMaintenanceRollbackCleanupInvocation exact){if(exact!=source||i!=next||ledger.cleaning!=-1
                ||!source.beforeCleanupNativeCurrent()||!ledger.nativeCurrent(-1)||originalOwnedHistory(i)==null)return false;
            ledger.cleaning=i;return true;}
        boolean accept(int i,McHeldKeys.ScopedHeldReceipt actual){if(ledger.cleaning!=i||actual==null||actual.key()!=ledger.keys[i]||actual.held()
                ||!actual.confirmed()||!actual.normalIdleHistoryCurrent()||!actual.normalIdleHistoryAdvancedFrom(ledger.working[i])||!ledger.nativeCurrent(i))return false;
            ledger.working[i]=actual.historySnapshotForRollbackCleanup();ledger.held[i]=false;ledger.owned[i]=null;ledger.pausedOwned[i]=null;ledger.cleaning=-1;next=i+1;
            return ledger.nativeCurrent(-1);}
        void skip(int i){if(i!=next||ledger.cleaning!=-1)throw new IllegalStateException("Rollback prefix cleanup order");next=i+1;}
    }
    public static final class SavedMaintenanceRollbackCleanupCompletion {
        private final VeltraphBootstrap.RootSavedMaintenanceRollbackCleanupInvocation source;private final RollbackPrefixHistory ledger;
        private final Thread thread=Thread.currentThread();
        private SavedMaintenanceRollbackCleanupCompletion(VeltraphBootstrap.RootSavedMaintenanceRollbackCleanupInvocation source,RollbackPrefixHistory ledger){this.source=source;this.ledger=ledger;}
        public VeltraphBootstrap.RootSavedMaintenanceRollbackCleanupInvocation originalCleanupIdentity(){return source;}
        public boolean acceptedFor(dev.veltraph.client.rewarp.mc.FarmMaintenanceCoordinator.SavedMaintenanceRollbackTerminal terminal,Minecraft client){
            try{return Thread.currentThread()==thread&&client==source.client()&&source.terminalIdentity()==terminal&&source.completionSourceCurrent(terminal,client)
                &&(ledger==null||ledger.cleanupClaimed&&ledger.cleaning==-1&&ledger.nativeCurrent(-1)&&java.util.Arrays.stream(ledger.owned).allMatch(r->r==null)&&java.util.Arrays.stream(ledger.pausedOwned).allMatch(r->r==null));}
            catch(RuntimeException|Error unavailable){return false;}
        }
    }
    public static SavedMaintenanceRollbackCleanupCompletion cleanupSavedMaintenanceRollbackPrefix(VeltraphBootstrap.RootSavedMaintenanceRollbackCleanupInvocation source){
        if(source==null||!source.beforeCleanupNativeCurrent())return null;var h=installed;var ledger=h==null?null:h.retainedRollbackPrefix;
        if(ledger==null||ledger.source!=source.originalRollbackIdentity())ledger=h==null?null:h.retainedSavedPausePrefix;
        if(ledger==null)return null; // Missing genuine paused native anchors are not a zero-effect certificate.
        if((ledger.source!=null&&ledger.source!=source.originalRollbackIdentity())||ledger.pauseSource!=source.originalCompletedPauseIdentity()
            ||ledger.pauseSource==null||ledger.pauseSource.enrollmentIdentity()!=source.enrollmentIdentity()||ledger.cleanupClaimed
            ||!ledger.nativeCurrent(-1))return null;
        ledger.cleanupClaimed=true;var plan=new RollbackPrefixCleanupPlan(ledger,source);
        for(int i=0;i<9;i++){
            if(ledger.owned[i]==null&&ledger.pausedOwned[i]==null){plan.skip(i);continue;}
            var released=McHeldKeys.clearRollbackPrefix(source,plan,i);
            if(!plan.accept(i,released))return null;
        }
        if(plan.next!=9||!source.finalCleanupNativeCurrent()||!ledger.nativeCurrent(-1))return null;
        return new SavedMaintenanceRollbackCleanupCompletion(source,ledger);
    }
    /** Passive pairing of the real Root pause wrapper; it does not suspend or release anything. */
    public static final class NativePauseObservation {
        private final RootNativeHistory history;
        private final VeltraphBootstrap.RootOriginalNativePauseInvocation source;
        private final Object[] beforeKeys;private final Object beforePose;
        private final McOwnedRotation.NativeMetadataSnapshot beforeMetadata;
        private boolean finished,accepted;
        private NativePauseObservation(RootNativeHistory history,VeltraphBootstrap.RootOriginalNativePauseInvocation source){
            this.history=history;this.source=source;beforeKeys=history.histories.clone();beforePose=history.poseHistory;beforeMetadata=history.metadata;
        }
    }
    public static NativePauseObservation beginPause(VeltraphBootstrap.RootOriginalNativePauseInvocation actual,Minecraft client){
        var h=installed;
        try{if(actual==null||h==null||h.client!=client||h.retired||!h.available||h.pauseObservation!=null
                ||actual.enrollmentIdentity()!=h.source||!actual.beforeNativeCurrent()||!h.currentFor(actual.originalFarm(),client))return null;
            var observation=new NativePauseObservation(h,actual);h.pauseObservation=observation;return observation;
        }catch(RuntimeException|Error unavailable){return null;}
    }
    /** Only normally returned canonical false publications have already advanced the retained native keys. */
    public static void finishPause(NativePauseObservation observation,VeltraphBootstrap.RootOriginalNativePauseInvocation actual){
        if(observation==null||observation.finished||observation.source!=actual)return;
        observation.finished=true;var h=observation.history;
        try{if(installed!=h||h.pauseObservation!=observation||h.retired||!h.available
                ||!actual.normalReturnedNativeCurrent()||!h.nativeCurrent(-1,false)){h.available=false;return;}
            // Canonical own camera/pose events may advance old anchors during onBeforeFarmSuspension.
            // nativeCurrent accepts only those already-observed private normal-return continuations.
            // Issued-return suspension clears seven movement keys and attack, leaving use untouched.
            for(int i=0;i<8;i++)if(h.expectedHeld[i]){h.available=false;return;}
            if(h.histories[8]!=observation.beforeKeys[8]){h.available=false;return;}
            observation.accepted=true;
        }catch(RuntimeException|Error unavailable){h.available=false;}
        finally{if(h.pauseObservation==observation)h.pauseObservation=null;}
    }
    /** Retire the exact observation after the actual Root wrapper leaves, including exceptional partial cleanup. */
    public static void closePause(NativePauseObservation observation,VeltraphBootstrap.RootOriginalNativePauseInvocation actual){
        if(observation==null||observation.source!=actual)return;
        var h=observation.history;boolean actualClose;
        try{actualClose=StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).walk(stream->{var caller=stream.skip(1).findFirst().orElse(null);
            return caller!=null&&caller.getDeclaringClass()==VeltraphBootstrap.class&&caller.getMethodName().equals("closeOriginalNativePause");});}
        catch(RuntimeException|Error unavailable){
            // Exact private pair only: unknown observation can retire its own data, never supply success or a native effect.
            if(h.pauseObservation==observation){observation.finished=true;observation.accepted=false;h.available=false;h.pauseObservation=null;}return;
        }
        if(!actualClose)return;
        if(!observation.finished){observation.finished=true;h.available=false;}
        if(h.pauseObservation==observation)h.pauseObservation=null;
    }
    /** Saved pause is distinct from issued return: its real post-suspend operation can restore sneak. */
    public static final class SavedMaintenancePauseObservation {
        private final RootNativeHistory history;
        private final VeltraphBootstrap.RootOriginalNativeMaintenancePauseInvocation source;
        private final Thread thread=Thread.currentThread();
        private final Object[] beforeKeys;
        private final Object shiftBinding;
        private final KeyMapping shift;
        private Object suspendedShift;
        private McHeldKeys.SavedMaintenanceKeyInvocation posture;
        private McHeldKeys.ScopedHeldReceipt receipt;
        private SavedMaintenancePauseCompletion completion;
        private boolean suspendAccepted,postureStarted,postureAccepted,finished,accepted,closed;
        private SavedMaintenancePauseObservation(RootNativeHistory history,VeltraphBootstrap.RootOriginalNativeMaintenancePauseInvocation source){
            this.history=history;this.source=source;beforeKeys=history.histories.clone();shift=history.keys[5];shiftBinding=history.bindingHistories[5];
        }
        private boolean boundCurrent(){return !closed&&Thread.currentThread()==thread&&installed==history&&!history.retired&&history.available
            &&history.savedMaintenancePause==this&&source.enrollmentIdentity()==history.source&&source.originalFarm()!=null
            &&source.originalPostureKey()==shift&&history.keys[5]==shift&&history.bindingHistories[5]==shiftBinding;}
        /** Original source checks occur before a selected SDK instruction, never create authority. */
        boolean beforePostureCurrent(){return boundCurrent()&&suspendAccepted&&!postureStarted&&source.postureEpisodeCurrent()
            &&suspendedShift==history.histories[5]&&history.nativeCurrent(-1,false);}
        boolean postureOtherCurrent(McHeldKeys.SavedMaintenanceKeyInvocation exact){return boundCurrent()&&suspendAccepted&&postureStarted
            &&posture==exact&&exact.matchesPosture(source)&&source.postureEpisodeCurrent()&&history.nativeCurrent(5,false);}
        /** Selected FIELD is checked after Toggle mode callbacks using OTHER8 + original source only. */
        boolean finalPostureCurrent(McHeldKeys.SavedMaintenanceKeyInvocation exact){return boundCurrent()&&suspendAccepted&&postureStarted
            &&posture==exact&&exact.matchesPosture(source)&&source.finalPostureNativeSourceCurrent()&&history.nativeCurrent(5,false);}
        Object originalShiftHistory(){return suspendedShift;}
        Object originalShiftBinding(){return shiftBinding;}
    }
    public static SavedMaintenancePauseObservation beginSavedMaintenancePause(VeltraphBootstrap.RootOriginalNativeMaintenancePauseInvocation actual,Minecraft client){
        var h=installed;
        try{if(actual==null||h==null||h.client!=client||h.retired||!h.available||h.pauseObservation!=null||h.savedMaintenancePause!=null
                ||actual.enrollmentIdentity()!=h.source||actual.originalPostureKey()!=h.keys[5]||!actual.beforeNativeCurrent()
                ||!h.currentFor(actual.originalFarm(),client))return null;
            var next=new SavedMaintenancePauseObservation(h,actual);h.savedMaintenancePause=next;
            if(!next.boundCurrent()||!actual.beforeNativeCurrent()||!h.nativeCurrent(-1,false)){h.available=false;h.savedMaintenancePause=null;return null;}
            h.retainedSavedPauseObservation=next;h.retainedSavedPausePrefix=new RollbackPrefixHistory(h,null,actual);
            return next;
        }catch(RuntimeException|Error unavailable){if(h!=null)h.available=false;return null;}
    }
    /** Genuine normally returned suspend has already published its eight false releases through old receipts. */
    public static void finishSavedMaintenanceSuspend(SavedMaintenancePauseObservation observation,VeltraphBootstrap.RootOriginalNativeMaintenancePauseInvocation actual){
        if(observation==null||observation.source!=actual)return;var h=observation.history;
        try{if(!observation.boundCurrent()||observation.suspendAccepted||observation.finished
                ||!actual.normalSuspendReturnedNativeCurrent()||!h.nativeCurrent(-1,false)){h.available=false;return;}
            for(int i=0;i<8;i++)if(h.expectedHeld[i]){h.available=false;return;}
            if(h.histories[8]!=observation.beforeKeys[8]){h.available=false;return;}
            // This is the old accepted history after only actual release receipts, not a current-field seed.
            observation.suspendedShift=h.histories[5];observation.suspendAccepted=true;
        }catch(RuntimeException|Error unavailable){h.available=false;}
    }
    static SavedMaintenancePauseObservation beginSavedMaintenancePosture(VeltraphBootstrap.RootOriginalNativeMaintenancePauseInvocation actual,
            McHeldKeys.SavedMaintenanceKeyInvocation exact,Minecraft client){
        var h=installed;var next=h==null?null:h.savedMaintenancePause;
        try{if(actual==null||exact==null||next==null||h.client!=client||next.source!=actual||!exact.matchesPosture(actual)
                ||next.posture!=null||!next.beforePostureCurrent()){if(next!=null&&next.source==actual)h.available=false;return null;}
            next.posture=exact;next.postureStarted=true;return next;
        }catch(RuntimeException|Error unavailable){if(h!=null)h.available=false;return null;}
    }
    /** The actual key operation's private receipt is consumed once; method-return markers cannot advance keys. */
    static void acceptSavedMaintenancePostureNormal(SavedMaintenancePauseObservation observation,McHeldKeys.SavedMaintenanceKeyInvocation exact,
            McHeldKeys.ScopedHeldReceipt actual){
        if(observation==null)return;var h=observation.history;
        try{if(observation.postureAccepted||observation.posture!=exact||!exact.postureNormalReturnedCurrent()
                ||!observation.postureOtherCurrent(exact)||actual==null||!actual.confirmed()||actual.key()!=observation.shift
                ||actual.held()!=observation.source.desiredPostureHeld()||!actual.normalIdleHistoryCurrent()
                ||!actual.normalIdleHistoryAdvancedFrom(observation.suspendedShift)){h.available=false;return;}
            h.keyReturned(observation.source.originalFarm(),observation.shift,actual,true);
            if(!h.available||!h.nativeCurrent(-1,false)){h.available=false;return;}
            observation.receipt=actual;observation.postureAccepted=true;
        }catch(RuntimeException|Error unavailable){h.available=false;}
    }
    static void closeSavedMaintenancePosture(SavedMaintenancePauseObservation observation,McHeldKeys.SavedMaintenanceKeyInvocation exact){
        if(observation==null||observation.posture!=exact)return;var h=observation.history;
        try{if(!observation.postureAccepted||!exact.postureNormalReturnedCurrent()||!h.nativeCurrent(-1,false))h.available=false;}
        catch(RuntimeException|Error unavailable){h.available=false;}
    }
    public static void finishSavedMaintenancePause(SavedMaintenancePauseObservation observation,VeltraphBootstrap.RootOriginalNativeMaintenancePauseInvocation actual){
        if(observation==null||observation.source!=actual)return;var h=observation.history;
        try{if(!observation.boundCurrent()||observation.finished||!observation.suspendAccepted||!observation.postureAccepted
                ||observation.receipt==null||!observation.receipt.confirmed()||!observation.receipt.normalIdleHistoryCurrent()
                ||observation.receipt.held()!=actual.desiredPostureHeld()||!actual.normalReturnedNativeCurrent()
                ||!h.nativeCurrent(-1,false)){h.available=false;return;}
            for(int i=0;i<8;i++)if(i!=5&&h.expectedHeld[i]){h.available=false;return;}
            if(h.histories[8]!=observation.beforeKeys[8]){h.available=false;return;}
            observation.finished=true;observation.accepted=true;
        }catch(RuntimeException|Error unavailable){h.available=false;}
    }
    /** Historical typed affiliation of the real accepted pause, never future native/source authority. */
    public static final class SavedMaintenancePauseCompletion {
        private final SavedMaintenancePauseObservation observation;
        private final VeltraphBootstrap.RootOriginalNativeMaintenancePauseInvocation source;
        private final McHeldKeys.ScopedHeldReceipt postureReceipt;
        private SavedMaintenancePauseCompletion(SavedMaintenancePauseObservation observation){
            this.observation=observation;source=observation.source;postureReceipt=observation.receipt;
        }
        /** Identity-only historical pairing. No callbacks, current-field reads, or renewed native snapshots. */
        public boolean matches(VeltraphBootstrap.RootOriginalNativeMaintenancePauseInvocation actual){
            return actual!=null&&actual==source&&observation.source==source&&observation.receipt==postureReceipt
                &&observation.completion==this;
        }
    }
    /** Root retains this exact completion after accepted finish and before its genuine finally-close. */
    public static SavedMaintenancePauseCompletion completeSavedMaintenancePause(SavedMaintenancePauseObservation observation,
            VeltraphBootstrap.RootOriginalNativeMaintenancePauseInvocation actual,Minecraft client){
        if(observation==null||actual==null||observation.source!=actual)return null;var h=observation.history;
        try{if(h.client!=client||!observation.boundCurrent()||!observation.finished||!observation.accepted
                ||!observation.suspendAccepted||!observation.postureAccepted||observation.completion!=null
                ||observation.receipt==null||!observation.receipt.confirmed()||observation.receipt.key()!=observation.shift
                ||observation.receipt.held()!=actual.desiredPostureHeld()||!observation.receipt.normalIdleHistoryCurrent()
                ||!actual.normalReturnedNativeCurrent()||!h.nativeCurrent(-1,false))return null;
            for(int i=0;i<8;i++)if(i!=5&&h.expectedHeld[i])return null;
            if(h.histories[8]!=observation.beforeKeys[8])return null;
            // All validation precedes this private identity-only handout. It seeds no history or source.
            var completion=new SavedMaintenancePauseCompletion(observation);observation.completion=completion;
            h.retainedSavedPauseObservation=observation;return completion;
        }catch(RuntimeException|Error unavailable){h.available=false;return null;}
    }
    public static void closeSavedMaintenancePause(SavedMaintenancePauseObservation observation,VeltraphBootstrap.RootOriginalNativeMaintenancePauseInvocation actual){
        if(observation==null||observation.source!=actual||Thread.currentThread()!=observation.thread)return;var h=observation.history;
        if(h.savedMaintenancePause!=observation||observation.closed)return;
        if(!observation.finished||!observation.accepted)h.available=false;
        observation.closed=true;h.savedMaintenancePause=null;
    }
    /** Lookup of a completed genuine SDK GUI transition before a real native phase. No source is minted here. */
    static GuiTicket retainedGuiForNativePhase(Minecraft client){
        var h=installed;var ticket=h==null?null:h.guiTicket;
        try{return h!=null&&h.client==client&&ticket!=null&&ticket.finished&&ticket.accepted&&!ticket.failed
            &&ticket.entry.nextScreen()!=null&&ticket.sourceCurrent()&&h.nativeCurrent(-1,false)?ticket:null;}
        catch(RuntimeException|Error unavailable){return null;}
    }
    static boolean guiMetadataAffiliationCurrent(GuiTicket ticket,Minecraft client){
        try{return ticket!=null&&ticket.history.client==client&&ticket.history.guiTicket==ticket&&ticket.finished&&ticket.accepted
            &&ticket.entry.nextScreen()!=null&&ticket.sourceCurrent()&&ticket.history.nativeCurrent(-1,false,true);}
        catch(RuntimeException|Error unavailable){return false;}
    }
    /** Exact native phase cancellation descends from the accepted SDK GUI transition and old metadata anchor. */
    static void normalGuiCameraReturned(McOwnedRotation.NativeMetadataNormalReturn actual){
        var h=installed;if(h==null||!h.available)return;
        try{var ticket=actual==null?null:actual.originalGui();
            if(ticket==null||!actual.guiSourceCurrent()||!guiMetadataAffiliationCurrent(ticket,h.client)
                    ||!actual.advancedFrom(h.metadata)){h.available=false;return;}
            h.metadata=actual.afterSnapshot();if(!h.nativeCurrent(-1,false))h.available=false;
        }catch(RuntimeException|Error unavailable){h.available=false;}
    }
    /** Existing original native custody captured before the genuine Root stop callbacks. No current-field seed. */
    public static final class TerminalTicket {
        private final RootNativeHistory history;private final VeltraphBootstrap.RootOriginalNativeTermination source;
        private final Minecraft client;private final GuiTicket gui;private boolean consumed;
        private TerminalTicket(RootNativeHistory history,VeltraphBootstrap.RootOriginalNativeTermination source){this.history=history;this.source=source;client=history.client;
            var actual=history.guiTicket;gui=actual!=null&&actual.accepted&&!actual.failed&&actual.sourceCurrent()?actual:null;}
        /** Exact closed no-gameplay stop only. Passive roles remain source-proved; old native facts never refresh. */
        public boolean noGameplayRetirementCurrent(VeltraphBootstrap.RootOriginalNativeTermination actual,Minecraft exactClient){
            var h=history;
            try{
                if(actual!=source||exactClient!=client||consumed||installed!=h||h.terminalTicket!=this||h.retired||!h.available
                        ||actual.originalEnrollment()!=h.source||actual.installationIdentity()!=h.originalInstallation
                        ||!actual.completedNoGameplayStopBeforeLogicalCurrent(client)||!h.nativeCurrent(-1,false))return false;
                if(client.gui.screen()!=null){
                    // The genuine accepted SDK entry was retained before the real stop, while its original PUB was live.
                    if(gui==null||h.guiTicket!=gui||gui.history!=h||!gui.finished||!gui.accepted||gui.failed||gui.publication==null
                            ||gui.entry.nextScreen()==null||client.gui.screen()!=gui.entry.nextScreen())return false;
                }
                for(boolean held:h.expectedHeld)if(held)return false;
                return true;
            }catch(RuntimeException|Error unavailable){return false;}
        }
        /** Existing GUI old-history only; actual Root returned stop source owns the separate logical effect. */
        public boolean guiNativeRetirementCurrent(VeltraphBootstrap.RootOriginalNativeTermination actual,Minecraft exactClient){
            var h=history;
            try{if(actual!=source||exactClient!=client||consumed||installed!=h||h.terminalTicket!=this||h.retired||!h.available
                    ||gui==null||h.guiTicket!=gui||!gui.finished||!gui.accepted||gui.failed||gui.publication==null
                    ||gui.history!=h||gui.entry.nextScreen()==null||client.gui.screen()!=gui.entry.nextScreen()
                    ||!actual.completedOwnGuiStopBeforeLogicalCurrent(client)||!h.nativeCurrent(-1,false))return false;
                for(boolean held:h.expectedHeld)if(held)return false;
                return true;
            }catch(RuntimeException|Error unavailable){return false;}
        }
    }
    /** Separate normal terminal origin. It cannot revive the old workflow or grant gameplay effects. */
    public static final class TerminalNoOwnerHandout {
        private final VeltraphBootstrap.RootOriginalNativeTermination source;private final Minecraft client;
        private final VeltraphBootstrap.RootOriginalNativeInstallation installation;
        private final Object player,world,listener,connection,options,gameMode;
        private final KeyMapping[] keys;private final NormalIdleKeyCarrier[] carriers;
        private final Object[] histories;private final boolean[] held;private final String[] bindings;private final Object[] bindingHistories;
        private final OwnedPoseHistoryCarrier poses;private final Object poseHistory;private final float yaw,pitch;
        private final McOwnedRotation.NativeMetadataSnapshot metadata;
        private TerminalNoOwnerHandout(TerminalTicket ticket){var h=ticket.history;source=ticket.source;client=h.client;installation=h.originalInstallation;
            player=h.player;world=h.world;listener=h.listener;connection=h.connection;options=h.options;gameMode=h.gameMode;
            keys=h.keys.clone();carriers=h.carriers.clone();histories=h.histories.clone();held=h.expectedHeld.clone();bindings=h.bindings.clone();bindingHistories=h.bindingHistories.clone();
            poses=h.poses;poseHistory=h.poseHistory;yaw=h.expectedYaw;pitch=h.expectedPitch;metadata=h.metadata;
        }
        /** Existing permanently retired idle source only; this lookup never creates or refreshes native facts. */
        public VeltraphBootstrap.RootOriginalNativeIdleContinuation idleContinuationIdentity(){return source.idleContinuationIdentity();}
        public Minecraft client(){return client;}
        public boolean current(Minecraft actualClient){
            try{if(actualClient!=client||!source.terminalHistoryCurrent(client)||!installation.currentNativeNoOwner(client)
                    ||VeltraphBootstrap.originalNativeInstallationFor(client)!=installation||client.player!=player||client.level!=world
                    ||client.getConnection()!=listener||client.getConnection()==null||client.getConnection().getConnection()!=connection
                    ||client.options!=options||client.gameMode!=gameMode||metadata==null||!metadata.current())return false;
                var actual=currentKeys(client);for(int i=0;i<keys.length;i++)if(actual[i]!=keys[i]||!carriers[i].normalIdleKeyCurrent(histories[i])
                    ||keys[i].isDown()!=held[i]||!bindings[i].equals(keys[i].saveString())
                    ||bindingHistories[i]==null||!NativeKeyBindingHistory.current(keys[i],bindingHistories[i]))return false;
                return poses.ownedPoseHistoryCurrent(poseHistory)&&Float.floatToIntBits(client.player.getYRot())==Float.floatToIntBits(yaw)
                    &&Float.floatToIntBits(client.player.getXRot())==Float.floatToIntBits(pitch);
            }catch(RuntimeException|Error unavailable){return false;}
        }
        private boolean metadataOriginCurrent(Minecraft actualClient){
            try{return actualClient==client&&source.terminalHistoryCurrent(client)&&installation.currentNativeNoOwner(client)
                &&VeltraphBootstrap.originalNativeInstallationFor(client)==installation&&client.player==player&&client.level==world
                &&client.getConnection()==listener&&client.getConnection()!=null&&client.getConnection().getConnection()==connection
                &&client.options==options&&client.gameMode==gameMode&&metadata!=null&&metadata.current();}
            catch(RuntimeException|Error unavailable){return false;}
        }
        public Object keySnapshot(KeyMapping key){for(int i=0;i<keys.length;i++)if(keys[i]==key)return histories[i];return null;}
        public boolean held(KeyMapping key){for(int i=0;i<keys.length;i++)if(keys[i]==key)return held[i];throw new IllegalArgumentException("Foreign key");}
        public Object bindingSnapshot(KeyMapping key){for(int i=0;i<keys.length;i++)if(keys[i]==key)return bindingHistories[i];return null;}
        public String binding(KeyMapping key){for(int i=0;i<keys.length;i++)if(keys[i]==key)return bindings[i];return null;}
        public Object poseSnapshot(OwnedPoseHistoryCarrier carrier){return carrier==poses?poseHistory:null;}
        public float yaw(){return yaw;}public float pitch(){return pitch;}
    }
    /** Existing actual terminal metadata origin only; no fresh snapshot or old workflow renewal. */
    static McOwnedRotation.NativeMetadataSnapshot terminalMetadataFor(Minecraft client){
        var actual=retainedTerminal;
        try{return actual!=null&&actual.metadataOriginCurrent(client)?actual.metadata:null;}
        catch(RuntimeException|Error unavailable){return null;}
    }
    public static TerminalTicket beginTerminal(VeltraphBootstrap.RootOriginalNativeTermination actual,Minecraft client){
        var h=installed;
        try{if(actual==null||h==null||h.retired||!h.available||h.client!=client||h.terminalTicket!=null
                ||actual.originalEnrollment()!=h.source||actual.installationIdentity()!=h.originalInstallation
                ||!actual.beforeNativeCurrent(client)||!h.currentFor(actual.originalModule(),client))return null;
            var ticket=new TerminalTicket(h,actual);h.terminalTicket=ticket;return ticket;
        }catch(RuntimeException|Error unavailable){return null;}
    }
    public static TerminalNoOwnerHandout finishTerminal(TerminalTicket ticket,VeltraphBootstrap.RootOriginalNativeTermination actual,Minecraft client){
        if(ticket==null||actual==null||ticket.source!=actual||ticket.client!=client||ticket.consumed)return null;
        ticket.consumed=true;var h=ticket.history;
        try{if(installed!=h||h.terminalTicket!=ticket||h.retired||!h.available||!actual.normalReturnedNativeCurrent(client)
                ||!h.nativeCurrent(-1,false))return null;
            for(boolean held:h.expectedHeld)if(held)return null;
            // Every value and opaque history comes from the original accepted normal publication path.
            var handout=new TerminalNoOwnerHandout(ticket);h.retired=true;h.available=false;retainedTerminal=handout;
            return handout;
        }catch(RuntimeException|Error unavailable){return null;}
        finally{if(h.terminalTicket==ticket)h.terminalTicket=null;}
    }
    /** The real canonical service invokes this only after its exact original native API normally returns. */
    public static void normalCameraReturned(McOwnedRotation.NativeMetadataNormalReturn actual) {
        var next=installed;if(next==null)return;
        try {
            next.retainPauseNativeCancellation(actual);
            if(!next.available)return;
            var call=actual==null?null:actual.originalCall();var original=call==null?null:call.originalModule();
            if(call==null||original==null||!actual.matchesCall(call)||!call.normalReturnedCurrent(actual)
                    ||!call.nativeSourceCurrent(next.client)||!next.source.originalProducerCurrent(original,next.client)
                    ||!next.nativeCurrent(-1,false,true)||!actual.advancedFrom(next.metadata)){next.available=false;return;}
            next.metadata=actual.afterSnapshot();
            if(!next.nativeCurrent(-1,false))next.available=false;
        }catch(RuntimeException|Error unavailable){next.available=false;}
    }
    /** Genuine normal SDK idle phase only; null scope, empty state and raw epoch equality never mint it. */
    static void normalIdleCameraReturned(McOwnedRotation.NativeMetadataNormalReturn actual) {
        var next=installed;if(next==null||!next.available)return;
        try {
            if(!next.source.initialNoOwnerCurrent(next.client)||!next.nativeCurrent(-1,false,true)
                    ||actual==null||!actual.cancellation()||actual.originalCall()!=null||!actual.advancedFrom(next.metadata)){
                next.available=false;return;
            }
            next.metadata=actual.afterSnapshot();if(!next.nativeCurrent(-1,false))next.available=false;
        }catch(RuntimeException|Error unavailable){next.available=false;}
    }
    static void normalPoseReturned(McOwnedRotation.PoseNormalReturn actual) {
        var next=installed;if(next==null||!next.available)return;
        try {
            var original=actual.originalModule();
            if(original==null||!actual.nativeSourceCurrent()||!next.source.originalProducerCurrent(original,next.client)
                    ||!next.nativeCurrent(-1,true)||!actual.advancedFrom(next.poses,next.poseHistory)) {next.available=false;return;}
            var after=next.poses.ownedPoseHistorySnapshot();
            if(after==null||!next.poses.ownedPoseHistoryCurrent(after)||!actual.advancedFrom(next.poses,next.poseHistory)
                    ||!next.source.originalProducerCurrent(original,next.client)||!next.nativeCurrent(-1,true)) {next.available=false;return;}
            next.poseHistory=after;next.expectedYaw=actual.yaw();next.expectedPitch=actual.pitch();
            if(!next.finitePose()||!next.nativeCurrent(-1,false))next.available=false;
        } catch(RuntimeException|Error unavailable){next.available=false;}
    }
}
