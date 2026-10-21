package dev.veltraph.client.farming.movement;

import dev.veltraph.client.config.ConfigKey;
import dev.veltraph.client.config.ConfigService;
import dev.veltraph.client.config.FarmingConfig;
import dev.veltraph.client.config.HoldForwardPolicy;
import dev.veltraph.client.config.LaneSide;
import dev.veltraph.client.log.VeltraphLog;
import dev.veltraph.client.service.FarmServices;
import dev.veltraph.client.world.Direction;
import dev.veltraph.client.world.Vec3;

import java.util.Random;
import java.util.function.BooleanSupplier;

/**
 * Lateral S-shape crop-row engine — a clean source-first port of the Wart/Crops behavior described in
 * {@code reports/_spec_parts/FarmingForwardExecutor.md} + {@code MovementDirectionDecoded.md}, with the
 * <b>movement axis corrected</b> per live feedback (goal Target 2 / root cause #7):
 *
 * <pre>
 *   PREPARE_TOOL -> AIMING -> FARMING_SIDEWAYS  --row end-->  ROW_TRANSITION_FORWARD
 *                               ^                                   |
 *                               +------- reverse lateral ----------+
 * </pre>
 *
 * <p>The <b>primary motion is lateral</b> (strafe across the crop row) while facing + breaking the crop
 * in front; at a row end the engine steps <b>forward</b> one row and reverses the lateral direction.
 * Faithful behaviors ported from the original:
 * <ul>
 *   <li><b>{@code isOnGround} gate</b>, tool-selected gate, and the recovered <i>right-then-left</i>
 *       movable-direction probe ({@link FarmGeometry#movableLateral()}).</li>
 *   <li><b>{@code SSHAPE_HOLD_FORWARD_POLICY=NEVER} does not freeze movement</b> — it only disables the
 *       extra continuous-forward helper; the strafe still drives movement and break stays on (the live
 *       no-move bug, root cause #1). {@code ALWAYS} additionally holds forward through the sweep.</li>
 *   <li><b>Latch the initial crop-row view</b> ({@link FarmGeometry#targetVec()}) once at start, then
 *       keep the same heading and pitch through ordinary row movement and reversals.</li>
 *   <li><b>Macro-owned heading</b> captured once at {@link #start()} and used for all geometry — never
 *       the live {@code player.facing()} (root cause #4).</li>
 *   <li><b>Smooth multi-tick aim</b> via {@link FarmAimController} (no one-tick snap; root cause #5).</li>
 *   <li>60s stuck/reconsider path that recomputes the movable direction (Target 3).</li>
 * </ul>
 *
 * <p>Pure (config + services + {@link FarmGeometry}), so it is unit-testable with fakes.
 */
public final class SShapeRowEngine {

    private static final float EYE_HEIGHT = 1.62f;

    private final ConfigService config;
    private final FarmServices services;
    private final SShapeGeometry geometry;
    private final ConfigKey<HoldForwardPolicy> holdPolicyKey;
    private final BooleanSupplier toolReady;

    private SShapeState state = SShapeState.IDLE;
    private Direction heading = Direction.NORTH;
    private TurnDirection lateralSide = TurnDirection.RIGHT;
    private TurnDirection startSide = TurnDirection.RIGHT;
    private int forwardTicksLeft;
    private Vec3 transitionTarget;
    private Vec3 previousTransitionPose;
    private long previousTransitionTick=Long.MIN_VALUE;
    private long transitionObservationGeneration;
    private Object transitionTickFrame;
    private Object fixedTickFrame;
    private long transitionStartedMs;
    private int completedRowTransitions;
    private boolean initialCoverageEnabled;
    private java.util.function.BooleanSupplier initialCoveragePermission=()->false;
    private Runnable initialCoverageRevoked=()->releaseAll();
    private final InitialLaneCoverage initialCoverage=new InitialLaneCoverage();
    private FarmRouteDriver dropDriver;
    private FarmDropHandoff dropHandoff;
    private Vec3 dropTarget;
    private int completedHeightDrops;
    private long enteredRecoveringMs;
    private String pauseReason = "idle";
    private FarmAimController aim;
    private float rowPitch;
    private float rowYaw;
    private boolean fixedRowSteering;
    private boolean normalReferenceView;
    private Vec3 sweepAnchor;
    private final SupplementalWaterCoverage supplementalCoverage;
    private boolean supplementalAttempted;
    private long runGeneration;
    private int completedSupplementalPasses;
    private Object setupFrame;

    // Last input decisions, surfaced in diagnostics().
    private boolean heldForward;
    private boolean heldLeft;
    private boolean heldRight;
    private boolean heldBreak;
    /** Ticks elapsed in the current continuous-break run (for the attack-cadence re-tap). */
    private long breakRunTick;

    public SShapeRowEngine(ConfigService config, FarmServices services, SShapeGeometry geometry,
                           BooleanSupplier toolReady) {
        this(config, services, geometry, FarmingConfig.SSHAPE_HOLD_FORWARD_POLICY, toolReady);
    }

    public SShapeRowEngine(ConfigService config, FarmServices services, SShapeGeometry geometry,
                           ConfigKey<HoldForwardPolicy> holdPolicyKey, BooleanSupplier toolReady) {
        this.config = config;
        this.services = services;
        this.geometry = geometry;
        this.holdPolicyKey = holdPolicyKey;
        this.toolReady = toolReady;
        this.supplementalCoverage=new SupplementalWaterCoverage(new SupplementalWaterCoverage.World() {
            public Vec3 position(){return services.player().position();}
            public boolean onGround(){return services.player().onGround();}
            public boolean inWater(){return geometry.supplementalWaterAt(services.player().position());}
            public boolean bodyClear(Vec3 feet,boolean allowWater){return geometry.supplementalCoverageBodyClear(feet,allowWater);}
            public boolean current(SupplementalWaterCoverage.Plan plan){return geometry.supplementalCoverageCurrent(plan);}
        },new SupplementalWaterCoverage.MovementPort() {
            public void toward(Vec3 target,boolean jump,boolean attack){
                long originalRun=runGeneration,originalHelper=supplementalGeneration();
                java.util.function.BooleanSupplier same=()->originalRun==runGeneration&&originalHelper==supplementalGeneration();
                boolean precise=!jump&&services.player().onGround();if(!same.getAsBoolean())return;
                steerToward(target,precise,same);if(!same.getAsBoolean())return;
                services.input().setSneakHeld(false);if(!same.getAsBoolean())return;
                services.input().setSprintHeld(false);if(!same.getAsBoolean())return;
                services.input().setJumpHeld(jump);if(!same.getAsBoolean())return;
                boolean harvest=attack&&harvestAvailable();if(!same.getAsBoolean())return;
                guardedBreak(harvest,same);
            }
            public void release(){long run=runGeneration,helper=supplementalGeneration();releaseGuarded(()->run==runGeneration&&helper==supplementalGeneration());}
        });
    }

    /** Begin (or restart): capture the macro-owned heading from the initial setup and prepare the tool. */
    public void start() { start(initialCoverageEnabled); }

