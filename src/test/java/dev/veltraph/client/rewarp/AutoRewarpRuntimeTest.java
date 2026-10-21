package dev.veltraph.client.rewarp;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure state-machine tests for {@link AutoRewarpRuntime} with a fake {@link AutoRewarpRuntime.Actions}
 * recorder and an injected clock. Covers: the leave-area + stuck triggers, the enabled/no-farm gates,
 * the warp→wait→settle→resume happy path, the wait timeout, and the area-unknown false-positive guard.
 */
class AutoRewarpRuntimeTest {

    private static final AutoRewarpRuntime.Settings SETTINGS =
            new AutoRewarpRuntime.Settings(1500L, 10000L, 60000L);

    /** Records side effects so tests can assert "exactly once". */
    private static final class FakeActions implements AutoRewarpRuntime.Actions {
        @Override public boolean requiresFarmReturn() { return false; } // These cases isolate command observation.
        int suspendCalls;
        int sendCalls;
        int resumeCalls;
        int stopCalls;
        boolean sendResult = true;

        @Override public void suspendFarming() {
            suspendCalls++;
        }

        @Override public boolean sendRewarp() {
            sendCalls++;
            return sendResult;
        }

        @Override public void resumeFarming() {
            resumeCalls++;
        }

        @Override public void stopMacro() {
            stopCalls++;
        }
    }

    /** Inputs for a normal "farming in the Garden, nothing wrong" tick. */
    private static AutoRewarpRuntime.Inputs farmingHealthy(long now) {
        return new AutoRewarpRuntime.Inputs(true, true, true, true,
                /*inGarden*/ true, /*areaKnown*/ true, /*onGround*/ true, /*toolReady*/ true,
                /*stuck*/ false, /*stuckSinceMs*/ 0L, now);
    }

    /** Inputs for "farming but left the Garden" (definite non-Garden area). */
    private static AutoRewarpRuntime.Inputs leftGarden(long now) {
        return new AutoRewarpRuntime.Inputs(true, true, true, true,
                false, true, true, true, false, 0L, now);
    }

    @Test
    void triggerFiresOnLeaveAreaWhileFarmingAndEnabled() {
        FakeActions a = new FakeActions();
        AutoRewarpRuntime rt = new AutoRewarpRuntime(a);

        rt.tick(farmingHealthy(0), SETTINGS);
        assertEquals(AutoRewarpState.IDLE, rt.state());
        assertEquals(0, a.suspendCalls);

        rt.tick(leftGarden(100), SETTINGS);
        assertEquals(AutoRewarpState.WARPING, rt.state());
        assertEquals(1, a.suspendCalls);
        assertEquals(1, a.sendCalls);
        assertEquals("left the Garden", rt.lastTrigger());
    }

    @Test
    void doesNotFireWhenAutoRewarpDisabled() {
        FakeActions a = new FakeActions();
        AutoRewarpRuntime rt = new AutoRewarpRuntime(a);
        AutoRewarpRuntime.Inputs in = new AutoRewarpRuntime.Inputs(true, /*enabled*/ false, true, true,
                false, true, true, true, false, 0L, 0L);
        rt.tick(in, SETTINGS);
        assertEquals(AutoRewarpState.IDLE, rt.state());
        assertEquals(0, a.suspendCalls);
        assertEquals(0, a.sendCalls);
    }

    @Test
    void doesNotFireWhenNoFarmRunning() {
        FakeActions a = new FakeActions();
        AutoRewarpRuntime rt = new AutoRewarpRuntime(a);
        AutoRewarpRuntime.Inputs in = new AutoRewarpRuntime.Inputs(/*farmRunning*/ false, true, true, true,
                false, true, true, true, false, 0L, 0L);
        rt.tick(in, SETTINGS);
        assertEquals(AutoRewarpState.IDLE, rt.state());
        assertEquals(0, a.suspendCalls);
        assertEquals(0, a.sendCalls);
    }

    @Test
    void doesNotFireOnLeaveAreaWhenThatTriggerDisabled() {
        FakeActions a = new FakeActions();
        AutoRewarpRuntime rt = new AutoRewarpRuntime(a);
        AutoRewarpRuntime.Inputs in = new AutoRewarpRuntime.Inputs(true, true,
                /*onLeaveArea*/ false, /*onStuck*/ true,
                false, true, true, true, false, 0L, 0L);
        rt.tick(in, SETTINGS);
        assertEquals(AutoRewarpState.IDLE, rt.state());
        assertEquals(0, a.sendCalls);
    }

