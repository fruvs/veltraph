package dev.veltraph.client.combat.ghost;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

import dev.veltraph.client.combat.ghost.GhostSourceWalkFrontier.Cell;
import dev.veltraph.client.combat.ghost.GhostSourceWalkWorker.Edge;
import dev.veltraph.client.combat.ghost.GhostSourceWalkWorker.EdgeKey;
import dev.veltraph.client.combat.ghost.GhostSourceWalkWorker.Inputs;
import dev.veltraph.client.combat.ghost.GhostSourceWalkWorker.Observed;
import dev.veltraph.client.combat.ghost.GhostSourceWalkWorker.ObservedEdges;
import dev.veltraph.client.combat.ghost.GhostSourceWalkWorker.Progress;
import dev.veltraph.client.combat.ghost.GhostSourceWalkWorker.Result;
import dev.veltraph.client.combat.ghost.GhostSourceWalkWorker.Status;

/**
 * Differential tests against the immutable synchronous worker, with explicit independent outcomes.
 * Cap, single-node, blocked-frontier and deep-drop fixtures preserve the existing WorkerTest cases;
 * tie insertion and no-relaxation fixtures extend the independently authored FrontierTest cases.
 * Every requested edge can suspend twice before completing, including blocked edges.
 */
class GhostSourceWalkGraphContinuationTest {
    private static final Cell START = new Cell(0, 0, 0);
    private static final Edge BLOCKED = new Edge(Observed.NO, Double.NaN);

    private static Inputs input(Cell end, int cap, boolean firstVolume, boolean secondVolume, Map<EdgeKey, Edge> edges) {
        return new Inputs(START, end, cap, 1, 1, firstVolume, secondVolume,
                Observed.YES, Observed.YES, Observed.YES, Observed.YES, edges);
    }

    private static Map<EdgeKey, Edge> blocked(Cell from, boolean volume, boolean retry) {
        var edges = new HashMap<EdgeKey, Edge>();
        for (var offset : GhostSourceWalkFrontier.offsets(1, 1, volume, retry)) {
            if (GhostSourceWalkFrontier.structuralMove(offset)) {
                edges.put(new EdgeKey(from, new Cell(from.x() + offset.x(), from.y() + offset.y(),
                        from.z() + offset.z()), retry), BLOCKED);
            }
        }
        return edges;
    }

    private static void allow(Map<EdgeKey, Edge> edges, Cell from, Cell to, boolean retry, double cost) {
        edges.put(new EdgeKey(from, to, retry), new Edge(Observed.YES, cost));
    }

    private static Inputs capFixture(int cap) {
        var end = new Cell(1, 0, 0);
        var edges = blocked(START, false, false);
        allow(edges, START, end, false, 1);
        return input(end, cap, false, false, edges);
    }

    private static Inputs deepDropFixture() {
        var end = new Cell(0, -3, 0);
        var edges = blocked(START, false, false);
        edges.putAll(blocked(START, false, true));
        allow(edges, START, end, true, 7);
        return input(end, 2, false, false, edges);
    }

    private static final class RecordingEdges implements ObservedEdges {
        final Map<EdgeKey, Edge> edges;
        final List<EdgeKey> completed = new ArrayList<>();
        boolean current = true;
        int currentChecks;

        RecordingEdges(Map<EdgeKey, Edge> edges) { this.edges = edges; }
        @Override public boolean current() { currentChecks++; return current; }
        @Override public Edge observe(EdgeKey edge) { completed.add(edge); return edges.get(edge); }
    }

    private static final class MissingEdges implements ObservedEdges {
        final Map<EdgeKey, Edge> edges;
        final Map<EdgeKey, CompletableFuture<Void>> availability = new HashMap<>();
        final List<EdgeKey> attempted = new ArrayList<>();
        final List<EdgeKey> completed = new ArrayList<>();
        boolean current = true;

