package dev.veltraph.client.galatea;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class FigObservedScaffoldMetadataTest {
    private final FigObservedScaffoldMetadata binding=new FigObservedScaffoldMetadata("minecraft:stick","Native scaffold","components","scaffold","rise:","rise:"," blocks");
    private FigObservedScaffoldMetadata.Item item(int slot,String name,List<String> lore){return new FigObservedScaffoldMetadata.Item(slot,"minecraft:stick",name,"components",lore);}
    @Test void separateFieldsUseExactSourceGrammar(){var b=new FigObservedScaffoldMetadata("minecraft:stick","Native scaffold","components","scaffold","rise:","rise:"," blocks");
        var o=b.observe(List.of(item(2,"Native scaffold",List.of("rise:3 blocks height 9."))));
        assertEquals(9,o.values().geometryHeight());assertEquals(3,o.values().completionRise());assertEquals(2,o.hotbarSlot());}
    @Test void nextLineGeometryDoesNotStripPeriodsFromRise(){var b=new FigObservedScaffoldMetadata("minecraft:stick","Native scaffold","components","scaffold","rise:","rise:"," blocks");
        assertEquals(12,b.observe(List.of(item(0,"Native scaffold",List.of("rise: -2 blocks", " 1.2. ")))).values().geometryHeight());
        assertThrows(IllegalArgumentException.class,()->b.observe(List.of(item(0,"Native scaffold",List.of("rise: 1.2 blocks", " 12. ")))));}
    @Test void originalPlusFiveIsNotTokenLength(){var b=new FigObservedScaffoldMetadata("minecraft:stick","Native scaffold","components","scaffold","abcd","abcd"," blocks");
        assertEquals(3,b.observe(List.of(item(0,"Native scaffold",List.of("abcd 3 blocks", "9.")))).values().completionRise());}
    @Test void firstCaptionMatchCannotBeSkippedForLaterExactWitness(){
        assertThrows(IllegalArgumentException.class,()->binding.observe(List.of(item(1,"Foreign scaffold",List.of("rise:3 blocks height 9.")),item(2,"Native scaffold",List.of("rise:3 blocks height 9.")))));}
    @Test void mainInventoryCannotSupplyHotbarItem(){assertThrows(IllegalArgumentException.class,()->binding.observe(List.of(item(12,"Native scaffold",List.of("rise:3 blocks height 9.")))));}
    @Test void oldHeightOnlySchemaDoesNotInventCompletionRise(){assertThrows(RuntimeException.class,()->FigObservedScaffoldMetadata.parse("{\"schema\":1}"));}
}
