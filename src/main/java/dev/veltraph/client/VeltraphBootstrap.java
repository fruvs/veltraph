package dev.veltraph.client;

import dev.veltraph.client.config.ConfigService;
import dev.veltraph.client.config.FarmingConfig;
import dev.veltraph.client.config.JsonConfigService;
import dev.veltraph.client.config.PersistableKeys;
import dev.veltraph.client.core.ModuleActivationGuard;
import dev.veltraph.client.core.ModuleManager;
import dev.veltraph.client.farming.domain.FarmLayoutRegistry;
import dev.veltraph.client.farming.module.FarmingModules;
import dev.veltraph.client.game.GameContextService;
import dev.veltraph.client.game.mc.McGameContextService;
import dev.veltraph.client.core.Module;
import dev.veltraph.client.hud.mc.VeltraphHudOverlay;
import dev.veltraph.client.hydra.HydraAntiStaff;
import dev.veltraph.client.hydra.HydraResponder;
import dev.veltraph.client.hydra.mc.HydraAntiStaffAdapter;
import dev.veltraph.client.hydra.mc.McHydraResponder;
import dev.veltraph.client.garden.visitor.VisitorAdvisor;
import dev.veltraph.client.garden.visitor.mc.McVisitorScreenSource;
import dev.veltraph.client.fishing.module.FishingModule;
import dev.veltraph.client.fishing.mc.McFishingServices;
import dev.veltraph.client.combat.heal.CombatAutoHeal;
import dev.veltraph.client.combat.heal.mc.McHealServices;
import dev.veltraph.client.foraging.module.TreeChopModule;
import dev.veltraph.client.foraging.mc.McForagingServices;
import dev.veltraph.client.hydra.mc.McHydraAlertSink;
import dev.veltraph.client.input.ScriptingInputLock;
import dev.veltraph.client.log.DebugLogController;
import dev.veltraph.client.log.FileLogSink;
import dev.veltraph.client.log.VeltraphLog;
import dev.veltraph.client.log.VeltraphLogger;
import dev.veltraph.client.rewarp.AutoRewarpRuntime;
import dev.veltraph.client.rewarp.RewarpCommandHandler;
import dev.veltraph.client.rewarp.RewarpService;
import dev.veltraph.client.rewarp.RewarpWarpExecutor;
import dev.veltraph.client.rewarp.StuckTracker;
import dev.veltraph.client.rewarp.mc.RewarpCapture;
import dev.veltraph.client.rewarp.mc.FarmMaintenanceCoordinator;
import dev.veltraph.client.service.ChatService;
import dev.veltraph.client.service.FarmServices;
import dev.veltraph.client.service.SystemClockService;
import dev.veltraph.client.service.mc.McChatService;
import dev.veltraph.client.service.mc.McFarmServices;
import dev.veltraph.client.service.mc.McServerChatService;
import dev.veltraph.client.service.mc.McServerCommandService;
import dev.veltraph.client.ui.VeltraphScreen;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * Framework wiring for Veltraph, kept separate from the Fabric entrypoint so the wiring is plain code.
 *
 * <p>Builds the config service, farming registry, module manager, the local game-context + chat
 * services, and the shared {@link ModuleActivationGuard}; registers the farming modules, keybinds,
 * client commands, and the pause-menu button; and drives the per-tick update. Modules tick only while
 * a world and player are present, and a running module is auto-stopped when its location becomes
 * invalid (unless it declares a travel/setup phase).
 */
public final class VeltraphBootstrap {

    private static final Logger LOGGER = LoggerFactory.getLogger("Veltraph");

    private static boolean initialized;

    private static ModuleManager moduleManager;
    private static ConfigService config;
    private static JsonConfigService jsonConfig;
    private static java.nio.file.Path configFile;
    private static long lastConfigSaveTick;
    private static FarmLayoutRegistry farmRegistry;
    private static dev.veltraph.client.hunting.ShardCaptureModule originalShardCapture;
    private static dev.veltraph.client.garden.copy.GardenAnalyzerModule originalGardenAnalyzer;
    private static SystemClockService clock;
    private static GameContextService gameContext;
    private static dev.veltraph.client.garden.visitor.history.mc.McVisitorHistoryPacketObserver visitorHistoryObserver;
    private static VisitorHistoryDeferredRegistration visitorHistoryDeferred;
    /** Private affiliation token; creating it schedules no work and grants no early-thread exception. */
    private static final class VisitorHistoryDeferredRegistration {
        private final Minecraft client;private final GameContextService location;
        private boolean stopped;private dev.veltraph.client.garden.visitor.history.mc.McVisitorHistoryPacketObserver installed;
        private VisitorHistoryDeferredRegistration(Minecraft client,GameContextService location){this.client=client;this.location=location;}
    }
    private static FarmServices farmServices;
    private static McFishingServices nativeFishingServices;
    private static FishingModule registeredFishingModule;
    private static ChatService chat;
    private static ModuleActivationGuard guard;
    private static DebugLogController debugLog;
    private static RewarpService rewarpService;
    private static RewarpCommandHandler rewarpCommand;
    private static AutoRewarpRuntime autoRewarp;
    private static dev.veltraph.client.rewarp.mc.McFarmCycleCoordinator farmCycle;
    private static dev.veltraph.client.rewarp.mc.McSuspendedFarmReturnCoordinator suspendedFarmReturn;
    private static RootFarmReturnRegistration rootFarmReturnRegistration;
    private static RootFarmReturnTick activeFarmReturnTick;
    private static RootFarmReturnAdmission activeFarmReturnAdmission;
    private static RootGameplayReturnFrame activeGameplayReturnFrame;
    private static RootFarmReturnCloseCause activeFarmReturnManualClose;
    private static dev.veltraph.client.rewarp.mc.McFarmAutoRecoveryCoordinator farmAutoRecovery;
    private static dev.veltraph.client.rewarp.mc.FarmMaintenanceCoordinator farmMaintenance;
    private static dev.veltraph.client.foraging.maintenance.mc.McForagingMaintenanceCoordinator foragingMaintenance;
    private static dev.veltraph.client.galatea.workflow.McGalateaWorkflowFamily galateaWorkflows;
    private static dev.veltraph.client.inventory.selling.McFarmAutoSellModule farmAutoSell;
    private static dev.veltraph.client.inventory.selling.CapturedGeorgeHelper capturedGeorgeHelper;
    private static dev.veltraph.client.scheduling.mc.McScriptScheduler scriptScheduler;
    private static long lastScheduledRewarpMillis=-1,scheduledConnectionSince;
    private static Object scheduledObservedConnection;
    private static dev.veltraph.client.rewarp.SavedAnchorRecoveryModule savedAnchorRecovery;
    private static HydraAntiStaff hydra;
    private static HydraAntiStaffAdapter hydraAdapter;
    private static VeltraphHudOverlay hudOverlay;
    private static dev.veltraph.client.metrics.mc.McLocalMetricsCollector localMetrics;
    private static dev.veltraph.client.metrics.mc.McOwnerMetricsAdapter localMetricsOwners;
    private static dev.veltraph.client.metrics.mc.McSellingFinancialAdapter localFinancial;
    private static dev.veltraph.client.garden.pests.mc.McPestTrapMaintenanceModule trapMaintenance;
    private static dev.veltraph.client.garden.admission.mc.McGardenThresholdAdmission gardenThresholdAdmission;
    private static final dev.veltraph.client.garden.pests.PestAutoAdmissionTracker pestAutoAdmissionTracker=new dev.veltraph.client.garden.pests.PestAutoAdmissionTracker();
    private static dev.veltraph.client.garden.pests.mc.McNearbyPestAdmission nearbyPestAdmission;
    private static dev.veltraph.client.garden.pests.mc.McPestEarlyLoadoutObservation earlyLoadoutObservation;
    private static RootEarlyLoadoutObservationSource rootEarlyLoadoutObservationSource;
    private static dev.veltraph.client.garden.jacob.mc.McJacobRewardModule jacobReward;
    private static final dev.veltraph.client.garden.jacob.JacobContestTracker jacobTracker=new dev.veltraph.client.garden.jacob.JacobContestTracker();
    private static dev.veltraph.client.garden.jacob.JacobContestTracker.Ended lastJacobEnded;
    private static dev.veltraph.client.metrics.LocalSessionMetrics.Snapshot localMetricsSnapshot;
    private static java.util.List<dev.veltraph.client.hud.HudLine> localMetricsHud=java.util.List.of();
    private static String localMetricsSignature,localMetricsAttemptedSignature,localMetricsStatus="Disabled";
    private static Object localMetricsPlayer,localMetricsWorld,localMetricsConnection;
    private static long localMetricsMessageSerial;
    private static dev.veltraph.client.service.mc.NativeActionBarSignal.Subscription actionBarSubscription;
    private static dev.veltraph.client.service.mc.NativeHealFeedbackSignal.Subscription healFeedbackSubscription;
    private static VisitorAdvisor visitorAdvisor;
    private static dev.veltraph.client.failsafe.McGeneralFailsafe generalFailsafe;
    private static dev.veltraph.client.greenhouse.GreenhouseModule greenhouseModule;
    private static dev.veltraph.client.chocolate.ChocolateFactoryModule chocolateFactoryModule;
    private static dev.veltraph.client.observation.unexpected.McUnexpectedBehaviorMonitor unexpectedObserver;
    private static dev.veltraph.client.hydra.mc.McCropSpeciesReplantObserver cropSpeciesObserver;
    private static long cropSpeciesLastAlert=Long.MIN_VALUE;
    private static String unexpectedOwner = "observer";
    private static long unexpectedGeneration, unexpectedLastAlert = Long.MIN_VALUE;
    private static final dev.veltraph.client.observation.unexpected.UnexpectedStopFrame unexpectedStopFrame=
            new dev.veltraph.client.observation.unexpected.UnexpectedStopFrame();
    private static Object lastGeneralPlayer, lastGeneralConnection;
    private static boolean survivalStopSuppressed, previousHealEnabled;
    private static java.util.UUID survivalStoppedPlayerId;
    private static Object survivalStoppedConnection;
    private static dev.veltraph.client.notification.LocalNotifications notifications;
    private static final dev.veltraph.client.combat.heal.SkyBlockHealth skyBlockHealth = new dev.veltraph.client.combat.heal.SkyBlockHealth();
    private static CombatAutoHeal combatAutoHeal;
    private static dev.veltraph.client.dev.DeveloperModeController developerMode;
    private static dev.veltraph.client.dev.DevTestRegistry devTests;
    private static dev.veltraph.client.dev.DevTestContext devTestContext;
    private static ClientLevel lastWorld;
    private static final dev.veltraph.client.combat.sven.InstalledSvenWarpLifecycle svenWarpLifecycle =
            new dev.veltraph.client.combat.sven.InstalledSvenWarpLifecycle();

    private static final dev.veltraph.client.combat.ghost.InstalledGhostWarpLifecycle ghostWarpLifecycle =
            new dev.veltraph.client.combat.ghost.InstalledGhostWarpLifecycle();

    private static String ghostWarpSignature;private static Object ghostInitialActivation;
    private static boolean ghostTransportQuiescent,ghostAwaitingFooting;
    private static dev.veltraph.client.inventory.bottle.BottleAcquisitionModule registeredBottleAcquisition;
    private static dev.veltraph.client.inventory.bottle.BottleAcquisitionRuntime.Travel bottleWarpTravel;
    private static String bottleWarpSettings;
    private static java.util.Map<net.minecraft.world.entity.EquipmentSlot,net.minecraft.world.item.ItemStack> bottleWarpGear=java.util.Map.of();
    private static boolean bottleTransportQuiescent;


    private static final DateTimeFormatter LINE_TIME = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");
    private static final DateTimeFormatter FILE_STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm-ss");

    private VeltraphBootstrap() {
    }

    public static synchronized void init() {
        if (initialized) {
            return;
        }
        initialized = true;
        try{originalControlStartup=new RootOriginalControlStartup();ScriptingInputLock.beginOriginalRootInitialization(originalControlStartup);}
        catch(RuntimeException|Error unavailable){originalControlStartup=null;}
        boolean originalInitializationReturned=false;
        try{
        Minecraft client = Minecraft.getInstance();
        farmRegistry = FarmLayoutRegistry.createDefault();
        clock = new SystemClockService();
        gameContext = new McGameContextService(client);
        // ClientEntrypoint runs inside Minecraft construction before its native main-thread affiliation exists.
        // Retain only exact Root client/location/generation identity; actual install happens on an ordinary tick.
        var historyRegistration=new VisitorHistoryDeferredRegistration(client,gameContext);
        visitorHistoryDeferred=historyRegistration;
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents.CLIENT_STOPPING.register(stoppingClient->
                stopVisitorHistoryObservation(historyRegistration,stoppingClient));
        chat = new McChatService(client);

        // Local debug logger: writes only to <gameDir>/logs/veltraph/ (no network/telemetry).
        VeltraphLogger logger = new VeltraphLogger(null,
                () -> LocalTime.now().format(LINE_TIME),
                msg -> { LOGGER.warn("[Veltraph] {}", msg); safeChat(msg); });
        VeltraphLog.set(logger);

        // Persistent JSON config (Target 3): <configDir>/veltraph/config.json. Created/loaded before the
        // debug-log controller so the persisted DEBUG_LOG_* + every setting survive restarts. Local file
        // only — never transmitted.
        jsonConfig = new JsonConfigService(PersistableKeys.all());
        config = jsonConfig;
        configFile = FabricLoader.getInstance().getConfigDir().resolve("veltraph").resolve("config.json");

        // Rewarp / return-to-location (Target 2): store is populated from the config file on load.
        rewarpService = new RewarpService();
        jsonConfig.load(configFile, rewarpService.store());

        debugLog = new DebugLogController(config, logger, VeltraphBootstrap::createLogSink,
                VeltraphBootstrap::safeChat);
        debugLog.initFromConfig();

        // Developer Mode (Target 1, local test harness): when persisted ON, force verbose logging
        // immediately so it is active from the first tick of this launch. Local only; see
        // docs/DEVELOPER_MODE_NOTES.md.
        developerMode = new dev.veltraph.client.dev.DeveloperModeController(config, debugLog);
        developerMode.initFromConfig();

        // Normal in-world runtime path: services backed by the live Minecraft client.
        FarmServices services = createOriginalFarmServices(client, clock, config);
        farmServices = services;

        RewarpCapture capture = new RewarpCapture(client, services.player(), gameContext);
        var nativeRewarpCommands=new McServerCommandService(client);
        RewarpWarpExecutor rewarpWarp = new RewarpWarpExecutor(command->{
            boolean sent=nativeRewarpCommands.sendCommand(command);
            if(sent)lastScheduledRewarpMillis=services.clock().nowMillis();return sent;
        });
        rewarpCommand = new RewarpCommandHandler(rewarpService,
                () -> capture.capture(System.currentTimeMillis()),
                VeltraphBootstrap::safeChat,
                System::currentTimeMillis,
                rewarpWarp,
                () -> config.get(FarmingConfig.REWARP_COMMAND));

        moduleManager = new ModuleManager();
        rootRotationProvider=new RootRotationAuthorityProvider(client,moduleManager,config);
        var originalRootInstallation=dev.veltraph.client.service.mc.McOwnedRotation.installRoot(client,rootRotationProvider);
        rootInputReleaseRegistration=new RootInputReleaseRegistration(rootRotationProvider,services,originalRootInstallation);
        try{
            originalNativeEnrollment=new RootOriginalNativeEnrollment(rootInputReleaseRegistration);
            originalEnableRegistration=moduleManager.observeActualEnables(new ModuleManager.ActualEnableObserver(){
                public Object before(ModuleManager.ActualEnableStart actual){return captureOriginalRootEnable(actual);}
                public void after(ModuleManager.OwnEnableReceipt actual,Object captured){completeOriginalRootEnable(actual,captured);}
            });
            originalDisableRegistration=moduleManager.observeActualDisables(new ModuleManager.ActualDisableObserver(){
                public Object before(ModuleManager.ActualDisableStart actual){return captureOriginalRootDisableWithRollback(actual);}
                public void after(ModuleManager.OwnDisableReceipt actual,Object captured){completeOriginalRootDisableWithRollback(actual,captured);}
                public void closed(ModuleManager.ActualDisableStart actual,Object captured){
                    captured=originalDisableCaptured(captured);
                    if(captured instanceof RootPassiveTransition passive&&passive.terminalStop!=null&&passive.disabling==actual&&!passive.closed)closeOriginalPassiveTransition(passive);
                    if(captured instanceof RootOriginalTerminalDisable owner&&owner.start==actual){
                        if(owner.terminal.ownerReturned==null)owner.terminal.revoked=true;
                        if(owner.terminal.pendingOwnerDisable==owner)owner.terminal.pendingOwnerDisable=null;
                    }
                }
            });
            originalModuleTickRegistration=moduleManager.observeActualModuleTicks(new ModuleManager.ActualModuleTickObserver(){
                public Object before(ModuleManager.ModuleTickDeclaration actual){return captureOriginalPassiveTick(actual);}
                public void after(ModuleManager.ModuleTickDeclaration actual,Object captured){finishOriginalPassiveTick(actual,captured);}
                public void closed(ModuleManager.ModuleTickDeclaration actual,Object captured){closeOriginalPassiveTick(actual,captured);}
            });
        }catch(RuntimeException|Error unavailable){originalNativeEnrollment=null;originalEnableRegistration=null;originalDisableRegistration=null;originalModuleTickRegistration=null;}
        dev.veltraph.client.service.mc.McInputService.registerCanonicalRootRelease(rootInputReleaseRegistration);
        rootManualStopRegistration=moduleManager.observeManualStops(new ModuleManager.ManualStopObserver(){
            public Object before(ModuleManager.ManualStopStart actual){
                var prior=activeFarmReturnManualClose;if(prior!=null)prior.revoked=true;
                var cause=captureFarmReturnManualClose(client,actual);activeFarmReturnManualClose=cause;
                return new RootManualStopCapture(captureOriginalRootManualStop(client,actual),cause);
            }
            public void after(ModuleManager.ManualStopReceipt actual,Object captured){
                if(captured instanceof RootManualStopCapture original){
                    try{finishOriginalRootManualStop(actual,original.logical());}
                    finally{if(activeFarmReturnManualClose==original.close())activeFarmReturnManualClose=null;}
                }else finishOriginalRootManualStop(actual,captured);
            }
            public void closedNoGameplayStop(ModuleManager.OwnNoGameplayStopReceipt actual,Object captured){
                captured=originalManualLogicalCapture(captured);
                try{retireRootNoGameplayLogicalStop(actual,captured);}finally{finishOriginalRetiredIdle(actual,captured);}
            }
        });
        Runnable returnFallback=()->{
            if(client.player!=null&&client.player.isAlive()&&client.level!=null&&client.gui.screen()==null
                    &&gameContext.current().isArea(dev.veltraph.client.game.GameArea.GARDEN))
                rewarpWarp.execute(config.get(FarmingConfig.REWARP_COMMAND));
            else safeChat("Veltraph: no applicable saved return or return command; macro stopped.");
        };
        savedAnchorRecovery=new dev.veltraph.client.rewarp.SavedAnchorRecoveryModule(moduleManager,
                ()->gameContext.current().area(),new dev.veltraph.client.rewarp.mc.McSavedAnchorRecovery(client,services,config),returnFallback);
        moduleManager.register(savedAnchorRecovery);
        FarmingModules.registerAll(moduleManager, services, config, farmRegistry, rewarpService);
        moduleManager.register(new dev.veltraph.client.garden.visitor.mc.McVisitorServeModule(client,services,config));
        moduleManager.register(new dev.veltraph.client.garden.pests.mc.McPestSprayModule(client,services,config));
        moduleManager.register(new dev.veltraph.client.garden.pests.mc.McPestTrapModule(client,services,config));
        moduleManager.register(new dev.veltraph.client.garden.pests.mc.McPestRepellentModule(client,services,config));
        moduleManager.register(new dev.veltraph.client.garden.pests.mc.McPestVacuumModule(client,services,config));
        moduleManager.register(new dev.veltraph.client.garden.pests.mc.McPestSupplyRefillModule(client,services,config));
        originalShardCapture=new dev.veltraph.client.hunting.ShardCaptureModule(client,config);
        moduleManager.register(originalShardCapture);
        moduleManager.register(new dev.veltraph.client.hunting.ShardWorkflowModule(client,config));
        moduleManager.register(new dev.veltraph.client.hunting.ActiveHuntingModule(client,config,services));
        greenhouseModule = new dev.veltraph.client.greenhouse.GreenhouseModule(client,config,services);
        greenhouseModule.bindPersistence(VeltraphBootstrap::confirmConfigSaved);
        moduleManager.register(greenhouseModule);
        chocolateFactoryModule = new dev.veltraph.client.chocolate.ChocolateFactoryModule(client,config);
        chocolateFactoryModule.bindPersistence(VeltraphBootstrap::confirmConfigSaved);
        moduleManager.register(chocolateFactoryModule);
        moduleManager.register(new dev.veltraph.client.inventory.BazaarAutoSellModule(client,config));
        moduleManager.register(new dev.veltraph.client.inventory.selling.BazaarCustodyModule(client,config,moduleManager));
        moduleManager.register(new dev.veltraph.client.inventory.selling.GuardedSellingModule(client,services,config));
        moduleManager.register(new dev.veltraph.client.inventory.equipment.EquipmentWorkflowModule(client,config));
        moduleManager.register(new dev.veltraph.client.inventory.abiphone.AbiphoneCallModule(client,config));
        moduleManager.register(new dev.veltraph.client.skills.AlchemyModule(client,config,services));
        moduleManager.register(new dev.veltraph.client.skills.AlchemyFlowModule(client,config,services));
        moduleManager.register(new dev.veltraph.client.skills.CarpentryModule(client,config,services));
        moduleManager.register(new dev.veltraph.client.skills.CarpentryFlowModule(client,config,services));
        moduleManager.register(new dev.veltraph.client.skills.BottleStoreModule(client,config,services));
        moduleManager.register(new dev.veltraph.client.inventory.bottle.BottleFillModule(client,config,services));
        var bottleAcquisition=new dev.veltraph.client.inventory.bottle.BottleAcquisitionModule(client,config,services,gameContext);
        moduleManager.register(bottleAcquisition);registeredBottleAcquisition=bottleAcquisition;
        moduleManager.register(new dev.veltraph.client.galatea.agatha.AgathaContestModule(
                "galatea_agatha_claim","Agatha Contest Claim",client,config,services));
        moduleManager.register(new dev.veltraph.client.galatea.trash.LushlilacTrashModule(client,services,config));
        var gardenBlueprintStore=new dev.veltraph.client.garden.copy.GardenBlueprintStore(
                configFile.getParent().resolve("garden-blueprints"));
        var gardenAnalyzerModule=new dev.veltraph.client.garden.copy.GardenAnalyzerModule(client,config,gardenBlueprintStore);
        var gardenCopyModule=new dev.veltraph.client.garden.copy.GardenCopyModule(client,services,config);
        moduleManager.register(gardenAnalyzerModule);originalGardenAnalyzer=gardenAnalyzerModule;
        moduleManager.register(gardenCopyModule);
        moduleManager.register(new dev.veltraph.client.skills.FairySoulModule(client,config,services,
                configFile.getParent().resolve("fairy-claims")));
        moduleManager.register(new dev.veltraph.client.mining.vein.VeinMiningModule(client,services,config));
        moduleManager.register(new dev.veltraph.client.mining.commission.CommissionModule(client,services,config));
        var miningServices=new dev.veltraph.client.mining.mc.McMiningServices(client,services,config);
        moduleManager.register(new dev.veltraph.client.mining.module.MiningModule("cobblestone","Cobblestone Mining",
                dev.veltraph.client.mining.MiningMode.COBBLESTONE,miningServices,config));
        moduleManager.register(new dev.veltraph.client.mining.module.MiningModule("mine_down","Mine Down",
                dev.veltraph.client.mining.MiningMode.MINE_DOWN,miningServices,config));
        moduleManager.register(new dev.veltraph.client.mining.module.MiningModule("echo","Echo Mining",
                dev.veltraph.client.mining.MiningMode.ECHO,miningServices,config,
                new dev.veltraph.client.mining.recorded.McRecordedEchoPort(client,miningServices)));
        // Core fishing macro (Batch 4): cast -> wait bite -> reel -> recast. Local only (rod right-click +
        // local bobber polling); runs in any SkyBlock water. See docs/FISHING_NOTES.md.
        var fishingServices=createOriginalFishingServices(client).withFishingArea(()->gameContext.current().area()).withGoldenFish(services.rotation()).withCombat(
                new dev.veltraph.client.fishing.mc.FishingCombat(client,config,services));
        var fishingModule=new FishingModule(fishingServices,config,new java.util.Random());
        fishingServices.bindOwner(fishingModule);
        nativeFishingServices=fishingServices;registeredFishingModule=fishingModule;
        moduleManager.register(fishingModule);
        // Tree-chop foraging macro (Batch 7): hold-to-break the crosshair log with reach/timeout/blacklist
        // failsafes. Local only (attack-key hold + local block read). See docs/FORAGING_NOTES.md.
        moduleManager.register(new TreeChopModule(
                new McForagingServices(client, config, services.breaking()), config));
        moduleManager.register(new dev.veltraph.client.foraging.supply.ForagingSupplyModule(client,config,services));
        moduleManager.register(new dev.veltraph.client.foraging.replant.ForagingReplantModule(
                "island_foraging_replant","Private Island Dark Oak",client,config,services));
        moduleManager.register(new dev.veltraph.client.foraging.module.AutoForagingModule(
                "park_foraging", "Park Foraging", dev.veltraph.client.game.GameArea.THE_PARK, client, config, services));
        moduleManager.register(new dev.veltraph.client.foraging.module.AutoForagingModule(
                "hub_foraging", "Hub Foraging", dev.veltraph.client.game.GameArea.HUB, client, config, services));
        var figModule=new dev.veltraph.client.galatea.GalateaForagingModule("galatea_fig","Fig Trees",
                dev.veltraph.client.galatea.GalateaPlanner.Kind.FIG,client,config,services);
        moduleManager.register(figModule);
        var figScaffoldUse=dev.veltraph.client.galatea.McFigOwnedUseBridge.install(client,
                figModule::scaffoldUseBinding,()->moduleManager.inputOwner().orElse(null));
        figModule.bindRootScaffoldUse(figScaffoldUse);
        dev.veltraph.client.galatea.McFigHarvestPickupBridge.install(client,figModule::figHarvestPickupBinding,
                ()->moduleManager.inputOwner().orElse(null));
        dev.veltraph.client.galatea.McFigToolWearBridge.install(client,figModule::figToolWearBinding,
                ()->moduleManager.inputOwner().orElse(null));
        figModule.bindRootScaffoldNetworkGap(()->dev.veltraph.client.galatea.McFigPacketGapObservation.observed(client));
        figModule.bindRootScaffoldPermission(()->figModule.isEnabled()
                &&moduleManager.inputOwner().orElse(null)==figModule
                &&dev.veltraph.client.service.mc.McOwnedRotation.installed(client)
                &&dev.veltraph.client.service.mc.McOwnedRotation.authority(client)==figModule
                &&client.player!=null&&client.level!=null&&client.getConnection()!=null&&client.player.isAlive()
                &&client.gui.screen()==null&&client.player.containerMenu==client.player.inventoryMenu
                &&client.player.containerMenu.getCarried().isEmpty());
        moduleManager.register(new dev.veltraph.client.galatea.GalateaForagingModule("galatea_mangrove","Mangrove Trees",
                dev.veltraph.client.galatea.GalateaPlanner.Kind.MANGROVE,client,config,services));
        moduleManager.register(new dev.veltraph.client.galatea.GalateaForagingModule("galatea_materials","Galatea Materials",
                dev.veltraph.client.galatea.GalateaPlanner.Kind.MATERIALS,client,config,services));
        guard = new ModuleActivationGuard(moduleManager, gameContext, chat);
        try{originalActivationRegistration=guard.observeOriginalActivations(new ModuleActivationGuard.OriginalActivationObserver(){
            public Object beforeRequest(ModuleActivationGuard.OriginalActivationStart actual){return captureOriginalEnableAdmission(actual);}
            public void beforeEnabled(ModuleActivationGuard.OriginalActivationStart actual,Object captured){validateOriginalEnableAdmission(actual,captured);}
            public void afterEnabled(ModuleActivationGuard.OriginalActivationStart actual,Object captured){finishOriginalEnableAdmission(actual,captured);}
            public void closedRequest(ModuleActivationGuard.OriginalActivationStart actual,Object captured){closeOriginalPassiveGuard(actual,captured);}
        });}catch(RuntimeException|Error unavailable){originalActivationRegistration=null;}
        dev.veltraph.client.greenhouse.GreenhouseCommands.register(greenhouseModule,guard);
        dev.veltraph.client.chocolate.ChocolateFactoryMenuControls.register(chocolateFactoryModule);
        guard.externalControlBlocker(module->(combatAutoHeal!=null&&combatAutoHeal.ownsControl())
                ||(hydra!=null&&hydra.hasActiveLocalReaction())
                ?java.util.Optional.of("Veltraph: wait for the active survival/failsafe response before starting "+module.displayName()+".")
                :java.util.Optional.empty());
        farmMaintenance=new dev.veltraph.client.rewarp.mc.FarmMaintenanceCoordinator(client,services,config,moduleManager,gameContext);
        farmMaintenance.preparedObservationBaseline(()->baselineUnexpectedObservations(client,System.currentTimeMillis()));
        foragingMaintenance=new dev.veltraph.client.foraging.maintenance.mc.McForagingMaintenanceCoordinator(client,services,config,moduleManager,gameContext);
        foragingMaintenance.register(
                (dev.veltraph.client.galatea.GalateaForagingModule)moduleManager.get("galatea_fig").orElseThrow(),
                (dev.veltraph.client.galatea.agatha.AgathaContestModule)moduleManager.get("galatea_agatha_claim").orElseThrow());
        galateaWorkflows=new dev.veltraph.client.galatea.workflow.McGalateaWorkflowFamily(client,services,config,moduleManager,gameContext,
                VeltraphBootstrap::galateaHigherPriorityOwned);
        guard.activationTransaction((module,activation)->activateOriginalFarmMaintenance(module,()->foragingMaintenance.activate(module,()->galateaWorkflows.activate(module,activation))));
        guard.activationConflictExemption((running,starting)->permitsSuspendedReturnConflict(running,starting)
                ||farmMaintenance.permitsActivationConflict(running,starting)
                ||foragingMaintenance.permitsActivationConflict(running,starting)
                ||galateaWorkflows.permitsActivationConflict(running,starting));
        moduleManager.beforeDisable(module->{if(suspendedFarmReturn!=null)suspendedFarmReturn.onManualStop(module,currentFarmReturnManualClose());if(module==registeredBottleAcquisition)clearBottleWarp();if(module instanceof dev.veltraph.client.combat.ghost.GhostGrindModule)clearGhostWarp();if(module instanceof dev.veltraph.client.farming.module.FarmModule)jacobTracker.reset();farmMaintenance.beforeDisable(module);foragingMaintenance.beforeDisable(module);galateaWorkflows.beforeDisable(module);if(scriptScheduler!=null)scriptScheduler.beforeManualStop(module);});
        moduleManager.beforeStopAll(()->{if(suspendedFarmReturn!=null)suspendedFarmReturn.closeFromRoot(currentFarmReturnManualClose(),"Controller stop-all");clearBottleWarp();clearGhostWarp();
            jacobTracker.reset();
            survivalStopSuppressed=true;
            var stoppedPlayer=client.player!=null?client.player:
                    lastGeneralPlayer instanceof net.minecraft.client.player.LocalPlayer oldPlayer?oldPlayer:null;
            survivalStoppedPlayerId=stoppedPlayer==null?null:stoppedPlayer.getUUID();
            survivalStoppedConnection=client.getConnection()!=null?client.getConnection():lastGeneralConnection;
            previousHealEnabled=combatAutoHeal!=null&&combatAutoHeal.enabled();
            if(combatAutoHeal!=null)combatAutoHeal.reset();
            // Retire prepared reservations and issued returns through the existing manual-stop APIs.
            // beforeDisable still cancels each exact pair, including normal individual owner stops.
            if(farmMaintenance!=null)farmMaintenance.cancelActive("Controller stop-all");
            if(farmAutoRecovery!=null)farmAutoRecovery.cancel();
            if(farmCycle!=null)farmCycle.cancel(false);
            foragingMaintenance.cancelActive("Controller stop-all");
            galateaWorkflows.cancelActive("Controller stop-all");
            if(scriptScheduler!=null)scriptScheduler.cancel("Controller stop-all");
            if(hydra!=null)hydra.resetResponses();
            if(generalFailsafe!=null)generalFailsafe.onWorldStop();
        });
        moduleManager.leasedInputOwner(()->{
            var owner=farmMaintenance.effectiveInputOwner();
            if(owner!=null)return owner;
            owner=foragingMaintenance.effectiveInputOwner();
            if(owner!=null)return owner;
            owner=galateaWorkflows.effectiveInputOwner();
            return owner!=null?owner:scriptScheduler==null?null:scriptScheduler.inputOwner();
        });
        rootMaintenanceControlSource=new RootMaintenanceControlSource();
        farmMaintenance.externalControlOwned(rootMaintenanceControlSource);
        foragingMaintenance.externalControlOwned(()->(combatAutoHeal!=null&&combatAutoHeal.ownsControl())
                ||(hydra!=null&&hydra.hasActiveLocalReaction())||farmMaintenance.active()||scheduledRestActive()||galateaWorkflows.active());
        rootNamedLoadoutSettingsSource=new RootNamedLoadoutSettingsSource(config);
        java.util.function.Supplier<dev.veltraph.client.garden.loadout.NamedLoadoutFlow.Settings> namedLoadoutSettings=rootNamedLoadoutSettingsSource;
        var visitorLoadoutPort=new dev.veltraph.client.garden.loadout.mc.McNamedLoadoutPort(client);
        var visitorMaintenancePort=new dev.veltraph.client.garden.visitor.mc.McVisitorMaintenancePort(farmMaintenance,client,services,config);
        var visitorCycle=new dev.veltraph.client.garden.visitor.mc.McVisitorCycleModule(client,services,config,gameContext,
                visitorMaintenancePort,new dev.veltraph.client.garden.loadout.NamedLoadoutFlow(visitorLoadoutPort),
                visitorLoadoutPort,namedLoadoutSettings);
        visitorMaintenancePort.attach(visitorCycle);
        moduleManager.register(visitorCycle);
        farmMaintenance.register(visitorCycle,visitorCycle.maintenancePolicy(),visitorMaintenancePort::committed);
        gardenThresholdAdmission=new dev.veltraph.client.garden.admission.mc.McGardenThresholdAdmission(client,()->config,moduleManager,farmMaintenance,gameContext);
        var pestLoadoutPort=new dev.veltraph.client.garden.loadout.mc.McNamedLoadoutPort(client);
        var sprayParent=new dev.veltraph.client.garden.pests.mc.McPestSprayonatorMaintenanceModule(client,services,config,farmMaintenance,
                new dev.veltraph.client.rewarp.mc.McAssignedPlotTravel(client,services.input()));
        moduleManager.register(sprayParent);
        farmMaintenance.register(sprayParent,sprayParent.maintenancePolicy(),sprayParent::committed);
        var pestMaintenance=new dev.veltraph.client.garden.pests.mc.McPestMaintenanceModule(client,services,config,farmMaintenance,
                new dev.veltraph.client.garden.loadout.NamedLoadoutFlow(pestLoadoutPort),pestLoadoutPort,namedLoadoutSettings,
                new dev.veltraph.client.rewarp.mc.McAssignedPlotTravel(client,services.input()));
        moduleManager.register(pestMaintenance);
        farmMaintenance.register(pestMaintenance,pestMaintenance.maintenancePolicy(),pestMaintenance::committed);
        var earlyPort=new dev.veltraph.client.garden.loadout.mc.McNamedLoadoutPort(client);
        earlyRetainedLoadout=new dev.veltraph.client.garden.pests.mc.McPestEarlyLoadoutModule(client,services,config,farmMaintenance,moduleManager,earlyPort,
            new dev.veltraph.client.garden.loadout.NamedLoadoutFlow(earlyPort),rootNamedLoadoutSettingsSource);
        moduleManager.register(earlyRetainedLoadout);
        farmMaintenance.register(earlyRetainedLoadout,earlyRetainedLoadout.maintenancePolicy(),earlyRetainedLoadout::committed);
        rootEarlyLoadoutObservationSource=new RootEarlyLoadoutObservationSource(client,config);
        earlyLoadoutObservation=new dev.veltraph.client.garden.pests.mc.McPestEarlyLoadoutObservation(client,config,rootEarlyLoadoutObservationSource);
        var trackerLoadoutPort=new dev.veltraph.client.garden.loadout.mc.McNamedLoadoutPort(client);
        var pestTracker=new dev.veltraph.client.garden.pests.mc.McPestTrackedMaintenanceModule(client,services,config,farmMaintenance,
                new dev.veltraph.client.rewarp.mc.McAssignedPlotTravel(client,services.input()),
                new dev.veltraph.client.garden.loadout.NamedLoadoutFlow(trackerLoadoutPort),trackerLoadoutPort,namedLoadoutSettings);
        nearbyPestAdmission=new dev.veltraph.client.garden.pests.mc.McNearbyPestAdmission(client,config);
        moduleManager.register(pestTracker);
        farmMaintenance.register(pestTracker,pestTracker.maintenancePolicy(),pestTracker::committed);
        moduleManager.register(new dev.veltraph.client.garden.pests.mc.McPestPhilipModule(client,services,config));
        var pestPhilip=new dev.veltraph.client.garden.pests.mc.McPestPhilipMaintenanceModule(client,services,config,farmMaintenance);
        moduleManager.register(pestPhilip);
        farmMaintenance.register(pestPhilip,pestPhilip.maintenancePolicy(),pestPhilip::committed);
        farmAutoSell=new dev.veltraph.client.inventory.selling.McFarmAutoSellModule(client,services,config,farmMaintenance);
        moduleManager.register(farmAutoSell);
        farmMaintenance.register(farmAutoSell,farmAutoSell.maintenancePolicy(),farmAutoSell::committed);
        capturedGeorgeHelper=new dev.veltraph.client.inventory.selling.CapturedGeorgeHelper(client,services,config,farmMaintenance,gameContext);
        capturedGeorgeHelper.warpHooks(new dev.veltraph.client.inventory.selling.CapturedGeorgeHelper.WarpHooks(){
            public boolean beforeCommand(dev.veltraph.client.rewarp.mc.FarmMaintenanceCoordinator.GeorgeCapture capture,
                    dev.veltraph.client.rewarp.FarmMaintenanceLease.Token token,dev.veltraph.client.inventory.selling.GeorgeWarpPermit permit){
                long now=System.currentTimeMillis();
                if(localMetrics!=null)localMetrics.poll(now);
                if(localFinancial!=null)localFinancial.poll(now); // Drain final per-pet ACK before leaving its actual context.
                return scriptScheduler!=null&&scriptScheduler.retainGeorgeTimer(capture,capturedGeorgeHelper,permit);
            }
            public boolean admitted(dev.veltraph.client.rewarp.mc.FarmMaintenanceCoordinator.GeorgeCapture capture,
                    dev.veltraph.client.rewarp.FarmMaintenanceLease.Token token,dev.veltraph.client.inventory.selling.GeorgeWarpPermit permit,
                    dev.veltraph.client.metrics.FinancialOutcomeBindings.Scope previous,dev.veltraph.client.metrics.FinancialOutcomeBindings.Scope current){
                if(scriptScheduler==null||!scriptScheduler.commitGeorgeTimer(capture,capturedGeorgeHelper,permit))return false;
                if(generalFailsafe!=null)generalFailsafe.onWorldStop();
                if(hydraAdapter!=null)hydraAdapter.onWorldStop();skyBlockHealth.reset();if(combatAutoHeal!=null)combatAutoHeal.reset();
                lastGeneralPlayer=client.player;lastGeneralConnection=client.getConnection();lastWorld=client.level;
                long now=System.currentTimeMillis();
                // Bind the passive collector first, without polling/removing the previous activation bridge.
                if(localMetrics!=null)localMetrics.poll(now);
                if(localFinancial!=null)try{localFinancial.continueGeorge(capturedGeorgeHelper,token,permit,previous,current);localFinancial.poll(now);}
                catch(RuntimeException unavailable){/* Passive attribution cannot acquire or revoke native control. */}
                return true;
            }
        });
        moduleManager.register(capturedGeorgeHelper);
        farmMaintenance.registerGeorge(capturedGeorgeHelper,capturedGeorgeHelper.maintenancePolicy(),capturedGeorgeHelper::committed);
        jacobReward=new dev.veltraph.client.garden.jacob.mc.McJacobRewardModule(client,services,config,farmMaintenance,moduleManager);
        moduleManager.register(jacobReward);
        jacobReward.bindRootJournal(new RootJacobJournalStore(jacobReward));
        dev.veltraph.client.garden.jacob.mc.JacobRewardJournalCommands.register(jacobReward);
        farmMaintenance.register(jacobReward,jacobReward.maintenancePolicy(),jacobReward::committed);
        var greenhouseMaintenance=new dev.veltraph.client.greenhouse.GreenhouseMaintenanceModule(client,config,services,moduleManager,farmMaintenance);
        greenhouseMaintenance.bindPersistence(VeltraphBootstrap::confirmConfigSaved);
        moduleManager.register(greenhouseMaintenance);
        farmMaintenance.register(greenhouseMaintenance,greenhouseMaintenance.maintenancePolicy(),greenhouseMaintenance::committed);
        dev.veltraph.client.greenhouse.GreenhouseMaintenanceCommands.register(greenhouseMaintenance,guard);
        trapMaintenance=new dev.veltraph.client.garden.pests.mc.McPestTrapMaintenanceModule(client,services,config,farmMaintenance,VeltraphBootstrap::actualTrapTabRows);
        moduleManager.register(trapMaintenance);
        farmMaintenance.register(trapMaintenance,trapMaintenance.maintenancePolicy(),trapMaintenance::committed);
        farmCycle=new dev.veltraph.client.rewarp.mc.McFarmCycleCoordinator(client,services,config,moduleManager,gameContext,
                configFile.getParent().resolve("farm-cycles.json"));
        dev.veltraph.client.rewarp.mc.FarmCycleCommands.register(farmCycle);
        suspendedFarmReturn=new dev.veltraph.client.rewarp.mc.McSuspendedFarmReturnCoordinator(client,services,config,moduleManager,gameContext,farmCycle);
        rootFarmReturnRegistration=new RootFarmReturnRegistration(client,services,config,moduleManager,gameContext,suspendedFarmReturn,rootInputReleaseRegistration);
        dev.veltraph.client.rewarp.mc.FarmReturnCommands.register(suspendedFarmReturn);
        var slayerModule=new dev.veltraph.client.combat.slayer.SlayerModule(client,config,services);
        moduleManager.register(slayerModule);
        moduleManager.register(new dev.veltraph.client.combat.sven.SvenParentModule(client,config,services));
        dev.veltraph.client.combat.slayer.SlayerCommands.register(slayerModule,guard);
        registerMelee("zealot_grind", "Zealot Grinding", dev.veltraph.client.game.GameArea.THE_END, "Zealot,Zealot Bruiser,Special Zealot", client, services);
        moduleManager.register(new dev.veltraph.client.combat.quiver.AutoQuiverModule(client,services,config));
        moduleManager.register(new dev.veltraph.client.combat.grind.ZealotRangedModule(client,services,config,
                new dev.veltraph.client.navigation.LocalRouteStore(configFile.getParent().resolve("routes"))));
        moduleManager.register(new dev.veltraph.client.combat.ghost.GhostGrindModule(client,services,config,
                new dev.veltraph.client.navigation.LocalRouteStore(configFile.getParent().resolve("routes")),
                ()->combatAutoHeal!=null&&combatAutoHeal.ownsControl()));
        registerMelee("tarantula_grind", "Tarantula Grinding", dev.veltraph.client.game.GameArea.SPIDERS_DEN, "Spider,Splitter Spider,Voracious Spider,Tarantula Beast,Tarantula Broodfather", client, services, dev.veltraph.client.combat.grind.SourceCombatUnreachableConsumer.Declaration.TARANTULA_GRAVEYARD_CHILD);
        registerMelee("graveyard_grind", "Graveyard Grinding", dev.veltraph.client.game.GameArea.HUB, "Zombie,Graveyard Zombie,Revenant Horror,Revenant Champion,Revenant Sycophant", client, services, dev.veltraph.client.combat.grind.SourceCombatUnreachableConsumer.Declaration.GRAVEYARD_CHILD);
        registerMelee("bestiary_grind", "Bestiary Grinding", null, "", client, services, dev.veltraph.client.combat.grind.SourceCombatUnreachableConsumer.Declaration.BESTIARY_GRAVEYARD_CHILD);

        var routeModule = new dev.veltraph.client.navigation.mc.RouteModule(client, services, config);
        moduleManager.register(routeModule);
        new dev.veltraph.client.navigation.mc.RouteCommands(
                new dev.veltraph.client.navigation.LocalRouteStore(configFile.getParent().resolve("routes")),
                routeModule, gameContext, guard).register();

        var builderModule = new dev.veltraph.client.builder.BuilderModule(client, services, config);
        moduleManager.register(builderModule);
        var plotCleanerModule=new dev.veltraph.client.garden.plotcleaner.PlotCleanerModule(client,services,config);
        moduleManager.register(plotCleanerModule);
        dev.veltraph.client.garden.plotcleaner.PlotCleanerCommands.register(plotCleanerModule,guard);
        dev.veltraph.client.garden.copy.GardenCopyCommands.register(gardenAnalyzerModule,gardenCopyModule,guard);
        moduleManager.register(new dev.veltraph.client.gui.HarpModule(client, config));
        ((dev.veltraph.client.gui.HarpModule)moduleManager.get(dev.veltraph.client.gui.HarpModule.ID).orElseThrow()).attachNativeInputObserver();
        moduleManager.register(new dev.veltraph.client.gui.ExperimentsModule(client,config));
        dev.veltraph.client.builder.BuilderCommands.register(
                new dev.veltraph.client.builder.BuildLayoutStore(configFile.getParent().resolve("layouts")), builderModule, guard);
        var alertSink = new McHydraAlertSink(client);
        notifications = new dev.veltraph.client.notification.LocalNotifications(config,
                new dev.veltraph.client.notification.LocalNotifications.Sink() {
                    public void title(String title) { alertSink.showTitle("Veltraph",title); }
                    public void sound() { alertSink.playPing(); }
                    public void beep() { try { java.awt.Toolkit.getDefaultToolkit().beep(); } catch(RuntimeException ignored) {} }
                });
        generalFailsafe = new dev.veltraph.client.failsafe.McGeneralFailsafe(client,config,moduleManager,
                new dev.veltraph.client.failsafe.GeneralFailsafe.Actions() {
                    public void alert(String reason) { safeChat("Veltraph: "+reason);VeltraphLog.basic("Failsafe",reason); }
                    public void stop() {
                        if(combatAutoHeal!=null)combatAutoHeal.reset();
                        moduleManager.stopAll();
                        releaseOriginalRootLogicalAll();
                    }
                    public boolean beginResponse(dev.veltraph.client.failsafe.response.FailsafeResponseType type,Runnable completion) {
                        if(hydra==null||(combatAutoHeal!=null&&combatAutoHeal.ownsControl()))return false;
                        suspendOwnedMiningUse();
                        return hydra.beginGeneralResponse(!moduleManager.enabledControllers().isEmpty(),type,completion);
                    }
                    public void cancelResponse(dev.veltraph.client.failsafe.response.FailsafeResponseType type) {
                        if(hydra!=null)hydra.cancelGeneralResponse(type);
                    }
                    public void switchLobby() {
                        String command=config.get(dev.veltraph.client.config.FailsafeConfig.FAILSAFE_SWITCH_LOBBY_COMMAND).trim().replaceFirst("^/+","");
                        if(client.player!=null&&client.level!=null&&client.player.isAlive()
                                &&gameContext.current().inSkyBlock()
                                &&command.matches("(?i)(?:hub|skyblock|l(?:obby)?(?: [a-z0-9_-]{1,40})?|warp [a-z0-9_:.-]{1,80})"))
                            new McServerCommandService(client).sendCommand(command);
                        else safeChat("Veltraph: macro stopped; no valid configured lobby route was requested.");
                    }
                    public void returnToLocation() {
                        if(client.player!=null&&client.level!=null) {
                            var anchor=rewarpService.selectLocalTarget(gameContext.current().area(),client.level.dimension().identifier().toString(),
                                    new dev.veltraph.client.world.Vec3(client.player.getX(),client.player.getY(),client.player.getZ()));
                            if(anchor.isPresent()&&savedAnchorRecovery.begin(anchor.get()))return;
                        }
                        returnFallback.run();
                    }
                });
        generalFailsafe.sourcePolicies(()->config.get(dev.veltraph.client.config.FailsafeConfig.FAILSAFE_PLAYER_POLICY),
                ()->config.get(dev.veltraph.client.config.FailsafeConfig.FAILSAFE_MENTION_POLICY));
        generalFailsafe.mentionDelays(()->config.get(dev.veltraph.client.config.FailsafeConfig.FAILSAFE_MENTION_MIN_DELAY_MS),
                ()->config.get(dev.veltraph.client.config.FailsafeConfig.FAILSAFE_MENTION_MAX_DELAY_MS));
        generalFailsafe.externalControlOwned(()->combatAutoHeal!=null&&combatAutoHeal.ownsControl());

        // Command arrival is followed by return to the interrupted live farm pose before resume.
        // Persistent manually saved markers remain a separate recovery option.
        farmAutoRecovery=new dev.veltraph.client.rewarp.mc.McFarmAutoRecoveryCoordinator(
                client,services,config,moduleManager,gameContext,rewarpService,rewarpWarp);
        autoRewarp=farmAutoRecovery.runtime();
        guard.runningLocationExemption((module,ctx)->module instanceof dev.veltraph.client.farming.module.FarmModule farm
                &&suspendedFarmReturn!=null&&suspendedFarmReturn.permitsRunningLocation(farm,ctx)
                ||farmAutoRecovery.permitsRunningLocation(module,ctx)
                ||farmMaintenance.permitsRunningLocation(module,ctx));
        // Full family pause ports register first; remaining primary owners retain terminal timer metadata.
        scriptScheduler=new dev.veltraph.client.scheduling.mc.McScriptScheduler(client,services,config,moduleManager,gameContext,
                VeltraphBootstrap::scheduledHigherPriorityOwned,VeltraphBootstrap::scheduledRecentRewarpTimestamp,()->{
                    gameContext.current();
                    var observed=dev.veltraph.client.garden.jacob.JacobContestObservation.read(actualJacobSidebarRows()).scheduled();
                    return new dev.veltraph.client.scheduling.mc.McScriptScheduler.Contest(observed.active(),observed.remainingSeconds());
                });
        dev.veltraph.client.scheduling.mc.PrimaryRestBindings.registerAll(scriptScheduler,moduleManager,config,client,services,
                fishingModule,fishingServices,VeltraphBootstrap::atSourceFarmRewarpBoundary);
        dev.veltraph.client.scheduling.mc.PrimaryTimerBindings.registerAll(scriptScheduler,moduleManager,config);
        scriptScheduler.registerTimer(bottleAcquisition,()->bottleAcquisition.id(),()->bottleSettings());
        guard.standardReadiness(scriptScheduler::activationReadiness);
        guard.beforeActivation(module->{scriptScheduler.beforeActivation(module);unexpectedGeneration++;});
        localMetricsOwners=new dev.veltraph.client.metrics.mc.McOwnerMetricsAdapter(moduleManager,()->localMetrics,
                ()->config.get(dev.veltraph.client.config.LocalMetricsConfig.LOCAL_METRICS_ENABLED));
        localFinancial=new dev.veltraph.client.metrics.mc.McSellingFinancialAdapter(client,moduleManager,()->localMetrics,
                ()->config.get(dev.veltraph.client.config.LocalMetricsConfig.LOCAL_METRICS_ENABLED),true,true,true,false,true);
        originalDeferredMaintenanceActivator=new RootDeferredMaintenanceActivator(client);
        farmMaintenance.deferredActivator(originalDeferredMaintenanceActivator);
        guard.afterFreshStart(module->{
            if(farmMaintenance.deferFreshStart(module,()->observeFreshStart(client,module)))return;
            observeFreshStart(client,module);
        });


        // Local Hydra / Anti-Staff (Target 4, LOCAL_INCLUDE per HydraAntiStaffLocal.md): proximity +
        // teleport/world/limbo failsafes with local-only reactions (chat/title/sound + optional
        // stop-all). No network, no reporting — see docs/HYDRA_ANTISTAFF_NOTES.md.
        //
        // LOCAL_INCLUDE staff-response layer (HydraAntiStaffMechanism.md §4-§6): automated chat replies,
        // fix-angle, and afterdisconnect/avoid-evacuation re-route. The chat/command go over the SAME
        // vanilla in-game client path as the recovered rewarp command (no java.net, no backend/C2). Gated
        // by HydraConfig.STAFF_RESPONSE_POLICY (default OFF), so nothing sends until the user opts in.
        HydraResponder hydraResponder = new McHydraResponder(
                new McServerChatService(client), new McServerCommandService(client),
                services.rotation(), services.input(),()->releaseNativeInputs(client),VeltraphBootstrap::suspendOwnedMiningUse);
        hydra = new HydraAntiStaff(config, VeltraphBootstrap::safeChat,
                () -> {if(combatAutoHeal!=null)combatAutoHeal.reset();moduleManager.stopAll();}, ScriptingInputLock::releaseAll,
                new McHydraAlertSink(client),
                hydraResponder, java.util.concurrent.ThreadLocalRandom.current()::nextDouble);
        hydra.externalControlOwned(()->combatAutoHeal!=null&&combatAutoHeal.ownsControl());
        // Local failsafe automated-response routine (clean-room LOOK/MOVE/JUMP/SNEAK/clicks). Macro-gated +
        // off by default; advanced by the same Hydra responder tick path. Local camera/key inputs only.
        hydra.setFailsafeResponseController(new dev.veltraph.client.failsafe.response.FailsafeResponseController(
                config,
                new dev.veltraph.client.failsafe.response.mc.McFailsafeActionSink(
                        client, services.rotation(), services.input(), new McServerCommandService(client)),
                System::currentTimeMillis));
        hydraAdapter = new HydraAntiStaffAdapter(client, hydra, gameContext, moduleManager);
        hydraAdapter.sourceFloorGeneration(()->unexpectedGeneration);
        hydraAdapter.sourceFloorExternalOwner(()->combatAutoHeal!=null&&combatAutoHeal.ownsControl()
                ?new HydraAntiStaffAdapter.ExternalFloorOwner(combatAutoHeal,combatAutoHeal.hasVerifiedCustody()):null);
        hydraAdapter.barrierRunGeneration(()->unexpectedGeneration);
        hydraAdapter.expectedRecoveryTeleport((position,now)->
                (suspendedFarmReturn!=null&&suspendedFarmReturn.consumeExpectedTeleport(position,now))
                ||(farmCycle!=null&&farmCycle.consumeExpectedTeleport(position,now))
                ||(farmMaintenance!=null&&farmMaintenance.consumeExpectedTeleport(position,now))
                ||(foragingMaintenance!=null&&foragingMaintenance.consumeExpectedTeleport(position,now))
                ||(galateaWorkflows!=null&&galateaWorkflows.consumeExpectedTeleport(position,now))
                ||(farmAutoRecovery!=null&&farmAutoRecovery.consumeExpectedTeleport(position,now))
                ||(scriptScheduler!=null&&scriptScheduler.consumeExpectedTeleport(position,now)));
        hydraAdapter.expectedHubTeleport(VeltraphBootstrap::consumeHubExpectedTeleport);

        // In-world HUD overlay (Target 5): local rendering only — a compact Veltraph-skinned panel showing
        // the active macro + state, area, anti-staff status, and rewarp status. Gated to Hypixel SkyBlock
        // by HudVisibilityService and the HUD_ENABLED flag; never renders on menus/lobbies/BedWars.
        hudOverlay = new VeltraphHudOverlay(moduleManager, gameContext, rewarpService, hydra, config,VeltraphBootstrap::localMetricsHudLines);
        hudOverlay.register();

        // Garden Visitors (Batch 4): a local, advisory watcher. When enabled and in the Garden, it reads
        // the open visitor menu (read-only) and recommends accept/skip per the configured filter + budget.
        // Advisory only — it never clicks/buys/serves (auto-serve needs inventory-click packets +
        // barn-pathfinding, both excluded). See docs/GARDEN_VISITOR_NOTES.md.
        visitorAdvisor = new VisitorAdvisor(config, new McVisitorScreenSource(client),
                VeltraphBootstrap::safeChat);

        // Hyperion auto-heal assist (Batch 6, Slayers/Ghost): opt-in survival heal (look down -> cast
        // Hyperion AOE -> restore). Local only; no pathfinding/targeting/network. See docs/GHOST_AUTOHEAL_NOTES.md.
        combatAutoHeal = new CombatAutoHeal(config, new McHealServices(client, config, services.inventory()).withHealth(skyBlockHealth));

        // Local developer test harness (Target 1): one registry of clearly-labeled local test actions,
        // shared by the Settings Developer page and /vdebug dev. Gated behind Developer Mode; SERVER_BOUND
        // actions only send when the explicit Execute control allows it. See docs/DEVELOPER_MODE_NOTES.md.
        devTests = dev.veltraph.client.dev.DevTestActions.standard();
        devTestContext = new dev.veltraph.client.dev.mc.McDevTestContext(
                client, moduleManager, gameContext, hydra, config, chat);

        VeltraphKeybinds.register();
        VeltraphCommands.register();
        dev.veltraph.client.capture.CaptureGuiTools.register();
        dev.veltraph.client.capture.visitor.CaptureVisitorContextTools.register();
        dev.veltraph.client.capture.tool.CaptureSelectedToolTools.register();
        VeltraphMenuButton.register();
        if(originalRootEndTickRegistered)throw new IllegalStateException("Original Root native callback already registered");
        ClientTickEvents.END_CLIENT_TICK.register(originalRootEndTickCallback);originalRootEndTickRegistered=true;
        // Read-only chat-receive listener: feed RECEIVED game/system lines to the anti-staff watcher's
        // routing-text trigger. This ONLY inspects lines the client already received — it never sends,
        // replies to, or transmits anything (the original Hydra auto-reply chain is deliberately NOT
        // rebuilt; see docs/HYDRA_ANTISTAFF_NOTES.md). Wrapped so a bad line never breaks the listener.
        if(actionBarSubscription!=null)actionBarSubscription.close();
        actionBarSubscription=dev.veltraph.client.service.mc.NativeActionBarSignal.subscribe(VeltraphBootstrap::receivedNativeActionBar);
        if(healFeedbackSubscription!=null)healFeedbackSubscription.close();
        healFeedbackSubscription=dev.veltraph.client.service.mc.NativeHealFeedbackSignal.subscribe(
                dev.veltraph.client.combat.heal.HealNativeFeedbackRoute::receive);
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            try {
                if(overlay){
                    dev.veltraph.client.service.mc.NativeActionBarSignal.observeSystemOverlay(client,client.getConnection(),message);
                    return;
                }
                if (hydraAdapter != null && message != null) {
                    hydraAdapter.acceptChatLine(message.getString());
                }
                if(message != null) {
                    if(scriptScheduler!=null)scriptScheduler.validateBeforeUpdates();
                    long metricSerial=++localMetricsMessageSerial;
                    if(localMetrics!=null&&config.get(dev.veltraph.client.config.LocalMetricsConfig.LOCAL_METRICS_ENABLED)
                            &&client.player==localMetricsPlayer&&client.level==localMetricsWorld&&client.getConnection()==localMetricsConnection
                            &&gameContext.current().inSkyBlock())
                        try{localMetrics.receivedMessage(dev.veltraph.client.metrics.MetricContracts.Channel.SYSTEM,metricSerial,message.getString(),System.currentTimeMillis());}
                        catch(RuntimeException ignored){/* Preserve existing received-message owner routing. */}
                    jacobTracker.message(jacobScope(client),message.getString());
                    jacobTracker.takeEnded().ifPresent(event->{lastJacobEnded=event;if(jacobReward!=null)jacobReward.observedEnd(jacobScope(client),event);});
                    notifications.systemMessage(message.getString(),System.currentTimeMillis());
                    generalFailsafe.onMessage(message.getString());
                    if(suspendedFarmReturn!=null)suspendedFarmReturn.onServerMessage(message.getString());
                    if(farmAutoRecovery!=null)farmAutoRecovery.onServerMessage(message.getString());
                    if(farmMaintenance!=null)farmMaintenance.onServerMessage(message.getString());
                    if(scriptScheduler!=null)scriptScheduler.onServerMessage(message.getString());
                }
                if (message != null && !overlay) for (Module module : moduleManager.enabledModules()) {
                    if(scriptScheduler!=null&&scriptScheduler.ownsPaused(module))continue;
                    if(quiesceGhostWarpReceipt(module,message.getString(),client))continue;
                    if(quiesceSvenWarpReceipt(module,message.getString(),client))continue;
                    if(quiesceHubWarpReceipt(module,message.getString()))continue;
                    module.onServerMessage(message.getString());
                    if (module instanceof dev.veltraph.client.foraging.module.AutoForagingModule foraging)
                        foraging.onFeedback(message.getString());
                    if(module instanceof dev.veltraph.client.foraging.module.TreeChopModule treeChop)
                        treeChop.onFeedback(message.getString());
                    if(module instanceof dev.veltraph.client.galatea.GalateaForagingModule galatea)
                        galatea.onFeedback(message.getString());
                    if(module instanceof dev.veltraph.client.combat.grind.ZealotRangedModule ranged)
                        ranged.onChat(message.getString());
                }
            } catch (Throwable ignored) {
                // never let chat ingestion crash the client
            }
        });

        LOGGER.info("[Veltraph] Bootstrap complete: {} farming profiles, {} modules registered.",
                farmRegistry.size(), moduleManager.all().size());
        VeltraphLog.basic("VeltraphBootstrap.init", "mod initialized: " + farmRegistry.size()
                + " profiles, " + moduleManager.all().size() + " modules");
        originalInitializationReturned=true;
        }finally{finishOriginalControlStartup(originalInitializationReturned);}
    }

    /** Build the local file log sink under {@code <gameDir>/logs/veltraph/}. */
    private static void registerMelee(String id,String name,dev.veltraph.client.game.GameArea area,String targets,
                                      Minecraft client,FarmServices services) {
        moduleManager.register(new dev.veltraph.client.combat.grind.MeleeGrindModule(id,name,area,targets,client,services,config));
    }

    private static void registerMelee(String id,String name,dev.veltraph.client.game.GameArea area,String targets,
            Minecraft client,FarmServices services,dev.veltraph.client.combat.grind.SourceCombatUnreachableConsumer.Declaration declaration) {
        var module=new dev.veltraph.client.combat.grind.MeleeGrindModule(id,name,area,targets,client,services,config);
        module.sourceUnreachableConsumer(declaration);
        moduleManager.register(module);
    }

    private static FileLogSink createLogSink() throws Exception {
        java.nio.file.Path logsRoot = FabricLoader.getInstance().getGameDir().resolve("logs").resolve("veltraph");
        return new FileLogSink(logsRoot, LocalDateTime.now().format(FILE_STAMP));
    }

    private static void safeChat(String message) {
        try {
            if (chat != null) {
                chat.send(message);
            }
        } catch (Throwable ignored) {
            // chat unavailable (e.g. very early init) — the SLF4J log already has it
        }
    }

    /** One intake for both already received native action-bar packet paths. */
    private static void receivedNativeActionBar(dev.veltraph.client.service.mc.NativeActionBarSignal.Receipt receipt){
        var client=Minecraft.getInstance();
        if(!receipt.current(client)||moduleManager==null)return;
        String text=receipt.component().getString();long now=receipt.receivedAt();
        if(hydraAdapter!=null)hydraAdapter.acceptChatLine(text);
        skyBlockHealth.accept(text,now);
        if(scriptScheduler!=null)scriptScheduler.validateBeforeUpdates();
        long metricSerial=++localMetricsMessageSerial;
        if(localMetrics!=null&&config.get(dev.veltraph.client.config.LocalMetricsConfig.LOCAL_METRICS_ENABLED)
                &&client.player==localMetricsPlayer&&client.level==localMetricsWorld&&client.getConnection()==localMetricsConnection
                &&gameContext.current().inSkyBlock())
            try{localMetrics.receivedMessage(dev.veltraph.client.metrics.MetricContracts.Channel.ACTION_BAR,metricSerial,text,now);}
            catch(RuntimeException ignored){/* Passive metrics cannot block gameplay observations. */}
        for(Module module:moduleManager.enabledModules())
            try{if((scriptScheduler==null||!scriptScheduler.ownsPaused(module))&&!quiesceGhostWarpReceipt(module,text,client)&&!quiesceSvenWarpReceipt(module,text,client)&&!quiesceHubWarpReceipt(module,text))module.onActionBar(text);}catch(RuntimeException ignored){/* Isolate each read-only recipient. */}
    }
    public static java.util.OptionalDouble observedSkyBlockHealthPercent(long now){return skyBlockHealth.percent(now);}

    /** Cached passive observation only; render code must never poll the collector or gameplay owners. */
    public static dev.veltraph.client.metrics.LocalSessionMetrics.Snapshot localMetricsSnapshot(){return localMetricsSnapshot;}
    public static String localMetricsStatus(){return localMetricsStatus;}
    public static long localMetricsLastStatsReceiptMillis(){return localMetrics==null?0:localMetrics.lastStatsReceiptMillis();}
    public static java.util.List<dev.veltraph.client.hud.HudLine> localMetricsHudLines(){
        return config!=null&&config.get(dev.veltraph.client.config.LocalMetricsConfig.LOCAL_METRICS_ENABLED)?localMetricsHud:java.util.List.of();
    }
    private static void closeLocalMetrics(){
        if(localMetrics!=null)localMetrics.close();localMetrics=null;localMetricsSnapshot=null;localMetricsHud=java.util.List.of();
        localMetricsPlayer=localMetricsWorld=localMetricsConnection=null;localMetricsSignature=null;
    }
    private static String metricsSignature(){
        return config.get(dev.veltraph.client.config.LocalMetricsConfig.LOCAL_METRICS_ITEMS)+"|"+
                config.get(dev.veltraph.client.config.LocalMetricsConfig.LOCAL_METRICS_SIGNALS)+"|"+
                config.get(dev.veltraph.client.config.LocalMetricsConfig.LOCAL_METRICS_BPS_WINDOW_MS)+"|"+
                config.get(dev.veltraph.client.config.LocalMetricsConfig.LOCAL_METRICS_PICKUP_TIMEOUT_MS)+"|"+
                config.get(dev.veltraph.client.config.LocalMetricsConfig.LOCAL_METRICS_NOTICE_DEDUP_MS);
    }
    private static void updateLocalMetrics(Minecraft client,long now){
        if(!config.get(dev.veltraph.client.config.LocalMetricsConfig.LOCAL_METRICS_ENABLED)){
            closeLocalMetrics();localMetricsAttemptedSignature=null;localMetricsStatus="Disabled";if(localMetricsOwners!=null)localMetricsOwners.poll(now);if(localFinancial!=null)localFinancial.poll(now);return;
        }
        if(client.player==null||client.level==null||client.getConnection()==null||!gameContext.current().inSkyBlock()){
            closeLocalMetrics();localMetricsAttemptedSignature=null;localMetricsStatus="Waiting for native SkyBlock context";return;
        }
        String signature=metricsSignature();
        if(localMetrics!=null&&(!signature.equals(localMetricsSignature)||client.player!=localMetricsPlayer||client.level!=localMetricsWorld||client.getConnection()!=localMetricsConnection)){closeLocalMetrics();localMetricsAttemptedSignature=null;}
        if(localMetrics==null){
            if(signature.equals(localMetricsAttemptedSignature)){localMetricsHud=java.util.List.of(dev.veltraph.client.hud.HudLine.warning(localMetricsStatus));return;}
            localMetricsAttemptedSignature=signature;
            try{
                localMetrics=new dev.veltraph.client.metrics.mc.McLocalMetricsCollector(client,config,now);
                localMetricsSignature=signature;localMetricsPlayer=client.player;localMetricsWorld=client.level;localMetricsConnection=client.getConnection();
                localMetricsStatus="Local session metrics";
            }catch(RuntimeException invalid){closeLocalMetrics();localMetricsStatus="Metrics: invalid local contract";localMetricsHud=java.util.List.of(dev.veltraph.client.hud.HudLine.warning(localMetricsStatus));return;}
        }
        try{
            localMetrics.poll(now);if(localMetricsOwners!=null)localMetricsOwners.poll(now);if(localFinancial!=null)localFinancial.poll(now);localMetricsSnapshot=localMetrics.snapshot(now);
            localMetricsHud=dev.veltraph.client.metrics.HudMetricsLines.build(localMetricsSnapshot,localMetrics.lastStatsReceiptMillis(),now,dev.veltraph.client.metrics.HudMetricsLines.MAX_LINES);
        }catch(RuntimeException invalid){closeLocalMetrics();localMetricsStatus="Metrics: observation unavailable";localMetricsHud=java.util.List.of(dev.veltraph.client.hud.HudLine.warning(localMetricsStatus));}
    }

    /** Persist the current settings + rewarp points to the config file (no-op if not yet wired). */
    public static void saveConfig() {
        if (jsonConfig != null && configFile != null) {
            jsonConfig.save(configFile, rewarpService != null ? rewarpService.store() : null);
        }
    }

    /** Exact Root-owned durable selected-local Jacob ledger; no supplied persistence Boolean. */
    public static final class RootJacobJournalStore {
        private final dev.veltraph.client.garden.jacob.mc.McJacobRewardModule issuedOwner;
        private final ConfigService issuedConfig;private final JsonConfigService issuedWriter;private final java.nio.file.Path issuedConfigPath;
        private final dev.veltraph.client.garden.jacob.JacobRewardJournalFile file,outcomes;
        private RootJacobJournalStore(dev.veltraph.client.garden.jacob.mc.McJacobRewardModule owner){
            issuedOwner=owner;issuedConfig=config;issuedWriter=jsonConfig;issuedConfigPath=configFile;
            file=new dev.veltraph.client.garden.jacob.JacobRewardJournalFile(configFile.resolveSibling("jacob-reward-claims.json"));
            outcomes=new dev.veltraph.client.garden.jacob.JacobRewardJournalFile(configFile.resolveSibling("jacob-reward-receipts.json"));
        }
        public boolean affiliates(dev.veltraph.client.garden.jacob.mc.McJacobRewardModule owner,ConfigService expected){
            return owner==issuedOwner&&expected==issuedConfig&&config==issuedConfig&&jsonConfig==issuedWriter&&configFile==issuedConfigPath
                &&moduleManager!=null&&moduleManager.get(dev.veltraph.client.garden.jacob.mc.McJacobRewardModule.ID).orElse(null)==issuedOwner;
        }
        private void require(dev.veltraph.client.garden.jacob.mc.McJacobRewardModule owner){if(!Minecraft.getInstance().isSameThread()||!affiliates(owner,issuedConfig))throw new IllegalStateException("Actual client-thread Root journal binding required");}
        private boolean activeOwner(){var client=Minecraft.getInstance();return issuedOwner.isEnabled()&&moduleManager.inputOwner().orElse(null)==issuedOwner
            &&farmMaintenance!=null&&farmMaintenance.tokenFor(issuedOwner)!=null&&client.player!=null&&client.level!=null&&client.getConnection()!=null&&client.player.isAlive();}
        /** Private capsule minted only by actual forced/atomic/readback write under exact Root scope. */
        public static final class SavedIntent {
            private final RootJacobJournalStore issuer;private final java.util.UUID id;
            private final dev.veltraph.client.garden.jacob.JacobRewardJournalFile.VerifiedWrite saved;
            private final dev.veltraph.client.garden.jacob.JacobRewardJournalConfigFence configFence;
            private SavedIntent(RootJacobJournalStore issuer,java.util.UUID id,dev.veltraph.client.garden.jacob.JacobRewardJournalFile.VerifiedWrite saved,dev.veltraph.client.garden.jacob.JacobRewardJournalConfigFence fence){this.issuer=issuer;this.id=id;this.saved=saved;configFence=fence;}
            public java.util.UUID id(){return id;}
        }
        private boolean settledOutcomesMatch(dev.veltraph.client.garden.jacob.JacobRewardJournal intents)throws java.io.IOException {
            var confirmed=outcomes.read().value();return intents.entries().stream().filter(e->e.phase()==dev.veltraph.client.garden.jacob.JacobRewardJournal.Phase.ACKNOWLEDGED).allMatch(confirmed::hasQualifiedOutcome);
        }
        public String admissionError(dev.veltraph.client.garden.jacob.mc.McJacobRewardModule owner){
            try{require(owner);var journal=file.read().value();if(!settledOutcomesMatch(journal))return "Jacob acknowledged intent lacks matching durable outcome; claims blocked";if(journal.pending().isPresent()){var entry=journal.pending().orElseThrow();return "Reconcile unresolved local Jacob intent "+entry.id()+" contract "+entry.contractHash()+"; configured profile is not authenticated account identity";}if(!journal.canPrepare())return "Jacob journal capacity/generation exhausted; no entries evicted";return null;}
            catch(java.io.IOException|RuntimeException refused){return "Jacob journal unavailable/corrupt; no new claim or automatic reset";}
        }
        public SavedIntent prepare(dev.veltraph.client.garden.jacob.mc.McJacobRewardModule owner,String configuredProfile,String contractHash){
            try{require(owner);var client=Minecraft.getInstance();if(!activeOwner()||client.gui.screen()!=null||client.player.containerMenu!=client.player.inventoryMenu||!client.player.containerMenu.getCarried().isEmpty())return null;
                var nativePlayer=client.player;var nativeWorld=client.level;var nativeListener=client.getConnection();var nativeMenu=client.player.containerMenu;var lifecycle=moduleManager.lifecycleSnapshot();
                var fence=dev.veltraph.client.garden.jacob.JacobRewardJournalConfigFence.capture(issuedConfig).orElse(null);if(fence==null)return null;
                var before=file.read();if(!settledOutcomesMatch(before.value()))return null;var id=java.util.UUID.randomUUID();var next=before.value().prepare(id,configuredProfile,contractHash);var saved=file.write(before,next);
                if(!activeOwner()||client.player!=nativePlayer||client.level!=nativeWorld||client.getConnection()!=nativeListener||client.player.containerMenu!=nativeMenu||!client.player.containerMenu.getCarried().isEmpty()
                        ||!moduleManager.lifecycleCurrent(lifecycle)||!fence.current(issuedConfig)||!file.current(saved))return null;
                return new SavedIntent(this,id,saved,fence);
            }catch(java.io.IOException|RuntimeException refused){return null;}
        }
        public boolean current(dev.veltraph.client.garden.jacob.mc.McJacobRewardModule owner,SavedIntent intent){
            try{require(owner);return intent!=null&&intent.issuer==this&&intent.configFence.current(issuedConfig)&&file.current(intent.saved)&&settledOutcomesMatch(intent.saved.after().value());}
            catch(java.io.IOException|RuntimeException refused){return false;}
        }
        public SavedIntent acknowledge(dev.veltraph.client.garden.jacob.mc.McJacobRewardModule owner,SavedIntent intent,dev.veltraph.client.garden.jacob.mc.McJacobRewardModule.NativeReceipt receipt){
            try{require(owner);if(!activeOwner()||!current(owner,intent)||receipt==null||owner.nativeReceipt().orElse(null)!=receipt||receipt.tickets()<1)return null;
                var original=intent.saved.after().value().pending().orElseThrow();var beforeOutcome=outcomes.read();
                var confirmed=outcomes.write(beforeOutcome,beforeOutcome.value().recordQualifiedOutcome(original,receipt.id()));
                // Outcome failure (including after rename) cannot release the separate write-ahead intent.
                if(!activeOwner()||!current(owner,intent)||!intent.configFence.current(issuedConfig)||!outcomes.current(confirmed)||owner.nativeReceipt().orElse(null)!=receipt)return null;
                // Only after actual forced/directory-synced/readback outcome can the intent be released.
                var saved=file.write(intent.saved.after(),intent.saved.after().value().acknowledge(intent.id,receipt.id()));
                if(!activeOwner()||!intent.configFence.current(issuedConfig)||!file.current(saved)||!outcomes.current(confirmed)||owner.nativeReceipt().orElse(null)!=receipt)return null;
                return new SavedIntent(this,intent.id,saved,intent.configFence);
            }catch(java.io.IOException|RuntimeException refused){return null;}
        }
        public void unknown(dev.veltraph.client.garden.jacob.mc.McJacobRewardModule owner,SavedIntent intent){
            try{require(owner);if(intent==null||intent.issuer!=this)return;var before=file.read();var pending=before.value().pending().orElse(null);if(pending==null||!pending.id().equals(intent.id))return;var next=before.value().unknown(intent.id);if(next!=before.value())file.write(before,next);}
            catch(java.io.IOException|RuntimeException refused){/* Existing UNRESOLVED already blocks every future claim; never clear on failure. */}
        }
        /** Explicit user metadata decision. It never touches a flow, native receipt or farm. */
        public boolean reconcile(dev.veltraph.client.garden.jacob.mc.McJacobRewardModule owner,java.util.UUID id,String contractHash,dev.veltraph.client.garden.jacob.JacobRewardJournal.Decision decision){
            try{require(owner);if(owner.isEnabled()||farmMaintenance!=null&&farmMaintenance.tokenFor(owner)!=null)return false;var before=file.read();var saved=file.write(before,before.value().reconcile(id,contractHash,decision));return file.current(saved);}
            catch(java.io.IOException|RuntimeException refused){return false;}
        }
        public String status(dev.veltraph.client.garden.jacob.mc.McJacobRewardModule owner){try{require(owner);var journal=file.read().value();return journal.pending().map(e->"UNRESOLVED "+e.id()+" contract="+e.contractHash()+" configuredFarmProfile="+e.configuredFarmProfile()).orElse("Journal: "+journal.entries().size()+" retained local records; no unresolved intent");}catch(java.io.IOException|RuntimeException refused){return "Journal: unavailable/corrupt; claims blocked";}}
    }

    private static boolean confirmConfigSaved() {
        saveConfig();
        return jsonConfig!=null&&!jsonConfig.isDirty();
    }

    /** Exact module callback; never blindly clears a responder or borrower's use mapping. */
    private static void suspendOwnedMiningUse(){
        if(moduleManager!=null)moduleManager.inputOwner().filter(dev.veltraph.client.mining.module.MiningModule.class::isInstance)
                .map(dev.veltraph.client.mining.module.MiningModule.class::cast)
                .ifPresent(dev.veltraph.client.mining.module.MiningModule::suspendOwnedUse);
    }

    private static void releaseNativeInputs(Minecraft client) {
        releaseOriginalRootLogicalAll();
        for(var key:new net.minecraft.client.KeyMapping[]{client.options.keyUp,client.options.keyDown,
                client.options.keyLeft,client.options.keyRight,client.options.keyJump,
                client.options.keyShift,client.options.keySprint,client.options.keyAttack,client.options.keyUse})
            dev.veltraph.client.service.mc.McHeldKeys.setHeld(key,false);
    }

    private static dev.veltraph.client.observation.unexpected.UnexpectedBehaviorMonitor.Activation unexpectedActivation() {
        String owner="observer";
        if(hydra!=null&&hydra.hasActiveLocalReaction())owner="failsafe_response";
        else if(combatAutoHeal!=null&&combatAutoHeal.ownsControl())owner="survival";
        else if(moduleManager!=null) {
            owner=moduleManager.inputOwner().map(Module::id).orElse("observer");
            if(owner.equals("observer")&&farmMaintenance!=null&&farmMaintenance.controlOwnerId()!=null)
                owner=farmMaintenance.controlOwnerId();
            if(owner.equals("observer")&&foragingMaintenance!=null&&foragingMaintenance.controlOwnerId()!=null)
                owner=foragingMaintenance.controlOwnerId();
            if(owner.equals("observer")&&galateaWorkflows!=null&&galateaWorkflows.controlOwnerId()!=null)
                owner=galateaWorkflows.controlOwnerId();
        }
        if(!java.util.Objects.equals(owner,unexpectedOwner)){unexpectedOwner=owner;unexpectedGeneration++;}
        return new dev.veltraph.client.observation.unexpected.UnexpectedBehaviorMonitor.Activation(owner,unexpectedGeneration,!owner.equals("observer"));
    }

    private static void updateUnexpectedObservations(Minecraft client,long now) {
        if(!config.get(dev.veltraph.client.observation.unexpected.UnexpectedBehaviorConfig.ENABLED)) {
            if(unexpectedObserver!=null){unexpectedObserver.close();unexpectedObserver=null;}
            return;
        }
        ensureUnexpectedObserver(client);
        try{
            var origin=suspendedFarmReturn==null?null:dev.veltraph.client.rewarp.mc.IssuedFarmTransportOrigin.expectedForOriginalRoot(suspendedFarmReturn);
            unexpectedObserver.tick(now,origin);
        }catch(RuntimeException error){
            unexpectedObserver.close();unexpectedObserver=null;
            VeltraphLog.basic("Observation","Observation unavailable: "+error.getClass().getSimpleName());
        }
    }

    private static void ensureUnexpectedObserver(Minecraft client){
        if(unexpectedObserver==null)unexpectedObserver=new dev.veltraph.client.observation.unexpected.McUnexpectedBehaviorMonitor(
                client,VeltraphBootstrap::unexpectedActivation,new dev.veltraph.client.observation.unexpected.UnexpectedStopPolicy(
                        ()->config.get(dev.veltraph.client.observation.unexpected.UnexpectedBehaviorConfig.AUTO_STOP),
                        ()->{
                            unexpectedStopFrame.stop();
                            // The ordinary global manual-stop hook records the current player/connection
                            // suppression scope and cancels scheduler, maintenance, issued returns and responses.
                            moduleManager.stopAll();
                            if(farmServices!=null){farmServices.rotation().cancelRotation();farmServices.input().releaseAllMovement();farmServices.breaking().setBreaking(false);}
                            releaseNativeInputs(client);
                        },finding->{
                    if(finding.classification()!=dev.veltraph.client.observation.unexpected.UnexpectedBehaviorMonitor.Classification.UNEXPECTED)return;
                    long cooldown=Math.max(0,Math.min(60000,config.get(dev.veltraph.client.observation.unexpected.UnexpectedBehaviorConfig.ALERT_COOLDOWN_MS)));
                    if(config.get(dev.veltraph.client.observation.unexpected.UnexpectedBehaviorConfig.NOTIFY)
                            &&(unexpectedLastAlert==Long.MIN_VALUE||finding.timeMillis()-unexpectedLastAlert>=cooldown)) {
                        unexpectedLastAlert=finding.timeMillis();
                        safeChat("Veltraph: unexplained "+finding.kind()+" change. Cause uncertain; /vdebug unusual shows local evidence.");
                    }
                }));
    }
    private static void baselineUnexpectedObservations(Minecraft client,long now){
        if(!config.get(dev.veltraph.client.observation.unexpected.UnexpectedBehaviorConfig.ENABLED))return;
        ensureUnexpectedObserver(client);
        unexpectedObserver.baseline(now);
    }

    public static dev.veltraph.client.chocolate.ChocolateFactoryModule chocolateFactory(){return chocolateFactoryModule;}
    public static void rearmSurvivalAssist(){survivalStopSuppressed=false;}
    public static void onSourceFloorVerifiedBlock(net.minecraft.client.multiplayer.ClientLevel world,net.minecraft.core.BlockPos position,net.minecraft.world.level.block.state.BlockState incoming){
        if(hydraAdapter!=null)hydraAdapter.verifiedFloorBlock(world,position,incoming);
    }
    /** Read-only current source diagnostics; unavailable reporting never affects gameplay/observation. */
    public static dev.veltraph.client.hydra.mc.McCropSpeciesReplantObserver.Diagnostic cropSpeciesDiagnostic() {
        try { return cropSpeciesObserver==null?null:cropSpeciesObserver.diagnostic(); }
        catch(Throwable unavailable) { return null; }
    }
    /** Native attack ingress is read only and can never actuate or attribute a staff identity. */
    public static void onSourceCropAttack(net.minecraft.client.multiplayer.MultiPlayerGameMode mode,net.minecraft.core.BlockPos pos) {
        if(cropSpeciesObserver!=null)cropSpeciesObserver.attack(mode,pos);
    }
    /** Deliberate stop may disable the owner; only native PWC/menu/view freshness is checked here. */
    private static boolean cropSpeciesPwcCurrent(Minecraft client,dev.veltraph.client.hydra.CropSpeciesReplantWatcher.Scope scope) {
        var player=client.player;var world=client.level;
        return client.isSameThread()&&player!=null&&world!=null&&client.gameMode!=null
                &&scope.player()==player&&scope.world()==world&&scope.connection()==client.getConnection()
                &&player.isAlive()&&player.level()==world&&client.getCameraEntity()==player
                &&!client.isPaused()&&!world.tickRateManager().isEntityFrozen(player)
                &&client.gui.screen()==null&&player.containerMenu==player.inventoryMenu
                &&player.containerMenu.getCarried().isEmpty();
    }
    private static void updateCropSpeciesObservations(Minecraft client,long now) {
        try {
        if(cropSpeciesObserver==null) {
            var delivery=new dev.veltraph.client.hydra.CropSpeciesReplantDelivery(
                    ()->dev.veltraph.client.hydra.CropSpeciesObserverConfig.capture(config),
                    scope->cropSpeciesPwcCurrent(client,scope),()->{
                        // Existing ordinary unexpected-observation manual-stop route; never responses.
                        unexpectedStopFrame.stop();
                        moduleManager.stopAll();
                        if(farmServices!=null){farmServices.rotation().cancelRotation();farmServices.input().releaseAllMovement();farmServices.breaking().setBreaking(false);}
                        releaseNativeInputs(client);
                    },(finding,policy)->{
                        long cooldown=Math.max(0,Math.min(60000,policy.cooldownMillis()));
                        if(cropSpeciesLastAlert==Long.MIN_VALUE||finding.timeMillis()<cropSpeciesLastAlert||finding.timeMillis()-cropSpeciesLastAlert>=cooldown) {
                            cropSpeciesLastAlert=finding.timeMillis();
                            safeChat("Veltraph: observed " + finding.count() + " wrong-species crop replacements. Cause uncertain; no staff identity inferred.");
                        }
                    });
            cropSpeciesObserver=new dev.veltraph.client.hydra.mc.McCropSpeciesReplantObserver(
                    client,moduleManager,()->config,()->unexpectedGeneration,delivery::deliver);
        }
        cropSpeciesObserver.tick(now);
        } catch(Throwable unavailable) { if(cropSpeciesObserver!=null)cropSpeciesObserver.reset(); }
    }
    public static java.util.List<String> unexpectedDiagnostics(){return unexpectedObserver==null
            ?java.util.List.of("Unexpected-behavior observation is disabled or unavailable")
            :unexpectedObserver.diagnosticLines(12);}

    /** Flush the config to disk if it has unsaved changes (used on discrete events / world stop). */
    private static void flushConfigIfDirty() {
        if (jsonConfig != null && jsonConfig.isDirty()) {
            saveConfig();
        }
    }

    /** Observe every actual native container input, including menus unrelated to Harp. */
    public static void onNativeContainerInput(){
        if(moduleManager!=null)moduleManager.get("auto_harp").filter(dev.veltraph.client.gui.HarpModule.class::isInstance)
                .map(dev.veltraph.client.gui.HarpModule.class::cast).ifPresent(dev.veltraph.client.gui.HarpModule::onNativeContainerInput);
    }

    /** Ordered actual sidebar rows; strict adjacent-row attribution depends on native score order. */
    private static java.util.List<String> actualJacobSidebarRows(){
        return dev.veltraph.client.garden.jacob.mc.McJacobSidebarRows.read(Minecraft.getInstance());
    }

    /** Native sorted/tab-limited and team-formatted rows; no footer, lore or inferred count. */
    private static java.util.List<String> actualTrapTabRows(){
        var client=Minecraft.getInstance();
        if(client.getConnection()==null)return java.util.List.of();
        var overlay=client.gui.hud.getTabList();
        if(!((Object)overlay instanceof dev.veltraph.client.mixin.PlayerListHudAccessor access))return java.util.List.of();
        var rows=new java.util.ArrayList<String>();
        for(var info:access.veltraph$getPlayerInfos())rows.add(overlay.getNameForDisplay(info).getString());
        return java.util.List.copyOf(rows);
    }

    /** Keep the exact suspended farm's profile while a committed maintenance lease owns it. */
    private static dev.veltraph.client.garden.jacob.JacobContestTracker.Scope jacobScope(Minecraft client){
        if(client.player==null||client.level==null||client.getConnection()==null||moduleManager==null)return null;
        dev.veltraph.client.farming.module.FarmModule farm=null;
        if(farmMaintenance!=null&&farmMaintenance.active()
                &&farmMaintenance.effectiveInputOwner() instanceof dev.veltraph.client.rewarp.mc.FarmMaintenanceCoordinator.Participant helper){
            var token=farmMaintenance.tokenFor(helper);
            if(token!=null)for(var controller:moduleManager.enabledControllers())
                if(controller instanceof dev.veltraph.client.farming.module.FarmModule captured&&captured.maintenanceOwnedBy(token)){
                    if(farm!=null)return null;
                    farm=captured;
                }
        }else{
            var farms=moduleManager.enabledControllers().stream().filter(dev.veltraph.client.farming.module.FarmModule.class::isInstance).toList();
            if(farms.size()==1&&!((dev.veltraph.client.farming.module.FarmModule)farms.getFirst()).isSuspended())
                farm=(dev.veltraph.client.farming.module.FarmModule)farms.getFirst();
        }
        if(farm==null||!farm.isEnabled())return null;
        return new dev.veltraph.client.garden.jacob.JacobContestTracker.Scope(client.player,client.level,client.getConnection(),
                dev.veltraph.client.rewarp.mc.McFarmCycleCoordinator.profileId(farm));
    }

    /** Native display rows with only color codes removed; exact parent bindings retain case/spacing. */
    private static java.util.List<String> actualPestSidebarRows(Minecraft client){
        var scoreboard=client.level.getScoreboard();
        var objective=scoreboard.getDisplayObjective(net.minecraft.world.scores.DisplaySlot.SIDEBAR);
        if(objective==null)return java.util.List.of();
        return scoreboard.listPlayerScores(objective).stream().filter(entry->!entry.isHidden())
                .sorted(java.util.Comparator.comparingInt(net.minecraft.world.scores.PlayerScoreEntry::value).reversed()
                        .thenComparing(net.minecraft.world.scores.PlayerScoreEntry::owner,String.CASE_INSENSITIVE_ORDER))
                .limit(dev.veltraph.client.garden.jacob.mc.McJacobSidebarRows.NATIVE_DISPLAY_LIMIT)
                .map(entry->net.minecraft.world.scores.PlayerTeam.formatNameForTeam(scoreboard.getPlayersTeam(entry.owner()),entry.ownerName()).getString())
                .map(dev.veltraph.client.game.LocationText::stripColorCodes).toList();
    }

    /** One persistent observed parent, dispatched only through the ordinary registered lease/guard. */
    private static void tickObservedPestAdmission(Minecraft client,boolean exclusiveResponse){
        if((!config.get(dev.veltraph.client.config.PestAutoAdmissionConfig.PEST_AUTO_ADMISSION_ENABLED)
                &&!config.get(dev.veltraph.client.config.PestAutoAdmissionConfig.PEST_NEARBY_ENABLED))
                ||client.player==null||client.level==null||client.getConnection()==null||!client.player.isAlive()
                ||client.gui.screen()!=null||client.player.containerMenu!=client.player.inventoryMenu
                ||!client.player.containerMenu.getCarried().isEmpty()||exclusiveResponse||farmMaintenance==null
                ||farmMaintenance.active()||scheduledRestActive()||(farmCycle!=null&&farmCycle.active())
                ||(autoRewarp!=null&&autoRewarp.state()!=dev.veltraph.client.rewarp.AutoRewarpState.IDLE)
                ||(savedAnchorRecovery!=null&&savedAnchorRecovery.isEnabled())
                ||(foragingMaintenance!=null&&foragingMaintenance.active())||(galateaWorkflows!=null&&galateaWorkflows.active())
                ||(combatAutoHeal!=null&&combatAutoHeal.ownsControl())
                ||(hydra!=null&&(hydra.hasActiveLocalReaction()||hydra.hasActiveGeneralResponse()))){
            pestAutoAdmissionTracker.reset();if(nearbyPestAdmission!=null)nearbyPestAdmission.reset();return;
        }
        var current=gameContext.current(); // Resolves the actual native context at this dispatch boundary.
        var controllers=moduleManager.enabledControllers();
        if(!current.onHypixel()||!current.inSkyBlock()||!current.isArea(dev.veltraph.client.game.GameArea.GARDEN)
                ||controllers.size()!=1||!(controllers.getFirst() instanceof dev.veltraph.client.farming.module.FarmModule farm)
                ||!farm.isEnabled()||farm.isSuspended()){pestAutoAdmissionTracker.reset();if(nearbyPestAdmission!=null)nearbyPestAdmission.reset();return;}
        var plot=dev.veltraph.client.garden.pests.GardenPlotBounds.at(client.player.getX(),client.player.getZ());
        if(plot.isEmpty()||plot.getAsInt()==0){pestAutoAdmissionTracker.reset();if(nearbyPestAdmission!=null)nearbyPestAdmission.reset();return;}
        if(nearbyPestAdmission!=null&&nearbyPestAdmission.tick(farm,clock.currentTick(),plot.getAsInt(),guard))return;
        if(!config.get(dev.veltraph.client.config.PestAutoAdmissionConfig.PEST_AUTO_ADMISSION_ENABLED)){pestAutoAdmissionTracker.reset();return;}
        dev.veltraph.client.garden.pests.PestAutoAdmissionBindings bindings;
        try{bindings=dev.veltraph.client.garden.pests.PestAutoAdmissionBindings.read(
                config.get(dev.veltraph.client.config.PestAutoAdmissionConfig.PEST_AUTO_ADMISSION_BINDINGS));}
        catch(IllegalArgumentException invalid){pestAutoAdmissionTracker.reset();if(nearbyPestAdmission!=null)nearbyPestAdmission.reset();return;}
        var scope=new dev.veltraph.client.garden.pests.PestAutoAdmission.Scope(client.player,client.level,client.getConnection(),
                dev.veltraph.client.rewarp.mc.McFarmCycleCoordinator.profileId(farm),plot.getAsInt());
        long tick=clock.currentTick();
        pestAutoAdmissionTracker.observe(scope,tick,bindings,actualTrapTabRows().stream()
                .map(dev.veltraph.client.game.LocationText::stripColorCodes).toList(),actualPestSidebarRows(client));
        var claim=pestAutoAdmissionTracker.claim(scope,tick,config.get(dev.veltraph.client.config.PestConfig.LEAVE_ONE_PEST)).orElse(null);
        if(claim==null)return;
        Module selected=switch(claim.target()){
            case CURRENT_PLOT->moduleManager.get(dev.veltraph.client.garden.pests.mc.McPestMaintenanceModule.ID)
                    .filter(dev.veltraph.client.garden.pests.mc.McPestMaintenanceModule.class::isInstance).orElse(null);
            case TRACKER->moduleManager.get(dev.veltraph.client.garden.pests.mc.McPestTrackedMaintenanceModule.ID)
                    .filter(dev.veltraph.client.garden.pests.mc.McPestTrackedMaintenanceModule.class::isInstance).orElse(null);
        };
        if(selected==null||selected.isEnabled()||!(selected instanceof dev.veltraph.client.rewarp.mc.FarmMaintenanceCoordinator.Participant participant))return;
        boolean reserved=guard.start(selected);
        var token=farmMaintenance.tokenFor(participant);
        boolean consumed=reserved&&pestAutoAdmissionTracker.consume(claim,scope,tick,token,()->
                moduleManager.get(selected.id()).orElse(null)==selected&&farmMaintenance.tokenFor(participant)==token
                &&farmMaintenance.state()==dev.veltraph.client.rewarp.FarmMaintenanceLease.State.PREPARED
                &&farmMaintenance.preparedActivationValid(participant,token));
        if(!consumed){
            if(token!=null)farmMaintenance.cancel(token,"Observed pest admission reservation was not consumed");
            pestAutoAdmissionTracker.reject(claim,scope,tick); // Actual refusal retires; previews never do.
        }
    }

    public static java.util.Optional<dev.veltraph.client.garden.jacob.JacobContestTracker.Ended> lastJacobEnded(){return java.util.Optional.ofNullable(lastJacobEnded);}

    private static void observeFreshStart(Minecraft client,Module module){
            survivalStopSuppressed=false;
            scriptScheduler.ownerStarted(module);
            long now=System.currentTimeMillis();
            if(!module.isPassiveObserver()){
                var baselinePlayer=client.player;var baselineWorld=client.level;var baselineConnection=client.getConnection();
                long baselineGeneration=unexpectedActivation().generation();
                var baselineObserver=unexpectedObserver;
                boolean captured=dev.veltraph.client.observation.unexpected.UnexpectedBaselineCapture.capture(
                        ()->baselineUnexpectedObservations(client,now),
                        ()->moduleManager.get(module.id()).orElse(null)==module&&module.isEnabled()
                                &&client.player==baselinePlayer&&client.level==baselineWorld&&client.getConnection()==baselineConnection
                                &&unexpectedGeneration==baselineGeneration,
                        ()->moduleManager.disable(module.id()),
                        failure->{if(unexpectedObserver!=null&&unexpectedObserver==baselineObserver){unexpectedObserver.close();unexpectedObserver=null;}
                            VeltraphLog.basic("Observation","Activation baseline unavailable: "+failure.getClass().getSimpleName());});
                if(!captured)return;
            }
            updateLocalMetrics(client,now); // Bind the fresh collector context before excluding prior receipts.
            if(localFinancial!=null)try{localFinancial.afterFreshStart(module,now);}
            catch(RuntimeException ignored){/* Passive attribution must not interfere with activation. */}
            if(localMetricsOwners!=null)try{localMetricsOwners.afterFreshStart(module,now);}
            catch(RuntimeException ignored){/* A passive metric cannot interfere with owner activation. */}
    }

    private static boolean visitorHistoryAffiliated(VisitorHistoryDeferredRegistration registration,Minecraft client){
        return initialized&&registration!=null&&Minecraft.getInstance()==registration.client
                &&dev.veltraph.client.garden.visitor.history.VisitorHistoryInstallationAffiliation.current(
                    registration.client,registration.location,registration,client,gameContext,visitorHistoryDeferred,
                    client!=null&&client.isSameThread(),registration.stopped);
    }
    private static void tickVisitorHistoryObservation(Minecraft client){
        var registration=visitorHistoryDeferred;
        if(!visitorHistoryAffiliated(registration,client))return;
        if(registration.installed==null){
            if(visitorHistoryObserver!=null)return; // Never replace or retire a handle not owned by this registration.
            var created=dev.veltraph.client.garden.visitor.history.mc.McVisitorHistoryPacketObserver.install(client,registration.location);
            if(!visitorHistoryAffiliated(registration,client)||visitorHistoryObserver!=null){created.retire();return;}
            registration.installed=created;visitorHistoryObserver=created;
        }
        if(visitorHistoryObserver==registration.installed)registration.installed.observeLifecycle();
    }
    private static void stopVisitorHistoryObservation(VisitorHistoryDeferredRegistration registration,Minecraft client){
        if(!visitorHistoryAffiliated(registration,client))return;
        registration.stopped=true;
        var owned=registration.installed;
        if(owned!=null&&visitorHistoryObserver==owned){owned.retire();if(visitorHistoryObserver==owned)visitorHistoryObserver=null;registration.installed=null;}
    }
    private static final ClientTickEvents.EndTick originalRootEndTickCallback=VeltraphBootstrap::onClientTick;
    private static boolean originalRootEndTickRegistered;
    /** Identity only; this getter creates no tick, callback or source. */
    public static Object originalRootEndTickCallbackIdentity(){return originalRootEndTickCallback;}
    private static RootOriginalGameplayTickInvocation originalGameplayTickInvocation;
    private static final class RootOriginalGameplayTickInvocation {
        final dev.veltraph.client.service.mc.RootNativeGameplayTickPhase.RootTickTicket nativeTick;
        final Minecraft client;final Object player,world,listener,connection,user,options,gameMode;
        final ModuleManager registry;final ModuleActivationGuard activation;final RootOriginalNativeInstallation installation;
        final RootInputReleaseRegistration registration;final ConfigService store;final RootOriginalInputPublication publication;
        final Thread thread=Thread.currentThread();boolean revoked,closed,normalReturned;RootFarmReturnTick returnedFarmTick;
        RootOriginalGameplayTickInvocation(Minecraft client,dev.veltraph.client.service.mc.RootNativeGameplayTickPhase.RootTickTicket nativeTick){
            this.client=client;this.nativeTick=nativeTick;player=client.player;world=client.level;listener=client.getConnection();
            connection=client.getConnection()==null?null:client.getConnection().getConnection();user=client.getUser();options=client.options;gameMode=client.gameMode;
            registry=moduleManager;activation=guard;installation=originalNativeInstallation;registration=rootInputReleaseRegistration;store=config;publication=originalInputPublication;
        }
        /** Exact retained native claim only; no provider/context callback at final source leaves. */
        boolean activeNativeCurrent(){try{return !revoked&&!closed&&originalGameplayTickInvocation==this
            &&Thread.currentThread()==thread&&nativeTick!=null&&nativeTick.current();}catch(Throwable unavailable){return false;}}
        /** Historical origin after the genuine Root body and its exact native claim have normally closed. */
        boolean sourceOriginCurrent(){try{return !revoked&&Thread.currentThread()==thread&&nativeTick!=null
            &&(closed?normalReturned&&nativeTick.normalReturned():activeNativeCurrent());}catch(Throwable unavailable){return false;}}
        boolean current(){return activeNativeCurrent()
            &&client==Minecraft.getInstance()&&client.isSameThread()&&registry==moduleManager&&activation==guard&&installation==originalNativeInstallation
            &&registration==rootInputReleaseRegistration&&store==config&&publication==originalInputPublication&&client.player==player&&client.level==world
            &&client.getConnection()==listener&&(listener==null?connection==null:client.getConnection().getConnection()==connection)
            &&client.getUser()==user&&client.options==options&&client.gameMode==gameMode;}
    }
    private static boolean originalGameplayInvocationCurrent(){var actual=originalGameplayTickInvocation;return actual!=null&&actual.current();}
    private static void onClientTick(Minecraft client) {
        dev.veltraph.client.service.mc.RootNativeGameplayTickPhase.RootTickTicket nativeTick=null;
        RootOriginalGameplayTickInvocation actual=null;
        try{
            // The real claim remains the first operation; linkage/setup failure cannot suppress the old callback.
            nativeTick=dev.veltraph.client.service.mc.RootNativeGameplayTickPhase.claimOriginalRootTick(client,originalRootEndTickCallback);
            if(originalGameplayTickInvocation!=null)originalGameplayTickInvocation.revoked=true;
            else if(nativeTick!=null){actual=new RootOriginalGameplayTickInvocation(client,nativeTick);originalGameplayTickInvocation=actual;}
        }catch(Throwable unavailable){if(actual!=null)actual.revoked=true;}
        boolean normal=false;
        try{onOriginalClientTick(client);normal=true;}
        finally{
            if(actual!=null){
                var returned=actual.returnedFarmTick;
                if(returned!=null&&returned.gameplay==actual){
                    try{returned.normalReturned=normal&&!actual.revoked&&actual.nativeTick.current();}
                    catch(Throwable unavailable){returned.normalReturned=false;}
                }
                actual.closed=true;if(!normal)actual.revoked=true;if(originalGameplayTickInvocation==actual)originalGameplayTickInvocation=null;
            }
            try{dev.veltraph.client.service.mc.RootNativeGameplayTickPhase.finishRootTick(nativeTick,normal&&actual!=null&&!actual.revoked);
                if(actual!=null)actual.normalReturned=normal&&!actual.revoked&&nativeTick!=null&&nativeTick.normalReturned();}
            catch(Throwable unavailable){if(actual!=null){actual.normalReturned=false;actual.revoked=true;}
                /* Negative observation failure cannot replace the original callback result/exception. */}
        }
    }
    private static void onOriginalClientTick(Minecraft client) {
        // Constructor-time startup has no native client thread yet. Bind only the originally installed private Root identity here.
        bindOriginalRootInputReleaseAtNativePhase(client);
        tickVisitorHistoryObservation(client);
        unexpectedStopFrame.begin();
        // Sample native interference before an engine can correct/overwrite it, then acknowledge
        // owned actions afterwards. Identical samples produce no second observation.
        updateUnexpectedObservations(client,System.currentTimeMillis());
        updateCropSpeciesObservations(client,System.currentTimeMillis());
        try{
            if(!unexpectedStopFrame.stopped())onGameplayTick(client);
            else {
                // Keep central time/context current without advancing a scheduler, cleanup actuator,
                // deferred helper, recovery or keybind activation after the stop in this same frame.
                clock.advanceTick();gameContext.current();
                lastGeneralPlayer=client.player;lastGeneralConnection=client.getConnection();lastWorld=client.level;
                if(client.player==null||client.level==null)skyBlockHealth.reset();
                flushConfigIfDirty();
            }
        }finally{
            var failedStop=activeFarmReturnManualClose;
            if(failedStop!=null&&!failedStop.current(suspendedFarmReturn)){
                failedStop.revoked=true;if(activeFarmReturnManualClose==failedStop)activeFarmReturnManualClose=null;
            }
            long now=System.currentTimeMillis();
            updateUnexpectedObservations(client,now);
            updateLocalMetrics(client,now);
        }
    }

    private static final class RootGameplayReturnFrame {
        final Minecraft client;final Thread thread=Thread.currentThread();RootFarmReturnTick returnedTick;
        final RootOriginalGameplayTickInvocation gameplay;
        boolean keysPolled,generalPolled,hydraPolled,schedulerPolled,terminalGameplayRetired;
        RootGameplayReturnFrame(Minecraft client){this.client=client;gameplay=originalGameplayTickInvocation;}
        boolean authenticNativeCurrent(){return gameplay!=null&&originalGameplayTickInvocation==gameplay&&!gameplay.revoked&&!gameplay.closed
            &&gameplay.client==client&&Thread.currentThread()==gameplay.thread&&gameplay.nativeTick.current();}
    }
    /** Only normal return of the actual registered Root body seals a retained native frame receipt. */
    private static void onGameplayTick(Minecraft client) {
        if(activeGameplayReturnFrame!=null)throw new IllegalStateException("Root gameplay dispatch reentry");
        var frame=new RootGameplayReturnFrame(client);activeGameplayReturnFrame=frame;boolean normal=false;
        try{onGameplayTickBody(client);normal=true;}
        finally{
            var tick=frame.returnedTick;
            if(tick!=null){
                tick.dispatchClosed=true;
                if(!normal)tick.normalReturned=false;
                if(frame.gameplay!=null&&tick.gameplay==frame.gameplay)frame.gameplay.returnedFarmTick=tick;
            }
            if(activeFarmReturnTick==tick)activeFarmReturnTick=null;
            var admission=activeFarmReturnAdmission;
            if(admission!=null&&admission.tick==tick)activeFarmReturnAdmission=null;
            if(activeGameplayReturnFrame==frame)activeGameplayReturnFrame=null;
        }
    }
    private static void onGameplayTickBody(Minecraft client) {
        clock.advanceTick();
        if(tickOwnedSuspendedFarmReturn(client))return;
        boolean terminalOnly=activeGameplayReturnFrame!=null&&activeGameplayReturnFrame.terminalGameplayRetired;
        // Validate issued tickets before native refs, helpers, inputs or engine callbacks can change.
        if(!terminalOnly&&scriptScheduler!=null)scriptScheduler.validateBeforeUpdates();
        observeScheduledConnection(client);
        var contestScope=jacobScope(client);
        jacobTracker.tick(contestScope,contestScope!=null&&gameContext.current().isArea(dev.veltraph.client.game.GameArea.GARDEN),
                actualJacobSidebarRows(),clock.currentTick());
        jacobTracker.takeEnded().ifPresent(event->{lastJacobEnded=event;if(jacobReward!=null)jacobReward.observedEnd(contestScope,event);});

        Module pausedRestOwner=scriptScheduler==null?null:dev.veltraph.client.scheduling.InstalledRestLifecycle.exactPausedOwner(
                moduleManager.all(),scriptScheduler::ownsPaused);
        boolean ownedRestTransition=scriptScheduler!=null&&dev.veltraph.client.scheduling.InstalledRestLifecycle.permitsNativeTransition(
                pausedRestOwner,scriptScheduler.expectedWorldTransition());
        if(!terminalOnly&&pausedRestOwner!=null&&(ownedRestTransition||scriptScheduler.state()==dev.veltraph.client.scheduling.SourceRestRuntime.State.WAITING_TO_DISCONNECT)){
            tickOwnedScheduledRest(client);return;
        }
        // Separate issued SAME-CONNECTION Sven authority, before any old Root refs are replaced.
        if(!terminalOnly&&tickOwnedBottleWarp(client))return;
        if(!terminalOnly&&tickOwnedGeorgeWarp(client))return;
        if(!terminalOnly&&tickOwnedGhostWarp(client))return;
        if(!terminalOnly&&tickOwnedSvenWarp(client))return;
        if(!terminalOnly&&tickOwnedHubWarp(client))return;
        if(client.player!=null&&client.level!=null&&client.getConnection()!=null
                &&((lastGeneralPlayer!=null&&client.player!=lastGeneralPlayer)
                ||(lastGeneralConnection!=null&&client.getConnection()!=lastGeneralConnection))) {
            boolean stoppedSameScope=survivalStopSuppressed&&survivalStoppedPlayerId!=null
                    &&survivalStoppedPlayerId.equals(client.player.getUUID())
                    &&survivalStoppedConnection==client.getConnection();
            if(generalFailsafe!=null)generalFailsafe.onWorldStop();
            if(hydra!=null)hydra.resetResponses();
            moduleManager.stopAll();
            releaseOriginalRootLogicalAll();
            survivalStopSuppressed=stoppedSameScope; // Same UUID/connection replacement cannot undo a prior STOP.
        }
        lastGeneralPlayer=client.player;lastGeneralConnection=client.getConnection();
        if(!terminalOnly&&farmMaintenance!=null)tickOriginalFarmMaintenance();
        if(!terminalOnly)moduleManager.maintenanceTick();
        if (client.player == null || client.level == null) {
            if(generalFailsafe!=null)generalFailsafe.onWorldStop();
            if(scriptScheduler!=null)scriptScheduler.cancel("World/player unavailable");
            if(farmMaintenance!=null)farmMaintenance.cancelActive("World/player unavailable");
            if(foragingMaintenance!=null)foragingMaintenance.cancelActive("World/player unavailable");
            if(galateaWorkflows!=null)galateaWorkflows.cancelActive("World/player unavailable");
            if(farmAutoRecovery!=null)farmAutoRecovery.cancel();
            if(farmCycle!=null)farmCycle.cancel(false);
            if(farmServices!=null) farmServices.rotation().cancelRotation();
            skyBlockHealth.reset();
            if(combatAutoHeal!=null) combatAutoHeal.reset();
            // No world/player (menu / disconnect). Feed the anti-staff watcher one routing tick FIRST,
            // while it can still observe that a macro was running, so the LIMBO_ROUTING event fires.
            if (hydraAdapter != null) {
                pollRootHydraOnce();
                hydraAdapter.onWorldStop();
            }
            // Then stop everything and release input ownership (mouse-look + keyboard suppression
            // released, all held keys released by stopAll).
            if (!moduleManager.enabledModules().isEmpty()) {
                VeltraphLog.basic("VeltraphBootstrap.worldStop",
                        "stopping macros because world/player became unavailable");
                safeChat("Veltraph: stopped macro because the world changed.");
                moduleManager.stopAll();
            }
            releaseOriginalRootLogicalAll();
            if(scriptScheduler!=null)pollRootSchedulerOnce();
            lastWorld = client.level;
            flushConfigIfDirty(); // persist any pending settings/rewarp changes on menu/disconnect
            return;
        }
        if(lastWorld != null && client.level != lastWorld) {
            if(generalFailsafe!=null)generalFailsafe.onWorldStop();
            if(hydra!=null)hydra.resetResponses();
            if(scriptScheduler!=null)scriptScheduler.cancel("World changed");
            if(farmMaintenance!=null)farmMaintenance.cancelActive("World changed");
            if(foragingMaintenance!=null)foragingMaintenance.cancelActive("World changed");
            if(galateaWorkflows!=null)galateaWorkflows.cancelActive("World changed");
            if(farmAutoRecovery!=null)farmAutoRecovery.cancel();
            if(farmCycle!=null)farmCycle.cancel(false);
            if(farmServices!=null) farmServices.rotation().cancelRotation();
            skyBlockHealth.reset();
            if(combatAutoHeal!=null) combatAutoHeal.reset();
        }
        if (lastWorld != null && client.level != lastWorld && !moduleManager.enabledModules().isEmpty()) {
            VeltraphLog.basic("VeltraphBootstrap.worldStop",
                    "stopping macros because world object changed: "
                            + lastWorld.dimension().identifier() + " -> "
                            + client.level.dimension().identifier());
            safeChat("Veltraph: stopped macro because the world changed.");
            moduleManager.stopAll();
            releaseOriginalRootLogicalAll();
            if (hydraAdapter != null) {
                hydraAdapter.onWorldStop(); // reset position/dimension tracking for the new world
            }
            lastWorld = client.level;
            if(scriptScheduler!=null)pollRootSchedulerOnce();
            return;
        }
        lastWorld = client.level;
        if(!client.player.isAlive()&&scriptScheduler!=null)scriptScheduler.cancel("Player died");
        boolean responseWithoutController=moduleManager.enabledControllers().isEmpty()
                &&((hydra!=null&&hydra.hasActiveGeneralResponse())
                ||(combatAutoHeal!=null&&combatAutoHeal.ownsControl()));
        if(!client.player.isAlive()||(client.gui.screen()!=null&&responseWithoutController)) {
            if(hydra!=null)hydra.resetResponses();
            if(generalFailsafe!=null)generalFailsafe.onWorldStop();
            if(combatAutoHeal!=null)combatAutoHeal.reset();
            if(responseWithoutController||!moduleManager.enabledControllers().isEmpty())moduleManager.stopAll();
            releaseNativeInputs(client);
            return;
        }
        if (client.gui.screen() != null && !moduleManager.enabledControllers().isEmpty()) {
            if(autoRewarp!=null&&autoRewarp.state()!=dev.veltraph.client.rewarp.AutoRewarpState.IDLE) {
                farmAutoRecovery.cancel();moduleManager.stopControllers();releaseOriginalRootLogicalAll();return;
            }
            if (client.gui.screen() instanceof VeltraphScreen) {
                VeltraphLog.basic("VeltraphBootstrap.screenClose",
                        "closing Veltraph screen after macro start: " + client.gui.screen().getClass().getName());
                client.gui.setScreen(null);
            } else if (!((client.gui.screen() instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?>
                    ||client.gui.screen() instanceof net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen)
                    && moduleManager.enabledControllers().stream().allMatch(m->m.allowsScreen(client.gui.screen().getTitle().getString())
                    ||(farmMaintenance!=null&&farmMaintenance.permitsScreenForFarm(m,client.gui.screen().getTitle().getString()))
                    ||(foragingMaintenance!=null&&foragingMaintenance.permitsScreenForParent(m,client.gui.screen().getTitle().getString()))
                    ||(galateaWorkflows!=null&&galateaWorkflows.permitsScreenForParent(m,client.gui.screen().getTitle().getString()))))) {
            VeltraphLog.basic("VeltraphBootstrap.screenStop",
                    "stopping macros because a GUI opened: " + client.gui.screen().getClass().getName());
            safeChat("Veltraph: stopped macro because a GUI was opened.");
            moduleManager.stopControllers();
            if(farmAutoRecovery!=null)farmAutoRecovery.cancel();
            releaseOriginalRootLogicalAll();
            return;
            }
        }
        if(terminalOnly){
            // Ordinary world/player/death/GUI hard stops above remain authoritative. This private
            // completed-frame fact permits no new admission, engine, maintenance or deferred effects.
            pollRootKeysOnce();guard.enforceRunning();
            if(generalFailsafe!=null)pollRootGeneralSafetyOnce();
            if(hydraAdapter!=null)pollRootHydraOnce();
            flushConfigIfDirty();return;
        }
        // Keep the rotation GCD step in sync with the user's actual mouse sensitivity.
        syncMouseSensitivity(client);
        // A user stop takes precedence over initiating a new automatic recovery request.
        pollRootKeysOnce();
        if(farmMaintenance!=null)tickOriginalFarmMaintenance();
        // Own a narrowly matched same-world leave recovery before the location guard or engines run.
        // World/death/menu stops above remain authoritative; passive observers never grant ownership.
        if(farmAutoRecovery!=null&&(farmCycle==null||!farmCycle.active())&&!maintenanceOwnsFarm())farmAutoRecovery.beforeLocationGuard();
        // Stop any running module whose location is now invalid (releases its inputs).
        guard.enforceRunning();
        if(generalFailsafe!=null&&(savedAnchorRecovery==null||!savedAnchorRecovery.isEnabled()))pollRootGeneralSafetyOnce();
        boolean healingBefore=combatAutoHeal!=null && combatAutoHeal.state()!=dev.veltraph.client.combat.heal.HealState.IDLE;
        if(combatAutoHeal!=null&&combatAutoHeal.enabled()&&!previousHealEnabled)survivalStopSuppressed=false;
        previousHealEnabled=combatAutoHeal!=null&&combatAutoHeal.enabled();
        if(combatAutoHeal!=null&&!survivalStopSuppressed) {
            // Finish the exact Fishing temporary tool lease before the healer captures slot/view.
            // stopCombat cannot restore across foreign authority/context and cannot rearm this session.
            if(nativeFishingServices!=null&&registeredFishingModule!=null&&registeredFishingModule.isEnabled()
                    &&moduleManager.get(registeredFishingModule.id()).orElse(null)==registeredFishingModule
                    &&combatAutoHeal.shouldAcquireControl(gameContext.current().inSkyBlock())
                    &&moduleManager.all().stream().noneMatch(module->
                            module instanceof dev.veltraph.client.combat.ghost.GhostGrindModule ghost&&ghost.ownsHealControl()))
                nativeFishingServices.stopCombat();

            if(hydra!=null&&hydra.hasActiveLocalReaction()
                    &&combatAutoHeal.shouldAcquireControl(gameContext.current().inSkyBlock())) {
                hydra.resetResponses();
                if(generalFailsafe!=null)generalFailsafe.onWorldStop();
                moduleManager.stopControllers();
                releaseNativeInputs(client);
                survivalStopSuppressed=false; // This stop is the validated handoff to the healer.
            }
            if(maintenanceOwnsFarm()&&combatAutoHeal.shouldAcquireControl(gameContext.current().inSkyBlock()))
                farmMaintenance.cancelActive("Emergency auto-heal takeover");
            if(foragingMaintenance!=null&&foragingMaintenance.active()&&combatAutoHeal.shouldAcquireControl(gameContext.current().inSkyBlock()))
                foragingMaintenance.cancelActive("Emergency auto-heal takeover");
            if(galateaWorkflows!=null&&galateaWorkflows.active()&&combatAutoHeal.shouldAcquireControl(gameContext.current().inSkyBlock()))
                galateaWorkflows.cancelActive("Emergency auto-heal takeover");
            if(!combatAutoHeal.ownsControl()&&combatAutoHeal.shouldAcquireControl(gameContext.current().inSkyBlock())
                    &&moduleManager.all().stream().noneMatch(module->module instanceof dev.veltraph.client.combat.ghost.GhostGrindModule ghost&&ghost.ownsHealControl()))
            {
                suspendOwnedMiningUse();
                farmServices.rotation().cancelRotation();
            }
            combatAutoHeal.tick(gameContext.current().inSkyBlock(),moduleManager.all().stream().anyMatch(module->
                    module instanceof dev.veltraph.client.combat.ghost.GhostGrindModule ghost&&ghost.ownsHealControl()));
        }
        boolean exclusiveResponse=healingBefore
                ||(combatAutoHeal!=null && combatAutoHeal.state()!=dev.veltraph.client.combat.heal.HealState.IDLE)
                ||(hydra!=null && hydra.hasActiveLocalReaction());
        if(exclusiveResponse) {
            suspendOwnedMiningUse();
            if(farmMaintenance!=null)farmMaintenance.cancelActive("Exclusive failsafe response");
            if(foragingMaintenance!=null)foragingMaintenance.cancelActive("Exclusive failsafe response");
            if(galateaWorkflows!=null)galateaWorkflows.cancelActive("Exclusive failsafe response");
            if(farmCycle!=null)farmCycle.cancel(true);
            if(autoRewarp!=null && autoRewarp.state()!=dev.veltraph.client.rewarp.AutoRewarpState.IDLE) {
                farmAutoRecovery.cancel();moduleManager.stopControllers();
            }
            if(combatAutoHeal==null||survivalStopSuppressed||!combatAutoHeal.hasVerifiedCustody()
                    ||dev.veltraph.client.service.mc.McOwnedRotation.authority(client)!=combatAutoHeal)
                farmServices.rotation().cancelRotation();
            farmServices.input().releaseAllMovement();farmServices.breaking().setBreaking(false);
        } else {
            if(foragingMaintenance!=null&&foragingMaintenance.active())
                foragingMaintenance.tick(()->false);
            if(galateaWorkflows!=null&&galateaWorkflows.active())galateaWorkflows.tick(guard::start);
            // Publish the selected registered input owner before its ordinary gameplay callback.
            // The existing post-callback update still observes ownership changes made by modules.
            updateScheduledInputLock();
            tickOriginalRootModules();
            bottleTransportQuiescent=registeredBottleAcquisition!=null&&registeredBottleAcquisition.isEnabled()&&registeredBottleAcquisition.pendingNativeWarp().isPresent();
            var ghost=moduleManager.get(dev.veltraph.client.combat.ghost.GhostGrindModule.ID).orElse(null);
            ghostTransportQuiescent=ghost instanceof dev.veltraph.client.combat.ghost.GhostGrindModule parent
                    &&parent.isEnabled()&&parent.pendingTransportAttempt().isPresent();
            try {
                tickOriginalRootRotation();
            } catch(RuntimeException error) {
                farmServices.rotation().cancelRotation();moduleManager.stopAll();
                farmServices.input().releaseAllMovement();farmServices.breaking().setBreaking(false);
                VeltraphLog.basic("Rotation","Stopped after rotation callback failure: "+error);
            }
        }
        // Early timing observes and queues before the SAME genuine Root intent/Guard transaction cut.
        tickEarlyLoadoutObservation(client,exclusiveResponse);
        if(!exclusiveResponse)dispatchOriginalMaintenanceIntent(client);
        // Observe freshly applied module/native changes before any deferred/helper admission can act.
        updateUnexpectedObservations(client,System.currentTimeMillis());
        if(unexpectedStopFrame.stopped())return;
        // Automatic rewarp runtime (Batch 2): evaluate triggers + advance the warp/wait/settle machine.
        if(!exclusiveResponse) {
            if(farmCycle!=null)tickOriginalFarmCycle();
            if((farmCycle==null||!farmCycle.active())&&!maintenanceOwnsFarm())tickAutoRewarp(client);
            else if(farmAutoRecovery!=null)farmAutoRecovery.resetProgress();
        }
        // A macro owns gameplay input (mouse-look + keyboard suppression) while any module runs.
        java.util.List<Module> running = moduleManager.enabledModules();
        updateScheduledInputLock();
        // Local Hydra / Anti-Staff watcher: proximity + teleport/world/limbo failsafes. Runs after the
        // module tick so `scripting` reflects this tick's run state; cheap when disabled. Local only.
        if (hydraAdapter != null) {
            pollRootHydraOnce();
        }
        if(hydra!=null&&(combatAutoHeal==null||!combatAutoHeal.ownsControl())
                &&moduleManager.enabledControllers().isEmpty()&&hydra.hasActiveGeneralResponse()) {
            ScriptingInputLock.acquire("failsafe_response");
            hydra.tickGeneralResponse();
        }
        // Source AUTOSELL has priority over ordinary helper candidates; terminal stop and current
        // survival responses have already been processed. The guard commits the exact farm lease.
        if(capturedGeorgeHelper!=null&&capturedGeorgeHelper.selected()&&!capturedGeorgeHelper.isEnabled()&&!exclusiveResponse
                &&(hydra==null||!hydra.hasActiveLocalReaction())&&(combatAutoHeal==null||!combatAutoHeal.ownsControl())
                &&capturedGeorgeHelper.activationReadiness().isEmpty())guard.start(capturedGeorgeHelper);
        if(farmAutoSell!=null&&(capturedGeorgeHelper==null||!capturedGeorgeHelper.selected())&&!farmAutoSell.isEnabled()&&!exclusiveResponse
                &&(hydra==null||!hydra.hasActiveLocalReaction())
                &&(combatAutoHeal==null||!combatAutoHeal.ownsControl())
                &&farmAutoSell.activationReadiness().isEmpty())
            guard.start(farmAutoSell);
        if(foragingMaintenance!=null&&!foragingMaintenance.active()&&!exclusiveResponse
                &&(hydra==null||!hydra.hasActiveLocalReaction())
                &&(combatAutoHeal==null||!combatAutoHeal.ownsControl()))
            foragingMaintenance.tick(()->guard.start(moduleManager.get("galatea_agatha_claim").orElseThrow()));
        if(galateaWorkflows!=null&&!galateaWorkflows.active()&&!exclusiveResponse
                &&(hydra==null||!hydra.hasActiveLocalReaction())
                &&(combatAutoHeal==null||!combatAutoHeal.ownsControl()))
            galateaWorkflows.tick(guard::start);
        if(trapMaintenance!=null&&!trapMaintenance.isEnabled()&&!exclusiveResponse
                &&(hydra==null||!hydra.hasActiveLocalReaction())
                &&(combatAutoHeal==null||!combatAutoHeal.ownsControl())) {
            var scope=jacobScope(client);
            var rows=actualTrapTabRows();
            if(trapMaintenance.readyForContest(jacobTracker,scope,clock.currentTick(),rows))
                trapMaintenance.startForContest(jacobTracker,scope,clock.currentTick(),rows,()->guard.start(trapMaintenance));
        }
        if(jacobReward!=null&&!exclusiveResponse
                &&(hydra==null||!hydra.hasActiveLocalReaction())
                &&(combatAutoHeal==null||!combatAutoHeal.ownsControl()))jacobReward.dispatch(()->guard.start(jacobReward));
        // Current higher-priority helper/contest candidates have now had their dispatch opportunity.
        if(scriptScheduler!=null)pollRootSchedulerOnce();
        // Source threshold parent uses the same exact registered deferred farm/helper reservation.
        // Higher-priority survival, selling, contests and scheduled rest have already dispatched.
        if(gardenThresholdAdmission!=null){
            boolean gardenAdmissionBlocked=exclusiveResponse||scheduledRestActive()
                    ||(farmCycle!=null&&farmCycle.active())
                    ||(autoRewarp!=null&&autoRewarp.state()!=dev.veltraph.client.rewarp.AutoRewarpState.IDLE)
                    ||(savedAnchorRecovery!=null&&savedAnchorRecovery.isEnabled())
                    ||(foragingMaintenance!=null&&foragingMaintenance.active())||(galateaWorkflows!=null&&galateaWorkflows.active())
                    ||(combatAutoHeal!=null&&combatAutoHeal.ownsControl())
                    ||(hydra!=null&&(hydra.hasActiveLocalReaction()||hydra.hasActiveGeneralResponse()));
            gardenThresholdAdmission.tick(clock.currentTick(),gardenAdmissionBlocked,
                    client.player!=null&&client.level!=null&&client.getConnection()!=null?actualTrapTabRows().stream().map(dev.veltraph.client.game.LocationText::stripColorCodes).toList():java.util.List.of(),
                    client.level!=null?actualPestSidebarRows(client):java.util.List.of(),guard::start);
        }
        tickObservedPestAdmission(client,exclusiveResponse);
        if(farmMaintenance!=null)farmMaintenance.dispatchPrepared();
        updateScheduledInputLock();
        // Developer Mode dev-test harness: advance ONLY explicitly dev-queued Hydra look/movement reactions
        // (Dev buttons / `/vdebug hydra reaction`) while no macro is running, so they visibly exercise smooth
        // auto-angle + movement. This NEVER runs detection / auto-reply / reroute / proximity / staff-response
        // — that path stays macro-gated inside hydraAdapter.tick above (scripting=false → inert). Bounded: it
        // only ticks while a dev-queued reaction is actually active, and only with no macro (when a macro is
        // running the reaction is advanced by the normal macro-gated response path instead).
        if (hydra != null && developerMode != null && developerMode.enabled()
                && moduleManager.enabledModules().isEmpty()
                && hydra.hasActiveLocalReaction()) {
            hydra.tickResponder();
        }
        // Garden Visitors advisory watcher (Batch 4): read-only; only evaluates in the Garden. Cheap when
        // disabled. Local only — recommends accept/skip, never clicks/buys/serves.
        if (visitorAdvisor != null) {
            boolean inGarden = gameContext != null
                    && gameContext.current().isArea(dev.veltraph.client.game.GameArea.GARDEN);
            visitorAdvisor.tick(inGarden);
        }
        // VERBOSE: per-tick macro diagnostics while a macro is running (supplier -> built only if on).
        if (!running.isEmpty() && VeltraphLog.shouldLog(dev.veltraph.client.log.DebugLogMode.VERBOSE)) {
            for (Module m : running) {
                VeltraphLog.verbose("tick", () -> m.diagnostics());
            }
        }
        // Debounced write-through: flush pending config/rewarp changes at most every ~5s. set() only
        // marks dirty on an actual value change, so the per-tick mouse-sensitivity sync never churns.
        if (jsonConfig != null && jsonConfig.isDirty() && clock.currentTick() - lastConfigSaveTick >= 100L) {
            saveConfig();
            lastConfigSaveTick = clock.currentTick();
        }
    }

    /**
     * Drive the automatic rewarp runtime once. Gathers the pure per-tick facts (a farm running, the
     * config gates, the resolved area, ground/tool, and a no-position-change stuck timer) and advances
     * the state machine. The runtime itself is pure; this method is the only place the live MC reads are
     * collected (through the existing services), so the decision logic stays unit-testable.
     */
    private static void tickAutoRewarp(Minecraft client) {
        if(farmAutoRecovery!=null)farmAutoRecovery.tick();
    }
    /** While Root holds an issued warp, only its configured exact denial may cancel the parent. */
    private static boolean quiesceSvenWarpReceipt(Module module,String line,Minecraft client){
        if(!svenWarpLifecycle.owns(module))return false;
        if(module instanceof dev.veltraph.client.combat.sven.SvenParentModule parent
                &&client.getConnection()==svenWarpLifecycle.permit().connection()
                &&svenWarpLifecycle.denial(parent,parent.pendingNativeWarp().orElse(null),
                config.get(dev.veltraph.client.combat.sven.SvenConfig.SVEN_TRAVEL_DENIED_MESSAGE),line,clock.nowMillis())){
            svenWarpLifecycle.clear();moduleManager.disable(parent.id());releaseNativeInputs(client);
            VeltraphLog.basic("SvenWarp","Stopped exact configured source fast-travel denial during issued handoff");
        }
        return true;
    }

    private static String bottleSettings(){
        var values=new java.util.TreeMap<String,String>();
        try{for(var holder:java.util.List.of(dev.veltraph.client.config.BottleAcquisitionConfig.class,dev.veltraph.client.config.TravelConfig.class,dev.veltraph.client.config.RotationConfig.class))
            for(var field:holder.getFields())if(field.get(null) instanceof dev.veltraph.client.config.ConfigKey<?> key)values.put(key.name(),String.valueOf(config.get(key)));}
        catch(ReflectiveOperationException error){throw new IllegalStateException("Bottle settings signature unavailable",error);}
        return values.toString();
    }
    private static void clearBottleWarp(){bottleWarpTravel=null;bottleWarpSettings=null;bottleWarpGear=java.util.Map.of();bottleTransportQuiescent=false;}
    private static boolean soleBottleOwner(dev.veltraph.client.inventory.bottle.BottleAcquisitionModule parent){return parent!=null&&moduleManager.get(parent.id()).orElse(null)==parent&&parent.isEnabled()
            &&moduleManager.enabledControllers().size()==1&&moduleManager.inputOwner().orElse(null)==parent;}
    /** Only this registered Bottle activation's issued permit may quiesce the ordinary world-stop path. */
    private static boolean tickOwnedBottleWarp(Minecraft client){
        var parent=registeredBottleAcquisition;var travel=parent==null?null:parent.pendingNativeWarp().orElse(null);
        if(travel==null){clearBottleWarp();return false;}
        if(bottleWarpTravel==null){
            if(!soleBottleOwner(parent)||travel.player()!=lastGeneralPlayer||travel.world()!=lastWorld||travel.connection()!=lastGeneralConnection
                    ||!(lastGeneralPlayer instanceof net.minecraft.client.player.LocalPlayer old)||!old.getUUID().equals(travel.uuid()))return false;
            bottleWarpTravel=travel;bottleWarpSettings=bottleSettings();
            var gear=new java.util.EnumMap<net.minecraft.world.entity.EquipmentSlot,net.minecraft.world.item.ItemStack>(net.minecraft.world.entity.EquipmentSlot.class);
            for(var slot:java.util.List.of(net.minecraft.world.entity.EquipmentSlot.HEAD,net.minecraft.world.entity.EquipmentSlot.CHEST,net.minecraft.world.entity.EquipmentSlot.LEGS,net.minecraft.world.entity.EquipmentSlot.FEET,net.minecraft.world.entity.EquipmentSlot.OFFHAND))gear.put(slot,old.getItemBySlot(slot).copy());
            bottleWarpGear=java.util.Map.copyOf(gear);
        }
        bottleTransportQuiescent=true;pollRootKeysOnce();
        if(!soleBottleOwner(parent)||parent.pendingNativeWarp().orElse(null)!=travel){clearBottleWarp();return false;}
        releaseNativeInputs(client);if(farmServices!=null){farmServices.rotation().cancelRotation();farmServices.input().releaseAllMovement();farmServices.breaking().setBreaking(false);}
        if(client.player!=null&&client.level!=null&&client.player.isAlive()&&generalFailsafe!=null)pollRootGeneralSafetyOnce();
        var observed=gameContext.current();boolean known=client.player!=null&&client.level!=null&&client.player.isAlive()&&client.gui.screen()==null
                &&observed.onHypixel()&&observed.inSkyBlock()&&observed.area().isSkyBlock()&&observed.area()!=dev.veltraph.client.game.GameArea.UNKNOWN&&observed.area()!=dev.veltraph.client.game.GameArea.LIMBO;
        if(known&&hydraAdapter!=null)pollRootHydraOnce();
        if(!soleBottleOwner(parent)){clearBottleWarp();return true;}
        var player=client.player;var connection=client.getConnection();long now=clock.nowMillis();
        Object listenerTracker=connection instanceof dev.veltraph.client.mixin.ScheduledClientLevelLoadAccessor listener?listener.veltraph$levelLoadTracker():null;
        Object screenTracker=client.gui.screen() instanceof dev.veltraph.client.mixin.ScheduledLevelLoadingScreenAccessor loading?loading.veltraph$loadTracker():null;
        boolean loading=dev.veltraph.client.combat.sven.InstalledSvenWarpLifecycle.loadingScreen(travel.connection(),connection,client.gui.screen() instanceof net.minecraft.client.gui.screens.LevelLoadingScreen,listenerTracker,screenTracker);
        boolean closed=client.gui.screen()==null&&(player==null||player.containerMenu==player.inventoryMenu&&player.containerMenu.getCarried().isEmpty());
        boolean equipment=player==null||bottleWarpGear.entrySet().stream().allMatch(entry->net.minecraft.world.item.ItemStack.matches(entry.getValue(),player.getItemBySlot(entry.getKey())));
        boolean allowed=bottleWarpTravel==travel&&bottleWarpSettings.equals(bottleSettings())&&connection==travel.connection()
                &&(player==null||player.isAlive()&&player.getUUID().equals(travel.uuid()))&&now>=travel.issuedMillis()&&now<=travel.expiresMillis()
                &&(closed||loading)&&equipment&&!scheduledHigherPriorityOwned();
        if(allowed&&(scriptScheduler==null||scriptScheduler.retainBottleTimer(parent,travel))){
            if(scriptScheduler!=null)pollRootSchedulerOnce();
            if(!soleBottleOwner(parent)||parent.pendingNativeWarp().orElse(null)!=travel){clearBottleWarp();return true;}
            var admission=loading?dev.veltraph.client.inventory.bottle.BottleAcquisitionRuntime.Admission.WAIT:parent.issuedTravelAdmission(parent,travel);
            admission=dev.veltraph.client.rewarp.BottleRootAdmission.withParentFooting(admission,player!=null&&player.onGround(),
                    player==null?Double.NaN:Math.hypot(player.getDeltaMovement().x,player.getDeltaMovement().z));
            var result=admission==dev.veltraph.client.inventory.bottle.BottleAcquisitionRuntime.Admission.STOP
                    ?dev.veltraph.client.inventory.bottle.BottleAcquisitionRuntime.Progress.FAILED
                    :admission==dev.veltraph.client.inventory.bottle.BottleAcquisitionRuntime.Admission.WAIT
                    ?parent.tickIssuedLoading(parent,travel):parent.tickIssuedTravel(parent);
            if(result==dev.veltraph.client.inventory.bottle.BottleAcquisitionRuntime.Progress.WAITING)return true;
            if(result==dev.veltraph.client.inventory.bottle.BottleAcquisitionRuntime.Progress.COMPLETE
                    &&(scriptScheduler==null||scriptScheduler.commitBottleTimer(parent,travel))){
                var previous=new dev.veltraph.client.metrics.FinancialOutcomeBindings.Scope(travel.player(),travel.world(),travel.connection());
                var current=new dev.veltraph.client.metrics.FinancialOutcomeBindings.Scope(client.player,client.level,client.getConnection());
                if(generalFailsafe!=null)generalFailsafe.onWorldStop();if(hydraAdapter!=null)hydraAdapter.onWorldStop();skyBlockHealth.reset();if(combatAutoHeal!=null)combatAutoHeal.reset();
                lastGeneralPlayer=client.player;lastGeneralConnection=client.getConnection();lastWorld=client.level;
                try{if(localMetrics!=null){localMetrics.poll(now);if(localFinancial!=null)localFinancial.continueBottle(parent,travel,previous,current,new dev.veltraph.client.metrics.BottleFinancialContinuation(parent,travel,previous,current));}}catch(RuntimeException unavailable){/* Metrics cannot grant gameplay authority. */}
                clearBottleWarp();return true;
            }
        }
        boolean heal=soleBottleOwner(parent)&&issuedTravelHealEligible(client);clearBottleWarp();
        if(soleBottleOwner(parent)){if(scriptScheduler!=null)scriptScheduler.cancel("Issued Bottle handoff revoked");moduleManager.disable(parent.id());releaseNativeInputs(client);if(heal)survivalStopSuppressed=false;}
        return true;
    }
    private static void clearGhostWarp(){ghostWarpLifecycle.clear();ghostWarpSignature=null;ghostInitialActivation=null;ghostTransportQuiescent=false;ghostAwaitingFooting=false;}

    /** During issued travel, only the exact configured denial may reach this quiet parent. */
    private static boolean quiesceGhostWarpReceipt(Module module,String line,Minecraft client){
        if(!(module instanceof dev.veltraph.client.combat.ghost.GhostGrindModule parent))return false;
        var permit=parent.pendingNativeWarp().orElse(null);
        if(permit==null)return false;
        if(client.getConnection()==permit.connection()&&line!=null&&line.replaceAll("§.","").strip().equalsIgnoreCase(permit.profile().denial())){
            clearGhostWarp();if(scriptScheduler!=null)scriptScheduler.cancel("Issued Ghost source denial");
            moduleManager.disable(parent.id());releaseNativeInputs(client);
        }
        return true;
    }
    /** Exact Ghost command custody; no grind, buff, navigation or healer action during transit. */
    private static boolean tickOwnedGhostWarp(Minecraft client){
        var registered=moduleManager.get(dev.veltraph.client.combat.ghost.GhostGrindModule.ID).orElse(null);
        var parent=registered instanceof dev.veltraph.client.combat.ghost.GhostGrindModule ghost?ghost:null;
        var permit=parent==null?null:parent.pendingNativeWarp().orElse(null);
        var attempt=parent==null?null:parent.pendingTransportAttempt().orElse(null);
        if(!ghostWarpLifecycle.active()&&permit==null&&attempt!=null)return tickUnissuedGhostTransport(client,parent,attempt);
        if(!ghostWarpLifecycle.active()){
            ghostTransportQuiescent=false;
            if(!(lastGeneralPlayer instanceof net.minecraft.client.player.LocalPlayer oldPlayer)
                    ||!ghostWarpLifecycle.capture(parent,moduleManager.inputOwner().orElse(null),moduleManager.enabledControllers().size(),
                    parent!=null&&parent.isEnabled(),permit,lastGeneralPlayer,lastWorld,lastGeneralConnection,oldPlayer.getUUID(),
                    gameContext.current().area(),clock.nowMillis()))return false;
            ghostWarpSignature=parent.scheduledSignature();
        }
        ghostTransportQuiescent=true;
        pollRootKeysOnce();
        if(!ghostWarpLifecycle.active())return false; // A manual takeover uses ordinary dispatch and its own timer.
        releaseNativeInputs(client);
        if(farmServices!=null){farmServices.rotation().cancelRotation();farmServices.input().releaseAllMovement();farmServices.breaking().setBreaking(false);}
        if(client.player!=null&&client.level!=null&&client.player.isAlive()&&generalFailsafe!=null)pollRootGeneralSafetyOnce();
        var observed=gameContext.current();
        boolean known=client.player!=null&&client.level!=null&&client.player.isAlive()&&client.gui.screen()==null
                &&observed.onHypixel()&&observed.inSkyBlock()&&observed.area().isSkyBlock()
                &&observed.area()!=dev.veltraph.client.game.GameArea.UNKNOWN&&observed.area()!=dev.veltraph.client.game.GameArea.LIMBO;
        if(known&&hydraAdapter!=null)pollRootHydraOnce();
        if(!ghostWarpLifecycle.active())return true; // Native STOP owns this update; no retired lease commit.
        var decision=ghostWarpDecision(client,parent);
        permit=parent==null?null:parent.pendingNativeWarp().orElse(null);
        if(decision!=dev.veltraph.client.combat.ghost.InstalledGhostWarpLifecycle.Decision.STOP
                &&(scriptScheduler==null||scriptScheduler.retainGhostTimer(parent,ghostWarpLifecycle.permit(),permit,parent.pendingTransportAttempt().orElse(null)))){
            if(scriptScheduler!=null)pollRootSchedulerOnce();
            if(!ghostWarpLifecycle.active())return true; // Expiry/disable already revoked the exact owner.
            decision=ghostWarpDecision(client,parent); // TIMER/manual callbacks may have revoked the owner.
            if(decision==dev.veltraph.client.combat.ghost.InstalledGhostWarpLifecycle.Decision.WAIT){
                // A received in-radius body waiting for the parent's footing is already an arrival;
                // keep its permit until settled rather than issuing a due duplicate command.
                if(!ghostAwaitingFooting)parent.tickIssuedTransport(moduleManager.inputOwner().orElse(null));
                return true; // Newly issued attempts are evaluated with fresh time on the next Root update.
            }
            if(decision==dev.veltraph.client.combat.ghost.InstalledGhostWarpLifecycle.Decision.COMMIT
                    &&parent.commitNativeWarp(permit,moduleManager.inputOwner().orElse(null))
                    &&(scriptScheduler==null||scriptScheduler.commitGhostTimer(parent,permit))){
                if(generalFailsafe!=null)generalFailsafe.onWorldStop();
                if(hydraAdapter!=null)hydraAdapter.onWorldStop();
                skyBlockHealth.reset();if(combatAutoHeal!=null)combatAutoHeal.reset();
                lastGeneralPlayer=client.player;lastGeneralConnection=client.getConnection();lastWorld=client.level;
                clearGhostWarp();return true; // Exact owner continues on the next ordinary Root update.
            }
        }
        boolean heal=parent!=null&&parent.isEnabled()&&moduleManager.inputOwner().orElse(null)==parent
                &&permit==ghostWarpLifecycle.permit()&&issuedTravelHealEligible(client);
        clearGhostWarp();if(scriptScheduler!=null)scriptScheduler.cancel("Issued Ghost handoff revoked");
        moduleManager.stopAll();releaseNativeInputs(client);if(heal)survivalStopSuppressed=false;
        VeltraphLog.basic("GhostWarp","Stopped rejected or revoked issued native arrival");return true;
    }
    /** Only the registered George helper/token may wait for its issued native context transition. */
    private static boolean tickOwnedGeorgeWarp(Minecraft client){
        var helper=capturedGeorgeHelper;var permit=helper==null?null:helper.pendingGeorgeWarp().orElse(null);
        if(permit==null)return false;var token=farmMaintenance.tokenFor(helper);
        pollRootKeysOnce();
        if(!dev.veltraph.client.rewarp.GeorgeManualCustody.mayContinue(helper,token,permit,helper.isEnabled(),moduleManager.inputOwner().orElse(null),
                farmMaintenance.tokenFor(helper),helper.pendingGeorgeWarp().orElse(null)))return false;
        releaseNativeInputs(client);
        farmServices.rotation().cancelRotation();farmServices.input().releaseAllMovement();farmServices.breaking().setBreaking(false);
        if(client.player!=null&&client.level!=null&&client.player.isAlive()&&generalFailsafe!=null)pollRootGeneralSafetyOnce();
        var observed=gameContext.current();
        boolean known=client.player!=null&&client.level!=null&&client.player.isAlive()&&client.gui.screen()==null
                &&observed.onHypixel()&&observed.inSkyBlock()&&observed.area().isSkyBlock()
                &&observed.area()!=dev.veltraph.client.game.GameArea.UNKNOWN&&observed.area()!=dev.veltraph.client.game.GameArea.LIMBO;
        if(known&&hydraAdapter!=null)pollRootHydraOnce();
        Object listenerTracker=client.getConnection() instanceof dev.veltraph.client.mixin.ScheduledClientLevelLoadAccessor a?a.veltraph$levelLoadTracker():null;
        Object screenTracker=client.gui.screen() instanceof dev.veltraph.client.mixin.ScheduledLevelLoadingScreenAccessor a?a.veltraph$loadTracker():null;
        boolean loading=dev.veltraph.client.combat.sven.InstalledSvenWarpLifecycle.loadingScreen(permit.pending().connection(),client.getConnection(),
                client.gui.screen() instanceof net.minecraft.client.gui.screens.LevelLoadingScreen,listenerTracker,screenTracker);
        boolean closed=client.gui.screen()==null&&(client.player==null||client.player.containerMenu==client.player.inventoryMenu&&client.player.containerMenu.getCarried().isEmpty());
        var capture=farmMaintenance.capturedGeorge(helper,token).orElse(null);
        boolean emergency=known&&combatAutoHeal!=null&&!survivalStopSuppressed&&combatAutoHeal.shouldAcquireControl(true);
        if(!helper.isEnabled()||!closed&&!loading||emergency||!farmMaintenance.validGeorgeTransit(helper,token,permit)
                ||scriptScheduler==null||!scriptScheduler.retainGeorgeTimer(capture,helper,permit)){
            boolean heal=emergency&&issuedTravelHealEligible(client);moduleManager.stopAll();releaseNativeInputs(client);if(heal)survivalStopSuppressed=false;return true;
        }
        helper.advanceIssuedGeorgeWarp(); // Cache only actual packet evidence before General unexpected-observation polling.
        pollRootSchedulerOnce(); // Same farm activation/timer instance, finite token; no rearm.
        if(!helper.isEnabled()||!farmMaintenance.validGeorgeTransit(helper,token,permit))return true;
        helper.onTick(); // Exact pending source parent branch; farm and ordinary NPC engine are not driven here.
        updateScheduledInputLock();return true;
    }
    /** No issued permit means no native-world admission: failed dispatch only retries in original context. */
    private static boolean tickUnissuedGhostTransport(Minecraft client,dev.veltraph.client.combat.ghost.GhostGrindModule parent,
            dev.veltraph.client.combat.ghost.GhostTransportLoop.Attempt attempt){
        if(ghostInitialActivation==null){ghostInitialActivation=attempt.activation();ghostWarpSignature=parent.scheduledSignature();}
        ghostTransportQuiescent=true;
        pollRootKeysOnce();
        if(!parent.isEnabled()||moduleManager.inputOwner().orElse(null)!=parent){clearGhostWarp();return false;}
        releaseNativeInputs(client);
        if(farmServices!=null){farmServices.rotation().cancelRotation();farmServices.input().releaseAllMovement();farmServices.breaking().setBreaking(false);}
        if(client.player!=null&&client.level!=null&&client.player.isAlive()&&generalFailsafe!=null)pollRootGeneralSafetyOnce();
        var observed=gameContext.current();
        if(client.player!=null&&client.level!=null&&client.player.isAlive()&&client.gui.screen()==null
                &&observed.onHypixel()&&observed.inSkyBlock()&&observed.area()!=dev.veltraph.client.game.GameArea.UNKNOWN
                &&observed.area()!=dev.veltraph.client.game.GameArea.LIMBO&&hydraAdapter!=null)pollRootHydraOnce();
        if(scriptScheduler!=null)pollRootSchedulerOnce(); // Original P/W/C only; no warp exception exists yet.
        var current=parent.pendingTransportAttempt().orElse(null);
        boolean original=client.player==lastGeneralPlayer&&client.level==lastWorld&&client.getConnection()==lastGeneralConnection
                &&client.player!=null&&client.player.isAlive()&&client.player.level()==client.level&&client.gui.screen()==null
                &&client.player.containerMenu==client.player.inventoryMenu&&client.player.containerMenu.getCarried().isEmpty();
        if(!parent.isEnabled()||moduleManager.enabledControllers().size()!=1||moduleManager.inputOwner().orElse(null)!=parent
                ||current==null||current.activation()!=ghostInitialActivation||!original||scheduledHigherPriorityOwned()
                ||!java.util.Objects.equals(ghostWarpSignature,parent.scheduledSignature())){
            clearGhostWarp();moduleManager.stopAll();releaseNativeInputs(client);return true;
        }
        parent.tickIssuedTransport(parent);return true;
    }
    private static dev.veltraph.client.combat.ghost.InstalledGhostWarpLifecycle.Decision ghostWarpDecision(Minecraft client,dev.veltraph.client.combat.ghost.GhostGrindModule parent){
        if(!ghostWarpLifecycle.active())return dev.veltraph.client.combat.ghost.InstalledGhostWarpLifecycle.Decision.STOP;
        var player=client.player;var world=client.level;var connection=client.getConnection();var permit=ghostWarpLifecycle.permit();
        boolean metadata=parent!=null&&java.util.Objects.equals(ghostWarpSignature,parent.scheduledSignature());
        try{metadata&=permit.profile().equals(dev.veltraph.client.combat.ghost.GhostPreflightProfile.parse(config.get(dev.veltraph.client.config.GhostGrindConfig.GHOST_SOURCE_PREFLIGHT_PROFILE)));}
        catch(RuntimeException invalid){metadata=false;}
        Object listenerTracker=connection instanceof dev.veltraph.client.mixin.ScheduledClientLevelLoadAccessor listener?listener.veltraph$levelLoadTracker():null;
        Object screenTracker=client.gui.screen() instanceof dev.veltraph.client.mixin.ScheduledLevelLoadingScreenAccessor loading?loading.veltraph$loadTracker():null;
        boolean loading=dev.veltraph.client.combat.sven.InstalledSvenWarpLifecycle.loadingScreen(permit.connection(),connection,
                client.gui.screen() instanceof net.minecraft.client.gui.screens.LevelLoadingScreen,listenerTracker,screenTracker);
        boolean closed=client.gui.screen()==null&&(player==null||player.containerMenu==player.inventoryMenu&&player.containerMenu.getCarried().isEmpty());
        boolean loaded=player!=null&&world!=null&&world.getChunkSource().hasChunk(player.blockPosition().getX()>>4,player.blockPosition().getZ()>>4)
                &&world.getChunkSource().hasChunk(permit.profile().anchor().toBlockPos().x()>>4,permit.profile().anchor().toBlockPos().z()>>4);
        var receipt=dev.veltraph.client.rewarp.mc.NativeTeleportSignal.read(client);
        var observation=new dev.veltraph.client.combat.ghost.InstalledGhostWarpLifecycle.Observation(player,world,connection,
                player==null?null:player.getUUID(),player!=null&&player.isAlive(),player!=null&&player.level()==world,loaded,closed,loading,
                gameContext.current().area(),world==null?null:world.dimension().identifier().toString(),
                player==null?null:new dev.veltraph.client.world.Vec3(player.getX(),player.getY(),player.getZ()),receipt.serial(),receipt.position(),
                !metadata||scheduledHigherPriorityOwned(),clock.nowMillis());
        var decision=ghostWarpLifecycle.evaluate(parent,moduleManager.inputOwner().orElse(null),moduleManager.enabledControllers().size(),
                parent!=null&&parent.isEnabled(),parent==null?null:parent.pendingNativeWarp().orElse(null),parent==null?null:parent.pendingTransportAttempt().orElse(null),observation);
        var admitted=dev.veltraph.client.rewarp.GhostRootAdmission.withParentFooting(decision,player!=null&&player.onGround(),
                player==null?Double.NaN:Math.hypot(player.getDeltaMovement().x,player.getDeltaMovement().z));
        ghostAwaitingFooting=decision==dev.veltraph.client.combat.ghost.InstalledGhostWarpLifecycle.Decision.COMMIT
                &&admitted==dev.veltraph.client.combat.ghost.InstalledGhostWarpLifecycle.Decision.WAIT;
        return admitted;
    }

    /** Quiesce only this issued parent handoff; normal lifecycle stops remain authoritative otherwise. */
    private static boolean tickOwnedSvenWarp(Minecraft client){
        var registered=moduleManager.get(dev.veltraph.client.combat.sven.SvenParentModule.ID).orElse(null);
        var parent=registered instanceof dev.veltraph.client.combat.sven.SvenParentModule sven?sven:null;
        var permit=parent==null?null:parent.pendingNativeWarp().orElse(null);
        if(!svenWarpLifecycle.active()){
            if(!(lastGeneralPlayer instanceof net.minecraft.client.player.LocalPlayer oldPlayer)
                    ||!svenWarpLifecycle.capture(parent,moduleManager.inputOwner().orElse(null),
                    moduleManager.enabledControllers().size(),parent!=null&&parent.isEnabled(),permit,
                    lastGeneralPlayer,lastWorld,lastGeneralConnection,oldPlayer.getUUID(),gameContext.current().area(),clock.nowMillis()))return false;
        }
        // Manual stop and source terminal stop timer retain first refusal; no ordinary engine tick.
        pollRootKeysOnce();
        if(scriptScheduler!=null)pollRootSchedulerOnce();
        releaseNativeInputs(client);
        if(farmServices!=null){farmServices.rotation().cancelRotation();farmServices.input().releaseAllMovement();farmServices.breaking().setBreaking(false);}
        if(client.player!=null&&client.level!=null&&client.player.isAlive()&&generalFailsafe!=null)pollRootGeneralSafetyOnce();
        permit=parent==null?null:parent.pendingNativeWarp().orElse(null);
        var player=client.player;var world=client.level;var connection=client.getConnection();
        var area=gameContext.current().area();
        boolean closed=client.gui.screen()==null&&(player==null||player.containerMenu==player.inventoryMenu&&player.containerMenu.getCarried().isEmpty());
        Object listenerTracker=connection instanceof dev.veltraph.client.mixin.ScheduledClientLevelLoadAccessor listener?listener.veltraph$levelLoadTracker():null;
        Object screenTracker=client.gui.screen() instanceof dev.veltraph.client.mixin.ScheduledLevelLoadingScreenAccessor loading?loading.veltraph$loadTracker():null;
        boolean loading=dev.veltraph.client.combat.sven.InstalledSvenWarpLifecycle.loadingScreen(
                svenWarpLifecycle.permit().connection(),connection,client.gui.screen() instanceof net.minecraft.client.gui.screens.LevelLoadingScreen,listenerTracker,screenTracker);
        var feet=player==null?null:new dev.veltraph.client.world.Vec3(player.getX(),player.getY(),player.getZ());
        var spawn=dev.veltraph.client.combat.sven.SvenCaveProfile.PARK_SPAWN.toBlockPos();
        boolean loaded=player!=null&&world!=null&&world.getChunkSource().hasChunk(player.blockPosition().getX()>>4,player.blockPosition().getZ()>>4)
                &&world.getChunkSource().hasChunk(spawn.x()>>4,spawn.z()>>4);
        var observation=new dev.veltraph.client.combat.sven.InstalledSvenWarpLifecycle.Observation(player,world,connection,
                player==null?null:player.getUUID(),player!=null&&player.isAlive(),player!=null&&player.level()==world,
                loaded,closed,loading,area,feet,scheduledHigherPriorityOwned(),clock.nowMillis());
        var decision=svenWarpLifecycle.evaluate(parent,moduleManager.inputOwner().orElse(null),moduleManager.enabledControllers().size(),
                parent!=null&&parent.isEnabled(),permit,observation);
        if(decision==dev.veltraph.client.combat.sven.InstalledSvenWarpLifecycle.Decision.WAIT)return true;
        if(decision==dev.veltraph.client.combat.sven.InstalledSvenWarpLifecycle.Decision.COMMIT
                &&parent.commitNativeWarp(svenWarpLifecycle.permit(),moduleManager.inputOwner().orElse(null))){
            // Reset world-local native observations only after the exact module accepts the arrival.
            if(generalFailsafe!=null)generalFailsafe.onWorldStop();
            if(hydraAdapter!=null)hydraAdapter.onWorldStop();
            skyBlockHealth.reset();
            if(combatAutoHeal!=null)combatAutoHeal.reset();
            lastGeneralPlayer=player;lastGeneralConnection=connection;lastWorld=world;
            svenWarpLifecycle.clear();return true; // Resume next Root tick; the source timer already ran once above.
        }
        boolean healHandoff=parent!=null&&parent.isEnabled()&&moduleManager.inputOwner().orElse(null)==parent
                &&permit==svenWarpLifecycle.permit()&&issuedTravelHealEligible(client);
        svenWarpLifecycle.clear();moduleManager.stopAll();releaseNativeInputs(client);
        if(healHandoff)survivalStopSuppressed=false;
        VeltraphLog.basic("SvenWarp","Stopped rejected or revoked issued same-connection arrival");
        return true;
    }

    /** One actual Root frame can reach both a refused return branch and ordinary hard-stop processing.
     * Retain only dispatch history, never a world-transition exemption or native action receipt. */
    private static RootGameplayReturnFrame currentRootGameplayFrame(){
        var frame=activeGameplayReturnFrame;
        return frame!=null&&frame.client==Minecraft.getInstance()&&frame.thread==Thread.currentThread()&&frame.authenticNativeCurrent()?frame:null;
    }
    private static void pollRootKeysOnce(){
        var frame=currentRootGameplayFrame();if(frame!=null){if(frame.keysPolled)return;frame.keysPolled=true;}
        VeltraphKeybinds.poll(guard);
    }
    private static void pollRootGeneralSafetyOnce(){
        var frame=currentRootGameplayFrame();if(frame!=null){if(frame.generalPolled)return;frame.generalPolled=true;}
        generalFailsafe.tick();
    }
    private static void pollRootHydraOnce(){
        var frame=currentRootGameplayFrame();if(frame!=null){if(frame.hydraPolled)return;frame.hydraPolled=true;}
        hydraAdapter.tick(System.currentTimeMillis());
    }
    private static void pollRootSchedulerOnce(){
        var frame=currentRootGameplayFrame();if(frame!=null){if(frame.schedulerPolled)return;frame.schedulerPolled=true;}
        scriptScheduler.tick();
    }

    /** The original captured timer advances once under this exact private return dispatch. */
    private static boolean pollIssuedFarmTimerOnce(dev.veltraph.client.rewarp.mc.McSuspendedFarmReturnCoordinator exact,
            RootFarmReturnTick tick){
        if(scriptScheduler==null)return false;
        var frame=currentRootGameplayFrame();
        if(frame==null||frame.returnedTick!=tick||exact!=suspendedFarmReturn||!tick.current(exact))return false;
        if(frame.schedulerPolled)return false;
        frame.schedulerPolled=true;
        var call=exact.currentTimerCall(tick);var retained=exact.timerRetention();
        return call!=null&&retained!=null&&scriptScheduler.tickIssuedFarmTimer(retained,call);
    }

    /** Only a current private source observation may take this Root dispatch. Active requests do not exempt worlds. */
    private static boolean tickOwnedSuspendedFarmReturn(Minecraft client) {
        var coordinator=suspendedFarmReturn;
        if(coordinator==null||!coordinator.active()&&!coordinator.hasPendingUserIntent()&&!coordinator.hasTerminalRootObservation())return false; // Dispatch eligibility only; never an exemption.
        var frame=currentRootGameplayFrame();
        if(frame==null||frame.client!=client||!frame.authenticNativeCurrent())return false;
        if(activeFarmReturnTick!=null)throw new IllegalStateException("Root farm return dispatch reentry");
        var actual=new RootFarmReturnTick(client,coordinator);activeFarmReturnTick=actual;
        frame.returnedTick=actual;
        try {
            pollRootKeysOnce();
            // Real manual/safety actions run first and can revoke the old source before any return effect.
            if(client.player!=null&&client.level!=null&&client.player.isAlive()&&generalFailsafe!=null)pollRootGeneralSafetyOnce();
            var known=gameContext.current();
            if(client.player!=null&&client.level!=null&&client.player.isAlive()&&client.gui.screen()==null
                &&known.onHypixel()&&known.inSkyBlock()&&known.area().isSkyBlock()
                &&known.area()!=dev.veltraph.client.game.GameArea.UNKNOWN&&known.area()!=dev.veltraph.client.game.GameArea.LIMBO
                &&hydraAdapter!=null)pollRootHydraOnce();
            var driverInput=coordinator.driverInputForRoot(actual);
            if(coordinator.active()&&coordinator.driver().isEnabled()&&driverInput==null){
                coordinator.closeFromRoot(farmReturnCloseCauseFor(actual,coordinator),"Original driver input instruction unavailable");return false;
            }
            if(driverInput!=null&&driverInput.loadedScene()&&!publishIssuedDriverOriginalInput(driverInput)){
                coordinator.closeFromRoot(farmReturnCloseCauseFor(actual,coordinator),"Original driver input publication refused");return false;
            }
            // An absent/unjoined native scene receives metadata observation only. No generic setter
            // can refresh the original publication or rebind control/native custody there.
            RootFarmReturnAdmission admission=null;
            if(coordinator.hasPendingUserIntent()){
                var selected=moduleManager.inputOwner().orElse(null);
                if(selected instanceof dev.veltraph.client.farming.module.FarmModule farm){
                    var original=originalRootPublicationFor(farm);
                    if(original!=null)admission=new RootFarmReturnAdmission(actual,coordinator,farm,original);
                }
            }
            activeFarmReturnAdmission=admission;
            try{coordinator.tick(actual,admission);}
            finally{if(activeFarmReturnAdmission==admission)activeFarmReturnAdmission=null;}
            var observation=coordinator.observationForRoot(actual);
            if(observation==null||!observation.current(actual)){
                var terminal=coordinator.handledTerminalObservationForRoot(actual);
                if(terminal!=null&&coordinator.handledTerminalFrame(terminal,actual)){
                    frame.terminalGameplayRetired=true;
                    coordinator.stopOriginalTerminalFromRoot(terminal,farmReturnCloseCauseFor(actual,coordinator),"Original terminal return proof refused");
                }
                return false;
            }
            if(!pollIssuedFarmTimerOnce(coordinator,actual)){
                var terminal=coordinator.handledTerminalObservationForRoot(actual);
                if(terminal!=null&&coordinator.handledTerminalFrame(terminal,actual)){
                    frame.terminalGameplayRetired=true;
                    coordinator.stopOriginalTerminalFromRoot(terminal,farmReturnCloseCauseFor(actual,coordinator),"Original terminal TIMER proof refused");
                }else coordinator.closeFromRoot(farmReturnCloseCauseFor(actual,coordinator),"Original issued-return TIMER proof refused");
                return false;
            }
            if(!observation.current(actual)){
                var terminal=coordinator.handledTerminalObservationForRoot(actual);
                if(terminal!=null&&coordinator.handledTerminalFrame(terminal,actual)){
                    frame.terminalGameplayRetired=true;
                    coordinator.stopOriginalTerminalFromRoot(terminal,farmReturnCloseCauseFor(actual,coordinator),"Original terminal handoff proof refused");
                }
                return false;
            }
            if(!observation.current(actual)){
                var terminal=coordinator.handledTerminalObservationForRoot(actual);
                if(terminal!=null&&coordinator.handledTerminalFrame(terminal,actual)){
                    frame.terminalGameplayRetired=true;
                    coordinator.stopOriginalTerminalFromRoot(terminal,farmReturnCloseCauseFor(actual,coordinator),"Original terminal handoff proof refused");
                }
                return false;
            }
            // Update only the exact proven native scene; unknown/manual transitions retain the existing hard stop below.
            lastGeneralPlayer=client.player;lastGeneralConnection=client.getConnection();lastWorld=client.level;
            flushConfigIfDirty();return true;
        } finally { /* The actual Root body, including its outer return, closes this token. */ }
    }
    private static boolean permitsSuspendedReturnConflict(Module running,Module starting) {
        var coordinator=suspendedFarmReturn;
        return coordinator!=null&&starting==coordinator.driver()
                &&running instanceof dev.veltraph.client.farming.module.FarmModule farm
                &&coordinator.isPausedFarm(farm)&&coordinator.permitsDriverActivation(coordinator.driver());
    }

    /** Ordinary safety updates never run a paused engine or interpret its issued departure as Limbo. */
    private static void tickOwnedScheduledRest(Minecraft client){
        pollRootKeysOnce(); // Manual stop/activation retains normal cancellation authority.
        if(scriptScheduler==null)return;
        boolean changed=client.player!=lastGeneralPlayer||client.level!=lastWorld||client.getConnection()!=lastGeneralConnection;
        if(changed){
            if(generalFailsafe!=null)generalFailsafe.onWorldStop();
            if(hydra!=null)hydra.resetResponses();
            if(hydraAdapter!=null)hydraAdapter.onWorldStop();
            skyBlockHealth.reset();
        }
        lastGeneralPlayer=client.player;lastGeneralConnection=client.getConnection();lastWorld=client.level;
        var observed=gameContext.current();
        boolean nativeKnown=client.player!=null&&client.level!=null&&client.player.isAlive()&&client.gui.screen()==null
                &&observed.onHypixel()&&observed.inSkyBlock()&&observed.area().isSkyBlock()
                &&observed.area()!=dev.veltraph.client.game.GameArea.UNKNOWN&&observed.area()!=dev.veltraph.client.game.GameArea.LIMBO;
        // Void/proximity STOP uses actual native entities even while location evidence is pending.
        if(client.player!=null&&client.level!=null&&client.player.isAlive()&&generalFailsafe!=null)pollRootGeneralSafetyOnce();
        if(nativeKnown&&hydraAdapter!=null)pollRootHydraOnce();
        scriptScheduler.validateBeforeUpdates(); // Critical native safety observers can revoke ownership.
        // The return driver keeps its controls between valid ticks; offline/delayed rest owns none.
        if(!scriptScheduler.ownsInput())releaseNativeInputs(client);
        pollRootSchedulerOnce(); // Includes TIMER exactly once, transport deadline, arrival and handoff.
        if(scriptScheduler.ownsInput()){
            try{farmServices.rotation().tickRotation();}
            catch(RuntimeException failure){scriptScheduler.cancel("Saved return rotation failed");}
        }
        updateScheduledInputLock();
        flushConfigIfDirty();
    }
    private static ModuleManager.ManualStopRegistration rootManualStopRegistration;
    /** Private original-source frame; no role/provider queries occur before original stop callbacks. */
    private static final class RootLogicalStopFrame {
        final Minecraft client;final ModuleManager root;final ModuleManager.ManualStopStart start;final Module oldOwner;
        final ScriptingInputLock.ControlSnapshot control;final RootRotationAuthorityProvider provider;final RootInputReleaseRegistration registration;
        final ConfigService store;final dev.veltraph.client.config.MapConfigService.StoredStateSnapshot settings;
        final Object player,world,connection,nativeConnection,options,gameMode;final RootOriginalMenuRequest menu;
        RootOriginalNativeTermination termination;RootOriginalGuiStopDeclaration guiStop;
        RootLogicalStopFrame(Minecraft client,ModuleManager.ManualStopStart start,Module oldOwner,ScriptingInputLock.ControlSnapshot control){
            this.client=client;this.start=start;this.oldOwner=oldOwner;this.control=control;root=moduleManager;provider=rootRotationProvider;registration=rootInputReleaseRegistration;store=config;menu=originalMenuRequest;
            settings=store instanceof dev.veltraph.client.config.MapConfigService m?m.storedStateSnapshot():store instanceof dev.veltraph.client.config.JsonConfigService j?j.storedStateSnapshot():null;
            player=client.player;world=client.level;connection=client.getConnection();nativeConnection=client.getConnection().getConnection();options=client.options;gameMode=client.gameMode;
        }
        boolean sceneCurrent(){return client.isSameThread()&&client==Minecraft.getInstance()&&root==moduleManager&&provider==rootRotationProvider&&provider.registeredCurrent()
            &&registration==rootInputReleaseRegistration&&registration.current(registration.actualInput(),client)&&rootManualStopRegistration!=null&&rootManualStopRegistration.current()
            &&store==config&&settings!=null&&settings.current()&&control.current()&&client.player==player&&client.level==world&&client.getConnection()==connection
            &&client.options==options&&client.gameMode==gameMode&&nativeConnection==client.getConnection().getConnection()&&((net.minecraft.network.Connection)nativeConnection).isConnected();}
    }
    private record RootOriginalDisableRollbackCapture(Object original,FarmMaintenanceCoordinator source,
            FarmMaintenanceCoordinator.OriginalRollbackOwnStop rollback){}
    private static Object captureOriginalRootDisableWithRollback(ModuleManager.ActualDisableStart actual){
        var source=farmMaintenance;var rollback=source==null?null:source.captureOriginalRollbackOwnStop(actual);
        var original=captureOriginalRootDisable(actual);
        return rollback==null?original:new RootOriginalDisableRollbackCapture(original,source,rollback);
    }
    private static Object originalDisableCaptured(Object captured){return captured instanceof RootOriginalDisableRollbackCapture original?original.original():captured;}
    private static void completeOriginalRootDisableWithRollback(ModuleManager.OwnDisableReceipt actual,Object captured){
        if(captured instanceof RootOriginalDisableRollbackCapture original){
            try{if(original.source()==farmMaintenance)original.source().retireOriginalRollbackOwnStop(actual,original.rollback());}
            finally{completeOriginalRootDisable(actual,original.original());}
        }else completeOriginalRootDisable(actual,captured);
    }
    private record RootOriginalManualStopCapture(Object logical,FarmMaintenanceCoordinator source,
            FarmMaintenanceCoordinator.OriginalRollbackManualStop rollback){}
    private static Object captureOriginalRootManualStop(Minecraft client,ModuleManager.ManualStopStart actual){
        var source=farmMaintenance;var rollback=source==null?null:source.captureOriginalRollbackManualStop(actual);
        return new RootOriginalManualStopCapture(captureRootLogicalStop(client,actual),source,rollback);
    }
    private static void finishOriginalRootManualStop(ModuleManager.ManualStopReceipt actual,Object captured){
        if(!(captured instanceof RootOriginalManualStopCapture original))return;
        try{if(original.source()!=null&&original.source()==farmMaintenance)original.source().retireOriginalRollbackManualStop(actual,original.rollback());}
        finally{retireRootLogicalStop(actual,original.logical());}
    }
    private static Object captureRootLogicalStop(Minecraft client,ModuleManager.ManualStopStart actual){
        if(!client.isSameThread()||actual.registryIdentity()!=moduleManager||!actual.startingCurrent()||client.player==null||client.level==null||client.getConnection()==null)return null;
        var control=ScriptingInputLock.controlSnapshot();if(control==null||!control.active()||control.ownerModuleId()==null)return null;
        var oldOwner=moduleManager.get(control.ownerModuleId()).orElse(null);if(oldOwner==null||!actual.enabledFromStart(oldOwner))return null;
        var frame=new RootLogicalStopFrame(client,actual,oldOwner,control);
        if(!frame.sceneCurrent()||!actual.startingCurrent())return null;
        try{
            var publication=originalInputPublication;
            if(publication!=null&&publication.publishedOwner==oldOwner&&publication.nativeHistoryCurrent()&&publication.input.control().sameHistory(control)){
                if(client.gui.screen() instanceof dev.veltraph.client.ui.AutomationsScreen originalScreen){
                    var installed=originalScreen.originalRootMenuForRoot();
                    if(installed!=null&&installed.currentFor(originalScreen))frame.guiStop=new RootOriginalGuiStopDeclaration(frame,installed,publication);
                }
                var termination=new RootOriginalNativeTermination(frame,publication);frame.termination=termination;originalNativeTermination=termination;
                termination.nativeTicket=dev.veltraph.client.service.mc.RootNativeHistory.beginTerminal(termination,client);
                if(termination.nativeTicket==null)termination.revoked=true;
            }
        }catch(RuntimeException|Error unavailable){if(frame.termination!=null)frame.termination.revoked=true;}
        return frame;
    }
    /** Opaque private canonical manual cause. It authorizes only a logical flag retirement, never native release. */
    public static final class RootLogicalInputRetirementCause {
        private final RootLogicalStopFrame frame;private final ModuleManager.ManualStopReceipt stopped;private final RootNoOwnerObservation noOwner;
        private final dev.veltraph.client.service.mc.McOwnedRotation.LogicalIdleObservation camera;
        private final net.minecraft.client.KeyMapping[] keys;private final dev.veltraph.client.service.mc.NormalIdleKeyCarrier[] carriers;
        private final Object[] histories;private final String[] bindings;private boolean prepared;
        private RootLogicalInputRetirementCause(RootLogicalStopFrame frame,ModuleManager.ManualStopReceipt stopped,RootNoOwnerObservation noOwner){
            this.frame=frame;this.stopped=stopped;this.noOwner=noOwner;camera=frame.registration.installation.observeLogicalIdle();var o=frame.client.options;
            keys=new net.minecraft.client.KeyMapping[]{o.keyUp,o.keyDown,o.keyLeft,o.keyRight,o.keyJump,o.keyShift,o.keySprint,o.keyAttack,o.keyUse};
            carriers=new dev.veltraph.client.service.mc.NormalIdleKeyCarrier[keys.length];histories=new Object[keys.length];bindings=new String[keys.length];
            for(int i=0;i<keys.length;i++){var key=keys[i];if((key.getClass()!=net.minecraft.client.KeyMapping.class&&key.getClass()!=net.minecraft.client.ToggleKeyMapping.class)||!(key instanceof dev.veltraph.client.service.mc.NormalIdleKeyCarrier carrier))continue;
                carriers[i]=carrier;histories[i]=carrier.normalIdleKeySnapshot();bindings[i]=key.saveString();}
        }
        public boolean prepareCurrent(ScriptingInputLock.ControlSnapshot original){if(prepared)return false;prepared=true;
            return original==frame.control&&noOwner!=null&&noOwner.current()&&finalCurrent(original);}
        /** Final stored-source/native leaf: no provider, owner, release, cancel, or publication callback. */
        public boolean finalCurrent(ScriptingInputLock.ControlSnapshot original){
            if(!prepared||original!=frame.control||!frame.sceneCurrent()||camera==null||!camera.current()||noOwner==null||!noOwner.manualCurrent()||!stopped.matches(frame.start)||!stopped.disabledOwnerFromStart(frame.oldOwner))return false;
            var o=frame.client.options;var current=new net.minecraft.client.KeyMapping[]{o.keyUp,o.keyDown,o.keyLeft,o.keyRight,o.keyJump,o.keyShift,o.keySprint,o.keyAttack,o.keyUse};
            for(int i=0;i<keys.length;i++)if(current[i]!=keys[i]||carriers[i]==null||histories[i]==null||keys[i].isDown()||!bindings[i].equals(keys[i].saveString())||!carriers[i].normalIdleKeyCurrent(histories[i]))return false;
            return frame.sceneCurrent()&&camera.current()&&noOwner.manualCurrent()&&stopped.matches(frame.start)&&stopped.disabledOwnerFromStart(frame.oldOwner)&&original.current();
        }
    }
    /** Real installed menu plus actual private Root manual-stop entry, captured before any stop callback. */
    public static final class RootOriginalGuiStopDeclaration {
        private final RootLogicalStopFrame frame;private final RootOriginalMenuInstallation menu;private final RootOriginalInputPublication publication;
        private RootOriginalGuiStopDeclaration(RootLogicalStopFrame frame,RootOriginalMenuInstallation menu,RootOriginalInputPublication publication){
            this.frame=frame;this.menu=menu;this.publication=publication;
        }
        private boolean current(){return frame.guiStop==this&&menu.currentFor(menu.screen)&&frame.client.gui.screen()==menu.screen
            &&publication==originalInputPublication&&frame.termination!=null&&frame.termination.publication==publication&&frame.sceneCurrent()
            &&frame.start.registryIdentity()==moduleManager&&frame.start.enabledFromStart(frame.oldOwner)&&frame.control.current();}
    }
    /** Separate real Automations stop cause; the original S44 gameplay cause and final leaf remain unchanged. */
    public static final class RootMenuLogicalInputRetirementCause {
        private final RootLogicalStopFrame frame;private final ModuleManager.ManualStopReceipt stopped;
        private final RootOriginalNativeTermination termination;private final dev.veltraph.client.service.mc.RootNativeHistory.TerminalTicket nativeTicket;
        private final dev.veltraph.client.service.mc.McOwnedRotation.LogicalIdleObservation camera;private boolean prepared;
        private RootMenuLogicalInputRetirementCause(RootLogicalStopFrame frame,ModuleManager.ManualStopReceipt stopped){
            this.frame=frame;this.stopped=stopped;termination=frame.termination;nativeTicket=termination.nativeTicket;camera=frame.registration.installation.observeLogicalIdle();
        }
        public boolean prepareCurrent(ScriptingInputLock.ControlSnapshot original){if(prepared)return false;prepared=true;
            return original==frame.control&&original.publicationCurrent()&&finalCurrent(original);}
        /** Retained genuine native GUI/stop anchor, checked in the final monitor immediately before logical mutation. */
        public boolean finalCurrent(ScriptingInputLock.ControlSnapshot original){return prepared&&original==frame.control&&termination==frame.termination
            &&termination.stopped==stopped&&termination.completedOwnGuiStopBeforeLogicalCurrent(frame.client)&&nativeTicket!=null&&camera!=null&&camera.current()
            &&nativeTicket.guiNativeRetirementCurrent(termination,frame.client)&&termination.completedOwnGuiStopBeforeLogicalCurrent(frame.client)&&camera.current()&&original.current();}
    }
    private static void retireRootLogicalStop(ModuleManager.ManualStopReceipt actual,Object captured){
        if(!(captured instanceof RootLogicalStopFrame frame)||!actual.matches(frame.start)||!actual.disabledOwnerFromStart(frame.oldOwner)||!frame.sceneCurrent())return;
        Object cause;ScriptingInputLock.RootLogicalRetirementReceipt returned;
        if(frame.guiStop!=null&&frame.termination!=null&&frame.guiStop.current()){
            try{frame.termination.stopped=actual;var menuCause=new RootMenuLogicalInputRetirementCause(frame,actual);cause=menuCause;
                returned=ScriptingInputLock.retireActualRootMenuLogicalReceipt(frame.control,menuCause);
            }catch(RuntimeException|Error unavailable){if(frame.termination!=null)frame.termination.revoked=true;return;}
        }else{
            var observed=frame.provider.read();if(observed.owner()!=null||observed.noOwner()==null)return;
            var gameplayCause=new RootLogicalInputRetirementCause(frame,actual,observed.noOwner());cause=gameplayCause;
            returned=ScriptingInputLock.retireActualRootLogicalReceipt(frame.control,gameplayCause);
        }
        var termination=frame.termination;
        if(termination!=null)try{
            termination.stopped=actual;termination.retirement=returned;termination.cause=cause;
            if(returned!=null&&termination.retirementMatches()&&termination.normalReturnedNativeCurrent(frame.client)){
                var installed=termination.installation;
                installed.idleControl=returned.returnedControl();termination.closedControl=returned.returnedControl();termination.logicalClosed=true;
                termination.nativeHandout=dev.veltraph.client.service.mc.RootNativeHistory.finishTerminal(termination.nativeTicket,termination,frame.client);
                termination.permanentlyRetired=termination.nativeHandout!=null;
                termination.publication.revoked=true;termination.publication.enable.revoked=true;
                // The extra closed-batch observer installs the independent idle source before accepting this handout.
            }else termination.revoked=true;
        }catch(RuntimeException|Error unavailable){termination.revoked=true;}
    }
    /** Distinct no-gameplay-owner logical cause, after the real stop body and private invocation have closed. */
    public static final class RootNoGameplayLogicalInputRetirementCause {
        private final RootOriginalNativeTermination terminal;private final ModuleManager.OwnNoGameplayStopReceipt stopped;
        private final dev.veltraph.client.service.mc.McOwnedRotation.LogicalIdleObservation camera;private boolean prepared;
        private RootNoGameplayLogicalInputRetirementCause(RootOriginalNativeTermination terminal,ModuleManager.OwnNoGameplayStopReceipt stopped){
            this.terminal=terminal;this.stopped=stopped;camera=terminal.frame.registration.installation.observeLogicalIdle();
        }
        public boolean prepareCurrent(ScriptingInputLock.ControlSnapshot original){if(prepared)return false;prepared=true;return finalCurrent(original);}
        /** Callback-free final leaf inside Scripting's final monitor; never current-value native origin. */
        public boolean finalCurrent(ScriptingInputLock.ControlSnapshot original){return prepared&&terminal.noGameplayStopped==stopped
            &&original==terminal.frame.control&&original.publicationCurrent()&&terminal.completedNoGameplayStopBeforeLogicalCurrent(terminal.frame.client)
            &&terminal.nativeTicket!=null&&camera!=null&&camera.current()
            &&terminal.nativeTicket.noGameplayRetirementCurrent(terminal,terminal.frame.client)
            &&terminal.completedNoGameplayStopBeforeLogicalCurrent(terminal.frame.client)&&camera.current();}
    }
    private static void retireRootNoGameplayLogicalStop(ModuleManager.OwnNoGameplayStopReceipt actual,Object captured){
        if(!(captured instanceof RootLogicalStopFrame frame)||frame.termination==null)return;
        var terminal=frame.termination;
        if(terminal.logicalClosed||terminal.permanentlyRetired)return;
        try{
            terminal.noGameplayStopped=actual;
            if(!terminal.completedNoGameplayStopBeforeLogicalCurrent(frame.client)){terminal.revoked=true;return;}
            var cause=new RootNoGameplayLogicalInputRetirementCause(terminal,actual);
            var returned=ScriptingInputLock.retireActualRootNoGameplayLogicalReceipt(frame.control,cause);
            terminal.retirement=returned;terminal.cause=cause;
            if(returned==null||!terminal.retirementMatches()||!terminal.normalReturnedNativeCurrent(frame.client)){terminal.revoked=true;return;}
            terminal.installation.idleControl=returned.returnedControl();terminal.closedControl=returned.returnedControl();terminal.logicalClosed=true;
            terminal.nativeHandout=dev.veltraph.client.service.mc.RootNativeHistory.finishTerminal(terminal.nativeTicket,terminal,frame.client);
            terminal.permanentlyRetired=terminal.nativeHandout!=null;terminal.publication.revoked=true;terminal.publication.enable.revoked=true;
            // Acceptance is deferred to the exact closed-batch source below.
        }catch(RuntimeException|Error unavailable){terminal.revoked=true;terminal.publication.revoked=true;terminal.publication.enable.revoked=true;}
    }
    private static RootOriginalNativeIdleContinuation originalNativeIdleContinuation;
    /** Permanently retired run's genuine continued-idle source. It never references old workflow settings as fresh authority. */
    public static final class RootOriginalNativeIdleContinuation {
        private final RootOriginalNativeTermination terminal;private final RootOriginalNativeInstallation installation;
        private final ModuleManager registry;private final ConfigService store;private final RootPrivatePassiveSource roles;
        private final ModuleActivationGuard.OriginalActivationStart guardSource;private final Minecraft client;
        private final Object player,world,listener,connection,options,gameMode;private final Thread thread=Thread.currentThread();
        private ModuleManager.LifecycleSnapshot lifecycle;private ScriptingInputLock.ControlSnapshot control;private boolean revoked;
        private RootOriginalNativeIdleContinuation(RootOriginalNativeTermination terminal,ModuleManager.LifecycleSnapshot lifecycle){
            this.terminal=terminal;this.lifecycle=lifecycle;installation=terminal.installation;registry=moduleManager;store=config;roles=originalPassiveSource;
            guardSource=terminal.enrollment.admission.request;control=terminal.closedControl;var frame=terminal.frame;client=frame.client;
            player=frame.player;world=frame.world;listener=frame.connection;connection=frame.nativeConnection;options=frame.options;gameMode=frame.gameMode;
        }
        public RootOriginalNativeInstallation installationIdentity(){return installation;}
        public boolean currentFor(Minecraft c){return !revoked&&this==originalNativeIdleContinuation&&terminal.idleContinuation==this
            &&terminal.permanentlyRetired&&terminal.logicalClosed&&!terminal.revoked&&terminal.publication.revoked&&terminal.publication.enable.revoked
            &&Thread.currentThread()==thread&&c==client&&c==Minecraft.getInstance()&&c.isSameThread()&&store==config&&registry==moduleManager
            &&installation==originalNativeInstallation&&installation.currentNativeNoOwner(c)&&roles==originalPassiveSource
            &&guardSource.originalBindingsCurrent()&&originalEnableRegistration!=null&&originalEnableRegistration.current()
            &&originalDisableRegistration!=null&&originalDisableRegistration.current()&&originalModuleTickRegistration!=null&&originalModuleTickRegistration.current()
            &&control!=null&&control.publicationCurrent()&&installation.idleControl!=null&&control.sameHistory(installation.idleControl)
            &&!control.active()&&control.ownerModuleId()==null&&registry.noGameplayOwnerRosterCurrent(lifecycle,roles)
            &&c.player==player&&c.level==world&&c.getConnection()==listener&&listener!=null&&c.getConnection().getConnection()==connection
            &&c.options==options&&c.gameMode==gameMode&&((net.minecraft.network.Connection)connection).isConnected()
            &&c.player!=null&&c.level!=null&&c.player.level()==c.level&&c.player.isAlive();}
    }
    private static void finishOriginalRetiredIdle(ModuleManager.OwnNoGameplayStopReceipt actual,Object captured){
        if(!(captured instanceof RootLogicalStopFrame frame)||frame.termination==null)return;
        var terminal=frame.termination;
        try{
            if(!terminal.permanentlyRetired||terminal.nativeHandout==null||!terminal.logicalClosed||terminal.revoked||terminal.idleContinuation!=null
                ||!actual.disabledOwnerFromStart(frame.start,originalPassiveSource,frame.oldOwner)||!terminal.nativeHandout.current(frame.client))return;
            var life=actual.normalReturnedLifecycle(frame.start,originalPassiveSource);if(life==null)return;
            var idle=new RootOriginalNativeIdleContinuation(terminal,life);terminal.idleContinuation=idle;originalNativeIdleContinuation=idle;
            if(!idle.currentFor(frame.client)){idle.revoked=true;terminal.revoked=true;return;}
            dev.veltraph.client.observation.nativeinput.McNativeManualInputLineage.acceptTerminal(terminal.nativeHandout);
        }catch(RuntimeException|Error unavailable){terminal.revoked=true;if(terminal.idleContinuation!=null)terminal.idleContinuation.revoked=true;}
    }
    private static RootInputDropCall normalInputDropCall;private static String normalRootDropDiagnostic="NOT_OBSERVED";
    /** Pure cached original-source branch, never polls an owner/provider/permit. */
    public static String cachedNormalRootDropDiagnostic(){return normalRootDropDiagnostic;}
    /** Only the actual Root input-lock update can construct this normal drop source event. */
    public static final class RootInputDropCall {
        private final RootNoOwnerObservation noOwner;private final Module oldOwner;private final ModuleManager root;private final boolean oldActive;private final Thread thread;private boolean revoked;
        private RootInputDropCall(RootNoOwnerObservation noOwner){this.noOwner=noOwner;root=moduleManager;oldActive=ScriptingInputLock.isActive();oldOwner=root.get(ScriptingInputLock.ownerModuleId()).orElse(null);thread=Thread.currentThread();}
        public RootNoOwnerObservation noOwner(){return noOwner;}
        public boolean matchesOriginal(Module exact){return !revoked&&oldActive&&oldOwner==exact&&exact!=null&&root==moduleManager&&root.get(exact.id()).orElse(null)==exact
            &&normalInputDropCall==this&&Thread.currentThread()==thread&&noOwner!=null&&noOwner.current();}
        public boolean current(){return matchesOriginal(oldOwner)&&!ScriptingInputLock.isActive()&&ScriptingInputLock.ownerModuleId()==null;}
    }
    private static void updateScheduledInputLock(){
        var owner=moduleManager.inputOwner();
        String foragingOwner=foragingMaintenance==null?null:foragingMaintenance.controlOwnerId();
        if(foragingOwner==null&&galateaWorkflows!=null)foragingOwner=galateaWorkflows.controlOwnerId();
        String maintenanceOwner=farmMaintenance==null?null:farmMaintenance.controlOwnerId();
        String capturedOwner=maintenanceOwner!=null?maintenanceOwner:foragingOwner;
        RootInputDropCall normal=null;
        if(normalInputDropCall!=null){normalInputDropCall.revoked=true;normalRootDropDiagnostic="ACTUAL_ROOT_DROP_REENTRY";}
        else if(owner.isEmpty()&&capturedOwner==null&&ScriptingInputLock.isActive()&&rootRotationProvider!=null
                &&dev.veltraph.client.service.mc.McOwnedRotation.hasNormalIdleCandidate(Minecraft.getInstance())){
            var observed=rootRotationProvider.read();if(observed.owner()==null&&observed.noOwner()!=null){normal=new RootInputDropCall(observed.noOwner());normalInputDropCall=normal;normalRootDropDiagnostic="AUTHENTIC_ROOT_NO_OWNER_DROP_CALL_ISSUED";}
            else normalRootDropDiagnostic=observed.owner()!=null?"ACTUAL_ROOT_ROTATION_OWNER_PRESENT":"FINAL_NULL_WITHOUT_AUTHENTIC_NO_OWNER_CAUSE";
        }
        var actualNormal=normal==null?null:dev.veltraph.client.service.mc.McOwnedRotation.beginNormalRootInputDrop(Minecraft.getInstance(),normal);
        try{
            boolean shouldOwn=owner.isPresent()||capturedOwner!=null;String ownerId=owner.map(Module::id).orElse(capturedOwner);
            var prior=originalInputPublication;var selected=owner.orElse(null);var retained=retainedLoadoutDispatch;
            if(retained!=null&&retained.readyToPublishInput()&&shouldOwn&&retained.helper.id().equals(ownerId)){
                if(retainedInputPublication!=null){retainedInputPublication.revoked=true;retained.revoked=true;ScriptingInputLock.set(shouldOwn,ownerId);}
                else{
                    var publication=new RootInputOwnershipPublication(retained);retainedInputPublication=publication;
                    try{retained.input=publishRetainedHelperOriginalInput(publication,shouldOwn,ownerId);if(retained.input==null)retained.revoked=true;}
                    finally{if(retainedInputPublication==publication)retainedInputPublication=null;}
                }
            }else if(prior!=null&&farmMaintenance!=null&&prior.savedEnableDeclaration!=null&&selected==prior.savedEnableDeclaration.helperIdentity()
                &&maintenanceOwner!=null&&shouldOwn)publishSavedMaintenanceOriginalInput(prior,selected,ownerId);
            else publishScheduledOriginalInput(selected,capturedOwner,shouldOwn,ownerId);
        }finally{
            try{dev.veltraph.client.service.mc.McOwnedRotation.finishNormalRootInputDrop(actualNormal);}finally{if(normalInputDropCall==normal)normalInputDropCall=null;}
        }
    }
    /** A live issued Hub handoff receives only its exact travel feedback while its engine is quiet. */
    private static boolean quiesceHubWarpReceipt(Module module,String line){
        if(!(module instanceof dev.veltraph.client.foraging.module.AutoForagingModule foraging)
                ||!foraging.hasIssuedHubWarp())return false;
        foraging.onHubFeedback(line);return true;
    }
    /** Preserve only the sole guarded input owner's issued same-connection Hub command. */
    private static boolean tickOwnedHubWarp(Minecraft client){
        var running=moduleManager.enabledControllers();
        if(running.size()!=1||!(running.getFirst() instanceof dev.veltraph.client.foraging.module.AutoForagingModule parent)
                ||!parent.hasIssuedHubWarp())return false;
        pollRootKeysOnce();
        if(scriptScheduler!=null)pollRootSchedulerOnce();
        releaseNativeInputs(client);
        if(farmServices!=null){farmServices.rotation().cancelRotation();farmServices.input().releaseAllMovement();farmServices.breaking().setBreaking(false);}
        if(client.player!=null&&client.level!=null&&client.player.isAlive()&&generalFailsafe!=null)pollRootGeneralSafetyOnce();
        var observed=gameContext.current();
        boolean nativeKnown=client.player!=null&&client.level!=null&&client.player.isAlive()&&client.gui.screen()==null
                &&observed.onHypixel()&&observed.inSkyBlock()&&observed.area().isSkyBlock()
                &&observed.area()!=dev.veltraph.client.game.GameArea.UNKNOWN&&observed.area()!=dev.veltraph.client.game.GameArea.LIMBO;
        if(nativeKnown&&hydraAdapter!=null)pollRootHydraOnce();
        if(!parent.isEnabled()||moduleManager.enabledControllers().size()!=1
                ||moduleManager.inputOwner().orElse(null)!=parent||scheduledHigherPriorityOwned()){
            boolean healHandoff=parent.isEnabled()&&moduleManager.inputOwner().orElse(null)==parent&&issuedTravelHealEligible(client);
            moduleManager.stopAll();releaseNativeInputs(client);
            if(healHandoff)survivalStopSuppressed=false;
            return true;
        }
        var transition=parent.expectedHubOwnerTransition();
        if(transition==dev.veltraph.client.foraging.hub.McHubSourceLoop.OwnerTransition.WAITING)return true;
        if(transition==dev.veltraph.client.foraging.hub.McHubSourceLoop.OwnerTransition.ARRIVED
                &&parent.commitHubOwnerTransition()){
            if(generalFailsafe!=null)generalFailsafe.onWorldStop();
            if(hydraAdapter!=null)hydraAdapter.onWorldStop();
            skyBlockHealth.reset();
            if(combatAutoHeal!=null)combatAutoHeal.reset();
            lastGeneralPlayer=client.player;lastGeneralConnection=client.getConnection();lastWorld=client.level;
            return true; // The original timer has advanced; resume normal owner callbacks next tick.
        }
        moduleManager.stopAll();releaseNativeInputs(client);
        VeltraphLog.basic("HubWarp","Stopped rejected or revoked issued same-connection Hub arrival");
        return true;
    }
    private static boolean consumeHubExpectedTeleport(dev.veltraph.client.world.Vec3 position,long now){
        if(moduleManager==null)return false;
        var enabled=moduleManager.enabledControllers();
        if(enabled.size()!=1)return false;
        var owner=enabled.getFirst();
        return owner instanceof dev.veltraph.client.foraging.module.AutoForagingModule foraging
                &&moduleManager.inputOwner().orElse(null)==owner
                &&foraging.consumeHubExpectedTeleport(position,now);
    }
    /** Only a fresh eligible healer in the issued travel's original UUID/connection may rearm after cancellation. */
    private static boolean issuedTravelHealEligible(Minecraft client){
        return !survivalStopSuppressed&&combatAutoHeal!=null&&client.player!=null&&client.level!=null
                &&client.player.isAlive()&&client.player.level()==client.level&&client.gui.screen()==null
                &&client.player.containerMenu==client.player.inventoryMenu&&client.player.inventoryMenu.getCarried().isEmpty()
                &&lastGeneralPlayer instanceof net.minecraft.client.player.LocalPlayer oldPlayer
                &&oldPlayer.getUUID().equals(client.player.getUUID())&&client.getConnection()==lastGeneralConnection
                &&gameContext.current().inSkyBlock()&&combatAutoHeal.shouldAcquireControl(true);
    }
    private static boolean scheduledHigherPriorityOwned(){
        var client=Minecraft.getInstance();
        boolean emergency=client.player!=null&&client.level!=null&&client.player.isAlive()&&gameContext.current().inSkyBlock()
                &&combatAutoHeal!=null&&!survivalStopSuppressed&&combatAutoHeal.shouldAcquireControl(true);
        return maintenanceOwnsFarm()||(foragingMaintenance!=null&&foragingMaintenance.active())
                ||(galateaWorkflows!=null&&galateaWorkflows.active())
                ||(farmCycle!=null&&farmCycle.active())
                ||(autoRewarp!=null&&autoRewarp.state()!=dev.veltraph.client.rewarp.AutoRewarpState.IDLE)
                ||(savedAnchorRecovery!=null&&savedAnchorRecovery.isEnabled())
                ||(combatAutoHeal!=null&&combatAutoHeal.ownsControl())||emergency
                ||(hydra!=null&&(hydra.hasActiveLocalReaction()||hydra.hasActiveGeneralResponse()))
                ||moduleManager.all().stream().anyMatch(Module::hasPendingCleanup);
    }
    private record RootManualStopCapture(Object logical,RootFarmReturnCloseCause close) {}
    private static Object originalManualLogicalCapture(Object captured){
        if(captured instanceof RootManualStopCapture r9)captured=r9.logical();
        return captured instanceof RootOriginalManualStopCapture original?original.logical():captured;
    }
    private static RootFarmReturnCloseCause captureFarmReturnManualClose(Minecraft client,ModuleManager.ManualStopStart actual){
        var coordinator=suspendedFarmReturn;var registration=farmReturnRegistrationFor(coordinator);
        if(coordinator==null||registration==null||actual==null||actual.registryIdentity()!=moduleManager
                ||!actual.startingCurrent()||!registration.current(coordinator,client,farmServices,config,moduleManager,gameContext))return null;
        return new RootFarmReturnCloseCause(client,coordinator,registration,actual,null);
    }
    private static RootFarmReturnCloseCause currentFarmReturnManualClose(){
        var exact=activeFarmReturnManualClose;return exact!=null&&exact.current(suspendedFarmReturn)?exact:null;
    }
    /** Only an actual active original Root tick can supply a tick cancellation cause. */
    public static RootFarmReturnCloseCause farmReturnCloseCauseFor(RootFarmReturnTick tick,
            dev.veltraph.client.rewarp.mc.McSuspendedFarmReturnCoordinator exact){
        if(tick==null||!tick.current(exact)||exact!=suspendedFarmReturn)return null;
        var registration=farmReturnRegistrationFor(exact);
        return registration==null?null:new RootFarmReturnCloseCause(Minecraft.getInstance(),exact,registration,null,tick);
    }
    /** Authentic stop provenance only. Native cleanup independently requires its exact old private receipts. */
    public static final class RootFarmReturnCloseCause {
        private final Minecraft client;private final dev.veltraph.client.rewarp.mc.McSuspendedFarmReturnCoordinator coordinator;
        private final RootFarmReturnRegistration registration;private final ModuleManager registry;
        private final ConfigService store;private final FarmServices services;private final Thread thread;
        private final ModuleManager.ManualStopStart manual;private final RootFarmReturnTick tick;
        private final Object player,world,connection;private boolean revoked;
        private RootFarmReturnCloseCause(Minecraft client,dev.veltraph.client.rewarp.mc.McSuspendedFarmReturnCoordinator coordinator,
                RootFarmReturnRegistration registration,ModuleManager.ManualStopStart manual,RootFarmReturnTick tick){
            this.client=client;this.coordinator=coordinator;this.registration=registration;this.manual=manual;this.tick=tick;
            registry=moduleManager;store=config;services=farmServices;thread=Thread.currentThread();
            player=client.player;world=client.level;connection=client.getConnection();
        }
        public boolean current(dev.veltraph.client.rewarp.mc.McSuspendedFarmReturnCoordinator exact){
            return !revoked&&exact==coordinator&&suspendedFarmReturn==coordinator&&Thread.currentThread()==thread
                &&client==Minecraft.getInstance()&&client.isSameThread()&&moduleManager==registry&&config==store&&farmServices==services
                &&rootFarmReturnRegistration==registration&&registration.current(exact,client,services,store,registry,gameContext)
                &&(manual!=null?activeFarmReturnManualClose==this&&manual.registryIdentity()==registry&&manual.startingCurrent()
                    :tick!=null&&tick.current(exact));
        }
        public boolean affectedFromOriginalStop(Module exact){return manual!=null&&manual.enabledFromStart(exact);}
        public Object originalPlayer(){return player;}
        public Object originalWorld(){return world;}
        public Object originalConnection(){return connection;}
    }

    /** Canonical coordinator data only; no caller can create a Root installation or tick receipt. */
    public static dev.veltraph.client.rewarp.mc.McSuspendedFarmReturnCoordinator suspendedFarmReturnCoordinator(){return suspendedFarmReturn;}
    public static RootFarmReturnRegistration farmReturnRegistrationFor(
            dev.veltraph.client.rewarp.mc.McSuspendedFarmReturnCoordinator exact){
        var registration=rootFarmReturnRegistration;
        return exact!=null&&exact==suspendedFarmReturn&&registration!=null&&registration.original==exact?registration:null;
    }
    /** Privately minted only by this actual Bootstrap factory. It affiliates existing native objects. */
    public static final class RootFarmReturnRegistration {
        private final Minecraft client;private final FarmServices services;private final ConfigService store;
        private final ModuleManager registry;private final dev.veltraph.client.game.GameContextService location;
        private final dev.veltraph.client.rewarp.mc.McSuspendedFarmReturnCoordinator original;
        private final RootInputReleaseRegistration installation;
        private RootFarmReturnRegistration(Minecraft client,FarmServices services,ConfigService store,
                ModuleManager registry,dev.veltraph.client.game.GameContextService location,
                dev.veltraph.client.rewarp.mc.McSuspendedFarmReturnCoordinator original,RootInputReleaseRegistration installation){
            this.client=client;this.services=services;this.store=store;this.registry=registry;this.location=location;this.original=original;this.installation=installation;
        }
        /** Stored/native identity check only. No role, settings, provider, clock or scene callback runs. */
        public boolean current(dev.veltraph.client.rewarp.mc.McSuspendedFarmReturnCoordinator exact,Minecraft actualClient,
                FarmServices actualServices,ConfigService actualStore,ModuleManager actualRegistry,
                dev.veltraph.client.game.GameContextService actualLocation){
            return rootFarmReturnRegistration==this&&suspendedFarmReturn==original&&exact==original
                &&client==actualClient&&client==Minecraft.getInstance()&&client.isSameThread()
                &&services==actualServices&&farmServices==services&&store==actualStore&&config==store
                &&registry==actualRegistry&&moduleManager==registry&&location==actualLocation&&gameContext==location
                &&rootInputReleaseRegistration==installation&&services.input() instanceof dev.veltraph.client.service.mc.McInputService input
                &&installation!=null&&installation.current(input,client);
        }
        /** Refusal-only safety observations; the issuer checks its stored final leaf afterwards. */
        public boolean higherPriorityIdle(){return current(original,client,services,store,registry,location)
                &&!scheduledRestActive()&&!scheduledHigherPriorityOwned()&&current(original,client,services,store,registry,location);}
        public java.util.Optional<dev.veltraph.client.farming.module.FarmModule.IssuedReturnCheckpoint> pauseOriginalFarm(
                dev.veltraph.client.rewarp.mc.McSuspendedFarmReturnCoordinator.PauseCall exact){
            // Reject a borrowed public registration call before any provider/native observer is sampled.
            boolean actualCaller=StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).walk(frames->{
                var caller=frames.filter(f->f.getDeclaringClass()!=RootFarmReturnRegistration.class).findFirst().orElse(null);
                return caller!=null&&caller.getDeclaringClass()==dev.veltraph.client.rewarp.mc.McSuspendedFarmReturnCoordinator.class
                    &&caller.getMethodName().equals("admit");
            });
            if(!actualCaller||rootFarmReturnRegistration!=this||suspendedFarmReturn!=original||exact==null
                ||!original.selectedPauseForRoot(exact))return java.util.Optional.empty();
            return pauseOriginalIssuedFarm(exact);
        }
        public boolean activateDriver(dev.veltraph.client.core.AbstractModule exact){
            if(!current(original,client,services,store,registry,location)||exact!=original.driver()
                ||registry.get(exact.id()).orElse(null)!=exact||!original.permitsDriverActivation(exact))return false;
            return guard.start(exact);
        }
        public void stopDriver(dev.veltraph.client.core.AbstractModule exact){
            if(!current(original,client,services,store,registry,location)||exact!=original.driver()
                ||registry.get(exact.id()).orElse(null)!=exact)throw new IllegalStateException("Original return driver registration was replaced");
            registry.disable(exact.id());
        }
        public boolean beginResumeOwner(dev.veltraph.client.rewarp.mc.McSuspendedFarmReturnCoordinator.ResumeCall exact){
            if(!current(original,client,services,store,registry,location)||exact==null||original.currentResumeCall()!=exact
                ||!exact.phaseCurrent()||!exact.farm().issuedReturnResumingBy(exact.checkpoint(),exact)||!exact.nativeCurrent())return false;
            if(!publishIssuedResumeOriginalInput(exact))return false;
            return current(original,client,services,store,registry,location)&&original.currentResumeCall()==exact
                &&exact.phaseCurrent()&&exact.farm().issuedReturnResumingBy(exact.checkpoint(),exact)&&exact.nativeCurrent();
        }
    }
    /** Real Farm body runs once. The native observation matures only after its finally has returned. */
    private static java.util.Optional<dev.veltraph.client.farming.module.FarmModule.IssuedReturnCheckpoint> pauseOriginalIssuedFarm(
            dev.veltraph.client.rewarp.mc.McSuspendedFarmReturnCoordinator.PauseCall exact){
        var registration=rootFarmReturnRegistration;
        if(registration==null||exact==null||!registration.original.selectedPauseForRoot(exact)
            ||!registration.current(registration.original,registration.client,registration.services,registration.store,registration.registry,registration.location))
            return java.util.Optional.empty();
        var actual=beginOriginalNativePause(exact);
        if(actual==null)return java.util.Optional.empty();
        try{
            var returned=exact.farm().pauseForIssuedReturn(exact);
            // No boolean or fabricated checkpoint can replace the actual original method result.
            finishOriginalNativePause(actual,returned);
            return returned;
        }finally{closeOriginalNativePause(actual);}
    }
    /** Pre-pause original Root admission, privately constructed only inside the registered gameplay helper. */
    public static final class RootFarmReturnAdmission {
        private final RootFarmReturnTick tick;private final RootFarmReturnRegistration registration;
        private final dev.veltraph.client.rewarp.mc.McSuspendedFarmReturnCoordinator coordinator;
        private final dev.veltraph.client.farming.module.FarmModule farm;
        private final RootOriginalInputPublication originalInput;
        private final Minecraft client;private final ModuleManager registry;private final ConfigService store;
        private final FarmServices services;private final ModuleManager.LifecycleSnapshot lifecycle;
        private final ScriptingInputLock.ControlSnapshot control;private final Thread thread;
        private final Object player,world,listener,connection,options,gameMode;
        private RootFarmReturnAdmission(RootFarmReturnTick tick,
                dev.veltraph.client.rewarp.mc.McSuspendedFarmReturnCoordinator coordinator,
                dev.veltraph.client.farming.module.FarmModule farm,RootOriginalInputPublication original){
            this.tick=tick;this.coordinator=coordinator;this.farm=farm;originalInput=original;
            client=Minecraft.getInstance();registry=moduleManager;store=config;services=farmServices;
            thread=Thread.currentThread();registration=farmReturnRegistrationFor(coordinator);
            player=client.player;world=client.level;listener=client.getConnection();
            connection=client.getConnection()==null?null:client.getConnection().getConnection();options=client.options;gameMode=client.gameMode;
            lifecycle=registry.lifecycleSnapshot();control=ScriptingInputLock.controlSnapshot();
        }
        /** Stored original publication only. Reading it cannot create or renew native authority. */
        public RootOriginalInputPublication originalInputPublication(){return originalInput;}
        public boolean current(dev.veltraph.client.rewarp.mc.McSuspendedFarmReturnCoordinator exact,RootFarmReturnTick actual){
            if(activeFarmReturnAdmission!=this||exact!=coordinator||actual!=tick||tick==null||!tick.current(exact)
                    ||Thread.currentThread()!=thread||client!=Minecraft.getInstance()||!client.isSameThread()
                    ||moduleManager!=registry||config!=store||farmServices!=services||registration==null
                    ||rootFarmReturnRegistration!=registration||!registration.current(exact,client,services,store,registry,gameContext)
                    ||originalInput==null||!originalInput.currentFor(farm)||originalRootPublicationFor(farm)!=originalInput
                    ||lifecycle==null||!lifecycle.known()||!registry.lifecycleCurrent(lifecycle)
                    ||registry.get(farm.id()).orElse(null)!=farm||!farm.isEnabled()
                    ||farm.isSuspended()&&!farm.issuedReturnPausingUnder(this)
                    ||control==null||!control.current()||!control.active()||!farm.id().equals(control.ownerModuleId()))return false;
            return client.player==player&&client.level==world&&client.getConnection()==listener&&listener!=null
                    &&client.getConnection().getConnection()==connection&&client.options==options&&client.gameMode==gameMode
                    &&player!=null&&world!=null&&client.getCameraEntity()==player&&client.player.isAlive()
                    &&client.player.level()==client.level&&client.gui.screen()==null&&!client.isPaused()
                    &&client.player.containerMenu==client.player.inventoryMenu&&client.player.inventoryMenu.getCarried().isEmpty();
        }
    }

    /** Provenance of one real Root dispatch, never a world-change or native action permission. */
    public static final class RootFarmReturnTick {
        private final Minecraft client;private final dev.veltraph.client.rewarp.mc.McSuspendedFarmReturnCoordinator coordinator;
        private final Thread thread;private final long capturedClockTick;
        private final RootFarmReturnRegistration registration;
        private final Object originalClock;private final RootOriginalGameplayTickInvocation gameplay;private boolean normalReturned,dispatchClosed;
        private RootFarmReturnTick(Minecraft client,dev.veltraph.client.rewarp.mc.McSuspendedFarmReturnCoordinator coordinator){
            this.client=client;this.coordinator=coordinator;thread=Thread.currentThread();capturedClockTick=clock.currentTick();
            registration=farmReturnRegistrationFor(coordinator);originalClock=clock;gameplay=activeGameplayReturnFrame==null?null:activeGameplayReturnFrame.gameplay;
        }
        public long clockTick(){return capturedClockTick;}
        public long originalClockTick(){return capturedClockTick;}
        public boolean returnedNormal(){return dispatchClosed&&normalReturned&&gameplay!=null&&gameplay.closed&&!gameplay.revoked;}
        /** Registration provenance only. Actual retained movement also checks its private source and SDK frame bound. */
        public boolean retainedRegistrationCurrent(dev.veltraph.client.rewarp.mc.McSuspendedFarmReturnCoordinator exact){
            if(!returnedNormal()||exact!=coordinator||suspendedFarmReturn!=coordinator||Thread.currentThread()!=thread
                ||client!=Minecraft.getInstance()||!client.isSameThread()||clock!=originalClock
                ||registration==null||rootFarmReturnRegistration!=registration)return false;
            long now=clock.currentTick();
            return now>=capturedClockTick&&now-capturedClockTick<=1
                &&registration.current(exact,client,farmServices,config,moduleManager,gameContext);
        }
        public boolean current(dev.veltraph.client.rewarp.mc.McSuspendedFarmReturnCoordinator exact){
            return !dispatchClosed&&activeFarmReturnTick==this&&exact==coordinator&&suspendedFarmReturn==coordinator
                &&gameplay!=null&&originalGameplayTickInvocation==gameplay&&!gameplay.closed&&!gameplay.revoked&&gameplay.nativeTick.current()
                &&activeGameplayReturnFrame!=null&&activeGameplayReturnFrame.gameplay==gameplay&&activeGameplayReturnFrame.returnedTick==this
                &&Thread.currentThread()==thread&&client==Minecraft.getInstance()&&client.isSameThread()
                &&clock==originalClock&&clock.currentTick()==capturedClockTick&&farmReturnRegistrationFor(exact)==registration
                &&registration!=null&&registration.current(exact,client,farmServices,config,moduleManager,gameContext);
        }
    }

    /** Root-only construction and installed observation identity. This grants no native gear action. */
    public static final class RootEarlyLoadoutObservationSource {
        private final Minecraft client;private final ConfigService store;
        private RootEarlyLoadoutObservationSource(Minecraft client,ConfigService store){this.client=client;this.store=store;}
        public boolean affiliates(dev.veltraph.client.garden.pests.mc.McPestEarlyLoadoutObservation actual,Minecraft nativeClient,ConfigService actualStore){return this==rootEarlyLoadoutObservationSource&&actual==earlyLoadoutObservation&&nativeClient==client&&actualStore==store&&config==store&&client.isSameThread();}
        public java.util.List<dev.veltraph.client.garden.pests.mc.PestEarlyMessageCarrier.Receipt> received(dev.veltraph.client.garden.pests.mc.McPestEarlyLoadoutObservation actual){
            return affiliates(actual,client,store)&&client.getConnection()!=null&&client.getConnection().getClass()==net.minecraft.client.multiplayer.ClientPacketListener.class
                &&client.getConnection() instanceof dev.veltraph.client.garden.pests.mc.PestEarlyMessageCarrier carrier&&carrier.earlyLoadoutMessageHistoryKnown()?carrier.earlyLoadoutReceivedMessages():null;}
    }
    private static void tickEarlyLoadoutObservation(Minecraft client,boolean exclusiveResponse){
        if(earlyLoadoutObservation==null)return;
        if(exclusiveResponse||scheduledRestActive()||farmMaintenance==null||farmMaintenance.active()
            ||(farmCycle!=null&&farmCycle.active())||(autoRewarp!=null&&autoRewarp.state()!=dev.veltraph.client.rewarp.AutoRewarpState.IDLE)
            ||(savedAnchorRecovery!=null&&savedAnchorRecovery.isEnabled())||(foragingMaintenance!=null&&foragingMaintenance.active())||(galateaWorkflows!=null&&galateaWorkflows.active())
            ||(combatAutoHeal!=null&&combatAutoHeal.ownsControl())||(hydra!=null&&(hydra.hasActiveLocalReaction()||hydra.hasActiveGeneralResponse()))){earlyLoadoutObservation.revoke();return;}
        var running=moduleManager.enabledControllers();
        if(running.size()!=1||!(running.getFirst() instanceof dev.veltraph.client.farming.module.FarmModule farm)){earlyLoadoutObservation.revoke();return;}
        try{var observed=earlyLoadoutObservation.observe(farm,clock.currentTick(),actualTrapTabRows().stream().map(dev.veltraph.client.game.LocationText::stripColorCodes).toList(),clock.nowMillis());dispatchEarlyRetainedLoadout(observed);}
        catch(RuntimeException unavailable){earlyLoadoutObservation.revoke();}
    }
    /** Read-only private session identity used by the native callback before and after original listeners. */
    public static Object earlyLoadoutReceiptScope(){return earlyLoadoutObservation==null?null:earlyLoadoutObservation.receiveScopeIdentity();}
    /** Read-only view; copied frames are not Root dispatch or native gear permission. */
    public static dev.veltraph.client.garden.pests.mc.McPestEarlyLoadoutObservation.TimingFrame earlyLoadoutTiming(){return earlyLoadoutObservation==null?null:earlyLoadoutObservation.latestObservation();}
    public static String earlyLoadoutObservationStatus(){return earlyLoadoutObservation==null?"Uninstalled early loadout observation":earlyLoadoutObservation.status();}

    private static dev.veltraph.client.garden.pests.mc.McPestEarlyLoadoutModule earlyRetainedLoadout;
    private static RootRetainedLoadoutDispatch retainedLoadoutDispatch;
    private static RootInputOwnershipPublication retainedInputPublication;
    /** Private real Root timing/request dispatch, distinct from advisory Desired or public helper activation. */
    public static final class RootRetainedLoadoutDispatch {
        private final dev.veltraph.client.garden.pests.mc.McPestEarlyLoadoutObservation.TimingFrame frame;
        private final dev.veltraph.client.farming.module.FarmModule farm;
        private final dev.veltraph.client.garden.pests.mc.McPestEarlyLoadoutModule helper;
        private final OwnedUseControlSnapshot root;
        private final ModuleManager.LifecycleSnapshot originalLifecycle;
        private final ScriptingInputLock.ControlSnapshot originalInput;
        private final RootOriginalInputPublication originalPublication;
        private final RootRetainedHelperEnableDeclaration helperEnableDeclaration;
        private FarmMaintenanceCoordinator.PendingMaintenanceIntent intent;
        private Object requestIdentity;
        private RootSavedMaintenanceIntentDispatch originalIntentDispatch;
        private RootOriginalNativeMaintenancePauseInvocation originalPause;
        private FarmMaintenanceCoordinator.SavedMaintenanceEnableSource savedEnable;
        private ModuleManager.OwnEnableReceipt enable;
        private ModuleManager.LifecycleSnapshot activeLifecycle;
        private dev.veltraph.client.rewarp.FarmMaintenanceLease.Token token;
        private ScriptingInputLock.RootPublicationReceipt input;
        private boolean intentConsumed,consumed,committed,revoked;
        private RootRetainedLoadoutDispatch(dev.veltraph.client.garden.pests.mc.McPestEarlyLoadoutObservation.TimingFrame frame){
            this.frame=frame;farm=frame.farm();helper=earlyRetainedLoadout;
            root=new OwnedUseControlSnapshot(null);originalLifecycle=moduleManager.lifecycleSnapshot();
            originalInput=ScriptingInputLock.controlSnapshot();originalPublication=originalRootPublicationFor(farm);
            helperEnableDeclaration=new RootRetainedHelperEnableDeclaration(this);
        }
        public dev.veltraph.client.garden.pests.PestEarlyLoadoutPolicy.Desired desired(){return frame.desired();}
        public dev.veltraph.client.farming.module.FarmModule originalFarm(){return farm;}
        public RootOriginalInputPublication originalInputPublication(){return originalPublication;}
        public boolean initialCurrent(dev.veltraph.client.garden.pests.mc.McPestEarlyLoadoutModule expected,FarmMaintenanceCoordinator coordinator){
            return !consumed&&!revoked&&this==retainedLoadoutDispatch&&expected==helper&&helper==earlyRetainedLoadout&&coordinator==farmMaintenance
                &&frame.currentOriginal()&&frame.tick()==clock.currentTick()&&farm.isEnabled()&&!farm.isSuspended()
                &&root.current()&&moduleManager.lifecycleCurrent(originalLifecycle)&&originalInput!=null&&originalInput.current()
                &&originalInput.active()&&farm.id().equals(originalInput.ownerModuleId())&&originalPublication!=null&&originalPublication.currentFor(farm);
        }
        private boolean bindOriginalIntent(RootSavedMaintenanceIntentDispatch actual){
            if(intentConsumed||revoked||this!=retainedLoadoutDispatch||actual==null||actual!=savedMaintenanceIntentDispatch
                ||intent==null||actual.intent!=intent||actual.requestIdentity!=requestIdentity||actual.selected!=helper||!actual.matchesIntent(intent))return false;
            intentConsumed=true;originalIntentDispatch=actual; // One original request, consumed before any callback.
            if(!actual.contextCurrent()||!initialCurrent(helper,farmMaintenance)){revoked=true;return false;}return true;
        }
        private void observeClosedOriginalPause(RootOriginalNativeMaintenancePauseInvocation actual){
            if(revoked||consumed||originalPause!=null||this!=retainedLoadoutDispatch||!intentConsumed||actual==null
                ||actual.transaction.dispatch!=originalIntentDispatch||actual.transaction.selected!=helper
                ||actual.pause.helperIdentity()!=helper||actual.farm!=farm||actual.publication!=originalPublication
                ||actual.pause.coordinatorIdentity()!=farmMaintenance||!actual.acceptedNormalPauseProof())return;
            originalPause=actual;token=actual.token;savedEnable=actual.declaration;
            if(savedEnable==null||savedEnable.helperIdentity()!=helper||savedEnable.farm()!=farm
                ||savedEnable.maintenanceToken()!=token||savedEnable.originalInputPublication()!=originalPublication)revoked=true;
        }
        public boolean reservedCurrent(dev.veltraph.client.garden.pests.mc.McPestEarlyLoadoutModule expected,FarmMaintenanceCoordinator coordinator,
                dev.veltraph.client.rewarp.FarmMaintenanceLease.Token issued){
            if(revoked||consumed||this!=retainedLoadoutDispatch||expected!=helper||helper!=earlyRetainedLoadout||coordinator!=farmMaintenance
                ||!intentConsumed||originalPause==null||!originalPause.acceptedNormalPauseProof()||issued==null||issued!=token
                ||coordinator.tokenFor(helper)!=issued||coordinator.state()!=dev.veltraph.client.rewarp.FarmMaintenanceLease.State.PREPARED
                ||savedEnable==null||savedEnable.maintenanceToken()!=issued||!farm.maintenanceOwnedBy(issued)
                ||!moduleManager.lifecycleCurrent(originalLifecycle)||!frame.currentOriginal())return false;
            consumed=true;return true;
        }
        public boolean commitActual(dev.veltraph.client.garden.pests.mc.McPestEarlyLoadoutModule expected,FarmMaintenanceCoordinator coordinator,
                dev.veltraph.client.rewarp.FarmMaintenanceLease.Token issued,ModuleManager.OwnEnableReceipt actual){
            if(!consumed||committed||revoked||this!=retainedLoadoutDispatch||expected!=helper||coordinator!=farmMaintenance||issued!=token
                ||coordinator.tokenFor(helper)!=token||coordinator.state()!=dev.veltraph.client.rewarp.FarmMaintenanceLease.State.HELPER_ACTIVE
                ||!farm.maintenanceOwnedBy(token)||helperEnableDeclaration.completed!=actual||helperEnableDeclaration.saved!=savedEnable
                ||!moduleManager.ownEnableMatches(actual,originalLifecycle,helper)||!originalInput.current())return false;
            enable=actual;activeLifecycle=actual.completedSnapshot();committed=true;
            return activeLifecycle!=null&&root.currentRetained(this);
        }
        private boolean lifecycleCurrent(){return activeLifecycle!=null&&(helper.isEnabled()?moduleManager.lifecycleCurrent(activeLifecycle)
            :moduleManager.completedOwnDisableFrom(activeLifecycle,helper).filter(ModuleManager.OwnDisableReceipt::current).isPresent());}
        public boolean continuationCurrent(FarmMaintenanceCoordinator coordinator,dev.veltraph.client.garden.pests.mc.McPestEarlyLoadoutModule expected,
                dev.veltraph.client.farming.module.FarmModule originalFarm){
            return !revoked&&committed&&this==retainedLoadoutDispatch&&expected==helper&&helper==earlyRetainedLoadout&&originalFarm==farm&&coordinator==farmMaintenance
                &&coordinator.tokenFor(helper)==token&&coordinator.retainedOriginalFarmOwned(farm,token,this)&&lifecycleCurrent()&&root.currentRetained(this)
                &&originalPause!=null&&originalPause.acceptedNormalPauseProof()&&input!=null&&input.current();
        }
        public boolean inputHandoffReady(){return input!=null&&input.current();}
        private boolean readyToPublishInput(){return committed&&!revoked&&this==retainedLoadoutDispatch&&helper.isEnabled()
            &&moduleManager.ownEnableMatches(enable,originalLifecycle,helper)&&root.currentRetained(this)
            &&originalPause!=null&&originalPause.acceptedNormalPauseProof()&&(input==null?originalInput.current():input.current());}
    }
    /** Joins the real SourceP first-enable claim; it cannot claim a second or substitute an onEnable snapshot. */
    private static final class RootRetainedHelperEnableDeclaration {
        private final RootRetainedLoadoutDispatch dispatch;
        private final RootOriginalInputPublication original;
        private final dev.veltraph.client.farming.module.FarmModule farm;
        private final dev.veltraph.client.garden.pests.mc.McPestEarlyLoadoutModule helper;
        private FarmMaintenanceCoordinator.SavedMaintenanceEnableSource saved;
        private ModuleManager.ActualEnableStart start;
        private ModuleManager.OwnEnableReceipt completed;
        private Object checkpoint;private boolean claimed,validated,revoked;
        private RootRetainedHelperEnableDeclaration(RootRetainedLoadoutDispatch dispatch){this.dispatch=dispatch;original=dispatch.originalPublication;farm=dispatch.farm;helper=dispatch.helper;}
        RootOriginalInputPublication originalInputPublication(){return original;}
        dev.veltraph.client.farming.module.FarmModule farm(){return farm;}
        Module helperIdentity(){return helper;}
        private boolean beginSavedEnable(FarmMaintenanceCoordinator.SavedMaintenanceEnableSource source,ModuleManager.ActualEnableStart actual){
            if(claimed||revoked||source==null||actual==null||dispatch!=retainedLoadoutDispatch||dispatch.helperEnableDeclaration!=this
                ||source!=dispatch.savedEnable||source.originalInputPublication()!=original||source.farm()!=farm||source.helperIdentity()!=helper
                ||source.maintenanceToken()!=dispatch.token||actual.registryIdentity()!=moduleManager||actual.parent()!=helper)return false;
            claimed=true;saved=source;start=actual;checkpoint=source.originalCheckpointIdentity();
            return checkpoint!=null&&actual.startingCurrent()&&actual.startedFrom(source.originalDisabledLifecycle());
        }
        private boolean savedClaimReturnedCurrent(){return claimed&&!revoked&&saved!=null&&start!=null&&dispatch==retainedLoadoutDispatch
            &&dispatch.helperEnableDeclaration==this&&saved==dispatch.savedEnable&&saved.beforeCurrent(start)
            &&saved.originalCheckpointIdentity()==checkpoint&&original!=null&&original.nativeHistoryCurrent();}
        private boolean observeSavedCompleted(ModuleManager.OwnEnableReceipt actual,FarmMaintenanceCoordinator.SavedMaintenanceEnableSource source){
            if(!claimed||revoked||completed!=null||actual==null||source!=saved||dispatch!=retainedLoadoutDispatch||dispatch.helperEnableDeclaration!=this
                ||actual.actualStart()!=start||actual.parent()!=helper||!actual.current()||checkpoint==null||source.originalCheckpointIdentity()!=checkpoint
                ||!moduleManager.ownEnableMatches(actual,source.originalDisabledLifecycle(),helper)||dispatch.originalPause==null
                ||!dispatch.originalPause.acceptedNormalPauseProof()){revoked=true;return false;}
            completed=actual;validated=true;return true;
        }
        boolean matchesCompletedEnable(ModuleManager.OwnEnableReceipt actual){return validated&&!revoked&&completed==actual&&actual!=null
            &&actual.actualStart()==start&&actual.parent()==helper&&actual.current()&&saved!=null&&saved.originalCheckpointIdentity()==checkpoint;}
    }
    /** Actual normal G7 handoff retains the original native pause, checkpoint and typed first enable. */
    public static final class RootInputOwnershipPublication {
        private final RootRetainedLoadoutDispatch dispatch;private final ModuleManager.LifecycleSnapshot lifecycle;
        private final ScriptingInputLock.ControlSnapshot before;private boolean revoked,claimed,publicationClaimed;private RootOriginalInputInvocation originalInvocation;
        private RootInputOwnershipPublication(RootRetainedLoadoutDispatch dispatch){this.dispatch=dispatch;lifecycle=moduleManager.lifecycleSnapshot();
            before=dispatch.originalPublication==null||dispatch.originalPublication.input==null?null:dispatch.originalPublication.input.control();}
        public RootOriginalInputPublication originalInputPublication(){return dispatch.originalPublication;}
        public Object sourceDeclarationIdentity(){return dispatch.helperEnableDeclaration;}
        public Object sourceDispatchIdentity(){return dispatch;}
        public dev.veltraph.client.farming.module.FarmModule farm(){return dispatch.farm;}
        public Module helperIdentity(){return dispatch.helper;}
        public dev.veltraph.client.rewarp.FarmMaintenanceLease.Token maintenanceToken(){return dispatch.token;}
        public Object originalPauseCall(){return dispatch.originalPause;}
        public Object originalCheckpointIdentity(){return dispatch.helperEnableDeclaration.checkpoint;}
        public Object rootTimingIdentity(){return dispatch.frame;}
        public boolean claimOriginalInputHandoff(RootOriginalInputInvocation actual){
            if(claimed||revoked||actual==null||retainedInputPublication!=this||dispatch!=retainedLoadoutDispatch||!actual.matchesRetainedHelper(this))return false;
            claimed=true;originalInvocation=actual;
            try{var declared=dispatch.helperEnableDeclaration;
                if(declared.revoked||!declared.validated||declared.completed!=dispatch.enable||dispatch.originalPause==null
                    ||!dispatch.originalPause.acceptedNormalPauseProof()||!dispatch.readyToPublishInput()||dispatch.originalPublication==null
                    ||!dispatch.originalPublication.nativeHistoryCurrent()||!actual.matchesRetainedHelper(this)||retainedInputPublication!=this){revoked=true;return false;}return true;
            }catch(RuntimeException|Error unknown){revoked=true;return false;}
        }
        private boolean actualCorePublicationCaller(){return StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).walk(frames->{
            var callers=frames.filter(f->f.getDeclaringClass()!=RootInputOwnershipPublication.class).limit(2).toList();
            return callers.size()==2&&callers.get(0).getDeclaringClass()==ScriptingInputLock.class
                &&callers.get(0).getMethodName().equals("publishActualRootOwnership")
                &&callers.get(1).getDeclaringClass()==VeltraphBootstrap.class
                &&callers.get(1).getMethodName().equals("publishRetainedHelperOriginalInput");
        });}
        public boolean beforeCurrent(ScriptingInputLock.ControlSnapshot actual,boolean active,String owner){
            if(publicationClaimed||!claimed||revoked||originalInvocation==null||retainedInputPublication!=this){revoked=true;return false;}
            publicationClaimed=true; // Consume the actual Core setter once BEFORE source/native/provider callbacks.
            try{
                boolean valid=actualCorePublicationCaller()&&actual!=null&&before!=null&&actual.sameHistory(before)
                    &&before.publicationCurrent()&&originalInvocation.matchesRetainedHelper(this)&&dispatch.readyToPublishInput()
                    &&active&&dispatch.helper.id().equals(owner)&&moduleManager.lifecycleCurrent(lifecycle)
                    &&actual.sameHistory(before)&&before.publicationCurrent()&&originalInvocation.matchesRetainedHelper(this)
                    &&farmMaintenance.retainedOriginalDeadlineCurrent(dispatch.farm,dispatch.helper,dispatch.token,dispatch);
                if(!valid)revoked=true;return valid;
            }catch(RuntimeException|Error unknown){revoked=true;return false;}
        }
        public boolean afterCurrent(boolean active,String owner){return publicationClaimed&&!revoked&&retainedInputPublication==this&&dispatch==retainedLoadoutDispatch&&dispatch.committed&&!dispatch.revoked
            &&active&&dispatch.helper.id().equals(owner)&&moduleManager.lifecycleCurrent(lifecycle)&&dispatch.root.currentRetained(dispatch);}
    }
    private static void dispatchEarlyRetainedLoadout(dev.veltraph.client.garden.pests.mc.McPestEarlyLoadoutObservation.TimingFrame observed){
        if(observed==null||earlyRetainedLoadout==null||observed.desired()==dev.veltraph.client.garden.pests.PestEarlyLoadoutPolicy.Desired.UNKNOWN||!observed.currentOriginal()
            ||!config.get(dev.veltraph.client.config.PestLoadoutConfig.PEST_LOADOUT_SWAP)||farmMaintenance.peekPendingIntentForRoot()!=null)return;
        if(config.get(dev.veltraph.client.config.PestLoadoutConfig.PEST_NAMED_LOADOUT_BINDINGS).isBlank()
            ||config.get(dev.veltraph.client.config.PestLoadoutConfig.PEST_RETAINED_LOADOUT_CONTRACTS).isBlank())return;
        String target=observed.desired()==dev.veltraph.client.garden.pests.PestEarlyLoadoutPolicy.Desired.ALT?config.get(dev.veltraph.client.config.PestLoadoutConfig.PEST_PEST_LOADOUT):config.get(dev.veltraph.client.config.PestLoadoutConfig.PEST_FARMING_LOADOUT);
        if(!farmMaintenance.retainedSelectionNeeded(observed.farm(),target))return;
        if(retainedLoadoutDispatch!=null)retainedLoadoutDispatch.revoked=true;
        var dispatch=new RootRetainedLoadoutDispatch(observed);retainedLoadoutDispatch=dispatch;
        if(!earlyRetainedLoadout.arm(dispatch)){dispatch.revoked=true;retainedLoadoutDispatch=null;return;}
        // Existing public activate queues data only; the real Root dispatcher below claims the actual Guard transaction.
        farmMaintenance.activate(earlyRetainedLoadout,()->false);
        var intent=farmMaintenance.peekPendingIntentForRoot();
        if(intent==null||intent.helperIdentity()!=earlyRetainedLoadout||!intent.requestContextCurrent()||!dispatch.initialCurrent(earlyRetainedLoadout,farmMaintenance)){
            dispatch.revoked=true;retainedLoadoutDispatch=null;return;
        }
        dispatch.intent=intent;dispatch.requestIdentity=intent.requestIdentity();
    }
    public static dev.veltraph.client.garden.pests.mc.McPestEarlyLoadoutModule earlyRetainedLoadout(){return earlyRetainedLoadout;}

    private static RootNamedLoadoutSettingsSource rootNamedLoadoutSettingsSource;
    /** Actual Root-created settings origin; an arbitrary supplier with copied values cannot qualify. */
    public static final class RootNamedLoadoutSettingsSource implements java.util.function.Supplier<dev.veltraph.client.garden.loadout.NamedLoadoutFlow.Settings> {
        private final ConfigService originalStore;
        private RootNamedLoadoutSettingsSource(ConfigService originalStore){this.originalStore=originalStore;}
        @Override public dev.veltraph.client.garden.loadout.NamedLoadoutFlow.Settings get(){return dev.veltraph.client.garden.pests.PestBindings.read(
                        config.get(dev.veltraph.client.config.PestLoadoutConfig.PEST_NAMED_LOADOUT_BINDINGS),
                        dev.veltraph.client.garden.loadout.NamedLoadoutFlow.Settings.class);}
        /** Source identity only; its caller must also retain the original whole scalar snapshot. */
        public boolean affiliates(ConfigService actualStore){return this==rootNamedLoadoutSettingsSource&&originalStore==actualStore&&actualStore==config;}
    }
    private static RootMaintenanceControlSource rootMaintenanceControlSource;
    /** Privately created by the actual Root installation; no caller Boolean can qualify a source. */
    public static final class RootMaintenanceControlSource implements java.util.function.BooleanSupplier {
        private RootMaintenanceControlSource(){}
        @Override public boolean getAsBoolean(){return (combatAutoHeal!=null&&combatAutoHeal.ownsControl())
                ||(hydra!=null&&hydra.hasActiveLocalReaction())||galateaWorkflows.active();}
        public boolean affiliates(Object actualCoordinator){return this==rootMaintenanceControlSource&&actualCoordinator==farmMaintenance;}
        private boolean actualPauseCaller(){return StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).walk(frames->{
            var caller=frames.skip(2).findFirst().orElse(null);return caller!=null&&caller.getDeclaringClass()==FarmMaintenanceCoordinator.class
                &&caller.getMethodName().equals("startTransactionOriginal");});}
        public RootOriginalNativeMaintenancePauseInvocation beginOriginalMaintenancePause(FarmMaintenanceCoordinator.SavedMaintenancePauseCall call){
            return actualPauseCaller()&&call!=null&&affiliates(call.coordinatorIdentity())?beginSavedMaintenanceOriginalPause(call):null;}
        public void finishOriginalMaintenanceSuspend(RootOriginalNativeMaintenancePauseInvocation actual){
            if(actualPauseCaller()&&actual!=null&&affiliates(actual.pause.coordinatorIdentity()))finishSavedMaintenanceOriginalSuspend(actual);}
        public void finishOriginalMaintenancePause(RootOriginalNativeMaintenancePauseInvocation actual){
            if(actualPauseCaller()&&actual!=null&&affiliates(actual.pause.coordinatorIdentity()))finishSavedMaintenanceOriginalPause(actual);}
        public void closeOriginalMaintenancePause(RootOriginalNativeMaintenancePauseInvocation actual){
            if(actualPauseCaller()&&actual!=null&&affiliates(actual.pause.coordinatorIdentity()))closeSavedMaintenanceOriginalPause(actual);}

        private boolean actualResumeCaller(){return StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).walk(stream->{var caller=stream.skip(2).findFirst().orElse(null);
            return caller!=null&&caller.getDeclaringClass()==FarmMaintenanceCoordinator.class&&caller.getMethodName().equals("resume");});}
        public RootSavedMaintenanceResumeInvocation beginOriginalResume(FarmMaintenanceCoordinator.SavedMaintenanceResumeCall call){return actualResumeCaller()
            &&call!=null&&affiliates(call.coordinatorIdentity())?beginSavedMaintenanceOriginalResume(call):null;}
        public boolean publishOriginalResume(FarmMaintenanceCoordinator.SavedMaintenanceResumeCall call,RootSavedMaintenanceResumeInvocation actual){return actualResumeCaller()
            &&call!=null&&affiliates(call.coordinatorIdentity())&&actual!=null&&actual.matchesResume(call)&&publishSavedMaintenanceResumeOriginalInput(call,actual);}
        public void closeOriginalResume(RootSavedMaintenanceResumeInvocation actual){if(actualResumeCaller()&&actual!=null)closeSavedMaintenanceOriginalResume(actual);}
        private boolean actualRetainedResumeCaller(){return StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).walk(stream->{var caller=stream.skip(2).findFirst().orElse(null);
            return caller!=null&&caller.getDeclaringClass()==FarmMaintenanceCoordinator.class&&caller.getMethodName().equals("finishRetainedLoadout");});}
        public RootRetainedMaintenanceResumeInvocation beginOriginalRetainedResume(FarmMaintenanceCoordinator.RetainedLoadoutOperation call){return actualRetainedResumeCaller()
            &&call!=null&&affiliates(call.coordinatorIdentity())?beginRetainedMaintenanceOriginalResume(call):null;}
        public boolean publishOriginalRetainedResume(FarmMaintenanceCoordinator.RetainedLoadoutOperation call,RootRetainedMaintenanceResumeInvocation actual){return actualRetainedResumeCaller()
            &&call!=null&&affiliates(call.coordinatorIdentity())&&actual!=null&&actual.matchesRetained(call)&&publishRetainedMaintenanceResumeOriginalInput(call,actual);}
        public void closeOriginalRetainedResume(RootRetainedMaintenanceResumeInvocation actual){if(actualRetainedResumeCaller()&&actual!=null)closeRetainedMaintenanceOriginalResume(actual);}
        private boolean actualRollbackCaller(){return StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).walk(stream->{var caller=stream.skip(2).findFirst().orElse(null);
            return caller!=null&&caller.getDeclaringClass()==FarmMaintenanceCoordinator.class&&caller.getMethodName().equals("rollbackOriginal");});}
        public RootSavedMaintenanceRollbackInvocation beginOriginalRollback(FarmMaintenanceCoordinator.SavedMaintenanceRollbackCall call){return actualRollbackCaller()
            &&call!=null&&affiliates(call.coordinatorIdentity())?beginSavedMaintenanceOriginalRollback(call):null;}
        public boolean publishOriginalRollback(FarmMaintenanceCoordinator.SavedMaintenanceRollbackCall call,RootSavedMaintenanceRollbackInvocation actual){return actualRollbackCaller()
            &&call!=null&&affiliates(call.coordinatorIdentity())&&actual!=null&&actual.matchesRollback(call)&&publishSavedMaintenanceRollbackOriginalInput(call,actual);}
        public void closeOriginalRollback(RootSavedMaintenanceRollbackInvocation actual){if(actualRollbackCaller()&&actual!=null)closeSavedMaintenanceOriginalRollback(actual);}
        public boolean originalFailedRollbackDispatchCurrent(FarmMaintenanceCoordinator coordinator){return affiliates(coordinator)&&originalGameplayInvocationCurrent();}
        private boolean actualFailedRollbackCaller(){return StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).walk(stream->{var caller=stream.skip(2).findFirst().orElse(null);
            return caller!=null&&caller.getDeclaringClass()==FarmMaintenanceCoordinator.class&&caller.getMethodName().equals("finishFailedRollbackTerminal");});}
        public dev.veltraph.client.service.mc.RootNativeHistory.SavedMaintenanceRollbackCleanupCompletion finishFailedOriginalRollback(FarmMaintenanceCoordinator.SavedMaintenanceRollbackTerminal terminal){
            return actualFailedRollbackCaller()&&terminal!=null&&affiliates(terminal.coordinatorIdentity())&&originalGameplayInvocationCurrent()
                ?finishSavedMaintenanceFailedRollback(terminal):null;
        }



    }
    private static final class OwnedUseControlSnapshot {
        final RootMaintenanceControlSource source;final RootRotationAuthorityProvider provider;
        final RootInputReleaseRegistration installed;final ModuleManager registry;
        final ModuleManager.LifecycleSnapshot lifecycle;private ModuleManager.LifecycleSnapshot effectiveLifecycle;private RootOwnedControlPassiveContinuation passiveContinuation;final ConfigService store;
        final dev.veltraph.client.config.MapConfigService.StoredStateSnapshot settings;
        final Object healer,hydraSource,responder,galatea,scheduler,foraging,cycle,rewarp;final Object expectedMaintenance;
        private OwnedUseControlSnapshot(Object expectedMaintenance){this.expectedMaintenance=expectedMaintenance;
            source=rootMaintenanceControlSource;provider=rootRotationProvider;installed=rootInputReleaseRegistration;
            registry=moduleManager;lifecycle=registry.lifecycleSnapshot();effectiveLifecycle=lifecycle;store=config;
            settings=store instanceof dev.veltraph.client.config.MapConfigService m?m.storedStateSnapshot():store instanceof dev.veltraph.client.config.JsonConfigService j?j.storedStateSnapshot():null;
            healer=combatAutoHeal;hydraSource=hydra;responder=hydra==null?null:hydra.nativeResponderIdentity();galatea=galateaWorkflows;
            scheduler=scriptScheduler;foraging=foragingMaintenance;cycle=farmCycle;rewarp=autoRewarp;
        }
        private boolean current(){return currentAt(effectiveLifecycle);}
        private boolean currentAt(ModuleManager.LifecycleSnapshot retained){
            if(source==null||source!=rootMaintenanceControlSource||provider==null||provider!=rootRotationProvider||!provider.registeredCurrent()
                ||installed==null||installed!=rootInputReleaseRegistration||!installed.current(installed.input,provider.client)
                ||registry!=moduleManager||store!=config||retained==null||!retained.known()||!registry.lifecycleCurrent(retained)||settings==null||!settings.current()
                ||healer!=combatAutoHeal||hydraSource!=hydra||galatea!=galateaWorkflows||scheduler!=scriptScheduler||foraging!=foragingMaintenance||cycle!=farmCycle||rewarp!=autoRewarp)return false;
            if(combatAutoHeal!=null&&combatAutoHeal.ownsControl())return false;
            if(hydra!=null&&(!(responder instanceof dev.veltraph.client.hydra.mc.McHydraResponder nativeResponder)
                ||hydra.nativeResponderIdentity()!=responder||!nativeResponder.nativeIdleCurrent()||!hydra.nativeIdleCurrent(responder)))return false;
            if(galateaWorkflows==null||galateaWorkflows.active())return false;
            if((foragingMaintenance!=null&&foragingMaintenance.active())||svenWarpLifecycle.active()||ghostWarpLifecycle.active()||ghostTransportQuiescent||bottleTransportQuiescent
                ||(scriptScheduler!=null&&scriptScheduler.state()!=dev.veltraph.client.scheduling.SourceRestRuntime.State.IDLE)
                ||(farmCycle!=null&&farmCycle.active())||(autoRewarp!=null&&autoRewarp.state()!=dev.veltraph.client.rewarp.AutoRewarpState.IDLE))return false;
            if(expectedMaintenance!=null)return farmMaintenance==expectedMaintenance&&farmMaintenance.active();
            return farmMaintenance==null||!farmMaintenance.active();
        }
        private boolean originalStandardCleanupIdentitiesCurrent(){return source==rootMaintenanceControlSource&&provider==rootRotationProvider
            &&installed==rootInputReleaseRegistration&&registry==moduleManager&&store==config&&healer==combatAutoHeal&&hydraSource==hydra
            &&galatea==galateaWorkflows&&scheduler==scriptScheduler&&foraging==foragingMaintenance&&cycle==farmCycle&&rewarp==autoRewarp;}
        private boolean originalStandardCleanupCurrent(Module exact,ModuleManager.PendingOwnDisable pending){
            var retained=effectiveLifecycle;
            if(exact==null||pending==null||pending.registryIdentity()!=registry||!pending.current(exact)
                ||registry.pendingActualOwnDisable(exact).orElse(null)!=pending||!registry.pendingOwnDisableFromMatches(retained,exact))return false;
            if(source==null||source!=rootMaintenanceControlSource||provider==null||provider!=rootRotationProvider||!provider.registeredCurrent()
                ||installed==null||installed!=rootInputReleaseRegistration||!installed.current(installed.input,provider.client)
                ||registry!=moduleManager||store!=config||retained==null||!retained.known()||!registry.pendingOwnDisableFromMatches(retained,exact)||settings==null||!settings.current()
                ||healer!=combatAutoHeal||hydraSource!=hydra||galatea!=galateaWorkflows||scheduler!=scriptScheduler||foraging!=foragingMaintenance||cycle!=farmCycle||rewarp!=autoRewarp)return false;
            if(combatAutoHeal!=null&&combatAutoHeal.ownsControl())return false;
            if(hydra!=null&&(!(responder instanceof dev.veltraph.client.hydra.mc.McHydraResponder nativeResponder)
                ||hydra.nativeResponderIdentity()!=responder||!nativeResponder.nativeIdleCurrent()||!hydra.nativeIdleCurrent(responder)))return false;
            if(galateaWorkflows==null||galateaWorkflows.active())return false;
            if((foragingMaintenance!=null&&foragingMaintenance.active())||svenWarpLifecycle.active()||ghostWarpLifecycle.active()||ghostTransportQuiescent||bottleTransportQuiescent
                ||(scriptScheduler!=null&&scriptScheduler.state()!=dev.veltraph.client.scheduling.SourceRestRuntime.State.IDLE)
                ||(farmCycle!=null&&farmCycle.active())||(autoRewarp!=null&&autoRewarp.state()!=dev.veltraph.client.rewarp.AutoRewarpState.IDLE))return false;
            return expectedMaintenance==null&&(farmMaintenance==null||!farmMaintenance.active())
                &&pending.current(exact)&&registry.pendingActualOwnDisable(exact).orElse(null)==pending
                &&registry.pendingOwnDisableFromMatches(retained,exact);
        }
        private boolean currentRetained(RootRetainedLoadoutDispatch actual){
            if(actual==null||actual.activeLifecycle==null||actual.root!=this||source==null||source!=rootMaintenanceControlSource||provider==null||provider!=rootRotationProvider||!provider.registeredCurrent()
                ||installed==null||installed!=rootInputReleaseRegistration||!installed.current(installed.input,provider.client)
                ||registry!=moduleManager||store!=config||!actual.activeLifecycle.known()||!actual.lifecycleCurrent()||settings==null||!settings.current()
                ||healer!=combatAutoHeal||hydraSource!=hydra||galatea!=galateaWorkflows||scheduler!=scriptScheduler||foraging!=foragingMaintenance||cycle!=farmCycle||rewarp!=autoRewarp)return false;
            if(combatAutoHeal!=null&&combatAutoHeal.ownsControl())return false;
            if(hydra!=null&&(!(responder instanceof dev.veltraph.client.hydra.mc.McHydraResponder nativeResponder)
                ||hydra.nativeResponderIdentity()!=responder||!nativeResponder.nativeIdleCurrent()||!hydra.nativeIdleCurrent(responder)))return false;
            if(galateaWorkflows==null||galateaWorkflows.active())return false;
            if((foragingMaintenance!=null&&foragingMaintenance.active())||svenWarpLifecycle.active()||ghostWarpLifecycle.active()||ghostTransportQuiescent||bottleTransportQuiescent
                ||(scriptScheduler!=null&&scriptScheduler.state()!=dev.veltraph.client.scheduling.SourceRestRuntime.State.IDLE)
                ||(farmCycle!=null&&farmCycle.active())||(autoRewarp!=null&&autoRewarp.state()!=dev.veltraph.client.rewarp.AutoRewarpState.IDLE))return false;
            return actual==retainedLoadoutDispatch&&actual.root==this&&actual.committed&&!actual.revoked&&farmMaintenance!=null&&farmMaintenance.active()&&farmMaintenance.tokenFor(actual.helper)==actual.token
                &&farmMaintenance.retainedOriginalDeadlineCurrent(actual.farm,actual.helper,actual.token,actual);
        }

        private boolean greenhouseGuardCleanupCurrent(){
            if(source==null||source!=rootMaintenanceControlSource||provider==null||provider!=rootRotationProvider||!provider.registeredCurrent()
                ||installed==null||installed!=rootInputReleaseRegistration||!installed.current(installed.input,provider.client)
                ||registry!=moduleManager||store!=config||effectiveLifecycle==null||!effectiveLifecycle.known()||!registry.lifecycleCurrent(effectiveLifecycle)||settings==null||!settings.greenhouseGuardCleanupCurrent()
                ||healer!=combatAutoHeal||hydraSource!=hydra||galatea!=galateaWorkflows||scheduler!=scriptScheduler||foraging!=foragingMaintenance||cycle!=farmCycle||rewarp!=autoRewarp)return false;
            if(combatAutoHeal!=null&&combatAutoHeal.ownsControl())return false;
            if(hydra!=null&&(!(responder instanceof dev.veltraph.client.hydra.mc.McHydraResponder nativeResponder)
                ||hydra.nativeResponderIdentity()!=responder||!nativeResponder.nativeIdleCurrent()||!hydra.nativeIdleCurrent(responder)))return false;
            if(galateaWorkflows==null||galateaWorkflows.active())return false;
            if((foragingMaintenance!=null&&foragingMaintenance.active())||svenWarpLifecycle.active()||ghostWarpLifecycle.active()||ghostTransportQuiescent||bottleTransportQuiescent
                ||(scriptScheduler!=null&&scriptScheduler.state()!=dev.veltraph.client.scheduling.SourceRestRuntime.State.IDLE)
                ||(farmCycle!=null&&farmCycle.active())||(autoRewarp!=null&&autoRewarp.state()!=dev.veltraph.client.rewarp.AutoRewarpState.IDLE))return false;
            if(expectedMaintenance!=null)return farmMaintenance==expectedMaintenance&&farmMaintenance.active();
            return farmMaintenance==null||!farmMaintenance.active();
        }
    }
    /** Existing exact Root control snapshot projected through one genuine passive operation; no new snapshot is minted. */
    public static final class RootOwnedControlPassiveContinuation {
        private final OwnedUseControlSnapshot snapshot;private final RootPassiveTransition transition;
        private final ModuleManager.LifecycleSnapshot before;private final RootOriginalInputPublication publication;
        private final RootOriginalNativeEnrollment enrollment;private final RootOriginalNativeInstallation installation;
        private final Thread thread=Thread.currentThread();private ModuleManager.LifecycleSnapshot after;
        private boolean prepared,claimed,revoked;
        private RootOwnedControlPassiveContinuation(OwnedUseControlSnapshot snapshot,RootPassiveTransition transition){
            this.snapshot=snapshot;this.transition=transition;before=snapshot.effectiveLifecycle;
            publication=transition.publication;enrollment=publication.enrollment;installation=transition.installation;
        }
        public boolean rawCurrent(){return !claimed&&!revoked&&Thread.currentThread()==thread&&snapshot.passiveContinuation==this
            &&snapshot.effectiveLifecycle==before&&snapshot.registry==transition.registry&&transition.publication==publication
            &&publication==originalInputPublication&&!publication.revoked&&publication.enrollment==enrollment&&enrollment==originalNativeEnrollment
            &&transition.installation==installation&&installation==originalNativeInstallation
            &&snapshot.source==rootMaintenanceControlSource&&snapshot.provider==rootRotationProvider&&snapshot.installed==rootInputReleaseRegistration
            &&snapshot.store==config&&snapshot.healer==combatAutoHeal&&snapshot.hydraSource==hydra&&snapshot.galatea==galateaWorkflows
            &&snapshot.scheduler==scriptScheduler&&snapshot.foraging==foragingMaintenance&&snapshot.cycle==farmCycle&&snapshot.rewarp==autoRewarp;}
        public boolean prepare(){
            if(prepared||!rawCurrent()){revoked=true;return false;}
            if(!transition.normalReturnedCurrent(transition.client)||!snapshot.registry.lifecycleSameHistory(before,transition.before)){
                revoked=true;return false;}
            var next=transition.normalReturnedLifecycle();
            if(next==null||!snapshot.currentAt(next)||!rawCurrent()||!transition.normalReturnedCurrent(transition.client)){
                revoked=true;return false;}
            after=next;prepared=true;return true;
        }
        public boolean commit(){
            if(!prepared||after==null||!transition.passiveDeclarationCommitWindowCurrent()||!rawCurrent()){revoked=true;return false;}
            claimed=true;snapshot.effectiveLifecycle=after;snapshot.passiveContinuation=null;return true;
        }
    }
    public static RootOwnedControlPassiveContinuation captureOwnedControlPassiveContinuation(Object exact,RootPassiveTransition transition){
        if(!(exact instanceof OwnedUseControlSnapshot snapshot)||transition==null)return null;
        if(snapshot.passiveContinuation!=null){snapshot.passiveContinuation.revoked=true;return null;}
        if(transition.publication==null||transition.publication!=originalInputPublication||!transition.sourceBeforeCurrent(transition.client)
            ||snapshot.registry!=transition.registry||!snapshot.registry.lifecycleSameHistory(snapshot.effectiveLifecycle,transition.before)
            ||!snapshot.current()||!transition.sourceBeforeCurrent(transition.client))return null;
        var next=new RootOwnedControlPassiveContinuation(snapshot,transition);snapshot.passiveContinuation=next;return next.rawCurrent()?next:null;
    }

    /** Captures only known actual Root source fields; generic installations/foreign source callbacks remain unavailable. */
    public static Object ownedUseControlSnapshot(Object expectedMaintenance){
        var provider=rootRotationProvider;if(provider==null||!provider.client.isSameThread()||rootMaintenanceControlSource==null)return null;
        var actual=new OwnedUseControlSnapshot(expectedMaintenance);return actual.current()?actual:null;
    }
    /** Callback-free source/runtime fields only. No authority/provider/config getter is invoked. */
    public static boolean ownedUseControlCurrent(Object exact){return exact instanceof OwnedUseControlSnapshot s&&s.current();}
    /** Original private Root control lineage for one Greenhouse guard restoration; no new action authority. */
    public static boolean greenhouseGuardCleanupControlCurrent(Object exact,Object exactCoordinator){
        return exactCoordinator!=null&&exact instanceof OwnedUseControlSnapshot s&&exactCoordinator==farmMaintenance
            &&s.expectedMaintenance==exactCoordinator&&s.greenhouseGuardCleanupCurrent();
    }

    private static RootRotationAuthorityProvider rootRotationProvider;
    private static RootInputReleaseRegistration rootInputReleaseRegistration;
    /** Exact actual SDK phase before original camera metadata mutation. Binds installation identities only. */
    public static void observeOriginalNativeCameraPhase(dev.veltraph.client.service.mc.RootNativeCameraPhase.Phase actual){
        try{
            var client=Minecraft.getInstance();
            boolean originalEntry=StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).walk(stream->{var caller=stream.skip(1).findFirst().orElse(null);
                return caller!=null&&caller.getDeclaringClass()==dev.veltraph.client.service.mc.RootNativeCameraPhase.class&&caller.getMethodName().equals("enter");});
            if(!originalEntry||actual==null||!actual.originalEntryCurrent(client))return;
            var original=rootInputReleaseRegistration;
            if(original!=null&&original.current(original.actualInput(),client)){
                dev.veltraph.client.service.mc.McInputService.registerCanonicalRootRelease(original);
                bindOriginalNativeInstallationKeys(client);
            }
        }catch(RuntimeException|Error unavailable){}
    }
    private static void bindOriginalRootInputReleaseAtNativePhase(Minecraft client){
        var original=rootInputReleaseRegistration;
        if(original!=null&&client.isSameThread()){
            dev.veltraph.client.service.mc.McInputService.registerCanonicalRootRelease(original);
            try{bindOriginalNativeInstallationKeys(client);}catch(RuntimeException|Error unavailable){}
            // Native history is admitted only by the genuine Guard fresh-request boundary; no world/GUI seed here.
        }
    }
    private static RootOriginalControlStartup originalControlStartup;
    /** Exact original initialization lineage; known constructor registration is observed on normal return only. */
    public static final class RootOriginalControlStartup {
        private ScriptingInputLock.ControlSnapshot control=ScriptingInputLock.originalPublicationSnapshot();
        private ScriptingInputLock.ControlSnapshot pending;private final Thread thread=Thread.currentThread();
        private boolean completed,revoked;private RootOriginalStartupConstruction construction;
        private RootOriginalControlStartup(){}
        public boolean actualInitializationEntry(){return !completed&&!revoked&&this==originalControlStartup&&Thread.currentThread()==thread
            &&StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).walk(stream->{var frames=stream.toList();
                return frames.size()>2&&frames.get(1).getDeclaringClass()==ScriptingInputLock.class&&frames.get(1).getMethodName().equals("beginOriginalRootInitialization")
                    &&frames.get(2).getDeclaringClass()==VeltraphBootstrap.class&&frames.get(2).getMethodName().equals("init");});}
        public boolean actualInitializationExit(){return completed&&this==originalControlStartup&&Thread.currentThread()==thread
            &&StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).walk(stream->{var frames=stream.toList();
                return frames.size()>3&&frames.get(1).getDeclaringClass()==ScriptingInputLock.class&&frames.get(1).getMethodName().equals("finishOriginalRootInitialization")
                    &&frames.get(2).getDeclaringClass()==VeltraphBootstrap.class&&frames.get(2).getMethodName().equals("finishOriginalControlStartup")
                    &&frames.get(3).getDeclaringClass()==VeltraphBootstrap.class&&frames.get(3).getMethodName().equals("init");});}
        public boolean actualReleaseInstallationCapture(){return completed&&!revoked&&this==originalControlStartup&&Thread.currentThread()==thread
            &&StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).walk(stream->{var frames=stream.toList();
                return frames.size()>3&&frames.get(1).getDeclaringClass()==ScriptingInputLock.class&&frames.get(1).getMethodName().equals("originalReleaseInstallationFrom")
                    &&frames.get(2).getDeclaringClass()==VeltraphBootstrap.class&&frames.get(2).getMethodName().equals("finishOriginalControlStartup")
                    &&frames.get(3).getDeclaringClass()==VeltraphBootstrap.class&&frames.get(3).getMethodName().equals("init");});}
        public boolean actualGuardInstallationCapture(ModuleActivationGuard actual){return completed&&!revoked&&this==originalControlStartup&&Thread.currentThread()==thread&&actual==guard
            &&StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).walk(stream->{var frames=stream.toList();
                return frames.size()>3&&frames.get(1).getDeclaringClass()==ModuleActivationGuard.class&&frames.get(1).getMethodName().equals("sealOriginalActivationSource")
                    &&frames.get(2).getDeclaringClass()==VeltraphBootstrap.class&&frames.get(2).getMethodName().equals("finishOriginalControlStartup")
                    &&frames.get(3).getDeclaringClass()==VeltraphBootstrap.class&&frames.get(3).getMethodName().equals("init");});}
        public boolean actualStopBindingCapture(ModuleManager actual){return completed&&!revoked&&this==originalControlStartup&&Thread.currentThread()==thread&&actual==moduleManager
            &&StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).walk(stream->{var frames=stream.toList();
                return frames.size()>3&&frames.get(1).getDeclaringClass()==ModuleManager.class&&frames.get(1).getMethodName().equals("originalStopBindingsFrom")
                    &&frames.get(2).getDeclaringClass()==VeltraphBootstrap.class&&frames.get(2).getMethodName().equals("finishOriginalControlStartup")
                    &&frames.get(3).getDeclaringClass()==VeltraphBootstrap.class&&frames.get(3).getMethodName().equals("init");});}
        private boolean actualConstructorCurrent(){
            if(completed||revoked||this!=originalControlStartup||Thread.currentThread()!=thread)return false;
            return StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).walk(stream->{
                var frames=stream.toList();int release=-1,constructor=-1,root=-1;
                for(int i=0;i<frames.size();i++){var f=frames.get(i);
                    if(f.getDeclaringClass()==ScriptingInputLock.class&&f.getMethodName().equals("onRelease")&&release<0)release=i;
                    if(f.getDeclaringClass()==dev.veltraph.client.service.mc.McInputService.class&&f.getMethodName().equals("<init>")&&constructor<0)constructor=i;
                    if(f.getDeclaringClass()==VeltraphBootstrap.class&&f.getMethodName().equals("init"))root=i;
                }
                if(release<0||constructor!=release+1||root!=constructor+3||construction==null||construction.startup!=this||construction.thread!=thread)return false;
                var parent=frames.get(constructor+1);var wrapper=frames.get(constructor+2);
                if(wrapper.getDeclaringClass()!=VeltraphBootstrap.class)return false;
                return construction.farm&&parent.getDeclaringClass()==McFarmServices.class&&parent.getMethodName().equals("create")&&wrapper.getMethodName().equals("createOriginalFarmServices")
                    ||!construction.farm&&parent.getDeclaringClass()==McFishingServices.class&&parent.getMethodName().equals("<init>")&&wrapper.getMethodName().equals("createOriginalFishingServices");
            });
        }
        public ScriptingInputLock.ControlSnapshot beforeActualRegistration(){
            if(pending!=null||!actualConstructorCurrent()||control==null||!control.publicationCurrent()||control.active()||control.ownerModuleId()!=null){revoked=true;return null;}
            pending=control;return pending;
        }
        public void registrationReturned(ScriptingInputLock.ControlSnapshot before,ScriptingInputLock.ControlSnapshot after){
            if(!actualConstructorCurrent()||before==null||before!=pending||after==null||!after.immediateReturnedSingleFrom(before)
                ||after.active()||after.ownerModuleId()!=null){revoked=true;pending=null;return;}
            control=after;construction.returnedRegistration=after;pending=null;
        }
    }
    private static final class RootOriginalStartupConstruction {
        final RootOriginalControlStartup startup;final Thread thread=Thread.currentThread();final boolean farm;
        ScriptingInputLock.ControlSnapshot returnedRegistration;
        private RootOriginalStartupConstruction(RootOriginalControlStartup startup,boolean farm){this.startup=startup;this.farm=farm;}
    }
    private static RootOriginalStartupConstruction beginOriginalStartupConstruction(boolean farm){
        var startup=originalControlStartup;if(startup==null||startup.completed)return null;
        var actual=new RootOriginalStartupConstruction(startup,farm);
        if(startup.construction!=null){startup.revoked=true;return null;}
        startup.construction=actual;return actual;
    }
    private static void finishOriginalStartupConstruction(RootOriginalStartupConstruction actual,boolean normal){
        if(actual==null)return;var startup=actual.startup;
        if(!normal||startup.construction!=actual||actual.returnedRegistration==null||actual.returnedRegistration!=startup.control
            ||!actual.returnedRegistration.publicationCurrent()||startup.pending!=null)startup.revoked=true;
        if(startup.construction==actual)startup.construction=null;
    }
    private static FarmServices createOriginalFarmServices(Minecraft client,dev.veltraph.client.service.ClockService clock,ConfigService store){
        RootOriginalStartupConstruction actual=null;
        try{actual=beginOriginalStartupConstruction(true);}catch(RuntimeException|Error unavailable){if(originalControlStartup!=null)originalControlStartup.revoked=true;}
        boolean normal=false;
        try{var services=McFarmServices.create(client,clock,store);normal=true;return services;}
        finally{try{finishOriginalStartupConstruction(actual,normal);}catch(RuntimeException|Error unavailable){if(originalControlStartup!=null)originalControlStartup.revoked=true;}}
    }
    private static McFishingServices createOriginalFishingServices(Minecraft client){
        RootOriginalStartupConstruction actual=null;
        try{actual=beginOriginalStartupConstruction(false);}catch(RuntimeException|Error unavailable){if(originalControlStartup!=null)originalControlStartup.revoked=true;}
        boolean normal=false;
        try{var services=new McFishingServices(client);normal=true;return services;}
        finally{try{finishOriginalStartupConstruction(actual,normal);}catch(RuntimeException|Error unavailable){if(originalControlStartup!=null)originalControlStartup.revoked=true;}}
    }
    private static void finishOriginalControlStartup(boolean normal){
        var startup=originalControlStartup;if(startup==null)return;
        try{
            var source=originalNativeEnrollment;
            boolean current=normal&&!startup.revoked&&startup.pending==null&&startup.construction==null&&startup.control!=null&&startup.control.publicationCurrent()
                &&!startup.control.active()&&startup.control.ownerModuleId()==null&&moduleManager!=null&&moduleManager.lifecycleSnapshot().noneEnabledCurrent(moduleManager);
            if(source!=null){if(current)source.idleControl=startup.control;else source.revoked=true;}
            if(!current)startup.revoked=true;
        }catch(RuntimeException|Error unavailable){startup.revoked=true;if(originalNativeEnrollment!=null)originalNativeEnrollment.revoked=true;}
        finally{
            startup.completed=true;
            try{if(!startup.revoked&&normal){var release=ScriptingInputLock.originalReleaseInstallationFrom(startup);var stops=moduleManager.originalStopBindingsFrom(startup);
                if(release!=null&&stops!=null){originalNativeInstallation=new RootOriginalNativeInstallation(rootInputReleaseRegistration,moduleManager,config,farmServices,release,stops,startup.control);
                    originalPassiveSource=new RootPrivatePassiveSource(originalNativeInstallation);}}}
            catch(RuntimeException|Error unavailable){originalNativeInstallation=null;}
            try{if(!startup.revoked&&normal&&guard!=null)guard.sealOriginalActivationSource(startup);}catch(RuntimeException|Error unavailable){originalActivationRegistration=null;}
            ScriptingInputLock.finishOriginalRootInitialization(startup);
        }
    }
    private static RootMaintenanceCoordinatorTick originalMaintenanceCoordinatorTick;
    private static final class RootMaintenanceCoordinatorTick {
        final RootOriginalGameplayTickInvocation gameplay;final FarmMaintenanceCoordinator coordinator=farmMaintenance;
        final Thread thread=Thread.currentThread();boolean revoked,closed;
        RootMaintenanceCoordinatorTick(RootOriginalGameplayTickInvocation gameplay){this.gameplay=gameplay;}
        boolean current(){return !revoked&&!closed&&originalMaintenanceCoordinatorTick==this&&Thread.currentThread()==thread
            &&gameplay!=null&&gameplay.current()&&coordinator==farmMaintenance;}
    }
    private static void tickOriginalFarmMaintenance(){
        RootMaintenanceCoordinatorTick actual=null;var gameplay=originalGameplayTickInvocation;
        if(originalMaintenanceCoordinatorTick!=null)originalMaintenanceCoordinatorTick.revoked=true;
        else if(gameplay!=null&&gameplay.current()){actual=new RootMaintenanceCoordinatorTick(gameplay);originalMaintenanceCoordinatorTick=actual;}
        try{farmMaintenance.tick();}
        finally{if(actual!=null){actual.closed=true;if(originalMaintenanceCoordinatorTick==actual)originalMaintenanceCoordinatorTick=null;}}
    }
    private static RootDeferredMaintenanceActivator originalDeferredMaintenanceActivator;
    private static RootSavedMaintenanceDeferredActivationCall savedMaintenanceDeferredActivation;
    private static final class RootDeferredMaintenanceActivator implements java.util.function.Function<Module,Boolean> {
        final Minecraft client;RootDeferredMaintenanceActivator(Minecraft client){this.client=client;}
        public Boolean apply(Module module){
            var tick=originalMaintenanceCoordinatorTick;
            if(this!=originalDeferredMaintenanceActivator||tick==null||!tick.current())return false;
            var source=farmMaintenance.pendingSavedMaintenanceTransactionForRoot(module);
            if(source==null||savedMaintenanceDeferredActivation!=null){if(savedMaintenanceDeferredActivation!=null)savedMaintenanceDeferredActivation.revoked=true;return false;}
            var actual=new RootSavedMaintenanceDeferredActivationCall(tick,source,module);savedMaintenanceDeferredActivation=actual;
            try{if(!actual.current())return false;
                if(!(gardenThresholdAdmission==null||gardenThresholdAdmission.permitsDeferred(module,
                client.player!=null&&client.level!=null&&client.getConnection()!=null?actualTrapTabRows().stream().map(dev.veltraph.client.game.LocationText::stripColorCodes).toList():java.util.List.of(),
                client.level!=null?actualPestSidebarRows(client):java.util.List.of())))return false;
                if(!actual.current())return false;
                return guard.start(module);
            }finally{actual.closed=true;if(savedMaintenanceDeferredActivation==actual)savedMaintenanceDeferredActivation=null;}
        }
    }
    private static final class RootSavedMaintenanceDeferredActivationCall {
        final RootMaintenanceCoordinatorTick tick;final FarmMaintenanceCoordinator.SavedMaintenanceEnableSource declaration;
        final Module selected;final RootOriginalInputPublication publication=originalInputPublication;
        final Thread thread=Thread.currentThread();ModuleActivationGuard.OriginalActivationStart request;boolean revoked,closed;
        RootSavedMaintenanceDeferredActivationCall(RootMaintenanceCoordinatorTick tick,FarmMaintenanceCoordinator.SavedMaintenanceEnableSource declaration,Module selected){
            this.tick=tick;this.declaration=declaration;this.selected=selected;}
        boolean current(){return !revoked&&!closed&&savedMaintenanceDeferredActivation==this&&Thread.currentThread()==thread&&tick.current()
            &&publication!=null&&publication==originalInputPublication&&!publication.revoked&&declaration.originalInputPublication()==publication
            &&declaration.helperIdentity()==selected&&farmMaintenance.pendingSavedMaintenanceTransactionForRoot(selected)==declaration;}
        boolean bindRequest(ModuleActivationGuard.OriginalActivationStart actual){
            if(request!=null||!current()||actual==null||actual.module()!=selected||actual.guardIdentity()!=guard||actual.registryIdentity()!=moduleManager
                ||!actual.entryCurrent()||!namedGuardCaller(RootDeferredMaintenanceActivator.class,"apply")){revoked=true;return false;}
            request=actual;return current()&&actual.originalBindingsCurrent();
        }
    }
    private static boolean namedGuardCaller(Class<?> expected,String method){return StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).walk(frames->{
        var all=frames.toList();for(int i=0;i+1<all.size();i++)if(all.get(i).getDeclaringClass()==ModuleActivationGuard.class&&all.get(i).getMethodName().equals("start"))
            return all.get(i+1).getDeclaringClass()==expected&&all.get(i+1).getMethodName().equals(method);return false;});}
    private static RootSavedMaintenanceIntentDispatch savedMaintenanceIntentDispatch;
    /** Private actual native Root callback request; the pending request remains data until first claimed here. */
    public static final class RootSavedMaintenanceIntentDispatch {
        private final RootOriginalGameplayTickInvocation gameplay;private final FarmMaintenanceCoordinator coordinator;
        private final FarmMaintenanceCoordinator.PendingMaintenanceIntent intent;private final Object requestIdentity;
        private final Module selected;private final RootOriginalInputPublication publication;private final RootOriginalNativeEnrollment enrollment;
        private final RootOriginalNativeInstallation installation;private final ModuleManager.LifecycleSnapshot before;
        private final ScriptingInputLock.ControlSnapshot control;private OwnedUseControlSnapshot runtime;
        private final Thread thread=Thread.currentThread();private ModuleActivationGuard.OriginalActivationStart guardRequest;
        private RootSavedMaintenanceTransactionInvocation transaction;private boolean claimed,guardEntered,revoked,closed;
        private RootSavedMaintenanceIntentDispatch(RootOriginalGameplayTickInvocation gameplay,FarmMaintenanceCoordinator.PendingMaintenanceIntent intent){
            this.gameplay=gameplay;this.intent=intent;coordinator=farmMaintenance;requestIdentity=intent.requestIdentity();selected=intent.helperIdentity();
            publication=originalInputPublication;enrollment=publication.enrollment;installation=originalNativeInstallation;before=publication.lifecycle;
            control=publication.input.control();
        }
        public boolean matchesIntent(FarmMaintenanceCoordinator.PendingMaintenanceIntent exact){return exact==intent&&requestIdentity==exact.requestIdentity()
            &&coordinator==exact.coordinatorIdentity()&&selected==exact.helperIdentity()&&Thread.currentThread()==thread
            &&savedMaintenanceIntentDispatch==this&&!closed&&!revoked;}
        private boolean contextCurrent(){return !closed&&!revoked&&savedMaintenanceIntentDispatch==this&&Thread.currentThread()==thread&&gameplay.current()
            &&coordinator==farmMaintenance&&installation!=null&&installation==originalNativeInstallation&&installation.currentFor(gameplay.client)
            &&publication==originalInputPublication&&!publication.revoked&&enrollment==originalNativeEnrollment&&publication.enrollment==enrollment
            &&control!=null&&control.publicationCurrent()&&publication.enable==originalEnableProof&&publication.enable.enableLineageCurrent();}
        private boolean beforeGuardCurrent(){return claimed&&contextCurrent()&&intent.claimedRequestContextCurrent(this)&&runtime!=null&&runtime.current()
            &&moduleManager.lifecycleCurrent(before)&&moduleManager.lifecycleSameHistory(before,publication.lifecycle)&&publication.nativeHistoryCurrent()
            &&contextCurrent();}
        private boolean bindRequest(ModuleActivationGuard.OriginalActivationStart actual){
            if(guardRequest!=null||!guardEntered||!beforeGuardCurrent()||actual==null||actual.module()!=selected||actual.guardIdentity()!=guard
                ||actual.registryIdentity()!=moduleManager||!actual.startedFrom(before)||!actual.entryCurrent()
                ||!namedGuardCaller(VeltraphBootstrap.class,"dispatchOriginalMaintenanceIntent")){revoked=true;return false;}
            guardRequest=actual;return beforeGuardCurrent()&&actual.originalBindingsCurrent();
        }
    }
    private static void dispatchOriginalMaintenanceIntent(Minecraft client){
        var gameplay=originalGameplayTickInvocation;
        if(gameplay==null||!gameplay.current()||client!=gameplay.client||farmMaintenance==null||unexpectedStopFrame.stopped())return;
        var intent=farmMaintenance.peekPendingIntentForRoot();var publication=originalInputPublication;
        if(intent==null||publication==null||publication.input==null||!intent.requestContextCurrent())return;
        if(savedMaintenanceIntentDispatch!=null){savedMaintenanceIntentDispatch.revoked=true;return;}
        var actual=new RootSavedMaintenanceIntentDispatch(gameplay,intent);savedMaintenanceIntentDispatch=actual;
        try{
            // First consume is before the real Guard's providers, policy, native reservation or helper callbacks.
            if(!intent.claimForRoot(actual))return;actual.claimed=true;
            if(actual.selected==earlyRetainedLoadout){
                var retained=retainedLoadoutDispatch;
                if(retained==null||!retained.bindOriginalIntent(actual)){actual.revoked=true;return;}
            }
            actual.runtime=ownedUseControlSnapshot(null) instanceof OwnedUseControlSnapshot capturedRuntime?capturedRuntime:null;
            if(!actual.beforeGuardCurrent())return;actual.guardEntered=true;
            boolean returned=guard.start(actual.selected);
            if(returned&&(actual.transaction==null||actual.guardRequest==null||!actual.transaction.transaction.normalReturnedCurrent()
                ||!actual.guardRequest.originalBindingsCurrent()))actual.revoked=true;
        }finally{actual.closed=true;if(savedMaintenanceIntentDispatch==actual)savedMaintenanceIntentDispatch=null;}
    }
    private static RootSavedMaintenanceTransactionInvocation savedMaintenanceTransactionInvocation;
    /** Before the actual Root-installed Guard transaction calls the Coordinator. Private constructor, one selected admission. */
    public static final class RootSavedMaintenanceTransactionInvocation {
        private final ModuleActivationGuard.ActualActivationTransactionCall transaction;private final FarmMaintenanceCoordinator coordinator;
        private final Module selected;private final RootOriginalInputPublication publication;private final ModuleManager registry;
        private final ModuleManager.LifecycleSnapshot before;private final Thread thread=Thread.currentThread();private boolean claimed,revoked,closed;
        private final RootSavedMaintenanceIntentDispatch dispatch;private final RootSavedMaintenanceDeferredActivationCall deferred;
        private RootSavedMaintenanceTransactionInvocation(ModuleActivationGuard.ActualActivationTransactionCall transaction,Module selected){
            this.transaction=transaction;this.selected=selected;coordinator=farmMaintenance;publication=originalInputPublication;registry=moduleManager;before=transaction.beforeLifecycle();
            dispatch=savedMaintenanceIntentDispatch;deferred=savedMaintenanceDeferredActivation;
        }
        public FarmMaintenanceCoordinator coordinatorIdentity(){return coordinator;}public Module moduleIdentity(){return selected;}
        public RootOriginalInputPublication originalInputPublication(){return publication;}
        public ModuleManager.LifecycleSnapshot originalBeforeLifecycle(){return before;}
        public ModuleActivationGuard.ActualActivationTransactionCall guardTransactionIdentity(){return transaction;}
        private boolean bindingCurrent(){return !revoked&&!closed&&savedMaintenanceTransactionInvocation==this&&Thread.currentThread()==thread
            &&coordinator==farmMaintenance&&registry==moduleManager&&transaction.registryIdentity()==registry&&transaction.currentFor(guard,selected)
            &&(dispatch!=null?dispatch.transaction==this&&dispatch.selected==selected&&dispatch.contextCurrent()
                &&dispatch.guardRequest==transaction.originalRequestIdentity():deferred!=null&&deferred.selected==selected&&deferred.current()
                    &&deferred.request==transaction.originalRequestIdentity());}
        public boolean claimOriginalAdmission(FarmMaintenanceCoordinator.AdmissionAttempt exact){
            if(claimed||!bindingCurrent()||exact==null||exact.rootInvocationIdentity()!=this||exact.coordinatorIdentity()!=coordinator||exact.moduleIdentity()!=selected)return false;
            boolean named=StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).walk(frames->{
                var caller=frames.filter(f->f.getDeclaringClass()!=RootSavedMaintenanceTransactionInvocation.class).findFirst().orElse(null);
                return caller!=null&&caller.getDeclaringClass()==FarmMaintenanceCoordinator.class&&caller.getMethodName().equals("activateFromOriginalRoot");
            });
            if(!named)return false;claimed=true;return true;
        }
        public boolean beforeWorkflowCurrent(){return claimed&&bindingCurrent()&&publication!=null&&publication==originalInputPublication
            &&moduleManager.lifecycleCurrent(before)&&moduleManager.lifecycleSameHistory(before,publication.enable.continuity)
            &&publication.nativeHistoryCurrent();}
        /** Affiliation after the first-consume boundary; new native/enable calls still require their own typed instructions. */
        public boolean originalInvocationCurrent(){return claimed&&bindingCurrent()&&publication!=null&&publication==originalInputPublication
            &&publication.enrollment==originalNativeEnrollment&&!publication.revoked&&publication.enable==originalEnableProof&&!publication.enable.revoked;}
    }
    private static boolean activateOriginalFarmMaintenance(Module module,java.util.function.BooleanSupplier activation){
        RootSavedMaintenanceTransactionInvocation actual=null;
        try{var source=guard.actualActivationTransactionForRoot(module);
            if(savedMaintenanceTransactionInvocation!=null)savedMaintenanceTransactionInvocation.revoked=true;
            else if(source!=null&&source.registryIdentity()==moduleManager
                &&(savedMaintenanceIntentDispatch!=null&&savedMaintenanceIntentDispatch.guardRequest==source.originalRequestIdentity()
                    &&savedMaintenanceIntentDispatch.selected==module&&savedMaintenanceIntentDispatch.beforeGuardCurrent()
                    ||savedMaintenanceDeferredActivation!=null&&savedMaintenanceDeferredActivation.request==source.originalRequestIdentity()
                        &&savedMaintenanceDeferredActivation.selected==module&&savedMaintenanceDeferredActivation.current())){
                actual=new RootSavedMaintenanceTransactionInvocation(source,module);savedMaintenanceTransactionInvocation=actual;
                if(actual.dispatch!=null){if(actual.dispatch.transaction!=null)actual.dispatch.revoked=true;else actual.dispatch.transaction=actual;}
            }
        }catch(RuntimeException|Error unavailable){}
        try{return actual==null?farmMaintenance.activate(module,activation):farmMaintenance.activateFromOriginalRoot(module,activation,actual);}
        finally{if(actual!=null){actual.closed=true;if(savedMaintenanceTransactionInvocation==actual)savedMaintenanceTransactionInvocation=null;}}
    }
    private static RootOriginalNativeMaintenancePauseInvocation originalNativeMaintenancePause;
    /** Actual reservation pause with original native receipt custody; method markers cannot prove key publication. */
    public static final class RootOriginalNativeMaintenancePauseInvocation {
        private final FarmMaintenanceCoordinator.SavedMaintenancePauseCall pause;
        private final FarmMaintenanceCoordinator.AdmissionAttempt admission;private final RootSavedMaintenanceTransactionInvocation transaction;
        private final Object capture;private final dev.veltraph.client.rewarp.FarmMaintenanceLease.Token token;
        private final FarmMaintenanceCoordinator.SavedMaintenanceEnableSource declaration;
        private final RootOriginalInputPublication publication;private final RootOriginalNativeEnrollment enrollment;
        private final RootOriginalNativeInstallation installation;private final dev.veltraph.client.farming.module.FarmModule farm;
        private final ModuleManager.LifecycleSnapshot lifecycle;private final ScriptingInputLock.ControlSnapshot control;
        private final Thread thread=Thread.currentThread();private boolean revoked,closed,suspendObserved,normalReturned;
        private dev.veltraph.client.service.mc.RootNativeHistory.SavedMaintenancePauseObservation observation;
        private dev.veltraph.client.service.mc.RootNativeHistory.SavedMaintenancePauseCompletion completion;
        private dev.veltraph.client.rewarp.mc.McIssuedReturnInventory.SavedMaintenancePauseSnapshot resources;
        private RootOriginalNativeMaintenancePauseInvocation(FarmMaintenanceCoordinator.SavedMaintenancePauseCall pause){
            this.pause=pause;admission=pause.actualAdmissionAttempt();transaction=admission.rootInvocationIdentity();capture=pause.originalCaptureIdentity();
            token=pause.maintenanceToken();declaration=pause.originalEnableDeclaration();publication=pause.originalInputPublication();enrollment=publication.enrollment;
            installation=originalNativeInstallation;farm=pause.farm();lifecycle=transaction.originalBeforeLifecycle();control=publication.input.control();
        }
        /** Typed historical pairing only; no current state is renewed by this inspection. */
        public boolean matchesPause(FarmMaintenanceCoordinator.SavedMaintenancePauseCall exact){return exact==pause&&pause.originalCaptureIdentity()==capture
            &&pause.maintenanceToken()==token&&pause.originalEnableDeclaration()==declaration&&pause.originalInputPublication()==publication
            &&pause.actualAdmissionAttempt()==admission&&admission.rootInvocationIdentity()==transaction&&pause.farm()==farm;}
        /** Historical closed Source identity only, including partial failure; never authorizes a new native pause. */
        public boolean closedPauseSourceMatches(FarmMaintenanceCoordinator.SavedMaintenancePauseCall exact){return closed&&Thread.currentThread()==thread
            &&matchesPause(exact)&&exact.coordinatorIdentity()==farmMaintenance&&enrollment==originalNativeEnrollment
            &&publication==originalInputPublication&&publication.original==farm&&installation==originalNativeInstallation;}
        public RootOriginalNativeEnrollment enrollmentIdentity(){return enrollment;}
        public dev.veltraph.client.farming.module.FarmModule originalFarm(){return farm;}
        public net.minecraft.client.KeyMapping originalPostureKey(){return pause.originalPostureKey();}
        public boolean desiredPostureHeld(){return pause.desiredPostureHeld();}
        private boolean affiliationCurrent(){return !revoked&&!closed&&originalNativeMaintenancePause==this&&Thread.currentThread()==thread&&matchesPause(pause)
            &&transaction==savedMaintenanceTransactionInvocation&&transaction.originalInvocationCurrent()&&transaction.originalInputPublication()==publication
            &&publication==originalInputPublication&&!publication.revoked&&publication.original==farm&&publication.enrollment==enrollment
            &&enrollment==originalNativeEnrollment&&installation!=null&&installation==originalNativeInstallation&&installation.currentFor(Minecraft.getInstance())
            &&enrollment.sceneCurrent(Minecraft.getInstance())&&moduleManager.lifecycleCurrent(lifecycle)&&control!=null&&control.publicationCurrent()
            &&farm.isEnabled()&&pause.coordinatorIdentity()==farmMaintenance;}
        public boolean beforeNativeCurrent(){return !normalReturned&&affiliationCurrent()&&pause.beforeNativeCurrent();}
        public boolean suspensionEpisodeCurrent(){return !normalReturned&&affiliationCurrent()&&pause.suspensionEpisodeCurrent()
            &&resources!=null&&resources.currentFinal();}
        public boolean normalSuspendReturnedNativeCurrent(){return !normalReturned&&affiliationCurrent()&&pause.normalSuspendReturnedCurrent()
            &&resources!=null&&resources.currentFinal();}
        public boolean postureEpisodeCurrent(){return !normalReturned&&suspendObserved&&affiliationCurrent()&&pause.postureEpisodeCurrent()
            &&resources!=null&&resources.currentFinal();}
        public boolean nativePostureIssuingCurrent(dev.veltraph.client.service.mc.McInputService exact){return postureEpisodeCurrent()
            &&enrollment.registration.currentFarmInput(exact,Minecraft.getInstance())&&postureEpisodeCurrent();}
        /** Repeated final negative source/resource leaf after the actual mode/provider callbacks. */
        public boolean finalPostureNativeSourceCurrent(){return postureEpisodeCurrent()&&resources!=null&&resources.currentFinal()&&affiliationCurrent();}
        public boolean normalReturnedNativeCurrent(){return suspendObserved&&affiliationCurrent()&&pause.normalPostureReturnedCurrent()
            &&resources!=null&&resources.currentFinal();}
        /** Completed actual native receipt is history only; it supplies no later native write source. */
        public boolean acceptedNormalPauseProof(){return closed&&!revoked&&normalReturned&&completion!=null&&completion.matches(this)&&matchesPause(pause);}
        private boolean producerCurrent(){return affiliationCurrent()&&(pause.beforeNativeCurrent()||pause.suspensionEpisodeCurrent()
            ||suspendObserved&&(pause.postureEpisodeCurrent()||pause.normalPostureReturnedCurrent()));}
    }
    /** Selected posture classification is retained even when the original negative source check now refuses. */
    public static RootOriginalNativeMaintenancePauseInvocation originalSavedMaintenancePauseForNative(){
        var actual=originalNativeMaintenancePause;return actual!=null&&actual.pause.postureInstructionSelected()?actual:null;
    }
    private static RootOriginalNativeMaintenancePauseInvocation beginSavedMaintenanceOriginalPause(FarmMaintenanceCoordinator.SavedMaintenancePauseCall pause){
        if(pause==null||originalNativeMaintenancePause!=null){if(originalNativeMaintenancePause!=null)originalNativeMaintenancePause.revoked=true;return null;}
        var transaction=savedMaintenanceTransactionInvocation;var publication=originalInputPublication;
        if(transaction==null||publication==null||pause.actualAdmissionAttempt()==null||pause.actualAdmissionAttempt().rootInvocationIdentity()!=transaction
            ||pause.originalInputPublication()!=publication||pause.farm()!=publication.original||!transaction.originalInvocationCurrent())return null;
        var actual=new RootOriginalNativeMaintenancePauseInvocation(pause);originalNativeMaintenancePause=actual;boolean ready=false;
        try{
            if(!pause.claimOriginalPause(actual)||!actual.beforeNativeCurrent())return null;
            actual.resources=pause.captureOriginalResources(actual);
            if(actual.resources==null||!actual.resources.currentFinal()||!actual.beforeNativeCurrent())return null;
            actual.observation=dev.veltraph.client.service.mc.RootNativeHistory.beginSavedMaintenancePause(actual,Minecraft.getInstance());
            if(actual.observation==null||!actual.beforeNativeCurrent()||!actual.resources.currentFinal())return null;
            ready=true;return actual;
        }finally{if(!ready){actual.revoked=true;closeSavedMaintenanceOriginalPause(actual);}}
    }
    private static void finishSavedMaintenanceOriginalSuspend(RootOriginalNativeMaintenancePauseInvocation actual){
        if(actual==null||originalNativeMaintenancePause!=actual)return;
        dev.veltraph.client.service.mc.RootNativeHistory.finishSavedMaintenanceSuspend(actual.observation,actual);
        if(!actual.normalSuspendReturnedNativeCurrent()||!actual.publication.nativeHistoryCurrent()){actual.revoked=true;return;}
        actual.suspendObserved=true;
    }
    private static void finishSavedMaintenanceOriginalPause(RootOriginalNativeMaintenancePauseInvocation actual){
        if(actual==null||originalNativeMaintenancePause!=actual)return;
        dev.veltraph.client.service.mc.RootNativeHistory.finishSavedMaintenancePause(actual.observation,actual);
        actual.completion=dev.veltraph.client.service.mc.RootNativeHistory.completeSavedMaintenancePause(actual.observation,actual,Minecraft.getInstance());
        actual.normalReturned=actual.completion!=null&&actual.completion.matches(actual);
        if(!actual.normalReturned)actual.revoked=true;
    }
    private static void closeSavedMaintenanceOriginalPause(RootOriginalNativeMaintenancePauseInvocation actual){
        if(actual==null||originalNativeMaintenancePause!=actual)return;
        try{dev.veltraph.client.service.mc.RootNativeHistory.closeSavedMaintenancePause(actual.observation,actual);}
        finally{try{actual.pause.closeOriginalResources(actual);}finally{
            if(!actual.normalReturned||actual.completion==null||!actual.completion.matches(actual))actual.revoked=true;
            actual.closed=true;
            var retained=retainedLoadoutDispatch;
            if(retained!=null&&actual.pause.helperIdentity()==earlyRetainedLoadout){
                try{retained.observeClosedOriginalPause(actual);}catch(RuntimeException|Error unknown){retained.revoked=true;}
            }
            if(originalNativeMaintenancePause==actual)originalNativeMaintenancePause=null;}}
    }
    private static RootOriginalNativePauseInvocation originalNativePauseInvocation;
    /** Actual private Root instruction around the original Farm pause body. Native observations grant no suspension. */
    public static final class RootOriginalNativePauseInvocation {
        private final dev.veltraph.client.rewarp.mc.McSuspendedFarmReturnCoordinator.PauseCall pause;
        private final RootOriginalInputPublication publication;private final RootOriginalNativeEnrollment enrollment;
        private final dev.veltraph.client.farming.module.FarmModule farm;private final Object source,tick;
        private final ModuleManager.LifecycleSnapshot lifecycle;private final ScriptingInputLock.ControlSnapshot control;
        private final Thread thread=Thread.currentThread();private boolean revoked,normalReturned;
        private dev.veltraph.client.farming.module.FarmModule.IssuedReturnCheckpoint checkpoint;
        private dev.veltraph.client.service.mc.RootNativeHistory.NativePauseObservation observation;
        private RootOriginalNativePauseInvocation(dev.veltraph.client.rewarp.mc.McSuspendedFarmReturnCoordinator.PauseCall pause,RootOriginalInputPublication publication){
            this.pause=pause;this.publication=publication;enrollment=publication.enrollment;farm=pause.farm();source=pause.sourceHeader();tick=pause.rootTickIdentity();
            lifecycle=publication.enable.continuity;control=publication.input.control();
        }
        public RootOriginalNativeEnrollment enrollmentIdentity(){return enrollment;}
        public dev.veltraph.client.farming.module.FarmModule originalFarm(){return farm;}
        private boolean affiliationCurrent(){return !revoked&&originalNativePauseInvocation==this&&Thread.currentThread()==thread&&publication==originalInputPublication
            &&publication.original==farm&&enrollment==originalNativeEnrollment&&enrollment.sceneCurrent(Minecraft.getInstance())&&pause.farm()==farm
            &&pause.sourceHeader()==source&&pause.rootTickIdentity()==tick&&control.publicationCurrent()&&moduleManager.lifecycleCurrent(lifecycle);}
        /** Stored callback-free original scene/actor/control plus actual private in-progress Farm method identity. */
        public boolean beforeNativeCurrent(){return !normalReturned&&affiliationCurrent()&&farm.isEnabled()
            &&(!farm.isSuspended()||farm.issuedReturnPausingUnder(pause.admissionIdentity()));}
        public boolean normalReturnedNativeCurrent(){return normalReturned&&affiliationCurrent()&&checkpoint!=null&&farm.issuedReturnPausedBound(checkpoint,pause);}
    }
    private static RootOriginalNativePauseInvocation beginOriginalNativePause(dev.veltraph.client.rewarp.mc.McSuspendedFarmReturnCoordinator.PauseCall pause){
        var publication=originalInputPublication;
        if(pause==null||publication==null||pause.sourceHeader().originalInputPublication()!=publication||pause.farm()!=publication.original)return null;
        boolean actualEntry=StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).walk(stream->{var caller=stream.skip(1).findFirst().orElse(null);
            return caller!=null&&caller.getDeclaringClass()==VeltraphBootstrap.class&&caller.getMethodName().equals("pauseOriginalIssuedFarm");});
        if(!actualEntry||originalNativePauseInvocation!=null){if(originalNativePauseInvocation!=null)originalNativePauseInvocation.revoked=true;return null;}
        if(!publication.nativeHistoryCurrent()||!pause.current(pause.farm()))return null;
        var actual=new RootOriginalNativePauseInvocation(pause,publication);originalNativePauseInvocation=actual;
        try{actual.observation=dev.veltraph.client.service.mc.RootNativeHistory.beginPause(actual,Minecraft.getInstance());}
        catch(RuntimeException|Error unavailable){actual.revoked=true;if(originalNativePauseInvocation==actual)originalNativePauseInvocation=null;return null;}
        if(actual.observation==null){actual.revoked=true;if(originalNativePauseInvocation==actual)originalNativePauseInvocation=null;return null;}return actual;
    }
    private static void finishOriginalNativePause(RootOriginalNativePauseInvocation actual,java.util.Optional<dev.veltraph.client.farming.module.FarmModule.IssuedReturnCheckpoint> returned){
        if(actual==null||originalNativePauseInvocation!=actual)return;actual.checkpoint=returned.orElse(null);actual.normalReturned=true;
        try{dev.veltraph.client.service.mc.RootNativeHistory.finishPause(actual.observation,actual);
            if(!actual.normalReturnedNativeCurrent())actual.revoked=true;}
        catch(RuntimeException|Error unavailable){actual.revoked=true;}
    }
    private static void closeOriginalNativePause(RootOriginalNativePauseInvocation actual){if(actual!=null){
        try{dev.veltraph.client.service.mc.RootNativeHistory.closePause(actual.observation,actual);}catch(RuntimeException|Error unavailable){}
        finally{actual.revoked=true;if(originalNativePauseInvocation==actual)originalNativePauseInvocation=null;}
    }}
    private static RootOriginalNativeTermination originalNativeTermination;
    /** Exact old run stop observation; never grants native cleanup, and never refreshes the old original publication. */
    public static final class RootOriginalNativeTermination {
        private final RootLogicalStopFrame frame;private final RootOriginalInputPublication publication;private final RootOriginalNativeInstallation installation;
        private final RootOriginalNativeEnrollment enrollment;private final Module original;private final Thread thread=Thread.currentThread();
        private dev.veltraph.client.service.mc.RootNativeHistory.TerminalTicket nativeTicket;
        private dev.veltraph.client.service.mc.RootNativeHistory.TerminalNoOwnerHandout nativeHandout;
        private ModuleManager.LifecycleSnapshot terminalLifecycle;private RootOriginalTerminalDisable pendingOwnerDisable;private ModuleManager.OwnDisableReceipt ownerReturned;
        private ModuleManager.ManualStopReceipt stopped;private ModuleManager.OwnNoGameplayStopReceipt noGameplayStopped;private ScriptingInputLock.RootLogicalRetirementReceipt retirement;
        private RootOriginalNativeIdleContinuation idleContinuation;private Object cause;private ScriptingInputLock.ControlSnapshot closedControl;private boolean revoked,permanentlyRetired,logicalClosed;
        private RootOriginalNativeTermination(RootLogicalStopFrame frame,RootOriginalInputPublication publication){this.frame=frame;this.publication=publication;
            installation=originalNativeInstallation;enrollment=publication.enrollment;original=publication.original;terminalLifecycle=publication.lifecycle;}
        public RootOriginalNativeEnrollment originalEnrollment(){return enrollment;}public Module originalModule(){return original;}
        public RootOriginalNativeInstallation installationIdentity(){return installation;}
        private boolean sceneCurrent(Minecraft client){return !revoked&&originalNativeTermination==this&&Thread.currentThread()==thread&&client==frame.client
            &&installation!=null&&installation.currentFor(client)&&client.player==frame.player&&client.level==frame.world&&client.getConnection()==frame.connection
            &&client.getConnection()!=null&&client.getConnection().getConnection()==frame.nativeConnection&&client.options==frame.options&&client.gameMode==frame.gameMode
            &&frame.root==moduleManager&&frame.provider==rootRotationProvider&&frame.registration==rootInputReleaseRegistration&&frame.store==config&&frame.settings!=null&&frame.settings.current();}
        private boolean originalTerminalLineageCurrent(Minecraft client){return sceneCurrent(client)&&!logicalClosed&&!permanentlyRetired
            &&publication==originalInputPublication&&!publication.revoked&&publication.enable==originalEnableProof&&!publication.enable.revoked
            &&enrollment==originalNativeEnrollment&&enrollment.admission!=null&&enrollment.admission.request.originalBindingsCurrent()
            &&frame.control.publicationCurrent()&&publication.input!=null&&publication.input.control().sameHistory(frame.control)
            &&terminalLifecycle!=null&&moduleManager.lifecycleCurrent(terminalLifecycle)
            &&(ownerReturned==null?moduleManager.lifecycleSameHistory(publication.lifecycle,terminalLifecycle):ownerReturned.parent()==original
                &&moduleManager.originalModuleLifecycleCurrent(terminalLifecycle,original));}
        public boolean beforeNativeCurrent(Minecraft client){return sceneCurrent(client)&&publication==originalInputPublication&&!permanentlyRetired
            &&frame.start.startingCurrent()&&publication.input!=null&&publication.input.current()&&frame.control.publicationCurrent();}
        public boolean cleanupNativeCurrent(Module exact,Minecraft client){return sceneCurrent(client)&&!permanentlyRetired&&publication==originalInputPublication
            &&(exact==original||exact==publication.publishedOwner)&&frame.control.publicationCurrent()&&(frame.start.nativeStopCurrent(exact)||completedNoGameplayStopBeforeLogicalCurrent(client));}
        /** Exact completed real menu stop before logical mutation; no native/helper/provider call occurs here. */
        public boolean completedOwnGuiStopBeforeLogicalCurrent(Minecraft client){
            return sceneCurrent(client)&&!permanentlyRetired&&!logicalClosed&&retirement==null&&frame.guiStop!=null&&frame.guiStop.current()
                &&publication==originalInputPublication&&stopped!=null&&stopped.matches(frame.start)&&stopped.disabledOwnerFromStart(frame.oldOwner)
                &&frame.start.nativeStopCurrent(frame.oldOwner)&&rootNativeHigherPriorityIdle();}
        /** Exact closed actual batch stop; known passive actors may remain enabled through their original role history. */
        public boolean completedNoGameplayStopBeforeLogicalCurrent(Minecraft client){return sceneCurrent(client)&&!permanentlyRetired&&!logicalClosed
            &&retirement==null&&publication==originalInputPublication&&frame.control.publicationCurrent()&&noGameplayStopped!=null
            &&noGameplayStopped.disabledOwnerFromStart(frame.start,originalPassiveSource,frame.oldOwner)
            &&rootNativeHigherPriorityIdle()&&(frame.guiStop==null?client.gui.screen()==null
                :frame.guiStop.menu.currentFor(frame.guiStop.menu.screen)&&client.gui.screen()==frame.guiStop.menu.screen);}
        private boolean retirementMatches(){return retirement!=null&&(cause instanceof RootLogicalInputRetirementCause logical?retirement.matches(frame.control,logical)
            :cause instanceof RootMenuLogicalInputRetirementCause menu?retirement.matches(frame.control,menu)
            :cause instanceof RootNoGameplayLogicalInputRetirementCause noGameplay&&retirement.matches(frame.control,noGameplay));}
        public boolean normalReturnedNativeCurrent(Minecraft client){return sceneCurrent(client)&&!permanentlyRetired
            &&(noGameplayStopped!=null?noGameplayStopped.disabledOwnerFromStart(frame.start,originalPassiveSource,frame.oldOwner)
                :stopped!=null&&stopped.matches(frame.start)&&stopped.disabledOwnerFromStart(frame.oldOwner)&&frame.start.nativeStopCurrent(frame.oldOwner))&&retirementMatches()
            &&retirement.returnedControl().publicationCurrent()&&!retirement.returnedControl().active()&&retirement.returnedControl().ownerModuleId()==null;}
        public RootOriginalNativeIdleContinuation idleContinuationIdentity(){return permanentlyRetired?idleContinuation:null;}
        public boolean terminalHistoryCurrent(Minecraft client){
            if(idleContinuation!=null)return permanentlyRetired&&idleContinuation.currentFor(client);
            // Only the exact original finish/closed-batch handoff may use the old stop context; no getter mints a successor.
            return sceneCurrent(client)&&logicalClosed&&closedControl!=null&&installation.currentNativeNoOwner(client)
                &&installation.idleControl!=null&&closedControl.sameHistory(installation.idleControl)&&closedControl.publicationCurrent();
        }
    }
    public static final class RootOriginalMenuInstallation {
        private final dev.veltraph.client.ui.AutomationsScreen screen;private final ModuleManager manager;private final ConfigService store;
        private final dev.veltraph.client.farming.domain.FarmLayoutRegistry registry;private final ModuleActivationGuard activation;
        private final RootOriginalNativeInstallation installation;
        private RootOriginalMenuInstallation(dev.veltraph.client.ui.AutomationsScreen screen,ModuleManager manager,ConfigService store,
                dev.veltraph.client.farming.domain.FarmLayoutRegistry registry,ModuleActivationGuard activation){
            this.screen=screen;this.manager=manager;this.store=store;this.registry=registry;this.activation=activation;installation=originalNativeInstallation;
        }
        public boolean currentFor(dev.veltraph.client.ui.AutomationsScreen exact){return exact==screen&&exact.originalRootMenuForRoot()==this&&current();}
        private boolean current(){return installation!=null&&installation==originalNativeInstallation&&manager==moduleManager&&store==config
            &&registry==farmRegistry&&activation==guard&&installation.currentFor(Minecraft.getInstance());}
    }
    /** Inert provenance from the actual completed canonical screen factory; never a native/activation permission. */
    public static RootOriginalMenuInstallation observeOriginalAutomationsMenu(dev.veltraph.client.ui.AutomationsScreen screen,ModuleManager manager,
            ConfigService store,dev.veltraph.client.farming.domain.FarmLayoutRegistry registry,ModuleActivationGuard activation){
        try{boolean factory=StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).walk(stream->{var frames=stream.skip(1).limit(2).toList();return frames.size()==2
            &&frames.get(0).getDeclaringClass()==dev.veltraph.client.ui.AutomationsScreen.class&&frames.get(0).getMethodName().equals("observeOriginalFactoryCompletion")
            &&frames.get(1).getDeclaringClass()==dev.veltraph.client.ui.VeltraphScreens.class&&frames.get(1).getMethodName().equals("automations");});
            if(!factory||screen==null||manager!=moduleManager||store!=config||registry!=farmRegistry||activation!=guard||originalNativeInstallation==null)return null;
            var source=new RootOriginalMenuInstallation(screen,manager,store,registry,activation);return source.current()?source:null;
        }catch(RuntimeException|Error unavailable){return null;}}
    private static RootOriginalMenuRequest originalMenuRequest;
    /** Private source minted before the actual installed Automations widget asks the genuine Guard. */
    public static final class RootOriginalMenuRequest {
        private final RootOriginalMenuInstallation menu;private final Module module;private final ModuleManager.LifecycleSnapshot lifecycle;
        private final ScriptingInputLock.ControlSnapshot control;private final dev.veltraph.client.config.MapConfigService.StoredStateSnapshot settings;
        private final Minecraft client;private final Object player,world,listener,connection,options,gameMode;private final Thread thread=Thread.currentThread();
        private final RootOriginalInputPublication previousPublication;private RootOriginalEnableAdmission admission;
        private boolean revoked,guardReturned,closingNative,nativeClosed,closed;
        private RootOriginalMenuRequest(RootOriginalMenuInstallation menu,Module module){
            this.menu=menu;this.module=module;client=Minecraft.getInstance();lifecycle=moduleManager.lifecycleSnapshot();control=ScriptingInputLock.originalPublicationSnapshot();
            settings=config instanceof dev.veltraph.client.config.MapConfigService m?m.storedStateSnapshot():config instanceof dev.veltraph.client.config.JsonConfigService j?j.storedStateSnapshot():null;
            player=client.player;world=client.level;listener=client.getConnection();connection=client.getConnection().getConnection();options=client.options;gameMode=client.gameMode;previousPublication=originalInputPublication;
        }
        private boolean sceneCurrent(){return !revoked&&Thread.currentThread()==thread&&menu.current()&&client.isSameThread()&&client==Minecraft.getInstance()
            &&player!=null&&world!=null&&listener!=null&&connection!=null&&client.player==player&&client.level==world&&client.getConnection()==listener
            &&client.getConnection().getConnection()==connection&&client.options==options&&client.gameMode==gameMode&&client.player.level()==client.level
            &&client.player.isAlive()&&((net.minecraft.network.Connection)connection).isConnected()&&settings!=null&&settings.current();}
        private boolean beforeGuardCurrent(){return sceneCurrent()&&!closed&&!guardReturned&&originalMenuRequest==this&&client.gui.screen()==menu.screen
            &&module instanceof dev.veltraph.client.core.AbstractModule&&moduleManager.get(module.id()).orElse(null)==module
            &&control!=null&&control.publicationCurrent()&&!control.active()&&control.ownerModuleId()==null&&moduleManager.noGameplayOwnerRosterCurrent(lifecycle,originalPassiveSource)
            &&menu.installation.currentNativeNoOwner(client)&&rootNativeHigherPriorityIdle()&&!client.isPaused()&&!client.player.isScoping()
            &&client.getCameraEntity()==client.player&&client.player.containerMenu==client.player.inventoryMenu&&client.player.containerMenu.getCarried().isEmpty();}
        private boolean enrolledSceneCurrent(){return sceneCurrent()&&admission!=null&&admission.enrolled
            &&(nativeClosed?(closed||client.gui.screen()==null):originalMenuRequest==this&&!closed
                &&(client.gui.screen()==menu.screen||closingNative&&client.gui.screen()==null));}
        public RootOriginalNativeEnrollment enrollmentIdentity(){return admission==null?null:admission.enrollment;}
        public Module originalModule(){return module;}
        /** Observation affiliation during the exact SDK close; SDK opcode receipts supply actual native normal-return proof. */
        public boolean nativeCloseObservationCurrent(Minecraft exact){return exact==client&&!closed&&originalMenuRequest==this&&closingNative&&!nativeClosed&&guardReturned
            &&sceneCurrent()&&(client.gui.screen()==menu.screen||client.gui.screen()==null)&&admission!=null&&admission.enrolled
            &&admission.request.closedNormalEnabledReturned()&&originalEnableProof!=null&&originalEnableProof.enrollment==admission.enrollment
            &&originalEnableProof.parent==module&&originalEnableProof.enableLineageCurrent()&&control.publicationCurrent();}
        public boolean beforeNativeCloseCurrent(Minecraft exact){return exact==client&&!closed&&originalMenuRequest==this&&closingNative&&!nativeClosed&&guardReturned
            &&client.gui.screen()==menu.screen&&enrolledSceneCurrent()&&admission.request.closedNormalEnabledReturned()&&originalEnableProof!=null&&originalEnableProof.enrollment==admission.enrollment
            &&originalEnableProof.parent==module&&originalEnableProof.enableLineageCurrent()&&control.publicationCurrent();}
        public boolean normalReturnedNativeCloseCurrent(Minecraft exact){return exact==client&&nativeClosed&&guardReturned&&enrolledSceneCurrent()
            &&admission.request.closedNormalEnabledReturned()&&originalEnableProof!=null&&originalEnableProof.enrollment==admission.enrollment&&originalEnableProof.enableLineageCurrent();}
    }
    /** Existing authentic pending menu close only; no getter can create or repair the source. */
    public static RootOriginalMenuRequest originalMenuRequestForNativeClose(Minecraft client){var original=originalMenuRequest;
        try{return original!=null&&original.beforeNativeCloseCurrent(client)?original:null;}catch(RuntimeException|Error unavailable){return null;}}
    private static boolean actualAutomationsRequestCaller(){return StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).walk(stream->{var caller=stream.skip(2).findFirst().orElse(null);
        return caller!=null&&caller.getDeclaringClass()==dev.veltraph.client.ui.AutomationsScreen.class&&caller.getMethodName().equals("toggleAndCloseIfStarted");});}
    public static RootOriginalMenuRequest beginOriginalAutomationsRequest(RootOriginalMenuInstallation menu,String id){
        try{if(!actualAutomationsRequestCaller()||menu==null||!menu.current()||Minecraft.getInstance().gui.screen()!=menu.screen)return null;
            if(originalMenuRequest!=null){originalMenuRequest.revoked=true;return null;}
            var module=moduleManager.get(id).orElse(null);if(!(module instanceof dev.veltraph.client.core.AbstractModule)||Minecraft.getInstance().getConnection()==null)return null;
            var request=new RootOriginalMenuRequest(menu,module);originalMenuRequest=request;return request;
        }catch(RuntimeException|Error unavailable){return null;}}
    public static void observeOriginalAutomationsGuardReturn(RootOriginalMenuRequest actual,boolean wasEnabled,boolean nowEnabled){
        if(!actualAutomationsRequestCaller()||actual==null||originalMenuRequest!=actual||actual.closed||actual.revoked)return;
        if(!wasEnabled&&nowEnabled&&actual.admission!=null&&actual.admission.request.closedNormalEnabledReturned()&&actual.enrolledSceneCurrent())actual.guardReturned=true;
        else if(!wasEnabled)actual.revoked=true;
    }
    public static void beginOriginalAutomationsNativeClose(RootOriginalMenuRequest actual){
        if(actualAutomationsRequestCaller()&&actual!=null&&originalMenuRequest==actual&&actual.guardReturned&&!actual.closed&&!actual.revoked)actual.closingNative=true;
    }
    public static void finishOriginalAutomationsNativeClose(RootOriginalMenuRequest actual){
        if(actualAutomationsRequestCaller()&&actual!=null&&originalMenuRequest==actual&&actual.closingNative&&!actual.closed&&!actual.revoked
            &&actual.client.gui.screen()==null){actual.nativeClosed=true;actual.closingNative=false;
            publishInitialOriginalMenuInput(actual);
        }
    }
    private static void publishInitialOriginalMenuInput(RootOriginalMenuRequest actual){
        try{if(actual==null||!actual.normalReturnedNativeCloseCurrent(actual.client)||originalInputPublication!=null
                ||originalEnableProof==null||originalEnableProof.enrollment!=actual.enrollmentIdentity()||originalEnableProof.parent!=actual.module)return;
            // The actual publication source is retained before the original selector/provider callbacks.
            var write=new RootOriginalOwnershipWrite(actual.module,false,null,null);
            var selected=moduleManager.inputOwner();
            String foragingOwner=foragingMaintenance==null?null:foragingMaintenance.controlOwnerId();
            if(foragingOwner==null&&galateaWorkflows!=null)foragingOwner=galateaWorkflows.controlOwnerId();
            String maintenanceOwner=farmMaintenance==null?null:farmMaintenance.controlOwnerId();
            String capturedOwner=maintenanceOwner!=null?maintenanceOwner:foragingOwner;
            if(selected.orElse(null)!=actual.module||capturedOwner!=null||!actual.normalReturnedNativeCloseCurrent(actual.client)
                ||!moduleManager.lifecycleCurrent(write.lifecycle)||write.before==null||!write.before.publicationCurrent()
                ||actual.enrollmentIdentity().nativeHistory==null||!actual.enrollmentIdentity().nativeHistory.currentFor(actual.module,actual.client))return;
            if(publishOriginalInputWrite(write,true,actual.module.id())){
                var publication=originalInputPublication;if(publication==null||!publication.nativeHistoryCurrent())actual.revoked=true;
            }
        }catch(RuntimeException|Error unavailable){actual.revoked=true;if(originalInputPublication!=null)originalInputPublication.revoked=true;}
    }
    public static void closeOriginalAutomationsRequest(RootOriginalMenuRequest actual){
        if(actualAutomationsRequestCaller()&&actual!=null&&originalMenuRequest==actual){actual.closed=true;if(actual.admission!=null&&!actual.nativeClosed)actual.revoked=true;originalMenuRequest=null;}
    }
    private static ModuleManager.ActualModuleTickRegistration originalModuleTickRegistration;
    private static RootPrivatePassiveSource originalPassiveSource;
    private static RootPassiveTransition pendingPassiveTransition;
    /** Exact two real Root-registered final no-input actors; no public role Boolean is read. */
    public static final class RootPrivatePassiveSource {
        private final RootOriginalNativeInstallation installation;private final ModuleManager registry;
        private final Module shard,garden;private final Thread thread=Thread.currentThread();private ModuleManager.LifecycleSnapshot roleHistory;
        private RootPrivatePassiveSource(RootOriginalNativeInstallation installation){this.installation=installation;registry=moduleManager;shard=originalShardCapture;garden=originalGardenAnalyzer;roleHistory=registry.lifecycleSnapshot();}
        public boolean declaredActor(Module exact){return Thread.currentThread()==thread&&this==originalPassiveSource&&registry==moduleManager
            &&installation==originalNativeInstallation&&exact!=null&&(exact==shard&&exact.getClass()==dev.veltraph.client.hunting.ShardCaptureModule.class
                ||exact==garden&&exact.getClass()==dev.veltraph.client.garden.copy.GardenAnalyzerModule.class);}
        public boolean originalRoleHistoryCurrent(ModuleManager exact){return exact==registry&&this==originalPassiveSource&&Thread.currentThread()==thread
            &&installation==originalNativeInstallation&&roleHistory!=null&&registry.originalModuleLifecycleCurrent(roleHistory,shard)
            &&registry.originalModuleLifecycleCurrent(roleHistory,garden);}
    }
    /** Genuine before/normal passive lifecycle operation, never a current-state role or native ownership grant. */
    public static final class RootPassiveTransition {
        private final RootPrivatePassiveSource declaration;private final RootOriginalNativeInstallation installation;
        private final ModuleManager registry;private final Module passive;private final ModuleManager.LifecycleSnapshot before;
        private final ModuleActivationGuard.OriginalActivationStart guardRequest;private final RootOriginalModuleTickInvocation rootTick;
        private final ModuleManager.ModuleTickDeclaration moduleTick;private final RootOriginalNativeTermination terminalStop;private final RootOriginalInputPublication publication;
        private final RootOriginalEnableProof proof;private final ConfigService store;private final dev.veltraph.client.config.MapConfigService.StoredStateSnapshot settings;
        private final ScriptingInputLock.ControlSnapshot control;private final Minecraft client;
        private final Object player,world,listener,connection,options,gameMode;private final Thread thread=Thread.currentThread();
        private ModuleManager.ActualEnableStart enabling;private ModuleManager.ActualDisableStart disabling;
        private ModuleManager.OwnEnableReceipt enabled;private ModuleManager.OwnDisableReceipt disabled;
        private ModuleManager.LifecycleSnapshot after;private boolean revoked,accepted,closed;
        private dev.veltraph.client.service.mc.RootNativeHistory.PassiveTransitionObservation activeNative;
        private dev.veltraph.client.observation.nativeinput.McNativeManualInputLineage.PassiveNativeTransitionTicket idleNative;
        private RootPassiveTransition(Module passive,ModuleManager.LifecycleSnapshot before,ModuleActivationGuard.OriginalActivationStart request,
                RootOriginalModuleTickInvocation tick,ModuleManager.ModuleTickDeclaration moduleTick,RootOriginalNativeTermination terminalStop){
            this.terminalStop=terminalStop;this.passive=passive;this.before=before;guardRequest=request;rootTick=tick;this.moduleTick=moduleTick;
            declaration=originalPassiveSource;installation=originalNativeInstallation;registry=moduleManager;
            publication=originalInputPublication!=null&&!originalInputPublication.revoked?originalInputPublication:null;proof=publication==null?null:publication.enable;store=config;
            settings=store instanceof dev.veltraph.client.config.MapConfigService m?m.storedStateSnapshot():store instanceof dev.veltraph.client.config.JsonConfigService j?j.storedStateSnapshot():null;
            control=ScriptingInputLock.originalPublicationSnapshot();client=Minecraft.getInstance();player=client.player;world=client.level;
            listener=client.getConnection();connection=client.getConnection()==null?null:client.getConnection().getConnection();options=client.options;gameMode=client.gameMode;
        }
        /** Exact authentic active-native commit phase; no source/native validation callback runs here. */
        public boolean passiveDeclarationCommitWindowCurrent(){return !revoked&&!closed&&accepted&&Thread.currentThread()==thread
            &&pendingPassiveTransition==this&&activeNative!=null&&activeNative.commitWindowCurrent();}
        public RootOriginalNativeInstallation originalInstallation(){return installation;}
        public RootOriginalInputPublication originalInputPublication(){return publication;}
        public RootOriginalNativeTermination originalTermination(){return terminalStop;}
        public Module passiveModule(){return passive;}public ModuleManager registryIdentity(){return registry;}
        public ModuleManager.LifecycleSnapshot beforeLifecycle(){return before;}
        private boolean sourceCurrent(Minecraft c){return !revoked&&Thread.currentThread()==thread&&c==client&&c==Minecraft.getInstance()&&c.isSameThread()
            &&declaration==originalPassiveSource&&declaration!=null&&declaration.declaredActor(passive)&&installation==originalNativeInstallation
            &&installation!=null&&installation.currentFor(c)&&registry==moduleManager&&control!=null&&control.publicationCurrent()
            &&store==config&&settings!=null&&settings.current()&&originalEnableRegistration!=null&&originalEnableRegistration.current()
            &&originalDisableRegistration!=null&&originalDisableRegistration.current()&&originalModuleTickRegistration!=null&&originalModuleTickRegistration.current()
            &&(publication==null?proof==null:publication==originalInputPublication&&!publication.revoked&&proof==originalEnableProof&&!proof.revoked
                &&proof.completed!=null&&(terminalStop!=null?terminalStop==originalNativeTermination&&terminalStop.publication==publication
                    &&terminalStop.sceneCurrent(c)&&proof.enrollment.admission.request.originalBindingsCurrent()
                    :proof.completed.parentEnableHistoryCurrent()&&proof.enrollment.admission.request.originalEnableSourceCurrent(proof.parent,proof.completed)))
            &&c.player==player&&c.level==world&&c.getConnection()==listener&&listener!=null&&c.getConnection().getConnection()==connection
            &&c.options==options&&c.gameMode==gameMode;}
        public boolean sourceBeforeCurrent(Minecraft c){return !closed&&!accepted&&pendingPassiveTransition==this&&sourceCurrent(c)&&registry.lifecycleCurrent(before)
            &&declaration.originalRoleHistoryCurrent(registry)&&(publication==null?registry.noGameplayOwnerRosterCurrent(before,declaration):terminalStop!=null?terminalStop.originalTerminalLineageCurrent(c)
                &&registry.lifecycleSameHistory(terminalStop.terminalLifecycle,before):registry.lifecycleSameHistory(proof.continuity,before)&&registry.lifecycleSameHistory(publication.lifecycle,before))
            &&(guardRequest!=null?guardRequest.guardIdentity()==guard&&guardRequest.registryIdentity()==registry&&guardRequest.module()==passive
                &&guardRequest.entryCurrent():terminalStop!=null?terminalStop==originalNativeTermination&&terminalStop.publication==publication
                    &&terminalStop.sceneCurrent(c)&&terminalStop.frame.start.nativeStopCurrent(passive)
                    &&disabling!=null&&disabling.originalManualInstruction()!=null&&disabling.originalManualInstruction().currentFor(disabling)
                :rootTick!=null&&originalModuleTickInvocation==rootTick&&rootTick.nativeCurrent()&&rootTick.registry==registry
                    &&moduleTick!=null&&moduleTick.issuingCurrent(passive));}
        public boolean lifecycleInProgressCurrent(ModuleManager.LifecycleSnapshot retained,Minecraft c){
            if(closed||pendingPassiveTransition!=this||!sourceCurrent(c)||!registry.lifecycleSameHistory(retained,before))return false;
            return enabling!=null&&(enabling.startingCurrent()||enabling.enabledCurrent())||disabling!=null&&(disabling.startingCurrent()||disabling.disabledCurrent())
                ||enabled!=null&&enabled.current()||disabled!=null&&disabled.closedCurrent();
        }
        public ModuleManager.LifecycleSnapshot normalReturnedLifecycle(){return after;}
        public boolean normalReturnedCurrent(Minecraft c){return !revoked&&accepted&&sourceCurrent(c)&&after!=null&&registry.lifecycleCurrent(after)
            &&declaration.originalRoleHistoryCurrent(registry)&&(publication==null?registry.noGameplayOwnerRosterCurrent(after,declaration)
                :terminalStop!=null?terminalStop.originalTerminalLineageCurrent(c)&&registry.lifecycleSameHistory(terminalStop.terminalLifecycle,after)
                    :publication==originalInputPublication&&proof==originalEnableProof&&registry.lifecycleSameHistory(proof.continuity,after)&&registry.lifecycleSameHistory(publication.lifecycle,after))
            &&(guardRequest!=null?enabled!=null&&enabled.current()&&guardRequest.closedNormalEnableSourceCurrent(passive,enabled)
                :terminalStop!=null?terminalStop==originalNativeTermination&&terminalStop.publication==publication&&terminalStop.sceneCurrent(c)
                    &&disabled!=null&&disabled.closedCurrent()&&disabled.actualStart()==disabling&&disabling.originalManualInstruction()!=null
                :rootTick!=null&&rootTick.gameplay!=null&&rootTick.gameplay.sourceOriginCurrent()&&!rootTick.revoked&&moduleTick!=null&&moduleTick.normalReturnedCurrent(passive)
                    &&(disabled!=null?disabled.closedCurrent():registry.lifecycleSameHistory(before,after)));}
    }
    private static RootPassiveTransition beginOriginalPassiveTransition(Module actor,ModuleManager.LifecycleSnapshot before,
            ModuleActivationGuard.OriginalActivationStart request,RootOriginalModuleTickInvocation rootTick,ModuleManager.ModuleTickDeclaration moduleTick){
        return beginOriginalPassiveTransition(actor,before,request,rootTick,moduleTick,null,null);
    }
    private static RootPassiveTransition beginOriginalPassiveTransition(Module actor,ModuleManager.LifecycleSnapshot before,
            ModuleActivationGuard.OriginalActivationStart request,RootOriginalModuleTickInvocation rootTick,ModuleManager.ModuleTickDeclaration moduleTick,
            RootOriginalNativeTermination terminal,ModuleManager.ActualDisableStart disable){
        if(pendingPassiveTransition!=null){pendingPassiveTransition.revoked=true;return null;}
        RootPassiveTransition actual=null;
        try{
            if(originalPassiveSource==null||!originalPassiveSource.declaredActor(actor)||!moduleManager.lifecycleCurrent(before))return null;
            actual=new RootPassiveTransition(actor,before,request,rootTick,moduleTick,terminal);actual.disabling=disable;pendingPassiveTransition=actual;
            if(!actual.sourceBeforeCurrent(actual.client)){actual.revoked=true;closeOriginalPassiveTransition(actual);return null;}
            if(actual.publication!=null){
                actual.activeNative=dev.veltraph.client.service.mc.RootNativeHistory.beginPassiveTransition(actual,actual.client);
                if(actual.activeNative==null)actual.revoked=true;
            }else{
                actual.idleNative=dev.veltraph.client.observation.nativeinput.McNativeManualInputLineage.beginPassiveTransition(actual,actual.client);
                if(actual.idleNative==null)actual.revoked=true;
            }
            if(!actual.sourceBeforeCurrent(actual.client)){actual.revoked=true;closeOriginalPassiveTransition(actual);return null;}
            return actual;
        }catch(RuntimeException|Error unavailable){if(actual!=null){actual.revoked=true;closeOriginalPassiveTransition(actual);}return null;}
    }
    private static Object captureOriginalPassiveGuard(ModuleActivationGuard.OriginalActivationStart request){
        if(request.guardIdentity()!=guard||request.registryIdentity()!=moduleManager||!request.entryCurrent())return null;
        return beginOriginalPassiveTransition(request.module(),request.originalBeforeLifecycle(),request,null,null);
    }
    private static Object captureOriginalPassiveTick(ModuleManager.ModuleTickDeclaration module){
        var tick=originalModuleTickInvocation;
        if(module==null||tick==null||!tick.nativeCurrent()||tick.registry!=moduleManager||module.registryIdentity()!=moduleManager
                ||!module.issuingCurrent(module.module())||originalPassiveSource==null||!originalPassiveSource.declaredActor(module.module()))return null;
        return beginOriginalPassiveTransition(module.module(),module.beforeSnapshot(),null,tick,module);
    }
    /** Accept only the issued Core normal receipt, before the next original module callback can change native input. */
    private static void acceptOriginalPassiveTransition(RootPassiveTransition actual,ModuleManager.LifecycleSnapshot returned){
        if(actual==null||pendingPassiveTransition!=actual||actual.closed||actual.revoked||!actual.sourceCurrent(actual.client)
                ||returned==null||!moduleManager.lifecycleCurrent(returned))return;
        if(actual.guardRequest!=null){
            if(actual.enabled==null||!actual.enabled.current()||!actual.guardRequest.closedNormalEnableSourceCurrent(actual.passive,actual.enabled)
                    ||!moduleManager.lifecycleSameHistory(returned,actual.enabled.completedSnapshot()))return;
        }else if(actual.terminalStop!=null){
            if(actual.terminalStop!=originalNativeTermination||actual.terminalStop.publication!=actual.publication||actual.disabled==null
                ||actual.disabling==null||actual.disabling.originalManualInstruction()==null||!actual.disabled.closedCurrent()
                ||!moduleManager.lifecycleSameHistory(returned,actual.disabled.stoppedSnapshot()))return;
        }else{
            if(actual.rootTick==null||actual.rootTick.revoked||originalModuleTickInvocation!=actual.rootTick||actual.moduleTick==null
                    ||!actual.moduleTick.normalReturnedCurrent(actual.passive))return;
            if(actual.disabled!=null){if(!actual.disabled.closedCurrent()||!moduleManager.lifecycleSameHistory(returned,actual.disabled.stoppedSnapshot()))return;}
            else if(!moduleManager.lifecycleSameHistory(actual.before,returned))return;
        }
        if(actual.terminalStop!=null){
            if(actual.terminalStop!=originalNativeTermination||actual.terminalStop.publication!=actual.publication
                    ||!moduleManager.lifecycleSameHistory(actual.terminalStop.terminalLifecycle,actual.before))return;
            actual.terminalStop.terminalLifecycle=returned;
        }else if(actual.publication!=null){
            if(actual.publication!=originalInputPublication||actual.proof!=originalEnableProof
                    ||!moduleManager.lifecycleSameHistory(actual.proof.continuity,actual.before)
                    ||!moduleManager.lifecycleSameHistory(actual.publication.lifecycle,actual.before))return;
            actual.proof.continuity=returned;actual.publication.lifecycle=returned;
        }
        // Only this actual operation substitutes its exact issued lifecycle; all key/pose/control facts stay retained.
        actual.declaration.roleHistory=returned;actual.after=returned;actual.accepted=true;
        if(actual.publication==null){var idle=originalNativeIdleContinuation;
            if(idle!=null&&!idle.revoked&&idle.installation==actual.installation&&idle.registry==actual.registry
                &&moduleManager.lifecycleSameHistory(idle.lifecycle,actual.before))idle.lifecycle=returned;
        }
        if(!actual.normalReturnedCurrent(actual.client)){actual.revoked=true;return;}
        if(actual.activeNative!=null)dev.veltraph.client.service.mc.RootNativeHistory.finishPassiveTransition(actual.activeNative,actual,actual.client);
        if(actual.idleNative!=null)dev.veltraph.client.observation.nativeinput.McNativeManualInputLineage.finishPassiveTransition(actual.idleNative,actual,actual.client);
    }
    private static void closeOriginalPassiveGuard(ModuleActivationGuard.OriginalActivationStart request,Object captured){
        if(!(captured instanceof RootPassiveTransition actual)||actual.guardRequest!=request)return;
        try{acceptOriginalPassiveTransition(actual,actual.enabled==null?null:actual.enabled.completedSnapshot());}
        catch(RuntimeException|Error unavailable){actual.revoked=true;}
        finally{closeOriginalPassiveTransition(actual);}
    }
    private static void finishOriginalPassiveTick(ModuleManager.ModuleTickDeclaration module,Object captured){
        if(captured instanceof RootPassiveTransition actual&&actual.moduleTick==module)
            try{acceptOriginalPassiveTransition(actual,module.normalReturnedSnapshot());}catch(RuntimeException|Error unavailable){actual.revoked=true;}
    }
    private static void closeOriginalPassiveTick(ModuleManager.ModuleTickDeclaration module,Object captured){
        if(captured instanceof RootPassiveTransition actual&&actual.moduleTick==module)closeOriginalPassiveTransition(actual);
    }
    private static void closeOriginalPassiveTransition(RootPassiveTransition actual){
        if(actual==null)return;
        try{if(actual.activeNative!=null)dev.veltraph.client.service.mc.RootNativeHistory.closePassiveTransition(actual.activeNative,actual,actual.client);}
        catch(RuntimeException|Error unavailable){actual.revoked=true;}
        try{if(actual.idleNative!=null)dev.veltraph.client.observation.nativeinput.McNativeManualInputLineage.closePassiveTransition(actual.idleNative,actual,actual.client);}
        catch(RuntimeException|Error unavailable){actual.revoked=true;}
        finally{
            actual.closed=true;
            if(!actual.accepted||actual.revoked){actual.revoked=true;if(actual.publication!=null)actual.publication.revoked=true;if(actual.proof!=null)actual.proof.revoked=true;}
            if(pendingPassiveTransition==actual)pendingPassiveTransition=null;
        }
    }
    private static ModuleActivationGuard.OriginalActivationRegistration originalActivationRegistration;
    private static RootOriginalEnableAdmission originalEnableAdmission;
    /** New run request only. Exact private actual installed Guard request, before any activation provider. */
    public static final class RootOriginalEnableAdmission {
        private final ModuleActivationGuard.OriginalActivationStart request;private final RootOriginalNativeInstallation installation;
        private final ModuleManager registry;private final Module module;private final ConfigService store;
        private final ModuleManager.LifecycleSnapshot lifecycle;private final dev.veltraph.client.config.MapConfigService.StoredStateSnapshot settings;
        private final ScriptingInputLock.ControlSnapshot control;private final RootNoOwnerObservation noOwner;private final RootOriginalMenuRequest menuRequest;
        private final Minecraft client;private final Object player,world,listener,connection,options,gameMode,screen;
        private RootOriginalNativeEnrollment enrollment;private dev.veltraph.client.service.mc.RootNativeHistory.FreshEnableTicket nativeTicket;
        private boolean validated,revoked,enrolled;
        private RootOriginalEnableAdmission(ModuleActivationGuard.OriginalActivationStart request,RootOriginalNativeInstallation installation,RootNoOwnerObservation noOwner){
            this.request=request;this.installation=installation;this.noOwner=noOwner;menuRequest=originalMenuRequest;registry=moduleManager;module=request.module();store=config;lifecycle=request.originalBeforeLifecycle();
            settings=store instanceof dev.veltraph.client.config.MapConfigService m?m.storedStateSnapshot():store instanceof dev.veltraph.client.config.JsonConfigService j?j.storedStateSnapshot():null;
            control=installation.idleControl;client=installation.client();player=client.player;world=client.level;listener=client.getConnection();connection=client.getConnection().getConnection();
            options=client.options;gameMode=client.gameMode;screen=client.gui.screen();
        }
        public ModuleManager actualRegistryIdentity(){return registry;}public Module originalModule(){return module;}
        public ModuleActivationGuard guardIdentity(){return request.guardIdentity();}public RootNoOwnerObservation sourceNoOwnerIdentity(){return noOwner;}
        public boolean freshRequestCurrent(Minecraft actualClient){return !revoked&&this==originalEnableAdmission&&actualClient==client&&request.guardIdentity()==guard
            &&request.registryIdentity()==registry&&registry==moduleManager&&request.module()==module&&request.entryCurrent()
            &&originalActivationRegistration!=null&&originalActivationRegistration.current()&&installation==originalNativeInstallation&&installation.currentNativeNoOwner(client)
            &&store==config&&settings!=null&&settings.current()&&control!=null&&control.publicationCurrent()&&!control.active()&&control.ownerModuleId()==null
            &&registry.noGameplayOwnerRosterCurrent(lifecycle,originalPassiveSource)&&module instanceof dev.veltraph.client.core.AbstractModule&&registry.get(module.id()).orElse(null)==module
            &&client.isSameThread()&&client.player==player&&client.level==world&&client.getConnection()==listener&&listener!=null
            &&client.getConnection().getConnection()==connection&&client.options==options&&client.gameMode==gameMode&&client.gui.screen()==screen
            &&player!=null&&world!=null&&client.player.level()==client.level&&client.player.isAlive();}
        public boolean noOwnerCurrent(Minecraft actualClient){return freshRequestCurrent(actualClient)&&(menuRequest==null?noOwner!=null&&noOwner.current():menuRequest.module==module&&menuRequest.beforeGuardCurrent());}
        private boolean beforeEnabledCurrent(){return validated&&!enrolled&&request.beforeEnabledCurrent()&&noOwnerCurrent(client)
            &&nativeTicket!=null&&nativeTicket.currentFor(enrollment,client);}
    }
    private static Object captureOriginalEnableAdmission(ModuleActivationGuard.OriginalActivationStart actual){
        var dispatch=savedMaintenanceIntentDispatch;
        if(dispatch!=null){return dispatch.bindRequest(actual)?dispatch:null;}
        var deferred=savedMaintenanceDeferredActivation;
        if(deferred!=null){return deferred.bindRequest(actual)?deferred:null;}
        if(actual!=null&&originalPassiveSource!=null&&originalPassiveSource.declaredActor(actual.module()))return captureOriginalPassiveGuard(actual);
        var installation=originalNativeInstallation;var client=Minecraft.getInstance();
        if(actual==null||actual.guardIdentity()!=guard||actual.registryIdentity()!=moduleManager||!actual.entryCurrent()
            ||installation==null||!installation.currentNativeNoOwner(client)||!moduleManager.noGameplayOwnerRosterCurrent(actual.originalBeforeLifecycle(),originalPassiveSource))return null;
        var menu=originalMenuRequest;RootNoOwnerObservation noOwner=null;
        if(menu!=null){if(menu.module!=actual.module()||!menu.beforeGuardCurrent()||!actual.startedFrom(menu.lifecycle))return null;}
        else{var observed=rootRotationProvider.read();if(observed.owner()!=null||observed.noOwner()==null||!observed.noOwner().current()||!actual.entryCurrent())return null;noOwner=observed.noOwner();}
        var admission=new RootOriginalEnableAdmission(actual,installation,noOwner);originalEnableAdmission=admission;if(menu!=null)menu.admission=admission;
        admission.enrollment=new RootOriginalNativeEnrollment(admission);
        admission.nativeTicket=dev.veltraph.client.service.mc.RootNativeHistory.prepareFreshEnable(admission.enrollment,client);
        if(admission.nativeTicket==null||!admission.noOwnerCurrent(client)){admission.revoked=true;return null;}
        return admission;
    }
    private static void validateOriginalEnableAdmission(ModuleActivationGuard.OriginalActivationStart actual,Object captured){
        if(captured instanceof RootSavedMaintenanceIntentDispatch dispatch){dispatch.revoked=true;return;}
        if(captured instanceof RootSavedMaintenanceDeferredActivationCall deferred){
            if(deferred.request!=actual||!actual.beforeEnabledCurrent()||!deferred.current())deferred.revoked=true;return;}
        if(captured instanceof RootPassiveTransition passive){if(passive.guardRequest!=actual||!actual.beforeEnabledCurrent()||!passive.sourceBeforeCurrent(passive.client))passive.revoked=true;return;}
        if(!(captured instanceof RootOriginalEnableAdmission admission)||admission.request!=actual||admission!=originalEnableAdmission
            ||!actual.beforeEnabledCurrent()||!admission.noOwnerCurrent(admission.client)||admission.nativeTicket==null
            ||!admission.nativeTicket.currentFor(admission.enrollment,admission.client)){if(captured instanceof RootOriginalEnableAdmission rejected)rejected.revoked=true;return;}
        admission.validated=true;
    }
    private static void finishOriginalEnableAdmission(ModuleActivationGuard.OriginalActivationStart actual,Object captured){
        if(captured instanceof RootSavedMaintenanceIntentDispatch dispatch){dispatch.revoked=true;return;}
        if(captured instanceof RootSavedMaintenanceDeferredActivationCall deferred){
            if(deferred.request!=actual||!actual.normalEnabledReturned()||!deferred.current())deferred.revoked=true;return;}
        if(captured instanceof RootPassiveTransition passive){if(passive.guardRequest!=actual||passive.enabled==null||!passive.enabled.current()||!actual.normalEnabledReturned())passive.revoked=true;return;}
        if(!(captured instanceof RootOriginalEnableAdmission admission)||admission.request!=actual||admission!=originalEnableAdmission)return;
        if(!actual.normalEnabledReturned()||!admission.enrolled||originalEnableProof==null||originalEnableProof.start.parent()!=admission.module
            ||originalEnableProof.completed==null||!originalEnableProof.enableLineageCurrent())admission.revoked=true;
    }
    private static RootOriginalNativeInstallation originalNativeInstallation;
    /** Existing actual completed initialization/native installation. No player, pose or native history is seeded. */
    public static final class RootOriginalNativeInstallation {
        private final RootInputReleaseRegistration registration;private final ModuleManager registry;private final ConfigService store;
        private final FarmServices services;private final ScriptingInputLock.OriginalReleaseInstallation release;private final ModuleManager.OriginalStopBindings stops;
        private ScriptingInputLock.ControlSnapshot idleControl;private Object options;private net.minecraft.client.KeyMapping[] keys;
        private RootOriginalNativeInstallation(RootInputReleaseRegistration registration,ModuleManager registry,ConfigService store,FarmServices services,
                ScriptingInputLock.OriginalReleaseInstallation release,ModuleManager.OriginalStopBindings stops,ScriptingInputLock.ControlSnapshot idleControl){
            this.registration=registration;this.registry=registry;this.store=store;this.services=services;this.release=release;this.stops=stops;this.idleControl=idleControl;
        }
        public Minecraft client(){return registration.provider.client;}
        public ModuleManager managerIdentity(){return registry;}
        public RootInputReleaseRegistration sourceIdentity(){return registration;}
        /** Existing canonical service identity only; this lookup grants no input/camera operation. */
        public dev.veltraph.client.service.mc.McRotationService rotationServiceIdentity(Minecraft client){
            if(!currentFor(client)||!(services.rotation() instanceof dev.veltraph.client.service.mc.McRotationService actual)
                    ||!registration.currentFarmRotation(actual,client))return null;
            return actual;
        }
        public RootPrivatePassiveSource passiveRoleDeclaration(){return this==originalNativeInstallation?originalPassiveSource:null;}
        public boolean currentFor(Minecraft client){return this==originalNativeInstallation&&registration==rootInputReleaseRegistration&&registry==moduleManager
            &&store==config&&services==farmServices&&release.current()&&stops.current()&&registration.current(registration.actualInput(),client);}
        private boolean noOwnerContextCurrent(Minecraft client){return currentFor(client)&&registry.noGameplayOwnerRosterCurrent(registry.lifecycleSnapshot(),originalPassiveSource)
            &&rootNativeHigherPriorityIdle()&&(suspendedFarmReturn==null||!suspendedFarmReturn.active());}
        public boolean currentNativeNoOwner(Minecraft client){return noOwnerContextCurrent(client)&&idleControl!=null&&idleControl.publicationCurrent()
            &&!idleControl.active()&&idleControl.ownerModuleId()==null&&keysCurrent(client);}
        private boolean keysCurrent(Minecraft client){if(options==null||keys==null||client.options!=options)return false;var o=client.options;
            var current=new net.minecraft.client.KeyMapping[]{o.keyUp,o.keyDown,o.keyLeft,o.keyRight,o.keyJump,o.keyShift,o.keySprint,o.keyAttack,o.keyUse};
            for(int i=0;i<keys.length;i++)if(keys[i]!=current[i])return false;return true;}
    }
    public static RootOriginalNativeInstallation originalNativeInstallationFor(Minecraft client){
        var original=originalNativeInstallation;try{return original!=null&&original.currentFor(client)?original:null;}catch(RuntimeException|Error unavailable){return null;}
    }
    /** Only the actual original Root native phase binds original options/key identities; never a getter/factory or history seed. */
    private static void bindOriginalNativeInstallationKeys(Minecraft client){
        var original=originalNativeInstallation;if(original==null||original.options!=null||client.options==null||!original.noOwnerContextCurrent(client)
            ||original.idleControl==null||!original.idleControl.publicationCurrent()||original.idleControl.active()||original.idleControl.ownerModuleId()!=null)return;
        var o=client.options;original.options=o;original.keys=new net.minecraft.client.KeyMapping[]{o.keyUp,o.keyDown,o.keyLeft,o.keyRight,o.keyJump,o.keyShift,o.keySprint,o.keyAttack,o.keyUse};
    }
    private static RootOriginalNativeEnrollment originalNativeEnrollment;
    private static ModuleManager.ActualEnableRegistration originalEnableRegistration;
    private static ModuleManager.ActualDisableRegistration originalDisableRegistration;
    private static RootOriginalExpectedDisable pendingOriginalExpectedDisable;
    private static RootOriginalEnableProof originalEnableProof;
    private static RootOriginalExpectedEnable pendingOriginalExpectedEnable;
    private static RootOriginalInputPublication originalInputPublication;
    private static RootOriginalOwnershipWrite originalOwnershipWrite;
    private static RootOriginalModuleTickInvocation originalModuleTickInvocation;

    private static final class RootOriginalModuleTickInvocation {
        final ModuleManager registry;final RootOriginalNativeEnrollment enrollment;final RootOriginalInputPublication publication;
        final RootOriginalGameplayTickInvocation gameplay;
        final Thread thread=Thread.currentThread();boolean revoked,normalReturned,closed;
        private RootOriginalModuleTickInvocation(RootOriginalGameplayTickInvocation gameplay){this.gameplay=gameplay;
            registry=moduleManager;enrollment=originalNativeEnrollment;publication=originalInputPublication;}
        boolean nativeCurrent(){return !revoked&&!closed&&Thread.currentThread()==thread&&originalModuleTickInvocation==this
            &&gameplay!=null&&gameplay.activeNativeCurrent();}
        boolean normalSourceCurrent(){return !revoked&&closed&&normalReturned&&Thread.currentThread()==thread
            &&gameplay!=null&&gameplay.sourceOriginCurrent();}
        boolean currentFor(Module exact){return nativeCurrent()
            &&registry==moduleManager&&enrollment==originalNativeEnrollment&&publication==originalInputPublication&&publication!=null
            &&publication.original==exact&&publication.logicalHistoryCurrent()&&registry.actualModuleTickCurrent(exact);}
        boolean currentHelper(RootOriginalNativeHelperPublication helper){return nativeCurrent()
            &&registry==moduleManager&&enrollment==originalNativeEnrollment&&publication==originalInputPublication&&helper!=null&&helper.publication==publication
            &&helper.currentFor(helper.helper,enrollment.registration.provider.client)&&registry.actualModuleTickCurrent(helper.helper);}
        boolean currentSavedReturn(FarmMaintenanceCoordinator.SavedReturnAlignmentSource source){return nativeCurrent()&&registry==moduleManager&&enrollment==originalNativeEnrollment&&publication==originalInputPublication
            &&source!=null&&publication!=null&&source.originalInputPublication()==publication&&publication.savedReturnLogicalCurrent(source)
            &&registry.actualModuleTickCurrent(source.inputOwner());}
    }
    /** Callback-free existing ordinary input issuer. Public/generic service calls cannot create it. */
    public static Module originalInputIssuingModule(dev.veltraph.client.service.mc.McInputService service){
        try{var source=originalNativeEnrollment;var proof=originalEnableProof;
            if(source==null||proof==null||!source.registration.currentFarmInput(service,source.registration.provider.client))return null;
            var cycle=originalCycleTick;var cycleActor=cycle==null?null:cycle.pausedInput(service);
            if(cycleActor!=null&&source.originalProducerCurrent(cycleActor,source.registration.provider.client))return cycleActor;
            var terminal=originalNativeTermination;
            if(terminal!=null&&terminal.enrollment==source){var actor=moduleManager.actualOwnDisableModule();
                if(actor!=null&&terminal.cleanupNativeCurrent(actor,source.registration.provider.client))return actor;}
            var maintenance=originalNativeMaintenancePause;
            if(maintenance!=null&&maintenance.enrollment==source&&maintenance.suspensionEpisodeCurrent())return maintenance.farm;
            var pause=originalNativePauseInvocation;
            if(pause!=null&&pause.enrollment==source&&pause.beforeNativeCurrent())return pause.farm;
            if(proof.completed==null&&proof.enrollment==source&&proof.start.enabledCurrent()
                &&source.originalProducerCurrent(proof.parent,source.registration.provider.client))return proof.parent;
            var actor=moduleManager.actualModuleTickModule();var tick=originalModuleTickInvocation;
            return actor!=null&&tick!=null&&tick.enrollment==source&&tick.currentFor(actor)
                &&source.originalProducerCurrent(actor,source.registration.provider.client)?actor:null;
        }catch(RuntimeException|Error unavailable){return null;}
    }
    /** Existing private issuer only, sampled before any rotation authority/provider callback. */
    public static Module originalCameraIssuingModule(dev.veltraph.client.service.mc.McRotationService service){
        try{
            var source=originalNativeEnrollment;var proof=originalEnableProof;
            if(source==null||proof==null||!source.registration.currentFarmRotation(service,source.registration.provider.client))return null;
            var cycle=originalCycleTick;var cycleActor=cycle==null?null:cycle.pausedCamera(service);
            if(cycleActor!=null&&source.originalProducerCurrent(cycleActor,source.registration.provider.client))return cycleActor;
            var termination=originalNativeTermination;
            if(termination!=null&&termination.enrollment==source){var stopping=moduleManager.actualOwnDisableModule();
                if(stopping!=null&&termination.cleanupNativeCurrent(stopping,source.registration.provider.client))return stopping;
            }
            var maintenance=originalNativeMaintenancePause;
            if(maintenance!=null&&maintenance.enrollment==source&&maintenance.suspensionEpisodeCurrent())return maintenance.farm;
            var pause=originalNativePauseInvocation;
            if(pause!=null&&pause.enrollment==source&&source.originalCameraIssuingCurrent(pause.farm,service))return pause.farm;
            if(proof.completed==null)return proof.start.enabledCurrent()&&source.originalProducerCurrent(proof.parent,source.registration.provider.client)?proof.parent:null;
            var helper=originalInputPublication==null?null:originalInputPublication.nativeHelperPublication;
            if(helper!=null&&helper.enrollment==source&&helper.currentFor(helper.helper,source.registration.provider.client)){
                var pending=pendingOriginalExpectedDisable;var actualTick=originalModuleTickInvocation;
                if(pending!=null&&pending.publication==helper.publication&&pending.start.parent()==helper.helper&&pending.inProgressCurrent()
                    ||actualTick!=null&&actualTick.currentHelper(helper)
                    ||originalRotationAdvanceInvocation!=null&&originalRotationAdvanceInvocation.currentFor(helper.original))return helper.helper;
            }
            var tick=originalModuleTickInvocation;var module=moduleManager.actualModuleTickModule();
            if(tick!=null&&module!=null&&tick.currentFor(module)&&source.originalCameraIssuingCurrent(module,service))return module;
            var advance=originalRotationAdvanceInvocation;return advance!=null&&advance.currentFor(proof.parent)?proof.parent:null;
        }catch(RuntimeException|Error unavailable){return null;}
    }
    private static void tickOriginalRootModules(){
        RootOriginalModuleTickInvocation actual=null;
        try{if(originalModuleTickInvocation!=null)originalModuleTickInvocation.revoked=true;
            else{var gameplay=originalGameplayTickInvocation;
                if(gameplay!=null&&gameplay.activeNativeCurrent()){actual=new RootOriginalModuleTickInvocation(gameplay);originalModuleTickInvocation=actual;}}}
        catch(Throwable unavailable){if(actual!=null)actual.revoked=true;}
        lastReturnedOriginalModuleTick=null;
        boolean originalNormal=false;
        try{moduleManager.tick();originalNormal=true;}
        finally{if(actual!=null){
            try{actual.normalReturned=originalNormal&&actual.nativeCurrent();actual.closed=true;
                if(actual.normalReturned)lastReturnedOriginalModuleTick=actual;
                else{actual.revoked=true;if(!originalNormal&&actual.publication!=null)actual.publication.revoked=true;}}
            catch(Throwable unavailable){actual.normalReturned=false;actual.revoked=true;actual.closed=true;}
            finally{if(originalModuleTickInvocation==actual)originalModuleTickInvocation=null;}}}
    }
    private static RootOriginalModuleTickInvocation lastReturnedOriginalModuleTick;
    private static RootOriginalRotationAdvanceInvocation originalRotationAdvanceInvocation;
    private static final class RootOriginalRotationAdvanceInvocation {
        final RootOriginalModuleTickInvocation previous;final RootOriginalGameplayTickInvocation gameplay;
        final Thread thread=Thread.currentThread();boolean revoked,normalReturned,closed;
        private RootOriginalRotationAdvanceInvocation(RootOriginalModuleTickInvocation previous,RootOriginalGameplayTickInvocation gameplay){
            this.previous=previous;this.gameplay=gameplay;}
        boolean nativeCurrent(){return !revoked&&!closed&&originalRotationAdvanceInvocation==this&&Thread.currentThread()==thread
            &&gameplay!=null&&gameplay.activeNativeCurrent()&&previous!=null&&previous.gameplay==gameplay&&previous.normalSourceCurrent();}
        boolean normalSourceCurrent(){return !revoked&&closed&&normalReturned&&Thread.currentThread()==thread
            &&gameplay!=null&&gameplay.sourceOriginCurrent()&&previous!=null&&previous.gameplay==gameplay&&previous.normalSourceCurrent();}
        boolean currentFor(Module exact){return nativeCurrent()&&previous!=null
            &&previous==lastReturnedOriginalModuleTick&&previous.normalReturned&&!previous.revoked&&previous.registry==moduleManager
            &&previous.enrollment==originalNativeEnrollment&&previous.publication==originalInputPublication&&previous.publication!=null
            &&previous.publication.original==exact&&previous.publication.logicalHistoryCurrent();}
        boolean currentSavedReturn(RootSavedReturnCameraQueueSource source){return nativeCurrent()&&previous!=null&&previous==lastReturnedOriginalModuleTick&&previous.normalReturned&&!previous.revoked
            &&previous.registry==moduleManager&&previous.enrollment==originalNativeEnrollment&&previous.publication==originalInputPublication
            &&source!=null&&previous.publication==source.publication&&source.storedCurrent();}
    }
    private static void tickOriginalRootRotation(){
        RootOriginalRotationAdvanceInvocation actual=null;
        try{if(originalRotationAdvanceInvocation!=null)originalRotationAdvanceInvocation.revoked=true;
            else{var gameplay=originalGameplayTickInvocation;var previous=lastReturnedOriginalModuleTick;
                if(gameplay!=null&&gameplay.activeNativeCurrent()&&previous!=null&&previous.gameplay==gameplay&&previous.normalSourceCurrent()){
                    actual=new RootOriginalRotationAdvanceInvocation(previous,gameplay);originalRotationAdvanceInvocation=actual;}}}
        catch(Throwable unavailable){if(actual!=null)actual.revoked=true;}
        boolean originalNormal=false;
        try{farmServices.rotation().tickRotation();originalNormal=true;}
        finally{if(actual!=null){
            try{actual.normalReturned=originalNormal&&actual.nativeCurrent();actual.closed=true;if(!actual.normalReturned)actual.revoked=true;}
            catch(Throwable unavailable){actual.normalReturned=false;actual.revoked=true;actual.closed=true;}
            finally{if(originalRotationAdvanceInvocation==actual)originalRotationAdvanceInvocation=null;}}}
    }
    /** Exact original service request declaration. A generic rotateTo/current owner cannot manufacture it. */
    public static final class RootOriginalCameraQueueSource {
        private RootCameraDeclarationPassiveContinuation passiveContinuation;
        private final RootOriginalModuleTickInvocation tick;private final ModuleManager.ModuleTickDeclaration declaration;
        private final RootOriginalInputPublication publication;private final RootOriginalNativeEnrollment enrollment;
        private final Module module;private final dev.veltraph.client.service.mc.McRotationService service;private final Thread thread=Thread.currentThread();
        private final Object player,world,listener,connection,options,gameMode;
        public RootCameraDeclarationPassiveContinuation capturePassiveContinuation(RootPassiveTransition transition,dev.veltraph.client.service.mc.McRotationService exactService){
            return captureCameraDeclarationPassiveContinuation(this,transition,exactService);
        }
        private RootOriginalCameraQueueSource(Module module,dev.veltraph.client.service.mc.McRotationService service,RootOriginalModuleTickInvocation tick,ModuleManager.ModuleTickDeclaration declaration){
            this.module=module;this.service=service;this.tick=tick;this.declaration=declaration;publication=originalInputPublication;enrollment=originalNativeEnrollment;
            var c=enrollment.registration.provider.client;player=c.player;world=c.level;listener=c.getConnection();connection=c.getConnection().getConnection();options=c.options;gameMode=c.gameMode;
        }
        private boolean storedCurrent(){var c=enrollment.registration.provider.client;return Thread.currentThread()==thread&&publication==originalInputPublication&&enrollment==originalNativeEnrollment
            &&publication!=null&&publication.original==module&&publication.logicalHistoryCurrent()&&enrollment.registration.currentFarmRotation(service,c)
            &&c.player==player&&c.level==world&&c.getConnection()==listener&&listener!=null&&c.getConnection().getConnection()==connection&&c.options==options&&c.gameMode==gameMode;}
        public boolean originalDeclarationCurrent(){return storedCurrent()&&tick.normalSourceCurrent()&&declaration.normalReturnedCurrent(module);}
        public boolean originalAdvanceCurrent(dev.veltraph.client.service.mc.McRotationService exactService){var advance=originalRotationAdvanceInvocation;
            return exactService==service&&originalDeclarationCurrent()&&advance!=null&&advance.currentFor(module);}
        /** Captured only by the genuine service advance before its authority/provider callbacks. */
        public RootOriginalCameraAdvanceInvocation captureAdvanceInvocation(dev.veltraph.client.service.mc.McRotationService exactService){
            try{boolean namedAdvance=StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).walk(stream->{var caller=stream.skip(1).findFirst().orElse(null);
                    return caller!=null&&caller.getDeclaringClass()==dev.veltraph.client.service.mc.McRotationService.class&&caller.getMethodName().equals("advance");});
                var advance=originalRotationAdvanceInvocation;
                return namedAdvance&&originalAdvanceCurrent(exactService)&&advance!=null?new RootOriginalCameraAdvanceInvocation(this,advance):null;
            }catch(RuntimeException|Error unavailable){return null;}
        }
        public Module originalModule(){return module;}public RootOriginalNativeEnrollment enrollmentIdentity(){return enrollment;}
    }
    /** Original closed tick declaration only; SourceP and Service request capsules must join the same commit. */
    public static final class RootCameraDeclarationPassiveContinuation {
        private final RootOriginalCameraQueueSource ordinary;private final RootSavedReturnCameraQueueSource saved;
        private final RootPassiveTransition transition;private final RootOriginalModuleTickInvocation tick;
        private final ModuleManager.ModuleTickDeclaration declaration;private final ModuleManager.ModuleTickPassiveContinuation core;
        private final RootOriginalInputPublication publication;private final RootOriginalNativeEnrollment enrollment;
        private final dev.veltraph.client.service.mc.McRotationService service;private final Thread thread=Thread.currentThread();
        private boolean prepared,claimed,revoked;
        private RootCameraDeclarationPassiveContinuation(Object source,RootPassiveTransition transition,ModuleManager.ModuleTickPassiveContinuation core){
            ordinary=source instanceof RootOriginalCameraQueueSource actual?actual:null;saved=source instanceof RootSavedReturnCameraQueueSource actual?actual:null;
            this.transition=transition;this.core=core;tick=ordinary!=null?ordinary.tick:saved.tick;declaration=ordinary!=null?ordinary.declaration:saved.declaration;
            publication=ordinary!=null?ordinary.publication:saved.publication;enrollment=ordinary!=null?ordinary.enrollment:saved.enrollment;
            service=ordinary!=null?ordinary.service:saved.service;
        }
        public boolean rawCurrent(){return !claimed&&!revoked&&Thread.currentThread()==thread
            &&(ordinary!=null?ordinary.passiveContinuation==this&&ordinary.tick==tick&&ordinary.declaration==declaration
                &&ordinary.publication==publication&&ordinary.enrollment==enrollment&&ordinary.service==service
                :saved!=null&&saved.passiveContinuation==this&&saved.tick==tick&&saved.declaration==declaration
                    &&saved.publication==publication&&saved.enrollment==enrollment&&saved.service==service)
            &&tick.normalSourceCurrent()&&tick.registry==transition.registry&&tick.enrollment==enrollment&&tick.publication==publication
            &&transition.publication==publication&&publication==originalInputPublication&&!publication.revoked
            &&enrollment==originalNativeEnrollment&&core.rawCurrent();}
        public boolean prepare(){
            if(prepared||!rawCurrent()){revoked=true;return false;}
            if(!transition.normalReturnedCurrent(transition.client)||(ordinary!=null?!ordinary.storedCurrent():!saved.storedCurrent())
                ||!core.prepare()||!rawCurrent()||!transition.normalReturnedCurrent(transition.client)){revoked=true;return false;}
            prepared=true;return true;
        }
        public boolean commit(){
            if(!prepared||!transition.passiveDeclarationCommitWindowCurrent()||!rawCurrent()){revoked=true;return false;}
            if(!core.commit()){revoked=true;return false;}
            claimed=true;if(ordinary!=null)ordinary.passiveContinuation=null;else saved.passiveContinuation=null;return true;
        }
    }
    private static RootCameraDeclarationPassiveContinuation captureCameraDeclarationPassiveContinuation(Object source,RootPassiveTransition transition,
            dev.veltraph.client.service.mc.McRotationService exactService){
        if(transition==null||exactService==null)return null;
        var ordinary=source instanceof RootOriginalCameraQueueSource actual?actual:null;
        var saved=source instanceof RootSavedReturnCameraQueueSource actual?actual:null;
        if(ordinary==null&&saved==null)return null;
        var pending=ordinary!=null?ordinary.passiveContinuation:saved.passiveContinuation;
        if(pending!=null){pending.revoked=true;return null;}
        var service=ordinary!=null?ordinary.service:saved.service;var publication=ordinary!=null?ordinary.publication:saved.publication;
        var declaration=ordinary!=null?ordinary.declaration:saved.declaration;var module=ordinary!=null?ordinary.module:saved.module;
        if(service!=exactService||publication!=transition.publication||publication==null
            ||!transition.sourceBeforeCurrent(transition.client)||(ordinary!=null?!ordinary.originalDeclarationCurrent():!saved.originalDeclarationCurrent())
            ||!transition.sourceBeforeCurrent(transition.client))return null;
        var core=declaration.capturePassiveContinuation(transition,module);if(core==null)return null;
        var next=new RootCameraDeclarationPassiveContinuation(source,transition,core);
        if(ordinary!=null)ordinary.passiveContinuation=next;else saved.passiveContinuation=next;
        return next.rawCurrent()?next:null;
    }
    /** One actual Root rotation invocation retained through its normal close; no later active-pointer resampling. */
    public static final class RootOriginalCameraAdvanceInvocation {
        private final RootOriginalCameraQueueSource source;private final RootOriginalRotationAdvanceInvocation advance;
        private final Thread thread=Thread.currentThread();
        private RootOriginalCameraAdvanceInvocation(RootOriginalCameraQueueSource source,RootOriginalRotationAdvanceInvocation advance){this.source=source;this.advance=advance;}
        public boolean issuingCurrent(){try{return Thread.currentThread()==thread&&!advance.closed&&!advance.revoked
            &&originalRotationAdvanceInvocation==advance&&source.originalAdvanceCurrent(source.service);}
            catch(RuntimeException|Error unavailable){return false;}}
        public boolean publicationCurrent(){try{return Thread.currentThread()==thread&&advance.normalSourceCurrent()
            &&advance.previous!=null&&advance.previous.normalReturned&&!advance.previous.revoked&&advance.previous.enrollment==source.enrollment
            &&advance.previous.publication==source.publication&&source.originalDeclarationCurrent();}
            catch(RuntimeException|Error unavailable){return false;}}
        public RootOriginalCameraQueueSource sourceIdentity(){return source;}
    }
    public static RootOriginalCameraQueueSource captureOriginalCameraQueueSource(Module exact,dev.veltraph.client.service.mc.McRotationService service){
        try{
            boolean actualEntry=StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).walk(stream->{var caller=stream.skip(1).findFirst().orElse(null);
                return caller!=null&&caller.getDeclaringClass()==dev.veltraph.client.service.mc.McRotationService.class&&caller.getMethodName().equals("rotateTo");});
            var tick=originalModuleTickInvocation;var source=originalNativeEnrollment;
            if(!actualEntry||source==null||tick==null||!tick.currentFor(exact)||!source.originalCameraIssuingCurrent(exact,service))return null;
            var declaration=moduleManager.currentModuleTickDeclaration(exact);return declaration==null?null:new RootOriginalCameraQueueSource(exact,service,tick,declaration);
        }catch(RuntimeException|Error unavailable){return null;}
    }
    /** Distinct original maintenance ALIGN declaration, retained from the authentic helper callback. */
    public static final class RootSavedReturnCameraQueueSource {
        private RootCameraDeclarationPassiveContinuation passiveContinuation;
        private final FarmMaintenanceCoordinator.SavedReturnAlignmentSource source;
        private final RootOriginalModuleTickInvocation tick;private final ModuleManager.ModuleTickDeclaration declaration;
        private final RootOriginalInputPublication publication;private final RootOriginalNativeEnrollment enrollment;
        private final FarmMaintenanceCoordinator.SavedMaintenanceEnableSource enableDeclaration;
        private final Module module;private final dev.veltraph.client.service.mc.McRotationService service;
        private final Object checkpoint,pause,token,player,world,listener,connection,options,gameMode;private final Thread thread=Thread.currentThread();
        public RootCameraDeclarationPassiveContinuation capturePassiveContinuation(RootPassiveTransition transition,dev.veltraph.client.service.mc.McRotationService exactService){
            return captureCameraDeclarationPassiveContinuation(this,transition,exactService);
        }
        private RootSavedReturnCameraQueueSource(FarmMaintenanceCoordinator.SavedReturnAlignmentSource source,dev.veltraph.client.service.mc.McRotationService service,
                RootOriginalModuleTickInvocation tick,ModuleManager.ModuleTickDeclaration declaration){
            this.source=source;this.service=service;this.tick=tick;this.declaration=declaration;publication=source.originalInputPublication();enrollment=publication.enrollment;
            enableDeclaration=source.originalEnableDeclaration();module=source.inputOwner();checkpoint=source.originalCheckpointIdentity();pause=source.originalPauseIdentity();token=source.cameraOwner();
            var c=enrollment.registration.provider.client;player=c.player;world=c.level;listener=c.getConnection();connection=c.getConnection().getConnection();options=c.options;gameMode=c.gameMode;
        }
        private boolean storedCurrent(){var c=enrollment.registration.provider.client;return Thread.currentThread()==thread&&publication==originalInputPublication
            &&enrollment==originalNativeEnrollment&&source.originalInputPublication()==publication&&source.originalEnableDeclaration()==enableDeclaration
            &&publication.savedEnableDeclaration==enableDeclaration&&module==source.inputOwner()&&checkpoint==source.originalCheckpointIdentity()
            &&pause==source.originalPauseIdentity()&&token==source.cameraOwner()&&source.rotationServiceIdentity()==service&&source.registryIdentity()==moduleManager
            &&publication.savedReturnLogicalCurrent(source)&&enrollment.registration.currentFarmRotation(service,c)
            &&c.player==player&&c.level==world&&c.getConnection()==listener&&listener!=null&&c.getConnection().getConnection()==connection&&c.options==options&&c.gameMode==gameMode;}
        public boolean originalDeclarationCurrent(){try{return storedCurrent()&&tick.normalSourceCurrent()&&declaration.normalReturnedCurrent(module)
            &&source.publicationAffiliationCurrent(publication)&&storedCurrent();}catch(RuntimeException|Error unavailable){return false;}}
        public boolean originalAdvanceCurrent(dev.veltraph.client.service.mc.McRotationService exactService){try{var actual=originalRotationAdvanceInvocation;
            return exactService==service&&originalDeclarationCurrent()&&actual!=null&&actual.currentSavedReturn(this);}catch(RuntimeException|Error unavailable){return false;}}
        public RootSavedReturnCameraAdvanceInvocation captureAdvanceInvocation(dev.veltraph.client.service.mc.McRotationService exactService){
            try{boolean named=StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).walk(stream->{var caller=stream.skip(1).findFirst().orElse(null);
                return caller!=null&&caller.getDeclaringClass()==dev.veltraph.client.service.mc.McRotationService.class&&caller.getMethodName().equals("advance");});
                var actual=originalRotationAdvanceInvocation;return named&&actual!=null&&originalAdvanceCurrent(exactService)?new RootSavedReturnCameraAdvanceInvocation(this,actual):null;
            }catch(RuntimeException|Error unavailable){return null;}}
        public FarmMaintenanceCoordinator.SavedReturnAlignmentSource sourceIdentity(){return source;}
        public RootOriginalNativeEnrollment enrollmentIdentity(){return enrollment;}
    }
    public static final class RootSavedReturnCameraAdvanceInvocation {
        private final RootSavedReturnCameraQueueSource source;private final RootOriginalRotationAdvanceInvocation advance;private final Thread thread=Thread.currentThread();
        private RootSavedReturnCameraAdvanceInvocation(RootSavedReturnCameraQueueSource source,RootOriginalRotationAdvanceInvocation advance){this.source=source;this.advance=advance;}
        public boolean issuingCurrent(){try{return Thread.currentThread()==thread&&!advance.closed&&!advance.revoked&&originalRotationAdvanceInvocation==advance
            &&source.originalAdvanceCurrent(source.service);}catch(RuntimeException|Error unavailable){return false;}}
        public boolean publicationCurrent(){try{return Thread.currentThread()==thread&&advance.normalSourceCurrent()&&advance.previous!=null
            &&advance.previous.normalReturned&&!advance.previous.revoked&&advance.previous.enrollment==source.enrollment&&advance.previous.publication==source.publication
            &&source.originalDeclarationCurrent();}catch(RuntimeException|Error unavailable){return false;}}
        public RootSavedReturnCameraQueueSource sourceIdentity(){return source;}
    }
    public static RootSavedReturnCameraQueueSource captureSavedReturnCameraQueueSource(FarmMaintenanceCoordinator.SavedReturnAlignmentSource source,
            dev.veltraph.client.service.mc.McRotationService service){
        try{boolean namedEntry=StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).walk(stream->{var caller=stream.skip(1).findFirst().orElse(null);
            return caller!=null&&caller.getDeclaringClass()==dev.veltraph.client.service.mc.McRotationService.class&&caller.getMethodName().equals("rotateToSavedReturnOwned");});
            var tick=originalModuleTickInvocation;var publication=originalInputPublication;
            if(!namedEntry||source==null||publication==null||source.originalInputPublication()!=publication||tick==null||!tick.currentSavedReturn(source)
                ||!publication.currentForSavedReturn(source)||!publication.enrollment.registration.currentFarmRotation(service,Minecraft.getInstance()))return null;
            var declaration=moduleManager.currentModuleTickDeclaration(source.inputOwner());
            return declaration!=null?new RootSavedReturnCameraQueueSource(source,service,tick,declaration):null;
        }catch(RuntimeException|Error unavailable){return null;}}
    /** Immutable installation plus authentic idle control history; never constructed from an active run. */
    public static final class RootOriginalNativeEnrollment {
        private final RootInputReleaseRegistration registration;
        private final ScriptingInputLock.ControlSnapshot installationControl;
        private final RootOriginalEnableAdmission admission;
        private ScriptingInputLock.ControlSnapshot idleControl;
        private RootNoOwnerObservation noOwner;
        private ModuleManager.LifecycleSnapshot idleLifecycle;
        private dev.veltraph.client.service.mc.RootNativeHistory nativeHistory;
        private Object player,world,listener,connection,options,gameMode;
        private boolean attempted,revoked;
        private RootOriginalNativeEnrollment(RootInputReleaseRegistration registration){
            this.registration=registration;installationControl=ScriptingInputLock.originalPublicationSnapshot();idleControl=installationControl;admission=null;
        }
        private RootOriginalNativeEnrollment(RootOriginalEnableAdmission admission){
            this.admission=admission;registration=admission.installation.registration;installationControl=admission.control;idleControl=admission.control;
            noOwner=admission.noOwner;idleLifecycle=admission.lifecycle;player=admission.player;world=admission.world;listener=admission.listener;
            connection=admission.connection;options=admission.options;gameMode=admission.gameMode;
        }
        /** Before-onEnable fresh admission only, without native/history/provider callbacks or current-pose seed. */
        public boolean freshAdmissionCurrent(Minecraft client){return !revoked&&admission!=null&&admission.enrollment==this&&admission.noOwnerCurrent(client);}
        private boolean sceneCurrent(Minecraft client){return !revoked&&this==originalNativeEnrollment&&registration==rootInputReleaseRegistration
            &&admission!=null&&admission.request.originalBindingsCurrent()
            &&registration.current(registration.actualInput(),client)&&client==Minecraft.getInstance()&&client.isSameThread()
            &&player!=null&&world!=null&&listener!=null&&connection!=null&&client.player==player&&client.level==world
            &&client.getConnection()==listener&&client.getConnection().getConnection()==connection&&client.options==options&&client.gameMode==gameMode
            &&((net.minecraft.network.Connection)connection).isConnected()&&client.player.level()==client.level&&client.player.isAlive()
            &&(admission==null||admission.menuRequest==null||admission.menuRequest.enrolledSceneCurrent());}
        private boolean logicalIdleCurrent(){var client=Minecraft.getInstance();return !revoked&&this==originalNativeEnrollment
            &&registration==rootInputReleaseRegistration&&registration.current(registration.actualInput(),client)
            &&originalInputPublication==null&&originalEnableProof==null&&idleControl!=null&&idleControl.publicationCurrent()
            &&!idleControl.active()&&idleControl.ownerModuleId()==null&&moduleManager.lifecycleSnapshot().noneEnabledCurrent(moduleManager)
            &&rootNativeHigherPriorityIdle();}
        public boolean initialNoOwnerCurrent(Minecraft client){return sceneCurrent(client)&&originalInputPublication==null
            &&idleControl!=null&&idleControl.publicationCurrent()&&!idleControl.active()&&idleControl.ownerModuleId()==null
            &&idleLifecycle!=null&&idleLifecycle.noneEnabledCurrent(moduleManager)&&noOwner!=null&&noOwner.current()
            &&client.gui.screen()==null&&!client.isPaused()&&client.getCameraEntity()==client.player;}
        /** Ordinary actual input method-entry only; saved restore uses its separate exact instruction producer. */
        public boolean originalInputIssuingCurrent(Module exact,dev.veltraph.client.service.mc.McInputService service){
            var client=registration.provider.client;
            if(!registration.currentFarmInput(service,client)||!originalProducerCurrent(exact,client))return false;
            var cycle=originalCycleTick;if(cycle!=null&&cycle.pausedInput(service)==exact)return true;
            var terminal=originalNativeTermination;
            if(terminal!=null&&terminal.enrollment==this&&moduleManager.actualOwnDisableModule()==exact&&terminal.cleanupNativeCurrent(exact,client))return true;
            var maintenance=originalNativeMaintenancePause;
            if(maintenance!=null&&maintenance.enrollment==this&&maintenance.farm==exact
                &&(maintenance.suspensionEpisodeCurrent()||maintenance.postureEpisodeCurrent()))return true;
            var pause=originalNativePauseInvocation;if(pause!=null&&pause.enrollment==this&&pause.farm==exact&&pause.beforeNativeCurrent())return true;
            var proof=originalEnableProof;
            if(proof!=null&&proof.enrollment==this&&proof.parent==exact&&proof.completed==null)return proof.start.enabledCurrent();
            var tick=originalModuleTickInvocation;return tick!=null&&tick.enrollment==this&&tick.currentFor(exact);
        }
        /** Issuing only. Render-time application instead inspects the tick-independent original producer. */
        public boolean originalCameraIssuingCurrent(Module exact,dev.veltraph.client.service.mc.McRotationService service){
            var client=registration.provider.client;
            if(!registration.currentFarmRotation(service,client))return false;
            var helper=originalInputPublication==null?null:originalInputPublication.nativeHelperPublication;
            if(helper!=null&&helper.enrollment==this&&helper.currentFor(exact,client)){
                var pending=pendingOriginalExpectedDisable;var actualTick=originalModuleTickInvocation;
                return pending!=null&&pending.publication==helper.publication&&pending.start.parent()==exact&&pending.inProgressCurrent()
                    ||actualTick!=null&&actualTick.currentHelper(helper)
                    ||originalRotationAdvanceInvocation!=null&&originalRotationAdvanceInvocation.currentFor(helper.original);
            }
            if(!originalProducerCurrent(exact,client))return false;
            var cycle=originalCycleTick;if(cycle!=null&&cycle.pausedCamera(service)==exact)return true;
            var terminal=originalNativeTermination;
            if(terminal!=null&&terminal.enrollment==this&&moduleManager.actualOwnDisableModule()==exact&&terminal.cleanupNativeCurrent(exact,client))return true;
            var maintenance=originalNativeMaintenancePause;
            if(maintenance!=null&&maintenance.enrollment==this&&maintenance.farm==exact&&maintenance.suspensionEpisodeCurrent())return true;
            var pause=originalNativePauseInvocation;
            if(pause!=null&&pause.enrollment==this&&pause.farm==exact&&pause.beforeNativeCurrent())return true;
            var proof=originalEnableProof;
            if(proof!=null&&proof.enrollment==this&&proof.parent==exact&&proof.completed==null)return proof.start.enabledCurrent();
            var tick=originalModuleTickInvocation;return tick!=null&&tick.enrollment==this&&tick.currentFor(exact);
        }
        /** Stored authentic affiliation outside the Root tick, including legitimate camera render application. */
        public boolean originalProducerCurrent(Module exact,Minecraft client){
            if(farmMaintenance!=null&&farmMaintenance.failedRollbackBlocksOriginalProducer(exact))return false;
            if(!sceneCurrent(client)||originalEnableRegistration==null||!originalEnableRegistration.current()||originalDisableRegistration==null||!originalDisableRegistration.current())return false;
            var maintenance=originalNativeMaintenancePause;
            if(maintenance!=null&&maintenance.enrollment==this&&maintenance.farm==exact&&maintenance.producerCurrent())return true;
            var pause=originalNativePauseInvocation;if(pause!=null&&pause.enrollment==this&&pause.farm==exact&&pause.beforeNativeCurrent())return true;
            var rollback=savedMaintenanceRollbackInvocation;if(rollback!=null&&rollback.enrollment==this&&rollback.farm==exact
                &&rollback.matchesRollback(rollback.call)&&rollback.completedPause.acceptedNormalPauseProof()&&originalGameplayInvocationCurrent())return true;
            var terminal=originalNativeTermination;if(terminal!=null&&terminal.enrollment==this&&terminal.cleanupNativeCurrent(exact,client))return true;
            var helper=originalInputPublication==null?null:originalInputPublication.nativeHelperPublication;
            if(helper!=null&&helper.enrollment==this&&helper.helper==exact)return helper.currentFor(exact,client);
            var proof=originalEnableProof;if(proof==null||proof.enrollment!=this||proof.parent!=exact||!proof.enableLineageCurrent())return false;
            var publication=originalInputPublication;
            return publication==null?idleControl!=null&&idleControl.publicationCurrent():publication.enrollment==this&&publication.original==exact&&publication.logicalHistoryCurrent();
        }
    }
    private static final class RootOriginalEnableProof {
        final RootOriginalNativeEnrollment enrollment;final ModuleManager.ActualEnableStart start;final Module parent;
        final ScriptingInputLock.ControlSnapshot before;ModuleManager.OwnEnableReceipt completed;ModuleManager.LifecycleSnapshot continuity;boolean revoked;
        RootOriginalEnableProof(RootOriginalNativeEnrollment enrollment,ModuleManager.ActualEnableStart start){this.enrollment=enrollment;this.start=start;parent=start.parent();before=enrollment.idleControl;}
        boolean enableLineageCurrent(){return !revoked&&this==originalEnableProof&&enrollment.admission!=null
            &&enrollment.admission.request.originalEnableSourceCurrent(parent,completed)&&originalModuleTickRegistration!=null&&originalModuleTickRegistration.current()&&originalEnableRegistration!=null&&originalEnableRegistration.current()&&originalDisableRegistration!=null&&originalDisableRegistration.current()
            &&start.registryIdentity()==moduleManager&&(completed==null?start.enabledCurrent():completed.actualStart()==start&&completed.parentEnableHistoryCurrent()
                &&(moduleManager.lifecycleCurrent(continuity)||pendingOriginalExpectedEnable!=null&&pendingOriginalExpectedEnable.original==this&&pendingOriginalExpectedEnable.inProgressCurrent()
                    ||pendingOriginalExpectedDisable!=null&&pendingOriginalExpectedDisable.original==this&&pendingOriginalExpectedDisable.inProgressCurrent()
                    ||pendingPassiveTransition!=null&&pendingPassiveTransition.proof==this&&pendingPassiveTransition.lifecycleInProgressCurrent(continuity,enrollment.registration.provider.client)));}
    }
    private static final class RootOriginalExpectedEnable {
        final RootOriginalEnableProof original;final RootOriginalInputPublication publication;
        final ModuleManager.ActualEnableStart start;final Object declaration;
        final ScriptingInputLock.ControlSnapshot control;final Thread thread=Thread.currentThread();boolean revoked;
        private RootOriginalExpectedEnable(RootOriginalEnableProof original,RootOriginalInputPublication publication,ModuleManager.ActualEnableStart start,Object declaration){
            this.original=original;this.publication=publication;this.start=start;this.declaration=declaration;control=publication.input.control();
        }
        private boolean inProgressCurrent(){return !revoked&&pendingOriginalExpectedEnable==this&&original==originalEnableProof&&publication==originalInputPublication
            &&Thread.currentThread()==thread&&control.publicationCurrent()&&start.registryIdentity()==moduleManager&&start.enabledCurrent();}
        private boolean completedCurrent(ModuleManager.OwnEnableReceipt actual){
            if(revoked||pendingOriginalExpectedEnable!=this||original!=originalEnableProof||publication!=originalInputPublication||Thread.currentThread()!=thread
                ||!control.publicationCurrent()||actual.actualStart()!=start||!actual.current())return false;
            if(declaration instanceof dev.veltraph.client.rewarp.mc.McSuspendedFarmReturnCoordinator.DriverEnableSource source)return source.matchesCompletedEnable(actual);
            if(declaration instanceof RootRetainedHelperEnableDeclaration source)return source.matchesCompletedEnable(actual);
            if(declaration instanceof FarmMaintenanceCoordinator.SavedMaintenanceEnableSource source)return source.matchesCompletedEnable(actual);
            return false;
        }
    }
    private static final class RootOriginalExpectedDisable {
        private final RootOriginalEnableProof original;private final RootOriginalInputPublication publication;
        private final ModuleManager.ActualDisableStart start;private final Object declaration;
        private final ScriptingInputLock.ControlSnapshot control;private final Thread thread=Thread.currentThread();private boolean revoked;
        private RootOriginalExpectedDisable(RootOriginalEnableProof original,RootOriginalInputPublication publication,ModuleManager.ActualDisableStart start,
                Object declaration){this.original=original;this.publication=publication;this.start=start;this.declaration=declaration;control=publication.input.control();}
        private boolean inProgressCurrent(){return !revoked&&pendingOriginalExpectedDisable==this&&original==originalEnableProof&&publication==originalInputPublication
            &&Thread.currentThread()==thread&&control.publicationCurrent()&&start.registryIdentity()==moduleManager&&start.disabledCurrent();}
        private boolean completedCurrent(ModuleManager.OwnDisableReceipt returned){return !revoked&&pendingOriginalExpectedDisable==this&&original==originalEnableProof
            &&publication==originalInputPublication&&Thread.currentThread()==thread&&control.publicationCurrent()&&returned!=null&&returned.actualStart()==start
            &&returned.closedCurrent()&&(declaration instanceof FarmMaintenanceCoordinator.RetainedLoadoutOperation retained&&retained.matchesCompletedDisable(returned)
                ||declaration instanceof FarmMaintenanceCoordinator.SavedMaintenanceResumeCall resume&&resume.matchesCompletedDisable(returned)
                ||declaration instanceof FarmMaintenanceCoordinator.SavedMaintenanceRollbackCall rollback&&rollback.matchesCompletedDisable(returned));}
    }
    private static final class RootOriginalTerminalDisable {
        private final RootOriginalNativeTermination terminal;private final ModuleManager.ActualDisableStart start;
        private RootOriginalTerminalDisable(RootOriginalNativeTermination terminal,ModuleManager.ActualDisableStart start){this.terminal=terminal;this.start=start;}
        private boolean normalCurrent(ModuleManager.OwnDisableReceipt actual){return terminal==originalNativeTermination&&terminal.pendingOwnerDisable==this
            &&start.originalManualInstruction()!=null&&actual!=null&&actual.actualStart()==start&&actual.parent()==terminal.original
            &&actual.closedCurrent()&&terminal.sceneCurrent(terminal.frame.client)&&terminal.frame.control.publicationCurrent()
            &&terminal.publication==originalInputPublication&&start.startedFrom(terminal.terminalLifecycle);}
    }
    private static RootOriginalStandardNormalReturnCleanupAdmission originalStandardNormalReturnCleanupAdmission;
    /** Original ordinary Fairy/control source only. This inert admission grants no input, camera or native-use action. */
    public static final class RootOriginalStandardNormalReturnCleanupAdmission {
        private final dev.veltraph.client.navigation.mc.LegacyTravelPublicationLease.Leg.StandardNormalReturnCleanupSource source;
        private final dev.veltraph.client.navigation.mc.LegacyTravelPublicationLease.Leg leg;private final dev.veltraph.client.skills.FairySoulModule owner;
        private final OwnedUseControlSnapshot control;private final RootOriginalInputPublication publication;private final RootOriginalEnableProof proof;
        private final RootOriginalNativeEnrollment enrollment;private final RootOriginalNativeInstallation installation;
        private final ScriptingInputLock.RootOriginalPublicationReceipt input;private final ModuleManager.ModuleTickDeclaration declaration;
        private final Thread thread=Thread.currentThread();private ModuleManager.ActualDisableStart disabling;
        private RootOriginalStandardNormalReturnCleanupAdmission(dev.veltraph.client.navigation.mc.LegacyTravelPublicationLease.Leg.StandardNormalReturnCleanupSource source,
                OwnedUseControlSnapshot control,RootOriginalInputPublication publication,ModuleManager.ModuleTickDeclaration declaration){
            this.source=source;leg=source.originalLegIdentity();owner=source.originalOwner();this.control=control;this.publication=publication;
            proof=publication.enable;enrollment=publication.enrollment;installation=originalNativeInstallation;input=publication.input;this.declaration=declaration;
        }
        private boolean originalFieldsCurrent(){var client=installation==null?null:installation.client();
            return originalStandardNormalReturnCleanupAdmission==this&&Thread.currentThread()==thread&&client!=null&&client==Minecraft.getInstance()&&client.isSameThread()
                &&source.originalLegIdentity()==leg&&source.originalOwner()==owner&&source.originalOwnedControlIdentity()==control
                &&publication==originalInputPublication&&!publication.revoked&&publication.original==owner&&publication.publishedOwner==owner
                &&publication.savedEnableDeclaration==null&&publication.nativeHelperPublication==null&&proof==originalEnableProof&&proof==publication.enable
                &&proof.parent==owner&&proof.completed!=null&&proof.completed.actualStart()==proof.start&&enrollment==originalNativeEnrollment
                &&enrollment==publication.enrollment&&enrollment.sceneCurrent(client)&&installation==originalNativeInstallation&&installation.currentFor(client)
                &&control.registry==moduleManager&&control.store==config&&control.expectedMaintenance==null&&control.originalStandardCleanupIdentitiesCurrent()
                &&input==publication.input&&input!=null&&input.current()
                &&input.control().publicationCurrent()&&input.control().active()&&owner.id().equals(input.control().ownerModuleId())
                &&originalEnableRegistration!=null&&originalEnableRegistration.current()&&originalDisableRegistration!=null&&originalDisableRegistration.current()
                &&originalModuleTickRegistration!=null&&originalModuleTickRegistration.current();}
        private boolean startingDisableCurrent(ModuleManager.ActualDisableStart actual){return actual!=null&&disabling==null&&originalFieldsCurrent()
            &&actual.registryIdentity()==moduleManager&&actual.parent()==owner&&actual.startingCurrent()&&actual.startedFrom(control.effectiveLifecycle)
            &&source.rawOriginalLoanCurrent(installation.client())&&proof.enableLineageCurrent()&&publication.logicalHistoryCurrent()&&control.current()
            &&actual.startingCurrent()&&source.rawOriginalLoanCurrent(installation.client())&&originalFieldsCurrent();}
        public boolean rawPendingCurrent(dev.veltraph.client.navigation.mc.LegacyTravelPublicationLease.Leg.StandardNormalReturnCleanupSource exact,ModuleManager.PendingOwnDisable pending){
            if(exact!=source||!originalFieldsCurrent()||pending==null||pending.registryIdentity()!=moduleManager||!pending.current(owner)
                ||moduleManager.pendingActualOwnDisable(owner).orElse(null)!=pending||!moduleManager.pendingOwnDisableFromMatches(control.effectiveLifecycle,owner)
                ||!source.rawOriginalLoanCurrent(installation.client()))return false;
            if(disabling!=null)return proof.revoked&&disabling.registryIdentity()==moduleManager&&disabling.parent()==owner
                &&disabling.disabledCurrent()&&disabling.startedFrom(control.effectiveLifecycle);
            var terminal=originalNativeTermination;
            return terminal!=null&&terminal.publication==publication&&terminal.original==owner&&terminal.enrollment==enrollment&&terminal.installation==installation
                &&terminal.pendingOwnerDisable!=null&&terminal.pendingOwnerDisable.start.parent()==owner
                &&terminal.pendingOwnerDisable.start.disabledCurrent()&&terminal.pendingOwnerDisable.start.startedFrom(control.effectiveLifecycle)
                &&terminal.cleanupNativeCurrent(owner,installation.client());
        }
        public boolean pendingCleanupCurrent(dev.veltraph.client.navigation.mc.LegacyTravelPublicationLease.Leg.StandardNormalReturnCleanupSource exact,ModuleManager.PendingOwnDisable pending){
            return rawPendingCurrent(exact,pending)&&control.originalStandardCleanupCurrent(owner,pending)&&rawPendingCurrent(exact,pending);}
    }
    /** Captures only a privately constructed actual ordinary admission from the real Leg selector, never public flags or source callbacks. */
    public static RootOriginalStandardNormalReturnCleanupAdmission captureOriginalStandardNormalReturnCleanupAdmission(
            dev.veltraph.client.navigation.mc.LegacyTravelPublicationLease.Leg.StandardNormalReturnCleanupSource source){
        try{boolean caller=StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).walk(stream->{var first=stream.skip(1).findFirst().orElse(null);
                return first!=null&&first.getDeclaringClass()==dev.veltraph.client.navigation.mc.LegacyTravelPublicationLease.Leg.class
                    &&first.getMethodName().equals("selectNormalReturnDeviceSlotLoanOwned");});
            var publication=originalInputPublication;var tick=originalModuleTickInvocation;var client=Minecraft.getInstance();
            if(!caller||source==null||publication==null||source.originalOwner()!=publication.original||publication.publishedOwner!=publication.original
                ||!(source.originalOwnedControlIdentity() instanceof OwnedUseControlSnapshot control)||control.expectedMaintenance!=null||!control.current()
                ||!source.positiveAdmissionCurrent(client)||!publication.nativeHistoryCurrent()||tick==null||!tick.nativeCurrent()
                ||tick.registry!=moduleManager||tick.enrollment!=publication.enrollment||tick.publication!=publication)return null;
            var declaration=moduleManager.currentModuleTickDeclaration(source.originalOwner());
            if(declaration==null||!declaration.issuingCurrent(source.originalOwner())||!source.positiveAdmissionCurrent(client))return null;
            var actual=new RootOriginalStandardNormalReturnCleanupAdmission(source,control,publication,declaration);originalStandardNormalReturnCleanupAdmission=actual;
            return actual.originalFieldsCurrent()&&control.current()&&source.positiveAdmissionCurrent(client)?actual:null;
        }catch(RuntimeException|Error unavailable){return null;}
    }
    private static RootOriginalNormalReturnCleanupDisable normalReturnCleanupDisable;
    /** Exact actual direct-owner disable entry, retained solely for old normal-return cleanup. */
    private static final class RootOriginalNormalReturnCleanupDisable {
        private final RootOriginalInputPublication publication;private final RootOriginalEnableProof proof;
        private final RootOriginalNativeEnrollment enrollment;private final RootOriginalNativeInstallation installation;
        private final ModuleManager.ActualDisableStart start;private final ModuleManager.LifecycleSnapshot before;
        private final ScriptingInputLock.RootOriginalPublicationReceipt input;private final Thread thread=Thread.currentThread();
        private RootOriginalNormalReturnCleanupDisable(RootOriginalInputPublication publication,ModuleManager.ActualDisableStart start){
            this.publication=publication;proof=publication.enable;enrollment=publication.enrollment;installation=originalNativeInstallation;
            this.start=start;before=start.beforeSnapshot();input=publication.input;
        }
        private boolean current(RootOriginalInputPublication exact,Module owner,Minecraft client,ModuleManager.PendingOwnDisable pending){
            return normalReturnCleanupDisable==this&&publication==exact&&publication==originalInputPublication&&!publication.revoked
                &&proof==originalEnableProof&&proof==publication.enable&&proof.revoked&&proof.parent==owner&&proof.completed!=null
                &&proof.completed.actualStart()==proof.start&&enrollment==originalNativeEnrollment&&enrollment==publication.enrollment
                &&installation==originalNativeInstallation&&installation!=null&&installation.currentFor(client)&&enrollment.sceneCurrent(client)
                &&Thread.currentThread()==thread&&start.registryIdentity()==moduleManager&&start.parent()==owner&&start.disabledCurrent()
                &&pending!=null&&pending.registryIdentity()==moduleManager&&pending.current(owner)
                &&moduleManager.pendingActualOwnDisable(owner).orElse(null)==pending&&moduleManager.pendingOwnDisableFromMatches(before,owner)
                &&publication.original==owner&&publication.publishedOwner==owner&&input==publication.input&&input!=null&&input.current()
                &&input.control().publicationCurrent()&&input.control().active()&&owner.id().equals(input.control().ownerModuleId());
        }
    }
    private static Object captureOriginalRootDisable(ModuleManager.ActualDisableStart actual){
        var terminalSource=originalNativeTermination;
        if(actual!=null&&terminalSource!=null&&actual.parent()==terminalSource.original&&actual.registryIdentity()==moduleManager
            &&actual.startingCurrent()&&actual.originalManualInstruction()!=null&&terminalSource.pendingOwnerDisable==null
            &&terminalSource.originalTerminalLineageCurrent(Minecraft.getInstance())&&actual.startedFrom(terminalSource.terminalLifecycle)
            &&terminalSource.frame.start.nativeStopCurrent(actual.parent())){
            var owner=new RootOriginalTerminalDisable(terminalSource,actual);terminalSource.pendingOwnerDisable=owner;return owner;
        }
        if(pendingPassiveTransition==null&&actual!=null&&originalPassiveSource!=null&&originalPassiveSource.declaredActor(actual.parent())
            &&terminalSource!=null&&terminalSource.publication==originalInputPublication&&!terminalSource.revoked&&!terminalSource.logicalClosed
            &&actual.registryIdentity()==moduleManager&&actual.startingCurrent()&&actual.originalManualInstruction()!=null
            &&terminalSource.frame.start.nativeStopCurrent(actual.parent()))
            return beginOriginalPassiveTransition(actual.parent(),actual.beforeSnapshot(),null,null,null,terminalSource,actual);
        var passive=pendingPassiveTransition;
        if(passive!=null&&actual!=null&&actual.parent()==passive.passive){
            if(passive.disabling!=null||passive.guardRequest!=null||!actual.startingCurrent()||!actual.startedFrom(passive.before)||!passive.sourceBeforeCurrent(passive.client)){passive.revoked=true;return null;}
            passive.disabling=actual;return passive;
        }
        var original=originalEnableProof;var publication=originalInputPublication;
        if(actual==null||original==null||publication==null||actual.registryIdentity()!=moduleManager||!actual.startingCurrent())return null;
        var terminal=originalNativeTermination;
        if(terminal!=null&&terminal.publication==publication&&terminal.cleanupNativeCurrent(actual.parent(),Minecraft.getInstance()))return null;
        var standard=originalStandardNormalReturnCleanupAdmission;
        if(standard!=null&&standard.startingDisableCurrent(actual)){
            standard.disabling=actual;original.revoked=true;return standard;
        }
        // Observe the genuine old owner before the stored enabled bit flips. This never grants input/use permission.
        if(actual.parent()==publication.original&&publication.publishedOwner==publication.original
            &&(publication.original instanceof dev.veltraph.client.fishing.module.FishingModule
                ||publication.original instanceof dev.veltraph.client.foraging.replant.ForagingReplantModule)
            &&publication.savedEnableDeclaration==null&&publication.nativeHelperPublication==null&&original.completed!=null
            &&original.enableLineageCurrent()&&publication.logicalHistoryCurrent()&&actual.startedFrom(original.continuity)){
            var cleanup=new RootOriginalNormalReturnCleanupDisable(publication,actual);normalReturnCleanupDisable=cleanup;
            original.revoked=true;return cleanup;
        }
        var rollback=farmMaintenance==null?null:farmMaintenance.pendingSavedMaintenanceRollbackDisableForRoot(actual);
        if(rollback!=null){
            if(rollback.originalInputPublication()!=publication||rollback.farm()!=publication.original||rollback.helperIdentity()!=actual.parent()
                ||pendingOriginalExpectedDisable!=null||!actual.startedFrom(original.continuity)||!rollback.claimOriginalDisable(actual))return null;
            if(!original.enableLineageCurrent()||!publication.nativeHistoryCurrent()||!actual.startingCurrent()||!actual.startedFrom(original.continuity))return null;
            var expected=new RootOriginalExpectedDisable(original,publication,actual,rollback);pendingOriginalExpectedDisable=expected;return expected;
        }
        var retained=farmMaintenance==null?null:farmMaintenance.pendingRetainedMaintenanceDisableForRoot(actual);
        if(retained!=null){
            if(retained.originalInputPublication()!=publication||retained.originalEnableDeclaration()!=publication.savedEnableDeclaration
                ||retained.farm()!=publication.original||retained.helperIdentity()!=actual.parent()||pendingOriginalExpectedDisable!=null
                ||!actual.startedFrom(original.continuity)||!retained.claimOriginalDisable(actual)){original.revoked=true;return null;}
            if(!original.enableLineageCurrent()||!publication.nativeHistoryCurrent()||!actual.startingCurrent()||!actual.startedFrom(original.continuity)){
                original.revoked=true;return null;}
            var expected=new RootOriginalExpectedDisable(original,publication,actual,retained);pendingOriginalExpectedDisable=expected;return expected;
        }
        var declared=farmMaintenance==null?null:farmMaintenance.pendingSavedMaintenanceDisableForRoot(actual);
        if(declared==null||declared.originalInputPublication()!=publication||declared.originalEnableDeclaration()!=publication.savedEnableDeclaration
            ||declared.farm()!=publication.original||declared.helperIdentity()!=actual.parent()||pendingOriginalExpectedDisable!=null
            ||!actual.startedFrom(original.continuity)){original.revoked=true;return null;}
        if(!declared.claimOriginalDisable(actual)){original.revoked=true;return null;}
        if(!original.enableLineageCurrent()||!publication.nativeHistoryCurrent()||!actual.startingCurrent()||!actual.startedFrom(original.continuity)){
            original.revoked=true;return null;}
        var expected=new RootOriginalExpectedDisable(original,publication,actual,declared);pendingOriginalExpectedDisable=expected;return expected;
    }
    private static void completeOriginalRootDisable(ModuleManager.OwnDisableReceipt returned,Object captured){
        if(captured instanceof RootOriginalStandardNormalReturnCleanupAdmission standard){
            if(originalStandardNormalReturnCleanupAdmission==standard)originalStandardNormalReturnCleanupAdmission=null;
            return;
        }
        if(captured instanceof RootOriginalNormalReturnCleanupDisable cleanup){
            if(normalReturnCleanupDisable==cleanup)normalReturnCleanupDisable=null;
            return;
        }
        if(captured instanceof RootOriginalTerminalDisable owner){
            if(owner.normalCurrent(returned)){owner.terminal.ownerReturned=returned;owner.terminal.terminalLifecycle=returned.stoppedSnapshot();}
            else owner.terminal.revoked=true;
            return;
        }
        if(captured instanceof RootPassiveTransition passive){
            if(returned!=null&&returned.actualStart()==passive.disabling&&returned.parent()==passive.passive&&returned.closedCurrent())passive.disabled=returned;else passive.revoked=true;
            if(passive.terminalStop!=null)try{acceptOriginalPassiveTransition(passive,returned==null?null:returned.stoppedSnapshot());}
                catch(RuntimeException|Error unavailable){passive.revoked=true;}finally{closeOriginalPassiveTransition(passive);}
            return;
        }
        if(!(captured instanceof RootOriginalExpectedDisable expected))return;
        try{if(!expected.completedCurrent(returned)){expected.original.revoked=true;return;}
            var stopped=returned.stoppedSnapshot();if(stopped==null){expected.original.revoked=true;return;}
            expected.original.continuity=stopped;expected.publication.lifecycle=stopped;
            var retained=retainedMaintenanceResumeInvocation;
            if(retained!=null&&retained.call==expected.declaration&&retained.publication==expected.publication&&retained.matchesRetained(retained.call))retained.disabled=returned;
            var resume=savedMaintenanceResumeInvocation;
            if(resume!=null&&resume.call==expected.declaration&&resume.publication==expected.publication&&resume.matchesResume(resume.call))resume.disabled=returned;
            var rollback=savedMaintenanceRollbackInvocation;
            if(rollback!=null&&rollback.call==expected.declaration&&rollback.publication==expected.publication&&rollback.matchesRollback(rollback.call))rollback.disabled=returned;
            if(!expected.original.enableLineageCurrent())expected.original.revoked=true;
        }finally{expected.revoked=true;if(pendingOriginalExpectedDisable==expected)pendingOriginalExpectedDisable=null;}
    }
    private static Object captureOriginalRootEnable(ModuleManager.ActualEnableStart actual){
        var passive=pendingPassiveTransition;
        if(passive!=null&&actual!=null&&actual.parent()==passive.passive){
            if(passive.enabling!=null||passive.guardRequest==null||!passive.guardRequest.beforeEnabledCurrent()||!actual.startingCurrent()
                ||!actual.startedFrom(passive.before)||!passive.sourceBeforeCurrent(passive.client)){passive.revoked=true;return null;}
            passive.enabling=actual;return passive;
        }
        var source=originalNativeEnrollment;var client=Minecraft.getInstance();var original=originalEnableProof;
        if(actual==null||actual.registryIdentity()!=moduleManager||!actual.startingCurrent())return null;
        var fresh=originalEnableAdmission;
        if(fresh!=null&&actual.parent()==fresh.module&&actual.startedFrom(fresh.lifecycle)&&fresh.beforeEnabledCurrent()){
            if(originalInputPublication!=null)originalInputPublication.revoked=true;
            if(original!=null)original.revoked=true;
            var next=fresh.enrollment;var nativeHistory=dev.veltraph.client.service.mc.RootNativeHistory.enrollFreshEnable(next,fresh.nativeTicket,client);
            if(nativeHistory==null){fresh.revoked=true;return null;}
            next.nativeHistory=nativeHistory;next.attempted=true;originalNativeEnrollment=next;
            originalInputPublication=null;pendingOriginalExpectedEnable=null;
            var proof=new RootOriginalEnableProof(next,actual);originalEnableProof=proof;fresh.enrolled=true;return proof;
        }
        if(source==null)return null;
        if(original!=null){
            var publication=originalInputPublication;
            if(original.completed==null||publication==null||pendingOriginalExpectedEnable!=null||!actual.startedFrom(original.continuity)){
                original.revoked=true;return null;
            }
            Object declared=null;
            var driver=suspendedFarmReturn==null?null:suspendedFarmReturn.pendingDriverEnableForRoot(actual);
            if(driver!=null&&driver.originalInputPublication()==publication&&driver.farm()==original.parent&&driver.driverIdentity()==actual.parent()){
                if(!driver.claimOriginalEnable(actual)){original.revoked=true;return null;}declared=driver;
            }else{
                var saved=farmMaintenance==null?null:farmMaintenance.pendingSavedMaintenanceEnableForRoot(actual);
                if(saved!=null&&saved.originalInputPublication()==publication&&saved.farm()==original.parent&&saved.helperIdentity()==actual.parent()){
                    RootRetainedHelperEnableDeclaration retained=null;
                    if(actual.parent()==earlyRetainedLoadout){
                        var selected=retainedLoadoutDispatch;retained=selected==null?null:selected.helperEnableDeclaration;
                        if(retained==null||!retained.beginSavedEnable(saved,actual)){original.revoked=true;return null;}
                    }
                    if(!saved.claimOriginalEnable(actual)){if(retained!=null)retained.revoked=true;original.revoked=true;return null;}
                    if(retained!=null&&!retained.savedClaimReturnedCurrent()){retained.revoked=true;original.revoked=true;return null;}
                    declared=saved;
                }else{original.revoked=true;return null;}
            }
            // The typed declaration has consumed its single actual invocation. Fresh negative inspections follow.
            if(!original.enableLineageCurrent()||!publication.nativeHistoryCurrent()||!actual.startedFrom(original.continuity)||!actual.startingCurrent()){
                original.revoked=true;return null;
            }
            var expected=new RootOriginalExpectedEnable(original,publication,actual,declared);pendingOriginalExpectedEnable=expected;return expected;
        }
        // A raw setEnabled or an unavailable fresh request preserves generic behavior but has no original publication authority.
        return null;
    }
    private static void completeOriginalRootEnable(ModuleManager.OwnEnableReceipt actual,Object captured){
        if(captured instanceof RootPassiveTransition passive){if(actual!=null&&actual.actualStart()==passive.enabling&&actual.parent()==passive.passive&&actual.current())passive.enabled=actual;else passive.revoked=true;return;}
        if(captured instanceof RootOriginalExpectedEnable expected){
            try{
                if(!expected.completedCurrent(actual)){expected.original.revoked=true;return;}
                var returned=actual.completedSnapshot();if(returned==null){expected.original.revoked=true;return;}
                // Exact declared actual enable normal return, never a fresh current-roster rebaseline.
                expected.original.continuity=returned;
                if(expected.declaration instanceof FarmMaintenanceCoordinator.SavedMaintenanceEnableSource saved){
                    expected.publication.savedEnableDeclaration=saved;expected.publication.savedHelperEnable=actual;
                    if(actual.parent()==earlyRetainedLoadout){
                        var retained=retainedLoadoutDispatch;var declared=retained==null?null:retained.helperEnableDeclaration;
                        if(declared==null||!declared.observeSavedCompleted(actual,saved)){expected.original.revoked=true;return;}
                    }
                }
                if(!expected.original.enableLineageCurrent()||!expected.publication.nativeHistoryCurrent())expected.original.revoked=true;
            }finally{expected.revoked=true;if(pendingOriginalExpectedEnable==expected)pendingOriginalExpectedEnable=null;}
            return;
        }
        if(!(captured instanceof RootOriginalEnableProof proof)||proof!=originalEnableProof||proof.revoked||actual.actualStart()!=proof.start
            ||actual.parent()!=proof.parent||!actual.current()||proof.before==null||!proof.before.publicationCurrent())return;
        proof.completed=actual;proof.continuity=actual.completedSnapshot();
        if(!proof.enableLineageCurrent()||!proof.enrollment.nativeHistory.currentFor(proof.parent,Minecraft.getInstance()))proof.revoked=true;
    }
    /** Retained original Root publication. Public inspection cannot create, renew or repair the receipt. */
    public static final class RootOriginalInputPublication {
        private final RootOriginalNativeEnrollment enrollment;private final RootOriginalEnableProof enable;
        private final Module original;private final dev.veltraph.client.service.mc.RootNativeHistory nativeHistory;
        private ScriptingInputLock.RootOriginalPublicationReceipt input;
        private Module publishedOwner;private ModuleManager.LifecycleSnapshot lifecycle;private boolean revoked;
        private FarmMaintenanceCoordinator.SavedMaintenanceEnableSource savedEnableDeclaration;
        private ModuleManager.OwnEnableReceipt savedHelperEnable;private RootOriginalNativeHelperPublication nativeHelperPublication;
        private RootOriginalInputPublication(RootOriginalOwnershipWrite actual,ScriptingInputLock.RootOriginalPublicationReceipt returned){
            enrollment=actual.enrollment;enable=actual.enable;original=enable.parent;nativeHistory=enrollment.nativeHistory;
            input=returned;publishedOwner=actual.selected;lifecycle=actual.lifecycle;
        }
        private boolean logicalHistoryCurrent(){return !revoked&&this==originalInputPublication&&enrollment.sceneCurrent(Minecraft.getInstance())
            &&input!=null&&input.current()&&enable.enableLineageCurrent();}
        public boolean nativeHistoryCurrent(){try{return logicalHistoryCurrent()&&nativeHistory!=null&&nativeHistory.currentFor(original,Minecraft.getInstance());}
            catch(RuntimeException|Error unavailable){revoked=true;return false;}}
        public boolean currentFor(Module exact){try{return exact==original&&publishedOwner==exact&&logicalHistoryCurrent()
            &&(moduleManager.lifecycleCurrent(lifecycle)||pendingPassiveTransition!=null&&pendingPassiveTransition.publication==this
                &&pendingPassiveTransition.lifecycleInProgressCurrent(lifecycle,Minecraft.getInstance()))&&input.control().active()&&exact.id().equals(input.control().ownerModuleId())&&nativeHistoryCurrent();}
            catch(RuntimeException|Error unavailable){revoked=true;return false;}}
        private boolean savedReturnLogicalCurrent(FarmMaintenanceCoordinator.SavedReturnAlignmentSource exact){
            return exact!=null&&savedEnableDeclaration!=null&&savedHelperEnable!=null&&savedHelperEnable.actualStart()!=null
                &&exact.originalInputPublication()==this&&exact.originalFarm()==original&&exact.originalEnableDeclaration()==savedEnableDeclaration
                &&savedEnableDeclaration.originalInputPublication()==this&&publishedOwner==savedEnableDeclaration.helperIdentity()
                &&savedHelperEnable.parent()==publishedOwner&&savedHelperEnable.parentEnableHistoryCurrent()&&logicalHistoryCurrent()
                &&moduleManager.lifecycleCurrent(lifecycle)&&input.control().active()&&publishedOwner.id().equals(input.control().ownerModuleId());
        }
        private boolean loadedReturnDeviceLogicalCurrent(FarmMaintenanceCoordinator.LoadedReturnDeviceAuthority exact,boolean cleanup){
            return exact!=null&&savedEnableDeclaration!=null&&savedHelperEnable!=null&&savedHelperEnable.actualStart()!=null
                &&exact.coordinatorIdentity()==farmMaintenance&&exact.originalInputPublication()==this&&exact.originalFarm()==original
                &&exact.originalEnableDeclaration()==savedEnableDeclaration&&savedEnableDeclaration.originalInputPublication()==this
                &&publishedOwner==savedEnableDeclaration.helperIdentity()&&publishedOwner==exact.inputOwner()
                &&savedEnableDeclaration.maintenanceToken()==exact.cameraOwner()&&savedHelperEnable.parent()==publishedOwner
                &&savedHelperEnable.parentEnableHistoryCurrent()&&logicalHistoryCurrent()
                &&(cleanup?moduleManager.ownCleanupLifecycleCurrent(lifecycle,publishedOwner):moduleManager.lifecycleCurrent(lifecycle))
                &&input.control().active()&&publishedOwner.id().equals(input.control().ownerModuleId());
        }
        /** Separate actual loaded-maintenance source; the original helper-active and ALIGN methods remain unchanged. */
        public boolean currentForLoadedReturnDevice(FarmMaintenanceCoordinator.LoadedReturnDeviceAuthority exact){try{
            return loadedReturnDeviceLogicalCurrent(exact,false)&&nativeHistoryCurrent()
                &&exact.publicationAffiliationCurrent(this)&&loadedReturnDeviceLogicalCurrent(exact,false);
        }catch(RuntimeException|Error unavailable){revoked=true;return false;}}
        /** Completed own-slot witness affiliation for the original RETURNING/READY observer only, never a device use. */
        public boolean currentForLoadedReturnDeviceRetired(FarmMaintenanceCoordinator.LoadedReturnDeviceAuthority exact){try{
            return loadedReturnDeviceLogicalCurrent(exact,true)&&nativeHistoryCurrent()
                &&exact.retiredPublicationAffiliationCurrent(this)&&loadedReturnDeviceLogicalCurrent(exact,true);
        }catch(RuntimeException|Error unavailable){revoked=true;return false;}}
        /** Exact retained publication affiliation for cleanup only; it cannot authorize ability use or a new source. */
        public boolean currentForLoadedReturnDeviceCleanup(FarmMaintenanceCoordinator.LoadedReturnDeviceAuthority exact){try{
            return loadedReturnDeviceLogicalCurrent(exact,true)&&nativeHistoryCurrent()
                &&exact.cleanupPublicationAffiliationCurrent(this)&&loadedReturnDeviceLogicalCurrent(exact,true);
        }catch(RuntimeException|Error unavailable){revoked=true;return false;}}
        /** Exact original saved-return affiliation after the genuine helper publication, never a current owner lookup. */
        public boolean currentForSavedReturn(FarmMaintenanceCoordinator.SavedReturnAlignmentSource exact){try{return savedReturnLogicalCurrent(exact)
            &&nativeHistoryCurrent()&&exact.publicationAffiliationCurrent(this)&&savedReturnLogicalCurrent(exact);}
            catch(RuntimeException|Error unavailable){revoked=true;return false;}}
    }
    /** Retained exact helper logical handoff; native observers still require their own genuine opcode normal-return receipts. */
    public static final class RootOriginalNativeHelperPublication {
        private final RootOriginalInputPublication publication;private final RootOriginalNativeEnrollment enrollment;
        private final Module original,helper;private final FarmMaintenanceCoordinator coordinator;
        private final FarmMaintenanceCoordinator.SavedMaintenanceEnableSource declaration;private final ModuleManager.OwnEnableReceipt enable;
        private final Object checkpoint,pause,token;private final Object player,world,listener,connection,options,gameMode;
        private ScriptingInputLock.RootOriginalPublicationReceipt control;private RootSavedMaintenanceResumeInvocation completedResume;private RootSavedMaintenanceRollbackInvocation completedRollback;private RootRetainedMaintenanceResumeInvocation completedRetained;private boolean revoked;private final Thread thread=Thread.currentThread();
        private RootOriginalNativeHelperPublication(RootOriginalInputPublication publication,ScriptingInputLock.RootOriginalPublicationReceipt control){
            this.publication=publication;this.control=control;enrollment=publication.enrollment;original=publication.original;helper=publication.publishedOwner;
            coordinator=farmMaintenance;declaration=publication.savedEnableDeclaration;enable=publication.savedHelperEnable;
            checkpoint=declaration.originalCheckpointIdentity();pause=declaration.originalPauseIdentity();token=declaration.maintenanceToken();
            var c=enrollment.registration.provider.client;player=c.player;world=c.level;listener=c.getConnection();connection=c.getConnection().getConnection();options=c.options;gameMode=c.gameMode;
        }
        private boolean originalLogicalCurrent(Minecraft client){return !revoked&&Thread.currentThread()==thread&&publication==originalInputPublication
            &&publication.nativeHelperPublication==this&&enrollment==originalNativeEnrollment&&publication.enrollment==enrollment&&publication.original==original
            &&coordinator==farmMaintenance&&declaration==publication.savedEnableDeclaration&&enable==publication.savedHelperEnable
            &&declaration.originalInputPublication()==publication&&declaration.farm()==original&&declaration.helperIdentity()==helper
            &&checkpoint==declaration.originalCheckpointIdentity()&&pause==declaration.originalPauseIdentity()&&token==declaration.maintenanceToken()
            &&control==publication.input&&control.current()&&publication.logicalHistoryCurrent()&&client==enrollment.registration.provider.client&&client.isSameThread()
            &&client.player==player&&client.level==world&&client.getConnection()==listener&&listener!=null&&client.getConnection().getConnection()==connection
            &&client.options==options&&client.gameMode==gameMode&&moduleManager.lifecycleCurrent(publication.enable.continuity);}
        public RootOriginalNativeEnrollment enrollmentIdentity(){return enrollment;}
        public RootOriginalInputPublication originalInputPublication(){return publication;}
        public Module originalFarm(){return original;}public Module helperIdentity(){return helper;}
        private boolean terminalCurrent(Module actor,Minecraft client){var terminal=originalNativeTermination;return !revoked&&Thread.currentThread()==thread
            &&publication==originalInputPublication&&publication.nativeHelperPublication==this&&enrollment==originalNativeEnrollment&&control==publication.input&&control.current()
            &&terminal!=null&&terminal.publication==publication&&terminal.enrollment==enrollment&&terminal.cleanupNativeCurrent(actor,client);}
        /** Callback-free source eligibility, never a native action or fresh history seed. */
        public boolean currentFor(Module exactHelper,Minecraft client){return exactHelper==helper&&(terminalCurrent(helper,client)||currentHelperFields(client));}
        private boolean currentHelperFields(Minecraft client){return originalLogicalCurrent(client)&&publication.publishedOwner==helper
            &&enable!=null&&enable.parent()==helper&&(enable.parentEnableHistoryCurrent()||pendingOriginalExpectedDisable!=null
                &&pendingOriginalExpectedDisable.publication==publication&&pendingOriginalExpectedDisable.start.parent()==helper&&pendingOriginalExpectedDisable.inProgressCurrent())&&control.control().active()&&helper.id().equals(control.control().ownerModuleId())
            &&declaration.nativePublicationAffiliationCurrent(publication)&&originalLogicalCurrent(client);}
        public boolean originalContinuationCurrent(Module exactOriginal,Minecraft client){
            if(exactOriginal!=original)return false;
            if(terminalCurrent(original,client)||currentFor(helper,client))return true;
            if(!originalLogicalCurrent(client)||!control.control().active())return false;
            var rollback=savedMaintenanceRollbackInvocation;
            if(rollback!=null&&rollback.publication==publication&&rollback.declaration==declaration&&rollback.matchesRollback(rollback.call)
                &&rollback.disabled!=null&&rollback.disabled.closedCurrent())return rollback.inputPublished
                    ?publication.publishedOwner==original&&original.id().equals(control.control().ownerModuleId())
                    :publication.publishedOwner==helper&&helper.id().equals(control.control().ownerModuleId());
            if(completedRollback!=null&&completedRollback.publication==publication&&completedRollback.declaration==declaration
                &&completedRollback.normalReturnedCurrent(client))return publication.publishedOwner==original&&original.id().equals(control.control().ownerModuleId());
            var retained=retainedMaintenanceResumeInvocation;
            if(retained!=null&&retained.publication==publication&&retained.declaration==declaration&&retained.belongsRetainedSource(retained.call)
                &&!retained.revoked&&retained.disabled!=null&&retained.disabled.closedCurrent())return retained.inputPublished
                    ?publication.publishedOwner==original&&original.id().equals(control.control().ownerModuleId())
                    :publication.publishedOwner==helper&&helper.id().equals(control.control().ownerModuleId());
            if(completedRetained!=null&&completedRetained.publication==publication&&completedRetained.declaration==declaration
                &&completedRetained.normalReturnedCurrent(client))return publication.publishedOwner==original&&original.id().equals(control.control().ownerModuleId());
            var resume=savedMaintenanceResumeInvocation;
            if(resume!=null&&resume.publication==publication&&resume.declaration==declaration&&resume.matchesResume(resume.call)
                &&resume.disabled!=null&&resume.disabled.closedCurrent())return resume.inputPublished
                    ?publication.publishedOwner==original&&original.id().equals(control.control().ownerModuleId())
                    :publication.publishedOwner==helper&&helper.id().equals(control.control().ownerModuleId());
            return publication.publishedOwner==original&&original.id().equals(control.control().ownerModuleId())&&completedResume!=null
                &&completedResume.publication==publication&&completedResume.declaration==declaration&&completedResume.normalReturnedCurrent(client);
        }
    }
    public static RootOriginalNativeHelperPublication originalNativeHelperPublicationFor(Module exactHelper,Minecraft client){var publication=originalInputPublication;
        try{var retained=publication==null?null:publication.nativeHelperPublication;return retained!=null&&retained.currentFor(exactHelper,client)?retained:null;}
        catch(RuntimeException|Error unavailable){return null;}}
    /** Existing original publication only. SDK GUI reset observers must capture before actual native opcodes. */
    public static RootOriginalInputPublication originalPublicationForNativeGui(Minecraft client){var publication=originalInputPublication;
        try{return publication!=null&&publication.enrollment.registration.provider.client==client&&publication.nativeHistoryCurrent()?publication:null;}
        catch(RuntimeException|Error unavailable){return null;}}
    /** Callback-free logical/source membership only; native observers separately validate their retained exact opcode history. */
    public static boolean originalNativeGuiAffiliationCurrent(RootOriginalInputPublication exact,Minecraft client){
        return exact!=null&&exact==originalInputPublication&&exact.enrollment.registration.provider.client==client&&exact.logicalHistoryCurrent();
    }
    /** Raw original affiliation for a private normal-return cleanup producer; never an action or active-use permission.
     * The caller retains the original publication/lifecycle and supplies only Core's actual private tick/disable observations. */
    public static boolean originalNormalReturnCleanupAffiliationCurrent(RootOriginalInputPublication exact,Module owner,Minecraft client,
            ModuleManager.LifecycleSnapshot originalEnabled,ModuleManager.ModuleTickDeclaration exactTick,ModuleManager.PendingOwnDisable exactDisable){
        try{
            if(exact==null||exact!=originalInputPublication||exact.revoked||owner==null||exact.original!=owner||exact.publishedOwner!=owner
                ||!(owner instanceof dev.veltraph.client.fishing.module.FishingModule||owner instanceof dev.veltraph.client.foraging.replant.ForagingReplantModule)
                ||exact.savedEnableDeclaration!=null||exact.nativeHelperPublication!=null||client==null||client!=Minecraft.getInstance()||!client.isSameThread()
                ||originalEnabled==null||moduleManager==null||!moduleManager.ownCleanupLifecycleCurrent(originalEnabled,owner)
                ||exact.enrollment!=originalNativeEnrollment||!exact.enrollment.sceneCurrent(client)
                ||originalNativeInstallation==null||!originalNativeInstallation.currentFor(client)||exact.input==null||!exact.input.current()
                ||!exact.input.control().publicationCurrent()||!exact.input.control().active()||!owner.id().equals(exact.input.control().ownerModuleId())
                ||originalModuleTickRegistration==null||!originalModuleTickRegistration.current()
                ||originalEnableRegistration==null||!originalEnableRegistration.current()||originalDisableRegistration==null||!originalDisableRegistration.current())return false;
            if(exactDisable!=null){var cleanup=normalReturnCleanupDisable;var terminal=originalNativeTermination;
                if(exactTick!=null||exactDisable.registryIdentity()!=moduleManager||!exactDisable.current(owner)
                    ||moduleManager.pendingActualOwnDisable(owner).orElse(null)!=exactDisable
                    ||!moduleManager.pendingOwnDisableFromMatches(originalEnabled,owner))return false;
                if(cleanup!=null&&moduleManager.lifecycleSameHistory(originalEnabled,cleanup.before)
                    &&cleanup.current(exact,owner,client,exactDisable))return true;
                // Existing exact private actual manual-stop instruction remains a separate cleanup-only source.
                return terminal!=null&&terminal.publication==exact&&terminal.original==owner&&terminal.enrollment==exact.enrollment
                    &&terminal.installation==originalNativeInstallation&&terminal.pendingOwnerDisable!=null
                    &&terminal.pendingOwnerDisable.start.parent()==owner&&terminal.pendingOwnerDisable.start.disabledCurrent()
                    &&terminal.pendingOwnerDisable.start.startedFrom(originalEnabled)&&terminal.cleanupNativeCurrent(owner,client);}
            var tick=originalModuleTickInvocation;
            return exactTick!=null&&exactTick.registryIdentity()==moduleManager&&exactTick.module()==owner&&exactTick.issuingCurrent(owner)
                &&moduleManager.currentModuleTickDeclaration(owner)==exactTick&&moduleManager.lifecycleCurrent(originalEnabled)
                &&tick!=null&&tick.nativeCurrent()&&tick.registry==moduleManager&&tick.enrollment==exact.enrollment&&tick.publication==exact
                &&exact.enable==originalEnableProof&&exact.enable.enableLineageCurrent()&&exact.logicalHistoryCurrent();
        }catch(RuntimeException|Error unavailable){return false;}
    }
    public static RootOriginalInputPublication originalRootPublicationFor(Module exact){
        var publication=originalInputPublication;
        try{return publication!=null&&publication.currentFor(exact)?publication:null;}
        catch(RuntimeException|Error unavailable){if(publication!=null)publication.revoked=true;return null;}
    }
    private static long originalWartDropIssueGeneration;
    /** Actual private Root module-tick plus original private Wart drop; no caller Boolean grants WATER. */
    public static final class RootWartDropPublication {
        private final dev.veltraph.client.farming.module.WartCropsFarmModule farm;
        private final dev.veltraph.client.farming.module.WartCropsFarmModule.VerticalDropAttempt attempt;
        private final dev.veltraph.client.farming.movement.McFarmRouteDriver driver;
        private final dev.veltraph.client.farming.movement.McFarmRouteDriver.DropAdmissionCall issue;
        private final RootOriginalInputPublication publication;
        private final RootOriginalNativeEnrollment enrollment;
        private final FarmServices services;
        private final ConfigService store;
        private final ModuleManager registry;
        private final ModuleManager.LifecycleSnapshot lifecycle;
        private final ScriptingInputLock.ControlSnapshot control;
        private final Object player,world,connection,options,gameMode;
        private final Thread thread=Thread.currentThread();
        private final long issueGeneration=++originalWartDropIssueGeneration;
        public long issueGeneration(){return issueGeneration;}
        private RootWartDropPublication(dev.veltraph.client.farming.module.WartCropsFarmModule farm,
                dev.veltraph.client.farming.module.WartCropsFarmModule.VerticalDropAttempt attempt,
                dev.veltraph.client.farming.movement.McFarmRouteDriver.DropAdmissionCall issue){
            this.farm=farm;this.attempt=attempt;this.issue=issue;this.driver=issue.driverIdentity();publication=originalInputPublication;enrollment=originalNativeEnrollment;
            services=farmServices;store=config;registry=moduleManager;lifecycle=registry.lifecycleSnapshot();control=publication.input.control();
            var c=enrollment.registration.provider.client;player=c.player;world=c.level;connection=c.getConnection();options=c.options;gameMode=c.gameMode;
        }
        public FarmServices farmServicesIdentity(){return services;}
        public ConfigService configIdentity(){return store;}
        public dev.veltraph.client.farming.module.WartCropsFarmModule farmIdentity(){return farm;}
        public dev.veltraph.client.farming.module.WartCropsFarmModule.VerticalDropAttempt attemptIdentity(){return attempt;}
        /** Stored source/native fields only after the service's last provider; no configuration/geometry/role callback. */
        public boolean sourceCurrent(){var c=enrollment.registration.provider.client;var tick=originalModuleTickInvocation;
            return issue.issuedSourceCurrent(this)&&Thread.currentThread()==thread&&c.isSameThread()&&publication==originalInputPublication&&enrollment==originalNativeEnrollment
                &&registry==moduleManager&&store==config&&services==farmServices&&lifecycle.known()&&registry.lifecycleCurrent(lifecycle)
                &&control.current()&&control.active()&&farm.id().equals(control.ownerModuleId())
                &&c.player==player&&c.level==world&&c.getConnection()==connection&&c.options==options&&c.gameMode==gameMode
                &&tick!=null&&tick.currentFor(farm)&&farm.nativeVerticalDropCurrent(attempt,driver);
        }
        public boolean beforeEffectCurrent(){return sourceCurrent()&&publication.nativeHistoryCurrent()&&sourceCurrent();}
        public boolean beforeKeyEffectCurrent(net.minecraft.client.KeyMapping key,java.util.List<dev.veltraph.client.service.mc.McHeldKeys.NativeInputInvocation> actual){
            var c=enrollment.registration.provider.client;
            return sourceCurrent()&&publication.nativeHistory!=null&&publication.nativeHistory.wartDropKeyEffectCurrent(farm,c,key,actual)&&sourceCurrent();
        }
        /** Exact SDK field veto after mode/provider callbacks; the pending key is explained only by its real native token. */
        public boolean keyFieldHistoryCurrent(net.minecraft.client.KeyMapping key,java.util.List<dev.veltraph.client.service.mc.McHeldKeys.NativeInputInvocation> returned,
                dev.veltraph.client.service.mc.McHeldKeys.NativeInputInvocation pending,int depth){
            var c=enrollment.registration.provider.client;
            return publication==originalInputPublication&&enrollment==originalNativeEnrollment&&publication.nativeHistory!=null
                &&publication.nativeHistory.wartDropKeyFieldCurrent(c,key,returned,pending,depth);
        }
        public boolean current(){var c=enrollment.registration.provider.client;
            return sourceCurrent()&&publication.currentFor(farm)&&enrollment.originalProducerCurrent(farm,c)&&sourceCurrent();
        }
        /** Exact original own-disable cleanup only; public stop or an enabled source cannot claim this window. */
        public boolean ownDisableCleanupCurrent(){var c=enrollment.registration.provider.client;
            return !farm.isEnabled()&&registry.actualOwnDisableModule()==farm&&originalNativeTermination!=null
                &&originalNativeTermination.cleanupNativeCurrent(farm,c)&&cleanupCurrent();
        }
        /** Release is routed only by the original native issuer; it never renews gameplay or a foreign camera. */
        public boolean cleanupCurrent(){var c=enrollment.registration.provider.client;
            if(Thread.currentThread()!=thread||!c.isSameThread()||publication!=originalInputPublication||enrollment!=originalNativeEnrollment
                ||registry!=moduleManager||store!=config||services!=farmServices||!control.current()
                ||c.player!=player||c.level!=world||c.getConnection()!=connection||c.options!=options||c.gameMode!=gameMode)return false;
            if(!sourceCurrent()&&(registry.actualOwnDisableModule()!=farm||originalNativeTermination==null
                    ||!originalNativeTermination.cleanupNativeCurrent(farm,c)))return false;
            return services.input() instanceof dev.veltraph.client.service.mc.McInputService input
                &&services.rotation() instanceof dev.veltraph.client.service.mc.McRotationService rotation
                &&originalInputIssuingModule(input)==farm&&originalCameraIssuingModule(rotation)==farm;
        }
    }
    public static RootWartDropPublication captureOriginalWartDrop(dev.veltraph.client.farming.movement.McFarmRouteDriver.DropAdmissionCall issue){
        if(issue==null||!issue.claimOriginalIssue())return null;
        var farm=issue.wartIdentity();var driver=issue.driverIdentity();var target=issue.targetPosition();int bound=issue.bound();
        var tick=originalModuleTickInvocation;var publication=originalInputPublication;var enrollment=originalNativeEnrollment;
        if(farm==null||driver==null||bound!=3||originalWartDropIssueGeneration==Long.MAX_VALUE||tick==null||publication==null||enrollment==null||!tick.currentFor(farm))return null;
        var attempt=farm.nativeVerticalDropAttempt(driver,target,bound);if(attempt==null)return null;
        var actual=new RootWartDropPublication(farm,attempt,issue);
        return issue.bindIssuedSource(actual)&&actual.current()?actual:null;
    }
    private static RootRetainedMaintenanceResumeInvocation retainedMaintenanceResumeInvocation;
    public static final class RootRetainedMaintenanceResumeInvocation {
        private final FarmMaintenanceCoordinator.RetainedLoadoutOperation call;private final FarmMaintenanceCoordinator coordinator;
        private final RootOriginalInputPublication publication;private final RootOriginalNativeEnrollment enrollment;
        private final RootOriginalModuleTickInvocation tick;private final FarmMaintenanceCoordinator.SavedMaintenanceEnableSource declaration;
        private final Module farm,helper;private final String farmId;private final Object checkpoint,pause,token;private final ModuleManager registry;private final Thread thread=Thread.currentThread();
        private boolean revoked,inputPublished,closed,normalReturned;private ModuleManager.OwnDisableReceipt disabled;
        private RootRetainedMaintenanceResumeInvocation(FarmMaintenanceCoordinator.RetainedLoadoutOperation call,RootOriginalInputPublication publication,RootOriginalModuleTickInvocation tick){
            this.call=call;this.publication=publication;this.tick=tick;coordinator=call.coordinatorIdentity();enrollment=publication.enrollment;declaration=call.originalEnableDeclaration();
            farm=call.farm();helper=call.helperIdentity();farmId=farm.id();checkpoint=call.originalCheckpointIdentity();pause=call.originalPauseIdentity();token=call.maintenanceToken();registry=moduleManager;
        }
        public boolean matchesRetained(FarmMaintenanceCoordinator.RetainedLoadoutOperation exact){return !revoked&&!closed&&retainedMaintenanceResumeInvocation==this
            &&Thread.currentThread()==thread&&registry==moduleManager&&coordinator==farmMaintenance&&publication==originalInputPublication&&enrollment==originalNativeEnrollment
            &&exact!=null&&call==exact&&coordinator==exact.coordinatorIdentity()&&publication==exact.originalInputPublication()&&publication.original==farm
            &&farm==exact.farm()&&helper==exact.helperIdentity()&&declaration==exact.originalEnableDeclaration()&&declaration==publication.savedEnableDeclaration
            &&checkpoint==exact.originalCheckpointIdentity()&&pause==exact.originalPauseIdentity()&&token==exact.maintenanceToken();}
        /** Historical exact source affiliation only; closing never creates a future input or native grant. */
        public boolean belongsRetainedSource(FarmMaintenanceCoordinator.RetainedLoadoutOperation exact){return exact!=null&&exact==call
            &&coordinator==exact.coordinatorIdentity()&&publication==exact.originalInputPublication()&&farm==exact.farm()&&helper==exact.helperIdentity()
            &&declaration==exact.originalEnableDeclaration()&&checkpoint==exact.originalCheckpointIdentity()&&pause==exact.originalPauseIdentity()&&token==exact.maintenanceToken();}
        public RootOriginalInputPublication originalPublication(){return publication;}
        public FarmMaintenanceCoordinator coordinatorIdentity(){return coordinator;}
        public RootOriginalNativeEnrollment enrollmentIdentity(){return enrollment;}
        public Module originalFarm(){return farm;}public Module helperIdentity(){return helper;}
        /** Actual private restore episode affiliation; the separate SDK opcode carrier supplies native completion. */
        /** Stored final effect leaf after every provider/query; no source policy, arrival, native-history or virtual actor call. */
        public boolean finalRestoreNativeCurrent(FarmMaintenanceCoordinator.RetainedLoadoutOperation exact,Minecraft client){
            var input=publication.input;var c=enrollment.registration.provider.client;var proof=publication.enable;
            return matchesRetained(exact)&&inputPublished&&exact.restorationEpisodeCurrent()&&client==c&&c==Minecraft.getInstance()&&c.isSameThread()
                &&originalModuleTickInvocation==tick&&tick.nativeCurrent()&&tick.registry==registry&&tick.enrollment==enrollment&&tick.publication==publication
                &&!publication.revoked&&!enrollment.revoked&&publication.enrollment==enrollment&&publication.original==farm&&publication.publishedOwner==farm
                &&proof==originalEnableProof&&!proof.revoked&&proof.enrollment==enrollment&&proof.parent==farm&&proof.completed!=null
                &&proof.completed.actualStart()==proof.start&&proof.completed.parentEnableHistoryCurrent()&&registry.lifecycleCurrent(proof.continuity)
                &&enrollment.admission!=null&&enrollment.admission.request.closedNormalEnableSourceCurrent(farm,proof.completed)
                &&originalModuleTickRegistration!=null&&originalModuleTickRegistration.current()
                &&originalEnableRegistration!=null&&originalEnableRegistration.current()&&originalDisableRegistration!=null&&originalDisableRegistration.current()
                &&disabled!=null&&disabled.closedCurrent()&&input!=null&&input.current()&&input.control().active()&&farmId.equals(input.control().ownerModuleId())
                &&enrollment.registration==rootInputReleaseRegistration&&enrollment.registration.provider==rootRotationProvider
                &&enrollment.registration.originalServices==farmServices&&rootRotationProvider.registry==registry&&rootRotationProvider.store==config
                &&c.player==enrollment.player&&c.level==enrollment.world&&c.options==enrollment.options&&c.gameMode==enrollment.gameMode
                &&c.getConnection()==enrollment.listener&&enrollment.listener!=null&&c.getConnection().getConnection()==enrollment.connection;
        }
        public boolean beforeRestoreNativeCurrent(Module exactOriginal,Minecraft client){return matchesRetained(call)&&inputPublished&&exactOriginal==farm
            &&client==enrollment.registration.provider.client&&call.restorationEpisodeCurrent()&&originalModuleTickInvocation==tick&&tick.nativeCurrent()&&publication.publishedOwner==farm
            &&publication.logicalHistoryCurrent()&&publication.input.control().active()&&farm.id().equals(publication.input.control().ownerModuleId());}
        public boolean normalReturnedCurrent(Minecraft client){return closed&&normalReturned&&!revoked&&client==enrollment.registration.provider.client
            &&publication==originalInputPublication&&enrollment==originalNativeEnrollment&&publication.original==farm&&publication.publishedOwner==farm
            &&publication.logicalHistoryCurrent()&&enrollment.sceneCurrent(client)&&Thread.currentThread()==thread;}
    }
    public static RootRetainedMaintenanceResumeInvocation originalRetainedMaintenanceResumeForNative(Module exactOriginal,Minecraft client){var actual=retainedMaintenanceResumeInvocation;
        try{return actual!=null&&actual.beforeRestoreNativeCurrent(exactOriginal,client)?actual:null;}catch(RuntimeException|Error unavailable){return null;}}
    private static RootRetainedMaintenanceResumeInvocation beginRetainedMaintenanceOriginalResume(FarmMaintenanceCoordinator.RetainedLoadoutOperation call){
        var publication=originalInputPublication;var tick=originalModuleTickInvocation;
        if(call==null||publication==null||call.originalInputPublication()!=publication||call.farm()!=publication.original
            ||call.coordinatorIdentity()!=farmMaintenance||call.originalEnableDeclaration()!=publication.savedEnableDeclaration)return null;
        if(retainedMaintenanceResumeInvocation!=null){retainedMaintenanceResumeInvocation.revoked=true;return null;}
        if(savedMaintenanceResumeInvocation!=null||savedMaintenanceRollbackInvocation!=null)return null;
        if(tick==null||!tick.nativeCurrent()||tick.registry!=moduleManager||tick.enrollment!=publication.enrollment||tick.publication!=publication
            ||!moduleManager.actualModuleTickCurrent(call.helperIdentity()))return null;
        var actual=new RootRetainedMaintenanceResumeInvocation(call,publication,tick);retainedMaintenanceResumeInvocation=actual;
        try{if(!call.claimOriginalRetainedResume(actual)||!publication.nativeHistoryCurrent()||!call.beforeNativeCurrent()){actual.revoked=true;if(retainedMaintenanceResumeInvocation==actual)retainedMaintenanceResumeInvocation=null;return null;}
            return actual;
        }catch(RuntimeException|Error unavailable){actual.revoked=true;publication.revoked=true;if(retainedMaintenanceResumeInvocation==actual)retainedMaintenanceResumeInvocation=null;return null;}
    }
    private static boolean publishRetainedMaintenanceResumeOriginalInput(FarmMaintenanceCoordinator.RetainedLoadoutOperation call,RootRetainedMaintenanceResumeInvocation actual){
        if(actual==null||!actual.matchesRetained(call)||actual.inputPublished||originalInputInvocation!=null){if(originalInputInvocation!=null)originalInputInvocation.revoked=true;return false;}
        var invocation=new RootOriginalInputInvocation(call,actual.publication);originalInputInvocation=invocation;
        try{if(!call.claimOriginalPublication(invocation)||!invocation.matchesRetainedMaintenanceResume(call)||!call.prepublicationCurrent()||!actual.publication.nativeHistoryCurrent())return false;
            boolean published=publishOriginalInputWrite(new RootOriginalOwnershipWrite(call),true,call.farm().id());actual.inputPublished=published;return published;
        }finally{invocation.revoked=true;if(originalInputInvocation==invocation)originalInputInvocation=null;}
    }
    private static void closeRetainedMaintenanceOriginalResume(RootRetainedMaintenanceResumeInvocation actual){
        if(actual==null||actual.closed)return;
        try{actual.normalReturned=actual.matchesRetained(actual.call)&&actual.inputPublished&&actual.call.normalReturnedCurrent()&&actual.publication.nativeHistoryCurrent();
            if(!actual.normalReturned){actual.revoked=true;actual.publication.revoked=true;}}
        catch(RuntimeException|Error unavailable){actual.revoked=true;actual.publication.revoked=true;}
        finally{actual.closed=true;
            if(actual.normalReturned&&!actual.revoked){var retained=actual.publication.nativeHelperPublication;
                if(retained!=null&&retained.declaration==actual.declaration&&!retained.revoked)retained.completedRetained=actual;
                else{actual.revoked=true;actual.publication.revoked=true;}}
            try{if(!dev.veltraph.client.service.mc.RootNativeHistory.closeRetainedMaintenanceResume(actual,actual.enrollment.registration.provider.client)){
                actual.revoked=true;actual.publication.revoked=true;}}
            catch(RuntimeException|Error unavailable){actual.revoked=true;actual.publication.revoked=true;}
            if(retainedMaintenanceResumeInvocation==actual)retainedMaintenanceResumeInvocation=null;
        }
    }

    private static RootSavedMaintenanceResumeInvocation savedMaintenanceResumeInvocation;
    public static final class RootSavedMaintenanceResumeInvocation {
        private final FarmMaintenanceCoordinator.SavedMaintenanceResumeCall call;private final FarmMaintenanceCoordinator coordinator;
        private final RootOriginalInputPublication publication;private final RootOriginalNativeEnrollment enrollment;
        private final RootOriginalModuleTickInvocation tick;private final FarmMaintenanceCoordinator.SavedMaintenanceEnableSource declaration;
        private final Module farm,helper;private final String farmId;private final Object checkpoint,pause,token;private final ModuleManager registry;private final Thread thread=Thread.currentThread();
        private boolean revoked,inputPublished,closed,normalReturned;private ModuleManager.OwnDisableReceipt disabled;
        private RootSavedMaintenanceResumeInvocation(FarmMaintenanceCoordinator.SavedMaintenanceResumeCall call,RootOriginalInputPublication publication,RootOriginalModuleTickInvocation tick){
            this.call=call;this.publication=publication;this.tick=tick;coordinator=call.coordinatorIdentity();enrollment=publication.enrollment;declaration=call.originalEnableDeclaration();
            farm=call.farm();helper=call.helperIdentity();farmId=farm.id();checkpoint=call.originalCheckpointIdentity();pause=call.originalPauseIdentity();token=call.maintenanceToken();registry=moduleManager;
        }
        public boolean matchesResume(FarmMaintenanceCoordinator.SavedMaintenanceResumeCall exact){return !revoked&&!closed&&savedMaintenanceResumeInvocation==this
            &&Thread.currentThread()==thread&&registry==moduleManager&&coordinator==farmMaintenance&&publication==originalInputPublication&&enrollment==originalNativeEnrollment
            &&exact!=null&&call==exact&&coordinator==exact.coordinatorIdentity()&&publication==exact.originalInputPublication()&&publication.original==farm
            &&farm==exact.farm()&&helper==exact.helperIdentity()&&declaration==exact.originalEnableDeclaration()&&declaration==publication.savedEnableDeclaration
            &&checkpoint==exact.originalCheckpointIdentity()&&pause==exact.originalPauseIdentity()&&token==exact.maintenanceToken();}
        public RootOriginalInputPublication originalPublication(){return publication;}
        public FarmMaintenanceCoordinator coordinatorIdentity(){return coordinator;}
        public RootOriginalNativeEnrollment enrollmentIdentity(){return enrollment;}
        public Module originalFarm(){return farm;}public Module helperIdentity(){return helper;}
        /** Actual private restore episode affiliation; the separate SDK opcode carrier supplies native completion. */
        /** Stored final effect leaf after every provider/query; no source policy, arrival, native-history or virtual actor call. */
        public boolean finalRestoreNativeCurrent(FarmMaintenanceCoordinator.SavedMaintenanceResumeCall exact,Minecraft client){
            var input=publication.input;var c=enrollment.registration.provider.client;var proof=publication.enable;
            return matchesResume(exact)&&inputPublished&&exact.restorationEpisodeCurrent()&&client==c&&c==Minecraft.getInstance()&&c.isSameThread()
                &&originalModuleTickInvocation==tick&&tick.nativeCurrent()&&tick.registry==registry&&tick.enrollment==enrollment&&tick.publication==publication
                &&!publication.revoked&&!enrollment.revoked&&publication.enrollment==enrollment&&publication.original==farm&&publication.publishedOwner==farm
                &&proof==originalEnableProof&&!proof.revoked&&proof.enrollment==enrollment&&proof.parent==farm&&proof.completed!=null
                &&proof.completed.actualStart()==proof.start&&proof.completed.parentEnableHistoryCurrent()&&registry.lifecycleCurrent(proof.continuity)
                &&enrollment.admission!=null&&enrollment.admission.request.closedNormalEnableSourceCurrent(farm,proof.completed)
                &&originalModuleTickRegistration!=null&&originalModuleTickRegistration.current()
                &&originalEnableRegistration!=null&&originalEnableRegistration.current()&&originalDisableRegistration!=null&&originalDisableRegistration.current()
                &&disabled!=null&&disabled.closedCurrent()&&input!=null&&input.current()&&input.control().active()&&farmId.equals(input.control().ownerModuleId())
                &&enrollment.registration==rootInputReleaseRegistration&&enrollment.registration.provider==rootRotationProvider
                &&enrollment.registration.originalServices==farmServices&&rootRotationProvider.registry==registry&&rootRotationProvider.store==config
                &&c.player==enrollment.player&&c.level==enrollment.world&&c.options==enrollment.options&&c.gameMode==enrollment.gameMode
                &&c.getConnection()==enrollment.listener&&enrollment.listener!=null&&c.getConnection().getConnection()==enrollment.connection;
        }
        public boolean beforeRestoreNativeCurrent(Module exactOriginal,Minecraft client){return matchesResume(call)&&inputPublished&&exactOriginal==farm
            &&client==enrollment.registration.provider.client&&call.restorationEpisodeCurrent()&&originalModuleTickInvocation==tick&&tick.nativeCurrent()&&publication.publishedOwner==farm
            &&publication.logicalHistoryCurrent()&&publication.input.control().active()&&farm.id().equals(publication.input.control().ownerModuleId());}
        public boolean normalReturnedCurrent(Minecraft client){return closed&&normalReturned&&!revoked&&client==enrollment.registration.provider.client
            &&publication==originalInputPublication&&enrollment==originalNativeEnrollment&&publication.original==farm&&publication.publishedOwner==farm
            &&publication.logicalHistoryCurrent()&&Thread.currentThread()==thread;}
    }
    public static RootSavedMaintenanceResumeInvocation originalSavedMaintenanceResumeForNative(Module exactOriginal,Minecraft client){var actual=savedMaintenanceResumeInvocation;
        try{return actual!=null&&actual.beforeRestoreNativeCurrent(exactOriginal,client)?actual:null;}catch(RuntimeException|Error unavailable){return null;}}
    private static RootSavedMaintenanceResumeInvocation beginSavedMaintenanceOriginalResume(FarmMaintenanceCoordinator.SavedMaintenanceResumeCall call){
        var publication=originalInputPublication;var tick=originalModuleTickInvocation;
        if(call==null||publication==null||call.originalInputPublication()!=publication||call.farm()!=publication.original
            ||call.coordinatorIdentity()!=farmMaintenance||call.originalEnableDeclaration()!=publication.savedEnableDeclaration)return null;
        if(savedMaintenanceResumeInvocation!=null){savedMaintenanceResumeInvocation.revoked=true;return null;}
        if(tick==null||!tick.nativeCurrent()||tick.registry!=moduleManager||tick.enrollment!=publication.enrollment||tick.publication!=publication
            ||!moduleManager.actualModuleTickCurrent(call.helperIdentity()))return null;
        var actual=new RootSavedMaintenanceResumeInvocation(call,publication,tick);savedMaintenanceResumeInvocation=actual;
        try{if(!call.claimOriginalResume(actual)||!publication.nativeHistoryCurrent()||!call.beforeNativeCurrent()){actual.revoked=true;if(savedMaintenanceResumeInvocation==actual)savedMaintenanceResumeInvocation=null;return null;}
            return actual;
        }catch(RuntimeException|Error unavailable){actual.revoked=true;publication.revoked=true;if(savedMaintenanceResumeInvocation==actual)savedMaintenanceResumeInvocation=null;return null;}
    }
    private static boolean publishSavedMaintenanceResumeOriginalInput(FarmMaintenanceCoordinator.SavedMaintenanceResumeCall call,RootSavedMaintenanceResumeInvocation actual){
        if(actual==null||!actual.matchesResume(call)||actual.inputPublished||originalInputInvocation!=null){if(originalInputInvocation!=null)originalInputInvocation.revoked=true;return false;}
        var invocation=new RootOriginalInputInvocation(call,actual.publication);originalInputInvocation=invocation;
        try{if(!call.claimOriginalPublication(invocation)||!invocation.matchesSavedMaintenanceResume(call)||!call.prepublicationCurrent()||!actual.publication.nativeHistoryCurrent())return false;
            boolean published=publishOriginalInputWrite(new RootOriginalOwnershipWrite(call),true,call.farm().id());actual.inputPublished=published;return published;
        }finally{invocation.revoked=true;if(originalInputInvocation==invocation)originalInputInvocation=null;}
    }
    private static void closeSavedMaintenanceOriginalResume(RootSavedMaintenanceResumeInvocation actual){
        if(actual==null)return;
        try{actual.normalReturned=actual.matchesResume(actual.call)&&actual.inputPublished&&actual.call.normalReturnedCurrent()&&actual.publication.nativeHistoryCurrent();
            if(!actual.normalReturned){actual.revoked=true;actual.publication.revoked=true;}}
        catch(RuntimeException|Error unavailable){actual.revoked=true;actual.publication.revoked=true;}
        finally{actual.closed=true;
            if(actual.normalReturned&&!actual.revoked){var retained=actual.publication.nativeHelperPublication;
                if(retained!=null&&retained.declaration==actual.declaration&&!retained.revoked)retained.completedResume=actual;
                else{actual.revoked=true;actual.publication.revoked=true;}}
            try{dev.veltraph.client.service.mc.RootNativeHistory.closeSavedMaintenanceResume(actual,actual.enrollment.registration.provider.client);}
            catch(RuntimeException|Error unavailable){actual.revoked=true;actual.publication.revoked=true;}
            if(savedMaintenanceResumeInvocation==actual)savedMaintenanceResumeInvocation=null;
        }
    }
    private static RootSavedMaintenanceRollbackCleanupInvocation savedMaintenanceRollbackCleanupInvocation;
    /** Distinct failed terminal producer. Closed/revoked rollback identities supply affiliation only. */
    public static final class RootSavedMaintenanceRollbackCleanupInvocation {
        private final FarmMaintenanceCoordinator.SavedMaintenanceRollbackTerminal terminal;
        private final RootSavedMaintenanceRollbackInvocation rollback;
        private final RootOriginalGameplayTickInvocation gameplay;private final RootMaintenanceControlSource control;
        private final ScriptingInputLock.ControlSnapshot retainedControl;
        private final RootOriginalInputPublication publication;private final RootOriginalNativeEnrollment enrollment;
        private final Module farm;private final FarmMaintenanceCoordinator coordinator;private final Thread thread=Thread.currentThread();
        private boolean closed,revoked,normalReturned;
        private RootSavedMaintenanceRollbackCleanupInvocation(FarmMaintenanceCoordinator.SavedMaintenanceRollbackTerminal terminal){
            this.terminal=terminal;rollback=terminal.originalRollbackIdentity();gameplay=originalGameplayTickInvocation;control=rootMaintenanceControlSource;
            publication=terminal.originalInputPublication();enrollment=rollback==null?terminal.originalCompletedPause().enrollmentIdentity():rollback.enrollmentIdentity();farm=terminal.farm();coordinator=terminal.coordinatorIdentity();
            retainedControl=rollback==null?terminal.originalCompletedPause().control:rollback.rollbackCleanupControl;
        }
        public FarmMaintenanceCoordinator.SavedMaintenanceRollbackTerminal terminalIdentity(){return terminal;}
        public RootSavedMaintenanceRollbackInvocation originalRollbackIdentity(){return rollback;}
        public RootOriginalNativeMaintenancePauseInvocation originalCompletedPauseIdentity(){return terminal.originalCompletedPause();}
        public RootOriginalInputPublication originalPublication(){return publication;}
        public RootOriginalNativeEnrollment enrollmentIdentity(){return enrollment;}
        public Module originalFarm(){return farm;}public Minecraft client(){return terminal.client();}
        private boolean identitiesCurrent(){return Thread.currentThread()==thread&&control==rootMaintenanceControlSource&&control!=null&&control.affiliates(coordinator)
            &&coordinator==farmMaintenance&&terminal.coordinatorIdentity()==coordinator&&terminal.originalInputPublication()==publication&&publication==originalInputPublication
            &&terminal.farm()==farm&&terminal.originalRollbackIdentity()==rollback
            &&(rollback==null?(terminal.zeroNativeRollbackCurrent()||terminal.failedPauseOnlyCurrent()):rollback.closedFailureSourceMatches(terminal.originalRollbackCall())
                &&rollback.originalPublication()==publication&&rollback.enrollmentIdentity()==enrollment)
            &&terminal.originalCompletedPause()!=null
            &&(terminal.failedPauseOnlyCurrent()?terminal.originalCompletedPause().closedPauseSourceMatches(terminal.originalCompletedPause().pause)
                :terminal.originalCompletedPause().acceptedNormalPauseProof())
            &&terminal.originalCompletedPause().enrollmentIdentity()==enrollment&&enrollment==originalNativeEnrollment
            &&retainedControl!=null&&retainedControl.publicationCurrent()
            &&gameplay!=null&&gameplay.current()&&client()==gameplay.client;}
        public boolean matchesTerminal(FarmMaintenanceCoordinator.SavedMaintenanceRollbackTerminal exact){return exact==terminal&&!closed&&!revoked
            &&savedMaintenanceRollbackCleanupInvocation==this&&identitiesCurrent();}
        public boolean beforeCleanupNativeCurrent(){return matchesTerminal(terminal)&&terminal.beforeCleanupNativeCurrent();}
        public boolean finalCleanupNativeCurrent(){return matchesTerminal(terminal)&&terminal.finalCleanupNativeCurrent(this);}
        /** Positive journal presence is required for an actual negative field write; a missing journal permits no native cleanup. */
        public boolean finalCleanupKeyNativeCurrent(){return terminal.originalCleanupResourceJournalRetained()&&finalCleanupNativeCurrent();}
        public boolean claimNativeKeyCleanup(dev.veltraph.client.service.mc.McHeldKeys.RollbackCleanupKeyInvocation actual){
            return actual!=null&&actual.matchesSource(this)&&beforeCleanupNativeCurrent();}
        public boolean completionSourceCurrent(FarmMaintenanceCoordinator.SavedMaintenanceRollbackTerminal exact,Minecraft client){
            return exact==terminal&&client==client()&&!revoked&&identitiesCurrent()&&terminal.finalCleanupNativeCurrent(this)
                &&(closed?normalReturned:savedMaintenanceRollbackCleanupInvocation==this);
        }
    }
    private static dev.veltraph.client.service.mc.RootNativeHistory.SavedMaintenanceRollbackCleanupCompletion finishSavedMaintenanceFailedRollback(
            FarmMaintenanceCoordinator.SavedMaintenanceRollbackTerminal terminal){
        if(savedMaintenanceRollbackCleanupInvocation!=null){savedMaintenanceRollbackCleanupInvocation.revoked=true;return null;}
        RootSavedMaintenanceRollbackCleanupInvocation actual=null;
        try{actual=new RootSavedMaintenanceRollbackCleanupInvocation(terminal);savedMaintenanceRollbackCleanupInvocation=actual;
            if(!actual.matchesTerminal(terminal)||!terminal.claimNativeCleanup(actual)||!actual.beforeCleanupNativeCurrent())return null;
            var completion=dev.veltraph.client.service.mc.RootNativeHistory.cleanupSavedMaintenanceRollbackPrefix(actual);
            actual.normalReturned=completion!=null&&completion.acceptedFor(terminal,actual.client());
            if(!actual.normalReturned){actual.revoked=true;return null;}return completion;
        }catch(RuntimeException|Error unavailable){if(actual!=null)actual.revoked=true;return null;}
        finally{if(actual!=null){actual.closed=true;if(savedMaintenanceRollbackCleanupInvocation==actual)savedMaintenanceRollbackCleanupInvocation=null;}}
    }
    private static RootSavedMaintenanceRollbackInvocation savedMaintenanceRollbackInvocation;
    public static final class RootSavedMaintenanceRollbackInvocation {
        private final FarmMaintenanceCoordinator.SavedMaintenanceRollbackCall call;private final FarmMaintenanceCoordinator coordinator;
        private final RootOriginalInputPublication publication;private final RootOriginalNativeEnrollment enrollment;
        private final FarmMaintenanceCoordinator.SavedMaintenanceEnableSource declaration;
        private final RootOriginalNativeMaintenancePauseInvocation completedPause;
        private final Module farm,helper;private final String farmId;private final Object checkpoint,pause,token;private final ModuleManager registry;private final Thread thread=Thread.currentThread();
        private boolean revoked,inputPublished,closed,normalReturned;private ModuleManager.OwnDisableReceipt disabled;
        private dev.veltraph.client.service.mc.RootNativeHistory.SavedMaintenanceRollbackCompletion nativeCompletion;
        private ScriptingInputLock.ControlSnapshot rollbackCleanupControl;
        private RootSavedMaintenanceRollbackInvocation(FarmMaintenanceCoordinator.SavedMaintenanceRollbackCall call,RootOriginalInputPublication publication){
            this.call=call;this.publication=publication;rollbackCleanupControl=publication.input==null?null:publication.input.control();completedPause=call.originalCompletedPause();coordinator=call.coordinatorIdentity();enrollment=publication.enrollment;declaration=call.originalEnableDeclaration();
            farm=call.farm();helper=call.helperIdentity();farmId=farm.id();checkpoint=call.originalCheckpointIdentity();pause=call.originalPauseIdentity();token=call.maintenanceToken();registry=moduleManager;
        }
        public boolean matchesRollback(FarmMaintenanceCoordinator.SavedMaintenanceRollbackCall exact){return !revoked&&!closed&&savedMaintenanceRollbackInvocation==this
            &&Thread.currentThread()==thread&&registry==moduleManager&&coordinator==farmMaintenance&&publication==originalInputPublication&&enrollment==originalNativeEnrollment
            &&exact!=null&&call==exact&&coordinator==exact.coordinatorIdentity()&&publication==exact.originalInputPublication()&&publication.original==farm
            &&farm==exact.farm()&&helper==exact.helperIdentity()&&declaration==exact.originalEnableDeclaration()&&completedPause==exact.originalCompletedPause()&&completedPause.acceptedNormalPauseProof()
            &&checkpoint==exact.originalCheckpointIdentity()&&pause==exact.originalPauseIdentity()&&token==exact.maintenanceToken();}
        /** Failure identity only: this never supplies normal history, completion or native permission. */
        public boolean closedFailureSourceMatches(FarmMaintenanceCoordinator.SavedMaintenanceRollbackCall exact){return closed
            &&call==exact&&exact!=null&&Thread.currentThread()==thread&&coordinator==farmMaintenance&&registry==moduleManager
            &&exact.coordinatorIdentity()==coordinator&&exact.farm()==farm&&exact.originalInputPublication()==publication
            &&exact.originalEnableDeclaration()==declaration&&exact.originalCompletedPause()==completedPause
            &&exact.maintenanceToken()==token&&exact.originalCheckpointIdentity()==checkpoint&&exact.originalPauseIdentity()==pause;}
        /** Closed exact identity only. Completion and the retained resource journal are checked separately. */
        public boolean closedSourceMatches(FarmMaintenanceCoordinator.SavedMaintenanceRollbackCall exact){return closed&&!revoked&&normalReturned
            &&call==exact&&exact!=null&&Thread.currentThread()==thread&&coordinator==farmMaintenance&&registry==moduleManager
            &&publication==originalInputPublication&&enrollment==originalNativeEnrollment&&publication.original==farm&&publication.publishedOwner==farm
            &&exact.coordinatorIdentity()==coordinator&&exact.farm()==farm&&exact.originalInputPublication()==publication
            &&exact.originalEnableDeclaration()==declaration&&exact.originalCompletedPause()==completedPause
            &&exact.maintenanceToken()==token&&exact.originalCheckpointIdentity()==checkpoint&&exact.originalPauseIdentity()==pause;}
        public RootOriginalNativeMaintenancePauseInvocation originalCompletedPauseIdentity(){return completedPause;}
        public RootOriginalInputPublication originalPublication(){return publication;}
        public FarmMaintenanceCoordinator coordinatorIdentity(){return coordinator;}
        public RootOriginalNativeEnrollment enrollmentIdentity(){return enrollment;}
        public Module originalFarm(){return farm;}public Module helperIdentity(){return helper;}
        /** Actual private restore episode affiliation; the separate SDK opcode carrier supplies native completion. */
        /** Stored final effect leaf after every provider/query; no source policy, arrival, native-history or virtual actor call. */
        public boolean finalRestoreNativeCurrent(FarmMaintenanceCoordinator.SavedMaintenanceRollbackCall exact,Minecraft client){
            var input=publication.input;var c=enrollment.registration.provider.client;var proof=publication.enable;
            return matchesRollback(exact)&&inputPublished&&exact.restorationEpisodeCurrent()&&client==c&&c==Minecraft.getInstance()&&c.isSameThread()
                &&originalGameplayInvocationCurrent()&&completedPause.acceptedNormalPauseProof()
                &&!publication.revoked&&!enrollment.revoked&&publication.enrollment==enrollment&&publication.original==farm&&publication.publishedOwner==farm
                &&proof==originalEnableProof&&!proof.revoked&&proof.enrollment==enrollment&&proof.parent==farm&&proof.completed!=null
                &&proof.completed.actualStart()==proof.start&&proof.completed.parentEnableHistoryCurrent()&&registry.lifecycleCurrent(proof.continuity)
                &&enrollment.admission!=null&&enrollment.admission.request.closedNormalEnableSourceCurrent(farm,proof.completed)
                &&originalModuleTickRegistration!=null&&originalModuleTickRegistration.current()
                &&originalEnableRegistration!=null&&originalEnableRegistration.current()&&originalDisableRegistration!=null&&originalDisableRegistration.current()
                &&(!call.helperWasEnabled()||disabled!=null&&disabled.closedCurrent())&&input!=null&&input.current()&&input.control().active()&&farmId.equals(input.control().ownerModuleId())
                &&enrollment.registration==rootInputReleaseRegistration&&enrollment.registration.provider==rootRotationProvider
                &&enrollment.registration.originalServices==farmServices&&rootRotationProvider.registry==registry&&rootRotationProvider.store==config
                &&c.player==enrollment.player&&c.level==enrollment.world&&c.options==enrollment.options&&c.gameMode==enrollment.gameMode
                &&c.getConnection()==enrollment.listener&&enrollment.listener!=null&&c.getConnection().getConnection()==enrollment.connection;
        }
        public boolean beforeRestoreNativeCurrent(Module exactOriginal,Minecraft client){return matchesRollback(call)&&inputPublished&&exactOriginal==farm
            &&client==enrollment.registration.provider.client&&call.restorationEpisodeCurrent()&&originalGameplayInvocationCurrent()&&completedPause.acceptedNormalPauseProof()&&publication.publishedOwner==farm
            &&publication.logicalHistoryCurrent()&&publication.input.control().active()&&farm.id().equals(publication.input.control().ownerModuleId());}
        public boolean nativeCompletionSourceCurrent(Minecraft client){return closed&&normalReturned&&!revoked&&client==enrollment.registration.provider.client
            &&publication==originalInputPublication&&enrollment==originalNativeEnrollment&&publication.original==farm&&publication.publishedOwner==farm
            &&publication.logicalHistoryCurrent()&&Thread.currentThread()==thread;}
        public boolean normalReturnedCurrent(Minecraft client){return nativeCompletionSourceCurrent(client)&&nativeCompletion!=null&&nativeCompletion.acceptedFor(this,client);}
    }
    public static RootSavedMaintenanceRollbackInvocation originalSavedMaintenanceRollbackForNative(Module exactOriginal,Minecraft client){var actual=savedMaintenanceRollbackInvocation;
        try{return actual!=null&&actual.beforeRestoreNativeCurrent(exactOriginal,client)?actual:null;}catch(RuntimeException|Error unavailable){return null;}}
    private static RootSavedMaintenanceRollbackInvocation beginSavedMaintenanceOriginalRollback(FarmMaintenanceCoordinator.SavedMaintenanceRollbackCall call){
        var publication=originalInputPublication;
        if(call==null||publication==null||call.originalInputPublication()!=publication||call.farm()!=publication.original
            ||call.coordinatorIdentity()!=farmMaintenance||call.originalCompletedPause()==null
            ||!call.originalCompletedPause().acceptedNormalPauseProof()||!originalGameplayInvocationCurrent())return null;
        if(savedMaintenanceRollbackInvocation!=null){savedMaintenanceRollbackInvocation.revoked=true;return null;}
        if(publication.enable!=originalEnableProof||!publication.enable.enableLineageCurrent())return null;
        var actual=new RootSavedMaintenanceRollbackInvocation(call,publication);savedMaintenanceRollbackInvocation=actual;
        try{if(!call.claimOriginalRollback(actual)||!publication.nativeHistoryCurrent()||!call.beforeNativeCurrent()){actual.revoked=true;if(savedMaintenanceRollbackInvocation==actual)savedMaintenanceRollbackInvocation=null;return null;}
            return actual;
        }catch(RuntimeException|Error unavailable){actual.revoked=true;publication.revoked=true;if(savedMaintenanceRollbackInvocation==actual)savedMaintenanceRollbackInvocation=null;return null;}
    }
    private static boolean publishSavedMaintenanceRollbackOriginalInput(FarmMaintenanceCoordinator.SavedMaintenanceRollbackCall call,RootSavedMaintenanceRollbackInvocation actual){
        if(actual==null||!actual.matchesRollback(call)||actual.inputPublished||originalInputInvocation!=null){if(originalInputInvocation!=null)originalInputInvocation.revoked=true;return false;}
        var invocation=new RootOriginalInputInvocation(call,actual.publication);originalInputInvocation=invocation;
        try{if(!call.claimOriginalPublication(invocation)||!invocation.matchesSavedMaintenanceRollback(call)||!call.prepublicationCurrent()||!actual.publication.nativeHistoryCurrent())return false;
            boolean published=publishOriginalInputWrite(new RootOriginalOwnershipWrite(call),true,call.farm().id());actual.inputPublished=published;
            if(published&&actual.publication.input!=null)actual.rollbackCleanupControl=actual.publication.input.control();return published;
        }finally{invocation.revoked=true;if(originalInputInvocation==invocation)originalInputInvocation=null;}
    }
    private static void closeSavedMaintenanceOriginalRollback(RootSavedMaintenanceRollbackInvocation actual){
        if(actual==null)return;
        try{actual.normalReturned=actual.matchesRollback(actual.call)&&actual.inputPublished&&actual.call.normalNativeRollbackReturnedCurrent()&&actual.publication.nativeHistoryCurrent();
            if(!actual.normalReturned){actual.revoked=true;actual.publication.revoked=true;}}
        catch(RuntimeException|Error unavailable){actual.revoked=true;actual.publication.revoked=true;}
        finally{actual.closed=true;
            if(actual.normalReturned&&!actual.revoked){var retained=actual.publication.nativeHelperPublication;
                if(retained!=null&&retained.declaration==actual.declaration&&!retained.revoked)retained.completedRollback=actual;
                else if(actual.call.helperWasEnabled()&&actual.publication.publishedOwner!=actual.farm){actual.revoked=true;actual.publication.revoked=true;}}
            try{actual.nativeCompletion=dev.veltraph.client.service.mc.RootNativeHistory.closeSavedMaintenanceRollback(actual,actual.enrollment.registration.provider.client);
                if(actual.nativeCompletion==null||!actual.nativeCompletion.acceptedFor(actual,actual.enrollment.registration.provider.client)){actual.revoked=true;actual.publication.revoked=true;}}
            catch(RuntimeException|Error unavailable){actual.revoked=true;actual.publication.revoked=true;}
            if(savedMaintenanceRollbackInvocation==actual)savedMaintenanceRollbackInvocation=null;
        }
    }
    private static RootSavedMaintenanceInputInvocation savedMaintenanceInputInvocation;
    /** Existing scheduled Root invocation only; no caller can construct an input-selection grant. */
    public static final class RootSavedMaintenanceInputInvocation {
        private final FarmMaintenanceCoordinator coordinator;private final RootOriginalInputPublication publication;
        private final FarmMaintenanceCoordinator.SavedMaintenanceEnableSource declaration;private final Module selected;
        private final ModuleManager registry;private final Thread thread=Thread.currentThread();private boolean revoked;
        private RootSavedMaintenanceInputInvocation(RootOriginalInputPublication publication,Module selected){
            this.publication=publication;this.selected=selected;coordinator=farmMaintenance;registry=moduleManager;declaration=publication.savedEnableDeclaration;
        }
        public boolean matchesDeclaration(FarmMaintenanceCoordinator.SavedMaintenanceEnableSource exact){return !revoked&&savedMaintenanceInputInvocation==this
            &&Thread.currentThread()==thread&&coordinator==farmMaintenance&&registry==moduleManager&&publication==originalInputPublication
            &&exact!=null&&declaration==exact&&publication.savedEnableDeclaration==exact&&publication.original==exact.farm()
            &&publication.enrollment==originalNativeEnrollment&&selected==exact.helperIdentity()&&exact.originalInputPublication()==publication;}
        public FarmMaintenanceCoordinator coordinatorIdentity(){return coordinator;}
        public RootOriginalInputPublication originalPublication(){return publication;}
        public Module selectedModule(){return selected;}
    }
    private static RootOriginalInputInvocation originalInputInvocation;
    /** Read-only exact private Root issuer. A caller object or Boolean cannot stand in for this type. */
    public static final class RootOriginalInputInvocation {
        private final Object call,source,checkpoint,pause,tick,declaration,maintenance;
        private final RootOriginalInputPublication original;
        private final Module selected;private final ModuleManager registry;private final Thread thread;
        private final RootOriginalNativeEnrollment enrollment;private boolean revoked;
        private RootOriginalInputInvocation(dev.veltraph.client.rewarp.mc.McSuspendedFarmReturnCoordinator.DriverInputCall call,RootOriginalInputPublication original){
            this.call=call;this.original=original;selected=call.driverIdentity();source=call.sourceHeader();checkpoint=call.checkpointIdentity();pause=call.originalPauseCall();tick=call.rootTickIdentity();
            registry=moduleManager;thread=Thread.currentThread();enrollment=original.enrollment;declaration=null;maintenance=null;
        }
        private RootOriginalInputInvocation(dev.veltraph.client.rewarp.mc.McSuspendedFarmReturnCoordinator.ResumeCall call,RootOriginalInputPublication original){
            this.call=call;this.original=original;selected=call.farm();source=call.sourceHeader();checkpoint=call.checkpoint();pause=null;tick=null;
            registry=moduleManager;thread=Thread.currentThread();enrollment=original.enrollment;declaration=null;maintenance=null;
        }
        private RootOriginalInputInvocation(FarmMaintenanceCoordinator.RetainedLoadoutOperation call,RootOriginalInputPublication original){
            this.call=call;this.original=original;selected=call.farm();source=retainedMaintenanceResumeInvocation;checkpoint=call.originalCheckpointIdentity();pause=call.originalPauseIdentity();tick=retainedMaintenanceResumeInvocation;
            declaration=call.originalEnableDeclaration();maintenance=call.maintenanceToken();registry=moduleManager;thread=Thread.currentThread();enrollment=original.enrollment;
        }
        private RootOriginalInputInvocation(FarmMaintenanceCoordinator.SavedMaintenanceResumeCall call,RootOriginalInputPublication original){
            this.call=call;this.original=original;selected=call.farm();source=savedMaintenanceResumeInvocation;checkpoint=call.originalCheckpointIdentity();pause=call.originalPauseIdentity();tick=savedMaintenanceResumeInvocation;
            declaration=call.originalEnableDeclaration();maintenance=call.maintenanceToken();registry=moduleManager;thread=Thread.currentThread();enrollment=original.enrollment;
        }
        private RootOriginalInputInvocation(FarmMaintenanceCoordinator.SavedMaintenanceRollbackCall call,RootOriginalInputPublication original){
            this.call=call;this.original=original;selected=call.farm();source=savedMaintenanceRollbackInvocation;checkpoint=call.originalCheckpointIdentity();pause=call.originalPauseIdentity();tick=savedMaintenanceRollbackInvocation;
            declaration=call.originalEnableDeclaration();maintenance=call.maintenanceToken();registry=moduleManager;thread=Thread.currentThread();enrollment=original.enrollment;
        }
        private RootOriginalInputInvocation(FarmMaintenanceCoordinator.SavedMaintenanceInputCall call,RootOriginalInputPublication original){
            this.call=call;this.original=original;selected=call.helperIdentity();source=savedMaintenanceInputInvocation;
            checkpoint=call.originalCheckpointIdentity();pause=call.originalPauseIdentity();tick=savedMaintenanceInputInvocation;
            declaration=call.originalEnableDeclaration();maintenance=call.maintenanceToken();registry=moduleManager;thread=Thread.currentThread();enrollment=original.enrollment;
        }
        private RootOriginalInputInvocation(RootInputOwnershipPublication call,RootOriginalInputPublication original){
            this.call=call;this.original=original;selected=call.helperIdentity();source=call.sourceDispatchIdentity();checkpoint=call.originalCheckpointIdentity();pause=call.originalPauseCall();tick=call.rootTimingIdentity();
            declaration=call.sourceDeclarationIdentity();maintenance=call.maintenanceToken();registry=moduleManager;thread=Thread.currentThread();enrollment=original.enrollment;
        }
        private boolean activeIdentity(){return !revoked&&originalInputInvocation==this&&Thread.currentThread()==thread
            &&registry==moduleManager&&original==originalInputPublication&&enrollment==originalNativeEnrollment&&original.enrollment==enrollment;}
        /** Cheap stored identities only, before the Core consumes its one-use claim. */
        public boolean matchesDriver(dev.veltraph.client.rewarp.mc.McSuspendedFarmReturnCoordinator.DriverInputCall exact){return activeIdentity()&&call==exact
            &&exact!=null&&original==exact.originalInputPublication()&&original.original==exact.farm()&&selected==exact.driverIdentity()
            &&source==exact.sourceHeader()&&checkpoint==exact.checkpointIdentity()&&pause==exact.originalPauseCall()&&tick==exact.rootTickIdentity();}
        public boolean matchesResume(dev.veltraph.client.rewarp.mc.McSuspendedFarmReturnCoordinator.ResumeCall exact){return activeIdentity()&&call==exact
            &&exact!=null&&original==exact.originalInputPublication()&&original.original==exact.farm()&&selected==exact.farm()
            &&source==exact.sourceHeader()&&checkpoint==exact.checkpoint();}
        public boolean matchesRetainedHelper(RootInputOwnershipPublication exact){return activeIdentity()&&call==exact&&exact!=null
            &&original==exact.originalInputPublication()&&original.original==exact.farm()&&selected==exact.helperIdentity()
            &&source==exact.sourceDispatchIdentity()&&checkpoint==exact.originalCheckpointIdentity()&&pause==exact.originalPauseCall()
            &&tick==exact.rootTimingIdentity()&&declaration==exact.sourceDeclarationIdentity()&&maintenance==exact.maintenanceToken();}
        public boolean matchesRetainedMaintenanceResume(FarmMaintenanceCoordinator.RetainedLoadoutOperation exact){return activeIdentity()&&call==exact&&exact!=null
            &&original==exact.originalInputPublication()&&original.original==exact.farm()&&selected==exact.farm()&&source==retainedMaintenanceResumeInvocation
            &&source instanceof RootRetainedMaintenanceResumeInvocation resume&&resume.matchesRetained(exact)&&checkpoint==exact.originalCheckpointIdentity()
            &&pause==exact.originalPauseIdentity()&&declaration==exact.originalEnableDeclaration()&&maintenance==exact.maintenanceToken();}
        public boolean matchesSavedMaintenanceResume(FarmMaintenanceCoordinator.SavedMaintenanceResumeCall exact){return activeIdentity()&&call==exact&&exact!=null
            &&original==exact.originalInputPublication()&&original.original==exact.farm()&&selected==exact.farm()&&source==savedMaintenanceResumeInvocation
            &&source instanceof RootSavedMaintenanceResumeInvocation resume&&resume.matchesResume(exact)&&checkpoint==exact.originalCheckpointIdentity()
            &&pause==exact.originalPauseIdentity()&&declaration==exact.originalEnableDeclaration()&&maintenance==exact.maintenanceToken();}
        public boolean matchesSavedMaintenanceRollback(FarmMaintenanceCoordinator.SavedMaintenanceRollbackCall exact){return activeIdentity()&&call==exact&&exact!=null
            &&original==exact.originalInputPublication()&&original.original==exact.farm()&&selected==exact.farm()&&source==savedMaintenanceRollbackInvocation
            &&source instanceof RootSavedMaintenanceRollbackInvocation resume&&resume.matchesRollback(exact)&&checkpoint==exact.originalCheckpointIdentity()
            &&pause==exact.originalPauseIdentity()&&declaration==exact.originalEnableDeclaration()&&maintenance==exact.maintenanceToken();}
        public boolean matchesSavedMaintenance(FarmMaintenanceCoordinator.SavedMaintenanceInputCall exact){return activeIdentity()&&call==exact&&exact!=null
            &&original==exact.originalInputPublication()&&original.original==exact.farm()&&selected==exact.helperIdentity()
            &&source==savedMaintenanceInputInvocation&&source instanceof RootSavedMaintenanceInputInvocation scheduled
            &&scheduled.matchesDeclaration(exact.originalEnableDeclaration())&&checkpoint==exact.originalCheckpointIdentity()
            &&pause==exact.originalPauseIdentity()&&declaration==exact.originalEnableDeclaration()&&maintenance==exact.maintenanceToken();}
        public RootOriginalInputPublication originalPublication(){return original;}
        public Module selectedModule(){return selected;}
        public Object sourceHeaderIdentity(){return source;}
    }
    /** Exact private setter invocation; only its own normal return can advance retained logical history. */
    public static final class RootOriginalOwnershipWrite {
        private final RootOriginalNativeEnrollment enrollment;private final RootOriginalEnableProof enable;
        private final RootOriginalNativeInstallation installation;
        private final RootOriginalInputPublication prior;private final Module selected;
        private final ScriptingInputLock.ControlSnapshot before;private final ModuleManager.LifecycleSnapshot lifecycle;
        private final Object driverCall,resumeCall,savedCall;private final RootOriginalInputInvocation invocation;private final boolean idle;private boolean claimed,revoked;
        private RootOriginalOwnershipWrite(Module selected,boolean idle,Object driverCall,Object resumeCall){
            this.selected=selected;this.idle=idle;this.driverCall=driverCall;this.resumeCall=resumeCall;savedCall=null;invocation=originalInputInvocation;
            enrollment=originalNativeEnrollment;enable=originalEnableProof;prior=originalInputPublication;installation=null;
            before=prior==null?enrollment==null?null:enrollment.idleControl:prior.input.control();lifecycle=moduleManager.lifecycleSnapshot();
        }
        private RootOriginalOwnershipWrite(RootOriginalNativeInstallation installation){
            this.installation=installation;selected=null;idle=true;driverCall=null;resumeCall=null;savedCall=null;invocation=null;enrollment=null;enable=null;prior=originalInputPublication;
            before=installation.idleControl;lifecycle=moduleManager.lifecycleSnapshot();
        }
        private RootOriginalOwnershipWrite(FarmMaintenanceCoordinator.SavedMaintenanceInputCall call){
            selected=call.helperIdentity();idle=false;driverCall=null;resumeCall=null;savedCall=call;invocation=originalInputInvocation;
            enrollment=originalNativeEnrollment;enable=originalEnableProof;prior=originalInputPublication;installation=null;
            before=prior.input.control();lifecycle=enable.continuity;
        }
        private RootOriginalOwnershipWrite(FarmMaintenanceCoordinator.RetainedLoadoutOperation call){
            selected=call.farm();idle=false;driverCall=null;resumeCall=call;savedCall=null;invocation=originalInputInvocation;
            enrollment=originalNativeEnrollment;enable=originalEnableProof;prior=originalInputPublication;installation=null;
            before=prior.input.control();lifecycle=enable.continuity;
        }
        private RootOriginalOwnershipWrite(FarmMaintenanceCoordinator.SavedMaintenanceResumeCall call){
            selected=call.farm();idle=false;driverCall=null;resumeCall=call;savedCall=null;invocation=originalInputInvocation;
            enrollment=originalNativeEnrollment;enable=originalEnableProof;prior=originalInputPublication;installation=null;
            before=prior.input.control();lifecycle=enable.continuity;
        }
        private RootOriginalOwnershipWrite(FarmMaintenanceCoordinator.SavedMaintenanceRollbackCall call){
            selected=call.farm();idle=false;driverCall=null;resumeCall=call;savedCall=null;invocation=originalInputInvocation;
            enrollment=originalNativeEnrollment;enable=originalEnableProof;prior=originalInputPublication;installation=null;
            before=prior.input.control();lifecycle=enable.continuity;
        }
        private boolean sourceCurrent(){
            if(revoked||originalOwnershipWrite!=this||enrollment==null||!lifecycle.known()
                ||!moduleManager.lifecycleCurrent(lifecycle)||originalEnableRegistration==null||!originalEnableRegistration.current())return false;
            if(idle)return prior==null&&enable==null&&lifecycle.noneEnabledCurrent(moduleManager)&&enrollment.logicalIdleCurrent();
            if(!enrollment.sceneCurrent(Minecraft.getInstance()))return false;
            if(enable==null||!enable.enableLineageCurrent()||enable.completed==null)return false;
            if(prior==null&&(enrollment.admission==null||!enrollment.admission.request.closedNormalEnableSourceCurrent(enable.parent,enable.completed)))return false;
            if(driverCall instanceof dev.veltraph.client.rewarp.mc.McSuspendedFarmReturnCoordinator.DriverInputCall call)
                return invocation!=null&&invocation.matchesDriver(call)&&prior!=null&&call.originalInputPublication()==prior&&call.farm()==prior.original&&selected==call.driverIdentity()
                    &&call.loadedScene()&&call.currentFinalNative()&&moduleManager.ownEnableMatches(call.driverEnableReceipt(),call.originalDisabledLifecycle(),selected);
            if(resumeCall instanceof dev.veltraph.client.rewarp.mc.McSuspendedFarmReturnCoordinator.ResumeCall call)
                return invocation!=null&&invocation.matchesResume(call)&&prior!=null&&call.originalInputPublication()==prior&&selected==prior.original&&call.farm()==prior.original&&call.nativeCurrent();
            if(resumeCall instanceof FarmMaintenanceCoordinator.RetainedLoadoutOperation call)return invocation!=null&&invocation.matchesRetainedMaintenanceResume(call)
                &&prior!=null&&prior.savedEnableDeclaration==call.originalEnableDeclaration()&&call.prepublicationCurrent();
            if(resumeCall instanceof FarmMaintenanceCoordinator.SavedMaintenanceResumeCall call)return invocation!=null&&invocation.matchesSavedMaintenanceResume(call)
                &&prior!=null&&prior.savedEnableDeclaration==call.originalEnableDeclaration()&&call.prepublicationCurrent();
            if(resumeCall instanceof FarmMaintenanceCoordinator.SavedMaintenanceRollbackCall call)return invocation!=null&&invocation.matchesSavedMaintenanceRollback(call)
                &&prior!=null&&call.originalInputPublication()==prior&&call.prepublicationCurrent();
            if(savedCall instanceof FarmMaintenanceCoordinator.SavedMaintenanceInputCall call)return invocation!=null&&invocation.matchesSavedMaintenance(call)
                &&prior!=null&&prior.savedEnableDeclaration==call.originalEnableDeclaration()&&prior.savedHelperEnable!=null
                &&prior.savedHelperEnable.parentEnableHistoryCurrent()&&call.currentFinalNative();
            return selected==enable.parent&&moduleManager.get(selected.id()).orElse(null)==selected;
        }
        public boolean beforeCurrent(ScriptingInputLock.ControlSnapshot actual,boolean active,String owner){
            if(claimed)return false;claimed=true;
            if(installation!=null)return !revoked&&originalOwnershipWrite==this&&!active&&owner==null&&actual!=null&&before!=null
                &&actual.sameHistory(before)&&before.publicationCurrent()&&installation.currentNativeNoOwner(Minecraft.getInstance())&&moduleManager.lifecycleCurrent(lifecycle);
            return active==!idle&&java.util.Objects.equals(owner,idle?null:selected.id())&&sourceCurrent()&&actual!=null&&before!=null
                &&actual.sameHistory(before)&&before.publicationCurrent()&&(prior==null?enrollment.nativeHistory!=null&&enable!=null?enrollment.nativeHistory.currentFor(enable.parent,Minecraft.getInstance()):idle:prior.nativeHistoryCurrent());
        }
        public boolean afterCurrent(boolean active,String owner){return claimed&&active==!idle&&java.util.Objects.equals(owner,idle?null:selected.id())
            &&sourceCurrentAfter();}
        private boolean sourceCurrentAfter(){
            if(installation!=null)return !revoked&&originalOwnershipWrite==this&&installation.noOwnerContextCurrent(Minecraft.getInstance())
                &&installation.keysCurrent(Minecraft.getInstance())&&moduleManager.lifecycleCurrent(lifecycle);
            // The setter has advanced control history; never recheck the stale before snapshot here.
            if(revoked||originalOwnershipWrite!=this||enrollment==null||!moduleManager.lifecycleCurrent(lifecycle)||enrollment.revoked
                ||enrollment!=originalNativeEnrollment||enrollment.registration!=rootInputReleaseRegistration
                ||!enrollment.registration.current(enrollment.registration.actualInput(),Minecraft.getInstance())
                ||originalEnableRegistration==null||!originalEnableRegistration.current())return false;
            if(idle)return prior==null&&enable==null&&lifecycle.noneEnabledCurrent(moduleManager);
            if(!enrollment.sceneCurrent(Minecraft.getInstance()))return false;
            if(enable==null||!enable.enableLineageCurrent()||enable.completed==null)return false;
            if(driverCall instanceof dev.veltraph.client.rewarp.mc.McSuspendedFarmReturnCoordinator.DriverInputCall call)return invocation!=null&&invocation.matchesDriver(call)&&call.currentFinalNative()&&call.originalInputPublication()==prior;
            if(resumeCall instanceof dev.veltraph.client.rewarp.mc.McSuspendedFarmReturnCoordinator.ResumeCall call)return invocation!=null&&invocation.matchesResume(call)&&call.nativeCurrent()&&call.originalInputPublication()==prior;
            if(resumeCall instanceof FarmMaintenanceCoordinator.RetainedLoadoutOperation call)return invocation!=null&&invocation.matchesRetainedMaintenanceResume(call)
                &&call.originalInputPublication()==prior&&call.afterOriginalInputCurrent();
            if(resumeCall instanceof FarmMaintenanceCoordinator.SavedMaintenanceResumeCall call)return invocation!=null&&invocation.matchesSavedMaintenanceResume(call)
                &&call.originalInputPublication()==prior&&call.afterOriginalInputCurrent();
            if(resumeCall instanceof FarmMaintenanceCoordinator.SavedMaintenanceRollbackCall call)return invocation!=null&&invocation.matchesSavedMaintenanceRollback(call)
                &&call.originalInputPublication()==prior&&call.afterOriginalInputCurrent();
            if(savedCall instanceof FarmMaintenanceCoordinator.SavedMaintenanceInputCall call)return invocation!=null&&invocation.matchesSavedMaintenance(call)
                &&call.originalInputPublication()==prior&&call.afterOriginalInputCurrent();
            return selected==enable.parent;
        }
    }
    private static boolean publishOriginalInputWrite(RootOriginalOwnershipWrite actual,boolean shouldOwn,String owner){return publishOriginalInputWrite(actual,shouldOwn,owner,false);}
    private static boolean publishOriginalInputWrite(RootOriginalOwnershipWrite actual,boolean shouldOwn,String owner,boolean singleRelease){
        if(originalOwnershipWrite!=null){originalOwnershipWrite.revoked=true;if(actual.driverCall==null&&actual.resumeCall==null){if(singleRelease)ScriptingInputLock.releaseAll();else ScriptingInputLock.set(shouldOwn,owner);}return false;}
        originalOwnershipWrite=actual;
        try{
            var returned=singleRelease?ScriptingInputLock.releaseOriginalRootOwnership(actual)
                :actual.driverCall!=null||actual.resumeCall!=null?ScriptingInputLock.publishIssuedRootOwnership(actual,shouldOwn,owner)
                :ScriptingInputLock.publishOriginalRootOwnership(actual,shouldOwn,owner);
            try{
            if(returned==null){if(actual.prior!=null)actual.prior.revoked=true;else if(actual.enrollment!=null)actual.enrollment.revoked=true;return false;}
            if(actual.idle){
                if(actual.installation!=null){
                    var terminal=originalNativeTermination;
                    boolean continuesTerminal=terminal!=null&&terminal.installation==actual.installation&&terminal.logicalClosed&&!terminal.revoked
                        &&terminal.closedControl!=null&&terminal.closedControl.sameHistory(actual.before);
                    actual.installation.idleControl=returned.control();
                    if(continuesTerminal){
                        var idle=terminal.idleContinuation;
                        if(idle!=null&&!idle.revoked&&idle==originalNativeIdleContinuation&&idle.control!=null&&idle.control.sameHistory(actual.before))idle.control=returned.control();
                        terminal.closedControl=returned.control();
                    }
                    return true;
                }
                var installed=originalNativeInstallation;
                if(installed!=null&&installed.idleControl!=null&&installed.idleControl.sameHistory(actual.before))installed.idleControl=returned.control();
                actual.enrollment.idleControl=returned.control();return true;
            }
            if(actual.prior==null)originalInputPublication=new RootOriginalInputPublication(actual,returned);
            else{actual.prior.input=returned;actual.prior.publishedOwner=actual.selected;actual.prior.lifecycle=actual.lifecycle;
                if(actual.savedCall instanceof FarmMaintenanceCoordinator.SavedMaintenanceInputCall call){
                    var retained=actual.prior.nativeHelperPublication;
                    if(retained==null)actual.prior.nativeHelperPublication=new RootOriginalNativeHelperPublication(actual.prior,returned);
                    else if(retained.declaration==call.originalEnableDeclaration()&&!retained.revoked)retained.control=returned;
                    else{actual.prior.revoked=true;return false;}
                }else if(actual.resumeCall instanceof FarmMaintenanceCoordinator.RetainedLoadoutOperation call){
                    var retained=actual.prior.nativeHelperPublication;
                    if(retained!=null&&retained.declaration==call.originalEnableDeclaration()&&!retained.revoked)retained.control=returned;
                    else{actual.prior.revoked=true;return false;}
                }else if(actual.resumeCall instanceof FarmMaintenanceCoordinator.SavedMaintenanceResumeCall call){
                    var retained=actual.prior.nativeHelperPublication;
                    if(retained!=null&&retained.declaration==call.originalEnableDeclaration()&&!retained.revoked)retained.control=returned;
                    else{actual.prior.revoked=true;return false;}
                }else if(actual.resumeCall instanceof FarmMaintenanceCoordinator.SavedMaintenanceRollbackCall call){
                    var retained=actual.prior.nativeHelperPublication;
                    if(retained!=null&&retained.declaration==call.originalEnableDeclaration()&&!retained.revoked)retained.control=returned;
                    else if(actual.prior.publishedOwner!=actual.prior.original){actual.prior.revoked=true;return false;}
                }else{
                    var retained=actual.prior.nativeHelperPublication;
                    if(retained!=null&&(retained.completedResume!=null||retained.completedRollback!=null||retained.completedRetained!=null)&&!retained.revoked&&actual.selected==actual.prior.original
                        &&retained.control.control().sameHistory(actual.before))retained.control=returned;
                }
            }
            return true;
            }catch(RuntimeException|Error unavailable){if(actual.prior!=null)actual.prior.revoked=true;else if(actual.enrollment!=null)actual.enrollment.revoked=true;return false;}
        }catch(RuntimeException|Error failure){if(actual.prior!=null)actual.prior.revoked=true;else if(actual.enrollment!=null)actual.enrollment.revoked=true;throw failure;}
        finally{if(originalOwnershipWrite==actual)originalOwnershipWrite=null;}
    }
    private static void publishScheduledOriginalInput(Module selected,String helperOwner,boolean active,String owner){
        var source=originalNativeEnrollment;var prior=originalInputPublication;
        boolean initial=prior==null&&originalEnableProof!=null&&originalEnableProof.completed!=null&&selected==originalEnableProof.parent;
        boolean repeat=prior!=null&&prior.publishedOwner==selected&&selected==prior.original;
        boolean idle=!active&&helperOwner==null&&originalNativeInstallation!=null;
        RootOriginalOwnershipWrite actual=null;
        try{
            if(!idle&&source!=null&&source.attempted&&!source.revoked&&source.logicalIdleCurrent()){
                // Only another actual canonical Root no-owner call may extend the idle roster evidence.
                var observed=rootRotationProvider.read();if(observed.owner()==null&&observed.noOwner()!=null&&observed.noOwner().current()){
                    source.noOwner=observed.noOwner();source.idleLifecycle=moduleManager.lifecycleSnapshot();
                }
            }
            if(idle)actual=new RootOriginalOwnershipWrite(originalNativeInstallation);
            else if(helperOwner==null&&(initial||repeat))actual=new RootOriginalOwnershipWrite(selected,false,null,null);
        }catch(RuntimeException|Error unavailable){if(prior!=null)prior.revoked=true;else if(source!=null)source.revoked=true;}
        if(actual!=null)publishOriginalInputWrite(actual,active,owner);else ScriptingInputLock.set(active,owner);
    }
    private static void releaseOriginalRootLogicalAll(){
        RootOriginalOwnershipWrite actual=null;
        try{if(originalNativeInstallation!=null)actual=new RootOriginalOwnershipWrite(originalNativeInstallation);}
        catch(RuntimeException|Error unavailable){if(originalNativeEnrollment!=null)originalNativeEnrollment.revoked=true;}
        if(actual==null)ScriptingInputLock.releaseAll();else publishOriginalInputWrite(actual,false,null,true);
    }
    /** The actual scheduled setter retains normal behavior even when its extra saved-source observation is unavailable. */
    private static void publishSavedMaintenanceOriginalInput(RootOriginalInputPublication prior,Module selected,String owner){
        RootSavedMaintenanceInputInvocation scheduled=null;RootOriginalInputInvocation invocation=null;RootOriginalOwnershipWrite write=null;
        try{
            if(savedMaintenanceInputInvocation!=null){savedMaintenanceInputInvocation.revoked=true;prior.revoked=true;}
            else if(originalInputInvocation!=null){originalInputInvocation.revoked=true;prior.revoked=true;}
            else{
                scheduled=new RootSavedMaintenanceInputInvocation(prior,selected);savedMaintenanceInputInvocation=scheduled;
                var call=farmMaintenance.savedMaintenanceInputForRoot(scheduled);
                if(call!=null&&scheduled.matchesDeclaration(call.originalEnableDeclaration())&&call.originalInputPublication()==prior&&call.helperIdentity()==selected){
                    invocation=new RootOriginalInputInvocation(call,prior);originalInputInvocation=invocation;
                    if(call.claimOriginalPublication(invocation)&&invocation.matchesSavedMaintenance(call)&&call.currentFinalNative()&&prior.nativeHistoryCurrent())
                        write=new RootOriginalOwnershipWrite(call);
                }
            }
        }catch(RuntimeException|Error unavailable){prior.revoked=true;}
        try{if(write==null){prior.revoked=true;ScriptingInputLock.set(true,owner);}else publishOriginalInputWrite(write,true,owner);}
        finally{
            if(invocation!=null){invocation.revoked=true;if(originalInputInvocation==invocation)originalInputInvocation=null;}
            if(scheduled!=null){scheduled.revoked=true;if(savedMaintenanceInputInvocation==scheduled)savedMaintenanceInputInvocation=null;}
        }
    }
    /** Ordinary actual G7 setter behavior remains single-shot; only typed normal return advances the original lineage. */
    private static ScriptingInputLock.RootPublicationReceipt publishRetainedHelperOriginalInput(RootInputOwnershipPublication call,boolean active,String owner){
        var prior=originalInputPublication;RootOriginalInputInvocation invocation=null;boolean authentic=false;
        try{
            if(call!=null&&prior!=null&&call.originalInputPublication()==prior&&call.farm()==prior.original&&active
                &&call.helperIdentity()!=null&&call.helperIdentity().id().equals(owner)&&originalInputInvocation==null){
                invocation=new RootOriginalInputInvocation(call,prior);originalInputInvocation=invocation;
                authentic=call.claimOriginalInputHandoff(invocation)&&prior.nativeHistoryCurrent()&&invocation.matchesRetainedHelper(call);
            }else if(originalInputInvocation!=null)originalInputInvocation.revoked=true;
        }catch(RuntimeException|Error unavailable){if(prior!=null)prior.revoked=true;}
        try{
            var returned=ScriptingInputLock.publishActualRootOwnership(call,active,owner);
            try{
                if(authentic&&returned!=null&&returned.current()&&invocation.matchesRetainedHelper(call)&&prior.enable.enableLineageCurrent()){
                    var declaration=call.dispatch.savedEnable;
                    if(declaration==null||prior.savedEnableDeclaration!=declaration||prior.savedHelperEnable!=call.dispatch.enable
                        ||declaration.originalInputPublication()!=prior||declaration.helperIdentity()!=invocation.selected
                        ||declaration.originalCheckpointIdentity()!=call.originalCheckpointIdentity()
                        ||!call.dispatch.originalPause.acceptedNormalPauseProof()){prior.revoked=true;return null;}
                    var originalControl=returned.originalPublicationReceipt();
                    prior.input=originalControl;prior.publishedOwner=invocation.selected;prior.lifecycle=prior.enable.continuity;
                    var retained=prior.nativeHelperPublication;
                    RootOriginalNativeHelperPublication next=null;
                    if(retained==null)next=new RootOriginalNativeHelperPublication(prior,originalControl);
                    else if(retained.declaration!=declaration||retained.enable!=call.dispatch.enable||retained.helper!=invocation.selected||retained.revoked){
                        prior.revoked=true;return null;
                    }
                    // Data-only constructor observations finish before publishing/advancing the native helper bridge.
                    if(!returned.current()||!invocation.matchesRetainedHelper(call)||!prior.enable.enableLineageCurrent()
                        ||!call.afterCurrent(active,owner)||prior.savedEnableDeclaration!=declaration
                        ||prior.savedHelperEnable!=call.dispatch.enable||declaration.originalCheckpointIdentity()!=call.originalCheckpointIdentity()
                        ||!call.dispatch.originalPause.acceptedNormalPauseProof()
                        ||!farmMaintenance.retainedOriginalDeadlineCurrent(call.dispatch.farm,call.dispatch.helper,call.dispatch.token,call.dispatch)){
                        if(next!=null)next.revoked=true;prior.revoked=true;return null;
                    }
                    if(next!=null)prior.nativeHelperPublication=next;else retained.control=originalControl;
                    return returned;
                }
            }catch(RuntimeException|Error unavailable){}
            if(prior!=null)prior.revoked=true;return null;
        }catch(RuntimeException|Error failure){if(prior!=null)prior.revoked=true;throw failure;}
        finally{if(invocation!=null){invocation.revoked=true;if(originalInputInvocation==invocation)originalInputInvocation=null;}}
    }
    private static boolean publishIssuedDriverOriginalInput(dev.veltraph.client.rewarp.mc.McSuspendedFarmReturnCoordinator.DriverInputCall call){
        var prior=originalInputPublication;
        if(call==null||prior==null||call.originalInputPublication()!=prior||call.farm()!=prior.original)return false;
        if(originalInputInvocation!=null){originalInputInvocation.revoked=true;return false;}
        var invocation=new RootOriginalInputInvocation(call,prior);originalInputInvocation=invocation;
        try{
            if(!call.claimOriginalPublication(invocation))return false;
            if(!call.loadedScene()||!call.currentFinalNative()||!prior.nativeHistoryCurrent())return false;
            var actual=new RootOriginalOwnershipWrite(call.driverIdentity(),false,call,null);
            return publishOriginalInputWrite(actual,true,call.driverIdentity().id());
        }finally{invocation.revoked=true;if(originalInputInvocation==invocation)originalInputInvocation=null;}
    }
    private static boolean publishIssuedResumeOriginalInput(dev.veltraph.client.rewarp.mc.McSuspendedFarmReturnCoordinator.ResumeCall call){
        var prior=originalInputPublication;
        if(call==null||prior==null||call.originalInputPublication()!=prior||call.farm()!=prior.original)return false;
        if(originalInputInvocation!=null){originalInputInvocation.revoked=true;return false;}
        var invocation=new RootOriginalInputInvocation(call,prior);originalInputInvocation=invocation;
        try{
            if(!call.claimOriginalPublication(invocation))return false;
            if(!call.nativeCurrent()||!prior.nativeHistoryCurrent())return false;
            var actual=new RootOriginalOwnershipWrite(call.farm(),false,null,call);
            return publishOriginalInputWrite(actual,true,call.farm().id());
        }finally{invocation.revoked=true;if(originalInputInvocation==invocation)originalInputInvocation=null;}
    }

    /** Authentic real Bootstrap provider/installation/immutable FarmServices affiliation; no public constructor. */
    public static final class RootInputReleaseRegistration {
        private final RootRotationAuthorityProvider provider;
        private final FarmServices originalServices;
        private final dev.veltraph.client.service.mc.McInputService input;
        private final dev.veltraph.client.service.mc.McOwnedRotation.RootInstallation installation;
        private RootInputReleaseRegistration(RootRotationAuthorityProvider provider,FarmServices originalServices,
                dev.veltraph.client.service.mc.McOwnedRotation.RootInstallation originalInstallation){
            this.provider=provider;this.originalServices=originalServices;
            input=originalServices.input() instanceof dev.veltraph.client.service.mc.McInputService actual?actual:null;
            // Inert exact provenance from the actual install operation, captured even before Minecraft assigns its native thread.
            installation=originalInstallation;
        }
        public dev.veltraph.client.service.mc.McInputService actualInput(){return input;}
        public dev.veltraph.client.service.mc.McBlockBreakingService actualBreaking(){return originalServices.breaking() instanceof dev.veltraph.client.service.mc.McBlockBreakingService actual?actual:null;}
        /** Exact immutable canonical rotation affiliation; an arbitrary install/supplier cannot satisfy this. */
        public boolean currentFarmInput(dev.veltraph.client.service.mc.McInputService exact,Minecraft actualClient){return exact!=null&&originalServices.input()==exact&&current(exact,actualClient);}
        public boolean currentFarmRotation(dev.veltraph.client.service.mc.McRotationService exact,Minecraft actualClient){
            return exact!=null&&originalServices.rotation()==exact&&current(input,actualClient);
        }
        public boolean currentFarmBreaking(dev.veltraph.client.service.mc.McBlockBreakingService exact,Minecraft actualClient){
            return exact!=null&&originalServices.breaking()==exact&&current(input,actualClient);
        }
        public boolean currentFarmInventory(dev.veltraph.client.service.mc.McInventoryService exact,Minecraft actualClient){
            return exact!=null&&originalServices.inventory()==exact&&current(input,actualClient);
        }
        /** Stored-source/native identities only. Never invokes selected-owner/rotation provider/clock callbacks. */
        public boolean current(dev.veltraph.client.service.mc.McInputService exact,Minecraft actualClient){
            return rootInputReleaseRegistration==this&&input!=null&&input==exact&&provider.client==actualClient
                &&actualClient.isSameThread()&&originalServices==farmServices&&originalServices.input()==exact
                &&provider.registeredCurrent()&&installation!=null&&installation.current(actualClient,provider);
        }
    }
    /** Actual Bootstrap registration only; public users cannot construct a provider or no-owner observation. */
    public static final class RootRotationAuthorityProvider implements java.util.function.Supplier<Object> {
        private final Minecraft client;private final ModuleManager registry;private final ConfigService store;private RootRotationCall call;
        private RootRotationAuthorityProvider(Minecraft client,ModuleManager registry,ConfigService store){this.client=client;this.registry=registry;this.store=store;}
        public Object get(){return read().owner();}
        public RootRotationRead read(){
            if(call!=null){call.revoked=true;return new RootRotationRead(ownedRotationAuthority(),null);}
            var next=new RootRotationCall(this,client.isSameThread()?registry.lifecycleSnapshot():null);call=next;
            try {var owner=ownedRotationAuthority();return new RootRotationRead(owner,next.revoked?null:next.idle,next.revoked?null:next.gui);}
            finally{if(call==next)call=null;}
        }
        public boolean registeredCurrent(){return rootRotationProvider==this&&client==Minecraft.getInstance()&&registry==moduleManager&&store==config;}
    }
    private static final class RootRotationCall {
        final RootRotationAuthorityProvider provider;final ModuleManager.LifecycleSnapshot before;boolean revoked;RootNoOwnerObservation idle;RootGuiAvailabilityObservation gui;
        private RootRotationCall(RootRotationAuthorityProvider provider,ModuleManager.LifecycleSnapshot before){this.provider=provider;this.before=before;}
    }
    public static final class RootRotationRead {
        private final Object owner;private final RootNoOwnerObservation idle;private final RootGuiAvailabilityObservation gui;
        private RootRotationRead(Object owner,RootNoOwnerObservation idle){this(owner,idle,null);}
        private RootRotationRead(Object owner,RootNoOwnerObservation idle,RootGuiAvailabilityObservation gui){this.owner=owner;this.idle=idle;this.gui=gui;}
        public Object owner(){return owner;}public RootNoOwnerObservation noOwner(){return idle;}public RootGuiAvailabilityObservation guiAvailability(){return gui;}
    }
    /** Callback-free native header; only the original canonical provider call can mint a GUI cause. */
    public static final class RootGuiAvailabilityHeader {
        private final RootRotationAuthorityProvider provider;private final ModuleManager.LifecycleSnapshot lifecycle;
        private final dev.veltraph.client.config.MapConfigService.StoredStateSnapshot settings;
        private final dev.veltraph.client.input.ScriptingInputLock.ControlSnapshot input;
        private final Object player,world,connection,options,gameMode,screen;private final float yaw,pitch;
        private RootGuiAvailabilityHeader(RootRotationAuthorityProvider provider){this.provider=provider;var c=provider.client;
            lifecycle=provider.registry.lifecycleSnapshot();input=dev.veltraph.client.input.ScriptingInputLock.controlSnapshot();
            settings=provider.store instanceof dev.veltraph.client.config.MapConfigService m?m.storedStateSnapshot():provider.store instanceof dev.veltraph.client.config.JsonConfigService j?j.storedStateSnapshot():null;
            player=c.player;world=c.level;connection=c.getConnection();options=c.options;gameMode=c.gameMode;screen=c.gui.screen();yaw=c.player==null?Float.NaN:c.player.getYRot();pitch=c.player==null?Float.NaN:c.player.getXRot();
        }
        public Object storeIdentity(){return provider.store;}public Object registryIdentity(){return provider.registry;}
        public boolean capturedInputActive(){return input!=null&&input.active();}public String capturedInputOwnerModuleId(){return input==null?null:input.ownerModuleId();}
        public boolean current(){var c=provider.client;return c.isSameThread()&&provider.registeredCurrent()&&lifecycle.known()&&provider.registry.lifecycleCurrent(lifecycle)
            &&settings!=null&&settings.current()&&input!=null&&input.current()&&c.player==player&&c.level==world&&c.getConnection()==connection&&c.options==options&&c.gameMode==gameMode
            &&screen!=null&&screen.getClass()==net.minecraft.client.gui.screens.inventory.InventoryScreen.class&&c.gui.screen()==screen
            &&c.player!=null&&c.level!=null&&c.getConnection()!=null&&c.options!=null&&c.gameMode!=null&&c.player.level()==c.level&&c.player.isAlive()
            &&!c.isPaused()&&!c.player.isScoping()&&!c.level.tickRateManager().isEntityFrozen(c.player)&&c.getCameraEntity()==player
            &&Math.abs(dev.veltraph.client.rotation.SourceRotationStepper.wrap(c.player.getYRot()-yaw))<.001&&Math.abs(c.player.getXRot()-pitch)<.001;}
        public boolean matchesNative(Object p,Object w,Object connection){return player==p&&world==w&&this.connection==connection&&current();}
    }
    public static RootGuiAvailabilityHeader guiAvailabilityHeader(){var provider=rootRotationProvider;
        if(provider==null||!provider.client.isSameThread()||!provider.registeredCurrent()||provider.client.gui.screen()==null)return null;
        var header=new RootGuiAvailabilityHeader(provider);return header.current()?header:null;}
    public static final class RootGuiAvailabilityObservation {
        private final RootRotationCall call;private final RootGuiAvailabilityHeader header;
        private RootGuiAvailabilityObservation(RootRotationCall call,RootGuiAvailabilityHeader header){this.call=call;this.header=header;}
        public boolean current(){return !call.revoked&&call.before!=null&&call.before.known()&&call.provider.registry.lifecycleCurrent(call.before)&&header.current();}
        public boolean matches(RootGuiAvailabilityHeader original){return original!=null&&original.provider==call.provider&&original.current()&&current();}
    }
    private static void observeActualRootGuiAvailability(){var provider=rootRotationProvider;var call=provider==null?null:provider.call;
        if(call==null||call.revoked||!provider.registeredCurrent()||call.before==null||!call.before.known()||!provider.registry.lifecycleCurrent(call.before))return;
        var header=guiAvailabilityHeader();if(header!=null&&provider.registry.lifecycleCurrent(call.before))call.gui=new RootGuiAvailabilityObservation(call,header);
    }
    public static final class RootNoOwnerObservation {
        private final RootRotationCall call;private final Object player,world,connection,options,gameMode;
        private RootNoOwnerObservation(RootRotationCall call){this.call=call;var c=call.provider.client;player=c.player;world=c.level;connection=c.getConnection();options=c.options;gameMode=c.gameMode;}
        public boolean current(){var provider=call.provider;var c=provider.client;
            if(!c.isSameThread()||call.revoked||!provider.registeredCurrent()||call.before==null||!call.before.known()||!provider.registry.lifecycleCurrent(call.before))return false;
            if(!provider.registry.noGameplayOwnerRosterCurrent(call.before,originalPassiveSource))return false;
            return rootNativeHigherPriorityIdle()&&c.isSameThread()&&c.player==player&&c.level==world&&c.getConnection()==connection&&c.options==options&&c.gameMode==gameMode
                &&c.player!=null&&c.level!=null&&c.getConnection()!=null&&c.player.level()==c.level&&c.player.isAlive()&&!c.isPaused()&&c.gui.screen()==null
                &&!c.player.isScoping()&&c.getCameraEntity()==c.player&&c.player.containerMenu==c.player.inventoryMenu&&c.player.containerMenu.getCarried().isEmpty()
                &&provider.registry.lifecycleCurrent(call.before);
        }
        /** Same canonical cause, private stored-enabled fields only for the manual final leaf. */
        public boolean manualCurrent(){var provider=call.provider;var c=provider.client;
            if(!c.isSameThread()||call.revoked||!provider.registeredCurrent()||call.before==null||!call.before.known()||!provider.registry.lifecycleCurrent(call.before))return false;
            if(!provider.registry.noGameplayOwnerRosterCurrent(call.before,originalPassiveSource))return false;
            return rootNativeHigherPriorityIdle()&&c.isSameThread()&&c.player==player&&c.level==world&&c.getConnection()==connection&&c.options==options&&c.gameMode==gameMode
                &&c.player!=null&&c.level!=null&&c.getConnection()!=null&&c.player.level()==c.level&&c.player.isAlive()&&!c.isPaused()&&c.gui.screen()==null
                &&!c.player.isScoping()&&c.getCameraEntity()==c.player&&c.player.containerMenu==c.player.inventoryMenu&&c.player.containerMenu.getCarried().isEmpty()
                &&provider.registry.lifecycleCurrent(call.before);
        }
    }
    /** Source-owned native fields only. Unknown responder implementations are unavailable to this scoped proof. */
    private static boolean rootNativeHigherPriorityIdle(){
        if(combatAutoHeal!=null&&combatAutoHeal.ownsControl())return false;
        if(hydra!=null){var responder=hydra.nativeResponderIdentity();
            if(!(responder instanceof dev.veltraph.client.hydra.mc.McHydraResponder actual)||!actual.nativeIdleCurrent()||!hydra.nativeIdleCurrent(responder))return false;}
        if(scriptScheduler!=null){var state=scriptScheduler.state();if(state!=dev.veltraph.client.scheduling.SourceRestRuntime.State.IDLE)return false;}
        return !svenWarpLifecycle.active()&&!ghostWarpLifecycle.active()&&!ghostTransportQuiescent&&!bottleTransportQuiescent
            &&(farmMaintenance==null||!farmMaintenance.active())&&(foragingMaintenance==null||!foragingMaintenance.active())
            &&(galateaWorkflows==null||!galateaWorkflows.active())&&(farmCycle==null||!farmCycle.active())
            &&(autoRewarp==null||autoRewarp.state()==dev.veltraph.client.rewarp.AutoRewarpState.IDLE);
    }
    private static void observeActualRootNoOwner(){var provider=rootRotationProvider;var call=provider==null?null:provider.call;
        if(call==null||call.revoked||!provider.registeredCurrent())return;
        try{var observed=new RootNoOwnerObservation(call);if(observed.current())call.idle=observed;}catch(RuntimeException unavailable){}
    }
    private static RootFarmCycleTick originalCycleTick;
    /** Retained actual private Root invocation; lookup/data alone cannot start a cycle or grant input. */
    public static final class RootFarmCycleTick {
        private final dev.veltraph.client.rewarp.mc.McFarmCycleCoordinator coordinator;
        private final RootOriginalGameplayTickInvocation gameplay;
        private final ModuleManager.LifecycleSnapshot beforeLifecycle=moduleManager.lifecycleSnapshot();
        public ModuleManager.LifecycleSnapshot originalBeforeLifecycle(){return beforeLifecycle;}
        private final Thread thread=Thread.currentThread();
        private boolean claimed,revoked;
        private RootFarmCycleTick(dev.veltraph.client.rewarp.mc.McFarmCycleCoordinator coordinator,RootOriginalGameplayTickInvocation gameplay){this.coordinator=coordinator;this.gameplay=gameplay;}
        public boolean claim(dev.veltraph.client.rewarp.mc.McFarmCycleCoordinator exact){
            if(claimed||revoked||originalCycleTick!=this||coordinator!=exact||Thread.currentThread()!=thread)return false;
            var caller=StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).walk(frames->frames.skip(1).findFirst().orElse(null));
            if(caller==null||caller.getDeclaringClass()!=dev.veltraph.client.rewarp.mc.McFarmCycleCoordinator.class||!caller.getMethodName().equals("tickFromOriginalRoot"))return false;
            claimed=true;
            return currentFor(exact);
        }
        private Module pausedInput(dev.veltraph.client.service.mc.McInputService service){
            return currentFor(coordinator)&&gameplay.publication!=null&&originalInputPublication==gameplay.publication
                &&gameplay.publication.enrollment==originalNativeEnrollment?coordinator.pausedInputForOriginalRoot(this,service):null;
        }
        private Module pausedCamera(dev.veltraph.client.service.mc.McRotationService service){
            return currentFor(coordinator)&&gameplay.publication!=null&&originalInputPublication==gameplay.publication
                &&gameplay.publication.enrollment==originalNativeEnrollment?coordinator.pausedCameraForOriginalRoot(this,service):null;
        }
        public boolean currentFor(dev.veltraph.client.rewarp.mc.McFarmCycleCoordinator exact){return claimed&&!revoked&&originalCycleTick==this&&exact==coordinator&&farmCycle==coordinator
            &&Thread.currentThread()==thread&&gameplay!=null&&originalGameplayTickInvocation==gameplay&&gameplay.current();}
    }
    private static void tickOriginalFarmCycle(){
        if(originalCycleTick!=null){originalCycleTick.revoked=true;return;}
        var gameplay=originalGameplayTickInvocation;
        if(gameplay==null||!gameplay.current())return;
        var source=new RootFarmCycleTick(farmCycle,gameplay);originalCycleTick=source;
        try{farmCycle.tickFromOriginalRoot(source);}finally{source.revoked=true;if(originalCycleTick==source)originalCycleTick=null;}
    }
    /** Lightweight live identity lookup for frame arithmetic; no readiness, scans or action callbacks. */
    private static Object ownedRotationAuthority(){
        if(moduleManager==null)return null;
        var client=Minecraft.getInstance();
        if(client.player==null||client.level==null||client.getConnection()==null||!client.player.isAlive()
                ||client.player.level()!=client.level)return null;
        if(client.gui.screen()!=null){observeActualRootGuiAvailability();return null;}
        if(combatAutoHeal!=null&&!survivalStopSuppressed&&combatAutoHeal.ownsControl())return combatAutoHeal;
        if(hydra!=null&&(hydra.hasActiveLocalReaction()||hydra.hasActiveGeneralResponse()))return hydra;
        if(scriptScheduler!=null){
            var state=scriptScheduler.state();
            if(state==dev.veltraph.client.scheduling.SourceRestRuntime.State.WAITING_TO_DISCONNECT
                    ||state==dev.veltraph.client.scheduling.SourceRestRuntime.State.DISCONNECTED
                    ||state==dev.veltraph.client.scheduling.SourceRestRuntime.State.RECONNECTING)return null;
            if(state==dev.veltraph.client.scheduling.SourceRestRuntime.State.VERIFYING_ARRIVAL)
                return scriptScheduler.rotationAuthorityToken();
        }
        if(svenWarpLifecycle.active()||ghostWarpLifecycle.active()||ghostTransportQuiescent||bottleTransportQuiescent)return null;
        if(farmMaintenance!=null&&farmMaintenance.active())return farmMaintenance.rotationAuthorityToken();
        if(foragingMaintenance!=null&&foragingMaintenance.active())return foragingMaintenance.rotationAuthorityToken();
        if(galateaWorkflows!=null&&galateaWorkflows.active()){
            for(var helper:galateaWorkflows.helpers()){
                var coordinator=galateaWorkflows.coordinator(helper);
                if(coordinator.active())return coordinator.rotationAuthorityToken();
            }
            return null;
        }
        if(farmCycle!=null&&farmCycle.active())return farmCycle.rotationAuthorityToken();
        if(autoRewarp!=null&&autoRewarp.state()!=dev.veltraph.client.rewarp.AutoRewarpState.IDLE)
            return autoRewarp.state()==dev.veltraph.client.rewarp.AutoRewarpState.RETURNING?farmAutoRecovery:null;
        var scan=moduleManager.scanUniqueGameplayOwner(module->
                module instanceof dev.veltraph.client.foraging.module.AutoForagingModule foraging&&foraging.hasIssuedHubWarp()
                ||module instanceof dev.veltraph.client.combat.sven.SvenParentModule sven&&sven.pendingNativeWarp().isPresent()
                ||module instanceof dev.veltraph.client.combat.ghost.GhostGrindModule ghost&&ghost.pendingNativeWarp().isPresent());
        if(scan.status()==ModuleManager.UniqueOwnerStatus.NONE)observeActualRootNoOwner();
        return scan.owner();
    }
    /** Other owners only: the family's paused parent/helper reservation must not cancel itself. */
    private static boolean galateaHigherPriorityOwned(){
        return maintenanceOwnsFarm()||(foragingMaintenance!=null&&foragingMaintenance.active())||scheduledRestActive()
                ||(farmCycle!=null&&farmCycle.active())
                ||(autoRewarp!=null&&autoRewarp.state()!=dev.veltraph.client.rewarp.AutoRewarpState.IDLE)
                ||(savedAnchorRecovery!=null&&savedAnchorRecovery.isEnabled())
                ||(combatAutoHeal!=null&&combatAutoHeal.ownsControl())
                ||(hydra!=null&&(hydra.hasActiveLocalReaction()||hydra.hasActiveGeneralResponse()))
                ||moduleManager.all().stream().anyMatch(module->module.hasPendingCleanup()
                    &&!galateaWorkflows.helpers().contains(module)&&!galateaWorkflows.ownsParent(module));
    }
    private static void observeScheduledConnection(Minecraft client){
        Object connection=client.getConnection();
        if(connection!=null&&connection!=scheduledObservedConnection){
            scheduledObservedConnection=connection;scheduledConnectionSince=clock.nowMillis();
        }
    }
    private static long scheduledRecentRewarpTimestamp(){
        // A newly observed native connection conservatively starts a fresh guard window. This is
        // scope age, never evidence of a rewarp or arrival. Actual successful sends extend it.
        return Math.max(scheduledConnectionSince,Math.max(lastScheduledRewarpMillis,farmCycle==null?-1:farmCycle.lastRewarpMillis()));
    }
    private static boolean atSourceFarmRewarpBoundary(dev.veltraph.client.farming.module.FarmModule farm){
        var client=Minecraft.getInstance();
        if(farmCycle==null||client.player==null||client.level==null)return false;
        var position=new dev.veltraph.client.world.Vec3(client.player.getX(),client.player.getY(),client.player.getZ());
        String profile=dev.veltraph.client.rewarp.mc.McFarmCycleCoordinator.profileId(farm);
        return farmCycle.plans().stream().filter(plan->plan.profileId().equals(profile)).anyMatch(plan->
                plan.matchingEnd(gameContext.current().area(),client.level.dimension().identifier().toString(),position,
                        config.get(FarmingConfig.FARM_CYCLE_MARKER_RADIUS)).isPresent());
    }
    private static boolean maintenanceOwnsFarm(){return farmMaintenance!=null&&farmMaintenance.active();}
    private static boolean scheduledRestActive(){
        if(scriptScheduler==null)return false;
        return switch(scriptScheduler.state()){
            case IDLE,COMPLETE,CANCELLED,FAILED->false;
            default->true;
        };
    }

    /** Mirror the live MC mouse sensitivity into config so the aim's GCD step matches the user's mouse. */
    private static void syncMouseSensitivity(Minecraft client) {
        try {
            if (client.options != null) {
                double sens = client.options.sensitivity().get();
                config.set(dev.veltraph.client.config.FarmingConfig.ROTATION_MOUSE_SENSITIVITY, sens);
            }
        } catch (Throwable ignored) {
            // Option API unavailable — keep the configured default.
        }
    }

    public static DebugLogController debugLog() {
        return debugLog;
    }

    public static ModuleManager moduleManager() {
        return moduleManager;
    }

    public static ConfigService config() {
        return config;
    }

    public static FarmLayoutRegistry farmRegistry() {
        return farmRegistry;
    }

    public static GameContextService gameContext() {
        return gameContext;
    }

    public static ChatService chat() {
        return chat;
    }

    public static ModuleActivationGuard guard() {
        return guard;
    }
    /** Explicit registered maintenance participants; registration and committed adapter binding are opt-in. */
    public static dev.veltraph.client.rewarp.mc.FarmMaintenanceCoordinator farmMaintenance(){return farmMaintenance;}
    public static dev.veltraph.client.foraging.maintenance.mc.McForagingMaintenanceCoordinator foragingMaintenance(){return foragingMaintenance;}
    public static dev.veltraph.client.galatea.workflow.McGalateaWorkflowFamily galateaWorkflows(){return galateaWorkflows;}
    /** Explicit request pins the current eligible registered parent; ordinary ticks still use guard.start. */
    public static boolean requestGalateaReset(){return galateaWorkflows!=null&&!galateaHigherPriorityOwned()&&galateaWorkflows.requestReset();}
    /** Installed exact family scheduled-rest lifecycle and terminal source stop timer. */
    public static dev.veltraph.client.scheduling.mc.McScriptScheduler scriptScheduler(){return scriptScheduler;}

    /** Shared return-to-location service (saved-point store + pending-return state). */
    public static RewarpService rewarp() {
        return rewarpService;
    }

    /** Handler that applies parsed {@code rewarp} commands against {@link #rewarp()}. */
    public static RewarpCommandHandler rewarpCommand() {
        return rewarpCommand;
    }

    /** Automatic rewarp runtime (Batch 2): pure warp/wait/settle/resume state machine. */
    /** Identity only: a public constructor or permissive predicate cannot affiliate a tool-recovery issuer. */
    public static boolean actualFarmToolCoordinator(Minecraft client,Object candidate) {
        return client==Minecraft.getInstance()&&client.isSameThread()&&candidate!=null
                &&(candidate==farmCycle||candidate==farmAutoRecovery);
    }

    public static AutoRewarpRuntime autoRewarp() {
        return autoRewarp;
    }

    /** Local Hydra / Anti-Staff feature (Target 4): proximity + teleport/world/limbo watcher, local only. */
    public static HydraAntiStaff hydra() {
        return hydra;
    }

    /** Garden Visitors advisory watcher (Batch 4): local accept/skip recommendation, read-only. */
    public static VisitorAdvisor visitor() {
        return visitorAdvisor;
    }

    /** Hyperion auto-heal assist (Batch 6, Slayers/Ghost): local survival heal, opt-in. */
    public static CombatAutoHeal autoHeal() {
        return combatAutoHeal;
    }

    /** Developer Mode controller (Target 1): persistent dev mode + forced verbose logging. */
    public static dev.veltraph.client.dev.DeveloperModeController developerMode() {
        return developerMode;
    }

    /** Local developer test-action registry (Target 1): shared by the UI and {@code /vdebug dev}. */
    public static dev.veltraph.client.dev.DevTestRegistry devTests() {
        return devTests;
    }

    /** Live MC execution seam for dev-test actions (Target 1). */
    public static dev.veltraph.client.dev.DevTestContext devTestContext() {
        return devTestContext;
    }
}
