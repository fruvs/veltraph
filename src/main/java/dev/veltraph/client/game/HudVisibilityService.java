package dev.veltraph.client.game;

import java.util.EnumSet;
import java.util.Set;

/**
 * Decides whether a given {@link HudCategory} may render in the current {@link GameContext}. Rules
 * follow the recovered location gates (see {@code docs/LOCATION_GATES.md}):
 *
 * <ul>
 *   <li><b>Nothing renders outside Hypixel SkyBlock.</b></li>
 *   <li>Garden / Pest / Visitor HUD → only in {@link GameArea#GARDEN}.</li>
 *   <li>Foraging HUD → island / park / galatea / hub.</li>
 *   <li>Ghost HUD → {@link GameArea#DWARVEN_MINES}.</li>
 *   <li>Fishing / Slayer HUD → anywhere in SkyBlock (exact area gate UNKNOWN — not over-gated).</li>
 *   <li>Script-only HUD → only while scripting (a module running).</li>
 *   <li>Generic HUD → anywhere in SkyBlock.</li>
 * </ul>
 *
 * <p>The core decision is a pure static method so it is fully unit-testable.
 */
public final class HudVisibilityService {

    private static final Set<GameArea> FORAGING_AREAS =
            EnumSet.of(GameArea.ISLAND, GameArea.THE_PARK, GameArea.GALATEA, GameArea.HUB);

    private HudVisibilityService() {
    }

    /**
     * @param category HUD category
     * @param ctx      current game context
     * @param scripting whether a module is actively running
     * @return whether this HUD category may render now
     */
    public static boolean isVisible(HudCategory category, GameContext ctx, boolean scripting) {
        // Absolute rule: no HUD outside Hypixel SkyBlock.
        if (ctx == null || !ctx.inSkyBlock()) {
            return false;
        }
        return switch (category) {
            case GARDEN, PEST, VISITOR -> ctx.area() == GameArea.GARDEN;
            case FORAGING -> FORAGING_AREAS.contains(ctx.area());
            case GHOST -> ctx.area() == GameArea.DWARVEN_MINES;
            case FISHING, SLAYER, GENERIC -> true; // in SkyBlock; exact area unknown for fishing/slayer
            case SCRIPT -> scripting;
        };
    }
}