    @Test
    void requestsReleaseAndWarpExactlyOnceOnTrigger() {
        FakeActions a = new FakeActions();
        AutoRewarpRuntime rt = new AutoRewarpRuntime(a);
        rt.tick(leftGarden(0), SETTINGS);
        // Re-ticking while already WARPING must not re-suspend / re-send.
        rt.tick(leftGarden(50), SETTINGS);
        assertEquals(1, a.suspendCalls);
        assertEquals(1, a.sendCalls);
        assertEquals(AutoRewarpState.WAITING_GARDEN, rt.state());
    }

    @Test
    void happyPathWarpWaitSettleResume() {
        FakeActions a = new FakeActions();
        AutoRewarpRuntime rt = new AutoRewarpRuntime(a);

        // Trigger -> WARPING.
        rt.tick(leftGarden(0), SETTINGS);
        assertEquals(AutoRewarpState.WARPING, rt.state());

        // WARPING -> WAITING_GARDEN.
        rt.tick(leftGarden(20), SETTINGS);
        assertEquals(AutoRewarpState.WAITING_GARDEN, rt.state());

        // Still not in the Garden -> stays waiting.
        rt.tick(leftGarden(500), SETTINGS);
        assertEquals(AutoRewarpState.WAITING_GARDEN, rt.state());

        // Back in the Garden -> SETTLING.
        rt.tick(farmingHealthy(1000), SETTINGS);
        assertEquals(AutoRewarpState.SETTLING, rt.state());

        // Wait not yet elapsed (settle window 1500ms from t=1000) -> stays SETTLING.
        rt.tick(farmingHealthy(2000), SETTINGS);
        assertEquals(AutoRewarpState.SETTLING, rt.state());

        // Wait elapsed + grounded + tool ok -> RESUME.
        rt.tick(farmingHealthy(2600), SETTINGS);
        assertEquals(AutoRewarpState.RESUME, rt.state());
        assertEquals(0, a.resumeCalls);

        // RESUME -> resume callback fires, back to IDLE.
        rt.tick(farmingHealthy(2620), SETTINGS);
        assertEquals(AutoRewarpState.IDLE, rt.state());
        assertEquals(1, a.resumeCalls);
        assertEquals(0, a.stopCalls);
    }

    @Test
    void settlingWaitsForGroundAndTool() {
        FakeActions a = new FakeActions();
        AutoRewarpRuntime rt = new AutoRewarpRuntime(a);
        rt.tick(leftGarden(0), SETTINGS);     // WARPING
        rt.tick(leftGarden(10), SETTINGS);    // WAITING_GARDEN
        rt.tick(farmingHealthy(100), SETTINGS); // SETTLING (phaseStart=100)

        // Wait elapsed but airborne -> hold in SETTLING.
        AutoRewarpRuntime.Inputs airborne = new AutoRewarpRuntime.Inputs(true, true, true, true,
                true, true, /*onGround*/ false, /*toolReady*/ true, false, 0L, 2000L);
        rt.tick(airborne, SETTINGS);
        assertEquals(AutoRewarpState.SETTLING, rt.state());

        // Grounded but tool not ready -> still hold.
        AutoRewarpRuntime.Inputs noTool = new AutoRewarpRuntime.Inputs(true, true, true, true,
                true, true, /*onGround*/ true, /*toolReady*/ false, false, 0L, 2100L);
        rt.tick(noTool, SETTINGS);
        assertEquals(AutoRewarpState.SETTLING, rt.state());

        // Continuous healthy window starts again after ground/tool interruptions.
        rt.tick(farmingHealthy(2200), SETTINGS);
        assertEquals(AutoRewarpState.SETTLING, rt.state());
        rt.tick(farmingHealthy(3700), SETTINGS);
        assertEquals(AutoRewarpState.RESUME, rt.state());
    }