    /** Actual complete begin/restart atomically configures coverage; active metadata-only setters still refuse. */
    public void start(boolean initialLaneCoverage) {
        Object previous=setupFrame,frame=new Object();setupFrame=frame;
        try{startOriginal(initialLaneCoverage);}finally{if(setupFrame==frame)setupFrame=previous;}
    }
    private void startOriginal(boolean initialLaneCoverage) {
        long originalRun=++runGeneration;
        if(supplementalCoverage.plan()!=null)supplementalCoverage.stop();
        if(originalRun!=runGeneration)return;
        supplementalAttempted=false;completedSupplementalPasses=0;
        if(dropHandoff!=null)dropHandoff.stop();
        if(originalRun!=runGeneration)return;
        services.rotation().cancelRotation();
        if(originalRun!=runGeneration)return;
        var nextAim=buildAim(false);if(originalRun!=runGeneration)return;
        var nextHeading=Direction.fromYaw(Angles.snapToCardinal(services.rotation().yaw()));if(originalRun!=runGeneration)return;
        this.aim=nextAim;this.heading=nextHeading;
        geometry.setHeading(heading);
        if(originalRun!=runGeneration)return;
        FixedRowView rowView=geometry.initialFixedRowView();
        if(originalRun!=runGeneration)return;
        boolean required=geometry.requiresNormalFixedRowView();if(originalRun!=runGeneration)return;
        if(required&&rowView==null){
            String refused=geometry.normalFixedRowViewRefusal();if(originalRun!=runGeneration)return;
            initialCoverage.cancel();this.state=SShapeState.STOPPED;
            this.pauseReason="normal S profile unsupported by observed crop outline: "+refused;
            releaseGuarded(()->originalRun==runGeneration);
            if(originalRun==runGeneration)throw new IllegalStateException(pauseReason);
            return;
        }
        if(required){
            try{nextAim=buildAim(true);}
            catch(NormalProfileRefusal refused){
                if(originalRun!=runGeneration)return;
                initialCoverage.cancel();this.state=SShapeState.STOPPED;this.pauseReason=refused.getMessage();
                releaseGuarded(()->originalRun==runGeneration);
                if(originalRun==runGeneration)throw refused;
                return;
            }
            if(originalRun!=runGeneration)return;
            this.aim=nextAim;
        }
        float nextYaw=rowView==null?(float)heading.toYaw():rowView.yaw();
        float nextPitch=rowView==null?cropPitch():rowView.pitch();if(originalRun!=runGeneration)return;
        var nextAnchor=services.player().position();if(originalRun!=runGeneration)return;
        var nextSide=TurnDirection.fromLaneSide(config.get(FarmingConfig.NETHERWART_STARTING_DIRECTION));if(originalRun!=runGeneration)return;
        this.rowYaw=nextYaw;this.rowPitch=nextPitch;
        this.fixedRowSteering=rowView!=null;
        this.normalReferenceView=required;
        this.sweepAnchor=nextAnchor; // Original supported lane, not a crop-facing path.
        this.startSide = nextSide;
        this.lateralSide = startSide;
        this.initialCoverageEnabled=initialLaneCoverage;
        initialCoverage.start(initialCoverageEnabled,heading,startSide);
        this.aim.setTarget(headingYaw(), rowPitch);
        this.state = SShapeState.PREPARE_TOOL;
        this.forwardTicksLeft = 0;
        this.transitionTarget = null;
        clearRowTransitionObservation();
        this.completedRowTransitions = 0;
        this.completedHeightDrops=0;this.dropTarget=null;
        this.pauseReason = "preparing tool";
        VeltraphLog.details("SShapeRowEngine.start", "captured heading=" + heading + " ("
                + headingYaw() + "°) startSide=" + startSide);
    }

    public void stop() {
        long originalRun=++runGeneration;
        if(supplementalCoverage.plan()!=null)supplementalCoverage.stop();
        if(originalRun!=runGeneration)return;
        supplementalAttempted=false;
        if(dropDriver!=null)dropDriver.stop();
        if(originalRun!=runGeneration)return;
        dropTarget=null;
        initialCoverage.cancel();
        clearRowTransitionObservation();
        this.state = SShapeState.STOPPED;
        this.pauseReason = "stopped";
        releaseAll();
    }

    /** Plot teleport/rewarp invalidates positional turn evidence while preserving the owned view. */
    public void resumeFromObservedPosition() {
        Object previous=setupFrame,frame=new Object();setupFrame=frame;
        try{resumeOriginalObservedPosition();}finally{if(setupFrame==frame)setupFrame=previous;}
    }
    private void resumeOriginalObservedPosition() {
        long originalRun=++runGeneration;
        if(supplementalCoverage.plan()!=null)supplementalCoverage.stop();
        if(originalRun!=runGeneration)return;
        supplementalAttempted=false;
        if(dropDriver!=null)dropDriver.stop();
        if(originalRun!=runGeneration)return;
        dropTarget=null;
        transitionTarget = null;
        clearRowTransitionObservation();
        forwardTicksLeft = 0;
        var nextAnchor=services.player().position();if(originalRun!=runGeneration)return;
        sweepAnchor=nextAnchor;
        boolean switchSide=config.get(FarmingConfig.NETHERWART_SWITCH_DIRECTION_ON_TP);if(originalRun!=runGeneration)return;
        if (switchSide) lateralSide = lateralSide.opposite();
        releaseAll();
        if(originalRun!=runGeneration)return;
        var nextAim=buildAim();if(originalRun!=runGeneration)return;aim=nextAim;
        aim.setTarget(headingYaw(), rowPitch);
        state = SShapeState.AIMING;
        pauseReason = "rechecking lane after return";
    }

    public SShapeState state() {
        return state;
    }

    public Direction heading() {
        return heading;
    }

    public TurnDirection lateralSide() {
        return lateralSide;
    }

    public boolean isReturnSweep() {
        return lateralSide != startSide;
    }

    public String pauseReason() {
        return pauseReason;
    }

    /** Passive normal-profile phase only; native ownership is checked separately by the adapter. */
    public boolean groundedCropWorkActive(){
        return fixedRowSteering&&normalReferenceView&&setupFrame==null
                &&(state==SShapeState.PREPARE_TOOL||state==SShapeState.AIMING
                    ||state==SShapeState.FARMING_SIDEWAYS||state==SShapeState.ROW_TRANSITION_FORWARD);
    }

    /** A live source invocation is transient custody, never a resumable maintenance checkpoint. */
    public boolean rowInvocationActive(){return fixedTickFrame!=null||transitionTickFrame!=null;}

    /** Suspension retires observational arrival credit without moving or changing the route. */
    public void retireRowTransitionObservation(){
        // Retire only retained observation credit or a live source invocation. An unused engine
        // (Vertical/ACE) has no such history and must keep its exact idle maintenance checkpoint.
        if(previousTransitionPose!=null||previousTransitionTick!=Long.MIN_VALUE||rowInvocationActive())
            ++transitionObservationGeneration;
        clearRowTransitionObservation();
    }
    private void clearRowTransitionObservation(){previousTransitionPose=null;previousTransitionTick=Long.MIN_VALUE;}

    public int completedRowTransitions() { return completedRowTransitions; }
    public Vec3 transitionTarget() { return transitionTarget; }
    public boolean initialCoveragePending(){return initialCoverage.pending();}
    public void setInitialCoverageRevocation(Runnable revoked){initialCoverageRevoked=java.util.Objects.requireNonNull(revoked);}
    /** Retire interrupted frames without performing native cleanup; the module owns that cleanup. */
    public void quiesceInitialCoverage(){
        ++runGeneration;
        supplementalCoverage.quiesce();
        initialCoverage.cancel();
        clearRowTransitionObservation();
        state=SShapeState.STOPPED;
        pauseReason="initial lane activation permission revoked";
    }
    public void setInitialCoveragePermission(java.util.function.BooleanSupplier permission){initialCoveragePermission=java.util.Objects.requireNonNull(permission);}
    public void setInitialLaneCoverage(boolean enabled){if(state!=SShapeState.IDLE&&state!=SShapeState.STOPPED)throw new IllegalStateException("Set initial coverage before start");initialCoverageEnabled=enabled;}
    public void setDropDriver(FarmRouteDriver driver){dropDriver=driver;dropHandoff=new FarmDropHandoff(driver);}
    public Vec3 activeDropTarget(){return dropTarget;}
    public int completedHeightDrops(){return completedHeightDrops;}
    private long supplementalGeneration(){return supplementalCoverage.generation();}
    public SupplementalWaterCoverage.State supplementalState(){return supplementalCoverage.state();}
    public int completedSupplementalPasses(){return completedSupplementalPasses;}

