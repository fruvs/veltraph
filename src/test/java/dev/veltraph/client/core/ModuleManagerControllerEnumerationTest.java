package dev.veltraph.client.core;

import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.List;
import java.util.NoSuchElementException;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Arbitrary Module callbacks exercise the original complete two-phase enumeration as the oracle. */
class ModuleManagerControllerEnumerationTest {
    private enum Query {
        COUNT, FIRST;

        Object read(ModuleManager manager, boolean optimized) {
            if (this == COUNT) return optimized ? manager.enabledControllerCount() : manager.enabledControllers().size();
            Module first = optimized ? manager.firstEnabledController() : manager.enabledControllers().getFirst();
            return first.id();
        }
    }

    private static final class Probe implements Module {
        final String id;
        final List<String> trace;
        boolean enabled;
        boolean passive;
        Runnable enabledHook;
        Runnable passiveHook;

        Probe(String id, boolean enabled, boolean passive, List<String> trace) {
            this.id = id;
            this.enabled = enabled;
            this.passive = passive;
            this.trace = trace;
        }

        @Override public String id() { return id; }
        @Override public String displayName() { return id; }
        @Override public ModuleCategory category() { return ModuleCategory.INTERNAL; }
        @Override public boolean isEnabled() {
            trace.add("enabled:" + id);
            if (enabledHook != null) enabledHook.run();
            return enabled;
        }
        @Override public boolean isPassiveObserver() {
            trace.add("passive:" + id);
            if (passiveHook != null) passiveHook.run();
            return passive;
        }
        @Override public void setEnabled(boolean value) { enabled = value; }
        @Override public void onEnable() {}
        @Override public void onDisable() {}
        @Override public void onTick() {}
        @Override public MacroState state() { return enabled ? MacroState.RUNNING : MacroState.STOPPED; }
    }

    private static final class Fixture {
        final ModuleManager manager = new ModuleManager();
        final List<String> trace = new ArrayList<>();
        final boolean optimized;

        Fixture(boolean optimized) { this.optimized = optimized; }

        Probe add(String id, boolean enabled, boolean passive) {
            var module = new Probe(id, enabled, passive, trace);
            manager.register(module);
            return module;
        }

        Object read(Query query) { return query.read(manager, optimized); }
    }

    private interface Scenario {
        Object run(Fixture fixture, Query query);
    }

    private record Outcome(Object value, Class<? extends Throwable> failure, List<String> trace) {}

    private static Outcome outcome(boolean optimized, Query query, Scenario scenario) {
        var fixture = new Fixture(optimized);
        try {
            return new Outcome(scenario.run(fixture, query), null, List.copyOf(fixture.trace));
        } catch (Throwable failure) {
            return new Outcome(null, failure.getClass(), List.copyOf(fixture.trace));
        }
    }

    private static Outcome compare(Query query, Scenario scenario) {
        var reference = outcome(false, query, scenario);
        var actual = outcome(true, query, scenario);
        assertEquals(reference, actual, "Must preserve every enabled/role getter and the reference result or failure");
        return actual;
    }

    @Test void emptyRegistryRetainsCountZeroAndGetFirstException() {
        var count = compare(Query.COUNT, (fixture, query) -> fixture.read(query));
        assertEquals(0, count.value());
        assertTrue(count.trace().isEmpty());
        var first = compare(Query.FIRST, (fixture, query) -> fixture.read(query));
        assertEquals(NoSuchElementException.class, first.failure());
        assertTrue(first.trace().isEmpty());
    }

    @Test void disabledReferencesNeverReadTheirRole() {
        for (var query : Query.values()) {
            var actual = compare(query, (fixture, operation) -> {
                fixture.add("a", false, false);
                fixture.add("b", false, true);
                return fixture.read(operation);
            });
            assertEquals(List.of("enabled:a", "enabled:b"), actual.trace());
            if (query == Query.COUNT) assertEquals(0, actual.value());
            else assertEquals(NoSuchElementException.class, actual.failure());
        }
    }

    @Test void allPassiveReferencesAreCompletelyEnumeratedBeforeEmptyResult() {
        for (var query : Query.values()) {
            var actual = compare(query, (fixture, operation) -> {
                fixture.add("a", true, true);
                fixture.add("b", true, true);
                return fixture.read(operation);
            });
            assertEquals(List.of("enabled:a", "enabled:b", "passive:a", "passive:b"), actual.trace());
            if (query == Query.COUNT) assertEquals(0, actual.value());
            else assertEquals(NoSuchElementException.class, actual.failure());
        }
    }

