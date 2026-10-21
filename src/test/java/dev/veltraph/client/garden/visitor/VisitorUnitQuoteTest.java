package dev.veltraph.client.garden.visitor;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
class VisitorUnitQuoteTest {
    @Test void explicitUnitQuotesRoundUpRatherThanUnderstateGuardedValue(){
        assertEquals(3,VisitorUnitQuote.read(List.of("Unit Price: 2.01 Coins")).orElseThrow());
        assertEquals(1235,VisitorUnitQuote.read(List.of("§6Price: 1,234.01 Coins each")).orElseThrow());
    }
    @Test void ambiguousTotalsNegativeOverflowDuplicateAndMalformedQuotesFailClosed(){
        for(var lines:List.of(List.of("Price: 12 Coins"),List.of("Unit Price: -3 Coins"),
                List.of("Unit Price: 1,2 Coins"),List.of("Unit Price: 999999999999999999999 Coins"),
                List.of("Unit Price: 3 Coins","Unit Price: 4 Coins")))assertTrue(VisitorUnitQuote.read(lines).isEmpty());
    }
}
