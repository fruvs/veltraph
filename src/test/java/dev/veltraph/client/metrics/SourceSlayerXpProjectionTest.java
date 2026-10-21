package dev.veltraph.client.metrics;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SourceSlayerXpProjectionTest {
    static List<String> rows(String title,String xp){return List.of("§aSlayer:","Quest: Active","Boss: Revenant",title,xp);}
    @Test void actualRelativeWidgetAndSourceHalfUpCompactArithmetic(){
        var result=SourceSlayerXpProjection.tabRows(rows("Zombie Slayer VII","1.2345k / 2M"));
        assertEquals(SourceSlayerXpProjection.State.OBSERVED,result.state());var value=result.widget();
        assertEquals("Zombie",value.selection());assertEquals(7,value.level());assertEquals(1235,value.currentXp());assertEquals(2000000,value.goalXp());assertTrue(value.compact());
    }
    @Test void sourceFiltersEmptyRowsBeforeRelativeOffsetsAndAcceptsExactGroupedNumbers(){
        var values=new ArrayList<>(rows("Vampire Slayer 5","1,250 / 5,000"));values.add(1,"§r  ");
        var value=SourceSlayerXpProjection.tabRows(values).widget();assertEquals(5,value.level());assertEquals(1250,value.currentXp());assertFalse(value.compact());
    }
    @Test void maxSentinelRemainsUnavailableInsteadOfIntegerMaxEarnedXp(){
        for(String text:List.of("MAX","MAX LEVEL")){var max=SourceSlayerXpProjection.tabRows(rows("Zombie Slayer IX",text));assertEquals(SourceSlayerXpProjection.State.MAX,max.state());assertNull(max.widget());}
    }
    @Test void duplicateHeadersWrongNeighborMalformedTitlesAndExtraTokensAreUnavailable(){
        var repeated=new ArrayList<>(rows("Zombie Slayer VII","100/200"));repeated.add("Slayer:");assertUnavailable(repeated);
        for(String title:List.of("Zombie Slayer IIV","Zombie Slayer 07","Zombie Other VII","Zombie Slayer VII extra"))assertUnavailable(rows(title,"100/200"));
        assertUnavailable(List.of("Slayer:","Zombie Slayer VII","100/200"));
    }
    @Test void ambiguousNegativeOverflowFractionalExactAndInvalidGoalsAreUnavailable(){
        for(String xp:List.of("100/200/300","-1/200","201/200","100/0","100.5/200","9,99/200","1e3/2M","999999999999999999k/999999999999999999M","1k/2B","quoted 100 Coins/200"))assertUnavailable(rows("Zombie Slayer VII",xp));
        assertUnavailable(Collections.nCopies(129,"Slayer:"));assertUnavailable(Arrays.asList("Slayer:",null));
    }
    private static void assertUnavailable(List<String> rows){assertEquals(SourceSlayerXpProjection.State.UNAVAILABLE,SourceSlayerXpProjection.tabRows(rows).state());}
}
