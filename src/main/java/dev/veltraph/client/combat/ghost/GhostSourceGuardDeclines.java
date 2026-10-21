package dev.veltraph.client.combat.ghost;

import dev.veltraph.client.game.GameArea;
import dev.veltraph.client.combat.ghost.GhostSourceParentGuardDispatcher.*;

/** Only source-proved declines; enabled unbound helpers are not silently unhandled. */
public final class GhostSourceGuardDeclines {
    private GhostSourceGuardDeclines() {}
    public static Result ordinary(Role role,boolean autoSell,boolean instantPests,boolean instantVisitors,boolean collectTraps,GameArea actualArea){
        return switch(role){
            case FARM_GARDEN_PAUSE,FARM_PEST_SPRAY,MARKER_WORK -> Result.UNHANDLED; // Source Ghost hierarchy and COMBAT/DWARVEN constructor.
            case AUTO_SELL -> autoSell?Result.UNKNOWN:Result.UNHANDLED;
            case PESTS -> instantPests?Result.UNKNOWN:Result.UNHANDLED;
            case VISITORS -> instantVisitors?Result.UNKNOWN:Result.UNHANDLED;
            case PEST_TRAPS -> collectTraps?Result.UNKNOWN:Result.UNHANDLED;
            case MOONGLADE -> actualArea==null||actualArea==GameArea.UNKNOWN?Result.UNKNOWN:
                    actualArea==GameArea.DWARVEN_MINES?Result.UNHANDLED:Result.UNKNOWN;
            case SCHEDULED_REST -> Result.UNKNOWN; // Actual Root scheduler must supply its current result.
        };
    }
}
