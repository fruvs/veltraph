package dev.veltraph.client.combat.grind;

import dev.veltraph.client.config.*;
import dev.veltraph.client.core.AbstractModule;
import dev.veltraph.client.core.ModuleCategory;
import dev.veltraph.client.game.GameArea;
import dev.veltraph.client.navigation.WalkController;
import dev.veltraph.client.navigation.mc.McNavigator;
import dev.veltraph.client.service.FarmServices;
import dev.veltraph.client.world.BlockPos;
import dev.veltraph.client.world.Vec3;
import java.util.*;
import java.util.function.Predicate;
import net.minecraft.client.Minecraft;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.EntityHitResult;

/** Minecraft adapter for local melee profiles. Uses vanilla attacks only after crosshair and reach checks. */
public final class MeleeGrindModule extends AbstractModule {
    private final Minecraft client;
    private final FarmServices services;
    private final ConfigService config;
    private final GameArea area;
    private final String names;
    private final TargetFilter targetFilter;
    private final McBestiaryWeaponSelection bestiaryWeapon;
    private final java.util.function.Supplier<? extends dev.veltraph.client.core.AbstractModule> declaredWeaponParent;
    dev.veltraph.client.core.AbstractModule declaredWeaponParentIdentity(){return declaredWeaponParent==null?this:declaredWeaponParent.get();}
    private final CombatWeaponPolicy.Family weaponFamily;
    /** Explicit parent policy owner; this is deliberately separate from CombatWeaponPolicy.Family. */
    public enum ParentWeaponDeclaration { FISHING_AUTO_KILLER }
    private final ParentWeaponDeclaration parentWeaponDeclaration;
    private final java.util.function.Supplier<CombatWeaponPolicy> parentWeaponPolicy;
    private final java.util.function.IntSupplier parentFixedSlot;
    private final java.util.function.Supplier<String> parentWeaponSignature;
    /** Pure observation policy; an absent parent policy preserves the generic child's behavior. */
    static final class TargetFilter {
        private final Predicate<CombatTarget> predicate;
        TargetFilter(Predicate<CombatTarget> predicate){this.predicate=predicate;}
        boolean bounded(){return predicate!=null;}
        boolean accepts(CombatTarget target){return target!=null&&(predicate==null||predicate.test(target));}
        List<CombatTarget> candidates(List<CombatTarget> observed){return observed.stream().filter(this::accepts).toList();}
    }
    /** Parent specialization observes the actual selected body/task; it never receives synthetic reach or target authority. */
    public interface OwnedMechanic {
        boolean active();boolean borrowing();boolean failed();String status();double range();
        boolean wants(LivingEntity boss,dev.veltraph.client.combat.slayer.SlayerObjectives.Task task);
        boolean suppresses(dev.veltraph.client.combat.slayer.SlayerObjectives.Task task);
        net.minecraft.world.entity.Entity aimTarget(LivingEntity boss,dev.veltraph.client.combat.slayer.SlayerObjectives.Task task);
        boolean tick(MeleeGrindModule child,LivingEntity boss,dev.veltraph.client.combat.slayer.SlayerObjectives.Task task,boolean safety,long now);
    }
    private McSlayerFinishingSwap finishing;
    private boolean requestedFinishingEligible;
    private FinishingBoundary finishingBoundary;
    private final dev.veltraph.client.combat.slayer.CombatAwareness finishingAwareness=new dev.veltraph.client.combat.slayer.CombatAwareness();
    private static final class FinishingBoundary {
        final LivingEntity target;final String observedName;final Object player,world,connection;final int tick;final boolean[] hardware;
        private FinishingBoundary(Minecraft client,LivingEntity target,String observedName){this.target=target;this.observedName=observedName;player=client.player;world=client.level;connection=client.getConnection();
            tick=client.player.tickCount;hardware=finishingHardware(client);}
    }
    public void finishingParent(dev.veltraph.client.combat.slayer.SlayerModule.FinishingParentBinding actual){
        if(isEnabled()||finishing!=null||actual==null||!actual.belongsTo(this))throw new IllegalStateException("Bind the actual typed finishing parent before activation");
        finishing=new McSlayerFinishingSwap(client,actual,this,config,dev.veltraph.client.VeltraphBootstrap.moduleManager());
    }
    /** End only a genuine current old borrow before the original owned loot flow changes phase.
     * Completion text supplies no inventory identity or delivery authority. */
    public boolean finishingReleaseBeforeOwnedLoot(){return finishing==null||finishing.restoreActive();}
    public boolean finishingRequiresOwnedCleanup(){return finishing!=null&&finishing.requiresOwnedCleanup();}
    public int finishingSelections(){return finishing==null?0:finishing.selections();}
    public int finishingRestores(){return finishing==null?0:finishing.restores();}
    public long finishingSubmittedAttacks(){return finishing==null?0:finishing.submittedAttacks();}
    public void observeFinishingParentStopped(dev.veltraph.client.core.ModuleManager.OwnDisableReceipt receipt){if(finishing!=null)finishing.observeParentStopped(receipt);}
    private static boolean[] finishingHardware(Minecraft client){var o=client.options;return new boolean[]{o.keyUp.isDown(),o.keyDown.isDown(),o.keyLeft.isDown(),o.keyRight.isDown(),o.keyJump.isDown(),o.keyShift.isDown(),o.keySprint.isDown(),o.keyAttack.isDown(),o.keyUse.isDown()};}
    boolean finishingBoundaryLeafCurrent(Object actual,LivingEntity target){return actual!=null&&finishingBoundary==actual&&finishingBoundary.target==target
            &&client.player==finishingBoundary.player&&client.level==finishingBoundary.world&&client.getConnection()==finishingBoundary.connection
            &&client.player!=null&&client.player.tickCount==finishingBoundary.tick&&java.util.Arrays.equals(finishingBoundary.hardware,finishingHardware(client))
            &&isEnabled()&&!scheduledPaused()&&!cocoonHeld&&!scheduledBusy&&!ownedProtocolPending&&!objectiveAbort&&ownedMechanic==null
            &&target.isAlive();}
    boolean finishingBoundaryCurrent(Object actual,LivingEntity target){return finishingBoundaryLeafCurrent(actual,target)
            &&!ownedMechanicPending()&&selectedCombatTarget().orElse(null)==target&&finishingBoundaryNativeLeafCurrent(actual,target);}
    /** No target-filter, role/provider or mechanic callback runs after this leaf begins. */
    boolean finishingBoundaryNativeLeafCurrent(Object actual,LivingEntity target){return finishingBoundaryLeafCurrent(actual,target)
            &&client.level!=null&&client.level.getEntity(loop.targetId())==target&&sourceReachable(target)
            &&finishingFreshHazardClear(target)&&finishingPhysicalHit(target)&&finishingBoundaryLeafCurrent(actual,target);}
    private boolean finishingFreshHazardClear(LivingEntity target){
        var boundary=finishingBoundary;
        if(boundary==null||boundary.target!=target||boundary.observedName==null||client.player==null||client.level==null)return false;
        var observed=finishingAwareness.observe(client,target,boundary.observedName,client.player.tickCount*50L);
        return observed.response()==dev.veltraph.client.combat.slayer.SlayerSignals.Response.NONE&&!observed.suspendAttacks();
    }
    private boolean finishingPhysicalHit(LivingEntity target){
        if(client.player==null||client.level==null||client.gameMode==null||client.gui.screen()!=null||client.level.getEntity(loop.targetId())!=target||!sourceReachable(target))return false;
        var p=client.player;var eye=p.getEyePosition();var ray=p.getLookAngle();double reach=clamp(config.get(CombatConfig.COMBAT_MELEE_REACH),1,3,3);
        var box=target.getBoundingBox().inflate(target.getPickRadius());var received=box.move(target.getClientPosition().subtract(target.position()));
        if(!dev.veltraph.client.combat.slayer.CombatHitEnvelope.intersects(new Vec3(eye.x,eye.y,eye.z),new Vec3(ray.x,ray.y,ray.z),new Vec3(box.minX,box.minY,box.minZ),new Vec3(box.maxX,box.maxY,box.maxZ),reach)
                ||!dev.veltraph.client.combat.slayer.CombatHitEnvelope.intersects(new Vec3(eye.x,eye.y,eye.z),new Vec3(ray.x,ray.y,ray.z),new Vec3(received.minX,received.minY,received.minZ),new Vec3(received.maxX,received.maxY,received.maxZ),reach))return false;
        var targetMotion=target.getKnownSpeed();var playerMotion=p.getKnownMovement();
        if(!dev.veltraph.client.combat.slayer.CombatHitEnvelope.observedCadence(new Vec3(eye.x,eye.y,eye.z),new Vec3(ray.x,ray.y,ray.z),new Vec3(received.minX,received.minY,received.minZ),new Vec3(received.maxX,received.maxY,received.maxZ),new Vec3(targetMotion.x,targetMotion.y,targetMotion.z),new Vec3(playerMotion.x,playerMotion.y,playerMotion.z),target.getType().updateInterval(),reach))return false;
        var crossing=box.contains(eye)?Optional.of(eye):box.clip(eye,eye.add(ray.scale(reach)));if(crossing.isEmpty())return false;var point=crossing.get();double distance=eye.distanceTo(point);
        for(var other:client.level.entitiesForRendering())if(other!=p&&other!=target&&other.isAlive()&&other.isPickable()){
            var otherBox=other.getBoundingBox().inflate(other.getPickRadius());var hit=otherBox.contains(eye)?Optional.of(eye):otherBox.clip(eye,eye.add(ray.scale(reach)));
            if(hit.isPresent()&&eye.distanceTo(hit.get())<=distance-1e-6)return false;}
        int samples=Math.max(1,(int)Math.ceil(distance*4));for(int sample=0;sample<=samples;sample++)if(!client.level.isLoaded(net.minecraft.core.BlockPos.containing(eye.lerp(point,(double)sample/samples))))return false;
        return client.level.clip(new net.minecraft.world.level.ClipContext(eye,point,net.minecraft.world.level.ClipContext.Block.COLLIDER,net.minecraft.world.level.ClipContext.Fluid.NONE,p)).getType()==net.minecraft.world.phys.HitResult.Type.MISS;
    }
    private OwnedMechanic ownedMechanic;
    public void ownedMechanic(OwnedMechanic value){if(isEnabled())throw new IllegalStateException("Bind before enabling the combat child");ownedMechanic=value;}
    public boolean ownedMechanicPending(){return ownedMechanic!=null&&ownedMechanic.active();}
    private void acquireMechanic(){
        requestedAttack=null;requestedWeapon=null;requestedWorld=null;requestedSlot=-1;
        objectiveRequests.reset();navigation.stop();pathActive=false;lastPathGoal=null;pathTarget=-1;
        services.input().releaseAllMovement();services.breaking().setBreaking(false);
    }
    /** Explicit mapped Graveyard child declaration; other melee consumers retain their own policy. */
    private SourceCombatUnreachableConsumer.Declaration sourceUnreachableDeclaration;
    public void sourceUnreachableConsumer(SourceCombatUnreachableConsumer.Declaration declaration) {
        if(isEnabled())throw new IllegalStateException("Bind the recovered combat child before enabling it");
        sourceUnreachableDeclaration=Objects.requireNonNull(declaration);
    }
    private boolean sourceReachable(net.minecraft.world.entity.Entity actualBody) {
        return SourceCombatUnreachableConsumer.accepts(sourceUnreachableDeclaration,actualBody,
                dev.veltraph.client.combat.ghost.GhostSourceUnreachableActorLedger.runtime());
    }
    private final CombatLoop loop=new CombatLoop();
    private McNavigator navigation;
    private final CombatScheduledPause scheduledPause;
    private net.minecraft.client.player.LocalPlayer nativeOwner;
    private net.minecraft.client.multiplayer.ClientLevel nativeWorld;
    private net.minecraft.client.multiplayer.ClientPacketListener nativeConnection;
    private boolean scheduledBusy;
    private boolean cocoonHeld;
    private UUID cocoonReplacement;
    private final CombatCamera camera;
    private final boolean parentCamera;
    private final dev.veltraph.client.combat.slayer.DaggerAttunement dagger=new dev.veltraph.client.combat.slayer.DaggerAttunement();
    private final dev.veltraph.client.combat.slayer.ImpelController impel=new dev.veltraph.client.combat.slayer.ImpelController();
    private final dev.veltraph.client.navigation.TravelHeading impelPitch=new dev.veltraph.client.navigation.TravelHeading();
    private boolean impelTurning;
    private final dev.veltraph.client.combat.slayer.VampireFinisher finisher=new dev.veltraph.client.combat.slayer.VampireFinisher();
    private final dev.veltraph.client.combat.slayer.SpringInteraction spring=new dev.veltraph.client.combat.slayer.SpringInteraction();
    private final dev.veltraph.client.combat.slayer.HolyIceController holyIce=new dev.veltraph.client.combat.slayer.HolyIceController();
    private final dev.veltraph.client.combat.slayer.ObjectiveRequests objectiveRequests=new dev.veltraph.client.combat.slayer.ObjectiveRequests();
    private final CombatAim softAim=new CombatAim();
    private final CombatMovement movement=new CombatMovement();
    private final dev.veltraph.client.combat.slayer.CombatAwareness awareness=new dev.veltraph.client.combat.slayer.CombatAwareness();
    private BlockPos lastPathGoal;
    private boolean pathActive;
    private long lastSpecialAction;
    private long nextPath;
    private int pathTarget=-1;
    private String phase="IDLE";
    private net.minecraft.world.entity.Entity requestedAttack;
    private net.minecraft.client.multiplayer.ClientLevel requestedWorld;
    private net.minecraft.world.item.ItemStack requestedWeapon;
    private int requestedSlot=-1;
    private boolean ownedProtocolPending,objectiveAbort;
    private UUID abortObjective;
    private String objectiveFailure="";
    private dev.veltraph.client.combat.slayer.SlayerObjectives.Task lastObjective;
    public MeleeGrindModule(String id,String title,GameArea area,String names,Minecraft client,
                             FarmServices services,ConfigService config) {
        this(id,title,area,names,client,services,config,null);
    }
    public MeleeGrindModule(String id,String title,GameArea area,String names,Minecraft client,
                             FarmServices services,ConfigService config,CombatCamera parentCamera) {
        this(id,title,area,names,client,services,config,parentCamera,null);
    }
    /** Parent-owned target bounds, evaluated on physical body observations rather than nameplates. */
    public MeleeGrindModule(String id,String title,GameArea area,String names,Minecraft client,
                             FarmServices services,ConfigService config,CombatCamera parentCamera,Predicate<CombatTarget> targetPredicate) {
        this(id,title,area,names,client,services,config,parentCamera,targetPredicate,null,null);
    }
    /** Typed declaration from the actual owning parent, never inferred from a module ID. */
    public MeleeGrindModule(String id,String title,GameArea area,String names,Minecraft client,
            FarmServices services,ConfigService config,CombatCamera parentCamera,Predicate<CombatTarget> targetPredicate,
            CombatWeaponPolicy.Family family,Predicate<MeleeGrindModule> parentAuthority) {
        this(id,title,area,names,client,services,config,parentCamera,targetPredicate,family,parentAuthority,null,null,null,null,null);
    }
    public MeleeGrindModule(String id,String title,GameArea area,String names,Minecraft client,
            FarmServices services,ConfigService config,CombatCamera parentCamera,Predicate<CombatTarget> targetPredicate,
            CombatWeaponPolicy.Family family,Predicate<MeleeGrindModule> parentAuthority,
            java.util.function.Supplier<? extends dev.veltraph.client.core.AbstractModule> actualDeclaredParent){
        this(id,title,area,names,client,services,config,parentCamera,targetPredicate,family,parentAuthority,null,null,null,null,java.util.Objects.requireNonNull(actualDeclaredParent));
    }
    /** Passive pre-reel proposal; only enabled, exactly-bound parent custody may commit it. */
    public MeleeGrindModule(String id,String title,GameArea area,String names,Minecraft client,
            FarmServices services,ConfigService config,CombatCamera parentCamera,Predicate<CombatTarget> targetPredicate,
            ParentWeaponDeclaration declaration,java.util.function.Supplier<CombatWeaponPolicy> policy,
            java.util.function.IntSupplier fixedSlot,java.util.function.Supplier<String> signature,
            Predicate<MeleeGrindModule> parentAuthority){
        this(id,title,area,names,client,services,config,parentCamera,targetPredicate,null,parentAuthority,
                java.util.Objects.requireNonNull(declaration),java.util.Objects.requireNonNull(policy),
                java.util.Objects.requireNonNull(fixedSlot),java.util.Objects.requireNonNull(signature),null);
    }
    public MeleeGrindModule(String id,String title,GameArea area,String names,Minecraft client,
            FarmServices services,ConfigService config,CombatCamera parentCamera,Predicate<CombatTarget> targetPredicate,
            ParentWeaponDeclaration declaration,java.util.function.Supplier<CombatWeaponPolicy> policy,
            java.util.function.IntSupplier fixedSlot,java.util.function.Supplier<String> signature,
            Predicate<MeleeGrindModule> parentAuthority,
            java.util.function.Supplier<? extends dev.veltraph.client.core.AbstractModule> actualDeclaredParent){
        this(id,title,area,names,client,services,config,parentCamera,targetPredicate,null,parentAuthority,
                java.util.Objects.requireNonNull(declaration),java.util.Objects.requireNonNull(policy),
                java.util.Objects.requireNonNull(fixedSlot),java.util.Objects.requireNonNull(signature),java.util.Objects.requireNonNull(actualDeclaredParent));
    }
    private MeleeGrindModule(String id,String title,GameArea area,String names,Minecraft client,
            FarmServices services,ConfigService config,CombatCamera parentCamera,Predicate<CombatTarget> targetPredicate,
            CombatWeaponPolicy.Family family,Predicate<MeleeGrindModule> parentAuthority,
            ParentWeaponDeclaration declaration,java.util.function.Supplier<CombatWeaponPolicy> policy,
            java.util.function.IntSupplier fixedSlot,java.util.function.Supplier<String> signature,
            java.util.function.Supplier<? extends dev.veltraph.client.core.AbstractModule> actualDeclaredParent){
        super(id,title,ModuleCategory.COMBAT);declaredWeaponParent=actualDeclaredParent; this.client=client; this.services=services;
        weaponFamily=family;parentWeaponDeclaration=declaration;parentWeaponPolicy=policy;parentFixedSlot=fixedSlot;parentWeaponSignature=signature;
        if(declaration!=null&&parentAuthority==null)throw new IllegalArgumentException("Fishing weapon policy requires actual bound parent custody");
        if(family!=null&&parentAuthority==null)throw new IllegalArgumentException("Typed combat family requires actual parent custody");
        targetFilter=new TargetFilter(targetPredicate);
        this.parentCamera=parentCamera!=null;camera=parentCamera==null?new CombatCamera():parentCamera;
        this.config=config; this.area=area; this.names=names; navigation=new McNavigator(client,services.input(),config);
        bestiaryWeapon=declaration!=null?new McBestiaryWeaponSelection(client,services.inventory(),config,declaration,
                policy,fixedSlot,()->isEnabled()&&parentAuthority.test(this),this):family!=null?new McBestiaryWeaponSelection(client,services.inventory(),config,family,
                ()->isEnabled()&&parentAuthority.test(this),false,this):id.equals("bestiary_grind")?new McBestiaryWeaponSelection(client,services.inventory(),config,this):null;
        scheduledPause=new CombatScheduledPause(client,this::isEnabled,this::scheduledPauseReadiness,this::scheduledPauseSignature,
                this::releaseForScheduledPause,this::rebindScheduledNative,()->phase="WAIT_AFTER_OWNED_REJOIN",()->setEnabled(false));
    }
    public dev.veltraph.client.scheduling.ScheduledPausePort scheduledPausePort(){return scheduledPause;}
    public boolean scheduledPaused(){return scheduledPause.paused();}
    public int confirmedKills(){return loop.kills();}
    /** Read-only native commit receipt. A prepared proposal does not expose a committed slot. */
    public int committedWeaponSlot(){return bestiaryWeapon!=null?bestiaryWeapon.slot():isEnabled()?primaryWeaponSlot():-1;}
    /** Only actual issued native attacks advance this; parent may bound selected-tool wear by it. */
    public long issuedWeaponAttacks(){return bestiaryWeapon==null?0:bestiaryWeapon.issuedAttacks();}
    public FishingWeaponSelectionState.Phase fishingWeaponReservationPhase(int originalRodSlot,AbstractModule actualParent){return parentWeaponDeclaration==ParentWeaponDeclaration.FISHING_AUTO_KILLER&&bestiaryWeapon!=null?bestiaryWeapon.fishingReservationPhase(originalRodSlot,actualParent):FishingWeaponSelectionState.Phase.REFUSED;}
    public dev.veltraph.client.garden.pests.mc.McPestInventoryRestorationObserver.SelectionHistory fishingWeaponReturnHistory(int originalRodSlot,AbstractModule actualParent){return parentWeaponDeclaration==ParentWeaponDeclaration.FISHING_AUTO_KILLER&&bestiaryWeapon!=null?bestiaryWeapon.fishingReturnHistory(originalRodSlot,actualParent):null;}

