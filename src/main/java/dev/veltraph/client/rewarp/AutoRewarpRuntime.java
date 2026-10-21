package dev.veltraph.client.rewarp;

import dev.veltraph.client.log.VeltraphLog;
import dev.veltraph.client.world.Vec3;
import java.util.Objects;

/**
 * Local command recovery with observed arrival, bounded settling and guarded resumption.
 * Source: ReturnToLocationController's failed-travel rewarp fallback, STOP_FLYING_AND_CHECK_SPOT
 * actual-position/motion checks and ROTATE_PROPERLY completion before handing control back.
 * This dispatch does not reconstruct source plot selection or salted retry decisions.
 */
public final class AutoRewarpRuntime {
    private static final String TAG = "AutoRewarp";
    // Explicit local observation defaults, not recovered teleport/PID constants.
    private static final double TELEPORT_STEP = 3.0, SETTLE_DRIFT = .25, MAX_SETTLE_SPEED = .1;

    public enum Return { ACTIVE, ARRIVED, FAILED, CANCELLED }

    public interface Actions {
        void suspendFarming();
        /** Sending a command is only a request, never arrival evidence. */
        boolean sendRewarp();
        void resumeFarming();
        void stopMacro();
        /** Production requires the interrupted farm pose, not only command arrival. */
        default boolean requiresFarmReturn() { return true; }
        default boolean startFarmReturn() { return false; }
        /** ARRIVED must revalidate actual pose/heading/ground even after terminal completion. */
        default Return tickFarmReturn() { return Return.FAILED; }
        default void cancelFarmReturn() {}
    }

    /** World is an opaque local identity token; no Minecraft dependencies are needed by this machine. */
    public record Observation(boolean valid, Object world, String dimension, Vec3 position, double speed) {
        public Observation {
            if (position != null && (!Double.isFinite(position.x()) || !Double.isFinite(position.y())
                    || !Double.isFinite(position.z()))) valid = false;
            if (!Double.isFinite(speed) || speed < 0) valid = false;
        }
        private static Observation areaOnly() { return new Observation(true, null, null, null, 0); }
    }

    public record Inputs(boolean farmRunning, boolean autoRewarpEnabled, boolean onLeaveAreaEnabled,
                         boolean onStuckEnabled, boolean inGarden, boolean areaKnown, boolean onGround,
                         boolean toolReady, boolean stuck, long stuckSinceMs, long nowMs, Observation observation) {
        public Inputs { Objects.requireNonNull(observation, "observation"); }
        /** Area-only callers can prove cross-area arrival but cannot prove a same-Garden warp. */
        public Inputs(boolean farmRunning, boolean autoRewarpEnabled, boolean onLeaveAreaEnabled,
                      boolean onStuckEnabled, boolean inGarden, boolean areaKnown, boolean onGround,
                      boolean toolReady, boolean stuck, long stuckSinceMs, long nowMs) {
            this(farmRunning, autoRewarpEnabled, onLeaveAreaEnabled, onStuckEnabled, inGarden, areaKnown,
                    onGround, toolReady, stuck, stuckSinceMs, nowMs, Observation.areaOnly());
        }
    }
    public record Settings(long settleWaitMs, long maxWaitMs, long stuckMs) {
        public Settings {
            if (settleWaitMs < 0 || maxWaitMs <= 0 || stuckMs < 0)
                throw new IllegalArgumentException("Invalid rewarp timing");
        }
    }

    private final Actions actions;
    private AutoRewarpState state = AutoRewarpState.IDLE;
    private long startedMs, settleSinceMs = -1, lastTickMs;
    private String lastTrigger = "none";
    private boolean outsideGardenObserved, arrivalObserved, farmReturnStarted;
    private Observation previous, settleOrigin;
    private Observation expectedLanding;
    private long expectedLandingAt;

    public AutoRewarpRuntime(Actions actions) { this.actions = Objects.requireNonNull(actions, "actions"); }
    public AutoRewarpState state() { return state; }
    public String lastTrigger() { return lastTrigger; }
    /** External world/manual-stop owner already released inputs; discard the abandoned request. */
    public void cancel() { reset(); }
    public boolean consumeExpectedTeleport(Object world,Vec3 position,long now){
        if(expectedLanding==null||world!=expectedLanding.world()||position==null
                ||!Double.isFinite(position.x())||!Double.isFinite(position.y())||!Double.isFinite(position.z())
                ||now<expectedLandingAt||now-expectedLandingAt>=500||expectedLanding.position().distanceTo(position)>.35)return false;
        expectedLanding=null;return true;
    }

