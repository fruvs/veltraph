package dev.veltraph.client.dev;

/**
 * Outcome of running a {@link DevTestAction}. Pure value type so action logic is unit-testable.
 *
 * @param status  how it resolved
 * @param message a short human-readable summary (shown in local chat + logged)
 */
public record DevTestResult(Status status, String message) {

    public enum Status {
        /** A local-only effect ran (chat HUD / stop / camera / log). */
        LOCAL,
        /** A would-be server action was shown/logged but not sent. */
        DRY_RUN,
        /** A server-bound action was actually sent. */
        EXECUTED,
        /** Nothing happened on purpose (e.g. no macro was running). */
        NO_OP,
        /** Refused by gating (Dev Mode off / context predicate / server-bound not allowed). */
        BLOCKED,
        /** The action id is unknown / the underlying system is unavailable. */
        UNAVAILABLE
    }

    public static DevTestResult local(String message) {
        return new DevTestResult(Status.LOCAL, message);
    }

    public static DevTestResult dryRun(String message) {
        return new DevTestResult(Status.DRY_RUN, message);
    }

    public static DevTestResult executed(String message) {
        return new DevTestResult(Status.EXECUTED, message);
    }

    public static DevTestResult noOp(String message) {
        return new DevTestResult(Status.NO_OP, message);
    }

    public static DevTestResult blocked(String message) {
        return new DevTestResult(Status.BLOCKED, message);
    }

    public static DevTestResult unavailable(String message) {
        return new DevTestResult(Status.UNAVAILABLE, message);
    }

    /** Whether the action did something (local effect, dry-run, executed, or deliberate no-op). */
    public boolean ran() {
        return status == Status.LOCAL || status == Status.DRY_RUN
                || status == Status.EXECUTED || status == Status.NO_OP;
    }

    public boolean wasDryRun() {
        return status == Status.DRY_RUN;
    }

    public boolean wasExecuted() {
        return status == Status.EXECUTED;
    }
}
