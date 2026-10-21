package dev.veltraph.client.combat.grind;

import dev.veltraph.client.combat.ghost.GhostSourceUnreachableActorLedger;
import java.util.Objects;

/** Membership-only consumer of the shared source CombatPathing native-Entity ledger.
 * Source reader: Graveyard ordinary predicate, after standability and before ignore/protected.
 * It owns no failure writes, clock, cleanup, UUID conversion, or per-world resets.
 */
public final class SourceCombatUnreachableConsumer {
    public enum Declaration {
        GRAVEYARD_CHILD, BESTIARY_GRAVEYARD_CHILD, TARANTULA_GRAVEYARD_CHILD,
        REVENANT_SLAYER_GRAVEYARD_CHILD, TARANTULA_SLAYER_GRAVEYARD_CHILD
    }
    private SourceCombatUnreachableConsumer() {}
    /** Exact parent menu declarations whose decoded workflows hand off to Graveyard. */
    public static Declaration declaredSlayerChild(String actualBossLabel) {
        if("Revenant Horror".equals(actualBossLabel))return Declaration.REVENANT_SLAYER_GRAVEYARD_CHILD;
        if("Tarantula Broodfather".equals(actualBossLabel))return Declaration.TARANTULA_SLAYER_GRAVEYARD_CHILD;
        return null;
    }
    /** Native binding passes the actual body object; reader neither expires nor clears entries. */
    public static boolean accepts(Declaration actualDeclaration,Object actualNativeBody,
                                  GhostSourceUnreachableActorLedger sharedLedger) {
        if(actualNativeBody==null)return false;
        if(actualDeclaration==null)return true;
        return !Objects.requireNonNull(sharedLedger).containsIdentity(actualNativeBody);
    }
}
