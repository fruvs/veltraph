package dev.veltraph.client.combat.ghost;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import dev.veltraph.client.config.*;
import dev.veltraph.client.ui.content.*;
class GhostSourceWalkConfigTest {
    @Test void actualHolderIncludesSourceDefaultsAndExplicitModernPolicyBoundary(){
        assertTrue(GhostGrindConfig.GHOST_PATH_SMOOTHING_ENABLED.defaultValue());assertFalse(GhostGrindConfig.GHOST_PATH_KEEP_FINE_ADJUSTMENTS.defaultValue());
        int distance=GhostGrindConfig.GHOST_PATH_SHORTCUT_DISTANCE.defaultValue();assertTrue(distance>=4&&distance<7);
        assertEquals("",GhostGrindConfig.GHOST_WALK_FORBIDDEN_CELLS.defaultValue());
        var registered=PersistableKeys.collect(List.of(GhostGrindConfig.class));for(var key:List.of(GhostGrindConfig.GHOST_PATH_SMOOTHING_ENABLED,GhostGrindConfig.GHOST_PATH_KEEP_FINE_ADJUSTMENTS,GhostGrindConfig.GHOST_PATH_SHORTCUT_DISTANCE,GhostGrindConfig.GHOST_WALK_FORBIDDEN_CELLS))assertTrue(registered.contains(key));
    }
    @Test void configuredPolicyAndSmoothingRoundTripThroughActualHolder(@TempDir Path directory){
        var before=new JsonConfigService(PersistableKeys.all());before.set(GhostGrindConfig.GHOST_WALK_FORBIDDEN_CELLS,"[[114,76,34]]");before.set(GhostGrindConfig.GHOST_PATH_SMOOTHING_ENABLED,false);before.set(GhostGrindConfig.GHOST_PATH_KEEP_FINE_ADJUSTMENTS,true);before.set(GhostGrindConfig.GHOST_PATH_SHORTCUT_DISTANCE,6);
        var file=directory.resolve("ghost-walk.json");before.save(file,null);var after=new JsonConfigService(PersistableKeys.all());after.load(file,null);
        for(var key:List.of(GhostGrindConfig.GHOST_PATH_SMOOTHING_ENABLED,GhostGrindConfig.GHOST_PATH_KEEP_FINE_ADJUSTMENTS,GhostGrindConfig.GHOST_PATH_SHORTCUT_DISTANCE,GhostGrindConfig.GHOST_WALK_FORBIDDEN_CELLS))assertEquals(before.get(key),after.get(key));
        assertTrue(GhostSourceWalkForbiddenCells.fromActualConfiguredJson(after.get(GhostGrindConfig.GHOST_WALK_FORBIDDEN_CELLS)).available());
    }
    @Test void meaningfulUiRowsExposeActualConfigKeysWithoutProofLabels(){
        var content=SettingsContent.build();var rows=SettingsContent.CATEGORY_ORDER.stream().flatMap(c->content.forCategory(c).rows().stream()).toList();
        for(var key:List.of(GhostGrindConfig.GHOST_PATH_SMOOTHING_ENABLED,GhostGrindConfig.GHOST_PATH_KEEP_FINE_ADJUSTMENTS,GhostGrindConfig.GHOST_PATH_SHORTCUT_DISTANCE,GhostGrindConfig.GHOST_WALK_FORBIDDEN_CELLS))assertEquals(1,rows.stream().filter(r->key.name().equals(r.configKey())).count());
    }
}
