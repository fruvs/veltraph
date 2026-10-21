package dev.veltraph.client.service;

/**
 * Bundle of the shared services a farming planner/module needs. Passing one aggregate keeps planner
 * constructors small and makes it trivial to inject fakes in tests.
 */
public record FarmServices(
        ClockService clock,
        PlayerService player,
        WorldService world,
        InputService input,
        RotationService rotation,
        BlockBreakingService breaking,
        InventoryService inventory) {
}
