package dev.veltraph.client.rewarp;

import dev.veltraph.client.game.GameArea;
import dev.veltraph.client.world.Vec3;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class FarmCycleResetRuntimeTest {
    private static final String PROFILE="pumpkin_melon_saigo_current",DIM="minecraft:overworld";
    private static final Vec3 START=new Vec3(.5,70,1.7),END=new Vec3(2.5,70,79.7);
    private final Object owner=new Object(),world=new Object();
    private final FarmCyclePlan plan=new FarmCyclePlan(PROFILE,new FarmReturnPoint(DIM,GameArea.GARDEN,START,0,25.8f),List.of(END));
    private final FarmCycleResetRuntime.Settings settings=new FarmCycleResetRuntime.Settings(1,100,50,1000,.35,1,.1);
    private final Actions actions=new Actions();
    private final FarmCycleResetRuntime runtime=new FarmCycleResetRuntime(actions);
    private static final class Actions implements FarmCycleResetRuntime.Actions {
        int suspended,sent,aimed,restarted,stopped,cancelled;
        boolean suspend=true,send=true,aim=true,restart=true,throwSend;
        Object stoppedOwner;
        public boolean suspend(Object owner){suspended++;return suspend;}
        public boolean sendRewarp(Object owner){sent++;if(throwSend)throw new IllegalStateException("fixture transport failed");return send;}
        public boolean aimAtStart(Object owner,FarmReturnPoint start){aimed++;return aim;}
        public boolean restartAfterCycleReset(Object owner){restarted++;return restart;}
        public void stop(Object owner,String reason){stopped++;stoppedOwner=owner;}
        public void cancelAim(Object owner){cancelled++;}
    }
    private FarmCycleResetRuntime.Observation observation(Vec3 position,float yaw,float pitch,boolean grounded,double speed) {
        return new FarmCycleResetRuntime.Observation(true,world,GameArea.GARDEN,DIM,position,speed,grounded,yaw,pitch);
    }
    private FarmCycleResetRuntime.Inputs input(long ms,long tick,Vec3 position) {
        return new FarmCycleResetRuntime.Inputs(true,owner,PROFILE,plan,observation(position,0,25.8f,true,0),true,tick,ms);
    }
    private void tick(long ms,Vec3 position){runtime.tick(input(ms,ms,position),settings);}
    private void request(){runtime.tick(input(0,0,END),settings);tick(100,END);}
    @Test void sourceMarkerCadenceIsThreeTicksAndRadiusIsStrict() {
        runtime.tick(input(0,1,END),settings);assertEquals(FarmCycleResetRuntime.State.IDLE,runtime.state());
        runtime.tick(input(0,3,new Vec3(END.x()+1,END.y(),END.z())),settings);
        assertEquals(FarmCycleResetRuntime.State.IDLE,runtime.state());
        runtime.tick(input(0,6,END),settings);assertEquals(FarmCycleResetRuntime.State.REQUEST_DELAY,runtime.state());
        tick(99,END);assertEquals(0,actions.sent);
        tick(100,END);assertEquals(1,actions.sent);
    }
    @Test void dispatchDoesNotEstablishArrivalAndMissingAcknowledgmentStopsWithoutRetry() {
        request();tick(200,END);tick(500,END);
        assertEquals(FarmCycleResetRuntime.State.WAITING_ARRIVAL,runtime.state());assertEquals(0,actions.restarted);
        tick(1000,END);assertEquals(FarmCycleResetRuntime.State.IDLE,runtime.state());
        assertEquals(1,actions.sent);assertEquals(1,actions.stopped);assertEquals(0,runtime.completedCycles());
    }
    @Test void actualStartSettlementAndMeasuredAnglesPrecedeOneRestart() {
        request();
        runtime.tick(new FarmCycleResetRuntime.Inputs(true,owner,PROFILE,plan,observation(START,90,0,true,0),true,120,120),settings);
        assertEquals(FarmCycleResetRuntime.State.SETTLING,runtime.state());
        runtime.tick(new FarmCycleResetRuntime.Inputs(true,owner,PROFILE,plan,observation(START,90,0,true,0),true,171,171),settings);
        runtime.tick(new FarmCycleResetRuntime.Inputs(true,owner,PROFILE,plan,observation(START,90,0,true,0),true,172,172),settings);
        assertEquals(FarmCycleResetRuntime.State.ALIGNING,runtime.state());assertEquals(1,actions.aimed);assertEquals(0,actions.restarted);
        tick(190,START);assertEquals(FarmCycleResetRuntime.State.RESTARTING,runtime.state());
        tick(191,START);assertEquals(1,actions.restarted);assertEquals(1,runtime.completedCycles());
        assertEquals(FarmCycleResetRuntime.State.IDLE,runtime.state());tick(195,START);assertEquals(1,actions.sent);
    }
    @Test void wrongSpawnDoesNotTravelBackToTheFarmEndOrResume() {
        request();tick(120,new Vec3(40,70,40));
        assertEquals(1,actions.stopped);assertEquals(0,actions.restarted);assertEquals(0,actions.aimed);
        assertTrue(runtime.lastReason().contains("explicit START"));
    }
    @Test void actualGroundAndToolReadinessAreRequiredAtStart() {
        request();
        runtime.tick(new FarmCycleResetRuntime.Inputs(true,owner,PROFILE,plan,observation(START,0,25.8f,false,0),true,120,120),settings);
        runtime.tick(new FarmCycleResetRuntime.Inputs(true,owner,PROFILE,plan,observation(START,0,25.8f,true,0),false,180,180),settings);
        assertEquals(FarmCycleResetRuntime.State.SETTLING,runtime.state());assertEquals(0,actions.restarted);
        tick(200,START);tick(251,START);tick(252,START);tick(253,START);
        assertEquals(1,actions.restarted);
    }
    @Test void highSpeedResetsTheSettleEvidence() {
        request();tick(120,START);
        runtime.tick(new FarmCycleResetRuntime.Inputs(true,owner,PROFILE,plan,observation(START,0,25.8f,true,.2),true,180,180),settings);
        tick(200,START);tick(240,START);assertEquals(FarmCycleResetRuntime.State.SETTLING,runtime.state());
        tick(251,START);tick(252,START);tick(253,START);assertEquals(1,actions.restarted);
    }
    @Test void ownerReplacementStopsTheCapturedOwnerOnly() {
        request();
        runtime.tick(new FarmCycleResetRuntime.Inputs(true,new Object(),PROFILE,plan,observation(START,0,25.8f,true,0),true,120,120),settings);
        assertEquals(1,actions.stopped);assertSame(owner,actions.stoppedOwner);assertEquals(0,actions.restarted);
    }
    @Test void profileAndExplicitPlanChangesCannotUseOldMarkers() {
        request();
        var changed=new FarmCyclePlan(PROFILE,new FarmReturnPoint(DIM,GameArea.GARDEN,new Vec3(5,70,5),0,25.8f),List.of(END));
        runtime.tick(new FarmCycleResetRuntime.Inputs(true,owner,PROFILE,changed,observation(START,0,25.8f,true,0),true,120,120),settings);
        assertEquals(1,actions.stopped);assertEquals(0,actions.restarted);
    }
    @Test void backwardClockCannotExtendTheTotalDeadline() {
        request();tick(99,END);assertEquals(1,actions.stopped);assertEquals(FarmCycleResetRuntime.State.IDLE,runtime.state());
    }
    @Test void manualWorldStopCancelsPendingAimWithoutRestarting() {
        request();tick(120,START);runtime.cancel();tick(200,START);
        assertEquals(FarmCycleResetRuntime.State.IDLE,runtime.state());assertEquals(0,actions.restarted);assertEquals(1,actions.cancelled);
    }
    @Test void transportFailureAndRefusalFailClosed() {
        actions.throwSend=true;request();assertEquals(1,actions.stopped);assertEquals(0,actions.restarted);
    }
    @Test void startPoseDriftBeforeRestartStopsTheCycle() {
        request();tick(120,START);tick(171,START);tick(172,START);
        tick(173,new Vec3(2,70,1.7));assertEquals(FarmCycleResetRuntime.State.SETTLING,runtime.state());
        tick(174,new Vec3(2,70,1.7));assertEquals(1,actions.stopped);assertEquals(0,actions.restarted);
    }
    @Test void missingWorldAndPreDispatchWorldReplacementCannotSendOrResume() {
        runtime.tick(input(0,0,END),settings);
        var replacement=new FarmCycleResetRuntime.Observation(true,new Object(),GameArea.GARDEN,DIM,END,0,true,0,25.8f);
        runtime.tick(new FarmCycleResetRuntime.Inputs(true,owner,PROFILE,plan,replacement,true,100,100),settings);
        assertEquals(0,actions.sent);assertEquals(1,actions.stopped);
        var absent=new FarmCycleResetRuntime.Observation(true,null,GameArea.GARDEN,DIM,END,0,true,0,25.8f);
        assertFalse(absent.valid());
    }
    @Test void enabledOwnerDisappearanceCannotLeaveAnAbandonedSuspension() {
        request();
        runtime.tick(new FarmCycleResetRuntime.Inputs(false,null,null,null,observation(END,0,25.8f,true,0),true,120,120),settings);
        assertEquals(1,actions.stopped);assertSame(owner,actions.stoppedOwner);
        assertEquals(1,actions.cancelled);assertFalse(runtime.active());
    }
    @Test void worldReplacementDuringSettleCannotInheritStartEvidence() {
        request();tick(120,START);
        var replacement=new FarmCycleResetRuntime.Observation(true,new Object(),GameArea.GARDEN,DIM,START,0,true,0,25.8f);
        runtime.tick(new FarmCycleResetRuntime.Inputs(true,owner,PROFILE,plan,replacement,true,180,180),settings);
        assertEquals(1,actions.stopped);assertEquals(0,actions.restarted);assertFalse(runtime.active());
    }
    @Test void warpWorldReplacementCanEstablishArrivalButRequiresItsOwnSettle() {
        request();Object arrivalWorld=new Object();
        for(long now:new long[]{120,171,172,173}){
            var arrival=new FarmCycleResetRuntime.Observation(true,arrivalWorld,GameArea.GARDEN,DIM,START,0,true,0,25.8f);
            runtime.tick(new FarmCycleResetRuntime.Inputs(true,owner,PROFILE,plan,arrival,true,now,now),settings);
        }
        assertEquals(1,actions.restarted);assertEquals(1,runtime.completedCycles());
    }
    @Test void exactlyTwoBlockStepDoesNotProveWarpArrival() {
        request();tick(120,new Vec3(END.x()+2,END.y(),END.z()));
        assertEquals(FarmCycleResetRuntime.State.WAITING_ARRIVAL,runtime.state());assertEquals(0,actions.stopped);
    }
    @Test void lostToolAtFinalRestartMustSettleAgainAndCanNeverRestartWithoutTool() {
        request();tick(120,START);tick(171,START);tick(172,START);
        runtime.tick(new FarmCycleResetRuntime.Inputs(true,owner,PROFILE,plan,observation(START,0,25.8f,true,0),false,173,173),settings);
        assertEquals(FarmCycleResetRuntime.State.SETTLING,runtime.state());assertEquals(0,actions.restarted);
        runtime.tick(new FarmCycleResetRuntime.Inputs(true,owner,PROFILE,plan,observation(START,0,25.8f,true,0),false,1000,1000),settings);
        assertEquals(1,actions.stopped);assertEquals(0,actions.restarted);
    }
    @Test void onlyOneObservedStartLandingCanAcknowledgeTheLaterFailsafeTick(){
        request();tick(120,START);
        assertFalse(runtime.consumeExpectedTeleport(owner,world,new Vec3(50,70,50),121));
        assertFalse(runtime.consumeExpectedTeleport(owner,world,new Vec3(Double.NaN,70,50),121));
        assertFalse(runtime.consumeExpectedTeleport(new Object(),world,START,121));
        assertFalse(runtime.consumeExpectedTeleport(owner,new Object(),START,121));
        assertTrue(runtime.consumeExpectedTeleport(owner,world,START,121));
        assertFalse(runtime.consumeExpectedTeleport(owner,world,START,121));
        runtime.cancel();assertFalse(runtime.consumeExpectedTeleport(owner,world,START,122));
    }
    @Test void unobservedAndExpiredLandingCannotMaskAnotherTeleport(){
        request();assertFalse(runtime.consumeExpectedTeleport(owner,world,START,110));
        tick(120,START);assertFalse(runtime.consumeExpectedTeleport(owner,world,START,620));
    }
}
