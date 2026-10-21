package dev.veltraph.client.failsafe;

import dev.veltraph.client.config.*;
import dev.veltraph.client.failsafe.response.FailsafeResponseType;
import java.util.*;
import java.util.function.*;
import java.util.regex.Pattern;

/** General local failsafes, independent of the optional Hydra watcher. */
public final class GeneralFailsafe {
    public record Player(double distance, boolean whitelisted, boolean lookingAt, boolean inFront,
                         boolean visible, String identity) {
        public Player(double distance, boolean whitelisted, boolean lookingAt, boolean inFront, boolean visible) {
            this(distance, whitelisted, lookingAt, inFront, visible, "");
        }
    }
    public interface Actions {
        void alert(String reason);
        void stop();
        void returnToLocation();
        /** Root supplies a guarded local route; absent that integration, stop remains authoritative. */
        default void switchLobby() { }
        /** True transfers completion ownership to the bounded runner. False completes now. */
        default boolean beginResponse(FailsafeResponseType type, Runnable completion) { return false; }
        /** Cancel only this runtime's exact general response ticket when its source permission changes. */
        default void cancelResponse(FailsafeResponseType type) { }
    }
    private final ConfigService config;
    private final Actions actions;
    private final LongSupplier clock;
    private final DoubleSupplier random;
    private LongSupplier mentionMinDelay = () -> 3000L;
    private LongSupplier mentionMaxDelay = () -> 10000L;
    private long generation;
    private Supplier<FailsafeSourcePolicy> playerPolicy = () -> FailsafeSourcePolicy.LOCAL_POLICY;
    private Supplier<FailsafeSourcePolicy> mentionPolicy = () -> FailsafeSourcePolicy.LOCAL_POLICY;
    private final Set<String> previousPlayers = new HashSet<>();
    private FailsafeResponseType latched;
    private boolean suspiciousLatched;
    private boolean mentionPending;
    private boolean emergencyPending;
    private FailsafeResponseType ownedResponse;
    private FailsafeSourcePolicy ownedSourcePolicy;
    private long mentionDeadline;