        MissingEdges(Map<EdgeKey, Edge> edges) { this.edges = edges; }
        @Override public boolean current() { return current; }
        @Override public Edge observe(EdgeKey edge) {
            attempted.add(edge);
            var future = availability.computeIfAbsent(edge, ignored -> new CompletableFuture<>());
            if (!future.isDone()) throw new GhostSourceWalkPendingCell(future);
            completed.add(edge);
            return edges.get(edge);
        }
    }

    /** Replay every synchronous request in order; even an immediate retry must not pop/recount. */
    private static Result suspendAtEveryEdge(Inputs inputs) {
        var originalEdges = new RecordingEdges(inputs.edges());
        var originalProgress = new Progress();
        var expected = GhostSourceWalkWorker.solve(inputs, originalEdges, originalProgress);
        var missingEdges = new MissingEdges(inputs.edges());
        var progress = new Progress();
        var continuation = new GhostSourceWalkGraphContinuation(inputs, missingEdges, progress);
        int completedCount = 0;
        Result actual;
        while (true) {
            try {
                actual = continuation.advance();
                break;
            } catch (GhostSourceWalkPendingCell pending) {
                assertTrue(completedCount < originalEdges.completed.size(), "No extra observed edge may appear");
                var expectedEdge = originalEdges.completed.get(completedCount);
                assertEquals(expectedEdge, missingEdges.attempted.getLast());
                assertEquals(completedCount, missingEdges.completed.size());
                if (completedCount > 0) assertEquals(originalEdges.completed.get(completedCount - 1), missingEdges.completed.getLast());
                var held = progress.cached();
                assertTrue(held.visited() > 0);
                assertEquals("GRAPH", held.stage());
                var again = assertThrows(GhostSourceWalkPendingCell.class, continuation::advance);
                assertSame(pending.availability(), again.availability());
                assertSame(missingEdges.availability.get(expectedEdge), again.availability());
                assertEquals(held, progress.cached(), "Retry must not pop, recount, reset, or change graph progress");
                assertEquals(expectedEdge, missingEdges.attempted.getLast());
                assertEquals(completedCount, missingEdges.completed.size());
                assertTrue(missingEdges.availability.get(expectedEdge).complete(null));
                completedCount++;
            }
        }
        assertEquals(expected, actual);
        assertEquals(originalEdges.completed, missingEdges.completed);
        assertEquals(originalEdges.completed.size() * 3, missingEdges.attempted.size());
        for (int i = 0; i < originalEdges.completed.size(); i++) {
            assertEquals(List.of(originalEdges.completed.get(i), originalEdges.completed.get(i), originalEdges.completed.get(i)),
                    missingEdges.attempted.subList(i * 3, i * 3 + 3));
        }
        var expectedProgress = originalProgress.cached();
        var actualProgress = progress.cached();
        assertEquals(expectedProgress.stage(), actualProgress.stage());
        assertEquals(expectedProgress.visited(), actualProgress.visited());
        assertEquals(expectedProgress.open(), actualProgress.open());
        assertEquals(expectedProgress.dropRetry(), actualProgress.dropRetry());
        assertEquals(expectedProgress.firstPassVisits(), actualProgress.firstPassVisits());
        assertTrue(actualProgress.startedNanos() > 0);
        assertTrue(actualProgress.lastStageNanos() >= actualProgress.startedNanos());
        assertSame(actual, continuation.advance());
        assertEquals(originalEdges.completed, missingEdges.completed);
        return actual;
    }

    @Test void completedObservationsRetainExactOriginalResultAndTrace() {
        var inputs = capFixture(2);
        var originalEdges = new RecordingEdges(inputs.edges());
        var actualEdges = new RecordingEdges(inputs.edges());
        var expected = GhostSourceWalkWorker.solve(inputs, originalEdges);
        var actual = new GhostSourceWalkGraphContinuation(inputs, actualEdges, null).advance();
        assertEquals(expected, actual);
        assertEquals(originalEdges.completed, actualEdges.completed);
        assertEquals(originalEdges.currentChecks, actualEdges.currentChecks);
    }

