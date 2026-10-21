package dev.veltraph.client.combat.ghost;

import dev.veltraph.client.combat.ghost.GhostSourceSampledRay.Point;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GhostSourceWalkSteeringTest {
    private static GhostSourceWalkSteering.Frame frame(long tick,float yaw,Point target,double vx,double vz,boolean grounded,boolean jump) {
        return new GhostSourceWalkSteering.Frame(tick,new Point(0,0,0),yaw,0,vx,vz,grounded,target,jump,0);
    }
    private static Point bearing(double yaw,double distance) {
        return new Point(-Math.sin(Math.toRadians(yaw))*distance,0,Math.cos(Math.toRadians(yaw))*distance);
    }
    private static float wrap(float angle) { return (angle+540)%360-180; }

    @Test void diagonalAndBackwardCombinationsAreAvailableWithoutAxisAlignedWaypoints() {
        for(int sector=0;sector<8;sector++) {
            var s=new GhostSourceWalkSteering();var out=s.advance(frame(0,0,bearing(sector*45,8),0,0,true,false));
            assertEquals(GhostSourceWalkSteering.Outcome.MOVING,out.outcome());
            assertEquals(sector==0||sector==1||sector==7,out.forward());
            assertEquals(sector==3||sector==4||sector==5,out.back());
            assertEquals(sector==5||sector==6||sector==7,out.left());
            assertEquals(sector==1||sector==2||sector==3,out.right());
            assertFalse(out.forward()&&out.back());assertFalse(out.left()&&out.right());
        }
    }
    @Test void keyChoiceUsesActualYawEvenWhenRequestedFutureYawWouldChooseAnotherSector() {
        var out=new GhostSourceWalkSteering().advance(frame(0,0,bearing(23,8),0,0,true,false));
        assertTrue(out.forward());assertTrue(out.right());
        assertEquals(1.2f,out.yaw(),.0001f);
        assertTrue(23-out.yaw()<22.5); // Requested future yaw would choose forward alone.
    }
    @Test void smallAlternatingBearingAtSectorBoundaryDoesNotTwitchKeys() {
        var s=new GhostSourceWalkSteering();
        for(int tick=0;tick<30;tick++) {
            var out=s.advance(frame(tick,0,bearing(tick%2==0?22:24,8),0,0,true,false));
            assertTrue(out.forward());assertFalse(out.right());assertFalse(out.left());
        }
        assertTrue(s.advance(frame(30,0,bearing(31,8),0,0,true,false)).right());
    }
    @Test void actualCameraStepsWrapAndRemainBoundedUnderAlternatingTargets() {
        var s=new GhostSourceWalkSteering();float yaw=179,previousRate=0;
        for(int tick=0;tick<90;tick++) {
            var out=s.advance(frame(tick,yaw,bearing((tick/9)%2==0?-145:120,8),0,0,true,false));
            float rate=wrap(out.yaw()-yaw);
            assertTrue(Math.abs(rate)<=6.001);assertTrue(Math.abs(rate-previousRate)<=1.201);
            yaw=out.yaw();previousRate=rate;
        }
    }
    @Test void longArcConvergesWithoutSnappingNinetyDegrees() {
        var s=new GhostSourceWalkSteering();float yaw=-70;int diagonals=0;
        for(int tick=0;tick<110;tick++) {
            var out=s.advance(frame(tick,yaw,bearing(110,8),0,0,true,false));
            assertTrue(Math.abs(wrap(out.yaw()-yaw))<=6.001);
            if((out.forward()||out.back())&&(out.left()||out.right()))diagonals++;
            yaw=out.yaw();
        }
        assertTrue(diagonals>0);assertEquals(0,wrap(110-yaw),.03);
    }
    @Test void nativeMomentumReleasesBeforeOvershootingButMovingAwayDoesNotStop() {
        var towards=new GhostSourceWalkSteering().advance(frame(0,0,bearing(0,.30),0,.20,true,false));
        assertEquals(GhostSourceWalkSteering.Outcome.BRAKING,towards.outcome());assertFalse(towards.movingKeys());
        var away=new GhostSourceWalkSteering().advance(frame(0,0,bearing(0,.30),0,-.20,true,false));
        assertEquals(GhostSourceWalkSteering.Outcome.MOVING,away.outcome());assertTrue(away.forward());
        var at=new GhostSourceWalkSteering().advance(frame(0,0,bearing(0,.05),0,0,true,false));
        assertEquals(GhostSourceWalkSteering.Outcome.AT_POINT,at.outcome());assertFalse(at.movingKeys());assertFalse(at.jump());
    }
    @Test void jumpIsOneGroundedPulseUntilObservedAirborneAndCooldownCompletes() {
        var s=new GhostSourceWalkSteering();var target=new Point(0,1,4);
        assertTrue(s.advance(frame(0,0,target,0,0,true,true)).jump());
        for(int tick=1;tick<4;tick++)assertFalse(s.advance(frame(tick,0,target,0,0,true,true)).jump());
        assertFalse(s.advance(frame(4,0,target,0,0,false,true)).jump());
        assertFalse(s.advance(frame(5,0,target,0,0,true,true)).jump());
        assertTrue(s.advance(frame(6,0,target,0,0,true,true)).jump());
        assertFalse(s.advance(frame(7,0,target,0,0,true,true)).jump());
    }
    @Test void staleOrDuplicateTickCannotAdvanceSmootherOrRepeatJump() {
        var s=new GhostSourceWalkSteering();var f=frame(8,0,new Point(-8,1,8),0,0,true,true);
        assertTrue(s.advance(f).jump());
        var duplicate=s.advance(f);assertEquals(GhostSourceWalkSteering.Outcome.REJECTED,duplicate.outcome());assertFalse(duplicate.movingKeys());assertFalse(duplicate.jump());
        assertEquals(GhostSourceWalkSteering.Outcome.REJECTED,s.advance(frame(7,0,new Point(0,0,8),0,0,true,false)).outcome());
        var next=s.advance(frame(9,1.2f,new Point(-8,1,8),0,0,true,true));assertEquals(3.6f,next.yaw(),.001f);
    }
    @Test void sidewaysMotionCorrectsHeadingWithinBoundedDisplacementWithoutSyntheticRandomness() {
        var s=new GhostSourceWalkSteering();var out=s.advance(frame(0,0,new Point(0,0,4),.20,0,true,false));
        assertTrue(out.yaw()>0);assertTrue(out.forward());assertFalse(out.left());assertFalse(out.right());
        var same=new GhostSourceWalkSteering().advance(frame(0,0,new Point(0,0,4),.20,0,true,false));assertEquals(out,same);
    }
    @Test void pitchIsLinearAndUsesSameAccelerationBound() {
        var s=new GhostSourceWalkSteering();float pitch=-70,previousRate=0;
        for(int tick=0;tick<90;tick++) {
            var f=new GhostSourceWalkSteering.Frame(tick,new Point(0,0,0),0,pitch,0,0,true,new Point(0,0,8),false,70);
            var out=s.advance(f);float rate=out.pitch()-pitch;
            assertTrue(rate>=0&&rate<=6.001);assertTrue(Math.abs(rate-previousRate)<=1.201);pitch=out.pitch();previousRate=rate;
        }
        assertEquals(70,pitch,.03);
    }
    @Test void invalidNativeMotionIsRejectedAndResetRestoresInitialOwnedCursorState() {
        var s=new GhostSourceWalkSteering();
        var bad=s.advance(frame(0,0,new Point(0,0,8),Double.NaN,0,true,false));assertEquals(GhostSourceWalkSteering.Outcome.REJECTED,bad.outcome());
        assertEquals(GhostSourceWalkSteering.Outcome.REJECTED,s.advance(frame(0,0,new Point(0,0,8),Double.MAX_VALUE,Double.MAX_VALUE,true,false)).outcome());
        var first=s.advance(frame(0,0,new Point(-8,1,8),0,0,true,true));assertTrue(first.jump());
        s.reset();assertEquals(first,s.advance(frame(0,0,new Point(-8,1,8),0,0,true,true)));
    }
}
