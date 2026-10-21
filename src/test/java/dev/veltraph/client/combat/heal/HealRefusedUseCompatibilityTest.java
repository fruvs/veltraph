package dev.veltraph.client.combat.heal;

import dev.veltraph.client.config.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Compiles with old APIs; old productive orchestration falsely counts this refused native use. */
class HealRefusedUseCompatibilityTest {
    private static final class RefusingNativePort implements HealServices {
        int requested, actualUses;
        public boolean available(){return true;} public boolean hasHyperion(){return true;}
        public double healthPct(){return 30;} public boolean clearAbove(){return true;}
        public void lookDown(){} public void restoreLook(){}
        public void castHyperion(){requested++; /* Native cooldown guard refused, no use invocation. */}
        // This extra method compiles against the old interface and overrides the new outcome seam.
        public HealCastOutcome castWithOutcome(){castHyperion();return HealCastOutcome.REFUSED_COOLDOWN;}
    }
    @Test void refusedNativeUseMustNotBeCountedAsHeal() {
        var config=new MapConfigService();config.set(GhostConfig.GHOST_AUTO_HEAL_ENABLED,true);
        var port=new RefusingNativePort();var heal=new CombatAutoHeal(config,port);
        heal.tick(true);heal.tick(true);heal.tick(true);
        assertEquals(1,port.requested);assertEquals(0,port.actualUses);
        assertEquals(0,heal.healCount(),"REFUSED_USE_RED old orchestrator counted a request that emitted no native use");
    }
}