    @Test void singleControllerAndObserversPreserveEncounterOrderAndBothPhases() {
        for (var query : Query.values()) {
            var actual = compare(query, (fixture, operation) -> {
                fixture.add("observer", true, true);
                fixture.add("owner", true, false);
                fixture.add("disabled", false, false);
                return fixture.read(operation);
            });
            assertEquals(List.of("enabled:observer", "enabled:owner", "enabled:disabled", "passive:observer", "passive:owner"), actual.trace());
            assertNull(actual.failure());
            assertEquals(query == Query.COUNT ? 1 : "owner", actual.value());
        }
    }

    @Test void multipleControllersDoNotShortCircuitRoleGetters() {
        for (var query : Query.values()) {
            var actual = compare(query, (fixture, operation) -> {
                fixture.add("first", true, false);
                fixture.add("second", true, false);
                fixture.add("observer", true, true);
                fixture.add("third", true, false);
                return fixture.read(operation);
            });
            assertEquals(List.of("enabled:first", "enabled:second", "enabled:observer", "enabled:third",
                    "passive:first", "passive:second", "passive:observer", "passive:third"), actual.trace());
            assertEquals(query == Query.COUNT ? 3 : "first", actual.value());
            assertNull(actual.failure());
        }
    }

    @Test void separateQueriesReadFreshDisableEnableAndRoleChanges() {
        for (var query : Query.values()) {
            var actual = compare(query, (fixture, operation) -> {
                var first = fixture.add("first", true, false);
                var observer = fixture.add("observer", true, true);
                var later = fixture.add("later", false, false);
                var before = fixture.read(operation);
                first.enabled = false;
                observer.passive = false;
                later.enabled = true;
                var after = fixture.read(operation);
                return List.of(before, after);
            });
            assertEquals(query == Query.COUNT ? List.of(1, 2) : List.of("first", "observer"), actual.value());
            assertEquals(List.of("enabled:first", "enabled:observer", "enabled:later", "passive:first", "passive:observer",
                    "enabled:first", "enabled:observer", "enabled:later", "passive:observer", "passive:later"), actual.trace());
        }
    }

    @Test void enabledGetterMutationsAffectLaterEnabledReadsExactlyOnce() {
        for (var query : Query.values()) {
            var actual = compare(query, (fixture, operation) -> {
                var first = fixture.add("first", true, true);
                var disabled = fixture.add("disabled", true, false);
                var newOwner = fixture.add("newOwner", false, false);
                first.enabledHook = () -> { disabled.enabled = false; newOwner.enabled = true; };
                return fixture.read(operation);
            });
            assertEquals(query == Query.COUNT ? 1 : "newOwner", actual.value());
            assertEquals(List.of("enabled:first", "enabled:disabled", "enabled:newOwner", "passive:first", "passive:newOwner"), actual.trace());
        }
    }

    @Test void roleGetterDisableEnableChangesPreserveTheEnabledReferenceSnapshot() {
        for (var query : Query.values()) {
            var actual = compare(query, (fixture, operation) -> {
                var observer = fixture.add("observer", true, true);
                var owner = fixture.add("owner", true, false);
                var newOwner = fixture.add("newOwner", false, false);
                observer.passiveHook = () -> { owner.enabled = false; newOwner.enabled = true; };
                var held = fixture.read(operation);
                observer.passiveHook = null;
                var fresh = fixture.read(operation);
                return List.of(held, fresh);
            });
            assertEquals(query == Query.COUNT ? List.of(1, 1) : List.of("owner", "newOwner"), actual.value());
            assertEquals(List.of("enabled:observer", "enabled:owner", "enabled:newOwner", "passive:observer", "passive:owner",
                    "enabled:observer", "enabled:owner", "enabled:newOwner", "passive:observer", "passive:newOwner"), actual.trace());
        }
    }

    @Test void earlierRoleCallbackCanChangeALaterRoleWithoutReadingEnabledAgain() {
        for (var query : Query.values()) {
            var actual = compare(query, (fixture, operation) -> {
                var observer = fixture.add("observer", true, true);
                var formerOwner = fixture.add("formerOwner", true, false);
                var newOwner = fixture.add("newOwner", true, true);
                observer.passiveHook = () -> { formerOwner.passive = true; newOwner.passive = false; };
                return fixture.read(operation);
            });
            assertEquals(query == Query.COUNT ? 1 : "newOwner", actual.value());
            assertEquals(List.of("enabled:observer", "enabled:formerOwner", "enabled:newOwner",
                    "passive:observer", "passive:formerOwner", "passive:newOwner"), actual.trace());
        }
    }

