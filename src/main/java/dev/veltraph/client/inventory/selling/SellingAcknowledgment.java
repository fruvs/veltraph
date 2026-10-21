package dev.veltraph.client.inventory.selling;

import dev.veltraph.client.config.AutoSellMode;
import java.math.BigDecimal;
import java.util.Objects;

/** Read-only exact server sale proof. Currency defaults UNKNOWN; it must never be inferred from a prefix. */
public record SellingAcknowledgment(String outcomeId,AutoSellMode channel,String itemIdentity,int inventoryIndex,int quantity,
                                    BigDecimal proceeds,BigDecimal purseBefore,BigDecimal purseAfter,String currencyPrefix,
                                    Denomination denomination,Object player,Object world,Object connection,
                                    dev.veltraph.client.metrics.FinancialActionReceipt.Evidence evidence) {
    public SellingAcknowledgment(String outcomeId,AutoSellMode channel,String itemIdentity,int inventoryIndex,int quantity,
            BigDecimal proceeds,BigDecimal purseBefore,BigDecimal purseAfter,String currencyPrefix,Denomination denomination,Object player,Object world,Object connection){
        this(outcomeId,channel,itemIdentity,inventoryIndex,quantity,proceeds,purseBefore,purseAfter,currencyPrefix,denomination,player,world,connection,null);
    }
    public enum Denomination { UNKNOWN, COINS }
    public SellingAcknowledgment {
        Objects.requireNonNull(outcomeId);Objects.requireNonNull(channel);Objects.requireNonNull(itemIdentity);
        Objects.requireNonNull(proceeds);Objects.requireNonNull(purseBefore);Objects.requireNonNull(purseAfter);
        Objects.requireNonNull(currencyPrefix);Objects.requireNonNull(denomination);
        Objects.requireNonNull(player);Objects.requireNonNull(world);Objects.requireNonNull(connection);
        if(outcomeId.isBlank()||channel==AutoSellMode.TRASH||quantity<1||inventoryIndex<9||inventoryIndex>35
                ||proceeds.signum()<0||purseAfter.subtract(purseBefore).compareTo(proceeds)!=0)throw new IllegalArgumentException("Exact financial sale proof required");
    }
    public boolean current(Object currentPlayer,Object currentWorld,Object currentConnection){
        return player==currentPlayer&&world==currentWorld&&connection==currentConnection;
    }
}
