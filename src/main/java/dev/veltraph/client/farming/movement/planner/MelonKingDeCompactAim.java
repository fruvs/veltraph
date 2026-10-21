package dev.veltraph.client.farming.movement.planner;

import java.util.OptionalDouble;

/** Native26.3 ladder clearance and lower-fruit window; retain the nearest mouse-reachable view. */
public final class MelonKingDeCompactAim {
    // Measured standing SoulSand wall contact: ladder clears above58.938 degrees; lower
    // fruit stays below its top until59.289. These inset limits preserve both margins.
    public static final double MIN_PITCH=-59.25,MAX_PITCH=-58.94;
    private MelonKingDeCompactAim() {}
    public static OptionalDouble target(double initialPitch,boolean mouseGcd,double sensitivity) {
        if(!Double.isFinite(initialPitch)||initialPitch < -90||initialPitch > 90
                ||!Double.isFinite(sensitivity)||sensitivity<0||sensitivity>1)return OptionalDouble.empty();
        if(!mouseGcd)return OptionalDouble.of(-59);
        double step=Math.pow(sensitivity*.6+.2,3)*1.2;
        long low=(long)Math.ceil((MIN_PITCH-initialPitch)/step),high=(long)Math.floor((MAX_PITCH-initialPitch)/step);
        if(low>high)return OptionalDouble.empty();
        long nearest=Math.max(low,Math.min(high,Math.round((-59-initialPitch)/step)));
        return OptionalDouble.of(initialPitch+nearest*step);
    }
    public static boolean ready(float pitch){return Float.isFinite(pitch)&&pitch>=MIN_PITCH&&pitch<=MAX_PITCH;}
}