    public void tick() {
        if(setupFrame!=null||fixedTickFrame!=null)return;
        if(!fixedRowSteering){tickOriginal();return;}
        Object frame=new Object();fixedTickFrame=frame;
        try{tickOriginal();}finally{if(fixedTickFrame==frame)fixedTickFrame=null;}
    }

    private void tickOriginal() {
        if(setupFrame!=null)return; // A setup callback cannot borrow a partially constructed run.
        long originalRun=runGeneration;boolean originalFixed=fixedRowSteering;
        if (state == SShapeState.IDLE || state == SShapeState.STOPPED) {
            return;
        }
        if(initialCoverage.pending()&&!initialCoveragePermission.getAsBoolean()){
            if(originalFixed&&originalRun!=runGeneration)return;
            quiesceInitialCoverage();
            initialCoverageRevoked.run();
            return;
        }
        if(originalFixed&&originalRun!=runGeneration)return;
        if (aim == null) {
            aim = buildAim();
        }
        geometry.setHeading(heading);
        if(originalFixed&&originalRun!=runGeneration)return;
        SShapeState prev = state;
        VeltraphLog.verbose("SShapeRowEngine.tick", () -> "tick state=" + prev + " side=" + lateralSide);
        if(originalFixed&&originalRun!=runGeneration)return;
        switch (state) {
            case PREPARE_TOOL -> tickPrepareTool();
            case AIMING -> tickAiming();
            case FARMING_SIDEWAYS -> tickFarmingSideways();
            case ROW_TRANSITION_FORWARD -> tickRowTransition();
            case SUPPLEMENTAL_COVERAGE -> tickSupplementalCoverage();
            case INTENTIONAL_DROP -> tickIntentionalDrop();
            case RECOVERING -> tickRecovering();
            default -> { /* IDLE / STOPPED handled above */ }
        }
        if (state != prev) {
            // DETAILS: macro state transition (with the reason recorded by the handler).
            VeltraphLog.details("SShapeRowEngine", prev + " -> " + state + " (" + pauseReason + ")");
        }
    }

    private void tickPrepareTool() {
        if(fixedRowSteering){
            long run=runGeneration;java.util.function.BooleanSupplier same=()->run==runGeneration;
            releaseGuarded(same);if(!same.getAsBoolean())return;pauseReason="preparing tool";
            boolean ground=grounded();if(!same.getAsBoolean())return;services.input().setSneakHeld(!ground);if(!same.getAsBoolean())return;
            boolean ready=toolReady.getAsBoolean();if(!same.getAsBoolean())return;
            if(ready){ground=grounded();if(!same.getAsBoolean())return;if(ground){services.input().setSneakHeld(false);if(!same.getAsBoolean())return;aim.setTarget(headingYaw(),rowPitch);state=SShapeState.AIMING;}}
            return;
        }
        releaseAll();
        pauseReason = "preparing tool";
        services.input().setSneakHeld(!grounded());
        if (toolReady.getAsBoolean() && grounded()) {
            services.input().setSneakHeld(false);
            aim.setTarget(headingYaw(), rowPitch);
            state = SShapeState.AIMING;
        }
    }

    private void tickAiming() {
        if(fixedRowSteering){
            long run=runGeneration;java.util.function.BooleanSupplier same=()->run==runGeneration;
            releaseGuarded(same);if(!same.getAsBoolean())return;pauseReason="panning to row heading/crop";
            retargetAndAim();if(!same.getAsBoolean())return;boolean ready=aimReady();if(!same.getAsBoolean())return;
            if(ready){boolean ground=grounded();if(!same.getAsBoolean())return;if(ground){boolean tool=toolReady.getAsBoolean();if(!same.getAsBoolean())return;if(tool)enterFixedSidewaysOrTransition(run);}}
            return;
        }
        pauseMovement();
        pauseReason = "panning to row heading/crop";
        retargetAndAim();
        if (aimReady() && grounded() && toolReady.getAsBoolean()) {
            enterSidewaysOrTransition();
        }
    }

