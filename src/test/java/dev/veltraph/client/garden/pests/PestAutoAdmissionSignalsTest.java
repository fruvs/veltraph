package dev.veltraph.client.garden.pests;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

final class PestAutoAdmissionSignalsTest {
    // Authored fixture strings exercise token grammar; these are not claimed native labels.
    private final PestAutoAdmissionSignals.Bindings bindings=new PestAutoAdmissionSignals.Bindings("Fixture tab:","Fixture sidebar:");
    private java.util.OptionalInt read(List<String> tab,List<String> sidebar){return PestAutoAdmissionSignals.read(bindings,tab,sidebar);}
    @Test void exactTabPrefixAndFinalXTokenTakePrecedence(){assertEquals(2,read(List.of("Fixture tab: ignored x2"),List.of("Fixture sidebar: 7")).orElseThrow());}
    @Test void everyXIsRemovedAfterLowercaseLeadingX(){assertEquals(2,read(List.of("Fixture tab: xx2x"),List.of()).orElseThrow());}
    @Test void signedCountsRemainSignedForAdmissionValidation(){assertEquals(-2,read(List.of("Fixture tab: x-2"),List.of()).orElseThrow());assertEquals(2,read(List.of("Fixture tab: x+2"),List.of()).orElseThrow());assertEquals(-2,read(List.of(),List.of("Fixture sidebar: -2")).orElseThrow());}
    @Test void finalTokenWithoutLowercaseXUsesSourceSidebarFallback(){assertEquals(3,read(List.of("Fixture tab: 2","Fixture tab: X9"),List.of("Fixture sidebar: 3")).orElseThrow());}
    @Test void malformedLeadingXReturnsUnknownWithoutFallback(){assertTrue(read(List.of("Fixture tab: xunknown"),List.of("Fixture sidebar: 3")).isEmpty());}
    @Test void sidebarParsesTheFinalLiteralSpaceToken(){assertEquals(3,read(List.of(),List.of("Fixture sidebar: additional words 3")).orElseThrow());assertTrue(read(List.of(),List.of("Fixture sidebar: 3 ")).isEmpty());}
    @Test void caseAndWhitespaceAreNeverNormalizedByTheGrammar(){assertTrue(read(List.of("fixture tab: x2"),List.of("fixture sidebar: 3")).isEmpty());assertTrue(read(List.of("Fixture tab: x2\t"),List.of("Fixture sidebar: 3")).isEmpty());}
    @Test void tabTrailingSpaceDoesNotContainAnXFinalToken(){assertEquals(3,read(List.of("Fixture tab: x2 "),List.of("Fixture sidebar: 3")).orElseThrow());}
    @Test void firstSourceMatchedTokenKeepsItsOwnValue(){assertEquals(2,read(List.of("Fixture tab: x2","Fixture tab: x3"),List.of()).orElseThrow());assertTrue(read(List.of(),List.of("Fixture sidebar: unknown","Fixture sidebar: 3")).isEmpty());}
    @Test void overflowIsUnknownRatherThanClampedOrWrapped(){assertTrue(read(List.of("Fixture tab: x2147483648"),List.of("Fixture sidebar: 3")).isEmpty());assertTrue(read(List.of(),List.of("Fixture sidebar: -2147483649")).isEmpty());}
    @Test void unknownPrefixesAndRowsCannotInventANativeCount(){assertTrue(PestAutoAdmissionSignals.read(null,List.of(),List.of()).isEmpty());assertTrue(PestAutoAdmissionSignals.read(new PestAutoAdmissionSignals.Bindings("","Fixture sidebar:"),List.of(),List.of("Fixture sidebar: 3")).isEmpty());assertTrue(PestAutoAdmissionSignals.read(bindings,null,List.of("Fixture sidebar: 3")).isEmpty());assertTrue(read(List.of(),List.of()).isEmpty());}
}
