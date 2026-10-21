package dev.veltraph.client.metrics;

import dev.veltraph.client.core.Module;
import dev.veltraph.client.inventory.selling.SellingAcknowledgment;
import java.util.List;

/** Reattribute existing native sale evidence to its still-active Visitor parent; never infer currency. */
public final class VisitorPostQueueReceiptProjection {
    private VisitorPostQueueReceiptProjection() {}
    public static List<FinancialOutcomeBindings.Outcome> project(Module visitor,List<SellingAcknowledgment> receipts){
        return receipts.stream().filter(receipt->receipt.evidence()!=null).map(receipt->new FinancialOutcomeBindings.Outcome(
                new FinancialActionReceipt(receipt.outcomeId(),visitor,FinancialActionReceipt.Kind.SALE,
                        receipt.denomination()==SellingAcknowledgment.Denomination.COINS?FinancialActionReceipt.Denomination.COINS:FinancialActionReceipt.Denomination.UNKNOWN,
                        receipt.proceeds(),new FinancialOutcomeBindings.Scope(receipt.player(),receipt.world(),receipt.connection()),List.of(receipt.evidence())))).toList();
    }
}
