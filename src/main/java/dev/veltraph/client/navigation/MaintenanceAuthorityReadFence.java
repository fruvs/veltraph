package dev.veltraph.client.navigation;
import java.util.function.BooleanSupplier;
/** Cancellation observation only. Final native ids are read after the last potentially reentrant source getter. */
public final class MaintenanceAuthorityReadFence {
    private MaintenanceAuthorityReadFence(){}
    public static boolean afterReads(boolean eligible,BooleanSupplier exactSource,BooleanSupplier exactNative){
        if(!eligible||exactSource==null||exactNative==null)return false;
        try{return exactSource.getAsBoolean()&&exactNative.getAsBoolean();}
        catch(RuntimeException|Error unavailable){return false;}
    }
}
