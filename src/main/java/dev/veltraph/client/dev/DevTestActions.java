package dev.veltraph.client.dev;

import dev.veltraph.client.failsafe.response.FailsafeResponseType;
import dev.veltraph.client.failsafe.response.FailsafeRoutine;
import dev.veltraph.client.hydra.HydraEvent;
import dev.veltraph.client.hydra.response.HydraReaction;

/**
 * Builds the standard {@link DevTestRegistry} of local developer test actions. Each action exercises an
 * <b>already-implemented</b> Veltraph system through the {@link DevTestContext} seam:
 *
 * <ul>
 *   <li>GENERAL — macro stop / input release / context probe;</li>
 *   <li>FAILSAFE — the anomaly classes the live anti-staff detector reacts to (wrong-location gate,
 *       GUI-open stop, world/dimension change, limbo/routing, teleport/lagback, teleport-into-block,
 *       forced-adventure, floor-change, player-nearby), plus rewarp dry-run + explicit execute;</li>
 *   <li>HYDRA — the local staff-response reactions (passive alert, stop-macro, chat reply dry-run/execute,
 *       fix-angle, after-disconnect / avoid-evacuation re-route dry-run + execute, rare/parity
 *       classification, barrier/floor-change response class);</li>
 *   <li>CHAT — local chat output, server chat dry-run + explicit execute.</li>
 * </ul>
 *
 * Pure: the actions only call the seam, so they are unit-testable with a recording fake context. The
 * server-bound execute actions are {@link DevTestRisk#SERVER_BOUND} and never run unless the registry's
 * explicit-allow gate is set by the labeled Execute control.
 */
public final class DevTestActions {

    private DevTestActions() {
    }

