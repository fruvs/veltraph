package dev.veltraph.client.navigation;
import dev.veltraph.client.world.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class GroundLocomotionDropTest {
    private static final Vec3 ORIGIN=new Vec3(.5,71,3.5),LANDING=new Vec3(.5,70,4.5);
    private static final WalkController.Output WALK=new WalkController.Output(true,false,0,WalkController.State.WALKING);
    private static GroundLocomotionPolicy.Proof proof(Vec3 origin){return new GroundLocomotionPolicy.Proof(LANDING,true,true,false,false,origin);}
    private static GroundLocomotionPolicy.Frame frame(Vec3 p,boolean ground,long now){return new GroundLocomotionPolicy.Frame(p,ground,false,now,true,true);}
    @Test void originalGroundedDropAdmissionObservesDepartureAndLandingWithoutJump(){
        var p=new GroundLocomotionPolicy();var initial=p.tick(frame(ORIGIN,true,100),WALK,proof(ORIGIN));
        assertEquals(GroundLocomotionPolicy.Phase.DROPPING,p.phase());assertTrue(initial.forward());assertFalse(initial.jump());assertFalse(initial.sprint());
        var air=p.tick(frame(new Vec3(.5,70.8,4.1),false,200),WALK,proof(ORIGIN));assertTrue(air.forward());assertFalse(air.blocked());
        var land=p.tick(frame(LANDING,true,400),WALK,proof(ORIGIN));assertEquals(GroundLocomotionPolicy.Phase.LANDED,p.phase());assertFalse(land.forward());assertFalse(land.jump());
    }
    @Test void foreignFallCannotCreateAReceiptFromAnOriginalEdgeWhileAlreadyAirborne(){
        var p=new GroundLocomotionPolicy();assertTrue(p.tick(frame(new Vec3(.5,70.8,4.1),false,100),WALK,proof(ORIGIN)).blocked());
        assertTrue(new GroundLocomotionPolicy().tick(frame(new Vec3(.5,70.8,4.1),false,100),WALK,proof(null)).blocked());
    }
    @Test void originalDropPermissionMustRemainTheSameFreshEdgeAndLanding(){
        var p=new GroundLocomotionPolicy();p.tick(frame(ORIGIN,true,100),WALK,proof(ORIGIN));
        assertTrue(p.tick(frame(new Vec3(.5,70.8,4.1),false,200),WALK,proof(null)).blocked());
        var q=new GroundLocomotionPolicy();q.tick(frame(ORIGIN,true,100),WALK,proof(ORIGIN));
        assertTrue(q.tick(frame(new Vec3(.5,70.8,4.1),false,200),WALK,proof(new Vec3(.5,71,3.6))).blocked());
    }
    @Test void dropDepartureAndLandingRemainFiniteAndCannotBeInferredFromElapsedTime(){
        var p=new GroundLocomotionPolicy();p.tick(frame(ORIGIN,true,100),WALK,proof(ORIGIN));
        assertTrue(p.tick(frame(ORIGIN,true,801),WALK,proof(ORIGIN)).blocked());
        var q=new GroundLocomotionPolicy();q.tick(frame(ORIGIN,true,100),WALK,proof(ORIGIN));q.tick(frame(new Vec3(.5,70.8,4.1),false,200),WALK,proof(ORIGIN));
        assertTrue(q.tick(frame(new Vec3(.5,70.8,4.1),false,1901),WALK,proof(ORIGIN)).blocked());
    }
    @Test void originalFractionalDropAndShiftedLevelPointKeepPhysicalAndCardinalNodeCertificates(){
        var fractional=new Vec3(.5,70.125,4.5);
        assertTrue(GroundLocomotionMotion.drop(new Vec3(.5,70.8,4.1),new Vec3(0,-.2,.1),ORIGIN,fractional,.1));
        var shifted=new Vec3(.6,71,3.5);var current=new Vec3(.56,70.8,4.1);
        assertTrue(GroundLocomotionMotion.drop(current,new Vec3(-.01,-.2,.1),shifted,LANDING,.1));
        assertFalse(GroundLocomotionMotion.drop(current,new Vec3(0,-.2,.1),ORIGIN,new Vec3(.5,70.5,4.5),.1));
    }
    @Test void actualLandingMotionRetainsOnlySupportedFiniteNeutralBrakeEnvelope(){
        assertTrue(GroundLocomotionMotion.landingMotion(new Vec3(.5,70,4.7),new Vec3(0,-.0784,.117),LANDING,.1));
        assertFalse(GroundLocomotionMotion.landingMotion(new Vec3(.5,70,5.2),new Vec3(0,-.0784,.117),LANDING,.1));
        assertFalse(GroundLocomotionMotion.landingMotion(LANDING,new Vec3(.4,-.0784,.117),LANDING,.1));
        assertFalse(GroundLocomotionMotion.landingMotion(LANDING,new Vec3(0,-.0784,.117),LANDING,.2));
    }
    @Test void dropMotionGateRejectsNonCardinalBigFallsUnknownSpeedAndForeignImpulse(){
        assertTrue(GroundLocomotionMotion.drop(new Vec3(.5,70.8,4.1),new Vec3(0,-.2,.1),ORIGIN,LANDING,.1));
        assertFalse(GroundLocomotionMotion.drop(new Vec3(.5,70.8,4.1),new Vec3(.4,-.2,.1),ORIGIN,LANDING,.1));
        assertFalse(GroundLocomotionMotion.drop(new Vec3(.5,70.8,4.1),new Vec3(0,-.2,.1),ORIGIN,LANDING,.2));
        assertFalse(GroundLocomotionMotion.drop(new Vec3(.5,70.8,4.1),new Vec3(0,-.2,.1),ORIGIN,new Vec3(1.5,70,4.5),.1));
        assertFalse(GroundLocomotionMotion.drop(new Vec3(.5,69.8,4.1),new Vec3(0,-.7,.1),ORIGIN,new Vec3(.5,69,4.5),.1));
    }
}
