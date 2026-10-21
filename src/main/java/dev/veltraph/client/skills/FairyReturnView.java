package dev.veltraph.client.skills;

import dev.veltraph.client.service.RotationService;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;

/** Requested local smooth-return correction; original reset angular semantics remain unbound. */
final class FairyReturnView {
    enum State { IDLE, RESTORING, COMPLETE, CANCELLED, FAILED }
    private final RotationService rotation;
    private final BooleanSupplier current, ownsCamera;
    private final float yaw,pitch;private final float remainderBound;private boolean endpointPublished;
    private State state=State.IDLE;
    private long generation,requestGeneration;private final LongSupplier nativeGeneration;
    FairyReturnView(RotationService rotation,BooleanSupplier current,BooleanSupplier ownsCamera,float yaw,float pitch) {this(rotation,current,ownsCamera,yaw,pitch,.2f);}
    FairyReturnView(RotationService rotation,BooleanSupplier current,BooleanSupplier ownsCamera,float yaw,float pitch,float remainderBound) {this(rotation,current,ownsCamera,yaw,pitch,remainderBound,null);}
    FairyReturnView(RotationService rotation,BooleanSupplier current,BooleanSupplier ownsCamera,float yaw,float pitch,float remainderBound,LongSupplier nativeGeneration) {
        this.rotation=Objects.requireNonNull(rotation);this.current=Objects.requireNonNull(current);
        this.nativeGeneration=nativeGeneration;this.ownsCamera=Objects.requireNonNull(ownsCamera);this.yaw=yaw;this.pitch=pitch;this.remainderBound=remainderBound;
    }
    boolean start() {
        if(state!=State.IDLE||!current.getAsBoolean()){state=State.FAILED;return false;}
        if(!preparePublication(1)){state=State.FAILED;return false;}
        state=State.RESTORING;long ticket=++generation;
        rotation.rotateTo(yaw,pitch,()->{
            if(ticket!=generation||state!=State.RESTORING)return;
            if(!current.getAsBoolean()||!ownsRequest()){abort(State.FAILED);return;}
            double remainderYaw=Math.abs(dev.veltraph.client.rotation.SourceRotationStepper.wrap(rotation.yaw()-yaw));
            double remainderPitch=Math.abs(rotation.pitch()-pitch);
            // rotateTo completes at its quantized endpoint. Publish only the remaining sub-degree
            // endpoint using existing bounded tracking; SEARCHING waits its actual pose.
            if(remainderYaw>=.01||remainderPitch>=.01){
                if(remainderYaw>remainderBound||remainderPitch>remainderBound){abort(State.FAILED);return;}
                endpointPublished=true;return;
            }
            state=State.COMPLETE;
        });
        if(!current.getAsBoolean()||!ownsRequest()){abort(State.FAILED);return false;}
        return true;
    }
    private boolean ownsRequest(){return nativeGeneration==null||nativeGeneration.getAsLong()==requestGeneration;}
    private boolean preparePublication(int increments){
        if(nativeGeneration==null)return true;
        try{requestGeneration=Math.addExact(nativeGeneration.getAsLong(),increments);return true;}catch(ArithmeticException overflow){return false;}
    }
    State tick() {
        if(state==State.RESTORING&&(!current.getAsBoolean()||!ownsRequest()))abort(State.FAILED);
        if(state==State.RESTORING&&endpointPublished){
            if(!preparePublication(2)){abort(State.FAILED);return state;}
            rotation.track(yaw,pitch);
            if(!current.getAsBoolean()||!ownsRequest()){abort(State.FAILED);return state;}
            if(Math.abs(dev.veltraph.client.rotation.SourceRotationStepper.wrap(rotation.yaw()-yaw))<.01
                &&Math.abs(rotation.pitch()-pitch)<.01)state=State.COMPLETE;
        }
        return state;
    }
    void cancel(){abort(State.CANCELLED);}
    private void abort(State next) {
        ++generation;
        if(state==State.RESTORING&&ownsCamera.getAsBoolean()&&ownsRequest())rotation.cancelRotation();
        state=next;
    }
    State state(){return state;}
}