    /** Read-only exact selected native body for the parent quest's death/drop correlation. */
    public Optional<LivingEntity> selectedCombatTarget(){
        if(!isEnabled()||scheduledPaused()||client.level!=nativeWorld||client.player!=nativeOwner||client.getConnection()!=nativeConnection)return Optional.empty();
        var entity=client.level.getEntity(loop.targetId());return entity instanceof LivingEntity living&&permittedNativeTarget(living)?Optional.of(living):Optional.empty();
    }
    public record SelectedTarget(LivingEntity body,String observedName) {}
    /** The same unambiguous nameplate association used to select the physical combat body. */
    public Optional<SelectedTarget> selectedNamedCombatTarget(){
        return selectedCombatTarget().flatMap(body->targets(clamp(config.get(CombatConfig.COMBAT_SEARCH_RADIUS),2,24,16)).stream()
                .filter(candidate->candidate.id()==body.getId()&&candidate.alive()&&!candidate.protectedTarget())
                .findFirst().map(candidate->new SelectedTarget(body,candidate.name())));
    }
    /** Root polls this before rebind; reading it never changes the token or native references. */
    public Optional<String> schedulingReadiness(){return scheduledPaused()?scheduledPause.arrivalReadiness():scheduledPauseReadiness();}
    public String scheduledPauseSignature(){return id()+"|"+area+"|"+names()+"|"+(bestiaryWeapon==null?config.get(CombatConfig.COMBAT_WEAPON_SLOT):config.get(CombatConfig.BESTIARY_PREFERRED_WEAPON))
            +"|"+config.get(CombatConfig.COMBAT_SEARCH_RADIUS)+"|"+config.get(CombatConfig.COMBAT_MELEE_REACH)
            +"|"+config.get(CombatConfig.COMBAT_TARGET_TIMEOUT_MS)+"|"+config.get(CombatConfig.COMBAT_VIEW_WINDOW)
            +"|"+config.get(CombatConfig.COMBAT_RETREAT_DISTANCE)+"|"+config.get(RotationConfig.ROTATION_SPEED_MULTIPLIER)
            +(weaponFamily==null?"":"|"+new com.google.gson.Gson().toJson(java.util.List.of(weaponFamily.name(),config.get(CombatConfig.COMBAT_CONTEXTUAL_WEAPON_PROFILE),config.get(CombatConfig.COMBAT_WEAPON_SLOT),config.get(CombatConfig.BESTIARY_PREFERRED_WEAPON))))
            +(parentWeaponDeclaration==null?"":"|"+OwnedWeaponStart.parentPolicySignature(parentWeaponDeclaration,parentWeaponPolicy.get(),parentFixedSlot.getAsInt(),parentWeaponSignature.get(),config.get(CombatConfig.BESTIARY_PREFERRED_WEAPON)));}
    public Optional<String> scheduledPauseReadiness(){
        var p=client.player;
        if(!isEnabled()||scheduledPaused()||p==null||client.level==null||client.getConnection()==null
                ||p!=nativeOwner||client.level!=nativeWorld||client.getConnection()!=nativeConnection)return Optional.of("Combat native owner unavailable");
        if(!p.isAlive()||p.getHealth()<p.getMaxHealth()*.5||!p.onGround()||p.isInWater()||p.isInLava()
                ||p.isUsingItem()||p.isSwinging()||p.getDeltaMovement().length()>.1||p.getAttackStrengthScale(0)<.95)return Optional.of("Combat needs healthy supported idle posture");
        if(client.gui.screen()!=null||p.containerMenu!=p.inventoryMenu||!p.containerMenu.getCarried().isEmpty()
                ||!weaponReady()||services.inventory().selectedSlot()!=weaponSlot())return Optional.of("Combat menu/tool boundary is not idle");
        if(ownedMechanicPending()||cocoonHeld||cocoonReplacement!=null||loop.targetId()>=0||pathActive||requestedAttack!=null||scheduledBusy||ownedProtocolPending||objectiveAbort
                ||lastObjective!=null&&!lastObjective.removed())return Optional.of("Combat target/ability/objective interaction pending");
        var feet=p.blockPosition();var support=p.getBlockPosBelowThatAffectsMyMovement();if(!client.level.isLoaded(feet)||!client.level.isLoaded(support)
                ||!client.level.isLoaded(feet.above())||client.level.getBlockState(support).getCollisionShape(client.level,support).isEmpty())
            return Optional.of("Combat standing support is not loaded");
        return Optional.empty();
    }
    private void releaseForScheduledPause(){navigation.stop();services.rotation().cancelRotation();
        requestedAttack=null;requestedWeapon=null;requestedWorld=null;requestedSlot=-1;services.breaking().setBreaking(false);
        CombatScheduledPause.releaseKeys(client);}
    private void rebindScheduledNative(){
        if(bestiaryWeapon!=null&&!bestiaryWeapon.rebind(scheduledPauseSignature())){setEnabled(false);return;}
        nativeOwner=client.player;nativeWorld=client.level;nativeConnection=client.getConnection();
        navigation=new McNavigator(client,services.input(),config);pathActive=false;lastPathGoal=null;pathTarget=-1;nextPath=0;
        requestedAttack=null;requestedWeapon=null;requestedWorld=null;requestedSlot=-1;
        loop.rebind(System.currentTimeMillis());awareness.reset();objectiveRequests.reset();lastObjective=null;
        ownedProtocolPending=objectiveAbort=scheduledBusy=false;abortObjective=null;objectiveFailure="";
        holyIce.reset();spring.reset();finisher.reset();dagger.reset();impel.reset(dev.veltraph.client.combat.slayer.ImpelSignal.read(client.level).serial());
        impelPitch.reset();impelTurning=false;if(!parentCamera)camera.reset();softAim.reset();movement.reset();
        services.breaking().installTargetGuard(this,p->false);
    }
    public boolean holdForOwnedCocoon(LivingEntity observedBoss){
        if(!isEnabled()||scheduledPaused()||client.player!=nativeOwner||client.level!=nativeWorld||client.getConnection()!=nativeConnection)return false;
        if(observedBoss!=null&&observedBoss.level()==nativeWorld&&observedBoss.getHealth()<=0)loop.confirmedDeath(observedBoss.getId());
        cocoonHeld=true;releaseForScheduledPause();phase="WAIT_OWN_COCOON";return true;
    }
    public boolean resumeOwnedCocoonBody(SelectedTarget replacement){
        if(!isEnabled()||scheduledPaused()||!cocoonHeld||replacement==null||client.player!=nativeOwner||client.level!=nativeWorld||client.getConnection()!=nativeConnection
                ||!replacement.body().isAlive()||client.level.getEntity(replacement.body().getId())!=replacement.body()||!weaponReady()
                ||services.inventory().selectedSlot()!=weaponSlot()||!CombatLoop.matches(replacement.observedName(),names()))return false;
        if(targets(24).stream().noneMatch(candidate->candidate.id()==replacement.body().getId()&&candidate.alive()&&!candidate.protectedTarget()
                &&candidate.name().equals(replacement.observedName())))return false;
        rebindScheduledNative();cocoonHeld=false;cocoonReplacement=replacement.body().getUUID();phase="OWN_COCOON_REPLACEMENT";return true;
    }
    private String names() { return id().equals("fishing_auto_killer")?config.get(FishingConfig.FISHING_KILL_TARGETS):names.isBlank()?config.get(CombatConfig.BESTIARY_TARGET_NAMES):names; }
    @Override public Optional<GameArea> requiredArea() { return Optional.ofNullable(area); }
    @Override public boolean requiresSkyBlock() { return true; }
    private int primaryWeaponSlot(){return bestiaryWeapon==null?config.get(CombatConfig.COMBAT_WEAPON_SLOT)-1:bestiaryWeapon.slot();}
    private int weaponSlot(){return finishing==null?primaryWeaponSlot():finishing.heldSlot(primaryWeaponSlot());}
    private boolean weaponReady() {
        if(bestiaryWeapon!=null)return bestiaryWeapon.valid(scheduledPauseSignature(),false);
        if(client.player==null||weaponSlot()<0||weaponSlot()>8) return false;
        var stack=client.player.getInventory().getItem(weaponSlot());
        return !stack.isEmpty() && (stack.is(ItemTags.SWORDS)||stack.is(ItemTags.AXES)||(stack.is(Items.FISHING_ROD)&&stack.getHoverName().getString().toLowerCase(Locale.ROOT).contains("whip")));
    }
    @Override public Optional<String> preStartCheck() {
        if(names().isBlank()) return Optional.of("Set Bestiary Target Names before starting.");
        if(bestiaryWeapon!=null)return bestiaryWeapon.prepare(scheduledPauseSignature());
        if(!weaponReady()) return Optional.of("Set Combat Weapon Slot (1-9) to a sword, axe or whip.");
        services.inventory().selectSlot(weaponSlot()); return Optional.empty();
    }
    @Override public void onEnable() {
        if(bestiaryWeapon!=null&&!bestiaryWeapon.commit(scheduledPauseSignature())){setEnabled(false);throw new IllegalStateException("Bestiary weapon proposal changed before activation; no tool selected.");}
        cocoonHeld=false;cocoonReplacement=null;
        scheduledPause.invalidate();nativeOwner=client.player;nativeWorld=client.level;nativeConnection=client.getConnection();scheduledBusy=false;
        services.breaking().installTargetGuard(this,p->false);
        requestedAttack=null;requestedWeapon=null;requestedWorld=null;requestedSlot=-1;
        try { ownedProtocolPending=objectiveAbort=false;abortObjective=null;objectiveFailure="";loop.reset();objectiveRequests.reset();holyIce.reset(); spring.reset();finisher.reset();impel.reset(dev.veltraph.client.combat.slayer.ImpelSignal.read(client.level).serial());impelPitch.reset();impelTurning=false;dagger.reset();if(!parentCamera)camera.reset();softAim.reset(); movement.reset(); awareness.reset(); services.rotation().cancelRotation(); pathActive=false;lastPathGoal=null; nextPath=0; pathTarget=-1; phase="LOCATE";
        } catch(RuntimeException|Error failure){services.breaking().clearTargetGuard(this);services.input().releaseAllMovement();throw failure;}
    }
    @Override public void onDisable() { if(finishing!=null)finishing.childStopping();finishingAwareness.reset();finishingBoundary=null;requestedFinishingEligible=false; if(bestiaryWeapon!=null)bestiaryWeapon.release();cocoonHeld=false;cocoonReplacement=null;scheduledPause.invalidate();nativeOwner=null;nativeWorld=null;nativeConnection=null;try { requestedAttack=null;requestedWeapon=null;requestedWorld=null;requestedSlot=-1;services.rotation().cancelRotation(); navigation.stop();objectiveRequests.reset();awareness.reset();lastObjective=null;holyIce.reset(); services.breaking().setBreaking(false); phase="STOPPED"; } finally {services.breaking().clearTargetGuard(this);} }
    @Override public void onTick() {
        if(scheduledPaused())return;
        if(cocoonHeld)return;
        var player=client.player;if(player==null) { tickCombat();return; }
        float yaw=player.getYRot(),pitch=player.getXRot();
        requestedAttack=null;requestedWeapon=null;requestedWorld=null;requestedSlot=-1;
        requestedFinishingEligible=false;impelTurning=false;tickCombat();
        if(isEnabled()&&client.player==player&&player.isAlive()&&client.gui.screen()==null) {
            double speed=config.get(RotationConfig.ROTATION_SPEED_MULTIPLIER);
            var angles=camera.apply(player.tickCount,yaw,pitch,dev.veltraph.client.service.mc.McOwnedRotation.requestedYaw(client),dev.veltraph.client.service.mc.McOwnedRotation.requestedPitch(client),speed,speed*(impelTurning?1.4:1));
            dev.veltraph.client.service.mc.McOwnedRotation.plan(client,angles.yaw(),angles.pitch());
            flushAttack();
        }
    }
    private void tickCombat() {
        if(client.player==null||client.level==null||client.player!=nativeOwner||client.level!=nativeWorld||client.getConnection()!=nativeConnection||client.gui.screen()!=null||!weaponReady()
                ||(!ownedMechanicPending()&&services.inventory().selectedSlot()!=weaponSlot())||client.player.isDeadOrDying()) { setEnabled(false); return; }
        double radius=clamp(config.get(CombatConfig.COMBAT_SEARCH_RADIUS),2,24,16);
        double reach=clamp(config.get(CombatConfig.COMBAT_MELEE_REACH),1,3,3);
        List<CombatTarget> observedCandidates=targets(radius);
        List<CombatTarget> candidates=targetFilter.candidates(observedCandidates);
        if(cocoonReplacement!=null)candidates=candidates.stream().filter(candidate->{var entity=client.level.getEntity(candidate.id());return entity!=null&&entity.getUUID().equals(cocoonReplacement);}).toList();
        boolean aligned=client.hitResult instanceof EntityHitResult hit && hit.getEntity().getId()==loop.targetId();
        long now=System.currentTimeMillis();
        int previousTarget=loop.targetId();
        var decision=loop.tick(candidates,new Vec3(client.player.getX(),client.player.getY(),client.player.getZ()),names(),reach,
                radius,aligned,client.player.getAttackStrengthScale(0)>=.95,now,config.get(CombatConfig.COMBAT_TARGET_TIMEOUT_MS));
        if(ownedProtocolPending&&previousTarget>=0&&loop.targetId()!=previousTarget) {
            setEnabled(false);phase="Owned objective lost its selected boss; controls released instead of switching encounters";return;
        }
        phase=decision.action().name();
        if(decision.target()==null) { if(finishing!=null&&finishing.borrowed()&&!finishing.restoreActive()){setEnabled(false);return;}navigation.stop();scheduledBusy=false;return; }
        var target=client.level.getEntity(decision.target().id());
        if(!(target instanceof LivingEntity mob)||!mob.isAlive()||!permittedNativeTarget(mob)) { navigation.stop(); return; }
        if(finishing!=null&&finishing.borrowed()&&!finishing.sameTarget(mob)&&!finishing.restoreActive()){setEnabled(false);return;}
        if(finishing!=null)finishing.observe(mob);
        Vec3 observedPosition=new Vec3(client.player.getX(),client.player.getY(),client.player.getZ());
        var observation=awareness.observe(client,mob,decision.target().name(),client.player.tickCount*50L,
                p->dev.veltraph.client.navigation.TravelPath.walkClear(navigation,observedPosition,p));
        scheduledBusy=observation.response()!=dev.veltraph.client.combat.slayer.SlayerSignals.Response.NONE
                ||observation.mania()||observation.twinclaws().active()||observation.suspendAttacks();
        var objectiveTask=observation.task();
        lastObjective=objectiveTask;
        if(objectiveAbort&&objectiveTask!=null&&objectiveTask.removed()&&objectiveTask.identity().equals(abortObjective)) {
            objectiveAbort=false;abortObjective=null;objectiveFailure="";
        }
        ownedProtocolPending=objectiveTask!=null&&!objectiveTask.removed();
        if(ownedProtocolPending&&!objectiveTask.present()) {
            objectiveAbort=true;abortObjective=objectiveTask.identity();objectiveFailure="Owned objective tracking unavailable; disengage and stop";
        }
        var response=objectiveAbort?(observation.mania()?dev.veltraph.client.combat.slayer.SlayerSignals.Response.SAFE_FLOOR
                :dev.veltraph.client.combat.slayer.SlayerSignals.Response.RETREAT):observation.response();
        boolean specialized=ownedMechanic!=null&&ownedMechanic.wants(mob,objectiveTask);
        boolean specializedEggs=ownedMechanic!=null&&ownedMechanic.suppresses(objectiveTask);
        if(finishing!=null&&finishing.borrowed()&&(scheduledBusy||ownedProtocolPending||objectiveAbort||specialized||specializedEggs)){
            if(!finishing.restoreActive()){setEnabled(false);return;}
        }
        boolean specializedSafety=false;
        var specializedTarget=specialized?ownedMechanic.aimTarget(mob,objectiveTask):null;
        if(specialized){
            boolean safety=specializedSafety=objectiveAbort||client.player.getHealth()<client.player.getMaxHealth()*.5||observation.suspendAttacks()
                    ||observation.twinclaws().active()||observation.mania()
                    ||response==dev.veltraph.client.combat.slayer.SlayerSignals.Response.RETREAT
                    ||response==dev.veltraph.client.combat.slayer.SlayerSignals.Response.ESCAPE;
            var eye=client.player.getEyePosition();var b=specializedTarget.getBoundingBox();
            var nearest=new net.minecraft.world.phys.Vec3(Math.clamp(eye.x,b.minX,b.maxX),Math.clamp(eye.y,b.minY,b.maxY),Math.clamp(eye.z,b.minZ,b.maxZ));
            boolean nativeRange=eye.distanceTo(nearest)<=ownedMechanic.range()-.15&&client.player.hasLineOfSight(specializedTarget);
            if(ownedMechanic.borrowing()||ownedMechanic.active()&&safety||!safety&&nativeRange){
                acquireMechanic();loop.preserveTarget(mob.getId(),now);
                ownedMechanic.tick(this,mob,objectiveTask,safety,now);phase=ownedMechanic.status();
                if(ownedMechanic.failed())setEnabled(false);
                return;
            }
            if(!safety)reach=Math.min(reach,Math.max(.5,ownedMechanic.range()-.5));
        }
        if(!objectiveAbort&&(objectiveTask!=null&&objectiveTask.present()&&!objectiveTask.removed()||observation.twinclaws().active()
                ||observation.mania()||response==dev.veltraph.client.combat.slayer.SlayerSignals.Response.RETREAT
                ||response==dev.veltraph.client.combat.slayer.SlayerSignals.Response.ESCAPE
                ||observation.entity()!=null&&(response==dev.veltraph.client.combat.slayer.SlayerSignals.Response.LOOK
                    ||response==dev.veltraph.client.combat.slayer.SlayerSignals.Response.SPRING)
                ||response==dev.veltraph.client.combat.slayer.SlayerSignals.Response.GLYPH))loop.preserveTarget(mob.getId(),now);
        var iceItems=new ArrayList<dev.veltraph.client.combat.slayer.HolyIceController.Item>();
        if(observation.twinclaws().active())for(int slot=0;slot<9;slot++) {
            var stack=client.player.getInventory().getItem(slot);var lore=stack.get(net.minecraft.core.component.DataComponents.LORE);
            iceItems.add(new dev.veltraph.client.combat.slayer.HolyIceController.Item(slot,stack.getHoverName().getString(),
                    lore==null?List.of():lore.lines().stream().map(line->line.getString()).toList(),
                    stack.is(Items.ICE)||stack.is(Items.PACKED_ICE)));
        }
        var ice=holyIce.tick(mob.getId(),observation.twinclaws(),iceItems,now);
        if(ice.use()&&client.gameMode!=null) {
            int previous=services.inventory().selectedSlot();services.inventory().selectSlot(ice.slot());
            try {client.gameMode.useItem(client.player,InteractionHand.MAIN_HAND);}
            finally {services.inventory().selectSlot(previous);}
        }
        var receipt=dev.veltraph.client.combat.slayer.ImpelSignal.read(client.level);
        var impelAction=dev.veltraph.client.combat.slayer.ImpelController.Action.NONE;
        if(dev.veltraph.client.combat.slayer.SlayerSignals.family(decision.target().name())==dev.veltraph.client.combat.slayer.SlayerSignals.Family.VAMPIRE
                && observation.mania()) {
            impelAction=impel.tick(receipt,now,client.player.getXRot(),client.player.onGround(),client.player.isShiftKeyDown());
        } else impel.reset(receipt.serial());
        impelTurning=impelAction==dev.veltraph.client.combat.slayer.ImpelController.Action.TURN_UP
                ||impelAction==dev.veltraph.client.combat.slayer.ImpelController.Action.TURN_DOWN||impelAction==dev.veltraph.client.combat.slayer.ImpelController.Action.CLICK;
        boolean overlappingSpring=observation.mania()&&observation.spring()!=null
                &&(response==dev.veltraph.client.combat.slayer.SlayerSignals.Response.SAFE_FLOOR
                    ||response==dev.veltraph.client.combat.slayer.SlayerSignals.Response.SPRING);
        var mechanicEntity=overlappingSpring?observation.spring():observation.entity();
        boolean springTask=response==dev.veltraph.client.combat.slayer.SlayerSignals.Response.SPRING||overlappingSpring;
        boolean retreat=response==dev.veltraph.client.combat.slayer.SlayerSignals.Response.RETREAT;
        boolean mechanicTarget=mechanicEntity!=null && (overlappingSpring ||response==dev.veltraph.client.combat.slayer.SlayerSignals.Response.SPRING || response==dev.veltraph.client.combat.slayer.SlayerSignals.Response.PILLAR || response==dev.veltraph.client.combat.slayer.SlayerSignals.Response.EGGS || response==dev.veltraph.client.combat.slayer.SlayerSignals.Response.ADDS || response==dev.veltraph.client.combat.slayer.SlayerSignals.Response.LOOK);
        boolean objective=observation.mania() || response==dev.veltraph.client.combat.slayer.SlayerSignals.Response.ICHOR || response==dev.veltraph.client.combat.slayer.SlayerSignals.Response.GLYPH
                ||response==dev.veltraph.client.combat.slayer.SlayerSignals.Response.SAFE_FLOOR
                ||response==dev.veltraph.client.combat.slayer.SlayerSignals.Response.ESCAPE;
        boolean special=response!=dev.veltraph.client.combat.slayer.SlayerSignals.Response.NONE;
        var player=client.player;
        Vec3 position=new Vec3(player.getX(),player.getY(),player.getZ());
        Vec3 boss=new Vec3(mob.getX(),mob.getY(),mob.getZ());
        Vec3 destination=observation.mania()?observation.safeFloor():observation.objective();
        if(objectiveAbort&&!observation.mania())destination=boss;
        if(destination==null)destination=boss;
        double distance=position.horizontalDistanceTo(retreat?destination:boss);
        // Pathfinding is only the obstacle fallback. Direct local steering follows the live entity each tick.
        boolean direct=dev.veltraph.client.navigation.TravelPath.walkClear(navigation,position,destination);
        if((!special||response==dev.veltraph.client.combat.slayer.SlayerSignals.Response.FINISH) && distance>reach && !direct) {
            var goal=destination.toBlockPos();
            if(!pathActive || now>=nextPath && !goal.equals(lastPathGoal)) {
                nextPath=now+750;pathTarget=mob.getId();lastPathGoal=goal;pathActive=navigation.start(goal);
            }
            if(pathActive) {
                var status=navigation.tick();
                if(status!=WalkController.State.WALKING) pathActive=false;
            } else services.input().releaseAllMovement();
            phase="APPROACH_AROUND_OBSTACLE";return;
        }
        if(pathActive) { navigation.stop();pathActive=false; }
        var eye=player.getEyePosition();var aimEntity=specialized&&specializedTarget!=null?specializedTarget:mechanicTarget?mechanicEntity:mob;var box=aimEntity.getBoundingBox();var point=box.getCenter();
        if(response==dev.veltraph.client.combat.slayer.SlayerSignals.Response.LOOK||springTask) point=aimEntity.getEyePosition();
        if(mechanicTarget||specialized)distance=position.horizontalDistanceTo(new Vec3(aimEntity.getX(),aimEntity.getY(),aimEntity.getZ()));
        // Aim within the hit volume, not at a fixed anatomical point. The soft window leaves valid aim alone.
        double dx=point.x-eye.x,dy=point.y-eye.y,dz=point.z-eye.z;
        float yaw=(float)Math.toDegrees(Math.atan2(-dx,dz));
        float pitch=(float)-Math.toDegrees(Math.atan2(dy,Math.hypot(dx,dz)));
        boolean near=distance<=reach+.5 && !retreat;
        double yawWindow=mechanicTarget?0:near?Math.toDegrees(Math.atan2(Math.max(.04,box.getXsize()*.27),Math.max(.5,distance))):clamp(config.get(CombatConfig.COMBAT_VIEW_WINDOW),5,35,18);
        double pitchWindow=mechanicTarget?0:near?Math.toDegrees(Math.atan2(box.getYsize()*.22,Math.max(.5,distance))):15;
        var angles=softAim.step(player.getYRot(),player.getXRot(),yaw,pitch,yawWindow,pitchWindow,
                config.get(RotationConfig.ROTATION_SPEED_MULTIPLIER));
        services.rotation().aim(angles.yaw(),impelTurning?player.getXRot():angles.pitch());
        double retreatDistance=clamp(config.get(CombatConfig.COMBAT_RETREAT_DISTANCE),7,12,8);
        double combatRadius=retreat?retreatDistance:objective?0:Math.max(1.6,reach-.65);
        if(specialized&&!specializedSafety)combatRadius=Math.min(combatRadius,Math.max(.1,ownedMechanic.range()-.5));
        boolean hold=retreat&&distance>=retreatDistance || objective&&(observation.mania()?observation.safeFloor()!=null:observation.objective()!=null)&&position.horizontalDistanceTo(destination)<.45&&Math.hypot(player.getDeltaMovement().x,player.getDeltaMovement().z)<.02;
        if(response==dev.veltraph.client.combat.slayer.SlayerSignals.Response.LOOK) hold=true;
        if(springTask&&!observation.mania()&&distance<reach-.2&&player.hasLineOfSight(aimEntity))hold=true;
        if(response==dev.veltraph.client.combat.slayer.SlayerSignals.Response.PILLAR&&objectiveTask!=null
                &&distance<reach-.2&&player.hasLineOfSight(aimEntity)&&projectileRayHitsOwnedBody(objectiveTask.bodies(),reach)
                &&client.hitResult instanceof EntityHitResult pillarHit&&objectiveTask.bodies().contains(pillarHit.getEntity().getUUID()))hold=true;
        if(!special && near && Math.abs(WalkController.wrap(yaw-angles.yaw()))>16) hold=true;
        if(observation.mania()&&observation.safeFloor()==null)hold=true;
        if(response==dev.veltraph.client.combat.slayer.SlayerSignals.Response.ESCAPE&&observation.objective()==null)hold=true;
        var otherMobs=observedCandidates.stream().filter(t->t.id()!=mob.getId()&&t.alive())
                .map(CombatTarget::position).filter(p->p.horizontalDistanceTo(position)<4).toList();
        var keys=movement.choose(position,destination,angles.yaw(),combatRadius,retreat||objective,hold,next->{
            if(!dev.veltraph.client.navigation.TravelPath.walkClear(navigation,position,next)) return false;
            // Check momentum beyond the input decision as well as the intended step.
            var velocity=player.getDeltaMovement();
            Vec3 predicted=new Vec3(next.x()+velocity.x*2,next.y(),next.z()+velocity.z*2);
            if(!dev.veltraph.client.navigation.TravelPath.walkClear(navigation,position,predicted)) return false;
            if(response==dev.veltraph.client.combat.slayer.SlayerSignals.Response.ESCAPE
                    &&!client.level.getBlockState(player.blockPosition().below()).is(net.minecraft.world.level.block.Blocks.BEDROCK)) {
                var nextFloor=next.toBlockPos().down();var predictedFloor=predicted.toBlockPos().down();
                if(client.level.getBlockState(new net.minecraft.core.BlockPos(nextFloor.x(),nextFloor.y(),nextFloor.z())).is(net.minecraft.world.level.block.Blocks.BEDROCK)
                        ||client.level.getBlockState(new net.minecraft.core.BlockPos(predictedFloor.x(),predictedFloor.y(),predictedFloor.z())).is(net.minecraft.world.level.block.Blocks.BEDROCK))return false;
            }
            if(observation.mania()&&client.level.getBlockState(player.blockPosition().below()).is(net.minecraft.world.level.block.Blocks.DYED_TERRACOTTA.green())) {
                var floor=next.toBlockPos().down();var predictedFloor=predicted.toBlockPos().down();
                if(!client.level.getBlockState(new net.minecraft.core.BlockPos(floor.x(),floor.y(),floor.z())).is(net.minecraft.world.level.block.Blocks.DYED_TERRACOTTA.green())
                        ||!client.level.getBlockState(new net.minecraft.core.BlockPos(predictedFloor.x(),predictedFloor.y(),predictedFloor.z())).is(net.minecraft.world.level.block.Blocks.DYED_TERRACOTTA.green()))return false;
            }
            return dev.veltraph.client.combat.slayer.EnvironmentalHazardEscape.permitsAdvance(position,next,observation.danger())
                    &&dev.veltraph.client.combat.slayer.EnvironmentalHazardEscape.permitsAdvance(position,predicted,observation.danger());
        },otherMobs,new Vec3(player.getDeltaMovement().x,player.getDeltaMovement().y,player.getDeltaMovement().z));
        services.input().releaseAllMovement();
        services.input().setForwardHeld(keys.forward());services.input().setBackHeld(keys.back());
        services.input().setLeftHeld(keys.left());services.input().setRightHeld(keys.right());
        phase=special?observation.detail():distance>reach?"APPROACH":"ORBIT_AND_ATTACK";
        if(objectiveAbort) {
            boolean onGreen=client.level.getBlockState(player.blockPosition().below()).is(net.minecraft.world.level.block.Blocks.DYED_TERRACOTTA.green());
            boolean blocked=!(keys.forward()||keys.back()||keys.left()||keys.right());
            phase=objectiveFailure;
            if(observation.mania()?onGreen&&Math.hypot(player.getDeltaMovement().x,player.getDeltaMovement().z)<.03:distance>=retreatDistance||blocked) {
                setEnabled(false);phase=objectiveFailure+" | Stopped with controls released";return;
            }
        }
        if(observation.twinclaws().active()&&!ice.detail().isBlank())phase+=" | "+ice.detail();
        if(impelAction!=dev.veltraph.client.combat.slayer.ImpelController.Action.NONE) {
            phase="Impel: "+impelAction;
            switch(impelAction) {
                case JUMP -> { if(navigation.clear(position.toBlockPos().up(2)))services.input().setJumpHeld(true);else phase="Impel: jump blocked by ceiling"; }
                case SNEAK -> services.input().setSneakHeld(true);
                case TURN_UP,TURN_DOWN -> dev.veltraph.client.service.mc.McOwnedRotation.planPitch(client,impelPitch.step(player.getXRot(),impelAction==dev.veltraph.client.combat.slayer.ImpelController.Action.TURN_UP?-82:82,
                        config.get(RotationConfig.ROTATION_SPEED_MULTIPLIER)*1.4));
                case CLICK -> swingAndNotify();
                case EXPIRED -> phase="Impel expired before action completed";
                case NONE -> {}
            }
            return;
        }
        impelPitch.reset();
        if(specialized||specializedEggs){phase="Tara native approach / phase admission | "+(ownedMechanic==null?"":ownedMechanic.status());return;}
        boolean objectiveControlled=objectiveTask!=null&&(objectiveTask.kind()==dev.veltraph.client.combat.slayer.SlayerObjectives.Kind.EGGS
                &&response==dev.veltraph.client.combat.slayer.SlayerSignals.Response.EGGS
                ||objectiveTask.kind()==dev.veltraph.client.combat.slayer.SlayerObjectives.Kind.PILLAR
                &&response==dev.veltraph.client.combat.slayer.SlayerSignals.Response.PILLAR);
        int shortbow=-1;
        if(objectiveTask!=null&&objectiveTask.kind()==dev.veltraph.client.combat.slayer.SlayerObjectives.Kind.EGGS)
            for(int slot=0;slot<9;slot++)if(player.getInventory().getItem(slot).is(Items.BOW)
                    &&player.getInventory().getItem(slot).getHoverName().getString().toLowerCase(Locale.ROOT).contains("shortbow")) {shortbow=slot;break;}
        boolean requestReady=objectiveControlled&&objectiveTask.actionable()&&objectiveTask.target()==aimEntity&&client.gameMode!=null
                &&player.hasLineOfSight(aimEntity);
        if(requestReady&&objectiveTask.kind()==dev.veltraph.client.combat.slayer.SlayerObjectives.Kind.EGGS) {
            var intercept=projectileOwnedIntercept(objectiveTask.bodies(),6);
            requestReady=shortbow>=0&&distance<=6&&intercept.isPresent()
                    &&projectileCorridorClear(aimEntity,intercept.orElse(point),objectiveTask.bodies());
        }
        else if(requestReady)requestReady=client.hitResult instanceof EntityHitResult hit&&objectiveTask.bodies().contains(hit.getEntity().getUUID())
                    &&player.hasLineOfSight(hit.getEntity())&&hit.getLocation().distanceTo(eye)<=reach
                    &&projectileRayHitsOwnedBody(objectiveTask.bodies(),reach);
        boolean lethalHold=objectiveAbort||retreat||observation.suspendAttacks()||observation.twinclaws().active();
        var objectiveDecision=objectiveRequests.tick(objectiveTask==null?null:objectiveTask.identity(),objectiveTask==null?-1:objectiveTask.progress(),
                objectiveTask!=null&&objectiveTask.present(),requestReady,lethalHold,objectiveTask!=null&&objectiveTask.removed(),now);
        if(objectiveDecision.state()==dev.veltraph.client.combat.slayer.ObjectiveRequests.State.EXPIRED) {
            objectiveAbort=true;abortObjective=objectiveTask.identity();objectiveFailure=objectiveDecision.detail();
            services.input().releaseAllMovement();phase=objectiveFailure;return;
        }
        if(objectiveTask!=null&&!lethalHold) {
            phase=objectiveTask.detail()+" | "+objectiveDecision.detail();
            if(objectiveTask.kind()==dev.veltraph.client.combat.slayer.SlayerObjectives.Kind.EGGS&&shortbow<0&&!objectiveTask.removed())phase="Owned egg objective requires a Shortbow in the hotbar; server removal pending";
            else if(objectiveTask.kind()==dev.veltraph.client.combat.slayer.SlayerObjectives.Kind.EGGS&&!requestReady&&!objectiveTask.removed())phase="Owned egg body: wait for clear physical shot volume";
        }
        if(lethalHold)return; // all entity attacks stay suspended while the actual hazard remains
        if(objectiveDecision.state()==dev.veltraph.client.combat.slayer.ObjectiveRequests.State.ACKNOWLEDGED)return;
        if(objectiveControlled) {
            if(objectiveDecision.state()==dev.veltraph.client.combat.slayer.ObjectiveRequests.State.REQUEST) {
                if(objectiveTask.kind()==dev.veltraph.client.combat.slayer.SlayerObjectives.Kind.EGGS) {
                    int previous=services.inventory().selectedSlot();services.inventory().selectSlot(shortbow);
                    try {client.gameMode.useItem(player,InteractionHand.MAIN_HAND);}
                    finally {player.stopUsingItem();services.inventory().selectSlot(previous);}
                } else if(client.hitResult instanceof EntityHitResult hit)attackAndNotify(hit.getEntity());
            }
            return;
        }
        if(springTask&&client.gameMode!=null) {
            EntityHitResult springHit=client.hitResult instanceof EntityHitResult hit&&hit.getEntity()==aimEntity?hit:null;
            boolean hittable=springHit!=null&&player.hasLineOfSight(aimEntity)&&springHit.getLocation().distanceTo(eye)<=reach-.25
                    &&Math.hypot(player.getDeltaMovement().x,player.getDeltaMovement().z)<.03
                    &&springHit.getLocation().y>=aimEntity.getY()+aimEntity.getBbHeight()*.65;
            switch(spring.tick(aimEntity.getId(),hittable,now)) {
                case ATTACK -> attackAndNotify(aimEntity);
                case INTERACT -> client.gameMode.interact(player,aimEntity,springHit,InteractionHand.MAIN_HAND);
                case NONE -> {}
            }
            phase=hittable?"Compressing Killer Spring":"Approaching/aiming Spring head";
            return;
        }
        if(mechanicTarget && response==dev.veltraph.client.combat.slayer.SlayerSignals.Response.ADDS && now-lastSpecialAction>250 && client.gameMode!=null) {
            if(client.hitResult instanceof EntityHitResult hit&&hit.getEntity()==aimEntity&&hit.getLocation().distanceTo(eye)<=reach
                    && player.getAttackStrengthScale(0)>=.95) { attackAndNotify(aimEntity);lastSpecialAction=now; }
        }
        if(response==dev.veltraph.client.combat.slayer.SlayerSignals.Response.ATTUNEMENT && client.gameMode!=null) {
            var items=new ArrayList<dev.veltraph.client.combat.slayer.DaggerAttunement.Item>();
            for(int i=0;i<9;i++) {
                var stack=player.getInventory().getItem(i);var lore=stack.get(net.minecraft.core.component.DataComponents.LORE);
                if(!stack.is(ItemTags.SWORDS)&&!stack.is(ItemTags.AXES))continue;
                items.add(new dev.veltraph.client.combat.slayer.DaggerAttunement.Item(i,stack.getHoverName().getString(),
                        lore==null?List.of():lore.lines().stream().map(line->line.getString()).toList()));
            }
            var choice=dagger.tick(observation.detail(),items,now,dev.veltraph.client.combat.slayer.SlayerTitleSignal.read(client.level));phase=choice.status();
            if(choice.action()==dev.veltraph.client.combat.slayer.DaggerAttunement.Action.SWITCH) {
                services.inventory().selectSlot(choice.slot());client.gameMode.useItem(player,InteractionHand.MAIN_HAND);
                services.inventory().selectSlot(weaponSlot());lastSpecialAction=now;
            } else if(choice.action()==dev.veltraph.client.combat.slayer.DaggerAttunement.Action.READY && now-lastSpecialAction>150
                    && player.hasLineOfSight(mob) && client.hitResult instanceof EntityHitResult hit&&hit.getEntity()==mob&&hit.getLocation().distanceTo(eye)<=reach) {
                services.inventory().selectSlot(choice.slot());attackAndNotify(mob);services.inventory().selectSlot(weaponSlot());lastSpecialAction=now;
            }
        }
        if(response==dev.veltraph.client.combat.slayer.SlayerSignals.Response.HITSHIELD && now-lastSpecialAction>=100
                && client.gameMode!=null && player.hasLineOfSight(mob) && client.hitResult instanceof EntityHitResult shieldHit
                && shieldHit.getEntity()==mob && shieldHit.getLocation().distanceTo(eye)<=reach) {
            attackAndNotify(mob);lastSpecialAction=now;
        }
        boolean finishFallback=false;
        if(response==dev.veltraph.client.combat.slayer.SlayerSignals.Response.FINISH) {
            boolean hittable=client.gameMode!=null&&player.hasLineOfSight(mob)&&client.hitResult instanceof EntityHitResult hit
                    &&hit.getEntity()==mob&&hit.getLocation().distanceTo(eye)<=reach;
            var finish=finisher.tick(mob.getId(),true,services.inventory().hotbarItemNames(),hittable,now);
            phase=finish.status();
            if(finish.action()==dev.veltraph.client.combat.slayer.VampireFinisher.Action.ATTACK) {
                int previous=services.inventory().selectedSlot();services.inventory().selectSlot(finish.slot());
                try { attackAndNotify(mob); } finally {services.inventory().selectSlot(previous);}
            }
            finishFallback=finish.action()==dev.veltraph.client.combat.slayer.VampireFinisher.Action.MELEE;
        }
        if((!special||finishFallback) && decision.action()==CombatLoop.Action.ATTACK && player.hasLineOfSight(mob)
                && client.hitResult instanceof EntityHitResult hit && hit.getEntity()==mob
                && hit.getLocation().distanceTo(eye)<=reach && client.gameMode!=null) {
            attackAndNotify(mob);
            requestedFinishingEligible=!special&&!scheduledBusy&&!ownedProtocolPending&&!objectiveAbort&&!ownedMechanicPending();
        }
    }