    @Test void registrationDuringRolePhaseAppearsOnlyInTheNextFreshQuery() {
        for (var query : Query.values()) {
            var actual = compare(query, (fixture, operation) -> {
                var first = fixture.add("first", true, false);
                fixture.add("observer", true, true);
                first.passiveHook = () -> { first.passiveHook = null; fixture.add("newOwner", true, false); };
                var before = fixture.read(operation);
                var after = fixture.read(operation);
                return List.of(before, after);
            });
            assertEquals(query == Query.COUNT ? List.of(1, 2) : List.of("first", "first"), actual.value());
            assertEquals(List.of("enabled:first", "enabled:observer", "passive:first", "passive:observer",
                    "enabled:first", "enabled:observer", "enabled:newOwner", "passive:first", "passive:observer", "passive:newOwner"), actual.trace());
            assertNull(actual.failure());
        }
    }

    @Test void registrationDuringEnabledPhaseKeepsOriginalFailFastException() {
        for (var query : Query.values()) {
            var actual = compare(query, (fixture, operation) -> {
                var first = fixture.add("first", true, false);
                fixture.add("second", true, false);
                first.enabledHook = () -> fixture.add("newOwner", true, false);
                return fixture.read(operation);
            });
            assertEquals(ConcurrentModificationException.class, actual.failure());
            assertEquals(List.of("enabled:first"), actual.trace());
        }
    }

    @Test void nestedQueryDuringEnabledCollectionCannotClearTheOuterPartialSnapshot() {
        for (var query : Query.values()) {
            var actual = compare(query, (fixture, operation) -> {
                fixture.add("first", true, false);
                var second = fixture.add("second", true, false);
                fixture.add("disabled", false, false);
                second.enabledHook = () -> {
                    second.enabledHook = null;
                    fixture.trace.add("nested-result:" + fixture.read(operation));
                };
                return fixture.read(operation);
            });
            var nested = query == Query.COUNT ? "nested-result:2" : "nested-result:first";
            assertEquals(List.of("enabled:first", "enabled:second", "enabled:first", "enabled:second", "enabled:disabled",
                    "passive:first", "passive:second", nested, "enabled:disabled", "passive:first", "passive:second"), actual.trace());
            assertEquals(query == Query.COUNT ? 2 : "first", actual.value());
            assertNull(actual.failure());
        }
    }

    @Test void nestedRoleQueryCanMutateFlagsWithoutChangingTheOuterEnabledSnapshot() {
        for (var query : Query.values()) {
            var actual = compare(query, (fixture, operation) -> {
                var first = fixture.add("first", true, true);
                var held = fixture.add("held", true, false);
                var newOwner = fixture.add("newOwner", false, false);
                first.passiveHook = () -> {
                    first.passiveHook = null;
                    held.enabled = false;
                    newOwner.enabled = true;
                    fixture.trace.add("nested-result:" + fixture.read(operation));
                };
                return fixture.read(operation);
            });
            var nested = query == Query.COUNT ? "nested-result:1" : "nested-result:newOwner";
            assertEquals(List.of("enabled:first", "enabled:held", "enabled:newOwner", "passive:first",
                    "enabled:first", "enabled:held", "enabled:newOwner", "passive:first", "passive:newOwner", nested, "passive:held"), actual.trace());
            assertEquals(query == Query.COUNT ? 1 : "held", actual.value());
            assertNull(actual.failure());
        }
    }

    @Test void threeReentrantDepthsRetainSeparateCompleteSnapshots() {
        for (var query : Query.values()) {
            var actual = compare(query, (fixture, operation) -> {
                var first = fixture.add("first", true, false);
                fixture.add("second", true, false);
                int[] remaining = {2};
                first.passiveHook = () -> {
                    if (remaining[0]-- > 0) fixture.trace.add("nested-result:" + fixture.read(operation));
                };
                return fixture.read(operation);
            });
            var nested = query == Query.COUNT ? "nested-result:2" : "nested-result:first";
            assertEquals(List.of("enabled:first", "enabled:second", "passive:first",
                    "enabled:first", "enabled:second", "passive:first",
                    "enabled:first", "enabled:second", "passive:first", "passive:second", nested, "passive:second", nested, "passive:second"), actual.trace());
            assertEquals(query == Query.COUNT ? 2 : "first", actual.value());
            assertNull(actual.failure());
        }
    }

    @Test void countAndFirstCanBeNestedInEachOtherWithoutChangingReadOrder() {
        for (var query : Query.values()) {
            var actual = compare(query, (fixture, operation) -> {
                var first = fixture.add("first", true, false);
                fixture.add("second", true, false);
                first.passiveHook = () -> {
                    first.passiveHook = null;
                    var nested = operation == Query.COUNT ? Query.FIRST : Query.COUNT;
                    fixture.trace.add("nested-result:" + fixture.read(nested));
                };
                return fixture.read(operation);
            });
            var nested = query == Query.COUNT ? "nested-result:first" : "nested-result:2";
            assertEquals(List.of("enabled:first", "enabled:second", "passive:first",
                    "enabled:first", "enabled:second", "passive:first", "passive:second", nested, "passive:second"), actual.trace());
            assertEquals(query == Query.COUNT ? 2 : "first", actual.value());
        }
    }

