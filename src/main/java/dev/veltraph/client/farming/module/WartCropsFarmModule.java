package dev.veltraph.client.farming.module;

import dev.veltraph.client.config.ConfigService;
import dev.veltraph.client.config.FarmingConfig;
import dev.veltraph.client.config.HoldForwardPolicy;
import dev.veltraph.client.farming.domain.FarmLayoutProfile;
import dev.veltraph.client.farming.domain.FarmLayoutRegistry;
import dev.veltraph.client.farming.domain.FarmMovementMode;
import dev.veltraph.client.farming.domain.WartVariety;
import dev.veltraph.client.farming.domain.WartCropsDesign;
import dev.veltraph.client.farming.movement.FarmCollisionProbe;
import dev.veltraph.client.farming.movement.McFarmCollisionProbe;
import dev.veltraph.client.farming.movement.FarmRouteDriver;
import dev.veltraph.client.farming.movement.McFarmRouteDriver;
import dev.veltraph.client.farming.movement.FarmDropHandoff;
import dev.veltraph.client.farming.movement.planner.AceTwoKeyWheatPlanner;
import dev.veltraph.client.farming.movement.Angles;
import dev.veltraph.client.farming.movement.CropSightline;
import dev.veltraph.client.farming.movement.FarmAimController;
import dev.veltraph.client.farming.movement.FarmGeometry;
import dev.veltraph.client.farming.movement.SShapeRowEngine;
import dev.veltraph.client.farming.movement.SShapeState;
import dev.veltraph.client.farming.movement.TurnDirection;
import dev.veltraph.client.farming.movement.planner.VerticalWartPlanner;
import dev.veltraph.client.log.VeltraphLog;
import dev.veltraph.client.service.FarmServices;
import dev.veltraph.client.world.BlockPos;
import dev.veltraph.client.world.Direction;
import dev.veltraph.client.world.ProbeResult;
import dev.veltraph.client.world.Vec3;

import java.util.List;
import java.util.Random;

/**
 * Wart/Crops farming module. One card with a 4-value crop selector ({@link WartVariety}) and two
 * layout modes:
 *
 * <ul>
 *   <li>{@link FarmMovementMode#S_SHAPE} — crop sightlines, supported end-corridor solving and
 *       position-acknowledged turns through {@link SShapeRowEngine}.</li>
 *   <li>{@link FarmMovementMode#VERTICAL} — the recovered {@link VerticalWartPlanner} two-counter
 *       still-detection machine, now driven against the real services through {@link VerticalWartWorld}.</li>
 * </ul>
 */
public final class WartCropsFarmModule extends FarmModule {

    public static final String ID = "wart_crops";

    private final FarmLayoutProfile sShapeProfile;
    private final FarmLayoutProfile verticalProfile;
    private final FarmLayoutProfile aceProfile;
    private final FarmGeometry geometry;
    private final SShapeRowEngine sShapeEngine;
    private final VerticalWartPlanner verticalPlanner;
    private final VerticalWartWorld verticalWorld;

    private FarmMovementMode mode = FarmMovementMode.S_SHAPE;
    private WartVariety variety = WartVariety.NETHER_WART;
    private boolean explicitVariety;
    private WartCropsDesign design=WartCropsDesign.CLASSICAL,runningDesign=WartCropsDesign.CLASSICAL;
    private final AceTwoKeyWheatPlanner ace=new AceTwoKeyWheatPlanner();
    private FarmCollisionProbe collisionProbe;
    private FarmRouteDriver routeDriver;
    private dev.veltraph.client.farming.movement.CropCompletionPort reachCompletion;
    private Object initialPlayer,initialWorld,initialConnection;
    private boolean initialCoverageRevoked;
    private FarmAimController aceAim;
    private float aceYaw;
    private String aceStopReason="";

    public WartCropsFarmModule(FarmServices services, ConfigService config, FarmLayoutRegistry registry) {
        super(ID, "Wart/Crops", services, config, registry);
        this.sShapeProfile = registry.byId(FarmLayoutRegistry.WART_CROPS_SSHAPE).orElseThrow();
        this.verticalProfile = registry.byId(FarmLayoutRegistry.WART_CROPS_VERTICAL).orElseThrow();
        this.aceProfile=registry.byId(FarmLayoutRegistry.WART_CROPS_ACE_TWO_KEY).orElseThrow();
        this.design=config.get(FarmingConfig.WART_CROPS_DESIGN);
        this.geometry = new FarmGeometry(config, services.player(), services.world());
        if(services.world() instanceof dev.veltraph.client.service.mc.McWorldService){
            collisionProbe=new McFarmCollisionProbe(net.minecraft.client.Minecraft.getInstance());
            routeDriver=new McFarmRouteDriver(net.minecraft.client.Minecraft.getInstance(),services,config,this);
            geometry.setCollisionProbe(collisionProbe);
            reachCompletion=new dev.veltraph.client.farming.movement.McReachableCropCompletion(net.minecraft.client.Minecraft.getInstance(),this,services,this::selectedFarmingToolReady,this::reachCompletionEligible);
        }
        this.sShapeEngine = new SShapeRowEngine(config, services, geometry,
                FarmingConfig.SSHAPE_HOLD_FORWARD_POLICY, this::toolReady);
        if(routeDriver!=null)sShapeEngine.setDropDriver(routeDriver);
        this.verticalWorld = new VerticalWartWorld();
        this.verticalPlanner = new VerticalWartPlanner(config, verticalWorld);
    }

    @Override
    protected List<String> toolKeywords() {
        selectRunCrop();
        return List.of("hoe", "wart");
    }