    private void tickFarmingSideways() {
        long originalRun=runGeneration;boolean fixed=fixedRowSteering;
        java.util.function.BooleanSupplier same=()->originalRun==runGeneration;
        retargetAndAim();
        if(fixed&&!same.getAsBoolean())return;
        boolean ready=toolReady.getAsBoolean();if(fixed&&!same.getAsBoolean())return;
        if (!ready) {
            pauseReason = "tool not selected";
            state = SShapeState.PREPARE_TOOL;
            releaseAll();
            return;
        }
        boolean ground=grounded();if(fixed&&!same.getAsBoolean())return;
        if (!ground) {
            pauseReason = "not on ground";
            state = SShapeState.AIMING;
            if(fixed)releaseGuarded(same);else pauseMovement();
            if(fixed&&!same.getAsBoolean())return;
            services.input().setSneakHeld(true);
            return;
        }
        services.input().setSneakHeld(false);
        if(fixed&&!same.getAsBoolean())return;
        if(fixed){services.input().setJumpHeld(false);if(!same.getAsBoolean())return;}
        boolean large=aimLargeError();if(fixed&&!same.getAsBoolean())return;
        if (large) {
            pauseReason = "large aim error";
            state = SShapeState.AIMING;
            if(fixed)releaseGuarded(same);else pauseMovement();
            return;
        }
        // Row end: can't strafe further this way -> step forward into the next row, then reverse.
        boolean strafe=geometry.canStrafe(lateralSide);if(fixed&&!same.getAsBoolean())return;
        if (!strafe) {
            if(initialCoverage.pending()){
                var pose=services.player().position();if(fixed&&!same.getAsBoolean())return;
                boolean proved=geometry.initialCoverageEndProved(lateralSide,initialCoverage.requiresForwardExitProof());if(fixed&&!same.getAsBoolean())return;
                boolean returnOpen=geometry.canStrafe(lateralSide.opposite());if(fixed&&!same.getAsBoolean())return;
                var decision=initialCoverage.end(lateralSide,pose,proved,returnOpen);
                if(decision==InitialLaneCoverage.Decision.DENY){pauseReason="initial lane end is unproved; stop for fresh geometry";releaseAll();return;}
                if(decision==InitialLaneCoverage.Decision.REVERSE){lateralSide=lateralSide.opposite();releaseAll();if(fixed&&!same.getAsBoolean())return;pauseReason="covering remaining original initial lane before advancing";return;}
            }
            Vec3 landing=geometry.safeDropTarget(lateralSide);if(fixed&&!same.getAsBoolean())return;
            boolean canDrop=config.get(FarmingConfig.DROP_AND_TURN);if(fixed&&!same.getAsBoolean())return;
            if(canDrop&&dropDriver!=null&&landing!=null){
                releaseAll();if(fixed&&!same.getAsBoolean())return;
                services.rotation().cancelRotation();if(fixed&&!same.getAsBoolean())return;
                long time=now();if(fixed&&!same.getAsBoolean())return;
                dropTarget=landing;boolean started=dropHandoff.start(landing,time);if(fixed&&!same.getAsBoolean())return;
                if(started){state=SShapeState.INTENTIONAL_DROP;pauseReason="validated ledge descent";}
                else {time=now();if(fixed&&!same.getAsBoolean())return;String status=dropDriver.status();if(fixed&&!same.getAsBoolean())return;dropTarget=null;state=SShapeState.RECOVERING;enteredRecoveringMs=time;pauseReason="drop route rejected: "+status;}
                return;
            }
            boolean transition=fixed?fixedHasRowTransition(originalRun):hasRowTransition();if(fixed&&!same.getAsBoolean())return;
            if (transition) {
                // Recovered atRowEnd() gating: only commit the forward row step when centred in the
                // block (near-middle), so we don't clip the lane edge stepping into the next row.
                boolean requireMiddle=config.get(FarmingConfig.REQUIRE_NEAR_MIDDLE_FOR_TURN);if(fixed&&!same.getAsBoolean())return;
                boolean near=!requireMiddle||geometry.nearMiddle();if(fixed&&!same.getAsBoolean())return;
                if (!near) {
                    Vec3 centre = geometry.rowEndCenter();
                    if(fixed&&!same.getAsBoolean())return;
                    if (centre != null) {if(fixed)steerToward(centre,grounded(),same);else steerToward(centre);}
                    else if(fixed){guardedStrafe(null,same);guardedForward(false,same);}
                    else { setStrafe(null); setForward(false); }
                    if(fixed&&!same.getAsBoolean())return;
                    boolean available=harvestAvailable();if(fixed&&!same.getAsBoolean())return;
                    if(fixed)guardedBreak(available,same);else driveBreak(available);
                    if(fixed&&!same.getAsBoolean())return;
                    pauseReason = "row end, settling to near-middle before forward";
                    return;
                }
                if(fixed&&!supplementalAttempted) {
                    supplementalAttempted=true;
                    var plan=geometry.supplementalCoveragePlan(lateralSide);
                    if(originalRun!=runGeneration)return;
                    if(plan!=null) {
                        long helperBefore=supplementalCoverage.generation();
                        boolean started=supplementalCoverage.start(plan);
                        if(originalRun!=runGeneration)return;
                        long helperAfter=supplementalCoverage.generation();
                        if(helperAfter!=helperBefore&&helperAfter!=helperBefore+1)return;
                        if(started) {state=SShapeState.SUPPLEMENTAL_COVERAGE;pauseReason="covering loaded out-of-range strip without changing row view";return;}
                        releaseAll();if(!same.getAsBoolean())return;long time=now();if(!same.getAsBoolean())return;
                        state=SShapeState.RECOVERING;enteredRecoveringMs=time;pauseReason="supplemental loaded stance admission refused";return;
                    }
                }
                if(fixed)beginFixedRowTransition(originalRun);else beginRowTransition();
            } else {boolean opposite=geometry.canStrafe(lateralSide.opposite());if(fixed&&!same.getAsBoolean())return;
              if (opposite) {
                lateralSide = lateralSide.opposite();
                pauseReason = "reversed lateral (no forward)";
            } else {
                if(fixed){long time=now();if(!same.getAsBoolean())return;pauseReason="row end, no lane";state=SShapeState.RECOVERING;enteredRecoveringMs=time;releaseGuarded(same);return;}
                pauseReason = "row end, no lane";
                state = SShapeState.RECOVERING;
                enteredRecoveringMs = now();
                releaseAll();
            }
            }
            return;
        }
        // Sideways traversal: strafe + break; forward only if the policy asks (NEVER -> no forward,
        // but the strafe still moves us, so NEVER never freezes the macro).
        boolean harvest=harvestAvailable();if(fixed&&!same.getAsBoolean())return;
        if(fixed){guardedBreak(harvest,same);if(!same.getAsBoolean())return;guardedStrafe(lateralSide,same);if(!same.getAsBoolean())return;}
        else {driveBreak(harvest);setStrafe(lateralSide);}
        if(fixedRowSteering) {
            Vec3 position=services.player().position();
            if(!same.getAsBoolean())return;
            double correction=(sweepAnchor.x()-position.x())*heading.dx()
                    +(sweepAnchor.z()-position.z())*heading.dz();
            guardedForward(correction>.04,same);if(!same.getAsBoolean())return;
            services.input().setBackHeld(correction<-.04);
            if(!same.getAsBoolean())return;
        } else setForward(shouldHoldForwardDuringSweep());
        pauseReason = "farming sideways " + lateralSide + (isReturnSweep() ? " (return)" : "");
    }

    private void tickSupplementalCoverage(){
        long originalRun=runGeneration,originalHelper=supplementalCoverage.generation();
        retargetAndAim();if(originalRun!=runGeneration||originalHelper!=supplementalCoverage.generation())return;
        boolean ready=toolReady.getAsBoolean();if(originalRun!=runGeneration||originalHelper!=supplementalCoverage.generation())return;
        if(!ready) {
            long beforeStop=supplementalGeneration();supplementalCoverage.stop();if(originalRun!=runGeneration||supplementalGeneration()!=beforeStop+1)return;
            state=SShapeState.PREPARE_TOOL;pauseReason="supplemental tool unavailable";releaseAll();return;
        }
        boolean large=aimLargeError();if(originalRun!=runGeneration||originalHelper!=supplementalGeneration())return;
        if(large){supplementalCoverage.pause();if(originalRun!=runGeneration||originalHelper!=supplementalGeneration())return;pauseReason="restoring original fixed view before supplemental movement";return;}
        var result=supplementalCoverage.tick();if(originalRun!=runGeneration||originalHelper!=supplementalCoverage.generation())return;
        pauseReason=supplementalCoverage.reason();
        if(result==SupplementalWaterCoverage.State.COMPLETE) {
            long beforeStop=supplementalGeneration();supplementalCoverage.stop();if(originalRun!=runGeneration||supplementalGeneration()!=beforeStop+1)return;
            completedSupplementalPasses++;
            state=SShapeState.FARMING_SIDEWAYS;beginFixedRowTransition(originalRun);
        }else if(result==SupplementalWaterCoverage.State.BLOCKED||result==SupplementalWaterCoverage.State.IDLE) {
            long beforeStop=supplementalGeneration();supplementalCoverage.stop();if(originalRun!=runGeneration||supplementalGeneration()!=beforeStop+1)return;
            long stopped=supplementalGeneration(),time=now();if(originalRun!=runGeneration||stopped!=supplementalGeneration())return;
            state=SShapeState.RECOVERING;enteredRecoveringMs=time;releaseGuarded(()->originalRun==runGeneration&&stopped==supplementalGeneration());
        }
    }

    private void tickIntentionalDrop(){
        if(fixedRowSteering){tickFixedIntentionalDrop(runGeneration);return;}
        driveBreak(false);
        if(dropDriver==null||dropTarget==null||!geometry.dropLandingValid(dropTarget)){
            if(dropDriver!=null)dropDriver.stop();dropTarget=null;state=SShapeState.RECOVERING;
            enteredRecoveringMs=now();pauseReason="drop landing changed/unloaded";releaseAll();return;
        }
        FarmRouteDriver.State result=dropHandoff.tick(services.player().position(),grounded(),geometry.dropLandingValid(dropTarget),now());
        pauseReason="drop route: "+dropHandoff.status();
        if(result==FarmRouteDriver.State.ACTIVE)return;
        Vec3 expected=dropTarget;dropDriver.stop();dropTarget=null;
        if(result!=FarmRouteDriver.State.ARRIVED||!grounded()
                ||Math.abs(services.player().position().y()-expected.y())>.12
                ||services.player().position().horizontalDistanceTo(expected)>.6){
            state=SShapeState.RECOVERING;enteredRecoveringMs=now();pauseReason="drop has no observed standing arrival";releaseAll();return;
        }
        completedHeightDrops++;lateralSide=lateralSide.opposite();sweepAnchor=expected;
        services.rotation().cancelRotation();aim.setTarget(headingYaw(),rowPitch);
        state=SShapeState.AIMING;pauseReason="observed lower farm landing, restoring crop view";
    }