    public GeneralFailsafe(ConfigService config, Actions actions) {
        this(config, actions, System::currentTimeMillis, Math::random);
    }
    public GeneralFailsafe(ConfigService config, Actions actions, LongSupplier clock, DoubleSupplier random) {
        this.config = Objects.requireNonNull(config);
        this.actions = Objects.requireNonNull(actions);
        this.clock = Objects.requireNonNull(clock);
        this.random = Objects.requireNonNull(random);
    }
    /** Separate persisted policy suppliers are owned/wired by Root. Defaults preserve local behavior. */
    public void sourcePolicies(Supplier<FailsafeSourcePolicy> player, Supplier<FailsafeSourcePolicy> mention) {
        playerPolicy = Objects.requireNonNull(player);
        mentionPolicy = Objects.requireNonNull(mention);
        previousPlayers.clear();
        mentionPending = false;
        emergencyPending = false;
    }
    public void reset() {
        generation++;
        latched = null;
        suspiciousLatched = false;
        previousPlayers.clear();
        mentionPending = false;
        emergencyPending = false;
        ownedResponse = null;
        ownedSourcePolicy = null;
    }
    /** Source defaults are 3–10s; configured local overrides are bounded to two minutes. */
    public void mentionDelays(LongSupplier minimum, LongSupplier maximum) {
        mentionMinDelay = Objects.requireNonNull(minimum);
        mentionMaxDelay = Objects.requireNonNull(maximum);
        mentionPending = false;
    }
    public void tick(boolean active, boolean belowWorld, List<Player> nearby) {
        tick(active, belowWorld, nearby, false);
    }
    /** External ownership authorizes observation, while its execution veto is captured for this tick. */
    public void tick(boolean active, boolean belowWorld, List<Player> nearby, boolean externalOwns) {
        if (ownedResponse != null && (!triggerStillEnabled(ownedResponse)
                || sourcePolicy(ownedResponse) != ownedSourcePolicy || externalOwns)) {
            FailsafeResponseType revoked = ownedResponse;
            reset();
            actions.cancelResponse(revoked);
        }
        if (!active) {
            if (emergencyPending) { latched = null; suspiciousLatched = false; previousPlayers.clear(); mentionPending = false; }
            else reset();
            return;
        }
        if (mentionPending) {
            if (!config.get(FailsafeConfig.FAILSAFE_MENTION_ENABLED)
                    || policy(mentionPolicy) != FailsafeSourcePolicy.SWITCH_LOBBY) mentionPending = false;
            else if (clock.getAsLong() >= mentionDeadline) {
                mentionPending = false;
                react(FailsafeResponseType.MENTION, "Mention failsafe", externalOwns);
            }
        }
        FailsafeResponseType trigger = null;
        String reason = "";
        Set<String> observed = new HashSet<>();
        if (config.get(FailsafeConfig.FAILSAFE_CHECK_VOID) && belowWorld) {
            trigger = FailsafeResponseType.VOID;
            reason = "Falling below the world";
        } else if (config.get(FailsafeConfig.FAILSAFE_PLAYER_NEARBY_ENABLED)) {
            double radius = config.get(FailsafeConfig.FAILSAFE_PLAYER_RADIUS);
            boolean persistObservation = policy(playerPolicy) != FailsafeSourcePolicy.LOCAL_POLICY;
            long count = nearby.stream().filter(Objects::nonNull).filter(p -> !p.whitelisted()
                    && Double.isFinite(p.distance()) && p.distance() >= 0
                    && p.distance() <= Math.max(0, Math.min(128, radius))
                    && (!config.get(FailsafeConfig.FAILSAFE_ONLY_LOOKING_AT) || p.lookingAt())
                    && (!config.get(FailsafeConfig.FAILSAFE_ONLY_IN_FRONT) || p.inFront())
                    && (!config.get(FailsafeConfig.FAILSAFE_RAYTRACED_ONLY) || p.visible()))
                    .filter(p -> {
                        if (!persistObservation) return true;
                        if (p.identity() == null || p.identity().isBlank()) return false;
                        observed.add(p.identity());
                        return previousPlayers.contains(p.identity());
                    }).count();
            if (count >= Math.max(1, config.get(FailsafeConfig.FAILSAFE_PLAYER_COUNT))) {
                trigger = FailsafeResponseType.PLAYER_NEARBY;
                reason = "Nearby-player failsafe (" + count + ")";
            }
        }
        previousPlayers.clear();
        previousPlayers.addAll(observed);
        if (trigger == null) { latched = null; return; }
        if (trigger != latched) { latched = trigger; react(trigger, reason, externalOwns); }
    }
    /** Local raw @name adapter; the original typed mention-event parser is not claimed recovered. */
    public void mention(boolean active, String playerName, String message) {
        mention(active, playerName, message, false);
    }
    public void mention(boolean active, String playerName, String message, boolean externalOwns) {
        if (!active || playerName == null || playerName.isBlank() || message == null) return;
        if (Pattern.compile("(?i)(?<![a-z0-9_])@" + Pattern.quote(playerName) + "(?![a-z0-9_])")
                .matcher(message.replaceAll("§.", "")).find()) mentionObserved(true, externalOwns);
    }
    /** Typed mention ingress, requiring a caller-confirmed active owner and observed event. */
    public void mentionObserved(boolean active) {
        mentionObserved(active, false);
    }
    public void mentionObserved(boolean active, boolean externalOwns) {
        if (!active || !config.get(FailsafeConfig.FAILSAFE_MENTION_ENABLED)) return;
        if (policy(mentionPolicy) == FailsafeSourcePolicy.SWITCH_LOBBY) {
            if (!mentionPending) {
                double roll = random.getAsDouble();
                if (!Double.isFinite(roll)) roll = 0;
                roll = Math.max(0, Math.min(Math.nextDown(1.0), roll));
                long minimum = Math.max(0, Math.min(120000, mentionMinDelay.getAsLong()));
                long maximum = Math.max(minimum, Math.max(0, Math.min(120000, mentionMaxDelay.getAsLong())));
                long delay = minimum + (long) (roll * (maximum - minimum + 1)); // Kotlin IntRange inclusive
                long now = clock.getAsLong();
                mentionDeadline = now > Long.MAX_VALUE - delay ? Long.MAX_VALUE : now + delay;
                mentionPending = true;
            }
        } else react(FailsafeResponseType.MENTION, "Mention failsafe", externalOwns);
    }
    /** Local threshold for independently attributed observations; this is not a new detector. */
    public void suspicious(boolean active, int observedCount) {
        suspicious(active, observedCount, false);
    }
    public void suspicious(boolean active, int observedCount, boolean externalOwns) {
        int threshold = Math.max(1, config.get(FailsafeConfig.FAILSAFE_SUSPICIOUS_SENSITIVITY));
        if (!active || observedCount < threshold) { suspiciousLatched = false; return; }
        if (!suspiciousLatched) {
            suspiciousLatched = true;
            react(FailsafeResponseType.REPEATED_SUSPICIOUS, "Repeated suspicious activity (" + observedCount + ")", externalOwns);
        }
    }
    private static FailsafeSourcePolicy policy(Supplier<FailsafeSourcePolicy> source) {
        FailsafeSourcePolicy value = source.get();
        return value == null ? FailsafeSourcePolicy.LOCAL_POLICY : value;
    }
    private void react(FailsafeResponseType trigger, String reason, boolean externalOwns) {
        FailsafeSourcePolicy capturedSource = sourcePolicy(trigger);
        actions.alert(reason);
        if (capturedSource == FailsafeSourcePolicy.NOTIFY) return;
        boolean alreadyStopped = trigger == FailsafeResponseType.VOID;
        if (alreadyStopped) actions.stop();
        long expectedGeneration = generation;
        Runnable completion = () -> {
            if (generation != expectedGeneration) return;
            ownedResponse = null;
            ownedSourcePolicy = null;
            if (alreadyStopped) emergencyPending = false;
            if (!triggerStillEnabled(trigger) || sourcePolicy(trigger) != capturedSource) return;
            applyPolicy(trigger, alreadyStopped);
        };
        boolean transferred = false;
        try { if (!externalOwns) transferred = actions.beginResponse(trigger, completion); }
        catch (RuntimeException failure) {
            dev.veltraph.client.log.VeltraphLog.basic("Failsafe", "Response unavailable: " + failure.getClass().getSimpleName());
        }
        if (transferred && alreadyStopped) emergencyPending = true;
        if (transferred) { ownedResponse = trigger; ownedSourcePolicy = capturedSource; }
        if (!transferred) completion.run();
    }
    private void applyPolicy(FailsafeResponseType trigger, boolean alreadyStopped) {
        FailsafeSourcePolicy source = sourcePolicy(trigger);
        if (source == FailsafeSourcePolicy.NOTIFY) return;
        if (source == FailsafeSourcePolicy.SWITCH_LOBBY) { actions.stop(); actions.switchLobby(); return; }
        switch (config.get(FailsafeConfig.FAILSAFE_REACTION_POLICY)) {
            case DO_NOTHING -> { }
            case STOP_SCRIPT -> { if (!alreadyStopped) actions.stop(); }
            case STOP_AND_RETURN_TO_LOCATION -> { if (!alreadyStopped) actions.stop(); actions.returnToLocation(); }
        }
    }
    private FailsafeSourcePolicy sourcePolicy(FailsafeResponseType trigger) {
        return trigger == FailsafeResponseType.PLAYER_NEARBY ? policy(playerPolicy)
                : trigger == FailsafeResponseType.MENTION ? policy(mentionPolicy) : FailsafeSourcePolicy.LOCAL_POLICY;
    }
    private boolean triggerStillEnabled(FailsafeResponseType trigger) {
        return switch (trigger) {
            case MENTION -> config.get(FailsafeConfig.FAILSAFE_MENTION_ENABLED);
            case PLAYER_NEARBY -> config.get(FailsafeConfig.FAILSAFE_PLAYER_NEARBY_ENABLED);
            case VOID -> config.get(FailsafeConfig.FAILSAFE_CHECK_VOID);
            default -> true;
        };
    }
}