    public FarmMovementMode mode() {
        return mode;
    }

    /** Switch layout mode. Only honored while disabled (avoids mid-run engine swaps). */
    public void setMode(FarmMovementMode mode) {
        if (mode != FarmMovementMode.S_SHAPE && mode != FarmMovementMode.VERTICAL) {
            throw new IllegalArgumentException("Wart/Crops supports S_SHAPE or VERTICAL, got " + mode);
        }
        if(this.mode!=mode&&isEnabled())setEnabled(false);
        this.mode = mode;
    }

    public WartCropsDesign design(){return design;}
    public void setDesign(WartCropsDesign design){java.util.Objects.requireNonNull(design);
        if(this.design!=design&&isEnabled())setEnabled(false);
        this.design=design;config.set(FarmingConfig.WART_CROPS_DESIGN,design);}
    /** Fresh shared eligibility at entry and immediately before native view/input publication. */
    boolean reachCompletionEligible(){return config.get(FarmingConfig.WHEAT_REACH_COMPLETION)&&variety==WartVariety.WHEAT&&runningDesign==WartCropsDesign.CLASSICAL&&config.get(FarmingConfig.WART_CROPS_DESIGN)==runningDesign;}
    /** Explicit injected completion port for pure permission/caller regression fixtures. */
    public void setReachCompletionPort(dev.veltraph.client.farming.movement.CropCompletionPort port){if(isEnabled())throw new IllegalStateException("Stop farm before binding completion port");reachCompletion=java.util.Objects.requireNonNull(port);}
    /** Pure fixture seam; production reads actual loaded Minecraft shapes. */
    public void setCollisionProbe(FarmCollisionProbe probe){this.collisionProbe=java.util.Objects.requireNonNull(probe);geometry.setCollisionProbe(probe);}
    public void setRouteDriver(FarmRouteDriver driver){routeDriver=java.util.Objects.requireNonNull(driver);
        sShapeEngine.setDropDriver(driver);verticalWorld.dropHandoff=new FarmDropHandoff(driver);}
    /** Private original vertical from/to witness. Public accessors cannot issue or replace an attempt. */
    public final class VerticalDropAttempt {
        private final FarmRouteDriver driver=routeDriver;
        private final FarmDropHandoff handoff=verticalWorld.dropHandoff;
        private final Vec3 from,target;
        private final Direction heading=verticalWorld.heading;
        private final TurnDirection direction=verticalWorld.turnDir;
        private final WartVariety crop=variety;
        private final WartCropsDesign layout=runningDesign;
        private final dev.veltraph.client.config.MapConfigService.StoredStateSnapshot settings;
        private VerticalDropAttempt(Vec3 from,Vec3 target){this.from=from;this.target=target;
            settings=config instanceof dev.veltraph.client.config.MapConfigService m?m.storedStateSnapshot()
                :config instanceof dev.veltraph.client.config.JsonConfigService j?j.storedStateSnapshot():null;}
        public Vec3 fromPosition(){return from;}public Vec3 targetPosition(){return target;}
    }
    public VerticalDropAttempt nativeVerticalDropAttempt(McFarmRouteDriver exact,Vec3 target,int bound){
        var attempt=verticalWorld.dropAttempt;
        return bound==3&&attempt!=null&&attempt.target==target
            &&dev.veltraph.client.navigation.SharedMovementPolicy.finite(attempt.from)&&dev.veltraph.client.navigation.SharedMovementPolicy.finite(target)
            &&attempt.from.y()-target.y()>.15&&attempt.from.y()-target.y()<=3.001
            &&nativeVerticalDropCurrent(attempt,exact)?attempt:null;
    }
    /** Final refusal-only source leaf: the original private dispatch and exact fields, never a caller's role flag. */
    public boolean nativeVerticalDropCurrent(VerticalDropAttempt attempt,McFarmRouteDriver exact){
        return attempt!=null&&attempt==verticalWorld.dropAttempt&&verticalWorld.dropDispatch&&exact==routeDriver&&attempt.driver==exact
            &&attempt.handoff==verticalWorld.dropHandoff&&attempt.target==verticalWorld.dropTarget
            &&mode==FarmMovementMode.VERTICAL&&isEnabled()&&!isSuspended()&&variety==attempt.crop&&runningDesign==attempt.layout
            &&verticalWorld.heading==attempt.heading&&verticalWorld.turnDir==attempt.direction
            &&attempt.settings!=null&&attempt.settings.current();
    }
    public Vec3 activeDropTarget(){return mode==FarmMovementMode.S_SHAPE?sShapeEngine.activeDropTarget():verticalWorld.dropTarget;}
    public int completedHeightDrops(){return mode==FarmMovementMode.S_SHAPE?sShapeEngine.completedHeightDrops():verticalWorld.observedDrops;}
    public int maxIntentionalDropBlocks(){return 3;}
    public int aceCompletedEnds(){return ace.completedEnds();}
    public int aceObservedDrops(){return ace.observedDrops();}
    public AceTwoKeyWheatPlanner.State aceState(){return ace.state();}
    public WartVariety variety() {
        return variety;
    }

    public void setVariety(WartVariety variety) {
        explicitVariety=true;
        this.variety = variety;
        if (variety != null) geometry.setSelectedCrop(selectedCropId());
    }

    @Override
    public FarmLayoutProfile activeProfile() {
        if(mode==FarmMovementMode.VERTICAL)return verticalProfile;
        return (isEnabled()?runningDesign:config.get(FarmingConfig.WART_CROPS_DESIGN))==WartCropsDesign.ACE_TWO_KEY_LOOP?aceProfile:sShapeProfile;
    }

