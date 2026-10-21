package dev.veltraph.client.fishing.module;

import dev.veltraph.client.config.ConfigService;
import dev.veltraph.client.config.FishingConfig;
import dev.veltraph.client.core.AbstractModule;
import dev.veltraph.client.core.ModuleCategory;
import dev.veltraph.client.fishing.BiteDetector;
import dev.veltraph.client.fishing.BobberFacts;
import dev.veltraph.client.fishing.FishingAction;
import dev.veltraph.client.fishing.FishingInputs;
import dev.veltraph.client.fishing.FishingRuntime;
import dev.veltraph.client.fishing.FishingServices;
import dev.veltraph.client.fishing.FishingTuning;
import dev.veltraph.client.game.GameArea;
import dev.veltraph.client.log.VeltraphLog;

import java.util.Optional;
import java.util.Random;

/**
 * Core fishing macro: cast → wait for a bite → reel → recast, driven by the pure {@link FishingRuntime}
 * (`reports/_spec_parts/FishingBiteWire.md`). The macro uses normal rod actions and ground return to the saved cast position; bite detection is purely
 * the local bobber entity; incompatible-rod server messages stop the loop. The only randomness is the recovered 0–2 tick humanize
 * jitter (config, default off). Only normal gameplay rod actions are sent; no external networking.
 *
 * <p>Runs in any SkyBlock area (water bodies), so {@link #requiredArea()} is empty. Requires a fishing rod
 * in hand to start ({@link #preStartCheck()}).
 */
public final class FishingModule extends AbstractModule {

    public static final String ID = "core_fishing";

    private final FishingServices services;
    private final ConfigService config;
    private final Random random;

    private FishingRuntime runtime;
    private final dev.veltraph.client.fishing.FishingSurfaceController surface =
            new dev.veltraph.client.fishing.FishingSurfaceController();
    private boolean surfacing, rodSubflow;
    private String stopReason = "";
    private Object scheduledTicket;
    private dev.veltraph.client.fishing.FishingScheduledHooks scheduledHooks;
    private boolean scheduledRebound;
    private java.util.Map<String,Object> scheduledSettings=java.util.Map.of();

    public FishingModule(FishingServices services, ConfigService config, Random random) {
        super(ID, "Core Fishing", ModuleCategory.FISHING);
        this.services = services;
        this.config = config;
        this.random = random;
    }

    @Override
    public Optional<GameArea> requiredArea() {
        return Optional.empty(); // any SkyBlock water
    }

    @Override
    public boolean requiresSkyBlock() {
        return true;
    }

    @Override
    public Optional<String> preStartCheck() {
        String setupError=services.rodSetupConfigurationError(config);
        if(!setupError.isEmpty())return Optional.of(setupError);
        if (!config.get(dev.veltraph.client.fishing.setup.FishingSetupConfig.FISHING_AUTO_ROD) && !services.hasFishingRod()) {
            return Optional.of("Hold a fishing rod to start fishing.");
        }
        String trashError=services.trashConfigurationError(config);if(!trashError.isEmpty())return Optional.of(trashError);
        String guardError=services.catchGuardConfigurationError(config);if(!guardError.isEmpty())return Optional.of(guardError);
        String grappleError=services.grappleConfigurationError(config);if(!grappleError.isEmpty())return Optional.of(grappleError);
        String combatError=services.combatConfigurationError(config);if(!combatError.isEmpty())return Optional.of(combatError);
        String supportError=services.supportConfigurationError(config);if(!supportError.isEmpty())return Optional.of(supportError);
        String baitError=services.baitConfigurationError(config);if(!baitError.isEmpty())return Optional.of(baitError);
        if (config.get(FishingConfig.FISHING_WORM_HUNTING) && !services.submerged()) {
            return Optional.of("Start underwater at the fishing position for Underwater Hunting.");
        }
        return Optional.empty();
    }

    @Override
    public void onEnable() {
        scheduledTicket=null;scheduledHooks=null;scheduledRebound=false;scheduledSettings=java.util.Map.of();
        surface.reset(); surfacing=false; rodSubflow=false; stopReason="";
        runtime = new FishingRuntime(random, FishingTuning.fromConfig(config));
        services.startFishing(config);
        VeltraphLog.basic("Fishing", "fishing started");
    }

