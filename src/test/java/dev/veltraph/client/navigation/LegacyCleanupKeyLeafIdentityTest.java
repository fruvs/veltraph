package dev.veltraph.client.navigation;
import dev.veltraph.client.core.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class LegacyCleanupKeyLeafIdentityTest {
 static class Scene {
  final Object options=new Object();final Object[] mappings=new Object[9];final boolean[] down=new boolean[9];
  Scene(){for(int i=0;i<9;i++)mappings[i]=new Object();}
  boolean current(Object actualOptions,Object[] expected){return LegacyCleanupKeyLeafIdentity.current(options,actualOptions,expected,mappings,down);}
 }
 @Test void exactNineConfirmedNeutralMappingsHaveFreshFinalEvidence(){var scene=new Scene();assertTrue(scene.current(scene.options,scene.mappings.clone()));}
 @Test void actualLeasedInputSupplierRepressAfterEarlyCleanReadIsDeniedWithoutLifecycleOrControlMutation(){
  var root=new ModuleManager();var scene=new Scene();var expected=scene.mappings.clone();var before=root.lifecycleSnapshot();
  assertTrue(scene.current(scene.options,expected));long generation=4,epoch=7,serial=2;
  root.leasedInputOwner(()->{scene.down[0]=true;return null;});before=root.lifecycleSnapshot();
  assertTrue(root.inputOwner().isEmpty());assertTrue(root.lifecycleCurrent(before));assertEquals(4,generation);assertEquals(7,epoch);assertEquals(2,serial);
  assertFalse(scene.current(scene.options,expected));
 }
 @Test void everyActualKeyIncludingAttackUseHasFreshFalseRequirement(){for(int i=0;i<9;i++){var scene=new Scene();scene.down[i]=true;assertFalse(scene.current(scene.options,scene.mappings.clone()));}}
 @Test void replacementMappingWithSameFalseValueCannotBorrowOldReceipt(){var scene=new Scene();var expected=scene.mappings.clone();scene.mappings[8]=new Object();assertFalse(scene.current(scene.options,expected));}
 @Test void replacedOptionsCannotBorrowNineOldReceipts(){var scene=new Scene();assertFalse(scene.current(new Object(),scene.mappings.clone()));}
 @Test void callbackRepressBeforeTokenReadInvalidatesPreviouslyFreshEvidence(){var scene=new Scene();var expected=scene.mappings.clone();assertTrue(scene.current(scene.options,expected));Runnable provider=()->scene.down[6]=true;provider.run();assertFalse(scene.current(scene.options,expected));}
 @Test void unknownAndPartialPublicationCannotQualify(){var scene=new Scene();assertFalse(LegacyCleanupKeyLeafIdentity.current(scene.options,scene.options,new Object[8],scene.mappings,scene.down));scene.mappings[0]=null;assertFalse(scene.current(scene.options,scene.mappings.clone()));}
}
