package dev.veltraph.client.farming.movement.planner;

import dev.veltraph.client.config.ConfigService;
import dev.veltraph.client.config.FarmingConfig;
import dev.veltraph.client.config.LaneSide;
import dev.veltraph.client.farming.movement.TurnDirection;

/**
 * Sugarcane (and shared-code Flowers) back/opposite traversal planner
 * (see {@code reports/_spec_parts/FarmingMovementPlannerHotspots.md} §5c and
 * {@code reports/_spec_parts/FarmingSugarcaneFlowersLayout.md}).
 *
 * <p>Recovered behavior (CONFIRMED, §5c — "no classical/S-shape mode"):
 * <ul>
 *   <li>The only mode knob is {@code SUGARCANE_DIR} (a {@code {LEFT, RIGHT}} lane-side, default LEFT;
 *       registry init :724). There is NO classical vs S-shape named mode.</li>
 *   <li>Back/opposite toggle ({@code llIllIlIlllIIIII…}, :459-471):
 *       {@code currentDir = (currentDir == SUGARCANE_DIR.get()) ? BACK : SUGARCANE_DIR.get()} — each
 *       lane-switch flips the configured side &harr; BACK (the return leg).</li>
 *   <li>Back-leg detector ({@code IllIIIlIIlIlll…}, :538-550): {@code currentDir != SUGARCANE_DIR.get()}.</li>
 *   <li>Aim-yaw offset ({@code lIIIllIIIIlIlIIlII…}, :481-506; javap-confirmed
 *       {@code ifeq -> bipush 45 / bipush -45 / i2d / dadd / dreturn}):
 *       {@code aimYaw = baseYaw + (SUGARCANE_DIR == LEFT ? +45 : -45)}.</li>
 * </ul>
 *
 * <p>Pure (config only) and fully unit-testable.
 */
public final class SugarcanePlanner {

    private final ConfigService config;
    private TurnDirection currentDir;

    public SugarcanePlanner(ConfigService config) {
        this.config = config;
        this.currentDir = configuredDir();
    }

    private TurnDirection configuredDir() {
        return TurnDirection.fromLaneSide(config.get(FarmingConfig.SUGARCANE_DIR));
    }

    /** Reset to the configured starting side. */
    public void reset() {
        this.currentDir = configuredDir();
    }

    /** Toggle between the configured side and the BACK return leg (§5c :459-471). */
    public void onLaneSwitch() {
        TurnDirection base = configuredDir();
        this.currentDir = (currentDir == base) ? TurnDirection.BACK : base;
    }

    public TurnDirection currentDir() {
        return currentDir;
    }

    /** True while traversing the return (BACK) leg (§5c :538-550). */
    public boolean isOnBackLeg() {
        return currentDir != configuredDir();
    }

    /** Literal lateral input; BACK has no lateral key. Original virtual input bytecode confirms this. */
    public TurnDirection strafeSide() {
        return isOnBackLeg() ? null : configuredDir();
    }

    /** Aim yaw: {@code baseYaw + 45} when the configured side is LEFT, otherwise {@code baseYaw - 45}. */
    public double aimYaw(double baseYaw) {
        return baseYaw + (config.get(FarmingConfig.SUGARCANE_DIR) == LaneSide.LEFT ? 45.0 : -45.0);
    }

    /** Passive exact maintenance checkpoint. No services, geometry, clocks or planner callbacks run. */
    public dev.veltraph.client.farming.module.FarmMaintenanceState maintenanceState() {
        return dev.veltraph.client.farming.module.FarmMaintenanceState.of(this,
                "config", config,
                "currentDir", currentDir);
    }
}
