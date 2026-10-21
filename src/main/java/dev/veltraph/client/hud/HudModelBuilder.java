package dev.veltraph.client.hud;

import dev.veltraph.client.core.MacroState;
import dev.veltraph.client.game.GameContext;
import dev.veltraph.client.game.HudCategory;
import dev.veltraph.client.game.HudVisibilityService;
import dev.veltraph.client.hydra.HydraState;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure builder that turns a {@link HudInputs} snapshot into the {@link HudModel} the renderer draws
 * (Target 5). No Minecraft dependency, so the whole "what lines to show" decision is unit-testable.
 *
 * <h2>Visibility</h2>
 * The panel is the <b>generic Veltraph HUD</b>: it renders only when {@code HUD_ENABLED} is on and the
 * {@link HudVisibilityService} allows {@link HudCategory#GENERIC} in the current context (i.e. only on
 * Hypixel SkyBlock — never on menus, lobbies, BedWars, or outside SkyBlock). When hidden it returns
 * {@link HudModel#hidden()} and nothing renders.
 *
 * <h2>Per-line area gating</h2>
 * The active-macro line is additionally gated by the module's own {@link HudCategory} through the same
 * {@link HudVisibilityService}, so a farming macro line (category {@link HudCategory#GARDEN}) is hidden
 * when not in the Garden — mirroring the recovered location gates exactly.
 *
 * <h2>Content</h2>
 * Title, area/context, active macro + {@link MacroState} + its concise status line (or an idle line
 * when none), the anti-staff status (+ last event / nearby player), and the rewarp status (saved count
 * + pending-return flag). Each line is kept short.
 */
public final class HudModelBuilder {

    public static final String TITLE = "Veltraph";

    private HudModelBuilder() {
    }

    /** Build the HUD model from a pure snapshot. Returns {@link HudModel#hidden()} when nothing shows. */
    public static HudModel build(HudInputs in) {
        if (in == null) {
            return HudModel.hidden();
        }
        GameContext ctx = in.context() == null ? GameContext.NONE : in.context();
        boolean scripting = in.scripting();

        // Panel-level gate: only the GENERIC HUD (anywhere in SkyBlock), and only when enabled.
        if (!in.hudEnabled() || !HudVisibilityService.isVisible(HudCategory.GENERIC, ctx, scripting)) {
            return HudModel.hidden();
        }

        List<HudLine> lines = new ArrayList<>();
        lines.add(HudLine.title(TITLE));
        lines.add(HudLine.muted("Area: " + areaName(ctx)));
        addMacroLines(lines, in, ctx, scripting);
        lines.add(hydraLine(in.hydra()));
        lines.add(rewarpLine(in.rewarp()));
        return HudModel.of(lines);
    }

    private static void addMacroLines(List<HudLine> lines, HudInputs in, GameContext ctx, boolean scripting) {
        HudInputs.ModuleInfo mod = in.activeModule();
        if (mod == null) {
            lines.add(HudLine.muted("Idle — no macro running"));
            return;
        }
        // Gate the macro line by the module's own HUD category (e.g. farming hides outside the Garden).
        HudCategory cat = mod.hudCategory() == null ? HudCategory.SCRIPT : mod.hudCategory();
        if (!HudVisibilityService.isVisible(cat, ctx, scripting)) {
            lines.add(HudLine.muted("Macro paused (out of area)"));
            return;
        }
        MacroState state = mod.state() == null ? MacroState.STOPPED : mod.state();
        lines.add(new HudLine(mod.displayName() + " — " + state, roleFor(state)));
        String status = mod.statusLine();
        if (status != null && !status.isBlank()) {
            lines.add(HudLine.body(trim(status, 40)));
        }
    }

    private static HudLine.Role roleFor(MacroState state) {
        return switch (state) {
            case RUNNING -> HudLine.Role.ACTIVE;
            case PAUSED -> HudLine.Role.WARNING;
            case STOPPED -> HudLine.Role.STOPPED;
        };
    }

    private static HudLine hydraLine(HudInputs.HydraInfo h) {
        if (h == null || !h.enabled()) {
            return HudLine.muted("Anti-Staff: off");
        }
        HydraState state = h.state() == null ? HydraState.IDLE : h.state();
        StringBuilder sb = new StringBuilder("Anti-Staff: ").append(state);
        if (state == HydraState.ALERTED) {
            String who = h.nearbyName();
            if (who != null && !who.isBlank()) {
                sb.append(" (").append(who).append(')');
            }
        }
        if (h.lastEvent() != null && !h.lastEvent().isBlank()) {
            sb.append(" last=").append(h.lastEvent());
        }
        HudLine.Role role = state == HydraState.ALERTED ? HudLine.Role.WARNING : HudLine.Role.ACTIVE;
        return new HudLine(trim(sb.toString(), 44), role);
    }

    private static HudLine rewarpLine(HudInputs.RewarpInfo r) {
        if (r == null) {
            return HudLine.muted("Rewarp: 0 saved");
        }
        if (r.returnPending()) {
            return HudLine.warning("Rewarp: " + r.savedCount() + " saved, return pending");
        }
        return HudLine.muted("Rewarp: " + r.savedCount() + " saved");
    }

    private static String areaName(GameContext ctx) {
        return ctx.area() == null ? "Unknown" : ctx.area().displayName();
    }

    private static String trim(String s, int max) {
        if (s.length() <= max) {
            return s;
        }
        return s.substring(0, Math.max(0, max - 1)) + "…";
    }
}
