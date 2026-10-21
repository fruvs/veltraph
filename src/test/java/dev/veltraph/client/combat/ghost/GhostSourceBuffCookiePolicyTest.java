package dev.veltraph.client.combat.ghost;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
final class GhostSourceBuffCookiePolicyTest {
    @Test void recoveredOnlyCookieEnablesSourceWithoutConsultingDifferentProfileGrammar(){var source=new GhostSourceCookiePredicate();var profileReads=new AtomicInteger();
        assertTrue(GhostSourceBuffCookiePolicy.ready(()->source.observe("Cookie Buff active§s",List.of())?GhostSourceJumpBoostGate.Observed.YES:GhostSourceJumpBoostGate.Observed.NO,()->{profileReads.incrementAndGet();return false;}));assertEquals(0,profileReads.get());}
    @Test void sourcePositiveCacheSurvivesFreshHudDisappearanceThroughHelperPhases(){var source=new GhostSourceCookiePredicate();source.observe("Cookie Buff active§s",List.of());
        for(int phase=0;phase<7;phase++)assertTrue(GhostSourceBuffCookiePolicy.ready(()->source.observe(null,List.of())?GhostSourceJumpBoostGate.Observed.YES:GhostSourceJumpBoostGate.Observed.NO,()->{fail("Source helper must not recheck fresh profile HUD");return false;}));}
    @Test void explicitSourceInactiveAndUnavailableDeclineWithoutProfileFallback(){var source=new GhostSourceCookiePredicate();source.observe("Cookie Buff active§s",List.of());
        assertFalse(GhostSourceBuffCookiePolicy.ready(()->source.observe("Cookie Buff Not active!§s",List.of())?GhostSourceJumpBoostGate.Observed.YES:GhostSourceJumpBoostGate.Observed.NO,()->true));
        assertFalse(GhostSourceBuffCookiePolicy.ready(()->GhostSourceJumpBoostGate.Observed.UNKNOWN,()->true));}
    @Test void ordinaryIngressStillUsesItsOriginalProfileOnly(){var calls=new AtomicInteger();assertFalse(GhostSourceBuffCookiePolicy.ready(null,()->{calls.incrementAndGet();return false;}));assertTrue(GhostSourceBuffCookiePolicy.ready(null,()->{calls.incrementAndGet();return true;}));assertEquals(2,calls.get());}
}