    @Override
    public void onDisable() {
        var oldTicket=scheduledTicket;var oldHooks=scheduledHooks;scheduledTicket=null;scheduledHooks=null;scheduledRebound=false;scheduledSettings=java.util.Map.of();
        if(oldHooks!=null)oldHooks.cancel(oldTicket);
        services.stopCombat();
        services.stopCatchGuard();
        services.stopTrash();
        services.stopTotem();
        services.stopBait();
        services.stopSupport();
        services.stopGrapple();
        services.stopGoldenFish();
        services.stopObstructionReturn();
        services.stopWormHunting();
        services.stopRodSetup();
        surface.reset(); surfacing=false; services.setSwimmingUp(false);
        if (runtime != null) {
            runtime.reset();
        }
        VeltraphLog.basic("Fishing", "fishing stopped");
    }

    @Override
    public void onTick() {
        if (runtime == null || !isEnabled() || scheduledTicket!=null) {
            return;
        }
        if (!services.available()) { setEnabled(false); return; }
        // Setup owns the startup boundary before any controller can use a rod or temporary tool.
        boolean settingUp=services.tickRodSetup();
        if(!services.rodSetupFailure().isEmpty()){stop(services.rodSetupFailure());return;}
        if(settingUp || !services.rodSetupReady())return;
        // Underwater hunting has its own higher-priority <=1.5-bubble recovery.
        // It retains input while the saved-position return dives back after full refill.
        boolean wormEmergency=config.get(FishingConfig.FISHING_WORM_HUNTING)
                && services.submerged() && services.bubbles()<=1.5;
        if(wormEmergency&&services.totemActive()) services.stopTotem();
        // Emergency ascent may retain an outstanding core reel. Observe its removal
        // while swimming, without abandoning the old acknowledgment or clicking again.
        if (!services.wormStatus().equals("IDLE")
                && runtime.state()==dev.veltraph.client.fishing.FishingState.POST_CATCH_DELAY) {
            runtime.tick(new FishingInputs(true,true,services.hasFishingRod(),services.bobber().present(),false,System.currentTimeMillis()));
            if(runtime.state()==dev.veltraph.client.fishing.FishingState.FAILED) {stop("Server did not acknowledge reeling the bobber");return;}
        }
        boolean hunting=services.tickWormHunting(!rodSubflow && services.hasFishingRod() && !services.goldenFishActive()
                && (wormEmergency || runtime.state()!=dev.veltraph.client.fishing.FishingState.POST_CATCH_DELAY));
        if (!services.wormFailure().isEmpty()) { stop(services.wormFailure()); return; }
        if (hunting) { rodSubflow=true; return; }
        boolean wasSurfacing = surfacing;
        surfacing=surface.tick(config.get(FishingConfig.FISHING_SURFACE_ON_LOW_BUBBLES) || wormEmergency,
                services.submerged(),services.bubbles(),services.surfaceReachable(),
                wormEmergency ? 1.5 : config.get(FishingConfig.FISHING_SURFACE_BUBBLES),
                config.get(FishingConfig.FISHING_SURFACE_DEBOUNCE_MS),System.currentTimeMillis());
        // Low-air recovery owns input before the optional killer. Otherwise a chasing
        // creature can keep combat active until the player drowns with a sword equipped.
        if (surfacing && !wasSurfacing) { services.stopCombat(); services.stopTotem(); services.stopBait(); services.stopTrash(); services.stopSupport(); services.stopGrapple(); services.stopGoldenFish(); services.stopObstructionReturn(); }
        services.setSwimmingUp(surfacing);
        if (surfacing) return;
        if(!services.grappleFailure().isEmpty()){stop(services.grappleFailure());return;}
        if(services.grappleActive()){services.tickGrapple(false);if(!services.grappleFailure().isEmpty()){stop(services.grappleFailure());return;}rodSubflow=true;return;}
        if(!services.supportFailure().isEmpty()){stop(services.supportFailure());return;}
        if(services.supportActive()) {
            services.tickSupport(false);
            if(!services.supportFailure().isEmpty()){stop(services.supportFailure());return;}
            rodSubflow=true;return;
        }
        if(!services.baitFailure().isEmpty()){stop(services.baitFailure());return;}
        if(services.baitActive()) {
            services.tickBait(false);
            if(!services.baitFailure().isEmpty()){stop(services.baitFailure());return;}
            rodSubflow=true;return;
        }
        if(services.totemActive()) {
            services.tickTotem(false);
            if(!services.totemFailure().isEmpty()){stop(services.totemFailure());return;}
            rodSubflow=true;return;
        }
        if(!services.trashFailure().isEmpty()){stop(services.trashFailure());return;}
        if(services.trashActive()){services.tickTrash(false);if(!services.trashFailure().isEmpty()){stop(services.trashFailure());return;}rodSubflow=true;return;}
        // Finish an outstanding core reel before another controller may touch the rod.
        // Otherwise its "initial reel" could arrive after removal and cast accidentally.
        if (!rodSubflow && runtime.state()==dev.veltraph.client.fishing.FishingState.POST_CATCH_DELAY) {
            if (!services.hasFishingRod()) { stop("Fishing rod is no longer held"); return; }
            runtime.tick(new FishingInputs(true,true,services.hasFishingRod(),services.bobber().present(),false,System.currentTimeMillis()));
            if (runtime.state()==dev.veltraph.client.fishing.FishingState.FAILED) { stop("Server did not acknowledge reeling the bobber"); return; }
            if (runtime.state()==dev.veltraph.client.fishing.FishingState.POST_CATCH_DELAY) return;
        }
        // Support placement owns rod/slot/aim exclusively after outstanding reel acknowledgment.
        if(services.tickTotem(!rodSubflow&&!surfacing&&!services.goldenFishActive()&&services.hasFishingRod())) {
            if(!services.totemFailure().isEmpty()){stop(services.totemFailure());return;}
            services.stopCombat();rodSubflow=true;return;
        }
        if(services.tickGrapple(!rodSubflow&&!services.totemActive()&&!services.supportActive()&&!services.baitActive()&&!services.goldenFishActive()&&services.hasFishingRod())){if(!services.grappleFailure().isEmpty()){stop(services.grappleFailure());return;}services.stopCombat();rodSubflow=true;return;}
        if(services.tickSupport((!runtime.awaitingCastAcknowledgment()||services.bobber().present())&&!rodSubflow&&!services.totemActive()&&!services.baitActive()&&!services.goldenFishActive()&&services.hasFishingRod())) {
            if(!services.supportFailure().isEmpty()){stop(services.supportFailure());return;}
            services.stopCombat();rodSubflow=true;return;
        }
        if(services.tickBait(!rodSubflow&&!services.totemActive()&&!services.goldenFishActive()&&services.hasFishingRod()&&!services.submerged())) {
            if(!services.baitFailure().isEmpty()){stop(services.baitFailure());return;}
            services.stopCombat();rodSubflow=true;return;
        }
        if(services.tickTrash(!rodSubflow&&!services.totemActive()&&!services.supportActive()&&!services.baitActive()&&!services.goldenFishActive()&&services.hasFishingRod()&&!services.submerged())) {
            if(!services.trashFailure().isEmpty()){stop(services.trashFailure());return;}services.stopCombat();rodSubflow=true;return;
        }
        if (services.obstructionActive() && !services.hasFishingRod()) { stop("Fishing rod is no longer held"); return; }
        BobberFacts facts = services.bobber();
        boolean guardReady=services.catchGuardReady();
        if(!services.catchGuardFailure().isEmpty()){stop(services.catchGuardFailure());return;}
        boolean biting = guardReady && BiteDetector.isBite(facts, config.get(FishingConfig.FISHING_BITE_DETECTION),
                config.get(FishingConfig.FISHING_BOBBER_DIP_THRESHOLD));
        // Source FishingLoop: obstructed armor-stand casts return to the saved position
        // before incrementing the catch counter or issuing the ordinary catch reel.
        boolean returning=services.tickObstructionReturn(!rodSubflow && !services.goldenFishActive() && services.hasFishingRod()
                && biting && runtime.catchReady(System.currentTimeMillis()));
        if (!services.obstructionFailure().isEmpty()) { stop(services.obstructionFailure()); return; }
        if (returning) { rodSubflow=true; return; }
        if (services.goldenFishActive() && !services.hasFishingRod()) { stop("Fishing rod is no longer held"); return; }
        if (!services.goldenFishFailure().isEmpty()) { stop(services.goldenFishFailure()); return; }
        if (services.tickGoldenFish(config.get(FishingConfig.FISHING_GOLDEN_FISH))) { rodSubflow=true; return; }
        if (!(config.get(FishingConfig.FISHING_WORM_HUNTING) && services.submerged()) && services.tickCombat(!runtime.awaitingCastAcknowledgment()||facts.present())) { rodSubflow=true; services.setSwimmingUp(false); return; }
        if (!services.hasFishingRod()) { stop("Fishing rod is no longer held"); return; }
        if (rodSubflow) { runtime.resumeAfterSubflow(); rodSubflow=false; }

        FishingInputs in = new FishingInputs(
                isEnabled(), services.available(), services.hasFishingRod(),
                facts.present(), biting, System.currentTimeMillis());
        FishingAction action = runtime.tick(in);
        if (runtime.state() == dev.veltraph.client.fishing.FishingState.FAILED) {
            stop("Server did not acknowledge reeling the bobber");
            return;
        }
        switch (action) {
            case CAST -> {
                services.useRod();
                VeltraphLog.details("Fishing", "cast");
            }
            case REEL -> {
                services.useRod();
                VeltraphLog.basic("Fishing", "reel (catch #" + runtime.catchCount() + ")");
            }
            case NONE -> { /* waiting */ }
        }
    }

