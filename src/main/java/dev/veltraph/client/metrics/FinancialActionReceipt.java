package dev.veltraph.client.metrics;

import dev.veltraph.client.core.Module;
import java.math.BigDecimal;
import java.util.*;

/** Local-only typed action evidence. Neither quotes nor purse samples alone construct receipts. */
public record FinancialActionReceipt(String id,Module owner,Kind kind,Denomination denomination,
        BigDecimal nativeAmountOrNull,FinancialOutcomeBindings.Scope scope,List<Evidence> evidence) {
    public enum Kind { SALE, PURCHASE, CRAFT_FEE, REWARD }
    public enum Denomination { UNKNOWN, COINS, CHOCOLATE, JACOB_TICKETS }
    /** Sequence is the producer's matched receipt/accepted action serial, not a fabricated wire packet ID. */
    public record Evidence(Object menu,int menuId,int requestRevision,int receiptRevision,long sequence,String channel) {
        public Evidence {
            Objects.requireNonNull(menu);Objects.requireNonNull(channel);
            if(menuId<0||requestRevision<0||receiptRevision<0||sequence<1||channel.isBlank())throw new IllegalArgumentException("Exact local menu/action attribution required");
        }
    }
    public FinancialActionReceipt {
        Objects.requireNonNull(id);Objects.requireNonNull(owner);Objects.requireNonNull(kind);
        Objects.requireNonNull(denomination);Objects.requireNonNull(scope);evidence=List.copyOf(evidence);
        if(!UUID.fromString(id).toString().equals(id)||evidence.isEmpty()||evidence.size()>64
                ||nativeAmountOrNull!=null&&(nativeAmountOrNull.signum()<0||nativeAmountOrNull.precision()>24))
            throw new IllegalArgumentException("Canonical receipt UUID and bounded nonnegative native amount required");
    }
    public boolean expense(){return kind!=Kind.SALE&&kind!=Kind.REWARD;}
    /** A native Chocolate quote is not a Coin valuation. UNKNOWN also stays unavailable. */
    public BigDecimal coinAmountOrNull(){return denomination==Denomination.COINS?nativeAmountOrNull:null;}
}
