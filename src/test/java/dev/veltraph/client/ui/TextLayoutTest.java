package dev.veltraph.client.ui;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class TextLayoutTest {
    @Test void fitsLongLabelsWithoutOverlappingControls() {
        assertTrue(TextLayout.fit("Long setting label",42,s->s.length()*6).length()*6<=42);
        assertEquals("",TextLayout.fit("Long",2,s->s.length()*6));
        assertEquals("Short",TextLayout.fit("Short",60,s->s.length()*6));
    }
    @Test void wrapsEvenLongUnbrokenSourceNames() {
        var lines=TextLayout.wrap("this is a verylongfilenamewithoutspaces.java",60,s->s.length()*6);
        assertTrue(lines.stream().allMatch(s->s.length()<=10));
        assertEquals("thisisaverylongfilenamewithoutspaces.java",String.join("",lines).replace(" ",""));
    }
    @Test void railFitsAllCategoriesAtMinimumSupportedHeight() {
        assertTrue(TextLayout.railRowHeight(166,12)*12+14<=166);
        assertTrue(TextLayout.railRowHeight(166,10)*10+14<=166);
    }
}