    @Override public boolean consumeExpectedTeleport(dev.veltraph.client.world.Vec3 position,long now){
        return isEnabled()&&scheduledTicket==null&&services.consumeExpectedTeleport(position,now);
    }

    @Override public boolean allowsScreen(String title) { return scheduledTicket==null&&(services.baitAllowsScreen(title)||services.supportAllowsScreen(title)||services.trashAllowsScreen(title)); }

    @Override public void onActionBar(String message) {
        if(isEnabled()&&scheduledTicket==null)services.fishingActionBar(message);
    }

    @Override
    public void onServerMessage(String message) {
        if (!isEnabled() || scheduledTicket!=null || message == null) return;
        services.baitMessage(message);
        services.supportMessage(message);
        String plain = message.replaceAll("§.", "").strip();
        if (config.get(FishingConfig.FISHING_GOLDEN_FISH)) {
            if (plain.startsWith("You spot a Golden Fish surface from beneath the lava!")) services.stopCombat();
            services.goldenFishMessage(plain);
        }
        // FishingLoop.lobbyProtectionEnabledSettings: the server rejects incompatible
        // water/lava rods. Do not interpret ordinary player chat containing this text.
        if (plain.startsWith("This Fishing Rod does not work in ")) {
            stop("Wrong Rod: " + plain);
        }
    }

