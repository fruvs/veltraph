package dev.veltraph.client.mining.vein;

import java.util.Objects;
import java.util.function.Supplier;

/** Cleanup-only routing: once typed custody was attempted, raw cleanup can never be inferred. */
public final class VeinCollectionCleanupDispatch {
    public enum Route { LEGACY,TYPED }
    public static final class Outcome<T> {
        private final Route route;private final T typed;
        private Outcome(Route route,T typed){this.route=route;this.typed=typed;}
        public Route route(){return route;}public T typed(){return typed;}
    }
    private boolean typed;
    public void enterTyped(){typed=true;}
    public boolean typed(){return typed;}
    public <T> Outcome<T> stop(Supplier<T> retireTyped,Runnable stopLegacy){
        Objects.requireNonNull(retireTyped);Objects.requireNonNull(stopLegacy);
        if(typed)return new Outcome<>(Route.TYPED,retireTyped.get());
        stopLegacy.run();return new Outcome<>(Route.LEGACY,null);
    }
}