    /** Build a fresh registry populated with the standard local test actions. */
    public static DevTestRegistry standard() {
        DevTestRegistry r = new DevTestRegistry();

        // ---- GENERAL ----
        r.register(DevTestAction.of("general.stop", "Stop Macros + Release Input",
                "Stop all running macros and release script-owned input.",
                DevTestCategory.GENERAL, DevTestRisk.LOCAL_ONLY, ctx -> {
                    int n = ctx.stopAllMacros();
                    ctx.releaseInput();
                    return DevTestResult.local("stopped " + n + " macro(s); input released");
                }));
        r.register(DevTestAction.of("general.release_input", "Release Input Only",
                "Release all script-owned input without stopping macros.",
                DevTestCategory.GENERAL, DevTestRisk.LOCAL_ONLY, ctx -> {
                    ctx.releaseInput();
                    return DevTestResult.local("input released");
                }));
        r.register(DevTestAction.of("general.context", "Print Context",
                "Print the current area / SkyBlock context + module state to local chat.",
                DevTestCategory.GENERAL, DevTestRisk.LOCAL_ONLY, ctx -> {
                    String msg = ctx.contextSummary() + " | " + ctx.moduleStateSummary();
                    ctx.localChat("[Veltraph Dev] " + msg);
                    return DevTestResult.local(msg);
                }));

        // ---- FAILSAFE ----
        r.register(DevTestAction.of("failsafe.location_gate", "Wrong-Location Gate",
                "Evaluate the activation gate for a representative module against the live area.",
                DevTestCategory.FAILSAFE, DevTestRisk.LOCAL_ONLY, ctx -> {
                    String decision = ctx.gateDecision();
                    ctx.localChat("[Veltraph Dev] gate: " + decision);
                    return DevTestResult.local("gate: " + decision);
                }));
        r.register(DevTestAction.of("failsafe.gui_stop", "GUI-Open Auto-Stop",
                "Simulate the GUI-open safety stop (stop macros + release input).",
                DevTestCategory.FAILSAFE, DevTestRisk.LOCAL_ONLY, ctx -> {
                    int n = ctx.stopAllMacros();
                    ctx.releaseInput();
                    return DevTestResult.local("GUI-open stop: stopped " + n + " macro(s); input released");
                }));
        r.register(alert("failsafe.world_change", "World / Dimension Anomaly",
                "Run the local alert for an unexpected world/dimension change.",
                DevTestCategory.FAILSAFE, HydraEvent.Type.WORLD_ANOMALY));
        r.register(alert("failsafe.limbo", "Limbo / Disconnect Routing",
                "Run the local alert for being routed to Limbo / disconnect.",
                DevTestCategory.FAILSAFE, HydraEvent.Type.LIMBO_ROUTING));
        r.register(alert("failsafe.routing_text", "Routing / Kick Text",
                "Run the local alert for a received routing/kick chat line.",
                DevTestCategory.FAILSAFE, HydraEvent.Type.ROUTING_TEXT));
        r.register(alert("failsafe.teleport", "Teleport Anomaly",
                "Run the local alert for a large one-tick position jump.",
                DevTestCategory.FAILSAFE, HydraEvent.Type.TELEPORT_ANOMALY));
        r.register(alert("failsafe.lagback", "Invalid Movement / Lagback",
                "Run the local alert for a server rubber-band (lagback).",
                DevTestCategory.FAILSAFE, HydraEvent.Type.INVALID_MOVEMENT));
        r.register(alert("failsafe.teleport_into_block", "Teleport Into Block",
                "Run the local alert for a teleport into an enclosed space.",
                DevTestCategory.FAILSAFE, HydraEvent.Type.TELEPORT_INTO_BLOCK));
        r.register(alert("failsafe.forced_adventure", "Forced Adventure Mode",
                "Run the local alert for a forced gamemode change.",
                DevTestCategory.FAILSAFE, HydraEvent.Type.FORCED_ADVENTURE));
        r.register(alert("failsafe.floor_change", "Floor-Change Anomaly",
                "Run the local alert for repeated floor/block changes under the player.",
                DevTestCategory.FAILSAFE, HydraEvent.Type.FLOOR_CHANGE));
        r.register(alert("failsafe.barrier", "Barrier In Path",
                "Run the local alert for a block newly placed in your forward path.",
                DevTestCategory.FAILSAFE, HydraEvent.Type.BARRIER));
        r.register(alert("failsafe.player_nearby", "Player-Nearby / Staff Proximity",
                "Run the local alert for a non-whitelisted player within the alert distance.",
                DevTestCategory.FAILSAFE, HydraEvent.Type.STAFF_NEARBY));
        r.register(DevTestAction.of("failsafe.rewarp_dryrun", "Rewarp (Dry-Run)",
                "Show the configured rewarp command that would be sent (does not send).",
                DevTestCategory.FAILSAFE, DevTestRisk.DRY_RUN, ctx ->
                        DevTestResult.dryRun("would send /" + ctx.rewarpCommand())));
        r.register(DevTestAction.of("failsafe.rewarp_execute", "Rewarp (EXECUTE — sends command)",
                "Actually send the configured rewarp command to the server.",
                DevTestCategory.FAILSAFE, DevTestRisk.SERVER_BOUND, ctx -> {
                    String cmd = ctx.rewarpCommand();
                    boolean sent = ctx.serverCommand(cmd);
                    return sent ? DevTestResult.executed("sent /" + cmd)
                            : DevTestResult.noOp("could not send /" + cmd + " (not connected)");
                }));

        // ---- HYDRA / ANTI-STAFF ----
        r.register(alert("hydra.passive_alert", "Passive Staff Alert",
                "Run the passive anti-staff alert (local chat/title/sound, no stop).",
                DevTestCategory.HYDRA, HydraEvent.Type.STAFF_NEARBY));
        r.register(DevTestAction.of("hydra.stop_macro", "Stop-Macro Reaction (STOP_ALL)",
                "Run the STOP_ALL reaction: stop macros + release input.",
                DevTestCategory.HYDRA, DevTestRisk.LOCAL_ONLY, ctx -> {
                    int n = ctx.stopAllMacros();
                    ctx.releaseInput();
                    return DevTestResult.local("STOP_ALL: stopped " + n + " macro(s); input released");
                }));
        r.register(DevTestAction.of("hydra.chat_reply_dryrun", "Chat Reply (Dry-Run)",
                "Show the staff-reply line that would be sent (does not send).",
                DevTestCategory.HYDRA, DevTestRisk.DRY_RUN, ctx ->
                        DevTestResult.dryRun("would reply: \"" + ctx.firstReplyTemplate() + "\"")));
        r.register(DevTestAction.of("hydra.chat_reply_execute", "Chat Reply (EXECUTE — sends chat)",
                "Actually send a staff-reply line to the server.",
                DevTestCategory.HYDRA, DevTestRisk.SERVER_BOUND, ctx -> {
                    String line = ctx.firstReplyTemplate();
                    boolean sent = ctx.serverChat(line);
                    return sent ? DevTestResult.executed("sent chat: \"" + line + "\"")
                            : DevTestResult.noOp("could not send chat (not connected)");
                }));
        r.register(DevTestAction.of("hydra.fix_angle", "Fix-Angle / Auto-Angle",
                "Smoothly ease the local camera to a visible target at the far edge of the fix-angle window "
                        + "(away from the current pitch), so the smooth auto-angle is actually visible. Local only.",
                DevTestCategory.HYDRA, DevTestRisk.LOCAL_ONLY, ctx -> {
                    double min = ctx.fixAngleMinPitch();
                    double max = ctx.fixAngleMaxPitch();
                    double cur = ctx.currentPitch();
                    double targetPitch = visibleFixAnglePitch(cur, min, max);
                    // Sweep the yaw too (deterministic 45° offset) so the move is unmistakably visible.
                    double curYaw = ctx.currentYaw();
                    double targetYaw = Double.isNaN(curYaw) ? Double.NaN : curYaw + 45.0;
                    ctx.fixAngle(targetYaw, targetPitch);
                    return DevTestResult.local("fix-angle (smooth): cur=(" + fmt(curYaw) + ", " + fmt(cur)
                            + ") -> target=(" + fmt(targetYaw) + ", " + fmt(targetPitch) + ") window "
                            + fmt(min) + ".." + fmt(max));
                }));
        r.register(DevTestAction.of("hydra.after_disconnect_dryrun", "After-Disconnect Re-Route (Dry-Run)",
                "Show the after-disconnect re-route command (does not send).",
                DevTestCategory.HYDRA, DevTestRisk.DRY_RUN, ctx ->
                        DevTestResult.dryRun("would run /" + ctx.afterDisconnectCommand())));
        r.register(DevTestAction.of("hydra.avoid_evac_dryrun", "Avoid-Evacuation Re-Route (Dry-Run)",
                "Show the avoid-evacuation re-route command (does not send).",
                DevTestCategory.HYDRA, DevTestRisk.DRY_RUN, ctx ->
                        DevTestResult.dryRun("would run /" + ctx.avoidEvacuationCommand())));
        r.register(DevTestAction.of("hydra.reroute_execute", "Re-Route (EXECUTE — sends command)",
                "Actually send the after-disconnect re-route command to the server.",
                DevTestCategory.HYDRA, DevTestRisk.SERVER_BOUND, ctx -> {
                    String cmd = ctx.afterDisconnectCommand();
                    boolean sent = ctx.serverCommand(cmd);
                    return sent ? DevTestResult.executed("sent /" + cmd)
                            : DevTestResult.noOp("could not send /" + cmd + " (not connected)");
                }));
        r.register(DevTestAction.of("hydra.rare_parity", "Rare / Parity Classification",
                "Log the rare/parity predicates + response eligibility for a parity-class check.",
                DevTestCategory.HYDRA, DevTestRisk.LOCAL_ONLY, ctx -> {
                    String c = ctx.classifyHydra(HydraEvent.Type.FLOOR_CHANGE);
                    ctx.localChat("[Veltraph Dev] parity " + (ctx.parityMode() ? "ON" : "OFF") + ": " + c);
                    return DevTestResult.local("parity=" + ctx.parityMode() + " " + c);
                }));
        r.register(alert("hydra.barrier_floor", "Barrier / Floor-Change Response Class",
                "Run the floor-change response-class local alert.",
                DevTestCategory.HYDRA, HydraEvent.Type.FLOOR_CHANGE));

        // Movement reactions: queue a local CAGE/BARRIER/FLOOR_CHANGE/BACKUP reaction (local key inputs only).
        // Advanced over the following ticks by the Developer Mode dev-test tick path (no macro required).
        r.register(reactionAction("hydra.reaction_cage", "Movement Reaction — CAGE",
                "jump to break free + back-hop out", HydraReaction.CAGE));
        r.register(reactionAction("hydra.reaction_barrier", "Movement Reaction — BARRIER",
                "back off the barrier + strafe around it", HydraReaction.BARRIER));
        r.register(reactionAction("hydra.reaction_floor_change", "Movement Reaction — FLOOR_CHANGE",
                "back-hop to stable ground", HydraReaction.FLOOR_CHANGE));
        r.register(reactionAction("hydra.reaction_backup", "Movement Reaction — BACKUP",
                "back-pedal away from a push", HydraReaction.BACKUP));

        // ---- FAILSAFE AUTOMATED RESPONSE (local routine: look / move / jump / sneak / clicks) ----
        // Per-trigger response plans (queue a routine; advanced by the dev-test tick path, no macro needed).
        for (FailsafeResponseType type : FailsafeResponseType.values()) {
            final FailsafeResponseType t = type;
            r.register(DevTestAction.of("failsafe.response." + t.name().toLowerCase(java.util.Locale.ROOT),
                    "Response: " + titleCase(t.name()),
                    "Queue the local automated failsafe response routine for " + t.name()
                            + " (look around + move/jump/clicks; local input only).",
                    DevTestCategory.FAILSAFE, DevTestRisk.LOCAL_ONLY, ctx ->
                            DevTestResult.local(ctx.runFailsafeResponse(t))));
        }
        // Focused routine tests.
        for (FailsafeRoutine routine : FailsafeRoutine.values()) {
            final FailsafeRoutine ro = routine;
            r.register(DevTestAction.of("failsafe.routine." + ro.name().toLowerCase(java.util.Locale.ROOT),
                    "Routine: " + titleCase(ro.name()),
                    "Queue the focused " + ro.name() + " local routine (no macro needed).",
                    DevTestCategory.FAILSAFE, DevTestRisk.LOCAL_ONLY, ctx ->
                            DevTestResult.local(ctx.runFailsafeRoutine(ro))));
        }

        // ---- CHAT / COMMAND ----
        r.register(DevTestAction.of("chat.local", "Local Chat Output",
                "Write a line to the local client chat HUD only (never sent to the server).",
                DevTestCategory.CHAT, DevTestRisk.LOCAL_ONLY, ctx -> {
                    ctx.localChat("[Veltraph Dev] local chat output test");
                    return DevTestResult.local("wrote a local chat line");
                }));
        r.register(DevTestAction.of("chat.server_dryrun", "Server Chat (Dry-Run)",
                "Show the chat message that would be sent to the server (does not send).",
                DevTestCategory.CHAT, DevTestRisk.DRY_RUN, ctx ->
                        DevTestResult.dryRun("would send chat: \"Veltraph dev test\"")));
        r.register(DevTestAction.of("chat.server_execute", "Server Chat (EXECUTE — sends to server)",
                "Actually send a test chat message to the server.",
                DevTestCategory.CHAT, DevTestRisk.SERVER_BOUND, ctx -> {
                    boolean sent = ctx.serverChat("Veltraph dev test");
                    return sent ? DevTestResult.executed("sent chat: \"Veltraph dev test\"")
                            : DevTestResult.noOp("could not send chat (not connected)");
                }));

        return r;
    }