    @Override public boolean ownsGameplayInput(){return scheduledTicket==null;}
    public boolean awaitingCoreCastAcknowledgment(){return runtime!=null&&runtime.awaitingCastAcknowledgment();}
    public Object runtimeIdentity(){return runtime;}
    public boolean isScheduledPaused(){return scheduledTicket!=null;}
    public boolean safeScheduledPauseBoundary(){
        return isEnabled()&&runtime!=null&&runtime.scheduledBoundary()&&!rodSubflow&&!surfacing
                &&services.available()&&services.rodSetupReady()&&services.rodSetupFailure().isEmpty()&&services.hasFishingRod()&&!services.bobber().present()
                &&!services.trashActive()&&!services.baitActive()&&!services.totemActive()&&!services.supportActive()&&!services.grappleActive()
                &&!services.goldenFishActive()&&!services.obstructionActive()&&services.wormStatus().equals("IDLE")
                &&services.baitFailure().isEmpty()&&services.totemFailure().isEmpty()&&services.supportFailure().isEmpty()
                &&services.trashFailure().isEmpty()&&services.catchGuardFailure().isEmpty()&&services.grappleFailure().isEmpty()&&services.goldenFishFailure().isEmpty()&&services.obstructionFailure().isEmpty()&&services.wormFailure().isEmpty();
    }
    public boolean suspendForScheduledRest(Object ticket,dev.veltraph.client.fishing.FishingScheduledHooks hooks){
        if(ticket==null||hooks==null||scheduledTicket!=null||!safeScheduledPauseBoundary()||!hooks.ready())return false;
        var settings=fishingSettings();
        if(!runtime.suspendScheduled(ticket,System.currentTimeMillis()))return false;
        scheduledTicket=ticket;scheduledHooks=hooks;scheduledRebound=false;scheduledSettings=settings;
        try{if(hooks.pause(ticket))return true;}catch(RuntimeException ignored){}
        stop("Scheduled fishing pause could not acquire native ownership");return false;
    }
    public boolean scheduledPauseOwnedBy(Object ticket){return ticket!=null&&isEnabled()&&scheduledTicket==ticket&&runtime!=null&&runtime.scheduledOwnedBy(ticket);}
    public boolean rebindForScheduledRest(Object ticket){
        if(!scheduledPauseOwnedBy(ticket)||scheduledRebound)return false;
        if(!scheduledSettings.equals(fishingSettings())){stop("Scheduled fishing settings changed");return false;}
        var engine=runtime;var state=runtime.state();int catches=runtime.catchCount();
        try{if(scheduledHooks.rebind(ticket)&&scheduledPauseOwnedBy(ticket)&&runtime==engine&&runtime.state()==state&&runtime.catchCount()==catches){scheduledRebound=true;return true;}}
        catch(RuntimeException ignored){}
        stop("Scheduled fishing native rebind failed");return false;
    }
    public boolean resumeAfterScheduledRest(Object ticket){
        if(!scheduledPauseOwnedBy(ticket)||!scheduledRebound)return false;
        if(!scheduledSettings.equals(fishingSettings())){stop("Scheduled fishing settings changed");return false;}
        try{if(!scheduledHooks.resume(ticket)||!runtime.resumeScheduled(ticket,System.currentTimeMillis())){stop("Scheduled fishing resume context changed");return false;}}
        catch(RuntimeException ignored){stop("Scheduled fishing resume failed");return false;}
        scheduledTicket=null;scheduledHooks=null;scheduledRebound=false;scheduledSettings=java.util.Map.of();return true;
    }
    private java.util.Map<String,Object> fishingSettings(){
        var values=new java.util.TreeMap<String,Object>();
        try{for(var holder:java.util.List.of(FishingConfig.class,dev.veltraph.client.fishing.trash.FishingTrashConfig.class,dev.veltraph.client.fishing.setup.FishingSetupConfig.class))for(var field:holder.getFields())if(field.get(null) instanceof dev.veltraph.client.config.ConfigKey<?> key)values.put(key.name(),config.get(key));}
        catch(ReflectiveOperationException failure){throw new IllegalStateException("Cannot pin fishing settings",failure);}
        return java.util.Collections.unmodifiableMap(values);
    }
    public void stopScheduledRest(Object ticket){if(ticket!=null&&scheduledTicket==ticket&&isEnabled())stop("Scheduled fishing rest stopped");}

