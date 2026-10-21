package dev.veltraph.client.rotation;
/** Recovered busy(true) query: actual activity, Euclidean angular distance and quantized axis speeds. */
public final class SourceRotationBusy {
    private static final double THRESHOLD=java.util.concurrent.ThreadLocalRandom.current().nextDouble(5,7);
    private SourceRotationBusy(){}
    public static double threshold(){return THRESHOLD;}
    public static boolean evaluate(boolean rotating,boolean targetPresent,float targetYaw,float targetPitch,
            float yaw,float pitch,boolean yawControllerPresent,float yawSpeed,float pitchSpeed,double threshold){
        if(!Double.isFinite(threshold)||threshold<5||threshold>=7)throw new IllegalArgumentException("Source threshold must be in [5,7)");
        // Ordinary source writes enforce rotating => target present. The impossible null receiver
        // would throw in the source; reject it instead of inventing a false busy result.
        if(rotating&&!targetPresent)throw new IllegalStateException("Rotating source controller has no target");
        if(!(rotating||targetPresent))return false;
        if(!Float.isFinite(targetYaw)||!Float.isFinite(targetPitch)||!Float.isFinite(yaw)||!Float.isFinite(pitch)
                ||!Float.isFinite(yawSpeed)||!Float.isFinite(pitchSpeed))return false;
        if(Math.hypot(SourceRotationStepper.wrap(targetYaw-yaw),targetPitch-pitch)<.1)return false;
        if(!yawControllerPresent)return false;
        return yawSpeed>threshold||pitchSpeed>threshold;
    }
}
