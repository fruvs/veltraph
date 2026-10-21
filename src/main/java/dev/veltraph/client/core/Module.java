package dev.veltraph.client.core;

import dev.veltraph.client.game.GameArea;

import java.util.Optional;

/**
 * A toggleable automation unit. Matches the interface sketched in {@code REBUILD_PLAN.md}.
 *
 * <p>Lifecycle: {@link #setEnabled(boolean)} flips the enabled flag and invokes {@link #onEnable()} /
 * {@link #onDisable()}. {@link #onTick()} runs once per client tick while enabled. Implementations
 * must release all inputs in {@link #onDisable()} (safety contract).
 *
 * <p>Location gating: {@link #requiredArea()} / {@link #requiresSkyBlock()} declare the recovered
 * world requirements (see {@code docs/LOCATION_GATES.md}); the activation guard enforces them.
 */
public interface Module {

    String id();

    String displayName();

    ModuleCategory category();

    boolean isEnabled();

    void setEnabled(boolean enabled);

    void onEnable();

    void onDisable();

    /** Called once per client tick while enabled. */
    void onTick();

    /** Read-only/GUI helpers can leave manual gameplay input available while waiting for their screen. */
    default boolean ownsGameplayInput() { return true; }

    /** A read-only observer never drives gameplay, menus, travel or inventory actions. */
    default boolean isPassiveObserver() { return false; }

    /** Bounded restitution of this workflow's owned item after stop; never starts new work. */
    default void maintenanceTick() {}

    /** Reserve interaction while an acknowledged owned-item transfer is being undone. */
    default boolean hasPendingCleanup() { return false; }

    /** Only explicitly declared handled-screen titles may remain open while this module runs. */
    default boolean allowsScreen(String title) { return false; }
    /** Received server/game message, never an outgoing chat action. */
    default void onServerMessage(String line) {}
    /** Server action-bar observation, separate from chat acknowledgments. */
    default void onActionBar(String line) {}

    /** Consume a narrowly matched acknowledgment of this module's own issued travel action. */
    default boolean consumeExpectedTeleport(dev.veltraph.client.world.Vec3 position,long now) { return false; }

    /** Current run-lifecycle state for HUD/debug display. */
    MacroState state();

    /** The SkyBlock area this module requires, if any (e.g. farming → GARDEN). Default: none. */
    default Optional<GameArea> requiredArea() {
        return Optional.empty();
    }

    /**
     * Finite known source areas for this disabled parent's owned ingress start. Target-area and
     * running-location requirements remain declared by requiredArea; ordinary modules admit none.
     */
    default java.util.Set<GameArea> ingressStartAreas() { return java.util.Set.of(); }

    /** Whether this module requires being in Hypixel SkyBlock at all. Default: false. */
    default boolean requiresSkyBlock() {
        return false;
    }

    /**
     * Whether the module has a documented travel/setup phase during which it may legitimately be
     * outside its target area. If false, the runtime stops the module when the location becomes
     * invalid. Default: false.
     */
    default boolean hasTravelSetupPhase() {
        return false;
    }

    /**
     * Pre-start readiness check, run by the activation guard after the location gate passes and before
     * the module is enabled. Returns a chat-ready error to block the start (e.g. required tool missing),
     * or empty to allow it. May perform start-time setup (e.g. selecting the tool slot). Default: empty.
     */
    default java.util.Optional<String> preStartCheck() {
        return java.util.Optional.empty();
    }

    /** Multi-line live diagnostics for {@code /vdebug macro}. Default: id + enabled flag. */
    default String diagnostics() {
        return id() + " " + (isEnabled() ? "ON" : "off");
    }

    /**
     * A single concise lane/pause-state line for the in-world HUD (Target 5) — NOT the full multi-line
     * {@link #diagnostics()} dump. Defaults to the first line of {@link #diagnostics()} so existing
     * modules need no change; the HUD keeps it short. Pure (no Minecraft).
     */
    default String statusLine() {
        String diag = diagnostics();
        if (diag == null || diag.isEmpty()) {
            return "";
        }
        int nl = diag.indexOf('\n');
        return nl < 0 ? diag : diag.substring(0, nl);
    }
}
