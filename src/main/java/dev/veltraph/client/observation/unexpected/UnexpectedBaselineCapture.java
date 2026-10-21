package dev.veltraph.client.observation.unexpected;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
/** Snapshot failure containment; cancellation is permitted only for the exact captured custody. */
public final class UnexpectedBaselineCapture {
    private UnexpectedBaselineCapture() {}
    public static boolean capture(Runnable snapshot,BooleanSupplier stillOwned,Runnable cancelExact,Consumer<RuntimeException> unavailable){
        Objects.requireNonNull(snapshot);Objects.requireNonNull(stillOwned);Objects.requireNonNull(cancelExact);Objects.requireNonNull(unavailable);
        try{snapshot.run();return true;}
        catch(RuntimeException failure){
            try{if(stillOwned.getAsBoolean())cancelExact.run();}
            catch(RuntimeException cleanup){failure.addSuppressed(cleanup);}
            unavailable.accept(failure);return false;
        }
    }
}
