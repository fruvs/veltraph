package dev.veltraph.client.galatea;
import dev.veltraph.client.core.Module;
import dev.veltraph.client.config.*;
import dev.veltraph.client.navigation.mc.McTravelNavigator;
import dev.veltraph.client.service.FarmServices;
import dev.veltraph.client.service.mc.McOwnedRotation;
import dev.veltraph.client.world.BlockPos;
import dev.veltraph.client.world.Vec3;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import net.minecraft.world.entity.EquipmentSlot;
import dev.veltraph.client.inventory.NativeStackIdentity;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
/** STAGED source caller: normal native walk/aim/slot and guarded one-key dispatch, never scaffold fabrication. */
public final class McFigScaffoldCaller {
    public interface Host {
        Module owner();boolean permission();boolean networkGapObserved();String settings();int axeSlot();
        FigHarvestPickupLedger harvestLedger();FigHarvestPickupLedger.Scope harvestScope();
        default FigToolWearLedger toolWearLedger(){return null;}
        void selectSlot(int slot);void aim(BlockPos seed);void attack(boolean held);
        void enterThrowing();void enterBreak(Vec3 centeredFeet);void failed(String reason);
    }
    public record Receipt(FigScaffoldStateMachine.State state,long generation,
            FigScaffoldStateMachine.Dispatch dispatch,int currentReachable,int raisedReachable,
            int geometryHeight,int completionRise,int startCeilY,long started,long lastMotion,Vec3 centeredFeet,int walkingBudget,Vec3 sourceCentralPose,Vec3 nativeCentralGoal){}
    /** Cached only after a real ordinary-tick fence/ledger commit; getters never reevaluate custody. */
    public record WearAdmission(long generation,long atMillis,FigScaffoldStateMachine.State state,
            boolean running,int axeSlot,int selectedSlot,long originalDeadline){}
    private static final FigSourcePollTimer.Distraction DISTRACTION=new FigSourcePollTimer.Distraction(
            ThreadLocalRandom.current().nextDouble(.005,.02),ThreadLocalRandom.current().nextDouble(2,2.5));
    private static final long GIFT_HOLD_MS=ThreadLocalRandom.current().nextLong(500,800);
    private static final Map<KeyMapping,FigSourceClickHistory> INPUT_HISTORY=new WeakHashMap<>();
    private static final List<EquipmentSlot> GEAR=List.of(EquipmentSlot.HEAD,EquipmentSlot.CHEST,EquipmentSlot.LEGS,EquipmentSlot.FEET,EquipmentSlot.OFFHAND);
    private static final FigSourcePollTimer.RandomSource RANDOM=new FigSourcePollTimer.RandomSource(){
        public long nextLong(long low,long high){return ThreadLocalRandom.current().nextLong(low,high);}
        public double nextDouble(){return ThreadLocalRandom.current().nextDouble();}
        public double nextDouble(double low,double high){return ThreadLocalRandom.current().nextDouble(low,high);}
    };
    private final Minecraft client;private final ConfigService config;private final FarmServices services;private final Host host;
    private final FigScaffoldAttemptFence fence=new FigScaffoldAttemptFence();
    private final FigScaffoldStateMachine machine=new FigScaffoldStateMachine(FigScaffoldStateMachine.recoveredOrdinaryCallerTimeouts());
    private McTravelNavigator path;private McFigSourceSelector selector;private McFigOwnedUseBridge bridge;
    private FigObservedScaffoldMetadata.Observation metadata;
    private final FigSourcePollTimer switchTimer=new FigSourcePollTimer(180,350,true,RANDOM,DISTRACTION);
    private final FigSourcePollTimer startTimer=new FigSourcePollTimer(1100,1300,false,RANDOM,DISTRACTION);
    private final FigSourcePollTimer aimTimer=new FigSourcePollTimer(200,400,true,RANDOM,DISTRACTION);
    private long callbackGeneration,giftTimestamp;private Vec3 centeredFeet;private boolean running,pathPending;
    private Random axeDecisionRandom;private WearAdmission lastWearAdmission;
    private int currentReachable,raisedReachable,walkingBudget;private Vec3 sourceCentralPose,nativeCentralGoal;
    public McFigScaffoldCaller(Minecraft client,ConfigService config,FarmServices services,Host host){this.client=client;this.config=config;this.services=services;this.host=host;}
    public void bindBridge(McFigOwnedUseBridge bridge){if(running)throw new IllegalStateException("Stop Fig before changing native use bridge");this.bridge=Objects.requireNonNull(bridge);}
    /** Optional seeded axe-decision stream; source timers and every other random draw stay independent. */
    public void bindAxeDecisionRandom(Random random){if(host.owner().isEnabled()||running)throw new IllegalStateException("Stop Fig before changing axe-decision random source");axeDecisionRandom=Objects.requireNonNull(random);}
    public void resetAxeDecisionRandom(){if(host.owner().isEnabled()||running)throw new IllegalStateException("Stop Fig before restoring axe-decision random source");axeDecisionRandom=null;}
    public boolean axeDecisionRandomBound(){return axeDecisionRandom!=null;}
    double nextAxeDecisionSample(){return axeDecisionRandom==null?ThreadLocalRandom.current().nextDouble():axeDecisionRandom.nextDouble();}
    public Optional<WearAdmission> wearAdmission(){return Optional.ofNullable(lastWearAdmission);}
    /** Source activation clears cached attempt/aim and gift timestamp, not static poll timer intervals. */
    public void onActivation(){cancel();lastWearAdmission=null;giftTimestamp=0;selector=null;centeredFeet=null;}
    public boolean begin(McFigSourceSelector selector,FigObservedScaffoldMetadata.Observation metadata,
            boolean aimHigher,BlockPos previousTarget,long now,long originalDeadline){
        cancel();lastWearAdmission=null;this.selector=Objects.requireNonNull(selector);this.metadata=Objects.requireNonNull(metadata);centeredFeet=null;
        var decision=machine.begin(metadata.values());callbackGeneration=decision.generation();
        if(bridge==null||!fence.begin(callbackGeneration,now,originalDeadline,frame())){machine.stop();host.failed("Fig scaffold native Root bridge/custody unavailable");return false;}
        running=true;path=new McTravelNavigator(client,services,config);
        var stand=selector.prepareScaffold(aimHigher,previousTarget);
        if(stand.isEmpty()){apply(machine.prepared(callbackGeneration,false,null,now),now);return true;}
        sourceCentralPose=stand.orElseThrow();
        var nativeTerrain=new dev.veltraph.client.navigation.mc.McNavigator(client,services.input(),config);
        nativeCentralGoal=nativeTerrain.position(sourceCentralPose.toBlockPos());
        boolean started=path.startWalking(nativeCentralGoal,500);walkingBudget=path.lastWalkingSearchBudget();
        if(!started){apply(machine.prepared(callbackGeneration,false,null,now),now);return true;}
        pathPending=true;return true;
    }
    private FigScaffoldAttemptFence.Frame frame(){
        var p=client.player;var w=client.level;var c=client.getConnection();
        boolean nativeReady=client.isSameThread()&&host.owner().isEnabled()&&host.permission()&&p!=null&&w!=null&&c!=null&&p.isAlive()&&client.gui.screen()==null
                &&p.containerMenu==p.inventoryMenu&&p.inventoryMenu.getCarried().isEmpty()&&McOwnedRotation.authority(client)==host.owner();
        var inventory=new ArrayList<NativeStackIdentity.Snapshot>();var gear=new ArrayList<NativeStackIdentity.Snapshot>();
        if(nativeReady){for(int i=0;i<p.getInventory().getContainerSize();i++)inventory.add(NativeStackIdentity.capture(p.getInventory().getItem(i),w.registryAccess()));for(var slot:GEAR)gear.add(NativeStackIdentity.capture(p.getItemBySlot(slot),w.registryAccess()));}
        return new FigScaffoldAttemptFence.Frame(p,w,c,McOwnedRotation.authority(client),McOwnedRotation.epoch(client),p==null?-1:p.getInventory().getSelectedSlot(),inventory,gear,host.settings(),nativeReady);
    }
    private boolean valid(long now){try{return fence.valid(machine.generation(),now,frame());}catch(RuntimeException unavailable){return false;}}
    /** Only the ordinary gameplay tick may consume complete native pickup or owned axe-wear receipts. */
    private boolean validWithHarvest(long now){try{
        var actual=frame();long generation=machine.generation();
        if(fence.valid(generation,now,actual))return true;
        if(!fence.harvestCustody(generation,now,actual))return false;
        var ledger=host.harvestLedger();var scope=host.harvestScope();if(ledger==null||scope==null||metadata==null)return false;
        var protectedSlots=new HashSet<Integer>();protectedSlots.add(host.axeSlot());protectedSlots.add(metadata.hotbarSlot());protectedSlots.add(fence.originalHeldSlot());
        var baseline=fence.inventoryBaseline();
        var permit=ledger.review(scope,generation,baseline,actual.inventory(),protectedSlots);
        if(permit.isPresent())return fence.admitHarvest(generation,now,actual,permit.orElseThrow(),ledger,scope);
        var wearLedger=host.toolWearLedger();if(wearLedger==null)return false;
        int axeSlot=host.axeSlot();
        var wear=wearLedger.review(scope,generation,axeSlot,baseline,actual.inventory(),now,actual.epoch(),actual.settings(),fence.deadline());if(wear.isEmpty())return false;
        var projected=wear.orElseThrow().after();
        var pickup=projected.equals(actual.inventory())?Optional.<FigHarvestPickupLedger.Permit>empty():ledger.review(scope,generation,projected,actual.inventory(),protectedSlots);
        if(!projected.equals(actual.inventory())&&pickup.isEmpty())return false;
        boolean admitted=fence.admitWear(generation,now,actual,axeSlot,wear.orElseThrow(),wearLedger,scope,pickup.orElse(null),ledger);
        if(admitted)lastWearAdmission=new WearAdmission(generation,now,machine.state(),running,axeSlot,actual.selectedSlot(),fence.deadline());
        return admitted;
    }catch(RuntimeException unavailable){return false;}}
    /** Native observer's revalidation excludes only the inventory awaiting exact receipt closure. */
    public boolean nativeHarvestCustody(){try{return fence.harvestCustody(machine.generation(),services.clock().nowMillis(),frame());}catch(RuntimeException unavailable){return false;}}
    /** Read-only lineage for trusted native action capture; never reviews or consumes a wear permit. */
    public long toolWearAttemptGeneration(){return running?machine.generation():-1;}
    public long toolWearAttemptDeadline(){return running?fence.deadline():-1;}
    public boolean hasFrozenAttempt(){return metadata!=null&&fence.originalHeldSlot()>=0;}
    public McFigOwnedUseBridge.Binding binding(){
        if(!running||!client.isSameThread()||!valid(services.clock().nowMillis()))return null;
        // Current signature must remain visible to the native consumer, so drift cannot hide behind a cached string.
        return new McFigOwnedUseBridge.Binding(host.owner(),machine.generation(),fence.deadline(),host.settings());
    }
    /** Source central-stand leg keeps its original500-node walking primitive; it cannot issue a device. */
    public boolean consumeExpectedTeleport(Vec3 position,long now){
        return running&&pathPending&&path!=null&&client.isSameThread()&&valid(now)
                &&path.consumeExpectedTeleport(position,now);
    }
    public void tick(long now){
        if(!running)return;
        if(!validWithHarvest(now)){cancel();host.failed("Fig scaffold attempt lost original native scope/loadout/slot/settings/epoch");return;}
        if(pathPending){var result=path.tick();if(result==McTravelNavigator.State.ACTIVE)return;
            pathPending=false;path.stop();
            if(result!=McTravelNavigator.State.ARRIVED){apply(machine.prepared(callbackGeneration,false,null,now),now);return;}
            prepared(callbackGeneration,now);return;
        }
        var p=client.player;var feet=new BlockPos(p.blockPosition().getX(),p.blockPosition().getY(),p.blockPosition().getZ());
        double dx=p.getX()-p.xo,dy=p.getY()-p.yo,dz=p.getZ()-p.zo,length=Math.sqrt(dx*dx+dy*dy+dz*dz);
        boolean giftHold=now-giftTimestamp<GIFT_HOLD_MS;
        var state=machine.state();boolean switchReady=state==FigScaffoldStateMachine.State.SWITCH_TO_SCAFFOLDING&&!giftHold&&switchTimer.poll(now);
        boolean startReady=state==FigScaffoldStateMachine.State.START_SCAFFOLDING&&!giftHold&&p.onGround()&&startTimer.poll(now);
        // Source WAIT polls/publishes periodic aim before the packet-gap and random branch.
        boolean aimReady=state==FigScaffoldStateMachine.State.WAITING_FOR_SCAFFOLDING&&aimTimer.poll(now);
        boolean networkGap=host.networkGapObserved();
        double randomSample=state==FigScaffoldStateMachine.State.WAITING_FOR_SCAFFOLDING?nextAxeDecisionSample():1;
        boolean randomAxe=state==FigScaffoldStateMachine.State.WAITING_FOR_SCAFFOLDING&&randomSample<.05&&now-machine.started()>200;
        boolean axeSelected=p.getInventory().getSelectedSlot()==host.axeSlot();
        boolean feetPassable=state==FigScaffoldStateMachine.State.WAITING_FOR_SCAFFOLDING&&!randomAxe&&axeSelected&&selector.scaffoldPassable(feet);
        boolean seedVisible=feetPassable&&selector.scaffoldSeedVisible();
        apply(machine.tick(new FigScaffoldStateMachine.Frame(now,true,p.getInventory().getSelectedSlot()==metadata.hotbarSlot(),
                axeSelected,giftHold,switchReady,startReady,aimReady,p.onGround(),
                feetPassable,selector.scaffoldPassable(feet.up()),seedVisible,networkGap,length,p.getY(),randomSample)),now);
    }
    private void prepared(long generation,long now){
        if(generation!=machine.generation()||!valid(now))return;
        var p=client.player;var e=p.getEyePosition();var eye=new Vec3(e.x,e.y,e.z);
        currentReachable=selector.scaffoldReachableCount(eye);raisedReachable=selector.scaffoldReachableCount(new Vec3(eye.x(),eye.y()+metadata.values().completionRise(),eye.z()));
        var feet=new BlockPos(p.blockPosition().getX(),p.blockPosition().getY(),p.blockPosition().getZ());
        var result=machine.prepared(generation,true,new FigScaffoldStateMachine.Admission(selector.scaffoldPassable(feet.up()),currentReachable,raisedReachable),now);
        if(result.state()==FigScaffoldStateMachine.State.SWITCH_TO_SCAFFOLDING)switchTimer.reset(now);
        apply(result,now);
    }
    private void ownedSelect(int slot,long now){
        if(!valid(now))throw new IllegalStateException("Fig owned slot transition lost its frozen attempt");
        host.selectSlot(slot);if(!fence.selected(machine.generation(),now,slot,frame()))throw new IllegalStateException("Fig native selected-slot receipt did not match owned request");
    }
    private void apply(FigScaffoldStateMachine.Decision decision,long now){
        if(decision.generation()!=machine.generation())return;
        for(var command:decision.commands())switch(command){
            case PREPARE_CENTRAL_STAND -> {} // begin owns the generation-bound ordinary path request.
            case AIM -> selector.scaffoldSeed().ifPresent(host::aim);
            case CLEAR_ATTACK -> host.attack(false);
            case SELECT_SCAFFOLD -> ownedSelect(metadata.hotbarSlot(),now);
            case SELECT_AXE -> ownedSelect(host.axeSlot(),now);
            case HOLD_ATTACK -> host.attack(true);
            case INVOKE_RIGHT_CLICK_ONCE -> {
                var history=INPUT_HISTORY.computeIfAbsent(client.options.keyUse,key->new FigSourceClickHistory(ThreadLocalRandom.current().nextInt(10,15)));
                var dispatch=bridge.issueOnce(host.owner(),decision.generation(),metadata.hotbarSlot(),fence.deadline(),host.settings(),()->history.admit(services.clock().nowMillis(),valid(services.clock().nowMillis()),true));
                machine.recordDispatch(decision.generation(),dispatch);
            }
            case CAPTURE_CENTERED_FEET -> centeredFeet=selector.captureCenteredStand();
            case ENTER_SCAFFOLD_BREAK -> {releasePath();running=false;revoke();host.enterBreak(centeredFeet);}
            case ENTER_THROWING -> {releasePath();running=false;revoke();host.enterThrowing();}
            case RELEASE_OWNED_INPUTS -> {if(McOwnedRotation.authority(client)==host.owner())host.attack(false);}
        }
    }
    public void feedback(String raw,long now){
        if(raw!=null&&raw.startsWith(" ")&&raw.contains("TREE GIFT"))giftTimestamp=now;
        if(metadata!=null)apply(machine.placementFeedback(raw),now);
    }
    private void revoke(){if(bridge!=null)bridge.revoke(host.owner(),machine.generation());}
    private void releasePath(){
        var p=client.player;
        var scope=new FigScaffoldAttemptFence.Frame(p,client.level,client.getConnection(),McOwnedRotation.authority(client),McOwnedRotation.epoch(client),p==null?-1:p.getInventory().getSelectedSlot(),List.of(),List.of(),"",false);
        if(path!=null&&client.isSameThread()&&fence.sameNativeScope(scope))path.stop();
        path=null;pathPending=false;
    }
    public void cancel(){
        long old=machine.generation();if(bridge!=null)bridge.revoke(host.owner(),old);
        releasePath();running=false;fence.cancel();machine.stop();
    }
    public boolean running(){return running;}
    public boolean sourceParentEntryHold(long now){return machine.sourceParentEntryHold(now);}
    public Receipt receipt(){return new Receipt(machine.state(),machine.generation(),machine.dispatch(),currentReachable,raisedReachable,
            metadata==null?0:metadata.values().geometryHeight(),metadata==null?0:metadata.values().completionRise(),machine.startCeilY(),machine.started(),machine.lastMotion(),centeredFeet,walkingBudget,sourceCentralPose,nativeCentralGoal);}
}
