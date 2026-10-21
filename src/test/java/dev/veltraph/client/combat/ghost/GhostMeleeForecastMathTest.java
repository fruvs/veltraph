package dev.veltraph.client.combat.ghost;

import dev.veltraph.client.world.Vec3;
import net.minecraft.world.phys.AABB;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Pure native AABB forecast math. Native scope/body/support guards remain separately mandatory. */
class GhostMeleeForecastMathTest {
    static final Vec3 ACTOR=new Vec3(0,76,0),TARGET=new Vec3(0,76,2.25),ZERO=new Vec3(0,0,0);
    static final net.minecraft.world.phys.Vec3 EYE=new net.minecraft.world.phys.Vec3(0,77.62,0),LOOK=new net.minecraft.world.phys.Vec3(0,0,1);
    static final AABB BOX=new AABB(-.3,76,1.95,.3,77.7,2.55);
    boolean safe(Vec3 actorVelocity,Vec3 intent){return GhostMeleeForecastMath.allows(ACTOR,TARGET,actorVelocity,intent,2.25,EYE,LOOK,BOX);}
    @Test void boundedUnrealizedHorizontalIntentCanRemainInsideActualAndForecastHitVolume(){assertTrue(safe(ZERO,new Vec3(0,0,.392)));}
    @Test void incomingVelocityThatPredictsLeavingStandoffOrHitVolumeMustReengage(){assertFalse(safe(ZERO,new Vec3(0,0,.65)));assertFalse(safe(ZERO,new Vec3(0,0,.8)));}
    @Test void lateralIntentThatLeavesTheCurrentPhysicalRayCannotAuthorizeStay(){assertFalse(safe(ZERO,new Vec3(.392,0,0)));}
    @Test void verticalIntentIsNeverReinterpretedAsStationary(){assertFalse(safe(ZERO,new Vec3(0,.03,0)));assertFalse(safe(ZERO,new Vec3(0,-.03,0)));assertFalse(safe(ZERO,new Vec3(0,.001,0)));}
    @Test void excessiveActorMomentumOrRiseCannotBeHiddenByTheDistanceBand(){assertFalse(safe(new Vec3(0,0,.36),ZERO));assertFalse(safe(new Vec3(0,.03,0),ZERO));assertFalse(safe(new Vec3(0,-.03,0),ZERO));assertFalse(safe(new Vec3(0,-.001,0),ZERO));}
    @Test void actorMomentumPredictionMustRetainStandoffAsWellAsThreeBlockHitReach(){var actor=new Vec3(0,76,0);var target=new Vec3(0,76,2.75);var box=new AABB(-.3,76,2.45,.3,77.7,3.05);assertFalse(GhostMeleeForecastMath.allows(actor,target,new Vec3(0,0,-.2),ZERO,2.25,EYE,LOOK,box));}
    @Test void invalidOrNonfiniteRayBoxAndOperandsCannotCreateGeometricAdmission(){assertFalse(safe(new Vec3(Double.NaN,0,0),ZERO));assertFalse(GhostMeleeForecastMath.allows(ACTOR,TARGET,ZERO,ZERO,2.25,EYE,new net.minecraft.world.phys.Vec3(0,0,2),BOX));}
    @Test void targetOutsideCurrentReachCannotEnterThroughAFutureApproachPrediction(){var far=new Vec3(0,76,3.5);var box=new AABB(-.3,76,3.2,.3,77.7,3.8);assertFalse(GhostMeleeForecastMath.allows(ACTOR,far,ZERO,new Vec3(0,0,-.5),2.25,EYE,LOOK,box));}
}
