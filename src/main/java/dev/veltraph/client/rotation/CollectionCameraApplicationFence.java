package dev.veltraph.client.rotation;
import java.util.Optional;
/** Ordering only; original native Root authority and typed source proof are independently mandatory. */
public final class CollectionCameraApplicationFence {
 private CollectionCameraApplicationFence(){}
 public interface Effects {boolean beforeObserver();void observe();boolean beforeAdvance();Optional<OwnedRotationTrajectory.Application> advance();boolean beforeApply(OwnedRotationTrajectory.Application actual);boolean apply(OwnedRotationTrajectory.Application actual);}
 public static boolean run(Effects effects){if(!effects.beforeObserver())return false;effects.observe();if(!effects.beforeAdvance())return false;var actual=effects.advance();return actual.isPresent()&&effects.beforeApply(actual.get())&&effects.apply(actual.get());}
}
