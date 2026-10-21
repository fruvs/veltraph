package dev.veltraph.client.navigation;

import dev.veltraph.client.world.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GroundLocomotionPolicyTest {
    private static final Vec3 START=new Vec3(.5,1,.5), STEP=new Vec3(.5,2,1.5), FAR=new Vec3(.5,1,8.5);
    private static GroundLocomotionPolicy.Frame frame(Vec3 pos,boolean ground,boolean sprinting,long now){
        return new GroundLocomotionPolicy.Frame(pos,ground,sprinting,now,true,true);
    }
    private static WalkController.Output requested(boolean jump){return new WalkController.Output(true,jump,0,WalkController.State.WALKING);}
    private static GroundLocomotionPolicy.Proof proof(Vec3 target,boolean jump,boolean sprint){return new GroundLocomotionPolicy.Proof(target,true,true,jump,sprint);}
    private static void launch(GroundLocomotionPolicy p){assertTrue(p.tick(frame(START,true,false,100),requested(true),proof(STEP,true,false)).jump());}
    @Test void flatSupportedTravelUsesSprintOnlyWithNativeAvailabilityAndWideProof(){
        var p=new GroundLocomotionPolicy();var out=p.tick(frame(START,true,false,100),requested(false),proof(FAR,false,true));
        assertTrue(out.forward());assertTrue(out.sprint());assertFalse(out.jump());assertFalse(out.blocked());
    }
    @Test void narrowOrLowCeilingProofKeepsOrdinaryWalking(){
        var out=new GroundLocomotionPolicy().tick(frame(START,true,false,100),requested(false),proof(FAR,false,false));
        assertTrue(out.forward());assertFalse(out.sprint());assertFalse(out.jump());
    }
    @Test void HungerOrNativeSprintUnavailableKeepsWalking(){
        var out=new GroundLocomotionPolicy().tick(new GroundLocomotionPolicy.Frame(START,true,false,100,true,false),requested(false),proof(FAR,false,true));
        assertTrue(out.forward());assertFalse(out.sprint());
    }
    @Test void nativeSprintFlagRequiresNeutralBrakeWhenProofNoLongerAllowsSprint(){
        var out=new GroundLocomotionPolicy().tick(frame(START,true,true,100),requested(false),proof(FAR,false,false));
        assertFalse(out.forward());assertFalse(out.sprint());assertFalse(out.blocked());
    }
    @Test void supportedRisingEdgeIssuesExactlyOneJumpPress(){
        var p=new GroundLocomotionPolicy();launch(p);assertEquals(GroundLocomotionPolicy.Phase.TAKEOFF,p.phase());
        var waiting=p.tick(frame(START,true,false,150),requested(true),proof(STEP,true,false));assertTrue(waiting.forward());assertFalse(waiting.jump());
        var airborne=p.tick(frame(new Vec3(.5,1.3,.8),false,false,200),requested(true),proof(STEP,true,false));
        assertEquals(GroundLocomotionPolicy.Phase.AIRBORNE,p.phase());assertFalse(airborne.jump());assertFalse(airborne.sprint());
    }
    @Test void jumpDoesNotTrustTimeAsDepartureReceipt(){
        var p=new GroundLocomotionPolicy();launch(p);var out=p.tick(frame(START,true,false,451),requested(true),proof(STEP,true,false));
        assertTrue(out.blocked());assertFalse(out.forward());assertFalse(out.jump());
    }
    @Test void airborneLandingDeadlineIsFinite(){
        var p=new GroundLocomotionPolicy();launch(p);p.tick(frame(new Vec3(.5,1.4,.8),false,false,200),requested(false),proof(STEP,true,false));
        assertTrue(p.tick(frame(new Vec3(.5,2,1.5),false,false,1901),requested(false),proof(STEP,true,false)).blocked());
    }
    @Test void actualSupportedLandingReleasesAndDoesNotHoldJumpForNextEdge(){
        var p=new GroundLocomotionPolicy();launch(p);p.tick(frame(new Vec3(.5,1.4,.8),false,false,200),requested(false),proof(STEP,true,false));
        var out=p.tick(frame(STEP,true,false,600),requested(false),proof(STEP,true,false));
        assertEquals(GroundLocomotionPolicy.Phase.LANDED,p.phase());assertFalse(out.forward());assertFalse(out.jump());assertFalse(out.sprint());assertFalse(out.blocked());
        assertTrue(p.tick(frame(STEP,true,false,650),requested(false),proof(new Vec3(.5,2,6),false,false)).forward());
    }
    @Test void landingOutsideExactAdmittedWindowBlocks(){
        var p=new GroundLocomotionPolicy();launch(p);p.tick(frame(new Vec3(.5,1.4,.8),false,false,200),requested(false),proof(STEP,true,false));
        assertTrue(p.tick(frame(new Vec3(2,2,1.5),true,false,600),requested(false),proof(STEP,true,false)).blocked());
    }
    @Test void disappearingLandingOrFreshJumpSweepBlocksAirborneContinuation(){
        for(boolean landing:new boolean[]{false,true}){
            var p=new GroundLocomotionPolicy();launch(p);
            assertTrue(p.tick(frame(new Vec3(.5,1.4,.8),false,false,200),requested(false),new GroundLocomotionPolicy.Proof(STEP,true,landing,false,false)).blocked());
        }
    }
    @Test void changingLandingDoesNotTransferAJumpCertificate(){
        var p=new GroundLocomotionPolicy();launch(p);
        assertTrue(p.tick(frame(new Vec3(.5,1.4,.8),false,false,200),requested(false),proof(new Vec3(1.5,2,1.5),true,false)).blocked());
    }
    @Test void jumpNeedsFreshSupportedGroundForwardAlignmentAndOneBlockBound(){
        for(var in:new GroundLocomotionPolicy.Frame[]{frame(START,false,false,100),frame(START,true,false,100)}){
            var p=new GroundLocomotionPolicy();var target=in.onGround()?new Vec3(.5,3,1.5):STEP;
            assertTrue(p.tick(in,requested(true),proof(target,true,false)).blocked());
        }
        assertTrue(new GroundLocomotionPolicy().tick(frame(START,true,false,100),new WalkController.Output(false,true,0,WalkController.State.WALKING),proof(STEP,true,false)).blocked());
        assertTrue(new GroundLocomotionPolicy().tick(frame(START,true,false,100),requested(true),proof(new Vec3(.5,2,4.5),true,false)).blocked());
    }
    @Test void unsupportedJumpEffectsOrHeadroomNeverGrantJump(){
        assertTrue(new GroundLocomotionPolicy().tick(frame(START,true,false,100),requested(true),proof(STEP,false,false)).blocked());
    }
    @Test void sprintingIsNeutralizedBeforeAOneBlockStepJump(){
        var p=new GroundLocomotionPolicy();var out=p.tick(frame(START,true,true,100),requested(true),proof(STEP,true,false));
        assertFalse(out.forward());assertFalse(out.jump());assertEquals(GroundLocomotionPolicy.Phase.READY,p.phase());
        assertTrue(p.tick(frame(START,true,false,150),requested(true),proof(STEP,true,false)).jump());
    }
    @Test void revokedWorldGuiManualOwnerAndClockCannotRetainAnyAction(){
        var p=new GroundLocomotionPolicy();launch(p);
        assertTrue(p.tick(new GroundLocomotionPolicy.Frame(START,true,false,150,false,true),requested(true),proof(STEP,true,false)).blocked());
        assertTrue(p.tick(frame(START,true,false,200),requested(false),proof(FAR,false,true)).blocked());
        var q=new GroundLocomotionPolicy();q.tick(frame(START,true,false,100),requested(false),proof(FAR,false,true));
        assertTrue(q.tick(frame(START,true,false,99),requested(false),proof(FAR,false,true)).blocked());
    }
    @Test void foreignAirborneMotionWithoutOwnedTakeoffCannotContinueWalking(){
        assertTrue(new GroundLocomotionPolicy().tick(frame(new Vec3(.5,1.3,.5),false,false,100),requested(false),proof(FAR,false,false)).blocked());
    }
    @Test void terminalAndExplicitResetReleaseAndRequireANewObservedDeparture(){
        for(var state:new WalkController.State[]{WalkController.State.ARRIVED,WalkController.State.BLOCKED,WalkController.State.IDLE}){
            var p=new GroundLocomotionPolicy();launch(p);
            var out=p.tick(frame(START,true,false,150),new WalkController.Output(true,true,0,state),proof(STEP,true,false));
            assertFalse(out.forward());assertFalse(out.jump());assertFalse(out.sprint());assertEquals(GroundLocomotionPolicy.Phase.READY,p.phase());
        }
        var p=new GroundLocomotionPolicy();launch(p);p.reset();assertNull(p.takeoff());assertNull(p.landing());launch(p);
    }
}
