package dev.veltraph.client.hud;

import java.util.List;

/**
 * The pure, immutable description of what the in-world HUD should draw this frame, produced by
 * {@link HudModelBuilder} from a snapshot of the live services. The renderer
 * ({@code hud.mc.VeltraphHudOverlay}) reads {@link #visible()} (gracefully no-ops when hidden) and
 * draws {@link #lines()} in order. No Minecraft dependency, so the decision logic is fully testable.
 *
 * @param visible whether the panel should render at all (false → renderer no-ops)
 * @param lines   the lines to draw, top to bottom (empty when hidden)
 */
public record HudModel(boolean visible, List<HudLine> lines) {

    /** A hidden model: nothing renders. */
    public static final HudModel HIDDEN = new HudModel(false, List.of());

    public HudModel {
        lines = List.copyOf(lines);
    }

    public static HudModel hidden() {
        return HIDDEN;
    }

    public static HudModel of(List<HudLine> lines) {
        return new HudModel(true, lines);
    }

    public int lineCount() {
        return lines.size();
    }
}
