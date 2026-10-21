package dev.veltraph.client.inventory.selling;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CookieEvidenceTest {
    private static final String GRAMMAR="""
        {"footerStart":"BUFF(","footerEnd":")END","footerInactive":"NONE","tabMarker":"COOKIE",
         "tabInlineInactive":"DETAIL","tabNextInactivePrefix":"OFF"}
        """;
    @Test void originalExactLineRemainsCurrentUniqueAndFormattingClean(){
        assertTrue(CookieEvidence.active("other\n§aACTIVE\nother",List.of(),"ACTIVE",""));
        assertFalse(CookieEvidence.active("ACTIVE\nACTIVE",List.of(),"ACTIVE",""));
        assertFalse(CookieEvidence.active("INACTIVE",List.of(),"ACTIVE",""));
        assertFalse(CookieEvidence.active(null,List.of(),"ACTIVE",""));
    }
    @Test void configuredFooterRegionMustHaveBothBoundsAndNoInactiveToken(){
        assertTrue(CookieEvidence.active("BUFF(2 days)END",List.of(),"",GRAMMAR));
        assertFalse(CookieEvidence.active("BUFF(NONE)END",List.of(),"",GRAMMAR));
        assertFalse(CookieEvidence.active("BUFF(2 days",List.of(),"",GRAMMAR));
        assertFalse(CookieEvidence.active(")END BUFF(2 days",List.of(),"",GRAMMAR));
    }
    @Test void tabFallbackPreservesSourceInlineAndAdjacentInactiveChecks(){
        assertTrue(CookieEvidence.active("",List.of("COOKIE active"),"",GRAMMAR));
        assertFalse(CookieEvidence.active("",List.of("COOKIE DETAIL","  OFF  "),"",GRAMMAR));
        assertTrue(CookieEvidence.active("",List.of("COOKIE DETAIL","2 days"),"",GRAMMAR));
        assertFalse(CookieEvidence.active("",List.of("COOKIE DETAIL"),"",GRAMMAR));
    }
    @Test void footerInactiveMayUseFreshTabFallbackWithoutCaching(){
        assertTrue(CookieEvidence.active("BUFF(NONE)END",List.of("COOKIE active"),"",GRAMMAR));
        assertFalse(CookieEvidence.active("BUFF(NONE)END",List.of(),"",GRAMMAR));
    }
    @Test void duplicateNativeRegionsOrMarkerRowsFailClosed(){
        assertFalse(CookieEvidence.active("BUFF(x)END BUFF(x)END",List.of("COOKIE active"),"",GRAMMAR));
        assertFalse(CookieEvidence.active("",List.of("COOKIE active","COOKIE active"),"",GRAMMAR));
    }
    @Test void malformedBindingsNeverFallBackToAnOldExactLine(){
        for(String invalid:List.of("{}","{\"footerStart\":1}","bad","null",GRAMMAR.replace("\"OFF\"","\"\"")))
            assertFalse(CookieEvidence.active("ACTIVE",List.of("COOKIE active"),"ACTIVE",invalid));
    }
    @Test void literalLabelsCannotBecomeRegularExpressions(){
        assertFalse(CookieEvidence.active("BUFF2 daysEND",List.of(),"",GRAMMAR));
        assertTrue(CookieEvidence.active("§aBUFF(2 days)END",List.of(),"",GRAMMAR));
    }
    @Test void recoveredFishingCapabilityRequiresZeroEmptySlots(){
        assertTrue(CookieEvidence.pressureReady(2,2,false));assertFalse(CookieEvidence.pressureReady(3,2,false));
        assertTrue(CookieEvidence.pressureReady(0,2,true));assertFalse(CookieEvidence.pressureReady(1,2,true));
        assertFalse(CookieEvidence.pressureReady(0,3,false));assertFalse(CookieEvidence.pressureReady(-1,2,false));
    }
}
