package dev.veltraph.client.mining.commission;
import java.util.*;

/** Monotonic observation history only. Its boolean cannot replace any native action custody. */
public final class CommissionProgressHistory {
    private final CommissionWorkBinding.Binding binding;private final String context;
    private final Map<CommissionWorkBinding.Identity,Double> progress=new HashMap<>();
    private long serial;private boolean revoked;
    public CommissionProgressHistory(CommissionWorkBinding.Binding binding,CommissionObservation baseline){
        this.binding=Objects.requireNonNull(binding);context=Objects.requireNonNull(baseline).context();serial=baseline.serial();
        if(!binding.current(baseline))throw new IllegalArgumentException("Invalid commission progress baseline");
        for(var task:baseline.tasks()){var key=CommissionWorkBinding.Identity.of(task);if(binding.tasks().contains(key))progress.put(key,task.percent());}
    }
    public boolean observe(CommissionObservation observation){
        if(revoked)return false;
        if(observation==null||!context.equals(observation.context())||observation.serial()<serial||!binding.current(observation)){revoked=true;return false;}
        for(var task:observation.tasks()){var key=CommissionWorkBinding.Identity.of(task);if(!binding.tasks().contains(key))continue;
            var old=progress.get(key);if(old==null||task.percent()<old||task.percent()!=old&&observation.serial()<=serial){revoked=true;return false;}}
        for(var task:observation.tasks()){var key=CommissionWorkBinding.Identity.of(task);if(binding.tasks().contains(key))progress.put(key,task.percent());}
        serial=observation.serial();return true;
    }
    public boolean current(){return !revoked;}
}