    @Override
    public void onEnable() {
        initialCoverageRevoked=false;
        if(reachCompletion!=null)reachCompletion.cancel();
        installFarmTargetGuard( this::allowedHarvestTarget);
        try {
            if(routeDriver!=null)routeDriver.stop();services.rotation().cancelRotation();
            design=config.get(FarmingConfig.WART_CROPS_DESIGN);runningDesign=design;
            selectRunCrop();geometry.setSelectedCrop(selectedCropId());
            if(mode==FarmMovementMode.S_SHAPE&&runningDesign==WartCropsDesign.ACE_TWO_KEY_LOOP){
                aceStopReason="";aceYaw=Angles.wrapDegrees(-56.04f+90*Math.round((services.rotation().yaw()+56.04f)/90));
                aceAim=buildAceAim();aceAim.setTarget(aceYaw,0);
                ace.start(config.get(FarmingConfig.NETHERWART_STARTING_DIRECTION),services.player().position(),services.clock().nowMillis());
            } else if (mode == FarmMovementMode.S_SHAPE) {
                boolean nativeInitialCoverage=bindInitialLaneContext();
                sShapeEngine.setInitialCoveragePermission(this::initialLanePermission);
                sShapeEngine.setInitialCoverageRevocation(this::revokeInitialLaneCoverage);
                // Normal END -> START reuses this same active engine through its complete restart.
                sShapeEngine.start(nativeInitialCoverage&&reachCompletionEligible());
            } else {
                verticalWorld.start();
                verticalPlanner.start();
            }
        } catch (RuntimeException | Error failure) {
            releaseInputs();
            clearFarmTargetGuard();
            throw failure;
        }
    }

    private String selectedCropId() {
        return switch (variety) {
            case WHEAT -> "minecraft:wheat";
            case CARROT -> "minecraft:carrots";
            case POTATO -> "minecraft:potatoes";
            case NETHER_WART -> "minecraft:nether_wart";
        };
    }

    /** The card promises automatic crop detection; explicit API selections remain authoritative. */
    private void selectRunCrop(){
        if(mode==FarmMovementMode.S_SHAPE&&config.get(FarmingConfig.WART_CROPS_DESIGN)==WartCropsDesign.ACE_TWO_KEY_LOOP){
            variety=WartVariety.WHEAT;return;
        }
        if(explicitVariety||!services.player().isAvailable())return;
        WartVariety hit=varietyFor(services.breaking().targetBlockId());
        var hitPosition=services.breaking().targetBlockPos();
        if(hit!=null&&hitPosition.isPresent()){
            BlockPos cell=hitPosition.get();Vec3 centre=new Vec3(cell.x()+.5,cell.y()+.5,cell.z()+.5);
            if(services.world().isLoaded(cell)&&services.world().isLoaded(cell.down())
                    &&centre.distanceTo(services.player().position())<=5.5
                    &&services.world().isBlock(cell.down(),hit==WartVariety.NETHER_WART?"minecraft:soul_sand":"minecraft:farmland")){
                variety=hit;return;
            }
        }
        BlockPos centre=new Vec3(services.player().position().x(),services.player().position().y()+.15,
                services.player().position().z()).toBlockPos();
        double closest=Double.POSITIVE_INFINITY;WartVariety found=null;
        for(int x=-4;x<=4;x++)for(int z=-4;z<=4;z++)for(int y=-1;y<=3;y++){
            BlockPos cell=centre.add(x,y,z),support=cell.down();
            if(!services.world().isLoaded(cell)||!services.world().isLoaded(support))continue;
            WartVariety crop=varietyFor(services.world().blockId(cell));if(crop==null)continue;
            String soil=crop==WartVariety.NETHER_WART?"minecraft:soul_sand":"minecraft:farmland";
            if(!services.world().isBlock(support,soil))continue;
            double distance=x*x+z*z+y*y;
            if(distance<closest){closest=distance;found=crop;}
        }
        if(found!=null)variety=found;
    }
    private static WartVariety varietyFor(String id){if(id==null)return null;return switch(id){
        case "minecraft:wheat"->WartVariety.WHEAT;case "minecraft:carrots"->WartVariety.CARROT;
        case "minecraft:potatoes"->WartVariety.POTATO;case "minecraft:nether_wart"->WartVariety.NETHER_WART;
        default->null;
    };}

    private boolean allowedHarvestTarget(BlockPos actual) {
        BlockPos support = actual.down();
        return services.world().isLoaded(actual) && services.world().isLoaded(support)
                && services.world().isBlock(actual, selectedCropId())
                // Normal S keeps sweeping/attacking through selected crops at every growth stage.
                &&(mode==FarmMovementMode.S_SHAPE&&runningDesign==WartCropsDesign.CLASSICAL
                        ||collisionProbe==null||collisionProbe.matureCrop(actual))
                && services.world().isBlock(support, variety == WartVariety.NETHER_WART
                        ? "minecraft:soul_sand" : "minecraft:farmland");
    }

    @Override
    protected void onDisableFarming() {
        try { if(reachCompletion!=null)reachCompletion.cancel();sShapeEngine.stop(); ace.stop(); verticalWorld.stop(); super.onDisableFarming(); }
        finally { clearFarmTargetGuard(); }
    }