    @Test void everyMissingEdgeRetainsExactCapGoalAndCosts() {
        var actual = suspendAtEveryEdge(capFixture(2));
        assertEquals(Status.FOUND, actual.status());
        assertEquals(2, actual.visitedCount());
        assertEquals(List.of(START, new Cell(1, 0, 0)), actual.orderedNodes());
        assertEquals(List.of(1.0), actual.graphEdgeCosts());
    }

    @Test void everyMissingEdgeRetainsCapBeforeNextPop() {
        var actual = suspendAtEveryEdge(capFixture(1));
        assertEquals(Status.VISIT_LIMIT, actual.status());
        assertEquals(1, actual.visitedCount());
        assertFalse(actual.dropRetry());
    }

    @Test void nonUnitStepAndRawGroundRiseOffsetsArePreserved() {
        var end = new Cell(2, -1, 0);
        var edges = new HashMap<EdgeKey, Edge>();
        for (var offset : GhostSourceWalkFrontier.offsets(2, 1, false, false)) {
            if (GhostSourceWalkFrontier.structuralMove(offset)) {
                edges.put(new EdgeKey(START, new Cell(offset.x(), offset.y(), offset.z()), false), BLOCKED);
            }
        }
        allow(edges, START, end, false, 9.5);
        var inputs = new Inputs(START, end, 2, 2, 1, false, false,
                Observed.YES, Observed.YES, Observed.YES, Observed.YES, edges);
        var actual = suspendAtEveryEdge(inputs);
        assertEquals(Status.FOUND, actual.status());
        assertEquals(List.of(START, end), actual.orderedNodes());
        assertEquals(List.of(9.5), actual.graphEdgeCosts());
        assertEquals(2, actual.visitedCount());
    }

    @Test void equalPriorityLaterInsertionStillWinsAndItsGoalParentIsNotRelaxed() {
        var end = new Cell(0, 0, 1);
        var left = new Cell(-1, 0, 0);
        var right = new Cell(1, 0, 0);
        var edges = blocked(START, true, false);
        edges.putAll(blocked(left, true, false));
        edges.putAll(blocked(right, true, false));
        allow(edges, START, left, false, 1);
        allow(edges, START, right, false, 1);
        allow(edges, left, end, false, 11);
        allow(edges, right, end, false, 13);
        var inputs = input(end, 4, true, false, edges);
        var actual = suspendAtEveryEdge(inputs);
        assertEquals(Status.FOUND, actual.status());
        assertEquals(List.of(START, right, end), actual.orderedNodes());
        assertEquals(List.of(1.0, 13.0), actual.graphEdgeCosts());
        assertEquals(4, actual.visitedCount());
        var recorded = new RecordingEdges(edges);
        GhostSourceWalkWorker.solve(inputs, recorded);
        assertEquals(List.of(START, right, left), recorded.completed.stream().map(EdgeKey::from).distinct().toList());
        assertFalse(recorded.completed.contains(new EdgeKey(left, end, false)));
    }

    @Test void cheaperLaterEdgeNeverReopensOrRelaxAnAlreadyDiscoveredCoordinate() {
        var end = new Cell(2, 0, 0);
        var expensive = new Cell(1, 0, 0);
        var detour = new Cell(0, 0, 1);
        var edges = blocked(START, true, false);
        edges.putAll(blocked(expensive, true, false));
        edges.putAll(blocked(detour, true, false));
        allow(edges, START, expensive, false, 10);
        allow(edges, START, detour, false, 1);
        allow(edges, detour, expensive, false, .1);
        allow(edges, expensive, end, false, 1);
        var inputs = input(end, 4, true, false, edges);
        var actual = suspendAtEveryEdge(inputs);
        assertEquals(List.of(START, expensive, end), actual.orderedNodes());
        assertEquals(List.of(10.0, 1.0), actual.graphEdgeCosts());
        assertEquals(4, actual.visitedCount());
        var recorded = new RecordingEdges(edges);
        GhostSourceWalkWorker.solve(inputs, recorded);
        assertFalse(recorded.completed.contains(new EdgeKey(detour, expensive, false)));
        assertFalse(recorded.completed.contains(new EdgeKey(expensive, START, false)));
    }

