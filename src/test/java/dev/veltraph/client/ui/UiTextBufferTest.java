package dev.veltraph.client.ui;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class UiTextBufferTest {
 @Test void backspaceDeletesAWholeSupplementaryCodepoint(){var b=new UiTextBuffer();b.set("x😀");b.backspace();assertEquals("x",b.text());}
 @Test void deleteAndMoveDoNotSplitSurrogates(){var b=new UiTextBuffer();b.set("a😀b");b.home(false);b.move(1,false);b.delete();assertEquals("ab",b.text());}
 @Test void selectionReplacementAndCopyAreExact(){var b=new UiTextBuffer();b.set("existing");b.selectAll();assertEquals("existing",b.selection());assertTrue(b.insert("new"));assertEquals("new",b.text());assertFalse(b.selected());}
 @Test void shiftNavigationSelectsOnlyRequestedText(){var b=new UiTextBuffer();b.set("ab😀");b.move(-1,true);assertEquals("😀",b.selection());b.deleteSelection();assertEquals("ab",b.text());}
 @Test void pasteFiltersControlsWithoutExecutingAnything(){var b=new UiTextBuffer();assertTrue(b.insert("a\nb\u0000c"));assertEquals("abc",b.text());}
 @Test void oversizeReplacementRefusesWithoutPartialMutation(){var b=new UiTextBuffer();b.set("old");b.selectAll();assertFalse(b.insert("x".repeat(UiTextBuffer.MAX_LENGTH+1)));assertEquals("old",b.text());assertEquals("old",b.selection());}
 @Test void homeEndAndSelectionCollapseArePredictable(){var b=new UiTextBuffer();b.set("abc");b.home(true);assertEquals("abc",b.selection());b.move(1,false);assertEquals("",b.selection());assertTrue(b.insert("d"));assertEquals("abcd",b.text());}
 @Test void preeditPreviewUsesSelectionWithoutCommitting(){var b=new UiTextBuffer();b.set("a😀b");b.move(-1,false);b.move(-1,true);assertEquals("a候補b",b.preview("候補"));assertEquals("a😀b",b.text());assertEquals("😀",b.selection());assertEquals("a😀b",b.preview(null));}
 @Test void compositionBoundRefusesWithoutDraftMutation(){var b=new UiTextBuffer();b.set("old");assertThrows(IllegalArgumentException.class,()->b.preview("x".repeat(UiTextBuffer.MAX_LENGTH+1)));assertEquals("old",b.text());}
}
