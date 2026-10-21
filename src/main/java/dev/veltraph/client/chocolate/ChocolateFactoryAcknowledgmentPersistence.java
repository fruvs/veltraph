package dev.veltraph.client.chocolate;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** A confirmed native spend is actionable only after its exact receipt is durably confirmed. */
final class ChocolateFactoryAcknowledgmentPersistence {
    private ChocolateFactoryAcknowledgmentPersistence() {}
    static boolean confirm(ChocolateFactoryModule.Journal acknowledgment,Consumer<ChocolateFactoryModule.Journal> write,BooleanSupplier flush){
        if(!"ACKNOWLEDGED".equals(acknowledgment.status()))throw new IllegalArgumentException("Confirmed receipt required");
        write.accept(acknowledgment);
        if(flush.getAsBoolean())return true;
        // Retain the known spend and its receipt, and separately latch reconciliation. If this
        // best-effort marker cannot reach disk, the prior write-ahead PENDING still blocks restart.
        write.accept(new ChocolateFactoryModule.Journal(acknowledgment.schema(),acknowledgment.scope(),acknowledgment.verifiedOn(),"ACKNOWLEDGED_UNPERSISTED",acknowledgment.upgrade(),acknowledgment.price(),acknowledgment.spent(),"Native spend acknowledged; durable receipt confirmation failed. Reconcile explicitly.",acknowledgment.manualDeclaredDebit(),acknowledgment.intentId()));
        flush.getAsBoolean();
        return false;
    }
}
