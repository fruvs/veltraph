package dev.veltraph.client.metrics;

import dev.veltraph.client.core.Module;
import dev.veltraph.client.core.ModuleManager;
import java.math.BigDecimal;
import java.util.*;
import java.util.function.*;

/** Read-only, fresh-activation bridge for already acknowledged financial outcomes. */
public final class FinancialOutcomeBindings {
    public record Scope(Object player,Object world,Object connection) {
        public Scope {Objects.requireNonNull(player);Objects.requireNonNull(world);Objects.requireNonNull(connection);}
        public boolean same(Scope other){return other!=null&&player==other.player&&world==other.world&&connection==other.connection;}
    }
    public record Outcome(String id,boolean expense,BigDecimal amountOrNull,Scope scope,FinancialActionReceipt receipt) {
        public Outcome(String id,boolean expense,BigDecimal amountOrNull,Scope scope){this(id,expense,amountOrNull,scope,null);}
        public Outcome(FinancialActionReceipt receipt){this(receipt.id(),receipt.expense(),receipt.coinAmountOrNull(),receipt.scope(),receipt);}
        public Outcome {
            Objects.requireNonNull(id);Objects.requireNonNull(scope);
            if(receipt!=null&&(!id.equals(receipt.id())||expense!=receipt.expense()||!Objects.equals(amountOrNull,receipt.coinAmountOrNull())||!scope.same(receipt.scope())))throw new IllegalArgumentException("Typed receipt attribution must match the outcome");
            if(!UUID.fromString(id).toString().equals(id)||amountOrNull!=null&&amountOrNull.signum()<0)
                throw new IllegalArgumentException("Canonical activation UUID and nonnegative confirmed amount required");
        }
    }
    public record Binding(Supplier<List<Outcome>> outcomes,boolean terminalOnly) {
        public Binding(Supplier<Optional<Outcome>> acknowledged){this(()->acknowledged.get().stream().toList(),true);}
        public Binding {Objects.requireNonNull(outcomes);}
    }
    public interface Sink {
        default Object identity(){return this;}
        boolean confirmed(String outcomeId,boolean expense,BigDecimal amountOrNull,long now);
        default boolean confirmed(Outcome outcome,long now){return confirmed(outcome.id(),outcome.expense(),outcome.amountOrNull(),now);}
    }
    private record Activation(Binding binding,Scope scope,Object sinkIdentity,Set<String> seen) {}
    private final ModuleManager manager;private final Function<Module,Optional<Binding>> classify;
    private final Supplier<Scope> scope;private final Supplier<Sink> sink;private final BooleanSupplier enabled;
    private final Map<Module,Activation> active=new IdentityHashMap<>();
    private record BottlePending(Activation activation,dev.veltraph.client.inventory.bottle.BottleAcquisitionRuntime.Travel travel,long lastNow){}
    private final Map<Module,BottlePending> bottlePending=new IdentityHashMap<>();
    private boolean retainPendingBottle(Module owner,Activation activation,Scope current,long now){
        if(!(owner instanceof BottleFinancialOwner bottle)||!owner.isEnabled()||activation.binding().terminalOnly())return false;
        var travel=bottle.pendingBottleFinancialTravel().orElse(null);var prior=bottlePending.get(owner);
        if(travel==null||current==null||current.connection()!=travel.connection()||activation.scope().player()!=travel.player()
                ||activation.scope().world()!=travel.world()||activation.scope().connection()!=travel.connection()
                ||now<travel.issuedMillis()||now>travel.expiresMillis()
                ||prior!=null&&(prior.activation()!=activation||prior.travel()!=travel||now<prior.lastNow()))return false;
        bottlePending.put(owner,new BottlePending(activation,travel,now));return true;
    }
    public FinancialOutcomeBindings(ModuleManager manager,Function<Module,Optional<Binding>> classify,
                                    Supplier<Scope> scope,Supplier<Sink> sink,BooleanSupplier enabled){
        this.manager=Objects.requireNonNull(manager);this.classify=Objects.requireNonNull(classify);
        this.scope=Objects.requireNonNull(scope);this.sink=Objects.requireNonNull(sink);this.enabled=Objects.requireNonNull(enabled);
    }
    /** Root calls after a real successful fresh start. A preserved resume never arms an outcome. */
    public void afterFreshStart(Module owner,long now){
        if(owner==null)return;active.remove(owner);bottlePending.remove(owner);
        if(manager.get(owner.id()).orElse(null)!=owner||!owner.isEnabled()||!enabled.getAsBoolean())return;
        var binding=classify.apply(owner);if(binding.isEmpty())return;
        var currentScope=scope.get();var target=sink.get();if(currentScope==null||target==null)return;
        // Some deferred owners still expose the previous terminal result until their first tick.
        var seen=new HashSet<String>();for(var prior:binding.get().outcomes().get())seen.add(prior.id());
        active.put(owner,new Activation(binding.get(),currentScope,target.identity(),seen));
    }
    /** Preserve only the exact admitted George activation and seen receipts across its trusted native handoff. */
    public boolean continueGeorge(GeorgeFinancialOwner owner,dev.veltraph.client.rewarp.FarmMaintenanceLease.Token token,
            dev.veltraph.client.inventory.selling.GeorgeWarpPermit permit,Scope previous,Scope current){
        var activation=active.get(owner);var target=sink.get();var observed=scope.get();
        if(owner==null||manager.get(owner.id()).orElse(null)!=owner||!owner.isEnabled()||!enabled.getAsBoolean()
                ||activation==null||activation.binding().terminalOnly()||previous==null||current==null||observed==null||target==null
                ||!activation.scope().same(previous)||!observed.same(current)||permit==null
                ||permit.state()!=dev.veltraph.client.inventory.selling.GeorgeWarpPermit.State.COMMITTED
                ||!owner.admittedGeorgeContinuation(token,permit,previous,current))return false;
        active.put(owner,new Activation(activation.binding(),current,target.identity(),activation.seen()));return true;
    }
    public boolean continueBottle(BottleFinancialOwner owner,dev.veltraph.client.inventory.bottle.BottleAcquisitionRuntime.Travel travel,
            Scope previous,Scope current,BottleFinancialContinuation admission){
        var activation=active.get(owner);var target=sink.get();var observed=scope.get();
        if(owner==null||manager.get(owner.id()).orElse(null)!=owner||!owner.isEnabled()||!enabled.getAsBoolean()||activation==null||activation.binding().terminalOnly()
                ||previous==null||current==null||observed==null||target==null||!activation.scope().same(previous)||!observed.same(current)||owner.pendingBottleFinancialTravel().isPresent()
                ||admission==null||!admission.consume(owner,travel,previous,current))return false;
        active.put(owner,new Activation(activation.binding(),current,target.identity(),activation.seen()));bottlePending.remove(owner);return true;
    }
    /** Poll after owner ticks/owned cleanup: COMPLETE commonly disables its own module. */
    public void poll(long now){
        if(!enabled.getAsBoolean()){active.clear();bottlePending.clear();return;}
        var currentScope=scope.get();var target=sink.get();if(currentScope==null||target==null){active.clear();bottlePending.clear();return;}
        var iterator=active.entrySet().iterator();
        while(iterator.hasNext()){
            var entry=iterator.next();var owner=entry.getKey();var activation=entry.getValue();
            if(manager.get(owner.id()).orElse(null)!=owner){iterator.remove();bottlePending.remove(owner);continue;}
            if(bottlePending.containsKey(owner)||!activation.scope().same(currentScope)||activation.sinkIdentity()!=target.identity()){
                if(retainPendingBottle(owner,activation,currentScope,now))continue;
                iterator.remove();bottlePending.remove(owner);continue;
            }
            boolean consumed=false;
            for(var result:activation.binding().outcomes().get()){
                if(activation.seen().contains(result.id()))continue;
                if(activation.seen().size()>=4096){consumed=true;break;}
                activation.seen().add(result.id());consumed=true;
                if(result.scope().same(currentScope)&&(result.receipt()==null||result.receipt().owner()==owner))target.confirmed(result,now);
                if(activation.binding().terminalOnly())break;
            }
            if(consumed&&activation.binding().terminalOnly()||!owner.isEnabled()&&!owner.hasPendingCleanup()){iterator.remove();bottlePending.remove(owner);}
        }
    }
}