    public void tick(Inputs in, Settings settings) {
        if (!in.farmRunning()) { reset(); return; } // Module disable owns input release.
        if (state != AutoRewarpState.IDLE && (!in.autoRewarpEnabled() || !in.observation().valid())) {
            fail("Recovery cancelled by configuration/player/menu context"); return;
        }
        if (state == AutoRewarpState.IDLE) { tickIdle(in, settings); return; }
        // One deadline covers request, area bounces and settling. A backward clock never extends it.
        if (in.nowMs() < lastTickMs || in.nowMs() - startedMs >= settings.maxWaitMs()) {
            fail("Recovery deadline expired"); return;
        }
        lastTickMs = in.nowMs();
        boolean awaitingCommand=state==AutoRewarpState.WARPING||state==AutoRewarpState.WAITING_GARDEN;
        boolean observedOwnStep=awaitingCommand&&previous!=null&&previous.world()==in.observation().world()
                &&stepped(previous,in.observation());
        observe(in);
        if(observedOwnStep&&arrivalObserved&&in.areaKnown()&&in.inGarden()){
            expectedLanding=in.observation();expectedLandingAt=in.nowMs();
        }
        switch (state) {
            case WARPING -> state = AutoRewarpState.WAITING_GARDEN;
            case WAITING_GARDEN -> {
                if (arrivalObserved && in.areaKnown() && in.inGarden()) {
                    state = AutoRewarpState.SETTLING;
                    updateSettle(in, settings);
                }
            }
            case SETTLING -> updateSettle(in, settings);
            case RETURNING -> {
                try {
                    var result=actions.tickFarmReturn();
                    if(result==Return.ARRIVED) { state=AutoRewarpState.RESUME;settleOrigin=in.observation(); }
                    else if(result!=Return.ACTIVE) { fail("Could not return to interrupted farm pose");return; }
                } catch(RuntimeException failure) { fail("Farm return failed");return; }
            }
            case RESUME -> {
                // Revalidate on the actual callback tick, including drift since the last settled tick.
                if (!ready(in) || changedWorld(settleOrigin, in.observation()) || drifted(in.observation())) {
                    state = AutoRewarpState.SETTLING; settleSinceMs = -1; updateSettle(in, settings);
                } else {
                    try {
                        if(actions.requiresFarmReturn()) {
                            if(!farmReturnStarted) {
                                farmReturnStarted=true;
                                if(!actions.startFarmReturn()) { fail("No applicable interrupted farm return");return; }
                                state=AutoRewarpState.RETURNING;
                                break;
                            }
                            if(actions.tickFarmReturn()!=Return.ARRIVED) { fail("Farm pose changed before resume");return; }
                        }
                        actions.resumeFarming();
                    } catch (RuntimeException failure) { fail("Resume failed"); return; }
                    reset();
                }
            }
            case IDLE -> { }
        }
        if (state != AutoRewarpState.IDLE) previous = in.observation();
    }

    private void tickIdle(Inputs in, Settings settings) {
        if (!in.autoRewarpEnabled() || !in.observation().valid()) return;
        String trigger = null;
        if (in.onLeaveAreaEnabled() && in.areaKnown() && !in.inGarden()) trigger = "left the Garden";
        else if (in.onStuckEnabled() && in.areaKnown() && in.inGarden() && in.stuck() && in.nowMs() >= in.stuckSinceMs()
                && in.nowMs() - in.stuckSinceMs() >= settings.stuckMs()) trigger = "stuck (no progress)";
        if (trigger == null) return;
        lastTrigger = trigger;
        startedMs = lastTickMs = in.nowMs();
        previous = in.observation();
        outsideGardenObserved = in.areaKnown() && !in.inGarden();
        arrivalObserved = false;
        state = AutoRewarpState.WARPING;
        boolean sent;
        try {
            actions.suspendFarming();
            sent = actions.sendRewarp();
        } catch (RuntimeException failure) { fail("Rewarp action failed"); return; }
        if (!sent) fail("Rewarp command could not be sent");
    }

    private void observe(Inputs in) {
        Observation now = in.observation();
        if (changedWorld(previous, now) || stepped(previous, now)) {
            arrivalObserved = true;
            settleSinceMs = -1;
        }
        if (in.areaKnown() && !in.inGarden()) {
            outsideGardenObserved = true;
            arrivalObserved = false;
        } else if (in.areaKnown() && in.inGarden() && outsideGardenObserved) {
            arrivalObserved = true;
            outsideGardenObserved = false;
        }
    }
    private boolean changedWorld(Observation a, Observation b) {
        return a != null && ((a.world() != null && b.world() != null && a.world() != b.world())
                || (a.dimension() != null && b.dimension() != null && !a.dimension().equals(b.dimension())));
    }
    private boolean stepped(Observation a, Observation b) {
        return a != null && a.position() != null && b.position() != null
                && a.position().distanceTo(b.position()) >= TELEPORT_STEP;
    }
    private boolean ready(Inputs in) {
        return arrivalObserved && in.areaKnown() && in.inGarden() && in.onGround() && in.toolReady()
                && in.observation().speed() < MAX_SETTLE_SPEED;
    }
    private boolean drifted(Observation now) {
        return settleOrigin != null && settleOrigin.position() != null && now.position() != null
                && settleOrigin.position().distanceTo(now.position()) > SETTLE_DRIFT;
    }
    private void updateSettle(Inputs in, Settings settings) {
        if (!in.areaKnown() || !in.inGarden() || !arrivalObserved) {
            state = AutoRewarpState.WAITING_GARDEN; settleSinceMs = -1; return;
        }
        if (!ready(in)) { settleSinceMs = -1; return; }
        if (settleSinceMs < 0 || changedWorld(settleOrigin, in.observation()) || drifted(in.observation())) {
            settleSinceMs = in.nowMs(); settleOrigin = in.observation();
        }
        if (in.nowMs() - settleSinceMs >= settings.settleWaitMs()) state = AutoRewarpState.RESUME;
    }
    private void fail(String reason) {
        VeltraphLog.basic(TAG, reason + "; stopping macro");
        // Reset first: a stop callback can call back into this runtime.
        try { reset(); } finally { actions.stopMacro(); }
    }
    private void reset() {
        state = AutoRewarpState.IDLE; startedMs = lastTickMs = 0; settleSinceMs = -1;
        previous = settleOrigin = null; outsideGardenObserved = arrivalObserved = farmReturnStarted = false;
        expectedLanding=null;expectedLandingAt=0;
        actions.cancelFarmReturn();
    }
    public String statusLine(boolean enabled) {
        return "auto-rewarp: " + (enabled ? "ON" : "OFF") + " | state=" + state
                + " | lastTrigger=" + lastTrigger + " | arrivalObserved=" + arrivalObserved;
    }
}