    private void tickFixedIntentionalDrop(long run){
        java.util.function.BooleanSupplier same=()->run==runGeneration;
        guardedBreak(false,same);if(!same.getAsBoolean())return;
        var driver=dropDriver;var target=dropTarget;boolean valid=driver!=null&&target!=null&&geometry.dropLandingValid(target);if(!same.getAsBoolean())return;
        if(!valid){if(driver!=null)driver.stop();if(!same.getAsBoolean())return;long time=now();if(!same.getAsBoolean())return;dropTarget=null;state=SShapeState.RECOVERING;enteredRecoveringMs=time;pauseReason="drop landing changed/unloaded";releaseGuarded(same);return;}
        var pose=services.player().position();if(!same.getAsBoolean())return;boolean ground=grounded();if(!same.getAsBoolean())return;
        valid=geometry.dropLandingValid(target);if(!same.getAsBoolean())return;long time=now();if(!same.getAsBoolean())return;
        var result=dropHandoff.tick(pose,ground,valid,time);if(!same.getAsBoolean())return;
        String status=dropHandoff.status();if(!same.getAsBoolean())return;pauseReason="drop route: "+status;
        if(result==FarmRouteDriver.State.ACTIVE)return;
        driver.stop();if(!same.getAsBoolean())return;ground=grounded();if(!same.getAsBoolean())return;
        pose=services.player().position();if(!same.getAsBoolean())return;
        if(result!=FarmRouteDriver.State.ARRIVED||!ground||Math.abs(pose.y()-target.y())>.12||pose.horizontalDistanceTo(target)>.6){
            time=now();if(!same.getAsBoolean())return;dropTarget=null;state=SShapeState.RECOVERING;enteredRecoveringMs=time;pauseReason="drop has no observed standing arrival";releaseGuarded(same);return;}
        dropTarget=null;completedHeightDrops++;lateralSide=lateralSide.opposite();sweepAnchor=target;
        services.rotation().cancelRotation();if(!same.getAsBoolean())return;aim.setTarget(headingYaw(),rowPitch);
        state=SShapeState.AIMING;pauseReason="observed lower farm landing, restoring crop view";
    }

    private void tickRowTransition() {
        if(fixedRowSteering){tickFixedRowTransition(runGeneration);return;}
        retargetAndAim();
        if (!toolReady.getAsBoolean()) {
            state = SShapeState.PREPARE_TOOL;
            releaseAll();
            return;
        }
        if (!grounded()) {
            pauseReason = "not on ground";
            state = SShapeState.AIMING;
            pauseMovement();
            services.input().setSneakHeld(true);
            return;
        }
        if (transitionTarget != null) {
            if (services.player().position().horizontalDistanceTo(transitionTarget) <= 0.18) {
                finishRowTransition();
            } else if (now() - transitionStartedMs > config.get(FarmingConfig.STUCK_RECONSIDER_MS)) {
                state = SShapeState.RECOVERING;
                enteredRecoveringMs = now();
                pauseReason = "adjacent row destination not reached";
                releaseAll();
            } else {
                steerToward(transitionTarget);
                driveBreak(harvestAvailable());
                pauseReason = "crossing supported end corridor to " + transitionTarget;
            }
            return;
        }
        services.input().setSneakHeld(false);
        // Step forward one row; keep breaking through the transition (do not fully stop breaking).
        setStrafe(null);
        setForward(true);
        driveBreak(true);
        pauseReason = "row transition forward";
        if (--forwardTicksLeft <= 0) {
            finishRowTransition();
        }
    }

    private void tickFixedRowTransition(long run) {
        if(transitionTickFrame!=null)return; // A provider callback cannot borrow the live arrival invocation.
        Object frame=new Object();transitionTickFrame=frame;
        try{tickFixedRowTransitionOriginal(run,frame);}finally{if(transitionTickFrame==frame)transitionTickFrame=null;}
    }

    private void tickFixedRowTransitionOriginal(long run,Object frame) {
        long originalObservation=transitionObservationGeneration;
        java.util.function.BooleanSupplier same=()->run==runGeneration&&originalObservation==transitionObservationGeneration&&transitionTickFrame==frame;
        retargetAndAim();if(!same.getAsBoolean())return;boolean ready=toolReady.getAsBoolean();if(!same.getAsBoolean())return;
        if(!ready){clearRowTransitionObservation();state=SShapeState.PREPARE_TOOL;releaseGuarded(same);return;}
        boolean ground=grounded();if(!same.getAsBoolean())return;
        if(!ground){clearRowTransitionObservation();state=SShapeState.AIMING;pauseReason="not on ground";releaseGuarded(same);if(!same.getAsBoolean())return;services.input().setSneakHeld(true);return;}
        services.input().setJumpHeld(false);if(!same.getAsBoolean())return;
        var target=transitionTarget;
        if(target!=null){
            var pose=services.player().position();if(!same.getAsBoolean())return;
            var velocity=services.player().horizontalVelocity();if(!same.getAsBoolean())return;
            long tick=services.clock().currentTick();if(!same.getAsBoolean())return;
            boolean consecutive=tick>previousTransitionTick&&tick-previousTransitionTick==1;
            boolean arrived=consecutive&&FixedRowTransitionMotion.arrivalObserved(previousTransitionPose,pose,target,velocity);
            previousTransitionPose=pose;previousTransitionTick=tick;
            if(arrived){finishFixedRowTransition(run);return;}
            long time=now();if(!same.getAsBoolean())return;long bound=config.get(FarmingConfig.STUCK_RECONSIDER_MS);if(!same.getAsBoolean())return;
            if(time-transitionStartedMs>bound){state=SShapeState.RECOVERING;enteredRecoveringMs=time;pauseReason="adjacent row destination not reached";releaseGuarded(same);return;}
            steerToward(target,true,same);if(!same.getAsBoolean())return;
            boolean harvest=harvestAvailable();if(!same.getAsBoolean())return;guardedBreak(harvest,same);if(!same.getAsBoolean())return;
            pauseReason="crossing supported end corridor to "+target;return;
        }
        services.input().setSneakHeld(false);if(!same.getAsBoolean())return;
        guardedStrafe(null,same);if(!same.getAsBoolean())return;guardedForward(true,same);if(!same.getAsBoolean())return;
        guardedBreak(true,same);if(!same.getAsBoolean())return;pauseReason="row transition forward";
        if(--forwardTicksLeft<=0)finishFixedRowTransition(run);
    }

    private void enterFixedSidewaysOrTransition(long run) {
        java.util.function.BooleanSupplier same=()->run==runGeneration;
        boolean strafe=geometry.canStrafe(lateralSide);if(!same.getAsBoolean())return;
        if(strafe){state=SShapeState.FARMING_SIDEWAYS;return;}
        var movable=geometry.movableLateral();if(!same.getAsBoolean())return;
        if(movable==TurnDirection.LEFT||movable==TurnDirection.RIGHT){lateralSide=movable;state=SShapeState.FARMING_SIDEWAYS;return;}
        boolean transition=fixedHasRowTransition(run);if(!same.getAsBoolean())return;
        if(transition)beginFixedRowTransition(run);
        else{long time=now();if(!same.getAsBoolean())return;state=SShapeState.RECOVERING;enteredRecoveringMs=time;pauseReason="no movable lane at start";}
    }

