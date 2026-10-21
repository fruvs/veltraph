package dev.veltraph.client.skills;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
/** Architectural regression on the actual productive caller, not a synthetic passing motion model. */
class FairyReturnSourceRegressionTest {
 @Test void actualReturnNeverMutatesPlayerPoseDirectly() throws Exception {
  String source=Files.readString(Path.of("src/main/java/dev/veltraph/client/skills/McFairySoulTraversal.java"));
  assertFalse(source.contains("setYRot("),"Typed Fairy currently snaps saved yaw directly");
  assertFalse(source.contains("setXRot("),"Typed Fairy currently snaps saved pitch directly");
 }
 @Test void actualModuleWaitsForCameraCompletion() throws Exception {
  String source=Files.readString(Path.of("src/main/java/dev/veltraph/client/skills/FairySoulModule.java"));
  assertTrue(source.contains("RESTORING_VIEW"),"Physical return currently resumes SEARCHING before camera completion");
  String disable=source.substring(source.indexOf("void onDisable()"),source.indexOf("void onServerMessage("));
  assertFalse(disable.contains("restoreView()"),"Critical disable must never queue or snap a restored camera");
 }
}