    @Override protected void onResumeAfterRewarp() {
        if(reachCompletion!=null)reachCompletion.cancel();
        if(routeDriver!=null)routeDriver.stop();services.rotation().cancelRotation();
        installFarmTargetGuard(this::allowedHarvestTarget);
        if(mode==FarmMovementMode.S_SHAPE&&runningDesign==WartCropsDesign.ACE_TWO_KEY_LOOP){
            ace.start(ace.side(),services.player().position(),services.clock().nowMillis());
            aceAim=buildAceAim();aceAim.setTarget(aceYaw,0);
        } else if (mode == FarmMovementMode.S_SHAPE) sShapeEngine.resumeFromObservedPosition();
        else { verticalWorld.resumeAfterTeleport(); verticalPlanner.resumeAfterTeleport(); }
    }

    /** Initial coverage is a native capability; an injected pure completion port does not supply PWC. */
    private boolean bindInitialLaneContext(){
        initialPlayer=null;initialWorld=null;initialConnection=null;
        if(!(services.world() instanceof dev.veltraph.client.service.mc.McWorldService)||reachCompletion==null)return false;
        var c=net.minecraft.client.Minecraft.getInstance();
        if(c==null||!c.isSameThread()||c.player==null||c.level==null||c.getConnection()==null
                ||c.player.level()!=c.level||!c.player.isAlive())return false;
        initialPlayer=c.player;initialWorld=c.level;initialConnection=c.getConnection();
        return true;
    }

    private boolean initialLanePermission(){
        if(!reachCompletionEligible()||isSuspended()||!selectedFarmingToolReady()||reachCompletion==null)return false;
        var c=net.minecraft.client.Minecraft.getInstance();
        var binding=services.breaking().targetGuardBinding(this);
        return c!=null&&c.isSameThread()&&c.player==initialPlayer&&c.level==initialWorld&&c.getConnection()==initialConnection
                &&c.player!=null&&c.player.isAlive()&&c.player.level()==c.level&&c.getCameraEntity()==c.player&&c.gui.screen()==null
                &&!c.isPaused()&&!c.level.tickRateManager().isEntityFrozen(c.player)
                &&dev.veltraph.client.VeltraphBootstrap.moduleManager().inputOwner().orElse(null)==this
                &&dev.veltraph.client.service.mc.McOwnedRotation.authority(c)==this
                &&binding.isPresent()&&services.breaking().targetGuardBoundToCurrentContext(this,binding.get());
    }

    private void revokeInitialLaneCoverage(){
        initialCoverageRevoked=true;
        sShapeEngine.quiesceInitialCoverage();
        if(reachCompletion!=null)reachCompletion.cancel();
        var c=net.minecraft.client.Minecraft.getInstance();
        if(c!=null&&c.isSameThread()&&dev.veltraph.client.VeltraphBootstrap.moduleManager().inputOwner().orElse(null)==this
                &&dev.veltraph.client.service.mc.McOwnedRotation.authority(c)==this)releaseInputs();
    }

    @Override
    protected void onTickFarming() {
        if(config.get(FarmingConfig.WART_CROPS_DESIGN)!=runningDesign){
            aceStopReason="Design changed; restart from observed entry";setEnabled(false);return;
        }
        if(mode==FarmMovementMode.S_SHAPE&&runningDesign==WartCropsDesign.ACE_TWO_KEY_LOOP){tickAce();return;}
        if (mode == FarmMovementMode.S_SHAPE) {
            if(initialCoverageRevoked){revokeInitialLaneCoverage();return;}
            if(sShapeEngine.initialCoveragePending()&&!initialLanePermission()){
                revokeInitialLaneCoverage();
                return;
            }
            if(reachCompletion!=null){
                boolean allowed=reachCompletionEligible();
                if(!allowed)reachCompletion.cancel();
                else if(reachCompletion.active()||sShapeEngine.state()==dev.veltraph.client.farming.movement.SShapeState.FARMING_SIDEWAYS){
                    // A normal S-shape run holds one row view; completion may audit that hit but never turn behind.
                    var result=reachCompletion.tickFixedRowView(true,sShapeEngine.targetYaw(),sShapeEngine.targetPitch());
                    if(result!=dev.veltraph.client.farming.movement.CropCompletionPort.Result.IDLE)return;
                }
            }
            sShapeEngine.tick();
            if(reachCompletion!=null&&reachCompletionEligible())reachCompletion.observePrimaryRowTick(sShapeEngine.targetYaw(),sShapeEngine.lateralSide(),sShapeEngine.state()==dev.veltraph.client.farming.movement.SShapeState.FARMING_SIDEWAYS);
            return;
        }
        // VERTICAL: the two-counter machine decides turns; the world adapter holds the macro-owned
        // heading with a smooth aim, gates motion on ground, and drives forward/break + the row-turn
        // strafe pulse (see VerticalWartWorld).
        if(verticalWorld.tickDrop())return;
        verticalPlanner.tick();
        verticalWorld.applyInputs();
    }

    @Override
    public String debugState() {
        if(mode==FarmMovementMode.S_SHAPE&&runningDesign==WartCropsDesign.ACE_TWO_KEY_LOOP)return "Ace wheat/"+ace.status();
        String base = "Wart/Crops/" + variety + "/" + mode;
        return mode == FarmMovementMode.S_SHAPE
                ? base + "/" + sShapeEngine.state() + "/" + sShapeEngine.lateralSide()
                : base + "/dir=" + verticalPlanner.currentDir();
    }