    private void tickRecovering() {
        if(fixedRowSteering){
            long run=runGeneration;java.util.function.BooleanSupplier same=()->run==runGeneration;
            releaseGuarded(same);if(!same.getAsBoolean())return;long time=now();if(!same.getAsBoolean())return;
            long bound=config.get(FarmingConfig.STUCK_RECONSIDER_MS);if(!same.getAsBoolean())return;
            if(time-enteredRecoveringMs<bound)return;
            var movable=geometry.movableLateral();if(!same.getAsBoolean())return;
            boolean retry=movable!=TurnDirection.FORWARD;
            if(!retry){retry=geometry.canStepForward();if(!same.getAsBoolean())return;}
            if(retry)state=SShapeState.AIMING;return;
        }
        releaseAll();
        long stuckMs = now() - enteredRecoveringMs;
        if (stuckMs > config.get(FarmingConfig.STUCK_RECONSIDER_MS)) {
            // Reconsider Direction (recovered failsafe): re-probe the movable lateral direction.
            pauseReason = "Reconsidering Direction";
            TurnDirection movable = geometry.movableLateral();
            if (movable == TurnDirection.LEFT || movable == TurnDirection.RIGHT) {
                lateralSide = movable;
            }
            enteredRecoveringMs = now();
        } else {
            pauseReason = "recovering (stuck)";
        }
        if (geometry.canStrafe(lateralSide) || geometry.canStrafe(lateralSide.opposite())
                || geometry.canStepForward()) {
            state = SShapeState.AIMING;
        }
    }

    private void enterSidewaysOrTransition() {
        if (geometry.canStrafe(lateralSide)) {
            state = SShapeState.FARMING_SIDEWAYS;
            return;
        }
        TurnDirection movable = geometry.movableLateral();
        if (movable == TurnDirection.LEFT || movable == TurnDirection.RIGHT) {
            lateralSide = movable;
            state = SShapeState.FARMING_SIDEWAYS;
        } else if (hasRowTransition()) {
            beginRowTransition();
        } else {
            pauseReason = "no movable lane at start";
            state = SShapeState.RECOVERING;
            enteredRecoveringMs = now();
        }
    }

    private void beginRowTransition() {
        transitionTarget = geometry.rowTransitionTarget(lateralSide);
        transitionStartedMs = now();
        forwardTicksLeft = Math.max(1, config.get(FarmingConfig.ROW_TRANSITION_FORWARD_TICKS));
        // Publish both corridor axes immediately while retaining the same row view.
        if(transitionTarget!=null)steerToward(transitionTarget);
        else {setStrafe(null);services.input().setBackHeld(false);setForward(true);}
        driveBreak(harvestAvailable());
        state = SShapeState.ROW_TRANSITION_FORWARD;
        pauseReason = "row end -> forward";
    }

    private void beginFixedRowTransition(long originalRun) {
        long originalObservation=transitionObservationGeneration;
        java.util.function.BooleanSupplier same=()->originalRun==runGeneration&&originalObservation==transitionObservationGeneration;
        if(!same.getAsBoolean())return;
        var target=geometry.rowTransitionTarget(lateralSide);if(!same.getAsBoolean())return;
        long started=now();if(!same.getAsBoolean())return;
        int ticks=Math.max(1,config.get(FarmingConfig.ROW_TRANSITION_FORWARD_TICKS));if(!same.getAsBoolean())return;
        transitionTarget=target;clearRowTransitionObservation();transitionStartedMs=started;forwardTicksLeft=ticks;
        state=SShapeState.ROW_TRANSITION_FORWARD;pauseReason="row end -> forward";
        if(target!=null)steerToward(target,grounded(),same);
        else {guardedStrafe(null,same);if(!same.getAsBoolean())return;services.input().setBackHeld(false);if(!same.getAsBoolean())return;guardedForward(true,same);}
        if(!same.getAsBoolean())return;boolean harvest=harvestAvailable();if(!same.getAsBoolean())return;
        guardedBreak(harvest,same);
    }

    private boolean hasRowTransition() {
        return geometry.rowTransitionTarget(lateralSide) != null
                || (!geometry.requiresObservedRowDestination() && geometry.canStepForward());
    }

    private boolean fixedHasRowTransition(long run) {
        if(run!=runGeneration)return false;
        var target=geometry.rowTransitionTarget(lateralSide);if(run!=runGeneration)return false;
        if(target!=null)return true;
        boolean required=geometry.requiresObservedRowDestination();if(run!=runGeneration||required)return false;
        boolean forward=geometry.canStepForward();return run==runGeneration&&forward;
    }

    private void finishRowTransition() {
        if(fixedRowSteering){finishFixedRowTransition(runGeneration);return;}
        completedRowTransitions++;
        sweepAnchor=transitionTarget==null?services.player().position():transitionTarget;
        supplementalAttempted=false;
        transitionTarget = null;
        lateralSide = lateralSide.opposite();
        setForward(false);
        services.input().setBackHeld(false);
        services.input().setSneakHeld(false);
        state = SShapeState.FARMING_SIDEWAYS;
        // Start the return without an idle tick; completion came from the observed player position.
        setStrafe(lateralSide);
        pauseReason = "reached adjacent row, reversed lateral";
    }

    private void finishFixedRowTransition(long originalRun) {
        long originalObservation=transitionObservationGeneration;
        java.util.function.BooleanSupplier same=()->originalRun==runGeneration&&originalObservation==transitionObservationGeneration;
        var anchor=transitionTarget==null?services.player().position():transitionTarget;if(!same.getAsBoolean())return;
        completedRowTransitions++;sweepAnchor=anchor;supplementalAttempted=false;transitionTarget=null;clearRowTransitionObservation();
        lateralSide=lateralSide.opposite();state=SShapeState.FARMING_SIDEWAYS;
        guardedForward(false,same);if(!same.getAsBoolean())return;services.input().setBackHeld(false);
        if(!same.getAsBoolean())return;services.input().setSneakHeld(false);if(!same.getAsBoolean())return;
        guardedStrafe(lateralSide,same);if(!same.getAsBoolean())return;
        pauseReason="reached adjacent row, reversed lateral";
    }

    /** Hold ordinary corridor keys; coasting decisions use observed momentum, never a clock pulse. */
    private void steerToward(Vec3 target) {
        long originalRun=runGeneration;boolean precise=grounded();if(originalRun!=runGeneration)return;
        steerToward(target,precise,()->originalRun==runGeneration);
    }
    private void steerToward(Vec3 target,boolean preciseGrounded,java.util.function.BooleanSupplier same) {
        long originalObservation=transitionObservationGeneration;
        var originalSame=same;
        same=()->originalSame.getAsBoolean()&&originalObservation==transitionObservationGeneration;
        if(!same.getAsBoolean())return;
        Vec3 position = services.player().position();if(!same.getAsBoolean())return;
        if(fixedRowSteering) {
            var keys=FixedViewKeySteering.toward(position,target,rowYaw);
            if(preciseGrounded&&state==SShapeState.ROW_TRANSITION_FORWARD){
                var velocity=services.player().horizontalVelocity();if(!same.getAsBoolean())return;
                keys=FixedRowTransitionMotion.coastingKeys(position,target,rowYaw,velocity);
            }
            // Supplemental dry-cap/channel alignment keeps its separate existing precision policy.
            boolean supplementalPulse=preciseGrounded&&state==SShapeState.SUPPLEMENTAL_COVERAGE
                    &&position.horizontalDistanceTo(target)<.85&&services.clock().currentTick()%2==0;
            if(!same.getAsBoolean())return;
            guardedForward(!supplementalPulse&&keys.forward(),same);if(!same.getAsBoolean())return;
            services.input().setBackHeld(!supplementalPulse&&keys.back());if(!same.getAsBoolean())return;
            guardedStrafe(supplementalPulse?null:keys.left()?TurnDirection.LEFT:keys.right()?TurnDirection.RIGHT:null,same);if(!same.getAsBoolean())return;
            services.input().setSneakHeld(false);
            return;
        }
        double dx = target.x() - position.x(), dz = target.z() - position.z();
        double forward=dx*heading.dx()+dz*heading.dz();
        Direction right=heading.rotateYClockwise();
        double sideways=dx*right.dx()+dz*right.dz();
        setForward(forward > 0.12);
        services.input().setBackHeld(forward < -0.12);
        setStrafe(sideways > 0.12 ? TurnDirection.RIGHT : sideways < -0.12 ? TurnDirection.LEFT : null);
        services.input().setSneakHeld(Math.hypot(dx, dz) < 0.85);
    }

