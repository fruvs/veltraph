package dev.veltraph.client.hydra;
import java.util.function.Consumer;
/** Reporting is strictly downstream of a completed decision; neither sink can throw into gameplay. */
public final class FloorDiagnosticReporting {
    private FloorDiagnosticReporting(){}
    public static void run(Runnable reporting,Consumer<Throwable> unavailable){try{reporting.run();}catch(Throwable failure){try{unavailable.accept(failure);}catch(Throwable ignored){}}}
    public static <T>T read(java.util.function.Supplier<T> supplemental,T unavailable){try{return supplemental.get();}catch(Throwable ignored){return unavailable;}}
}
