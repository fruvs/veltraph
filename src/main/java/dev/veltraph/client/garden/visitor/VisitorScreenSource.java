package dev.veltraph.client.garden.visitor;

import java.util.Optional;

/**
 * Read-only source of the currently-open Garden visitor offer. The MC adapter
 * ({@code garden/visitor/mc/McVisitorScreenSource}) reads the open visitor {@code HandledScreen} and its
 * item slots' lore; tests supply a fake. Keeping this an interface keeps the pure {@link VisitorAdvisor}
 * Minecraft-free and unit-testable.
 *
 * <p><b>Read-only.</b> Implementations only inspect the open screen; they never click, buy, or serve.
 */
@FunctionalInterface
public interface VisitorScreenSource {

    /** The offer from the open visitor menu, or empty when no visitor menu is open / unreadable. */
    Optional<VisitorOffer> read();

    /** Explicit history status; false isNew alone must not be presented as confirmed SEEN. */
    default dev.veltraph.client.garden.visitor.history.VisitorHistoryStore.Observation historyObservation(){
        return dev.veltraph.client.garden.visitor.history.VisitorHistoryResolver.UNKNOWN.observe("");
    }

    /** A source that never sees a visitor menu (test/default). */
    VisitorScreenSource NONE = Optional::empty;
}