    @Test void everyMissingEdgePreservesResetDeepDropAndFirstAttemptCount() {
        var actual = suspendAtEveryEdge(deepDropFixture());
        assertEquals(Status.FOUND, actual.status());
        assertTrue(actual.dropRetry());
        assertEquals(1, actual.firstAttemptVisited());
        assertEquals(2, actual.visitedCount());
        assertEquals(List.of(START, new Cell(0, -3, 0)), actual.orderedNodes());
        assertEquals(List.of(7.0), actual.graphEdgeCosts());
    }

    @Test void exhaustedDropFrontierTerminatesWithoutAnotherRetry() {
        var edges = blocked(START, false, false);
        edges.putAll(blocked(START, false, true));
        var actual = suspendAtEveryEdge(input(new Cell(2, 0, 0), 6000, false, false, edges));
        assertEquals(Status.NO_UNVISITED, actual.status());
        assertTrue(actual.dropRetry());
        assertEquals(1, actual.firstAttemptVisited());
        assertEquals(1, actual.visitedCount());
    }

    @Test void emptyOpenIsCheckedBeforeCapAndFirstVolumeSuppressesDrop() {
        var actual = suspendAtEveryEdge(input(new Cell(2, 0, 0), 1, true, false, blocked(START, true, false)));
        assertEquals(Status.NO_UNVISITED, actual.status());
        assertFalse(actual.dropRetry());
        assertEquals(0, actual.firstAttemptVisited());
        assertEquals(1, actual.visitedCount());
    }

    @Test void secondVolumeFlagKeepsVolumeOffsetsButStillAllowsTheSourceRetry() {
        var edges = blocked(START, true, false);
        edges.putAll(blocked(START, true, true));
        var actual = suspendAtEveryEdge(input(new Cell(2, 0, 0), 1, false, true, edges));
        assertEquals(Status.NO_UNVISITED, actual.status());
        assertTrue(actual.dropRetry());
        assertEquals(1, actual.firstAttemptVisited());
        assertEquals(1, actual.visitedCount());
    }

    private static Inputs lineExhaustionFixture(int nodes) {
        var edges = new HashMap<EdgeKey, Edge>();
        for (int x = 0; x < nodes; x++) {
            var from = new Cell(x, 0, 0);
            edges.putAll(blocked(from, true, false));
            if (x + 1 < nodes) allow(edges, from, new Cell(x + 1, 0, 0), false, 1);
        }
        edges.putAll(blocked(START, true, true));
        return input(new Cell(1000, 0, 0), 1000, false, true, edges);
    }

    @Test void sourceRetryBoundaryRemainsStrictlyBelowFiveHundredVisits() {
        var below = suspendAtEveryEdge(lineExhaustionFixture(499));
        assertEquals(Status.NO_UNVISITED, below.status());
        assertTrue(below.dropRetry());
        assertEquals(499, below.firstAttemptVisited());
        assertEquals(1, below.visitedCount());
        var boundary = suspendAtEveryEdge(lineExhaustionFixture(500));
        assertEquals(Status.NO_UNVISITED, boundary.status());
        assertFalse(boundary.dropRetry());
        assertEquals(0, boundary.firstAttemptVisited());
        assertEquals(500, boundary.visitedCount());
    }

    @Test void missingCompletedObservationIsUnknownWithTheOriginalCounts() {
        var actual = suspendAtEveryEdge(input(new Cell(1, 0, 0), 6000, false, false, Map.of()));
        assertEquals(Status.UNKNOWN_INPUT, actual.status());
        assertEquals(1, actual.visitedCount());
        assertTrue(actual.orderedNodes().isEmpty());
        assertFalse(actual.dropRetry());
    }

