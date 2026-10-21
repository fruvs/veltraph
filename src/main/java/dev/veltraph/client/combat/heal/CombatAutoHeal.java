package dev.veltraph.client.combat.heal;

import dev.veltraph.client.config.ConfigService;
import dev.veltraph.client.config.GhostConfig;
import dev.veltraph.client.log.VeltraphLog;

/**
 * Local, opt-in Hyperion auto-heal assist (Batch 6 — Slayers/Ghost). Owns the pure {@link HyperionAutoHeal}
 * FSM and drives the {@link HealServices} seam. When enabled and in SkyBlock, it watches the player's
 * health and, on a low-health trigger, looks down → checks clearance → casts the Hyperion AOE heal →
 * waits → restores the camera. Source: {@code reports/_spec_parts/GhostAutoHeal.md}.
 *
 * <p>Tick-driven service (like the anti-staff watcher), not a {@code ModuleManager} macro, so it runs
 * alongside manual slayer/ghost grinding. <b>Local only</b>: pitch change + use-item; no pathfinding, no
 * combat targeting, no network.
 */
public final class CombatAutoHeal {

    private final ConfigService config;
    private final HealServices services;
    private final HealSourceEndLedger sourceEnd;
    private final java.util.function.LongSupplier clock;
    private boolean cancelledUntilRecovery;
    private final HealSourceRepeat sourceRepeat=new HealSourceRepeat();
    private boolean repeatPending;
    private long emittedAt;
    private final HealHyperionWatchdog watchdog=new HealHyperionWatchdog();
    private final HealSourceBufferPhases bufferPhases=new HealSourceBufferPhases();
    private HealSourceRepeat.Decision bufferedDecision;
    private long lastSourceTick=Long.MIN_VALUE;
    private HealServices.SourceTickScope lastSourceScope;
    private final HyperionAutoHeal runtime = new HyperionAutoHeal(true);

    public CombatAutoHeal(ConfigService config, HealServices services) {
        this(config,services,SharedHealSourceEnd.processLedger(),System::currentTimeMillis);
    }

    /** Deterministic test seam; production retains the same process ledger and system clock. */
    CombatAutoHeal(ConfigService config, HealServices services, HealSourceEndLedger sourceEnd,
                   java.util.function.LongSupplier clock) {
        this.config = config;
        this.services = services == null ? HealServices.NONE : services;
        this.sourceEnd = java.util.Objects.requireNonNull(sourceEnd);
        this.clock = java.util.Objects.requireNonNull(clock);
    }

    public boolean enabled() {
        return config.get(GhostConfig.GHOST_AUTO_HEAL_ENABLED);
    }

    /** Advance one tick. {@code inSkyBlock} gates the assist (with the disable/leave path still restoring). */
    public void tick(boolean inSkyBlock) {
        tick(inSkyBlock,false);
    }

    /** Dedicated workflows must claim only after this assist's existing snapshot has been restored. */
    public boolean hasVerifiedCustody() { return ownsControl() && services.ownsSnapshot() && services.custodyValid(); }

    public boolean ownsControl() { return runtime.state()!=HealState.IDLE; }

    /** Allows the root to finish a temporary equipment lease before the heal captures its slot. */
    public boolean shouldAcquireControl(boolean inSkyBlock) {
        if (cancelledUntilRecovery || !enabled() || !inSkyBlock || !services.available()) return false;
        if(services.usesSourceReadiness())return runtime.state()==HealState.IDLE&&sourceEnd.mayAcquire(clock.getAsLong())&&services.sourceReady(false,false);
        return runtime.wouldStart(new HealInputs(true,true,services.hasHyperion(),
                services.healthPct(),false,clock.getAsLong()),HealTuning.fromConfig(config));
    }

