package dev.veltraph.client.metrics;
import dev.veltraph.client.core.Module;import dev.veltraph.client.inventory.selling.SellingAcknowledgment;import java.util.*;import java.util.function.Supplier;
/** Existing native per-stack evidence, attributed once to the actual registered seller owner. */
public final class SellingReceiptProjection {
 private SellingReceiptProjection(){}
 public static List<FinancialOutcomeBindings.Outcome> project(Module owner,List<SellingAcknowledgment> receipts){return VisitorPostQueueReceiptProjection.project(owner,receipts);}
 public static FinancialOutcomeBindings.Binding binding(Module owner,Supplier<List<SellingAcknowledgment>> receipts){return new FinancialOutcomeBindings.Binding(()->project(owner,receipts.get()),false);}
}
