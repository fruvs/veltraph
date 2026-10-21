package dev.veltraph.client.dev;

import dev.veltraph.client.hydra.HydraEvent;

import java.util.ArrayList;
import java.util.List;

/** Recording {@link DevTestContext} test double — captures every local effect so action logic is verifiable. */
final class RecordingDevTestContext implements DevTestContext {

    int stopAllCalls;
    int releaseInputCalls;
    int runningModules = 2;          // pretend two macros are running
    boolean connected = true;        // server reachability for server-bound sends
    boolean parity = false;
    final List<String> localChats = new ArrayList<>();
    final List<String> serverChats = new ArrayList<>();
    final List<String> serverCommands = new ArrayList<>();
    final List<HydraEvent.Type> alerts = new ArrayList<>();
    final List<HydraEvent.Type> classifies = new ArrayList<>();
    final List<dev.veltraph.client.hydra.response.HydraReaction> reactions = new ArrayList<>();
    Double fixYaw;
    Double fixPitch;
    double curPitch = 30.0;   // a deterministic non-edge current pitch for fix-angle target tests
    double curYaw = 100.0;

    @Override public String areaName() { return "GARDEN"; }

    @Override public String contextSummary() { return "Hypixel=true SkyBlock=true area=Garden"; }

    @Override public String moduleStateSummary() {
        return runningModules == 0 ? "no macro running" : "running: m1, m2";
    }

    @Override public int stopAllMacros() {
        stopAllCalls++;
        int n = runningModules;
        runningModules = 0;
        return n;
    }

    @Override public void releaseInput() { releaseInputCalls++; }

    @Override public String gateDecision() { return "would allow farming-wart (context: Garden)"; }

    @Override public String simulateHydraAlert(HydraEvent.Type type) {
        alerts.add(type);
        return type + "/DANGEROUS alert [chat] — test";
    }

    @Override public String classifyHydra(HydraEvent.Type type) {
        classifies.add(type);
        return type + " forceReactRare=true thresholdRare=true response-eligible=false (parity-check)";
    }

    @Override public void fixAngle(double yaw, double pitch) {
        this.fixYaw = yaw;
        this.fixPitch = pitch;
    }

    @Override public double currentPitch() { return curPitch; }

    @Override public double currentYaw() { return curYaw; }

    @Override public double fixAngleMinPitch() { return -38.0; }

    @Override public double fixAngleMaxPitch() { return 38.0; }

    @Override public String runReaction(dev.veltraph.client.hydra.response.HydraReaction reaction) {
        reactions.add(reaction);
        return reaction + " reaction queued (test)";
    }

    final List<dev.veltraph.client.failsafe.response.FailsafeResponseType> failsafeResponses = new ArrayList<>();
    final List<dev.veltraph.client.failsafe.response.FailsafeRoutine> failsafeRoutines = new ArrayList<>();

    @Override public String runFailsafeResponse(dev.veltraph.client.failsafe.response.FailsafeResponseType type) {
        failsafeResponses.add(type);
        return type + " failsafe response queued (test)";
    }

    @Override public String runFailsafeRoutine(dev.veltraph.client.failsafe.response.FailsafeRoutine routine) {
        failsafeRoutines.add(routine);
        return routine + " failsafe routine queued (test)";
    }

    @Override public boolean parityMode() { return parity; }

    @Override public String firstReplyTemplate() { return "LOL"; }

    @Override public String afterDisconnectCommand() { return "lobby"; }

    @Override public String avoidEvacuationCommand() { return "lobby"; }

    @Override public String rewarpCommand() { return "warp garden"; }

    @Override public void localChat(String message) { localChats.add(message); }

    @Override public boolean serverChat(String message) {
        if (connected) {
            serverChats.add(message);
        }
        return connected;
    }

    @Override public boolean serverCommand(String command) {
        if (connected) {
            serverCommands.add(command);
        }
        return connected;
    }
}
