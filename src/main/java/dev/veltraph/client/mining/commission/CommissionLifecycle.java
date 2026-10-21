package dev.veltraph.client.mining.commission;

import java.util.List;
import java.util.Objects;
import static dev.veltraph.client.mining.commission.CommissionObservation.*;

/** Commission orchestration; actual actions remain owned by a native, freshly checked adapter. */
public final class CommissionLifecycle {
    public enum State { STOPPED, OBSERVING, NAVIGATING, WORKING, CLAIMING, REFUELING, COOLING, PAUSED }
    public enum Operation { NAVIGATE, MINE, COMBAT, CLAIM, REFUEL, COOL }
    public enum Result { PENDING, SUCCEEDED, FAILED }
    public record Policy(long observationMaxAgeMillis, long operationTimeoutMillis, int pathRetries,
                         long retryDelayMillis) {
        public Policy {
            if (observationMaxAgeMillis < 1 || operationTimeoutMillis < 1 || pathRetries < 0 || pathRetries > 10 || retryDelayMillis < 1)
                throw new IllegalArgumentException("Invalid commission policy");
        }
    }
    public record Frame(String context, long nowMillis, boolean owned, boolean toolReady, boolean capacity,
                        String region, CommissionObservation commissions, boolean fuelLow, boolean coldHigh) {}
    /** Baseline is in the adapter's native action receipt domain, unrelated to commission serials. */
    public record ActionIssue(long ticket, Operation operation, String context, String receiptDomain,
                              String actionNonce, long actionBaseline, long issuedMillis) {
        public ActionIssue {
            Objects.requireNonNull(operation); Objects.requireNonNull(context);
            Objects.requireNonNull(receiptDomain); Objects.requireNonNull(actionNonce);
            if (ticket < 1 || context.isBlank() || receiptDomain.isBlank() || actionNonce.isBlank()
                    || actionBaseline < 0 || issuedMillis < 0) throw new IllegalArgumentException("Invalid action issue");
        }
    }
    public record Receipt(long ticket, Operation operation, String context, String receiptDomain,
                          String actionNonce, long actionSerial, long observedMillis, Result result) {}
    public interface Port {
        /** Return the admitted action's baseline captured BEFORE dispatch, or null on refusal. */
        ActionIssue start(Operation operation, List<Task> tasks, long ticket, Frame frame);
        Receipt receipt(long ticket);
        void cancel(long ticket);
        void releaseOwned();
    }
    private final Port port;
    private final Policy policy;
    private State state = State.STOPPED;
    private String context = "", reason = "Stopped";
    private long ticket, started, issuedCommissionSerial, notBefore, lastNow;
    private ActionIssue issue;
    private int failures, confirmedClaims;
    private List<Task> selection = List.of();
    public CommissionLifecycle(Port port, Policy policy) {
        this.port = Objects.requireNonNull(port); this.policy = Objects.requireNonNull(policy);
    }
    public void start(Frame frame) {
        stop(); context = ""; confirmedClaims = 0; failures = 0; notBefore = 0;
        if (!valid(frame)) { state = State.PAUSED; reason = "Fresh owned commission observation required"; return; }
        context = frame.context(); lastNow = frame.nowMillis(); state = State.OBSERVING; reason = "Observing commissions";
    }
    public void stop() {
        if (running()) port.cancel(ticket);
        port.releaseOwned(); selection = List.of(); state = State.STOPPED; reason = "Stopped";
    }
    private boolean running() {
        return state == State.NAVIGATING || state == State.WORKING || state == State.CLAIMING
                || state == State.REFUELING || state == State.COOLING;
    }
    private boolean valid(Frame frame) {
        if (frame == null || frame.context() == null || frame.context().isBlank() || !frame.owned() || frame.nowMillis() < 0
                || frame.commissions() == null || !frame.context().equals(frame.commissions().context())) return false;
        long age = frame.nowMillis() - frame.commissions().observedMillis();
        return age >= 0 && age <= policy.observationMaxAgeMillis() && frame.commissions().completeSection();
    }
    private void pause(String why) {
        if (running()) port.cancel(ticket);
        port.releaseOwned(); state = State.PAUSED; reason = why;
    }
    private void launch(Operation op, Frame frame) {
        ticket++; started = frame.nowMillis(); issuedCommissionSerial = frame.commissions().serial();
        state = switch (op) {
            case NAVIGATE -> State.NAVIGATING; case MINE, COMBAT -> State.WORKING;
            case CLAIM -> State.CLAIMING; case REFUEL -> State.REFUELING; case COOL -> State.COOLING;
        };
        issue = port.start(op, selection, ticket, frame);
        if (issue == null || issue.ticket() != ticket || issue.operation() != op
                || !context.equals(issue.context()) || issue.issuedMillis() < started
                || issue.issuedMillis() - started > policy.observationMaxAgeMillis())
            pause("Native " + op + " admission unavailable");
        else reason = op.name();
    }
    public void tick(Frame frame) {
        if (state == State.STOPPED || state == State.PAUSED) return;
        if (!valid(frame) || !context.equals(frame.context()) || frame.nowMillis() < lastNow) {
            pause("Commission context, observation or clock changed"); return;
        }
        lastNow = frame.nowMillis();
        if (!frame.toolReady() || !frame.capacity()) { pause("Mining equipment or capacity unavailable"); return; }
        if (running()) {
            // The original operation deadline wins over late or same-deadline outcomes.
            if (frame.nowMillis() - started >= policy.operationTimeoutMillis()) { failed(frame); return; }
            boolean selectedStillActive = selection.stream().allMatch(old -> frame.commissions().tasks().stream()
                    .anyMatch(current -> current.name().equals(old.name()) && current.work() == old.work()
                        && current.region().equals(old.region()) && current.resource().equals(old.resource()) && current.active()));
            if ((state == State.NAVIGATING || state == State.WORKING) && !selectedStillActive) {
                port.cancel(ticket); port.releaseOwned(); state = State.OBSERVING;
            } else if (state != State.CLAIMING && frame.commissions().tasks().stream().anyMatch(Task::done)) {
                port.cancel(ticket); port.releaseOwned(); state = State.OBSERVING;
            } else if (state != State.COOLING && frame.coldHigh()) {
                port.cancel(ticket); port.releaseOwned(); state = State.OBSERVING;
            } else if (state == State.WORKING && frame.fuelLow()) {
                port.cancel(ticket); port.releaseOwned(); state = State.OBSERVING;
            } else {
                Receipt receipt = port.receipt(ticket);
                if (receipt != null && issue != null && receipt.ticket() == ticket && receipt.operation() == issue.operation()
                        && context.equals(receipt.context()) && issue.receiptDomain().equals(receipt.receiptDomain())
                        && issue.actionNonce().equals(receipt.actionNonce()) && receipt.actionSerial() > issue.actionBaseline()
                        && receipt.observedMillis() >= issue.issuedMillis()
                        && receipt.observedMillis() <= frame.nowMillis()
                        && (receipt.result() == Result.SUCCEEDED || receipt.result() == Result.FAILED)) {
                    if (receipt.result() == Result.FAILED) { failed(frame); return; }
                    State completed = state;
                    if (completed == State.CLAIMING && (frame.commissions().serial() <= issuedCommissionSerial
                            || selection.stream().anyMatch(old -> frame.commissions().tasks().stream()
                                .anyMatch(current -> sameTask(current, old) && current.done())))) {
                        if (frame.nowMillis() - started >= policy.operationTimeoutMillis()) failed(frame);
                        return;
                    }
                    if ((completed == State.COOLING && frame.coldHigh()) || (completed == State.REFUELING && frame.fuelLow())) {
                        if (frame.nowMillis() - started >= policy.operationTimeoutMillis()) failed(frame);
                        return;
                    }
                    port.cancel(ticket); port.releaseOwned(); state = State.OBSERVING; failures = 0;
                    if (completed == State.NAVIGATING) {
                        // A task may have changed during navigation; do not work stale selection.
                        List<Task> fresh = CommissionSelector.select(frame.commissions().tasks(), frame.region());
                        if (fresh.isEmpty() || !sameTasks(fresh, selection)) { selection = fresh; return; }
                        launch(selection.getFirst().work() == Work.MOB ? Operation.COMBAT : Operation.MINE, frame); return;
                    }
                    if (completed == State.CLAIMING) confirmedClaims++;
                    notBefore = frame.nowMillis() + policy.retryDelayMillis(); return;
                }
                return;
            }
        }
        if (frame.nowMillis() < notBefore) return;
        if (frame.coldHigh()) { selection = List.of(); launch(Operation.COOL, frame); return; }
        if (frame.fuelLow()) { selection = List.of(); launch(Operation.REFUEL, frame); return; }
        List<Task> done = frame.commissions().tasks().stream().filter(Task::done).toList();
        if (!done.isEmpty()) { selection = done; launch(Operation.CLAIM, frame); return; }
        selection = CommissionSelector.select(frame.commissions().tasks(), frame.region());
        if (selection.isEmpty()) { port.releaseOwned(); reason = "No supported active commission"; return; }
        launch(Operation.NAVIGATE, frame);
    }
    private static boolean sameTasks(List<Task> a, List<Task> b) {
        if (a.size() != b.size()) return false;
        for (int i = 0; i < a.size(); i++) {
            Task first = a.get(i), second = b.get(i);
            if (!sameTask(first, second)) return false;
        }
        return true;
    }
    private static boolean sameTask(Task first, Task second) {
        return first.name().equals(second.name()) && first.work() == second.work()
                && first.region().equals(second.region()) && first.resource().equals(second.resource());
    }
    private void failed(Frame frame) {
        State failed = state; port.cancel(ticket); port.releaseOwned();
        if (failed != State.NAVIGATING || ++failures > policy.pathRetries()) {
            state = State.PAUSED; reason = "Native " + failed + " failed or timed out"; return;
        }
        state = State.OBSERVING; notBefore = frame.nowMillis() + policy.retryDelayMillis(); reason = "Waiting before path retry";
    }
    public State state() { return state; }
    public String reason() { return reason; }
    public int confirmedClaimBatches() { return confirmedClaims; }
    public long ticket() { return ticket; }
}
