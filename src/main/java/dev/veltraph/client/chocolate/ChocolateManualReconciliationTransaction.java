package dev.veltraph.client.chocolate;
import java.util.function.*;
/** Local durability transaction only: no native input, financial ACK or automatic rearm. */
final class ChocolateManualReconciliationTransaction {
    private ChocolateManualReconciliationTransaction() {}
    static <T> boolean commit(T pending,T resolved,Consumer<T> write,BooleanSupplier flush,BooleanSupplier stillValid,Runnable closeRuntime,Runnable restoreOwnedRuntime){
        boolean closing=false;
        try{write.accept(resolved);if(!flush.getAsBoolean()||!stillValid.getAsBoolean()){restore(pending,write,flush);return false;}closing=true;closeRuntime.run();return true;}
        catch(RuntimeException failure){if(closing)try{restoreOwnedRuntime.run();}catch(RuntimeException restoreFailure){failure.addSuppressed(restoreFailure);}restore(pending,write,flush);return false;}
    }
    private static <T> void restore(T pending,Consumer<T> write,BooleanSupplier flush){try{write.accept(pending);}catch(RuntimeException ignored){}try{flush.getAsBoolean();}catch(RuntimeException ignored){}}
}