    private void swingAndNotify() {
        client.player.swing(InteractionHand.MAIN_HAND,net.minecraft.world.item.component.SwingAnimation.DEFAULT,false);
        client.player.connection.send(net.minecraft.network.protocol.game.ServerboundPunchPacket.INSTANCE);
    }
    private void attackAndNotify(net.minecraft.world.entity.Entity entity) {
        if(client.player==null||client.level==null||entity==null||!permittedNativeTarget(entity))return;
        requestedAttack=entity;requestedWorld=client.level;requestedSlot=services.inventory().selectedSlot();
        requestedWeapon=client.player.getMainHandItem().copy();
    }
    /** The last actually applied pose, never the pending endpoint, authorizes the physical ray. */
    private void flushAttack(){
        var target=requestedAttack;var weapon=requestedWeapon;int slot=requestedSlot;
        var world=requestedWorld;
        requestedAttack=null;requestedWeapon=null;requestedSlot=-1;requestedWorld=null;
        if(!isEnabled()||scheduledPaused()||cocoonHeld||target==null||weapon==null||weapon.isEmpty()||client.gameMode==null||client.player==null||client.level!=world
                ||client.player!=nativeOwner||client.level!=nativeWorld||client.getConnection()!=nativeConnection
                ||client.gui.screen()!=null||!client.player.isAlive()||target==client.player||!target.isAlive()||!target.isPickable()
                ||client.level.getEntity(target.getId())!=target||slot<0||slot>8
                ||!net.minecraft.world.item.ItemStack.isSameItemSameComponents(weapon,client.player.getInventory().getItem(slot))
                ||!permittedNativeTarget(target))return;
        var eye=client.player.getEyePosition();var ray=client.player.getLookAngle();
        var box=target.getBoundingBox().inflate(target.getPickRadius());
        // Target rendering still interpolates native entity positions. The received body is a read-only server observation;
        // it must also intersect before a cached render hit may authorize an attack.
        var receivedBox=box.move(target.getClientPosition().subtract(target.position()));
        double reach=clamp(config.get(CombatConfig.COMBAT_MELEE_REACH),1,3,3);
        if(!dev.veltraph.client.combat.slayer.CombatHitEnvelope.intersects(
                new Vec3(eye.x,eye.y,eye.z),new Vec3(ray.x,ray.y,ray.z),
                new Vec3(box.minX,box.minY,box.minZ),new Vec3(box.maxX,box.maxY,box.maxZ),reach)
                ||!dev.veltraph.client.combat.slayer.CombatHitEnvelope.intersects(
                    new Vec3(eye.x,eye.y,eye.z),new Vec3(ray.x,ray.y,ray.z),
                    new Vec3(receivedBox.minX,receivedBox.minY,receivedBox.minZ),
                    new Vec3(receivedBox.maxX,receivedBox.maxY,receivedBox.maxZ),reach))return;
        // Native mob updates span several simulation steps. Future client-player motion cannot
        // authorize a request that misses from the actual stationary packet eye during that gap.
        var targetMotion=target.getKnownSpeed();var playerMotion=client.player.getKnownMovement();
        if(!dev.veltraph.client.combat.slayer.CombatHitEnvelope.observedCadence(
                new Vec3(eye.x,eye.y,eye.z),new Vec3(ray.x,ray.y,ray.z),
                new Vec3(receivedBox.minX,receivedBox.minY,receivedBox.minZ),new Vec3(receivedBox.maxX,receivedBox.maxY,receivedBox.maxZ),
                new Vec3(targetMotion.x,targetMotion.y,targetMotion.z),new Vec3(playerMotion.x,playerMotion.y,playerMotion.z),target.getType().updateInterval(),reach))return;
        var crossing=box.contains(eye)?Optional.of(eye):box.clip(eye,eye.add(ray.scale(reach)));
        if(crossing.isEmpty())return;
        var point=crossing.get();double hitDistance=eye.distanceTo(point);
        for(var other:client.level.entitiesForRendering())if(other!=client.player&&other!=target&&other.isAlive()&&other.isPickable()){
            var otherBox=other.getBoundingBox().inflate(other.getPickRadius());
            var otherHit=otherBox.contains(eye)?Optional.of(eye):otherBox.clip(eye,eye.add(ray.scale(reach)));
            if(otherHit.isPresent()&&eye.distanceTo(otherHit.get())<=hitDistance-1e-6)return;
        }
        int samples=Math.max(1,(int)Math.ceil(eye.distanceTo(point)*4));
        for(int sample=0;sample<=samples;sample++){
            var cell=net.minecraft.core.BlockPos.containing(eye.lerp(point,(double)sample/samples));
            if(!client.level.isLoaded(cell))return;
        }
        if(client.level.clip(new net.minecraft.world.level.ClipContext(eye,point,
                net.minecraft.world.level.ClipContext.Block.COLLIDER,net.minecraft.world.level.ClipContext.Fluid.NONE,client.player))
                .getType()!=net.minecraft.world.phys.HitResult.Type.MISS)return;
        if(requestedFinishingEligible&&finishing!=null&&target instanceof LivingEntity living){
            var named=selectedNamedCombatTarget().filter(value->value.body()==living).orElse(null);
            if(named==null)return;
            var boundary=new FinishingBoundary(client,living,named.observedName());finishingBoundary=boundary;
            try {
                var selected=finishing.selectForActualBoundary(living,boundary);
                if(selected==McSlayerFinishingSwap.Selection.REVOKED){setEnabled(false);return;}
                if(selected==McSlayerFinishingSwap.Selection.FINISHING){
                    var nativeAttack=finishing.beginActualAttack(living,boundary);if(nativeAttack==null){setEnabled(false);return;}
                    boolean returned=false;
                    try{client.player.connection.send(new net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.Rot(client.player.getYRot(),client.player.getXRot(),client.player.onGround(),client.player.horizontalCollision));
                        if(!finishing.beforeActualAttack(nativeAttack))return;
                        client.gameMode.attack(client.player,living);returned=true;
                    }finally{if(!finishing.finishActualAttack(nativeAttack,returned))setEnabled(false);}
                    if(isEnabled())swingAndNotify();return;
                }
            }finally{if(finishingBoundary==boundary)finishingBoundary=null;}
        }
        int previous=services.inventory().selectedSlot();
        boolean fishingHeldLoan=parentWeaponDeclaration==ParentWeaponDeclaration.FISHING_AUTO_KILLER;
        if(fishingHeldLoan&&(bestiaryWeapon==null||previous!=slot||bestiaryWeapon.slot()!=slot
                ||!bestiaryWeapon.valid(scheduledPauseSignature(),true)))return;
        // The genuine Fishing loan already selected this exact native slot. Another setter revokes its history.
        if(!fishingHeldLoan)services.inventory().selectSlot(slot);
        try {
            // Publish the already bounded actual view before interaction. Position remains owned
            // by vanilla, which permits only one position-bearing update per client tick.
            client.player.connection.send(new net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.Rot(
                    client.player.getYRot(),client.player.getXRot(),client.player.onGround(),client.player.horizontalCollision));
            // The original packet call may cross a callback boundary. Revalidate actual custody before attacking.
            if(fishingHeldLoan&&(bestiaryWeapon.slot()!=slot||!bestiaryWeapon.valid(scheduledPauseSignature(),true)))return;
            client.gameMode.attack(client.player,target);swingAndNotify();
            if(bestiaryWeapon!=null)bestiaryWeapon.issuedAttack();
        }
        finally {if(!fishingHeldLoan)services.inventory().selectSlot(previous);}
    }
    /** A cached render pose cannot authorize a body whose freshly received position left the bounds. */
    private boolean permittedNativeTarget(net.minecraft.world.entity.Entity entity){
        if(entity==null||client.level==null||client.level.getEntity(entity.getId())!=entity||!sourceReachable(entity))return false;
        if(!targetFilter.bounded())return true;
        CombatTarget observed=targets(clamp(config.get(CombatConfig.COMBAT_SEARCH_RADIUS),2,24,16)).stream()
                .filter(candidate->candidate.id()==entity.getId()).findFirst().orElseGet(()->{
                    String name=entity.getName().getString();
                    return new CombatTarget(entity.getId(),name,new Vec3(entity.getX(),entity.getY(),entity.getZ()),entity.isAlive(),
                            name.contains("[NPC]")||name.toLowerCase(Locale.ROOT).contains("hunting this monster"));
                });
        if(!targetFilter.accepts(observed))return false;
        var received=entity.getClientPosition();
        return targetFilter.accepts(new CombatTarget(observed.id(),observed.name(),new Vec3(received.x,received.y,received.z),
                observed.alive(),observed.protectedTarget()));
    }
    private boolean projectileCorridorClear(net.minecraft.world.entity.Entity intended,net.minecraft.world.phys.Vec3 point,Set<UUID> permitted) {
        var eye=client.player.getEyePosition();double targetDistance=eye.distanceTo(point);
        for(var entity:client.level.entitiesForRendering())if(entity!=client.player&&entity!=intended&&!permitted.contains(entity.getUUID())&&entity.isAlive()&&entity.isPickable()) {
            var crossing=entity.getBoundingBox().inflate(.15).clip(eye,point);
            if(crossing.isPresent()&&eye.distanceTo(crossing.get())<targetDistance-.05)return false;
        }
        return true;
    }
    private boolean projectileRayHitsOwnedBody(Set<UUID> bodies,double range) {
        return projectileOwnedIntercept(bodies,range).isPresent();
    }
    private Optional<net.minecraft.world.phys.Vec3> projectileOwnedIntercept(Set<UUID> bodies,double range) {
        var eye=client.player.getEyePosition();var end=eye.add(client.player.getViewVector(1).scale(range));
        net.minecraft.world.phys.Vec3 nearest=null;
        for(var e:client.level.entitiesForRendering())if(bodies.contains(e.getUUID())&&e.isAlive()) {
            var crossing=e.getBoundingBox().inflate(.02).clip(eye,end);
            if(crossing.isPresent()&&(nearest==null||eye.distanceTo(crossing.get())<eye.distanceTo(nearest)))nearest=crossing.get();
        }
        return Optional.ofNullable(nearest);
    }

