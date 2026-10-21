package dev.veltraph.client.galatea;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FigObservedScaffoldHeightTest {
    private final FigObservedScaffoldHeight binding=new FigObservedScaffoldHeight("minecraft:scaffolding","Observed scaffold","native-components","Observed marker");
    private FigObservedScaffoldHeight.Item item(String components,List<String> lore){return new FigObservedScaffoldHeight.Item("minecraft:scaffolding","Observed scaffold",components,lore);}
    @Test void terminalDotUsesLastSpaceToken(){assertEquals(5,binding.observe(List.of(item("native-components",List.of("Observed marker 5.")))));}
    @Test void nonterminalDotUsesNextCompleteLine(){assertEquals(12,binding.observe(List.of(item("native-components",List.of("Observed marker", " 12. ")))));}
    @Test void sourceTrimAlsoHandlesNativeNonBreakingSpaces(){assertEquals(12,binding.observe(List.of(item("native-components",List.of("Observed marker", "\u00a012.\u00a0")))));}
    @Test void malformedOrMissingNativeValueNeverGetsHeightEight(){
        assertThrows(IllegalArgumentException.class,()->binding.observe(List.of(item("native-components",List.of("Observed marker")))));
        assertThrows(IllegalArgumentException.class,()->binding.observe(List.of(item("native-components",List.of("Observed marker nope.")))));
        assertThrows(IllegalArgumentException.class,()->binding.observe(List.of(item("native-components",List.of("Observed marker 999999999999.")))));
    }
    @Test void changedComponentsAndAmbiguousItemsRefuse(){
        assertThrows(IllegalArgumentException.class,()->binding.observe(List.of(item("foreign",List.of("Observed marker 5.")))));
        var i=item("native-components",List.of("Observed marker 5."));assertThrows(IllegalArgumentException.class,()->binding.observe(List.of(i,i)));
    }
    @Test void rawNegativeAndPeriodRemovalAreNotClampedToInventedDomain(){
        assertEquals(-2,binding.observe(List.of(item("native-components",List.of("Observed marker -2.")))));
        assertEquals(12,binding.observe(List.of(item("native-components",List.of("Observed marker 1.2.")))));
    }
    @Test void schemaAndUnexpectedFieldsRefuse(){
        assertThrows(RuntimeException.class,()->FigObservedScaffoldHeight.parse("{}"));
        assertThrows(RuntimeException.class,()->FigObservedScaffoldHeight.parse("{\"schema\":1,\"registryId\":\"minecraft:scaffolding\",\"name\":\"Observed scaffold\",\"components\":\"native-components\",\"marker\":\"Observed marker\",\"guessedHeight\":8}"));
    }
}