    /** A LOCAL_ONLY action that runs the real anti-staff alert path for one synthetic event type. */
    private static DevTestAction alert(String id, String label, String desc, DevTestCategory cat,
                                       HydraEvent.Type type) {
        return DevTestAction.of(id, label, desc, cat, DevTestRisk.LOCAL_ONLY, ctx ->
                DevTestResult.local(ctx.simulateHydraAlert(type)));
    }

    /** A LOCAL_ONLY action that queues a local movement reaction (advanced by the dev-test tick path). */
    private static DevTestAction reactionAction(String id, String label, String shape, HydraReaction reaction) {
        return DevTestAction.of(id, label, "Queue the local " + reaction + " movement reaction (" + shape
                + "; local key inputs only, no macro required).",
                DevTestCategory.HYDRA, DevTestRisk.LOCAL_ONLY, ctx ->
                        DevTestResult.local(ctx.runReaction(reaction)));
    }

    private static String fmt(double v) {
        return Double.isNaN(v) ? "keep" : String.format(java.util.Locale.ROOT, "%.1f", v);
    }

    /**
     * Pick a <b>visible</b>, deterministic fix-angle target pitch within the configured {@code [min,max]}
     * window, away from the current pitch — never the midpoint (which produced little/no movement). Chooses
     * the far edge from the current pitch (so the camera travel is maximal); if the current pitch is unknown
     * it targets {@code max}. Falls back to {@code +30} / {@code -90..90} clamp if the window is degenerate
     * (min == max) so the test still moves. Pure — unit-tested.
     */
    static double visibleFixAnglePitch(double currentPitch, double min, double max) {
        double lo = Math.min(min, max);
        double hi = Math.max(min, max);
        if (lo == hi) {
            // Degenerate window: still produce a visible move relative to the current pitch.
            double base = Double.isNaN(currentPitch) ? 0.0 : currentPitch;
            return Math.max(-90.0, Math.min(90.0, base >= 0 ? base - 30.0 : base + 30.0));
        }
        if (Double.isNaN(currentPitch)) {
            return hi; // deterministic visible default (an edge, not the midpoint)
        }
        double mid = (lo + hi) / 2.0;
        return currentPitch >= mid ? lo : hi; // far edge from the current pitch → guaranteed travel
    }

    /** {@code BOSS_TELEPORTED} → {@code "Boss Teleported"} for a readable dev-button label. */
    private static String titleCase(String enumName) {
        String[] parts = enumName.toLowerCase(java.util.Locale.ROOT).split("_");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p.isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1));
        }
        return sb.toString();
    }
}
