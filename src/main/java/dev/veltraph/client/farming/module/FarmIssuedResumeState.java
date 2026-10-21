package dev.veltraph.client.farming.module;

/** Family-minted immutable semantics, never a native permission or a resume capability. */
public final class FarmIssuedResumeState {
    private final FarmMaintenanceState state;
    private FarmIssuedResumeState(FarmMaintenanceState state) { this.state=state; }
    // Only farming family sources in this package can mint a snapshot.
    static FarmIssuedResumeState of(FarmModule owner,Object... namedFields) {
        return new FarmIssuedResumeState(FarmMaintenanceState.of(owner,namedFields));
    }
    @Override public boolean equals(Object other) {
        return other instanceof FarmIssuedResumeState snapshot && state.equals(snapshot.state);
    }
    @Override public int hashCode() { return state.hashCode(); }
}