    /** Root routes this exclusive control check before any camera, item use or restoration action. */
    public void tick(boolean inSkyBlock,boolean anotherOwnsHealing) {
        if(anotherOwnsHealing)return;
        if(cancelledUntilRecovery){
            if(!enabled() || (services.usesSourceReadiness()?services.sourceHealthRecovered():services.healthPct()>HealTuning.fromConfig(config).activatePct()))cancelledUntilRecovery=false;
            else return;
        }
        if (runtime.state()!=HealState.IDLE && !services.custodyValid()) { reset(); cancelledUntilRecovery=true; return; }
        services.tickCamera();
        if (runtime.state()!=HealState.IDLE && !services.custodyValid()) { reset(); cancelledUntilRecovery=true; return; }
        boolean enabled = enabled();
        // Cancellation precedes deduplication: a second call in the same frame must release lost custody.
        if(enabled&&services.available()&&inSkyBlock&&services.usesSourceReadiness()){
            long nativeTick=services.sourceClientTick();var scope=services.sourceTickScope();
            if(nativeTick!=Long.MIN_VALUE){
                if(nativeTick==lastSourceTick&&sameSourceScope(scope,lastSourceScope))return;
                lastSourceTick=nativeTick;lastSourceScope=scope;
            }
        }

        // Cheap path: nothing to do when off and not mid-heal.
        if (!enabled && runtime.state() == HealState.IDLE) {
            return;
        }
        HealInputs in = new HealInputs(
                enabled,
                services.available() && inSkyBlock,
                services.hasHyperion(),
                services.healthPct(),
                services.clearAbove(),
                clock.getAsLong(), services.cameraReady(), services.restorationComplete());
        HealAction action=HealAction.NONE;
        if(services.usesSourceReadiness()&&runtime.state()==HealState.IDLE){
            if(enabled&&in.available()&&sourceEnd.mayAcquire(in.nowMillis())&&services.sourceReady(false,false)){
                sourceRepeat.beginAttempt();watchdog.reset();bufferPhases.reset();bufferedDecision=null;repeatPending=false;action=runtime.beginSourceAttempt(in.nowMillis());
            }else action=HealAction.NONE;
        }else{
            boolean finishedSourceBuffer=false;
            if(enabled&&in.available()&&services.usesSourceReadiness()&&runtime.state()==HealState.WAIT&&repeatPending){
                boolean positionReceipt=services.sourcePostUsePositionReceived();
                var buffer=watchdog.read(in.nowMillis(),services.sourceHyperionLookDown(),services.custodyValid(),positionReceipt);
                if(bufferPhases.inBuffer()&&positionReceipt&&services.sourceHyperionLookDown())bufferPhases.posePacket();
                if(bufferPhases.inBuffer()&&buffer==HealHyperionWatchdog.Decision.TIMED_OUT){
                    services.cancelOwnedHealRotation();
                    if(!services.custodyValid()){reset();cancelledUntilRecovery=true;return;}
                    VeltraphLog.basic("AutoHeal","Hyperion heal took too long, cancelling.");
                }
                int bufferDelay=bufferPhases.inBuffer()&&buffer!=HealHyperionWatchdog.Decision.WAIT&&!services.sourceHyperionLookDown()
                        ?services.sampleSourcePhaseDelay():0;
                var step=bufferPhases.tick(buffer!=HealHyperionWatchdog.Decision.WAIT,bufferDelay);
                if(step==HealSourceBufferPhases.Step.WAIT)return;
                if(step==HealSourceBufferPhases.Step.SWITCH_DECISION){
                    var mode=config.get(GhostConfig.AUTO_HEAL_STACKING);
                    boolean normalReady=mode!=HealStackingMode.SPAM&&services.prospectiveSourceReady();
                    bufferedDecision=sourceRepeat.afterUse(mode,normalReady);
                    if(bufferedDecision==HealSourceRepeat.Decision.FINISH&&!services.restoreSourceSavedSlot()){reset();cancelledUntilRecovery=true;return;}
                    bufferPhases.decisionMade(services.sampleSourcePhaseDelay());return;
                }
                repeatPending=false;sourceEnd.endBeforeRepeatOrRelease(in.nowMillis());
                if(bufferedDecision==HealSourceRepeat.Decision.HEAL_AGAIN&&services.prepareSourceRepeat()
                        &&runtime.continueSourceAttempt())return;
                action=runtime.finishSourceBuffer();finishedSourceBuffer=true;
            }
            if(!finishedSourceBuffer)action=runtime.tick(in,HealTuning.fromConfig(config));
        }

        switch (action) {
            case LOOK_DOWN -> {
                services.lookDown();
                VeltraphLog.basic("AutoHeal", services.usesSourceReadiness()
                        ? "source readiness passed for raw health threshold; preparing owned heal"
                        : "low health (" + (int) in.healthPct() + "%) — look down + heal");
            }
            case CAST -> {
                var outcome = services.castWithOutcome();
                runtime.resolveCast(outcome);
                if (outcome == HealCastOutcome.EMITTED){
                    emittedAt=in.nowMillis();watchdog.emitted(in.nowMillis());bufferPhases.emitted();bufferedDecision=null;repeatPending=services.usesSourceReadiness();
                    VeltraphLog.basic("AutoHeal", "used selected heal item (#" + runtime.healCount() + ")");
                }
            }
            case RESTORE -> {
                services.restoreLook();
                if (runtime.lastAttemptBlocked()) {
                    VeltraphLog.basic("AutoHeal", "blocked by a block — finishing without casting");
                }
            }
            case NONE -> { /* idle / waiting */ }
        }
    }

    private static boolean sameSourceScope(HealServices.SourceTickScope a,HealServices.SourceTickScope b){
        return a==b||(a!=null&&b!=null&&a.player()==b.player()&&a.world()==b.world()&&a.connection()==b.connection());
    }

    public void reset() {
        services.discardSnapshot();
        runtime.reset();
        cancelledUntilRecovery=false;repeatPending=false;sourceRepeat.beginAttempt();watchdog.reset();bufferPhases.reset();bufferedDecision=null;
    }

    // ---- read API (status / HUD / debug) ----

    public HealState state() {
        return runtime.state();
    }

    public int healCount() {
        return runtime.healCount();
    }

    public void setEnabled(boolean on) {
        config.set(GhostConfig.GHOST_AUTO_HEAL_ENABLED, on);
        if (!on) {
            // keep state for an in-flight restore; the next tick will restore the camera if needed
            VeltraphLog.basic("AutoHeal", "disabled");
        } else {
            VeltraphLog.basic("AutoHeal", "enabled");
        }
    }

    /** One-line status for {@code /vdebug heal}. */
    public String status() {
        return "Auto-Heal: " + (enabled() ? "ON" : "OFF")
                + " | state=" + state()
                + " | " + (services.usesSourceReadiness()?HealRawThresholds.fromConfig(config).status()+" [emergency parent route unbound]":
                    "activate<=" + config.get(GhostConfig.GHOST_HEALTH_TO_ACTIVATE_PCT) + "%"
                    + " (emergency<=" + config.get(GhostConfig.GHOST_EMERGENCY_HEALTH_PCT) + "%)")
                + " | weapon=" + config.get(GhostConfig.GHOST_HEAL_WEAPON_NAME)
                + " | heals=" + healCount();
    }
}