    @Test
    void waitTimeoutStopsMacroAndResets() {
        FakeActions a = new FakeActions();
        AutoRewarpRuntime rt = new AutoRewarpRuntime(a);
        rt.tick(leftGarden(0), SETTINGS);   // WARPING
        rt.tick(leftGarden(10), SETTINGS);  // WAITING_GARDEN (phaseStart=10)

        // Just under the 10000ms cap -> still waiting.
        rt.tick(leftGarden(9000), SETTINGS);
        assertEquals(AutoRewarpState.WAITING_GARDEN, rt.state());

        // Past the cap, no Garden -> timeout: stop the macro, back to IDLE.
        rt.tick(leftGarden(10011), SETTINGS);
        assertEquals(AutoRewarpState.IDLE, rt.state());
        assertEquals(1, a.stopCalls);
        assertEquals(0, a.resumeCalls);
    }

    @Test
    void stuckTriggerFiresAfterConfiguredWindow() {
        FakeActions a = new FakeActions();
        AutoRewarpRuntime rt = new AutoRewarpRuntime(a);

        // Stuck since t=1000; window is 60000ms. At t=30000 not yet (29000 < 60000).
        AutoRewarpRuntime.Inputs stuckEarly = new AutoRewarpRuntime.Inputs(true, true, true, true,
                /*inGarden*/ true, true, true, true, /*stuck*/ true, /*stuckSince*/ 1000L, 30000L);
        rt.tick(stuckEarly, SETTINGS);
        assertEquals(AutoRewarpState.IDLE, rt.state());
        assertEquals(0, a.sendCalls);

        // At t=61000, 60000ms elapsed -> fires.
        AutoRewarpRuntime.Inputs stuckLate = new AutoRewarpRuntime.Inputs(true, true, true, true,
                true, true, true, true, true, 1000L, 61000L);
        rt.tick(stuckLate, SETTINGS);
        assertEquals(AutoRewarpState.WARPING, rt.state());
        assertEquals("stuck (no progress)", rt.lastTrigger());
        assertEquals(1, a.sendCalls);
    }

    @Test
    void areaUnknownBlipDoesNotTriggerLeaveArea() {
        FakeActions a = new FakeActions();
        AutoRewarpRuntime rt = new AutoRewarpRuntime(a);
        // Not in Garden but area is NOT known (a blip): must not fire.
        AutoRewarpRuntime.Inputs blip = new AutoRewarpRuntime.Inputs(true, true, true, true,
                /*inGarden*/ false, /*areaKnown*/ false, true, true, false, 0L, 0L);
        rt.tick(blip, SETTINGS);
        assertEquals(AutoRewarpState.IDLE, rt.state());
        assertEquals(0, a.sendCalls);
    }

    @Test
    void areaUnknownBlipDuringWaitDoesNotDoubleTrigger() {
        FakeActions a = new FakeActions();
        AutoRewarpRuntime rt = new AutoRewarpRuntime(a);
        rt.tick(leftGarden(0), SETTINGS);   // WARPING
        rt.tick(leftGarden(10), SETTINGS);  // WAITING_GARDEN

        // An area-unknown blip while waiting: still exactly one warp/suspend; stays waiting (no Garden).
        AutoRewarpRuntime.Inputs blip = new AutoRewarpRuntime.Inputs(true, true, true, true,
                false, /*areaKnown*/ false, true, true, false, 0L, 200L);
        rt.tick(blip, SETTINGS);
        assertEquals(AutoRewarpState.WAITING_GARDEN, rt.state());
        assertEquals(1, a.suspendCalls);
        assertEquals(1, a.sendCalls);
    }

    @Test
    void farmStoppedMidFlightResetsToIdle() {
        FakeActions a = new FakeActions();
        AutoRewarpRuntime rt = new AutoRewarpRuntime(a);
        rt.tick(leftGarden(0), SETTINGS);  // WARPING

        AutoRewarpRuntime.Inputs farmStopped = new AutoRewarpRuntime.Inputs(/*farmRunning*/ false, true, true, true,
                false, true, true, true, false, 0L, 50L);
        rt.tick(farmStopped, SETTINGS);
        assertEquals(AutoRewarpState.IDLE, rt.state());
        // No resume/stop fired: the module's own disable already released inputs.
        assertEquals(0, a.resumeCalls);
        assertEquals(0, a.stopCalls);
    }

