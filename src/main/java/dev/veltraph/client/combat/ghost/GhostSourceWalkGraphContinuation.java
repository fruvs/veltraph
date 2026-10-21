package dev.veltraph.client.combat.ghost;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import dev.veltraph.client.combat.ghost.GhostSourceWalkFrontier.Cell;
import dev.veltraph.client.combat.ghost.GhostSourceWalkFrontier.Node;
import dev.veltraph.client.combat.ghost.GhostSourceWalkFrontier.Offset;
import dev.veltraph.client.combat.ghost.GhostSourceWalkWorker.EdgeKey;
import dev.veltraph.client.combat.ghost.GhostSourceWalkWorker.Inputs;
import dev.veltraph.client.combat.ghost.GhostSourceWalkWorker.Observed;
import dev.veltraph.client.combat.ghost.GhostSourceWalkWorker.ObservedEdges;
import dev.veltraph.client.combat.ghost.GhostSourceWalkWorker.Progress;
import dev.veltraph.client.combat.ghost.GhostSourceWalkWorker.Result;
import dev.veltraph.client.combat.ghost.GhostSourceWalkWorker.Status;

/**
 * Resumable form of {@link GhostSourceWalkWorker#solve(Inputs, ObservedEdges, Progress)}.
 * The original frontier owns insertion order, discovered coordinates, parents, costs and the cap.
 * An observation exception leaves the popped node and offset index intact: the next advance retries
 * that exact edge. This class neither waits for an observation nor substitutes an edge decision.
 * A caller must serialize advances for one continuation.
 */
public final class GhostSourceWalkGraphContinuation {
    private final Inputs inputs;
    private final ObservedEdges observedEdges;
    private final Progress progress;
    private GhostSourceWalkFrontier frontier;
    private List<Offset> offsets;
    private Node from;
    private int offsetIndex;
    private boolean initialized;
    private boolean dropRetry;
    private int firstVisits;
    private Result terminalResult;

    public GhostSourceWalkGraphContinuation(Inputs inputs, ObservedEdges observedEdges, Progress progress) {
        this.inputs = Objects.requireNonNull(inputs);
        this.observedEdges = Objects.requireNonNull(observedEdges);
        this.progress = progress;
    }

    /** Returns a memoized terminal result, or propagates the observer's pending-cell exception. */
    public Result advance() {
        if (terminalResult != null) return terminalResult;
        if (!initialized) {
            if (progress != null) progress.stage("GRAPH");
            if (!observedEdges.current()) return terminal(Status.UNKNOWN_INPUT);
            var observations = List.of(inputs.startAir(), inputs.endAir(), inputs.startLoaded(), inputs.endLoaded());
            var failures = List.of(Status.START_NOT_AIR, Status.END_NOT_AIR, Status.START_NOT_LOADED, Status.END_NOT_LOADED);
            for (int i = 0; i < observations.size(); i++) {
                if (observations.get(i) == Observed.UNKNOWN) return terminal(Status.UNKNOWN_INPUT);
                if (observations.get(i) == Observed.NO) return terminal(failures.get(i));
            }
            initialized = true;
            beginAttempt();
        } else if (!observedEdges.current()) {
            // Invalidation while suspended retains this attempt's original visit accounting.
            return terminal(Status.UNKNOWN_INPUT);
        }

        while (true) {
            if (from == null) {
                if (!observedEdges.current()) return terminal(Status.UNKNOWN_INPUT);
                // Source order: exhausted OPEN before cap, then pop/visit before destination.
                if (!frontier.hasOpen()) {
                    if (GhostSourceWalkFrontier.sourceDropRetry(inputs.firstVolumeFlag(), dropRetry, frontier.visitedCount())) {
                        firstVisits = frontier.visitedCount();
                        dropRetry = true;
                        beginAttempt();
                        continue;
                    }
                    return terminal(Status.NO_UNVISITED);
                }
                if (!frontier.underVisitLimit()) return terminal(Status.VISIT_LIMIT);
                from = frontier.popVisit();
                offsetIndex = 0;
                if (progress != null) progress.graph(frontier.visitedCount(), frontier.openCount(), dropRetry, firstVisits);
                if (frontier.reached(from)) {
                    var costs = new ArrayList<Double>();
                    for (var node = from; node.parent() != null; node = node.parent()) costs.addFirst(node.edgeCost());
                    return finish(new Result(Status.FOUND, GhostSourceWalkFrontier.reconstruct(from), costs,
                            frontier.visitedCount(), dropRetry, firstVisits));
                }
            }

            while (offsetIndex < offsets.size()) {
                var offset = offsets.get(offsetIndex);
                if (!GhostSourceWalkFrontier.structuralMove(offset)) {
                    offsetIndex++;
                    continue;
                }
                var to = new Cell(Math.addExact(from.cell().x(), offset.x()),
                        Math.addExact(from.cell().y(), offset.y()), Math.addExact(from.cell().z(), offset.z()));
                if (frontier.alreadyDiscovered(to)) {
                    offsetIndex++;
                    continue;
                }
                // No catch and no prior cursor mutation: pending availability retries this edge.
                var observed = observedEdges.observe(new EdgeKey(from.cell(), to, dropRetry));
                if (!observedEdges.current()) return terminal(Status.UNKNOWN_INPUT);
                if (observed == null || observed.admissible() == Observed.UNKNOWN) return terminal(Status.UNKNOWN_INPUT);
                if (observed.admissible() == Observed.YES) frontier.addObservedEdge(from, to, observed.fullSourceCost());
                offsetIndex++; // Advance only after the complete valid observation was processed.
            }
            from = null;
        }
    }

    private void beginAttempt() {
        frontier = new GhostSourceWalkFrontier(inputs.start(), inputs.end(), inputs.visitLimit());
        offsets = GhostSourceWalkFrontier.offsets(inputs.step(), inputs.riseMax(),
                inputs.firstVolumeFlag() || inputs.secondVolumeFlag(), dropRetry);
        from = null;
        offsetIndex = 0;
    }

    private Result terminal(Status status) {
        return finish(new Result(status, List.of(), List.of(), frontier == null ? 0 : frontier.visitedCount(),
                dropRetry, firstVisits));
    }

    private Result finish(Result result) {
        terminalResult = result;
        if (progress != null) {
            progress.graph(result.visitedCount(), 0, result.dropRetry(), result.firstAttemptVisited());
            progress.stage("GRAPH_" + result.status());
        }
        return result;
    }
}