    @Test void completedUnknownObservationNeverBecomesBlocked() {
        var edges = blocked(START, true, false);
        var first = new EdgeKey(START, new Cell(-1, 0, -1), false);
        edges.put(first, new Edge(Observed.UNKNOWN, Double.NaN));
        var actual = suspendAtEveryEdge(input(new Cell(2, 0, 0), 6000, true, false, edges));
        assertEquals(Status.UNKNOWN_INPUT, actual.status());
        assertEquals(1, actual.visitedCount());
        assertFalse(actual.dropRetry());
    }

    @Test void preflightFailureOrderMatchesOriginalBeforeAnyEdgeRequest() {
        var inputs = new Inputs(START, new Cell(1, 0, 0), 6000, 1, 1, false, false,
                Observed.NO, Observed.UNKNOWN, Observed.NO, Observed.UNKNOWN, Map.of());
        var actual = suspendAtEveryEdge(inputs);
        assertEquals(Status.START_NOT_AIR, actual.status());
        assertEquals(0, actual.visitedCount());
    }

    @Test void allPreflightFailurePositionsRetainTheOriginalStatusAndCounts() {
        var failures = List.of(Status.START_NOT_AIR, Status.END_NOT_AIR, Status.START_NOT_LOADED, Status.END_NOT_LOADED);
        for (int position = 0; position < 4; position++) {
            for (var value : List.of(Observed.NO, Observed.UNKNOWN)) {
                var checks = new ArrayList<>(List.of(Observed.YES, Observed.YES, Observed.YES, Observed.YES));
                checks.set(position, value);
                var inputs = new Inputs(START, new Cell(1, 0, 0), 2, 1, 1, false, false,
                        checks.get(0), checks.get(1), checks.get(2), checks.get(3), Map.of());
                var actual = suspendAtEveryEdge(inputs);
                assertEquals(value == Observed.UNKNOWN ? Status.UNKNOWN_INPUT : failures.get(position), actual.status());
                assertEquals(0, actual.visitedCount());
            }
        }
    }

    @Test void singleNodeGoalIsVisitedWithoutObservingAnyEdge() {
        var actual = suspendAtEveryEdge(input(START, 1, false, false, Map.of()));
        assertEquals(Status.FOUND, actual.status());
        assertEquals(1, actual.visitedCount());
        assertEquals(List.of(START), actual.orderedNodes());
        assertTrue(actual.graphEdgeCosts().isEmpty());
    }

    @Test void initialRevocationTerminatesAndCannotBeResurrected() {
        var edges = new RecordingEdges(Map.of());
        edges.current = false;
        var progress = new Progress();
        var continuation = new GhostSourceWalkGraphContinuation(capFixture(2), edges, progress);
        var actual = continuation.advance();
        assertEquals(Status.UNKNOWN_INPUT, actual.status());
        assertEquals(0, actual.visitedCount());
        assertEquals("GRAPH_UNKNOWN_INPUT", progress.cached().stage());
        int checks = edges.currentChecks;
        edges.current = true;
        assertSame(actual, continuation.advance());
        assertEquals(checks, edges.currentChecks);
        assertTrue(edges.completed.isEmpty());
    }

    @Test void suspendedRevocationRetainsTheAlreadyPoppedNodeAndNeverRetriesIt() {
        var inputs = capFixture(2);
        var edges = new MissingEdges(inputs.edges());
        var progress = new Progress();
        var continuation = new GhostSourceWalkGraphContinuation(inputs, edges, progress);
        var pending = assertThrows(GhostSourceWalkPendingCell.class, continuation::advance);
        assertEquals(1, progress.cached().visited());
        var heldAttempts = List.copyOf(edges.attempted);
        edges.current = false;
        var actual = continuation.advance();
        assertEquals(Status.UNKNOWN_INPUT, actual.status());
        assertEquals(1, actual.visitedCount());
        assertFalse(actual.dropRetry());
        assertEquals(0, actual.firstAttemptVisited());
        assertEquals(heldAttempts, edges.attempted);
        assertEquals(0, progress.cached().open());
        assertEquals("GRAPH_UNKNOWN_INPUT", progress.cached().stage());
        pending.availability().complete(null);
        edges.current = true;
        assertSame(actual, continuation.advance());
        assertEquals(heldAttempts, edges.attempted);
    }