    @Test
    void statusLineReflectsEnabledAndState() {
        AutoRewarpRuntime rt = new AutoRewarpRuntime(new FakeActions());
        String off = rt.statusLine(false);
        assertTrue(off.contains("OFF"));
        assertTrue(off.contains("IDLE"));
        rt.tick(leftGarden(0), SETTINGS);
        assertTrue(rt.statusLine(true).contains("ON"));
        assertTrue(rt.statusLine(true).contains("WARPING"));
    }

    @Test
    void sendFailureStopsImmediatelyWithoutWaitingOrResume() {
        FakeActions a = new FakeActions(); a.sendResult = false;
        AutoRewarpRuntime rt = new AutoRewarpRuntime(a);
        rt.tick(leftGarden(0), SETTINGS);
        assertEquals(AutoRewarpState.IDLE, rt.state());
        assertEquals(1, a.stopCalls); assertEquals(0, a.resumeCalls);
    }

    private static AutoRewarpRuntime.Inputs observed(long now, double x, boolean garden, boolean known,
                                                     boolean valid, double speed, Object world) {
        return new AutoRewarpRuntime.Inputs(true, true, true, true, garden, known, true, true,
                true, 0, now, new AutoRewarpRuntime.Observation(valid, world, "minecraft:overworld",
                new dev.veltraph.client.world.Vec3(x, 70, 0), speed));
    }
    private static final AutoRewarpRuntime.Settings SHORT = new AutoRewarpRuntime.Settings(100, 1000, 0);

