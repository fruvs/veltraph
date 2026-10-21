package dev.veltraph.client.metrics.mc;

import dev.veltraph.client.core.Module;
import dev.veltraph.client.core.ModuleManager;
import dev.veltraph.client.inventory.selling.*;
import dev.veltraph.client.metrics.*;
import dev.veltraph.client.skills.CarpentryFlowModule;
import dev.veltraph.client.chocolate.ChocolateFactoryModule;
import java.util.*;
import java.util.function.*;
import net.minecraft.client.Minecraft;
import net.minecraft.world.inventory.AbstractContainerMenu;

/** Reads already accepted local action receipts; it never clicks, drives owners, or values loot. */
public final class McSellingFinancialAdapter {
    private final FinancialOutcomeBindings bindings;
    public McSellingFinancialAdapter(Minecraft client,ModuleManager manager,Supplier<McLocalMetricsCollector> collector,BooleanSupplier enabled){
        this(client,manager,collector,enabled,false,false,false,false,false);
    }
    public McSellingFinancialAdapter(Minecraft client,ModuleManager manager,Supplier<McLocalMetricsCollector> collector,
                                    BooleanSupplier enabled,boolean guardedNativeAccepted,boolean farmNativeAccepted,boolean bazaarNativeAccepted){
        this(client,manager,collector,enabled,guardedNativeAccepted,farmNativeAccepted,bazaarNativeAccepted,false,false);
    }
    /** Explicit per-producer acceptance; purchases stay false until their native fixtures are accepted. */
    public McSellingFinancialAdapter(Minecraft client,ModuleManager manager,Supplier<McLocalMetricsCollector> collector,
            BooleanSupplier enabled,boolean guardedNativeAccepted,boolean farmNativeAccepted,boolean bazaarNativeAccepted,
            boolean carpentryNativeAccepted,boolean chocolateNativeAccepted){
        Supplier<FinancialOutcomeBindings.Scope> scope=()->{
            if(!client.isSameThread()||client.player==null||client.level==null||client.getConnection()==null)return null;
            return new FinancialOutcomeBindings.Scope(client.player,client.level,client.getConnection());
        };
        bindings=new FinancialOutcomeBindings(manager,owner->classify(owner,scope,guardedNativeAccepted,farmNativeAccepted,bazaarNativeAccepted,carpentryNativeAccepted,chocolateNativeAccepted),scope,()->{
            var current=collector.get();if(current==null||current.ownerObservationIdentity()==null)return null;
            var observationIdentity=current.ownerObservationIdentity();
            return new FinancialOutcomeBindings.Sink(){
                @Override public Object identity(){return observationIdentity;}
                @Override public boolean confirmed(String id,boolean expense,java.math.BigDecimal amount,long now){return current.confirmedFinancialOutcome(id,expense,amount,now);}
                @Override public boolean confirmed(FinancialOutcomeBindings.Outcome outcome,long now){return outcome.receipt()==null?confirmed(outcome.id(),outcome.expense(),outcome.amountOrNull(),now):current.confirmedFinancialOutcome(outcome.receipt(),now);}
            };
        },enabled);
    }
    public void afterFreshStart(Module owner,long now){bindings.afterFreshStart(owner,now);}
    public void poll(long now){bindings.poll(now);}
    public boolean continueGeorge(dev.veltraph.client.metrics.GeorgeFinancialOwner owner,dev.veltraph.client.rewarp.FarmMaintenanceLease.Token token,
            dev.veltraph.client.inventory.selling.GeorgeWarpPermit permit,FinancialOutcomeBindings.Scope previous,FinancialOutcomeBindings.Scope current){
        return bindings.continueGeorge(owner,token,permit,previous,current);
    }