    @Override
    public String diagnostics() {
        if(mode==FarmMovementMode.S_SHAPE&&runningDesign==WartCropsDesign.ACE_TWO_KEY_LOOP)
            return "Exact Ace wheat | "+ace.status()+" yaw="+aceYaw+" | "+aceStopReason;
        if (mode != FarmMovementMode.S_SHAPE) {
            return "Wart/Crops VERTICAL crop=" + variety + " toolSlot=" + chosenToolSlot() + "\n"
                    + verticalWorld.diagnostics();
        }
        return "Wart/Crops S-SHAPE crop=" + variety + " toolSlot=" + chosenToolSlot() + "\n"
                + sShapeEngine.diagnostics();
    }

    /** Pure active source phase; maturity never selects a jump or changes this classification. */
    public boolean groundedCropWorkActive(){
        return isEnabled()&&!isSuspended()&&mode==FarmMovementMode.S_SHAPE
                &&runningDesign==WartCropsDesign.CLASSICAL&&sShapeEngine.groundedCropWorkActive();
    }

    public SShapeState sShapeState() {
        return sShapeEngine.state();
    }

    public SShapeRowEngine engine() {
        return sShapeEngine;
    }

    private FarmAimController buildAceAim() {
        return new FarmAimController(new FarmAimController.Settings(
                Math.min(6f,config.get(FarmingConfig.ROTATION_MAX_YAW_DEG_PER_TICK).floatValue()),
                Math.min(6f,config.get(FarmingConfig.ROTATION_MAX_PITCH_DEG_PER_TICK).floatValue()),
                config.get(FarmingConfig.ROTATION_ACCELERATION),1f,
                config.get(FarmingConfig.ROTATION_PAUSE_ERROR_DEG).floatValue(),
                config.get(FarmingConfig.ROTATION_USE_MOUSE_GCD),config.get(FarmingConfig.ROTATION_MOUSE_SENSITIVITY),
                false,0f,20,20,1),new Random());
    }
    private void tickAce() {
        if(variety!=WartVariety.WHEAT||collisionProbe==null||!services.player().isAvailable()){
            aceStopReason="Ace profile requires wheat and actual collision evidence";setEnabled(false);return;
        }
        float[] next=aceAim.step(services.rotation().yaw(),services.rotation().pitch());
        services.rotation().aim(next[0],next[1]);
        Vec3 p=services.player().position();
        var support=collisionProbe.supportY(p.x(),p.z(),p.y()+.02,4);
        boolean safe=support.isPresent()&&p.y()-support.getAsDouble()<=3.001
                &&collisionProbe.clear(new Vec3(p.x(),support.getAsDouble(),p.z()),true);
        boolean supported=support.isPresent()&&Math.abs(p.y()-support.getAsDouble())<=.15;
        boolean wet=safe&&collisionProbe.water(p);
        double angle=Math.toRadians(aceYaw),sign=ace.side()==dev.veltraph.client.config.LaneSide.RIGHT?1:-1;
        boolean travel=false;
        // At335speed a dry native step can clear a thin skull/plate guide in one motion. Inspect
        // short and full step endpoints instead of rejecting that authored aperture at half-step.
        for(double step:new double[]{.35,.6,.85}){
            double dx=-Math.cos(angle)*step*sign,dz=-Math.sin(angle)*step*sign;
            travel|=aceStepSafe(p,dx,dz)||aceStepSafe(p,dx,0)||aceStepSafe(p,0,dz);
        }
        safe|=travel;
        boolean headWet=services.world().isWater(new Vec3(p.x(),p.y()+collisionProbe.eyeHeight(),p.z()).toBlockPos());
        var output=ace.tick(new AceTwoKeyWheatPlanner.Facts(p,services.player().horizontalSpeed(),
                collisionProbe.loaded(p),collisionProbe.clear(p,true)&&!headWet,supported,
                services.player().onGround(),wet,safe,travel,
                toolReady()&&aceAim.withinTolerance(services.rotation().yaw(),services.rotation().pitch()),
                services.breaking().targetBlockId().equals("minecraft:wheat")&&services.breaking().targetBlockPos()
                        .filter(collisionProbe::matureCrop).isPresent(),services.clock().nowMillis()));
        services.input().releaseAllMovement();services.input().setSprintHeld(false);
        services.input().setLeftHeld(output.left());services.input().setRightHeld(output.right());
        services.breaking().setBreaking(output.attack());
        if(output.state()==AceTwoKeyWheatPlanner.State.BLOCKED){aceStopReason=ace.status()+" pos="+p
                +" floor="+support+" ground="+services.player().onGround()+" wet="+wet+" safe="+safe
                +" travel="+travel+" headWet="+headWet;setEnabled(false);}
    }
    private boolean aceStepSafe(Vec3 p,double dx,double dz){
        if(Math.abs(dx)+Math.abs(dz)<.01)return false;
        double x=p.x()+dx,z=p.z()+dz;
        var support=collisionProbe.supportY(x,z,p.y()+.6,4);
        if(support.isEmpty()||p.y()-support.getAsDouble()>3.001)return false;
        return collisionProbe.clear(new Vec3(x,support.getAsDouble(),z),true);
    }

    /** Source vertical adapter: persistent lateral key, optional forward helper, horizontal
     * entry probes, significant-height input release and observed-position recovery. */
    private final class VerticalWartWorld implements VerticalWartPlanner.World {
    /** Passive exact maintenance checkpoint. No services, geometry, clocks or planner callbacks run. */
    public dev.veltraph.client.farming.module.FarmMaintenanceState maintenanceState() {
        return dev.veltraph.client.farming.module.FarmMaintenanceState.of(this,
                "heading", heading,
                "aim", aim == null ? null : aim.maintenanceState(),
                "heightPauseRequested", heightPauseRequested,
                "dropHandoff", dropHandoff == null ? null : dropHandoff.maintenanceState(),
                "dropTarget", dropTarget,
                // Negative checkpoint operands only: no attempt is exposed, restored or reissued.
                "dropAttempt", FarmMaintenanceState.identity(dropAttempt),
                "dropDispatch", dropDispatch,
                "observedDrops", observedDrops,
                "turnDir", turnDir,
                "heldForward", heldForward,
                "heldLeft", heldLeft,
                "heldRight", heldRight,
                "heldBreak", heldBreak,
                "grounded", grounded,
                "blocker", blocker);
    }


