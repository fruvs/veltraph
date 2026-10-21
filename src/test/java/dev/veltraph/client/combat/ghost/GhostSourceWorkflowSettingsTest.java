package dev.veltraph.client.combat.ghost;
import dev.veltraph.client.config.*;
import dev.veltraph.client.ui.content.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
class GhostSourceWorkflowSettingsTest {
 @Test void newWorkflowRowsPreserveDeliveredDestinationAndHelperControls(){
  var content=SettingsContent.build();
  var rows=SettingsContent.CATEGORY_ORDER.stream().flatMap(c->content.forCategory(c).rows().stream()).toList();
  for(var key:List.of(GhostGrindConfig.GHOST_SOURCE_MOVEMENT_WORKFLOW,GhostGrindConfig.GHOST_SOURCE_DESTINATION,
    GhostGrindConfig.GHOST_SOURCE_MODIFY_JUMP_BOOST,GhostGrindConfig.GHOST_SOURCE_ACTIVATE_PESTS_INSTANTLY,
    GhostGrindConfig.GHOST_SOURCE_ACTIVATE_VISITOR_INSTANTLY,GhostGrindConfig.GHOST_PATH_SMOOTHING_ENABLED,
    GhostGrindConfig.GHOST_PATH_SHORTCUT_DISTANCE,GhostGrindConfig.GHOST_PATH_KEEP_FINE_ADJUSTMENTS,
    GhostGrindConfig.GHOST_WALK_FORBIDDEN_CELLS))
   assertEquals(1,rows.stream().filter(r->key.name().equals(r.configKey())).count(),key.name());
 }
 @Test void explicitWorkflowAndPolicySurviveActualConfigReload(@TempDir Path directory){
  assertEquals(GhostMovementWorkflow.LEGACY_AUTHORED_ROUTES,GhostGrindConfig.GHOST_SOURCE_MOVEMENT_WORKFLOW.defaultValue());
  var original=new JsonConfigService(PersistableKeys.all());
  original.set(GhostGrindConfig.GHOST_SOURCE_MOVEMENT_WORKFLOW,GhostMovementWorkflow.SOURCE_WALK);
  original.set(GhostGrindConfig.GHOST_WALK_FORBIDDEN_CELLS,"[]");
  original.set(GhostGrindConfig.GHOST_SOURCE_ACTIVATE_PESTS_INSTANTLY,true);
  var file=directory.resolve("workflow.json");original.save(file,null);
  var reloaded=new JsonConfigService(PersistableKeys.all());reloaded.load(file,null);
  assertEquals(GhostMovementWorkflow.SOURCE_WALK,reloaded.get(GhostGrindConfig.GHOST_SOURCE_MOVEMENT_WORKFLOW));
  assertEquals("[]",reloaded.get(GhostGrindConfig.GHOST_WALK_FORBIDDEN_CELLS));
  assertTrue(reloaded.get(GhostGrindConfig.GHOST_SOURCE_ACTIVATE_PESTS_INSTANTLY));
 }
}
