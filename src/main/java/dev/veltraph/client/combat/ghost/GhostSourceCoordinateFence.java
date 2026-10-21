package dev.veltraph.client.combat.ghost;
import java.util.function.BooleanSupplier;
/** Actual native producer boundaries, separate from source graph rules. No guard result is cached as authority. */
final class GhostSourceCoordinateFence {
    static boolean current(BooleanSupplier fullScope,BooleanSupplier exactFrame){return fullScope.getAsBoolean()&&exactFrame.getAsBoolean();}
    static boolean read(BooleanSupplier fullScope,BooleanSupplier exactFrame,BooleanSupplier nativeMatches){
        return current(fullScope,exactFrame)&&nativeMatches.getAsBoolean()&&current(fullScope,exactFrame);
    }
    static boolean publication(BooleanSupplier fullScope,BooleanSupplier exactFrame,BooleanSupplier footprint,BooleanSupplier sealed){
        return current(fullScope,exactFrame)&&footprint.getAsBoolean()&&sealed.getAsBoolean()&&current(fullScope,exactFrame)&&footprint.getAsBoolean()&&current(fullScope,exactFrame)&&sealed.getAsBoolean();
    }
}