    @Test void sameGardenSentCommandCannotResumeWithoutObservedArrival() {
        var a = new FakeActions(); var rt = new AutoRewarpRuntime(a); var world = new Object();
        rt.tick(observed(0, 0, true, true, true, 0, world), SHORT);
        for (int i = 1; i < 10; i++) rt.tick(observed(i*100, i*.3, true, true, true, 0, world), SHORT);
        assertEquals(AutoRewarpState.WAITING_GARDEN, rt.state()); assertEquals(0, a.resumeCalls);
        rt.tick(observed(1000, 3, true, true, true, 0, world), SHORT);
        assertEquals(AutoRewarpState.IDLE, rt.state()); assertEquals(1, a.stopCalls);
    }
    @Test void observedSameWorldTeleportSettlesThenResumesExactlyOnce() {
        var a = new FakeActions(); var rt = new AutoRewarpRuntime(a); var world = new Object();
        rt.tick(observed(0, 0, true, true, true, 0, world), SHORT);
        rt.tick(observed(50, 8, true, true, true, 0, world), SHORT);
        rt.tick(observed(100, 8, true, true, true, 0, world), SHORT);
        rt.tick(observed(200, 8, true, true, true, 0, world), SHORT);
        assertEquals(AutoRewarpState.RESUME, rt.state()); assertEquals(0, a.resumeCalls);
        rt.tick(observed(250, 8, true, true, true, 0, world), SHORT);
        assertEquals(AutoRewarpState.IDLE, rt.state()); assertEquals(1, a.resumeCalls);
        assertEquals(1, a.sendCalls); assertEquals(0, a.stopCalls);
    }
    @Test void unknownAreaCannotFinishSettlingOrResume() {
        var a = new FakeActions(); var rt = new AutoRewarpRuntime(a);
        rt.tick(leftGarden(0), SHORT); rt.tick(leftGarden(10), SHORT); rt.tick(farmingHealthy(50), SHORT);
        var unknown = new AutoRewarpRuntime.Inputs(true,true,true,true,true,false,true,true,false,0,200);
        rt.tick(unknown, SHORT); assertEquals(AutoRewarpState.WAITING_GARDEN,rt.state());
        assertEquals(0,a.resumeCalls);
    }
    @Test void deadlineCoversSettlingAndRepeatedAreaBounces() {
        var a = new FakeActions(); var rt = new AutoRewarpRuntime(a);
        rt.tick(leftGarden(0), SHORT); rt.tick(leftGarden(10), SHORT); rt.tick(farmingHealthy(50), SHORT);
        rt.tick(leftGarden(500), SHORT); rt.tick(farmingHealthy(900), SHORT);
        rt.tick(farmingHealthy(1000), SHORT);
        assertEquals(AutoRewarpState.IDLE,rt.state()); assertEquals(1,a.stopCalls); assertEquals(0,a.resumeCalls);
    }
    @Test void finalResumeTickRechecksAreaAndMotion() {
        for (boolean areaLost : new boolean[]{true,false}) {
            var a = new FakeActions(); var rt = new AutoRewarpRuntime(a); var world=new Object();
            rt.tick(observed(0,0,true,true,true,0,world),SHORT);
            rt.tick(observed(10,8,true,true,true,0,world),SHORT);
            rt.tick(observed(50,8,true,true,true,0,world),SHORT);
            rt.tick(observed(150,8,true,true,true,0,world),SHORT);
            assertEquals(AutoRewarpState.RESUME,rt.state());
            rt.tick(observed(200,8,!areaLost,true,true,areaLost?0:.2,world),SHORT);
            assertEquals(0,a.resumeCalls); assertTrue(rt.state()!=AutoRewarpState.RESUME);
        }
    }
    @Test void positionDriftRestartsContinuousSettleWindow() {
        var a=new FakeActions();var rt=new AutoRewarpRuntime(a);var world=new Object();
        rt.tick(observed(0,0,true,true,true,0,world),SHORT);
        rt.tick(observed(10,8,true,true,true,0,world),SHORT);
        rt.tick(observed(50,8,true,true,true,0,world),SHORT);
        rt.tick(observed(150,8.3,true,true,true,0,world),SHORT);
        assertEquals(AutoRewarpState.SETTLING,rt.state());
        rt.tick(observed(250,8.3,true,true,true,0,world),SHORT);
        assertEquals(AutoRewarpState.RESUME,rt.state());
    }
    @Test void invalidContextOrConfigurationCancelsWhileWaiting() {
        for(boolean invalid:new boolean[]{true,false}) {
            var a=new FakeActions();var rt=new AutoRewarpRuntime(a);var world=new Object();
            rt.tick(observed(0,0,true,true,true,0,world),SHORT);
            var next=observed(50,0,true,true,!invalid,0,world);
            if(!invalid)next=new AutoRewarpRuntime.Inputs(true,false,true,true,true,true,true,true,false,0,50,next.observation());
            rt.tick(next,SHORT);assertEquals(AutoRewarpState.IDLE,rt.state());assertEquals(1,a.stopCalls);
        }
    }
    @Test void actionFailureStopsWithoutResuming() {
        int[] stop={0};
        var rt=new AutoRewarpRuntime(new AutoRewarpRuntime.Actions(){
            public void suspendFarming(){throw new IllegalStateException("sink failed");}
            public boolean sendRewarp(){throw new AssertionError("Must not send after release failed");}
            public void resumeFarming(){throw new AssertionError("Must not resume");}
            public void stopMacro(){stop[0]++;}
        });
        rt.tick(leftGarden(0),SHORT);assertEquals(AutoRewarpState.IDLE,rt.state());assertEquals(1,stop[0]);
    }
    @Test void backwardsClockCannotExtendRecoveryDeadline() {
        var a=new FakeActions();var rt=new AutoRewarpRuntime(a);
        rt.tick(leftGarden(200),SHORT);rt.tick(leftGarden(100),SHORT);
        assertEquals(AutoRewarpState.IDLE,rt.state());assertEquals(1,a.stopCalls);
    }
    @Test void unknownAreaDoesNotSendStuckRecoveryCommand() {
        var a=new FakeActions();var rt=new AutoRewarpRuntime(a);var world=new Object();
        rt.tick(observed(0,0,true,false,true,0,world),SHORT);
        assertEquals(AutoRewarpState.IDLE,rt.state());assertEquals(0,a.sendCalls);
    }
    @Test void externalCancellationClearsObservedWorldAndHasNoCallbacks() {
        var a=new FakeActions();var rt=new AutoRewarpRuntime(a);var world=new Object();
        rt.tick(observed(0,0,true,true,true,0,world),SHORT);rt.cancel();rt.cancel();
        assertEquals(AutoRewarpState.IDLE,rt.state());assertEquals(0,a.stopCalls);assertEquals(0,a.resumeCalls);
        assertTrue(rt.statusLine(true).contains("arrivalObserved=false"));
    }

