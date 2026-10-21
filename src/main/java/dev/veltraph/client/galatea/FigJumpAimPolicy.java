package dev.veltraph.client.galatea;

import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

/** Readable Fig jump endpoint and counter arithmetic. Source reset signals must be supplied,
 * never inferred from a pulse timer or a failed hit. */
public final class FigJumpAimPolicy {
    public enum Mode { STRONG, AVERAGE, LAZY }
    public record Angle(double yaw,double pitch) {
        public Angle {
            if(!Double.isFinite(yaw)||!Double.isFinite(pitch)||Math.abs(pitch)>90)
                throw new IllegalArgumentException("Invalid camera endpoint");
        }
    }
    // Source angle utility samples this once, rather than resampling each frame/target.
    private static final double PROCESS_WEIGHT=ThreadLocalRandom.current().nextDouble(.2,.4);
    private FigJumpAimPolicy() {}
    public static double processWeight() { return PROCESS_WEIGHT; }
    public static Mode effective(Mode configured,int counter) {
        Objects.requireNonNull(configured);
        return Mode.values()[Math.max(0,configured.ordinal()-Math.max(counter,0))];
    }
    /** Caller order matters: yaw uses first+(1-weight)*second vectors while pitch lerps by weight. */
    public static Angle blend(Angle first,Angle second,double weight) {
        Objects.requireNonNull(first);Objects.requireNonNull(second);
        if(!Double.isFinite(weight)||weight<.2||weight>=.4)throw new IllegalArgumentException("Source weight outside [0.2,0.4)");
        double a=Math.toRadians(first.yaw()),b=Math.toRadians(second.yaw());
        double yaw=Math.toDegrees(Math.atan2(Math.sin(a)+(1-weight)*Math.sin(b),Math.cos(a)+(1-weight)*Math.cos(b)));
        return new Angle(yaw,first.pitch()+(second.pitch()-first.pitch())*weight);
    }
    public static Angle endpoint(Mode configured,int counter,Angle suppliedBase,Angle stemCenter,double weight) {
        return switch(effective(configured,counter)) {
            case STRONG -> Objects.requireNonNull(suppliedBase);
            case AVERAGE -> blend(suppliedBase,stemCenter,weight);
            case LAZY -> blend(stemCenter,suppliedBase,weight);
        };
    }
    /** Known source arithmetic, with both otherwise unbound reset flags explicit. */
    public static int observeCounter(int counter,boolean helperReset,boolean figReset,boolean onGround,boolean jumpHeld) {
        if(helperReset||figReset) { counter=0;if(!onGround)counter--; }
        if(onGround&&jumpHeld)counter++;
        return counter;
    }
}
