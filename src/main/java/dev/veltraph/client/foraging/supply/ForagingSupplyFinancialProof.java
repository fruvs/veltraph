package dev.veltraph.client.foraging.supply;
import dev.veltraph.client.core.Module;
import dev.veltraph.client.inventory.bottle.BottleFillReceipts;
import dev.veltraph.client.metrics.*;
import java.util.*;
import java.math.BigDecimal;
/** Internal attribution validator after the port matched exact inventory changes; it does not prove delivery by itself. Polls only read immutable output. */
public final class ForagingSupplyFinancialProof {
    private ForagingSupplyFinancialProof() {}
    public static Optional<FinancialActionReceipt> accept(Module owner,UUID activation,Object menu,int menuId,int requestRevision,
            FinancialOutcomeBindings.Scope scope,long amount,List<BottleFillReceipts.Receipt> receipts) {
        if(owner==null||activation==null||menu==null||menuId<0||requestRevision<0||scope==null||amount<1||receipts==null||receipts.isEmpty()||receipts.size()>64)return Optional.empty();
        var evidence=new ArrayList<FinancialActionReceipt.Evidence>();var seen=new HashSet<Long>();
        for(var receipt:receipts){if(receipt==null||receipt.scope()==null||receipt.kind()==null)return Optional.empty();var nativeScope=receipt.scope();if(nativeScope.menu()!=menu||nativeScope.menuId()!=menuId||nativeScope.player()!=scope.player()||nativeScope.world()!=scope.world()||nativeScope.connection()!=scope.connection()||receipt.kind()==BottleFillReceipts.Kind.PLAYER_SLOT||receipt.revision()<requestRevision||receipt.sequence()<1||!seen.add(receipt.sequence()))return Optional.empty();evidence.add(new FinancialActionReceipt.Evidence(menu,menuId,requestRevision,receipt.revision(),receipt.sequence(),"applied-container-inventory-and-purse"));}
        return Optional.of(new FinancialActionReceipt(activation.toString(),owner,FinancialActionReceipt.Kind.PURCHASE,FinancialActionReceipt.Denomination.COINS,BigDecimal.valueOf(amount),scope,evidence));
    }
}