    public List<CombatTarget> targets(double radius) {
        if(client.player==null||client.level==null) return List.of();
        List<LivingEntity> mobs=new ArrayList<>();
        List<ArmorStand> labels=new ArrayList<>();
        for(var e:client.level.entitiesForRendering()) {
            if(e.distanceToSqr(client.player)>(radius+4)*(radius+4)) continue;
            if(e instanceof ArmorStand label && label.hasCustomName()) labels.add(label);
            else if(e instanceof LivingEntity mob && (!(e instanceof Player) || CombatLoop.matches("Riftstalker Bloodfiend",names())
                    && client.getConnection()!=null && client.getConnection().getPlayerInfo(e.getUUID())==null)) mobs.add(mob);
        }
        List<CombatTarget> candidates=new ArrayList<>();
        for(var mob:mobs) {
            String label=mob.getName().getString();
            // Hypixel nameplates are separate armor stands. Only associate an unambiguous nearby label.
            var nearby=labels.stream().filter(l->l.getBoundingBox().inflate(.6,2,.6).intersects(mob.getBoundingBox()))
                    .filter(l->CombatLoop.matches(l.getName().getString(),names())).toList();
            if(nearby.size()==1) {
                var plate=nearby.get(0);
                long owners=mobs.stream().filter(m->plate.getBoundingBox().inflate(.6,2,.6).intersects(m.getBoundingBox())).count();
                var family=dev.veltraph.client.combat.slayer.SlayerSignals.family(label);
                boolean sameBossFamily=family!=dev.veltraph.client.combat.slayer.SlayerSignals.Family.OTHER
                        &&family==dev.veltraph.client.combat.slayer.SlayerSignals.family(plate.getName().getString());
                if(owners==1&&(!CombatLoop.matches(label,names())||sameBossFamily))label=plate.getName().getString();
            }
            // Recovered ordinary eligibility reads the shared native-identity ledger before protected-target rejection.
            // Membership is intentionally independent of clock; only the source path wrapper performs cleanup.
            if(!sourceReachable(mob))continue;
            boolean protectedMob=label.contains("[NPC]") || label.toLowerCase(Locale.ROOT).contains("hunting this monster");
            candidates.add(new CombatTarget(mob.getId(),label,new Vec3(mob.getX(),mob.getY(),mob.getZ()),mob.isAlive(),protectedMob));
        }
        return candidates;
    }
    public boolean hasMatchingTargets() {
        return targets(clamp(config.get(CombatConfig.COMBAT_SEARCH_RADIUS),2,24,16)).stream()
                .anyMatch(t->targetFilter.accepts(t)&&t.alive()&&!t.protectedTarget()&&CombatLoop.matches(t.name(),names()));
    }
    public Optional<dev.veltraph.client.combat.slayer.SlayerObjectives.Task> currentObjective() {return Optional.ofNullable(lastObjective);}
    private static double clamp(double x,double min,double max,double fallback) { return Double.isFinite(x)?Math.max(min,Math.min(max,x)):fallback; }
    @Override public String diagnostics() { return id()+" | "+phase+" | target="+loop.targetId()+" | confirmed kills="+loop.kills()+" | path="+navigation.status(); }
}
