package dev.veltraph.client.chocolate;
import java.util.function.*;
/** Confirmation failures preserve the exact known result and leave a restart reconciliation marker. */
final class ChocolateConsequentialPersistence {
    private ChocolateConsequentialPersistence() {}
    static boolean persist(ChocolateActionJournal journal,Consumer<ChocolateActionJournal> write,BooleanSupplier flush){write.accept(journal);if(flush.getAsBoolean())return true;var marker=new ChocolateActionJournal(journal.schema(),journal.id(),journal.scope(),journal.action(),journal.status().equals("ACKNOWLEDGED")?"ACKNOWLEDGED_UNPERSISTED":"UNKNOWN",journal.phase(),journal.evidence(),"Durable consequential journal confirmation failed; reconcile explicitly",journal.result());write.accept(marker);flush.getAsBoolean();return false;}
}
