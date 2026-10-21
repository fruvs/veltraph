package dev.veltraph.client.navigation;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
final class GroundDynamicStageCostErrorContainmentTest {
 @Test void linkageClockFailureIsUnavailableWithoutReplacingOriginalRefusal(){
  var calls=new AtomicInteger();var trace=new ArrayList<String>();var observed=new GroundDynamicStageCost(()->{calls.incrementAndGet();throw new LinkageError("diagnostic");});
  trace.add("original-plan");assertDoesNotThrow(()->observed.mark(0));trace.add("original-refusal");var snapshot=assertDoesNotThrow(observed::freeze);trace.add("original-stop");
  assertEquals(java.util.List.of("original-plan","original-refusal","original-stop"),trace);assertEquals(1,calls.get());assertTrue(observed.observationUnavailable());assertNull(snapshot.beforeOriginalPlanNanos());assertEquals(1L,snapshot.failedDiagnosticClockMask());
 }
 @Test void assertionClockFailureDoesNotBecomeTimestamp(){
  var observed=new GroundDynamicStageCost(()->{throw new AssertionError("observer");});assertDoesNotThrow(()->observed.mark(7));var snapshot=observed.freeze();assertTrue(observed.observationUnavailable());assertEquals(1L<<7,snapshot.failedDiagnosticClockMask());assertNull(snapshot.beforeOriginalCaptureGuardsNanos());
 }
 @Test void allocationClockFailureDoesNotEscapeOrFabricateVisitedValue(){
  var observed=new GroundDynamicStageCost(()->{throw new OutOfMemoryError("injected diagnostic");});assertDoesNotThrow(()->observed.mark(9));var snapshot=observed.freeze();assertEquals(0L,snapshot.visitedMask());assertEquals(1L<<9,snapshot.failedDiagnosticClockMask());assertNull(snapshot.beforeOriginalPlaneEligibilityNanos());
 }
 @Test void realSnapshotFactoryLinkageErrorIsContainedBeforeCleanup(){
  var calls=new AtomicInteger();var observed=new GroundDynamicStageCost(()->44L,o->{calls.incrementAndGet();throw new NoClassDefFoundError("diagnostic snapshot");});observed.mark(0);
  assertNull(assertDoesNotThrow(observed::freeze));assertTrue(observed.observationUnavailable());assertEquals(1,calls.get());var cleanup=new AtomicInteger();cleanup.incrementAndGet();assertEquals(1,cleanup.get());
 }
 @Test void realSnapshotFactoryAllocationErrorReturnsUnavailableNotPartialSnapshot(){
  var observed=new GroundDynamicStageCost(()->55L,o->{throw new OutOfMemoryError("injected snapshot allocation");});observed.mark(0);assertNull(assertDoesNotThrow(observed::freeze));assertTrue(observed.observationUnavailable());
 }
 @Test void nullObserverSnapshotCannotPretendCompleteObservation(){
  var observed=new GroundDynamicStageCost(()->66L,o->null);observed.mark(0);assertNull(observed.freeze());assertTrue(observed.observationUnavailable());
 }
 @Test void originalPrimaryErrorOutsideObserverRemainsSameObject(){
  var observed=new GroundDynamicStageCost(()->{throw new LinkageError("observer");});var primary=new AssertionError("original operation");var actual=assertThrows(AssertionError.class,()->{observed.mark(0);throw primary;});assertSame(primary,actual);
 }
 @Test void validObserverStillUsesOriginalOneReadAndIdempotentSnapshot(){
  var reads=new AtomicInteger();var observed=new GroundDynamicStageCost(()->100L+reads.incrementAndGet());observed.mark(0);observed.mark(12);var first=observed.freeze();assertSame(first,observed.freeze());assertFalse(observed.observationUnavailable());assertEquals(2,reads.get());assertEquals(101L,first.beforeOriginalPlanNanos());assertEquals(102L,first.afterOriginalOuterEligibilityGuardNanos());
 }
}
