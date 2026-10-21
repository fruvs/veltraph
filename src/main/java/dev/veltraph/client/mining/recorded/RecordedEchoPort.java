package dev.veltraph.client.mining.recorded;

import dev.veltraph.client.mining.MiningServices;

/** Vanilla placement adapter owns a component-pinned hotbar item and one acknowledged outcome. */
public interface RecordedEchoPort {
    MiningServices mining();
    /** Empty means safe to begin or continue the exact placement; otherwise release and pause. */
    String placementProblem(RecordedEchoProfile.Placement placement);
    /** True only after both the bound block appeared and exactly one pinned material was consumed. */
    boolean placementComplete(RecordedEchoProfile.Placement placement);
    /** Ordinary useItemOn against the currently observed native ray. False while aiming. */
    boolean place(RecordedEchoProfile.Placement placement);
    /** Clears use input and restores only this adapter's still-owned hotbar selection. */
    void releasePlacement();
}
