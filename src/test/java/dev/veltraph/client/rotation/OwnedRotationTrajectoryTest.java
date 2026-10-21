package dev.veltraph.client.rotation;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OwnedRotationTrajectoryTest {
    private final Object owner=new Object(),player=new Object(),world=new Object(),connection=new Object();
    private final OwnedRotationTrajectory.Scope scope=new OwnedRotationTrajectory.Scope(owner,player,world,connection);
    private final OwnedRotationTrajectory trajectory=new OwnedRotationTrajectory();
    private static OwnedRotationTrajectory.Pose pose(float yaw,float pitch){return new OwnedRotationTrajectory.Pose(yaw,pitch);}
    @Test void planningDoesNotApplyFutureEndpointAndFramesShareOneTickStep(){
        long ticket=trajectory.plan(scope,10,pose(0,0),pose(6,3));
        assertFalse(trajectory.completed(scope,ticket));
        var first=trajectory.frame(scope,10,0,pose(0,0)).orElseThrow();assertEquals(pose(0,0),first.pose());
        var quarter=trajectory.frame(scope,10,.25f,first.pose()).orElseThrow();assertEquals(pose(1.5f,.75f),quarter.pose());
        var half=trajectory.frame(scope,10,.5f,quarter.pose()).orElseThrow();assertEquals(pose(3,1.5f),half.pose());
        var threeQuarter=trajectory.frame(scope,10,.75f,half.pose()).orElseThrow();assertEquals(pose(4.5f,2.25f),threeQuarter.pose());
        assertFalse(trajectory.completed(scope,ticket));
        var last=trajectory.frame(scope,10,1,threeQuarter.pose()).orElseThrow();assertEquals(pose(6,3),last.pose());
        assertTrue(trajectory.completed(scope,ticket));
    }
    @Test void nextTickConsumesEndpointAndNeverRewindsAnUnrenewedSegment(){
        long ticket=trajectory.plan(scope,10,pose(0,0),pose(6,0));
        var half=trajectory.frame(scope,10,.5f,pose(0,0)).orElseThrow();
        var applied=trajectory.beforeTick(scope,10,half.pose()).orElseThrow();assertEquals(pose(6,0),applied.pose());
        assertTrue(trajectory.completed(scope,ticket));assertFalse(trajectory.active());
        assertTrue(trajectory.frame(scope,11,.1f,applied.pose()).isEmpty());
    }
    @Test void completionSurvivesSameOwnerRenewalButNotForeignOwner(){
        long ticket=trajectory.plan(scope,10,pose(0,0),pose(6,0));
        trajectory.beforeTick(scope,10,pose(0,0)).orElseThrow();
        trajectory.plan(scope,11,pose(6,0),pose(12,0));assertTrue(trajectory.completed(scope,ticket));
        var foreign=new OwnedRotationTrajectory.Scope(new Object(),player,world,connection);
        trajectory.plan(foreign,11,pose(6,0),pose(12,0));assertFalse(trajectory.completed(scope,ticket));
    }
    @Test void yawUsesNearestEquivalentAndPitchTravelsThroughZero(){
        trajectory.plan(scope,1,pose(179,-90),pose(-179,90));
        var half=trajectory.frame(scope,1,.5f,pose(179,-90)).orElseThrow();assertEquals(pose(180,0),half.pose());
        assertEquals(pose(181,90),trajectory.beforeTick(scope,1,half.pose()).orElseThrow().pose());
    }
    @Test void replacementAfterAFrameStartsAtTheLastActualPose(){
        trajectory.plan(scope,1,pose(0,0),pose(6,0));
        var half=trajectory.frame(scope,1,.5f,pose(0,0)).orElseThrow();
        trajectory.plan(scope,1,half.pose(),pose(9,0));
        assertEquals(pose(3,0),trajectory.frame(scope,1,.5f,half.pose()).orElseThrow().pose());
        assertEquals(pose(6,0),trajectory.frame(scope,1,.75f,half.pose()).orElseThrow().pose());
    }
    @Test void cancellationStopsWithoutRestoringHistoricalAnglesOrCompleting(){
        long ticket=trajectory.plan(scope,1,pose(0,0),pose(6,0));
        var half=trajectory.frame(scope,1,.5f,pose(0,0)).orElseThrow();trajectory.cancel();
        assertTrue(trajectory.frame(scope,1,.75f,half.pose()).isEmpty());assertTrue(trajectory.beforeTick(scope,1,half.pose()).isEmpty());
        assertFalse(trajectory.completed(scope,ticket));
    }
    @Test void revocationEpochChangesButValidFramesAndEndpointRestoreDoNot(){
        long epoch=trajectory.epoch();trajectory.plan(scope,1,pose(0,0),pose(6,0));
        var half=trajectory.frame(scope,1,.5f,pose(0,0)).orElseThrow();trajectory.beforeTick(scope,1,half.pose()).orElseThrow();
        assertEquals(epoch,trajectory.epoch());trajectory.cancel();assertEquals(epoch+1,trajectory.epoch());
    }
    @Test void allNativeIdentityAndOwnerChangesRevokeOldWork(){
        for(int changed=0;changed<4;changed++){
            trajectory.plan(scope,1,pose(0,0),pose(6,0));
            var foreign=new OwnedRotationTrajectory.Scope(changed==0?new Object():owner,changed==1?new Object():player,changed==2?new Object():world,changed==3?new Object():connection);
            assertTrue(trajectory.frame(foreign,1,.5f,pose(0,0)).isEmpty());assertFalse(trajectory.active());
        }
    }
    @Test void nativeOrUserCorrectionAndDecreasingTimeCannotBeUndone(){
        trajectory.plan(scope,1,pose(0,0),pose(6,0));
        assertTrue(trajectory.frame(scope,1,.5f,pose(-10,0)).isEmpty());
        trajectory.plan(scope,1,pose(0,0),pose(6,0));var half=trajectory.frame(scope,1,.5f,pose(0,0)).orElseThrow();
        assertTrue(trajectory.frame(scope,1,.25f,half.pose()).isEmpty());
    }
    @Test void correctionAfterTickEndpointInvalidatesItsCompletionReceipt(){
        long ticket=trajectory.plan(scope,1,pose(0,0),pose(6,0));trajectory.beforeTick(scope,1,pose(0,0)).orElseThrow();
        trajectory.validate(scope,2,pose(12,-7));assertFalse(trajectory.completed(scope,ticket));assertEquals(1,trajectory.epoch());
    }
    @Test void validationAcceptsAConsumedEndpointUntilGuardedCompletionDispatch(){
        long ticket=trajectory.plan(scope,1,pose(0,0),pose(6,0));trajectory.beforeTick(scope,1,pose(0,0)).orElseThrow();
        trajectory.validate(scope,2,pose(6,0));assertTrue(trajectory.completed(scope,ticket));assertEquals(0,trajectory.epoch());
    }
    @Test void sameTickReplacementCannotCreditSupersededEndpoint(){
        long old=trajectory.plan(scope,1,pose(0,0),pose(6,0));long next=trajectory.plan(scope,1,pose(0,0),pose(2,1));
        trajectory.beforeTick(scope,1,pose(0,0)).orElseThrow();assertFalse(trajectory.completed(scope,old));assertTrue(trajectory.completed(scope,next));
    }
    @Test void realMaintenanceTokenPreservesReturnAndRevokesResumedOrReplacedLease(){
        var lease=new dev.veltraph.client.rewarp.FarmMaintenanceLease();Object farm=new Object(),helper=new Object();
        var token=lease.prepare(farm,helper,0,1000);assertTrue(lease.commit(token));
        var helperScope=new OwnedRotationTrajectory.Scope(token,player,world,connection);
        long ticket=trajectory.plan(helperScope,1,pose(0,0),pose(6,0));
        assertTrue(lease.beginRestore(token));assertTrue(lease.beginReturn(token));
        var half=trajectory.frame(helperScope,1,.5f,pose(0,0)).orElseThrow();assertEquals(pose(3,0),half.pose());
        assertTrue(lease.ready(token));assertTrue(lease.resume(token));
        var resumedScope=new OwnedRotationTrajectory.Scope(farm,player,world,connection);
        assertTrue(trajectory.frame(resumedScope,1,.75f,half.pose()).isEmpty());assertFalse(trajectory.completed(helperScope,ticket));
        var next=lease.prepare(farm,helper,2,1000);assertNotSame(token,next);assertTrue(lease.commit(next));
        var nextScope=new OwnedRotationTrajectory.Scope(next,player,world,connection);
        trajectory.plan(nextScope,2,half.pose(),pose(9,0));assertTrue(trajectory.frame(helperScope,2,.5f,half.pose()).isEmpty());
    }
    @Test void consumedEndpointReceiptSurvivesIdleFramesWithoutReapplyingPose(){
        long ticket=trajectory.plan(scope,10,pose(140,18),pose(141,19));
        var endpoint=trajectory.beforeTick(scope,10,pose(140,18)).orElseThrow().pose();
        long epoch=trajectory.epoch();assertTrue(trajectory.completed(scope,ticket));
        assertTrue(trajectory.frame(scope,11,.25f,endpoint).isEmpty());
        assertTrue(trajectory.frame(scope,11,.75f,endpoint).isEmpty());
        assertTrue(trajectory.completed(scope,ticket),"Idle render revoked a physically consumed endpoint before source callback dispatch");
        assertEquals(epoch,trajectory.epoch());assertFalse(trajectory.active());
    }
    @Test void realRotationRequestCanConsumeItsActualEndpointAfterIdleRender(){
        var callback=new java.util.concurrent.atomic.AtomicInteger();
        var request=new RotationRequest(new SourceRotationStepper());
        request.replace(6,0,callback::incrementAndGet);
        var step=request.advance(6,0,SourceRotationStepper.Settings.standard(.5,1),true,false);
        assertTrue(step.complete());long ticket=trajectory.plan(scope,10,pose(6,0),pose(step.yaw(),step.pitch()));
        var actual=trajectory.beforeTick(scope,10,pose(6,0)).orElseThrow().pose();
        assertTrue(trajectory.frame(scope,11,.5f,actual).isEmpty());
        if(trajectory.completed(scope,ticket))request.applied(step);else request.cancel();
        assertEquals(1,callback.get(),"The real source request lost its actual camera completion receipt between manual source ticks");
        assertFalse(request.pending());request.applied(step);assertEquals(1,callback.get());
    }
    @Test void staleNavigationMustBeValidatedBeforeBindingFreshRequestEpoch(){
        trajectory.plan(scope,10,pose(140,18),pose(146,20));
        var observed=trajectory.frame(scope,10,.5f,pose(140,18)).orElseThrow().pose();
        long capturedTooEarly=trajectory.epoch();
        trajectory.validate(scope,11,observed);
        assertNotEquals(capturedTooEarly,trajectory.epoch(),"Existing capture-before-validate service order rejects its own new request");
        assertFalse(trajectory.active());long freshEpoch=trajectory.epoch();
        long fresh=trajectory.plan(scope,11,observed,pose(-90,0));
        assertEquals(freshEpoch,trajectory.epoch());
        assertEquals(pose(270,0),trajectory.beforeTick(scope,11,observed).orElseThrow().pose());
        assertTrue(trajectory.completed(scope,fresh));
        trajectory.validate(scope,12,pose(270,2));
        assertFalse(trajectory.completed(scope,fresh),"Correction after a fresh submission must still revoke its receipt");
    }
    @Test void consumedReceiptStillRevokesOnOwnerNativeContextOrActualPoseChange(){
        for(int change=0;change<5;change++){
            long ticket=trajectory.plan(scope,10,pose(0,0),pose(6,0));
            var endpoint=trajectory.beforeTick(scope,10,pose(0,0)).orElseThrow().pose();
            var current=new OwnedRotationTrajectory.Scope(change==0?new Object():owner,change==1?new Object():player,
                    change==2?new Object():world,change==3?new Object():connection);
            assertTrue(trajectory.frame(current,11,.5f,change==4?pose(6,2):endpoint).isEmpty());
            assertFalse(trajectory.completed(scope,ticket));assertFalse(trajectory.active());
        }
    }
    @Test void absentAuthorityOrExplicitStopCannotConsumePriorEndpointReceipt(){
        long ticket=trajectory.plan(scope,10,pose(0,0),pose(6,0));
        var endpoint=trajectory.beforeTick(scope,10,pose(0,0)).orElseThrow().pose();
        assertTrue(trajectory.frame(null,11,.5f,endpoint).isEmpty());assertFalse(trajectory.completed(scope,ticket));
        ticket=trajectory.plan(scope,12,endpoint,pose(9,0));trajectory.beforeTick(scope,12,endpoint).orElseThrow();
        trajectory.cancel();assertFalse(trajectory.completed(scope,ticket));assertTrue(trajectory.frame(scope,13,.5f,pose(9,0)).isEmpty());
    }
    @Test void consumedReceiptSurvivesAnotherIdleNativeTickBeforeItsConsumer(){
        long ticket=trajectory.plan(scope,10,pose(0,0),pose(6,0));
        var endpoint=trajectory.beforeTick(scope,10,pose(0,0)).orElseThrow().pose();long epoch=trajectory.epoch();
        assertTrue(trajectory.beforeTick(scope,11,endpoint).isEmpty());
        assertTrue(trajectory.frame(scope,11,.5f,endpoint).isEmpty());
        assertTrue(trajectory.beforeTick(scope,12,endpoint).isEmpty());
        assertTrue(trajectory.completed(scope,ticket),"Idle tick erased an actually consumed endpoint before its owner consumed completion");
        assertEquals(epoch,trajectory.epoch());assertFalse(trajectory.active());
    }
}