        private static final float EYE_HEIGHT = 1.62f;

        private Direction heading = Direction.NORTH;
        private FarmAimController aim;
        private boolean heightPauseRequested;
        private FarmDropHandoff dropHandoff=routeDriver==null?null:new FarmDropHandoff(routeDriver);
        private Vec3 dropTarget;
        private VerticalDropAttempt dropAttempt;
        private boolean dropDispatch;
        private int observedDrops;
        private TurnDirection turnDir = TurnDirection.LEFT;

        // Last input decisions, surfaced in diagnostics().
        private boolean heldForward;
        private boolean heldLeft;
        private boolean heldRight;
        private boolean heldBreak;
        private boolean grounded = true;
        private String blocker = "idle";

        /** Capture the macro-owned heading once and (re)build the smooth aim, mirroring S-shape start(). */
        void start() {
            heightPauseRequested = false;
            dropTarget=null;dropAttempt=null;dropDispatch=false;observedDrops=0;
            turnDir = TurnDirection.LEFT;
            this.aim = buildAim();
            this.heading = Direction.fromYaw(Angles.snapToCardinal(services.rotation().yaw()));
            this.aim.setTarget(headingYaw(), cropPitch());
            this.blocker = "vertical start";
            VeltraphLog.details("VerticalWartWorld.start", "captured heading=" + heading + " ("
                    + headingYaw() + "°)");
        }

        /**
         * Hold the crop view smoothly and apply persistent source lateral movement.
         * Counter/scheduler values never become timed movement pulses.
         */
        void applyInputs() {
            if (aim == null) {
                start();
            }
            retargetAndAim();

            grounded = services.player().onGround();
            if (!grounded) {
                // Off ground: pause movement + break (ground gating like the S-shape path).
                blocker = "not on ground";
                setForward(false);
                setStrafe(null);
                setBreaking(false);
                return;
            }

            if (heightPauseRequested || !toolReady() || !aim.withinTolerance(services.rotation().yaw(),services.rotation().pitch())) {
                heightPauseRequested=false;
                services.input().releaseAllMovement(); setBreaking(false);
                heldForward=heldLeft=heldRight=false; blocker="height/tool/aim settling";return;
            }
            turnDir=verticalPlanner.currentDir();
            boolean traversable=geometry.canStrafe(turnDir);
            if(!traversable&&config.get(FarmingConfig.DROP_AND_TURN)&&dropHandoff!=null){
                Vec3 lower=geometry.safeDropTarget(turnDir);
                if(lower!=null){
                    services.input().releaseAllMovement();setBreaking(false);services.rotation().cancelRotation();
                    if(dropDispatch){setEnabled(false);return;}
                    dropTarget=lower;dropAttempt=new VerticalDropAttempt(services.player().position(),lower);dropDispatch=true;
                    try{if(!dropHandoff.start(lower,services.clock().nowMillis())){dropTarget=null;dropAttempt=null;}}
                    finally{dropDispatch=false;}
                    blocker=dropHandoff.status();return;
                }
            }
            setStrafe(traversable?turnDir:null);
            HoldForwardPolicy policy=config.get(FarmingConfig.VERTICAL_HOLD_FORWARD_POLICY);
            setForward(traversable && (policy==HoldForwardPolicy.ALWAYS
                    || policy==HoldForwardPolicy.TAP && services.clock().currentTick()%2==0));
            setBreaking(geometry.harvestTargetAvailable());
            blocker=traversable?"persistent lateral farming "+turnDir:"waiting for source row/height reconsideration";
        }

        void resumeAfterTeleport() {
            heightPauseRequested=false;
            if(dropHandoff!=null)dropHandoff.stop();dropTarget=null;dropAttempt=null;dropDispatch=false;
            services.input().releaseAllMovement();setBreaking(false);
            heldForward=heldLeft=heldRight=false;
            this.aim=buildAim();
            aim.setTarget(headingYaw(), config.get(FarmingConfig.WART_FIX_PITCH_AFTER_TP)
                    ? cropPitch() : services.rotation().pitch());
            blocker="rechecking observed vertical row after return";
        }

        void stop(){if(dropHandoff!=null)dropHandoff.stop();dropTarget=null;dropAttempt=null;dropDispatch=false;
            heldForward=heldLeft=heldRight=heldBreak=false;}

        boolean tickDrop(){
            if(dropTarget==null)return false;
            setBreaking(false);
            if(dropDispatch){setEnabled(false);return true;}
            FarmRouteDriver.State outcome;dropDispatch=true;
            try{outcome=dropHandoff.tick(services.player().position(),services.player().onGround(),
                    geometry.dropLandingValid(dropTarget),services.clock().nowMillis());}
            finally{dropDispatch=false;}
            blocker="vertical drop: "+dropHandoff.status();
            if(outcome==FarmRouteDriver.State.ACTIVE)return true;
            dropHandoff.stop();dropTarget=null;dropAttempt=null;
            if(outcome==FarmRouteDriver.State.BLOCKED){setEnabled(false);return true;}
            observedDrops++;verticalPlanner.acknowledgeDropLanding();
            services.rotation().cancelRotation();aim.setTarget(headingYaw(),cropPitch());
            blocker="actual lower standing arrival, restoring crop view";return true;
        }

