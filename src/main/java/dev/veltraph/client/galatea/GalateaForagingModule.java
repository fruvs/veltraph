package dev.veltraph.client.galatea;

import dev.veltraph.client.config.*;
import dev.veltraph.client.core.AbstractModule;
import dev.veltraph.client.core.ModuleCategory;
import dev.veltraph.client.game.GameArea;
import dev.veltraph.client.foraging.ForagingAxePolicy;
import dev.veltraph.client.foraging.ForagingHarvestCycle;
import dev.veltraph.client.foraging.mc.McForagingAbilitySource;
import dev.veltraph.client.navigation.LocalRoute;
import dev.veltraph.client.navigation.mc.McTravelNavigator;
import dev.veltraph.client.service.FarmServices;
import dev.veltraph.client.world.BlockPos;
import dev.veltraph.client.world.Direction;
import dev.veltraph.client.world.Vec3;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.entity.item.ItemEntity;

/** Loaded-world Galatea collection controller. All changes use vanilla inputs/item interaction.
 * Tree sessions retain connected branches through source axe modes and wait for world outcomes. */
public final class GalateaForagingModule extends AbstractModule implements dev.veltraph.client.foraging.scheduling.ForagingRestOwner {
    private final Minecraft client;
    private final ConfigService config;
    private final FarmServices services;
    private final GalateaPlanner.Kind kind;
    private final McTravelNavigator navigation;
    private final dev.veltraph.client.navigation.mc.McParentRotationService interactionRotation;
    private final dev.veltraph.client.galatea.recovery.McFigFallRecovery fallRecovery;
    private final Map<BlockPos,Long> blacklist=new HashMap<>();
    private final Set<BlockPos> attemptedApproaches=new HashSet<>();
    private final ForagingHarvestCycle harvestCycle=new ForagingHarvestCycle();
    private final FigAimPointCache jumpAimPoints=new FigAimPointCache();
    private final FigJumpCounter jumpCounter=new FigJumpCounter();
    private final FigJumpLatch figJumpLatch=new FigJumpLatch();
    private final FigEndDisplacementCache figDisplacement=new FigEndDisplacementCache();
    private final FigTargetPassability targetPassability=new FigTargetPassability();
    private boolean ownedJumpHeld;
    private FigJumpAimPolicy.Mode lastJumpAimMode;
    private final MaterialHarvestCycle materialCycle=new MaterialHarvestCycle();
    private final Set<UUID> previousDrops=new HashSet<>();
    private GalateaSession session;
    private McFigSourceSelector figSourceSelector;
    private FigSourceRegionBinding figSourceRegion;
    private FigObservedDescriptors figObservedDescriptors;
    private int figObservedScaffoldHeight;
    private FigObservedScaffoldMetadata.Observation figScaffoldMetadata;
    private final McFigScaffoldCaller figScaffold;
    private java.util.function.BooleanSupplier figRootScaffoldPermission=()->false;
    private java.util.function.BooleanSupplier figRootScaffoldNetworkGap;
    private final FigSourceDescriptorCooldown figDescriptorCooldown=new FigSourceDescriptorCooldown();
    private record DescriptorIdentity(BlockPos base,List<BlockPos> geometry,String provenance){private DescriptorIdentity{geometry=List.copyOf(geometry);}}
    private final Map<DescriptorIdentity,Object> figDescriptorTokens=new HashMap<>();
    private Object figDescriptorToken;
    private final FigHarvestPickupLedger figHarvestLedger=new FigHarvestPickupLedger();
    private final FigToolWearLedger figToolWearLedger=new FigToolWearLedger();
    private long figHarvestActivationGeneration,figHarvestTreeGeneration,figHarvestDeadline;
    private String figHarvestSettings;
    private List<dev.veltraph.client.inventory.NativeStackIdentity.Snapshot> figHarvestGear=List.of();
    private java.lang.ref.WeakReference<Object> figDescriptorRegistryPlayer=new java.lang.ref.WeakReference<>(null),
            figDescriptorRegistryWorld=new java.lang.ref.WeakReference<>(null),figDescriptorRegistryConnection=new java.lang.ref.WeakReference<>(null);
    private BlockPos figPreviousTarget=new BlockPos(0,0,0);
    private GalateaPlanner.Tree previous;
    private BlockPos target,scaffoldBase,scaffoldSupport;
    private Set<String> targetIds=Set.of();
    private net.minecraft.client.multiplayer.ClientLevel ownerLevel;
    private Object travelConnection;
    private net.minecraft.client.player.LocalPlayer owner;
    private Object figOwnerConnection;
    private int axeSlot=-1,materialSlot=-1,previousSlot=-1,lastSelectedSlot=-1,placedScaffolds,trees,resources,removed,largestBatch,multiBatches,activations,inventoryLogsBefore,collectedLogs;
    private long nextScan,actionStarted,nextUse,lastProgress,missingSince,nextObservation,nextAxe,pickupStarted,pickupQuietSince;
    private String phase="IDLE",status="Idle";
    private final dev.veltraph.client.foraging.scheduling.ForagingPauseGate rest=new dev.veltraph.client.foraging.scheduling.ForagingPauseGate();
    private HitResult blockedRestHit;