    // ---- Aim ----

    private static final class NormalProfileRefusal extends IllegalStateException{
        private NormalProfileRefusal(String why){super(why);}
    }
    private FarmAimController buildAim(){return buildAim(normalReferenceView);}
    private FarmAimController buildAim(boolean normal) {
        FarmAimController.Settings settings = new FarmAimController.Settings(
                config.get(FarmingConfig.ROTATION_MAX_YAW_DEG_PER_TICK).floatValue(),
                config.get(FarmingConfig.ROTATION_MAX_PITCH_DEG_PER_TICK).floatValue(),
                config.get(FarmingConfig.ROTATION_ACCELERATION),
                config.get(FarmingConfig.ROTATION_TOLERANCE_DEG).floatValue(),
                config.get(FarmingConfig.ROTATION_PAUSE_ERROR_DEG).floatValue(),
                config.get(FarmingConfig.ROTATION_USE_MOUSE_GCD),
                config.get(FarmingConfig.ROTATION_MOUSE_SENSITIVITY),
                false, // This S-shape session holds its row view; other farm aim policies are unchanged.
                config.get(FarmingConfig.ROTATION_JITTER_MAX_DEG).floatValue(),
                config.get(FarmingConfig.ROTATION_JITTER_MIN_INTERVAL_TICKS),
                config.get(FarmingConfig.ROTATION_JITTER_MAX_INTERVAL_TICKS),
                config.get(FarmingConfig.ROTATION_JITTER_HOLD_TICKS));
        if(!normal)return new FarmAimController(settings,new Random());
        float requested=Math.min(.1f,settings.toleranceDeg());
        if(!Float.isFinite(requested)||requested<=0)throw new NormalProfileRefusal("normal S profile requires finite positive settling tolerance");
        float gcd=settings.useMouseGcd()?(float)(Math.pow(settings.mouseSensitivity()*.6+.2,3)*1.2):0f;
        if(settings.useMouseGcd()&&(!Float.isFinite(gcd)||gcd<=0))throw new NormalProfileRefusal("normal S profile requires finite positive mouse step");
        float settled=Math.max(requested,gcd>0?gcd*.5f+4f*Math.ulp(90f):0f);
        if(settled>.2f)throw new NormalProfileRefusal("normal S profile mouse half-step cannot settle inside reference window: "+settled);
        var precise=new FarmAimController.Settings(settings.maxYawPerTick(),settings.maxPitchPerTick(),settings.acceleration(),
            settled,settings.pauseErrorDeg(),settings.useMouseGcd(),settings.mouseSensitivity(),settings.jitter(),settings.jitterMaxDeg(),
            settings.jitterMinIntervalTicks(),settings.jitterMaxIntervalTicks(),settings.jitterHoldTicks());
        return new FarmAimController(precise,new Random(),requested);
    }

    /** Smoothly restore the single session row view; crop changes never retarget it. */
    private void retargetAndAim() {
        long originalRun=runGeneration;var originalAim=aim;float wantedYaw=headingYaw(),wantedPitch=rowPitch;
        originalAim.retarget(wantedYaw,wantedPitch);
        float curYaw = services.rotation().yaw();
        if(originalRun!=runGeneration||originalAim!=aim)return;
        float curPitch = services.rotation().pitch();
        if(originalRun!=runGeneration||originalAim!=aim)return;
        float[] next = originalAim.step(curYaw, curPitch);
        if(originalRun!=runGeneration||originalAim!=aim)return;
        services.rotation().aim(next[0], next[1]);
        if(originalRun!=runGeneration||originalAim!=aim)return;
        // DETAILS: aim target vs current (the row-aim decision the live test needs to see).
        VeltraphLog.details("SShapeRowEngine.aim", () -> String.format(
                "target=%.1f/%.1f current=%.1f/%.1f err=%.1f", headingYaw(), rowPitch,
                curYaw, curPitch, aim.aimError(curYaw, curPitch)));
    }

    private float headingYaw() {
        return rowYaw;
    }

    /** Initial pitch toward the selected row; called only at a fresh complete start. */
    private float cropPitch() {
        Vec3 pos = services.player().position();
        Vec3 eye = new Vec3(pos.x(), pos.y() + geometry.eyeHeight(), pos.z());
        return CropSightline.pitchTo(eye, geometry.targetVec());
    }

    private boolean aimReady() {
        if(fixedRowSteering)return fixedAimCondition(runGeneration,false);
        return aim.withinTolerance(services.rotation().yaw(), services.rotation().pitch());
    }

    private boolean aimLargeError() {
        if(fixedRowSteering)return fixedAimCondition(runGeneration,true);
        return aim.isLargeError(services.rotation().yaw(), services.rotation().pitch());
    }

    private boolean fixedAimCondition(long run,boolean large) {
        var original=aim;if(run!=runGeneration)return false;
        float yaw=services.rotation().yaw();if(run!=runGeneration||original!=aim)return false;
        float pitch=services.rotation().pitch();if(run!=runGeneration||original!=aim)return false;
        return large?original.isLargeError(yaw,pitch):original.withinTolerance(yaw,pitch)
            &&(!normalReferenceView||pitch>=2.8f&&pitch<=3.3f);
    }

    public float aimError() {
        return aim == null ? 0f : aim.aimError(services.rotation().yaw(), services.rotation().pitch());
    }

    public float targetYaw() {
        return aim == null ? headingYaw() : aim.targetYaw();
    }

    public float targetPitch() {
        return aim == null ? rowPitch : aim.targetPitch();
    }

    // ---- Inputs ----

    private boolean shouldHoldForwardDuringSweep() {
        HoldForwardPolicy policy = config.get(holdPolicyKey);
        return switch (policy) {
            case ALWAYS -> true;
            case TAP -> services.clock().currentTick() % 2L == 0L;
            case NEVER -> false; // strafe carries the motion; NEVER does not freeze the macro
        };
    }

    private void setForward(boolean held) {
        if (held != heldForward) {
            VeltraphLog.verbose("SShapeRowEngine.input", "forward " + (held ? "HELD" : "released"));
        }
        heldForward = held;
        services.input().setForwardHeld(held);
    }