    @Test void laterRoleGetterFailureStillPropagatesAfterFindingTheFirstController() {
        for (var query : Query.values()) {
            var actual = compare(query, (fixture, operation) -> {
                fixture.add("first", true, false);
                var later = fixture.add("later", true, false);
                later.passiveHook = () -> { throw new IllegalStateException("later role getter"); };
                return fixture.read(operation);
            });
            assertEquals(IllegalStateException.class, actual.failure());
            assertEquals(List.of("enabled:first", "enabled:later", "passive:first", "passive:later"), actual.trace());
        }
    }

    @Test void thrownEnabledGetterReleasesPartiallyCollectedReferencesForTheNextQuery() {
        for (var query : Query.values()) {
            var fixture = new Fixture(true);
            var old = fixture.add("old", true, false);
            var later = fixture.add("later", true, false);
            var failure = new IllegalStateException("enabled getter");
            later.enabledHook = () -> { throw failure; };
            assertSame(failure, assertThrows(IllegalStateException.class, () -> fixture.read(query)));
            assertEquals(List.of("enabled:old", "enabled:later"), fixture.trace);
            fixture.trace.clear();
            old.enabled = false;
            later.enabledHook = null;
            assertEquals(query == Query.COUNT ? 1 : "later", fixture.read(query));
            assertEquals(List.of("enabled:old", "enabled:later", "passive:later"), fixture.trace);
        }
    }

    @Test void thrownRoleErrorReleasesReferencesAndIsNeverWrapped() {
        for (var query : Query.values()) {
            var fixture = new Fixture(true);
            var old = fixture.add("old", true, false);
            var later = fixture.add("later", true, false);
            var failure = new AssertionError("role getter");
            later.passiveHook = () -> { throw failure; };
            assertSame(failure, assertThrows(AssertionError.class, () -> fixture.read(query)));
            assertEquals(List.of("enabled:old", "enabled:later", "passive:old", "passive:later"), fixture.trace);
            fixture.trace.clear();
            old.enabled = false;
            later.passiveHook = null;
            assertEquals(query == Query.COUNT ? 1 : "later", fixture.read(query));
            assertEquals(List.of("enabled:old", "enabled:later", "passive:later"), fixture.trace);
        }
    }

    @Test void caughtNestedGetterFailureReleasesOnlyTheNestedBorrow() {
        for (var query : Query.values()) {
            var actual = compare(query, (fixture, operation) -> {
                var first = fixture.add("first", true, false);
                var second = fixture.add("second", true, false);
                first.passiveHook = () -> {
                    first.passiveHook = null;
                    second.enabledHook = () -> { throw new IllegalStateException("nested enabled getter"); };
                    try {
                        fixture.read(operation);
                    } catch (IllegalStateException expected) {
                        fixture.trace.add("nested-failure");
                    } finally {
                        second.enabledHook = null;
                    }
                };
                return fixture.read(operation);
            });
            assertEquals(List.of("enabled:first", "enabled:second", "passive:first",
                    "enabled:first", "enabled:second", "nested-failure", "passive:second"), actual.trace());
            assertEquals(query == Query.COUNT ? 2 : "first", actual.value());
            assertNull(actual.failure());
        }
    }

    @Test void registrationFailureCleanupDoesNotRetainOrDuplicateOldReferences() {
        for (var query : Query.values()) {
            var fixture = new Fixture(true);
            var first = fixture.add("first", true, false);
            fixture.add("second", true, false);
            first.enabledHook = () -> fixture.add("newOwner", true, false);
            assertThrows(ConcurrentModificationException.class, () -> fixture.read(query));
            first.enabledHook = null;
            first.enabled = false;
            fixture.trace.clear();
            assertEquals(query == Query.COUNT ? 2 : "second", fixture.read(query));
            assertEquals(List.of("enabled:first", "enabled:second", "enabled:newOwner", "passive:second", "passive:newOwner"), fixture.trace);
        }
    }

    @Test void separateCountThenFirstChecksAlwaysPerformFreshCompleteEnumeration() {
        var actual = compare(Query.COUNT, (fixture, ignored) -> {
            var old = fixture.add("old", true, false);
            var replacement = fixture.add("replacement", false, false);
            var count = fixture.read(Query.COUNT);
            old.enabled = false;
            replacement.enabled = true;
            var first = fixture.read(Query.FIRST);
            return List.of(count, first);
        });
        assertEquals(List.of(1, "replacement"), actual.value());
        assertEquals(List.of("enabled:old", "enabled:replacement", "passive:old",
                "enabled:old", "enabled:replacement", "passive:replacement"), actual.trace());
    }
}
