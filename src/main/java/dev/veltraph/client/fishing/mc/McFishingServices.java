package dev.veltraph.client.fishing.mc;

import dev.veltraph.client.fishing.BobberFacts;
import dev.veltraph.client.fishing.FishingServices;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Minecraft implementation of {@link FishingServices}: reads the rod/bobber state and performs the single
 * rod right-click. All reads are try/catch-degraded so a UI/mapping hiccup never crashes the client.
 *
 * <p>Verified yarn signatures (1.21.11): {@code PlayerEntity#fishHook} (field), {@code Entity#getVelocity}
 * (= {@code method_18798}), {@code FishingBobberEntity#getHookedEntity} (= {@code method_26957}),
 * {@code PlayerEntity#getMainHandStack} (= {@code method_6047}),
 * {@code ClientPlayerInteractionManager#interactItem(PlayerEntity, Hand)} (= {@code method_2919}).
 *
 * <p>{@link #useRod()} is an item-use (cast/reel) — NOT a block-break — so the Pass-13 ghost-break caution
 * (which was specific to {@code updateBlockBreakingProgress}) does not apply; casting a rod inherently
 * sends a use-item packet, which is the intended, minimal action.
 */
public final class McFishingServices implements FishingServices,dev.veltraph.client.fishing.FishingScheduledHooks {

    private final Minecraft client;
    private final dev.veltraph.client.service.mc.McInputService pauseInput;
    private final dev.veltraph.client.navigation.mc.McNavigator pauseNavigator;
    private FishingCombat combat;
    private Object scheduledTicket;
    private record PauseCapture(Object player,Object world,Object connection,java.util.UUID id,String dimension,int slot,dev.veltraph.client.inventory.NativeStackIdentity.Snapshot rod,java.util.List<dev.veltraph.client.inventory.NativeStackIdentity.Snapshot> gear){}
    private PauseCapture scheduledCapture;private boolean scheduledRebound;
    private static final java.util.List<net.minecraft.world.entity.EquipmentSlot> PAUSE_GEAR=java.util.List.of(net.minecraft.world.entity.EquipmentSlot.HEAD,net.minecraft.world.entity.EquipmentSlot.CHEST,net.minecraft.world.entity.EquipmentSlot.LEGS,net.minecraft.world.entity.EquipmentSlot.FEET,net.minecraft.world.entity.EquipmentSlot.OFFHAND);

    private McFishingRodSetup rodSetup;
    private dev.veltraph.client.config.ConfigService setupConfig;
    private boolean setupHelpersInitialized;private Object nativeSession;
    private McGoldenFish golden;
    private McFishingReturn obstruction;
    private McFishingWorm worm;
    private dev.veltraph.client.core.Module owner;
    private McFishingTotem totem;
    private McFishingBait bait;
    private McFishingTrash trash;
    private McFishingGrapple grapple;
    private McFishingSlugGuard slug;
    private McFishingOrb orb;
    private McFishingBlackHole blackHole;
    private McFishingRain rain;
    private java.util.function.Supplier<dev.veltraph.client.game.GameArea> fishingArea=()->dev.veltraph.client.game.GameArea.UNKNOWN;
    public McFishingServices withFishingArea(java.util.function.Supplier<dev.veltraph.client.game.GameArea> area) { fishingArea=area;return this; }
    private dev.veltraph.client.service.RotationService rotation;
    @Override public void startFishing(dev.veltraph.client.config.ConfigService config) {
        scheduledTicket=null;scheduledCapture=null;scheduledRebound=false;
        if(combat!=null)combat.startSession();
        stopRodSetup();rodSetup=new McFishingRodSetup(client,config);
        setupConfig=config;setupHelpersInitialized=false;nativeSession=new Object();
        if(rodSetup.ready())initializeFishingHelpers(config);
    }
    private void initializeFishingHelpers(dev.veltraph.client.config.ConfigService config) {
        stopTrash();trash=new McFishingTrash(client,config,rotation,this::useRod,movementServices());
        stopCatchGuard();slug=new McFishingSlugGuard(client,config,()->fishingArea.get());
        stopGrapple();grapple=new McFishingGrapple(client,config,rotation,this::useRod,()->fishingArea.get(),movementServices());
        stopSupport();blackHole=new McFishingBlackHole(client,config,rotation,this::useRod);orb=new McFishingOrb(client,config,rotation,this::useRod);rain=new McFishingRain(client,config,rotation,this::useRod,()->fishingArea.get(),movementServices());
        stopBait();bait=new McFishingBait(client,config,this::useRod);
        stopTotem();totem=new McFishingTotem(client,config,rotation,this::useRod);
        stopObstructionReturn();stopWormHunting();obstruction=new McFishingReturn(client,config,rotation,movementServices());
        worm=new McFishingWorm(client,config,rotation,this::useRod,owner);
        setupHelpersInitialized=true;
    }
    @Override public String rodSetupConfigurationError(dev.veltraph.client.config.ConfigService config){return McFishingRodSetup.configurationError(client,config);}
    @Override public boolean tickRodSetup(){
        if(scheduledTicket!=null||rodSetup==null)return false;
        boolean pending=rodSetup.tick();
        if(!pending&&rodSetup.ready()&&!setupHelpersInitialized)initializeFishingHelpers(setupConfig);
        return pending;
    }
    @Override public boolean rodSetupReady(){return rodSetup==null||rodSetup.ready();}
    @Override public String rodSetupFailure(){return rodSetup==null?"":rodSetup.failure();}
    @Override public String rodSetupStatus(){return rodSetup==null?"OFF":rodSetup.status();}
    @Override public void stopRodSetup(){if(scheduledTicket==null&&rodSetup!=null)rodSetup.stop();}
    @Override public boolean tickWormHunting(boolean allowStart) { if(scheduledTicket!=null)return false;return worm!=null&&worm.tick(allowStart); }
    @Override public void stopWormHunting() { if(scheduledTicket!=null)return; if(worm!=null)worm.stop(); }
    @Override public String wormFailure() { return worm==null?"":worm.failure(); }
    @Override public String wormStatus() { return worm==null?"IDLE":worm.status(); }
    @Override public boolean tickObstructionReturn(boolean allowStart) { if(scheduledTicket!=null)return false;return obstruction!=null&&obstruction.tick(allowStart); }
    @Override public void stopObstructionReturn() { if(scheduledTicket!=null)return; if(obstruction!=null)obstruction.stop(); }
    @Override public boolean obstructionActive() { return obstruction!=null&&obstruction.active(); }
    @Override public String obstructionFailure() { return obstruction==null?"":obstruction.failure(); }
    @Override public String obstructionStatus() { return obstruction==null?"IDLE":obstruction.status(); }
    public McFishingServices withGoldenFish(dev.veltraph.client.service.RotationService rotation) { this.rotation=rotation;golden=new McGoldenFish(client,rotation);return this; }
    @Override public void goldenFishMessage(String message) { if(scheduledTicket==null)golden.message(message); }
    @Override public boolean tickGoldenFish(boolean enabled) { if(scheduledTicket!=null)return false; if(!enabled){golden.stop();return false;}return golden.tick(this::useRod); }
    @Override public void stopGoldenFish() { if(scheduledTicket!=null)return; golden.stop(); }
    @Override public boolean goldenFishActive() { return golden.active(); }
    @Override public String goldenFishFailure() { return golden.failure(); }
    @Override public String goldenFishStatus() { return golden.status(); }
    private dev.veltraph.client.service.FarmServices movementServices(){return combat==null?null:combat.movementServices();}
    @Override public boolean consumeExpectedTeleport(dev.veltraph.client.world.Vec3 position,long now){
        if(scheduledTicket!=null)return false;
        if(combat!=null&&combat.consumeExpectedTeleport(position,now))return true;
        if(trash!=null&&trash.active())return trash.consumeExpectedTeleport(position,now);
        if(rain!=null&&rain.active())return rain.consumeExpectedTeleport(position,now);
        if(grapple!=null&&grapple.active())return grapple.consumeExpectedTeleport(position,now);
        return obstruction!=null&&obstruction.active()&&obstruction.consumeExpectedTeleport(position,now);
    }
    public McFishingServices withCombat(FishingCombat combat) { this.combat=combat;return this; }
    public McFishingServices bindOwner(dev.veltraph.client.core.Module owner){this.owner=java.util.Objects.requireNonNull(owner);if(combat!=null)combat.bindOwner(owner);return this;}
    @Override public String combatConfigurationError(dev.veltraph.client.config.ConfigService cfg){return McFishingOffense.problem(cfg);}
    @Override public boolean tickCombat() { if(scheduledTicket!=null)return false;return combat!=null&&combat.tick(); }
    @Override public boolean tickCombat(boolean allowStart){return scheduledTicket==null&&combat!=null&&combat.tick(allowStart);}
    @Override public void stopCombat() { if(scheduledTicket!=null)return; if(combat!=null) combat.stop(); }

    public McFishingServices(Minecraft client) {
        this.client = client;
        pauseInput=new dev.veltraph.client.service.mc.McInputService(client);pauseNavigator=new dev.veltraph.client.navigation.mc.McNavigator(client,pauseInput);
        rotation=new dev.veltraph.client.service.mc.McRotationService(client);
        golden=new McGoldenFish(client,rotation);
    }

    @Override
    public boolean available() {
        return client.player != null && client.level != null && (client.gui.screen() == null
                ||bait!=null&&client.gui.screen() instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?> screen&&(bait.allowsScreen(screen.getTitle().getString())||supportAllowsScreen(screen.getTitle().getString())||trashAllowsScreen(screen.getTitle().getString())));
    }

    @Override
    public boolean hasFishingRod() {
        try {
            if (client.player == null) {
                return false;
            }
            ItemStack stack = client.player.getMainHandItem();
            if (stack == null || stack.isEmpty()) {
                return false;
            }
            // Stable source rod scoring explicitly requires the native fishing-rod item.
            // Custom display names do not turn blaze rods or arbitrary tools into fishing rods.
            return stack.is(Items.FISHING_ROD);
        } catch (Throwable t) {
            return false;
        }
    }

    @Override
    public BobberFacts bobber() {
        try {
            if (client.player == null) {
                return BobberFacts.none();
            }
            FishingHook hook = client.player.fishing;
            if(slug!=null&&scheduledTicket==null)slug.observe();
            if (hook == null) {
                return BobberFacts.none();
            }
            double velocityY = hook.getDeltaMovement().y;
            boolean hooked = hook.getHookedIn() != null;
            boolean biting = hook instanceof dev.veltraph.client.mixin.FishingBobberAccessor accessor
                    && accessor.veltraph$caughtFish();
            return new BobberFacts(true, velocityY, hooked, biting);
        } catch (Throwable t) {
            return BobberFacts.none();
        }
    }

    @Override
    public void useRod() {
        try {
            if (scheduledTicket!=null||!available() || !hasFishingRod() || rodSetup!=null&&!rodSetup.mayUseRod() || client.gameMode == null) {
                return;
            }
            client.gameMode.useItem(client.player, InteractionHand.MAIN_HAND);
            client.player.swing(InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, false);
        } catch (Throwable t) {
            // never let a rod-use hiccup crash the client
        }
    }

    @Override public void fishingActionBar(String message) { if(scheduledTicket!=null)return;if(totem!=null)totem.actionBar(message);if(orb!=null)orb.mana(message);if(blackHole!=null)blackHole.mana(message);if(combat!=null)combat.mana(message); }
    @Override public boolean tickTotem(boolean allowStart) { if(scheduledTicket!=null)return false;return totem!=null&&totem.tick(allowStart); }
    @Override public boolean totemActive() { return totem!=null&&totem.active(); }
    @Override public String totemFailure() { return totem==null?"":totem.failure(); }
    @Override public String totemStatus() { return totem==null?"IDLE":totem.status(); }
    @Override public void stopTotem() { if(scheduledTicket!=null)return; if(totem!=null)totem.stop(); }

    @Override public String baitConfigurationError(dev.veltraph.client.config.ConfigService config) { return McFishingBait.configurationError(config); }
    @Override public boolean tickBait(boolean allowStart) { if(scheduledTicket!=null)return false;return bait!=null&&bait.tick(allowStart); }
    @Override public boolean baitActive() { return bait!=null&&bait.active(); }
    @Override public boolean baitAllowsScreen(String title) { return bait!=null&&bait.allowsScreen(title); }
    @Override public String baitFailure() { return bait==null?"":bait.failure(); }
    @Override public String baitStatus() { return bait==null?"IDLE":bait.status(); }
    @Override public void baitMessage(String message) { if(scheduledTicket!=null)return;if(bait!=null)bait.message(message); }
    @Override public void stopBait() { if(scheduledTicket!=null)return; if(bait!=null)bait.stop(); }

    @Override public String supportConfigurationError(dev.veltraph.client.config.ConfigService cfg) { String issue=McFishingOrb.problem(cfg);if(issue.isEmpty())issue=McFishingRain.problem(cfg);return issue.isEmpty()?McFishingBlackHole.problem(cfg):issue; }
    @Override public boolean supportActive() { return orb!=null&&orb.active()||rain!=null&&rain.active()||blackHole!=null&&blackHole.active(); }
    @Override public boolean supportAllowsScreen(String title) { return rain!=null&&rain.screen(title); }
    @Override public String supportFailure() { String issue=orb==null?"":orb.failure();if(issue.isEmpty()&&rain!=null)issue=rain.failure();return issue.isEmpty()&&blackHole!=null?blackHole.failure():issue; }
    @Override public String supportStatus() { return "orb="+(orb==null?"IDLE":orb.status())+" rain="+(rain==null?"IDLE":rain.status())+" blackhole="+(blackHole==null?"IDLE":blackHole.status()); }
    @Override public void supportMessage(String message) { if(scheduledTicket!=null)return;if(rain!=null)rain.message(message);if(combat!=null)combat.message(message); }
    @Override public void stopSupport() { if(scheduledTicket!=null)return; if(orb!=null)orb.stop();if(rain!=null)rain.stop();if(blackHole!=null)blackHole.stop(); }
    @Override public boolean tickSupport(boolean allow) { if(scheduledTicket!=null)return false; if(blackHole!=null&&blackHole.active())return blackHole.tick(false);if(rain!=null&&rain.active())return rain.tick(false);if(orb!=null&&orb.tick(allow))return true;if(rain!=null&&rain.tick(allow))return true;return blackHole!=null&&blackHole.tick(allow); }

    @Override public String trashConfigurationError(dev.veltraph.client.config.ConfigService cfg){return McFishingTrash.problem(cfg);}
    @Override public boolean tickTrash(boolean allow){return scheduledTicket==null&&trash!=null&&trash.tick(allow);}
    @Override public boolean trashActive(){return trash!=null&&trash.active();}
    @Override public boolean trashAllowsScreen(String title){return trash!=null&&trash.screen(title);}
    @Override public String trashFailure(){return trash==null?"":trash.failure();}
    @Override public String trashStatus(){return trash==null?"IDLE":trash.status();}
    @Override public void stopTrash(){if(scheduledTicket==null&&trash!=null)trash.stop();}

    @Override public String catchGuardConfigurationError(dev.veltraph.client.config.ConfigService cfg){return McFishingSlugGuard.problem(cfg);}
    @Override public boolean catchGuardReady(){return scheduledTicket==null&&(slug==null||slug.ready());}
    @Override public String catchGuardFailure(){return slug==null?"":slug.failure();}
    @Override public String catchGuardStatus(){return slug==null?"OFF":slug.status();}
    @Override public void stopCatchGuard(){if(scheduledTicket==null&&slug!=null)slug.stop();}

    @Override public String grappleConfigurationError(dev.veltraph.client.config.ConfigService cfg) { return McFishingGrapple.problem(cfg); }
    @Override public boolean tickGrapple(boolean allow) { if(scheduledTicket!=null)return false; return grapple!=null&&grapple.tick(allow); }
    @Override public boolean grappleActive() { return grapple!=null&&grapple.active(); }
    @Override public String grappleFailure() { return grapple==null?"":grapple.failure(); }
    @Override public String grappleStatus() { return grapple==null?"IDLE":grapple.status(); }
    @Override public void stopGrapple() { if(scheduledTicket!=null)return; if(grapple!=null)grapple.stop(); }

    @Override public boolean submerged() { return available() && client.player.isUnderWater(); }
    @Override public double bubbles() {
        return available() ? 10d*client.player.getAirSupply()/Math.max(1,client.player.getMaxAirSupply()) : 10;
    }
    @Override public boolean surfaceReachable() {
        if (!available()) return false;
        var start=client.player.blockPosition();
        for (int dy=1;dy<=10;dy++) {
            var p=start.above(dy);
            if (!client.level.getBlockState(p).getCollisionShape(client.level,p).isEmpty()) return false;
            if (!client.level.getFluidState(p).isEmpty() && client.level.getFluidState(p.above()).isEmpty())
                return client.level.getBlockState(p.above()).getCollisionShape(client.level,p.above()).isEmpty();
        }
        return false;
    }
    @Override public void setSwimmingUp(boolean swimming) {
        if (client.options != null) if(scheduledTicket==null)dev.veltraph.client.service.mc.McHeldKeys.setHeld(client.options.keyJump,swimming&&available());
    }
    private boolean helpersIdle(){return (combat==null||combat.idleForPause())&&!totemActive()&&!baitActive()&&!supportActive()&&!trashActive()&&(grapple==null||grapple.idleForPause())&&!goldenFishActive()&&!obstructionActive()&&(worm==null||worm.idleForPause())&&totemFailure().isEmpty()&&baitFailure().isEmpty()&&supportFailure().isEmpty()&&grappleFailure().isEmpty()&&goldenFishFailure().isEmpty()&&obstructionFailure().isEmpty()&&wormFailure().isEmpty()&&catchGuardFailure().isEmpty()&&trashFailure().isEmpty();}
    @Override public boolean ready(){
        if(!client.isSameThread()||client.player==null||client.level==null||client.gameMode==null||client.getConnection()==null||!client.player.isAlive()||!fishingArea.get().isSkyBlock()||client.gui.screen()!=null||client.player.containerMenu!=client.player.inventoryMenu||!client.player.containerMenu.getCarried().isEmpty()||client.player.fishing!=null||client.player.isUsingItem()||!hasFishingRod()||!client.player.onGround()||!rodSetupReady()||!helpersIdle())return false;
        return pauseNavigator.standingClear(new dev.veltraph.client.world.Vec3(client.player.getX(),client.player.getY(),client.player.getZ()));
    }
    private PauseCapture capture(){return new PauseCapture(client.player,client.level,client.getConnection(),client.player.getUUID(),client.level.dimension().identifier().toString(),client.player.getInventory().getSelectedSlot(),dev.veltraph.client.inventory.NativeStackIdentity.capture(client.player.getMainHandItem(),client.level.registryAccess()),PAUSE_GEAR.stream().map(slot->dev.veltraph.client.inventory.NativeStackIdentity.capture(client.player.getItemBySlot(slot),client.level.registryAccess())).toList());}
    private boolean protectedPauseLoadout(){
        if(scheduledCapture==null||!ready()||!client.player.getUUID().equals(scheduledCapture.id())||!client.level.dimension().identifier().toString().equals(scheduledCapture.dimension())||client.player.getInventory().getSelectedSlot()!=scheduledCapture.slot()||!scheduledCapture.rod().matches(client.player.getMainHandItem(),client.level.registryAccess()))return false;
        for(int i=0;i<PAUSE_GEAR.size();i++)if(!scheduledCapture.gear().get(i).matches(client.player.getItemBySlot(PAUSE_GEAR.get(i)),client.level.registryAccess()))return false;return true;
    }
    @Override public boolean pause(Object ticket){
        if(ticket==null||scheduledTicket!=null||!ready())return false;scheduledTicket=ticket;scheduledCapture=capture();scheduledRebound=false;
        rotation.cancelRotation();pauseInput.releaseAllMovement();dev.veltraph.client.service.mc.McHeldKeys.setHeld(client.options.keyUse,false);dev.veltraph.client.service.mc.McHeldKeys.setHeld(client.options.keyAttack,false);return true;
    }
    @Override public boolean rebind(Object ticket){
        if(ticket==null||scheduledTicket!=ticket||scheduledRebound||!protectedPauseLoadout())return false;
        if(totem!=null&&!totem.rebindIdle()||bait!=null&&!bait.rebindIdle()||orb!=null&&!orb.rebindIdle()||rain!=null&&!rain.rebindIdle()||blackHole!=null&&!blackHole.rebindIdle()||grapple!=null&&!grapple.rebindIdle()||golden!=null&&!golden.rebindIdle()||obstruction!=null&&!obstruction.rebindIdle()||worm!=null&&!worm.rebindIdle()||combat!=null&&!combat.rebindIdle())return false;
        if(trash!=null&&!trash.rebindIdle())return false;
        if(slug!=null&&!slug.rebindIdle())return false;
        if(rodSetup!=null&&!rodSetup.rebindReady())return false;
        scheduledCapture=capture();scheduledRebound=true;return true;
    }
    @Override public boolean resume(Object ticket){
        if(ticket==null||scheduledTicket!=ticket||!scheduledRebound||!protectedPauseLoadout()||client.player!=scheduledCapture.player()||client.level!=scheduledCapture.world()||client.getConnection()!=scheduledCapture.connection())return false;
        scheduledTicket=null;scheduledCapture=null;scheduledRebound=false;return true;
    }
    @Override public void cancel(Object ticket){if(ticket!=null&&scheduledTicket==ticket){scheduledTicket=null;scheduledCapture=null;scheduledRebound=false;}}

    /** Original final-service/session/helper field custody captured before any registry codec visitor. */
    private final class NativeSessionCustody {
        private final dev.veltraph.client.fishing.module.FishingModule parent;
        private final Object session=nativeSession,ticket=scheduledTicket,capture=scheduledCapture;
        private final boolean rebound=scheduledRebound,initialized=setupHelpersInitialized;
        private final dev.veltraph.client.config.ConfigService store=setupConfig;
        private final McFishingRodSetup setup=rodSetup;
        private final FishingCombat originalCombat=combat;private final McFishingReturn originalObstruction=obstruction;
        private final McFishingRain originalRain=rain;private final McFishingTrash originalTrash=trash;private final McFishingGrapple originalGrapple=grapple;
        private final McFishingWorm originalWorm=worm;private final McFishingTotem originalTotem=totem;private final McFishingBait originalBait=bait;
        private final McFishingOrb originalOrb=orb;private final McFishingBlackHole originalBlackHole=blackHole;private final McGoldenFish originalGolden=golden;
        private final Object originalRotation=rotation,originalArea=fishingArea;
        private NativeSessionCustody(dev.veltraph.client.fishing.module.FishingModule parent){this.parent=parent;}
        private boolean rawCurrent(){return owner==parent&&nativeSession==session&&session!=null&&scheduledTicket==ticket&&ticket==null
            &&scheduledCapture==capture&&capture==null&&scheduledRebound==rebound&&!rebound&&setupConfig==store&&store==dev.veltraph.client.VeltraphBootstrap.config()
            &&setupHelpersInitialized==initialized&&initialized&&rodSetup==setup&&setup!=null
            &&combat==originalCombat&&obstruction==originalObstruction&&rain==originalRain&&trash==originalTrash&&grapple==originalGrapple
            &&worm==originalWorm&&totem==originalTotem&&bait==originalBait&&orb==originalOrb&&blackHole==originalBlackHole&&golden==originalGolden
            &&rotation==originalRotation&&fishingArea==originalArea;}
    }
    /** Installed final-service identity and original helper journey only; callers cannot supply source permission. */
    public final class NativeNormalReturnSource {
        private final NativeSessionCustody custody;private final McFishingRodSetup.NativeCustody rod;
        private final McFishingTravel.NativeReturnSource travel;
        private NativeNormalReturnSource(NativeSessionCustody custody,McFishingRodSetup.NativeCustody rod,McFishingTravel.NativeReturnSource travel){this.custody=custody;this.rod=rod;this.travel=travel;}
        public dev.veltraph.client.core.ModuleManager.ModuleTickDeclaration declaration(){return travel.declaration();}
        public dev.veltraph.client.VeltraphBootstrap.RootOriginalInputPublication publication(){return travel.publication();}
        /** Semantic visitors precede all original stored/session/role field vetoes. */
        public boolean nativeCurrent(){return rod!=null&&rod.nativeCurrent()&&travel.nativeCurrent()
            &&custody.parent.isEnabled()&&custody.rawCurrent()&&rod.rawCurrent();}
    }
    public NativeNormalReturnSource captureNativeNormalReturnSource(dev.veltraph.client.fishing.module.FishingModule parent,
            dev.veltraph.client.navigation.mc.LegacyTravelPublicationLease.Leg exact){
        boolean caller=StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).walk(stream->{var first=stream.filter(f->f.getDeclaringClass()!=McFishingServices.class).findFirst().orElse(null);
            return first!=null&&first.getDeclaringClass()==dev.veltraph.client.fishing.module.FishingModule.class&&first.getMethodName().equals("captureNativeNormalReturnSource");});
        if(!caller||parent==null||owner!=parent||exact==null||nativeSession==null||scheduledTicket!=null||!setupHelpersInitialized||rodSetup==null
            ||setupConfig!=dev.veltraph.client.VeltraphBootstrap.config()||!client.isSameThread())return null;
        var custody=new NativeSessionCustody(parent);
        var rod=custody.setup.captureNativeCustody();if(rod==null||!custody.rawCurrent())return null;
        McFishingTravel.NativeReturnSource travel=null;
        if(custody.originalObstruction!=null)travel=custody.originalObstruction.captureNativeReturnSource(exact);
        if(travel==null&&custody.originalRain!=null)travel=custody.originalRain.captureNativeReturnSource(exact);
        if(travel==null&&custody.originalTrash!=null)travel=custody.originalTrash.captureNativeReturnSource(exact);
        if(travel==null&&custody.originalGrapple!=null)travel=custody.originalGrapple.captureNativeReturnSource(exact);
        if(travel==null&&custody.originalCombat!=null)travel=custody.originalCombat.captureNativeReturnSource(exact);
        if(travel==null)return null;
        var result=new NativeNormalReturnSource(custody,rod,travel);return result.nativeCurrent()?result:null;
    }
    /** Separate old-loan cleanup source; this type is never an active normal-return use source. */
    public final class NativeNormalReturnCleanupSource {
        private final NativeSessionCustody custody;private final McFishingRodSetup.NativeCustody rod;
        private final McFishingTravel.NativeCleanupSource travel;
        private NativeNormalReturnCleanupSource(NativeSessionCustody custody,McFishingRodSetup.NativeCustody rod,McFishingTravel.NativeCleanupSource travel){this.custody=custody;this.rod=rod;this.travel=travel;}
        public boolean nativeCurrent(){return rod!=null&&rod.nativeCurrent()&&travel.nativeCurrent()&&custody.rawCurrent()&&rod.rawCurrent();}
    }
    public NativeNormalReturnCleanupSource captureNativeNormalReturnCleanupSource(dev.veltraph.client.fishing.module.FishingModule parent,
            dev.veltraph.client.navigation.mc.LegacyTravelPublicationLease.Leg exact){
        boolean caller=StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).walk(stream->{var first=stream.filter(f->f.getDeclaringClass()!=McFishingServices.class).findFirst().orElse(null);
            return first!=null&&first.getDeclaringClass()==dev.veltraph.client.fishing.module.FishingModule.class&&first.getMethodName().equals("captureNativeNormalReturnCleanupSource");});
        if(!caller||parent==null||owner!=parent||exact==null||nativeSession==null||scheduledTicket!=null||!setupHelpersInitialized||rodSetup==null
            ||setupConfig!=dev.veltraph.client.VeltraphBootstrap.config()||!client.isSameThread())return null;
        var custody=new NativeSessionCustody(parent);
        var rod=custody.setup.captureNativeCustody();if(rod==null||!custody.rawCurrent())return null;
        McFishingTravel.NativeCleanupSource travel=null;
        if(custody.originalObstruction!=null)travel=custody.originalObstruction.captureNativeCleanupSource(exact);
        if(travel==null&&custody.originalRain!=null)travel=custody.originalRain.captureNativeCleanupSource(exact);
        if(travel==null&&custody.originalTrash!=null)travel=custody.originalTrash.captureNativeCleanupSource(exact);
        if(travel==null&&custody.originalGrapple!=null)travel=custody.originalGrapple.captureNativeCleanupSource(exact);
        if(travel==null&&custody.originalCombat!=null)travel=custody.originalCombat.captureNativeCleanupSource(exact);
        if(travel==null)return null;
        var result=new NativeNormalReturnCleanupSource(custody,rod,travel);return result.nativeCurrent()?result:null;
    }
}
