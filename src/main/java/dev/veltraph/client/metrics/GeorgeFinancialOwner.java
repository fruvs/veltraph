package dev.veltraph.client.metrics;
import dev.veltraph.client.core.Module;
import dev.veltraph.client.inventory.selling.GeorgeWarpPermit;
import dev.veltraph.client.rewarp.FarmMaintenanceLease;
import java.util.List;
/** Narrow native George producer; only an exact committed lease may continue its financial scope. */
public interface GeorgeFinancialOwner extends Module {
    List<FinancialActionReceipt> acknowledgedGeorgeFinancialActions();
    boolean admittedGeorgeContinuation(FarmMaintenanceLease.Token token,GeorgeWarpPermit permit,
            FinancialOutcomeBindings.Scope previous,FinancialOutcomeBindings.Scope current);
}