    @Test void suspendedDropRevocationKeepsFirstAttemptAndCurrentAttemptCounts() {
        var inputs = deepDropFixture();
        var edges = new MissingEdges(inputs.edges());
        var continuation = new GhostSourceWalkGraphContinuation(inputs, edges, new Progress());
        while (true) {
            var pending = assertThrows(GhostSourceWalkPendingCell.class, continuation::advance);
            if (edges.attempted.getLast().dropRetry()) break;
            pending.availability().complete(null);
        }
        int attempts = edges.attempted.size();
        edges.current = false;
        var actual = continuation.advance();
        assertEquals(Status.UNKNOWN_INPUT, actual.status());
        assertTrue(actual.dropRetry());
        assertEquals(1, actual.firstAttemptVisited());
        assertEquals(1, actual.visitedCount());
        assertEquals(attempts, edges.attempted.size());
        assertSame(actual, continuation.advance());
    }

    @Test void invalidationDuringCompletedObservationWinsBeforeEdgeInsertion() {
        var inputs = capFixture(2);
        var original = GhostSourceWalkWorker.solve(inputs, new ObservedEdges() {
            boolean valid = true;
            @Override public boolean current() { return valid; }
            @Override public Edge observe(EdgeKey edge) { valid = false; return new Edge(Observed.YES, 1); }
        });
        var continuation = new GhostSourceWalkGraphContinuation(inputs, new ObservedEdges() {
            boolean valid = true;
            @Override public boolean current() { return valid; }
            @Override public Edge observe(EdgeKey edge) { valid = false; return new Edge(Observed.YES, 1); }
        }, null);
        var actual = continuation.advance();
        assertEquals(original, actual);
        assertEquals(Status.UNKNOWN_INPUT, actual.status());
        assertEquals(1, actual.visitedCount());
        assertTrue(actual.orderedNodes().isEmpty());
        assertSame(actual, continuation.advance());
    }

    @Test void terminalSuccessIsMemoizedAcrossLaterCurrentRevocation() {
        var inputs = capFixture(2);
        var edges = new RecordingEdges(inputs.edges());
        var continuation = new GhostSourceWalkGraphContinuation(inputs, edges, new Progress());
        var actual = continuation.advance();
        assertEquals(Status.FOUND, actual.status());
        var completed = List.copyOf(edges.completed);
        int checks = edges.currentChecks;
        edges.current = false;
        assertSame(actual, continuation.advance());
        assertEquals(checks, edges.currentChecks);
        assertEquals(completed, edges.completed);
    }

    @Test void observerRuntimeExceptionPropagatesUnchangedWithoutConsumingTheEdge() {
        var inputs = capFixture(2);
        var failure = new IllegalStateException("Independent observer failure");
        var attempted = new ArrayList<EdgeKey>();
        var edges = new ObservedEdges() {
            boolean fail = true;
            @Override public boolean current() { return true; }
            @Override public Edge observe(EdgeKey edge) {
                attempted.add(edge);
                if (fail) { fail = false; throw failure; }
                return inputs.edges().get(edge);
            }
        };
        var progress = new Progress();
        var continuation = new GhostSourceWalkGraphContinuation(inputs, edges, progress);
        assertSame(failure, assertThrows(IllegalStateException.class, continuation::advance));
        assertEquals(1, progress.cached().visited());
        var actual = continuation.advance();
        assertEquals(GhostSourceWalkWorker.solve(inputs), actual);
        assertEquals(attempted.get(0), attempted.get(1));
    }
}
