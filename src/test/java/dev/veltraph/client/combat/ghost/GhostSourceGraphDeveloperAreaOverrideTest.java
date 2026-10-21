package dev.veltraph.client.combat.ghost;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class GhostSourceGraphDeveloperAreaOverrideTest {
    @Test void freshSourceNullableFieldIsAbsentUntilExplicitSelection(){
        var o=new GhostSourceGraphDeveloperAreaOverride();assertTrue(o.selected().isEmpty());assertEquals(0,o.revision());
        assertTrue(o.applyExplicitArguments(List.of("sven_ruins")));assertEquals(GhostSourceParentAreaResource.SVEN_RUINS,o.selected().orElseThrow());
    }
    @Test void sourceInvalidArgumentPreservesSelectionAndRevision(){
        var o=new GhostSourceGraphDeveloperAreaOverride();o.assignFromExplicitCommand(GhostSourceParentAreaResource.REV);long revision=o.revision();
        assertFalse(o.applyExplicitArguments(List.of("unknown")));assertEquals(GhostSourceParentAreaResource.REV,o.selected().orElseThrow());assertEquals(revision,o.revision());
    }
    @Test void noArgumentsExplicitlyClearAndFirstArgumentIsCaseInsensitive(){
        var o=new GhostSourceGraphDeveloperAreaOverride();assertTrue(o.applyExplicitArguments(List.of("VoIdGlOoM","ignored")));
        assertEquals(GhostSourceParentAreaResource.VOIDGLOOM,o.selected().orElseThrow());long before=o.revision();
        assertTrue(o.applyExplicitArguments(List.of()));assertTrue(o.selected().isEmpty());assertTrue(o.revision()>before);
    }
}
