package dev.veltraph.client.metrics;

import dev.veltraph.client.core.Module;
import dev.veltraph.client.core.ModuleManager;
import java.util.*;
import java.util.function.*;

/** Pure lifecycle bridge. Observes public totals; never invokes owner enable/disable/reset/tick. */
public final class OwnerMetricsBindings {
    public record Binding(OwnerMetricEpochs.OwnerCounter kind,LongSupplier total){
        public Binding {Objects.requireNonNull(kind);Objects.requireNonNull(total);}
    }
    public interface Sink {
        default Object identity(){return this;}
        void freshStart(String ownerId,OwnerMetricEpochs.OwnerCounter kind,long baseline,long now);
        void total(String ownerId,OwnerMetricEpochs.OwnerCounter kind,long cumulative,long now);
    }
    private record Observed(long activation,Object sinkIdentity) {}
    private final ModuleManager manager;private final Function<Module,Optional<Binding>> classify;
    private final Supplier<Sink> sink;private final BooleanSupplier enabled;
    private final Map<Module,Long> activations=new IdentityHashMap<>();private final Map<Module,Observed> observed=new IdentityHashMap<>();private long activationSerial;
    public OwnerMetricsBindings(ModuleManager manager,Function<Module,Optional<Binding>> classify,Supplier<Sink> sink,BooleanSupplier enabled){this.manager=Objects.requireNonNull(manager);this.classify=Objects.requireNonNull(classify);this.sink=Objects.requireNonNull(sink);this.enabled=Objects.requireNonNull(enabled);}
    /** Root invokes exactly once after an actual successful owner fresh start, never on preserved resume. */
    public void afterFreshStart(Module module,long now){
        if(module==null||manager.get(module.id()).orElse(null)!=module||!module.isEnabled())return;
        var binding=classify.apply(module);if(binding.isEmpty())return;
        activations.put(module,++activationSerial);
        if(!enabled.getAsBoolean()){observed.remove(module);return;}
        var target=sink.get();if(target!=null)baseline(module,binding.get(),target,now);
    }
    public void poll(long now){
        if(!enabled.getAsBoolean()){observed.clear();return;}var target=sink.get();if(target==null){observed.clear();return;}
        for(var module:manager.all()){
            var activation=activations.get(module);if(activation==null)continue;var binding=classify.apply(module);if(binding.isEmpty())continue;
            var prior=observed.get(module);
            if(!module.isEnabled()){
                // A completing owner can disable itself during its tick. Consume its final public
                // total once, only through the same already-observed activation and native scope.
                if(prior!=null&&prior.activation()==activation&&prior.sinkIdentity()==target.identity()){
                    long total=binding.get().total().getAsLong();if(total>=0)target.total(module.id(),binding.get().kind(),total,now);
                }
                activations.remove(module);observed.remove(module);continue;
            }
            if(prior==null||prior.activation()!=activation||prior.sinkIdentity()!=target.identity())baseline(module,binding.get(),target,now);
            else {long total=binding.get().total().getAsLong();if(total>=0)target.total(module.id(),binding.get().kind(),total,now);}
        }
    }
    private void baseline(Module module,Binding binding,Sink target,long now){
        long total=binding.total().getAsLong();if(total<0)return;target.freshStart(module.id(),binding.kind(),total,now);observed.put(module,new Observed(activations.get(module),target.identity()));
    }
}
