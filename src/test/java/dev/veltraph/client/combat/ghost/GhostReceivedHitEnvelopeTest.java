package dev.veltraph.client.combat.ghost;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GhostReceivedHitEnvelopeTest {
    private final Object player=new Object(),world=new Object(),connection=new Object(),actor=new Object(),job=new Object();
    private final java.util.UUID uuid=java.util.UUID.randomUUID();
    private GhostReceivedHitEnvelope.Frame frame(Vec3 eye,Vec3 look,Vec3 current,Vec3 received,Vec3 endpoint,AABB box){
        return new GhostReceivedHitEnvelope.Frame(player,world,connection,actor,uuid,job,7,10,0,eye,look,current,received,endpoint,box,0);
    }
    private final Vec3 eye=new Vec3(0,77.62,0),look=new Vec3(0,0,1),position=new Vec3(0,76,2.25);
    private final AABB box=new AABB(-.3,76,1.95,.3,77.7,2.55);
    @Test void literalCdPostKnockbackRayHitsHistoricalClientBodyButMissesLandedBody(){
        var eye=new Vec3(132.86482363508404,77.62000000476837,64.5775071699949);var ray=new Vec3(.9553899168968201,.06352153420448303,-.2884145677089691);
        var clientBox=new AABB(135.41113056783368,76.52425129427138,63.46455197634784,136.01113059167554,78.2242513419551,64.0645520001897);
        var landedBox=new AABB(135.7002950787252,76,63.55686167744666,136.30029510256705,77.70000004768372,64.15686170128852);
        assertTrue(clientBox.clip(eye,eye.add(ray.scale(3))).isPresent());assertTrue(landedBox.clip(eye,eye.add(ray.scale(3))).isEmpty());
        var current=new Vec3(135.71113057975461,76.52425129427138,63.76455198826877);var received=new Vec3(136.00029509064612,76,63.85686168936759);
        // Landed pose supplied as geometry counterexample; this does not claim CD had received that packet already.
        assertFalse(GhostReceivedHitEnvelope.allows(frame(eye,ray,current,received,received,clientBox)));
    }
    @Test void legitimatelySpawnedNativeActorAtOriginRemainsAdmissible(){var origin=Vec3.ZERO;assertTrue(GhostReceivedHitEnvelope.allows(frame(new Vec3(0,1.62,-2),look,origin,origin,origin,new AABB(-.3,0,-.3,.3,1.7,.3))));}
    @Test void settledNativeBoxAndReceivedPoseRetainOrdinaryHit(){assertTrue(GhostReceivedHitEnvelope.allows(frame(eye,look,position,position,position,box)));}
    @Test void overlappingMovingNativeBoxesAllowACommonHitWithoutWaitingForStationarity(){assertTrue(GhostReceivedHitEnvelope.allows(frame(eye,look,position,position.add(.1,0,.1),position.add(.2,0,.2),box)));}
    @Test void currentElevatedKnockbackBoxCannotAuthorizeRayAboveReceivedLandingBody(){var high=box.move(0,.525,0);var elevated=position.add(0,.525,0);var upward=new Vec3(0,.07,1).normalize();assertTrue(high.clip(eye,eye.add(upward.scale(3))).isPresent());assertFalse(GhostReceivedHitEnvelope.allows(frame(eye,upward,elevated,position,position,high)));}
    @Test void currentOnlySideHitCannotAuthorizeReceivedMovedBody(){assertFalse(GhostReceivedHitEnvelope.allows(frame(eye,look,position,position.add(.5,0,0),position.add(.5,0,0),box)));}
    @Test void endpointOnlyHitCannotAuthorizeCurrentMiss(){var shifted=box.move(.5,0,0);assertFalse(GhostReceivedHitEnvelope.allows(frame(eye,look,position.add(.5,0,0),position,position,shifted)));}
    @Test void interpolationEndpointAlsoMustShareTheHitCorridor(){assertFalse(GhostReceivedHitEnvelope.allows(frame(eye,look,position,position,position.add(.5,0,0),box)));}
    @Test void disjointEndpointBoxesCannotPassByIntersectingSeparateRayLocations(){var vertical=new Vec3(0,1,0);assertFalse(GhostReceivedHitEnvelope.allows(frame(new Vec3(0,75,2.25),vertical,position,position.add(0,2,0),position.add(0,2,0),box)));}
    @Test void rangeBeyondThreeRemainsRejected(){var far=position.add(0,0,1.1);assertFalse(GhostReceivedHitEnvelope.allows(frame(eye,look,far,far,far,box.move(0,0,1.1))));}
    @Test void justInsideNativeThreeBoundaryIsAccepted(){var edge=new AABB(-.3,76,2.999,.3,77.7,3.6);var p=new Vec3(0,76,3.299);assertTrue(GhostReceivedHitEnvelope.allows(frame(eye,look,p,p,p,edge)));}
    @Test void uninitializedCodecBaseAndMalformedVectorsFailClosed(){assertFalse(GhostReceivedHitEnvelope.allows(frame(eye,look,position,Vec3.ZERO,position,box)));assertFalse(GhostReceivedHitEnvelope.allows(frame(eye,new Vec3(0,0,2),position,position,position,box)));assertFalse(GhostReceivedHitEnvelope.allows(frame(eye,look,position,new Vec3(Double.NaN,0,0),position,box)));assertFalse(GhostReceivedHitEnvelope.allows(null));}
    @Test void packetEndpointChangingAfterGeometryInvalidatesFrame(){var first=frame(eye,look,position,position,position,box);assertTrue(GhostReceivedHitEnvelope.same(first,frame(eye,look,position,position,position,box)));assertFalse(GhostReceivedHitEnvelope.same(first,frame(eye,look,position,position.add(.01,0,0),position,box)));}
    @Test void postReadWorldActorJobCameraAndTickChangesInvalidateFrame(){var f=frame(eye,look,position,position,position,box);assertFalse(GhostReceivedHitEnvelope.same(f,new GhostReceivedHitEnvelope.Frame(player,new Object(),connection,actor,uuid,job,7,10,0,eye,look,position,position,position,box,0)));assertFalse(GhostReceivedHitEnvelope.same(f,new GhostReceivedHitEnvelope.Frame(player,world,connection,new Object(),uuid,job,7,10,0,eye,look,position,position,position,box,0)));assertFalse(GhostReceivedHitEnvelope.same(f,new GhostReceivedHitEnvelope.Frame(player,world,connection,actor,uuid,new Object(),7,10,0,eye,look,position,position,position,box,0)));assertFalse(GhostReceivedHitEnvelope.same(f,new GhostReceivedHitEnvelope.Frame(player,world,connection,actor,uuid,job,8,11,0,eye,look,position,position,position,box,0)));}
    @Test void literalR4AllPacketEndpointsAgreeAirborneButAttackStillDefers(){
        var e=new Vec3(132.8667956306664,77.62000000476837,64.6395886235289);
        var l=new Vec3(.958467960357666,.029716502875089645,-.2836379408836365);
        var at=new Vec3(135.781982421875,76.582763671875,63.82958984375);
        var nativeBox=new AABB(135.48198240995407,76.582763671875,63.52958983182907,136.08198243379593,78.28276371955872,64.12958985567093);
        var f=frame(e,l,at,at,at,nativeBox);
        assertTrue(GhostReceivedHitEnvelope.allows(f),"Actual agreeing endpoints reproduce the previous admission");
        assertFalse(GhostReceivedHitEnvelope.motionAllows(f,new GhostReceivedHitEnvelope.Motion(false,new Vec3(.09690532869437826,-.2724714643227736,.024645058902520958))));
    }
    @Test void literalR4LaterServerLandingRayRemainsMissWithoutRangeOrHeightRelaxation(){
        var e=new Vec3(132.8667956306664,77.62000000476837,64.6395886235289);
        var l=new Vec3(.9584950804710388,.029716502875089645,-.2835460305213928);
        var b=new AABB(135.77058595434607,76,63.594628762928316,136.37058597818793,77.70000004768372,64.19462878677018);
        assertTrue(b.clip(e,e.add(l.scale(3))).isEmpty());
        double xEntry=(b.minX-e.x)/l.x,yExit=(b.maxY-e.y)/l.y;
        assertTrue(xEntry>3);assertTrue(yExit<xEntry);assertEquals(3.0295307538278236,xEntry,1e-10);
        assertEquals(2.692108262254695,yExit,1e-10);
    }
    @Test void genuineGroundedOrdinaryHitRemainsAdmissibleWithNativeNegativeGravity(){
        assertTrue(GhostReceivedHitEnvelope.motionAllows(frame(eye,look,position,position,position,box),new GhostReceivedHitEnvelope.Motion(true,new Vec3(0,-.0784000015258789,0))));
    }
    @Test void movingGroundedBodyRetainsSharedCorridorWithoutWaitingForStationarity(){
        var f=frame(eye,look,position,position.add(.1,0,.1),position.add(.2,0,.2),box);
        assertTrue(GhostReceivedHitEnvelope.motionAllows(f,new GhostReceivedHitEnvelope.Motion(true,new Vec3(.05,-.0784,.05))));
    }
    @Test void groundedOutgoingMotionBeyondOriginalReachDefersWithoutPadding(){
        var p=new Vec3(0,76,3.299);var edge=new AABB(-.3,76,2.999,.3,77.7,3.599);
        var f=frame(eye,look,p,p,p,edge);assertTrue(GhostReceivedHitEnvelope.allows(f));
        assertFalse(GhostReceivedHitEnvelope.motionAllows(f,new GhostReceivedHitEnvelope.Motion(true,new Vec3(0,-.0784,.1))));
    }
    @Test void groundedIncomingMotionKeepsOriginalReachBoundary(){
        var p=new Vec3(0,76,3.299);var edge=new AABB(-.3,76,2.999,.3,77.7,3.599);
        assertTrue(GhostReceivedHitEnvelope.motionAllows(frame(eye,look,p,p,p,edge),new GhostReceivedHitEnvelope.Motion(true,new Vec3(0,-.0784,-.1))));
    }
    @Test void observedLandingAndNaturalNewRayCanResumeAfterAirborneDeferral(){
        var f=frame(eye,look,position,position,position,box);
        assertFalse(GhostReceivedHitEnvelope.motionAllows(f,new GhostReceivedHitEnvelope.Motion(false,new Vec3(.1,-.2,0))));
        assertTrue(GhostReceivedHitEnvelope.motionAllows(f,new GhostReceivedHitEnvelope.Motion(true,new Vec3(.01,-.0784,0))));
    }
    @Test void airborneAscentAndApexAlsoCannotManufactureGroundedLandingPermission(){
        var f=frame(eye,look,position,position,position,box);
        assertFalse(GhostReceivedHitEnvelope.motionAllows(f,new GhostReceivedHitEnvelope.Motion(false,new Vec3(0,.2,0))));
        assertFalse(GhostReceivedHitEnvelope.motionAllows(f,new GhostReceivedHitEnvelope.Motion(false,Vec3.ZERO)));
    }
    @Test void contradictoryGroundedUpwardImpulseDefers(){
        assertFalse(GhostReceivedHitEnvelope.motionAllows(frame(eye,look,position,position,position,box),new GhostReceivedHitEnvelope.Motion(true,new Vec3(0,.1,0))));
    }
    @Test void malformedMotionNeverAddsPermission(){
        var f=frame(eye,look,position,position,position,box);
        assertFalse(GhostReceivedHitEnvelope.motionAllows(f,null));assertFalse(GhostReceivedHitEnvelope.motionAllows(f,new GhostReceivedHitEnvelope.Motion(true,null)));
        assertFalse(GhostReceivedHitEnvelope.motionAllows(f,new GhostReceivedHitEnvelope.Motion(true,new Vec3(Double.NaN,0,0))));
        assertFalse(GhostReceivedHitEnvelope.motionAllows(null,new GhostReceivedHitEnvelope.Motion(true,Vec3.ZERO)));
    }
    @Test void motionDoesNotRescueOriginalEndpointVeto(){
        var f=frame(eye,look,position,position.add(.5,0,0),position.add(.5,0,0),box);
        assertFalse(GhostReceivedHitEnvelope.motionAllows(f,new GhostReceivedHitEnvelope.Motion(true,new Vec3(-.5,0,0))));
    }
    @Test void finalNativeGroundChangeInvalidatesExactMotionObservation(){
        var first=new GhostReceivedHitEnvelope.Motion(false,new Vec3(.1,-.2,.03));
        assertFalse(GhostReceivedHitEnvelope.sameMotion(first,new GhostReceivedHitEnvelope.Motion(true,first.velocity())));
    }
    @Test void finalNativeVelocityChangeInvalidatesExactMotionObservation(){
        var first=new GhostReceivedHitEnvelope.Motion(true,new Vec3(.1,-.0784,.03));
        assertTrue(GhostReceivedHitEnvelope.sameMotion(first,new GhostReceivedHitEnvelope.Motion(true,first.velocity())));
        assertFalse(GhostReceivedHitEnvelope.sameMotion(first,new GhostReceivedHitEnvelope.Motion(true,new Vec3(.1001,-.0784,.03))));
        assertFalse(GhostReceivedHitEnvelope.sameMotion(first,null));
    }
}