    public GalateaForagingModule(String id,String name,GalateaPlanner.Kind kind,Minecraft client,ConfigService config,FarmServices services) {
        super(id,name,ModuleCategory.FORAGING);
        this.kind=kind;this.client=client;this.config=config;this.services=services;
        interactionRotation=new dev.veltraph.client.navigation.mc.McParentRotationService(client,services,()->this);navigation=new McTravelNavigator(client,services,config);
        fallRecovery=new dev.veltraph.client.galatea.recovery.McFigFallRecovery(client,config,services);
        figScaffold=kind==GalateaPlanner.Kind.FIG?new McFigScaffoldCaller(client,config,services,new McFigScaffoldCaller.Host(){
            public dev.veltraph.client.core.Module owner(){return GalateaForagingModule.this;}
            public boolean permission(){return !rest.paused()&&figRootScaffoldNetworkGap!=null&&figRootScaffoldPermission.getAsBoolean();}
            public boolean networkGapObserved(){return figRootScaffoldNetworkGap!=null&&figRootScaffoldNetworkGap.getAsBoolean();}
            public String settings(){return scaffoldSettings();}
            public FigHarvestPickupLedger harvestLedger(){return figHarvestLedger;}
            @Override public FigToolWearLedger toolWearLedger(){return figToolWearLedger;}
            public FigHarvestPickupLedger.Scope harvestScope(){return figNativeHarvestScope();}
            public int axeSlot(){return axeSlot;}
            public void selectSlot(int slot){GalateaForagingModule.this.selectSlot(slot);}
            public void aim(BlockPos seed){GalateaForagingModule.this.aim(seed,false);}
            public void attack(boolean held){services.breaking().setBreaking(held);}
            public void enterThrowing(){if(session!=null){session.setMode(GalateaPlanner.AxeMode.THROWING);jumpCounter.enterJumpOrThrow();target=null;phase="CUT";actionStarted=services.clock().nowMillis();}}
            public void enterBreak(Vec3 centeredFeet){scaffoldBase=centeredFeet.toBlockPos();target=null;phase="CUT";actionStarted=services.clock().nowMillis();}
            public void failed(String why){status=why;setEnabled(false);}
        }):null;
    }
    public void bindRootScaffoldPermission(java.util.function.BooleanSupplier permission){if(isEnabled())throw new IllegalStateException("Stop Fig before changing Root permission");figRootScaffoldPermission=Objects.requireNonNull(permission);}
    public void bindRootScaffoldNetworkGap(java.util.function.BooleanSupplier gap){if(isEnabled())throw new IllegalStateException("Stop Fig before changing network-gap observer");figRootScaffoldNetworkGap=Objects.requireNonNull(gap);}
    public void bindRootScaffoldUse(McFigOwnedUseBridge bridge){if(isEnabled()||figScaffold==null)throw new IllegalStateException("Native Fig scaffold bridge requires disabled Fig parent");figScaffold.bindBridge(bridge);}
    public void bindFigScaffoldAxeDecisionRandom(java.util.Random random){if(isEnabled()||figScaffold==null)throw new IllegalStateException("Axe-decision random binding requires disabled Fig parent");figScaffold.bindAxeDecisionRandom(random);}
    public void resetFigScaffoldAxeDecisionRandom(){if(isEnabled()||figScaffold==null)throw new IllegalStateException("Axe-decision random restore requires disabled Fig parent");figScaffold.resetAxeDecisionRandom();}
    public boolean figScaffoldAxeDecisionRandomBound(){return figScaffold!=null&&figScaffold.axeDecisionRandomBound();}
    public Optional<McFigScaffoldCaller.WearAdmission> figScaffoldWearAdmission(){return figScaffold==null?Optional.empty():figScaffold.wearAdmission();}
    /** Explicit command hook only; ordinary restart does not erase the source descriptor cooldown. */
    public void clearFigDescriptorCooldown(){figDescriptorCooldown.reset();}
    public McFigOwnedUseBridge.Binding scaffoldUseBinding(){return figScaffold==null?null:figScaffold.binding();}
    public Optional<McFigScaffoldCaller.Receipt> figScaffoldReceipt(){return figScaffold==null?Optional.empty():Optional.of(figScaffold.receipt());}
    public boolean figScaffoldSourceParentEntryHold(long now){return isEnabled()&&session!=null&&session.mode()==GalateaPlanner.AxeMode.SCAFFOLD&&figScaffold!=null&&figScaffold.sourceParentEntryHold(now);}
    public FigHarvestPickupLedger.Scope figNativeHarvestScope(){
        if(kind!=GalateaPlanner.Kind.FIG||!isEnabled()||session==null||owner==null)return null;
        return new FigHarvestPickupLedger.Scope(this,owner,ownerLevel,figOwnerConnection,figHarvestActivationGeneration,session,figHarvestTreeGeneration);
    }
    public McFigHarvestPickupBridge.Binding figHarvestPickupBinding(){
        var scope=figNativeHarvestScope();if(scope==null||!figNativeHarvestCustody(scope))return null;
        return new McFigHarvestPickupBridge.Binding(scope,figHarvestLedger,Set.copyOf(session.tree().logs()),figHarvestDeadline,services.breaking(),()->figNativeHarvestCustody(scope));
    }
    /** Inert native observer binding; actual attempt values are captured by the native action, never review. */
    public McFigToolWearBridge.Binding figToolWearBinding(){
        var scope=figNativeHarvestScope();
        if(scope==null||figScaffold==null||figScaffold.toolWearAttemptGeneration()<0||figScaffold.toolWearAttemptDeadline()<0
                ||!figNativeHarvestCustody(scope))return null;
        return new McFigToolWearBridge.Binding(scope,figToolWearLedger,figScaffold.toolWearAttemptDeadline(),services.breaking(),
                ()->axeSlot,this::scaffoldSettings,figScaffold::toolWearAttemptGeneration,()->figNativeHarvestCustody(scope));
    }
    public boolean figNativeHarvestCustody(FigHarvestPickupLedger.Scope expected){
        var current=figNativeHarvestScope();if(current==null||!current.same(expected)||!client.isSameThread()||client.player!=owner||client.level!=ownerLevel||client.getConnection()!=figOwnerConnection||!owner.isAlive()
                ||client.gui.screen()!=null||owner.containerMenu!=owner.inventoryMenu||!owner.inventoryMenu.getCarried().isEmpty()
                ||dev.veltraph.client.service.mc.McOwnedRotation.authority(client)!=this||!figRootScaffoldPermission.getAsBoolean()||!Objects.equals(figHarvestSettings,scaffoldSettings()))return false;
        var equipment=List.of(net.minecraft.world.entity.EquipmentSlot.HEAD,net.minecraft.world.entity.EquipmentSlot.CHEST,net.minecraft.world.entity.EquipmentSlot.LEGS,net.minecraft.world.entity.EquipmentSlot.FEET,net.minecraft.world.entity.EquipmentSlot.OFFHAND);
        if(figHarvestGear.size()!=equipment.size())return false;
        for(int i=0;i<equipment.size();i++)if(!figHarvestGear.get(i).matches(owner.getItemBySlot(equipment.get(i)),ownerLevel.registryAccess()))return false;
        if(figScaffold!=null&&figScaffold.running())return figScaffold.nativeHarvestCustody();
        return services.inventory().selectedSlot()==axeSlot&&findAxe()==axeSlot;
    }
    private void beginFigHarvestScope(){
        if(kind!=GalateaPlanner.Kind.FIG)return;
        figToolWearLedger.clear();
        figHarvestTreeGeneration++;figHarvestDeadline=lastProgress+timeout()*4;figHarvestSettings=scaffoldSettings();
        var equipment=List.of(net.minecraft.world.entity.EquipmentSlot.HEAD,net.minecraft.world.entity.EquipmentSlot.CHEST,net.minecraft.world.entity.EquipmentSlot.LEGS,net.minecraft.world.entity.EquipmentSlot.FEET,net.minecraft.world.entity.EquipmentSlot.OFFHAND);
        figHarvestGear=equipment.stream().map(slot->dev.veltraph.client.inventory.NativeStackIdentity.capture(owner.getItemBySlot(slot),ownerLevel.registryAccess())).toList();
        figHarvestLedger.bind(figNativeHarvestScope(),Set.copyOf(session.tree().logs()),figHarvestDeadline);
    }
    private String scaffoldSettings(){var values=new TreeMap<String,String>();for(var key:PersistableKeys.collect(List.of(GalateaConfig.class,ForagingConfig.class,FigSourceSelectionConfig.class,FigRecoveryConfig.class,TravelConfig.class,RotationConfig.class)))values.put(key.name(),String.valueOf(config.get(key)));return new com.google.gson.Gson().toJson(values);}
    private FigObservedScaffoldMetadata.Observation scaffoldMetadata(){return config.get(GalateaConfig.FIG_USE_SCAFFOLDING)?McFigScaffoldMetadata.observe(client,config.get(FigSourceSelectionConfig.FIG_OBSERVED_SCAFFOLD_BINDING)):null;}
    @Override public boolean requiresSkyBlock() { return true; }
    @Override public Optional<GameArea> requiredArea() { return Optional.of(GameArea.GALATEA); }
    public static Set<String> parseIds(String ids) {
        if(ids==null) return Set.of();Set<String> result=new HashSet<>();
        for(String token:ids.split(",")) if(token.strip().matches("[a-z0-9_.-]+:[a-z0-9_/.-]+")) result.add(token.strip());
        return Set.copyOf(result);
    }
    private String ids() { return config.get(switch(kind) {
        case FIG -> GalateaConfig.FIG_LOG_IDS;case MANGROVE -> GalateaConfig.MANGROVE_LOG_IDS;case MATERIALS -> GalateaConfig.GALATEA_MATERIAL_IDS;
    }); }
    private Optional<GalateaPlanner.Bounds> preferred() {
        if(kind==GalateaPlanner.Kind.MATERIALS) return GalateaPlanner.Bounds.parse(config.get(GalateaConfig.GALATEA_MATERIAL_REGION_BOUNDS));
        boolean enabled=kind==GalateaPlanner.Kind.MANGROVE?config.get(GalateaConfig.MANGROVE_RESTRICT_REGION):config.get(GalateaConfig.FIG_RESTRICT_REGION);
        return enabled?GalateaPlanner.Bounds.parse(config.get(kind==GalateaPlanner.Kind.MANGROVE?GalateaConfig.MANGROVE_REGION_BOUNDS:GalateaConfig.FIG_REGION_BOUNDS)):Optional.empty();
    }
    @Override public Optional<String> preStartCheck() {
        if(client.player==null||client.level==null) return Optional.of("Load the Galatea world first.");
        if(parseIds(ids()).isEmpty()) return Optional.of("Configure Galatea target block IDs first.");
        boolean restricted=kind==GalateaPlanner.Kind.MATERIALS?!config.get(GalateaConfig.GALATEA_MATERIAL_REGION_BOUNDS).isBlank():kind==GalateaPlanner.Kind.MANGROVE?config.get(GalateaConfig.MANGROVE_RESTRICT_REGION):config.get(GalateaConfig.FIG_RESTRICT_REGION);
        if(restricted&&preferred().isEmpty()) return Optional.of("Preferred region requires six ordered min/max XYZ bounds.");
        if(kind!=GalateaPlanner.Kind.MATERIALS&&findAxe()<0) return Optional.of("Put a foraging axe in the hotbar.");
        if(kind!=GalateaPlanner.Kind.MATERIALS&&(config.get(ForagingConfig.FORAGING_AXE_MODE)==ForagingConfig.AxeMode.THROWING
                ||kind==GalateaPlanner.Kind.MANGROVE&&!config.get(GalateaConfig.MANGROVE_MOVE_AROUND))
                &&!McForagingAbilitySource.hasThrowingAbility(client,findAxe()))
            return Optional.of("This profile requires an axe with observed Throwing Axe ability lore.");
        if(kind==GalateaPlanner.Kind.MATERIALS&&findMaterialTool()<0)
            return Optional.of("Keep an inert item or empty slot in hotbar slots 1-8 for bush interaction.");
        if(kind!=GalateaPlanner.Kind.MATERIALS&&config.get(GalateaConfig.GALATEA_REQUIRE_SWEEP)&&!(McForagingAbilitySource.profile(client,config,findAxe()).sweep()>0))
            return Optional.of("The Galatea profile requires an observed positive Sweep stat in the tab list or axe lore.");
        if(kind==GalateaPlanner.Kind.MANGROVE&&!navigation.hasEtherwarpTool()) return Optional.of("Mangrove tree access requires an item with Ether Transmission lore in the hotbar.");
        if(kind==GalateaPlanner.Kind.FIG&&config.get(GalateaConfig.FIG_USE_SCAFFOLDING)&&findScaffold()<0)
            return Optional.of("Put the bound scaffold ability item in your hotbar and check its saved item binding.");
        if(kind==GalateaPlanner.Kind.FIG) {
            try {
                if(!config.get(FigSourceSelectionConfig.FIG_SOURCE_REGION_BINDING).isBlank())FigSourceRegionBinding.parse(config.get(FigSourceSelectionConfig.FIG_SOURCE_REGION_BINDING)).requireCurrent(figRegionLines());
                FigObservedDescriptors.parse(config.get(FigSourceSelectionConfig.FIG_OBSERVED_TREE_DESCRIPTORS));
                scaffoldMetadata();
                if(!(client.player instanceof dev.veltraph.client.mixin.FigJumpPowerAccessor))return Optional.of("Native Fig jump-power observer unavailable.");
            } catch(RuntimeException missing) {return Optional.of("Fig source observations: "+missing.getMessage());}
        }
        return Optional.empty();
    }
    @Override public void onEnable() {
        if(figScaffold!=null)figScaffold.onActivation();figDescriptorToken=null;
        if(kind==GalateaPlanner.Kind.FIG){figHarvestActivationGeneration++;figHarvestLedger.clear();figToolWearLedger.clear();figHarvestSettings=null;figHarvestGear=List.of();}
        if(kind==GalateaPlanner.Kind.FIG&&(figDescriptorRegistryPlayer.get()!=client.player||figDescriptorRegistryWorld.get()!=client.level||figDescriptorRegistryConnection.get()!=client.getConnection())){
            // A different actual native descriptor lifetime cannot reuse the old local observation identities.
            figDescriptorCooldown.reset();figDescriptorTokens.clear();
            figDescriptorRegistryPlayer=new java.lang.ref.WeakReference<>(client.player);figDescriptorRegistryWorld=new java.lang.ref.WeakReference<>(client.level);figDescriptorRegistryConnection=new java.lang.ref.WeakReference<>(client.getConnection());
        }
        fallRecovery.reset();jumpAimPoints.clear();lastJumpAimMode=null;figJumpLatch.release();figDisplacement.clear();
        jumpCounter.enable(config.get(GalateaConfig.GALATEA_FORAGING_SPEED),java.util.concurrent.ThreadLocalRandom.current().nextLong(-10,10));ownedJumpHeld=false;
        rest.clear();blockedRestHit=null;
        owner=client.player;ownerLevel=client.level;travelConnection=client.getConnection();figOwnerConnection=kind==GalateaPlanner.Kind.FIG?client.getConnection():null;
        if(kind==GalateaPlanner.Kind.FIG)figDisplacement.bind(owner,ownerLevel,figOwnerConnection);
        previousSlot=services.inventory().selectedSlot();lastSelectedSlot=-1;axeSlot=findAxe();materialSlot=findMaterialTool();targetIds=parseIds(ids());
        session=null;previous=null;target=null;blacklist.clear();trees=resources=removed=placedScaffolds=largestBatch=multiBatches=activations=collectedLogs=0;nextScan=nextUse=nextAxe=0;harvestCycle.reset();previousDrops.clear();
        figSourceSelector=null;figSourceRegion=null;figObservedDescriptors=null;figObservedScaffoldHeight=0;figPreviousTarget=new BlockPos(0,0,0);
        if(kind==GalateaPlanner.Kind.FIG) {
            if(!config.get(FigSourceSelectionConfig.FIG_SOURCE_REGION_BINDING).isBlank()) {
                figSourceRegion=FigSourceRegionBinding.parse(config.get(FigSourceSelectionConfig.FIG_SOURCE_REGION_BINDING));
                figSourceRegion.requireCurrent(figRegionLines());
            }
            figObservedDescriptors=FigObservedDescriptors.parse(config.get(FigSourceSelectionConfig.FIG_OBSERVED_TREE_DESCRIPTORS));
            figScaffoldMetadata=scaffoldMetadata();figObservedScaffoldHeight=figScaffoldMetadata==null?0:figScaffoldMetadata.values().geometryHeight();
        }
        if(kind==GalateaPlanner.Kind.FIG)services.breaking().installTargetGuard(this,p->figNativeHarvestScope()!=null&&session.tree().logs().contains(p)&&resource(p));
        nextObservation=0;phase="SEARCH";status="Scanning loaded targets";
    }
    @Override public void onDisable() {
        if(figScaffold!=null)figScaffold.cancel();figDescriptorToken=null;
        if(kind==GalateaPlanner.Kind.FIG){services.breaking().clearTargetGuard(this);figHarvestLedger.clear();figToolWearLedger.clear();figHarvestSettings=null;figHarvestGear=List.of();}
        fallRecovery.cancel();jumpAimPoints.clear();lastJumpAimMode=null;
        rest.clear();blockedRestHit=null;
        int selected=services.inventory().selectedSlot();release();
        if(client.player==owner&&client.level==ownerLevel&&previousSlot>=0&&selected==lastSelectedSlot) services.inventory().selectSlot(previousSlot);
        owner=null;ownerLevel=null;travelConnection=null;figOwnerConnection=null;session=null;target=null;figSourceSelector=null;figSourceRegion=null;figObservedDescriptors=null;phase="STOPPED";
    }
    private List<String> figRegionLines() {
        var context=new dev.veltraph.client.game.mc.McGameContextService(client);context.resolution();
        var signals=context.signals();var lines=new ArrayList<String>();
        lines.add(signals.sidebarTitleRaw());lines.addAll(signals.sidebarRowsRaw());lines.add(signals.tabFooterRaw());lines.addAll(signals.tabLinesRaw());
        return lines.stream().filter(Objects::nonNull).map(dev.veltraph.client.gui.MenuSnapshot::clean).toList();
    }
    private McFigSourceWorld.RayContext figRayContext() {
        var region=figSourceRegion==null?FigSourceRegionBinding.observed(figRegionLines()):figSourceRegion.requireCurrent(figRegionLines());
        return new McFigSourceWorld.RayContext(region==FigSourceRegionBinding.Region.MOONGLADE_MARSH,false,region!=FigSourceRegionBinding.Region.UNBOUND);
    }
    private boolean figObservationsCurrent() {
        if(kind!=GalateaPlanner.Kind.FIG)return true;
        try {
            figRayContext();
            return true;
        } catch(RuntimeException drift){status="Fig source observations: "+drift.getMessage();setEnabled(false);return false;}
    }
    private void release() {
        if(figScaffold!=null)figScaffold.cancel();
        if(kind==GalateaPlanner.Kind.FIG)figToolWearLedger.clear();
        services.breaking().setBreaking(false);releaseMovement();interactionRotation.release(navigation);
    }
    private void setJumpHeld(boolean held) { services.input().setJumpHeld(held);ownedJumpHeld=held;if(!held)figJumpLatch.release(); }
    private void releaseMovement() { services.input().releaseAllMovement();ownedJumpHeld=false;figJumpLatch.release();figDisplacement.clear(); }
    private void selectSlot(int slot) { if(services.inventory().selectedSlot()!=slot) services.inventory().selectSlot(slot);lastSelectedSlot=slot; }
    private static net.minecraft.core.BlockPos mc(BlockPos p) { return new net.minecraft.core.BlockPos(p.x(),p.y(),p.z()); }
    private static BlockPos local(net.minecraft.core.BlockPos p) { return new BlockPos(p.getX(),p.getY(),p.getZ()); }
    private boolean loaded(BlockPos p) { return client.level!=null&&client.level.getChunkSource().hasChunk(p.x()>>4,p.z()>>4); }
    private boolean resource(BlockPos p) { return loaded(p)&&targetIds.contains(BuiltInRegistries.BLOCK.getKey(client.level.getBlockState(mc(p)).getBlock()).toString()); }
    private double distance(BlockPos p) { return client.player.getEyePosition().distanceTo(net.minecraft.world.phys.Vec3.atCenterOf(mc(p))); }
    private boolean aligned(BlockPos p) { return client.hitResult!=blockedRestHit&&client.hitResult instanceof BlockHitResult h&&h.getType()==HitResult.Type.BLOCK&&h.getBlockPos().equals(mc(p)); }
    private long timeout() { return Math.max(1500,Math.min(30000,config.get(GalateaConfig.GALATEA_ACTION_TIMEOUT_MS))); }
    private double throwRange() {
        double range=config.get(GalateaConfig.GALATEA_AXE_THROW_RANGE);
        return Double.isFinite(range)?Math.max(4.5,Math.min(32,range)):16;
    }
    private int findAxe() {
        var names=services.inventory().hotbarItemNames();
        for(int i=0;i<names.size();i++) { String n=names.get(i).toLowerCase(Locale.ROOT);if((n.contains("axe")&&!n.contains("pickaxe"))||n.contains("treecapitator")) return i; }
        return -1;
    }
    private int findScaffold() {
        if(kind==GalateaPlanner.Kind.FIG){try{var observed=scaffoldMetadata();return observed==null?-1:observed.hotbarSlot();}catch(RuntimeException unavailable){return -1;}}
        if(client.player==null) return -1;
        for(int i=0;i<9;i++) if(client.player.getInventory().getItem(i).is(Items.SCAFFOLDING)) return i;
        return -1;
    }
    private int findMaterialTool() {
        if(client.player==null) return -1;
        List<LushlilacToolPolicy.Tool> tools=new ArrayList<>();
        for(int i=0;i<8;i++) {
            var stack=client.player.getInventory().getItem(i);var lore=stack.get(DataComponents.LORE);
            var lines=lore==null?List.<String>of():lore.lines().stream().map(l->l.getString()).toList();
            tools.add(new LushlilacToolPolicy.Tool(i,stack.isEmpty(),LushlilacToolPolicy.rightClickAbility(lines),
                    stack.getItem() instanceof net.minecraft.world.item.BlockItem,stack.has(DataComponents.CONSUMABLE)||stack.has(DataComponents.FOOD)||LushlilacToolPolicy.consumableLore(lines)));
        }
        return LushlilacToolPolicy.choose(tools,config.get(GalateaConfig.LUSHLILAC_CLICK_TYPE));
    }
    private boolean throwAbility() {
        return McForagingAbilitySource.hasThrowingAbility(client,axeSlot);
    }
    private long responseWindow() { return Math.max(100,Math.min(5000,config.get(ForagingConfig.FORAGING_ABILITY_RESULT_WINDOW_MS))); }
    private ForagingAxePolicy.Profile axeProfile() {
        return McForagingAbilitySource.profile(client,config,axeSlot,session!=null&&session.mode()==GalateaPlanner.AxeMode.THROWING?ForagingConfig.AxeMode.THROWING:null);
    }
    private int maturity(BlockPos p) {
        if(!loaded(p)) return -1;
        var state=client.level.getBlockState(mc(p));
        return state.hasProperty(net.minecraft.world.level.block.SweetBerryBushBlock.AGE)?state.getValue(net.minecraft.world.level.block.SweetBerryBushBlock.AGE):-1;
    }
    private void aim(BlockPos p,boolean higher) {
        var eye=client.player.getEyePosition();var point=net.minecraft.world.phys.Vec3.atCenterOf(mc(p)).add(0,higher?.2:0,0);var d=point.subtract(eye);
        interactionRotation.track((float)Math.toDegrees(Math.atan2(-d.x,d.z)),(float)-Math.toDegrees(Math.atan2(d.y,Math.hypot(d.x,d.z))));
    }
    /** Productive source endpoint uses the counter observed before this jumping handler changes inputs. */
    private void aimJump(BlockPos p,long now) {
        var eye=client.player.getEyePosition();var pos=mc(p);var shape=client.level.getBlockState(pos).getShape(client.level,pos);
        var box=shape.isEmpty()?new net.minecraft.world.phys.AABB(pos):shape.bounds().move(pos);
        var player=new Vec3(client.player.getX(),client.player.getY(),client.player.getZ());
        var base=jumpAimPoints.point(p,player,now,()->{
            var random=java.util.concurrent.ThreadLocalRandom.current();double horizontal=Math.max(.15,box.maxX-box.minX-.4)/2,vertical=Math.max(.15,box.maxY-box.minY-.4)/2;
            var sampled=new Vec3((box.minX+box.maxX)/2+random.nextDouble(-horizontal,horizontal),
                    (box.minY+box.maxY)/2+random.nextDouble(-vertical,vertical),
                    (box.minZ+box.maxZ)/2+random.nextDouble(-horizontal,horizontal));
            return FigAimPointResolver.resolve(p,new FigAimPointResolver.Bounds(box.minX,box.minY,box.minZ,box.maxX,box.maxY,box.maxZ),sampled,point->{
                var hit=client.level.clip(new net.minecraft.world.level.ClipContext(eye,new net.minecraft.world.phys.Vec3(point.x(),point.y(),point.z()),
                        net.minecraft.world.level.ClipContext.Block.OUTLINE,net.minecraft.world.level.ClipContext.Fluid.NONE,net.minecraft.world.phys.shapes.CollisionContext.empty()));
                return hit.getType()==HitResult.Type.BLOCK&&hit.getBlockPos().equals(pos);
            });
        });
        var center=net.minecraft.world.phys.Vec3.atCenterOf(pos);
        var supplied=jumpAngle(base.x()-eye.x,base.y()-eye.y,base.z()-eye.z);
        var targetAngle=jumpAngle(center.x-eye.x,center.y-eye.y,center.z-eye.z);
        var configured=config.get(GalateaConfig.FIG_JUMP_BOOST_AIMING);lastJumpAimMode=FigJumpAimPolicy.effective(configured,jumpCounter.counter());
        var endpoint=FigJumpAimPolicy.endpoint(configured,jumpCounter.counter(),supplied,targetAngle,FigJumpAimPolicy.processWeight());
        interactionRotation.track((float)endpoint.yaw(),(float)endpoint.pitch());
    }
    private static FigJumpAimPolicy.Angle jumpAngle(double x,double y,double z) {
        return new FigJumpAimPolicy.Angle(Math.toDegrees(Math.atan2(-x,z)),-Math.toDegrees(Math.atan2(y,Math.hypot(x,z))));
    }
    public Optional<FigJumpAimPolicy.Mode> jumpAimMode(){return Optional.ofNullable(lastJumpAimMode);}
    public int jumpAimCounter(){return jumpCounter.counter();}
    public Optional<FigSelectionReceipt> candidateSelectionReceipt(){return figSourceSelector==null?Optional.empty():figSourceSelector.lastReceipt();}
    public List<FigSelectionReceipt> candidateSelectionHistory(){return figSourceSelector==null?List.of():figSourceSelector.recentReceipts();}
    public OptionalInt candidateObservedJumpMaximum(){return figSourceSelector==null?OptionalInt.empty():OptionalInt.of(figSourceSelector.descriptorJumpMaximum());}
    public boolean jumpTargetComplete(){return jumpCounter.helperComplete();}
    public long jumpTargetDelayMs(){return jumpCounter.delayMs();}
    public Optional<FigEndDisplacementCache.Sample> jumpDisplacementSample(){return Optional.ofNullable(figDisplacement.sample());}
    private void finish(long now,boolean success,String why) {finish(now,success,why,false);}
    private void finish(long now,boolean success,String why,boolean descriptorCooldownOnly) {
        release();status=why;
        if(session!=null) {
            collectedLogs+=Math.max(0,inventoryLogs()-inventoryLogsBefore);
            removed+=session.removed();previous=session.tree();if(success&&session.complete()) trees++;
            // Cool down the whole geometry, including surviving inaccessible branches.
            if(!descriptorCooldownOnly)for(BlockPos p:session.tree().logs()) blacklist.put(p,now);
        } else if(target!=null&&!descriptorCooldownOnly) blacklist.put(target,now);
        if(kind==GalateaPlanner.Kind.FIG){figHarvestLedger.clear();figToolWearLedger.clear();}
        session=null;target=null;figSourceSelector=null;figDescriptorToken=null;scaffoldBase=null;scaffoldSupport=null;phase="SEARCH";nextScan=now+500;missingSince=0;
    }
    private boolean occupied(GalateaPlanner.Tree tree) {
        if(!config.get(GalateaConfig.FORAGING_ABANDON_TREES_WITH_OTHERS)) return false;
        var box=new net.minecraft.world.phys.AABB(mc(tree.root())).inflate(4,tree.height(),4);
        return !client.level.getEntitiesOfClass(net.minecraft.world.entity.player.Player.class,box,p->p!=client.player&&p.isAlive()).isEmpty();
    }
    @Override public void onTick() {
        if(rest.paused())return;
        if(client.player!=owner||client.level!=ownerLevel||owner==null||client.gui.screen()!=null||!owner.isAlive()) { setEnabled(false);return; }
        if(!figObservationsCurrent())return;
        long now=services.clock().nowMillis();
        if(kind==GalateaPlanner.Kind.FIG) {
            if(figOwnerConnection==null||client.getConnection()!=figOwnerConnection) { release();setEnabled(false);return; }
            // A local release clears the cached sample; rebind only the same frozen native scope.
            if(figDisplacement.sample()==null)figDisplacement.bind(owner,ownerLevel,figOwnerConnection);
            if(!figDisplacement.refresh(owner,ownerLevel,figOwnerConnection,
                    owner.getX()-owner.xo,owner.getY()-owner.yo,owner.getZ()-owner.zo)) {release();setEnabled(false);return;}
        }
        if(phase.equals("FIG_FALL_RECOVERY")) {
            var recovery=fallRecovery.tick();status=fallRecovery.status();
            if(recovery==dev.veltraph.client.galatea.recovery.FigFallRecoveryCycle.State.RECOVERED) {
                // Rebuild loaded tree geometry after the source success callback. Preserve the
                // recovery expected-teleport token until the shared observer consumes it.
                finish(now,false,"Source Fig fall recovery reached its native goal");
            } else if(recovery!=dev.veltraph.client.galatea.recovery.FigFallRecoveryCycle.State.TRAVELLING) {
                setEnabled(false);
            }
            return;
        }
        if(kind==GalateaPlanner.Kind.FIG) {
            boolean zero=target==null||target.equals(new BlockPos(0,0,0));
            boolean passable=!zero&&loaded(target)&&targetPassability.passable(client.level.getBlockState(mc(target)));
            jumpCounter.observeTarget(now,zero,passable,session!=null&&session.excluded(target),services.rotation().sourceBusy());
        }
        if(kind==GalateaPlanner.Kind.FIG)figDescriptorCooldown.expire(now);
        blacklist.entrySet().removeIf(e->now-e.getValue()>Math.max(1000,config.get(GalateaConfig.GALATEA_BLACKLIST_MS)));
        if(phase.equals("SEARCH")) { if(now>=nextScan) scan(now);return; }
        if(target!=null&&!loaded(target)) { finish(now,false,"Target unloaded");return; }
        if(session!=null) {
            if(occupied(session.tree())) { finish(now,false,"Another player near this tree");return; }
            // Unloading a branch must never be interpreted as tree removal.
            if(session.tree().logs().stream().anyMatch(p->!loaded(p))) { finish(now,false,"Tree geometry unloaded");return; }
            var ability=axeProfile();
            int before=session.removed();
            if(ability.multiLog()||harvestCycle.active()||now>=nextObservation) {
                session.observe(this::resource);
                nextObservation=now+1000/Math.max(1,Math.min(7,config.get(GalateaConfig.GALATEA_FORAGING_SPEED)));
            }
            if(session.removed()>before) { lastProgress=now;nextAxe=Math.max(nextAxe,now+ability.cooldownMs());services.breaking().setBreaking(false); }
            int batchesBefore=harvestCycle.multiLogBatches();harvestCycle.observe(session.removed(),ability.multiLog(),now,responseWindow());
            multiBatches+=harvestCycle.multiLogBatches()-batchesBefore;largestBatch=Math.max(largestBatch,harvestCycle.largestBatch());
            if(phase.equals("PICKUP")) {
                if(!session.complete()) { navigation.stop();phase="CUT";missingSince=0;lastProgress=now;return; }
                pickup(now);return;
            }
            if(session.complete()) {
                services.breaking().setBreaking(false);
                if(missingSince==0) missingSince=now;
                if(now-missingSince>=Math.max(500,ability.multiLog()?responseWindow():0)) {
                    session.observe(this::resource);
                    if(session.complete()) {
                        releaseMovement();interactionRotation.release(navigation);
                        phase="PICKUP";pickupStarted=pickupQuietSince=now;target=null;
                    } else missingSince=0;
                }
                return;
            }
            missingSince=0;
            if(now-lastProgress>timeout()*4) { finish(now,false,"Tree made no progress");return; }
            if(!phase.equals("RECOVER")&&client.player.getY()<session.tree().root().y()-4) {
                if(beginSourceFallRecovery())return;
                release();if(approach(session.tree().root())) { phase="RECOVER";actionStarted=now;status="Recovering to tree base"; }
                else finish(now,false,"No ground recovery route");return;
            }
        }
        if(phase.equals("WALK")||phase.equals("WARP")||phase.equals("RECOVER")||phase.equals("SCAFFOLD_WALK")) {
            var result=navigation.tick();
            if(result==McTravelNavigator.State.FAILED||now-actionStarted>timeout()*3) { if(beginSourceFallRecovery())return;finish(now,false,"Travel failed");return; }
            if(result==McTravelNavigator.State.ARRIVED) {
                // Etherwarp already releases its controls on arrival. Preserve its expected
                // teleport token until the shared failsafe observer consumes this position.
                if(!phase.equals("WARP")) navigation.stop();actionStarted=now;
                phase=kind==GalateaPlanner.Kind.MATERIALS?"COLLECT":phase.equals("SCAFFOLD_WALK")?"CLIMB":"CUT";
                if(axeSlot>=0&&kind!=GalateaPlanner.Kind.MATERIALS) selectSlot(axeSlot);
            }
            return;
        }
        if(kind==GalateaPlanner.Kind.FIG&&phase.equals("FIG_SOURCE_SCAFFOLD")){figScaffold.tick(now);return;}
        if(phase.equals("COLLECT")) { collect(now);return; }
        if(phase.equals("PLACE_SCAFFOLD")||phase.equals("WAIT_SCAFFOLD")||phase.equals("CLIMB")) { scaffold(now);return; }
        cut(now);
    }
    private void scan(long now) {
        nextScan=now+1000;int radius=Math.max(4,Math.min(24,config.get(GalateaConfig.GALATEA_SCAN_RADIUS)));
        BlockPos origin=local(client.player.blockPosition());List<GalateaPlanner.Tree> trees=new ArrayList<>();Set<BlockPos> scanned=new HashSet<>();int components=0;
        scan: for(int x=-radius;x<=radius;x++) for(int z=-radius;z<=radius;z++) for(int y=-12;y<=20;y++) {
            BlockPos p=origin.add(x,y,z);if(scanned.contains(p)||blacklist.containsKey(p)||!resource(p)) continue;
            if(kind==GalateaPlanner.Kind.MATERIALS) {
                int age=maturity(p);
                if((age<0||age>=2)&&preferred().map(b->b.contains(p)).orElse(true)) trees.add(new GalateaPlanner.Tree(p,List.of(p),0,0,false));
            }
            else {
                // A rejected giant/truncated component can be reached from many seeds. Bound
                // component work independently of the coordinate scan and per-tree log cap.
                if(++components>64) break scan;
                var tree=GalateaPlanner.component(p,this::resource,q->Math.abs(q.x()-origin.x())<=radius&&Math.abs(q.z()-origin.z())<=radius&&q.y()>=origin.y()-12&&q.y()<=origin.y()+28&&loaded(q),256);
                if(tree.isPresent()) {
                    scanned.addAll(tree.get().logs());
                    boolean truncated=tree.get().logs().stream().anyMatch(q->Math.abs(q.x()-origin.x())>=radius||Math.abs(q.z()-origin.z())>=radius||q.y()<=origin.y()-12||q.y()>=origin.y()+28
                            ||!loaded(q.add(1,0,0))||!loaded(q.add(-1,0,0))||!loaded(q.add(0,0,1))||!loaded(q.add(0,0,-1)));
                    if(!truncated&&!occupied(tree.get())&&tree.get().logs().stream().noneMatch(blacklist::containsKey)) trees.add(tree.get());
                }
            }
        }
        var position=new Vec3(client.player.getX(),client.player.getY(),client.player.getZ());var preferred=preferred().orElse(null);
        boolean preferSmall=config.get(GalateaConfig.FIG_PREFER_SMALL_TREES);
        // Exclude source-disallowed Fig candidates before ranking, session creation or travel.
        // A penalty is insufficient: the closest large tree must never become the only target.
        trees.removeIf(t->!GalateaPlanner.eligible(kind,t,preferSmall));
        trees.sort(Comparator.comparingDouble(t->GalateaPlanner.score(kind,t,position,client.player.getYRot(),previous,preferred,preferSmall)));
        for(var tree:trees.stream().limit(4).toList()) {
            if(kind==GalateaPlanner.Kind.MATERIALS) {
                target=tree.root();materialCycle.begin(maturity(target),rewardCount());actionStarted=now;missingSince=0;
                if(distance(target)<=4.4) phase="COLLECT";
                else if(approach(target)) phase="WALK";else { blacklist.put(target,now);target=null;continue; }
            } else {
                session=new GalateaSession(tree);target=null;actionStarted=lastProgress=now;placedScaffolds=0;attemptedApproaches.clear();harvestCycle.reset();nextAxe=0;
                if(kind==GalateaPlanner.Kind.FIG)beginFigHarvestScope();
                if(kind==GalateaPlanner.Kind.FIG) {
                    try {
                        var descriptor=figObservedDescriptors.find(tree).map(observed->new McFigSourceSelector.Descriptor(observed.base(),tree.logs(),observed.provenance()))
                                .orElseGet(()->McFigSourceSelector.Descriptor.observedLoadedComponent(tree));
                        figDescriptorToken=figDescriptorTokens.computeIfAbsent(new DescriptorIdentity(descriptor.base(),descriptor.blocks(),descriptor.provenance()),key->new Object());
                        if(figDescriptorCooldown.contains(figDescriptorToken)){session=null;figDescriptorToken=null;continue;}
                        figSourceSelector=new McFigSourceSelector(client,targetPassability,descriptor,figObservedScaffoldHeight,this::figRayContext);
                    } catch(RuntimeException unknown){finish(now,false,"Fig descriptor unavailable: "+unknown.getMessage());return;}
                }
                inventoryLogsBefore=inventoryLogs();previousDrops.clear();matchingDrops().forEach(drop->previousDrops.add(drop.getUUID()));
                if(kind==GalateaPlanner.Kind.MANGROVE) {
                    if(!warpOntoTree(tree)) { blacklist.put(tree.root(),now);session=null;continue; }phase="WARP";
                    if(!config.get(GalateaConfig.MANGROVE_MOVE_AROUND)) session.setMode(GalateaPlanner.AxeMode.THROWING);
                } else if(session.choose(p->distance(p)<=4.4,Comparator.comparingDouble(this::distance)).isPresent()) phase="CUT";
                else if(approach(tree.root())) phase="WALK";
                else if(config.get(GalateaConfig.GALATEA_ETHERWARP_USAGE)!=GalateaPlanner.EtherwarpUsage.NONE&&navigation.hasEtherwarpTool()&&warpOntoTree(tree)) phase="WARP";
                else { blacklist.put(tree.root(),now);session=null;continue; }
            }
            status="Selected loaded "+kind.name().toLowerCase(Locale.ROOT)+" target";return;
        }
        status="No reachable loaded targets";
    }
    private boolean approach(BlockPos root) {
        List<BlockPos> spots=new ArrayList<>();
        for(int[] d:new int[][]{{1,0},{-1,0},{0,1},{0,-1}}) for(int y:new int[]{0,-1,1}) spots.add(root.add(d[0],y,d[1]));
        spots.sort(Comparator.comparingDouble(this::distance));
        for(BlockPos p:spots) if(startApproach(p)) return true;return false;
    }
    private boolean startApproach(BlockPos p) {
        if(!navigation.startApproach(p,dev.veltraph.client.navigation.SharedMovementPolicy.Purpose.GENERAL_GROUND_TRAVEL)) return false;
        // Prime ordinary walk/takeoff controls before the next vanilla physics tick.
        if(navigation.tick()==McTravelNavigator.State.FAILED) { navigation.stop();return false; }
        return true;
    }
    private boolean logReward(net.minecraft.world.item.ItemStack stack) {
        if(stack.isEmpty()) return false;
        if(targetIds.contains(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString())) return true;
        String name=stack.getHoverName().getString().replaceAll("§.","").toLowerCase(Locale.ROOT);
        return Arrays.stream(config.get(GalateaConfig.GALATEA_LOG_REWARD_NAMES).toLowerCase(Locale.ROOT).split(","))
                .map(String::strip).filter(s->!s.isEmpty()).anyMatch(name::contains);
    }
    private int inventoryLogs() {
        if(client.player==null) return 0;int count=0;
        for(int i=0;i<client.player.getInventory().getContainerSize();i++) {
            var stack=client.player.getInventory().getItem(i);if(logReward(stack)) count+=stack.getCount();
        }
        return count;
    }
    private List<ItemEntity> matchingDrops() {
        if(session==null||client.level==null) return List.of();
        var logs=session.tree().logs();int minX=Integer.MAX_VALUE,minY=Integer.MAX_VALUE,minZ=Integer.MAX_VALUE,maxX=Integer.MIN_VALUE,maxY=Integer.MIN_VALUE,maxZ=Integer.MIN_VALUE;
        for(BlockPos p:logs) { minX=Math.min(minX,p.x());minY=Math.min(minY,p.y());minZ=Math.min(minZ,p.z());maxX=Math.max(maxX,p.x());maxY=Math.max(maxY,p.y());maxZ=Math.max(maxZ,p.z()); }
        var box=new net.minecraft.world.phys.AABB(minX-3,minY-3,minZ-3,maxX+4,maxY+4,maxZ+4);
        return client.level.getEntitiesOfClass(ItemEntity.class,box,drop->drop.isAlive()&&logReward(drop.getItem())&&!previousDrops.contains(drop.getUUID()));
    }
    private void pickup(long now) {
        services.breaking().setBreaking(false);var drops=matchingDrops();
        if(now-pickupStarted>Math.max(1000,Math.min(30000,config.get(ForagingConfig.FORAGING_COLLECTION_TIMEOUT_MS)))) {
            finish(now,true,"Tree removed; pickup timed out with "+drops.size()+" matching drops");return;
        }
        if(drops.isEmpty()) {
            navigation.stop();if(now-pickupQuietSince>=500) finish(now,true,"Whole tree removed; observed inventory gain recorded");return;
        }
        pickupQuietSince=now;
        var next=drops.stream().min(Comparator.comparingDouble(drop->drop.distanceToSqr(client.player))).orElseThrow();
        var state=navigation.tick();
        if(state!=McTravelNavigator.State.ACTIVE) startApproach(local(next.blockPosition()));
        status="Picking up observed tree drops | inventory gain="+Math.max(0,inventoryLogs()-inventoryLogsBefore);
    }
    private boolean warpOntoTree(GalateaPlanner.Tree tree) {
        for(BlockPos p:tree.logs().stream().sorted(Comparator.comparingInt(BlockPos::y).reversed()).toList()) {
            if(!loaded(p.up(2))||!client.level.getBlockState(mc(p.up())).isAir()||!client.level.getBlockState(mc(p.up(2))).isAir()) continue;
            var landing=new Vec3(p.x()+.5,p.y()+1,p.z()+.5);
            // A tree top is usually above the player, so its UP face cannot be seen from the
            // ground. Acquire an actually visible side face while retaining the top landing.
            for(Direction face:new Direction[]{Direction.UP,Direction.WEST,Direction.EAST,Direction.NORTH,Direction.SOUTH}) {
                var point=net.minecraft.world.phys.Vec3.atCenterOf(mc(p)).add(face.dx()*.499,face.dy()*.499,face.dz()*.499);
                var hit=client.level.clip(new net.minecraft.world.level.ClipContext(client.player.getEyePosition(),point,
                        net.minecraft.world.level.ClipContext.Block.COLLIDER,net.minecraft.world.level.ClipContext.Fluid.NONE,client.player));
                if(hit.getType()!=HitResult.Type.BLOCK||!hit.getBlockPos().equals(mc(p))||!hit.getDirection().name().equals(face.name())) continue;
                if(navigation.start(new LocalRoute.Node(LocalRoute.Kind.ETHERWARP,landing,0,0,p,face))) return true;
            }
        }
        return false;
    }
    private void nextMode(long now) {
        release();target=null;
        if(!session.advance(kind==GalateaPlanner.Kind.FIG&&config.get(GalateaConfig.FIG_JUMP_BOOST),kind==GalateaPlanner.Kind.FIG&&config.get(GalateaConfig.FIG_USE_SCAFFOLDING)&&findScaffold()>=0,kind==GalateaPlanner.Kind.FIG&&config.get(GalateaConfig.FIG_SKIP_AXE_THROWING))) {
            finish(now,false,"Remaining branches inaccessible or axe throwing skipped");return;
        }
        if(kind==GalateaPlanner.Kind.FIG&&session.mode()==GalateaPlanner.AxeMode.JUMPING)figSourceSelector.enterJump();

        if(kind==GalateaPlanner.Kind.FIG&&session.mode()==GalateaPlanner.AxeMode.SCAFFOLD){
            if(figScaffoldMetadata==null){status="Fig schema2 scaffold metadata unavailable";setEnabled(false);return;}
            phase="FIG_SOURCE_SCAFFOLD";actionStarted=now;
            figScaffold.begin(figSourceSelector,figScaffoldMetadata,config.get(GalateaConfig.FIG_AIM_HIGHER_AT_STEM),figPreviousTarget,now,lastProgress+timeout()*4);return;
        }
        if(session.mode()==GalateaPlanner.AxeMode.SCAFFOLD) {
            BlockPos feet=local(client.player.blockPosition());scaffoldBase=feet;scaffoldSupport=feet.down();
            if(!loaded(scaffoldSupport)||client.level.getBlockState(mc(scaffoldSupport)).getCollisionShape(client.level,mc(scaffoldSupport)).isEmpty()) { nextMode(now);return; }
            // Use an adjacent empty column so normal placement never intersects the player's body.
            for(int[] d:new int[][]{{1,0},{-1,0},{0,1},{0,-1}}) {
                BlockPos base=feet.add(d[0],0,d[1]);
                if(loaded(base)&&client.level.getBlockState(mc(base)).canBeReplaced()&&!client.level.getBlockState(mc(base.down())).getCollisionShape(client.level,mc(base.down())).isEmpty()) {
                    scaffoldBase=base;scaffoldSupport=base.down();phase="PLACE_SCAFFOLD";actionStarted=now;return;
                }
            }
            nextMode(now);return;
        }
        if(kind==GalateaPlanner.Kind.FIG&&(session.mode()==GalateaPlanner.AxeMode.JUMPING||session.mode()==GalateaPlanner.AxeMode.THROWING))jumpCounter.enterJumpOrThrow();
        actionStarted=now;phase="CUT";
    }
    private void cut(long now) {
        if(session==null) { finish(now,false,"Lost tree session");return; }
        if(kind==GalateaPlanner.Kind.FIG&&session.mode()==GalateaPlanner.AxeMode.JUMPING) {
            jumpCounter.observeJumpTick(client.player.onGround(),ownedJumpHeld&&client.options.keyJump.isDown());
            if(jumpCounter.counter()>1)session.exclude(target);
            var displacement=figDisplacement.sample();
            if(displacement!=null&&figJumpLatch.observe(displacement.x(),displacement.y(),displacement.z()))setJumpHeld(true);
        }
        if(axeSlot<0||findAxe()!=axeSlot) { setEnabled(false);return; }selectSlot(axeSlot);
        var ability=axeProfile();
        if(config.get(GalateaConfig.GALATEA_REQUIRE_SWEEP)&&!(ability.sweep()>0)) { status="Sweep stat became unavailable";setEnabled(false);return; }
        if(harvestCycle.waiting(now)||now<nextAxe||McForagingAbilitySource.cooldownActive(client)) {
            services.breaking().setBreaking(false);if(kind!=GalateaPlanner.Kind.FIG||session.mode()!=GalateaPlanner.AxeMode.JUMPING)setJumpHeld(false);
            status="Waiting for axe outcome/cooldown | remaining="+session.remaining();return;
        }
        if(target!=null&&kind==GalateaPlanner.Kind.FIG&&jumpCounter.helperComplete()) {
            target=null;services.breaking().setBreaking(false);actionStarted=now;
        } else if(target!=null&&!resource(target)) {
            services.breaking().setBreaking(false);
            if(kind==GalateaPlanner.Kind.FIG){status="Waiting for source target completion";return;}
            target=null;actionStarted=now;
        }
        if(kind==GalateaPlanner.Kind.FIG&&target!=null&&targetPassability.passable(client.level.getBlockState(mc(target)))&&!jumpCounter.helperComplete()) {
            services.breaking().setBreaking(false);status="Waiting for source target completion";return;
        }
        boolean throwing=session.mode()==GalateaPlanner.AxeMode.THROWING||ability.activation()==ForagingAxePolicy.Activation.USE;
        if(target==null) {
            if(kind==GalateaPlanner.Kind.FIG)jumpCounter.calculatingCandidate();
            if(kind==GalateaPlanner.Kind.FIG) {
                try {
                    Set<BlockPos> preparationFailures=new HashSet<>();
                    for(int attempt=0;attempt<=5;attempt++) {
                        target=figSourceSelector.select(session.mode(),session.remainingLogs(),p->session.canAttempt(p)&&!preparationFailures.contains(p),
                                config.get(GalateaConfig.FIG_AIM_HIGHER_AT_STEM),figPreviousTarget).orElse(null);
                        if(target==null)break;
                        figPreviousTarget=target;
                        if(session.mode()!=GalateaPlanner.AxeMode.STEM&&session.mode()!=GalateaPlanner.AxeMode.THROWING)break;
                        var stand=session.mode()==GalateaPlanner.AxeMode.THROWING?figSourceSelector.prepareThrow(target):figSourceSelector.prepareStem(target);
                        if(stand.isEmpty()){preparationFailures.add(target);target=null;continue;}
                        if(new Vec3(client.player.getX(),client.player.getY(),client.player.getZ()).distanceTo(stand.orElseThrow())>.35) {
                            if(navigation.start(new LocalRoute.Node(stand.orElseThrow(),0,0),.35,dev.veltraph.client.navigation.SharedMovementPolicy.Purpose.CROP_WORK)) {
                                phase="WALK";actionStarted=now;services.breaking().setBreaking(false);return;
                            }
                            preparationFailures.add(target);target=null;continue;
                        }
                        break;
                    }
                } catch(RuntimeException unknown){finish(now,false,"Fig candidate observation unavailable: "+unknown.getMessage());return;}
                if(target==null){nextMode(now);return;}
            } else target=session.choose(p->distance(p)<=(throwing?throwRange():4.4),Comparator.comparingDouble(this::distance)).orElse(null);
            if(target==null) {
                if(session.mode()==GalateaPlanner.AxeMode.STEM&&(kind!=GalateaPlanner.Kind.MANGROVE||config.get(GalateaConfig.MANGROVE_MOVE_AROUND))) {
                    for(BlockPos p:session.remainingLogs().stream().filter(p->!attemptedApproaches.contains(p)).sorted(Comparator.comparingDouble(this::distance)).limit(3).toList()) {
                        attemptedApproaches.add(p);
                        if(approach(p)) { target=p;phase="WALK";actionStarted=now;services.breaking().setBreaking(false);return; }
                    }
                }
                // Jump mode can acquire a high branch while ordinary jump input raises eye height.
                if(session.mode()==GalateaPlanner.AxeMode.JUMPING) {
                    target=session.choose(p->distance(p)<7&&p.y()>client.player.getY(),Comparator.comparingDouble(this::distance)).orElse(null);
                }
                if(target==null) { nextMode(now);return; }
            }
            actionStarted=now;
        }
        if(now-actionStarted>timeout()) { session.fail(target);if(kind==GalateaPlanner.Kind.FIG)session.exclude(target);target=null;services.breaking().setBreaking(false);if(kind!=GalateaPlanner.Kind.FIG||session.mode()!=GalateaPlanner.AxeMode.JUMPING)setJumpHeld(false);return; }
        if(kind==GalateaPlanner.Kind.FIG&&session.mode()==GalateaPlanner.AxeMode.JUMPING)aimJump(target,now);
        else aim(target,session.mode()==GalateaPlanner.AxeMode.STEM&&config.get(GalateaConfig.FIG_AIM_HIGHER_AT_STEM));
        services.input().setSneakHeld(config.get(GalateaConfig.FIG_PREFER_SNEAKING)&&!throwing);
        if(kind!=GalateaPlanner.Kind.FIG&&session.mode()==GalateaPlanner.AxeMode.JUMPING) setJumpHeld((now-actionStarted)%900<350);
        if(throwing) {
            services.breaking().setBreaking(false);
            // Raycast the exact target at extended range; never use a throwing ability through walls.
            var hit=client.player.pick(throwRange(),1,false);
            if(!throwAbility()) { finish(now,false,"Remaining branches need observed Throwing Axe ability lore");return; }
            if(hit instanceof BlockHitResult h&&hit.getType()==HitResult.Type.BLOCK&&h.getBlockPos().equals(mc(target))&&!harvestCycle.active()&&client.gameMode!=null) {
                client.gameMode.useItem(client.player,InteractionHand.MAIN_HAND);harvestCycle.activate(true,now,responseWindow());activations++;nextAxe=now+ability.cooldownMs();
            }
        } else {
            boolean attack=distance(target)<=4.4&&aligned(target)&&!(kind==GalateaPlanner.Kind.FIG&&session.excluded(target));
            if(attack) { if(!harvestCycle.active()) activations++;harvestCycle.activate(false,now,responseWindow()); }
            services.breaking().setBreaking(attack);
        }
        status=session.mode()+" | Sweep="+ability.sweep()+" | logs remaining="+session.remaining();
    }
    private void scaffold(long now) {
        services.breaking().setBreaking(false);
        if(now-actionStarted>timeout()) { nextMode(now);return; }
        if(phase.equals("CLIMB")) {
            selectSlot(axeSlot);setJumpHeld(true);
            if(client.player.getY()>=scaffoldSupport.y()+.75) { setJumpHeld(false);phase="CUT";target=null;actionStarted=now; }
            return;
        }
        if(phase.equals("WAIT_SCAFFOLD")) {
            BlockPos placed=scaffoldSupport.up();
            if(client.level.getBlockState(mc(placed)).is(Blocks.SCAFFOLDING)) {
                scaffoldSupport=placed;placedScaffolds++;
                int wanted=kind==GalateaPlanner.Kind.FIG?figObservedScaffoldHeight:Math.min(8,Math.max(1,session.tree().height()-3));
                if(placedScaffolds>=wanted||distance(scaffoldSupport)>4) {
                    var position=new Vec3(scaffoldBase.x()+.5,scaffoldBase.y(),scaffoldBase.z()+.5);
                    if(navigation.start(new LocalRoute.Node(position,0,0),.35,dev.veltraph.client.navigation.SharedMovementPolicy.Purpose.CROP_WORK)) { phase="SCAFFOLD_WALK";actionStarted=now; }
                    else nextMode(now);
                } else { phase="PLACE_SCAFFOLD";actionStarted=now; }
            }
            return;
        }
        int slot=findScaffold();if(slot<0||distance(scaffoldSupport)>4.4) { nextMode(now);return; }
        selectSlot(slot);
        var point=net.minecraft.world.phys.Vec3.atCenterOf(mc(scaffoldSupport)).add(0,.499,0);var d=point.subtract(client.player.getEyePosition());
        interactionRotation.track((float)Math.toDegrees(Math.atan2(-d.x,d.z)),(float)-Math.toDegrees(Math.atan2(d.y,Math.hypot(d.x,d.z))));
        if(client.hitResult instanceof BlockHitResult h&&h.getBlockPos().equals(mc(scaffoldSupport))&&h.getDirection()==net.minecraft.core.Direction.UP&&client.gameMode!=null) {
            client.gameMode.useItemOn(client.player,InteractionHand.MAIN_HAND,h);phase="WAIT_SCAFFOLD";actionStarted=now;
        }
    }
    private List<String> rewardTokens() {
        return Arrays.stream((config.get(GalateaConfig.GALATEA_MATERIAL_REWARD_TOKEN)+","+config.get(GalateaConfig.GALATEA_MATERIAL_REWARD_NAMES)).toLowerCase(Locale.ROOT).split(","))
                .map(String::strip).filter(s->!s.isEmpty()).distinct().toList();
    }
    private int rewardCount() {
        if(client.player==null) return 0;var tokens=rewardTokens();int count=0;
        for(int i=0;i<36;i++) { var stack=client.player.getInventory().getItem(i);String name=stack.getHoverName().getString().replaceAll("§.","").toLowerCase(Locale.ROOT);if(tokens.stream().anyMatch(name::contains)) count+=stack.getCount(); }
        return count;
    }
    private void collect(long now) {
        var outcome=materialCycle.observe(now,loaded(target),resource(target),maturity(target),rewardCount(),
                Math.max(100,Math.min(5000,config.get(GalateaConfig.GALATEA_MATERIAL_CONFIRM_MS))),timeout(),config.get(GalateaConfig.LUSHLILAC_ASSUME_CLICKED));
        if(outcome==MaterialHarvestCycle.Outcome.CONFIRMED) { resources++;finish(now,true,"Bush harvest confirmed");return; }
        if(outcome==MaterialHarvestCycle.Outcome.ASSUMED) { finish(now,false,"Click assumed; collection unconfirmed");return; }
        if(outcome==MaterialHarvestCycle.Outcome.FAILED) { finish(now,false,"No stable collection outcome");return; }
        if(materialCycle.wasClicked()&&config.get(GalateaConfig.LUSHLILAC_CLICK_TYPE)==GalateaPlanner.ClickType.RIGHT) {
            services.breaking().setBreaking(false);status="Waiting for bush maturity/reward acknowledgement";return;
        }
        if(materialCycle.wasClicked()&&!resource(target)) { services.breaking().setBreaking(false);return; }
        if(now-actionStarted>timeout()) { finish(now,false,"No collection outcome");return; }
        if(distance(target)>4.4) { finish(now,false,"Material left reach");return; }
        materialSlot=findMaterialTool();
        if(materialSlot<0) { status="No ideal bush tool in slots 1-8";setEnabled(false);return; }
        selectSlot(materialSlot);
        if(!materialCycle.wasClicked()&&maturity(target)>=0&&maturity(target)<2) { finish(now,false,"Bush is no longer ripe");return; }
        aim(target,false);if(!aligned(target)) { services.breaking().setBreaking(false);return; }
        if(config.get(GalateaConfig.LUSHLILAC_CLICK_TYPE)==GalateaPlanner.ClickType.LEFT) {
            services.breaking().setBreaking(true);materialCycle.clicked(now);
        } else if(!materialCycle.wasClicked()&&now>=nextUse&&client.gameMode!=null&&client.hitResult instanceof BlockHitResult hit) {
            services.breaking().setBreaking(false);client.gameMode.useItemOn(client.player,InteractionHand.MAIN_HAND,hit);materialCycle.clicked(now);
            nextUse=now+Math.max(100,config.get(GalateaConfig.GALATEA_USE_COOLDOWN_MS));
        }
    }
    public void onFeedback(String text) {
        if(!isEnabled()||rest.paused()||text==null) return;
        String clean=text.replaceAll("§.","");String lower=clean.toLowerCase(Locale.ROOT);
        if(kind==GalateaPlanner.Kind.FIG){
            long now=services.clock().nowMillis();
            if(clean.startsWith("You cannot damage a tree")){
                if(session!=null&&figDescriptorToken!=null){figDescriptorCooldown.cannotDamage(figDescriptorToken,now);finish(now,false,clean,true);}
                return;
            }
            figScaffold.feedback(clean,now);
            if(clean.startsWith("You can't place a scaffolding here!")){
                if(session!=null&&session.mode()!=GalateaPlanner.AxeMode.THROWING){figScaffold.cancel();session.setMode(GalateaPlanner.AxeMode.THROWING);jumpCounter.enterJumpOrThrow();target=null;phase="CUT";actionStarted=now;}
                return;
            }
        }
        if(kind!=GalateaPlanner.Kind.FIG&&session!=null&&session.mode()==GalateaPlanner.AxeMode.SCAFFOLD&&clean.startsWith("You can't place a scaffolding here")) {
            nextMode(services.clock().nowMillis());return;
        }
        if(clean.startsWith("You cannot damage a tree")||lower.contains("already collected")||lower.contains("not enough mana")) {
            finish(services.clock().nowMillis(),false,clean);return;
        }
        // Inventory and block changes are the default evidence. A reward message must identify both
        // the collection and configured reward, rather than accepting arbitrary unrelated chat.
        if(kind==GalateaPlanner.Kind.MATERIALS&&materialCycle.wasClicked()&&rewardTokens().stream().anyMatch(lower::contains)&&(lower.startsWith("you collected ")||lower.startsWith("you received ")))
            materialCycle.feedback();
    }
    private boolean beginSourceFallRecovery() {
        if(kind!=GalateaPlanner.Kind.FIG||!config.get(FigRecoveryConfig.FIG_FALL_RECOVERY)||!fallRecovery.inArea())return false;
        var velocity=client.player.getDeltaMovement();if(Math.hypot(velocity.x,velocity.z)>=.1)return false;
        release();fallRecovery.reset();
        if(fallRecovery.start()){phase="FIG_FALL_RECOVERY";status=fallRecovery.status();}
        else {status=fallRecovery.status();setEnabled(false);}
        return true;
    }
    @Override public void onActionBar(String text) {if(kind==GalateaPlanner.Kind.FIG)fallRecovery.onActionBar(text);}
    @Override public boolean consumeExpectedTeleport(Vec3 position,long now) {
        if(!isEnabled()||rest.paused()||!client.isSameThread()||owner==null||travelConnection==null
                ||client.player!=owner||client.level!=ownerLevel||client.getConnection()!=travelConnection)return false;
        if(figScaffold!=null&&figScaffold.consumeExpectedTeleport(position,now))return true;
        return navigation.consumeExpectedTeleport(position,now)||fallRecovery.consumeExpectedTeleport(position,now);
    }
    @Override public boolean ownsGameplayInput(){return !rest.paused();}
    @Override public Optional<String> scheduledRestReadiness(){
        if(!isEnabled()||!phase.equals("SEARCH")||session!=null||target!=null||scaffoldBase!=null||scaffoldSupport!=null||harvestCycle.active())return Optional.of("Galatea requires its idle between-target search boundary");
        if(!rest.paused()&&(client.player!=owner||client.level!=ownerLevel))return Optional.of("Galatea original native owner changed");
        if(client.player==null||client.level==null||!client.player.isAlive()||client.gui.screen()!=null||client.player.containerMenu!=client.player.inventoryMenu||!client.player.containerMenu.getCarried().isEmpty())return Optional.of("Galatea requires a healthy closed native menu");
        if(McForagingAbilitySource.cooldownActive(client)||services.clock().nowMillis()<nextAxe)return Optional.of("Galatea ability/cooldown remains active");
        return preStartCheck();
    }
    @Override public boolean pauseForScheduledRest(Object ticket){return rest.pause(ticket,scheduledRestReadiness().isEmpty(),services.clock().nowMillis(),this::release);}
    @Override public boolean scheduledRestOwnedBy(Object ticket){return isEnabled()&&rest.owns(ticket);}
    @Override public boolean rebindForScheduledRest(Object ticket){
        if(!scheduledRestOwnedBy(ticket)||scheduledRestReadiness().isPresent())return false;
        owner=client.player;ownerLevel=client.level;travelConnection=client.getConnection();blockedRestHit=client.hitResult;previous=null;previousDrops.clear();attemptedApproaches.clear();navigation.stop();return rest.rebound(ticket);
    }
    @Override public boolean resumeFromScheduledRest(Object ticket){if(!scheduledRestOwnedBy(ticket))return false;long elapsed=rest.resume(ticket,services.clock().nowMillis());if(elapsed<0)return false;nextScan+=elapsed;return true;}
    public Object scheduledEngineIdentity(){return kind==GalateaPlanner.Kind.MATERIALS?materialCycle:harvestCycle;}
    @Override public String diagnostics() { return id()+" | "+phase+" | "+status+" | trees="+trees+" | removed logs="+(removed+(session==null?0:session.removed()))+" | collected log items="+(collectedLogs+(session==null?0:Math.max(0,inventoryLogs()-inventoryLogsBefore)))+" | resources="+resources+" | largest observed batch="+largestBatch+" | multi-log batches="+multiBatches+" | axe activations="+activations+" | blacklisted="+blacklist.size(); }
    public GalateaPlanner.Kind kind() { return kind; }
    public int completedTrees() { return trees; }
    public int collectedResources() { return resources; }
    public int largestObservedBatch() { return largestBatch; }
    public int observedMultiLogBatches() { return multiBatches; }
    public int axeActivations() { return activations; }
    public int collectedLogItems() { return collectedLogs; }
}
