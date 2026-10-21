package dev.veltraph.client.combat.slayer;

import dev.veltraph.client.config.*;
import dev.veltraph.client.core.*;
import dev.veltraph.client.gui.*;
import dev.veltraph.client.combat.grind.MeleeGrindModule;
import dev.veltraph.client.service.FarmServices;
import dev.veltraph.client.log.VeltraphLog;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import java.util.*;

/** Batphone acquisition/cancellation + confirmed-active combat + reward/restart loop. */
public final class SlayerModule extends AbstractModule {
    public static final String ID="auto_slayer";
    private final Minecraft client;private final ConfigService config;private final FarmServices services;
    private final McMenuAccess menus;private final SlayerQuestController quest=new SlayerQuestController();
    private final SlayerQuestLoot loot;private boolean completionWaiting;private int confirmedDropUnits;private String lootSetup="";
    private final McSlayerQuestTierObserver questTier;
    private final SlayerCocoon cocoon;private boolean cocoonBound,deferredCompletion;private int cocoonTransitions;private String cocoonSetup="";
    private final dev.veltraph.client.combat.grind.CombatCamera camera=new dev.veltraph.client.combat.grind.CombatCamera();
    private final dev.veltraph.client.combat.slayer.tara.McTaraParent tara;
    private String taraSetup="";private final List<String> taraDeferredMessages=new ArrayList<>();
    public boolean taraPending(){return tara.active();}public String taraStatus(){return tara.status();}public int taraCompletedGroups(){return tara.completed();}
    /** Read-only child identity for native same-child continuation verification. */
    public Object combatChildIdentity(){return combat;}
    /** Native observed submitted-tier/confirmation/start sequence; public chat/state alone cannot mint it. */
    public Optional<McSlayerQuestTierObserver.NativeQuestTier> observedQuestTier(){return questTier.evidence();}
    public String questTierDiagnostics(){return questTier.diagnostics();}
    boolean matchesQuestTierController(Object actualController){return actualController==quest;}
    boolean matchesQuestTierNativeContext(Object actualSettings,Object player,Object world,Object connection){
        return isEnabled()&&!scheduledPaused()&&settings==actualSettings&&owner==player&&ownerLevel==world
                &&ownerConnection==connection&&client.player==owner&&client.level==ownerLevel
                &&client.getConnection()==ownerConnection&&activeSetup!=null&&activeSetup.current(config);
    }
    private enum FinishingSourceChild { SPIDER, ZOMBIE_BELOW_65, ADULT_WOLF, ENDERMAN }
    /** Opaque declaration issued only by this actual parent for its actual newly constructed child.
     * It is neither an externally supplied family enum nor proof of a configured requested tier. */
    public static final class FinishingParentBinding {
        private final SlayerModule issuer;private final MeleeGrindModule child;private final SlayerSettings declared;
        private final FinishingSourceChild sourceChild;
        private FinishingParentBinding(SlayerModule issuer,MeleeGrindModule child,SlayerSettings declared,FinishingSourceChild sourceChild){this.issuer=issuer;this.child=child;this.declared=declared;this.sourceChild=sourceChild;}
        public SlayerModule parent(){return issuer;}
        public boolean belongsTo(MeleeGrindModule actual){return child==actual&&issuer.combat==actual&&issuer.finishingBinding==this;}
        /** Known source subtype predicate plus explicitly declared modern native boss names; no inferred source salt. */
        public boolean targetLeaf(MeleeGrindModule actual,net.minecraft.world.entity.LivingEntity target,String observedName){
            if(!belongsTo(actual)||target==null||!SlayerFinishingNames.matches(declared.bossLabel(),observedName))return false;
            return switch(sourceChild){
                case SPIDER -> target instanceof net.minecraft.world.entity.monster.spider.Spider;
                case ZOMBIE_BELOW_65 -> target instanceof net.minecraft.world.entity.monster.zombie.Zombie&&target.getY()<65;
                case ADULT_WOLF -> target instanceof net.minecraft.world.entity.animal.wolf.Wolf wolf&&!wolf.isBaby();
                case ENDERMAN -> target instanceof net.minecraft.world.entity.monster.Enderman;
            };
        }
    }
    private FinishingParentBinding finishingBinding;
    private Object finishingLootHandoff;
    private static FinishingSourceChild finishingSourceChild(SlayerSettings actual,boolean tara){
        if(tara)return null;
        return switch(actual.bossLabel()){
            case "Tarantula Broodfather" -> FinishingSourceChild.SPIDER;
            case "Revenant Horror" -> FinishingSourceChild.ZOMBIE_BELOW_65;
            case "Sven Packmaster" -> FinishingSourceChild.ADULT_WOLF;
            case "Voidgloom Seraph" -> FinishingSourceChild.ENDERMAN;
            default -> null; // No recovered marker-interface dispatch for Blaze/Vampire/unknown parents.
        };
    }
    /** Actual bound parent/child, cached settings and received chosen tier; labels alone grant nothing. */
    public boolean finishingBindingCurrent(MeleeGrindModule actual,McSlayerQuestTierObserver.NativeQuestTier tier){
        var proven=tier==null?observedQuestTier().orElse(null):tier;
        if(proven==null||!proven.belongsTo(this))return false; // All role/provider callbacks first.
        return finishingBindingLeafCurrent(actual,proven);
    }
    /** Exact cached private flags and real tier after all callbacks; never evidence()/current()/belongsTo(). */
    public boolean finishingBindingLeafCurrent(MeleeGrindModule actual,McSlayerQuestTierObserver.NativeQuestTier tier){
        var binding=finishingBinding;var proven=tier;
        return binding!=null&&binding.belongsTo(actual)&&isEnabled()&&!scheduledPaused()&&settings!=null&&settings.equals(binding.declared)
                &&quest.state()==SlayerQuestController.State.ACTIVE&&!completionWaiting&&!cocoon.transactionPending()&&!tara.active()
                &&!config.get(TaraConfig.TARA_USE_SOUL_WHIP)
                &&proven!=null&&proven.belongsToLeaf(this)&&proven.declaredBossLabel().equals(settings.bossLabel())
                &&proven.nativeChoiceMenuTitle().equals(settings.bossTitle())&&proven.actualTier()>=1&&proven.actualTier()<=5
                &&proven.nativeTierLabel().equals(settings.tierPattern().replace("{tier}",SlayerSettings.roman(proven.actualTier())));
    }
    public int finishingOriginalReturnSlot(MeleeGrindModule actual){return combat==actual?previousSlot:-1;}
    public int finishingSelections(){return combat==null?0:combat.finishingSelections();}
    public int finishingRestores(){return combat==null?0:combat.finishingRestores();}
    public long finishingSubmittedAttacks(){return combat==null?0:combat.finishingSubmittedAttacks();}
    @Override protected void postDisableNormalReturned(ModuleManager.OwnDisableReceipt actual){
        if(combat!=null)combat.observeFinishingParentStopped(actual);
    }
    private SlayerSettings settings;private MeleeGrindModule combat;
    private boolean cancelOnly,closingMenu;private int previousSlot,ownedMenu=-1;
    private String lastStatus="Idle";
    private SlayerActiveSetup activeSetup;
    private net.minecraft.client.player.LocalPlayer owner;
    private net.minecraft.client.multiplayer.ClientLevel ownerLevel;
    private dev.veltraph.client.navigation.mc.McTravelNavigator accessTravel;
    private final dev.veltraph.client.combat.grind.CombatScheduledPause scheduledPause;
    private Object borrowedCombatTicket;
    private net.minecraft.client.multiplayer.ClientPacketListener ownerConnection;
    private final dev.veltraph.client.scheduling.ScheduledPausePort pausePort=new dev.veltraph.client.scheduling.ScheduledPausePort(){
        public boolean pause(Object ticket){
            if(!scheduledPause.pause(ticket))return false;
            if(combat!=null&&combat.isEnabled()){
                borrowedCombatTicket=ticket;
                if(!combat.scheduledPausePort().pause(ticket)){scheduledPause.stop(ticket);return false;}
            }
            return owns(ticket);
        }
        public boolean owns(Object ticket){return scheduledPause.owns(ticket)&&(borrowedCombatTicket==null
                ||borrowedCombatTicket==ticket&&combat!=null&&combat.scheduledPausePort().owns(ticket));}
        public boolean rebindAfterOwnedArrival(Object ticket){
            if(!owns(ticket)||schedulingReadiness().isPresent())return false;
            if(borrowedCombatTicket!=null&&!combat.scheduledPausePort().rebindAfterOwnedArrival(ticket))return false;
            return scheduledPause.rebindAfterOwnedArrival(ticket);
        }
        public boolean resume(Object ticket){
            if(!owns(ticket)||!scheduledPause.resume(ticket))return false;
            if(borrowedCombatTicket!=null&&!combat.scheduledPausePort().resume(ticket)){setEnabled(false);return false;}
            borrowedCombatTicket=null;return isEnabled();
        }
        public void stop(Object ticket){if(scheduledPause.owns(ticket))scheduledPause.stop(ticket);}
    };
    private net.minecraft.world.entity.Entity accessNpc;
    private boolean approaching,accessWalking;private long accessStarted;
    private String accessStatus="Idle";
    public SlayerModule(Minecraft client,ConfigService config,FarmServices services) {
        super(ID,"Auto-Slayer",ModuleCategory.COMBAT);this.client=client;this.config=config;this.services=services;menus=new McMenuAccess(client);
        accessTravel=new dev.veltraph.client.navigation.mc.McTravelNavigator(client,services,config);
        tara=new dev.veltraph.client.combat.slayer.tara.McTaraParent(client,config,services);
        loot=new SlayerQuestLoot(client,services,config);
        cocoon=new SlayerCocoon(client,config,services);
        questTier=new McSlayerQuestTierObserver(client,config,this,quest);
        scheduledPause=new dev.veltraph.client.combat.grind.CombatScheduledPause(client,this::isEnabled,this::scheduledPauseReadiness,this::scheduledPauseSignature,
                this::releaseForScheduledPause,this::rebindScheduledNative,()->{settings=SlayerSettings.from(config);accessStatus="Owned rejoin; preserved quest";},()->setEnabled(false));
    }
    public dev.veltraph.client.scheduling.ScheduledPausePort scheduledPausePort(){return pausePort;}
    public boolean scheduledPaused(){return scheduledPause.paused();}
    public int completedQuests(){return quest.completed();}
    public int combatKills(){return combat==null?0:combat.confirmedKills();}
    public int confirmedQuestDropUnits(){return confirmedDropUnits;}
    public boolean questLootPending(){return completionWaiting;}
    public String questLootStatus(){return loot.status();}
    public String cocoonStatus(){return cocoon.status();}public int cocoonTransitions(){return cocoonTransitions;}
    public SlayerQuestController.State questState(){return quest.state();}
    /** Paused arrival proof precedes root's explicit rebind; preserved quest/child ownership is required. */
    public Optional<String> schedulingReadiness(){
        if(!scheduledPaused())return scheduledPauseReadiness();
        if(borrowedCombatTicket==null||!pausePort.owns(borrowedCombatTicket)||quest.state()!=SlayerQuestController.State.ACTIVE
                ||combat==null||!combat.isEnabled()||!combat.scheduledPaused())return Optional.of("Preserved Slayer quest/child pause ownership is unavailable");
        var arrival=scheduledPause.arrivalReadiness();return arrival.isPresent()?arrival:combat.schedulingReadiness();
    }
    public Optional<String> scheduledPauseReadiness(){
        if(!isEnabled()||scheduledPaused()||client.player==null||client.level==null||client.player!=owner
                ||client.level!=ownerLevel||client.getConnection()!=ownerConnection)return Optional.of("Slayer native owner unavailable");
        if(tara.active()||cancelOnly||cocoon.transactionPending()||completionWaiting||loot.pending()||loot.transactionPending()||quest.state()!=SlayerQuestController.State.ACTIVE||quest.inMenus()||approaching||accessWalking
                ||accessNpc!=null||closingMenu||ownedMenu>=0||client.gui.screen()!=null||menus.read()!=null)
            return Optional.of("Slayer quest/menu/access transaction is not idle");
        if(combat==null||!combat.isEnabled())return Optional.of("Slayer combat owner is not active");
        return combat.scheduledPauseReadiness();
    }
    public String scheduledPauseSignature(){
        var values=new TreeMap<String,String>();for(var key:PersistableKeys.collect(List.of(SlayerConfig.class,TaraConfig.class,TravelConfig.class)))values.put(key.name(),String.valueOf(config.get(key)));
        return ID+"|"+cancelOnly+"|"+values+"|"+(combat==null?"unavailable combat proof":combat.scheduledPauseSignature());
    }
    private void releaseForScheduledPause(){questTier.close();accessTravel.stop();services.rotation().cancelRotation();
        services.input().releaseAllMovement();services.breaking().setBreaking(false);
        dev.veltraph.client.combat.grind.CombatScheduledPause.releaseKeys(client);}
    private void rebindScheduledNative(){owner=client.player;ownerLevel=client.level;ownerConnection=client.getConnection();
        accessTravel=new dev.veltraph.client.navigation.mc.McTravelNavigator(client,services,config);
        approaching=accessWalking=false;accessNpc=null;ownedMenu=-1;closingMenu=false;loot.reset();cocoon.reset();camera.reset();}
    public void cancellationOnly(boolean value) { if(isEnabled()) throw new IllegalStateException("Stop Slayer before changing its operation");cancelOnly=value; }
    @Override public boolean requiresSkyBlock() { return true; }
    /** Only exact known Spider menu declarations select the optional typed source interface. */
    public static dev.veltraph.client.combat.grind.CombatWeaponPolicy.Family declaredWeaponFamily(String bossLabel,boolean taraActive){
        return !taraActive&&"Tarantula Broodfather".equals(bossLabel)
                ?dev.veltraph.client.combat.grind.CombatWeaponPolicy.Family.SLAYER_SPIDER:null;
    }
    @Override public Optional<String> preStartCheck() {
        settings=SlayerSettings.from(config);
        var taraError=TaraConfig.validation(config);if(taraError.isPresent())return taraError;
        var cocoonError=SlayerCocoon.validation(config);if(cocoonError.isPresent())return cocoonError;
        var lootError=SlayerQuestLoot.validation(config);if(lootError.isPresent())return lootError;
        if(phoneSlot()<0&&findNpc()==null) return Optional.of("Put a Batphone in the hotbar or move within loaded range of the configured Slayer NPC.");
        if(!cancelOnly) {
            if(config.get(SlayerConfig.SLAYER_TARGET_NAMES).isBlank()) return Optional.of("Set Slayer target names first.");
            combat=new MeleeGrindModule("slayer_combat","Slayer Combat",null,config.get(SlayerConfig.SLAYER_TARGET_NAMES),client,services,config,camera,
                    target->!config.get(TaraConfig.TARA_USE_SOUL_WHIP)||SlayerSignals.family(target.name())==SlayerSignals.Family.SPIDER,
                    declaredWeaponFamily(settings.bossLabel(),config.get(TaraConfig.TARA_USE_SOUL_WHIP)),
                    child->isEnabled()&&combat==child&&Objects.equals(settings.bossLabel(),config.get(SlayerConfig.SLAYER_BOSS_LABEL))&&client.player==owner&&client.level==ownerLevel&&client.getConnection()==ownerConnection,()->this);
            var finishingSource=finishingSourceChild(settings,config.get(TaraConfig.TARA_USE_SOUL_WHIP));
            finishingBinding=finishingSource==null?null:new FinishingParentBinding(this,combat,settings,finishingSource);
            if(finishingBinding!=null)combat.finishingParent(finishingBinding);
            var sourceCombat=dev.veltraph.client.combat.grind.SourceCombatUnreachableConsumer.declaredSlayerChild(settings.bossLabel());
            if(sourceCombat!=null)combat.sourceUnreachableConsumer(sourceCombat);
            var taraNativeError=tara.configure(combat);if(taraNativeError.isPresent())return taraNativeError;
            if(tara.configured())combat.ownedMechanic(tara);
            return combat.preStartCheck();
        }
        return Optional.empty();
    }
    @Override public void onEnable() {
        finishingLootHandoff=null;
        camera.reset();taraSetup=TaraConfig.signature(config);taraDeferredMessages.clear();
        scheduledPause.invalidate();borrowedCombatTicket=null;ownerConnection=client.getConnection();
        settings=SlayerSettings.from(config);activeSetup=SlayerActiveSetup.capture(config);previousSlot=services.inventory().selectedSlot();ownedMenu=-1;closingMenu=false;
        owner=client.player;ownerLevel=client.level;
        approaching=false;accessWalking=false;accessNpc=null;accessStatus="Idle";
        quest.start(cancelOnly,System.currentTimeMillis());lastStatus=quest.status();completionWaiting=false;confirmedDropUnits=0;loot.reset();lootSetup=lootSetup();
        cocoon.reset();cocoonBound=deferredCompletion=false;cocoonTransitions=0;cocoonSetup=cocoonSetup();
        services.breaking().installTargetGuard(this,p->false);
        questTier.bind();
    }
    @Override public void onDisable() {
        questTier.close();
        tara.stop();taraDeferredMessages.clear();
        scheduledPause.invalidate();borrowedCombatTicket=null;ownerConnection=null;
        if(combat!=null) combat.setEnabled(false);
        loot.stop();cocoon.stop();completionWaiting=false;services.breaking().clearTargetGuard(this);
        accessTravel.stop();approaching=false;accessWalking=false;accessNpc=null;
        services.rotation().cancelRotation();services.input().releaseAllMovement();services.breaking().setBreaking(false);
        if(client.player==owner&&client.level==ownerLevel) {
            var menu=menus.read();if(menu!=null&&menu.id()==ownedMenu&&settings!=null&&settings.allowed(menu.title())) menus.close(menu.id());
            if(client.player!=null&&(combat==null||!combat.finishingRequiresOwnedCleanup())) services.inventory().selectSlot(previousSlot);
        }
        if(quest.state()!=SlayerQuestController.State.FAILED&&quest.state()!=SlayerQuestController.State.COMPLETE) quest.stop();
        owner=null;ownerLevel=null;
        ownedMenu=-1;closingMenu=false;cancelOnly=false;
    }
    @Override public boolean allowsScreen(String title) {
        return settings!=null&&settings.allowed(MenuSnapshot.clean(title))&&(quest.inMenus()||closingMenu);
    }
    /** Only the active owned NPC-ingress travel leg may explain its actual issued transport. */
    @Override public boolean consumeExpectedTeleport(dev.veltraph.client.world.Vec3 position,long now){
        return isEnabled()&&!scheduledPaused()&&approaching&&accessWalking&&accessTravel!=null
            &&activeSetup!=null&&activeSetup.current(config)&&!completionWaiting&&!cocoon.pending()&&!tara.active()
            &&(combat==null||!combat.isEnabled())&&client.player==owner&&client.level==ownerLevel
            &&client.getConnection()==ownerConnection&&owner!=null&&owner.isAlive()
            &&accessTravel.consumeExpectedTeleport(position,now);
    }
    private int phoneSlot() {
        String desired=config.get(SlayerConfig.SLAYER_PHONE_NAME).strip().toLowerCase(Locale.ROOT);
        if(desired.isBlank()) return -1;
        var names=services.inventory().hotbarItemNames();
        for(int i=0;i<names.size();i++) if(names.get(i).toLowerCase(Locale.ROOT).contains(desired)) return i;
        return -1;
    }
    private net.minecraft.world.entity.Entity findNpc() {
        if(client.player==null||client.level==null) return null;
        String name=config.get(SlayerConfig.SLAYER_NPC_NAME).strip();if(name.isBlank()) return null;
        net.minecraft.world.entity.Entity best=null;double distance=64*64;
        for(var entity:client.level.entitiesForRendering()) {
            if(entity==client.player||!(entity instanceof net.minecraft.world.entity.LivingEntity)||entity instanceof net.minecraft.world.entity.decoration.ArmorStand||!entity.isAlive()) continue;
            if(!MenuSnapshot.clean(entity.getName().getString()).equalsIgnoreCase(name)) continue;
            double d=entity.distanceToSqr(client.player);if(d<distance) { best=entity;distance=d; }
        }
        return best;
    }
    private void approachNpc() {
        if(accessNpc==null||!accessNpc.isAlive()||System.currentTimeMillis()-accessStarted>30000) { stopWith("Slayer NPC approach failed or timed out");return; }
        double distance=client.player.distanceTo(accessNpc);
        if(distance>2.8||!client.player.hasLineOfSight(accessNpc)) {
            if(!accessWalking) {
                var p=accessNpc.blockPosition();boolean found=false;
                for(int[] offset:new int[][]{{1,0},{-1,0},{0,1},{0,-1}}) {
                    if(accessTravel.startApproach(new dev.veltraph.client.world.BlockPos(p.getX()+offset[0],p.getY(),p.getZ()+offset[1]))) { found=true;break; }
                }
                if(!found) { stopWith("No permitted path to Slayer NPC");return; }accessWalking=true;
            }
            var state=accessTravel.tick();
            if(state==dev.veltraph.client.navigation.mc.McTravelNavigator.State.FAILED) { stopWith("Slayer NPC path blocked");return; }
            if(state==dev.veltraph.client.navigation.mc.McTravelNavigator.State.ARRIVED) accessWalking=false;
            return;
        }
        accessTravel.stop();accessWalking=false;
        var delta=accessNpc.getBoundingBox().getCenter().subtract(client.player.getEyePosition());
        float yaw=(float)Math.toDegrees(Math.atan2(-delta.x,delta.z)),pitch=(float)-Math.toDegrees(Math.atan2(delta.y,Math.hypot(delta.x,delta.z)));
        services.rotation().track(yaw,pitch);
        if(client.hitResult instanceof net.minecraft.world.phys.EntityHitResult hit&&hit.getEntity()==accessNpc&&client.gameMode!=null) {
            client.gameMode.interact(client.player,accessNpc,hit,InteractionHand.MAIN_HAND);
            accessStatus="NPC interaction sent";
            approaching=false;quest.accessReady(System.currentTimeMillis());
        }
    }
    private String lootSetup(){return config.get(SlayerConfig.SLAYER_COLLECT_QUEST_DROPS)+"|"+config.get(SlayerConfig.SLAYER_QUEST_BOSS_NAMES)+"|"+config.get(SlayerConfig.SLAYER_QUEST_DROP_NAMES)+"|"+config.get(SlayerConfig.SLAYER_QUEST_LOOT_TIMEOUT_MS)+"|"+config.get(SlayerConfig.SLAYER_QUEST_DROP_RADIUS);}
    private String cocoonSetup(){return config.get(SlayerConfig.SLAYER_QUEST_CURRENCY)+"|"+config.get(SlayerConfig.SLAYER_COCOON_RECOVERY)+"|"+config.get(SlayerConfig.SLAYER_COCOON_HEAD_HASHES)+"|"+config.get(SlayerConfig.SLAYER_COCOON_TIMEOUT_MS)+"|"+config.get(SlayerConfig.SLAYER_COCOON_RECEIPT_WINDOW_MS)+"|"+config.get(SlayerConfig.SLAYER_COCOON_REPLACEMENT_RADIUS);}
    @Override public void onTick() {
        if(scheduledPaused())return;
        var player=client.player;float yaw=player==null?0:player.getYRot(),pitch=player==null?0:player.getXRot();
        try {tickQuest();}
        finally {if(isEnabled()&&player!=null&&client.player==player&&player==owner&&client.level==ownerLevel&&player.isAlive()){
            double speed=config.get(RotationConfig.ROTATION_SPEED_MULTIPLIER);
            var angles=camera.apply(player.tickCount,yaw,pitch,dev.veltraph.client.service.mc.McOwnedRotation.requestedYaw(client),dev.veltraph.client.service.mc.McOwnedRotation.requestedPitch(client),speed,speed);
            dev.veltraph.client.service.mc.McOwnedRotation.plan(client,angles.yaw(),angles.pitch());
        }}
    }
    private void tickQuest(){
        questTier.refresh();
        if(activeSetup==null||!activeSetup.current(config)){stopWith("Slayer/combat setup changed during owned quest");return;}
        if(!taraSetup.equals(TaraConfig.signature(config))){stopWith("Tara setup changed during owned Slayer quest");return;}
        if(!Objects.equals(lootSetup,lootSetup())){stopWith("Quest loot setup changed; stop before changing this transaction");return;}
        if(!Objects.equals(cocoonSetup,cocoonSetup())){stopWith("Quest currency/cocoon setup changed during the owned transaction");return;}
        if(client.player==null||client.player!=owner||client.level!=ownerLevel||client.getConnection()!=ownerConnection||!client.player.isAlive()) { stopWith("World/player unavailable");return; }
        if(cocoonBound&&!cocoon.boundProof(combat.targets(24))){stopWith(cocoon.status());return;}
        if(cocoon.pending()){
            cocoon.tick(System.currentTimeMillis(),combat.targets(24));accessStatus=cocoon.status();
            if(cocoon.state()==SlayerCocoon.State.FAILED){stopWith(cocoon.status());return;}
            if(cocoon.state()!=SlayerCocoon.State.READY)return;
            var replacement=cocoon.replacement().orElseThrow();
            if(!combat.resumeOwnedCocoonBody(replacement)){stopWith("Cocoon replacement lost exact enabled-child/native/weapon proof");return;}
            cocoonBound=true;cocoonTransitions++;loot.reset();
            if(config.get(SlayerConfig.SLAYER_COLLECT_QUEST_DROPS))loot.watch(replacement);
        }
        if(cocoonBound&&deferredCompletion&&cocoon.replacementDeathObserved()){
            deferredCompletion=false;onServerMessage("SLAYER QUEST COMPLETE!");
        }
        var menu=menus.read();
        if(completionWaiting){
            loot.tick(System.currentTimeMillis(),combat.targets(24));accessStatus=loot.status();
            if(loot.state()==SlayerQuestLoot.State.FAILED){stopWith(loot.status());return;}
            if(loot.state()!=SlayerQuestLoot.State.COMPLETE)return;
            if(config.get(SlayerConfig.SLAYER_COCOON_RECOVERY)&&cocoon.receiptWindowOpen(System.currentTimeMillis())){accessStatus="Native drop delivered; configured own-cocoon receipt window pending";return;}
            completionWaiting=false;confirmedDropUnits+=loot.delivered();quest.message("SLAYER QUEST COMPLETE!",settings,System.currentTimeMillis());
            if(quest.state()==SlayerQuestController.State.COMPLETE){setEnabled(false);return;}
        }
        if(approaching) {
            if(menu!=null&&settings.allowed(menu.title())) { approaching=false;accessTravel.stop();quest.accessReady(System.currentTimeMillis()); }
            else { approachNpc();return; }
        }
        if(client.gui.screen()!=null&&menu==null) { stopWith("Unexpected non-container screen");return; }
        if(menu!=null&&quest.state()==SlayerQuestController.State.ACTIVE&&menu.id()!=ownedMenu) { stopWith("Unrelated menu opened during combat");return; }
        if(menu!=null&&settings.allowed(menu.title())) ownedMenu=menu.id();
        var action=quest.tick(menu,settings,System.currentTimeMillis());
        switch(action.kind()) {
            case USE_PHONE -> {
                stopCombat();int slot=phoneSlot();
                if(slot<0) {
                    accessNpc=findNpc();if(accessNpc==null) { stopWith("Batphone and Slayer NPC unavailable");return; }
                    approaching=true;accessWalking=false;accessStarted=System.currentTimeMillis();
                    accessStatus="NPC approach";
                } else {
                    if(client.gameMode==null) { stopWith("Batphone unavailable");return; }
                    services.inventory().selectSlot(slot);client.gameMode.useItem(client.player,InteractionHand.MAIN_HAND);
                    accessStatus="Batphone used in slot "+(slot+1);
                }
            }
            case CLICK -> { stopCombat();
                var tierObservation=questTier.beforeAction(action,menu,settings);boolean submitted=false;
                try{submitted=menus.click(action.menuId(),action.slotId(),action.expectedName());}
                finally{questTier.afterAction(tierObservation,submitted);}
                if(!submitted) { stopWith("Menu changed before click");return; }
            }
            case CLOSE_MENU -> { stopCombat();menus.close(action.menuId());closingMenu=true; }
            case NONE -> {}
        }
        if(quest.state()==SlayerQuestController.State.ACTIVE&&menu==null) {
            closingMenu=false;ownedMenu=-1;accessNpc=null;
            if(combat==null) { stopWith("Combat runtime unavailable");return; }
            if(!combat.isEnabled()) {
                var error=combat.preStartCheck();if(error.isPresent()) { stopWith(error.get());return; }combat.setEnabled(true);
            }
            if(config.get(SlayerConfig.SLAYER_COLLECT_QUEST_DROPS))combat.selectedNamedCombatTarget().ifPresent(loot::watch);
            combat.selectedNamedCombatTarget().ifPresent(selected->cocoon.watch(selected,System.currentTimeMillis()));
            combat.onTick();
            if(tara.failed()&&tara.configured()){stopWith(tara.status());return;}
            if(!tara.active()&&!taraDeferredMessages.isEmpty()){var deferred=List.copyOf(taraDeferredMessages);taraDeferredMessages.clear();for(var message:deferred)onServerMessage(message);if(!isEnabled())return;}
            if(tara.active()){accessStatus=tara.status();return;}
            if(config.get(SlayerConfig.SLAYER_COLLECT_QUEST_DROPS)){combat.selectedNamedCombatTarget().ifPresent(loot::watch);loot.observe(combat.targets(24));}
            combat.selectedNamedCombatTarget().ifPresent(selected->cocoon.watch(selected,System.currentTimeMillis()));
            if(loot.state()==SlayerQuestLoot.State.FAILED){stopWith(loot.status());return;}
            if(!combat.isEnabled()) { stopWith("Combat stopped: "+combat.diagnostics());return; }
        } else stopCombat();
        String status=quest.status();
        if(!status.equals(lastStatus)) { lastStatus=status;VeltraphLog.basic("Slayer",status); }
        if(quest.state()==SlayerQuestController.State.FAILED||quest.state()==SlayerQuestController.State.COMPLETE) setEnabled(false);
    }
    private void stopCombat() {
        if(combat!=null&&combat.isEnabled()) combat.setEnabled(false);
        services.input().releaseAllMovement();services.breaking().setBreaking(false);
    }
    private void stopWith(String reason) { quest.abort(reason);lastStatus=quest.status();VeltraphLog.basic("Slayer",lastStatus);setEnabled(false); }
    @Override public void onServerMessage(String line) {
        if(!isEnabled()||settings==null||scheduledPaused()) return;
        if(activeSetup==null||!activeSetup.current(config)){stopWith("Slayer/combat setup changed before quest receipt");return;}
        boolean finishingReturnFailed=false;
        if("SLAYER QUEST COMPLETE!".equals(line)&&finishingLootHandoff!=null)return; // Duplicate reentry has no new delivery authority.
        if("SLAYER QUEST COMPLETE!".equals(line)&&quest.state()==SlayerQuestController.State.ACTIVE
                &&config.get(SlayerConfig.SLAYER_COLLECT_QUEST_DROPS)&&!completionWaiting&&!cocoon.activeCycle()&&!tara.active()&&combat!=null){
            var actualChild=combat;var actualSettings=settings;var player=client.player;var world=client.level;
            var connection=client.getConnection();var gameMode=client.gameMode;
            var registry=dev.veltraph.client.VeltraphBootstrap.moduleManager();var before=registry.lifecycleSnapshot();
            var invocation=new Object();finishingLootHandoff=invocation;
            try {finishingReturnFailed=!actualChild.finishingReleaseBeforeOwnedLoot();}
            finally {if(finishingLootHandoff==invocation)finishingLootHandoff=null;}
            // A captured old callback cannot retire or stop a different actual episode after reentry.
            if(dev.veltraph.client.VeltraphBootstrap.moduleManager()!=registry||!registry.lifecycleCurrent(before)
                    ||combat!=actualChild||settings!=actualSettings||client.player!=player||client.level!=world
                    ||client.getConnection()!=connection||client.gameMode!=gameMode)return;
        }
        questTier.messageObserved(line); // Original retirement follows the guarded old-loan return, before old control flow.
        if(finishingReturnFailed){stopWith("Finishing tool custody changed before owned quest loot; no inventory rebasing");return;}
        tara.message(line,System.currentTimeMillis());
        if(tara.active()&&(line.equals("SLAYER QUEST COMPLETE!")||SlayerCocoon.receipt(line))){
            if(!taraDeferredMessages.contains(line))taraDeferredMessages.add(line);
            accessStatus="Tara actual WAITING/Bow effect pending; quest/cocoon receipt held";return;
        }
        if(SlayerCocoon.receipt(line)&&quest.state()==SlayerQuestController.State.ACTIVE){
            if(cocoon.activeCycle())return;
            if(!cocoon.begin(System.currentTimeMillis())||combat==null||!combat.holdForOwnedCocoon(cocoon.observedBoss().orElse(null))){stopWith(cocoon.status());return;}
            deferredCompletion|=completionWaiting;completionWaiting=false;loot.reset();accessStatus=cocoon.status();return;
        }
        if(line.equals("SLAYER QUEST COMPLETE!")&&cocoon.activeCycle()&&!cocoon.replacementDeathObserved()){
            deferredCompletion=true;accessStatus="Native completion held until verified cocoon replacement death and loot";return;
        }
        if(line.equals("SLAYER QUEST COMPLETE!")&&quest.state()==SlayerQuestController.State.ACTIVE&&config.get(SlayerConfig.SLAYER_COLLECT_QUEST_DROPS)){
            if(completionWaiting)return;
            if(!loot.begin(System.currentTimeMillis())){stopWith(loot.status());return;}
            completionWaiting=true;
            if(config.get(SlayerConfig.SLAYER_COCOON_RECOVERY)&&cocoon.receiptWindowOpen(System.currentTimeMillis())&&combat!=null&&combat.isEnabled()){
                if(!combat.holdForOwnedCocoon(cocoon.observedBoss().orElse(null))){stopWith("Unable to preserve child during cocoon receipt window");return;}
            }else stopCombat();accessStatus=loot.status();return;
        }
        quest.message(line,settings,System.currentTimeMillis());
        if(line.equals("SLAYER QUEST STARTED!")&&quest.state()==SlayerQuestController.State.ACTIVE){taraDeferredMessages.clear();if(combat!=null){var error=tara.configure(combat);if(error.isPresent()){stopWith(error.get());return;}}loot.reset();completionWaiting=false;cocoon.reset();cocoonBound=deferredCompletion=false;}
        if(quest.state()==SlayerQuestController.State.ACTIVE) closingMenu=ownedMenu>=0;
        else stopCombat();
        if(!quest.status().equals(lastStatus)) { lastStatus=quest.status();VeltraphLog.basic("Slayer",lastStatus); }
        if(quest.state()==SlayerQuestController.State.FAILED||quest.state()==SlayerQuestController.State.COMPLETE) setEnabled(false);
    }
    @Override public String diagnostics() { return (approaching?"Approaching Slayer NPC | ":"")+quest.status()+" | "+accessStatus+(combat!=null&&combat.isEnabled()?" | "+combat.diagnostics():""); }
}
