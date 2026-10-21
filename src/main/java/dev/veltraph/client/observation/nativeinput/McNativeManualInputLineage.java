package dev.veltraph.client.observation.nativeinput;

import dev.veltraph.client.VeltraphBootstrap;
import dev.veltraph.client.core.AbstractModule;
import dev.veltraph.client.core.ModuleManager;
import dev.veltraph.client.input.ScriptingInputLock;
import dev.veltraph.client.service.mc.NormalIdleKeyCarrier;
import dev.veltraph.client.service.mc.OwnedPoseHistoryCarrier;
import java.util.*;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.world.entity.Entity;
import com.mojang.blaze3d.platform.InputConstants;
import static dev.veltraph.client.observation.nativeinput.NativeOpcodeReturnTrace.Opcode.*;

/** Paired actual SDK observations only. These returns are NOT a born scene or a Root admission seed. */
public final class McNativeManualInputLineage {
    private McNativeManualInputLineage(){}
    private static final ThreadLocal<KeyboardEpisode> KEYBOARD=new ThreadLocal<>();
    private static final ThreadLocal<StaticKeyEpisode> STATIC_KEY=new ThreadLocal<>();
    private static final ThreadLocal<PoseEpisode> POSE=new ThreadLocal<>();
    private static final ThreadLocal<NativeKeyAttempt> KEY_ATTEMPT=new ThreadLocal<>();
    private static NativeOpcodeReturn lastKeyboard,lastPose,lastButton;
    private static final ThreadLocal<MouseButtonEpisode> MOUSE_BUTTON=new ThreadLocal<>();
    private static final ThreadLocal<MouseStaticKeyEpisode> MOUSE_STATIC_KEY=new ThreadLocal<>();
    private static final ThreadLocal<MouseKeyAttempt> MOUSE_KEY_ATTEMPT=new ThreadLocal<>();
    private static final ThreadLocal<NativeToggleKeyAttempt> NATIVE_TOGGLE=new ThreadLocal<>();
    private static final ThreadLocal<MouseMoveEpisode> MOUSE_MOVE=new ThreadLocal<>();
    private static MouseMoveBatch queuedMoves;
    private static NoOwnerInputHandout canonicalNoOwner;
    private static PassiveNativeTransitionTicket passiveTransition;
    private static final List<java.lang.ref.WeakReference<NativePlayerBirthProof>> birthAttempts=new ArrayList<>();
    private static boolean claimBirthAttempt(NativePlayerBirthProof proof){for(var it=birthAttempts.iterator();it.hasNext();){var old=it.next().get();if(old==null)it.remove();else if(old==proof)return false;}birthAttempts.add(new java.lang.ref.WeakReference<>(proof));return true;}
    private static boolean actualBirthHandoffCaller(){return StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).walk(frames->frames.filter(f->f.getDeclaringClass()!=McNativeManualInputLineage.class).findFirst().map(f->f.getDeclaringClass()==NativePlayerBirthProof.class&&(f.getMethodName().equals("positionExitedObserved")||f.getMethodName().equals("finalizeRetainedInitialization"))).orElse(false));}
    private static boolean sdkCaller(Class<?> owner,String method){return StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).walk(s->s.limit(14).anyMatch(f->f.getDeclaringClass()==owner&&f.getMethodName().equals(method)));}
    private static KeyMapping[] keys(Minecraft c){var o=c.options;return new KeyMapping[]{o.keyUp,o.keyDown,o.keyLeft,o.keyRight,o.keyJump,o.keyShift,o.keySprint,o.keyAttack,o.keyUse};}
    /** Capture before any callback-capable reads. This is a frame observation, not positive origin enrollment. */
    private static final class Scene {
        final Minecraft client;final Object player,world,listener,connection,options,mode,screen;
        final ModuleManager root;final ModuleManager.LifecycleSnapshot lifecycle;final ScriptingInputLock.ControlSnapshot control;
        final VeltraphBootstrap.RootOriginalNativeIdleContinuation retiredIdle;
        final KeyMapping[] keys;final NormalIdleKeyCarrier[] carriers;final Object[] histories;final String[] bindings;final Object[] bindingHistory;final boolean[] held;
        final OwnedPoseHistoryCarrier poseCarrier;final Object poseHistory;final float yaw,pitch;
        Scene(Minecraft c){this(c,existingRetiredIdle(c));}
        private Scene(Minecraft c,VeltraphBootstrap.RootOriginalNativeIdleContinuation retiredIdle){
            this.retiredIdle=retiredIdle;client=c;player=c.player;world=c.level;listener=c.getConnection();connection=c.getConnection().getConnection();options=c.options;mode=c.gameMode;screen=c.gui.screen();
            root=VeltraphBootstrap.moduleManager();lifecycle=root.lifecycleSnapshot();control=ScriptingInputLock.originalPublicationSnapshot();
            keys=McNativeManualInputLineage.keys(c);carriers=new NormalIdleKeyCarrier[9];histories=new Object[9];bindings=new String[9];bindingHistory=new Object[9];held=new boolean[9];
            for(int i=0;i<9;i++){if(!(keys[i] instanceof NormalIdleKeyCarrier carrier))throw new IllegalStateException("Native key history unavailable");carriers[i]=carrier;histories[i]=carrier.normalIdleKeySnapshot();bindings[i]=keys[i].saveString();bindingHistory[i]=NativeKeyBindingHistory.snapshot(keys[i]);held[i]=keys[i].isDown();}
            if(!((Object)c.player instanceof OwnedPoseHistoryCarrier carrier))throw new IllegalStateException("Native pose history unavailable");poseCarrier=carrier;poseHistory=carrier.ownedPoseHistorySnapshot();yaw=c.player.getYRot();pitch=c.player.getXRot();
        }
        private Scene(Scene original,Object declaredScreen){this(original,declaredScreen,original.lifecycle);}
        private Scene(Scene original,Object declaredScreen,ModuleManager.LifecycleSnapshot issued){retiredIdle=original.retiredIdle;client=original.client;player=original.player;world=original.world;listener=original.listener;connection=original.connection;options=original.options;mode=original.mode;screen=declaredScreen;root=original.root;lifecycle=issued;control=original.control;keys=original.keys.clone();carriers=original.carriers.clone();histories=original.histories.clone();bindings=original.bindings.clone();bindingHistory=original.bindingHistory.clone();held=original.held.clone();poseCarrier=original.poseCarrier;poseHistory=original.poseHistory;yaw=original.yaw;pitch=original.pitch;}
        static Scene capture(){var c=Minecraft.getInstance();if(c==null||!c.isSameThread()||c.player==null||c.level==null||c.getConnection()==null||c.options==null||c.gameMode==null||VeltraphBootstrap.moduleManager()==null)return null;
            try{var s=new Scene(c);return s.current(-1,false)?s:null;}catch(RuntimeException|Error unavailable){return null;}}
        boolean header(){return control!=null&&control.publicationCurrent()&&!control.active()&&control.ownerModuleId()==null&&nativeHeader();}
        boolean continuedHeader(VeltraphBootstrap.RootOriginalNativeInstallation actual){return actual!=null&&VeltraphBootstrap.originalNativeInstallationFor(client)==actual&&actual.currentNativeNoOwner(client)&&nativeHeader();}
        private boolean nativeHeader(){
            if(client!=Minecraft.getInstance()||!client.isSameThread()||client.player!=player||client.level!=world||client.getConnection()!=listener||client.getConnection()==null
                    ||client.getConnection().getConnection()!=connection||!((net.minecraft.network.Connection)connection).isConnected()||client.options!=options||client.gameMode!=mode||client.gui.screen()!=screen
                    ||VeltraphBootstrap.moduleManager()!=root||lifecycle==null||!lifecycle.known())return false;
            var installed=VeltraphBootstrap.originalNativeInstallationFor(client);if(retiredIdle!=null&&(retiredIdle.installationIdentity()!=installed||!retiredIdle.currentFor(client)))return false;var roles=installed==null?null:installed.passiveRoleDeclaration();
            if(roles==null||!root.noGameplayOwnerRosterCurrent(lifecycle,roles))return false;
            var actual=McNativeManualInputLineage.keys(client);for(int i=0;i<9;i++)if(actual[i]!=keys[i]||!bindings[i].equals(keys[i].saveString())||bindingHistory[i]==null||!NativeKeyBindingHistory.current(keys[i],bindingHistory[i]))return false;
            return client.player.isAlive()&&client.player.level()==client.level&&!client.isPaused()&&client.getCameraEntity()==player&&client.isWindowActive();
        }
        boolean current(int changedKey,boolean changedPose){return header()&&historiesCurrent(changedKey,changedPose);}
        boolean continuedCurrent(VeltraphBootstrap.RootOriginalNativeInstallation actual,int changedKey,boolean changedPose){return continuedHeader(actual)&&historiesCurrent(changedKey,changedPose);}
        private boolean historiesCurrent(int changedKey,boolean changedPose){
            for(int i=0;i<9;i++)if(i!=changedKey&&(histories[i]==null||!carriers[i].normalIdleKeyCurrent(histories[i])||keys[i].isDown()!=held[i]))return false;
            return changedPose||poseHistory!=null&&poseCarrier.ownedPoseHistoryCurrent(poseHistory)&&Float.floatToIntBits(client.player.getYRot())==Float.floatToIntBits(yaw)&&Float.floatToIntBits(client.player.getXRot())==Float.floatToIntBits(pitch);
        }
    }
    /** Copy only an already retained terminal source into a factual SDK observation; this never creates a seed. */
    private static VeltraphBootstrap.RootOriginalNativeIdleContinuation existingRetiredIdle(Minecraft client){var old=canonicalNoOwner;return old!=null&&old.scene.client==client?old.scene.retiredIdle:null;}
    /** Private-ctor source normal return. Root must separately bind an authentic BEFORE born/terminal chain. */
    public static final class NativeOpcodeReturn {
        private final Scene before;private final Object[] afterKeys;private final boolean[] afterHeld;private final Object afterPose;private final float afterYaw,afterPitch;
        private final List<Object> yawCalls,pitchCalls;private final int keyIndex;private final String source;private final NativeToggleReturnTrace.Return actualToggle;
        private NativeOpcodeReturn(Scene before,int index,Object[] afterKeys,boolean[] afterHeld,Object afterPose,List<Object> yaw,List<Object> pitch,String source){this(before,index,afterKeys,afterHeld,afterPose,yaw,pitch,source,null);}
        private NativeOpcodeReturn(Scene before,int index,Object[] afterKeys,boolean[] afterHeld,Object afterPose,List<Object> yaw,List<Object> pitch,String source,NativeToggleReturnTrace.Return actualToggle){
            this.actualToggle=actualToggle;this.before=before;keyIndex=index;this.afterKeys=afterKeys.clone();this.afterHeld=afterHeld.clone();this.afterPose=afterPose;this.yawCalls=List.copyOf(yaw);this.pitchCalls=List.copyOf(pitch);this.source=source;afterYaw=before.client.player.getYRot();afterPitch=before.client.player.getXRot();
        }
        public String sdkSource(){return source;}
        public boolean current(Minecraft c){try{if(c!=before.client||!before.header())return false;for(int i=0;i<9;i++)if(afterKeys[i]==null||!before.carriers[i].normalIdleKeyCurrent(afterKeys[i])||before.keys[i].isDown()!=afterHeld[i])return false;
            return afterPose!=null&&before.poseCarrier.ownedPoseHistoryCurrent(afterPose)&&Float.floatToIntBits(c.player.getYRot())==Float.floatToIntBits(afterYaw)&&Float.floatToIntBits(c.player.getXRot())==Float.floatToIntBits(afterPitch);}catch(RuntimeException|Error unavailable){return false;}}
        public Object beforeKeySnapshot(KeyMapping exact){for(int i=0;i<9;i++)if(before.keys[i]==exact)return before.histories[i];return null;}
        public Object afterKeySnapshot(KeyMapping exact){for(int i=0;i<9;i++)if(before.keys[i]==exact)return afterKeys[i];return null;}
        public Object beforePoseSnapshot(OwnedPoseHistoryCarrier exact){return exact==before.poseCarrier?before.poseHistory:null;}
        public Object afterPoseSnapshot(OwnedPoseHistoryCarrier exact){return exact==before.poseCarrier?afterPose:null;}
        public boolean held(KeyMapping exact){for(int i=0;i<9;i++)if(before.keys[i]==exact)return afterHeld[i];throw new IllegalArgumentException("Foreign mapping");}
        public String binding(KeyMapping exact){for(int i=0;i<9;i++)if(before.keys[i]==exact)return before.bindings[i];return null;}
        public float yaw(){return afterYaw;}public float pitch(){return afterPitch;}
        public int actualYawCalls(){return yawCalls.size();}public int actualPitchCalls(){return pitchCalls.size();}
        public boolean keyAdvancedFrom(KeyMapping key,Object old){if(keyIndex<0||before.keys[keyIndex]!=key)return false;return actualToggle==null?before.carriers[keyIndex].normalIdleUnscopedAdvanced(old,afterKeys[keyIndex]):actualToggle.current()&&actualToggle.hasField()&&before.carriers[keyIndex].normalIdleKeyAdvanced(old,afterKeys[keyIndex],Collections.singletonList(null));}
        public boolean poseAdvancedFrom(OwnedPoseHistoryCarrier carrier,Object old){return keyIndex<0&&carrier==before.poseCarrier&&carrier.ownedPoseHistoryAdvanced(old,afterPose,yawCalls,pitchCalls);}
    }
    /** Lookup existing source returns only. It NEVER captures a new snapshot or declares a known Root seed. */
    public static NativeOpcodeReturn peekKeyboardReturn(Minecraft c){var r=lastKeyboard;return r!=null&&r.current(c)?r:null;}
    public static NativeOpcodeReturn peekPoseReturn(Minecraft c){var r=lastPose;return r!=null&&r.current(c)?r:null;}
    /** Only a genuine retained SDK birth or exact successor native-opcode return can construct this. */
    public static final class NoOwnerInputHandout {
        private final Scene scene;private final VeltraphBootstrap.RootOriginalNativeInstallation originalRoot;private final NativePlayerBirthProof birth;private final dev.veltraph.client.service.mc.RootNativeHistory.TerminalNoOwnerHandout terminal;private final dev.veltraph.client.service.mc.McOwnedRotation.InstallationSnapshot installation;
        private final Object[] keyFacts;private final boolean[] heldFacts;private final String[] bindingFacts;private final Object poseFact;private final float yaw,pitch;
        private final NativeOpcodeReturn actual;
        private NoOwnerInputHandout(Scene scene,NativePlayerBirthProof birth,dev.veltraph.client.service.mc.McOwnedRotation.InstallationSnapshot installation,Object[] keys,boolean[] held,String[] bindings,Object pose,float yaw,float pitch,NoOwnerInputHandout previous,NativeOpcodeReturn actual){
            this.scene=scene;this.originalRoot=previous==null?VeltraphBootstrap.originalNativeInstallationFor(scene.client):previous.originalRoot;this.birth=birth;this.terminal=previous==null?null:previous.terminal;this.installation=installation;keyFacts=keys.clone();heldFacts=held.clone();bindingFacts=bindings.clone();poseFact=pose;this.yaw=yaw;this.pitch=pitch;this.actual=actual;
        }
        private NoOwnerInputHandout(Scene scene,dev.veltraph.client.service.mc.RootNativeHistory.TerminalNoOwnerHandout terminal,dev.veltraph.client.service.mc.McOwnedRotation.InstallationSnapshot installation,Object[] keys,boolean[] held,String[] bindings,Object pose,float yaw,float pitch){this.scene=scene;originalRoot=VeltraphBootstrap.originalNativeInstallationFor(scene.client);birth=null;this.terminal=terminal;this.installation=installation;keyFacts=keys.clone();heldFacts=held.clone();bindingFacts=bindings.clone();poseFact=pose;this.yaw=yaw;this.pitch=pitch;actual=null;}
        public boolean current(Minecraft c){try{if(c!=scene.client||canonicalNoOwner!=this||!installation.current()||!scene.continuedHeader(originalRoot))return false;
            for(int i=0;i<9;i++)if(keyFacts[i]==null||!bindingFacts[i].equals(scene.keys[i].saveString())||!scene.carriers[i].normalIdleKeyCurrent(keyFacts[i])||scene.keys[i].isDown()!=heldFacts[i])return false;
            return poseFact!=null&&scene.poseCarrier.ownedPoseHistoryCurrent(poseFact)&&Float.floatToIntBits(c.player.getYRot())==Float.floatToIntBits(yaw)&&Float.floatToIntBits(c.player.getXRot())==Float.floatToIntBits(pitch);
        }catch(RuntimeException|Error unavailable){return false;}}
        public Object keySnapshot(KeyMapping exact){for(int i=0;i<9;i++)if(scene.keys[i]==exact)return keyFacts[i];return null;}
        public Object bindingSnapshot(KeyMapping exact){for(int i=0;i<9;i++)if(scene.keys[i]==exact)return scene.bindingHistory[i];return null;}
        public Object poseSnapshot(OwnedPoseHistoryCarrier exact){return exact==scene.poseCarrier?poseFact:null;}
        public boolean held(KeyMapping exact){for(int i=0;i<9;i++)if(scene.keys[i]==exact)return heldFacts[i];throw new IllegalArgumentException("Foreign mapping");}
        public String binding(KeyMapping exact){for(int i=0;i<9;i++)if(scene.keys[i]==exact)return bindingFacts[i];return null;}
        public float yaw(){return yaw;}public float pitch(){return pitch;}
        public boolean keyAdvancedFrom(KeyMapping exact,Object old){return actual!=null&&actual.keyAdvancedFrom(exact,old);}
        public boolean poseAdvancedFrom(OwnedPoseHistoryCarrier exact,Object old){return actual!=null&&actual.poseAdvancedFrom(exact,old);}
    }
    /** Canonical read only. No fresh key/pose capture, no TTL, no initializer fallback. */
    public static NoOwnerInputHandout peekNoOwner(Minecraft c){var actual=canonicalNoOwner;return actual!=null&&actual.current(c)?actual:null;}
    /** Only an actual private Root passive before/closed operation can advance an existing native-idle lifecycle header. */
    public static final class PassiveNativeTransitionTicket {
        private final VeltraphBootstrap.RootPassiveTransition source;private final NoOwnerInputHandout before;private final Minecraft client;private final ModuleManager.LifecycleSnapshot originalLifecycle;
        private boolean revoked,finished,closed;
        private PassiveNativeTransitionTicket(VeltraphBootstrap.RootPassiveTransition s,NoOwnerInputHandout old,Minecraft c){source=s;before=old;client=c;originalLifecycle=old.scene.lifecycle;}
    }
    public static PassiveNativeTransitionTicket beginPassiveTransition(VeltraphBootstrap.RootPassiveTransition actual,Minecraft client){try{
        if(passiveTransition!=null){passiveTransition.revoked=true;return null;}
        var old=canonicalNoOwner;if(actual==null||client==null||!client.isSameThread()||actual.originalInputPublication()!=null||old==null||!old.current(client)||!actual.sourceBeforeCurrent(client)||actual.originalInstallation()!=old.originalRoot||actual.registryIdentity()!=old.scene.root||!old.scene.root.lifecycleSameHistory(old.scene.lifecycle,actual.beforeLifecycle()))return null;
        var ticket=new PassiveNativeTransitionTicket(actual,old,client);passiveTransition=ticket;return ticket;
    }catch(RuntimeException|Error unavailable){return null;}}
    public static void finishPassiveTransition(PassiveNativeTransitionTicket ticket,VeltraphBootstrap.RootPassiveTransition actual,Minecraft client){if(ticket==null)return;try{
        if(passiveTransition!=ticket||ticket.closed||ticket.finished||ticket.revoked||ticket.source!=actual||ticket.client!=client||canonicalNoOwner!=ticket.before||actual==null||actual.originalInputPublication()!=null||!actual.normalReturnedCurrent(client)||actual.originalInstallation()!=ticket.before.originalRoot||actual.registryIdentity()!=ticket.before.scene.root||!actual.registryIdentity().lifecycleSameHistory(ticket.originalLifecycle,actual.beforeLifecycle())){ticket.revoked=true;return;}
        var issued=actual.normalReturnedLifecycle();if(issued==null){ticket.revoked=true;return;}var old=ticket.before;var header=new Scene(old.scene,old.scene.screen,issued);
        if(!old.installation.current()||!header.continuedHeader(old.originalRoot)){ticket.revoked=true;return;}
        for(int i=0;i<9;i++)if(!header.carriers[i].normalIdleKeyCurrent(old.keyFacts[i])||header.keys[i].isDown()!=old.heldFacts[i]||!NativeKeyBindingHistory.current(header.keys[i],old.scene.bindingHistory[i])||!old.bindingFacts[i].equals(header.keys[i].saveString())){ticket.revoked=true;return;}
        if(!header.poseCarrier.ownedPoseHistoryCurrent(old.poseFact)||Float.floatToRawIntBits(client.player.getYRot())!=Float.floatToRawIntBits(old.yaw)||Float.floatToRawIntBits(client.player.getXRot())!=Float.floatToRawIntBits(old.pitch)||!actual.normalReturnedCurrent(client)){ticket.revoked=true;return;}
        canonicalNoOwner=new NoOwnerInputHandout(header,old.birth,old.installation,old.keyFacts,old.heldFacts,old.bindingFacts,old.poseFact,old.yaw,old.pitch,old,null);ticket.finished=true;
    }catch(RuntimeException|Error unavailable){ticket.revoked=true;}}
    public static void closePassiveTransition(PassiveNativeTransitionTicket ticket,VeltraphBootstrap.RootPassiveTransition actual,Minecraft client){if(ticket==null)return;try{if(ticket.source!=actual||ticket.client!=client||passiveTransition!=ticket)ticket.revoked=true;ticket.closed=true;}finally{if(passiveTransition==ticket)passiveTransition=null;}}
    public static void acceptNativeBirth(NativePlayerBirthProof proof){
        try{if(proof==null)return;var c=proof.client();if(c==null||!c.isSameThread()||!claimBirthAttempt(proof)||!actualBirthHandoffCaller()||!proof.normalInitializationCurrent(c))return;
            if(!proof.current(c)||proof.player()!=c.player||proof.world()!=c.level||proof.listener()!=c.getConnection()||proof.nativeConnection()!=c.getConnection().getConnection()||proof.options()!=c.options)return;
            // Existing retained canonical installation metadata only; a generic installation/current capture is insufficient.
            var metadata=dev.veltraph.client.service.mc.McOwnedRotation.knownIdleMetadataSnapshot(c);if(metadata==null||!metadata.current())return;
            var installed=dev.veltraph.client.service.mc.McOwnedRotation.installationSnapshot(c);if(installed==null||!installed.current())return;
            var scene=new Scene(c,null);var rootInstallation=VeltraphBootstrap.originalNativeInstallationFor(c);if(!scene.header()||rootInstallation==null||!rootInstallation.currentNativeNoOwner(c))return;var keys=new Object[9];var held=new boolean[9];var bindings=new String[9];
            for(int i=0;i<9;i++){keys[i]=proof.keySnapshot(scene.keys[i]);held[i]=proof.held(scene.keys[i]);bindings[i]=proof.binding(scene.keys[i]);scene.bindingHistory[i]=proof.bindingSnapshot(scene.keys[i]);if(scene.bindingHistory[i]==null||!NativeKeyBindingHistory.current(scene.keys[i],scene.bindingHistory[i])||keys[i]==null||bindings[i]==null||!scene.carriers[i].normalIdleKeyCurrent(keys[i])||scene.keys[i].isDown()!=held[i]||!bindings[i].equals(scene.keys[i].saveString()))return;}
            var pose=proof.poseSnapshot();if(proof.poseCarrier()!=scene.poseCarrier||pose==null||!scene.poseCarrier.ownedPoseHistoryCurrent(pose)||!proof.current(c)||!metadata.current()||!installed.current()||!scene.header())return;
            canonicalNoOwner=new NoOwnerInputHandout(scene,proof,installed,keys,held,bindings,pose,proof.yaw(),proof.pitch(),null,null);
        }catch(RuntimeException|Error unavailable){/* Birth observation cannot change the real SDK handler result. */}
    }
    /** Existing actual terminal facts only; normal stop retires the old producer before this callback. */
    public static void acceptTerminal(dev.veltraph.client.service.mc.RootNativeHistory.TerminalNoOwnerHandout actual){try{
        if(actual==null)return;var c=actual.client();var idle=actual.idleContinuationIdentity();if(c==null||idle==null||!idle.currentFor(c)||!actual.current(c))return;
        var metadata=dev.veltraph.client.service.mc.McOwnedRotation.knownIdleMetadataSnapshot(c);if(metadata==null||!metadata.current())return;
        var installed=dev.veltraph.client.service.mc.McOwnedRotation.installationSnapshot(c);if(installed==null||!installed.current())return;
        var scene=new Scene(c,idle);var rootInstallation=VeltraphBootstrap.originalNativeInstallationFor(c);if(!scene.header()||rootInstallation==null||idle.installationIdentity()!=rootInstallation||!rootInstallation.currentNativeNoOwner(c))return;var keys=new Object[9];var held=new boolean[9];var bindings=new String[9];
        for(int i=0;i<9;i++){keys[i]=actual.keySnapshot(scene.keys[i]);held[i]=actual.held(scene.keys[i]);bindings[i]=actual.binding(scene.keys[i]);scene.bindingHistory[i]=actual.bindingSnapshot(scene.keys[i]);if(scene.bindingHistory[i]==null||!NativeKeyBindingHistory.current(scene.keys[i],scene.bindingHistory[i])||keys[i]==null||bindings[i]==null||!scene.carriers[i].normalIdleKeyCurrent(keys[i])||scene.keys[i].isDown()!=held[i]||!bindings[i].equals(scene.keys[i].saveString()))return;}
        var pose=actual.poseSnapshot(scene.poseCarrier);if(pose==null||!scene.poseCarrier.ownedPoseHistoryCurrent(pose)||!actual.current(c)||!metadata.current()||!installed.current()||!idle.currentFor(c)||actual.idleContinuationIdentity()!=idle||!scene.header())return;
        canonicalNoOwner=new NoOwnerInputHandout(scene,actual,installed,keys,held,bindings,pose,actual.yaw(),actual.pitch());
    }catch(RuntimeException|Error unavailable){/* No old poisoned workflow or current-value rebaseline. */}}
    private static void appendKnownReturn(NativeOpcodeReturn returned){var old=canonicalNoOwner;if(old==null||returned==null)return;
        try{var c=returned.before.client;if(!old.installation.current()||old.scene.player!=returned.before.player||old.scene.world!=returned.before.world||old.scene.listener!=returned.before.listener||old.scene.options!=returned.before.options||old.scene.root!=returned.before.root||!returned.current(c))return;
            for(int i=0;i<9;i++){if(old.scene.keys[i]!=returned.before.keys[i]||!old.bindingFacts[i].equals(returned.before.bindings[i])||old.heldFacts[i]!=returned.before.held[i])return;
                if(i==returned.keyIndex){if(!returned.keyAdvancedFrom(old.scene.keys[i],old.keyFacts[i]))return;}
                else if(!old.scene.carriers[i].normalIdleKeyCurrent(old.keyFacts[i]))return;
            }
            if(returned.keyIndex<0){if(!returned.poseAdvancedFrom(old.scene.poseCarrier,old.poseFact))return;}else if(!old.scene.poseCarrier.ownedPoseHistoryCurrent(old.poseFact))return;
            if(!old.scene.continuedHeader(old.originalRoot))return;
            canonicalNoOwner=new NoOwnerInputHandout(returned.before,old.birth,old.installation,returned.afterKeys,returned.afterHeld,returned.before.bindings,returned.afterPose,returned.afterYaw,returned.afterPitch,old,returned);
        }catch(RuntimeException|Error unavailable){/* Never rebaseline unknown history. */}
    }
    static void actualGuiResetReturned(NativeGuiKeyResetLineage.NormalReset actual){try{NativePlayerBirthProof.actualLoadingResetReturned(actual);}catch(RuntimeException|Error unavailable){}var old=canonicalNoOwner;if(old==null||actual==null)return;try{
        var c=old.scene.client;if(!actual.matchesNative(c,old.scene.options,old.scene.player,old.scene.world,old.scene.listener,old.scene.root)||actual.oldScreen()!=old.scene.screen||!old.installation.current()||!old.scene.poseCarrier.ownedPoseHistoryCurrent(old.poseFact))return;
        var after=new Object[9];var held=new boolean[9];for(int i=0;i<9;i++){if(!actual.advances(old.scene.keys[i],old.keyFacts[i])||!old.bindingFacts[i].equals(actual.binding(old.scene.keys[i])))return;after[i]=actual.after(old.scene.keys[i]);}
        var header=new Scene(old.scene,actual.nextScreen());if(!header.continuedHeader(old.originalRoot))return;
        canonicalNoOwner=new NoOwnerInputHandout(header,old.birth,old.installation,after,held,old.bindingFacts,old.poseFact,old.yaw,old.pitch,old,null);
    }catch(RuntimeException|Error unavailable){/* Only the exact normal native screen transition can change the header. */}}
    static void actualGuiTransitionReturned(NativeGuiKeyResetLineage.NormalTransition actual){try{NativePlayerBirthProof.actualLoadingTransitionReturned(actual);}catch(RuntimeException|Error unavailable){}var old=canonicalNoOwner;if(old==null||actual==null)return;try{
        var c=old.scene.client;if(!actual.matchesNative(c,old.scene.options,old.scene.player,old.scene.world,old.scene.listener,old.scene.root)||actual.oldScreen()!=old.scene.screen||!old.installation.current()||!old.scene.poseCarrier.ownedPoseHistoryCurrent(old.poseFact))return;
        var after=new Object[9];var held=new boolean[9];for(int i=0;i<9;i++){if(!actual.advances(old.scene.keys[i],old.keyFacts[i])||!old.bindingFacts[i].equals(actual.binding(old.scene.keys[i])))return;after[i]=actual.after(old.scene.keys[i]);held[i]=actual.held(old.scene.keys[i]);}
        var header=new Scene(old.scene,actual.nextScreen());if(!header.continuedHeader(old.originalRoot))return;
        canonicalNoOwner=new NoOwnerInputHandout(header,old.birth,old.installation,after,held,old.bindingFacts,old.poseFact,old.yaw,old.pitch,old,null);
    }catch(RuntimeException|Error unavailable){/* Declared native GUI handover alone changes the header. */}}
    public static final class KeyboardEpisode {
        private final Scene scene;private final long window;private final int action;private final KeyEvent event;private final KeyboardEpisode prior;
        private NativeOpcodeReturn candidate;private NativeToggleKeyAttempt toggleNoChange;private NoOwnerInputHandout noChangeBefore;private Object noChangeAfter;private int noChangeIndex=-1;private int staticCalls;private boolean invalid;
        private KeyboardEpisode(Scene scene,long window,int action,KeyEvent event,KeyboardEpisode prior){this.scene=scene;this.window=window;this.action=action;this.event=event;this.prior=prior;invalid=prior!=null;}
    }
    public static KeyboardEpisode beginKeyboard(long window,int action,KeyEvent event){
        try{if(!NativeSdlInputLineage.claimKeyboard(window,action,event)||!sdkCaller(net.minecraft.client.KeyboardHandler.class,"keyPress"))return null;var scene=Scene.capture();if(scene==null||scene.screen!=null||window!=scene.client.getWindow().handle()||event==null||action!=InputConstants.PRESS&&action!=InputConstants.RELEASE&&action!=InputConstants.REPEAT)return null;
            var prior=KEYBOARD.get();if(prior!=null)prior.invalid=true;var ep=new KeyboardEpisode(scene,window,action,event,prior);var mouse=MOUSE_BUTTON.get();if(mouse!=null){mouse.invalid=true;ep.invalid=true;}KEYBOARD.set(ep);return ep;}catch(RuntimeException|Error unavailable){return null;}}
    public static void finishKeyboard(KeyboardEpisode exact,boolean normal){if(exact==null)return;try{
        if(KEYBOARD.get()!=exact){exact.invalid=true;return;}
        lastKeyboard=null;
        if(normal&&!exact.invalid&&exact.staticCalls==1&&exact.noChangeBefore!=null){
            if(!NativeSdlInputLineage.deferManualCompletion(()->{if(exact.toggleNoChange!=null)appendKnownToggleNoChange(exact.toggleNoChange,exact.noChangeAfter);else appendKnownKeyboardNoChange(exact);}))exact.invalid=true;
        }else if(normal&&!exact.invalid&&exact.staticCalls==1&&exact.candidate!=null&&exact.candidate.current(exact.scene.client)){
            if(!NativeSdlInputLineage.deferManualCompletion(()->{if(!exact.invalid&&exact.candidate.current(exact.scene.client)){lastKeyboard=exact.candidate;appendKnownReturn(exact.candidate);}}))exact.invalid=true;
        }
    }catch(RuntimeException|Error unavailable){lastKeyboard=null;}finally{if(KEYBOARD.get()==exact)KEYBOARD.set(exact.prior);}}
    /** Actual repeat/same-held field return can continue only a preexisting known source; no public no-op origin is minted. */
    private static void appendKnownKeyboardNoChange(KeyboardEpisode actual){appendKnownKeyNoChange(actual.scene,actual.noChangeBefore,actual.noChangeIndex,actual.noChangeAfter);}
    private static void appendKnownKeyNoChange(Scene s,NoOwnerInputHandout old,int index,Object after){
        if(old==null||canonicalNoOwner!=old||index<0||(index>4&&index!=7&&index!=8)||after==null||!s.current(index,false)||!old.installation.current()||!old.scene.continuedHeader(old.originalRoot)
                ||!s.carriers[index].normalIdleUnscopedAdvanced(old.keyFacts[index],after)||!s.carriers[index].normalIdleKeyCurrent(after)||s.keys[index].isDown()!=old.heldFacts[index]
                ||!old.scene.poseCarrier.ownedPoseHistoryCurrent(old.poseFact)||Float.floatToIntBits(s.client.player.getYRot())!=Float.floatToIntBits(old.yaw)||Float.floatToIntBits(s.client.player.getXRot())!=Float.floatToIntBits(old.pitch))return;
        for(int i=0;i<9;i++)if(s.keys[i]!=old.scene.keys[i]||s.bindingHistory[i]!=old.scene.bindingHistory[i]||i!=index&&!old.scene.carriers[i].normalIdleKeyCurrent(old.keyFacts[i]))return;
        var keys=old.keyFacts.clone();keys[index]=after;canonicalNoOwner=new NoOwnerInputHandout(s,old.birth,old.installation,keys,old.heldFacts,old.bindingFacts,old.poseFact,old.yaw,old.pitch,old,null);
    }
    public static final class StaticKeyEpisode {
        private final KeyboardEpisode handler;private final int index;private final boolean down;private final StaticKeyEpisode prior;
        private final NativeOpcodeReturnTrace trace=new NativeOpcodeReturnTrace(List.of(KEY_SET));private boolean invalid;
        private NativeToggleKeyAttempt toggle;
        private StaticKeyEpisode(KeyboardEpisode handler,int index,boolean down,StaticKeyEpisode prior){this.handler=handler;this.index=index;this.down=down;this.prior=prior;invalid=prior!=null;}
    }
    public static StaticKeyEpisode beginKeyboardSet(InputConstants.Key nativeKey,boolean down){
        var handler=KEYBOARD.get();if(handler==null)return null;
        try{handler.staticCalls++;if(!sdkCaller(net.minecraft.client.KeyboardHandler.class,"keyPress")||handler.invalid||handler.staticCalls!=1||nativeKey!=InputConstants.getKey(handler.event)||down!=(handler.action!=InputConstants.RELEASE)||!handler.scene.current(-1,false))return null;
            // Supported keyboard movement channels: actual plain forward/backward/strafe/jump, never toggle/ambiguous duplicate bindings.
            int index=-1;for(int i=0;i<9;i++)if(handler.scene.keys[i].matches(handler.event)){if(index>=0){handler.invalid=true;return null;}index=i;}
            if(index<0||index>8||((index<=4&&handler.scene.keys[index].getClass()!=KeyMapping.class)||(index>=5&&handler.scene.keys[index].getClass()!=net.minecraft.client.ToggleKeyMapping.class))){handler.invalid=true;return null;}
            if(index<=4&&handler.scene.held[index]==down){var known=canonicalNoOwner;if(known==null||!known.current(handler.scene.client)||known.scene.keys[index]!=handler.scene.keys[index]||!handler.scene.carriers[index].normalIdleKeyAdvanced(known.keyFacts[index],handler.scene.histories[index],List.of())){handler.invalid=true;return null;}handler.noChangeBefore=known;handler.noChangeIndex=index;}
            var prior=STATIC_KEY.get();if(prior!=null){prior.invalid=true;handler.invalid=true;}var ep=new StaticKeyEpisode(handler,index,down,prior);STATIC_KEY.set(ep);return ep;
        }catch(RuntimeException|Error unavailable){handler.invalid=true;return null;}}
    public static void finishKeyboardSet(StaticKeyEpisode exact,boolean normal){if(exact==null)return;try{
        var h=exact.handler;var s=h.scene;
        if(exact.toggle!=null){finishToggleKeyboardSet(exact,normal);return;}
        if(STATIC_KEY.get()!=exact||exact.invalid||!exact.trace.finish(normal)||!s.current(exact.index,false)){h.invalid=true;return;}
        var after=s.carriers[exact.index].normalIdleKeySnapshot();if(after==null||!s.carriers[exact.index].normalIdleUnscopedAdvanced(s.histories[exact.index],after)||!s.carriers[exact.index].normalIdleKeyCurrent(after)||s.keys[exact.index].isDown()!=exact.down){h.invalid=true;return;}
        var histories=s.histories.clone();histories[exact.index]=after;var held=s.held.clone();held[exact.index]=exact.down;
        if(h.noChangeBefore!=null){h.noChangeAfter=after;return;}
        h.candidate=new NativeOpcodeReturn(s,exact.index,histories,held,s.poseHistory,List.of(),List.of(),"KeyboardHandler.keyPress→KeyMapping.set→lambda$set$0→setDown");
    }catch(RuntimeException|Error unavailable){exact.handler.invalid=true;}finally{if(STATIC_KEY.get()==exact)STATIC_KEY.set(exact.prior);}}
    /** Actual gameplay mouse held channel only. Screen clicks, control-click quirks and unsupported bindings remain unknown. */
    public static final class MouseButtonEpisode {
        private final Scene scene;private final long window;private final net.minecraft.client.input.MouseButtonInfo info;private final int action;private final MouseButtonEpisode prior;
        private boolean invalid;private int staticCalls;private NativeOpcodeReturn candidate;private NativeToggleKeyAttempt toggleNoChange;private NoOwnerInputHandout noChangeBefore;private int noChangeIndex=-1;private Object noChangeAfter;
        private MouseButtonEpisode(Scene s,long w,net.minecraft.client.input.MouseButtonInfo i,int a,MouseButtonEpisode p){scene=s;window=w;info=i;action=a;prior=p;invalid=p!=null;}
    }
    public static MouseButtonEpisode beginMouseButton(long window,net.minecraft.client.input.MouseButtonInfo info,int action){try{
        if(!NativeSdlInputLineage.claimButton(window,info,action)||!sdkCaller(net.minecraft.client.MouseHandler.class,"onButton")||info==null||info.hasControlDownWithQuirk()||action!=InputConstants.PRESS&&action!=InputConstants.RELEASE)return null;
        var s=Scene.capture();if(s==null||s.screen!=null||window!=s.client.getWindow().handle()||!s.client.mouseHandler.isMouseGrabbed())return null;
        var prior=MOUSE_BUTTON.get();if(prior!=null)prior.invalid=true;var result=new MouseButtonEpisode(s,window,info,action,prior);var keyboard=KEYBOARD.get();if(keyboard!=null){keyboard.invalid=true;result.invalid=true;}MOUSE_BUTTON.set(result);return result;
    }catch(RuntimeException|Error unavailable){return null;}}
    public static void finishMouseButton(MouseButtonEpisode exact,boolean normal){if(exact==null)return;try{
        if(MOUSE_BUTTON.get()!=exact){exact.invalid=true;return;}
        lastButton=null;
        if(normal&&!exact.invalid&&exact.staticCalls==1&&exact.noChangeBefore!=null){
            if(!NativeSdlInputLineage.deferManualCompletion(()->{if(exact.toggleNoChange!=null)appendKnownToggleNoChange(exact.toggleNoChange,exact.noChangeAfter);else appendKnownKeyNoChange(exact.scene,exact.noChangeBefore,exact.noChangeIndex,exact.noChangeAfter);}))exact.invalid=true;
        }else if(normal&&!exact.invalid&&exact.staticCalls==1&&exact.candidate!=null&&exact.candidate.current(exact.scene.client)){
            if(!NativeSdlInputLineage.deferManualCompletion(()->{if(!exact.invalid&&exact.candidate.current(exact.scene.client)){lastButton=exact.candidate;appendKnownReturn(exact.candidate);}}))exact.invalid=true;
        }
    }catch(RuntimeException|Error unavailable){lastButton=null;}finally{if(MOUSE_BUTTON.get()==exact)MOUSE_BUTTON.set(exact.prior);}}
    public static NativeOpcodeReturn peekMouseButtonReturn(Minecraft c){var r=lastButton;return r!=null&&r.current(c)?r:null;}
    public static final class MouseStaticKeyEpisode {
        private final MouseButtonEpisode handler;private final int index;private final boolean down;private final MouseStaticKeyEpisode prior;private boolean invalid;
        private final NativeOpcodeReturnTrace trace=new NativeOpcodeReturnTrace(List.of(KEY_SET));
        private NativeToggleKeyAttempt toggle;
        private MouseStaticKeyEpisode(MouseButtonEpisode h,int i,boolean d,MouseStaticKeyEpisode p){handler=h;index=i;down=d;prior=p;invalid=p!=null;}
    }
    public static MouseStaticKeyEpisode beginMouseButtonSet(InputConstants.Key nativeKey,boolean down){var h=MOUSE_BUTTON.get();if(h==null)return null;try{
        if(!sdkCaller(net.minecraft.client.MouseHandler.class,"onButton")||h.invalid||++h.staticCalls!=1||nativeKey!=InputConstants.Type.MOUSE.getOrCreate(h.info.button())||down!=(h.action==InputConstants.PRESS)||!h.scene.current(-1,false))return null;
        int index=-1;for(int i=0;i<9;i++)if(h.scene.keys[i].matches(nativeKey)){if(index>=0){h.invalid=true;return null;}index=i;}
        if((index!=7&&index!=8)||(h.scene.keys[index].getClass()!=KeyMapping.class&&h.scene.keys[index].getClass()!=net.minecraft.client.ToggleKeyMapping.class)){h.invalid=true;return null;}
        if(h.scene.keys[index].getClass()==KeyMapping.class&&h.scene.held[index]==down){var known=canonicalNoOwner;if(known==null||!known.current(h.scene.client)||known.scene.keys[index]!=h.scene.keys[index]||!h.scene.carriers[index].normalIdleKeyAdvanced(known.keyFacts[index],h.scene.histories[index],List.of())){h.invalid=true;return null;}h.noChangeBefore=known;h.noChangeIndex=index;}
        var prior=MOUSE_STATIC_KEY.get();if(prior!=null){prior.invalid=true;h.invalid=true;}var result=new MouseStaticKeyEpisode(h,index,down,prior);MOUSE_STATIC_KEY.set(result);return result;
    }catch(RuntimeException|Error unavailable){h.invalid=true;return null;}}
    public static void finishMouseButtonSet(MouseStaticKeyEpisode exact,boolean normal){if(exact==null)return;try{var h=exact.handler;var s=h.scene;
        if(exact.toggle!=null){finishToggleMouseSet(exact,normal);return;}
        if(MOUSE_STATIC_KEY.get()!=exact||exact.invalid||!exact.trace.finish(normal)||!s.current(exact.index,false)){h.invalid=true;return;}
        var after=s.carriers[exact.index].normalIdleKeySnapshot();if(after==null||!s.carriers[exact.index].normalIdleUnscopedAdvanced(s.histories[exact.index],after)||!s.carriers[exact.index].normalIdleKeyCurrent(after)||s.keys[exact.index].isDown()!=exact.down){h.invalid=true;return;}
        if(h.noChangeBefore!=null){h.noChangeAfter=after;return;}var keys=s.histories.clone();keys[exact.index]=after;var held=s.held.clone();held[exact.index]=exact.down;
        h.candidate=new NativeOpcodeReturn(s,exact.index,keys,held,s.poseHistory,List.of(),List.of(),"MouseHandler.onButton→KeyMapping.set→lambda$set$0→setDown→actual field normal return");
    }catch(RuntimeException|Error unavailable){exact.handler.invalid=true;}finally{if(MOUSE_STATIC_KEY.get()==exact)MOUSE_STATIC_KEY.set(exact.prior);}}
    public static final class MouseKeyAttempt {
        private final MouseStaticKeyEpisode source;private final NativeOpcodeReturnTrace.Attempt attempt;private Object fieldToken;private boolean fieldReturned;
        private MouseKeyAttempt(MouseStaticKeyEpisode s,NativeOpcodeReturnTrace.Attempt a){source=s;attempt=a;}
    }
    public static MouseKeyAttempt beginNativeMouseKeySetter(KeyMapping key,boolean down){var source=MOUSE_STATIC_KEY.get();if(source==null)return null;if(source.handler.scene.keys[source.index].getClass()==net.minecraft.client.ToggleKeyMapping.class){if(source.handler.scene.keys[source.index]!=key)source.invalid=true;return null;}try{
        if(source.invalid||!sdkCaller(KeyMapping.class,"lambda$set$0")||source.handler.scene.keys[source.index]!=key||source.down!=down||MOUSE_KEY_ATTEMPT.get()!=null){source.invalid=true;return null;}
        var a=source.trace.begin(KEY_SET,key,down?1:0);if(a==null)return null;var result=new MouseKeyAttempt(source,a);MOUSE_KEY_ATTEMPT.set(result);return result;
    }catch(RuntimeException|Error unavailable){source.invalid=true;return null;}}
    public static Object claimNativeMouseKeySetter(KeyMapping key,boolean down){var a=MOUSE_KEY_ATTEMPT.get();if(a==null)return null;try{if(a.source.invalid||!actualKeyFieldCaller()){a.source.invalid=true;return null;}var token=a.source.trace.claim(KEY_SET,key,down?1:0);a.fieldToken=token;return token;}catch(RuntimeException|Error unavailable){a.source.invalid=true;return null;}}
    public static void nativeMouseKeyFieldReturned(Object token,KeyMapping key,boolean down,boolean normal){var a=MOUSE_KEY_ATTEMPT.get();if(a==null)return;try{if(a.source.invalid||!actualKeyFieldCaller()||token==null||token!=a.fieldToken||a.fieldReturned||!normal||key!=a.source.handler.scene.keys[a.source.index]||key.isDown()!=down){a.source.invalid=true;return;}a.fieldReturned=true;}catch(RuntimeException|Error unavailable){a.source.invalid=true;}}
    public static void finishNativeMouseKeySetter(MouseKeyAttempt a,boolean normal){if(a==null)return;try{if(MOUSE_KEY_ATTEMPT.get()!=a){a.source.invalid=true;return;}a.source.trace.normalReturn(a.attempt,normal&&a.fieldReturned&&!a.source.invalid);}catch(RuntimeException|Error unavailable){a.source.invalid=true;}finally{if(MOUSE_KEY_ATTEMPT.get()==a)MOUSE_KEY_ATTEMPT.remove();}}
    /** Exact native toggle query/optional-super/field trace; no McHeld token or history primitive is changed. */
    public static final class NativeToggleKeyAttempt {
        private final StaticKeyEpisode keyboard;private final MouseStaticKeyEpisode mouse;private final Scene scene;private final int index;private final KeyMapping key;private final boolean requested;private final NoOwnerInputHandout knownBefore;private final Object sourceIdentity;
        private NativeToggleReturnTrace trace;private NativeToggleReturnTrace.Return returned;private ToggleSuper activeSuper;private boolean nestedEntryObserved;
        private NativeToggleKeyAttempt(StaticKeyEpisode k,MouseStaticKeyEpisode m,Scene s,int i,boolean requested){keyboard=k;mouse=m;scene=s;index=i;key=s.keys[i];this.requested=requested;sourceIdentity=k!=null?k:m;knownBefore=canonicalNoOwner!=null&&canonicalNoOwner.current(s.client)?canonicalNoOwner:null;}
        private void invalid(){if(keyboard!=null){keyboard.invalid=true;keyboard.handler.invalid=true;}if(mouse!=null){mouse.invalid=true;mouse.handler.invalid=true;}}
    }
    private static boolean toggleCaller(String suffix){return StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).walk(frames->frames.limit(8).anyMatch(f->f.getDeclaringClass()==net.minecraft.client.ToggleKeyMapping.class&&f.getMethodName().endsWith(suffix)));}
    public static NativeToggleKeyAttempt beginNativeToggleKeySetter(KeyMapping key,boolean requested,java.util.function.BooleanSupplier supplier){NativeToggleKeyAttempt a=null;try{
        var selected=NATIVE_TOGGLE.get();if(selected!=null){selected.nestedEntryObserved=true;selected.invalid();if(selected.trace!=null)selected.trace.nestedEntryObserved();return null;}
        if(!toggleCaller("veltraph$manualToggleOuter")||!sdkCaller(KeyMapping.class,"lambda$set$0")||key.getClass()!=net.minecraft.client.ToggleKeyMapping.class)return null;
        var k=STATIC_KEY.get();var m=MOUSE_STATIC_KEY.get();if((k==null)==(m==null)){if(k!=null)k.invalid=true;if(m!=null)m.invalid=true;return null;}
        var scene=k!=null?k.handler.scene:m.handler.scene;int index=k!=null?k.index:m.index;boolean down=k!=null?k.down:m.down;
        if(scene.keys[index]!=key||requested!=down||!scene.current(-1,false))return null;
        a=new NativeToggleKeyAttempt(k,m,scene,index,requested);a.trace=NativeToggleReturnTrace.begin(a.sourceIdentity,key,requested,scene.held[index],supplier);if(a.trace==null){a.invalid();return null;}if(k!=null)k.toggle=a;else m.toggle=a;NATIVE_TOGGLE.set(a);return a;
    }catch(RuntimeException|Error unavailable){if(a!=null){a.invalid();if(a.trace!=null)try{a.trace.outerReturned(a.sourceIdentity,a.key,a.requested,a.scene.held[a.index],false);}catch(RuntimeException|Error ignored){}}return null;}}
    public static final class ToggleMode {private final NativeToggleKeyAttempt source;private final NativeToggleReturnTrace.Mode exact;private ToggleMode(NativeToggleKeyAttempt s,NativeToggleReturnTrace.Mode e){source=s;exact=e;}}
    public static ToggleMode beginNativeToggleMode(KeyMapping key,java.util.function.BooleanSupplier supplier){var a=NATIVE_TOGGLE.get();if(a==null)return null;try{if(!toggleCaller("veltraph$manualToggleMode")||key!=a.key||!a.scene.current(a.index,false)){a.invalid();return null;}var exact=a.trace.modeEntered(key,supplier);return exact==null?null:new ToggleMode(a,exact);}catch(RuntimeException|Error unavailable){a.invalid();return null;}}
    public static void finishNativeToggleMode(ToggleMode mode,boolean result,boolean normal){if(mode==null)return;try{if(NATIVE_TOGGLE.get()!=mode.source){mode.source.invalid();return;}mode.source.trace.modeReturned(mode.exact,result,normal);}catch(RuntimeException|Error unavailable){mode.source.invalid();}}
    public static final class ToggleSuper {private final NativeToggleKeyAttempt source;private final NativeToggleReturnTrace.Super exact;private final Object fieldToken=new Object();private NativeToggleReturnTrace.Field field;private final boolean down;private ToggleSuper(NativeToggleKeyAttempt a,NativeToggleReturnTrace.Super e,boolean d){source=a;exact=e;down=d;}}
    public static ToggleSuper beginNativeToggleSuper(KeyMapping key,boolean down){var a=NATIVE_TOGGLE.get();if(a==null)return null;try{if(!toggleCaller("veltraph$manualToggleSuper")||key!=a.key||a.activeSuper!=null||!a.scene.current(a.index,false)){a.invalid();return null;}var exact=a.trace.superEntered(key,down);if(exact==null)return null;var result=new ToggleSuper(a,exact,down);a.activeSuper=result;return result;}catch(RuntimeException|Error unavailable){a.invalid();return null;}}
    public static Object claimNativeToggleKeySetter(KeyMapping key,boolean down){var a=NATIVE_TOGGLE.get();if(a==null)return null;try{var inner=a.activeSuper;if(!actualKeyFieldCaller()||key!=a.key||inner==null||inner.down!=down||inner.field!=null){a.invalid();return null;}inner.field=a.trace.fieldEntered(inner.exact,inner.fieldToken,key,down);return inner.field==null?null:inner.fieldToken;}catch(RuntimeException|Error unavailable){a.invalid();return null;}}
    public static void nativeToggleKeyFieldReturned(Object token,KeyMapping key,boolean down,boolean normal){var a=NATIVE_TOGGLE.get();if(a==null)return;try{var inner=a.activeSuper;if(!actualKeyFieldCaller()||key!=a.key||inner==null||token!=inner.fieldToken||inner.field==null||key.isDown()!=down){a.invalid();return;}a.trace.fieldReturned(inner.field,token,key,down,normal);}catch(RuntimeException|Error unavailable){a.invalid();}}
    public static void finishNativeToggleSuper(ToggleSuper inner,boolean normal){if(inner==null)return;try{if(NATIVE_TOGGLE.get()!=inner.source||inner.source.activeSuper!=inner){inner.source.invalid();return;}inner.source.trace.superReturned(inner.exact,normal);}catch(RuntimeException|Error unavailable){inner.source.invalid();}finally{if(inner.source.activeSuper==inner)inner.source.activeSuper=null;}}
    public static void finishNativeToggleKeySetter(NativeToggleKeyAttempt a,boolean normal){if(a==null)return;try{if(NATIVE_TOGGLE.get()!=a){a.invalid();try{a.trace.outerReturned(a.sourceIdentity,a.key,a.requested,a.scene.held[a.index],false);}catch(RuntimeException|Error ignored){}return;}a.returned=a.trace.outerReturned(a.sourceIdentity,a.key,a.requested,a.key.isDown(),normal);}catch(RuntimeException|Error unavailable){a.invalid();try{a.trace.outerReturned(a.sourceIdentity,a.key,a.requested,a.scene.held[a.index],false);}catch(RuntimeException|Error ignored){}}finally{if(NATIVE_TOGGLE.get()==a)NATIVE_TOGGLE.remove();}}
    private static boolean toggleNormalHistory(NativeToggleKeyAttempt a,Object after){return a!=null&&a.returned!=null&&a.returned.current()&&a.scene.keys[a.index].isDown()==a.returned.held()&&a.scene.carriers[a.index].normalIdleKeyCurrent(after)
        &&(a.returned.hasField()?a.scene.carriers[a.index].normalIdleKeyAdvanced(a.scene.histories[a.index],after,Collections.singletonList(null)):a.scene.carriers[a.index].normalIdleUnscopedAdvanced(a.scene.histories[a.index],after));}
    private static void appendKnownToggleNoChange(NativeToggleKeyAttempt a,Object after){var old=a.knownBefore;var s=a.scene;if(old==null||canonicalNoOwner!=old||a.returned==null||!a.returned.current()||a.returned.held()!=old.heldFacts[a.index]||!toggleNormalHistory(a,after)||!s.current(a.index,false)||!old.installation.current()||!old.scene.continuedHeader(old.originalRoot)||!old.scene.poseCarrier.ownedPoseHistoryCurrent(old.poseFact)||Float.floatToIntBits(s.client.player.getYRot())!=Float.floatToIntBits(old.yaw)||Float.floatToIntBits(s.client.player.getXRot())!=Float.floatToIntBits(old.pitch))return;
        for(int i=0;i<9;i++)if(s.keys[i]!=old.scene.keys[i]||s.bindingHistory[i]!=old.scene.bindingHistory[i]||i!=a.index&&!old.scene.carriers[i].normalIdleKeyCurrent(old.keyFacts[i]))return;
        if(!s.carriers[a.index].normalIdleKeyAdvanced(old.keyFacts[a.index],s.histories[a.index],List.of()))return;var keys=old.keyFacts.clone();keys[a.index]=after;canonicalNoOwner=new NoOwnerInputHandout(s,old.birth,old.installation,keys,old.heldFacts,old.bindingFacts,old.poseFact,old.yaw,old.pitch,old,null);
    }
    private static NativeOpcodeReturn toggleChangedReturn(NativeToggleKeyAttempt a,Object after,String source){var s=a.scene;if(a.returned==null||!a.returned.hasField()||a.returned.held()==s.held[a.index])return null;var keys=s.histories.clone();keys[a.index]=after;var held=s.held.clone();held[a.index]=a.returned.held();return new NativeOpcodeReturn(s,a.index,keys,held,s.poseHistory,List.of(),List.of(),source,a.returned);}
    private static void finishToggleKeyboardSet(StaticKeyEpisode exact,boolean normal){var a=exact.toggle;var h=exact.handler;var s=h.scene;var after=s.carriers[exact.index].normalIdleKeySnapshot();if(STATIC_KEY.get()!=exact||exact.invalid||!normal||!s.current(exact.index,false)||!toggleNormalHistory(a,after)){h.invalid=true;return;}if(a.returned.held()==s.held[exact.index]){h.noChangeBefore=a.knownBefore;h.noChangeIndex=exact.index;h.noChangeAfter=after;h.toggleNoChange=a;return;}h.candidate=toggleChangedReturn(a,after,"KeyboardHandler.keyPress→ToggleKeyMapping mode/super/field normal trace");}
    private static void finishToggleMouseSet(MouseStaticKeyEpisode exact,boolean normal){var a=exact.toggle;var h=exact.handler;var s=h.scene;var after=s.carriers[exact.index].normalIdleKeySnapshot();if(MOUSE_STATIC_KEY.get()!=exact||exact.invalid||!normal||!s.current(exact.index,false)||!toggleNormalHistory(a,after)){h.invalid=true;return;}if(a.returned.held()==s.held[exact.index]){h.noChangeBefore=a.knownBefore;h.noChangeIndex=exact.index;h.noChangeAfter=after;h.toggleNoChange=a;return;}h.candidate=toggleChangedReturn(a,after,"MouseHandler.onButton→ToggleKeyMapping mode/super/field normal trace");}
    public static final class NativeKeyAttempt {private final StaticKeyEpisode source;private final NativeOpcodeReturnTrace.Attempt attempt;private final NativeKeyAttempt prior;private Object fieldToken;private boolean fieldReturned;
        private NativeKeyAttempt(StaticKeyEpisode source,NativeOpcodeReturnTrace.Attempt attempt,NativeKeyAttempt prior){this.source=source;this.attempt=attempt;this.prior=prior;}}
    public static NativeKeyAttempt beginNativeKeySetter(KeyMapping key,boolean down){var source=STATIC_KEY.get();if(source==null)return null;if(source.handler.scene.keys[source.index].getClass()==net.minecraft.client.ToggleKeyMapping.class){if(source.handler.scene.keys[source.index]!=key)source.invalid=true;return null;}
        try{if(!sdkCaller(KeyMapping.class,"lambda$set$0")||source.handler.scene.keys[source.index]!=key||source.down!=down){source.invalid=true;return null;}
            var prior=KEY_ATTEMPT.get();if(prior!=null){source.invalid=true;return null;}var a=source.trace.begin(KEY_SET,key,down?1:0);if(a==null)return null;
            var result=new NativeKeyAttempt(source,a,prior);KEY_ATTEMPT.set(result);return result;}catch(RuntimeException|Error unavailable){source.invalid=true;return null;}}
    /** Exact private woven field-opcode caller; SDK enclosing setDown alone cannot qualify a callback. */
    private static boolean actualKeyFieldCaller(){return StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).walk(s->s.limit(6).anyMatch(f->f.getDeclaringClass()==KeyMapping.class&&f.getMethodName().endsWith("veltraph$manualActualKeyWrite")));}
    public static Object claimNativeKeySetter(KeyMapping key,boolean down){var actual=KEY_ATTEMPT.get();if(actual==null)return null;
        try{if(actual.source.invalid||!actualKeyFieldCaller()){actual.source.invalid=true;return null;}var token=actual.source.trace.claim(KEY_SET,key,down?1:0);actual.fieldToken=token;return token;}
        catch(RuntimeException|Error unavailable){actual.source.invalid=true;return null;}}
    public static void nativeKeyFieldReturned(Object token,KeyMapping key,boolean down,boolean normal){var actual=KEY_ATTEMPT.get();if(actual==null)return;
        try{if(!actualKeyFieldCaller()||actual.source.invalid||token==null||token!=actual.fieldToken||actual.fieldReturned||!normal||key!=actual.source.handler.scene.keys[actual.source.index]||key.isDown()!=down){actual.source.invalid=true;return;}actual.fieldReturned=true;}
        catch(RuntimeException|Error unavailable){actual.source.invalid=true;}}
    public static void finishNativeKeySetter(NativeKeyAttempt exact,boolean normal){if(exact==null)return;try{if(KEY_ATTEMPT.get()!=exact){exact.source.invalid=true;return;}exact.source.trace.normalReturn(exact.attempt,normal&&exact.fieldReturned&&!exact.source.invalid);}catch(RuntimeException|Error unavailable){exact.source.invalid=true;}finally{if(KEY_ATTEMPT.get()==exact)KEY_ATTEMPT.set(exact.prior);}}
    private static final ThreadLocal<java.util.ArrayDeque<PoseSetterEntry>> POSE_SETTERS=ThreadLocal.withInitial(java.util.ArrayDeque::new);
    private record PoseSetterEntry(Entity actor,boolean yaw,float requested,Object actualToken){}
    public static void nativePoseSetterEntered(Entity actor,boolean yaw,float requested,Object actualToken){try{POSE_SETTERS.get().push(new PoseSetterEntry(actor,yaw,requested,actualToken));}catch(RuntimeException|Error unavailable){var ep=POSE.get();if(ep!=null)ep.invalid=true;}}
    public static void nativePoseSetterReturned(Entity actor,boolean yaw){try{var stack=POSE_SETTERS.get();if(stack.isEmpty())return;var actual=stack.pop();if(actual.actor()!=actor||actual.yaw()!=yaw){stack.push(actual);return;}}catch(RuntimeException|Error unavailable){var ep=POSE.get();if(ep!=null)ep.invalid=true;}}
    /** Already claimed setter token only; this never allocates a source or claims a second event. */
    public static Object nativePoseFieldToken(Entity actor,boolean yaw){try{var actual=POSE_SETTERS.get().peek();return actual!=null&&actual.actor()==actor&&actual.yaw()==yaw?actual.actualToken():null;}catch(RuntimeException|Error unavailable){return null;}}
    public static float nativePoseFieldRequested(Entity actor,boolean yaw,float fieldValue){try{var actual=POSE_SETTERS.get().peek();return actual!=null&&actual.actor()==actor&&actual.yaw()==yaw?actual.requested():fieldValue;}catch(RuntimeException|Error unavailable){return fieldValue;}}
    private static boolean actualPoseFieldCaller(boolean yaw){String suffix=yaw?"veltraph$manualPoseYawField":"veltraph$manualPosePitchField";
        return StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).walk(s->s.limit(6).anyMatch(f->f.getDeclaringClass()==Entity.class&&f.getMethodName().endsWith(suffix)));}
    public static void nativePoseFieldReturned(Object token,Entity actor,boolean yaw,float actualValue,boolean normal){try{
        if(!actualPoseFieldCaller(yaw)){var ep=POSE.get();if(ep!=null)ep.invalid=true;return;}
        var actual=POSE_SETTERS.get().peek();if(actual==null||actual.actor()!=actor||actual.yaw()!=yaw||actual.actualToken()!=token)return;
        var ep=POSE.get();if(ep!=null){var opcode=ep.activeOpcode;
            if(opcode==null||opcode.source!=ep||opcode.attempt!=token||opcode.fieldReturned||token==null||!normal||!Float.isFinite(actualValue)||Float.floatToIntBits(yaw?actor.getYRot():actor.getXRot())!=Float.floatToIntBits(actualValue))ep.invalid=true;
            else opcode.fieldReturned=true;
        }
    }catch(RuntimeException|Error unavailable){var ep=POSE.get();if(ep!=null)ep.invalid=true;}}
    public static final class MouseMoveEpisode {
        private final Scene scene;private final Object handler,beforeMouse;private final dev.veltraph.client.service.mc.NormalIdleMouseCarrier history;
        private final MouseMoveEpisode prior;private boolean invalid;
        private MouseMoveEpisode(Scene scene,Object handler,dev.veltraph.client.service.mc.NormalIdleMouseCarrier history,Object beforeMouse,MouseMoveEpisode prior){this.scene=scene;this.handler=handler;this.history=history;this.beforeMouse=beforeMouse;this.prior=prior;invalid=prior!=null;}
    }
    private static final class MouseMoveBatch {
        final Scene first;final VeltraphBootstrap.RootOriginalNativeInstallation originalRoot;final Object handler;final dev.veltraph.client.service.mc.NormalIdleMouseCarrier history;Object after;int count;
        MouseMoveBatch(MouseMoveEpisode move,Object after){first=move.scene;originalRoot=VeltraphBootstrap.originalNativeInstallationFor(first.client);handler=move.handler;history=move.history;this.after=after;count=1;}
        boolean current(){return count>0&&count<=32&&first.continuedCurrent(originalRoot,-1,false)&&first.client.mouseHandler==handler&&history.normalIdleMouseCurrent(after)&&first.client.mouseHandler.isMouseGrabbed();}
    }
    public static MouseMoveEpisode beginNativeMouseMove(Object handler,long window,double dx,double dy){try{
        if(!NativeSdlInputLineage.claimMotion(handler,window,dx,dy)||!sdkCaller(net.minecraft.client.MouseHandler.class,"onMove")||!Double.isFinite(dx)||!Double.isFinite(dy)||dx==0&&dy==0)return null;
        var s=Scene.capture();if(s==null||s.screen!=null||s.client.mouseHandler!=handler||window!=s.client.getWindow().handle()||!s.client.mouseHandler.isMouseGrabbed()||!((Object)handler instanceof dev.veltraph.client.service.mc.NormalIdleMouseCarrier history)||!((Object)handler instanceof NativeManualMouseHistoryCarrier))return null;
        var before=history.normalIdleMouseSnapshot();if(before==null||!history.normalIdleMouseCurrent(before))return null;
        var prior=MOUSE_MOVE.get();if(prior!=null){prior.invalid=true;queuedMoves=null;}var actual=new MouseMoveEpisode(s,handler,history,before,prior);MOUSE_MOVE.set(actual);return actual;
    }catch(RuntimeException|Error unavailable){queuedMoves=null;return null;}}
    public static void finishNativeMouseMove(MouseMoveEpisode actual,boolean normal){if(actual==null)return;try{
        if(MOUSE_MOVE.get()!=actual||actual.invalid||!normal||!actual.scene.current(-1,false)||!actual.scene.client.mouseHandler.isMouseGrabbed()){queuedMoves=null;return;}
        var after=actual.history.normalIdleMouseSnapshot();if(after==null||!actual.history.normalIdleMouseCurrent(after)||!((NativeManualMouseHistoryCarrier)actual.handler).nativeManualMouseAdvancedOnce(actual.beforeMouse,after)){queuedMoves=null;return;}
        if(!NativeSdlInputLineage.deferManualCompletion(()->{
            if(actual.invalid||!actual.scene.current(-1,false)||!actual.history.normalIdleMouseCurrent(after)){queuedMoves=null;return;}
            var batch=queuedMoves;if(batch==null)queuedMoves=new MouseMoveBatch(actual,after);
            else if(batch.handler!=actual.handler||batch.count>=32||!batch.first.continuedCurrent(batch.originalRoot,-1,false)||!((NativeManualMouseHistoryCarrier)actual.handler).nativeManualMouseAdvancedOnce(batch.after,after)){queuedMoves=null;return;}
            else{batch.after=after;batch.count++;}
        })){actual.invalid=true;queuedMoves=null;}
    }catch(RuntimeException|Error unavailable){queuedMoves=null;}finally{if(MOUSE_MOVE.get()==actual)MOUSE_MOVE.set(actual.prior);}}
    public static final class PoseEpisode {
        private final Scene scene;private final MouseMoveBatch moves;private final dev.veltraph.client.service.mc.RootNativeSdlBoundaryPhase.OriginalMouseAccumulationCall rootMouse;private final NoOwnerInputHandout stationaryBefore;private final NativePlayerBirthProof.PendingBirthPoseObservation pendingBirthBefore;private final NativeStationaryPoseArithmetic.Result stationaryExpected;private int stationaryOpcode;private final PoseEpisode prior;private final NativeOpcodeReturnTrace trace=new NativeOpcodeReturnTrace(List.of(PITCH_SET,YAW_SET,PITCH_SET));private NativePoseAttempt activeOpcode;private boolean invalid;
        private PoseEpisode(Scene scene,MouseMoveBatch moves,NoOwnerInputHandout stationaryBefore,NativePlayerBirthProof.PendingBirthPoseObservation pending,NativeStationaryPoseArithmetic.Result expected,PoseEpisode prior,dev.veltraph.client.service.mc.RootNativeSdlBoundaryPhase.OriginalMouseAccumulationCall rootMouse){this.scene=scene;this.moves=moves;this.rootMouse=rootMouse;this.stationaryBefore=stationaryBefore;pendingBirthBefore=pending;stationaryExpected=expected;this.prior=prior;invalid=prior!=null;}
    }
    /** Opcode pairing independent of a Root seed. Queued onMove correlation is an additional pending dependency. */
    public static PoseEpisode beginNativeTurn(Entity actual,double dx,double dy){
        try{var inner=NativeMouseInvocationPhase.claimActualEntityTurn(actual,dx,dy);
            if(inner==null)return null;var originalClient=Minecraft.getInstance();
            var rootMouse=dev.veltraph.client.service.mc.RootNativeGameplayTickPhase.claimOriginalMouseAccumulation(originalClient,originalClient.mouseHandler);
            if(rootMouse==null||rootMouse.clientIdentity()!=originalClient||rootMouse.handlerIdentity()!=originalClient.mouseHandler||!rootMouse.current()
                ||!sdkCaller(net.minecraft.client.MouseHandler.class,"turnPlayer")||!sdkCaller(net.minecraft.client.MouseHandler.class,"handleAccumulatedMovement")||!Double.isFinite(dx)||!Double.isFinite(dy))return null;
            var s=Scene.capture();if(s==null||s.player!=actual||s.screen!=null||!s.client.mouseHandler.isMouseGrabbed())return null;
            var moves=queuedMoves;queuedMoves=null;NoOwnerInputHandout stationary=null;NativePlayerBirthProof.PendingBirthPoseObservation pending=null;NativeStationaryPoseArithmetic.Result expected=null;
            if(dx==0&&dy==0){stationary=canonicalNoOwner;
                if(stationary!=null&&stationary.current(s.client)){if(stationary.scene.poseCarrier!=s.poseCarrier||!s.poseCarrier.ownedPoseHistoryAdvanced(stationary.poseFact,s.poseHistory,List.of(),List.of()))return null;expected=NativeStationaryPoseArithmetic.calculate(stationary.yaw,stationary.pitch,dx,dy);}
                else{stationary=null;pending=NativePlayerBirthProof.beginPendingStationary(s.client,actual);if(pending==null||!pending.currentBefore(s.client)||pending.poseCarrier()!=s.poseCarrier||!s.poseCarrier.ownedPoseHistoryAdvanced(pending.beforePoseSnapshot(),s.poseHistory,List.of(),List.of()))return null;
                    for(int i=0;i<9;i++)if(pending.bindingSnapshot(s.keys[i])!=s.bindingHistory[i]||!s.carriers[i].normalIdleKeyAdvanced(pending.keySnapshot(s.keys[i]),s.histories[i],List.of())||pending.held(s.keys[i])!=s.held[i]||!pending.binding(s.keys[i]).equals(s.bindings[i]))return null;
                    expected=NativeStationaryPoseArithmetic.calculate(pending.yaw(),pending.pitch(),dx,dy);}
                if(expected==null)return null;moves=null;}
            else if(moves==null||!moves.current())return null;
            var prior=POSE.get();if(prior!=null)prior.invalid=true;var ep=new PoseEpisode(s,moves,stationary,pending,expected,prior,rootMouse);POSE.set(ep);return ep;}catch(RuntimeException|Error unavailable){return null;}}
    /** Private source-normal fact for one selected partial birth; it never exposes a NoOwner handout or public movement origin. */
    public static final class PendingStationaryReturn {
        private final Scene before;private final NativePlayerBirthProof.PendingBirthPoseObservation pending;private final Object after;private final List<Object> yawCalls,pitchCalls;private final NativeStationaryPoseArithmetic.Result expected;
        private PendingStationaryReturn(Scene s,NativePlayerBirthProof.PendingBirthPoseObservation p,Object after,List<Object> yaw,List<Object> pitch,NativeStationaryPoseArithmetic.Result expected){before=s;pending=p;this.after=after;yawCalls=List.copyOf(yaw);pitchCalls=List.copyOf(pitch);this.expected=expected;}
        public boolean matchesBefore(Object actual){return actual==pending;}
        public boolean current(Minecraft client){try{if(client!=before.client||!before.current(-1,true)||after==null||!before.poseCarrier.ownedPoseHistoryCurrent(after)||Float.floatToRawIntBits(client.player.getYRot())!=Float.floatToRawIntBits(expected.finalYaw())||Float.floatToRawIntBits(client.player.getXRot())!=Float.floatToRawIntBits(expected.finalPitch()))return false;for(int i=0;i<9;i++)if(pending.bindingSnapshot(before.keys[i])!=before.bindingHistory[i]||!before.carriers[i].normalIdleKeyCurrent(pending.keySnapshot(before.keys[i]))||before.keys[i].isDown()!=pending.held(before.keys[i]))return false;return true;}catch(RuntimeException|Error unavailable){return false;}}
        public Object afterPoseSnapshot(OwnedPoseHistoryCarrier carrier){return carrier==before.poseCarrier?after:null;}
        public boolean advancedFrom(OwnedPoseHistoryCarrier carrier,Object old){return carrier==before.poseCarrier&&old==pending.beforePoseSnapshot()&&yawCalls.size()==1&&pitchCalls.size()==2&&carrier.ownedPoseHistoryAdvanced(old,after,yawCalls,pitchCalls);}
        public float yaw(){return expected.finalYaw();}public float pitch(){return expected.finalPitch();}
    }
    public static final class NativePoseAttempt {private final PoseEpisode source;private final NativeOpcodeReturnTrace.Attempt attempt;private boolean fieldReturned;
        private NativePoseAttempt(PoseEpisode source,NativeOpcodeReturnTrace.Attempt attempt){this.source=source;this.attempt=attempt;}}
    public static NativePoseAttempt beginNativePoseOpcode(Entity actual,boolean yaw,float value){var ep=POSE.get();if(ep==null)return null;
        try{if(!sdkCaller(Entity.class,"turn")||ep.scene.player!=actual||!Float.isFinite(value)){ep.invalid=true;return null;}if(ep.activeOpcode!=null){ep.invalid=true;return null;}if(ep.stationaryExpected!=null){int step=ep.stationaryOpcode++;if(step>2||yaw!=(step==1)){ep.invalid=true;return null;}float expected=step==0?ep.stationaryExpected.firstPitchArgument():step==1?ep.stationaryExpected.yawArgument():ep.stationaryExpected.secondPitchArgument();if(Float.floatToRawIntBits(value)!=Float.floatToRawIntBits(expected)){ep.invalid=true;return null;}}var a=ep.trace.begin(yaw?YAW_SET:PITCH_SET,actual,Float.floatToIntBits(value));if(a==null)return null;var result=new NativePoseAttempt(ep,a);ep.activeOpcode=result;return result;}catch(RuntimeException|Error unavailable){ep.invalid=true;return null;}}
    /** Existing mod-source claim remains first. This only looks up/claims an already pending actual SDK token. */
    public static Object claimNativePoseSetter(Entity actual,boolean yaw,float value){var ep=POSE.get();if(ep==null)return null;
        try{return ep.trace.claim(yaw?YAW_SET:PITCH_SET,actual,Float.floatToIntBits(value));}catch(RuntimeException|Error unavailable){ep.invalid=true;return null;}}
    public static void finishNativePoseOpcode(NativePoseAttempt exact,boolean normal){if(exact==null)return;try{if(POSE.get()!=exact.source||exact.source.activeOpcode!=exact){exact.source.invalid=true;return;}exact.source.trace.normalReturn(exact.attempt,normal&&exact.fieldReturned&&!exact.source.invalid);}catch(RuntimeException|Error unavailable){exact.source.invalid=true;}finally{if(exact.source.activeOpcode==exact)exact.source.activeOpcode=null;}}
    public static void finishNativeTurn(PoseEpisode exact,boolean normal){if(exact==null)return;try{
        var s=exact.scene;if(POSE.get()!=exact||exact.invalid||exact.rootMouse.clientIdentity()!=s.client||exact.rootMouse.handlerIdentity()!=s.client.mouseHandler||!exact.rootMouse.current()||!exact.trace.finish(normal)||!s.current(-1,true)||exact.stationaryExpected==null&&(exact.moves==null||!exact.moves.history.normalIdleMouseCurrent(exact.moves.after))){lastPose=null;return;}
        var after=s.poseCarrier.ownedPoseHistorySnapshot();var yaw=exact.trace.returned(YAW_SET);var pitch=exact.trace.returned(PITCH_SET);
        if(after==null||yaw.size()!=1||pitch.size()!=2||!s.poseCarrier.ownedPoseHistoryAdvanced(s.poseHistory,after,yaw,pitch)||!s.poseCarrier.ownedPoseHistoryCurrent(after)){lastPose=null;return;}
        if(exact.pendingBirthBefore!=null){
            lastPose=null;var pending=exact.pendingBirthBefore;if(exact.stationaryExpected==null||exact.stationaryOpcode!=3||Float.floatToRawIntBits(s.client.player.getYRot())!=Float.floatToRawIntBits(exact.stationaryExpected.finalYaw())||Float.floatToRawIntBits(s.client.player.getXRot())!=Float.floatToRawIntBits(exact.stationaryExpected.finalPitch())||!s.poseCarrier.ownedPoseHistoryAdvanced(pending.beforePoseSnapshot(),after,yaw,pitch))return;
            var returned=new PendingStationaryReturn(s,pending,after,yaw,pitch,exact.stationaryExpected);NativePlayerBirthProof.finishPendingStationary(pending,returned);return;
        }
        if(exact.stationaryBefore!=null){
            // A real stationary SDK turn may advance its setters' histories. Continue only an already known chain; do not expose a new no-op origin.
            lastPose=null;var old=exact.stationaryBefore;
            if(canonicalNoOwner!=old||!old.installation.current()||!old.scene.continuedHeader(old.originalRoot)
                    ||exact.stationaryExpected==null||exact.stationaryOpcode!=3||Float.floatToRawIntBits(s.client.player.getYRot())!=Float.floatToRawIntBits(exact.stationaryExpected.finalYaw())||Float.floatToRawIntBits(s.client.player.getXRot())!=Float.floatToRawIntBits(exact.stationaryExpected.finalPitch())
                    ||!s.poseCarrier.ownedPoseHistoryAdvanced(old.poseFact,after,yaw,pitch))return;
            for(int i=0;i<9;i++)if(s.keys[i]!=old.scene.keys[i]||s.bindingHistory[i]!=old.scene.bindingHistory[i]||!old.scene.carriers[i].normalIdleKeyCurrent(old.keyFacts[i])||s.keys[i].isDown()!=old.heldFacts[i])return;
            canonicalNoOwner=new NoOwnerInputHandout(s,old.birth,old.installation,old.keyFacts,old.heldFacts,old.bindingFacts,after,exact.stationaryExpected.finalYaw(),exact.stationaryExpected.finalPitch(),old,null);return;
        }
        lastPose=new NativeOpcodeReturn(s,-1,s.histories,s.held,after,yaw,pitch,"MouseHandler.handleAccumulatedMovement→turnPlayer→LocalPlayer.turn→Entity.turn(1yaw,2pitch)");appendKnownReturn(lastPose);
    }catch(RuntimeException|Error unavailable){lastPose=null;}finally{if(POSE.get()==exact)POSE.set(exact.prior);}}
}
