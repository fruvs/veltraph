package dev.veltraph.client.combat.ghost;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
final class GhostSourceCookiePredicateTest {
    @Test void sourceFormattingRemovalPreservesNonFormattingSectionSTerminator(){assertEquals("Cookie Buff§s",GhostSourceCookiePredicate.normalize("  §aCookie Buff§s  "));assertEquals("§z",GhostSourceCookiePredicate.normalize("§z"));}
    @Test void recoveredRegexStoresBackslashNNotLiteralNewline(){assertTrue(GhostSourceCookiePredicate.FOOTER_PATTERN.contains("\\n"));assertFalse(GhostSourceCookiePredicate.FOOTER_PATTERN.contains("\n"));}
    @Test void recognizedActiveFooterSetsPositive(){var predicate=new GhostSourceCookiePredicate();assertTrue(predicate.observe("§aCookie Buff\n2h 3m§s",List.of()));}
    @Test void recognizedInactiveFooterResetsPreviouslyPositive(){var predicate=new GhostSourceCookiePredicate();assertTrue(predicate.observe("Cookie Buff active§s",List.of()));assertFalse(predicate.observe("Cookie Buff Not active!§s",List.of()));}
    @Test void unrecognizedOrAbsentFooterRetainsPositiveWithoutAgeOrWorldReset(){var predicate=new GhostSourceCookiePredicate();assertTrue(predicate.observe("Cookie Buff active§s",List.of()));assertTrue(predicate.observe(null,List.of()));assertTrue(predicate.observe("Unrelated footer",List.of("Cookie Buff Not active!","Not active")));}
    @Test void recognizedFooterWithoutTerminatorClearsPositiveBeforeRowFallback(){var predicate=new GhostSourceCookiePredicate();assertTrue(predicate.observe("Cookie Buff active§s",List.of()));assertFalse(predicate.observe("Cookie Buff active",List.of()));assertTrue(predicate.observe("Cookie Buff malformed",List.of("Cookie Buff")));}
    @Test void ordinaryCookieRowIsPositiveAndFormattingIsNormalized(){assertTrue(new GhostSourceCookiePredicate().observe(null,List.of("§aCookie Buff")));}
    @Test void inactiveMarkerPeeksNextOrderedNonemptyRowAndBreaks(){var predicate=new GhostSourceCookiePredicate();assertFalse(predicate.observe(null,List.of("Cookie Buff Not active!"," ","§7Not active today","Cookie Buff")));}
    @Test void inactiveMarkerWithoutInactiveNextRowIsPositiveAsSource(){assertTrue(new GhostSourceCookiePredicate().observe(null,List.of("Cookie Buff Not active!")));assertTrue(new GhostSourceCookiePredicate().observe(null,List.of("Cookie Buff Not active!","Active now")));}
    @Test void unavailablePureHudDefaultsFalseButDoesNotRevokeCachedPositive(){var predicate=new GhostSourceCookiePredicate();assertFalse(predicate.observe(null,null));assertTrue(predicate.observe(null,List.of("Cookie Buff")));assertTrue(predicate.observe(null,null));}
}