    private void stop(String reason) {
        stopReason = reason;
        VeltraphLog.basic("Fishing", reason);
        setEnabled(false);
    }

    @Override
    public String diagnostics() {
        if (runtime == null) {
            return ID + " off";
        }
        return ID + " " + (isEnabled() ? "ON" : "off")
                + " | state=" + runtime.state()
                + (scheduledTicket==null?"":" | scheduled=PAUSED")
                + (stopReason.isEmpty() ? "" : " | stopped=" + stopReason)
                + " | surfacing=" + surfacing
                + " | return=" + services.obstructionStatus()
                + " | worm=" + services.wormStatus()
                + " | grapple="+services.grappleStatus()+" | support=" + services.supportStatus()
                + " | bait=" + services.baitStatus()
                + " | totem=" + services.totemStatus()
                + " | golden=" + services.goldenFishStatus()
                + " | trash="+services.trashStatus()
                + " | slug="+services.catchGuardStatus()
                + " | catches=" + runtime.catchCount()
                + " | setup=" + services.rodSetupStatus()
                + " | rod=" + services.hasFishingRod()
                + " | bobber=" + services.bobber().present()
                + (runtime.lastBiteTooSoon() ? " | (too soon)" : "");
    }
    /** Genuine installed Fishing owner source for the exact ordinary Leg factory. */
    public final class NativeNormalReturnSource {
        private final FishingRuntime engine=runtime;private final Object ticket=scheduledTicket,hooks=scheduledHooks;
        private final boolean rebound=scheduledRebound;private final java.util.Map<String,Object> settings=scheduledSettings;
        private final dev.veltraph.client.core.ModuleManager root;
        private final dev.veltraph.client.navigation.mc.LegacyTravelPublicationLease.Leg leg;
        private final dev.veltraph.client.fishing.mc.McFishingServices actualServices;
        private final dev.veltraph.client.fishing.mc.McFishingServices.NativeNormalReturnSource actual;
        private final dev.veltraph.client.core.ModuleManager.ModuleTickDeclaration declaration;
        private final dev.veltraph.client.VeltraphBootstrap.RootOriginalInputPublication publication;
        private NativeNormalReturnSource(dev.veltraph.client.core.ModuleManager root,dev.veltraph.client.navigation.mc.LegacyTravelPublicationLease.Leg leg,
                dev.veltraph.client.fishing.mc.McFishingServices services,dev.veltraph.client.fishing.mc.McFishingServices.NativeNormalReturnSource actual){
            this.root=root;this.leg=leg;actualServices=services;this.actual=actual;declaration=actual.declaration();publication=actual.publication();
        }
        /** Additional raw veto only after original provider getters; no role/provider/config/slot recursion. */
        public boolean nativeCurrent(){return actual.nativeCurrent()&&isEnabled()&&runtime==engine&&engine!=null&&!surfacing&&scheduledTicket==ticket&&ticket==null
            &&scheduledHooks==hooks&&hooks==null&&scheduledRebound==rebound&&!rebound&&scheduledSettings==settings
            &&services==actualServices&&config==dev.veltraph.client.VeltraphBootstrap.config()&&root==dev.veltraph.client.VeltraphBootstrap.moduleManager()
            &&declaration!=null&&declaration.registryIdentity()==root&&declaration.module()==FishingModule.this
            &&(declaration.issuingCurrent(FishingModule.this)||declaration.normalReturnedCurrent(FishingModule.this))
            &&dev.veltraph.client.VeltraphBootstrap.originalNativeGuiAffiliationCurrent(publication,net.minecraft.client.Minecraft.getInstance());}
        public boolean belongsTo(dev.veltraph.client.navigation.mc.LegacyTravelPublicationLease.Leg exact){return leg==exact&&nativeCurrent();}
    }
    public NativeNormalReturnSource captureNativeNormalReturnSource(dev.veltraph.client.navigation.mc.LegacyTravelPublicationLease.Leg exact){
        boolean caller=StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).walk(stream->{var first=stream.filter(f->f.getDeclaringClass()!=FishingModule.class).findFirst().orElse(null);
            return first!=null&&first.getDeclaringClass()==dev.veltraph.client.navigation.mc.LegacyTravelPublicationLease.Leg.class&&first.getMethodName().equals("ownedUseSnapshot");});
        if(!caller||exact==null||!isEnabled()||runtime==null||surfacing||scheduledTicket!=null||scheduledHooks!=null||scheduledRebound
            ||!(services instanceof dev.veltraph.client.fishing.mc.McFishingServices actualServices))return null;
        var root=dev.veltraph.client.VeltraphBootstrap.moduleManager();if(root==null||config!=dev.veltraph.client.VeltraphBootstrap.config())return null;
        var actual=actualServices.captureNativeNormalReturnSource(this,exact);if(actual==null)return null;
        var result=new NativeNormalReturnSource(root,exact,actualServices,actual);
        var declaration=result.declaration;var publication=result.publication;
        if(!result.nativeCurrent()||declaration==null||declaration.registryIdentity()!=root||declaration.module()!=this||publication==null
            ||!(declaration.issuingCurrent(this)||declaration.normalReturnedCurrent(this))
            ||dev.veltraph.client.VeltraphBootstrap.originalRootPublicationFor(this)!=publication)return null;
        // Read the original final Fishing role getter itself between the genuine declaration/publication guards.
        if(!ownsGameplayInput())return null;
        if(root!=dev.veltraph.client.VeltraphBootstrap.moduleManager()
            ||!(declaration.issuingCurrent(this)||declaration.normalReturnedCurrent(this))
            ||dev.veltraph.client.VeltraphBootstrap.originalRootPublicationFor(this)!=publication||!result.nativeCurrent())return null;
        return result.nativeCurrent()?result:null;
    }
    /** Cleanup-only source for the genuine private helper/native old-loan restoration call. */
    public final class NativeNormalReturnCleanupSource {
        private final FishingRuntime engine=runtime;private final Object ticket=scheduledTicket,hooks=scheduledHooks;
        private final boolean rebound=scheduledRebound;private final java.util.Map<String,Object> settings=scheduledSettings;
        private final dev.veltraph.client.core.ModuleManager root;
        private final dev.veltraph.client.navigation.mc.LegacyTravelPublicationLease.Leg leg;
        private final dev.veltraph.client.fishing.mc.McFishingServices actualServices;
        private final dev.veltraph.client.fishing.mc.McFishingServices.NativeNormalReturnCleanupSource actual;
        private NativeNormalReturnCleanupSource(dev.veltraph.client.core.ModuleManager root,dev.veltraph.client.navigation.mc.LegacyTravelPublicationLease.Leg leg,
                dev.veltraph.client.fishing.mc.McFishingServices services,dev.veltraph.client.fishing.mc.McFishingServices.NativeNormalReturnCleanupSource actual){
            this.root=root;this.leg=leg;actualServices=services;this.actual=actual;
        }
        public boolean nativeCurrent(){return actual.nativeCurrent()&&runtime==engine&&engine!=null&&scheduledTicket==ticket&&ticket==null
            &&scheduledHooks==hooks&&hooks==null&&scheduledRebound==rebound&&!rebound&&scheduledSettings==settings
            &&services==actualServices&&config==dev.veltraph.client.VeltraphBootstrap.config()&&root==dev.veltraph.client.VeltraphBootstrap.moduleManager();}
        public boolean belongsTo(dev.veltraph.client.navigation.mc.LegacyTravelPublicationLease.Leg exact){return leg==exact&&nativeCurrent();}
    }
    public NativeNormalReturnCleanupSource captureNativeNormalReturnCleanupSource(dev.veltraph.client.navigation.mc.LegacyTravelPublicationLease.Leg exact){
        boolean caller=StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).walk(stream->{var first=stream.filter(f->f.getDeclaringClass()!=FishingModule.class).findFirst().orElse(null);
            return first!=null&&first.getDeclaringClass()==dev.veltraph.client.navigation.mc.LegacyTravelPublicationLease.Leg.class&&first.getMethodName().equals("normalReturnCleanupSnapshot");});
        if(!caller||exact==null||runtime==null||scheduledTicket!=null||scheduledHooks!=null||scheduledRebound
            ||!(services instanceof dev.veltraph.client.fishing.mc.McFishingServices actualServices))return null;
        var root=dev.veltraph.client.VeltraphBootstrap.moduleManager();if(root==null||config!=dev.veltraph.client.VeltraphBootstrap.config())return null;
        var actual=actualServices.captureNativeNormalReturnCleanupSource(this,exact);if(actual==null)return null;
        var result=new NativeNormalReturnCleanupSource(root,exact,actualServices,actual);return result.nativeCurrent()?result:null;
    }
}
