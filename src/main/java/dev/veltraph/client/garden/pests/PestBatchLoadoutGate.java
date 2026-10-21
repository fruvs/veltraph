package dev.veltraph.client.garden.pests;

import dev.veltraph.client.garden.loadout.NamedLoadoutFlow;
import dev.veltraph.client.gui.MenuSnapshot;

/** Refusal gate around the ordinary batch flow. This does not grant retained-loadout custody. */
public final class PestBatchLoadoutGate {
    private PestBatchLoadoutGate() {}

    /** Check the captured original before the next tick can publish its first equipment click. */
    public static PestMaintenanceRuntime.Progress tick(NamedLoadoutFlow flow, boolean restoring, String farmingName, long tick) {
        if (flow == null || !originalAllowed(flow, farmingName)) return PestMaintenanceRuntime.Progress.FAILED;
        flow.tick(tick);
        if (!originalAllowed(flow, farmingName)) return PestMaintenanceRuntime.Progress.FAILED;
        if (flow.active()) return PestMaintenanceRuntime.Progress.ACTIVE;
        return flow.state() == (restoring ? NamedLoadoutFlow.State.RESTORED : NamedLoadoutFlow.State.APPLIED)
            ? PestMaintenanceRuntime.Progress.COMPLETE : PestMaintenanceRuntime.Progress.FAILED;
    }

    private static boolean originalAllowed(NamedLoadoutFlow flow, String farmingName) {
        String expected = MenuSnapshot.clean(farmingName);
        if (expected.isBlank()) return false;
        var original = flow.original();
        if (original != null) return original.name().equalsIgnoreCase(expected);
        // WAIT_MENU is the only phase that can capture an original. Its tick returns before SWAP_LOADOUT.
        return flow.state() == NamedLoadoutFlow.State.OPEN_LOADOUT || flow.state() == NamedLoadoutFlow.State.WAIT_MENU;
    }
}