    private void guardedForward(boolean held,java.util.function.BooleanSupplier same) {
        if(!same.getAsBoolean())return;
        if(held!=heldForward)VeltraphLog.verbose("SShapeRowEngine.input","forward "+(held?"HELD":"released"));
        if(!same.getAsBoolean())return;
        services.input().setForwardHeld(held);if(!same.getAsBoolean())return;
        heldForward=held;
    }
    private void guardedStrafe(TurnDirection side,java.util.function.BooleanSupplier same) {
        if(!same.getAsBoolean())return;boolean left=side==TurnDirection.LEFT,right=side==TurnDirection.RIGHT;
        if(left!=heldLeft||right!=heldRight)VeltraphLog.verbose("SShapeRowEngine.input","strafe "+(left?"LEFT":right?"RIGHT":"none"));
        if(!same.getAsBoolean())return;
        services.input().setLeftHeld(left);if(!same.getAsBoolean())return;
        services.input().setRightHeld(right);if(!same.getAsBoolean())return;
        heldLeft=left;heldRight=right;
    }
    private void guardedBreak(boolean desired,java.util.function.BooleanSupplier same) {
        if(!same.getAsBoolean())return;
        long nextCounter=0;boolean hold=false;
        if(desired){int cadence=config.get(FarmingConfig.ATTACK_CADENCE_TICKS);if(!same.getAsBoolean())return;
            hold=AttackCadence.shouldHold(breakRunTick,cadence);nextCounter=breakRunTick+1;}
        if(hold!=heldBreak)VeltraphLog.details("SShapeRowEngine.break","breaking "+(hold?"ON":"off"));
        if(!same.getAsBoolean())return;
        services.breaking().setBreaking(hold);if(!same.getAsBoolean())return;
        breakRunTick=nextCounter;heldBreak=hold;
    }
    private void releaseGuarded(java.util.function.BooleanSupplier same) {
        if(!same.getAsBoolean())return;services.input().setForwardHeld(false);
        if(!same.getAsBoolean())return;services.input().setBackHeld(false);
        if(!same.getAsBoolean())return;services.input().setLeftHeld(false);
        if(!same.getAsBoolean())return;services.input().setRightHeld(false);
        if(!same.getAsBoolean())return;services.input().setSneakHeld(false);
        if(!same.getAsBoolean())return;services.input().setJumpHeld(false);
        if(!same.getAsBoolean())return;services.input().setSprintHeld(false);
        if(!same.getAsBoolean())return;services.breaking().setBreaking(false);
        if(!same.getAsBoolean())return;
        breakRunTick=0;heldBreak=heldForward=heldLeft=heldRight=false;
    }

    private void setStrafe(TurnDirection side) {
        boolean left = side == TurnDirection.LEFT;
        boolean right = side == TurnDirection.RIGHT;
        if (left != heldLeft || right != heldRight) {
            VeltraphLog.verbose("SShapeRowEngine.input",
                    "strafe " + (left ? "LEFT" : right ? "RIGHT" : "none"));
        }
        heldLeft = left;
        heldRight = right;
        services.input().setLeftHeld(heldLeft);
        services.input().setRightHeld(heldRight);
    }

    /**
     * Drive the attack key for an active break, applying the configured cadence: continuous hold by
     * default ({@code ATTACK_CADENCE_TICKS == 0}), or a one-tick release every N ticks so the client
     * re-issues a fresh break swing. Resets the cadence run-counter whenever breaking stops.
     */
    private boolean harvestAvailable(){
        // Normal S sweeps continuously at the captured view, including through growing crops.
        // The native target guard still restricts every destroy attempt to the selected crop/soil.
        if(fixedRowSteering&&normalReferenceView)return true;
        return fixedRowSteering?geometry.fixedViewHarvestTargetAvailable():geometry.harvestTargetAvailable();
    }

    private void driveBreak(boolean desired) {
        if (!desired) {
            breakRunTick = 0;
            setBreaking(false);
            return;
        }
        int cadence = config.get(FarmingConfig.ATTACK_CADENCE_TICKS);
        boolean hold = AttackCadence.shouldHold(breakRunTick, cadence);
        breakRunTick++;
        setBreaking(hold);
    }

    private void setBreaking(boolean breaking) {
        if (breaking != heldBreak) {
            VeltraphLog.details("SShapeRowEngine.break", "breaking " + (breaking ? "ON" : "off"));
        }
        heldBreak = breaking;
        services.breaking().setBreaking(breaking);
    }

    private void pauseMovement() {
        setStrafe(null);
        setForward(false);
        services.input().setBackHeld(false);
        services.input().setSneakHeld(false);
        breakRunTick = 0;
        setBreaking(false);
    }

    private void releaseAll() {
        if(fixedRowSteering){long run=runGeneration;releaseGuarded(()->run==runGeneration);return;}
        services.input().releaseAllMovement();
        breakRunTick = 0;
        setBreaking(false);
        heldForward = heldLeft = heldRight = false;
    }

    private boolean grounded() {
        return services.player().onGround();
    }

    private long now() {
        return services.clock().nowMillis();
    }

    /** Live diagnostics line for {@code /vdebug macro}. */
    public String diagnostics() {
        return String.format(
                "state=%s side=%s%s heading=%s head=%.0f tgt=%.0f/%.0f err=%.1f%n"
                + "  keys[F=%b L=%b R=%b break=%b] nearMid=%b inFront=%b strafeOK=%b stepFwd=%b movable=%s%n"
                + "  pause=%s",
                state, lateralSide, isReturnSweep() ? "(ret)" : "", heading,
                headingYaw(), targetYaw(), targetPitch(), aimError(),
                heldForward, heldLeft, heldRight, heldBreak,
                geometry.nearMiddle(), geometry.blockInFront(), geometry.canStrafe(lateralSide),
                geometry.canStepForward(), geometry.movableLateral(), pauseReason);
    }

    /** Passive exact maintenance checkpoint. No services, geometry, clocks or planner callbacks run. */
    public dev.veltraph.client.farming.module.FarmMaintenanceState maintenanceState() {
        return dev.veltraph.client.farming.module.FarmMaintenanceState.of(this,
                "config", config,
                "services", services,
                "geometry", geometry instanceof dev.veltraph.client.farming.movement.FarmGeometry actual ? actual.maintenanceState() : dev.veltraph.client.farming.module.FarmMaintenanceState.identity(geometry),
                "holdPolicyKey", holdPolicyKey,
                "toolReady", toolReady,
                "state", state,
                "heading", heading,
                "lateralSide", lateralSide,
                "startSide", startSide,
                "forwardTicksLeft", forwardTicksLeft,
                "transitionTarget", transitionTarget,
                "previousTransitionPose", previousTransitionPose,
                "previousTransitionTick", previousTransitionTick,
                "transitionObservationGeneration", transitionObservationGeneration,
                "transitionTickFrame", transitionTickFrame,
                "fixedTickFrame", fixedTickFrame,
                "transitionStartedMs", transitionStartedMs,
                "completedRowTransitions", completedRowTransitions,
                "initialCoverage",initialCoverage.maintenanceState(),"initialCoverageEnabled",initialCoverageEnabled,"initialCoveragePermission",initialCoveragePermission,"initialCoverageRevoked",initialCoverageRevoked,
                "dropDriver", dropDriver,
                "dropHandoff", dropHandoff == null ? null : dropHandoff.maintenanceState(),
                "dropTarget", dropTarget,
                "completedHeightDrops", completedHeightDrops,
                "enteredRecoveringMs", enteredRecoveringMs,
                "pauseReason", pauseReason,
                "rowPitch", rowPitch,
                "rowYaw", rowYaw,"fixedRowSteering",fixedRowSteering,"normalReferenceView",normalReferenceView,"sweepAnchor",sweepAnchor,
                "supplementalCoverage",supplementalCoverage.maintenanceState(),"supplementalAttempted",supplementalAttempted,"runGeneration",runGeneration,"completedSupplementalPasses",completedSupplementalPasses,"setupFrame",setupFrame,
                "aim", aim == null ? null : aim.maintenanceState(),
                "heldForward", heldForward,
                "heldLeft", heldLeft,
                "heldRight", heldRight,
                "heldBreak", heldBreak,
                "breakRunTick", breakRunTick);
    }
}
