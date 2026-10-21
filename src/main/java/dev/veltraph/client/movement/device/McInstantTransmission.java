package dev.veltraph.client.movement.device;

import dev.veltraph.client.combat.heal.mc.McCapturedHudVitals;
import dev.veltraph.client.config.ConfigService;
import dev.veltraph.client.navigation.mc.McNavigator;
import dev.veltraph.client.service.FarmServices;
import dev.veltraph.client.service.mc.McOwnedRotation;
import dev.veltraph.client.service.mc.McRotationService;
import dev.veltraph.client.world.Vec3;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import java.util.*;

/** Shared one-shot device executor. Caller owns the existing Root lease; this installs no input owner. */
public final class McInstantTransmission {
    /** Must check registered/enabled parent, lock, input owner, current policy store/revisions and journey token. */
    public interface LeaseFence { boolean current(Object parent,Object player,Object world,Object connection,long cameraEpoch); Object policy(); long revision(); long journey(); default CameraCustodyAdapter cameraAdapter(){return null;} }
    /** A view of the caller's same concrete Legacy leg; no fresh source receipt may be adopted. */
    public interface CameraCustodyAdapter {
        McRotationService.CleanupReceipt queueOwnedSource(float yaw,float pitch,Runnable completed);
        McRotationService.CleanupReceipt refreshOwnedSource(McRotationService.CleanupReceipt exactHanded);
        /** Optional notification after this executor has accepted its actual refreshed camera receipt. Grants no authority. */
        default void deliverQueuedSourceCompletion(McRotationService.CleanupReceipt exactHanded){}
        boolean cancelOwnedSource(McRotationService.CleanupReceipt exactHanded);
        /** Optional same concrete Leg GUI retirement. Defaults grant no cleanup. */
        default McRotationService.GuiRequestRetirement retireGuiOwnedSource(McRotationService.CleanupReceipt exactHanded){return null;}
        default McRotationService.GuiRequestRetirement observedGuiRequestRetirement(){return null;}
        default boolean guiCleanupCurrent(McRotationService.GuiRequestRetirement exact){return false;}
        default Object guiCleanupSnapshot(McRotationService.GuiRequestRetirement exact){return null;}
        default boolean guiCleanupSnapshotCurrent(McRotationService.GuiRequestRetirement exact,Object snapshot){return false;}
        boolean current();boolean cleanupCurrent();boolean inventoryCleanupCurrent();
        boolean selectSlotOwned(int expectedCurrent,int next,java.util.function.BooleanSupplier beforeDeviceFence,java.util.function.BooleanSupplier afterDeviceFence);
        default SelectedSlotCleanup restoreSelectedSlotLoan(){return SelectedSlotCleanup.UNAVAILABLE;}
        /** Actual private start interruption only; defaults grant no slot or cleanup authority. */
        default SelectedSlotCleanup restoreStartingSlotLoan(StartCustody exact){return SelectedSlotCleanup.UNAVAILABLE;}
        Object publicationSnapshot();boolean publicationCurrent(Object exactSnapshot);
        default Object ownedUseSnapshot(){return null;}
        default boolean ownedUseSnapshotCurrent(Object exactSnapshot){return false;}
        long cameraEpoch();long requestGeneration();Object cameraAuthority();
    }
    public enum SelectedSlotCleanup { UNAVAILABLE, RESTORED, REFUSED }
    private StartCustody startCustody;
    /** Only this executor's real qualified start constructs custody before its actual adapter selection. */
    public final class StartCustody {
        private final McInstantTransmission executor=McInstantTransmission.this;
        private final CameraCustodyAdapter adapter=cameraAdapter;private final LeaseFence lease=fence;
        private final Object parent=owner,originalPlayer=player,originalWorld=world,listener=connection,store=policy;
        private final Object options=client.options,gameMode=client.gameMode,nativeInventory=client.player.getInventory(),menu=client.player.inventoryMenu;
        private final Object transport=client.getConnection().getConnection();
        private final McMovementDeviceInventory.Snapshot originalInventory=inventory;
        private final McMovementDeviceInventory.Tool originalTool=tool;
        private final Vec3 originalGoal=goal;private final long serial=journey,originalRevision=revision,originalEpoch=epoch,originalGeneration=cameraGeneration;
        private final int prior=priorSlot,selected=tool.slot();
        private final Thread thread=Thread.currentThread();
        private final DeviceStartCustodyState lifecycle=new DeviceStartCustodyState(this);
        private StartCustody(){}
        public McInstantTransmission executorIdentity(){return executor;}public CameraCustodyAdapter adapterIdentity(){return adapter;}
        public LeaseFence leaseIdentity(){return lease;}public Object parentIdentity(){return parent;}public long journeySerial(){return serial;}
        public int originalSlot(){return prior;}public int deviceSlot(){return selected;}
        public boolean matchesOriginal(McInstantTransmission exactExecutor,CameraCustodyAdapter exactAdapter,LeaseFence exactLease,
                Object exactParent,long exactJourney,int exactPrior,int exactSelected){
            return executor==exactExecutor&&adapter==exactAdapter&&lease==exactLease&&parent==exactParent&&serial==exactJourney
                &&prior==exactPrior&&selected==exactSelected&&startCustody==this;
        }
        /** Raw original native facts only. Actual retained loan/source/pending-disable must independently authorize cleanup. */
        public boolean nativeStructuralCurrent(){
            return lifecycle.cleanupInFlight(this)&&state==State.CANCELLED&&startCustody==this&&Thread.currentThread()==thread&&client.isSameThread()
                &&cameraAdapter==adapter&&fence==lease&&owner==parent&&player==originalPlayer&&world==originalWorld&&connection==listener
                &&policy==store&&revision==originalRevision&&journey==serial&&goal==originalGoal&&inventory==originalInventory&&tool==originalTool
                &&epoch==originalEpoch&&cameraGeneration==originalGeneration&&priorSlot==prior&&tool.slot()==selected
                &&client.player==originalPlayer&&client.level==originalWorld&&client.getConnection()==listener&&client.getConnection()!=null
                &&client.getConnection().getConnection()==transport&&client.options==options&&client.gameMode==gameMode
                &&client.player.getInventory()==nativeInventory&&client.player.inventoryMenu==menu&&client.player.containerMenu==menu
                &&client.player.inventoryMenu.getCarried().isEmpty()&&client.player.level()==client.level
                &&McOwnedRotation.epoch(client)==originalEpoch&&services.rotation() instanceof McRotationService rotation
                &&rotation.requestGeneration()==originalGeneration&&cameraReceipt==null&&operation==null&&!aimRequested&&!dispatched;
        }
        private void interruptAndClean(){
            if(!lifecycle.interrupt(this))return;
            state=State.CANCELLED;reason="Cancelled during actual device start";
            if(!lifecycle.beginCleanup(this))return;
            try{if(nativeStructuralCurrent())adapter.restoreStartingSlotLoan(this);}
            finally{lifecycle.endCleanup(this);}
        }
    }
    public enum State { IDLE, AIMING, WAITING, ARRIVED, FAILED, CANCELLED }
    private static final Object TELEPORT_DOMAIN=new Object();
    private final Minecraft client;private final FarmServices services;private final McNavigator terrain;
    private State state=State.IDLE;private Object owner,player,world,connection,policy,cameraOwner;private LeaseFence fence;private CameraCustodyAdapter cameraAdapter;private TerminalCleanupReceipt terminalCleanup;private GuiCleanupReceipt guiCleanup;
    private long revision,journey,epoch,started,lastNow,deadline;private int priorSlot;private Vec3 goal;
    private McMovementDeviceInventory.Snapshot inventory;private McMovementDeviceInventory.Tool tool;
    private MovementDeviceEvidence.Capability capability;private DeviceUseReceipt operation;private Object nonce;
    private DeviceUseReceipt.Applied acceptedReceipt;
    private McRotationService.CleanupReceipt cameraReceipt;private boolean aimRequested,rotationCompleted;private long cameraGeneration=-1;
    private OwnedUseAttempt ownedUseAttempt;
    private String cleanupDiagnostic="";
    public String cleanupDiagnostic(){return cleanupDiagnostic;}
    private String reason="Idle";private int stable;private boolean dispatched;private Runnable cancelIntent=()->{};
    public McInstantTransmission(Minecraft client,FarmServices services,ConfigService config){this.client=Objects.requireNonNull(client);this.services=Objects.requireNonNull(services);terrain=new McNavigator(client,services.input(),config);}
    public record Probe(McMovementDeviceInventory.Snapshot inventory,McMovementDeviceInventory.Tool tool,MovementDeviceEvidence.Capability capability,Vec3 goal,long manaSerial,long manaReceivedAt) {}
    /** Non-authorizing discovery for the shared mode policy. Dispatch rechecks every native predicate. */
    public static Optional<Probe> probe(Minecraft c,FarmServices services,ConfigService config,Vec3 goal,long now){
        if(c==null||!c.isSameThread()||c.player==null||c.level==null||c.getConnection()==null||!c.player.isAlive()||!c.player.onGround()||c.player.isPassenger()||c.player.isShiftKeyDown()||c.gui.screen()!=null||!finite(goal))return Optional.empty();
        var saved=McMovementDeviceInventory.read(c);if(saved.isEmpty())return Optional.empty();
        McMovementDeviceInventory.Tool selected=null;MovementDeviceEvidence.Capability ability=null;
        for(var t:saved.get().tools())for(var a:t.capabilities())if(a.kind()==MovementDeviceEvidence.Kind.INSTANT_TRANSMISSION&&!a.sneak()&&!a.causesOtherActions()){
            if(selected!=null&&!selected.identity().equals(t.identity()))return Optional.empty();selected=t;ability=a;
        }
        if(selected==null)return Optional.empty();var p=c.player.position();var end=new net.minecraft.world.phys.Vec3(goal.x(),goal.y(),goal.z());
        if(Math.abs(p.distanceTo(end)-ability.range())>.15||ability.range()<=0||!new McNavigator(c,services.input(),config).standingClear(goal))return Optional.empty();
        var eye=c.player.getEyePosition();var rayEnd=eye.add(end.subtract(p));
        for(int i=0;i<=32;i++){double t=i/32d;int x=(int)Math.floor(eye.x+(rayEnd.x-eye.x)*t),z=(int)Math.floor(eye.z+(rayEnd.z-eye.z)*t);if(!c.level.getChunkSource().hasChunk(x>>4,z>>4))return Optional.empty();}
        if(c.level.clip(new ClipContext(eye,rayEnd,ClipContext.Block.COLLIDER,ClipContext.Fluid.ANY,c.player)).getType()!=HitResult.Type.MISS)return Optional.empty();
        var mana=McCapturedHudVitals.current(c,now);if(mana.isEmpty()||mana.get().fields().currentMana()<ability.manaCost())return Optional.empty();
        return Optional.of(new Probe(saved.get(),selected,ability,goal,mana.get().serial(),mana.get().receivedAt()));
    }
    public boolean start(Object parent,LeaseFence lease,Vec3 destination,long now,long timeoutMillis){
        thread();if(state!=State.IDLE||startCustody!=null)return false;
        if(parent==null||lease==null||!finite(destination)||now<0||timeoutMillis<500||timeoutMillis>10000||Long.MAX_VALUE-now<timeoutMillis)return false;
        owner=parent;fence=lease;cameraAdapter=lease.cameraAdapter();cameraOwner=cameraAdapter==null?parent:cameraAdapter.cameraAuthority();player=client.player;world=client.level;connection=client.getConnection();epoch=McOwnedRotation.epoch(client);
        policy=lease.policy();revision=lease.revision();journey=lease.journey();goal=destination;started=lastNow=now;deadline=now+timeoutMillis;
        if(services.rotation() instanceof McRotationService rotation)cameraGeneration=rotation.requestGeneration();else return fail("Receipt-aware native rotation required");
        if(!custody()||!lease.current(owner,player,world,connection,epoch)||policy==null||revision<0||journey<1)return fail("Movement lease unavailable");
        var found=McMovementDeviceInventory.read(client);if(found.isEmpty())return fail("Complete persistent inventory unavailable");inventory=found.get();priorSlot=inventory.selectedSlot();
        var candidates=new ArrayList<McMovementDeviceInventory.Tool>();
        for(var t:inventory.tools())if(t.capabilities().stream().anyMatch(c->c.kind()==MovementDeviceEvidence.Kind.INSTANT_TRANSMISSION&&!c.sneak()&&!c.causesOtherActions()))candidates.add(t);
        if(candidates.isEmpty())return fail("No qualified Instant Transmission hotbar item");
        var prototype=candidates.getFirst().identity();if(candidates.stream().anyMatch(t->!t.identity().equals(prototype)))return fail("Ambiguous device variants");
        tool=candidates.getFirst();capability=tool.capabilities().stream().filter(c->c.kind()==MovementDeviceEvidence.Kind.INSTANT_TRANSMISSION).findFirst().orElseThrow();
        double distance=position().distanceTo(goal);
        if(capability.range()<=0||Math.abs(distance-capability.range())>.15||!geometry())return fail("Destination requires qualified forward range and loaded supported geometry");
        var mana=McCapturedHudVitals.current(client,now);
        if(mana.isEmpty()||mana.get().fields().currentMana()<capability.manaCost())return fail("Fresh received mana unavailable or insufficient");
        if(!guard(now))return fail("Lease changed during device admission");
        if(cameraAdapter==null){
            services.inventory().selectSlot(tool.slot());
            if(!guard(now)||services.inventory().selectedSlot()!=tool.slot())return fail("Device selection did not preserve custody");
            state=State.AIMING;reason="Aiming at qualified destination";return true;
        }
        var actualStart=new StartCustody();startCustody=actualStart;
        try {
            boolean selected=cameraAdapter.selectSlotOwned(priorSlot,tool.slot(),()->slotPublicationFence(priorSlot,false),()->slotPublicationFence(tool.slot(),false));
            if(!actualStart.lifecycle.selectionReturned(actualStart))return false;
            if(!selected)return fail("Scoped device selection refused");
            boolean preserved=guard(now);
            if(preserved)preserved=services.inventory().selectedSlot()==tool.slot();
            if(!actualStart.lifecycle.open(actualStart))return false;
            if(!preserved)return fail("Device selection did not preserve custody");
            if(state!=State.IDLE||startCustody!=actualStart||!actualStart.lifecycle.finish(actualStart))return false;
            state=State.AIMING;reason="Aiming at qualified destination";return true;
        } finally {actualStart.lifecycle.close(actualStart);if(startCustody==actualStart)startCustody=null;}
    }
    public State tick(long now){
        thread();if(state!=State.AIMING&&state!=State.WAITING)return state;
        if(!refreshCameraReceipt())return terminate(State.FAILED,"Native camera request publication receipt unavailable");
        if(cameraAdapter!=null)cameraAdapter.deliverQueuedSourceCompletion(cameraReceipt);
        if(!guard(now))return terminate(State.FAILED,"Movement scope or inventory changed");
        if(now>=deadline)return terminate(State.FAILED,"Original device deadline elapsed; no automatic retry");
        if(services.inventory().selectedSlot()!=tool.slot())return terminate(State.FAILED,"Selected slot changed");
        if(state==State.WAITING){
            if(NativeDeviceTeleportReceipt.rejected(operation.issue()))return terminate(State.FAILED,"Native transport receipt rejected");
            var nativeReceipt=NativeDeviceTeleportReceipt.applied(operation.issue());var ctx=context();
            if(nativeReceipt!=null&&nativeReceipt.position()!=null&&nativeReceipt.serial()>operation.issue().baseline()){
                // Native signal is after-applied. This proves scoped arrival, not mana spending or ability causation.
                var r=nativeReceipt;
                if(position().distanceTo(nativeReceipt.position())<=.05&&Math.abs(position().y()-goal.y())<.08&&client.player.onGround()&&Math.hypot(client.player.getDeltaMovement().x,client.player.getDeltaMovement().z)<.08&&terrain.standingClear(position())&&guard(now)&&operation.observe(ctx,r,now)){acceptedReceipt=r;return terminate(State.ARRIVED,"Applied server position confirms destination");}
            }
            if(!operation.validate(ctx,now))return terminate(State.FAILED,"Device receipt revoked or timed out");return state;
        }
        if(!geometry()||!guard(now))return terminate(State.FAILED,"Destination geometry changed");
        var delta=new net.minecraft.world.phys.Vec3(goal.x(),goal.y(),goal.z()).subtract(client.player.position());
        float yaw=(float)Math.toDegrees(Math.atan2(-delta.x,delta.z)),pitch=(float)-Math.toDegrees(Math.atan2(delta.y,Math.hypot(delta.x,delta.z)));
        if(!aimRequested){
            if(!guard(now)||!(services.rotation() instanceof McRotationService rotation))return terminate(State.FAILED,"Lease revoked before camera publication");
            Runnable completed=()->{if(refreshCameraReceipt()&&cleanupIdentity()&&cameraCurrent())rotationCompleted=true;};
            if(cameraAdapter==null){rotation.rotateTo(yaw,pitch,completed);cameraReceipt=rotation.cleanupReceipt(cameraOwner);}
            else cameraReceipt=cameraAdapter.queueOwnedSource(yaw,pitch,completed);
            aimRequested=true;cameraGeneration=rotation.requestGeneration();
            if(cameraReceipt==null||cameraReceipt.service()!=rotation||cameraReceipt.generation()!=cameraGeneration||cameraReceipt.authority()!=cameraOwner
                    ||cameraReceipt.player()!=player||cameraReceipt.world()!=world||cameraReceipt.connection()!=connection||!rotation.currentRequest(cameraReceipt))return terminate(State.FAILED,"Native camera request receipt missing");
            if(!custody()||fence.policy()!=policy||fence.revision()!=revision||fence.journey()!=journey)return terminate(State.FAILED,"Context changed during camera publication");
            epoch=cameraReceipt.epoch();if(!guard(now))return terminate(State.FAILED,"Camera publication lost custody");return state;
        }
        if(!rotationCompleted||Math.abs(wrap(client.player.getYRot()-yaw))>1||Math.abs(client.player.getXRot()-pitch)>1){stable=0;return state;}
        if(++stable<2)return state;
        var mana=McCapturedHudVitals.current(client,now);
        if(client.player.isShiftKeyDown()||mana.isEmpty()||mana.get().fields().currentMana()<capability.manaCost()||!geometry()||!guard(now))return terminate(State.FAILED,"Fresh mana, geometry or lease unavailable before use");
        long baseline=NativeDeviceTeleportReceipt.baseline(client);nonce=new Object();operation=new DeviceUseReceipt();
        long issuedAt=services.clock().nowMillis();if(issuedAt<now||issuedAt>=deadline)return terminate(State.FAILED,"Use clock outside original deadline");
        var issue=new DeviceUseReceipt.Issue(context(),TELEPORT_DOMAIN,nonce,baseline,issuedAt,deadline,goal,.30);
        if(!operation.arm(issue)||!guard(issuedAt)||!geometry()||!guard(issuedAt))return terminate(State.FAILED,"Final dispatch fence changed");
        // Only ordinary vanilla use; no position setters, packet spoofing or command transport.
        state=State.WAITING;reason="Waiting for actual use submission and applied native position receipt";
        if(!NativeDeviceTeleportReceipt.arm(client,issue,()->guard(System.currentTimeMillis())))return terminate(State.FAILED,"Native transport observation already leased");
        cancelIntent=dev.veltraph.client.observation.unexpected.UnexpectedActions.expectOwnedTeleport(client,goal,.30,deadline-issuedAt,()->state==State.WAITING&&custody()&&McOwnedRotation.epoch(client)==epoch&&fence.policy()==policy&&fence.revision()==revision&&fence.journey()==journey&&fence.current(owner,player,world,connection,epoch));
        var attempt=captureOwnedUseAttempt(issue);if(attempt==null)return terminate(State.FAILED,"Owned native use custody unavailable; no retry");
        NativeDeviceTeleportReceipt.openSubmissionWindow(issue);ownedUseAttempt=attempt;
        if(!NativeOwnedDeviceUseBoundary.openSource(attempt)){NativeDeviceTeleportReceipt.closeSubmissionWindow(issue);return terminate(State.FAILED,"Owned native use entry unavailable; no retry");}
        try{client.gameMode.useItem(client.player,InteractionHand.MAIN_HAND);}catch(RuntimeException failure){terminate(State.FAILED,"Native use failed; no retry");throw failure;}
        finally{NativeOwnedDeviceUseBoundary.closeSource(attempt);NativeDeviceTeleportReceipt.closeSubmissionWindow(issue);}
        NativeDeviceTeleportReceipt.returnedNormally(issue);
        dispatched=NativeDeviceTeleportReceipt.submitted(issue);
        if(!dispatched)return terminate(State.FAILED,"No qualified ordinary use packet submission; no retry");
        return state;
    }
    /** Actual dispatcher-private capture. A journal window or public Issue cannot construct this attempt. */
    public final class OwnedUseAttempt {
        private final DeviceUseReceipt.Issue issue;private final OwnedUseNativeSnapshot snapshot;private final NativeDeviceTeleportReceipt.SourceSession observer;
        private Object nativeInvocation;private final OwnedUseCommitState commit=new OwnedUseCommitState();
        private OwnedUseAttempt(DeviceUseReceipt.Issue issue,OwnedUseNativeSnapshot snapshot,NativeDeviceTeleportReceipt.SourceSession observer){this.issue=issue;this.snapshot=snapshot;this.observer=observer;}
        Minecraft client(){return client;}DeviceUseReceipt.Issue issue(){return issue;}
        net.minecraft.client.multiplayer.MultiPlayerGameMode originalMode(){return snapshot.gameMode;}Object actualNativeInvocation(){return nativeInvocation;}
        boolean openable(){return snapshot.current()&&commit.ready()&&state==State.WAITING&&operation!=null&&operation.issue()==issue&&NativeDeviceTeleportReceipt.baseline(client)==issue.baseline();}
        boolean claim(net.minecraft.client.multiplayer.MultiPlayerGameMode mode,Object actual,net.minecraft.world.entity.player.Player player,InteractionHand hand){
            if(!commit.ready()||mode!=snapshot.gameMode||player!=McInstantTransmission.this.player||hand!=InteractionHand.MAIN_HAND)return false;
            if(!commit.claim())return false;nativeInvocation=actual;return true;
        }
        void close(){commit.close();}
        boolean beforeLocal(Object actual,net.minecraft.world.item.ItemStack stack,net.minecraft.world.level.Level level,net.minecraft.world.entity.player.Player player,InteractionHand hand){
            if(actual!=nativeInvocation||level!=world||player!=McInstantTransmission.this.player||hand!=InteractionHand.MAIN_HAND||client.player==null||client.player!=McInstantTransmission.this.player||stack!=client.player.getItemInHand(hand))return refuse();
            boolean live=guard(System.currentTimeMillis());
            return commit.localResult(live&&currentNative(actual)&&observer.currentOpen(issue));
        }
        boolean beforeSend(Object actual,net.minecraft.network.protocol.game.ServerboundUseItemPacket packet,net.minecraft.network.Connection transport,NativeDeviceTeleportReceipt.SendInvocation observed){
            // Passive freshness has already returned; this path invokes no caller/provider getter.
            return commit.reserve(()->observer.matches(observed,issue,packet,transport)
                &&transport==snapshot.transport&&packet.hand()==InteractionHand.MAIN_HAND&&packet.yRot()==snapshot.yaw&&packet.xRot()==snapshot.pitch&&currentNative(actual));
        }
        private boolean currentNative(Object actual){
            if(commit.refusedOrClosed()||nativeInvocation!=actual||ownedUseAttempt!=this||!NativeOwnedDeviceUseBoundary.sourceCurrent(this)
                ||state!=State.WAITING||operation==null||operation.issue()!=issue||cameraAdapter!=snapshot.adapter||fence!=snapshot.originalFence
                ||policy!=snapshot.store||revision!=snapshot.revision||journey!=snapshot.journey||epoch!=snapshot.epoch||cameraGeneration!=snapshot.generation)return false;
            if(!(snapshot.gameMode instanceof OwnedNativeUseCarrier carrier)||!carrier.ownedUseInvocationCurrent(actual)||carrier.ownedUseAttempt(actual)!=this)return false;
            long now=System.currentTimeMillis();if(!OwnedUseTimeWindow.permits(now,issue.issuedAt(),issue.deadline(),snapshot.journeyDeadline)||NativeDeviceTeleportReceipt.baseline(client)!=issue.baseline()||!snapshot.current())return false;
            // Resolve the actual opaque Leg final leaf before the last independent native/private checks.
            if(snapshot.adapter==null||!snapshot.adapter.ownedUseSnapshotCurrent(snapshot.legSnapshot)||!snapshot.authenticSourceCurrent())return false;
            // After component/native-source checks, no provider or component callback may precede submission.
            now=System.currentTimeMillis();return OwnedUseTimeWindow.permits(now,issue.issuedAt(),issue.deadline(),snapshot.journeyDeadline)&&nativeInvocation==actual&&ownedUseAttempt==this
                &&NativeOwnedDeviceUseBoundary.sourceCurrent(this)&&state==State.WAITING&&operation!=null&&operation.issue()==issue
                &&cameraAdapter==snapshot.adapter&&fence==snapshot.originalFence&&policy==snapshot.store&&revision==snapshot.revision&&journey==snapshot.journey
                &&epoch==snapshot.epoch&&cameraGeneration==snapshot.generation&&carrier.ownedUseInvocationCurrent(actual)&&carrier.ownedUseAttempt(actual)==this
                &&snapshot.nativeFields()&&NativeDeviceTeleportReceipt.baseline(client)==issue.baseline();
        }
        private boolean refuse(){return commit.refuse();}
    }
    private final class OwnedUseNativeSnapshot {
        final dev.veltraph.client.core.ModuleManager root;final dev.veltraph.client.core.ModuleManager.LifecycleSnapshot lifecycle;
        final dev.veltraph.client.config.MapConfigService.StoredStateSnapshot settings;
        final dev.veltraph.client.input.ScriptingInputLock.ControlSnapshot input;final dev.veltraph.client.service.mc.NormalIdleHardwareSnapshot hardware;
        final McOwnedRotation.InstallationSnapshot installation;final CameraCustodyAdapter adapter=cameraAdapter;final LeaseFence originalFence=fence;
        final Object sourceParent=owner,sourceCamera=cameraOwner;final Vec3 sourceGoal=goal;
        final Object store=policy,originalPlayer=player,originalWorld=world,originalConnection=connection,options=client.options,menu=client.player.containerMenu;
        final net.minecraft.client.multiplayer.MultiPlayerGameMode gameMode=client.gameMode;final net.minecraft.network.Connection transport=client.getConnection().getConnection();
        final net.minecraft.world.level.GameType mode=gameMode.getPlayerMode();final long revision=McInstantTransmission.this.revision,journey=McInstantTransmission.this.journey,
            epoch=McInstantTransmission.this.epoch,generation=cameraGeneration,publication=McOwnedRotation.publicationSerial(client);
        final double x=client.player.getX(),y=client.player.getY(),z=client.player.getZ();final float yaw=client.player.getYRot(),pitch=client.player.getXRot();
        final int slot=client.player.getInventory().getSelectedSlot();final java.util.List<net.minecraft.world.item.ItemStack> items;
        final net.minecraft.client.KeyMapping[] keys;final Object[] mappings;Object legSnapshot;long journeyDeadline=-1;
        private OwnedUseNativeSnapshot(){
            root=dev.veltraph.client.VeltraphBootstrap.moduleManager();lifecycle=root==null?null:root.lifecycleSnapshot();
            settings=policy instanceof dev.veltraph.client.config.MapConfigService m?m.storedStateSnapshot():policy instanceof dev.veltraph.client.config.JsonConfigService j?j.storedStateSnapshot():null;
            input=dev.veltraph.client.input.ScriptingInputLock.controlSnapshot();hardware=dev.veltraph.client.service.mc.NormalIdleHardwareSnapshot.captureBeforeRelease(client);installation=McOwnedRotation.installationSnapshot(client);
            var copies=new java.util.ArrayList<net.minecraft.world.item.ItemStack>(36);for(int i=0;i<36;i++)copies.add(client.player.getInventory().getItem(i).copy());items=java.util.List.copyOf(copies);
            var o=client.options;keys=new net.minecraft.client.KeyMapping[]{o.keyUp,o.keyDown,o.keyLeft,o.keyRight,o.keyJump,o.keyShift,o.keySprint,o.keyAttack,o.keyUse};mappings=new Object[9];
            for(int i=0;i<9;i++)if(keys[i] instanceof OwnedUseMappedKeyCarrier carrier)mappings[i]=carrier.ownedUseMappingSnapshot();
        }
        private boolean current(){
            if(!nativeFields())return false;
            for(int i=0;i<36;i++)if(!net.minecraft.world.item.ItemStack.matches(items.get(i),client.player.getInventory().getItem(i)))return false;
            // Component equality precedes the final private/native/config/input/hardware/Leg leaves.
            return adapter!=null&&legSnapshot!=null&&adapter.ownedUseSnapshotCurrent(legSnapshot)&&authenticSourceCurrent()&&nativeFields()&&root.lifecycleCurrent(lifecycle);
        }
        /** Independently authenticates the private actual caller admission; adapter Boolean is a refusal only. */
        private boolean authenticSourceCurrent(){
            return legSnapshot!=null&&dev.veltraph.client.navigation.mc.McSharedMovementNavigator.ownedDeviceUseSnapshotCurrent(
                McInstantTransmission.this,legSnapshot,originalFence,sourceParent,sourceCamera,store,epoch,generation,journey,sourceGoal);
        }
        private boolean nativeFields(){
            if(owner!=sourceParent||cameraOwner!=sourceCamera||goal!=sourceGoal||fence!=originalFence||cameraAdapter!=adapter
                ||!client.isSameThread()||root==null||lifecycle==null||!lifecycle.known()||dev.veltraph.client.VeltraphBootstrap.moduleManager()!=root||!root.lifecycleCurrent(lifecycle)
                ||dev.veltraph.client.VeltraphBootstrap.config()!=store||settings==null||!settings.current()||input==null||!input.current()||!input.active()
                ||hardware==null||!hardware.current()||installation==null||!installation.current()||client.player!=originalPlayer||client.level!=originalWorld||client.getConnection()!=originalConnection
                ||client.options!=options||client.gameMode!=gameMode||gameMode.getClass()!=net.minecraft.client.multiplayer.MultiPlayerGameMode.class||gameMode.getPlayerMode()!=mode
                ||client.player==null||client.level==null||client.getConnection()==null||client.getConnection().getConnection()!=transport||client.getCameraEntity()!=client.player
                ||!client.player.isAlive()||client.player.isPassenger()||client.player.isScoping()||!client.player.onGround()||client.isPaused()||client.gui.screen()!=null
                ||client.level.tickRateManager().isEntityFrozen(client.player)||client.player.containerMenu!=menu||menu!=client.player.inventoryMenu||!client.player.inventoryMenu.getCarried().isEmpty()
                ||client.player.getInventory().getSelectedSlot()!=slot||slot!=tool.slot()||McOwnedRotation.epoch(client)!=epoch||McOwnedRotation.publicationSerial(client)!=publication
                ||!(services.rotation() instanceof McRotationService rotation)||rotation.requestGeneration()!=generation||client.player.getX()!=x||client.player.getY()!=y||client.player.getZ()!=z
                ||client.player.getYRot()!=yaw||client.player.getXRot()!=pitch)return false;
            var o=client.options;var actual=new net.minecraft.client.KeyMapping[]{o.keyUp,o.keyDown,o.keyLeft,o.keyRight,o.keyJump,o.keyShift,o.keySprint,o.keyAttack,o.keyUse};
            for(int i=0;i<9;i++)if(actual[i]!=keys[i]||mappings[i]==null||!(actual[i] instanceof OwnedUseMappedKeyCarrier carrier)||!carrier.ownedUseMappingCurrent(mappings[i]))return false;
            return true;
        }
    }
    private OwnedUseAttempt captureOwnedUseAttempt(DeviceUseReceipt.Issue issue){
        if(!client.isSameThread()||cameraAdapter==null||state!=State.WAITING||operation==null||operation.issue()!=issue||client.player==null||client.level==null||client.getConnection()==null||client.gameMode==null||client.options==null)return null;
        var snapshot=new OwnedUseNativeSnapshot();
        if(cameraAdapter!=null)snapshot.legSnapshot=cameraAdapter.ownedUseSnapshot();
        snapshot.journeyDeadline=dev.veltraph.client.navigation.mc.McSharedMovementNavigator.ownedDeviceUseJourneyDeadline(snapshot.legSnapshot);
        if(snapshot.journeyDeadline<0)return null;
        if(!guard(System.currentTimeMillis())||!snapshot.current())return null;
        var observer=NativeDeviceTeleportReceipt.sourceSession(issue);if(observer==null)return null;
        if(dev.veltraph.client.navigation.mc.McSharedMovementNavigator.issuedOwnedUseSource(snapshot.legSnapshot)){
            var transit=dev.veltraph.client.navigation.mc.McSharedMovementNavigator.issuedDeviceTransitForOwnedUse(snapshot.legSnapshot,this,issue);
            if(transit==null||!NativeDeviceTeleportReceipt.bindIssuedReturnTransit(issue,transit))return null;
        }
        return new OwnedUseAttempt(issue,snapshot,observer);
    }
    private boolean geometry(){
        if(!custody()||tool==null||!finite(goal))return false;
        var p=position();if(Math.abs(p.distanceTo(goal)-capability.range())>.15)return false;
        if(!terrain.standingClear(goal))return false;
        var eye=client.player.getEyePosition();var end=eye.add(goal.x()-p.x(),goal.y()-p.y(),goal.z()-p.z());
        // Fence all crossed columns before clip; unloaded columns never become an apparent clear ray.
        for(int i=0;i<=32;i++){double t=i/32d;int x=(int)Math.floor(eye.x+(end.x-eye.x)*t),z=(int)Math.floor(eye.z+(end.z-eye.z)*t);if(!client.level.getChunkSource().hasChunk(x>>4,z>>4))return false;}
        return client.level.clip(new ClipContext(eye,end,ClipContext.Block.COLLIDER,ClipContext.Fluid.ANY,client.player)).getType()==HitResult.Type.MISS;
    }
    private boolean custody(){return client.isSameThread()&&client.player!=null&&client.player==player&&client.level!=null&&client.level==world&&client.getConnection()!=null&&client.getConnection()==connection&&client.player.level()==client.level&&client.player.isAlive()&&!client.player.isPassenger()&&(state==State.WAITING||state==State.ARRIVED||client.player.onGround())&&client.gui.screen()==null&&client.gameMode!=null&&client.getCameraEntity()==client.player&&McOwnedRotation.installed(client)&&cameraOwner!=null&&McOwnedRotation.authority(client)==cameraOwner&&(cameraAdapter==null||cameraAdapter.current()&&cameraAdapter.cameraAuthority()==cameraOwner&&adapterParentCurrent());}
    private boolean adapterParentCurrent(){
        var root=dev.veltraph.client.VeltraphBootstrap.moduleManager();
        return owner instanceof dev.veltraph.client.core.Module parent&&root!=null&&root.get(parent.id()).orElse(null)==parent&&parent.isEnabled()
            &&root.inputOwner().orElse(null)==parent&&dev.veltraph.client.input.ScriptingInputLock.isActive()
            &&parent.id().equals(dev.veltraph.client.input.ScriptingInputLock.ownerModuleId());
    }
    private boolean guard(long now){if(now<lastNow)return false;lastNow=now;return custody()&&(state==State.IDLE||tool!=null&&services.inventory().selectedSlot()==tool.slot())&&cameraCurrent()&&McOwnedRotation.epoch(client)==epoch&&fence!=null&&fence.cameraAdapter()==cameraAdapter&&fence.policy()==policy&&fence.revision()==revision&&fence.journey()==journey&&fence.current(owner,player,world,connection,epoch)&&McMovementDeviceInventory.unchanged(client,inventory);}
    private boolean refreshCameraReceipt(){
        if(cameraReceipt==null)return cameraCurrent();
        if(!(services.rotation() instanceof McRotationService rotation))return false;
        var refreshed=cameraAdapter==null?rotation.refreshOwnedReceipt(cameraReceipt):cameraAdapter.refreshOwnedSource(cameraReceipt);
        if(refreshed==null||refreshed.service()!=rotation||refreshed.generation()!=cameraGeneration||refreshed.authority()!=cameraOwner
                ||refreshed.player()!=player||refreshed.world()!=world||refreshed.connection()!=connection)return false;
        boolean live=rotation.currentRequest(refreshed);
        boolean cleanupOnly=cameraAdapter!=null&&cameraAdapter.cleanupCurrent()&&rotation.ownsCleanup(refreshed,true);
        if(!live&&!cleanupOnly)return false;
        if(cameraAdapter!=null&&(cameraAdapter.cameraAuthority()!=cameraOwner||cameraAdapter.cameraEpoch()!=refreshed.epoch()||cameraAdapter.requestGeneration()!=refreshed.generation()))return false;
        if(state==State.WAITING&&refreshed.epoch()!=epoch)return false;
        cameraReceipt=refreshed;epoch=refreshed.epoch();return true;
    }
    private boolean cameraCurrent(){return services.rotation() instanceof McRotationService rotation&&rotation.requestGeneration()==cameraGeneration
        &&(cameraReceipt==null||rotation.currentRequest(cameraReceipt))&&(cameraAdapter==null||cameraAdapter.current()&&cameraAdapter.cameraAuthority()==cameraOwner&&cameraAdapter.cameraEpoch()==epoch&&cameraAdapter.requestGeneration()==cameraGeneration);}
    private boolean cameraCleanupCurrent(){return services.rotation() instanceof McRotationService rotation&&rotation.requestGeneration()==cameraGeneration
        &&(cameraReceipt==null||(cameraAdapter==null?rotation.currentRequest(cameraReceipt):rotation.ownsCleanup(cameraReceipt,true)))
        &&(cameraAdapter==null||cameraAdapter.cleanupCurrent()&&cameraAdapter.cameraAuthority()==cameraOwner&&cameraAdapter.cameraEpoch()==epoch&&cameraAdapter.requestGeneration()==cameraGeneration);}
    private boolean cleanupIdentity(){
        if(!client.isSameThread()||fence==null||fence.cameraAdapter()!=cameraAdapter||fence.policy()!=policy||fence.journey()!=journey||client.player!=player||client.level!=world||client.getConnection()!=connection||McOwnedRotation.epoch(client)!=epoch)return false;
        if(cameraAdapter==null)return McOwnedRotation.authority(client)==owner;
        var root=dev.veltraph.client.VeltraphBootstrap.moduleManager();
        return owner instanceof dev.veltraph.client.core.Module parent&&root!=null&&root.get(parent.id()).orElse(null)==parent
            &&cameraAdapter.cleanupCurrent()&&cameraAdapter.cameraAuthority()==cameraOwner
            &&(McOwnedRotation.authority(client)==cameraOwner||McOwnedRotation.authority(client)==null);
    }
    private DeviceUseReceipt.Context context(){return new DeviceUseReceipt.Context(owner,player,world,connection,policy,revision,journey,epoch);}
    private Vec3 position(){return new Vec3(client.player.getX(),client.player.getY(),client.player.getZ());}
    private boolean fail(String text){terminate(State.FAILED,text);return false;}
    private State terminate(State next,String text){state=next;reason=text;if(operation!=null)operation.revoke();cleanup();return state;}
    private void cleanup(){
        if(cameraAdapter==null){cleanupStandalone();return;}
        if(cameraReceipt!=null)refreshCameraReceipt();
        cancelIntent.run();cancelIntent=()->{};
        var raw=NativeDeviceTeleportReceipt.current(client);
        boolean freshAccepted=acceptedReceipt!=null&&raw!=null&&raw.serial()==acceptedReceipt.serial();
        if(operation!=null)NativeDeviceTeleportReceipt.retire(operation.issue());
        if(!cleanupIdentity()||!cameraCleanupCurrent()){
            if(cleanupGuiOwnedRequest()){captureCleanupDiagnostic("gui-actual-request-retired",true);return;}
            captureCleanupDiagnostic("cleanup-fence-refused",false);return;
        }
        if(!(services.rotation() instanceof McRotationService rotation))return;
        long beforeEpoch=epoch,beforeGeneration=cameraGeneration,ticket=cameraReceipt==null?-1:rotation.ownedPublicationTicket(cameraReceipt);
        boolean cancelled=false;
        if(cameraReceipt!=null){
            cancelled=cameraAdapter==null?rotation.cancelOwned(cameraReceipt,false):cameraAdapter.cancelOwnedSource(cameraReceipt);captureCleanupDiagnostic("cancel-return",cancelled);
            if(cancelled&&client.player==player&&client.level==world&&client.getConnection()==connection
                    &&(McOwnedRotation.authority(client)==cameraOwner||cameraAdapter!=null&&McOwnedRotation.authority(client)==null)){
                long afterEpoch=McOwnedRotation.epoch(client),afterGeneration=rotation.requestGeneration();
                if(afterEpoch!=beforeEpoch+1||afterGeneration!=beforeGeneration+1||ticket>0&&McOwnedRotation.publicationSerial(client)!=ticket)return;
                if(cameraAdapter!=null&&(cameraAdapter.cameraAuthority()!=cameraOwner||cameraAdapter.cameraEpoch()!=afterEpoch||cameraAdapter.requestGeneration()!=afterGeneration||!cameraAdapter.cleanupCurrent()))return;
                if(ticket<=0){
                    var slotCleanup=cameraAdapter.restoreSelectedSlotLoan();
                    if(slotCleanup==SelectedSlotCleanup.RESTORED){epoch=afterEpoch;cameraGeneration=afterGeneration;captureCleanupDiagnostic("unpublished-source-slot-restored",true);}
                    return;
                }
                epoch=afterEpoch;cameraGeneration=afterGeneration;
            }else if(cameraAdapter!=null)return;
        }
        var slotCleanup=cameraAdapter.restoreSelectedSlotLoan();
        if(slotCleanup==SelectedSlotCleanup.REFUSED)return;
        if(slotCleanup==SelectedSlotCleanup.RESTORED&&!cameraAdapter.current()){
            captureCleanupDiagnostic("cleanup-only-slot-restored",cancelled);return;
        }
        boolean gear=cameraAdapter==null||cameraAdapter.current()&&cameraAdapter.inventoryCleanupCurrent();
        if(gear&&(slotCleanup==SelectedSlotCleanup.RESTORED||inventoryRestoreFence(ticket))){
            if(slotCleanup!=SelectedSlotCleanup.RESTORED&&!cameraAdapter.selectSlotOwned(tool.slot(),priorSlot,()->slotPublicationFence(tool.slot(),true),()->slotPublicationFence(priorSlot,true)))return;
            var terminalSnapshot=captureTerminalSnapshot();if(terminalSnapshot==null)return;
            var terminalRaw=NativeDeviceTeleportReceipt.current(client);
            if(cameraAdapter!=null&&cancelled&&freshAccepted&&acceptedReceipt!=null&&operation!=null
                    &&fence.policy()==policy&&fence.revision()==revision&&fence.journey()==journey&&cleanupIdentity()
                    &&DeviceTerminalCleanupEvidence.valid(new DeviceTerminalCleanupEvidence.Input(state==State.ARRIVED,dispatched,true,cameraAdapter.current(),cameraAdapter.inventoryCleanupCurrent(),McMovementDeviceInventory.unchanged(client,inventory),
                        beforeEpoch,epoch,beforeGeneration,cameraGeneration,ticket,McOwnedRotation.publicationSerial(client),acceptedReceipt.serial(),terminalRaw==null?-1:terminalRaw.serial(),priorSlot,services.inventory().selectedSlot()))&&finalLeaseProof()&&terminalSnapshot.current())
                terminalCleanup=new TerminalCleanupReceipt(cameraAdapter,operation.issue(),acceptedReceipt,beforeEpoch,epoch,beforeGeneration,cameraGeneration,ticket,priorSlot);
        }
    }
    /** Authenticated same-Leg GUI cleanup precedes ordinary old-epoch refusal; no camera/gear operation is repeated. */
    private boolean cleanupGuiOwnedRequest(){
        if(guiCleanup!=null)return guiCleanup.current();
        if(!guiAdapterIdentity()||cameraReceipt==null||!(services.rotation() instanceof McRotationService rotation)
            ||cameraReceipt.service()!=rotation||cameraReceipt.generation()!=cameraGeneration||cameraReceipt.authority()!=cameraOwner
            ||cameraReceipt.player()!=player||cameraReceipt.world()!=world||cameraReceipt.connection()!=connection||cameraReceipt.epoch()!=epoch)return false;
        var actualGui=McOwnedRotation.guiAvailabilityRevocation(client);
        if(actualGui==null||!actualGui.matchesOriginal(cameraOwner,player,world,connection,epoch,actualGui.actualTicket()))return false;
        long originalEpoch=epoch,originalGeneration=cameraGeneration;var originalSource=cameraReceipt;
        var retired=cameraAdapter.observedGuiRequestRetirement();
        if(retired==null)retired=cameraAdapter.retireGuiOwnedSource(originalSource); // The same actual Leg is the sole service retirement caller.
        if(retired==null||!guiAdapterIdentity()||cameraReceipt!=originalSource||!guiRetirementCurrent(retired,originalSource,originalEpoch,originalGeneration))return false;
        // All callbacks and the actual private Leg leaf finish before the new cleanup receipt is constructed.
        var currentGui=retired.currentGuiProof();if(currentGui==null||!guiRetirementCurrent(retired,originalSource,originalEpoch,originalGeneration)
            ||retired.currentGuiProof()!=currentGui||!currentGui.current())return false;
        var receipt=new GuiCleanupReceipt(cameraAdapter,retired,originalSource,originalEpoch,originalGeneration);
        // Adopt only the typed actual certificate endpoint, never the current raw camera epoch.
        guiCleanup=receipt;epoch=currentGui.afterEpoch();cameraGeneration=retired.afterGeneration();return receipt.current();
    }
    private boolean guiAdapterIdentity(){
        if(!client.isSameThread()||cameraAdapter==null||fence==null||fence.cameraAdapter()!=cameraAdapter||fence.policy()!=policy||fence.journey()!=journey)return false;
        return owner instanceof dev.veltraph.client.core.Module parent&&dev.veltraph.client.VeltraphBootstrap.moduleManager()!=null
            &&dev.veltraph.client.VeltraphBootstrap.moduleManager().get(parent.id()).orElse(null)==parent
            &&dev.veltraph.client.VeltraphBootstrap.config()==policy&&client.player==player&&client.level==world&&client.getConnection()==connection;
    }
    private boolean guiRetirementCurrent(McRotationService.GuiRequestRetirement retired,McRotationService.CleanupReceipt source,long originalEpoch,long originalGeneration){
        if(!guiAdapterIdentity()||retired.originalSource()!=source||cameraAdapter.observedGuiRequestRetirement()!=retired
            ||!cameraAdapter.guiCleanupCurrent(retired))return false;
        Object guiSnapshot=cameraAdapter.guiCleanupSnapshot(retired);if(guiSnapshot==null)return false;
        var currentGui=retired.currentGuiProof();var originalGui=retired.guiProof();
        if(currentGui==null||originalGui==null||!currentGui.descendsFrom(originalGui)
            ||!currentGui.matchesOriginal(cameraOwner,player,world,connection,originalEpoch,retired.actualTicket())
            ||source.service()!=services.rotation()||source.authority()!=cameraOwner||source.player()!=player||source.world()!=world||source.connection()!=connection
            ||!DeviceGuiRequestRetirementIdentity.valid(new DeviceGuiRequestRetirementIdentity.Input(source,retired.originalSource(),originalGeneration,retired.beforeGeneration(),retired.afterGeneration(),
                originalEpoch,originalGui.beforeEpoch(),retired.beforeEpoch(),retired.afterEpoch(),originalGui.afterEpoch(),currentGui.afterEpoch(),retired.actualTicket(),currentGui.actualTicket())))return false;
        // All caller/provider reads precede this final real private/native GUI certificate and source leaf.
        return guiAdapterIdentity()&&retired.current()&&currentGui.current()&&services.rotation() instanceof McRotationService rotation
            &&rotation.requestGeneration()==retired.afterGeneration()&&McOwnedRotation.epoch(client)==currentGui.afterEpoch()
            &&McOwnedRotation.publicationSerial(client)==retired.actualTicket()&&client.player==player&&client.level==world&&client.getConnection()==connection
            &&cameraAdapter.guiCleanupSnapshotCurrent(retired,guiSnapshot);
    }
    /** Actual GUI request retirement only. This is neither arrival nor inventory/continuation permission. */
    public final class GuiCleanupReceipt {
        private final CameraCustodyAdapter adapter;private final McRotationService.GuiRequestRetirement retirement;
        private final McRotationService.CleanupReceipt source;private final long originalEpoch,originalGeneration;
        private GuiCleanupReceipt(CameraCustodyAdapter adapter,McRotationService.GuiRequestRetirement retirement,McRotationService.CleanupReceipt source,long originalEpoch,long originalGeneration){this.adapter=adapter;this.retirement=retirement;this.source=source;this.originalEpoch=originalEpoch;this.originalGeneration=originalGeneration;}
        public McRotationService.GuiRequestRetirement retirement(){return retirement;}
        public boolean current(){return guiCleanup==this&&cameraAdapter==adapter&&guiRetirementCurrent(retirement,source,originalEpoch,originalGeneration);}
    }
    public GuiCleanupReceipt guiCleanup(){return guiCleanup;}
    private boolean finalLeaseProof(){return fence!=null&&fence.cameraAdapter()==cameraAdapter&&fence.policy()==policy&&fence.revision()==revision&&fence.journey()==journey
        &&fence.current(owner,player,world,connection,epoch)&&cameraAdapter.current()&&cameraAdapter.inventoryCleanupCurrent();}
    private boolean slotPublicationFence(int expectedSlot,boolean restoring){
        if(cameraAdapter==null||!client.isSameThread()||fence.cameraAdapter()!=cameraAdapter||fence.policy()!=policy||fence.revision()!=revision||fence.journey()!=journey
            ||!cameraAdapter.current()||restoring&&!cameraAdapter.inventoryCleanupCurrent())return false;
        return client.player==player&&client.level==world&&client.getConnection()==connection&&McOwnedRotation.authority(client)==cameraOwner
            &&McOwnedRotation.epoch(client)==epoch&&services.rotation() instanceof McRotationService rotation&&rotation.requestGeneration()==cameraGeneration
            &&services.inventory().selectedSlot()==expectedSlot&&McMovementDeviceInventory.unchanged(client,inventory);
    }
    /** Snapshot is created internally before callbacks; final equality uses native copies and authentic lifecycle evidence. */
    private final class TerminalNativeSnapshot {
        private final dev.veltraph.client.core.ModuleManager root;private final dev.veltraph.client.core.ModuleManager.LifecycleSnapshot lifecycle;
        private final Object legSnapshot,options,gameMode,menu;private final long observedEpoch,generation,publication,ack;
        private final int slot;private final double x,y,z;private final float yaw,pitch;private final java.util.List<net.minecraft.world.item.ItemStack> nativeInventory;private final DeviceTerminalNativeLeaf.Frame frame;
        private TerminalNativeSnapshot(dev.veltraph.client.core.ModuleManager root,dev.veltraph.client.core.ModuleManager.LifecycleSnapshot lifecycle,Object legSnapshot){
            this.root=root;this.lifecycle=lifecycle;this.legSnapshot=legSnapshot;options=client.options;gameMode=client.gameMode;menu=client.player.containerMenu;
            observedEpoch=McOwnedRotation.epoch(client);generation=((McRotationService)services.rotation()).requestGeneration();publication=McOwnedRotation.publicationSerial(client);
            var raw=NativeDeviceTeleportReceipt.current(client);ack=raw==null?-1:raw.serial();slot=client.player.getInventory().getSelectedSlot();
            x=client.player.getX();y=client.player.getY();z=client.player.getZ();yaw=client.player.getYRot();pitch=client.player.getXRot();
            frame=new DeviceTerminalNativeLeaf.Frame(root,player,world,connection,policy,options,gameMode,menu,cameraOwner,observedEpoch,generation,publication,ack,slot,x,y,z,yaw,pitch);
            var stacks=new java.util.ArrayList<net.minecraft.world.item.ItemStack>(36);for(int i=0;i<36;i++)stacks.add(client.player.getInventory().getItem(i).copy());nativeInventory=java.util.List.copyOf(stacks);
        }
        private boolean current(){
            // Actual authority supplier is the final possible callback. Everything after it is a stored/native leaf check.
            Object actualCamera=McOwnedRotation.authority(client);
            if(!cameraAdapter.publicationCurrent(legSnapshot)||!root.lifecycleCurrent(lifecycle))return false;
            var raw=NativeDeviceTeleportReceipt.current(client);
            if(!client.isSameThread()||dev.veltraph.client.VeltraphBootstrap.moduleManager()!=root||dev.veltraph.client.VeltraphBootstrap.config()!=policy
                ||client.player!=player||client.level!=world||client.getConnection()!=connection||client.options!=options||client.gameMode!=gameMode
                ||actualCamera!=cameraOwner||client.getCameraEntity()!=player||!client.player.isAlive()||client.player.isPassenger()||client.player.isScoping()
                ||client.isPaused()||client.gui.screen()!=null||client.level.tickRateManager().isEntityFrozen(client.player)||!client.player.onGround()
                ||Math.hypot(client.player.getDeltaMovement().x,client.player.getDeltaMovement().z)>=.08||McOwnedRotation.epoch(client)!=observedEpoch
                ||((McRotationService)services.rotation()).requestGeneration()!=generation||McOwnedRotation.publicationSerial(client)!=publication
                ||raw==null||raw.serial()!=ack||acceptedReceipt==null||raw.serial()!=acceptedReceipt.serial()||client.player.containerMenu!=menu
                ||menu!=client.player.inventoryMenu||!client.player.inventoryMenu.getCarried().isEmpty()||client.player.getInventory().getSelectedSlot()!=slot
                ||client.player.getX()!=x||client.player.getY()!=y||client.player.getZ()!=z||client.player.getYRot()!=yaw||client.player.getXRot()!=pitch)return false;
            var actual=new DeviceTerminalNativeLeaf.Frame(dev.veltraph.client.VeltraphBootstrap.moduleManager(),client.player,client.level,client.getConnection(),dev.veltraph.client.VeltraphBootstrap.config(),client.options,client.gameMode,client.player.containerMenu,actualCamera,
                McOwnedRotation.epoch(client),((McRotationService)services.rotation()).requestGeneration(),McOwnedRotation.publicationSerial(client),raw.serial(),client.player.getInventory().getSelectedSlot(),client.player.getX(),client.player.getY(),client.player.getZ(),client.player.getYRot(),client.player.getXRot());
            if(!DeviceTerminalNativeLeaf.same(frame,actual))return false;
            for(int i=0;i<36;i++)if(!net.minecraft.world.item.ItemStack.matches(nativeInventory.get(i),client.player.getInventory().getItem(i)))return false;
            return root.lifecycleCurrent(lifecycle)&&cameraAdapter.publicationCurrent(legSnapshot);
        }
    }
    private TerminalNativeSnapshot captureTerminalSnapshot(){
        if(!client.isSameThread()||cameraAdapter==null||client.player!=player||client.level!=world||client.getConnection()!=connection||!(services.rotation() instanceof McRotationService))return null;
        var root=dev.veltraph.client.VeltraphBootstrap.moduleManager();var lifecycle=root==null?null:root.lifecycleSnapshot();
        if(lifecycle==null||!lifecycle.known())return null;Object legSnapshot=cameraAdapter.publicationSnapshot();if(legSnapshot==null)return null;
        var snapshot=new TerminalNativeSnapshot(root,lifecycle,legSnapshot);return root.lifecycleCurrent(lifecycle)&&cameraAdapter.publicationCurrent(legSnapshot)?snapshot:null;
    }
    private boolean inventoryRestoreFence(long ticket){
        if(cameraAdapter==null||!cleanupIdentity()||fence.revision()!=revision||!cameraAdapter.current()||!cameraAdapter.inventoryCleanupCurrent())return false;
        // All role/policy callbacks precede the final native context, generation, slot and complete component reads.
        if(client.player!=player||client.level!=world||client.getConnection()!=connection||client.getCameraEntity()!=player
            ||McOwnedRotation.authority(client)!=cameraOwner||McOwnedRotation.epoch(client)!=epoch
            ||!(services.rotation() instanceof McRotationService rotation)||rotation.requestGeneration()!=cameraGeneration
            ||McOwnedRotation.publicationSerial(client)!=ticket||tool==null)return false;
        return services.inventory().selectedSlot()==tool.slot()&&McMovementDeviceInventory.unchanged(client,inventory)
            &&client.player==player&&client.level==world&&client.getConnection()==connection&&McOwnedRotation.authority(client)==cameraOwner
            &&McOwnedRotation.epoch(client)==epoch&&rotation.requestGeneration()==cameraGeneration&&McOwnedRotation.publicationSerial(client)==ticket
            &&services.inventory().selectedSlot()==tool.slot();
    }
    private void captureCleanupDiagnostic(String phase,boolean actualCancelled){
        try{cleanupDiagnostic="phase="+phase+" actualCancel="+actualCancelled+" state="+state+" epochHeld="+epoch+" epochNative="+McOwnedRotation.epoch(client)
            +" genHeld="+cameraGeneration+" genNative="+(services.rotation() instanceof McRotationService r?r.requestGeneration():-1)
            +" serialNative="+McOwnedRotation.publicationSerial(client)+" receipt="+(cameraReceipt==null?"null":"generation="+cameraReceipt.generation()+" epoch="+cameraReceipt.epoch()+" authorityClass="+cameraReceipt.authority().getClass().getName())
            +" gui="+(client.gui.screen()==null?"null":client.gui.screen().getClass().getName());
        }catch(Throwable unavailable){cleanupDiagnostic="diagnostic unavailable";}
    }
    /** Original standalone path: no delegated camera or handoff permission is introduced. */
    private void cleanupStandalone(){
        if(cameraReceipt!=null)refreshCameraReceipt();
        cancelIntent.run();cancelIntent=()->{};
        if(operation!=null)NativeDeviceTeleportReceipt.retire(operation.issue());
        if(cleanupIdentity()&&cameraCurrent()){
            if(cameraReceipt!=null&&services.rotation() instanceof McRotationService rotation&&rotation.cancelOwned(cameraReceipt,false)&&client.player==player&&client.level==world&&client.getConnection()==connection&&McOwnedRotation.authority(client)==owner){epoch=McOwnedRotation.epoch(client);cameraGeneration=rotation.requestGeneration();}
            if(tool!=null&&services.inventory().selectedSlot()==tool.slot()&&McMovementDeviceInventory.unchanged(client,inventory))services.inventory().selectSlot(priorSlot);
        }
    }
    /** Actual delegated cancellation and local slot restoration; this is not a new-leg admission. */
    public final class TerminalCleanupReceipt {
        private final CameraCustodyAdapter adapter;private final DeviceUseReceipt.Issue issue;private final DeviceUseReceipt.Applied applied;
        private final long beforeEpoch,afterEpoch,beforeGeneration,afterGeneration,ticket;private final int slot;
        private TerminalCleanupReceipt(CameraCustodyAdapter adapter,DeviceUseReceipt.Issue issue,DeviceUseReceipt.Applied applied,long beforeEpoch,long afterEpoch,long beforeGeneration,long afterGeneration,long ticket,int slot){this.adapter=adapter;this.issue=issue;this.applied=applied;this.beforeEpoch=beforeEpoch;this.afterEpoch=afterEpoch;this.beforeGeneration=beforeGeneration;this.afterGeneration=afterGeneration;this.ticket=ticket;this.slot=slot;}
        public CameraCustodyAdapter adapter(){return adapter;}public DeviceUseReceipt.Issue issue(){return issue;}public DeviceUseReceipt.Applied applied(){return applied;}
        public long beforeEpoch(){return beforeEpoch;}public long afterEpoch(){return afterEpoch;}public long beforeGeneration(){return beforeGeneration;}public long afterGeneration(){return afterGeneration;}public long actualTicket(){return ticket;}public int previousSlot(){return slot;}
        public boolean current(){
            if(terminalCleanup!=this||state!=State.ARRIVED||!client.isSameThread())return false;
            var snapshot=captureTerminalSnapshot();if(snapshot==null)return false;
            boolean proof=cleanupIdentity()&&fence.revision()==revision&&adapter.current()&&adapter.inventoryCleanupCurrent()
                &&adapter.cameraEpoch()==afterEpoch&&adapter.requestGeneration()==afterGeneration
                &&services.rotation() instanceof McRotationService rotation&&rotation.requestGeneration()==afterGeneration&&McOwnedRotation.publicationSerial(client)==ticket
                &&services.inventory().selectedSlot()==slot&&McMovementDeviceInventory.unchanged(client,inventory)&&terminalPoseCurrent(applied);
            return proof&&finalLeaseProof()&&snapshot.current();
        }

    }
    private boolean terminalPoseCurrent(DeviceUseReceipt.Applied applied){
        var raw=NativeDeviceTeleportReceipt.current(client);
        return applied!=null&&raw!=null&&raw.serial()==applied.serial()&&position().distanceTo(applied.position())<=.05
            &&client.player.onGround()&&Math.hypot(client.player.getDeltaMovement().x,client.player.getDeltaMovement().z)<.08&&terrain.standingClear(position());
    }
    public TerminalCleanupReceipt terminalCleanup(){return terminalCleanup;}
    public void cancel(){
        thread();var actual=startCustody;
        if(state==State.IDLE&&actual!=null){actual.interruptAndClean();return;}
        if(state==State.AIMING||state==State.WAITING)terminate(State.CANCELLED,"Cancelled");
    }
    /** Read-only own-publication handoff before a shared caller compares its cached epoch. */
    public boolean refreshOwnedPublication(){
        thread();if(state==State.WAITING)return guard(System.currentTimeMillis());
        if(state!=State.AIMING||!custody()||fence==null||fence.policy()!=policy||fence.revision()!=revision||fence.journey()!=journey
            ||tool==null||services.inventory().selectedSlot()!=tool.slot()||!McMovementDeviceInventory.unchanged(client,inventory)
            ||!(services.rotation() instanceof McRotationService rotation)||rotation.requestGeneration()!=cameraGeneration)return false;
        // The exact owned transition is the sole epoch bridge; the native/config/input fence is checked afterward.
        return refreshCameraReceipt()&&guard(System.currentTimeMillis());
    }
    public long cameraEpoch(){return epoch;}
    public long cameraRequestGeneration(){return cameraGeneration;}
    public State state(){return state;}public String reason(){return reason;}public boolean dispatched(){return dispatched;}
    public DeviceUseReceipt.Applied appliedReceipt(){return acceptedReceipt;}
    public DeviceUseReceipt.Issue issue(){return operation==null?null:operation.issue();}
    private void thread(){if(!client.isSameThread())throw new IllegalStateException("Device executor requires client thread");}
    private static double wrap(double v){v%=360;if(v>=180)v-=360;if(v< -180)v+=360;return v;}
    private static boolean finite(Vec3 p){return p!=null&&Double.isFinite(p.x())&&Double.isFinite(p.y())&&Double.isFinite(p.z());}
}
