package dev.veltraph.client.hydra;

/** Test-only delegation to the original pure event factories without widening production API. */
public final class HydraEventTestFixtures {
    private HydraEventTestFixtures() {}

    public static HydraEvent floorChange(boolean stop) {
        return HydraEvent.floorChange(stop);
    }

    public static HydraEvent staffNearby(String name, double distance, HydraSeverity severity, boolean stop) {
        return HydraEvent.staffNearby(name, distance, severity, stop);
    }
}
