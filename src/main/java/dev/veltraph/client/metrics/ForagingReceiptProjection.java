package dev.veltraph.client.metrics;
import dev.veltraph.client.core.Module;
import java.util.*;
import java.util.function.Supplier;
/** Publishes already accepted acquisition evidence; never observes packets or constructs proof. */
public final class ForagingReceiptProjection {
    private ForagingReceiptProjection(){}
    public static FinancialOutcomeBindings.Binding binding(Module owner,Supplier<List<FinancialActionReceipt>> receipts){
        Objects.requireNonNull(owner);Objects.requireNonNull(receipts);
        return new FinancialOutcomeBindings.Binding(()->receipts.get().stream().filter(receipt->receipt!=null&&receipt.owner()==owner&&receipt.kind()==FinancialActionReceipt.Kind.PURCHASE).map(FinancialOutcomeBindings.Outcome::new).toList(),false);
    }
}
