package dev.veltraph.client.navigation;

import dev.veltraph.client.world.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static dev.veltraph.client.navigation.CapturedProfileTrajectory.Input;

class CameraRelativeGroundSteeringTest {
    private static final Vec3 ORIGIN = new Vec3(.5D, 70D, .5D);
    private static Vec3 target(double x, double z) { return new Vec3(.5D+x,70D,.5D+z); }
    private static Vec3 heading(double degrees) {
        double r=Math.toRadians(degrees);return target(-Math.sin(r)*5D,Math.cos(r)*5D);
    }
    @Test void zeroYawUsesVanillaForwardPositiveZ() {
        assertEquals(Input.FORWARD,CameraRelativeGroundSteering.select(ORIGIN,target(0,5),0,Input.NEUTRAL));
    }
    @Test void zeroYawLeftMovesPositiveXAndRightNegativeX() {
        assertEquals(Input.LEFT,CameraRelativeGroundSteering.select(ORIGIN,target(5,0),0,Input.NEUTRAL));
        assertEquals(Input.RIGHT,CameraRelativeGroundSteering.select(ORIGIN,target(-5,0),0,Input.NEUTRAL));
    }
    @Test void ninetyYawForwardMovesNegativeX() {
        assertEquals(Input.FORWARD,CameraRelativeGroundSteering.select(ORIGIN,target(-5,0),90,Input.NEUTRAL));
        assertEquals(Input.BACKWARD,CameraRelativeGroundSteering.select(ORIGIN,target(5,0),90,Input.NEUTRAL));
    }
    @Test void twoAxesAreSelectedBeforeCameraHasTurned() {
        assertEquals(Input.FORWARD_RIGHT,CameraRelativeGroundSteering.select(ORIGIN,target(-5,5),0,Input.NEUTRAL));
        assertEquals(Input.FORWARD_LEFT,CameraRelativeGroundSteering.select(ORIGIN,target(5,5),0,Input.NEUTRAL));
    }
    @Test void westFacingCameraUsesLeftForWorldNorth() {
        assertEquals(Input.LEFT,CameraRelativeGroundSteering.select(ORIGIN,target(0,5),90,Input.NEUTRAL));
    }
    @Test void backingAndStrafingCanContinueDuringLargeOwnedTurn() {
        assertEquals(Input.BACKWARD_RIGHT,CameraRelativeGroundSteering.select(ORIGIN,target(-5,-5),0,Input.NEUTRAL));
        assertEquals(Input.BACKWARD_LEFT,CameraRelativeGroundSteering.select(ORIGIN,target(5,-5),0,Input.NEUTRAL));
    }
    @Test void wrapCrossingRetainsEquivalentVanillaBasis() {
        assertEquals(CameraRelativeGroundSteering.select(ORIGIN,target(-3,-5),179,Input.NEUTRAL),
                CameraRelativeGroundSteering.select(ORIGIN,target(-3,-5),-181,Input.NEUTRAL));
    }
    @Test void hysteresisAvoidsBoundaryKeyChatterButCannotHoldWrongSector() {
        assertEquals(Input.FORWARD,CameraRelativeGroundSteering.select(ORIGIN,heading(24),0,Input.FORWARD));
        assertEquals(Input.FORWARD_RIGHT,CameraRelativeGroundSteering.select(ORIGIN,heading(29),0,Input.FORWARD));
        assertEquals(Input.FORWARD_RIGHT,CameraRelativeGroundSteering.select(ORIGIN,heading(21),0,Input.FORWARD_RIGHT));
        assertEquals(Input.FORWARD,CameraRelativeGroundSteering.select(ORIGIN,heading(16),0,Input.FORWARD_RIGHT));
    }
    @Test void priorOppositeInputNeverBecomesAKeyRetentionPermission() {
        assertEquals(Input.FORWARD,CameraRelativeGroundSteering.select(ORIGIN,target(0,5),0,Input.BACKWARD));
    }
    @Test void diagonalUsesNativeUnitSquareRemapWithoutSqrtTwoAcceleration() {
        Vec3 forward=CapturedProfileTrajectory.inputAcceleration(Input.FORWARD,.1F,0F);
        Vec3 diagonal=CapturedProfileTrajectory.inputAcceleration(Input.FORWARD_LEFT,.1F,0F);
        assertEquals((double).98F*(double).1F,Math.hypot(forward.x(),forward.z()),1E-15D);
        assertEquals(.1D,Math.hypot(diagonal.x(),diagonal.z()),1E-7D);
        assertTrue(Math.hypot(diagonal.x(),diagonal.z())<1.021D*Math.hypot(forward.x(),forward.z()));
    }
    @Test void worldDirectionIsUnitLengthInAllEightSectors() {
        for(Input input:Input.values())if(input!=Input.NEUTRAL){
            Vec3 vector=CameraRelativeGroundSteering.worldDirection(127.75F,input);
            assertEquals(1D,Math.hypot(vector.x(),vector.z()),1E-12D);
        }
    }
    @Test void noDesiredCameraYawCanBeConfusedWithActualMovementBasis() {
        assertEquals(Input.RIGHT,CameraRelativeGroundSteering.select(ORIGIN,target(-5,0),0,Input.NEUTRAL));
        assertEquals(Input.FORWARD,CameraRelativeGroundSteering.select(ORIGIN,target(-5,0),90,Input.NEUTRAL));
    }
    @Test void unknownNonfiniteOrVerticalTargetProducesNeutralOnly() {
        assertEquals(Input.NEUTRAL,CameraRelativeGroundSteering.select(null,target(0,5),0,null));
        assertEquals(Input.NEUTRAL,CameraRelativeGroundSteering.select(ORIGIN,new Vec3(Double.NaN,70,5),0,null));
        assertEquals(Input.NEUTRAL,CameraRelativeGroundSteering.select(ORIGIN,target(0,5),Float.NaN,null));
        assertEquals(Input.NEUTRAL,CameraRelativeGroundSteering.select(ORIGIN,new Vec3(.5,71,5.5),0,null));
    }
    @Test void ReachedTargetHasNoDirectionalPulse() {
        assertEquals(Input.NEUTRAL,CameraRelativeGroundSteering.select(ORIGIN,ORIGIN,45,Input.FORWARD));
    }
    @Test void unknownPriorSectorDoesNotDisableFreshValidChoice() {
        assertEquals(Input.FORWARD_RIGHT,CameraRelativeGroundSteering.select(ORIGIN,target(-5,5),0,null));
        assertEquals(Input.FORWARD_RIGHT,CameraRelativeGroundSteering.select(ORIGIN,target(-5,5),0,Input.NEUTRAL));
    }
    @Test void originalFlatCourseAllowsScopeButJumpDropRampOrUnknownCannot() {
        assertTrue(CameraRelativeGroundSteering.flatRoute(ORIGIN,java.util.List.of(target(1,0),target(5,0))));
        assertFalse(CameraRelativeGroundSteering.flatRoute(ORIGIN,java.util.List.of(new Vec3(1.5,71,.5))));
        assertFalse(CameraRelativeGroundSteering.flatRoute(ORIGIN,java.util.List.of(new Vec3(1.5,69,.5))));
        assertFalse(CameraRelativeGroundSteering.flatRoute(ORIGIN,java.util.List.of(new Vec3(1.5,70.0625,.5))));
        assertFalse(CameraRelativeGroundSteering.flatRoute(ORIGIN,java.util.List.of()));
        assertFalse(CameraRelativeGroundSteering.flatRoute(ORIGIN,null));
    }
    @Test void verticalToleranceUsesActualFiniteHalfOpenBoundary() {
        assertEquals(Input.NEUTRAL,CameraRelativeGroundSteering.select(new Vec3(0,0,0),new Vec3(1,.05,1),0,null));
        assertNotEquals(Input.NEUTRAL,CameraRelativeGroundSteering.select(new Vec3(0,0,0),new Vec3(1,Math.nextDown(.05D),1),0,null));
    }
    @Test void nearestSectorIsBoundedAcrossTurnAndNativeYawWrap() {
        for(int yaw=-180;yaw<=180;yaw+=7)for(int targetYaw=-180;targetYaw<=180;targetYaw+=11){
            Vec3 target=heading(targetYaw);Input choice=CameraRelativeGroundSteering.select(ORIGIN,target,yaw,Input.NEUTRAL);
            assertTrue(CameraRelativeGroundSteering.headingError(ORIGIN,target,yaw,choice)<=22.51D);
        }
    }
}
