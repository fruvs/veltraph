package dev.veltraph.client.scheduling;

import java.util.Objects;
import java.util.function.BooleanSupplier;

/** One explicit handoff: canceled interactions stay cleared and failed resumes never replay movement. */
public final class ScheduledResumeHandoff {
    public enum State { READY,RESUMED,FAILED }
    private State state=State.READY;
    public boolean complete(BooleanSupplier resumeExactEngine,Runnable restoreMovement,Runnable clearInteractions,Runnable failStop){
        Objects.requireNonNull(resumeExactEngine);Objects.requireNonNull(restoreMovement);Objects.requireNonNull(clearInteractions);Objects.requireNonNull(failStop);
        if(state!=State.READY)return false;
        // Consume before callback invocation, preventing reentrant/duplicate input replay.
        state=State.FAILED;
        try{
            clearInteractions.run();
            if(!resumeExactEngine.getAsBoolean())return false;
            restoreMovement.run();
            clearInteractions.run();
            state=State.RESUMED;return true;
        }catch(RuntimeException failure){return false;}
        finally{if(state!=State.RESUMED)try{failStop.run();}finally{clearInteractions.run();}}
    }
    public State state(){return state;}
}
