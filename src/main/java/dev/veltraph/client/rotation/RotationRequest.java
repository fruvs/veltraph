package dev.veltraph.client.rotation;

/** One replaceable target and completion callback, matching the decoded rotating/setRotation flow. */
public final class RotationRequest {
    private final SourceRotationStepper stepper;
    private float yaw,pitch;private Runnable completed;private boolean pending;
    private SourceRotationStepper.Step planned,sourceStep;
    private boolean sourceTarget;
    public RotationRequest(SourceRotationStepper stepper) { this.stepper=stepper; }
    public void replace(float yaw,float pitch,Runnable completed) {
        if(!Float.isFinite(yaw)||!Float.isFinite(pitch)||Math.abs(pitch)>90) throw new IllegalArgumentException("Invalid target");
        this.yaw=yaw;this.pitch=pitch;this.completed=completed;pending=true;
        planned=null;sourceStep=null;sourceTarget=true;
    }
    public SourceRotationStepper.Step advance(float currentYaw,float currentPitch,SourceRotationStepper.Settings settings,boolean available,boolean spyglass) {
        planned=null;
        if(!pending||!available||spyglass) return new SourceRotationStepper.Step(currentYaw,currentPitch,0,0,false,false);
        planned=stepper.step(currentYaw,currentPitch,yaw,pitch,settings);sourceStep=planned;return planned;
    }
    /** Called only after the resulting camera angle was actually applied. Clear before invoking reentrant callbacks. */
    public void applied(SourceRotationStepper.Step step) {
        if(!pending||step!=planned||!step.complete()||step.rejected()) return;
        Runnable callback=completed;cancel();if(callback!=null) callback.run();
    }
    public void cancel() { pending=false;completed=null;planned=null;sourceTarget=false;sourceStep=null; }
    /** Observes the prior actual source calculation without advancing controller or callback state. */
    public boolean sourceBusy(float currentYaw,float currentPitch){
        return SourceRotationBusy.evaluate(pending,sourceTarget,yaw,pitch,currentYaw,currentPitch,sourceStep!=null,
                sourceStep==null?0:sourceStep.yawSpeed(),sourceStep==null?0:sourceStep.pitchSpeed(),SourceRotationBusy.threshold());
    }
    public boolean pending() { return pending; }
}
