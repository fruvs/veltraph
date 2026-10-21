package dev.veltraph.client.service.mc;

import java.util.function.BooleanSupplier;

/** Post-publication ordering only. Actual private native receipts and stored leaves supply all evidence. */
final class OwnedBreakingCommitFence {
    private OwnedBreakingCommitFence() {}
    static boolean current(boolean normallyConfirmed,BooleanSupplier actualHistory,BooleanSupplier sourceVeto,BooleanSupplier storedNativeLeaf){
        try{return normallyConfirmed&&actualHistory.getAsBoolean()&&sourceVeto.getAsBoolean()
            &&storedNativeLeaf.getAsBoolean()&&actualHistory.getAsBoolean();}
        catch(RuntimeException unavailable){return false;}
    }
}
