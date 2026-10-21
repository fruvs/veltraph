package dev.veltraph.client.mining.vein;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
final class VeinCollectionMotionTest {
 @Test void NonzeroSubthresholdNativeDecayDoesNotRequireExactZero(){assertTrue(VeinCollectionMotion.unchangedOrDecaying(1e-9,-.08,-1e-9,5e-10,-.04,-5e-10));}
 @Test void NewImpulseFromZeroRefuses(){assertFalse(VeinCollectionMotion.unchangedOrDecaying(0,-.08,0,.001,-.08,0));}
 @Test void GrowingOrReversedImpulseRefuses(){assertFalse(VeinCollectionMotion.decay(.01,.02));assertFalse(VeinCollectionMotion.decay(.01,-.005));}
 @Test void UpwardOrGrowingVerticalImpulseRefuses(){assertFalse(VeinCollectionMotion.unchangedOrDecaying(0,-.08,0,0,.01,0));assertFalse(VeinCollectionMotion.unchangedOrDecaying(0,-.08,0,0,-2,0));}
 @Test void NonfiniteMotionRefuses(){assertFalse(VeinCollectionMotion.decay(Double.NaN,0));assertFalse(VeinCollectionMotion.decay(0,Double.POSITIVE_INFINITY));}
 @Test void NativeZeroAfterFrictionOrCollisionRemainsValid(){assertTrue(VeinCollectionMotion.unchangedOrDecaying(.01,-.08,-.01,0,0,0));}
}