        // ---- Smooth aim (mirrors SShapeRowEngine.buildAim()/retargetAndAim()) ----

        private FarmAimController buildAim() {
            FarmAimController.Settings settings = new FarmAimController.Settings(
                    config.get(FarmingConfig.ROTATION_MAX_YAW_DEG_PER_TICK).floatValue(),
                    config.get(FarmingConfig.ROTATION_MAX_PITCH_DEG_PER_TICK).floatValue(),
                    config.get(FarmingConfig.ROTATION_ACCELERATION),
                    config.get(FarmingConfig.ROTATION_TOLERANCE_DEG).floatValue(),
                    config.get(FarmingConfig.ROTATION_PAUSE_ERROR_DEG).floatValue(),
                    config.get(FarmingConfig.ROTATION_USE_MOUSE_GCD),
                    config.get(FarmingConfig.ROTATION_MOUSE_SENSITIVITY),
                    config.get(FarmingConfig.ROTATION_JITTER),
                    config.get(FarmingConfig.ROTATION_JITTER_MAX_DEG).floatValue(),
                    config.get(FarmingConfig.ROTATION_JITTER_MIN_INTERVAL_TICKS),
                    config.get(FarmingConfig.ROTATION_JITTER_MAX_INTERVAL_TICKS),
                    config.get(FarmingConfig.ROTATION_JITTER_HOLD_TICKS));
            return new FarmAimController(settings, new Random());
        }

        /** Track the crop target continuously and step the smooth aim one tick (no ramp reset, no snap). */
        private void retargetAndAim() {
            aim.retarget(headingYaw(), cropPitch());
            float curYaw = services.rotation().yaw();
            float curPitch = services.rotation().pitch();
            float[] next = aim.step(curYaw, curPitch);
            services.rotation().aim(next[0], next[1]);
            VeltraphLog.verbose("VerticalWartWorld.aim", () -> String.format(
                    "target=%.1f/%.1f current=%.1f/%.1f err=%.1f", headingYaw(), cropPitch(),
                    curYaw, curPitch, aim.aimError(curYaw, curPitch)));
        }

        private float headingYaw() {
            return (float) heading.toYaw();
        }

        /** Pitch toward the wart block in front, from the player's eye (never a fixed 0). */
        private float cropPitch() {
            geometry.setHeading(heading);
            Vec3 pos = services.player().position();
            Vec3 eye = new Vec3(pos.x(), pos.y() + geometry.eyeHeight(), pos.z());
            return CropSightline.pitchTo(eye, geometry.targetVec());
        }

        // ---- Input helpers (verbose on edges, like the S-shape engine) ----

        private void setForward(boolean held) {
            if (held != heldForward) {
                VeltraphLog.verbose("VerticalWartWorld.input", "forward " + (held ? "HELD" : "released"));
            }
            heldForward = held;
            services.input().setForwardHeld(held);
        }

        private void setStrafe(TurnDirection side) {
            boolean left = side == TurnDirection.LEFT;
            boolean right = side == TurnDirection.RIGHT;
            if (left != heldLeft || right != heldRight) {
                VeltraphLog.verbose("VerticalWartWorld.input",
                        "strafe " + (left ? "LEFT" : right ? "RIGHT" : "none"));
            }
            heldLeft = left;
            heldRight = right;
            services.input().setLeftHeld(left);
            services.input().setRightHeld(right);
        }

        private void setBreaking(boolean breaking) {
            if (breaking != heldBreak) {
                VeltraphLog.details("VerticalWartWorld.break", "breaking " + (breaking ? "ON" : "off"));
            }
            heldBreak = breaking;
            services.breaking().setBreaking(breaking);
        }

        // ---- VerticalWartPlanner.World ----

        @Override
        public Vec3 position() {
            return services.player().position();
        }

        @Override
        public double verticalPos() {
            return services.player().position().y();
        }

        @Override
        public double horizontalSpeed() {
            return services.player().horizontalSpeed();
        }

        @Override
        public boolean rowAdvanceCheck() {
            // Advance check along the macro-owned heading (vertical mode reuses the crop-aware geometry).
            geometry.setHeading(heading);
            return geometry.canStrafe(verticalPlanner.currentDir()) || config.get(FarmingConfig.DROP_AND_TURN)
                    &&dropHandoff!=null&&geometry.safeDropTarget(verticalPlanner.currentDir())!=null;
        }

        @Override
        public boolean stepPredicates() {
            // Recovered large-step branch's two world/crop predicates: on ground + a crop/wall to break
            // in front (so a real vertical step is in progress rather than a row end).
            geometry.setHeading(heading);
            return services.player().onGround() && geometry.blockInFront();
        }

        /** Raw GeometryEngine direction getter tables; the scan itself is horizontal. */
        @Override public ProbeResult scanDirection(boolean second,int limit) {
            // Verified Yarn fields11043=NORTH,11034=EAST (the older report swapped them).
            Direction direction=geometry.lateral(second?TurnDirection.RIGHT:TurnDirection.LEFT);
            BlockPos base=geometry.rowBodyPos();
            for(int distance=1;distance<Math.min(256,limit);distance++) {
                BlockPos probe=base.offset(direction,distance);
                if(!services.world().isLoaded(probe)||!services.world().isLoaded(probe.down())
                        ||!services.world().isLoaded(probe.up()))return ProbeResult.NONE;
                if(services.world().isPassable(probe)&&services.world().isPassable(probe.down()))return ProbeResult.FOUND;
                if(!services.world().isPassable(probe)||!services.world().isPassable(probe.up()))return ProbeResult.BLOCKED;
            }
            return ProbeResult.NONE;
        }

