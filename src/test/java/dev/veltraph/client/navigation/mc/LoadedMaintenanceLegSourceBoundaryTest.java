package dev.veltraph.client.navigation.mc;
import dev.veltraph.client.core.Module;
import dev.veltraph.client.navigation.SharedMovementPolicy;
import dev.veltraph.client.world.Vec3;
import java.lang.reflect.Modifier;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
/** Boundary tests only. No native authority, loan or loaded segment is manufactured here. */
class LoadedMaintenanceLegSourceBoundaryTest {
 @Test void loadedPositiveAndCleanupSourcesCannotBeCopiedFromPublicValues()throws Exception{
  for(var suffix:new String[]{"$Leg$LoadedMaintenanceUseSource","$Leg$LoadedMaintenanceCleanupSource"}){
   var type=Class.forName(LegacyTravelPublicationLease.class.getName()+suffix);
   assertTrue(Modifier.isPrivate(type.getModifiers()));assertEquals(0,type.getConstructors().length);
   for(var constructor:type.getDeclaredConstructors())assertTrue(Modifier.isPrivate(constructor.getModifiers()));
  }
 }
 @Test void selectedSourceAdmissionRequiresActualTypedSourceAndChild()throws Exception{
  var method=LegacyTravelPublicationLease.class.getMethod("beginAfterLoadedSegment",McLoadedGroundReturnNavigator.SelectedLoadedSegment.class,
      McSharedMovementNavigator.class,long.class,Module.class,SharedMovementPolicy.Purpose.class,Vec3.class);
  assertEquals(LegacyTravelPublicationLease.Leg.class,method.getReturnType());
 }
 @Test void internalContinuationRequiresActualTypedPredecessor()throws Exception{
  var method=LegacyTravelPublicationLease.class.getMethod("beginAfterLoadedContinuation",McLoadedGroundReturnNavigator.SelectedLoadedSegment.class,
      McSharedMovementNavigator.class,long.class,SharedMovementPolicy.Purpose.class,Vec3.class,LegacyTravelPublicationLease.Leg.class);
  assertEquals(LegacyTravelPublicationLease.Leg.class,method.getReturnType());
 }
 @Test void activeAndRetiredRawObserversAreSeparateMethods()throws Exception{
  for(var name:new String[]{"loadedMaintenanceSourceFieldsCurrent","loadedMaintenanceRetiredFieldsCurrent","loadedSegmentSourceFieldsCurrent"})
   assertEquals(boolean.class,LegacyTravelPublicationLease.Leg.class.getMethod(name,McLoadedGroundReturnNavigator.SelectedLoadedSegment.class).getReturnType());
 }
 @Test void originalPublicOrdinarySnapshotFactoryKeepsItsNoArgumentSignature()throws Exception{
  assertEquals(Object.class,LegacyTravelPublicationLease.Leg.class.getMethod("ownedUseSnapshot").getReturnType());
  assertEquals(Object.class,LegacyTravelPublicationLease.Leg.class.getMethod("normalReturnCleanupSnapshot").getReturnType());
 }
 @Test void genuineRestorationWitnessCacheIsPrivateAndTyped()throws Exception{
  var field=LegacyTravelPublicationLease.Leg.class.getDeclaredField("loadedReturnedRestoration");
  assertTrue(Modifier.isPrivate(field.getModifiers()));assertEquals(LegacyTravelPublicationLease.Leg.NormalReturnRestorationWitness.class,field.getType());
 }
}
