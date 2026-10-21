package dev.veltraph.client.metrics;

import dev.veltraph.client.inventory.bottle.BottleAcquisitionRuntime.Travel;
import java.util.Objects;

/** One admitted native context handoff of the same Bottle activation; seen outcomes remain intact. */
public final class BottleFinancialContinuation {
    private final Object owner;private final Travel travel;private final FinancialOutcomeBindings.Scope previous,current;private boolean consumed;
    public BottleFinancialContinuation(Object owner,Travel travel,FinancialOutcomeBindings.Scope previous,FinancialOutcomeBindings.Scope current){
        this.owner=Objects.requireNonNull(owner);this.travel=Objects.requireNonNull(travel);this.previous=Objects.requireNonNull(previous);this.current=Objects.requireNonNull(current);
        if(previous.player()!=travel.player()||previous.world()!=travel.world()||previous.connection()!=travel.connection()||current.connection()!=travel.connection())throw new IllegalArgumentException("Exact issued/admitted Bottle contexts required");
    }
    public boolean consume(Object owner,Travel travel,FinancialOutcomeBindings.Scope previous,FinancialOutcomeBindings.Scope current){if(consumed||this.owner!=owner||this.travel!=travel||!this.previous.same(previous)||!this.current.same(current))return false;consumed=true;return true;}
}
