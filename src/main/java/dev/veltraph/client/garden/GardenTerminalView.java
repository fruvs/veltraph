package dev.veltraph.client.garden;

import dev.veltraph.client.service.RotationService;
import dev.veltraph.client.rotation.SourceRotationStepper;
import java.util.function.BooleanSupplier;

/** Local smooth terminal restoration, before a Garden workflow relinquishes its authority. */
public final class GardenTerminalView {
    public enum State { IDLE, RESTORING, COMPLETE, FAILED, CANCELLED }
    private final RotationService rotation;
    private final BooleanSupplier current, ownsCamera;
    private final float yaw, pitch;
    private State state=State.IDLE;
    private boolean fine;
    private long generation;
    public GardenTerminalView(RotationService rotation,BooleanSupplier current,BooleanSupplier ownsCamera,float yaw,float pitch){
        this.rotation=java.util.Objects.requireNonNull(rotation);this.current=java.util.Objects.requireNonNull(current);
        this.ownsCamera=java.util.Objects.requireNonNull(ownsCamera);this.yaw=yaw;this.pitch=pitch;
    }
    public boolean start(){
        if(state!=State.IDLE||!current.getAsBoolean()||!ownsCamera.getAsBoolean()){state=State.FAILED;return false;}
        state=State.RESTORING;long issued=++generation;
        rotation.rotateTo(yaw,pitch,()->{
            if(issued!=generation||state!=State.RESTORING)return;
            if(!current.getAsBoolean()||!ownsCamera.getAsBoolean()){abort(State.FAILED);return;}
            // Completion is the actual quantized endpoint, which can retain less than one mouse step.
            float bound=SourceRotationStepper.mouseStep(1)+.001f;
            if(yawError()>bound||Math.abs(rotation.pitch()-pitch)>bound){abort(State.FAILED);return;}
            fine=true;
        });
        return true;
    }
    public State tick(){
        if(state==State.RESTORING){
            if(!current.getAsBoolean()||!ownsCamera.getAsBoolean()){abort(State.FAILED);return state;}
            if(fine){
                if(yawError()<.01&&Math.abs(rotation.pitch()-pitch)<.01)state=State.COMPLETE;
                else rotation.track(yaw,pitch);
            }
        }
        return state;
    }
    private double yawError(){return Math.abs(SourceRotationStepper.wrap(rotation.yaw()-yaw));}
    public void cancel(){abort(State.CANCELLED);}
    private void abort(State next){++generation;if((state==State.RESTORING||state==State.COMPLETE)&&ownsCamera.getAsBoolean())rotation.cancelRotation();state=next;}
    public State state(){return state;}
}