    private static class ReturnActions implements AutoRewarpRuntime.Actions {
        int starts,resumes,stops,cancels;boolean start=true;
        AutoRewarpRuntime.Return result=AutoRewarpRuntime.Return.ACTIVE;
        public void suspendFarming(){} public boolean sendRewarp(){return true;}
        public void resumeFarming(){resumes++;} public void stopMacro(){stops++;}
        public boolean startFarmReturn(){starts++;return start;}
        public AutoRewarpRuntime.Return tickFarmReturn(){return result;}
        public void cancelFarmReturn(){cancels++;}
    }
    private static AutoRewarpRuntime returning(ReturnActions a) {
        var rt=new AutoRewarpRuntime(a);rt.tick(leftGarden(0),SHORT);rt.tick(leftGarden(10),SHORT);
        rt.tick(farmingHealthy(50),SHORT);rt.tick(farmingHealthy(150),SHORT);rt.tick(farmingHealthy(200),SHORT);
        return rt;
    }
    @Test void commandArrivalCannotResumeBeforeFarmReturnCompletes() {
        var a=new ReturnActions();var rt=returning(a);
        assertEquals(AutoRewarpState.RETURNING,rt.state());assertEquals(1,a.starts);assertEquals(0,a.resumes);
        rt.tick(farmingHealthy(250),SHORT);assertEquals(AutoRewarpState.RETURNING,rt.state());
        a.result=AutoRewarpRuntime.Return.ARRIVED;rt.tick(farmingHealthy(300),SHORT);
        assertEquals(AutoRewarpState.RESUME,rt.state());assertEquals(0,a.resumes);
        rt.tick(farmingHealthy(350),SHORT);assertEquals(1,a.resumes);assertEquals(1,a.cancels);
    }
    @Test void noOriginalPoseOrFailedReturnStopsRatherThanUsingManualMarker() {
        for(boolean unavailable:new boolean[]{true,false}){
            var a=new ReturnActions();a.start=!unavailable;var rt=returning(a);
            if(!unavailable){a.result=AutoRewarpRuntime.Return.FAILED;rt.tick(farmingHealthy(250),SHORT);}
            assertEquals(AutoRewarpState.IDLE,rt.state());assertEquals(1,a.stops);assertEquals(0,a.resumes);
        }
    }
    @Test void poseChangedBetweenArrivalAndCallbackCannotResume() {
        var a=new ReturnActions();var rt=returning(a);a.result=AutoRewarpRuntime.Return.ARRIVED;
        rt.tick(farmingHealthy(250),SHORT);a.result=AutoRewarpRuntime.Return.FAILED;
        rt.tick(farmingHealthy(300),SHORT);assertEquals(1,a.stops);assertEquals(0,a.resumes);
    }
    @Test void cancellationDuringTravelNeverRestartsTheFarm() {
        var a=new ReturnActions();var rt=returning(a);rt.cancel();
        assertEquals(AutoRewarpState.IDLE,rt.state());assertEquals(1,a.cancels);
        assertEquals(0,a.resumes);assertEquals(0,a.stops);
    }
    @Test void originalReturnSharesTheCommandRecoveryDeadline() {
        var a=new ReturnActions();var rt=returning(a);rt.tick(farmingHealthy(1000),SHORT);
        assertEquals(AutoRewarpState.IDLE,rt.state());assertEquals(1,a.stops);assertEquals(1,a.cancels);
    }
    @Test void observedCommandLandingOnlyAcknowledgesOneSameWorldFailsafeSample(){
        var runtime=new AutoRewarpRuntime(new FakeActions());var world=new Object();
        runtime.tick(observed(0,0,false,true,true,0,world),SHORT);
        assertFalse(runtime.consumeExpectedTeleport(world,new dev.veltraph.client.world.Vec3(8,70,0),10));
        runtime.tick(observed(20,8,true,true,true,0,world),SHORT);
        assertFalse(runtime.consumeExpectedTeleport(new Object(),new dev.veltraph.client.world.Vec3(8,70,0),21));
        assertFalse(runtime.consumeExpectedTeleport(world,new dev.veltraph.client.world.Vec3(40,70,0),21));
        assertTrue(runtime.consumeExpectedTeleport(world,new dev.veltraph.client.world.Vec3(8,70,0),21));
        assertFalse(runtime.consumeExpectedTeleport(world,new dev.veltraph.client.world.Vec3(8,70,0),21));
        runtime.cancel();assertFalse(runtime.consumeExpectedTeleport(world,new dev.veltraph.client.world.Vec3(8,70,0),22));
    }

}
