package dev.veltraph.client.rewarp;
import java.util.function.BooleanSupplier;
/** Passive observations cannot select a tool; repair belongs only to independently verified custody. */
public final class FarmRecoveryToolObservation {
    private FarmRecoveryToolObservation() {}
    public static boolean ready(boolean ownsIssuedReturn,BooleanSupplier selectedTool,BooleanSupplier ownedRepair) {
        return ownsIssuedReturn?ownedRepair.getAsBoolean():selectedTool.getAsBoolean();
    }
}