    public boolean continueBottle(dev.veltraph.client.inventory.bottle.BottleAcquisitionModule owner,dev.veltraph.client.inventory.bottle.BottleAcquisitionRuntime.Travel travel,
            FinancialOutcomeBindings.Scope previous,FinancialOutcomeBindings.Scope current,dev.veltraph.client.metrics.BottleFinancialContinuation admission){return bindings.continueBottle(owner,travel,previous,current,admission);}
    private static Optional<FinancialOutcomeBindings.Binding> classify(Module owner,Supplier<FinancialOutcomeBindings.Scope> scope,boolean guarded,boolean farm,boolean bazaar,boolean carpentry,boolean chocolate){
        if(owner instanceof dev.veltraph.client.foraging.replant.ForagingReplantModule replant)return Optional.of(ForagingReceiptProjection.binding(owner,replant::acknowledgedFinancialActions));
        if(owner instanceof dev.veltraph.client.garden.jacob.mc.McJacobRewardModule jacob)return Optional.of(new FinancialOutcomeBindings.Binding(
                ()->jacob.acknowledgedFinancialActions().stream().map(FinancialOutcomeBindings.Outcome::new).toList(),false));
        if(owner instanceof dev.veltraph.client.foraging.supply.ForagingSupplyModule supply)return Optional.of(ForagingReceiptProjection.binding(owner,supply::acknowledgedFinancialActions));
        if(owner instanceof dev.veltraph.client.inventory.bottle.BottleAcquisitionModule bottle)return Optional.of(new FinancialOutcomeBindings.Binding(
                ()->bottle.financialReceipts().stream().map(FinancialOutcomeBindings.Outcome::new).toList(),false));
        if(farm&&owner instanceof dev.veltraph.client.metrics.GeorgeFinancialOwner george)return Optional.of(new FinancialOutcomeBindings.Binding(
                ()->george.acknowledgedGeorgeFinancialActions().stream().map(FinancialOutcomeBindings.Outcome::new).toList(),false));
        if(owner instanceof dev.veltraph.client.garden.visitor.mc.McVisitorCycleModule visitor){
            return Optional.of(new FinancialOutcomeBindings.Binding(()->{
                var outcomes=new java.util.ArrayList<FinancialOutcomeBindings.Outcome>();
                if(guarded)outcomes.addAll(dev.veltraph.client.metrics.VisitorPostQueueReceiptProjection.project(owner,visitor.acknowledgedPostQueueSales()));
                if(bazaar)visitor.acknowledgedPostQueueBazaarSale().flatMap(receipt->outcome(owner,receipt)).ifPresent(outcomes::add);
                return java.util.List.copyOf(outcomes);
            },false));
        }
        if(guarded&&owner instanceof GuardedSellingModule module)return Optional.of(SellingReceiptProjection.binding(owner,module::acknowledgedSales));
        if(farm&&owner instanceof McFarmAutoSellModule module)return Optional.of(new FinancialOutcomeBindings.Binding(()->
                module.acknowledgedSale().flatMap(p->outcome(owner,p)).or(()->bazaar?module.acknowledgedBazaarSale().flatMap(p->outcome(owner,p)):Optional.empty())));
        if(bazaar&&owner instanceof BazaarCustodyModule module)return Optional.of(new FinancialOutcomeBindings.Binding(()->module.acknowledgedSale().flatMap(p->outcome(owner,p))));
        if(carpentry&&owner instanceof CarpentryFlowModule module)return Optional.of(new FinancialOutcomeBindings.Binding(()->module.acknowledgedFinancialActions().stream().map(FinancialOutcomeBindings.Outcome::new).toList(),false));
        if(chocolate&&owner instanceof ChocolateFactoryModule module){
            var boundScope=scope.get();if(boundScope==null)return Optional.empty();
            return Optional.of(new FinancialOutcomeBindings.Binding(()->module.runtime().acknowledgedUpgrades().stream().map(p->{
                var before=p.selection().observation();var after=p.receipt();var menu=(AbstractContainerMenu)before.menu();
                return new FinancialOutcomeBindings.Outcome(new FinancialActionReceipt(p.id(),owner,FinancialActionReceipt.Kind.PURCHASE,FinancialActionReceipt.Denomination.CHOCOLATE,p.selection().quote().price(),boundScope,
                        List.of(new FinancialActionReceipt.Evidence(before.menu(),menu.containerId,before.revision(),after.revision(),p.sequence(),"CHOCOLATE_LEVEL_BALANCE_RECEIPT"))));
            }).toList(),false));
        }
        return Optional.empty();
    }
    private static Optional<FinancialOutcomeBindings.Binding> sale(Module owner,Supplier<Optional<SellingAcknowledgment>> receipt){
        return Optional.of(new FinancialOutcomeBindings.Binding(()->receipt.get().flatMap(p->outcome(owner,p))));
    }
    private static FinancialActionReceipt.Denomination denomination(SellingAcknowledgment.Denomination denomination){return denomination==SellingAcknowledgment.Denomination.COINS?FinancialActionReceipt.Denomination.COINS:FinancialActionReceipt.Denomination.UNKNOWN;}
    private static Optional<FinancialOutcomeBindings.Outcome> outcome(Module owner,SellingAcknowledgment proof){
        if(proof.evidence()==null)return Optional.empty();
        return Optional.of(new FinancialOutcomeBindings.Outcome(new FinancialActionReceipt(proof.outcomeId(),owner,FinancialActionReceipt.Kind.SALE,denomination(proof.denomination()),proof.proceeds(),new FinancialOutcomeBindings.Scope(proof.player(),proof.world(),proof.connection()),List.of(proof.evidence()))));
    }
    private static Optional<FinancialOutcomeBindings.Outcome> outcome(Module owner,BazaarCustodyModule.Acknowledgment proof){
        if(proof.evidence().isEmpty())return Optional.empty();
        return Optional.of(new FinancialOutcomeBindings.Outcome(new FinancialActionReceipt(proof.outcomeId(),owner,FinancialActionReceipt.Kind.SALE,denomination(proof.denomination()),proof.proceeds(),new FinancialOutcomeBindings.Scope(proof.player(),proof.world(),proof.connection()),proof.evidence())));
    }
}