        @Override public void onStill(TurnDirection dir,int diagnosticUnits) {
            blocker="source still observation "+dir+" (schedule="+diagnosticUnits+")";
        }
        @Override public void commitTurn() {
            turnDir=verticalPlanner.currentDir();
            blocker="source lateral direction changed to "+turnDir;
        }
        @Override public void releaseForHeightChange() {
            heightPauseRequested=true;
            services.input().releaseAllMovement();setBreaking(false);
            heldForward=heldLeft=heldRight=false;
        }

        /** Live {@code /vdebug macro} diagnostics line for the VERTICAL path (mirrors S-shape style). */
        String diagnostics() {
            geometry.setHeading(heading);
            float curYaw = services.rotation().yaw();
            float curPitch = services.rotation().pitch();
            float err = aim == null ? 0f : aim.aimError(curYaw, curPitch);
            return String.format(
                    "dir=%s heading=%s head=%.0f tgt=%.0f/%.0f err=%.1f heightPause=%b drops=%d target=%s%n"
                    + "  counters[still=%d fwd=%d] scan=%s side=%s%n"
                    + "  keys[F=%b L=%b R=%b break=%b] ground=%b stepFwd=%b inFront=%b%n"
                    + "  blocker=%s",
                    verticalPlanner.currentDir(), heading, headingYaw(),
                    headingYaw(), cropPitch(), err, heightPauseRequested,observedDrops,dropTarget,
                    verticalPlanner.stillCounter(), verticalPlanner.forwardCounter(),
                    verticalPlanner.lastScan(), verticalPlanner.lastResolvedSide(),
                    heldForward, heldLeft, heldRight, heldBreak,
                    grounded, geometry.canStepForward(), geometry.blockInFront(),
                    blocker);
        }
    }

    /** Passive exact maintenance checkpoint. No services, geometry, clocks or planner callbacks run. */
    public dev.veltraph.client.farming.module.FarmMaintenanceState maintenanceState() {
        return dev.veltraph.client.farming.module.FarmMaintenanceState.of(this,
                "sShapeProfile", sShapeProfile,
                "verticalProfile", verticalProfile,
                "aceProfile", aceProfile,
                "geometry", geometry == null ? null : geometry.maintenanceState(),
                "sShapeEngine", sShapeEngine == null ? null : sShapeEngine.maintenanceState(),
                "verticalPlanner", verticalPlanner == null ? null : verticalPlanner.maintenanceState(),
                "verticalWorld", verticalWorld == null ? null : verticalWorld.maintenanceState(),
                "mode", mode,
                "variety", variety,
                "explicitVariety", explicitVariety,
                "design", design,
                "runningDesign", runningDesign,
                "ace", ace == null ? null : ace.maintenanceState(),
                "collisionProbe", collisionProbe,
                "routeDriver", routeDriver==null?null:routeDriver.maintenanceState().orElseGet(()->FarmMaintenanceState.of(routeDriver,"unsupported",true)),
                "aceAim", aceAim == null ? null : aceAim.maintenanceState(),
                "aceYaw", aceYaw,
                "aceStopReason", aceStopReason,
                "reachCompletion", reachCompletion==null?null:reachCompletion.maintenanceState(),
                "initialPlayer",initialPlayer,"initialWorld",initialWorld,"initialConnection",initialConnection,"initialCoverageRevoked",initialCoverageRevoked);
    }
    @Override protected void onBeforeFarmSuspension(){try{if(reachCompletion!=null)reachCompletion.cancel();}finally{if(sShapeEngine!=null)sShapeEngine.retireRowTransitionObservation();}}
    @Override protected java.util.Optional<FarmMaintenanceState> maintenanceProgress(){
        return java.util.Optional.of(maintenanceState());
    }
    @Override protected FarmRouteDriver maintenanceRouteDriver(){return activeDropTarget()==null?null:routeDriver;}
    @Override protected java.util.Optional<String> maintenanceResumeReadinessError(){
        if(sShapeEngine!=null&&sShapeEngine.rowInvocationActive())
            return java.util.Optional.of("S-shape live tick invocation cannot be captured or resumed by maintenance");
        return verticalWorld.dropAttempt!=null||verticalWorld.dropDispatch
                ?java.util.Optional.of("Wart original WATER drop custody cannot resume through maintenance"):java.util.Optional.empty();
    }
    @Override protected java.util.Optional<String> maintenanceProgressReadinessError(){
        if(sShapeEngine!=null&&sShapeEngine.rowInvocationActive())
            return java.util.Optional.of("S-shape live tick invocation cannot be captured or resumed by maintenance");
        if(verticalWorld.dropAttempt!=null||verticalWorld.dropDispatch)
            return java.util.Optional.of("Wart original WATER drop custody cannot be captured or resumed by maintenance");
        if(reachCompletion!=null&&reachCompletion.active())return java.util.Optional.of("Wheat reach completion owns a stationary view; finish or cancel before maintenance");
        // McFarmRouteDriver caches an active navigation owner and native world. An active drop
        // cannot be leased until a separately proved route checkpoint/rebind exists.
        return activeDropTarget()!=null&&(routeDriver==null||routeDriver.maintenanceState().isEmpty())
                ?java.util.Optional.of("Wart active native drop route has no exact maintenance checkpoint"):java.util.Optional.empty();
    }
}
